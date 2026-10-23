# Filename: T-2026-10-22-chinese-serif-font-054.md
# Codex Harness Task: 内置中文衬线字体与标题应用

id: T-2026-10-22-054
title: 内置中文衬线字体与标题应用
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-scheme-a-morandi-requirement-052.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 40
shared_code_touched: false
depends_on: [T-2026-10-22-morandi-token-chain-053.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 052 已拍板允许内置中文衬线字体；本片把系统 TTC 里的 SC 字重提取成 APK 资源，并只用于品牌与页面主标题。
- Problem/background: 当前所有标题用 `android:fontFamily="serif"`，落到 ROM 自带衬线，字重与字形随厂商变化；方案 A 要求「知芽」品牌与页面主标题稳定读作同一套衬线。
- Users/scenarios: 真机首页品牌区、学习库标题、分支导航标题；正文与卡片标题保持 sans。
- Expected outcome: `res/font/noto_serif_sc.otf` 随 APK 分发，OFL 许可随仓库分发；首页品牌、首页 hero、学习库标题、分支导航标题引用该资源；卡片/搜索标题去掉 `serif`，回到主题 sans。
- Core flow and reversibility: 设备 `/system/fonts/NotoSerifCJK-Regular.ttc` → fontTools 提取第 3 个 face（SC）→ `res/font`；回退为删除资源、许可与四处 `fontFamily`，布局其余不变。
- Inputs/outputs: 输入是设备系统字体；输出是 OTF 资源、OFL 许可、四处标题引用与一处去 serif。
- State and exception handling: 字体加载失败时 Android 回退系统字体，不崩溃；字体只影响字形，不影响布局 id 与业务。
- Constraints and trade-offs: 不新增依赖（fontTools 只在构建期使用）；不引入 Kotlin/AndroidX；不联网下载字体；正文不用衬线；不自动提交。
- AI recommendation and rationale: 用设备现成 TTC 提取，避免 GitHub 下载超时；单字重全量 SC 保证用户主题标题不因缺字回退；字体只用于静态标题，动态正文仍 sans。
- User-confirmation decisions: 允许内置中文衬线字体（052）。
- AI-autonomous decisions: ① 取 SC face；② 许可写入 assets 并随 APK 分发；③ 卡片与搜索标题去 serif，与方案 A 的 sans 标题一致；④ 不引入字体子集化工具链（父卡已列为 out of scope）。
- Acceptance evidence: 新 JVM 测试 RED→GREEN；`:app:assembleDebug`；真机截图复核字体生效（059 汇总）。
- Facts: 设备 TTC 26,283,508 字节、5 个 face，SC 为第 3 个（`Noto Serif CJK SC` / `NotoSerifCJKsc-Regular`）；当前 `serif` 出现在 `screen_home_body.xml`、`item_home_topic.xml`、`item_library_topic.xml`、`item_search_hit.xml`；仓库原先无 `res/font/`。
- Assumptions: 提取出的 OTF 为 CFF 轮廓，Android 28+ 可加载；24.4MB 的单字重对个人安装可接受。
- Unknowns: 24.4MB 对 APK 体积与冷启动的影响；厂商 ROM 对资源字体的 fallback 差异。
- Risks: 字体体积过大；若误用于动态长文本会放大 APK 内存占用；漏删卡片 serif 会让方案 A 的 sans 卡片标题漂移。
- Deferred: 字体子集化、多字重、可变字体。
- Non-goals: 改正文、改 Web 字体加载、改业务。
- Skipped non-critical questions and reason: 未追问是否子集化；父卡已将子集化列为 out of scope，本片记录体积实测。

## Goal

让方案 A 的中文衬线只来自一个随 APK 分发的字体资源，并只落在品牌与页面主标题上。

## Scope

### In scope
- `android/app/src/main/res/font/noto_serif_sc.otf`。
- `android/app/src/main/assets/licenses/NotoSerifCJK-OFL.txt`。
- `screen_home_body.xml`、`screen_library_body.xml`、`screen_navigation_body.xml`。
- `item_home_topic.xml`、`item_library_topic.xml`、`item_search_hit.xml`（去 serif）。
- 新 JVM 测试 `ChineseSerifTitleTest`。
- `Docs/04-ux-design/design-system/README.md`、`tokens.json` 的字体说明。

### Out of scope
- 字体子集化、多字重、正文衬线、Web @font-face、页面结构（055–057）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只改字形资源与标题引用；文本、字号、颜色、布局、点击与滚动不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 字体唯一生产资源是 `res/font/noto_serif_sc.otf`；消费者是四个标题 TextView；主题默认仍为 `sans-serif`。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: APK 内含 Noto Serif CJK SC 单字重；品牌与页面标题使用该字体；卡片标题回到 sans。
- Authoritative code/config/migration evidence: `res/font/noto_serif_sc.otf`、`assets/licenses/NotoSerifCJK-OFL.txt`、四处 `fontFamily`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`android/README.md`、`tokens.json` fontFamily note。
- Documentation files updated: 设计系统 README 的字体说明与已知缺口；`android/README.md` 的资源字体说明。
- Producer/consumer/sibling/compatibility paths checked: `themes.xml` 默认 sans、三个 item 布局的 serif、`NavigationScreen`/`LibraryScreen` 的标题绑定。
- Superseded phrases/examples reverse-searched: 「本轮不打包字体」「品牌衬线走回退栈」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保留 2026-09-22 的「本轮不打包」历史记录，追加 2026-10-22 的打包决定。

## Acceptance criteria

- [x] `ChineseSerifTitleTest` 断言字体为 OTTO/CFF、许可存在且含 OFL 1.1 与 Adobe 版权、四处标题引用 `@font/noto_serif_sc`、三个卡片/搜索布局不再有 `fontFamily="serif"`。
- [x] 定向 JVM 命令 RED 非 0、GREEN 为 0，命令一致。
- [x] `:app:assembleDebug` 退出码 0，字体资源被打包（APK 22MB；`res/font/noto_serif_sc.otf` 与 `assets/licenses/NotoSerifCJK-OFL.txt` 均在包内）。
- [x] 全量 JVM 套件仍绿。
- [x] 未自动提交或推送；未新增依赖。

## TDD execution record

### RED — failing test first
- Test/scenario: 字体、许可与标题引用都尚未落地时先跑新测试。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.ChineseSerifTitleTest"`
- Exit code: 1
- Observed failure: `4 tests completed, 4 failed`；四个方法分别落在 `theFontShipsWithItsOflLicence`（许可文件不存在）、`theBundledFaceIsAnOpenTypeCffFont`（资源不存在）、`onlyTheBrandAndPageTitlesUseTheBundledSerif`（四处标题仍无 `@font/noto_serif_sc`）、`cardAndSearchTitlesStaySans`（三个 item 布局仍带 `fontFamily="serif"`）。
- Failure reason validated: 失败都指向「资源/引用尚未落地」，不是测试自身编译或路径错误。
- Evidence: `artifacts/evidence/android-scheme-a-parity-2026-10-22/054-red.txt`

### GREEN — minimum implementation
- Implementation: 从设备 TTC 第 3 个 face 提取 `res/font/noto_serif_sc.otf`（24,427,224 字节，OTTO/CFF）；写入 `assets/licenses/NotoSerifCJK-OFL.txt`（OFL 1.1 + Adobe 2017–2022 版权）；首页品牌/hero、学习库标题、分支导航标题改引用该资源；三个卡片/搜索布局删 `fontFamily="serif"`，回主题 sans。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.ChineseSerifTitleTest"`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`；`artifacts/evidence/android-scheme-a-parity-2026-10-22/054-green.txt`
- Regression evidence: 全量 `./gradlew :app:testDebugUnitTest` 退出码 0（`054-jvm-all.txt`）；`./gradlew :app:assembleDebug` 退出码 0（`054-assemble.txt`）；token guard 退出码 0，69 tokens / 24 resources（`054-token-guard.txt`）。

### REFACTOR — optional
- Cleanup: 无额外重构；字体提取脚本与 TTC 只留在 `artifacts/tmp/`，不进仓库。
- Regression evidence: 同上全量 JVM。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 提取脚本只做一次：`TTCollection(...).fonts[2].save(...)`；不把 TTC 或临时脚本提交进仓库。
- 字体文件放 `res/font/`，资源名 `noto_serif_sc`；不得使用大写或连字符。
- 许可文件放 `assets/licenses/`，随 APK 分发；`Docs` 只引用该文件，不复制第二份。
- 首页 hero 在 055 可能改为副标题；本片只替换字体资源，不改结构。

## Verification report

- Verdict: PASS
- Evidence: `054-red.txt`（rc=1，4/4 失败）→ `054-green.txt`（rc=0）；`054-jvm-all.txt` rc=0；`054-assemble.txt` rc=0；`054-token-guard.txt` rc=0（69/24）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 新增测试未改旧断言；`GlassMaterialTokensTest` 的 token 计数属 053，与本片无关。卡片标题「去 serif」由本片新测试 `cardAndSearchTitlesStaySans` 覆盖，等价于方案 A 的 sans 卡片标题。
- Gaps: 字体体积 24.4MB 让 APK 到约 22MB；冷启动字体内存未做基线对比。真机字形与回退效果由 059 截图复核。
- Risks: 厂商 ROM 对资源字体的 fallback 差异；未来若把该字体应用到动态长文会放大内存。

## Handoff notes

- Completed: 字体资源、OFL 许可、四处标题引用、三处去 serif、JVM 测试、文档同步。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 055 首页壳层与方案 A 结构对齐；`home_hero_title` 若改为 sans 副标题，需按 054 记录的理由调整 `ChineseSerifTitleTest` 的字体断言。
- Watch-outs: 字体是二进制资源，不要用文本工具改写；许可不得删。
