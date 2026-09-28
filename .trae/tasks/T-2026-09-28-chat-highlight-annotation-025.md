# Filename: T-2026-09-28-chat-highlight-annotation-025.md
# Treelearn Harness Task: 对话页文本高亮与批注（父需求）

id: T-2026-09-28-025
title: 对话页文本高亮与批注
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: android
stack: android
estimated_production_lines: 0
shared_code_touched: true
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 回答正文（prose 段与表格单元格）无法做学习标记——没有高亮、没有批注，选词菜单只有问 AI/新建主题/复制/全选 4 项。权威方案：`Docs/04-ux-design/知芽-TreeLearn-对话页文本高亮与批注方案-V1.0.md`（内文 V1.1，**状态：已确认——8 项拍板全部落定（2026-09-28 15:23）**，配套交互 Demo 已验收 33/33 断言通过）。
- Users/scenarios: 学习者在对话页长按选中回答正文（prose 或表格单元格），做高亮与批注，重进页面仍在。
- Expected outcome: 6 格菜单（问AI/新建主题/高亮/批注/复制/全选）；黄色高亮 `#FFF3B0`；批注便签锚点；重生成后标记全清（digest 机制）；DB V4→V5 新增 `message_annotations` 单表。
- Core flow and reversibility: 选区 → 高亮（相交合并并区间）或批注（弹输入框，锚便签图标）→ 点便签看/编辑/删；高亮二次点击精确重合 → 恢复选区 + 首格变「取消高亮」。可逆：删除高亮/批注即回原状。
- Inputs/outputs: 输入 = 用户选区与动作；输出 = span 视觉变化 + `message_annotations` 行。
- State and exception handling: 流式中（非 stable）高亮/批注画出但置灰、store 拒绝写入；digest 失配在读路径自愈清除（仅 `isStable()` 时比对，防流式误删）。
- Constraints and trade-offs: 菜单格一律图标承载（24dp 网格/1.8 描边圆角端点，同 `ic_action_*` 族），**不得文字按钮**（2026-09-28 15:23 用户确认）。
- AI recommendation and rationale: 见方案 §5 取舍表（digest 删除 vs 重定位、严格重合、并区间合并、单表+kind 入唯一索引、覆盖层便签）。
- User choice and custom additions: ① B 重生成全删 ② E+A 严格重合 ③ H 支持单元格 ④ C 新增 MARK 组 ⑤ D `#FFF3B0` ⑥ F 覆盖层+绝对位移 ⑦ G 单表+kind ⑧ H 之外：菜单格禁文字按钮。
- AI-autonomous decisions: 任务拆分 T-A~T-D 依赖链；`anchor_text` 列删除。
- User-confirmation decisions: 上 8 项（方案变更记录 V1.1 + V1.1 15:23 确认行）；本 Harness 四任务开工方式（2026-09-28 用户选择「Harness 四任务按序」+「分主题提交后再开工」）。
- Acceptance evidence: 交互 Demo `知芽-TreeLearn-高亮批注交互Demo-V1.1.html`（CDP 33/33）；各子任务验收见其文件。
- Facts: 方案 §1 落点表全部核实（选区路径/单卡片仲裁/DB V4/11 表/minSdk 28）。
- Assumptions: 本产品回答只有「整体替换」一种变更形态（digest 粒度够用）。
- Unknowns: 无（方案已核实到文件行号）。
- Risks: R-1 digest 流式误删 / R-2 背景色绘制顺序 / R-3 ClickableSpan 吞长按 / R-4 跨线并发 / R-5 新弹窗未纳管 / R-6 单元格 textIsSelectable 改长按行为 / R-7 wrapper 层破坏布局断言 / R-8 便签锚点错位（缓解见方案 §7）。
- Deferred: 单元格内 QUOTE/FORK 语义沿用 run-local 现状，不改。
- Non-goals: 思维链区、译文区、用户消息不支持标记；不做锚点重定位。
- Skipped non-critical questions and reason: 无——8 项全部拍板。

## Goal

对话页回答正文（prose + 表格单元格）支持高亮与批注的完整学习动线，按 T-A→T-B→T-C→T-D 四个子任务交付。

## Scope

### In scope
- 子任务 T-2026-09-28-annot-data-layer-026 / annot-menu-prose-highlight-027 / annot-cell-highlight-028 / annot-note-fullchain-029 的编排与验收。

### Out of scope
- 思维链/译文/用户消息；锚点重定位；多设备同步。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增标记能力；既有 4 格菜单语义、选区路径（遍历 `answerViews`）、单卡片仲裁、表格渲染保真全部保留。
- Producer/consumer/sibling entrypoints and platform branches: `ChatScreen.renderAnswerBody`/`inflateTable`、`WordMenu`/`WordMenuPopup`、`TreeLearnSchema`(V4→V5)、`SqliteChatStore`；手机/平板共用 `screen_chat_body`，无分支漂移。
- Native bridge or independent-client compatibility: 无。
- Behavior-neutral non-applicability reason: N/A。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: DB 版本 4→5、新增第 12 张表、菜单 4→6 格、token 43→44 / 资源 15→16。
- Authoritative code/config/migration evidence: 各子任务 GREEN 证据。
- Documentation surfaces checked: `android/README.md`、`design-system/README.md`、tokens 表——由命中子任务同步。
- Documentation files updated: 由子任务记录。
- Producer/consumer/sibling/compatibility paths checked: 方案 §1。
- Superseded phrases/examples reverse-searched: 子任务负责。
- `documentation impact: none` reason and evidence: 不适用。
- Historical/PRD handling: PRD 6.2.19/6.2.20 为上游引用，不改。

## Acceptance criteria

- [ ] 四个子任务全部 `verified`，依赖链 T-A→T-B→T-C→T-D 满足。
- [ ] 方案 §8 验收要点 V-1~V-12 有对应证据（分布在四个子任务，见各自 acceptance）。
- [ ] 守门全绿：token 守门、Java-only guard、JVM 单测、`validate_tasks.py`。

## TDD execution record

### RED — failing test first
- Test/scenario: 由子任务记录。

### GREEN — minimum implementation
- Implementation: 由子任务记录。

### TDD or sizing exception — only with prior user approval
- Exception type: 无。

## Implementation notes

- 编排约定：子任务串行执行（共享 ChatScreen/schema，不可并行）；verifier 串行。
- 新建第 12 张永久表 `message_annotations` 的**建表入场记录**放在 T-2026-09-28-annot-data-layer-026；用户确认证据 = 方案 §3.1 DDL 随 8 项拍板获用户确认（2026-09-28 15:07/15:23）+ 本日 Harness 选择。

## Verification report

- Verdict: PENDING

## Handoff notes

- Completed: 方案 V1.1 拍板齐备；四子任务文件已建。
- In progress: T-A。
- Blocker: 无。
- Unblock condition: —
- Next steps: T-A→T-B→T-C→T-D 串行；每个完成即 verifier。
- Watch-outs: 跨线并发写树（R-4）；真机共用（仪器测试窗口协调）；AGP 测试后重装应用。
