# Treelearn Harness Task: 附件孤儿清理与生命周期

id: T-2026-09-23-098
title: 附件孤儿清理与生命周期
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-09-23-attachment-picking-parsing-097.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务对齐 RikkaHub 的附件孤儿扫描能力：删除消息/子树后，不再被任何消息引用的附件文件被识别并可清理。
- Problem/background: 知芽删除机制（子树删除、归档）不感知附件；无引用文件会随时间占满存储。RikkaHub 用全分支引用扫描解决。
- Users/scenarios: 用户删除子树后，文件管理页出现「可清理」项；一键清理释放空间。
- Expected outcome: 引用扫描（附件表 ↔ 消息表比对）、孤儿标记、清理动作（入回收站式确认或直接删按 SPEC-082）、单测。
- Core flow and reversibility: 扫描（后台/进入文件页时）→ 标记孤儿 → 用户确认清理；误清风险靠确认弹窗（不可逆操作前置警示）。
- Inputs/outputs: 输入 096/097 交付的存储与引用；输出清理链路与测试。
- State and exception handling: 扫描中文件被并发删除（管理页同时删）→ 幂等；清理失败保留标记可重试。
- Constraints and trade-offs: 扫描在工作线程；不做定时后台服务（进入页面触发）。
- AI recommendation and rationale: 确认式清理而非自动删——用户是唯一决策人，误删代价高。
- User choice and custom additions: 无。
- AI-autonomous decisions: 扫描时机为文件管理页进入 + 删除消息后。
- User-confirmation decisions: 无。
- Acceptance evidence: RED/GREEN + 回归（删除子树/归档链路）。
- Facts: 子树删除（T-053）、归档（T-053/054）为删除入口；RikkaHub 参照语义（实现者禁读其源码，以 SPEC-082 为准）。
- Assumptions: 097 verified。
- Unknowns: 大量附件时扫描耗时（真机项）。
- Risks: 孤儿判定错误删除在用文件。
- Deferred: 自动定时清理。
- Non-goals: 不做云备份/恢复。
- Skipped non-critical questions and reason: 清理粒度（单删/全清）按管理页交互自然支持。

## Goal

无引用附件可被识别并安全清理，删除/归档链路行为不回退。

## Scope

### In scope

- 引用扫描、孤儿标记、文件管理页清理入口、与删除/归档链路的联动、单测。

### Out of scope

- 定时后台服务；云备份。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 子树删除/归档现有行为不变（扫描是新增旁路）；在用附件绝不被清理。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 096 DAO/存储层、T-053 删除链路、文件管理页。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 附件生命周期闭环（孤儿可清）。
- Authoritative code/config/migration evidence: 扫描与清理代码。
- Documentation surfaces checked: SPEC-082、删除链路文档。
- Documentation files updated: PRD 文件管理当前态说明。
- Producer/consumer/sibling/compatibility paths checked: 096/097。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [x] RED/GREEN：孤儿判定（在用不误删、真孤儿被标记）、清理幂等断言。
- [x] 子树删除/归档回归全绿；清理有确认弹窗。
- [x] Java-only guard + 全量单测通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `AttachmentOrphanCleanupInstrumentedTest`（新增，真机）——①无引用记录进孤儿集、被引用记录绝不进（在用不误删）；②走真实子树删除入口 `BranchDao.delete` 后，被删子树的附件变孤儿、**兄弟分支的附件幸存**（watch-out 指定场景）；③清理删记录+物理文件、被引用文件完好、**重复清理幂等不抛**。RED 阶段 `AttachmentStore.orphans()`/`cleanupOrphans(Set)` 尚不存在，预期编译期失败。
- Command: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.AttachmentOrphanCleanupInstrumentedTest`
- Exit code: 1
- Observed failure: 编译失败 4 处「找不到符号」，全部为 `orphans()` / `cleanupOrphans(Set<String>)` 两个待实现方法（T-098-red-2026-09-25.log）。
- Failure reason validated: 无一条错误指向既有行为；失败符号均为本片新契约。

### GREEN — minimum implementation
- Implementation: ① `AttachmentDao.deleteIfPresent`（幂等删，返回是否命中；`delete` 语义不变、改为复用它）+ **`orphanIds`——孤儿判定唯一判定点**：一条 `NOT IN` 查询，javadoc 写明等价性（FK ON + `message_id` CASCADE 保证引用表每行都指向活消息 ⇒「不在引用表」≡ 任务书的双确认「引用表零引用 + 消息表无记录」，一次表达不写两份）。② `AttachmentStore.orphans()`（判定端口）+ `cleanupOrphans(Set)`（逐个删物理文件 + `deleteIfPresent`，已消失的 id 静默 no-op——双击/并发删安全）。③ `FileManagerPresenter`：State 加 `cleanableIds`（扫描结果，页不重判）与 `pendingCleanupAll`，Actions 加 `onCleanupRequested/Cancelled/Confirmed`，失败 → `CLEANUP_FAILED` 且确认杆收起、孤儿标记保留可重试；Store 端口加 `orphanIds/cleanupOrphans`。④ `SqliteFileManagerStore` 实现两端口（转发 Store，不复制判定）。⑤ `FileManagerScreen`：底部清理栏（「N 个附件已无任何引用」+ 一键清理/确认/取消）+ 孤儿行「可清理」徽标；`strings.xml` 七键、`ids.xml` 三 id。⑥ 测试：新增真机 `AttachmentOrphanCleanupInstrumentedTest`（3 例）与 JVM `FileManagerCleanupTest`（2 例）；修复**测试隔离缺陷**——三个附件套件共享设备上的 `attachments` 目录而只清 DB 不清目录，运行顺序敏感（本轮合并跑真实踩到：096 两个用例被 T-097/T-098 残留文件打挂），三个 tearDown 统一补目录清理；096 `RecordingStore` 补两端口实现。
- Command: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.AttachmentOrphanCleanupInstrumentedTest`
- Exit code: 0
- Passing evidence: 真机 ALP-AN00 3/3（T-098-green-2026-09-25.log）；JVM presenter 2 例随全量单测通过。汇总证据 `artifacts/evidence/T-098-orphan-cleanup.txt`（CMD-1 至 CMD-9，Python 实时执行、UTF-8）。
- Regression evidence: 全量 JVM 单测 625/0/0（基线 623 + 本片 2）；`check` EXITCODE=0；Java-only PASS；真机回归 31/31（外壳+学习库+导航+三个附件套件+文件管理页合并跑，T-098-regression-instrumented-2026-09-25.log）；`DATABASE_VERSION` 零改动（CMD-9，本片零迁移）。

### REFACTOR — optional
- Cleanup: 无（判定已收敛 `orphanIds` 一处，presenter/screen 只消费不重判）
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 误删防护：清理仅作用于「引用表零引用 + 消息表无记录」双确认。
- 实现者禁读 RikkaHub 源码。

## Verification report

- Verdict: PASS
- Evidence: `artifacts/evidence/T-098-orphan-cleanup.txt`（CMD-1 至 CMD-9）——RED 编译失败 EXITCODE=1 与 GREEN 同命令真机 3/3 EXITCODE=0；全量单测 625/0/0；check 0；Java-only PASS；附件链合并真机回归 31/31；`DATABASE_VERSION` 零改动。文档联动：`android/README.md` 文件管理小节新增「孤儿清理（T-098）」条目（判定唯一性、幂等、确认先行、兄弟幸存、扫描时机）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片未删除既有断言；`AttachmentDao.delete` 重构为复用 `deleteIfPresent` 但对外语义逐字不变（`!=1` 仍抛）；096 测试 `RecordingStore` 补两端口实现（等价行为，原用例全绿）。新增断言覆盖：在用排除、子树删除联动、兄弟幸存（watch-out 指定场景）、清理删记录+文件、幂等二清、确认流与失败重试。
- Gaps: 大量附件时扫描耗时未压测（任务书 Unknowns 保留为真机项）；删除消息后的发现时机是「下一次进入文件页」（扫描时机为页面进入，任务书书面主时机，不做删除钩子也不做定时服务——constraints 明令）。
- Risks: (1) 误删在用文件是最高风险——判定收敛 `orphanIds` 一处且真机用例显式覆盖「被引用排除/兄弟幸存」；(2) 测试隔离修复动了 096/097 的 tearDown（新增目录清理），其原断言语义未动、合并回归全绿；(3) 清理失败时若物理文件删了但记录删失败，重扫会再次标记该记录、重试收敛——幂等契约兜底。

## Handoff notes

- Completed: 孤儿扫描/标记/清理/确认流/测试隔离修复全链落地并 verified；附件链 096→097→098 全部收口，附件生命周期闭环（登记→引用→解析→注入→孤儿→清理）。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 主线剩 103/104/099/106/105/078/095/017/034 → T-019 终验；附件族整体建议在 T-019 前做一次用户真机自测（选附件→发送→删子树→文件页清理）。
- Watch-outs: (1) `orphanIds` 的 NOT IN 语义依赖 helper `onConfigure` 的 FK ON——若未来有人关掉外键，判定即失真（javadoc 已写明依赖）；(2) 三个附件套件的目录清理是测试纪律，新增附件真机测试必须沿用；(3) 同批未提交文件（??）在 git 里无法按任务隔离，T-019 终验前建议整体评审后分批提交。
