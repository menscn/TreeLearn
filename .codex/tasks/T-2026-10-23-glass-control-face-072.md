# Filename: T-2026-10-23-glass-control-face-072.md
# Codex Harness Task: 第六面 --glass-control（控制面）：标签栏与悬停态不再让两层文字互相渗透

id: T-2026-10-23-072
title: 第六面 --glass-control（控制面）
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
depends_on: [T-2026-10-23-glass-opacity-065.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Problem/background: 用户 2026-10-23 反馈「按钮的不透明度太低了，导致两个上下层的文字会看起来重叠，Android 和 web 都修改」。065 把 chrome 提到 .74 之后，悬浮标签栏的**标签文字**压在**下方列表卡片的标题**之上：两层字各 74% 白底叠在一起，读起来是一条糊住的带。这不是「玻璃不够透」的反面故障，而是这一类面根本不该参与通透度权衡。
- Users/scenarios: 真机首页/学习库底部悬浮胶囊（标签压在滚过的卡片标题上）；Web 的推荐标签悬停态、设置页人设入口悬停态、人设反馈浮层。
- Expected outcome: 这类「面上的文字压在别处的文字之上」的控制面读得出只是一层字；分离与投影两条判据不降；其余四面（chrome/card/reader/field）一个不动。
- Core flow and reversibility: 原型 `:root` 加一枚 `glass-control` → `tokens.json` → `values/glass.xml`（**只有 ARGB 一面，故意不派生 `_lo`**）→ 生成器重出 `tokens.css` → 新增 `bg_glass_control_bar.xml` 作为消费者。撤销是删掉这枚 token 与其消费者，链上守门脚本会自动拦下四方集合差。
- Inputs/outputs: 输入是 065 定下的五面阶梯与 061 的 tint 派生规则；输出是一枚新 token、一个 Android drawable、四处消费者改判、三端各一条断言。不新增依赖，不新增资源种类（drawable 与 token 各一）。
- State and accessibility/exception handling: 命中区域、焦点顺序、滚动结构一律不变；只换面底色这一层绘制。平涂不影响可读性方向（更实）。
- Constraints and trade-offs: 设计数值只能来自 token 链。**核心矛盾**：J4「内容经过玻璃」奖励通透，而控制面的失败模式是两层字互相渗透——同一枚面上这两条判据方向相反。取舍是新增一面专给控制面，而不是给标签栏单独放宽 J4 或把 chrome 再提一档（那会连带影响所有随页面滚动的栏）。
- AI recommendation and rationale: 与阅读面同档（.93）并**平涂**。平涂的理由是可量的：`bg_glass_bar` 的 145° tint 把下半段压暗到 `alpha × falloff`（.74 × .54），而下半段正是与下方卡片标题重叠最多的那半条带；一块 62dp 高的胶囊载不动这个梯度还保持可读。受光层与 1px 边线保留，光源与全场其余面仍是同一个。
- User-confirmation decisions: 「按钮的不透明度太低了，导致两个上下层的文字会看起来重叠，Android 和 web 都修改」（2026-10-23）。
- AI-autonomous decisions: ① 目标 alpha 取 .93，与 reader 同档（阶梯里 reader 已经是「读得清」的那一档）；② 不给 `glass_control_lo`——平涂是这枚面的定义的一部分，并在两处断言里注册为例外，而不是留一个没人消费的派生值；③ Web 侧只换三处「文字压在别处之上」的悬停/浮层，不去动静止态卡片面；④ 顺手补一条「悬停不许比静止更透明」的断言，把这类倒挂钉死；⑤ Android 侧新建 `bg_glass_control_bar.xml` 而不是给 `bg_glass_bar.xml` 加开关——两个面是两件事，合在一个 drawable 里就要靠布尔参数表达材质。
- Acceptance evidence: 真机胶囊 J1/J2/J3 三条读数不降（33.1 / 12.9 / 26.2+13.0，见 `072-android-home-{top,bottom}.png`）；滚动恢复后的同位像素复测证明面内不随滚动变化（直边段 0.49% 变化 / |ΔL| 中位 1.00）；Web 运行时回读 `--glass-control = rgba(255,255,255,.93)` 且 CSSOM 里三处消费者确实引它；两端 alpha 下限断言与平涂例外断言在位。
- Facts: 五面 alpha 的权威落点是原型 `:root` 与 `design-system/tokens.json`；Android 副本 `values/glass.xml`，派生规则与下限断言在 `GlassMaterialTokensTest`；Web 消费面是生成的 `tokens.css` + `glass.css`；胶囊布局是 `layout/screen_tab_bar.xml` 与 `layout-sw600dp/` 镜像。
- Assumptions: 用户要的是「两层字别糊」，不是「整场玻璃再变实」——所以只动控制面，chrome 保持 .74。
- Unknowns: 深色模式如果将来做，控制面该是「更实的深面」还是「同样 .93 的反色」，本片没有消费者可以验证。
- Risks: 六面里出现两枚同值（reader .93 与 control .93），后续有人以为是重复而合并——两者的**绘制方式**不同（reader 带 tint，control 平涂），已由两处例外断言分开守着。
- Deferred: 两枚一级顶栏的覆盖化（在 075 落地，并把 control 面一起带上去）；`check_glass_depth.py` 是否要为「控制面」显式豁免 J4（本片只撤主张、不改脚本）。
- Non-goals: 不改 chrome/card/reader/field 的值、不改 J1–J4 阈值与取区规则、不改滚动结构、不新增依赖、不自动提交或推送。
- Skipped non-critical questions and reason: 未追问「是否连 field（输入框）也换 control」——输入框压的是自己的占位文字，不是别处的文字，不属于这一类失败模式。

## Goal

给「面上的文字压在别处文字之上」这一类面一枚不参与通透度权衡的第六面（.93 平涂），并在 Android 悬浮标签栏与 Web 三处悬停/浮层上落地，其余五面一个值不动。

## Scope

### In scope
- 链上按固定顺序新增 `glass-control`：原型 `:root` → `tokens.json` → `values/glass.xml` → 生成 `tokens.css`。
- 新增 `res/drawable/bg_glass_control_bar.xml`（受光层 + 1px 边线，**平涂无 tint**），`layout/screen_tab_bar.xml` 与 sw600dp 镜像改用它。
- Web 侧 `.suggestion:hover`、`.settings-persona-entry:hover`、`.persona-feedback` 从 chrome 改到 control。
- 三处断言：两端 alpha 下限把 control 记进集合（0.92）；平涂例外在 `GlassSurfaceLayoutTest` 与 `glass-depth.test.ts` 各注册一条；新增「悬停不许比静止更透明」。
- J4 主张的处置：Android 胶囊不再主张 J4，与 065 给 Web 顶栏的处置对齐。

### Out of scope
- 五面既有值、J1–J4 阈值与取区规则、`bg_glass_bar` 的其余消费者、滚动结构、业务组件、深色模式。

### Behavior impact — applicable surfaces only
- 视觉：底部胶囊与三处悬停/浮层更实，两层文字不再互相渗透；其余面逐字节不变。
- 判据：Android 胶囊 J4 从主张改为不主张（2.3 L 是 .93 平涂的算术结果）；J1/J2/J3 三条只升不降。
- 无功能/数据/状态/命中影响：改的是 drawable 引用与 CSS 背景值，`tab_home` / `tab_library` 的 id、命中区与 `MainActivity` 的接线都不变（`IconAssetsTest` 的 `assertIconAction` 读数不变即是这条的回归锁）。

## Documentation impact record

- Changed runtime facts: 玻璃从五面变六面；控制面平涂；Android 悬浮标签栏的材质归属；Android 胶囊不再主张 J4。
- Authoritative code/config/migration evidence: `Docs/04-ux-design/design-system/tokens.json` 的 `glass-control` 条目与 `values/glass.xml` 的 `glass_control`（注意后者**没有** `glass_control_lo`）；`res/drawable/bg_glass_control_bar.xml` 的头注释（写的是「为什么是第六面而不是再抬一档 chrome」）。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`（token 表 + 变更记录）、`android/README.md`（应用外壳表）、`frontend/src/styles/glass.css` 注释。
- Documentation files updated: 设计系统 README 变更记录新增 072 条并随 075 复测把「J4 暂时撤回」改为定稿；`glass.css` 头部注释标注片号。
- Producer/consumer/sibling/compatibility paths checked: `bg_glass_bar` 仍有两个活消费者（聊天、抽屉导航），未被孤立；`glass-chrome` 消费者计数由 `check_design_tokens.py` 打印。
- Superseded phrases/examples reverse-searched: 「五面」在断言名与文档里的出现逐处核对——下限断言改口为「六面」，065 那条变更记录保留原文不改写（它记录的是当时的裁决）。
- Historical/PRD handling: 065 卡片与其证据原样保留；本片只在自己的卡片与变更记录里更新 J4 处置。

## Acceptance criteria

- [x] 控制面在链上存在且值为 rgba(255,255,255,.93)，四方集合相等、`gen_design_tokens_css.py --check` 逐字节一致。
- [x] Android 悬浮标签栏取平涂控制面：真机胶囊 J1/J2/J3 不降；面内同位像素差不随页面滚动变化（直边段 0.49% / 中位 1.00 L）。
- [x] Web 三处悬停/浮层取 control，且运行时回读证实 CSSOM 真在引用它；静止态与悬停态不存在材质倒挂。
- [x] 其余五面与两枚随页面滚动的栏一个值没动（`check_design_tokens.py` 读数 + `bg_glass_bar` 仍有消费者）。
- [x] 平涂例外被断言钉住：改回 tint 或补一个 `glass_control_lo` 都会判红。

## TDD execution record

### RED — failing test first
- Test/scenario: `GlassMaterialTokensTest.theGlassSurfacesStayAboveTheAgreedOpacityFloors`（control 记入下限集合）＋ `GlassSurfaceLayoutTest` 平涂例外 ＋ `glass.test.ts`「六面 alpha 停在拍板下限之上」「悬停不许比静止更透明」＋ `glass-depth.test.ts`「控制面平涂」。
- Command: N/A（无活体 RED，见下方 exception 栏；GREEN 用的命令如实记录，两栏不假称同一条命令跑过两次）
- Exit code: N/A
- Observed failure: **无活体 RED 转录**。本片是用户当场报修的观感缺陷，断言与实现同批写下；事后补录一份「把 token 删掉再跑」的转录只会得到「变量不存在」这种与缺陷无关的红。
- Failure reason validated: 见下方 TDD exception 栏的等效证据——等效证据量的对象是缺陷本身（两帧同位像素差），不是断言字符串。

### GREEN — minimum implementation
- Implementation: 链上新增 `glass-control` → `values/glass.xml` 的 `glass_control` → 新 drawable `bg_glass_control_bar.xml`（平涂）→ `screen_tab_bar.xml` 两份改引用 → Web 三处改 `var(--glass-control)` → 四条断言。
- Command: `pnpm --dir frontend exec vitest run test/styles`
- Exit code: 0
- Transcript: `artifacts/evidence/glass-control-face-2026-10-23/072-web-styles-green.txt` → `Test Files 4 passed (4)` / `Tests 20 passed (20)`。（这一份转录与 073 卡共用同一次运行：两条断言在同一天连续两片里，最后一次跑即是两者当前的绿。）
- Passing evidence: 4 files / 20 tests passed（`pnpm --dir frontend verify` steps=16 ok=16 bad=0 fault=0）。Android 侧随全量 JVM 通过（当时 946 例 0 失败）。
- Regression evidence: 真机 `adb install -r` Success + 胶囊三条读数不降；同位像素复测见 `072-reading.txt` 第 2 节。

### REFACTOR — optional
- Cleanup: 无。
- Regression evidence: 无。

### LOOP — optional
- 不适用。

### TDD or sizing exception
- Exception type: approved-exception（断言先行，无活体 RED）
- Reason: 观感缺陷类改动，「先红」需要人工把已拍板的 token 再删一次，产出的红与用户报修的那个失败模式无关。
- User approval: 用户 2026-10-23 的指令范围内是「直接改」，Harness 侧对本片未要求 verifier；如实登记，不冒充 RED。
- Alternative verification: ① 真机同一条胶囊、同一个 box 的两帧同位像素差（chrome 面 33.05% 变化 → control 面 3.65%，面内直边段 0.49%），这就是「重叠文字的渗透」这一件事的可复现量；② 滚动恢复后的 J4 复测（2.3 L）把「不再主张 J4」写成有数的处置而不是回避；③ 运行时回读证明 control 是活值不是死数值。

## Implementation notes

- 平涂的证据在 drawable 本身：`bg_glass_control_bar.xml` 只有一层 `<solid>` + `<stroke>`，最上面保留受光 sheen；与 `bg_glass_bar.xml` 的差别就是那一段 `<gradient>`。头注释记录了为什么这枚面不走对角 tint。
- `glass_control` 故意没有 `_lo`。`GlassSurfaceLayoutTest` 的平涂例外不是「漏了一条断言」，是把「这枚面不该有暗端」这件事写成可回归的形式。
- J4 的处置与 065 同一条理由：这条判据奖励通透，控制面买的是可读。两端顶栏/标签栏都归到「不主张 J4」，让位结构改由断言兜住。
- 本片的 Android 胶囊取区框在复测时回到与 dumpsys `tab_bar_pill` 逐位吻合的 `78,2385..1121,2586`；落地当天读到的 `61,2382..1138,2586` 是同一枚面的另一版框（背景不同、检测 heuristic 不同）。两处都留在证据里，不挑好看的读数。

## Verification report

- Verdict: PASS
- Evidence: 单元/契约——Android JVM 全量 0 失败；`pnpm --dir frontend verify` 16/16；`test/styles` 20 例通过；`check_design_tokens.py` OK=10 FAIL=0（measured_tokens=72 measured_resources=24）；`gen_design_tokens_css.py --check` 逐字节一致（bytes=2348）。真机——AWSS013807000356 `adb install -r` Success，胶囊三条判据读数不降。运行时——Web computed style / CSSOM 回读（`072-web-live.txt`）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 断言只增不删；`everyTintedGlassFaceFalloffsOnTheProposalDiagonal` 改名并把 control 注册为 flat 例外，覆盖面从「所有面都有暗端」收窄为「所有带 tint 的面都有暗端」，收窄的部分由「控制面平涂」两条正向断言接住。
- Gaps: 控制面只有手机与 Web 两种消费者；Pad 的胶囊复用同一 drawable，没有单独宽窗截图（本机只有一台手机）。深色模式无消费者，未验证。
- Risks: reader 与 control 同值容易被后来者合并；已由两处平涂例外断言分开，改动会判红。

## Handoff notes

- Completed: token 链第六面、Android drawable 与两份标签栏、Web 三处消费者、四条断言、真机与运行时取证、变更记录。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 075 把两枚一级顶栏也搬到常驻层并继承这枚控制面（那片暴露出「栏一常驻，推荐语就穿栏」是同一个失败模式的第二次发生）。
- Watch-outs: 别把 control 当成「更透的替代品」到处用——它的适用判据是「面上的文字压在别处的文字之上」，随页面滚动的栏用了就白白丢掉通透。
