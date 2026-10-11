# Spec: Android 渐进式迁移到可复用 Web UI

> 范围历史说明（2026-10-10）：本文记录 Android WebView 先行的草案，当时不交付浏览器正式端。用户已另行确认 localhost Web 版、两端不同步与 Web 专注阅读布局，见 [新规格](SPEC-2026-10-10-local-web-edition.md)。不得用本文的旧非目标否决新需求，也不得把旧草案当成已经实现的 Web 工程。

id: SPEC-2026-10-01-progressive-web-ui
title: Android 渐进式迁移到可复用 Web UI
date: 2026-10-01
intent: Docs/intent/INTENT-2026-10-01-progressive-web-ui.md
author: spec-author
status: draft

## Requirement spec（需求规格）

- 范围：在 APK 内新增 React + TypeScript 前端，由 Android WebView 承载；通过版本化原生桥接调用现有 Android 本地应用能力；以逐页并行与回退方式迁移可共享页面。第一试点建议选择设置入口页（只含导航，不触碰密钥编辑），再迁移学习库与搜索；该顺序可在实现调研后由 owner 调整。
- 验收方向：包内资源可离线启动；同一 React 页面可在浏览器开发预览中用 Mock Runtime Adapter 运行；Android 桥接请求受版本、方法和参数校验；未迁移原生页面仍能访问；迁移页面的数据与原生权威来源一致；生成状态在页面重建后从 Android 恢复；回退与异常不丢失当前业务数据。
- 非目标：不交付浏览器正式客户端或其它平台包；不新增后端、账号、同步或远程遥测；不迁移本地 SQLite、Keystore、生成/SSE 的权威实现到 JavaScript；不一次性重写或删除所有原生页面；不将 API Key、Android Context、数据库句柄或原始异常暴露给 JavaScript。
- 边界覆盖：本规格依用户 2026-10-01 决策，限定覆盖此前“Web 桌面端暂缓”和“不引入 React”的方向限制；不覆盖无服务端、本地优先、Java-only、密钥保护等不变项。
- 来源：`Docs/intent/INTENT-2026-10-01-progressive-web-ui.md` 的 Problem、Expected outcome、Core flow、Constraints and non-goals、Decision record；当前 Android 模块边界见 `Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md` §3、§14、§17。

## Interface plan（界面方案）

- 信息架构：初期保留 `MainActivity` 作为单 Activity 与生命周期宿主；WebView 作为可交换页面进入现有内容容器。迁移试点先保留现有底部 Tab 和未迁移原生页面。主页、学习库、设置等通用页面逐步迁移后，React 再接管这些共同页面的路由与导航外壳；Android 仍管理系统返回、权限及原生专属能力切换。
- 入口位置：调试和首轮发布通过按页面配置的迁移策略进入 Web 页面；未启用或 Web 页面加载失败时回到对应原生页面。不得依赖远程开关服务。试点稳定后逐页面改变默认承载方式，旧页面保留到行为等价与回退验收结束。
- 状态与异常：Web 页面具有加载中、可用、桥接不兼容、资源加载失败、能力调用失败及原生回退状态。生成页面另有空闲、提交、推理流、回答流、稳定、失败、中断、取消状态；用户可见文案由前端 i18n 提供，业务状态只由 Android 返回。WebView 重建后先取快照，再恢复订阅，不能把内存中的 React 状态当作已持久化事实。
- 数量与呈现方式：本规格不重做视觉稿，不新增布局尺寸或色值；迁移页面保持现有信息结构与 `Docs/04-ux-design/design-system/tokens.json` 中已定义 token 的视觉语义。手机与平板横屏都需覆盖；触控热区继续满足项目现有基线。Web 开发预览使用 Mock Runtime Adapter，不等同浏览器正式端。
- 来源：用户批准的渐进迁移范围；现有 `MainActivity`、`activity_main.xml`、`HomeScreen`、`LibraryScreen`、`SettingsScreen` 路由与内容容器；设计依据 `Docs/04-ux-design/design-system/README.md`、`tokens.json` 与项目 UX 规则。

## Technical spec（技术规格）

### 目标分层

- `frontend/`：React + TypeScript 静态 UI、路由、i18n、页面 ViewState 投影及 `RuntimeAdapter` 接口。组件只调用应用用例或 Adapter，不直接访问原生对象、SQLite、HTTP、Key 或系统能力。初期实现 `MockRuntimeAdapter` 供普通浏览器开发预览，以及 `AndroidRuntimeAdapter` 供 APK；不实现真实 Browser Runtime、浏览器持久化或网页端 AI Key 流。
- Android Web 宿主：在现有单 Activity 中增加 Java Web 页面容器、WebView 生命周期控制、本地资源加载、桥接路由与 DTO 编解码。将桥接能力封装在 `WebApplicationFacade` 一类应用边界中；不得将 `Activity`、`Context`、View、Cursor、DAO 或 Presenter 直接序列化或暴露给 JavaScript。
- 既有能力：继续以 Android `AppContainer`、既有应用用例/Store、`:core` 领域状态、`:data` SQLite 与 Keystore、`:ai` Provider/SSE、`GenerationCoordinator` 为权威实现。优先通过已有接口接入；只有当前职责被 UI Presenter 捆绑、无法安全调用时才做最小抽取，禁止为 WebView 重写业务规则。
- 树布局：第一阶段保留原生 `TreeCanvasScreen`/Canvas，不通过桥接逐帧传输触控。后续单独评估以现有 `:tree-layout` 计算结果作为基准，让 Web 用 SVG/Canvas 绘制；只有 golden fixtures 覆盖和设备对比通过后才考虑移植布局算法。

### 前端与 APK 构建

- `frontend/` 使用 React、TypeScript、Vite 与仓库统一的 pnpm 工具链；确切版本由实现任务核对 Node/pnpm 支持、锁文件、许可证、漏洞、包体积及 Java-only 约束后固定，不在规格阶段猜版本。
- Android APK 只打包前端静态构建产物，不在运行时连接开发服务器或 CDN。推荐 Gradle 生成任务在前端产出 `dist` 后复制到 `android/app/build/generated/...` 并加入 assets source set；生成文件不作为第二份手工维护的源码。Gradle、Android README 与根 README 必须记录 Node/pnpm 版本、安装方式、构建入口和失败诊断。
- 前端 i18n 与 UI copy guard 必须覆盖 `frontend/`；样式映射复用现有 design tokens。Token 的唯一权威仍是原型 `:root` 区域，`tokens.json` 是既有机器镜像；如需 CSS token 生成，采用可重复生成并带漂移校验的路径，不另行手抄设计数值。
- 开发浏览器预览仅连接 Mock Runtime，不接生产 Key 或真实用户数据库。APK 集成验收使用打包后的本地资源，不以 Vite dev server 替代。

### WebView 资源与桥安全

- 用 AndroidX WebKit `WebViewAssetLoader` 从 APK assets 提供 HTTPS 风格的同源本地 URL；不要用 `file://`、远程部署页面或 `loadData()` 承载生产入口。固定并校验本应用的本地 origin；所有非本地导航拦截，不允许页面跳转后保留原生权限。
- 优先使用 AndroidX WebKit `WebViewCompat.addWebMessageListener`，把 allowed origin 精确限制到本地 assets origin，并在加载页面前注册。每次启动检查 `WebViewFeature.WEB_MESSAGE_LISTENER`；功能不可用时关闭桥接并显示原生回退，禁止静默降级到更宽松的桥接方式。添加 AndroidX WebKit 依赖前必须核对当前稳定版本、minSdk、传递依赖 Kotlin 字节码与 APK 体积，并通过项目 Java-only guard 的最终 dex 检查。
- WebView 仅对包内可信 UI 启用 JavaScript；禁用 file/content 任意访问和混合内容；限制导航、外链、下载、弹窗及新窗口；生产关闭 Web 内容调试。通过内容安全策略禁止远端脚本、未授权连接与内联可执行脚本，避免将未转义回答内容拼入 HTML。
- 原生桥只暴露消息发送/回复能力，不暴露任意反射、类名、方法名或通用 Java 调用。Java 侧按方法 allowlist、协议版本、请求大小、字段类型、ID 格式与业务权限逐项校验；未知方法、版本或畸形内容一律失败关闭。不得记录请求正文、回答正文、推理、API Key、完整 Prompt 或文件内容。
- Android 官方文档是 WebView 资源加载、消息监听和桥安全 API 的核对来源；实现任务开始时必须再次核对当前 AndroidX WebKit 版本、目标 WebView provider 覆盖与设备行为，不能仅凭本规格中的类名假定可用。

### Bridge v1 契约

- 单一契约事实源放在 `contracts/android-bridge-v1.md`；请求、响应、事件样例和错误表均由该文件维护。Java 与 TypeScript 代码各自实现解析和类型，但通过同一组 JSON fixture 做双向一致性测试；不得在 README、前端和 Java 注释中复制第二份规范正文。
- 请求 envelope：`protocol` 固定为 `treelearn.android-bridge`，`version` 为整数，`requestId` 为本桥接会话内唯一字符串，`method` 为 allowlist 项，`params` 为对象。
- 响应 envelope：回显 `requestId`，并且恰有 `ok=true,result` 或 `ok=false,error`。错误只含稳定 `code`、可选安全参数与 `retryable`；用户文案使用 `messageKey` 在前端 i18n 映射，不显示 Java 异常原文。
- 事件 envelope：`eventId`、`operationId`、进程内递增 `sequence`、`type`、`payload`。第一阶段只在需要时开启生成事件，不支持任意 topic/channel 订阅。
- 初始方法集：`app.handshake`、`app.getCapabilities`、`settings.getSafeSnapshot`、`library.getSnapshot`、`library.search`、`navigation.open`。聊天阶段再增加 `branch.getSnapshot`、`generation.start`、`generation.getState`、`generation.cancel`、`generation.retry`、`generation.reset`。每方法的字段、错误和 DTO 在契约文件中逐项定义后才可实现。
- DTO 必须是无平台类型的 JSON 值：字符串 ID、有限枚举、布尔值、毫秒时间戳、列表与对象；不得传输 Java 序列化对象、SQLite 行游标或需要前端猜测单位的时间。
- 桥接命令只调用既有业务端口；如接口不存在，先新增面向用例的窄接口，再实现 Android Adapter。Bridge 本身不决定创建、分叉、归档、稳定回答、Key 回退等领域语义。

### 异步生成与恢复

- `generation.start` 输入现有聊天命令所需的 branch/question/model/thinking 参数；首个主题创建必须复用现有 `submitFirstTopic` 原子接受路径，不允许 Web 层分步创建空主题。
- `generation.start` 的客户端只在明确用户动作时发送一次。传输结果不确定或 WebView 重建时，禁止盲目重发非幂等命令；先按 branch/operation 查询 Android 权威快照，再决定继续显示、调用原位 retry 或等待用户再次提交。桥接 requestId 的去重范围如不能跨进程保证，必须明确标为会话级，不得宣称持久幂等。
- 生成事件包括 `generation.delta`、`generation.stable`、`generation.failed`、`generation.interrupted` 与 `generation.canceled`。每事件有操作 ID 与序号；只有 Android 状态机确认稳定后才能启用分叉。
- 前端先订阅，再读取 Android 快照；按响应定义的快照/事件边界丢弃已包含的旧事件并应用后续事件。桥接不承诺事件持久重放；重连后由 `generation.getState` 返回最新已落盘部分内容与真实状态。尚未持久化的 UI 暂态不得冒充恢复成功。
- 回答和推理内容是 UI 必需数据，可以经本地桥送达，但不得写入桥接日志、崩溃诊断或遥测。API Key 永不经 Bridge；Key 输入、读取、替换继续走原生安全界面与 Keystore。

### 迁移步骤与依赖

1. **边界与清单**：实现前更新当前架构权威，明确新增 React/WebView 与保留的本地/Java-only 边界；盘点每屏页面、Presenter、Store、路由、系统能力和回退入口。确认页面顺序、WebView 最低能力、Node/pnpm 与设备矩阵。未核实项记录为风险，不预先添加依赖。
2. **Bridge 契约**：编写唯一的 `contracts/android-bridge-v1.md`，先定义 handshake、capabilities、请求响应、错误与 DTO；准备 Java/TypeScript 共用 fixture。契约未确认前，前后端桥接实现不可并行开工。
3. **前端骨架**：新增 `frontend/` React + TypeScript + Vite、pnpm 锁文件、i18n、RuntimeAdapter、MockRuntimeAdapter 和浏览器预览。通过前端 build、lint、i18n guard；此阶段不接真实数据或 Key。
4. **Android 宿主**：增加 Java WebView host、包内 assets 生成/打包任务、AssetLoader、严格 origin 桥、页面策略与原生回退。初始 Web 入口限调试或显式测试开关，原生仍为默认。验证断网启动、导航拦截、桥接握手、生命周期与返回键。
5. **首个低风险试点**：建议设置入口页，先迁导航清单并保持密钥、Provider 编辑和系统设置子页原生；实现 capability/read-safe snapshot 与 native navigation。验证用户路径和错误回退后才将该页默认切到 Web。
6. **学习库与搜索**：迁移列表、空态、搜索和打开分支；调用现有 Library/Navigation/Reading 数据端口，不复制排序、归档或未读业务规则。验证结果一致、返回栈、平板横屏和较大数据集。
7. **首页**：分开迁只读历史/推荐显示、草稿与输入交互，再迁发送入口。生成仍由 Android 原子提交和 `GenerationCoordinator` 执行；无可用 Key、重复点击、取消与首主题创建语义保持既有行为。
8. **聊天与生成**：先迁移静态消息呈现、引用与阅读状态，再接流式事件、推理折叠、取消、失败、重试、重置和重建恢复。对稳定回答/部分回答、旧 revision、子分支来源、事件乱序/重复/丢失分别做回归。聊天全量迁移前原生聊天页必须仍可用。
9. **原生专属能力评估**：文件选取、语音/TTS、Key 管理与树画布先保持原生；仅在需求明确且桥接 DTO、权限、取消语义安全后，逐项接入。树画布独立建立 Java layout golden fixture 和 SVG 对齐验收，不与聊天迁移同批。
10. **逐页切流与清理**：每页达标后单独调整默认页面策略，保留至少一个可测试原生回退入口；只有任务证明无路由、深链、返回路径、测试或其他消费者依赖时，才另行删除旧布局或 Java 页面。
11. **浏览器正式端后续**：单独立项定义浏览器数据持久化、密钥保护、Provider CORS/代理、安全威胁、备份/导出与发布形态，再实现 Browser Runtime Adapter。React 组件可复用不等于业务能力或数据可直接跨端复用。

### 测试与完成门槛

- 前端：生产 build、lint、user-copy-i18n；Adapter/DTO 测试覆盖成功、错误、未知能力、序列化和重复/乱序事件；i18n 至少覆盖 `zh-CN` 与 `en-US`。
- Android：Java-only guard；本地资源加载和 origin 安全 instrumentation；桥接协议 fixture 与 Java 解析测试；WebView 销毁/重建、返回键、屏幕旋转和外链拦截测试；现有相关原生单元与 instrumentation 回归。
- 集成：在 API 36 模拟器或真机安装启动 APK，实际证明离线冷启动显示初始页面、前端 bundle 与桥接正常；代表性 Android WebView provider 上验证 `WEB_MESSAGE_LISTENER` 能力。不以编译或浏览器 Mock 预览替代设备启动证据。
- 生成链路：真实测试配置下验证发送、流式增量、推理、稳定、失败、中断、取消、原位重试或重置、重复命令与 WebView 重建恢复；测试证据不包含问题、回答、推理、Prompt 或 Key 内容。
- 构建文档：`frontend/README.md`、根 `README.md` 与 `android/README.md` 同步安装、构建、打包、启动及排障入口；变更包含 Node/pnpm 前置条件与 Android 设备启动证据。
- 变更治理：逐页执行小型垂直切片，优先遵守每个执行任务 200 行生产代码目标；桥接契约触发 contract-guardian，Android 改动触发 Java-only guard，依赖改变须核实完整 APK/dex；生产行为、启动和文档同步按项目相应规则验证。不得将多个高风险页面并入一个大任务。
- 观测决定：本地优先范围内不新增远程行为遥测；必要诊断只记录操作 ID、状态、错误分类与耗时，不记录用户内容、Key 或 Prompt。
- 完成定义：每一迁移页面均有 Web 与原生等价或回退证据；Bridge 版本、方法与错误契约一致；状态恢复不依赖 React 内存；生产 APK 无远程 UI 依赖；既有 Android 本地数据、Key 边界、Java-only 和业务规则保持不变。

### 交给实现者的开工提示

先阅读 `AGENTS.md`、本 intent、本规格及本次路径命中的 `.trae/rules/`。这份规格是设计输入，不是已批准的任务清单；先让用户 review 并接受，再由 requirement-decomposer 创建执行任务。接受后，按本规格的依赖顺序切成小型垂直任务；不可先写业务代码再补桥接契约。实现者不得擅自把浏览器正式端、后端、账号或同步加进范围。

## Doubts（疑虑点）

- 疑虑 1：现行 Android 本地优先技术方案仍声明不建设 React/Web；已确认的 2026-10-01 用户决策覆盖这一项，但当前权威技术方案、项目背景包和历史评估尚未同步更新（冲突对象：`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md`、`.trae/context/project-brief.md`、`Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md`；两种走法：接受本规格后通过后续文档变更统一更新当前态并将历史评估显式标为历史，或否决迁移并维持原边界；谁拍板：用户）。
- 疑虑 2：AndroidX WebKit 的版本、目标 WebView provider 覆盖及其传递依赖尚未核实（冲突对象：Android Java-only、minSdk 与运行设备矩阵；两种走法：选择支持所需 WebMessageListener 的依赖并通过 dex/设备验证，或暂停 Web 页面默认启用直到有安全兼容方案；谁拍板：工程实现者提出证据，用户接受风险）。
- 疑虑 3：浏览器正式端的本地数据与密钥保护方式未知（冲突对象：Android Keystore 与 SQLite 本地边界；两种走法：未来浏览器独立本地库或凭据方案，或另行建设获批后端；谁拍板：未来单独需求决策，本规格不预判）。
