"""文档类守门的双向自检（T-2026-09-21-028 / T-2026-09-21-031）。

用法（从任意目录）：
    python .codex/scripts/dev/checkers_selftest.py

做法：把守门真正需要的那部分（.codex/、AGENTS.md、Docs/）拷进一个临时目录，
在副本上注入一条违规，跑副本里的守门脚本，断言「该违规必须被判 FAIL」（反向），
随后还原文件；开始时先跑一次未注入的基线，断言每条守门都绿（正向——D1 之后这条
覆盖的正是「路由已入库、工作树无增量」这一形状，旧版在该形状下恒 INCONCLUSIVE）。
全程不动工作区。

退出码：0 = 全部用例符合预期；1 = 有用例不符合预期；3 = 自检自身故障（结论不可信）。
「自检自身故障」与「用例不符合预期」必须分开：前者是「没跑完」，后者是「跑完发现坏了」。
"""

import io
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))

INTENT_AGENT = ".codex/agents/intent-capturer.md"
VERIFIER_AGENT = ".codex/agents/verifier.md"
PROJECT_BRIEF = ".codex/context/project-brief.md"
ROADMAP = ".codex/ROADMAP.md"
AGENTS = "AGENTS.md"
TRAE_README = ".codex/README.md"

CHECK_INTENT = ".codex/scripts/check_intent_capturer_agent.py"
CHECK_ROADMAP = ".codex/scripts/check_roadmap_and_routing.py"
CHECK_TEMPLATE = ".codex/scripts/check_intent_template.py"
CHECK_REPLAY = ".codex/scripts/dev/check_intent_replay.py"
TEMPLATE = ".codex/templates/INTENT.md"
SPEC_AGENT = ".codex/agents/spec-author.md"
SPEC_TEMPLATE = ".codex/templates/SPEC.md"
CHECK_SPEC = ".codex/scripts/check_spec_author_agent.py"

# 先跑一遍「未注入」的基线：守门对健康的副本必须转绿，否则下面的反向用例没有意义。
BASELINES = (CHECK_INTENT, CHECK_ROADMAP, CHECK_TEMPLATE, CHECK_SPEC)

# 需要「把目标文件当参数传给守门」的用例（回放校验器读的是参数指定的实例文件）。
ARGS_BY_CASE = {
    "replay:template-is-not-an-instance": ["{target}"],
    "replay:terminal-value-on-next-line": ["{target}"],
}

# 三段都是 pm-discovery 的原文，长度刻意选在「一档能挡、一档挡不住」的位置（命中数
# 由 normalize 后的字符数与阈值推出，实测见 T-2026-10-15-002-green 证据）：
# STOLEN_LONG 89 字 → 60 字档 30 命中（>1 即 FAIL）；
# STOLEN_SHORT 恰好 60 字 → 60 字档 1 命中（落在 ≤1 的允许范围内），40 字档 21 命中。
STOLEN_LONG = (
    "用户说出来的几乎都是方案（「给我加个导出按钮」），"
    "要问的是问题（「你现在怎么把内容带出去，卡在哪一步」）。"
    "在至少写下一条**不做会怎样**的具体后果之前，不要进入下面的收敛判定。"
)
STOLEN_SHORT = (
    "本地优先、无远程服务端**：任何引入云端账号、云同步、云备份、远程服务的方案都属于新需求，"
    "必须回到需求澄清，不能夹带进现"
)
# 只有 44 字（实测 normalize 后 44 字符），用来钉住 40 字档的阈值：
# 实测 60 字档 0 命中（不足以触发），40 字档 5 命中（必须触发）。
STOLEN_TRIM = "本地优先、无远程服务端**：任何引入云端账号、云同步、云备份、远程服务的方案都属于新需求"

# (用例名, 守门脚本, 目标文件, 原串, 新串, 期望出现的 FAIL 标签, 操作)
CASES = [
    ("intent:agent-file-missing", CHECK_INTENT, INTENT_AGENT, None, None,
     "file exists: .codex/agents/intent-capturer.md", "delete"),
    ("intent:prerequisite-file-gone", CHECK_INTENT, PROJECT_BRIEF, None, None,
     "prerequisite path exists on disk: .codex/context/project-brief.md", "delete"),
    ("intent:field-marker-unreadable", CHECK_INTENT, INTENT_AGENT,
     "OUTPUT-FIELDS:", "OUTPUT-FIELDSX:",
     "both field markers are readable", "replace"),
    ("intent:field-set-drift", CHECK_INTENT, INTENT_AGENT,
     "OUTPUT-FIELDS: id |", "OUTPUT-FIELDS: idX |",
     "agent output field set equals the template FIELDS marker", "replace"),
    ("intent:field-not-backticked", CHECK_INTENT, INTENT_AGENT,
     "- `id`", "- `idd`",
     "every template field appears backticked in the output-format section", "replace"),
    ("intent:section-list-drift", CHECK_INTENT, INTENT_AGENT,
     "- `Core flow（核心流程）`", "- `Coreflow（核心流程）`",
     "listed body sections equal the template ## titles", "replace"),
    ("intent:required-section-missing", CHECK_INTENT, INTENT_AGENT,
     "## 禁区", "## 禁止",
     "required sections present", "replace"),
    ("intent:verifier-section-uncovered", CHECK_INTENT, VERIFIER_AGENT,
     "## 职责清单", "## 附加要求\n\n- 占位。\n\n## 职责清单",
     "section set covers .codex/agents/verifier.md", "replace"),
    ("intent:writable-scope-lost", CHECK_INTENT, INTENT_AGENT,
     "可写：`Docs/intent/**`", "只读：`Docs/intent/**`",
     "declares writable scope Docs/intent/**", "replace"),
    ("intent:readonly-scope-lost", CHECK_INTENT, INTENT_AGENT,
     "`backend/**`、`.codex/tasks/**`", "`backend/**`、`.codex/task/**`",
     "declares read-only scopes", "replace"),
    ("intent:prerequisite-name-missing", CHECK_INTENT, INTENT_AGENT,
     "`.codex/context/project-brief.md`](../context/project-brief.md)",
     "`.codex/context/project-brieff.md`](../context/project-brief.md)",
     "names all 3 prerequisite files", "replace"),
    ("intent:terminal-state-lost", CHECK_INTENT, INTENT_AGENT,
     "needs-more-info", "need-more-info",
     "handles terminal state: needs-more-info", "replace_all"),
    ("intent:handoff-checklist-lost", CHECK_INTENT, INTENT_AGENT,
     "移交清单", "交接清单",
     "accepted branch produces a handoff checklist", "replace_all"),
    ("intent:handoff-target-lost", CHECK_INTENT, INTENT_AGENT,
     "requirement-decomposer", "requirement-splitter",
     "handoff target is requirement-decomposer", "replace_all"),
    ("intent:rejected-branch-lost", CHECK_INTENT, INTENT_AGENT,
     "不得删除", "可以删除",
     "rejected branch keeps the intent file", "replace_all"),
    ("intent:gap-escalation-lost", CHECK_INTENT, INTENT_AGENT,
     "未知项", "已确认项",
     "background-gap escalation is written as a prohibition in one clause", "replace_all"),
    ("intent:gap-semantics-reversed", CHECK_INTENT, INTENT_AGENT,
     "，**不得凭印象补一个看起来合理的答案**。",
     "，**按常识补一个看起来合理的答案即可**。",
     "background-gap escalation is written as a prohibition in one clause", "replace"),
    ("intent:placeholder-injected", CHECK_INTENT, INTENT_AGENT,
     None, "\nTODO\n",
     "no placeholder tokens", "append"),
    ("intent:copied-methodology-long", CHECK_INTENT, INTENT_AGENT,
     None, "\n" + STOLEN_LONG + "\n",
     "shares at most 1 run(s) of 60 chars with pm-discovery", "append"),
    ("intent:copied-methodology-short", CHECK_INTENT, INTENT_AGENT,
     None, "\n" + STOLEN_SHORT + "\n",
     "shares at most 0 run(s) of 40 chars with pm-discovery", "append"),
    ("intent:copied-methodology-44-chars", CHECK_INTENT, INTENT_AGENT,
     None, "\n" + STOLEN_TRIM + "\n",
     "shares at most 0 run(s) of 40 chars with pm-discovery", "append"),
    ("roadmap:file-missing", CHECK_ROADMAP, ROADMAP, None, None,
     "file exists: .codex/ROADMAP.md", "delete"),
    ("roadmap:phase-section-renamed", CHECK_ROADMAP, ROADMAP,
     "## Phase C（依赖外部条件）", "## Phase C（条件未定）",
     "phase section exists: Phase C（依赖外部条件）", "replace"),
    ("roadmap:entries-unreadable", CHECK_ROADMAP, ROADMAP,
     "### ", "#### ",
     "Phase B（下一批） has >= 7 entries", "replace_all"),
    ("roadmap:field-renamed", CHECK_ROADMAP, ROADMAP,
     "- **依赖**：`.codex/agents/verifier.md`", "- **前置**：`.codex/agents/verifier.md`",
     "Phase B（下一批） every entry carries all 8 field names", "replace"),
    ("roadmap:prompt-too-short", CHECK_ROADMAP, ROADMAP,
     "- **执行提示词**：先读 `.codex/agents/verifier.md`",
     "- **执行提示词**：照这份路线做完就行。",
     "Phase B（下一批） every 执行提示词 is >= 40 chars", "replace_line"),
    ("roadmap:prompt-dead-path", CHECK_ROADMAP, ROADMAP,
     ".codex/HARNESS.md", ".codex/HARNES.md",
     "Phase B（下一批） every path inside 执行提示词 exists", "replace"),
    ("roadmap:vague-source", CHECK_ROADMAP, ROADMAP,
     "- **开源来源**：`https://github.com/obra/superpowers` → `skills/requesting-code-review",
     "- **开源来源**：网上找的，细节回头再补。",
     "Phase B（下一批） every 开源来源 cites github.com or a skills path", "replace_line"),
    ("roadmap:phase-b-capability-lost", CHECK_ROADMAP, ROADMAP,
     "eval-keeper", "evalguard",
     "Phase B covers the seven planned capabilities", "replace_all"),
    ("roadmap:phase-b-spec-author-lost", CHECK_ROADMAP, ROADMAP,
     "spec-author", "specwriter",
     "Phase B covers the seven planned capabilities", "replace_all"),
    ("roadmap:phase-c-capability-lost", CHECK_ROADMAP, ROADMAP,
     "release-gate", "releasegate",
     "Phase C covers the three planned capabilities", "replace_all"),
    ("roadmap:placeholder-injected", CHECK_ROADMAP, ROADMAP,
     None, "\nTBD\n",
     "no placeholder tokens in ROADMAP", "append"),
    ("roadmap:agents-row-deleted", CHECK_ROADMAP, AGENTS,
     "| `android/**` | [Android](.codex/rules/android.md) | 分析、修改前；完成前执行 Java-only guard |\n",
     "",
     "AGENTS.md change contains no deletion line", "replace"),
    ("roadmap:agents-existing-row-vanished", CHECK_ROADMAP, AGENTS,
     "| `android/**` | [Android](.codex/rules/android.md) | 分析、修改前；完成前执行 Java-only guard |\n",
     "",
     "every pre-existing routing row and case record survives verbatim", "replace"),
    # D1 之后，路由判据读的是 AGENTS.md 全文而不是 git 增量，因此每条当前态判据
    # 各配一条注入用例。注意这些用例都是**多因**的：改一行路由会同时让
    # 「HEAD 的每行逐字幸存」与「不得出现删除行」转红，所以期望标签只押自己要证的那条。
    ("roadmap:routing-floor-load-bearing", CHECK_ROADMAP, CHECK_ROADMAP,
     "MIN_ROUTE_ROWS = 12", "MIN_ROUTE_ROWS = 999",
     "AGENTS.md routing table currently has >=", "replace"),
    ("roadmap:routing-table-truncated", CHECK_ROADMAP, AGENTS,
     "](.codex/rules/", None,
     "AGENTS.md routing table currently has >=", "drop_matching"),
    ("roadmap:routing-trigger-lost", CHECK_ROADMAP, AGENTS,
     "](.codex/context/project-brief.md)", "](.codex/README.md)",
     "current routing rows cover all four triggers", "replace"),
    ("roadmap:routing-target-dead", CHECK_ROADMAP, AGENTS,
     "](.codex/rules/android.md)", "](.codex/rules/androidx.md)",
     "every current routing-row target exists", "replace"),
    ("roadmap:readme-entry-removed", CHECK_ROADMAP, TRAE_README,
     ".codex/templates/", ".codex/template/",
     "目录结构 section lists the four new entries", "replace"),
    # 回放校验器的负向对照：模板本身不是一份可用实例（它的「终态」一栏并列三种取值），
    # 因此必须被判 FAIL。这条用来证明该脚本不是「对什么都说 OK」。
    ("replay:template-is-not-an-instance", CHECK_REPLAY, TEMPLATE, None, None,
     "decision record states exactly one terminal state", "none"),
    # 028 缺口条款的第二/第三条判据（独立复核 C-2 的两种形态）：
    # ① 把禁令整句换成「「不得编造」这条规矩不再适用」——三个词还在，禁令已废；
    # ② 保留禁令原文、追加一句把它否掉——同行三词判定与「逐字保留」判定都会放行。
    ("intent:gap-prohibition-negated", CHECK_INTENT, INTENT_AGENT,
     "**不得凭印象补一个看起来合理的答案**。", "「不得编造」这条规矩不再适用。",
     "background-gap escalation keeps the canonical prohibition clause", "replace"),
    ("intent:gap-prohibition-hedged", CHECK_INTENT, INTENT_AGENT,
     "**不得凭印象补一个看起来合理的答案**。",
     "**不得凭印象补一个看起来合理的答案**，与其它约定冲突时不再适用。",
     "no line in the agent file negates or exempts the background-gap rule", "replace"),
    # 独立复核第二轮 A2：把禁令换一个**名单外**的同义词废掉（「作废」不在旧名单里）。
    ("intent:gap-prohibition-synonym", CHECK_INTENT, INTENT_AGENT,
     "**不得凭印象补一个看起来合理的答案**。",
     "不得凭印象补一个看起来合理的答案——该要求已作废。",
     "no line in the agent file negates or exempts the background-gap rule", "replace"),
    # 独立复核第二轮 A4：原句一字不动，把降级写在**另一行**（旧版只看首行，放行）。
    ("intent:gap-prohibition-crossline", CHECK_INTENT, INTENT_AGENT, None,
     "\n- 上述背景缺口要求在赶工期时可以忽略。\n",
     "no line in the agent file negates or exempts the background-gap rule", "append"),
    # 030 的独立判据：注记只与脚本常量比对时，「协同缩表」可以零告警降字段。
    # 这里只把模板正文的一个 ## 小节降级为 ###：字段名仍能在注记里找到（第一条断言放行），
    # 但「注记 == 元数据键 ∪ ## 标题」立刻不等。
    # 独立复核第三轮 B1 的**耦合洞**：只扫「提到 缺口/未知项 的行」时，把否定写到另一行即漏，
    # 连名单内的 `作废` 也逃得掉。现在改为全文扫描，这一条必须转红。
    ("intent:gap-waiver-uncoupled-line", CHECK_INTENT, INTENT_AGENT, None,
     "\n- 上述规则于赶工期时作废。\n",
     "no line in the agent file negates or exempts the background-gap rule", "append"),
    ("template:marker-vs-section-drift", CHECK_TEMPLATE, TEMPLATE,
     "## Core flow（核心流程）", "### Core flow（核心流程）",
     "FIELDS marker equals the template own metadata keys + ## titles", "replace"),
    # 030 的下界锚：协同缩表（正文 + 注记 + 028 列表 + 脚本常量一起删）是上一条挡不住的，
    # 只有「必需小节来自契约本身」这条能挡。这里只删正文里的一行 ## 标题，验证它读的是规格。
    ("template:required-section-dropped", CHECK_TEMPLATE, TEMPLATE,
     "## Core flow（核心流程）\n", "",
     "template body has exactly the 9 required ## sections", "replace"),
    # 终态取值必须落在「终态：」**同一行**：旧正则用 \s* 会跨行吃到下一行的取值。
    ("replay:terminal-value-on-next-line", CHECK_REPLAY, TEMPLATE,
     "- 终态：accepted / rejected / needs-more-info", "- 终态：\naccepted",
     "decision record states exactly one terminal state", "replace"),
    # 规格自检（轻量版，2026-09-21 按用户决定简化）：判据只剩几条，每条配一条反向用例。
    # 旧的 17 条判据与「每条分支都要有注入用例」的自闸门已删——规格阶段不再当门禁对象。
    ("spec:file-missing", CHECK_SPEC, SPEC_TEMPLATE, None, None,
     "spec file exists", "delete"),
    ("spec:content-class-lost", CHECK_SPEC, SPEC_TEMPLATE,
     "界面方案", "界面布局",
     "declares the four content classes", "replace_all"),
    ("spec:placeholder-injected", CHECK_SPEC, SPEC_TEMPLATE,
     None, "\nTODO\n",
     "no placeholder tokens", "append"),
    ("spec:bare-px-injected", CHECK_SPEC, SPEC_TEMPLATE,
     None, "\n按 14px 排\n",
     "no bare hex or ad-hoc px values", "append"),
]


# The old .trae process required append-only AGENTS history. Treelearn's authorized
# migration replaces that process root, so those two historical mutation cases are retired.
CASES = [c for c in CASES if c[0] not in {
    "roadmap:agents-row-deleted",
    "roadmap:agents-existing-row-vanished",
}]

def read_text(path):
    with io.open(path, encoding="utf-8", newline="") as f:
        return f.read()


def write_text(path, text):
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)


def build_workspace(root):
    shutil.copytree(os.path.join(REPO_ROOT, ".codex"), os.path.join(root, ".codex"),
                    ignore=shutil.ignore_patterns("__pycache__"))
    shutil.copytree(os.path.join(REPO_ROOT, "Docs"), os.path.join(root, "Docs"),
                    ignore=shutil.ignore_patterns("__pycache__"))
    # 基线提交用 HEAD 版本的 AGENTS.md，提交后再把工作区版本摆回去：
    # 这样副本里的「未提交增量」与真实仓库同形，删除行判据与「HEAD 每行逐字幸存」
    # 判据在副本里量到的东西才和在外面直跑时一致（两者读的都是 HEAD ↔ 工作树这一对）。
    head = subprocess.run(["git", "show", "HEAD:" + AGENTS], cwd=REPO_ROOT,
                          capture_output=True, text=True,
                          encoding="utf-8", errors="replace")
    if head.returncode != 0:
        return False, "git show HEAD:{0} failed: {1}".format(
            AGENTS, (head.stderr or "").strip()[:160])
    write_text(os.path.join(root, AGENTS), head.stdout)
    longest = 0
    for base, _dirs, files in os.walk(root):
        for nm in files:
            longest = max(longest, len(os.path.join(base, nm)))
    if longest >= 250:
        return False, "longest path {0} is too close to the 259 limit".format(longest)
    for args in (["init", "-q"],
                 ["-c", "user.email=selftest@example.invalid",
                  "-c", "user.name=selftest", "add", "-A"],
                 ["-c", "user.email=selftest@example.invalid",
                  "-c", "user.name=selftest", "commit", "-qm", "baseline"]):
        proc = subprocess.run(["git"] + args, cwd=root, capture_output=True,
                              text=True, encoding="utf-8", errors="replace")
        if proc.returncode != 0:
            return False, "git {0} failed: {1}".format(args[0], (proc.stderr or "").strip()[:160])
    # 提交完基线后，把工作区版本的 AGENTS.md 摆回去，让副本的 git diff 与真实仓库同形。
    shutil.copy2(os.path.join(REPO_ROOT, AGENTS), os.path.join(root, AGENTS))
    return True, "longest path {0}".format(longest)


def run_checker(root, checker, extra=()):
    cmd = [sys.executable, "-X", "utf8", checker.replace("/", os.sep)] + list(extra)
    return subprocess.run(cmd,
                          cwd=root, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


def run_case(root, case):
    name, checker, target, old, new, label, op = case
    path = os.path.join(root, target.replace("/", os.sep))
    original = read_text(path) if os.path.exists(path) else None
    try:
        if op == "none":
            pass
        elif op == "delete":
            os.remove(path)
        elif op == "append":
            write_text(path, original + new)
        elif op == "drop_matching":
            if original is None:
                return "HARNESS", "target file does not exist: " + target
            lines = original.split("\n")
            kept = [l for l in lines if old not in l]
            if len(kept) == len(lines):
                return "HARNESS", "drop anchor matched no line: {0!r}".format(old)
            write_text(path, "\n".join(kept))
        elif op in ("replace", "replace_all", "replace_line"):
            if original is None:
                return "HARNESS", "target file does not exist: " + target
            if op == "replace":
                if old not in original:
                    return "HARNESS", "anchor not found: {0!r}".format(old)
                write_text(path, original.replace(old, new, 1))
            elif op == "replace_all":
                if old not in original:
                    return "HARNESS", "anchor not found: {0!r}".format(old)
                write_text(path, original.replace(old, new))
            else:
                lines = original.split("\n")
                hit = [i for i, l in enumerate(lines) if l.startswith(old)]
                if len(hit) != 1:
                    return "HARNESS", "line anchor matched {0} times".format(len(hit))
                lines[hit[0]] = new
                write_text(path, "\n".join(lines))
        else:
            return "HARNESS", "unknown op: " + op

        extra = [a.replace("{target}", target) for a in ARGS_BY_CASE.get(name, [])]
        proc = run_checker(root, checker, extra)
        out = proc.stdout or ""
        fired = [l for l in out.splitlines() if l.startswith("FAIL ") and label in l]
        if not fired:
            seen = [l for l in out.splitlines() if l.startswith("FAIL ")]
            return "MISMATCH", "expected FAIL label, rc={0}, seen={1}".format(
                proc.returncode, seen[:3])
        if proc.returncode != 1:
            return "MISMATCH", "FAIL line printed but exit code is {0}".format(proc.returncode)
        return "OK", "rc=1, " + fired[0].strip()
    finally:
        if original is not None and os.path.exists(os.path.dirname(path)):
            write_text(path, original)


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    print("Command: python .codex/scripts/dev/checkers_selftest.py")
    print("Mode: full ({0} injected-violation cases + {1} baselines)".format(
        len(CASES), len(BASELINES)))

    root = tempfile.mkdtemp(prefix="tlx-selftest-")
    ok = skipped = mismatch = harness = 0
    try:
        built, note = build_workspace(root)
        print("Workspace: {0}".format(root))
        print("Workspace note: {0}".format(note))
        if not built:
            print("SELFTEST aborted: could not build a healthy workspace")
            print("OVERALL: INCONCLUSIVE")
            print("EXITCODE=3")
            return 3

        for checker in BASELINES:
            proc = run_checker(root, checker)
            tag = {CHECK_INTENT: "check_intent_capturer_agent",
                   CHECK_ROADMAP: "check_roadmap_and_routing",
                   CHECK_TEMPLATE: "check_intent_template",
                   CHECK_SPEC: "check_spec_author_agent"}[checker]
            if proc.returncode != 0:
                print("BASELINE FAIL {0} rc={1}".format(tag, proc.returncode))
                print((proc.stdout or "").strip()[-600:])
                print("OVERALL: INCONCLUSIVE")
                print("EXITCODE=3")
                return 3
            print("BASELINE OK {0} rc=0".format(tag))

        for i, case in enumerate(CASES, 1):
            status, detail = run_case(root, case)
            if status == "OK":
                ok += 1
            elif status == "MISMATCH":
                mismatch += 1
            else:
                harness += 1
            print("CASE {0:02d} {1:<9} {2} - {3}".format(i, status, case[0], detail))

        print("SELFTEST cases={0} ok={1} mismatch={2} harness_errors={3} skipped={4}".format(
            len(CASES), ok, mismatch, harness, skipped))
    except Exception as exc:  # noqa: BLE001 - 自检自身故障必须与「用例不匹配」分开
        print("SELFTEST harness error: {0}: {1}".format(type(exc).__name__, exc))
        print("OVERALL: INCONCLUSIVE")
        print("EXITCODE=3")
        return 3
    finally:
        try:
            shutil.rmtree(root, ignore_errors=True)
        except Exception as exc:
            print("WARN cleanup failed, workspace kept: {0}".format(exc))

    if harness:
        print("OVERALL: INCONCLUSIVE")
        print("EXITCODE=3")
        return 3
    if mismatch:
        print("OVERALL: FAIL")
        print("EXITCODE=1")
        return 1
    print("OVERALL: PASS")
    print("EXITCODE=0")
    return 0


if __name__ == "__main__":
    sys.exit(main())

