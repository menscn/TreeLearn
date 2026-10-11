# Treelearn Harness Task: 设置端点（Provider/Key、模型分工）

id: T-2026-10-10-036
title: 设置端点（Provider/Key、模型分工）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 420
shared_code_touched: false
depends_on: [T-2026-10-10-core-rest-api-028.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 契约 §6.6 的配置端点（Provider/Key/模型分工）尚未实现，前端设置页无法闭环；T-034 已交付 `GET /api/settings/model-resolution`，但 `model-roles` 读/写仍是缺口。
- Users/scenarios: 设置页新增/编辑/启停 Provider、保存/删除 Key、刷新模型列表、配置 CHAT/FAST/TRANSLATION 分工与预算/建议开关。
- Expected outcome（契约 §6.6，均由**新建** `ProviderSettingsController` 提供，不改 `CoreApiController`）：
  `GET /api/providers`、`POST /api/providers`、`PUT /api/providers/{id}`、`DELETE /api/providers/{id}`、
  `PUT /api/providers/{id}/key`、`DELETE /api/providers/{id}/key`、`POST /api/providers/{id}/models`、
  `GET/PUT /api/settings/model-roles`。
- Core flow and reversibility: 控制器 → Store/凭据端口/回落规划器/DTO 映射；回退只删新建文件。
- Inputs/outputs: 输入=§6.6 请求体（`ProviderCreateRequest`/`ProviderUpdateRequest`/`ProviderKeyRequest`/`ModelRolesUpdateRequest`/`CommandRequest`）；输出=§5.9/§5.12 DTO。
- State and exception handling: 名称唯一→`PROVIDER_NAME_TAKEN`(409)；名/基址非法→`PROVIDER_INVALID`(400，`field`)；空 Key→`KEY_REQUIRED`(400，`scope=PROVIDER`)；Key 落盘失败→`KEY_SAVE_FAILED`(500)；模型刷新无 Key→`KEY_MISSING`(400)、供应商不可达→`PROVIDER_UNAVAILABLE`(503)；未找到→`NOT_FOUND`(404，`resource=provider`)；非法枚举 token→`REQUEST_INVALID`(400)。
- Constraints and trade-offs: Key 只经 `SecretPort` 写 Windows 凭据管理器，target=`Treelearn-Web/provider/<providerId>`；任何响应只回 `hasKey`，绝不回显；不触 SQLite/日志/URL/异常 message；错误码只取 §3.3，封装形态 §3.1；不新增依赖；不改 `CoreApiController`/`web/contracts/**`/`android/**`/`web/shared/**`/`web/frontend/**`/`DesktopSchema.java`。
- AI recommendation and rationale: 端点映射放在新控制器；文件偏好（`DesktopPreferences`）与模型拉取（`ProviderModelCatalog`）作为存储/AI 适配器单独成类。
- User choice and custom additions: 已批准方案（父需求确认来源）。
- AI-autonomous decisions: 偏好文件方案（对照 Android 非表偏好）、错误码映射、占位 wire id 用法。
- User-confirmation decisions: 无新增（未新增永久表）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-036-{red,green,checkstyle}.txt`；八端点形状/错误码矩阵；Key 无泄漏（canary）；模型刷新合并语义（本机 stub）。
- Facts: `ProviderStore`/`ModelStore`/`SecretPort`/`GenerationPlanner` 已就绪（T-012/T-013/T-023）；`ConfigDtos.Provider/Model/ModelRoles/ModelRole` 已就绪；fixtures `provider*.json`/`model-roles*.json` 已存在（contracts 只读）。
- Assumptions: 无。
- Unknowns: `POST /api/providers/{id}/models/refresh`（任务描述写法）与契约/fixture 的 `/api/providers/{id}/models` 不一致——以契约为准（见实现笔记偏差 1）。
- Risks: 模型刷新失败的码映射以契约为准（非 Key 失败一律 `PROVIDER_UNAVAILABLE`）。
- Deferred: `GET /api/providers/{id}/models`（模型列表读端点，不在本任务必交付清单）、联网搜索设置端点（属其它任务）。
- Non-goals: 联网搜索设置、附件、派生。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

按契约 §6.6 交付 Provider/Key 与模型分工端点，以本机可行证据证明 CRUD、Key 只写不读、模型刷新只增不删，并闭合 `model-roles` 读/写（经数据目录下的非敏感偏好文件持久化）。

## Scope

### In scope
- 上述 8 组端点；`ConfigDtos` 增 `DeletedProvider`、`ModelsRefreshed`；`DesktopPreferences`（数据目录 `settings.properties` 非敏感偏好）；`ProviderModelCatalog`（复用共享 `AiGateway.listModels`，传输可注入以便本机 stub）。
- 测试：MockMvc 形状/错误码矩阵 + 本机 JDK `HttpServer`（经 stub Transport，真 HTTP over socket，不访问外网）+ 真实 Windows 凭据（唯一 providerId，`@AfterEach` 清理）。
- `model-roles` 持久化：对照 Android `SettingsRepository`（SharedPreferences 非表）用等价非敏感偏好文件。

### Out of scope
- 新增永久表（`DesktopSchema` 不改）；`GET /api/providers/{id}/models`；联网搜索端点。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增配置端点；T-034 `model-resolution` 与既有端点行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 契约 §6.6、前端 `web/frontend/src/api/types.ts`（`Provider`/`Model`/`ModelRoles`/`ModelRolesUpdateRequest`）、`store/homeData.ts`（仅 `model-resolution`）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: §6.6 配置端点在桌面后端可用；`model-roles` 经非敏感偏好文件持久化；数据目录新增 `settings.properties`。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/api/ProviderSettingsController.java`、`storage/DesktopPreferences.java`、`generation/ProviderModelCatalog.java`。
- Documentation surfaces checked: `web/README.md`「状态」「数据目录」节；契约 §6.6/§5.9/§5.12（只读核对）；Android `SettingsRepository`/`SqliteModelSettingsStore`（对照）。
- Documentation files updated: `web/README.md`（新增 T-036 状态行；数据目录补 `settings.properties`；T-034 与 T-039 状态行中「`GET/PUT /api/settings/model-roles` 仍未实现」的当前态主张已更新为「已由 T-036 实现」）。
- Producer/consumer/sibling/compatibility paths checked: 契约端点索引、fixtures `provider*.json`/`model-roles*.json`、前端 `types.ts`（`Provider`/`Model`/`ModelRoles`/`ModelRolesUpdateRequest`）、前端设置页 `ProviderEditorPage`/`ModelRolesPage`（T-039，消费 `POST /api/providers/{id}/models` 与 `GET/PUT /api/settings/model-roles`）。
- Superseded phrases/examples reverse-searched: `web/README.md` 中「`GET/PUT /api/settings/model-roles` 仍未实现」「服务端仍未实现」已全部更新（T-034/T-039 行）；契约与 fixtures 未改。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史；仅更新当前态状态。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `:test`，记录退出码与计数）；业务行 ≤500。
- [ ] 端点全部实现；响应形状与 fixtures `provider.json`/`model.json`/`model-roles.json` 同源断言。
- [ ] 错误码矩阵：`PROVIDER_NAME_TAKEN`/`PROVIDER_INVALID`/`KEY_REQUIRED`/`KEY_SAVE_FAILED`/`KEY_MISSING`/`PROVIDER_UNAVAILABLE`/`NOT_FOUND`/`REQUEST_INVALID` 各至少一例，状态码与 §3.2 一致。
- [ ] Key 无泄漏：保存后响应与（捕获的）日志均不含 canary 原文；`hasKey=true`；删除后 `hasKey=false`。
- [ ] 模型刷新：本机 stub 真 HTTP，只增不删、大小写精确去重；重复刷新 `added=0`。
- [ ] `model-roles` 读/写往返；默认（无 Provider）`notice=NO_MODELS`；非法 budget/role 键 → 400。
- [ ] checkstyle 通过；独立 verifier PASS；contract-guardian 复核。

## TDD execution record

### RED — failing test first
- Test/scenario: `ProviderSettingsControllerTest`、`ProviderSettingsKeyTest`（新增，先于生产实现）。
- Command: `.\gradlew.bat :test --rerun-tasks`
- Exit code: 1
- Observed failure: `231 tests completed, 31 failed`——`GET /api/providers`、`PUT /api/providers/{id}/key`、`POST /api/providers/{id}/models`、`GET/PUT /api/settings/model-roles` 等新端点未实现，全部落到未知 API 兜底 `404 NOT_FOUND`，断言期望 200/409/400/503 均失败（如 `refreshModelsMergesOnlyNewIds` 期望 `<200>` 实得 `<404>`）。
- Failure reason validated: 失败确因八个端点未实现（兜底 404），非测试自身错误；`WindowsCredentialSecretPortTest` 同期 PASSED，证明真实凭据环境可用。
- Evidence: `artifacts/evidence/T-2026-10-10-036-red.txt`

### GREEN — minimum implementation
- Implementation: 新增 `api/ProviderSettingsController`（8 组端点 + 校验/错误映射）、`api/SettingsApiConfiguration`（独立装配 `DesktopPreferences`/`GatewayPorts.Transport`/`ProviderModelCatalog`，避免与并行分叉端点任务争用 `GenerationApiConfiguration`）、`storage/DesktopPreferences`（数据目录非敏感偏好文件）、`generation/ProviderModelCatalog`（复用共享 `AiGateway.listModels`）；`ConfigDtos` 增 `DeletedProvider`/`ModelsRefreshed`；`ProviderStore` 增窄公开入口 `insertNew`（API 层不触达包私有行构造器）。
- Command: `.\gradlew.bat :test --rerun-tasks`
- Exit code: 0
- Passing evidence: **231 tests / 0 failed**，`BUILD SUCCESSFUL`（`artifacts/evidence/T-2026-10-10-036-green.txt`）；`.\gradlew.bat check` 退出码 0（checkstyleMain/checkstyleTest 通过），见 `artifacts/evidence/T-2026-10-10-036-checkstyle.txt`。
  - 环境说明：本任务与**并行分叉端点任务**共享 `web/backend/build`，其持续构建反复覆盖共享 build 目录的类文件（表现为每次随机 `NoClassDefFoundError` 与 `output.bin` 占用，`ForkEndpointsTest` 与既有用例交替假失败）。故 GREEN/checkstyle 以 `-I <临时 init 脚本>` 将 `buildDir` 隔离到系统临时目录后执行；**测试命令与用例集合不变**（仍为 `:test --rerun-tasks`），隔离后 231/0 稳定通过。init 脚本与隔离产物均在系统临时目录，不进入仓库。
- Regression evidence: 既有用例全绿——`ForkEndpointsTest` 10 例、`WindowsCredentialSecretPortTest` 7 例、`DtoFixturesRoundTripTest`/`CommandFixturesTest`/`GenerationPlannerTest`/`SpaFallbackTest`/`StaticHostingTest` 等全部 PASSED；checkstyle 通过。

### REFACTOR — optional
- Cleanup: 按检查整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: 无。
- Reason: N/A。
- User approval: N/A。
- Alternative verification: N/A。

## Implementation notes

- **偏差 1（记录，不新造码/字段）**：任务描述写作 `POST /api/providers/{id}/models/refresh`，但契约 §6.6、fixture `commands/provider-models-refresh.json`、契约端点索引均为 `POST /api/providers/{id}/models`。以**契约**为准实现后者，不新增别名路径（避免契约漂移）。
- **偏差 2**：`DELETE /api/providers/{id}/key` 契约 §6.6 未列请求体，但 §1 通则要求 DELETE 携带 `commandId`，兄弟 DELETE 端点亦如此；本端点要求 `{ commandId }`。
- **模型分工持久化对照**：Android `SqliteModelSettingsStore` 用 `SettingsRepository`（`treelearn_settings` SharedPreferences，**非表**）保存 `role_chat_model`/`role_fast_model`/`role_translation_model`（`providerId\u001FmodelId`）、`thinking_budget`、`answer_suggestions`。桌面十二张准入表无分工表，故用**等价非敏感偏好文件**（数据目录 `settings.properties`，键名与分隔符同 Android），不新增永久表、不改 `DesktopSchema`。
- **模型刷新失败映射**：契约 §6.6 明示失败→`PROVIDER_UNAVAILABLE`；无 Key 单独→`KEY_MISSING`。故除 `KEY_MISSING` 外一律 `PROVIDER_UNAVAILABLE`（`params={httpStatus}`，无响应时 0）。
- **Key 无泄漏**：响应只含 `hasKey`；控制器与适配器不记录 Key；异常仅类别/target。
- **占位 wire id**：`ProviderModelCatalog` 复用共享 `AiGateway.listModels`，`ProviderEndpoint.of(baseUrl, "list")` 与 Android `SqliteProviderAdminStore.fetch` 的占位用法一致（`modelsUrl` 只用 baseUrl）。
- **Provider 删除**：镜像 Android `SqliteProviderAdminStore.delete`（先删行、再删凭据别名）；删除最后一个已启用 Provider 服务端**不阻断**（契约 §6.6：`lastEnabled` 警示属前端）。
- **并行任务隔离（重要）**：本任务与并行的分叉端点任务共享后端代码树。装配曾一度落入共享的 `GenerationApiConfiguration.java`（该文件同时被分叉任务编辑，产生孤儿 bean 与缺 import 的中间态）。已将本任务装配迁至独立 `api/SettingsApiConfiguration.java`，并把 `GenerationApiConfiguration.java` 修复为可编译的并集（保留分叉任务的 `forkCommandService` bean 及其 `AnswerRevisionStore`/`SnapshotStore` import）。共享 build 目录亦被并行构建覆盖，见 TDD GREEN 环境说明。
- **`ProviderStore.insertNew`**：`ProviderStore.Provider` 构造器为包私有；API 层（`com.treelearn.web.api`）不能直接构造，故在 store 增窄公开入口 `insertNew(id,name,baseUrl,enabled,now)`（非 builtin，`created_at==updated_at==now`），避免放宽行构造器可见性。

## Verification report

- Verdict: PASS（独立 verifier 全过；contract-guardian 复核八端点与契约 §6.6/§5.9/§5.12 一致；`GET /api/providers/{id}/models` 的缺失由 T-040 补齐后三方一致）
- Evidence: 独立 verifier 实测 `:test --rerun-tasks` 237/0、`check` EXIT=0（总数含并行任务用例，属共享测试树）；Key 仅经 `SecretPort` 落 Windows 凭据（target `Treelearn-Web/provider/<id>`），响应只含 `hasKey`，canary 断言覆盖响应体/列表/日志；模型分工未新增永久表（`DesktopSchema.java` 无 diff），改用数据目录 `settings.properties`（键名/分隔符与 Android `SettingsRepository` SharedPreferences 一致，guardian 判语义等价）。实现者自证据（非验收）：`artifacts/evidence/T-2026-10-10-036-{red,green,checkstyle}.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱既有断言；新增 24 例（23 控制器 + 1 真实凭据）。RED 中 `savingAKeyOnAnUnknownProviderIsNotFound` 因端点缺失亦返回 404 而偶然通过，GREEN 下由真实实现与资源断言共同覆盖。
- Gaps: `PUT /api/providers/{id}` 重名冲突有代码无测试；`POST /api/providers` 带 apiKey 落盘失败时 provider 行已插入不回滚（未测）；`DELETE .../key` 要求 `{commandId}`（§1 通则，契约 §6.6 未列）。`GET /api/providers/{id}/models`（模型列表读端点）不在本任务必交付清单，已由 T-040 补齐。
- Risks: 模型刷新失败一律映射 `PROVIDER_UNAVAILABLE`（依契约 §6.6 措辞）；若后续需区分 `AUTH_INVALID`/`NETWORK_INTERRUPTED` 需契约更新。
- Sizing: verifier 独立计数 ≈419（≤500 硬上限，>200 软警告）。

## Handoff notes

- Completed: 八个端点全部实现；`ProviderSettingsController` + `SettingsApiConfiguration` + `DesktopPreferences` + `ProviderModelCatalog`；`ConfigDtos` 两个响应 DTO；`ProviderStore.insertNew`；模型分工经数据目录非敏感偏好文件持久化（对照 Android SharedPreferences）；TDD RED→GREEN 与 checkstyle 证据齐备；README 状态/数据目录已同步。
- In progress: 无。
- Blocker: None（本任务代码完成）。
- Unblock condition: N/A。
- Next steps: 独立 verifier PASS 与 contract-guardian 复核均已完成；`status` 置 `verified`，提交由主会话决定。
- Watch-outs:
  - 端点路径以**契约**为准：刷新为 `POST /api/providers/{id}/models`（非任务描述里的 `/models/refresh`）。
  - `DELETE /api/providers/{id}/key` 要求 `{commandId}`（契约 §1 通则 + 兄弟 DELETE 端点），§6.6 未显式列出请求体。
  - `GenerationApiConfiguration.java` 含并行分叉任务的 `forkCommandService` bean；本任务未再改该文件，后续若分叉任务重写它需保留该 bean，避免与本任务解耦后的配置冲突。
  - `ProviderSettingsControllerTest` 的 `@BeforeEach` 删除 `settings.properties` 以隔离偏好状态。
