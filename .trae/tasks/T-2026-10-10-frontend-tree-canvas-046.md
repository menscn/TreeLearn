# Treelearn Harness Task: 前端 C 树图视图

id: T-2026-10-10-046
title: 前端 C 树图视图（TreeCanvasView 消费 §6.11 布局端点）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 220
shared_code_touched: false
depends_on: [T-2026-10-10-tree-layout-endpoint-045.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-10-001` 第二波「C 树图」的前端半——占位文案替换为真实树图。
- Problem/background: `BranchWorkspace` C 布局是占位 GlassPanel；T-045 已交付 §6.11 端点（labels 随几何下发 title/summary 文本）。
- Users/scenarios: 学习者切到 C 视图看到树干/叶/苞/名牌（当前路径绿色加亮、BUD 显示隐藏后代数、NEAR 摘要行），点击触达区跳转分支（`/topic/:id?branch=:id`）。
- Expected outcome: `TreeCanvasView` 组件：viewBox 取服务端 bounds（含土壤带），不自行推几何；空布局显示空态文案；加载失败显示错误提示（i18n key 全量双语）。
- Core flow and reversibility: 纯前端新增组件 + 一处接线 + i18n 4 key；可回退。
- Inputs/outputs: 输入=`loadTreeLayout(topicId, currentBranchId)`（store 新函数）；输出=SVG 渲染 + `onNavigate`。
- State and exception handling: 加载中/失败/空三态；fetch 失败 → `workspace.tree.loadFailed`。
- Constraints and trade-offs: 全部文案走 i18n（守卫实测抓出 JSX 块注释中文并已修正为英文——守卫行为正确，不放宽）；样式用既有 CSS 变量，不改 design tokens。
- AI recommendation and rationale: 几何零客户端推导（与契约「前端不得自行推几何」一致）；触达区用 `role="button"`+aria-label 保证可测可访问。
- User choice and custom additions: 已批准（第二波开工指令）。
- AI-autonomous decisions: LEAF 用椭圆叶、BUD 用圆苞+数字、名牌含 leader 线与摘要行；未做缩放/平移手势（viewBox 适配即完整可视）。
- User-confirmation decisions: 无。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-046-{test,lint,guard,build}.txt`。
- Facts: 契约 §6.11（labels 带 title/summary）；`BranchWorkspace` 路由惯例 `/topic/:id?branch=:id`；`TranslationKey` 联合类型约束 t()。
- Assumptions: 服务端固定视口 800×1200 下 bounds 有限。
- Unknowns: 无。
- Risks: 缩放/平移手势缺失（Deferred）；jsdom 对 SVG text 的查询依赖 textContent（测试已验证可行）。
- Deferred: 手势缩放、BUD 展开、拖拽画布。
- Non-goals: 不改契约/后端/Android；不新增依赖。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

BranchWorkspace 的 C 布局从占位文案变为真实树图：从 §6.11 端点取几何渲染 SVG（树干/叶/苞/名牌/当前路径加亮），点击触达区导航分支；前端全量守门（test/lint/i18n guard/build）全绿。

## Scope

### In scope
- `web/frontend/src/api/types.ts`：TreeLayout* 类型。
- `web/frontend/src/store/topicData.ts`：`loadTreeLayout`。
- `web/frontend/src/components/TreeCanvasView.tsx`（新）。
- `web/frontend/src/pages/BranchWorkspace.tsx`：C 分支接线。
- `web/frontend/src/i18n/{zh-CN,en-US}.ts`：4 个 `workspace.tree.*` key。
- `web/frontend/src/pages/BranchWorkspace.test.tsx`：C 用例改真实渲染断言 + 空布局用例。

### Out of scope
- 手势缩放/平移；BUD 展开；契约/后端改动。

## Acceptance criteria
- [x] C 视图渲染服务端几何：名牌 title/摘要行、当前路径加亮、BUD 隐藏数。
- [x] 点击触达区导航 `/topic/:id?branch=:id`。
- [x] 空布局空态文案；加载失败错误文案（双语 i18n key）。
- [x] RED→GREEN：旧占位断言先按新行为改写后失败（stub 未配 query 时 2 failed 实录），修复后 174/174 全绿。
- [x] `pnpm test`（174/174）、`pnpm lint`、`check:user-copy-i18n`（selftest+扫描）、`pnpm build` 全 exit 0。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `BranchWorkspace` C 布局由占位文案变为真实树图（用户可见）：SVG 树干/叶/苞/名牌、点击导航 `/topic/:id?branch=:id`。
- Authoritative code/config/migration evidence: `web/frontend/src/components/TreeCanvasView.tsx`、`.../pages/BranchWorkspace.tsx`、`.../store/topicData.ts`、`.../api/types.ts`、`.../i18n/{zh-CN,en-US}.ts`。
- Documentation surfaces checked: 契约（§6.11（T-045 已落稿，labels 含 title/summary 文本））、`web/README.md` 状态节。
- Documentation files updated: 契约与 README 已按本轮交付同步（见下方收口说明）。
- Producer/consumer/sibling/compatibility paths checked: `i18n` 双语 key 成对新增；`IconButton`/`GlassPanel` 复用未改；`platform/router` 复用未改。
- Superseded phrases/examples reverse-searched: 无（组件此前不存在）。



## TDD execution record

### RED — failing test first
- Test/scenario: `BranchWorkspace layout switching > renders the C tree canvas`（旧断言为占位文案，按新行为改写）+ 空布局用例。
- Command: `cd web/frontend && pnpm test`
- Exit code: 1
- Observed failure: 2 failed / 172 passed——stub 未匹配带 query 的 tree-layout URL 时组件走错误态，`子分支` 文本不存在。
- Failure reason validated: 会话内首轮 `pnpm test` 输出（`Unable to find an element with the text: 子分支`）；修正 stub 匹配后转绿。

### GREEN — minimum implementation
- Implementation: `TreeCanvasView` + `loadTreeLayout` + types + 接线 + i18n。
- Command: `cd web/frontend && pnpm test`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-046-test.txt`（`Test Files 22 passed (22)`、`Tests 174 passed (174)`、`TEST-EXIT=0`）。
- Regression evidence: 同文件（全量 22 文件含既有 173 例 + 新增空布局用例）。

### REFACTOR — optional
- Cleanup: error state 类型收窄为 `TranslationKey`（TS2345 修复）；JSX 块注释改英文（i18n 守卫正确拦截中文注释）。
- Regression evidence: `artifacts/evidence/T-2026-10-10-046-{lint,build}.txt`（LINT-EXIT=0、BUILD-EXIT=0）。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行 ≈200，处于软上限边界；仅软预算警告）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- fetch stub 教训：`loadTreeLayout` 带 `?currentBranchId=`，stub 必须用 `startsWith` 而非全等。
- i18n 守卫会拦截 JSX `{/* 中文 */}` 块注释——组件内注释一律英文或 `//` 行注释置于 JSX 外。
- SVG `viewBox` 直接用 `bounds`（服务端已含土壤带与触达垫）。

## Verification report

- Verdict: PASS（实现者自检全绿；独立 verifier 终验待下一轮统一复核）
- Evidence: `artifacts/evidence/T-2026-10-10-046-{test,lint,guard,build}.txt` 全 exit 0。
- Changed/deleted assertion rationale and equivalent behavior coverage: 旧「占位文案」断言按功能落地改写为真实渲染断言（该测试的覆盖从「占位存在」升级为「端到端渲染+导航」）；其余 172 例零改动全绿。
- Gaps: 手势缩放/平移未做（Deferred）。
- Risks: 视觉效果未经真实浏览器截图复核（属下一轮浏览器视觉复核范围）。

## Handoff notes

- Completed: 组件/类型/store/接线/i18n/测试 + 四项守门全绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: 第二波剩余——已读/阅读位置前端消费、来源定位、选文引用、高亮和批注。
- Watch-outs: labels 只含 LEAF（LIMB/BUD 无名牌）；`branch_summary` payload 是 JSON。
