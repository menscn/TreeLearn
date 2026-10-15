#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Derive the three-layer 1:1 ledger from the screen inventory plus the instrument records.

The ledger is generated, never filled in.  Its rows come from screen-inventory.json (code
reachability), its structure column from the same JSON, its visual column from the judgement lines
the 003 instrument printed into its measurement record, and its behaviour column from the replay
fields of the screen profile - so a cell either names the evidence that produced it or says, in
words, which evidence is missing.  Readiness is therefore visible as a count instead of being
asserted by a person.

Independence of the checks is deliberate: the inventory is re-derived from the current source tree
and hashed, so a layout change that was not followed by a regeneration stops this script with
INVENTORY STALE rather than producing a tidy ledger off a stale list (SPEC section 10, the
anti-rot clause).  The row floor is a literal, not len(inventory), because a floor computed from
the checked list is no floor at all.

    python -X utf8 .trae/scripts/dev/gen_parity_ledger.py            write artifacts/parity/ledger.md
    python -X utf8 .trae/scripts/dev/gen_parity_ledger.py --no-write print and judge, write nothing
Exit codes: 0 judgements met, 1 a judgement failed, 2 usage error, 3 instrument fault.
"""
import argparse
import hashlib
import importlib.util
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
INV_DEFAULT = os.path.join(REPO, "Docs/04-ux-design/parity/screen-inventory.json")
PROFILE_DEFAULT = os.path.join(REPO, ".trae/scripts/dev/parity/screens.json")
OUT_DEFAULT = os.path.join(REPO, "artifacts/parity/ledger.md")
JUDGEMENT = re.compile(r"^== (\d+)\.", re.M)
# A floor that is not derived from the checked list: an emptied inventory must not be able to
# certify itself by shrinking the floor to zero.
MIN_ROWS = 12
# The claim is the pair of numbers, not the path they were read from.  Comparing the whole
# spec_claims dict would turn the ledger red for a checkout living somewhere else - a relocation
# is not a disagreement between the spec and the inventory.
CLAIM_KEYS = ("screens", "surfaces")


class Fault(Exception):
    """The instrument or one of its inputs is broken - tier 3, never a coverage claim."""


def read_bytes(path):
    if not os.path.isfile(path):
        raise Fault("input not found: %s" % path)
    return open(path, "rb").read()


def load_json(path):
    raw = read_bytes(path)
    try:
        return raw, json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, ValueError) as exc:
        raise Fault("inventory is not readable JSON: %s (%s)" % (path, exc))


def inventory_module():
    """Import gen_screen_inventory.py from its own directory, not from sys.path guesses."""
    path = os.path.join(HERE, "gen_screen_inventory.py")
    spec = importlib.util.spec_from_file_location("gen_screen_inventory", path)
    mod = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(mod)
    except Exception as exc:
        raise Fault("could not load the inventory instrument at %s: %r" % (path, exc))
    return mod


def projection(obj):
    """The measured part of an inventory, without the fields that move with every commit.

    Comparing the whole rendered JSON would turn the ledger red for any unrelated commit, because
    source_commit is inside it.  Anti-rot is about the interface changing, so the comparison is on
    what the tree says about itself - and a bare commit move is printed as its own fact instead.
    """
    keys = ("schema_version", "screens", "surfaces", "components", "layout_census",
            "unreferenced_layouts", "unreached_screens", "counts")
    return hashlib.sha256(json.dumps({k: obj[k] for k in keys}, sort_keys=True,
                                      ensure_ascii=False).encode("utf-8")).hexdigest()


def rederive(mod, java, res, spec):
    """Re-run the instrument over the current tree; nothing here trusts the filed inventory."""
    try:
        data = mod.analyse(java, res)
        claims = mod.spec_claims(spec)
        commit = mod.source_commit(REPO)
    except mod.Fault as exc:
        raise Fault("the inventory instrument itself faulted: %s" % exc)
    return {"projection": projection(data), "commit": commit, "claims": claims}


def claims_disagree(fresh_claims, inv_claims):
    """Compare only the numbers a claim is made of; where they were read from is not a claim."""
    return [fresh_claims.get(k) for k in CLAIM_KEYS] != [inv_claims.get(k) for k in CLAIM_KEYS]


def profile_key(row):
    """Screens key off the class name minus the suffix; surfaces off their layout file."""
    if row["kind"] == "SCREEN":
        cls = row["class"]
        return cls[:-6].lower() if cls.endswith("Screen") else cls.lower()
    layout = row.get("layout") or ""
    return layout.rsplit(".", 1)[-1].lower() if layout else ""


def visual_state(key, entry):
    """Read the judgement lines the instrument actually printed, not a person's summary of them."""
    if not entry:
        return "NO-PROFILE", "屏档案里没有这个键"
    record = entry.get("record_reference") or ""
    path = os.path.join(REPO, record) if record else ""
    if not record:
        return "NO-RECORD", "档案没有指向量测记录"
    if not os.path.isfile(path):
        return "RECORD-MISSING", "档案指向的记录文件不在仓库里: %s" % record
    text = read_bytes(path).decode("utf-8", "replace")
    got = sorted({int(m.group(1)) for m in JUDGEMENT.finditer(text)})
    want = [int(re.search(r"(\d+)", s).group(1)) for s in entry.get("require_sections", [])
            if re.search(r"(\d+)", s)]
    missing = [n for n in want if n not in got]
    if missing:
        return "RECORD-INCOMPLETE", "记录里缺节 %s" % missing
    shot = entry.get("sample_shot") or ""
    if not os.path.isfile(os.path.join(REPO, shot)):
        return "SHOT-MISSING", "档案指向的截图不在仓库里: %s" % shot
    return "MEASURED", "记录含 %d 节，必需节 %d/%d 齐，样图在位" % (
        len(got), len(want), len(want))


def behaviour_state(entry, replay):
    if not entry:
        return "NO-PROFILE", "没有夹具回放规格可查"
    if not replay:
        return "NO-REPLAY-SPEC", "档案没有 prototype 回放段"
    clicks, checks = len(replay.get("clicks", [])), len(replay.get("dom_checks", []))
    if not clicks or not checks:
        return "REPLAY-SPEC-INCOMPLETE", "回放段 clicks=%d dom_checks=%d" % (clicks, checks)
    return "REPLAY-SPEC", "回放规格 %d 次点击 / %d 项 DOM 检查（%s）" % (
        clicks, checks, replay.get("wait_for") or "无等待选择器")


def instrument_line(name):
    """Where the DOM/behaviour instrument lives tells you whether 006 has landed yet."""
    for rel in (".trae/scripts/dev/parity/%s" % name, "artifacts/tmp/%s" % name):
        if os.path.isfile(os.path.join(REPO, rel)):
            return "adopted" if rel.startswith(".trae") else "not-adopted:%s" % rel
    return "absent"


def rows_of(inv):
    out = []
    for s in inv["screens"]:
        out.append({"kind": "SCREEN", "class": s["class"], "layout": s["root_layout"],
                    "self_drawn": s["self_drawn"], "sw600dp": s["sw600dp"],
                    "entries": len(s["reached_by"]), "channels": ",".join(s["channels"])})
    for s in inv["surfaces"]:
        out.append({"kind": "SURFACE", "class": s["producer"], "layout": s["layout"],
                    "surface_kind": s["kind"], "sw600dp": s["layout_sw600dp"],
                    "entries": 1, "channels": s["kind"]})
    return out


def structure_cell(row):
    if row["kind"] == "SCREEN":
        base = "自绘（无根布局）" if row["self_drawn"] else "根布局 %s" % (row["layout"] or "-")
        return "%s / 入口 %d 处（%s）/ sw600dp=%s" % (
            base, row["entries"], row["channels"], "有" if row["sw600dp"] else "无")
    return "浮层 %s（%s）/ sw600dp=%s" % (
        row["layout"] or "代码绘制，无布局文件", row["surface_kind"],
        "有" if row["sw600dp"] else "无")


def build(inv, inv_sha, inv_proj, fresh, drift, profile_path, argv):
    counts = inv["counts"]
    entries = json.loads(read_bytes(profile_path).decode("utf-8"))["screens"]
    replay_tool = instrument_line("verify_1to1.mjs")
    stale = fresh["projection"] != inv_proj
    lines = []
    lines.append("# 1:1 台账（生成物，勿手改）")
    lines.append("")
    lines.append("- 生成命令：`python -X utf8 .trae/scripts/dev/gen_parity_ledger.py`")
    # argv, not sys.argv: the selftest drives main() in-process, and a ledger that named the
    # driver's command line would record an invocation that never happened.
    lines.append("- 本次实际参数：%s" % (" ".join(argv) or "(无，全部走默认路径)"))
    lines.append("- 清单登记来源提交：%s；本行现场读到的 HEAD：%s" % (inv["source_commit"], fresh["commit"]))
    lines.append("- 清单文件哈希（本次读到的字节）：`%s`" % inv_sha)
    lines.append("- 实测面哈希：已落盘 `%s` ／ 当前树重导出 `%s`" % (inv_proj, fresh["projection"]))
    lines.append("- 清单新鲜度：%s" % ("STALE（当前树与已落盘清单的实测面不一致，先重生成清单）"
                                 if stale else "一致（当前树重导出的实测面与已落盘清单同哈希）"))
    lines.append("- 规格宣称与清单登记宣称：%s" % ("一致" if drift == "MATCH"
                                       else "不一致 CLAIM-DRIFT（规格已改，清单未重生成）"))
    lines.append("- 行为仪器（verify_1to1.mjs）：%s — 未收编时本列只报缺证据，不报通过。" % replay_tool)
    lines.append("- 每格的含义：它说的是**证据在不在**，不是还原对不对。MEASURED 只表示 Android 侧基线"
                 "记录在位且必需节齐全；Web 侧是否等价，要等阶段 3 的比对给出另一份记录。")
    lines.append("")
    lines.append("| 条目 | 类别 | 结构层 | 视觉层 | 行为层 |")
    lines.append("|---|---|---|---|---|")
    tally = {"visual": {}, "behaviour": {}}
    for row in rows_of(inv):
        key = profile_key(row)
        entry = entries.get(key)
        vstate, vdetail = visual_state(key, entry)
        bstate, bdetail = behaviour_state(entry, (entry or {}).get("prototype"))
        tally["visual"][vstate] = tally["visual"].get(vstate, 0) + 1
        tally["behaviour"][bstate] = tally["behaviour"].get(bstate, 0) + 1
        name = row["class"] if row["kind"] == "SCREEN" else "%s ← %s" % (
            row["layout"] or "(代码绘制)", row["class"])
        lines.append("| %s | %s | %s | %s：%s | %s：%s |" % (
            name, "屏" if row["kind"] == "SCREEN" else "浮层", structure_cell(row),
            vstate, vdetail, bstate, bdetail))
    lines.append("")
    lines.append("## 汇总（全部由上面的行现场计数得出）")
    lines.append("")
    lines.append("- 台账条目 = 屏 %d + 浮层 %d = %d" % (counts["screens"], counts["surfaces"],
                                             counts["screens"] + counts["surfaces"]))
    for kind, label in (("visual", "视觉层"), ("behaviour", "行为层")):
        parts = ", ".join("%s=%d" % (k, tally[kind][k]) for k in sorted(tally[kind]))
        lines.append("- %s 各状态：%s" % (label, parts))
        lines.append("- %s 状态合计 = %d（必须等于台账条目数）" % (label, sum(tally[kind].values())))
    lines.append("- 自绘屏 %d 个（清单里 self_drawn=True 的条数，逐条见上表）" % counts["self_drawn"])
    lines.append("- sw600dp 变体屏 %d 个" % counts["sw600dp_variants"])
    lines.append("- 未被任何入口引用的屏 = %d；未被引用的布局文件 = %d" % (
        counts["unreached_screens"], counts["unreferenced_layouts"]))
    lines.append("- 规格宣称 屏 %d / 浮层 %d，清单实测 屏 %d / 浮层 %d，差 %+d / %+d" % (
        inv["spec_claims"]["screens"], inv["spec_claims"]["surfaces"], counts["screens"],
        counts["surfaces"], inv["spec_comparison"]["screens_diff"],
        inv["spec_comparison"]["surfaces_diff"]))
    lines.append("")
    return lines, tally


def judgements(inv, rows, inv_proj, fresh, drift):
    bad = []
    if len(rows) < MIN_ROWS:
        bad.append("row floor: measured=%d literal-min=%d (an inventory that yields no rows "
                   "must not certify itself)" % (len(rows), MIN_ROWS))
    if fresh["projection"] != inv_proj:
        bad.append("INVENTORY STALE: the tree re-derives to a different measurement face than the "
                   "filed inventory - regenerate the inventory before trusting this ledger")
    if drift != "MATCH":
        bad.append("CLAIM DRIFT: the spec and the inventory disagree about the spec's own numbers")
    counts = inv["counts"]
    if counts["screens"] != sum(1 for r in rows if r["kind"] == "SCREEN") or \
       counts["surfaces"] != sum(1 for r in rows if r["kind"] == "SURFACE"):
        bad.append("ledger rows do not match the inventory counts (a row was dropped or invented)")
    if counts["unreached_screens"]:
        bad.append("unreached screens present: %s" % ", ".join(inv["unreached_screens"]))
    if counts["unreferenced_layouts"]:
        bad.append("unreferenced layouts present: %s" % ", ".join(inv["unreferenced_layouts"]))
    return bad


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    ap = argparse.ArgumentParser(prog="gen_parity_ledger.py")
    ap.add_argument("--inventory", default=INV_DEFAULT)
    ap.add_argument("--profile", default=PROFILE_DEFAULT)
    ap.add_argument("--java", default=None, help="only for the selftest fixture tree")
    ap.add_argument("--res", default=None, help="only for the selftest fixture tree")
    ap.add_argument("--spec", default=None, help="only for the selftest fixture tree")
    ap.add_argument("--out", default=OUT_DEFAULT)
    ap.add_argument("--no-write", action="store_true")
    try:
        opts = ap.parse_args(argv)
    except SystemExit:
        return 2
    try:
        # Loading the sibling instrument has to be inside the fault guard: measured with
        # gen_screen_inventory.py moved aside, the call above this block left an uncaught traceback
        # and rc=1 - the tier that means "a judgement did not hold" - so "the harness could not
        # start" was readable only as "the coverage claim is red".
        mod = inventory_module()
        java = opts.java if opts.java else mod.JAVA_DEFAULT
        res = opts.res if opts.res else mod.RES_DEFAULT
        spec = opts.spec if opts.spec else mod.SPEC_DEFAULT
        raw, inv = load_json(opts.inventory)
        inv_sha = hashlib.sha256(raw).hexdigest()
        inv_proj = projection(inv)
        fresh = rederive(mod, java, res, spec)
        drift = "DRIFT" if claims_disagree(fresh["claims"], inv["spec_claims"]) else "MATCH"
        rows = rows_of(inv)
        lines, tally = build(inv, inv_sha, inv_proj, fresh, drift, opts.profile, argv)
    except Fault as exc:
        print("INSTRUMENT FAULT: %s" % exc)
        print("EXITCODE=3")
        return 3
    bad = judgements(inv, rows, inv_proj, fresh, drift)
    for b in bad:
        print("FAIL %s" % b)
    lines.append("判定：%s" % ("全部通过" if not bad else "; ".join(bad)))
    lines.append("")
    body = ("\n".join(lines) + "\n").encode("utf-8")
    try:
        body.decode("utf-8")
    except UnicodeDecodeError as exc:
        print("INSTRUMENT FAULT: the ledger bytes are not valid UTF-8: %s" % exc)
        print("EXITCODE=3")
        return 3
    digest = hashlib.sha256(body).hexdigest()
    exit_code = 0 if not bad else 1
    body += ("EXITCODE=%d\n" % exit_code).encode("ascii")
    if opts.no_write:
        # printing the body is what makes --no-write usable as a review command; the written
        # artifact and the console then carry the same judgement lines, not two summaries.
        print("\n".join(lines))
        print("LEDGER derived bytes=%d sha256=%s (not written)" % (len(body), digest))
    else:
        directory = os.path.dirname(opts.out)
        if directory:
            os.makedirs(directory, exist_ok=True)
        open(opts.out, "wb").write(body)
        print("LEDGER written path=%s bytes=%d sha256=%s"
              % (opts.out.replace(os.sep, "/"), len(body), digest))
    print("LEDGER rows=%d screens=%d surfaces=%d visual_states=%s behaviour_states=%s"
          % (len(rows), inv["counts"]["screens"], inv["counts"]["surfaces"],
             json.dumps(tally["visual"], sort_keys=True), json.dumps(tally["behaviour"], sort_keys=True)))
    print("EXITCODE=%d" % exit_code)
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
