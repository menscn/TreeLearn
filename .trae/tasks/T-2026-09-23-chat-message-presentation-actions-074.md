# Treelearn Harness Task: 消息呈现与动作能力吸收

id: T-2026-09-23-074
title: 消息呈现与动作能力吸收
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-functional-visual-absorption-071.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 190
shared_code_touched: true
depends_on: [T-2026-09-23-rikkahub-capability-absorption-matrix-072.md, T-2026-09-23-chat-page-visual-redesign-spec-073.md, T-2026-09-22-fork-anchor-quote-prefill-and-source-card-065.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 功能与对话页视觉分层吸收；本片负责消息呈现与消息动作的第一批高价值能力。
- Problem/background: RikkaHub 将 Markdown 内容、思考/工具块、消息动作、版本切换和分支入口组织在同一消息单元；知芽已有 MarkdownRenderer、reasoning、来源卡和动作条，但需要按 073 统一层级，并补齐矩阵批准的消息能力。
- Users/scenarios: 学习者阅读 AI 回答、选择文字引用、复制/分享、重新生成、切换版本、查看来源或进入子分支；生成中和失败时动作必须按状态可用/不可用。
- Expected outcome: 消息行视觉与动作能力统一，保留树状来源和版本语义；第一批只实现 072 中批准为 P0 的消息相关能力。
- Core flow and reversibility: state → message row → action → presenter/store；动作失败保留当前内容并给可行动反馈；不覆盖旧版本，不改变 BranchSource。
- Inputs/outputs: 输入 072/073 规格与现有 ChatScreen/Presenter/MarkdownRenderer；输出 Java/XML、资源与 JVM/布局断言。
- State and exception handling: stable/forming/failed/canceled/archived/no-Key/empty-content 均有动作策略；破坏性动作确认；生成中删除/分支按规格拒绝或延后。
- Constraints and trade-offs: Java-only、无新渲染依赖、生产代码目标 ≤200 行；复用现有 CommonMark 与 BranchAnchor；不得把 RikkaHub MessageNode 结构引入知芽。
- AI recommendation and rationale: 先把消息动作和内容层级做稳，再扩展附件/语音/翻译等高代价能力；避免一次改动同时触及输入、生成和存储。
- User choice and custom additions: 用户要求功能吸收；具体 P0 由 072 矩阵和 073 视觉规格确定。
- AI-autonomous decisions: 不在本片加入新数据库字段；若矩阵中的能力需要 schema/新依赖，转为后续任务而不是在本片绕过边界。
- User-confirmation decisions: 072/073 review 前不执行。
- Acceptance evidence: 目标测试 RED/GREEN、全量 JVM 单测、assembleDebug、Java-only guard、布局/touch/contentDescription/token 扫描。
- Facts: 现有消费者为 `ChatScreen.java`、`AnswerActionBar.java`、`item_chat_message.xml`、`view_answer_actions.xml`、`MarkdownRenderer.java`。
- Assumptions: 065 的来源卡与句子锚定任务已完成或可作为稳定依赖。
- Unknowns: Markdown 表格/LaTeX 是否进入 P0 由 072 决定，未提前承诺。
- Risks: 动作 id/回调断裂导致运行时空引用；消息流式重绘下文本选择和布局抖动；动作条过载。
- Deferred: 附件、语音、翻译、收藏、TTS、表格/LaTeX 按矩阵另立任务。
- Non-goals: 不改网络/SSE、分支 schema、提示词、整屏导航或主题系统。
- Skipped non-critical questions and reason: 功能优先级由 072 集中拍板，本片不重复询问。

## Goal

按已批准规格完成消息呈现与消息动作第一批能力，做到视觉、状态、树状来源和现有回调一致。

## Scope

### In scope
- `ChatScreen` 消息行与 `item_chat_message.xml` 的层级、状态呈现和稳定布局。
- `AnswerActionBar`/`view_answer_actions.xml` 的批准动作、版本/分支入口和可用性状态。
- Markdown 内容层中已批准的代码/链接/引用呈现增强，复用现有 renderer。
- 相关字符串、图标、无障碍标签和 token 引用。
- JVM/布局/资源断言与全量回归。

### Out of scope
- 生成协议、流解析、自动滚动和输入栏状态机（075/076）。
- 新表、新迁移、新 Provider、新附件或语音管线。
- 直接读取 RikkaHub 源码；实现只读 072/073 和知芽代码。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 增强消息视觉与动作入口；保留消息顺序、稳定版本不可变、来源锚定、三入口分支语义和失败不静默丢内容。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatPresenter`/`ChatActions` 生产动作，`ChatScreen`/XML 消费；手机与平板布局均核对。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用，属于 Android 行为/表现变更。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 消息动作与内容呈现状态发生变化。
- Authoritative code/config/migration evidence: `ChatScreen.java`、`ChatPresenter.java`、`ChatActions.java`、`MarkdownRenderer.java`、相关 XML/strings/drawables。
- Documentation surfaces checked: 073 视觉规格、现有 PRD 消息动作与分支条款、`android/README.md`。
- Documentation files updated: 按实际变化更新 073 引用的当前态说明和 `android/README.md`；若无额外变化，任务中写 `documentation impact: none` 及搜索证据。
- Producer/consumer/sibling/compatibility paths checked: 所有 `findViewById` 动作 id、三种分支入口、版本选择器、source card。
- Superseded phrases/examples reverse-searched: 旧动作文案、旧布局 id、`showVersions`、`onForkPressed`、`onQuotePressed`。
- `documentation impact: none` reason and evidence, if applicable: 由实现结果决定，不能预填无变更。
- Historical/PRD handling: 冻结 PRD 条款冲突时停止并回到需求澄清，不静默改写。

## Acceptance criteria

- [ ] RED 先证明批准的消息动作/布局断言在实现前失败，GREEN 用同一命令通过。
- [ ] AI/user/源卡/reasoning/partial/failure/archived 行的层级和动作状态与 073 一致，内容变化不造成动作位跳动。
- [ ] 现有分支锚定、来源卡、版本切换和消息选择测试全绿；动作 id、回调、contentDescription 与触达 ≥44dp。
- [ ] Markdown/代码/链接能力不引入未验证依赖，正文选择与复制行为保持可用。
- [ ] `testDebugUnitTest`、assembleDebug、Java-only guard、设计 token 检查均有证据。
- [ ] 生产代码非空非注释行数 ≤200，实测数字从证据文件回读。

## TDD execution record

### RED — failing test first
- Test/scenario: 消息呈现与动作（新增 `ChatMessagePresentationTest`、`ChatMessageRowLayoutTest`：版本 n/N 呈现、动作条各态可用性、左缘绿线、ai_tag 底色、代码块复制接线）。首跑因缺 `activeRevisionNo`/11 参工厂编译失败（RED-1，退出码 1）；补最小字段启用后复跑，13 tests / 6 failed（RED-2，断言按预期失败）。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatMessagePresentationTest" --tests "com.treelearn.app.ui.ChatMessageRowLayoutTest"`
- Exit code: 1
- Observed failure: RED-1 首跑缺 `activeRevisionNo`/11 参工厂编译失败；补最小字段启用后 RED-2 复跑 13 tests / 6 failed（动作条顺序、n/N 格式、左缘绿线、ai_tag 底色、s2 间距、代码块复制接线六类断言失败）。
- Failure reason validated: 失败均由本任务目标行为缺失导致（对应实现随后补齐）。

### GREEN — minimum implementation
- Implementation: `ChatMessage` 增加 `activeRevisionNo`（11 参工厂重载，旧 10 参委托）；`ChatWiring` 传入 revisionNo；`AnswerActionBar` 版本 chip「n/N」+TalkBack 播报；`MarkdownRenderer` 代码块 ClickableSpan 复制；`item_chat_message.xml` 左缘 2dp 绿线（`tl_green`）+ `chat_ai_tag` 绿底圆角；`view_answer_actions.xml` 重排（重置→版本→引用/复制/分支/子分支）；strings/dimens/drawable 配套；顺带修复既有 lint 债（`windowOptOutEdgeToEdgeEnforcement` 移入 `values-v35/themes.xml`）。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatMessagePresentationTest" --tests "com.treelearn.app.ui.ChatMessageRowLayoutTest"`
- Exit code: 0
- Passing evidence: 同命令 13/13 通过；全量 `gradlew check` 另行退出码 0，证据 `artifacts/evidence/.t074-green-target-tests.log`、`.t074-check.log`（BUILD SUCCESSFUL；`verifyNoKotlinDex: PASS`、`verifyDependencyDirection: PASS`）。
- Regression evidence: `artifacts/evidence/.t074-full-unittest-assemble.log`（全量单测 + assembleDebug 退出码 0）。

### REFACTOR — optional
- Cleanup: 无（首版即达规格）。
- Regression evidence: `gradlew check` 退出码 0。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（tdd_mode: required）。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 先锁定消息动作集合，再改布局；不得边改 UI 边新增功能入口。
- 代码块/链接等内容增强若超出 200 行或需要新依赖，拆成后续任务并记录原因。
- 流式期间避免启用会与频繁重绘冲突的选择容器；稳定后再开放完整选择能力。

## Verification report

- Verdict: PASS（独立 verifier 2026-09-24：初判 PARTIAL，缺口=行数证据缺失；主会话补 `artifacts/evidence/.t074-linecount.txt` 后由独立 verifier 复核改判 PASS——「并集参考 1966 + 增量判定 109 ≤190」口径成立，与 T-003 先例一致）
- Evidence: verifier 只读复跑 `:app:testDebugUnitTest --rerun` 退出码 0、`verifyNoKotlinDex` PASS、`find android -name "*.kt" -o -name "*.kts"`（排除 build/）为空、`git status` 确认未触及 `:ai`/`:data`/schema/提示词；TDD 时序核验 RED-1(01:51)→RED-2(01:54, 13 tests/6 failed)→GREEN(01:59, 13/13)；`item_chat_message.xml:103-106` 左缘绿线与 `chat_ai_tag` 绿底实测在位。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。
- Gaps: 真机长按、分享系统面板和大字号观感待人工补证；`themes.xml` lint 债修复（`windowOptOutEdgeToEdgeEnforcement` 移 `values-v35`）属披露的范围外延。
- Risks: 动作过多导致窄屏溢出（沿用既有 6 控件）；必须保持稳定热区和可访问标签。

## Handoff notes

- Completed: 任务契约已生成。
- In progress: 等待 072/073 verified。
- Blocker: 规格未确认前不得实现。
- Unblock condition: 072/073 verified，065 的来源卡依赖可用。
- Next steps: RED → GREEN → 全量回归 → Java-only guard。
- Watch-outs: 不读取 RikkaHub 源码；不修改 Branch/Message schema；不为了视觉便利删除既有分支来源信息。
