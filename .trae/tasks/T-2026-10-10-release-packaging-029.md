# Treelearn Harness Task: 发行入口——静态托管、单命令启停与数据目录

id: T-2026-10-10-029
title: 发行入口——静态托管、单命令启停与数据目录
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 200
shared_code_touched: false
depends_on: [T-2026-10-10-core-rest-api-028.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 用户要求「启动电脑本机服务后通过 localhost 在浏览器使用」+ 真实启停方式与发行产物；开发口已有 start/stop-backend.ps1（dev）。需要发行口：构建前端 → 由后端静态托管（同源）→ 单命令启动 → 浏览器只访问 `http://127.0.0.1:8733`。
- Users/scenarios: 单人本地使用；双击/单命令启动与停止；数据目录独立于仓库。
- Expected outcome: 后端托管 `web/frontend/dist`（静态资源 + SPA 回退到 index.html，`/api/**` 除外）；`web/scripts/start-web.ps1`（构建前端产物（如缺）→ 启动后端 → 等待 ready → 打印访问地址与数据目录）与 `stop-web.ps1`；README 发行口章节；发行产物=可复制目录（backend jar + 前端 dist）说明。
- Core flow and reversibility: 构建 → 启动 → ready → 浏览器使用 → 停止；数据目录保留。
- Inputs/outputs: 输入=仓库 checkout 与 JDK/Node；输出=运行中的本机服务与静态页面。
- State and exception handling: 端口占用→明确诊断；前端产物缺失→脚本自动构建或提示；数据目录不可写→启动失败信息。
- Constraints and trade-offs: 不引入打包器（用 Spring Boot 静态资源 + `bootJar`）；不新增依赖；不改变开发口（dev 代理仍可用）。
- AI recommendation and rationale: `ResourceHandler` 配置 + SPA fallback 控制器（仅当路径非 `/api/**` 且非静态资源时转发 index.html）；脚本幂等。
- User choice and custom additions: 用户要求「真实 localhost 启动和停止方式、数据目录、发行产物」。
- AI-autonomous decisions: 脚本形态、产物目录（如 `web/dist/` 或 `web/frontend/dist` 原样托管）。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-029-*`：真实启动（发行口）→ `GET /` 返回 SPA、`/api/health/ready` ready、`/api/topics` 正常、未知前端路由回退 index；停止后端口释放；数据目录内容；jar 产物路径与大小。
- Facts: 后端 ready 端点与端口 8733；`web/frontend/dist` 由 `pnpm build` 产出；现有 dev 脚本。
- Assumptions: 浏览器只需同源访问，无需 CORS。
- Unknowns: 静态托管与 SSE 端点共存（同一服务）——以实测证明。
- Risks: SPA 回退误吞 API 路由 → 测试断言 `/api/**` 不被回退；静态资源缓存头（本地场景从简）。
- Deferred: `jpackage`/附 JDK 的发行包（记录为可选演进）。
- Non-goals: 公网/局域网访问；自动更新。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付发行入口：静态托管 + 单命令启停 + 数据目录说明，以真实启动/停止证据证明用户可用。

## Scope

### In scope
- 后端：静态资源托管 `classpath:/static/` 之外的 `web/frontend/dist`（开发时文件系统路径，允许配置 `treelearn.frontend-dist`，缺省探测相对路径）+ SPA 回退（`/api/**`、已知静态资源除外；404 API 仍返回错误封装）。
- `web/scripts/start-web.ps1`（缺 dist 时提示可选 `-Build`；等待 ready；打印地址/数据目录）、`stop-web.ps1`（幂等）。
- `web/README.md`：发行口章节（启动/停止/数据目录/发行产物/浏览器地址）。
- 测试：SPA 回退（`/`、`/library`、`/topic/x` → index.html 内容；`/api/unknown` → 404 封装；`/api/health/ready` 正常）；真实启动/停止证据。
- 发行产物：`gradlew bootJar` 产物路径与前端 dist 组合说明（真实文件证据）。

### Out of scope
- 附带 JDK 的安装包；网络访问；自动更新。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: `/api/**` 行为不变（回退不得吞 API）；ready 不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 前端 build 产物；dev 代理不变。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 发行口=单命令启动同源服务（8733）与浏览器访问地址；数据目录 `%LOCALAPPDATA%\TreeLearn-Web`。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/**`（静态配置）、`web/scripts/*-web.ps1`。
- Documentation surfaces checked: `web/README.md`（发行口/数据目录）、开发方案 §1（两套入口）。
- Documentation files updated: `web/README.md` 发行口章节（完整）。
- Producer/consumer/sibling/compatibility paths checked: dev 脚本与代理。
- Superseded phrases/examples reverse-searched: 「发行入口……在发行任务完成后补充」。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `:test`，记录退出码与计数）；业务行 ≤250。
- [ ] SPA 回退：前端路由（含深层 `/topic/:id`）返回 index.html；`/api/**` 不被回退（未知 API→404 封装）；静态资源正常。
- [ ] 真实发行口启动：`start-web.ps1` 后 `GET /` 返回 SPA、`/api/health/ready` ready、`/api/topics` 可访问；`stop-web.ps1` 后端口释放（幂等）。
- [ ] 数据目录与发行产物证据（jar 路径/大小、dist 路径）。
- [ ] README 发行口章节完整（启动/停止/数据目录/产物/地址）。
- [ ] checkstyle 通过；独立 verifier PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `SpaFallbackTest`（MockMvc）。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 1
- Observed failure: 见证据
- Failure reason validated: 缺失回退与静态配置

### GREEN — minimum implementation
- Implementation: 静态托管 + 回退 + 脚本。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 0
- Passing evidence: 见证据
- Regression evidence: 既有全绿

### REFACTOR — optional
- Cleanup: 按检查整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 回退实现必须显式排除 `/api/**`（含 SSE 端点），并有测试。
- 脚本用 `$env:JAVA_HOME` 或仓库 gradle wrapper；打印的地址/数据目录须与 README 一致。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10）+ PARTIAL→PASS（contract-guardian：三处缺口修复后闭合）
- Evidence: verifier 自跑 `:test --rerun-tasks` 192/0、`check` EXIT=0；**独立真实发行口验收**：`pnpm build` → `start-web.ps1` → `/` 200 text/html、`/library`/`/topic/{uuid}` 200、`/assets/*.js` 200、`/api/health/ready` 200、`/api/topics` 200、`/api/does-not-exist` 404+§3.1 封装 → `stop-web.ps1` 后 `Get-NetTCPConnection -LocalPort 8733` 零监听、二次 stop 幂等。
- 产物证据：`web/backend/build/libs/treelearn-web-backend-0.1.0.jar` 34,282,890 B（≈32.7 MiB）；`web/frontend/dist/`（index 403 B + assets 275,846 B）。
- 静态隔离：`/api/**`（含 SSE 路径）显式短路不回退；`RequestMappingHandlerMapping`（order 0）优先于资源处理器；`add-mappings=false` 影响面经 verifier 判定为「仅移除 Boot 默认 classpath 映射，无其它静态资源/端点受影响」。
- 缺口修复（guardian 三项）：① `isApiPath` 改大小写不敏感 + `CoreApiExceptionHandler` 捕获 `NoResourceFoundException` → `NOT_FOUND(resource=endpoint)`（`/API/foo`、`/Api/nothing` → 404 封装；RED 195 跑 2 败 → GREEN 195 全绿）；② 契约 §3.3 `NOT_FOUND.resource` 追加 `endpoint`；③ §11 新增「通用 `ANY /api/**`（未声明路径）」行。
- 已知限制：真实 HTTP 复验大小写修复需重启服务（运行中的旧进程仍返回 200）——已记录，由后续统一重启复核。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；+13 用例（182→195）。
- 规模：verifier 独立计数 233（≤250 自定目标、≤500 硬上限）。
- Gaps: 浏览器端复验（已由后续浏览器验收任务覆盖）；`/settings`、`/library` 为占位页（属 T-028b 范围）。
- Risks: 静态缓存头从简（任务已声明）；SPA 回退与 API 隔离依赖 resolver 短路（有测试）。

## Handoff notes

- Completed: 静态托管 + SPA 回退 + 单命令启停 + 发行产物 + README 发行口章节 + guardian 三项缺口修复；195 项测试全绿；真实发行口启停与端口释放证据齐备。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: 浏览器验收发现的视觉缺陷修复（毛玻璃模糊、侧栏溢出）与 `/api/settings/model-resolution` 端点；随后终版交付报告。
- Watch-outs: `/api/**` 不得被回退（含大小写变体）；running 服务需重启才反映大小写修复。