# Treelearn Harness Task: 全屏编辑器

id: T-2026-09-23-077
title: 全屏编辑器（composer 3.7，用户拍板要做）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-functional-visual-absorption-071.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 90
shared_code_touched: true
depends_on: [T-2026-09-23-chat-page-visual-redesign-spec-073.md, T-2026-09-23-composer-input-capabilities-076.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 功能与对话页视觉分层吸收；本任务实现矩阵条目 3.7（全屏编辑器）。
- Problem/background: T-076 曾判定全屏编辑器「不做」（理由：073 当时无视觉规格、旋转/重建丢失已由持久化覆盖）。用户 2026-09-24 明确推翻：「全屏编辑器要做」。073 §5.1 已补视觉规格（轻流程）。
- Users/scenarios: 学习者在小屏键盘上写长问题，需要全屏空间编辑草稿与引用上下文。
- Expected outcome: composer 控件行新增全屏编辑入口 + 全屏编辑覆盖层（Java/XML），草稿实时回写、完成/返回保存、归档与无 Key 态入口灰显。
- Core flow and reversibility: 点入口 → 全屏层（标题/引用只读/大草稿框/取消+完成）→ 完成或返回键=保存并关闭，焦点回 composer。纯 UI 层，可回滚。
- Inputs/outputs: 输入 073 §5.1 规格、076 的 `ChatDraftStore` 链路与三态实现；输出 `android/app/**` 变更。
- State and exception handling: 空草稿入口灰显；S-ARCHIVED/S-NOKEY 灰显+提示；进程重建时全屏层内容随草稿持久化恢复；低动效直接切换。
- Constraints and trade-offs: Java-only、无新依赖、DialogFragment 或全屏 View 二选一由实现者定；触达 ≥44dp；新文案键中文（仓库单语约定）；不改分支/存储/提示词契约。
- AI recommendation and rationale: 独立小任务而非重开 076（076 已 verified，重开破坏状态机语义）。
- User choice and custom additions: 用户 2026-09-24「全屏编辑器要做」。
- AI-autonomous decisions: 入口位置与形态细节按 073 §5.1；取消键也保存草稿（草稿无破坏性）。
- User-confirmation decisions: 无遗留（规格与决策均已定）。
- Acceptance evidence: RED/GREEN 证据、`gradlew check` 退出码 0（含 no-kotlin/依赖方向 guard）、行数 ≤200。
- Facts: 076 已交付 `ChatDraftStore`/`SharedPreferencesChatDraftStore`/`SendState`；073 §5.1 已有规格。
- Assumptions: 076 的持久化链路可直接复用（键击回写）。
- Unknowns: 真机 IME 与全屏层组合表现（无设备，留待人工）。
- Risks: 全屏层与 edge-to-edge（values-v35 主题债）交互；DialogFragment 状态恢复。
- Deferred: 无。
- Non-goals: 不改 076 已交付的三态/持久化语义；不做 Markdown 预览；不加语音/附件。
- Skipped non-critical questions and reason: 实现载体（DialogFragment vs 全屏 View）由实现者按现有代码定，无需再问。

## Goal

实现 073 §5.1 全屏编辑器：入口、全屏覆盖层、草稿实时回写与状态降级，行为与规格逐条一致。

## Scope

### In scope
- composer 控件行展开入口钮（≥44dp、空草稿/归档/无 Key 灰显规则）。
- 全屏编辑覆盖层（标题/关闭、引用只读行、大草稿框、取消/完成动作行）。
- 草稿实时回写（复用 076 持久化链路）、完成/返回键保存关闭、焦点回归、TalkBack 播报、低动效。
- 相关 strings/drawable/dimens 与单测（Presenter 态 + 覆盖层行为断言）。

### Out of scope
- 076 已交付语义的变更；Markdown 预览；附件/语音；`layout-sw600dp` 专用差异（覆盖层为全屏自适应）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增入口与覆盖层；composer 既有三态、草稿持久化、引用行行为不得回退（076 测试必须保持绿）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `screen_chat_body.xml` composer、`ChatScreen.java`、`ChatPresenter.java`、`ChatViewState.java`（仅加只读字段/回调，不改既有语义）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用（有 UI 行为变更，走 TDD）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 对话页新增全屏编辑入口与覆盖层。
- Authoritative code/config/migration evidence: `Docs/04-ux-design/知芽（TreeLearn）对话页视觉重做规格_V1.0.md` §5.1；T-076 交付记录。
- Documentation surfaces checked: `android/README.md`（composer 章节需同步）。
- Documentation files updated: 实现时同步 `android/README.md`。
- Producer/consumer/sibling/compatibility paths checked: 本文件 Scope 已列。
- Superseded phrases/examples reverse-searched: T-076 交接备忘「全屏编辑器不做」已标注被本任务推翻。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 矩阵 3.7 由 P1「第一批备选」转为已实施，矩阵不回改（历史记录）。

## Acceptance criteria

- [ ] 空草稿/归档/无 Key 态入口灰显且点击有对应反馈；有草稿时进入全屏层。
- [ ] 全屏层：引用只读展示、大草稿框占满剩余高度、取消/完成/系统返回均保存草稿并关闭、焦点回 composer。
- [ ] 编辑实时回写 `ChatDraftStore`（进程重建后可恢复）；低动效直接切换；TalkBack 进入/关闭播报。
- [ ] RED 先失败后 GREEN；`gradlew check` 退出码 0；076 既有测试不回退；生产行 ≤200。
- [ ] 新文案键、≥44dp 热区、`android/README.md` 同步。

## TDD execution record

### RED — failing test first
- Test/scenario: 全屏编辑器（新增 `ChatFullscreenEditorTest`：入口四态判定、完成/取消/系统返回均只关不清、键击回写 ChatDraftStore 且重建可恢复）。RED 为 13 个缺失符号（fullscreenEditing / withFullscreenEditing / fullscreenEditEntryAvailable / onFullscreenEditOpened / onFullscreenEditClosed）。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.*ChatFullscreenEditorTest*"`
- Exit code: 1
- Observed failure: 编译期 13 个未解析符号，全部对应本任务目标行为。
- Failure reason validated: 缺失符号与实现一一对应，实现后转绿。

### GREEN — minimum implementation
- Implementation: 新增 `res/layout/overlay_fullscreen_editor.xml`（55 行）与 `ic_chat_expand.xml`（10 行）；`ChatScreen.java`（+74，覆盖层渲染与系统返回收敛）、`ChatViewState.java`（+19，fullscreenEditing 只读快照位）、`ChatPresenter.java`（+8）、`ChatActions.java`（+2）、`MainActivity.java`（+3）、`screen_chat_body.xml`（+11，44dp 入口钮）、`screen_chat.xml` 与 sw600dp 各 +4、strings +7 键。载体选全屏 View（仓库零 AndroidX 无 fragment 依赖，无 Dialog 状态恢复问题）。共 197 行。
- Command: `./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.*ChatFullscreenEditorTest*"`
- Exit code: 0
- Passing evidence: 同命令 BUILD SUCCESSFUL；全量 `gradlew check` 另行退出码 0，证据 `artifacts/evidence/.t077-green.log`、`.t077-check.log`（`verifyDependencyDirection: PASS`、`verifyNoKotlinDex: PASS`）；076 既有测试全绿。
- Regression evidence: `.t077-check.log`；行数 197≤200（`.t077-linecount.log`，超 90 目标未超 200 上限——软限制警告级，无需规模例外）。

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（tdd_mode: required）。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 载体（DialogFragment vs 全屏 View）由实现者按现有代码结构选定，报告说明理由。
- 覆盖层内容属 ViewState 只读快照 + 既有回调，不新增状态机。
- 键击回写若沿用 076 的每键 `apply()`，沿用即可；如需节流不得改变恢复语义。

## Verification report

- Verdict: PASS（独立 verifier 2026-09-24：亲跑目标测试退出码 0、verifyNoKotlinDex PASS、Java-only 扫描为空、§5.1 规格逐条核对、076 既有测试未改弱、规模 197≤200、无禁区路径）
- Evidence: 证据文件 `.t077-red.log`（exit 1，13 缺失符号）、`.t077-green.log`、`.t077-check.log`（exit 0，双 guard PASS）、`.t077-linecount.log`；规格逐条：四态灰显（`ChatViewState.java:384-386`）、引用只读行（`overlay_fullscreen_editor.xml:30-35`）、取消/完成/系统返回同归 close 且不清草稿（`ChatPresenter.java:312-313`、测试钉死 `ChatFullscreenEditorTest.java:85-102`）、焦点回归+光标末尾（`ChatScreen.java:483-493`）、44dp 入口钮与中文 contentDescription。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。
- Gaps: ① 规格原文「草稿非空或聚焦时可用」实现收窄为「非空可用」（契约口径成立，偏差已记录）；② 引用行为视觉等价的 TextView 而非字面复用 quote_row；③ 系统返回键仅 Presenter 层单测，真机 IME×手势组合待人工。
- Risks: 全屏层与 edge-to-edge 组合待真机观察；overlay 布局为控行数用紧凑属性风格（文件头已注明）。

## Handoff notes

- Completed: 任务创建；073 §5.1 规格补齐；实现完成（2026-09-24，android-dev 子代理，TDD 证据齐备，生产行 197/200，超 90 目标为软限制警告）。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: android-dev 子代理 TDD 实现 → 独立 verifier 验收。
- Watch-outs: 076 既有测试必须保持绿；不得把覆盖层做成新状态机。
