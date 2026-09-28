# Filename: T-2026-09-28-annot-note-fullchain-029.md
# Treelearn Harness Task: 批注全链路（T-D）

id: T-2026-09-28-029
title: 批注全链路（T-D）
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
depends_on: [T-2026-09-28-annot-cell-highlight-028.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary: 见 T-2026-09-28-chat-highlight-annotation-025.md。本任务交付批注（NOTE）的完整动线。

- Problem/background: 菜单「批注」格在 T-B 只挂了接口；编辑弹窗、便签锚点、椭圆列表、digest 清除联动未建。
- Expected outcome: 批注建立/编辑/删除、便签图标（prose+单元格）、N≥2 椭圆列表、重生成清除（V-9）、流式不误删（V-10）。
- Core flow and reversibility: 方案 §2.4/§2.5/§3.3。
- State and exception handling: 同容器同 kind 精确重合 → 拒新建直接开编辑（预填原文）；流式期间不做 digest 比对。
- Constraints and trade-offs: 便签 = 覆盖层 + 绝对位移（`Layout.getPrimaryHorizontal`/`getLineTop`，复用 `WordMenuPopup.selectionBox()` 坐标）；prose 一层覆盖层 + 每表一层（在 HSV 内容内）。
- AI-autonomous decisions: 弹窗复用 `WordMenuPopup.place()` 夹取。
- User-confirmation decisions: 拍板 B（重生成全删）、E+A（严格重合）、F（覆盖层形态）。
- Acceptance evidence: 本文件 TDD + 真机 V-4/V-5/V-6/V-9/V-10 证据。
- Facts: `ChatScreen:826 showWordMenu`/`:838 dismissPopups`（新弹窗必须纳管）；`selectingTarget()`/定位器由 T-B/C 定稿。
- Assumptions: digest = SHA-256(整条回答 markdown)，创建时记录。
- Unknowns: 无。
- Risks: R-1（流式误删，最重）/ R-5 / R-8。
- Deferred: 无。
- Non-goals: 不做批注导出/搜索。
- Skipped non-critical questions and reason: 无。

## Goal

批注从建立到清除的完整可验收链路，V-4~V-6、V-9、V-10 真机/仪器证据齐全，父需求随之收口。

## Scope

### In scope
- `NoteEditorDialog.java`（新）：多行输入 AlertDialog，确定/取消。
- `NoteAnchorPopup.java` + `popup_note_list.xml`（新）：椭圆列表弹窗。
- 便签图标：prose 覆盖层 + 每表覆盖层；`onLayoutChange` 重算锚点；列表空 → 图标隐藏。
- `dismissPopups()` 纳管两个新弹窗。
- digest 清除联动（读路径已在 T-026；本任务保证 stable 语义接线 + 仪器测试）。
- strings + 仪器测试 + `ChatMessageRowLayoutTest`/`IconAssetsTest` 同步。

### Out of scope
- schema/DAO（T-026 已交付）；菜单/高亮回归保护。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 重生成后回答内容替换，标记全清（拍板行为）；非重生成的流式 tick 不触发清除。
- Producer/consumer: 便签图标 onClick → 列表/编辑弹窗；全部经 `dismissPopups()` 仲裁。
- Platform branches: 无。
- Behavior-neutral non-applicability reason: N/A。

## Documentation impact record

- Changed runtime facts: 新增 2 类弹窗 + 覆盖层；V-9/V-10 行为。
- Documentation files updated: `android/README.md` 对话页标记一节；方案文档状态（全部落地）。
- Superseded phrases reverse-searched: 「批注未实现」类表述反查。

## Acceptance criteria

- [ ] V-4：便签图标落位选区末尾右上角，HSV 横向滚动随内容移动（仪器测试 + 截图）。
- [ ] V-5：同容器同 kind 精确重合二次批注被拒并打开编辑；异区间允许。
- [ ] V-6：同锚点多条 → 椭圆列表列出全部；点条目进编辑；可删除。
- [ ] V-9：建批注/高亮 → 触发重生成 → DB 该 `message_id` 下标记 0 行。
- [ ] V-10：流式期间旧批注仍在（不误删）；完成后按拍板语义判定。
- [ ] 单卡片仲裁：菜单/列表/编辑弹窗同时只存活一个。
- [ ] JVM 全量单测绿；Java-only guard PASS；token 守门 0 failed；真机 V-1~V-12 汇总复核（父需求收口）。

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

- 「异步写入 + 轮询已停」教训（T-011 读回）：凡是派生/异步改标记数据，须有重读触发，光改写路径是假修。
- 新弹窗漏纳管 = 双卡片共存 bug，`dismissPopups()` 一处收口。
- 覆盖层不进文字流（F 拍板），`ReplacementSpan` 不可用（撑行高且 HSV 内无处安放）。

## Verification report

- Verdict: PENDING

## Handoff notes

- Completed: —
- In progress: —
- Blocker: 依赖 T-028。
- Unblock condition: T-028 verified。
- Next steps: 全链路真机走查 V-1~V-12 → 父需求 verifier 收口。
- Watch-outs: 真机共用窗口；AGP 测试后重装应用。
