# Filename: T-2026-10-22-scheme-a-shell-home-055.md
# Codex Harness Task: 方案 A 外壳悬浮 tab bar 与首页结构对齐

id: T-2026-10-22-055
title: 方案 A 外壳悬浮 tab bar 与首页结构对齐
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-scheme-a-morandi-requirement-052.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 420
shared_code_touched: false
depends_on: [T-2026-10-22-chinese-serif-font-054.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 052 已拍板方案 A 结构 1:1。053 落了莫兰迪 token，054 落了中文衬线；本片把 Android 外壳与首页改成方案 A 的胶囊顶栏、大号衬线品牌、玻璃输入卡、232dp 推荐卡、带 chevron 的历史卡与悬浮胶囊 tab bar。
- Problem/background: 049 只把玻璃材质铺在旧结构上：tab bar 仍是被内容推上去的通栏条，首页仍是裸 appbar、56dp 芽标 + 30sp 品牌、无副标题位置、推荐 chip 是 44dp 胶囊、历史卡是 16sp 大标题。真机与方案 A 的差异首先是结构，不是色值。
- Users/scenarios: 单人自用 Android 手机（ALP-AN00，1200×2652，Android 13/SDK 33），平板保留 `layout-sw600dp`。
- Expected outcome: 首页顶栏是一枚 52dp 胶囊玻璃；品牌锁定区为 40dp 芽标 + 41sp 衬线 + 12sp sans 副标题；输入卡 138dp 最小高度、玻璃、圆角 28dp；推荐项 232dp 玻璃卡；历史卡 11/12 内边距 + 14dp chevron + 13sp 标题；tab bar 悬浮在内容之上（左右 24dp、底部 20dp、62dp 高、全圆角），内容由宿主预留 90dp 底部空间。
- Core flow and reversibility: 改 `activity_main.xml`、`screen_tab_bar.xml`（手机/平板）、`screen_home_body.xml`、`item_home_topic.xml`、`item_recommendation.xml`、`MainActivity.swap` 与 dimens/drawable；业务绑定 id 不变。回退为还原这些文件与宿主 padding。
- Inputs/outputs: 输入是方案 A 的 CSS 数值与现有玻璃/莫兰迪 token；输出是结构、尺寸、间距和两份 tab bar 副本的一致改造。
- State and exception handling: 只有视图层级与尺寸变化；草稿、滚动、tab 切换、抽屉、返回栈、数据读取不变。tab bar 隐藏时宿主底部 padding 归零，避免二级页面留白。
- Constraints and trade-offs: Java-only、零 AndroidX/Kotlin、无新依赖；不新增死按钮；保留真机状态栏；不改文案语义；设计数值落在 dimens 并注明来源；平板边界继续成立。
- AI recommendation and rationale: 用宿主 padding 让位而不是给每个屏幕加 padding，这样首页与学习库共用同一决策；tab bar 根 id 保持不变，外围加透明容器只为平板限宽；推荐卡保留 PRD 6.2.17 要求的分类标签，只把承载从 44dp 胶囊改为 232dp 玻璃卡。
- User-confirmation decisions: ① A + 莫兰迪毛玻璃；② 允许内置中文衬线；③ 接不了真实数据就删；④ 保留真机状态栏。
- AI-autonomous decisions: ① 首页附件按钮不恢复（父卡已判死）；② 副标题沿用现有 `home_hero_title` 文案，只改字号/字体/位置，遵守“不改文案语义”；③ 发送键保留 44dp 触达（项目无障碍底线），不缩到方案 A 的 40dp，并在布局注释登记；④ tab bar 选中底色用现有 `glass_sheen`（方案 A 的 `--tab-active` 是提案局部变量，token 权威链没有它）；⑤ 推荐卡保留分类标签，去掉 chevron（整卡就是入口）。
- Acceptance evidence: 新 JVM 结构测试 RED→GREEN；既有 `HomeComposerLayoutTest`、`HomeRecommendationStripTest`、`GlassSurfaceLayoutTest`、`ChineseSerifTitleTest` 全绿；`:app:assembleDebug`、`:app:assembleDebugAndroidTest`；059 真机四屏截图。
- Facts: 方案 A 权威 `Docs/04-ux-design/2026-10-21-玻璃质感重设计提案/A-晨雾玻璃.html`（`.topbar` 452-468、`.home-brand` 1906-1932、`.composer` 555-599、`.suggest` 656-672、`.topic` 678-715、`.tabbar` 731-762）；当前 `activity_main.xml` 的 tab bar 在纵向流里；`MainActivity.swap` 是可见性唯一决策点；`HomeScreen.render` 目前在有主题时隐藏副标题。
- Assumptions: 宿主 padding 能让 ScrollView/列表的可视区自然避开悬浮栏；`bg_glass_bar` 只被 tab bar 使用，可以安全改成全圆角；平板用外层透明容器 + 限宽内层保持同一视觉。
- Unknowns: 90dp 让位在超大字体/输入法下是否足够；胶囊顶栏在 320dp 宽窗口下的压缩表现。
- Risks: 悬浮栏遮住内容；两份 tab bar 副本漂移；改 `ChineseSerifTitleTest` 时丢失等价覆盖；`HomeScreen` 删掉 hero 可见性判断后旧动效测试需要复核。
- Deferred: 首页推荐按分类整卡着色；平板专属布局重排。
- Non-goals: 改业务规则、数据库、接口、文案语义；恢复首页附件；恢复聊天标题 chevron；实时模糊。
- Skipped non-critical questions and reason: 未追问发送键 40dp 还是 44dp；无障碍底线优先，差异已登记。

## Goal

让首页与外壳在真机上读作方案 A：悬浮胶囊 tab、胶囊玻璃顶栏、大号衬线品牌、玻璃输入卡、232dp 推荐卡和紧凑历史卡，同时所有既有业务入口保持可用。

## Scope

### In scope
- `android/app/src/main/res/layout/activity_main.xml`。
- `android/app/src/main/res/layout/screen_tab_bar.xml`、`layout-sw600dp/screen_tab_bar.xml`。
- `android/app/src/main/res/layout/screen_home_body.xml`、`item_home_topic.xml`、`item_recommendation.xml`。
- `android/app/src/main/res/drawable/bg_glass_bar.xml`、`bg_tab_item.xml`、`bg_tab_item_active.xml`、`bg_rec_card.xml`、`bg_glass_composer.xml`。
- `android/app/src/main/res/values/dimens.xml`。
- `android/app/src/main/java/com/treelearn/app/MainActivity.java`（swap 的底部让位）。
- `android/app/src/main/java/com/treelearn/app/screen/HomeScreen.java`（副标题常显、卡片间距）。
- `android/app/src/test/java/com/treelearn/app/screen/SchemeAHomeShellLayoutTest.java`。
- `ChineseSerifTitleTest` 的断言调整（副标题改 sans）。
- `android/app/src/androidTest/.../BottomTabShellInstrumentedTest`、`HomeBackdropInstrumentedTest` 的 tab bar 定位更新。

### Out of scope
- 对话页（056）、学习库/分支导航（057）、Web（058）。
- 推荐漂移算法、分类映射、业务状态。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只改壳层与首页的结构、尺寸和绘制；tab 点击、抽屉、设置、草稿、发送、推荐点击、历史卡打开、返回栈、滚动保持不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `activity_main.xml` 生产唯一 `tab_bar` 与 `shell_host`；`MainActivity.swap` 消费二者决定可见性与底部让位；手机/平板 `screen_tab_bar.xml` 是同一组 id 的两份布局；`HomeScreen` 消费 `home_hero`/`home_hero_title`。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: tab bar 从纵向流改为悬浮胶囊并让内容底部预留 90dp；首页品牌区、输入卡、推荐卡、历史卡的结构与尺寸按方案 A 重排；副标题常显且为 sans。
- Authoritative code/config/migration evidence: `activity_main.xml`、`screen_tab_bar.xml`、`screen_home_body.xml`、`item_home_topic.xml`、`item_recommendation.xml`、`MainActivity.swap`、`HomeScreen.render`。
- Documentation surfaces checked: `android/README.md`、`.codex/context/source-of-truth.md`、`.codex/rules/multi-device.md`。
- Documentation files updated: 059 汇总更新 `android/README.md` 的外壳与入口说明。
- Producer/consumer/sibling/compatibility paths checked: `GlassSurfaceLayoutTest`、`HomeComposerLayoutTest`、`HomeRecommendationStripTest`、`ChineseSerifTitleTest`、`BottomTabShellInstrumentedTest`、`HomeBackdropInstrumentedTest`。
- Superseded phrases/examples reverse-searched: 「tab bar 在 `main_root` 纵向流里」「首页 hero 在有主题时隐藏」「推荐 chip 44dp」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保留 2026-10-21 玻璃材质记录；本片追加结构对齐说明。

## Acceptance criteria

- [x] 新 `SchemeAHomeShellLayoutTest` 覆盖：悬浮 tab 与宿主让位、胶囊 tab 与选中态、胶囊顶栏、41sp 衬线品牌、12sp sans 副标题、138dp 玻璃输入卡、232dp 推荐卡、11/12 历史卡与 14dp chevron、首页无附件入口。
- [x] 定向 JVM 命令 RED 非 0、GREEN 为 0，命令一致。
- [x] 既有首页/玻璃/字体/推荐测试仍绿；全量 JVM 0 失败。
- [x] `:app:assembleDebug` 与 `:app:assembleDebugAndroidTest` 退出码 0。
- [x] 手机与平板 tab bar 两份副本 id 一致、可见性与底部让位由 `MainActivity.swap` 单点决定。
- [x] 未自动提交或推送；未新增依赖；未恢复首页附件或聊天 chevron。

## TDD execution record

### RED — failing test first
- Test/scenario: 外壳与首页仍是旧结构时先跑新结构测试。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.SchemeAHomeShellLayoutTest"`
- Exit code: 1
- Observed failure: 8 例中 7 例失败：悬浮 tab 与宿主让位、胶囊 tab、胶囊顶栏、品牌区、玻璃输入卡、232dp 推荐卡、紧凑历史卡。唯一通过的是「首页无附件入口」，因为旧布局本来就没有附件按钮。
- Failure reason validated: 旧 `activity_main.xml` 的 tab bar 仍在纵向流里、无 `tab_bar_pill`/`bg_glass_bar` 胶囊；顶栏是裸 appbar；品牌 30sp sans；输入卡是旧高度；推荐项是 44dp 胶囊；历史卡是 16sp 标题。失败原因是结构差异，不是测试写错。

### GREEN — minimum implementation
- Implementation: `activity_main.xml` 改 `FrameLayout` 覆盖层 + 底部 `include`；手机/平板 `screen_tab_bar.xml` 改为 62dp 胶囊 + 50dp 双 pill + `bg_tab_item` 选中态；`MainActivity.swap` 用 `boolean firstLevel` 同时决定可见性与 90dp 宿主 padding；`screen_home_body.xml` 改胶囊顶栏、40dp 芽标 + 41sp 衬线品牌 + 12sp sans 副标题、138dp 玻璃输入卡；`item_recommendation.xml` 改 232dp 玻璃卡（保留分类标签、去 chevron）；`item_home_topic.xml` 改 11/12 内边距 + 14dp chevron；`HomeScreen` 移除 hero 可见性判断；新增 5 个 drawable 与 30 个 dimens。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.SchemeAHomeShellLayoutTest"`
- Exit code: 0
- Passing evidence: `055-green.txt`（8/8，BUILD SUCCESSFUL）。首次 GREEN 被 `bg_tab_item_active.xml` 注释中的 `--` 判为非法 XML，改正后又被 include 跨行写法触发测试助手的精确子串越界；两处都是实现/助手缺陷，分别修复后同一命令转绿。
- Regression evidence: 全量 `./gradlew :app:testDebugUnitTest` 退出码 0（`055-jvm-all.txt`）。首轮回归发现 `RecommendationTagTest` 仍钉着已删除的 `rec_chevron`，按方案 A 改钉「图标 + 分类 + 文本」并显式断言无 chevron；改后 872 例 0 失败。

### REFACTOR — optional
- Cleanup: 无额外重构；方案 A 数值集中在 `dimens.xml`，未在布局里散落数字。
- Regression evidence: 同 GREEN 的全量 JVM 与构建证据。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 方案 A 数值以 `A-晨雾玻璃.html` 为准；新增尺寸进 `dimens.xml` 并写来源注释。
- 宿主让位只在 `MainActivity.swap` 写一次：一级页面 90dp，二级页面 0。
- 推荐卡保留 `rec_tag`（PRD 6.2.17 点 3 要求分类），去掉没有独立动作的 chevron。
- 发送键保持 44dp：方案 A 的 40dp 与本项目 44dp 无障碍底线冲突，取底线并登记差异。

## Verification report

- Verdict: PASS
- Evidence: `055-red.txt`（rc=1，7/8 失败）→ `055-green.txt`（rc=0，8/8）；`055-jvm-all.txt` rc=0（872 例 0 失败）；`055-builds.txt` rc=0（`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`verifyNoKotlinDex: PASS (3 APK(s))`，shipped APK 22,181,626 B / 18 dex）；`055-token-guard.txt` rc=0（69 tokens / 24 resources，四方一致）。
- Changed/deleted assertion rationale and equivalent behavior coverage: ① `RecommendationTagTest` 原断言要求 `rec_chevron` 与 `ic_chevron_right`；方案 A 的整卡就是入口，chevron 是暗示第二个动作的装饰，故删除断言并改为断言 `rec_icon` + `@drawable/ic_sparkle`、保留 `rec_tag`/`rec_text`，同时显式断言 chevron 不存在——覆盖从「三个部件之一」变为「新三个部件」，分类标签与文本的覆盖未减少。② `ChineseSerifTitleTest` 原断言 `home_hero_title` 使用内置衬线；方案 A 把标题位改为品牌下方的 12sp sans 副标题，故改为断言该 id 不含 `@font/noto_serif_sc`，品牌与三个页面主标题的衬线断言保持不变。③ 仪器测试仅把 `tab_bar` 的查找改为 `tab_bar_pill`，因为新的根节点是透明定位容器。
- Gaps: 真机四屏截图与视觉复核按 052 安排由 059 统一取证；本片未在设备上验证超大字体与 320dp 窄窗下胶囊顶栏的压缩表现。
- Risks: 90dp 宿主让位在超大字体/输入法弹出时可能不足（待 059 真机复核）；手机与平板两份 tab bar 副本存在漂移风险（结构测试只读手机副本，平板靠同 id 与 `tab_bar_tablet_width` 约束）；`bg_glass_bar` 被顶栏与 tab bar 共用，后续改圆角会同时影响两处。

## Handoff notes

- Completed: 外壳悬浮胶囊 tab、宿主单点让位、首页胶囊顶栏、衬线品牌锁定区、玻璃输入卡、232dp 推荐卡、紧凑历史卡；定向与全量 JVM、双构建、Java-only、token 守门全绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 056 对话页结构对齐与 057 学习库/分支导航结构对齐（可并行），随后 058 Web token 消费、059 整跑与真机取证。
- Watch-outs: 两份 tab bar 副本必须同步；不要给每个屏幕加自己的底部 padding；不要恢复首页附件或聊天 chevron；`ChineseSerifTitleTest` 的副标题断言已随方案 A 调整，后续不要误改回衬线。
