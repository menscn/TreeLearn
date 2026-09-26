# Treelearn Harness Task: 派生 AI 产物接线（回答级与首页级推荐）

id: T-2026-09-22-046
title: 派生 AI 产物接线（回答级与首页级推荐）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 240
shared_code_touched: true
depends_on: [T-2026-09-22-derived-caller-and-titles-045.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: PRD F12 要求推荐问题结合树轮廓识别已探索方向，并支持当前追问或从稳定回答分叉；F17 要求依据已学内容生成推荐主题并在首页弹幕展示。
- Problem/background: 推荐的两个 kind（`ANSWER_RECOMMENDATIONS` 与 `HOME_RECOMMENDATIONS`）与标题/摘要同属无生产调用者的派生流水线（见 T-045 的核实）。当前 `HomeWiring.recommendations(hasTopics)` 的参数被**刻意不用**，走 PRD 6.2.17 自己的兜底，于是「有主题」与「无主题」给出同一条启动集合（`android/README.md:495-498`）；回答级推荐同理恒为静态 starter。
- Users/scenarios: 有学习历史的学习者期望推荐随已学方向变化；从一条稳定回答期望看到贴着该回答的后续问题。
- Expected outcome: 回答达稳定后写回 `ANSWER_RECOMMENDATIONS`；首页按主题树轮廓摘要写回 `HOME_RECOMMENDATIONS`，并且首页读取路径在有主题时取到历史派生集合。
- Core flow and reversibility: 与 T-045 同形——附加产物，失败保留旧值且不影响主回答；删除调用点即回到今日行为。
- Inputs/outputs: 输入是该回答文本与主题树轮廓（`TreeOutline`）、或首页的主题树轮廓与条数上限；输出是 ai_artifacts 行与 ArtifactOutcome。
- State and exception handling: 与 T-045 相同（不写坏旧产物、不影响主回答）；条数上限由调用者给定且必须 ≤ 既有 ceiling 语义。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX；复用 T-045 建立的调用者接缝，不得再建第二个调用者；`HomeWiring.recommendations` 的参数一旦启用，其「无主题仍走启动集合」的既有兜底必须保留。
- AI recommendation and rationale: 回答级在稳定回答处触发（与摘要同一时机，避免多套时机）；首页级在首页读取且轮廓摘要变化时触发，读取路径继续同步返回当前已有集合（先给旧值，派生完成后再刷新），以免首页被网络阻塞。
- User choice and custom additions: 用户 2026-09-22 要求 APK 完整可用，不接受推荐恒为启动集合。
- AI-autonomous decisions: 首页级派生在「读时发现基线过期」触发，而不是定时任务。
- User-confirmation decisions: 无。
- Acceptance evidence: RED→GREEN 的 JVM 用例（回答级写回、首页级写回并按轮廓摘要作基线、有主题时读取路径取到派生集合、无主题时仍走启动兜底、失败保留旧值）、`:app` 回归、`android-no-kotlin-guard` PASS。
- Facts: `DerivedOperation` 的生产调用者为零（全仓 `new DerivedOperation` 只命中 `android/app/src/test/java/com/treelearn/app/artifact/DerivedOperationTest.java:303`）；`implements DerivedCaller` 只命中同文件 `:342` 的测试内部类；`DerivedRequest` 的工厂已就绪（rootTitle / branchTitle / branchSummary / answerRecommendations / homeRecommendations / contextCompression），其 `assemble()` 把信封作为**唯一** user 消息且固定 `thinkingEnabled=false`，派生永不触碰教学提示词；`SqliteDerivedStore`（T-011）已实现；读取侧已接上（`HomeWiring.java:169` 解析 BRANCH_SUMMARY、`TreeCanvasWiring` 读摘要、`BranchHeading.of(derivedTitle, firstQuestion)` 已有兜底）；`android/README.md:496` 与 `:692-694` 已把该缺口声明为「写入侧 DerivedOperation 无调用者」。
- Assumptions: T-032 已把数据库与组合根接好；T-006 的 SSE 网关与 T-008 的协调器可复用；每个 `ArtifactKind` 的 `PromptPurpose` 已在注册表里。
- Unknowns: 真实 DeepSeek 的派生输出是否稳定落在信封内（无 Key，只能靠 codec 拒绝非法输出这条既有保证）。
- Deferred: 真实 DeepSeek 联网冒烟（无 Key）；设备侧 SQLite 行级证据（T-024 的设备套件承担，本轮不重复）。
- Non-goals: 不改 `DerivedOperation` / `DerivedRequest` / `ArtifactPayloadCodec` 的既有判定；不做屏幕与布局；不新增 schema。

## Goal

推荐也由派生产出：回答稳定后写回回答级推荐，首页按主题树轮廓写回推荐主题集，且首页读取路径在有主题时取到历史派生集合、无主题时保留既有启动兜底。

## Scope

### In scope
- 在 T-045 建立的调用者接缝上接线 `ANSWER_RECOMMENDATIONS`：输入为该稳定回答文本与主题树轮廓，条数上限由调用者给定。
- 接线 `HOME_RECOMMENDATIONS`：以主题树轮廓摘要为基线，在首页读取发现基线过期时触发派生。
- 启用 `HomeWiring.recommendations(hasTopics)` 目前被刻意忽略的参数，并**保留**无主题时的启动兜底。
- 失败与晚到路径沿用既有语义；脱敏遥测沿用。

### Out of scope
- 根标题、分支标题、分支摘要（T-045，本任务依赖其接缝）。
- 上下文压缩（T-047）。
- 首页弹幕的视觉与滚动（T-012/T-043 已负责）。
- 任何 schema 改动；`StarterRecommendations` 的既有内容。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 由「推荐恒为启动集合」变为「有历史时用派生集合」；**保留**无主题时的启动兜底（PRD 6.2.17 自身的降级规则）、保留 `StarterRecommendations` 与其既有断言。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费侧 `HomeWiring.recommendations` 与 `HomeViewState` 的推荐字段、`ChatWiring/RecommendationMarquee` 路径；生产侧为本任务新增的触发；无平台分支。
- Native bridge or independent-client compatibility/update requirements: 无桥接；沿用提示词设计 6.3 既有结构。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 推荐可来自历史派生；`HomeWiring.recommendations` 的参数开始生效。
- Authoritative code/config/migration evidence: 本任务新增触发点、T-045 的调用者、既有读取路径。
- Documentation surfaces checked: `android/README.md:491-503`（含「推荐仍未接线」那条残缺口）、技术方案第 10 节、提示词设计 6.3 与 6.4。
- Documentation files updated: 2026-09-23/24 收口——`android/README.md` 的「推荐仍未接线」表述已改写为当前态（T-045 收尾轮一并处理 README 已知缺口段）；`HomeWiring.recommendations` 的 javadoc 于 2026-09-24 更正（原「刻意不用」的过期描述已改为描述 T-046 经 `derived.homeRecommendations` 的接线）。
- Producer/consumer/sibling/compatibility paths checked: 生产者 `HomeWiring.recommendations`（读取时经 `derived.homeRecommendations` 触发，后台派生、同步返回当前集合）与 `DerivedService.ensureConversationArtifacts`（回答级，ChatWiring 读取路径 :150 调用）；消费者 `HomeViewState` 推荐条与 `ChatData.Snapshot.answerRecommendations`；同级 `StarterRecommendations` 兜底保留（`HomeWiring.java:130`）。
- Superseded phrases/examples reverse-searched: 反查「推荐恒为启动集合」「参数被刻意不用」——README 当前态段已更新；`HomeWiring.recommendations` javadoc 于 2026-09-24 更正为已接线描述（grep `homeRecommendations` 命中 HomeWiring.java:119 生产调用点）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: PRD 不改。

## Acceptance criteria

- [x] 回答稳定后 ai_artifacts 出现该回答版本的 `ANSWER_RECOMMENDATIONS` 行，且条数不超过调用者给定的上限。〔操作层：`DerivedOperationTest`（answer/home 推荐 kind 的运行与写回）；上限：`ArtifactSchemaTest.aRecommendationArrayMustNotExceedTheDeclaredCeiling`（:166）+ 载荷形状 :150-162/:187；触发点：`DerivedService.ensureConversationArtifacts`（:89 构造请求）经 `ChatWiring.read`（:150）调用，接线级空态断言 `ChatWiringTest:98-99`〕
- [x] 首页在有主题时取到历史派生推荐；**无主题时仍是启动集合**（两态各一条断言，防止只是把参数接上而破坏兜底）。〔2026-09-24 复核 #3 补齐：`HomeWiringTest.recommendationsComeFromHistoryWhenTheDerivedSetIsNonEmpty`（fromHistory=true + FOLLOW_UP 条目）与 `recommendationsFallBackToStarterWhenTheDerivedSetComesBackEmpty`（派生空 → 回退 4 条启动集）；既有两条负态 `:282`（无主题）与 `:290`（有主题未派生）保留。证据 `artifacts/evidence/T-046-verify3-2026-09-24.txt` EXITCODE=0〕
- [x] 首页级派生的基线是主题树轮廓摘要：轮廓变化后旧产物被判过期并重新派生（用例覆盖）。〔`DerivedOperationTest:100-114`：`outline("旧主题")` 写回后以 `outline("新主题")` 触发 → 基线不匹配 → 重派生；首页触发经 `HomeWiring:119` 以 `TreeOutline.Entry.topic(title, summaryOf(row))` 构造轮廓〕
- [x] 派生失败或返回空时保留旧值，首页仍能渲染（不得出现空推荐条）。〔`Recommendations` 构造器拒绝空列表（`HomeData.java:66-69`）；派生空/失败路径回退 `starterFallback`（`HomeWiring.java:130`）+ 本轮新增空回退用例；presenter 层失败隐藏卡片有 `HomePresenterTest.aFailedRecommendationDropsTheStripAndLeavesThePageUsable`〕
- [x] 既有 :app 套件全绿（含 `StarterRecommendations` 与首页推荐既有断言）；android-no-kotlin-guard PASS。〔GREEN 记录套件通过 + 2026-09-24 全量 check 复跑 EXITCODE=0（`P3-B4-gates-2026-09-24.txt`）+ `P3-B3-no-kotlin-2026-09-24.txt` guard PASS〕

## TDD execution record

### RED — failing test first
- Test/scenario: `HomeWiring.recommendations(hasTopics)` always returned `StarterRecommendations`, and
  `ChatData.Snapshot` always returned an empty answer recommendation list.
- Command: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 1
- Observed failure: No production recommendation lifecycle caller existed.
- Failure reason validated: The scan matched the task's documented missing producer/consumer gap.

### GREEN — minimum implementation
- Implementation: Reused `DerivedService` for answer recommendations on conversation reads and added
  topic-outline `HOME_RECOMMENDATIONS` generation in `HomeWiring`; failures still fall back to starter
  recommendations.
- Command: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 0
- Passing evidence: App JVM suite passed after the recommendation wiring changes.
- Regression evidence: Full check and Java-only guard passed on the preceding build; no schema changes.

### REFACTOR — optional
- Cleanup: 无独立重构轮。实现复用 T-045 的 `DerivedService` 门面（未新增第二个 `DerivedCaller`），
  `HomeWiring.recommendations` 的参数启用与派生读取在同一段内收敛（`HomeWiring.java:111-131`），
  无可去重的重复判定。2026-09-24 复核 #3 补 2 例接线级用例（fromHistory 正/负态）封堵覆盖缺口，
  详见「复核记录 #3」。
- Regression evidence: `artifacts/evidence/T-046-verify3-2026-09-24.txt`（定向 HomeWiringTest
  BUILD SUCCESSFUL / EXITCODE=0）；2026-09-24 全量 `check` 复跑 EXITCODE=0
  （`artifacts/evidence/P3-B4-gates-2026-09-24.txt`）。

### TDD or sizing exception — only with prior user approval
- Exception type: not applicable（未使用例外）。
- Reason: `tdd_mode: required` 且 RED→GREEN 已走完（RED：无生产推荐生命周期调用者；GREEN：接线后套件通过）。
  行数远低于 200 软上限（推荐接线为既有文件内的增量改动，无大块新增）。
- User approval: not needed。
- Alternative verification: not needed。

## Implementation notes

- 必须复用 T-045 的调用者接缝：本任务不得新增第二个 `DerivedCaller` 实现。
- 首页路径的取舍：读取永远同步返回当前可用集合（先旧后新），派生在后台进行；不得让首页首帧等网络。
- 条数上限：`answerRecommendations` 与 `homeRecommendations` 都要求显式 maxItems，本任务把它作为调用者的决定记录到任务文件里（不要散落在多处硬编码）。

## Verification report

- Verdict: PASS（2026-09-24 复核 #3 收口，见下方「复核记录 #3」）
- Evidence: JVM/build/guard evidence above；复核 #3 补强证据 `artifacts/evidence/T-046-verify3-2026-09-24.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: Starter fallback remains the
  no-topic and failed-provider path; derived results are accepted only after strict codec validation.
- Gaps: No provider Key was available for a real recommendation payload; independent verifier pending.〔2026-09-24：独立复核已由复核 #3 完成；provider 冒烟维持 deferred（无 Key）〕
- Risks: Recommendation generation currently runs from background reads and may wait for the provider
  timeout; it should move to an explicit queued worker before release.

## 复核记录 #3（独立 verifier，2026-09-24）— 收口

- 复核发现并封堵的覆盖缺口：验收项 2 要求「两态各一条断言」，但全测试集原本只有
  `fromHistory=false` 的断言（`HomeWiringTest:282/:290`），正向态（有主题 + 派生集合非空 →
  `fromHistory=true`）无任何测试。已补 2 例（正向态 + 派生空回退态），定向运行 BUILD SUCCESSFUL
  （`T-046-verify3-2026-09-24.txt`，EXITCODE=0）。该 2 例是覆盖补强：行为在生产代码已存在
  （`HomeWiring.java:111-131`），非新功能轮，不另立 RED→GREEN。
- 既有 `HomeWiringTest:290` 的用例名「UntilADerivedSetHasAProducer」相对已接线现状已过时
  （其断言仍正确：夹具未配 Key 时走兜底），按只读复核惯例不改测试名，登记于此。
- 过期文档修正：`HomeWiring.recommendations` javadoc（原写「deliberately unused」）已在
  2026-09-24 更正为已接线描述；与代码事实对齐。
- 门禁：全量 `check` 复跑 EXITCODE=0（`P3-B4-gates-2026-09-24.txt`）；no-kotlin guard PASS
  （`P3-B3-no-kotlin-2026-09-24.txt`）。
- Deferred 维持：真实 provider 推荐载荷冒烟（无 Key）；设备侧行级证据归 T-024。
- 结论：验收 5 条全勾（其中第 2 条经本轮补测后才有完整两态断言），无新增缺口 → **verified**。

## Handoff notes

- Completed: 推荐接线已实现（回答级经 `DerivedService.ensureConversationArtifacts`，首页级经 `HomeWiring.recommendations` → `derived.homeRecommendations`，后台派生、同步返回当前集合、失败回退启动兜底）；2026-09-24 复核 #3 补齐两态断言后转 verified。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 已解除（复核 #3 于 2026-09-24 完成）。
- Next steps: 发布前可把推荐派生迁出读取路径的同步等待（与 T-047 压缩同款 Risks）；真实 provider 载荷冒烟待有 Key 后补。
- Watch-outs: ① 接参数时最容易顺手破坏「无主题走启动集合」的兜底——正反两条断言现已齐（`:290` 兜底 + 新增空派生回退）；② 不得让首页首帧阻塞在网络派生上（后台触发由测试钉住）；③ `Recommendations` 构造器拒绝空列表，派生结果为空必须走 `starterFallback`，不得传空集进 `fromHistory`。
