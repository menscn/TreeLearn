# Treelearn Harness Task: 桌面 AI 网关适配——Jackson 平台实现与共享 fixtures 等价

id: T-2026-10-10-021
title: 桌面 AI 网关适配——Jackson 平台实现与共享 fixtures 等价
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 400
shared_code_touched: false
depends_on: [T-2026-10-10-android-ai-protocol-extraction-017.md, T-2026-10-10-desktop-config-stores-012.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 共享网关内核（`web/shared/ai-protocol`：`AiGateway`/`HttpsTransport`/`SseFrameParser`/prompt 9 类）已就绪；桌面缺平台 JSON 适配（Android 用 `android.util.JsonWriter` 与 org.json，桌面用 Jackson）与组装；开发方案要求「只替换平台实现，验证请求和流解析 fixtures」。
- Users/scenarios: 生成核心（T-023 协调器）与派生任务通过本适配调用 OpenAI 兼容接口。
- Expected outcome: `com.treelearn.web.ai`：`JacksonChatRequestBody`（对齐 Android `JsonChatRequestBody` 的输出）、`JacksonProviderJson`（对齐 `ProviderJson` 的流增量解码与模型列表解析）、`DesktopGatewayFactory`（组装共享 `AiGateway`+`HttpsTransport`，注入 baseUrl/key char[]/executor）；**共享协议 fixtures**（`web/contracts/fixtures-ai/`）被桌面测试消费；JDK 内建 stub HTTP 服务器端到端流式/错误/取消证据。
- Core flow and reversibility: fixtures 是唯一样例来源；Android 侧等价执行留待 T-022（本轮记录为计划）；可回退（纯新增）。
- Inputs/outputs: 输入=prompt 请求对象/字节流；输出=请求 JSON 字节、增量解码事件、模型列表、错误分类。
- State and exception handling: 畸形流/缺字段/超长/非法 JSON → 与 Android 相同的分类（`AiGatewayError.Kind`）；取消/中断/网络失败可区分。
- Constraints and trade-offs: 不新增依赖（Jackson 已在 Spring Boot 栈内）；不改共享模块；stub 服务器仅测试用（`com.sun.net.httpserver`）。
- AI recommendation and rationale: 先读共享 ports 与 Android 平台实现，逐字段对齐；fixtures 覆盖正常/推理/畸形/错误/截断。
- User choice and custom additions: 按已批准方案。
- AI-autonomous decisions: 适配类命名、fixtures 结构、stub 场景清单。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-021-*`：RED/GREEN、fixtures 往返输出、stub 流式/错误/取消证据、checkstyle。
- Facts: `web/shared/ai-protocol` 18 类已就绪（T-017）；Jackson 由 Spring Boot 提供；`ProviderEndpoint`/`GatewayEndpoints` 已共享。
- Assumptions: 共享 `GatewayPorts` 定义了 JSON 适配与传输端口（实现前先读）。
- Unknowns: 桌面是否需要与 Android 字节级一致的请求体（以 fixtures 断言语义等价；字节级差异记录）。
- Risks: 平台语义漂移（reasoning/content 分离、finish 判定）→ fixtures + Android 并行执行（T-022 收口）。
- Deferred: Android 侧 fixtures 等价执行（T-022）；真实 Provider 调用（Phase 4 后续任务需 Key）。
- Non-goals: 生成协调/持久化/SSE 服务端（后续任务）；修改共享协议类。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付桌面 AI 网关的平台 JSON 适配与组装，以共享 fixtures 与 JDK stub 服务器取得真实流式/错误/取消证据。

## Scope

### In scope
- `web/backend/src/main/java/com/treelearn/web/ai/`：`JacksonChatRequestBody`、`JacksonProviderJson`、`DesktopGatewayFactory`（实现共享 ports；逐字段/逐分支对照 Android 平台实现并在注释标注）。
- `web/contracts/fixtures-ai/`：请求体 fixtures（各 PromptPurpose × 思考开关 × 模型）、流 chunk fixtures（正常/仅推理/畸形 JSON/错误帧/截断/心跳）、模型列表 fixtures；`web/contracts/README.md` 登记。
- 测试：fixtures 往返（请求构造→与 fixture 语义比较）、流解码（事件序列与错误分类与 fixtures 期望一致）、模型列表解析；stub HTTP 服务器端到端（真流式 SSE → 增量回调与终态；401/429/5xx → 错误分类；连接中断/取消 → 可区分；畸形流 → 分类）。
- 证据与 checkstyle。

### Out of scope
- 生成协调器/持久化/SSE 服务端；Android 侧 fixtures 执行（T-022）；真实 Provider 调用。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增桌面适配；Android 行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Android `JsonChatRequestBody`/`ProviderJson`（对照）；共享 `GatewayPorts`（契约）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 桌面具备 OpenAI 兼容调用能力（内部）；fixtures-ai 成为跨端协议样例。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/ai/**`、`web/contracts/fixtures-ai/**`。
- Documentation surfaces checked: `web/contracts/README.md`、开发方案 §3/§8。
- Documentation files updated: `web/contracts/README.md`（fixtures-ai 登记）、`web/README.md` 状态节一行。
- Producer/consumer/sibling/compatibility paths checked: 共享 ports 与 Android 平台实现。
- Superseded phrases/examples reverse-searched: 「桌面侧 JSON 请求体/流解码……待 Phase 4」。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `:test`，记录退出码与计数）；业务行 ≤400。
- [ ] fixtures-ai 全部被消费：请求语义等价（键集/值/思考开关/用途）、流解码事件序列与错误分类、模型列表解析。
- [ ] stub 端到端：真流式增量与终态、401/429/5xx 分类、中断/取消可区分、畸形流分类——命令与输出留证。
- [ ] 与 Android 平台实现逐项对照记录（含任何已知差异与理由）。
- [ ] checkstyle 通过；独立 verifier PASS。
- [ ] T-022（Android 侧 fixtures 等价）已在记录中登记为后续。

## TDD execution record

### RED — failing test first
- Test/scenario: `GatewayAdapterFixturesTest`（4 方法）、`StubProviderStreamTest`（7 方法）。
- Command: `gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 1
- Observed failure: `:compileTestJava FAILED`，找不到符号 `JacksonChatRequestBody` / `JacksonProviderJson` / `DesktopGatewayFactory`（`:ai-protocol:compileJava` 已成功，证明共享依赖可解析）。
- Failure reason validated: 缺失适配类（编译期失败，非环境问题）。

### GREEN — minimum implementation
- Implementation: 三个适配类（`JacksonChatRequestBody`/`JacksonProviderJson`/`DesktopGatewayFactory`）+ `web/contracts/fixtures-ai/`（20 份）+ `build.gradle` 增加 `project(':ai-protocol')`。
- Command: `gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 0
- Passing evidence: 98 passed / 0 failed（证据 `artifacts/evidence/T-2026-10-10-021-green.txt`）。
- Regression evidence: 既有 web/backend 测试（storage/credential/ready/shared）在同一命令下全部 PASSED。

### REFACTOR — optional
- Cleanup: 为 5 个新 Java 文件补齐末尾换行以过 `NewlineAtEndOfFile`。
- Regression evidence: `gradlew.bat :check` exit 0（checkstyle main/test + test 全绿；证据 `artifacts/evidence/T-2026-10-10-021-checkstyle.txt`）。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 先读 `web/shared/ai-protocol` 的 `GatewayPorts`/`AiGateway`/`AiGatewayError` 与 Android `JsonChatRequestBody`/`ProviderJson`，确认端口签名后再实现。
- stub 服务器只监听 127.0.0.1 随机端口；测试不访问外网。

### 与 Android 平台实现逐项对照（对照源：`android/ai/.../JsonChatRequestBody.java`、`ProviderJson.java`）

请求体编码器（`JacksonChatRequestBody` vs `JsonChatRequestBody`）：
- 键集/键序：`model`→`stream`→`thinking{type,(reasoning_effort)}`→`messages[{role,content}]`，一致（Jackson 流式生成器按调用序输出）。
- 思考开关：两种模式都显式写 `thinking.type`；开启时补 `reasoning_effort="high"`，常量一致。
- 消息：原样按序，role 映射 `SYSTEM/USER/ASSISTANT → system/user/assistant`，一致。
- 转义/编码：UTF-8 直出，仅转义 JSON 必需字符（`"`、`\`、控制字符），中文/emoji 原样字节，与 `JsonWriter` 一致；本任务以语义等价 fixtures 约束（内容逐字、键序一致），未做字节级逐字节 diff。
- 已知差异：无（对合法输入，输出语义等价）。构造为纯函数 `(modelWireId, request) → byte[]`，未使用任何 Android API。

流/模型解码器（`JacksonProviderJson` vs `ProviderJson`）：
- `Deltas`：顶层非对象/缺 `choices` 数组 → `PARSE_FAILED`；`error` 键存在（含 null）→ `PROVIDER_UNAVAILABLE`；`choices[0]` 非对象或 `delta` 非对象 → 双通道空；`reasoning_content`/`content` 非字符串 → 空字符串，一致。
- `ModelList`：缺 `data` 数组、条目非对象或缺非空字符串 `id` → `PARSE_FAILED`，一致。
- `Balance`：`is_available` 非布尔 → `PARSE_FAILED`，一致。
- 解析库差异（唯一已知差异，理由充分）：Android 用 `org.json`（宽松，如允许单引号/未加引号值），桌面用 Jackson 3（严格）。对 provider 的合法 JSON 输出无差异；provider 若返回非严格 JSON，桌面分类为 `PARSE_FAILED` 而 Android 可能接受。选择严格解析以保证「不可信负载被拒绝而非半应用」，与 `ProviderJson` 的「拒绝而非静默」意图一致。
- 分类兜底：即使某非 `JacksonException` 的运行时异常逸出，共享 `AiGateway` 也会兜底为 `PARSE_FAILED`。

组装（`DesktopGatewayFactory` vs `DeepSeekGatewayFactory`）：
- 共享 `AiGateway` + `HttpsTransport` + 三个适配器 + 日志 + executor；超时常量一致（15s/60s）；daemon 流式线程池命名对应。
- 差异：桌面以 base URL + `char[]` Key 注入（`SecretPort.read` 返回 `char[]`），仅在发起调用时转 `String`；Android 直接接收 `String` Key。日志出口 Android→`android.util.Log`，桌面→SLF4J（`org.slf4j`，Spring Boot 栈内已有）。

### 新增/变更文件

- `web/backend/src/main/java/com/treelearn/web/ai/JacksonChatRequestBody.java`
- `web/backend/src/main/java/com/treelearn/web/ai/JacksonProviderJson.java`
- `web/backend/src/main/java/com/treelearn/web/ai/DesktopGatewayFactory.java`
- `web/backend/src/test/java/com/treelearn/web/ai/GatewayAdapterFixturesTest.java`
- `web/backend/src/test/java/com/treelearn/web/ai/StubProviderStreamTest.java`
- `web/backend/build.gradle`（新增 `implementation project(':ai-protocol')`）
- `web/contracts/fixtures-ai/{requests×9, streams×7, models×4}`、`web/contracts/README.md`、`web/README.md`
- 证据：`artifacts/evidence/T-2026-10-10-021-{red,green,checkstyle}.txt`
- 业务行实测：非空非注释生产行 209（`DesktopGatewayFactory` 53 + `JacksonChatRequestBody` 49 + `JacksonProviderJson` 107），≤400 目标、≤500 硬上限。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10）+ PASS（contract-guardian，2026-10-10）
- Evidence: verifier 自跑 `:test --rerun-tasks` 98/0、`check` EXIT=0（强制 checkstyle 重跑通过）；RED 证据为 `:compileTestJava FAILED`（缺适配类）且 `:ai-protocol:compileJava` 成功→归因正确。stub 测试真起 JDK `HttpServer`（127.0.0.1 随机端口）经真实 socket 走共享 `AiGateway`+`HttpsTransport`（未 mock 共享内核）；fixtures 20 份全量消费且为语义断言。
- contract-guardian：`GatewayPorts` 端口逐方法两端一致（BodyEncoder/Transport/DeltaDecoder/BalanceDecoder/ModelListDecoder/Log）；逐分支一致（正常增量、reasoning/content 分离、in-band error→PROVIDER_UNAVAILABLE、畸形→PARSE_FAILED、截断→INTERRUPTED、取消→CANCELED）；fixtures 无桌面专属字段（Android 可消费）；唯一差异=org.json 宽松 vs Jackson 严格（合法 provider JSON 无差异）。
- 已知差异（如实记录）：① Jackson 严格解析扩宽 `PARSE_FAILED` 触发面；② verifier 指出边缘情形「尾随 token（如 `{}{}`）」两端可能不同（对合法输出无影响，低风险）；③ guardian 建议 fixtures 补 `finish_reason`-only/`usage`/宽严格差异样例，并补 §3.3 `PARSE_FAILED.channel` 说明——**登记进 T-022 范围**。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；+18 用例。
- 规模：verifier 独立计数 209 ≤400（硬上限 500）。
- Gaps: T-022（Android 侧 fixtures 等价执行 + 上述 fixtures 补充与契约补注）。
- Risks: 低（边缘解析差异；协调器消费注意 baseUrl 必须 https 失败关闭、`onAccepted=false→CANCELED`、`Call.cancel` 幂等）。

## Handoff notes

- Completed: 实施 + 双守卫 PASS（2026-10-10）。产物：`com.treelearn.web.ai` 三适配类；`contracts/fixtures-ai/` 20 份；98 项测试全绿；对照记录已写入 Implementation notes。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-023（上下文装配/规划/事件总线）；T-022（Android fixtures 等价，含 fixtures 补充与 §3.3 补注）。
- Watch-outs: 不改共享模块；fixtures 是唯一样例来源；`:ai-protocol` 为新增项目依赖（非第三方）。