# Filename: T-2026-10-23-glass-opacity-065.md
# Codex Harness Task: 玻璃五面不透明度上调与 Web 顶栏 J4 退让

id: T-2026-10-23-065
title: 玻璃五面不透明度上调与 Web 顶栏 J4 退让
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-glass-depth-requirement-060.md
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: android
stack: android
estimated_production_lines: 30
shared_code_touched: true
depends_on: [T-2026-10-22-web-glass-depth-063.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 用户 2026-10-23 反馈「玻璃的不透明度太低了，需要优化一下」。061/063 把通透面定在 chrome .54 / card .72 / reader .86 / field .50，Android 端没有真实模糊（`PaperRuleBackdrop` 明确不伪造 backdrop blur），54% 白压在列表文字上就是文字穿过胶囊可读；Web 端有 28px 模糊，但同一组 alpha 让玻璃面与雾面场景的差值被压平，读起来像「同色贴片」而不是「一块浮着的面」。
- Users/scenarios: 真机首页底部胶囊与顶栏、对话页阅读面与 composer、学习库/设置卡面；Web 宽窗（常驻侧栏 + 覆盖顶栏）与 618×1350 窄窗；`prefers-reduced-transparency` 与不支持 `backdrop-filter` 两条降级路径。
- Expected outcome: 五面读得出「有一块面」——分离与投影两条判据不降；Android 的「内容经过玻璃」仍成立；Web 顶栏那条像素信号按用户裁决让位给不透明度，并且这笔代价是**先量出来再花**的，不是事后解释。
- Core flow and reversibility: 原型 `:root` 四枚 alpha → `tokens.json` 同四枚 → `values/glass.xml` 四面 ARGB 与四个 `glass_*_lo` 派生副本 → 生成器重出 `tokens.css` → 四方守门。撤销是把四枚 alpha 改回旧值后重跑同一条链，两端副本由脚本与两条下限断言自动跟上，没有手写消费者。
- Inputs/outputs: 输入是 061 定下的 token 链与 063 的取景实现；输出是四枚 token 的新值、两端各一条 alpha 下限断言、真机与浏览器逐帧读数、设计系统 README 变更记录。不新增 token、不新增依赖或资源、不改判据阈值与取区规则。
- State and accessibility/exception handling: 业务状态、命中区域、焦点顺序、滚动结构一律不变；两条 Web 降级块与 Android 的 `GlassSurfaceLayout` 投影绘制路径不变；alpha 上调只会提高正文—表面对比，不会降低。
- Constraints and trade-offs: 设计数值只能来自 token 链，两端不得出现第二份字面量；`glass_*_lo` 由「该面 alpha 字节 × falloff」派生，改 alpha 必须同步派生值，否则 `GlassMaterialTokensTest` 判红。**核心矛盾**：顶栏是 Web 唯一的覆盖层，J4（内容经过玻璃）与顶栏 alpha 是同一根轴，alpha 越高信号越弱，阈值 8.0 L 之下的天花板是 chrome ≈ .62——要更实就必须放弃 Web 的这条像素主张，或给两端分档阈值。
- AI recommendation and rationale: 先把曲线量出来（运行时单变量覆盖，不改仓库文件）再让拍板发生，避免「改完才发现判据红」。推荐跟到 .74 并放弃 Web 顶栏 J4：让位结构本身仍由 CSS 断言兜住，而 Android 端 J4 有 2.6 倍余量、主张保留，判据的跨端一致性只在「这一面这一条」上退让，比给 Web 单独放宽阈值更可审计。
- User-confirmation decisions: 「玻璃的不透明度太低了，需要优化一下」（2026-10-23）；在实测曲线上选择「跟到 .74，放弃 Web 的像素判据」，即顶栏与其余四面一起变实、Web 顶栏不再主张 J4、Android 的 J4 主张保留。
- AI-autonomous decisions: ① 具体目标值 chrome .74 / card .86 / reader .93 / field .74，按「内容面比 chrome 更实、阅读面最高」的既有阶梯上移；② `edge`/`line`/`sheen`/`tint-falloff` 不动——用户说的是表面太透，不是高光与边线不够；③ 两端断言写成**下限**（.70/.84/.92/.70）而不是当前值，抄值会造出第二份事实且对「更透明」方向永不响；④ `glass-reader` 的 note 从「固定 86%」改写为记录两次拍板的时间线，不静默覆盖 2026-10-21 的裁决；⑤ 真机取证沿用 061/062 的同一 box（滚到底、末条停在胶囊上方），使五条读数可直接对照。
- Acceptance evidence: 同一取区实现与同一组阈值下——Web `.composer` 面 J1/J2/J3 PASS、真机底部胶囊 J1/J2/J3/J4 PASS（box 与 061/062 逐位相同）；两端 alpha 下限断言 RED→GREEN；`check_design_tokens.py` 与 `gen_design_tokens_css.py --check` 退出码 0；`pnpm --dir frontend verify` 16/16；Android 全量 JVM 与 core 守门无红。证据落 `artifacts/evidence/glass-opacity-2026-10-23/`。
- Facts: 五面 alpha 的权威落点是原型 `:root` 第 69–77 行与 `design-system/tokens.json` 的 `glass-*` 条目；Android 副本在 `android/app/src/main/res/values/glass.xml`，派生规则在 `GlassMaterialTokensTest` 第 58–61 与 111 行（`Math.round(alpha*255)`、`round(该面 alpha 字节 × falloff)`）；Web 消费面是生成的 `frontend/src/styles/tokens.css` 与 `glass.css` 的四面 `color-mix`。
- Assumptions: 用户对「更实」的期待是整面上移，而不是给某一面单独开值——阶梯关系（chrome < card < reader）保持不变。
- Unknowns: 顶栏到 .74 后，Web 上「内容经过玻璃」在观感上是否还值得保留这套结构（若用户下一轮说「看不出滚动」，需要重新讨论顶栏的通透度或改做滚动渐浓）。
- Risks: alpha 上移会让 145° tint 的明暗差被压扁，面读起来更接近平板白卡——用 J2（投影）与 J1（分离）两条读数把「浮起来」这件事继续钉住，本片两者都只升不降；Web 顶栏 J4 从 11.3 掉到 4.9，若后续有人拿这条读数当回归基线会误判，因此退让写进卡片、README 与证据三处。
- Deferred: 深色模式、Web 颗粒、Android 首页顶栏覆盖化（见 Gaps ③）。
- Non-goals: 不改判据阈值与取区规则、不给两端分档阈值、不改滚动结构与业务组件、不动 `snapshot.css`、不新增 token、不自动提交或推送。
- Skipped non-critical questions and reason: 未追问「edge/sheen 要不要一起上移」——用户指向的是表面通透度，高光与边线不是同一件事，问了也只是多一个旋钮。

## Goal

五面玻璃在不改判据、不改结构的前提下整体上移一档，Android 四条判据仍全绿，Web 顶栏那条与不透明度互斥的像素主张按拍板让位并被三处记录。

## Scope

### In scope
- 四枚 alpha 沿固定顺序落链：原型 `:root` → `tokens.json` → `values/glass.xml`（含四个派生 `_lo`）→ 生成 `tokens.css`。
- 两端各一条 alpha 下限断言（JVM 读 `tokens.json`、vitest 读生成的 `tokens.css`）。
- 运行时单变量曲线实测（chrome alpha → Web 顶栏 J4），作为拍板输入。
- 真机与浏览器逐帧取证、README 变更记录。

### Out of scope
- 判据阈值与取区规则（一个没动）、两端分档阈值、`edge`/`line`/`sheen`/`falloff` 数值、滚动结构、业务组件、Android 首页顶栏覆盖化。

### Behavior impact — applicable surfaces only
- 视觉：所有玻璃面更实，Android 底部胶囊与顶栏的文字穿透减弱；Web composer/卡片投影更强。
- 判据：Web 顶栏 J4 不再主张（4.9 L < 8.0 L，有意为之）；其余主张面读数只升不降。
- 无功能/数据/状态影响。

## Documentation impact record

- `Docs/04-ux-design/design-system/README.md`：变更记录新增 2026-10-23 条目（四枚新值、曲线实测、退让与其记录位置、下限断言）。
- 原型 `:root` 的区块注释：写明五面 alpha 于 2026-10-23 上移，以及顶栏通透不再靠压 alpha 换。
- `tokens.json` 的 `glass-reader` note：把「固定 86%」改写为 2026-10-21 与 2026-10-23 两次拍板的时间线。
- `values/glass.xml` 的 tint 注释：把「86% reader / 54% chrome」的旧百分比改为 93% / 74%。
- `GlassMaterialTokensTest` 的断言消息：去掉写死的旧百分比。
- 063 卡片与其证据不改写（历史记录）；退让由本片卡片、README、`065-after-reading.txt` 三处承担当前事实。

## Acceptance criteria

- [x] 四枚 alpha 在四方链上一致，`check_design_tokens.py` 与 `gen_design_tokens_css.py --check` 退出码 0。
- [x] Android `glass.xml` 四面副本与四个 `_lo` 派生值与 `tokens.json` 一致（JVM 重算判据绿）。
- [x] 两端 alpha 下限断言 RED→GREEN，断言写下限不写当前值。
- [x] 真机底部胶囊 J1/J2/J3/J4 在同一 box 下全绿；Web composer 面 J1/J2/J3 绿；Web 顶栏 J4 的退让有实测曲线支撑并写进三处。
- [x] `pnpm --dir frontend verify` 16/16、全量 vitest 绿、Android 全量 JVM 无失败、core 守门无红。
- [x] 未新增 token、依赖或资源；未自动提交或推送。

## TDD execution record

### RED — failing test first
- Test/scenario: ① `GlassMaterialTokensTest.theGlassSurfacesStayAboveTheAgreedOpacityFloors`（新增，读 `tokens.json`）；② `frontend/test/styles/glass.test.ts`「五面 alpha 停在拍板下限之上」（新增，读生成的 `tokens.css`）。
- Command: `pnpm --dir frontend exec vitest run test/styles/glass.test.ts test/styles/glass-depth.test.ts`；`./gradlew :app:testDebugUnitTest --tests "*GlassMaterialTokensTest*"`（在 `android/` 下执行）
- Exit code: 1
- Transcript: `artifacts/evidence/glass-opacity-2026-10-23/065-web-red.txt` → `Tests 1 failed | 4 passed (5)`；`065-android-red.txt` → `BUILD FAILED`，用例 `theGlassSurfacesStayAboveTheAgreedOpacityFloors`。
- Observed failure: `--glass-chrome 低于拍板下限: expected 0.54 to be greater than or equal to 0.7`；`java.lang.AssertionError: glass-chrome must stay at or above alpha 0.7, got 0.54`。
- Failure reason validated: 两条命令里其余用例（vitest 4 条、JVM 其余 4 条）都是绿的，测试不是恒红，它精确描述「值还没上移」。像素 RED 另有一处对照价值：曲线实测（第一节）在旧 token 下跑 chrome .70/.74/.78 覆盖，J4 依次 5.9/4.9/3.9 L，证明退让是可预期的，不是实现失误。

### GREEN — minimum implementation
- Implementation: ① 原型 `:root` 四枚 alpha 改为 .74/.86/.93/.74，区块注释记录这次拍板与退让；② `tokens.json` 同步四枚值，`glass-reader` note 改写为两次拍板的时间线；③ `values/glass.xml` 四面 ARGB 改 `#BD/#DB/#ED/#BD`，四个 `_lo` 按 `round(该面 alpha 字节 × .58)` 重算为 `#6E/#7F/#89/#6E`，tint 注释的百分比跟着改；④ `gen_design_tokens_css.py` 重出 `tokens.css`；⑤ 两端下限断言（见 RED）；⑥ 断言消息里写死的旧百分比改为不依赖具体值的表述。生产代码零改动：`glass.css`、Android drawable 与 `GlassSurfaceLayout` 都只引用 token，没有第二处需要跟。
- Command: `pnpm --dir frontend exec vitest run test/styles/glass.test.ts test/styles/glass-depth.test.ts`；`./gradlew :app:testDebugUnitTest --tests "*GlassMaterialTokensTest*"`（在 `android/` 下执行）
- Exit code: 0
- Passing evidence: vitest → `Tests 12 passed (12)`（`065-web-green.txt`）；JVM → `BUILD SUCCESSFUL`、`ANDROID_EXIT=0`（`065-android-green.txt`）。像素判据：`check_glass_depth.py --shot 065-web-home-top.png --band bottom` → `VERDICT: PASS`（J1 19.8 / J2 46.4 / J3 43.7 + 彩度 12.0）；真机 `--shot 065-android-home-bottom.png --band bottom --drift-before 065-android-home-top.png --drift-after 065-android-home-bottom.png` → `VERDICT: PASS`（J1 22.8 / J2 12.4 / J3 26.2 + 彩度 13.0 / J4 21.2，box `78,2385..1121,2586` 与 061/062 逐位相同）。
- Regression evidence: `check_design_tokens.py` → `OK=10 FAIL=0 SKIPPED=0`（`measured_prototype=71 measured_tokens_json=71 measured_resources=24 measured_css=71`）；`gen_design_tokens_css.py --check` → 逐字节一致（`bytes=2306 tokens=71`）；`pnpm --dir frontend verify` → `steps=16 ok=16 bad=0 fault=0`；全量 vitest → `Test Files 20 passed (20)`；`./gradlew :app:assembleDebug :app:testDebugUnitTest` → `BUILD SUCCESSFUL`、`tests=898 failures=0`；`check_glass_depth.py --selftest` → `cases=14 failures=0`；真机 `adb -s AWSS013807000356 install -r`（未跑 instrumentation，避免清真机数据）。逐帧读数、曲线与对照见 `065-after-reading.txt`。

### REFACTOR — optional
- Cleanup: 断言消息与注释里三处写死的旧百分比（`GlassMaterialTokensTest` 的「86% reader / 54% chrome」、`glass.xml` 的同源注释、`tokens.json` 的「固定 86%」）改为不随值漂移的表述或带日期的拍板时间线——留着它们，下一次调值就会留下会说谎的注释。
- Regression evidence: 同一批 GREEN 命令复跑全绿；`check_source_of_truth` 与 core 守门收尾轮无红。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 曲线先于拍板：alpha→J4 的映射用运行时覆盖实测（`artifacts/tmp/opacity_sweep.mjs`、`opacity_sweep2.mjs`），仓库文件未改、判据未改；用户是在「.62 是阈值下的天花板」这个读数上做的决定。
- 「上实下透」被实测否掉：把 falloff 从 .58 降到 .45（tint 暗端 alpha 回到 .31）只让 J4 从 5.9 换回 6.1 L，支配项是整面 alpha，不是对角暗端。这条否掉的理由写进证据，避免下一片再试一遍。
- 下限断言分两端各一条，是因为同一枚事实在两端有两个消费者（`tokens.json` 与生成的 `tokens.css`）；断言只读事实源、不复制值，所以不构成第二份权威。
- Android 的 `_lo` 是 `round(该面 alpha 字节 × falloff)` 而不是 `round(alpha × falloff × 255)`——先量化到字节再乘，与 `GlassMaterialTokensTest` 第 111 行的实现一致；两片都按这条规则算，因此 J 判据看到的暗端与 JVM 重算的暗端是同一个数。
- 真机取证要用与 061/062 相同的页面状态（滚到底、末条停在胶囊上方）：静止在页面顶时同一 `--band bottom` 会框到「列表卡 + 胶囊」的合并面（实测 box `52,2101..1147,2586`、J2 读成 -8.3），那不是材质事实而是取景事实。
- 三个 Web CSS 与本工作线的 token 文件在本工作线仍未跟踪，`git diff` 与按 numstat 的度量量不到它们。

## Verification report

- Verdict: PASS。主张面四条判据在同一实现同一阈值下成立，Web 顶栏 J4 按用户裁决不再主张且退让有三处记录；两端下限断言与四方链全绿。
- Evidence: `065-after-reading.txt`（曲线、主张读数、不藏红的退让读数、before/after 对照、断言 RED 原文、取景说明）；`065-web-red.txt` / `065-web-green.txt` / `065-android-red.txt` / `065-android-green.txt` / `065-android-build-test.txt`；截图 `065-web-home-top.png`（2880×1800）、`065-web-narrow-chat-{top,scrolled}.png`（1236×2700）、`065-android-home-{top,bottom}.png`（1200×2652）。
- Changed/deleted assertion rationale: 没有删除或放宽任何既有断言；新增两条下限断言；`GlassMaterialTokensTest` 的 tint 断言只改消息文本（旧消息写死 86%/54%），判定条件 `reader > chrome` 不变。判据脚本 `check_glass_depth.py` 一个字节未改。
- Gaps: ① Web 顶栏 J4 = 4.9 L，低于共享阈值 8.0——这是拍板换来的，不是缺陷；让位结构仍由 `glass-depth.test.ts` 的覆盖层与留白成对断言兜住，Android 同条判据 21.2 L 继续主张。② Web 顶栏（及任何满宽贴边面）J1 的仪器盲区沿用 063 登记，本片未触碰。③ 顺带发现：Android 首页顶栏不是覆盖层，滚到底那一帧整条随内容划走、正文副标题顶到状态栏下缘（与 062 的 `062-after-home-scrolled.png` 同形，非本片引入），而 Web 顶栏是覆盖层——两端结构不对称，另开一片。④ 学习库/首页在 618 宽度不溢出（`range=0`）的既有缺口未变。
- Risks: 若下一轮用户说「看不出内容在滚」，唯一的旋钮就是顶栏 alpha 或滚动渐浓，届时必须重跑这条 J4 曲线，不能沿用本片的 4.9 L；`tokens.css` 是生成物，任何手改都会被逐字节校验拦下。

## Handoff notes

- Completed: 四枚 alpha 沿四方链落地、Android 副本与派生 `_lo` 重算、两端下限断言、曲线实测、真机与浏览器取证、README 变更记录、本片卡片。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 若用户仍觉得透，下一档是 chrome .80+，但那会让 Web 顶栏彻底成为不透明条，应先谈「顶栏要不要还是玻璃」；② Android 首页顶栏覆盖化（与 Web 对齐）另开一片；③ 深色模式与 Web 颗粒仍在 Deferred。
- Watch-outs: 不要为了找回 Web 的 J4 读数去压顶栏 alpha 或改阈值——曲线已经量过，那是同一根轴的另一端；不要把当前值抄进断言（下限才对）；`glass_*_lo` 必须按「字节 × falloff」重算，改 alpha 忘了改副本会被 JVM 判红；真机取证必须用滚到底那一帧，否则 `--band bottom` 框到合并面会读出假 J2 红。
- Reproducibility: 曲线与截图对照驱动 `artifacts/tmp/opacity_sweep.mjs`、`opacity_sweep2.mjs` 落在 gitignore 的 `artifacts/tmp/`，只作归因；四条判据的复现只需跟踪件 `check_glass_depth.py`、`web_capture.mjs` 与本目录的 PNG。
