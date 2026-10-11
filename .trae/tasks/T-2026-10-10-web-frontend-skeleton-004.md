# Treelearn Harness Task: Web 前端工程骨架与 i18n 基建

id: T-2026-10-10-004
title: Web 前端工程骨架与 i18n 基建
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 200
shared_code_touched: false
depends_on: [T-2026-10-10-web-runtime-feasibility-002.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 知芽本地 Web 版（父需求 T-2026-10-10-001）之「运行边界与共享构件」阶段：建立 `web/frontend` React+TS+Vite 骨架与 i18n 基建。
- Problem/background: 前端规则要求所有用户可见文案走 i18n（zh-CN/en-US 渲染时解析）、依赖方向 pages→layouts→components→hooks→store→api→platform→utils；骨架即建立该边界与守卫。
- Users/scenarios: 开发者与最终用户；本任务交付可启动的空壳与质量基建。
- Expected outcome: `web/frontend` Vite+React+TS 工程、pnpm 锁文件、AppShell 空壳、i18n 基建（双语字典 + 渲染时解析）、`check:user-copy-i18n` 守卫脚本（自研，含正反自检）、Vitest + Testing Library 测试通过、dev server 启动证据、`/api` 代理到 127.0.0.1:8733。
- Core flow and reversibility: `pnpm dev` → 页面可开；`pnpm build` 产出静态产物；`pnpm test` 绿；`pnpm check:user-copy-i18n` 绿；无业务逻辑。
- Inputs/outputs: 输入为命令；输出为构建产物与测试输出。
- State and exception handling: 硬编码文案被守卫拒绝；i18n 缺 key 在开发期报错可见。
- Constraints and trade-offs: 不引入运行时 CDN；依赖版本以 T-002 实测为准；主题与视觉细节由 T-006 承担，本任务只留样式基座。
- AI recommendation and rationale: 自研轻量 i18n（无第三方依赖）减少攻击面；守卫脚本从第一天强制双语，避免后期改造。
- User choice and custom additions: 用户确认方案；补充 icon-only 按钮与毛玻璃由后续 T-006 落实。
- AI-autonomous decisions: 目录结构、脚本实现、测试工具选型。
- User-confirmation decisions: 无。
- Acceptance evidence: RED/GREEN、构建与启动证据、守卫自检输出。
- Facts: Node 22.22 / pnpm 11.5 可用；后端端口 8733（T-002 确认）。
- Assumptions: npm registry 可达（T-002 实测）。
- Unknowns: 无阻塞性未知。
- Risks: Vite 首装耗时；用锁文件固定版本。
- Deferred: 视觉设计系统、页面实现（后续任务）。
- Non-goals: 业务页面、SSE 客户端接线、状态管理、路由。

## Goal

`web/frontend` 以 `pnpm dev` 真实启动并可访问空壳页面；`pnpm test` 与 `pnpm check:user-copy-i18n` 通过；`pnpm build` 产出静态产物；i18n 键在渲染时解析且双语键完整。

## Scope

### In scope
- Vite + React + TS 工程与目录边界（`src/{pages,layouts,components,hooks,store,api,platform,utils,i18n,styles}`）。
- i18n 基建：`zh-CN`/`en-US` 字典、`useI18n`/`t()` 渲染时解析、缺 key 可见报错。
- `scripts/check-user-copy-i18n.mjs`：扫描 `src/**` 生产代码中的硬编码用户可见文案（CJK 与英文 UI 字面量），带正反自检模式。
- AppShell 空壳（侧栏/主区占位、语言切换演示）+ 组件测试（渲染 + 双语解析）。
- `vite.config.ts` dev 代理 `/api` → `127.0.0.1:8733`。
- `web/README.md` 前端段落雏形；`web/scripts` 下 dev 启动脚本（可选）。

### Out of scope
- 视觉 token/毛玻璃/图标（T-006）；业务页面与数据流；SSE。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增独立前端工程，不影响 Android 与后端。
- Producer/consumer/sibling entrypoints and platform branches: 新消费者；无既有分支。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason: 新增工程。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新增前端 dev/build 命令与端口代理。
- Authoritative code/config/migration evidence: `web/frontend/**`。
- Documentation surfaces checked: `web/README.md`（新建）。
- Documentation files updated: `web/README.md` 前端段落。
- Producer/consumer/sibling/compatibility paths checked: 后端端口一致性（8733）。
- Superseded phrases/examples reverse-searched: 旧方案 `frontend/` 路径由 T-007 反向检索。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: N/A。

## Acceptance criteria

- [x] `pnpm test` 通过：4 tests（AppShell 双语渲染 + 渲染时切换 + i18n 键集一致 + 按语言解析）；RED 先行证据（2 失败、exit 1）。
- [x] `pnpm check:user-copy-i18n` PASS（1 OK 行、3 生产文件）且 `-- --selftest` PASS（6 例：含 CJK 字面量、JSX 文本、aria-label 字面量三类注入必须转红）。
- [x] `pnpm dev` 真实启动：页面 200、含 root 容器（修正转义后的复核）与入口脚本；`pnpm build` 产出 `dist/index.html`（exit 0）。
- [x] dev 代理联调：`http://127.0.0.1:5173/api/health/ready` 经代理返回 200 与后端 JSON（真实同源链路）。
- [x] 依赖以锁文件固定（`pnpm-lock.yaml` 入库存档）；`src/` 与 `index.html` 无任何外部 `http(s)://` 引用（无运行时 CDN）。
- [x] lint：`pnpm lint`（ESLint 10 + typescript-eslint 8 + 目录依赖方向规则）exit 0；生产代码实测 121 行非空非注释（src 除 setupTests 与测试）。
- [x] 停止：vite 监听进程停止后 5173 端口释放；后端经 stop 脚本停止、8733 释放（startup 证据）。

## TDD execution record

### RED — failing test first
- Test/scenario: `AppShell.test.tsx`（双语渲染与渲染时切换）；实现前 AppShell 为 `return null` stub
- Command: pnpm test
- Exit code: 1
- Observed failure: `src/layouts/AppShell.test.tsx (2 tests | 2 failed)`（找不到 `知芽` 与占位文案）；同次 `i18n.test.ts` 2 tests 通过（证据 `T-2026-10-10-004-red.txt`）
- Failure reason validated: 失败确由 shell 未渲染导致；实现后同一命令 4/4 通过

### GREEN — minimum implementation
- Implementation: AppShell（侧栏/标题/语言切换/内容占位，全部经 `t()`）、I18nProvider、双语字典与键奇偶校验、ESLint 依赖方向、i18n 守卫脚本（含自检）
- Command: pnpm test
- Exit code: 0
- Passing evidence: `Test Files 2 passed (2)`、`Tests 4 passed (4)`（`T-2026-10-10-004-green.txt`）
- Regression evidence: ① `pnpm lint` exit 0；② i18n 守卫 PASS + 自检 6 例 PASS；③ `pnpm build` exit 0 且 `dist/index.html` 存在；④ dev+backend 联调：页面 200、代理 `/api/health/ready` 200（`T-2026-10-10-004-startup.txt`）

### REFACTOR — optional
- Cleanup: 启动的 vite 与后端进程均停止、端口释放；证据中的转义误报已就地修正并注明。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 实测锁定（pnpm 11.5.2）：react/react-dom 19.3.0、@types/react(-dom) 19.3.0、typescript 5.9.3、vite 8.3.4、
  @vitejs/plugin-react 6.1.2、vitest 5.0.3、@testing-library/react 16.3.3、@testing-library/dom 10.4.2、jsdom 30.1.2、
  eslint 10.12.0、typescript-eslint 8.71.1；全部精确版本写入 `pnpm-lock.yaml`。
- 依赖方向由 `eslint.config.js` 的 `no-restricted-imports` 按层（pages→layouts→components→hooks→store→api→platform→utils）机械拦截反向 import。
- i18n 守卫为行级预过滤器（CJK/JSX 文本/用户可见 prop 字面量三类 + `i18n-ignore` 抑制），自检 6 例；诚实标注其非解析器属性。
- dev server 显式绑定 `127.0.0.1`（与 loopback 原则一致、避免 localhost 的 IPv6 解析歧义）。
- 视觉与图标（毛玻璃、icon-only 按钮）在 T-2026-10-10-006 交付；本任务 AppShell 仅骨架样式。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10；只读核对 + 独立复跑 + ESLint API 实测）
- Evidence: verifier 独立复跑 `pnpm test`（4 passed）、`pnpm lint`（exit 0）、t-copy 守卫（PASS + 自检 6 例）、`pnpm build`（dist 存在）、dev+后端代理联调（页面 200 含 root 容器、`/api/health/ready` 200）；依赖方向用 ESLint API `lintText` 实测：相对/别名/`import type` 反向 import 均被拦截，同层与向下 import 无误报；锁文件精确版本；生产代码 121 行与声明一致。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A（新增任务）
- Gaps: ① 守卫为行级预过滤器（跨 `{}` 拼接 JSX 文本、单引号/模板 prop 字面量不在覆盖内）——已在守卫文档中诚实标注；② 动态 `import()` 可绕过层规则——**已处置**：T-006 在 `eslint.config.js` 为层目录加 `no-restricted-syntax: ImportExpression`；③ 骨架样式为临时值——**已处置**：T-006 替换为设计系统。
- Risks: 语言未持久化（刷新回 zh-CN，设计允许，后续任务按需接入偏好持久化）；`<html lang>` 同步缺陷由 T-006 浏览器验证发现并修复（已补断言）。

## Handoff notes

- Completed: Vite+React+TS 工程、i18n 基建（双语字典/渲染时解析/键集测试）、AppShell 空壳、ESLint 依赖方向、i18n 守卫（含自检）、锁文件、dev 代理联调与构建证据；web/README 前端段落；独立 verifier PASS；两条 verifier 建议已在 T-006 落地。
- In progress: 待与 T-006 一并落库。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-007 文档同步；Phase 3 契约接入 `api/` 层。
- Watch-outs: 前端文案必须走 `t()`（守卫，已接入 lint/build/dev 入口）；新目录层不得反向 import（lint 拦截，含动态 import）；`pnpm run check:user-copy-i18n -- --selftest` 需 `--` 分隔参数。