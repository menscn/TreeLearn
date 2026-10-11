# Treelearn Harness Task: model-resolution 与已保存的模型分工保持一致

id: T-2026-10-10-041
title: model-resolution 与已保存的模型分工保持一致
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 60
shared_code_touched: false
depends_on: [T-2026-10-10-model-resolution-endpoint-034.md, T-2026-10-10-provider-settings-endpoints-036.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-10-treelearn-local-web-edition-001.md`（本地 Web 版）已确认；本任务修复功能一致性矩阵「模型分工/模型回落」行的前后不一致缺陷。
- Problem/background: `GET /api/settings/model-resolution`（T-034）调用 `GenerationPlanner.resolveAll(null, null, null)`，忽略用户在 `PUT /api/settings/model-roles`（T-036）保存的分工。用户在设置页选定分工后，回读的 `model-resolution` 仍按「未配置」回落，前后不一致，违反父需求「共享业务判定唯一实现、配置真实生效」。
- Users/scenarios: 本地 Web 用户在设置页为 CHAT/FAST/TRANSLATION 选定 Provider/模型后，主页加载 `model-resolution` 判断是否禁用发送并给出引导。
- Expected outcome: `model-resolution` 按已保存的分工配置解析，回落判定仍走同一 `GenerationPlanner.resolveAll`；未保存分工时与修复前一致。
- Core flow and reversibility: 读端点改为读取同一偏好文件的三个角色选择 → 交给规划器解析；可回退。
- Inputs/outputs: 输入=无参 GET；输出=`ModelResolution`（仅 `roles`）。
- State and exception handling: 分工指向的模型仍可用→`NONE`；不可用→按规划器链回落 `BUILTIN`/`EARLIEST`；无任何可用模型→`EMPTY`。
- Constraints and trade-offs: 不改 `android/**`、`web/shared/**`、`DesktopSchema.java`、`web/contracts/**`；不新增依赖；不 git commit；不重启 8733 服务。
- AI recommendation and rationale: 抽出一个共享读取器承载角色偏好的键名/分隔符与读取，两个端点共用，消除读取口径分叉；解析仍唯一走 `GenerationPlanner`。
- User choice and custom additions: 已批准（缺陷修复）。
- AI-autonomous decisions: 共享读取器类型与归置、测试用例切分。
- User-confirmation decisions: 无新增（未新增永久表、未改契约）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-041-{red,green,checkstyle}.txt`。
- Facts: T-034 提供 `model-resolution`（`resolveAll`）；T-036 用 `DesktopPreferences`（数据目录 `settings.properties`）持久化分工，键名/分隔符对照 Android `SettingsRepository`/`SqliteModelSettingsStore`。
- Assumptions: 无。
- Unknowns: 无。
- Risks: `model-resolution` 不查 Key（`resolveAll` 不触碰凭据端口），故「删 Key」对本端点回落无影响（对照 Android `resolve` 亦只按可用性）。
- Deferred: 其它设置端点。
- Non-goals: 契约/PROvider CRUD/前端改动。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

`GET /api/settings/model-resolution` 反映已保存的模型分工，未保存时行为与修复前一致；回落判定唯一复用 `GenerationPlanner`。

## Scope

### In scope
- `web/backend/src/main/java/com/treelearn/web/api/SettingsApiController.java`：读取已保存分工后交给 `GenerationPlanner.resolveAll`。
- `web/backend/src/main/java/com/treelearn/web/api/ModelRoleSettings.java`：角色偏好（键名/分隔符/读取/写入/预算/建议）的唯一读写入口。
- `web/backend/src/main/java/com/treelearn/web/api/ProviderSettingsController.java`：改用共享读取器，删除重复的偏好键名与解析（行为不变）。
- `web/backend/src/test/java/com/treelearn/web/api/SettingsApiControllerTest.java`：新增保存分工/不可用回落用例。

### Out of scope
- `android/**`、`web/shared/**`、`DesktopSchema.java`、`web/contracts/**`；新增永久表或依赖；前端；其它端点。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: `model-resolution` 改读已保存分工；未保存分工时回落链不变；`model-roles` 读写行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者=`web/backend/.../api/SettingsApiController.java`；消费者=前端 `homeData.ts`/`HomePage.tsx`；同级=`ProviderSettingsController.modelRolesSnapshot`。
- Native bridge or independent-client compatibility/update requirements: 不适用（响应形状不变）。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `GET /api/settings/model-resolution` 现反映已保存的模型分工（此前恒以未配置回落）。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/api/SettingsApiController.java`、`.../ModelRoleSettings.java`、`.../ProviderSettingsController.java`。
- Documentation surfaces checked: `web/contracts/api.md` §5.12/§6.6（形状/语义不变，只读核对）；`web/README.md` 状态节；`Docs/05-technical-design/...功能一致性矩阵.md`（「模型分工」行，只读核对）。
- Documentation files updated: `web/README.md` 新增一条 T-041 状态行（说明 `model-resolution` 现按已保存分工解析、`ModelRoleSettings` 为唯一读取入口、回落判定仍唯一走 `GenerationPlanner`）；响应形状与契约 §5.12 不变，故未改契约。
- Producer/consumer/sibling/compatibility paths checked: 前端 `homeData.ts`/`HomePage.tsx`（消费 `fallback` token，形状不变）；同级 `ProviderSettingsController.modelRolesSnapshot`（改用同一读取器）。
- Superseded phrases/examples reverse-searched: `resolveAll(null, null, null)`（唯一处已消除）；README 中「忽略分工/恒按未配置」无既有表述需改写。
- `documentation impact: none` reason and evidence: 不适用（已更新 README）。
- Historical/PRD handling: 不改写历史；T-034/T-036/T-040 任务行保持原状。

## Acceptance criteria

- [x] 保存分工后 `GET /api/settings/model-resolution` 反映该分工（角色→所选 providerId/modelId，`fallback=NONE`），未配置角色按规划器链回落。（`savedRoleAssignmentIsReflectedByModelResolution`）
- [x] 分工指向不可用（Provider/模型被删）时回落 token 正确（对照 Android `resolve` 链：`BUILTIN`/`EARLIEST`/`EMPTY`）。（`unavailableSavedRoleFallsBackThroughThePlannerChain`）
- [x] 未保存分工时行为与修复前一致（既有 4 例 + 回归用例 `unsavedRolesKeepThePreFixFallbackChain`）。
- [x] 回落判定唯一复用 `GenerationPlanner.resolveAll`（未另立规则）；角色偏好读取唯一共享（`ModelRoleSettings`），无重复键名/解析。
- [x] `:test --rerun-tasks` 全绿、`check` 通过；未改 `android/**`/`web/shared/**`/`DesktopSchema.java`/`web/contracts/**`（`git status` 证明）；业务行 ≈80 ≤200。
- [x] `validate_tasks.py` PASS；独立 verifier PASS（已验证）。

## TDD execution record

### RED — failing test first
- Test/scenario: `SettingsApiControllerTest` 新增 3 例（`savedRoleAssignmentIsReflectedByModelResolution`/`unavailableSavedRoleFallsBackThroughThePlannerChain`/`unsavedRolesKeepThePreFixFallbackChain`），先于生产实现。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 1
- Observed failure: `237 tests completed, 1 failed`——`savedRoleAssignmentIsReflectedByModelResolution` 报 `java.lang.AssertionError: JSON path "$.roles.CHAT.fallback" expected:<NONE> but was:<BUILTIN>`（保存的 CHAT 分工被 `resolveAll(null,null,null)` 忽略，恒按未配置回落到内置默认）。
- Failure reason validated: 失败确因 `SettingsApiController` 忽略已保存分工（非测试自身错误）；同批 `unsavedRolesKeepThePreFixFallbackChain` 与既有 4 例通过，证明回落链本身可用、失败点唯一落在「读取已保存分工」。
- Evidence: `artifacts/evidence/T-2026-10-10-041-red.txt`（含 EXIT=1 与失败详情）

### GREEN — minimum implementation
- Implementation: 新增 `api/ModelRoleSettings`（角色偏好键名/分隔符与读写的唯一入口，对照 Android `SettingsRepository`/`SqliteModelSettingsStore`）；`SettingsApiController` 读取已保存分工后交给同一 `GenerationPlanner.resolveAll`；`ProviderSettingsController` 改用共享读取器（行为不变，删除重复的 5 个常量与 4 个私有方法）。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 0
- Passing evidence: **237 tests / 0 failed**，`BUILD SUCCESSFUL`（`artifacts/evidence/T-2026-10-10-041-green.txt`）；`.\gradlew.bat check` 退出码 0（checkstyleMain/checkstyleTest 通过），见 `artifacts/evidence/T-2026-10-10-041-checkstyle.txt`。
- Regression evidence: 既有 234 例全绿（234 + 新增 3 = 237）；`SettingsApiControllerTest` 7 例（4 旧 + 3 新）与 `ProviderSettingsControllerTest`（model-roles 读/写往返、默认 NO_MODELS、非法 token 400）均 PASSED。

### REFACTOR — optional
- Cleanup: `ProviderSettingsController` 去重——删除 `ROLE_SEPARATOR`/`KEY_ROLE_*`/`KEY_BUDGET`/`KEY_SUGGESTIONS` 常量与 `readChoice`/`saveChoice`/`readBudget`/`roleKey` 私有方法，改用 `ModelRoleSettings`；`Choice` 导入随之移除。
- Regression evidence: 同 GREEN（237/0）。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 回落判定不在控制器：两个端点都把 `GenerationPlanner.Choice` 交给 `GenerationPlanner.resolveAll`，无第二套判定。
- `ModelRoleSettings` 键名/分隔符对照 Android `SettingsRepository`/`SqliteModelSettingsStore`（`role_chat_model`/`role_fast_model`/`role_translation_model`，分隔符 `\u001F`）。
- `model-resolution` 不读取 Key：`resolveAll` 有意不触碰凭据端口（见 `GenerationPlanner` 类注释）。

## Verification report

- Verdict: PASS（独立 verifier 全过）
- Evidence: 独立 verifier 实测 `:test --rerun-tasks` 237/0、`check` EXIT=0；`model-resolution` 现经共享 `api/ModelRoleSettings.java` 读取已保存分工后交同一 `GenerationPlanner.resolveAll`（未另立判定），与 `ProviderSettingsController` 共用同一读写入口；三例断言（保存分工生效 `NONE` / 删 Provider 回落 `BUILTIN` / 未保存回归）。实现者自证据 `artifacts/evidence/T-2026-10-10-041-{red,green,checkstyle}.txt`（RED 237/1 failed、GREEN 237/0、`check` EXIT=0）。均为 MockMvc 单元/契约层证据（无启动/浏览器证据，本修复为读来源修正、响应形状不变）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱既有断言；新增 3 例（234→237）。`insertProvider` 由 `void` 改返回 `String id`（既有调用忽略返回，等价）；`@BeforeEach` 增删 `settings.properties` 以隔离偏好状态（不改变既有断言）。
- Gaps: 未跑启动/浏览器端到端（本修复不改对外形状，读来源修正由 MockMvc 覆盖）；`model-resolution` 不查 Key（与 Android `resolve` 一致），故「删 Key」不改变本端点结果。
- Risks: 低（读端点、无写入、响应形状不变）。
- Sizing: 独立计数 ≈80。

## Handoff notes

- Completed: `model-resolution` 改按已保存分工解析；`api/ModelRoleSettings` 为角色偏好唯一读写入口，两个控制器共用；`ProviderSettingsController` 去重（删 5 常量 + 4 私有方法，行为不变）；新增 3 例测试；RED→GREEN、checkstyle 证据齐备；README 新增 T-041 状态行。
- In progress: 无。
- Blocker: None。
- Unblock condition: N/A。
- Next steps: 独立 verifier PASS 已完成；`status` 置 `verified`，提交由主会话决定。
- Watch-outs:
  - 工作区含 T-036/T-037/T-039/T-040 未提交改动；本任务只改 `SettingsApiController.java`、新增 `ModelRoleSettings.java`、改 `ProviderSettingsController.java`（未跟踪文件）、改 `SettingsApiControllerTest.java` 与 README，未触 `android/**`/`web/shared/**`/`DesktopSchema.java`/`web/contracts/**`。
  - `ProviderSettingsController.java` 为未跟踪文件（T-036 新建未提交），`git diff` 不显示其改动，核验请直接读文件。
  - 若后续新增第 4 个角色或用例增减，`ModelRoleSettings` 的 `key(role)` 需同步（默认分支回落 TRANSLATION）。
