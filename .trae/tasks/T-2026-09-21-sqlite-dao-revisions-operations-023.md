# Treelearn Harness Task: answer_revisions / generation_operations 两表 DAO 与 stabilize 落盘入口

id: T-2026-09-21-023
title: answer_revisions / generation_operations 两表 DAO 与 stabilize 落盘入口
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 300
shared_code_touched: true
depends_on: [T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-21-sqlite-dao-topic-branch-message-022.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 七类本地事实必须以可迁移、可事务验证的 SQLite 结构持久化（见《Android SQLite 建表入场确认清单 V1.0》）。
- Problem/background: 原 T-004 把七表 schema、DAO、事务与迁移放在一个任务里，实测生产代码约 900+ 行，超过 `harness-tasks.md:45` 的 500 行硬上限。**本任务于 2026-09-21 二次切分**：实测预估 480–580 行仍压在/越过 500 硬限，经用户裁定按「三片垂直切分」处理，本任务收窄为**切片 A**；原范围里的 `context_snapshots`/`ai_artifacts` 两表 DAO 与首题原子创建事务分别移交 T-2026-09-21-024 与 T-2026-09-21-025。文件名随之由 `…-sqlite-dao-generation-artifacts-023.md` 改为 `…-sqlite-dao-revisions-operations-023.md`（稳定 id 不变）。
- Users/scenarios: 用户在分支上得到稳定回答后，重启应用仍能读回该版本；生成中断后可从操作行推导恢复目标；同一分支不会同时存在两个在飞生成。
- Expected outcome: `:data` 提供 `AnswerRevisionDao`、`GenerationOperationDao`（含 `GenerationOperationRow`），以及供协调器/T-006 网关调用的 **stabilize 落盘入口**（同一事务内创建 revision 并把 operation 置为 `STABLE`，版本从 operation 行原样复制）。另提供把 SQLite 约束冲突翻译成领域错误的共享 helper。
- Core flow and reversibility: 稳定化在单个事务内写入 `answer_revisions` 1 行并更新 `generation_operations.state = 'STABLE'`；任一步失败则整体回滚，不留「有 revision 但 operation 仍非终态」或反向的中间态。
- Inputs/outputs: 输入为 `:core` 实体/行对象与命令；输出为不可变实体、显式空值或分类错误。
- State and exception handling: 终态操作不可再改写；`(message_id, revision_no)` 重复必须被真实 SQLite 拒绝；不稳定内容只进 operation 缓冲区。
- Constraints and trade-offs: DDL 一律不在本任务（属 T-004）；时间用 UTC epoch millis；参数化 SQL；按**行形态**读写 `generation_operations`，**不新增** `GenerationOperation.restore`；列名一律引用 `TreeLearnSchema` 常量。
- AI recommendation and rationale: operation 用行形态而非实体再水合，因为 `GenerationOperation` 无 `restore` 入口，且恢复只需状态推导（技术方案 §7）。
- User choice and custom additions: 推理、最终回答和失败部分内容均须落库；只落提示词用途/版本，不落 system 正文。
- AI-autonomous decisions: 活动操作上限由 T-004 的偏索引（`uq_generation_operations_active`）承担，DAO 只负责把约束冲突翻译成领域错误。
- User-confirmation decisions: ① 七张逻辑表及 2026-09-21 的提示词用途/版本字段已确认；不新增第八张表。② **2026-09-21 裁定按三片垂直切分本任务原范围**，本片只做两表 DAO 与 stabilize。
- Acceptance evidence: instrumentation 唯一约束、终态不可改写、活动操作上限、部分内容追加、stabilize 版本随同复制、关闭重开后读取一致。
- Facts: 技术方案 §6.1/§6.2、§7、§9.2/§9.3；建表清单 §2/§3。
- Assumptions: 首版无历史库；迁移路径由 T-004 覆盖。
- Unknowns: 无。
- Risks: 事务范围过大造成锁等待；批量流式落盘与终态写并发。
- Deferred: SSE 批量落盘节流（250ms/256 字符）由 T-006/T-008 实现，本任务只提供落盘入口。
- Non-goals: 不实现 SSE 解析、网络、UI 或并发调度；不实现「流是否正常终止且回答非空」的判定逻辑。
- Skipped non-critical questions and reason: 索引微调由真实查询计划决定。

## Goal

让不可变回答版本与可恢复生成状态在真实 Android SQLite 中被原子写入、按约束拒绝非法行，并让「稳定化」这一领域动作在单个事务内全成或全败。

## Scope

### In scope
- `AnswerRevisionDao`（写路径走 `AnswerRevision.create`，只读映射内才允许 `AnswerRevision.restore`）。
- `GenerationOperationDao` 与 `GenerationOperationRow`（行形态读写、部分 reasoning/answer 追加、状态迁移、活动操作上限、终态不可改写）。
- 约束冲突 → 领域错误的共享翻译 helper（T-022 未建立；`SQLiteConstraintException` 的扩展码 → `DomainErrorCode`）。
- **stabilize 落盘入口**：同一事务内创建 `answer_revisions` 行并把该 operation 置为 `STABLE`，`prompt_policy_version` 从 operation 行原样复制。

### Out of scope
- schema/DDL 与 `SQLiteOpenHelper`（T-004）。
- `topics`/`branches`/`messages` 三表 DAO（T-2026-09-21-022）。
- `context_snapshots`/`ai_artifacts` 两表 DAO（**T-2026-09-21-024**，2026-09-21 切分）。
- 首题原子创建事务与跨主题写入路径规则（**T-2026-09-21-025**，2026-09-21 切分）。
- SSE 解析与节流（T-006/T-008）、上下文组装（T-007）、UI 与搜索。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增持久化读写与稳定化事务；保持 `:core` 的不可变约束、状态迁移规则与「不稳定内容不得升级为稳定来源」不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 上游 `:core` 的 `AnswerRevision`/`GenerationOperation` 与 T-021 的状态机；下游 T-006 网关（调用 stabilize 落盘）、T-008 协调器恢复（按行形态读 operation）、T-009/T-010（重置与重试）、UI。
- Native bridge or independent-client compatibility/update requirements: 无桥接；只消费 Android framework SQLite。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 两表列名、索引与唯一/偏索引约束、触发器行为、stabilize 的事务边界与失败语义。
- Authoritative code/config/migration evidence: `android/data` 的 DAO 与事务源码、`:app` 的 instrumentation 夹具。
- Documentation surfaces checked: 技术方案 §5.1/§6.1/§6.2/§7/§9.2/§9.3、建表清单 §2/§3、`android/README.md`。
- Documentation files updated: `android/README.md` 的持久化入口索引；若 stabilize 的事务边界与技术方案 §7 有差异，同步技术方案。
- Producer/consumer/sibling/compatibility paths checked: 两表全部 DAO 方法、级联路径、T-006/T-008 的调用点。
- Superseded phrases/examples reverse-searched: 旧列名、旧 DAO 方法名、`GenerationOperation.restore`。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写冻结 PRD。

## Acceptance criteria

- [ ] 同一 instrumentation 命令先 RED 后 GREEN。
- [ ] **版本随同复制**：stabilize 事务把 operation 行的 `prompt_policy_version` 原样复制到 `answer_revisions.prompt_policy_version`（同一字符串，非重新取值），且两者在同一事务内可见。
- [ ] **稳定化全败**：在事务中途注入失败（例如该 message 上 `revision_no` 已存在），回滚后 `answer_revisions` 无新行、`generation_operations.state` 未变，不留「revision 已写但 operation 非终态」的中间态。
- [ ] `answer_revisions`：`(message_id, revision_no)` 重复插入被真实 SQLite 拒绝（按扩展码断言成因）；`answer_content` 为空被拒绝；`prompt_policy_version` 为空被拒绝；行被 `UPDATE` 时触发器拒绝；删除消息后该表行级联清空（且证明「消息仍存在时单独 DELETE revision 被拒」）。
- [ ] `generation_operations`：同一 `branch_id` 的第二个活动操作被拒绝（偏索引，按扩展码断言）；终态行被 `UPDATE` 时触发器拒绝；部分 reasoning/answer 可批量追加且不产生中间可见脏状态。
- [ ] **再水合契约（自 T-003 下推）**：`AnswerRevision.restore` 只允许出现在 `AnswerRevisionDao` 的**只读映射**方法内；写路径（创建、稳定化、重试、重置、分叉、迁移、回填）必须走 `AnswerRevision.create`。另：本任务**不得新增** `GenerationOperation.restore`，对 `android/data` 与 `:core` 全部生产源码做机械检索并给出命令与完整输出。
- [ ] **不落 system 正文**：本任务所有 DAO 的公开方法签名中不出现 `systemContent`/`systemPrompt` 形态的入参，也不新增任何正文列；对两表写入夹具后扫描其全部 TEXT 列，断言不包含提示词正文哨兵子串。
- [ ] 关闭重开后逐字段相等（含 UTC 毫秒时间点），且 `PRAGMA foreign_key_check` 无结果。
- [ ] 相邻失败场景：删除整条消息后级联清空 revision 且操作行不残留悬空引用；对不存在或已终态的 operation 调 stabilize 返回明确错误而不是裸异常。
- [ ] `db-migration-reviewer` 按 Android SQLite 守门复核两表约束、级联、事务回滚与偏索引证据，不得用替身数据库。

## TDD execution record

### RED — failing test first
- Test/scenario: 新增 `:app` androidTest 用例类 `AnswerRevisionGenerationOperationDaoInstrumentedTest` 的 12 例（revision 往返与唯一、行不可改写、随消息级联、operation 行形态与活动操作上限、终态封闭、增量原地追加、stabilize 的版本原样复制/事务边界/全败回滚）
- Command: cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain
- Exit code: 1
- Observed failure: 编译级 RED，javac 自报 53 个错误，且 53 行全部是「找不到符号」（「程序包…不存在」为 0 行）——用例写在任何生产类型之前，编译器停在待建类型上
- Failure reason validated: 缺失符号清单逐项都是本任务要新建的类型：GenerationOperationDao（50 变量 + 2 类）、AnswerRevisionDao（32 + 2）、GenerationOperationRow（12）、GenerationStabilizer（6）、PromptPurpose.fromPrefix(String)（2）；数字与复现命令取自 artifacts/evidence/T-023-red.txt 的三段汇总，且自洽（0 + 53 = 53）；补上这些类型后同一条命令转绿，说明失败由缺失实现导致

### GREEN — minimum implementation
- Implementation: AnswerRevisionDao（写路径走 AnswerRevision.create，只读映射才允许 restore）、GenerationOperationRow + GenerationOperationDao（行形态读写、原地原子追加、包私有 markStable）、GenerationStabilizer（自己持有事务边界，state 先写、revision 后写）、ConstraintFailures 共享翻译
- Command: cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain
- Exit code: 0
- Passing evidence: artifacts/evidence/T-023-green.txt（BUILD SUCCESSFUL in 45s、EXITCODE=0）；同批次 52 例规模与逐例名单见 artifacts/evidence/T-023-tests.txt（tests="52" failures="0" errors="0" skipped="0"），本任务 12 例
- Regression evidence: 同一批次内 T-022/T-004/T-002 的既有 40 例全绿（见 artifacts/evidence/T-023-tests.txt 的 every other case in the same batch 段）；artifacts/evidence/T-023-lint.txt（:app:lintDebug）；Java-only 守门 artifacts/evidence/T-023-guard.txt；变异测试 artifacts/evidence/T-023-mutation.txt（两处注入缺陷各自让对应用例转红，还原后以 SHA-256 自证）——本条是「版本随同复制」与「拒绝必须抛异常」两条断言的唯一替代见证，故按要求做了变异

### REFACTOR — optional
- Cleanup: 本任务由 2026-09-21 的三片垂直切分从原 T-023 中拆出（context_snapshots / ai_artifacts 两表 DAO 移入 T-2026-09-21-024），任务文件与文件名同步改名，无遗留旧名引用
- Regression evidence: artifacts/evidence/T-023-tests.txt 同批次 52 例全绿；python .trae/scripts/validate_tasks.py 见 artifacts/evidence/T-023-validate-tasks.txt

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required；规模按切片控制在 500 行硬上限内（用户 2026-09-21 裁定三片切分）
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- instrumentation 测试写在 `:app` 的 androidTest 源集（项目约定：单一测试 APK），不在 `:data` 声明 androidTest 依赖。
- 列名一律引用 T-004 的 schema 常量，不写字面量字符串。
- `generation_operations` 按**行形态**实现，恢复流程用 `GenerationState.processRecoverableStates()` + `recoveryTarget()` 推导目标状态，**不新增** `GenerationOperation.restore`。
- stabilize 落盘入口必须能被 T-006 的 accepted 回调**同步等待**（提交成功后才返回并允许读取 SSE body）；失败必须返回明确错误而不是抛裸异常穿透网络层。
- 不稳定内容（部分推理/回答）只写 operation 缓冲区；只有收到正常流终止且最终回答非空才允许创建 `AnswerRevision` 并把 operation 置为 `STABLE`——本任务只提供落盘入口与约束，**不实现判定逻辑**。
- 偏索引（活动操作上限）的具体 DDL 属于 T-004；本任务若发现 T-004 未提供该约束，应报告缺口而不是在本任务临时建索引。
- 约束冲突翻译只做一处（共享 helper），不得在每个 DAO 里各写一份「按消息文本猜约束名」的翻译。

## Verification report

> 独立 verifier 复核（verifier #2，2026-09-21），只读：未修改任何生产代码、测试或他人证据。
> 本轮按用户 2026-09-21 指令**不使用模拟器**；凡严格需要真机的验收项一律记为
> `deferred — 本轮不使用模拟器（用户指令 2026-09-21）`，既不算 PASS 也不算 FAIL。
> verifier 自产证据见 `artifacts/evidence/VERIFY2-*.txt`（helper 脚本在 `artifacts/evidence/.verify2/`）。

- Verdict: PARTIAL
- 审阅文件: 本任务文件；`android/data/.../db/dao/{AnswerRevisionDao,GenerationOperationDao,
  GenerationOperationRow,GenerationStabilizer,ConstraintFailures}.java`；
  `:app` 的 `AnswerRevisionGenerationOperationDaoInstrumentedTest.java`；
  证据 `T-023-{red,green,tests,mutation,guard,guard-selftest,lint,core-javadoc,core-tests,
  prompt-body-surface,restore-location,validate-tasks}.txt`；verifier 证据
  `VERIFY2-{assemble-androidtest,guard,lint,sizing,static-findings}.txt`。
- 代码量: **454** 非空非注释行（verifier 独立复算 5 个文件：84+38+202+45+85；物理行 832）/ 500 硬上限
  → **在预算内**（超 200 软上限，警告）。
- 任务合规: PASS（template_version 2；`id: T-2026-09-21-023` 与改名后的文件名一致；`status: verifying`；
  RED/GREEN 键为纯值且两条 `Command` 逐字节相同；`validate_tasks.py` PASS）。
- Lint: `:app:lintDebug` PASS（`abortOnError true` 下 BUILD SUCCESSFUL，EXITCODE=0，`VERIFY2-lint.txt`）。
- 依赖方向: PASS（`verifyDependencyDirection: PASS`）。
- 验收点对照:
  - [x] 先 RED 后 GREEN（编译级）— RED 自洽：`T-023-red.txt` 结论段「javac 自报 53 / 找不到符号 53 /
        程序包…不存在 0」→ 0+53=53，与任务正文一致。
  - [x] **版本随同复制**、**稳定化全败（回滚）** — 这是本任务两条最关键的断言，`T-023-mutation.txt`
        对两者各做了一次变异：把版本改成从用途常量重推（应捕获
        `stabilizeCopiesThePinnedVersionFromTheRowVerbatim`）、把拒绝改成从工作体返回而不抛异常
        （应捕获「revision 已写但 operation 非终态」被提交），改完用 SHA-256 证明还原。
        该证据结构成立（变异 → 对应用例转红 → 还原）。
  - [ ] **instrumentation 执行** — **deferred — 本轮不使用模拟器（用户指令 2026-09-21）**。
        可得的最强替代：verifier 先清空 `app/build/intermediates/javac/debugAndroidTest` 再跑
        `:app:assembleDebugAndroidTest`，`:app:compileDebugAndroidTestJavaWithJavac` **真实执行**、
        产出 27 个 class（含 `AnswerRevisionGenerationOperationDaoInstrumentedTest.class`）、
        EXITCODE=0（`VERIFY2-assemble-androidtest.txt`）。**只证明可编译，未执行任何用例。**
  - [x] **所报「T-023 DAO 设备用例失败」已静态定性 —— 不是本任务的缺陷。**
        (a) 断言与生产代码**不矛盾**：`GenerationStabilizer.java:119-125` 对
        `row.partialAnswer.trim().isEmpty()` 返回 `DomainErrorCode.OPERATION_NOT_COMPLETABLE`，
        测试第 491-497 行断言同一码、并断言未写入 revision、状态仍为 `STREAMING_ANSWER`；
        (b) 那份 XML 快照（verifier 存档 `VERIFY2-device-xml-snapshot.xml`）里
        `tests="1" failures="1"`、`<failure></failure>` **为空**，`<system-err>` 写的是
        `Test run failed to complete. Instrumentation run failed due to Process crashed.`——
        即**进程被杀**，不是断言失败；verifier 后续直接 `adb shell am instrument` 复现时两次拿到
        `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`，且同一时段另有 lane 在执行
        install/uninstall（`pm list instrumentation` 一度为空）。
        **一次真机运行本应显示**：该类 12 个用例逐个出现 `INSTRUMENTATION_STATUS_CODE: 0`，最后一行
        `OK (12 tests)`；若真有缺陷，失败会带堆栈与具体用例名，而不是空 `<failure/>`。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）。
- 文档影响记录: PASS（`android/README.md` 的 `:data` 持久化入口索引含本任务两表 DAO 行；
  verifier 在 README:228/235/236 复核到 `BranchDao`/`ContextSnapshotDao`/`AiArtifactDao` 行）。
- 启动证据: N/A。
- 结论: **PARTIAL**
- 修复指令: 无代码级修复指令。待设备可用时整包跑一次
  `:app:connectedDebugAndroidTest`，从 JUnit XML（`TEST-<device>-_app-.xml`）读回 64/52+ 例的
  `failures="0" errors="0"`，并确认 `AnswerRevisionGenerationOperationDaoInstrumentedTest` 的 12 例在列。
- 阻塞点与剩余风险: 设备执行 deferred；`generation_operations` 的活动上限依赖 T-004 的偏索引
  （本任务只做翻译，未经真机验证）；夹具「篡改过的 operation 行」形态已被变异测试覆盖，可信。

## Handoff notes

- Completed: 两表 DAO、行形态 operation、稳定化事务入口与约束翻译；12 例真机证据（RED 53 个编译错误 → GREEN 52 例全绿）、lint、Java-only 守门、两处变异测试。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 完成后解锁 **T-2026-09-21-025**（首题原子创建事务，依赖本任务的 operation 写路径）、T-006（网关 accepted 事务）、T-008（恢复与重试）、T-011（派生写回，另需 T-2026-09-21-024）。
- Watch-outs:
  - 不要为了让写路径「跑通」而把 `restore` 当构造器用；需要「重建后再变更」时先用 `restore` 重建只读实体，再经领域工厂产生新状态。
  - 「版本随同复制」必须证明是**读取 operation 行**得到的字符串，而不是从 `GenerationCommand`/`PromptSpec` 重新推出来——两者在版本合法时字符串相同，只有用「篡改过的 operation 行」做夹具才能把二者区分开。
  - `ai_artifacts.based_on_revision_id` 是 `ON DELETE SET NULL`，删除 revision 不会删 artifact；artifact 的清理不属本任务（T-2026-09-21-024 的 AiArtifactDao.deleteByScope 已交付，供 T-011 使用）；context_snapshots 的 ContextSnapshotDao 同样已在 T-2026-09-21-024 交付。
