# Filename: T-2026-10-23-pinned-topbar-075.md
# Codex Harness Task: 首页与学习库顶部横栏常驻悬浮，列在栏下滚动

id: T-2026-10-23-075
title: 两枚一级顶栏改为常驻悬浮
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-glass-depth-requirement-060.md
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: android
stack: android
estimated_production_lines: 60
shared_code_touched: true
depends_on: [T-2026-10-23-glass-control-face-072.md, T-2026-10-23-home-history-list-074.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 用户 2026-10-23 在一张标注截图上圈住首页顶部那条栏（☰ + 太阳/齿轮），写「红色这个常驻，不跟随滑动，往上滑动时，这个横栏就类似浮在上面」。Android 的 `home_appbar` 与 `library_appbar` 当时是滚动列的第一个子节点，向上一划就跟着走，用户刚用的两个入口随之消失。065 卡片把「Android 首页顶栏覆盖化」明确记为 Deferred，本片就是那笔延期账。
- Users/scenarios: 真机首页（1200×2652）与学习库的向上滑动；Pad 宽窗（`layout-sw600dp` 两份镜像）；随页面滚动的聊天栏与抽屉导航栏作为对照组。
- Expected outcome: 栏不随滑动走、看起来浮在列之上，列经过栏下方继续走；栏内的字不许被下面的字穿透（这是本片实测暴露出来的第二件事）。
- Core flow and reversibility: 结构改动集中在四个布局文件 + 一枚新 dimen。撤销是把 appbar 放回 body 并去掉 paddingTop——但那样会同时撤销设计事实源（原型早已把 `.topbar` 与 `.app-body` 并排放），所以这条撤销不该作为选项保留。
- Inputs/outputs: 输入是既有的 appbar 布局块、`topbar_offset`(6dp) 与 `topbar_height`(52dp) 两枚既有 dimen、072 已入库的 `bg_glass_control_bar`；输出是 1 枚新 dimen（`top_bar_clearance` 58dp）、四个布局文件的结构改动、两条新单测、两处旧单测的读取点改指。不改 Java、不改 SQL、不改 presenter。
- State and accessibility/exception handling: `home_drawer_button` / `home_settings_button` / `library_back` / `library_title` 的 id、图标、tint、content description、命中区全部不变；`library_root` 这个 id 保留，所以 Java 侧按 id 寻址的代码零改动。空态、搜索态、分段位不受影响。
- Constraints and trade-offs: 设计数值只能来自 token 链，让位数值只能有一处权威定义。**核心矛盾**：栏一常驻就变成「压在移动文字之上的 chrome」，而 065 拍板保留的 chrome .74 在这条状态下会让下面的推荐语穿栏而过——实测两帧同位差 33.05% 的像素在变、|ΔL| 中位 4.14 / P95 10.07。第二条矛盾是仪器层面的：`check_glass_depth.py --band top` 给这枚常驻栏找到的面框从 y=1 开始，比真胶囊高 134 行，于是那条 box 上的 J1/J2/J4 全都不可引用。
- AI recommendation and rationale: ① 常驻栏直接继承 072 已拍板的控制面（.93 平涂），而不是把 chrome 再抬一档——后者会连带改变两枚随页面滚动的栏，而那两枚没有「压在别人的文字之上」这个问题；② `top_bar_clearance` 定义为 `topbar_offset + topbar_height` 的**和**，不引入第三个自由数，并用解析后的 dp 数值比较把它钉住；③ 宽窗的栏锁 `content_measure` 宽 + `layout_gravity` 居中，不给 marginHorizontal，避免出现「满宽栏压在封顶列上」；④ 仪器盲区按 061 的既有处置：不放宽阈值、不改脚本，换成不引用那条 box 的判据，另用分区两帧差给结论。
- User-confirmation decisions: 「红色这个常驻，不跟随滑动，往上滑动时，这个横栏就类似浮在上面」（2026-10-23，带标注截图）。
- AI-autonomous decisions: ① 学习库顶栏一并常驻（用户只圈了首页，但两枚是同一种一级栏，留一枚滚着就是新的端内不一致）；② 两枚常驻栏换 control 面（用户没说换面，这是本片自己实测暴露的缺陷，理由与 072 同一条，因此不另开一次拍板）；③ 聊天与抽屉导航**不**换面，保持 chrome + 145° tint；④ Web 侧零改动并登记为有意端差（Web 早就常驻，且 `blur(28px)` 实测让栏内 0.00% 像素变化，已达同一结果）；⑤ 顺手把 `screen_home.xml` 的头注释补上原型事实源出处。
- Acceptance evidence: dumpsys 回读 `home_appbar` 是 `home_root` 的后一个兄弟（52,20-1148,189）且 `home_content` 起点 189px = 58×3.25；一次滑动后栏带同位差 control 面 3.65%（面内直边段 0.49%、|ΔL| 中位 1.00）而栏下正文带 45.10% 在翻动；库页 0.83% 对 59.33%；Web 回读 `position=absolute` + `.page-scroll padding-top=64px` 成对；两条新单测 RED(1)→GREEN(0) 全转录；全量 JVM 946 例 0 失败 + `assembleDebug` 成功 + 真机 `install -r` Success。
- Facts: 原型 `_src/screens/home.html` 第 2 行就是 `<header class="topbar glass">`，与 `.app-body` 并排，`_src/glass.css` 的 `.topbar` 是 `flex: none`——设计事实源本来就常驻；Web 的 `.workspace-topbar` 自 063 起是 `position:absolute; z-index:2` + 滚动容器 `padding-top`；Android 的底部胶囊用「后置兄弟 + clipToPadding=false + clipChildren=false」这套结构（T-2026-10-22-062），本片把同一套结构用到顶部，不是新发明。
- Assumptions: 用户要的「浮在上面」= 栏不随滑动移动且看起来盖在列之上（有投影、有让位），不包含「栏要更透」——072 与本片实测都指向相反方向。
- Unknowns: 首页那 6dp 上浮边距（栏上方的 20px 带）里滚动内容可见，这是「悬浮」的正常表现还是会被读成瑕疵，要看用户下一次观感反馈。
- Risks: 栏高或上浮边距被人单独改动而让位没跟上，玻璃下缘出现一条空带（063 在 Web 上踩过同一坑）；已由 dp 等式断言拦住。第二条风险是误用：把 control 面当成「更透的替代品」推到随页面滚动的栏上，白白丢掉通透。
- Deferred: 聊天/导航栏是否也要常驻（它们的宿主是二级屏，当前判据不适用）；`check_glass_depth.py` 为控制面显式豁免 J4（本片只撤主张不改脚本）；Pad 宽窗真机取证。
- Non-goals: 不改 J1–J4 阈值与取区规则、不改 `bg_glass_bar` 的其余消费者、不改 token 值与链上枚数、不改 Web、不新增依赖、不自动提交或推送。
- Skipped non-critical questions and reason: 未追问「滚动时栏要不要渐浓/加投影变化」——那需要新的判据与新的观感裁决，用户这条指令只要求常驻与浮起。

## Goal

首页与学习库的顶部横栏改为不随滑动的悬浮覆盖层，列在栏下走过，同时两枚常驻栏采用控制面使栏内文字不被下方内容穿透；随页面滚动的两枚栏保持原材质。

## Scope

### In scope
- 四个布局文件的结构调整：`layout/screen_home.xml`、`layout/screen_library.xml` 与 `layout-sw600dp/` 两份镜像——根 `FrameLayout` 加 `clipChildren="false"`，appbar 从 body 搬出成为 ScrollView 的后置兄弟，滚动容器加 `paddingTop="@dimen/top_bar_clearance"` + `clipToPadding="false"`。
- `layout/screen_home_body.xml` 与 `screen_library_body.xml` 删除各自的 appbar 块。
- 新 dimen `top_bar_clearance` = 58dp（= `topbar_offset` 6 + `topbar_height` 52）。
- 两枚常驻栏的背景改为 `@drawable/bg_glass_control_bar`（072 的 .93 平涂控制面）。
- 两条新单测 + 两处旧单测读取点改指新宿主文件。
- `android/README.md` 应用外壳表新增一行；设计系统 README 新增 075 变更记录（含 072 的 J4 处置定稿）。

### Out of scope
- 聊天页与抽屉导航的顶栏（仍随页面滚、仍 chrome + tint）、底部胶囊与 `tab_bar_clearance`、token 值与枚数、J1–J4 阈值与取区规则、Web 侧、`MainActivity` 与两个 Screen 的 Java 代码。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 顶栏从「滚动列的第一行」变为「常驻覆盖层」，列起点下移 58dp 并在栏下走过；两枚常驻栏的材质从 chrome .74（带 145° tint）变为 control .93（平涂）。保留：所有控件 id/图标/tint/content description/命中区、`library_root` id、底部让位归属、聊天与导航栏的材质与行为、空态与搜索态。
- Producer/consumer/sibling entrypoints and platform branches（代码证据）：producer 是布局文件本身，Java 只按 id 取控件（`HomeScreen` / `LibraryScreen` 未改）；consumers 含 `IconAssetsTest`（`assertIconAction("layout/screen_library.xml", "library_back", …)`）与 `ChineseSerifTitleTest`（`elementAround(layout("screen_library.xml"), "library_title")`），两处随宿主文件改指，判定的内容一字未改；sibling 是 `screen_tab_bar.xml`（同一套后置兄弟结构，062 落的）；平台分支为 `layout-sw600dp/` 两份镜像，同判同改。
- Native bridge or independent-client compatibility：无。不涉桥接、数据格式与 SQL。
- Behavior-neutral non-applicability reason：不适用（结构与视觉行为均有改动）。

## Documentation impact record

- Changed runtime facts: 两枚一级顶栏常驻；`top_bar_clearance` 成为第二个让位数（顶部），且与 `topbar_offset`/`topbar_height` 成等式；常驻栏取控制面；Android 胶囊与常驻栏都不再主张 J4；Web 顶栏保留 chrome 的理由是它有真模糊（端差）。
- Authoritative code/config/migration evidence: `res/values/dimens.xml` 的 `top_bar_clearance`（注释写明它等于哪两枚之和、改哪个数必须跟着改）、四个布局文件的头注释、`res/drawable/bg_glass_control_bar.xml` 的头注释（「自 075 起也是两枚常驻栏的面」）。
- Documentation surfaces checked: `android/README.md`（应用外壳表、`screen.HomeScreen` 行、`screen.LibraryScreen` 行、场景层小节）、`Docs/04-ux-design/design-system/README.md`（变更记录）、`Docs/spec/`（本片不涉及既有规格条款，未改）。
- Documentation files updated: `android/README.md` 新增一行讲两枚常驻栏的结构与让位归属，并在 HomeScreen / LibraryScreen 两行各加一句指路（唯一正文在那新增一行里，不重复数值）；设计系统 README 新增 075 条并把 072 条的「J4 暂时撤回待复测」改写为定稿不主张。
- Producer/consumer/sibling/compatibility paths checked: 反向搜索 `home_appbar` / `library_appbar` / `screen_library_body` / `bg_glass_bar` 在代码、测试与文档里的全部出现；`bg_glass_bar` 确认仍有两个活消费者（聊天、导航），未孤立。
- Superseded phrases/examples reverse-searched: 「顶栏跟随滚动」这类措辞在 `android/README.md` 与 055/057 卡片里的原文不改写（那是当时事实），当前态只由新增行表述。
- Historical/PRD handling: 065 卡片的 Deferred 条目「Android 首页顶栏覆盖化」不改写，本片卡片正文引用它作为延期账的来源。

## Acceptance criteria

- [x] 顶栏不随滑动移动，列在栏下走过：真机一次 `input swipe` 后，栏下正文带 45.10%（首页）/ 59.33%（库页）同位像素在变，栏带自身 3.65% / 0.83%。
- [x] 栏内文字不被下方内容穿透：栏带同位差从 chrome 面 33.05%（|ΔL| 中位 4.14、P95 10.07）降到 control 面 3.65%（中位 1.00、P95 3.07），面内直边段 0.49% / 中位 1.00 / 最大 10.71。
- [x] 让位与栏高成对、不留空带也不吃字：`home_content` 起点 189px = `top_bar_clearance` 58dp × 3.25；`dp("top_bar_clearance") == dp("topbar_offset") + dp("topbar_height")` 由断言比较解析后的数值。
- [x] 保留行为：控件 id/图标/tint/content description/命中区不变（`IconAssetsTest`、`ChineseSerifTitleTest` 仍绿，只是换了宿主文件）；底部 `tab_bar_clearance` 归属不变；聊天与抽屉导航栏材质与滚动行为不变；末条仍停在悬浮胶囊上方。
- [x] 宽窗同判：`layout-sw600dp/` 两份同为后置兄弟 + 让位，栏宽锁 `content_measure` 且 `layout_gravity="top|center_horizontal"`。
- [x] 判据盲区如实登记，不给任何一端放宽阈值：`--band top` 那条 box 上的 J1/J2/J4 不引用，只引用两次跑共用同一 box 时 J1 的方向（9.9→17.6 L）。

## TDD execution record

### RED — failing test first
- Test/scenario: `SchemeAHomeShellLayoutTest.theHomeTopbarFloatsWhileTheColumnScrollsUnderIt`（新增）与 `SchemeALibraryNavigationLayoutTest.theLibraryTopbarFloatsWhileTheRowsScrollUnderIt`（新增）；两枚常驻栏的面由 `theHomeTopbarIsAGlassCapsule` / `theLibraryTopbarIsTheSerifCapsule` 改为要求 `bg_glass_control_bar`。
- Command: `./gradlew :app:testDebugUnitTest --tests "*SchemeAHomeShellLayoutTest*" --tests "*SchemeALibraryNavigationLayoutTest*" --console=plain`
- Exit code: 1
- Transcript: `artifacts/evidence/pinned-topbar-2026-10-23/075-red-console.txt` → `20 tests completed, 4 failed`、`BUILD FAILED in 6s`。
- Observed failure: `theHomeTopbarIsAGlassCapsule` → `id home_appbar must exist`；`theHomeTopbarFloatsWhileTheColumnScrollsUnderIt` → `the bar is a later sibling of the scroll container, so it floats over it`；`theLibraryTopbarFloatsWhileTheRowsScrollUnderIt` → `the bar is a later sibling of it, so it floats over the rows`；`theLibraryTopbarIsTheSerifCapsule` → `id library_appbar must exist`。
- Failure reason validated: 四条红各自对应本片要立的一件事（栏不在外壳里 / 兄弟次序不成立 / 面不是控制面）。退回方式是 `git show HEAD:<path> > <path>` 放回 6 个布局文件（四个 appbar 宿主 + 两份 body 相关，含 sw600dp 两份），`values/dimens.xml` 全程未动，所以 `dp("top_bar_clearance")` 在 RED 那次也能解析——等式断言不是红的来源。跑完从 `artifacts/tmp/075-red-backup/` 逐一 cp 回并与备份 md5 逐个比对一致（RED 之后 `screen_home.xml` 的头注释又补了原型出处一句，那是注释、不含被断言的字符串，全量套件在其后重跑过）。

### GREEN — minimum implementation
- Implementation: ① 新 dimen `top_bar_clearance` 58dp；② `screen_home.xml` / `screen_library.xml`（含 sw600dp 两份）根 `FrameLayout` 加 `clipChildren="false"`，ScrollView 加 `paddingTop="@dimen/top_bar_clearance"` + `clipToPadding="false"`，appbar 作为后置兄弟并改用 `bg_glass_control_bar`；③ 两份 body 删除 appbar 块并改写头注释；④ 两条新单测 + `dp()`/`tablet()` 两个测试辅助（比数值不比字符串）；⑤ `IconAssetsTest`、`ChineseSerifTitleTest` 读取点改指 `screen_library.xml`。
- Command: `./gradlew :app:testDebugUnitTest --tests "*SchemeAHomeShellLayoutTest*" --tests "*SchemeALibraryNavigationLayoutTest*" --console=plain`
- Exit code: 0
- Transcript: `artifacts/evidence/pinned-topbar-2026-10-23/075-green-console.txt` → `BUILD SUCCESSFUL in 10s`。
- Passing evidence: 20 例全通过；真机 `adb -s AWSS013807000356 install -r` Success；dumpsys 回读 `home_appbar 52,20-1148,189` / `home_content 0,189-1200,3656` / `library_appbar 52,20-1148,189`。
- Regression evidence: `./gradlew :app:testDebugUnitTest :app:assembleDebug` → `BUILD SUCCESSFUL`，`tests=946 failures=0 errors=0 skipped=0`（`075-suite.txt`）；`pnpm --dir frontend verify` → `steps=16 ok=16 bad=0 fault=0`；`check_design_tokens.py` → `OK=10 FAIL=0 SKIPPED=0`（`measured_tokens=72 measured_resources=24`）；`gen_design_tokens_css.py --check` 逐字节一致；`check_glass_depth.py --selftest` 通过；`validate_tasks.py` → `errors=0`。

### REFACTOR — optional
- Cleanup: 搬动过程中自造的一处 XML 缩进错位（`home_settings_button` 块属性掉到 8 空格）用一次重排脚本修平，两份 home 布局一起处理。
- Regression evidence: 上述全量 946 例 + `assembleDebug` 在整理后重跑，仍 0 失败。

### LOOP — optional
- 不适用。

## Implementation notes

- 结构不是新发明的：底部胶囊在 T-2026-10-22-062 就用「后置兄弟 + `clipToPadding=false` + 根 `clipChildren=false`」这套；本片只是把同一套搬到顶部。`clipChildren` 的理由也同源——投影由父层绘制，裁掉子边界会让悬浮条没有投影。
- 让位数只有一处权威定义（`top_bar_clearance`），注释里写明它等于哪两枚之和，改栏高必须同步；断言比的是解析后的 dp 数而不是字符串，避免出现「改成一个新数字，注释与断言一起失效」的第二事实。
- 仪器教训（本片最值得留的一条）：`--band top` 把这枚 inset 胶囊的面框读成 `85,1..1114,303`，比真面高 134 行。分区实测解释了这条 box：`115..135`（栏上方 6dp 边距）一段变化 56.27%，J4 的 10.2/14.2 全部由它贡献；`1..115`（状态栏）0.21%、中位 0.00 不参与；`135..304`（面本体）3.60%、中位 1.00。所以那条 box 上的 J1/J2/J4 一概不引用，处置与 061 登记的贴边盲区同类（不放宽阈值、不改脚本）。J2 尤其明显：两枚不同的面给出同一个 -2.1，说明取样行根本没落在面的下缘上。
- 换面后的残差做了逐像素定位：>25 L 的采样点只有 48 个（0.09%），全在 `x 64..76 / y 268..286`，即左下圆角弧**之外**的投影带（胶囊高 169px，圆角半径 ≈84px，弧只吃四角）。面内直边段的 0.49% / 最大 10.71 才是「栏稳不稳」的正确读数。
- Web 一片未改，是有意登记：Web 早就常驻，且它的 .74 + `blur(28px)` 实测拿到栏内 0.00% 变化——材质路径不同、结果相同。065 早已量化过这条模糊的保护作用（Web 顶栏 J4 .54→11.3 / .74→4.9 L）。
- `bg_glass_control_bar.xml` 的头注释随本片改写：它现在是三枚面（底部胶囊 + 两枚常驻顶栏）共用的 drawable，注释写清「什么时候该用它、什么时候该用 `bg_glass_bar`」。

## Verification report

- Verdict: PASS（附带一条如实的覆盖缺口与一条仪器盲区）
- Evidence: 单元——两条新单测 RED(1)→GREEN(0) 全转录 + 全量 JVM `tests=946 failures=0` + `assembleDebug` 成功。结构——`dumpsys activity top` 的兄弟次序与几何（`075-geometry-excerpt.txt`）。像素——`075-band-drift.py` 的四组分箱读数（chrome 面 33.05% → control 面 3.65%，面内 0.49%；栏下正文带 45.10%/59.33%；库页 0.83%），随证据入库、从仓库根可直接复跑。判据——`075-glass-depth-console.txt` 四条命令带 exit code 的原始输出。运行时——Web computed style（`position=absolute`、`z-index=2`、`.page-scroll padding-top=64px`）+ Web 两帧顶栏带 0.00% 变化。真机——`adb install -r` Success（未跑 instrumentation，避免清真机业务数据与 API Key）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。`theHomeTopbarIsAGlassCapsule` / `theLibraryTopbarIsTheSerifCapsule` 的**读取源**从 body 布局改为外壳布局、面要求从 `bg_glass_bar` 改为 `bg_glass_control_bar`——控件本身（id、图标、tint、文案）一点没改，所以 `IconAssetsTest` 与 `ChineseSerifTitleTest` 的保护内容不变，只改宿主路径；这两处改指本身就是本片搬动布局的连带回归证据（它们当时真的红了）。
- Gaps: ① Pad（sw600dp）只有布局同判与单测，**没有宽窗真机/模拟器截图**——本机只有一台手机，「栏在封顶列上居中、不出现满宽栏」这条只能由源码断言保证；② 滚动时栏上方那 6dp 边距带里内容可见（实测 56.27% 在变），这是否会被读成瑕疵未拍板；③ `check_glass_depth.py` 对控制面/顶栏锚定的取区盲区仍未在脚本层面修，只登记不引用。
- Risks: 有人把 control 面推广到随页面滚动的栏（丢掉通透）；有人单独改 `topbar_height`（断言会红，这是设计意图）；有人拿那条 mis-boxed 的 `--band top` 读数当回归基线（因此卡片与证据两处都写明它不可引用）。

## Handoff notes

- Completed: 四个布局 + 一枚 dimen + drawable 注释、两条新单测、两处旧断言改指、真机与运行时取证（含仪器脚本）、`android/README.md` 新增行、设计系统 075 变更记录与 072 的 J4 定稿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 若要修仪器，方向是给 `--band top` 加「inset 胶囊只取比留白亮的那一段」而不是从 y=1 起算，并同步 061/062/063 的历史读数复现；② 若要 Pad 覆盖，需要一台宽窗设备或模拟器；③ 072/073/074/075 四片已 verified，等用户决定是否整树提交。
- Watch-outs: 顶栏改动会连带 `IconAssetsTest` / `ChineseSerifTitleTest` 的宿主路径——以后搬任何带 id 的控件都要顺手 grep 这两个测试文件；常驻栏一律用 control 面，随页面滚的栏一律留 chrome，这条边界写在 `bg_glass_control_bar.xml` 的头注释里。
