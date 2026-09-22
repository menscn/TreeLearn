# Treelearn Harness Task: RikkaHub 交互基准笔记

id: T-2026-09-23-068
title: RikkaHub 交互基准笔记（L1）
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-09-23-rikkahub-layered-absorption-067.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-23-rikkahub-layered-absorption-067.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: RikkaHub 分层吸收——L1 交互/UX 全量借鉴、L2 机制级 clean-room、L3 不做。本任务是 L1。
- Problem/background: 知芽需要一份从 RikkaHub 提炼的交互基准，供高保真原型（`Docs/04-ux-design/`）与后续交互设计对照。
- Users/scenarios: 用户在 review 知芽交互设计与原型时，用本笔记对照 RikkaHub 的成熟做法；真机项由用户自行体验补全。
- Expected outcome: `Docs/04a-ux-cross-review/RikkaHub交互基准笔记.md`。
- Core flow and reversibility: 静态读 RikkaHub UI 代码 → 提炼交互范式（入口、主流程、状态、反馈、文案）→ 记录「真机待办」。纯文档，无不可逆操作。
- Inputs/outputs: 输入 RikkaHub `app/src/main/java/me/rerere/rikkahub/ui/**`（只读）；输出一份 Markdown 笔记。
- State and exception handling: 无法静态确认的交互（动效、手感、性能）一律列入「真机待办」，不写成结论。
- Constraints and trade-offs: 只借鉴范式与信息结构，不复制像素与文案；并入知芽 UX 时必须走 design token（`Docs/04-ux-design/design-system/tokens.json`）；笔记不得包含 RikkaHub 源代码。
- AI recommendation and rationale: 以源码静态分析替代真机体验（AI 无法装机），真机项显式留给用户。
- User choice and custom additions: 用户 2026-09-23「按推荐方案」含 L1 全量借鉴。
- AI-autonomous decisions: 笔记按「页面 → 组件 → 交互模式 → 与知芽映射建议」组织；重点深挖消息分支交互（与知芽树状同题）。
- User-confirmation decisions: 用户 review 笔记后决定哪些条目进入知芽原型。
- Acceptance evidence: 笔记存在、UTF-8、无占位符，每个事实带 RikkaHub 仓库内路径。
- Facts: RikkaHub 页面清单 chat/assistant/history/favorite/search/setting/backup/share/translator/imggen/stats/debug/log（`ui/pages/` 目录实测）。
- Assumptions: 源码中的 Compose 组件层级能反映实际交互。
- Unknowns: 动效手感、流式渲染性能、深浅色切换体验。
- Risks: 把 RikkaHub 交互直接写成知芽需求（越权）；笔记仅作参照，采纳与否由用户拍板。
- Deferred: 真机体验评审（用户执行）。
- Non-goals: 不改知芽任何 UX 文档与原型；不输出设计稿。
- Skipped non-critical questions and reason: 笔记语言用中文、位置按仓库既有目录，无需再问。

## Goal

产出一份基于源码静态分析的《RikkaHub 交互基准笔记》，把可借鉴的交互范式、信息结构与「真机待办」分开陈述。

## Scope

### In scope
- 新建 `Docs/04a-ux-cross-review/RikkaHub交互基准笔记.md`：聊天主界面（流式输出、停止、消息操作）、消息分支交互、Markdown 展示、设置页信息结构、与人设/助手管理、历史与收藏的导航结构；每条附 RikkaHub 源码路径出处；末尾列「真机待办」与「与知芽的映射建议（待用户拍板）」。

### Out of scope
- 修改 `Docs/04-ux-design/**` 任何现有文档；设计 token 变更；RikkaHub 云生态功能（MCP、workspace、imggen、translator）的交互分析。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增文档，不改运行行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 无生产代码改动。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯文档任务。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: `D:\Project\Android\rikkahub-master/app/src/main/java/me/rerere/rikkahub/ui/**`。
- Documentation surfaces checked: `Docs/04a-ux-cross-review/` 目录现状。
- Documentation files updated: 本笔记本身。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 不适用。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 笔记不替代 `Docs/04-ux-design/**` 的权威地位，仅作对照参照。

## Acceptance criteria

- [ ] `Docs/04a-ux-cross-review/RikkaHub交互基准笔记.md` 存在且 UTF-8，无占位符。
- [ ] 每个交互事实都标注 RikkaHub 仓库内文件路径；无法静态确认的条目在「真机待办」而非正文结论。
- [ ] 不含 RikkaHub 源代码或逐行伪代码；不复制 RikkaHub 文案原文（引用意图时用中文转述）。
- [ ] 明确声明：笔记是参照不是需求，采纳条目由用户拍板后才进入知芽 UX 文档。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（文档任务）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 产出《RikkaHub 交互基准笔记》
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 2026-09-23 复跑 `SUMMARY files=70 errors=0`；笔记已落盘 `Docs/04a-ux-cross-review/RikkaHub交互基准笔记.md`
- Regression evidence: 不适用（无生产代码）

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（文档任务）
- Reason: 叙述性产物无可先行失败的测试；正确性由「事实可回溯路径 + 用户 review」保证
- User approval: 用户 2026-09-23 明确指示按推荐方案执行（推荐方案含 L1 笔记交付）
- Alternative verification: 事实出处抽查 + 用户 review

## Implementation notes

- 重点深挖消息分支（Message Branching）交互：入口、分支切换、回溯路径——这是知芽树状交互最接近的参照物。
- 分析按知芽 UX 评审维度组织：入口、主流程、状态、反馈、文案。

## Verification report

- Verdict: PASS（用户 2026-09-24 确认收口；笔记第 6 节映射建议已被 072/073 吸收，第 7 节真机待办保留给用户）
- Evidence: 笔记存在（`Docs/04a-ux-cross-review/RikkaHub交互基准笔记.md`，UTF-8）；八节结构覆盖聊天主界面、消息操作、分支交互、设置/助手、历史/收藏、映射建议（逐条标注取舍建议）、真机待办（7 项）、非目标；每个事实条目标注 RikkaHub 仓库内路径；无源代码引用。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 静态分析与真机体验存在差距，已在「真机待办」声明。

## Handoff notes

- Completed: 任务创建。
- In progress: 源码分析与笔记撰写。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 交付后请用户 review；真机待办由用户体验后补充结论。
- Watch-outs: 笔记不是需求；不得绕过 design token 直接改知芽 UX 文档。
