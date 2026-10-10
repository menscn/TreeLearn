# Treelearn Harness Task: 本地历史加载性能基线诊断

id: T-2026-10-01-119
title: 本地历史加载性能基线诊断
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

Parent summary: T-2026-10-01-android-daily-use-issues-111.md 的 #9 报告本地保存的历史 AI 对话打开很慢，用户希望知道原因。

- Problem/background: 历史在本地保存但加载体验慢；没有数据库规模、设备耗时或具体卡顿阶段数据。
- Users/scenarios: Magic8 Pro Air / MagicOS 11 上打开历史列表并进入含多条消息/分支的会话。
- Expected outcome: 获得可重复的冷/热加载基线，区分列表、选中会话、数据库读取与 UI 首次/完整渲染阶段，定位瓶颈；不臆造性能阈值。
- Core flow and reversibility: 记录同一数据集 → 冷启动加载 → 热启动重复 → 导出耗时与规模摘要；不清理、改写或删除用户历史。
- Inputs/outputs: 本地历史规模计数、页面选择与时间点；输出为分布/中位数等可复核基线和瓶颈假设/证据。
- State and exception handling: 目标机或数据集不可用时标记 PARTIAL；冷启动方式不一致时单独报告，不混为同一指标。
- Constraints and trade-offs: 只诊断，无生产改动；不记录会话正文、标题、用户标识或敏感数据库转储；不设定未经用户确认的绝对时延线。
- AI recommendation and rationale: 先量化同设备同数据集的阶段耗时及规模，再根据实际数据设定优化目标，避免盲目改查询或引入索引。
- User choice and custom additions: 用户确认 Magic8 Pro Air / MagicOS 11；本轮仅拆 Harness。
- AI-autonomous decisions: 用冷/热两组重复样本和固定数据规模记录；优化任务由实测瓶颈决定。
- User-confirmation decisions: 不虚构可接受耗时阈值。
- Acceptance evidence: 设备/应用版本、测试数据规模、各阶段操作/时间戳、多次结果摘要与安全证据。
- Facts: Android 本地 SQLite 为业务权威；本任务不改变存储契约。
- Assumptions: 目标机存在可重复打开的历史主题，或能在不接触敏感内容的前提下构造等规模测试数据。
- Unknowns: 历史主题数量、消息/分支分布、慢在首页列表还是会话渲染、冷/热差异。
- Risks: 用小型空数据集得出的性能结论不能代表用户现场；抓取数据库原文有隐私风险。
- Deferred: 根据基线和用户目标另建优化实现任务；不在此任务中直接改 SQL/缓存/索引。
- Non-goals: 数据库迁移、清理或重写历史、无基线的查询重构。
- Skipped non-critical questions and reason: 无；先采基线即可发现后续需拍板的性能目标。

## Goal

在目标设备上建立本地历史加载的可复核冷/热基线，并用实际耗时证据定位慢点；不修改生产代码或历史数据。

## Scope

### In scope
- 固定测试数据集，记录主题/分支/消息数量等无内容元数据；区分历史列表可见、会话首屏可见、完整消息渲染完成。
- 在目标机分别采集冷启动与热加载至少 5 次，保留原始时间点并汇总中位数/波动范围。
- 追踪活动 SQLite 读取/结果映射/主线程渲染调用路径；有证据时提出独立优化任务和目标阈值待拍板项。

### Out of scope
- 任何生产代码/数据库/索引修改、用户历史导出或删除、未测即设置性能门槛。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: N/A — 只读基线测量，保持历史数据与加载行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `SqliteChatStore`/历史读取 → `ChatPresenter` 初次加载 → `ChatScreen` 消息列表渲染；启动首页列表与会话选择入口分别计时。
- Native bridge or independent-client compatibility/update requirements: N/A — 不改接口/桥接。
- Behavior-neutral non-applicability reason, if applicable: 不改运行行为、数据或契约。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: None — 记录实测基线，不宣称改变运行行为。
- Authoritative code/config/migration evidence: 活动的 `SqliteChatStore.java`、`ChatPresenter.java`、`ChatScreen.java` 及实际 SQLite 查询路径。
- Documentation surfaces checked: `android/README.md` 的本地优先存储和历史加载说明。
- Documentation files updated: None — 本任务产物为性能诊断证据，不修改当前态文档。
- Producer/consumer/sibling/compatibility paths checked: 历史列表、会话首次读取、消息映射/渲染及冷启动/热加载路径。
- Superseded phrases/examples reverse-searched: N/A — 无文档改动。
- `documentation impact: none` reason and evidence, if applicable: 只读测量；保持现有加载契约和 SQLite 行为。
- Historical/PRD handling: 报告标注设备/构建/数据规模/采集日期，作为带条件的性能快照，不冒充当前所有设备基准。

## Acceptance criteria

- [ ] 记录 Magic8 Pro Air / MagicOS 11、应用版本、数据集计数与至少 5 次冷/热样本；冷/热操作条件明确且一致。
- [ ] 分别给出历史列表、选中主题首屏、完整渲染的时间摘要与波动；指出证据支持的瓶颈及仍未知项，不记录会话内容。
- [ ] 不设置无依据的绝对性能阈值、不改数据库/生产代码；设备或数据集不足时如实标 PARTIAL，并列出后续优化任务/目标待拍板项。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A — 只读性能基线任务。
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A — 本任务不修改生产实现。
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: N/A
- Reason: TDD 不适用于只读测量。
- User approval: N/A
- Alternative verification: 固定数据集的设备计时与只读代码路径分析。

## Implementation notes

- 生成性能证据时记录采集命令/步骤、设备、构建、运行次数和退出码；证据只包含无内容规模计数与耗时。
- 不复制含用户正文的数据库到仓库；需要测试数据时使用脱敏或合成 fixture。

## Verification report

- Verdict: BLOCKED
- Evidence: `artifacts/evidence/T-2026-10-01-target-device-availability.txt` 记录 `adb devices -l` 与 `getprop`；当前连接为 HONOR ALP-AN00 / Android 13 / MagicOS 7.2.0.106，不是指定 Magic8 Pro Air / MagicOS 11。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A — 无代码/断言变更。
- Gaps: 指定目标机未连接；数据集规模和各阶段耗时仍未知。
- Risks: 测量样本与用户实际历史体量不匹配。

## Handoff notes

- Completed: 已将 #9 定义为先测基线、再由证据决定优化任务。
- In progress: 等待目标机接入后继续测量。
- Blocker: 指定目标机未连接，无法采集所要求的同设备基线。
- Unblock condition: 接入 Magic8 Pro Air / MagicOS 11，并可重复打开同一历史数据集。
- Next steps: 目标机可用后按固定数据集采集冷/热耗时及阶段瓶颈，不设未经确认的阈值。
- Watch-outs: 不拍脑袋设置阈值、不输出历史正文、不破坏用户本地 SQLite 数据。
