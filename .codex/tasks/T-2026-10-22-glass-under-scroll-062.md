# Filename: T-2026-10-22-glass-under-scroll-062.md
# Codex Harness Task: 列表内容从悬浮胶囊与顶栏下方经过

id: T-2026-10-22-062
title: 列表内容从悬浮胶囊与顶栏下方经过
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-glass-depth-requirement-060.md
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: android
stack: android
estimated_production_lines: 40
shared_code_touched: true
depends_on: [T-2026-10-22-glass-material-depth-061.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 060 实测的第④条根因：`MainActivity#swap` 在 tab bar 可见时给 `shell_host` 加 90dp `tab_bar_clearance` 底部内边距，内容被顶在胶囊之上，永远不从玻璃下方经过。通透度没有可透之物，是「不像浮在上面」里观感权重最高的一项。
- Users/scenarios: ALP-AN00 首页与学习库滚动、对话滚动；平板 `layout-sw600dp` 变体；输入法顶起聊天 dock 时。
- Expected outcome: 列表内容滑到胶囊/顶栏下方时被玻璃压住并透出模糊轮廓，末条在滚到底时仍完整可见，输入法与返回栈行为不变。
- Core flow and reversibility: 宿主让位改为 0 → 各屏滚动容器改为 `clipToPadding=false` + 底部留白 → 悬浮面 elevation 保证绘制顺序在内容之上 → 真机滚动截帧跑 J4。撤销只需恢复宿主内边距。
- Inputs/outputs: 输入是 061 已完成的材质与投影；输出是让位归属改变（宿主 → 滚动容器）、绘制顺序与各屏留白断言、滚动截帧证据。
- State and exception handling: 草稿、滚动位置、返回栈、选中态、空态、输入法规避、无障碍焦点不变；tab bar 隐藏时（全屏编辑器等）留白必须同步为 0，否则末条悬空。
- Constraints and trade-offs: Java-only；不改 View id；`tab_bar_clearance` 仍只能有一处权威数值；命中区域不变；不得为通过判据把玻璃改成不透明。
- AI recommendation and rationale: 保留「单一 clearance 数值」，只把应用它的对象从宿主换成滚动容器，避免每屏各写一份底部留白。
- User-confirmation decisions: 用户 2026-10-22 明确「接受 B 的滚动结构改动」。
- AI-autonomous decisions: ① 留白由每屏滚动容器的 `clipToPadding=false` + 底部 padding 承担；② 顶栏如与内容重叠同样处理，不重叠则不动；③ 若某屏无法在不改业务的前提下让位，记录为该片未覆盖项而非强行改造。
- Acceptance evidence: J4（滚动时胶囊区域内同一像素行亮度变化 ≥ 8 L）+ 末条可见性截图 + 既有布局/外壳测试全绿；证据落 `artifacts/evidence/android-glass-depth-2026-10-22/`。
- Facts: 让位代码在 `app/MainActivity.java` 引用 `R.dimen.tab_bar_clearance` 处；胶囊在 `screen_tab_bar.xml` 与 `layout-sw600dp/screen_tab_bar.xml`，背景 `bg_glass_bar`，`android:elevation="@dimen/glass_capsule_elevation"`；各屏 body 为 `screen_home_body.xml`、`screen_library_body.xml`、`screen_chat_body.xml`、`screen_navigation_body.xml` 等。
- Assumptions: `clipToPadding=false` 在 ScrollView 上足以让末条滚到胶囊上方可见。
- Unknowns: 输入法顶起时 dock 与胶囊留白的叠加表现；厂商 ROM 上 elevation 绘制顺序的边角差异。
- Risks: 末条被胶囊永久遮住；tab bar 隐藏时留白未回收造成底部空洞；滚动位置恢复与选中态跟随被打断；既有断言依赖宿主 padding。
- Deferred: 顶栏滚动渐深（滚过时玻璃加浓）作为后续可选增量。
- Non-goals: 不改材质数值（061）、不改业务逻辑/数据库/接口/依赖、不自动提交或推送。
- Skipped non-critical questions and reason: 未追问是否给胶囊下方内容加「压暗遮罩」；先验证纯透过效果，避免与 061 的投影叠加成脏边。

## Goal

列表内容真的从悬浮玻璃下方经过，且末条可见性、输入法与返回栈行为不回退。

## Scope

### In scope
- `MainActivity#swap` 的让位归属调整与各屏滚动容器 `clipToPadding`/底部留白。
- 悬浮面绘制顺序（elevation）保障。
- J4 滚动截帧判据与末条可见性取证；既有外壳与布局断言回归。

### Out of scope
- 材质数值与投影（061）；实时模糊；业务逻辑。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图改变滚动容器可视裁切与留白归属；必须保持末条完整可见、滚动位置与选中态恢复、输入法顶起、返回栈与无障碍焦点顺序。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `MainActivity#swap`/`selectTab` 与 `R.dimen.tab_bar_clearance`；消费者是每屏滚动容器、`layout-sw600dp` 变体、全屏编辑器覆盖层（tab bar 隐藏态）。
- Native bridge or independent-client compatibility/update requirements: 不涉及原生桥或夹具。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本片刻意改变滚动呈现边界，属行为相关。

## Documentation impact record

- Changed runtime facts: tab bar 让位从宿主内边距改为滚动容器留白；内容可在玻璃下方经过。
- Authoritative code/config/migration evidence: `MainActivity`、`activity_main.xml`、`dimens.xml`、各 `screen_*_body.xml` 与其 `layout-sw600dp` 变体。
- Documentation surfaces checked: `android/README.md`、`.codex/rules/multi-device.md`、`.codex/rules/regression.md`。
- Documentation files updated: `android/README.md` 的外壳与让位条目。
- Producer/consumer/sibling/compatibility paths checked: tab bar 可见性开关、全屏编辑器隐藏态、平板变体。
- Superseded phrases reverse-searched: 「宿主底部内边距让位」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: PRD 6.2.16 的悬浮 tab bar 条款不变，只改实现归属。

## Acceptance criteria

- [x] J4：滚动时胶囊区域内同一像素行亮度变化 ≥ 8 L（真机截帧实测，`animator_duration_scale` 无关）。（0.0 L → 35.1 L，同一 box `78,2385..1121,2586`）
- [x] 末条在滚到底时完整可见，不被胶囊遮住；tab bar 隐藏态无底部空洞。（`062-after-home-bottom.png` 末条停在胶囊上方且投影完整；`062-chat-bar-hidden.png` 二屏无留白空洞）
- [x] 输入法顶起、滚动位置恢复、选中态与返回栈行为不变（定向测试 + 真机复核）。（`062-chat-ime.png` 键盘顶起输入坞、玻璃阅读区仍可见、底部无空洞；897 例 JVM 全绿覆盖既有选中态/返回栈测试。滚动位置恢复未做真机逐帧复核，记在 Gaps）
- [x] 既有外壳/布局/parity 测试全绿；`:app:testDebugUnitTest` 全绿；core 守门无红。（897/0/0；`run_all_guards.py --tier core` rc=0）
- [x] 未新增依赖、未改材质数值、未自动提交或推送。（J1–J3 与 061 逐位一致即材质未动的读数证据）

## TDD execution record

### RED — failing test first
- Test/scenario: ① `screen.TabBarUnderScrollLayoutTest` 四条（留白归属在滚动容器 / 宿主不再自留 / 只有两屏付留白 / 数值不写成字面量）；② J4 真机截帧判据（胶囊区域内同一像素行在两帧之间的亮度变化）。
- Command: `./gradlew :app:testDebugUnitTest --tests "*TabBarUnderScrollLayoutTest*" --tests "*SchemeAHomeShellLayoutTest*"`
- Exit code: 1
- Transcript（逐字，定向 JVM）: `TabBarUnderScrollLayoutTest > theHostStopsPaddingItselfForTheBar FAILED` / `java.lang.AssertionError at TabBarUnderScrollLayoutTest.java:68` / `4 tests completed, 3 failed` / `BUILD FAILED in 2s`。
- Transcript（逐字，像素判据）: `python -X utf8 .codex/scripts/dev/check_glass_depth.py --shot 062-baseline-home-top.png --band bottom --drift-before 062-baseline-home-top.png --drift-after 062-baseline-home-scrolled.png` → `GLASS-DEPTH shot=062-baseline-home-top.png band=bottom box=78,2385..1121,2586 scene_pixels=14400` / `FAIL J4 玻璃下内容经过 = 0.0 L 变化（需 ≥ 8）` / `VERDICT: FAIL (1 条未成立)` / `EXITCODE=1`。两帧为 061 build 上「滚动前 / 一次 swipe 之后」的实拍。
- Observed failure: 滚动前后胶囊取区内亮度**一位不动**（0.0 L），即玻璃下方永远没有东西经过；JVM 三条红在结构还没改（宿主仍写 `host.setPadding(...)`、滚动容器仍无 `clipToPadding`/留白）。
- Failure reason validated: 第四条 `theClearanceIsNeverWrittenAsALiteral` 在同一批里**是绿的**，说明这套测试不是恒红——它精确描述「还没做」而不是「写错了」。J4 读数的 box 与 061 逐位相同（`78,2385..1121,2586`），排除了读数落在别处的可能。留档：`artifacts/evidence/android-glass-depth-2026-10-22/062-baseline-reading.txt`。

### GREEN — minimum implementation
- Implementation: ① `MainActivity#swap` 删除 `host.setPadding(0, 0, 0, barClearance)`，只保留 `firstLevel` 一个谓词决定胶囊显隐；② `layout/screen_home.xml`、`layout/screen_library.xml` 及其 `layout-sw600dp/` 变体的 ScrollView 加 `android:clipToPadding="false"` + `android:paddingBottom="@dimen/tab_bar_clearance"`；③ `activity_main.xml` 头注释改为说明留白由两屏自己付；④ `SchemeAHomeShellLayoutTest` 的宿主内边距断言改写（见 Verification report）；⑤ 仪器 `check_glass_depth.py` 修两处失明（见 Implementation notes）。材质数值一个没动。
- Command: `./gradlew :app:testDebugUnitTest --tests "*TabBarUnderScrollLayoutTest*" --tests "*SchemeAHomeShellLayoutTest*"`
- Exit code: 0
- Passing evidence: 同一命令重跑 → `BUILD SUCCESSFUL in 2s`，`TEST-…TabBarUnderScrollLayoutTest.xml` 4 例 0 失败 0 错误、`TEST-…SchemeAHomeShellLayoutTest.xml` 8 例 0 失败 0 错误（两份报告同一时间戳 22:28 落盘）。像素判据：`python -X utf8 .codex/scripts/dev/check_glass_depth.py --shot 062-after-home-bottom.png --band bottom --drift-before 062-after-home-top.png --drift-after 062-after-home-bottom.png` → `OK J4 玻璃下内容经过 = 35.1 L 变化（需 ≥ 8）`、`EXITCODE=0`，同一 box `78,2385..1121,2586`，J1–J3 与 061 逐位一致（19.6 / 12.4 / 26.2 / 13.0）——**只有 J4 从 0.0 变 35.1**，正是本片唯一意图改变的那件事。
- Regression evidence: `./gradlew :app:assembleDebug :app:testDebugUnitTest` → `tests 897 failures 0 skipped 0`；`adb -s AWSS013807000356 install -r` 后实拍（未跑 instrumentation，避免清真机数据）；`check_glass_depth.py --selftest` → `cases=11 failures=0`；`run_all_guards.py --tier core` rc=0（含 `check_design_tokens`、`check_run_all_guards --selftest cases=33`、`check_pointer_liveness`）。留档 `062-after-reading.txt` 与三帧滚动截图；滚到底帧里末条「2 个分支 · 6天前」完整停在胶囊上方且投影未被吃掉。胶囊命中未回退：受控「重启→截图→点击→截图」测试确认 `062-chat-frame-a.png`/`062-chat-frame-b.png` 两张都是点击后进入的对话页（此前一度把库页误标为对话页，已改名纠正）；tab bar 隐藏态与输入法顶起分别取证 `062-chat-bar-hidden.png`、`062-chat-ime.png`；学习库 `062-library-rest.png`、`062-library-after-swipe.png` 两帧几乎相同，实测确认当前 3 个主题不构成滚动区间（见 Gaps）。

### REFACTOR — optional
- Cleanup: `TabBarUnderScrollLayoutTest` 内层循环变量 `variant` 与外层 `String[] variant` 重名导致 6 个编译错误（`File无法转换为String`），改名为 `file`；两条测试的 javadoc 从「做什么」改写为「为什么留白必须归滚动容器」。
- Regression evidence: 同一 JVM 套件保持全绿（897 例 0 失败）。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 只在滚动容器上设 `clipToPadding=false`，避免在 `main_root`/`shell_host` 上开全局裁切豁免。
- 留白数值仍引用 `@dimen/tab_bar_clearance`，不在每屏写死 dp（`theClearanceIsNeverWrittenAsALiteral` 锁住）。
- tab bar 隐藏态与平板变体各自取证一次，不靠推定。
- 实际生产改动量：`MainActivity` +13/−4，四个布局与 `activity_main.xml` 注释共 +56/−21（`git diff --numstat` 对 HEAD，含本会话更早片段的注释改写），与 `estimated_production_lines: 40` 同量级。
- **仪器修了两处失明**（阈值一个没动，`J1_MIN_LIFT`/`J2_MIN_SHADOW`/`J3_*`/`J4_MIN_DRIFT` 全部保持 061 落盘值）：
  - 让位之后，胶囊取区里会出现「内容一直亮到窗口下缘」的帧，`_bright_runs` 把描边尖峰和纸纹亮行误接成一个无限高的面，投影取样区被推到画面外 → 退出码 3（仪器故障，不是判据未成立）。修法是加「下缘回落」规则：`FACE_LOCAL_ROWS=12` 之内的滚动亮度水平往下掉超过 `FACE_STEP_FALL=8.0` 即认定面的下缘；`settled` 守卫保证描边尖峰不参与基线，否则会把胶囊自己的头 11 行切掉（曾测出 box `78,2396..1121,2586`、J1 18.0，与 061 不可比）。
  - `_band` 增加帧边过滤：`runs = [b for b in runs if b[1] < y1]`——悬浮面的定义性事实之一是「它下面还有别的东西接住它的投影」，一直贴到窗口下缘的亮块不是悬浮面。
  - 两条规则各配一个自检用例（`_composite(..., under=True)` 断几何 `60,840..539,900` 与 J1 达标；J2 在该合成帧上**合法为负**，因为亮内容本来就比投影更亮，真机 J2 才是证据），cases 9 → 11。
- **取区条件（写进 `_band` docstring，不是静默绕过）**：J1–J3 必须在静止帧上读。内容铺满到窗口下缘的密集帧会被帧边过滤判为「无悬浮面」，此时读数应换到末条停稳的帧；本片 GREEN 三帧中 `062-after-home-bottom.png` 即静止帧。

## Verification report

- Verdict: PASS
- Evidence: `artifacts/evidence/android-glass-depth-2026-10-22/` 下 `062-baseline-*`（RED，J4 0.0 L / exit 1）与 `062-after-*`（GREEN，J1 19.6 / J2 12.4 / J3 26.2 + 彩度 13.0 / J4 35.1 / exit 0、selftest 11 例 0 失败）；`062-chat-bar-hidden.png`、`062-chat-ime.png`、`062-library-rest.png`、`062-library-after-swipe.png`、`062-chat-frame-a/b.png`；`tests 897 failures 0 skipped 0`；`run_all_guards.py --tier core` rc=0。
- Changed/deleted assertion rationale and equivalent behavior coverage: `SchemeAHomeShellLayoutTest` 原断言「`swap` 给宿主加 `R.dimen.tab_bar_clearance`，否则 `": 0"`」——那正是本片要拆掉的不变量，保留它会锁死缺陷。改名后的 `theTabBarFloatsAboveTheContentThatScrollsUnderIt` 覆盖同一意图的等价事实：① `@+id/shell_host` 在 `screen_tab_bar` 的 `<include>` **之前**出现（胶囊是后置兄弟，因此能浮在内容上）；② `swap` 体内仍有 `firstLevel ? View.VISIBLE : View.GONE`（二级屏是**收起胶囊**，不是坐在被加过内边距的宿主里）。新增 `TabBarUnderScrollLayoutTest` 四条从留白归属、宿主不再自留、只有四份布局付它、数值唯一四个角度补回原断言的保护面。
- Gaps: ① 学习库未真正滚过——ALP-AN00 上当前只有 3 个主题，列表高度不足视口，没有滚动区间，因此 J4 只在首页实测；留白结构已按同一模式落好并由测试锁定，等数据变多后自然生效。② `layout-sw600dp` 变体只有 JVM 断言覆盖，手边无平板设备，未实拍。③ 滚动位置恢复未做真机逐帧复核（依赖既有 `HomeScreen` 状态保持与全量 JVM 绿）。④ 顶栏滚动渐深按 Deferred 未做。
- Risks: 厂商 ROM 上 elevation 绘制顺序的边角差异可能让胶囊在个别机型被内容盖住（本机 ALP-AN00 实测正常）；内容一直亮到窗口下缘的极端密集帧下，J1–J3 需要人工挑静止帧才能读——这是仪器 docstring 里明写的取区条件，不是阈值松动。

## Handoff notes

- Completed: 让位归属从宿主迁到两屏滚动容器；J4 从 0.0 L 实测到 35.1 L；仪器两处失明修复 + 2 个自检用例；外壳/布局断言改写并留理由；`android/README.md` 三条（`MainActivity`、`activity_main.xml`、`screen_tab_bar.xml`）同步。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 063 把同一套材质与让位搬到 `frontend/`（`glass.css` 消费 `--glass-shadow-key/ambient`、`color-mix` 推导 tint 暗端、删 `--glass-shadow`、顶栏改 overlay、用同一阈值跑浏览器截图）。
- Watch-outs: 063 若遇到「内容铺满到视口下缘」的 Web 帧，同样要挑静止帧读 J1–J3，不要为了读数把玻璃改不透明；学习库 J4 待主题数超过一屏后补一次真机读数。
