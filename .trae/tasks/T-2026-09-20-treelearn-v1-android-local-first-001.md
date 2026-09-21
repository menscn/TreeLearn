# Treelearn Harness Task: 知芽 V1 Android 本地优先应用

id: T-2026-09-20-001
title: 知芽 V1 Android 本地优先应用
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 线性 AI 对话难以保留问题来源、隔离探索路径并恢复阅读现场；项目从 0 到 1，需要可维护、可扩展但不过度建设的本地架构。
- Users/scenarios: 产品经理本人在 Android 手机上学习陌生领域；手机竖屏优先，同时兼容平板横屏。
- Expected outcome: 交付纯 Java Android APK，支持多主题、分支对话、稳定版本锚定、创建快照、DeepSeek 流式生成、版本化自适应学习导师提示词、推理展示与本地持久化、树画布、搜索、归档和失败恢复。
- Core flow and reversibility: Key 守卫后提交根问题；远端接受后原子创建树；稳定回答可追问或分叉；重试/重置不重复消息且不改写旧来源；删除需确认且不可恢复；进程中断保留部分内容并手动重试。
- Inputs/outputs: 输入为本机草稿、DeepSeek Key、模型/思考选择和用户操作；输出为本机主题树、回答版本、思考内容、快照、派生产物和可侧载 APK。
- State and exception handling: 生成采用持久状态机；401/402/422/429/503、断网、SSE 解析失败和进程死亡均映射为明确状态；仅稳定版本可分叉。
- Constraints and trade-offs: Java-only、minSdk 28、无后端/账号/同步/备份；切分支后继续生成，后台仅进程存活期尽力继续；每分支单生成、应用最多两路；首版选择简单可审计的 SQLiteOpenHelper 和 HttpsURLConnection。
- AI recommendation and rationale: 采用 B 模块化单体；用不可变 AnswerRevision、ContextSnapshot、持久 GenerationOperation 和纯 Java TreeLayout 隔离高风险状态，避免用原型的可变内存对象直接实现。
- User choice and custom additions: 用户选择 B；要求 P0/P1 全部纳入；`reasoning_content` 展示、落库并进入后续上下文，默认折叠且文字更灰；DeepSeek 默认采用“自适应导师”，让学习更高效、信息更准确、更容易懂。
- AI-autonomous decisions: 七张最小逻辑表；应用级并发 2；思考历史通过普通上下文标签发送，因为无 tools 时供应商忽略专用历史字段；折叠展开状态不跨重启保存。
- User-confirmation decisions: 纯 Android、本地应用、手机竖屏优先兼容平板横屏、个人 APK 侧载、无进程死亡自动续传、B 架构、推理内容完整闭环，以及 2026-09-21 确认的版本化自适应学习导师提示词策略。
- Acceptance evidence: 所有子任务 verifier PASS；真实设备/模拟器安装 release APK；完整核心流程、Java-only、lint、依赖方向、secret scan 和文档一致性证据。
- Facts: 当前仓库仅有文档和 Harness；V3.1 PRD、V3.1 增量基准、v2.5 原型、Android 技术方案和 DeepSeek 提示词设计 V1.0 为实现依据。
- Assumptions: 用户持有可用 DeepSeek Key；目标设备支持 API 28 以上；模型 ID 在实现与发布前按官方文档复核。
- Unknowns: 无会改变当前首版架构的未决项；未来平台与上线时间不在本期承诺。
- Risks: API 漂移、长推理导致 token/存储增长、进程中断、中文搜索性能、复杂树标签碰撞、字体体积与严格 Java-only 依赖冲突。
- Deferred: 云同步、跨设备、服务端、工具调用、其他模型供应商、笔记/知识库/测验及远程遥测。
- Non-goals: 不开发 backend/frontend，不保证进程死亡自动恢复，不持久化画布像素，不引入 Kotlin。
- Skipped non-critical questions and reason: 推荐条数、摘要阈值等继续由能力约束与测试确定，不改变模块边界。

## Goal

按确认的 V3.1 产品与技术契约交付可侧载、可恢复、Java-only 的知芽 Android V1。

## Scope

### In scope
- `.trae/tasks/` 中列出的 Android、集成和验收子任务。
- `Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md` 的 P0/P1 设计。
- `Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md` 的用途、版本、正文、注入顺序和验收契约。

### Out of scope
- 服务端、Web、iOS、账号、云同步、共享、备份和未列出的学习平台功能。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 从无生产实现建立应用；保留 PRD、原型的全部已确认不变量和交互语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 实现后由各子任务记录真实路径；当前权威为 PRD、增量基准、原型与技术方案。
- Native bridge or independent-client compatibility/update requirements: 不存在 Web/后端桥；仅 Android 本机与 DeepSeek HTTPS 契约。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新建纯 Android 本地应用及其完整运行时契约。
- Authoritative code/config/migration evidence: 子任务完成后填入 Android 生产代码、配置与 SQLite migration 证据。
- Documentation surfaces checked: V3.0/V3.1 需求基准、V3.1 PRD、v2.5 原型、Android 技术方案、提示词设计、后续 android/README。
- Documentation files updated: 已同步 V3.1 增量基准、PRD、Android 技术方案与 DeepSeek 提示词设计；F21 无新增 UI，原型不承担真实 system 验证。
- Producer/consumer/sibling/compatibility paths checked: 由每个子任务按实际调用链记录。
- Superseded phrases/examples reverse-searched: 已处理待同步 BASE-21～26、原型 v1.2/v1.3 当前态引用、40dp 当前口径、思考内容缺失和“派生任务可复用主回答提示词”。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: V3.0 冻结文件不改写；V3.1 通过增量基准叠加。

## Acceptance criteria

- [ ] 所有依赖子任务达到 `verified`，`python .trae/scripts/validate_tasks.py` 返回 0。
- [ ] release APK 在 API 36 手机竖屏和代表性平板横屏环境安装启动，核心流程无崩溃。（用户 2026-09-21：验收环境为 API 36/36.1，不再建设 API 28 环境）
- [ ] Java-only、Android lint、依赖方向、secret scan、文档一致性和专项验收全部 PASS。
- [ ] T-020 的版本化提示词策略通过确定性测试；真实 DeepSeek 两个发布模型在 thinking on/off 下通过脱敏行为冒烟，最终回答折叠 reasoning 后仍可独立理解。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING（父需求由子任务提供 TDD 证据）
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
- Exception type: Not applicable to parent requirement
- Reason: Parent aggregates child evidence and contains no production implementation
- User approval: User explicitly requested Harness task creation on 2026-09-20
- Alternative verification: Child verifier reports plus final integration verification

## Implementation notes

- Product authority: V3.0 baseline + V3.1 incremental baseline + V3.1 PRD.
- Engineering authority: Android local-first technical architecture V1.0.
- Prompt authority: DeepSeek adaptive learning tutor prompt design V1.0.
- UX authority: high-fidelity prototype v2.5; mock state implementation is not a production data model.

## Verification report

- Verdict: PENDING
- Evidence: 等全部子任务验证完成后汇总。
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: 当前仅完成需求、方案和任务拆分，未实现 Android 代码。
- Risks: 见协作记录。

## Handoff notes

- Completed: 需求确认、V3.1 增量基准、PRD/原型同步、技术方案与任务拆分。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 按 depends_on 顺序派 `android-dev`，每个任务完成后串行派 verifier。
- Watch-outs: 不得用原型可变 message 结构替代回答版本；不得引入 Kotlin、静默丢弃 reasoning_content、裁剪 system 或让派生调用误用主回答提示词。
