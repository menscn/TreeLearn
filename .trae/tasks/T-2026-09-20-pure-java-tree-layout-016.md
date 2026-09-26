# Treelearn Harness Task: 实现纯 Java 树布局引擎

id: T-2026-09-20-016
title: 实现纯 Java 树布局引擎
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: false
depends_on: [T-2026-09-20-model-immutable-answer-domain-003.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 树画布几何必须从不可变领域快照推导，不持久化像素，并适配手机/平板视口。
- Problem/background: 将布局写进 View 会难以测试，持久化坐标又会在字体、折叠和窗口变化时失效。
- Users/scenarios: 用户查看多层分支、折叠/展开、切换节点、适应屏幕并观察未读芽点。
- Expected outcome: `:tree-layout` 的输入/输出模型、确定性布局、bounds、label placement 和 hit regions。
- Core flow and reversibility: 输入可见树/轮数/约束；输出几何；任意状态变化重新计算并以稳定节点修正视口。
- Inputs/outputs: 输入不可变 TreeSnapshot/ViewportConstraints；输出 branch/leaf/bud/label/leader/bounds/hit geometry。
- State and exception handling: 空树、深/宽树、极长标题、小视口和折叠祖先均返回有限有效几何。
- Constraints and trade-offs: 纯 Java、无 android.*、无随机全局状态；空间不足可省略标签但节点仍可命中。
- AI recommendation and rationale: 几何内核独立模块，Canvas 只绘制和做矩阵转换。
- User choice and custom additions: 手机竖屏优先，兼容平板横屏。
- AI-autonomous decisions: 根从底部向上；枝长由用户轮数推导并封顶；三级密度。
- User-confirmation decisions: P1 独立树布局引擎。
- Acceptance evidence: 纯 JVM golden/property tests 覆盖几何不变量和两类视口。
- Facts: 技术方案 §12 是权威。
- Assumptions: 字体测量通过接口注入，不依赖 Android Paint。
- Unknowns: 最终视觉参数可由 Canvas 任务微调。
- Risks: 标签碰撞、浮点不稳定、极端树 O(n²)。
- Deferred: 3D、物理动画和服务端布局。
- Non-goals: 不绘制 View、不处理手势或数据库。
- Skipped non-critical questions and reason: 美术曲线参数可在不破坏输出契约下迭代。

## Goal

提供确定、可测、与 Android View 解耦的树几何和命中布局。

## Scope

### In scope
- immutable layout input/output、几何算法、标签避让、bounds/hit areas、stable anchor delta 和 JVM tests。

### Out of scope
- Canvas 绘制、Matrix 手势、弹层和真实字体资源。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增派生几何（`:tree-layout` 此前只有空骨架）；不写数据库、不改变分支领域顺序、不修改 `:core` 任一类型。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是画布调用方对 `LayoutNode` 的投影（尚不存在，见 Handoff）；消费者是未来的 `TreeCanvasView`（`android/app` 下暂无该类，`rg -l TreeCanvasView android` 无命中）；手机/平板差异只体现在传入的 `ViewportConstraints`（`Fixtures.PHONE` 412x700 / `Fixtures.TABLET` 1024x640），生产代码不含平台分支。
- Native bridge or independent-client compatibility/update requirements: 无桥接、无前端契约面；模块对 `:core` 的依赖方向被 `verifyDependencyDirection` 机械锁定为「零项目依赖」。
- Behavior-neutral non-applicability reason, if applicable: 不适用；本任务新增代码，不修改既有行为。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `:tree-layout` 现在含 12 个纯 Java 类型（输入 TreeSnapshot/LayoutNode/ViewportConstraints/Density，输出 LayoutResult/BranchGeometry/LabelGeometry/HitRegion/ViewTransform/Bounds/Pt，引擎 TreeLayout）；常量 = 原型实测值；不读时钟/随机源/数据库，不持久化任何像素。
- Authoritative code/config/migration evidence: `android/tree-layout/src/main/java/com/treelearn/treelayout/*.java`、`android/tree-layout/src/test/java/...`、`android/build.gradle` 的 `moduleDependencyRules` / `pureJvmModules`。
- Documentation surfaces checked: 技术方案 §3.1、§11、§12、§16.1；原型 v1 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 第 1889-1895、2001-2037、2086 行；`android/README.md` 第 20-46 行。
- Documentation files updated: 无（本任务写范围明确排除 `Docs/**` 与 `android/README.md`）。核对发现两处因此变旧的现存表述，已按规则上报而非自行改写：`android/README.md:35` 断言 `:tree-layout` 仍没有 `src/main/java`；技术方案第 54 行断言 `:tree-layout` 只依赖 `:core`。两处都需要一次文档收尾任务，见 Handoff notes。
- Producer/consumer/sibling/compatibility paths checked: 消费者 `:app`（树画布 View）尚未存在；同级模块 `:core` 未改动；`:tree-layout` 对上下游都没有项目依赖（`project_dependency_lines=0`）。
- Superseded phrases/examples reverse-searched: `仍**没有**` 命中 `android/README.md:35`；``只依赖 `:core` `` 命中技术方案第 54 行；`持久化像素坐标` 命中技术方案第 32/64 行（仍是正确约束，无需改写）。三条都查了，只有前两条需要收尾。
- `documentation impact: none` reason and evidence, if applicable: 不适用（有运行时事实变化，只是文档面在本任务写范围之外）。
- Historical/PRD handling: 原型与 PRD 未改写；原型仅作为常量与算法的来源被引用，其 2026 版原型被标记为「意图快照」而非当前态契约。

## Acceptance criteria

- [x] 同一 JVM 命令先 RED 后 GREEN；输出对相同输入确定且所有坐标/bounds 有限。证据：`cd android; .\gradlew.bat :tree-layout:test --no-daemon --console=plain` 在 `artifacts/evidence/T-016-red.txt`（RED，exit 1）与 `artifacts/evidence/T-016-green.txt`（GREEN，exit 0）各跑一次；确定性由 `TreeLayoutGeometryTest.sameInputProducesBitIdenticalOutput` 逐字段相等断言（含 label / hit / bounds），有限性由 `deepChainStaysFinite`、`largeTreeStaysFiniteAndReportsItsSize` 遍历全部枝段字段与 bounds 断言。
- [x] 叶/枝/芽数量、枝长轮数、折叠后代与未读聚合符合输入，不把归档子树带入默认布局。证据：`segmentLengthUsesRoundsDepthDecayAndTheCap`（127.92 / 80.688 / 300 三个 golden）、`singleNodeTreeIsOneLeafAndKeepsTheSoilBand`、`branchWithOnlyArchivedChildrenBecomesALeaf`、`collapsedBranchCountsHiddenDescendantsAndTheirUnread`、`archivedSubtreesLeaveTheDefaultLayout`、`childOrderFollowsTheSnapshotOrder`。
- [x] 已放置标签互不重叠且不遮挡叶片；无法放置可省略标签但 hit region 仍存在且不小于交互下限。证据：`barLabelsNeverOverlapAndNeverCoverALeaf`（叶片盒用 `Fixtures.leafBox` 独立重算，不复用生产几何）、`canopyBarSitsEntirelyAboveTheCrownAndAboveEveryLeafBox`、`crowdedBarOmitsLabelsButKeepsEveryNodeHittable`（12 叶里至少 1 片无标签，每片仍可命中且宽高 >= 44）。
- [x] 手机竖屏和平板横屏 fixture 的 fit bounds、底部对齐与操作节点 stable anchor delta 正确。证据：`fitKeepsTheTreeInsideTheViewportAndAlignsTheBottom`（PHONE 412x700 与 TABLET 1024x640 各跑 4 个快照）、`screenPointsRoundTripThroughTheFitTransform`、`archivingASiblingReportsTheExactAnchorShift`（锚点位移 (-16.06058, 27.52306)）、`collapseNeverMovesTheSurvivingNodes`。

## TDD execution record

### RED — failing test first
- Test/scenario: TreeLayoutGeometryTest + TreeLayoutLabelTest + TreeLayoutHitFitAnchorTest 共 34 个 JVM 用例，跑在 fail-closed 的 TreeLayout.compute 桩上
- Command: cd android; .\gradlew.bat :tree-layout:test --no-daemon --console=plain
- Exit code: 1
- Observed failure: 34 tests completed, 25 failed；几何、标签、命中与拟合断言全部因 compute 返回空 LayoutResult 而失败
- Failure reason validated: 失败只由缺失行为造成：探针仅改 compute 的返回值（桩 sha256 FA72FB2E…），把源码按字节还原（sha256 141FD565…）后同一条命令转绿，证明红与实现对应

```text
run 1, before any production source existed -> exit 1
  javac: 100 errors (10 x "程序包…不存在" + 90 x "找不到符号"), T-016-red.txt 行 1-999
  note: javac prints each message twice (inline + grouped), so raw greps read 20 + 180 = 200; see the read-back block at the end of T-016-red.txt
run 2, fail-closed stub against the then-33-test revision -> exit 1
  33 tests completed, 25 failed   (numbers now stale: the suite grew while still red)
run 3, fail-closed stub against the shipped 34-test suite -> exit 1   [the values quoted above]
  34 tests completed, 25 failed
  = 12 TreeLayoutGeometryTest + 7 TreeLayoutHitFitAnchorTest + 6 TreeLayoutLabelTest
  the 9 passing cases are the ones that assert empty/refusal behaviour, which a stub cannot break
  stub  sha256 FA72FB2EC6AC12CF48BEB41ED071E40FA4CD84424B2FCF2B1BEB6EAEF4252097
  after restoring TreeLayout.java byte-identically:
  sha256 141FD56572D31E22BDA6143DC40509F15DA3F889109B65FA7AC56352375C810D  (equal to the pre-probe hash)
```

### GREEN — minimum implementation
- Implementation: 12 个纯 Java 类型 —— 输入 TreeSnapshot/LayoutNode/ViewportConstraints/Density，输出 LayoutResult/BranchGeometry/LabelGeometry/HitRegion/ViewTransform/Bounds/Pt，引擎 TreeLayout（几何、扇角、芽、树冠标签栏、命中区域、拟合、锚点）
- Command: cd android; .\gradlew.bat :tree-layout:test --no-daemon --console=plain
- Exit code: 0
- Passing evidence: artifacts/evidence/T-016-green.txt：被判命令 exit 0；同文件内附加强制执行块（--rerun-tasks）逐条打印 34 个 PASSED；JUnit XML 回读 15+11+8=34 tests, failures=0, errors=0
- Regression evidence: artifacts/evidence/T-016-green.txt：`cd android; .\gradlew.bat :core:test :tree-layout:test --no-daemon --console=plain` exit 0；强制块 exit 0，:core 132 例 + :tree-layout 34 例（20:17 时点），并发编辑 :core 后复测 :core 154 例 + :tree-layout 34 例，failures+errors=0

### REFACTOR — optional
- Cleanup: 未做行为重构（GREEN 后无生产代码改动）。GREEN 之后唯一的一次源码编辑是 RED 重测探针，随后按字节还原并校验哈希相等
- Regression evidence: 还原后强制重跑 `:tree-layout:test --rerun-tasks`：34 PASSED，exit 0（artifacts/evidence/T-016-green.txt 末段）

### TDD or sizing exception — only with prior user approval
- Exception type: sizing（实测 862 非空非注释生产行 / 12 文件，超过 500 硬上限 72%；原估算 200）
- Reason: 该项工作量落在输入校验、几何、树冠标签栏、命中区域、拟合与锚点六个必需面；拆分会产出没有消费者的模块，而估算 200 行时低估了校验与输出模型的体量
- User approval: **2026-09-24 用户裁定「事后追认豁免」**（对话内批准；批准落在实现之后，按 `harness-tasks.md` 第 60 行如实记为事后追认）。剩余风险：单 task 超硬上限 72%；拆分会产出没有消费者的模块（见 Reason），故以豁免收口并记录。
- Alternative verification: 完整 JVM 行为套件（34 例）+ 可复算的规模测量命令（artifacts/evidence/T-016-checks.txt 末段），未以删减功能换取行数

## Implementation notes

- 规模实测（判定口径=非空非注释生产行）：862 行 / 12 文件，物理行 1247；命令与逐文件输出见 `artifacts/evidence/T-016-checks.txt` 的「size measurement」段。估算值仍是 front matter 的 200，超限事实与待批例外见上节。
- 常量来源为原型实测值，测试把它们独立重抄了一遍而不是读生产常量：`CV_SEG_BASE 120`、`CV_SEG_STEP 36`、`CV_SEG_MAX 300`（原型 1889 行）、`CV_LEAF_L/W 34/15`（1890）、`CV_FAN_MAX/STEP 46/16`（1893）、`CV_DEPTH_DECAY 0.82`（1894）、`CV_SPROUT 0.22`（1895）、`CV_LBL_W/H 136/24`、`CV_SOIL 44`（1891）、`CV_LBL_LANE 30`（1892）、三级阈值 0.72/1.05（2004）、标题栏 4 行且行内间隔 8、`topY = min(tipY) - 14`（2020-2035）。
- 所有集合按稳定 ID/领域顺序排序；`TreeSnapshot` 只把 Map 当查找表，进入几何的每个列表都来自输入列表顺序（`childOrderFollowsTheSnapshotOrder`、`snapshotChildrenKeepTheirInputOrder`）。
- y 轴向下：根在 `y=0`，树向负 y 生长，土壤带落在 `maxY`，`fit` 把 `maxY` 对到视口底部内边距（`Bounds` 注释与 `fitKeepsTheTreeInsideTheViewportAndAlignsTheBottom`）。
- 性能：`largeTreeStaysFiniteAndReportsItsSize` 对 6 层×5 叉的 9331 节点树计时，实测 `nodes=9331 leaves=7776 labels=19 elapsedMs=15`（JUnit XML system-out 回读），断言 < 5000 ms；不是截图式证据。
- 与 `:core` 的偏差：`:tree-layout` 被 `moduleDependencyRules` 禁止任何项目依赖，因此输入用本模块自己的 `LayoutNode`（纯 Java 记录投影），而不是 `com.treelearn.core.domain.Branch`。该记录不重新判定任何领域不变量：`rounds`/`ownUnread`/`collapsed`/`archived` 都由调用方投影进来，模块不创建分支、不改领域顺序。见 Handoff 的偏差说明与文档收尾项。

## Verification report

> 独立 verifier 复核（verifier #2，2026-09-21 第三轮），只读：未修改任何生产代码、测试或他人证据。
> `:tree-layout` 是纯 JVM 模块，本任务**没有**需要真机的验收项（真机渲染属 T-017），因此本轮不存在
> `deferred` 条目。verifier 自产证据见 `artifacts/evidence/VERIFY2-*.txt`、
> `VERIFY3-verifyDependencyDirection.txt`；`artifacts/evidence/T-016-doc-sync-2026-09-21.txt` 是被复核对象（leader 产出），非 verifier 产出。

- Verdict: PARTIAL（功能判据 PASS；文档一致性经本轮复核**由 FAIL 转为 PASS**；规模判据 OPEN 未获批）
- 审阅文件与证据: 本任务文件；`android/tree-layout/src/main/java/com/treelearn/treelayout/**`（12 个生产类型）；
  证据 `T-016-{red,green,checks}.txt`；verifier 证据 `VERIFY2-{jvm-suites,sizing,guard,static-findings}.txt`；
  本轮新增复核 `android/README.md:34-40`、《技术方案》V1.0 第 54 行、`android/build.gradle:25-34`、
  `artifacts/evidence/T-016-doc-sync-2026-09-21.txt`、`VERIFY3-verifyDependencyDirection.txt`。
- 代码量: **862** 非空非注释行（verifier 独立复算 12 文件，与任务自报一致；物理行我 1259 / 它 1247，
  差 12 同样来自注释与空行口径）；按裁定 grep 口径复算 = **861**。两种口径都 > 500 硬上限 →
  **规模判据 OPEN，未获批**：超出 72%，仓库内只有 T-003 一份用户裁定例外，不含本任务。
  verifier **不自行批准**，本条按「未获批的偏差」留在报告中，等用户明确裁定。
- 任务合规: PASS（template_version 2；id/文件名一致；`status: verifying`；RED/GREEN 键为纯值、两条
  `Command` 逐字节相同；`validate_tasks.py` PASS）。
- Lint: N/A（`:tree-layout` 为纯 JVM 模块）。
- 依赖方向: **PASS** — verifier 本轮亲自复跑
  `cd android; .\gradlew.bat verifyDependencyDirection --no-daemon --console=plain` →
  `verifyDependencyDirection: PASS (downward-only module graph + pure-JVM module purity)`、`EXITCODE=0`，
  输出中**不再出现** `vacuous` 字样（`VERIFY3-verifyDependencyDirection.txt`）。
  另经机械复核：`android/build.gradle:27` 为 `':tree-layout': []`（零项目依赖），
  `:tree-layout` 生产文件 **12** 个、`:core` **22** 个。
- 验收点对照:
  - [x] 先 RED 后 GREEN；输出确定且所有坐标/bounds 有限 — verifier 亲自复跑
        `:tree-layout:test --rerun-tasks`：3 个类 **34 例全绿（15+11+8）**，`VERIFY2-jvm-suites.txt`。
        RED 自洽性：34 completed / 25 failed，12+7+6=25。
  - [x] 叶/枝/芽数量、枝长轮数、折叠后代与未读聚合、归档子树不入默认布局 — 由 `T-016-red.txt` 的桩探针与
        还原哈希（`FA72FB2E…` 桩 / `141FD565…` 还原）证明失败确由缺失实现造成。
  - [x] 标签互不重叠、可省略但命中区仍在且不小于交互下限 — 34 例覆盖（叶片盒在测试里独立重算）。
  - [x] 手机竖屏与平板横屏 fixture 的 fit bounds、底部对齐与 stable anchor delta — 几何断言全绿；
        **真机渲染不在本任务判据内**（画布是 T-017）。
  - [x] **文档一致性 — PASS（本轮复核，此前为 FAIL）**：
        `android/README.md:34-40` 现为「覆盖状态（2026-09-21 更新）」段落，写明 `:core` 22 个领域类型、
        `:tree-layout` 12 个纯 JVM 类型，「两个纯 JVM 模块现在都有真实源码，纯净性扫描对二者均为真实强制，
        不再打印 `WARNING … purity contract is vacuous …`」；旧的「仍没有 `src/main/java`、扫描仍是空跑」断言已删除。
        《技术方案》V1.0 第 54 行现为「`:data`、`:ai` 只依赖 `:core`，`:tree-layout` 不依赖任何模块（纯 JVM）」，
        与 `android/build.gradle:27` 一致。verifier 逐行读回原文确认（**不采信 leader 自述，独立重读**）。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）。
- 文档影响记录: **PASS**（修复后 README 与《技术方案》均与活动事实一致，缺口闭环，不再依赖「用户批准的延后」）。
- 启动证据: N/A。
- 结论: **PASS**（2026-09-24 复核 #3 收口，见下方「复核记录 #3」；verifier #2 判 PARTIAL 的唯一未决项「规模判据 OPEN」已由用户裁定解除）
- 修复指令: 已全部执行——规模例外于 2026-09-24 获用户裁定「事后追认豁免」（见 TDD or sizing exception 节），无剩余未决项。
- 阻塞点与剩余风险: 无阻塞。遗留风险仅两条已登记事项：几何常量若再调需同步改测试并重跑（golden 由测试独立重抄）；`HitRegion.Source.ANCHOR` 的来源断言缺失（Watch-outs ③），留待画布侧新增命中语义时补。

## 复核记录 #3（独立 verifier，2026-09-24）— 收口

- verifier #2（2026-09-21）判 PARTIAL 的唯一未决项是规模：862 行 > 500 硬上限且无获批例外。
  该项已于 2026-09-24 由用户裁定「事后追认豁免」解除，并已按 `harness-tasks.md` 第 60 行登记为
  事后追认（见 TDD or sizing exception 节 User approval 字段）。
- 规模裁定后的状态核对（本轮实读）：任务文件无新增未决项；Blocker ②（guard dex 阶段因 DeepSeek
  lane 在途改动而失败）所指的 `GatewayPorts`/`AiGatewayError`/`StreamOutcome`/`ProviderJson` 均
  已落地，2026-09-24 全量 `check` 复跑 EXITCODE=0（`artifacts/evidence/P3-B4-gates-2026-09-24.txt`），
  guard PASS（`P3-B3-no-kotlin-2026-09-24.txt`），即 Blocker ② 的 Unblock 条件已满足。
- 本任务为纯 JVM 模块，无设备判据、无 deferred 项（verifier #2 已确认）。
- 结论：验收 4 条全勾 + 文档一致性 PASS + 规模例外已批 → **verified**。
## Handoff notes

- Completed: `:tree-layout` 的纯 Java 几何内核（12 个生产类型）、34 个 JVM 用例、RED/GREEN/回归/纯度/Java-only/规模证据（`artifacts/evidence/T-016-{red,green,checks}.txt`）。RED exit 1 → GREEN exit 0；回归在并发编辑 `:core` 前后各测一次（132 例 → 154 例）均 0 失败，未出现跨 lane 干扰。
- In progress: 无（实现与自验均已完成，等待独立 verifier）。
- Blocker: ① 规模例外未获批：实测 862 行 > 500 硬上限，按规则实现者不能自行追认，需要用户裁定「批准例外」或「要求拆解」；② `android/scripts/no-kotlin-check.ps1` 的 dex 阶段在整个仓库范围内失败（`:app:compileDebugAndroidTestJavaWithJavac` 55 个错误，`DeepSeek*InstrumentedTest` 引用尚不存在的 `GatewayPorts`/`AiGatewayError`/`StreamOutcome`/`ProviderJson`），属 DeepSeek 网关那条 lane 的在途改动，不在本任务写范围；该 guard 对 `:tree-layout` 的源码与依赖两项扫描均打印 OK。
- Unblock condition: ① 用户对规模例外给出明确裁定；② DeepSeek instrumentation 测试依赖的网关类型落地后 guard dex 阶段恢复（本任务的 JVM 证据不受影响）。
- Next steps: 树画布任务消费 `LayoutResult`（`TreeCanvasView` + `Matrix` 拟合）；一次文档收尾任务修正 `android/README.md:35` 与「技术方案」第 54 行；`:tree-layout` 的输入投影（`Branch` → `LayoutNode`）应由画布/装配层实现。
- Watch-outs: ① 不得在此模块引用 `android.*`/Kotlin（`verifyDependencyDirection` + no-kotlin guard 机械执行，源码里唯一出现 `android.graphics.Matrix` 的位置是 `ViewTransform` 的一段 javadoc 散文，不是 import）；② 几何是派生的，任何像素坐标都不得落库；③ `HitRegion.Source.ANCHOR` 有生产者但测试只断言了 `LABEL` 来源，新增命中语义时应补一条来源断言；④ 改 `TreeLayout` 常量会同时改变 golden 值，测试是独立重抄的，改常量必须同步改测试并重跑。

## Deviation notes (recorded for the verifier)

- 模块依赖偏差：技术方案第 54 行写「`:tree-layout` 只依赖 `:core`」，但 `android/build.gradle` 的 `moduleDependencyRules` 对 `:tree-layout` 只放行「零项目依赖」。以构建规则为运行时事实，本任务因此自带输入记录类型，未修改根构建文件，也未引入任何新依赖。
- 证据整理说明：`artifacts/evidence/T-016-checks.txt` 里最早那段 javadoc 输出因 JDK 中文输出被控制台代码页转成 U+FFFD（不可复核）而已删除，并已用英文 locale + 强制 UTF-8 stdout 重跑补上；文件内保留了该删除的说明与重跑命令，全文 U+FFFD 计数为 0。
