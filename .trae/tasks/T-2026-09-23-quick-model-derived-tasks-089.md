# Treelearn Harness Task: 快速模型接入派生任务链路

id: T-2026-09-23-089
title: 快速模型接入派生任务链路
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 130
shared_code_touched: false
depends_on: [T-2026-09-23-ai-gateway-provider-abstraction-085.md, T-2026-09-23-model-role-settings-screen-088.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务把「快速模型」分工接入知芽现有 PromptPurpose 派生任务（标题生成、推荐问题等）。
- Problem/background: RikkaHub 的快速模型用于标题/建议等后台任务；知芽派生任务（T-011/T-045/T-046）目前与对话同模型；切换便宜模型可提速降费。
- Users/scenarios: 用户设好快速模型后，生标题/推荐走快速模型，主对话不受影响。
- Expected outcome: 派生任务按 PromptPurpose 路由到快速模型配置；未配置时回落聊天模型（088 回退规则）。
- Core flow and reversibility: 派生任务发起 → 读取快速模型分工 → 网关注入该提供商/模型 → 结果回写原链路。
- Inputs/outputs: 输入 085 网关抽象、088 分工配置；输出派生链路接线与测试。
- State and exception handling: 快速模型失败不阻塞主对话（派生任务现有失败语义保持）。
- Constraints and trade-offs: PromptPurpose 判定点唯一（regression 失败案例：判定不双写）。
- AI recommendation and rationale: 在派生任务发起处单点读取分工，不在各派生调用处散读。
- User choice and custom additions: 无。
- AI-autonomous decisions: 回落逻辑复用 088 的共享判定，不另写第二份。
- User-confirmation decisions: 无。
- Acceptance evidence: RED/GREEN 派生链路测试 + 对话回归全绿。
- Facts: PromptPurpose 契约（`core`）；派生调用方见 `app/generation/` 与 T-045/T-046 交付。
- Assumptions: 085/088 verified。
- Unknowns: 无。
- Risks: 派生任务上下文长度差异导致标题质量变化。
- Deferred: 按用途更细的模型路由。
- Non-goals: 不改翻译（090）。
- Skipped non-critical questions and reason: 无。

## Goal

派生任务按「快速模型」分工路由，未配置时无损回落。

## Scope

### In scope

- 派生任务（标题/推荐）读取快速模型分工、网关注入、回落判定、单测。

### Out of scope

- 翻译链路；模型设置 UI。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 派生任务结果结构与消费端不变；未配快速模型时行为与现在完全一致。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `app/generation/` 派生发起处、`core` PromptPurpose。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 派生任务可按分工走不同模型。
- Authoritative code/config/migration evidence: 接线代码与测试。
- Documentation surfaces checked: PromptPurpose 技术文档。
- Documentation files updated: 派生任务文档备注分工路由。
- Producer/consumer/sibling/compatibility paths checked: 标题消费（树导航/库列表）、推荐消费（对话页）。
- Superseded phrases/examples reverse-searched: 「派生任务与对话同模型」表述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] RED/GREEN：分工路由与回落断言；未配置时行为不变的回归断言。
- [ ] 快速模型失败不影响主对话链路（测试证据）。
- [ ] Java-only/依赖方向 guard + 全量单测通过。

## TDD execution record

### RED — failing test first
- Test/scenario: QuickModelGatewayTest 六断言（①已配置快速分工→按 Route 的 provider baseUrl+modelWireId 打开端点流、Key 按 providerId 解析、request/sink 原样透传、回落网关零调用；②未配置→既有聊天模型路径逐参一致（回归）；③Key 不可读→回落；④快速流失败→不经聊天模型重试（失败语义保持）；⑤Key 缓冲打开后擦除；⑥cancel 委托端点 Call）
- Command: cd android && ./gradlew.bat --no-watch-fs --no-daemon --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.generation.QuickModelGatewayTest"
- Exit code: 1
- Observed failure: BUILD FAILED in 12s，:app:compileDebugJavaWithJavac 20 个编译错误，全部为「程序包 QuickModelGateway 不存在 / 找不到符号 QuickModelGateway(.Route/.EndpointStreams)」，均指向测试对被测类 API 的引用；其余任务 up-to-date（证据 T-089-red.txt / T-089-red-raw.txt / T-089-red-exit.txt）
- Failure reason validated: 失败确为被测生产类尚不存在（T-089 代码未实现），非测试语法或既有回归问题

### GREEN — minimum implementation
- Implementation: NEW QuickModelGateway.java（app/generation，派生链路唯一快速模型路由点：Routes/Keys/EndpointStreams 三端口 + Route 值对象；Route==null 或 Key 不可读→委托回落 GenerationGateway；路由流失败仅上报 terminal 不重试；Key 缓冲擦除）+ NEW SqliteQuickModelRoutes.java（app/wiring，经 SqliteModelSettingsStore.read(Role.FAST) 复用 088 共享回落判定，仅补查 ProviderDao base_url，空解析或 provider 行已删→null）+ MOD AppContainer.java（modelSettingsStore 上移、装配 QuickModelGateway 为 DerivedService 网关，Keys 适配 KeystoreSecretStore 且仅内置 provider 做旧键迁移，主对话网关不变）。DerivedService/ChatWiring/HomeWiring 调用面零改动，思考开关语义零改动
- Command: cd android && ./gradlew.bat --no-watch-fs --no-daemon --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.generation.QuickModelGatewayTest"
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL in 15s，QuickModelGatewayTest 6/6 通过（证据 T-089-green.txt / T-089-green-raw.txt / T-089-green-exit.txt）
- Regression evidence: 全量 `./gradlew.bat --no-watch-fs --no-daemon --console=plain check assembleDebug` exit 0（297 actionable tasks，全模块 JVM 测试+lint+assembleDebug，证据 T-089-check.txt）；no-kotlin 守门 VERDICT: PASS exit 0（证据 T-089-no-kotlin.txt）；测试②断言未配置时行为与既有路径逐参一致

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 回落判定与 088 共用同一判定点（失败案例库：判定收敛到唯一判定点）。
- 实现者禁读 RikkaHub 源码。

## Verification report

- Verdict: PASS
- Evidence: independent verifier 2026-09-23：单点路由复用 088 判定（SqliteQuickModelRoutes 经 read(Role.FAST)）、未配置逐参透传回归、快速失败不重试不阻塞主对话、Key 擦除/cancel 均属实；check 297 tasks exit 0。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除性断言；纯新增或新增面板，既有行为回归由全量 check 覆盖（详见 GREEN 回归证据）。
- Gaps: 无行为缺口
- Risks: RED 原文含少量级联错误（程序包 AiGateway 不存在），任务措辞略过强但实质成立

## Handoff notes

- Completed: 任务创建。
- In progress: 待开工。
- Blocker: 085/088 未 verified。
- Unblock condition: 依赖 verified。
- Next steps: RED→GREEN→guard→verifier。
- Watch-outs: 不在多个派生调用处散读配置。
