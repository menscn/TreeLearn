# Treelearn Harness Task: 实现不可变上下文快照与组装器

id: T-2026-09-20-007
title: 实现不可变上下文快照与组装器
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 190
shared_code_touched: true
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-20-deepseek-stream-gateway-006.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 分支出生上下文必须冻结；历史 reasoning 必须真实进入后续 DeepSeek 上下文。
- Problem/background: DeepSeek 在无 tools 时忽略历史消息专用 `reasoning_content` 字段，且动态回溯父分支会改变旧分支语义。
- Users/scenarios: 用户从旧回答版本分叉、父分支继续变化后，在子分支继续追问。
- Expected outcome: 版本化 ContextSnapshot、digest、供应商无关 ContextPlan、DeepSeekContextEncoder 和容量压缩边界。
- Core flow and reversibility: 创建分支时原子固化引用；core 结构化选择上下文；ai 将稳定回答编码成普通 assistant content。
- Inputs/outputs: 输入 snapshot 与本分支稳定消息；输出有序 ContextPlan，再输出 provider messages 或显式超限/压缩失败。
- State and exception handling: 缺失 revision、digest 不符、非法 JSON、超窗或摘要失败均 fail closed。
- Constraints and trade-offs: 使用结构化 JSON；内部标签固定；不得静默删除 reasoning。
- AI recommendation and rationale: `<treelearn_reasoning>` 与 `<treelearn_answer>` 封装使供应商实际消费完整历史。
- User choice and custom additions: reasoning 展示、落库、加入后续上下文三项均为强需求。
- AI-autonomous decisions: 标签只用于内部请求，不在 UI 显示；快照创建后不可变。
- User-confirmation decisions: 不可变回答版本与 P0 创建快照。
- Acceptance evidence: 纯 JVM fixture 精确断言上下文顺序、标签、版本锚定和失败关闭。
- Facts: 技术方案 §5.3 与 §9.4 是权威；system 注入归 T-020，不属于 ContextPlan。
- Assumptions: 当前首版不发送 tools。
- Unknowns: 实际上下文上限由发布时模型契约复核。
- Risks: 标签转义、摘要覆盖范围不足、错误引用 active revision。
- Deferred: tools 专用 reasoning 回传协议。
- Non-goals: 不执行网络调用或写 UI。
- Skipped non-critical questions and reason: token 估算器实现可替换，不改变 fail-closed 语义。

## Goal

保证每个分支后续请求使用创建时固定来源，并包含可辨识的历史思考与最终回答。

## Scope

### In scope
- snapshot codec/digest、创建读取、BuildContextPlanUseCase、DeepSeekContextEncoder、reasoning 标签、历史容量预算和摘要接口边界；为 T-020 暴露明确的预算输入/结果。

### Out of scope
- system 提示词正文/注入、摘要网络生成、GenerationCoordinator 和页面折叠状态。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增固定上下文；保留分支隔离和旧 revision 引用。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): CreateBranch 生产 snapshot；GenerationCoordinator 和派生 AI 消费。
- Native bridge or independent-client compatibility/update requirements: 输出需兼容 AiGateway 普通 messages 契约。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: snapshot schema/version、digest 和内部标签协议。
- Authoritative code/config/migration evidence: `android/core` context plan/use case、`android/data` snapshot repository 与 `android/ai` DeepSeek encoder。
- Documentation surfaces checked: 技术方案 §5/§9、PRD 上下文与隐私章节。
- Documentation files updated: `android/README.md` —— `:core` 领域层入口索引补 `JsonText` / `ContextSnapshot`(+`Source`/`SourceKind`) / `ContextSnapshotCodec` / `ContextPlan`(+`Entry`/`EntryKind`) / `BuildContextPlanUseCase` 五行；`:data` 的 `ContextSnapshotDao` 行把「载荷 schema 属 T-007 的 codec」改为指向 `ContextSnapshotCodec`；`:core` 章节补「供应商边界」段说明标签只在 `:ai`；覆盖缺口一段的类型数按实测改为 22。
- Documentation files deliberately not changed: 技术方案 §5.3/§9.4 已经就是本实现所落地的形态（版本化规范化 JSON + 整体 SHA-256 + 四个固定标签），本任务没有改变 schema 或标签，因此不改正文；PRD/隐私说明已经写明历史问题、推理与回答会提交给 DeepSeek，行为与之一致，故不改。
- Producer/consumer/sibling/compatibility paths checked: 整条回答分叉、选区分叉、普通追问、重置后旧子分支。
- Superseded phrases/examples reverse-searched: “reasoning 不回传”与专用字段历史回传示例。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 只更新当前态。

## Acceptance criteria

- [ ] 同一 JVM 测试命令先 RED 后 GREEN，证明快照在父分支后续变化和 active revision 切换后仍完全不变。
- [ ] 输出普通 assistant content 精确包含 reasoning/answer 标签、转义后的原文与确定顺序。
- [ ] 缺 revision、digest 损坏、非法 snapshot、摘要失败或在调用方预留 system/当前问题/最小回答空间后仍超窗时禁止请求，不得只丢弃推理。
- [ ] ContextPlan 与 DeepSeekContextEncoder 不生成、保存或摘要 system 消息；system 恰好一次且首位的职责明确交给 T-020 请求组装器。
- [ ] 隐私文档与真实组装行为一致：历史问题、推理和回答可能提交给 DeepSeek。

## TDD execution record

### RED — failing test first
- Test/scenario: 三个新测试类先落盘（ContextSnapshotTest、BuildContextPlanUseCaseTest、DeepSeekContextEncoderTest），它们引用的 :core / :ai 生产类型当时还不存在。
- Command: cd android; .\gradlew.bat :core:test :ai:testDebugUnitTest --no-daemon --console=plain
- Exit code: 1
- Observed failure: :core:compileTestJava 失败，javac 自报 100 个错误，全部是「测试引用的生产类型还不存在」这一类（找不到符号 49 + 程序包不存在 48 + 其他 3 = 100），落在 2 个 :core 测试文件上。
- Failure reason validated: 失败原因是符号不存在，不是环境或配置问题——同一次运行里 :core:compileJava 成功、只有 :core:compileTestJava 失败，诊断给出的行号与最终落盘的测试源一致，而同一批测试在类型补齐后即编译通过并全绿。

RED 原始证据与读法（`artifacts/evidence/T-007-red.txt`，头部自带命令行与模式）：

```text
$ Select-String -Path artifacts/evidence/T-007-red.txt -Pattern '(\d+) 个错误' -Encoding UTF8 | Select-Object -First 1
artifacts/evidence/T-007-red.txt:414: 100 个错误
$ Select-String -Path artifacts/evidence/T-007-red.txt -Pattern '^EXITCODE='
artifacts/evidence/T-007-red.txt:833: EXITCODE=1
$ Select-String -Path artifacts/evidence/T-007-red.txt -Pattern '^> Task .*FAILED'
artifacts/evidence/T-007-red.txt:13: > Task :core:compileTestJava FAILED
顶层诊断行（`^[A-Z]:.*\.java:\d+: `）共 100 条，去重文件 2 个：
  android/core/src/test/java/com/treelearn/core/domain/BuildContextPlanUseCaseTest.java
  android/core/src/test/java/com/treelearn/core/domain/ContextSnapshotTest.java
```

局限（不掩饰）：该次运行没有加 `--continue`，Gradle 在第一个失败任务处停止，因此 `:ai` 侧的
`DeepSeekContextEncoderTest` 从未被编译——它的「RED」是静态事实（当时不存在 `DeepSeekContextEncoder`），
不是本文件里观测到的失败。`:core` 那一半是观测到的。

### GREEN — minimum implementation
- Implementation: 新增 :core 的 JsonText、ContextSnapshot(+Source/SourceKind)、ContextSnapshotCodec、ContextPlan(+Entry/EntryKind)、BuildContextPlanUseCase 与 :ai 的 DeepSeekContextEncoder；DomainErrorCode 增加 6 个码，每个都有本任务代码里的返回点。
- Command: cd android; .\gradlew.bat :core:test :ai:testDebugUnitTest --no-daemon --console=plain
- Exit code: 0
- Passing evidence: artifacts/evidence/T-007-green.txt（BUILD SUCCESSFUL in 18s、0 个 FAILED 任务、EXITCODE=0）与 artifacts/evidence/T-007-tests.txt（20 个测试类、226 用例、failures=0、errors=0、skipped=0，逐类数由脚本从 JUnit XML 读回后写进该文件）。坦白记录运行模式：那一次调用里 18 个任务全是 up-to-date，所以这些计数来自同一脚本读回的上一次成功运行留下的 XML，而不是该次新跑出来的；不依赖 up-to-date 的等价复跑见 artifacts/evidence/T-007-regression.txt（--rerun-tasks，同样 226/0/0/0）。新类计数：ContextSnapshotTest 12、BuildContextPlanUseCaseTest 10、DeepSeekContextEncoderTest 8，合计 30。
- Regression evidence: 同一命令下的既有用例全部保留通过：226 − 30 = 196 个既有用例，无删除或改写的断言。构建健康复跑（含其它 lane 已并入的 :core/:ai 改动）见 artifacts/evidence/T-007-regression.txt；变异测试见 artifacts/evidence/T-007-mutation.txt（VERDICT: PASS，基线 OK (22 tests)；关掉 digest 比较 → 恰好 aPayloadWhoseDigestDoesNotMatchIsRefused 转红；关掉版本闸门 → 恰好 aSchemaVersionThisBuildCannotReadIsRefused 转红；还原后 22 例重新全绿；工作树 22 个领域源文件 SHA-256 前后一致）；守门 artifacts/evidence/T-007-guard.txt（VERDICT: PASS (13 OK line(s))，GUARD EXITCODE=0，唯一的 NOTICE 是 androidTest APK 的 Kotlin 注解类、无运行期链接，属已接受情形）。

### REFACTOR — optional
- Cleanup: 没有另做结构重构。RED 暴露的两处缺陷在实现期就地修正（见「实现期发现」），修正方式是把判定收敛回唯一位置，没有引入新的判定点。
- Regression evidence: 与 GREEN 同一次运行（无独立重构提交）；变异测试 artifacts/evidence/T-007-mutation.txt 里的「还原后重新全绿」一节即为修正后行为的独立复跑。

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

### Sizing deviation — recorded, not approved
- 判定口径与实测值: 非空非注释生产代码行；6 个新增文件共 664 行 + DomainErrorCode.java 改动 7 行 = 671 行（逐文件读数与产生它的命令见 artifacts/evidence/T-007-checks.txt）。
- 硬上限与超出比例: 500 行；671 / 500 = 超出 34%。
- User approval: 未取得。任务估算为 190 行，实测远超之后没有获得任何用户批准的规模例外，实现者不能自行追认，因此本任务不得声称通过规模判据。
- Remaining risk: 单任务交付面偏大（6 个生产文件 + 3 个测试类），评审成本高；若要收敛，应按「快照 codec/实体」与「上下文组装 + 编码器」拆成两个任务，本仓库当前不允许实现者新建任务文件，故未自行拆分。
- Verifier expectation: 在用户明确批准规模例外（按规则须记为「事后追认」）之前，规模判据应判失败，其余判据按证据独立判定。

### 实现期发现
- D1（RED 暴露，已修）: BuildContextPlanUseCase.build 最初只在解析循环里检查 history 的空元素，于是一条为空元素同时也是 AI 消息时会被静默跳过——「畸形参数是调用方 bug」的用例因此转红。修法是在任何业务判定之前先校验全部 history 元素，与 :core 的错误通道规则一致（参数形状先于业务码）。
- D2（RED 暴露，已修）: DeepSeekContextEncoderTest.contentThatImitatesTheFramingCanNotForgeABoundary 的 framingLines 辅助只看开标签，4 个标签只数到 2 个，断言名字与它实际检查的范围不符。修法是同时统计 `</treelearn_` 开头的行，使「四个标签都不得被内容伪造」成为真正的断言内容。

### 验收条件 → 证据对照（判定权在 verifier）
| 验收条件 | 证据 |
| :--- | :--- |
| 同一命令先 RED 后 GREEN，且快照在父分支变化/active revision 切换后不变 | RED 见上（exit 1）；GREEN 见上（exit 0）；`ContextSnapshotTest.theSnapshotIsUnaffectedByEverythingThatHappensUpstreamAfterwards`、`BuildContextPlanUseCaseTest.theBirthHalfIsPinnedToTheSnapshotEvenAfterTheParentMovesOn` |
| assistant content 精确含标签、转义后的原文与确定顺序 | `DeepSeekContextEncoderTest` 8 例（`aStableRevisionIsOneAssistantTurnFramedReasoningThenAnswer`、`contentThatImitatesTheFramingCanNotForgeABoundary`、`escapeAndUnescapeRoundTripEveryShape` 等） |
| 缺 revision / digest 损坏 / 非法 snapshot / 摘要失败 / 超窗一律禁止请求 | `ContextSnapshotTest.aPayloadWhoseDigestDoesNotMatchIsRefused`、`everyPayloadOutsideTheSchemaIsRefused`、`aSchemaVersionThisBuildCannotReadIsRefused`；`BuildContextPlanUseCaseTest` 的 3 个 fail-closed 用例；变异测试证明其中两条断言可证伪 |
| 不生成 system 消息；system 恰好一次且首位归 T-020 | `ContextPlan` 无 system 字段（类型层面）；`DeepSeekContextEncoderTest.theEncoderNeverEmitsASystemMessage`、`theProviderFramingLivesOnlyInThisAdapter`（机械扫描 :core 生产源，0 命中） |
| 隐私文档与行为一致 | 技术方案 §9.2/隐私章节未变且与实现一致；`ContextSnapshot.toString` 只打印 digest 前 8 位，不含 payload |

## Implementation notes

- JSON 读写必须使用结构化 API；digest 基于规范化字节，不基于 HashMap 遍历顺序。
- `:core` 不得引用 DeepSeek DTO 或标签；标签协议集中在 `:ai`，并做往返、恶意同名标签和空 reasoning 测试。
- 容量接口必须接收调用方已预留预算或返回可组合的精确估算，不能假设 system 为零成本。
- 实现补充：纯 JVM `:core` 拿不到 Android 的 `org.json`，且本仓不新增第三方依赖，因此结构化 JSON 读写落在 `JsonText`（只接受对象/数组/字符串；重复键、原始控制字符、未知转义、根值后的多余文本一律拒绝），digest 覆盖它写出的规范字节。
- 实现补充：`ContextSnapshotCodec.decode` 与 `ContextSnapshot.fromStored` 的拒绝顺序固定为「schema 版本 → 结构 → digest」，使一个多缺陷的坏行总是报同一个首要缺陷。

## Verification report

### 规模例外——事后追认（用户批准，2026-09-24）

- Exception type: 规模（超 500 行硬上限）——**用户 2026-09-24 事后追认豁免**
- Reason: 实测 **669-671** 非空非注释行 / 500 硬上限，超 **约 34%**（前轮 verifier 独立复算，口径与 `sizing-adjudication-2026-09-21.txt` 一致）。不可变上下文快照的 :core 领域簇 5 文件 + DeepSeekContextEncoder（判定与编码互为一体）；用户同日裁定不做回溯拆分（与 T-039 的拆分路径区别对待，理由：无自然切缝，硬拆产生的切片无法独立编译，属治理表演）。
- User approval: **用户 2026-09-24 经结构化选项明确批准本任务豁免**（同轮批准 T-006/T-007/T-011 三项，明确否决「同 T-039 拆分」与「维持现状」选项）。本批准发生在实现完成之后，按 `rules/harness-tasks.md` 规模条款**如实记为事后追认**，不写成事先批准。
- 实测行数与超出比例: 669-671 行 / 超 约 34%（判定口径：非空非注释；物理行并列记录于前轮 verifier 证据）。
- 剩余风险: ① 500 行硬上限的威慑力被本次追认软化，后续任务可能以「先写后要豁免」效仿——缓解：本例已如实标注事后属性，失败案例库可追加此模式监控；② 三模块的规模按整体判定，未来向其追加代码不再受本例外保护，增量仍按 200/500 评估。

### verifier #3 批量复核（2026-09-24，独立于本任务实现者）

- Verdict: **PASS（设备项 deferred；规模例外已由用户 2026-09-24 事后追认）**。前轮 verifier 的技术判定（TDD 闭环、guard/lint、静态扫描、JVM 套件）全部维持；唯一 OPEN 的规模判据随用户批准关闭。
- Fresh evidence（2026-09-24 批次 2）: `artifacts/evidence/P3-B1-gates-2026-09-24.txt`（全模块 JVM 套件 + lint + androidTest 编译，BUILD SUCCESSFUL / EXITCODE=0；全库 188 套件 1725 tests / 0 失败）、`P3-B1-no-kotlin-2026-09-24.txt`（EXITCODE=0）。
- 判定依据: 前轮 verifier #2 复核结论无新事实推翻；本任务交付面（网关/快照/派生机制）在 2026-09-23 至 09-24 的后续任务（T-045/T-046/T-047 等）生产消费中被持续复用且全量套件持续全绿，构成追加的行为证据。
- Gaps（不变，均 deferred）: 设备执行项 —— `deferred — 本轮不使用模拟器（用户指令 2026-09-21）`；真实 provider 冒烟（无 Key）。

> 独立 verifier 复核（verifier #2，2026-09-21），只读：未修改任何生产代码、测试或他人证据。
> 本轮按用户 2026-09-21 指令**不使用模拟器**；凡严格需要真机的验收项一律记为
> `deferred — 本轮不使用模拟器（用户指令 2026-09-21）`，既不算 PASS 也不算 FAIL。
> verifier 自产证据见 `artifacts/evidence/VERIFY2-*.txt`（helper 脚本在 `artifacts/evidence/.verify2/`）。

- Verdict: PARTIAL
- 审阅文件: 本任务文件；`android/core` 的 `JsonText`/`ContextSnapshot`/`ContextSnapshotCodec`/
  `ContextPlan`/`BuildContextPlanUseCase`，`android/ai` 的 `DeepSeekContextEncoder`，`DomainErrorCode`；
  证据 `T-007-{red,green,tests,checks,mutation,guard,regression,validate-tasks}.txt`；
  verifier 证据 `VERIFY2-{jvm-suites,sizing,guard,static-findings}.txt`。
- 代码量: **662** 非空非注释行（verifier 复算：5 个 `:core` 新文件 575 + `DeepSeekContextEncoder` 87；
  任务自报 664，物理行 895 与我一致），另加 `DomainErrorCode.java` 改动 7 行 → 合计 **669–671** / 500 硬上限。
  无已批准例外 → **规模判据 OPEN，未获批**。
  另按 `.trae` 裁定的 grep 口径复算 = **671**（664 + `DomainErrorCode` 7，见 `VERIFY2-sizing-ruling-method.txt`）。
- 任务合规: PASS（template_version 2；id/文件名一致；`status: verifying`；RED/GREEN 键为纯值且两条
  `Command` 逐字节相同；`validate_tasks.py` PASS）。
- Lint: N/A（`:core` 为纯 java-library，无 lint 任务）；Java-only 守门见下。
- 依赖方向: PASS（`verifyDependencyDirection: PASS`，EXITCODE=0）。
- 验收点对照:
  - [x] 快照不可变、digest 由构造路径派生、每分支唯一 — JVM 用例 + `ContextSnapshotDao` 唯一索引用例。
  - [x] 上下文顺序、四标签封装、转义与「伪造边界」防护 — verifier 复跑 `:core:test` 与 `:ai` 全绿：
        `ContextSnapshotTest` 12、`BuildContextPlanUseCaseTest` 10、`DeepSeekContextEncoderTest` 8。
  - [x] fail-closed（缺 revision / digest 不符 / 非法 JSON / 摘要缺失 / 超窗）—
        `ContextSnapshotCodec` 的拒绝顺序固定为「版本 → 结构 → digest」，六个 `SNAPSHOT_*`/`CONTEXT_*`
        码**每一个都有 main 源里的生产者**（verifier 逐码 rg 复核，见 `VERIFY2-static-findings.txt` 第 3 节）。
  - [x] `:core` 不出现供应商标签 — verifier 对 `android/core/src/main`、`android/data/src/main`、
        `android/tree-layout/src/main`、`android/app/src/main` 做机械检索：**0 命中**；标签只在 `:ai`。
  - [ ] TDD 证据完整性 — **部分成立**：任务已自述「`--continue` 未加，`:ai` 一侧的 RED 是静态事实而非
        观测到的失败」（任务文件第 105-107 行）。该缺口按原文保留，不按观测到的 RED 计。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）。
- 文档影响记录: PASS — verifier 在 `android/README.md` 复核到任务声称的 5 行（180 `ContextSnapshot`、
  181 `ContextSnapshotCodec`、185 `ContextPlan`、186 `BuildContextPlanUseCase`、192 `JsonText`），
  且 §5.3/§9.4 与实现形态一致。
- 启动证据: N/A。
- 结论: **PARTIAL**
- 修复指令: ① 规模：用户明确裁定例外或拆成「快照 codec/实体」与「上下文组装 + 编码器」两个任务；
  ② 若要把 TDD 证据补全，需在 `:ai` 侧单独制造一次可观测的 RED（例如在隔离副本上编译）并把命令与
  退出码落盘。
- 阻塞点与剩余风险: 规模例外未批准；`:ai` 侧 RED 未被观测；provider 对标签文本的解释与 token 膨胀
  只能由 T-018/T-019 的真实行为验收覆盖。

## Handoff notes

- Completed: :core 的 JsonText（结构化 JSON 读写）、ContextSnapshot（不可变出生快照）、ContextSnapshotCodec（规范化载荷 + SHA-256）、ContextPlan（供应商无关的有序上下文）、BuildContextPlanUseCase（快照 + 本分支稳定轮次 → 计划），以及 :ai 的 DeepSeekContextEncoder（稳定版本编码成普通 assistant 内容）；DomainErrorCode 增加 6 个码，每个都有本任务的生产者（对照表见 artifacts/evidence/T-007-checks.txt 第 2 节）。测试 3 个类 30 例（12/10/8），JVM 全套 226 例全绿。
- In progress: 无。生产代码、测试与全部证据（RED / GREEN / 计数 / 强制重跑 / 变异 / 守门 / 机械核对）均已落盘；剩余动作只有本轮验收。
- Blocker: 功能上没有阻塞。流程上有一项：规模判据 671 行超出 500 硬上限且没有用户批准的例外（见「Sizing deviation」），本任务不得声称通过该判据。
- Unblock condition: 用户明确批准规模例外（须如实记为事后追认），或把交付拆成两个任务重新跑。
- Next steps: 解锁 T-020 提示词策略，再解锁 GenerationCoordinator 和派生 AI。T-020 的请求组装器应按「system（恰好一条，首位）→ ContextPlan 经 DeepSeekContextEncoder 得到的若干条 → 当前问题（最后一条）」组装，并用 BuildContextPlanUseCase.Budget 传入已经预留 system/当前问题/最小回答空间/编码器框架开销的容量。
- Watch-outs: 出生半段必须只读 snapshot.sources 钉住的引用，绝不能用 Message.activeRevisionId() 替代 snapshot 里的 sourceRevisionId——那正是本任务要挡住的语义漂移。；四个标签只允许出现在 :ai 的 DeepSeekContextEncoder，:core 与 UI 不得出现，:core 生产源由 DeepSeekContextEncoderTest.theProviderFramingLivesOnlyInThisAdapter 机械扫描。；:data 的 ContextSnapshotDao 继续把 payload 当不透明字符串，载荷 schema 只在 ContextSnapshotCodec 一处，本任务没有改动 android/data/**/db/**。；payload、问题、回答与推理正文不得进入日志、异常消息或 toString（ContextSnapshot.toString 只打印 digest 前 8 位）。；调用方义务且本类观测不到——historyMessages 必须是本分支已落库的轮次且不含当前问题；sources 必须能看见快照钉住的父分支行；budget.reservedChars 必须已经覆盖上面那些开销。；快照创建是一次性的：ContextSnapshot.create 的 digest 由构造路径派生，没有任何入口允许调用方提供 digest。