# Filename: T-2026-10-22-scheme-a-release-evidence-059.md
# Codex Harness Task: 方案 A 整跑、真机四屏截图与收口

id: T-2026-10-22-059
title: 方案 A 整跑、真机四屏截图与收口
template_version: 2
task_type: verification
status: verified
parent_requirement: T-2026-10-22-scheme-a-morandi-requirement-052.md
requirements_confirmed: true
generated_by: agent
owner: verifier
owner_lane: verification
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-10-22-scheme-a-library-navigation-057.md, T-2026-10-22-web-morandi-token-consumption-058.md]
tdd_mode: not-applicable

## Requirement collaboration brief

- Parent summary: 052 的 053–058 已分别完成 token 链、字体、外壳/首页、对话、学习库/分支导航与 Web 材质消费；本片只做整跑、真机取证、文档同步与父卡收口，不再改界面结构。
- Problem/background: 各子片各自留了定向证据，但还没有一次「同一工作树、同一命令」的整跑；真机四屏（首页、对话、分支导航、学习库）也尚未截图，用户装 APK 后「和方案 A 不一致」的原始问题需要真机证据闭环。
- Users/scenarios: 单人自用 Android 手机（ALP-AN00，1200×2652，Android 13/SDK 33）；浏览器侧以 `pnpm --dir frontend verify` 为出口。
- Expected outcome: Android 全量 JVM 0 失败、三份 APK 构建与 `verifyNoKotlinDex` 退出码 0；`adb install -r` 保留数据并截到首页/对话/分支导航/学习库四屏；`validate_tasks.py` 与 `run_all_guards.py --tier core` 的判决如实记录；证据目录有可复跑的 README；052 关闭。
- Core flow and reversibility: 只读整跑 + `adb install -r` + 截图 + 文档；不写业务代码。回退为删除新增证据与文档改动。
- Inputs/outputs: 输入是当前工作树（053–058 的实现）；输出是 `artifacts/evidence/android-scheme-a-parity-2026-10-22/` 下的整跑日志、四张真机截图与 README，以及 052 的验收记录。
- State and exception handling: 真机验证只用 `adb install -r`；**禁止运行任何会 `clearTopics()` 的仪器测试或全量 `connectedAndroidTest`**，它们会卸载应用并清掉用户本地数据与 API Key。若导航入口需要真实主题而本机没有，就如实记录缺口，不伪造截图。
- Constraints and trade-offs: 不新增依赖、不改业务代码、不自动提交或推送；证据必须能看出命令、退出码与观察结果；已知红项不得写成 PASS。
- AI recommendation and rationale: 先整跑（JVM/构建/守门），再装机截图，最后文档与父卡收口；截图顺序按「首页 → 对话 → 分支导航 → 学习库」，每屏保留真机状态栏。
- User-confirmation decisions: ① A + 莫兰迪毛玻璃；② 允许内置中文衬线；③ 接不了真实数据就删；④ 保留真机状态栏；⑤ 按 Harness 实现。
- AI-autonomous decisions: ① 不跑仪器测试，改以 JVM + 构建 + `verifyNoKotlinDex` + 真机截图作证据；② 证据 README 记录设备序列号、APK 路径、命令与每屏截图文件名；③ tint 的剩余未消费面作为已知缺口记录，不在收口片补界面。
- Acceptance evidence: 命令退出码与日志；四张 `adb` 截图；`validate_tasks.py` 与 core guard 汇总；`artifacts/metrics/report.md` 重生成。
- Facts: 设备序列号 `AWSS013807000356`；applicationId `com.treelearn`，启动组件 `com.treelearn/com.treelearn.app.MainActivity`；debug APK 在 `android/app/build/outputs/apk/debug/app-debug.apk`；证据目录已存在 053–058 日志；`BottomTabShellInstrumentedTest` 的 `@Before/@After` 会 `clearTopics()`。
- Assumptions: 本机已有真实主题与对话，足以截到分支导航；若入口需要新建主题，用真实输入而不是夹具。
- Unknowns: 导航页在无子分支时是否只显示虚线空态；四屏截图的滚动位置是否能展示方案 A 的关键结构。
- Risks: 误跑仪器测试导致用户数据丢失；截图被其他应用遮挡；真机观感与小屏可辨度仍有主观差异。
- Deferred: 顶部胶囊吸顶、总结渐隐遮罩、平板双栏、其余 tint 的界面消费。
- Non-goals: 改界面、改业务、补新功能、提交或推送。
- Skipped non-critical questions and reason: 未追问四屏截图的具体滚动位置；以能看清顶栏、卡片与分支行为准，截图后在 README 注明。

## Goal

用同一工作树的整跑与真机四屏截图，证明方案 A + 莫兰迪在 Android 与 Web 两条线上都可交付，并关闭父需求 052。

## Scope

### In scope
- Android 全量 JVM（app + data）、`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:assembleRelease`、`verifyNoKotlinDex`。
- token 四方守门与 Web `pnpm --dir frontend verify` 的最终读数复核。
- `adb install -r` 后真机四屏截图（首页、对话、分支导航、学习库）与数据保留确认。
- `artifacts/evidence/android-scheme-a-parity-2026-10-22/README.md` 证据索引。
- `validate_tasks.py`、`run_all_guards.py --tier core`、`.codex/scripts/metrics_report.py` 重生成。
- 文档同步与 052 关闭。

### Out of scope
- 任何界面、业务、依赖或测试语义修改。
- 仪器测试与全量 `connectedAndroidTest`（会清数据）。
- 新功能与 deferred 项。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 本片不改变运行时行为；只取证与收口。用户本机数据必须在 `install -r` 后保留。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 053–058 的实现与各自证据；`MainActivity` 的四屏入口与返回栈；Web `frontend/scripts/verify.mjs`。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: 验证片不改生产代码。

## Documentation impact record

- Changed runtime facts: 无（收口片）。
- Authoritative code/config/migration evidence: 053–058 的任务卡与证据文件；`artifacts/evidence/android-scheme-a-parity-2026-10-22/`。
- Documentation surfaces checked: `android/README.md`、`Docs/04-ux-design/design-system/README.md`、`.codex/roadmap`、`Docs/04-ux-design/parity/screen-inventory.json`。
- Documentation files updated: 证据 README；052 的验收与 Handoff；`Docs/04-ux-design/design-system/README.md` 移除散文里的 token 读数（改由守门脚本打印）；`Docs/04-ux-design/parity/screen-inventory.json` 与 `artifacts/parity/ledger.md` 由生成器重出；如真机发现与文档冲突，先修文档事实再收口。
- Producer/consumer/sibling/compatibility paths checked: `validate_tasks.py`、`run_all_guards.py`、`metrics_report.py`、`check_design_tokens.py`。
- Superseded phrases/examples reverse-searched: 「真机四屏待补」「059 待执行」「052 PENDING」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保留历史结论；本片只追加 2026-10-22 的整跑与截图结果。

## Acceptance criteria

- [x] Android 全量 JVM 0 失败；debug/androidTest/release 三份 APK 构建与 `verifyNoKotlinDex` 退出码 0。
- [x] token 四方守门 0 失败；Web `verify` 16/16 且 Vitest 全绿。
- [x] `adb install -r` 成功且本机数据保留；首页、对话、分支导航、学习库四张真机截图落盘，保留真机状态栏。
- [x] 证据 README 写明设备、APK、命令、退出码与截图索引，任何人可按文复跑。
- [x] `validate_tasks.py` 0 error；`run_all_guards.py --tier core` 判决如实记录；`artifacts/metrics/report.md` 已按当前任务卡重生成。
- [x] 052 的验收项逐条有证据或如实缺口；未自动提交或推送；未新增依赖。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（验证片，不新增行为）
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A（验证片）
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片是验证与收口，不新增生产行为；行为判据来自 053–058 的既有测试与整跑。
- User approval: 用户 2026-10-22 已确认按 Harness 执行 052 拆分与收口。
- Alternative verification: 全量 JVM、构建、Java-only 守门、token 守门、Web verify 与真机截图。

## Implementation notes

- 真机验证只用 `adb install -r`；启动组件是 `com.treelearn/com.treelearn.app.MainActivity`。
- 截图前先确认前台是 Treelearn（`dumpsys activity activities`），避免截到其他应用。
- 四屏顺序：首页 → 对话 → 分支导航 → 学习库；导航页从对话/画布顶栏进入。
- 证据文件命名沿用 `059-*.txt` 与 `059-<screen>.png`。
- 收口前重生成 metrics；`status` 翻 verified 后再补一次。

## Verification report

- Verdict: PASS
- Evidence:
  - Android 全量 JVM：`:app:testDebugUnitTest` 887 tests / 0 failures / 0 errors；`:data:testDebugUnitTest` 65 / 0 / 0，两条任务均以 `--rerun-tasks` 实跑，`BUILD SUCCESSFUL`，退出码 0（`059-jvm-all.txt`）。
  - 三份 APK 与 Java-only：debug / androidTest / release 构建成功；`verifyNoKotlinDex: PASS (3 APK(s) inspected ...)`，退出码 0（`059-android.txt`）。
  - token 四方守门：`OK=10 FAIL=0 SKIPPED=0`，`measured_prototype=69 measured_tokens_json=69 measured_resources=24 measured_css=69`，前端裸色值 0 命中（`059-token-guard.txt`）。
  - Web：`verify steps=16 ok=16 bad=0 fault=0`；Vitest 19 files / 116 tests passed（`058-verify.txt`、`058-vitest-all.txt`）。
  - 真机：`adb install -r` 安装后本机 3 个真实主题与历史保留；首页、对话、分支导航、学习库四张截图均保留真机状态栏（`059-home.png`、`059-chat.png`、`059-navigation.png`、`059-library.png`）；学习库分段读数为「进行中 3 / 已完成 0」，与数据保留一致。
  - 证据索引：`artifacts/evidence/android-scheme-a-parity-2026-10-22/README.md` 记录设备、APK、组件、一键复核命令与逐片证据。
  - core guard 首轮暴露 3 红 1 INCONCLUSIVE，均已按根因修复后复跑：① 设计系统 README 散文写死 token 读数 → 改为只指向守门脚本（`059-sot.txt` 0 failed）；② metrics 报告落后于 2026-10-22 任务卡 → 重生成（`059-metrics.txt` 0 failed）；③ parity 清单因 `ChildBranchPopup` 删除与 `MainActivity` 行号位移而过期 → 按仪器自身指令重生成清单与台账，screens=14 / surfaces=15、台账 29 行判定通过，自检 20/20（`059-inventory-regen.txt`、`059-ledger-regen.txt`、`059-parity-selftest.txt`；修复前诊断保留在 `059-parity-before-fix.txt` 与 `059-guards-core-before-fix.txt`）。
  - 最终 core guard：`RUN-ALL-GUARDS pass=39 fail=0 inconclusive=0 fault=0 timeout=0`，`VERDICT: PASS (subset only — 39/45 entry(ies) judged)`，`EXITCODE=0`（`059-guards-core.txt`）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片未改生产代码与测试断言。修复的三处都是生成物/散文与当前事实脱节：token 读数从散文移除后仍由 `check_design_tokens.py` 唯一提供；metrics 与 parity 清单/台账均由各自的生成器重出，没有手改读数或放宽判据。
- Gaps: 未运行任何仪器测试（`BottomTabShellInstrumentedTest` 会 `clearTopics()`，全量 instrumentation 会卸载应用并清本地数据/API Key）；Pad 横屏截图未采集；`tint-rose`/`tint-lilac`/`tint-sand` 仍无消费者；顶部胶囊吸顶、总结渐隐遮罩、平板双栏仍 deferred。
- Risks: 内置 26MB 字体对 APK 体积与安装时间的影响已在 debug APK 22182465 字节中体现；厂商 ROM 的 tint 色偏与 22dp 节点可辨度仍需长期观察；本轮未覆盖输入法与悬浮 tab bar 的交互回归。

## Handoff notes

- Completed: Android JVM / 构建 / Java-only / token 守门 / Web verify / 真机四屏 / 证据 README / parity 清单与台账重生成 / metrics / 052 收口。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 若要继续，按 deferred 清单挑一项（建议先做顶部胶囊吸顶或 Pad 横屏证据），并为 `tint-rose`/`tint-lilac`/`tint-sand` 找到真实消费面或退役。
- Watch-outs: 永远不要为看界面跑全量 instrumentation；真机只 `adb install -r`；任务卡改动后先重生成 metrics 再跑 core guard；不要手改 `tokens.css`、`screen-inventory.json` 与 `artifacts/parity/ledger.md`。
