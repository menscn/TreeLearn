# Treelearn Harness Task: 首页与主题级收口（示例主题、主题删除、根标题改名、未读标识）

id: T-2026-09-21-043
title: 首页与主题级收口（示例主题、主题删除、根标题改名、未读标识）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 330
shared_code_touched: true
depends_on: [T-2026-09-21-real-data-path-wiring-032.md, T-2026-09-21-navigation-page-archive-delete-039.md, T-2026-09-21-search-page-and-home-entry-040.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把首页上仍然存在或缺失的几个 P0 入口补齐：空状态引导目前只弹「该入口尚未接入本版本」；主题不能永久删除；根标题不能手动修改；可达界面（首页/对话）没有任何未读标识（PRD 6.2.1、6.2.2、6.2.9、6.2.16、6.2.17）。
- Problem/background: `HomeScreen.java:113-114` 把 `home_empty_guide` 接成 Toast；`HomeActions` 只有草稿/提交/推荐/主题/设置/模型/思考七个动作，没有主题级删除与根标题改名；`UnreadRollup` 与 `branches` 的两个未读序列列已有，但唯一显示未读的地方是画布节点卡与导航页。
- Users/scenarios: 新装用户从空状态被引导创建第一个主题；改名让主题身份更贴合自己的理解；不再需要的主题连同其分支被永久删除（需确认）；首页列表能看出哪个主题有未读。
- Expected outcome: 空状态引导有真实去路；主题可改名、可经确认永久删除；首页主题卡与对话页能显示未读数。
- Core flow and reversibility: 改名可逆（再改一次）；删除不可逆，必须先确认且文案写明「含分支数量」；未读清算单调前进（由库层 CHECK 保证）。
- Inputs/outputs: 输入既有 `TopicDao`/`BranchDao`、`UnreadRollup` 与 `HomePresenter`；输出新增的首页与对话页入口、动作与文案。
- State and exception handling: 删除确认前不得改库；改名失败给可见 notice；空主题名拒绝提交；未读读取失败降级为不显示徽标而不是显示 0 误报。
- Constraints and trade-offs: Java-only、API 28+、零 AndroidX；不新增 schema；布局只用 design token 档位；可点区域 ≥ 44×44 dp。
- AI recommendation and rationale: 主题级删除复用既有级联（`topics` 被删时 `branches` ON DELETE CASCADE），不写第二套递归删除逻辑——递归删除已有实现，重复会漂移。
- User choice and custom additions: 用户 2026-09-21 要求交付完整可用 APK。
- AI-autonomous decisions: 未读徽标放在首页主题卡与对话页分支标题旁；改名用系统输入对话框。
- User-confirmation decisions: 设备验证由用户装机实测（2026-09-21 决定）。
- Acceptance evidence: RED→GREEN 的 JVM 测试（空状态引导产生创建路径、改名落库、删除确认前库未变、删除后主题与其分支消失、未读读取失败不显示 0）、`:app` 单测与 `.\gradlew.bat check` 退出码 0、Java-only guard PASS、原文扫描证明 `home_entry_pending` 不再有生产者。
- Facts: `home_entry_pending` 文案当前只被 `HomeScreen.java:113` 使用；`topics` 对 `branches` 是 ON DELETE CASCADE；`UnreadRollup` 已存在；`HomeActions` 无主题级动作。
- Assumptions: T-032 已接好真实数据路径；T-039/T-040 已交付导航页与搜索页（本任务与其共用 `MainActivity` 宿主，须串行）。
- Unknowns: 真机上的对话框与徽标观感（无设备）。
- Risks: 与 T-039/T-040 并发修改 `MainActivity`/`HomeScreen`（故本任务排在二者之后）；删除确认信息不足；未读显示与导航页口径不一致。
- Deferred: 真机观感与像素证据。
- Non-goals: 不改未读的派生规则；不做跨主题搜索；不新增 schema。
- Skipped non-critical questions and reason: 「示例主题」在 PRD 6.2.16 的落点按空状态引导既有文案实现，不新增演示数据。

## Goal

首页的空状态引导、主题改名、主题永久删除（含确认）与未读徽标全部有真实实现，且不再有 `home_entry_pending` 占位。

## Scope

### In scope
- `HomeScreen` 的 `home_empty_guide` 由 Toast 改为真实去路（聚焦输入框并给出创建引导），删除 `home_entry_pending` 文案与引用。
- 首页主题卡新增「改名」与「永久删除」入口；删除走确认对话框并写明受影响的分支数量。
- 对话页分支标题旁显示该分支未读数；首页主题卡显示该主题未读汇总。
- `HomeActions`/`HomePresenter` 增加改名、主题删除、未读读取三个动作；`rootQuestionSubmitter` 之外的读写仍走既有 DAO。
- `strings.xml` 补齐改名、删除确认、未读徽标文案。

### Out of scope
- 未读派生规则的改动（既有 `UnreadRollup` + 两个序列列）。
- 导航页内的归档/删除（T-039 已交付，本任务只补首页侧入口）。
- 任何 schema 改动。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 空状态引导由占位变为真实；**保留**既有级联删除语义、`HomePresenter` 既有测试结论与「失败必须可见」的语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是 `HomePresenter` 的新动作；消费者是 `TopicDao`/`BranchDao` 与屏幕；宿主是 `MainActivity`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 空状态引导有真实去路；主题可改名与永久删除；首页与对话页显示未读；`home_entry_pending` 文案被移除。
- Authoritative code/config/migration evidence: `screen/HomeScreen.java`、`screen/ChatScreen.java`、`ui/HomePresenter.java`、`ui/HomeActions.java`、`res/values/strings.xml`、测试输出。
- Documentation surfaces checked: `android/README.md`（`:app` 入口索引与文案索引）、Android 技术方案 §9/§11、PRD 6.2.1/6.2.2/6.2.9/6.2.16/6.2.17。
- Documentation files updated: README 中「示例主题入口是 Toast 占位」与「可达界面无未读标识」的当前态表述改为真实实现。
- Producer/consumer/sibling/compatibility paths checked: 空状态 → 首个主题；改名 → 首页与对话标题同步；删除 → 主题消失；未读 → 阅读后递减。
- Superseded phrases/examples reverse-searched:「该入口尚未接入本版本」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: PRD 6.2.16 为需求依据，不改写。

## Acceptance criteria

- [x] 同一 JVM 测试命令先 RED 后 GREEN：断言空状态引导产生创建路径、改名落库并可读回、删除确认前库未变、确认后主题与其全部分支消失、未读读取失败时不显示 0 而隐藏徽标。
- [x] 原文扫描证明 `home_entry_pending` 在 `strings.xml` 与生产代码中都不再有生产者与引用。（verifier #3 复扫 `artifacts/evidence/T-043-verify3-scans-2026-09-24.txt`：0 命中。）
- [x] 删除不写第二套递归逻辑：原文扫描证明主题删除依赖既有级联（无新增按分支逐个删除的循环）。（verifier #3 复扫同文件：递归形态 0 命中；删除唯一走 `TopicDao.delete`，`SqliteHomeStore:80` 与 `SqliteNavigationStore:130` 复用同一 DAO + 级联。）
- [x] `:app:testDebugUnitTest`、`.\gradlew.bat check`、Java-only guard、设计 token 守门、文档影响记录齐全且退出码 0。（verifier #3 本轮实测：`check` EXITCODE=0 `P3-B3-gates-2026-09-24.txt`；Java-only guard PASS `P3-B3-no-kotlin-2026-09-24.txt`。）
- [x] 真机观感与像素证据记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得写成 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: Existing home production path had a Toast-only empty guide, no topic mutation actions,
  and no unread value in `TopicCard`.
- Command: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 1
- Observed failure: The pre-change home path had no testable rename/delete/unread behavior and still
  contained the `home_entry_pending` Toast placeholder.
- Failure reason validated: The failure was the documented missing behavior; the same command is the
  GREEN command below after the implementation landed.

### GREEN — minimum implementation
- Implementation: Added real empty-state focus behavior, topic rename/delete actions, confirmation
  dialog with branch count, unread total derived from sequence gaps, DAO-backed root title update and
  topic cascade delete; removed the pending string.
- Command: `.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain --rerun-tasks --no-build-cache`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`; the app JVM suite completed 358 tests with no failures.
- Regression evidence: `.\gradlew.bat check --no-daemon --console=plain --rerun-tasks` completed with
  `BUILD SUCCESSFUL`; Java-only guard passed. Real device launch stayed alive with `MainActivity` as the
  focused application window.

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- 本任务排在 T-039/T-040 之后：三者都要改 `MainActivity` 与 `HomeScreen`，并发会产生两个写者。
- 主题删除不可逆：确认文案必须写明受影响的分支数量；确认前不得改库。
- `home_entry_pending` 文案整体删除，不得以注释保留。
- 未读徽标与导航页/画布必须同源（都读 `UnreadRollup`），不得在屏幕里自己算。
- 不写任何正文（标题、问题、回答）到日志或证据文件。

## Verification report

- Verdict: PARTIAL
- Evidence: JVM and build evidence above; source scan confirms `home_entry_pending` has no remaining
  production/string match; device launch evidence was captured via `adb` on `AWSS013807000356`.
- Changed/deleted assertion rationale and equivalent behavior coverage: The placeholder click was
  replaced by focus behavior; topic deletion still delegates to `TopicDao.delete` and its existing
  cascade, while unread is calculated from `latest_stable_sequence - last_seen_stable_sequence`.
- Gaps: No scripted tap-through of rename/delete dialogs or pixel review yet; no real model key was
  available for end-to-end generation.
- Risks: The task exceeds its original soft line estimate and still needs independent verifier review.

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 等 T-032 组合根与 T-039/T-040 的宿主装配落地。
- Unblock condition: T-039 与 T-040 进入 verifying。
- Next steps: 实现后交独立 verifier；随后由发布任务重建签名 APK。
- Watch-outs: 不要新增第二份未读计算；不要保留占位文案。

## Verification report — 收尾复核（verifier #3，2026-09-24）

- Verdict: PASS。实现者自检 PARTIAL 的残留项（独立 verifier 未复核）由本轮补上。
- 验收扫描（独立复跑，`artifacts/evidence/T-043-verify3-scans-2026-09-24.txt`）：`home_entry_pending` 在源码与资源 0 命中；递归删除形态（deleteBranch/recursiveDelete/deleteChildren/deleteDescendants）0 命中；主题删除唯一走 `TopicDao.delete`（`SqliteHomeStore:80`、`SqliteNavigationStore:130` 复用同一 DAO + 级联），无第二套判定。
- RED→GREEN：同一命令 `:app:testDebugUnitTest --rerun-tasks --no-build-cache` 先 1 后 0（任务正文记录），GREEN 358 例 0 失败；本轮门禁 `check` EXITCODE=0（`P3-B3-gates-2026-09-24.txt`）、Java-only guard PASS（`P3-B3-no-kotlin-2026-09-24.txt`）。
- 设备项（对话框/徽标观感、像素）：`deferred — 用户自行装机验证（user decision 2026-09-21）`；实现者记录的真机启动（adb 设备 `AWSS013807000356`）是补充观察，不替代脚本化验收。
- 规模：超 200 软上限（实现者 Risks 自述），未超 500 硬上限的记录存在；共享文件（MainActivity/HomeScreen）归属增量无法与并行 lane 分离，按「无越线证据 + 软上限警告」处理。
- 未读计算与导航页同源（`UnreadRollup`，不在屏幕自算）：实现记录与扫描一致，维持 PASS。
