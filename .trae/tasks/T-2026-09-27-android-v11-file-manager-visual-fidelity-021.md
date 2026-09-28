# Treelearn Harness Task: 文件管理视觉复刻

id: T-2026-09-27-021
title: 文件管理视觉复刻
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-27-android-v11-full-screen-visual-fidelity-009.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 130
shared_code_touched: false
depends_on: [T-2026-09-27-android-v11-settings-home-visual-fidelity-016.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 文件列表、占用信息、空/错误/清理确认状态按 V1.1 复刻。
- Expected outcome: 文件条目与清理反馈层级清楚，视觉符合原型。
- User choice and custom additions: 不新增文件能力；验证使用隔离测试文件，不操作用户文件。
- Risks: 清理动作有破坏性，截图与回归不能误删真实用户文件。

## Goal

文件管理页视觉匹配原型，文件扫描、打开、清理确认及取消行为保持。

## Scope

### In scope
- `FileManagerScreen.java`、文件管理专属布局/资源和对应测试。

### Out of scope
- 存储策略、文件格式、清理规则、权限或新文件操作。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 文件列表/占用/确认反馈视觉改变；打开、清理确认/取消和错误处理不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `FileManagerScreen` 消费 `FileManagerPresenter` 提供的列表和动作；页面入口由当前设置路由承载。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（清理行为须安全回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 仅文件管理界面视觉，文件保留/删除规则不变。
- Authoritative code/config/migration evidence: `FileManagerScreen.java`、`FileManagerPresenter` 与文件列表生产者。
- Documentation surfaces checked: `android/README.md` 文件管理和清理说明。
- Documentation files updated: 若操作和清理范围不变，记录 none 与当前态检索证据。
- Producer/consumer/sibling/compatibility paths checked: 空列表、有文件、打开、确认清理、取消与失败反馈。
- Superseded phrases/examples reverse-searched: 无；确认不变更清理动作文案契约。
- `documentation impact: none` reason and evidence, if applicable: 纯视觉调整且当前说明准确时适用。
- Historical/PRD handling: 不改历史报告。

## Acceptance criteria

- [ ] 先增加空态、有文件、打开、确认/取消清理回归断言。
- [ ] 使用隔离测试文件截图对照原型列表、占用和确认状态；不得显示/删除用户文件。
- [ ] 确认后仅删除原规则定义的目标，取消无副作用；手机/平板无溢出且触达 ≥44dp。
- [ ] 定向测试、lint、token guard、Java-only guard 与启动证据通过。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING；使用临时测试目录锁定清理目标和取消无副作用。
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

- 任何删除测试必须绑定临时目录并在结束后清理，不访问用户存储。

## Verification report

- Verdict: PENDING
- Evidence: PENDING；记录隔离目录、测试文件、操作和截图。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 安全与任务范围已确认。
- In progress: 等待 016。
- Blocker: 设置中心尚未验收。
- Unblock condition: 016 verified。
- Next steps: 先 RED，再使用临时数据完成文件列表/清理视觉复刻。
- Watch-outs: 禁止在真实用户数据目录执行清理验证。
