# Treelearn Harness Task: 关闭思考时阻断推理标记泄漏

id: T-2026-10-01-118
title: 关闭思考时阻断推理标记泄漏
template_version: 2
task_type: implementation
status: in_progress
parent_requirement: T-2026-10-01-android-daily-use-issues-111.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 170
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

Parent summary: T-2026-10-01-android-daily-use-issues-111.md 的 #7 报告关闭思考后偶尔仍在可见回答中出现 `treelearn_reasoning` 标记。

- Problem/background: 用户关闭思考过程后，仍可能看到推理协议标记；当前 AI 网关分别传递 reasoning/content，消息行的 ReasoningDisclosure 受 thinkingEnabled 控制，历史上下文编码器另有结构标签。
- Users/scenarios: 关闭思考设置后生成普通回答；开启思考时维持现有独立披露；模型/网关可能将 reasoning delta 与 answer delta 分片到达。
- Expected outcome: 关闭思考时推理通道内容及协议包装不能混入可见最终回答；开启时仍按既有 UI 规则单独展示思考，不破坏真正的 answer。
- Core flow and reversibility: 读取开关 → 接收 reasoning/answer delta → 按通道和协议解析 → 持久化/翻译 → 聊天行展示；设置切换仍可恢复原有思考披露行为。
- Inputs/outputs: thinkingEnabled、分离的 reasoning/content 流及可能跨 chunk 的协议标记；输出为不泄漏内部推理标签的用户可见回答。
- State and exception handling: 分片标签跨 delta、缺少结束标签、空 reasoning、失败/取消和历史读回均不得把内部内容误当普通回答；不能因过滤丢掉合法答案。
- Constraints and trade-offs: Java-only；不改外部 AI provider/请求契约；`DeepSeekContextEncoder` 的历史上下文格式与当前输出解析必须区分；不删除历史数据或把推理写入日志。
- AI recommendation and rationale: 在应用拥有的消息通道/协议边界处理，优先使用明确字段而非对完整回答做宽泛字符串替换。
- User choice and custom additions: 用户明确反馈关闭思考后偶尔出现 `treelearn_reasoning`，要求本轮只拆 Harness。
- AI-autonomous decisions: 思考开启时保留受控 ReasoningDisclosure；测试 reasoning 与 answer 分片交错、包装标签拆分和异常终止。
- User-confirmation decisions: 思考关闭时不得把内部 reasoning 或其协议边界显示在最终答案中。
- Acceptance evidence: 单测覆盖通道分离/标签分片/失败边界，UI 投影测试及 MagicOS 11 实机生成观察。
- Facts: 主生成链中 `GenerationCoordinator.onDelta` 将 `reasoningChunk`/`contentChunk` 分别写入 `GenerationOperation` 与 `StreamBatch`，再由 flush 持久化；读取后 `ChatScreen.Row` 把 `message.text` 作为回答正文并把 `message.reasoning` 交给 `ReasoningDisclosure.render(..., state.thinkingEnabled)`。`DeepSeekContextEncoder` 定义的 `<treelearn_reasoning>`/`<treelearn_answer>` 是历史上下文 framing；当前生产源码未发现对应的流式输出 parser。
- Assumptions: 反馈指用户可见回答中出现的标记，而非任务/代码/日志中的文本。
- Unknowns: 用户看到的字面标签是否由 provider 回显、来自 `contentChunk`，以及跨 chunk/缺少结束标签时应如何安全处置；尚无目标设备可见输出样本。
- Risks: 粗暴删除 token 子串可能破坏用户合法回答；在上下文编码器过滤可能损坏后续对话语义。
- Deferred: provider 模型输出质量、思考请求策略或服务端行为不在本任务。
- Non-goals: 隐藏 thinkingEnabled=true 时已有的独立思考披露；改造 AI provider、prompt policy、DB schema 或远程调用边界。
- Skipped non-critical questions and reason: N/A — 用户可见目标明确；泄漏通道与根因仍按 Unknowns 通过生产链路和 RED 测试核实，不需扩大业务澄清范围。

## Goal

关闭思考时阻止内部 reasoning 内容/协议包装进入可见最终回答，同时保持思考开启时的独立披露与真实 answer 完整。

## Scope

### In scope
- 追踪 `GenerationSink.onDelta` → `GenerationCoordinator` → `StreamBatch`/`MessageTranslator` → `ChatScreen.Row`/`ReasoningDisclosure` 的生产消费者链。
- 先添加可证伪测试：关闭思考时 reasoning-only 与标记跨 chunk 输入不泄漏；开启思考时 disclosure 仍可见；合法 answer 不被误删。
- 仅在应用控制的通道/协议消费处修复；若动生成处理流程，遵循现有脱敏本地日志约定，不记录正文、推理、prompt 或密钥；明确无远程行为遥测。

### Out of scope
- 修改 provider 请求/API、提示词策略、历史上下文标签定义、schema 或推理内容持久化治理；宽泛字符串替换所有答案。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 思考关闭时不显示 internal reasoning/协议包装；思考开启时维持 ReasoningDisclosure；答案正文及消息顺序不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `GenerationGateway` stream callback → `GenerationCoordinator.onDelta` → `GenerationOperation`/`StreamBatch` → flush/persist → chat read → `ChatScreen.Row.message.text` 与 `ReasoningDisclosure`；`MessageTranslator` 由独立翻译动作调用，不是主生成消费者，作为 sibling 回归面核对；`DeepSeekContextEncoder` 只作历史上下文对照。
- Native bridge or independent-client compatibility/update requirements: N/A — 不改 provider 契约或桥接。
- Behavior-neutral non-applicability reason, if applicable: N/A — 修正生成内容的用户可见边界。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 思考关闭时推理通道/协议包装不进入可见最终回答；思考开启的独立披露保持不变。
- Authoritative code/config/migration evidence: `GenerationCoordinator.java`、`StreamBatch.java`、`GenerationOperation`/持久化写入路径、`ChatScreen.java` 与 `ReasoningDisclosure.java`；`MessageTranslator.java` 是独立 sibling，`DeepSeekContextEncoder.java` 是历史 framing 对照。
- Documentation surfaces checked: DeepSeek 提示词设计、AI 生成内容显示约束、`android/README.md` 与当前思考开关行为。
- Documentation files updated: 实现时同步用户可见思考开关和回答边界说明。
- Producer/consumer/sibling/compatibility paths checked: 网关双通道、普通内容、推理 disclosure、历史读回、失败/取消与派生任务消费者。
- Superseded phrases/examples reverse-searched: 实现后反查 `treelearn_reasoning`、thinkingEnabled 和“关闭思考”相关用户可见说明。
- `documentation impact: none` reason and evidence, if applicable: N/A — 用户可见生成显示语义变化需同步文档。
- Historical/PRD handling: 不改写历史 prompt 设计；区分历史上下文编码标签与当轮输出。

## Acceptance criteria

- [ ] RED 测试证明关闭思考时 reasoning-only delta、完整/跨 chunk 协议包装不会进入 visible answer；失败/取消和缺失结束标记不造成泄漏。
- [ ] thinkingEnabled=true 时现有独立思考披露仍工作；合法 answer（含普通文字、代码/用户讨论标记的场景）不被宽泛过滤误删；历史上下文编码回归通过。
- [ ] 目标设备确认关闭思考后的回答不显示 `treelearn_reasoning` 包装或 reasoning；Java-only guard、脱敏日志约束和受影响文档检查有证据，生产改动不超过 200 行。

## TDD execution record

### RED — failing test first
- Test/scenario: `ChatScreenReasoningLeakInstrumentedTest.thinkingDisabledAnswerProjectionHidesFramingAndReasoning` 用完整合成协议封装验证思考关闭时的用户可见投影。
- Command: `adb -s emulator-5558 shell am instrument -w com.treelearn.test/androidx.test.runner.AndroidJUnitRunner`
- Exit code: 1
- Observed failure: 专属可见正文断言在 `ChatScreenReasoningLeakInstrumentedTest.java:74` 失败，reasoning 与 framing 仍出现在 answer view。
- Failure reason validated: `ChatScreen.Row.render` 把 `message.text` 原样交给 `renderAnswerBody`，未按思考开关投影；证据 `artifacts/evidence/T-2026-10-01-118-chat-screen-red.txt` 第 55–60 行为目标断言失败，第 938–939 行为整包摘要（280 项中 29 项失败，其余 28 项尚未在本任务归因）。

### GREEN — minimum implementation
- Implementation: PENDING — 修复确认的应用内双通道/协议消费断点，不修改 provider 调用边界。
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: N/A
- Reason: 无例外；按 TDD 执行。
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 本任务新增/调整的是 Android 内部生成内容消费逻辑，不授权改 provider 请求边界。
- 如改处理流程，日志仅保留现有安全 metadata/计数/耗时/模式；不增加远程遥测或输出任何原文。
- 估算生产改动 170 行；超 200 行先拆分。

## Verification report

- Verdict: PENDING
- Evidence: PENDING — 通道/UI 测试、设备生成行为和 Java-only guard 尚未执行。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING — 实现时记录。
- Gaps: 实际泄漏来源尚未复现。
- Risks: 过滤层放错位置会污染历史上下文或合法答案。

## Handoff notes

- Completed: 已确认用户可见目标、范围与 provider 请求边界；`GenerationCoordinator`、双通道批处理和 UI disclosure 路径已列为核查入口。
- In progress: 新增 ChatScreen 可见投影 RED 并在 Android 16 API 36 AVD `emulator-5558` 完成整包 instrumentation；目标断言按预期失败，测试文件 `android/app/src/androidTest/java/com/treelearn/app/ChatScreenReasoningLeakInstrumentedTest.java`。
- Blocker: 目标 Magic8 Pro Air / MagicOS 11 当前不可用，仅阻塞最终设备生成观察；本地调用链分析和 JVM 测试可继续。
- Unblock condition: 建立可复核的 RED 输入后转入 in_progress；目标设备接入后再完成 MagicOS 11 生成观察。
- Next steps: 核对双通道与持久化消费者，补足 reasoning-only、跨 chunk/缺失结束标记、失败取消及合法 answer 正反例；在保护历史读回与非思考内容的前提下最小修复，再更新 Android 当前行为文档。
- Watch-outs: 不对所有 answer 做宽泛字符串过滤；不得记录 prompt、推理正文或密钥；保留 thinkingEnabled=true 的 disclosure 与 DeepSeek 历史上下文编码。
