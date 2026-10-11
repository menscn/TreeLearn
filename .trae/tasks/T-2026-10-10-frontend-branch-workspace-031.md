# Treelearn Harness Task: 工作区（A 专注阅读 + B 并排分支）与真实 SSE 流式

id: T-2026-10-10-031
title: 工作区（A 专注阅读 + B 并排分支）与真实 SSE 流式
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 500
shared_code_touched: false
depends_on: [T-2026-10-10-frontend-shell-home-030.md, T-2026-10-10-frontend-contract-codecs-015.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: A「专注阅读」是默认布局，B「并排分支」为辅助；工作区要消费真实 SSE 契约（`sse.ts`）显示生成中/稳定/失败/中断/取消，并支持停止与原位重试入口（后端端点存在后即联调）。
- Users/scenarios: 进入主题 → 默认 A；从分叉来源切到 B 对照；追问与引用；生成中停止；失败/中断原位重试；刷新恢复（从快照读事件）。
- Expected outcome: `/topic/:id` 工作区（含分支参数）；A：阅读列（消息位、活跃修订内容、流式增量与光标、状态条与动作）；B：并排对照（当前分支 vs 其分叉来源；锚点高亮与快照说明）；布局切换 A/B/C（C 待树布局端点，占位并显式说明）；composer 追问 + 引用 chip；停止/重试动作接契约端点。
- Core flow and reversibility: 契约客户端 + mock transport 测试；真实联调在 API 层完成后（记录为后续）。
- Inputs/outputs: 输入=分支树/消息/操作事件；输出=渲染与动作请求。
- State and exception handling: 生成中/稳定/失败/中断/取消、断线重连（`sse.ts` 快照恢复）、晚到/重复事件、错误码→i18n。
- Constraints and trade-offs: 消息渲染本轮为**安全纯文本**（转义 + 保留换行/空白）；完整 Markdown/代码/表格/公式渲染属后续任务；不得引入新依赖；不得在前端实现树布局计算（C 视图依赖后端共享 `:tree-layout` 端点）。
- AI recommendation and rationale: A/B 先行、C 待端点；真实 SSE 走既有客户端（fetch-event-source），测试注入帧源。
- User choice and custom additions: 已批准方案与用户视觉要求（毛玻璃/icon-only）。
- AI-autonomous decisions: 组件切分、状态呈现细节。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-031-*` RED/GREEN + 守卫输出。
- Facts: `sse.ts` 已有状态机与 `onError`；`TopicSummary`/`Branch`/`Message`/`Operation` 类型已就绪；后端 SSE 端点属 T-025/后续。
- Assumptions: 事件帧形状以契约 §7 fixtures 为准（测试直接消费 fixtures 场景驱动 UI 状态）。
- Unknowns: 无阻塞项。
- Risks: 后端端点未就绪前只能在 mock 层验证（记录）；消息渲染能力本轮受限（记录）。
- Deferred: C 树图探索（依赖树布局端点）、Markdown/公式、选文/复制/引用动作细化（后续任务）、翻译/朗读。
- Non-goals: 后端实现；C 视图；Markdown 渲染器。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付 A/B 工作区与真实 SSE 流式 UI（mock 驱动测试），C 视图留待树布局端点并显式标注。

## Scope

### In scope
- 路由 `/topic/:id`（+ 可选查询参数 `branch`）替代占位；加载分支树（`GET /api/topics/{id}/branches`）与消息（`GET /api/branches/{branchId}/messages`）。
- A 布局：阅读列（USER/AI 位、活跃修订内容、流式增量经 `sse.ts`、设置光标）；状态条（生成中/稳定/失败/中断/取消 + 停止/原位重试按钮，icon-only）；追问 composer（引用 chip、thinking 开关、停止）；错误码→i18n。
- B 布局：当前分支与其分叉来源（`Branch.source`）并排；锚点区间在来源消息上的高亮（若在已加载消息内；否则显示可定位说明）；快照（`contextSnapshotId`）说明区。
- 布局切换控件（A/B/C，localStorage 记忆）；C 显示占位说明（未实现原因）。
- 测试：SSE fixtures 驱动状态机到 UI（正常/失败/中断/取消/重连补快照/晚到重复忽略）；A/B 渲染与切换；停止/重试请求发出（mock 断言）；错误码文案；依赖方向与 i18n 守卫。

### Out of scope
- C 视图实现；Markdown 渲染；选文/复制/高亮/批注；翻译/朗读；真实联调。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: `/topic/:id` 由占位页替换为工作区；占位页行为记录（仅提示）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `src/pages/Router.tsx`、`AppShell`、`sse.ts`。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 工作区可用（A/B），C 未实现。
- Authoritative code/config/migration evidence: `web/frontend/src/**`。
- Documentation surfaces checked: `web/README.md` 状态节；功能矩阵 C 行标注未验证。
- Documentation files updated: `web/README.md` 状态一行（含 C 待实现说明）。
- Producer/consumer/sibling/compatibility paths checked: 契约 §7 与 fixtures。
- Superseded phrases/examples reverse-searched: 「/topic/:id 占位」。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `pnpm test`，记录退出码与计数）；业务行 ≤500（硬上限；立项估 400 偏低，leader 已修正口径）。
- [ ] A：消息渲染（安全转义）、流式增量追加、状态条与停止/重试动作（mock 断言请求体含 commandId）；错误码文案。
- [ ] B：并排显示来源分支与锚点可读降级；快照说明区。
- [ ] SSE：6 个 sse fixtures 场景全部驱动 UI 状态（含重连补快照与晚到忽略）。
- [ ] 布局切换 A/B/C（C 占位）持久化；键盘可达。
- [ ] `pnpm lint`/`check:user-copy-i18n`/`build` 全过；无新依赖；前端不计算树布局。
- [ ] 独立 verifier PASS；真实联调与浏览器视觉登记为后续。

## TDD execution record

### RED — failing test first
- Test/scenario: `BranchWorkspace`、`useOperationStream`、`SideBySideView`、`layoutPreference`。
- Command: `pnpm test`（cwd=web/frontend）
- Exit code: 1
- Observed failure: 4 个新测试文件模块解析失败 + router.test 3 断言失败（branch 查询参数）
- Failure reason validated: 缺失组件/路由

### GREEN — minimum implementation
- Implementation: A/B 工作区 + SSE 接入 + 布局切换（首轮 83；草稿修复 86；errorCode 文案修复 88）。
- Command: `pnpm test`（cwd=web/frontend）
- Exit code: 0
- Passing evidence: 88 passed / 0 failed（14 文件）
- Regression evidence: 既有 58 项全绿（58→83→86→88）

### REFACTOR — optional
- Cleanup: 按 lint/评审整理（含 verifier 两项修复）。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None（规模未超 500 硬上限；超立项估算 400 按警告记录，leader 已修正口径行）
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- SSE 帧→UI 状态：直接消费 `web/contracts/fixtures/sse/*.json` 的帧序列（测试注入帧源），保证与契约同源。
- 安全渲染：本轮仅转义文本并保留换行；链接不做可点击（后续 Markdown 任务统一处理安全链接）。
- 失败终态文案：从 `failed` 帧的 `errorCode` 经 `errors.<code>` 渲染，未知码回退通用文案。

## Verification report

- Verdict: PASS（独立 verifier 两轮：首轮 PARTIAL 两项 → 修复后回归 PASS，2026-10-10）
- Evidence: verifier 自跑 `pnpm test` 88/88、`lint`/`check:user-copy-i18n`/`build` 全 EXIT=0；RED 真实；6 个 sse fixtures 驱动真实 `OperationEventState`（含 gap 补快照与晚到/重复/外来忽略）；断言强度抽查 ≥6 例均真断言（含 HTML 注入转义）；修复复核：失败文案取 `errors.<code>`（`RATE_LIMITED` 中英键均存在）+ 未知码回退；sizing 独立复算 497（含 i18n 46 / 不含 451）与证据一致。
- 修复记录：① 分支级草稿（`useDraft(draftKeyFor(branchId))`，四类行为真断言，`useDraft` 键切换无泄漏）；② `failed` 帧 errorCode 具体文案 + 回退；③ sizing 证据补计。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；+30 用例（58→88）。
- 规模：verifier 独立计数 497（含 i18n 46；不含 451）——未超 500 硬上限（余 3）、>200 警告；超立项估算 400 属估算偏低（同类镜像任务实测约为估算 1.2–1.5 倍），口径行已由 leader 修正为 ≤500 并注明。
- Gaps: 真实联调（后端 SSE 端点 T-025 完成后）；C 视图（依赖服务端树布局端点，前端明确不计算布局）；Markdown/选文-引用设置入口；浏览器视觉复核——均已登记。
- Risks: 端到端可消费性待联调；「停止」仅取消等待直到 SSE 端点落地后复核。

## Handoff notes

- Completed: 实施 + 两轮修复 + 独立 verifier PASS（2026-10-10）。产物：`BranchWorkspace`、`SideBySideView`、`MessageView`、`useOperationStream`、`store/{topicData,layoutPreference}`、`sse.ts` 帧源、Composer 引用 chip；88 项测试全绿。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-025（后端 SSE 端点）→ 真实联调；C 视图（树布局端点契约+后端）；Markdown 渲染任务。
- Watch-outs: 前端不得计算树布局；`GET /api/topics` 现返回 `[TopicSummary]`；SSE 帧形状以契约 fixtures 为准。