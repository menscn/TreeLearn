# Treelearn Harness Task: 实现 Keystore 密钥与本地设置

id: T-2026-09-20-005
title: 实现 Keystore 密钥与本地设置
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 150
shared_code_touched: false
depends_on: [T-2026-09-20-bootstrap-java-android-modules-002.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 个人 APK 在本机保存 DeepSeek Key 与模型/思考偏好，禁止备份和泄露。
- Problem/background: 明文 SharedPreferences、日志或 Bundle 会暴露 API Key。
- Users/scenarios: 用户首次保存、替换、验证或删除 Key，并调整模型与思考模式。
- Expected outcome: Keystore AES-GCM 密钥仓储和非敏感设置仓储，失败可诊断但不泄密。
- Core flow and reversibility: 保存前加密；替换覆盖密文；删除同时移除 alias 与密文；失效后要求重录。
- Inputs/outputs: 输入 Key/偏好；输出存在性、临时解密值或分类错误。
- State and exception handling: 密文损坏、alias 丢失、认证失效均 fail closed。
- Constraints and trade-offs: API 28+；Key 不进数据库、日志、剪贴板、Intent 或 saved state。
- AI recommendation and rationale: 平台 Keystore 管理 AES-GCM key，Preferences 只存版本化密文与 IV。
- User choice and custom additions: 个人设备直接安装 APK，无账号或云端密钥托管。
- AI-autonomous decisions: 展开状态不保存；模型/思考开关属于非敏感偏好。
- User-confirmation decisions: 本地应用、禁备份和 DeepSeek 接入。
- Acceptance evidence: 设备端保存/替换/删除/损坏/重启测试和 manifest 备份检查。
- Facts: 技术方案 §14 是安全权威。
- Assumptions: 设备提供标准 Android Keystore。
- Unknowns: 无。
- Risks: 将解密值留在长生命周期对象或异常消息。
- Deferred: 生物识别二次保护和系统凭据同步。
- Non-goals: 不验证余额，不发网络请求。
- Skipped non-critical questions and reason: alias 命名是内部实现，不影响用户契约。

## Goal

安全持久化 DeepSeek Key，并向业务层提供最小生命周期的读取能力。

## Scope

### In scope
- SecretStore、SettingsRepository、AES-GCM envelope、备份禁用配置和设备测试。

### Out of scope
- DeepSeek 网关、设置页面和业务提交。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增 Key/偏好持久化；不触及对话数据库。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 设置页写入；余额验证和生成网关读取；Manifest 控制备份。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: Key 加密格式、alias、删除和备份策略。
- Authoritative code/config/migration evidence: `android/data` secret/settings 实现、Manifest 与 backup rules。
- Documentation surfaces checked: 技术方案 §14、隐私说明、Android README。
- Documentation files updated: 实际安全配置与恢复限制同步到 README/隐私说明。
- Producer/consumer/sibling/compatibility paths checked: 设置、首次主题、生成网关、日志。
- Superseded phrases/examples reverse-searched: 明文 key、云备份和设备迁移表述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保持 PRD 本地安全口径。

## Acceptance criteria

- [ ] 同一设备测试命令先 RED 后 GREEN，覆盖保存、重启读取、替换、删除及损坏密文。
- [ ] Preferences/SQLite/日志/Bundle 中均搜不到测试 Key 明文，错误文案不回显 Key。
- [ ] Manifest 和 data extraction/backup rules 明确关闭云备份与设备迁移恢复。
- [ ] no-kotlin guard、lint 和 API 36 设备路径通过（用户 2026-09-21 决定以 API 36/36.1 为验收环境，见 T-002 执行期需求变更；`minSdk 28` 为声明兼容下限）。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING
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
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- 解密结果按调用即用，不缓存到 Application；尽快清空可变字符缓冲。
- AES-GCM 每次保存使用新 IV；保存 envelope schemaVersion 以支持后续迁移。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: OEM Keystore 差异和错误日志泄露。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 供生成协调器和设置 UI 使用。
- Watch-outs: 测试 fixture 也不得提交真实 Key。
