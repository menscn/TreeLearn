# Filename: T-2026-10-23-home-history-list-074.md
# Codex Harness Task: 首页历史主题画出整份读取结果，不再截断到三条

id: T-2026-10-23-074
title: 首页历史主题不再截断（并因此恢复整页可滑）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-scheme-a-morandi-requirement-052.md
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: android
stack: android
estimated_production_lines: 8
shared_code_touched: false
depends_on: [T-2026-10-23-glass-control-face-072.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 用户 2026-10-23 报「还发现个问题，首页不能滑动了！」，随后附一张标注截图：「这里有一块空白，什么也没有，导致无法下滑」。同一屏上还有第二个症状：区头写着「6 个」，画面里只有 3 张卡。两条其实是同一条：`HomeScreen.MAX_RECENT = 3` 把历史列表截断，区头却按 `state.topics.size()` 报全量。
- Users/scenarios: 真机首页（1200×2652）在历史主题多于三条时的下滑；底部悬浮胶囊之上那段 `tab_bar_clearance`（90dp = 293px）内边距。
- Expected outcome: 区头说的条数与画出来的卡数是同一条列表；列比视口高，整页拖得开，底部那 293px 不再是一块拖不开的空白。
- Core flow and reversibility: 只改「画几条」这一段渲染循环。撤销是把 `MAX_RECENT` 常量放回来——但那条状态下整页不可滑，所以这条「撤销」实际是一个已确认缺陷，不该作为选项保留。
- Inputs/outputs: 输入是既有的 `HomeViewState.topics`（presenters 早已给出全量并按最近使用排序）；输出是渲染循环的一处条件改动与一条结构性回归断言。不改数据层、不改 SQL、不改 DAO、不改排序、不改 presenter。
- State and accessibility/exception handling: 空态句分支（`hasTopics()` 为假）不变；卡片可选部位（摘要行、未读点）不变；点击恢复主题的路径不变。命中区域一个没动。
- Constraints and trade-offs: 「首页只画三条」曾是 2026-09-27 抽屉与首页 UX 规格里的裁决，所以本片要动一条被文档写成现状的规则。**核心矛盾**：截断列表与全量计数并存时，卡面少还是可原谅的观感问题，列高低于 `ScrollView` 自身却是功能性死锁——`ScrollView.onInterceptTouchEvent` 第 661 行 `if (getScrollY() == 0 && !canScrollVertically(1)) return false;` 会拒绝接管**任何**拖拽，整页都动不了。
- AI recommendation and rationale: 去掉截断，而不是「保留三条 + 区头也只报三条」。因为后一种修法只遮住症状，列仍然比视口矮（区头只报三条时页面依旧拖不开），而且抽屉的最近学习已经是那个固定条数的快捷面，首页再造一个上限没有第二个理由。
- User-confirmation decisions: 「首页不能滑动了」+「这里有一块空白，什么也没有，导致无法下滑」——用户要的是能下滑、空白那块有内容，两者都指向「把列表补全」。
- AI-autonomous decisions: ① 删除 `MAX_RECENT` 常量而不是保留但调大——留一个大数就是留一个第二事实；② `HomeMotion.CARD_STAGGER_STEPS_MAX = 5` 保留，它是入场等待上限不是列表上限，但它的 javadoc 原本引用了将被删除的 `MAX_RECENT`，改写理由而不改数值；③ 断言写成「源码里不得再出现 `MAX_RECENT` / `visibleCount`」的反向形式，防止以后又长出一个第二列表上限；④ 取证仪器改用两帧像素差（原方案想用 `dumpsys` 的 bounds，那条是坏仪器，见 Facts）。
- Acceptance evidence: 真机 `home_content` 从 2486 高变 3432/3656 高（> `home_root` 的 2537），一次滑动的两帧差从 3.37% 变 21.85%/42.83%；区头「7 个」对画面 7 张卡；`HomeMotionTest` 一条 RED→GREEN 全转录；全量 JVM 0 失败。
- Facts: `android/widget/ScrollView.java:661` 的拖拽闸门；`View.canScrollVertically` 走 `computeVerticalScrollRange() − computeVerticalScrollExtent()`，而 `ScrollView` 的 range override 返回 `getChildAt(0).getBottom()`、`View.computeVerticalScrollExtent()` 返回 `getHeight()` 且**不减 padding**——所以「列比视口矮」直接等价于「报不能滚」。取证侧：`dumpsys activity top` 打印的是 `View.getBounds()`，`ScrollView` 滚动时只改自己的 `mScrollY`，不改子 view 的 `mTop`，因此 bounds 永远读作 `0,0-…`，**不能**用来判滚没滚；本页不滚时的像素差基线是 3.4%（只有推荐语在动）。
- Assumptions: 全量画出不等于无限长——`topics` 来自 `LibraryData.topics()` 的既有读取，用户实际历史规模在十几条量级，卡片成本与 `fillViewport` 结构都在可接受范围。
- Unknowns: 若将来历史规模到上百条，需要的是分页/回收（RecyclerView），不是新的截断常量；本片没有这个规模的压力，不做。
- Risks: 列变长后，首页入场 stagger 的等待窗口更容易被触到上限（前 5 张动完就开始等）——这是等待策略不是正确性问题，已在 javadoc 里写清。
- Deferred: 首页是否该给「最近学习」一个「展开全部」折叠态（产品议题，不是本片缺陷）。
- Non-goals: 不改数据读取与排序、不改卡片视觉、不改 Web（Web 从来不截断）、不改抽屉的固定三条快捷面、不新增依赖、不自动提交或推送。
- Skipped non-critical questions and reason: 未追问「区头要不要改成只显示前 N 条 + 更多」——那会把同一个缺陷换成另一种截断，而且用户点名要的是「那块空白里有东西」。

## Goal

首页历史主题把 `topics` 的**全部**条目画出来，使区头计数与卡面列表是同一件事，并因此让列高超过滚动视口、恢复整页可滑。

## Scope

### In scope
- `screen.HomeScreen.renderRecent` 的循环上限由 `MAX_RECENT` 改为 `state.topics.size()`，并删除该常量。
- `HomeMotion.CARD_STAGGER_STEPS_MAX` 的 javadoc 改写（它引用的旧理由随常量一起失效）。
- `HomeMotionTest` 新增一条结构性断言（正向：迭代全量；反向：不得再出现第二列表上限）。
- `android/README.md` 与 `Docs/spec/SPEC-2026-09-27-home-drawer-ux.md` 把「历史最多三条」标注为已被取代。

### Out of scope
- 数据层与 presenter、卡片布局与视觉、抽屉的固定三条快捷面、`tab_bar_clearance` 的让位归属、Web 侧。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 首页历史区从「最多 3 张卡 + 区头报全量」变为「画全量、区头与卡数一致」；整页从「拖不开」变为「可滑到底，末条停在悬浮胶囊上方」。空态、点击恢复、可选部位规则、content description 全部保留。
- Producer/consumer/sibling entrypoints and platform branches（代码证据）：producer 是 `HomeViewState.topics`（未改）；consumer 只有 `HomeScreen.renderRecent` 这一处；sibling 是 `HomeDrawerScreen` 的最近学习（仍固定条数，本片不触及，并在规格里明确「本条只取代首页」）；平台分支无——Pad 走同一份 `screen_home_body.xml`。
- Native bridge or independent-client compatibility：无。不涉桥接、不涉 SQL、不涉数据格式。
- Behavior-neutral non-applicability reason：不适用（本片是行为改动）。

## Documentation impact record

- Changed runtime facts: 首页历史区不再截断；「最多三条」不再是现状；整页可滑的前置条件是列高超过视口。
- Authoritative code/config/migration evidence: `screen/HomeScreen.java`（`renderRecent` 的循环与已删除的常量）、`HomeMotionTest.theHomeDrawsEveryTopicTheReadReturned`。
- Documentation surfaces checked: `android/README.md`（数据流表 + 首页相关小节）、`Docs/spec/SPEC-2026-09-27-home-drawer-ux.md`、`Docs/04-ux-design/design-system/README.md`（本片不涉及 token，未写）。
- Documentation files updated: `android/README.md` 第 487 行（历史来源措辞）与第 492 行（`screen.HomeScreen` 行补上 074 的完整因果，并标片号）；`SPEC-2026-09-27-home-drawer-ux.md` 第 26 行的取代标注从「顺序部分」扩到「顺序与历史条数部分」，并加一句「本条只取代首页」。
- Producer/consumer/sibling/compatibility paths checked: 反向搜索 `MAX_RECENT`、`visibleCount`、「最多三条」、「最近三条」在文档与测试里的全部出现，逐处判定是首页事实（改）还是抽屉事实（保留）。
- Superseded phrases/examples reverse-searched: 「历史最多展示三条」在规格里的原句保留但加取代标注，不静默覆盖 2026-09-27 的裁决。
- Historical/PRD handling: 规格原文不删，只标「已被取代」并写明取代它的片号与理由。

## Acceptance criteria

- [x] 区头播报的条数与画出来的卡片数来自同一条列表（真机「7 个」对 7 张卡）。
- [x] 历史多于三条时首页可下滑到底，末条停在悬浮胶囊上缘之上（两帧像素差 21.85%/42.83%，对照不滚动基线 3.4%）。
- [x] 保留行为：空态句、卡片可选部位、点击恢复主题、content description、抽屉的固定条数快捷面（相邻回归由全量 JVM 覆盖）。
- [x] 源码里不再有第二处列表上限（断言反向锁 `MAX_RECENT` / `visibleCount`）。

## TDD execution record

### RED — failing test first
- Test/scenario: `HomeMotionTest.theHomeDrawsEveryTopicTheReadReturned`（要求 `HomeScreen.java` 含 `index < state.topics.size()`、不含 `MAX_RECENT` / `visibleCount`）
- Command: `./gradlew :app:testDebugUnitTest --tests "*HomeMotionTest*" --console=plain`
- Exit code: 1
- Transcript: `artifacts/evidence/home-history-list-2026-10-23/074-red-console.txt` → `8 tests completed, 1 failed`、`BUILD FAILED in 3s`。
- Observed failure: `HomeMotionTest > theHomeDrawsEveryTopicTheReadReturned FAILED`，`java.lang.AssertionError at HomeMotionTest.java:87`。
- Failure reason validated: 红的原因就是缺陷本身——当时工作树里 `HomeScreen.java` 被临时换回 HEAD 版本（`MAX_RECENT` 在位），三条断言逐条不满足。换回方式：`git show HEAD:…HomeScreen.java > 同路径`，跑完立即从 `artifacts/tmp/HomeScreen.java.fixed` cp 回，md5 前后一致 `e1e4afbd99d19a228659c652562227cc`。

### GREEN — minimum implementation
- Implementation: 循环上限改为 `state.topics.size()`，删除 `MAX_RECENT`，改写 `CARD_STAGGER_STEPS_MAX` 的 javadoc 理由。
- Command: `./gradlew :app:testDebugUnitTest --tests "*HomeMotionTest*" --console=plain`
- Exit code: 0
- Transcript: `artifacts/evidence/home-history-list-2026-10-23/074-green-console.txt` → `BUILD SUCCESSFUL in 8s`。
- Passing evidence: 8 例全通过。
- Regression evidence: 全量 `./gradlew :app:testDebugUnitTest` exit 0、944 例 0 失败（073 退役 2 例之后的基数）；真机 `adb install -r` Success + `074-device-reading.txt` 的 bounds 与两帧差对照。

### REFACTOR — optional
- Cleanup: 无（改动本身就是一处删除 + 一处循环条件）。
- Regression evidence: 同上。

### LOOP — optional
- 不适用。

## Implementation notes

- 仪器选型是这片最值钱的部分：bounds 不能证明滚动（`ScrollView` 只改 `mScrollY`），所以「滚没滚」只看两帧同位像素差；bounds 只用来判「列比视口高还是矮」这一件事。前一版结论就是被 bounds 误导成「页面在滚」的，写进了 `074-device-reading.txt` 第 0 节以免重犯。
- `dumpsys activity top` 在这台机器上比 `uiautomator dump` 可靠：后者在本 App 恒失败（`RecommendationMarquee` 永动动画导致拿不到 idle state）。
- 为什么列矮就拖不开，与 padding 的相互作用写进读数文件第 1 节：`computeVerticalScrollExtent()` 不减 padding，所以 2486 − 2537 < 0 直接把闸门关掉，293px 的内边距变成拖不开的空白——用户截图指的那块空白就是这个。
- 不改 `fillViewport`、不改 `paddingBottom`：让位归属仍由 `tab_bar_clearance` 负责，本片只改「画几条」。

## Verification report

- Verdict: PASS
- Evidence: 单元——`HomeMotionTest` RED(1)→GREEN(0) 全转录 + 全量 JVM 944 例 0 失败（075 之后为 946 例，见 `pinned-topbar-2026-10-23/075-suite.txt`）。真机——同一条 `input swipe` 的两帧差 3.37%/3.53%（修前，等于不滚动基线）→ 21.85%/42.83%（修后）；`home_content` 2486 → 3432 → 3656 vs `home_root` 2537；截图 `074-before-swipe-{a,b}.png` / `074-device-after-{top,bottom}.png`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除；新增一条同时含正向（迭代全量）与反向（禁止第二上限）形式的断言。
- Gaps: 只有一台手机（AWSS013807000356），Pad 宽窗没有真机滑动取证；宽窗走同一份 body 布局，结构性风险低但覆盖确实缺。
- Risks: 历史规模增长后首页会变长，真正的解法是回收列表；本片留下的反向断言会拦下「再加一个上限」这种回退，但不会提示「该分页了」。

## Handoff notes

- Completed: 渲染循环修复、常量删除、javadoc 理由改写、结构性断言、真机前后取证、README 与规格取代标注。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 若历史条数继续增长，把首页历史区改为 RecyclerView（并保留区头与列表同源这条断言）。
- Watch-outs: 任何人用 `dumpsys` 的 bounds 判「有没有滚」都会得出错误结论；判滚动只有两帧像素差这一条路。
