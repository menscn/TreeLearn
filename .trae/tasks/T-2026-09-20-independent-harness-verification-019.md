# Treelearn Harness Task: 独立执行 Harness 最终验收

id: T-2026-09-20-019
title: 独立执行 Harness 最终验收
template_version: 2
task_type: verification
status: ready
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: verifier
owner_lane: verification
stack: android
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-20-security-release-core-hardening-018.md]
tdd_mode: not-applicable

## Requirement collaboration brief

- Parent summary: 独立核验知芽 V1 的代码、运行行为、专项 guard、文档一致性与所有子任务证据。
- Problem/background: 实现者自报通过不能替代独立复核，尤其涉及 SQLite、AI、Java-only、隐私与设备适配。
- Users/scenarios: 交付负责人依据报告决定是否可将父需求标记 verified 并侧载 APK。
- Expected outcome: 按 Harness 验收规则形成 PASS/FAIL/PARTIAL 报告，逐项引用可复现证据，并独立核验提示词契约与学习行为边界。
- Core flow and reversibility: 先审任务依赖与变更；运行机械检查；执行真实分支；失败回派对应任务，不直接改代码。
- Inputs/outputs: 输入全部 Task、diff、构建产物与测试环境；输出 task 状态、缺口、风险和最终 verdict。
- State and exception handling: 环境、设备、Key 或外部服务缺失时标明 blocked/unverified，不用模拟结果代替。
- Constraints and trade-offs: verifier 只读；结论范围必须匹配证据范围；无自动提交。
- AI recommendation and rationale: 专项 guard 与端到端行为交叉验证，避免只看测试名或截图。
- User choice and custom additions: 所有 P0/P1 与 reasoning 完整闭环均属必验项。
- AI-autonomous decisions: 任何必需证据缺失均不授予最终 PASS。
- User-confirmation decisions: 用户明确要求按 Harness 创建开发任务，供其他 AI 实施。
- Acceptance evidence: 本任务自身报告和可复现命令。
- Facts: `.trae/rules/harness-verification.md` 和所有命中的专项规则为验收权威。
- Assumptions: 实现任务已进入 verifying，且交接记录完整。
- Unknowns: 执行时设备与 DeepSeek 可用性。
- Risks: 只验证 happy path、忽略 release 差异或把 fixture 当真实集成。
- Deferred: 商店审核和长期线上监控。
- Non-goals: 不实现或修复生产代码，不替实现者补证据。
- Skipped non-critical questions and reason: 无；缺口由报告明确列出。

## Goal

以独立、可复现的证据判断 Android V1 是否满足产品、架构、Harness 与发布边界。

## Scope

### In scope
- Task 契约、diff、单元/设备/真实集成、release APK、专项 guards、PRD/原型/技术方案一致性。

### Out of scope
- 代码修改、自动提交、商店发布和超出 V1 的建议功能。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只读验收，无运行时行为变更。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 核验首页、设置、聊天、导航、搜索、画布、恢复及其 repositories/gateway。
- Native bridge or independent-client compatibility/update requirements: 核验 DeepSeek HTTPS、Android 生命周期、剪贴板和备份配置。
- Behavior-neutral non-applicability reason, if applicable: 验收任务不改代码。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无；仅记录已验证事实和缺口。
- Authoritative code/config/migration evidence: 最终 revision 的生产代码、配置、schema、测试与设备输出。
- Documentation surfaces checked: V3.1 增量基准、PRD、v2.5 原型、Android 技术方案、提示词设计 V1.0、Android README、全部 Task。
- Documentation files updated: verifier 不编辑；不一致时 FAIL 并回派实现/文档任务。
- Producer/consumer/sibling/compatibility paths checked: 全调用链与手机/平板、debug/release 分支。
- Superseded phrases/examples reverse-searched: 旧模型、旧原型版本、40dp、reasoning 不持久化/不回传和后台保证续传。
- `documentation impact: none` reason and evidence, if applicable: 只读验收，不产生运行事实。
- Historical/PRD handling: 历史 changelog 不作为当前实现契约。

## Acceptance criteria

- [ ] `python .trae/scripts/validate_tasks.py` 为 0；所有实现任务包含可信 RED/GREEN 且依赖已 verified。
- [ ] lint、unit、instrumentation、release build、`apksigner verify --print-certs`、安装启动、依赖方向、secret scan 和内容日志扫描全部有可复现输出；报告包含 APK/证书 SHA-256，但不包含 keystore 口令。
- [ ] `android-no-kotlin-guard` PASS；`contract-guardian` 核验 DeepSeek/DTO/状态/错误契约；`db-migration-reviewer` 按 Android SQLite 守门核验七表入场、schema version、新装/升级/重开、PRAGMA、事务/约束与级联证据。
- [ ] 真实核心流覆盖 reasoning 默认折叠/灰色/展开/持久化/复制/搜索/后续上下文，以及不可变版本、分支隔离、并发、恢复和晚回调。
- [ ] API 36 手机竖屏与代表性平板横屏均安装同一签名 release APK 并实际操作；Canvas 非空、不重叠，44dp 与字体放大通过；同签名覆盖安装路径成功。（用户 2026-09-21：验收环境为 API 36/36.1）
- [ ] 数据库证据来自 API 36 模拟器或真机的 Android framework SQLite；不得用 MySQL、H2、JDBC SQLite、Robolectric shadow 或纯内存 fake 冒充最终证据。
- [ ] T-020 的注册表、system 首位且唯一、当前 user 末位、用途隔离、token 预留、恶意历史文本和版本复制测试均有真实 RED/GREEN；实现中的 `tl-learning-answer/1.0.0` 与提示词设计文档一致。
- [ ] 独立复核真实 DeepSeek 两模型 × thinking on/off 的脱敏行为矩阵：最终回答独立完整、错误前提被纠正、时效事实声明核验边界、派生 JSON 不受教学提示词污染；缺 Key 或缺任一必需矩阵不得给最终 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable to read-only verification task
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: Not applicable
- Command: Not applicable
- Exit code: Not applicable
- Passing evidence: Not applicable
- Regression evidence: Not applicable

### REFACTOR — optional
- Cleanup: Not applicable
- Regression evidence: Not applicable

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: Verification task changes no production code
- User approval: User requested Harness verification task creation on 2026-09-20
- Alternative verification: Independent command, device and contract evidence listed above

## Implementation notes

- 执行前读取 `.trae/rules/harness-verification.md` 及本次所有专项规则。
- verifier 不编辑代码；发现失败回派原任务并要求最小修复及新增回归测试。
- 不得展示或写入真实 Key、system prompt、用户问题、reasoning、answer、派生输入、quote 或 snapshot 正文；只报告用途/版本和结构性断言。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 无；执行时以环境和依赖状态重新评估。
- Unblock condition: 全部依赖任务进入 verifying/verified 且测试环境可用。
- Next steps: PASS 后将本任务与父 requirement 标记 verified；否则回派失败任务。
- Watch-outs: 不能用 debug、fixture 或 Activity recreate 代替 release、真实 API 或进程死亡证据。
