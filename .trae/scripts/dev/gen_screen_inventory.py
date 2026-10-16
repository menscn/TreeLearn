#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Derive the Android screen / surface inventory from code reachability.

The reproduction scope used to be a number typed into a specification.  A layout-directory census
gives a third answer again - files named screen_*.xml plus their *_body fragments - and misses the
screens that build their view tree in code.  Here a screen is a class of the screen package that
MainActivity reaches, and a surface is something the SDK itself floats over the content:
PopupWindow, Dialog, PopupMenu, AlertDialog.Builder, an included subtree that starts hidden, a
subtree addView'd into a host instead of swapped in.  The first two of those were the whole list
until a reconciliation against this measurement found that the pattern set had been hand-copied
from the two constructors someone happened to think of, leaving live floating surfaces out of the
coverage claim - the empty-match-set failure mode, one pattern short of visible.  Every match is
additionally required to sit on a line that starts as code, so a javadoc mentioning a constructor
cannot mint a surface.  The spec is checked for a *pointer* to this file, not for numbers: since the
2026-10-16 ruling that the inventory is the range authority, a count written into the spec would be
a second source and would go stale the next time Android changes.  Count-shaped lines in the spec
are printed with their line numbers and left to a human to label as historical.  Empty match sets
fail closed; the JSON is deterministic (no timestamps), so the same tree always gives the same bytes
and a hash of it can be cited.

    python .trae/scripts/dev/gen_screen_inventory.py                 analyse, print, write the JSON
    python -X utf8 .trae/scripts/dev/screen_inventory_selftest.py    positive and negative cases
Exit codes: 0 judgements met, 1 a judgement failed, 2 usage error, 3 instrument fault.
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
JAVA_DEFAULT = os.path.join(REPO, "android/app/src/main/java/com/treelearn/app")
RES_DEFAULT = os.path.join(REPO, "android/app/src/main/res")
OUT_DEFAULT = os.path.join(REPO, "Docs/04-ux-design/parity/screen-inventory.json")
SPEC_DEFAULT = os.path.join(REPO, "Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md")
# Derived from the output path so the pointer test cannot outlive a move of the inventory itself.
INVENTORY_NAME = os.path.basename(OUT_DEFAULT)
# 词表要含裸量词「屏」：只列复合词（屏幕/自绘屏）时，「现值 14 屏 / 其中 5 屏自绘」这类写法
# 完全不被点名，于是"复述只打印给人标注"这条兜底对最像当前值的那种句子失效——与浮层少计同一族。
COUNT_SHAPE = re.compile(r"\d+\s*个?\s*(?:屏幕|浮层|自绘屏|屏)")
MIN_SCREENS = 8
MIN_SURFACES = 3
POPUP_WINDOW = re.compile(r"new\s+PopupWindow\s*\(")
DIALOG = re.compile(r"new\s+Dialog\s*\(")
POPUP_MENU = re.compile(r"new\s+PopupMenu\s*\(")
ALERT_DIALOG = re.compile(r"new\s+AlertDialog\.Builder\s*\(")
# The kind vocabulary lives here and nowhere else. A consumer that re-lists it (the green evidence's
# per-entry check did) becomes a second source, so a legitimate new kind reads as "unknown kind" and
# the tempting fix is to delete the check rather than to extend the copy.
SURFACE_PATTERNS = (("popup_window", POPUP_WINDOW), ("dialog", DIALOG),
                    ("popup_menu", POPUP_MENU), ("alert_dialog", ALERT_DIALOG))
# Kinds not minted by a constructor pattern: a host view added to a container in code, and an
# <include> whose host layout is hidden. They join the vocabulary so consumers can derive it.
STRUCTURAL_KINDS = ("code_drawn_host", "included_hidden")
SURFACE_KINDS = tuple(sorted([k for k, _ in SURFACE_PATTERNS] + list(STRUCTURAL_KINDS)))
# Kinds whose content is not an inflated R.layout at the call site: a PopupMenu fills itself from
# getMenu().add(R.string...), a message AlertDialog from setTitle/setMessage. Running the
# nearest-layout heuristic over them would attribute some unrelated layout to the surface and then
# derive a sw600dp variant that does not exist.
LAYOUTLESS_KINDS = ("popup_menu", "alert_dialog")
LAYOUT_REF = re.compile(r"R\.layout\.(\w+)")
ROOT_INFLATE = re.compile(r"inflate\(\s*R\.layout\.(\w+)\s*,\s*(?:\([\w.]+\)\s*)?this\s*,\s*true\s*\)")
FIND_VIEW = re.compile(r"findViewById\(\s*R\.id\.(\w+)")
SIGNATURE = re.compile(r"[^\w](?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?"
                       r"[\w<>\[\],.]+\s+(\w+)\s*\([^)]*\)\s*(?:throws [\w,\s]+)?\{")


class Fault(Exception):
    """The instrument or its input is broken - tier 3, never a coverage claim."""


def read(path):
    if not os.path.isfile(path):
        raise Fault("missing input: %s" % path)
    data = open(path, "rb").read()
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise Fault("input is not valid UTF-8: %s (%s)" % (path, exc))


def line_of(text, pos):
    return text.count("\n", 0, pos) + 1


def method_spans(text):
    """[(name, start, end)] per method body; an unbalanced body is a fault, not a silent <none>."""
    found = []
    for m in SIGNATURE.finditer(text):
        depth, i, n = 0, m.end() - 1, len(text)
        while i < n:
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        if depth != 0:
            raise Fault("unbalanced method body for %s at line %d"
                        % (m.group(1), line_of(text, m.start())))
        found.append((m.group(1), m.start(), i))
    found.sort(key=lambda t: t[1])
    return found


def enclosing(spans, pos):
    name = "<none>"
    for nm, start, end in spans:
        if start <= pos <= end:
            name = nm
    return name


def is_code_line(lines, ln):
    """Whether the raw line carrying a match is live code rather than documentation.

    Deliberately parser-free: it looks only at what the line itself starts with, so it cannot be
    talked out of a judgement by comment state elsewhere in the file. A Java comment lexer here
    would be both the criterion and the exemption for its own misses, which is the shape this
    repository has already been burned by twice. The residue it does not see - a marker sitting
    inside a block comment on a line that does not itself start with a comment token - is recorded
    as an unmeasured gap in the task file rather than claimed to be absent.
    """
    if not 1 <= ln <= len(lines):
        raise Fault("line %d is outside the scanned file (%d line(s))" % (ln, len(lines)))
    start = lines[ln - 1].lstrip()
    return not (start.startswith("//") or start.startswith("*") or start.startswith("/*"))


def scan_java_file(path, window=40):
    text = read(path)
    lines = text.split("\n")

    def live(match):
        return is_code_line(lines, line_of(text, match.start()))

    refs = [(m.group(1), line_of(text, m.start()))
            for m in LAYOUT_REF.finditer(text) if live(m)]
    root = None
    for m in ROOT_INFLATE.finditer(text):
        if live(m):
            root = (m.group(1), line_of(text, m.start()))
            break
    sites = []
    for kind, pat in SURFACE_PATTERNS:
        for m in pat.finditer(text):
            if not live(m):
                continue
            ln = line_of(text, m.start())
            if kind in LAYOUTLESS_KINDS:
                sites.append({"kind": kind, "line": ln, "layout": None})
                continue
            # the layout inflated just above the constructor is the one it shows; only when nothing
            # precedes it inside the window does the next line below count (Dialog + setContentView).
            near = [(0 if rline <= ln else 1, abs(ln - rline), name)
                    for name, rline in refs if abs(ln - rline) <= window]
            near.sort()
            sites.append({"kind": kind, "line": ln, "layout": near[0][2] if near else None})
    return {"refs": refs, "root": root, "sites": sites,
            "ids": {m.group(1) for m in FIND_VIEW.finditer(text) if live(m)}}


def layout_census(res_root):
    """name -> layout-* dirs holding it, its root id, whether it starts hidden, what it includes."""
    census = {}
    for entry in sorted(os.listdir(res_root)):
        d = os.path.join(res_root, entry)
        if not entry.startswith("layout") or not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            if not f.endswith(".xml"):
                continue
            name = f[:-4]
            info = census.setdefault(name, {"dirs": [], "includes": [], "root_id": None, "hidden": False})
            info["dirs"].append(entry)
            text = read(os.path.join(d, f))
            first = re.search(r"<(?!/?xml|!)[A-Za-z][\w.]*[^>]*?>", text, re.S)
            if not first:
                raise Fault("layout %s/%s has no parsable root element" % (entry, f))
            if entry != "layout":
                continue
            tag = first.group(0)
            ident = re.search(r'android:id="@\+?id/(\w+)"', tag)
            info["root_id"] = ident.group(1) if ident else None
            info["hidden"] = bool(re.search(r'android:visibility="gone"', tag))
            info["includes"] = re.findall(r'<include[^>]*?layout="@layout/(\w+)"[^>]*?>', text, re.S)
    for name, info in sorted(census.items()):
        for inc in info["includes"]:
            if inc not in census:
                raise Fault("dead include: %s refers to missing layout %s" % (name, inc))
    return census


def arrival_edges(java_root):
    """How MainActivity reaches each screen class, and through which channel."""
    text = read(os.path.join(java_root, "MainActivity.java"))
    spans = method_spans(text)
    reached = {}

    def record(cls, pos, channel, host_id=None):
        reached.setdefault(cls, []).append(
            {"method": enclosing(spans, pos), "line": line_of(text, pos),
             "channel": channel, "host_id": host_id})

    for m in re.finditer(r"new\s+(\w+Screen)\s*\(\s*(?:this|getContext\(\))", text):
        record(m.group(1), m.start(), "constructed")
    fields = {}
    for m in re.finditer(r"(?:private|final)\s+(?:final\s+)?(\w*Screen)\s+(\w+)\s*[;=]", text):
        if enclosing(spans, m.start()) == "<none>":
            fields[m.group(2)] = m.group(1)
    for m in re.finditer(r"swap\(\s*(\w+)\s*\)", text):
        if m.group(1) in fields:
            record(fields[m.group(1)], m.start(), "swap")
    for m in re.finditer(r"addView\(\s*(\w+)\b", text):
        if m.group(1) in fields:
            host = FIND_VIEW.search(text[max(0, m.start() - 200):m.start()])
            record(fields[m.group(1)], m.start(), "addView", host.group(1) if host else None)
    return reached, {n for n in LAYOUT_REF.findall(text)}


def analyse(java_root, res_root):
    screen_dir = os.path.join(java_root, "screen")
    if not os.path.isdir(screen_dir):
        raise Fault("missing screen package: %s" % screen_dir)
    census = layout_census(res_root)
    reached, main_refs = arrival_edges(java_root)
    files = {}
    for f in sorted(os.listdir(screen_dir)):
        if f.endswith(".java"):
            info = scan_java_file(os.path.join(screen_dir, f))
            info["class"] = f[:-5]
            info["path"] = "screen/" + f
            files[f[:-5]] = info

    screens, surfaces = [], []
    for stem in sorted(files):
        info = files[stem]
        for site in info["sites"]:
            surfaces.append({"kind": site["kind"], "producer": stem, "line": site["line"],
                             "layout": site["layout"], "host_layout": None, "root_id": None,
                             "consumer_proven": None, "host_is_screen_root": None,
                             "layout_sw600dp": bool(site["layout"]) and
                             "layout-sw600dp" in census.get(site["layout"], {}).get("dirs", [])})
        if not stem.endswith("Screen"):
            continue
        hits = reached.get(stem, [])
        root_layout = info["root"][0] if info["root"] else None
        screens.append({"class": stem, "path": info["path"], "root_layout": root_layout,
                        "self_drawn": root_layout is None,
                        "sw600dp": bool(root_layout) and
                        "layout-sw600dp" in census.get(root_layout, {}).get("dirs", []),
                        "reached_by": hits, "channels": sorted({h["channel"] for h in hits}),
                        "layout_refs": sorted({n for n, _ in info["refs"]})})
        add = [h for h in hits if h["channel"] == "addView"]
        if add:
            surfaces.append({"kind": "code_drawn_host", "producer": stem, "line": add[0]["line"],
                             "layout": None, "host_layout": None, "root_id": None,
                             "consumer_proven": None, "host_is_screen_root": None,
                             "host_id": add[0]["host_id"], "layout_sw600dp": False})

    owner = {s["root_layout"]: s["class"] for s in screens if s["root_layout"]}
    visible_includes = 0
    for name, info in sorted(census.items()):
        for inc in info["includes"]:
            meta = census[inc]
            host_class = owner.get(name)
            if not meta["hidden"]:
                visible_includes += 1
                continue
            surfaces.append({"kind": "included_hidden", "producer": host_class, "line": None,
                             "layout": inc, "host_layout": name, "root_id": meta["root_id"],
                             "host_is_screen_root": host_class is not None,
                             "consumer_proven": bool(meta["root_id"]) and host_class in files
                             and meta["root_id"] in files[host_class]["ids"],
                             "layout_sw600dp": "layout-sw600dp" in meta["dirs"]})

    referenced = set(main_refs)
    for info in files.values():
        referenced |= {n for n, _ in info["refs"]}
    for info in census.values():
        referenced |= set(info["includes"])
    unreferenced = sorted(set(census) - referenced)
    unreached = sorted(s["class"] for s in screens if not s["reached_by"])
    counts = {"screens": len(screens), "self_drawn": sum(1 for s in screens if s["self_drawn"]),
              "sw600dp_variants": sum(1 for s in screens if s["sw600dp"]), "surfaces": len(surfaces),
              "surfaces_by_kind": {k: sum(1 for s in surfaces if s["kind"] == k)
                                   for k in sorted({s["kind"] for s in surfaces})},
              "component_classes": sum(1 for stem in files if not stem.endswith("Screen")),
              "layout_files": len(census), "screen_package_files": len(files),
              "visible_includes": visible_includes, "unreferenced_layouts": len(unreferenced),
              "unreached_screens": len(unreached)}
    return {"schema_version": 1, "screens": screens, "surfaces": surfaces,
            "components": sorted(stem for stem in files if not stem.endswith("Screen")),
            "layout_census": census, "unreferenced_layouts": unreferenced,
            "unreached_screens": unreached, "counts": counts}


def spec_link(spec_path):
    """The spec owns a pointer, not a number.

    Until 2026-10-16 this function parsed the counts *out of the spec text* and diffed them against
    the measurement, which was the right guard while a human wrote the scope by hand - it is what
    exposed the 6-vs-16 surface gap. Once the inventory became the range authority (user ruling,
    2026-10-16), parsing numbers from the spec would either force the spec to keep carrying them
    (a second source, stale the moment Android changes) or fault the instrument every time the spec
    was written correctly. So: the pointer is judged, count-shaped lines are printed with their line
    numbers and left to a human to label as historical - "is this restatement marked as history?" is
    not a judgement this instrument can make without reading intent.
    """
    text = read(spec_path)
    return {"source": spec_path.replace(os.sep, "/"),
            "inventory_cited": INVENTORY_NAME in text,
            "count_shaped_lines": [i for i, l in enumerate(text.splitlines(), 1)
                                   if COUNT_SHAPE.search(l)]}


def source_commit(cwd):
    try:
        p = subprocess.run(["git", "-c", "core.quotepath=false", "rev-parse", "HEAD"],
                           cwd=cwd, capture_output=True, text=True)
    except OSError as exc:
        raise Fault("git is not runnable: %s" % exc)
    if p.returncode != 0:
        raise Fault("git rev-parse HEAD failed: %s" % p.stderr.strip()[:160])
    return p.stdout.strip()


def render(data, commit, link):
    inv = dict(data)
    inv["generated_by"] = "python .trae/scripts/dev/gen_screen_inventory.py"
    inv["source_commit"] = commit
    inv["spec_link"] = link
    return json.dumps(inv, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def report_lines(data, link):
    out = []
    for s in data["screens"]:
        ent = ",".join("%s@%s" % (h["method"], h["line"]) for h in s["reached_by"]) or "-"
        out.append("SCREEN %-24s root=%-22s self_drawn=%-5s sw600dp=%-5s channels=%-22s entries=%s"
                   % (s["class"], s["root_layout"] or "-", s["self_drawn"], s["sw600dp"],
                      ",".join(s["channels"]) or "none", ent))
    for s in data["surfaces"]:
        extra = ""
        if s["kind"] == "included_hidden":
            extra = " host=%s consumer_proven=%s" % (s["host_layout"], s["consumer_proven"])
        if s["kind"] == "code_drawn_host":
            extra = " host_id=%s" % s.get("host_id")
        out.append("SURFACE %-17s layout=%-24s producer=%-24s line=%-5s sw600dp=%-5s%s"
                   % (s["kind"], s["layout"] or "-", s["producer"] or "-", s["line"],
                      s["layout_sw600dp"], extra))
    for name in data["unreferenced_layouts"]:
        out.append("UNREFERENCED_LAYOUT " + name)
    for cls in data["unreached_screens"]:
        out.append("UNREACHED_SCREEN " + cls)
    c = data["counts"]
    out.append("MEASURED screens=%d self_drawn=%d sw600dp=%d surfaces=%d visible_includes=%d "
               "component_classes=%d layout_files=%d screen_package_files=%d"
               % (c["screens"], c["self_drawn"], c["sw600dp_variants"], c["surfaces"],
                  c["visible_includes"], c["component_classes"], c["layout_files"],
                  c["screen_package_files"]))
    out.append("SURFACES_BY_KIND " + json.dumps(c["surfaces_by_kind"], sort_keys=True))
    ok = c["screens"] >= MIN_SCREENS and c["surfaces"] >= MIN_SURFACES
    out.append("FLOOR screens>=%d got=%d surfaces>=%d got=%d ok=%s"
               % (MIN_SCREENS, c["screens"], MIN_SURFACES, c["surfaces"], ok))
    pointer = link["inventory_cited"]
    out.append("SPEC_POINTER cites=%s file=%s judged-into-exit=%s"
               % (pointer, INVENTORY_NAME, pointer))
    out.append("SPEC_RESTATED count-shaped=%d lines=%s (printed for a human to label as "
               "historical - not judged: the numbers belong to this file's counts)"
               % (len(link["count_shaped_lines"]),
                  ",".join(str(n) for n in link["count_shaped_lines"]) or "none"))
    return out, (ok and pointer)


def main(argv=None):
    ap = argparse.ArgumentParser(prog="gen_screen_inventory.py")
    ap.add_argument("--java", default=JAVA_DEFAULT)
    ap.add_argument("--res", default=RES_DEFAULT)
    ap.add_argument("--out", default=OUT_DEFAULT)
    ap.add_argument("--spec", default=SPEC_DEFAULT)
    ap.add_argument("--no-write", action="store_true")
    try:
        opts = ap.parse_args(argv)
    except SystemExit:
        return 2
    try:
        data = analyse(opts.java, opts.res)
        link = spec_link(opts.spec)
        text = render(data, source_commit(REPO), link)
    except Fault as exc:
        print("INSTRUMENT FAULT: %s" % exc)
        print("EXITCODE=3")
        return 3
    lines, ok = report_lines(data, link)
    for line in lines:
        print(line)
    raw = text.encode("utf-8")
    digest = hashlib.sha256(raw).hexdigest()
    if opts.no_write:
        print("JSON derived bytes=%d sha256=%s (not written)" % (len(raw), digest))
    else:
        os.makedirs(os.path.dirname(opts.out), exist_ok=True)
        open(opts.out, "wb").write(raw)
        print("JSON written path=%s bytes=%d sha256=%s"
              % (opts.out.replace(os.sep, "/"), len(raw), digest))
    print("EXITCODE=%d" % (0 if ok else 1))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
