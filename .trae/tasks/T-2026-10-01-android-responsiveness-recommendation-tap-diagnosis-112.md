# Treelearn Harness Task: Android 无响应与推荐点击诊断

id: T-2026-10-01-112
title: Android 无响应与推荐点击诊断
template_version: 2
task_type: verification
status: blocked
parent_requirement: T-2026-10-01-android-daily-use-issues-111.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

Parent summary: T-2026-10-01-android-daily-use-issues-111.md 将十项现场反馈拆为可验证 Android 切片。本任务只诊断 #1 与 #10，不预设共同根因。

- Problem/background: #1 报告侧栏返回、输入键盘唤起、文本选择及其他按钮偶发无响应；#10 报告点击 follow-up 推荐问题无响应，并怀疑与 #1 类似。当前没有复现步骤或设备日志。
- Users/scenarios: Magic8 Pro Air、MagicOS 11 上进入对话、打开侧栏、聚焦输入、选择文字、点击普通推荐文字及其分支操作入口。
- Expected outcome: 按交互入口分别记录是否复现、触摸是否到达、界面是否阻塞及与既有 107/108/109 任务的真实重叠，给出有证据的下一步，不改生产代码。
- Core flow and reversibility: 使用不含敏感内容的测试对话重复执行；记录发生/未发生后退出页面即可恢复，不修改历史数据。
- Inputs/outputs: 输入为控件/触摸操作与设备构建信息；输出为复现步骤、频次、时间点、脱敏诊断日志和建议的独立修复范围。
- State and exception handling: 无法取得目标设备或现象未复现时记录 PARTIAL/未复现，不报告已修复；日志采集失败时保留手工步骤和缺口。
- Constraints and trade-offs: 本轮只拆任务；不猜测为主线程阻塞或热区冲突；不记录聊天正文、推理内容、密钥或完整请求。
- AI recommendation and rationale: 将通用无响应与推荐入口分开观察，避免把相似症状当成同一根因。
- User choice and custom additions: 用户确认 Android-only、Magic8 Pro Air / MagicOS 11，并要求只拆 Harness。
- AI-autonomous decisions: 把推荐文字点击和分支热区分别测试；仅对证据支持的根因创建后续修复任务。
- User-confirmation decisions: 目标平台与本轮不写生产代码已确认。
- Acceptance evidence: 目标设备复现记录、带时间戳且已脱敏的诊断材料、每个入口的预期/实际结果。
- Facts: 任务 107 已覆盖文本选中/高亮反馈；任务 108 覆盖推荐分支创建后的自动发送且仍在 verifying；任务 109 覆盖子分支弹窗显示。这些任务不自动代表本反馈已闭环。
- Assumptions: 可安装当前待验 APK 并连接目标设备。
- Unknowns: #1 的具体触发频率与页面状态；#10 点击的是推荐文字还是分支热区、预期是直接发送还是打开分支。
- Risks: #10 若只测分支按钮会漏掉普通推荐文字入口；现有未提交变更不代表已验证或已发布。
- Deferred: 根因修复任务待诊断证据形成后另建。
- Non-goals: 修复点击/线程/输入焦点代码；重做选择交互；重开已验证的 107/109。
- Skipped non-critical questions and reason: 无，当前先收集能区分事件分发与页面无响应的证据。

## Goal

在 Magic8 Pro Air / MagicOS 11 上分别诊断 #1 的控件无响应与 #10 的推荐点击无响应，并形成可派发的根因证据；不修改生产代码。

## Scope

### In scope
- 记录应用版本、系统构建、精确页面/控件、操作顺序、复现次数与时间点；普通推荐文字与分支热区分别测试。
- 对可复现问题采集脱敏的触摸/窗口/主线程相关证据，并将发现映射到实际调用路径。
- 结论指出 107/108/109 的覆盖边界；如有新根因，提出独立修复任务范围。

### Out of scope
- 生产代码、测试代码、配置、文档或数据迁移修改；凭症状直接定根因；把未复现解释成已修复。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: N/A — 只收集诊断证据，无运行时行为变更。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 核对 `MainActivity` 的页面/返回入口、`ChatScreen` 的触摸控件及推荐消息点击消费者；具体路径以复现后调用链为准。
- Native bridge or independent-client compatibility/update requirements: N/A — 本任务不改桥接或接口。
- Behavior-neutral non-applicability reason, if applicable: 只读诊断，不改变 Android 行为。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: None.
- Authoritative code/config/migration evidence: 活动版本的 `MainActivity.java`、`ChatScreen.java`、推荐消息生产/点击处理路径；执行时记录实际命中文件与行。
- Documentation surfaces checked: 任务 107/108/109 的范围和当前 Android 用户流程文档。
- Documentation files updated: None — 诊断任务，无生产事实变更。
- Producer/consumer/sibling/compatibility paths checked: #1 与 #10 的具体生产者/消费者待设备复现后记录；文本选择、普通推荐点击、推荐分支热区分别核对。
- Superseded phrases/examples reverse-searched: N/A — 无文档改动。
- `documentation impact: none` reason and evidence, if applicable: 不改运行行为；以只读代码路径核对和目标设备证据证明。
- Historical/PRD handling: 不修改历史需求或既有任务结论。

## Acceptance criteria

- [ ] 分别记录侧栏返回、输入法唤起、文本选择/其他按钮及普通推荐文字/分支热区的操作步骤、尝试次数、预期与实际。
- [ ] 复现时提供脱敏时间点与诊断证据；不得包含聊天正文、推理文本、密钥或完整请求；未复现明确报告为未复现。
- [ ] 结论准确说明与 107/108/109 的交集；若证据支持新根因，给出独立修复任务建议，不改代码、不报告修复 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A — 只读设备诊断任务。
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A — 本任务不修改实现。
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: N/A
- Reason: TDD 不适用于只读诊断。
- User approval: N/A
- Alternative verification: 目标设备复现步骤与脱敏诊断证据。

## Implementation notes

- 不触碰工作区已有代码改动；现象未复现时保留未知项。
- 推荐分支自动发送任务 108 的现有状态必须如实引用，不能用它替代普通推荐点击验收。

## Verification report

- Verdict: BLOCKED
- Evidence: `artifacts/evidence/T-2026-10-01-target-device-availability.txt` 记录 `adb devices -l` 与 `getprop`；当前连接为 HONOR ALP-AN00 / Android 13 / MagicOS 7.2.0.106，不是指定 Magic8 Pro Air / MagicOS 11。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A — 无代码和断言变更。
- Gaps: 指定目标机未连接；#1/#10 的具体页面复现和日志仍未知。
- Risks: 设备不可用时只能给 PARTIAL 结论。

## Handoff notes

- Completed: 已依据用户截图拆出 #1/#10 诊断边界。
- In progress: 等待目标机接入后继续诊断。
- Blocker: 指定 Magic8 Pro Air / MagicOS 11 未连接。
- Unblock condition: 接入 Magic8 Pro Air / MagicOS 11 并提供待验 APK。
- Next steps: 目标机接入后分别复现侧栏/输入/按钮、普通推荐文字与分支热区，记录脱敏证据。
- Watch-outs: 107 的文本选择反馈、108 的分支自动发送、109 的弹窗显示彼此不同；不要把相似表象合并成同一缺陷。
