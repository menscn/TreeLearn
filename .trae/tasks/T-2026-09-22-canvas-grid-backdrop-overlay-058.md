# Treelearn Harness Task: 画布点阵网格背景叠加

id: T-2026-09-22-058
title: 画布点阵网格背景叠加
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-22-figma-ia-and-visual-migration-049.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-09-22-design-token-baseline-sync-050.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把 Figma 精修稿的信息架构与视觉**有机**迁移进 Android 工程，红线是「只改交互与表现，功能规格一项不动」。本批的最后一片是最小的纯装饰件：给画布加新稿的点阵网格背景，并让画布控制条用上统一的线性图标。
- Problem/background: 新稿的画布有细密点阵背景与一组缩放工具栏。工程侧 `screen/tree_body` 已有控制条（折叠 / 进入 / 适应），且画布的布局算法是**已验收的纯 Java 树布局**（`tree-layout` 模块）并有几何、标签、命中四组测试盯着。SPEC 疑虑 4 的结论已由用户拍板：**保留现有控制条，只叠加点阵网格背景**——不替换工具栏。
- Expected outcome: 画布背后出现细密点阵网格；控制条动作位换成与 056/057 同一约定的线性图标；**画布的布局行为、命中与几何结果一字不变**。
- Inputs/outputs: 输入为画布容器的背景；输出为网格的点阵绘制与控制条的图标位。
- Constraints and trade-offs: 唯一允许的改动方式是**叠加**——在画布之下放一个自绘背景 View，或让控制条换图标。不得改动 `TreeCanvasView` 的布局/命中/标签逻辑，不得改动已验收的 `tree-layout` 模块，不得把新稿的 SVG 布局常量搬进工程。宁可网格参数复用既有 `space_*` 档位而不新增度量 token。
- Acceptance evidence: 由真实命令输出提供——既有四组画布测试全绿的回归证据；网格 View 的参数断言；`tree-layout` 模块零改动的证明；构建与 Java-only 守门退出码。
- Facts: `res/drawable/` 下无 UI 图标，全仓只有 `screen/TreeCanvasView.java` 使用 `Path()` 自绘；画布相关的既有测试有 `TreeCanvasGeometryTest`、`TreeCanvasLabelsTest`、`TreeCanvasPresenterTest`、`TreeCanvasViewStateTest`、`TreeCanvasWiringTest`、`TreeCanvasStoreInstrumentedTest`；`screen_tree_body.xml` 在 `layout/` 与 `layout-sw600dp/` 下各有一份（平板为真双列结构）。
- Non-goals: 不替换或改造控制条（SPEC 疑虑 4 采 B）；不改布局算法；不新增交互（不做平移/缩放手势）；不引入图片位图资产。

## Goal

画布背后出现一层细密点阵网格（纯装饰、不参与任何命中与布局计算），且画布的既有布局、几何与命中结果逐项不变。

## Scope

### In scope
- 新建 `screen/GridBackdrop.java`：一个只做绘制的 `View` 子类，在 `onDraw` 里按固定间距绘制点阵。参数（间距、点径、颜色）全部取自既有资源：颜色用 `@color/tl_line` 一类既有 token，间距与点径**复用既有 `@dimen/space_s*` 档位**。不新增 `dimens.xml` 条目。
- `res/layout/screen_tree_body.xml` 与 `res/layout-sw600dp/` 下的画布布局：把 `GridBackdrop` 作为画布容器的**兄弟节点置于画布之下**（z 序在下），铺满容器；控制条动作位换成图标（本片自带约 3 枚 `ic_tree_*` 资产，按 056/057 同一约定）。
- 本片独占 `res/layout/screen_tree_body.xml` 与 `layout-sw600dp/` 下的画布布局（本批其它任务不改动 tree 相关布局；056/057 已把这两个文件列为 058 的领地）。
- `res/values/strings.xml`：为控制条图标动作补齐 `contentDescription`（`cd_` 前缀），不删原文字键。

### Out of scope
- `android/tree-layout/` 模块的任何文件（已验收的纯 Java 布局算法）
- `screen/TreeCanvasView.java` 的布局、命中、标签逻辑
- `screen/TreeCanvasScreen.java` 的动作语义与顺序
- `res/values/colors.xml`、`dimens.xml`、`themes.xml`（050 的领地，且**不得新增度量条目**）
- 全部 IA 链条布局（052–055）、`item_recommendation.xml`、056/057 的领地文件
- 新稿画布右上角的缩放工具栏（SPEC 疑虑 4 结论 B：不替换现控制条）
- 位图资产、第三方绘图库

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**画布容器的背景**（新增点阵层）与**控制条的视觉载体**（文字 → 图标）。必须原样保留的行为是：`tree-layout` 的全部几何与标签结果、`TreeCanvasView` 的命中测试、画布控制条的三个动作及其顺序与可用性、以及平板下画布的真双列结构。这是本批**最不能碰行为**的一片。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 布局结果的唯一生产者是 `android/tree-layout/` 模块（`TreeLayoutGeometryTest`、`TreeLayoutHitFitAnchorTest`、`TreeLayoutLabelTest` 三组测试盯着）；画布视图的生产者是 `screen/TreeCanvasView.java`；控制条动作的生产者是 `screen/TreeCanvasScreen.java` 与 `ui/TreeCanvasPresenter.java`。平台分支为 `layout/screen_tree_body.xml` 与 `layout-sw600dp/`（平板为真双列），两套都要挂上背景层。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 本片不改 `android/tree-layout/`、不改 `TreeCanvasView` 的算法、不改 `android/data/` 与 `android/core/`，故这些侧无行为影响；点阵层是纯绘制，不参与命中。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 画布容器多了一层点阵网格背景（纯装饰）；画布控制条的动作位由文字改为线性图标。
- Authoritative code/config/migration evidence: 新建的 `screen/GridBackdrop.java`；`res/layout/screen_tree_body.xml` 与 `layout-sw600dp/` 对应文件；`android/tree-layout/`（只读，应零改动）。
- Documentation surfaces checked: `android/README.md`（画布与图标约定两节）、`Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md`（差异分类 D：画布装饰）。
- Documentation files updated: `android/README.md` 的画布小节补一句「背景点阵为纯装饰层，不参与命中与布局」；图标约定一节登记 `ic_tree_*` 前缀（若 056/057 已建立该节，保持一致格式）。
- Producer/consumer/sibling/compatibility paths checked: `tree-layout` 模块、`screen/TreeCanvasView.java`、`screen/TreeCanvasScreen.java`、`ui/TreeCanvasPresenter.java`、`TreeCanvasGeometryTest`、`TreeCanvasLabelsTest`、`TreeCanvasPresenterTest`、`TreeCanvasViewStateTest`、`TreeCanvasWiringTest`、`TreeCanvasStoreInstrumentedTest`。
- Superseded phrases/examples reverse-searched: 画布控制条的动作名（折叠 / 进入 / 适应）在 `android/README.md` 与 `res/values/strings.xml` 中的位置；`android:text="@string/` 在画布布局中的出现位置。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次有 README 变更（画布装饰说明与图标登记）。
- Historical/PRD handling: 不涉及 PRD 口径变化。新稿的缩放工具栏属**不做**项，已在 SPEC 疑虑 4 记录，不需在别处补记。

## Acceptance criteria

- [ ] 网格是纯装饰：`GridBackdrop` 不参与命中——断言画布的命中结果与加背景前逐项一致（复用既有的命中测试，不新写判定）。
- [ ] 布局行为零变化：`tree-layout` 模块 `git diff` 零改动；`TreeLayoutGeometryTest`、`TreeLayoutHitFitAnchorTest`、`TreeLayoutLabelTest` 三组测试全绿。
- [ ] 画布既有测试全绿：`TreeCanvasGeometryTest`、`TreeCanvasLabelsTest`、`TreeCanvasPresenterTest`、`TreeCanvasViewStateTest`、`TreeCanvasWiringTest`、`TreeCanvasStoreInstrumentedTest` 全部通过，且通过计数不低于本片开工前基线（须记录两个数字）。
- [ ] 度量不新增：`git diff android/app/src/main/res/values/` 对 `colors.xml`、`dimens.xml`、`themes.xml` 零改动；网格参数取自既有档位（证据文件里给出取自哪个 token）。
- [ ] 两套平台都覆盖：`layout/screen_tree_body.xml` 与 `layout-sw600dp/` 对应文件都挂上了背景层；平板下画布仍为真双列结构（既有断言覆盖）。
- [ ] 控制条动作不变：三个动作的顺序与可用性不变，仅视觉载体改为图标；每个图标动作触达 ≥ `@dimen/touch_min` 且有非空 `contentDescription`。
- [ ] 未越界：`git diff` 证明 `screen/TreeCanvasView.java` 与 `screen/TreeCanvasScreen.java` 的动作语义未改（若因换图标必须改 `findViewById`，须证明只是 id 引用、无逻辑改动）。
- [ ] 构建与守门：`.\gradlew.bat check` 退出码 0；Java-only 守门 `android/scripts/no-kotlin-check.ps1` 退出码 0；`python .trae/scripts/dev/count_production_lines.py` 实测新增行数落进证据文件。

## TDD execution record

### RED — failing test first
- Test/scenario: `CanvasBackdropInstrumentedTest`（新增，`app/src/androidTest/java/com/treelearn/app/`）——三组结构断言：①背景层存在、在 stage 内 z 序位于画布之下、不点击不聚焦、铺满容器；②网格参数取自既有 token（间距 `space_s6`、点径 `hairline`、颜色 `tl_line`）；③控制条三动作保留原 id 且改为图标按钮、触达 ≥ `touch_min`、contentDescription 非空。RED 阶段 `GridBackdrop`/`canvas_backdrop`/`ic_tree_*` 尚不存在，预期编译期失败。
- Command: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.CanvasBackdropInstrumentedTest`
- Exit code: 1
- Observed failure: 编译失败，5 个错误
- Failure reason validated: 全部为「找不到符号」（类 `GridBackdrop`、变量 `canvas_backdrop` 等，位于 `CanvasBackdropInstrumentedTest.java` 第 58 行附近，日志第 125–135 行）——符号在 RED 阶段尚不存在，失败原因与「断言先于实现」的意图一致。证据：`artifacts/evidence/T-058-red-2026-09-24.log`（`EXITCODE=1`）。

### GREEN — minimum implementation
- Implementation: ① 新建 `screen/GridBackdrop.java`：`View` 子类，`onDraw` 绘点阵；间距=`space_s6`、点径=`hairline`、颜色=`tl_line`，公开 `spacingPx()/dotRadiusPx()/dotColor()` 三个参数 seam 供测试断言；`setClickable(false)`/`setFocusable(false)`/`IMPORTANT_FOR_ACCESSIBILITY_NO` 三重纯装饰保障。② 新建 4 枚矢量图标 `ic_tree_fold/unfold/open/fit.xml`（24dp、`strokeWidth 1.8`、圆头端点、中性源色，tint 在使用处）。③ `screen_tree_body.xml`：`GridBackdrop` 作为 `tree_stage` 的**第一个子节点**（z 序在画布之下）；`tree_fold/tree_open/tree_fit` 由 `Button` 改 `ImageButton`（src+tint+contentDescription，`minHeight/minWidth=touch_min`）。④ `strings.xml` 补 `cd_tree_fold/unfold/open/fit` 四键（原 `tree_fold` 等键保留给节点弹窗）。⑤ `TreeCanvasScreen.java`：`fold` 字段 `Button`→`ImageButton`；新增 `renderFoldControl(current)`（按折叠态 `setImageResource` + `setContentDescription` 切换）；`foldable()` 保留给节点弹窗；动作语义与顺序零改动。
- Command: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.CanvasBackdropInstrumentedTest`
- Exit code: 0
- Passing evidence: 真机（ALP-AN00 - 13）`Starting 3 tests` / `Finished 3 tests`，3/3 全绿；`BUILD SUCCESSFUL`。证据：`artifacts/evidence/T-058-green-2026-09-24.log`（`EXITCODE=0`，第 133/135/145 行）。
- Regression evidence: 全量 `:app` 单测 tests=615 failures=0 errors=0（与 T-055 收口基线一致，本片未增删单测；计数来自 `android/app/build/test-results/testDebugUnitTest/` 69 个 XML 的 `tests= failures= errors=` 属性汇总）；`:tree-layout:test` tests=34 failures=0 errors=0（`android/tree-layout/build/test-results/test/*.xml` 汇总）；`./gradlew.bat check` 退出码 0（首次运行 `dexBuilderRelease AccessDeniedException` 为 Windows 构建中间件瞬时文件锁，原样重跑通过，见 `T-058-check-2026-09-24.log`）；Java-only 守门 `VERDICT: PASS (13 OK line(s))`（`T-058-nokotlin-2026-09-24.txt`）；真机 `TreeCanvasStoreInstrumentedTest` 8/0/0（`T-058-store-instrumented-2026-09-24.log`）；`git diff -- android/tree-layout/` 零输出；`git diff -- android/app/src/main/java/com/treelearn/app/screen/TreeCanvasView.java` 零输出。

### REFACTOR — optional
- Cleanup: 无独立重构轮；测试 seam 可见性（`spacingPx` 等改 public 以跨包断言）与 float 断言 delta 在 GREEN 内一并修正。
- Regression evidence: 同 GREEN 回归证据（单测 615/0/0、tree-layout 34/0/0、check 0、Java-only PASS、真机 3/3 + 8/0/0）。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: TDD 适用（可先写「背景层存在且为纯装饰」的断言）；规模 120 行未超任何阈值。
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- **唯一允许的改动方式是叠加。** 点阵必须放在画布**之下**（z 序在下）作为兄弟节点，或由一个独立 `View` 绘制；**不得**在 `TreeCanvasView` 的 `onDraw` 里插绘制步骤——那会与已验收的几何/标签测试纠缠，是本片最大的风险来源。
- 新稿画布有缩放工具栏，但 SPEC 疑虑 4 已由用户拍板采 **B：保留现控制条，只叠加点阵网格背景**。不要「顺手」把工具栏做进来——那是新交互，越过了「不新增功能」的红线。
- 不新增 `dimens.xml` 条目：050 已 verified 且它是 `res/values/` 的所有者；网格间距与点径复用既有 `@dimen/space_s*` 档位，颜色用既有 `@color/tl_*`。若确需新档位，须停下并把依赖显式化，不得就地改 050 的领地产物。
- 图标约定与 056/057 完全一致（24dp 网格、`strokeWidth 1.8`、圆头端点、资产内中性源色、使用处 `android:tint`）。画布控制条的图标只有约 3 枚，本片自带，不依赖 057。
- 平板下的画布是真双列结构，背景层要与列结构对齐铺满；只挂在手机布局上会让两套配置漂移——这正是 `layout-sw600dp/` 存在的意义。

## Verification report

- Verdict: PASS
- Evidence: 全部落进 `artifacts/evidence/T-058-canvas-grid-backdrop.txt`（CMD-1 至 CMD-14，命令与 `EXITCODE=` 内嵌）。
  逐条对账：①命中/布局零变化——`git diff` 对 `TreeCanvasView.java` 与 `android/tree-layout/` 均零输出（CMD-11/12），
  既有画布单测随全量 615/0/0 全绿；②`tree-layout` 零改动 + `:tree-layout:test` 34/0/0（CMD-4）；③`TreeCanvasStoreInstrumentedTest`
  8/0/0 真机（CMD-5）；④度量不新增——`colors.xml`/`themes.xml` 零改动，`dimens.xml` 存在的唯一 diff 是 **T-041 遗留未提交**
  的 `popup_max_height` +3 行（内容见 CMD-13 原样粘贴），T-058 未触碰 `res/values/` 下任何文件；网格参数取自既有档位：
  间距=`space_s6`、点径=`hairline`、颜色=`tl_line`（测试断言即证明）；⑤两套平台——手机与平板**共用同一 `screen_tree_body.xml`**
  （`layout/screen_tree.xml` 与 `layout-sw600dp/screen_tree.xml` 均 `<include>` 同一 body，body 文件头注释明言
  "so the phone and the tablet cannot drift apart"），挂一次背景层即两平台同时覆盖；`layout-sw600dp/` 下不存在 body 副本，
  无漂移面；平板真双列结构由既有 `TreeCanvasStoreInstrumentedTest` 覆盖；⑥控制条动作不变——三动作顺序与语义零改动
  （`TreeCanvasScreen.java` 的 diff 仅为 `Button`→`ImageButton` 类型引用与图标切换渲染），触达 `touch_min`（布局
  `minHeight/minWidth`，见 CMD-14 grep），`cd_tree_*` 四键非空且随折叠态切换；⑦构建与守门——`check` 0（CMD-3）、
  Java-only PASS（CMD-6）、真机 3/3（CMD-2）；⑧生产行数实测 96 行（`count_production_lines.py` 对 5 个新建文件：
  GridBackdrop 44 + 4 枚图标各 13，CMD-7），落在预估 120 行内。RED/GREEN 同命令（CMD-1/2）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片不删除断言；不修改布局算法，故既有几何/标签/命中断言语义不变，必须全绿。
- Gaps: 点阵的观感（间距、点径、对比）最终由人 review 判定；结构断言只能证明它存在且不参与命中。
- Risks: 最大风险是把装饰做进了自绘逻辑，从而扰动已验收的布局与命中；其次是把新稿的缩放工具栏一并做进来（新交互，越红线）。两者都由「不改 `TreeCanvasView` 算法」与「AC 里对命中逐项比对」拦住。

## Handoff notes

- Completed: 本片已 100% 完成并 verified（2026-09-24）。画布 `tree_stage` 内、画布之下新增 `GridBackdrop` 点阵层
  （`space_s6`/`hairline`/`tl_line`，纯装饰：不点击、不聚焦、无障碍标记不可交互）；控制条三动作（折叠/进入/适应）
  换为 056/057 同约定的线性图标，`contentDescription` 随折叠态切换；`TreeCanvasView` 与 `tree-layout` 模块零改动。
  全部门禁绿：单测 615/0/0、tree-layout 34/0/0、check 0、Java-only PASS、真机 3/3 + 8/0/0。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 本片是本批最后一块拼图，完成后除 T-055 的用户裁决外，本批全部子任务可达 verified。后续顺序按父需求的
  附件链推进：096→097→098 → 103/104/099/106/105/078/095/017/034 → T-019 终验（见 `.trae/ROADMAP.md`）。
- Watch-outs: (1) 点阵只能叠加，不得进 `TreeCanvasView` 的算法——已由零 diff 证明守住；(2) 不得替换或扩展控制条
  （疑虑 4 采 B）——已守住；(3) 不得新增 `dimens.xml` 条目——已守住（`dimens.xml` 的 diff 仅为 T-041 遗留）；(4) 不得改
  `tree-layout` 模块——已守住；(5) 平板布局背景层——因 body 单源共用，天然两平台覆盖。
