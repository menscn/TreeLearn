# Treelearn Harness Task: 提供商列表与编辑视觉复刻

id: T-2026-09-27-017
title: 提供商列表与编辑视觉复刻
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 140
shared_code_touched: false
depends_on: [T-2026-09-27-android-v11-settings-home-visual-fidelity-016.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 复刻设置中的提供商列表、启用/禁用和现有编辑状态；沿用生产当前页面组织。
- Expected outcome: 提供商卡片、状态指示、编辑字段与保存/取消反馈对应 V1.1。
- User choice and custom additions: 不新增 provider 能力；密钥只显示安全掩码，验证用假数据。
- Risks: 保存/删除和密钥保管属于既有功能，不能被视觉改造绕过。

## Goal

提供商列表及编辑状态达到原型视觉，同时保持配置保存、验证与密钥脱敏流程。

## Scope

### In scope
- `ProviderAdminScreen.java`、`screen_provider_admin.xml` 与提供商页专属资源/测试。

### Out of scope
- 网络/API 契约、供应商列表扩充、凭据存储和真实 Key 验证。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 列表/表单布局与视觉状态变化；现有添加、编辑、保存、取消、验证反馈保持。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ProviderAdminScreen` 消费 `ProviderAdminPresenter` 的视图状态和动作；`MainActivity` 保持子页路由。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（表单动作须回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 只变更提供商页视觉，不更改支持能力或网络行为。
- Authoritative code/config/migration evidence: `ProviderAdminScreen.java`、`ProviderAdminPresenter`、`screen_provider_admin.xml`。
- Documentation surfaces checked: `android/README.md` Provider 配置边界与密钥处理说明。
- Documentation files updated: 若能力/字段无变化，记录 none 及反向搜索结果。
- Producer/consumer/sibling/compatibility paths checked: 列表、编辑、保存/取消、验证失败/成功状态。
- Superseded phrases/examples reverse-searched: 页面入口和已有字段的当前态说明。
- `documentation impact: none` reason and evidence, if applicable: 视图样式调整时适用。
- Historical/PRD handling: 不改历史材料。

## Acceptance criteria

- [ ] 先增加列表/编辑状态与保存/取消动作的失败回归断言，再改视图。
- [ ] 假 Provider 数据下列表、编辑、禁用、错误/成功状态截图按原型对齐；密钥永不显示原文。
- [ ] 保存、取消、返回及当前 provider 选择不回归；phone/sw600dp、44dp 触达区通过。
- [ ] 定向测试、lint、token guard、Java-only guard 与启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING；覆盖 Provider 编辑状态和现有保存/取消流程。
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

- 仅使用隔离假凭据；不得让密钥原文进入日志、证据文件或截图。

## Verification report

- Verdict: PENDING
- Evidence: PENDING；列出假数据、状态、设备和截图。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 页面范围与安全边界已确认。
- In progress: 等待 016。
- Blocker: 设置中心尚未验收。
- Unblock condition: 016 verified。
- Next steps: 先 RED，再完成提供商页面视觉并验证原有表单路径。
- Watch-outs: 不触碰 provider API 或凭据存储。
