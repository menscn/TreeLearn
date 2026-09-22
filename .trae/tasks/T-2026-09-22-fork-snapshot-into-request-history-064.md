# Treelearn Harness Task: 创建快照进入请求上下文 + fork 守卫与串屏修复

id: T-2026-09-22-064
title: 创建快照进入请求上下文 + fork 守卫与串屏修复
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-22-chat-defects-ime-reasoning-fork-062.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 140
shared_code_touched: true
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 修复对话页三缺陷；本片负责数据正确性核心——子分支的请求必须携带出生快照上下文，并顺带修复两处已定位的相邻缺陷。
- Problem/background: 用户实测：新建分支后发出的问题不携带任何上游上下文，AI 无从回答。根因：`SqliteChatStore.history()`（第 133–156 行）拼请求历史时只读本分支消息行，而 `ChatWiring.fork`（第 203–260 行）在分叉时写入的创建快照（`context_snapshots`，含上游全部用户消息与稳定版本引用）**没有任何读回路径**——直接违反 PRD §6.2.4 规格第 2 条「当前分支的正常 AI 回答只使用本分支对话及出生时的上游快照」。
- Users/scenarios: 用户从稳定回答新建分支后直接提问，AI 依据分叉时的上游背景回答。
- Expected outcome: 子分支首个问题（及后续问题）的请求历史 = 出生快照的 sources（按序展开为用户消息/稳定版本 entry）+ 本分支自身消息行；快照缺失（根分支）行为不变。
- Core flow and reversibility: 读路径扩展，无写路径变更，回滚即删新增读取与合并。
- Inputs/outputs: 输入不变（branchId + questionOrdinal）；输出 `ContextPlan` 在子分支场景多出快照前置 entry。
- State and exception handling: 快照解码失败（`ContextSnapshot.fromStored` 返回 failure）按 STORAGE 拒绝本次发送，不静默丢弃上游背景（PRD §6.2.4 异常 2）；快照 source 指向的行缺失同此。
- Constraints and trade-offs: 不改 schema/migration/写路径；合并逻辑必须可 JVM 测试（纯 Java 类型），DAO 只做薄读取。
- AI recommendation and rationale: 顺带修两处已定位缺陷——① `ChatWiring.fork` 缺 Key 前置拦截（send/retry/reset 都有，PRD §6.2.3 异常 2 要求）；② `MainActivity.render`/`renderPosition` 不校验 branchId，分叉切屏后旧 presenter 的收尾 read 会把旧分支内容闪进新屏（`ChatViewState.branchId` 已存在可直接校验）。三者同属 fork 链路正确性，合一片避免三次开 Gradle。
- User choice and custom additions: 验证层级经用户指示为**代码测试，不用真机或模拟器**。
- AI-autonomous decisions: source 的 `SUMMARY` 类型若 `ContextPlan.EntryKind` 无对应值则按 STORAGE 拒绝并留待域层扩展（当前分叉只产生 user/revision 两种 source，属防御分支）。
- User-confirmation decisions: 见 User choice。
- Acceptance evidence: JVM 单测 RED/GREEN + 全量单测回归 + assembleDebug。
- Facts: `ContextSnapshotDao.findByBranchId` 已存在（`android/data/.../ContextSnapshotDao.java`）；`ContextSnapshot.fromStored` 是唯一读回入口（payload 经 `ContextSnapshotCodec.decode`）；`ChatWiring.historyForRequest` 是请求历史的唯一拼装点（第 322–363 行）；现有 JVM 测试桩见 `app/src/test/java/com/treelearn/app/wiring/WiringDoubles.java`。
- Assumptions: 快照 payload 的 sources 引用的消息/版本行在分叉后依然存在（它们属父分支，删除分支才会级联）。
- Unknowns: 无。
- Risks: 真实 SQLite 行为未按规则验证（用户豁免），SQL 与 JVM 假实现不一致的漏检风险由语句级断言部分兜底。
- Deferred: 快照 sources 里的压缩摘要（SUMMARY）语义。
- Non-goals: 不改提示词结构与预算分配；不改 fork 的创建逻辑本身（065 才动锚定）。
- Skipped non-critical questions and reason: 无。

## Goal

子分支发出的每个问题，其请求历史都以其出生快照为前置上下文；fork 与切屏的两处相邻缺陷一并修复。

## Scope

### In scope
- `wiring/SqliteChatStore.java`：`history()` 先读 `ContextSnapshotDao.findByBranchId`，decode 后按 sources 顺序补 entry，再接本分支行。
- 快照 sources → `ContextPlan.Entry` 的合并逻辑抽为纯 JVM 类型（新文件，置于 `wiring/`），含缺失行/解码失败的失败通道。
- `wiring/ChatWiring.java`：`fork()` 增加 `missingKey()` 拦截（与 `send`/`replace` 同款）。
- `MainActivity.java`：`render(ChatViewState)` 与 `renderPosition` 校验 `state.branchId`/当前屏一致，不一致丢弃。
- 测试：新合并类型的 JVM 用例（快照前置、根分支无快照、行缺失拒绝、解码失败拒绝）；`WiringDoubles` 桩同步；fork 无 Key 拒绝的 presenter/命令级用例；DAO 语句级断言（参照 `SearchDaoStatementTest` 模式）。

### Out of scope
- `DeepSeekRequestAssembler`/预算逻辑；fork 锚定与 UI（065）；schema/migration；任何写路径。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是子分支请求历史多出快照前置段。必须保留：根分支历史行为逐字节不变；`historyForRequest` 的十轮压缩路径对前置 entry 的语义（快照 entry 是否参与压缩计数须在实现时核对并记录）；重置/重试的「历史与原尝试一致」规则（`questionBehind` 语义不动）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `SqliteChatStore.history` 与 `ChatWiring.historyForRequest`，消费者 `DeepSeekContextEncoder`/`DeepSeekRequestAssembler`；同级入口 `HomeWiring` 的根问题路径不经此函数。平台分支无（单布局逻辑）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 子分支请求携带快照上下文；fork 有 Key 守卫；陈旧状态被丢弃。
- Authoritative code/config/migration evidence: 上述文件。
- Documentation surfaces checked: `V3.2 PRD` §6.2.4（实现后核对措辞一致）、`android/README.md`。
- Documentation files updated: `android/README.md` 两处（grep 证据见下）。① `wiring.ChatStore`/`SqliteChatStore` 行（659 行）：`history()` 原表述「历史因此就是存储的行」已过时，改为「非根分支先经 `ContextSnapshot.fromStored` 读回出生快照、由 `SnapshotHistoryMerger` 按 sources 顺序展开成前置 entry，再接本分支行；快照不可解码或引用行缺失经 `ChatStore.SnapshotUnreadable` 拒绝」。② 860 行附近的「晚到状态丢弃」段补记 T-064 的跨分支校验（render 比对 `state.branchId`、`renderPosition(branchId, ...)` 比对随调用交出的分支）。grep 回读：`grep -n "T-064" android/README.md` → 659、865 两行命中。
- Producer/consumer/sibling/compatibility paths checked: `ChatData.Snapshot`/`ChatCommands` 未改；`ChatSink.renderPosition` 签名加 `branchId` 首参，生产实现（MainActivity）与调用方（ChatPresenter）及测试桩 `ChatTestDoubles.RecordingChatSink` 已同步。
- Superseded phrases/examples reverse-searched: 「创建快照仅用于展示」类措辞反向检索（`grep -rn "仅用于展示\|快照仅" Docs/03-prd-draft/V3.2*.md Docs/05-technical-design/*.md android/README.md`）为空，无过时表述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改 PRD 正文（行为本就是 PRD 要求，属实现纠偏）。核对结论：§6.2.4 规格第 2 条「当前分支的正常 AI 回答只使用本分支对话及出生时的上游快照」与实现后行为一致；异常 2「不得静默丢弃上游背景」与 SnapshotUnreadable → STORAGE 提示路径一致，无需改动。

## Acceptance criteria

- [x] RED：新 JVM 用例先失败，命令与退出码入证据；失败确由缺失行为导致。（`artifacts/evidence/.t064-red.log`，EXITCODE=1，javac 报 13 个错误，全部为 `SnapshotHistoryMerger` 不存在 / `ChatStore.SnapshotUnreadable` 找不到符号——引用尚不存在的类型所致）
- [x] GREEN：同一条命令转零退出码；根分支（无快照）用例证明既有行为逐字节不变。（`artifacts/evidence/.t064-green.log`，EXITCODE=0，57 个用例全过；`rootBranchWithoutSnapshotKeepsRowsOnly` 证明 merge(null, rows) 原样返回）
- [x] fork 无 Key 时返回 KEY_MISSING 类拒绝且不创建分支（既有 PRD 异常 2）。（`forkWithoutKeyIsRefusedBeforeCreation`：KEY_MISSING、createBranchCalls==0、submitCalls==0）
- [x] `MainActivity` 丢弃非当前分支的 render/renderPosition（可用 JVM 单测覆盖判定逻辑或记录文件级证据）。（文件级证据：MainActivity `render` 走 `isTheBranchOnScreen(state.branchId)`，`renderPosition(String branchId, ...)` 走 `isTheBranchOnScreen(branchId)`；MainActivity 为 Activity 无法 JVM 直测，判定逻辑经 ChatSink 签名带出 branchId 使其真实可判）
- [x] 全量 `testDebugUnitTest` 回归绿 + `assembleDebug` 成功。（`.t064-fullregression.log` EXITCODE=0；`.t064-assemble.log` EXITCODE=0）
- [x] 验证缺口如实登记：真实 Android SQLite 未运行（用户豁免），DAO 仅语句级断言。（见 Verification report 的 Gaps）

## TDD execution record

### RED — failing test first
- Test/scenario: 先写快照合并类型用例：`historyOfForkedBranchPrependsSnapshotSources`、`rootBranchWithoutSnapshotKeepsRowsOnly`、`missingSnapshotRowRefusesPlan`、`forkWithoutKeyIsRefusedBeforeCreation`。前两例引用尚不存在的新类型 → 编译期失败；随实现推进转运行期断言。（实际落地：`SnapshotHistoryMergerTest` 6 例——四个命名用例 + `aSummarySourceIsRefusedUntilTheDomainGrowsAReader`（SUMMARY 防御分支）+ `snapshotEntriesAreOrdinaryPlanEntriesSoTheTenRoundCompressionCountsThem`（压缩口径钉死）+ DAO 语句级断言 `theSnapshotReadStandsOnASchemaThatGuaranteesOneIntactRowPerBranch`（SearchDaoStatementTest 模式，断言 context_snapshots 表/唯一索引/两触发器的 DDL 形状）；`ChatWiringTest` 新增 2 例——`forkWithoutKeyIsRefusedBeforeCreation` 与失败通道用例 `sendRefusesWhenTheBirthSnapshotCannotBeTrusted`。既有 3 个 fork 用例补 `key.configured = true` 并留注释说明理由，无断言削弱。）
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.wiring.*"
- Exit code: 1
- Observed failure: `:app:compileDebugUnitTestJavaWithJavac` FAILED，javac 报 13 个错误：`SnapshotHistoryMergerTest` 12 个（程序包 `SnapshotHistoryMerger` 不存在、`SnapshotHistoryMerger.Rows` 找不到符号、两处「方法不会覆盖或实现超类型的方法」）+ `ChatWiringTest` 1 个（`ChatStore.SnapshotUnreadable` 找不到符号）。
- Failure reason validated: 是。全部错误均指向尚不存在的新类型/新失败通道，即本任务要实现的行为缺口，非测试自身笔误。证据：`artifacts/evidence/.t064-red.log`。

### GREEN — minimum implementation
- Implementation: ① 新建 `wiring/SnapshotHistoryMerger.java`（纯 JVM 静态合并：null 快照原样返回本分支行；USER_MESSAGE/STABLE_REVISION 按源逐行解析为普通 `ContextPlan.Entry`；行缺失 → `CONTEXT_SOURCE_MISSING`、SUMMARY 源 → `CONTEXT_SUMMARY_MISSING`）；② `ChatStore` 增 `SnapshotUnreadable` 失败通道并更新 `history()` 契约注释；③ `SqliteChatStore.history()` 先 `ContextSnapshotDao.findByBranchId` + `ContextSnapshot.fromStored`（唯一读回入口）解码，再经合并类型前置快照 entry，最后接本分支行（ordinal 截断只作用于本分支行）；④ `ChatWiring.fork()` 增 `missingKey()` 前置拦截；⑤ `ChatWiring.submit()` 捕获 `SnapshotUnreadable` → STORAGE 拒绝；⑥ `MainActivity.render`/`renderPosition` 增分支一致性守卫（`ChatSink.renderPosition` 签名加 `branchId` 首参，`ChatPresenter` 传参、`ChatTestDoubles` 桩同步）。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.wiring.*"
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL，57 个用例全过（`SnapshotHistoryMergerTest` 6 + `ChatWiringTest` 24 + 既有 wiring 用例）。证据：`artifacts/evidence/.t064-green.log`。（过程记录：GREEN 共三轮——首轮测试侧误把 `DomainError.code` 字段写成 `code()`（3 个编译错误）；第二轮发现对 `ChatWiring.java` 的并行编辑互相覆盖、fork 守卫丢失（同文件并行编辑为 T-052 已登记教训，本轮改为串行编辑后补齐），同轮修正语句级断言中索引 DDL 的空格差异；第三轮同一条命令转绿。）
- Regression evidence: `./gradlew.bat testDebugUnitTest`（全量）→ `artifacts/evidence/.t064-fullregression.log` EXITCODE=0；`./gradlew.bat assembleDebug` → `artifacts/evidence/.t064-assemble.log` EXITCODE=0。

### REFACTOR — optional
- Cleanup: 无需独立重构轮。实现按最小形态一次成型：合并逻辑单独成文件、DAO 只做薄读取、失败通道单一；GREEN 后未再改动生产代码。
- Regression evidence: 不适用（无 REFACTOR 改动；GREEN 与全量回归即最终状态证据）。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（tdd_mode: required）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 合并顺序即快照 sources 顺序；本分支行接在其后，`questionOrdinal` 仍只截断本分支行（快照前置段不受 ordinal 影响——子分支首问 ordinal 从 0 起）。已实现：`SqliteChatStore.history()` 的截断循环只遍历本分支行，快照前置段在该循环之后经合并类型加入。
- `ContextSnapshot.fromStored` 失败时抛出的语义：在 store 层转为可被 `ChatWiring` 捕获的失败通道，最终以 STORAGE notice 呈现。已实现：`ChatStore.SnapshotUnreadable`（unchecked），`ChatWiring.submit()` 捕获 → `ChatCommandResult.refused(STORAGE)`，用例 `sendRefusesWhenTheBirthSnapshotCannotBeTrusted` 钉死。
- 快照前置 entry 是否计入 `historyForRequest` 的「最近十轮」压缩，实现时明确判定并写进测试，防止两处口径漂移。**判定：计入，不特判。**快照前置 entry 是普通 `ContextPlan.Entry`（USER_MESSAGE / STABLE_REVISION），`historyForRequest` 对合并后的单一列表按 `STABLE_REVISION` 计数，快照来源与本分支行同口径；由 `SnapshotHistoryMergerTest.snapshotEntriesAreOrdinaryPlanEntriesSoTheTenRoundCompressionCountsThem` 以类型契约钉死（不引入新 EntryKind、不走 SUMMARY）。SUMMARY 源按防御分支以 `CONTEXT_SUMMARY_MISSING` 拒绝（当前分叉只产生 user/revision 两种 source）。
- 串屏修复的实现说明：`ReadingPosition` 不携带分支身份，`MainActivity.renderPosition` 无法从位置参数本身校验分支，故 `ChatSink.renderPosition` 签名加 `branchId` 首参（唯一生产调用方 `ChatPresenter` 传自身 `branchId`，测试桩 `ChatTestDoubles.RecordingChatSink` 同步）；`MainActivity` 以 `chatBranchId`（与 `chatScreen` 在所有开屏/离屏路径同步赋值/置空，grep 核对 472–474、515–517、558、568、599–601、649–651、694–696 行）比对两处入参，不一致丢弃。

## Verification report

- Verdict: PARTIAL（verifier 独立复核 2026-09-23：代码与 JVM 层全部通过；真实 SQLite 引擎验证被用户豁免，上限 PARTIAL）
- Evidence: verifier 逐项核对——`SqliteChatStore.history`:161–177 确经 `findByBranchId`+`fromStored` 读快照，ordinal 截断循环（:140–143）只作用于本分支行；`ChatWiring.fork`:273 missingKey 拦截在位；`MainActivity`:311/322 `isTheBranchOnScreen` 双守卫；`SnapshotHistoryMergerTest` 四个指定用例断言真实（读源码核对，非空断言）。证据文件 `.t064-red/green/fullregression/assemble.log` 均含命令行与 EXITCODE=，与任务记录一致。verifier 独立重跑全量 testDebugUnitTest EXITCODE=0、assembleDebug EXITCODE=0。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除既有用例；3 个既有 fork 用例仅补 missingKey 前置，断言未削弱（verifier 核对）。
- Gaps: 真实 Android SQLite 验证被用户豁免（2026-09-22 指示「代码测试，不要用真机或者模拟器测试」）；与 `.trae/rules/sql.md` 的真实引擎要求存在已登记偏差。
- Risks: JVM 假实现与 SQLite 真实行为不一致的漏检风险。

## Handoff notes

- Completed: 快照读回与合并（`SnapshotHistoryMerger` + `SqliteChatStore.history` 读回路径，根分支行为不变）；fork 的 Key 前置拦截；`MainActivity` render/renderPosition 跨分支丢弃（`ChatSink.renderPosition` 带分支）；8 个新 JVM 用例 + DAO 语句级断言；RED/GREEN/全量回归/assembleDebug 证据齐备；README 两处最小更新，PRD §6.2.4 核对一致。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 065（fork 锚定与 UI）——消费本片的快照读取路径；`ChatWiring` 已由本片收口，065 可直接开。
- Watch-outs: 与 065 共享 `ChatWiring`，不得并行；065 消费本片的快照读取路径。注意 `ChatSink.renderPosition` 签名已变（首参 branchId），065 若触达对话页 UI 链路需沿用。
