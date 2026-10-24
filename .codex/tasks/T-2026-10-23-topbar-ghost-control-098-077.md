# Filename: T-2026-10-23-topbar-ghost-control-098-077.md
# Codex Harness Task: 常驻顶栏不再透字：控制面 .93 → .98（Android + Web 同链）

id: T-2026-10-23-077
title: 常驻顶栏透印，控制面抬到 .98
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
depends_on: [T-2026-10-23-pinned-topbar-075.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 用户 2026-10-23 指令「首页，顶部的那一栏，不透明度太低，会导致和下一层的内容有重叠」。075 把两枚一级顶栏改成常驻悬浮之后，列从栏下穿过；075 当时把栏换成 072 的 `--glass-control`（.93 平涂）并判定残差「只有 0.09% 且全在投影带上」。这个缺陷是那条裁决留下的：推荐语整行仍以可读水印穿过 .93 的面。
- Users/scenarios: 首页与学习库的常驻顶栏（`home_appbar` / `library_appbar`），任何一条文字行滚到栏下时都成立；首页最明显，因为 hero 与推荐语都是大字号深色行。
- Expected outcome: 栏内读不出栏下的内容，而「栏浮在列上」这条 075 已拍板的结构不变——不靠把内容挡在栏外，也不靠把栏改回随页面滚动。
- Core flow and reversibility: 改的是一枚 token 的 alpha，链上四处（原型 `:root` → `tokens.json` → `values/glass.xml` → 生成的 `tokens.css`）加两处下限断言。撤销是把 alpha 抄回 .93，但那样缺陷立刻回来，所以真正的回退路径是「换材质路线」（PixelCopy 截栏后区域再糊），不是一键还原。
- Inputs/outputs: 输入是既有材质链与既有仪器（`check_glass_depth.py` 的 8 L 线、`png_probe`）；输出是 token 一处数值 + 两处下限 + 一台真机的按行读数。不新增文件到生产面、不新增依赖。
- State and accessibility/exception handling: 无状态变化。纯提升面与文字的对比，可读性只升不降；命中区、焦点顺序、滚动结构一个没动。
- Constraints and trade-offs: 「不新增一面」决定了技术方案——072 已经把「面上的文字压在别处文字之上」这一类定义成 `glass-control`，再开第七面是给同一个语义放两个数。取舍是这一面在静态下与纯白只差 0.3 L，通透度基本让光；换来的是不需要真模糊也不再透字。Android 没有 backdrop blur（`RenderEffect` 只糊视图自身），所以「透而不可读」这条在 Java-only 上拿不到。
- AI recommendation and rationale: 判据复用 `check_glass_depth.py` 已登记的 `J4_MIN_DRIFT = 8.0`（方向相反），不新调一个数：8 L 是本项目对「看得出来面下有东西在动」的既有分界，对通透面是拿分线，对控制面就是缺陷线。抬到 .98 后同一批行 3.00 L，留 2.7 倍余量。
- User-confirmation decisions: 「首页，顶部的那一栏，不透明度太低，会导致和下一层的内容有重叠」（2026-10-23）。
- AI-autonomous decisions: ① 抬 `glass-control` 本身，不开新面（Web 三个 control 消费者随之上移，代码里没有 .93 字面量，全走 `var()`）；② 下限从 0.92 抬到 0.97 而不是抄当前值（抄值会出现第二份事实，且对「更透明」方向永不响）；③ 底部胶囊与库页不重测，理由写在 Gaps；④ 075 的裁决按「后账」补记，不改写它当时的读数原文。
- Acceptance evidence: 同一台机、同一个取样框、同一批行的前后对照：.93 时 y=256..284 变化像素 13.62..22.25% / |ΔL| max 10.28（8 条行全 FAIL）；.98 时 0.00% / max 3.00（8 条行全 PASS）。单帧墨深 11.28 → 3.00 L。
- Facts: `home_appbar` dumpsys 为 `52,20-1148,189`（绝对 y 135..304），自身控件只占 x=78..195 与 x=1005..1122；`home_content` 与 appbar 同为 52..1148 内缩，所以 x<52 / x>1148 没有内容穿过（两帧同位 |ΔL| = 0.00）；`glass_control` 的 ARGB 副本由 `GlassMaterialTokensTest` 按 `round(alpha×255)` 从 `tokens.json` 重算（.98 → 250 → `#FAFFFFFF`）；Web `.workspace-topbar` 取 `--glass-chrome` + `backdrop-filter`，不引用 control。
- Assumptions: 用户说的「重叠」是栏内看得见栏下那一行的影子，不是「栏挡住了本该看见的内容」——从「不透明度太低」这个限定语判断，指的是前者。
- Unknowns: 3.00 L 是水印而不是可读文字，但不是零；用户在实际亮度/距离下是否仍觉得看得见，只有下一轮反馈能回答。可用的下一档是 .99（算术预测 1.5 L）。
- Risks: ① 有人把这一面当成「还能再透一点」的候选往下调，那 8 条行立刻回到 10 L；由两处下限断言拦。② 075 那句「残差全在投影带上」被后来人当现行结论复用；已在那条变更记录后补「后账」。③ 取帧取到一对没滚动的帧，FAIL 会长得像 PASS；读数文件第 1 节把这次踩的坑写明了。
- Deferred: PixelCopy 截栏后区域再模糊（能同时拿回通透与不可读，但属另一片的量级）；宽窗/Pad 真机实拍。
- Non-goals: 不动 `glass-chrome` / `card` / `reader` / `field` 任何一面、不动 075 的常驻结构与 58dp 让位、不给胶囊投影带调参、不新增 token、不自动提交或推送。
- Skipped non-critical questions and reason: 没问「要不要把顶栏改回随页面滚动」——075 是用户当天明确拍板的常驻，推翻它要先由用户提出；也没问「要不要顺手把底部胶囊一起重测」，那片的风险更低且结论方向单调。

## Goal

两枚常驻顶栏在真机上读不出栏下穿过的内容，且这条结论由一个已登记的数值分界（8 L）而不是主观印象给出。

## Scope

### In scope
- `glass-control` 由 `rgba(255,255,255,.93)` 抬到 `.98`，走完整 token 链（原型 `:root` → `tokens.json` → `values/glass.xml` → 生成的 `tokens.css`）。
- 两端下限断言 `glass-control` 由 0.92 抬到 0.97，理由与读数写进注释。
- 按行的真机读数与可复现仪器（`077-bar-ghost.py`）。
- 当前态文档：设计系统 README 的分组表与变更记录、`android/README.md` 的外壳行。

### Out of scope
- 其余五面与 `edge` / `line` / `sheen` / `shadow-*` / `tint-falloff`、075 的常驻结构与 `top_bar_clearance`、胶囊圆角与投影带、底部标签栏的取区与判据、业务组件。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 意图改动只有「栏下内容还剩多少可见」；保留的是常驻悬浮、58dp 让位、圆角胶囊、sheen 与投影、以及随页面滚动的两枚栏（聊天 / 抽屉导航继续 `bg_glass_bar` = chrome .74 + 145° tint）。
- Producer/consumer/sibling entrypoints and platform branches（代码证据）：生产者一枚 `@color/glass_control`，消费者是 `bg_glass_control_bar.xml` 的 `<solid>`，它被 `screen_home.xml` / `screen_library.xml` / `screen_tab_bar.xml` 三份布局引用（含 `layout-sw600dp/` 同名三份），所以手机与宽窗、顶栏与底部胶囊一起上移——这就是「改一处即全站」的依据。Web 侧 `var(--glass-control)` 的三个消费者是 `.suggestion:hover`、`.settings-persona-entry:hover`、`.persona-feedback`；`.workspace-topbar` 不在此列（chrome + blur）。
- Native bridge or independent-client compatibility：无。token 链与 1:1 仪器都不引用 `glass-control`（`verify_1to1.mjs` 的 `DOM_CHECKS` 里没有它），本片不会动 parity 词表。
- Behavior-neutral non-applicability reason：不适用（材质改动，但无数据/状态/命中影响）。

## Documentation impact record

- Changed runtime facts: 控制面 alpha .93 → .98；两枚常驻顶栏与底部胶囊不再透出栏下内容；`GlassMaterialTokensTest` / `glass.test.ts` 的 control 下限 0.92 → 0.97。
- Authoritative code/config/migration evidence: `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `:root --glass-control`、`Docs/04-ux-design/design-system/tokens.json` 的 `glass-control.note`、`android/app/src/main/res/values/glass.xml`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`（分组表 + 变更记录）、`android/README.md` 第 1123 行、`frontend/README.md`、`Docs/spec/`。
- Documentation files updated: 设计系统 README 新增 077 变更记录、给 075 那条补「后账」、分组表 `color` 行补 `glass-control`（072 漏登记）与 `glass-shadow-key` / `glass-shadow-ambient`（061 漏登记）；`android/README.md` 第 1123 行改为按行读数与 8 L 判据。
- Producer/consumer/sibling/compatibility paths checked: 反向搜索 `glass-control` / `glass_control` / `.93` / `EDFFFFFF` 在代码、测试、文档与仪器中的全部出现，逐处区分「链上的值」（改）与「reader 面的 .93」（不动）。
- Superseded phrases/examples reverse-searched: 072 与 075 卡片里「.93 平涂」「面内直边段 0.49%」的原文**不改写**（那是当时的读数），只在当前态文档里更新，并在 075 那条后补账。
- Historical/PRD handling: 历史卡片保留；当前态只由设计系统 README 与 `android/README.md` 表述。

## Acceptance criteria

- [x] 两枚常驻顶栏在真机上读不出栏下穿过的内容：面内中段按行 |ΔL| max 10.28 → 3.00，变化像素 13.62..22.25% → 0.00%，判据线 8.0 L。
- [x] 结构不变：075 的常驻悬浮、`top_bar_clearance`(58dp)、`clipToPadding` / `clipChildren` 一个字节没动；滚动仍由正文三段 44.20% / 51.23% / 45.43% 的同位变化证明「页面是活的」。
- [x] Web 同步：token 链四处一致、`gen_design_tokens_css.py --check` 逐字节一致；`.workspace-topbar` 因不引用 control 而按构造不变（075 登记的材质端差未被推翻）。
- [x] 无第二份事实：断言仍是下限（0.97）而不是当前值；代码里没有任何 .93 字面量残留。

## TDD execution record

### RED — failing test first
- Test/scenario: `GlassMaterialTokensTest.theGlassSurfacesStayAboveTheAgreedOpacityFloors` 与 `glass.test.ts`「六面 alpha 停在拍板下限之上」——只把 `glass-control` 的下限从 0.92 抬到 0.97，token 一个字不动。
- Command: `pnpm --dir frontend exec vitest run test/styles/glass.test.ts`
- Exit code: 1
- Observed failure: `AssertionError: --glass-control 低于拍板下限: expected 0.93 to be greater than or equal to 0.97`；`Test Files 1 failed (1) / Tests 1 failed | 5 passed (6)`。
- Failure reason validated: 红的正是本片要改的那条事实（面的 alpha 不够），不是断言字符串能否变红。Android 侧同一条思路的第二判据：`./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.screen.GlassMaterialTokensTest"` → `5 tests completed, 1 failed`，`glass-control must stay at or above alpha 0.97, got 0.93`。

### GREEN — minimum implementation
- Implementation: 链上四处把 control 抬到 .98（原型 `:root`、`tokens.json` 值与 note、`glass.xml` `#EDFFFFFF`→`#FAFFFFFF`、重出 `tokens.css`）。生产代码零改动，布局零改动。
- Command: `pnpm --dir frontend exec vitest run test/styles/glass.test.ts`
- Exit code: 0
- Transcript: `Test Files 1 passed (1) / Tests 6 passed (6)`——与 RED 同一条定向命令，唯一的自变量是链上的 alpha。
- Passing evidence: 同族全量 `pnpm --dir frontend exec vitest run test/styles` → `Test Files 4 passed (4) / Tests 20 passed (20)`；Android `./gradlew :app:testDebugUnitTest` exit 0（app=946 例，0 失败）；`check_design_tokens.py` OK=10 FAIL=0 SKIPPED=0；`gen_design_tokens_css.py --check` 逐字节一致（bytes=2348，tokens=72）；`pnpm --dir frontend verify` steps=16 ok=16 bad=0 fault=0；真机 `adb install -r` Success。
- Regression evidence: 像素判据 `python -X utf8 artifacts/evidence/topbar-ghost-2026-10-23/077-bar-ghost.py` → 修前 8 条行全 FAIL、修后全 PASS（同一框、同一批行、唯一自变量是 alpha）。判据之外补一次全面扫（x=200..1000 step2、y=140..300 step2，32400 个采样点，两帧同位）：越过 8 L 线的像素 3.01% → 0.00%，max 10.28 → 3.00 L——面上没有任何一处以 8 L 的幅度把下层带上来，不只是那几条推荐语。

### REFACTOR — optional
- Cleanup: 顺带补登记分组表 `color` 行漏掉的三个 token 名（`glass-control`、`glass-shadow-key`、`glass-shadow-ambient`）。
- Regression evidence: `check_design_tokens.py` 仍 OK=10 FAIL=0（表是文档，不参与比对，故不构成新事实源）。

### LOOP — optional
- 不适用。

## Implementation notes

- 这片最难的不是改数值，是**先证明是 alpha 而不是几何**。三条候选（裸条、投影带、面内透印）分别量完才动 token：裸条两帧同位 |ΔL| = 0.00 直接排除「内容从栏外侧穿过」，投影带 48→20 点不动它，剩下面内才是缺陷本体。少了这一步，很容易顺手把栏拉满宽，那是把 075 的裁决改坏。
- 取样框是从 dumpsys 里 appbar **自身控件的 x 段**推出来的（78..195 与 1005..1122），所以 x=200..1000 里出现的墨必然来自栏下——这条让「透印」这个结论不依赖人眼看图。
- 判据用 8 L 而不是新调一个数：`J4_MIN_DRIFT = 8.0` 已经是本项目对「面下有东西在动」的登记分界，它的自检还反向断言同一张图必须读不出漂移。对通透面越过 8 是拿分，对控制面是缺陷——同一个数、两个方向，这比再立一个阈值更难被日后随手放宽。
- 075 为什么没抓到：它的面内框是 `y 150..250`，**恰好停在穿过的那几条行（256..284）之上 6 行**。缺陷不在框里，「干净」是取区取出来的。这条写进 075 那条变更记录的后账，也写进仪器脚本的 docstring。

## Verification report

- Verdict: PASS
- Evidence: 单元——Web `test/styles` 20 例、`test/styles/glass.test.ts` 6 例（RED/GREEN 同一条定向命令）、Android 全量 app=946 例 0 失败。契约/守门——`check_design_tokens.py` OK=10 FAIL=0，`gen_design_tokens_css.py --check` 逐字节一致，`frontend verify` 16/16，`run_all_guards.py` pass=38 fail=1（唯一那条红是上一片遗留的 `check_pointer_liveness`，人设 SPEC 锚点落在空行，与本片的 token 链无因果关系），`checkers_selftest` 268/268，`metrics_report.py`（只读档）0 failed，`validate_tasks.py` files=72 errors=0。像素——`077-bar-ghost.py` 前后两对帧同框同批行：10.28 L / 13.62..22.25% → 3.00 L / 0.00%；单帧墨深 11.28 → 3.00 L，与 alpha 算术预测 3.19 相符；全面扫 8 L 越线像素 3.01% → 0.00%。真机——`adb -s AWSS013807000356 install -r` Success。
- Changed/deleted assertion rationale and equivalent behavior coverage: 没有删除任何断言；两处 control 下限由 0.92 抬到 0.97，仍是下限不是当前值。072 立的「控制面平涂（无 `_lo`、无 145° tint）」与「悬停不许比静止更透明」两条原样保留——本片只朝更不透明方向动，不会让倒挂重新成立。
- Gaps: ① 只有手机真机，宽窗/Pad 无实拍（`layout-sw600dp` 三份共享同一枚 `@color/glass_control`，材质结论可迁移，但「宽窗下推荐语穿栏」没拍过）。② 底部胶囊与库页未重测：这两帧里胶囊面内恰好是列表底部留白经过（0.00%），库页 075 量过顶栏带 0.83% / 中位 0.07 且栏内无常驻动画；alpha 只朝更遮住方向动，两者不会因本片变差。③ 3.00 L 不是零，放大到 2x 仍能看出那一行的影子。④ 上一片留下的 `check_pointer_liveness` 那条红（人设 SPEC 的锚点落在空行）与本片的 token 链无关，未在本片处理。
- Risks: 后续把这一面往回调透会立刻让 8 条行回到 10 L，由两处下限拦；把「栏内应该看得见内容」当成回归来恢复，由 `077-bar-ghost.py` 的判据与两处文档注释拦。

## Handoff notes

- Completed: 链上四处 .98、两端下限 0.97、真机按行读数与可复现仪器、前后对照图、当前态文档与 075 后账、守门与 metrics。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 若用户仍说看到重叠，下一档是 .99（预测 1.5 L，优于 Web 的 2.28），代价是这一面静态下与纯白只差 0.3 L；若用户改口要「透但读不出」，那要走 PixelCopy 截栏后区域再糊，单独立一片，不要靠降 alpha 硬凑。
- Watch-outs: ① 别为这一面再开第七个 token——072 的语义定义已经覆盖它，两个数管同一件事必然漂移。② 别把 `glass_reader` 的 .93 一起改：它与 control 同值不同义，`grep` 时最容易顺手。③ 取帧必须先看正文三段有没有 40%+ 的同位变化，否则「栏带在动」可能只是投影带，FAIL 会长得像 PASS。
