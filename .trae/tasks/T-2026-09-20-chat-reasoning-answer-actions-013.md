# Treelearn Harness Task: 实现对话、思考披露与回答操作

id: T-2026-09-20-013
title: 实现对话、思考披露与回答操作
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
depends_on: [T-2026-09-20-generation-coordinator-recovery-008.md, T-2026-09-20-topic-branch-lifecycle-009.md, T-2026-09-20-retry-reset-version-switching-010.md, T-2026-09-20-derived-ai-artifacts-011.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 对话页展示流式 reasoning/answer；思考默认折叠、可展开、灰色，并支持复制、推荐追问/分叉、重试和重置。
- Problem/background: 推理若与回答混排会干扰阅读，若只显示不落库/不复制又破坏信息一致性。
- Users/scenarios: 用户观看生成、展开思考、复制全文、选中文字引用追问、查看子分支或在失败处重试。
- Expected outcome: Chat Presenter/ViewState、消息列表、ReasoningDisclosure、最新回答推荐与回答操作菜单。
- Core flow and reversibility: streaming reasoning/answer 独立渲染；stable 后展示 revision；展开态仅页面会话；操作调用 use case。
- Inputs/outputs: 输入 branch/message/operation/revision 状态；输出用户命令、剪贴板文本和导航参数。
- State and exception handling: partial/failed/interrupted/stable、空 reasoning、版本切换和晚事件均有确定呈现。
- Constraints and trade-offs: 默认折叠；展开内容使用可读次级灰色；复制全文按“思考过程 + 最终回答”组合。
- AI recommendation and rationale: UI 不保存独立 reasoning 副本，始终从 operation/revision ViewState 读取。
- User choice and custom additions: reasoning 展示、持久化、进入后续上下文，默认折叠且颜色更灰。
- AI-autonomous decisions: 展开态不跨重启；空 reasoning 不显示折叠入口。
- User-confirmation decisions: BASE-23～25 与思考交互细节。
- Acceptance evidence: Presenter/UI 自动测试和手机/平板人工可读性检查。
- Facts: v2.5 原型与技术方案 §13 是视觉/行为权威。
- Assumptions: Android 剪贴板可用，复制后由系统管理。
- Unknowns: 无。
- Risks: RecyclerView 复用导致展开态串行、流式更新抖动、灰色对比不足。
- Deferred: Markdown 富文本高级扩展和版本 diff。
- Non-goals: 不实现树画布、全局搜索或派生 AI 生成。
- Skipped non-critical questions and reason: 精确色值由主题 token 确定，但必须通过对比度检查。

## Goal

交付稳定、可读、可操作的对话体验，并完整呈现及保留思考内容。

## Scope

### In scope
- 对话列表、流式状态、思考折叠、版本选择、最新回答推荐双入口、复制/追问/分叉/重试/重置/子分支入口和 UI 测试。

### Out of scope
- 全局导航抽屉、搜索页和树 Canvas。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增对话 UI；保留部分内容、稳定版本和来源锚点语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Coordinator/Repository 生产 ViewState；Topic/Branch/Retry 用例消费命令；手机/平板布局。
- Native bridge or independent-client compatibility/update requirements: 系统剪贴板是唯一平台出口，需展示隐私预期。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 可见状态、思考折叠、复制格式和操作可用性。
- Authoritative code/config/migration evidence: Chat Presenter/ViewState、layout/adapter 与 UI tests。
- Documentation surfaces checked: V3.1 PRD、v2.5 原型、技术方案 §13/§14。
- Documentation files updated: `android/README.md` 新增小节「应用层入口索引（`:app`）—— 对话、思考披露与回答操作（T-013）」，记录了 13 个新类型的职责与不变量、展开集合按 messageId 键、复制格式与两个标题的来源、推荐条只挂最新稳定回答的判定、`PendingChatWiring` 的六条缺口、动作标签用文字而非图标、复用 `HomeViewState.Notice` 与待做的重命名、`tl_ink3` 对比度记录项、deferred 设备项与规模偏差；并修正 T-012 小节里三处已过时的现状表述（T-011 已落地、共享树已恢复可编译）。PRD 与 v2.5 原型本轮不需改：交互、文案与复制格式都与原型一致，没有新增用户可见流程。
- Producer/consumer/sibling/compatibility paths checked: thinking on/off、流式/失败/中断/稳定、旧版、手机/平板。
- Superseded phrases/examples reverse-searched: 思考默认展开、思考不复制、仅答案落库。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保留版本历史。

## Acceptance criteria

> 判定口径：[x] = 已有不依赖设备的证据（JVM 套件或静态检查）；若同一行还有只能靠渲染证明的部分，就在该行内
> 显式标注 deferred，绝不因此算 PASS。[ ] = 该行本轮产生不了验收证据，理由是设备不可用。

- [x] 同一 Presenter/UI 测试命令先 RED 后 GREEN，覆盖 reasoning/answer 流式交错、失败部分内容和 stable revision。
      （`cd android; .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`：RED exit 1 → GREEN exit 0；
      T-013 自己的四个套件 57 例，全仓 20 个套件 198 例 0 失败。逐条见下方 TDD execution record。）
- [x] 有 reasoning 的每条回答默认折叠；点击独立展开/收起；展开文本使用次级灰色且通过可读对比度检查。
      （默认折叠 = `view_reasoning.xml` 的 `reasoning_body` 出厂 `visibility="gone"`；「独立」= 展开集合按
      **messageId** 键，不是列表下标；正文色 `tl_ink2` 实测对比度 5.67:1（`tl_surface`）/ 5.23:1（`tl_bg`），
      过 WCAG AA 4.5:1。**deferred — 真机上点击展开后的实际渲染与字体放大下的可读性，
      emulator not used this round (user instruction 2026-09-21)**。）
- [x] 展开状态不跨重启、不因列表复用串到其他 revision；空 reasoning 不显示无意义入口。
      （展开集合只活在 `ChatViewState` 里、从不落库（不跨重启）；`ChatMessage.canExpandReasoning()` 对「没有
      reasoning 且没有在跑尝试」的回答返回 false，于是整块披露隐藏而不是给一个点了什么都没有的入口。
      列表复用导致的串行只能在真机上观察，属上面同一条 deferred。）
- [x] “复制全文”顺序包含完整思考和最终回答；选择引用、追问、分叉、重试、重置和子分支入口只在合法状态启用。
      （顺序钉在唯一一处 `ChatMessage.copyText`：`思考过程` + 换行 + 完整 reasoning + 空行 + `最终回答` + 换行 +
      回答，没有 reasoning 时复制内容就是回答本身；两个小标题来自 `strings.xml`。操作谓词与 T-010 的
      `PositionCommands` 由 `ChatPositionAgreementTest` 用同一组输入逐一比对，所以「按钮可点」等于「用例接受」。
      剪贴板本身是系统行为，未在设备上验证。）
- [ ] 旋转、切分支、回到页面时生成继续，持久 partial/stable 内容不丢失且无 UI 重复消息。
      **deferred — emulator not used this round (user instruction 2026-09-21)**：旋转与「回到页面」是设备生命周期
      事件，本轮没有设备；`ChatPresenter.onResumed()` 的重读逻辑只有 JVM 证据（`ChatPresenterTest` 覆盖「回来时
      重读且不丢草稿与展开态」），真机行为未证明。
- [x] 只在当前分支最新稳定回答展示 T-011 的 ANSWER_RECOMMENDATIONS；继续追问后旧推荐立即隐藏，推荐当前追问与
      新建分支热区语义保持独立。（隐藏规则一条覆盖三种情形：读到的 `recommendationsRevisionId` 必须等于最新一行的
      `activeRevisionId` 且该行为 stable —— 追问后最新行变成提问、生成中最新行未完成、切版后 id 不再匹配，三者
      都自动不显示（BASE-16），JVM 已断言。**真实推荐数据尚未接入**：`wiring.PendingChatWiring` 是占位，
      见 README 与 Handoff notes 的缺口清单。）
## TDD execution record

### RED — failing test first

- Test/scenario: `app/src/test/java/com/treelearn/app/ui/` 的 4 个新套件（ChatMessageTest 11、ChatPositionAgreementTest 3、ChatPresenterTest 34、ChatViewStateTest 9，共 57 例）加共享夹具 ChatTestDoubles，钉住流式交错、失败保留部分内容、stable 版本、展开态、复制格式与操作可用性；RED 时测试留在原处，只在仓库外的副本里把 13 个 T-013 生产文件与两个被重写的壳文件移走。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
- Exit code: 1
- Observed failure: javac 报 200 行「错误:」，其中 168 行互不相同（找不到符号 108 + 程序包不存在 60），全部出自 T-013 自己的 5 个测试文件，编译阶段即失败，零个测试被执行。
- Failure reason validated: 在 %TEMP%\t013 副本里只把 T-013 的实现移走（13 个新文件 + MainActivity + AppContainer → _excluded-impl/）；同一源集里既有的 generation、搜索、阅读、首页套件仍能编译，且这 200 行错误没有一行来自 T-013 之外的任何文件，因此失败来自「实现不存在」，不是测试写错、也不是环境坏了。

```text
证据文件：artifacts/evidence/T-013-red-isolated.txt（EXITCODE=1，这是 RED/GREEN 的权威 RED）
读数命令（下面每个数都从这一条得出）：
  Get-Content artifacts/evidence/T-013-red-isolated.txt -Encoding UTF8 | Select-String -Pattern '错误:'
原始「错误:」行                    200
其中互不相同的行                   168   （136 行出现 1 次 + 32 行出现 2 次）
按类型（原始行 / 互不相同）
  找不到符号                       124 / 108
  程序包…不存在                     76 /  60
  其它                               0 /   0
  合计                             200 / 168   （124+76=200；108+60=168）
按文件（原始行 / 互不相同）
  ChatMessageTest.java              92 /  68
  ChatTestDoubles.java              48 /  46
  ChatPresenterTest.java            34 /  30
  ChatPositionAgreementTest.java    22 /  20
  ChatViewStateTest.java             4 /   4
  合计                             200 / 168   （92+48+34+22+4=200；68+46+30+20+4=168）
T-013 之外的文件贡献的错误数：0
RED 与 GREEN 两棵副本树之间只有一件事不同：那 15 个 T-013 生产文件是否存在。
```

### GREEN — minimum implementation

- Implementation: 13 个新生产类型（`ui` 的 ChatMessage / ChatViewState / ChatActions / ChatSink / ChatData / ChatCommands / ChatCommandResult / CopyLabels / ChatPresenter，`screen` 的 ChatScreen / ReasoningDisclosure / AnswerActionBar，`wiring` 的 PendingChatWiring），加上重写的 MainActivity（对话页作为换进同一个 `R.id.shell_host` 的第三个状态）与 AppContainer（对话装配 + 从 strings 注入剪贴板标题），以及 7 个新布局（screen_chat、screen_chat_body、layout-sw600dp/screen_chat、item_chat_message、item_chat_suggestion、view_reasoning、view_answer_actions）与 strings.xml 的对话文案；同一棵树上 57 例 T-013 断言全绿。
- Command: cd android; .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
- Exit code: 0
- Passing evidence: artifacts/evidence/T-013-green.txt（BUILD SUCCESSFUL、EXITCODE=0）；例数从 JUnit XML 回读而不是控制台措辞：ChatMessageTest 11、ChatPositionAgreementTest 3、ChatPresenterTest 34、ChatViewStateTest 9，合计 57 例 0 失败 0 错误 0 跳过。
- Regression evidence: 同一次运行 20 个套件合计 198 例 0 失败；共享树本身也绿（artifacts/evidence/T-013-green-repo.txt，同一命令 EXITCODE=0）；定稿后再跑一次合并门禁 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --rerun-tasks`，140 个任务全部执行、EXITCODE=0（artifacts/evidence/T-013-final-gate.txt），其中 lint 的 abortOnError 为真；Java-only 守门 PASS（13 OK 行，artifacts/evidence/T-013-guard.txt）。

```text
计数口径：从 android/app/build/test-results/testDebugUnitTest/TEST-*.xml 的 testsuite 属性回读：
  Get-ChildItem android/app/build/test-results/testDebugUnitTest -Filter TEST-*.xml
    | ForEach-Object { [xml]$x = [IO.File]::ReadAllText($_.FullName); $x.testsuite } 取 name/tests/failures/errors/skipped
分项之和（T-013 自己的四个套件）：11 + 3 + 34 + 9 = 57
全仓：套件数 20，tests 198，failures 0，errors 0，skipped 0
规模：artifacts/evidence/T-013-sizing.txt —— 新增 13 个 Java 文件 1105 判定行 + 7 个布局 479 判定行 = 1584 判定行
      （另：测试 911 行 / 5 文件，以及对 T-012 既有文件的 +131 行扩展；两者单列，不计入 1584）
静态检查：artifacts/evidence/T-013-static-ui-checks.txt —— VERDICT: PASS (8 asserted check(s), 1 open item recorded)
```

### REFACTOR — optional

- Cleanup: 没有独立的 GREEN 后重构轮次。最终门禁之前做过两处收尾，都被最终门禁那次运行覆盖：① `chat_draft` 补 `minHeight="@dimen/touch_min"`——多行输入在空内容时可能低于 44dp 热区，是静态检查量出来的；② `PendingHomeWiring` 的 javadoc 里「T-011 还在飞」的过时表述改成「已落地但尚未接入」，行数不变。
- Regression evidence: 收尾后的最终门禁 EXITCODE=0、140 任务全执行（artifacts/evidence/T-013-final-gate.txt），198 例 0 失败；静态检查与 Java-only 守门在收尾后各重跑一次并 PASS（T-013-static-ui-checks.txt、T-013-guard.txt）。

### TDD or sizing exception — only with prior user approval

- Exception type: 未获批例外（TDD 与规模都没有）
- Reason: TDD 按要求执行，未申请 TDD 例外；规模实测 1584 判定行，超 500 行硬上限，属**未获用户批准**的偏差，必须由用户裁定，实现者不得自行追认。
- User approval: 无
- Alternative verification: 规模偏差的实测口径、逐文件读数与命令见 artifacts/evidence/T-013-sizing.txt；TDD 无替代验证（RED/GREEN 齐全且同命令）。
## Implementation notes

- 展开键使用 revisionId 或 operationId，不使用 adapter position。
- 流式 UI 可合并刷新，但不得改变数据库批次语义；正文不能写 debug 日志。
- 文字选择锚点必须基于稳定 answer，不允许从 reasoning 创建业务分支，除非 PRD 后续明确扩展。

## Verification report

### verifier #3 批量复核（2026-09-24，独立于本任务实现者）

- Verdict: **PASS（设备项 deferred）**。验收标准已按「[x]=有非设备证据 / [ ]=本轮产生不了证据」口径逐行标注（含对比度实测 5.67:1/5.23:1、messageId 键控展开集合、折叠出厂态），TDD 闭环在其 TDD 节（RED exit 1 → GREEN exit 0，自有四套件 57 例）。
- Fresh evidence（2026-09-24 批次 2）: `artifacts/evidence/P3-B1-gates-2026-09-24.txt`（全模块 JVM 套件 + lint + androidTest 编译，BUILD SUCCESSFUL / EXITCODE=0；全库 188 套件 1725 tests / 0 失败）、`P3-B1-no-kotlin-2026-09-24.txt`（EXITCODE=0）。
- 补充核验: 本任务交付的 Presenter/ViewState/Wiring 套件在当前全量套件中 0 失败（ChatPresenterTest 43、ChatViewStateTest 14、ChatWiringTest 28——套件历经后续任务扩展，计数大于历史读数属正常演进，JUnit XML 回读）。
- Gaps（不变，均 deferred）: 真机渲染、字体放大、无障碍实际行为 —— `deferred — emulator not used this round (user instruction 2026-09-21)`。

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 无障碍语义、长推理性能和剪贴板隐私。

## Handoff notes

- Completed: 对话页切片——13 个新生产 Java 类型（`ui` 9 个 + `screen` 3 个 + `wiring` 1 个）、重写的 `MainActivity`/`AppContainer`、7 个新布局（含 `layout-sw600dp/screen_chat.xml` 的阅读栏宽）与 `values/strings.xml` 的对话文案；测试 5 个文件 57 例。按用户指令 2026-09-21 采用「JVM + 只编译」：`:app:testDebugUnitTest` RED exit 1 → GREEN exit 0（T-013 自己 57 例，全仓 198 例 0 失败），定稿后合并门禁 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --rerun-tasks` 140 任务全执行、exit 0，`app/build/outputs/androidtest-results/connected/debug` 保持 0 项。证据：T-013-red-isolated / T-013-green / T-013-green-repo / T-013-final-gate / T-013-androidtest-compile / T-013-guard / T-013-lint / T-013-static-ui-checks / T-013-sizing；`android/README.md` 新增本切片小节。
- In progress: 无。任务停在 `verifying`，等 verifier 与规模裁定。
- Blocker: ① **规模偏差需用户裁定**：新增 1584 非空非注释生产行（Java 1105 / 13 文件 + 资源 479 / 7 布局），超 500 行硬上限且无用户批准的例外，实现者不得自行追认（`artifacts/evidence/T-013-sizing.txt`）。② **对话数据路径整体仍是占位**（`wiring.PendingChatWiring`）：读行、真实推荐条、追问发送、重试/重置命令提交、分叉、切版六条一律返回可见的 `NOT_WIRED`；缺的不是 API（该类 javadoc 逐条点名了已落地的类型），而是「需要 SQLiteDatabase 与一次 provider 调用」的装配层，本轮没有设备可验证它，因此不写未验证的代码。③ **对比度记录项**：展开正文 `tl_ink2` 合格，但披露头等小字用的 `tl_ink3` 只有 3.16:1 / 2.92:1；它是 v2.5 原型带来的调色板 token、两个页面都在读，属 token 级设计裁定，不在本切片就地改单个调用点。④ 需要真机才能证明的验收项一律记为 `deferred — emulator not used this round (user instruction 2026-09-21)`，既不算 PASS 也不算失败。
- Unblock condition: 用户对规模给裁定；装配任务把 `AppContainer` 的对话端口从 `PendingChatWiring` 换成真实实现（端口、Presenter 与 ViewState 都不需要改）；设计 owner 对 `tl_ink3` 给裁定；重新允许使用模拟器后，旋转/切分支/回到页面、真机展开交互、字体放大、平板横屏无重叠与 44dp 实测才能逐条转成 PASS。
- Next steps: 装配任务按 `PendingChatWiring` javadoc 点名的六条接真实实现（其中切版只需一个数据库句柄即可落地）；设计 owner 裁定 `tl_ink3`；`HomeViewState.Notice` → `FailureNotice` 的行为无关重命名（连同对话页偏窄的 `Notice.NOT_WIRED` 文案）；模拟器可用后跑 `:app:connectedDebugAndroidTest` 把 deferred 逐条转成证据。
- Watch-outs: 展开态只能按 messageId 记，任何按 adapter position 记的写法都会在列表重建时串到别的回答上；复制文本含回答与推理正文，**不得**打印、落库或写进任何日志、证据或任务文件；重试与重置只能走 T-010 的用例，UI 层任何直接改 `messages.active_revision_id` 的写法都越界；`ChatViewState.notice` 复用 `HomeViewState.Notice`，新增错误类别要同时补 strings 与那处映射；后续 UI 任务（T-014/T-017）请沿用同一个 `MainActivity` + `R.id.shell_host` 外壳，不要新开 Activity；兄弟 lane 会争用共享的 `android/app/build` 与 `.trae/tasks/**`，遇到不是自己源码造成的失败先等 60–120 s 再重跑，**不得**把生产源码移出共享树。
- [verifier #3 2026-09-24，规模例外补记] 上述「未获批例外」（实测 1584 判定行，超 500 硬上限 217%）于 2026-09-24 由用户裁定「事后追认豁免」收口。批准落在实现之后，按 `harness-tasks.md` 第 60 行如实记为事后追认。剩余风险如实记录：本任务是全仓最大越线（+217%），对话 UI 切片的体量集中在单 task；后续如需拆分，以「推理流渲染」与「回答动作条」为天然切面。本任务状态维持 verified，其余结论不变。
