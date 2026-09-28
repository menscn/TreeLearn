# Treelearn Harness Task: 语音设置视觉复刻

id: T-2026-09-27-020
title: 语音设置视觉复刻
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-09-27-android-v11-settings-home-visual-fidelity-016.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 语音配置与试听状态按 V1.1 复刻，使用当前 Android TTS 能力。
- Expected outcome: 语音选项、当前状态、试听按钮及反馈层级清晰匹配原型。
- User choice and custom additions: 不新增语音服务、权限或语言包。
- Risks: 实际 TTS 引擎因设备差异可能缺失，视觉验收不能把环境缺失误判为样式差异。

## Goal

语音设置页视觉对应原型，试听/停止、选择和系统语音边界不变。

## Scope

### In scope
- `VoiceSettingsScreen.java` 及语音设置页专属资源/测试。

### Out of scope
- 新 TTS/网络服务、系统权限、语音合成算法、模型调用或语言资源包。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 语音选项与试听控件视觉改变；播放/停止/失败反馈和配置写回保持。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `VoiceSettingsScreen` 消费 `VoiceSettingsPresenter` 状态与 `SystemTtsEngine` 反馈。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（试听/失败反馈需回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 仅语音设置界面视觉，不改变 TTS 提供者和能力。
- Authoritative code/config/migration evidence: `VoiceSettingsScreen.java`、`VoiceSettingsPresenter`、`SystemTtsEngine`。
- Documentation surfaces checked: `android/README.md` 语音设置与系统 TTS 说明。
- Documentation files updated: 若用户流程和系统依赖事实不变，记录 none 与搜索证据。
- Producer/consumer/sibling/compatibility paths checked: 选择、试听、停止、无引擎/错误反馈。
- Superseded phrases/examples reverse-searched: 无；确认原入口、动作名称未变。
- `documentation impact: none` reason and evidence, if applicable: 视觉变化时适用。
- Historical/PRD handling: 不修改历史材料。

## Acceptance criteria

- [ ] 先为选择、试听、停止及引擎缺失状态增加回归断言。
- [ ] 截图对照原型语音配置和试听反馈；设备引擎缺失时用测试替身验证错误状态。
- [ ] 试听不越过当前系统 TTS 边界；状态/选择/返回行为保持；手机和平板与 ≥44dp 触达区通过。
- [ ] 定向测试、lint、token guard、Java-only guard 与启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING；覆盖语音状态与试听生命周期。
- Command: PENDING
- Exit code: PENDING
- Observed failure: PENDING
- Failure reason validated: PENDING

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: None; TDD is required.
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 真实语音试听只在明确的本地验证步骤执行；自动截图使用无声/可控状态。

## Verification report

- Verdict: PENDING
- Evidence: PENDING；区分真实 TTS 环境与替身状态。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 任务边界已确认。
- In progress: 等待 016。
- Blocker: 设置中心尚未验收。
- Unblock condition: 016 verified。
- Next steps: 先 RED，再复刻视觉并验证试听状态。
- Watch-outs: 不把设备 TTS 引擎差异记为 UI 样式 Gap。
