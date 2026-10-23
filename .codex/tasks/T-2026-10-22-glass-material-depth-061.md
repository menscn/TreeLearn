# Filename: T-2026-10-22-glass-material-depth-061.md
# Codex Harness Task: 玻璃材质投影/受光缘/tint 衰减与雾面背景质感

id: T-2026-10-22-061
title: 玻璃材质投影/受光缘/tint 衰减与雾面背景质感
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-glass-depth-requirement-060.md
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: android
stack: android
estimated_production_lines: 390
shared_code_touched: true
depends_on: [T-2026-10-22-glass-depth-requirement-060.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 父卡 060 实测确认玻璃与背景只差 4% 亮度、Android 侧投影为 0、事实源里的 `--glass-shadow` 仍是「不透明表面」时代的 `.07` 弱值，且雾面留区亮度跨度只有 12 L、彩度≈0。方案 A 权威 `.glass` 的四层（上缘亮 rim、下缘暗 rim、主投影、环境投影）与 145° 对角 tint 从没进过 token 链。
- Users/scenarios: 同 060：ALP-AN00 真机为主，Web 为同一批 token 的第二个消费者。
- Expected outcome: 玻璃面有可测投影暗带、对角明暗渐变与顶部受光；雾面有明暗跨度与色相漂移；两端数值仍只有一处权威。
- Core flow and reversibility: 判据脚本先行 → 原型 `:root` 改/增 token → `tokens.json` 镜像 → `glass.xml`/`colors.xml` 副本 → `gen_design_tokens_css.py` 重生成 → Android drawable 与 `PaperRuleBackdrop` 消费 → Web `glass.css` 消费 → 真机截图跑 J1–J3。每步可单独撤销。
- Inputs/outputs: 输入是方案 A 的 `.glass` 数值与真机基线读数；输出是新增/修订 token、`check_glass_depth.py`、Android 材质与场景改造、Web 材质消费、JVM 断言与截图证据。
- State and exception handling: 只改数值与绘制；View 层级、id、命中区域与滚动行为不变（让位改造属 062，本片不碰）。阅读面固定 86%。低动效/降级路径不变。
- Constraints and trade-offs: Java-only、零 AndroidX、无新依赖；`shape` 渐变只支持 45° 倍数，取 315°（=CSS 135°）与提案 145° 差 10°，按机械等价登记；elevation 阴影几何由系统决定，本片刻意不用手绘假阴影，也不与 elevation 并存两套。
- AI recommendation and rationale: 阴影与背景跨度是「浮起来」收益最高的两项，先做；方向性 rim 不做近似（近似会把「一条 1px 亮边」变成两处互相矛盾的读数），改为把提案已有的顶部受光层补到唯一缺它的那两块面上。
- User-confirmation decisions: A 材质对齐要做；背景颜色质感要加强（用户 2026-10-22 明确要求）。
- AI-autonomous decisions: ① 阴影落两档 token（key/ambient），值取方案 A 原档并按 Android 能力映射；② 新增 `--glass-shadow-key/--glass-shadow-ambient/--glass-tint-falloff` 与四个 tint 暗端副本；**rim 两档与 `--mist-grain` 已撤回**——撤回的理由是「不做死按钮」：Android 的 1px 边只能由 stroke 画一圈，给不出一头亮一头暗，token 落地也读不到方向性，颗粒则在真机上没有观察到 banding，没有要治的病；③ `--glass-shadow` 旧值被两档取代而非并存，删除动作留给片 063（Web 是它最后一个消费者，本片删会让 Web 当场悬空）；④ mist 四色与 glow 五色只改数值，`--paper-rule` 维持 .018；⑤ 底栏/侧栏补上 `glass_sheen` 受光层——提案把高光画在 `.glass` 上、`glass.css` 把它画在 `.workspace-topbar`/`.app-sidebar`/`.branch-panel` 上，Android 唯独这两块面没有，补齐是消端差而不是加装饰。
- Acceptance evidence: J1（玻璃—背景 ≥10 L）、J2（投影暗带 ≥6 L）、J3（跨度 ≥24 L 且彩度 ≥6）、J5/J6 守门与套件全绿；证据在 `artifacts/evidence/android-glass-depth-2026-10-22/`。
- Facts: `.glass` 四层在方案 A 文件 392–404 行；`check_design_tokens.py` 把不透明 hex 映射到 `colors.xml` 的 `tl_*`、rgba 不参与该映射，故新 rgba token 落 `glass.xml`；`GlassMaterialTokensTest` 重算 alpha 并计数；`tokens.css` 只能由 `gen_design_tokens_css.py` 生成且守门做逐字节比对。
- Assumptions: elevation + 自定义 outline provider 能让半透明背景投影生效（outline alpha 与背景填充解耦）。
- Unknowns: 加深后的雾面与半透明白叠加时正文对比余量；颗粒层的绘制成本。
- Risks: 投影过强读成 Material 卡片；背景加深读成「脏」；token 改名会同时打断 Web 与测试计数。
- Deferred: 真折射、深色模式、按屏分档投影。
- Non-goals: 不改滚动让位（062）、不改业务逻辑/数据库/接口/依赖、不自动提交或推送。
- Skipped non-critical questions and reason: 未追问是否需要四档投影；先用两档并在真机读数后决定，避免一次发明四个数值。

## Goal

让玻璃面在真机上产生可测投影与方向性受光，并让雾面背景产生可测的明暗跨度与色相漂移，数值仍只由 token 权威链提供。

## Scope

### In scope
- `check_glass_depth.py`：对真机截图按固定区域取法给出 J1–J4 数值与判决，带 `--selftest` 正反用例。
- token 链：新增阴影两档与 `glass-tint-falloff`、四个 tint 暗端副本；修订 `mist-*` 与 `glow-*` 数值；`--glass-shadow` 由两档取代（删除留给片 063）。
- Android：`glass.xml` ARGB 副本、10 个 `bg_glass_*.xml` 的对角 tint、悬浮面的 outline provider 与 elevation 阴影色、底栏与侧栏补顶部受光层。
- `tokens.css` 由生成器同步（Web 侧副本必须与四方一致），但 Web 的实际材质消费、场景加深与顶栏让位归片 063，避免两片共写同一条消费判据。
- 断言：`GlassMaterialTokensTest`、材质/场景布局测试与新增判据测试。

### Out of scope
- 滚动让位与 `tab_bar_clearance`（062）；实时模糊；业务代码。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只改材质数值与场景绘制；所有 SurfaceView/TextView 的层级、id、可见性判定与命中区域不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 v1 原型 `:root`；消费者 `tokens.json`/`colors.xml`/`glass.xml`/`tokens.css`/`GlassMaterialTokensTest`/`PaperRuleBackdrop`/Web `glass.css`。材质副本消费者是 10 个 `bg_glass_*.xml` 与 `layout`/`layout-sw600dp` 两套 `screen_tab_bar.xml`。
- Native bridge or independent-client compatibility/update requirements: 不涉及原生桥或夹具。
- Behavior-neutral non-applicability reason, if applicable: 不适用——绘制成本与对比度变化需在真机取证。

## Documentation impact record

- Changed runtime facts: 玻璃材质由「平色 + 顶 sheen + 均匀描边」变为「对角 tint + 两档投影」，底栏与侧栏补上原本只有其它面才有的顶部受光层；雾面场景数值加深（颗粒未做，理由见 AI-autonomous decisions ②）。
- Authoritative code/config/migration evidence: 原型 `:root`、`tokens.json`、`glass.xml`、`colors.xml`、`tokens.css`、`drawable/bg_glass_*.xml`、`PaperRuleBackdrop`、`frontend/src/styles/glass.css`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、提案目录 README、`.codex/context/source-of-truth.md`、`.codex/rules/ux-design.md`、`android/README.md`。
- Documentation files updated: 设计系统 README 的 token 变更记录；`android/README.md` 的材质与场景条目。
- Producer/consumer/sibling/compatibility paths checked: `check_design_tokens.py` 的 token 集合相等判定、生成物逐字节校验、`GlassMaterialTokensTest` 计数。
- Superseded phrases reverse-searched: 「0 12px 32px rgba(22,36,28,.07)」「三处柔光」「无投影」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 追加记录，不改写 2026-10-21/22 既有拍板。

## Acceptance criteria

- [x] `check_glass_depth.py --selftest` 正反用例全绿，退出码 0。（cases=9 failures=0）
- [x] J1 玻璃—背景亮度差 ≥ 10 L；J2 投影暗带 ≥ 6 L；J3 留区跨度 ≥ 24 L 且彩度 ≥ 6（真机实测读数）。（19.6 / 12.4 / 26.2 / 13.0）
- [x] `check_design_tokens.py` 与 `gen_design_tokens_css.py --check` 退出码 0（四方一致，无手改生成物）。
- [x] `GlassMaterialTokensTest` 覆盖新 token 与 falloff 推导；定向命令 RED→GREEN。（RED 的实际留档程度见 TDD 记录的 Unit-level RED 条）
- [x] `:app:testDebugUnitTest` 全绿；`verifyNoKotlinDex`/`verifyDependencyDirection` 绿；Web 回归绿；core 守门无红。（893 例 0 失败、no-kotlin PASS、`pnpm --dir frontend verify` 16/16；core 守门整跑 `RUN-ALL-GUARDS pass=39 fail=0 inconclusive=0 fault=0 timeout=0`、`EXITCODE=0`，档位为 core 子集，整表绿不由此声明。062 之后同一套件为 897 例 0 失败）
- [x] 阅读面仍 86%；未新增依赖；未自动提交或推送。

## TDD execution record

### RED — failing test first
- Test/scenario: 判据先行——`check_glass_depth.py` 对**改动前**的真机首页截图给出 J1–J3 读数；仪器本身先自证（合成图正反用例），再对实拍出判决。
- Command: `python -X utf8 .codex/scripts/dev/check_glass_depth.py --selftest`；`python -X utf8 .codex/scripts/dev/check_glass_depth.py --shot <首页底部胶囊帧> --band bottom`（同一命令、同一取区规则与阈值，只换被测帧；RED 用 `061-baseline-home.png`，GREEN 用 `061-after-home.png`，文件名分别记在 Transcript 与 Passing evidence）
- Exit code: 1
- Transcript: `artifacts/evidence/android-glass-depth-2026-10-22/061-baseline-before.txt`——`SELFTEST cases=9 failures=0` / `box=78,2385..1121,2586 scene_pixels=14400` / `FAIL J1 = 9.8 L`、`FAIL J2 = 0.2 L`、`FAIL J3 跨度 = 7.7 L`、`FAIL J3 彩度 = 3.0`。
- Observed failure: 四条判据全部未成立（退出码 1 = 判据不成立，不是仪器故障 3；同批 `--selftest` 单独跑为 0）；投影暗带实测 0.2 L，即 Android 侧「没有投影」这一断言被像素证实。
- Failure reason validated: 红在材质与场景数值，不在仪器——第一版仪器的 J2 参照窗口在底部锚定胶囊处出框，把「看不到」报成「找不到可测的带」（退出码 1 的假红）。已改为与暗带逐行对齐的左右邻位参照，并留下 `near-edge.png` 正例守住这条回归；现在 0.2 L 是真读数。
- Unit-level RED: 判据 RED 覆盖了本片的全部四条主张，逐例断言没有单独留档，如实记录如下——`theTintEndFollowsEachSurfaceAlphaInsteadOfOneSharedValue` 与 `everyGlassFaceFalloffsOnTheProposalDiagonal` 与 token 副本同批写入，写入时 `glass_*_lo`/`angle="315"` 均不存在，跑一次即红（红在缺资源，非缺断言）；`theChromeFacesCarryTheTopSheenUnderTheirTint` 的 RED 有像素旁证：补 sheen 之前同一台真机、同一取区几何读到的 J1 = 9.5 L（差 0.5 未达标），补后 19.6 L——这一次中间读数发生在同片迭代内，未单独存档，只记数不谎称有 transcript。

### GREEN — minimum implementation
- Implementation: token 链（原型 `:root` → `tokens.json` → `colors.xml`/`glass.xml` → 生成 `tokens.css`）新增阴影两档与 `glass-tint-falloff`、加深 mist 四色与 glow 五色 alpha；10 个 `bg_glass_*.xml` 的 `<solid>` 改为 315° 对角 tint（暗端 = 该面 alpha × falloff，落 `glass_*_lo` 副本）；`GlassSurfaceLayout` 用自有 outline（`setAlpha(1f)`）+ `setOutlineSpotShadowColor`/`setOutlineAmbientShadowColor` 投影，父链 `clipChildren=false`；`bg_glass_bar.xml`/`bg_glass_panel.xml` 补 `glass_sheen` 受光层。
- Command: `python -X utf8 .codex/scripts/dev/check_glass_depth.py --selftest`；`python -X utf8 .codex/scripts/dev/check_glass_depth.py --shot <首页底部胶囊帧> --band bottom`（同一命令、同一取区规则与阈值，只换被测帧；RED 用 `061-baseline-home.png`，GREEN 用 `061-after-home.png`，文件名分别记在 Transcript 与 Passing evidence）
- Exit code: 0
- Passing evidence: `artifacts/evidence/android-glass-depth-2026-10-22/061-after-reading.txt`——`box=78,2385..1121,2586`、J1 19.6 L（基线 9.8）、J2 12.4 L（基线 0.2）、J3 跨度 26.2 L（基线 7.7）、J3 彩度 13.0（基线 3.0），截图 `061-after-home.png`。取区几何与基线逐位相同（`78,2385..1121,2586`）——基线是旧算法（跳变配对）读的，改动后是新算法（同行留白分离）读的，两套规则在同一块胶囊上给出同一个框，所以前后读数可比；四条判据的变化只能来自材质与场景本身。其余退出码：selftest 0（cases=9 failures=0）、`check_design_tokens` 0（OK=10 FAIL=0，裸色值 53 文件 0 命中）、`gen --check` 0（逐字节一致，72 token）、`no-kotlin-check.ps1` 0（PASS，13 OK）、`verifyDependencyDirection` PASS。
- Regression evidence: `:app:testDebugUnitTest` 893 例 0 失败 0 跳过（`GlassSurfaceLayoutTest` 12 例含新增 `theChromeFacesCarryTheTopSheenUnderTheirTint`/`everyGlassFaceFalloffsOnTheProposalDiagonal`/`theFloatingCapsuleProjectsThroughItsOwnOutline`，`GlassMaterialTokensTest` 4 例含 `theTintEndFollowsEachSurfaceAlphaInsteadOfOneSharedValue`）；Java-only 守门 PASS；未跑 instrumentation（会清真机数据），界面证据是 `install -r` 后的实拍。

### REFACTOR — optional
- Cleanup: 无独立重构轮——四个 `_lo` 暗端从第一次落笔就是「该面 alpha × `glass-tint-falloff`」的推导副本，没有「先写字面量再收敛」的中间态需要清理；仪器两次改动是修失明不是重构，逐条记在 Implementation notes。
- Regression evidence: 同一 JVM 套件保持全绿（893 例 0 失败 0 跳过），四方守门与 `gen_design_tokens_css.py --check` 逐字节校验仍为 0。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 取区规则写在 `check_glass_depth.py` 的模块 docstring 与 `_bright_runs`：悬浮面 = 一行中部比**同一行**左右留白亮 ≥3 的连续行段（≥8 行），按锚点取最近的一块；纸纹线每 104 行横穿整幅会把留白抬到和面一样亮，容 0.3% 高的空隙。全宽贴边面（Web 顶栏）所在行没有留白可取，退到上下缘跳变（下缘跨 0.6% 高，因为投影是软坡；收口沿连续行走到底，否则会把面的最底几行当成下缘）。不用绝对亮度找面：加深后的雾顶本身就亮，中部诱饵卡与贴边全宽顶栏两条负例在守。
- 仪器改过两次都是**为了看见**，不是为了放行：第一版 J2 参照窗口在底部锚定胶囊处出框（`near-edge.png` 正例守这条回归）；第二版加了真投影之后，软坡让逐行跳变读不出下缘，整块面被读成「贴到画面底」，退出码 3。改完取区后几何读数与基线逐位相同（`78,2385..1121,2586`），说明新算法没有顺手把面挪大。
- Android 投影只走 elevation：`GlassSurfaceLayout` 自给 round rect outline 并 `outline.setAlpha(1f)`，让阴影 alpha 与半透明填充解耦（填充一透，系统阴影就跟着变淡），再用 `setOutlineSpotShadowColor`/`setOutlineAmbientShadowColor` 取两档 token 色；父链要两级 `clipChildren=false`——胶囊的直接父级放开只够它画出自己的框，`main_root` 仍会把阴影裁回框内。
- 对角 tint 用 `shape` 的 `angle=315`，暗端 = 该面 alpha × `glass-tint-falloff`，四个 `_lo` 副本由测试重算，不写字面量。
- 底栏与侧栏补 `glass_sheen` 受光层：这是全 app 唯一两块缺光源的面，补齐后 J1 从 9.5 → 19.6 L；卡片/阅读面/输入区原本就有这一层，Web 侧 `.workspace-topbar`/`.app-sidebar`/`.branch-panel` 同样有，端差归零。
- 颗粒层未做：真机场景没有观察到 banding，加一层没有病可治的 alpha 就是造死数值；`--mist-grain` 因此没有进入 token 链。

## Verification report

- Verdict: 本片范围内成立（真机四条判据全绿、token 四方一致、JVM 893 例全绿、Java-only 与依赖方向守门 PASS、Web verify 16/16）；core 守门整跑结果见下条，跑完即可把状态翻 verified。
- Evidence: `artifacts/evidence/android-glass-depth-2026-10-22/`（`061-baseline-before.txt` + `061-baseline-home.png` 为改动前读数，`061-after-reading.txt` + `061-after-home.png` 为改动后读数）；命令与退出码逐条记在 GREEN。core 守门读数暂存 `artifacts/tmp/core-guards-061.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 取区算法整体换掉（跳变配对 → 同行留白亮度分离），旧的「第一条亮边」配对没有留下断言——它的失败模式由 `check_glass_depth.py --selftest` 的合成图正/负例覆盖（中部诱饵卡、贴边全宽顶栏、近边缘胶囊 `near-edge.png`），不是靠人眼复核。token 计数断言从 12 项增到 14 项，覆盖阴影两档与 falloff 推导，`_lo` 副本由测试重算而非写死。
- Gaps: ① 实时折射/背景模糊仍不做（用户 2026-10-22 明确「先不做」C 档），Android 玻璃下的纸纹线是清晰的，不是模糊的——这是端差登记项，不是本片缺陷；② 只有底部锚定的胶囊走了 outline 投影，其它玻璃面（卡片/面板）没有投影，是否统一给档留给 060 的后续裁决；③ Web 侧 J1–J4 读数尚未跑（属片 063），本片只保证同一脚本同一阈值能读两端。
- Risks: 加深后的雾面让正文对比余量变宽（背景更暗、墨色不变），但阅读面 alpha 固定 86%，未随通透度下调；`--glass-shadow` 在 Web 消费改完前仍是活引用，任何提前删除都会打断 `check_design_tokens.py` 的集合相等判定。

## Handoff notes

- Completed: 判据脚本（含两次「为了看见」的取区修正）、token 链四档改动、10 个玻璃 drawable 的对角 tint、悬浮胶囊的 outline 投影、底栏与侧栏的受光层、真机 J1–J3 全绿读数。
- In progress: core 守门整跑（串行）；跑完把 status 翻 verified 并补一次 `metrics_report.py` 重生成。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 062 让内容滚到玻璃下方（J4 需要滚动前后两张截图）；063 让 Web 消费同一批 token、删 `--glass-shadow`、顶栏改覆盖层，并用同一脚本同一阈值出两端的判决。
- Watch-outs: ① 投影要两级 `clipChildren=false`（胶囊父级 + `main_root`），少一级就看不见；② 062 改完让位后，胶囊下方会有内容，取区几何与 J2 参照都会变，必须重跑一次前后对照而不是沿用本片读数；③ 不要在 063 之前删 `--glass-shadow`；④ 真机只看界面用 `adb install -r`，跑全量 instrumentation 会清掉本地数据与 API Key。
