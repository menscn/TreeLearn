# Treelearn Harness Task: Plan 阶段背景知识包（L0 常驻入口 + L2 领域词表）

id: T-2026-09-21-027
title: Plan 阶段背景知识包（L0 常驻入口 + L2 领域词表）
template_version: 2
task_type: config
status: verified
parent_requirement: T-2026-09-21-ai-native-sdlc-agent-team-026.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: approved-exception

## Requirement collaboration brief

Parent summary: 用户是单人开发者，要把 AI-native SDLC 各阶段做成可调用角色；首批（方案 A）只做 Stage 1 Plan。`AGENTS.md` 为最高级规则文件，遗留事项必须落成可被其它 AI 执行的文档。

本任务相关细节：
- Problem/background: PDF 要求「让 agent 读文件而不是读人的脑子」，但仓库现有产品背景散落在 `Docs/01-demand`、`Docs/03-prd-draft`、`Docs/05-technical-design`，单份最大 95KB，直接塞进 context 会挤占额度且过期内容只有负收益。
- Users/scenarios: intent-capturer 每次开局需要一份薄背景；其它阶段角色后续复用同一份。
- Expected outcome: 两层背景——L0 常驻（≤1 页，只做常驻事实与索引）、L2 领域词表（术语 → 权威出处），正文一律指向 `Docs/**`。
- Inputs/outputs: 输入 `Docs/**` 与现有 rules；输出 `.trae/context/project-brief.md`、`.trae/context/domain-glossary.md`。
- Constraints and trade-offs: 与 Docs 重叠即产生第二事实源；因此 L0 只允许「一句话事实 + 指针」，禁止整段复制。
- AI-autonomous decisions: 目录名 `.trae/context/`、文件命名、词表条目数下限。
- User-confirmation decisions: 首批范围；SoT 分工（intent 管变更意图、Docs 管当前产品事实）。
- Acceptance evidence: 行数断言、路径存在性断言、与 Docs 的 40 字连续子串重合断言。
- Facts: 执行本任务时 `Docs/` 有 10 个文件（01-demand 2、03-prd-draft 2、03a 1、04-ux-design 1、05-technical-design 4）。验收时该集合已变为 11 个——同批任务 030 新增了 `Docs/intent/README.md`；索引已同步（见 GREEN）。此后并发会话又新增 `Docs/04-ux-design/design-system/{README.md,tokens.json}`，集合变为 13 个，索引第二次同步为 13 条（`listed=13 actual=13 missing=[] extra=[]`）。
- Unknowns: 无。
- Risks: 词表写太细会变成术语词典维护负担 → 只收 ≥2 处代码/文档实际使用的术语。

## Goal

新增 `.trae/context/project-brief.md`（≤1 页常驻入口）与 `.trae/context/domain-glossary.md`（领域词表），使任一角色能在不读取完整 PRD 的前提下获得准确的项目背景与权威出处索引。

## Scope

### In scope
- 新建 `.trae/context/project-brief.md`、`.trae/context/domain-glossary.md`。
- 新增校验脚本 `.trae/scripts/check_context_pack.py`。

### Out of scope
- 不改写 `Docs/**` 任何正文；不新增 L1 层（L1 就是 `Docs/**` 本身）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只新增 Markdown 与一个只读校验脚本，现有规则与角色行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费者为任务 028（intent-capturer 的「执行前必读」）；证据为 028 任务文件引用。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯文档新增，无可运行分支。

## Documentation impact record

Follow [code-doc alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: `Docs/01-demand/*`、`Docs/03-prd-draft/*`、`Docs/05-technical-design/*` 为产品事实权威；`.trae/rules/*.md` 为工程约束权威。
- Documentation surfaces checked: `Docs/**` 全部文件（执行时 10 个，验收时 11 个）与 `.trae/rules/` 全部既有规则文件。
- Documentation files updated: 无（本任务只新增索引层）。
- Producer/consumer/sibling/compatibility paths checked: project-brief 的「权威文档索引」节是否覆盖全部 Docs 文件；同批 030 新增 `Docs/intent/README.md` 后正是靠这条断言发现索引漂移并修正。
- Superseded phrases/examples reverse-searched: 不适用（未新增规范性表述）。
- `documentation impact: none` reason and evidence, if applicable: 本任务不改变任何运行时事实，只新增指向权威正文的索引，故 docs 正文无需改动。
- Historical/PRD handling: 保持原样。

## Acceptance criteria

- [ ] `.trae/context/project-brief.md` 存在，且**非空非标题行 ≤ 60 行**；校验脚本打印实测行数。
- [ ] project-brief 含六节且标题逐字为：产品一句话、当前阶段与技术栈、关键约束、权威文档索引、事实源（SoT）声明、阅读顺序。
- [ ] 「权威文档索引」节列出 `Docs/` 下的全部文件路径；脚本断言每个列出的路径存在且**集合与 `Docs/` 递归文件集合相等**——因此此后任何新增 Docs 文件都必须同步本索引，缺一个即 FAIL（实测已发生过一次：030 新增 `Docs/intent/README.md` 时索引仍是 10 个，被这条断言抓出并补齐到 11 个）。
- [ ] `.trae/context/domain-glossary.md` 含 ≥ 12 条术语，每条格式为 `术语 | 定义（≤40 字） | 权威出处路径`。
- [ ] 词表中每条「权威出处路径」均存在（脚本逐条断言并打印失败项）。
- [ ] **无正文复制**：project-brief 与 `Docs/**` 各自的 40 字连续子串交集为空；脚本输出命中数必须为 0。
- [ ] 「事实源（SoT）声明」节明确写出四条：产品事实 → `Docs/**`；变更意图与收敛过程 → `Docs/intent/**`；Harness 任务状态 → `.trae/tasks/**`；工程约束 → `.trae/rules/**`。
- [ ] 全文不含 `PENDING`、`TBD`、`TODO`、`待填` 占位符（脚本扫描断言命中数为 0）。

## TDD execution record

### RED — failing test first

- Test/scenario: 不适用（纯文档新增，无生产代码行为）。
- Command: 不适用
- Exit code: 0
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation

- Implementation: 新增 `.trae/context/project-brief.md`、`.trae/context/domain-glossary.md` 与校验脚本。
- Command: python .trae/scripts/check_context_pack.py
- Exit code: 0
- Passing evidence: `python .trae/scripts/check_context_pack.py` 打印 10 条 OK、0 条 FAIL、`EXITCODE=0`，其中可核的实测值：非空非标题行 38（上限 60）、六节缺失列表为空、索引 listed=13 actual=13 missing=[] extra=[]、索引内失效路径 dead=[]、词表 15 条且 dead_sources=[]、与 `Docs/**` 的 40 字重合 0 命中、占位符命中为空。
- Regression evidence: 本任务只新增 `.trae/context/project-brief.md` 与 `.trae/context/domain-glossary.md`（外加同批 027 的校验脚本），未改动任何 `Docs/**` 正文与业务代码；`git status --porcelain -- Docs` 里没有本任务引入的改动。

### REFACTOR — optional

- Cleanup: 无。
- Regression evidence: 无。

### TDD or sizing exception — only with prior user approval

- Exception type: TDD 不适用（文档/配置类任务，无生产代码）。
- Reason: 交付物为 Markdown 索引层，不存在可先写的失败测试；等价验证为机械断言脚本。
- User approval: 用户于 2026-09-21 明确批准本批 5 个文档类任务的 TDD 例外（执行前批准，非事后追认）。
- Alternative verification: 上述验收标准中的八项脚本断言。

## Implementation notes

- L0 只写「一句话事实 + 指针」，凡是需要展开的内容一律写「见 `<路径>`」。
- 词表只收在 `Docs/**` 或 `.trae/rules/**` 中实际出现 ≥2 次的术语。
- 校验脚本纯标准库、UTF-8，无第三方依赖。

## Verification report

- Verdict: PASS
- Evidence: `python .trae/scripts/check_context_pack.py` → 10 条 OK / 0 FAIL / `EXITCODE=0`，实测判据行：非空非标题行 38（上限 60）、`docs index equals the real Docs/ file set — listed=13 actual=13 missing=[] extra=[]`、词表 15 条且 `dead_sources=[]`、与 `Docs/**` 的 40 字重合 `measured_hits=0`、占位符命中为空。独立复核（verifier，2026-09-21 20:53–20:56，全程只读）逐条复核并判 PASS，AC7 的 SoT 四条由该 verifier 手工核对通过。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言被删除或放宽。「索引集合 == `Docs/` 递归文件集合」这条不变量在本次会话内两次转红，两次都以**补索引**收场：第一次 030 新增 `Docs/intent/README.md`（10 → 11），第二次并发会话新增 `Docs/04-ux-design/design-system/{README.md,tokens.json}`（11 → 13）。两次都不是放宽判据。
- Gaps: ① AC7（SoT 四条）与词表「定义 ≤40 字」没有脚本断言，只有人工核对；② 40 字重合判据先经 `mask_paths` 把仓库路径换成占位符，占位符用共享 mapping，因此两份文件若逐条列出同一串路径仍可对齐成 ≥40 字假命中（verifier 注入重现 `measured_hits=76`，真实交付为 0），属理论风险；③ 该不变量会让任何并发新增 `Docs/` 文件立刻把守门打红——这是设计要求（强制同步），代价是并发会话期间守门会周期性转红。
- Risks: 并发会话仍在写 `Docs/**`；本任务读数以 `listed=13 actual=13` 为准，之后任何新增都必须同步索引，否则守门转红。

## Handoff notes

- Completed: 无。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 供 028 引用为「执行前必读」。
- Watch-outs: 一旦 project-brief 出现与 Docs 相同的整段文字，它就变成第二事实源，必须重写成指针。
