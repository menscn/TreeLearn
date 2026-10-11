# 知芽（TreeLearn）Web 本地版：交付报告（2026-10-10）

> 本报告是当前态交付记录。结论只覆盖有运行证据的部分；未实现/未验证项逐条标注，不以后续任务状态代替运行事实。
> **2026-10-11 收口修订**：按实际代码与运行证据重核功能矩阵（T-042/043/044 收口 + 此前 T-036/037/039/040/041），旧版的「10/13/17 行」与「40 行」计数作废——旧矩阵实际仅列 34 行且多处结论早于最新任务。

## 1. 实际运行环境（实测）

| 项 | 值 |
|---|---|
| 操作系统 | Windows（本机开发机） |
| JDK | 21.0.10（`JAVA_HOME`；注意 PATH 上的 `java`/`javac` 存根在本机崩溃，构建须用 gradlew） |
| Node / 包管理器 | Node 22.22.0 / pnpm 11.5.2 |
| 后端 | Spring Boot 4.1.1（`spring-boot-starter-webmvc`），Java 21 编译目标 |
| 桌面数据库 | SQLite（`org.xerial:sqlite-jdbc:3.53.4.0`，内嵌引擎实测 `sqlite_version=3.53.4`） |
| 凭据 | Windows 凭据管理器（JNA 5.19.1，手写 Advapi32，未用 jna-platform） |
| 前端 | React 19 + TypeScript + Vite（pnpm），无运行时 CDN，图标 lucide-react 1.55.0 |
| 浏览器 | Chrome/Edge（自动化复验使用 Chromium 142） |

## 2. 真实启动与停止方式（本报告的证据来源）

发行口（单命令，同源托管前端产物）：

```powershell
# 启动：构建缺失时先 pnpm build，然后启动后端并等待 ready
powershell -NoProfile -ExecutionPolicy Bypass -File web/scripts/start-web.ps1

# 停止（按 127.0.0.1:8733 的监听进程；端口已空闲时幂等成功）
powershell -NoProfile -ExecutionPolicy Bypass -File web/scripts/stop-web.ps1
```

- 浏览器访问：`http://127.0.0.1:8733/`（服务只绑定 loopback）。
- 就绪判据：`GET /api/health/ready` 返回 `{"service":"treelearn-web-backend","status":"ready"}`。
- 开发口（热更新）：`cd web/frontend; pnpm dev`（5173，`/api` 代理到 8733）+ `web/scripts/start-backend.ps1`。
- 实测证据：`artifacts/evidence/T-2026-10-10-029-release-{run,http,stop}.txt`（`/` 与前端路由 200、`/api/health/ready` 200、`/api/topics` 200、未知 API 404 封装、stop 后端口零监听、二次 stop 幂等）。

## 3. 数据目录与发行产物

- 数据目录：`%LOCALAPPDATA%\TreeLearn-Web\`（`treelearn.db` + `attachments/` + `logs/`），可用 `TREELEARN_DATA_DIR` 覆盖；**不指向仓库 checkout**；停止/升级不删除数据。
- 版本机制：`PRAGMA user_version`（当前 1）；每次连接启用并读回校验 `PRAGMA foreign_keys=ON`、`busy_timeout=5000`、`journal_mode=WAL`；迁移失败整链回滚且不盖版本号。
- 发行产物（实测）：后端 `web/backend/build/libs/treelearn-web-backend-0.1.0.jar`（34,282,890 B ≈ 32.7 MiB）+ 前端 `web/frontend/dist/`（index.html 403 B + assets）。
- 桌面数据库对象：12 张逻辑表 / 15 索引 / 8 触发器 + 内置 DeepSeek Provider 种子（与 Android V5 最终形状逐项语义等价，由 db-migration-reviewer 复核「无多无少无差异」）。

## 4. 功能一致性矩阵：逐行结论

口径：**已交付=有 Web 运行证据 + Android 对照**；**部分=有实现但有明确缺口**；**未实现=本批未做**（不以后续任务状态代替）。四类证据（Android 对照/Web 运行/正常/失败或重建）在任务文件与 `artifacts/evidence/` 中逐项留档。

### 4.1 主题、对话与生成

| 项 | 状态 | 说明与证据 |
|---|---|---|
| 主题创建 | 已交付 | 首问原子接受（五组行一事务）、无 Key 不落空主题（`KEY_MISSING`）；端到端 `CoreApiEndToEndTest`；主页 `HomePage` + 引导闭环（T-034） |
| 连续追问 | 已交付 | `POST /api/branches/{id}/messages` + 工作区追问 composer；不混入兄弟历史由分支维度保证 |
| 稳定回答分叉 | 已交付 | `POST /api/branches/{id}/forks`（整条 + 选文、冻结出生快照、溯源）；独立 verifier PASS（T-037） |
| 来源回溯 | 部分 | B 视图显示分叉来源与锚点高亮/降级；「跳回原来源定位」交互未实现 |
| 生成状态 | 已交付 | 协调器终态判定（正常+非空→STABLE；空/仅推理→FAILED）+ SSE §7 + UI 状态条；失败/中断/取消可区分 |
| 并发与调度 | 部分 | 单分支活跃操作互斥（`BRANCH_BUSY`）已交付并有测试；多标签页真实竞争未做浏览器验证 |
| 停止生成 | 已交付 | `POST /api/operations/{id}/cancel`（CANCELED 保 partial）+ UI 停止按钮 |
| 原位重试与重置 | 部分 | retry 已交付（原位新建尝试、不改 ordinal）；reset 未实现 |
| 回答版本 | 部分 | 活跃修订读取与显示已交付；版本切换器 UI 未实现 |
| 思考过程 | 已交付 | `reasoning_content` 分离、折叠展示、思考开关随请求持久化 |
| 上下文压缩 | **未实现** | 摘要读取路径与压缩派生未做（`CONTEXT_SUMMARY_MISSING` 已按 Android 语义拒绝） |
| 提示词策略 | 已交付 | 共享 `prompt` 9 类（两端唯一实现）+ 用途/版本判据；请求 fixtures 覆盖 8 用途×思考两态 |

### 4.2 阅读与知识树

| 项 | 状态 | 说明与证据 |
|---|---|---|
| Markdown、代码、表格、公式 | 已交付 | 自写渲染器（无 `dangerouslySetInnerHTML`）、流式未闭合语法降级、公式为可读降级（非富渲染）；安全用例独立复验 10+11 例 |
| 复制与引用 | 部分 | 代码块复制已交付（含失败提示）；选区复制/引用 chip 设置入口未实现 |
| 推荐问题 | **未实现** | 派生任务未做 |
| 高亮 | 已交付 | 端点（T-047）+ 前端划选与 `<mark>` 渲染（T-049：多高亮 Markdown、划选→原文偏移映射、操作条/列表/删除） |
| 批注 | 已交付 | 端点（T-047）+ 前端 NOTE 弹窗提交与批注列表/删除（T-049）；表格单元格内划选未做 |
| 批注失效 | 已交付 | 答案重生成后按显示文本 SHA-256 digest 在读取时同事务清理失配行（T-047，镜像 Android AnnotationDao） |
| 草稿与阅读位置 | 已交付 | 草稿（T-031）+ 读写端点（T-042）+ 前端消费：离开分支自动保存阅读位置（T-048） |
| 未读 | 部分 | 分支树未读对 + seen 端点（T-042）+ 前端消费：进入分支自动已读、树图未读徽标（T-048）；跨标签页真实竞争未做浏览器验证 |
| 树导航与折叠 | 已交付 | 分支树端点 + 折叠持久化（T-042）+ C 树图全链路：§6.11 布局端点（共享 `:tree-layout` 几何，T-045）+ 前端 `TreeCanvasView` 渲染与点击导航（T-046） |
| 归档与恢复 | 已交付 | `POST /api/branches/{id}/{archive,restore}`：根分支 400 `BRANCH_NOT_ARCHIVABLE`、状态冲突 409、归档不取消生成（真实 HTTP+SQLite 13 例，T-042） |
| 删除 | 已交付 | 删除预览（快照范围 + SHA-256 digest）与确认删除（先取消活跃操作再单事务删除、digest 过期 409、幂等），真实 HTTP+SQLite（T-042） |
| 应用内搜索 | 已交付 | `GET /api/search`（七臂 UNION ALL 镜像 Android SearchDao、归档不可搜、DISPLAY_ORDER、UTF-16 命中区间）+ 学习库页三范围分组/高亮/Ctrl+K（真实 HTTP+SQLite 14 例，T-043/044） |
| 学习统计 | 已交付 | `GET /api/stats`（镜像 Android StatsAggregator 口径：设备时区自然日/连续段/空态/当月热力，非法参数 400）+ 统计页与月份切换（T-043/044） |

### 4.3 配置、平台能力与文件

| 项 | 状态 | 说明与证据 |
|---|---|---|
| Provider/Key | 已交付 | 设置端点（T-036：Provider CRUD、Key 只写 Windows 凭据 `Treelearn-Web/provider/<id>`）+ 设置页（T-039，Key 不落 localStorage/URL/日志）；**真实 Key 端到端生成仍未验证**（需专用 Web Key） |
| 模型分工 | 已交付 | GET/PUT `/api/settings/model-roles`（数据目录非敏感偏好文件，键名对照 Android SharedPreferences，未新增永久表）+ 分工页与回落一致性（T-036/039/041） |
| 模型回落 | 已交付 | `GET /api/settings/model-resolution`（NONE/BUILTIN/EARLIEST/EMPTY），浏览器复验 200 |
| 联网搜索 | **未实现** | 端点未做 |
| 翻译与朗读 | **未实现** | 未做 |
| 语音输入 | **未实现** | 浏览器能力探测与降级未做（Android 侧为系统语音服务） |
| 附件注册与解析 | **未实现** | `attachments` 恒空数组（已在 README 与任务记录标注） |
| 文件管理与清理 | **未实现** | 未做 |
| 设置/返回路径 | 已交付 | `/settings/*` 分区导航 + Provider/分工页正文（T-039）；未知分区回落通用/关于页 |

### 4.4 汇总

- **已交付**：21 行；**部分**：6 行；**未实现**：7 行（矩阵实际共 34 行；2026-10-11 第二波后：C 树图、高亮/批注、批注失效转已交付）；旧版「40 行 / 10+13+17」计数与其自身行列表不符，已按行清点更正）。
- 结论：**核心闭环（配置→首问→流式→稳定→追问→停止→重试→重启恢复）+ 分叉、归档/恢复、删除、阅读位置、未读已见、折叠、应用内搜索、学习统计、C 树图、批注端点、Provider/Key 与模型分工设置在 Web 端真实可用**；**辅助功能（批注前端消费、推荐问题、上下文压缩、联网搜索、翻译/朗读、语音、附件）本批仍未交付**。

## 5. 双端一致性与共享实现

- 共享纯 Java 构件（两端 projectDir 引用同一份源码，无副本）：`web/shared/domain`（`:core`，29 类型）、`web/shared/tree-layout`（12 类型）、`web/shared/ai-protocol`（T-017 抽取的 18 类：prompt 9 + gateway 9）。
- Android 回归（每次涉及共享/Android 的变更均独立复跑）：`:core` 223 / `:ai` 31 / `:data` 65 单测、`:app:assembleDebug`、`verifyDependencyDirection`、`no-kotlin-check.ps1`（VERDICT PASS，13 OK / 3 APK dex 无 Kotlin 运行时）。
- 桌面平台适配：Jackson 版请求体/流解码（与 Android `org.json` 平台实现逐分支对照；唯一差异=严格解析，已记录）、JNA 凭据、sqlite-jdbc。

## 6. 质量守门执行情况

- 独立 verifier：每个任务均由**独立于实现者**的 verifier 复核（不接受实现者自证）；verifier 自行运行测试/构建/真实启动。
- 专项 guard：contract-guardian（契约，多次发现并促成修复：kind 大小写、SSE 终态补帧、`active` 端点语义、§4 幂等落点）、db-migration-reviewer（桌面 schema）、android-no-kotlin-guard（Java-only）均独立执行。
- 真实引擎/真实事件证据：SQLite（真实 sqlite-jdbc，非 H2/mock）、Windows 凭据（真实凭据管理器 + 跨进程重读）、SSE（真实 HTTP 流）、发行口（真实启停 + 端口释放）、浏览器（四轮真实复验）。
- 任务机械校验：`python .trae/scripts/validate_tasks.py` → `files=99 errors=0 warnings=29`（2026-10-11 收口；warnings 为规模软上限提示，可保留）。

## 7. 剩余缺口与未验证项（如实标注）

**未实现（功能缺口，需后续任务）**：来源定位跳转、上下文压缩、推荐问题、联网搜索、翻译/朗读、语音输入、附件注册与解析、文件管理与清理、retry 之外的 reset。\n\n**未验证（有实现但缺证据）**：多标签页真实竞争与浏览器重连（需浏览器轮）；Android fixtures 等价验证。~~真实 Provider Key 端到端生成~~ **已于 2026-10-11 用用户提供的 DeepSeek Key 完成验证**（`artifacts/evidence/T-2026-10-10-e2e-realkey.txt`：写 Key→模型刷新→分工解析→首问→SSE stable→权威读回）。

**未验证（有实现但缺证据）**：真实 Provider Key 端到端生成（代码已推进，最终报告必须把这一项标为未验证，直到提供专用 Web Key 并完成真实流式链路测试）；多标签页真实竞争与浏览器重连（需浏览器轮）；Android fixtures 等价验证。

**基础设施事件（2026-10-11）**：仓库 `.git` 分支指针与全部提交对象丢失（工作树与暂存区完好；远端不可达），恢复前所有变更只能留在工作区/暂存区，无法形成提交。

**未验证（有实现但缺端到端证据）**：
- **真实模型生成**：未提供 Web 端 Provider API Key，故「真实 Provider 的提问→流式→稳定」未经真实调用验证；现有证据为桩网关（本机 HTTP stub）与共享 fixtures 等价性。**这是本次交付最大的未验证项。**
- 多标签页同分支竞争；浏览器网络抖动下的重连/心跳表现；毛玻璃在超窄/超宽视口的逐一截图。
- Android 侧 fixtures 等价执行（T-022 计划）未落地。

**流程偏差（如实记录，供复核）**：
1. 规模越线（无用户批准，按既有授权留痕）：T-010（675 行）、T-024（773 行）、T-028（732 行）超 500 硬上限；verifier 均判「规模 FAIL」，本报告在此列明，**裁决权在用户**（批准例外或要求拆分重做）。根因是立项估算偏低（同类镜像任务实测约为估算 1.2–2 倍）。
2. TDD 证据强度：T-024/T-035 的首轮 RED 为**回溯式补录**（实现先于红灯落笔，后以临时回退复现）；T-006 的 TDD 偏差此前已记录。均如实标注，未声称例外获批。
3. 桌面 12 张表准入：确认来源为「用户指示下按推荐执行」（集中呈报时用户跳过提问未反对），非逐条口头确认；`model-roles` 等新增持久化需求将再次触发准入确认。

## 8. 结论

- Web 本地版**已在真实 localhost 上可启动、可停止、可浏览器使用**，数据独立保存于 `%LOCALAPPDATA%\TreeLearn-Web`，发行产物为可复制目录（jar + dist）。
- **核心学习闭环可用**；功能矩阵约 40 行中 10 行已交付、13 行部分交付、17 行未交付——**未达到与 Android 全量功能对齐**。
- 视觉交付达到「简约 + 毛玻璃（真实生效）+ icon-only 按钮 + 中英双语」；毛玻璃曾因「前缀属性被构建剥离」而实际失效，经四轮浏览器复验定位并修复（computed `blur(18px)`/`blur(24px)`）。
- 后续建议顺序：Provider/Key 设置端点（解锁真实使用）→ 附件 → 分叉 → 批注/高亮 → 搜索/统计 → 归档/删除 → 派生任务 → 树图 C 视图；并补齐真实 Key 验证与 Android fixtures 等价执行。
