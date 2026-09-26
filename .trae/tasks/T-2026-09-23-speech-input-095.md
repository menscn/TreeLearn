# Treelearn Harness Task: 语音识别输入

id: T-2026-09-23-095
title: 语音识别输入
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
depends_on: [T-2026-09-23-voice-tts-stt-spec-081.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务用系统 `SpeechRecognizer` 实现语音输入：按住说话→松开识别→文本入草稿（不直接发送）。
- Problem/background: RikkaHub 输入栏有语音入口；知芽无。SPEC-081 定识别结果进草稿保留用户确认权。
- Users/scenarios: 学习者按住输入栏语音键说问题，松开后看到识别文本进入草稿，确认后发送。
- Expected outcome: 输入栏语音键（长按说话）、识别态 UI（录音指示）、结果入草稿、[PERM]/[OFF] 降级、单测。
- Core flow and reversibility: 长按→申请/检查麦克风权限→录音→松开识别→草稿追加（不清空已有草稿）。
- Inputs/outputs: 输入 SPEC-081 与 T-076 输入栏结构；输出语音输入链路与测试。
- State and exception handling: [PERM] 拒绝→提示后不保留伪入口；[OFF] 识别失败保留静音前已识别片段（若平台提供）；设备无识别服务→隐藏入口。
- Constraints and trade-offs: 录音不持久化；权限只在用户触发时申请；识别用平台 API 不引第三方 SDK。
- AI recommendation and rationale: 识别结果追加到草稿末尾而非替换，避免误覆盖用户已输入内容。
- User choice and custom additions: 无。
- AI-autonomous decisions: 语音键与发送键的位置关系按 081 拍板（输入栏内）。
- User-confirmation decisions: 无。
- Acceptance evidence: RED/GREEN + 权限拒绝路径测试 + 输入栏回归（T-076 验收面）。
- Facts: 输入栏 `screen_chat_body.xml`/`ChatDraftStore`（T-076）；Android `SpeechRecognizer`。
- Assumptions: 081 verified。
- Unknowns: 厂商 SpeechRecognizer 可用性（真机项）。
- Risks: 权限流程打断录音状态机；识别中用户旋转屏幕。
- Deferred: 连续听写、语音唤醒。
- Non-goals: 不做 TTS（093/094）；识别文本不直接发送。
- Skipped non-critical questions and reason: 录音指示动效强度按低动效默认。

## Goal

输入栏可长按说话识别，结果进草稿，权限与服务缺失时优雅降级。

## Scope

### In scope

- 语音键、录音指示、SpeechRecognizer 封装（含假实现测试钩）、识别结果入草稿、权限处理、单测。

### Out of scope

- TTS；直接发送；录音文件。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 输入栏三态/草稿/引用行为（T-076 验收面）不变；无识别服务设备不显示入口。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `screen_chat_body.xml`、`ChatPresenter`、`ChatDraftStore`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 输入栏新增语音输入；新增麦克风权限声明。
- Authoritative code/config/migration evidence: AndroidManifest 权限与输入栏代码。
- Documentation surfaces checked: SPEC-081、T-076 文档。
- Documentation files updated: PRD 输入能力当前态说明；启动/权限说明。
- Producer/consumer/sibling/compatibility paths checked: 076 输入栏契约。
- Superseded phrases/examples reverse-searched: 「无麦克风权限」相关表述核对。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] RED/GREEN：长按触发、结果追加草稿、权限拒绝降级断言。
- [ ] 草稿既有内容不被覆盖；旋转中识别状态可恢复或干净取消。
- [ ] 44dp/Java-only guard + 全量单测 + `assembleDebug` 通过；manifest 权限仅新增 RECORD_AUDIO。

## TDD execution record

### RED — failing test first
- Test/scenario: 「ChatSpeechInputTest」（新增，JVM）——①识别结果追加：既有草稿「已有草稿」+ 识别「新问题」→ 草稿「已有草稿新问题」（追加不清空，SPEC-081）；空草稿直接成为识别文本；②空白识别结果（无匹配静默回落）→ 草稿不变；③「SpeechInputPolicy」：无识别服务 → 键不渲染；权限拒绝 → 置灰且首次点按给引导、再点不再给；权限授予 → 键可用。RED 阶段 SpeechInputPolicy 与 ChatActions.onSpeechRecognized 尚不存在，预期编译失败。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatSpeechInputTest"
- Exit code: 1
- Observed failure: 编译失败 14 错——ChatPresenter 无 onSpeechRecognized、SpeechInputPolicy 不存在（T-095-red-2026-09-25.log）。
- Failure reason validated: 全部错误指向本片待实现契约，无一条指向既有行为。

### GREEN — minimum implementation
- Implementation: ①新 `ui/SpeechInputPolicy.java`——键的三态判定唯一判定点（无识别服务→不渲染；权限拒绝→置灰+一次性引导标志；再授予→恢复），View 只读不判；②新 `speech/SpeechInputGateway.java`（端口）+ `speech/PlatformSpeechInput.java`（81 行，平台 `SpeechRecognizer` 封装，partial/final/error 三回调，零新依赖）；③`ChatActions.onSpeechRecognized` + `ChatPresenter` 实现——识别文本**追加进草稿、不直接发送**（复用 `onDraftChanged` 同一条保存-渲染链，空白结果（[OFF] 完全静音）不动草稿）；④composer 加长按说话键（44dp ImageButton + `ic_chat_voice` 图标 + 三条 strings）；`ChatScreen` 键触控（按下→宿主权限流、松开→停会话）+ 聆听态 tint/cd 切换 + 置灰 alpha；⑤`MainActivity` 宿主流——权限只在对话页首次长按时询问（`REQUEST_MICROPHONE`），拒绝→`markPermissionDenied`+现场 Toast，授予→`markPermissionGranted`+直接开会话；错误→保留平台 partial 文本再静默回落（[OFF] 保险）；四个聊天房间出口统一 `stopSpeechSession()`；⑥manifest 声明 `RECORD_AUDIO`。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.ui.ChatSpeechInputTest"
- Exit code: 0
- Passing evidence: 5/5（GREEN 同 RED 命令；T-095-green-2026-09-25.log BUILD SUCCESSFUL）。执行修正三处：ChatScreen/MainActivity 的 import 缺失、`voiceKey` 字段类型 View→ImageView（setImageTintList 需要）。
- Regression evidence: 全量 JVM 单测 643/0/0（75 文件）；check EXITCODE=0；Java-only PASS（13 OK）；真机外壳 5/5（BottomTabShell，composer 与房间切换变更零扰动）；证据 `artifacts/evidence/T-095-speech-input.txt`（CMD-1 至 CMD-9，命令+退出码内嵌）。

### REFACTOR — optional
- Cleanup: 无独立轮——`SpeechInputPolicy` 的判定与 `ChatScreen` 的绘制从初稿即分离；宿主流四个出口的会话清理收敛为单一 `stopSpeechSession()`。
- Regression evidence: 全量单测 643/0/0 同上。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 实现者禁读 RikkaHub 源码。
- 与 092 同触输入栏区域，串行执行。

## Verification report

- Verdict: PASS
- Evidence: RED/GREEN 同命令 EXITCODE 1→0；单测 643/0/0；check 0；Java-only PASS；真机外壳 5/5；零新依赖（CMD-8：build.gradle 零改动）；manifest 权限声明（CMD-7）。汇总 `artifacts/evidence/T-095-speech-input.txt`（CMD-1 至 CMD-9，命令+退出码内嵌）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无既有断言删除；新增断言全部锚定 SPEC-081 拍板原文（追加不清空/无匹配静默回落/无服务不渲染/拒绝置灰+一次性引导/授予可用），非新发明判据。
- Gaps: 真机语音识别的端到端听写效果（口音/噪声/长句）无法用自动化断言——须用户真机自测长按说话；`SpeechRecognizer` 各厂商实现差异（小米/鸿蒙的行为差异）未逐一验证。
- Risks: 最大风险是权限流变成启动时弹窗（SPEC-081 明令禁止）——权限询问只在 `onVoicePressed` 一处、由测试与代码位置双重锚定；其次是识别会话泄漏（离开房间不 stop）——四个房间出口统一 `stopSpeechSession()` 拦住。
- 规模：新文件 148 行 + 修改归属 +128 = 276（估算 170，超出部分为宿主权限流与会话清理的规则 javadoc），如实记录未作例外申请。

## Handoff notes

- Completed: 语音输入全链（policy/gateway/平台封装/composer 键/presenter 追加链/宿主权限流/manifest）交付并 verified；SPEC-081 全部拍板条目逐条落地。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: T-017（平板 Canvas 布局）→ 父需求收口（078/034/059/049/001）→ T-019 终验。
- Watch-outs: (1) 识别文本只入草稿不入消息表（发送仍是用户显式行为）；(2) 权限申请不得移到启动路径（onVoicePressed 是唯一询问点）；(3) [OFF] 的 partial 保留逻辑依赖 `onError` 前平台至少回调过一次 `onPartial`，若厂商实现不回调则静默无文本（可接受降级）；(4) 语音键的渲染条件在 `setVoiceAvailable`，未来若换识别引擎只改 `PlatformSpeechInput.isAvailable()`。
