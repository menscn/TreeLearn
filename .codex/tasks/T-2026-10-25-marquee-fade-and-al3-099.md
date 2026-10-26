# Filename: T-2026-10-25-marquee-fade-and-al3-099.md
# Codex Harness Task: 推荐区保留双行跑马灯、羽化带改 26dp，摘要硬截登记为已由卡片版式兜住（AH2 + AL3）

id: T-2026-10-25-099
title: 推荐区跑马灯保留与 26dp 羽化（AH2）／摘要硬截（AL3）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 12
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 AH2（重要）——推荐区右列裁成半张卡、文字断成半句；AL3（优化）——卡片摘要按字符硬截出现「符…」。
- Problem/background: AH2 的初稿把双行跑马灯换成翻页 + 圆点，用户明确否掉：「我还是需要还原成以前那样，滚动的，不要修改这部分。」因此本片**不改动跑马灯结构**，只把两侧羽化带从 20dp 抬到评审板的 26px，作为「右列被裁」的可见边界提示。AL3 的「符…」在产物里没有对应的字符串截断代码——它是评审板截图自身的文本裁切；卡片侧由 101 的两行 clamp 承担，长摘要以省略号收束而不是在任意字符处断开。
- Users/scenarios: 在首页横向浏览推荐问题的人；他们的视线需要知道卡片是「滑出去的」，而不是被页面边缘硬切。
- Expected outcome: 跑马灯仍是两行、连续滚动；左右羽化带 26dp；不再出现翻页控件或圆点。
- Core flow and reversibility: 只改 `dimens.xml` 的 `rec_fade_width`（20dp → 26dp）与一处测试归属注释。回退＝把该 dimen 改回 20dp。
- Inputs/outputs: 输入是评审板 AH2 与用户否决；输出是 26dp 的 `rec_fade_width`，`EdgeFadeLayout` 继续只读该 token。
- State and exception handling: 宽度不足 `edge * 2` 时 `EdgeFadeLayout` 退回无羽化（既有分支，未改）；两行内容与滚动由 `RecommendationMarquee` 既有逻辑承担。
- Constraints and trade-offs: 26dp 比原 20dp 多遮 12dp 的卡片面积；换来「卡还在继续」的明确信号，同时不新增控件。
- AI recommendation and rationale: 用户已经否决分页；再把羽化做成过宽的遮罩会削弱「可滑」的暗示。26dp 是板上量出的值，且不与 gutter 别名共用，避免改 gutter 时连带移动羽化。
- User-confirmation decisions: 22 条全做；推荐区还原成旧的滚动形态、不要改这部分。
- AI-autonomous decisions: ① 不新增翻页控件与指示点；② 羽化宽度保持独立 token，不别名到 `page_gutter`；③ AL3 不在数据层做字符截断，交由 101 的两行 clamp。
- Acceptance evidence: `HomeRecommendationStripTest.theFadeWidthIsAWidthAndNotASpacingToken`，先红后绿。
- Facts: `dimens.xml:216`（`rec_fade_width 26dp`）；`EdgeFadeLayout.java:71`（读 token）；`HomeRecommendationStripTest.java:221`；AL3 的落点见 `item_recommendation.xml` 的 `rec_text maxLines=2`（101）。
- Assumptions: 评审板的 26px 与 Android 的 26dp 在同一视觉档；项目一直以 dp 对应板的 px（同宽 369dp 设备）。
- Unknowns: 无。
- Risks: 真机上两侧各遮 26dp 后，首尾卡片可读面积减少；若用户觉得过度，回退只需改一个 dimen。
- Deferred: AL3 的「数据层摘要长度」不做——当前产物没有字符串截断代码，加长度规则会替数据面做决定。
- Non-goals: 不改跑马灯的行数、速度、暂停逻辑与卡片内容；不引入翻页／圆点；不改 `RecommendationMarquee`。
- Skipped non-critical questions and reason: 未再问「要不要翻页」——用户已明确否决，重复询问是不尊重既有拍板。

## Goal

保留双行滚动跑马灯，把两侧羽化带的宽度改为评审板的 26dp，并把摘要硬截登记为不存在的产物行为。

## Scope

### In scope
- `android/app/src/main/res/values/dimens.xml` 的 `rec_fade_width`。
- `HomeRecommendationStripTest` 的羽化判据与归属注释（26dp 属于本片，不属于 101）。

### Out of scope
- `RecommendationMarquee` 结构、翻页／圆点、卡片文案与摘要数据。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 羽化带宽 20dp → 26dp。必须保留：两行连续滚动、暂停与恢复、点击进主题、退化宽度（`width <= edge * 2`）时不画遮罩、`DST_IN` 合成语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `dimens.xml:216`；唯一消费者 `EdgeFadeLayout.java:71`；`RecommendationMarquee` 与卡片布局不含该值。Pad 面复用同一 layout，宽度自适应。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 推荐区羽化带从 20dp 变为 26dp；跑马灯形态按用户否决保持不变。
- Authoritative code/config/migration evidence: `dimens.xml:216`；`EdgeFadeLayout.java:71`；`HomeRecommendationStripTest.java:221-238`。
- Documentation surfaces checked: `.codex/rules/android.md`、`.codex/rules/ux-design.md`（尺寸 token 只此一处）。
- Documentation files updated: 无需改文档；宽度理由写在测试注释里。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "rec_fade_width" android/app/src` 只命中 dimen、`EdgeFadeLayout` 与判据。
- Superseded phrases/examples reverse-searched: 旧测试注释把 26dp 归给 101，已改为 099（26dp 属本片）。
- `documentation impact: none` reason and evidence: 单 token 数值调整，无契约变化。
- Historical/PRD handling: 不改写 2026-10-22 的跑马灯实现记录；本片保留其结构。

## Acceptance criteria

- [x] `rec_fade_width` 为 26dp，且 `EdgeFadeLayout` 只读该 token、不内联数字、不读 gutter。
- [x] 跑马灯仍为双行滚动；没有翻页控件或圆点。
- [x] 退化宽度下仍不画遮罩（既有判据保持绿）。
- [x] AL3 在产物中无可截断代码，卡片摘要由 101 的两行 clamp 收束，本片不改数据。

## TDD execution record

### RED — failing test first
- Test/scenario: `theFadeWidthIsAWidthAndNotASpacingToken`——dimen 必须是 26dp、布局只读 token、不内联 26、不读 gutter。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.HomeRecommendationStripTest.theFadeWidthIsAWidthAndNotASpacingToken"`
- Exit code: 1
- Observed failure: `HomeRecommendationStripTest > theFadeWidthIsAWidthAndNotASpacingToken FAILED`，`java.lang.AssertionError at HomeRecommendationStripTest.java:225`（`099-red.txt`）。
- Failure reason validated: 把 `rec_fade_width` 临时改回 20dp 后同一条命令判红，红点正是第一条断言「the fade width is its own token」；恢复 26dp 即绿，说明该值只由这一个 token 决定，没有第二处内联。

### GREEN — minimum implementation
- Implementation: `dimens.xml` 的 `rec_fade_width` 改为 26dp；测试注释的归属改为 099。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.HomeRecommendationStripTest.theFadeWidthIsAWidthAndNotASpacingToken"`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`、`EXITCODE=0`（`099-green.txt`）。
- Regression evidence: Android 全量 109 套 / 954 用例绿（`096-full-suite.txt` 同批）；`HomeRecommendationStripTest` 的其余九条（DST_IN、退化宽度、两行、暂停）保持绿。

### REFACTOR — optional
- Cleanup: 无。
- Regression evidence: 不适用。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿。
- Failure identity and repeat count: 单条判据单次红。
- Stop condition hit: dimen 与判据同时绿。
- Handoff: 105 的整跑包含本片。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- AH2 的正确处置是「不做的决定也要有记录」：用户已经否决翻页，本片把这条否决写进卡片，并在代码里保持跑马灯原样，而不是悄悄留下一个「以后可换」的分支。
- 羽化带做成独立 token 的理由在判据里：它与 gutter 数值相同纯属巧合，别名会让改排版时连带移动遮罩。
- AL3 不是布局问题，也不是数据问题，而是评审板截图里的文本裁切；产物没有 `substring` 截断，卡片已由两行 clamp 收束。把它硬造成「改数据」会凭空引入一条长度规则。

## Verification report

- Verdict: PASS
- Evidence: `099-red.txt`（FAILED / EXITCODE=1）、`099-green.txt`（BUILD SUCCESSFUL / EXITCODE=0）；Android 全量绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除；只改归属注释与一个数值。
- Gaps: 未做真机截图对比 26dp 的实际遮挡面积。
- Risks: 遮挡偏多在真机上才会显现；回退成本是一个 dimen。

## Handoff notes

- Completed: 26dp 羽化；AH2 的否决入档；AL3 的落点说明。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 无。
- Watch-outs: 不要以「可滑暗示不足」为由把跑马灯换成翻页——用户已明确否决。
