# Filename: T-2026-10-22-web-glass-depth-063.md
# Codex Harness Task: Web 同步玻璃材质、雾面场景与顶栏让位

id: T-2026-10-22-063
title: Web 同步玻璃材质、雾面场景与顶栏让位
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-glass-depth-requirement-060.md
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: frontend
stack: frontend
estimated_production_lines: 90
shared_code_touched: true
depends_on: [T-2026-10-22-glass-material-depth-061.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 用户 2026-10-22 明确要求「Web 也要同步修改」，不接受 Web 只做 token 副本的消费而不改观感。实测的 Web 缺口与 Android 同源且更隐蔽：`frontend/src/styles/glass.css` 的六个玻璃面已经挂真实 `backdrop-filter: blur(28px) saturate(150%)`，但阴影统一是弱值 `--glass-shadow:0 12px 32px rgba(22,36,28,.07)`，没有方案 A `.glass` 的上缘亮 rim 与下缘暗 rim；`body` 场景用的还是 `mist-base #F3F4EE / mist-deep #ECEEE6`（跨度不足 3 L 的伪雾面）；`.workspace-topbar` 在 `.app-workspace` 的列首是 `flex:none;height:76px`，滚动发生在其后的 `.workspace-main`，内容从不经过顶栏下方——真实模糊没有任何可模糊之物，所以 Web 的「玻璃感」比 Android 更接近一块同色贴片。
- Users/scenarios: 桌面宽窗（常驻侧栏 + 顶栏 + 滚动主区）与窄窗（覆盖侧栏 + 遮罩）；`prefers-reduced-transparency` 与不支持 `backdrop-filter` 的浏览器降级路径；采集快照栈 `snapshot.css` 保持自身盒模型不受影响。
- Expected outcome: Web 端 J1–J4 与 Android 用同一取区规则和同一阈值各测一次并达标——玻璃面有可测投影与对角 tint；雾面有可测明暗跨度与色相漂移；滚动时列表内容经过顶栏下方并被真实模糊压住。
- Core flow and reversibility: 061 落定 token 后，改 `glass.css` 材质消费 → 同步 `body` 场景 → 顶栏改覆盖 + 主区滚动留白 → 浏览器截图跑判据 → 全量 Web 回归。撤销是逐条恢复 CSS 块，不动业务组件。
- Inputs/outputs: 输入是 061 的 token 集合与方案 A `.glass` 四层；输出是 `frontend/src/styles/glass.css` 的材质/场景/让位改造、`tokens.css` 的生成物一致性、浏览器截图证据与新增的 CSS 断言。不新增依赖、不新增资源文件。
- State and accessibility/exception handling: 业务状态、草稿、筛选、阅读位置、焦点顺序与命中区域不变；`.nav-scrim` 窄窗遮罩的 z-index 与定位不变；顶栏改覆盖后其内部按钮必须仍可点、不被主区内容遮挡；不支持 `backdrop-filter` 与 reduced-transparency 两条既有降级块必须继续成立（它们决定加深后的雾面上正文对比是否仍达标）。
- Constraints and trade-offs: 设计数值只能来自 `tokens.css`，CSS 里不得出现裸色值（`check_design_tokens.py` 的裸 hex 判据按文件路径收录、不解析注释与字符串，写死数值必被点名）；颗粒噪声不在本片引入——Web 是否可见 banding 要用截图实测判定，实测到了另开增量，不凭推定跳过也不凭推定实现；顶栏覆盖会让主区首屏可视高度减少，需与既有断点（760px）共存。
- AI recommendation and rationale: 优先把已有真实模糊用起来（让位），其次才是阴影与 tint 数值；两端共用一套判据而不是给 Web 单独放宽门限，否则「同步修改」无法机械验收。
- User-confirmation decisions: Web 也要同步修改（2026-10-22 明确要求）；背景质感要加强；接受让内容经过玻璃下方的结构改动。
- AI-autonomous decisions: ① 顶栏覆盖用绝对定位 + 主区顶部留白实现，不改 `.app-workspace` 的 flex 主轴结构；② 投影写成 `box-shadow` 多层并全部引用 token 变量，不引入新 CSS 自定义属性；**方向性 rim 不做**——061 实测后已把 rim 两档从 token 链撤回（Android 只能画整圈边，给不出方向性），Web 单独保留一对没人消费的 rim 值就是死数值；③ 窄窗覆盖侧栏与遮罩层级保持现状，只验证不受顶栏改造影响；④ 判据脚本取区按截图尺寸比例取，Android 与 Web 复用同一份；⑤ 对角 tint 的暗端用 `color-mix(in srgb, <tint> calc(var(--glass-tint-falloff) * 100%), transparent)` 从 falloff token 推导，与 Android 的 `glass_*_lo` 副本同源，不各写一份字面量；⑥ **本片负责从四方链删除 `--glass-shadow`**（061 刻意留着它，因为 Web 是最后一个消费者，提前删会让 `glass.css` 引用悬空）。
- Acceptance evidence: Web 截图上 J1（玻璃—背景 ≥10 L）、J2（投影暗带 ≥6 L）、J3（留区跨度 ≥24 L 且彩度 ≥6）、J4（滚动时顶栏区域内同一像素行亮度变化 ≥8 L）；`pnpm --dir frontend verify`、`test`、`lint` 与四方守门全绿；证据落 `artifacts/evidence/android-glass-depth-2026-10-22/`（同目录内以 `web-` 前缀区分）。
- Facts: 现材料消费者清单在 `glass.css` 第 30–45 行（共享 1px 边 + `--glass-shadow`）与第 47–160 行（六个玻璃面各自 `backdrop-filter`）；`body` 场景在 `glass.css` 第 9–28 行；顶栏与主区规则在 `workspace.css` 第 179–187 与 232 行；外壳结构在 `layouts/AppShell.tsx` 第 68 与 125 行；降级块在 `glass.css` 第 167–201 行。
- Assumptions: 顶栏绝对定位后其背景模糊仍能正确采样下方滚动内容（Chromium 的 `backdrop-filter` 与定位层级无冲突）。
- Unknowns: 顶栏覆盖后长列表首屏内容的可读性（雾面加深 + 顶栏变淡的叠加）；Web 侧是否真的出现 banding。
- Risks: 顶栏改覆盖可能遮住主区首个可交互元素或破坏 `position:sticky` 的既有表格头；投影加深在白色 UI 上读成灰脏；`--glass-shadow` 改名会让未更新的 CSS 引用变成 `undefined` 静默失效——必须由四方守门与一次全量 grep 共同兜住。**判据对全宽贴边面的已知盲区（061 落地时发现，先登记再取证）**：`_background` 遇到贴边面退到四角场景中位，而顶栏自己就占住上面两角，J1 可能读成 ≈0 或直接判仪器故障。处置顺序是先换被测面（`--band top` 之外再读一块不贴边的玻璃面，例如 `.chat-reading`）并把选择写进证据，而不是给 Web 单独放宽阈值、也不是改脚本取区凑数——两端同一判据是「同步修改」能被机械验收的前提。
- Deferred: Web 颗粒（等截图实测判定）、Web 结构 1:1、深色模式。
- Non-goals: 不改业务组件与 store、不改 `snapshot.css` 采集栈、不新增依赖或资源、不改 061 的 token 数值定义、不自动提交或推送。
- Skipped non-critical questions and reason: 未追问是否给 Web 顶栏也做滚动渐浓；先让既有模糊起作用，避免同一片里叠两个未验证变量。

## Goal

Web 端在与 Android 同一套像素判据下达到「玻璃浮在内容之上、雾面有质感」，且两条既有降级路径不回退。

## Scope

### In scope
- `glass.css`：阴影两档 + 对角 tint（falloff 推导）消费新 token，删除 `--glass-shadow` 引用；`body` 场景同步 mist/glow 数值（061 已把四色与五光加深，Web 侧只是把新值用起来）。
- `.workspace-topbar` 覆盖化与 `.workspace-main` 滚动留白，使内容经过玻璃下方。
- 浏览器截图判据取证（J1–J4）与既有降级块回归。
- Web 测试/lint/verify 与四方守门全绿。

### Out of scope
- token 数值定义与 Android 绘制（061）、Android 让位（062）、Web 颗粒（Deferred）、业务组件。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图改变顶栏的定位归属与主区留白、玻璃材质数值与场景数值；必须保持滚动位置与阅读位置恢复、筛选与草稿、焦点顺序、命中区域、窄窗遮罩行为与两条降级路径。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 数值生产者 v1 原型 `:root` → `tokens.json` → 生成的 `tokens.css`；消费者 `glass.css` 与 `pages.css`/`workspace.css` 的材质声明。让位生产者是 `workspace.css` 的顶栏与主区规则，结构消费者是 `layouts/AppShell.tsx`；快照栈 `snapshot.css` 是兄弟实现，不在改造面内但须确认未被波及。
- Native bridge or independent-client compatibility/update requirements: 不涉及夹具与后端接口；`sync_fixtures.py --check` 必须继续绿。
- Behavior-neutral non-applicability reason, if applicable: 不适用——顶栏定位归属变化属行为相关，需真机浏览器取证。

## Documentation impact record

- Changed runtime facts: Web 玻璃面阴影/tint 变为方案 A 的四层里 token 能表达的那几层（rim 已随 061 撤回）；`body` 雾面数值加深；顶栏由列首改为覆盖，内容经过玻璃下方。
- Authoritative code/config/migration evidence: `frontend/src/styles/glass.css`、`tokens.css`（生成物）、`workspace.css`、`layouts/AppShell.tsx`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`.codex/rules/frontend.md`、`.codex/rules/ux-design.md`、`frontend/README.md`。
- Documentation files updated: 设计系统 README 的 token 变更记录追加 Web 消费条目。
- Producer/consumer/sibling/compatibility paths checked: 一次全量 grep `--glass-shadow` 引用点；`check_design_tokens.py` 的裸 hex 判据与生成物逐字节校验。
- Superseded phrases reverse-searched: 「Web 只消费 token，不改结构」（052 线的旧边界，本片由用户明确要求取代）、「0 12px 32px rgba(22,36,28,.07)」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 记录本片的结构改动来自用户 2026-10-22 的同步要求，不改写 052 的当时边界声明。

## Acceptance criteria

- [x] `glass.css` 不再引用被取代的 `--glass-shadow`；全量 grep 无残留引用。
- [x] CSS 无裸色值：`check_design_tokens.py` 与 `gen_design_tokens_css.py --check` 退出码 0。
- [x] Web 截图判据：J1 ≥10 L、J2 ≥6 L、J3 跨度 ≥24 L 且彩度 ≥6、J4 ≥8 L，与 Android 同一取区规则同一阈值。
- [x] 顶栏覆盖后主区首个交互元素可点、命中区域正确；窄窗遮罩与 `prefers-reduced-transparency`/不支持 `backdrop-filter` 两条降级仍生效。
- [x] `pnpm --dir frontend verify`、`test`、`lint` 全绿；core 守门无红。
- [x] 未新增依赖或资源文件；未自动提交或推送。

## TDD execution record

### RED — failing test first
- Test/scenario: `frontend/test/styles/glass-depth.test.ts`（新建，6 条）——① 被取代的 `--glass-shadow` 在链与消费面都清空；② 投影两档各引用一枚 token 且几何照抄方案 A；③ 四面 tint 暗端由 `--glass-tint-falloff` 推导；④ 顶栏是覆盖层、滚动容器自己付留白；⑤ 两条既有降级与窄窗侧栏仍在；⑥ 材质层不写裸色值。另有像素 RED：J4 在无让位结构下的零漂移对照（见 Verification report）。
- Command: `pnpm --dir frontend exec vitest run test/styles/glass-depth.test.ts test/styles/glass.test.ts`
- Exit code: 1
- Transcript: `artifacts/evidence/android-glass-depth-2026-10-22/web-063-red.txt`（逐字，含四条断言的 Expected/Received 全文）：`Tests 4 failed | 6 passed (10)`。
- Observed failure: ①②③④ 四条红——`glass.css` 仍写 `var(--glass-shadow)`、只有一档弱投影、tint 暗端没人推导、`.workspace-topbar` 规则体是 `height:76px; flex:none; …`（`expected … to match /position:\s*absolute/`）。
- Failure reason validated: 同一批里 ⑤⑥ 与 `glass.test.ts` 四条**是绿的**，说明测试不是恒红，它精确描述「还没做」。RED 之后补的第 7 条（窄窗栏高与留白成对）来自实测缺陷复现，不在本条 RED 的 4 红里；它对着旧 CSS 同样会红（旧文件既没有窄窗 `.chat-scroll`，也没有窄窗 `.page-scroll` 覆盖）。曾尝试用 `git show HEAD:` 复现整批 RED，实测确认 `glass.css`/`workspace.css`/`pages.css` 在本工作线是**未跟踪文件**（`git status --short` 三条 `??`），HEAD 里没有旧版可取，复现不成立；备份与还原已用 sha256 逐字节校验回原状，三个文件未被这次尝试改动。

### GREEN — minimum implementation
- Implementation: ① `glass.css` 头注释改为「本文件也负责浮起来」；共享块把 `var(--glass-shadow)` 换成两档 `0 20px 40px -28px var(--glass-shadow-key)` + `0 2px 6px var(--glass-shadow-ambient)`，`.composer:focus-within` 同步；② 四面 tint 改 `linear-gradient(145deg, <tint>, color-mix(in srgb, <tint> calc(var(--glass-tint-falloff) * 100%), transparent))`，chrome/reader/card/field 四组各一条，上缘受光层 `linear-gradient(180deg, var(--glass-sheen), transparent 22%)` 只留给三个大面；③ `.workspace-topbar` 由列首 `flex:none` 改为 `.workspace-column`（新增 `position:relative`）的 `position:absolute; inset:0 0 auto 0; z-index:2` 覆盖层；④ `.chat-scroll`（workspace.css）与 `.page-scroll`（pages.css）各加 `padding-top:76px`，`@media (max-width:760px)` 内两条跟着 `height:64px` 改 `padding-top:64px`；⑤ 从四方链删除 `glass-shadow`：原型 `:root`、`tokens.json`、`colors.xml`/`glass.xml` 无引用、生成器重出 `tokens.css`；⑥ 仪器 `check_glass_depth.py` 只补取景规则（贴边顶栏先找下缘、边缘扫描改窗口中位加最陡列），阈值与判据条数一个没动。
- Command: `pnpm --dir frontend exec vitest run test/styles/glass-depth.test.ts test/styles/glass.test.ts`
- Exit code: 0
- Passing evidence: 同一命令重跑 → `Tests 11 passed (11)`，逐字 transcript 留档 `web-063-green.txt`。像素判据：`python -X utf8 .codex/scripts/dev/check_glass_depth.py --shot web-063-home-top.png --band bottom` → `VERDICT: PASS`（J1 19.4 / J2 41.6 / J3 41.8 跨度 + 17.0 彩度）；`… --shot web-063-narrow-chat-top.png --band top --drift-before web-063-narrow-chat-top.png --drift-after web-063-narrow-chat-scrolled.png` → `OK J4 玻璃下内容经过 = 11.3 L 变化（需 ≥ 8）`。
- Regression evidence: `pnpm --dir frontend verify` → `verify steps=16 ok=16 bad=0 fault=0`、`OVERALL_EXITCODE=0`（lint 全量 vitest build 与六条前端静态守门都在其中）；`check_design_tokens.py` → `OK=10 FAIL=0 SKIPPED=0`；`gen_design_tokens_css.py --check` → `byte-for-byte … bytes=2306 tokens=71`；`check_glass_depth.py --selftest` → `cases=14 failures=0`；Android 四条读数逐字节复现（061 GREEN 19.6/12.4/26.2/13.0、062 GREEN 同 box J4 35.1、两条 RED 原读数不变）；`verify_063.mjs` 十条交互与降级实测全 OK（顶栏控件命中、窄窗抽屉 z=20 与遮罩 z=19 未被顶栏 z=2 影响、reduced-transparency 下玻璃面 `backdrop-filter` 全 none、默认媒体仍 `blur(28px) saturate(1.5)`）。全量 grep：`--glass-shadow` 仅剩测试断言、注释与历史卡片/证据文本，生产面无引用。`position: sticky` 在 `frontend/src/styles` 零命中，父卡担心的表头遮挡不存在。证据与逐帧读数在 `web-063-after-reading.txt`。

### REFACTOR — optional
- Cleanup: `_step_edge` 的 docstring 原本写着未经复测的「J1 从真实的 +35 L 掉到 2.6 L」与「真边缘实测 37 L」，改为可复现的 33.7→2.6 与同一条线一步跳 38.0 L（用 `drift_probe` 同法在 `web-063-narrow-top.png` 的 x47→x48 量得）；新增第 7 条断言把「窄窗栏高与留白成对」钉住，并把这条约束的权威落点写成注释指向彼此（`workspace.css` 与 `pages.css` 各一处，不新增 token）。
- Regression evidence: 同一 vitest 命令 11 例全绿；`check_source_of_truth` 由 1 红转绿（061 变更记录里「Android 10 个 `bg_glass_*.xml`」命中「生成型事实不得写进散文」的锚点，改为不写数量）。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 判据取区按截图短边比例定义（胶囊/顶栏内部取几何中心线，40dp 等效外距取 `w*0.033`），Android 与 Web 复用 `check_glass_depth.py` 的同一份实现。
- 顶栏覆盖只改定位与留白，不改其在 `AppShell.tsx` 里的 DOM 顺序与 `data-layer` 标记。
- 所有阴影/tint 写成 `var(--token)`，任何需要新数值的情况回到 061 改 token，不在 CSS 里发明。
- 每条判据主张在哪一扇面：J1/J2/J3 主张在首页 `.composer`（唯一不贴画面边的玻璃面），J4 主张在 `.workspace-topbar` 的窄窗聊天帧（唯一的覆盖层，内容真的从它下方经过）。这是父卡 060/061 登记的「贴边面 J1 盲区」按预登记处置执行的结果，写进 `web-063-after-reading.txt` 第一节，没有换阈值、没有改取区凑数。
- 顶栏覆盖带来的留白在窄窗必须跟着 `height:64px` 改：留白沿用 76 会在玻璃下缘露一条空带。这条实测缺陷是命中区与几何实测（`verify_063.mjs`）发现的，修在 `workspace.css`/`pages.css` 的 `@media (max-width:760px)` 里，并由 `glass-depth.test.ts` 第 7 条断言钉住「栏高与留白成对」。
- 采集必须带 `Emulation.setScrollbarsHidden`：Chromium 的常驻经典滚动条正好占住判据当背景参照的左右列，会把 J1 读成 4.4 L；这条只关取景，不动材质与阈值，理由写在 `web_capture.mjs` 注释里。
- 仪器为 Web 补的两条取景规则：① `anchor=="top"` 时先按上下缘跳变找面，找不到再退回亮行（旧顺序会在满宽顶栏上退化成 14 逻辑 px 的假带，J4 读出 119.4 的无效值）；② 边缘扫描改「两侧各取 `win` 列中位再比跳变 + 回到窗口邻域取最陡列」，因为 Chromium 对 135° 渐变做逐设备列 ±4 L 的有序抖动，1px 边判据会把它读成面的边界。Android 那端由 Canvas 直接着色没有抖动，所以这两条只补 Web 的取景，四条 Android 旧读数逐字节复现。
- 实际生产改动量：`glass.css` 材质层（共享投影块 + 四组 145° tint + 头注释）约 +45/−18，`workspace.css`（顶栏覆盖化、`.workspace-column` 相对定位、两处留白）约 +20/−6，`pages.css`（两处留白）约 +9/−1，四方链删 `glass-shadow`（原型 `:root` 一行 + `tokens.json` 一条 + 重出的 `tokens.css` 一行）。`git diff --numstat` 对 HEAD **量不到**前两项：这三个 CSS 在本工作线是未跟踪文件（`git status --short` 三条 `??`），登记为测量盲区。

## Verification report

- Verdict: PASS。四条判据在同一份取区实现、同一组阈值下各有成立读数，前端 16 步 verify 与四方守门绿，Android 旧读数未被本次取景改动移动过一位。
- Evidence: `web-063-after-reading.txt`（主张的四条读数、其余各帧不藏红的完整读数、宽窗 J4 稀释的逐行分布与单变量对照、无让位零漂移对照、十条交互与降级实测）；`web-063-red.txt` / `web-063-green.txt`；截图 `web-063-{home,chat,library}-{top,scrolled}.png`（2880×1800）与 `web-063-narrow{,-chat}-{top,scrolled}.png`（1236×2700）；`check_design_tokens.py` 0 failure、`gen_design_tokens_css.py --check` 逐字节一致（`tokens=71`）、`check_glass_depth.py --selftest` `cases=14 failures=0`、`run_all_guards.py --tier core` 收尾轮。
- Changed/deleted assertion rationale: `glass.test.ts` 的 token 消费清单把被取代的 `--glass-shadow` 换成 `--glass-shadow-key`/`--glass-shadow-ambient`/`--glass-tint-falloff` 三枚（它主张的是「材质层必须消费这批 token」，被取代的名字没有保留义务）；`glass-depth.test.ts` 第 6 条「两条既有降级与窄窗侧栏都还在」原文未改，继续为 `@supports not`、`prefers-reduced-transparency` 与窄窗侧栏托底；仪器 `check_glass_depth.py` 的阈值常量、判据条数与退出码语义一个没动，只补取景，并由三条新自测用例（贴边顶栏按找下缘、抖动不移面缘、抖动留在自身幅度内）钉住。
- Gaps: ① 顶栏（以及任何满宽贴边面）的 J1 在本仪器下读不出，属仪器盲区而非材质缺陷——已用不贴边的 composer 面主张，并保留顶栏面的 J2/J3 交叉核对。② 同一规则同一阈值下，1440 宽窗顶栏的 J4 读 4.2~5.9 L：逐行分布证明信号全在面框下缘（y 119..131 行内 p95 14.9~19.6、p100 20.9），而宽窗顶栏有整段宽度下面只是页面留白，整面 p95 被稀释；单变量对照（chrome .30 → 8.8、blur0 → p95 0.0 而 p100 46.5）证明支配项是 tint alpha 与「面宽里有多少真的有内容经过」，不是模糊。这一条按实测如实登记，未据此放宽阈值，也不构成对 061 token 的改动。③ 学习库（宽窗）与首页（618×1350）内容不溢出，`range=0`，两帧逐字节相同，这两处无从主张 J4（与 062 已登记的同源缺口一致）。④ Web 是否出现 banding：本次截图未见需要处理的条带，颗粒仍按卡片停在 Deferred，不凭推定实现。
- Risks: 顶栏覆盖化后，任何新增的满宽覆盖面都会撞上同一条 J1 盲区；若下一片要给顶栏加滚动渐浓（063 明确未叠的第二个变量），必须同时重跑这条 J4 取证，不能沿用本片的 11.3 L。

## Handoff notes

- Completed: Web 材质（两档投影 + 145° falloff tint）、`body` 雾面新值消费、顶栏覆盖化与两断点留白、`--glass-shadow` 四方链删除、`check_glass_depth.py` Web 取景补齐、J1–J4 取证与交互/降级实测、设计系统 README 变更记录。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 若要 Web 与 Android 同帧对比观感，先补「深色模式」与「Web 颗粒」两条 Deferred 的实测判定；顶栏滚动渐浓是下一个未验证变量，另开一片并把 J4 取证重跑一遍。
- Watch-outs: 三个 Web CSS 在本工作线仍未跟踪，`git diff` 与任何按 numstat 的度量都量不到它们；不要让 `tokens.css` 手改；不要为读数把顶栏改成不透明；不要顺手动 `snapshot.css`；`Emulation.setScrollbarsHidden` 与 `--band` 选择都属于取景，写进证据就不能省，否则下一片复现不出同一组数。
- Reproducibility: 证据里点名的 `drift_probe.py`、`mat_probe.mjs`、`nobar_probe.mjs`、`verify_063.mjs` 落在 gitignore 的 `artifacts/tmp/`（全仓 20 余张卡的一次性诊断件同源），只作归因，不承担判决；四条 J1–J4 的复现只需跟踪件 `check_glass_depth.py` 加同目录的 PNG 与取景说明。
