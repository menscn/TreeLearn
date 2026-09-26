# Treelearn Harness Task: 实现 DeepSeek HTTP 与 SSE 网关

id: T-2026-09-20-006
title: 实现 DeepSeek HTTP 与 SSE 网关
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
depends_on: [T-2026-09-20-bootstrap-java-android-modules-002.md, T-2026-09-20-model-immutable-answer-domain-003.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 使用 DeepSeek 流式生成，并将 reasoning 与 answer 两条通道独立交付业务层。
- Problem/background: 网络块不等于 SSE/JSON 事件，错误处理或拼接错误会丢失思考内容并污染状态机。
- Users/scenarios: 用户验证余额、选择模型/思考模式、流式生成、断网后原位重试。
- Expected outcome: 纯 Java AiGateway、严格保序的 role/messages 请求映射、余额接口、SSE parser、取消句柄和稳定错误分类。
- Core flow and reversibility: 显式构建请求；2xx 后回调 accepted；逐事件回调 reasoning/content；DONE 完成；取消关闭连接。
- Inputs/outputs: 输入 Key、模型、thinking、PromptPurpose/policyVersion 和已组装的有序 messages；输出 accepted、delta、completed 或分类错误事件。
- State and exception handling: 401/402/400/422/429/503、超时、EOF、非法 JSON、未知字段和取消分别映射。
- Constraints and trade-offs: HttpsURLConnection、JsonReader/Writer、无 tools、禁止明文流量和内容日志；网关不自行补 system 或改写消息顺序。
- AI recommendation and rationale: 独立网关屏蔽供应商 DTO，业务层不依赖网络分块或 HTTP 细节。
- User choice and custom additions: thinking 内容必须展示、落库并进入后续上下文。
- AI-autonomous decisions: 每次请求显式写 thinking 开关，不依赖供应商默认值。
- User-confirmation decisions: DeepSeek、两种模型和首版无后台自动续传。
- Acceptance evidence: fixture 服务器与受控真实 API 冒烟测试。
- Facts: 当前官方 base URL 为 `https://api.deepseek.com`；模型 ID 发布前必须复核。
- Assumptions: 用户提供测试 Key，且不写入仓库。
- Unknowns: 供应商未来字段或模型名可能变化。
- Risks: SSE 多行、UTF-8 边界、取消竞态、响应正文泄露。
- Deferred: tools、其他模型供应商和自动重连。
- Non-goals: 不落库、不组装上下文、不管理应用级并发。
- Skipped non-critical questions and reason: 连接池优化不影响 V1 正确性。

## Goal

提供可测试、可取消、不会混淆 reasoning/content 的 DeepSeek 流式边界。

## Scope

### In scope
- 请求/响应 DTO 映射（含 role/messages 保序）、余额查询、SSE parser、超时、取消、错误映射和脱敏网络测试。

### Out of scope
- system 提示词选择/组装、operation 状态持久化、上下文压缩和页面渲染。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增远端生成入口；保持 Key 与正文不进入日志。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): GenerationCoordinator 与派生 AI 用例消费；SecretStore 提供临时 Key。
- Native bridge or independent-client compatibility/update requirements: DeepSeek HTTP/SSE 是唯一外部契约。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `android/ai` 新增 `com.treelearn.ai.gateway` 包（端口、SSE 分帧、请求体、HTTPS 传输、载荷解码、错误分类、终态结果、工厂）；base URL `https://api.deepseek.com`；仅 HTTPS（`GatewayEndpoints.requireHttps` 在开连接前拒绝明文）；状态→错误类别映射（401/403、402、429、其它 4xx、5xx）；SSE 分帧规则（多行 `data:`、注释/心跳忽略、未知字段忽略、空行派发、`[DONE]`、EOF 无哨兵 → INTERRUPTED）。
- Authoritative code/config/migration evidence: `android/ai/src/main/java/com/treelearn/ai/gateway/**`（11 个类）与其 JVM / 设备测试；证据 `artifacts/evidence/T-006-red.txt`、`T-006-green.txt`、`T-006-tests.txt`、`T-006-device-isolated.txt`、`T-006-guard.txt`、`T-006-sizing.txt`。
- Documentation surfaces checked: 技术方案 §9.1/§9.2/§9.3/§9.5、提示词设计 V1.0、PRD 模型/思考/错误章节、`android/README.md`（grep `Gateway|gateway|SseFrameParser|ProviderJson|HttpsTransport|AiGatewayError|StreamOutcome` 零命中，即该包尚未进入 README）。
- Documentation files updated: 本次未改任何文档。写入范围被限定为 `android/ai/**`、本任务的 `:app` androidTest 类、`artifacts/evidence/T-006-*` 与本任务文件；`android/README.md` 的 `:ai` 行尚未描述 gateway 类，已记入 Handoff notes 作为未完成的文档项（需由 README 的负责方或获授权后补写）。
- Producer/consumer/sibling/compatibility paths checked: 余额、首次主题、普通生成、派生 AI、取消。
- Superseded phrases/examples reverse-searched: 旧模型名、默认 thinking 和旧响应字段。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次确有运行事实变化（新增 gateway 包与 HTTPS/错误映射契约），且该变化尚未写入 `android/README.md`。
- Historical/PRD handling: 不改写历史 changelog。

## Acceptance criteria

- [ ] 同一 parser/gateway 测试命令先 RED 后 GREEN，覆盖任意网络分块、多行 data、心跳、未知字段与 `[DONE]`。
- [ ] reasoning_content 与 content 交错时按各自顺序准确回调，EOF 无 DONE 不得报告成功。
- [ ] 401/402/422/429/503、超时、取消和解析失败均得到稳定错误分类并保留已回调部分。
- [ ] fixture 精确验证传入的 system/user/assistant role、顺序与 UTF-8 内容被 JsonWriter 原样编码；网关不新增、删除、合并或重排消息，日志只含用途/版本等脱敏元数据。
- [ ] 使用临时外部 Key 的真实冒烟测试验证余额、模型、thinking on/off；证据不含 Key、prompt 或响应正文。

## TDD execution record

### RED — failing test first
- Test/scenario: 先写测试后写实现。JVM 侧 SseFrameParserTest / AiGatewayStreamTest / AiGatewayBalanceTest / GatewayContractTest，设备侧 DeepSeekPayloadDecoderInstrumentedTest / DeepSeekRequestBodyInstrumentedTest / DeepSeekStreamInstrumentedTest；运行时 com.treelearn.ai.gateway 包尚不存在
- Command: cd android; .\gradlew.bat :ai:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon --console=plain --continue
- Exit code: 1
- Observed failure: javac 拒绝编译新测试源：:ai 报 100 个错误、:app 报 55 个错误（合计 155），逐条都是「程序包 … 不存在」或「找不到符号」
- Failure reason validated: 155 条诊断全部指向尚未存在的 gateway 类型（找不到符号 32 + 程序包GatewayPorts不存在 36 + 程序包AiGatewayError不存在 25 + 程序包StreamOutcome不存在 16 + 程序包com.treelearn.ai.gateway不存在 10 + 程序包AiGateway不存在 9 + 程序包ProviderJson不存在 4 + 程序包SseFrameParser不存在 1 + 方法不会覆盖或实现超类型的方法 22 = 155），没有一条来自测试自身或构建配置，故红由缺失的生产代码造成；逐类计数与取数命令见 T-006-red.txt 末段

### GREEN — minimum implementation
- Implementation: com.treelearn.ai.gateway 落地 11 个生产类（GatewayPorts 端口、SseFrameParser 分帧、JsonChatRequestBody 请求体、HttpsTransport 传输、ProviderJson 解码、AiGatewayError 分类、StreamOutcome 终态、GenerationSink 回调、GatewayEndpoints 端点、DeepSeekGatewayFactory 装配、AiGateway 编排），与 RED 完全相同的命令变为零退出码
- Command: cd android; .\gradlew.bat :ai:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon --console=plain --continue
- Exit code: 0
- Passing evidence: T-006-green.txt（EXITCODE=0、BUILD SUCCESSFUL in 53s、Starting 99 tests / Finished 99 tests on Medium_Phone_API_36.1(AVD) - 16）；T-006-tests.txt（gateway JVM 45 例 + 设备 16 例，0 失败）
- Regression evidence: T-006-tests.txt（:ai 全模块 9+16+8+12+8+8+11 = 72 例 0 失败，含并行 lane 的 com.treelearn.ai.prompt 套件；gateway 45 例取自 --rerun-tasks 的真实执行，同文件的 XML 计数与取数命令可复核）、T-006-device-isolated.txt（本任务 3 个设备类单独重跑 OK (16 tests)）、T-006-guard.txt（Java-only VERDICT: PASS，13 OK lines）

### REFACTOR — optional
- Cleanup: 没有单独的 GREEN 后重构轮次；GREEN 迭代中的三处修正（拒绝 accepted 后不得继续投递 delta、同一 chunk 内 refusal/sentinel 之后的事件必须丢弃、解析失败消息要对 stream 与 balance 两条路径都成立）都发生在被记录的 GREEN 运行之前，已由该运行覆盖
- Regression evidence: T-006-tests.txt（--rerun-tasks 真实执行，72/72）与 T-006-guard.txt（VERDICT: PASS）

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 无例外（先红后绿已执行）；sizing 超限但未获批准
- Reason: 本任务生产代码实测 749 非空非注释行（11 个文件，逐文件数字与取数命令见 T-006-sizing.txt），为 500 硬上限的 1.5 倍、200 目标的 3.7 倍；在保留范围内行为（余额查询、SSE 分帧、带内错误识别、取消与 accepted 闸门）的前提下，未找到能把非空非注释行压到 500 以下的改法
- User approval: 未取得。.trae/rules/harness-tasks.md 规定规模例外必须由用户明确批准、实现者自行追认无效，因此本任务不得判为完成：需要用户批准规模例外，或把本任务拆分
- Alternative verification: 等价证据齐备（RED / GREEN / 守门 / 设备三件套 / JUnit 计数），但规模闸门本身仍处于未通过状态

## Implementation notes

- 不允许按 read() 块直接 parse JSON；先完成 SSE 事件组装。
- accepted 回调必须由调用方同步确认后才继续读取 SSE body；确认失败立即关闭连接，禁止 delta 抢在本地 operation 事务之前到达。
- 原始请求/响应不得写日志；日志只记录状态、字符数、耗时和错误类别。
- 网关只传输 T-020 产出的消息；不得内置兜底 system、根据 thinking 替换正文或接受调用方传入可写日志的 prompt 字符串。
- 实现开始与发布前各复核一次 DeepSeek 官方文档。

## Verification report

### 规模例外——事后追认（用户批准，2026-09-24）

- Exception type: 规模（超 500 行硬上限）——**用户 2026-09-24 事后追认豁免**
- Reason: 实测 **748** 非空非注释行 / 500 硬上限，超 **49.6%**（前轮 verifier 独立复算，口径与 `sizing-adjudication-2026-09-21.txt` 一致）。DeepSeek SSE 网关 11 个生产类（连接、分帧、payload 解码、错误语义互相咬合，无自然拆分缝）；用户同日裁定不做回溯拆分（与 T-039 的拆分路径区别对待，理由：无自然切缝，硬拆产生的切片无法独立编译，属治理表演）。
- User approval: **用户 2026-09-24 经结构化选项明确批准本任务豁免**（同轮批准 T-006/T-007/T-011 三项，明确否决「同 T-039 拆分」与「维持现状」选项）。本批准发生在实现完成之后，按 `rules/harness-tasks.md` 规模条款**如实记为事后追认**，不写成事先批准。
- 实测行数与超出比例: 748 行 / 超 49.6%（判定口径：非空非注释；物理行并列记录于前轮 verifier 证据）。
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
- 审阅文件: 本任务文件；`android/ai/src/main/java/com/treelearn/ai/gateway/**`（11 个生产类）；
  `:app` 三个设备用例；证据 `T-006-{red,green,tests,guard,sizing,device-isolated}.txt`；
  verifier 证据 `VERIFY2-{jvm-suites,sizing,assemble-androidtest,guard,lint,static-findings}.txt`。
- 代码量: **748** 非空非注释行（verifier 自带计数器复算，11 文件；任务自报 749，差 1 在
  `AiGateway.java` 的 263/264，物理行 1121 两边一致）/ 500 硬上限。
  `sizing-adjudication-2026-09-21.txt` 的已批准例外表**不含本任务** → **规模判据 OPEN，未获批**。
  另按 `.trae` 裁定的 grep 口径复算 = **749**（与任务自报逐字相同，见 `VERIFY2-sizing-ruling-method.txt`）。
- 任务合规: PASS（template_version 2；`id: T-2026-09-20-006` 与文件名一致；`status: verifying`；
  RED/GREEN 五个键均为纯值、两条 `Command` 逐字节相同；`validate_tasks.py` 对本文件 PASS）。
- Lint: `:app:lintDebug` PASS（EXITCODE=0，`VERIFY2-lint.txt`）；`:ai` 为 java-library 无 lint 任务。
- 依赖方向: PASS（`verifyDependencyDirection: PASS`，EXITCODE=0）。
- 验收点对照:
  - [x] 1 先 RED 后 GREEN — verifier 亲自复跑 `:ai:testDebugUnitTest --rerun-tasks`：gateway 4 个类
        **45 例全绿（12+16+9+8）**，见 `VERIFY2-jvm-suites.txt`。RED 的 155 可核：
        `T-006-red.txt` 第 373 行 `100 个错误`、第 620 行 `55 个错误`，100+55=155；九类符号缺失
        之和亦为 155（36+32+25+22+16+10+9+4+1）。
  - [x] 2 两通道按各自顺序回调、EOF 无 `[DONE]` 不报成功 — JVM `SseFrameParserTest`/`AiGatewayStreamTest`
        覆盖，verifier 复跑全绿。
  - [x] 3 401/402/422/429/503、超时、取消、解析失败的稳定分类 — 判定收敛在
        `AiGatewayError.forHttpStatus` 单点，`Kind` 与《技术方案》§9.5 表逐条一致（多出的
        `PARSE_FAILED` 由本任务验收条件第 3 条明确要求）。
  - [ ] 4 请求体 role/顺序/UTF-8 原样编码 — 其证据是**设备用例** `DeepSeekRequestBodyInstrumentedTest`。
        **deferred — 本轮不使用模拟器（用户指令 2026-09-21）**。可得的最强替代：verifier 先清空
        `app/build/intermediates/javac/debugAndroidTest` 再跑 `:app:assembleDebugAndroidTest`，
        `:app:compileDebugAndroidTestJavaWithJavac` **真实执行**、产出 27 个 class、EXITCODE=0
        （`VERIFY2-assemble-androidtest.txt`）。**只证明这套 instrumentation 源码可编译，未执行任何用例。**
  - [x] 5 真实 Key 冒烟 — **blocked**：未提供 `TREELEARN_DEEPSEEK_API_KEY`，未对真实端点发起过请求；
        不以 fixture 冒充 live。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`，3 个 APK dex 级确认
  无 Kotlin 运行期字节码，EXITCODE=0）。
- 文档影响记录: **PASS** — 本任务自报「README 未描述 gateway 类」是**自报缺口而非矛盾**：
  `android/README.md:46` 已把 `:ai` 描述为「DeepSeek HTTPS/SSE、上下文编码、版本化提示词注册表与请求组装」；
  《技术方案》§9.1（base URL、仅 HTTPS）、§9.3（SSE 分帧规则）、§9.5（错误映射表）逐条与实现一致。
  README 只为 `:core`/`:data` 建类级索引，`:ai` 本就没有类级索引，故不列类名不构成「与现行事实矛盾」。
- 启动证据: N/A（库模块，无进程/端口/环境变量变化）。
- 结论: **PARTIAL**
- 修复指令: ① 规模：由用户明确裁定「批准 748 行例外（若在实现完成之后批准，须如实记为事后追认）」或
  按「SSE 分帧 + 载荷解码」/「传输 + 错误分类 + 编排」拆成两个任务；
  ② 真实 Key 冒烟待授权提供临时 key 后补跑，证据不得含 key、问题或正文。
- 阻塞点与剩余风险: 规模例外未批准；真实 Key 冒烟未执行；设备行为未执行（本轮明确 deferred）；
  供应商契约（模型 ID / base URL）发布前需按官方文档复核。
- 新型错误（建议追加 AGENTS.md 失败案例库）: **把现行文档已经覆盖的事实自报成「未完成的文档缺口」**
  → 会让 verifier 去修一个不存在的缺口（或据此误判 FAIL）。规则：自报文档缺口时必须给出
  「哪一行、与哪一条实现事实矛盾」的对照；给不出对照的按假缺口计，只作提示不作判据。

## Handoff notes

- Completed: android/ai 的 DeepSeek HTTPS/SSE 网关（11 个生产类）；4 个 JVM 测试类共 45 例；3 个设备测试类共 16 例；证据 T-006-red / T-006-green / T-006-tests / T-006-device-isolated / T-006-guard / T-006-sizing。
- In progress: 无。
- Blocker: ① 生产代码实测 749 非空非注释行，超过 500 硬上限且未获用户批准的规模例外（实现者不能自行追认，故本任务停在 verifying）；② 验收第 5 条的真实 Key 冒烟测试未执行——本次没有被提供任何 Key，也未对真实 api.deepseek.com 发起过请求。
- Unblock condition: 用户批准规模例外或把任务拆分；提供临时 TREELEARN_DEEPSEEK_API_KEY 后执行余额 / thinking on-off / 流式冒烟。
- Next steps: T-008/T-023 消费本网关（accepted 闸门、reasoning/content 双通道、终态）；android/README.md 的 :ai 行尚未描述 gateway 类，需由 README 负责方补写。
- Watch-outs: 发布前必须按 DeepSeek 官方文档复核 base URL 与模型 ID；reasoning_content 不得当作最终答案；网关不判定「流已正常结束且回答非空」，稳定化是调用方（T-008/T-023）的义务；日志只含 id / 状态 / 计数 / 耗时 / 模型 / 用途 / 策略版本 / 错误类别，绝不含 Key、system 正文、问题、回答或推理正文。
