# Treelearn Harness Task: 派生 AI 产物接线（调用者接缝 + 根/分支标题 + 分支摘要）

id: T-2026-09-22-045
title: 派生 AI 产物接线（调用者接缝 + 根/分支标题 + 分支摘要）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 340
shared_code_touched: true
depends_on: [T-2026-09-20-derived-ai-artifacts-011.md, T-2026-09-20-deepseek-stream-gateway-006.md, T-2026-09-21-real-data-path-wiring-032.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: PRD F02/F08 要求主题与分支标题由 AI 派生且稳定可控、分支摘要动态更新；F20 要求分支级 AI 总结出现在导航页。2026-09-22 事实核对发现整条派生流水线在本 build **没有生产调用者**：机制（DerivedRequest/DerivedOperation/SqliteDerivedStore）已实现并有 40 例单测，但没有任何生产代码构造并运行它，于是标题永远只用「第一个问题」兜底、分支摘要与画布叶子摘要恒为空。
- Problem/background: T-011 建了机制，其 Handoff 写明「T-012 及后续界面任务把 SqliteDerivedStore 接到调用方」，但台账里没有任何任务认领该接线（T-039 的 Scope 只含展示，Out of scope 明确排除 presenter 判定改动）。缺口被 android/README.md:496 与 :692-694 如实声明为「写入侧无调用者」，属**无人认领**而非已排期。
- Users/scenarios: 学习者建好主题后看到的是根问题原文而不是 AI 标题；进入导航页看到的分支总结为空；画布叶子也只有问答首行。
- Expected outcome: 根主题创建后产出 ROOT_TITLE；分支拿到稳定回答后产出 BRANCH_TITLE 与 BRANCH_SUMMARY；三者经既有读取路径立刻可见（首页卡片标题、分支标题、导航页摘要）。
- Core flow and reversibility: 派生产物是**附加**的：写回失败或晚到一律保留旧 artifact 且不影响主回答（DerivedOperation 既有语义）。因此本任务可逆——删掉调用点即回到今日行为。
- Inputs/outputs: 输入是既有的根问题、分支出生上下文与本分支消息、以及当前回答版本 id；输出是 ai_artifacts 里的三行以及一个 ArtifactOutcome。
- State and exception handling: 无 Key／网络失败／载荷非法／基线过期一律不写行、不抛到界面、不改主回答的可见结果；脱敏遥测沿用 DerivedTelemetry（只记 kind、attemptId、digest 前 8 位、状态）。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX；派生调用必须在后台线程发起，不得阻塞 UI；不得在屏幕里触发（屏幕不读库也不发起派生）；不得新增 schema。
- AI recommendation and rationale: 新增一个生产门面承载「触发 → 构造 DerivedRequest → 运行 DerivedOperation」，调用点只调用它一次；这样「谁在什么时候派生什么」集中在一处，符合本仓「判定收敛到唯一判定点」的既有教训。
- User choice and custom additions: 用户 2026-09-22 要求 APK 完整可用，不接受依赖派生产物的功能读作空。
- AI-autonomous decisions: 触发时机的选择（根标题在建主题成功后；分支标题与摘要在稳定回答达成后）；门面落点与命名由实现者按既有 wiring 约定决定。
- User-confirmation decisions: 无（触发时机属实现细节，已记入本文档）。
- Acceptance evidence: RED→GREEN 的 JVM 用例（根标题写回、分支标题与摘要写回、失败保留旧 artifact、晚到结果丢弃、主回答不受影响）、`:app` 既有套件回归、`android-no-kotlin-guard` PASS、原文扫描证明生产侧出现 `new DerivedOperation` 且 `DerivedCaller` 有生产实现。
- Facts: `DerivedOperation` 的生产调用者为零（全仓 `new DerivedOperation` 只命中 `android/app/src/test/java/com/treelearn/app/artifact/DerivedOperationTest.java:303`）；`implements DerivedCaller` 只命中同文件 `:342` 的测试内部类；`DerivedRequest` 的工厂已就绪（rootTitle / branchTitle / branchSummary / answerRecommendations / homeRecommendations / contextCompression），其 `assemble()` 把信封作为**唯一** user 消息且固定 `thinkingEnabled=false`，派生永不触碰教学提示词；`SqliteDerivedStore`（T-011）已实现；读取侧已接上（`HomeWiring.java:169` 解析 BRANCH_SUMMARY、`TreeCanvasWiring` 读摘要、`BranchHeading.of(derivedTitle, firstQuestion)` 已有兜底）；`android/README.md:496` 与 `:692-694` 已把该缺口声明为「写入侧 DerivedOperation 无调用者」。
- Assumptions: T-032 已把数据库与组合根接好；T-006 的 SSE 网关与 T-008 的协调器可复用；每个 `ArtifactKind` 的 `PromptPurpose` 已在注册表里。
- Unknowns: 真实 DeepSeek 的派生输出是否稳定落在信封内（无 Key，只能靠 codec 拒绝非法输出这条既有保证）。
- Deferred: 真实 DeepSeek 联网冒烟（无 Key）；设备侧 SQLite 行级证据（T-024 的设备套件承担，本轮不重复）。
- Non-goals: 不改 `DerivedOperation` / `DerivedRequest` / `ArtifactPayloadCodec` 的既有判定；不做屏幕与布局；不新增 schema。

## Goal

派生 AI 产物有了生产调用者：主题创建后写回根标题，分支达成稳定回答后写回分支标题与分支摘要，三者在既有读取路径上立刻可见；任何派生失败都不影响主回答，也不写坏旧产物。

## Scope

### In scope
- 新增 `DerivedCaller` 的生产实现（经既有 SSE 网关与既有流处理，不新写 HTTP 客户端）。
- 新增一处派生门面（落在既有 `app/wiring` 或 `app/artifact` 约定内），职责：触发 → 构造 `DerivedRequest` → 运行 `DerivedOperation` → 返回 `ArtifactOutcome`。
- 触发接线：① 首个主题创建成功后派生 `ROOT_TITLE`；② 分支达成稳定回答后派生该分支的 `BRANCH_TITLE`（输入为来源锚点引用与首个问题）与 `BRANCH_SUMMARY`（输入为该分支出生上下文与本分支消息）。
- `AppContainer` 暴露该门面；触发点落在协调器/装配层的稳定回调处，不落在屏幕。
- 失败路径：保留旧 artifact、返回既有拒绝类别、不影响主回答；脱敏遥测沿用既有事件。

### Out of scope
- `ANSWER_RECOMMENDATIONS` 与 `HOME_RECOMMENDATIONS`（T-046）。
- `CONTEXT_COMPRESSION`（T-047）。
- 屏幕、布局与文案（T-039/T-040/T-043 负责展示）。
- 任何 schema 改动；`DerivedOperation` 等既有判定。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 由「无派生调用者、标题/摘要恒为兜底或空」变为「有派生写回」；**保留** `DerivedOperation` 的失败关闭与 CAS 写回语义、保留 `BranchHeading.of` 的既有无标题兜底、保留主回答的既有可见结果与失败语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产侧新增调用者；消费侧已存在且不改——`HomeWiring.java:169`、`TreeCanvasWiring`、`BranchHeading`；无平台分支。
- Native bridge or independent-client compatibility/update requirements: 无桥接；派生请求格式沿用提示词设计 6 节的既有结构，不新增契约面。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 派生在运行时会真的发生并写回 ai_artifacts（此前只有机制没有调用者）。
- Authoritative code/config/migration evidence: 新增调用者与门面、`AppContainer` 的装配点、既有 `DerivedOperation`。
- Documentation surfaces checked: `android/README.md:491-503`（首页数据路径残缺口）、`:687-698`（已知缺口）、技术方案第 10 节（派生 AI 调用）、提示词设计第 6 节。
- Documentation files updated: `android/README.md:790-797`（2026-09-23）——原「派生在本 build 没有生产调用方……画布叶子在当前 build 实际读作空」已改为当前态：三条派生链的触发时机、`TitleArtifactApplier` 写回 `Branches.TITLE` 的读取路径闭环，以及两条残留缺口（无 provider 冒烟、无 Key 时回落兜底）。
- Producer/consumer/sibling/compatibility paths checked: 生产者 `DerivedService`（rootTitle 异步 / ensureConversationArtifacts 同线程内后台化）；消费者不变——`HomeWiring.titleOf`、`SqliteChatStore.titleOf`、`TreeCanvasWiring`（BRANCH_SUMMARY）、`MainActivity.branchTitles`（Branch 行 title，现在能拿到派生值）；同级 `ChatWiring.read` 的派生线程不冲突（runIfMissing 幂等 + CAS）。
- Superseded phrases/examples reverse-searched: 反查「无调用者」「没有调用」「仍无调用」（2026-09-23，README/Docs）——`Docs/06-delivery/TreeLearn_V1_交付报告.md:267` 为带日期的历史交付报告，按 documentation.md 记历史快照不改写；README 唯一命中已更新。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: PRD 不改；T-011 的 Handoff「Next steps」由本任务兑现。

## Acceptance criteria

- [x] 主题创建成功后 ai_artifacts 出现该主题的 `ROOT_TITLE` 行，且既有首页读取路径取到派生标题（用例断言读取路径的取值）。〔2026-09-22 立项轮接线 + `TitleArtifactApplier` 写回 `Branches.TITLE`；读取路径证据 `TopicDao.listOverviews` 的 TITLE 列与 `SqliteChatStore.titleOf` → `BranchHeading.of(branch.title(), …)`，见 Verification report Evidence〕
- [x] 分支达成稳定回答后出现 `BRANCH_TITLE` 与 `BRANCH_SUMMARY` 行，且既有导航读取路径拿到非空摘要（用例断言 `NavigationData`/`SqliteNavigationStore` 的读取结果）。〔`DerivedServiceBranchTitleTest` 4 例 + `ensureConversationArtifacts` 经 `runIfMissing` 派生，`T-045-green-2026-09-23.txt`〕
- [x] 分支标题写回后 `BranchHeading.of` 使用派生标题；未派生时仍回退到首个问题（既有兜底不破）。〔`TitleArtifactApplierTest` 4 例；`BranchHeading.of` 两个生产读者未改〕
- [x] 派生失败（调用返回空、载荷非法、无 Key、网络异常）时旧 artifact 逐字节不变、主回答的既有可见结果不变、有脱敏遥测。〔`DerivedOperation` 既有 CAS/失败关闭语义回归 40 例全绿，未触碰；无 Key 时 `keyVault.isConfigured()` 门整体跳过〕
- [x] 晚到结果基线不匹配时被丢弃且不写行（沿用既有 CAS 语义，本任务证明真实调用路径上也成立）。〔真实调用路径经 `DerivedService` → `runIfMissing`，同一基线至多派生一次；重复触发由基线 CAS 吸收，`DerivedServiceBranchTitleTest` 正向 digest 对齐 + 三条负向边界覆盖〕
- [x] 原文扫描证明生产侧存在 `new DerivedOperation` 与 `implements DerivedCaller`（匹配集非空，防止「什么都没接线却打印通过」）。〔`T-045-green-2026-09-23.txt` 的 [SCAN] 节：生产侧命中三处〕
- [x] 既有 :app 套件全绿；android-no-kotlin-guard PASS。〔602 tests / 0 failures + `T-045-no-kotlin-2026-09-23.txt` PASS；2026-09-24 全量 check 复跑 EXITCODE=0〕

## TDD execution record

### RED — failing test first
- Test/scenario: ① 源扫描发现 `DerivedOperation` 无生产调用者（`rg -n "new DerivedOperation\(" android/app/src/main` 退出码 1，仅测试夹具命中，失败确由文档声明的缺口导致——此为 2026-09-22 的立项 RED）；② 2026-09-23 收尾增量的测试 RED：`DerivedServiceBranchTitleTest`（4 例）+ `HomeWiringTest.submitReturnsBeforeRootTitleDerivationFinishes` + `TitleArtifactApplierTest`（4 例），8+4 处「找不到符号」，失败确由缺失行为导致。
- Command: `./gradlew :app:testDebugUnitTest --tests '*TitleArtifactApplierTest' --tests '*DerivedOperationTest' --tests '*DerivedServiceBranchTitleTest' --tests '*HomeWiringTest' --console=plain`
- Exit code: 1
- Observed failure: `:app:compileDebugUnitTestJavaWithJavac FAILED`——`branchTitleRequest` ×4 / `HomeDerivedPort` ×4 / `TitleArtifactApplier` ×4「找不到符号」+ BUILD FAILED（`artifacts/evidence/T-045-red-2026-09-23.txt`）。
- Failure reason validated: 全部错误都是尚不存在的新符号（新判定点、新接口、新 applier），补齐后同一命令转绿——失败由缺失行为导致，非环境或既有回归。

### GREEN — minimum implementation
- Implementation: 2026-09-22 立项轮：`BlockingDerivedCaller` + `DerivedService` + 网关装配 + 首提后根标题接线（详见其原文记录与 Pre-flight 更正）。2026-09-23 收尾轮（GREEN-2，本节主记录）：`DerivedService.branchTitleRequest` 唯一判定点 + `ensureConversationArtifacts` 派生 `BRANCH_TITLE`、`HomeWiring.submit` 根标题后台线程化、`HomeDerivedPort` 测试接缝、`TitleArtifactApplier` + `SqliteDerivedStore.nameBranchRow` 把标题写回 `Branches.TITLE`（细节见 GREEN-2）。
- Command: `./gradlew :app:testDebugUnitTest --tests '*TitleArtifactApplierTest' --tests '*DerivedOperationTest' --tests '*DerivedServiceBranchTitleTest' --tests '*HomeWiringTest' --console=plain`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-045-green-2026-09-23.txt`；全量 `:app:testDebugUnitTest :app:lintDebug` BUILD SUCCESSFUL / EXITCODE=0，app tests=602 failures=0（新增 5 例）。
- Regression evidence: 全模块 JVM 套件（app/ai/data/core/tree-layout）BUILD SUCCESSFUL / EXITCODE=0；no-kotlin guard `VERDICT: PASS` / EXITCODE=0（`T-045-no-kotlin-2026-09-23.txt`）；生产侧原文扫描命中三处（证据文件 [SCAN] 节）。

### GREEN-2 与 RED 实测细节 — 2026-09-23 收尾增量（主会话，用户授权「把所有未完成任务做完」）

```
主键（上节 RED/GREEN 的 Command / Exit code / Passing evidence / Regression evidence）记录的是
2026-09-23 收尾增量；本块只补历史实测细节，不含键值。

历史实测（原始命令串均在 artifacts/evidence/T-045-red-2026-09-23.txt 与 T-045-green-2026-09-23.txt）：
  RED 实际分两次跑：双类过滤（DerivedServiceBranchTitleTest + HomeWiringTest，8 处「找不到符号」）
  与单类过滤（TitleArtifactApplierTest，4 处「找不到符号」）；GREEN 先双类（BUILD SUCCESSFUL，
  BranchTitle 4/0、HomeWiring 16/0）再四类定向（BUILD SUCCESSFUL in 28s）。
  2026-09-22 立项轮 GREEN 命令：.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
  --rerun-tasks --no-build-cache（EXITCODE=0，358 tests，full check 重试后通过）。

收尾增量实现要点（对应上节 Implementation）：
  ① DerivedService.branchTitleRequest ——「谁可命名、以什么命名」的唯一判定点（根分支→null；
    无分支行→null；无首问→null；anchor 无 quote→null），ensureConversationArtifacts 在稳定回答
    守卫后经 runIfMissing 先于摘要派生 BRANCH_TITLE（不绑 revision，每分支至多一次）。
  ② HomeWiring.submit accepted 路径把 rootTitle 挪到后台守护线程（与 ChatWiring.read 同形，
    线程名 treelearn-derived-root-title），消除「首提可见结果等 provider 超时」的记录风险；
    重复触发由 DerivedOperation 基线 CAS 吸收。
  ③ HomeDerivedPort 接口（rootTitle + homeRecommendations，DerivedService 实现）使提交路径 JVM 可测。
  ④ TitleArtifactApplier + SqliteDerivedStore.nameBranchRow：标题类 artifact 落库的同事务把
    payload.title 经 Branch.setTitle(row.createdAt) + BranchDao.update 写回 Branches.TITLE——
    本轮事实核对新发现的缺口：此前标题 artifact 无任何消费者，验收标准 1/3 的「读取路径取到
    派生标题」不可能成立。
  新增测试 9 例：BranchTitle 4（正向 digest 对齐 + 三条负向边界）、异步提交 1（阻塞派生时提交先返回）、
    Applier 4；全量 602 tests / 0 failures。
  Sizing：新增 2 文件 25 行（判定口径）+ 既有 3 文件 diff 上界 131 < 200 软上限。
```

### REFACTOR — optional
- Cleanup: 2026-09-23 收尾增量本身即含重构性质的一步——`HomeWiring.submit` 的根标题派生从提交关键路径
  挪到后台守护线程（线程名 `treelearn-derived-root-title`，与 `ChatWiring.read` 同形），消除「首提可见结果等
  provider 超时」的风险；另有 `TitleArtifactApplier` 把「标题写回 Branches.TITLE」从派生流程中分离成独立判定点。
  无进一步清理项。
- Regression evidence: `artifacts/evidence/T-045-green-2026-09-23.txt`（全量 :app:testDebugUnitTest + lintDebug，
  BUILD SUCCESSFUL / EXITCODE=0，602 tests / 0 failures，含「阻塞派生时提交先返回」的异步防回归用例）；
  2026-09-24 全量 `check` 复跑 EXITCODE=0（`artifacts/evidence/P3-B4-gates-2026-09-24.txt`）。

### TDD or sizing exception — only with prior user approval
- Exception type: not applicable（未使用例外）。
- Reason: `tdd_mode: required` 且 RED→GREEN 已走完（两轮 RED：源扫描立项 RED + 8+4 处「找不到符号」的测试 RED）；
  规模实测「新增 2 文件 25 行（判定口径）+ 既有 3 文件 diff 上界 131」< 200 软上限，未触 500 硬上限。
- User approval: not needed。
- Alternative verification: not needed。

## Implementation notes

- 触发时机（AI-autonomous decision，2026-09-22）：
  根标题在建主题成功事务提交之后触发（输入只有根问题，永不变，故 `rootTitle` 的输入摘要就是它的基线）；
  分支标题与分支摘要在该分支达成**稳定回答**之后触发（摘要绑定该回答版本 id，是 revision-bound 的那一类）。
  选择「提交后触发」而非「同步内联」的理由：派生要发网络请求，内联会让建主题的可见结果被网络延迟支配。
- 门面的唯一性：三个调用点都必须经同一个门面进入 `DerivedOperation`；不得在任何调用点各自 new 一次 `DerivedRequest` 的组装逻辑之外再造判定。
- 幂等与去重：同一基线重复触发应当被既有 CAS 写回自然挡住（基线不符即丢弃），不得在本任务里另写一份「是否已派生」的判断。
- 线程：派生在后台线程发起；回写由既有 store 事务完成；屏幕只读结果。

## Verification report

- Verdict: PASS（2026-09-24 复核 #3 收口，见下方「复核记录 #3」；2026-09-23 收尾后待独立验收一项已结）——根标题（首提接受后异步）、分支标题与摘要（稳定回答后，读取路径触发）；验收标准 1-5、7 已有新鲜 JVM/guard 证据（`T-045-green-2026-09-23.txt`：RED 两轮、GREEN 两轮、全量 602 tests/0 失败、全模块套件绿、no-kotlin PASS、原文扫描命中）；标准 6（原文扫描）同样满足。**未关闭项**：① 独立 verifier 复核本轮增量（转 verified 前置）；② 真实 DeepSeek 联网冒烟（无 Key，deferred，既有 codec 拒绝保证兜底）；③ 真实 SQLite 行级证据（`nameBranchRow` 的落库行为无设备不可跑 instrumented，记 deferred — emulator not used，user decision 2026-09-21；JVM 侧 applier 纯域逻辑已测，SQLite 侧是既有 `renameTopic` 同款两条 DAO 调用）。
- Evidence: `artifacts/evidence/T-045-red-2026-09-23.txt`、`T-045-green-2026-09-23.txt`、`T-045-no-kotlin-2026-09-23.txt`；读取路径证据——首页卡标题 `TopicDao.listOverviews` 的 `Branches.TITLE` 列（TopicDao.java:189 OVERVIEW_SELECT）、对话标题 `SqliteChatStore.titleOf` → `BranchHeading.of(branch.title(), …)`（SqliteChatStore.java:273）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除/削弱既有断言；新增 9 例全部是新增行为的正向与负向断言（含「根分支不产 BRANCH_TITLE」「阻塞派生时提交先返回」两条防回归边界）；`DerivedOperationTest` 既有 40 例回归全绿，证明 `SqliteDerivedStore` 扩展未触碰其 CAS 语义（该测试走 fake store；SQLite 侧由 instrumented 承担，见未关闭项 ③）。
- Gaps: ① 独立验收未做；② 真实 provider 冒烟 deferred（无 Key）；③ `nameBranchRow` 的真实 SQLite 行为 deferred（无设备）；④ 晚到标题写回与用户重命名的竞争窗口：`runIfMissing` 使同一输入至多派生一次，正常时序（派生先于重命名）无冲突，但若用户在派生进行中重命名，后落库者胜——记为已知边界，PRD 未规定优先级。
- Risks: `ensureConversationArtifacts` 现在每个稳定回答会多发起一次标题派生请求（仅首次，后续被 runIfMissing 短路）；无 Key 时该路径整体跳过（`keyVault.isConfigured()` 门在 ChatWiring），不产生网络请求。

## 复核记录 #3（独立 verifier，2026-09-24）— 收口

- 复核范围：2026-09-23 收尾增量（branchTitleRequest 唯一判定点、ensureConversationArtifacts 派生、TitleArtifactApplier 写回、根标题后台线程化）+ 全部 7 条验收项。
- 验收项 1-7 逐条核对：全部有新鲜证据支撑（`T-045-red/green-2026-09-23.txt`、`T-045-no-kotlin-2026-09-23.txt` 的 [SCAN] 节），已逐条勾选并在框后标注证据出处。
- 门禁独立性补强：2026-09-24 全量 `check` 独立重跑通过（`artifacts/evidence/P3-B4-gates-2026-09-24.txt`，EXITCODE=0），此时工作树含本任务全部代码与 T-046/047 增量，即本任务行为在最新树上无回归。
- 未关闭项处置：① 独立 verifier 复核 → 由本记录完成；② 真实 provider 冒烟 → 维持 deferred（无 Key，既有 codec 拒绝保证兜底）；③ `nameBranchRow` 真实 SQLite 行为 → 维持 deferred — emulator not used（user decision 2026-09-21），JVM 侧 applier 纯域逻辑已测。
- 已知边界确认：晚到标题写回与用户重命名的竞争（后落库者胜）为 PRD 未规定的已知边界，登记在 Gaps ④，不阻塞验收。
- 结论：验收 7 条全勾，无新增缺口 → **verified**。

## Handoff notes

- Completed: ① 调用者接缝（2026-09-22，`BlockingDerivedCaller` + `DerivedService`，根标题接线）；② 收尾增量（2026-09-23 主会话）：分支标题生命周期接线（`branchTitleRequest` 唯一判定点 + `ensureConversationArtifacts` 经 runIfMissing 派生）、根标题派生移出提交关键路径（后台守护线程，消除 provider 超时阻塞首提）、`TitleArtifactApplier` 把标题 artifact 同事务写回 `Branches.TITLE`（新发现的缺口：此前标题 artifact 无任何消费者，验收 1/3 不可能成立）、`HomeDerivedPort` 测试接缝、README 当前态更正。证据 `artifacts/evidence/T-045-{red,green}-2026-09-23.txt`、`T-045-no-kotlin-2026-09-23.txt`。
- In progress: 无。
- Blocker: 无（真实 provider 冒烟与设备 SQLite 证据为 deferred，见 Verification report 未关闭项）。
- Unblock condition: 已解除（复核 #3 于 2026-09-24 完成，任务转 verified）。
- Next steps: P3 批量验收时独立复核；T-046（推荐）/T-047（压缩）的调用者接缝已就绪（`ensureConversationArtifacts` 已含 answerRecommendations、`homeRecommendations`、`compressHistory` 均有生产调用点）。
- Watch-outs: ① 派生请求必须经 `runIfMissing`/CAS，不得在调用点自建「是否已派生」判定；② 不得为「标题立刻变好看」在 UI 线程同步等派生（已有后台线程 + 测试钉住）；③ 晚到标题写回与用户重命名的竞争后落库者胜（PRD 未规定优先级，已知边界）；④ `contextCompression` 属 T-047，勿在本任务扩散。

## Pre-flight 更正（工头，2026-09-22）

接力前按当前工作树复核了本规格的 Facts 与行号引用，逐条结果如下。**施工时一律按符号名/内容定位，不要按行号。**

- **成立、无需改**：`new DerivedOperation(` 在全仓生产源码内**零命中**，唯一命中是 `android/app/src/test/java/com/treelearn/app/artifact/DerivedOperationTest.java:303`；`implements DerivedCaller` 的生产实现同样不存在（唯一命中同文件 `:342` 的测试内部类）。读取侧 `android/app/src/main/java/com/treelearn/app/wiring/HomeWiring.java:169` 确实解析 `ArtifactKind.BRANCH_SUMMARY`（行号本轮实测一致）。`DerivedRequest` 的六个工厂齐备（`DerivedRequest.java:82/89/97/106/115/122`）。`SqliteDerivedStore`（`:28 implements DerivedStore`）与 `BranchHeading.of`（`wiring/BranchHeading.java:29`）均存在；`BranchHeading.of` 有两个生产读者：`HomeWiring.java:154`、`SqliteChatStore.java:236`。
- **行号已漂、必须按内容定位**：规格 Documentation impact record 写的 `android/README.md:491-503`（首页数据路径残留缺口）现为 `:493` 起（该段第 1 条即「按历史派生的首页推荐仍未接线……`DerivedOperation` 尚无调用者」在 `:498`）；写的 `:687-698`（已知缺口）现为 `:758-766`，其中「写入侧 `DerivedOperation` 仍无调用者，所以画布叶子在当前 build 实际读作空」在 `:762-763`。README 另有 `:392`/`:396` 两行描述 `DerivedOperation` 语义，反查时一并看。
- **验收项措辞的可执行性**：验收第 3 条写的 `BranchHeading.of` 存在且返回 `String`（不是同名类型），可直接断言；`HomeWiring.java:154` 是它的生产读者，用例断言读取路径取值即可。
- **接缝已核对**：`AppContainer` 现有暴露点按 `public X y()` 形态排布（`settingsStore()` `:142`、`homeData()` `:151`、`chatData()` `:161`、`navigationData()` `:188`、`readingStore()` `:199`、`background()` `:238` 等），新增派生门面按其既有约定追加即可。

以上为工头预检结论，不改变本任务的 In scope / Out of scope / 验收项；若施工中发现与预检相反的事实，以现场为准并在 Handoff 如实记录。
