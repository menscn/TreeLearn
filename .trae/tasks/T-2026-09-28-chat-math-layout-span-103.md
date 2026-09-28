# Treelearn Harness Task: 公式布局计算与自绘 Span

id: T-2026-09-28-103
title: 公式布局计算与自绘 Span
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-28-chat-math-formula-rendering-100.md
requirements_confirmed: true
generated_by: leader
owner: android-math-implementer
owner_lane: android
stack: android
estimated_production_lines: 180
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
- 新增 `android/app/src/main/java/com/treelearn/app/ui/math/MathMetrics.java`（测量接口：`measureText`、`ascent`/`descent`、`textWidths`）、`PaintMetrics.java`（`Paint` 实现）、`MathLayout.java`（布局，`MathBox` 结果含 x/y/width/height/baseline）、`MathSpan.java`（`ReplacementSpan` + `LineHeightSpan.WithDensity`）。
- 布局规则：上标/下标 0.7 倍字号并上下偏移；`\frac` 分子分母居中、分数线按规则厚度与外扩；`\sqrt` 根号符随内容高度拉伸 + 上划线；`\sum \int \lim` 等大运算符放大，上下限在正上方/正下方（行内 `\sum` 可保持上下限在角标位以免行距过大——由实现选定并在单测固定）；`\left\right` 定界符随内容高度缩放；字母用数学斜体、数字与运算符直立；`\text{}` 用正文样式。
- `MathSpan.getSize` 返回布局宽高并写入 `FontMetricsInt`（含 ascent/descent），`chooseHeight` 在行高不足时补足 leading 防裁剪；`draw` 用 `TextPaint` 的颜色与密度，块级与行内用不同字号比例常量。
- JVM 单测用 `MathMetrics` 的确定性假实现断言：分式高度大于单行、上标基线高于主体、大运算符宽高关系、`\left(` 高度随内容增长。

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

- [ ] JVM 单测：`\frac{1}{2}` 布局高度 > 单行高度且分数线居中；`x^2` 上标基线高于主体基线且字号更小；`\sum_{i=1}^{n}` 的上下限在运算符正上下方且整体宽度不小于运算符宽。
- [ ] JVM 单测：`\left(\frac{a}{b}\right)` 左定界符高度随内容增长（对比 `\left(x\right)`）。
- [ ] 仪器/真机证据：含公式的 TextView 渲染后公式**未被裁切**（`chooseHeight` 生效），与同行中文基线对齐，观感截图留证于 `artifacts/`。
- [ ] 绘制不改变文本颜色/字号设置（继承 `TextPaint`，不写裸 hex px；颜色取自 `TextPaint.getColor()`）。
- [ ] 无新依赖；Java-only guard PASS；既有答案渲染测试未受影响（本任务未接线）。

## TDD execution record

### RED — failing test first
- PENDING
### GREEN — minimum implementation
- PENDING
### REFACTOR — optional
- PENDING
### TDD or sizing exception — only with prior user approval
- 无例外。

## Implementation notes

- 布局与绘制分离是本任务的可测性前提：`MathLayout` 只依赖 `MathMetrics`，不碰 `Canvas`/`Paint`；`MathSpan` 只消费布局结果。
- `MathSpan` 同时实现 `LineHeightSpan.WithDensity`，否则高分公式被行高裁切（已知坑，验收截图必须核对）。
- 所有尺寸比例写成命名常量，不写裸 px；颜色不写裸 hex（沿用 `TextPaint`）。
- 绘制证据需真机或模拟器：按既有仪器测试方式跑（`connectedDebugAndroidTest` 类域隔离，AGP 不支持 `--tests`），截图存 `artifacts/`。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 无。
- In progress: 无。
- Blocker: 待 102 完成。
- Unblock condition: T-102 verified。
- Next steps: 先写 `MathLayoutTest`（JVM、假 `MathMetrics`）RED，再实现布局与 `MathSpan`，最后补仪器截图。
- Watch-outs: 不要在布局里直接用 `Paint`（JVM 单测是 stub）；行高必须处理；`\text{}` 内不斜体。
