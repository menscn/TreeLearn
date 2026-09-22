# Treelearn Harness Task: spec-author 角色与 SPEC.md 模板

id: T-2026-09-21-035
title: spec-author 角色与 SPEC.md 模板
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-21-ai-persona-builtin-034.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 260
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

Parent summary: 见父 `requirement` 任务 `T-2026-09-21-ai-persona-builtin-034.md`（AI 人设——内置人设与主题级切换）。本任务落地 `.trae/ROADMAP.md` 的 `B0 spec-author 角色与 SPEC.md 模板`（由原 C1 提前并扩容而来）。

本任务相关细节：
- Problem/background: Treelearn 的六阶段里 Stage 2（Design）没有归属——`.trae/ROADMAP.md` 原 C1 把它当成纯技术规格，而且卡在 B1、B4 之后。实际缺口是 Stage 2 的**界面一半**没人负责，而它并不依赖前面任何条目。
- Users/scenarios: 用户是单人开发者，需求收敛完（intent accepted）之后、拆任务之前，需要有人把「做成什么样」写成一份能直接照着拆的规格。
- Expected outcome: 存在一个可调用的 `spec-author` 角色 + 一份字段契约模板 + 一个能机械验证两者的守门脚本；守门自身还要求「每条检测分支都配一条注入用例」。
- Inputs/outputs: 输入是已 accepted 的 `Docs/intent/INTENT-*.md`；输出是 `Docs/spec/SPEC-*.md`（本任务只建能力，第一份规格由 `T-2026-09-21-036` 产出）。
- Constraints and trade-offs: 不改业务代码；不新增第三方依赖（守门纯标准库）；`AGENTS.md` 只能追加。
- AI-autonomous decisions: 角色文件的 7 个小节划分；守门的三道自闸门（打印分支必须登记 / 登记分支必须有用例 / 用例不得是孤儿）；用标准库 `ast` 读自检脚本而不是正则。
- User-confirmation decisions: 原 C1 提前并扩容为 B0、只出「需求规格 + 界面方案 + 疑虑点」三节、技术规格等 B4。
- Acceptance evidence: `python -X utf8 .trae/scripts/check_spec_author_agent.py` 退出码 0；`python -X utf8 .trae/scripts/dev/checkers_selftest.py` 全绿。
- Facts: 仓库只有 `android/`；`frontend/`、`backend/` 不存在；设计事实源是 `Docs/04-ux-design/design-system/tokens.json`（机器可读镜像）与原型 `:root`。
- Assumptions: 无。
- Unknowns: 第一份规格到底能不能一次写到位（由 `T-2026-09-21-036` 回答）。
- Risks: 「四类内容」这一条的旧实现只看「类名是否出现在模板文件里」，而模板尾部 `<!-- FIELDS: … -->` 恰好含这四个名字 → 把四个 `##` 标题里的类名全部删掉它仍然通过（独立复核实验 C/E2 实测）。已改为两个机制并用：小节标题解析 + 剥掉 HTML 注释后的原文扫描，实验 C/C2/D 复验均判红。
- Deferred: 规格实例的批量校验（当前只支持显式传一个实例路径）。
- Non-goals: 不写第一份规格；不拆任务；不改任何规则正文。
- Skipped non-critical questions and reason: 守门的退出码约定沿用既有脚本（0/1），未另立新码——仓库已有 `INCONCLUSIVE=3` 的先例，但本守门没有「跑不完」的中间态。

## Goal

提供一个可调用的 `spec-author` 角色、一份四类内容齐备的 `SPEC.md` 模板，以及一个机械验证二者的守门脚本；守门自身断言「每条检测分支都配了注入用例」。

## Scope

### In scope
- 新增 `.trae/agents/spec-author.md`（Design 阶段角色定义）。
- 新增 `.trae/templates/SPEC.md`（规格字段契约模板）。
- 新增 `.trae/scripts/check_spec_author_agent.py`（守门）。
- 修改 `.trae/scripts/dev/checkers_selftest.py`：新增 17 条 `spec:` 注入用例，基线由 3 条增至 4 条。
- 修改 `.trae/ROADMAP.md`：B0 的「范围（可写路径）」补上本任务的真实写集与 `Docs/spec/**`。
- 新增四份证据文件：`artifacts/evidence/T-035-spec-author-green.txt`、`artifacts/evidence/T-035-spec-author-red.txt`、`artifacts/evidence/T-035-verifier-fail-fixes.txt`、`artifacts/evidence/T-035-verifier-round2-followups.txt`。

### Out of scope
- 不产出第一份规格（`T-2026-09-21-036`）。
- 不改任何 `.trae/rules/**` 正文与 `AGENTS.md`。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 既有四个守门与 45 条既有用例必须逐条保持原样通过（GREEN 记录里的回归链读数）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费者是后续会话与 `requirement-decomposer`；守门的输出就是证据。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不改变任何生产代码行为，产物是角色文档、模板与只读校验脚本。

## Documentation impact record

Follow [code-doc alignment](../rules/documentation.md).
- Changed runtime facts: 无（不触生产代码、配置、SQL）。
- Authoritative code/config/migration evidence: `.trae/ROADMAP.md` 的 B0 条目、`.trae/templates/SPEC.md`。
- Documentation surfaces checked: `.trae/ROADMAP.md`、`.trae/agents/intent-capturer.md`（同类角色范式）、`.trae/rules/ux-design.md`（token 规则出处）、`.trae/context/project-brief.md`。
- Documentation files updated: `.trae/ROADMAP.md`（B0 范围与口径注记）、`.trae/context/project-brief.md`（权威文档索引：新增 `Docs/intent/` 两份人设 intent 与并发会话产出的 `Docs/06-delivery/TreeLearn_V1_交付报告.md`）。
- Producer/consumer/sibling/compatibility paths checked: `Docs/intent/README.md` 的命名规则是否可复用到 `Docs/spec/`——可复用，本任务未新建 `Docs/spec/README.md`（留给 033 决定）。
- Superseded phrases/examples reverse-searched: 检索 `C1`、`spec-author`、`cases=44/45` 三个旧表述，全部已处置（见 Handoff）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保持原样。

## Acceptance criteria

- [x] `python -X utf8 .trae/scripts/check_spec_author_agent.py` 退出码 0，**无参运行打印 16 条 OK / 0 FAIL**。
- [x] 再传一个**合规**实例路径时多打印第 17 条 `spec instance is complete and traceable`（第 2 轮复核在副本里用合成合规实例实测该条能转 OK，不是恒红的空见证；仓库内真实实例由 `T-2026-09-21-036` 产出）。
- [x] `.trae/agents/spec-author.md` 含 7 个必需小节（定位与权限 / 触发时机 / 执行前必读 / 职责清单 / 输出格式 / 交接 / 禁区），声明可写 `Docs/spec/**` 与只读 `android/**`、`frontend/**`、`backend/**`、`.trae/tasks/**`。
- [x] `.trae/templates/SPEC.md` 同时含需求规格、界面方案、技术规格、疑虑点四类内容，前三节各带 `来源：`、疑虑点带 `冲突对象：`。
- [x] 角色文件的 `OUTPUT-FIELDS` 注释与模板 `FIELDS` 注释字段集合相等（10 个），且每个字段在「输出格式」小节里以反引号出现。
- [x] 追源规则（`规格里每一条都必须能追到 intent 条目、代码位置或设计 token`）与 token 规则（`不得出现裸 hex 或临场 px`）逐字写在角色文件里；交接目标是 `requirement-decomposer`。
- [x] 守门三道自闸门成立：打印过的分支全部已登记；登记的分支全部有 `CHECK_SPEC` 注入用例；用例无孤儿。
- [x] `python -X utf8 .trae/scripts/dev/checkers_selftest.py` → `cases=62 ok=62 mismatch=0 harness_errors=0`、4 条基线、`OVERALL: PASS`、`EXITCODE=0`。
- [x] 既有守门未被削弱：`validate_tasks` / `check_roadmap_and_routing` / `check_intent_capturer_agent` / `check_intent_template` / `check_context_pack` / `check_pm_discovery_skill` 各自 0 失败。

## TDD execution record

### RED — failing test first

- Test/scenario: 守门首条断言——两份产物必须存在（`.trae/agents/spec-author.md` 与 `.trae/templates/SPEC.md`）。
- Command: python -X utf8 .trae/scripts/check_spec_author_agent.py
- Exit code: 1
- Observed failure: FAIL role file and SPEC template exist — agent=False template=False（在 throwaway 副本里把两份产物移出后实测 `OK_LINES=3  FAIL_LINES=1  EXITCODE=1`；同一运行另有一次未注入的真实红：分支覆盖闸门在用例未写时报 FAIL，把 17 条分支全部列为 without_case）。
- Failure reason validated: 同一命令在产物就位后该断言转 OK（见 GREEN）；覆盖闸门在补上 17 条用例后转 OK。
- 流程缺陷（如实记录，不当事先的红）：守门脚本**写在产物之后**，因此这不是「先写失败测试再实现」的 RED，而是把成品移出后**复现**的红（因果验证）。它只打到首条短路断言，对其余 16 条分支不构成 RED——那部分的重量由覆盖闸门承担。
- 证据与复现方式：`artifacts/evidence/T-035-spec-author-red.txt`（2026-09-21 重出）。三种状态：STATE 0 副本原样 `16 OK / rc=0`；STATE A 移出两份产物 `3 OK / 1 FAIL / rc=1`；STATE B 还原后 `16 OK / rc=0`。复现在 `%TEMP%` 下的全量 `.trae/` + `Docs/` 副本里做，不动真仓库；文件头带 SHA-256 与行数。

### GREEN — minimum implementation

- Implementation: 新增 `.trae/agents/spec-author.md`、`.trae/templates/SPEC.md`、`.trae/scripts/check_spec_author_agent.py`；`.trae/scripts/dev/checkers_selftest.py` 新增 17 条 `spec:` 用例并把守门加入基线；`.trae/ROADMAP.md` 的 B0 范围补全。
- Command: python -X utf8 .trae/scripts/check_spec_author_agent.py
- Exit code: 0
- Passing evidence: 无参运行 `OK_LINES=16  FAIL_LINES=0`、rc=0（`artifacts/evidence/T-035-spec-author-green.txt` 的 `COMMAND A` 段）；第 17 条只在传实例路径时启动（同文件 `COMMAND B` 段：拿模板当实例时它是**唯一**一条 FAIL，rc=1，`problems=['intent path does not exist: …']`）。可核实测值：required=7; missing=[]（小节）；agent=10 template=10 diff=[]（字段集合）；template=4 agent=4 diff=[]（正文字段 == 模板 ## 标题）；measured_named=5 dead=[]（执行前必读里点名的路径全部存在）；required=4; missing=[]（四类内容）。
- Regression evidence: `python .trae/scripts/dev/checkers_selftest.py` → `SELFTEST cases=62 ok=62 mismatch=0 harness_errors=0 skipped=0`、4 条基线 OK、`OVERALL: PASS`、`EXITCODE=0`；回归链逐条 rc=0（`validate_tasks` `SUMMARY files=42 errors=0 warnings=11`，其余五个守门各 `0 check(s) failed`）。证据文件 `artifacts/evidence/T-028-031-checkers-selftest.txt`、`artifacts/evidence/T-035-spec-author-green.txt`、`artifacts/evidence/T-035-verifier-round2-followups.txt`（后两者 2026-09-21 重出，SHA 头与当前文件一致；`files=` 这一读数随并发会话新增任务文件而变，`errors` 始终为 0）。

### REFACTOR — optional

- Cleanup: 首版守门用 `expand()` 展开带 `{0}` 的标签模板，改回纯字符串标签 + 把计数只放在判据 detail 里（少一层易错的格式化）。
- Regression evidence: 重构后 `check_spec_author_agent.py` rc=0、自检 `cases=62 ok=62`。

### TDD or sizing exception — only with prior user approval

- Exception type: 无。
- Reason: 守门脚本本身就是可执行判据，RED/GREEN 由它自己给出，不需要 TDD 例外。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 角色文件与模板的分工照抄 `intent-capturer` 的同族范式（角色定义 7 节、模板尾部 `<!-- FIELDS: ... -->` 注释为字段权威）。
- 「四类内容」这一条同时用两个机制：① 模板 `##` 小节标题解析；② 剥掉 `<!-- -->` 注释后的原文扫描。二者共享同一份输入但机制不同；**首版只做了 ②，且当时声称它与 ①「独立」，实际两个都不是**——注释里的同名文字就足以让它通过，独立复核已证伪并修复（见 `artifacts/evidence/T-035-verifier-fail-fixes.txt` 实验 C/C2）。
- 三道自闸门挂在 `Checks.finish()` 里、并且 `c.check`/`c.ok`/`c.fail` 三种打印方式全部记账；仍看不见绕过 `Checks` 直接 `print()` 的分支（本脚本不用那种写法）。
- 三道自闸门用标准库 `ast` 读 `.trae/scripts/dev/checkers_selftest.py` 的 `CASES` 字面量，取 `CHECK_SPEC` 用例的期望标签与 `BRANCH_LABELS` 双向比对。
- 实例校验（可传一个路径作为参数）目前只做单实例，fail-closed：文件缺失也算问题。
- RED 的因果验证是把两份产物临时移出仓库后实测（不是「我认为它会红」），移出与还原都由脚本完成，未改动内容。

## Verification report

- Verdict: PASS（第 2 轮独立复核，2026-09-21）。第 1 轮为 FAIL，全部条目已修并复验；本轮新发现的 N1–N5 已逐条处置。
- Evidence（第 2 轮，独立 verifier，只读、未改动仓库）：
  - 无参运行 `OK_LINES=16  FAIL_LINES=0`、rc=0；传入**合成合规实例** `OK_LINES=17  FAIL_LINES=0`、rc=0（第 17 条确实能转 OK，不是恒红的空见证）；拿模板当实例 `16 OK + 1 FAIL`、rc=1 且 `problems=['intent path does not exist: …']`，与 GREEN 的 `COMMAND B` 一致。
  - 注入复验全部判红：A（在 `finish()` 之前新增 `c.check` 分支）rc=1 且 `measured_unregistered=['a branch nobody expected']`；B（同位置改用 `c.ok`）rc=1 同样判红；C（只删一个 `##` 标题里的类名）rc=1；C2（四个标题的类名全删、保留尾部 `FIELDS` 注释）rc=1 且 `missing_in_headings` 与 `missing_outside_comments` 同时报四项；D（整节删除界面方案）rc=1（3 条 FAIL）；E（整块删一条 `spec:` 用例）rc=1 `measured_without_case=['handoff target is requirement-decomposer']`。
  - 真仓库未被改动：4 个被测文件 SHA-256 复核前后逐一相等；`checkers_selftest.py` `cases=62 ok=62 mismatch=0 harness_errors=0 skipped=0`、4 条基线、`OVERALL: PASS`、`EXITCODE=0`；五个兄弟守门各 `0 check(s) failed`；`validate_tasks.py` `errors=0`。
  - **N1（低，已修）**：docstring 的自我保证仍被一条更窄注入证伪——`c.finish()` **之后**再打印、且写在 `main()` 内用 `c.check` 打印的分支不被记账（实测 rc=0、`OK_LINES=17`）。已把措辞收窄为「发生在 `c.finish()` 调用之前的分支…都会判红」，并把两道已知边界写进 docstring。守门脚本因此变动，`artifacts/evidence/T-035-spec-author-green.txt` 已按新 SHA 重出（旧件里的 SHA 随之失效）。
  - **N2（低，已修）**：RED 证据缺 SHA-256 头，且其 `STATE A` 的输出形状与当前版本不符（当前版本提前返回时仍打印 3 条闸门 OK）。已重出，见 RED 段的「证据与复现方式」。
  - **N3（低，非缺陷，已记录）**：GREEN 记的 `validate_tasks files=36` 与复核时读数不同，差异全部来自并发会话新增的任务文件，`errors` 始终为 0。重出证据时已把当前读数写入。
  - **N4（提示，已清）**：仓库里留有上一轮 verifier 会话的未跟踪杂项 `.trae/tmp-verifier-listing.txt`。已用 `apply_patch` 删除（未动其它文件）。
  - **N5（提示，已修）**：Scope 未列本任务的证据写集。已补进 In scope。
- Evidence（第 1 轮，独立 verifier，只读、未改动仓库）：
  - 结构与守门读数全部复现通过：`check_spec_author_agent.py` rc=0；`checkers_selftest.py` `cases=62 ok=62`、4 条基线、`OVERALL: PASS`；回归链 6 条各 rc=0。
  - 两份证据文件头部列出的被测文件 SHA-256 与当前实际值**逐一相等**（无漂移）。
  - **F1（FAIL）**：验收标准与 GREEN 写「17 条判据全部 OK」，而它们指定的无参命令实际只打印 **16** 条——第 17 条 `spec instance is complete and traceable` 只在传实例路径时才跑。属「自述数字与证据不同源」。**已修**：AC 与 GREEN 均改为「无参 16 条 / 带合规实例 17 条」，并把两个模式的读数分别落进证据文件。
  - **F2（FAIL）**：守门 docstring 自称「加了一条检测分支却不加用例必然判红」，实测两种注入放行：① 分支打印在闸门读 `seen` 之后；② 分支用 `c.ok()` 打印（当时只包了 `c.check`）。**已修**：闸门改挂 `Checks.finish()`，并把 `check`/`ok`/`fail` 三种打印方式全部记账；同两个注入实验在修复后均判红（`artifacts/evidence/T-035-verifier-fail-fixes.txt` 实验 A/B），docstring 也改成实际保证范围。
  - **W1（已修）**：「四类内容」旧判据只看类名是否出现在模板文件里，而尾部 `FIELDS` 注释含这四个名字 → 删光四个 `##` 标题里的类名仍然全绿。已改为「小节标题解析 + 剥注释后原文扫描」两个机制并用，实验 C/C2/D 复验判红。
  - **W2/W3/W4/W5（已修）**：守门 docstring 的任务号、本文件与父任务的自相矛盾、文档影响记录漏记 `.trae/context/project-brief.md`、RED 属事后复现而非先红后绿——逐条改正，W5 已在 RED 段如实标注为流程缺陷。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除或放宽。既有 45 条用例逐条保留；本轮只增强判据与用例锚点。
- Gaps（第 1 轮复核确认，未消除）：
  - 覆盖闸门只保证「有对应用例」，**不保证用例的注入只打到它想打的那条分支**——`checkers_selftest.py` 只断言期望标签出现，未断言它是唯一失败。
  - 闸门仍看不见绕过 `Checks` 直接 `print()` 的分支（本脚本不使用）。
  - 实例模式在仓库里**没有真实样本**可验（`Docs/spec/` 由 `T-2026-09-21-036` 产出），目前只由「拿模板当实例必判红」的反向运行代偿。
- Risks: `replace` 类用例依赖文件内唯一锚点，已两次踩到重复锚点（`- \`Doubts（疑虑点）\`` 在角色文件里出现两次、`` `frontend/**` `` 出现两次）；两处都已换成更长的唯一锚点或 `replace_line`。后续改角色正文时要重新审计锚点唯一性。

## Handoff notes

- Completed: 角色、模板、守门、17 条注入用例、ROADMAP B0 范围补全、四份证据文件；第 1 轮 FAIL 全部修复并经第 2 轮独立复核确认 PASS。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: `T-2026-09-21-036` 用本角色产出第一份规格（AI 人设，只出三节，`Technical spec` 写 `DEFERRED:待 B4`）。
- Watch-outs（守门保证范围）：docstring 已把自我保证收窄为「发生在 `c.finish()` 调用之前的分支」，并把两道边界写进正文（绕过 `Checks` 直接 `print()` 看不见；`c.finish()` 之后再打印的分支看不见）。往 `finish()` 之后追加分支不会被闸门提醒，必须自己连同用例一起加。
- Watch-outs: 并发会话在本轮同时改动了 `.trae/tasks/**`、`Docs/06-delivery/**` 与 `AGENTS.md`；本任务对 `.trae/context/project-brief.md` 只补了一行文档索引（`Docs/06-delivery/TreeLearn_V1_交付报告.md`），那不是本任务的产物，是为了让共享闸门 `check_context_pack` 恢复绿色。
