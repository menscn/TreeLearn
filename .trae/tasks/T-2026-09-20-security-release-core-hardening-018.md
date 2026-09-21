# Treelearn Harness Task: 完成安全、Release 与核心流加固

id: T-2026-09-20-018
title: 完成安全、Release 与核心流加固
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: true
depends_on: [T-2026-09-20-home-settings-ui-012.md, T-2026-09-20-chat-reasoning-answer-actions-013.md, T-2026-09-20-navigation-archive-delete-014.md, T-2026-09-20-search-unread-reading-position-015.md, T-2026-09-20-tree-canvas-tablet-ui-017.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 将所有切片收敛为可侧载 release APK，并完成隐私、日志、网络、恢复和核心用户流加固。
- Problem/background: 单任务测试通过不代表 release 配置、模块连接和真实生命周期正确。
- Users/scenarios: 用户在个人设备安装 APK，配置 Key，创建/分叉/搜索/归档主题，后台切换并从中断恢复。
- Expected outcome: release 配置、Telemetry no-op、脱敏日志、HTTPS/备份守门、提示词行为集成测试和可安装 APK。
- Core flow and reversibility: 安装后从空状态走完整流程；卸载清除本机数据；应用内删除按产品规则执行。
- Inputs/outputs: 输入各模块实现、提示词行为样例和临时测试 Key；输出使用仓库外专用侧载证书签名的可安装 release APK、校验摘要、脱敏行为验证报告和启动命令。
- State and exception handling: 离线、错误码、旋转、后台、进程死亡、低存储和长内容均纳入核心回归。
- Constraints and trade-offs: Java-only、API 28+、release 无远程遥测、无正文日志、无备份、无明文流量。
- AI recommendation and rationale: 用端到端测试连接状态边界，再做最小发布配置，不在此任务扩功能。
- User choice and custom additions: 个人设备直接安装 APK；后台仅尽力继续。
- AI-autonomous decisions: release TelemetrySink 使用 no-op；debug 也只允许脱敏元数据。
- User-confirmation decisions: 技术方案全部 P0/P1 与本地安全边界。
- Acceptance evidence: lint/unit/instrumentation/release build、安装启动、secret scan、依赖图和真实核心流。
- Facts: 技术方案 §14～§16 是权威。
- Assumptions: 验收设备/模拟器与临时 DeepSeek Key 可用。
- Unknowns: 应用商店正式签名证书仍由用户未来发布时管理；本期只建立仓库外的个人侧载签名。
- Risks: release-only 混淆、网络安全配置和生命周期回归。
- Deferred: 商店发布、崩溃上报和云端遥测。
- Non-goals: 不增加新产品功能或后端。
- Skipped non-critical questions and reason: 商店渠道未指定，当前只要求个人侧载。

## Goal

产出在目标设备可安装、核心流可复现且满足隐私/Java-only 守门的 release APK。

## Scope

### In scope
- release build、网络/备份/log/telemetry 配置、核心端到端测试、安装启动、性能与泄密检查。

### Out of scope
- 商店签名托管、云服务、远程遥测和新功能。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 集成现有切片并强化 release；不改变已确认业务语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): app container 连接 core/data/ai/tree-layout；debug/release、API 36、手机/平板均执行。
- Native bridge or independent-client compatibility/update requirements: DeepSeek HTTPS 与 Android 系统生命周期为外部边界。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 构建变体、APK、启动、网络/备份/日志/遥测和已知限制。
- Authoritative code/config/migration evidence: Gradle/Manifest/network/backup/proguard、测试与安装输出。
- Documentation surfaces checked: Android README、Android 技术方案、提示词设计 V1.0、PRD F21/隐私/错误处理。
- Documentation files updated: 写明构建、测试、安装、临时 Key 注入和已知限制。
- Producer/consumer/sibling/compatibility paths checked: 所有模块入口、release/debug、手机/平板和进程恢复。
- Superseded phrases/examples reverse-searched: 云备份、远程遥测、明文流量和后台保证续传。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 只同步当前运行事实。

## Acceptance criteria

- [ ] 同一端到端目标测试命令先 RED 后 GREEN；完整创建、reasoning 展开、分叉、重置、搜索、归档、画布流程通过。
- [ ] lint、所有 unit/instrumentation、release assemble、依赖方向、Java-only guard 和 secret/content log scan 返回 0。
- [ ] Manifest/Network Security/backup 配置证明仅 HTTPS、禁备份/迁移；release TelemetrySink 为 no-op。
- [ ] release APK 使用仓库外的 Treelearn 专用个人侧载 keystore 签名，`apksigner verify --print-certs` 通过；在 API 36 手机竖屏和代表性平板横屏安装启动，无崩溃、重叠或空白 Canvas。（用户 2026-09-21：验收环境为 API 36/36.1）
- [ ] 真实 DeepSeek 冒烟验证两个发布模型、thinking on/off、版本化 system 被接受、历史 reasoning 进入后续请求和派生 JSON 可解析；证据不含 system、Key、问题、reasoning 或 answer 正文。
- [ ] 受控行为集覆盖入门/专业问题、错误前提、可假设与必须澄清的歧义、时效信息、类比边界和 reasoning 折叠；按结构性 rubric 判定，不逐字匹配，任一明显编造、内部协议泄露或最终回答依赖展开 reasoning 均为 FAIL。
- [ ] secret/content log scan 同时证明 SQLite、SharedPreferences、日志、异常和报告不含 system content；只允许 PromptPurpose/promptPolicyVersion 等脱敏元数据。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING
- Command: PENDING
- Exit code: PENDING
- Observed failure: PENDING
- Failure reason validated: PENDING

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- 未提供正式证书时，生成 Treelearn 专用个人侧载 keystore 并保存在仓库外；口令只进入环境变量或未跟踪的本地配置，绝不打印或提交。记录 keystore 路径、证书 SHA-256 和 APK SHA-256，不记录口令。
- 正式商店签名材料、个人侧载 keystore、测试 Key 和 local.properties 均不入库。
- 进程死亡测试必须用系统终止方式，不以 Activity recreate 冒充。
- release 验证发现功能缺陷时回到所属任务修复并补 RED/GREEN，不在本任务堆叠大改。
- 真实模型行为有非确定性；至少对失败样例复跑并保留脱敏结构结论。不能用单个“看起来不错”的截图替代 T-020 确定性契约测试。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 设备/OEM 差异与供应商在线可用性。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 真实 API 验收需要环境变量中的临时 Key；设备/模拟器与缺失 SDK 组件由执行者自动准备。
- Unblock condition: 在执行进程可见的环境变量中提供测试 Key；密钥不得写任务文件或日志。
- Next steps: 派独立 verifier 执行最终任务。
- Watch-outs: 未覆盖真实分支不得声称 release ready。
