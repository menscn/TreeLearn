# 知芽（TreeLearn）RikkaHub 开源方案吸收评估与方案

> 版本：V0.1（draft，待拍板）
> 日期：2026-09-23
> 状态：分析结论，未经用户确认不进入实施
> 结论可信范围：基于对 `D:\Project\Android\rikkahub-master` 目录结构与关键文件的静态考察，未编译运行 rikkahub。

## 一、结论

**「把 RikkaHub 完整吸收为知芽基座」不可行；「分层吸收」可行且收益高。**

不可行的原因（三条硬冲突，均有可核查依据）：

1. **许可证冲突**：RikkaHub 采用 AGPL-3.0（`rikkahub-master/LICENSE` 首行、`README.md` License 节）。一旦把它的代码复制进知芽并分发 APK，知芽整个应用必须按 AGPL-3.0 开源并提供完整源码。知芽是否接受这一义务是上游决定，未确认前不能复制任何代码。
2. **技术栈冲突**：RikkaHub 是 Kotlin + Jetpack Compose + Koin + Room + DataStore + Okhttp（`README.md` Development 节，settings.gradle.kts 共 13 个模块）。知芽是 Java-only 硬约束（`.codex/context/project-brief.md`「关键约束」：不写 Kotlin 源码、不加 Kotlin 插件、不引 Kotlin 坐标），且已有 207 个 Java 生产文件、5 个模块（`:app`/`:core`/`:data`/`:ai`/`:tree-layout`）和《Android本地优先技术架构方案_V1.0》。整库吸收等于推翻已交付的 V1。
3. **产品形态冲突**：RikkaHub 是线性会话的通用 LLM 聊天客户端（多 Provider、云生态、依赖 google-services.json 构建）。知芽的核心差异化是树状学习分支与本地优先（数据与 Key 只留本机）。拿通用聊天骨架做基座，核心差异化反而要塞进别人的骨架里重做。

## 二、RikkaHub 资产盘点（实测）

| 资产 | 证据（来源） | 对知芽的价值 |
|---|---|---|
| 聊天主界面、流式输出、消息操作 | `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/`、`ui/components/message/` | 高：交互范式直接可借鉴 |
| 消息分支（Message Branching） | `README.md` Features 节「🪾 Message Branching」 | 高：与知芽树状分支直接同题，其交互与数据模型值得研究 |
| Markdown 富文本渲染（代码高亮/LaTeX/表格/Mermaid） | 独立模块 `:highlight`、`ui/components/richtext/`、`ui/components/table/` | 高：知芽 AI 回答展示的硬需求 |
| 多 Provider 抽象（openai/claude/google）+ SSE 流 | `ai/src/main/java/me/rerere/ai/provider/providers/`、`me/rerere/ai/stream/`（38 个文件） | 中：知芽只用 DeepSeek，抽象层可参考思路，不需要多 Provider |
| Agent/助手人设管理 | `ui/pages/assistant/` | 中：知芽已有 AI 人设需求（`Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`） |
| 会话历史/收藏/搜索/备份 | `ui/pages/history/`、`favorite/`、`search/`、`backup/` | 中：知芽 V3.1 范围外，属远期 |
| MCP、工作区（proot Linux）、翻译、图片生成、Web 服务 | `README.md` Features 节 | 低：与知芽单机定位无关，明确不吸收 |
| 数据层（Room/DataStore/Firebase） | `data/` 包 | 不吸收：与 SQLiteOpenHelper + 本地优先约束冲突 |

代码规模：app 模块 404 个 Kotlin 文件，ai 模块 38 个（`find … -name "*.kt" | wc -l`，2026-09-23 实测）。

## 三、推荐方案：三层吸收

### L1 交互/UX 吸收（建议全量做，零法律与架构成本）

- 做法：把 rikkahub 装到真机，按知芽已有 UX 评审维度（入口、主流程、状态、反馈、文案）逐屏截图对比，输出《RikkaHub 交互基准笔记》，再选择性并入知芽的高保真原型（`Docs/04-ux-design/`）。
- 重点研究对象：流式打字与停止交互、消息长按操作菜单、Markdown 展开收起、Provider/模型设置页信息结构、**消息分支的操作路径**（它如何让用户开分支、如何回溯——这是知芽树状交互的最接近参照物）。
- 约束：只借鉴范式与信息结构，不复制像素与文案；并入知芽 UX 时必须走 design token（`Docs/04-ux-design/design-system/tokens.json`）。

### L2 机制级吸收：clean-room 重实现（按需做，建议做 2 项）

「clean-room」含义：A（AI）读 RikkaHub 源码，把**机制**写成不含任何源代码的中文规格；B（另一轮 AI/人）只看规格用 Java 在知芽架构内实现。这样产物不是衍生作品，AGPL 义务不触发。直接「读着 Kotlin 写 Java」不构成 clean-room，仍有 AGPL 衍生作品风险——这条必须诚实对待。

优先候选（与知芽 V3.x 需求直接相关）：

1. **SSE 流式解析的工程边界**：RikkaHub `me/rerere/ai/stream/` 对断流、不完整 chunk、错误恢复的处理比自建解析更经受过实战。知芽现用 `HttpsURLConnection` + 自建 SSE（project-brief），可对照补齐边界用例。
2. **分支消息的数据模型**：研究它 Message Branching 的表结构与回溯语义，对照知芽七张逻辑表中的会话/消息表，吸收其回溯语义的合理部分（知芽树状模型为主，只取其已验证的边角处理）。

暂缓候选：Markdown 渲染选型（先评估现成 Java/跨语言渲染库，如 Markwon，再决定是否需要参考其 `:highlight` 模块）。

### L3 整库基座吸收（不推荐，除非两项前置全部翻转）

只有同时满足才可考虑：① 用户接受知芽整体 AGPL-3.0 开源；② 用户决定解除 Java-only 约束、放弃 XML/View 架构与已交付 V1、接受 Firebase 依赖被移除的改造工作量。届时路线是 fork RikkaHub → 剥离云端功能 → 把树状分支做成核心屏。代价：知芽 207 个 Java 文件与架构方案 V1.0 作废性重构，且再无退路。

## 四、落地路线（对齐现有 ROADMAP，不新建任务，待确认后再建）

1. **第一步（半天）**：真机安装 RikkaHub，L1 体验评审，产出《RikkaHub 交互基准笔记》到 `Docs/04a-ux-cross-review/`。
2. **第二步**：拍板 AGPL 立场 → 决定 L2 深度；拍板通过后为 SSE 边界与分支数据模型各立一份 clean-room 规格（`Docs/05-technical-design/`）。
3. **第三步**：规格并入对应开发任务（届时按 AGENTS.md 询问是否走 Harness Task 流程）。

## 五、待拍板事项

1. **AGPL 合规立场**（决定 L2/L3 深度，本文唯一待决项）：知芽未来是否可能上架分发且不接受整体开源？
   - 若「是」→ 只能走 L1 + 严格 clean-room L2（本文推荐路线，即默认假设）。
   - 若「否，接受 AGPL 开源」→ L2 可放宽为直接参考源码实现，L3 重新评估。

## 六、事实源与证据记录

- rikkahub 许可证：`D:\Project\Android\rikkahub-master\LICENSE`（首行 GNU AFFERO GENERAL PUBLIC LICENSE）。
- rikkahub 技术栈与功能：`D:\Project\Android\rikkahub-master\README.md`（Features、Development 节）。
- 模块清单：`D:\Project\Android\rikkahub-master\settings.gradle.kts`（13 个 include）。
- 代码规模：`find app/src/main/java -name "*.kt" | wc -l` = 404；`find ai/src/main/java -name "*.kt" | wc -l` = 38（2026-09-23 实测）。
- 知芽约束：`.codex/context/project-brief.md`「关键约束」「当前阶段与技术栈」节。
- 知芽现状：`find android -name "*.java" -path "*main*"` = 207（2026-09-23 实测）；模块见 project-brief。
