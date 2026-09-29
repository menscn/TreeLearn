# Treelearn Harness Task: 公式布局计算与自绘 Span

id: T-2026-09-28-103
title: 公式布局计算与自绘 Span
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-28-chat-math-formula-rendering-100.md
requirements_confirmed: true
generated_by: leader
owner: android-math-implementer
owner_lane: android
stack: android
estimated_production_lines: 369
shared_code_touched: false
depends_on: [T-2026-09-28-chat-math-parse-node-102.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 T-2026-09-28-100；本任务把 102 的节点树变成可测量的布局，并用 `ReplacementSpan` 画出来。用户拍板：画不出的降级为等宽底色源码（降级发生在 104，本任务只负责「算得出、画得出」）。
- Expected outcome: 布局尺寸可在纯 JVM 单测断言；绘制在真 TextView 中不裁切、基线与正文对齐、上下标与分式观感正确。
- Constraints and trade-offs: 不引入依赖、不引入 WebView；布局与绘制分离以便可测；`android.graphics.Paint` 在 JVM 单测中是 stub，故测量必须走可替换的 `MathMetrics` 接口。
- Facts: 答案正文 `TextView` 为 `match_parent`、字体走 `--sans`；`ReplacementSpan` 高度超过行高时会被裁，需同时实现行高处理。
- Risks: 行高不足裁切；分式/根号纵向溢出；与行内中文基线不齐。
- Deferred: 矩阵等多行结构；公式内自动换行（过宽由 104 降级或整体缩放兜底）。

## Goal

实现 `MathLayout`（节点树 → 带坐标与基线的布局树，纯 JVM 可测）与 `MathSpan`（`ReplacementSpan`，按布局树绘制并处理行高），使公式在答案 TextView 中正确显示。

## Scope

### In scope
- 新增 `android/app/src/main/java/com/treelearn/app/ui/math/MathMetrics.java`（测量接口：`measureText`、`ascent`/`descent`）、`PaintMetrics.java`（`TextPaint` 实现）、`MathLayout.java`（布局，`MathBox` 结果含 width/ascent/descent + 扁平化 `Glyph`/`HLine` 列表）、`MathSpan.java`（`ReplacementSpan` + `LineHeightSpan.WithDensity`）。
- 布局规则：上标/下标 0.7 倍字号并上下偏移；`\frac` 分子分母居中、分数线按规则厚度与外扩；`\sqrt` 根号符随内容高度拉伸 + 上划线；`\sum \int \lim` 等大运算符放大，上下限在正上方/正下方；`\left\right` 定界符随内容高度缩放；字母用数学斜体、数字与运算符直立；`\text{}` 用正文样式。
- `MathSpan.getSize` 返回布局宽度并在 `FontMetricsInt` 中写 ascent/descent；`chooseHeight` 在行高不足时补足 leading 防裁剪；`draw` 用 `TextPaint` 的颜色与密度，块级与行内用不同字号比例常量；颜色取自 `paint.getColor()`，不写裸 hex。
- JVM 单测用 `MathMetrics` 的确定性假实现断言：分式高度大于单行、上标基线高于主体、大运算符宽高关系、`\left(` 高度随内容增长、`	ext{}` 直立。

### Out of scope
- 接入渲染与降级（104）；解析（102）；扫描（101）；横向滚动或自动换行。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增未接线类；不改动既有答案渲染。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者为 102 的 `MathNode`；本期无消费者（104 起 `MarkdownRenderer` 消费）；平台分支：tablet landscape 与低动效/字号缩放需沿用（不因公式破坏既有布局）。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 未接线，行为中性；JVM 单测 + 仪器测试证明。

## Documentation impact record

- Changed runtime facts: 无（未接线）。
- Authoritative code/config/migration evidence: 新增 `MathMetrics.java` / `PaintMetrics.java` / `MathLayout.java` / `MathSpan.java`（`android/app/src/main/java/com/treelearn/app/ui/math/`）。
- Documentation surfaces checked: `android/README.md`（无新依赖）；`Docs/04-ux-design/`（不改设计 token，不新增颜色/字号 token）。
- Documentation files updated: 无（集中在 T-104）。
- Producer/consumer/sibling/compatibility paths checked: 102 输出 `MathNode`；104 将按 `MathSpan` 回填。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence: 未接线新增类，不改运行时行为与构建面；证据为不修改 `MarkdownRenderer.java` / `ChatScreen.java` / `build.gradle` / `colors.xml`。
- Historical/PRD handling: 不涉及。

## Acceptance criteria

- [x] JVM 单测：`\frac{1}{2}` 布局高度 > 单行高度且分数线居中；`x^2` 上标基线高于主体基线且字号更小；`\sum_{i=1}^{n}` 的上下限在运算符正上下方且整体宽度不小于运算符宽。
- [x] JVM 单测：`\left(\frac{a}{b}\right)` 左定界符高度随内容增长（对比 `\left(x\right)`）。
- [ ] 仪器/真机证据：含公式的 TextView 渲染后公式**未被裁切**（`chooseHeight` 生效），与同行中文基线对齐，观感截图留证于 `artifacts/`；当前已有行高/像素证据，但尚缺公式与中文同行基线的独立断言。
- [x] 绘制不改变文本颜色/字号设置（继承 `TextPaint`，不写裸 hex px；颜色取自 `TextPaint.getColor()`）。
- [x] 无新依赖；Java-only guard PASS；既有答案渲染测试未受影响（本任务未接线）。

## TDD execution record

### RED — failing test first
- Test/scenario: MathLayoutTest superscript baseline assertion with initial superscript-baseline implementation
- Command: cd android && JAVA_HOME=/d/Environment/Jdk/jdk21 ./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.ui.math.MathLayoutTest" --no-build-cache --rerun
- Exit code: 1
- Observed failure: assertTrue(superscript baseline above base baseline) failed; superscript y was below the base baseline
- Failure reason validated: Code inspection confirmed supBaselineY used base.ascent - rise + sup.ascent, which pushed the superscript below the baseline; reproduced locally and fixed to base.ascent - rise

### GREEN — minimum implementation
- Implementation: Added MathMetrics/PaintMetrics/MathLayout/MathSpan; fixed superscript baseline and LineHeightSpan WithDensity 7-arg chooseHeight
- Command: cd android && JAVA_HOME=/d/Environment/Jdk/jdk21 ./gradlew :app:testDebugUnitTest --tests "com.treelearn.app.ui.math.MathLayoutTest" --no-build-cache --rerun
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL in 2s; TEST-com.treelearn.app.ui.math.MathLayoutTest.xml tests="5" skipped="0" failures="0" errors="0"
- Regression evidence: artifacts/evidence/T-103-connected-androidtest.log shows 2 tests on CGA-W00 finished with BUILD SUCCESSFUL; existing MarkdownRendererInstrumentedTest unchanged because MathSpan is not wired yet

### REFACTOR — optional
- No structural refactor; only corrected the superscript baseline offset and added the missing LineHeightSpan chooseHeight overload.

### TDD or sizing exception — only with prior user approval
- Exception type: 规模例外，事后追认（不是 TDD 例外）。
- Reason: 实测 369 行非空非注释生产代码，超过 200 行 169 行（84.5%），未超过 500 行硬上限；八类节点均有独立排版规则，且测量接口、布局、ReplacementSpan 和行高必须共同满足公式不裁切这一验收目标。
- User approval: 用户于 2026-09-28 表示“特例批注可以通过”，在本任务审计语境下明确批准 T-102 与 T-103 两项作带说明的规模特例；仅适用于本任务，不是事先批准，记录为流程偏差。
- Alternative verification: `count_production_lines.py` 统计 369 行；MathLayout JVM 5/5、MathSpan 真机仪器用例 3/3、Java-only guard PASS，并补充最终 APK 字节一致性与启动证据。整包仪器测试当前为 275 项中 29 项失败，需单独按既有邻接基线处理。

## Implementation notes

- 2026-09-28 用户明确授权修复独立复核指出的字号/密度适配与绘制后 Paint 状态问题；本轮只修改 T-103 的 `MathSpan` 与其定向仪器测试，不改变 T-104 接线范围。
- 布局与绘制分离是本任务的可测性前提：`MathLayout` 只依赖 `MathMetrics` 接口，不直接引用 `Canvas`/`Paint`；`MathSpan` 只消费 `MathLayout.MathBox` 结果。四个生产文件：
  - `MathMetrics.java`（6 行）：接口 `measureText`、`ascent`、`descent`。
  - `PaintMetrics.java`（23 行）：`TextPaint` 实现 `MathMetrics`。
  - `MathLayout.java`（259 行）：节点树 → `MathBox{width, ascent, descent, List<Glyph>, List<HLine>}`；`Glyph{text, x, baselineY, textSize, italic}`、`HLine{x, y, width, thickness}`。
  - `MathSpan.java`：`ReplacementSpan` + `LineHeightSpan.WithDensity`；构造时一次性算布局并缓存，运行时按宿主 `Paint` 字号缩放 glyph/line 几何，并在绘制后恢复 `Paint` 状态。
- `MathBox` 坐标从盒子顶边往下：`ascent` 为基线距顶；`descent` 为基线到底；`height()` = ascent + descent。每个 `Glyph` 存自己的 baselineY（距盒顶），每个 `HLine` 存自己的 y（距盒顶）。
- 行高处理：`MathSpan` 同时实现 `LineHeightSpan.WithDensity`。`getSize` 写 `FontMetricsInt`；`chooseHeight` 在需要时把 `fm.ascent/top/descent/bottom` 同步扩展，使公式盒子落在本行内不被裁。仪器测试 `tallFormulaIsNotClippedByLineHeight` 断言 `lineHeight >= span.geometry().height() - 1f`。
- 比例常量全部命名：`SUPER_SCALE=0.7`、`UNDER_OVER_SCALE=0.7`、`ACCENT_SCALE=0.85`、`PAD_FACTOR=0.12`、`GAP_FACTOR=0.05`、`BAR_FACTOR=0.06`、`DELIM_HEIGHT_FACTOR=0.9`、`BLOCK_SCALE=1.15`、`INLINE_SCALE=1.0`、`BASE_TEXT_SIZE=16f`。
- 斜体规则：`AtomKind.ORDINARY` 走数学斜体，其他 `NUMBER/OPERATOR/RELATION/PUNCT/TEXT` 直立；`\text{}` 因 parser 产出 `AtomKind.TEXT` 自动直立。
- 颜色与样式沿用传入 `TextPaint`：绘制用 `paint.getColor()`，不写裸 hex；所有尺寸均为命名常量倍数。
- 布局在 `MathSpan` 构造时算一次并缓存，`draw` 不重算，适配流式刷新高频场景。
- 仪器测试文件按项目约定放在 `android/app/src/androidTest/java/com/treelearn/app/MathSpanInstrumentedTest.java`（顶层包 `com.treelearn.app`，与其他 instrumented 测试一致），含 3 个测试：行高不裁切、绘制有像素、宿主字号缩放且恢复 `Paint` 状态。

## Verification report

- Verdict: PARTIAL
- Evidence: `artifacts/evidence/T-2026-09-28-t103-current-verification.txt`（当前日期口径下的 MathLayout 5/5、MathSpan 3/3、MarkdownRenderer 11/11、lint、Java-only guard、整包 275/29 邻接失败、Debug 构建及安装启动）；设备与本地产物 SHA-256 均为 `7CED59649B9CCD00EC57B127A78710D68D5E80D14E3BB0A320CD795F900E905F`。
- Gaps: 独立 verifier 已复核但结论为 Harness evidence FAIL；原始 RED 日志不可回读；公式与中文同行基线尚无独立真机断言；T-101 仍为 FAIL、T-102 为 PARTIAL，公式父任务不能整体标记 verified；整包 connected instrumentation 仍有既有失败。
- Risks: 不支持的 `\begin{}` 等语法在 T-104 降级为等宽源码；本任务的字号/密度与 Paint 状态修复已有定向证据，但未因此把 T-103 或父需求提升为 verified。

### Independent verifier addendum (latest)

- Verdict: FAIL on Harness evidence. 独立 verifier 确认定向行为证据有效，但按父需求默认 200 行约束核对时发现 T-103 实测 369 行；用户的规模特例已如实记录为事后追认，不能由 verifier 代为改判为 PASS。另有原始 RED 不可回读与同行中文基线证据缺口。
- Remaining gaps: T-101/T-102 依赖链未闭环；T-103 仍需保留 `PARTIAL`，父公式任务仍不能标记 `verified`。

## Handoff notes

- Completed: `MathLayout`、`MathSpan`、`MathMetrics`、`PaintMetrics` 及对应 JVM/仪器测试全部通过。
- In progress: 无代码工作；任务保留 `verifying`，等待 Harness 主会话依据独立 verifier 结果收口。
- Blocker: 独立 verifier 对 Harness 证据给出 FAIL；同时 T-101/T-102 依赖链、原始 RED 可回读性和中文同行基线证据仍未闭环。
- Unblock condition: 补齐可回读 RED 与同行基线证据，并由 Harness 主会话重新核对规模特例与完整验收标准。
- Next steps: 不再扩大 T-103 代码范围；保持 T-104 与父需求 `PARTIAL`，先处理 T-101/T-102 的独立缺口。
- Watch-outs: 不要在布局里直接用 `Paint`（JVM 单测是 stub）；行高必须处理；块级公式需居中独立成段。
