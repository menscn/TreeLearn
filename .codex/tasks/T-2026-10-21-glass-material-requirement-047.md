# Filename: T-2026-10-21-glass-material-requirement-047.md
# Codex Harness Task: 晨雾玻璃材质重设计需求父卡（A + C 高光边 + 更强通透）

id: T-2026-10-21-047
title: 晨雾玻璃材质重设计需求父卡
template_version: 2
task_type: requirement
status: verified
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

- Problem/background: 用户已从 `Docs/04-ux-design/2026-10-21-玻璃质感重设计提案/` 的三套方向中选定 **A 晨雾玻璃** 为底，叠加 **C 浮光** 的 1px 高光边缘，并要求比 A 更强的玻璃通透感。当前生产界面（Android 原生与 Web 工作区）仍是 2026-09-22 的纸感方案：不透明 surface、细描边、无玻璃层。用户明确要求本轮按 Harness 落地，而不是继续停留在提案。
- Users/scenarios: 单人自用、Android 手机为主（平板横屏保留），Web 工作区用于桌面阅读与对照。场景：首页输入问题、对话阅读长回答、分支导航、学习库扫描、设置查看、底部 tab 切换；透明玻璃不能牺牲长文阅读与弱化文字的对比度。
- Expected outcome: 在不改 IA、文案、业务逻辑、数据与依赖的前提下，把选定材质落到共享设计 token、Android 原生界面与 Web 工作区：雾面场景背景、半透明 chrome、独立阅读面、1px 白色高光边、22/28 圆角、克制投影；Android 无 `backdrop-filter` 时以柔焦场景＋半透明面实现同等观感，并在文档中如实说明。
- Core flow and reversibility: 改 token 链（原型 `:root` → `tokens.json` → `colors.xml` → 重新生成 `tokens.css`）→ Android 共享 drawable／背景 view 与 Web 材质覆盖层消费同一批 token → 守门与真机/浏览器取证。回退为逐个任务删除新增 token、材质资源与 `glass.css`，现有页面结构与业务代码不动。
- Inputs/outputs: 输入是选定的 A 提案参数（玻璃 62%、blur 28px、saturate 150%、reader 86%、edge 72%、圆角 22/28）与 C 的 edge 88%；输出是新增 `glass-*`／`glow-*`／`mist-*` token、Android `values/glass.xml` 与场景绘制、Web `glass.css`，以及截图与读数证据。不产生新的网络、存储或业务副作用。
- State and exception handling: 材质只影响绘制。Android API < 31 不做实时背景模糊，使用静态雾面场景与半透明面；Web 在不支持 `backdrop-filter` 或用户开启减少透明时回退到更高不透明度的 surface。任何回退都不得改变布局、命中区域或文案。
- Constraints and trade-offs: Java-only、零 AndroidX、零 Kotlin、无新依赖；不改 SQLite/后端/接口；不自动提交或推送；设计数值只能有一份权威正文。透明与可读性的取舍固定为 **chrome 更透、阅读面保持 86%**：标题、正文与输入内容不因玻璃而降低到 AA 以下。
- AI recommendation and rationale: 先扩 token 链再改两个消费者。Android 侧以一面全屏雾面场景＋共享玻璃 drawable 获得最大覆盖面，避免逐屏重写 40 个 layout；Web 侧用独立 `glass.css` 覆盖现有 surface，不改 DOM。两侧都把 1px 高光边做成共享表面的一部分，而不是每张卡片各写一份。
- AI-autonomous decisions: ① 本轮同时覆盖 Android 与 Web（两者共用同一 token 链，只做 Android 会留下第二套视觉事实）；② Android 先落地，Web 依赖 token 后并行；③ 不改 IA、文案、交互与业务状态；④ 取值定为 chrome `rgba(255,255,255,.54)`、card `.72`、reader `.86`、field `rgba(244,241,235,.50)`、edge `.88`、line `rgba(22,36,28,.06)`、blur `28px`、saturate `150%`、圆角 `22/28`；⑤ Android 不伪造实时模糊，API 28+ 一律使用柔焦场景；⑥ 新增 `values/glass.xml` 作为 rgba token 的 Android 副本，并由 JUnit 读取 `tokens.json` 自动比对，避免第五份手抄清单。
- User-confirmation decisions: 方向 A＋C 1px 高光边＋更强通透；按 Harness 开发。其余技术取舍由本卡记录为 AI 自主决定。
- Acceptance evidence: 048 的 token 守门 RED/GREEN；049 的 Android 单测 RED/GREEN、`:app:assembleDebug`、`:app:verifyNoKotlin`、`adb install -r` 后真机截图；050 的 Vitest RED/GREEN、`pnpm --dir frontend verify`、桌面与窄窗浏览器截图；051 的整跑、`validate_tasks.py`、`run_all_guards.py --tier core` 与证据目录。
- Facts: 设计 token 当前由原型 `:root`（行号由守门读取）、`tokens.json`、`colors.xml`（不透明 hex 子集）与生成的 `tokens.css` 四方构成；Android 由单 Activity 外壳与 40 个 layout 组成，共享 surface 集中在 `bg_card`、`bg_library_card`、`bg_topic_card`、`bg_composer_inner`、`bg_field`、`bg_library_search` 等 drawable；`PaperRuleBackdrop` 是唯一的全屏背景 view。Web 工作区外壳在 `frontend/src/styles/workspace.css`，页面在 `pages.css`，`main.tsx` 顺序加载样式。提案页已用 Playwright 验证，但生产未实现。
- Assumptions: A 的圆角 22/28 作为新增 `r-xl`/`r-2xl`，不改既有 r-sm/r-md/r-lg；Android 平板与手机共用同一材质；真机 API 级别支持 8 位 ARGB 颜色资源。
- Unknowns: 真机厂商对半透明白色叠加的色偏程度；Web 在低端设备上多层 `backdrop-filter` 的滚动性能。本轮以实测截图与降级规则覆盖，不做性能预算承诺。
- Risks: 54% 透明度过低会削弱小字对比；Android 的 `backgroundTint` 会将部分玻璃面重新变成不透明（聊天错误/用户消息卡片），需保留其语义色；tab bar 的玻璃背景会让既有“透明底栏”设备断言失效，必须同步改为“非不透明”判据；token 行号与旧 README 的 44 token 叙述会过期，需要登记。
- Deferred: 实时背景模糊（Android）、动态壁纸取色、深色模式、玻璃材质的动效、低端设备帧率基准。
- Non-goals: 不改页面结构、不改文案、不改业务逻辑与数据库、不新增依赖、不引入 AndroidX/Kotlin、不做账号/同步/遥测、不自动提交或推送。
- Skipped non-critical questions and reason: 没有追问高光边的方向与强度分布、卡片阴影的具体扩散半径；这些不改变目标或验收，由统一材质层与截图复核决定。没有追问是否保留纸感横格；保留（A 的场景含纸感），因为它不增加成本且维持品牌连续性。

## Goal

把用户选定的「A 晨雾玻璃 + C 1px 高光边 + 更强通透」落成 Android 与 Web 的共同生产材质：同一批 token、共享玻璃表面、柔焦雾面场景、阅读面保可读性，并以守门、构建、真机与浏览器证据收口。

## Scope

### In scope
- 片 048：扩设计 token 链（原型 `:root`、`tokens.json`、`colors.xml`、生成的 `tokens.css`）与设计系统变更记录。
- 片 049：Android 雾面场景、`values/glass.xml`、共享玻璃 drawable、tab bar/抽屉/输入区/卡片、主题状态栏、圆角 dimens，以及读取 `tokens.json` 的 JUnit 映射测试与结构测试。
- 片 050：Web 工作区材质覆盖层 `glass.css`（场景、chrome、reader、卡片、字段、回退）与结构测试。
- 片 051：整跑、真机 `adb install -r` 截图、Playwright 桌面/窄窗截图、文档同步与 Task v2 收口。

### Out of scope
- IA、DOM/布局结构、文案、业务状态、导航、数据与接口。
- 实时 Android 背景模糊、深色模式、新字体、新图标、新依赖。
- 提案 HTML 与三套方向稿的视觉修改。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 外观从纸感不透明面变为雾面玻璃；所有可点区域、滚动、输入法、返回栈、数据读取与文案不变。阅读面保持 86% 白，正文与小字对比度不低于既有 AA 结论。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): token 生产者是原型 `:root`；消费者是 `tokens.json`、`colors.xml`、生成的 `tokens.css`、Android `values/glass.xml` 与 `glass.css`。Android 背景唯一生产者是 `PaperRuleBackdrop`；Web 表面分布在 `workspace.css`／`pages.css`，由新 `glass.css` 统一覆盖。
- Native bridge or independent-client compatibility/update requirements: 不涉及原生桥、夹具或数据库；Android 与 Web 各自消费 token，不互相调用。
- Behavior-neutral non-applicability reason, if applicable: 材质层不改变 View 层级（除 tab bar 去掉一根装饰分隔线）与 DOM 结构。

## Documentation impact record

- Changed runtime facts: 生产界面材质与新增 token；Android 无实时模糊、使用柔焦场景；Web 使用 `backdrop-filter` 并在不支持时回退；tab bar 由透明变为半透明玻璃。
- Authoritative code/config/migration evidence: 原型 `:root`、`tokens.json`、`colors.xml`、`tokens.css`、`values/glass.xml`、`AppShell`/`glass.css`、`PaperRuleBackdrop`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`android/README.md`、提案目录 `README.md`、`.codex/rules/ux-design.md`、`.codex/rules/android.md`、`.codex/rules/frontend.md`、`.codex/context/source-of-truth.md`。
- Documentation files updated: 048 更新设计系统变更记录与源行号；049 更新 Android README 的材质与降级说明；051 更新提案 README 的落地状态并写证据说明。
- Producer/consumer/sibling/compatibility paths checked: `screen_tab_bar` 的透明底断言、`HomeDrawerScreen` 的背景、聊天输入区、`bg_card` 的 tint 用法、Web 的窄窗侧栏覆盖层。
- Superseded phrases/examples reverse-searched: 「tab bar 必须是透明背景」「纸感不透明 surface」「44 token」——前者改写为「非不透明」，后两者追加 2026-10-21 材料说明，不改写历史记录。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 2026-09-22 的纸感设计记录保留；本次只追加材质变更，不改写历史结论。

## Acceptance criteria

- [x] 设计 token 四方守门与自检全绿；新增玻璃 token 在四处取值一致。
- [x] Android 构建、单元测试与无 Kotlin 守门绿；真机 `install -r` 后首页、对话、学习库、设置可见玻璃场景与高光边，且既有数据未被清理。
- [x] Web 类型检查、测试、lint、build 与设计 token 守门绿；桌面与窄窗截图可见玻璃外壳、阅读面与高光边。
- [x] 所有既有可点区域、文案、业务逻辑、数据库与接口不变；tab bar 的设备断言改为“非不透明”并仍能防止遮住背景。
- [x] `python -X utf8 .codex/scripts/validate_tasks.py` 与 `python -X utf8 .codex/scripts/run_all_guards.py --tier core` 退出码 0，或如实记录既有红项。
- [x] 文档同步：设计系统变更记录、Android 材质说明、提案落地状态与证据 README。
- [x] 未自动提交或推送；未新增依赖；未删除用户未提交的改动。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（需求父卡）
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
- Reason: 父卡只承载已确认需求；实现由 048–050 各自 TDD，051 做整跑与取证。
- User approval: 用户 2026-10-21 已确认方向与 Harness 调度。
- Alternative verification: 子卡 RED/GREEN 与真机/浏览器证据。

## Implementation notes

- 任务顺序：047 → 048 →（049 ∥ 050）→ 051。
- 玻璃取值以本卡 AI-autonomous decisions 为准；任何调整必须先改原型与 tokens.json，再让两个消费者跟随。
- Android 只新增/改写共享资源与场景 view，不逐屏复制表面；聊天里带语义色的卡片保留其 tint。
- Web 把材质集中在 `glass.css`，通过加载顺序覆盖现有 surface，不修改组件树。
- 真机验证只用 `adb install -r`，不跑会卸载应用的整包仪器测试。

## Verification report

- Verdict: PASS
- Evidence: 048 token 链 RED→GREEN（63 token / 20 资源 / 41 自检例，生成 `tokens.css` 2065 字节逐字节一致）；049 Android 定向 RED 11 例中 10 红 → GREEN，全量 JVM 96 个结果文件 859 例 0 失败，`assembleDebug` + `assembleDebugAndroidTest` + `verifyNoKotlinDex` 通过，真机 `adb install -r` 后首页/对话/学习库/设置可见玻璃场景与高光边且原始数据已恢复；050 Web RED 4 红 / 107 绿 → 111/111，`pnpm --dir frontend verify` 16/16，浏览器 8/8 读数与 8 张桌面/窄窗截图；051 的 `validate_tasks.py`（46 文件、0 错误）、`check_context_pack`、`check_source_of_truth`、`metrics_report` 与 `checkers_selftest`（268/268）均为 0，核心整跑 37 绿、2 条浏览器仪器超时。原始记录集中在 `artifacts/evidence/glass-material-2026-10-21/`。
- Changed/deleted assertion rationale and equivalent behavior coverage: `HomeBackdropInstrumentedTest` 的 tab bar 判据由「透明或 null」改为「非 null 且 `getOpacity() != PixelFormat.OPAQUE`」，仍保留「页面不各自拥有第二份背景」的核心断言，继续防止底栏遮住场景；`ChatMotionLayoutStabilityTest` 的失败条仍断言软红 token，只把承载方式从 `bg_card + tint` 换成独立的 `bg_semantic_alert`，等价行为覆盖未减少。
- Gaps: 核心整跑无法在本机退出 0——两条浏览器仪器打印 PASS 后进程被 Edge 句柄拖住并撞 600s 上限（2026-10-20 基线为 39/39 全绿，详见 051）；未安装 Playwright，浏览器证据使用仓库既有 CDP/Edge 驱动；Android 不做实时模糊；平板横屏未单独截图；对话/学习库玻璃读数来自临时种子库并已恢复；未做厂商色偏、正式对比度仪器测量与低端设备帧率基准。
- Risks: 半透明白在部分厂商屏幕可能色偏；低端机多层半透明与 Web `backdrop-filter` 可能有帧率损失；后续新增表面若继续用实色 drawable 会绕过玻璃层；`backgroundTint` 若重新施加到玻璃 drawable 会抹掉 1px 高光边；生成型计数若写进正文会让事实源守门再次判红。

## Handoff notes

- Completed: 047 需求裁决；048 token 链；049 Android 材质；050 Web 材质；051 整跑、真机与浏览器取证及文档收口。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 后续新增页面沿用 `bg_glass_*`、`glass.xml` 与 `glass.css`；兼容性扩展前先处理 051 登记的 GAP。
- Watch-outs: 不要把玻璃做成半透明但文字失去对比；不要让 Android 假装有实时模糊；不要把 Web 与 Android 的设计值各写一份。
