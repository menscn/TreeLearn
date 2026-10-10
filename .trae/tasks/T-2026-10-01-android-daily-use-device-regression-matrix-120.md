# Treelearn Harness Task: Android 日常问题目标机回归矩阵

id: T-2026-10-01-120
title: Android 日常问题目标机回归矩阵
template_version: 2
task_type: verification
status: ready
parent_requirement: T-2026-10-01-android-daily-use-issues-111.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-29-selection-flow-responsiveness-107.md, T-2026-09-29-recommendation-fork-auto-send-108.md, T-2026-09-29-child-branch-popup-visual-fix-109.md, T-2026-10-01-android-responsiveness-recommendation-tap-diagnosis-112.md, T-2026-10-01-android-crash-diagnosis-113.md, T-2026-10-01-android-send-keyboard-and-message-visibility-114.md, T-2026-10-01-android-child-branch-back-navigation-115.md, T-2026-10-01-android-top-controls-system-insets-116.md, T-2026-10-01-android-long-answer-scroll-follow-mode-117.md, T-2026-10-01-android-reasoning-leak-prevention-118.md, T-2026-10-01-android-history-load-performance-diagnosis-119.md]
tdd_mode: not-applicable

## Requirement collaboration brief

Parent summary: T-2026-10-01-android-daily-use-issues-111.md 收口用户截图中的 #1–#10。此任务只在各诊断/实现任务完成后验证 Magic8 Pro Air / MagicOS 11 的整体验收并建立可复查矩阵。

- Problem/background: 十项现场反馈分属无响应、输入发送、分支返回、闪退、系统栏、滚动、推理标记、消息可见性、历史加载及推荐点击；当前版本尚无统一目标机证据。
- Users/scenarios: Magic8 Pro Air / MagicOS 11 用户完成日常对话、分支、历史、推荐题、长回答阅读等真实路径。
- Expected outcome: 每项均有实际结果与证据索引，明确区分已修复、未复现、仍开放、部分验证或设备阻塞；不能将未知标绿。
- Core flow and reversibility: 安装已验收 APK → 逐项执行固定步骤 → 保存无敏感数据证据 → 恢复测试页面；不删除或重置用户历史。
- Inputs/outputs: 已完成子任务和 APK 构建信息；输出为 #1–#10 矩阵、每项状态/步骤/预期/实际、证据链接与未决项。
- State and exception handling: 缺少目标机或新增诊断修复依赖未完成时保持 PARTIAL/blocked；未复现只记未复现，不等同于已修复。
- Constraints and trade-offs: 用户本轮仅授权 Harness 拆分；此任务是后续验收工作项，不在当前回合执行；不写业务代码。
- AI recommendation and rationale: 使用十项独立行而非一个总 smoke-test 结论，避免局部通过掩盖单项缺陷。
- User choice and custom additions: 目标设备 Magic8 Pro Air / MagicOS 11；本轮只拆 Harness。
- AI-autonomous decisions: 检查任务 107/108/109 的届时实际状态；诊断新增的修复任务必须在本任务进入 in_progress 前补入 depends_on。
- User-confirmation decisions: 不把没有设备证据的交互报告为已修复。
- Acceptance evidence: 当前待验 APK、设备与系统信息、十项逐条步骤/预期/实际、截图或安全日志索引、遗漏与缺口。
- Facts: 107 已验证；108 当前 verifying；109 已验证；上述状态会变化，执行时重新读取任务文件，不依赖本任务编写时的状态快照。
- Assumptions: 目标机届时可安装完整候选构建并重复操作。
- Unknowns: 诊断后是否出现新修复任务；目标设备是否可在最终回归阶段使用。
- Risks: 只测 happy path、跨入口漏测、泄漏对话/推理内容或凭旧 APK 得出结论。
- Deferred: 诊断后才出现的修复任务与依赖需在回归开始前纳入矩阵。
- Non-goals: 本任务不代替子任务 TDD、Java-only guard 或性能诊断；不把 smoke test 当独立 verifier。
- Skipped non-critical questions and reason: 无；设备和十项范围已确认。

## Goal

在所有相关子任务结束后，对 #1–#10 逐项执行 Magic8 Pro Air / MagicOS 11 目标机回归并汇总真实状态。

## Scope

### In scope
- 建立 #1–#10 独立结果矩阵，记录 APK/设备/系统构建、步骤、预期、实际、证据索引与状态。
- 核对所有修复任务、任务 107/108/109 的最新验收范围，覆盖推荐文字与分支热区不同入口。
- 复查隐私脱敏；将未修复、未复现、设备不可用分别列出，不自动关闭父需求。

### Out of scope
- 新生产实现、替代子任务验证、测试用户原始敏感对话、全仓 lint/build 或未授权提交。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: N/A — 最终目标机验证，不改运行时行为；按各子任务预期核对原有消息、分支、输入失败草稿及思考开启披露。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 对照父任务列出的 Activity、ChatPresenter/ChatWiring/ChatScreen、存储、生成双通道及分支导航入口；以具体子任务的活动路径为准。
- Native bridge or independent-client compatibility/update requirements: N/A — 不改契约。
- Behavior-neutral non-applicability reason, if applicable: 只进行集成验收，不改代码或配置。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: None — 汇总已由各子任务证明的行为，不创建新的行为主张。
- Authoritative code/config/migration evidence: 各实现任务的活动生产代码和测试证据，以及实际安装 APK 元数据。
- Documentation surfaces checked: 各子任务已更新的 Android 当前态文档与对应任务文件。
- Documentation files updated: None — 本任务为最终证据矩阵；如发现文档漂移，回到对应任务修复后再验。
- Producer/consumer/sibling/compatibility paths checked: 对照各子任务验收覆盖，不用总 smoke-test 替代入口级验证。
- Superseded phrases/examples reverse-searched: 按子任务报告核对；发现不一致时标记缺口。
- `documentation impact: none` reason and evidence, if applicable: 不改代码/文档，只汇总现有证据。
- Historical/PRD handling: 使用带日期与 APK/系统版本的目标机快照，不改写历史记录。

## Acceptance criteria

- [ ] 每个 #1–#10 都有独立状态、步骤、预期/实际及证据索引；未复现、开放、阻塞、部分验证与修复通过使用不同状态。
- [ ] 验收机确认为 Magic8 Pro Air / MagicOS 11，APK 版本/构建一致；未将模拟器或旧 APK 结果当作目标机证据。
- [ ] 若 #112/#113/#119 导出新的修复任务，进入本任务 in_progress 前已将其加入 depends_on 且通过任务校验；任何用户可见缺陷仍未闭环时父需求不得报告完成。
- [ ] 脱敏核查通过；任务 107/108/109 与所有新修复的范围、证据和状态均按执行时最新文件核对。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A — 目标机整体验收矩阵，不新增生产行为。
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
- Reason: TDD 不适用于纯验证任务。
- User approval: N/A
- Alternative verification: 逐项目标机步骤和证据矩阵。

## Implementation notes

- 本任务现在仅进入 ready 队列，必须等待依赖任务；诊断发现新增修复后先更新 depends_on 并运行 `.trae/scripts/validate_tasks.py`。
- 用户数据保持原状；截屏需检查并遮蔽账号、问题正文、推理内容及凭据。

## Verification report

- Verdict: PENDING
- Evidence: PENDING — 目标机十项回归尚未执行。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A — 无代码/断言变更。
- Gaps: 各子任务实现/诊断与最终设备使用情况待定。
- Risks: 依赖遗漏会导致在修复尚未就绪时误报回归通过。

## Handoff notes

- Completed: 已拆最终目标机回归矩阵并链接现有 107/108/109 与新任务。
- In progress: 等待依赖任务完成。
- Blocker: 当前各子任务尚未执行，目标机证据缺失。
- Unblock condition: 依赖全完成；新增修复任务写入 depends_on；目标机与待验 APK 可用。
- Next steps: 逐项运行十条真实路径，写证据索引，回填 PARTIAL/VERIFIED 结论。
- Watch-outs: 不可用“安装成功”替代交互验收；不因 #1 的文本选择已验证就关闭所有无响应反馈。
