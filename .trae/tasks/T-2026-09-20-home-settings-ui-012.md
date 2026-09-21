# Treelearn Harness Task: 实现统一首页与设置界面

id: T-2026-09-20-012
title: 实现统一首页与设置界面
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
shared_code_touched: false
depends_on: [T-2026-09-20-keystore-secret-settings-005.md, T-2026-09-20-topic-branch-lifecycle-009.md, T-2026-09-20-derived-ai-artifacts-011.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首屏直接支持继续学习、创建主题、AI 推荐和 Key/模型/思考设置。
- Problem/background: 0 到 1 应用需要将主题入口与新问题收敛到同一高频工作面。
- Users/scenarios: 用户打开应用继续最近主题、从推荐开始、输入新问题或配置 DeepSeek。
- Expected outcome: 原生 XML/View 首页与设置页、Presenter/ViewState、草稿保持和错误反馈。
- Core flow and reversibility: 无 Key 引导设置；保存后可验证；首题失败保留草稿；主题点击恢复最后分支。
- Inputs/outputs: 输入主题列表、推荐、偏好、用户草稿；输出导航或创建命令。
- State and exception handling: loading/empty/error、Key 缺失/无效/余额不足、推荐失败均有独立状态。
- Constraints and trade-offs: 手机竖屏优先，平板横屏合理利用宽度；44dp 热区；字体放大不遮挡。
- AI recommendation and rationale: ViewState 与 controller 分离，页面不直接访问 DAO/Gateway。
- User choice and custom additions: 纯原生 Android，个人 APK，首页 AI 话题推荐。
- AI-autonomous decisions: 推荐失败不阻止手动创建；草稿只在首题成功 accepted 后清空。
- User-confirmation decisions: BASE-21/22 与设备方向。
- Acceptance evidence: instrumentation/截图检查首页、设置、失败草稿和手机/平板布局。
- Facts: v2.5 原型和 V3.1 PRD 是交互权威。
- Assumptions: 具体字体可能回退系统中文字体。
- Unknowns: 无。
- Risks: 首页状态过载、Key 错误回显、横屏留白失衡。
- Deferred: 账号、云同步、营销首屏和复杂 onboarding。
- Non-goals: 不实现聊天、树画布或搜索。
- Skipped non-critical questions and reason: 动效细节以原型和无障碍优先，不影响业务契约。

## Goal

交付可直接开始或继续学习、且能安全配置 DeepSeek 的统一首页和设置流程。

## Scope

### In scope
- 首页主题/推荐/新问题、设置 Key/模型/thinking、Presenter/ViewState、手机/平板资源和 UI 测试。

### Out of scope
- 对话页、画布、归档与搜索结果页。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增首页/设置；保持首题 2xx 前无空树及失败草稿。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): Topic/Artifact/Settings Repository 生产；CreateTopic 消费；手机/平板布局分支。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 页面入口、可见状态、设置项和导航参数。
- Authoritative code/config/migration evidence: layout/resources、Presenter/ViewState、UI tests。
- Documentation surfaces checked: V3.1 PRD 首页/设置、v2.5 原型、Android README。
- Documentation files updated: 可见文案或流程改变时同步 PRD/原型当前态。
- Producer/consumer/sibling/compatibility paths checked: 空/有主题、推荐成功/失败、Key 各状态、手机/平板。
- Superseded phrases/examples reverse-searched: 独立营销落地页和旧设置项。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 历史版本说明不回写。

## Acceptance criteria

- [ ] 同一 Presenter/UI 测试命令先 RED 后 GREEN，覆盖 empty/loading/error/ready、推荐失败和 Key 状态。
- [ ] 401/402/422/429/503 或断网时首页草稿保留、无空 topic；accepted 后才清草稿并进入根分支。
- [ ] 设置可保存/替换/删除 Key，切换模型和 thinking；任何 UI/日志不回显完整 Key。
- [ ] API 36 手机竖屏和平板横屏无重叠，字体放大可读，所有交互目标至少 44dp。（用户 2026-09-21：验收环境为 API 36/36.1）

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

- 页面仅传 UUID 导航；Key 输入离开页面时清空内存文本。
- 远程字体不得运行时下载；资源授权未确认时使用系统字体。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 不同窗口尺寸和输入法遮挡。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 纳入最终集成验收。
- Watch-outs: 页面不可自行重试网络造成重复首题。
