# Filename: T-2026-10-23-background-rules-073.md
# Codex Harness Task: 整页横格从所有界面背景撤掉，背景颜色分布一分不动

id: T-2026-10-23-073
title: 整页横格撤掉，只删一层
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-glass-depth-requirement-060.md
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: android
stack: android
estimated_production_lines: 0
shared_code_touched: true
depends_on: [T-2026-10-23-glass-control-face-072.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Problem/background: 用户 2026-10-23 指令「还有个问题，背景中的横线去掉吧，现在不好看，所有界面的背景中的横线都去掉，不要影响其他地方，以及背景的颜色分布等，都不要影响」。横格是 061 之前信纸质感的遗留层，叠在全站雾面场景最上面；玻璃面变实之后，它开始与卡片自己的边线打架，读起来像「屏幕上有脏条纹」而不是「这是纸」。
- Users/scenarios: 全部界面共用的背景装饰层（Android 的 `shell_backdrop`、Web 的 `body`），首页/学习库/聊天/设置都画同一层。
- Expected outcome: 横线消失，而 mist 四色渐变与五处径向柔光**一处不少、一位不偏**——用户点名的那条约束（背景颜色分布不受影响）是这片真正的验收对象，删本身不难。
- Core flow and reversibility: 三处绘制同批删（原型 `.screen`、Web `glass.css` 的 `body`、Android `PaperRuleBackdrop.onDraw` 的画线循环）。撤销是把三层加回来，但加回来必须同时恢复已经失去生产者的那几个数值，所以「可逆」是有成本的，写在这里而不是假装一键还原。
- Inputs/outputs: 输入是既有的场景绘制路径与 `paper-rule` token；输出是三层删除 + 一批随之失去生产者的数值清理 + 两条负断言。不新增文件、不新增依赖。
- State and accessibility/exception handling: 无状态变化；横线是纯装饰，删除不影响任何命中区、焦点顺序或可读性（背景与文字对比只升不降）。
- Constraints and trade-offs: 「只许删一层」这条约束决定了这片的技术方案——不能整段重写背景，否则 mist/glow 的坐标会被顺手「优化」。取舍是保留 `PaperRuleBackdrop` 这个**名字**与这个类（改名要重写已归档的 parity 证据，视觉收益为零），只掏掉它的画线职责，并在 javadoc 里写明它现在是 <em>rule-free</em>。
- AI recommendation and rationale: 连带的死数值一并清掉，符合项目既定的「接不了真数据就删，不留死数值」原则：`PaperRules.pageRuleColour` / `ruleAlpha` / `RULE_ALPHA` 与 backdrop 的三个测试 seam（`ruleColor()` / `spacingPx()` / `thicknessPx()`）在画线循环删除后就没有生产者了；同时**保留**输入卡内的信纸格线（那是面内横线，不是背景）与 `paper-rule` token 本身（Web 13 屏快照的 `.snap-screen` 仍消费它）。
- User-confirmation decisions: 「背景中的横线去掉吧……所有界面的背景中的横线都去掉，不要影响其他地方，以及背景的颜色分布等，都不要影响」（2026-10-23）。
- AI-autonomous decisions: ① 三处同批删，不留「Android 先删、Web 下一片」的中间态（用户说的是「所有界面」）；② 类名与文件名不动，只在 javadoc 记事实；③ token 不退役（有活消费者），只断开真实界面的引用；④ 证法选「同一条带两帧对照 + 运行时 computed style」，而不是只加一条源码断言——因为用户约束的是像素结果，不是源码写法。
- Acceptance evidence: 同机同页两帧对照：变化的行只有 56 段中的 17/20 段严格落在 104px 格子（= `@dimen/space_s8`，原画线周期），段长只有 {1,3}px 两种；剔除有线行后的 1828 行里 |ΔL| 中位 0.00、|Δ(R-B)| 中位 0.00。Web 运行时回读 `body` 实际绘制 6 层（5 枚 radial + 1 枚 linear），`repeating` 计数 0、`--paper-rule` 引用计数 0。
- Facts: 横格有三处生产者（原型 `_src/glass.css` 的 `.screen`、`frontend/src/styles/glass.css` 的 `body` 最上层、`screen/PaperRuleBackdrop.onDraw`），三者是同一层装饰在不同宿主上的副本；`space_s8` = 104px @density 3.25；`PaperRules.writingRuleColour` 是输入卡格线的唯一判定点，与背景层无关。
- Assumptions: 用户说的「横线」是背景上那条重复格线，不包括卡片边线、输入卡信纸格线与发丝线（`glass_line` / `hairline`）——从「所有界面的背景」这个限定语判断，指的就是共用的装饰层。
- Unknowns: 撤掉格线后，浅色背景下「这是纸不是塑料」这条线索只剩 mist 渐变与字重承担；若用户下一轮说「太平」，讨论的应是纸张颗粒或更淡的面内纹理，不是把背景格线加回来。
- Risks: 有人把「背景没横线」误当成回归而去恢复它；所以负断言（源码里不得再出现 `lineOffsets` / `tl_line` / `PaperRules.`）与 `glass-depth.test.ts` 那条点名留下 6 层的断言是这片的主要防线。
- Deferred: 纸张颗粒 / 面内纹理（未拍板，不做）；原型 HTML 的重渲染快照一致性复核（快照仍按原样引用 `--paper-rule`）。
- Non-goals: 不动 mist 与 glow 的任何坐标/半径/色值、不动输入卡格线、不改 token 值、不给两端分档判据、不新增依赖、不自动提交或推送。
- Skipped non-critical questions and reason: 未追问「卡片里的格线要不要一起去掉」——用户限定的是「背景中的横线」，输入卡格线是面内纹理，问了反而诱导扩大范围。

## Goal

所有界面共用的背景横格层在三处宿主上一起消失，同时用像素证明背景的明暗与色相分布一分未动。

## Scope

### In scope
- 删除原型 `.screen` 的 `repeating-linear-gradient`、Web `glass.css` 的 `body` 最上层、Android `PaperRuleBackdrop.onDraw` 的画线循环。
- 随之清掉失去生产者的数值与 seam：`PaperRules.pageRuleColour` / `ruleAlpha` / `RULE_ALPHA`、backdrop 的 `ruleColor()` / `spacingPx()` / `thicknessPx()`；`PaperRulesTest` 的整页纸纹用例退役。
- 两条负断言（Android）＋一条点名断言（Web）。
- 同机两帧对照与 Web 运行时 computed style 取证。

### Out of scope
- mist 四色与五处柔光的任何参数、输入卡信纸格线、`paper-rule` token 的值与存在、`PaperRuleBackdrop` 的类名与文件位置、玻璃面材质、业务组件。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 背景少一层重复横线；mist 渐变、五处柔光、卡片边线、输入卡格线、玻璃面与投影全部原样保留。
- Producer/consumer/sibling entrypoints and platform branches（代码证据）：`PaperRuleBackdrop` 由 `activity_main.xml` 的 `shell_backdrop` 全屏持有，是所有 Android 界面的共用祖先（这就是「所有界面」一次改完的依据）；Web 侧 `body` 同理。pad 走同一份 `PaperRuleBackdrop`，无平台分支。`PaperRules.writingRuleColour` 的消费者（`HomeComposerLayoutTest` 点名的那处）保留。
- Native bridge or independent-client compatibility：无。
- Behavior-neutral non-applicability reason：不适用（这是视觉行为改动，但无数据/状态/命中影响）。

## Documentation impact record

- Changed runtime facts: 全站背景不再画横格；`PaperRuleBackdrop` 的职责从「mist + glow + 横格」变「mist + glow」；`paper-rule` 在真实界面无消费者（仅剩快照）。
- Authoritative code/config/migration evidence: `screen/PaperRuleBackdrop.java`（javadoc 写明 <em>rule-free</em> 自 T-2026-10-23-073 及其原因）、`Docs/04-ux-design/design-system/tokens.json` 的 `paper-rule` note。
- Documentation surfaces checked: `android/README.md` 第 693 行（场景层描述）、`Docs/04-ux-design/design-system/README.md`（token 表 + 变更记录）、`frontend/src/styles/glass.css` 头注释。
- Documentation files updated: `android/README.md` 第 499 行改为「横格自 2026-10-23-073 起不再画」并保留 mist/glow 的原句；设计系统 README 新增 073 变更记录；`glass.css` 注释标注片号。
- Producer/consumer/sibling/compatibility paths checked: 反向搜索「横格」「纸纹」「rule」「repeating-linear-gradient」「`--paper-rule`」在代码、测试与文档中的全部出现，逐处区分背景（删）与面内格线/快照（留）。
- Superseded phrases/examples reverse-searched: 061/062 卡片与其证据里描述横格的原文**不改写**（那是当时的事实），只在当前态文档里更新。
- Historical/PRD handling: 历史卡片保留；当前态只由 `android/README.md` 与设计系统 README 表述。

## Acceptance criteria

- [x] 三处背景的横格层都不再绘制，且没有任何界面残留（共用宿主各一处，改一次即全站；`dumpsys` 与 Web computed style 双向确认）。
- [x] 用户点名的约束：背景颜色分布不动——同一条带剔除有线行后 1828 行 |ΔL| 中位 0.00、|Δ(R-B)| 中位 0.00；Web 留下的 6 层逐层点名核对。
- [x] 保留行为：输入卡信纸格线仍在（`PaperRules.writingRuleColour`）、卡片边线与发丝线仍在、玻璃面与投影读数不变。
- [x] 无死数值：画线循环删除后失去生产者的常量与 seam 一并清掉，`check_design_tokens.py` 无新增 FAIL。

## TDD execution record

### RED — failing test first
- Test/scenario: `GlassSurfaceLayoutTest.theScenePainterUsesTheMistAndGlowTokens` 与 `HomeComposerLayoutTest.theWritingTextureReadsItsColourFromTheOneJudgement` 各加一条负断言（backdrop 源码不得再出现 `lineOffsets` / `tl_line` / `PaperRules.`）；`frontend/test/styles/glass-depth.test.ts`「整页背景不再画横格，但场景的渐变与五处柔光一处不少」。
- Command: N/A（无活体 RED，见下方 exception 栏；GREEN 用的命令如实记录，两栏不假称同一条命令跑过两次）
- Exit code: N/A
- Observed failure: **无活体 RED 转录**。断言与三层删除同批写下；事后「把线加回去再跑一次」得到的红不携带任何新信息（缺陷是肉眼可见的、且改动是三处纯删除）。
- Failure reason validated: 等效证据直接量的是用户约束本身（见 Alternative verification），不是断言字符串能否变红。

### GREEN — minimum implementation
- Implementation: 三处删除 + 死数值/seam 清理 + 三条断言 + 文档当前态更新。
- Command: `pnpm --dir frontend exec vitest run test/styles`
- Exit code: 0
- Transcript: `artifacts/evidence/background-rules-2026-10-23/073-web-styles-green.txt` → `Test Files 4 passed (4)` / `Tests 20 passed (20)`。（与 072 卡共用同一次运行：两片连续落地后最后一次跑即两者当前的绿。）
- Passing evidence: `test/styles` 4 files / 20 tests passed；`pnpm --dir frontend verify` steps=16 ok=16 bad=0 fault=0。
- Regression evidence: Android `./gradlew :app:testDebugUnitTest` exit 0（本片退役 2 例整页纸纹用例：946 → 944）；`check_design_tokens.py` OK=10 FAIL=0 SKIPPED=0；`gen_design_tokens_css.py --check` 逐字节一致（bytes=2348，tokens=72）。真机 `adb install -r` Success。

### REFACTOR — optional
- Cleanup: 顺带清掉了三个只为测试存在的 seam（`ruleColor()` / `spacingPx()` / `thicknessPx()`），它们失去生产者后就没人调用。
- Regression evidence: 全量 JVM 0 失败；被退役的 2 例只测「背景有横线」这一件事，其反面由新的负断言接管。

### LOOP — optional
- 不适用。

### TDD or sizing exception
- Exception type: approved-exception（断言先行，无活体 RED）
- Reason: 三处纯删除的视觉缺陷，「先红」只能靠把已删的绘制加回去制造，不产生新信息；用户约束的是像素结果，所以这片的主要证据是像素对照而不是断言转录。
- User approval: 在用户「去掉横线、不要影响其他地方」的指令范围内直接执行；Harness 未对本片要求 verifier。
- Alternative verification: ① 同机同页同一条带（x 2..58、y 400..2300，1900 行）两帧对照——修前 69 行/22 段/最大 12.61 L 偏离，修后 14 行/5 段/最大 0.89 L（再取一帧 14 行/6 段/最大 1.00 L，即取整噪声级）；② 变化行的段首间距 104,104,… 严格对上 `space_s8` 周期，段长只有 {1,3}px = 原线厚；③ 可比行 |ΔL| 与 |Δ(R-B)| 中位均为 0.00，这就是「颜色分布没动」的量化说法；④ Web 侧读浏览器实际算出的 `backgroundImage`（不是 CSS 文本），6 层逐层与 `PaperRuleBackdrop` 的参数对应。

## Implementation notes

- 「只删一层」是这片最难的部分，也是最容易在后来被破坏的部分：留下的 6 层在 `glass-depth.test.ts` 里被逐层点名，改错任何一枚 glow 的圆心或半径都会红。
- `PaperRuleBackdrop` 的类名保留不是疏忽：改名会重写 `artifacts/evidence/` 里已归档的 parity 证据文件名与引用，而视觉收益为零。理由写在类的 javadoc 里，卡片只留指路。
- `paper-rule` token 保持 `.018` 不退役、不改名，因为 Web 13 屏快照的 `.snap-screen` 还在消费它——「真实界面不画」与「token 没人用」是两件事。
- 073 与 072 是同一天连续两片：072 处理「两层文字互相渗透」，073 处理「背景条纹与卡片边线打架」，两者都属「玻璃做上去之后旧装饰开始碍事」这一族。

## Verification report

- Verdict: PASS
- Evidence: 单元——`test/styles` 20 例 + Android 全量 944 例（075 后 946）0 失败。契约/守门——`check_design_tokens.py` OK=10 FAIL=0，`gen_design_tokens_css.py --check` 逐字节一致。像素——`073-before-home.png`（10:30，横格在位）对 `073-after-home.png`（16:26，当前构建）同一条带对照，读数见 `073-reading.txt` 第 1/2 节。运行时——`073-web-computed-style.txt` 是 `getComputedStyle(document.body).backgroundImage` 的 raw 原文。真机——`adb install -r` Success。
- Changed/deleted assertion rationale and equivalent behavior coverage: 退役 `PaperRulesTest` 的整页纸纹用例（946→944，2 例）——它们断言的正是被撤销的行为，保留即永久红；覆盖面由 `GlassSurfaceLayoutTest` 与 `HomeComposerLayoutTest` 的两条新负断言接手（反向：源码里不该再出现画线的痕迹），输入卡格线仍由 `theWritingTextureReadsItsColourFromTheOneJudgement` 正向守着。
- Gaps: 无宽窗/Pad 真机截图（本机一台手机，共用宿主使风险低）；Web 只回读了 `body`，未逐屏截图复核快照线（`.snap-screen` 仍在用 `--paper-rule`，属有意保留）。
- Risks: 「背景没横线」被误判为回归而恢复；已由三条断言与两处 javadoc 兜住。

## Handoff notes

- Completed: 三处背景横格删除、死数值与 seam 清理、三条断言、像素与运行时双证、当前态文档更新。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 若用户反馈背景「太平」，先讨论面内纹理或纸张颗粒，**不要**恢复背景格线。
- Watch-outs: `PaperRuleBackdrop` 里不要新增任何重复图案层——它是所有 Android 界面的共用背景祖先，加一层就等于给所有界面加，且这次的三条负断言会先红。
