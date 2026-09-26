# Treelearn Harness Task: 子分支列表与长按划词操作菜单

id: T-2026-09-21-041
title: 子分支列表与长按划词操作菜单
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 300
shared_code_touched: true
depends_on: [T-2026-09-21-real-data-path-wiring-032.md, T-2026-09-20-navigation-archive-delete-014.md, T-2026-09-20-chat-reasoning-answer-actions-013.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: PRD 6.2.19 第 2 条要求长按选中回答文字后弹出**四项**操作菜单（追问 / 新建分支 / 复制 / 全选）且「AI 操作与系统操作分列」，第 4 条要求分叉按钮文案统一为「新建分支」；PRD 6.2.20 要求回答卡片的「子分支列表」能列出该回答直接分出的子分支并跳转。两处目前一块是系统工具条、一块是 Toast 占位。
- Problem/background: `ChatScreen` 只靠 `item_chat_message.xml` 的 `textIsSelectable="true"` 唤起**系统**文本工具条（全仓 `src/main` 对 `OnLongClickListener`/`ActionMode`/`setCustomSelectionActionModeCallback` 零命中），没有应用自己的四项菜单，`strings.xml` 里也没有「全选」；`MainActivity.openChildren` 只弹 `chat_children_pending`（“子分支列表尚未接入本版本”），没有弹层也没有跳转。
- Users/scenarios: 用户长按回答里的一句话，弹出「追问 / 新建分支 / 复制 / 全选」并可按语义分组；点「子分支列表」看到这条回答分出的子分支并跳过去。
- Expected outcome: 长按出现应用自有的四项菜单（分组呈现，热区互不重叠且 ≥ 44×44 dp），四项各自接既有动作；「子分支列表」成为真实弹层，点一条进入该子分支。
- Core flow and reversibility: 追问与复制是既有无副作用动作；新建分支沿用既有 fork 路径（可逆性由该路径保证）；全选只是改变选择范围；子分支列表是只读弹层。
- Inputs/outputs: 输入 `ChatActions` 既有动作（`onQuotePressed`/`onCopyPressed`/`onForkPressed`/`onChildrenPressed`）与 `ChatMessage.hasChildren()`/`childBranchCount`；输出新的划词菜单与子分支弹层，及其布局与文案。
- State and exception handling: 回答仍在生成或生成失败时**不提供「新建分支」**（PRD 6.2.19 异常 1），复制与全选仍可用；未选中文字时点追问要给出可见提示而不是静默；菜单空间不足时调整位置保证主体可见（异常 4）。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX（不能用 `ActionMode` 的 AndroidX 增强）；布局只用 design token 档位；「分叉」文案统一改为「新建分支」。
- AI recommendation and rationale: 用自绘 `PopupWindow` 承载四项菜单而不是系统文本工具条的自定义回调——后者在不引入 AndroidX 的前提下版本差异大，且无法保证「两组分列」的呈现。
- User choice and custom additions: 用户 2026-09-21 明确要求引用追问与子分支列表都必须实现；用户已知本仓不引入 AndroidX。
- AI-autonomous decisions: 菜单用 `PopupWindow` + 一份布局；分组用语义分组标签而不是两个独立弹层。
- User-confirmation decisions: 设备验证由用户装机实测（2026-09-21 决定）。
- Acceptance evidence: RED→GREEN 的 JVM 测试（四项动作各自被正确转发、生成中不给新建分支、子分支列表只列未归档子分支并可跳转）、`:app` 单测与 `.\gradlew.bat check` 退出码 0、Java-only guard PASS、原文扫描证明 `chat_children_pending` 不再有生产者。
- Facts: `AnswerActionBar` 已有 `onQuotePressed/onCopyPressed/onResetPressed/onForkPressed/onChildrenPressed/onVersionPressed` 六个回调；`ChatMessage.hasChildren()` 已实现；`MainActivity.openChildren` 目前只弹 Toast；`strings.xml:143` 是「子分支列表尚未接入本版本。」。
- Assumptions: T-032 已把对话读路径接好，`ChatMessage` 能带上子分支数量。
- Unknowns: 真机上 `PopupWindow` 与系统选择工具条并存的观感（无设备）。
- Risks: 与系统文本工具条同时出现造成两套菜单；把「能否新建分支」判定复制到屏幕；`chat_children_pending` 删除后遗漏引用。
- Deferred: 真机观感与像素证据。
- Non-goals: 不改复制/重置/版本切换的既有行为；不做画布内弹层；不引入 AndroidX。
- Skipped non-critical questions and reason: 菜单项图标沿用既有 `view_answer_actions.xml` 的图标资源，不新增素材。

## Goal

长按回答文字弹出应用自有的四项分组操作菜单，回答卡片的「子分支列表」成为能跳转的真实弹层，两者都不再有占位文案。

## Scope

### In scope
- 新增划词操作菜单：`res/layout/popup_word_action.xml` 与 `screen/` 下的承载类，四项为追问 / 新建分支 / 复制 / 全选，按「AI 操作 / 系统操作」分组。
- `ChatScreen` 接入长按选中路径（保留既有 `selectedText()` 读取），并把四项转发到既有 `ChatActions`。
- 新增「全选」文案与动作：把该条回答全文作为选择范围。
- 新增子分支列表弹层：`res/layout/popup_child_branches.xml` 与承载逻辑；列出该回答直接分出且未归档的子分支，点一条进入该分支。
- `MainActivity.openChildren` 由 Toast 占位改为打开弹层；删除 `chat_children_pending` 文案与其引用。
- 「分叉」文案统一改为「新建分支」（`strings.xml` 与相关 contentDescription）。
- 生成中/失败的回答不提供「新建分支」（复用 `ChatMessage.canFork()` 既有判定，不新写一份）。

### Out of scope
- `ChatPresenter` 的既有动作语义改动（fork/quote/copy 的判定规则）。
- 画布内节点弹层（`view_node_popup.xml` 归画布任务）。
- 任何 schema 改动。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 长按从「只有系统工具条」变为「应用四项菜单」；**保留** `ChatMessage.canQuote()/canFork()/canCopy()` 的既有判定与 `ChatPresenter` 的既有测试结论，不新增第二份资格判定。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是 `ChatMessage` 的谓词与 `ChatPresenter` 的动作；消费者是新菜单与新弹层；宿主是 `MainActivity`。
- Native bridge or independent-client compatibility/update requirements: 文本选择是平台边界，用平台 `TextView` 选择能力，不引入桥。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 长按划词有应用自有四项菜单；子分支列表可跳转；「分叉」文案改为「新建分支」；`chat_children_pending` 文案被移除。
- Authoritative code/config/migration evidence: `res/layout/popup_word_action.xml`、`res/layout/popup_child_branches.xml`、`screen/ChatScreen.java`、`MainActivity.java`、`res/values/strings.xml`、测试输出。
- Documentation surfaces checked: `android/README.md`（`:app` 入口索引与文案索引）、Android 技术方案 §9、PRD 6.2.19/6.2.20、UX 原型划词气泡与子分支弹层。
- Documentation files updated: `android/README.md` 的 `:app` 对话索引补 6 行新类型（`ui.TextSelection` / `ui.WordMenu` / `ui.ChildBranch` / `screen.WordMenuPopup` / `screen.ChildBranchPopup` / `wiring.BranchChildren`）、标题补本切片名、`ui.ChatSink` 与 `screen.AnswerActionBar` 两行的职责描述更正、新增 3 条「交互事实」与 3 条「已知缺口与偏差」（含平台工具条残留风险与本次规模超限）；`screen.AnswerActionBar` 与 `fork` 两条的按钮文案由「分叉」改为「新建分支」。`Docs/06-delivery/TreeLearn_V1_交付报告.md` 第 2.1 节把「划词四项菜单与子分支列表弹层」由「不能用」移入「能用」并给出生产构造点，第 1 节的缺口计数由 6 处改为 5 处、缺口列表同步。
- Producer/consumer/sibling/compatibility paths checked: 长按 → 四项菜单 → 各自动作；子分支列表 → 进入子分支 → 返回。
- Superseded phrases/examples reverse-searched:「子分支列表尚未接入本版本」「分叉」按钮文案。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Device-deferred documentation note: README 与交付报告都把设备侧观感写为 `deferred — 用户自行装机验证（user decision 2026-09-21）`，未写 PASS。
- Historical/PRD handling: PRD 6.2.19 为需求依据，不改写。

## Acceptance criteria

- [x] 同一 JVM 测试命令先 RED 后 GREEN：断言四项动作各自被正确转发；断言生成中/失败的回答不出现「新建分支」；断言子分支列表只列未归档的直接子分支且点击产生进入该分支的意图；断言「全选」把整条回答作为选择范围。RED `artifacts/evidence/T-041-red.txt`（EXITCODE=1）；GREEN `artifacts/evidence/T-041-green-uncached.txt`（EXITCODE=0）。四组断言落点：`ui.WordMenuTest` 7 例（分组、未完成回答上不出现 FORK、可按性、落点翻转与夹取）、`ui.TextSelectionTest` 9 例（长按所在句、全选、空选区、`V3.1` 不被句点切开）、`wiring.BranchChildrenTest` 5 例（只列未归档的直接子分支、两处计数口径一致）、`ui.ChatPresenterTest` 38 例（含三参 `openChildren` 的转发与会话意图）。
- [x] 原文扫描证明 `chat_children_pending` 不再有任何生产者与引用，且 `strings.xml` 中已无该条目。证据 `artifacts/evidence/T-041-scans.txt` 的 scan 1：`android/` 内 hits=0（该扫描同时找标识符与那句旧文案；本轮 README 初稿曾把旧文案写回来，被这条断言当场挡下并已改掉）。
- [x] 资格判定只有一个来源：原文扫描证明屏幕未自行判断能否新建分支，而是复用 `ChatMessage.canFork()`。证据同文件 scan 3：`canFork` 在 `app/src/main` 下 7 处命中，定义恰好 1 处（`ui/ChatMessage.java:146`），其余是读取 / `{@link}` / 注释；`screen/WordMenuPopup.java` 0 处命中（弹层不参与判定）。
- [x] `:app:testDebugUnitTest`、`.\gradlew.bat check`、Java-only guard、设计 token 守门、文档影响记录齐全且退出码 0。证据：`T-041-green-counts.txt`（31 测试类 / 326 例 / 0 failures / 0 errors）、`T-041-check.txt`（`BUILD SUCCESSFUL`）、`T-041-no-kotlin.txt`（`VERDICT: PASS (13 OK line(s))`）、`T-041-design-tokens.txt`（0 checks failed）、`T-041-design-tokens-selftest.txt`。
- [x] 真机观感与像素证据记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得写成 PASS。README 与交付报告两处均如此记录。
- [x] 规模：实测 544 非空非注释生产行，超 500 硬上限 9%；2026-09-24 用户**事后追认豁免**（对话内裁定），例外记录见「TDD or sizing exception」小节；明细见 `artifacts/evidence/T-041-sizing.txt`。

## TDD execution record

### RED — failing test first
- Test/scenario: 四个新行为各自先写 JVM 测试、后写实现——会话语义（四项动作的转发与会话意图）、菜单的出现与可按性、长按取句与全选、子分支列表的成员与计数。测试落地时 `ui.WordMenu`、`ui.TextSelection`、`ui.ChildBranch`、`wiring.BranchChildren`、`screen.WordMenuPopup`、`screen.ChildBranchPopup` 全都还不存在。
- Command: cd D:\Project\Android\Treelearn\android; .\gradlew.bat --no-daemon --console=plain :app:test
- Exit code: 1
- Observed failure: `:app:compileDebugUnitTestJavaWithJavac` 编译失败，javac 自报 86 个错误，全部指向本任务尚未新增的生产类型与方法重载。
- Failure reason validated: 每一条错误的成因都是「生产类型 / 方法重载不存在」，来自测试对目标行为的引用而不是测试自身写错；同一批测试在实现补齐后一次全绿（见 GREEN）。

证据行号取自 `artifacts/evidence/T-041-red.txt`。该文件里 `86 个错误` 出现两次，是 Gradle 在失败摘要中重复了 javac 的 stderr，不是跑了两次。

```text
:70     > Task :app:compileDebugUnitTestJavaWithJavac FAILED
:71     ChatTestDoubles.java:158: 错误: 找不到符号      （:74/:79/:84/:89 均为「符号: 类 ChildBranch」）
:91     WordMenuTest.java:109: 错误: 程序包WordMenu不存在
:404    BranchChildrenTest.java:77: 错误: 找不到符号    （符号: 变量 BranchChildren）
:431    86 个错误
tail    BUILD FAILED in 12s / 52 actionable tasks: 1 executed, 51 up-to-date / EXITCODE=1
```

### GREEN — minimum implementation
- Implementation: 9 个新生产文件（`ui/TextSelection`、`ui/WordMenu`、`ui/ChildBranch`、`wiring/BranchChildren`、`screen/WordMenuPopup`、`screen/ChildBranchPopup`、`res/layout/popup_word_action.xml`、`res/layout/popup_child_branches.xml`、`res/layout/item_child_branch.xml`）加上 4 个既有文件的改动（`screen/ChatScreen`、`ui/ChatSink`、`MainActivity`、`res/values/strings.xml`）。
- Command: cd D:\Project\Android\Treelearn\android; .\gradlew.bat --no-daemon --console=plain :app:test
- Exit code: 0
- Passing evidence: 载重的是 `--rerun-tasks --no-build-cache` 那次复跑——`:app:testDebugUnitTest` 与 `:app:testReleaseUnitTest` 都实际执行，`104 actionable tasks: 104 executed`，`BUILD SUCCESSFUL in 26s`；回读测试 XML = 31 测试类 / 326 例 / 0 failures / 0 errors。只作对照的是同名命令的普通跑（104 个任务全 UP-TO-DATE，不作依据）。文件：`artifacts/evidence/T-041-green-uncached.txt`、`T-041-green-counts.txt`、`T-041-green.txt`。
- Regression evidence: 那次无缓存复跑跑的是 `:app` 全部 326 例而不是只跑新用例，相邻的 `ui.ChatPresenterTest` 38 例与既有对话套件同批全绿；另一次 `.\gradlew.bat check` 退出码 0（`artifacts/evidence/T-041-check.txt`）。

### REFACTOR — optional
- Cleanup: 未做独立重构轮。本轮只做两件收尾：把不再有生产者的占位文案整体删除；把新布局的资源档位与新类注释与既有 `view_answer_actions.xml` / `view_node_popup.xml` 的写法对齐。
- Regression evidence: 每次收尾后重跑同一条 `:app:test`；最后一次为无缓存复跑，见 `artifacts/evidence/T-041-green-uncached.txt`（EXITCODE=0）。

### TDD or sizing exception — only with prior user approval
- Exception type: 规模例外（**事后追认**，2026-09-24 用户批准）
- Reason: 本任务把两个独立交付项装进一个 task——长按划词菜单约 400 行、子分支列表弹层约 175 行，各自都在 500 以内，合起来越线 9%。口径按 `.trae/rules/harness-tasks.md` 第 45 行「新增文件按全部非空非注释行，既有文件只按本次改动行」，判定值 544（新增 9 文件 502 + 既有文件改动 42），物理行 727 只作参考；逐文件明细与命令见 `artifacts/evidence/T-041-sizing.txt`。
- User approval: 2026-09-24 用户在对话中裁定「事后追认豁免」。批准落在实现之后，按 `harness-tasks.md` 第 60 行如实记为事后追认。剩余风险：单 task 越线 9%；后续如需拆分，以「划词菜单 / 子分支弹层」为天然切面。
- Alternative verification: 不适用（本项是规模例外，不是 TDD 例外；TDD 按 required 执行，见上）。

## Implementation notes

- 「能否新建分支」「能否追问」只允许复用 `ChatMessage` 的既有谓词；把判定复制进菜单构建代码会被判 FAIL。
- `chat_children_pending` 文案整体删除，不得以注释形式保留（本仓库失败案例库明确禁止保留无生产者代码）。
- 布局只用 `tokens.json` 档位；新建分支入口的热区必须 ≥ 44×44 dp 且与追问热区不重叠（PRD 6.2.19 第 8 条）。
- 不写任何正文（选中文字、问题、回答）到日志或证据文件。

## Verification report

> 实现者自述（保留原样，未改动）：实现完成、证据齐全，但**不宣称 PASS**——规模一项按 `.trae/rules/harness-tasks.md` 第 45 行是失败项（544 > 500 且无用户批准的例外）。其余四项验收标准均有命令与退出码支撑。最终判定交独立 verifier 与用户。

- Verdict: PARTIAL（独立 verifier，2026-09-22 04:57；只判不实现，未改任何生产文件）。7 条判据 4 PASS / 3 PARTIAL；规模硬项经我复算**逐项等于**实现者的数字（502 + 42 = 544），任务里**没有**用户批准的规模例外记录，按 `harness-tasks.md` 第 45/60 行实现者不得自行追认、verifier 无追认权，故它是未闭合项而非我能收口的项；另发现 D1–D4 四条。**状态留 `verifying`**。
- Evidence: `artifacts/evidence/T-041-verify-scan.txt`（我自己的独立扫描，14 节、逐节带 `command:` 与 `EXITCODE=`，头部 captured-at/cwd；13 份原始 T-041 证据逐条回读）。本次**未跑 Gradle**：同一工作树另一 lane 正在跑 T-040 并持有构建目录，故构建类结论按证据回读 + 复算，未独立复跑（见 Gaps）。 另：报告引用的行号已对今日源码逐条复检，修正 2 处漂移（focusable 是 `PopupWindow` 构造第 4 参、弹层高度 `:55-56`）；复检记录与命令见 `artifacts/evidence/T-041-verify-citations.txt`。

### 逐条判据

1. **四个动作都有生产消费者（逐项给出「菜单项 → 执行者」）— PASS。** 四行都是 `WordMenuPopup` 构造器里挂的 `OnClickListener`（`:75`/`:76`/`:77`/`:78`），没有一项停在屏幕层。① **追问**：`:75` quoteRow → `Listener.onQuoteRequested`（`:39`）→ `ChatScreen.java:583` `Row.onQuoteRequested()` → `:529 onQuotePressed(id)` → `:514 quotedFromAnswer()` → `:505 selectedText()`（读的就是该行 `TextView` 自己的选区，菜单打开时已把整句写进去）→ 非空才 `fire(actions -> actions.onQuotePressed(id, selection))` → `ChatPresenter.java:175 onQuotePressed` → `:181 render(state.withQuote(anchor).withNotice(null))`。引用落在**当前分支**的引用条（`ChatViewState.quote`）；`branchId` 全程是 presenter 自己的分支，`withQuote` 不改分支，下一条问题经 `onSend` 的 `commands.send(branchId, text, quote, ...)` 带出去。一处**措辞差异**：落地是「进引用条、随下一条问题发出」，不是「填进输入框正文」——与 PRD 6.2.19「把该句作为下一条问题的上下文」及既有引用路径一致，记为措辞观察而非缺陷。② **新建分支**：`:76` forkRow → `onForkRequested`（`:42`）→ `ChatScreen.java:588` → `:547 onForkPressed` → `ChatPresenter.java:238`（`canFork()` 守卫）→ `:409 fork()` → `:410 commands.fork(messageId, question)` → `ChatWiring.java:144` → `Branch.child` + `BranchCreation.create`（**既有唯一分叉路径**）；`fork()` 经 `publish(..., mayOpenBranch=true, ...)`，成功后 `sink.openBranch(result.branchId())` 进入新分支。没有第二条创建路径。③ **复制**：`:77` copyRow → `onCopyRequested`（`:45`）→ `ChatScreen.java:593` → `:598 onSelectionCopy` → `fire(onSelectionCopyPressed(selectedText()))` → `ChatPresenter.java:281` → `sink.copyToClipboard(text)` → `MainActivity` 的 `copyToClipboard`：与回答行复制**同一个写入点**，带 `ClipDescription.EXTRA_IS_SENSITIVE`（扫描命中 `EXTRA_IS_SENSITIVE` 与 `setPrimaryClip`），且复制的是**选中那段**而非整条回答（PRD 6.2.19 第 5 条）。④ **全选**：`:78` selectAllRow → `WordMenuPopup.java:148 selectAll()` → `apply(TextSelection.whole(answer.getText().length()))` + `bind()`，只作用于**这一行**的 `TextView`，随后用同一选区重算菜单项。`SELECT_ALL` 在弹层内部被回答、不经 presenter，符合「全选只是改变选择范围」。四条链的每一跳我都在生产源里扫到（`T-041-verify-scan.txt` 第 2–5 节），没有一处只有测试能到。

2. **菜单项可用性由数据驱动，不是写死 — PASS。** `WordMenu.entriesFor`（`WordMenu.java:65`）：QUOTE = `message.canQuote() && selected`；FORK **仅当** `message.canFork()` 时加入条目（`:69`）；COPY = `selected`；SELECT_ALL = `true`。谓词本体是 `ChatMessage.java:138 canCopy` / `:142 canQuote` / `:146 canFork`（三者同为 `isStable()`）。弹层**从不提** `canFork`（扫描：`WordMenuPopup.java` 命中 0），它只把 `entry.enabled` 画成 `setEnabled(...)` + `setAlpha(0.4f)`（`WordMenuPopup.java:139-145`）。**禁用项不可被触发**：`setEnabled(false)` 后框架 `View.onTouchEvent` 在 DISABLED 分支提前返回（照旧消费触摸但不派发点击），`OnClickListener` 因此不会被调用——灰掉的「追问」点不动，与 `ChatPresenter.onQuotePressed` 自己的 `!message.canQuote()` 拒绝是**同一条规则**（框架级行为，实机确认列入 deferred）。注：长按必先算出非空整句，菜单一旦弹出 `selected` 恒真，故「追问变灰」对应的是「回答尚未稳定」而不是「没选中文字」；brief 那句「未选中文字时点追问要给出可见提示」在菜单路径上**不可达但无害**（无选区时 `ChatScreen.java:571-575` 连菜单都不开），我不把它算作缺陷。

3. **子分支弹层 — PARTIAL（见 D1）。** 构造链：`ChatPresenter.java:264 onChildrenPressed`（`hasChildren()` 守卫）→ `:271 ui.post(() -> sink.openChildren(branchId, messageId, children))` → `MainActivity.java:388 openChildren` → `ChatScreen.java:359 showChildren` → `:366 ChildBranchPopup.show(row.childrenAnchor(), children, listener)` → `ChildBranchPopup.java:83 row.setOnClickListener` → `:85 listener.onBranchChosen(child.branchId)` → `MainActivity.java:234 openBranch(branchId)` → `showChat(branchId)`：行点击与画布/首页进入分支**同一条入口**。空子分支：`ChatScreen.java:363` 的 `row == null || children.isEmpty()` 直接 return（不弹空层），且 presenter 侧 `!hasChildren()` 先挡——两处用的是**同一个** `BranchChildren` 规则（见判据 4），所以「有条目但列表为空」在结构上不可达，属纵深防御。**超一屏**：弹层对 `children` 逐个 `list.addView(row)`（`:63`）**无上限**，`popup_child_branches.xml:23-27` 的列表是普通竖向 `LinearLayout`，**无 ScrollView、无 maxHeight**，`PopupWindow` 为 `WRAP_CONTENT`（`:55-56`）并 `showAsDropDown`（`:65`）；对照导航页同一类列表放在 `screen_navigation.xml:6` 的 `ScrollView` 里 → **D1**。行数上限本身**不是**第二套口径（两边都没有 cap），差异在**滚动可达性**。

4. **无第二判定点 / 无生产者重载 / 不可达分支 — PARTIAL（见 D2/D3）。** `canFork` 在生产源码里**定义恰好 1 处**（`ChatMessage.java:146`），其余命中都是读取 / javadoc / 注释（我复跑了 T-041 scan 3 的断言）。「子分支 N」的计数与弹层成员**共用 `BranchChildren`**：`SqliteChatStore.java:227` 用 `countsOf` 算 `childBranchCount`、`:104` 用 `of` 取成员，`hasChildren()`（`ChatMessage.java:170`）只是 `>0` 的读——单一判定点成立。`openChildren` 只有 1 个生产者（`ChatPresenter.java:271`）与 1 个实现（`MainActivity.java:388`），无多余重载。但命中两条形态：**D2**（新增的 `branchId` 参数无消费者）与 **D3**（交付报告规模数字与任务自身证据不一致）。另：`ChatScreen.java:363` 的空列表守卫、`:571` 的空选区守卫都是「先挡再算」的防御，不是新判定；`WordMenu.entriesFor` 里 `canFork()` 被调用两次（`:69`/`:70`）只是重复读同一个纯函数，不构成第二判定点。

5. **事件消费 — PASS（设备侧项 deferred）。** `ChatScreen.java:442 answer.setOnLongClickListener(view -> { openWordMenu(); return true; });` **返回 true** → 长按被本行消费，不会继续走点击，也就不会「长按顺手把行点了」；`:435 setOnTouchListener` 只记 `pressX/pressY` 并 `return false`，不抢事件，长按监听因此仍收得到。`openWordMenu`（`:571`）先算整句、空则 `return`，再 `dismissPopups()`（`:577`）保证同一时刻只有一个弹层；状态重渲染（`:208`）与 `onDetachedFromWindow`（`:386`）都会 `dismissPopups()`，流式刷新不会留下指向旧回答的菜单。两个弹层都 focusable + outside-touchable（`WordMenuPopup.java:79-80`(构造第4参)+`:82`、`ChildBranchPopup.java:55-56`(构造第4参)+`:58`），点外部即关——这也是我判「滚动时菜单停在错位」**不是**缺陷的静态依据（菜单显示时落在菜单外的触摸会先关掉它）。设备侧（长按是否压得住平台文本工具条、44dp 实触、落点与裁切）一律 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不写 PASS。

6. **证据自证 — PARTIAL（见 D4）。** 13 份 T-041 证据**每份**都有 captured-at / cwd / command / `EXITCODE=`（第 12 节逐文件表），且不是只有退出码：`T-041-no-kotlin.txt` 有 `VERDICT: PASS (13 OK line(s))`、`T-041-sizing.txt` 有 `SIZING ASSERTION PASSED` + 逐文件明细、`T-041-scans.txt` 有 8 条断言逐条 OK（取数：`Select-String -Pattern '\sOK\s*$'` 于该文件计 8 行；报告初稿误记 7，本轮已更正） 与 `SCAN ASSERTION PASSED`、`T-041-green-counts.txt` 有逐类 `tests=N failures=0 errors=0`、`T-041-hygiene.txt` 把 repo-wide 的 `EXITCODE=2` 明确标为 informational（决定值是 scoped 的 0）。`T-041-green.txt` 是任务自己声明「全 UP-TO-DATE、不作依据」的对照跑，我按对照读、不据此判分。但**没有任何变异测试证据**（对比 T-042 有三份 `T-042-mutation-*`）→ **D4**：RED 的 `86 个 javac 错误` 证明的是「生产类型尚不存在」，不是「每条断言都能辨真假」；唯一有真实证伪记录的是占位文案那条扫描（README 初稿写回旧文案被当场挡下，见任务正文），比合成变异更强，但只覆盖那一条。

7. **文档同步 — PARTIAL（见 D3）。** `android/README.md:673-675`（入口索引行 `:638-641`） 对子分支弹层的描述（列出直接分出且未归档的子分支、点一行经 `openBranch` 进入、占位文案整体删除）与今天的源码一致；`:678-680` 的「按钮叫「新建分支」，数据仍叫「分叉」」也属实——我的第 11 节扫描：`app/src/main` 命中 0，`android/` 内 14 处命中里 11 处在 README 的数据概念叙述、2 处在 `androidTest` 样本、1 处在 `data/.../TreeLearnSchema.java` 的注释，全在 README 声明的范围之外。`Docs/06-delivery/TreeLearn_V1_交付报告.md:100` 的 T-041 行（`WordMenuPopup` 由 `ChatScreen.Row` 的 `OnLongClickListener` 构造；`ChildBranchPopup` 由 `MainActivity.openChildren` 构造，行由 `wiring.BranchChildren` 判定）与源码一致；`:201` 仍记 T-041 为 `verifying`、设备项写 deferred，正确。唯一不符是 `:197` 与报告第 7 节的规模数字 → **D3**。

- **第五条验收标准（规模，勾选框未勾）— 我复算确认实现者的数字，但批准记录不存在。** 按同一口径重数（`T-041-verify-scan.txt` 第 10 节）：9 个新文件 = 61+56+12+41+135+60+91+21+25 = **502**；四个既有文件的改动行 = 29+1+2+10 = **42**；合计 **544**，与 `T-041-sizing.txt` 自报的 502/42/544 逐项相等。**仅新增 9 文件一项 502 就已越线**，与估算方法无关。任务里没有用户批准的规模例外记录，`harness-tasks.md:41-60` 的既有裁定表里也没有 T-041；按第 45 行「超过 500 行除非已记录用户批准的规模例外，否则为失败」与第 60 行「verifier 对任何例外都无追认权」，我**不追认、也不判它已闭合**。注意第 60 行同时写明规模例外**可以事后追认**（「若批准落在实现之后，如实记为「事后追认」即可成立」），故「用户裁定记录例外」与「拆成两条」都是合法出路，由用户择一。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除的是 `strings.xml` 的占位文案与其唯一调用点（`MainActivity.openChildren` 的 Toast），替代覆盖是弹层的成员/计数用例 + `T-041-scans.txt` 的零引用断言（删掉的是「没有行为」的占位，不是行为）。`ui.ChatSink.openChildren` 扩参后仍由 `ChatPresenter` 单一生产者驱动、`MainActivity` 单一实现读取。**但**：`ChatScreen`/`ChatSink`/`WordMenu*` 等 11 个文件在 git 里是**未跟踪**（第 1 节：只有 `MainActivity.java`/`strings.xml` 是 `M`），我无法用 git 证明既有测试的断言集与改动前等价——只核到当前断言仍在断言原有规则，未出现「为让新代码变绿而放宽」的形态。这一点列入覆盖边界。
- Gaps: ① **未跑 Gradle**（另一 lane 持锁）：`:app:test` / `check` / Java-only guard / 设计 token 守门只回读证据未独立复跑；`app/build` 下的 JUnit XML 会被并行 lane 覆盖（`T-041-green-counts.txt` 里那份 debug XML 无法二次回读）。② **无设备**：长按是否压住平台工具条、44dp 实触、弹层裁切与落点、`PopupWindow` 绝对坐标是否受状态栏 inset 影响，全部只有源码判据，记为 deferred。③ 只判 T-041 的接缝与四项动作；T-040/T-044 的在制品按「存在即算生产调用者」处理，未判它们自身。④ 规模的下限口径无法把 T-041 的改动与共享文件里其它 lane 的改动逐行分离（未跟踪 + 共享文件）。
- Risks: ① `MainActivity.java`（04:40:34）与 `strings.xml`（04:39:37）的 mtime **晚于全部 T-041 证据**（最晚 `T-041-reverify-2026-09-22.txt` 02:44:15），是并行 lane 的在制品；我按今天的树复算，规模 544 与证据一致、`openChildren` 仍在 `:388`，结论未受影响，但后续引用行号会继续漂移。② 平台自己的悬浮工具条仍可能与应用菜单同屏两套（零 AndroidX 下没有等价的自定义 `ActionMode` 回调可关它），只能 deferred。③ 写放大/续期风险不在本任务范围。 ④ 文档行号同样会漂移（交付报告由其它 lane 维护）：报告初稿错引的 :96 / :197 / :645-652 本轮已改到今天的 :100 / :201 / :673-675，复检见 `artifacts/evidence/T-041-verify-citations.txt`；引用文档时请按内容定位、不要只认行号。

### Findings

- **D1 [P2，需拍板] 子分支弹层没有滚动容器、也没有行数上限，超过一屏的行不可达。** `ChildBranchPopup.show` 把 `children` **全部** `addView` 进 `popup_child_branches.xml:23-27` 的普通 `LinearLayout`（`:63`），`PopupWindow` 用 `WRAP_CONTENT` 高度（`:55-56`）并 `showAsDropDown`（`:65`）；列表无 `ScrollView`、无 `maxHeight`、无 cap，`BranchChildren.of` 也不截断。对照导航页：`screen_navigation.xml:6` 用 `ScrollView` 包住同一类列表。`PopupWindow` 默认把窗口裁到屏幕内且自身不滚动，故子分支多到超过一屏时**底部的行既看不到也点不到**（阈值取决于设备高度，属设备侧；但「缺滚动容器」是静态事实）。**建议 + 理由 + 代价**：① 给 `child_branches_list` 外套 `ScrollView` 并给弹层一个 `maxHeight`（用既有 `tokens.json` 档位），约 10 行 XML + 1 行 `setHeight`，收益是与导航页口径一致、任何分支数都可达；② 或给弹层设一个与导航页一致的显示上限并在标题写「N 条」，代价是新增一处 cap 判定（还要说清「列表短了但计数仍是 N」怎么措辞），不如 ① 干净。二者需需求方拍板，verifier 不做选择。

- **D2 [P3] `ChatSink.openChildren` 新增的 `branchId` 参数没有消费者。** `ChatPresenter.java:271` 把 `branchId` 传下去，而 `MainActivity.java:388-390` 的实现体只用了 `messageId`/`children`（`chatScreen.showChildren(messageId, children, this::openBranch)`），`branchId` 全程未被读。任务正文把这次扩参解释为「分支在调用前就读好，屏幕因此不在 UI 线程读库」——那句描述的是 `List<ChildBranch>` 参数，不是 `branchId`。于是端口签名里多了一个**生产者有、消费者无**的参数，属本仓失败案例库的「无生产者的参数」形态（当前不产生错误行为）。**建议 + 理由 + 代价**：删掉该参数（`ChatSink`/`ChatPresenter`/`MainActivity`/`ChatTestDoubles` 四处），或由作者说明它服务谁；理由是端口参数即契约，留一个没人读的参数会让下一个实现者以为这里需要 `branchId`；代价约 4 行签名 + 重跑一次 `:app` 单测。

- **D3 [P3] 交付报告的 T-041 规模数字与任务自己的证据不一致。** `Docs/06-delivery/TreeLearn_V1_交付报告.md:201` 写「实测规模 534~549 行（两口径）」，报告第 7 节解释为「实现者给的 496（新增 5 文件）+ 38（改动行）= 534；交付复核者按同一规则重数得 511 + 38 = 549」；而任务自己的 `T-041-sizing.txt` 与我的独立复算都是「**9 个新文件 502** + 改动 42 = **544**」。两处的**文件数（5 vs 9）与三个数字全不同**。越线结论两边一致、不影响判定，但按本仓「每个数字都要写成从哪个文件哪行得到」的要求，读者会以为 T-041 只新增了 5 个文件。**建议 + 理由 + 代价**：把交付报告里的 T-041 数字改成与 `T-041-sizing.txt` 同源（502/42/544），或注明与该口径的差异来自哪里；理由是同一任务出现两套「是否越线」的读数正是 `harness-tasks.md` 第 47 行明令禁止的；代价改 2 行文档，零代码。

- **D4 [P2，需拍板] 承担行为锁定角色的新断言没有变异测试证据。** `WordMenuTest`(7) / `TextSelectionTest`(9) / `BranchChildrenTest`(5) / `ChatPresenterTest`（含三参 `openChildren` 转发）是「四项动作各自转发正确 / 未完成回答不出现 FORK / 弹层只列未归档直接子分支 / 全选取整条」的**唯一**证据；本轮**只有** RED（`86 个 javac 错误`，`EXITCODE=1`）与 GREEN（`104 executed`、`EXITCODE=0`），**没有任何 `T-041-mutation-*`**。RED 的编译错误只证明「生产类型不存在」，不证明「每条断言能区分行为」；按本仓失败案例库「承担唯一替代证据或缺陷回归锁角色的断言必须做变异测试」，这是缺口（同轮 T-042 做了 3 次变异并留证）。**建议 + 理由 + 代价**：① 补 2–3 次变异（例如去掉 `entriesFor` 的 `if (message.canFork())`、去掉 `BranchChildren.isChild` 的 `!isArchived()`、把 `selectAll` 改成 `TextSelection.of(0, 1)`），确认**只有**目标用例转红再还原落盘，代价约 3 次 `:app:test`（每次十几秒）+ 3 个证据文件；② 或由用户裁定「JVM 覆盖已足够、豁免变异」，代价零代码，但要承认这条规则在 T-041 上不适用。verifier 不做选择。

### 覆盖边界

判定的是 T-041 的四项划词动作、子分支弹层、占位文案删除、`canFork` 单一判定点、规模与文档同步；**未**判 T-032/T-040/T-044 自身的完成度，也未判实机表现（长按压不压得住平台工具条、44dp 实触、弹层裁切、`PopupWindow` 坐标是否受系统栏影响）——后者一律 `deferred — 用户自行装机验证（user decision 2026-09-21）`。13 份原始证据只做**回读与复算**（逐条复算的量：规模 544、占位零生产者、`canFork` 单一定义、`app/src/main` 无按钮词），未独立复跑任何 Gradle 目标。`MainActivity.java` / `strings.xml` 在证据之后被并行 lane 改过，本报告的行号以**今天**的树为准。

## Handoff notes

- Completed: 9 个新生产文件 + 4 个既有文件改动全部落地并通过 `:app` 全量单测（326 例）；`chat_children_pending` 与其唯一调用点已删除并用扫描证明零生产者；「分叉」按钮文案与 `contentDescription` 已统一为「新建分支」；RED→GREEN 与 `check` / Java-only guard / 设计 token 守门证据齐全；README 与交付报告的当前态表述已同步。
- In progress: 无。
- Blocker: 规模超 500 硬上限且无用户批准（判定值 544，`artifacts/evidence/T-041-sizing.txt`）。数字不依赖估算方法：(A) 组 9 个新文件本身就是 502 行，已越线，改动行是额外而非关键项。
- Unblock condition: 用户明确裁定——把本任务拆成「长按划词菜单」与「子分支列表弹层」两条（这是规模里天然的切面），或记录一条规模例外（需批准日期/批准人/实测行数与超出比例/剩余风险）。
- Next steps: 交独立 verifier 逐项复核；随后由用户裁定规模项；`Docs/06-delivery` 与 README 已就绪，无需二次改动。
- Watch-outs: ① 不要新写第二份「能否新建分支」判定——`WordMenu.entriesFor` 读 `ChatMessage.canFork()` 是唯一判定点，菜单里出现 `FORK` 这件事本身就是该谓词的结果；② 占位文案不得以任何形式（含注释、文档引用原文）回到 `android/`，`T-041-scans.txt` 的 scan 1 会当场挡下；③ 本轮的断言口径是 JVM：任何需要真机的结论（落点、裁切、平台工具条并存、44dp 实触）都只能写 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得写成 PASS；④ 本任务**没有**改 `ChatPresenter` 的 fork/quote/copy 判定，也没有改任何 schema。；⑤ `android/app/build/tmp/compileReleaseJavaWithJavac/compileTransaction/stash-dir/MainActivity.class.uniqueId15` 是一份 javac 编译残留，里面还带着被删文案——`build/` 不入库、它不是源码生产者，因此任何「零生产者」扫描都必须排除 `build/`，且交付前的 APK 必须重建后才算数。
- 遥测决定与理由: 本切片**不发射任何遥测**。划词菜单与子分支弹层是纯 UI 手势与只读列出，既不改状态也不产生外部调用；本仓遥测出口 `wiring.LogcatTelemetry` 只记生成状态与计数、明确不记正文，而这里没有任何新的状态迁移可记。新增日志只会把「用户选了哪句话」这类正文风险引进来，收益为零。
- 流程观察（如实记录，不算偏差）: 本任务文件交到我手上时 front matter 是 `status: ready`，而任务简报说它已是 `in_progress`。以文件为准（`ready`），本轮从 `ready` 直接推进到 `verifying`，期间没有第二个写者。
- 偏差（已记录，见下）: PRD 6.2.19 没有定义「词」，长按落地为**整句**选择；PRD 同条第 8 项的「44×44 圆形热区」在移动原型里没有对应物，落地为 44dp 高的整宽行。

## Verification report — Fix round 2 复验（verifier #3，2026-09-24）

- Verdict: PASS（独立复核，针对 2026-09-22 verifier 报告的 D1–D4 与规模项的修复轮）。
- D1（子分支弹层超一屏不可达）→ 闭合。`popup_child_branches.xml` 的 `child_branches_list` 已包进 `ScrollView`；`dimens.xml` 新增 `popup_max_height`（320dp）；`ChildBranchPopup` 用 `content.measure(...)` + `popup.setHeight(Math.min(content.getMeasuredHeight(), maxPx))` 限高。超一屏的行可滚动到达。
- D2（`ChatSink.openChildren` 的死参数 `branchId`）→ 闭合。签名改为 `(String messageId, List<ChildBranch> children)`，`ChatPresenter` / `MainActivity` / `ChatTestDoubles` 同步；`ChatPresenterTest` 两条断言改为 messageId 形态。
- D3（交付报告 T-041 规模数字与任务证据不一致）→ 闭合。报告数字已改为与 `T-041-sizing.txt` 同源的 544（超 9%）。
- D4（行为锁定断言无变异证据）→ 闭合。M1（`BranchChildren.isChild` 去掉 `!isArchived()` → 弹层成员相关用例转红，EXITCODE=1）与 M2（`WordMenu.entriesFor` 去掉 `canFork()` 门 → `aHalfFinishedAnswerIsNotOfferedANewBranchAtAll` 精准转红，EXITCODE=1）各自只打红目标用例，还原后绿。证据 `artifacts/evidence/T-041-mutation-2026-09-24.txt`。
- 规模项 → 2026-09-24 用户**事后追认豁免**（见「TDD or sizing exception」小节）。
- 门禁（本轮实测，非回读旧证据）：`./gradlew --no-daemon --console=plain check` EXITCODE=0（`artifacts/evidence/P3-B3-gates-2026-09-24.txt`）；Java-only guard `VERDICT: PASS (13 OK line(s))`、EXITCODE=0（`artifacts/evidence/P3-B3-no-kotlin-2026-09-24.txt`）。
- 设备侧仍 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不计 PASS/FAIL。
