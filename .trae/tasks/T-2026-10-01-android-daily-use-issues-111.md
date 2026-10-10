# Treelearn Harness Task: Android 日常使用问题闭环（父需求）

id: T-2026-10-01-111
title: Android 日常使用问题闭环（父需求）
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

- Problem/background: 用户报告十项日常使用问题：①控件/侧边栏/输入焦点偶发无响应；②发送后输入法未自动收起；③新建子分支后返回首页而非父分支；④使用中闪退；⑤分支导航/总览的顶部控件与状态栏冲突；⑥长输出持续自动滚动，手动阅读困难；⑦关闭思考后仍偶尔出现 `treelearn_reasoning`；⑧AI 已回复但用户问题没有显示；⑨本地历史加载慢；⑩点击 follow-up 推荐问题无响应。反馈是现场观察，不代表已在当前工作区版本复现，也不预设共同根因。
- Users/scenarios: Android 原生应用单人用户；目标验收设备为用户提供的 Magic8 Pro Air、MagicOS 11；覆盖输入、生成、长回答阅读、分支导航/总览及重开历史主题。
- Expected outcome: 十项分别得到有证据的诊断、修复或明确未复现结论；已确认的交互问题有独立任务；未知性能/崩溃问题先取证再决定后续修复；消息、分支关系与阅读位置不丢失。
- Core flow and reversibility: 打开历史分支 → 输入并发送 → 看见自己的问题及生成状态 → 阅读长回答并切换跟随/手动滚动 → 创建子分支后返回父分支 → 重开历史并恢复阅读。发送被拒绝时保留草稿；诊断发现新根因时新增有界修复任务，不以诊断通过代替修复完成。
- Inputs/outputs: 输入为用户问题、推荐点击、返回/导航操作和滚动手势；输出为本地持久消息、可见生成状态、正确返回目标、可控滚动及可复核的设备/性能/崩溃证据。
- State and exception handling: 发送被接受后清空输入并收起输入法；拒绝时保留草稿和错误反馈。关闭思考时内部推理与结构标签不得污染可见回答。卡顿、闪退或加载慢未复现时标记未知/未验证，不报 PASS。
- Constraints and trade-offs: Android-only、Java-only、本地 SQLite 为业务权威；每个实现任务生产代码估算不超过 200 行；历史加载先用同设备同数据集测基线，不臆造毫秒阈值；保留现有工作区差异。
- AI recommendation and rationale: 将明确行为与未知根因分开；先做可复现的诊断/基线，再按独立行为修复，避免十项合并成大改造或凭症状猜原因。
- User choice and custom additions: 用户确认只处理 Android，验收设备为 Magic8 Pro Air / MagicOS 11，并要求“拆解 Harness 就行”。用户反馈要求：超过一屏后暂停自动跟随；显示向下按钮供用户恢复跟随；触摸/滚动可随时手动阅读。
- AI-autonomous decisions: 发送被接受后才收起键盘；发送失败保留草稿；推荐文字与分支热区分开验收；不设置未经确认的性能绝对门槛。
- User-confirmation decisions: Android-only；Magic8 Pro Air / MagicOS 11 首要验收；十项反馈纳入本轮；本轮只建立任务，不改业务代码。
- Acceptance evidence: 每项记录设备/构建、步骤、命令或界面观察、预期与实际、崩溃或耗时证据及缺口；实现任务先 RED 后 GREEN；所有 Android 实现完成前运行 Java-only guard；未复现不等于修复。
- Facts: `MainActivity.onBackRequested` 对普通聊天返回当前 tab，代码未显式记住新建分支的父分支；`ChatScreen` 已有 follow-latest 和 window inset 逻辑，但现行滚动触发条件不同于本次要求；`ChatScreen.renderMessages` 会遍历消息行；`ChatWiring.send` 在提交生成前持久化用户消息；推理包装标签由 `DeepSeekContextEncoder` 用于历史上下文编码，当前文档要求不可显示到 UI。已验收的文本选择/高亮任务为 T-2026-09-29-107；子分支弹窗任务 T-2026-09-29-109 已验证；推荐分支自动发送 T-2026-09-29-108 仍在 verifying。
- Assumptions: 用户报告针对日常使用构建；目标设备可安装和交互验收；历史数据可在保留数据前提下重复测量。
- Unknowns: 设备 Android API/系统构建号和应用版本、闪退复现步骤/堆栈、无响应的具体页面/控件、历史数据规模与加载耗时、#10 点击的是推荐文字还是分支热区。诊断任务记录这些事实，不猜测填补。
- Risks: MagicOS 定制可能影响软键盘、状态栏、触摸和后台调度；工作区已有未提交更改，且相关任务 107/108/109 状态不同，必须保留并避免重复改写。
- Deferred: 只有诊断证明后才建立的卡顿/闪退/性能根因修复；其他 Android 设备兼容；全局 UI 重设计。
- Non-goals: Web/服务端、账号同步、云备份和远程遥测；无证据不改 SQLite schema/migration；不重复实现已验收的文本选择/高亮与子分支弹窗。
- Skipped non-critical questions and reason: 暂不追问性能绝对阈值，先取目标机基线；推荐文字与分支热区均列入回归以消除入口歧义。

## Goal

把十项反馈闭环到独立诊断、实现与设备验收任务；保留既有任务状态，所有完成结论由对应证据支持。

## Scope

### In scope
- #1/#4/#10：目标设备无响应、闪退和推荐点击入口的复现诊断。
- #2/#8：发送后键盘收起与用户问题可见性。
- #3：新建子分支后的返回目标。
- #5：分支导航/总览避让系统状态栏。
- #6：长输出暂停跟随、按钮恢复跟随、手动滚动。
- #7：关闭思考时阻止推理标记/内容泄漏到可见回答。
- #9：本地历史加载性能基线与瓶颈诊断。
- 所有十项在 Magic8 Pro Air / MagicOS 11 上的最终回归与证据索引。

### Out of scope
- 本父需求只编排任务，不实现生产代码。
- 无证据的根因修复、服务端/云功能、全站视觉改版或数据库迁移。
- 不覆盖用户工作区已有未提交修改，也不重开已验收任务。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 按用户明确反馈改进发送反馈、返回导航、系统栏、滚动模式和推理隔离；保留失败草稿、错误提示、消息顺序、分支关系和阅读位置。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `MainActivity.showChat/onBackRequested`；`ChatPresenter.send/publish` → `ChatWiring.send` → `SqliteChatStore` → `ChatScreen.renderMessages`；`ChatScreen` window insets/scroll；`:ai` `DeepSeekContextEncoder` → 生成响应 → 对话行渲染。
- Native bridge or independent-client compatibility/update requirements: N/A；本地 Android 应用，无前后端桥接。
- Behavior-neutral non-applicability reason, if applicable: N/A；子任务包含用户可见行为改变。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 当前未改运行行为；由各实现子任务记录实际行为变化。
- Authoritative code/config/migration evidence: `android/app/src/main/java/com/treelearn/app/MainActivity.java`、`screen/ChatScreen.java`、`ui/ChatPresenter.java`、`wiring/ChatWiring.java`、`:ai` `DeepSeekContextEncoder.java`。
- Documentation surfaces checked: V3.0 需求基准的分支返回流程；Android 本地优先技术方案；DeepSeek 提示词设计中上下文标签不可显示到 UI 的约束；Android README。
- Documentation files updated: N/A — 当前仅创建任务。
- Producer/consumer/sibling/compatibility paths checked: 已按发送、返回、导航、生成流、滚动和历史加载分配子任务；目标机证据待执行。
- Superseded phrases/examples reverse-searched: PENDING — 实现任务按实际改动反查。
- `documentation impact: none` reason and evidence, if applicable: 当前没有生产代码/配置/契约改动；每个实现任务须重新判断。
- Historical/PRD handling: 不静默改写历史文档；用户可见规则变化时在对应任务更新当前态文档。

## Acceptance criteria

- [ ] 问题映射：#1/#10 → 112（无响应/推荐点击诊断）；#2/#8 → 114（发送后收键盘与用户问题可见）；#3 → 115；#4 → 113；#5 → 116；#6 → 117；#7 → 118；#9 → 119；十项最终目标机矩阵 → 120。
- [ ] 现有 107 仅覆盖文本选中/高亮，108 覆盖推荐分支自动发送且当前 verifying，109 覆盖子分支弹窗；不得把这些相关任务误记为 #1/#10 已整体闭环。
- [ ] 实现任务 114–118 均估算 ≤200 行并要求 TDD；#1/#4/#9/#10 先诊断，证据确认根因后再建独立修复任务。
- [ ] 112–120 每个新任务均通过 Task v2 定向校验；MagicOS 11 实机证据缺失时不得将交互标记为已修复；诊断产生的后续修复任务需加入 120 的依赖后再验收。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A — requirement/task decomposition only
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A — parent task introduces no production code
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: N/A
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 本轮只拆 Harness，不执行生产代码修改；现有未提交文件不作覆盖、清理或提交。
- 子任务映射：112 诊断 #1/#10；113 诊断 #4；114 修复 #2/#8；115 修复 #3；116 修复 #5；117 修复 #6；118 修复 #7；119 诊断 #9；120 汇总十项目标机回归。
- 诊断发现的新根因应追加独立、可验收的修复任务；开始 120 前将新增修复加入其 depends_on。

## Verification report

- Verdict: PENDING
- Evidence: 子任务状态见本文件交接；T114 定向测试 XML 为 52 tests / 0 failures，Android lint 与独立 Java-only guard 退出 0；T114/T116 目标设备检查均确认指定 Magic8 Pro Air / MagicOS 11 不可用。最终设备矩阵尚未执行。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A — no production assertions changed.
- Gaps: 目标设备真实交互、崩溃证据和历史性能基线待子任务采集。
- Risks: 诊断可能要求追加修复任务，父任务不能在问题仍已知未修时标为 verified。

## Handoff notes

- Completed: 用户确认 Android-only、目标设备及十项反馈；Harness 子任务已拆分。T114 本地实现、定向测试、lint、Java-only guard 与 README 更新已通过；因目标机缺席标记 blocked。T116 已核对 Insets 入口但未猜测修改，同样因无法在目标机定位具体冲突而 blocked。
- In progress: T118 已由 Android owner 完成 RED；模拟 `ChatScreen` 中包含完整 reasoning/answer framing 的历史消息时，关闭思考仍将 framing 作为 answer 显示。Android owner 正在按 RED 补回归场景并修复应用内消费边界；provider 是否实际回显该协议仍未知。
- Blocker: 当前连接设备是 HONOR ALP-AN00 / MagicOS 7.2.0.106 / Android 13，不是指定 Magic8 Pro Air / MagicOS 11；阻塞 112/113/116/119 的目标机诊断、114/117/118 实机验收及最终矩阵。
- Unblock condition: 接入 Magic8 Pro Air / MagicOS 11；同时确保诊断采样不记录聊天正文、回答、推理或密钥。
- Next steps: 完成 T118 GREEN 与独立复核后处理 T117；T115 等其依赖的 108 进入 `verified` 后再开工；目标机接入后恢复 112/113/116/119，并将诊断新增修复纳入 T120 依赖。
- Watch-outs: 保留 T-2026-09-29-107/108/109 与所有用户现有工作区差异；不在错误设备安装 APK；日志/证据不写问题正文、回答正文、推理正文、完整提示词或 API Key。
