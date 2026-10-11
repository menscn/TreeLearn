# Treelearn Harness Task: 契约缺口收口（模型列表读端点、过时注释、响应包裹 fixture、ForkResult 澄清）

id: T-2026-10-10-040
title: 契约缺口收口（模型列表读端点、过时注释、响应包裹 fixture、ForkResult 澄清）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 180
shared_code_touched: false
depends_on: [T-2026-10-10-provider-settings-endpoints-036.md, T-2026-10-10-fork-endpoints-037.md, T-2026-10-10-frontend-settings-page-039.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-10-001`（本地 Web 版）已确认；本任务收口 T-036/T-037/T-039 三项独立复核共同遗留的契约缺口。
- Problem/background: 复核发现四处未收口——(1) `GET /api/providers/{id}/models` 契约 §6.6/§11 与前端已定义并消费，但后端未实现，落兜底 404，真实集成下模型分工页选不到模型（contract-guardian FAIL，阻断级）；(2) `SettingsApiController` 类注释与 `ModelRolesPage` 注释仍称 model-roles「尚未实现」，与 T-036 现状矛盾；(3) 契约 §6.6 的两个响应包裹 `{deletedProviderId}`、`{added,models}` 缺 fixtures 与双端形状断言；(4) 契约 §6.3 `ForkResult` 未言明 `bootstrap.rootBranch` 指子分支。
- Users/scenarios: 本地 Web 用户进入「模型分工」页需要选择模型，依赖 `GET /api/providers/{id}/models` 返回已启用 Provider 的模型；该端点缺失时下拉为空。
- Expected outcome: 四处缺口全部收口，双端 fixtures 计数/形状断言同步，`validate_tasks.py` PASS。
- Core flow and reversibility: 后端控制器新增一个只读 GET；其余为注释/契约/fixtures/测试改动，均可回退。
- Inputs/outputs: 输入=`GET /api/providers/{id}/models`；输出=`[Model, ...]`（§5.9），Provider 不存在→`404 NOT_FOUND`（`resource=provider`）。
- State and exception handling: 只读；无状态变更；未找到→§3.1 封装 404 `NOT_FOUND`。
- Constraints and trade-offs: 不改 `android/**`、`web/shared/**`、`DesktopSchema.java`；不新增第三方依赖；不 git commit；不重启 8733 服务；用户可见文案走 i18n；契约改动仅限 §6.3 澄清一句。
- AI recommendation and rationale: 端点复用既有 `ModelStore.listForProvider` 与 `ProviderSettingsController` 已有的 Model 投影，避免第二套映射；fixtures 字段严格取自契约 §6.6 与后端 `ConfigDtos` 实际字段，不臆造。
- User choice and custom additions: 已批准（父需求确认来源）。
- AI-autonomous decisions: 测试用例切分、注释措辞、README 当前态更新。
- User-confirmation decisions: 无新增（未新增永久表）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-040-*.txt`（RED/GREEN/checkstyle/frontend/json）。
- Facts: `ConfigDtos` 已含 `DeletedProvider`/`ModelsRefreshed`；`ModelStore.listForProvider` 已就绪；契约 §6.6/§11 已列 `GET/POST /api/providers/{id}/models`；前端 `settingsData.ts` 已消费该 GET。
- Assumptions: 无。
- Unknowns: 无。
- Risks: 无实义风险；仅 fixtures 计数漂移必须双端同改。
- Deferred: 不涉及的其它契约端点。
- Non-goals: 联网搜索设置、附件、派生、Android。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

收口四项复核遗留：实现 `GET /api/providers/{id}/models`；更正两处过时注释；补 `deleted-provider`/`models-refreshed` 两个响应包裹 fixture 并双端同步；为契约 §6.3 `ForkResult.bootstrap.rootBranch` 补一句澄清。

## Scope

### In scope
- `ProviderSettingsController` 新增 `GET /api/providers/{id}/models`；`ProviderSettingsControllerTest` 增 3 例（有模型/空列表/不存在）。
- `SettingsApiController` 类注释、`ModelRolesPage.tsx` 注释、T-039 任务文件 Implementation notes / Documentation impact record 更正为现状。
- 新增 `web/contracts/fixtures/dtos/deleted-provider.json`、`models-refreshed.json`；双端计数/形状断言同步（后端 26→28，前端 dtos 26→28、总数 102→104）；`types.ts` 补 `DeletedProvider`/`ModelsRefreshed`。
- 契约 `web/contracts/api.md` §6.3 澄清一句。
- `web/README.md` 当前态更新。

### Out of scope
- 其它契约端点、Android、`web/shared/**`、`DesktopSchema.java`、新依赖。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增只读 GET；T-036 八个端点与其余端点行为不变；注释/契约/fixtures 改动为行为中性。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者=契约 §6.6；消费者=前端 `web/frontend/src/store/settingsData.ts#loadProviderModels` 与 `ModelRolesPage`；同级=既有 `POST /api/providers/{id}/models`（刷新）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 注释/契约/fixtures 为行为中性，用 `git diff` 与 fixtures 往返测试作证据。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `GET /api/providers/{id}/models` 由兜底 404 变为按 §6.6 返回 `[Model, ...]`；fixtures 总数 102→104、dtos 26→28。
- Authoritative code/config/migration evidence: `web/backend/.../api/ProviderSettingsController.java`、`web/contracts/api.md` §6.6/§11、`web/contracts/fixtures/dtos/*.json`。
- Documentation surfaces checked: `web/README.md` 状态与 fixtures 计数节；`web/contracts/api.md` §6.3/§6.6/§11；T-039 任务文件。
- Documentation files updated: `web/README.md`（fixtures 计数 102→104、移除「GET models 未实现」缺口、新增 T-040 状态行）；`web/contracts/api.md` §6.3（澄清一句）；T-039 任务文件（Implementation notes / Documentation impact record）。
- Producer/consumer/sibling/compatibility paths checked: 契约端点索引；fixtures `provider.json`/`model.json`/`model-roles.json`；前端 `types.ts`/`settingsData.ts`/`ModelRolesPage`。
- Superseded phrases/examples reverse-searched: `未实现`、`102`、`26`（详见交接备忘）。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史；仅更新当前态与 T-039 任务文件的现状段落。

## Acceptance criteria

- [x] RED→GREEN（同命令 `.\gradlew.bat :test --rerun-tasks`，红色 234/3 failed → 绿色 234/0）；业务行（非空非注释）实测约 17，≤500。
- [x] `GET /api/providers/{id}/models`：有模型返回 `[Model, ...]` 且键集同 fixture `model.json`；无模型返回 `[]`；未知 Provider → `404 NOT_FOUND`（`resource=provider`）。
- [x] 两处注释与现状一致；T-039 Implementation notes / Documentation impact record 与 README 一致。
- [x] 两个新 fixture JSON 合法（`ConvertFrom-Json` 全量 104/104 通过）且被双端往返/形状断言消费（后端 28、前端 dtos 28、总数 104）。
- [x] 契约 §6.3 澄清仅一处，不扩散。
- [x] checkstyle 通过；前端 `pnpm test`(149/149)/`lint`/`check:user-copy-i18n`/`build` 全过；`validate_tasks.py` PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `ProviderSettingsControllerTest` 新增 3 例（`listModelsReturnsTheProvidersModels`/`listModelsReturnsEmptyArrayForAProviderWithoutModels`/`listModelsOnUnknownProviderIsNotFound`），先于生产实现。
- Command: `.\gradlew.bat :test --rerun-tasks`
- Exit code: 1
- Observed failure: `234 tests completed, 3 failed`——新增的 `listModelsReturnsTheProvidersModels`/`listModelsReturnsEmptyArrayForAProviderWithoutModels` 因 `GET /api/providers/{id}/models` 未实现落到未知 API 兜底 `404 NOT_FOUND`，`andExpect(status().isOk())` 失败；`listModelsOnUnknownProviderIsNotFound` 兜底 404 的 `params` 无 `resource=provider`，断言失败。
- Failure reason validated: 失败确因模型列表读端点未实现（兜底 404），非测试自身错误；同期既有 231 例全绿。
- Evidence: `artifacts/evidence/T-2026-10-10-040-red.txt`

### GREEN — minimum implementation
- Implementation: `ProviderSettingsController` 新增 `GET /api/providers/{id}/models`（复用 `ModelStore.listForProvider` 与抽出的 `modelDtos` 投影，刷新端点改用同一投影）；两个 fixtures；双端 fixtures 计数/形状断言（后端 28、前端 dtos 28、总数 104）；`types.ts` 增 `DeletedProvider`/`ModelsRefreshed`；注释/契约/README/T-039 更正。
- Command: `.\gradlew.bat :test --rerun-tasks`
- Exit code: 0
- Passing evidence: **234 tests / 0 failed**，`BUILD SUCCESSFUL`（`artifacts/evidence/T-2026-10-10-040-green.txt`）；新增 3 例全绿，`DtoFixturesRoundTripTest`（28 fixtures）通过。`.\gradlew.bat check` 退出码 0（checkstyleMain/checkstyleTest 通过），见 `artifacts/evidence/T-2026-10-10-040-checkstyle.txt`。
- Regression evidence: 既有 231 例全绿（231 + 新增 3 = 234）；fixtures 计数断言随新增同步（26→28），无既有断言被削弱。

### REFACTOR — optional
- Cleanup: 无额外重构。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: 无。
- Reason: N/A。
- User approval: N/A。
- Alternative verification: N/A。

## Implementation notes

- 端点复用 `ProviderSettingsController` 既有 Model 投影（`ConfigDtos.Model(id, provider.name, model.modelId)`），与 `POST .../models` 同源，避免第二套映射。
- fixtures 字段严格取自契约 §6.6（`{deletedProviderId}`、`{added, models}`）与后端 `ConfigDtos.DeletedProvider`/`ModelsRefreshed`/`Model` 实际字段，未臆造。
- 契约改动仅 §6.3 一句澄清；§6.6/§11 已列该 GET，无需改契约正文。

## Verification report

- Verdict: PASS（独立 verifier 全过；contract-guardian 回归复核：上轮 FAIL 已闭合、三方一致）
- Evidence: 独立 verifier 实测 `:test --rerun-tasks` 237/0、`check` EXIT=0、前端 `pnpm test` 149 例；fixtures 全量 `ConvertFrom-Json` 104/104；guardian 确认 `GET /api/providers/{id}/models` 形状与 §6.6/§5.9 一致（`ProviderSettingsController.listModels`，未知 Provider→404 `NOT_FOUND(resource=provider)`）；两个新 fixture 字段与 `ConfigDtos` 一致且双端真实同步（后端 28、前端 dtos 28/总数 104/完备性 46）；两处过时注释已改；`api.md` 仅 +1 行（§6.3 澄清）；§11 无需补记仍成立。实现者自证据 `artifacts/evidence/T-2026-10-10-040-{red,green,checkstyle,frontend-test,frontend-lint,frontend-i18n,frontend-build,json}.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: fixtures 计数断言随新增 fixtures 同步（后端 26→28、前端 dtos 26→28、总数 102→104、完备性 44→46），非削弱；新增端点用例含正/空/未找到三态。
- Gaps: fixtures fixtures-ai（`contracts/fixtures-ai/`）不在本次 `ConvertFrom-Json` 范围（未改动）。
- Risks: 无。
- Sizing: 独立计数 ≈17。

## Handoff notes

- Completed: ① `GET /api/providers/{id}/models` 已实现（`ProviderSettingsController.listModels` + `modelDtos` 投影）并含 3 例测试；② `SettingsApiController` 类注释与 `ModelRolesPage.tsx` 注释、T-039 任务文件 Implementation notes / Documentation impact record 已更正为现状（404 分支标注为防御性）；③ 新增 `dtos/deleted-provider.json`、`dtos/models-refreshed.json`，双端计数/形状断言同步，`types.ts` 补 `DeletedProvider`/`ModelsRefreshed`，`settingsData.ts` 改用命名类型；④ 契约 §6.3 补 `ForkResult.bootstrap.rootBranch` 澄清一句。RED→GREEN、checkstyle、前端四项与 fixtures JSON 校验证据齐备；README 当前态（fixtures 计数、移除 GET models 缺口、新增 T-040 状态行）已同步。
- In progress: 无。
- Blocker: None。
- Unblock condition: N/A。
- Next steps: 独立 verifier PASS 与 contract-guardian 回归复核均已完成（上轮 FAIL 闭合、三方一致）；`status` 置 `verified`，提交由主会话决定。
- Watch-outs:
  - fixtures 计数双端同改（后端 `DtoFixturesRoundTripTest`、前端 `fixtures.test.ts` 的 26/28、102/104、44/46 三处计数）；再增减 fixtures 需同步这两处。
  - 契约改动仅 §6.3 一句澄清；§6.6/§11 本已列该 GET，未改。
  - `ProviderSettingsController.java`/`ModelRolesPage.tsx`/`settingsData.ts` 等为 T-036/T-039 新建且尚未提交（工作区未跟踪），本任务在其上做增量改动。
