# Filename: T-2026-09-28-annot-cell-highlight-028.md
# Treelearn Harness Task: 单元格基建 + 单元格高亮（T-C）

id: T-2026-09-28-028
title: 单元格基建 + 单元格高亮（T-C）
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-28-chat-highlight-annotation-025.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 430
shared_code_touched: true
depends_on: [T-2026-09-28-annot-menu-prose-highlight-027.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary: 见 T-2026-09-28-chat-highlight-annotation-025.md。本任务把标记动线扩展到表格单元格（拍板 H）。

- Problem/background: 单元格是普通 TextView，未开 `textIsSelectable`，无法承载选区与 span。
- Expected outcome: 单元格可选/可选菜单/可高亮；`Row` 选区扫描扩为 `selectingTarget()`（prose + `CellRef` 注册表）。
- Core flow and reversibility: 方案 §2.6——长按单元格 → 同一套 6 格菜单；定位键 `(table_index,row,col,start,end)`。
- State and exception handling: 与 prose 一致（流式置灰）。
- Constraints and trade-offs: `item_chat_table.xml` 加 `FrameLayout` wrapper（为 T-D 覆盖层预留），横向滚动零监听。
- AI-autonomous decisions: 单元格锚定用结构位（行列由 Markdown 结构决定，无漂移）。
- User-confirmation decisions: 拍板 H（支持单元格）。
- Acceptance evidence: 本文件 TDD + 真机截图。
- Facts: `ChatScreen:1048 renderAnswerBody`/`:1097 inflateTable`；`ChatMessageRowLayoutTest` 引用 `item_chat_table`（方案 §1/§2.6 核实）。
- Assumptions: `WordMenuPopup` 构造入参即 TextView，对单元格直接复用。
- Unknowns: 无。
- Risks: R-3/R-6/R-7/R-8。
- Deferred: 便签锚点绘制（T-D）；单元格内 QUOTE/FORK 语义不改。
- Non-goals: `MarkdownRenderer` 不动（仅确认）。
- Skipped non-critical questions and reason: 无。

## Goal

表格单元格获得与 prose 完全一致的选区/菜单/高亮能力，布局结构变更同步测试，真机 V-12 全链路通过。

## Scope

### In scope
- `inflateTable()`：`textIsSelectable(true)` + `LinkMovementMethod` + `SelectionMenuCallback` + `setText(..., SPANNABLE)`；`CellRef` 注册表。
- `selectingRun()` → `selectingTarget()`；`selectedText()` 扩展（**工作区记忆红线的直接延伸**）。
- `item_chat_table.xml`：`HSV > FrameLayout > TableLayout`。
- `Highlighter` 扩展到单元格容器。
- `ChatMessageRowLayoutTest` 同步。

### Out of scope
- 批注弹窗/便签（T-D）；prose 侧行为改动（回归保护即可）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 单元格获得可选性（预期行为即本特性）；表格点击滚动、列宽 stretch、单元格内换行不错位全部保留。
- Producer/consumer: `CellRef` 仅 `Row` 内消费；布局测试断言同步（改断言须写等价覆盖说明）。
- Platform branches: 手机/平板同一布局。
- Behavior-neutral non-applicability reason: N/A。

## Documentation impact record

- Changed runtime facts: 单元格可选；`item_chat_table` 结构三层。
- Documentation files updated: `android/README.md` 表格行；方案 §2.6 勾选落地状态。
- Superseded phrases reverse-searched: 「单元格未开 selectable」类表述反查。

## Acceptance criteria

- [ ] 真机 V-12：单元格内选区 → 6 格菜单 → 高亮 → 重进页面仍在。
- [ ] V-2 单元格版：长按菜单正常；短按 ClickableSpan（高亮二次点击）与长按不互斥。
- [ ] 表格横向滚动、列对齐、单元格换行回归通过（既有布局测试 + 真机目视）。
- [ ] `ChatMessageRowLayoutTest` 断言更新为新三层结构且给出等价覆盖说明。
- [ ] JVM 全量单测绿；Java-only guard PASS；token 守门 0 failed（无新 token 则 44/16 不变）。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING
- Command: PENDING
- Exit code: PENDING
- Observed failure: PENDING
- Failure reason validated: PENDING

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: 无（估 430 >200 警告级，<500）。

## Implementation notes

- `elementAround()`（只取开标签）对元素内部结构断言失灵；断言 wrapper 内子节点用 `elementWithBody()`（工作区记忆）。
- 表头行 r=0 含在定位范围内（方案 §3.2）。
- 布局 XML 改完跑 `--` 注释全树扫描。

## Verification report

- Verdict: PENDING

## Handoff notes

- Completed: —
- In progress: —
- Blocker: 依赖 T-027。
- Unblock condition: T-027 verified。
- Next steps: —
- Watch-outs: `selectingTarget()` 返回 (TextView, 定位器) 元组语义定稿后 T-D 沿用。
