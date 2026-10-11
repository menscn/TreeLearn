# Treelearn Harness Task: 前端外壳与主页（A 布局入口、导航、首问）

id: T-2026-10-10-030
title: 前端外壳与主页（A 布局入口、导航、首问）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 380
shared_code_touched: false
depends_on: [T-2026-10-10-web-design-system-icons-006.md, T-2026-10-10-frontend-contract-codecs-015.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 设计系统与契约客户端已就绪；需要产品外壳与入口页（原型信息结构参考 `Docs/04-ux-design/知芽-Web本地版-交互原型-V0.1.html`，视觉权威=token 与毛玻璃基元）。本任务为前端轨首片：外壳 + 主页 + 最小路由 + 草稿（按分支，本地保存）。
- Users/scenarios: 桌面键鼠/触控板、窗口宽度变化；首次进入无 Key → 引导设置；首问输入与状态机。
- Expected outcome: `AppShell`（导航、设置入口、i18n、主题、响应宽度、键盘可达）；最小内部路由（无新依赖：history API + 路径匹配；路由：`/`、`/library`、`/topic/:id`、`/settings/*` 占位）；主页（hero + 首问 composer + 最近主题卡）；composer 状态机（无 Key 禁用并给可执行引导；草稿非空可发送；生成中抑制重复）；草稿按分支存 `localStorage`（可恢复缓存，含清理策略）。
- Core flow and reversibility: 纯前端 + mock 数据（真实 API 联调在后续任务）；可回退（新增文件）。
- Inputs/outputs: 输入=契约客户端（已就绪）与本地草稿；输出=页面渲染与交互状态。
- State and exception handling: 无 Key、加载中、空列表、API 失败（错误码→i18n 文案，来自 `api/errors`）。
- Constraints and trade-offs: 用户文案全走 i18n（守卫）；按钮 icon-only（既有 `IconButton` + 图标注册表）；毛玻璃/薄荷雾面视觉以 token 为准；不新增依赖；依赖方向 pages→layouts→components→hooks→store→api→platform→utils。
- AI recommendation and rationale: 最小路由自实现（避免未验证依赖）；组件测试用 RTL + mock transport；视觉证据留待浏览器任务。
- User choice and custom additions: 用户要求 huashu-design 美观度/毛玻璃/图标按钮（已由 T-006 落地为 token 与基元，本任务消费）。
- AI-autonomous decisions: 组件切分、路由实现、草稿键设计。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-030-*`：RED/GREEN（`pnpm test`）、lint/i18n/build；组件测试覆盖状态机与草稿恢复。
- Facts: T-006 交付 `GlassPanel`/`IconButton`/图标注册表/tokens；T-015 交付 `api/types|errors|client|sse`；前端既有 i18n 渲染时解析与守卫。
- Assumptions: 真实数据经契约客户端；本任务测试以 mock transport 驱动。
- Unknowns: 无。
- Risks: 视觉最终效果需浏览器任务复核（记录为后续）；路由极简实现的能力边界（记录限制）。
- Deferred: 工作区 A/B/C 对话页（下一任务）；真实联调与浏览器截图（后续）。
- Non-goals: 后端 API；B/C 视图；学习库/设置页正文。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付应用外壳、最小路由与主页首问闭环（至接口边界，mock 驱动），视觉与文案遵循设计系统与 i18n 守卫。

## Scope

### In scope
- `src/app/`（路由与 Shell）、`src/pages/HomePage`、`src/layouts/`（如需要）、`src/components/`（消费既有基元）、`src/store/`（主页状态与草稿）。
- 路由：`/`（主页）、`/library`、`/topic/:id`、`/settings/*`（后两者本任务占位页）；未知路径 404 占位。
- 主页：hero、首问 composer（thinking 开关、发送/停止状态机、无 Key 引导、错误展示）、最近主题卡（`GET /api/topics`，mock 测试）。
- 草稿：按「分支/主页」键存 localStorage；发送成功后处理；清理策略（如 30 天或显式清除）。
- 测试：路由、Shell 导航与键盘可达、composer 状态机（无 Key、空草稿、生成中）、草稿恢复、错误码→文案。

### Out of scope
- B/C 视图、学习库/设置正文、真实联调、浏览器截图（后续任务）。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增页面/路由；AppShell 既有空壳行为被替换（记录旧行为：仅标题壳）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `src/main.tsx`（挂载点）、既有 i18n/设计系统。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 前端具备主页与导航（用户可见）。
- Authoritative code/config/migration evidence: `web/frontend/src/**`。
- Documentation surfaces checked: `web/README.md` 前端状态节。
- Documentation files updated: `web/README.md` 状态一行。
- Producer/consumer/sibling/compatibility paths checked: 契约客户端与设计系统。
- Superseded phrases/examples reverse-searched: 「AppShell 空壳」表述。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `pnpm test`，cwd=web/frontend，记录退出码与计数）；业务行 ≤380。
- [ ] 路由与 Shell：四条路由可达、未知路径占位、导航键盘可达（Tab/Enter）、设置入口 icon-only。
- [ ] composer 状态机：无 Key→禁用+引导；空草稿→禁用；生成中→抑制重复；错误码→i18n 文案（无硬编码）。
- [ ] 草稿：按分支键保存、刷新恢复、发送成功后的清理策略有测试。
- [ ] 视觉：使用 token/毛玻璃基元/图标注册表（代码证据）；无自由色值/自绘图标。
- [ ] `pnpm lint`、`check:user-copy-i18n`、`build` 全过；依赖方向未破。
- [ ] 独立 verifier PASS；浏览器视觉复核登记为后续任务。

## TDD execution record

### RED — failing test first
- Test/scenario: `src/**/__tests__`（路由/Shell/composer/草稿）。
- Command: `pnpm test`（cwd=web/frontend）
- Exit code: 1
- Observed failure: 4 个新测试文件模块解析失败 + AppShell 4 条旧断言失败（空壳被真实外壳取代）
- Failure reason validated: 缺失组件/路由

### GREEN — minimum implementation
- Implementation: Shell + 路由 + 主页 + 草稿（首轮 57 项；契约修复后 58 项）。
- Command: `pnpm test`（cwd=web/frontend）
- Exit code: 0
- Passing evidence: 58 passed / 0 failed
- Regression evidence: 既有 32 项全绿（32→57→58）

### REFACTOR — optional
- Cleanup: 按 lint/评审整理（含本轮契约缺口修复与 guardian 意见：HomePage 头注释更正）。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 视觉参考：`Docs/04-ux-design/知芽-Web本地版-交互原型-V0.1.html` 的信息结构与 `web-local-design-tokens.json`（token 已生成）——**不复制原型里的模拟业务逻辑**。
- 路由实现保持极简并文档化限制（无嵌套路由参数解析之外的特性）。
- 本轮修复（契约缺口）：`GET /api/topics` 原返回裸 `Topic`（无名称）而主页卡需要标题——已按 Android `TopicDao.listOverviews`/桌面 `TopicStore.listOrderedByRecency` 新增契约 §5.1b `TopicSummary` 并改为 `[TopicSummary]`；`displayTitle = rootTitle ?? rootQuestion`。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10）+ PASS（contract-guardian，2026-10-10；1 条低危注释问题已修）
- Evidence: verifier 自跑 `pnpm test` 58/58、`lint`/`check:user-copy-i18n`（21 生产文件）/`build` 全 EXIT=0；RED 证据真实（解析失败+旧断言失败）。契约修复复核：§5.1b 字段与 Android/桌面概览行一致、`displayTitle` 派生规则一致、fixture/TS/键清单/测试四处一致（fixtures 101）、HomePage 回退有测试；guardian 全量反查无其它 `[Topic]` 旧形状消费者。
- 断言强度抽查（verifier，≥6）：状态机/草稿恢复为真状态断言；mock 只 mock 传输边界。
- 修复记录：① 契约缺口（TopicSummary）；② guardian 低危——HomePage 头注释与实现矛盾 → 已更正（leader 应用同一提交）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；+26 用例（32→58）。
- 规模：verifier 独立计数约 380–383（含修复增量），≤500 硬上限、>200 警告、略超自定 380 目标（记录）。
- Gaps: 真实 API 联调与浏览器视觉复核（毛玻璃/88/248px 断点）留待后续任务；composer「抑制重复」未做二次点击验证（verifier 备注）。
- Risks: 极简路由能力边界已知并文档化；无 Key 判定当前以 model-resolution `fallback=EMPTY` 近似（服务端 `POST /api/topics` 的 `KEY_MISSING` 为权威收口，联调时复核）。

## Handoff notes

- Completed: 实施 + 契约修复 + 双守卫 PASS（2026-10-10）。产物：`src/platform/router.ts`、`hooks/{useRoute,useDraft}`、`store/{drafts,homeData}`、`components/Composer`、`pages/{Router,HomePage}`、`layouts/AppShell`（真实外壳）；契约 §5.1b + fixture；58 项测试全绿。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock Condition: N/A.
- Next steps: 工作区 A/B/C 对话页（下一任务）；真实联调与浏览器视觉复核（Phase 5/7）。
- Watch-outs: i18n 守卫；不得引入新依赖；`GET /api/topics` 现返回 `[TopicSummary]`（后端实现须对齐）。