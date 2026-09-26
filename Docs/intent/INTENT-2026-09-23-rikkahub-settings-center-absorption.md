# Intent: RikkaHub 设置中心五能力全量吸收（提供商/模型/搜索/语音/文件管理）

id: INTENT-2026-09-23-rikkahub-settings-center-absorption
title: RikkaHub 设置中心五能力全量吸收（提供商/模型/搜索/语音/文件管理）
date: 2026-09-23
author: 用户
origin: 用户想法
status: accepted

## Problem（问题与背景）

- 问题/背景：用户比对开源方案 RikkaHub（`D:\Project\Android\rikkahub-master`，AGPL-3.0，Kotlin+Compose），认为其设置中心的五块能力值得全量搬入知芽：①提供商管理（多供应商接入、启用/禁用、模型列表）；②模型设置（聊天/快速/翻译模型分工 + 思考预算 + 聊天建议开关）；③搜索服务（对话联网搜索增强）；④语音（TTS 提供商可插拔 + 语音识别）；⑤文件管理（聊天附件上传、解析与管理）。
- 用户/场景：单人开发者即唯一用户，学习场景中希望按用途分工选模型（快速模型干标题等杂活）、朗读回答、带附件提问、联网检索。

## Users and scenarios（用户与场景）

- 本期只有用户本人使用；真机体验验收由用户完成。

## Expected outcome（预期结果）

- 预期结果：五块界面交互全量对齐 RikkaHub，但视觉风格遵循知芽自身 design token 体系（生长绿、纸感、44dp 热区）；每块界面对应的底层能力真实可用——TTS 调用本机系统 TTS、语音识别走系统 STT、搜索调用真实搜索服务、多供应商可配置并可切换。

## Core flow（核心流程）

- 核心流程（含中断、撤销、重试）：设置中心入口 → 提供商增删改/启停 → 模型分工选择 → 对话使用所选模型；语音朗读/识别在对话页触发；附件从输入栏选择、解析入上下文，文件管理页可查可删。
- 输入/输出：输入为各提供商 API 地址/Key/模型列表、搜索服务配置、系统 TTS/STT、用户文件；输出为持久化配置与对话行为变化。
- 状态与异常：无 Key/网络失败/权限拒绝/解析失败须有降级或拦截，不得半成品入口。

## Constraints and non-goals（约束与非目标）

- 约束：Java-only、XML/View、无服务端、AGPL clean-room（实现者禁读 RikkaHub 源码，规格作者可读）、密钥只进 Keystore、SQLite 前向迁移、单任务生产代码 ≤200 行。
- 权衡偏好：界面交互照搬、视觉风格用知芽 token；能力真实优先于数量。
- 非目标（non-goals）：Material You 动态取色、自定义请求头/请求体、Prompt 变量系统、Web 端、MCP、Agent 工作区、深色模式（维持矩阵 7.3/4.4/4.5/8.2 reject 与 7.4 defer）。

## Open questions（开放问题）

- 已确认事实、假设、未知项：
  - 已确认：用户 2026-09-23 07:25 指令「五个全部搬过来，界面交互全部搬但风格保持知芽自己的，每个界面背后对应的能力要真实（如本机 TTS）」；前一轮对话已确认分期（一二三期都要做）。
  - 假设：用户所说「搜索调用本机的搜索」理解为「搜索服务真实可调用」（Android 无系统级网页搜索 API，RikkaHub 实际走 Tavily/Bing 等服务 API）——规格阶段与用户核对。
  - 未知项：协议范围（先 OpenAI 兼容 vs 同步 Gemini/Anthropic 原生）、思考预算在 DeepSeek 上的映射、聊天建议开关与现有 Recommendation（T-046）的关系——由规格任务收敛，列入拍板区。

## Convergence verdict（收敛判定）

### 有价值吗

结论：是
证据：矩阵 V1.0（T-072）将多 Provider/附件/语音判为 defer/reject 仅因当时「DeepSeek-only 冻结基准 + 用户未拍板」；用户本次明确推翻并要求全做。

### 值得做吗

结论：是
证据：用户直接指令，且五块能力相互独立、可分期交付；与树状学习主流程不冲突（模型分工反而增强派生任务链路）。

### 可以做吗

结论：是
证据：知芽已有 Keystore（T-005）、SSE 网关（T-006）、派生任务契约 PromptPurpose、系统 TTS/STT 均为 Android 平台 API，无技术不可行项；规模需按 Harness 拆分为 16 个 ≤200 行实现任务。

## Decision record（决策记录）

- 决策权归属：用户
- 决策人：用户
- 决策日期：2026-09-23
- 终态：accepted
- 否决理由（status 为 rejected 时必填）：不适用
- 验收标准与证据：每个实现任务的 acceptance + verifier 独立验收；真机体验由用户自测。
- 风险与后续范围：基线变更（DeepSeek-only→多供应商）必须先落需求规格再动代码；AGPL clean-room 边界靠任务文件 watch-outs 强制。

## Next step（下一步）

- accepted：移交 requirement-decomposer，并附「移交清单」，写明子任务切片建议、依赖顺序和不可并行的部分。
- 移交清单：父需求 T-2026-09-23-078；规格任务 079–082（四份 SPEC：提供商与模型 / 搜索 / 语音 / 文件管理）；实现任务 083–098（基座 8 个：schema/Keystore/网关抽象/模型列表/两屏 UI/快速模型接线/翻译；搜索 2 个；语音 3 个；文件 3 个）。不可并行：SQLite schema（083）与 :ai 网关（085/086）触共享代码区，须独立串行；Gradle 同工程任务一律串行。
