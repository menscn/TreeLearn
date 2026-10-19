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
# 样本派生必须走被判脚本同一套归一化/屏蔽/窗口函数，否则「自检以为会命中的样本」与
# 「守门真正看到的文本」又是两份实现（D4 的成因）。
sys.path.insert(0, os.path.join(REPO_ROOT, ".codex", "scripts"))

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
CODE_REVIEWER_MD = ".codex/agents/code-reviewer.md"
CODE_REVIEWER_TOML = ".codex/agents/code-reviewer.toml"
REVIEW_TEMPLATE = "Docs/REVIEW.md"
REVIEW_ARCHIVE_DIR = "Docs/reviews"
CHECK_REVIEW = ".codex/scripts/check_code_reviewer_agent.py"

# 先跑一遍「未注入」的基线：守门对健康的副本必须转绿，否则下面的反向用例没有意义。
BASELINES = (CHECK_INTENT, CHECK_ROADMAP, CHECK_TEMPLATE, CHECK_SPEC, CHECK_REVIEW)

# 需要「把目标文件当参数传给守门」的用例（回放校验器读的是参数指定的实例文件）。
ARGS_BY_CASE = {
    "replay:template-is-not-an-instance": ["{target}"],
    "replay:terminal-value-on-next-line": ["{target}"],
}

# D4：正控样本一律**从被判文件现场切片**，不再手抄。
# 手抄的三段样本（旧 STOLEN_LONG/SHORT/TRIM）在 002 片改写 pm-discovery 那一行后立刻失配，
# 而且失配方向是「正控失效、自检仍报绿」——fail-open。现在每条样本都要同时满足：
# ① 它确实是 pm-discovery 归一化文本里的连续片段（找不到原文即仪器故障，不是「用例通过」）；
# ② 它不含路径片段与占位符词，免得 mask_paths 与「no placeholder tokens」两条判据掺进来；
# ③ 它落在预期的阈值档位上（60 字档响/不响、40 字档响），档位关系是断言出来的而不是注释里说的。
PM_SKILL = ".codex/skills/pm-discovery/SKILL.md"
SAMPLE_SPEC_LONG = ("long", 89, "fire60")      # 60 字档必须响（>1 命中）
SAMPLE_SPEC_EXACT = ("exact60", 60, "quiet60")  # 60 字档恰好 1 命中（允许），40 字档必须响
SAMPLE_SPEC_TRIM = ("trim", 44, "quiet60")      # 60 字档 0 命中（够不到窗口），40 字档必须响


def derive_sample(skill_text, length, tier):
    """从 pm-discovery 现文里取第一段满足档位关系的样本；取不到就返回 None（判仪器故障）。"""
    from harness_check_lib import PLACEHOLDERS, PATH_RE, normalize, overlap_hits
    flat = normalize(skill_text)
    # 从正文起扫：文件头那段 YAML 前置元数据虽然也是「原文」，但拿它当抄袭样本证不到
    # 「抄方法论正文」这件事，读证据的人会以为正控在模拟整句照抄。
    if skill_text.startswith("---"):
        end = skill_text.find("\n---", 3)
        if end != -1:
            flat = flat[len(normalize(skill_text[:end + 4])):]
    for i in range(0, max(0, len(flat) - length) + 1):
        candidate = flat[i:i + length]
        if PATH_RE.search(candidate) or any(p in candidate for p in PLACEHOLDERS):
            continue
        if candidate not in flat:  # ①「样本必须能在被判文本里找到原文」
            continue
        hits60 = len(overlap_hits(candidate, skill_text, 60))
        hits40 = len(overlap_hits(candidate, skill_text, 40))
        if hits40 < 1:
            continue
        if tier == "fire60" and hits60 > 1:
            return candidate
        if tier == "quiet60" and hits60 <= 1:
            return candidate
    return None


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
     None, SAMPLE_SPEC_LONG,
     "shares at most 1 run(s) of 60 chars with pm-discovery", "append_sample"),
    ("intent:copied-methodology-short", CHECK_INTENT, INTENT_AGENT,
     None, SAMPLE_SPEC_EXACT,
     "shares at most 0 run(s) of 40 chars with pm-discovery", "append_sample"),
    ("intent:copied-methodology-44-chars", CHECK_INTENT, INTENT_AGENT,
     None, SAMPLE_SPEC_TRIM,
     "shares at most 0 run(s) of 40 chars with pm-discovery", "append_sample"),
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
     "Phase B（下一批） every path inside 执行提示词 exists", "replace_all"),
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
    # B1（ROADMAP）：code-reviewer 角色与 Docs/REVIEW.md 骨架成对存在。每条判据各配一条
    # 注入用例——这套判据里凡是「两边现场派生再比对」的，都必须在单边被改动时转红。
    ("review:agent-file-missing", CHECK_REVIEW, CODE_REVIEWER_MD, None, None,
     "the five inputs exist", "delete"),
    ("review:template-file-missing", CHECK_REVIEW, REVIEW_TEMPLATE, None, None,
     "the five inputs exist", "delete"),
    ("review:verifier-section-uncovered", CHECK_REVIEW, CODE_REVIEWER_MD,
     "<rules>", "<scope>",
     "agent section names cover verifier.md", "replace"),
    # 「测试充分性」在角色正文里出现两处（职责清单与输出格式的小节清单），所以这条必须
    # 全替——只替第一处时第二处仍让子串判定放行，那正是要防的「一处措辞撑起一条判据」。
    ("review:dimension-dropped", CHECK_REVIEW, CODE_REVIEWER_MD,
     "测试充分性", "测试覆盖度",
     "agent names the four review dimensions", "replace_all"),
    ("review:archive-path-missing", CHECK_REVIEW, REVIEW_TEMPLATE,
     "Docs/reviews/", "Docs/notes/",
     "review template declares an archive path", "replace_all"),
    # 声明了落点却没这个目录 = 死指针：角色第一次写报告时才发现目录不在场，那已经是运行时事故。
    ("review:archive-dir-missing", CHECK_REVIEW, REVIEW_ARCHIVE_DIR, None, None,
     "the declared archive directory exists on disk", "delete_dir"),
    ("review:severity-merged", CHECK_REVIEW, REVIEW_TEMPLATE,
     "## 非阻塞建议", "## 建议",
     "report separates blocking from non-blocking", "replace"),
    ("review:location-dropped", CHECK_REVIEW, REVIEW_TEMPLATE,
     "文件:行", "某处",
     "every finding must carry a file:line location", "replace_all"),
    # 两条裸下限各自承重：抽干骨架字段、抽干任务卡 Verification report 字段，
    # 都会让「两边不重名」这条自动成立——只有下限能挡住这种协同缩表。
    ("review:field-list-drained", CHECK_REVIEW, REVIEW_TEMPLATE,
     "- **", None,
     "review template declares its own field list", "drop_matching"),
    ("review:task-fields-drained", CHECK_REVIEW, ".codex/tasks/_TEMPLATE.md",
     "- Gaps:\n- Risks:\n", "",
     "task template still defines the Verification report fields", "replace"),
    ("review:field-collision-injected", CHECK_REVIEW, REVIEW_TEMPLATE,
     None, "\n- **Gaps**：\n",
     "report fields do not redefine task-card verification fields", "append"),
    ("review:placeholder-injected", CHECK_REVIEW, REVIEW_TEMPLATE,
     None, "\nTODO\n",
     "role text and report skeleton carry no placeholder tokens", "append"),
    # .md 与 .toml 是同一份角色正文的两份拷贝：只改一处必须被判红，否则「加载的角色」
    # 与「读的角色说明」各说各话而无人报警。
    ("review:toml-md-drift", CHECK_REVIEW, CODE_REVIEWER_MD,
     None, "\n本段只写在 markdown 里，用来证明打包正文不是逐字拷贝。\n",
     "agent .toml body equals the agent markdown verbatim", "append"),
    ("review:toml-unparsable", CHECK_REVIEW, CODE_REVIEWER_TOML,
     None, "\nbroken = [\n",
     "agent .toml parses as a Codex agent file", "append"),
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


def run_dir_case(root, checker, path, target, label):
    """目录型注入：删掉整个目录，断言「该目录必须在场」这条判据转红，随后按现文还原。"""
    if not os.path.isdir(path):
        return "HARNESS", "target directory does not exist: " + target
    shutil.rmtree(path)
    try:
        proc = run_checker(root, checker)
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
        src = os.path.join(REPO_ROOT, target.replace("/", os.sep))
        if os.path.isdir(src):
            shutil.copytree(src, path, ignore=shutil.ignore_patterns("__pycache__"))


def run_case(root, case):
    name, checker, target, old, new, label, op = case
    path = os.path.join(root, target.replace("/", os.sep))
    # 目录型目标没有「原文」可读（read_text 在目录上会抛 IsADirectoryError，那会被记成
    # 仪器故障），所以它走自己的还原路径：按 REPO_ROOT 现文把目录与其内容摆回去。
    if op == "delete_dir":
        return run_dir_case(root, checker, path, target, label)
    original = read_text(path) if os.path.exists(path) else None
    try:
        if op == "none":
            pass
        elif op == "delete":
            os.remove(path)
        elif op == "append":
            write_text(path, original + new)
        elif op == "append_sample":
            skill_path = os.path.join(root, PM_SKILL.replace("/", os.sep))
            sample = derive_sample(read_text(skill_path), new[1], new[2])
            if sample is None:
                # 取不到样本 = 正控失效。它必须以「没跑完」的身份出现（rc=3），
                # 而不是悄悄少跑一条用例继续报绿——那正是 D4 记的 fail-open 形态。
                return "HARNESS", (
                    "no derivable 正控 sample for tier {0} (length {1}) from {2}: "
                    "the judged text changed".format(new[2], new[1], PM_SKILL))
            write_text(path, original + "\n" + sample + "\n")
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

    # D4 的复发防护：抄样本这件事一旦回到「用例里写死一段文本」就重新变成 fail-open，
    # 而那时每条用例照样会绿。所以这里不检查样本本身（派生自现文，天然对得上，查了等于没查），
    # 而是检查**形状**——三条抄检用例必须走 append_sample，出现字面量样本即判仪器故障。
    literal_copy_cases = [c[0] for c in CASES
                          if c[0].startswith("intent:copied-methodology") and c[6] != "append_sample"]
    if literal_copy_cases:
        print("SELFTEST harness error: 抄检用例又带回字面量样本（D4 复发）：{0}".format(
            literal_copy_cases))
        print("OVERALL: INCONCLUSIVE")
        print("EXITCODE=3")
        return 3

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
                   CHECK_SPEC: "check_spec_author_agent",
                   CHECK_REVIEW: "check_code_reviewer_agent"}[checker]
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

