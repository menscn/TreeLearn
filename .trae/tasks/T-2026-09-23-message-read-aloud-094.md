# Treelearn Harness Task: 消息朗读（TTS 动作）

id: T-2026-09-23-094
title: 消息朗读（TTS 动作）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 110
shared_code_touched: false
depends_on: [T-2026-09-23-system-tts-settings-093.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务在消息动作条增加「朗读/停止」动作，消费 093 的 TTS 封装。
- Problem/background: RikkaHub 消息有 TTS 朗读；知芽无。SPEC-081 定了中断语义（翻页/离开/新朗读请求打断）。
- Users/scenarios: 学习者点消息「朗读」听回答；再点停止；离开对话自动停。
- Expected outcome: 动作条朗读入口（朗读/停止两态）、朗读中视觉反馈、中断语义、单测。
- Core flow and reversibility: 朗读→speaking 态→用户停止或页面离开→idle。
- Inputs/outputs: 输入 093 TTS 封装与 SPEC-081 拍板（自动滚动跟随）；输出动作与测试。
- State and exception handling: 生成中的消息朗读可用性按 081 拍板；引擎错误→提示并复位。
- Constraints and trade-offs: 长文本分段朗读策略（平台队列 or 自切分）由实现者按封装能力定。
- AI recommendation and rationale: 朗读与生成中状态互斥规则按 SPEC-081（避免抢音频焦点）。
- User choice and custom additions: 无。
- AI-autonomous decisions: 朗读入口图标沿用动作条 token 风格。
- User-confirmation decisions: 无。
- Acceptance evidence: RED/GREEN + 回归（动作条其余动作不变）。
- Facts: `AnswerActionBar`/`view_answer_actions.xml`（T-074）。
- Assumptions: 093 verified。
- Unknowns: 超长回答朗读的稳定性（真机项）。
- Risks: 朗读状态与 ChatViewState 集成引入重绘噪声（regression 规则：日志/重绘噪声）。
- Deferred: 背景连读、倍速控制。
- Non-goals: 不做语音识别。
- Skipped non-critical questions and reason: 分段长度属实现细节。

## Goal

消息可朗读、可停止、离开页面自动停，动作条其余行为不回退。

## Scope

### In scope

- 动作条朗读/停止动作、朗读状态与中断、视觉反馈、单测。

### Out of scope

- TTS 引擎封装（093）；语音输入（095）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 动作条既有动作与布局语义不变（T-074 验收面）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `AnswerActionBar`、`ChatActions`、`ChatViewState`、093 TTS 封装。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 消息动作新增朗读。
- Authoritative code/config/migration evidence: 动作与状态代码。
- Documentation surfaces checked: SPEC-081、T-074 视觉规格。
- Documentation files updated: PRD 消息动作当前态说明。
- Producer/consumer/sibling/compatibility paths checked: 074 布局契约。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [x] RED/GREEN：朗读触发/停止/离开页面中断/新请求打断断言。
- [x] 动作条既有动作回归全绿；无 TTS 引擎时入口降级隐藏或提示。
- [x] Java-only guard + 全量单测通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `:app` ChatSpeakerTest（10 用例：按压朗读转 speaking、新请求打断旧的、同条再按转停止、长文按分段队列逐段推进且播完回 idle、每段不超上限、剔除 Markdown 记号、播错转 error 且下次重试、无就绪引擎按压不触发、interrupt 仅在朗读中停止、离开页面先 stop 后 release 且释放后回调静默）。
- Command: cd android && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatSpeakerTest"
- Exit code: 1
- Observed failure: :app:compileDebugUnitTestJavaWithJavac FAILED，编译错误全部指向缺失的 ChatSpeaker（Phase/State/Sink/构造器/onSpeakPressed/interrupt/leavePage/chunk/MAX_SEGMENT），无其他源文件报错。
- Failure reason validated: 是（证据 `artifacts/evidence/T-094-red.txt`，尾部 EXITCODE=1）。

### GREEN — minimum implementation
- Implementation: 新增 ui/ChatSpeaker（Phase IDLE/SPEAKING/STOPPED/ERROR 状态机；两态按压同条停止/异条打断；chunk 句读切分+Markdown 剔除+单段 120 上限硬切；onPlaybackDone 驱动分段队列；onPlaybackError 转 ERROR 终点态可重试；leavePage 先 stop 后 release 且 released 门控回调静默）；ChatMessage.canSpeak=isStable（互斥规则同一谓词）；ChatActions.onSpeakPressed + ChatPresenter 注入 speaker 端口并在 onSend 先 interrupt（覆盖按钮/IME/推荐追问全部发送路径）；AnswerActionBar 增 act_speak 动作与 renderSpeakFace 两态图标（无引擎 GONE，朗读中换 ic_chat_stop）；ChatScreen 实现 ChatSpeaker.Sink（speak 状态跳变只重绘受影响行的朗读脸；ERROR 复用语音页提示文案）；view_answer_actions.xml 增 act_speak 按钮、ic_action_speak.xml 图标、strings.xml 增 cd_act_speak/cd_act_speak_stop；MainActivity showChat 每次访问新建 speaker（Screen detach→leavePage，与 093 同构）。
- Command: cd android && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatSpeakerTest"
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL，10/10 用例通过（`artifacts/evidence/T-094-green.txt`，头部含命令行与退出码）。测试夹具一处修正：longAnswer 初版 10 句切出多于 2 段致断言错位，改为 8×26 字符恰切 2 段，行为断言不变。
- Regression evidence: 全量 `./gradlew.bat --no-watch-fs check assembleDebug` exit 0（`artifacts/evidence/T-094-check.txt`，BUILD SUCCESSFUL）；no-kotlin guard VERDICT: PASS 13 OK（`artifacts/evidence/T-094-no-kotlin.txt`）。动作条其余动作回归由全量 check 覆盖。

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
- 与 090/092 同触动作条/ChatScreen 区域，串行执行。
- 朗读端口 ChatSpeaker 与 View 解耦（T-093 同构），测试全走假引擎；分段策略=句读切分+累计 120 字符上限+超长硬切，队列由引擎 onDone 驱动。
- 互斥实现：canSpeak=isStable 单一谓词（生成中的最新行天然排除）；发送打断收口在 ChatPresenter.onSend 单点。
- 行数口径（非空非注释）：新增 ui/ChatSpeaker.java=198（低于 200 硬线、超 170 目标，系分段/剔除工具并入同文件；证据 artifacts/evidence/.t094-linecount.txt）。修改文件按 T-093 先例不计入新增行数口径。
- 两次 Gradle 并发（回归+guard 内部构建）曾致 Windows 文件锁 dexBuilderRelease 拒绝访问，串行重跑后均 exit 0，非代码问题。

## Verification report

- Verdict: PASS
- Evidence: independent verifier 2026-09-23：RED/GREEN 命令一致（RED exit 1 十八错全指缺失 ChatSpeaker → GREEN exit 0，10/10），证据含命令行与退出码；契约抽查全过（onSpeakPressed 打断收口、onSend 先 interrupt、canSpeak=isStable 互斥、leavePage 先 stop 后 release 且 released 门控、Markdown 剔除+120 上限句读分段不丢不重、无滚动调用合规拍板项）；行数两口径 198/172 均 <200；no-kotlin PASS；acceptance 漏勾已由 leader 补勾。
- Changed/deleted assertion rationale and equivalent behavior coverage: 纯新增能力；既有对话行为回归由全量 check 覆盖。
- Gaps: 真机项（厂商 TTS 长文连读稳定性）留用户试听。
- Risks: 打断依赖 T-093 SystemTtsEngine 的 flush 语义（假引擎测试已验；真机 ROM 差异为既有 Unknowns）。

## Handoff notes

- Completed: RED(10 用例 exit 1)→GREEN(同命令 exit 0)→全量 check assembleDebug exit 0→no-kotlin PASS→行数证据 198；任务文件回填，status=verifying。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: verifier 独立验收（Verdict 留空）→用户真机试听长文分段与打断体验。
- Watch-outs: 朗读状态跳变只重绘受影响行的朗读脸，不触发全量行重建（regression 规则：重绘噪声）；引擎随对话页访问创建、detach 释放，不得改为常驻；095 语音输入不得在本动作条/输入栏提前留入口。
