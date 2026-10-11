# Treelearn Harness Task: Web 后端工程骨架与最小 ready

id: T-2026-10-10-003
title: Web 后端工程骨架与最小 ready
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
depends_on: [T-2026-10-10-web-runtime-feasibility-002.md, T-2026-10-10-shared-module-relocation-005.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 知芽本地 Web 版（父需求 T-2026-10-10-001）之「运行边界与共享构件」阶段：建立 `web/backend` 最小可启动骨架。
- Problem/background: 发行形态要求「电脑一条启动入口打开 localhost，网页和本机业务服务同源运行」；骨架先证明真实 ready 与共享构件消费，不先搭空壳业务。
- Users/scenarios: 开发者与最终用户；本任务只交付最小存活能力与真实启动证据。
- Expected outcome: `web/backend` 独立 Gradle 构建（wrapper 与 android 同版本）、Spring Boot 应用绑定 127.0.0.1、`/api/health/ready` 返回稳定 JSON、静态托管占位、消费 `web/shared` 中 `:core`/`:tree-layout` 编译期依赖、启动与停止脚本、真实启动证据。
- Core flow and reversibility: `gradlew bootRun` 或 `java -jar` → started 日志 → `curl /api/health/ready` 200；Ctrl+C/停止脚本干净退出；无数据副作用。
- Inputs/outputs: 输入为构建命令；输出为 ready JSON 与日志；错误（端口冲突）给出可读诊断。
- State and exception handling: 端口被占用时明确报错退出，不静默换端口。
- Constraints and trade-offs: 只绑定 loopback；依赖版本用 T-002 实测结论；不实现业务端点。
- AI recommendation and rationale: 静态资源目录先占位，Phase 4/5 替换为前端产物；健康端点含协议版本字段供契约阶段扩展。
- User choice and custom additions: 用户确认方案与 Spring Boot 路线。
- AI-autonomous decisions: 包名 `com.treelearn.web`、端口 8733（T-002 确认）、构建细节。
- User-confirmation decisions: 无。
- Acceptance evidence: RED/GREEN 记录、真实启动证据文件、lint。
- Facts: T-002 将给出 Spring Boot 精确版本；构建消费 `web/shared/domain`（T-005 迁移后）。
- Assumptions: T-002 依赖解析成功。
- Unknowns: 无阻塞性未知。
- Risks: Spring Boot 首下载耗时；用后台运行与超时控制。
- Deferred: 业务 API/SSE（Phase 3+）。
- Non-goals: 数据库、凭据、业务端点、前端构建集成。

## Goal

`web/backend` 可在 Windows 本机以一条命令真实启动，绑定 127.0.0.1:8733，`GET /api/health/ready` 返回 200 与稳定 JSON，且编译期消费 `web/shared` 共享构件。

## Scope

### In scope
- `web/backend` Gradle 工程（wrapper、settings 引用 `web/shared/domain`、`web/shared/tree-layout`）。
- 最小 Spring Boot 应用：loopback 绑定、ready 端点、静态目录占位、端口冲突诊断。
- `web/scripts/start-backend.ps1`、`stop-backend.ps1`（或等价脚本）与 `web/README.md` 启动段落雏形。
- 单测：ready 端点 200 + JSON 字段；共享构件类在 classpath（消费证明）。
- 真实启动证据落 `artifacts/evidence/`。

### Out of scope
- 业务 API/SSE、SQLite、凭据、附件、Host/Origin 强化（后续任务）。
- 前端产物集成（占位即可）。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增独立本机服务，不影响 Android 与仓库既有模块。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 新消费者 `web/backend` → `web/shared/domain`、`web/shared/tree-layout`。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason: 新增工程，无既有行为变更。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新增本机服务入口、端口 8733、ready 端点。
- Authoritative code/config/migration evidence: `web/backend/**`。
- Documentation surfaces checked: `web/README.md`（新建）；根 README 与规格由 T-007 同步。
- Documentation files updated: `web/README.md` 启动段落。
- Producer/consumer/sibling/compatibility paths checked: android 构建不受影响（T-005 验证）。
- Superseded phrases/examples reverse-searched: 旧方案根级 `backend/` 路径在 T-007 反向检索。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史材料。

## Acceptance criteria

- [x] `web/backend` 下 `.\gradlew.bat test`：ready 端点测试（真实随机端口 HTTP）与共享构件消费测试通过（`T-2026-10-10-003-green.txt`：BUILD SUCCESSFUL、2 测试；同一构建内共享模块 `:core`/`:tree-layout` 测试亦运行通过）。
- [x] 真实启动：`gradlew bootRun` 日志 `Tomcat started on port 8733` + `Started WebApplication in 1.164 seconds`；`GET /api/health/ready` → 200 `{"service":"treelearn-web-backend","status":"ready"}`；`GET /` 占位页 200；停止脚本 exit 0 且端口在停止后空闲（`T-2026-10-10-003-startup.txt`）。
- [x] 端口被占用时明确诊断：`APPLICATION FAILED TO START` + `Web server failed to start. Port 8733 was already in use.` + 指引；无静默换端口（`T-2026-10-10-003-port-conflict.txt`）。
- [x] `GET /` 返回占位页（静态托管通道可用；产物为空壳，正式前端产物在后续任务替换）。
- [x] 无任何 Key/密钥写入代码或日志（代码仅 2 个生产类；日志证据仅框架启动行）；绑定地址实测仅 `127.0.0.1`（`Get-NetTCPConnection` 记录）。
- [x] lint：checkstyle 14.3.0（main/test 双源集）通过，含一次违规修复（4 个文件缺行尾换行 → 修复后 `check` exit 0，`T-2026-10-10-003-lint.txt`）；生产代码实测 **24 行非空非注释**（WebApplication 9 + ReadyController 15；物理 45 行），远低于预算。

## TDD execution record

### RED — failing test first
- Test/scenario: `ReadyEndpointTest`（真实 HTTP：期望 `/api/health/ready` 200 + `"status":"ready"`；控制器实现前请求得到 404）
- Command: .\gradlew.bat test --console=plain
- Exit code: 1
- Observed failure: `ReadyEndpointTest ... FAILED: body was: {"timestamp":...,"status":404,...,"path":"/api/health/ready"} ==> expected: <200> but was: <404>`（证据 `T-2026-10-10-003-red.txt`，首次运行含依赖下载 1m53s）；同次 `SharedClasspathTest PASSED`（共享构件已可达）
- Failure reason validated: 失败由 endpoint 缺失（404）导致；补 `ReadyController` 后同一命令转绿

### GREEN — minimum implementation
- Implementation: `WebApplication`、`api/ReadyController`、`application.properties`（loopback 8733）、`static/index.html` 占位、两个测试、checkstyle 配置与 `start/stop-backend.ps1`
- Command: .\gradlew.bat test --console=plain
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL（9s）；`:test` 全绿；同一构建运行内共享模块 `:core`/`:tree-layout` 测试通过（`T-2026-10-10-003-green.txt`）
- Regression evidence: ① checkstyle `check` → BUILD SUCCESSFUL / EXITCODE_CHECK2=0（lint.txt；含违规修复记录）；② 真实启动/停止（startup.txt：READY 4s、仅 127.0.0.1 监听、ready 200、静态页 200、stop exit 0、端口释放、Gradle 进程退出）；③ 端口冲突（port-conflict.txt）；④ 共享结构守卫（`T-2026-10-10-005` 复跑：6 OK，含 web/backend 引用）

### REFACTOR — optional
- Cleanup: 临时端口占用探针已停止；bootRun 进程在停止脚本后退出。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 包名 `com.treelearn.web`；Spring Boot **4.1.1**（T-002 实测锁定；使用 `spring-boot-starter-webmvc`，非弃用的 web starter）、checkstyle **14.3.0**（`maven-metadata.xml` 实读最新稳定；搜索索引显示的 10.26.1 为过期数据）、Gradle wrapper 8.14.3（复制自 android）。
- `settings.gradle` 以 projectDir 引用 `../shared/domain`、`../shared/tree-layout`；web 构建内共享模块测试随 `test` 一同运行。
- 静态托管：`src/main/resources/static/index.html` 为占位；正式前端产物在发行任务中替换本目录内容。
- checkstyle 曾报 4 个文件「文件未以空行结尾」（写入工具未保留行尾换行），修复后 exit 0；后续文件写入需保证末尾换行。
- ready 契约字段（`service`/`status`）为暂定值，唯一权威契约为 `web/contracts`（Phase 3）；在此之前保持稳定。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10；只读核对 + 独立复现启动/停止/端口冲突/clean test）
- Evidence: verifier 独立 `clean test`（15 tasks executed，ReadyEndpointTest/SharedClasspathTest + 共享模块测试全绿）、`check --rerun-tasks`（checkstyleMain/Test 执行、exit 0）、独立启动（`Started WebApplication in 1.204s`、ready 200 `{"status":"ready","service":"treelearn-web-backend"}`、仅 127.0.0.1 监听、`GET /` 200 含占位文案）、独立停止（PID 停止、端口释放、空闲态幂等 exit 0）；端口冲突证据 + 4 个 Java 文件行尾换行抽查；生产代码非空非注释 24 行与声明一致。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A（新增任务）
- Gaps: 无阻断项。残余风险（verifier）：① ready JSON 的键序不稳定（`Map.of` 无序；证据文件与实测键序不同）→ 由 Phase 3 `web/contracts` 固化字段与顺序时处理；② 端点暂无 contract-guardian 复核与权威契约来源（范围内 deferred，Phase 3）；③ `web/shared/*/` 下有历史忽略目录 `build-*`（随目录迁移移动、不入库，非本任务产物）。
- Risks: 同 Gaps ①（对严格依赖键序的消费者存在隐患）。

## Handoff notes

- Completed: web/backend 骨架（Spring Boot 4.1.1、loopback 8733）、ready 端点、静态占位、两枚测试、checkstyle、启动/停止脚本、web/README 启动段落；RED→GREEN→真实启动/停止/端口冲突证据齐备；独立 verifier PASS。
- In progress: 待落库提交。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: Phase 3 契约任务固化 ready 字段与键序；T-004 前端骨架进行中（dev 代理指向 127.0.0.1:8733）。
- Watch-outs: 端口 8733 由 stop 脚本按监听进程停止；gradlew 需在 `web/backend` 目录运行；checkstyle 要求行尾换行。