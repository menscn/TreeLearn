# Treelearn Harness Task: 真机上的约束错误分类缺陷（父需求）

id: T-2026-09-22-059
title: 真机上的约束错误分类缺陷（父需求）
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Parent summary: 这不是一个「想做的新功能」，而是一次**证据暴露**：在真机上跑全量仪器测试，发现约束冲突的错误分类在 Android 13 真机上失效，而过去的验收证据全部产自 API 36 模拟器，天然照不到这一面。用户于 2026-09-22 明确选择「现在补一小片修掉」，本父需求把这件事立成片，子任务为 `T-2026-09-22-constraint-codes-and-red-suites-060.md`。**本片与「Figma 新稿信息架构与视觉迁移」批次（`T-2026-09-22-figma-ia-and-visual-migration-049.md` 的子任务 050–058）无依赖关系**，两者只是恰好都被同一次真机测试照到。
- Problem/background: 在真机 `AWSS013807000356 / ALP-AN00 / Android 13` 上跑 `:app:connectedDebugAndroidTest`，得到 `178 tests / 26 failures`。逐条归因后是**四类不同原因**，而不是一个：21 个是测试里写死了 `(code NNNN` 的文案前缀（真机是 `(Sqlite code NNNN`）；**2 个是生产代码缺陷**——`ConstraintFailures.EXTENDED_CODE` 的正则 `\(code ([0-9]+)` 在真机文案上取不到码，`extendedCode()` 返回 0，于是唯一约束冲突认不出来；2 个是测试自己在断言消息里无条件调用 `.error()`；1 个是 `NavigationStoreInstrumentedTest` 的夹具没跟上「标题非空」这个后来加上的要求。
- Users/scenarios: 受影响的是**真机上的终端用户**。实测到的两条可见后果：新建一个 id 已存在的主题时，用户拿到的是 `STORAGE_REFUSED`（「存储失败」）而不是 PRD 规定的 `TOPIC_ID_ALREADY_EXISTS`（「主题已存在」）；以及一次 stabilize 里裸的 `SQLiteConstraintException` 直接穿透到调用方，而不是被翻译成领域错误。两条都不是推断，是仪器测试里实际断言差异的原文（`expected:<TOPIC_ID_ALREADY_EXISTS> but was:<STORAGE_REFUSED>`）。
- Expected outcome: 真机与模拟器上全量仪器测试都全绿；真机上重复主题 id 恢复报 `TOPIC_ID_ALREADY_EXISTS`；「怎么从异常文案里读出扩展码」全仓只判定一次，四个测试套件不再各自写一份文案匹配。
- Core flow and reversibility: 纯缺陷修复，改动集中在取码与分类两步，回退即恢复原状。错误分类的**目标值不变**——本片是让规格在该报的时候真的报出来，不是调整规格。这与迁移批次的红线（「只改交互与表现，功能规格一项不动」）方向一致：这条规格本来就没被满足。
- Inputs/outputs: 输入是设备抛出的 `SQLiteConstraintException` 文案；输出是 `DomainError` / `DomainErrorCode`。本片不改输入来源，只改「怎么从文案里读出码」。
- State and exception handling: 「无法命名的拒绝必须由调用方重新抛出，不得伪报成某个业务码」是既有的显式约定（`ConstraintFailures` 类注释第 36–37 行），本片不得削弱它。放宽正则后误匹配面变大，因此 `isUniqueOn` 的「码值 + 列名列表」双重判据必须保留。
- Constraints and trade-offs: **判定只留一处**是硬要求。现状是「生产一份取码正则 + 四个测试套件各写一份文案前缀」共五份实现，属于 `.trae/AGENTS.md` 失败案例库首条描述的漂移结构；不收敛的话，下一轮 Android 改文案还会集体再红一次。取舍在于：测试若改为调用生产判定点，会降低测试独立性——因此分工是「取码共用、期望的码值仍由测试独立给出」。另一个真实约束是跨模块可见性：`ConstraintFailures` 是 `data` 模块的 package-private 类，androidTest 在 `app` 模块。
- AI recommendation and rationale: 推荐「一起修」，包含生产侧取码缺陷。理由：这不是改规格，是把规格修回来；只清测试侧的话，真机上「重复主题 id 报错不对」会继续存在且模拟器永远测不出来。推荐的修法是正则只认 `code ([0-9]+)` 这一稳定片段（**不依赖前缀**，从而覆盖未知的第三种文案），而不是按已知前缀枚举。
- User choice and custom additions: 用户于 2026-09-22 在两个选项中选择「一起修（推荐）」，即同意本片包含 `data` 模块的生产代码改动。用户此前对迁移批次给过的红线（「这次只是交互的调整，不能影响功能的规格」）在本片不构成限制——本片修的是既有规格未被满足的问题。
- AI-autonomous decisions: 把 26 个失败按真实日志逐条归因成四类而不是接受实现者「全在同一个文案比对上」的概括；把「取码共用、码值独立给出」定为测试改造的分工；把「不依赖前缀」定为修复取向而非前缀枚举；把本片从迁移批次里独立出来单独立需求片，而不是挂到 049 下。
- User-confirmation decisions: 是否包含生产侧缺陷的修复（已确认：一起修）。
- Acceptance evidence: 由子任务的真实命令输出提供——真机与模拟器两次全量仪器测试的退出码与计数；针对两种文案的取码断言；`grep` 证明手写文案匹配只剩一处；`isUniqueOn` 双重判据未削弱的证据；`gradlew check`、Java-only 守门与全量 JVM 单测的退出码。
- Facts: 设备真机 `ALP-AN00 / Android 13`，模拟器 `Medium_Phone_API_36.1 / API 36`，本机都可用。五类约束码：UNIQUE=2067、NOT NULL=1299、TRIGGER=1811、FK=787、CHECK=275。**「为什么以前没发现」有直接证据**：`artifacts/evidence/T-004-constraint-code-probe.txt` 记录了当初的探针环境是 `emulator-5554 / Medium_Phone_API_36.1 / API 36`，那台机器吐的文案是 `(code 1299 SQLITE_CONSTRAINT_NOTNULL)`，与代码里的正则**恰好一致**；真机吐的是 `(Sqlite code 1299 SQLITE_CONSTRAINT_NOTNULL), (OS error - 2:No such file or directory)`。原始失败日志在 `artifacts/evidence/.t052-full-androidtest.log` 第 133–237 行。
- Assumptions: 真机与模拟器的差异来自 Android 版本对 `SQLiteException` 文案的构造方式，不是 SQLite 采用了不同的扩展码——由「两边码值相同、仅前缀与尾缀不同」的实测支持。
- Unknowns: 是否还有其它 Android 版本使用第三种文案，本机无法穷举。这是选择「不依赖前缀」而非枚举的直接原因。
- Risks: (1) 放宽正则引入误匹配——由既有的双重判据与现有测试共同兜底；(2) 测试改用生产判定点后独立性下降——由「取码共用、码值独立给出」的分工缓解；(3) 夹具补标题可能牵出其它夹具缺口；(4) 更上游的流程风险：**过去的仪器测试证据全部产自模拟器**，任何依赖设备/系统版本差异的行为都可能同样被漏掉——本片只修这一处，不声称已排查全部同类风险。
- Deferred: 把仪器测试的**默认跑测设备**固定下来（真机 vs 模拟器）并写进 README——本片只在 README 记录「两种环境都要跑」的事实，不改跑测默认。对「还有多少结论是只在模拟器上验证过的」做一次系统性排查，本轮不做。
- Non-goals: 不改 schema、不改 migration、不新增表或列；不改任何 `DomainErrorCode` 的取值或语义；不修迁移批次（050–058）的领地文件；不新增第三方依赖。
- Skipped non-critical questions and reason: 未追问「要不要顺便把仪器测试默认设备改成真机」——那属于跑测策略，改变的是流程而不是本片要修的缺陷，且会让本片的验收范围不可控；已记入 Deferred。

## Goal

把「约束冲突的分类在 Android 13 真机上失效」这一缺陷修掉，并让全量仪器测试在**真机与模拟器两个环境**上都可判定为全绿——从而使后续所有片子的验收基线重新成立。

## Scope

### In scope
- 子任务一个：`T-2026-09-22-constraint-codes-and-red-suites-060.md`（`owner_lane: android`）。
- 生产侧：`android/data/src/main/java/com/treelearn/data/db/dao/ConstraintFailures.java` 的取码判定。
- 测试侧：四个写死文案前缀的 androidTest 套件、`TreeCanvasStoreInstrumentedTest` 的急切求值、`NavigationStoreInstrumentedTest` 的夹具。
- `android/README.md`：补「约束错误码与跑测环境」一节。

### Out of scope
- `android/**` 下迁移批次（050–058）的领地文件
- 数据库 schema、migration、`DomainErrorCode` 的取值与语义
- 跑测策略的变更（默认设备固定）
- 对「还有多少结论只在模拟器上验证过」的系统性排查

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**约束冲突分类的准确度**（真机上从 `STORAGE_REFUSED` 恢复为 `TOPIC_ID_ALREADY_EXISTS`）。必须原样保留的是：`isUniqueOn` 的「码值 + 列名列表」双重判据、「无法命名的拒绝由调用方重新抛出」的约定、以及全部 `DomainErrorCode` 的既有语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者为 `data/db/dao/ConstraintFailures.java`；直接消费者为 `data/db/dao/FirstTopicCreation.java` 与 AnswerRevision / GenerationOperation 的 stabilize 路径；间接消费者是 `app` 侧把这些错误呈现给用户的屏。平台分支是**运行环境**分支（真机 Android 13 vs 模拟器 API 36），两套都要有证据——这正是本需求的核心。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本需求确有生产行为变化，且该变化是修复。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 约束冲突的扩展码读取不再依赖 `(code ` 前缀，因此在 Android 13 真机上也能正确分类；重复主题 id 在真机上恢复报 `TOPIC_ID_ALREADY_EXISTS`。
- Authoritative code/config/migration evidence: `android/data/src/main/java/com/treelearn/data/db/dao/ConstraintFailures.java`；`android/app/src/androidTest/java/com/treelearn/app/` 下六个受影响的套件；`artifacts/evidence/T-004-constraint-code-probe.txt`（旧探针，仅 API 36 模拟器）。
- Documentation surfaces checked: `android/README.md`（测试与设备小节）、`Docs/05-technical-design/`（若记载了错误分类的实现方式）。
- Documentation files updated: 由子任务更新 `android/README.md`（新增「约束错误码与跑测环境」一节，记录两种文案与「两种环境都要跑」）。
- Producer/consumer/sibling/compatibility paths checked: `FirstTopicCreation.java`、`FirstTopicCreationRulesTest`（JVM 侧同名规则的消费者）、`DomainError` / `DomainErrorCode`、`DomainResult.error()`、`Guard.text`。
- Superseded phrases/examples reverse-searched: `(code ` 与 `Sqlite code` 在 `android/**` 与 `Docs/**` 中的位置；`ConstraintFailures` 类注释里「SQLite's own stable format」这句已被实测推翻，须同步改掉。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次有 README 变更。
- Historical/PRD handling: 不改 PRD，V3.1 / V3.2 均不动。本需求修的是既有 PRD 规格在真机上未被满足的问题。

## Acceptance criteria

- [ ] 26 个失败已按真实日志逐条归因为四类，归因结论与 `artifacts/evidence/.t052-full-androidtest.log` 逐条可核。
- [ ] 真机上全量仪器测试退出码 0、零失败（开工前基线 `178 tests / 26 failures`）。
- [ ] 模拟器 `Medium_Phone_API_36.1` 上同样退出码 0、零失败——证明修复是「两种文案都认」而不是「把期望改成真机那一份」。
- [ ] 取码判定全仓只剩一处：`grep -rn "\(code \|Sqlite code" android/` 的手写文案匹配只出现在唯一判定点，四个 androidTest 套件不再各自写一份。
- [ ] 分类目标值不变：真机上重复主题 id 报 `TOPIC_ID_ALREADY_EXISTS`；stabilize 不再让裸 `SQLiteConstraintException` 穿透。
- [ ] `isUniqueOn` 的双重判据未被削弱；未触碰 schema / migration / 迁移批次领地文件。
- [ ] 子任务通过 Task v2 机械校验且在 `verifying` 之后由独立 verifier 判定 `verified`。
- [ ] 保留行为不退化：`gradlew check` 退出码 0、Java-only 守门退出码 0、全量 JVM 单测零失败且通过数不低于 `suites= 37 tests= 364`。

## TDD execution record

### RED — failing test first
- Test/scenario: 不适用——父需求任务不含生产实现，TDD 证据由子任务提供。
- Command: 不适用
- Exit code: 不适用
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation
- Implementation: 不适用——本任务只产出需求与子任务拆分。
- Command: 不适用
- Exit code: 不适用
- Passing evidence: `validate_tasks.py` 的退出码。
- Regression evidence: 不适用

### REFACTOR — optional
- Cleanup: 不适用
- Regression evidence: 不适用

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（父需求任务无生产实现）
- Reason: 父需求聚合子任务证据，自身不含业务改动
- User approval: 用户于 2026-09-22 明确选择「现在补一小片修掉」
- Alternative verification: 子任务的真实命令输出与两个环境上的仪器测试结果

## Implementation notes

- 本需求的**证据价值高于它的代码量**：它证明了「全部仪器测试证据产自模拟器」这一环境盲区是真实存在的、并且已经造成了一个在真机上错着却从未被任何一次验收照到的生产缺陷。这条应当作为失败案例写进 `.trae/AGENTS.md`。
- 归因必须逐条对齐原始日志，不要接受概括。实现者最初的概括「26 个失败全在同一个文案比对上」是**错的**——其中 2 个是生产缺陷、2 个是测试急切求值、1 个是夹具漂移。
- 不要把本需求挂到迁移批次的需求片下。两者的红线方向不同：迁移批次的红线是「不许改功能规格」，而本需求修的是「既有规格未被满足」，挂错会让复核者用错误的判据审这两片。

## Verification report

- Verdict: PENDING
- Evidence: 待子任务完成后汇总；本任务自身的证据为 `validate_tasks.py` 的输出。
- Changed/deleted assertion rationale and equivalent behavior coverage: 待子任务给出（判据强度不变或更强：从比对文案前缀改为比对码值 + 列名列表）。
- Gaps: 是否还存在第三种文案无法在本机穷举；「还有多少结论只在模拟器上验证过」未做系统排查。
- Risks: 见协作记录的 Risks 一节，其中最上游的一条是环境盲区本身。

## Handoff notes

- Completed: 需求澄清（用户在「一起修 / 只清测试侧」之间选择「一起修」）；26 个失败已按真实日志逐条归因为四类；「为什么以前没发现」已由旧探针的实测环境确证；生产侧缺陷的用户可见后果已由实际断言差异确证；子任务文件 `T-2026-09-22-constraint-codes-and-red-suites-060.md` 已落盘并通过机械校验。
- In progress: 子任务 `T-2026-09-22-constraint-codes-and-red-suites-060.md` 已 `verified`（独立复核者 `agent-45e1ab1e`，verdict PASS）。真机 `ALP-AN00` 与模拟器 `Medium_Phone_API_36.1` 各 `178 tests / 0 failures`，26 个红灯全清，生产侧取码缺陷已修。**本需求已完成**。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 无待办。给 CHECK 约束起名（`CONSTRAINT ck_...`）是唯一能恢复「精确到哪条 CHECK」的版本无关结构解，但属 schema/migration 变更，已记为独立片候选，**须用户另行授权**后方可立片。另有一条流程改进未做：把仪器测试的默认跑测设备固定并写进 README（本片只在 README 记录了「两种环境都要跑」的事实）。
- Watch-outs: (1) 不得只改测试期望而不修生产取码；(2) 不得按已知前缀枚举写法；(3) 不得弱化 `isUniqueOn` 的双重判据；(4) 不得让四个套件继续各写一份文案匹配；(5) 不得为迁就夹具放宽生产的「标题非空」约束；(6) 不得触碰迁移批次领地文件；(7) 同一台机器上不得并发跑两次 Gradle。
