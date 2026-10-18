#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T-2026-10-15-009 提交后回读：DOM 侧仪器的谱系在 git 里能承受到哪一步。

为什么单独一支产物：GREEN 判的是「工作树 vs HEAD 的增量窗口」与「旧路径还在不在」，
前者提交后为空、后者提交后反转。按既有规则，这类读数只能在提交后的产物里判，而且必须
换成与提交状态无关的形状（AGENTS.md 的 [guard] 增量判据条与 [evidence] 时效性状态条）。

与 006 的关键差别（本机读数逐条见产物的阈值行，这里不抄）：009 把前身字节**归档**成
artifacts/evidence/ 下的只读副本，git 在默认阈值下就把「旧路径 → 归档副本」配成改名；而新仪器
文件是档案驱动重写，git 对「这一半有没有前身」的答复**随 --diff-filter 配对阈值而变**（同一阈值
名单里有的档只携带落片那次，有的档补齐移动前那条）。所以这个答案不能当承重判据：本产物把
「全携带」押在归档那一半（命中阈值逐条写进判据行），新仪器那一半的携带读数逐条打印而不判定，
git 承不了的那一半改由两处替代覆盖承担——块 02 的反向对照，与块 03 的字节级归档出处 + 四处正文。
把「某一阈值下没配上」写成「历史缺失」，或把它压成「携带了」，都是失实：两种写法都会让下一片
把这半谱系当成 git 能证明的事。

本文件不得出现字面提交号（历史一被重写就假红）。块 04 用一条扫描自己源码的判据钉住这点，
正控的字节串由代码构造而非写死——否则正控自己就是那条判据要抓的东西。
"""
import hashlib
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
OLD_REL = "artifacts/tmp/verify_1to1.mjs"
NEW_REL = ".trae/scripts/dev/parity/verify_1to1.mjs"
SELFTEST_REL = ".trae/scripts/dev/parity/verify_1to1_selftest.mjs"
GEN_LEDGER_REL = ".trae/scripts/dev/gen_parity_ledger.py"
PRE_COPY_REL = "artifacts/evidence/T-2026-10-15-009-pre-adoption-verify_1to1.mjs"
PRE_COPY = os.path.join(REPO, PRE_COPY_REL.replace("/", os.sep))
TASK = os.path.join(REPO, ".trae", "tasks",
                    "T-2026-10-15-parity-dom-instrument-adoption-009.md")
DOC = os.path.join(REPO, "Docs", "04-ux-design",
                   "知芽-TreeLearn-对话页1比1复刻方案-V1.0.md")
SPEC = os.path.join(REPO, "Docs", "spec", "SPEC-2026-10-15-web-h5-android-parity.md")
LEDGER = os.path.join(REPO, "artifacts", "parity", "ledger.md")
EV = os.path.join(REPO, "artifacts", "evidence")
ARTIFACTS = ["T-2026-10-15-009-red.txt", "T-2026-10-15-009-green.txt",
             "T-2026-10-15-009-mutation.txt"]
OUT = os.path.join(EV, "T-2026-10-15-009-post-commit.txt")
SELF = os.path.abspath(__file__)
BLOCKS = []
BODY = []
FAULTS = []

THRESHOLDS = ["", "-M40%", "-M10%", "-M1%"]      # 空串 = git 默认阈值
MIN_LINEAGE = 3          # 替代谱系处数下限：与下面清单条数无关的裸字面量
MIN_BLOCKS = 9           # 判定块数下限：裸字面量，少一块即判 3
MIN_BODY_LINES = 40      # 正文行数下限：裸字面量，防「块 09 扫了一份空正文」
HEX_WORD = re.compile(r"\b[0-9a-f]{7,40}\b")


def emit(fh, text):
    BODY.append(text)
    fh.write(text + "\n")
    fh.flush()
    print(text, flush=True)


def run(cmd):
    r = subprocess.run(cmd, cwd=REPO, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    return r.returncode, (r.stdout or "") + (r.stderr or "")


def run_bytes(cmd):
    r = subprocess.run(cmd, cwd=REPO, capture_output=True)
    return r.returncode, r.stdout, r.stderr


def block(fh, idx, label, checks):
    bad = [t for t, ok in checks if not ok]
    emit(fh, "===== block %02d - %s =====" % (idx, label))
    for t, ok in checks:
        emit(fh, ("  ok   " if ok else "  FAIL ") + t)
    got = 0 if not bad else 1
    BLOCKS.append((idx, label, 0, got))
    emit(fh, "  block %02d: want=0 got=%d%s" % (idx, got, "" if not bad else " <- MISMATCH"))
    emit(fh, "")


def commits_for(path, thresh=None):
    """该路径在历史里的提交集合。--follow 只许配单个 pathspec（git 的硬前提）。"""
    cmd = ["git", "log", "--follow"]
    if thresh:
        cmd.append(thresh)
    cmd += ["--format=%H", "--", path]
    rc, out = run(cmd)
    return rc, [l.strip() for l in out.splitlines() if l.strip()]


def surface_snapshot():
    """本片能影响的范围之外的生产面读数：共享工作树里「相对 HEAD 干净」会把别人的在飞
    工作算成本片缺陷，因此这里只取「本次运行窗口内的位移」（起始快照与结束读数比对）。"""
    paths = {}
    for cmd in (["git", "-c", "core.quotepath=false", "diff", "--name-only", "HEAD"],
                ["git", "-c", "core.quotepath=false", "ls-files", "--others", "--exclude-standard"]):
        rc, out = run(cmd)
        if rc != 0:
            return None
        for line in out.splitlines():
            p = line.strip().strip('"')
            if p.startswith(("android/", "backend/", "frontend/")):
                fp = os.path.join(REPO, p.replace("/", os.sep))
                try:
                    paths[p] = hashlib.sha256(open(fp, "rb").read()).hexdigest()
                except OSError:
                    paths[p] = "UNREADABLE"
    return paths


def main():
    for p in (PRE_COPY, TASK, DOC, SPEC, LEDGER):
        if not os.path.isfile(p):
            print("FAULT 缺少被核文件: %s" % p)
            return 3
    start = surface_snapshot()
    if start is None:
        print("FAULT 生产面起始快照没跑成（git 返回非 0），窗口增量判据因此无法判定")
        return 3

    fh = open(OUT, "w", encoding="utf-8", newline="\n")
    try:
        rc_new, _, _ = run_bytes(["git", "cat-file", "-e", "HEAD:%s" % NEW_REL])
        rc_old, _, _ = run_bytes(["git", "cat-file", "-e", "HEAD:%s" % OLD_REL])
        rc_pre, _, _ = run_bytes(["git", "cat-file", "-e", "HEAD:%s" % PRE_COPY_REL])
        rc_head, head_out = run(["git", "rev-parse", "HEAD"])
        rc_st, _, _ = run_bytes(["git", "cat-file", "-e", "HEAD:%s" % SELFTEST_REL])
        emit(fh, "# T-2026-10-15-009 提交后回读：DOM 侧仪器的谱系承受到哪一步")
        emit(fh, "# 生成命令：python -X utf8 artifacts/tmp/gen-009-post-commit.py")
        emit(fh, "# 前置：该片已提交。块 01 检查该前置；不成立判 3（现在还不能认证），不判 0——")
        emit(fh, "#   把「还没提交」读成「提交过且没问题」是假见证。")
        emit(fh, "# HEAD 取 git rev-parse HEAD 的现场读数（复算同命令），提交号不抄进正文；")
        emit(fh, "#   因此这里只打印它的长度——块 04 扫本文件源码、块 09 扫写完的正文，"
             "两处都会把任何 7 位以上的裸十六进制词判红。")
        emit(fh, "  HEAD 读数长度=%d 字符（复算：git rev-parse HEAD；值不抄进正文）" % len(head_out.strip()))
        emit(fh, "# 指纹名单的边界：本运行只写本产物，因此本产物不在任何判定前名单里；其余被核对象各自由")
        emit(fh, "#   块 05（仪器与自检、已落盘台账）、块 03（前身归档副本字节）、块 06（HEAD 里的改动面）、")
        emit(fh, "#   块 08（三份同片证据）现场复核。要核对某个当前字节就用 sha256sum 该路径。")

        # 01 前置：移动确实进了历史。
        block(fh, 1, "precondition: the adoption is in history", [
            ("新仪器路径在 HEAD 里（git cat-file -e rc=0）", rc_new == 0),
            ("自检脚本也在 HEAD 里（它是被提交的仪器的一部分，不是临时件）", rc_st == 0),
            ("前身归档副本在 HEAD 里（删除必须留下可回算的出处，而不是只删）", rc_pre == 0),
            ("旧路径已从 HEAD 消失（rc 非 0 才是移动而不是复制）", rc_old != 0),
            ("git rev-parse HEAD rc=0", rc_head == 0),
        ])
        if rc_new != 0 or rc_pre != 0:
            FAULTS.append("新路径或归档副本不在 HEAD 里——本片尚未提交，历史判据无法判定（不是缺陷复发）")

        # 02 携带：集合包含 + 阈值写进判据行；两条路径分开量，因为 009 的 git 配对落在归档那一半。
        rc_old_log, old_commits = commits_for(OLD_REL)
        emit(fh, "  旧路径（%s）在历史里拥有的提交=%d" % (OLD_REL, len(old_commits)))
        carry = {}
        for target in (PRE_COPY_REL, NEW_REL):
            for t in THRESHOLDS:
                rc_f, followed = commits_for(target, thresh=(t or None))
                fs = set(followed)
                carry[(target, t or "default")] = (
                    rc_f, [c for c in old_commits if c in fs],
                    [c for c in old_commits if c not in fs], len(followed))
                emit(fh, "  %-72s 阈值 %-8s follow 集合=%d 携带=%d/%d 缺失=%d rc=%d"
                     % (target, t or "default", len(followed),
                        len(carry[(target, t or "default")][1]), len(old_commits),
                        len(carry[(target, t or "default")][2]), rc_f))
        pre_full = [k for k, v in carry.items()
                    if k[0] == PRE_COPY_REL and v[0] == 0 and old_commits and not v[2]]
        new_full = [k for k, v in carry.items()
                    if k[0] == NEW_REL and v[0] == 0 and old_commits and not v[2]]
        # 移动/删除那次提交按**角色**求（不是按条数），因为反向对照要区分「移动前」与「移动那次」
        # 两半；提交号只在这里求、不抄进正文。
        rc_del, del_out = run(["git", "-c", "core.quotepath=false", "log", "--format=%H",
                               "--diff-filter=RD", "--name-status", "--", OLD_REL])
        del_lines = [l for l in del_out.splitlines()
                     if l[:1] in ("D", "R") and "\t" in l]
        del_id = next((l.strip() for l in del_out.splitlines()
                       if re.fullmatch(r"[0-9a-f]{40}", l.strip())), "")
        same_before_move = None
        same_at_move = None
        if del_id:
            arc = hashlib.sha256(open(PRE_COPY, "rb").read()).hexdigest()
            rc_blob, blob, _ = run_bytes(["git", "show", "%s^:%s" % (del_id, OLD_REL)])
            same_before_move = rc_blob == 0 and hashlib.sha256(blob).hexdigest() == arc
            # 正控：把同一比较挪到「移除那次提交本身」上——那里这条路径已被删掉，字节读不到。
            # 两条给出不同答案才说明上面那条押的是「移动前的字节」，而不是一条恒真的哈希比较。
            rc_b2, blob2, _ = run_bytes(["git", "show", "%s:%s" % (del_id, OLD_REL)])
            same_at_move = rc_b2 == 0 and hashlib.sha256(blob2).hexdigest() == arc

        # 反向对照：同一次提交里新增的无关文件（自检脚本）**必须不携带移动前的提交**。
        # 003 的教训是「移动那次提交同时出现在新旧两条路径上」，所以这里把旧路径的提交集
        # 先按角色切成「移动前」与「移动那次」两半：判据押在移动前那一半上，
        # 「有任何历史」就满足不了它。
        rc_ctl, ctl_commits = commits_for(SELFTEST_REL)
        ctl_set = set(ctl_commits)
        pre_move = [c for c in old_commits if c != del_id]
        ctl_carried = [c for c in pre_move if c in ctl_set]
        # 同一份数据喂给**未切分**的旧口径（「对照文件只要有几条提交落在旧路径的提交集里就算
        # 携带」）必须给出非零命中——那正是 003 实测到的假过形状。两条口径答案不同，才证明
        # 「按角色切」这句不是自述而是当场量出来的。
        ctl_naive = [c for c in old_commits if c in ctl_set]
        emit(fh, "  旧路径提交切分：移动前=%d 移动/删除那次=%d（按角色切，不写死提交号）"
             % (len(pre_move), len(old_commits) - len(pre_move)))
        emit(fh, "  反向对照（同片新增的自检脚本，默认阈值）携带移动前提交=%d/%d —— 必须为 0，"
             "否则「携带」这条判据只是在说「这条路径有历史」" % (len(ctl_carried), len(pre_move)))
        emit(fh, "  同一对照按未切分口径命中=%d（必须 >0：把判据退回「有任何历史」时这里就是假过）"
             % len(ctl_naive))

        emit(fh, "  新仪器路径由 git 全携带的阈值=%s（本机=%s）。**这一条只打印、不当判据**：上面逐条阈值行"
             "显示 git 对「这一半有没有前身」的答复随阈值改变，而一条会随阈值或内容反转的读数当判据就是"
             "拿时效性状态当判据。git 承不了的那一半由两处替代覆盖承重：块 02 的反向对照（同片新增的无关"
             "文件必须**不**携带移动前提交）与块 03 的字节级归档出处 + 四处正文。"
             % ([k[1] for k in new_full] or "无", NEW_REL))

        block(fh, 2, "history carry measured as set inclusion, threshold named", [
            ("旧路径在历史里确有提交（集合非空，否则「包含」是空见证）", len(old_commits) > 0),
            ("对旧路径的 git log 跑成（rc=0）", rc_old_log == 0),
            ("每个阈值对归档副本与新路径各自的 --follow 都真的跑成（rc 全为 0，空集合不冒充 0 携带）",
             all(v[0] == 0 for v in carry.values())),
            ("归档那一半在某个写明阈值下**全携带**旧路径的每条提交（命中阈值=%s）"
             % ([k[1] for k in pre_full] or "无"), bool(pre_full)),
            ("反向对照为 0 携带，且移动前集合非空（自检脚本 %d 条，移动前=%d，命中=%d）"
             % (len(ctl_commits), len(pre_move), len(ctl_carried)),
             rc_ctl == 0 and pre_move and not ctl_carried),
            ("同一对照按未切分口径会有命中（=%d，>0）——它证明上面那条押的是「移动前那一半」，"
             "不是「这条路径有没有历史」" % len(ctl_naive), len(ctl_naive) > 0),
        ])

        # 03 替代谱系：git 承不了的那一半落在哪几处正文。
        src_tool = open(os.path.join(REPO, NEW_REL.replace("/", os.sep)), encoding="utf-8").read()
        task_src = open(TASK, encoding="utf-8").read()
        doc_src = open(DOC, encoding="utf-8").read()
        spec_src = open(SPEC, encoding="utf-8").read()
        lineage = [
            ("新仪器页首写明前身路径", ("前身 %s" % OLD_REL) in src_tool),
            ("009 任务卡同时写归档副本路径与前身路径",
             PRE_COPY_REL in task_src and OLD_REL in task_src),
            ("复刻方案仍保留前身路径那一行并注明 009 收编",
             OLD_REL in doc_src and "T-2026-10-15-009" in doc_src),
            ("规格 §10 把这一支写成「已由 009 迁入同一目录」",
             "verify_1to1.mjs" in spec_src and "T-2026-10-15-009" in spec_src),
        ]
        named = [t for t, ok in lineage if not ok]
        emit(fh, "  移动/删除那次提交的记录行=%s（只打印形状，提交号不抄）"
             % (del_lines[:1] or "(无)"))
        emit(fh, "  归档副本哈希只以复算命令出现（复算：sha256sum %s 与 git show <移除那次提交>^:%s）"
             % (PRE_COPY_REL, OLD_REL))
        block(fh, 3, "lineage recorded where git cannot carry it", [
            ("四处指向前身的面都在位（缺: %s）" % (named or "无"), not named),
            ("替代谱系处数 >= MIN_LINEAGE=%d（实测 %d）" % (MIN_LINEAGE, len(lineage)),
             len(lineage) >= MIN_LINEAGE),
            ("归档的前身副本与「移除那次提交的父提交」上的字节逐字节相同=%s" % same_before_move,
             same_before_move is True),
            ("同一比较挪到「移除那次提交本身」上必须为假=%s（两条答案不同才说明上面那条押的是"
             "移动前的字节，而不是一条恒真的哈希比较）" % same_at_move, same_at_move is False),
            ("移除那次提交被 git 记为改名或删除，且 git 跑成、id 形状为 40 位十六进制"
             "（rc=%d 命中=%s；空集合与跑坏分不开就是假见证）" % (rc_del, bool(del_id)),
             rc_del == 0 and bool(del_id)),
        ])

        # 04 提交号不得写死：扫本文件源码，并让正控自己会响。产物正文的全量扫描放在块 09——
        # 在这里扫 BODY 只能扫到截至块 04 的前缀，后面五块写出的行没有任何东西复核，
        # 那是「匹配集为空必须判 FAIL」这一族在扫描时机上的形态。
        src_self = open(SELF, encoding="utf-8").read()
        offenders = [m.group(0) for m in HEX_WORD.finditer(src_self)]
        control = "ab" * 20                       # 由代码构造，源码里因此没有 40 位十六进制词
        caught_control = bool(HEX_WORD.search(control))
        emit(fh, "  源码扫描命中=%d ；合成正控（40 位形状）被同一正则命中=%s"
             % (len(offenders), caught_control))
        block(fh, 4, "no literal commit id baked in; the scan itself rings", [
            ("本文件源码里没有 7 位以上的裸十六进制词（命中: %s）" % (offenders[:3] or "无"),
             not offenders),
            ("扫描对合成的 40 位提交号形状会响（否则本条与块 09 都是空见证）", caught_control),
        ])

        # 05 提交后仪器仍然可用：自检跑通、台账那一行随树自愈。
        rc_self, out_self = run(["node", os.path.join(REPO, SELFTEST_REL.replace("/", os.sep)),
                                 "--selftest"])
        passed = "SELFTEST PASS" in out_self and "failures=0" in out_self
        rc_lg, out_lg = run(["python", "-X", "utf8",
                             os.path.join(REPO, GEN_LEDGER_REL.replace("/", os.sep)), "--no-write"])
        adopted = bool(re.search(r"行为仪器（verify_1to1\.mjs）：adopted\b", out_lg))
        fresh = "清单新鲜度：一致" in out_lg
        stale = ("清单新鲜度：STALE" in out_lg) or ("INVENTORY STALE" in out_lg)
        ledger_disk = open(LEDGER, encoding="utf-8").read()
        disk_adopted = bool(re.search(r"行为仪器（verify_1to1\.mjs）：adopted\b", ledger_disk))
        verdict_line = [l for l in out_self.splitlines() if "SELFTEST" in l]
        emit(fh, "  自检读数（只打印，例数与变异条数不抄进正文，复算同命令的末行）：%s"
             % (verdict_line[-1].strip() if verdict_line else "(没有 SELFTEST 判行)"))
        block(fh, 5, "the instrument still runs after the move", [
            ("node 自检 rc=0 且打印 SELFTEST PASS 与 failures=0", rc_self == 0 and passed),
            ("台账生成器现场读到的行为仪器那一行是 adopted（随树自愈，不是人写的现位置声明）", adopted),
            ("已落盘台账里那一行也写 adopted（磁盘字节与生成器一致=%s）" % disk_adopted, disk_adopted),
            ("清单新鲜度与退出码互不矛盾（一致=%s STALE=%s rc=%d；新鲜度是维护状态，两态各自可判）"
             % (fresh, stale, rc_lg), (fresh and rc_lg == 0) or (stale and rc_lg != 0)),
        ])

        # 06 落片那次提交改动面不含应用生产路径（这条替代 GREEN 里那条提交后失效的窗口判据）。
        rc_add, add_out = run(["git", "-c", "core.quotepath=false", "log", "-1", "--format=%H",
                               "--diff-filter=A", "--name-status", "--", NEW_REL])
        add_id = next((l.strip() for l in add_out.splitlines()
                       if re.fullmatch(r"[0-9a-f]{40}", l.strip())), "")
        surfaces = []
        if add_id:
            rc_show, show_out = run(["git", "-c", "core.quotepath=false", "show",
                                     "--format=", "--name-only", add_id])
            surfaces = [l.strip() for l in show_out.splitlines() if l.strip()]
        app_paths = [p for p in surfaces if p.startswith(("android/", "backend/", "frontend/"))]
        emit(fh, "  落片那次提交改动面=%d 个路径；其中应用生产路径=%d %s"
             % (len(surfaces), len(app_paths), app_paths or "[]"))
        block(fh, 6, "the landing commit touches no application production path", [
            ("新增该仪器的那次提交被 git 找到（rc=0 且 id 形状为 40 位十六进制）",
             rc_add == 0 and bool(add_id)),
            ("改动面读到了路径（非空集合，否则下面两条是空见证）", len(surfaces) > 0),
            ("改动面不含 android/、backend/、frontend/ 任何路径（命中: %s）" % (app_paths or "无"),
             not app_paths),
            ("改动面确实包含仪器、自检、三份证据与归档副本",
             all(any(p.endswith(t) for p in surfaces) for t in
                 (NEW_REL, SELFTEST_REL, "009-red.txt", "009-green.txt",
                  "009-mutation.txt", PRE_COPY_REL))),
        ])

        # 07 窗口增量：本运行没动生产面，且判据自己会响（内存正控）。
        end = surface_snapshot()
        if end is None:
            FAULTS.append("生产面结束快照没跑成，窗口增量判据无法判定")
            block(fh, 7, "production surface did not move during this run", [
                ("结束快照跑成（不成立即判 3，不与「用例不符」共用退出码）", False)])
        else:
            moved = sorted([p for p in set(list(start) + list(end))
                            if start.get(p) != end.get(p)])
            fake = dict(start)
            fake["android/src/mem-positive-control.java"] = "clean-at-start"
            moved_ctl = sorted([p for p in set(list(fake) + list(end))
                                if fake.get(p) != end.get(p)])
            names_ctl = "android/src/mem-positive-control.java" in moved_ctl
            emit(fh, "  起始脏集=%d 结束脏集=%d movers=%s 正控=%s"
                 % (len(start), len(end), moved or "[]", names_ctl))
            block(fh, 7, "production surface did not move during this run", [
                ("本次运行窗口内生产面没有位移（movers=%s）" % (moved or "[]"), not moved),
                ("本来干净的路径在窗口内变脏必须被点名（内存正控命中=%s，否则增量是空守卫）"
                 % names_ctl, names_ctl),
            ])

        # 08 三份同片证据的判定行仍然成立（本运行只读它们）。
        # 这三份的**末行**是编码闸门行而不是 OVERALL：生成器写完 OVERALL 之后才追加闸门，
        # 本产物自己也是这个形状。所以判据取「最后一条 OVERALL_EXITCODE= 行」并要求其后
        # 只允许闸门行——按末行硬比会把设计内的闸门读成缺陷（第一版实测 got=1 就是这么来的）。
        ver = {}
        for name in ARTIFACTS:
            raw = open(os.path.join(EV, name), "rb").read()
            try:
                raw.decode("utf-8", "strict")
                ok_utf8 = True
            except UnicodeDecodeError:
                ok_utf8 = False
            lines = [l.strip() for l in raw.decode("utf-8", "replace").rstrip().splitlines()]
            hits = [i for i, l in enumerate(lines) if l.startswith("OVERALL_EXITCODE=")]
            last = lines[hits[-1]] if hits else "(没有 OVERALL_EXITCODE= 行)"
            after = lines[hits[-1] + 1:] if hits else []
            ok_tail = bool(hits) and last == "OVERALL_EXITCODE=0" and all(
                a.startswith("DECODE ok") for a in after)
            ver[name] = (ok_utf8, raw.count(b"\r"), ok_tail, last, after)
            emit(fh, "  %-40s utf8=%s cr=%d 尾读=%s 其后 %d 行（须全是闸门行）"
                 % (name, ok_utf8, ver[name][1], last, len(after)))
        block(fh, 8, "the three 009 artefacts still end green and LF-only", [
            ("三份证据各自最后一条 OVERALL_EXITCODE= 行都是 0，且其后只出现编码闸门行（缺: %s）"
             % ([n for n, v in ver.items() if not v[2]] or "无"),
             all(v[2] for v in ver.values())),
            ("三份证据都可严格 UTF-8 解码（缺: %s）"
             % ([n for n, v in ver.items() if not v[0]] or "无"), all(v[0] for v in ver.values())),
            ("三份证据的 carriage return 计数都是 0（.gitattributes 已把它们钉成 LF）",
             all(v[1] == 0 for v in ver.values())),
        ])

        # 09 正文扫描：只有到这一步 BODY 才是判定块的全部正文（块 01-08 已写完）。
        # 正控用**本次真实读到的**移动/删除那次提交号注入同一正文，因此它证明的是「这个扫描
        # 对本产物真正可能犯的那个错」会响，而不是「正则会认某种形状」。
        # 覆盖范围要说明白：本块只扫到判定块为止的正文，其后的汇总段与 OVERALL 行由**收尾闸门**
        # 对写出的字节整体再扫一遍（末行的 commit-shaped-tokens=0 就是它的读数），
        # 两块合起来才是「整份产物没有提交号」——写成一处就是失实引用。
        body_lines = list(BODY)
        body_hex = [m.group(0) for m in HEX_WORD.finditer("\n".join(body_lines))]
        injected = "\n".join(body_lines + ["  正控注入：%s" % del_id])
        ctl_new = sorted({m.group(0) for m in HEX_WORD.finditer(injected)} - set(body_hex))
        emit(fh, "  已写出正文行数=%d 下限=%d 命中=%d ；注入真实提交号后新增命中=%d（必须 ≥1，否则本扫描是空见证）"
             % (len(body_lines), MIN_BODY_LINES, len(body_hex), len(ctl_new)))
        block(fh, 9, "the judgement blocks' body carries no commit-shaped token", [
            ("正文确实写到了下限（行数=%d，MIN_BODY_LINES=%d）——扫一份空正文不算通过"
             % (len(body_lines), MIN_BODY_LINES), len(body_lines) >= MIN_BODY_LINES),
            ("块 01-08 的正文里没有 7 位以上的裸十六进制词（命中: %s；汇总段由收尾闸门覆盖）"
             % (body_hex[:3] or "无"), not body_hex),
            ("同一扫描对注入的真实移动/删除那次提交号会响（新增命中=%d）" % len(ctl_new),
             bool(ctl_new)),
        ])

        total = 3 if FAULTS else (1 if any(w != g for _, _, w, g in BLOCKS) else 0)
        if len(BLOCKS) < MIN_BLOCKS:
            print("FLOOR FAULT: 判定块只有 %d 块，下限是 %d——少一块就是「少验一项而全绿」"
                  % (len(BLOCKS), MIN_BLOCKS))
            total = 3
        emit(fh, "===== 汇总 =====")
        emit(fh, "  blocks=%d floor=%d expectation_entries=%d faults=%d"
             % (len(BLOCKS), MIN_BLOCKS, len(BLOCKS), len(FAULTS)))
        for f in FAULTS:
            emit(fh, "  FAULT %s" % f)
        for idx, label, want, got in BLOCKS:
            emit(fh, "  block %02d %-46s want=0 got=%d %s"
                 % (idx, label[:46], got, "ok" if want == got else "MISMATCH"))
        fh.write("OVERALL_EXITCODE=%d\n" % total)
        fh.flush()
    finally:
        fh.close()

    raw = open(OUT, "rb").read()
    try:
        raw.decode("utf-8", "strict")
        utf8_ok, cr = True, raw.count(b"\r")
    except UnicodeDecodeError as e:
        utf8_ok, cr = False, raw.count(b"\r")
        print("UTF8 GATE: %s" % e)
    tail_hex = [m.group(0) for m in HEX_WORD.finditer(raw.decode("utf-8", "replace"))]
    if utf8_ok and cr == 0 and not tail_hex:
        # 追加的这行是纯 ASCII：向已判定合法的字节串追加纯 ASCII 不可能产生非法起始字节。
        with open(OUT, "a", encoding="utf-8", newline="\n") as ap:
            ap.write("DECODE ok utf8=True cr=0 commit-shaped-tokens=0\n")
        raw = open(OUT, "rb").read()
    else:
        print("DECODE FAIL utf8=%s cr=%d commit-shaped-tokens=%d %s"
              % (utf8_ok, cr, len(tail_hex), tail_hex[:3]))
    print("DECODE gate utf8=%s cr=%d bytes=%d lines=%d sha256=%s"
          % (utf8_ok, cr, len(raw), raw.count(b"\n"), hashlib.sha256(raw).hexdigest()))
    print("  复算：sha256sum artifacts/evidence/T-2026-10-15-009-post-commit.txt")
    return total if (utf8_ok and cr == 0 and not tail_hex) else 3


if __name__ == "__main__":
    sys.exit(main())
