# 知芽 Web 本地版：详细开发方案 V1.0

日期：2026-10-10。状态：开发设计输入，未实现、未验证生产运行。产品目标、数据独立、A 主布局及视觉来源已由用户确认；依赖、桌面数据库准入和平台适配需要开发阶段验证。

入口：[AI 开发交接](TreeLearn_Web本地版_AI开发交接入口.md)。范围权威：[意图](../intent/INTENT-2026-10-10-local-web-edition.md)、[规格](../spec/SPEC-2026-10-10-local-web-edition.md)。功能验收：[一致性矩阵](TreeLearn_Web本地版_功能一致性矩阵.md)。UI：[HTML 原型](../04-ux-design/知芽-Web本地版-交互原型-V0.1.html)、[Web token](../04-ux-design/web-local-design-tokens.json)。

## 1. 实现目标与边界

1. 电脑一条启动入口打开 localhost，网页和本机业务服务同源运行；发行包包含静态页面，评估附带 Java runtime。开发热更新与用户发行启动是两套明确入口。
2. 两端数据库、附件、Key 和偏好分别归各自设备；不建设账户、同步、远程访问或云存储。不做 Android 页面迁移，把共享 Java 接入与业务等价作为本次 Android 相关工作。
3. 完成一致性矩阵全部范围内行为，包含失败、重启与恢复；交付一个可发消息的 Demo 不算功能齐全。
4. 使用用户指定 Web 配色与 v1.1 图标。HTML 原型用于信息结构和交互参考，模拟业务代码不能复制为生产状态机。
5. 先验证共享模块、数据库/凭据和生成核心，再补页面；不先搭空壳、后猜业务契约。

## 2. 推荐工程结构

以下为 2026-10-10 用户确认后的目标组织；`web/` 一级目录由 T-2026-10-10-003/004/005 落地，实际状态以仓库为准。

```text
Treelearn/
  web/
    frontend/               React + TypeScript + Vite、页面、i18n、RuntimeAdapter（已建骨架）
    backend/                仅本机 Java/Spring Boot host、HTTP/SSE、桌面适配（已建骨架，loopback 8733）
    shared/
      domain/               从 :core 复用的唯一领域实现（已迁移，两端 projectDir 引用）
      tree-layout/          从 :tree-layout 复用的唯一布局实现（已迁移）
      application/          用例、生成协调与窄 Repository/系统能力端口（按需抽取，未抽取前不加空壳）
      ai-protocol/          从 android/ai 抽取的唯一请求/提示词/SSE 纯 Java 能力（已抽取，平台代码留 android/ai）
    contracts/              唯一 API/SSE 规范、共享 fixtures；正式开发时建立
    scripts/                安装、开发、构建、发行启动与必要 guard（已建结构守卫/启停脚本）
    README.md               Web 本地版的权威启动说明（开发口与发行口）
  android/                  原生宿主、Android SQLite/Keystore/网络日志适配（:core/:tree-layout/:ai-protocol 的 projectDir 指向 web/shared）
```

- `web/shared/domain` 与 `web/shared/tree-layout` 已从 `:core`/`:tree-layout` 迁移至此并验证（T-2026-10-10-005，Android 与 Web 两端以 projectDir 引用同一份源码，项目名/包名不变）；`web/shared/ai-protocol`（`:ai-protocol`）已从 `android/ai` 抽取（T-2026-10-10-017，跨端 `projectDir` 引用同一份源码，包名不变，`android/ai` 仅保留平台耦合类）；后续如需 `web/shared/application`，只在有真实双端消费者时抽取，不留未被使用的抽象。
- Android 的 `:core`/`:tree-layout` 项目名与 Java package 保持兼容（迁移未改）。Web 服务只依赖纯 Java 构件，不依赖 APK、Android Context、Cursor、Handler 或 AGP Android AAR。
- `application` 抽取的是共用判定与用例，Android/Web 分别实现持久化、线程/调度通知和平台能力；只有证明可达、实际被两个入口调用的共享代码才算共享，不留未被消费者使用的第二套抽象。
- AI 纯协议代码依赖用途、模型、凭据获取端口；网络与日志是适配。Java Controller 不直接拼接 Provider 请求或触碰 Key，统一调用应用用例与 AI Gateway。

### 前端边界

采用页面 → layout/components → hooks/use-cases → store → runtime/api → platform/utils 的单向边界。使用统一 HTTP 客户端；生成流遵守项目的认证 SSE 客户端规则。数据状态存储原始 ID/时间/枚举，i18n 在渲染时解析，错误由适配层转换成可行动文案。

建议页面组件：`AppShell`、`HomePage`、`LibraryPage`、`BranchWorkspace`、`MessageRenderer`、`Composer`、`ContextPanel`、`TreeOverview`、`SettingsLayout`、`ProviderEditor`、`ModelRolesPage`、`SearchServicePage`、`VoicePage`、`FilesPage`、`StatsPage`。组件名是组织建议，不能借命名重新定义业务规则。

## 3. 可行性先行，不把 Android 模块当桌面 JAR

| 验证 | 操作与观察结果 | 失败处理 |
|---|---|---|
| 核心 JVM | 用桌面 Java 编译和运行现有 core/tree-layout fixtures；确保不需要 Android 运行时 | 查真实 import/构建配置，作最小解耦；不全量重写 TypeScript 域 |
| 生成移植 | 追踪 GenerationCoordinator → transaction/DAO → AI Gateway；列出需要的读写与取消端口 | 先抽窄端口，保留 Android adapter 与测试；禁止直接用 JDBC 替换类里零散 Android API |
| AI 边界 | 隔离 Android 日志、JSON/网络实现，验证请求和流解析 fixtures | 只替换平台实现，不修改 prompt purpose/version 判据 |
| SQLite 桌面 | 候选驱动在真实本机引擎执行必要约束/事务/迁移；记录 SQLite 版本 | 不能用 Android 或 H2 结果替代；未通过就停止数据库相关完成结论 |
| 凭据 | 保存/重读/删除/损坏/权限失败；确认日志与返回对象无 Key | Windows 系统凭据/DPAPI 适配未验证前，不改为明文或浏览器存储 |
| 发行启动 | 正常服务 ready、静态首页和本机 API 可达；停止后数据保留 | 依赖缺失和端口冲突必须给出明确诊断；不以 build 成功替代 ready |

活动源码已经证明 `ai` 是 Android library，`GenerationCoordinator` 依赖 Android SQLite。不能声称“不改业务直接搬用”。版本与性能阈值需调研后固定，不凭训练记忆猜当前稳定版。

## 4. 共享规则与两端行为锁

### 必须唯一实现的判定

- `GenerationOperation`/`GenerationState` 的状态与稳定化；非法调用与业务失败类别保持区分。
- `PromptPurpose`/`PromptSpec` 的用途和版本判定；多缺陷输入不遮盖调用方 bug。
- `Branch` 的父子关系、来源与归档资格；创建快照与回答版本不可变。
- `Annotation` 的容器、半开区间、精确锚点和高亮合并；范围端点以领域 Span 的定义为准，不能照抄可能含糊的 UI 参数名。
- 未读聚合、读锚点、模型分工有效性与可用配置回落。尽量共享现有纯函数；抽不出来时先记录实际耦合与消费者，再重构。

同一组 fixtures 分别通过 Android 适配与桌面适配执行，比较成功/失败、稳定错误类别、版本/快照引用、事务副作用。夹具必须覆盖边界与多缺陷组合，不只保存 happy path JSON。统计、时间、Key 存在与可读性、部分回答等不能由前端重新裁定。

## 5. 数据、文件与 schema 生命周期

### 权威与目录

Web 业务持久化由本机服务拥有，推荐 SQLite 单库与独立附件目录。数据目录使用系统应用数据目录或用户明确配置的位置，不能默认为项目 checkout；安装升级与关闭服务均不能删除数据。

SQLite 保留 UTC epoch 毫秒、显式外键、生产事务、完整性检查与递增迁移。UI 偏好可独立保存在安全的非敏感偏好文件；业务草稿、阅读位置与生成状态要有跨服务重启恢复的权威来源，不能仅存浏览器内存。

### 桌面表准入草案

现行 `TreeLearnSchema.TABLE_NAMES` 声明的逻辑表为：主题、分支、消息、稳定回答版本、生成操作、创建快照、派生 AI 结果、提供商、模型、附件、消息附件引用、消息批注。本方案以这些职责为对照，不执行 DDL，不暗中新增冗余表。

| 领域组 | 主要生产者/消费者 | 必要性与生命周期 |
|---|---|---|
| 主题/分支/消息 | 创建/分叉/聊天用例；主页/库/导航/统计 | 树状学习身份与独立历史；删除按照确认的树计划与事务执行 |
| 回答版本/创建快照 | 生成稳定化/分叉用例；版本选择/来源上下文 | 不可变稳定内容与出生上下文；不能用当前回答推导旧快照 |
| 生成操作 | 生成协调；SSE/恢复/取消/重试 | 持久身份与终态，跨刷新/服务重启；保留与清理由已确认业务规则决定 |
| 派生结果 | 摘要/标题/推荐/压缩用例；对应 UI/后续上下文 | 按用途、作用域、输入身份应用，stale 结果丢弃，不写成新的主回答 |
| Provider/模型 | 管理/模型获取；分工选择/AI Gateway | 可用配置与供应商模型标识；Key 不存明文列；模型合并不删除远端暂缺项 |
| 附件/消息引用 | 注册/解析/引用；消息渲染/文件管理 | 文件内容去重；删除内容后引用能表达缺失；消息删除才清自己的引用 |
| 批注 | 稳定内容标注；渲染/批注侧栏 | 容器区间与 digest；内容不匹配按既有清理语义执行 |

**开发前须完成每个桌面永久表的正式入场记录与用户确认**，这是 `.trae/rules/sql.md` 的明确要求，Android 已有表不能自动当作桌面建表批准。记录包括引擎、字段/索引/约束、权威、标识、增长、保留、敏感数据、删除、发布/升级和消费者。本轮提供设计输入，不提前创建表或伪造已获批准。

真实桌面引擎至少验证新装、重开、升级、失败回滚、幂等重跑、约束、外键/完整性与 DAO 查询；Android 相关改动另用真实 Android SQLite 验证。两个引擎的 schema 语义需要对照，证据不能相互替代。

### 文件操作

- 注册按内容 digest 去重；临时写入 → 完成文件 → 记录提交，失败清理未登记内容；路径由服务生成，不接受浏览器任意路径。
- 文件格式与抽取器对照活动 `AttachmentStore` 与生成拼接链路，不从旧原型 `.docx`/图片示例扩展生产范围。
- 删除被引用附件是允许的：内容与记录删除，引用保留缺失状态；不能改成“有引用就禁止删除”。孤儿候选来自唯一查询；清理可重跑，竞态不得误删新出现的有效引用，发现既有实现缺口时单独记录修复。
- 大小/上下文预算超限明确拦截或按已确认降级处理，不静默截断并声称完整注入。

## 6. API、SSE 与重复提交设计要求

正式规范在 `web/contracts/` 建立唯一正文及共享 fixtures，经过 contract-guardian；下表是用例覆盖清单，不是第二份已定稿路由。

| 用例组 | 读/写与身份 | 契约必须写明 |
|---|---|---|
| 启动/能力 | 应用版本、兼容协议、可用平台能力 | 何时 ready，语音/文件不可用如何表达 |
| 主题/分支 | 列表、来源、快照、创建、重命名、归档、恢复、删除 | ID、稳定来源版本、权限/资格、计划版本、事务与不可逆范围 |
| 聊天/生成 | 分支快照、提交、查询、取消、原位 retry/reset | 持久命令身份、操作归属、分支并发、何时接受、失败类别 |
| 版本/派生结果 | 回答版本、摘要/推荐/压缩/翻译 | 内容身份、用途、输入 digest、stale 判定 |
| 批注/阅读 | 容器区间、digest、标注写入/删除、阅读锚点/已读 | 半开区间、编码单位、清理策略、是否稳定、可见性证据 |
| Provider/模型/搜索 | 安全配置、Key 写入/删除、模型列表、分工与搜索设置 | 密钥不可读回、回落规则、失败是否可重试 |
| 文件/统计 | 上传/列表/引用/删除/孤儿清理、统计快照 | 限制、解析状态、物理缺失、同源统计与日期含义 |

- 普通请求经统一 envelope 返回稳定类别和安全参数；错误文案由前端本地化，不把异常原文返回 UI。
- 时间点单位统一 epoch 毫秒；date-only 单独标识；真实时间在设备时区渲染。
- 客户端操作身份与日志 requestId 分开；禁止拿新的传输请求号作为新的业务提交。
- 提交结果不确定时查询权威 receipt/operation；恢复后禁止盲目重发。去重信息必须有持久化承载及生命周期；优先复用既有生成身份/字段，若需新字段或表则补 schema 准入，不用进程内 Map 冒充持久幂等。
- SSE 包含 operation 归属与单调事件序号；快照与事件边界必须定义。重复事件幂等处理，缺口/重连读快照；不同操作的晚到 delta 不得写到当前操作。终态只确认一次。
- 浏览器刷新不应停止服务拥有的操作；停止是显式用户命令。服务重启时不假装续接供应商字节流，根据真实已落盘状态落定中断，保留部分内容。

## 7. 生成完整路径

1. 读取可用模型分工与 Key 状态，按用途固定 prompt policy，确定 branch 与操作 identity。
2. 已有主题按生产规则先持久化 operation 再发送；首主题保留现有 provider accepted 后原子写入的义务，不能前端先建空主题。
3. 请求经 AI Gateway，推理/回答分离；增量按现有提交窗口持久化。服务内缓存只是调度或派生状态，恢复读取数据库。
4. 正常流终止且回答非空 → 写稳定版本并转换终态；其它终止分别走失败、中断、取消。独立用例负责校验上游正常终止，纯领域类型无法观察的前提必须在 javadoc/交接中写明。
5. 后续标题/摘要/推荐/压缩等按用途和输入身份生成；旧输入结果不能覆盖新内容。
6. 分叉只消费稳定版本和冻结来源；生成 UI、React Flow、推荐按钮均不自己放宽资格。

需要实际测试的竞争：cancel 与 complete、旧 delta 与 retry、新模型配置与已固定命令、同分支双标签提交、删除计划与新生成、服务退出与未提交 batch。

## 8. Web 交互实现注意事项

### 文本与批注

- 建立稳定的文本容器映射，区分 PROSE 与 CELL；表头算入表格行索引。区间以容器文本为基准，渲染 AST、DOM Text 节点和领域文本间有确定映射。
- Java/JS 都需明确编码与 end-exclusive；测试 emoji、组合字符、中文、重复句子、行内公式/代码、表格、跨行以及选区跨容器。
- 不用 `innerHTML` 或 DOM node path 持久化位置，不用整个 Markdown 原文偏移冒充渲染正文容器偏移。
- `AnnotationDao.listFor` 的 digest mismatch 是清理而非自动重定位；正式实现与 UI 说明必须一致。不能通过把错误标记挪到相似文字来“保住批注”。

### 读位置、草稿与滚动

- 使用 branch/message/fragment 身份保存读锚点，像素只是辅助；窗口缩放或文本重排后重新解析定位。
- 生成流只更新需要的消息；不要每个 token 重建整列、重置输入或选区。上翻时暂停跟随，回到最新是明确动作。
- 切分支、打开设置、模型选择、全屏编辑、选文动作均保护草稿。输入法组合事件与快捷键做真实浏览器验证。
- B 的左右栏分别持有视口状态；输入只属于当前分支。树图跳转返回后按来源恢复，不丢主阅读。

### 画布

- 候选 React Flow 只负责渲染/命中/视口，树结构来自服务，布局坐标来自共用算法或通过黄金 fixtures 证明等价的适配。
- 禁止用户创建边/重挂父节点；节点移动不写入领域关系。折叠/展开是已有业务命令，不只是浏览器暂时隐藏。
- 大树先测真实规模与渲染瓶颈再定阈值，采用裁剪/虚拟化等可证实优化，不凭节点上限数字承诺性能。

### 视觉与本地化

- Web 颜色只从 `web-local-design-tokens.json` 生成，基础档位从原系统读取。用户新配色只适用于 Web 命名空间，Android 色值不随之漂移。
- v1.1 的 `Ic` 与各 `I*` SVG 轮廓作为图标来源，保留语义和描边。品牌萌芽不另画一套。
- 主操作的鼠尾草绿底与浅色文字对比不足，因此使用 `web-on-sage`；次级正文使用可在雾绿底达标的前景色。按 actual foreground/background 检查 AA，不看颜色名字。
- 所有生产用户文案使用 zh-CN/en-US 对应 i18n key，包括可访问标签、通知、导出文案、错误和相对时间。原型中文文案仅为设计输入。

## 9. 凭据、loopback 与日志

- 本机服务只绑定 loopback，并校验 Host、Origin 与写操作来源；同源页面无需通配 CORS。阻止外部网站通过 DNS rebinding 或跨源表单写入本机配置。
- 以适配端口实现 Windows 系统凭据/DPAPI；读取只用于发送，安全快照返回掩码与状态。输入字段提交后尽快清空，保存失败保留可重输的说明，不留在全局 store。
- 模型回答视为不可信文本，渲染器禁止任意脚本和危险 URL；外链/Markdown HTML 的策略写进安全测试，静态资源不依赖运行时 CDN。
- 本地 debug 日志只记录 operation/branch identity、状态、计数、耗时与稳定失败类别；正文、推理、prompt、Key、文件内容不记。
- 新 HTTP host 的 requestId/span 规则按服务边界落定，不能拿 Android 无服务端例外直接豁免；本机限定与无远程遥测的适用记录和旧服务规则需要显式核对。

## 10. 开源借鉴的本地源码入口

| 已下载源码 | 重点核查位置 | 允许借鉴与禁止顺带吸收 |
|---|---|---|
| `D:/Project/Android/LibreChat-main/LibreChat-main` | `client/src/components/UnifiedSidebar/`、`Messages/ScrollToBottom.tsx`、`data-provider/SSE/` | 借鉴桌面导航、独立输入/滚动、流式状态；不引入 Mongo/账号/Agent 数据模型 |
| `D:/Project/Android/anything-llm-master/anything-llm-master` | `frontend/src/components/LLMSelection/`、`pages/WorkspaceSettings/` | 参考配置加载、Provider/模型/文件的错误与状态；不照搬明文 Key 生命周期、工作区/RAG 平台范围 |
| `D:/Project/Android/xyflow-main/xyflow-main` | `packages/react/`、`examples/react/src/examples/Basic/` | React Flow 视口/节点/控件；关闭图编辑自由，按本项目树关系消费 |

本轮读取三个本地 LICENSE，均为 MIT。正式引入仍需固定源码/包版本、登记 NOTICE、检查选用文件及传递依赖许可和漏洞；阅读参考不等于安装或构建验证。没有本地找到 Prompt-Tree/Fractal/assistant-ui，不杜撰其下载位置或本地运行结果。

## 11. 开发顺序与交付切片

以下是阶段依赖建议，不是 Task v2 文件。开发 AI 根据用户是否选择 Harness 决定创建 Task；选择后遵守仓库拆分、行数、TDD、verifier 和专项 guard，不拿本设计阶段的轻量流程替代开发门禁。

| 顺序 | 产物 | 进入下一阶段的证明 |
|---|---|---|
| 1 范围与构件 | 功能矩阵核对、共享 JVM 能力、最小本机 ready、规则适用记录 | 桌面 JVM 与 Android 回归；没有新平台耦合 |
| 2 存储与凭据 | 桌面 schema 准入、迁移/Repository、附件目录、SecretPort | 真实桌面引擎/凭据的正反路径与重启恢复 |
| 3 唯一契约 | 正式 API/SSE 文档、双端 fixtures、客户端/Controller 编解码 | contract-guardian 对照；未知字段/单位/错误语义不漂移 |
| 4 生成核心 | 创建、提交、停止、失败/中断、retry/reset、稳定版本与分叉 | 真实 Provider 配置或明确缺口；状态和事务锁覆盖 |
| 5 Web 闭环 | A 工作区、主页/库、配置、来源、树导航、真实数据 | localhost 正常入口：首问 → 流式 → 稳定 → 分叉 → 来源 → 重启恢复 |
| 6 功能补齐 | 渲染、选区、批注、文件、搜索、翻译/语音、统计、B/C | 功能矩阵每行均有正常/失败/重建结果 |
| 7 发行 | 单一启动入口、静态打包、健康/ready、数据目录、README | 干净安装/升级/停止后启动、端口冲突与数据保留 |

## 12. 必要验收与证据

- Java：`web/shared` 的 JVM 套件、用途/版本跨入口对照、生成状态与多缺陷输入、布局 golden fixtures；共享修改覆盖实际 Android 消费者。
- 数据：实际桌面 SQLite 新装/升级/回滚/重开与约束；修改 Android 数据层另跑真实 Android SQLite，不能共用同一个引擎的结果自证。
- 契约：同一 fixtures Java producer/TypeScript consumer 往返；snapshot/sequence、取消、重复、未知事件、时间单位、错误映射。
- 浏览器：Chrome/Edge 的键鼠/IME、窗口重排、阅读与选区、B/C、输入与权限；部分回答不可分叉；数据重建不发重复模型请求。
- 恢复：发送后刷新、关页后再开、服务 kill 冷启动、两个标签抢同分支、旧流晚到、删除/取消竞争；测试输出不得含真实聊天和 Key。
- 内容：中文/英文、两个跨日时区与夏令时区域；PROSE/CELL 锚点、emoji、Math/Markdown、XSS、危险链接、缺附件标记。
- 语音：系统/浏览器引擎可用、不可用、权限拒绝、识别失败和取消；如果无可用适配，此功能未验证，整版功能一致不能报完成。
- 启动：前端正常入口与本机服务逐个 ready/页面/API；发行静态资源离线可加载；在所支持环境真正冷启动，不能用 mock/build/lint 替代。
- 守门：生产 frontend 文案 i18n、lint/build；Android 改动 Java-only；数据库 db-migration-reviewer；契约 contract-guardian；Harness 选择后每片 verifier 及任务机械校验。

回归锁或唯一替代证据要做变异验证：退化成缺陷形态后相应用例转红，再还原绿色。证据记录准确命令/模式/时间/退出码，数字从证据文件回读；不能凭记忆写测试数量或成功比例。

## 13. 尚需工程验证的事项

- Spring Boot/JDK/Node/pnpm/SQLite 驱动与 React Flow 版本、兼容、许可、体积；本轮不指定未经安装/运行验证的版本。
- 桌面永久表清单批准与规则适用记录；这两个是实施准入，不是 UI 设计缺稿。
- 语音识别网络归属和目标浏览器覆盖；不能自动新增付费服务来补齐。
- 共享生成/数据库端口抽取的最小范围；不把整个 Android 模块迁移当作已授权的无风险重构。
- 旧人设材料与当前无可达选择器的差异；查不到生产入口时保留固定教学提示词，不新增假功能。

## 14. 文档影响与交接纪律

本轮新增设计输入与 HTML，不新增生产 API/SQL/依赖或启动脚本。原型在本机浏览器可体验，不代表正式服务已存在。旧 WebView 草案是历史范围，新意图将浏览器正式端纳入目标，两端仍不同步。

开发时同步根/模块 README、当前架构与项目背景包、正式契约、schema/表清单、安装/启动/环境示例及设计 token 消费记录。带日期调研材料保留历史说明，发现 PRD 漂移先报告，不静默改写历史。

交付用户时区分：设计已交付、具体功能已验证、未验证限制和实际启动方式。不能把“页面存在”“原型成功提示”“任务标记完成”作为真实运行事实。
