# Codex Harness Task: Android 手机版可用性与 JVM 门禁绿（需求父卡）

id: T-2026-10-18-017
title: Android 手机版可用性与 JVM 门禁绿（需求父卡）
template_version: 2
task_type: requirement
status: in_progress
parent_requirement: null
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: android
stack: android
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 用户把本轮目标定为「手机版和 Web 均可正常使用」。Web 线以 Android 现状为 1:1 权威（`T-2026-10-18-web-h5-android-parity-requirement-005.md` 的非目标里明确排除 Android 侧代码），因此手机侧的可用性缺陷与门禁红没有可挂靠的父需求。本轮 review 与真机复现确认了两类事实：① 唯一多分支主题打开「分支导航」整屏读不出（`NavigationData.Node` 要求标题非空，而子分支按设计出生无标题）；② `:app` JVM 套件被 `.codex/ROADMAP.md` §D8 记载的既有 flaky 拖着，「门禁不红」这条判据当前不可机械判定。
- Users/scenarios: 学习者在真机上对话→派生子分支→回分支导航/画布查看树结构；工程侧在改动后需要一条可重复、可判定的测试门禁信号。
- Expected outcome: 真机 `adb install -r` 后各入口真实可用且失败必有可读原因；`:app:testDebugUnitTest` 在无本线改动引入的红之外不再有既有红，使门禁重新可用作判定。
- Core flow and reversibility: 按片推进——显示侧不变量放宽 + 诊断口（016）、既有 flaky 时序（D8 片）、真机仪器既有红（D7 片）。每片独立可回退，不迁移数据、不改 SQL schema、不动 AI 生成路径。
- Inputs/outputs: 输入 `.codex/ROADMAP.md` §D7/§D8 的既有读数与真机 `AWSS013807000356`；输出 `android/app/src/**` 改动、`artifacts/evidence/T-2026-10-18-web-android-slice-*.txt` 与成对的前后截图。
- State and exception handling: 「读不出」「读到的内容为空」「尚未命名」三者必须是不同状态；任何吞掉异常只落到界面提示的 catch 都必须先把原因送到宿主（`TreelearnUi` 日志）。
- Constraints and trade-offs: Java-only 硬约束；不新增依赖；不新增用户可见文案（未命名分支是否给占位文案属产品判断，不在工程片里擅自决定）；真机验证只用 `adb install -r`，不跑会卸载应用、清空本地数据与 API Key 的全量仪器套件。
- AI recommendation and rationale: 先修「让门禁可读」（诊断口 + flaky 清零），再谈逐屏 1:1；否则 Web 复刻继承的是一个未验证的权威，且任何回归都无信号。
- User choice and custom additions: 用户要求按 Harness 一站到底、过程中不请示、每片提交到本地仓库。
- AI-autonomous decisions: ① 本卡作为 Android 手机线的父需求，与 Web 线 005 分开，避免两条线互相把对方的范围吞进来；② 诊断口沿用 sink default 方法而不是新增 Android 类型依赖；③ D7 的真机仪器红按「先读代码/证据判性质、再决定是否本片修」的顺序处理，不为凑绿而降低断言。
- User-confirmation decisions: Harness 执行、一站到底、提交到本地（不推送）。
- Acceptance evidence: 每片一份证据 txt（want/got 表 + `OVERALL_EXITCODE=`）＋ 真机前后截图；Java-only guard PASS；卡片命令与实际执行逐字一致。
- Facts: 016 修复前 `NavigationPresenterTest` 23 例中 2 例红（`IllegalArgumentException: title must not be null`）；修复后 23/23 绿；同一设备重跑，分支列表出现标题为空的那条子分支且 `logcat -s TreelearnUi` 为空。018 之前 `:app` 全量 `846 tests completed, 2 failed`（两条即 §D8 的 `ChatGenerationStageTest`）；018 之后同一条命令 `848 / 0 / 0`。
- Assumptions: §D8 的两条红源于时序假设而非生产逻辑缺陷；若读码后判定为真实缺陷，改片的目标随之改为修缺陷而不是修测试。
- Unknowns: D7 的 11 例真机既有红里有多少属于同一根因；本轮只按 `connectedDebugAndroidTest` 的历史读数登记，未重跑（重跑会卸载应用）。
- Risks: 放宽不变量会让「字段缺失」在别处更隐蔽——缓解是同一改动必带诊断口；flaky 清零若靠放宽超时完成，则只是把问题藏起来。
- Deferred: 写路径（归档/删除/折叠）其余静默 catch 的接线；未命名分支的占位文案产品判断；AI 生成链路的真实 key 验证。
- Non-goals: 不做 Web 复刻、不动 `android/keystore.properties` 与签名配置、不引入 Kotlin、不做远程同步或遥测、不以全量仪器套件作为日常验证手段。
- Skipped non-critical questions and reason: 未追问日志级别与 tag 命名，沿用 `MainActivity` 既有 `TreelearnUi` 约定即可判定。

## Goal

让 Android 手机版在真机上各入口真实可用、失败原因可读，并使 JVM 测试门禁重新成为可判定的绿色信号。

## Scope

### In scope
- 子片 016（分支导航未命名子分支 + 读失败诊断口）。
- 子片 018（§D8 生成阶段机不再依赖线程调度，已交付）。待立片：§D7 真机仪器既有红的定性。
- 证据统一落 `artifacts/evidence/T-2026-10-18-web-android-slice-<NNN>-*.txt`，前后截图同目录。

### Out of scope
- Web（H5）逐屏 1:1（由 005 线负责）、`backend/`、SQL schema 迁移、签名与发布（release skill）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 学习者看到的错误文案与 `Phase.ERROR` 语义不变；被改变的只是「无标题分支不再算读失败」与「读失败一定留下一行日志」。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `android/app/src/main/java/com/treelearn/app/navigation/*`、`.../ui/TreeCanvas*`；消费者 `MainActivity` 的 sink 实现；手机与平板同一套 Presenter，平板横屏行为未变（见 016 证据）。
- Native bridge or independent-client compatibility/update requirements: 无桥接改动；Web 线的 1:1 权威自此包含「无标题分支照常列出」这一条。
- Behavior-neutral non-applicability reason, if applicable: 无。

## Documentation impact record

- Changed runtime facts: 分支导航对「尚未命名的子分支」可读；导航/画布读失败会经 `reportUnreadable` 输出 `TreelearnUi` 日志。
- Authoritative code/config/migration evidence: `NavigationData.Node` 构造器、`NavigationSink`/`TreeCanvasSink` 的 default 方法、`MainActivity` 的 override。
- Documentation surfaces checked: `.codex/rules/android.md`、`.codex/rules/multi-device.md`、`.codex/ROADMAP.md` §D7/§D8、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`。
- Documentation files updated: 仅本卡与 016 子卡；ROADMAP 的 D8 状态在对应片完成后回写。
- Producer/consumer/sibling/compatibility paths checked: Web 线未复制导航读路径逻辑（`frontend/src/**` 无对应实现），因此不需要同步改动。
- Superseded phrases/examples reverse-searched: 「分支导航读不出」在 project memory `defect-nav-tree-unreadable.md` 中记为未修复缺陷，需随本片更新。
- `documentation impact: none` reason and evidence, 若适用：不适用——行为事实确已变化。
- Historical/PRD handling: 历史任务卡与旧 `.trae` 记录不回写。

## Acceptance criteria

- [x] 真机上唯一多分支主题的「分支导航」不再整屏读不出，未命名子分支照常列出（016 前后截图 + `logcat` 空）。
- [x] 每一次导航/画布读失败都在日志里留下一行可判定原因（016 的 sink 断言 + dex 内新日志行存在性）。
- [x] `./gradlew :app:testDebugUnitTest` 无本线引入的红，且 §D8 既有两条红清零，门禁可判绿（片 018：848 例 / 0 失败）。
- [ ] §D7 真机仪器既有红完成定性：确认为环境/时序假设的记录判据，确认是真实缺陷的立片修复。
- [ ] Java-only guard 对每片 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（需求卡，无被测实现；子片各自留 RED）
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 需求卡只承载已确认需求与子片编排。
- User approval: N/A
- Alternative verification: 子片各自给出 RED/GREEN 与真机证据。

## Implementation notes

- 编号说明：016 早于本父卡建立（先有修复、后补需求挂靠），沿用原编号与证据文件名不回改；本卡取 017。
- 门禁口径：`.codex/ROADMAP.md` §D8 明确区分「standalone 跑」与「在套件里跑」两种读数，本片判定以套件读数为准。

## Verification report

- Verdict: PENDING
- Evidence: 子片给出；本卡的复现命令为 `python -X utf8 .codex/scripts/validate_tasks.py` 与 `./gradlew :app:testDebugUnitTest`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（本卡无断言）。
- Gaps: ① §D7 的 11 例既有红尚未逐条定性；② 未命名分支的可见文案仍是空白，待产品判断；③ 平板横屏只用现有截图间接覆盖，未另开平板档实测。
- Risks: 若 D8 定性为「生产逻辑确有并发缺陷」，则门禁绿要等真实修复，本轮不宜放宽断言。

## Handoff notes

- Completed: 016 一片交付（修复 + 诊断口 + 真机前后证据）；018 闭合 §D8，`:app` JVM 门禁 848/0 首次全绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: D7 其余真机红的定性 → 与 Web 线阶段一夹具采集并行推进；在线真实发送一次的生成流观测需用户授权（花额度、写真实数据）。
- Watch-outs: ① 真机验证只用 `adb install -r`，全量仪器套件会卸载应用并清空本地数据与 API Key；② `uiautomator dump` 在有轮播动画的界面会返回过期 dump，判据以 `screencap` 截图为准；③ 任何写进卡片的命令必须与实际执行逐字相同；④ 本工作树 Web 线与本线同时在飞，提交按片但需核对 `git diff --name-only`。
