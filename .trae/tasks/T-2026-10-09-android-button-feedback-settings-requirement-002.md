# Treelearn Harness Task: Android 按钮反馈与设置入口调整（需求）

id: T-2026-10-09-002
title: Android 按钮反馈与设置入口调整
template_version: 2
task_type: requirement
status: in_progress
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

- Problem/background: 用户使用 Android 应用时发现按钮按压背景与动效不理想；希望减少设置页信息、调整设置入口位置。
- Users/scenarios: ALP-AN00 / MagicOS 11 用户日常使用首页、设置和聊天页面。
- Expected outcome: 全应用明确按钮有统一原生按压 ripple；设置页不再展示“数据边界”；设置从底部 tab 迁至首页右上角齿轮。
- Core flow and reversibility: 首页齿轮打开设置，返回首页；聊天内既有设置入口保持，返回原聊天；缺少 Key 时的配置引导与回退不变。
- Inputs/outputs: 用户点击按钮，看到稳定一致的短时状态层并触发原命令；不改变业务数据输入/输出。
- State and exception handling: 保留默认、禁用、危险动作和主操作背景含义；不改变数据存储、备份、Provider 请求内容。
- Constraints and trade-offs: Android-only、Java-only、无新增依赖；不用模拟器；不做用户完整功能走查；首页和学习库仍为底部两个 tab。
- AI recommendation and rationale: 选择 Android 原生 ripple 叠加保留底色，避免自定义缩放导致布局移动；复用既有齿轮矢量资源。
- User choice and custom additions: 用户确认方案 A；要求改完构建 APK 并安装到 ALP-AN00。
- AI-autonomous decisions: 内容卡片业务点击不变；聊天设置入口保留；用户负责最终视觉/功能走查。
- User-confirmation decisions: 按钮反馈、设置页文案、入口位置、目标设备和安装要求均由用户确认。
- Acceptance evidence: 定向测试、Android lint、必要 JVM 测试、Java-only dex 扫描、debug APK 构建、指定真机安装与初始 Activity 启动检查。
- Facts: 当前设置入口为底部第三 tab；首页顶栏左侧为抽屉按钮；设置根布局含数据边界说明；部分 drawable 使用 state_pressed 整块换色。
- Assumptions: 现有颜色资源可实现统一 ripple，不需要变更设计 token。
- Unknowns: MagicOS 11 最终按压观感待用户装机验证。
- Risks: 隐藏数据说明减少界面隐私提示，但不改变任何数据处理；由用户明确授权。
- Deferred: 目标机上的完整手工页面走查及其它机型验证。
- Non-goals: 后端、数据库、数据处理、分支返回、内容卡片行为及模拟器测试。
- Skipped non-critical questions and reason: 用户已确认设计方案和验收边界，无待决事项。

## Goal

改进全应用明确按钮反馈，并将设置入口迁至首页右上角，保持现有设置调用来源和返回行为。

## Scope

### In scope
- Android 按钮与图标/导航控件按压反馈。
- 手机、平板设置 tab 移除；首页右上角齿轮入口。
- 设置根页数据边界文案移除。
- 更新当前态 Android 与 UX 文档。
- 构建 debug APK，定向安装启动 ALP-AN00。

### Out of scope
- 数据处理/隐私策略、本地存储、安全配置、聊天业务和内容卡片行为。
- 模拟器及用户手工功能走查。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 按钮只改变视觉反馈；首页设置入口替代底部入口；聊天内设置与缺 Key 引导保持原来返回对象。
- Producer/consumer/sibling entrypoints and platform branches: HomeScreen、ChatScreen/ChatPresenter、MainActivity 的 showSettings/onBackRequested/swap、手机和平板导航布局、设置布局和按钮 drawable。
- Native bridge or independent-client compatibility/update requirements: N/A；无跨端接口。
- Behavior-neutral non-applicability reason, if applicable: N/A；包含导航变化，需覆盖返回路径。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 设置 tab 移除并移至首页右上角；设置页移除数据边界展示；按钮 feedback 统一。
- Authoritative code/config/migration evidence: MainActivity.java、HomeScreen.java、相关布局及 drawable。
- Documentation surfaces checked: android/README.md、设置页设计方案、对应 dated prototype。
- Documentation files updated: android/README.md；Docs/04-ux-design/知芽-TreeLearn-设置页重设计方案-V1.0.md（历史快照标记）；Docs/04-ux-design/知芽-TreeLearn-设置入口与按钮反馈更新-2026-10-09.md；Docs/04-ux-design/design-system/README.md。
- Producer/consumer/sibling/compatibility paths checked: HomeScreen → MainActivity.showSettings；ChatScreen/ChatPresenter → showSettings；MainActivity back/swap；手机/平板布局；设置布局与实际数据存储/Provider 调用未改。
- Superseded phrases/examples reverse-searched: Android 当前代码/README 不再引用旧设置 tab 或数据边界区块；9 月设置稿与原型保留为有明确链接的历史快照。
- documentation impact: none reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改 PRD；更新当前态文档，标记旧设置交互稿为历史快照。

## Acceptance criteria

- [ ] 按钮有一致 ripple，且禁用/危险语义保留；真实触摸效果待用户走查。
- [ ] 首页右上角齿轮打开设置并能返回首页；聊天内设置和缺 Key 引导保持返回来源；设备行为待用户走查。
- [x] 底部只有首页与学习库；设置页不再展示数据边界；数据行为不变。静态布局/生产路径已核对。
- [x] 定向 SettingsBackRouteTest 2/2、Android lint、依赖方向与最终 APK Java-only guard 通过；全量 JVM 套件另有一项聊天阶段失败，隔离证据列于子任务。
- [x] Debug APK 安装并启动于 ALP-AN00；不使用模拟器。
- [x] 文档影响、差异检查与字符完整性检查通过。

## TDD execution record

### RED — failing test first
- Test/scenario: 子任务负责 RED/GREEN 记录。
- Command: N/A — parent requirement only.
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: 子任务负责实现记录。
- Command: N/A — parent requirement only.
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 用户方案确认日期：2026-10-09。
- 不新增遥测事件；只改界面视觉与入口。

## Verification report

- Verdict: PARTIAL — 独立 verifier 已完成只读核对。
- Evidence: 子任务 T-2026-10-09-android-button-feedback-home-settings-entry-001.md 的验证记录与 artifacts/evidence/T-2026-10-09-001-* 证据覆盖单测、lint、Java-only、构建、部署、启动和文档完整性。
- Changed/deleted assertion rationale and equivalent behavior coverage: 更新旧三 tab instrumentation 断言，改测两个底栏入口、首页齿轮打开设置和设置返回首页；新用例仅编译，本轮未在设备运行。
- Gaps: 真机按钮 ripple、首页设置点击/返回、聊天入口、缺 Key 引导未触摸验收；全量 JVM 831 项有 1 项 ChatGenerationStageTest 失败，隔离复跑为 SENDING 预期/IDLE 实际，未归责本次 UI 改动；verifier 因缺实机交互给 PARTIAL。
- Risks: MagicOS 11 上实际波纹观感和不同按钮背景对比尚未由用户确认；APK 已启动，不代表交互完成。

## Handoff notes

- Completed: 用户确认范围；实现子任务已构建并安装 debug APK 至 ALP-AN00；lint、依赖方向、Java-only guard 与文档校验通过。
- In progress: 子任务保持 verifying，等用户完成目标机实际交互走查。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: 用户在 ALP-AN00 走查设置入口/返回与按钮反馈；记录结果后关闭父/子任务。
- Watch-outs: 保留用户工作区已有差异；目标设备 ALP-AN00，禁止模拟器。
