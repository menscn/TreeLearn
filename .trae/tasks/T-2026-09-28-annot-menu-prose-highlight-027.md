# Filename: T-2026-09-28-annot-menu-prose-highlight-027.md
# Treelearn Harness Task: 菜单 6 格 + prose 高亮（T-B）

id: T-2026-09-28-027
title: 菜单 6 格 + prose 高亮（T-B）
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-28-chat-highlight-annotation-025.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 380
shared_code_touched: true
depends_on: [T-2026-09-28-annot-data-layer-026.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary: 见 T-2026-09-28-chat-highlight-annotation-025.md。本任务交付标记动线的前半段：菜单扩格与 prose 高亮。

- Problem/background: 选词菜单 4 格无标记动作；正文无法高亮。
- Expected outcome: 6 格菜单（+高亮/批注占位，批注回调本任务只挂接口不实现弹窗）；prose 段高亮落库与二次点击取消；黄色 token 三方同步。
- Core flow and reversibility: 方案 §2.2/§2.3——建立高亮（Markdown 渲染后统一 flush）；精确重合二次点击 → 恢复选区 + 首格「取消高亮」；部分重叠 → 合并并区间落库。
- State and exception handling: 流式中高亮/批注格画出但置灰（与 QUOTE 同规则）；store 拒绝可见。
- Constraints and trade-offs: **菜单格一律图标承载（24dp/1.8 描边/圆角端点），不得文字按钮**（15:23 用户确认硬约束）；「取消高亮」是图标态切换（记号笔→带斜杠记号笔），不是独立格。
- AI-autonomous decisions: `Highlighter` 独立类（不与 MarkdownRenderer 混排）。
- User-confirmation decisions: D 黄色 `#FFF3B0`；C 新增 MARK 组。
- Acceptance evidence: 本文件 TDD + 真机截图。
- Facts: `WordMenu.java:33` Item 枚举；`popup_word_action.xml` 4 等宽格；`ChatScreen:1170` SelectionMenuCallback；`MarkdownRenderer.java:331` 代码块灰底 span（方案 §1 核实）。
- Assumptions: prose 已有 `LinkMovementMethod`，ClickableSpan 可用。
- Unknowns: 无。
- Risks: R-2 绘制顺序 / R-3 ClickableSpan 吞长按（V-2/V-3 专项）。
- Deferred: 单元格（T-C）、批注弹窗与便签（T-D）。
- Non-goals: 不改 `render()`（思考链/译文路径）。
- Skipped non-critical questions and reason: 无。

## Goal

prose 段高亮全链路（建/合并/取消/持久化/重进仍在）+ 菜单 6 格图标化，token 三方同步后守门全绿。

## Scope

### In scope
- `WordMenu`：Item +HIGHLIGHT/+NOTE、Group +MARK、`entriesFor`。
- `popup_word_action.xml`：4 格→6 格，全图标（新增记号笔/便签/带斜杠记号笔 3 矢量，入 `CHAT_ICON_ASSETS`）。
- `WordMenuPopup`：Listener +2 回调；置灰规则。
- `Highlighter.java`（新）：span flush、精确重合判定接 `Annotation`、合并落库。
- `ChatScreen`：prose 段接线（选区→高亮；ClickableSpan 二次点击）。
- token 三方同步：`yellow-highlight`/`tl_yellow_highlight`（资源 15→16）+ design-system README + 守门 0 failed。

### Out of scope
- 单元格选择与高亮（T-C）；批注编辑弹窗/便签锚（T-D，本任务 NOTE 回调可临时空实现但接口定稿）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 既有 4 格（问AI/新建主题/复制/全选）语义与顺序不变，新增格插在中间；选区读取仍遍历 `answerViews`；单卡片仲裁不变。
- Producer/consumer: WordMenu Listener 增两回调 → ChatScreen 唯一实现；`IconAssetsTest` 契约同步（图标化是拍板行为，改断言须写等价覆盖）。
- Platform branches: 无。
- Behavior-neutral non-applicability reason: N/A。

## Documentation impact record

- Changed runtime facts: 菜单 6 格、token 43→44/资源 15→16、新 drawable ×3。
- Documentation files updated: `design-system/README.md`（token 表/资源数/`:root` 行号/变更日志）、`android/README.md` 菜单描述、方案文档状态行。
- Superseded phrases reverse-searched: 「四格」「4 格」菜单描述反查更新。

## Acceptance criteria

- [ ] 真机：长按 prose 选区 → 6 格图标菜单；点高亮 → 黄色 `#FFF3B0` 可见（截图取色留容差）。
- [ ] 精确重合点高亮 → 选区恢复 + 首格带斜杠记号笔；部分重叠 → 首格仍记号笔，点击合并为并区间（V-3/V-11）。
- [ ] 与代码块灰底共存：高亮绘制在上（V-R2 专项断言 span 顺序）。
- [ ] 长按 ActionMode 与短按 ClickableSpan 不互相破坏（V-2）。
- [ ] 高亮重进页面仍在（读路径回归）。
- [ ] token 守门 0 failed（44/16）；JVM 全量单测绿；Java-only guard PASS；布局/图标测试同步（`IconAssetsTest` 含 3 新矢量与 6 格结构断言）。

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
- Exception type: 无（估 380 >200 警告级，<500）。

## Implementation notes

- `renderSegments` 路径改，勿动 `render()`（工作区记忆红线）。
- 新增不透明 hex token ⇒ colors.xml 必 +1，README/tokens.json/`:root` 行号同步；改前先跑守门取实测基数。
- 菜单宽度 ~288dp，`leftFor()` 夹取自动贴边，窄屏无碍（方案 §2.1）。

## Verification report

- Verdict: PENDING

## Handoff notes

- Completed: —
- In progress: —
- Blocker: 依赖 T-026 完成。
- Unblock condition: T-026 verified。
- Next steps: 接口对齐 T-026 的 DAO 签名后开工。
- Watch-outs: 置灰态不吞点击（可见拒绝原则）；批注回调接口定稿后 T-D 不得改签名。
