# Treelearn 项目背景包（L0 常驻入口）

> 读取时机：任何需要了解产品事实或工程约束的角色，在开工前。本文件只写「一句话事实 + 指针」，需要展开的一切以指针所指的权威正文为准。

## 产品一句话

知芽（TreeLearn）是 Android 单机应用：把 AI 问答从线性会话改成可回溯的树状学习，用户能从稳定的 AI 回答上开子分支深入追问，再回到来源继续读；业务数据与各家提供商 Key 都只留在本机。

## 当前阶段与技术栈

- 阶段：需求以 V3.0 冻结基准 + V3.1 增量（BASE-21～26）+ V3.3 增量（BASE-27～29，多 Provider，2026-09-24）为准；信息架构以 V3.2 PRD 修订为准；正在做 Android 首版与设置中心能力。
- 形态：纯 Android 原生；手机竖屏优先、兼容平板横屏；个人设备直接装 APK。
- 模块：`:app`、`:core`、`:data`、`:ai`、`:tree-layout`；依赖只能向下，`:core` 不碰 Android UI、网络、数据库实现。
- 构建：Gradle Groovy DSL、Java 17 toolchain、`minSdk 28`。
- UI 与状态：原生 XML/View 加自定义 View/Canvas；显式 ScreenController/Presenter 配不可变 ViewState。
- 存储：`SQLiteOpenHelper` 加显式 DAO 和前向版本迁移；核心七张逻辑表 + 提供商/模型配置两张表（T-083 交付，共九张）。
- 网络与模型：`HttpsURLConnection` 加自建 SSE 解析；OpenAI 兼容多供应商（基线变更依据：`Docs/spec/SPEC-2026-09-23-providers-and-models.md`，accepted），思考模式开关每次请求显式传。
- 密钥：Android Keystore（AES-GCM）；明文不进 SQLite、日志或备份。

## 关键约束

- Java-only：不写 Kotlin 源码，不加 Kotlin Gradle 插件，不引 `org.jetbrains.kotlin` 或 `-ktx` 坐标。
- 无远程服务端：不做账号、同步、共享、云备份、远程遥测。Web 线（用户 2026-10-15 拍板，见 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`）规划新建 `frontend/` 与仅监听本机环回地址的 `backend/`；两目录尚未创建，且不改变「数据只留本机、密钥不进服务与浏览器」的边界。
- 时间：落库与对外传输统一用 UTC epoch 毫秒。
- 触屏热区不小于 44dp。
- 单人开发：所有流程都必须假设没有第二个人做复核。
- 规模：单个任务的生产代码目标 200 行；RikkaHub 吸收战役内（T-086～T-105，用户 2026-09-24 两次裁定「可以放行」「预授权追认」）实测超 500 行的任务记「预授权追认」例外后放行——实现者须两轮压缩后如实记录，verifier 须独立实测行数并评估风险；战役外维持 200/500 原口径。

## 权威文档索引

- `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md`
- `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md`
- `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.3增量.md`（BASE-27～29，多 Provider，accepted 2026-09-24）
- `Docs/03-prd-draft/V3.0-知芽（TreeLearn）产品需求文档（PRD）初稿.md`
- `Docs/03-prd-draft/V3.1-知芽（TreeLearn）产品需求文档（PRD）.md`
- `Docs/03-prd-draft/V3.2-知芽（TreeLearn）产品需求文档（PRD）.md`
- `Docs/03-prd-draft/V3.3-知芽（TreeLearn）产品需求文档（PRD）多Provider增量.md`（accepted 2026-09-24）
- `Docs/03a-prd-cross-review/V3.0-PRD交叉评审报告.md`
- `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html`
- `Docs/04-ux-design/design-system/README.md`
- `Docs/04-ux-design/design-system/tokens.json`
- `Docs/05-technical-design/知芽（TreeLearn）Android SQLite 建表入场确认清单_V1.0.md`
- `Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md`
- `Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md`
- `Docs/05-technical-design/TreeLearn_Harness全量开发交付提示词.md`
- `Docs/intent/README.md`（变更意图文件的命名规则、状态表与交接约定）
- `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`（AI 人设偏好：内置人设，accepted）
- `Docs/intent/INTENT-2026-09-21-user-authored-persona.md`（用户自建人设，needs-more-info）
- `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`（AI 内置人设规格，draft）
- `Docs/06-delivery/TreeLearn_V1_交付报告.md`
- `Docs/spec/SPEC-2026-09-23-providers-and-models.md`、`SPEC-2026-09-23-search-service.md`、`SPEC-2026-09-23-voice-tts-stt.md`、`SPEC-2026-09-23-file-management.md`（设置中心五能力规格，均 accepted，2026-09-23）

## 事实源（SoT）声明

- 产品事实（需求边界、功能规则、技术方案）：以 `Docs/**` 为唯一权威。
- 变更意图与收敛过程（想法 → 已确认需求）：以 `Docs/intent/**` 为权威。
- Harness 任务状态、TDD 证据与验收结论：以 `.codex/tasks/**` 为权威。
- 工程约束与守门规则：以 `.codex/rules/**` 为权威。
- 同一事实只允许一处权威；其它位置只能是指针或副本，冲突时以本节所列处为准。

## 阅读顺序

1. 本文件（常驻，不展开）。
2. `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md` 与 `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md`：需求边界。
3. `Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md`：工程方案。
4. 命中改动面时再读 `.codex/rules/**` 对应正文与 `Docs/03-prd-draft/**` 细则。
