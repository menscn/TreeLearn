# Treelearn Harness Task: topics / branches / messages 三表 DAO 与只读映射

id: T-2026-09-21-022
title: topics / branches / messages 三表 DAO 与只读映射
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 320
shared_code_touched: true
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 七类本地事实必须以可迁移、可事务验证的 SQLite 结构持久化（见《Android SQLite 建表入场确认清单 V1.0》）。
- Problem/background: 原 T-004 把七表 schema、DAO、事务与迁移放在一个任务里，实测生产代码约 900+ 行，超过 `harness-tasks.md:45` 的 500 行硬上限。按「拆解阶段就估算 ≤ 400 行」的规则拆为三个切片，本任务是第二片。
- Users/scenarios: 用户创建主题、追问、分叉、归档、删除后重启应用，树结构与消息顺序仍一致。
- Expected outcome: `:data` 提供 `topics`/`branches`/`messages` 三表的窄 DAO，并让 `:core` 的 `Topic`/`Branch`/`Message` 可双向映射。
- Core flow and reversibility: 插入与更新走显式事务；只读查询通过 `Cursor → 实体` 映射；删除由外键级联承担，不在 DAO 里手写递归删除。
- Inputs/outputs: 输入为 `:core` 实体与查询条件；输出为不可变实体、显式空值或分类错误。
- State and exception handling: 拒绝重复 `(branch_id, ordinal)`；拒绝同一主题出现第二个根分支；连接或约束异常不得伪装成功。
- Constraints and trade-offs: UUID 字符串主键、UTC epoch millis、参数化 SQL；DDL 一律不出现本任务的代码里（只在 T-004 的 schema/helper 中）。
- AI recommendation and rationale: 每个 DAO 只暴露窄方法，事务边界由调用方显式开启，保持 Java-only。
- User choice and custom additions: 标题与阅读位置字段落在 `branches`；用户消息正文创建后不可变。
- AI-autonomous decisions: 复用 T-004 的事务 runner 与 schema 常量，不重复定义 DDL/列名。
- User-confirmation decisions: 七张逻辑表与跨表约定已确认（清单 §2/§3）；不新增第八张表。
- Acceptance evidence: instrumentation 新装写入、唯一约束、每主题单根、级联删除、关闭重开后读取一致。
- Facts: 技术方案 §5/§6、清单 §2/§3。
- Assumptions: 首个发行版无历史库，迁移路径由 T-004 覆盖。
- Unknowns: 无。
- Risks: 手写列名与 schema 常量漂移；只读映射误用写路径工厂。
- Deferred: 搜索排序与未读聚合（T-015）。
- Non-goals: 不实现 UI、HTTP、生成编排与搜索。
- Skipped non-critical questions and reason: 索引微调由真实查询计划决定。

## Goal

让主题、分支与消息三类权威事实在真实 Android SQLite 中可原子写入、按约束拒绝非法行、并可无损读回 `:core` 实体。

## Scope

### In scope
- `TopicDao`、`BranchDao`、`MessageDao` 及三表的 `Cursor → 实体` 只读映射。
- `:core` 的 `Branch.restore` 与 `Message.restore`（见下方「开工前实测」，是实现只读映射的必要前置）。

### 开工前实测：`:core` 缺两个再水合入口（2026-09-21）

验收条件 5 要求「关闭重开后读回与写入前**逐字段相等**」，因此 DAO 的只读映射必须能重建实体。
实测 `android/core/src/main/java/com/treelearn/core/domain/`：

| 类型 | 构造 | 既有再水合入口 | 结论 |
| :--- | :--- | :--- | :--- |
| `Topic` | `public Topic(...)`（`Topic.java:18`） | 不需要 | public 构造即为再水合路径，可直接用 |
| `Branch` | `private Branch(...)`（`Branch.java:30`） | **无** | 缺 `restore`；且构造把 `updatedAt` 固定为 `createdAt`（`:47`），**无法**携带已持久化的 `updatedAt` |
| `Message` | `private Message(...)`（`Message.java:25`） | **无** | 缺 `restore`；但构造**已接受** `activeRevisionId`/`activeOperationId`，`restore` 可直接复用它 |

这与 T-004 遇到的 `GenerationOperation` 是同一类缺口，但**处置不同**：T-004 那张表按行形态实现即可，
而本任务的验收明确要求读回 `:core` 实体，绕不过去。因此按 `:core` 既有约定补两个 `restore`
（`BranchSource.restore:94`、`AnswerRevision.restore` 是先例：**再水合已持久化的行，不做业务判定，
但仍校验身份，损坏的行大声失败**）。

`Branch` 需要额外处理：`updatedAt` 只能经 private `apply` 设置，故给 private 构造补 `updatedAt`
形参（`root`/`child` 两处调用点各传一次 `now`），并在构造内校验 `updatedAt >= createdAt` ——
该不变量此前**在类型内无法表达**，现在能表达了就应当守住。

### Out of scope
- schema/DDL 与 `SQLiteOpenHelper`（T-004）、其余四表 DAO（T-2026-09-21-023）、UI、搜索、生成编排。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增持久化读写；保持 `:core` 的不可变约束与状态迁移规则不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 上游 `:core` 的 `Topic`/`Branch`/`Message`；下游 T-2026-09-21-023、上下文、生成、搜索与 UI 用例。
- Native bridge or independent-client compatibility/update requirements: 无桥接；只消费 Android framework SQLite。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 三表的列名、索引、唯一约束与级联行为及其 DAO 入口；`branches` 锚点四字段的空值性（同生同灭）。
- Authoritative code/config/migration evidence: `android/data` 的 DAO 源码与 `:app` 的 instrumentation 夹具。
- Documentation surfaces checked: 技术方案 §5.2、§6.1、§11；清单 §2、§3、§4；`android/README.md` 的领域层/持久化入口索引。
- Documentation files updated: `android/README.md`（领域层/持久化入口索引 + DAO 包索引表）；`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md` §5.2（补「此时 quote/hash 亦为空」与四字段同生同灭规则）；`Docs/05-technical-design/知芽（TreeLearn）Android SQLite 建表入场确认清单_V1.0.md` §2（`branches` 行的约束列）与 §4（新增一条**未勾选**的待确认项）。
- Producer/consumer/sibling/compatibility paths checked: 三表全部 DAO 方法与级联路径。
- Superseded phrases/examples reverse-searched: 旧列名与旧 DAO 方法名；「anchorHash 必填」的表述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写冻结 PRD。
- **首轮记录的更正（守门 M2）**：上一版这里只写了 surfaces **checked**，而 `git diff` 显示当时文档侧仅 `android/README.md` 有改动——「已检查」被当成了「已同步」。本轮已真正同步技术方案 §5.2 与清单 §2/§4，并把清单 §4 的确认框保持**未勾选**：字段语义变更需要用户追认，本任务不代替用户确认（清单 `:50` 明文要求）。

## Acceptance criteria

- [ ] 同一 instrumentation 命令先 RED 后 GREEN。
- [ ] `(branch_id, ordinal)` 重复插入被真实 SQLite 拒绝，且不留下半行。
- [ ] 同一主题插入第二个根分支被判 FAIL（`branches` 的根唯一约束）。
- [ ] 删除主题后 `branches`/`messages` 级联清空，`PRAGMA foreign_key_check` 无结果。
- [ ] 关闭重开后 `Topic`/`Branch`/`Message` 通过只读映射读回与写入前逐字段相等。
- [ ] 非根分支读回时必须带 `BranchSource`（`parent/sourceMessage/sourceRevision/anchor/contextSnapshot`），且整条回答分叉时偏移为空、版本引用仍非空。
- [ ] **再水合契约**：`BranchSource.restore` 只允许出现在 `BranchDao` 的只读映射方法内；本任务对 `android/data` 全部生产源码做机械检索并给出命令与完整输出。
- [ ] `db-migration-reviewer` 按 Android SQLite 守门复核约束与级联证据，不得用替身数据库。

## TDD execution record

### RED — failing test first
- Test/scenario: `:app` 的 `androidTest/com/treelearn/app/TopicBranchMessageDaoInstrumentedTest.java`（13 例，本任务验收规格的完整实现：往返、非根分支来源、唯一约束、级联、事务回滚、查询顺序），写于任何 DAO 之前。
- Command: ./gradlew :app:connectedDebugAndroidTest --console=plain
- Exit code: 1
- Observed failure: `:app:compileDebugAndroidTestJavaWithJavac FAILED`，javac 自报 **66 个错误**，全部落在三个尚未存在的 DAO 类型上（`TopicDao`、`BranchDao`、`MessageDao`）。构成**由证据文件当场复算得出**（命令与结果见下），不是凭印象写的：`程序包com.treelearn.data.db.dao不存在` **3 条** + `找不到符号` **63 条** = 66，与编译器自报行（`artifacts/evidence/T-022-red.txt:409（verifier 复核：:399 是 `^` 续行，:409/:744 才是判据行）` 的 `66 个错误`）一致。
  ```
  $ grep -c '^[A-Z]:.*: 错误: 找不到符号' artifacts/evidence/T-022-red.txt
  63
  $ grep -c '^[A-Z]:.*: 错误: 程序包com.treelearn.data.db.dao不存在' artifacts/evidence/T-022-red.txt
  3
  ```
  注：文件里这两类错误各出现两次（`:399` 附近一次、Gradle 在 `FAILURE` 段缩进重印一次），因此上面的 grep 用 `^[A-Z]:` 锚定顶格行以避免重复计数。**本行曾写作「去重后 63 条：×3 + ×60」——那是个自相矛盾的数**（3+60≠66），由独立 verifier 指出后按上述命令改正；证据文件里从未有过 60 这个数。
  另：本 RED 记录的是 **13 例**版本的套件（交付版为 15 例，见 GREEN），编译期失败成因相同（同一批待建 DAO），因此下面 GREEN 的「命令逐字符相同」指的是**命令**相同而非套件内容冻结。
- Failure reason validated: 每条错误的名字都是待建 DAO 本身，无第二成因。**证据强度如实标注：编译级 RED，不是行为级 RED**——它证明能力缺失，不证明「观察到某个具体错误行为再修好」。证据 `artifacts/evidence/T-022-red.txt`。

### GREEN — minimum implementation
- Implementation: 新增 `:data` 的 `com.treelearn.data.db.dao` 包四个生产类——`TopicDao`（七表之一的窄行访问）、`BranchDao`（含**唯一**的 `BranchSource.restore` 只读映射）、`MessageDao`、`Rows`（共享的类型化 Cursor 读取，消除「可空整数」的三套约定）；并在 `:core` 补 `Branch.restore` / `Message.restore`（见「开工前实测」一节，是非做不可的前置）。命令与 RED **逐字符相同**。
- Command: ./gradlew :app:connectedDebugAndroidTest --console=plain
- Exit code: 0
- Passing evidence: JUnit XML `tests="52" failures="0" errors="0" skipped="0"`，其中 **15 例属本任务**（24 例是 T-004 的同批回归、1 例是 T-002 启动测试，其余为 T-023 加入后同批运行的用例；本任务 15 例的清单见 `T-022-tests.txt` 的 `--- T-022 cases (15) ---` 段）。用例清单与 XML 路径、XML 的 SHA-256 见 `artifacts/evidence/T-022-tests.txt`，构建输出 `Starting 52 tests` / `Finished 52 tests` / `EXITCODE=0` 见 `artifacts/evidence/T-022-green.txt`（**该文件在 N6 修改后整份重出，头部 `Generated:` / `Exit code:` 由 `.gen-android-test-green.sh` 在同一次运行里写入，不再是补记头**）。
- Regression evidence: ① `:core:test` **131 例 / 0 失败 / 0 错误 / 0 跳过**（126 例既有 + 5 例新增：`Branch.restore` 三例覆盖「携带创建路径产不出的状态」「拒 `updatedAt` 早于 `createdAt`」「沿用根/来源规则」，`Message.restore` 两例覆盖「携带 active 引用」「沿用角色与内容规则」），证据 `artifacts/evidence/T-022-core-tests.txt`（**131 例是本任务验收时的快照**：该数取自本任务验收轮的 XML，此后其它 lane 继续新增用例，全库现为 13 套件 / 154 例，见 `artifacts/evidence/VERIFY-core-test-counts.txt`）；② `BranchSource.restore` 在 `:data` 生产源码中**仅 1 处调用**、位于 `BranchDao.readSource`，且 `:data` 仍然零 DDL（`artifacts/evidence/T-022-restore-location.txt`，已按 H2 重出：逐条命令带 `EXITCODE=`、被检文件带 SHA-256、结论与输出一致）；③ Java-only 守门 `VERDICT: PASS (13 OK line(s))`、exit 0 与完整模式自检 `OVERALL = SELFTEST PASS (40 case(s))`、exit 0（`T-022-guard.txt`、`T-022-guard-selftest.txt`）；④ `:app:lintDebug` 与 `:core:test` 同批通过（`T-022-checks.txt`）；⑤ `:core:javadoc` exit 0（`T-022-core-javadoc.txt`——本条是本轮补的：守门指出 131+37 全绿不覆盖 javadoc，而本仓库恰有「测试绿、javadoc 红」的前例）。

### 本轮守门发现与修复（`db-migration-reviewer` PARTIAL → 逐条收口）

| 发现 | 处置 | 收口证据 |
| :--- | :--- | :--- |
| **H1** 去掉 `anchor_hash` 必填后放宽过头：半截锚点可写，读路径分别 NPE / 抛错错类 / **静默丢字段**；且 `ofRange` 分支从未被执行 | ① `TreeLearnSchema` 的 `branches` 增加**四字段同生同灭** CHECK（全空 = 整条回答分叉，全非空 = ranged 且 `start < end`），并把原先独立的 range CHECK 合并进去，避免同一条规则由两个 CHECK 分别判定；② `BranchDao.readSource` 显式区分两种合法形态，半截锚点抛带说明的 `IllegalStateException`，`ofRange` 失败时抛出携带 `domain error` 的异常而非 `.value()` | `T-004` 的 `forkAnchorColumnsAreAllOrNothing`（24 例之一：两类合法形态可写 + 六种半截/逆向形态按 `code 275` 被拒）；`T-022` 的 `rangedForkRoundTripsBothLocators`（首次执行 `ofRange` 读路径）；`readSourceFailsLoudOnARowTheCheckWouldHaveRejected`（见下条） |
| H1 附带：库层挡住坏行后，读路径的 fail-loud 已无法经正常写入触发，容易变成「只靠复审相信」的代码 | 用 `PRAGMA ignore_check_constraints=ON` 在单个语句上关掉 CHECK 造出三类坏行（模拟未来迁移 / 手工 sqlite3 / 还原的备份），断言读路径**大声失败且消息可诊断**，其中最坏的「静默降级成整条回答分叉」被专门钉住 | 新用例 `readSourceFailsLoudOnARowTheCheckWouldHaveRejected`（本任务 15 例之一），真机 API 36 通过 |
| **H2** `T-022-restore-location.txt` 不自证且已过期（`.` 被当字面量、漏匹配、结论与自身输出矛盾、无退出码） | 用 `artifacts/evidence/.gen-t022-restore-location.sh` 重出：6 条命令逐条带 `EXITCODE=`、两种 restore 写法分开统计再合并、命令按 shell 规则加引号可直接粘回复现、被检文件带 SHA-256、结论改为对输出的直接陈述 | `T-022-restore-location.txt` |
| **M1** `reading_*` 四列零生产者零消费者，而用例名承诺了它没验证的事，还用字面量列名 | 用例改名 `readingPositionColumnsExistAsIntegerOffsets`，改用 `TreeLearnSchema.Branches.*` 常量，并**真正断言列类型为 INTEGER**（此前只断言「列存在」）；缺口写入下方 Gaps | `T-022-tests.txt` 的用例名；Gaps 第 4 条 |
| **M2** `anchor_hash` 空值性属字段语义变更，未回建表清单 §2 与技术方案 §5.2 同步 | 技术方案 §5.2 补「此时 quote/hash 亦为空」与四字段同生同灭规则；清单 §2 的 `branches` 行补同一约束；**清单 §4 新增一条未勾选的待确认项**（记录发现来源与依据，不代替用户追认） | 两份权威文档的 diff；清单 §4 |
| **L1** `theDaoNeverEmitsDdlAndNeverWritesNullableIdentityColumns` 名实不符 | 改名 `theStoreRejectsANullIdentityOnANotNullColumn`，并在 javadoc 里写明「DAO 零 DDL」是源码属性、由守门与一次性检索承担，行为测试无法见证 | `T-022-tests.txt`；用例 javadoc |
| **L2** `insert` 无 javadoc；半成品行的异常读不出契约 | `BranchDao.insert` 补 javadoc（有意不写 `context_snapshot_id` + 三步同事务）；`readSource` 对空快照先判，抛出含契约原文与分支 id 的 `IllegalStateException`（不再落到 `Guard.id` 的通用消息） | `BranchDao.java` 的 `insert`/`readSource` |
| **L3** 跨主题误删边界未记录 | 写入 `TreeLearnSchema` 的 `source_message_id` 注释，说明「同主题」规则属写入路径（023），库层无法用 CHECK 表达 | `TreeLearnSchema.java` |
| **L4** 修正只对新装生效（`DATABASE_VERSION` 仍为 1、无迁移） | 记入下方 Gaps 与 Handoff watch-outs，并写入清单 §3 的迁移约定 | Gaps 第 5 条；清单 §3 |
| Q2 `restore` 惯例没有可执行守门 | 记为后续项（见 Gaps 第 6 条），不在本任务夹带实现 | Gaps 第 6 条 |
| Q2 验收条件 7 措辞比事实宽：androidTest 里也有 `BranchSource.restore` | 把 4 处**创建用法**改为真实创建入口 `BranchSource.fromOperation(..., GenerationState.STABLE, ...)`，条件按字面成立，并顺带覆盖创建路径的 STABLE 规则 | `TopicBranchMessageDaoInstrumentedTest.java`；`T-022-restore-location.txt` 的 Command 3 |


### 实现中被测试逼出来的两个真实缺陷（不是格式问题）

1. **T-004 的 `branches` CHECK 让整条回答分叉永远写不进去。** 该 CHECK 要求非根分支 `anchor_hash IS NOT NULL`，而 `:core` 的 `BranchAnchor.wholeAnswer()` 按设计让 `quote`/`hash` 全为 null（§5.2 只把**版本引用**列为必填）。实现 RED 时 fixture 立刻被 FK/CHECK 打回，才发现这是 schema 与领域模型互相矛盾：**schema 必须服从领域模型**。已改为只强制 `source_message_id` + `source_revision_id`，并在 schema 注释里写明这次更正与理由。
   **本轮守门（H1）纠正了这个改法本身**：单纯去掉 `anchor_hash` 的强制放宽得过头，半截锚点（有偏移无回退、有回退无偏移）随之变成合法行。现在改为**四字段同生同灭** CHECK——全空表示整条回答分叉，全非空表示 ranged 且 `start < end`；偏移与 quote/hash 之外的**内容**规则（quote/hash 非空、quote 长度须等于偏移跨度）仍由领域层 `BranchAnchor.ofRange` 单点判定，读路径遇到违例行大声失败而不静默降级。也就是说：**分组不变量归库层，内容不变量归领域层**，同一条规则不写在两个地方。
2. **两步写是调用方义务，DAO 必须 fail loud。** `context_snapshot_id` 与 `context_snapshots.branch_id` 互为外键，`insert` 因此不能写这个指针（否则 FK 立刻失败），必须 insert → 快照行 → `setContextSnapshot` 三步在同一事务内完成。读到「只完成一半」的行时 `BranchSource.restore` 会抛异常——这是**有意**的，已在 `BranchDao.read` 的 javadoc 里写成「调用方义务 + 本类无法观测」，而不是把 null 悄悄容错掉（否则调用方会拿到一个来源无法解析的分支）。

另有两处是**测试夹具自身的错**，被约束正确拦下，如实记录：`withLastVisitedBranch` 误把 topic id 当 branch id；以及给子分支指向了并不存在的快照行。

### REFACTOR — optional
- Cleanup: 无结构性重构。三个 DAO 与 Rows 在第一轮 GREEN 时即按「窄行访问 + 共享 Cursor 读取」分层落定，其后各轮只增加约束/校验与注释，没有需要搬动的结构；唯一的结构性调整（Rows 的抽取）发生在首轮 GREEN 之前，已写在 Implementation notes。**原写 PENDING 属漏填**，非「有待重构」。
- Regression evidence: 不适用（无重构）。就近的回归证据是 rtifacts/evidence/T-022-core-tests.txt 与设备轮次的 15/15。

### TDD or sizing exception — only with prior user approval
- Exception type: **Sizing（规模）**，TDD 无例外（`tdd_mode: required` 已满足）。
- **口径已由用户于 2026-09-21 一次性裁定：「非空非注释的生产代码行」**（覆盖 T-003/T-021/T-004/T-022 四个已完成任务）。本文件早先写下的「口径冲突待用户裁定」「两个口径一并列出，不挑好看的那个」段落**随裁定作废**，判定值现取非空非注释口径。裁定正文与四个任务的实测复算见 `.trae/rules/harness-tasks.md` 的 Task sizing 节例外表。
- Reason（命令 + 读数位置）：判定口径命令
  `grep -v -E '^[[:space:]]*$|^[[:space:]]*//|^[[:space:]]*\*|^[[:space:]]*/\*' <files> | wc -l`；`:core` 增量用 `git diff --numstat`。
  - 新增 DAO 包（4 文件）：**非空非注释 349 行 / 物理 531 行**（`BranchDao` 178、`MessageDao` 81、`TopicDao` 63、`Rows` 27）。
  - `:core` 改动（`Branch.restore` + 构造加 `updatedAt` + `Message.restore`）：**+37 非空非注释 / +69 物理**（`git diff --numstat`）。
  - **合计非空非注释 386 行 / 物理 600 行**；**判定值 = 386**。（本文件更早写的「约 417」把 `:core` 增量估高了——估 68、实测 37。）
  - **独立复算**（`artifacts/evidence/sizing-adjudication-2026-09-21.txt`，由脚本生成、数字取自命令捕获输出而非手抄）：DAO 包非空非注释**今日实测 349，与本记录逐字相同**；物理行今日为 535（较记录 +4，来自 N5/N6 的注释与用例注释改写——注释不计入判定值，故判定值不变）。
- User approval: **已批准（2026-09-21，用户一次裁定口径并批准例外）**。按判定口径本任务 **386 < 500 行硬上限 → 未越线，不需要例外**；超出 200 行软上限属 verifier **警告**级，已在例外表中记录。批准人 / 日期 / 任务 ID / 判定口径实测值与超出比例均已写入 `.trae/rules/harness-tasks.md`。
- Alternative verification: 不拆分。若将来确实需要拆，把 `Rows`（共享读取）与三个 DAO 分开会切断「DAO 只读映射 ↔ `restore`」这条必须一起验证的链。
- **`estimated_production_lines: 320` 已过期**：保留原值不改写，避免用「改大估算」掩盖偏差；判定一律以实测 386 为准。

## Implementation notes

- instrumentation 测试写在 `:app` 的 androidTest 源集（项目约定：单一测试 APK），不在 `:data` 声明 androidTest 依赖。
- 只读映射用 `AnswerRevision.restore` / `BranchSource.restore`；**写路径**必须走 `:core` 的工厂与变更方法。
- 列名一律引用 T-004 的 schema 常量，不写字面量字符串。
- 每打开一次数据库显式启用并验证 `PRAGMA foreign_keys=ON`（由 T-004 的 helper 负责，本任务只依赖）。
- 级联删除由外键 `ON DELETE CASCADE` 承担，DAO 不得手写递归删除。

## 独立复核（两轮，均由独立于实现者的一方出具）

### 1. 独立 verifier（read-only）

完整报告：`artifacts/evidence/T-022-verification-verifier.md`。结论 **PARTIAL**：八条验收条件全部成立、lint/guard/契约校验全绿，但有两项留待处理（一项阻塞于用户裁定）。

它自己复跑了全部计数、独立复算了规模，并对**两处承担唯一证据的断言**做了变异测试——这是本轮最有价值的产出：

| 变异 | 观察到的结果 | 结论 |
| :--- | :--- | :--- |
| `BranchDao.readSource` 的半截锚点守卫改为 `if (false && …)` | `40 例 / 1 失败`，唯一红点正是 `readSourceFailsLoudOnARowTheCheckWouldHaveRejected`，异常 `NullPointerException … Integer.intValue() on a null object reference @ BranchDao.readSource` | 非假见证；且变异出的正是该守卫要挡的历史形态 |
| `TreeLearnSchema` 的锚点分组 CHECK 改为 `CHECK (1 = 1)` | `40 例 / 1 失败`，唯一红点是 T-004 的 `forkAnchorColumnsAreAllOrNothing` | 非假见证 |

两处还原后 SHA-256 与变异前一致，重跑 40/40 绿。它同时独立复算了 `T-022-restore-location.txt` 里 7 个文件的 SHA-256（逐字符相同）、逐字复跑其中 4 条命令（匹配集与退出码一致）。

它提出的四项：

- **F1（唯一阻塞项，治理）**：统一非空非注释 386 行 / 统一物理 600 行；物理口径超 500 硬上限且无**事先**批准。要求不在"让用户猜口径"上停留——已在上面 sizing 一节按它的实测值改写。
- **F2**：H1/H2/M1/M2/L1–L4 的"已收口"只有实现者自己的套件作证，缺第二轮专项守门。→ **已派，见下**。
- **F3（已修）**：RED 自述「去重后 63 条：×3 + ×60」自相矛盾。实测构成为 **×3 + ×63 = 66**，已按失败案例库的规则改写成"命令 + 读数位置"。
- **F4（已修）**：`status` 由 `ready` 改为 `verifying`；RED/GREEN 两份证据补上**标注为补记**的来历头（不伪造运行时间），并在 RED 段注明证据记录于 13 例版本。
- 另指出一处跨任务事实错误（`android/README.md` 的 APK 体积/dex 数已过期）——已修，并改为**不在 README 复制易漂移的数字**，改指守门证据为准。

### 2. `db-migration-reviewer` 第二轮

完整报告：`artifacts/evidence/T-022-verification-db-review-round2.md`。结论 **PARTIAL**，但**数据库守门维度全部 PASS**：H1/H2/M1/M2/L1/L3/L4 判 **CLOSED**，L2 判 PARTIAL，另提三项新的低级别发现。

| 新发现 | 处置 |
| :--- | :--- |
| **N1** `BranchDao.read` 的 javadoc 仍在描述已被本类取代的异常路径（同一 hunk 内自相矛盾，且这句正是本仓库最强调的"调用方义务"条款） | **已修**：javadoc 改为"本类在 `readSource` 中先判并抛 `IllegalStateException`（含契约原文与分支 id）；`BranchSource.restore` 只在快照非空时才被调用"，并以过去时保留变更原因 |
| **N2** 「分组归库层 / 内容归领域层」的表述与代码不一致：`start < end` 其实**两层都判**；且"过 CHECK 却被领域拒"的子集零覆盖，而原用例的 `(4,4,…)` 样本**恰是 CHECK 所拒**，注释把分类写错了 | **已修**：① 三处（schema 注释 / README / 技术方案 §5.2）改写为**如实划界**——库层判分组与方向，其中方向是**刻意且唯一**的重叠（理由：让方向颠倒的行在写入时即被拒，而不是先落库再让整个主题因一条坏行读不出来；两层结论一致故不产生分叉）；内容规则只在 `ofRange` 一处判定。② 用例样本换成 `(0, 4, "ab", …)`——**通过 CHECK**（四列齐全且 `0 < 4`）而**只被领域层拒绝**（quote 长度 2 ≠ 跨度 4） |
| **N3** L4 的处置动作没有 owner，写下的"首发前必须处置"不会再有人翻出来 | **已修**：建表入场清单 §3 点名承接者（首个发行版的版本切割任务 `T-2026-09-20-security-release-core-hardening-018` 的发布检查项），并写明在该限制被处置或明确豁免并记录之前不得宣告可发布 |

守门明确列出本轮**未**验证的部分：未上机跑 instrumentation（采信时间戳与 SHA-256 证明其后无源码改动）、未复核 lint/guard/javadoc 证据文件内容、未做变异测试（由 verifier 完成）。这两轮的分工是刻意的：专项守门判数据库轴，verifier 判任务合规与证据自证，互不代判。

### 3. `db-migration-reviewer` 第三轮（只读确认：判其自身 N1–N3 是否真收口）

第三轮不提新议题，只做确认 + 复核新引入样本的语义。结论 **PARTIAL**，但明确写下「**数据库轴：可以判 PASS**（引擎判定／版本与顺序／约束与完整性／UTC／建表入场记录全部 PASS）；本轮总评仍为 PARTIAL，只因 N4–N6 属证据与措辞层、尚未收口」。

> **归档位置**：本轮**没有**另立报告文件——只有本节的表格是它的归档记录（第一轮 `T-022-verification-db-review.md`、第二轮 `T-022-verification-db-review-round2.md` 各有独立文件）。不另立文件是刻意的：把一份转述整理出来的内容冠以「第 N 轮报告」的名字，会让后来者以为那是守门的原样输出。要原始措辞请重派该守门。

> **行号口径说明（勿按现在的树直读）**：本节表格里的行号是**第三轮评审当时**的树状态。本轮 N5 的 javadoc 改写使 `readSource` 整体下移一行，故现在应读作：锚点分组判 `:206-213`、`ofRange` `:215-226`、空快照前置判 `:232-238`（`if` 在 `:232`）、`readSource` 起于 `:200`、`BranchSource.restore` 调用在 `:239`。与 `T-022-restore-location.txt` 的结论段（`:228`/`:239`）一致——那份是派生的，已自动跟着位移；本节是引用，故只能标注，不能跟着改。

| 项 | 判定 |
| :--- | :--- |
| **N1** | **CLOSED** —— `BranchDao.java:156-170`；`:162-169` 已描述 `readSource` 的前置判，`:164-166` 旧行为保留为过去时，前置判本体在 `:231-237` |
| **N2** | **CLOSED（含 N6 残余）** —— `TreeLearnSchema.java:265-290`、`android/README.md:233-246`、技术方案 §5.2:114 三处划界一致；样本在 `:296`；`BranchAnchor.java:65-70` 确认它只被长度不符拒绝 |
| **N3** | **CLOSED** —— 建表入场清单 §3:37 已点名 `T-2026-09-20-018` 的发布检查项 |

第三轮新发现（均为低，属证据与措辞层）与本轮处置：

| 新发现 | 处置 |
| :--- | :--- |
| **N4【低】** `T-022-restore-location.txt` 的**结论段**引用旧版行号（`:224`/`:235`/`BranchDao.java:171`/`TreeLearnSchema.java:311`），而同文件正文命令输出为 `:227`/`:238`/`:174`/`:330` —— 只有这段手写文字没跟着重出，「结论与自身输出不一致」，与首轮 H2 同类 | **已修（治因，不治表象）**：不再重写那几个数字，而是把**结论段整个改为从各命令的捕获输出机械生成**（`hit_of` / `count_of` / `files_of` 读捕获文件）。重出后结论自动跟着源码位移走——本轮 N5 的 javadoc 改动让行号再移一位至 `:228`/`:239`，文件自行反映——此后不可能再漂移。另新增两处可机械核验的派生事实：androidTest 侧真实调用 **0 处**、`:data` 命中集只落在 **1 个文件** |
| **N5【很低】** `BranchDao.java:162-163` 写 `readSource` "checks for that before it does anything else"，实际顺序是先判锚点分组（`:205-212`）与 `ofRange`（`:214-225`），空快照判在 `:231` | **已修**：改为「`readSource` 先在四个锚点列上重述锚点规则，然后才读快照 id；为空则抛 `IllegalStateException`」，不再声称顺序特权 |
| **N6【低】** N2 的新样本 `(0,4,"ab",…)` 仍**种在 `PRAGMA ignore_check_constraints=ON` 块内**，于是「库层放行」这半边仍只能从 DDL 静态推导 | **已修（改成设备证据，而非改注释措辞）**：把该行**移出 PRAGMA 块**，在 CHECK **打开**的状态下种植。`plantFork` 走 `execSQL`，若库层真拒这种形态就会抛 `SQLiteConstraintException` 并让用例当场失败——「过 CHECK」由此成为可执行事实而非推断。用例注释同步写明这一用意 |

第三轮另做了一次对 N2 语义的独立复核：在**宿主机 SQLite 3.53.1** 上确认 CHECK 接受 `(0,4,"ab")`、按 `code 275` 拒绝 `(4,4)`/`(4,0)`/仅回退三行；守门自己标注「**这不是设备证据**」（设备无 `sqlite3` 二进制）。上表 N6 的处置正是为把这一条补成真的设备证据。

本轮另修一处**归档忠实度**问题：第二轮报告文件（`T-022-verification-db-review-round2.md`）的 `§2` 处置文字是归档时补写的，其中 N1/N2/N3 标着「已修」，但那些修复到**第三轮**才成立，`§2 N2` 的样本替换也被写成「本轮修复」。已在文件页首加「归档时序说明（阅读前必读）」，三处标签改为「已修（第三轮复核判 CLOSED）」，`§3` 末条明说该样本的真机用例是第三轮才补的。——「归档时把后来的状态写进当时的报告」会让后来者把治理时序读错，比数字错更难发现。

## Verification report

### verifier #3 批量复核（2026-09-24，独立于本任务实现者）

- Verdict: **PASS（设备项 deferred）**。verifier #2 的静态判定全部维持；本轮新鲜门禁复跑全绿。
- Fresh evidence（2026-09-24）: `artifacts/evidence/P3-B1-gates-2026-09-24.txt`（全模块 JVM 套件 + lint + `:app:assembleDebugAndroidTest` 编译，BUILD SUCCESSFUL / EXITCODE=0；全库 188 套件 1725 tests / 0 失败）；`P3-B1-no-kotlin-2026-09-24.txt`（EXITCODE=0）；再水合探针 `P3-B1-rehydration-probes-2026-09-24.txt` R1——`BranchSource.restore` 的生产调用点唯一命中 `BranchDao.java:239`（只读映射内），其余命中均为注释/javadoc，机械检索 EXITCODE=0。
- 判定依据: 验收项 1-8 中凡不需要设备的项已由前两轮独立复核关闭且本轮无新事实推翻； instrumentation 项（真实 SQLite 拒绝/级联/逐字段重读）编译级通过、执行留待设备。
- Gaps（不变，均 deferred）: 全部 instrumentation 执行项 —— `deferred — 本轮不使用模拟器（用户指令 2026-09-21）`，用例已编译（androidTest BUILD SUCCESSFUL）。
> **最新结论：见本段末尾「第九轮独立复核结论」（verifier #2，2026-09-21 第四轮）。下方第一个 - Verdict: 是历史轮次的记录，不是当前判定。**

- Verdict: **VERIFYING（不得报 PASS）**。八条验收条件**全部满足且证据可核**；两个独立方均已复核并各自在其职责轴内判定：独立 `verifier` 判「技术面成立」（F1–F4 与跨任务发现均已修），`db-migration-reviewer` 第二/第三轮判「**数据库轴可以判 PASS**」。
- **不报 PASS 的原因现在只剩一条，且不是技术原因**：按仓库规则，**verifier 的 PARTIAL 不能由实现者自行改判为 PASS**，须由 verifier 或用户给出 PASS。此前那条「规模口径未获事先批准」的阻塞**已由用户 2026-09-21 的裁定解除**（口径「非空非注释」，本任务 386 < 500，未越线）。
- Evidence: `artifacts/evidence/T-022-red.txt`（补记来历头 + javac 自报 66 = ×3 + ×63，复算命令见 RED 段；**RED 的补记头保留是对的**——那份捕获无法重跑，而补记头明确说明内容不是补记时产生的）、`T-022-green.txt`（52 例 / 0 失败 / `EXITCODE=0`，N6 修改后整份重出，头部为本次运行自带）、`T-022-tests.txt`（15 例 T-022 + 24 例 T-004 + 1 例启动，含 XML 路径与 XML SHA-256）、`T-022-core-tests.txt`（`:core:test --rerun` 131 例，逐文件计数取自 XML）、`T-022-core-javadoc.txt`（exit 0）、`T-022-restore-location.txt`（`BranchSource.restore` 调用点仅 1 处 + `:data` 零 DDL，逐条命令带 `EXITCODE=`、被检文件带 SHA-256；**结论段由生成脚本从捕获输出机械派生**）、`T-022-checks.txt`（`:app:lintDebug` 通过）、`T-022-guard.txt`（PASS 13 行 / exit 0）、`T-022-guard-selftest.txt`（40 例 PASS / exit 0）、`T-022-validate-tasks.txt`。**规模裁定证据**：`sizing-adjudication-2026-09-21.txt`（四个任务的复算比对）。**复核报告**：`T-022-verification-verifier.md`（独立 verifier）、`T-022-verification-db-review-round2.md`（数据库守门第二轮，页首含归档时序说明）、`T-022-verification-db-review.md`（首轮）。设备 `emulator-5554` / API 36。
- 验收条件逐条：
  1. 同一命令先 RED（exit 1，编译级）后 GREEN（exit 0）——✓
  2. `(branch_id, ordinal)` 重复被真实 SQLite 拒绝且不留半行——✓（按 `code 2067` 断言成因，并断言被拒行的 id 查不到）
  3. 同主题第二个根分支被判 FAIL——✓（按 `code 2067` + `branches.topic_id`）
  4. 删除主题后级联清空且 `foreign_key_check` 无结果——✓（另有一条「删子分支只清自己的子树」）
  5. 关闭重开后逐字段相等——✓（`Topic`/`Branch`/`Message` 各一条，且字段在往返前被改到非默认值，否则断言可能在默认值上碰巧成立）
  6. 非根分支读回带完整 `BranchSource`，整条回答分叉偏移为空、版本引用非空——✓
  7. **再水合契约**：`BranchSource.restore` 只出现在 `BranchDao` 只读映射内——✓（`T-022-restore-location.txt`，逐条命令带 `EXITCODE=`）
  8. `db-migration-reviewer` 复核——✓ 三轮均已派出，第三轮明确判「数据库轴可以判 PASS」；独立 `verifier` 亦已派出并收口 F1–F4（`artifacts/evidence/T-022-verification-verifier.md`）
- Changed/deleted assertion rationale and equivalent behavior coverage:
  - 删除 `roleInvariantsAreEnforcedByTheStoreNotOnlyByTheType`——**它造不出被测输入**（`:core` 的 `Message` 工厂与 `Guard` 已保证「AI 无正文 / USER 必带正文」，因此无法构造非法行），留着只会是一条永远通过的假见证。等价覆盖：`messages` 的角色/内容 CHECK 由 T-004 的 `messageContentIsConstrainedByRole` 按 `code 275` 断言；「存储层不得写出空身份」由本任务的 `theStoreRejectsANullIdentityOnANotNullColumn` 断言。
  - 删除 `halfFilledAnchorsAreRejectedByTheStore`（本轮守门 H1 期间新增，随即**归位到 T-004**）：它断言的是**库层 CHECK**，而该 CHECK 是 T-004 的 DDL 产物；留在 T-022 就是「同一条规则两处实现」——将来改 CHECK 要同步两个套件，必有一处被忘。等价覆盖：T-004 的 `forkAnchorColumnsAreAllOrNothing`（同一批 instrumentation 运行，证据同源 `T-004-green.txt`）。
  - 本任务改为覆盖**属于自己**的那一半：`readSourceFailsLoudOnARowTheCheckWouldHaveRejected`，用 `PRAGMA ignore_check_constraints=ON` 单语句关掉 CHECK 造出三类坏行，验证读路径 fail loud 且消息可诊断（而不是静默降级）。
  - 两处改名以消除名实不符：`readingPositionColumnsArePersistedAsOffsetsNotAsScrollState` → `readingPositionColumnsExistAsIntegerOffsets`（并真正断言列类型）；`theDaoNeverEmitsDdlAndNeverWritesNullableIdentityColumns` → `theStoreRejectsANullIdentityOnANotNullColumn`。
- Gaps:
  - `context_snapshots` / `answer_revisions` / `generation_operations` / `ai_artifacts` 四表的 DAO 不在本任务（T-2026-09-21-023）；本任务的夹具直接写 SQL 造这两类行。
  - **两步写（insert → 快照行 → setContextSnapshot）目前只是调用方义务**，由 023 的首题事务保证；本任务把它写成 `insert`/`setContextSnapshot` 的 javadoc，并让读到半成品时抛出的异常**带契约原文与分支 id**（不再落到 `Guard.id` 的通用消息）。
  - `:core` 的 `Branch.restore` / `Message.restore` 属本任务新增的共享 API，其**外部使用面**要等 023 与 T-009 落地后才能确认没有被写路径误用（本任务已用机械检索保证 `:data` 内只有一处调用、且在只读映射里）。
  - **`reading_*` 四列零生产者零消费者**（守门 M1）。`reading_anchor_message_id` / `reading_anchor_revision_id` / `reading_list_item_offset` / `reading_viewport_offset` 目前只有 schema 定义与 T-004/本任务的列存在性断言；`Branch` 没有对应字段，因此本任务**无法**做任何持久化往返。技术方案 §11 要求的阅读位置读写属 **T-009**。本任务只断言「四列存在且为 INTEGER」，用例名不再承诺未验证的行为。
  - **修复只对新装生效（守门 L4）**。`branches` 的 CHECK 修正只走 `onCreate`：任何已经用旧 V1 DDL 建过库的设备（开发机/测试机残留库、已安装的调试包）会永久保留旧 CHECK，而 `DATABASE_VERSION` 仍是 1、`FORWARD_MIGRATIONS` 为空，无法靠迁移修好。当前 V1 未发布（无 tag、versionCode 1）故可接受，但**首个发行版前必须处置**：要么清库，要么改为 V2 + 前向迁移（SQLite 重建 `branches` 表并复制数据）。
  - **跨主题误删边界（守门 L3）**。`branches.source_message_id` / `source_revision_id` 是 `ON DELETE CASCADE`，而 schema 与领域都**不保证** source 与被分叉主题同属一个 topic。越过该规则的行会让「删主题 A」连带删掉主题 B 的分支。本任务 DAO 制造不出这种行（创建路径从所在主题取 source），但 023 的 `execSQL` 夹具与未来 DAO 可以。已写入 `TreeLearnSchema` 注释；**同主题规则归写入路径（T-2026-09-21-025）**，库层无法用 CHECK 表达。（该处原指向 T-2026-09-21-023，023 于 2026-09-21 按用户裁定切分后不再承担写入路径，`TreeLearnSchema` 注释已同步改指 025。）
  - **`restore` 惯例缺可执行守门（守门 Q2 建议）**。`android/README.md` 写下「`BranchSource.restore` 在全 `:data` 只出现一次」这样一条**永久维持性声明**，支撑它的却是 `T-022-restore-location.txt` 里的一次性 shell 检索。建议并入 `android/scripts/` 的守门脚本，并配一条「故意多写一处必须判 FAIL」的自检用例。属后续项，本任务不夹带实现。
- Risks: 列名与 schema 常量漂移（已用「列名一律引用常量 + 机械检索 DDL 产地」压住）；只读映射被写路径误用（`restore` 命名 + 单点检索）；`:core` 的 `updatedAt` 新不变量若被后续任务放宽会重新失去校验；`readSource` 的 fail-loud 分支在库层 CHECK 生效后**只能靠关掉 CHECK 的夹具触发**（本任务已用该手法钉住，若将来删掉该用例，这条防线就只剩静态阅读）。

### 第 4 轮独立复核结论（verifier，2026-09-21；本轮不使用模拟器）

- Verdict: **FAIL（记录层；数据库轴与设备轴此前均已复核为真）**。状态保持 `verifying`，不置 `verified`。
- 本轮亲自复现 / 回读（`VERIFY-static-recheck-2026-09-21.txt`）：
  - 规模：4 个 DAO 文件非空非注释 **349** 行，与本记录逐字相同（复算见 `sizing-adjudication-2026-09-21.txt`）。
  - 再水合契约（验收 7）：`rg -n 'BranchSource\.restore|AnswerRevision\.restore' android/data/src/main/java` → `BranchSource.restore` 的生产调用点**只有 `dao/BranchDao.java:239` 一处**，其余命中均为注释/javadoc；与「只允许出现在 `BranchDao` 只读映射内」一致。
  - RED 构成：`T-022-red.txt` 顶格 `找不到符号` **63** + `程序包com.treelearn.data.db.dao不存在` **3** = **66**，与该文件第 409 行的 `66 个错误` 一致（F3 已把旧的「3+60」自相矛盾改对）。
  - 设备轮次（2026-09-21 20:49，**在本轮「不使用模拟器」指令之前**由本 lane 执行）：`TopicBranchMessageDaoInstrumentedTest` **15/15 通过**。
- 未通过项：
  1. **GREEN 记录引用的证据不支持该句**：本文件写「JUnit XML `tests="40"` … 构建输出 `Starting 40 tests` / `Finished 40 tests` … 见 `artifacts/evidence/T-022-tests.txt` / `T-022-green.txt`」；实测 `T-022-tests.txt:6` 是 `<testsuite … tests="52">`，`T-022-green.txt:138/141` 是 `Starting 52 tests` / `Finished 52 tests`（第 6 行补记头写「= 40 例（本轮加入 T-023 后为 52 例）」）。两份被引证据里都**没有** `tests="40"` / `Starting 40 tests`。
  2. **REFACTOR 段仍是 `PENDING`**（`Cleanup: PENDING` / `Regression evidence: PENDING`）；REFACTOR 属可选，但留空未说明理由。
  3. `android/README.md` 中「`BranchSource.restore` 在全 `:data` 只出现一次」是**永久维持性声明**，目前只靠一次性 shell 检索支撑；本文件已把它登记为后续项（非阻塞，已登记）。
- 验收条件对照：②③④⑤⑥⑦ 由设备轮次（20:49）与静态检索复核通过；①（instrumentation 先 RED 后 GREEN）**只复核了 GREEN**，RED 需要移出生产类并要求设备 → `deferred — emulator not used this round (user instruction 2026-09-21)`；⑧（`db-migration-reviewer` 三轮）由本文件记录的三份专项报告承担，本轮未重跑。
- 翻转条件：① 把 `tests="40"` / `Starting 40 tests` 改成与所引证据一致的写法（或按新证据重录）；② 给 REFACTOR 段写明「无重构 / 无回归差异」或补证据。

### 记录修复轮（record repair，2026-09-21 下；本轮不使用模拟器）

> 本节由**记录修复 lane**（与实现同方，**不是**独立 verifier）填写：只修记录与引用，不改生产代码、不改测试、不改判据。
> 上方「第 4 轮独立复核结论」原样保留，本节是回应而不是覆盖。

第 4 轮列出的未通过项逐条处置：

| 第 4 轮发现 | 处置 | 修复后实测（命令 + 读数位置） |
| :--- | :--- | :--- |
| ① GREEN 引用的证据不支持 `tests="40"` / `Starting 40 tests` | 按被引证据的真实内容改为 52，并写清 52 的构成与本任务 15 例的查法 | `Select-String -Path artifacts\evidence\T-022-tests.txt -Pattern 'tests="'` → 第 6 行 `tests="52"`；`Select-String -Path artifacts\evidence\T-022-green.txt -Pattern 'Starting|Finished'` → 第 138 行 `Starting 52 tests`、第 141 行 `Finished 52 tests` |
| ② REFACTOR 段留 `PENDING` 未说明理由 | 写明「无结构性重构」及理由，并把漏填如实标为漏填 | 本文件 `### REFACTOR — optional` 段 |
| （附带）`:core:test` 的 131 例被当作当前值 | 标注为「本任务验收时快照」，并给出全库当前实测值 | `artifacts/evidence/VERIFY-core-test-counts.txt` 第 15 行 `TOTAL suites=13 tests=154 failures=0 errors=0 skipped=0` |
| 验收条件 ① 的 RED 半边 | `deferred — emulator not used this round (user instruction 2026-09-21)`；既不判 PASS 也不判 FAIL | 「第 4 轮独立复核结论」的验收条件对照段 |

- **仍未处置（非阻塞，已登记）**：`android/README.md` 的「`BranchSource.restore` 在全 `:data` 只出现一次」是一条**永久维持性声明**，目前仍只由一次性检索支撑（第 4 轮第 3 条）。属后续项，本任务不夹带实现。
- 本轮**未**执行任何设备命令（无 `connectedDebugAndroidTest`、无 `adb`、无模拟器）。
### 第九轮独立复核结论（verifier #2，第四轮；2026-09-21；本轮不使用模拟器）

> 本轮针对**记录修复轮（record repair）**的处置做独立复核。verifier 只读。
> 自产证据：`VERIFY5-jvm-suites.txt`、`VERIFY5-androidtest-javadoc-depdirection.txt`。

- Verdict: **PARTIAL**。① 记录层修复基本经复核为真；② **本任务的验收条件几乎全是设备判据**（第 97–104 行全部为未勾选 `[ ]`），按用户 2026-09-21 指令记为 `deferred`；③ 本轮**发现一处新的行号引用错误**（见下）。状态保持 `verifying`。
- 记录修复轮各项的独立复核（命令 + 读数位置）：

| 修复项 | 独立复核结果 |
| :--- | :--- |
| `tests="40"` / `Starting 40 tests` → 52 | **为真**。`T-022-tests.txt:6` = `<testsuite name="com.treelearn.app.AnswerRevisionGenerationOperationDaoInstrumentedTest" tests="52" …>`；`T-022-green.txt:138/141` = `Starting 52 tests` / `Finished 52 tests`、`:154` = `EXITCODE=0`；两份被引证据里已无 `40` |
| 本任务 15 例的查法 | **为真**。`T-022-tests.txt:8` = `--- T-022 cases (15) ---` |
| REFACTOR `PENDING` → 说明原因 | **为真**。第 156 行写明「无结构性重构 …… **原写 PENDING 属漏填**」，非「有待重构」 |
| 131 标注为验收时快照 | **为真**（第 127 行）。本轮实测 `:core` 为 **18 套件 / 187 用例**（`VERIFY5-jvm-suites.txt`），131 与 154 均已过期 |
| 验收条件 7（再水合契约） | **为真，本轮亲自复核**。`rg "\.restore\(" android/data/src/main/java` → `AnswerRevisionDao.java:112`、`BranchDao.java:175`（`Branch.restore`）、`BranchDao.java:239`（`BranchSource.restore`）、`MessageDao.java:85`，**四处全部位于只读映射 `read(Cursor)` 内**；写路径无任何 `restore` 调用 |

- **本轮新发现（记录层缺陷，中低）**：第 112 行写「与编译器自报行（`artifacts/evidence/T-022-red.txt:399` 的 `66 个错误`）一致」，但**该文件第 399 行是 `          ^`**。`66 个错误` 实际出现在 **第 409 行**与**第 744 行**（另在第 7 行的补记来历头里重述）。**数量本身正确**——本轮复算：顶格 `找不到符号` **63** + `程序包com.treelearn.data.db.dao不存在` **3** = **66**，与第 409 行一致；错的只是**行号**。第 276 行（第 4 轮小节）引用的是正确的 **409**，故这是第 112 行漏改的孤立错引；成因很可能是该证据文件后来被前置了 10 行来历头、而引用未回读。**建议改为 `:409`。**
- 本轮亲自复现（自产证据）：
  - `cd android; .\gradlew.bat :core:test :ai:testDebugUnitTest :data:testDebugUnitTest :tree-layout:test :app:testDebugUnitTest --rerun-tasks --no-daemon --console=plain` → `BUILD SUCCESSFUL`、exit 0；`:data` **2 套件 / 13 用例 / 全 0**（`VERIFY5-jvm-suites.txt`）。
  - instrumentation 源码**能编译**（`> Task :app:compileDebugAndroidTestJavaWithJavac` 成功、`BUILD SUCCESSFUL`、exit 0）；`android/app/build/outputs/androidTest-results/connected/debug` 文件数 **0**——只证明编译，不证明执行。
- **设备判据（deferred）**：第 97「同一 instrumentation 命令先 RED 后 GREEN」、第 98「`(branch_id, ordinal)` 重复插入被真实 SQLite 拒绝」、第 99「同主题第二个根分支被判 FAIL」、第 100「删除主题后级联清空且 `foreign_key_check` 无结果」、第 101「关闭重开后逐字段相等」、第 102「非根分支读回必须带 `BranchSource`」、第 104「`db-migration-reviewer` 按 Android SQLite 复核」——全部记为 `deferred — emulator not used this round (user instruction 2026-09-21)`。历史设备轮次（20:49，在指令之前）曾记 15/15 通过，但那不是本轮证据。
- 规模：`estimated_production_lines: 320`；按用户裁定口径实测 **386 < 500 硬上限**（DAO 349 + `:core` 37），故**不在未获批越线清单内**，仅高于 200 软上限（verifier 警告级）——与本文件第 170 行「`estimated_production_lines` 已过期」互不矛盾。

## Handoff notes

- Completed: `:data` 的 `com.treelearn.data.db.dao` 四类（`TopicDao`/`BranchDao`/`MessageDao`/`Rows`）；`:core` 的 `Branch.restore`、`Message.restore` 与 `Branch` 构造的 `updatedAt` 校验；T-004 的 `branches` CHECK 两次修正（去掉对 `anchor_hash` 的强制 → 补四字段同生同灭 CHECK）；`:app` 的 15 例真实设备验收；守门 H1/H2/M1/M2/L1–L4 全部收口；三轮 `db-migration-reviewer` 与独立 `verifier` 的发现（H1/H2/M1/M2/L1–L4、F1–F4、N1–N6）全部处置；证据文件与四个生成脚本（`artifacts/evidence/.gen-t022-restore-location.sh`、`.gen-android-test-cases.sh`、`.gen-guard-evidence.sh`、`.gen-sizing-adjudication.sh`，均为幂等）。规模口径已由用户裁定（`.trae/rules/harness-tasks.md` 例外表 + `artifacts/evidence/sizing-adjudication-2026-09-21.txt`）。
- In progress: 等待 `verifier` 轴给出 PASS（实现者不得自行改判）；其余实现、证据与措辞均已收口。
- Blocker: 无。（规模口径已裁定：非空非注释 386 < 500，未越线；原先以此为唯一阻塞项，现解除。）
- Unblock condition: 不适用。
- Next steps: 解锁 T-2026-09-21-023（其余四表 DAO 与首题原子创建事务，它需要本任务的 `DatabaseTransactionRunner` + `BranchDao.setContextSnapshot` 三步写）、T-009（主题/分支生命周期，同时接手 `reading_*` 阅读位置）、T-007（上下文快照消费）。
- Watch-outs:
  - **不要在本任务或下游新增 DDL**；列名一律引用 `TreeLearnSchema` 常量。
  - **不要为「让写路径跑通」而使用 `restore`**：它只属于只读映射。写路径必须走 `:core` 的工厂与变更方法（测试夹具也一样——本任务已把 4 处创建用法改为 `BranchSource.fromOperation(..., GenerationState.STABLE, ...)`）。
  - **非根分支的两步写是硬要求**：`BranchDao.insert` 故意不写 `context_snapshot_id`，读到一半的行会 fail loud。023 必须把 insert → 快照行 → `setContextSnapshot` 放在**同一事务**里。
  - `branchRoundTrips*` 这类往返用例的字段必须**先改成非默认值再写库**，否则断言会在默认值上「碰巧正确」。
  - 整条回答分叉的 `anchor_hash` 为 null **是合法的**（`BranchAnchor.wholeAnswer()` 的设计）——但**不要把它加回去当唯一约束**。正确的形态是四字段同生同灭；「只去掉 hash 必填」曾让半截锚点变成合法行（守门 H1）。
  - **夹具不得制造领域层产不出的行**：T-004 的 `insertChildBranch` 原先写 quote+hash 却不写偏移，被新 CHECK 当场拒绝——这类夹具会让测试在一个不存在的状态上「通过」。
  - 写带锚点的原始 SQL 时，**不要**顺手传 `context_snapshot_id` 到一个不存在的快照：那会先撞外键，断言就不再是关于锚点 CHECK 的了。
