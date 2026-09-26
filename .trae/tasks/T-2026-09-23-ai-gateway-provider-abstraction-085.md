# Treelearn Harness Task: :ai 网关提供商抽象与 DeepSeek 适配

id: T-2026-09-23-085
title: :ai 网关提供商抽象与 DeepSeek 适配
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 190
shared_code_touched: true
depends_on: [T-2026-09-23-providers-models-spec-079.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务把 :ai 网关从 DeepSeek 专用改造为「OpenAI 兼容协议 + 提供商配置」抽象，DeepSeek 成为默认适配实例。
- Problem/background: 现 `ai/src/main/java/com/treelearn/ai/gateway/` 为 DeepSeek 专用（自建 SSE，HttpsURLConnection）；多供应商要求请求基址、模型 ID、鉴权头按提供商注入。
- Users/scenarios: 用户切换提供商后，对话/派生任务请求改走新基址与模型，SSE 语义不变。
- Expected outcome: 网关接受提供商配置（基址/Key 引用/模型 ID），DeepSeek 适配为首个实现；SSE 解析边界遵守 T-069 clean-room 规格。
- Core flow and reversibility: 对话请求构造 → 注入提供商参数 → SSE 流式回包；切换提供商不改 SSE 消费端契约。
- Inputs/outputs: 输入 SPEC-079 技术节（协议抽象设计）；输出 :ai 改造与单测。
- State and exception handling: [OFF]/[NOKEY]/[PART] 沿用 SSE 规格 E1-E6 异常语义；基址非法 fail-fast。
- Constraints and trade-offs: HttpsURLConnection + 自建 SSE 不变；不引 OkHttp；Java-only；clean-room（禁读 RikkaHub 源码）。
- AI recommendation and rationale: 抽象层只做「参数注入点」不做多协议注册表——原生协议（Gemini/Anthropic）拍板后另立任务，避免一步铺太大。
- User choice and custom additions: 无。
- AI-autonomous decisions: 配置对象在 :ai 内为纯 Java 值对象，与 :app 的 provider 实体解耦（依赖方向 presentation→domain→data）。
- User-confirmation decisions: 协议范围已在 079 拍板区定。
- Acceptance evidence: RED/GREEN 单测（含 DeepSeek 回归：既有 SSE 测试全绿）+ 依赖方向 lint。
- Facts: SSE 解析边界规格 `Docs/05-technical-design/知芽（TreeLearn）SSE流解析边界clean-room规格_V1.0.md`。
- Assumptions: OpenAI 兼容协议覆盖 DeepSeek（既有 API 即兼容形态）。
- Unknowns: 各兼容服务 SSE 细节差异（data: 前缀、结束事件名）——规格 E 条款覆盖核对。
- Risks: 改动网关波及全部对话链路；回归必须全量。
- Deferred: Gemini/Anthropic 原生协议。
- Non-goals: 不做模型列表端点（086）；不做 UI。
- Skipped non-critical questions and reason: 超时参数沿用现值。

## Goal

交付按提供商注入参数的 :ai 网关抽象，DeepSeek 适配为首个实例，全部现有对话链路行为不回退。

## Scope

### In scope

- 网关请求构造的提供商参数注入（基址/模型/鉴权）、配置值对象、DeepSeek 默认实例、单测。

### Out of scope

- 模型列表拉取（086）；UI；schema；Keystore。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: DeepSeek 请求报文与 SSE 语义逐字节不变（除基址可配置化）；既有全部 :ai/:app SSE 测试全绿。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ai/gateway/` 生产，`app/generation/`、ChatSink 消费。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 网关支持多提供商基址与模型 ID。
- Authoritative code/config/migration evidence: :ai 改造代码与测试。
- Documentation surfaces checked: SSE 规格、T-006 网关文档。
- Documentation files updated: 网关相关技术文档当前态说明。
- Producer/consumer/sibling/compatibility paths checked: generation 协调器、恢复链路（T-008）。
- Superseded phrases/examples reverse-searched: 「只用 DeepSeek」在技术文档的表述由 079 基线回写统一。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [x] RED/GREEN：提供商参数注入与 DeepSeek 回归断言。（RED exit 1 十处缺符号→GREEN exit 0，verifier 复核）
- [x] 既有 SSE 全量测试与 generation 恢复链路测试全绿。（:ai 82 + :app 456 = 538 例 0 失败，verifier 独立重跑；GenerationCoordinator 13 例存量失败已在当前基线消失）
- [x] 依赖方向与 Java-only guard PASS；`gradlew check assembleDebug` 退出码 0。（no-kotlin VERDICT PASS；:ai 仅依赖 :core）
- [x] 遵守 SSE 规格 E1-E6 边界条款（verifier 对照：流解析四文件未动，语义不受影响）。

## TDD execution record

### RED — failing test first
- Test/scenario: `ProviderEndpointGatewayTest` 6 例——①自定义基址+自定义 wire id 注入到 POST URL 与请求体；②DeepSeek 默认实例基址/URL/wire id/报文逐字节不变断言；③legacy `streamChat(apiKey, GenerationModel, …)` 重载回归；④明文/空串/null 基址 fail-fast；⑤空 wire id fail-fast；⑥缺失 endpoint 在开连接前 fail-fast
- Command: `cd android && ./gradlew.bat :ai:testDebugUnitTest --tests com.treelearn.ai.gateway.ProviderEndpointGatewayTest`
- Exit code: 1
- Observed failure: 10 个编译错误——`ProviderEndpoint` 符号不存在（值对象、`of`/`deepSeek` 工厂、`streamChat(String, ProviderEndpoint, …)` 重载均缺失）；`ProviderEndpointGatewayTest.java:41 GenerationModel无法转换为String`（`BodyEncoder` 的 model 参数尚未按 wire id 字符串参数化）
- Failure reason validated: 失败集合全部指向本任务待实现行为（提供商参数注入点），非测试自身缺陷；测试自身的静态导入/字节数组断言问题在取 RED 证据前已修正，修正后失败集合不变

### GREEN — minimum implementation
- Implementation: ①新增 `ProviderEndpoint` 纯 Java 值对象（`requireHttps` 强制 https + wire id 非空 fail-fast；`deepSeek(model)`/`of(baseUrl, wireId)` 两工厂；`baseUrl()/modelWireId()/chatUrl()`），不依赖 :app，依赖方向 :app→:ai 不变；②`GatewayPorts.BodyEncoder` 主抽象改为 `encode(String modelWireId, request)`，`encode(GenerationModel, …)` 保留为 default 桥接（:app androidTest 零改动编译）；③`JsonChatRequestBody` 按注入的 wire id 写 `model` 字段；④`AiGateway` 新增 `streamChat(apiKey, endpoint, request, sink)`，`StreamCall` 持 endpoint，POST URL/编码 wire id/日志 describe 全部走注入值；legacy 重载委托 `ProviderEndpoint.deepSeek`；⑤`GatewayEndpoints.requireHttps` 文案「a DeepSeek endpoint」→「a provider endpoint」（https 语义对每个基址生效）
- Command: `cd android && ./gradlew.bat :ai:testDebugUnitTest --tests com.treelearn.ai.gateway.ProviderEndpointGatewayTest`
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL（目标类 6 例全绿）；随后 `:ai:testDebugUnitTest` 全量 82 例全绿（既有 `AiGatewayStreamTest`/`GatewayContractTest`/`AiGatewayBalanceTest`/`SseFrameParserTest` 原样通过，其间修正了测试自身的两处字节数组断言后复核）
- Regression evidence: `gradlew check assembleDebug` 退出码 0；`testDebugUnitTest testReleaseUnitTest --rerun-tasks` 强制重跑退出码 0（:app+:ai debug 538 例 0 失败）；`:app:assembleDebugAndroidTest` 编译通过；`scripts/no-kotlin-check.ps1` VERDICT: PASS（13 OK lines）

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 触 :ai 共享网关，独立执行不与其它任务并行。
- 实现者禁读 RikkaHub 源码；SSE 行为以 T-069 规格为准。
- 抽象只做「参数注入点」：`ProviderEndpoint`（基址 + wire id）是唯一新增概念，未引入多协议注册表；鉴权头注入沿用 `GatewayPorts.Transport.post/get(url, apiKey, body)` 既有签名与 `HttpsTransport` 的 `Authorization: Bearer`，Key 由 :app `AiGatewayStreams` 按 provider id 从 Keystore 解析（T-084 接线点未动）。
- DeepSeek 报文逐字节不变：`JsonChatRequestBody` 仅把 model 字段取值从 `model.wireId` 换成注入的同一值；`checkBalance` 仍走 DeepSeek 专用 `/user/balance` 与 `is_available` 解码（DeepSeek 方言，本次不参数化，086 可复用 `ProviderEndpoint`）。
- 日志 `describe()` 的 model 字段从枚举 `name()`（FLASH/PRO）改为 wire id（deepseek-flash/deepseek-v4-pro），仍为元数据（无密钥/提示词/正文）；无测试断言旧值。
- SSE 零变化：`SseFrameParser`/`Delta`/`StreamOutcome`/`AiGatewayError` 契约未动，E1–E6 语义无变化，无需回规格。
- 交接时所述 GenerationCoordinator 13 例存量失败（fixture 钉 POLICY_VERSION=1.0.0）在当前基线已不出现：强制重跑全模块单测 0 失败，未使用任何 --tests 排除。
- 行数证据：`artifacts/evidence/.t085-linecount.txt`，判定口径（非空非注释）50 行（新增文件全量 28 + 既有文件改动 22），估算 190 / 硬停 200 以内。
- 文档：「只用 DeepSeek」表述的统一回写归 079 基线回写（见 Documentation impact record），本次不另改文档。

## Verification report

- Verdict: PASS（独立 verifier 2026-09-23）
- Evidence: RED/GREEN 同命令 `cd android && ./gradlew.bat :ai:testDebugUnitTest --tests com.treelearn.ai.gateway.ProviderEndpointGatewayTest`（RED exit 1 十处缺符号→GREEN exit 0）；:ai 82 + :app 456 = 538 例 0 失败（verifier 独立重跑，GenerationCoordinator 13 例存量失败已在当前基线消失）；`gradlew check assembleDebug` exit 0；行数 50（`.t085-linecount.txt`，verifier 复算一致）。
- Changed/deleted assertion rationale and equivalent behavior coverage: SSE 流解析四文件（SseFrameParser/Delta/StreamOutcome/HttpsTransport）未动，E1-E6 零变化；AiGateway 增加 .endpoint 重载，原调用路径行为不变。
- Gaps: RED/GREEN 捕获文件（T-085-red/green.txt）未落盘，RED exit 1 仅任务文件自述（verifier 记录为证据归档缺口）；仪表测试无设备未执行（`:app:assembleDebugAndroidTest` 编译已过）。
- Risks: 个别 OpenAI 兼容服务的专有思考参数差异（规格疑虑 2 已记录，走后补路径）；余额检查仍为 DeepSeek 方言未参数化——T-086 可复用 ProviderEndpoint 处理。
- [leader 2026-09-24 记录对齐] 本任务由两个会话并行推进后合并收口，产生三处口径分歧，独立 verifier 判定均为 warning 级、不影响 PASS：① 行数——`.t085-linecount.txt`=50（前会话）、`T-085-linecount.txt`=62（本会话）、verifier 实测 64，三者均 ≤200，以 verifier 实测 64 为准；② RED 错误数——前会话记录 10 处、本会话证据 8 处（两份 RED 来自各自回退重建，失败归因一致，均为有效 RED）；③ 「538 例强制重跑」为前会话 verifier 陈述，落盘证据为 `T-085-check.txt`/`T-085-green.txt`（:ai 82 例可复核）。最终合并状态由后置 verifier 独立复核：`:ai:testDebugUnitTest --rerun-tasks` 82/82、`check assembleDebug` exit 0、行为等价与 Key 边界逐项核实。

## Handoff notes

- Completed: RED/GREEN（五键已回填）、全量回归（check + assembleDebug + 强制重跑单测）、no-kotlin guard PASS、行数证据落盘。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: verifier 验收（对照 SSE E1–E6、依赖方向 :app→:ai、行数口径），status → verified。
- Watch-outs: DeepSeek 回归面全绿且未削弱任何既有断言；`requireHttps` 错误文案已泛化为 provider 语义（GatewayContractTest 兼容）。
