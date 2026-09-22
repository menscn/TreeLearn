# Treelearn Harness Task: 实现主题与分支生命周期

id: T-2026-09-20-009
title: 实现主题与分支生命周期
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: true
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-20-deepseek-stream-gateway-006.md, T-2026-09-20-immutable-context-snapshots-007.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首题远端接受前不建空树；稳定回答可整条或选区分叉并固定来源版本。
- Problem/background: 本地先建主题会在 Key/余额/请求失败时残留垃圾；分叉若跟随 active revision 会改写历史。
- Users/scenarios: 用户创建首题、继续追问、从整条回答或选中文字创建子分支。
- Expected outcome: CreateTopic、AppendQuestion、CreateBranch 用例和事务契约。
- Core flow and reversibility: 草稿先留 UI；验证 Key/余额；模型请求 2xx accepted 后原子建根；分叉原子建 snapshot。
- Inputs/outputs: 输入草稿、branch/revision/anchor；输出 topic/branch/operation ID 或领域错误。
- State and exception handling: 2xx 前失败无树；2xx 后中断保留树与部分内容；非稳定版本禁止分叉。
- Constraints and trade-offs: 一主题一根；用户消息不可变；anchor 用 offsets + quote + hash。
- AI recommendation and rationale: 将“供应商已接受”作为首题本地身份边界，兼顾无空树和流中断恢复。
- User choice and custom additions: 首版个人本地应用，失败回到原位重试。
- AI-autonomous decisions: 本地 operation UUID 去重；供应商计算不可见重复不承诺端到端幂等。
- User-confirmation decisions: P0 首次创建原子性与不可变来源。
- Acceptance evidence: fake gateway + SQLite 事务集成测试覆盖 2xx 前后所有失败点。
- Facts: 技术方案 §5、§8 是权威。
- Assumptions: Gateway 能在读取流前发出 accepted 事件。
- Unknowns: 供应商未提供可依赖幂等键。
- Risks: accepted 与本地事务失败造成远端已计费但本地无树。
- Deferred: 服务端幂等和跨设备合并。
- Non-goals: 不实现页面视觉、重置或归档。
- Skipped non-critical questions and reason: 默认标题由派生 AI 任务负责，不影响树创建。

## Goal

建立无空树、来源版本固定、事务一致的主题和分支核心流程。

## Scope

### In scope
- CreateTopic/AppendQuestion/CreateBranch、anchor 校验、snapshot 创建、首次 accepted 事务和失败结果。

### Out of scope
- 页面、派生标题、重试/重置、删除和搜索。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增主题/分支命令；保持草稿、稳定版本和分支隔离语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 首页/聊天触发；Gateway accepted；DAO/ContextSnapshot/Coordinator 消费。
- Native bridge or independent-client compatibility/update requirements: 需匹配 Gateway accepted 与 Repository 事务接口。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首题落库边界、分支来源、anchor 与快照创建。
- Authoritative code/config/migration evidence: application use cases 与事务集成测试。
- Documentation surfaces checked: 技术方案 §5/§8、PRD 创建/追问/分叉流程、原型入口。
- Documentation files updated: 真实边界改变时同步 PRD 和技术方案。
- Producer/consumer/sibling/compatibility paths checked: 首页提交、聊天追问、整条分叉、选区分叉、失败返回。
- Superseded phrases/examples reverse-searched: 提交即建空树、动态继承父上下文。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 当前态与历史 changelog 分开。

## Acceptance criteria

> 本轮按用户指令（2026-09-21）**不使用模拟器**：需要真机的行为结论一律记为
> `deferred — emulator not used this round (user instruction 2026-09-21)`，永不记为 PASS。
> 本任务本轮交付的切片是「分叉创建的**唯一原子写入口** + 读路径大声失败 + 该不变量的文档化」。

- [ ] 同一集成测试命令先 RED 后 GREEN；401/402/422/429/503 与 2xx 前断网均不产生 topic/branch/message。
  - 状态：deferred — emulator not used this round (user instruction 2026-09-21)。该切片的实现与真机断言属 T-2026-09-21-025（首题原子创建）与 T-2026-09-20-008（协调器失败通道）；本任务不重复实现，也不替它们宣称通过。
- [ ] 2xx accepted 后一个事务创建根 topic/branch、用户/AI message 和 operation；流中断保留该现场。
  - 状态：deferred — emulator not used this round (user instruction 2026-09-21)。同上，属 T-025 与 T-008。
- [ ] 仅稳定 revision 可分叉；新 branch 固定 sourceRevisionId、anchor 与 immutable snapshot。
  - 状态：compile-only 已通过（断言写在 BranchCreationInstrumentedTest 的 1、2、10 号用例里）；行为结论 deferred — emulator not used this round (user instruction 2026-09-21)。
- [ ] 父分支后续追问或重置后，子分支来源和上下文仍指向创建时版本。
  - 状态：compile-only 已通过（aForkKeepsItsPinnedRevisionAndSnapshotAfterTheParentAnswerMovesOn）；行为结论 deferred — emulator not used this round (user instruction 2026-09-21)。
- [ ] 评审指出的 P1：非根分支允许 context_snapshot_id 为 NULL，而读路径对该行大声失败，库接受的状态应用读不出来。
  - 状态：本轮以「单一原子写入口 BranchCreation.create + 既有读路径大声失败」闭合，DDL 未改；证据见 artifacts/evidence/T-009-red.txt 与 T-009-green.txt（编译验证）。行为结论同样 deferred。

## TDD execution record

### RED — failing test first
- Test/scenario: :app 的 androidTest 套件 BranchCreationInstrumentedTest（本任务新建，10 个 @Test）先写；生产类 BranchCreation 不在编译路径上时，该套件必须编译失败（compile-only RED：本轮不跑设备，失败以编译器为准，不假装成一次设备断言）。
- Command: cd android; .\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
- Exit code: 1
- Observed failure: 任务 :app:compileDebugAndroidTestJavaWithJavac FAILED，javac 自报 10 个错误，全部落在 BranchCreationInstrumentedTest.java（7 条 程序包 BranchCreation 不存在 + 3 条 找不到符号，行号 28/83/90/110/181/185/208/213/229/234）。
- Failure reason validated: 失败由缺失的被测生产类造成，而不是环境问题：错误行只属于唯一的引用方（本任务自己的新测试文件），把该类放回原处后同一条命令即得到 GREEN；两份记录的命令字符串逐字节相同。

### GREEN — minimum implementation
- Implementation: 新增 android/data/src/main/java/com/treelearn/data/db/dao/BranchCreation.java（非根分支的唯一创建入口）：分支行 → 出生快照行 → 指针三步在同一个 DatabaseTransactionRunner 事务里完成，任一步被拒则三步整体回滚；跨主题来源判定复用 FirstTopicCreation.sourceRefusal，不写第二份；调用方 bug（null 入参、根分支、快照的 branchId 或 id 与分支不一致）在开事务前抛 IllegalArgumentException；库层拒绝以 Refusal.STORAGE_REFUSED 连同 SQLite 自带消息返回。既有读路径 BranchDao.findById 对指针为 NULL 的非根行仍大声失败（消息含 no birth snapshot），DDL 未改。
- Command: cd android; .\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
- Exit code: 0
- Passing evidence: artifacts/evidence/T-009-green.txt——BUILD SUCCESSFUL，EXITCODE=0；同一文件末尾的 artifact check 段另给判据行：构建出的 androidTest APK 的 classes3.dex 字符串表里有 Lcom/treelearn/app/BranchCreationInstrumentedTest; 与两个测试方法名，且 javac 产物的 mtime 晚于源文件 mtime（编译进测试 APK，未执行）。
- Regression evidence: artifacts/evidence/T-009-jvm.txt（EXITCODE=0，其中 :data:testDebugUnitTest 本轮实际执行）、artifacts/evidence/T-009-tests.txt（各套件 JUnit 计数读自 XML）、artifacts/evidence/T-009-guard.txt（VERDICT: PASS，EXITCODE=0）。

### REFACTOR — optional
- Cleanup: 无重构：本轮只新增一个类并改写 android/README.md 的持久化入口索引，未改动既有实现的形状。
- Regression evidence: 与 GREEN 同一组回归证据（JVM 套件 + Java-only 守门），未因本轮改动出现新的红。

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

### Evidence detail (this round)

上面五个键给纯值；下面是从证据文件里回读的原始读数，以及每条数字的取数命令。

```text
# RED — artifacts/evidence/T-009-red.txt
Command: cd android; .\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
EXITCODE=1
> Task :app:compileDebugAndroidTestJavaWithJavac FAILED
javac self-report: 10 个错误（该行在文件里出现两次：javac 自己的计数行 + 失败摘要块）
Get-Content artifacts/evidence/T-009-red.txt | Select-String ": 错误: "
  列 0（以 D:\ 开头）命中 10 行 = 7 条「程序包 BranchCreation 不存在」+ 3 条「找不到符号」（7 + 3 = 10，分项之和等于总数）
  缩进的失败摘要块再命中同一批 10 行（javac 打印两遍，故文件内共 20 行）
  去重后的出错文件只有 1 个: BranchCreationInstrumentedTest.java
RESTORE: BranchCreation.java restored from the hold file; sha256 AFTER=4FBFC72B620B54BB9097FEFFB677EF76C0C0A839A704DE7E72238B3FB429B0F2 ; MATCH=True
手法（如实记录，且已判定为不该再用）: 把 android/data/src/main/java/com/treelearn/data/db/dao/BranchCreation.java
  移到 %TEMP% 下约 11 秒以制造缺失符号，随即按 SHA-256 复原。另一条 lane 的同类做法让整仓约 20 分钟
  不可构建，见 artifacts/evidence/T-009-crosslane-stash-recovery.txt；后续 compile-only RED 应在仓库外的临时副本上做。

# GREEN — artifacts/evidence/T-009-green.txt
Command: cd android; .\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
EXITCODE=0
BUILD SUCCESSFUL in 13s
末尾 artifact check 段（同一文件）给出的判据行:
  app-debug-androidTest.apk  sha256=C5A75005B7B3C5CF3A0FE85A17D556C2A663A2139BD8EEE6476D1EDDDD1E5840  948961 B
  dex 字符串表扫描: classes3.dex  testClassDescriptor=True  testMethod1=True  testMethod2=True
  .../intermediates/javac/debugAndroidTest/.../BranchCreationInstrumentedTest.class  mtime=2026-09-21 21:28:46
  源文件 android/app/src/androidTest/java/com/treelearn/app/BranchCreationInstrumentedTest.java  mtime=2026-09-21 20:42:17（早于编译产物）
诚实标注: 这次 GREEN 的 gradle 运行把 :app:compileDebugAndroidTestJavaWithJavac 报为 UP-TO-DATE（并发 lane 已用同一份输入编过），
  所以退出码 0 单独不足以自证；上面那组「类与方法确实在编译产物与测试 APK 里」的读数是补上的判据行。

# JVM — artifacts/evidence/T-009-jvm.txt
Command: cd android; .\gradlew.bat :core:test :ai:testDebugUnitTest :data:testDebugUnitTest :tree-layout:test --no-daemon --console=plain
EXITCODE=0
JUnit 计数（逐类读自各模块 XML，见 artifacts/evidence/T-009-tests.txt）:
  :core:test                 154 tests / 0 failures / 0 errors / 0 skipped  (13 个类)
  :ai:testDebugUnitTest       72 / 0 / 0 / 0  (7 个类)
  :data:testDebugUnitTest      6 / 0 / 0 / 0  (1 个类，本轮在该次 gradle 运行里实际执行)
  :tree-layout:test           34 / 0 / 0 / 0  (3 个类)
  :app:testDebugUnitTest      27 / 0 / 0 / 0  (5 个类，附带上界证据)
  on-device 结果集: 无——本轮未使用模拟器，androidTest-results/connected/debug 是空目录（0 个文件）

# GUARD — artifacts/evidence/T-009-guard.txt
Command: powershell -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1   (从仓库根执行)
VERDICT: PASS (13 OK line(s))   EXITCODE=0

# 规模（判定口径：非空非注释生产行；新增文件按全部非空非注释行）
android/data/src/main/java/com/treelearn/data/db/dao/BranchCreation.java
  physical=226  nonEmpty=199  nonEmptyNonComment=109（取数命令见 artifacts/evidence/T-009-tests.txt 的 sizing 段）
  109 低于 200 软上限；物理行仅作参考，不作判定依据。
```

## Implementation notes

- 首题 accepted 后数据库事务失败要明确反馈，不得用第二个本地 topic 猜测补偿。
- accepted 回调必须在根事务提交成功后才向网关确认继续读取 SSE；提交失败关闭本次流。
- anchor offsets 以稳定 answer 字符序列定义，并保存 quote/hash 回退。

## Verification report

> 独立 verifier 复核（verifier #2，2026-09-21 第三轮），只读：未修改任何生产代码、测试或他人证据。
> 本轮按用户 2026-09-21 指令**不使用模拟器**；需要真机的验收项一律记为
> `deferred — emulator not used this round (user instruction 2026-09-21)`，既不算 PASS 也不算 FAIL。
> verifier 自产证据见 `artifacts/evidence/VERIFY3-*.txt`。

- Verdict: PARTIAL
- 审阅文件与证据: 本任务文件；`android/data/.../db/dao/{BranchCreation,BranchDao,FirstTopicCreation}.java`、
  `android/data/.../db/TreeLearnSchema.java`、`android/app/src/androidTest/.../BranchCreationInstrumentedTest.java`、
  `android/README.md`；证据 `T-009-{red,green,jvm,tests,guard,device-era-2026-09-21,crosslane-stash-recovery}.txt`；
  verifier 证据 `VERIFY3-T009-green-rerun.txt`、`VERIFY3-jvm-rerun.txt`。
- 代码量: `BranchCreation.java` **109** 非空非注释（物理 226 / 非空 199）；verifier 复算逐字一致 → 在预算内。
- 任务合规: PASS（`validate_tasks.py` PASS；RED/GREEN 键为纯值；两条 `Command` 逐字节相同）。
- 验收点对照:
  - [x] **唯一原子写入口** — verifier 读源码确认：`BranchCreation.createInside` 在**一个** `DatabaseTransactionRunner`
    事务内依次执行 `FirstTopicCreation.sourceRefusal` → `BranchDao.insert` → `ContextSnapshotDao.insert` →
    `BranchDao.setContextSnapshot`；任一拒绝 `throw refuse(...)` 触发回滚并返回分类 `Outcome`；
    `Refusal` 两个值都有生产者；调用方 bug（null、根分支、快照与分支不一致）在开事务前抛 `IllegalArgumentException`。
  - [x] **读路径大声失败** — `BranchDao.readSource` 对半截锚点抛 `IllegalStateException`；`BranchDao` 第 233 行对
    「非根分支没有出生快照」抛 `IllegalStateException`（消息含 no birth snapshot）。未放宽。
  - [x] 跨主题来源判定**复用** `FirstTopicCreation.sourceRefusal`，源码中未写第二份。
  - [x] **DDL 未被本任务改动** — `TreeLearnSchema.java` mtime **18:08:17**，早于本任务工作时刻
    （`BranchCreation.java` mtime 21:01:03）；schema 自己的注释也把同主题规则判给写路径。verifier 读 `BRANCHES_DEFINITIONS`
    后同意「立即 CHECK + 互为外键 ⇒ DDL 表达不了该不变量」这一判断。
  - [x] RED 数字可核：20 条 `错误:` 行 = 10 条去重 × 2 遍；3 条 `找不到符号` + 7 条 `程序包BranchCreation不存在` = 10，
    与 javac 自报 10 一致；出错文件去重后只有 `BranchCreationInstrumentedTest.java`；行号与任务所写十处逐一吻合。
  - [x] **GREEN 我亲自复跑并强制重编译**（先清空 `app/build/intermediates/javac/debugAndroidTest`）：
    `:app:compileDebugAndroidTestJavaWithJavac` **真实执行**、`BUILD SUCCESSFUL`、`EXITCODE=0`，产物含
    `BranchCreationInstrumentedTest.class` 与 `$Seeded.class`（该套件 10 个 `@Test`）。这消除了 lane 自述的
    「本次 GREEN 里该任务 UP-TO-DATE，退出码不足以自证」这一缺口。
  - [x] JVM 套件 verifier 复跑：core 154 / ai 72 / data 6 / tree-layout 34，全部 0 失败（XML 回读）。
  - [ ] 行为结论（仅稳定版本可分叉、父分支移动后子分支仍钉住、10 例真机断言）—
    **deferred — 本轮不使用模拟器（用户指令 2026-09-21）**。
- 专项守门路由: android-no-kotlin-guard PASS；`verifyDependencyDirection` PASS（verifier 复跑）。
- 文档影响记录: PASS — `android/README.md:224/229/230/259-262` 已把 BranchCreation 行与「不变量由两半承担、都不在 DDL」
  写成现行事实，verifier 逐行复核存在。
- 启动证据: N/A。
- 结论: **PARTIAL**
- 修复指令: 无代码级修复。设备恢复后跑 `:app:connectedDebugAndroidTest` 取 10 例行为结论；可择机更新 `BranchDao`
  javadoc 里「调用方欠第二步」的旧措辞（当前刻意保留，以免使 T-022 钉住该文件 SHA 的证据失效）。
- 阻塞点与剩余风险: 设备执行 deferred；绕过入口写原始 SQL 仍能造出半截行——这正是读路径那一半不可省的原因，
  已由 `aNonRootRowWithoutItsSnapshotIsRefusedByTheReadPath` 设计覆盖（未执行）；本任务 compile-only RED 曾用
  「把生产类移出共享树约 11 秒」的做法，任务已自评不该再用且有两条 lane 的复原记录。

## Handoff notes

- Completed: 新增 BranchCreation（:data）——非根分支的唯一原子创建入口：分支行、出生快照行与两者之间的指针在同一个 DatabaseTransactionRunner 事务里；跨主题来源判定复用 FirstTopicCreation.sourceRefusal；调用方 bug 在开事务前抛 IllegalArgumentException，库层拒绝返回 Refusal.STORAGE_REFUSED 并带 SQLite 自身消息。读路径 BranchDao.findById 对指针为 NULL 的非根行仍大声失败，未放宽。文档：android/README.md 的「持久化入口索引（:data）」新增 BranchCreation 行，并把原「两步写是调用方义务」段落改写为「不变量由单一写入口 + 读路径两半承担，且都不在 DDL」。
- In progress: 真机执行 BranchCreationInstrumentedTest 的 10 个用例——本轮按用户指令未运行，只做编译验证。
- Blocker: 无（本轮不再需要模拟器）。
- Unblock condition: 用户解除「本轮不使用模拟器」的指令后，跑 cd android; .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain。
- Next steps: ① 真机跑该套件取行为结论；② UI 层分叉入口接入 BranchCreation.create；③ 归档与删除语义按技术方案 §7 与 android/README.md 的现有约定继续（本任务未实现删除）。
- Watch-outs: ① **DDL 表达不了这条不变量**：branches.context_snapshot_id 与 context_snapshots.branch_id 互为外键，而 SQLite 的 CHECK 是立即约束、不能延迟，插入分支行的那一刻快照行还不存在；延迟外键或新增列**已考虑并刻意不采用**（属 T-004 的 schema 变更，需 db-migration-reviewer 与用户确认）。不变量因此落在两半上——单一写入口，加上读路径大声失败；绕过入口写原始 SQL 仍能造出半截行，这正是读路径那一半不是冗余的原因。② 本任务的 RED 用「把 BranchCreation.java 移出工作树约 11 秒」制造缺失符号，随后按 SHA-256 复原（MATCH=True）；**这不是好做法**：另一条 lane 的同类做法让整仓约 20 分钟不可构建（见 artifacts/evidence/T-009-crosslane-stash-recovery.txt），后续 compile-only RED 应在仓库外的临时副本上做。③ BranchDao 的 javadoc 仍写着「调用方欠第二步」，本轮**刻意未改**：T-022 的证据 artifacts/evidence/T-022-restore-location.txt 钉住了该文件的 SHA-256 与行号，改它会让那份证据失效；入口的权威指向在 BranchCreation 的 javadoc 与 android/README.md。④ 本轮的 GREEN 运行里 :app:compileDebugAndroidTestJavaWithJavac 报 UP-TO-DATE（并发 lane 已编过同一份输入），所以退出码 0 不足以自证，已另附「类与方法确实在编译产物与测试 APK 里」的判据行。⑤ 分叉不得引用 partial operation 或未来 active revision（沿用原 watch-out）。
