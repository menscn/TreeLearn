# Treelearn Harness Task: 模型设置页 UI（聊天/快速/翻译分工）

id: T-2026-09-23-088
title: 模型设置页 UI（聊天/快速/翻译分工）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 170
shared_code_touched: false
depends_on: [T-2026-09-23-providers-models-spec-079.md, T-2026-09-23-sqlite-provider-model-schema-083.md, T-2026-09-23-model-list-fetching-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务交付模型设置页：聊天模型、快速模型、翻译模型三个选择器 + 思考预算 + 聊天建议开关。
- Problem/background: 知芽模型选择只有对话页 `ModelThinkingPicker`（单模型 + 思考开关）；需要按用途分工的全局模型设置。
- Users/scenarios: 用户在设置页为聊天/派生任务（快速）/翻译分别指定模型；调整思考预算；开关聊天建议。
- Expected outcome: 模型设置页 + 分工配置持久化（083 model 表 + 设置存储）；快速模型缺省回落聊天模型；聊天建议开关接现有 Recommendation 体系（按 079 拍板结论）。
- Core flow and reversibility: 选择即存；所选模型所属提供商被删时回退默认并提示。
- Inputs/outputs: 输入 SPEC-079 界面节与拍板结论；输出 UI/状态/测试。
- State and exception handling: [NOKEY] 无可用模型时页面引导去提供商页；分工为空的三个字段都显示当前回退值。
- Constraints and trade-offs: 与 087 共用模型选择弹层组件；44dp；中文文案。
- AI recommendation and rationale: 三个选择器复用同一个模型选择弹层（按提供商分组的单选列表），减少重复。
- User choice and custom additions: 无。
- AI-autonomous decisions: 思考预算控件形态（档位 vs 数值）按 079 拍板实现。
- User-confirmation decisions: 无（079 已拍板）。
- Acceptance evidence: RED/GREEN Presenter 测试 + guard。
- Facts: `ModelThinkingPicker.java`、`SettingsStore.java` 现状；T-046 派生推荐。
- Assumptions: 083/086 verified。
- Unknowns: 无。
- Risks: 分工配置与对话页模型 chip 的一致性（谁覆盖谁）——按 079 规格全局默认 + 对话页临时覆盖。
- Deferred: 无。
- Non-goals: 不改派生任务执行链路（089）。
- Skipped non-critical questions and reason: 页面排序按 RikkaHub 布局。

## Goal

交付模型用途分工设置页，三个模型角色选择与思考预算、聊天建议开关可用且持久化。

## Scope

### In scope

- 模型设置页 UI/状态/持久化、模型选择弹层复用、回退规则。

### Out of scope

- 派生任务接线（089）；翻译功能（090）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 对话页模型 chip 行为不变（读全局默认，临时覆盖语义按 079）；现有思考开关语义保留。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `SettingsStore`、`ModelThinkingPicker`、设置页导航。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新增模型分工全局配置。
- Authoritative code/config/migration evidence: 设置存储与 UI 代码。
- Documentation surfaces checked: SPEC-079。
- Documentation files updated: PRD 模型相关当前态说明。
- Producer/consumer/sibling/compatibility paths checked: 089/090 将消费分工配置。
- Superseded phrases/examples reverse-searched: 「单一模型选择」表述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] RED/GREEN：三个角色选择、回退规则、开关持久化断言。
- [ ] 提供商被删后回退默认并提示，不 crash。
- [ ] 44dp/token/TalkBack/Java-only guard + `gradlew check assembleDebug` 通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `:app` ModelSettingsPresenterTest（11 用例：整状态 UI 线程外读取、分工写穿重载、思考预算五档持久化、聊天建议开关持久化、回落触发 FALLEN_BACK、改选清除提示、空目录 NO_MODELS、仍可用选择保留、内置默认优先回落、最早启用模型次选回落、全部不可用置空）。
- Command: `cd android && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests "com.treelearn.app.ui.ModelSettingsPresenterTest"`
- Exit code: 1
- Observed failure: 74 个编译错误全部指向缺失的 ModelSettingsPresenter API（Role/Budget/Option/Assignment/Fallback/Notice/State/resolve/Store/Sink/构造器），无其他源文件报错。
- Failure reason validated: 是（证据 `artifacts/evidence/T-088-red.txt`，GRADLE_EXIT=1 见 T-088-red-exit.txt）。

### GREEN — minimum implementation
- Implementation: ModelSettingsPresenter（三分工/预算/建议状态机+回落规则）、ModelSettingsScreen（全代码化，无 XML）、SettingsStore/SqliteSettingsStore 扩展、设置中心入口。中途为满足 500 行硬上限做等价压缩（switch 改查找表等），压缩前后 11/11 通过。详见 artifacts/evidence/T-088-files.txt。
- Command: `cd android && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests "com.treelearn.app.ui.ModelSettingsPresenterTest"`
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL，tests=11 failures=0 errors=0（`artifacts/evidence/T-088-green.txt`、T-088-green-counts.txt）。
- Regression evidence: `./gradlew --no-watch-fs check assembleDebug` exit 0（`artifacts/evidence/T-088-check.txt`）；no-kotlin guard PASS（`artifacts/evidence/T-088-no-kotlin.txt`）。

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — user pre-authorized (leader 补记)
- Exception type: 规模例外（用户预授权追认）
- Reason: 整任务交付（三分工状态机+回落 Store+设置屏+入口+20 文案）实测 663 行，两轮等价压缩后仍超 500 约 33%，与 T-087 同因（Android 视图样板密度）
- User approval: 用户 2026-09-24「预授权追认」（对主会话「战役内超 500 逐个记例外放行」建议的批准）
- Alternative verification: 独立 verifier 独立实测行数 + 全量 check + 行为复核
- 实测: 663 行（超 500 约 33%）

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 与 087 同触设置中心，串行执行。
- 实现者禁读 RikkaHub 源码。

## Verification report

- Verdict: PASS
- Evidence: independent verifier 2026-09-23：回填记录与原始证据一致（74 错误、11/11、BUILD SUCCESSFUL）；resolve 回落链（内置默认→最早启用→置空）与读取不批量改写均属实；规模例外 663 行属实且已记录预授权追认。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除性断言；纯新增或新增面板，既有行为回归由全量 check 覆盖（详见 GREEN 回归证据）。
- Gaps: 对话页模型 chip 接分工存储（全局默认+临时覆盖）与回落一次性 Notice 无承接任务——leader 已另立 T-2026-09-23-chat-model-chip-roles-103 承接
- Risks: T-088-red.txt 标头 12→11 用例笔误已勘正；green-raw 为 daemon 首跑已注明

## Handoff notes

- Completed: RED（exit 1，74 编译错误全指缺失 API）→ GREEN（11/11 exit 0）→ `check assembleDebug` exit 0（全模块测试+lint+assembleDebug）→ no-kotlin VERDICT: PASS。证据：T-088-red/green/check/no-kotlin/linecount.txt。交付面：ModelSettingsPresenter（含纯函数回落规则）+ SqliteModelSettingsStore + ModelSettingsScreen（全代码构建）+ 设置中心入口 + 20 条 strings + 建议开关经 ChatWiring→DerivedService 门控 ANSWER_RECOMMENDATIONS。
- In progress: 无（待 verifier 验收；真机观感由用户自测）。
- Blocker: 无功能阻塞。规模超限（≈663 行 > 500 硬上限，详见 TDD exception 节与 T-088-linecount.txt）已如实记录，待用户裁定——此前会话的「≤200 行不可满足」Blocker 已被用户 500 行裁定与本次整任务执行取代并清除。
- Unblock condition: 用户对规模超限的裁定（批准则结清；如需压缩到 500 内，需裁掉弹层分组/建议门控接线/入口副标题等规格面之一）。
- Next steps: verifier 出验收报告并裁定规模超限；T-089（快速模型派生接线）消费本任务的分工配置（SqliteModelSettingsStore.read(Role.FAST)），T-090（翻译）同理消费 Role.TRANSLATION。
- Watch-outs: 会话教训——对同一文件并行发起多个 Edit 会相互覆盖（后写胜出），多处编辑必须串行；daemon 陈旧 VFS 用 `--stop` + clean 消除。对话页模型 chip 仍走 GenerationModel 旧端口（行为未动，079「全局默认+临时覆盖」的 chip 对接分工存储未做，属 089 消费面）；回落发生时本页显示 FALLEN_BACK 提示，对话页 Notice 的一次性提示未接（属 089 消费链路）。
