# Treelearn Harness Task: 前端设置页——Provider/Key/模型分工与模型选择

id: T-2026-10-10-039
title: 前端设置页——Provider/Key/模型分工与模型选择
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 400
shared_code_touched: false
depends_on: [T-2026-10-10-frontend-branch-workspace-031.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary: 交付知芽本地 Web 版（React+TS+Vite，`web/frontend`），功能与活动 Android 调用链一致，薄荷雾面/毛玻璃/icon-only 双语。本任务为前端轨设置片：把 `/settings` 占位替换为真实设置页。

- Problem/background: 当前 `/settings` 仅占位，用户无法录入 Provider/API Key，导致 Web 端无法真实使用；模型分工与回落无入口。
- Users/scenarios: 单人本地用户首次配置 Provider+Key、切换启停、拉取模型、编辑/删除、配置三档模型分工；无 Key 时从主页/工作区一键跳到提供商页。
- Expected outcome: `SettingsLayout` 分区导航 + `ProviderEditorPage`（`/settings/providers`）+ `ModelRolesPage`（`/settings/model-roles`）+ 通用/关于页；无 Key 引导闭环。
- Core flow and reversibility: 去设置 → 提供商页录入 Key → 拉取模型 → 模型分工页选择 → 保存；均有明确成功/失败反馈，可回退（新增文件）。
- Inputs/outputs: 输入=契约客户端 `api` 与 fixtures 对齐的字段；输出=页面渲染与命令下发。
- State and exception handling: 加载中/空态/无 Key/重名/最后提供商/404 未支持；错误码→i18n 文案，不静默成功。
- Constraints and trade-offs: 用户文案全走 i18n；按钮 icon-only（`IconButton` + 图标注册表）；只用 token 色值/毛玻璃面板；不新增依赖；依赖方向 pages→layouts→components→hooks→store→api→platform→utils。
- AI recommendation and rationale: 数据访问集中到 `store/settingsData.ts`（只经 `api`）；组件测试 mock `fetch` 传输边界；`model-roles` 读/写端点由 T-036 提供，前端对意外 `404` 保留明确提示（不静默成功）。
- User choice and custom additions: 用户确认整体方案与「全部按钮 icon-only/毛玻璃/i18n 守卫」约束（父需求）。
- AI-autonomous decisions: 分区路由、表单与确认层切分、Key 内存清理时机、`model-roles` 404 提示文案。
- User-confirmation decisions: 无新增（范围已由父需求与矩阵行覆盖）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-039-*`：RED/GREEN（`pnpm test`）、lint/check:user-copy-i18n/build。
- Facts: 契约 §6.6/§5.9/§5.12/§3.3 与 fixtures 已定义字段；`api/types.ts` 已含 Provider/Model/ModelRoles/ModelResolution；`model-resolution` 读端点已由 T-034 落地；`model-roles` 读/写端点已由 T-036 提供（数据目录非敏感偏好文件，未新增永久表）。
- Assumptions: 真实 Provider 联调在后续任务；本任务测试以 mock transport 驱动。
- Unknowns: 无（`model-roles` 读/写端点已由 T-036 落地，形状以契约 §5.12 为准）。
- Risks: `model-roles` 读/写端点已由 T-036 提供；前端对 `404 NOT_FOUND` 的明确提示保留为防御性兜底（端点意外缺失时不静默成功）。
- Deferred: 通用/关于页正文、联网搜索设置、真实 Provider 端到端联调、浏览器视觉复核。
- Non-goals: 后端/契约/Android 改动；附件、分叉、批注、搜索、统计。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

把 `/settings/*` 从占位替换为真实设置页：Provider/Key 录入与模型分工配置，至契约客户端边界（mock 驱动），满足 i18n/icon-only/毛玻璃/键盘可达约束，并对 `model-roles` 端点意外缺失保留明确提示（不静默成功）。

## Scope

### In scope
- `web/frontend/src/layouts/SettingsLayout.tsx`、`src/pages/ProviderEditorPage.tsx`、`src/pages/ModelRolesPage.tsx`、`src/store/settingsData.ts`。
- `src/pages/Router.tsx` 接线 `/settings/providers`、`/settings/model-roles`、`/settings`（通用/关于）。
- `src/pages/HomePage.tsx`、`src/pages/BranchWorkspace.tsx` 的「去设置」改为 `/settings/providers`。
- `src/i18n/{zh-CN,en-US}.ts` 新增设置文案；`src/components/icons.ts` 新增所需图标；`src/styles/global.css` 新增设置样式。
- 测试：`ProviderEditorPage.test.tsx`、`ModelRolesPage.test.tsx`、`SettingsLayout`/`Router` 覆盖。

### Out of scope
- `web/backend/**`、`web/contracts/**`、`android/**`、`web/shared/**`；联网搜索/语音设置；真实联调。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: `/settings/*` 占位页被真实设置页替换（旧行为：仅渲染 `placeholder.body`）；`/library`、404、主页/工作区其余行为保留。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `Router.tsx`（唯一页面映射）、`platform/router.ts`（`/settings/<section>` 已解析）、`HomePage.tsx`/`BranchWorkspace.tsx`（无 Key 引导入口）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `/settings/providers`、`/settings/model-roles` 可用（用户可见）；「去设置」目标改为提供商页。
- Authoritative code/config/migration evidence: `web/frontend/src/pages/*`、`web/frontend/src/layouts/SettingsLayout.tsx`、`web/frontend/src/store/settingsData.ts`。
- Documentation surfaces checked: `web/README.md` 前端状态/已知缺口节；契约 §6.6/§5.12。
- Documentation files updated: `web/README.md` 状态一行（设置录入由占位变为可用）；`model-roles` 读/写端点状态由 `T-2026-10-10-036` 实现（数据目录非敏感偏好文件），前端 `404 NOT_FOUND` 提示保留为防御性兜底。
- Producer/consumer/sibling/compatibility paths checked: 契约客户端、i18n、图标注册表、Router。
- Superseded phrases/examples reverse-searched: 「设置页正文」占位表述、`/settings` 引导目标。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `pnpm test`，cwd=web/frontend，记录退出码与计数）；业务行 ≤500（估算 400，soft 200 警告）。
- [ ] 提供商页：列表（名称/启停/模型数徽标/Key 脱敏状态）、空态引导、新建（重名内联提示）、编辑、删除确认、最后提供商警示、Key 录入（password 型、提交后清空内存、不入 localStorage/URL/日志）、拉取模型结果。
- [ ] 模型分工页：CHAT/FAST/TRANSLATION 模型选择 + 预算 OFF/AUTO/LOW/MID/HIGH + 建议开关；保存调用 `PUT /api/settings/model-roles`；`GET /api/settings/model-resolution` 展示回落（NONE/BUILTIN/EARLIEST/EMPTY）；404/未支持明确提示（不静默成功）。
- [ ] 无 Key 引导闭环：主页/工作区「去设置」到达 `/settings/providers`，可见 Key 录入入口。
- [ ] i18n 双语渲染、无硬编码用户文案；icon-only 按钮 + 图标注册表；依赖方向未破。
- [ ] `pnpm lint`、`check:user-copy-i18n`、`build` 全过；独立 verifier PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `src/pages/ProviderEditorPage.test.tsx`、`src/pages/ModelRolesPage.test.tsx`、`src/layouts/SettingsLayout.test.tsx`、`src/pages/Router.test.tsx`（新增设置路由）。
- Command: pnpm test
- Exit code: 1
- Observed failure: `Test Files 4 failed | 14 passed (18)`，失败文件因 `Failed to resolve import "../layouts/SettingsLayout" / "./ProviderEditorPage" / "./ModelRolesPage"`（实现缺失）；可运行的 14 文件 126 例通过。
- Failure reason validated: `SettingsLayout`/`ProviderEditorPage`/`ModelRolesPage` 及 `settingsData` 尚不存在，路由未接线。

### GREEN — minimum implementation
- Implementation: `store/settingsData.ts` + `layouts/SettingsLayout.tsx` + `pages/ProviderEditorPage.tsx` + `pages/ModelRolesPage.tsx` + `Router`/引导接线 + i18n/图标/样式。
- Command: pnpm test
- Exit code: 0
- Passing evidence: `Test Files 18 passed (18)`、`Tests 149 passed (149)`（证据 `artifacts/evidence/T-2026-10-10-039-green.txt`）。
- Regression evidence: 变更前基线 131 例（15 文件）保持通过；本任务新增 18 例（131→149），无既有断言被削弱。

### REFACTOR — optional
- Cleanup: 通过 `pnpm lint`（eslint + `check:user-copy-i18n`，33 文件）与 `pnpm build`；为守 `check:user-copy-i18n` 的文本预过滤，把箭头类型 `() => Promise<void>` 改为 `interface Action { (): Promise<void> }`，并移除 JSX 中文注释行。
- Regression evidence: 同 GREEN（18 文件 / 149 例）。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 端点以契约为唯一权威：拉取模型为 `POST /api/providers/{id}/models`（任务描述写作 `/models/refresh`，按契约 §6.6 行 621 实现）。
- 重名文案沿用既有键 `errors.PROVIDER_NAME_TAKEN`（任务描述写作 `errors.providerNameTaken`）。
- 命令类请求一律带客户端 `commandId`（契约 §4）。
- `model-roles` 读/写端点已由 `T-2026-10-10-036` 实现（数据目录非敏感偏好文件，未新增 schema）；保存收到 `404 NOT_FOUND` 时显示明确不可用提示，绝不静默成功——该分支保留为**防御性处理**（端点意外缺失时兜底）。
- Key 输入 `type=password`，成功后清空内存值；不写入 localStorage/URL/日志。
- 业务行实测（非空非注释）：新增 4 文件 328 + 改动 164 = **492**，证据 `artifacts/evidence/T-2026-10-10-039-sizing.txt`。≤500 硬上限；>200 soft 警告（估算 400 略有超出，因设置页两条交互面较大）。
- 图标注册表新增 `Check`/`Info`/`KeyRound`/`Power`/`RefreshCw`/`SlidersHorizontal`/`Trash2`（均 lucide-react 1.55.0，无新增依赖）。
- 文档：`web/README.md` 状态新增一行（设置页可用 + `model-roles` 写端点缺口）。

## Verification report

- Verdict: PASS（独立 verifier 全过；1 项文档漂移已由 T-040 更正）
- Evidence: 独立 verifier 实测 `pnpm test` 18 文件/149 例、`lint`/`check:user-copy-i18n`/`build` 全 EXIT=0；设置分区导航 icon-only + aria-label + Enter 可达；提供商列表/空态/重名内联/编辑/启停/删除确认 + 最后提供商警示；Key 输入 `type=password`、提交后清空内存值、不入 localStorage/URL/日志（测试真断言）；模型分工三角色 + 档位 + 建议开关 + 预算 + 回落展示 + 保存；404 分支为防御性（后端读写端点已由 T-036 提供）且不静默成功；「去设置」→ `/settings/providers`。自测证据 `artifacts/evidence/T-2026-10-10-039-{red,green,lint,user-copy-i18n,build,sizing}.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: Router/HomePage 旧「设置占位 / `/settings` 引导」断言因本任务授权的真实设置页与新引导目标而过时，已按新行为更新；无其它断言削弱。
- Gaps: `GET /api/providers/{id}/models` 的消费依赖后端（已由 T-040 补齐）；浏览器端到端未做（真实 Key 未提供）。
- Risks: 无实义风险（`model-roles` 读/写端点已由 T-036 提供；前端 404 提示仅为防御性兜底）。
- Sizing: 独立计数 492（≤500 硬上限，>200 软警告）。

## Handoff notes

- Completed: 实施 + 自测（2026-10-10）。产物：`src/store/settingsData.ts`、`src/layouts/SettingsLayout.tsx`、`src/pages/{ProviderEditorPage,ModelRolesPage}.tsx`、`Router`/`HomePage`/`BranchWorkspace` 接线、i18n 双语键、图标注册表、`global.css` 设置样式；`pnpm test` 149/149，lint/i18n/build 全过。
- In progress: 无。
- Blocker: None。
- Unblock condition: N/A。
- Next steps: 独立 verifier PASS 已完成（1 项文档漂移已由 T-040 更正）；`status` 置 `verified`。剩余：真实 Provider 端到端联调与浏览器视觉复核（真实 Key 未提供，本任务范围外）。
- Watch-outs: i18n 用户文案守卫（`{/* 中文 */}` JSX 注释与 `=> Promise<void>` 这类箭头类型会误触发文本预过滤）；不新增依赖；`model-roles` 读/写端点已由 T-036 提供（数据目录非敏感偏好文件，未新增表），前端对意外 `404` 仅作防御性提示、不得静默成功。
