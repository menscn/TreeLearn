#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T-2026-10-15-009 GREEN 证据生成器：DOM 侧闭环量测仪器收编入库。

每块 want=0；总退出码由 want/got 表派生，不硬写。子进程判据一律同时断言
「退出码」与「判据行的存在与条数」——只看退出码会把「什么都没量」读成通过
（本仓库在 005/006/007 的兄弟门上各实测过一次）。

探针的**输出键名是外部契约**：块 04 拿 2026-09-27 那份归档读数逐键对账，
唯一有意差异面（tokens）必须当场打印两侧名单并解释，不得静默。
"""
import hashlib
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
EV = os.path.join(REPO, "artifacts", "evidence")
PAR = os.path.join(REPO, ".trae", "scripts", "dev", "parity")
TOOL = os.path.join(PAR, "verify_1to1.mjs")
BATT = os.path.join(PAR, "verify_1to1_selftest.mjs")
LAYER = os.path.join(PAR, "parity_cdp.mjs")
SHOT = os.path.join(PAR, "cdp_shot.mjs")
MEASURE = os.path.join(PAR, "measure_screen.py")
PROFILE = os.path.join(PAR, "screens.json")
PROTOTYPE = os.path.join(REPO, "Docs", "04-ux-design", "知芽-TreeLearn-高保真原型-v1.html")
ARCHIVE_JSON = os.path.join(EV, "ux-chat-1to1-render-dommeasure-2026-09-27.json")
PRE_COPY = os.path.join(EV, "T-2026-10-15-009-pre-adoption-verify_1to1.mjs")
WORK = os.path.join(REPO, "artifacts", "tmp", "_g009")
OUT = os.path.join(EV, "T-2026-10-15-009-green.txt")
OLD_REL = "artifacts/tmp/verify_1to1.mjs"
MOVED = ".trae/scripts/dev/parity/verify_1to1.mjs"
BATT_REL = ".trae/scripts/dev/parity/verify_1to1_selftest.mjs"
LAYER_REL = ".trae/scripts/dev/parity/parity_cdp.mjs"
PRE_COPY_REL = "artifacts/evidence/T-2026-10-15-009-pre-adoption-verify_1to1.mjs"
GEN_LEDGER = os.path.join(REPO, ".trae", "scripts", "dev", "gen_parity_ledger.py")
LEDGER = os.path.join(REPO, "artifacts", "parity", "ledger.md")
DOC_MD = os.path.join(REPO, "Docs", "04-ux-design", "知芽-TreeLearn-对话页1比1复刻方案-V1.0.md")
SPEC_MD = os.path.join(REPO, "Docs", "spec", "SPEC-2026-10-15-web-h5-android-parity.md")

# 名单 = 本运行判定要读过的每一个文件。块 12 用它反查「有没有判据读了一份没留指纹的文件」。
FINGER = [("verify_1to1.mjs", TOOL), ("verify_1to1_selftest.mjs", BATT),
          ("parity_cdp.mjs", LAYER), ("cdp_shot.mjs", SHOT), ("measure_screen.py", MEASURE),
          ("screens.json", PROFILE),
          ("T-2026-10-15-009-pre-adoption-verify_1to1.mjs", PRE_COPY),
          ("ux-chat-1to1-render-dommeasure-2026-09-27.json", ARCHIVE_JSON),
          ("知芽-TreeLearn-高保真原型-v1.html", PROTOTYPE),
          ("知芽-TreeLearn-对话页1比1复刻方案-V1.0.md", DOC_MD),
          ("SPEC-2026-10-15-web-h5-android-parity.md", SPEC_MD),
          ("artifacts/parity/ledger.md", LEDGER),
          ("gen_parity_ledger.py", GEN_LEDGER)]

BLOCKS = []
FAULTS = []
HDR = []

MIN_CLI_CASES = 8        # 与 CASES 清单内容无关的裸字面量下限
MIN_MUT_CASES = 8        # 与 MUTS 清单内容无关的裸字面量下限
MIN_JUDGEMENTS = 10      # 一次真实闭环至少打出多少条判决
MIN_PROFILE_FIXTURES = 4 # 档案侧 fail-closed 的夹具条数下限
MIN_USAGE_CASES = 4      # 用法档实测条数下限
MIN_WATCHED = 15         # 被盯集合下限：空集合让「无位移」成空见证（实测集合=本片碰过的两个目录的跟踪文件）
MIN_ACCOUNTED = 4        # 改动面条目下限：与 FACE 清单内容无关
MIN_NEW_FILES = 3        # 全新文件条数下限：与 NEW_FILES 清单内容无关
MIN_FINGER = 10          # 名单条目下限：与 FINGER 清单内容无关
MIN_BLOCKS = 8           # 判定块下限

CALL_TIMEOUT = 600


def emit(fh, text):
    fh.write(text + "\n")
    fh.flush()      # 被中断也要保住已写出的行
    print(text, flush=True)


def emit_head(fh, text):
    HDR.append(text)
    emit(fh, text)


def sha(p):
    b = open(p, "rb").read()
    return len(b), b.count(b"\n"), hashlib.sha256(b).hexdigest()


def run(cmd):
    try:
        r = subprocess.run(cmd, cwd=REPO, capture_output=True, text=True,
                           encoding="utf-8", errors="replace", timeout=CALL_TIMEOUT)
    except subprocess.TimeoutExpired:
        FAULTS.append("子进程超时 %ds: %s" % (CALL_TIMEOUT, " ".join(str(c) for c in cmd)[:120]))
        print("FAULT %s" % FAULTS[-1], file=sys.stderr, flush=True)
        return 255, "", ""
    return r.returncode, r.stdout or "", r.stderr or ""


def block(fh, idx, label, checks):
    bad = [t for t, ok in checks if not ok]
    emit(fh, "===== block %02d - %s =====" % (idx, label))
    for t, ok in checks:
        emit(fh, ("  ok   " if ok else "  FAIL ") + t)
    got = 0 if not bad else 1
    BLOCKS.append((idx, label, 0, got))
    emit(fh, "  block %02d: want=0 got=%d%s" % (idx, got, "" if not bad else " <- MISMATCH"))
    emit(fh, "")


def keysets(d):
    """顶层键集 + 每个 dict 值的键集；比较用键，不用值（值随原型制式变化，见块 04 说明）。"""
    top = set(d)
    sub = {k: set(v) for k, v in d.items() if isinstance(v, dict)}
    return top, sub


def key_diff(new, old):
    """键集对账的两半：归档顶层键在新读数里缺哪些，以及除 tokens 外哪些嵌套字典的键集不同。
    变异电池 import 的就是**这一份**实现——判定只许有一处，重写第二份会量到一个生产路径不用的形状。"""
    t_old, s_old = keysets(old)
    t_new, s_new = keysets(new)
    missing_top = sorted(t_old - t_new)
    sub_diff = {k: (sorted(s_old[k] - s_new.get(k, set())), sorted(s_new.get(k, set()) - s_old[k]))
                for k in s_old if k != "tokens" and s_old[k] != s_new.get(k, set())}
    return missing_top, sub_diff


def main():
    os.makedirs(WORK, exist_ok=True)
    prof_dir = os.path.join(WORK, "prof")
    os.makedirs(prof_dir, exist_ok=True)
    fh = open(OUT, "w", encoding="utf-8", newline="\n")
    try:
        for p in (TOOL, BATT, LAYER, SHOT, MEASURE, PROFILE, PROTOTYPE, ARCHIVE_JSON, PRE_COPY,
                  GEN_LEDGER, LEDGER, DOC_MD, SPEC_MD):
            if not os.path.isfile(p):
                emit(fh, "FAULT 缺少被核文件: %s" % p)
                return 3
        emit(fh, "# T-2026-10-15-009 GREEN：DOM 侧闭环量测仪器收编入库")
        emit(fh, "# 生成命令：python -X utf8 artifacts/tmp/gen-009-green.py")
        emit(fh, "# 被核文件（本次运行判定前读到的字节；本运行不写这些文件，因此你今天回算应当相同）：")
        for name, p in FINGER:
            n, l, s = sha(p)
            emit_head(fh, "#   %s bytes=%d lines=%d sha256=%s" % (name, n, l, s))
        emit(fh, "# 名单边界：本运行写的是本产物与 artifacts/tmp/_g009/ 下的中间读数与四份档案夹具；")
        emit(fh, "#   夹具由本运行现场生成（从 screens.json 派生后各改一处），不是手抄的第二份正文。")
        emit(fh, "#   块 12 断言「每一条判据读过的文件都在这份名单里」，名单本身因此承重、不是装饰。")
        emit(fh, "#   本产物自己的哈希见末行 DECODE 之后的控制台读数（复算：sha256sum 本文件），")
        emit(fh, "#   不抄进正文，否则下一行就把它作废。")
        emit(fh, "# 历史判据（移动而非重写）不在本产物里：它要求那次移动已进 git 历史，提交前必然红，")
        emit(fh, "#   属时效性状态；提交后由 gen-009-post-commit.py 单独出。")
        emit(fh, "# 规模口径见块 11：判定面 = 仪器本体（新文件全文），自检伴随文件按「Tests NOT counted」排除，")
        emit(fh, "#   该排除是**承重的**（judged + companion 越过 500 硬上限），两个口径都留在块 11。")
        emit(fh, "")

        tool_src = open(TOOL, encoding="utf-8").read()
        batt_src = open(BATT, encoding="utf-8").read()
        layer_src = open(LAYER, encoding="utf-8").read()

        # 01 三支脚本可解析；电池必须以静态 import 委派（动态 import 在本仓库实测会撞环）。
        rc1, o1, e1 = run(["node", "--check", TOOL])
        rc2, o2, e2 = run(["node", "--check", BATT])
        rc3, o3, e3 = run(["node", "--check", LAYER])
        emit(fh, "  node --check: verify_1to1=%d verify_1to1_selftest=%d parity_cdp=%d" % (rc1, rc2, rc3))
        static_delegation = ('from "./verify_1to1_selftest.mjs"' in tool_src
                             and 'await import("./verify_1to1_selftest.mjs")' not in tool_src)
        block(fh, 1, "all three scripts parse and delegation is static", [
            ("仪器本体 rc=0", rc1 == 0), ("自检伴随文件 rc=0", rc2 == 0), ("共享层 rc=0", rc3 == 0),
            ("仪器静态导入电池且正文无动态 await import（环的成因）", static_delegation),
            ("电池反向不导入仪器（避免环的另一半）", 'from "./verify_1to1.mjs"' in batt_src),
        ])

        # 02 电池：退出码 + 判决行存在与条数 + 档位覆盖 + 下限承重。
        rc, out, err = run(["node", TOOL, "--selftest"])
        open(os.path.join(WORK, "green-selftest.txt"), "w", encoding="utf-8", newline="\n").write(out)
        lines = out.splitlines()
        m = re.search(r"SELFTEST (PASS|FAIL) cases=(\d+) cli=(\d+) muts=(\d+) failures=(\d+)", out)
        # 判决行的**形状**决定它属于哪一层（本仓库踩过：全文找 "FAIL " 会把设计内的见证当成本片缺陷）。
        #   CLI 层  ： `ok   <名> rc=<v> (…s, want=<v>)` 或 `(unit, want=<v>)`
        #   词表层  ： `ok   <检查>/<中文注入> 判红且其它 5 项不受影响`（无 rc=）
        #   夹具见证： 被 echo 的仪器正文，如 `FAIL wait-for-absent …`（既无 rc= 也无「判红」）
        rc_line = re.compile(r"^(ok   |FAIL )(\S+) rc=(\d+).*want=(\d+)")
        cli_hits = [l for l in lines if rc_line.match(l) and "(unit," not in l]
        unit_hits = [l for l in lines if rc_line.match(l) and "(unit," in l]
        fail_cli = [l for l in cli_hits + unit_hits if l.startswith("FAIL ")]
        mut_hits = [l for l in lines if re.match(r"^(ok   |FAIL )\S+/.*判红且其它", l)]
        fail_mut = [l for l in mut_hits if l.startswith("FAIL ")]
        echo_fails = [l for l in lines if l.startswith("FAIL ")
                      and not rc_line.match(l) and "判红且其它" not in l]
        tiers = sorted({int(rc_line.match(l).group(3)) for l in cli_hits})
        cases = int(m.group(2)) if m else -1
        cli_n = int(m.group(3)) if m else -1
        mut_n = int(m.group(4)) if m else -1
        fails = int(m.group(5)) if m else -1
        pos_ctrl = [l for l in lines if l.startswith("ok   positive-control ")]
        emit(fh, "  电池读数: rc=%d 汇总行=%s ok-cli=%d unit=%d ok-mut=%d FAIL行(电池)=%d "
             "夹具 echo 的 FAIL 见证行=%d tiers=%s stderr=%d 字节"
             % (rc, m.group(0) if m else "无", len(cli_hits), len(unit_hits), len(mut_hits),
                len(fail_cli) + len(fail_mut), len(echo_fails), tiers, len(err.encode("utf-8"))))
        for l in echo_fails:
            emit(fh, "    夹具见证（设计内，不判成本片缺陷）: %s" % l[:150])
        named = [n for n in ("pos-real-dom-render", "pos-url-escape", "neg-unknown-option",
                             "neg-missing-screen", "neg-bad-wait", "neg-unknown-screen",
                             "neg-browser-absent-injection", "neg-profile-no-prototype",
                             "neg-html-missing", "neg-profile-not-utf8", "neg-checks-absent",
                             "neg-checks-empty", "neg-check-name-unknown", "neg-url-file-missing",
                             "neg-navigate-rejected", "neg-cdp-handshake-fail",
                             "neg-wait-for-absent", "neg-click-absent",
                             "neg-page-not-rendered", "neg-app-not-booted")
                 if not any(rc_line.match(l) and rc_line.match(l).group(2) == n and l.startswith("ok   ")
                            for l in cli_hits)]
        # 三条 rc=1 的用例必须各自留下它要触发的那条**页面判据行**；只测退出码会把
        # 「用例其实没走到那条分支」读成通过。
        witnesses = [w for w in ("wait-for-absent", "click-target-absent", "page-not-rendered")
                     if not any(w in l for l in echo_fails)]
        # 「等不到 .screen」的成因要可分：见证行必须带上 #root 的读数（0=没起来→3，>0=结构不符→1）。
        render_probe = [l for l in echo_fails
                        if "page-not-rendered" in l and "#root 子元素=" in l]
        block(fh, 2, "battery green, non-empty, and tier-complete", [
            ("rc 恰好 0", rc == 0),
            ("打印了 SELFTEST PASS 汇总行（空 stdout 就是空见证）", bool(m) and m.group(1) == "PASS"),
            ("打印的 cli/muts 与按形状归位的判决行数一致（条数不是自述）",
             cli_n == len(cli_hits) and mut_n == len(mut_hits)),
            ("cases = cli+muts+2，且那 2 条各有见证（unit 空判据 + 词表层正控）",
             cases == cli_n + mut_n + len(unit_hits) + len(pos_ctrl)
             and len(unit_hits) == 1 and len(pos_ctrl) == 1),
            ("failures=0 且电池自己的判决行里没有 FAIL", fails == 0 and not fail_cli and not fail_mut),
            ("用例总数下限 MIN_CLI_CASES=%d 与 MIN_MUT_CASES=%d 达成（与清单内容无关）"
             % (MIN_CLI_CASES, MIN_MUT_CASES), cli_n >= MIN_CLI_CASES and mut_n >= MIN_MUT_CASES),
            ("四个档位 0/1/2/3 都被 CLI 层走到（实测 %s）" % tiers, tiers == [0, 1, 2, 3]),
            ("跨档用例逐条在位（缺: %s）" % (named or "无"), not named),
            ("三条期望 rc=1 的用例各自留下页面判据见证（缺: %s）" % (witnesses or "无"), not witnesses),
            ("「等不到 .screen」的见证行带上 #root 读数（%d 条），成因因此可分" % len(render_probe),
             len(render_probe) == 1),
            ("该见证判据在电池源码里落地而不是只写在注释里（witness: 声明 3 条且循环真的截 console.log）",
             batt_src.count("witness:") == 3 and "echoed.some(" in batt_src
             and "console.log = real" in batt_src),
            ("stderr 不含 unsettled top-level await（那条告警等于仪器没跑完）",
             "unsettled top-level await" not in err),
        ])

        # 03 一次真实闭环：按屏名跑通，六条词表判据逐条打出，读数落盘。
        dom_json = os.path.join(WORK, "green-chat-dom.json")
        rc, out, err = run(["node", TOOL, "--screen", "chat", "--out", dom_json])
        emit(fh, "  命令: node %s --screen chat --out %s"
             % (MOVED, os.path.relpath(dom_json, REPO).replace("\\", "/")))
        jm = re.search(r"judgements=(\d+) failed=(\d+)", out)
        prof = json.load(open(PROFILE, encoding="utf-8"))
        check_names = prof["screens"]["chat"]["prototype"]["dom_checks"]
        printed = [n for n in check_names if not any(
            (l.startswith("OK   " + n + " ") or l.startswith("OK   " + n + " =") or l.startswith("FAIL " + n + " "))
            for l in out.splitlines())]
        toks = re.search(r"tokens = 现场回读 (\d+) 个（词表要求 (\d+) 个", out)
        emit(fh, "  读数: rc=%d %s 每条检查都打了字=%s token 回读=%s"
             % (rc, jm.group(0) if jm else "无判据统计行", "全部在位" if not printed else "缺 " + str(printed),
                toks.group(0) if toks else "无"))
        block(fh, 3, "one real closed loop through the profile", [
            ("rc 恰好 0", rc == 0),
            ("VERDICT: PASS 与 EXITCODE=0 都在", "VERDICT: PASS" in out and "EXITCODE=0" in out),
            ("判据统计行存在且 failed=0", bool(jm) and jm.group(2) == "0"),
            ("判据条数下限 MIN_JUDGEMENTS=%d 达成（实测 %s）"
             % (MIN_JUDGEMENTS, jm.group(1) if jm else "无"), bool(jm) and int(jm.group(1)) >= MIN_JUDGEMENTS),
            ("档案点名的每条检查各自打了一条判决（缺: %s）" % (printed or "无"), not printed),
            ("汇总行把「档案名单」原样打出来（防止加了第六项而仪器没测）",
             ("dom-checks = %d/%d 成立（名单来自档案 %s）" % (len(check_names), len(check_names), ", ".join(check_names))) in out),
            ("HTML 路径来自档案且中文路径未坏", "知芽-TreeLearn-高保真原型-v1.html" in out),
            ("点击序列来自档案", "clicks = .demo-link -> .topic-card" in out),
            ("浏览器版本进了证据", re.search(r"browser = Edg/\S+", out) is not None),
            ("token 是现场回读且与词表要求同数", bool(toks) and toks.group(1) == toks.group(2)),
            ("读数 JSON 真的落盘且非空", os.path.isfile(dom_json) and os.path.getsize(dom_json) > 1000),
        ])

        # 04 保持行为：探针输出键集与 2026-09-27 归档逐键相同（值不判，理由见正文）。
        old = json.load(open(ARCHIVE_JSON, encoding="utf-8"))
        new = json.load(open(dom_json, encoding="utf-8"))
        t_old, s_old = keysets(old)
        t_new, s_new = keysets(new)
        missing_top, sub_diff = key_diff(new, old)
        emit(fh, "  键集对账: 顶层 归档=%d 新=%d 缺=%s；嵌套有差异的键=%s（嵌套已比对字典=%d）"
             % (len(t_old), len(t_new), missing_top or "无", sub_diff or "无", len(s_old)))
        emit(fh, "  tokens 面（唯一有意差异）: 归档名单=%s" % sorted(old.get("tokens", {})))
        emit(fh, "  tokens 面: 新名单=%s" % sorted(new.get("tokens", {})))
        emit(fh, "  代表值（只打印不判，值随原型制式变化）: 用户消息底色 归档=%s 新=%s；格线周期 归档=%s 新=%s"
             % (old.get("userBubble", {}).get("css", {}).get("background-color"),
                new.get("userBubble", {}).get("css", {}).get("background-color"),
                old.get("gridTexture"), new.get("gridTexture", "")[:40]))
        req = sorted({t for t in re.findall(r'DOM_CHECKS\["([^"]+)"\]\.tokens = \[([^\]]*)\]', tool_src)
                      for t in re.findall(r'"(--[^"]+)"', t[1])})
        block(fh, 4, "keep-behaviour: probe output keys match the 2026-09-27 archive", [
            ("归档顶层键在新读数里一个不缺（缺: %s）" % (missing_top or "无"), not missing_top),
            ("除 tokens 外每个嵌套字典的键集也与归档相同（差异: %s）" % (sub_diff or "无"), not sub_diff),
            ("新读数的 tokens 键集 == 词表要求的名单（漂移即点名）",
             sorted(new.get("tokens", {})) == req),
            ("tokens 差异是有据的：归档名单含已退役的 --green-bubble，新名单不含",
             "--green-bubble" in old.get("tokens", {}) and "--green-bubble" not in new.get("tokens", {})),
            ("新名单非空（空名单让上面两条成空见证）", bool(req)),
            ("嵌套比对面非空（已比对 %d 个顶层字典，否则上面那条嵌套判据是空见证）" % len(s_old),
             len(s_old) >= 6),
        ])
        # 05 档案侧 fail-closed：夹具由 screens.json **现场派生**后各改一处，不是手抄第二份正文。
        #    必须带一条「派生但未改动」的正控：否则「四份夹具都退 3」可能只是调用形态本身坏了。
        base = json.loads(open(PROFILE, encoding="utf-8").read())
        fixtures = []

        def mk(name, mutate):
            p = json.loads(json.dumps(base))
            mutate(p["screens"]["chat"])
            path = os.path.join(prof_dir, name)
            open(path, "w", encoding="utf-8", newline="\n").write(json.dumps(p, ensure_ascii=False))
            rc, out, err = run(["node", TOOL, "--screen", "chat", "--profile", path])
            fixtures.append((name, rc, out + err))
            return rc, out + err

        mk("derived-unchanged.json", lambda c: None)
        mk("empty-checks.json", lambda c: c["prototype"].__setitem__("dom_checks", []))
        mk("no-checks.json", lambda c: c["prototype"].pop("dom_checks"))
        mk("unknown-name.json", lambda c: c["prototype"].__setitem__("dom_checks", ["body-bg", "nope-not-in-vocab"]))
        mk("no-prototype.json", lambda c: c.pop("prototype"))
        pos = fixtures[0]
        negs = fixtures[1:]
        for name, rc, text in fixtures:
            emit(fh, "  夹具 %-24s rc=%d 末两行=%s" % (name, rc, " | ".join(text.strip().splitlines()[-2:])[:150]))
        no_tier = [n for n, _, t in negs if "EXITCODE=3" not in t]
        pass_lines = [n for n, rc, t in negs if "VERDICT: PASS" in t]
        block(fh, 5, "profile-side fail-closed with a positive control", [
            ("夹具条数下限 MIN_PROFILE_FIXTURES=%d 达成（负向夹具，不含正控）" % MIN_PROFILE_FIXTURES,
             len(negs) >= MIN_PROFILE_FIXTURES),
            ("正控：同一套夹具流水线、未改动的那份必须退 0（实测 %d）" % pos[1], pos[1] == 0),
            ("四份负向夹具全部退 3（实测 %s）" % [x[1] for x in negs], all(x[1] == 3 for x in negs)),
            ("每份负向夹具都打印了档位行 EXITCODE=3（缺: %s）" % (no_tier or "无"), not no_tier),
            ("没有任何一份负向夹具打出 VERDICT: PASS（有: %s）" % (pass_lines or "无"), not pass_lines),
            ("每份负向夹具都有 INSTRUMENT FAULT 行（属仪器故障档而非判据 FAIL）",
             all("INSTRUMENT FAULT" in t for _, _, t in negs)),
            ("四条负向各自点名了不同的成因（缺: %s）"
             % ([n for n, _, t in negs
                 if not any(k in t for k in ("没有 dom_checks", "prototype 段", "不在词表内", "档案"))] or "无"),
             all(any(k in t for k in ("没有 dom_checks", "prototype 段", "不在词表内", "档案"))
                 for _, _, t in negs)),
        ])

        # 06 用法档实测：四条调用形态错误各退 2，且都不许起浏览器、不许打 PASS。
        usage_cases = [([], "连 --screen 都没给"),
                       (["--screen", "nope"], "屏名不在档案里"),
                       (["--screen", "chat", "--wait-ms", "0"], "--wait-ms 非正整数"),
                       (["--bogus", "1"], "未知选项")]
        usage = []
        for argv, why in usage_cases:
            rc, out, err = run(["node", TOOL] + argv)
            usage.append((why, rc, out + err))
            emit(fh, "  用法 %-22s argv=%s rc=%d 末行=%s"
                 % (why, " ".join(argv) or "(空)", rc, (out + err).strip().splitlines()[-1][:120]))
        block(fh, 6, "usage tier measured through the real CLI", [
            ("用法用例条数下限 MIN_USAGE_CASES=%d 达成" % MIN_USAGE_CASES, len(usage) >= MIN_USAGE_CASES),
            ("四条全部退 2（实测 %s）" % [r for _, r, _ in usage], all(r == 2 for _, r, _ in usage)),
            ("四条都打印了 EXITCODE=2 档位行（复核者拿得到约定）",
             all("EXITCODE=2" in t for _, _, t in usage)),
            ("四条都没有 VERDICT: PASS 也没有判据行",
             all("VERDICT: PASS" not in t and "judgements=" not in t for _, _, t in usage)),
            ("四条都没有起浏览器（没有 browser = 行）——用法错误不该有副作用",
             all("browser = " not in t for _, _, t in usage)),
        ])

        # 07 兄弟门不回归：本片动过共享层（头注与一条故障见证文案），两支兄弟仪器必须仍绿。
        rc_s, out_s, err_s = run(["node", SHOT, "--selftest"])
        m_s = re.search(r"SELFTEST (PASS|FAIL) cases=(\d+)", out_s)
        rc_m, out_m, err_m = run(["python", "-X", "utf8", MEASURE, "--selftest"])
        m_m = re.search(r"(\d+) case\(s\) executed, expectation table says (\d+), (\d+) failure", out_m)
        emit(fh, "  产图侧: rc=%d %s；像素仪器: rc=%d %s"
             % (rc_s, m_s.group(0) if m_s else "无汇总行", rc_m, m_m.group(0) if m_m else "无汇总行"))
        block(fh, 7, "sibling gates still green after the shared-layer edit", [
            ("cdp_shot.mjs --selftest rc 恰好 0", rc_s == 0),
            ("它打印了 SELFTEST PASS cases=N（退出码 0 但没有判决行就是空见证）",
             bool(m_s) and m_s.group(1) == "PASS" and int(m_s.group(2)) >= 12),
            ("measure_screen.py --selftest rc 恰好 0", rc_m == 0),
            ("它打印了 case(s) executed 行且执行数==期望表条数、failure=0",
             bool(m_m) and m_m.group(1) == m_m.group(2) and m_m.group(3) == "0"),
        ])

        # 08 共享层文案同步：那句「待收编」在本片落地后就是反的；顺带修掉一条没有任何消费者、
        #    也不符合本仓库见证命名的 stray 标签（实测 git grep 全仓只有它自己那一处命中）。
        rc_g, out_g, _ = run(["git", "-c", "core.quotepath=false", "grep", "-n", "FACTOR-check"])
        stray_hits = [l for l in out_g.splitlines() if l.strip()]
        fault_witness = [t for n, _, t in fixtures if "VERDICT: FAULT 仪器故障不写成判据失败" in t]
        block(fh, 8, "shared-layer wording matches the current state", [
            ("共享层头注不再写「待收编的 verify_1to1.mjs」", "待收编的 verify_1to1" not in layer_src),
            ("头注改成现况并点名本片（DOM 侧仪器已入库）",
             "T-2026-10-15-009" in layer_src and "DOM 侧 verify_1to1.mjs" in layer_src),
            ("stray 标签 FACTOR-check 全仓命中为 0（git grep rc 属于 {0,1}）",
             not stray_hits),
            ("Fault 路径实测打印了命名一致的见证行（夹具里命中 %d/%d 份）"
             % (len(fault_witness), len(negs)), len(fault_witness) == len(negs)),
            ("该见证行不与判据混码：这些夹具的 rc 仍是 3（不是 0/1）",
             all(x[1] == 3 for x in negs)),
        ])
        # 09 运行窗口增量：共享工作树里别人的在飞工作不得被本片当成缺陷，但本片窗口内的位移必须点名。
        watch_dirs = (".trae/scripts/dev/parity", "Docs/04-ux-design")
        rc_ls, out_ls, _ = run(["git", "-c", "core.quotepath=false", "ls-files", "-z", "--"] + list(watch_dirs))
        tracked = [os.path.join(REPO, rel.replace("/", os.sep)) for rel in filter(None, out_ls.split("\0"))]

        def snap(paths):
            d = {}
            for p in paths:
                key = os.path.relpath(p, REPO).replace(os.sep, "/")
                d[key] = "<absent>" if not os.path.isfile(p) else hashlib.sha256(open(p, "rb").read()).hexdigest()
            return d

        # 正控必须与被判集合走**同一套快照/差分**，而且要在起始快照之前就位：
        # 第一版把控制文件建在 start 之后，于是它自己出现在 start/mid 的差集里，
        # 「窗口内位移」被自己的正控打红（假警报），而 movers 判据本来要保护的东西没被量到。
        control = os.path.join(WORK, "_watched-control.tmp")
        open(control, "w", encoding="utf-8", newline="\n").write("x\n")
        start = snap(tracked + [control])
        mid = snap(tracked + [control])
        open(control, "w", encoding="utf-8", newline="\n").write("yy-two-bytes-more\n")
        after = snap(tracked + [control])
        os.remove(control)
        def changed(a, b):
            """按**键的并集**比：对称差集只会抓到「新增/消失」，内容变了但两边都有键的路径永远抓不到
            （本仓库的正控第一次就是这么变成空响的——它自称会响，实测捕获恒为空）。"""
            return sorted(k for k in set(a) | set(b) if a.get(k) != b.get(k))

        movers = changed(start, mid)
        watchers = changed(mid, after)
        control_key = os.path.relpath(control, REPO).replace(os.sep, "/")
        emit(fh, "  被盯集合=%d（git ls-files rc=%d）窗口内位移=%d movers=%s"
             % (len(start), rc_ls, len(movers), movers or "[]"))
        emit(fh, "  正控（同一条快照/差分机制，被盯文件在窗口内被改）捕获=%s" % (watchers or "[]"))
        block(fh, 9, "run-window delta over the shared work tree", [
            ("git 列被盯集合 rc 恰好 0", rc_ls == 0),
            ("被盯集合非空（下限 MIN_WATCHED=%d，空集合让「无位移」成空见证）" % MIN_WATCHED,
             len(start) >= MIN_WATCHED),
            ("本片运行期间被盯目录无位移（有则等安静子窗口重跑，不改判据）", not movers),
            ("正控会响：窗口内真改一个被盯文件必须被点名（且只点名它）", watchers == [control_key]),
        ])

        # 10 改动面对 git 可见 + 旧路径只剩历史。判定面用 --no-renames（该视图永不折叠路径），
        #    配对形态只作对照打印：006 实测过 -M 在 20% 阈值下也不配对，把一次显示层测量当契约是错的。
        FACE = [OLD_REL, MOVED, BATT_REL, LAYER_REL, PRE_COPY_REL]
        rc_n1, o_n1, _ = run(["git", "-c", "core.quotepath=false", "diff", "--no-renames", "--numstat",
                              "HEAD", "--"] + FACE)
        rc_m1, o_m1, _ = run(["git", "-c", "core.quotepath=false", "diff", "-M", "--numstat", "HEAD", "--"] + FACE)
        rc_u, o_u, _ = run(["git", "-c", "core.quotepath=false", "ls-files", "--others", "--exclude-standard",
                            "--"] + FACE)
        rows = {}
        for l in o_n1.splitlines():
            if l.strip():
                p = l.split("\t")
                if len(p) == 3:
                    rows[p[2]] = (int(p[0]) if p[0] != "-" else -1, int(p[1]) if p[1] != "-" else -1)
        untracked = [l.strip() for l in o_u.splitlines() if l.strip()]
        invisible = [p for p in FACE if p not in rows and p not in untracked]
        change_face = [p for p in FACE if p in rows or p in untracked]
        # 「全新文件」要求它们被 git **看见**，而不是要求某一种暂存形态：已 git add 的新文件
        # 出现在 diff HEAD 的增量里（A）而不在未跟踪清单里，未暂存的才落在未跟踪清单。
        # 原判据写死「必须在未跟踪清单里」，于是同一份内容只因人先 add 过一次就判红。
        NEW_FILES = [MOVED, BATT_REL, PRE_COPY_REL]
        seen_where = {p: ("untracked" if p not in rows else "diff HEAD (A/M)")
                      for p in NEW_FILES if p in rows or p in untracked}
        new_unseen = [p for p in NEW_FILES if p not in seen_where]
        emit(fh, "  全新文件被 git 看见=%d/%d 位置=%s"
             % (len(seen_where), len(NEW_FILES), seen_where or "{}"))
        emit(fh, "  基线 base=HEAD window=uncommitted-only 改动面条目=%d（下限 %d）不可见=%s"
             % (len(change_face), MIN_ACCOUNTED, invisible or "[]"))
        for p in FACE:
            if p in rows:
                emit(fh, "    numstat %s added=%d deleted=%d" % (p, rows[p][0], rows[p][1]))
            elif p in untracked:
                emit(fh, "    未跟踪（git diff 对它失明）%s" % p)
            else:
                emit(fh, "    既不在增量里也不在未跟踪清单里 %s" % p)
        emit(fh, "  配对对照（只打印不判）：--no-renames 行=%d ；-M 行=%d"
             % (len(rows), len([l for l in o_m1.splitlines() if l.strip()])))
        for l in o_m1.splitlines():
            if l.strip():
                emit(fh, "    %s" % l[:150])

        rc_gg, out_gg, _ = run(["git", "-c", "core.quotepath=false", "grep", "-n", "artifacts/tmp/verify_1to1"])
        hits = [l for l in out_gg.splitlines() if l.strip()]
        HIST = (".trae/tasks/", "artifacts/evidence/", "artifacts/tmp/", MOVED, BATT_REL)
        doc = "Docs/04-ux-design/知芽-TreeLearn-对话页1比1复刻方案-V1.0.md"
        spec = "Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md"
        ledger_rel = "artifacts/parity/ledger.md"
        current_state = [l for l in hits if not l.startswith(HIST)]
        in_doc = [l for l in current_state if l.startswith(doc + ":")]
        # 台账是**生成物**（首行自述「生成物，勿手改」），它那一行由 gen_parity_ledger.py 的
        # instrument_line() 派生，不是人写的现位置声明。把它混进「手写当前态文档残留」会把
        # 「等一次重生成就自愈的派生行」读成「文档没回写」；但也不能就此放过。
        # 原先这里判的是「恰好一条派生行」，那是**只在重生成之前成立**的时效状态：一旦台账按
        # 安静窗口重生成，旧路径命中归零而该块转红，读者会把「已自愈」读成「文档回写复发」。
        # 改成两态各自可判：过渡态=恰好一条且形状是生成器模板；自愈态=零条命中且台账自己写 adopted。
        in_ledger = [l for l in current_state if l.startswith(ledger_rel + ":")]
        outside = [l for l in current_state
                   if not l.startswith(doc + ":") and not l.startswith(ledger_rel + ":")]
        ledger_src = open(os.path.join(REPO, ledger_rel.replace("/", os.sep)), encoding="utf-8").read()
        gen_src = open(GEN_LEDGER, encoding="utf-8").read()
        doc_src = open(os.path.join(REPO, doc.replace("/", os.sep)), encoding="utf-8").read()
        spec_src = open(os.path.join(REPO, spec.replace("/", os.sep)), encoding="utf-8").read()
        # 生成器按候选顺序取第一个存在的路径，所以「新路径排在旧路径之前」就是它会自愈的依据。
        i_new = gen_src.find(".trae/scripts/dev/parity/%s")
        i_old = gen_src.find("artifacts/tmp/%s")
        derived_shape = [l for l in in_ledger
                         if re.search(r"行为仪器（verify_1to1\.mjs）：(adopted|not-adopted:\S+|absent)", l)]
        adopted_row = bool(re.search(r"行为仪器（verify_1to1\.mjs）：adopted\b", ledger_src))
        rc_lg, out_lg, _ = run(["python", "-X", "utf8", GEN_LEDGER, "--no-write"])
        obs = ("INVENTORY STALE" in out_lg) or ("清单新鲜度：STALE" in out_lg)
        emit(fh, "  生成器现场读数（只打印不判：清单面受别人在飞工作影响，重生成要等安静子窗口）"
             " rc=%d 现场那行=%s 是否报清单STALE=%s"
             % (rc_lg, next((l for l in out_lg.splitlines() if "行为仪器" in l), "(无)"), obs))
        emit(fh, "  反向检索 artifacts/tmp/verify_1to1: rc=%d 命中=%d 其中非历史面=%d（手写残留=%d 台账生成行=%d）"
             % (rc_gg, len(hits), len(current_state), len(outside), len(in_ledger)))
        for h in hits:
            emit(fh, "    命中: %s" % h[:170])
        block(fh, 10, "change face visible to git; old path only as history", [
            ("每条 git 调用 rc 属于 {0,1}（>=2 表示 pathspec 打错而不是干净）",
             all(rc in (0, 1) for rc in (rc_n1, rc_m1, rc_u, rc_gg))),
            ("FACE 五项逐个被某个 git 视图看到（不可见: %s）" % (invisible or "无"), not invisible),
            ("改动面条目 >= MIN_ACCOUNTED=%d（空集合让「无不可见」成空见证）" % MIN_ACCOUNTED,
             len(change_face) >= MIN_ACCOUNTED),
            ("三条全新文件被 git 看见：在 diff HEAD 增量或未跟踪清单之一（缺: %s）" % (new_unseen or "无"),
             not new_unseen and len(seen_where) >= MIN_NEW_FILES),
            ("全树命中非空（空命中=空见证，说明这条搜索从没扫到东西）", len(hits) > 0),
            ("除复刻方案与台账生成行外没有把旧路径写成现位置的面（残留: %s）" % (outside[:2] or "无"), not outside),
            ("台账的旧路径命中要么是恰好一条生成器派生行（过渡态），要么归零且台账自述 adopted（自愈态）——"
             "两态互斥且必居其一（实测命中=%d 模板形状=%d adopted行=%s）"
             % (len(in_ledger), len(derived_shape), adopted_row),
             len(in_ledger) == len(derived_shape)
             and ((len(in_ledger) == 1 and not adopted_row) or (len(in_ledger) == 0 and adopted_row))),
            ("台账自述是生成物、且生成器优先认新路径（重生成即自愈，不是手写漂移）",
             "生成物，勿手改" in ledger_src and i_new >= 0 and i_old >= 0 and i_new < i_old),
            ("复刻方案仍提旧路径时同文件必须给出权威位置注记", (not in_doc) or
             ("现在的权威位置是 `.trae/scripts/dev/parity/`" in doc_src)),
            ("SPEC §10 的 carve-out 已改成「DOM 侧一支已于 009 入库」，不残留「仍是位置参数形态」",
             "T-2026-10-15-009" in spec_src and "仍是 `artifacts/tmp/` 下的位置参数形态" not in spec_src),
        ])

        # 11 规模判定值：新文件按全文，既有文件（共享层）按本次新增行面；两口径都留档。
        import difflib
        head_layer = subprocess.run(["git", "show", "HEAD:%s" % LAYER_REL], cwd=REPO, capture_output=True)
        added_lines = []
        if head_layer.returncode == 0:
            before_txt = head_layer.stdout.decode("utf-8", "replace")
            after_txt = open(LAYER, encoding="utf-8").read()
            added_lines = [l for l in difflib.unified_diff(before_txt.splitlines(), after_txt.splitlines(),
                                                           lineterm="")
                           if l.startswith("+") and not l.startswith("+++")]
        face = os.path.join(WORK, "layer-added.txt")
        open(face, "w", encoding="utf-8", newline="\n").write("\n".join(x[1:] for x in added_lines) + "\n")
        rc_c1, out_c1, _ = run(["python", "-X", "utf8", ".trae/scripts/dev/count_production_lines.py",
                                os.path.relpath(face, REPO).replace(os.sep, "/")])
        rc_c2, out_c2, _ = run(["python", "-X", "utf8", ".trae/scripts/dev/count_production_lines.py",
                                MOVED, BATT_REL])
        rc_c3, out_c3, _ = run(["python", "-X", "utf8", ".trae/scripts/dev/count_production_lines.py", MOVED])

        def total_of(text):
            mm = re.search(r"^\s*(\d+)\s+TOTAL", text, re.M)
            return int(mm.group(1)) if mm else -1

        def per_of(text):
            return [int(x.group(1)) for x in
                    (re.match(r"^\s*(\d+)\s+(?!TOTAL\b)(\S+)$", l) for l in text.splitlines() if l.strip()) if x]

        layer_added = total_of(out_c1)
        both = per_of(out_c2)
        judged_instr = total_of(out_c3)
        judged = judged_instr + layer_added
        emit(fh, "  读数: 仪器本体=%d 共享层新增行面=%d（原始 %d 条）判定值=%d 电池=%d 两文件合计=%d 物理行=%d"
             % (judged_instr, layer_added, len(added_lines), judged,
                both[1] if len(both) == 2 else -1, sum(both) if both else -1,
                len(tool_src.splitlines())))
        for l in (out_c1 + out_c2 + out_c3).splitlines():
            emit(fh, "    %s" % l[:150])
        block(fh, 11, "judged size from the repo tool, exclusion is load-bearing", [
            ("三条 count 调用 rc 全为 0", rc_c1 == 0 and rc_c2 == 0 and rc_c3 == 0),
            ("面文件行数 == 由 HEAD 副本与当前字节算出的新增行条数（写进去的就是算出来的）",
             len(open(face, encoding="utf-8").read().splitlines()) == len(added_lines)),
            ("两文件口径给出两条逐文件读数且 TOTAL 由它们相加得到",
             len(both) == 2 and sum(both) == total_of(out_c2)),
            ("判定值 0 < n <= 500 硬上限内（本次 %d = 仪器 %d + 共享层新增行面 %d）" % (judged, judged_instr, layer_added),
             0 < judged <= 500),
            ("排除自检伴随文件是**承重的**：judged(%d) + battery(%d) = %d > 500，不分文件就越线"
             % (judged, both[1] if len(both) == 2 else -1, judged + (both[1] if len(both) == 2 else 0)),
             len(both) == 2 and judged + both[1] > 500),
            ("判定面（非空非注释）小于物理行数（%d < %d），两口径不混用" % (judged, len(tool_src.splitlines())),
             judged < len(tool_src.splitlines())),
        ])
        # 12 名单承重：本运行每处「读文件字节当判据」的地方都要在头部留有判定前指纹。
        #    只写不查的名单与没有名单等价（007 在 WATCH 名单上实测过：删掉一条后比较成空见证）。
        #    JUDGED 与 FINGER 是两处正文，加判据而不登记指纹必须在这里响。
        JUDGED = [TOOL, BATT, LAYER, SHOT, MEASURE, PROFILE, PRE_COPY, ARCHIVE_JSON,
                  PROTOTYPE, DOC_MD, SPEC_MD, LEDGER, GEN_LEDGER]
        listed = [bool(re.match(r"^#   %s bytes=\d+ lines=\d+ sha256=[0-9a-f]{64}$"
                                % re.escape(name), l)) for (name, _), l in zip(FINGER, HDR)]
        named_in_hdr = {os.path.normcase(os.path.abspath(p)) for _, p in FINGER}
        missing_fp = [os.path.relpath(p, REPO).replace(os.sep, "/") for p in JUDGED
                      if os.path.normcase(os.path.abspath(p)) not in
                      {os.path.normcase(x) for x in named_in_hdr}]
        emit(fh, "  判定面读过=%d 名单条目=%d（下限 %d）未登记=%s"
             % (len(JUDGED), len(HDR), MIN_FINGER, missing_fp or "[]"))
        block(fh, 12, "roster covers every file a judgement reads", [
            ("名单条目下限 MIN_FINGER=%d 达成（与 FINGER 内容无关）" % MIN_FINGER, len(HDR) >= MIN_FINGER),
            ("每条名单都三项齐全（bytes= / lines= / sha256= 各命中 %d/%d 条）"
             % (len([l for l in HDR if "sha256=" in l]), len(HDR)),
             all(("bytes=" in l and "lines=" in l and "sha256=" in l) for l in HDR) and len(HDR) > 0),
            ("判定面读过的每个文件都在名单里（缺: %s）" % (missing_fp or "无"), not missing_fp),
            ("FINGER 与 JUDGED 两个清单互等（谁被加了没人通知，这里就响）",
             named_in_hdr == {os.path.normcase(os.path.abspath(p)) for p in JUDGED}),
            ("名单里的路径全部真实存在（否则指纹是量在空文件上）", all(os.path.isfile(p) for p in JUDGED)),
            ("listed 集合与 FINGER 同源（头注每条都能反解出路径，不是纯文本装饰）",
             len(HDR) == len(FINGER) and all(listed)),
        ])
        # __REST__
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
    print("DECODE ok utf8=%s cr=%d bytes=%d lines=%d sha256=%s"
          % (utf8_ok, cr, len(raw), raw.count(b"\n"), hashlib.sha256(raw).hexdigest()))
    print("  复算：sha256sum artifacts/evidence/T-2026-10-15-009-green.txt")
    if FAULTS:
        return 3
    return total if utf8_ok and cr == 0 else 3


if __name__ == "__main__":
    sys.exit(main())
