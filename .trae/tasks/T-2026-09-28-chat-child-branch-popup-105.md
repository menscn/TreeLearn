# Filename: T-2026-09-28-chat-child-branch-popup-105.md
# Treelearn Harness Task: 对话答案分支入口无响应

id: T-2026-09-28-105
title: 对话答案分支入口无响应
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: android
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 用户截图指出答案操作栏中的分支按钮点击无响应；既有 `ChildBranchPopup` 尺寸修复后仍复现。
- Users/scenarios: 学习者在对话页点击有子分支答案的分支图标，查看并进入对应子分支。
- Expected outcome: 点击后显示该答案的子分支列表；点击列表项进入准确子分支；外点、返回键和页面离开可关闭弹层；空结果或失败不得静默无反馈。
- Core flow and reversibility: 分支按钮 → `ChatPresenter.onChildrenPressed` → 异步查询 → `ChatScreen.showChildren` → 锚定弹层 → 选择子分支并切换；关闭后可重新打开，不误切分支。
- Inputs/outputs: 输入当前 `branchId`、答案 `messageId`；输出 `ChildBranch` 的 branch id、标题和未读数。
- State and exception handling: 覆盖查询期间重渲染、锚点脱离窗口、空结果、查询失败、重复点击和弹层关闭。
- Constraints and trade-offs: Android Java-only；保留平板横屏和现有导航；不改数据库 schema、不新增依赖、不调用实时 AI API；使用本地固定夹具。
- AI recommendation and rationale: 追踪点击、查询、弹层 show/dismiss 和后续 render，不把单纯调整尺寸当作根因修复。
- User choice and custom additions: 用户确认按方案执行，选择固定样例而非实时 API 验证。
- AI-autonomous decisions: 使用确定性本地夹具；错误反馈复用现有界面语义，不扩展无关 UX。
- User-confirmation decisions: 分支列表可见且选项切换准确；不使用聊天中发送的 API Key。
- Acceptance evidence: 点击链测试、Android 设备/模拟器交互、APK 哈希和启动结果；截图/日志不含凭据。
- Facts: `AnswerActionBar` 转发点击；`ChatPresenter` 异步读 `ChatData.children`；`ChatScreen.showChildren` 空结果直接返回，`render` 会关闭弹层；`SqliteChatStore.children` 从当前 topic 的分支关系读取。
- Assumptions: 按钮仅在答案有子分支时显示；截图红框标记此入口。
- Unknowns: 复现设备 Android 版本与安装 APK 来源待执行时采集，不阻塞固定夹具验证。
- Risks: `ChatScreen.java`、`ChildBranchPopup.java` 与数据链已有未提交改动；先检查 diff，只能增量修改。
- Deferred: 分支列表视觉改版、树画布导航、服务端逻辑。
- Non-goals: 选区菜单和公式渲染（沿用既有 Harness 任务）；模型调用/API Key 验证。
- Skipped non-critical questions and reason: 未追问设备型号；先用固定夹具隔离根因，真机验收记录环境。

## Goal

恢复答案子分支入口的可见响应与正确导航，并以 Android UI 证据验证点击链。

## Scope

### In scope
- 编排子任务 `T-2026-09-28-chat-child-branch-popup-fix-106.md`。
- 验收答案操作栏点击、异步读取、弹层生命周期和子分支切换。

### Out of scope
- 选区菜单、高亮批注、公式渲染、后端 API 与数据库迁移。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 从无可见反馈改为显示子分支列表并能切换；复制、重试、创建分支等保持不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `AnswerActionBar` → `ChatScreen.Row` → `ChatPresenter.onChildrenPressed` → `ChatData.children`/`SqliteChatStore.children` → `ChatSink.openChildren` → `MainActivity.openChildren` → `ChatScreen.showChildren` → `ChildBranchPopup`；覆盖手机和平板横屏。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用；本任务修复用户可见行为。

## Documentation impact record

- Changed runtime facts: 子分支入口列表显示、关闭与切换行为。
- Authoritative code/config/migration evidence: `AnswerActionBar.java`、`ChatPresenter.java`、`ChatScreen.java`、`ChildBranchPopup.java`、`SqliteChatStore.java`。
- Documentation surfaces checked: `android/README.md` 与对话页/分支导航说明。
- Documentation files updated: 子任务按实现更新；当前流程未说明时在同一任务补写。
- Producer/consumer/sibling/compatibility paths checked: 点击链、复制/重试等相邻入口、横屏布局。
- Superseded phrases/examples reverse-searched: “子分支列表”“分支按钮”。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 只同步当前实现，不静默改写历史记录或 PRD。

## Acceptance criteria

- [ ] 子任务通过独立 verifier；按钮到列表展示、列表项到目标分支切换均有可复现证据。
- [ ] 空结果、查询失败、期间重渲染和页面 detach 不产生静默无响应或幽灵弹层。
- [ ] 受影响测试、Android lint、Java-only guard、模块启动结果如实记录；设备截图与被安装 APK SHA-256 相匹配。

## TDD execution record

### RED — failing test first
- Test/scenario: 由子任务记录。
- Command: 由子任务记录。
- Exit code: 由子任务记录。
- Observed failure: 由子任务记录。
- Failure reason validated: 由子任务记录。

### GREEN — minimum implementation
- Implementation: 由子任务记录。
- Command: 由子任务记录。
- Exit code: 由子任务记录。
- Passing evidence: 由子任务记录。
- Regression evidence: 由子任务记录。

### REFACTOR — optional
- Cleanup: 由子任务记录。
- Regression evidence: 由子任务记录。

### TDD or sizing exception — only with prior user approval
- Exception type: 无。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 实施任务生产代码预算目标 ≤200 行；先审阅现有 diff 后确定最小写入范围。
- Android instrumentation 不支持 `--tests` 过滤；设备验证遵守项目现行脚本规则。
- 用户提供的 API Key 不用于测试，不进入代码、配置、日志或证据。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 工作树包含其他 Android/任务线的暂存与未暂存改动，需执行者按路径保留。

## Handoff notes

- Completed: 已确认需求边界并建立父需求。
- In progress: 子任务待执行。
- Blocker: 无；实现前须审阅相关现有 diff。
- Unblock condition: 子任务按 RED→GREEN 完成并独立复核。
- Next steps: 建立并执行分支弹层实现子任务。
- Watch-outs: 不清理/重置用户现有改动，不替公式或选区任务报完成。
