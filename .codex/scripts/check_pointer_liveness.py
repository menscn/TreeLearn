"""当前态面上每一条 `文件:行` 声称的形状复算（ROADMAP D13）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/check_pointer_liveness.py
    python -X utf8 .codex/scripts/check_pointer_liveness.py --selftest

为什么需要常驻：加行会把指向该文件的声称整体挪位，这个缺陷类在本仓库登记过三次
（ROADMAP D1、D9，以及片 034 卡片 Gaps ①），而每次应对都是现写一份一次性驱动——
`artifacts/tmp/` 里现存 13 份 `*pointer*.py`，登记时点逐份累加物理行数为 2,660 行
（`ROADMAP.md` D13 的回读命令，读数 `artifacts/tmp/run298-driver-census.txt`），全部随 gitignore 蒸发。
本脚本把其中「形状」那一半升成一条会响的命令，修复器留在
`.codex/scripts/dev/remap_pointers.py`。

判形状，不判语义。一条声称有三种必死形状：路径不在场、行号越界（含起止倒置）、
落在空行上。三者都能机械复算，所以都判。至于「行号还在范围内、但那一行已经不是当初
说的那件事」——那要求声称时就把正文抄进文档，抄正文本身是 `check_source_of_truth.py`
要拦的事；这一类只能由本片那支修复器按 HEAD→工作树的 diff 复算，不在这里判。
把两件事写成一条判据的结果是：要么守门逼人删掉合法的就地改写，要么它悄悄不判而照样绿。

解析只有一份：`harness_check_lib.path_line_citations`，与 `run_evals.py` 的 `pointers-alive`
同一口径（反引号内、带仓库相对前缀）。所以「JVM 栈帧里的 `File.java:45`」「省略写法
`android/core/.../Foo.java:44`」不会被当成声称——前者根本不是声称，后者在当前态面上
确实是死指针（省略掉的目录无法解析），照判。

退出码：0 = 通过；1 = 有 FAIL；3 = `--selftest` 的仪器自身故障（夹具不健康或用例数对不上）。
"""

import io
import os
import shutil
import sys
import tempfile
from contextlib import redirect_stdout

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import (  # noqa: E402
    Checks, blank_landings, citation_problems, enable_utf8_stdout, exists,
    path_line_citations, read_text, repo_path)

INVENTORY = ".codex/context/source-of-truth.md"

# 当前态入口面。这份清单与 `check_source_of_truth.py` 里那份**故意各抄一遍**：共用一份常量的话，
# 把扫描面改窄会同时挪空「守门」和「量它的那把尺」，就没人发现集合空了（片 034 立的口径）。
# `dev/remap_pointers.py` 反过来 **import 本文件**这一份：修复器必须和判据对同一个面动手，
# 各抄一份的结果是修复器会搬动判据根本没在看的地方。
SCAN_PREFIXES = ("AGENTS.md", "README.md", "README_EN.md", ".codex/README.md",
                 ".codex/HARNESS.md", ".codex/ROADMAP.md", ".codex/context/",
                 ".codex/rules/", ".codex/skills/", "Docs/spec/", "Docs/05-technical-design/",
                 "Docs/04-ux-design/design-system/README.md", "android/README.md",
                 "frontend/README.md", "backend/README.md")
# 历史面：只统计、只打印，不判。片 034 的裁定是「历史捕获不回改」，把它的残留判红等于逼人
# 改写上一片的证据；这里保留读数，是为了让「不判」是可见的观测而不是隐藏的宽免（D2 同一处理）。
HISTORY_PREFIXES = (".codex/tasks/", "artifacts/", ".codex/references/", "Docs/intent/",
                    "Docs/01-demand/", "Docs/03-prd-draft/", "Docs/03a-prd-cross-review/",
                    "Docs/06-delivery/", "Docs/reviews/")

# 下限全部写在裸字面量上：由被检集合自己推导等于没有下限（ROADMAP D1 那一族）。
MIN_SCAN_FILES = 40
MIN_CITATIONS = 40
MIN_HIST_CITATIONS = 20
# 清单是这套仪器里最值钱的一批声称：它的行号一旦过期，每一个按清单找权威的人都被指向错的行。
# 只靠「总数够多」遮不住清单声称整体掉出判定集，所以给它单独一条下限。
MIN_INDEX_CLAIMS = 6

SKIP_DIRS = ("__pycache__", "node_modules", "build", "dist", ".git")


def scan_files(root=None, prefixes=SCAN_PREFIXES):
    """按前缀收集当前态（或历史态）面上的 `.md`，返回排序去重的仓库相对路径。"""
    base = repo_path("", root)
    out = []
    for walk_base, dirs, files in os.walk(base):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not d.startswith("build")]
        for name in files:
            if not name.endswith(".md"):
                continue
            rel = os.path.relpath(os.path.join(walk_base, name), base).replace(os.sep, "/")
            if rel.startswith(prefixes):
                out.append(rel)
    return sorted(set(out))


def claims_in(files, root=None):
    """把每个持有者正文里的声称摊平成 [(holder, (被引路径, 起, 止))]。"""
    out = []
    for rel in files:
        for trip in path_line_citations(read_text(rel, root)):
            out.append((rel, trip))
    return out


def group(problems):
    """按 lib 给的原因串分桶，让读数能指明坏在哪一类，而不是印成同一行。"""
    buckets = {"absent": [], "beyond": [], "reversed": []}
    for cite, why in problems:
        key = "absent" if why == "file absent" else ("reversed" if "reversed" in why else "beyond")
        buckets[key].append("{0}:{1}-{2} ({3})".format(cite[0], cite[1], cite[2], why))
    return buckets


def run(root=None):
    c = Checks("check_pointer_liveness")
    files = scan_files(root)
    if not c.check(len(files) >= MIN_SCAN_FILES,
                   "the current-state scan surface holds >= {0} files".format(MIN_SCAN_FILES),
                   "measured={0} floor={1}".format(len(files), MIN_SCAN_FILES)):
        return c.finish()
    items = claims_in(files, root)
    cites = [trip for _, trip in items]
    if not c.check(len(cites) >= MIN_CITATIONS,
                   "the claim parser found >= {0} `路径:行` claims on that surface".format(
                       MIN_CITATIONS),
                   "measured={0} floor={1} holders={2}".format(
                       len(cites), MIN_CITATIONS, len({h for h, _ in items}))):
        return c.finish()

    buckets = group(citation_problems(cites, root))
    c.check(not buckets["absent"], "every claimed path exists in the tree it was written in",
            "dead={0}".format(buckets["absent"][:6]))
    c.check(not buckets["beyond"], "every claimed line is inside the file it names",
            "beyond_eof={0}".format(buckets["beyond"][:6]))
    c.check(not buckets["reversed"], "no claimed range runs backwards",
            "reversed={0}".format(buckets["reversed"][:6]))
    blanks = blank_landings(cites, root)
    c.check(not blanks, "every claimed line lands on content, not on a blank line",
            "landing_blank={0}".format(
                ["{0}:{1}".format(rel, lo) for rel, lo, _ in blanks][:6]))

    index = [trip for holder, trip in items if holder == INVENTORY]
    c.check(len(index) >= MIN_INDEX_CLAIMS,
            "the fact index's own claims are inside the judged set",
            "measured={0} floor={1} index_present={2}".format(
                len(index), MIN_INDEX_CLAIMS, exists(INVENTORY, root)))

    hist_files = scan_files(root, HISTORY_PREFIXES)
    hist = claims_in(hist_files, root)
    hist_cites = [trip for _, trip in hist]
    c.check(len(hist_cites) >= MIN_HIST_CITATIONS,
            "the history face is counted, not silently dropped",
            "measured={0} floor={1} files={2}".format(
                len(hist_cites), MIN_HIST_CITATIONS, len(hist_files)))
    hist_bad = citation_problems(hist_cites, root)
    hist_blank = blank_landings(hist_cites, root)
    hb = group(hist_bad)
    c.note("historical claims are reported, never judged",
           "citations={0} dead={1} beyond_eof={2} reversed={3} landing_blank={4}".format(
               len(hist_cites), len(hb["absent"]), len(hb["beyond"]), len(hb["reversed"]),
               len(hist_blank)))
    print("holders={0} citations={1} judged_dead={2} judged_beyond={3} judged_reversed={4} "
          "judged_blank={5} index_claims={6} hist_citations={7}".format(
          len({h for h, _ in items}), len(cites), len(buckets["absent"]),
          len(buckets["beyond"]), len(buckets["reversed"]), len(blanks), len(index),
          len(hist_cites)))
    return c.finish()


# ---- 正反双向自检：夹具是一棵合成树，不动真仓库 ----

FIXTURE_TARGETS = {
    # Foo.java 的第 7/14/21/28/35 行是空行：没有声称指向它们，所以健康夹具仍全绿；
    # `landing-blank` 用例把第 3 行（被 30 个持有者引用）改成空行来证这条判据会响。
    "android/app/src/main/java/com/treelearn/Foo.java":
        "\n".join(["// line {0}".format(i) if i % 7 else "" for i in range(1, 41)]),
    # dummy.py 不留空行：清单的 10 条声称按奇数行铺开，撞上空行就会让正向夹具自己变红，
    # 那时下面每个反向用例都不作数（夹具不健康 = 仪器故障，不是「用例通过」）。
    ".codex/scripts/dummy.py":
        "\n".join(["# line {0}".format(i) for i in range(1, 31)]),
}
FIXTURE_INDEX_LINES = 10
FIXTURE_HOLDERS = 60
FIXTURE_CARDS = 25


def _write(root, rel, text):
    path = repo_path(rel, root)
    if not os.path.isdir(os.path.dirname(path)):
        os.makedirs(os.path.dirname(path))
    with io.open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)


def _read(root, rel):
    return read_text(rel, root)


def build_fixture(root):
    """造一棵同时越过三个下限的合成树：60 个持有者各 1 条声称、清单 10 条、卡片 25 条。

    声称一律指向 `FIXTURE_TARGETS` 里真实建出来的行，所以健康夹具必须全绿；
    夹具建不出健康态就是仪器故障，后面的每个反向用例都无从作证（同一族：
    「前置条件缺失不得算 PASS」）。
    """
    for rel, text in FIXTURE_TARGETS.items():
        _write(root, rel, text + "\n")
    for i in range(FIXTURE_HOLDERS):
        _write(root, ".codex/rules/r{0:02d}.md".format(i),
               "# 规则 {0}\n\n见 `{1}:3`。\n".format(i, list(FIXTURE_TARGETS)[i % 2]))
    index = ["# 清单\n"]
    for i in range(FIXTURE_INDEX_LINES):
        index.append("- **唯一权威**：`.codex/scripts/dummy.py:{0}`".format(1 + i * 2))
    _write(root, INVENTORY, "\n".join(index) + "\n")
    for i in range(FIXTURE_CARDS):
        _write(root, ".codex/tasks/card{0:02d}.md".format(i),
               "---\nid: T-x\n---\n\n引用 `{0}:5`。\n".format(list(FIXTURE_TARGETS)[i % 2]))


def _mutate_blank_line(root):
    """把 `Foo.java:3` 指向的第 3 行改成空行——形状合法、读者落到空白。"""
    rel = "android/app/src/main/java/com/treelearn/Foo.java"
    lines = _read(root, rel).splitlines()
    lines[2] = ""
    _write(root, rel, "\n".join(lines) + "\n")


CASES = (
    # (用例名, 变异函数, 期望出现的 FAIL 标签, 期望退出码)
    ("path-absent", lambda r: os.remove(repo_path(
        ".codex/scripts/dummy.py", r)), "every claimed path exists", 1),
    ("beyond-eof", lambda r: _patch(r, ".codex/rules/r00.md", ":3`", ":9999`"),
     "every claimed line is inside the file it names", 1),
    ("reversed-range", lambda r: _patch(r, ".codex/rules/r01.md", ":3`", ":9-4`"),
     "no claimed range runs backwards", 1),
    ("landing-blank", _mutate_blank_line, "every claimed line lands on content", 1),
    ("scan-drained", lambda r: _rename_md_suffix(r, ".md9"),
     "the current-state scan surface holds >= 40 files", 1),
    ("matcher-drained", lambda r: _strip_backticks(r),
     "the claim parser found >= 40", 1),
    ("index-claims-drained", lambda r: _write(r, INVENTORY, "# 清单\n\n没有声称。\n"),
     "the fact index's own claims are inside the judged set", 1),
    ("history-drained", lambda r: [os.remove(repo_path(".codex/tasks/card{0:02d}.md".format(i), r))
                                   for i in range(FIXTURE_CARDS)],
     "the history face is counted, not silently dropped", 1),
    # 两条负控：证明判据的**边界**也成立，而不是「凡出现即红」。
    ("stackframe-is-not-a-claim", lambda r: _append(
        r, ".codex/rules/r02.md",
        "\n```\nat androidx.test.platform.io.FileTestStorage.<init>"
        "(android/app/src/main/java/com/treelearn/Foo.java:45)\n```\n"), None, 0),
    ("history-defect-not-judged", lambda r: _append(
        r, ".codex/tasks/card00.md", "\n引用 `.codex/tasks/gone.md:99`。\n"), None, 0),
)


def _patch(root, rel, old, new):
    text = _read(root, rel)
    if old not in text:
        raise RuntimeError("fixture anchor missing: {0} in {1}".format(old, rel))
    _write(root, rel, text.replace(old, new, 1))


def _append(root, rel, extra):
    _write(root, rel, _read(root, rel) + extra)


def _rename_md_suffix(root, suffix):
    for i in range(FIXTURE_HOLDERS):
        os.rename(repo_path(".codex/rules/r{0:02d}.md".format(i), root),
                  repo_path(".codex/rules/r{0:02d}{1}".format(i, suffix), root))


def _strip_backticks(root):
    """把声称的反引号剥掉：形状还在，解析器不再认它是声称——模拟匹配集被挪空。"""
    for i in range(FIXTURE_HOLDERS):
        rel = ".codex/rules/r{0:02d}.md".format(i)
        _write(root, rel, _read(root, rel).replace("`", ""))


def _capture(root):
    buf = io.StringIO()
    with redirect_stdout(buf):
        rc = run(root)
    return rc, buf.getvalue()


def selftest():
    print("Command: python -X utf8 .codex/scripts/check_pointer_liveness.py --selftest")
    work = tempfile.mkdtemp(prefix="tl-pointer-liveness-")
    cases = bad = 0
    try:
        print("Workspace: {0}".format(work))
        build_fixture(work)
        rc, out = _capture(work)
        if rc != 0:
            # 夹具不健康 ⇒ 后面每个用例的判决都不可信；中止并判仪器故障，
            # 不要继续跑 10 个用例再去解释为什么全红。
            print("INSTRUMENT FAULT: healthy fixture copy did not pass (rc={0}); aborting, "
                  "the {1} injected cases would prove nothing".format(rc, len(CASES)))
            print(out.rstrip()[-500:])
            print("OVERALL: INCONCLUSIVE")
            print("EXITCODE=3")
            return 3
        print("BASELINE fixture rc=0 checks_failed=0")
        for name, mutate, label, expect in CASES:
            saved = {}
            try:
                for rel in scan_files(work) + scan_files(work, HISTORY_PREFIXES) + \
                        list(FIXTURE_TARGETS):
                    path = repo_path(rel, work)
                    if os.path.isfile(path):
                        with io.open(path, encoding="utf-8", newline="") as handle:
                            saved[rel] = handle.read()
                mutate(work)
                rc, out = _capture(work)
                cases += 1
                if rc == 3:
                    print("INSTRUMENT FAULT at CASE {0}: rc=3".format(name))
                    return 3
                ok = (rc == expect) and (label is None or label in out)
                print("CASE {0:02d} {1:<24} rc={2} expect={3} -> {4}".format(
                    cases, name, rc, expect, "ok" if ok else "MISMATCH"))
                if not ok:
                    bad += 1
                    if label is not None and label not in out:
                        print("  expected label not present: {0!r}".format(label))
            except RuntimeError as exc:
                print("INSTRUMENT FAULT at CASE {0}: {1}".format(name, exc))
                return 3
            finally:
                for rel, text in saved.items():
                    path = repo_path(rel, work)
                    if not os.path.isdir(os.path.dirname(path)):
                        os.makedirs(os.path.dirname(path))
                    with io.open(path, "w", encoding="utf-8", newline="") as handle:
                        handle.write(text)
        print("SELFTEST cases={0} mismatch={1} expected={2}".format(
            cases, bad, len(CASES)))
    finally:
        shutil.rmtree(work, ignore_errors=True)
    if bad:
        print("OVERALL: FAIL")
        print("EXITCODE=1")
        return 1
    if cases != len(CASES):
        print("OVERALL: INCONCLUSIVE")
        print("EXITCODE=3")
        return 3
    print("OVERALL: PASS")
    print("EXITCODE=0")
    return 0


def main(argv):
    enable_utf8_stdout()
    if "--selftest" in argv:
        return selftest()
    return run()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
