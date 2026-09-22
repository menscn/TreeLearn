# Treelearn Harness Task: AI 人设规格（三节：需求规格 / 界面方案 / 疑虑点）

id: T-2026-09-21-036
title: AI 人设规格（三节：需求规格 / 界面方案 / 疑虑点）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-21-ai-persona-builtin-034.md
requirements_confirmed: true
generated_by: leader
owner: spec-author
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-21-spec-author-agent-and-template-035.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary: 见 `T-2026-09-21-ai-persona-builtin-034.md`；需求正文的权威是 `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`（accepted）。

本任务相关细节：
- Problem/background: intent 已 accepted，但「做成什么样」还没有成稿——拆任务前没有任何界面/入口/异常态的定义。
- Users/scenarios: 用户本人 review 规格；后续 `requirement-decomposer` 照规格拆任务。
- Expected outcome: 一份 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`，含需求规格、界面方案、疑虑点三节，`Technical spec` 写 `DEFERRED:待 B4`。
- Inputs/outputs: 输入是 intent 文件、`.trae/rules/ux-design.md`、`Docs/04-ux-design/design-system/tokens.json`；输出是上面那份规格。
- Constraints and trade-offs: 界面方案的数值只能引用 token 名，不得出现裸 hex 或临场 px；每条都要能追回 intent 条目。
- AI-autonomous decisions: 规格里界面方案的展开粒度。
- User-confirmation decisions: 只出三节、技术规格等 B4；介绍页两处入口且不承担选择；新建主题默认档为「默认」。
- Acceptance evidence: `python -X utf8 .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 退出码 0。
- Facts: 设计事实源是原型 `:root`（42 个 token），`tokens.json` 是机器可读镜像，Android 侧 `colors.xml` 是 15 个不透明 hex 子集。
- Assumptions: 无。
- Unknowns: 每档的量化验收用例；介绍页将来的增删改查形态。
- Risks: 人设选择 UI 与介绍页会用到颜色与间距，规格里若写临场值就会破坏与设计事实源的一致性——守门的实例模式正是为此设的。
- Deferred: `Technical spec` 一栏的内容；`Docs/spec/README.md`（若需要）。
- Non-goals: 不实现任何一档人设；不改 token；不拆任务。
- Skipped non-critical questions and reason: 规格的篇幅上限未定——不影响验收判据。

## Goal

产出 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`，四类内容里写实三节、技术规格标 `DEFERRED:待 B4`，并通过守门的实例校验。

## Scope

### In scope
- 新增 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`。
- 修改 `.trae/context/project-brief.md`：权威文档索引补上新文件（`check_context_pack.py` 要求索引等于 `Docs/` 的真实文件集）。
- 新增证据文件 `artifacts/evidence/T-036-spec-instance-red.txt` 与 `artifacts/evidence/T-036-spec-instance-green.txt`。
- 修改 `.trae/ROADMAP.md`：B0 条目补记本条的交付任务 ID（路线文档自己的用法要求「开工后把该条目接到对应 Task ID，不要让路线文档和任务状态各说各话」）。
- 同轮随用户决定落地「阶段轻量化」：改 `.trae/scripts/check_spec_author_agent.py`（17 条判据 + 自闸门 → 4 条轻量检查）、`.trae/scripts/dev/checkers_selftest.py`（17 条 `spec:` 用例 → 4 条）、`AGENTS.md`（写入「阶段轻量化」）、`.trae/agents/spec-author.md` 与 `.trae/templates/SPEC.md`（改成「骨架不是门禁」）。

### Out of scope
- 不写 `Technical spec` 的正文（等 B4）。
- 不改 `Docs/intent/**`、不改任何代码。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 不改动任何生产代码与运行行为；既有守门读数不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费者是人与 `requirement-decomposer`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯新增一份规格文档 + 一行文档索引。

## Documentation impact record

Follow [code-doc alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: 本任务不改代码。
- Documentation surfaces checked: `.trae/rules/ux-design.md`、`Docs/04-ux-design/design-system/tokens.json`、`Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `:root`、`Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md`。
- Documentation files updated: `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`（新增）、`.trae/context/project-brief.md`（索引一行）、`.trae/ROADMAP.md`（B0 条目补记交付任务 ID 与「本条已开工、只剩 B4 后补技术规格」）。
- Producer/consumer/sibling/compatibility paths checked: 规格里引用到的每个 token 名都要在 `tokens.json` 里找得到。
- Superseded phrases/examples reverse-searched: 待执行时检索 `BASE-23`、`BASE-26` 的过时表述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保持原样。

## Acceptance criteria

- [x] `python -X utf8 .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 退出码 0。
- [x] 规格含需求规格、界面方案、技术规格、疑虑点四节；前三节各带 `来源：`，疑虑点带 `冲突对象：`。
- [x] 界面方案与疑虑点里只出现 token 名，没有任何裸 hex 或 `数字px`（守门逐行扫全文件）。
- [x] `Technical spec` 一栏写明 `DEFERRED:待 B4`，并说明落地后要补什么。
- [x] 文件头的 `intent:` 指向真实存在的 intent 文件。
- [x] `python -X utf8 .trae/scripts/check_context_pack.py` 退出码 0（文档索引与真实文件集相等）。

## TDD execution record

### RED — failing test first

- Test/scenario: 守门的实例校验对尚不存在的规格路径（fail-closed：缺文件也算问题）。
- Command: python -X utf8 .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-21-ai-persona-preference.md
- Exit code: 1
- Observed failure: FAIL spec instance is complete and traceable — path=Docs/spec/SPEC-2026-09-21-ai-persona-preference.md problems=['instance file missing: Docs/spec/SPEC-2026-09-21-ai-persona-preference.md']（同一运行 16 条 OK / 1 条 FAIL）。
- Failure reason validated: 规格文件写入后同一条断言转 OK（17 条 OK / 0 FAIL、rc=0，见 GREEN）；红的原因就是文件不存在，未连带其它判据变红。
- 证据与复现方式：`artifacts/evidence/T-036-spec-instance-red.txt`。首次红是**规格文件尚不存在时**的真实运行；该文件后来因规格改稿而重出，重出后在 throwaway 副本里用「把实例移出」复现同一红（STATE 0 `17 OK / rc=0`、STATE A `16 OK / 1 FAIL / rc=1`、STATE B 还原后 `17 OK / rc=0`），真仓库未被动过。

### GREEN — minimum implementation

- Implementation: 新增 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`（需求规格、界面方案、疑虑点三节写实，`Technical spec` 记 `DEFERRED:待 B4`）；`.trae/context/project-brief.md` 的权威文档索引补一行。
- Command: python -X utf8 .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-21-ai-persona-preference.md
- Exit code: 0
- Passing evidence: `OK_LINES=17  FAIL_LINES=0`、rc=0，其中第 17 条 `spec instance is complete and traceable — path=Docs/spec/SPEC-2026-09-21-ai-persona-preference.md problems=[]`；无参模式 `OK_LINES=16  FAIL_LINES=0`、rc=0。证据 `artifacts/evidence/T-036-spec-instance-green.txt` 的 `COMMAND A` 与 `COMMAND B` 段（文件头带三个被测文件的 SHA-256 与行数，尾部有回读）。
- Regression evidence: 同证据文件的 `COMMAND C/D/E` 段——`checkers_selftest.py` `SELFTEST cases=62 ok=62 mismatch=0 harness_errors=0 skipped=0`、4 条基线、`OVERALL: PASS`、`EXITCODE=0`；`validate_tasks.py` `SUMMARY files=42 errors=0 warnings=11`、`EXITCODE=0`；五个兄弟守门各 `0 check(s) failed`、`EXITCODE=0`。

### REFACTOR — optional

- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval

- Exception type: 无。
- Reason: 交付物虽是文档，但它有一份**先失败、后通过**的可执行判据（守门实例模式），因此按 `implementation` 走 `tdd_mode: required`，不需要 TDD 例外。若用户更希望按 `docs` 类型登记，需要在开工前另行批准 TDD 例外。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 严重依赖 `T-2026-09-21-035` 交付的守门与模板；`depends_on` 已声明。
- token 名以 `Docs/04-ux-design/design-system/tokens.json` 为准（`color.*`、`s1..s8`、`r-sm/r-md/r-lg/r-full`、`sh-1/sh-2`、`fs-*`）。
- 规格是给人 review 的产物：写完由人拍板接受，才交给 `requirement-decomposer`。

## Verification report

- Verdict: **机器校验通过、验收权交回人**（2026-09-21 收口）。第 1 轮独立复核 PASS 并给 5 条小修（已改）；第 2 轮独立复核 FAIL 4 条（已改）；**第 3 轮复核由用户 2026-09-21 当场叫停**——同日用户判定「规格设计与交互设计的流程太重」，本阶段改走轻流程（见 `AGENTS.md` 的「阶段轻量化」与 `.trae/ROADMAP.md` B0 验收方式），不再要求独立复核轮次与证据文件。
- Evidence（最终一轮机器读数：轻量自检 + 回归链）：
  - 规格实例：`python -X utf8 .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` → 4 条 OK / 0 FAIL、rc=0（四节标题齐全、`intent:` 指向真实文件、无占位符、无裸 hex 与临场 px）；模板骨架无参运行 3 条 OK / 0 FAIL、rc=0。
  - RED：副本里把规格移出后 `OK_LINES=0  FAIL_LINES=1`、rc=1（只判「文件不存在」这一条，无连带）；还原后转绿。
  - 证据重出（2026-09-22）：规格的 Doubts 一节对齐 intent 的 Decision record 之后，两份证据文件按新哈希重出并回读核对——规格 `sha256=43D5C5E3…`、62 行；守护脚本 `sha256=B48F031C…`、118 行；模板 47 行、`project-brief.md` 62 行。RED 为「临时副本里移出规格」两态复现（state0 判「文件不存在」`OK=0 / FAIL=1 / rc=1`，state1 还原后 `OK=4 / FAIL=0 / rc=0`），副本只放守护脚本、规格、以及规格 `intent:` 指向的那份 intent 三个文件；GREEN 跑五条命令（实例模式、模板模式、`checkers_selftest.py`、`validate_tasks.py`、六个兄弟守护各一次）。每条命令、退出码与被核文件哈希都写在文件头，引用方按哈希即可复算。
  - 回归链：`checkers_selftest.py` `cases=49 ok=49 mismatch=0 harness_errors=0 skipped=0`、`OVERALL: PASS`、`EXITCODE=0`（旧的 17 条 spec 用例随简化精简为 4 条）；`validate_tasks.py` `SUMMARY files=43 errors=0 warnings=12`、`EXITCODE=0`；六个守门各 `0 check(s) failed`；`check_context_pack` 索引 `listed=17 actual=17`。
  - 独立复核留下的可用结论（叫停前的第 1、2 轮实测）：规格点名的 19 个 token 名在 `tokens.json`（42 个）里全部命中；7 档清单与顺序与 intent 逐字一致；疑虑点 5/5 都含「冲突对象 / 两种走法 / 建议 / 代价 / 谁拍板」。
- 复核发现与处置（第 1 轮 5 条 + 第 2 轮 4 条，都在叫停前改完）：
  - ① 原型行号引用不符：`.popover-*` 实为第 356-372 行（373-382 是 `.po-node*` / `.po-list` / `.switch`）。已改成「`.popover-*` 区块（第 356-372 行）、`.switch` 开关（第 379-382 行）」。
  - ② 可机械验证清单漏「迁移可前向」，且「主题表有对应列」被软化成「主题记录上有对应字段」。已改回 intent 原文。
  - ③ 「照抄 intent 结论，不改写」强于实交（漏 Expected outcome 第 2 条、Java-only 约束、Core flow 的「来源版本 / 创建快照」、「单人开发」）。已补齐这几项，并把「来源」的口径收窄。
  - ④ 疑虑 2 的前提被证否（「唯一带滚动的是 `.po-list`」被 `.body-scroll` 与 `.sheet` 推翻；「没有先例」被原型里的子分支列表推翻）。已重写前提与建议理由。
  - ⑤ 界面方案与疑虑之间缺回指；设置页那行易被读成全局设置；入口位置那行的命名与 `set-label` 文案不一致。已改。
  - ⑥ 编辑事故：给「选择人设」弹窗加回指时，我把原句当上下文又加了一行，导致同一条要点出现两遍（守门查不出重复）。已删掉重复的那条。
- 流程简化（同轮随用户决定落地，只改「规格与交互设计」这一段，开发阶段一条未动）：自检脚本由 17 条判据 + 自闸门改为 4 条轻量检查；自检套件里的 17 条 `spec:` 用例精简为 4 条；`AGENTS.md` 写入「阶段轻量化」；`.trae/ROADMAP.md` B0 的验收方式与执行提示词同步改写；`.trae/agents/spec-author.md` 与 `.trae/templates/SPEC.md` 改成「骨架不是门禁」的口径。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删掉的 13 条判据属于「规格阶段的门禁」，按用户决定移除；保留的 4 条覆盖真实事故面（路径写错、四节缺失、规格悬空、设计数值漂移、占位符）。开发阶段的专项 guard 一条未删。
- Gaps: 规格的 5 条疑虑需要**人**拍板后才能进拆解（疑虑 1 的增删改查入口、疑虑 2 的弹窗形态、疑虑 3 的旧回答人设标记、疑虑 4 的最高规则正文落点、疑虑 5 的两档可分性）；第 3 轮独立复核未执行（用户叫停），因此这份规格的最终验收权在人。
- Risks: 界面方案引用的 token 名只保证「名字存在于 `tokens.json`」，不保证 Android 侧已有对应资源（`colors.xml` 只是不透明 hex 子集）——实现任务落地时以 `:root` 与 `tokens.json` 为准，需要新资源就走改设计的三步顺序。

## Handoff notes

- Completed: 规格已产出；文档索引已同步；轻量自检与回归链全绿；按用户决定把本阶段流程改轻。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 交**人** review 规格，逐条拍板那 5 条疑虑；人接受后才交给 `requirement-decomposer` 拆实现任务。
- Watch-outs: 轻流程不等于没有底线——设计数值仍只认 token，裸 hex 与临场 px 会被自检判红；`Docs/spec/` 已进入文档索引，新增任何规格文件都要同步 `.trae/context/project-brief.md`，否则 `check_context_pack.py` 判红。
