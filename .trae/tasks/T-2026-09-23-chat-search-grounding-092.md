# Treelearn Harness Task: 对话联网搜索集成

id: T-2026-09-23-092
title: 对话联网搜索集成
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: false
depends_on: [T-2026-09-23-search-service-settings-091.md, T-2026-09-23-ai-gateway-provider-abstraction-085.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务把搜索服务接入对话链路：触发检索、结果注入上下文、来源呈现。
- Problem/background: SPEC-080 定义了检索语义；本任务实现真实执行：对话页搜索开关（默认关）→ 检索 → 注入 → 回答带来源。
- Users/scenarios: 学习者提问时事类问题前打开搜索开关，得到带出处的回答。
- Expected outcome: 输入栏搜索开关、检索执行（引擎 API 调用）、结果注入上下文、来源标注呈现；关闭开关回到纯本地。
- Core flow and reversibility: 开 → 发送 → 检索（[OFF] 降级提示继续生成）→ 注入 → 生成；关 → 不检索。
- Inputs/outputs: 输入 091 配置、085 网关、fork 快照构造；输出集成链路与测试。
- State and exception handling: [OFF]/[NOKEY]/[PART]（空结果）按 SPEC-080；检索超时上限后放弃注入继续生成。
- Constraints and trade-offs: 检索结果不落库（080 拍板）；注入字符上限保护上下文预算；来源信息随创建快照冻结。
- AI recommendation and rationale: 检索在发送前串行执行（非流式中插），实现简单且语义清晰。
- User choice and custom additions: 无。
- AI-autonomous decisions: 来源呈现复用 SourceCard 风格。
- User-confirmation decisions: 无（080 已拍板）。
- Acceptance evidence: RED/GREEN（含检索失败降级、快照兼容断言）+ 回归。
- Facts: `SourceCard`（T-065）、fork 快照 `ContextSnapshot`、ChatDraftStore（T-076）。
- Assumptions: 091/085 verified。
- Unknowns: 引擎响应耗时分布（真机项）。
- Risks: 检索结果污染长期上下文；注入超预算。
- Deferred: 检索结果缓存、多引擎并发。
- Non-goals: 不做搜索设置页（091）。
- Skipped non-critical questions and reason: 超时默认值沿 SPEC-080。

## Goal

对话页可开启检索增强，结果带来源注入当次上下文，失败降级不阻断对话。

## Scope

### In scope

- 对话页搜索开关、检索执行、结果注入与上限、来源呈现、快照兼容、单测。

### Out of scope

- 设置页（091）；检索结果持久化。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 开关关闭时对话行为与现在完全一致；fork 快照结构不变（来源文本并入快照内容）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatPresenter`、`ChatViewState`、`RootQuestionSubmitter`、快照构造链。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 对话可选联网检索。
- Authoritative code/config/migration evidence: 集成代码与测试。
- Documentation surfaces checked: SPEC-080、快照契约文档。
- Documentation files updated: PRD 对话能力当前态说明。
- Producer/consumer/sibling/compatibility paths checked: 091 配置、快照消费者。
- Superseded phrases/examples reverse-searched: 「纯本地上下文」表述补充例外。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] RED/GREEN：开→检索→注入断言；关→行为不变断言；[OFF]/空结果降级断言。
- [ ] 注入字符上限生效；fork 后子分支上下文含来源（快照兼容）。
- [ ] Java-only guard + 全量单测 + `assembleDebug` 通过。

## TDD execution record

### RED — failing test first
- Test/scenario: 6 文件 30 用例（SearchInjection 6（任务初稿写 7，verifier 按 JUnit XML 勘正；总数 30 不变）：注入块框架/引用标注指令/条目序序/摘要标题裁剪/超限丢尾/空块；ChatSearchStep 10：OFF/NOKEY/OK/FAILED 降级/异常降级/PART/Key 缓冲清零/中文引擎名；ChatWiringSearch 6：NOKEY 拒绝零写入/注入不进行行/失败降级继续生成/空结果降级/开关关=旧路径/retry 不检索；ChatPresenterSearch 7：开关状态来自存储/NOKEY 拦截/开启持久化/关闭不需 Key/降级提示清草稿/普通发送无提示/读不重置；SettingsProvidersBinding 1：T-087 死入口绑定）。
- Command: cd android && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests "com.treelearn.app.search.web.*" --tests "com.treelearn.app.wiring.ChatWiringSearchTest" --tests "com.treelearn.app.ui.ChatPresenterSearchTest" --tests "com.treelearn.app.screen.SettingsProvidersBindingTest"
- Exit code: 1
- Observed failure: 88 个编译错误全部指向缺失的 T-092 API（ChatSearchStep/WebSearchGateway/SearchCredentials/SearchInjection/PromptLimits.SEARCH_*/HomeViewState.Notice.SEARCH_*/ChatCommandResult.postNotice 等）；3 处测试自身笔误已在 RED 阶段修正，修正后失败集纯指向缺失 API。
- Failure reason validated: 是（证据 `artifacts/evidence/T-092-red.txt`，EXITCODE 见 T-092-red-exit.txt）。

### GREEN — minimum implementation
- Implementation: app/search/web 包（WebSearchGateway 端口/HttpWebSearchGateway 智谱+Tavily+博查/SearchCredentials/KeystoreSearchCredentials）、ChatSearchStep（OFF/NOKEY/OK/FAILED/PART 编排，Key 缓冲 finally 清零）、SearchInjection（引用标注注入块，裁剪与超限丢尾）、PromptLimits 六常量；ChatWiring.send 检索串行于写入/准入之前，[NOKEY] 拒绝零写入；注入块前置于 currentInput，不落库；降级经 ranWithNotice 上屏；ChatViewState/Presenter/Actions/Screen（chip+底部面板）+SettingsStore+AppContainer/MainActivity 装配；另修复 T-087 死入口。
- Command: cd android && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests "com.treelearn.app.search.web.*" --tests "com.treelearn.app.wiring.ChatWiringSearchTest" --tests "com.treelearn.app.ui.ChatPresenterSearchTest" --tests "com.treelearn.app.screen.SettingsProvidersBindingTest"
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL，30 tests 0 failed（`artifacts/evidence/T-092-green.txt`、T-092-green-raw.txt）。
- Regression evidence: 全量 `./gradlew check` exit 0（`artifacts/evidence/T-092-check.txt`、T-092-check-exit.txt，verifyNoKotlinDex PASS；assembleDebug 未单独立证，由接管后 T-093-check.txt（2026-09-23 20:20，check assembleDebug exit 0，基于含本任务最终态的工作区）覆盖）；一处测试前提修正记录在 T-092-green.txt（回显问题受 SEARCH_QUERY_CHARS 先行裁剪，帧超限不可达，行为不变量保留）。

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 实现者禁读 RikkaHub 源码。
- 与 087–090 同触 ChatScreen/Presenter 区域，串行执行。

## Verification report

- Verdict: PASS
- Evidence: independent verifier 2026-09-23：检索串行于写入前（ChatWiring.java:256）、[NOKEY] 零写入（:259）、注入块不落库不进行行、Key 缓冲 finally 清零（ChatSearchStep.java:142）、OFF=旧路径、降级继续生成并上屏均属实；30 用例 0 失败（XML 实测）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除性断言；纯新增或新增面板，既有行为回归由全量 check 覆盖（详见 GREEN 回归证据）。
- Gaps: 无显式 fork 后含来源用例（由不落库结构+注入不进行行间接覆盖）
- Risks: SearchInjection 用例数 7→6 已勘正；assembleDebug 由接管后 T-093-check 覆盖并已注明

## Handoff notes

- Completed: RED(30 用例, exit 1 纯指向缺失 API)→GREEN(exit 0)→全量 check+no-kotlin(exit 0)→行数盘点(615, 例外节已记)→本文件回写。交付面：输入栏「联网」chip+底部面板（开关/引擎/去设置）、发送前串行真实 HTTPS 检索、注入块前置 currentInput（计入预算、不落库、fork 快照兼容）、三态降级（[NOKEY] 拦截零写入 / [OFF]/[PART] 行内提示不阻塞）。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: verifier 复核 → status verified → 用户真机自测（事类问题带来源回答、弱网降级提示）。
- Watch-outs: 检索失败绝不阻断主对话发送 —— 已由 ChatWiringSearchTest（失败/空结果降级继续生成并提示）与 retryNeverRunsASearch 显式覆盖。**顺带修复（归入本任务）**：T-087 登记缺陷——设置中心「提供商」入口 `setOnProvidersPressed` 从未绑定点击（死入口），已补 `settings_providers_entry` 点击绑定并由 SettingsProvidersBindingTest 源码扫描 guard 防回退。另注：estimated_production_lines 180 与实测 615 偏差已记例外节（用户 2026-09-24 预授权）。
