# Treelearn Harness Task: 实现统一首页与设置界面

id: T-2026-09-20-012
title: 实现统一首页与设置界面
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: false
depends_on: [T-2026-09-20-keystore-secret-settings-005.md, T-2026-09-20-topic-branch-lifecycle-009.md, T-2026-09-20-derived-ai-artifacts-011.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首屏直接支持继续学习、创建主题、AI 推荐和 Key/模型/思考设置。
- Problem/background: 0 到 1 应用需要将主题入口与新问题收敛到同一高频工作面。
- Users/scenarios: 用户打开应用继续最近主题、从推荐开始、输入新问题或配置 DeepSeek。
- Expected outcome: 原生 XML/View 首页与设置页、Presenter/ViewState、草稿保持和错误反馈。
- Core flow and reversibility: 无 Key 引导设置；保存后可验证；首题失败保留草稿；主题点击恢复最后分支。
- Inputs/outputs: 输入主题列表、推荐、偏好、用户草稿；输出导航或创建命令。
- State and exception handling: loading/empty/error、Key 缺失/无效/余额不足、推荐失败均有独立状态。
- Constraints and trade-offs: 手机竖屏优先，平板横屏合理利用宽度；44dp 热区；字体放大不遮挡。
- AI recommendation and rationale: ViewState 与 controller 分离，页面不直接访问 DAO/Gateway。
- User choice and custom additions: 纯原生 Android，个人 APK，首页 AI 话题推荐。
- AI-autonomous decisions: 推荐失败不阻止手动创建；草稿只在首题成功 accepted 后清空。
- User-confirmation decisions: BASE-21/22 与设备方向。
- Acceptance evidence: instrumentation/截图检查首页、设置、失败草稿和手机/平板布局。
- Facts: v2.5 原型和 V3.1 PRD 是交互权威。
- Assumptions: 具体字体可能回退系统中文字体。
- Unknowns: 无。
- Risks: 首页状态过载、Key 错误回显、横屏留白失衡。
- Deferred: 账号、云同步、营销首屏和复杂 onboarding。
- Non-goals: 不实现聊天、树画布或搜索。
- Skipped non-critical questions and reason: 动效细节以原型和无障碍优先，不影响业务契约。

## Goal

交付可直接开始或继续学习、且能安全配置 DeepSeek 的统一首页和设置流程。

## Scope

### In scope
- 首页主题/推荐/新问题、设置 Key/模型/thinking、Presenter/ViewState、手机/平板资源和 UI 测试。

### Out of scope
- 对话页、画布、归档与搜索结果页。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增首页/设置；保持首题 2xx 前无空树及失败草稿。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Topic/Artifact/Settings Repository 生产；CreateTopic 消费；手机/平板布局分支。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 页面入口、可见状态、设置项和导航参数。
- Authoritative code/config/migration evidence: layout/resources、Presenter/ViewState、UI tests。
- Documentation surfaces checked: V3.1 PRD 首页/设置、v2.5 原型、Android README。
- Documentation files updated: `android/README.md` 新增「应用层入口索引（`:app`）——首页与设置（T-012）」小节（13 行类型表 + 资源与尺寸事实 + 交互事实 + 已知缺口 + 规模实测），并把 T-005 小节的「`SettingsRepository` 目前没有运行时消费者（设置页 T-012 未实现）」改写为当前事实（消费者是 `wiring.AndroidSettingsStore`）、把「默认档位 T-012 可改」改写为「T-012 未改动默认值」。`Docs/**` 未改动：PRD 6.2.16/6.2.17/6.2.18 的交互口径没有变化，本任务按它实现，见「交互事实」一条。
- Producer/consumer/sibling/compatibility paths checked: 空/有主题、推荐成功/失败、Key 各状态、手机/平板。
- Superseded phrases/examples reverse-searched: 独立营销落地页和旧设置项。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 历史版本说明不回写。

## Acceptance criteria

- [ ] 同一 Presenter/UI 测试命令先 RED 后 GREEN，覆盖 empty/loading/error/ready、推荐失败和 Key 状态。已满足于 JVM 层：四个新套件 37 例（`HomePresenterTest` 17、`HomeViewStateTest` 7、`KeySnapshotTest` 6、`SettingsPresenterTest` 7）在同一命令串下一红一绿（`artifacts/evidence/T-012-red.txt` EXITCODE=1、`T-012-green.txt` EXITCODE=0，64 例 0 失败）。四相位、推荐失败（抛错则撤掉推荐条、返回则用启动兜底）、Key 三态与草稿保持都在其中。设备侧 UI 测试本轮只编译不执行，见最后一条。
- [ ] 401/402/422/429/503 或断网时首页草稿保留、无空 topic；accepted 后才清草稿并进入根分支。判定层已满足：`HomeViewState.Notice.from(AiGatewayError.Kind)` 把 401/402/422/429/503/断网分别映射到 KEY_REJECTED/BALANCE_UNAVAILABLE/REQUEST_REJECTED/RATE_LIMITED/PROVIDER_UNAVAILABLE/NETWORK_INTERRUPTED（`HomeViewStateTest` 固化），`HomePresenter` 只在 `SubmitResult.accepted` 分支清草稿并 `openBranch`，其余分支一律保留草稿且不创建任何行（`HomePresenterTest` 覆盖）。**真实 HTTP 路径与「库里确实没有 topic」需要设备与装配层**，记为 `deferred — emulator not used this round (user instruction 2026-09-21)`；且本切片尚无首题装配消费者，`submit()` 目前返回可见的 `NOT_WIRED` 拒绝（见 Handoff 的 Blocker ②）。
- [ ] 设置可保存/替换/删除 Key，切换模型和 thinking；任何 UI/日志不回显完整 Key。Key 的保存/替换/删除已满足（`SettingsPresenterTest` 7 例，含 keystore 拒绝后仍擦除缓冲区）；「不回显完整 Key」由结构保证：`KeySnapshot` 只吐掩码、`SettingsViewState` 不带明文、T-012 的 ui/screen/wiring 里没有任何 `Log.`/`System.out`（原文扫描：`artifacts/evidence/T-012-static-ui-checks.txt` 的 CHECK 1，唯一的命中是掩码前缀字面量）。**模型与 thinking 的切换本轮落在首页输入框内而不是设置页**：V3.1 6.2.18 point 2 与 point 3 明确「设置页不提供模型配置项」，由 `HomePresenter.onModelSelected` / `onThinkingToggled` + `SettingsRepository` 承担，`SettingsViewState` 刻意不含模型字段。这是对任务 brief 里「设置 Key/模型/thinking」措辞的**按 PRD 收窄**，不是漏做，理由与出处写在 README 同名小节。
- [ ] API 36 手机竖屏和平板横屏无重叠，字体放大可读，所有交互目标至少 44dp。（用户 2026-09-21：验收环境为 API 36/36.1）全部记为 `deferred — emulator not used this round (user instruction 2026-09-21)`：本轮不安装、不运行 instrumentation（`:app:assembleDebugAndroidTest` 仅编译，`app/build/outputs/androidTest-results/connected/debug` 保持为空）。可静态核查的部分已做：所有可点控件的 `layout_height`/`minWidth` 取 `@dimen/touch_min`(44dp) 或更大，字阶全部走 `text_*` 令牌而不写死 sp，平板用同一份列并以 `content_measure` 居中。

## TDD execution record

### RED — failing test first

- Test/scenario: app/src/test/java/com/treelearn/app/ui/ 的四个新套件（HomePresenterTest 17 例、HomeViewStateTest 7 例、KeySnapshotTest 6 例、SettingsPresenterTest 7 例，共 37 例）钉住四相位、推荐失败、Key 三态、草稿保持与「accepted 才清草稿」；RED 时测试留在原处，只把被测实现移走。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
- Exit code: 1
- Observed failure: javac 报 100 个错误（程序包不存在 39 + 找不到符号 45 + 方法不会覆盖或实现超类型的方法 16 = 100），编译阶段即失败，:app:testDebugUnitTest 从未开始，零个测试被执行。
- Failure reason validated: 在仓库外的副本里只移走 T-012 的实现（com/treelearn/app/{ui,screen,wiring}、AppContainer、MainActivity），这四个套件立刻编译失败，而同一源集里既有的 generation 四个套件与 FirstTopicCreationRulesTest 仍能编译；每个错误都指向缺失的 T-012 类型，因此失败来自「实现不存在」，不是测试写错、也不是环境坏了。

```text
证据：artifacts/evidence/T-012-red.txt（EXITCODE=1）
错误按文件分布（证据文件里每个错误行出现两次：javac 诊断块被打印了两遍，下表是除以 2 后的值）
  TestDoubles.java            50
  HomePresenterTest.java      28
  HomeViewStateTest.java      21
  SettingsPresenterTest.java   1
  合计                       100  与「100 个错误」一致
复现命令（可直接粘贴）
  Get-Content artifacts/evidence/T-012-red.txt -Encoding UTF8 | Select-String -Pattern '错误:'
RED 与 GREEN 两棵树之间只有一件事不同：T-012 的实现是否存在。
为什么用仓库外的副本：同一时刻共享树上的 :app 测试源集因另一个 lane 的在飞 RED 无法编译
（generation/PositionCommandsTest.java 引用尚不存在的 PositionCommands，28 个错误全出自那一个文件，
见 artifacts/evidence/T-012-in-repo-suite.txt）。把 T-012 的生产源码移出共享树会打断其他 lane 的构建，
用户 2026-09-21 已明令禁止，因此 RED 与 GREEN 都在 %TEMP%\t012-red 的副本里测量。
```

### GREEN — minimum implementation

- Implementation: com/treelearn/app/{ui,screen,wiring}/ 共 24 个新类型 + AppContainer（真实 settings/keystore 装配 + 单后台线程 + 主 Looper UiThread）+ 重写为「壳 + 宿主」的 MainActivity，以及 res 下的 dimens/drawable/layout/layout-sw600dp/layout 与 strings；同一棵树上 37 例 T-012 断言与既有 27 例一起全绿。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
- Exit code: 0
- Passing evidence: artifacts/evidence/T-012-green.txt（BUILD SUCCESSFUL、EXITCODE=0）；例数从 JUnit XML 回读而不是控制台措辞：HomePresenterTest 17、HomeViewStateTest 7、KeySnapshotTest 6、SettingsPresenterTest 7，合计 64 例 0 失败 0 跳过。
- Regression evidence: 同一次运行里既有的 generation 四个套件（GenerationSchedulerTest 5、LiveAttemptsTest 3、RecoveryScanTest 3、StreamBatchTest 5）与 FirstTopicCreationRulesTest 11 例仍全绿，说明这次新增没有改动别处行为；lint 由一红一绿固定：带旧 onBackPressed() 覆盖的副本 :app:lintDebug 报 1 error 并 BUILD FAILED，换成框架返回路径后同一命令 0 errors / BUILD SUCCESSFUL（artifacts/evidence/T-012-lint.txt）；Java-only 守门 PASS（13 OK 行，artifacts/evidence/T-012-guard.txt），instrumentation 源码在 --rerun-tasks 下真正重新编译一次并成功（85 tasks 全部执行，artifacts/evidence/T-012-androidtest-compile.txt）。

```text
计数口径：从 app/build/test-results/testDebugUnitTest/TEST-*.xml 的 testsuite 属性回读，
命令 ——
  Get-ChildItem app/build/test-results/testDebugUnitTest -Filter *.xml | ForEach-Object {
    [xml]$x = Get-Content $_.FullName; $x.testsuite } 取 name/tests/failures/errors/skipped
分项之和：17+7+6+7 = 37（T-012 自己的断言）
        5+3+3+5+11 = 27（既有断言，非本次新增）
        37+27 = 64，与 Gradle 汇总的 tests 一致
```

### REFACTOR — optional

- Cleanup: 没有独立的 GREEN 后重构轮次。GREEN 之前做过一轮缺陷修复，它发生在被记录的 GREEN 运行之前、由该运行覆盖：① 首页 Presenter 原本在构造期缓存 keyConfigured，改为一进首页就重新读 vault 与偏好（设置页新存的 Key 必须立刻影响发送守卫）；② 测试夹具原来用 t1/t2 这类假 id，改为规范小写 UUID 常量（Guard.id 只接受规范形式）。
- Regression evidence: 修复后的同一命令绿跑见 artifacts/evidence/T-012-green.txt（64 例 0 失败）；中间态失败运行没有被保留成证据文件（它记录的是一次失败状态），可复核的是这份绿跑与 T-012-red.txt 的一红一绿。

### TDD or sizing exception — only with prior user approval

- Exception type: Not applicable
- Reason: TDD required（本任务按 tdd_mode: required 执行，未申请 TDD 例外）
- User approval: Not applicable
- Alternative verification: Not applicable

规模偏差（已获用户裁定例外，2026-09-24 追认登记）：本切片实测 **1428 非空非注释生产行（Java，26 个文件）+ 884 行资源**，
超 200 行软上限、也超 500 行硬上限（`artifacts/evidence/T-012-sizing.txt`）。
**2026-09-24 用户裁定「事后追认豁免」**（对话内批准；批准落在实现之后，按 `harness-tasks.md` 第 60 行如实记为事后追认）。
剩余风险：单 task 超硬上限约 1.9 倍，是本仓已批豁免中偏差最大的一例；若后续再动首页/设置切片，天然切口是
「首页 Presenter/ViewState」与「设置 Presenter/ViewState + Key 掩码」两半。

## Implementation notes

- 页面仅传 UUID 导航；Key 输入离开页面时清空内存文本。
- 远程字体不得运行时下载；资源授权未确认时使用系统字体。
- 实现补充（本轮事实）：模型与思考的入口按 PRD 6.2.18 point 2/3 放在输入框内，设置页不提供模型项；
  首发/替换/删除 Key 的缓冲区所有权在 Presenter，`finally` 擦除；`KeySnapshot` 是唯一能产生掩码的类。
- 实现补充：`content_measure` 必须声明在 base `values/dimens.xml`——只写在 `values-sw600dp` 会让 lint 的
  `MissingDefaultResource` 失败，进而让 `lintVitalRelease` 与 Java-only 守门的 APK 级校验一起失败。
- 实现补充：`HomePresenter` 每次 `start()` / `onResumed()` 重读 vault 与偏好，否则设置页存下的 Key
  不会影响首页的发送守卫。
- 实现补充（记录修复，2026-09-21）：`MainActivity` 不再重写已废弃的 `onBackPressed()`。API 33+ 用框架的
  `getOnBackInvokedDispatcher().registerOnBackInvokedCallback(...)`，API 28–32 用 `dispatchKeyEvent(KEYCODE_BACK)`
  回落，两条路都收敛到同一个 `onBackRequested()`。不能用 AndroidX 的 `getOnBackPressedDispatcher()`：
  `:app` 主代码的编译类路径解析结果是**零 AndroidX 坐标**（只有四个 project 依赖），而 `androidx.activity`
  会连同 `androidx.core`/`androidx.lifecycle` 一起引入 `org.jetbrains.kotlin:kotlin-stdlib`，被 Java-only 规则
  当场拒绝。证据：`artifacts/evidence/T-012-lint.txt`。

## Verification report

- Verdict: **PASS**（2026-09-24 复核 #3 收口，见下方「复核记录 #3」）
- Evidence: `artifacts/evidence/T-012-red.txt`（EXITCODE=1，100 个编译错误全指向 T-012 缺失类型）、`T-012-green.txt`（EXITCODE=0，64 例 0 失败，其中 T-012 自己 37 例）、`T-012-androidtest-compile.txt`（instrumentation 85 任务真执行编译通过）、`T-012-guard.txt`（PASS 13 OK 行）、`T-012-lint.txt`（onBackPressed 一红一绿，修复后 0 error）、`T-012-sizing.txt`（1428+884 行，豁免已批）、`T-012-static-ui-checks.txt`（Key 不落日志等静态扫描）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除或弱化断言；GREEN 前的两处就地修复（Presenter 每次重读 vault、夹具 UUID 规范化）由 GREEN 运行本身覆盖。
- Gaps: ① 设备判据（401/402/… 真实 HTTP 路径、手机/平板布局、字体放大、44dp 实测）维持 `deferred — emulator not used this round (user instruction 2026-09-21)`，验收框内已逐条注明；② Blocker ②（首页数据路径占位 `PendingHomeWiring`、`submit()` 返回 NOT_WIRED）已由后续任务闭环——首题装配与真实数据路径由 T-032（real-data-path-wiring，verified）落地，历史派生推荐由 T-046（verified）提供 `HOME_RECOMMENDATIONS` 生产者。
- Risks: 不同窗口尺寸与输入法遮挡未实测（deferred）；1428 行的超限切片在后续演进时需按登记的切口拆分。

## 复核记录 #3（独立 verifier，2026-09-24）— 收口

- 规模：唯一待用户裁定的 Blocker ①（1428 生产行 + 884 资源行，无获批例外）已由用户 2026-09-24
  「事后追认豁免」解除，登记见 TDD 节末「规模偏差」段。
- Blocker ②（首页数据路径占位）现状核对（本轮实读）：T-032 已把 `AppContainer.homeData()` 从
  `PendingHomeWiring` 换成真实实现并接通首题提交（T-032 verified，2026-09-24）；T-046 已提供
  `HOME_RECOMMENDATIONS` 生产者（verified）；`TopicDao` 主题列表查询已随 T-032 的数据路径落地。
  三个子缺口 (a)(b)(c) 全部闭环。
- Blocker ③（设备项）：维持 `deferred — emulator not used (user instruction 2026-09-21)`，口径同全批。
- 门禁独立性：2026-09-24 全量 `check` 复跑 EXITCODE=0（`P3-B4-gates-2026-09-24.txt`）；no-kotlin
  guard PASS（`P3-B3-no-kotlin-2026-09-24.txt`）。
- 结论：JVM 判据全绿（37 例 T-012 断言 + 既有回归）+ 规模豁免已批 + 数据路径缺口由 T-032/T-046 闭环
  + 设备项按既定口径 deferred → **verified**。

## Handoff notes

- Completed: `android/app/src/main/java/com/treelearn/app/` 的首页与设置切片——24 个 `ui`/`screen`/`wiring` 类型 + `AppContainer` + 重写的 `MainActivity`（唯一 Activity + `R.id.shell_host` 换屏的壳），以及 `res/` 的 dimens/drawable/layout/layout-sw600dp/strings。按用户指令 2026-09-21 采用「JVM 套件 + instrumentation 只编译」：`:app:testDebugUnitTest` 在仓库外副本上 64 例 0 失败（其中 T-012 自己 37 例），`:app:assembleDebugAndroidTest --rerun-tasks` 全部 85 个任务真执行并成功，`app/build/outputs/androidTest-results/connected/debug` 保持为空。证据：T-012-red / T-012-green / T-012-in-repo-suite / T-012-androidtest-compile / T-012-guard / T-012-sizing / T-012-static-ui-checks / T-012-lint。`android/README.md` 新增同名小节并修正 T-005 小节的过时表述。
- In progress: 无。2026-09-24 复核 #3 收口转 verified（规模豁免已批、Blocker ② 由 T-032/T-046 闭环、设备项 deferred）。
- Blocker: ① **规模偏差需用户裁定**：实测 1428 非空非注释生产行 + 884 行资源，超 500 行硬上限，无用户批准的例外，实现者不得自行追认（`artifacts/evidence/T-012-sizing.txt`）。② **首页数据路径还是占位**：`wiring.PendingHomeWiring` 只真能满足「无主题时的启动推荐兜底」与「空主题列表」，缺三样——(a) `TopicDao` 没有「按最近使用列出主题」的查询，新增 Android SQL 必须真机/SQLite 验证而本轮不用设备，且那是 `:data` lane 的写路径；(b) 首题提交装配（`GenerationCoordinator.submitFirstTopic` + `FirstTopicCreation.create` 都已在，但把请求组装、版本校验、开连接、accepted 回调写库接起来的装配层没有生产消费者，T-008 记录的同一条缺口，且需要注入 API key）；(c) 按历史派生的首页推荐属 T-011 的 `HOME_RECOMMENDATIONS`，而 T-011 本轮仍在实现中（任务 `status: ready`、RED/GREEN 仍 PENDING）。在此之前 `submit()` 返回可见的 `NOT_WIRED` 拒绝，页面明说「本题已保留，本版本还不能发送」。③ 需要真机才能证明的验收项一律记为 `deferred — emulator not used this round (user instruction 2026-09-21)`，既不算 PASS 也不算失败。
- Unblock condition: 用户对规模给裁定（批准例外，或要求把首页数据路径/设置页拆成后续任务）；T-011 落地并提供一个 `HOME_RECOMMENDATIONS` 的生产者后 (c) 消失；`TopicDao` 的主题列表查询在真机上验证后 (a) 消失；装配任务提供首题提交消费者后 (b) 消失；重新允许使用模拟器后手机/平板、字体放大、44dp 与动效四项才能转成 PASS。
- Next steps: 由装配任务把 `AppContainer.homeData()`/`rootQuestionSubmitter()` 从 `PendingHomeWiring` 换成真实实现（端口已经就位，不需要改 Presenter）；`:data` 补主题列表查询；T-011 落地后把推荐条换成 `fromHistory`；模拟器可用后跑 `:app:connectedDebugAndroidTest` 把四条 deferred 逐条转成证据。
- Watch-outs: 页面不得自行重试网络造成重复首题（发送守卫只看 `submitting`，重试属于协调器）；`HomeViewState.Notice` 是分类不是句子，中文字符串只在 `HomeScreen.noticeText` 一处出现，新增错误类别必须同时补 strings 与那个 switch；`KeySnapshot` 的掩码是唯一可以离开该类的形式，任何新页面不得再实现一份；兄弟 lane 会争用共享的 `android/app/build` 与 `.trae/tasks/**`，遇到不是自己源码造成的失败先等 60–120 s 再重跑，**不得**把生产源码移出共享树；共享树上的 `:app` 测试源集目前因 `generation/PositionCommandsTest.java` 引用不存在的 `PositionCommands` 而无法编译（另一个 lane 的在飞 RED），跑全仓测试前先确认这一点是否已消失。