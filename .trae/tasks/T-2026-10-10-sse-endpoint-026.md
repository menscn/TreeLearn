# Treelearn Harness Task: SSE 服务端端点（契约 §7）

id: T-2026-10-10-026
title: SSE 服务端端点（契约 §7：snapshot/seq/Last-Event-ID/心跳/终态关流）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 300
shared_code_touched: false
depends_on: [T-2026-10-10-backend-contract-dtos-025.md, T-2026-10-10-generation-coordinator-stabilizer-024.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 前端 `sse.ts` 与契约 §7 已定稿；服务端尚无事件流端点；事件总线（T-023）与协调器（T-024）已就绪。
- Users/scenarios: 订阅生成操作（正常流式/失败/中断/取消）；刷新/断线重连（Last-Event-ID 缺口补快照）；终态后订阅立即补快照并关流。
- Expected outcome: `GET /api/operations/{id}/events`（`text/event-stream`）：首帧 `snapshot`（由持久化事实构造）、实时转发总线帧（`seq` 单调）、`Last-Event-ID` 语义（缺口/未知→先补快照；缓冲内→补发缺失帧）、15s 心跳注释、终态关流、未知操作 404（错误封装）。
- Core flow and reversibility: 新端点；可回退。
- Inputs/outputs: 输入=操作 id/Last-Event-ID；输出=SSE 帧（`id:`/`event:`/`data:`）与关闭。
- State and exception handling: 操作不存在→404；已终态→snapshot+关流；订阅期间终态→转发后关流；客户端断开→清理订阅；心跳可配置。
- Constraints and trade-offs: 帧序列化复用 DTO 层（不新造形状）；seq 语义以总线为准（不另立计数）；不新增依赖（Spring MVC 的 SSE 支持）。
- AI recommendation and rationale: `ResponseBodyEmitter`/`SseEmitter` + 总线订阅；测试用真实 HTTP 客户端流式读取。
- User choice and custom additions: 已批准方案。
- AI-autonomous decisions: emitter 选择、心跳实现、测试注入方式。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-026-*` RED/GREEN + 真 HTTP 流证据（帧序列原文）。
- Facts: 总线事件类型/顺序见 T-023（契约 §7 逐条）；coordinator 可注入 stub 网关起真实操作。
- Assumptions: Spring Boot 4.1.1 MVC 提供 SSE 支持（`spring-boot-starter-webmvc`）。
- Unknowns: 心跳间隔的测试注入方式（配置属性或构造参数）。
- Risks: 订阅/取消竞态（客户端断开与终态同时）→ 测试覆盖；seq 与 snapshot 的一致性 → 以总线为准并测试断言。
- Deferred: 前端真实联调（Phase 5 后续任务）；派生任务流（后续）。
- Non-goals: REST 端点（T-028）；派生/分叉端点。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付契约 §7 的 SSE 端点，以真实 HTTP 流证据证明 snapshot/seq/缺口恢复/心跳/终态关流语义。

## Scope

### In scope
- `com.treelearn.web.api.OperationEventsController`（或等价）：`GET /api/operations/{id}/events`；快照构造（操作状态/部分内容/终态修订）；总线订阅与转发；`Last-Event-ID` 处理；心跳（可配置，测试用短间隔）；终态关流；404 错误封装。
- 帧写入器（`id/event/data` 格式；`data` 为 JSON，复用 DTO）。
- 测试：真实 HTTP（RANDOM_PORT + Java HttpClient 流式读取）：正常（snapshot→deltas→stable→关流）、失败/中断/取消三终态、`Last-Event-ID` 缺口→补快照、缓冲内→补发、未知操作 404、已终态→snapshot+立即关流、心跳出现在静默期、客户端断开后订阅清理（无泄漏断言）。

### Out of scope
- REST 端点；派生任务；前端联调。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新端点；ready 与既有端点不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 总线（生产者）、前端 `sse.ts`（消费者）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 服务暴露 SSE 端点（用户可见间接：流式体验）。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/api/**`。
- Documentation surfaces checked: 契约 §7/§11（索引）；`web/README.md` 状态节。
- Documentation files updated: `web/README.md` 状态一行。
- Producer/consumer/sibling/compatibility paths checked: 前端 `sse.ts` 的 `Last-Event-ID`/去重逻辑与端点语义对照。
- Superseded phrases/examples reverse-searched: 「SSE 服务端属 T-025」类表述。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `:test`，记录退出码与计数）；业务行 ≤350。
- [ ] 真实 HTTP 流：帧格式（`id:/event:/data:`）、`seq` 单调、八类事件按契约 §7.2；snapshot 首帧内容与持久化事实一致（测试断言部分内容/状态/修订）。
- [ ] 缺口恢复：`Last-Event-ID` 早于缓冲起点或未知→先 `snapshot`；缓冲内→补发缺失帧；语义与契约 §7.3 一致。
- [ ] 终态：终态帧后关流；已终态订阅→snapshot+关流；未知操作→404（错误封装形态正确）。
- [ ] 心跳：静默期出现注释行（短间隔配置）；客户端断开后订阅清理（无泄漏）。
- [ ] checkstyle 通过；独立 verifier PASS；contract-guardian 复核帧语义↔契约 §7。

## TDD execution record

### RED — failing test first
- Test/scenario: `OperationEventsControllerTest`（真实 HTTP 流）。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 1
- Observed failure: 首轮 158 项 10 失败（新端点全 404）；修复轮 161 项 4 失败（终态补帧/params/竞态三处新测试）
- Failure reason validated: 缺失端点；修复轮失败对应用例真实红灯
- Evidence: `artifacts/evidence/T-2026-10-10-026-red.txt`、`...-026-fix-red.txt`

### GREEN — minimum implementation
- Implementation: 控制器 + 帧写入器 + 心跳（+修复轮：终态补帧、params 透传、`subscribeAndCatchUp` 临界区）。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 0
- Passing evidence: 161 passed / 0 failed（`...-026-green.txt`、`...-026-fix-green.txt`）
- Regression evidence: 既有全绿（148→159→161）；`check` EXIT=0

### REFACTOR — optional
- Cleanup: 按检查整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 测试的短心跳间隔通过配置属性注入（80ms；生产默认 15s 未改）；心跳注释行 `: keepalive`。
- 帧 payload 复用 `api.dto` 记录；不新造字段名。
- 修复轮实现要点：`writeTerminalSnapshot` 后按 state 补终态帧；协调器 `settle` 传 `AiGatewayError`（`httpStatus>0` 时进 params）；`GenerationEventBus.subscribeAndCatchUp` 同临界区注册+补发。
- 已记录偏差：①终态 snapshot 的 FAILED params 为空对象（持久化仅存 errorCode，httpStatus 不可重建，合 §3.3 允许空对象）；②snapshot 的 state 随订阅时机（§7.2 当前态，合规）；③`id:1` 无空格（SSE 规范允许，消费端取 `data.seq` 去重）；④修复轮新增 `listenerCount` 只读访问器与装配 bean。

## Verification report

- Verdict: PASS（verifier 两轮：首轮 PARTIAL → 修复回归后 PASS；contract-guardian 首轮 PARTIAL（1 高危 2 中低）→ 修复后回归 PASS）
- Evidence: verifier 自跑 `:test --rerun-tasks` 161/0、`check` EXIT=0；**真实 HTTP**：`@SpringBootTest(RANDOM_PORT)` + `java.net.http.HttpClient` 流式读取（非 MockMvc）；green 逐帧原文（`id:`/`event:`/`data:`、seq 严格递增、stable 带完整 revision）；快照与持久化行一致断言；Last-Event-ID 两态；心跳（80ms 注入）；断开后 `listenerCount==0`。
- 修复记录（guardian 清单闭合）：① 订阅已终态 → snapshot + 对应终态帧（stable 含 revision）→ 关流，前端不再 `closed-before-terminal`（guardian 复核 `sse.ts` 结论）；② FAILED 帧带 `{httpStatus}`（429 stub 实流断言）；③ 订阅竞态：同临界区注册+补发，并发测试断言首帧 snapshot 且 seq 连续；④ 契约 §7.1 心跳文案与 §7.3 512 容量补注（guardian 复核仅两处、未扩散）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；+13 用例（148→161）。
- 规模：verifier 独立计数约 400（自定 ≤350 超线属估算偏低——警告级；≤500 硬上限未触）。
- Gaps: 浏览器端同源 fetch-event-source 的端到端复核（Phase 7 联调任务）；RATE_LIMITED 实流 params 已有 stub 级证据、真实 Provider 级待 Key。
- Risks: 心跳/断连清理在真实浏览器网络抖动下的表现（联调复核）。

## Handoff notes

- Completed: SSE 端点 + 双守卫 PASS（2026-10-10）。产物：`OperationEventsController`、`GenerationConfiguration`（端点与协调器共用同一总线）、`GenerationEventBus.subscribeAndCatchUp/listenerCount`；161 项测试全绿。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-028（REST 端点 + 9 个响应包裹建模/fixtures）→ 前端真实联调；随后发行打包。
- Watch-outs: seq 以总线为准；终态必发终态帧（订阅已终态亦补）；404 用错误封装；512 缓冲容量为契约已记录事实。