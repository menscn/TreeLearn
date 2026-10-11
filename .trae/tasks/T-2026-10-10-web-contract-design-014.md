# Treelearn Harness Task: 唯一 API/SSE 契约——规范与共享 fixtures

id: T-2026-10-10-014
title: 唯一 API/SSE 契约——规范与共享 fixtures
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: approved-exception

## Requirement collaboration brief

- Problem/background: 前端与后端必须消费同一份 API/SSE 契约；矩阵明确「Web 的具体状态枚举/路由/DTO 以正式唯一契约为准，不把矩阵复制为第二份接口规范」。开发方案阶段 3 要求契约先行，由 contract-guardian 对照。
- Users/scenarios: 后续全部 Web 任务（后端端点、前端客户端、fixtures 双端往返）以此为唯一权威。
- Expected outcome: `web/contracts/` 内规范正文（端点、DTO、错误封装、SSE 协议、幂等、时间单位、状态语义）+ 共享 JSON fixtures；fixtures 同时被 Java producer 与 TypeScript consumer 消费（编解码在 T-015 落地）。
- Core flow and reversibility: 设计走查对照 Android 活动调用链 → 产出规范与 fixtures → contract-guardian 复核 → 修订。文档不通过不改代码。
- Inputs/outputs: 输入=功能矩阵、Android 活动源码（错误分类、状态机、命令语义）、共享领域模型、原型 HTML（交互参考）；输出=`web/contracts/README.md`、`web/contracts/api.md`、`web/contracts/fixtures/**`。
- State and exception handling: 契约必须覆盖失败/中断/取消/重复提交/重连缺口/晚到事件/删除计划过期/无 Key/回落。
- Constraints and trade-offs: 不用 OpenAPI 生成器（未验证依赖）；手写规范 + JSON fixtures + 双端测试比对；测试与文档不计行数。
- AI recommendation and rationale: 手写 + fixtures 往返（Java DTO 与 TS 类型各自解析同一 fixtures），以测试约束漂移，避免引入未验证的生成链。
- User choice and custom additions: 用户确认「正式开发前定稿唯一契约」的顺序。
- AI-autonomous decisions: 端点命名、DTO 字段命名、错误码词汇表（对齐 Android 现有分类）、SSE 事件词汇。
- User-confirmation decisions: 无新增实质问题。
- Acceptance evidence: contract-guardian 复核报告；fixtures JSON 语法校验；字段/单位/错误码与 Android 活动实现对照记录。
- Facts: ready 端点既有响应 `{"service":"treelearn-web-backend","status":"ready"}` 需保持；时间一律 UTC epoch **毫秒**数字；服务 loopback 无认证。
- Assumptions: 浏览器与同源静态托管（发行阶段）。
- Unknowns: 无阻塞项；Android 错误分类细节由本任务读取活动源码固定。
- Risks: 契约与 Android 语义漂移 → contract-guardian + 后续 fixtures 往返测试兜底；日后变更须同任务更新 fixtures 与双端类型。
- Deferred: OpenAPI 代码生成、鉴权、多用户。
- Non-goals: 实现端点/客户端（T-015 及后续）；把矩阵复制成第二份规范。
- Skipped non-critical questions and reason: 已确认目标不再重复确认；常规命名自主处理。

## Goal

产出唯一权威的 API/SSE 契约文档与共享 fixtures，供后端 Controller/DTO 与前端客户端共同消费，并通过 contract-guardian 复核。

## 设计骨架（本任务必须落实；细节以 Android 活动调用链为对照）

- 基础：`/api/**` 同源、loopback、无鉴权；成功响应为裸对象/数组（无 wrapper）。就绪端点保持现响应不变。
- 时间：DTO 中所有真实时间点为 JSON number（UTC epoch 毫秒），字段名带 `Millis` 后缀（如 `createdAtMillis`）；date-only 值（如统计月份 `month: "2026-10"`、日历日）为字符串并在文档中单独标识，消费者不得加时区偏移。
- 错误封装：非 2xx 返回
  `{"error":{"code":"<MACHINE_CODE>","params":{...},"retryable":bool},"requestId":"..."}`；
  文案由前端按 `errors.<code>` i18n 解析，`params` 只含安全插值参数；HTTP 状态映射 400/404/409/500/503；错误码词汇表对齐 Android 活动错误分类（读源码固定，含无 Key、无 Provider、鉴权失败、限流、不可达、流畸形、校验失败、非稳定回答不可分叉、活跃操作冲突、删除计划过期、附件超限/类型不支持、内部错误）。
- 命令幂等：所有变更请求带客户端生成 `commandId`（UUID）；同 `commandId` 重放必须返回同一 operation/结果，不产生第二条消息或第二次模型调用；幂等身份持久化在 `generation_operations.command_id`。
- 端点面（命名可微调，语义必须覆盖矩阵）：
  - 健康：`GET /api/health/ready`（冻结现状）。
  - 主题：`GET /api/topics`、`POST /api/topics`（首问原子接受：校验配置/Key → 创建主题+根分支+USER 消息+AI 逻辑位+操作；拒绝时不落空主题）、`GET /api/topics/{id}`、`GET /api/topics/{id}/deletion-preview`（树快照影响范围 + digest）、`POST /api/topics/{id}/delete`（带 preview digest，过期 409；执行取消活跃操作 + 事务删除）。
  - 分支：`GET /api/topics/{id}/branches`（树与折叠/未读/阅读位置/归档）、`GET /api/branches/{id}/messages`（消息 + 当前版本 + 附件引用）、`POST /api/branches/{id}/messages`（连续追问）、`POST /api/branches/{id}/forks`（整条/选文分叉：冻结快照 + 溯源 + 可选首问原子接受）、`POST /api/branches/{id}/archive|restore`（根拒绝）、`POST /api/branches/{id}/collapse`、`POST /api/branches/{id}/seen`（已见序列）、`PUT /api/branches/{id}/reading-position`、子树删除同 preview/digest 模式。
  - 生成操作：`GET /api/operations/{id}`（快照：state/部分内容/错误）、`GET /api/operations/active?branchId=`（恢复）、`POST /api/operations/{id}/cancel`、`GET /api/operations/{id}/events`（SSE）、`POST /api/messages/{id}/retry`（同位置新版本）、重置语义按 Android ChatCommands/ChatPresenter 对齐后写入文档。
  - 派生任务（推荐问题/翻译/标题/压缩）：复用 operation + SSE 机制，`promptPurpose` 区分，最终结果落 `ai_artifacts`（按 kind），过期输入以 digest 判定 stale。
  - 配置：Provider 增删改（`GET/POST /api/providers`、`PUT/DELETE /api/providers/{id}`，Key 只写不读、响应只含 `hasKey`）、`GET/POST /api/providers/{id}/models`（refresh 只增不删/精确去重）、模型分工 `GET/PUT /api/settings/model-roles`（CHAT/FAST/TRANSLATION；OFF/AUTO/LOW/MID/HIGH；建议开关；回落规则与提示）、`GET /api/settings/model-resolution`（含回落说明）、联网搜索 `GET/PUT /api/settings/web-search`（服务/档位/Key 状态）。
  - 应用内搜索：`GET /api/search?q=`（主题/分支/消息命中 + 可定位信息 + 命中区间）。
  - 统计：`GET /api/stats?timeZone=<IANA>&month=YYYY-MM`（累计提问/消息/连续学习 + 当月热力；服务端按传入设备时区计算，空月返回零值结构）。
  - 附件/文件：`POST /api/attachments`（上传，内容寻址去重）、`GET /api/attachments`、`GET /api/attachments/{id}/content`、`DELETE /api/attachments/{id}`（引用保留缺失标记）、`POST /api/files/cleanup`（孤儿清理幂等）。
  - 批注：`GET /api/messages/{id}/annotations`（读取时按 digest 清理失效）、`POST /api/messages/{id}/annotations`（HIGHLIGHT 合并语义在服务端用共享领域规则执行；NOTE 需非空正文）、`DELETE /api/annotations/{id}`。
  - 语音：无后端端点（浏览器能力探测 + 权限/降级 UI 在文档写明）。
- SSE 协议：`text/event-stream`；每事件 `id: <seq>`（同操作内严格递增）、`event: <type>`、`data: <json>`；类型：`snapshot`（订阅首帧，或重连缺口后先补快照）、`reasoning_delta`、`answer_delta`、`state`、`stable`、`failed`、`interrupted`、`canceled`；`data` 含 `operationId`、`seq`；终态后服务端关闭流；15s 心跳注释行；客户端按 `operationId` 过滤晚到事件、按 `seq` 去重；重连带 `Last-Event-ID`，缺口或缺失时服务端先发 `snapshot`。
- 语义冻结项：正常终止且非空才 `stable`；失败/中断/取消不合并；部分回答不可分叉；稳定版本不可变；快照不可变；单分支同一时刻至多一个活跃操作（409 CONFLICT）；取消后晚到回调不得覆盖终态。
- Fixtures（`web/contracts/fixtures/`，JSON，UTF-8）：`dtos/*.json`（每 DTO 样例）、`commands/*.json`（请求样例）、`errors/*.json`（错误码样例）、`sse/*.json`（有序帧序列：正常流式、失败、中断、取消、重连缺口补快照；含 `Last-Event-ID` 场景）。

## Scope

### In scope
- `web/contracts/README.md`（索引与消费方式）、`web/contracts/api.md`（唯一规范正文）、`web/contracts/fixtures/**`。
- Android 活动源码对照记录（错误分类、状态机、命令语义、字段词汇）写入 `api.md` 的「对照来源」节。

### Out of scope
- Java DTO/TS 类型实现（T-015）；任何 `web/backend`、`web/frontend` 代码改动；OpenAPI 生成器。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增文档与 fixtures；不改运行行为；ready 响应现状冻结为契约。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 未来 T-015/020/021 为消费者；Android 不消费。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason: 无生产代码变更。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无（设计契约文档）。
- Authoritative code/config/migration evidence: 对照 Android 活动源码与共享领域模型（在 api.md 标注）。
- Documentation surfaces checked: 功能一致性矩阵（不回写、不复制）、详细开发方案 §7（API/SSE 章节）。
- Documentation files updated: `web/contracts/**`（新增）；`web/README.md` 契约章节状态由 T-007 后续同步（本任务更新 contracts 状态行）。
- Producer/consumer/sibling/compatibility paths checked: ready 端点（冻结）；`application.properties`（无变更）。
- Superseded phrases/examples reverse-searched: 「唯一 API/SSE 契约（进行中）」（web/README.md）。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] `web/contracts/` 存在规范正文 + fixtures；JSON 全部可解析（语法校验命令与输出留证）。
- [ ] 契约覆盖：端点面、错误码词汇表、SSE 帧类型与顺序、幂等命令、snapshot/缺口恢复、时间单位规则、状态语义冻结项、静态托管与语音边界说明。
- [ ] 与 Android 活动源码对照逐项标注来源（文件+类/方法），无凭空命名；错误分类与状态枚举以活动源码为准。
- [ ] contract-guardian 只读复核 PASS（或 PARTIAL 且缺口已记录并修复）。
- [ ] 未触碰 `web/backend/**`、`web/frontend/**`、`android/**`（git status 证明）。
- [ ] `web/README.md` 的 contracts 状态行同步。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A — 契约设计任务（无行为变更）；以 JSON 语法校验与 contract-guardian 复核为替代验证。
- Command: 见证据
- Exit code: 见证据
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: 契约文档与 fixtures 落盘并通过复核。
- Command: 见证据
- Exit code: 见证据
- Passing evidence: contract-guardian 报告
- Regression evidence: 无生产代码变更（git status）

### REFACTOR — optional
- Cleanup: 按复核意见修订。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: tdd-not-applicable（纯文档/fixtures，无行为变更；任务契约允许 not-applicable）
- Reason: 无生产代码；验证方式=JSON 校验 + contract-guardian + 后续 T-015 往返测试。
- User approval: 用户指示下按推荐执行（2026-10-10 集中提问被跳过；纯文档任务无行为变更）
- Alternative verification: fixtures 双端往返（T-015）

## Implementation notes

- 端点/DTO 细节必须来自活动调用链：`HomeWiring`/`ChatWiring`/`ChatCommands`/`GenerationCoordinator`/`GenerationScheduler`/`DerivedService`/`SqliteSearchStore`/`StatsAggregator`/`AttachmentStore`/`FileManagerPresenter`/`AnnotationDao`/`Highlighter`/`ProviderAdminPresenter`/`ModelSettingsPresenter`/`SearchSettingsPresenter`/`PromptPurpose`。
- 不引入新依赖；不新增数据库对象。

### 实际结果（2026-10-10）

- 产出：`web/contracts/README.md`、`web/contracts/api.md`、`web/contracts/fixtures/**`（共 97 个 JSON）。
- 端点面：约 45 条，分 11 组——健康(1)、主题(5)、分支(11)、生成操作(7)、派生(2)、Provider(8)、设置(7)、搜索(1)、统计(1)、附件(5)、批注(3)。
- 错误码词汇表：39 个码（上游 8、领域状态 15、命令/并发 5、配置 7、附件 2、通用 2），每个含触发条件/`retryable`/`params` 键，并标注 Android 来源类。
- SSE 事件类型 8 个：`snapshot`/`reasoning_delta`/`answer_delta`/`state`/`stable`/`failed`/`interrupted`/`canceled`；含 `Last-Event-ID` 缺口补快照与晚到/重复忽略序列。
- fixtures：`dtos/` 24、`commands/` 28、`errors/` 39、`sse/` 6。
- 对照来源覆盖：共享领域模型（`web/shared/domain/.../domain/*`）与 Android 活动源码——`AiGatewayError`、`GenerationCoordinator`/`GenerationScheduler`/`LiveAttempts`/`LiveAttempt`、`PositionCommands`/`AnswerVersions`、`ChatCommands`/`ChatActions`/`ChatPresenter`/`Highlighter`、`TreeCanvasCommands`/`TreeCanvasPresenter`、`NavigationPresenter`/`NavigationData`/`LibraryPresenter`、`FileManagerPresenter`、`StatsDetailPresenter`、`ProviderAdminPresenter`/`ModelSettingsPresenter`/`SearchSettingsPresenter`、`SpeechInputPolicy`、`HomeViewState`/`HomeStats`、`SqliteChatStore`/`ChatStore`/`SqliteSearchStore`/`SqliteProviderAdminStore`/`SqliteReadingStore`、`DerivedService`/`DerivedOperation`/`ArtifactKind`/`ArtifactOutcome`、`AttachmentStore`/`AttachmentTextExtractor`、`StatsAggregator`、`FirstTopicCreation`/`BranchCreation`、`GenerationOperationDao`/`GenerationOperationRow`、`PromptLimits`、`ReadyController`。
- 契约层新增（非 Android 枚举，已显式标注）：`DELETION_PLAN_EXPIRED` 与删除预览 `digest`（对齐 `NavigationData.plan` fail-closed 与确认顺序）。
- JSON 校验：`web/contracts/fixtures/**/*.json` 逐个 `ConvertFrom-Json`，97/97 通过、0 失败（`artifacts/evidence/T-2026-10-10-014-json-validate.txt`）。
- 未触碰 `web/backend/**`、`web/frontend/**`、`android/**`、`web/README.md`；`web/README.md` 的 contracts 状态行按派工约定留给主会话统一更新（见交接备忘）。

## Verification report

- Verdict: PASS（contract-guardian 首轮 PARTIAL → 修复 5 项后回归 PASS，2026-10-10）
- Evidence: 首轮 17 项端点/语义抽查（14 一致、1 差异、1 已标注发明）、39 错误码 100% 可溯源、时间规则干净、sse 6/6 完整；修复后 42 码与 errors fixtures 名称集合完全一致（42=42，无孤儿/缺漏）、交叉引用全部可解析、`ModelResolution` 形状明确、`refsOf` 修正、§12 补 4 来源、README 登记 sse 包装字段。JSON 校验 100/100（`artifacts/evidence/T-2026-10-10-014-json-validate.txt`、`...-guardian-fix.txt`、`...-gitstatus.txt`）。
- 修复记录：① `SCOPE_UNRESOLVABLE`(409) 入词汇表 + fixture；② 归档再归档复用 `ILLEGAL_STATE_TRANSITION`(409)、根分支 `BRANCH_NOT_ARCHIVABLE`(400)、空 NOTE 正文 `NOTE_BODY_REQUIRED`(400)，各补 fixture；③ 失效引用 6.5/6.13→6.2/6.3；④ `model-resolution.json` 去中文句子、§5.12 定义形状（fallback token NONE/BUILTIN/EARLIEST/EMPTY）；⑤ 低优先三项。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A（无代码断言）。
- Gaps: Web 消费者可消费性待 T-015 双端 fixtures 往返实测（无消费者代码属预期）；后续消费验证点清单已由 contract-guardian 记录（错误码/params/retryable/HTTP 映射断言、SSE 过滤去重补快照、commandId 幂等、ready 冻结）。
- Risks: 契约与 Android 漂移风险由 fixtures 往返（T-015）+ 后续任务消费守卫控制；契约变更须同任务更新文档/fixtures/双端类型并回归 contract-guardian。

## Handoff notes

- Completed: 契约规范与 fixtures 定稿并通过 contract-guardian 回归 PASS（2026-10-10）。产物：`web/contracts/README.md`、`api.md`（约 45 端点、42 错误码、8 类 SSE 事件）、`fixtures/` 100 个 JSON（dtos 24、commands 28、errors 42、sse 6）。
- In progress: commit 由主会话执行；`web/README.md` 契约状态行已由主会话同步。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-015 双端编解码（Java DTO/SSE codec + TS 客户端 + fixtures 往返测试）——须在 Phase 4/5 消费前完成。
- Watch-outs:
  - ready 响应冻结为 `{"service":"treelearn-web-backend","status":"ready"}`。
  - 时间字段 `Millis` 后缀 + UTC epoch 毫秒；date-only（`month`/`day`）为字符串。
  - 契约变更必须同任务更新 fixtures 与双端类型并回归 contract-guardian。