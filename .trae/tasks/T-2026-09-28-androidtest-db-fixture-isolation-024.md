# Treelearn Harness Task: 仪器测试全量批跑 DB 夹具隔离缺陷

id: T-2026-09-28-024
title: 仪器测试全量批跑 DB 夹具隔离缺陷
template_version: 2
task_type: requirement
status: clarifying
parent_requirement: null
requirements_confirmed: false
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-27-home-visual-fidelity-verification-008.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 独立任务，由 `T-2026-09-27-home-visual-fidelity-verification-008` 独立验收发现的 G1 缺口转立。
  008 判定首页线 002–007 各条 acceptance 通过、并置为 verified，但**全量批跑仪器测试红**，故单独立此缺陷任务，
  不让它扣住已完成的首页线任务。
- Problem/background: `:app:connectedDebugAndroidTest` **全量批跑**在真机 `AWSS013807000356`
  （荣耀 ALP-AN00 / Android 13）上稳定失败：**两次独立运行均为 243 例 / 22 失败，且失败分布逐条相同**。
  同样这些类在**类域隔离批跑**下**全绿**（首页+壳层 6 类 22 例 0 失败；排除字母序最前 5 类的归因探针 40 例 0 失败）。
  失败形态统一为「no row arrived / never drew N card rows」类的等待超时 + 少量时序断言。
- Users/scenarios: 任何在本仓跑**全量**仪器测试的人（含 CI）。当前仓库靠「类域批跑」纪律维持绿像，
  纪律一旦松懈（如把两类倒序一起跑）即暴露。
- Expected outcome: 全量批跑 `:app:connectedDebugAndroidTest` EXITCODE=0，且不依赖「按类分批跑」的纪律。
- Core flow and reversibility: 定位污染源测试类 → 让每个测试类拿到独立的 DB 夹具（或统一走 helper 重建 + 容器重建）
  → 全量复跑绿。属测试基建改动，可逆。
- Inputs/outputs: 输入为现有 androidTest 套件与 `AppContainer` 的 DB 持有方式；输出为隔离机制 + 全量绿的证据。
- State and exception handling: **待用户拍板**（本任务创建于验收收口时，需求尚未与用户逐条确认）。
- Constraints and trade-offs: 优先不动生产代码；若必须动（如 `AppContainer` 的连接生命周期），需单独论证并登记。
  候选方案：① 统一 `deleteDatabase()` 为「helper 重建 + 容器重建」；② 引入进程内夹具隔离（如每个类独立 DB 名）；
  ③ 最小二分定位污染类后按类修。
- AI recommendation and rationale: 建议先做**最小二分定位**再选隔离方案——当前已排除字母序最前 5 类，
  但污染源未定位，直接上通用隔离方案可能过度设计。
- User choice and custom additions: 待确认。
- AI-autonomous decisions: 无。
- User-confirmation decisions: 是否立此任务（2026-09-28 用户就 008 收口选项裁决「收口并单立缺陷任务」，已确认）。
- Acceptance evidence: 全量 `connectedDebugAndroidTest` 的 EXITCODE=0 与测试计数证据文件。
- Facts: 多个 androidTest 类在 setUp/tearDown 调 `context().deleteDatabase(...)`
  （如 `AnswerVersions`:91,103、`AttachmentOrphanCleanup`:60,76、`AttachmentParseInject`:56,68），
  而 `AppContainer` 常开连接；`HomeTopicCardInstrumentedTest` javadoc:46-53 已自述
  「unlink 后壳层旧 inode 连接读不到夹具写盘」这一批跑风险——批跑失败形态与该自述风险一致。
  证据文件（2026-09-28 当场产出）：`artifacts/evidence/T-2026-09-27-008-connected-test.txt`、
  `-connected-test-rerun-full.txt`、`-connected-failures.txt`、`-connected-full-rerun-failures.txt`、
  `-connected-home-rerun.txt`、`-connected-attribution-probe.txt`。
- Assumptions: 污染源是测试类之间的 DB 夹具串扰，而非产品代码缺陷（隔离复跑全绿支持此假设）。
- Unknowns: 具体是哪几个测试类充当污染源；`AppContainer` 常开连接的具体持有位置。
- Risks: 若 CI 未来按全量跑 connected，会常红；改测试基建可能牵动多个既有测试类。
- Deferred: 是否顺带收敛「类域批跑」这一纪律性做法（待隔离修好后讨论）。
- Non-goals: 不改生产业务逻辑；不修首页线 002–007（已 verified，与此缺陷无关）。
- Skipped non-critical questions and reason: 未逐个复跑全部 243 例定位污染类——成本过高，
  先用二分法缩小范围。

## Goal

让 `:app:connectedDebugAndroidTest` 在真机上**全量批跑**通过，根因定位到具体的 DB 夹具串扰并修掉，
使仓库不再依赖「按类域分批跑」的纪律维持绿像。

## Scope

### In scope

- 用二分法定位充当污染源的 androidTest 测试类（已知 A 前缀 5 类无辜）。
- 为 androidTest 套件建立 DB 夹具隔离（候选：统一 helper 重建 + 容器重建，或按类独立 DB 名）。
- 全量批跑 `connectedDebugAndroidTest` 取得 EXITCODE=0 的证据。

### Out of scope

- 生产代码 / 业务逻辑改动（若最终必须动 `AppContainer`，需单独论证并登记）。
- 首页线 002–007 的任何改动（已 verified）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只改测试基建；每个测试类断言的业务行为必须保持不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `AppContainer` 的 DB 持有方式；
  各 androidTest 类的 setUp/tearDown 中 `deleteDatabase()` 调用点。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（测试基建改动，需回归证明行为中性）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 待实现后填写（若只改测试代码则「无运行时事实变更」）。
- Authoritative code/config/migration evidence: 待定位后填写。
- Documentation surfaces checked: `android/README.md`（测试一节，若有）。
- Documentation files updated: 待实现后填写。
- Producer/consumer/sibling/compatibility paths checked: 受影响的 androidTest 类清单。
- Superseded phrases/examples reverse-searched: `HomeTopicCardInstrumentedTest` javadoc:46-53 的批跑风险自述，
  修好后该段应改写或删除。
- `documentation impact: none` reason and evidence, if applicable: 待实现后填写。
- Historical/PRD handling: 无（PRD 不涉及测试基建）。

## Acceptance criteria

- [ ] 已定位到至少一个充当污染源的测试类，并给出可复现的最小对（哪两类一起跑必红、分开跑全绿）。
- [ ] 建立 DB 夹具隔离后，`:app:connectedDebugAndroidTest` **全量批跑** EXITCODE=0，证据文件含命令行与退出码。
- [ ] 既有断言未被删除；如改写，给出等价覆盖说明，并附真机回归证据。

## TDD execution record

### RED — failing test first
- Test/scenario: 待执行时填写（建议：先写一个能稳定复现该串扰的最小对组合作为 RED）
- Command: 待执行时填写
- Exit code: 待执行时填写
- Observed failure: 待执行时填写
- Failure reason validated: 待执行时填写

### GREEN — minimum implementation
- Implementation: 待执行时填写
- Command: 待执行时填写
- Exit code: 待执行时填写
- Passing evidence: 待执行时填写
- Regression evidence: 待执行时填写

### REFACTOR — optional
- Cleanup: 待执行时填写
- Regression evidence: 待执行时填写

### TDD or sizing exception — only with prior user approval
- Exception type: 待定
- Reason: 缺陷定位类任务可能难以先写出稳定失败的测试；若如此，须**事先**请用户批准 TDD 例外
- User approval: 未取得（本任务创建时尚未与用户确认实现方案）
- Alternative verification: 二分法最小复现对 + 全量批跑前后对比

## Implementation notes

- 来源：`T-2026-09-27-home-visual-fidelity-verification-008` 的 Gaps G1 与 Risks R1。
- 本机环境：Git Bash 开头需 `export PATH="/usr/bin:/bin:/c/Windows/System32:$PATH"`；
  gradle 用 `export JAVA_HOME=/d/Environment/Jdk/jdk21` 后 `./gradlew`（勿用 `cmd /c`）。
- 设备事实：`connectedDebugAndroidTest` 跑完 AGP 会**卸载被测应用**；真机可能息屏（AOD），
  唤醒用 `keyevent 224` + `wm dismiss-keyguard`。
- 已排除：字母序最前 5 个测试类非污染源（归因探针 40 例全绿）。

## Verification report

- Verdict: PENDING
- Evidence: 待执行时填写
- Changed/deleted assertion rationale and equivalent behavior coverage: 待执行时填写
- Gaps: 待执行时填写
- Risks: 待执行时填写

## Handoff notes

- Completed: 任务创建（由 008 独立验收的 G1 缺口转立）
- In progress: 无
- Blocker: 需求尚未与用户逐条确认（status 保持 clarifying）
- Unblock condition: 用户确认问题口径与隔离方案选型
- Next steps: ① 与用户确认方案选型（二分定位优先 / 直接上通用隔离）；② 定位污染源；③ 实施隔离并全量复跑
- Watch-outs: 不要为了「跑绿」而删除或弱化既有断言；不要顺手改生产逻辑；全量跑前确认源码静止
  （本仓有多条工作线共写 android/ 树的历史）
