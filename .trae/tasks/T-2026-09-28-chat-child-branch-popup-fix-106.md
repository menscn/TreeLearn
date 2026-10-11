# Filename: T-2026-09-28-chat-child-branch-popup-fix-106.md
# Treelearn Harness Task: 修复答案子分支弹层无响应

id: T-2026-09-28-106
title: 修复答案子分支弹层无响应
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-28-chat-child-branch-popup-105.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: true
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 对话页答案子分支按钮点击无可见响应；用户已确认列表打开、目标分支切换、异常不静默，采用固定本地夹具，不调用实时 API。
- Problem/background: 点击链经过 `AnswerActionBar`、`ChatPresenter`、`ChatData`、`ChatSink`、`MainActivity`、`ChatScreen` 与 `ChildBranchPopup`；现有代码中空列表会直接返回，渲染时会关闭弹层，单纯测 popup 尺寸不能覆盖整条链。
- Expected outcome: 从实际答案按钮进入可见、可操作的子分支列表；点列表项后导航到其 branch id。
- Core flow and reversibility: 本地答案/分支夹具 → 点击答案分支图标 → 异步读子分支 → 弹层展示 → 点目标 → 当前聊天切换；外点、返回键、离开页面后可安全关闭并重新打开。
- Inputs/outputs: 固定父 branch、message 与至少两个 child branch；弹层显示标题/未读数，点击输出正确 child branch id。
- State and exception handling: 覆盖查询空结果/异常、重复点击、查询期间状态更新、弹层关闭及页面 detach；用户可见路径不得静默无反馈。
- Constraints and trade-offs: Java-only；生产代码目标 ≤200 行；不改 schema、不新增依赖；保留复制/重试/创建分支与平板横屏。
- AI recommendation and rationale: 用生产 View/Presenter/Sink 链的仪器或设备验证定位触点，避免只用 presenter mock 或源代码扫描证明 UI 已工作。
- User choice and custom additions: 用户授权执行；固定数据验证，不使用其在对话中发送的 API Key。
- AI-autonomous decisions: 不相关的状态更新不应让一个仍有效且可交互的弹层意外消失；错误态复用既有对话界面反馈方式。
- User-confirmation decisions: 点击后必须看得到子分支列表，选择后进入对应分支。
- Acceptance evidence: RED/GREEN 定向测试；设备/模拟器交互步骤、画面、logcat；最终 APK SHA-256 与设备安装包一致并能冷启动。
- Facts: 当前工作树相关文件已有暂存与未暂存差异；`ChatPresenter.onChildrenPressed` 查询子分支，`ChatScreen.showChildren` 调用 `ChildBranchPopup.show`。
- Assumptions: 可由本地 SQLite/现有测试夹具构造确定性子分支，无需真实模型服务。
- Unknowns: 真机/模拟器设备与 Android 版本由执行者开工时确认；若没有可用设备，设备验收保持 blocked。
- Risks: 不得覆盖已有 `ChatScreen.java`、`ChildBranchPopup.java`、`ChatPresenter.java` 与 store 差异；无数据库迁移需求。
- Deferred: 子分支弹层视觉改版、跨设备同步及真实 AI 生成。
- Non-goals: 文本选择菜单、高亮和公式渲染任务。
- Skipped non-critical questions and reason: 无；固定夹具和 UI 预期已由用户确认。

## Goal

使答案子分支按钮在本地确定性数据下稳定打开列表并导航到正确分支。

## Scope

### In scope
- 沿点击→数据读取→弹层显示→选择导航路径定位并最小修复根因。
- 添加覆盖生产连接的回归测试及真实设备/模拟器验收记录。
- 必要时同步 `android/README.md` 的当前交互说明。

### Out of scope
- 重新设计分支图标/列表；修改分支领域模型、schema 或后端；改动选区/公式实现。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 点击从无响应改为显示列表并可选分支；外点/返回键关闭；复制、重试、分支创建语义不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `AnswerActionBar` → `ChatScreen.Row` → `ChatPresenter.onChildrenPressed` → `ChatData.children` / `ChatSink.openChildren` → `MainActivity.openChildren` → `ChatScreen.showChildren` → `ChildBranchPopup`；相邻 action bar actions 与 tablet landscape。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用；变更修复用户可见交互。

## Documentation impact record

- Changed runtime facts: 弹层显示、生命周期、错误反馈和选择分支行为。
- Authoritative code/config/migration evidence: 实际修复后的 `AnswerActionBar`、`ChatPresenter`、`ChatScreen`、`ChildBranchPopup` 与对应数据读取实现。
- Documentation surfaces checked: `android/README.md` 与对话/分支导航当前说明。
- Documentation files updated: 无；`android/README.md:733,748,756-759,811-813` 已描述子分支列表由 `ChatSink` 传入、弹层不读库、点行导航、外点/返回关闭；反向搜索未发现“子分支列表未接入”或旧的无响应说明。
- Producer/consumer/sibling/compatibility paths checked: 完整点击链、操作栏相邻按钮、手机与平板横屏。
- Superseded phrases/examples reverse-searched: “子分支列表”“分支按钮”及相关旧行为描述。
- `documentation impact: none` reason and evidence, if applicable: 本次仅修复 `ChatScreen` 弹层生命周期判定，未改变子分支数据契约、入口文案或导航语义；上述 README 现状与代码一致，`rg -n "子分支列表|分支按钮|还没接" android/README.md` 已核对。
- Historical/PRD handling: 仅同步当前行为，不静默改写带日期历史文档或 PRD。

## Acceptance criteria

- [ ] RED 明确复现当前按钮无响应/列表不可见；GREEN 同一命令通过，既有断言保留。
- [ ] 本地夹具下从答案行实际点击显示对应子分支和未读数；点击每个选项进入对应 branch id。
- [ ] 查询空/失败、非破坏性状态更新、重复点击、外点/返回键关闭与页面 detach 均有回归覆盖，无静默失败或幽灵弹层。
- [ ] 真机/模拟器验证截图与步骤证明可见且可点；冷启动无崩溃，安装 APK 与本地产物 SHA-256 一致。
- [ ] `gradlew lint`、相关 JVM/仪器测试、Java-only guard 结果记录；全量测试失败不得隐去或宣称 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `ChildBranchPopupLifecycleInstrumentedTest.childPopupRemainsVisibleAfterNonDestructiveStateRender` fails because `ChatScreen.render` dismisses the popup during an unrelated draft update.
- Command: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.ChildBranchPopupLifecycleInstrumentedTest" --no-build-cache --no-daemon --console=plain`
- Exit code: 1
- Observed failure: Full device suite finished 272 tests with 30 failures; the targeted popup lifecycle assertion failed.
- Failure reason validated: Assertion at `ChildBranchPopupLifecycleInstrumentedTest.java:91` reports the popup is no longer showing after the unrelated render; evidence is `artifacts/evidence/T-2026-09-28-math-renderer-connected-androidtest.txt` lines 161–164 and 288–316.

### GREEN — minimum implementation
- Implementation: `ChatScreen.render` 将无条件 `dismissPopups` 拆为只关闭选区菜单，并仅在锚定消息不存在、非稳定或不再有子分支时关闭子分支弹层；新增 `childrenPopupMessageId` 保持刷新后的可用性判定。`ChildBranchPopupLifecycleInstrumentedTest` 补齐答案按钮→`ChatPresenter`→固定 `ChatData`→弹层显示→刷新保持→点击第二个子分支的真实连接回归。
- Command: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.ChildBranchPopupLifecycleInstrumentedTest" --no-build-cache --no-daemon --console=plain`
- Exit code: 0
- Passing evidence: 设备 `CGA-W00 - 17` 完成 `1` 项，BUILD SUCCESSFUL；证据 `artifacts/evidence/T-2026-09-28-child-branch-popup-green-device.txt`，`EXITCODE=0`。
- Regression evidence: `.\gradlew.bat :app:compileDebugAndroidTestJavaWithJavac --no-daemon`、`scripts/no-kotlin-check.ps1`、`gradlew lint`、`verifyDependencyDirection` 均退出码 0；安装后 `com.treelearn` APK 本地与设备 SHA-256 一致，冷启动 `Status: ok`，首屏 `home_screen` 出现在 UI hierarchy，证据 `artifacts/evidence/T-2026-09-28-startup-screen-ready.txt` 与 `artifacts/evidence/treelearn-startup-ready-2026-09-28.png`。全量 instrumentation 基线仍为 272 项/30 失败，未隐去。

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: 无。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 写代码前逐段阅读工作树中相关文件的 staged 与 unstaged diff，保持无关差异原样；不得 reset、stash、checkout 或整体重写这些文件。
- 任务生产代码变更不超过 200 行；若根因要求扩大，先停下并按任务契约重新拆分，不以 TDD 例外绕过。
- 设备交互需要真实设备/模拟器证据；测试命令、设备信息、观察结果、APK 哈希和停止/清理情况均需记录。
- API Key 不进入任何命令行参数、日志、截图、仓库文件或证据；本任务固定数据验证无需它。
- 2026-09-28 follow-up：`ChildBranchPopup` 在添加行后按固定宽度重新测量，真机内容截图确认标题与两行子分支均已绘制；`ChatPresenter` 对空列表和读取异常统一显示 `STORAGE` notice，避免点击后静默无反馈。

## Verification report

- Verdict: PARTIAL
- Evidence: `artifacts/evidence/T-2026-09-28-child-popup-content-green.txt`；`ChildBranchPopupLifecycleInstrumentedTest` 真机 1/1；`artifacts/evidence/child-branch-popup-content.png` 显示标题与两行内容；`ChatPresenterTest` 全量通过并覆盖空列表/异常反馈；最终 Debug APK 安装成功且设备包哈希与本地产物一致。
- Changed/deleted assertion rationale and equivalent behavior coverage: 保留原有弹层尺寸/显示断言，新增刷新保持与实际按钮链路、列表项 branch id 断言；没有删除用户行为断言。
- Gaps: 独立 verifier 先前结论为 FAIL，尚未对本轮补丁复审；RED/GREEN 任务命令仍未统一；未覆盖横屏/窄屏；整包 instrumentation 的 30 个失败需按既有基线隔离分析。
- Risks: `childrenPopup` 的可用性依赖当前 `ChatMessage.childBranchCount`；若上游状态错误地把稳定消息标成无子分支，弹层会按安全策略关闭。

### Independent verifier addendum (latest)

- Verdict: FAIL on Harness evidence, while the core answer-button → presenter → child-popup path passed on the real device.
- Evidence gaps: RED/GREEN 目标命令未统一；每个子分支、重复点击、外点、返回键、detach 与横屏/窄屏尚未全部覆盖；全量 instrumentation 的 30 项失败仍按既有基线隔离。
- Related targeted evidence: `artifacts/evidence/T-2026-09-28-focused-regression-final.txt` records the recommendation fork and word-menu tests passing alongside the popup and formula regressions.

## Handoff notes

- Completed: RED 证据、生产修复、真实答案按钮链路回归、设备安装与冷启动验证。
- In progress: 独立 verifier 复核与最终证据归档。
- Blocker: 相关源码已有暂存与未暂存差异；执行者必须先审阅并保留。
- Unblock condition: 确认工作树差异归属后按 RED→GREEN 执行。
- Next steps: 完成独立 verifier；若用户要求整包绿，再另行拆分并处理 272/30 基线失败，不把它们归因于本任务。
- Watch-outs: 不覆盖其他工作线，不把编译成功代替实际点击；失败测试批次如实分类。
