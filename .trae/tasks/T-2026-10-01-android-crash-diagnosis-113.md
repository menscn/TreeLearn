# Treelearn Harness Task: Android 闪退诊断

id: T-2026-10-01-113
title: Android 闪退诊断
template_version: 2
task_type: verification
status: blocked
parent_requirement: T-2026-10-01-android-daily-use-issues-111.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

Parent summary: T-2026-10-01-android-daily-use-issues-111.md 收集 Magic8 Pro Air / MagicOS 11 的十项日常使用反馈。本任务只处理 #4 闪退诊断。

- Problem/background: 用户报告使用过程中出现闪退；没有给出触发路径、频率、应用版本或堆栈。
- Users/scenarios: 在 Magic8 Pro Air、MagicOS 11 上按用户可重复的日常聊天/分支/历史操作使用 Android 应用。
- Expected outcome: 确认是应用崩溃、系统回收、ANR 后退出或其他退出，并以脱敏证据定位可复现触发路径；不猜根因。
- Core flow and reversibility: 记录一次操作序列 → 观察退出 → 重新冷启动确认恢复；不清理或重置用户数据。
- Inputs/outputs: 输入为设备/应用构建信息、触发操作与时间；输出为可复核的日志/堆栈摘要、复现率和后续任务建议。
- State and exception handling: 无法复现时记录测试次数、覆盖场景与 PARTIAL/未复现，不标记已修复；日志不足时明确缺口。
- Constraints and trade-offs: 仅诊断；不采集聊天正文、推理文本、API key、令牌或完整网络请求；保留用户数据。
- AI recommendation and rationale: 先区分真实异常崩溃与系统回收/进程重启，避免把一次重启泛称为闪退。
- User choice and custom additions: 用户确认 Android-only、Magic8 Pro Air / MagicOS 11，当前只拆 Harness。
- AI-autonomous decisions: 仅在堆栈/系统记录支持后建议具体修复范围。
- User-confirmation decisions: 目标设备与诊断阶段边界已确认。
- Acceptance evidence: 设备与 APK 版本、触发步骤、时间戳、脱敏 AndroidRuntime/FATAL EXCEPTION/ANR 或系统退出记录。
- Facts: 反馈是现场观察，现有工作区改动不等于目标机复现或修复。
- Assumptions: 目标机可连接调试采集，或用户可提供对应时段系统日志。
- Unknowns: 触发页、前后台状态、频率、崩溃类型及调用栈。
- Risks: 用户数据或令牌可能出现在日志中，必须过滤并核查再归档。
- Deferred: 修复任务待诊断形成可定位证据后另建。
- Non-goals: 直接修复推测性崩溃、清空数据、更新系统或改变应用运行配置。
- Skipped non-critical questions and reason: 无；先按实际复现路径采集。

## Goal

在 Magic8 Pro Air / MagicOS 11 上获取 #4 闪退的可复核分类与触发证据，或准确记录未复现；不修改生产代码。

## Scope

### In scope
- 记录设备型号、MagicOS/Android 构建、应用版本、操作步骤、发生时间及复现尝试数。
- 将进程崩溃、ANR、系统回收、手动退出等分开；保存脱敏系统日志/堆栈摘要。
- 证据支持时建议独立、可验收的修复任务并标出生产调用链。

### Out of scope
- 修改生产代码/配置或用户数据；凭空推断原因；以冷启动成功代替崩溃根因闭环。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: N/A — 只读诊断。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 按实际堆栈追踪 Android Activity、对话 UI、存储及异步任务调用链；当前触发入口未知。
- Native bridge or independent-client compatibility/update requirements: N/A — 不改接口或桥接。
- Behavior-neutral non-applicability reason, if applicable: 不改运行行为，仅采集证据。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: None.
- Authoritative code/config/migration evidence: 实际崩溃堆栈命中的 Android Java 代码/配置待诊断后记录。
- Documentation surfaces checked: Android README 的支持设备/排障说明，若诊断指向当前态变更再决定更新。
- Documentation files updated: None — 诊断阶段无运行事实改变。
- Producer/consumer/sibling/compatibility paths checked: 待堆栈确认后记录。
- Superseded phrases/examples reverse-searched: N/A — 当前不改文档。
- `documentation impact: none` reason and evidence, if applicable: 只读诊断；无代码、配置或当前态主张变化。
- Historical/PRD handling: 不改写用户的历史反馈。

## Acceptance criteria

- [ ] 至少记录目标机信息、应用构建、触发步骤、复现尝试次数及实际退出类型；不能复现时明确写明覆盖范围。
- [ ] 可复现时给出脱敏时间戳与崩溃/ANR/系统回收证据；日志中不得保留正文、推理、密钥或令牌。
- [ ] 结论区分已证实根因、假设和未知项；若需修复，给独立任务建议；不修改代码，不以“未复现”报告 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A — 只读崩溃诊断。
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A — 无实现变更。
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: N/A
- Reason: TDD 不适用于只读诊断。
- User approval: N/A
- Alternative verification: 目标机日志与复现记录。

## Implementation notes

- 采集后先检查并脱敏，再把证据存入仓库允许的 evidence 位置。
- 若设备/日志不可用，保持 PARTIAL 并列出用户可补充的最小证据，不虚报完成。

## Verification report

- Verdict: BLOCKED
- Evidence: `artifacts/evidence/T-2026-10-01-target-device-availability.txt` 记录 `adb devices -l` 与 `getprop`；当前连接为 HONOR ALP-AN00 / Android 13 / MagicOS 7.2.0.106，不是指定 Magic8 Pro Air / MagicOS 11。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A — 无代码变更。
- Gaps: 指定目标机未连接；闪退时间点、触发步骤与系统日志待取得。
- Risks: 隐私信息进入日志；归档前必须逐项复核。

## Handoff notes

- Completed: 已将 #4 独立为崩溃诊断任务。
- In progress: 等待目标机接入后继续取证。
- Blocker: 指定目标机未连接，且当前没有崩溃时间点/触发步骤。
- Unblock condition: 接入 Magic8 Pro Air / MagicOS 11，并提供复现步骤或可脱敏系统记录。
- Next steps: 目标机可用后复现并区分应用崩溃、ANR 与系统回收；只在证据支持时建修复任务。
- Watch-outs: 不清库、不把系统杀进程当成应用崩溃、不在证据中留下凭据或对话内容。
