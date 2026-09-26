# 结构化需求基准说明书_知芽（TreeLearn）_V3.3增量（多 Provider 接入与模型分工）

> 版本：V3.3 增量 ｜ 日期：2026-09-24 ｜ 状态：accepted
> 变更依据：`Docs/intent/INTENT-2026-09-23-rikkahub-settings-center-absorption.md`（accepted，用户 2026-09-23 指令「五个全部搬过来」）+ `Docs/spec/SPEC-2026-09-23-providers-and-models.md`（accepted）+ 用户 2026-09-24 指令「把知芽扩展到多 Provider 方向，更新 project-brief 与 PRD（走变更流程）」。
> 本增量只改与「多 Provider」直接相关的基线条款；其余基准维持 V3.0/V3.1 不变。

## BASE-27 多供应商接入（废止「仅 DeepSeek」）

- 原条款：V3.0 第 87 行「用户自配 DeepSeek API Key，首版仅支持 DeepSeek API」；V3.1 第 76 行非目标「不引入非 DeepSeek 模型供应商」——**废止**。
- 新条款：支持 OpenAI 兼容协议的多供应商接入，用户可在设置中心新增/编辑/启用/禁用/删除提供商（名称、基址、Key 三要素），预置 DeepSeek 模板；每提供商可拉取其可用模型清单合并入本地。
- 协议范围：本期仅 OpenAI 兼容协议；Gemini/Anthropic 原生协议**暂缓**（非永久拒绝，另行立项）。

## BASE-28 模型三分工

- 模型按用途分工：**聊天模型**（教学回答主链路）、**快速模型**（标题/摘要/派生杂活）、**翻译模型**（翻译用途），在模型设置页各选其一。
- 思考模式开关仍每次请求显式传（沿用 BASE-23 的显式传递语义），思考预算随聊天模型配置。
- 禁用/删除提供商时，其模型即时退出全部分工选择器；被删除提供商上的分工自动回落并提示。

## BASE-29 Key 边界扩展（边界本身不变）

- 各提供商 Key 全部进 Android Keystore（AES-GCM）；明文不进 SQLite、日志或备份——边界规则沿用 V3.0，仅适用范围从单 Key 扩展为「每提供商一 Key」。
- 无任何可用 Key 时，发送被 Key 守卫拦截并引导，不产生半成品入口。

## 不变项（重申）

- 无服务端、本地优先、树状学习核心模型、Java-only、SQLiteOpenHelper 前向迁移、44dp 热区、单任务 ≤200 行——全部维持。
- 自定义请求头/请求体、Prompt 变量、MCP、Agent 工作区、Material You 取色维持 reject；深色模式维持 defer。

## 事实源

- 需求规格：`Docs/spec/SPEC-2026-09-23-providers-and-models.md`（accepted）。
- PRD 侧记录：`Docs/03-prd-draft/V3.3-知芽（TreeLearn）产品需求文档（PRD）多Provider增量.md`。
- 工程落地：T-083（SQLite 提供商/模型表，verified）、T-084（Keystore 多 Key，verified）、T-085（网关 Provider 抽象）及后续 086–089。
