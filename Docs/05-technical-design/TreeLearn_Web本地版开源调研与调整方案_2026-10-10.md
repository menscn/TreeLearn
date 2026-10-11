# 知芽（TreeLearn）Web 本地版：开源调研与调整方案

> 后续决定（2026-10-10）：用户已确认两端数据不同步、A 专注阅读默认，并指定薄荷雾面配色与 v1.1 图标。本文保留首次调研的历史表述；当前设计与开发输入从 [AI 交接入口](TreeLearn_Web本地版_AI开发交接入口.md)读取，未决数据关联问题已经关闭。

日期：2026-10-10（Asia/Shanghai）

状态：调研完成，方案待用户 review；不是已批准的实现规格或运行手册。

范围来源：用户要求启动服务后通过 localhost 在浏览器使用，功能与手机版保持一致，交互按 Web 调整；本轮先检索参考项目并给出处理方案。

## 1. 推荐结论

建议建设 TreeLearn 自己的 Web 本地版：React + TypeScript + Vite 页面，加一个只面向本机的 Java 服务；借鉴 LibreChat 的桌面聊天体验，重点参考 Fractal Chat 与 Prompt-Tree 的分支交互，评估 React Flow 承载树画布。

采用组件和局部能力复用，保留 TreeLearn 自己的领域规则。抽样核查的参考项目没有证明可以直接满足知芽的稳定回答分叉、不可变创建快照、回答版本与生成恢复契约。直接 fork 通用聊天产品，需要同时改造它的业务模型与部署体系。

建议优先做真实浏览器端，再考虑将共享页面接回 Android。此前方案的“先迁设置入口到 Android WebView”无法直接交付本次的 localhost 产品。

方案默认按单人、电脑本地运行设计。Web 与手机的数据关联方式仍待用户选择；本文以各自本地保存、导入/导出作为建议，不将自动同步或数据迁移当作已批准功能。

## 2. 当前仓库事实

| 核对项 | 核对结果与活动来源 |
|---|---|
| 现有可运行产品 | Android 原生 Java/XML/View；根目录没有生产 `frontend/` 或 `backend/`。HTML 原型不等于生产 Web 客户端。 |
| 先前 Web 意图 | [2026-10-01 意图](../intent/INTENT-2026-10-01-progressive-web-ui.md) 已 accepted；只批准 Android WebView 渐进迁移，浏览器正式端、服务端和同步均不在当时范围。 |
| 先前规格 | [2026-10-01 规格](../spec/SPEC-2026-10-01-progressive-web-ui.md) 仍是 draft；仅浏览器 Mock 预览，不是真实 Browser Runtime。 |
| 可复用的 Java 核心 | [`core` 构建](../../android/core/build.gradle) 与 [`tree-layout` 构建](../../android/tree-layout/build.gradle) 使用 `java-library`；本轮对两者生产源的 Android/AndroidX import 搜索未命中。仍需电脑 JVM 实际编译运行验证。 |
| AI 适配限制 | [`ai` 构建](../../android/ai/build.gradle) 使用 Android library，[`DeepSeekGatewayFactory`](../../android/ai/src/main/java/com/treelearn/ai/gateway/DeepSeekGatewayFactory.java) 使用 Android 日志。不能直接作为桌面 JVM 模块接入。 |
| 生成适配限制 | [`GenerationCoordinator`](../../android/app/src/main/java/com/treelearn/app/generation/GenerationCoordinator.java) 直接依赖 Android SQLite、DAO 与数据库 helper；重用需要先抽取窄平台端口，不能只复制类。 |
| 数据与密钥 | [`TreeLearnSchema`](../../android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java) 是现行 schema 来源；[`KeystoreSecretStore`](../../android/data/src/main/java/com/treelearn/data/secret/KeystoreSecretStore.java) 是 Android 凭据实现，浏览器和桌面 JVM 都不能直接调用。 |
| 真实功能入口 | [`AppContainer`](../../android/app/src/main/java/com/treelearn/app/AppContainer.java)、[`MainActivity`](../../android/app/src/main/java/com/treelearn/app/MainActivity.java)、[`ChatActions`](../../android/app/src/main/java/com/treelearn/app/ui/ChatActions.java) 是装配与用户动作来源。旧 RikkaHub 吸收矩阵的“无附件/无语音/DeepSeek-only”等描述已不能代表全部现行代码。 |
| 许可边界 | 根 [`LICENSE`](../../LICENSE) 是专有许可；引入第三方源码时逐个核对许可，不能将公开仓库统一视为可直接复制。 |

以上是源码与配置核对，不代表本轮已经在设备或浏览器运行验证。

## 3. 开源与源码参考调研

本轮检索 GitHub 仓库，核查 README、许可正文或声明、选定构建文件和源码路径。没有安装、启动这些参考项目，也没有完成全库代码、安全或依赖审计。访问日期统一为 2026-10-10。

### 3.1 推荐重点参考

| 项目 | 核实内容 | 借鉴方式 | 代价与边界 |
|---|---|---|---|
| [LibreChat](https://github.com/LibreChat-AI/LibreChat) | MIT；React/TypeScript 客户端；Express/MongoDB 服务；README 声明流式聊天、编辑/重提与分支、搜索、附件、语音。 | 优先参考聊天工作区、侧栏、模型选择、消息动作、流式阅读、附件与错误处理；按功能逐项核对后选择局部实现。 | 默认 Compose 涉及 MongoDB、Meilisearch、RAG 等服务；其“会话分支”不能直接当作知芽的“独立学习分支 + 创建快照”。不建议整套接入。 |
| [Fractal Chat](https://github.com/LeoLiao0806Xuan/Fractal_Chat) | Apache-2.0；React/Vite；README 明确标为 active prototype；支持递归子对话、浏览器持久化与导出。 | 优先参考选中文字创建子对话、父子分支切换、桌面分支阅读；局部代码需先核对与项目语义的差异。 | 小型原型，缺少本轮运行与压力验证；浏览器凭据和存储不能替代本机服务方案。 |
| [Prompt-Tree](https://github.com/yxp934/Prompt-Tree) | README 声明本地启动、树/DAG、上下文面板与压缩；实际包位于 `ai-chat-client/`，Next.js/React，使用 React Flow；包清单声明 MIT。 | 重点参考树与对话的切换、上下文来源展示、分支导航和本地启动体验。 | 本轮未核到完整 LICENSE 正文，源码复制暂不放行；自由拖入上下文、DAG 和多模型平行分支不是知芽现行契约，不能一并移植。 |
| [AnythingLLM](https://github.com/Mintplex-Labs/anything-llm) | MIT；Vite/React 前端，Express/Prisma 服务；README 提供本地/自托管、Provider 与文件能力。 | 参考 Provider 配置、文件管理、本地服务持久化和启动组织。 | 产品重心是工作区、文档与 RAG；不能提供知芽核心学习树模型，整套吸收会增加范围。 |
| [React Flow / xyflow](https://github.com/xyflow/xyflow) | MIT；React/Svelte 节点画布库；提供节点、连线、视口与交互基础。 | 树画布的优先组件候选；复用知芽布局计算或对照 fixtures 绘制，保留树关系的业务约束。 | 画布库不负责快照、归档、未读和分叉权限；默认编辑连线/自由重挂父节点能力应关闭，避免改变树的业务含义。 |
| [assistant-ui](https://github.com/assistant-ui/assistant-ui) | MIT；TypeScript/React 聊天组件与 runtime adapter；README 展示消息动作、输入栏、视口、分支选择与后端适配。 | 作为聊天基础组件候选，先验证自定义数据 runtime 能否覆盖稳定版本、推理和停止/恢复语义。 | 不采用它的默认数据模型替代 TreeLearn。若适配成本高，保留局部组件或直接实现薄界面。 |

### 3.2 不优先作为底座

- [Open WebUI](https://github.com/open-webui/open-webui)：本地启动、多 Provider、语音、文件与搜索值得参考；但前端为 Svelte、后端为 Python，当前 [许可](https://github.com/open-webui/open-webui/blob/main/LICENSE) 存在品牌保留条款与例外，不能按普通 MIT/BSD 处理。作为体验参考，不推荐整套改为知芽。
- [LobeHub](https://github.com/lobehub/lobehub)：设置和大屏工作区可参考；仓库由旧 lobe-chat 地址重定向。当前 [Community License](https://github.com/lobehub/lobehub/blob/main/LICENSE) 对商业衍生分发有额外条件；包清单的 MIT 字段不能覆盖许可正文。部署与业务范围也明显大于本次目标。
- [Cherry Studio](https://github.com/CherryHQ/cherry-studio)：桌面布局和 Provider 交互可参考；当前为 Electron 桌面应用、AGPL-3.0，不能当作现成 localhost 浏览器产品或普通宽松许可组件直接搬入。
- [tldraw branching-chat-template](https://github.com/tldraw/branching-chat-template)：适合参考无限画布聊天探索；模板为 MIT，但依赖的 tldraw SDK 使用自己的许可，不能以模板许可代替 SDK 许可。知芽首选更贴近树导航的 React Flow。
- [Chatbot UI](https://github.com/mckaywrigley/chatbot-ui)：MIT、Next.js/Supabase，可参考基础聊天结构；本轮 API 显示最近推送停留在 2024 年，兼容与维护成本需重验，优先级低于前述候选。

### 3.3 可追溯的主要核查入口

- LibreChat：[README](https://github.com/LibreChat-AI/LibreChat/blob/main/README.md)、[许可](https://github.com/LibreChat-AI/LibreChat/blob/main/LICENSE)、[客户端依赖](https://github.com/LibreChat-AI/LibreChat/blob/main/client/package.json)、[服务依赖](https://github.com/LibreChat-AI/LibreChat/blob/main/api/package.json)、[默认部署](https://github.com/LibreChat-AI/LibreChat/blob/main/docker-compose.yml)。本轮读取的客户端与服务文件 blob SHA 分别为 `6831d650cdf34fde9582d2d676d67a31ffddffa0`、`c63c38ea1dddc62e4b3cb81d13e19313e190e987`。
- AnythingLLM：[README](https://github.com/Mintplex-Labs/anything-llm/blob/master/README.md)、[许可](https://github.com/Mintplex-Labs/anything-llm/blob/master/LICENSE)、[前端依赖](https://github.com/Mintplex-Labs/anything-llm/blob/master/frontend/package.json)、[服务依赖](https://github.com/Mintplex-Labs/anything-llm/blob/master/server/package.json)。
- Fractal Chat：[README](https://github.com/LeoLiao0806Xuan/Fractal_Chat/blob/master/README.md)、[许可](https://github.com/LeoLiao0806Xuan/Fractal_Chat/blob/master/LICENSE)、[密钥加密实现](https://github.com/LeoLiao0806Xuan/Fractal_Chat/blob/master/src/services/crypto.ts)。
- Prompt-Tree：[README](https://github.com/yxp934/Prompt-Tree/blob/main/README.md)、[实际包清单](https://github.com/yxp934/Prompt-Tree/blob/main/ai-chat-client/package.json)、[分支列表](https://github.com/yxp934/Prompt-Tree/blob/main/ai-chat-client/src/components/tree/BranchList.tsx)、[上下文组装服务](https://github.com/yxp934/Prompt-Tree/blob/main/ai-chat-client/src/lib/services/contextBoxService.ts)。
- React Flow：[README](https://github.com/xyflow/xyflow/blob/main/README.md)、[许可](https://github.com/xyflow/xyflow/blob/main/LICENSE)；assistant-ui：[README](https://github.com/assistant-ui/assistant-ui/blob/main/README.md)、[许可](https://github.com/assistant-ui/assistant-ui/blob/main/LICENSE)。

GitHub 匿名 API 后段触发限流，本轮对部分已知路径改用 raw 源文件读取。未取到许可正文或未运行的内容均保留限制，没有因此推断其验证通过。上述链接指向活动分支；真正引入前必须固定版本/提交并再次核对许可及依赖。

## 4. 三种实现方式的取舍

| 路线 | 优点 | 主要代价 | 判断 |
|---|---|---|---|
| **自建 React 页面 + 本机 Java 服务** | 有机会共享现有纯 Java 领域与树布局；模型请求不受浏览器 Provider CORS 限制；数据可落到电脑文件。 | Android SQLite、Keystore、生成协调与 AI 装配要做平台适配；增加前端与本机服务构建。 | **推荐**，最贴近现有业务的一致性要求。 |
| React 页面 + Node 本机服务 | 前后端 TypeScript，启动和前端生态接入较方便。 | 必须重写 Java 领域实现或额外保留 Java 进程，规则双写与状态漂移风险更高。 | Java 复用调研失败时的备选。 |
| fork LibreChat/其它完整产品 | 通用聊天、设置、附件等能较早获得。 | 需要重构学习树、版本、快照、恢复与数据模型，还要维护上游产品自带的部署与功能。 | 不推荐作为本项目首版路线。 |

纯网页 IndexedDB 方案可以快速预览，但浏览器数据生命周期、凭据和不同 Provider 的 CORS 支持会影响正式版；本次已有“启动本机服务”的目标，不建议将它作为全部业务状态的权威存储。

## 5. 建议的调整方案

### 5.1 产品和交付边界

- 首版交付一个电脑本地产品：服务就绪后打开 localhost 地址，网页、业务接口与流式事件同源；常规使用无需启动手机或模拟器。
- 目标是一条启动入口，最终发行包携带已构建页面，并评估附带 Java runtime，避免用户分别启动前端、后端和数据库。当前尚无该启动入口；端口和安装命令由实现配置确定。
- 单用户、本机单实例、仅绑定 loopback；不因新增本机服务顺带引入账号、公网部署、云数据库或自动同步。这些仍是本文建议边界，接受方案后写入正式需求。
- 手机和 Web 功能对齐以现行生产调用链为基准；未实现或未接受的手机候选功能不自动进入 Web 范围。

### 5.2 技术分层和数据

- `frontend/` 建立 React + TypeScript + Vite 的真实客户端；保留之前 `RuntimeAdapter` 思路，页面通过平台适配接口调用本机 HTTP/SSE，未来才增加 Android Bridge Adapter。
- 推荐使用 Java/Spring Boot 承载本机 API、静态页面、生成调度与持久化。先做最小可运行性调研，确认核心 JAR 接入、服务体积、启动耗时与发行方式，再固定依赖。
- `:core` 与 `:tree-layout` 优先共享同一源码或正式构件，不复制一份业务实现到新目录。先在普通 JVM 验证编译和行为，必要调整只限移除平台耦合。
- AI/SSE、生成协调及 repository 按实际调用链拆分平台端口。网络、日志、事务和密钥获取用桌面实现替换，状态规则继续共用；没有证据前不承诺整个 Android 模块都可复用。
- 推荐电脑本地 SQLite 保存业务状态与附件引用，附件落电脑数据目录，支持服务重启恢复；不把 React 内存或 localStorage 当作唯一事实源。
- **规则调整必须显式完成**：现行后端默认 MySQL、共享状态与服务日志规则并非为本机单实例 SQLite 产品制定。接受该路线后，先明确“仅本机、不可横向扩容”的适用范围、桌面 SQLite 的真实引擎验证和本地持久化要求；未经确认不通过换目录绕过规则，也不把桌面 SQLite 结果冒充 Android SQLite/MySQL 结果。
- Java 应用服务经明确的 AI Gateway 边界调用 Provider；Controller 与网页不散落直连模型请求。持久时间和传输时间继续使用 UTC epoch 毫秒，展示使用浏览器当前设备时区。

### 5.3 凭据、生成与多标签页

- Provider Key 由本机服务持有；Windows 首发评估系统凭据库/DPAPI，提供独立平台适配。网页录入时会短暂接触用户输入，但查询只返回脱敏状态，不把完整 Key 写入浏览器持久存储或日志。
- 持久生成操作继续区分生成中、稳定、失败、中断与取消。刷新网页先查询权威状态、再接续显示，不能把刷新当作重新发送；重启服务后按实际状态落定中断并允许原位重试。
- 提交使用持久操作标识与明确的去重语义；同一分支不能被两个标签页重复生成。标签页只是同一服务的多个视图，不各自成为业务权威。
- 浏览器流式订阅使用项目要求的统一客户端与认证流式方案；事件归属、快照边界、取消与重连在正式契约中定义并验证，本报告不先发明一份接口规范。
- loopback 服务仍校验 Host/Origin 与写入来源；不使用通配跨域，不允许网页端控制任意系统命令或文件路径。日志只记录必要状态与脱敏计数，不记录聊天、推理、文件正文或 Key。

### 5.4 Web 交互调整

| 手机版能力 | Web 建议交互 | 必须保持的业务语义 |
|---|---|---|
| 首页、学习库、导航抽屉 | 左侧常驻主题/学习库导航和全局搜索；窄窗口折叠。首页保留发起主题、继续学习入口。 | 主题身份、排序、归档和未读以业务状态为准。 |
| 对话和来源卡片 | 中间专注阅读当前分支；顶部面包屑显示来源路径；来源可打开侧栏或返回定位。 | 普通追问不增枝，返回不丢阅读位置与草稿。 |
| 创建子分支、推荐追问 | 消息动作直接分叉；鼠标选中文本后出现浮动动作条；创建后支持当前工作区打开或旁栏对照。 | 只对稳定回答分叉，冻结创建上下文；草稿与选中文本不能被推荐动作误清。 |
| 树总览 | 可展开的大画布；支持鼠标/触控板平移缩放、定位当前节点、键盘导航和点击回对话。 | 树关系来自业务数据；禁止随意拖线更改父子关系。 |
| 高亮与批注 | 选中文本出现复制、引用、分叉、高亮、批注；点击高亮打开侧边批注面板；操作也有键盘/显式菜单入口。 | 按回答版本、规范文本片段与区间保存锚点，不持久化 DOM 路径；验证 Markdown/公式、重复文本、中文与 emoji 偏移。 |
| 消息富内容与生成反馈 | Markdown、代码复制、表格、公式沿用能力边界；思考可折叠；生成时可停止，用户上翻时暂停自动跟随。 | 部分回答可读但不可分叉；不因重渲染丢选区或抢滚动。 |
| 输入和附件 | 多行输入；键盘快捷操作兼容中文输入法；文件选择、拖入附件，支持能力范围内的粘贴。 | 语音识别追加草稿而不自动发送；附件生命周期、校验和清理与业务规则一致。 |
| Provider、模型、人设、搜索、文件与统计 | 常驻设置入口，主从式设置页；保留已有配置和状态反馈，避免照搬手机全屏层层跳转。 | 不扩大 Provider 协议范围、不暴露完整 Key；操作成功必须来自服务确认。 |
| 朗读和语音输入 | 朗读优先评估浏览器语音合成；识别按浏览器能力/明确适配的服务实现，显示麦克风授权和可用状态。 | Web Speech API 的覆盖和网络行为不能默认等同 Android 系统服务；语音未经目标浏览器实测不能报功能完全一致。 |

视觉继续使用知芽的纸感、生长绿与现有设计 token。大屏增加分栏与密度档位时走 token 的既有权威修改顺序，不硬编码一套新的颜色尺寸。

### 5.5 和手机版的功能一致性清单

先建立“手机版实际入口 → Web 入口 → 共用规则 → 平台差异 → 验收场景”的矩阵，至少覆盖：主题创建、分支与来源、连续问答、多 Provider/模型、人设、思考、版本、停止/重试/重置、上下文压缩、推荐问题、引用与复制、高亮批注、翻译/朗读/语音输入、附件和文件管理、应用内搜索与联网搜索、归档恢复/删除、未读与阅读位置、统计及设置。

其中某项存在代码不代表所有路径都通过；矩阵应由生产 producer/consumer 和运行结果共同核对。Web 首版完成标准是整张范围内矩阵的闭环，不能拿一个聊天 Demo 当“手机版功能一致”。

## 6. 建议推进顺序与验收

这是一组交付阶段建议，不是 Harness Task 清单；本轮未创建任务。

1. **范围与现状矩阵**：确定 Web 与手机的数据关联方式，确认 Web 先行、本机服务及平台语音差异的口径；盘点实际功能，更新新需求与当前架构边界。先前 Android WebView 意图保留为历史决策来源。
2. **Web 交互稿与运行可行性调研**：产出首页/学习库、对话工作区、树总览、设置的桌面交互稿；并验证共享 Java 核心、桌面存储与密钥适配的最小可行链路。
3. **完整核心纵向链路**：localhost 冷启动 → 配置 Provider → 创建主题 → 流式回答 → 从稳定回答分叉 → 返回来源 → 刷新与重启恢复。先打通真实持久化，再扩充页面。
4. **补齐功能矩阵**：学习库/搜索/归档、模型/人设/搜索设置、树画布、阅读与批注、文件、语音、统计；每项同时覆盖正常、失败和恢复状态。
5. **发行与一致性验收**：形成单一启动入口和数据目录说明；真实目标浏览器测试键盘、鼠标、缩放、多标签页和异常中断；全部范围内项目通过后，才称为 Web 首版完成。Android 共享代码发生修改时同时补齐 Android 回归与 Java-only 守门。

验收应包括：首次启动和服务不可用反馈；关页/刷新不重复提交；服务停止后已有数据保留；中断恢复；多标签页竞争；归档与删除原子性；高亮批注在重开/版本切换后定位正确；目标浏览器语音/文件权限路径；实际桌面数据库新装、升级、回滚、重开与完整性；密钥不进入日志/导出/浏览器持久化；中英文文案和设备时区；实际浏览器与服务启动证据。

尚未有实測工作量基础，不承诺工期或复用比例。主要成本集中在生成/存储平台适配、批注锚点、树交互及语音兼容，页面数量本身不是充分的估算依据。

## 7. 待确定与文档影响记录

当前需要用户选择的实质问题是 **Web 与手机如何关联数据**：建议两端各自本地保存，另行确认版本化导入/导出；自动同步会增加设备身份、冲突合并、断线恢复和密钥归属等设计，不从“功能一致”推导出来。若选择自动同步，本报告的数据与部署部分需要重写。

本轮只新增本调研与建议报告，没有改生产代码、当前 API 契约、设计 token、构建配置或启动行为。没有安装新依赖、生成执行 Task 或自动提交。

已核对文档面：根与 Android README、项目背景包、现行 Android 架构、2026-10-01 intent/spec、旧 RikkaHub 吸收矩阵。反向搜索词为 `Web 桌面端`、`无服务端`、`浏览器正式端`、`Mock Runtime`、`frontend` 和 `WebView`；既有文档仍描述旧边界，不能静默改为“Web 已交付”。接受新方案后再同步当前态，保留带日期材料的历史解释。

实现前需同步的权威面：新 Web 需求与规格、Android/桌面共享架构、loopback 服务治理适用范围、桌面数据库与凭据边界、功能一致性矩阵；实现时再新增唯一 API/SSE 契约与前后端消费者验证、模块 README、安装/启动/数据目录文档及适用 guards。
