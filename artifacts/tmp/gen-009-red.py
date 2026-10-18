#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T-2026-10-15-009 RED 证据生成器：DOM 侧闭环量测仪器收编前的缺口复现。

判的是「收编前的缺口确实存在」，所以每块 want = 旧形态的缺陷读数；
OVERALL_EXITCODE=0 表示缺口已如实复现，**不**表示旧仪器可用，也不表示本片已绿
（绿在 T-2026-10-15-009-green.txt）。

旧仪器本体先落到 artifacts/evidence/ 成为附件：本产物要靠那份字节取样，而
artifacts/tmp/verify_1to1.mjs 这个路径在同一次提交里就不存在了（时效性状态不得
当判据，见 AGENTS.md 失败案例库 2026-10-16 [evidence]）。

用 Python 而不是 bash 写：本仓库已记录过「双引号 echo 里的反引号被当命令替换」
「paste 只取多字节分隔符首字节」两类证据文件损坏形态。

对照面（新仪器）是**现场读来的**，不是抄来的：词表键集合由 node 真的 import 那个模块
取 Object.keys(DOM_CHECKS)，而不是正则扫源码——扫法与生产路径不同形的对照是一条假见证。
"""
import hashlib
import json
import os
import re
import socket
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
EV = os.path.join(REPO, "artifacts", "evidence")
PAR = os.path.join(REPO, ".trae", "scripts", "dev", "parity")
OLD_COPY = os.path.join(EV, "T-2026-10-15-009-pre-adoption-verify_1to1.mjs")
OLD_IN_HEAD = "artifacts/tmp/verify_1to1.mjs"
NEW_TOOL = os.path.join(PAR, "verify_1to1.mjs")
NEW_BATT = os.path.join(PAR, "verify_1to1_selftest.mjs")
LAYER = os.path.join(PAR, "parity_cdp.mjs")
PROFILE = os.path.join(PAR, "screens.json")
PROTOTYPE = os.path.join(REPO, "Docs", "04-ux-design", "知芽-TreeLearn-高保真原型-v1.html")
OUT = os.path.join(EV, "T-2026-10-15-009-red.txt")
BLOCKS = []
FAULTS = []
HDR = []

CALL_TIMEOUT = 180   # 单次子进程上限：超时必须成为仪器故障（3），不能是一次看不见的等待

MIN_BLOCKS = 6       # 判定块条数下限：与下面代码无关的裸字面量（空表让「全绿」成空见证）


def emit(fh, text):
    fh.write(text + "\n")
    fh.flush()      # 被中断也要保住已写出的行，否则中断后的产物是空文件
    print(text, flush=True)


def emit_head(fh, text):
    HDR.append(text)
    emit(fh, text)


def sha(p):
    b = open(p, "rb").read()
    return len(b), b.count(b"\n"), hashlib.sha256(b).hexdigest()


def run(cmd, cwd=REPO):
    try:
        r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True,
                           encoding="utf-8", errors="replace", timeout=CALL_TIMEOUT)
    except subprocess.TimeoutExpired:
        FAULTS.append("子进程超时 %ds: %s" % (CALL_TIMEOUT, " ".join(str(c) for c in cmd)[:120]))
        print("FAULT %s" % FAULTS[-1], file=sys.stderr, flush=True)
        return 255, ""
    return r.returncode, (r.stdout or "") + (r.stderr or "")


def read_lines(p):
    with open(p, "rb") as f:
        return f.read().decode("utf-8", "replace").splitlines()


def port_open(port):
    """9222 此刻是否可连——旧仪器把端口写死后，它的读数就取决于本机有没有别的东西占着它。

    这是**环境竞态**，只打印供读者解释 rc，不当判据（既有做法：一个窗口绿一个窗口红
    不是回归复发，是「此刻无法认证」）。
    """
    s = socket.socket()
    s.settimeout(0.6)
    try:
        return s.connect_ex(("127.0.0.1", port)) == 0
    finally:
        s.close()


def block(fh, idx, label, checks):
    """checks: list of (assertion_text, ok_bool)。want 恒为 0 = 「该缺口已如实复现」。"""
    emit(fh, "===== block %02d - %s =====" % (idx, label))
    bad = [t for t, ok in checks if not ok]
    for t, ok in checks:
        emit(fh, ("  ok   " if ok else "  FAIL ") + t)
    got = 0 if not bad else 1
    BLOCKS.append((idx, label, 0, got))
    emit(fh, "  block %02d: want=0 got=%d%s" % (idx, got, "" if not bad else " <- MISMATCH"))
    emit(fh, "")


def main():
    os.makedirs(EV, exist_ok=True)
    if not os.path.isfile(OLD_COPY):
        rc, out = run(["git", "-c", "core.quotepath=false", "show", "HEAD:%s" % OLD_IN_HEAD])
        if rc != 0:
            print("FAULT: 既没有归档副本，HEAD 里也没有 %s 可取: %s" % (OLD_IN_HEAD, out[-200:]))
            return 3
        open(OLD_COPY, "w", encoding="utf-8", newline="\n").write(out)
    for p in (OLD_COPY, NEW_TOOL, NEW_BATT, LAYER, PROFILE, PROTOTYPE):
        if not os.path.isfile(p):
            print("FAULT: 缺少 %s" % p)
            return 3

    fh = open(OUT, "w", encoding="utf-8", newline="\n")
    try:
        emit(fh, "# T-2026-10-15-009 RED：1:1 闭环量测的 DOM 侧仪器收编前跑不动、也判不了")
        emit(fh, "# 生成命令：python -X utf8 artifacts/tmp/gen-009-red.py")
        emit(fh, "# 被核文件（写入本产物之前的字节）：")
        reads = {}
        for name, p in (("pre-adoption-verify_1to1.mjs", OLD_COPY),
                        ("verify_1to1.mjs(新路径)", NEW_TOOL),
                        ("verify_1to1_selftest.mjs", NEW_BATT),
                        ("parity_cdp.mjs", LAYER),
                        ("screens.json", PROFILE)):
            n, l, s = sha(p)
            reads[name] = (n, l, s)
            emit_head(fh, "#   %s bytes=%d lines=%d sha256=%s" % (name, n, l, s))
        n_proto, l_proto, s_proto = sha(PROTOTYPE)
        reads["高保真原型-v1.html"] = (n_proto, l_proto, s_proto)
        emit_head(fh, "#   高保真原型-v1.html bytes=%d lines=%d sha256=%s" % (n_proto, l_proto, s_proto))
        emit(fh, "# 判读约定：本产物每块 want=0 表示「该缺口已如实复现」；旧仪器自己的读数")
        emit(fh, "#   （rc=1、无 SELFTEST 行、无 VERDICT 行）写在断言文本里。OVERALL_EXITCODE=0 = 缺口复现，")
        emit(fh, "#   不表示旧仪器可用，也不表示本片实现已绿（绿在 T-2026-10-15-009-green.txt；")
        emit(fh, "#   那份产物的名字由本文件派生，不是笔误）。")
        emit(fh, "# 名单边界：本运行只写本产物与（仅当缺失时）它自己的归档副本，不改任何被判文件；")
        emit(fh, "#   上面的指纹是「判定前读数」，本运行不重写这些文件，因此你今天回算应当与它相同；")
        emit(fh, "#   若不同，说明有别的运行改过仪器，请先重跑本脚本再看结论。复算命令：sha256sum <路径>。")
        emit(fh, "# 取样来源：前身字节取自归档副本；它由 git show HEAD:%s 落盘，" % OLD_IN_HEAD)
        emit(fh, "#   因此不依赖「旧路径仍在 HEAD 里」这个会随本片提交而失效的状态。")
        emit(fh, "")

        old_src = "\n".join(read_lines(OLD_COPY))
        new_src = "\n".join(read_lines(NEW_TOOL))
        batt_src = "\n".join(read_lines(NEW_BATT))
        layer_src = "\n".join(read_lines(LAYER))
        proto_src = open(PROTOTYPE, encoding="utf-8", errors="replace").read()

        # 01 旧仪器没有自检能力：它把 --selftest 当位置参数里的 URL，只能落到网络分支。
        rc_a, out_a = run(["node", OLD_COPY, "--selftest"])
        os.makedirs(os.path.join(REPO, "artifacts", "tmp", "_g009"), exist_ok=True)
        open(os.path.join(REPO, "artifacts", "tmp", "_g009", "red-selftest.txt"), "w",
             encoding="utf-8", newline="\n").write(out_a)
        emit(fh, "  命令: node %s --selftest" % os.path.relpath(OLD_COPY, REPO).replace("\\", "/"))
        emit(fh, "  退出码=%d 输出首行=%s" % (rc_a, out_a.splitlines()[0] if out_a.strip() else "(空)"))
        old_selftest_hits = len([l for l in old_src.splitlines() if "selftest" in l.lower()])
        new_batt_wired = ("--selftest" in new_src) and ('from "./verify_1to1_selftest.mjs"' in new_src)
        block(fh, 1, "old instrument cannot self-prove", [
            ("旧仪器正文里没有任何 selftest 分支（命中行数=%d，应为 0）" % old_selftest_hits,
             old_selftest_hits == 0),
            ("运行它不打印 SELFTEST 判决行（空见证，不是通过）", "SELFTEST" not in out_a),
            ("运行它不打印 EXITCODE= 行（旧形态从不打印档位）", "EXITCODE=" not in out_a),
            ("它不可能给出自检结论：rc 不是 0（本次=%d；1=网络分支失败，255=本机等不到）" % rc_a, rc_a != 0),
            ("对照：新仪器有 --selftest 分支且静态导入电池文件", new_batt_wired),
        ])

        # 02 仪器故障与判据失败同码：旧形态只有 0/1 两档，且 1 同时表示「连不上端口」和「页面不对」。
        codes = sorted({int(m.group(1)) for m in re.finditer(r"process\.exit\((\d+)\)", old_src)})
        rc_b, out_b = run(["node", OLD_COPY])
        emit(fh, "  读数: 旧仪器 process.exit 取值集合=%s；无参数运行 rc=%d 输出=%s"
             % (codes, rc_b, out_b.strip()[:120] or "(空)"))
        emit(fh, "  两次不同故障（把 --selftest 当 URL / 连 URL 都没给）的退出码=%s" % [rc_a, rc_b])
        block(fh, 2, "instrument fault indistinguishable from judgement failure", [
            ("旧仪器只有 0/1 两档（实测集合=%s，与「判据 FAIL」同码）" % codes, codes == [0, 1]),
            ("旧仪器没有 Usage/Fault 这两个类型（命中=0 才对）",
             len([t for t in ("class Usage", "class Fault", "EXITCODE=3", "INSTRUMENT FAULT")
                  if t in old_src]) == 0),
            ("同一档位被两种不同故障共用（--selftest 与无参数同码）", rc_a == rc_b),
            ("对照：共享层给出 0/1/2/3 四档常量表", "export const EXIT = { OK: 0, FAIL: 1, USAGE: 2, FAULT: 3 }" in layer_src),
            ("对照：共享层有 Usage 与 Fault 两个独立故障类型", "class Fault" in layer_src and "class Usage" in layer_src),
        ])

        # 03 只认位置参数：档案里的屏名/点击序列/等待选择器无从传入。
        old_screen = sum(1 for l in old_src.splitlines() if "--screen" in l)
        old_profile = sum(1 for l in old_src.splitlines() if "screens.json" in l or "--profile" in l)
        hardcoded_clicks = sum(1 for l in old_src.splitlines() if "['.demo-link', '.topic-card']" in l)
        prof = json.load(open(PROFILE, encoding="utf-8"))
        chat_proto = prof["screens"]["chat"]["prototype"]
        emit(fh, "  读数: 旧仪器 --screen 行=%d 档案行=%d 写死点击序列行=%d" % (old_screen, old_profile, hardcoded_clicks))
        emit(fh, "  档案侧 chat.prototype 现提供: clicks=%s wait_for=%s（旧仪器一条也接不到）"
             % (chat_proto.get("clicks"), chat_proto.get("wait_for")))
        block(fh, 3, "no profile-driven invocation", [
            ("旧仪器完全没有 --screen（0 行）", old_screen == 0),
            ("旧仪器完全不读屏档案（screens.json / --profile 0 行）", old_profile == 0),
            ("点击序列在旧仪器里是写死的一行（恰好 1 处）", hardcoded_clicks == 1),
            ("对照：新仪器按 --screen 取档案（>=1 行）", sum(1 for l in new_src.splitlines() if "--screen" in l) >= 1),
            ("对照：档案确实提供 clicks 与 wait_for（新仪器据此回放）",
             isinstance(chat_proto.get("clicks"), list) and bool(chat_proto.get("wait_for"))),
        ])

        # 04 端口写死：它假设 9222 可用，而本机那个端口可能被别人的东西占着或根本没人监听。
        fixed = sum(1 for l in old_src.splitlines() if l.strip().startswith("const PORT = 9222"))
        ephemeral = sum(1 for l in layer_src.splitlines() if "remote-debugging-port=0" in l)
        active_port = sum(1 for l in layer_src.splitlines() if "DevToolsActivePort" in l)
        listening = port_open(9222)
        emit(fh, "  读数: 旧仪器写死 9222=%d 行；共享层端口 0=%d 行、DevToolsActivePort=%d 行" % (fixed, ephemeral, active_port))
        emit(fh, "  环境竞态（打印不判）：本机 127.0.0.1:9222 此刻可连=%s —— 它决定了上面两次运行的 rc 落在哪条失败路径" % listening)
        block(fh, 4, "hard-coded debug port", [
            ("旧仪器写死了端口常量（恰好 1 行）", fixed == 1),
            ("旧仪器没有任何「让浏览器自选端口」的形状（0 命中）",
             ("DevToolsActivePort" not in old_src) and ("remote-debugging-port=0" not in old_src)),
            ("对照：新链路端口由浏览器自选并回读（端口 0 >=1 行、DevToolsActivePort >=1 行）",
             ephemeral >= 1 and active_port >= 1),
        ])

        # 05 探针抄了一份 token 名单，而其中一个已于 2026-09-28 退役：旧仪器对此一声不响。
        retired = "--green-bubble" in old_src
        proto_defines = "--green-bubble:" in proto_src
        retirement_note = "已于 2026-09-28 退役" in proto_src
        # 对照面读的是**词表声明的那份名单**（由 node 真的 import 模块取 DOM_CHECKS[*].tokens），
        # 不是「正文里某个字符串出现了几次」：退役说明本身要写进注释和失败文案（实测新仪器有 3 行
        # 提到 --green-bubble），用「命中=0」当判据会把设计内的注释判成本片缺陷——与「设计内见证
        # 被判缺陷」同族（本仓库在 007 的 MUTATION 产物上实测过一次）。
        rel_new = "./" + os.path.relpath(NEW_TOOL, REPO).replace("\\", "/")
        rc_tok, out_tok = run(["node", "-e",
                               "import(%s).then(m=>console.log(JSON.stringify("
                               "Object.entries(m.DOM_CHECKS).map(([k,v])=>[k,v.tokens||[]]))))"
                               % json.dumps(rel_new)])
        vocab_tokens = {}
        if rc_tok != 0:
            FAULTS.append("对照面取不到：node import 新仪器（取 tokens）失败 rc=%d %s" % (rc_tok, out_tok[-160:]))
        else:
            try:
                vocab_tokens = dict(json.loads(out_tok.strip()))
            except ValueError as e:
                FAULTS.append("对照面解析失败：词表 token 名单不是合法 JSON（%s）：%s" % (e, out_tok[-160:]))
        all_tokens = sorted({t for v in vocab_tokens.values() for t in v})
        undefined = [t for t in all_tokens if (t + ":") not in proto_src]
        emit(fh, "  读数: 旧仪器 token 名单含 --green-bubble=%s；原型现在定义它=%s；原型里的退役注记=%s"
             % (retired, proto_defines, retirement_note))
        emit(fh, "  对照: 词表要求的 token 名单=%s（原型 :root 未定义=%s）" % (all_tokens, undefined or "无"))
        block(fh, 5, "hard-copied token list keeps a retired name", [
            ("旧仪器在探针里抄了一份 token 名单，其中含已退役的 --green-bubble", retired),
            ("该 token 已不在原型 :root 里定义（读数为空串，而旧仪器不为此出声）", not proto_defines),
            ("退役事实在原型正文里有据（同文件退役注记在位）", retirement_note),
            ("对照：词表名单非空（空名单让下面两条成为空见证）", bool(all_tokens)),
            ("对照：新仪器词表要求的每个名字都在原型 :root 里真实定义（死名: %s）" % (undefined or "无"),
             not undefined),
            ("对照：词表要求的名单里已无退役的 --green-bubble", "--green-bubble" not in all_tokens),
            ("对照：探针不再抄第二份名单，改由 window.__PARITY_TOKEN_KEYS 单点注入", "__PARITY_TOKEN_KEYS" in new_src),
        ])

        # 06 旧仪器根本不判：它打印完 JSON 就退 0，「渲染侧达绿」此前靠人读数字。
        rc_keys, out_keys = run(["node", "-e",
                                 "import(" + json.dumps(
                                     "./" + os.path.relpath(NEW_TOOL, REPO).replace("\\", "/")) +
                                 ").then(m=>console.log(Object.keys(m.DOM_CHECKS).join(',')))"])
        vocab = sorted(x for x in out_keys.strip().split(",") if x) if rc_keys == 0 else []
        # import 失败要留下**当场**证据：它落在共享层的 Fault/Usage 出口里，
        # 若只在这里吞掉，产物里就没有任何一行能证明这条 FAIL 断言不是空证。
        import_fail = None
        if rc_keys != 0:
            import_fail = "rc=%d 末行=%s" % (rc_keys, (out_keys.strip().splitlines() or ["(空)"])[-1][:140])
            FAULTS.append("对照面取不到：node import 新仪器失败 rc=%d %s" % (rc_keys, out_keys[-160:]))
        dom_checks = sorted(chat_proto.get("dom_checks") or [])
        old_judgement = len([t for t in ("DOM_CHECKS", "VERDICT", "judgements=") if t in old_src])
        emit(fh, "  读数: 旧仪器命中判定标记=%d/3；词表键（由 node 现场 import 取得 rc=%d）=%s" % (old_judgement, rc_keys, vocab))
        emit(fh, "  对照: screens.json 的 chat.prototype.dom_checks=%s" % dom_checks)
        # 每条档案点名的检查都要有一条**以自己标签开头**的判决正文（detail 模板首词 = 标签）。
        # 不用「全文出现过这个名字」：注释与名单声明也算命中，读起来像判过其实没有；
        # 也不逐条解析 DOM_CHECKS[name](...) 调用点——那要解析 JS，而解析器既当判据又当豁免依据
        # 正是本仓库栽过的形态。取模板字面量首词这一形状，去掉任何一条判决即红。
        labelled = [n for n in dom_checks if ("detail: `" + n + " ") not in new_src]
        emit(fh, "  对照: 以标签开头的判决正文缺失=%s" % (labelled or "无"))
        emit(fh, "  对照: 词表条数=%d 与档案点名条数=%d 相同=%s；import 对照面=%s"
             % (len(vocab), len(dom_checks), len(vocab) == len(dom_checks), import_fail or "ok"))
        block(fh, 6, "no machine judgement exists at all", [
            ("旧仪器没有任何判定标记（DOM_CHECKS/VERDICT/judgements= 命中=0）", old_judgement == 0),
            ("旧仪器跑完就退 0：它只做 console.log(JSON) 再 exit(0)",
             "console.log(text)" in old_src and "process.exit(0)" in old_src),
            ("对照：词表键集合与档案点名的 dom_checks 集合逐个对得上（缺=%s 多=%s）"
             % ([n for n in dom_checks if n not in vocab] or "无",
                [n for n in vocab if n not in dom_checks] or "无"),
             bool(vocab) and sorted(vocab) == sorted(dom_checks)),
            ("对照：档案点名的每一条都有以标签开头的判决正文（缺: %s）" % (labelled or "无"), not labelled),
            ("对照：import 对照面当场成功（失败时下面两条断言是空证，见上一行读数）", import_fail is None),
            ("对照：新仪器把每条判定经 ledger 记录（ok/fail 两条通道都在正文里）",
             "ledger.ok(" in new_src and "ledger.fail(" in new_src),
            ("对照：统计行与 VERDICT 由共享层打印，且空见证显式判 FAULT（不在每支仪器里各写一份）",
             "judgements=" in layer_src and "VERDICT:" in layer_src and "没有任何判据" in layer_src),
        ])

        # 07 没有用例数下限：什么都没判也可能只是不打字。
        floor_old = len([t for t in ("MIN_CASES", "cases=", "failures=") if t in old_src])
        floor_new = len([t for t in ("MIN_CASES", "MIN_MUTS", "cases=") if t in batt_src])
        # 下限必须按**声明行**取（`const MIN_x = N;`），取引用行会把 `if (CASES.length < MIN_CASES ...)`
        # 也算成下限，那条判据就变成「文件里提到过这个词」——既有的「子串存在 ≠ 恰好一种取值」同族。
        lit = re.findall(r"(?m)^const MIN_(CASES|MUTS)\s*=\s*(\d+)\s*;", batt_src)
        emit(fh, "  读数: 旧仪器命中下限/计数标记=%d/3；新电池=%d/3，裸字面量下限声明=%s" % (floor_old, floor_new, lit))
        block(fh, 7, "no case-count floor", [
            ("旧仪器既没有用例数也没有下限标记", floor_old == 0),
            ("对照：新电池打印 cases= 并设 MIN_CASES/MIN_MUTS 两条裸字面量下界",
             floor_new == 3 and sorted(x[0] for x in lit) == ["CASES", "MUTS"]),
            ("对照：两条下限是与清单内容无关的字面量（实测值=%s，都必须 >0）" % [x[1] for x in lit],
             len(lit) == 2 and all(int(v) > 0 for _, v in lit)),
        ])

        # 08 名单对账：本运行判定用的每个文件都得在头里有判定前指纹，否则比较是空见证。
        needed = ("pre-adoption-verify_1to1.mjs", "verify_1to1.mjs(新路径)", "verify_1to1_selftest.mjs",
                  "parity_cdp.mjs", "screens.json", "高保真原型-v1.html")
        missing = [n for n in needed if not any(l.startswith("#   %s bytes=" % n) for l in HDR)]
        block(fh, 8, "every file used in a judgement has a pre-run fingerprint", [
            ("判定块条数下限 MIN_BLOCKS=%d 达成（空表让「全绿」成空见证）" % MIN_BLOCKS,
             len(BLOCKS) >= MIN_BLOCKS),
            ("本运行判定用过的每个文件都在名单里（缺: %s）" % (missing or "无"), not missing),
            ("名单条目都是三项齐全（bytes= / lines= / sha256= 各命中 %d/%d 条）"
             % (sum(1 for l in HDR if " bytes=" in l and " lines=" in l and " sha256=" in l), len(needed)),
             len(HDR) == len(needed)
             and all(" bytes=" in l and " lines=" in l and " sha256=" in l for l in HDR)),
        ])

        total = 1 if any(w != g for _, _, w, g in BLOCKS) else 0
        if FAULTS:
            total = 3
        emit(fh, "===== 汇总 =====")
        emit(fh, "  blocks=%d expectation_entries=%d faults=%d" % (len(BLOCKS), len(BLOCKS), len(FAULTS)))
        for f in FAULTS:
            emit(fh, "  FAULT %s" % f)
        for idx, label, want, got in BLOCKS:
            emit(fh, "  block %02d %-46s want=%d got=%d %s"
                 % (idx, label[:46], want, got, "ok" if want == got else "MISMATCH"))
        emit(fh, "  尾行之下的 EXITCODE 由本表派生，不硬写")
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
    if utf8_ok and cr == 0:
        with open(OUT, "a", encoding="utf-8", newline="\n") as ap:
            ap.write("DECODE ok utf8=True cr=0\n")
    raw = open(OUT, "rb").read()
    digest = hashlib.sha256(raw).hexdigest()
    print("DECODE ok utf8=%s cr=%d bytes=%d lines=%d sha256=%s"
          % (utf8_ok, cr, len(raw), raw.count(b"\n"), digest))
    print("  复算：sha256sum artifacts/evidence/T-2026-10-15-009-red.txt")
    if FAULTS:
        return 3
    return total if utf8_ok and cr == 0 else 3


if __name__ == "__main__":
    sys.exit(main())
