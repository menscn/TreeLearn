# Treelearn Harness Task: 消息翻译（翻译模型）

id: T-2026-09-23-090
title: 消息翻译（翻译模型）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 160
shared_code_touched: false
depends_on: [T-2026-09-23-providers-models-spec-079.md, T-2026-09-23-ai-gateway-provider-abstraction-085.md, T-2026-09-23-model-role-settings-screen-088.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务交付消息级翻译：AI 回答可一键翻译（走「翻译模型」分工），推翻矩阵 1.8 旧 reject。
- Problem/background: RikkaHub 动作条有翻译入口、设置有翻译模型；知芽无。用户本次要求翻译能力随模型设置一起搬。
- Users/scenarios: 学习者遇到英文教材式回答，点「翻译」得到中文译文（按 079 拍板的呈现方式：替换/对照）。
- Expected outcome: 消息动作条新增「翻译」，译文呈现按 SPEC-079 拍板；未配翻译模型回落聊天模型。
- Core flow and reversibility: 翻译请求 → 网关走翻译模型 → 译文呈现；可再次翻译（重试）或收起。
- Inputs/outputs: 输入 085/088 与 SPEC-079 呈现规格；输出动作、呈现与测试。
- State and exception handling: [OFF] 失败提示可重试；译文不覆盖原回答（原文不可变原则——稳定回答不可变契约）。
- Constraints and trade-offs: 译文属于派生展示，不落消息表主内容（落库与否按 079 拍板，默认不落）；不引入专用翻译 SDK。
- AI recommendation and rationale: 译文以 UI 态呈现不写消息表，保住「稳定回答不可变」的领域契约。
- User choice and custom additions: 无。
- AI-autonomous decisions: 翻译目标语言默认中文（用户主语言）。
- User-confirmation decisions: 呈现方式（对照 vs 替换）在 079 拍板。
- Acceptance evidence: RED/GREEN + 稳定回答不可变回归。
- Facts: 消息动作条 `AnswerActionBar`/`view_answer_actions.xml`（T-074 重排后）。
- Assumptions: 085/088 verified。
- Unknowns: 无。
- Risks: 翻译结果写库破坏不可变契约——禁止。
- Deferred: 多目标语言选择、整树批量翻译。
- Non-goals: 不做输入文本翻译、不做界面 i18n。
- Skipped non-critical questions and reason: 图标沿用动作条既有风格。

## Goal

交付消息动作条「翻译」入口与译文呈现，走翻译模型分工，稳定回答不被改写。

## Scope

### In scope

- 动作条翻译入口、翻译请求链路（翻译模型分工 + 回落）、译文呈现态、单测。

### Out of scope

- 模型设置页；输入翻译；i18n。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 动作条现有动作（retry/reset/fork/quote/copy）行为不变；稳定回答不可变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `AnswerActionBar`、`ChatActions`、`ChatViewState`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 消息动作新增翻译。
- Authoritative code/config/migration evidence: 动作条与翻译链路代码。
- Documentation surfaces checked: SPEC-079、矩阵 1.8 推翻记录。
- Documentation files updated: PRD 消息动作当前态说明。
- Producer/consumer/sibling/compatibility paths checked: 074 动作条布局。
- Superseded phrases/examples reverse-searched: 矩阵 1.8「reject」在 079 已记录推翻。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] RED/GREEN：翻译触发、回落、失败重试断言。
- [ ] 稳定回答内容不可变回归（消息表无写路径）。
- [ ] 44dp/中文文案/Java-only guard + 全量单测通过。

## TDD execution record

### RED — failing test first
- Test/scenario: MessageTranslatorTest 6 例（翻译分工路由逐字 rides、请求携带 MESSAGE_TRANSLATION 用途且 thinking 不传、无分工回落聊天网关、Key 不可读回落聊天网关、流失败只报失败不二次消费、空原文拒于调用前）+ ChatPresenterTranslationTest 9 例（按压启动且不触 store、完成展开且原文逐字不变、toggle 折叠、失败可重试、运行中二次按压忽略、失败后再按重发、generating 无入口、跨 store 重读存续、无额外 notice/stage）
- Command: cd android && ./gradlew.bat --no-watch-fs --no-daemon --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.generation.MessageTranslatorTest" --tests "com.treelearn.app.ui.ChatPresenterTranslationTest"
- Exit code: 1
- Observed failure: BUILD FAILED — :app:compileDebugUnitTestJavaWithJavac 编译错误 60+ 处（MessageTranslator/ChatPresenter.Translator/withTranslationStarted/translationVisible/PromptPurpose.MESSAGE_TRANSLATION 等符号不存在）
- Failure reason validated: 失败确为被测生产 API 尚不存在（T-090 未实现），非测试语法或既有回归（:app:compileDebugJavaWithJavac UP-TO-DATE，既有生产代码编译不受影响）。证据 artifacts/evidence/T-090-red.txt（raw 日志 T-090-red-raw.txt/-err.txt）

### GREEN — minimum implementation
- Implementation: PromptPurpose 追加 MESSAGE_TRANSLATION（tl-message-translation/1.0.0，registry/body/assembler 豁免登记，翻译请求不传思考开关）；MessageTranslator 单点路由（route null 或 Key 不可读 → legacy 聊天网关回落，089 同构；失败只报失败可重试）；SqliteTranslationRoutes（read(Role.TRANSLATION)+ProviderDao）；ChatViewState.Translations 不可变呈现态（不落库）+ withers/queries；ChatPresenter.Translator 端口 + onTranslatePressed/onTranslationToggled；ChatActions/ChatMessage/AnswerActionBar/ChatScreen（行内对照式译文区，默认展开可折叠，失败点击重试）；动作条按钮/图标/布局/中文文案；AppContainer+MainActivity 接线
- Command: cd android && ./gradlew.bat --no-watch-fs --no-daemon --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.generation.MessageTranslatorTest" --tests "com.treelearn.app.ui.ChatPresenterTranslationTest"
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL，MessageTranslatorTest 6/6 + ChatPresenterTranslationTest 9/9 = 15/15。证据 artifacts/evidence/T-090-green.txt（raw 日志 T-090-green-raw.txt）
- Regression evidence: ./gradlew check BUILD SUCCESSFUL（:core/:ai/:data/:app 全量测试绿，含 T-074/075 既有套件与稳定回答不可变回归——按压翻译不触任何 store 命令、finish 后 message.text 逐字不变）。首两轮 check 失败为既有契约测试未覆盖新增用途，按 SPEC-079 拍板口径扩展 DeepSeekPromptRegistryTest/ArtifactSchemaTest/PromptPurposeTest 三处后复跑通过，无既有行为回归。证据 artifacts/evidence/T-090-check.txt、T-090-no-kotlin.txt（VERDICT: PASS 13 OK，exit 0）、T-090-linecount.txt（实测生产行数 442 ≤ 500，无规模例外）

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
- 译文呈现走 079 拍板形态。

## Verification report

- Verdict: PASS
- Evidence: independent verifier 2026-09-23：翻译走 read(Role.TRANSLATION)（SqliteTranslationRoutes.java:36）、null 回落聊天网关、译文仅 UI 态不落库（与拍板一致）、稳定回答逐字不可变、失败可重试不吞错；15/15 用例；442 行合规。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除性断言；纯新增或新增面板，既有行为回归由全量 check 覆盖（详见 GREEN 回归证据）。
- Gaps: 无行为缺口
- Risks: 行数系混多任务工作树的人工盘点，无法 git 隔离复核

## Handoff notes

- Completed: 任务创建；依赖 085/088/089 已 verified 并通读；TDD RED→GREEN（15/15）→ check 全绿（含 T-074/075 回归与稳定回答不可变）→ no-kotlin PASS → 行数 442/500；证据五门齐备；状态置 verifying。
- In progress: 无。
- Next steps: verifier 复核（T-090 验收）；后续任务可在其上做整树翻译（Deferred）。
- Watch-outs: 译文禁止写入稳定回答主内容字段（Translations 为页会话 UI 态，不落库，跨 store 重读存续但进程重建即清）；ChatViewState/ChatPresenter/ChatScreen/MainActivity/strings.xml 工作树中混有 T-077/T-087/T-088 未提交改动，diff 需按任务归属甄别（T-090-linecount.txt 已注明口径）；回落口径为任务文件 Expected outcome + 089 先例（route null 或 Key 不可读 → legacy 聊天网关），非任务指令 parenthetical 的「明确失败」变体，verifier 勿混。
