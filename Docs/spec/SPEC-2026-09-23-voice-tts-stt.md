# Spec: 语音（系统 TTS 朗读与语音识别输入）

id: SPEC-2026-09-23-voice-tts-stt
title: 语音（系统 TTS 朗读与语音识别输入）
date: 2026-09-23
intent: Docs/intent/INTENT-2026-09-23-rikkahub-settings-center-absorption.md
author: spec-author
status: accepted

## Requirement spec（需求规格）

- 范围（三块，全部对应 intent「语音（TTS 提供商可插拔 + 语音识别）」与「TTS 调用本机系统 TTS、语音识别走系统 STT」的预期结果）：
  1. **语音设置页**：设置中心新增「语音」页。包含 TTS 提供商选择（第一批仅「系统 TTS」一项，固定存在、不可删除，作为默认引擎）、试听按钮（播放中变停止）、无引擎时的降级说明。交互骨架对齐 RikkaHub 语音设置页（`app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingSpeechPage.kt`：提供商列表 + 单选 + 试听 + 编辑/删除；系统 TTS 为默认且删除项禁用）。云 TTS 提供商（OpenAI TTS 等，见 `speech/src/main/java/me/rerere/tts/provider/TTSProviderSetting.kt`）不在本期，见疑虑 1。
  2. **消息朗读**：AI 回答动作条新增「朗读/停止」动作（知芽现状动作条为 quote/copy/reset/fork/children/version 六项，见 `android/app/src/main/java/com/treelearn/app/screen/AnswerActionBar.java`）。对齐 RikkaHub 消息动作的朗读二态切换（`app/src/main/java/me/rerere/rikkahub/ui/components/message/ChatMessageActions.kt` 第 126-158 行：播放中图标换停止，TTS 不可用时动作禁用）。
  3. **语音识别输入**：输入栏语音键，长按说话、松开识别，识别文本**追加进草稿、不直接发送**（保留用户确认权；intent 状态与异常节要求降级或拦截、不留半成品入口）。
- 验收方向（对应 intent 决策记录「每个实现任务的 acceptance + verifier 独立验收；真机体验由用户自测」）：
  - 设置页可选系统 TTS、试听可播可停；无引擎时页面有说明且不出现可点死的按钮。
  - 消息可朗读、再点停止；离开对话页自动停；新朗读请求打断旧的。
  - 长按语音键说一句中文，松开后文本出现在草稿输入框（追加，不清空已有草稿）；发送仍由用户手动触发。
  - 麦克风拒绝、无 TTS 引擎、无识别服务三条降级路径均有测试断言（T-093/094/095 验收面）。
- 非目标（对应 intent 约束与非目标节、任务 T-081 Deferred 行）：云 TTS 提供商（默认第二批，见疑虑 1）；录音文件持久化；后台连读/背景播放服务；语音唤醒；倍速控制与逐段跳过（T-094 已列 Deferred）；RikkaHub 的云 ASR 会话模式（`app/src/main/java/me/rerere/rikkahub/ui/pages/chat/VoiceSessionController.kt`，其语音输入走云 ASR 提供商而非系统识别，知芽不照搬该架构）。
- 已确认事实 / 假设 / 未知（分开陈述）：
  - 事实：Android `TextToSpeech` 与 `SpeechRecognizer`（含 `RECORD_AUDIO` 权限）均为平台 API，零新依赖；知芽动作条/草稿/输入栏现状见上述代码路径；T-093/094/095 三个实现任务已按「系统 TTS + 系统 STT」拆分并等待本规格。
  - 假设：目标设备装有可用的系统 TTS 引擎（中文）与识别服务（T-081 Assumptions 行）；语速/音调第一批用系统默认值（T-093 已记录该决策）。
  - 未知：各厂商 ROM 的 `SpeechRecognizer` 离线可用性与识别质量差异——真机项，标记为未知，不在本规格给结论（T-081 Unknowns 行）。
- 来源：`Docs/intent/INTENT-2026-09-23-rikkahub-settings-center-absorption.md`（预期结果、核心流程、状态与异常、约束与非目标、决策记录各节）；实现任务 `.codex/tasks/T-2026-09-23-system-tts-settings-093.md`、`T-2026-09-23-message-read-aloud-094.md`、`T-2026-09-23-speech-input-095.md`。

## Interface plan（界面方案）

- 信息架构：
  - 设置中心 →「语音」页（一级页面，与提供商管理、模型设置并列；设置中心导航见 `android/app/src/main/java/com/treelearn/app/screen/SettingsScreen.java` 与 `app/navigation/`，T-087 已确认该结构）。
  - 语音页结构对齐 RikkaHub 双区思路但第一批只保留 TTS 区：①「朗读引擎」区——提供商列表（第一批仅一行「系统 TTS」，单选态标记为默认引擎，不可删除）；②「试听」区——一句固定试听文案 + 播放/停止二态按钮（对齐 RikkaHub 设置页在选中项上放试听按钮、播放中图标切换为停止的事实，出处同上）。
- 入口位置：
  - 朗读入口：消息动作条（`AnswerActionBar`）第七个动作「朗读」，仅 AI 回答行显示；朗读中同一位置切换为「停止」。
  - 语音输入入口：输入栏（`android/app/src/main/res/layout/screen_chat_body.xml` 的 `chat_composer` 区，现含 chat_expand、chat_draft、chat_model_chip、chat_send）内新增语音键，与发送键并列——位置为拍板项 3（疑虑 3）。
- 状态与异常（逐条给降级，不留伪入口）：
  - **[PERM] 麦克风拒绝**：系统权限弹窗仅在用户长按语音键时触发；拒绝后当场提示，且本次会话不再把语音键画成可用的主动作（保留图标但点击给说明，或直接隐藏——由 T-095 实现按「不留伪入口」验收，本规格定为：拒绝后语音键置灰 + 首次点按给开启引导，不再重复弹窗）。
  - **无 TTS 引擎**（`TextToSpeech` 初始化失败或无默认引擎）：设置页显示说明文案，试听按钮不出现；消息动作条不渲染朗读动作（对齐 RikkaHub 以 isAvailable 门控动作可见/可用的事实）。
  - **无识别服务**（`SpeechRecognizer.isRecognitionAvailable` 为否）：语音键直接不渲染，不做占位。
  - **[OFF] 识别失败/无匹配**（`RecognitionListener` 的 ERROR_NO_MATCH、ERROR_SPEECH_TIMEOUT）：识别态结束，已收到的部分识别片段（若平台提供 partial results）保留追加进草稿；完全无结果则静默回到原草稿，不弹错误打断。
  - **朗读中断**：离开对话页自动停止（引擎随页面可见性释放）；新的朗读请求打断当前朗读（flush 语义）；用户点发送时若仍在朗读，先自动停止再发送。
  - **朗读与生成中的互斥规则**：生成中的最新行（`ChatViewState.Stage` 为 SENDING/REASONING/ANSWERING，见 `android/app/src/main/java/com/treelearn/app/ui/ChatViewState.java`）不可朗读（动作禁用）；已完成的历史行（STABLE/CANCELED/FAILED）在生成进行中**可以**朗读——朗读是本机音频播放，与 SSE 网络流不抢音频焦点；此点与 RikkaHub「任何时刻可朗读、无互斥」的事实存在有意差异，理由是避免流式重绘与朗读状态联动引入回归噪声（T-094 Risks 行）。
- 数量与呈现方式：
  - 新增页面 1（语音设置页）、新增动作 1（动作条朗读）、新增按键 1（输入栏语音键）。
  - 朗读中视觉反馈：动作条图标二态切换即可，不做进度条、不做自动滚动跟随（拍板项 2，疑虑 2）。
  - 触达区遵循既有 44dp 热区底线（T-076 实现注记：发送/清除/模型入口都要满足 44dp，语音键同规）。
- 来源（全部引用 `Docs/04-ux-design/design-system/tokens.json` 中存在的 token 名）：页面底 `color.bg`，卡片承载 `color.surface`，卡片圆角 `radius.r-md`，输入框容器圆角 `radius.r-lg`，语音键/试听按钮圆钮 `radius.r-full`；主色与选中态 `color.green`、`color.green-soft`，选中底 `color.green-faint`；标题 `fontSize.fs-h2`，正文 `fontSize.fs-body`，辅助说明 `fontSize.fs-sm`、`fs-cap`；说明文字颜色 `color.ink2`、`color.ink3`；分隔线 `color.line`；警示（识别失败/无引擎提示）`color.amber` 配 `color.amber-soft` 底；错误（引擎初始化失败）`color.red` 配 `color.red-soft` 底；卡片阴影 `shadow.sh-1`；区块间距 `space.s6`，行内间距 `space.s2`、`space.s3`，元素间距 `space.s4`。

## Technical spec（技术规格）

- 接口（机制与状态清单，不写实现代码）：
  - **TTS 引擎封装**（T-093 交付）：一个引擎封装对象，接口面为 初始化（携带引擎可用性回调）、朗读（flush 语义：清空当前队列从头开始）、停止、释放。对外暴露状态回调供 View 层订阅。封装与 View 解耦并配假实现测试钩（对齐 T-093「接口抽象 + 假实现」约束与知芽 `ChatDraftStore.NOOP` 的既有模式，见 `android/app/src/main/java/com/treelearn/app/ui/ChatDraftStore.java`）。
  - **朗读动作**（T-094 交付）：`AnswerActionBar.Listener` 增加 onSpeakPressed(messageId) 一个回调；启用判定从消息谓词读（动作条只读不判定的既有契约，见 `AnswerActionBar.java` 类注释）；文本取消息正文，朗读前剔除 Markdown 记号（对齐 RikkaHub speak 前 stripMarkdown 的事实，`app/src/main/java/me/rerere/rikkahub/ui/hooks/TTS.kt`）。
  - **语音输入封装**（T-095 交付）：识别控制器接口（开始/停止/状态回调）+ 假实现测试钩；平台侧用 `SpeechRecognizer` + `RecognitionListener`。
- 数据结构：语音设置持久化进既有设置存储（`android/app/src/main/java/com/treelearn/app/wiring/AndroidSettingsStore.java`）：默认 TTS 引擎标识（第一批恒为系统 TTS，字段留位以便第二批扩云提供商）、无其他字段（语速/音调第一批用系统默认，T-093 已记录）。识别结果不落盘、录音不持久化。
- 状态迁移：
  - **TTS 状态机**：idle → speaking（朗读请求被引擎接受）→ idle（播完/用户停止/被打断/离开页面）或 error（初始化失败、合成失败）。error 是终点态：提示后回 idle，下次请求重新初始化。生命周期绑定页面可见性——离开语音设置页或对话页时先停止后释放（对齐 RikkaHub Composable onDispose cleanup 与 TtsController.dispose 的事实；平台侧对应 `TextToSpeech.stop()` + `shutdown()`，由 `UtteranceProgressListener` 的 onDone/onError 回调驱动状态回收）。
  - **识别状态机**：idle → listening（权限已授且按下）→ recognizing（松开，等待结果）→ idle（结果或无结果）或 error（服务异常）。旋转屏幕/离开页面：干净取消（`destroy()`），不崩溃不悬挂。错误语义：无结果静默回退；服务异常给 `[OFF]` 提示。
- 长文本分段朗读策略：朗读前把正文按段落与句读切分为短段（对齐 RikkaHub `speech/src/main/java/me/rerere/tts/controller/TextChunker.kt` 的标点感知切分思路，单段上限约一百五十字符），逐段提交平台队列顺序播放；任一段失败即停止并报 error。分段属实现细节，具体切分阈值由 T-093/T-094 实现者定（T-094 已声明）。
- 识别结果入草稿：识别文本追加到现有草稿末尾（必要时间隔一个空格），走既有 `ChatViewState.withDraft` + `ChatDraftStore.save` 链路；**不**调用 `clear`（该契约只允许 accepted send 触发，见 `ChatDraftStore.java` 注释），**不**触发发送（T-076 实现注记：草稿是用户输入，任何路径不得未经规格允许清空它）。
- 权限申请时机：`RECORD_AUDIO` 仅在用户长按语音键的当刻检查并申请；不在启动时、不在进入对话页时、不在无入口时预弹（T-081 Watch-outs 行）。TTS 无需运行时权限。
- 无新依赖原则：TTS 与 STT 均为 Android 平台 API，本批不新增任何第三方库（对应 intent「能力真实优先于数量」与 T-081 约束行）。
- 来源：RikkaHub 事实——`speech/src/main/java/me/rerere/tts/provider/providers/SystemTTSProvider.kt`（TextToSpeech 初始化/setLanguage/speak 类调用/UtteranceProgressListener/shutdown 生命周期）、`speech/src/main/java/me/rerere/tts/controller/TtsController.kt`（flush 打断、stop/dispose、错误流）、`ui/pages/setting/SettingSpeechPage.kt`、`ui/components/message/ChatMessageActions.kt`；知芽事实——`screen/AnswerActionBar.java`、`ui/ChatDraftStore.java`、`ui/ChatViewState.java`、`res/layout/screen_chat_body.xml`、`.codex/tasks/T-2026-09-23-*.md`（074/075/076/093/094/095）。无 DEFERRED 项：本节各机制前置条目均已落地或在本批任务内交付。

## Doubts（疑虑点）

- 疑虑 1：**是否本期纳入云 TTS 提供商**。RikkaHub 有 OpenAI/Gemini/MiniMax 等十余种云 TTS（`speech/src/main/java/me/rerere/tts/provider/TTSProviderSetting.kt`），而 intent 预期结果只写「TTS 调用本机系统 TTS」，两者范围不一致。（冲突对象：intent 预期结果节 vs RikkaHub 提供商事实；两种走法：A 第一批只做系统 TTS，按本规格 / B 第一批同时做提供商抽象与至少一个云 TTS；建议 A——用户原话「文本转语音能调用本机的 TTS」已被系统 TTS 满足，零费用零依赖，且 T-093/094/095 已按 A 拆分；代价：音色少、无自然度调节，学习场景影响有限；谁拍板：用户）
- 疑虑 2：**朗读时是否自动滚动跟随**。跟随需要「朗读进度 ↔ 可视行」映射，且与 T-075 既有滚动跟随/脱离状态机叠加。（冲突对象：`.codex/tasks/T-2026-09-23-generation-motion-scroll-states-075.md` 的滚动策略与低动效取向；两种走法：A 不跟随，朗读纯音频 / B 朗读行高亮并随进度滚动；建议 A——低动效友好、不引入朗读↔滚动状态联动的新回归面；代价：长文朗读需手动定位当前句；谁拍板：用户）
- 疑虑 3：**语音键位置**。（冲突对象：`res/layout/screen_chat_body.xml` 输入栏空间 vs 44dp 热区底线（T-076 实现注记）；两种走法：A 输入栏内、与发送键并列 / B 放到全屏编辑器或动作条等次要位置；建议 A——口语提问是高频替代打字的路径，应在拇指热区一眼可见，T-095 已按 A 拆分；代价：键盘区更挤，窄屏上 44dp 热区与 `radius.r-lg` 输入框空间有竞争，需实现者微调排布；谁拍板：用户）

### 拍板记录（2026-09-23 08:05 用户回复「按照建议采纳」）

- 疑虑 1 **采纳建议 A**：本期只做系统 TTS，云 TTS 提供商不做。
- 疑虑 2 **采纳建议 A**：朗读不自动滚动跟随，朗读为纯音频。
- 疑虑 3 **采纳建议 A**：语音键放输入栏内、与发送键并列，44dp 热区由实现者微调排布保证。

据此本规格三项拍板点全部收敛，status 翻 accepted。
