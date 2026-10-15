# Intent: Android 渐进式迁移到可复用 Web UI

id: INTENT-2026-10-01-progressive-web-ui
title: Android 渐进式迁移到可复用 Web UI
date: 2026-10-01
author: 用户
origin: 用户确认的架构方向
status: accepted

## Problem（问题与背景）

- 问题/背景：当前 TreeLearn APK 的页面由 Android XML/View 与 Java 实现。若后续需要网页或其他终端，页面与交互需要另行实现，存在重复开发和两端行为逐渐不一致的风险；该后果是架构推断，尚无实际跨端用户数据。
- 用户/场景：当前使用 Android APK 的单人开发者，希望后续能够复用页面与交互实现；浏览器正式产品及其它平台用户不属于本次交付对象。

## Users and scenarios（用户与场景）

- 当前用户继续通过 APK 使用知芽（TreeLearn）。页面迁移按功能渐进进行，未迁移页面仍由现有原生界面提供。
- 后续实现者可在独立平台适配层上复用已迁移的 React 页面；该能力是扩展准备，不代表本次提供浏览器端产品。

## Expected outcome（预期结果）

- 预期结果：Android APK 中逐步由 React + TypeScript 页面承载可共享的界面；Android 本地 Java 能力通过有版本的桥接契约提供给页面；未迁移页面可继续使用现有原生界面并能回退。
- 数据与提供商密钥继续留在 Android 本机。后续浏览器或其他平台必须另行设计并实现自己的存储、凭据及平台能力适配，不得假定 Android 桥接代码可以直接复用。

## Core flow（核心流程）

- 核心流程（含中断、撤销、重试）：用户打开 APK 后由 Android 宿主加载包内 Web 页面；页面发起查询或命令，由 Java 适配层调用既有应用能力并返回 DTO；迁移中的 Web 页面出现故障时可回到对应原生页面。生成中断或 WebView 重建后，页面先查询 Android 权威状态，再允许用户继续、重试或重新发起操作。
- 输入/输出：Web 页面发送版本化 JSON 请求；Android 返回 DTO、稳定错误类别与生成状态事件。不得向 JavaScript 提供 API Key、数据库对象、Android Context 或未经筛选的异常。
- 状态与异常：Android SQLite 与现有生成状态机继续作为业务状态权威；桥接版本不兼容、能力不可用、请求格式错误、页面离线资源缺失时失败关闭并保留原生回退路径。

## Constraints and non-goals（约束与非目标）

- 约束：Android 保持 Java-only；继续本地优先；API Key 继续由 Android Keystore 保护；不修改业务数据归属；采用渐进迁移；生产 WebView 加载 APK 内置资源；遵循现有设计 token、平板横屏和触达区域规范。
- 权衡偏好：优先保留 SQLite、Keystore、AI/SSE、生成协调、文件与语音等已工作的 Android 本地能力，以版本化桥接复用页面；接受迁移期原生与 Web 页面并存及前端构建流程增加的维护成本。
- 非目标（non-goals）：本次不交付浏览器正式客户端，不实现浏览器本地存储或浏览器 AI 凭据流，不新增 Spring Boot、账号、云同步、远程页面加载或远程遥测，不一次性重写或删除全部原生页面，不以 Web UI 替换 Android 专属系统能力。

## Open questions（开放问题）

- 已确认事实：用户于 2026-10-01 选择方案 B，并确认覆盖此前“Web 桌面端暂缓”的方向限制；本次只批准 Android 先行的渐进式可复用 UI，不批准服务端、浏览器正式端、账号或同步。
- 假设：共同页面的复用会减少未来重复实现；本次没有成本基线或跨端使用数据，不能承诺具体节省比例。
- 未知项：具体首批页面、AndroidX WebKit 依赖版本与设备覆盖、前端构建环境和 APK 体积影响，需在技术规格评审及实现调研中验证。

## Convergence verdict（收敛判定）

### 有价值吗

结论：是，价值方向已由用户确认；量化收益仍未知。
证据：用户希望后续扩展网页或其他端口；当前 Android 页面由原生 XML/View 与 Java 构成，尚无可复用的 Web UI 实现。不迁移时，未来每增加一个终端都需要独立实现界面，存在重复劳动和体验漂移风险；这是可由代码结构支持的推断，不是用户损失统计。

### 值得做吗

结论：是，按渐进方式开始；浏览器正式端不在本次范围。
证据：用户明确选择方案 B。渐进迁移保留当前 APK 与本地数据能力，能先验证页面复用边界；相比全量重写，成本与回归风险更可控。机会成本是短期需要维护原生与 Web 两套页面，并投入前端构建和桥接治理。

### 可以做吗

结论：是，但必须通过 Android 适配层逐页迁移，不能直接把现有 Java 对象暴露给网页。
证据：当前代码已有 `:core`、`:data`、`:ai`、`:tree-layout` 模块、应用容器、数据接口与生成协调器，可作为既有能力来源；Java-only、Keystore、SQLite 和本地优先是必须保留的约束。WebView 桥、构建集成、页面状态恢复及跨端协议均需新增并验证。

## Decision record（决策记录）

- 决策权归属：架构方向由用户决定；页面顺序、桥接细节与测试策略由工程实现者在本意图边界内细化，并提交人 review。
- 决策人：用户
- 决策日期：2026-10-01
- 终态：accepted
- 否决理由（status 为 rejected 时必填）：不适用。
- 验收标准与证据：Android APK 能从包内静态资源启动已迁移页面；至少一个页面完成 Web 与原生并行、可回退验证；Android 数据和密钥仍由原生能力持有；版本化桥接有请求/响应/错误/异步状态测试；生成流重载后从权威状态恢复；Java-only、依赖、构建、真机启动与安全边界检查通过。具体逐阶段验收见对应设计规格。
- 风险与后续范围：WebView 性能、输入法、无障碍、平板布局、原生桥接攻击面、Gradle 与 Node 工具链协同、APK 体积均需验证；浏览器正式端及其它平台需要单独决策存储、凭据、网络与隐私边界。

## Next step（下一步）

- accepted：移交 spec-author 完成可交给实现者 review 的技术规格；本轮建议切片顺序为前端工程与契约、Android WebView 宿主、低风险页面试点、学习库与导航、首页、聊天与生成流、原生专属能力边界、树画布评估。前端与 Android 桥接契约不可并行定稿；聊天生成与其它页面迁移应在桥接及状态恢复验证后进行；树画布最后评估。人接受规格后再交 requirement-decomposer 拆实现任务。

<!-- FIELDS: id | title | date | author | origin | status | Problem（问题与背景） | Users and scenarios（用户与场景） | Expected outcome（预期结果） | Core flow（核心流程） | Constraints and non-goals（约束与非目标） | Open questions（开放问题） | Convergence verdict（收敛判定） | Decision record（决策记录） | Next step（下一步） -->
