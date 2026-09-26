# Treelearn Harness Task: 实现 AI 派生产物与过期保护

id: T-2026-09-20-011
title: 实现 AI 派生产物与过期保护
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
shared_code_touched: true
depends_on: [T-2026-09-20-deepseek-stream-gateway-006.md, T-2026-09-20-immutable-context-snapshots-007.md, T-2026-09-20-topic-branch-lifecycle-009.md, T-2026-09-21-versioned-learning-prompt-policy-020.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 标题、摘要、要点、推荐和上下文压缩可调用 AI，但不能覆盖已经变化的主状态或丢失必需上下文。
- Problem/background: 慢请求晚返回会用旧输入改写新回答的推荐或分支摘要。
- Users/scenarios: 用户快速重置、继续对话、切换主题时，旧派生请求仍可能完成。
- Expected outcome: artifact use cases、按用途隔离的 PromptSpec、结构化 JSON 校验、inputDigest/basedOnRevision CAS 写回。
- Core flow and reversibility: 请求记录基线；结果解析后事务内复核；匹配则 apply，不匹配 stale_discarded。
- Inputs/outputs: 输入 scope/kind/revision/digest；输出版本化 artifact 或分类失败。
- State and exception handling: 非法 JSON、缺字段、旧 digest、删除 scope 和网络失败均不改当前值。
- Constraints and trade-offs: 派生产物非权威；不得复用 LEARNING_ANSWER；不猜测截取 JSON；输入用结构化 API 编码；内容日志禁止。
- AI recommendation and rationale: 统一 artifact envelope 与 CAS，避免每个功能各自处理竞态。
- User choice and custom additions: 首页 AI 推荐、标题、摘要、要点和回答推荐属于 V3.1；上下文压缩必须同时覆盖历史问题、推理和回答。
- AI-autonomous decisions: 根标题只依根问题；回答推荐绑定具体稳定 revision。
- User-confirmation decisions: P0 派生产物防旧写回。
- Acceptance evidence: fake gateway 的乱序完成、解析失败和删除竞态测试。
- Facts: 技术方案 §10 是权威。
- Assumptions: provider 可按提示返回结构化 JSON，但必须严格验证。
- Unknowns: 推荐条数可在 UI 任务按 PRD 常量确定。
- Risks: digest 输入不规范导致误判相同或过度刷新。
- Deferred: 远程缓存、推荐个性化和服务端排序。
- Non-goals: 不实现具体首页/聊天视觉。
- Skipped non-critical questions and reason: 提示词按稳定版本迭代；本任务以提示词设计 V1.0 的用途和 artifact 契约为准。

## Goal

确保所有 AI 派生产物只在输入基线仍有效时写回，并能可靠丢弃晚到旧结果。

## Scope

### In scope
- 标题、摘要、要点、回答推荐、首页推荐、上下文压缩用例；对应 PromptPurpose 接入；结构化输入/输出 codec；CAS Repository；脱敏事件。

### Out of scope
- 页面布局、主对话生成和搜索。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增派生产物；不修改 message/revision/snapshot 权威内容。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): PromptRegistry/RequestAssembler 生产用途、版本与消息；Gateway 生产；artifact repository 存储；首页、导航、聊天消费。
- Native bridge or independent-client compatibility/update requirements: 结构化请求使用 AiGateway，不暴露 provider DTO。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: artifact kind、payload schema、digest 输入和失效条件。
- Authoritative code/config/migration evidence: use case/codec/repository 与乱序测试。
- Documentation surfaces checked: 技术方案 §9.2/§10、提示词设计 V1.0、PRD 推荐/标题/摘要/要点/F21 章节。
- Documentation files updated: android/README.md — updated the JsonText row (now integral numbers) and added the section 应用层入口索引（:app）—— AI 派生产物与过期保护（T-011） indexing all 13 types. The technical design §10 text is unchanged: the implemented kinds, payload fields and invalidation rules are exactly §10, so there was nothing to re-sync there.
- Producer/consumer/sibling/compatibility paths checked: 根标题、分支标题、摘要/要点、回答推荐、首页推荐。
- Superseded phrases/examples reverse-searched: 无基线异步写回和字符串截取 JSON。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 当前态同步，历史保留。

## Acceptance criteria

- [x] 同一 JVM/集成测试命令先 RED 后 GREEN，旧 revision/digest 的晚到结果全部 stale_discarded。
- [x] 合法且基线未变的结构化结果原子写入；非法 JSON/缺字段保留旧 artifact。
- [x] 删除 scope 后的回调不能重建孤儿 artifact；日志仅含 kind、随机 ID、digest 前缀和状态。
- [x] 首页推荐只提交技术方案允许的树轮廓，不提交完整对话正文。
- [x] ROOT_TITLE、BRANCH_TITLE、BRANCH_SUMMARY、ANSWER_RECOMMENDATIONS、HOME_RECOMMENDATIONS 使用各自 PromptPurpose/版本，均不包含 LEARNING_ANSWER 教学正文；输入由 JsonWriter 等结构化 API 编码并视为不可信数据。
- [x] 每个 artifact 保存 `promptPurpose/promptPolicyVersion`；未知用途、schema 外字段、恶意输入指令或提示词构建失败均保留旧 artifact，不调用错误用途或降级为主回答提示词。
- [x] CONTEXT_COMPRESSION 同时输入并覆盖对应问题、reasoning 与 answer，保留关键定义/条件/结论/不确定点；解析失败、遗漏必需段或压缩后仍超窗时失败关闭主回答，不以删掉 reasoning 降级。

Note on device-bound evidence (user instruction 2026-09-21): no emulator was used this round — no
connectedDebugAndroidTest, no adb, no device. The real-SQLite half of criterion 3 (deleteByScope
followed by a late callback) is covered at the port level by the JVM test
aCallbackAfterTheScopeWasDeletedNeverCreatesAnArtifact; its database-level run is
deferred — emulator not used this round (user instruction 2026-09-21).

## TDD execution record

### RED — failing test first
- Test/scenario: The four T-011 test classes under android/app/src/test/java/com/treelearn/app/artifact/ (ArtifactSchemaTest, ArtifactInputTest, DerivedRequestTest, DerivedOperationTest) were on disk while the production package com.treelearn.app.artifact they name was absent, so the app unit-test source set cannot compile and none of the acceptance assertions can run.
- Command: .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
- Exit code: 1
- Observed failure: javac cannot resolve the T-011 artifact symbols, so :app:compileDebugUnitTestJavaWithJavac fails and :app:testDebugUnitTest never runs a single test.
- Failure reason validated: The log carries only javac diagnostics naming the absent artifact package and types in the four T-011 test files, and the identical command compiles and runs green once that package is restored (GREEN below), so the red is missing production code rather than an environment fault.

```
javac reported two capped blocks of 100 errors each, 200 error lines in total (100 is javac's
per-invocation cap, so 200 is a floor on the true count). Grouped two ways, both summing to 200:
  by test file : ArtifactSchemaTest 138 + DerivedOperationTest 32 + ArtifactInputTest 24 + DerivedRequestTest 6
  by message   : 找不到符号 166 + 程序包ArtifactKind不存在 20 + 程序包TreeOutline不存在 10 + 程序包ArtifactInputs不存在 4
  read back via: Get-Content artifacts/evidence/T-011-red.txt | Select-String '错误:'
  evidence file: artifacts/evidence/T-011-red.txt (header + EXITCODE=1 trailer)
Disclosure: the shared repository may not have production sources moved out of it, so this state was
reproduced in an isolated copy (robocopy /E /XD build .gradle of android/; longest path 118 chars).
The copy C:\tl-red\android ran the red, with the artifact production package moved to
C:\tl-red\hold\artifact-prod for the duration. A sibling lane's in-flight red
(generation/PositionCommandsTest.java, whose PositionCommands class does not exist yet) was moved
aside in the copy as well, so every error in the file belongs to T-011 and to nothing else.
```

### GREEN — minimum implementation
- Implementation: 13 new production files under android/app/src/main/java/com/treelearn/app/artifact/ (ArtifactKind, ArtifactOutcome, ArtifactPayload, DerivedResult, ArtifactPayloadCodec, TreeOutline, ArtifactInputs, DerivedRequest, DerivedTelemetry, DerivedCaller, DerivedStore, DerivedOperation, SqliteDerivedStore) plus integral-number support in core JsonText (write branch, read dispatch, number(), isDigit()).
- Command: .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
- Exit code: 0
- Passing evidence: the four T-011 test classes pass, 40 tests in total (ArtifactInputTest 8 + ArtifactSchemaTest 11 + DerivedOperationTest 13 + DerivedRequestTest 8), 0 failures, 0 errors, read from the JUnit XML of that same run; artifacts/evidence/T-011-green.txt.
- Regression evidence: forced rerun (--rerun-tasks) of :core:test 154 + :ai:testDebugUnitTest 72 + :data:testDebugUnitTest 6 + :tree-layout:test 34 tests, 0 failures, 0 errors, because this change widens core JsonText, the single structural JSON reader of :core; artifacts/evidence/T-011-regression.txt.

```
Command string is byte-identical to RED; only the production package differs (restored, not moved).
The green run also happened in the isolated copy C:\tl-red\android, because the shared tree cannot
host the red state. The copy's artifact production and test packages are SHA-256 identical, file by
file, to the shared tree's (13 + 4 files); the sibling lane's PositionCommandsTest.java stays
excluded in the copy and is untouched in the shared tree.
Module total of that run: 13 classes, 104 tests, 0 failures, 0 errors (T-011 is 4 of those classes).
Shared-tree status afterwards: :app:compileDebugJavaWithJavac is BUILD SUCCESSFUL, but
:app:testDebugUnitTest still fails with 29 javac errors, all inside the sibling's
generation/PositionCommandsTest.java; evidence artifacts/evidence/T-011-shared-tree-blocked.txt.
```

### REFACTOR — optional
- Cleanup: Not performed; the codec, CAS store and use cases are already separated, and no duplicated decision was found to fold.
- Regression evidence: The GREEN rerun above is the current post-implementation state; artifacts/evidence/T-011-green.txt.

### TDD or sizing exception — only with prior user approval
- Exception type: No TDD exception is claimed; a sizing deviation is open and belongs to the user.
- Reason: tdd_mode is required and the RED/GREEN pair above is intact, so no TDD exception applies. The measured size is nevertheless 726 judged production lines (13 new files 694 + JsonText 32) against a 200-line estimate and the 500-line hard cap, so this slice is over budget and must be split or approved by the user.
- User approval: PENDING — the user must decide whether to split the slice or grant a sizing exception; this task does not self-approve it.
- Alternative verification: Not applicable to TDD. Sizing evidence is artifacts/evidence/T-011-sizing.txt (694 + 32 = 726; the parts sum to the whole).

## Implementation notes

- digest 必须由规范化输入产生；写回时在同一事务内重新读取基线。
- 结构化解析使用 JsonReader/Writer 或等价 Java 结构化 API。
- 输出 envelope 与输入边界按提示词设计 V1.0 §6；PromptPurpose 到 artifact kind 使用穷尽映射，不提供默认回退。

- 语义判定（供 verifier 核对本任务对验收措辞的实现）：CONTEXT_COMPRESSION 的「压缩后仍超窗」实现为 summary 字符数必须严格小于被压缩内容（问题 + reasoning + answer）的字符总数，否则 OVER_WINDOW 失败关闭（DerivedRequest.compressionSourceChars 与 DerivedOperation.run）；「解析失败 / 遗漏必需段」实现为 PAYLOAD_INVALID / SUMMARY_EMPTY，输入中任一 turn 缺 reasoning 时在调用前判 REASONING_OMITTED，绝不以删掉 reasoning 降级。
- 端口未接线：SqliteDerivedStore 只经编译验证（:app:compileDebugJavaWithJavac BUILD SUCCESSFUL），本轮未接到任何界面或导航调用方；接线由 T-012 及后续 UI 任务负责。

## Verification report

### 规模例外——事后追认（用户批准，2026-09-24）

- Exception type: 规模（超 500 行硬上限）——**用户 2026-09-24 事后追认豁免**
- Reason: 实测 **726** 非空非注释行 / 500 硬上限，超 **45.2%**（前轮 verifier 独立复算，口径与 `sizing-adjudication-2026-09-21.txt` 一致）。派生产物机制 13 文件（DerivedRequest/DerivedOperation/codec/store/telemetry 共用同一契约面）；用户同日裁定不做回溯拆分（与 T-039 的拆分路径区别对待，理由：无自然切缝，硬拆产生的切片无法独立编译，属治理表演）。
- User approval: **用户 2026-09-24 经结构化选项明确批准本任务豁免**（同轮批准 T-006/T-007/T-011 三项，明确否决「同 T-039 拆分」与「维持现状」选项）。本批准发生在实现完成之后，按 `rules/harness-tasks.md` 规模条款**如实记为事后追认**，不写成事先批准。
- 实测行数与超出比例: 726 行 / 超 45.2%（判定口径：非空非注释；物理行并列记录于前轮 verifier 证据）。
- 剩余风险: ① 500 行硬上限的威慑力被本次追认软化，后续任务可能以「先写后要豁免」效仿——缓解：本例已如实标注事后属性，失败案例库可追加此模式监控；② 三模块的规模按整体判定，未来向其追加代码不再受本例外保护，增量仍按 200/500 评估。

### verifier #3 批量复核（2026-09-24，独立于本任务实现者）

- Verdict: **PASS（设备项 deferred；规模例外已由用户 2026-09-24 事后追认）**。前轮 verifier 的技术判定（TDD 闭环、guard/lint、静态扫描、JVM 套件）全部维持；唯一 OPEN 的规模判据随用户批准关闭。
- Fresh evidence（2026-09-24 批次 2）: `artifacts/evidence/P3-B1-gates-2026-09-24.txt`（全模块 JVM 套件 + lint + androidTest 编译，BUILD SUCCESSFUL / EXITCODE=0；全库 188 套件 1725 tests / 0 失败）、`P3-B1-no-kotlin-2026-09-24.txt`（EXITCODE=0）。
- 判定依据: 前轮 verifier #2 复核结论无新事实推翻；本任务交付面（网关/快照/派生机制）在 2026-09-23 至 09-24 的后续任务（T-045/T-046/T-047 等）生产消费中被持续复用且全量套件持续全绿，构成追加的行为证据。
- Gaps（不变，均 deferred）: 设备执行项 —— `deferred — 本轮不使用模拟器（用户指令 2026-09-21）`；真实 provider 冒烟（无 Key）。
> **最新结论：见本段末尾「第九轮补充」（verifier #2，2026-09-21 第四轮）。下方第一个 - Verdict: 是历史轮次的记录，不是当前判定。**

> 独立 verifier 复核（verifier #2，2026-09-21 第四轮），只读：未修改任何生产代码、测试或他人证据。
> 本轮按用户 2026-09-21 指令**不使用模拟器**；需要真机的验收项一律记为
> `deferred — emulator not used this round (user instruction 2026-09-21)`，既不算 PASS 也不算 FAIL。
> verifier 自产证据见 `artifacts/evidence/VERIFY4-T011-shared-app-unittest.txt`、`VERIFY4-jvm-suites.txt`。

- Verdict: PARTIAL（JVM/GREEN 判据 PASS；设备判据 deferred；规模偏差未获批）
- 审阅文件与证据: 本任务文件；`android/app/src/main/java/com/treelearn/app/artifact/**`（13 个生产文件）、
  `android/app/src/test/java/com/treelearn/app/artifact/**`（4 个测试文件）、`android/core/src/main/java/com/treelearn/core/domain/JsonText.java`；
  证据 `T-011-{red,green,mutation,regression,shared-tree-blocked,sizing,guard}.txt`。
- 代码量: 13 个新文件 **694** 判定行 / 1136 物理行，加 `JsonText.java` 内 **32** 行（3+3+23+3）= **726** 判定行。
  verifier 复核分项自洽：3+3+23+3=32，694+32=726，分项之和等于总数；对照 500 硬上限 → **超出 45%，未获批**，
  按未裁定偏差处理（verifier 不代批）。
- 任务合规: PASS（`validate_tasks.py` 对本文件 PASS；RED/GREEN 五键为纯值；两条 `Command` 逐字节相同）。
- **隔离副本 GREEN 的判定（本轮重点，明确表态）**: GREEN 取自 `C:\tl-red\android`，因为共享树的 `:app` 单测源集当时被
  兄弟 lane T-010 的在飞 RED（`generation/PositionCommandsTest.java`，29 条 javac 错误全部属于该文件）挡住。
  verifier 判定该做法**可接受，且披露充分**，逐条核过：① 没有把任何生产源码移出共享树（只用了独立副本，
  与 T-005 被禁止的 stash 做法性质不同）；② 副本与本仓库的 13 个生产文件 + 4 个测试文件**逐字节 SHA-256 相同**
  （verifier 实测 checked=17 mismatches=0）；③ 被排除的只有兄弟 lane 那一个文件，且只在副本内；
  ④ RED/GREEN 命令字符串逐字节相同；⑤ 任务**如实披露**「共享树 GREEN 尚未观测」，没有冒认。
  任务文件里那句待办（「I will have it re-run in-tree once T-010 lands」）现在可以划掉：
- **共享树 GREEN 已由 verifier 亲自观测（本轮新增）**: T-010 的 `PositionCommands.java` 已落进共享树，阻塞消失。
  verifier 在**共享仓库**执行
  `cd android; .\gradlew.bat :app:testDebugUnitTest --rerun-tasks --no-daemon --console=plain`
  → `BUILD SUCCESSFUL`、`EXITCODE=0`；JUnit XML 回读：**20 套件 / 198 例 / 0 失败 / 0 错误 / 0 跳过**，
  其中本任务四个套件 `ArtifactInputTest 8 + ArtifactSchemaTest 11 + DerivedOperationTest 13 + DerivedRequestTest 8 = 40`。
  即此前唯一缺失的「in-tree GREEN」**已经在盘上**。
- 数字可核（逐条读回 `T-011-red.txt`）: 200 条 `错误:` 行；按文件 `ArtifactSchemaTest 138 + DerivedOperationTest 32 +
  ArtifactInputTest 24 + DerivedRequestTest 6 = 200`；按消息 `找不到符号 166 + 程序包ArtifactKind不存在 20 +
  程序包TreeOutline不存在 10 + 程序包ArtifactInputs不存在 4 = 200`。200 = javac 每次调用上限（两段各 100），
  任务已把它写明为「真实数量的**下界**」，没有当成精确值。
- 变异测试: `T-011-mutation.txt` 记 M1–M4，各自只让点名的断言转红（1 / 2 / 1 / 4 例），还原后 0 红、`EXITCODE=0`；
  四组分别指向本任务独有的判定（scope 已删、基线未比对、压缩未缩小被放行、空串被 codec 放行）。
- 验收点对照:
  - [x] 先 RED 后 GREEN、命令一致、GREEN 40/40 — 见上（含 verifier 在共享树的强制重跑）。
  - [x] 「只在输入基线仍有效时写回」— M1/M2 两例退化证明该判定是活的。
  - [x] 严格结构化 codec 与压缩失败关闭 — M4 / M3 分别证明空白拒绝与 OVER_WINDOW 拒绝是活的。
  - [x] 不泄漏正文 — 抽查证据与测试名，只含 kind / purpose / version / digest 前缀等元数据。
  - [ ] `SqliteDerivedStore` 与真实 SQLite 的孤儿保护与事务行为 — **deferred — emulator not used this round
    (user instruction 2026-09-21)**：本轮只有**编译**证据（`:app:compileDebugJavaWithJavac` BUILD SUCCESSFUL），
    数据库层证据由 T-024 的设备套件承担。
  - [ ] 端口接线（运行时谁调用 `SqliteDerivedStore`）— 本轮**无消费者**，属 T-012 及后续 UI 任务；
    任务把它记为「未接线」而不是「已完成」，判定成立。
- 专项守门路由: Java-only guard PASS（`T-011-guard.txt`：`VERDICT: PASS (13 OK line(s))`）。
- 文档影响记录: PASS（`android/README.md` 的 JsonText 行与 `:app`「AI 派生产物与过期保护（T-011）」索引已更新）。
- 启动证据: N/A（库内用例，无进程/端口）。
- 结论: **PARTIAL**
- 修复指令: ① 规模偏差 726 > 500 属未获批，须用户裁定（切口：codec/存储 与 use case 分层）或批准例外；
  ② 设备恢复后由 T-024 的设备套件补 `SqliteDerivedStore` 的库层结论；③ **无需代码修复**。
- 阻塞点与剩余风险: 设备判据 deferred；`SqliteDerivedStore` 未接线（T-012 负责）；`JsonText` 是本任务扩宽的
  `:core` 单一 JSON 读端，回归面由 `T-011-regression.txt` 的强制重跑覆盖（该次运行的模块数快照见该文件，
  数值随其它 lane 后续新增用例而变，不要当冻结值）。
- 第九轮补充（同一 verifier 轮次内的读数刷新）：上文本任务四个套件 **40 例**不变，但**共享树的总量在动**——`T-011-shared-app-unittest.txt` 记的 `:app` 为 20 套件 / 198 例（22:26），而 22:30 的强制重跑已是 **21 套件 / 216 例**（`VERIFY5-jvm-suites.txt`），增量来自兄弟 lane 新增的套件。引用 `:app` 总量时必须连时间戳一起引；本任务自己的 40 例（`ArtifactInputTest 8 + ArtifactSchemaTest 11 + DerivedOperationTest 13 + DerivedRequestTest 8`）在两次运行中逐字相同。另外本轮已确认 instrumentation 源码可编译（`> Task :app:compileDebugAndroidTestJavaWithJavac` 成功、exit 0），但设备执行未进行（用户 2026-09-21 指令）。

## Handoff notes

- Completed: T-011 的 13 个生产文件（android/app/src/main/java/com/treelearn/app/artifact/）与 4 个测试文件（40 例）已落盘；CAS 写回、晚到结果过期丢弃、严格结构化 codec、按用途隔离的 PromptSpec/版本、脱敏事件、压缩失败关闭均由测试锁定。
- In progress: 无。
- Blocker: 无（T-011 自身生产代码在共享仓库可编译）。
- Unblock condition: 不适用。
- Next steps: 由独立 verifier 复核本任务；T-012 及后续界面任务把 SqliteDerivedStore 接到调用方。
- Watch-outs:
  - 本轮未使用 emulator，也未跑 adb/设备；凡严格依赖设备的判据一律记为 deferred — emulator not used this round (user instruction 2026-09-21)，不是 PASS。
  - 共享仓库的 :app 单测源集当前被兄弟 lane T-010 的在飞 RED 挡住（generation/PositionCommandsTest.java 引用的 PositionCommands 尚不存在，29 条 javac 错误全部属于该文件）；T-011 未触碰该文件。GREEN 因此取自隔离副本 C:\tl-red\android，该副本与本仓库的 13 个生产文件、4 个测试文件逐字节 SHA-256 相同；共享仓库自身的阻塞状态见 artifacts/evidence/T-011-shared-tree-blocked.txt。
  - SqliteDerivedStore 与真实 SQLite 的孤儿保护只有编译证据，数据库层证据由 T-024 的设备套件承担（本轮 deferred）。
  - 规模偏差：实测 726 judged 行（13 个新文件 694 + core JsonText 32），超过 500 硬上限，属未获批偏差，须由用户裁决（拆分或批准）；证据 artifacts/evidence/T-011-sizing.txt。
  - 未调用任何真实 DeepSeek/网络请求，未使用 API key；证据与测试名只含 kind、purpose、promptPolicyVersion、digest 前缀、状态等元数据，不含提示词正文、问题、答案或推理文本。
- 回归补充: 本任务还改动 core JsonText（支持整数），已用 --rerun-tasks 重跑 :core/:ai/:data/:tree-layout，全绿。