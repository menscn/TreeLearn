# Treelearn Harness Task: 引用进请求（划词追问的引用文本真正发给模型）

id: T-2026-09-22-044
title: 引用进请求（划词追问的引用文本真正发给模型）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 70
shared_code_touched: true
depends_on: [T-2026-09-21-quote-persistence-messages-column-037.md, T-2026-09-21-real-data-path-wiring-032.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: PRD 6.2.19 第 3/6/7 条要求「追问」把选中内容作为引用放进当前输入框，回答留在当前分支，且用户消息保留引用的可见标识。若被引用的那句话从未进入发给模型的请求，引用追问就只是界面装饰：模型看不到学习者在问「这句话」。
- Problem/background: 2026-09-22 事实核对发现引用在三个环节断链。① 发送路径 `ChatWiring.java:94-95` 把引用写进 Message 行，但 `ChatWiring.java:103@@ 只把 userMessage.userContent 交给 submit；② 组装路径 `ChatWiring.java:232-238@@ 用 store.history(branchId, questionOrdinal) 与 input 组装 ContextPlan，而 `SqliteChatStore.java:109-131@@ 在 row.ordinal >= questionOrdinal 处 break，恰好排除持有引用的那一条；③ 领域路径 `ContextPlan.Entry@@（`android/core/src/main/java/com/treelearn/core/domain/ContextPlan.java:41-53@@）根本没有引用字段。android/ai/src/main 中亦无任何把引用写进正文的约定 —— 线格式在设计文档里不存在。
- Users/scenarios: 学习者在回答里选中一句话，点「追问」，补充自己的问题后发送；他期望模型针对那句话回答。
- Expected outcome: 有引用时，发给 DeepSeek 的最后一条 user 消息同时包含被引用的原句与该问题，引用被一对唯一信封标签包住；无引用时请求字节与今日完全一致。
- Core flow and reversibility: 只改「组装请求输入」这一步，不改落库、不改展示、不改分支结构；引用本身早已持久化在 messages.quoted_text（T-037），所以本任务是可逆的纯组装变更。
- Inputs/outputs: 输入是学习者的问题文本与引用文本（可能为空）；输出是传给 `DeepSeekRequestAssembler.assemble` 的 currentInput 字符串。
- State and exception handling: 引用为空或纯空白时不得出现任何标签；引用过长导致 input 超出预算时按既有 `PromptBuildException.Reason.RESERVE_EXCEEDS_BUDGET` 失败关闭，绝不截断引用、绝不静默丢弃引用。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX；信封字面量只能存在于适配器（`DeepSeekContextEncoder`）内部，不得出现在 :core 或 UI；不得为引用新增 `ContextPlan.EntryKind` —— 引用属于「当前问题」的一部分，不属于历史轮次（设计文档 5 节把当前问题定为恰好一条且最后一条）。
- AI recommendation and rationale: 见 Implementation notes 的线格式决定。
- User choice and custom additions: 用户 2026-09-22 明确要求「引用追问」必须完整实现，不接受「界面看起来有引用、模型看不到」的形态。
- AI-autonomous decisions: 线格式（信封标签 + 顺序 + 转义范围）；裁决理由与代价见 Implementation notes。
- User-confirmation decisions: 无。线格式属实现细节，已同步进提示词设计文档，不需用户拍板。
- Acceptance evidence: RED→GREEN 的 JVM 用例（有引用进正文 / 无引用字节不变 / 两个入口逐字符一致 / 引文不能伪造信封 / 超预算失败关闭）、`:ai` 与 `:app` 既有套件回归、`android-no-kotlin-guard` PASS、`contract-guardian` 对提示词设计文档的复核 PASS。
- Facts: `DeepSeekContextEncoder` 已定义四个信封字面量并把它们集中在 TAGS 数组，escape/unescape 已可逆；`DeepSeekRequestAssembler.assemble` 对 LEARNING_ANSWER 不做转义地原样使用 currentInput（`:47`）；`ChatWiring` 的 retry/reset 经 `questionBehind`（`:328-340`）从行里读回问题，是第二条会丢引用的入口；`Message.user` 与 `ChatMessage` 各自做了一次 quote 的 null/trim 归一化（已有的两处重复，本任务不得再加第三处）。
- Assumptions: messages.quoted_text 已由 T-037 持久化并可从渠道读回（`Message.quotedText()` 已在 `ChatWiring.java:270` 被使用）。
- Unknowns: 真实 DeepSeek 对被引用句子的问题是否按预期作答 —— 无 Key，只能靠线格式与提示词的静态核对。
- Risks: ① 只修 send 而漏掉 retry/reset，于是「重新生成」后引用再次消失（本仓失败库「同一条规则写在两个入口」的同形缺陷）；② 把「有没有引用」的判定复制到两处；③ 信封标签泄漏进 :core 或 UI。
- Deferred: 真实 DeepSeek 联网冒烟（无 Key）；设备侧观感。
- Non-goals: 不改引用如何产生（划词菜单归 T-041）；不改 messages 表结构；不改历史轮次如何编码；不做引用摘要或引用裁剪。
- Skipped non-critical questions and reason: 未讨论「引用是否也要作为独立 history 轮次」——设计文档 5 节已把当前问题定为恰好一条且最后一条，拆成两条会破坏该不变式。

## Goal

划词追问里被引用的那句话真正进入发给模型的请求：有引用时它出现在最后一条 user 消息的引用信封内，无引用时请求字节与改动前完全一致；且发送、重试、重置三条路径走同一个组装点。

## Scope

### In scope
- 在 `android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekContextEncoder.java` 增加第五个信封字面量与一个组装函数：把（引用，问题）组成 LEARNING_ANSWER 的 currentInput；引用经既有 escape 转义，问题保持学习者原文。
- 把新字面量并入 `TAGS`，使引文无法伪造信封；保持 escape/unescape 可逆与既有用例不变。
- `android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java`：send 与 replace（retry/reset）都经该唯一函数组装；让 `Asked` 一并携带引用，使 `questionBehind` 读回的行引用也能进正文。
- 同步 `Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md` 第 5 节：写明信封与顺序、无引用时不得出现标签、引用为不可信内容。

### Out of scope
- 划词菜单与子分支弹层（T-041）；导航页（T-039）；搜索页（T-040）；阅读位置（T-042）；首页主题级入口（T-043）。
- `ContextPlan` 的任何新增字段或 EntryKind。
- 数据库 schema、migration、messages.quoted_text 的写入与读取语义。
- `ChatMessage.quote` 与 `Message.user` 既有的 quote 归一化（只允许收敛到一处，不允许新增第三处）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 有引用时请求正文新增引用信封（预期变化）；无引用时请求字节不得改变（要保护）；引用落库、界面引用卡片与引用角标、回答版本与分支结构均不变（要保护）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `ChatWiring.submit`（`:232-238`）；两个消费者入口 `send`（`:103`）与 `replace`（`:222`，经 `questionBehind` `:328-340`）；下游 `DeepSeekRequestAssembler.assemble`（`:36-50`）与 `DeepSeekContextEncoder`；无平台分支。
- Native bridge or independent-client compatibility/update requirements: 无桥接；请求格式属应用与模型提供方之间的契约，已在提示词设计文档第 5 节落文。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: LEARNING_ANSWER 的 currentInput 在有引用时包含一个 quote 信封块；`DeepSeekContextEncoder.TAGS` 由 4 条字面量变为 6 条（新增一对 `<treelearn_quote>` / `</treelearn_quote>`）。
- Authoritative code/config/migration evidence: `DeepSeekContextEncoder` 的 TAGS 与组装函数、`ChatWiring` 的 submit 与 replace 是唯一判定点；无 migration 参与。
- Documentation surfaces checked: 提示词设计 V1.0 第 5 节（请求组装顺序）、第 3.1 节（领域契约）、第 9 节（变更与文档同步）；技术方案的上下文/请求章节；android/README.md 的请求预算小节。
- Documentation files updated: `Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md`（§5 约束补一句 + 新增 §5.1「引文的线格式」+ §8.1 补一条确定性测试）；`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md`（§9.2 新增一条〔2026-09-22 落地〕）；`android/README.md`（「供应商边界」补 quote 标签 + 对话页新增「引用真正进请求（T-044）」小节）。
- Producer/consumer/sibling/compatibility paths checked: 生产者 `DeepSeekContextEncoder.currentInput`（唯一判定点）；消费者入口 `ChatWiring.send`（转发 `userMessage.quotedText()`）与 `ChatWiring.replace`→`questionBehind`→`Asked.quote`（转发存行引文）；下游 `DeepSeekRequestAssembler.reserveFor` 以 `currentInput.length()` 计数，故引用自动计入预算；同族路径 `HomeWiring`（首页首题无引文 UI，仍直接传问题原文，等价于 `currentInput(question, null)`，本轮不改）；`:core` 的 `ContextPlan.Entry` 与 `EntryKind` 未改（引用不进历史）；无桥接、无 migration、无 schema 改动。
- Superseded phrases/examples reverse-searched: 反查「当前问题（恰好一条，最后一条）」——`rg -n "当前问题（恰好一条，最后一条）" Docs android/README.md` 命中 2 行（提示词设计 `:111`、技术方案 `:207`），该措辞在引用进正文后**仍然成立**（不新增消息），故未取代、只需在同一处补线格式；`rg -n "treelearn_reasoning" android/README.md` 命中 1 行（`:209`「供应商边界」），已同步加入 quote 标签；`rg -n "quoted_text" android/README.md` 命中 2 行（`:502` T-037 的既有描述、`:629` 本任务新增小节），二者不冲突。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: PRD 6.2.19 原文不改；本任务只补设计文档里缺失的线格式。

## Acceptance criteria

- [x] 有引用时，组装出的请求中最后一条 user 消息同时含被引用原句与问题，且被引用原句恰好被一对 quote 信封包住；证据为 :ai 的新 JVM 用例输出（`DeepSeekContextEncoderTest.aCitedSentenceIsFramedAndThenTheQuestionFollows` 逐字节断言，另 `ChatWiringTest.sendPutsTheCitedSentenceIntoTheRequestsCurrentInput` 断言请求最后一条消息）。
- [x] 无引用时，同一问题的 currentInput 与改动前逐字符相同（`aTurnWithoutACitationIsTheQuestionAlone` 断言 null/空/纯空白三种输入都等于问题本身，且信流行数为 0；`ChatWiringTest.aTurnWithoutACitationTravelsAsTheQuestionAlone` 断言请求正文就是问题原文）。
- [x] 发送与重试/重置两个入口对同一（问题，引用）产出逐字符相同的 currentInput（`ChatWiringTest.sendAndRetryAgreeCharacterForCharacterOnTheCurrentInput` 直接把两个入口的正文做 `assertEquals` 比对）。
- [x] 引文内含信封字面量或反斜杠时被转义，且 unescape 可逆恢复原文（`aCitationThatImitatesTheFramingCanNotForgeABoundary`：信流行数仍为 2、`unescape(帧体)` 等于原文）；:core 生产源码扫描仍不含任何信封字面量（既有扫描用例的 TAGS 循环自动覆盖新标签，前缀断言已同步加入 `treelearn_quote`）。
- [x] 引用使 currentInput 变长并计入既有预算校验：`DeepSeekRequestAssemblerTest.aCitationCountsIntoTheBudgetAndFailsClosedInsteadOfBeingTrimmed` 用「刚好放得下」，容量减 1 即 `RESERVE_EXCEEDS_BUDGET`，且同容量下不带引文的同一问题仍构建成功（对照），拒绝信息不含引文正文。
- [x] 既有 :ai 与 :app 套件全绿（全量 1093 例 0 失败 0 错误）。**两处既有断言按新行为改写**（`ChatWiringTest:163/:204`），改写理由与等价覆盖见 Handoff watch-outs ②③；引用落库与界面展示的既有断言未改语义。
- [x] 提示词设计 V1.0 第 5 节已写明线格式（新增 §5.1）；android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）。**contract-guardian 复核未由实现者自评**——按路由属独立 verifier 的职责，见 Handoff Next steps。

## TDD execution record

### RED — failing test first
- Test/scenario: 先写用例：`:app` 的 `ChatWiringTest` 断言「引用进请求」（新增 3 条 + 改写 2 条既有断言），`:ai` 的 `DeepSeekContextEncoderTest` / `DeepSeekRequestAssemblerTest` 断言信封、转义、唯一判定点与预算失败关闭。
- Command: cd android; .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests '*ChatWiringTest*' --rerun-tasks --no-build-cache
- Exit code: 1
- Observed failure: 22 tests completed, 4 failed —— 四条断言点的实际值都只有问题原文、没有引用：`expected:<[<treelearn_quote>\nthe sentence the learner selected\n</treelearn_quote>\n]a follow-up> but was:<[]a follow-up>`（另三条同形，分别是 `...]first question in th...`）。
- Failure reason validated: 失败确由「引用不进请求」这一缺失行为导致，而非用例写错：失败的四行（`ChatWiringTest.java:167`、`:213`、`:419`、`:471`）正好落在本次要改的两个入口（`send` 与 `retry`）；实现后同一条命令 0 失败。线格式接缝在实现前不存在，由第二条独立命令以 9 个 `找不到符号` 证明（若只跑第一条命令，`:ai` 侧的新用例根本不被编译，会得出「只有 :app 有问题」的错误印象）。

```text
证据: artifacts/evidence/T-044-red.txt
  run 1: 同一命令写成 :app:test 被 Gradle 拒绝（`Unknown command-line option '--tests'`）——`:app:test` 是聚合生命周期任务，
         只有真实测试任务（:app:testDebugUnitTest）才接受过滤；退出码 1，与「用例失败」同码，故单列一段以免误读。
  run 2 (:app): 22 tests completed, 4 failed; 54 actionable tasks: 54 executed; EXITCODE=1
         失败行: ChatWiringTest.java:167 / :213 / :419 / :471
  run 3 (:ai) : :ai:compileDebugUnitTestJavaWithJavac FAILED; 9 个错误; 15 actionable tasks: 15 executed; EXITCODE=1
证据: artifacts/evidence/T-044-red-ai.txt（单独重跑 :ai，便于精确计数）
  计数口径与一次易错读法: rg -c '错误: 找不到符号' 得 18，是 9 个错误出现两次所致——同一段编译器输出在文件里
  既有 javac 自己的输出、又有 Gradle 失败摘要里的回显。按调用点去重后 9 条构成:
  6 × 方法 currentInput(String,String) + 1 × 方法 currentInput(String,<空值>) + 1 × 变量 QUOTE_OPEN + 1 × 变量 QUOTE_CLOSE = 9
```

### GREEN — minimum implementation
- Implementation: `DeepSeekContextEncoder` 新增 `QUOTE_OPEN` / `QUOTE_CLOSE` 并入 `TAGS`，新增 `currentInput(question, quote)` 作为「有没有引用」的唯一判定点（null/空白归一化在此一处）；`ChatWiring.submit` 增加 `quote` 参数并改调 `DeepSeekContextEncoder.currentInput`，`send` 转发 `userMessage.quotedText()`，`replace` 经 `Asked.quote` 转发存行引文。生产代码净增 22 行（非空非注释口径），无新文件、无 schema 改动、无新依赖。
- Command: cd android; .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests '*ChatWiringTest*' --rerun-tasks --no-build-cache
- Exit code: 0
- Passing evidence: 与 RED 逐字同一条定向命令（代码冻结后重跑）：`BUILD SUCCESSFUL in 19s`、`54 actionable tasks: 54 executed`、`app/testDebugUnitTest` 1 suite 22 tests 0 failures —— 与 RED 是同一批 22 例（RED 时 4 条失败，现 0 条）；另跑全量 `test --rerun-tasks --no-build-cache`（代码冻结后重跑）：`BUILD SUCCESSFUL in 31s`、`124 actionable tasks: 124 executed`，全部 `TEST-*.xml` 回读 **1093 例 0 失败 0 错误**（口径见代码块）。
- Regression evidence: 全量 `test` 覆盖 core / tree-layout / ai / app / data 的全部既有套件；`.\gradlew.bat --no-daemon --console=plain check --rerun-tasks --no-build-cache` 退出码 0（`295 actionable tasks: 295 executed`，其中 `verifyNoKotlinDex: PASS (3 APK(s) inspected across app/build/outputs/apk, no Kotlin runtime bytecode)`）；android-no-kotlin-guard `VERDICT: PASS (13 OK line(s))`，退出码 0。

```text
证据: artifacts/evidence/T-044-green.txt（run 1 定向、run 2 全量、run 3 冻结后全量重跑）、artifacts/evidence/T-044-green-targeted.txt（与 RED 逐字同一条定向命令，EXITCODE=0）、artifacts/evidence/T-044-checks.txt（check run 2 + guard run 2，均为冻结后重跑）
按变体回读（Get-ChildItem android/<module>/build/test-results/<variant>/TEST-*.xml；下列读数取自 run 3，与 run 2 相同）:
  ai/testDebugUnitTest 7 suites 76 tests 0 failures | ai/testReleaseUnitTest 7 suites 76 tests 0 failures
  app/testDebugUnitTest 31 suites 329 tests 0 failures | app/testReleaseUnitTest 31 suites 329 tests 0 failures
  data/testDebugUnitTest 4 suites 26 tests 0 failures | data/testReleaseUnitTest 4 suites 26 tests 0 failures
  core/test 19 suites 197 tests 0 failures | tree-layout/test 3 suites 34 tests 0 failures
  合计 1093 例 0 失败 0 错误。口径: Android 模块（ai/app/data）的同一批用例在 debug 与 release 两个变体各跑一遍，
  故它们计入两次；core 与 tree-layout 是纯 JVM 模块，只跑一次。去重后（每模块计一个变体）为 662 例。
  两处既有断言的改写（均在 app/src/test，属测试代码，不计入生产行数）:
    ChatWiringTest:163 `assertEquals("a follow-up".length(), currentInputChars)`
    ChatWiringTest:204 `assertEquals(FIRST_QUESTION.length(), currentInputChars)`
    两者编码的正是本任务要修的行为（「引用被丢弃」），在引用进正文后必然为假。改为断言「信封+问题的完整正文」
    及其长度，并保留原断言的意图（当前输入是**去空白后**的问题；重试取的是该位置背后的那个问题而非分支最新问题）:
    前者另加 `assertFalse(latest.contains("  a follow-up  "))` 保住「去空白」这一原意。
规模: node artifacts/evidence/T-044-sizing.mjs → TOTAL code=22 comment=52 blank=16 physical=90; VERDICT: OK
  （判定值 22 ≤ 200 软上限，未触发 500 硬上限，不需要规模例外）；证据 artifacts/evidence/T-044-sizing.txt

副作用（必须登记）: `check` 会连带构建 APK（verifyNoKotlinDex 是 check 的一环），因此
  android/app/build/outputs/apk/release/app-release.apk 被本轮重建（冻结后 run 2 读数 472488 字节，
  先前登记 452348 字节；2:51 的 run 1 与 run 2 读数一致，即 472488）。
  另: app-debug.apk 两次读数不同（2:51 的 run 1 为 742371 字节、冻结后 run 2 为 626251 字节，两者 dex 数均为 13），
  androidTest 包 996789 字节。本任务未改打包配置，该差异来源未定位，已列为 Handoff watch-out ⑤，请勿当作 T-044 回归。
  指纹与交付报告由 leader 收口时刷新。
```

### REFACTOR — optional
- Cleanup: not needed。本次是纯增量（新增一对信封字面量 + 一个组装函数 + 两条转发），没有可去重的重复逻辑，也没有需要收敛的调用点。仓库里既有的重复——`Message.user` 与 `ChatMessage` 各自对 quote 做一次 null/trim 归一化——**刻意保留**：收敛它属于本任务 Out of scope，且会把改动扩大到 UI 与领域类型。唯一判定点已经收敛在 `DeepSeekContextEncoder.currentInput`，本任务没有新增第二处判断。
- Regression evidence: 无重构，故无「重构后」单独证据；GREEN 的冻结后全量 `test`（run 3，124/124 executed，1093 例 0 失败 0 错误）与 `check`（run 2，295/295 executed）即是同一份前后一致的行为证据。

### TDD or sizing exception — only with prior user approval
- Exception type: not applicable（未使用例外）
- Reason: `tdd_mode: required` 且 RED→GREEN 已按 TDD 走完；实测生产规模 22 行（非空非注释口径，`node artifacts/evidence/T-044-sizing.mjs` 输出 `TOTAL code=22`），远低于 200 软上限，更未触 500 硬上限，因此既不需要 TDD 例外也不需要规模例外。
- User approval: not needed
- Alternative verification: not needed

## Implementation notes

- 线格式（AI-autonomous decision，2026-09-22）：
  有引用时 currentInput 形如——
  信封开标签、换行、被转义后的引用、换行、信封闭标签、换行、学习者问题原文。
  无引用时 currentInput 就是学习者问题原文，不得出现任何标签。
- 为什么放在 currentInput 而不是历史轮次：设计文档第 5 节把「当前问题」定为恰好一条且最后一条；引用是对这句话的限定，属于它的一部分。放进历史会新增第二条 user 消息，破坏该不变式，并且要求给 :core 的 Entry 加字段（领域类型不该知道模型协议）。
- 为什么信封字面量只放在 DeepSeekContextEncoder：该类的 javadoc 已声明「标签只存在于这个类」，且有一条 JVM 用例扫描 :core 生产源码禁止这些字面量出现。新字面量必须并入 TAGS，否则引文可以伪造信封闭合。
- 为什么只转义引用、不转义问题：问题原文要同时被落库与界面原样展示，线上转义会让「发出去的」与「存下来的」不一致；引用是应用生成的信封内容，转义它即可挡住伪造。残留风险（引文之外、问题里出现的类标签文字）已记入 Risks 并写入设计文档。
- 唯一判定点：是否有引用（null/空白归一化）只能由适配器那一个函数判定；ChatWiring 的 send 与 replace 都只做转发，不得各自判断一次。
- 预算：不必新增校验，既有 reserveFor 以 currentInput.length() 计数，引用进入 currentInput 后自动计入；只需用例证明超预算时失败关闭而非截断。

## Verification report

- Verdict: PARTIAL
- Evidence: artifacts/evidence/T-044-verify-callchain.txt（判据 1/2：调用链与唯一判定点，EXITCODE=0）、artifacts/evidence/T-044-verify-doc-and-assertions.txt（判据 3/4/5/6/7：文档对码、改写断言、证据自证审计，EXITCODE=0）、artifacts/evidence/T-044-verify-verdict.txt（跨入口比对断言、验收点名用例存在性、声明文档面，EXITCODE=0）
- Changed/deleted assertion rationale and equivalent behavior coverage: 两处既有断言（ChatWiringTest 的 send 与 retry 两条用例，任务书原引 :163/:204）由「currentInputChars 等于问题长度」改写为「等于信封+问题的完整正文长度」，并新增逐字节 assertEquals(cited(...), latest)。改写是加强而非放松：原意图分别由 assertFalse(latest, latest.contains("  a follow-up  "))（当前输入是去空白后的问题）与 assertEquals(0, store.historyOrdinal)（重试取该位置背后的问题）保住；旧行为「引用被丢弃」现被断言为假。期望值由测试内写死信封字面量的 cited() 构造（源码注释明说不引用适配器常量以免自证），不构成自证。方向与等价覆盖经独立复现，见判据 7。
- Gaps: ① 设备侧与真实 DeepSeek 联网作答未验证，deferred — 用户自行装机验证（user decision 2026-09-21）。② 本轮 Gradle 门禁读数（1093 例 0 失败、check 退出码 0、Java-only guard PASS）取自实现者证据文件，verifier 未独立重跑（另一 lane 占用构建缓存），只核对到「含命令、cwd 与 EXITCODE=」，未复现。③ artifacts/evidence/T-044-contract-guardian.txt 的产出方无法确认（自述，见 F4）。④ T-044-sizing.txt 无 command: 头行。
- Risks: ① 归一化「只有一处」的表述与代码不符，下一位读者可能以为改一处就够（F2）。② 交付报告 §5 已声明为历史快照，但 :160 尾句的现在时断言仍可能被当作现行事实引用（F1），修复前不要据此引用。③ 真实 provider 对引文信封的反应仍未知（无 Key），线格式只在静态层被钉住。

**逐条判据**

- 1 真的进请求 — PASS。ChatWiring.send 于 :115 转发 userMessage.quotedText()、replace 于 :234 转发 asked.quote，两者汇入唯一的 submit（:246），后者在 :252 调 DeepSeekContextEncoder.currentInput(input, quote)；currentInput 在生产代码只有这一个调用点（T-044-verify-callchain.txt 第 1 节）。组装器 :42 validateCurrentInput 后于 :47 原样作为最后一条 USER 消息放入，无覆盖。不存在第三条路径：questionBehind（:357-358）返回的是纯问题文本与存行引文两个独立值，不是已拼好的正文，故无「进了请求又被它覆盖」的形态；HomeWiring:127 的局部变量同名但走无引文路径，等价于 currentInput(question, null)。
- 2 唯一判定点 — PASS（判定）/ FINDING（归一化）。「有没有引文」的唯一判定在 DeepSeekContextEncoder.currentInput :85-88（cited.isEmpty() 即返回问题本身）；信封字面量在生产源码只存在于该类 :45-46；escape/unescape 各只有一份实现，TAGS 是唯一标签清单。但同一归一化表达式 quote == null ? "" : quote.trim() 在生产代码有 3 处（编码器 :85、ChatWiring :104、ChatMessage :73），本轮新增 1 处——见 F2。
- 3 无引用零差异 — PASS。aTurnWithoutACitationIsTheQuestionAlone 断言 currentInput(QUESTION, "")、("   ")、(null) 三者都逐字符等于问题本身，且信流行数为 0；wiring 侧 aTurnWithoutACitationTravelsAsTheQuestionAlone 另断言请求正文就是问题原文且不含 <treelearn_。
- 4 失败关闭 — PASS。aCitationCountsIntoTheBudgetAndFailsClosedInsteadOfBeingTrimmed：容量恰好时正文原样接受（:262-263），容量减 1 即 RESERVE_EXCEEDS_BUDGET（:268），拒绝信息不含引文正文（:269-270），并以「同一问题、同容量、无引文仍成功」作对照（:273-275）证明撑爆预算的是引文而非问题。既有 reserveFor 用 currentInput.length() 计数，引文自动计入，未新增校验分支。
- 5 契约面一致性 — PASS。提示词设计 §5.1 :125-137 的线格式与代码逐项一致：标签名逐字符相同、顺序「开→引文→闭→问题」、换行位置一致、只转义引文与代码 escape(cited) 一致、无引文返回问题本身与代码一致。当前问题仍恰好一条且位于最后（由组装器构造式保证，非事后校验）。信封字面量在 :core 与 UI 生产源码 0 命中，且承担该扫描的用例断言匹配集非空。
- 6 证据自证 — PARTIAL。7 份实现者证据中 6 份含命令与 EXITCODE=；T-044-contract-guardian.txt 有命令清单但 0 条 EXITCODE（散文报告而非命令捕获），T-044-sizing.txt 无 command: 头行。
- 7 改写断言 — PASS。见 Changed/deleted assertion rationale；跨入口比对断言（send 与 retry 逐字符相等）已独立读到，正是本仓失败库要求的「修完必须加一条跨入口比对断言」。

**Findings**

- F1（必修，一行）Docs/06-delivery/TreeLearn_V1_交付报告.md:160 尾句仍写「『引用进入提示词的线格式未定义』仍成立，归 T-2026-09-21-real-data-path-wiring-032」。该句是现在时断言且已为假：线格式已由本任务写入提示词设计 §5.1，交付者是 T-044 而非 T-032。同一 item 前半句已带〔历史快照 · 2026-09-21〕且 §5 有节级历史快照声明，故定性为残留措辞而非新缺陷；修复为删去「仍成立」或改写为指向 §5.1 与 DeepSeekContextEncoder.currentInput。
- F2（应修或明确接受）quote 的 null/trim 归一化在生产代码 3 处。三处对同一输入结果相同（幂等、无分叉），因此不是行为缺陷；但提示词设计 §5.1 :136「null 与纯空白在此归一化」、android/README.md:641「判定（null/空白归一化）只有一处」、以及本任务 TDD 记录里的「null/空白归一化在此一处」都高估了唯一性：唯一的是判定，不是归一化。二选一：收敛到 :core 的一个共享谓词，或把三处表述改为「判定唯一、归一化不唯一」并写明理由。
- F3（记录）本任务 Facts 行称「Message.user 与 ChatMessage 各自做了一次 quote 的 null/trim 归一化」。实读不成立：Message.requiredQuote（:162-167）对 null 抛异常且不 trim，它做的是拒绝而非归一化。故「已有的两处重复」实为 ChatMessage:73 与 ChatWiring:104，而本任务确实加了第三处（编码器 :85）。后果无害（幂等），但该约束在字面上被越过，且 Facts 行的描述与代码不符。
- F4（流程）artifacts/evidence/T-044-contract-guardian.txt 头部自述「contract-guardian · 只读」、captured-at 03:03:33、列出只读检索命令、未跑 Gradle。其内容形态与独立契约复核一致（六问、行号级证据、对本任务自家文档判 FAIL），且与我的独立复核吻合（§5.1 线格式 PASS、唯一判定点、交付报告滞后）。但产出方无法确认：它是自述，且本任务 Handoff watch-outs ① 写的是「contract-guardian 复核未做」，与该文件存在相矛盾。结论：其实质结论可采信（经本轮独立复现），其独立性不可采信；若要它充当验收的 contract 轴证据，须由可识别的独立运行重出，或由本轮 verifier 的 contract 轴结论替代。
- F5（范围）交付报告不在本任务 Documentation impact record 声明的文档面内（声明的只有提示词设计 §5/§3.1/§9、技术方案上下文/请求章节、android/README.md）。故 F1 不是 T-044 的验收缺陷，而是需单独路由的文档卫生项。

**覆盖边界（本轮未覆盖，不得读作通过）**

- 未独立重跑任何 Gradle 任务：另一 lane 正占用构建缓存，本轮全部判据为只读源码/文档/证据回读。故「1093 例 0 失败 0 错误」「check 退出码 0」「Java-only guard PASS」是自述证据，我只核对了它们含命令、cwd 与 EXITCODE=，未复现。
- 设备侧、真实 DeepSeek 联网作答（无 Key）：deferred — 用户自行装机验证（user decision 2026-09-21）。不得读作 PASS，也不因此判本任务 FAIL。

**contract 轴结论**

- 端点级一致性（线格式 ↔ DeepSeekContextEncoder.currentInput）：PASS
- 信封字面量唯一性（:core / :app / UI 生产源码）：PASS（0 命中，且扫描用例断言匹配集非空）
- 单一判定点：PASS（判定）；归一化唯一性：FAIL（3 处，F2）
- 不变式（system 恰好一条且在 0；当前问题恰好一条且最后）：PASS
- 错误语义（超预算按既有 RESERVE_EXCEEDS_BUDGET 失败关闭、未新增错误码、拒绝不回显引文）：PASS
- 向后兼容（无 schema/migration/DTO 改动；无引文时字节不变）：PASS
- 文档同步：PARTIAL（§5.1、技术方案 §9.2、android/README.md 已同步；交付报告 :160 残留一句，但该文件不在本任务声明的文档面内）
- 契约轴整体：PARTIAL

## Handoff notes

- Completed: 实现、测试与文档全部落盘。生产代码：`DeepSeekContextEncoder` 新增 `QUOTE_OPEN` / `QUOTE_CLOSE` 并入 `TAGS`、新增 `currentInput(question, quote)` 作为「有没有引用」的唯一判定点；`ChatWiring.submit` 增加 quote 形参并改调该函数，`send` 转发 `userMessage.quotedText()`，`replace` 经 `Asked.quote` 转发存行引文（两处入口都只转发）。测试代码：`:app` 新增 3 条 + 改写 2 条既有断言，`:ai` 新增 3 条 + 扫描断言加 `treelearn_quote`。文档：提示词设计 V1.0 §5 补一句 + 新增 §5.1「引文的线格式」（含只转义引用的残留风险）+ §8.1 补一条；技术方案 §9.2 新增一条〔2026-09-22 落地〕；`android/README.md` 供应商边界与对话页两处。RED→GREEN 与门禁证据见 `artifacts/evidence/T-044-*.txt`。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 独立 verifier 按 Acceptance criteria 7 条验收（含 send 与 retry/reset 的跨入口逐字符比对断言，以及第 7 条的 deferred 设备侧条目）；② 按 `.trae/rules/contracts.md` 路由派 `contract-guardian` 复核 `Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md`（本任务改了请求线格式这一模型侧契约面）；③ leader 收口时刷新 APK 指纹与交付报告。 〔2026-09-22 独立 verifier 已判 PARTIAL：能力面（引文真的进请求、两入口逐字符一致、失败关闭、无引文零差异、:core 干净）经源码调用链与用例独立复现为 PASS；未结项的是 contract/文档轴——F1 交付报告 :160 残留一句现在时断言『线格式未定义』仍成立（一行修复，且该文件不在本任务声明的文档面内）、F2 quote 归一化在生产代码 3 处而三处表述称『只有一处』（判定唯一、归一化不唯一）、F3 任务 Facts 行对 Message.user 归一化的描述与代码不符、F4 盘上 contract-guardian 证据的产出方无法确认。本轮未独立重跑 Gradle，门禁读数为自述证据。详见 Verification report 与 artifacts/evidence/T-044-verify-*.txt。〕
- Watch-outs:
  ① contract-guardian 复核**未做**——本任务改了提示词设计文档里的请求线格式，按路由必须由独立角色复核，实现者不自评。
  ② `ChatWiringTest:163` 与 `:204` 两处**既有断言按新行为改写**（原文断言的正是本任务要修的缺陷行为「引用被丢弃」，如 `assertEquals("a follow-up".length(), currentInputChars)`）。改写理由与等价覆盖已在 GREEN 代码块写明；verifier 需确认改写后仍锁住原意图（「当前输入是去空白后的问题」「重试取的是该位置背后那个问题而不是分支最新问题」）。
  ③ `:app:test` 是聚合生命周期任务、拒收 `--tests`（`Unknown command-line option '--tests'`）；定向过滤只能用 `:app:testDebugUnitTest`。RED 记录的第一条命令即踩到这个坑，故 RED/GREEN 统一改用后者。
  ④ 引文之外、**问题原文里**若出现信封字面量会原样发给 provider（只转义引用、不转义问题这一决定的残留风险，已写入设计文档 §5.1）。
  ⑤ `check` 会连带构建 APK：`app-release.apk` 被本轮重建（472488 字节，run 1 与 run 2 读数一致）；`app-debug.apk` 两次读数不同（742371 → 626251 字节，dex 数均 13），本任务未改打包配置、差异来源未定位——leader 核 APK 指纹时留意。
  ⑥ 设备侧全部 deferred — 用户自行装机验证（user decision 2026-09-21）：引用追问的真实观感、真实 DeepSeek 联网作答（无 Key）均未验证，**不得记为 PASS**，也不得仅因此判本任务 FAIL。
  ⑦ 证据与被测代码同源：`ChatWiring.java` 最后一次修改在 2026-09-22 02:53:33，而最初一轮 `test` / `check` 证据产生于 02:50:54 / 02:51:12，**早于**它。已在代码冻结后重跑（`T-044-green.txt` run 3、`T-044-checks.txt` run 2、`T-044-green-targeted.txt`、`T-044-sizing.txt`）；引用旧一轮读数前请以冻结后的 run 为准。

## 工头更正（2026-09-22）：归一化处数与措辞的**实测**取数

上一轮我在派单里写「`quote == null ? "" : quote.trim()` 在生产代码有 4 处」——**这个数字是照抄 contract-guardian 报告的，未经我实测，且不准确**。现按当前工作树逐条复核，取数命令与读数如下（供后续修复轮直接用，不要再凭记忆）：

```
rg -n 'quote\s*==\s*null\s*\?\s*""\s*:\s*quote\.trim\(\)' android --glob '!**/build/**'
```

- 命中 **3** 处（严格形式，含 `.trim()`）：
  - `android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekContextEncoder.java:85`（`String cited = quote == null ? "" : quote.trim();`，本轮新增的那处）
  - `android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java:104`
  - `android/app/src/main/java/com/treelearn/app/ui/ChatMessage.java:73`
- 另有 **1** 处**同形但不 trim**，只有用更宽的检索才看得到：`android/app/src/main/java/com/treelearn/app/ui/ChatViewState.java:77`（`this.quote = quote == null ? "" : quote;`）。

**合计 4 个归一化点，其中 3 个 trim、1 个不 trim**（`3 + 1 = 4`）。三种输入的今日后果仍为零（`ChatMessage:73` 已在构造期 trim，展示侧拿到的是已归一值），所以这仍是**漂移风险**而非现网缺陷；但修复轮取数必须用上面那条命令，并说明「4 处中有 1 处形态不同」，不得再写成含糊的「4 处相同写法」。

顺带更正上一轮同一条派单里的另一处措辞：我说的「不允许新增第三处」是转述本任务文件 `:58` 的原文，**该约束的准确对象是「『有没有引文』的判定点」，不是「归一化点」**——判定点确实唯一（生产只有一个 `currentInput` 调用点），归一化点不唯一。这正是 contract-guardian 建议我们在文档里改述清楚的那一点。