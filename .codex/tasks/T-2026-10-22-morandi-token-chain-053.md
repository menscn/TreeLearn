# Filename: T-2026-10-22-morandi-token-chain-053.md
# Codex Harness Task: 莫兰迪 token 链与 Android 场景柔光

id: T-2026-10-22-053
title: 莫兰迪 token 链与 Android 场景柔光
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-scheme-a-morandi-requirement-052.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 140
shared_code_touched: true
depends_on: [T-2026-10-22-scheme-a-morandi-requirement-052.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 052 已确认方案 A 结构 1:1 + 莫兰迪增量 + 中文衬线 + 不留死按钮 + 真机状态栏；本片只扩 token 链与 Android 场景柔光。
- Problem/background: 047–051 的玻璃链只有绿/沙/白三处柔光，没有莫兰迪的玫瑰与丁香；推荐卡图标底、分支标签等非文字表面也无可用的低饱和 tint token。
- Users/scenarios: 真机首页、对话、分支导航与学习库的静态柔焦场景；所有屏共享一层背景。
- Expected outcome: 原型 `:root`、`tokens.json`、`colors.xml`（4 个不透明 tint）、`glass.xml`（2 个 rgba glow）与生成的 `tokens.css` 同步新增莫兰迪值；`PaperRuleBackdrop` 在绿/沙/白之外再画玫瑰与丁香两处柔光。
- Core flow and reversibility: 原型 `:root` → `tokens.json` → Android/Web 产物；回退为删除 6 个 token 与两处柔光绘制，不动既有 token。
- Inputs/outputs: 输入是方案 A 的莫兰迪低饱和方向；输出是 6 个 token、2 个 Android 光斑绘制与测试断言。
- State and exception handling: 纯静态绘制，无运行时状态；颜色缺失由资源系统在编译期失败，不做运行时兜底。
- Constraints and trade-offs: 不新增依赖；rgba 只能进 `glass.xml`，不透明 hex 只能进 `colors.xml`；不手改生成的 `tokens.css`；莫兰迪只用于柔光与非文字表面。
- AI recommendation and rationale: 用 2 个 rgba glow + 4 个不透明 tint，既满足 `check_design_tokens.py` 的 1:1 映射，又不把低饱和色带进正文文字。
- User-confirmation decisions: 见父卡 052（A + 莫兰迪毛玻璃）。
- AI-autonomous decisions: ① glow-rose/glow-lilac 沿用现有 glow token 的 alpha 量级；② tint 采用浅底图标圈用途，不用于文字；③ 柔光位置取右上与左下，与既有三处错开。
- Acceptance evidence: `GlassMaterialTokensTest` 与 `GlassSurfaceLayoutTest` RED→GREEN；token 三方守门；`gen_design_tokens_css.py --check`。
- Facts: 原型 `:root` 第 22–98 行是 token 权威；`glass.xml` 现有 10 个 rgba；`colors.xml` 现有 20 个资源（16 基础 + 4 mist）；`GlassMaterialTokensTest` 断言 10 个 glass/glow 与 4 个 mist。
- Assumptions: 现有柔光坐标按宽高比例计算，新增两处可直接复用同一绘制路径。
- Unknowns: 莫兰迪色在真机广色域屏上的色偏；两处新光斑与既有柔光的叠加是否过浓。
- Risks: 忘记同步 `tokens.css` 会让守门判红；把 tint 写进 `glass.xml` 会破坏 rgba/hex 分工；漏改测试计数会让守门假绿。
- Deferred: 推荐卡图标底与分支标签的实际消费在 055/056；Web 消费在 058。
- Non-goals: 不改结构、字体、业务、Web 结构。
- Skipped non-critical questions and reason: 未追问莫兰迪精确色号；由方案 A 的低饱和度与真机截图复核决定。

## Goal

让莫兰迪柔光与 tint 成为 token 权威链上的一等条目，并让 Android 背景场景实际消费两处新柔光。

## Scope

### In scope
- `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html`：`:root` 新增 `--glow-rose`、`--glow-lilac`、`--tint-rose`、`--tint-lilac`、`--tint-sand`、`--tint-sage`。
- `Docs/04-ux-design/design-system/tokens.json`：同步 6 条。
- `android/app/src/main/res/values/colors.xml`：4 个 `tl_tint_*`。
- `android/app/src/main/res/values/glass.xml`：`glow_rose`、`glow_lilac`。
- `frontend/src/styles/tokens.css`：由生成器重算。
- `android/app/src/main/java/com/treelearn/app/screen/PaperRuleBackdrop.java`：两处柔光。
- `android/app/src/test/java/com/treelearn/app/screen/GlassMaterialTokensTest.java`、`GlassSurfaceLayoutTest.java`。

### Out of scope
- 各屏消费 tint 的结构改造（055/056）、Web 消费（058）、字体（054）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只新增颜色与两处绘制；既有三处柔光、横格、玻璃 drawable 与所有布局不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 唯一生产者是原型 `:root`；消费者是 `tokens.json`、Android 资源与 Web `tokens.css`；场景唯一绘制点是 `PaperRuleBackdrop`。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 新增 6 个莫兰迪 token；Android 背景新增两处柔光。
- Authoritative code/config/migration evidence: 原型 `:root`、`tokens.json`、`colors.xml`、`glass.xml`、`tokens.css`、`PaperRuleBackdrop`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`.codex/context/source-of-truth.md`、`.codex/rules/ux-design.md`。
- Documentation files updated: `Docs/04-ux-design/design-system/README.md` 的 token 变更记录。
- Producer/consumer/sibling/compatibility paths checked: `GlassMaterialTokensTest` 的计数、`check_design_tokens.py` 的 rgba/hex 分派、`PaperRuleBackdrop` 的既有三处柔光。
- Superseded phrases/examples reverse-searched: 「三处柔光」「10 个 rgba」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 追加莫兰迪条目，不改写 2026-10-21 的晨雾玻璃记录。

## Acceptance criteria

- [x] `GlassMaterialTokensTest` 断言 12 个 glass/glow 与 4 个 mist，并新增 4 个 tint 的 1:1 映射。
- [x] `GlassSurfaceLayoutTest` 断言场景绘制同时读取 `glow_rose` 与 `glow_lilac`。
- [x] 定向 JVM 命令 RED 非 0、GREEN 为 0，命令一致。
- [x] `python -X utf8 .codex/scripts/dev/gen_design_tokens_css.py --check` 与 `check_design_tokens.py` 退出码 0。
- [x] 既有 JVM 套件全绿；未自动提交或推送；未新增依赖。

## TDD execution record

### RED — failing test first
- Test/scenario: `GlassMaterialTokensTest` 要求 12 个 glass/glow 与 4 个 tint；`GlassSurfaceLayoutTest` 要求场景读取 `glow_rose`/`glow_lilac`。token 尚未落地。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.GlassMaterialTokensTest" --tests "com.treelearn.app.screen.GlassSurfaceLayoutTest"`
- Exit code: 1
- Transcript: `artifacts/evidence/android-scheme-a-parity-2026-10-22/053-red.txt`
- Observed failure: `12 tests completed, 3 failed`——`theMorandiTintsAreOpaqueColoursOnAndroid`、`everyGlassTokenHasTheSameArgbCopyOnAndroid`、`theScenePainterUsesTheMistAndGlowTokens`。
- Failure reason validated: 三条失败都指向缺失的 6 个 token 与两处柔光，不是环境问题；同一命令在实现后转绿。

### GREEN — minimum implementation
- Implementation: 原型 `:root`、`tokens.json`、`colors.xml`（4 个 `tl_tint_*`）、`glass.xml`（`glow_rose`/`glow_lilac`）、重新生成的 `tokens.css`；`PaperRuleBackdrop` 新增玫瑰/丁香两处径向柔光。
- Command: `./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.GlassMaterialTokensTest" --tests "com.treelearn.app.screen.GlassSurfaceLayoutTest"`
- Exit code: 0
- Passing evidence: `12 tests completed` 全绿，`BUILD SUCCESSFUL`；证据 `artifacts/evidence/android-scheme-a-parity-2026-10-22/053-green.txt`。
- Regression evidence: 全量 `./gradlew :app:testDebugUnitTest` 退出码 0，96 个结果文件全部重跑；`gen_design_tokens_css.py --check` 与 `check_design_tokens.py` 退出码 0（69 token / 24 资源）。

### REFACTOR — optional
- Cleanup: 修正 `GlassMaterialTokensTest` 中「eight rgba」的过期注释为 twelve；`$meta.source_ref` 与设计系统 README 的 `:root` 行号同步到 22–105。
- Regression evidence: 同一 JVM 套件保持全绿。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- rgba→ARGB 按 `round(alpha*255)` 与 `#AARRGGBB` 大写；资源名把 `-` 换成 `_`。
- 不透明 tint 只进 `colors.xml`，命名 `tl_tint_rose` 等；测试通过 `tokens.json` 的 hex 逐项核对。
- `tokens.css` 只能由 `gen_design_tokens_css.py` 生成。
- 新柔光沿用 `PaperRuleBackdrop` 现有的径向渐变半径与 alpha 混合顺序。

## Verification report

- Verdict: PASS
- Evidence: 定向 RED `rc=1`（3 红）/ GREEN `rc=0`；全量 JVM 退出码 0；token 四方守门 `measured_tokens=69 measured_resources=24`、0 failure；生成器逐字节校验通过。证据文件在 `artifacts/evidence/android-scheme-a-parity-2026-10-22/`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 只把「10 个 rgba」与「3 处柔光」的旧计数改为 12 与 5 处；未删除任何既有 token 断言。
- Gaps: 两处新柔光的浓度只做了 JVM/守门验证，真机观感留待 059 四屏截图。
- Risks: 莫兰迪色在广色域屏上的色偏；五层柔光在低端机上的绘制成本。

## Handoff notes

- Completed: 6 个莫兰迪 token 已贯通原型→tokens.json→Android→Web 生成物；场景新增两处柔光；守门与全量 JVM 绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 054 打包中文衬线字体；055/056 再消费 tint。
- Watch-outs: 不要把手改 `tokens.css`；不要把 tint 放进 `glass.xml`；新增 token 后必须同步测试计数。
