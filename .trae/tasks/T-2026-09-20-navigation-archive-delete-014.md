# Treelearn Harness Task: 实现导航、归档与删除

id: T-2026-09-20-014
title: 实现导航、归档与删除
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: true
depends_on: [T-2026-09-20-topic-branch-lifecycle-009.md, T-2026-09-20-derived-ai-artifacts-011.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 用户可在主题树内定位分支，归档可恢复，永久删除需确认并正确处理活动生成。
- Problem/background: 树形内容若只靠返回栈导航会丢失父子语境；删除竞态会让晚回调重建数据。
- Users/scenarios: 用户打开主题导航、切换分支、折叠分组、归档/恢复分支，或永久删除子树/主题。
- Expected outcome: 含当前分支 AI 总结的导航 ViewState、Archive/Restore/Delete 用例、确认对话框和级联取消事务。
- Core flow and reversibility: 归档仅隐藏且可恢复；永久删除明确展示范围，确认后先取消操作再事务级联。
- Inputs/outputs: 输入 topic/branch ID 与删除范围；输出新选择、归档列表或删除结果。
- State and exception handling: 当前分支被归档/删除、祖先不可见、活动生成和晚回调均有确定行为。
- Constraints and trade-offs: 归档不取消生成；删除不可恢复；导航参数只传 UUID。
- AI recommendation and rationale: 归档与删除分离，避免误把“整理”变成数据丢失。
- User choice and custom additions: 个人本地数据，无云端回收站。
- AI-autonomous decisions: 删除后选择最近可见祖先或返回首页。
- User-confirmation decisions: P0/P1 用户流程与失败恢复口径。
- Acceptance evidence: use case/UI 测试覆盖归档中生成、级联删除和晚回调。
- Facts: PRD 导航/归档/删除和技术方案 §7 为权威。
- Assumptions: SQLite 外键和 Coordinator cancelScope 已可用。
- Unknowns: 无。
- Risks: 删除范围文案不清、归档结果仍被默认画布/搜索错误展示。
- Deferred: 回收站、批量导出和云恢复。
- Non-goals: 不实现画布几何和搜索算法。
- Skipped non-critical questions and reason: 导航动画不改变数据边界。

## Goal

提供明确、可恢复的归档和安全、不可恢复的删除，并保持导航上下文一致。

## Scope

### In scope
- 主题/分支导航列表、顶部 AI 总结及折叠、归档/恢复、删除确认、取消范围、事务级联和选择回退。

### Out of scope
- 树 Canvas、搜索和导出。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增导航整理；保持生成在归档后继续和删除后晚事件无效。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Branch Repository/Artifact 生产；Coordinator cancel 与 DAO delete 消费；画布/搜索后续读取可见范围。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 归档可见性、删除范围、取消顺序和导航回退。
- Authoritative code/config/migration evidence: use cases、navigation Presenter、DAO 集成测试。
- Documentation surfaces checked: PRD 导航/归档/删除、原型、技术方案 §7/§12。
- Documentation files updated: 用户可见流程或范围变化时同步 PRD/原型。
- Producer/consumer/sibling/compatibility paths checked: 当前/非当前分支、含活动生成子树、主题删除、恢复。
- Superseded phrases/examples reverse-searched: 归档即取消、删除可撤销和无确认删除。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 当前态更新，历史留存。

## Acceptance criteria

- [ ] 同一 use case/UI 测试命令先 RED 后 GREEN；归档后默认列表/画布隐藏，但生成继续且可恢复查看。
- [ ] 删除确认明确列出主题或子树范围；取消无副作用；确认后先取消范围内操作再原子级联删除。
- [ ] 删除后的晚到 delta/completed 不写回；当前节点消失后导航到最近可见祖先或首页。
- [ ] 旋转和进程重建只通过 UUID 恢复，不通过序列化大对象或过期内存引用。
- [ ] 导航内容区顶部展示 T-011 当前分支 BRANCH_SUMMARY/key_points，失败保留旧结果；不得吸收兄弟/后代内容或显示其它 PromptPurpose 的 artifact。

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

- 归档字段不是删除标记；级联删除不得依赖 UI 逐行循环。
- 删除确认文案必须使用准确数量，无法计算时 fail closed。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 取消与数据库删除竞态。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 解锁 Canvas UI 与最终集成。
- Watch-outs: 归档分支结果完成时不得自动跳回当前页面。
