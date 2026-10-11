# 知芽 Web 本地版：AI 开发交接入口

日期：2026-10-10。用户会将本文件与仓库交给另一 AI 执行开发。本轮只交付设计输入，尚未建立生产 Web 工程。

> **后续状态（2026-10-10 晚，与本文同日的执行进展）**：`web/` 已按用户要求建立并在推进中——`web/backend`（Spring Boot 4.1.1、loopback 8733、ready 端点）、`web/frontend`（Vite+React 骨架、i18n 基建、毛玻璃设计系统与 icon-only 按钮）、`web/shared`（`:core`/`:tree-layout` 迁入的唯一实现，Android 与 Web 两端 projectDir 引用）。启动/停止与守卫的权威说明见 `web/README.md`；本文下方「目录」描述的是设计交付当时的目标，实际路径以仓库为准。

## 已确认的决定

- 启动本机服务后通过 localhost 使用；两端数据不同步。
- Web 与活动手机版功能保持一致；交互适配键盘、鼠标、大屏。
- A 专注阅读默认，B 并排分支与 C 树图探索可切换；右侧分支/来源/批注按需展开。
- 用户截图的薄荷雾面 Web 配色，v1.1 原型图标，huashu-design 辅助设计。
- 本机 Java 服务与 React/TypeScript 是推荐路线；共享 Java 与 SQLite/系统凭据可行性须真实验证。

## 阅读顺序

1. 仓库 `AGENTS.md` 与按任务触发的 rules；没有用户 Harness 选择时，不把这份设计文档当作已创建的实现 Task。
2. [已确认意图](../intent/INTENT-2026-10-10-local-web-edition.md)与[规格](../spec/SPEC-2026-10-10-local-web-edition.md)。
3. [功能一致性矩阵](TreeLearn_Web本地版_功能一致性矩阵.md)，沿每行实际 producer/consumer 复核。
4. [详细开发方案](TreeLearn_Web本地版_详细开发方案_V1.0.md)。
5. [可离线打开的 HTML](../04-ux-design/知芽-Web本地版-交互原型-V0.1.html)、[Web token](../04-ux-design/web-local-design-tokens.json)、[原型说明](../04-ux-design/知芽-Web本地版-原型说明-V0.1.md)。
6. [原调研](TreeLearn_Web本地版开源调研与调整方案_2026-10-10.md)仅作候选来源；确认事项以新意图与本规格为准。

## 可以直接交给开发 AI 的提示词

> 请为知芽（TreeLearn）实现电脑本地 Web 版。先阅读本仓库 AGENTS.md，并按需读取 rules。产品目标与范围见 INTENT-2026-10-10-local-web-edition，交互见 SPEC-2026-10-10-local-web-edition、HTML 原型和 Web token 文件，详细实现输入见 TreeLearn_Web本地版_详细开发方案_V1.0，功能验收见一致性矩阵。
>
> 两端数据不同步；启动本机服务后访问 localhost，普通使用不依赖手机或模拟器。A 专注阅读默认，B/C 是辅助视图；使用已指定薄荷雾面配色和 v1.1 图标。功能规则以活动 Android 生产调用链为基准，不能照搬原型模拟生成、模拟保存或虚构的人设/文件格式。
>
> 优先复用纯 Java 领域与树布局，并建立本机 Java/Spring Boot 服务和 React/TypeScript 客户端。先验证 JVM 构件、生成协调平台端口、桌面存储与凭据，再定依赖和唯一 API/SSE 契约。不要让 Controller/页面散落直连 Provider，不在浏览器持久化 Key。不要增加账户、同步、公网、云数据库或 Agent/RAG 范围。
>
> 按项目规则确认工程流程，选择 Harness 后才建正式父/子 Task。桌面 SQLite 永久表准入需要明确确认与真实引擎证据；契约、数据库、Android 变更分别走对应 guard。现有 Android UI 保留；共享逻辑改变要补 Android 回归与 Java-only 检查。
>
> 按“构件与运行边界 → 存储/凭据 → 契约 → 生成/分叉 → 页面闭环 → 功能矩阵补齐 → 发行”推进。每项必须给出真实测试与启动证据；优先跑真实垂直链路，再扩大。已确认的目标不反复询问，工程中遇到会改变范围、安全、数据归属或验收的实质问题才带建议回给用户。
>
> 最终报告逐行功能对齐、实际 localhost 启动入口、数据目录、已验证浏览器/设备、失败/重建证据和仍未验证项。不能把 UI Demo、成功 Toast、编译、mock 或历史任务状态当作功能一致完成。

## 文件与环境说明

- HTML 不需要联网下载框架，双击即可打开。当前交互伴侣是临时设计展示服务，地址和存活时间不是未来产品端口承诺。
- 本地开源参考分别在 `D:/Project/Android/LibreChat-main/LibreChat-main`、`D:/Project/Android/anything-llm-master/anything-llm-master`、`D:/Project/Android/xyflow-main/xyflow-main`；实际选用源码/包还需固定版本、许可与 NOTICE。
- 图标路径复用 v1.1 的轮廓，品牌标志来自既有项目；用户配色参考图已保存在 Web 设计 assets 中。
- 本轮没有正式 API/SSE 契约、生产 SQL、依赖锁或正式启动脚本。文档中的目录和用例组是实现目标，开发应先形成唯一权威契约，而不是同时维护多份协议。
