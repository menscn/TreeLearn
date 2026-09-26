# Treelearn Harness Task: 对话页模型 chip 接分工存储与回落提示

id: T-2026-09-23-103
title: 对话页模型 chip 接分工存储与回落提示
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-09-23-model-role-settings-screen-088.md, T-2026-09-23-quick-model-derived-tasks-089.md]
tdd_mode: required

## Goal

对话页模型 chip 接入 088 交付的「聊天模型」分工解析链（全局默认 + 会话内临时覆盖），并在所选模型失效回落时经既有 Notice 通道给一次性提示——承接 verifier-088 发现的无承接缺口。

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务承接 088 验收时发现的缺口——对话页模型 chip 尚未接「聊天模型」分工存储，回落发生时也无一次性 Notice。
- Problem/background: 088 交付了三分工设置页与回落规则（SqliteModelSettingsStore），089/090 已消费 Role.FAST/TRANSLATION；但对话页模型 chip 仍读旧的全局模型设置，未走 Role.CHAT 分工链；所选聊天模型被删/失效回落时，对话页无一次性提示。verifier-088 在 Gaps 中明确此缺口，互指消费面无承接任务。
- Users/scenarios: 用户在对话页点模型 chip 选择/确认当前聊天模型（默认取分工存储 Role.CHAT 解析结果，可临时覆盖本会话）；设置页删除该模型后回到对话页，chip 回落为内置默认并看到一次性 Notice 提示。
- Expected outcome: chip 读取走 SqliteModelSettingsStore.read(Role.CHAT) 解析链；临时覆盖仅影响本会话不写穿；回落触发 HomeViewState/ChatViewState 既有 Notice 通道一次性上屏。
- Core flow and reversability: chip 点击弹出选择器（复用 ModelThinkingPicker 模式）；选择写临时覆盖；重进对话回落解析值；Notice 只在回落发生的那次会话渲染一次。
- Inputs/outputs: 输入 088 Store 与回落规则；输出 chip 接线 + 临时覆盖 + 一次性 Notice + 单测。
- State and exception handling: 分工存储为空/全部不可用 → 置空态 chip 显示占位并发 NO_MODELS 引导（与 088 语义一致）；不 crash。
- Constraints and trade-offs: 不改 088 的 resolve 纯函数与 Store；临时覆盖的存续边界=本会话内存（进程死即失），不做持久化（与 079 规格「全局默认+临时覆盖」口径一致）。
- AI recommendation and rationale: 临时覆盖放 ChatViewState 或 ChatPresenter 内存字段，避免另开存储。
- User choice and custom additions: 无。
- AI-autonomous decisions: Notice 文案复用 NoticeText 既有中文风格；chip 选择器布局复用现有 picker 弹层。
- User-confirmation decisions: 无。
- Acceptance evidence: RED/GREEN + guard。
- Facts: 088 verified（ModelSettingsPresenter.java:123 resolve 链）；ChatPresenter/ChatViewState 现有 chip 与 Notice 通道。
- Assumptions: 088/089 verified。
- Unknowns: 无。
- Risks: 触 ChatPresenter 热区，回归面大——现有 ChatPresenter 系测试必须全绿。
- Deferred: 无。
- Non-goals: 不做每会话持久化、不做 chip 内思考预算调节（已在既有思考开关）。
- Skipped non-critical questions and reason: 临时覆盖的粒度（会话级）按规格既定口径，不再询问。

## Scope

### In scope

- 对话页模型 chip 读 Role.CHAT 分工解析链；临时覆盖（会话内存）；回落一次性 Notice；单测。

### Out of scope

- 088/089/090 已交付面；思考开关；持久化临时覆盖。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: chip 数据源从旧全局设置切换为分工解析；开关关闭/未配置临时覆盖时对话行为与现状逐参一致。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): ChatPresenter（chip 与 Notice）、SqliteModelSettingsStore.read(Role.CHAT)（088 交付，不修改）。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 对话页模型 chip 数据源与回落提示行为。
- Authoritative code/config/migration evidence: ChatPresenter/ChatViewState diff。
- Documentation surfaces checked: SPEC-079 模型设置节。
- Documentation files updated: PRD 对话页当前态说明。
- Producer/consumer/sibling/compatibility paths checked: 088/089 消费面。
- Superseded phrases/examples reverse-searched: 旧「全局模型设置」表述反查。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] RED/GREEN：chip 读分工解析（默认/回落/置空三态）、临时覆盖会话内生效且不写穿、回落一次性 Notice 断言。
- [ ] 既有 ChatPresenter/ChatWiring 全量测试回归全绿（关闭覆盖路径行为不变）。
- [ ] 44dp/token/Java-only guard + 全量单测通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `ChatModelChipTest`（新增，JVM）——①start 解析 Role.CHAT 分工（modelId=deepseek-v4-pro → chip 换 PRO，无 notice）；②fallback=BUILTIN → 一次性 CHAT_MODEL_FALLBACK notice（后续 render 不重复）；③Assignment.EMPTY → NO_MODELS 引导且 model 保持兜底 tier；④chip 选择只改会话 model、不写穿全局 settings，重进对话回落解析值。RED 阶段带 ChatRoleSource 的构造器尚不存在，预期编译失败。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatModelChipTest"`
- Exit code: 1
- Observed failure: 编译失败——ChatPresenter 无 (…, NoPositionStore, ChatRoleSource) 形状的构造器（T-103-red-2026-09-25.log）。
- Failure reason validated: 唯一错误指向本片待实现构造器，无一条指向既有行为。

### GREEN — minimum implementation
- Implementation: ①`ChatPresenter` 新增 `ChatRoleSource` 接口（`read(): Assignment`）与 16 参主构造器（11 参与 15 参旧构造器变委托，存量调用零改动）；`start()` 在既有 `read()` 之后跑 `resolveChatRole()`——`Fallback.EMPTY`→`NO_MODELS`、`BUILTIN/EARLIEST`→`CHAT_MODEL_FALLBACK`、干净解析→静默 `withModel`，全部经 `ui.post` 回主线程（真机 `CalledFromWrongThreadException` 实测后修正，且 lambda 捕获改 final 副本）；②`onModelSelected` 不再写穿 `SettingsStore`（chip=会话内覆盖，持久选择只属 088 设置子页）；③`Notice` 枚举加 `CHAT_MODEL_FALLBACK`/`NO_MODELS`，`NoticeText` 加两案例、strings 加两键；④`MainActivity` 生产接线 `chatRoleSource = SqliteModelSettingsStore::read`。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatModelChipTest"`
- Exit code: 0
- Passing evidence: 4/4（GREEN 同 RED 命令；T-103-green-2026-09-25.log EXITCODE=0，BUILD SUCCESSFUL）。
- Regression evidence: 全量 JVM 单测 629/0/0（files=72；既有 `ChatPresenterTest.modelAndThinkingChangesArePersistedAndThenRendered` 按本片有意变更更新为「chip 不写穿」新语义并更名）；真机 BottomTabShell 5/5（线程修复后）；check EXITCODE=0（Windows 瞬时文件锁首跑失败，重跑过）；Java-only PASS；证据 `artifacts/evidence/T-103-chat-model-chip.txt`（CMD-1 至 CMD-9）。

### REFACTOR — optional
- Cleanup: 无独立轮——实现中已把三个旧构造器收敛为委托链，避免四份字段初始化漂移。
- Regression evidence: 全量单测 629/0/0 同上。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 实现者禁读 RikkaHub 源码。
- 与 094–098 串行执行，避免 ChatPresenter 热区并发写冲突。

## Verification report

- Verdict: PASS
- Evidence: RED/GREEN 同命令（`--tests ChatModelChipTest`）EXITCODE 1→0；全量单测 629/0/0；真机外壳 5/5；check 0；Java-only PASS（13 OK 行）。汇总 `artifacts/evidence/T-103-chat-model-chip.txt`（CMD-1 至 CMD-9，命令+退出码内嵌）。
- Changed/deleted assertion rationale and equivalent behavior coverage: `ChatPresenterTest.modelAndThinkingChangesArePersistedAndThenRendered` 被删除——它断言的「chip 选择写穿 `SettingsStore`」正是本片有意废止的行为（088 分工落地后 chip 写穿即双写漂移源）；等价覆盖：新 `ChatModelChipTest.testFour` 断言选择后 chip 生效、settings 未被触碰、重进对话回落分工解析值。
- Gaps: `modelId`→tier 映射只覆盖内置 wireId；映射失败的第三方模型 id 静默保持原 tier（无通知）——V2.2 无第三方模型供给前为死路径，若后续开放第三方模型须补「不可映射」通知。
- Risks: 最大风险是 chip 语义变更使「模型偏好被重置」的观感（旧会话回落分工值）——由一次性 fallback 通知解释；其次是 resolve 在后台线程 render 的线程违规（真机已踩、已修复为 `ui.post` 并在 README 记录教训）。

## Handoff notes

- Completed: 分工解析三态（干净/回落/空）与 chip 会话覆盖语义全部落地并 verified；`ChatRoleSource` 端口使 presenter 与 088 存储解耦；README 模型小节记录分界与线程教训。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 主线下片 T-104（依赖 089 的思考分工，模式与 103 同构）。
- Watch-outs: (1) `resolveChatRole` 的 render 必须走 `ui.post`（真机实测 CalledFromWrongThreadException）；(2) Notice 一次性语义由测试锁定，后续改渲染循环不得引入重复提示；(3) chip 不写穿 `SettingsStore` 是有意决策，回退须先经用户拍板。
