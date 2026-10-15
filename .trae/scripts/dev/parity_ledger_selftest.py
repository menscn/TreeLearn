#!/usr/bin/env python3
"""Positive/negative selftest for gen_parity_ledger.py.

Every judgement of the ledger generator has to be seen firing on a synthetic tree: the staleness
guard (the anti-rot clause of SPEC section 10), the row floor, the row-count cross-check, the
absence tokens of the visual and behaviour columns, and the exit tiers.  Fixtures live under
artifacts/tmp/_inv007_run/ and are removed afterwards; nothing here writes into the Android tree or
into the filed inventory.

Exit tiers: 0 all cases matched, 1 a case's judgement did not match, 2 bad usage,
3 instrument fault (fixture could not be built, or fewer cases ran than the literal below).
"""
import importlib.util
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import contextlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
try:
    import gen_screen_inventory as G
except ImportError as exc:
    # Same channel as the guard in screen_inventory_selftest.py: a traceback would exit with rc=1,
    # which is this suite's "a case did not match" tier.  The ledger instrument below is loaded
    # with _load() for the same reason; the 005 instrument is a plain import, so it needs the
    # guard here too rather than a second, different answer to the same absence.
    print("INSTRUMENT FAULT: gen_screen_inventory.py is not importable next to this script (%s);"
          " no case can run" % exc)
    print("EXITCODE=3")
    sys.exit(3)


def _load(name):
    """Load a sibling instrument, or return None so a missing one is a printed fault, not a crash.

    An ImportError traceback would end the run before the first judgement line, which is how
    "the harness broke" gets read as "the case failed".
    """
    path = os.path.join(HERE, name + ".py")
    if not os.path.isfile(path):
        return None
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


L = _load("gen_parity_ledger")
F = _load("screen_inventory_selftest")       # reuse the one fixture builder; do not fork it

EXPECTED_CASES = 17                          # bare literal; must not be derived from CASES
RUN_ROOT = os.path.join(G.REPO, "artifacts", "tmp", "_inv007_run")
REQUIRED_ATTRS = ("main", "projection", "rederive", "rows_of", "visual_state", "behaviour_state",
                  "judgements", "instrument_line", "claims_disagree", "Fault")

CASES = []


def case(name, want_rc):
    def deco(fn):
        CASES.append((name, want_rc, fn))
        return fn
    return deco


def w(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = text.encode("utf-8") if isinstance(text, str) else text
    open(path, "wb").write(data)


def run(argv):
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        rc = L.main(argv)
    return rc, buf.getvalue()


def inv_file(root, java, res, spec):
    """Let the real instrument write an inventory for a synthetic tree."""
    out = os.path.join(root, "inventory.json")
    rc, _ = run_inv(["--java", java, "--res", res, "--spec", spec, "--out", out])
    assert rc == 0, "fixture inventory did not build (rc=%d)" % rc
    return out


def run_inv(argv):
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        rc = G.main(argv)
    return rc, buf.getvalue()


def tree(root, screens=9, popups=3, dialog=True):
    return F.build(os.path.join(root, "tree"), screens=screens, popups=popups, dialog=dialog)


def profile(root, entries):
    path = os.path.join(root, "profile.json")
    w(path, json.dumps({"screens": entries}, ensure_ascii=False))
    return path


def record(root, sections=(1, 2, 3, 4, 5, 6, 7)):
    rel = "artifacts/tmp/_inv007_run/record.txt"
    path = os.path.join(RUN_ROOT, "record.txt")
    body = "\n".join("== %d. 节 %d ==" % (n, n) for n in sections) + "\n"
    w(path, body)
    return rel, path


def shot(root):
    rel = "artifacts/tmp/_inv007_run/shot.png"
    path = os.path.join(RUN_ROOT, "shot.png")
    w(path, b"\x89PNG\r\n\x1a\n fixture")
    return rel, path


def entry(rec_rel, shot_rel, sections=7, replay=True):
    e = {"record_reference": rec_rel, "sample_shot": shot_rel,
         "require_sections": ["== %d." % n for n in range(1, sections + 1)]}
    if replay:
        e["prototype"] = {"clicks": [".a", ".b"], "dom_checks": ["x", "y", "z"],
                          "wait_for": ".msg"}
    return e


def ledger(argv):
    return run(["--no-write"] + argv) if "--out" not in argv else run(argv)


def fail_lines(text):
    """The generator's judgement lines, located by line shape rather than by adjacency.

    A FAIL can be the very first line of stdout (that is what --no-write does with a firing
    judgement), so a search for a newline-preceded "FAIL " reports "no complaint" in exactly the
    case it exists to catch.  Measured: a relocated copy tree whose only difference was the path
    the spec was read from printed FAIL CLAIM DRIFT as line 1 while the old check still passed.
    """
    return [l for l in text.splitlines() if l.startswith("FAIL ")]


@case("pos-real-tree", 0)
def _c_real(root):
    rc, out = ledger([])
    return rc, [("tier 0 on the filed inventory", rc == 0),
                ("no FAIL line printed", not fail_lines(out)),
                ("freshness says the faces agree", "清单新鲜度：一致" in out),
                ("rows counted from the inventory", "rows=24" in out),
                ("the measured screen is recognised", '"MEASURED": 1' in out),
                ("unmeasured screens are counted, not hidden", '"NO-PROFILE": 23' in out),
                ("the replay spec is recognised", '"REPLAY-SPEC": 1' in out),
                ("the un-adopted Node instrument is named", "not-adopted" in out),
                ("exit code derived from judgements", out.strip().endswith("EXITCODE=0"))]


@case("pos-fail-line-scan-catches-a-leading-fail", 0)
def _c_scan(root):
    # A positive control for the check above, shaped like the failure it is meant to catch:
    # --no-write prints its judgement lines before the body, so the complaint leads the output.
    leading = "FAIL CLAIM DRIFT: the spec and the inventory disagree\n# 1:1 台账\n- 台账条目\n"
    prose = "# 1:1 台账\n- 说明：本文档 FAIL 于比较，不是判定行\n"
    return 0, [("a leading FAIL is seen as a judgement line",
               fail_lines(leading) == ["FAIL CLAIM DRIFT: the spec and the inventory disagree"]),
              ("the adjacency form this replaces missed it", "\nFAIL " not in leading),
              ("a FAIL inside prose is not a judgement line", not fail_lines(prose))]


@case("pos-fixture-tree", 0)
def _c_fixture(root):
    java, res, spec = tree(root)
    inv = inv_file(root, java, res, spec)
    rel_r, _ = record(root)
    rel_s, _ = shot(root)
    prof = profile(root, {"s01": entry(rel_r, rel_s)})
    rc, out = ledger(["--inventory", inv, "--profile", prof, "--java", java, "--res", res,
                      "--spec", spec])
    return rc, [("tier 0 with the fixture tree", rc == 0),
                ("the s01 screen reads MEASURED", '"MEASURED": 1' in out),
                ("its behaviour cell reads REPLAY-SPEC", '"REPLAY-SPEC": 1' in out),
                ("no staleness complaint", "INVENTORY STALE" not in out)]


@case("neg-stale-inventory", 1)
def _c_stale(root):
    java, res, spec = tree(root)
    inv = inv_file(root, java, res, spec)
    prof = profile(root, {})
    # the tree changes after the inventory was filed: this is exactly the drift the
    # anti-rot clause exists to catch, and the ledger must refuse to prettify it
    w(os.path.join(java, "screen", "LateScreen.java"), F.screen_java("LateScreen", "late_layout"))
    w(os.path.join(res, "layout", "late_layout.xml"), F.layout("FrameLayout", "late_layout_root"))
    rc, out = ledger(["--inventory", inv, "--profile", prof, "--java", java, "--res", res,
                      "--spec", spec])
    return rc, [("tier 1 not tier 3", rc == 1),
                ("staleness named in words", "INVENTORY STALE" in out),
                ("both faces printed so the diff is checkable", out.count("实测面哈希") >= 1),
                ("the ledger still reports its rows", "rows=" in out)]


@case("neg-claim-drift", 1)
def _c_drift(root):
    # The inventory recorded the spec's own numbers; the spec was edited afterwards while the
    # measured faces stayed put, so CLAIM DRIFT is the only judgement that can fire here.
    java, res, spec = tree(root)
    inv = inv_file(root, java, res, spec)
    w(spec, "范围：11 个屏幕与 4 个浮层逐屏还原。\n")
    rc, out = ledger(["--inventory", inv, "--profile", profile(root, {}),
                      "--java", java, "--res", res, "--spec", spec])
    return rc, [("tier 1 for a claim disagreement", rc == 1),
                ("drift named in words", "CLAIM DRIFT" in out),
                ("the header marks the disagreement too", "不一致 CLAIM-DRIFT" in out),
                ("nothing else fired, so the red is attributable",
                 "INVENTORY STALE" not in out and "row floor" not in out)]


@case("pos-drift-ignores-the-source-path", 0)
def _c_source_path(root):
    # The claim is the pair of numbers.  Where they were read from is not part of the claim, so a
    # checkout living at another path (a relocated copy, a second clone) must not be reported as
    # the spec and the inventory disagreeing.  This case is the regression lock for the form the
    # battery caught: comparing the whole dict turned every copy tree red.
    java, res, spec = tree(root)
    inv = inv_file(root, java, res, spec)
    data = json.loads(open(inv, "rb").read().decode("utf-8"))
    data["spec_claims"]["source"] = "W:/other/checkout/Docs/spec/SPEC-elsewhere.md"
    w(inv, json.dumps(data))
    rc, out = ledger(["--inventory", inv, "--profile", profile(root, {}),
                      "--java", java, "--res", res, "--spec", spec])
    return rc, [("tier 0 with only the path differing", rc == 0),
                ("no drift complaint", "CLAIM DRIFT" not in out),
                ("the header still reports agreement", "规格宣称与清单登记宣称：一致" in out),
                ("the numbers are still read and printed", "规格宣称 屏" in out)]


@case("neg-row-floor", 1)
def _c_floor(root):
    # a legal but small tree: the inventory is current, so the only thing that can fire is the
    # literal row floor. A floor derived from the inventory itself could never fire here.
    java, res, spec = tree(root, screens=8, popups=3, dialog=False)
    inv = inv_file(root, java, res, spec)
    rc, out = ledger(["--inventory", inv, "--profile", profile(root, {}),
                      "--java", java, "--res", res, "--spec", spec])
    return rc, [("tier 1 for a coverage shortfall", rc == 1),
                ("the floor fired and names both numbers", "row floor: measured=" in out
                 and "literal-min=12" in out),
                ("the shortfall is not dressed up as success", "判定：全部通过" not in out),
                ("staleness did not fire, so the red is attributable", "INVENTORY STALE" not in out)]


@case("neg-rows-mismatch", 1)
def _c_mismatch(root):
    java, res, spec = tree(root)
    inv = inv_file(root, java, res, spec)
    data = json.loads(open(inv, "rb").read().decode("utf-8"))
    data["counts"]["screens"] += 1                       # a count that no row can back
    w(inv, json.dumps(data))
    rc, out = ledger(["--inventory", inv, "--profile", profile(root, {}),
                      "--java", java, "--res", res, "--spec", spec])
    return rc, [("tier 1", rc == 1),
                ("the cross-check names the mismatch", "do not match the inventory counts" in out)]


@case("neg-unreached-screen", 1)
def _c_unreached(root):
    java, res, spec = tree(root)
    inv = inv_file(root, java, res, spec)
    data = json.loads(open(inv, "rb").read().decode("utf-8"))
    data["unreached_screens"] = ["GhostScreen"]
    data["counts"]["unreached_screens"] = 1
    w(inv, json.dumps(data))
    rc, out = ledger(["--inventory", inv, "--profile", profile(root, {}),
                      "--java", java, "--res", res, "--spec", spec])
    return rc, [("tier 1", rc == 1), ("the suspect class is named", "GhostScreen" in out),
                ("it is called a coverage hole, not a footnote", "unreached screens present" in out)]


@case("pos-absence-is-a-token-not-a-blank", 0)
def _c_absent(root):
    java, res, spec = tree(root)
    inv = inv_file(root, java, res, spec)
    rel_r, _ = record(root)
    rel_s, _ = shot(root)
    prof = profile(root, {"s01": entry(rel_r, rel_s),
                          "s02": {"record_reference": "artifacts/tmp/_inv007_run/gone.txt",
                                  "sample_shot": rel_s, "require_sections": ["== 1."]},
                          "s03": {"record_reference": rel_r, "sample_shot": rel_s,
                                  "require_sections": ["== 9."]},
                          "s04": {"record_reference": rel_r, "sample_shot": "docs/nope.png",
                                  "require_sections": ["== 1."]},
                          "s05": entry(rel_r, rel_s, replay=False)})
    rc, out = ledger(["--inventory", inv, "--profile", prof,
                      "--java", java, "--res", res, "--spec", spec])
    return rc, [("tier 0: absence is a state, not a failure", rc == 0),
                ("missing record named", "RECORD-MISSING" in out),
                ("missing section named with the number", "RECORD-INCOMPLETE" in out and "[9]" in out),
                ("missing shot named", "SHOT-MISSING" in out),
                ("missing replay spec named", "NO-REPLAY-SPEC" in out),
                ("every state is tallied", '"RECORD-MISSING": 1' in out
                 and '"RECORD-INCOMPLETE": 1' in out and '"SHOT-MISSING": 1' in out)]


@case("pos-written-artifact-selfdescribes", 0)
def _c_written(root):
    out_path = os.path.join(root, "ledger.md")
    rc, out = run(["--out", out_path])
    raw = open(out_path, "rb").read()
    try:
        text = raw.decode("utf-8")
        strict = True
    except UnicodeDecodeError:
        text = raw.decode("utf-8", "replace")
        strict = False
    body_rows = len([l for l in text.splitlines() if l.startswith("| ")]) - 1
    return rc, [("tier 0", rc == 0),
                ("file written where asked", "LEDGER written path" in out),
                ("bytes are strict UTF-8", strict),
                ("generation command in the file", "gen_parity_ledger.py" in text),
                ("the real invocation is in the file", "--out" in text),
                ("source commit in the file", "来源提交" in text),
                ("inventory hash in the file", "清单文件哈希" in text),
                ("exit code is the last line", text.rstrip().endswith("EXITCODE=0")),
                ("row tally in the file equals the tally printed",
                 body_rows == int(out.split("rows=")[1].split()[0]))]


@case("pos-no-write-writes-nothing", 0)
def _c_nowrite(root):
    out_path = os.path.join(root, "never.md")
    rc, out = run(["--no-write", "--out", out_path])
    return rc, [("tier 0", rc == 0),
                ("report says it did not write", "(not written)" in out),
                ("no file created", not os.path.exists(out_path))]


@case("neg-missing-inventory", 3)
def _c_missing_inv(root):
    rc, out = ledger(["--inventory", os.path.join(root, "nope.json")])
    return rc, [("tier 3 for a missing input", rc == 3),
                ("fault names the path", "input not found" in out)]


@case("neg-inventory-not-json", 3)
def _c_bad_json(root):
    path = os.path.join(root, "junk.json")
    w(path, b"{ not json at all")
    rc, out = ledger(["--inventory", path])
    return rc, [("tier 3", rc == 3), ("fault says it is not readable JSON",
                                      "not readable JSON" in out)]


@case("neg-missing-profile", 3)
def _c_missing_profile(root):
    rc, out = ledger(["--profile", os.path.join(root, "nope.json")])
    return rc, [("tier 3 when the profile is gone", rc == 3),
                ("fault names the profile", "input not found" in out)]


@case("neg-inventory-instrument-absent", 3)
def _c_no_instrument(root):
    """Spawned, not called in-process: the absence to reproduce is of the *sibling file*, which an
    in-process import would satisfy from sys.path.

    The copy sits at the same depth as production so the REPO it derives stays inside this repo, and
    it is deliberately written without gen_screen_inventory.py beside it.  Before the fix this case
    measured rc=1 with an empty stdout and a ModuleNotFoundError-shaped traceback on stderr - the
    same tier a failed judgement uses - so the assertions below are the regression lock.
    """
    dev = os.path.join(root, "depth", ".trae", "scripts", "dev")
    dst = os.path.join(dev, "gen_parity_ledger.py")
    w(dst, open(os.path.join(HERE, "gen_parity_ledger.py"), "rb").read())
    r = subprocess.run([sys.executable, "-X", "utf8", dst, "--no-write"],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    out, err = r.stdout or "", r.stderr or ""
    return r.returncode, [
        ("tier 3, not the case-mismatch tier", r.returncode == 3),
        ("fault names the instrument that could not be loaded",
         "could not load the inventory instrument" in out),
        ("the fault is reported on stdout, not only as a crash", "INSTRUMENT FAULT" in out),
        ("the run prints its own exit-code line", out.strip().endswith("EXITCODE=3")),
        ("no traceback escapes to stderr", "Traceback" not in err),
        ("no ledger comes out of a tree that cannot be measured", "LEDGER" not in out)]


@case("neg-unknown-arg", 2)
def _c_usage(root):
    rc, out = ledger(["--definitely-not-an-option"])
    return rc, [("tier 2 for bad usage", rc == 2), ("no ledger printed", "LEDGER" not in out)]


def _run_root():
    os.makedirs(RUN_ROOT, exist_ok=True)
    return RUN_ROOT


def main():
    if L is None or F is None:
        absent = ", ".join(n for n, m in (("gen_parity_ledger.py", L),
                                          ("screen_inventory_selftest.py", F)) if m is None)
        print("INSTRUMENT FAULT: %s not present next to this script, so no case can run" % absent)
        print("EXITCODE=3")
        return 3
    missing = [a for a in REQUIRED_ATTRS if not hasattr(L, a)]
    if missing:
        print("INSTRUMENT FAULT: gen_parity_ledger.py is missing %s" % ", ".join(missing))
        print("EXITCODE=3")
        return 3
    if len(CASES) != EXPECTED_CASES:
        print("INSTRUMENT FAULT: %d case(s) registered but the literal says %d"
              % (len(CASES), EXPECTED_CASES))
        print("EXITCODE=3")
        return 3
    tiers = {rc for _, rc, _ in CASES}
    if not {0, 1, 2, 3} <= tiers:
        print("INSTRUMENT FAULT: cases do not cover exit tiers 0, 1, 2 and 3 (got %s)"
              % sorted(tiers))
        print("EXITCODE=3")
        return 3
    if os.path.isdir(RUN_ROOT):
        shutil.rmtree(RUN_ROOT)
    bad = 0
    executed = 0
    try:
        for name, want, fn in CASES:
            root = tempfile.mkdtemp(prefix="c-", dir=_run_root())
            try:
                rc, checks = fn(root)
            except L.Fault as exc:
                rc, checks = 3, [("fixture raised a fault the case did not expect", str(exc))]
                bad += 1
                print("CASE %s rc=%d want=%d FAIL (unexpected fault: %s)" % (name, rc, want, exc))
                executed += 1
                continue
            except Exception as exc:
                print("INSTRUMENT FAULT in case %s: %s: %s" % (name, type(exc).__name__, exc))
                print("EXITCODE=3")
                return 3
            executed += 1
            ok = rc == want and all(p for _, p in checks)
            if not ok:
                bad += 1
            print("CASE %-32s rc=%d want=%d %s" % (name, rc, want, "ok" if ok else "FAIL"))
            for label, passed in checks:
                print("  check %-52s %s" % (label, "ok" if passed else "FAIL"))
    finally:
        shutil.rmtree(RUN_ROOT, ignore_errors=True)
    print("cases executed=%d expected=%d failures=%d" % (executed, EXPECTED_CASES, bad))
    if executed != EXPECTED_CASES:
        print("INSTRUMENT FAULT: fewer cases ran than registered")
        print("EXITCODE=3")
        return 3
    print("VERDICT: %s" % ("PASS" if bad == 0 else "FAIL"))
    print("EXITCODE=%d" % (0 if bad == 0 else 1))
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
