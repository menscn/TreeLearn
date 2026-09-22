# Treelearn Harness Task: 实现版本化学习导师提示词策略

id: T-2026-09-21-020
title: 实现版本化学习导师提示词策略
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
depends_on: [T-2026-09-20-model-immutable-answer-domain-003.md, T-2026-09-20-sqlite-schema-dao-transactions-004.md, T-2026-09-20-deepseek-stream-gateway-006.md, T-2026-09-20-immutable-context-snapshots-007.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: DeepSeek 主回答必须体现知芽“准确、易懂、高效”的学习定位，同时与标题、摘要、推荐和压缩调用隔离。
- Problem/background: 单一提示词覆盖所有请求会污染结构化派生产物；system 被重复、错位或裁剪会使回答行为失控且难以追踪。
- Users/scenarios: 用户学习入门或专业概念、纠正错误前提、询问时效信息、折叠思考后只阅读最终答案；派生调用仍需稳定 JSON。
- Expected outcome: 供应商无关 `PromptPurpose/PromptSpec`、版本化 DeepSeek 注册表、请求组装器、容量预留与确定性回归测试。
- Core flow and reversibility: 按用途取得固定 PromptSpec；主回答选定版本并持久化；system 第一、历史居中、当前 user 最后；构建失败不调用远端。
- Inputs/outputs: 输入用途、thinking 开关、ContextPlan 编码结果与当前输入；输出有序 DeepSeek messages、用途和策略版本，或显式构建错误。
- State and exception handling: 未知用途、空正文、重复/错位 system、容量不足、非法历史标签和动态 system 插值均 fail closed。
- Constraints and trade-offs: 首版不开放用户编辑提示词，不引入用户画像、联网检索、RAG 或新的永久表；准确性是行为约束，不是事实保证。
- AI recommendation and rationale: 用版本化注册表隔离运行时行为，用 PromptPurpose 隔离不同 JSON 契约，避免散落字符串和静默行为漂移。
- User choice and custom additions: 用户选择“自适应导师”，要求学习更高效、信息更准确、更容易懂，并于 2026-09-21 确认设计。
- AI-autonomous decisions: 版本格式 `tl-<purpose>/semver`；数据库只存版本号，完整正文只在 `:ai` 注册表中。
- User-confirmation decisions: 自适应导师正文、思考附加策略、派生用途隔离、版本落库和注入顺序已确认。
- Acceptance evidence: JVM fixture 精确断言注册表、消息顺序、token 边界、恶意上下文和版本复制；真实行为由 T-018/T-019 验收。
- Facts: `Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md` 是提示词正文与策略权威。
- Assumptions: T-006 提供有序消息请求边界，T-007 提供不含 system 的历史 ContextPlan 编码结果。
- Unknowns: 模型对自然语言风格的非确定性只能通过受控样例和真实冒烟评估，不能逐字断言。
- Risks: system 重复、提示词版本静默覆盖、派生任务误用教学提示词、恶意历史文字改变协议。
- Deferred: 用户自定义提示词、用户画像、在线评测服务、RAG 与其它模型供应商。
- Non-goals: 不实现 UI、派生业务用例、网络解析或数据库 DDL。
- Skipped non-critical questions and reason: 提示词正文与默认教学方式已由用户确认，无需再次选择。

## Goal

让每次 DeepSeek 调用都使用可追踪、按用途隔离且不能被历史内容篡改的 system 策略，并保证主回答符合自适应学习导师定位。

## Scope

### In scope
- `PromptPurpose/PromptSpec`、`DeepSeekPromptRegistry`、主回答基础提示词、thinking 静态附加策略、派生用途策略、`DeepSeekRequestAssembler`、token 预留和 fixture 测试。

### Out of scope
- SQLite schema/DAO、GenerationCoordinator 状态流、artifact CAS、页面渲染和真实 DeepSeek 发布冒烟。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增版本化 system 策略；保留 ContextPlan 的分支隔离、历史 reasoning 编码、thinking 显式开关和流式双通道。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 注册表生产 PromptSpec；请求组装器消费 ContextPlan；GenerationCoordinator 与 artifact use cases 选择用途；AiGateway 只发送有序 messages。
- Native bridge or independent-client compatibility/update requirements: DeepSeek messages 的 role/order 和 token 预算是外部契约；无 Web/后端桥。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: PromptPurpose、策略版本、system 正文、thinking 附加段、消息顺序、容量失败与派生用途隔离。
- Authoritative code/config/migration evidence: `android/core` 提示词契约、`android/ai` 注册表/组装器及 JVM fixture。
- Documentation surfaces checked: BASE-26、PRD F21、提示词设计 V1.0、技术架构 §9.2、Android README、DeepSeek 请求契约。
- Documentation files updated: 无（no-edit）。本任务新增的是 `:ai` 侧实现，逐条核对后所有当前态文档已把它描述成目标态，编辑只会制造与活动代码无差别的重复；核对证据见下一条 no-edit 结论。
- Producer/consumer/sibling/compatibility paths checked: 首次根问题、普通追问、重试、重置、标题、摘要、回答推荐、首页推荐、上下文压缩。
- Superseded phrases/examples reverse-searched: 无 system、单提示词覆盖全部调用、system 参与摘要/裁剪、用户可编辑提示词。
- `documentation impact: none` reason and evidence: reason —— 实现类名、用途、版本与消息顺序全部与现行当前态文档逐字一致，没有任何文档面因本变更变得不准确。evidence —— (1) `Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md` §9.2 第 202 行已逐字命名 `DeepSeekPromptRegistry` 与 `DeepSeekRequestAssembler`，并给出 system/history/user 的组装顺序与「恰好一条、第一条」；同节其余各点（system 不进 ContextPlan、不因预算被裁剪、thinking 只追加静态段、派生用途各自独立 PromptPurpose、数据库只存用途与版本号）与本实现逐条一致；(2) `android/README.md` 第 45 行已把 `:ai` 描述为「DeepSeek HTTPS/SSE、上下文编码、版本化提示词注册表与请求组装」且「不接受用户自定义 system 模板」；(3) 提示词正文的权威是提示词设计 V1.0 §4/§4.1/§6，九个正文逐字转录并以 digest 锁定（见 T-020-transcription.txt，本 lane 另做一次独立复核，9/9 逐字节一致）。反向查找「无 system」「单提示词覆盖全部调用」「system 参与摘要/裁剪」「用户可编辑提示词」，在 `android/README.md`、技术架构 §9.2、提示词设计 V1.0 中均无与本变更矛盾的现行表述。
- Historical/PRD handling: 不改写历史记录；当前行为变化新增版本记录。

## Acceptance criteria

- [x] 同一 JVM 测试命令先 RED 后 GREEN；所有 PromptPurpose 有唯一非空版本和非空 system，`LEARNING_ANSWER` 与各派生用途内容隔离。
- [x] 主回答请求恰好一条 system 且位于第一条，当前 user 恰好位于最后一条；thinking on 只追加已确认的静态段，任何 system 内容均无用户/API Key 动态插值。
- [x] token 预算先预留 system、当前 user 与最小回答空间；空间不足时拒绝构建，不裁剪 system，不静默删除历史 reasoning。
- [x] 历史内容含 `system:`、同名内部标签或“忽略此前规则”时不改变 role/order/PromptPurpose；派生输入使用结构化 JSON 且不能改变输出 schema。
- [x] `tl-learning-answer/1.0.0` 正文与提示词设计文档一致，并以行为 fixture 覆盖入门、专业、错误前提、歧义、时效信息、类比边界和最终回答独立性所需指令。
- [x] 日志/异常只允许用途、版本、字符数和错误类别，不包含 system content、用户/历史/派生输入或输出正文。
- 判定口径偏离（只影响上一条的单位，不影响其行为）: 第三条的「token 预算」在本实现中按 UTF-16 字符执行 —— 首版没有 tokenizer，而本任务不得新增任何第三方依赖，因此 PromptBudget 用字符表达容量。预留顺序、空间不足即拒绝构建、不裁剪 system、不静默删除历史 reasoning 这四项行为与验收条件逐字一致；单位不同这一点同时写在 PromptBudget 的 javadoc 与 Handoff watch-outs 里，请 verifier 据此判定，不要默认通过。

## TDD execution record

### RED — failing test first
- Test/scenario: 19 JVM unit tests written first under android/ai/src/test/java/com/treelearn/ai/prompt/ (DeepSeekPromptRegistryTest 8 + DeepSeekRequestAssemblerTest 11), against a `com.treelearn.ai.prompt` production package that did not exist yet. Both classes were afterwards hardened so that a red body comparison cannot print the prompt body, and RED was re-established for those final bytes (second half of artifacts/evidence/T-020-red.txt).
- Command: cd android; .\gradlew.bat :ai:testDebugUnitTest --no-daemon --console=plain
- Exit code: 1
- Observed failure: `:ai:compileDebugUnitTestJavaWithJavac` FAILED — javac reports 147 errors and every one of them is the absent production package, so not a single test method could run.
- Failure reason validated: The identical command exits 0 as soon as the production package is restored, so the failure is caused by the missing implementation and not by the tests or the build harness.

Detail (evidence: artifacts/evidence/T-020-red.txt):

```text
captured at 2026-09-21T20:08:07+08:00
state: both test classes present; android/ai/src/main/java/com/treelearn/ai/prompt was moved out of
       the workspace for this run, so the only difference from the GREEN tree is the missing production code.

> Task :ai:compileDebugUnitTestJavaWithJavac FAILED
  ...\DeepSeekRequestAssemblerTest.java:84: 错误: 程序包DeepSeekMessage不存在
          assertEquals(DeepSeekMessage.Role.USER, request.messages.get(3).role);
  100 个错误
BUILD FAILED in 13s
EXITCODE=1
```

Where every number above comes from — and why the printed "100 个错误" is NOT the number of errors:

```text
Get-Content artifacts/evidence/T-020-red.txt | Where-Object { $_ -match '错误: ' }
    | ForEach-Object { ($_ -split '错误: ',2)[1].Trim() } | Group-Object
  找不到符号                          154   (154/2 = 77)
  程序包DeepSeekMessage不存在          30   (30/2 = 15)
  程序包PromptBuildException不存在     16   (16/2 = 8)
200 lines holding 154/30/16 are exactly double the printed 100, because Gradle repeats the task
output inside its own failure report — the same duplication the `个错误` marker shows (it matches
twice: once on the task line, once on the report line).
javac's own closing line in that capture reads
  "仅显示前 100 个错误（共 147 个）；若要查看更多错误，请使用 -Xmaxerrs"
so 100 is javac's default print cap and 147 is what it actually found: reading the printed 100 as
the total understates the failure by 47. Re-measured with -Xmaxerrs 10000 against the hardened test
bytes, the complete set is
  找不到符号 114 + 程序包DeepSeekMessage不存在 17 + 程序包PromptBuildException不存在 15
  + 程序包PromptBodiesV1不存在 1 = 147
grouped by file: DeepSeekRequestAssemblerTest.java 105 + DeepSeekPromptRegistryTest.java 42 = 147
both new test files are named in the compiler output: DeepSeekPromptRegistryTest, DeepSeekRequestAssemblerTest
the capture holds two `^EXITCODE=` lines, one per run, and both read 1
```

### GREEN — minimum implementation
- Implementation: 8 production classes in android/ai/src/main/java/com/treelearn/ai/prompt/ — PromptBodiesV1 (the nine verbatim v1.0 bodies), DeepSeekPromptRegistry (exhaustive purpose to version + body), DeepSeekRequestAssembler (system first, caller history copied, current user last, reservation before history), DeepSeekRequest, DeepSeekMessage, PromptBudget, PromptBuildException, JsonObjectText. Java only, no new dependency, no edit to android/core/**.
- Command: cd android; .\gradlew.bat :ai:testDebugUnitTest --no-daemon --console=plain
- Exit code: 0
- Passing evidence: artifacts/evidence/T-020-green.txt — 19/19 of this task's tests pass (DeepSeekPromptRegistryTest 8, DeepSeekRequestAssemblerTest 11), 0 failures, 0 errors, read back from the JUnit XML the task itself wrote. The file carries two later re-runs taken as the sibling lanes' sources landed: 64 tests in :ai, then — after the body comparisons were hardened — 72 tests in :ai (this task's 19 + the gateway lane's 45 + the encoder lane's 8), 0 failures, 0 errors, run with --rerun-tasks so that the compile and the run are real rather than replayed.
- Regression evidence: artifacts/evidence/T-020-regression.txt — cd android; .\gradlew.bat :core:cleanTest :ai:cleanTestDebugUnitTest :ai:testDebugUnitTest :core:test --no-daemon --console=plain --no-build-cache, exit 0 with 0 failures and 0 errors on every run: 151 tests on the first (19 :ai + 132 :core), 196 after the gateway lane landed (64 + 132), 226 on the final run after the hardening (72 :ai + 154 :core). --no-build-cache plus cleanTest is what makes each one a real execution rather than a replayed FROM-CACHE result.

### REFACTOR — optional
- Cleanup: No post-GREEN refactor was needed. Instead of a refactor, the falsifiability of the new assertions was checked by mutation: three deliberate regressions, each turning a specific subset red and leaving the rest green (baseline 19/19 green, then A 5 failures, B 2 failures, C 1 failure; each mutation restored to the recorded sha256 6E35ACDF...AD8D8 and re-verified green).
- Regression evidence: artifacts/evidence/T-020-mutation.txt (the mutation run) and artifacts/evidence/T-020-regression.txt (the module regression after the restore).

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required; no TDD exception was needed and none was requested.
- User approval: Not applicable
- Alternative verification: Not applicable
- Sizing (measured): 344 non-empty non-comment production lines across the 8 files in android/ai/src/main/java/com/treelearn/ai/prompt/, against this task's estimate of 190 and the rules' 200 soft / 500 hard limits.
- Sizing verdict: WARN — over the 200 soft limit, far under the 500 hard limit, so no user-approved sizing exception is required; recorded here so the verifier surfaces it rather than discovering it.
- Sizing evidence: artifacts/evidence/T-020-sizing.txt (per-file counts, the counting convention taken from .trae/rules/harness-tasks.md, and physical lines listed separately as reference only).

## Implementation notes

- 提示词正文必须从设计文档逐字转录为 UTF-8 常量或只读资源，并用快照/digest 测试防止同版本静默变化；不要从 Markdown 在运行时读取。
- `PromptSpec` 对外不可变；注册表不提供任意字符串 lookup 回退，不识别用途即报错。
- 请求组装使用新列表，不修改或复用调用方可变集合；system 与当前 user 不进入 ContextPlan。
- 结构化派生输入使用 JsonWriter/JsonReader；不得用 XML/JSON 字符串拼接做转义。
- 精确行为样例见提示词设计 V1.0 §8；真实模型非确定性评估留给 T-018/T-019。

## Verification report

> 独立 verifier 复核（verifier #2，2026-09-21），只读：未修改任何生产代码、测试或他人证据。
> 本轮按用户 2026-09-21 指令**不使用模拟器**；凡严格需要真机的验收项一律记为
> `deferred — 本轮不使用模拟器（用户指令 2026-09-21）`，既不算 PASS 也不算 FAIL。
> verifier 自产证据见 `artifacts/evidence/VERIFY2-*.txt`（helper 脚本在 `artifacts/evidence/.verify2/`）。

- Verdict: **PASS**
- 审阅文件: 本任务文件；`android/ai/src/main/java/com/treelearn/ai/prompt/**`（8 个生产文件）；
  `SystemBodyAssertions.java` 与两个测试类；证据 `T-020-{red,green,tests,mutation,guard,regression,sizing,transcription}.txt`；
  verifier 证据 `VERIFY2-{jvm-suites,T020-leakscan,T020-transcription,round2-static,sizing,static-findings}.txt`。
- 代码量: **310** 非空非注释行（verifier 复算 8 个生产文件，不含 `DeepSeekContextEncoder`——那是 T-007 的）；
  任务自报 344，差异全部落在 `PromptBodiesV1.java`（我 23 / 它 55），原因是计数器对 Java
  **文本块正文**是否计为「代码行」口径不同。两种口径都**低于 500 硬上限**，只触发 200 软上限警告，
  不改变判定。物理行 563 与我 563 一致。
- 任务合规: PASS（template_version 2；id/文件名一致；RED/GREEN 键为纯值、两条 `Command` 逐字节相同；
  `validate_tasks.py` PASS）。
- Lint: N/A（`:ai` 为 java-library）。
- 依赖方向: PASS（`verifyDependencyDirection: PASS`）。
- 验收点对照:
  - [x] 先 RED 后 GREEN；每个 `PromptPurpose` 有唯一非空版本与非空 system；派生用途与主回答隔离 —
    verifier 复跑 `:ai:testDebugUnitTest --rerun-tasks`：`DeepSeekPromptRegistryTest` 8 +
    `DeepSeekRequestAssemblerTest` 11 = **19 例全绿**（`VERIFY2-jvm-suites.txt`）。
  - [x] system 恰好一条且首位、当前 user 末位、thinking 只追加静态段、无运行时插值 — 同上 19 例覆盖。
  - [x] 预算先预留 system/当前问题/最小回答，不足即拒绝构建且不裁剪 system — 变异证据 B 证明该断言可证伪。
  - [x] 历史内容含 `system:`、同名标签或「忽略此前规则」时 role/order/用途不变 — 变异证据 A/C 各自
    让对应用例转红，还原后 19 例重新全绿（SHA-256 自证还原）。
  - [x] **正文与设计文档逐字一致** — verifier **独立复算**：从设计文档的 9 个 ```text 围栏块按
    `"
".join` 计算 SHA-256，**9/9 命中**任务声明的摘要；再独立地从
    `PromptBodiesV1.java` 的 9 个文本块（正确处理 Java 行尾 `\` 续行转义）重算，**9/9 同样命中**
    （715/101/186/102/116/156/200/187/174 字符）。见 `VERIFY2-T020-transcription.txt`。
  - [x] 日志/异常不含正文 — **独立复核通过**：以设计文档的全部 130 段 CJK 连续串切成 1661 个
    14 字窗口，对 `artifacts/evidence/**` 全量扫描，**共享窗口数 = 0**；`SystemBodyAssertions`
    在断言转红时只打印长度与 SHA-256（`SystemBodyAssertions.java:28-34`）。
    另做**最长连续串**复核：对 9 段正文切 20 字窗口扫全部 166 个证据文件，**共享连续串 = 0**（`VERIFY2-round2-static.txt` §A）。
    （残留提示，非判据：`T-020-mutation.txt` 的变异 A 仍会经 JUnit `ComparisonFailure` 打印两处
    **合成夹具问题串**——它们是测试源码里公开的夹具数据，不是真实用户内容、也不是提示词正文。）
  - [x] token 预算按 UTF-16 字符而非模型 token — 已在 `PromptBudget` javadoc 与验收条件第 6 条声明，
    verifier 接受该偏离（单位不同、四项行为逐字一致）。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）。
- 文档影响记录: PASS — `documentation impact: none` 的支撑经 verifier 逐条核对成立：
  《技术方案》§9.2 第 202 行逐字命名 `DeepSeekPromptRegistry` 与 `DeepSeekRequestAssembler` 并给出
  system/history/user 顺序；`android/README.md:46` 已把 `:ai` 描述为「版本化提示词注册表与请求组装」
  且「不接受用户自定义 system 模板」。
- 启动证据: N/A。
- 结论: **PASS** → `status: verified`
- 修复指令: 无阻塞项。可选清理：把 `T-020-mutation.txt` 里两处夹具问题串也改为只打印长度/摘要，
  使证据文件对「任何正文形内容」都保持零回声。
- 阻塞点与剩余风险: 无。真实模型行为（自然语言风格的非确定性）按任务声明由 T-018/T-019 验收，
  不属本任务判据。
- 新型错误（建议追加 AGENTS.md 失败案例库）: **「计数口径不同」被误当成「数字对不上」**：
  同一个目录，文本块正文计不计入「非空非注释生产行」会让 `PromptBodiesV1.java` 出现 23 vs 55 的差，
  合计数 310 vs 344。规则：给出规模化数字时必须**同时写明计数器对文本块/续行的处理**，
  否则两方都会「按自己的口径复算并认为对方错了」。

## Handoff notes

- Completed: 版本化提示词注册表与请求组装器已落在 `:ai`：android/ai/src/main/java/com/treelearn/ai/prompt/{PromptBodiesV1,DeepSeekPromptRegistry,DeepSeekRequestAssembler,DeepSeekRequest,DeepSeekMessage,PromptBudget,PromptBuildException,JsonObjectText}.java，加 19 条 JVM 测试与一个共享的 SystemBodyAssertions（比较 system 正文的断言只报长度与 sha256，失败不会打印正文）。九个 v1.0 正文逐字转录并以 digest 锁定（本 lane 又独立复核一次，9/9 与设计文档 fenced block 逐字节一致，见 T-020-transcription.txt）。同一命令 RED 退出码 1（javac 147 个错误）→ GREEN 退出码 0；回归 `:ai` + `:core` 三次全绿：151 → 196 → 硬化后 226 条，每次 0 failures；Java-only guard 在 T-025 lane 补齐其数据层类之后 PASS（13 OK 行，3 个 APK 无 Kotlin 运行时字节码）。
- In progress: 无；任务停在 verifying，等独立 verifier 收口。
- Blocker: 无。真实 DeepSeek Key 不是确定性 fixture 的前置条件。
- Unblock condition: 不适用。
- Next steps: 解锁 T-008 主回答生成协调与 T-011 派生 AI 用途接入；verifier 从 artifacts/evidence/T-020-*.txt 收口。
- Watch-outs:
  - system 文本不得进入日志、SQLite、SharedPreferences、任务文件或证据文件；证据只保留用途、版本、字符数与 sha256。
  - 预算口径是 UTF-16 字符而不是模型 token（首版不引入 tokenizer，也不新增依赖）；PromptBudget 的 javadoc 写明了这一点，将来引入 tokenizer 应改该类而不是改调用方。
  - android/core/** 未被修改：PromptPurpose/PromptSpec 已拥有用途与版本契约，注册表按原样消费。
  - 包边界：本 lane 只拥有 `com.treelearn.ai.prompt.**`；`com.treelearn.ai.gateway.**` 属于 T-006，并在同一模块内并发编辑。上面的 GREEN/回归复跑是在 gateway 源码落地之后取的，因此它们编译的是模块当前的整体形态。
  - 规模：实测 344 非空非注释生产行，估算 190；超过 200 软上限、远低于 500 硬上限，已在 sizing 小节记为警告，而不是已批准例外。
  - 证据归属：T-020-guard.txt 里第一次 guard FAIL 是 T-025 lane 新建的 app androidTest 引用了尚不存在的数据层类（13 个 javac 错误全部落在 FirstTopicCreationInstrumentedTest.java），与本任务无关且未修改；该 lane 补齐后同一命令 exit 0。
  - 断言不得把提示词正文写进测试报告：第一版用 assertEquals 直接比较 system 正文，mutation A 一触发就把 715 字符的导师正文经 JUnit ComparisonFailure 打进了 T-020-mutation.txt（941 个替换字符的乱码）。现已统一走 SystemBodyAssertions（失败只报长度与 sha256），该证据文件已整体重出，旧运行在被取代说明里注明。任何后续比较 system 正文的断言都必须走这个共享 helper，不要再写第二份。
  - javac 默认只打印前 100 个错误（-Xmaxerrs 上限 100）。把「100 个错误」当作总数会把 147 少算 47 —— 本任务两次 RED 都受这个上限影响，完整 147 由 -Xmaxerrs 10000 取得；引用编译错误数时，先看有没有「仅显示前 … 共 N 个」这一行。
  - 本 lane 的 `com.treelearn.ai.prompt` 已被 gateway lane 的 AiGateway/JsonChatRequestBody 依赖，因此「把生产包移走再跑一次 Gradle RED」会打断同一模块其它 lane 的构建；RED 的复现改在仓库外隔离编译完成（不触碰工作区）。
