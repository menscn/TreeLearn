# Treelearn Harness Task: 热力图自绘 View

id: T-2026-09-23-101
title: 热力图自绘 View
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-home-drawer-stats-heatmap-099.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-09-23-stats-daily-aggregation-100.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首页抽屉与统计热力图吸收；本任务交付热力格子自绘 View，供热力条（102）与详情页（104）复用。
- Problem/background: 需要按活跃强度着色的格子网格（GitHub 风格热力图），XML/View 体系无现成组件。
- Users/scenarios: 首页迷你热力条（最近 14 天单行）与抽屉/详情页全量热力图（近若干周网格）复用同一 View。
- Expected outcome: 自绘 View 支持行/列数、格子尺寸、强度色阶（生长绿 token）、空态占位；自定义属性可配置。
- Core flow and reversibility: 纯展示组件，无状态。
- Inputs/outputs: 输入按天聚合结果（100 的输出）；输出渲染。
- State and exception handling: 空数据渲染全部空格子，不崩溃不误导。
- Constraints and trade-offs: Java-only、自绘 onDraw；颜色必须走 design token（`tokens.json` / `colors.xml`），守门 `check_design_tokens.py` 必过。
- AI recommendation and rationale: 一个 View 双场景复用，通过属性区分单行条与网格；避免两份实现漂移。
- User choice and custom additions: 无（视觉以设计稿为准）。
- AI-autonomous decisions: 强度色阶默认 5 档（空 + 4 级绿）。
- User-confirmation decisions: 无。
- Acceptance evidence: RED/GREEN（测量、色阶映射、空态）+ token 检查通过。
- Facts: token 守门脚本 `python .trae/scripts/dev/check_design_tokens.py`。
- Assumptions: 100 的聚合结果结构稳定。
- Unknowns: 设计稿最终色阶与格子尺寸。
- Risks: 硬编码 hex 绕过 token 守门。
- Deferred: 动画（格子点亮动效）。
- Non-goals: 触摸交互（点击查看某天详情）。
- Skipped non-critical questions and reason: 可访问性描述文本沿用 contentDescription 由容器承担。

## Goal

交付可复用的热力图自绘 View（单行热力条与网格热力图两种形态，design token 色阶）。

## Scope

### In scope

- 自绘 View 类、自定义属性、单元/截图测试、token 对齐。

### Out of scope

- 数据源（100）；首页/抽屉/详情页装配（102/103/104）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增组件，无旧行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费者为 102/104 的布局。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新增 UI 组件。
- Authoritative code/config/migration evidence: 新增 View 代码与属性定义。
- Documentation surfaces checked: tokens.json、UX 设计目录。
- Documentation files updated: 无。
- Producer/consumer/sibling/compatibility paths checked: sw600dp 核对。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 组件级新增，无对外契约。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] RED/GREEN：行/列测量、强度到色阶映射、空数据渲染。
- [ ] 颜色全部来自 token 资源，`check_design_tokens.py` 通过。
- [ ] Java-only guard + 测试命令退出码 0；生产代码 ≤200 行。

## TDD execution record

### RED — failing test first
- Test/scenario: 新增 `android/app/src/test/java/com/treelearn/app/ui/HeatmapLayoutTest.java`（16 用例：强度→色阶 5 档映射含 0/负/NaN/越界钳制、null/空数组/短数据→全部空格子、strip 恒单行与 grid 行列不下塌、strip 14 天与 grid 7 行测量含 gap、格子按列优先坐标、结构断言「View 只用 R.color token 无 hex 字面量 + attrs.xml 声明 5 个自定义属性」）。
- Command: `cd android && ./gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.ui.HeatmapLayoutTest`
- Exit code: 1
- Observed failure: 见 `artifacts/evidence/.t101-red.log`
  ```
  HeatmapLayoutTest.java: 找不到符号 HeatmapLayout / HeatmapView（37 个错误）
  BUILD FAILED in 1s
  ```
- Failure reason validated: 37 个错误全部为被测类缺失导致的符号解析失败，无其他编译错误，确认失败由缺失行为导致。

### GREEN — minimum implementation
- Implementation: 新增纯 Java `android/app/src/main/java/com/treelearn/app/ui/HeatmapLayout.java`（无 Android 依赖，JVM 可测：`levelFor` 强度 0..1 → 0..4 五档、`levelsFor` 数据→逐格档位含空态填充、strip/grid 行列解析、列优先 `cellX/cellY` 与含 gap 的 `contentWidth/contentHeight`）+ 自绘 `android/app/src/main/java/com/treelearn/app/ui/HeatmapView.java`（`onMeasure`/`onDraw` 只做绘制，色阶颜色全部 `R.color` token：tl_line 空档 + tl_green_faint/tl_green_soft/tl_green/tl_green_deep 四级绿；`setIntensities(float[])` 入口，null/空渲染全部空格子不崩溃）+ `android/app/src/main/res/values/attrs.xml`（`HeatmapView` styleable：tl_mode strip/grid、tl_rows、tl_columns、tl_cellSize、tl_gap、tl_emptyColor）。实现期修正：删除未读的 `mode` 字段引用残留一次编译错误后转绿。
- Command: `cd android && ./gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.ui.HeatmapLayoutTest`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`；XML 报告 `tests="16" skipped="0" failures="0" errors="0"`（来源 `artifacts/evidence/.t101-green.log` 与 `android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.HeatmapLayoutTest.xml`）。
- Regression evidence: `cd android && ./gradlew.bat :app:testDebugUnitTest`（受影响面 :app 全量）→ Exit code: 0，`BUILD SUCCESSFUL`（来源 `artifacts/evidence/.t101-regression-app.log`）。全模块 `testDebugUnitTest` → Exit code: 1，唯一失败为 `:ai` 模块 `AiGatewayModelListTest` 6 用例（92 completed / 6 failed，来源 `artifacts/evidence/.t101-regression.log`）——该失败来自并行任务 085（ai-gateway-provider-abstraction）在制 RED 状态（`git status` 显示 AiGateway/ProviderJson 为 085 未提交改动，期间编译错误随其推进自愈），与本任务（仅触 `android/app/**` 新增文件）零关联；`:app`/`:data`/`:core`/`:tree-layout` 全部通过。

### REFACTOR — optional
- Cleanup: 无（实现即最小形态；新增生产文件非空非注释行数 HeatmapLayout.java 56 + HeatmapView.java 85 + attrs.xml 14 = 155，来源 `python -X utf8 .trae/scripts/dev/count_production_lines.py` 输出，≤200）。
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 强度色阶映射逻辑放纯 Java 可测方法（输入强度 0..1 输出 token 色），onDraw 只做绘制。

## Verification report

- Verdict: PASS（verifier 2026-09-23 独立验收：独立复跑 `:app:testDebugUnitTest` EXIT 0（16 用例 XML failures=0）、lint EXIT 0、no-kotlin 全 OK、check_design_tokens.py EXIT 0（42 tokens ↔ 15 resources 一致）、行数独立复核 155 ≤200；证据日志逐一核对与自述一致；颜色全走 R.color token 无 hex 字面量；strip/grid 复用同一 View 无第二实现）
- Evidence: ① RED `:app:testDebugUnitTest --tests *HeatmapLayoutTest` Exit 1（37 个缺失符号错误，`.t101-red.log`）→ GREEN Exit 0，16/16 用例通过（`.t101-green.log` + 测试 XML）；② `:app:testDebugUnitTest` 全量 Exit 0（`.t101-regression-app.log`）；③ `./gradlew.bat lint` Exit 0（`.t101-lint.log`）；④ `no-kotlin-check.ps1` VERDICT: PASS / Exit code 0（`.t101-nokotlin.log`、`.t101-nokotlin-code.txt`）；⑤ `python .trae/scripts/dev/check_design_tokens.py` Exit 0（三方一致 42 tokens ↔ 15 resources）；⑥ 生产代码 155 行（非空非注释）≤200；⑦ 全模块回归中 `:ai` 的 6 个失败为并行任务 085 在制 RED（git 证据见 GREEN 节），非本任务引入。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删改既有断言；纯新增组件。测试覆盖色阶映射边界（0/负/NaN/1/越界）、空数据三态（null/空数组/短数据补空）、两种形态测量与列优先坐标、View 只用 token 色的结构断言。
- Gaps: onDraw 实际像素绘制无 Robolectric/设备截图证据（仓库无 Robolectric 基建、app 测试为纯 JVM JUnit，按 TreeCanvas 先例把算术抽到 `HeatmapLayout` JVM 可测 + 结构断言兜底；真实渲染证据留设备轮次，与 102/104 装配任务一并目验）。
- Risks: 色阶阈值（强度→档位的均匀分桶）为 AI 默认决策，设计稿定稿后若调整只改 `levelFor` 一处；空档色默认 `tl_line`，可经 `tl_emptyColor` 属性覆盖，无需改码。onDraw 像素级目验与 102/104 装配任务一并留设备轮次。

## Handoff notes

- Completed: 实现 + verifier 独立验收 PASS；**status: verified（2026-09-23）**。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 102（首页迷你热力条）开工，装配 `HeatmapView`（strip 形态）+ `StatsAggregator.aggregate`；随后 103 → 104 → 105。
- Watch-outs: 色阶阈值调整只改 `levelFor` 一处并同步测试；102 与 103 同触首页 Presenter/ViewState 面须串行。

- Completed: 任务创建；RED→GREEN 完成（16 用例全过）；:app 回归 Exit 0；lint Exit 0；no-kotlin-check VERDICT PASS（Exit 0）；check_design_tokens Exit 0；生产代码 155 行 ≤200；证据已回填。
- In progress: 无（android-dev 侧完成，status 保持 in_progress 待 verifier）。
- Blocker: 无（全模块回归中 `:ai` 6 个失败属并行任务 085 在制 RED，不影响本任务受影响面证据）。
- Unblock condition: 不适用。
- Next steps: verifier 复核；随后 102（热力条装配，XML 用 `tl_mode="strip"` + `setIntensities`）→ 103/104。
- Watch-outs: 色阶与空档色为 AI 默认口径，设计稿定稿后只改 `HeatmapLayout.levelFor` / `tl_emptyColor`；触摸热区由容器承担（本 View 不处理点击）；102/104 装配时的真实渲染目验留设备轮次。
