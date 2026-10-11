# 知芽（TreeLearn）Web 本地版

本目录集中承载 Web 产品：React + TypeScript 客户端与本机 Java 服务。数据与手机版（`android/`）相互独立，
不建设同步、账户、公网/局域网访问或云存储。

- 服务只绑定 loopback（`127.0.0.1:8733`），浏览器通过 localhost 访问。
- 共享业务判定唯一实现：纯 Java 模块位于 `web/shared/`（`domain`、`tree-layout`、`ai-protocol`），
  Android 与 Web 两个构建都以 projectDir 引用同一份源码，禁止拷贝。

## 目录结构

```text
web/
  backend/   本机 Java/Spring Boot 服务（HTTP/SSE、静态托管）
  frontend/  React + TypeScript + Vite 客户端（进行中）
  shared/    两端共享的纯 Java 构件
  contracts/ 唯一 API/SSE 契约：`api.md`（规范正文）+ `fixtures/`（双端共享样例）
  scripts/   开发、构建、启动与守卫脚本
  README.md  本文件
```

## 开发启动与停止（当前可用部分）

后端（开发口）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File web/scripts/start-backend.ps1
```

- 就绪信号：日志出现 `Started WebApplication`，且
  `http://127.0.0.1:8733/api/health/ready` 返回 `{"service":"treelearn-web-backend","status":"ready"}`。
- 端口冲突时服务会明确报 `Port 8733 was already in use` 并退出，不会静默换端口。

停止后端：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File web/scripts/stop-backend.ps1
```

- 该脚本按 127.0.0.1:8733 的监听进程停止服务；端口已空闲时为幂等成功（exit 0）。

前端（开发口）：

```powershell
cd web/frontend
pnpm install   # 首次
pnpm dev       # http://127.0.0.1:5173（/api 代理到 http://127.0.0.1:8733）
```

- 质量命令：`pnpm test`（Vitest + Testing Library）、`pnpm lint`（ESLint + 目录依赖方向规则 + 用户文案 i18n 守卫）、
  `pnpm check:design`（= `check:design-tokens` token 漂移守卫 + `check:contrast` 对比度守卫 + i18n 守卫；
  各自的 `-- --selftest` 跑正反自检）、`pnpm build`（守卫 + tsc + vite，产物 `dist/`；`pnpm dev` 与 `pnpm build` 都会先跑 `check:design`）。
- 依赖版本由 `pnpm-lock.yaml` 固定；无运行时 CDN 引用。图标使用 `lucide-react@1.55.0`（ISC；Feather 派生图标 MIT，
  许可见 `web/frontend/THIRD_PARTY_NOTICES.md`），只允许经 `src/components/icons.ts` 注册表引用，统一 `strokeWidth 1.8`。
  统一 SSE 客户端使用 `@microsoft/fetch-event-source@2.0.1`（MIT，同上许可文件），不使用原生 `EventSource`。

## 发行入口（单命令启动与静态打包）

发行口由后端**同源**托管前端构建产物，浏览器只访问 `http://127.0.0.1:8733`（契约 §10）。

构建并启动（前端产物缺失时加 `-Build` 先生成）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File web/scripts/start-web.ps1          # 直接启动
powershell -NoProfile -ExecutionPolicy Bypass -File web/scripts/start-web.ps1 -Build   # 先 pnpm build 再启动
```

- 脚本先构建后端 `bootJar`，再以 `java -jar` 后台启动本机服务（`127.0.0.1:8733`，仅 loopback），
  轮询 `/api/health/ready` 就绪后打印浏览器地址与数据目录；端口被占用时给出明确诊断（exit 2）。
- 就绪后浏览器访问 `http://127.0.0.1:8733` 使用。`/`、`/library`、`/topic/:id`、`/settings/*` 等前端
  路由由后端回退到 `index.html`（SPA history 回退），`/api/**` 命名空间**不回退**（未知接口返回
  404 + §3.1 错误封装）。前端产物不存在时服务仍启动，`/` 返回可读的构建缺失提示。
- 启动可显式指定前端产物位置：`java -jar <jar> --treelearn.frontend-dist=<dist 绝对路径>`；未指定时
  按 `../frontend/dist`、`web/frontend/dist`、`frontend/dist` 相对当前工作目录探测（`treelearn.frontend-dist`）。

停止（幂等，端口空闲时 exit 0）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File web/scripts/stop-web.ps1
```

数据目录（与开发口相同，`TREELEARN_DATA_DIR` / `treelearn.data-dir` 可覆盖）：
`%LOCALAPPDATA%\TreeLearn-Web\`，含 `treelearn.db`、`attachments\`、`logs\`；`start-web.ps1` 的服务
stdout/stderr 落在 `logs\web-stdout.log`、`logs\web-stderr.log`。

发行产物（可复制目录，两份放在同一发行目录即可发布）：

- 后端：`web/backend/build/libs/treelearn-web-backend-0.1.0.jar`（`.\gradlew.bat bootJar` 产出，约 32.7 MiB）。
- 前端：`web/frontend/dist/`（`pnpm build` 产出，含 `index.html` 与 `assets/`）。

开发口与发行口的差异：

| 项 | 开发口 | 发行口 |
|---|---|---|
| 前端 | `pnpm dev`（Vite `127.0.0.1:5173`，`/api` 代理到 8733） | 后端同源托管 `web/frontend/dist` |
| 后端 | `start-backend.ps1`（`gradlew bootRun`，前台） | `start-web.ps1`（构建并 `java -jar`，后台单命令） |
| 浏览器地址 | `http://127.0.0.1:5173` | `http://127.0.0.1:8733` |
| 停止 | `stop-backend.ps1` | `stop-web.ps1`（按端口停止，幂等） |

## 守卫

- `web/scripts/check-shared-modules.ps1`：共享模块结构守卫（位置、两端引用、无副本、共享树无 Kotlin）。
  覆盖 `:core`、`:tree-layout`、`:ai-protocol`；`:ai-protocol` 逐类断言「只在 `web/shared/ai-protocol`、
  不在 `android/ai`」（`android/ai` 合法保留三个平台耦合类）。自检：追加 `-Selftest`（12 例正反校验）。

## 数据目录

本机服务启动时在数据目录创建/迁移 SQLite 权威库（任务 `T-2026-10-10-009`）。

- 默认位置：`%LOCALAPPDATA%\TreeLearn-Web\`，不指向仓库 checkout；库文件为 `treelearn.db`，
  并在同目录创建 `attachments\`、`logs\` 子目录（本任务只建目录，内容写入属后续任务）；模型分工/预算/建议等
  **非敏感**偏好保存在同目录的 `settings.properties`（任务 `T-2026-10-10-036`，对照 Android `SettingsRepository`
  的 SharedPreferences 非表；**不含 API Key**）。
- 覆盖方式：设置环境变量 `TREELEARN_DATA_DIR`，或 Spring 属性 `treelearn.data-dir`
  （环境变量经 Spring relaxed binding 生效）。`LOCALAPPDATA` 不可用时回退 `user.home\AppData\Local`，
  仍不可得则服务启动失败并给出可读信息。
- 版本机制：`PRAGMA user_version`，当前 `user_version=1`（桌面 v1 为首个版本，直接建最终形状，无 ALTER 历史）。
  重开/重跑迁移幂等；库版本高于程序时拒绝启动（失败关闭）；迁移失败整链回滚且不盖版本号。
- 连接：每次打开都启用并读回校验 `PRAGMA foreign_keys=ON`，并设置 `busy_timeout=5000` 与 `journal_mode=WAL`。
- 启动中断落定（任务 `T-2026-10-10-011`）：服务在迁移之后、对外就绪之前，把上次异常退出遗留的活跃生成操作落定为终态
  （`STREAMING_*`/`SUBMITTING`/`VALIDATING` → `INTERRUPTED`，从未提交的 `STAGED` → `CANCELED`），保留已到的部分内容并更新时间；
  重跑幂等；落定失败则拒绝启动（失败关闭）。
- DDL 位置：全部 schema 语句只存在于 `web/backend/src/main/java/com/treelearn/web/storage/DesktopSchema.java`，
  由 `DdlLocationGuardTest` 扫描 `src/main/java` 证明其他生产文件不含 `CREATE TABLE/INDEX/TRIGGER`、`ALTER TABLE`。
- 凭据：API Key 存 Windows 系统凭据管理器（任务 `T-2026-10-10-013`），target 前缀 `Treelearn-Web/provider/<id>`，
  经 `com.treelearn.web.credential.SecretPort` 保存/重读/删除；数据目录与 SQLite 库均不含 Key，无明文兜底路径。

## 状态（2026-10-10）

- 后端骨架与最小 ready 已就绪（任务 `T-2026-10-10-003`）。
- 前端骨架与 i18n 基建已就绪（任务 `T-2026-10-10-004`）：AppShell 空壳、双语字典、渲染时解析、
  文案守卫与依赖方向 lint、`dist/` 构建产物与 dev 代理联调（`/api/health/ready` 经代理返回 200）。
- 设计系统已就绪（任务 `T-2026-10-10-006`）：Web token（薄荷命名空间 + 毛玻璃段）由
  `pnpm generate:tokens` 生成并有漂移守卫与对比度守卫（含合成玻璃底色，7 对全达 AA）；
  毛玻璃基元（GlassPanel/Dialog/Toast）、icon-only `IconButton`、图标注册表与许可；
  真实浏览器截图与会话验证见 `artifacts/evidence/T-2026-10-10-006-*`。
- 唯一 API/SSE 契约已定稿（任务 `T-2026-10-10-014`）：[`contracts/api.md`](contracts/api.md) 为规范正文（端点、错误码词汇表 42 项、SSE 帧协议与缺口恢复、命令幂等、时间字段 `Millis` 规则），`contracts/fixtures/` 为 Java/TypeScript 双端共享样例（104 个 JSON，经 contract-guardian 复核 PASS）。业务功能在后续任务按契约交付；功能矩阵验收在对应阶段逐行补证。
- 前端契约客户端就绪（任务 `T-2026-10-10-015`）：`src/api/` 提供契约 DTO 类型（`types.ts`）、42 错误码与统一 `ApiError` 编解码（`errors.ts`）、同源统一 HTTP 客户端（`client.ts`）与 SSE 客户端/状态机（`sse.ts`）；42 错误码中英双语文案与兜底/网络文案入库；`contracts/fixtures/` 的全部样例（现 104 个）由 `src/api/__tests__/` 全量往返约束漂移。页面/状态层消费属 Phase 5。
- 前端外壳与主页就绪（任务 `T-2026-10-10-030`）：`src/pages/Router` 接 `src/platform/router`（History API + 路径匹配，支持 `/`、`/library`、`/topic/:id`、`/settings/*` 与未知路径 404 占位）；`src/layouts/AppShell` 提供 icon-only 主导航（当前路由高亮、Enter 激活、Escape 回主内容区、双语切换、窄窗 88px 图标栏不隐藏导航）；`src/pages/HomePage` 提供 hero + 首问 composer（思考开关、发送/停止状态机、无可用模型禁用并引导设置、错误码→i18n 文案）与最近主题卡（`GET /api/topics` → 5.1b `TopicSummary`，卡片显示派生标题 `displayTitle`，无标题回退通用文案）；草稿按 `draft:<branchId|home>` 存 localStorage（防抖保存、重挂载恢复、发送成功后清理）。真实联调、工作区 A/B/C 与浏览器视觉复核属后续任务。
- 工作区（A 专注阅读 + B 并排分支）与真实 SSE 流式就绪（任务 `T-2026-10-10-031`）：`/topic/:id` 由占位页替换为 `src/pages/BranchWorkspace`（可选 `?branch=` 指定分支，缺省用 `lastVisitedBranchId` 或根分支）；顶部为根分支标题（=主题标题）、到当前分支的面包屑、A/B/C 布局切换（`localStorage` 记忆）与返回；阅读列渲染已提交消息位（USER 引用块、AI 活跃修订回答与折叠的思考过程；消息正文由任务 `T-2026-10-10-027` 的统一 Markdown 渲染器渲染）；流式区订阅当前活跃操作（`sse.ts` 帧源 + `useOperationStream`，增量即时上屏、终态状态条、停止=POST cancel、失败/中断=原位 retry）；追问 composer 带引用 chip 与 thinking 开关，`POST /api/branches/{id}/messages`；追问草稿按分支键 `draft:<branchId>` 经 `useDraft` 保存（切换分支不丢各分支草稿、重挂载恢复、发送成功后清理）。B 视图并排当前分支与其分叉来源（`Branch.source`），按 `sourceMessageId` 定位来源消息并高亮 `anchor` 区间（未命中显示可读降级），附 `contextSnapshotId` 快照说明。C 树图依赖服务端树布局端点：前端不计算树布局，仅占位说明。SSE 状态由 6 个 `contracts/fixtures/sse/*` 场景驱动 UI 验证（含重连补快照与晚到/重复/外来 operationId 忽略）；真实联调与浏览器视觉复核属后续任务。
- 统一消息渲染器就绪（任务 `T-2026-10-10-027`）：`src/components/markdown/` 提供自写 Markdown 解析器（`parse.ts`，输出块级/行内 AST）与 React 元素渲染（`Markdown.tsx`，**结构上不使用 `dangerouslySetInnerHTML`**，恶意输入只能成为可见文本）；覆盖标题（`#`~`###`）、段落、有序/无序列表、引用、围栏代码（语言标签 + `IconButton` 复制，`navigator.clipboard` 失败降级为 i18n 提示且不抛出）、表格（表头 + 列对齐）、分隔线、行内/块级公式（`$...$`、`$$...$$` 以样式化原文呈现=可读降级）与链接（仅 `http`/`https`，外部链接 `target="_blank"` + `rel="noopener noreferrer"`）；流式容忍未闭合围栏（渲染至文档尾）、未闭合公式（保留原文）与半写表格/缺分隔线（降级为段落）；超长行、行内解析深度与块数量有上限并超出降级为原文。`MessageView` 与工作区流式回答接入该渲染器，引用块、思考折叠与分叉锚点高亮保持。范围外语法（嵌套列表深层、脚注、定义列表、setext 标题、缩进代码块、图片）统一降级为段落/原文。
- 桌面 AI 网关适配就绪（任务 `T-2026-10-10-021`）：`com.treelearn.web.ai` 的 `JacksonChatRequestBody`/`JacksonProviderJson`/`DesktopGatewayFactory` 实现共享 `:ai-protocol` 的 JSON 端口并组装共享 `AiGateway` + `HttpsTransport`；`contracts/fixtures-ai/`（20 份）由 `GatewayAdapterFixturesTest` 全量消费，`StubProviderStreamTest` 以本机 JDK HTTP stub 提供真流式/状态分类/取消/中断证据。生成协调器属后续任务。
- 生成操作 SSE 端点就绪（任务 `T-2026-10-10-026`）：`GET /api/operations/{id}/events`（`text/event-stream`）按契约 §7 先发 `snapshot`（由持久化事实构造，seq 与事件总线一致）再转发增量/终态帧，终态关流；`Last-Event-ID` 缺口或未知先补快照、缓冲内补发缺失帧；心跳为注释行（默认 15s，属性 `treelearn.sse.keepalive-millis` 可调且测试注入短间隔）；未知操作返回 §3.1 错误封装 `404 NOT_FOUND`；客户端断开取消总线订阅（无泄漏）。真实 HTTP 流证据见 `artifacts/evidence/T-2026-10-10-026-*`。
- 核心闭环 REST 端点就绪（任务 `T-2026-10-10-028`）：`com.treelearn.web.api` 新增薄控制器 `CoreApiController`（`GET /api/topics`、`POST /api/topics`、`GET /api/topics/{id}`、`GET /api/topics/{id}/branches`、`GET /api/branches/{id}/messages`、`POST /api/branches/{id}/messages`、`GET /api/operations/{id}`、`GET /api/operations/active?branchId=`、`POST /api/operations/{id}/cancel`、`POST /api/messages/{id}/retry`），集中映射层 `ApiDtoMapper`（域/存储行 → §5 DTO），错误映射 `ErrorCatalog`/`ErrorResponses`/`CoreApiExceptionHandler`（§3.3 码 → §3.2 HTTP 状态 + §3.1 封装），命令装配 `ChatCommandService`（模型/Key 解析、上下文与提示词装配、`commandId` 幂等）；协调器新增公开 `acceptForRetry`。真实 HTTP 端到端（stub 网关）：首问→SSE stable→messages 读修订→追问→cancel→retry 全链路，证据见 `artifacts/evidence/T-2026-10-10-028-*`。
  - 已知缺口：`GET /api/branches/{id}/messages` 的 `attachments` 恒为空数组——附件上传/解析/引用未实现（属后续任务）；`commandId` 幂等已闭合（任务 `T-2026-10-10-035`）：客户端 `commandId` 经共享 `GenerationCommand.createWithId` 成为命令身份并落 `generation_operations.command_id`，`ChatCommandService` 以 `OperationStore.byCommandId` 持久去重，跨重启重放不再二次执行（终态操作重放返回当前状态）；证据见 `artifacts/evidence/T-2026-10-10-035-*`。`GET /api/operations/active?branchId=` 按契约 §6.4 返回 `200`：有活跃操作时为 `Operation`，无则为 JSON 字面量 `null`；`branchId` 缺失/为空/非规范 UUID → `400 REQUEST_INVALID`。
- 发行入口就绪（任务 `T-2026-10-10-029`）：后端同源托管前端构建产物（`com.treelearn.web.hosting`：`FrontendDist` 解析 dist、`StaticHostingConfiguration` 注册 `/**` 资源处理器并做 SPA 回退、`SpaRootController` 服务根路径），非 `/api/**` 路径回退 `index.html`，`/api/**` 由 `UnknownApiController` 兜底为 404 §3.1 封装（绝不回退）；产物缺失不阻断启动（`/` 返回可读提示）。单命令启停脚本 `web/scripts/start-web.ps1`（可选 `-Build`，构建 `bootJar` 后 `java -jar` 后台启动并等待 ready）与 `web/scripts/stop-web.ps1`（幂等）。已移除旧的 classpath 占位页 `backend/src/main/resources/static/index.html`（其注释即声明由本任务取代），并关闭 Boot 默认 `/**` 静态映射（`spring.web.resources.add-mappings=false`）。真实启动/回退/停止证据见 `artifacts/evidence/T-2026-10-10-029-*`。
- 设置回落端点就绪（任务 `T-2026-10-10-034`）：`com.treelearn.web.api` 新增 `SettingsApiController` 的 `GET /api/settings/model-resolution`，恒返回 `200` `ModelResolution`（只含 `roles`，键固定 `CHAT`/`FAST`/`TRANSLATION`，每项 `providerId`/`modelId`/`fallback`）；回落 token 全部来自既有 `GenerationPlanner` 回落链（`NONE`/`BUILTIN`/`EARLIEST`/`EMPTY`），无任何可用模型时为 `EMPTY` 而非 `404`，主页「无可用模型」引导因此闭环。`GenerationPlanner` 新增 `resolveAll`（复用同一回落判定）并注册为 bean。`GET/PUT /api/settings/model-roles` 已由 `T-2026-10-10-036` 实现（经数据目录非敏感偏好文件持久化，见下一条）；桌面十二张准入表仍无分工表，但改用等价非敏感偏好文件、未新增永久表。证据见 `artifacts/evidence/T-2026-10-10-034-*`。
- 设置端点（Provider/Key、模型分工）就绪（任务 `T-2026-10-10-036`）：`com.treelearn.web.api` 新增 `ProviderSettingsController`：`GET /api/providers`、`POST /api/providers`、`PUT /api/providers/{id}`、`DELETE /api/providers/{id}`、`PUT/DELETE /api/providers/{id}/key`（Key 只写不读，任何响应只回 `hasKey`，只经 Windows 凭据管理器 `Treelearn-Web/provider/<id>`）、`POST /api/providers/{id}/models`（拉取供应商 `/models` 并只增不删、精确去重）、`GET/PUT /api/settings/model-roles`（CHAT/FAST/TRANSLATION 分工 + budget + suggestions）。模型分工持久化在数据目录的 `settings.properties`（非敏感偏好文件，对照 Android `SettingsRepository` SharedPreferences，键名与分隔符一致；不新增永久表）；回落判定复用 `GenerationPlanner`。模型拉取经 `ProviderModelCatalog` 复用共享 `AiGateway.listModels`（测试以本机 JDK `HttpServer` 走真 HTTP，不访问外网）。Key 无泄漏以 canary 断言（响应与日志）。证据见 `artifacts/evidence/T-2026-10-10-036-*`。`GET /api/providers/{id}/models`（模型列表读端点）已由 `T-2026-10-10-040` 补齐。
- 前端视觉收口（任务 `T-2026-10-10-033`）：毛玻璃浮层/面板（`GlassPanel` 两态、侧栏、头部、Toast、对话）应用 `backdrop-filter: blur(var(--glass-blur|--glass-blur-raised))` 与 `-webkit-` 前缀，并有 `@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px)))` 降级（不支持时保留半透明填充、描边换成 `--line`，对比度守卫仍过、可读性不退化）；侧栏品牌按桌面断点（`1024px`）自适应——宽窗显示完整名、窄窗 88px 图标栏显示缩写（`TreeLearn→TL`、`知芽→知`），并以 `role="img"` + 完整 `aria-label` 保留可访问名称，中英双语均不裁切不溢出。第 2 轮：页面背景叠三层极淡薄荷/鼠尾草光斑（色值取自 `--bg-*` token）为毛玻璃面板提供可被 `backdrop-filter` 模糊的层次，`--glass-panel` 由 0.72 下调到 0.62 让背后层次透出（合成玻璃底色对比度 14.44:1 仍过 AA，token 漂移守卫仍过）。证据见 `artifacts/evidence/T-2026-10-10-033-*`。
- 前端设置页——Provider/Key 与模型分工就绪（任务 `T-2026-10-10-039`）：`/settings/*` 由占位替换为 `src/layouts/SettingsLayout`（icon-only 分区导航，Enter 激活）+ `src/pages/ProviderEditorPage`（`/settings/providers`：提供商列表（名称/启停/模型数徽标/Key 脱敏状态）、空态引导、新建（重名内联提示 `errors.PROVIDER_NAME_TAKEN`）、编辑、删除确认（含最后提供商警示）、Key 录入（`type=password`、提交后清空内存、不写 localStorage/URL/日志）、拉取模型 `POST /api/providers/{id}/models`）+ `src/pages/ModelRolesPage`（`/settings/model-roles`：CHAT/FAST/TRANSLATION 模型选择与预算 `OFF/AUTO/LOW/MID/HIGH`、建议开关，保存 `PUT /api/settings/model-roles`、并展示 `GET /api/settings/model-resolution` 回落 `NONE/BUILTIN/EARLIEST/EMPTY`），`/settings` 与未知分区回落通用/关于页；主页/工作区「去设置」目标改为 `/settings/providers`。已知缺口：`GET/PUT /api/settings/model-roles` 服务端已由 `T-2026-10-10-036` 实现（数据目录非敏感偏好文件，未新增 schema）；前端原「服务端未实现」的 `404 NOT_FOUND` 提示路径保留为兜底。数据访问集中在 `src/store/settingsData.ts`。证据见 `artifacts/evidence/T-2026-10-10-039-*`。
- 契约缺口收口（任务 `T-2026-10-10-040`）：补齐 `GET /api/providers/{id}/models`（模型列表读端点，未找到 Provider → `404 NOT_FOUND`，`resource=provider`），模型分工页因此可列出已启用 Provider 的模型，闭合 `contract-guardian` 的阻断级缺口；新增响应包裹 fixtures `dtos/deleted-provider.json`、`dtos/models-refreshed.json`（fixtures 总数 102→104、dtos 26→28，Java/TypeScript 双端往返与形状断言同步）；`SettingsApiController`/`ModelRolesPage` 关于 `model-roles`「未实现」的过时注释更正为现状（读写端点由 `T-2026-10-10-036` 提供）；契约 §6.3 补注 `ForkResult.bootstrap.rootBranch` 指新建的子分支。证据见 `artifacts/evidence/T-2026-10-10-040-*`。
- model-resolution 与已保存分工一致（任务 `T-2026-10-10-041`）：`GET /api/settings/model-resolution` 现按用户保存的模型分工解析（此前忽略分工、恒按未配置回落），角色偏好的键名/分隔符与读写抽出为 `api/ModelRoleSettings` 唯一入口，`SettingsApiController` 与 `ProviderSettingsController` 共用同一份事实；回落判定仍唯一走 `GenerationPlanner.resolveAll`。响应形状与契约 §5.12 不变。证据见 `artifacts/evidence/T-2026-10-10-041-*`。
- 本批交付（任务 `T-2026-10-10-036/037/039/040/041`，仅覆盖功能矩阵「Provider/Key、模型分工、模型回落、稳定回答分叉」数行）：设置端点（`ProviderSettingsController`：Provider CRUD、Key 脱敏只写 Windows 凭据 `Treelearn-Web/provider/<id>`、模型拉取合并、模型分工偏好文件 `settings.properties`）；分叉端点（`POST /api/branches/{id}/forks`：整条 + 选文分叉 + 冻结出生快照 + 溯源）；前端设置页（`/settings/*`，icon-only、Key `type=password` 且不落 localStorage/URL/日志）；`model-resolution` 读取已保存分工；契约收口（`GET /api/providers/{id}/models` 补齐，新增 `dtos/deleted-provider.json`、`dtos/models-refreshed.json` 两个 fixtures，总数 102→104）。五项均经独立 verifier PASS；契约面经 contract-guardian 复核。- 分支生命周期与删除端点就绪（任务 `T-2026-10-10-042`）：`POST /api/branches/{id}/{archive,restore,collapse,seen}`、`PUT /api/branches/{id}/reading-position`、`GET /api/branches/{id}/deletion-preview`、`POST /api/branches/{id}/delete`、`GET /api/topics/{id}/deletion-preview`、`POST /api/topics/{id}/delete` 由 404 变为按契约 §6.2/§6.3 可用。生命周期判定唯一收在共享域 `Branch`（根分支不可归档→400 `BRANCH_NOT_ARCHIVABLE`、状态冲突→409 `ILLEGAL_STATE_TRANSITION`、`seen` 只前进→409 `SEQUENCE_OUT_OF_ORDER`），删除范围+digest 收在 `BranchLifecycleService.plan`（`listForTopic` 快照 + BFS、父在子前、fail-closed `SCOPE_UNRESOLVABLE`；SHA-256 digest，过期→409 `DELETION_PLAN_EXPIRED`），删除顺序固定「取消活跃操作（`coordinator.cancel`，重启遗留直接落 CANCELED）→单事务删除（非根 `BranchStore.deleteSubtree` / 根或整树 `TopicStore.deleteCascade`）」，目标已不存在视为幂等成功。归档/折叠/已见/阅读位置/删除无持久 `commandId` 载体（schema 无该列，任务不改 schema）——重放幂等仅对「目标已不存在」成立。真实 HTTP+SQLite 13 例证据见 `artifacts/evidence/T-2026-10-10-042-*`。
- 应用内搜索与学习统计端点就绪（任务 `T-2026-10-10-043`）：`GET /api/search?q=`（七臂 `UNION ALL` 逐臂镜像 Android `SearchDao`：标题/提问/锚点/回答/推理/部分回答/部分推理，归档分支不可搜，全部 `LIKE ? ESCAPE '\'` 参数化，空查询不建 `LIKE '%%'` 直接返回空；排序复用共享 `SearchHit.DISPLAY_ORDER`；命中区间为相对 snippet 的 UTF-16 半开区间）与 `GET /api/stats?timeZone=&month=`（三个原始计数出 DB、自然日归属/连续段/空态镜像 Android `StatsAggregator`「有回答即活跃」口径；当月热力格 7×6 周一为首列，未来整月全 -1、非未来月补位格 0、当月未到日 -1；非法时区/月→400 `REQUEST_INVALID`）。真实 HTTP+SQLite 14 例证据见 `artifacts/evidence/T-2026-10-10-043-*`。
- 前端学习库与学习统计页就绪（任务 `T-2026-10-10-044`）：`/library` 由占位替换为真实学习库页（应用内搜索三范围分组 + 命中区间高亮 + Ctrl/Cmd+K 聚焦、主题列表含未读汇总与设备时区时间、归档区恢复分支），新增 `/stats` 统计页（累计 + 当月热力 + 月份切换，AppShell 统计入口 icon-only）；真实时间点经 `platform/datetime.ts` 按设备时区格式化、date-only 不换算。前端 173/173 测试全绿、`pnpm lint`/`check:user-copy-i18n`/`build` 全过；i18n 守卫修复 `=>`/`<=`/TS 泛型误报并带 3 个自测钉死（`artifacts/evidence/T-2026-10-10-044-*`）。
- C 树图全链路就绪（任务 `T-2026-10-10-045/046`）：契约 §6.11 `GET /api/topics/{id}/tree-layout`（几何全由共享 `:tree-layout` 模块计算，装配镜像 Android `TreeCanvasWiring`：rounds=USER 消息数、summary=branch_summary payload JSON、孤儿行跳过、无根空布局、根 archived fail-closed 500 `STORAGE_REFUSED`、非法 scale 400 `SCALE_INVALID`）+ 前端 `TreeCanvasView`（viewBox 取服务端 bounds、名牌 title/summary 随几何下发、当前路径加亮、BUD 隐藏数、触达区点击导航）。后端 11 例 RED→GREEN + `:check`（275 例+checkstyle）exit 0；前端 174/174 + lint/guard/build 全过（`artifacts/evidence/T-2026-10-10-045-*`、`T-2026-10-10-046-*`）。
- 批注端点就绪（任务 `T-2026-10-10-047`，契约 §6.10）：`GET /api/messages/{id}/annotations`（按显示文本 SHA-256 digest 同事务清理失配行后返回）、`POST /api/messages/{id}/annotations`（HIGHLIGHT 走共享域 `Annotation.mergeHighlights` 相交/相邻合并、NOTE 非空校验、生成中 AI 消息 409 `OPERATION_NOT_STABLE`、区间非法 400 `ANCHOR_INVALID`）、`DELETE /api/annotations/{id}`（幂等）。9 例真实 HTTP+SQLite RED→GREEN，后端全量 `:check` 284 例 + checkstyle exit 0（`artifacts/evidence/T-2026-10-10-047-*`）。
- 已读与阅读位置前端消费就绪（任务 `T-2026-10-10-048`）：进入带未读分支自动 `POST seen`、离开分支保存阅读位置、树图名牌未读徽标（`data-testid=unread-badge-*`）。前端 175/175 + lint/build 全过；RED 回退态复现实录 `artifacts/evidence/T-2026-10-10-048-red.txt`。批注的**前端渲染与划选入口仍未做**（端点已就绪）。
- 批注前端消费就绪（任务 `T-2026-10-10-049`）：对话页 AI 答案划选 → 操作条（高亮/批注/取消）→ HIGHLIGHT 即渲染 `<mark>`、NOTE 经弹窗提交；批注列表（引文/正文/删除）随消息显示。`Markdown` 升级为多高亮数组 + `data-md-*` 原文偏移标注（旧单值 prop 兼容）；`lib/selectionRange.ts` 做 DOM Selection→原文 UTF-16 半开区间映射。前端 180/180 + lint/guard/build 全过（`artifacts/evidence/T-2026-10-10-049-*`）。CELL 容器内划选、浮条跟随定位未做。
- **真实 Provider Key 端到端生成已验证（2026-10-11）**：用户提供的 DeepSeek Key 经设置端点写入 Windows 凭据管理器（Key 不入库不入证据，已脱敏）→ 模型刷新真实供应商调用成功（added=1，deepseek-v4-pro/deepseek-flash）→ 三角色解析 fallback=NONE → 首问原子接受 → SSE `stable` 终态（seq=33, revision e1a7666d, model=deepseek-v4-pro）→ 权威读回持久化确认。证据 `artifacts/evidence/T-2026-10-10-e2e-realkey.txt`。
- 第三波交付（任务 `T-2026-10-10-051..055`）：**reset/版本选择端点**（§6.4：reset=retry 语义+稳定版前置、select 只移指针，服务层 6 例 + 端点 4 例 RED→GREEN，`:check` 294 例）；**派生任务端点**（§6.5 切片：MESSAGE_TRANSLATION 全链路可用（operation+SSE+artifact 落库+stale fail-closed 判定），其余六 kind 400 `DERIVED_KIND_UNSUPPORTED` 明示 Deferred，`:check` 300 例）；**前端工具包**（朗读/语音输入/版本切换/reset 按钮/翻译块，189 例）；**附件后端**（§6.9：内容寻址 SHA-256 去重、单文件 20 MiB 上限、文本类型白名单、孤儿清理幂等，7 例 RED→GREEN，`:check` 307 例）；**附件前端**（Composer 附件上传/预览 chip/USER 消息附件行 + `/settings/files` 文件管理页：用量/引用数/解析状态/删除/孤儿清理，195 例）。全部 `validate_tasks.py` errors=0。
- 独立 verifier 合并终验（2026-10-11，READONLY 子代理，覆盖 T-045..049）：PASS——后端 `:check` 284 例 + checkstyle 复跑 exit 0、前端 180/180 + 全部守门复跑 exit 0、`validate_tasks.py` errors=0；E2E 证据脱敏确认。
- 状态（2026-10-11 收口）：任务校验 `validate_tasks.py` errors=0（29 条软预算 warning 保留）；后端全量 `:check`（264 例 + checkstyle）exit 0（独立 verifier 复跑确认）。仍属后续：真实浏览器视觉复核、真实 Provider Key 端到端生成、C 树图布局端点、附件/批注/推荐/压缩/翻译/朗读/语音、Android fixtures 等价验证。仓库 `.git` 曾于 2026-10-11 损坏，已恢复（远端历史接续，增量提交已推送）。
