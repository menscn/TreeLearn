# Treelearn Harness Task: 派生接线（上下文压缩，超预算前用摘要替代旧轮次）

id: T-2026-09-22-047
title: 派生接线（上下文压缩，超预算前用摘要替代旧轮次）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 260
shared_code_touched: true
depends_on: [T-2026-09-22-derived-caller-and-titles-045.md, T-2026-09-22-quote-into-request-044.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: PRD F15 要求 AI 辅助生成降级；提示词设计第 5 节要求 token 预算先预留系统消息、当前问题与最小回答空间，预算不足时**失败关闭**，且「不删除系统消息或仅删除历史推理」，历史原文或摘要二选一。技术方案第 9.2/10 节把「历史原文或摘要」的裁量交给 `CONTEXT_COMPRESSION` 产物。
- Problem/background: `CONTEXT_COMPRESSION` 与其它派生 kind 同属无生产调用者（见 T-045 核实）。当前 `ChatWiring.submit` 把 `store.history(branchId, questionOrdinal)` 的全部条目原样交给组装器，组装器在 `reserveFor` 里以 `HISTORY_EXCEEDS_BUDGET` 失败关闭。也就是说：今天一条足够长的对话**没有压缩可用**，只会拒绝——这是「部分不可用」的一种形态，而不是安全降级。
- Users/scenarios: 长学习会话里继续追问；学习者期望继续得到回答，而不是被拒绝。
- Expected outcome: 历史接近预算时先产出 `CONTEXT_COMPRESSION`，正文用压缩摘要替代被覆盖的旧轮次；压缩不可用时**保留**今日的失败关闭行为（不得静默截断历史）。
- Core flow and reversibility: 压缩产物是附加行；一旦删除产物即回到「原文超预算就拒绝」的今日行为，可逆。
- Inputs/outputs: 输入是待覆盖的旧轮次（问题 + reasoning + answer）与其字符数、以及绑定版本；输出是 ai_artifacts 的压缩行与 ArtifactOutcome。
- State and exception handling: 输入中任一 turn 缺 reasoning 时在调用前判 `REASONING_OMITTED`；压缩结果不缩小判 `OVER_WINDOW`；两者都不写行、不降级到主回答提示词（均已有实现，本任务不得改写其判定）。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX；压缩**不得**让正文出现第二条 system 消息或改变消息顺序；摘要进入正文后仍受既有 `PromptBuildException` 预算校验。
- AI recommendation and rationale: 触发点放在「组装请求时发现历史超预算」，而不是定时或每轮都压缩——压缩是成本，只在需要时做。首次仍按今日行为失败关闭，并**在同一轮内**尝试产出压缩供下一次使用；是否当轮重试由实现者按「不得让用户等待两次网络往返」的取舍决定并记录。
- User choice and custom additions: 用户 2026-09-22 要求 APK 完整可用，长会话不应只剩拒绝这一条路。
- AI-autonomous decisions: 压缩覆盖窗口的选择（哪些旧轮次被覆盖）与当轮是否重试。
- User-confirmation decisions: 无。
- Acceptance evidence: RED→GREEN 的 JVM 用例（超预算时产出压缩并写回、压缩不可用时仍失败关闭且不截断、含摘要的正文不含第二条 system 消息、缺 reasoning 拒绝、未缩小拒绝）、`:app` 与 `:ai` 回归、`android-no-kotlin-guard` PASS。
- Facts: `DerivedOperation` 的生产调用者为零（全仓 `new DerivedOperation` 只命中 `android/app/src/test/java/com/treelearn/app/artifact/DerivedOperationTest.java:303`）；`implements DerivedCaller` 只命中同文件 `:342` 的测试内部类；`DerivedRequest` 的工厂已就绪（rootTitle / branchTitle / branchSummary / answerRecommendations / homeRecommendations / contextCompression），其 `assemble()` 把信封作为**唯一** user 消息且固定 `thinkingEnabled=false`，派生永不触碰教学提示词；`SqliteDerivedStore`（T-011）已实现；读取侧已接上（`HomeWiring.java:169` 解析 BRANCH_SUMMARY、`TreeCanvasWiring` 读摘要、`BranchHeading.of(derivedTitle, firstQuestion)` 已有兜底）；`android/README.md:496` 与 `:692-694` 已把该缺口声明为「写入侧 DerivedOperation 无调用者」。
- Assumptions: T-032 已把数据库与组合根接好；T-006 的 SSE 网关与 T-008 的协调器可复用；每个 `ArtifactKind` 的 `PromptPurpose` 已在注册表里。
- Unknowns: 真实 DeepSeek 的派生输出是否稳定落在信封内（无 Key，只能靠 codec 拒绝非法输出这条既有保证）。
- Deferred: 真实 DeepSeek 联网冒烟（无 Key）；设备侧 SQLite 行级证据（T-024 的设备套件承担，本轮不重复）。
- Non-goals: 不改 `DerivedOperation` / `DerivedRequest` / `ArtifactPayloadCodec` 的既有判定；不做屏幕与布局；不新增 schema。

## Goal

长对话在历史超出预算前用 `CONTEXT_COMPRESSION` 摘要替代被覆盖的旧轮次，使继续追问成为可能；压缩不可用时保持今日的失败关闭，绝不静默截断或丢弃历史。

## Scope

### In scope
- 在 T-045 的调用者接缝上接线 `CONTEXT_COMPRESSION`：由待覆盖轮次构造请求并写回。
- 在对话请求组装路径上，当历史超预算时使用已存在的压缩产物替代被覆盖的旧轮次（复用 `ContextPlan` 既有的 `SUMMARY` 条目种类，不新增 EntryKind）。
- 压缩不可用时保持既有失败关闭（`HISTORY_EXCEEDS_BUDGET`），并保证消息顺序与「恰好一条 system」不变。
- JVM 用例覆盖上述判据。

### Out of scope
- `DerivedOperation` 的压缩判定本身（`REASONING_OMITTED` / `OVER_WINDOW` 已实现且有测试）。
- 标题与摘要（T-045）、推荐（T-046）。
- 引用进入请求（T-044，本任务依赖它以免两处同时改 `ChatWiring.submit`）。
- 任何 schema 改动；`PromptBudget` 的容量数值（T-032 的决定）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 由「历史超预算即拒绝」变为「先压缩、再以摘要继续」；**保留**失败关闭这一兜底（压缩不可用时仍是拒绝而不是截断）、保留「历史永不静默丢弃或摘要化」这条既有声明在**未压缩**路径上的成立。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatWiring.submit` 的 `Assembler`（`:235-238`）是本任务改动的消费点；`SqliteChatStore.history`（`:109-131`）是历史来源；`DeepSeekRequestAssembler.reserveFor`（`:93-110`）是失败关闭判定点，本任务不得改其判定；无平台分支。
- Native bridge or independent-client compatibility/update requirements: 无桥接；压缩请求沿用提示词设计 6.4 既有结构。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 长对话的正文可含摘要条目；压缩不再是「无调用者的机制」。
- Authoritative code/config/migration evidence: 本任务新增的触发与替换点、`ContextPlan.Entry.summary` 既有工厂、`DeepSeekContextEncoder` 对 SUMMARY 的既有权分支。
- Documentation surfaces checked: 提示词设计第 5 节（请求组装顺序与预算）、第 6.4 节（上下文压缩）、技术方案第 9.2/10 节、`android/README.md` 的请求预算与已知缺口两处。
- Documentation files updated: 2026-09-24 收口——`android/README.md` 的「DerivedOperation 无调用者」表述已随 T-045/T-046 接线改写；提示词设计第 5 节措辞修正为「判定唯一、归一化不唯一」（见 T-044 F2 记录）；新增用户可见文案 `notice_history_compressed`（strings.xml）。
- Producer/consumer/sibling/compatibility paths checked: `ChatWiring.submit` → `historyForRequest`（压缩触发与替换，唯一判定点）→ `RefusalNotices.of(refusal, promptReason, compressionApplied)`（拒绝→文案映射唯一判定点）→ `NoticeText` switch（fail-loud 默认分支）→ 首页/对话页共用。
- Superseded phrases/examples reverse-searched: 「历史永不静默截断或摘要化」措辞已限定到**未压缩**路径；压缩路径的替换行为由本任务验收项 1-3 显式覆盖。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: PRD 不改；提示词设计第 5 节的「原文或摘要二选一」由此获得实现。

## Acceptance criteria

- [x] 历史超预算时产出 `CONTEXT_COMPRESSION` 并写回 ai_artifacts；正文中被覆盖的旧轮次以 `SUMMARY` 条目出现（用例断言 `ContextPlan` 的条目种类序列）。〔GREEN 记录 + `DerivedRequestTest` 压缩信封断言〕
- [x] 压缩产物存在后，同长度对话的组装不再以 `HISTORY_EXCEEDS_BUDGET` 失败（用例：压缩前拒绝、压缩后通过，同一输入）。〔GREEN 记录〕
- [x] 压缩不可用（输入缺 reasoning、结果未缩小、调用返回空）时**仍失败关闭**，且历史条目一个都没被丢弃或截断（逐条断言）。〔GREEN 记录；压缩拒绝路径保留全量历史〕
- [x] 含摘要的正文仍恰好一条 system 且在位置 0；压缩不改变消息角色序列（用例）。〔GREEN 记录〕
- [x] 既有的 §5 不变式在**未压缩**路径上逐字未变（同输入同输出的既有断言不改语义）。〔`P3-B4-gates-2026-09-24.txt` 全量 check EXITCODE=0，既有套件未改〕
- [x] 既有 :app 与 :ai 套件全绿；android-no-kotlin-guard PASS。〔`P3-B4-gates-2026-09-24.txt`；guard PASS〕
- [x] （2026-09-22 工头裁定新增）`promptReason` 获得唯一消费者：`HISTORY_EXCEEDS_BUDGET` + 压缩成功 → 新文案 `notice_history_compressed`「这段历史已压缩，再发一次即可继续…」；压缩未产出 → 保留失败关闭文案。〔`RefusalNotices.of` 新重载为唯一判定点；`ChatPromptReasonNoticeTest` 5 例 RED→GREEN，含首页/对话页跨入口一致性断言；证据 `T-047-fix-red-2026-09-24.txt`（5 编译错误）、`T-047-fix-green-2026-09-24.txt`（BUILD SUCCESSFUL）〕

## TDD execution record

### RED — failing test first
- Test/scenario: `ChatWiring.submit` previously passed the entire `store.history(...)` list directly to
  the request assembler; no compression-plan factory existed.
- Command: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 1
- Observed failure: No production history cap or compression-plan caller existed.
- Failure reason validated: The scan matched the documented T-047 gap.

### GREEN — minimum implementation
- Implementation: Added `contextCompressionPlan`, `DerivedService.compressHistory`, and a
  `ChatWiring.historyForRequest` policy that keeps the latest ten complete stable-answer rounds and
  replaces older entries with one `SUMMARY` artifact. Compression refusal preserves the old full-history
  failure behavior; it never silently truncates.
- Command: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 0
- Passing evidence: App JVM suite passed; `DerivedRequestTest` now asserts the compression envelope and
  reasoning coverage.
- Regression evidence: Full `check` passed on the post-change build; Java-only guard remained PASS.

### REFACTOR — optional
- Cleanup: 2026-09-24 实施工头裁定项时把 `ChatWiring.historyForRequest` 的返回值从裸 `ContextPlan`
  重构为私有静态类 `RequestHistory { ContextPlan plan; boolean compressionApplied; }`，
  使「压缩是否成功」与「压缩计划」从同一判定点一并产出，供 `RefusalNotices.of` 新重载消费；
  `submit` 只读结果，不再重复判定。
- Regression evidence: `artifacts/evidence/T-047-fix-green-2026-09-24.txt`（:app:testDebugUnitTest BUILD SUCCESSFUL）；
  全量 `check` 见 `artifacts/evidence/P3-B4-gates-2026-09-24.txt`（EXITCODE=0）。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（未超行数预算，未跳过 RED）。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 必须复用 `ContextPlan.Entry.summary`，不得新增 EntryKind：摘要本来就是 `ContextPlan` 的第三种条目，领域层无需为压缩新增概念。
- 唯一判定点：「哪些旧轮次被覆盖」只能有一处判定；正文构造与压缩请求构造必须读同一个判定结果，否则会出现「压缩覆盖了 A 段、正文替换了 B 段」的漂移。
- 失败方向永远是保留旧行为（拒绝），不得为了让请求通过而截断历史——截断会让学习者以为模型看过没看过的东西。
- 与 T-044 的顺序：本任务改 `submit` 的 Assembler，T-044 也改它；两者必须串行，故 `depends_on` 里含 T-044。

## Verification report

- Verdict: PASS
- Evidence: TDD 记录（RED 失败、GREEN 通过）、工头裁定项 RED→GREEN
  （`artifacts/evidence/T-047-fix-red-2026-09-24.txt`：5 编译错误，缺失 `HISTORY_COMPRESSED` 与新重载；
  `artifacts/evidence/T-047-fix-green-2026-09-24.txt`：BUILD SUCCESSFUL）；
  全量 `check` `artifacts/evidence/P3-B4-gates-2026-09-24.txt`（EXITCODE=0）；Java-only guard PASS。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未削弱任何既有组装拒绝断言；
  新路径为附加行为，仅在压缩写回成功后替换较旧历史；`RequestHistory` 重构未改判定，跨入口一致性有断言。
- Gaps: 无真实 provider Key，端到端联网压缩请求未执行（既有 Deferred 项）；设备侧行级证据归 T-024 设备套件。
- Risks: 压缩仍从后台 submit 路径同步执行，可能等待 provider 超时；正确性可接受，发布前宜迁移到显式队列 Worker。

### 复核记录（独立 verifier #3，2026-09-24）

- 复核范围：工头裁定项（promptReason 消费者）落地质量 + 全量门禁。
- 判定点唯一性：拒绝→文案映射收敛在 `RefusalNotices.of(refusal, promptReason, compressionApplied)` 单一重载内；
  `ChatWiring` 不再各自判定；`NoticeText` switch 默认分支 fail-loud。
- 跨入口一致性：`ChatPromptReasonNoticeTest` 含首页/对话页对同一输入同 notice 的断言（5 例全过）。
- 失败关闭保留：`HISTORY_EXCEEDS_BUDGET` 且压缩未产出 → 仍 `REQUEST_REJECTED`（既有文案），有专例覆盖。
- 门禁：`P3-B4-gates-2026-09-24.txt` 全量 check EXITCODE=0；`P3-B3-no-kotlin-2026-09-24.txt` guard PASS。
- 结论：验收项全勾，裁定项 1-4 全部满足，无新增缺口 → verified。

## Handoff notes

- Completed: 压缩接线（historyForRequest 策略 + DerivedService.compressHistory + 写回）与
  工头裁定项（promptReason 消费者：HISTORY_COMPRESSED 文案）均已实现并测试，2026-09-24 verified。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 发布前可将压缩执行迁出 submit 主路径（见 Risks）；设备侧行级证据由 T-024 设备套件承担。
- Watch-outs: ① 「压缩不可用」永远回退到拒绝而非截断——改这块时先读 `ChatPromptReasonNoticeTest` 的三条失败关闭断言；② 拒绝→文案映射只有 `RefusalNotices.of` 一处，不要在屏幕层再加分支。

## Pre-flight 更正与工头裁定（2026-09-22）

接力前按当前工作树复核了本规格的行号与机制，并新增一条验收要求。**施工时一律按符号名/内容定位，不要按行号。**

- **机制成立**：预算硬失败点在 `android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekRequestAssembler.java` 的 `reserveFor`（`RESERVE_EXCEEDS_BUDGET` 在 `:97`、`HISTORY_EXCEEDS_BUDGET` 在 `:105`，异常消息自带「history is never silently dropped, truncated or summarised here」）；`PromptBuildException.Reason` 只有这两个值（`PromptBuildException.java:42/:44`）。生产调用者缺口与 T-045 同源（`new DerivedOperation` 仅测试命中）。
- **行号已漂、必须按内容定位**：规格 Facts 写的 `android/README.md:496` 现为 `:498`（那条「`DerivedOperation` 尚无调用者」）；`:692-694` 现为 `:762-763`（「写入侧 `DerivedOperation` 仍无调用者，所以画布叶子在当前 build 实际读作空」）。另 `:664` 是压缩产物自身受字符预算约束的那段，改文档时要一并核对。

### 裁定：拒绝理由字段必须获得消费者（本任务新增验收项）

**事实**：`GenerationCoordinator.SubmitOutcome` 有字段 `promptReason`（类型 `PromptBuildException.Reason`，`GenerationCoordinator.java:101`），其唯一写入点是 `:108`／生产构造点 `:184`（`SubmitOutcome.refused(Refusal.PROMPT_BUILD, refused.reason)`）；而**全仓生产源码里读取点为零**——`RefusalNotices.of(Refusal)` 只按 `Refusal` 分类，完全丢弃了 `promptReason`。这是本仓失败案例库记过的「有生产者、无消费者」形态（同类的 `DomainErrorCode.BRANCH_SOURCE_INVALID` 等曾连中三次）：字段携带的信息没有任何界面分支会用到，读代码的人会以为「超预算」与「预留超预算」在界面上被区分了，实际两条合并成一个泛化文案 `notice_request_rejected`「请求被拒绝了，请检查模型与内容后重试。」（`res/values/strings.xml:83`）。

**要求**：本任务必须给 `promptReason` 一个真实消费者，让「历史超预算」这条拒绝在界面上**可操作**，因为本任务落地后重试真的会成功：

1. 当拒绝理由是 `HISTORY_EXCEEDS_BUDGET` **且**同一轮内的压缩产出成功（写回 `APPLIED`）时，用户看到的必须是**指向下一步动作**的一条文案（例如「这段历史已压缩，再发一次即可继续」），**不得**继续复用「请检查模型与内容后重试」——那句话在这里是错的建议，用户照做也解决不了。
2. 当压缩未产出（无 Key／网络失败／载荷非法／未缩小）时，保留今日的失败关闭文案，不得让界面暗示「重试会好」。
3. **判定只允许一处**：拒绝 → 文案的映射收敛在既有唯一判定点 `android/app/src/main/java/com/treelearn/app/wiring/RefusalNotices.java`（及其输入）里做，**不得**在屏幕、presenter 或各调用点各自再判一次；新增 notice 类别时同步 `android/app/src/main/java/com/treelearn/app/screen/NoticeText.java` 的 switch（该类的默认分支会 fail loudly，漏加会立刻炸而不是静默变错）。
4. **跨入口一致性**：首页与对话页由同一个 `RefusalNotices` 供文案，必须有一条断言证明两个入口对同一输入给出同一个 notice（本仓的跨入口漂移教训）。

**代价与边界**：这条要求不改变「首次仍失败关闭」的既有取舍（压缩是要花一次网络往返的成本，不在用户等待的主路径里做），也不改 `DerivedOperation` / `PromptBuildException` 的既有判定；它只要求把已经拿在手里的拒绝理由真的用起来。若实现者认为某一子情形无法区分，可在 Handoff 说明并给出替代方案，但不得留下一处「拿得到却不用」的理由字段。
