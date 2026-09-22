# Treelearn Harness Task: 派生 AI 产物接线（调用者接缝 + 根/分支标题 + 分支摘要）

id: T-2026-09-22-045
title: 派生 AI 产物接线（调用者接缝 + 根/分支标题 + 分支摘要）
template_version: 2
task_type: implementation
status: in_progress
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
- Documentation files updated: 待实现时填写（两处 README 的「写入侧无调用者」表述必须改为已接线，并说明触发时机）。
- Producer/consumer/sibling/compatibility paths checked: 待实现时填写。
- Superseded phrases/examples reverse-searched: 待实现时填写（反查「DerivedOperation 尚无调用者」「叶子摘要读作空」等既有措辞）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: PRD 不改；T-011 的 Handoff「Next steps」由本任务兑现。

## Acceptance criteria

- [ ] 主题创建成功后 ai_artifacts 出现该主题的 `ROOT_TITLE` 行，且既有首页读取路径取到派生标题（用例断言读取路径的取值）。
- [ ] 分支达成稳定回答后出现 `BRANCH_TITLE` 与 `BRANCH_SUMMARY` 行，且既有导航读取路径拿到非空摘要（用例断言 `NavigationData`/`SqliteNavigationStore` 的读取结果）。
- [ ] 分支标题写回后 `BranchHeading.of` 使用派生标题；未派生时仍回退到首个问题（既有兜底不破）。
- [ ] 派生失败（调用返回空、载荷非法、无 Key、网络异常）时旧 artifact 逐字节不变、主回答的既有可见结果不变、有脱敏遥测。
- [ ] 晚到结果基线不匹配时被丢弃且不写行（沿用既有 CAS 语义，本任务证明真实调用路径上也成立）。
- [ ] 原文扫描证明生产侧存在 `new DerivedOperation` 与 `implements DerivedCaller`（匹配集非空，防止「什么都没接线却打印通过」）。
- [ ] 既有 :app 套件全绿；android-no-kotlin-guard PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: Existing source scan found no production `DerivedOperation` caller.
- Command: `rg -n "new DerivedOperation\\(" android/app/src/main`
- Exit code: 1
- Observed failure: No production caller existed; only test fixtures matched.
- Failure reason validated: The missing caller was the documented task gap.

### GREEN — minimum implementation
- Implementation: Added `BlockingDerivedCaller` and `DerivedService`, wired the real streaming gateway
  into the container, and invoked root-title derivation after a first topic transaction is accepted.
- Command: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 0
- Passing evidence: app JVM suite 358 tests passed; full `check` passed on the retry after a transient
  Gradle cache file-mode failure.
- Regression evidence: Java-only guard PASS; no schema changes.

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: PENDING
- Reason: PENDING
- User approval: PENDING
- Alternative verification: PENDING

## Implementation notes

- 触发时机（AI-autonomous decision，2026-09-22）：
  根标题在建主题成功事务提交之后触发（输入只有根问题，永不变，故 `rootTitle` 的输入摘要就是它的基线）；
  分支标题与分支摘要在该分支达成**稳定回答**之后触发（摘要绑定该回答版本 id，是 revision-bound 的那一类）。
  选择「提交后触发」而非「同步内联」的理由：派生要发网络请求，内联会让建主题的可见结果被网络延迟支配。
- 门面的唯一性：三个调用点都必须经同一个门面进入 `DerivedOperation`；不得在任何调用点各自 new 一次 `DerivedRequest` 的组装逻辑之外再造判定。
- 幂等与去重：同一基线重复触发应当被既有 CAS 写回自然挡住（基线不符即丢弃），不得在本任务里另写一份「是否已派生」的判断。
- 线程：派生在后台线程发起；回写由既有 store 事务完成；屏幕只读结果。

## Verification report

- Verdict: PARTIAL
- Evidence: JVM/build/guard evidence above.
- Changed/deleted assertion rationale and equivalent behavior coverage: No existing derived assertions
  were weakened; the new production caller uses the existing `DerivedOperation` baseline/CAS path.
- Gaps: Branch summaries and recommendation kinds are not yet lifecycle-wired; independent verifier and
  real provider smoke remain pending.
- Risks: Root-title derivation currently runs in the first-submit background operation and can wait for
  the provider timeout when a key is configured; this should move to a queued background job before final
  verification.

## Handoff notes

- Completed: 任务已立案（leader，2026-09-22），缺口与边界已核实，尚未实现。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 由 android-dev 执行 RED→GREEN；完成后交独立 verifier。同一缺口的另两片见 T-046（推荐）与 T-047（上下文压缩），它们依赖本任务建立的调用者接缝。
- Watch-outs: ① 必须让 `:app` 的读取路径用例证明「派生真的写进了同一张表」，只断言 outcome 会漏掉写错 scope 的形态；② 不得为了「让标题立刻变好看」而在 UI 线程同步等派生；③ `contextCompression` 不属于本任务，不要顺手接线。

## Pre-flight 更正（工头，2026-09-22）

接力前按当前工作树复核了本规格的 Facts 与行号引用，逐条结果如下。**施工时一律按符号名/内容定位，不要按行号。**

- **成立、无需改**：`new DerivedOperation(` 在全仓生产源码内**零命中**，唯一命中是 `android/app/src/test/java/com/treelearn/app/artifact/DerivedOperationTest.java:303`；`implements DerivedCaller` 的生产实现同样不存在（唯一命中同文件 `:342` 的测试内部类）。读取侧 `android/app/src/main/java/com/treelearn/app/wiring/HomeWiring.java:169` 确实解析 `ArtifactKind.BRANCH_SUMMARY`（行号本轮实测一致）。`DerivedRequest` 的六个工厂齐备（`DerivedRequest.java:82/89/97/106/115/122`）。`SqliteDerivedStore`（`:28 implements DerivedStore`）与 `BranchHeading.of`（`wiring/BranchHeading.java:29`）均存在；`BranchHeading.of` 有两个生产读者：`HomeWiring.java:154`、`SqliteChatStore.java:236`。
- **行号已漂、必须按内容定位**：规格 Documentation impact record 写的 `android/README.md:491-503`（首页数据路径残留缺口）现为 `:493` 起（该段第 1 条即「按历史派生的首页推荐仍未接线……`DerivedOperation` 尚无调用者」在 `:498`）；写的 `:687-698`（已知缺口）现为 `:758-766`，其中「写入侧 `DerivedOperation` 仍无调用者，所以画布叶子在当前 build 实际读作空」在 `:762-763`。README 另有 `:392`/`:396` 两行描述 `DerivedOperation` 语义，反查时一并看。
- **验收项措辞的可执行性**：验收第 3 条写的 `BranchHeading.of` 存在且返回 `String`（不是同名类型），可直接断言；`HomeWiring.java:154` 是它的生产读者，用例断言读取路径取值即可。
- **接缝已核对**：`AppContainer` 现有暴露点按 `public X y()` 形态排布（`settingsStore()` `:142`、`homeData()` `:151`、`chatData()` `:161`、`navigationData()` `:188`、`readingStore()` `:199`、`background()` `:238` 等），新增派生门面按其既有约定追加即可。

以上为工头预检结论，不改变本任务的 In scope / Out of scope / 验收项；若施工中发现与预检相反的事实，以现场为准并在 Handoff 如实记录。
