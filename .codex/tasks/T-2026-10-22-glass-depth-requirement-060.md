# Filename: T-2026-10-22-glass-depth-requirement-060.md
# Codex Harness Task: 玻璃「浮起来」与背景质感需求父卡

id: T-2026-10-22-060
title: 玻璃「浮起来」与背景质感需求父卡
template_version: 2
task_type: requirement
status: in_progress
parent_requirement: null
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 052–059 交付后用户复核真机，结论是「玻璃的感觉还不够，不像是浮在上面」，并追加「背景颜色的质感需要加强，不然做成玻璃后反而会觉得不行」。2026-10-22 在 ALP-AN00 首页截图上实测得到四条根因：① 底栏胶囊内部亮度 L≈246、胶囊外雾底 L≈236，玻璃与背景只差 4%，两层分不出来；② 胶囊下缘 2591–2609 行没有任何暗带，Android 侧投影实际为 0；③ 事实源里 `--glass-shadow:0 12px 32px rgba(22,36,28,.07)` 仍是 047–051 之前「不透明表面 + `--sh-1`」时代的值，而方案 A 权威 `.glass` 写的是 `inset 0 1px 0 rgba(255,255,255,.72)` + `inset 0 -1px 0 .12` + `0 20px 40px -28px rgba(...,.42)` + `0 2px 6px .05` 四层，材质换了但阴影/边缘从没跟上；④ `MainActivity#swap` 给宿主加 90dp `tab_bar_clearance`，内容永远不从玻璃下方经过，通透度没有可透之物。背景侧同样实测：页面左右留白的亮度 P5–P95 只有 12 L 跨度，通道均值 R=G=B=236.4（彩度≈0），五个莫兰迪柔光落在玻璃所在位置时完全不显色。
- Users/scenarios: 单人自用 Android 手机（ALP-AN00，1200×2652，density 3.25，Android 13），平板保留 `layout-sw600dp` 边界；Web 前端为同一材质的第二个消费者。场景是首页发问与列表滚动时看见悬浮胶囊、顶栏与卡片浮在雾面场景之上。
- Expected outcome: 玻璃读作「浮在内容之上的一层板」——有投影、有受光上缘、有对角明暗、下方有内容经过；背景读作「有光源、有纵深、有微粒质感的雾」——有可见的明暗跨度与色相漂移，而不是均匀的浅灰。**Android 与 Web 两端同时成立**：Web 虽有真实 `backdrop-filter`，但 `.workspace-topbar` 是列首 `flex:none`、内容同样不从玻璃下方经过，且 `--glass-shadow` 与 `body` 场景数值与 Android 同源同缺，所以两端的读数都必须过同一套判据。绿色仍是唯一动作色，莫兰迪只做柔光与非文字表面。
- Core flow and reversibility: 先立机械判据（J1–J6，见 Acceptance criteria）→ 扩/改 token 链 → Android 材质与场景 → Web 材质、场景与让位 → 真机与浏览器各跑一遍判据 → Android B 档滚动结构改造 → 再跑判据。061/062/063 各自独立可回退：061 只换数值与绘制，062 只换 Android 让位方式，063 只换 Web 材质消费与顶栏让位。
- Inputs/outputs: 输入是方案 A 权威文件的 `.glass` 数值、v1 原型 `:root` token 链、真机截图与 PIL 像素测量；输出是新增/修订的材质与场景 token、Android drawable 与 outline provider、`PaperRuleBackdrop` 场景改造、Web `glass.css`、一个把截图读数变成判决的测量脚本。不新增网络、存储、遥测或依赖。
- State and exception handling: 只改视图绘制、材质数值与滚动容器的让位方式；业务状态、草稿、滚动位置、返回栈、输入法、数据库与 API Key 不变。阅读面固定 86% 不下调（2026-10-21 拍板）。Android 仍不做实时背景模糊（060 明确 Deferred）。`prefers-reduced-transparency` 与不支持 `backdrop-filter` 的降级路径必须继续成立。
- Constraints and trade-offs: Java-only、零 AndroidX、零 Kotlin、无新依赖；设计数值只能来自 token 权威链（原型 `:root` → `tokens.json` → `colors.xml`/`glass.xml` → 生成的 `tokens.css`）；`shape` 渐变在 Android 只支持 45° 倍数，与提案 145° 的最近可表达值是 315°（=CSS 135°），差 10°，按机械等价登记而非改提案；elevation 阴影的几何由系统光源决定，无法照搬 CSS 的 offset/blur/spread，只能由 elevation 档位 + 阴影色逼近，差异登记在 061。不自动提交或推送。
- AI recommendation and rationale: 阴影、背景跨度、内容经过这三件是观感收益的前三名， tint/rim 是第二档；真折射（背景副本 + 降采样近似模糊）成本与风险都最高，且一旦 B 落地后收益重叠，故本轮不做。
- User-confirmation decisions: ① A（材质对齐）+ B（滚动结构）都做；② 接受 B 的滚动结构改动；③ C（真折射/实时模糊）先不做；④ 背景颜色质感必须一起加强；⑤ Web 也要同步修改，不作为 Android 的附庸（2026-10-22 用户明确要求，故单列片 063 独立取证）。
- AI-autonomous decisions: ① 阴影按方案 A 原值强度落 token（`.42` 级），不沿用被证伪的 `.07`；② Android 用自定义 `ViewOutlineProvider` 让阴影与半透明背景解耦，并用 `setOutlineAmbientShadowColor`/`setOutlineSpotShadowColor`（API 28 起可用）取 token 色，而不是手绘假阴影；③ tint 对角两档的浅色端由 `glass-tint-falloff`（.58，与提案 `calc(a1*.58)` 同源）导出，测试重算而非另立字面量；④ 背景加深只动 `mist-*` 与 `glow-*` 的数值，`paper-rule` 维持 2026-09-28 用户嫌粗后调到的 .018，不因加深底而回补横线浓度；⑤ 颗粒（去 banding）先只落在 Android 场景层（绘制成本与 alpha 都能由 token 直接控制）；Web 颗粒列为 Deferred 而非宣布不需要——是否真的可见 banding 要用浏览器截图实测判定，实测到了再补，不凭「浏览器会自抖动」的推定跳过；⑥ 判据脚本对两端截图使用同一取区规则与同一阈值，端差必须显式登记而不是各写一套门限。
- Acceptance evidence: 见 Acceptance criteria 的 J1–J6；判据脚本 `check_glass_depth.py` 对真机截图给出数值与判决，证据落 `artifacts/evidence/android-glass-depth-2026-10-22/`。
- Facts: 方案 A `.glass` 数值在 `Docs/04-ux-design/2026-10-21-玻璃质感重设计提案/A-晨雾玻璃.html` 第 392–404 行；token 权威是 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `:root`；Android 材质面是 10 个 `drawable/bg_glass_*.xml` + `values/glass.xml`，其中 0 个阴影层；场景绘制在 `screen/PaperRuleBackdrop.java`；让位在 `app/MainActivity.java` 的 `swap`（`R.dimen.tab_bar_clearance` = 90dp）；Web 材质在 `frontend/src/styles/glass.css`（`.app-sidebar`/`.workspace-topbar`/`.chat-reading`/`.composer`/`.branch-panel`/`.tree-dialog` 已有真实 `backdrop-filter`，阴影统一引用弱值 `--glass-shadow`），Web 外壳是 `layouts/AppShell.tsx` 的 `.app-workspace`（`display:flex;height:100dvh;overflow:hidden`）+ `.workspace-topbar`（`flex:none;height:76px`，列首不重叠）+ `.workspace-main` 滚动，`body` 场景在 `glass.css` 第 9–28 行。
- Assumptions: elevation 阴影在关闭 `clipChildren` 后能在胶囊下缘形成暗带；`layer-list` 的水平内缩亮带近似得上受光缘；加深后的 `mist-deep` 在真机广色域面板上不显脏。
- Unknowns: 更深的雾面与 `--glass-*` 半透明白叠加后的正文对比余量；颗粒层在 1200×2652 上的绘制成本；062 让位改造对聊天输入法顶起行为的连带影响。
- Risks: 背景加深会让既有的「浅色即干净」判断失效，需真机复核而非只在浏览器里看；062 改变滚动容器属性，可能破坏既有布局断言与末条可见性；改 token 值会同时改到 Web，四方一致守门必须一起绿；投影一旦过强会读成「Material 卡片」而非玻璃。
- Deferred: 真折射（背景副本 + 降采样近似模糊）、API 31 `RenderEffect` 门控、深色模式、Web 结构 1:1。
- Non-goals: 不改业务逻辑、数据库、接口、权限、文案语义；不新增依赖；不引入 AndroidX/Kotlin；不自动提交或推送；不删除用户未提交的改动；不复现 iOS 的动态岛或假状态栏。
- Skipped non-critical questions and reason: 未追问投影是否要按屏分档（顶栏/胶囊/卡片/阅读面各一档）；先在 061 用两档（玻璃面 + 悬浮面）落地，真机读数不足再细分。未追问颗粒是否用位图贴图还是绘制点阵；交给 061 实现并按成本实测。

## Goal

用可复算的像素判据把「玻璃浮在内容之上」与「雾面背景有质感」变成有明暗跨度、有投影、有内容经过的实测状态，并在 Android 与 Web 两端由同一批 token 驱动。

## Scope

### In scope
- 片 061：材质与背景 token（阴影两档、tint falloff、mist/glow 加深）贯通四方，Android drawable/outline/场景消费，以及把截图读数变成判决的测量脚本。方向性 rim 与颗粒两项在 061 实测后撤回，理由见 061 的 AI-autonomous decisions ②/⑤；061 另把提案已有、Android 独缺的顶部受光层补到底栏与侧栏。
- 片 062：Android 内容从悬浮胶囊与顶栏下方经过的让位改造（`clipToPadding` + 滚动容器留白），以及随之而来的绘制顺序与可见性保障。
- 片 063：Web 同步——`glass.css` 消费同一批材质 token、`body` 场景同步加深、`.workspace-topbar` 改为覆盖使内容经过玻璃下方，并用 061 的同一判据在浏览器截图上取证。
- 判据 J1–J6 的定义、实测与登记（Android 与 Web 各一份读数，同一取区规则与同一阈值）。

### Out of scope
- 实时/近似背景模糊（真折射）、深色模式、业务规则与数据、依赖与接口。
- 方案 A 提案 HTML 自身的视觉修改（提案是事实源，不是交付物）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图改变的是材质数值、场景绘制与滚动让位方式；所有既有 View id、命中区域、草稿、滚动位置、返回栈、输入法顶起、数据读取与缓存行为必须不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): token 生产者是 v1 原型 `:root`，消费者是 `tokens.json`、`colors.xml`、`glass.xml`、生成的 `tokens.css`、`GlassMaterialTokensTest`、`PaperRuleBackdrop` 与 Web `glass.css`/`tokens.css`。材质生产者是 `drawable/bg_glass_*.xml` 与各 `screen_*_body.xml` 的 `android:background`，消费者是各 Screen 类与 `layout-sw600dp` 变体。让位生产者是 `MainActivity#swap` 与 `R.dimen.tab_bar_clearance`，消费者是每屏滚动容器。
- Native bridge or independent-client compatibility/update requirements: 不涉及原生桥、数据库或夹具；Android 与 Web 各自消费 token，不互相调用。
- Behavior-neutral non-applicability reason, if applicable: 不适用——062 会改变滚动容器的 padding 归属，属行为相关，必须在 062 内单独取证。

## Documentation impact record

- Changed runtime facts: 玻璃材质新增投影/受光缘/tint 衰减三层；雾面场景数值加深；tab bar 让位方式从宿主 padding 改为滚动容器留白。
- Authoritative code/config/migration evidence: v1 原型 `:root`、`tokens.json`、`colors.xml`、`glass.xml`、`tokens.css`、`drawable/bg_glass_*.xml`、`PaperRuleBackdrop`、`MainActivity`、`frontend/src/styles/glass.css`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`Docs/04-ux-design/2026-10-21-玻璃质感重设计提案/README.md`、`.codex/context/source-of-truth.md`、`.codex/rules/ux-design.md`、`android/README.md`。
- Documentation files updated: 061/062 收口时追加材质与让位记录；不改写 2026-10-21/22 既有拍板正文。
- Producer/consumer/sibling/compatibility paths checked: `check_design_tokens.py` 的 rgba/hex 分派与生成物逐字节校验、`GlassMaterialTokensTest` 计数、`gen_design_tokens_css.py`。
- Superseded phrases/examples reverse-searched: 「Android 不使用实时模糊」保持不变；「12px 32px .07」旧阴影值；「宿主底部内边距让位」旧让位描述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 本卡只新增记录；PRD 6.2.16/6.2.17 的行为条款不变。

## Acceptance criteria

- [ ] J1 玻璃—背景分离：ALP-AN00 首页底栏胶囊内部与 40dp 外雾底的亮度差 ≥ 10 L（改前实测 4 L）。
- [ ] J2 投影成立：胶囊下缘到 8dp 范围内的最低点亮度比 40dp 外雾底低 ≥ 6 L（改前无暗带）。
- [ ] J3 背景有质感：页面留区亮度 P5–P95 跨度 ≥ 24 L（改前 12 L），且通道色差（R−B 的 P95−P5）≥ 6（改前约 0.3）。
- [ ] J4 内容真的经过玻璃下方：列表滚动时胶囊区域内同一像素行的亮度随内容变化 ≥ 8 L，且 `animator_duration_scale` 无关。
- [ ] J5 判据脚本 `check_glass_depth.py` 对 J1–J4 给出数值与判决，`--selftest` 正反用例全绿。
- [ ] J6 一致性：`check_design_tokens.py`、`gen_design_tokens_css.py --check`、`GlassMaterialTokensTest`、`:app:testDebugUnitTest` 全绿，Web 回归全绿，`run_all_guards.py --tier core` 无红。
- [ ] 阅读面仍为 86%、正文对比不回退、`prefers-reduced-transparency` 与不支持 `backdrop-filter` 的降级仍生效；未自动提交或推送；未新增依赖。

## TDD execution record

### RED — failing test first
- Test/scenario: 待 061 执行。判据脚本与 token 断言先于数值落地。
- Command: 待 061 执行。
- Exit code: 待 061 执行。
- Transcript: 待 061 执行。
- Observed failure: 待 061 执行。
- Failure reason validated: 待 061 执行。

### GREEN — minimum implementation
- Implementation: 待 061/062 执行。
- Command: 待 061/062 执行。
- Exit code: 待 061/062 执行。
- Passing evidence: 待 061/062 执行。
- Regression evidence: 待 061/062 执行。

### REFACTOR — optional
- Cleanup: 待执行。
- Regression evidence: 待执行。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本卡是需求父卡，不承担实现；两片按各自 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- rgba→ARGB 按 `round(alpha*255)` 与 `#AARRGGBB` 大写；资源名把 `-` 换成 `_`；rgba token 不进 `colors.xml`，进 `values/glass.xml`。
- 阴影几何不能整条搬进 Android：CSS 的 offset/blur/spread 在原生只由 elevation 档位与系统光源决定，因此落「阴影色 + elevation 档」两件事，几何差异登记在 061 而不是伪装成等价。
- 判据测量用真机截图，亮度取 `0.2126R+0.7152G+0.0722B`，区域取法在脚本里固定，避免每次手挑像素。解码走仓库已有的纯 Python `parity/png_probe.py`（不引入 PIL：项目底线是不新增依赖）。
- 061 实测后的落点（追加，不改写上面的拍板）：J1 缺口不在 tint 数值而在**受光层缺失**——底栏与侧栏是全 app 唯二没有 `glass_sheen` 的玻璃面，补上之后 J1 从 9.5 跳到 19.6 L；rim 与颗粒按「不做没有病可治的数值」撤回；仪器的找面规则从「上下缘跳变配对」换成「同一行比左右留白亮多少」，因为真投影本身就是一条软坡，会把逐行跳变读成「面贴到了画面底」。

## Verification report

- Verdict: 待 061/062 收口。
- Evidence: 待补。
- Changed/deleted assertion rationale and equivalent behavior coverage: 待补。
- Gaps: 待补。
- Risks: 待补。

## Handoff notes

- Completed: 根因与基线数值已实测登记（玻璃—背景 4 L、投影 0、留区跨度 12 L、彩度≈0）。注意：这四条是判据脚本落地前的手量数，权威基线以 `artifacts/evidence/android-glass-depth-2026-10-22/061-baseline-before.txt` 为准（J1 9.8 / J2 0.2 / J3 跨度 7.7 / 彩度 3.0）——手量把「页面中部留白」当成了玻璃所在的背景，读数和判据取区不是同一块像素。
- In progress: 061 已出真机全绿读数，待 core 守门整跑后 verified；063 Web 同步待执行；062 滚动让位待执行。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 061 先立判据脚本与 token，再改 Android 绘制与 Web 消费；J1–J3 达标后才开 062。
- Watch-outs: 不要把旧 `--glass-shadow` 的 .07 当作设计事实保留；不要为让 J2 变绿而手绘与 elevation 并存的第二套阴影；不要在 062 里同时改材质。
