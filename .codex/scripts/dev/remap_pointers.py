"""把当前态面上的 `文件:行` 声称按 HEAD→工作树的 diff 复算一遍（ROADMAP D13 的修复器）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/dev/remap_pointers.py                  # 干跑，什么都不写
    python -X utf8 .codex/scripts/dev/remap_pointers.py --apply          # 落盘
    python -X utf8 .codex/scripts/dev/remap_pointers.py --rules PATH [--apply]

为什么升为常驻：这个动作在本仓库被重写了 13 次（`artifacts/tmp/*pointer*.py`，2900 余行，
全部随 gitignore 蒸发），而每一次的规则都和别人不一样——`mapping()` 抄过两份，`SKIP-FRESH`
到片 034 才补上，`REVIEW` 挡住不写这条从片 033 起就是它自己在用的规矩。这里只留一份。

它管的边界，与它**不管**的那一半：
- **只搬当前态面**：持有者集合 import 自 `check_pointer_liveness.py`，与判据同一个面。
  历史任务卡与 evidence 里的号段是当时的读数，按映射改成就变成对上一片所作所为的假陈述。
- **只认带仓库前缀、写在反引号里的声称**（lib 的 `path_line_citations`，与守门同一口径）：
  裸文件名 `run_all_guards.py:89` 指哪一个文件无法确定，按 basename 全库搜索会把「重名即命中」
  当成真话。这一类不复算，但**打出来**（`alias_unresolved=`），让「没搬」是可见的观测而不是
  隐藏的宽免（ROADMAP D2 同一处理）。
- **候选对不上内容就整批不写**（REVIEW，退出码 1）：把一批声称静默指向别的段落比不修更坏。
- **本片写过的行不参与映射**（`SKIP-FRESH`）：映射方向是 HEAD→工作树，只有对着 HEAD 写下的
  号段才该搬。片 034 的第一版驱动没有这一条，把一句本来就照现树写的 `project-brief.md:61`
  搬成了 `:63`，被守门的「锚点必须在被引那一行开火」当场抓红。
- 就地改写、映射算不出候选的那一条，由 `--rules` 的 `hand` 行带**理由**进来并随读数打印：
  没有理由的 hand 行不认，因为那正好是「用后门把 REVIEW 变成绿」的形状。

退出码：0 = 判定完成（干跑或已写入）；1 = 有 REVIEW 未解，禁止写入；3 = 仪器故障（git 读不到 diff）。
"""

import difflib
import io
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPTS = os.path.dirname(HERE)
sys.path.insert(0, SCRIPTS)
from harness_check_lib import exists, path_line_citations, read_text, repo_path  # noqa: E402
from check_pointer_liveness import scan_files  # noqa: E402

REPO = os.path.dirname(os.path.dirname(SCRIPTS))
HEAD = {}


def git(*args):
    return subprocess.run(["git", "-c", "core.pager=cat"] + list(args), cwd=REPO,
                          capture_output=True, text=True, encoding="utf-8", errors="replace")


def git_show(rel):
    """HEAD 版本的文本；HEAD 里没有这个文件返回 None（未跟踪 ⇒ 整份都是本片新写的）。"""
    if rel not in HEAD:
        proc = git("show", "HEAD:" + rel)
        HEAD[rel] = proc.stdout if proc.returncode == 0 else None
    return HEAD[rel]


def changed_vs_head():
    proc = git("diff", "--name-only", "HEAD")
    if proc.returncode != 0:
        return None
    return {x.strip() for x in (proc.stdout or "").splitlines() if x.strip()}


def mapping(old, new):
    """HEAD 行号 → 工作树行号。等值块逐行对应；替换块映到替换段末行；增删映到邻行。"""
    m = {}
    for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, old, new).get_opcodes():
        if tag == "equal":
            for k in range(i2 - i1):
                m[i1 + k + 1] = j1 + k + 1
        elif tag == "replace":
            for k in range(i2 - i1):
                off = (j2 - j1) - 1 if (i2 - i1) == 1 else min(k, (j2 - j1) - 1)
                m[i1 + k + 1] = j1 + off + 1
        else:
            for k in range(i2 - i1):
                m[i1 + k + 1] = j1 + 1
    return m


def added_lines(rel):
    """本片在该文件里新写或改写过的行号集合；未跟踪文件返回字符串 "FRESH-FILE"。"""
    old = git_show(rel)
    if old is None:
        return "FRESH-FILE"
    new = read_text(rel).split("\n")
    out = set()
    for tag, _i1, _i2, j1, j2 in difflib.SequenceMatcher(None, old.split("\n"), new).get_opcodes():
        if tag != "equal":
            out.update(range(j1 + 1, j2 + 1))
    return out


def ne(lines, a, b):
    return [x.strip() for x in lines[a - 1:b] if x.strip()]


def same_stmt(o, n):
    return o == n or (len(o) >= 25 and o[:25] == n[:25])


def subseq(old_ne, new_ne):
    """旧块能否按顺序嵌进新块；返回 (是否, 内容不同的行对) 供读数打印。"""
    pairs = []
    j = 0
    for o in old_ne:
        while j < len(new_ne) and not same_stmt(o, new_ne[j]):
            j += 1
        if j == len(new_ne):
            return False, pairs
        if o != new_ne[j]:
            pairs.append((o[:40], new_ne[j][:40]))
        j += 1
    return True, pairs


def blank_pad(new, na, nb, claimed_span):
    """声称原本跨 k 行、新位置的内容块够不到 k 行时，把右端扩到紧随的空行上——
    保持声称的行数形状，不把一段代码缩成一行。"""
    while nb - na < claimed_span - 1 and nb < len(new) and not new[nb].strip():
        nb += 1
    return nb


def parse_span(text):
    parts = text.split("-")
    if not parts or not all(p.isdigit() for p in parts) or len(parts) > 2:
        raise SystemExit("rules 里的号段写错了：{0!r}（要 `32` 或 `120-122`）".format(text))
    lo = int(parts[0])
    return (lo, int(parts[1]) if len(parts) == 2 else lo)


def load_rules(path):
    """`--rules` 的两种行（制表符分隔）：
        exclude  持有者  行号  匹配串  理由
        hand     目标    旧号段  新号段  理由
    理由缺失的 hand 行直接丢弃并打出来——静默接受一条没有理由的裁定，等于给自己开后门。
    """
    exclude, hand = [], {}
    if not path:
        return exclude, hand
    with io.open(repo_path(path) if not os.path.isabs(path) else path, encoding="utf-8") as f:
        rows = [r for r in f.read().splitlines() if r.strip() and not r.startswith("#")]
    for row in rows:
        cells = row.split("\t")
        if cells[0] == "exclude" and len(cells) >= 5:
            exclude.append((cells[1], int(cells[2]), cells[3], cells[4]))
        elif cells[0] == "hand" and len(cells) >= 5:
            a, b = parse_span(cells[2]), parse_span(cells[3])
            hand[(cells[1], a[0], a[1])] = (b[0], b[1], cells[4])
        else:
            print("RULES 忽略一行（形状不认识或理由为空）：{0!r}".format(row))
    return exclude, hand


def landed(cite):
    """把声称读到的那一行摆出来——SKIP-FRESH 是一条**拒绝**，而任何拒绝都该附带被拒的东西。

    本片自己就撞上过一次：清单里新增的条目写着 `check_pointer_liveness.py:58`，写完之后再给那个
    文件加了一行，下限常量整体下移一位。持有者那一行是新写的 ⇒ 按规则不搬；而它是**生成型**条目，
    `check_source_of_truth.py` 的「锚点必须在被引那一行开火」只判声明型，于是全仓库没有一个仪器会
    为这条号段转红。打印落点内容就是把这种「形状合法、内容已换」的残余变成人在干跑里能一眼看见的
    一行输出（判不了≠看不见，这是 D13 那条边界的另一半）。
    """
    target, lo = cite[0], cite[1]
    lines = read_text(target).split("\n")
    if not 1 <= lo <= len(lines):
        return "OUT-OF-RANGE(lines={0})".format(len([x for x in lines if x.strip()]))
    return "{0}:{1} {2!r}".format(target, lo, lines[lo - 1].strip()[:56])


def collect_sites(holders, targets, exclude):
    """把当前态面上的声称摊成 [(目标, 起, 止, [(持有者, 行号, 原文)])]，跳过 FRESH 行与 exclude 行。"""
    grouped = {}
    fresh = []
    skipped = []
    for holder in holders:
        added = added_lines(holder)
        for no, line in enumerate(read_text(holder).split("\n"), 1):
            for cite in [t for t in path_line_citations(line) if t[0] in targets]:
                text = "`{0}:{1}`".format(cite[0], cite[1]) if cite[1] == cite[2] else \
                    "`{0}:{1}-{2}`".format(cite[0], cite[1], cite[2])
                hit = [x for x in exclude if x[0] == holder and x[1] == no and x[2] in line]
                if hit:
                    skipped.append((holder, no, text, hit[0][3]))
                    continue
                if added == "FRESH-FILE" or no in added:
                    fresh.append((holder, no, text,
                                  "整份文件是本片新写的" if added == "FRESH-FILE"
                                  else "该行是本片新写/改写过的", cite))
                    continue
                grouped.setdefault(cite, []).append((holder, no, text))
    return grouped, fresh, skipped


def decide(cite, maps, hand):
    """给一条声称算出新号段：返回 (起, 止, 怎么定的)。算不出来返回 (None, None, None)。"""
    target, lo, hi = cite
    mp, new, old = maps[target]
    old_ne = ne(old, lo, hi)
    starts = [i + 1 for i, l in enumerate(new) if l.strip() == (old_ne[0] if old_ne else "")]
    ends = [i + 1 for i, l in enumerate(new) if l.strip() == (old_ne[-1] if old_ne else "")]
    cands = [("diff-map", mp.get(lo), mp.get(hi))]
    if len(starts) == 1 and len(ends) == 1 and starts[0] <= ends[0]:
        cands.append(("anchor", starts[0], ends[0]))
    if len(starts) == 1:
        cands.append(("anchor+span", starts[0], starts[0] + (hi - lo)))
    for want_exact in (True, False):
        for tag, x, y in cands:
            if x is None or y is None or y < x:
                continue
            got = ne(new, x, y)
            if want_exact and got == old_ne:
                return x, blank_pad(new, x, y, hi - lo + 1), tag
            moved = subseq(old_ne, got)
            if not want_exact and moved[0] and got and (
                    got[0] == old_ne[0] or (len(old_ne) == 1 and same_stmt(old_ne[0], got[0]))):
                # 单行声称被**就地改写**时（行号不变、行变长了），逐字相等永远不成立；
                # 放宽到「同一行前缀还在」而不是允许任意换句——多行声称仍然必须首行逐字相同，
                # 否则一批文档引用会被静默指向别的段落。
                return x, y, "{0}(subseq,就地改={1})".format(tag, moved[1])
    if cite in hand:
        na, nb, why = hand[cite]
        return na, nb, "HAND-VERIFIED"
    return mp.get(lo), mp.get(hi), "NO-CANDIDATE-MATCHES"


def count_alias(holders):
    """数一下当前态面里「裸文件名:行」的形状——它们不是 lib 认的声称，只报数不改写。"""
    n = 0
    for holder in holders:
        for token in read_text(holder).split("`"):
            if "/" in token or ":" not in token:
                continue
            head, _, tail = token.partition(":")
            if "." in head and tail.replace("-", "").isdigit():
                n += 1
    return n


def fresh_face_claims(holders):
    """当前态面上指向**HEAD 里没有的文件**的声称——本片新写的号段，复算器无版本可映射。

    为什么单独列：这类声称的行号是照着工作树抄的，抄的那一刻是对的；之后同一片里再给那个文件
    加一行，它就安静地错。判据判不出（形状合法），复算器搬不动（HEAD 没有旧版本可对照），
    清单守门的「锚点必须在被引那一行开火」只判声明型、生成型条目豁免——片 035 自己就产出了一条
    （清单里指向新生成的守门下限常量的号段，`run305-remap-landed.txt` 前后各一次）。
    所以这里把它打印出来让人核对，而不是留成一条没人读的注释。
    """
    out = []
    for holder in holders:
        for no, line in enumerate(read_text(holder).split("\n"), 1):
            for cite in path_line_citations(line):
                if exists(cite[0]) and git_show(cite[0]) is None:
                    out.append((holder, no, cite))
    return out


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")
    apply_changes = "--apply" in argv
    rules = argv[argv.index("--rules") + 1] if "--rules" in argv else None
    exclude, hand = load_rules(rules)

    holders = scan_files()
    changed = changed_vs_head()
    if changed is None:
        print("INSTRUMENT FAULT: `git diff --name-only HEAD` 跑不通，映射的两侧取不到")
        print("EXITCODE=3")
        return 3
    targets = {rel for _, cites in ((h, path_line_citations(read_text(h))) for h in holders)
               for rel, _lo, _hi in cites
               if rel in changed and exists(rel) and git_show(rel) is not None}
    print("holders={0} changed_files={1} targets={2}".format(
        len(holders), len(changed), sorted(targets)))
    fresh_face = fresh_face_claims(holders)
    print("\n==== 指向本片新建文件的声称（无 HEAD 版本可映射，逐条人工核对落点内容）")
    for holder, no, cite in fresh_face:
        print("VERIFY-FRESH-TARGET {0}:{1} — 落点内容={2}".format(holder, no, landed(cite)))
    print("VERIFY-FRESH-TARGET claims={0}".format(len(fresh_face)))
    if not targets:
        print("VERDICT: PASS (nothing to remap)")
        print("EXITCODE=0")
        return 0

    maps = {}
    for rel in sorted(targets):
        old = git_show(rel).split("\n")
        new = read_text(rel).split("\n")
        maps[rel] = (mapping(old, new), new, old)
        print("target {0}: HEAD lines={1} now lines={2}".format(rel, len(old), len(new)))

    grouped, fresh, skipped = collect_sites(holders, targets, exclude)
    print("\n==== 不按映射搬的行（本片对着现树写的，搬了就是把对的改成错的）")
    for holder, no, text, why, cite in fresh:
        print("SKIP-FRESH {0}:{1} {2} — {3} 落点内容={4}".format(
            holder, no, text, why, landed(cite)))
    for holder, no, text, why in skipped:
        print("SKIP-RULES {0}:{1} {2} — {3}".format(holder, no, text, why))
    print("\n==== 声称条目数={0} 站点数={1}".format(
        len(grouped), sum(len(v) for v in grouped.values())))

    table = {}
    review = []
    for cite in sorted(grouped):
        na, nb, how = decide(cite, maps, hand)
        table[cite] = (na, nb)
        sites = grouped[cite]
        if (na, nb) == cite[1:]:
            continue
        tag = "REVIEW" if how == "NO-CANDIDATE-MATCHES" else how.split("(")[0]
        print("\n{0} [{1}] :{2}-{3} -> :{4}-{5}  sites={6}".format(
            cite[0], tag, cite[1], cite[2], na, nb, len(sites)))
        if how.startswith("HAND"):
            print("   理由={0}".format(hand[cite][2]))
        for holder, no, text in sites:
            print("     at {0}:{1}".format(holder, no))
        old_ne = ne(maps[cite[0]][2], cite[1], cite[2])
        new_ne = ne(maps[cite[0]][1], na, nb)
        print("   old_first={0!r}".format(old_ne[0][:72] if old_ne else ""))
        print("   new_first={0!r}".format(new_ne[0][:72] if new_ne else ""))
        print("   old_last ={0!r}".format(old_ne[-1][:72] if old_ne else ""))
        print("   new_last ={0!r}".format(new_ne[-1][:72] if new_ne else ""))
        if how == "NO-CANDIDATE-MATCHES":
            review.append(cite)

    print("\nMOVED 声称={0} SAME 声称={1} SKIP-FRESH 行={2} SKIP-RULES 行={3}".format(
        sum(1 for k, v in table.items() if v != k[1:]),
        sum(1 for k, v in table.items() if v == k[1:]), len(fresh), len(skipped)))
    print("alias_unresolved={0}（裸文件名声称，lib 口径不认，不复算也不改写）".format(
        count_alias(holders)))

    if review:
        print("\nREVIEW needed={0}（候选都对不上内容，先人工读现文再 --apply）".format(len(review)))
        for cite in review:
            print("   {0}:{1}-{2}".format(*cite))
        print("EXITCODE=1")
        return 1

    if not apply_changes:
        print("\nVERDICT: DRY RUN (pass --apply to write)")
        print("EXITCODE=0")
        return 0

    edits = 0
    for cite, (na, nb) in sorted(table.items()):
        if (na, nb) == cite[1:]:
            continue
        for holder, no, text in grouped[cite]:
            lines = read_text(holder).split("\n")
            if text not in lines[no - 1]:
                print("INSTRUMENT FAULT: {0}:{1} 里找不到 {2!r}（同一轮里被谁改过？）".format(
                    holder, no, text))
                print("EXITCODE=3")
                return 3
            new_text = "`{0}:{1}`".format(cite[0], na) if na == nb else \
                "`{0}:{1}-{2}`".format(cite[0], na, nb)
            lines[no - 1] = lines[no - 1].replace(text, new_text, 1)
            with io.open(repo_path(holder), "w", encoding="utf-8", newline="\n") as handle:
                handle.write("\n".join(lines))
            edits += 1
            print("FIX {0}:{1} {2!r} -> {3!r}".format(holder, no, text, new_text))
    print("\nEDITS applied={0}".format(edits))
    print("VERDICT: PASS")
    print("EXITCODE=0")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
