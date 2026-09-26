# Treelearn Harness Task: 规格｜语音（TTS 朗读与语音识别）

id: T-2026-09-23-081
title: 规格｜语音（TTS 朗读与语音识别）
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-23-rikkahub-settings-capabilities-078.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务产出语音（TTS 提供商可插拔 + 语音识别）的规格。
- Problem/background: 知芽无任何语音能力；RikkaHub 语音设置支持 TTS 提供商选择（系统 TTS/OpenAI TTS 等）与语音识别。用户明确要求「文本转语音能调用本机的 TTS，我们也得要有」——系统 TTS 为第一批必做。
- Users/scenarios: 学习者在通勤/放松时朗读 AI 回答；用语音代替打字提问。
- Expected outcome: `Docs/spec/SPEC-2026-09-23-voice-tts-stt.md`，过自检脚本。
- Core flow and reversibility: 语音设置页选择默认 TTS 提供商（系统 TTS 必选）+ 试听 → 对话页消息动作「朗读」开始/停止 → 语音键长按说话 → 松开识别 → 文本入草稿（不直接发送，用户确认）。
- Inputs/outputs: 输入 RikkaHub 语音页/朗读/语音输入交互事实（`ui/pages/setting/` 语音节、TTS provider、speech 相关源码路径由规格作者核对）与 Android `TextToSpeech`/`SpeechRecognizer` 平台 API；输出规格。
- State and exception handling: [PERM] 麦克风/TTS 权限拒绝降级为隐藏入口+提示；[OFF] 识别失败保留已识别文本；朗读中断（翻页/离开对话/新朗读请求）语义要明确。
- Constraints and trade-offs: 第一批只做系统 TTS + 系统 STT（Android 平台 API，零新依赖）；云端 TTS（OpenAI TTS 等）列拍板区，默认第二批；录音文件不持久化。
- AI recommendation and rationale: 系统能力先行满足「本机 TTS」要求且无依赖/费用；云 TTS 需要联网与计费，等真实需求。
- User choice and custom additions: 用户已确认本机 TTS 必做；云 TTS 待拍板。
- AI-autonomous decisions: 语音识别结果进草稿而非直接发送（保留用户确认权）；朗读跟随阅读锚点不自动滚动（低动效友好），拍板区复核。
- User-confirmation decisions: ①是否本期纳入云 TTS 提供商；②朗读时是否自动滚动跟随；③语音键位置（输入栏内 vs 动作条）。
- Acceptance evidence: SPEC 存在、自检退出码 0、拍板区齐。
- Facts: Android `TextToSpeech` 与 `SpeechRecognizer` 为平台 API；RikkaHub TTS 走 provider 抽象（路径由规格作者核对）；知芽输入栏现状见 T-076。
- Assumptions: 目标设备有可用系统 TTS 引擎（中文）。
- Unknowns: 厂商 SpeechRecognizer 差异（离线可用性）。
- Risks: 识别质量差导致误输入；朗读与生成中状态互斥处理不当。
- Deferred: 云 TTS、语音克隆、背景播放服务。
- Non-goals: 录音持久化、通话级语音流。
- Skipped non-critical questions and reason: 朗读语速/音调默认值用系统默认。

## Goal

产出语音设置页、消息朗读、语音输入的完整规格，供 093/094/095 实现任务直接引用。

## Scope

### In scope

- 语音设置页（TTS 提供商选择、试听、默认引擎）。
- 消息朗读动作与中断语义；语音识别输入流。

### Out of scope

- 代码实现；云 TTS（拍板后另立）；其他三块能力。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯文档。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 无代码改动。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 文档任务。

## Documentation impact record

Follow [code-documentation](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: RikkaHub 语音相关源（规格作者核对）；Android 平台 API 文档。
- Documentation surfaces checked: `Docs/spec/`。
- Documentation files updated: 新建 SPEC。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] SPEC 存在且自检脚本退出码 0。
- [ ] 系统权限拒绝/无引擎/识别失败降级路径齐备。
- [ ] 拍板区 3 项附建议+理由+代价；用户 review 完成。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（文档任务）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 由 spec-author 子代理（agent-7d064e8d）产出 `Docs/spec/SPEC-2026-09-23-voice-tts-stt.md` 四节规格；主会话独立复跑自检
- Command: `python .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-23-voice-tts-stt.md`
- Exit code: 0
- Passing evidence: 四节齐全（sections=4, missing=[]）、intent 指向真实文件、无占位符、无裸 hex/px 全 OK（主会话 2026-09-23 复跑 EXITCODE=0）；TTS 状态机/切分策略/权限时机写实，无 DEFERRED
- Regression evidence: 不适用（纯文档）

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（纯文档任务，沿先例）
- Reason: 无可先行失败的测试
- User approval: 用户 2026-09-23 指令按 Harness 拆解并执行
- Alternative verification: 自检脚本 + 用户 review

## Implementation notes

- 朗读与生成中状态互斥规则要写清（生成中允许朗读旧回答与否）。
- 界面数值只引用 token 名。

## Verification report

- Verdict: PASS（用户 2026-09-23 08:05 回复「按照建议采纳」＝三项拍板点全部按建议采纳，规格翻 accepted）
- Evidence: 规格四节齐备且自检脚本 EXITCODE=0（主会话复跑）；拍板记录已写入规格 Doubts 节末尾（本期只做系统 TTS / 朗读不自动滚动跟随 / 语音键入输入栏与发送键并列）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（无代码断言）。
- Gaps: 厂商 SpeechRecognizer/TTS 引擎差异为真机未知项，保留给 093/095 真机自测。
- Risks: 云 TTS 被推迟后，若用户对系统音色不满意需回来另立任务。

## Handoff notes

- Completed: 任务创建。
- In progress: 待开工。
- Blocker: 无。
- Unblock condition: 用户确认拆解。
- Next steps: 产出规格 → review → 驱动 093/094/095。
- Watch-outs: 权限申请不得在无入口时弹；识别文本进草稿而非直发。
