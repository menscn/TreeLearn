# Filename: T-2026-10-25-interrupt-card-stack-095.md
# Codex Harness Task: 中断卡把风险说明从按钮副标题改成三行（AC1）

id: T-2026-10-25-095
title: 中断卡三层排布（AC1）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 40
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 AC1（严重）——中断卡把「重试会怎样、已保留的文字还能不能复制」排在按钮右侧，成为按钮的副标题。
- Problem/background: 原先风险说明与重试键同一行，说明只能占按钮剩下的宽度；在手机上它被压成两三行窄条，而它解释的正是按钮不会做的事。用户读不到就没法判断该不该点。
- Users/scenarios: 生成中断的人——他们此刻最需要知道「我写的东西还在不在」「重试会重复扣费吗」。
- Expected outcome: 卡片三层：发生了什么 → 已保留的文字还能做什么 → 重试键另起一行、独占自己的 44dp。
- Core flow and reversibility: 只改 `item_chat_message.xml` 的层级与宽度。回退＝还原同一行排布。
- Inputs/outputs: 输入是评审板截图与 AC1 描述；输出是 `chat_failure_text` / `chat_retry_hint` / `chat_retry` 的顺序与宽度。
- State and exception handling: 三层都常驻，不因网络或状态显隐；`chat_failure` 整卡仍按失败态显隐（既有逻辑不动）。
- Constraints and trade-offs: 卡片会变高；可读性优先。重试键仍是 44dp 触控地板，`wrap_content` 宽度。
- AI recommendation and rationale: 让说明独占整宽，是把「读」放在「点」之前；按钮单独成行也避免它被读成说明的一部分。
- User-confirmation decisions: 22 条全做。
- AI-autonomous decisions: ① 三层顺序固定为 发生了什么／还能做什么／动作；② 说明用 `text_cap` 12sp 与 ink3，动作键用 `text_sm` 13sp；③ 行距用 `space_s1`（8dp）串联。
- Acceptance evidence: `ChatMotionLayoutStabilityTest.theFailureBarStacksItsReasonAboveItsKey` 先红后绿。
- Facts: `095-red.txt`（1 failed / 24 tests，`the note owns the card's full width instead of the key's leftover`）；`095-green.txt`（BUILD SUCCESSFUL）。
- Assumptions: 文案键 `chat_retry_hint` 已存在且内容就是「已保留的文字」那条说明（未改文案）。
- Unknowns: 无。
- Risks: 卡片高度上升，长回答页里会多占一屏的一点空间；这是把说明从「看不见」移到「看得见」的必然代价。
- Deferred: 失败态的可折叠「详情」不做——当前文案已经足够短。
- Non-goals: 不改失败文案；不改重试逻辑；不改其它消息行（用户/AI/分支）。
- Skipped non-critical questions and reason: 未问「要不要给说明加一个图标」——加图标会把阅读顺序再切碎，且没有对应发现。

## Goal

让中断卡的风险说明独占整行、位于重试键之上，使「读」发生在「点」之前。

## Scope

### In scope
- `android/app/src/main/res/layout/item_chat_message.xml` 的 `chat_failure` 子树。
- `ChatMotionLayoutStabilityTest` 的三层顺序钉子。

### Out of scope
- 失败文案与重试动作；其它消息状态；Web 面（无对应发现）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 说明的位置与宽度；必须保留失败卡在其状态下的显隐、重试键的 44dp 触控地板、`chat_failure_text` 的红色语义与 TalkBack 播报。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `item_chat_message.xml`；消费者 `ChatScreen` 对 `chat_retry` 的点击绑定与失败态显隐。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 中断卡从「说明 + 按钮同行」变成三层；说明不再受按钮宽度约束。
- Authoritative code/config/migration evidence: `item_chat_message.xml:151-190`。
- Documentation surfaces checked: `.codex/rules/android.md`、`.codex/rules/observability.md`（失败态语义未变）。
- Documentation files updated: 无需改文档；层级理由写在布局注释。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "chat_retry_hint" android/app/src` 只命中布局与字符串。
- Superseded phrases/examples reverse-searched: 无「副标题」表述残留。
- `documentation impact: none` reason and evidence: 只改布局层级，不改任何对外契约或文案。
- Historical/PRD handling: 不改写 056 的对话面历史。

## Acceptance criteria

- [x] 三个节点的文档顺序是 `chat_failure_text` → `chat_retry_hint` → `chat_retry`。
- [x] 说明 `layout_width="match_parent"`，不再吃按钮剩下的宽度。
- [x] 重试键有自己的 `layout_marginTop`，单独成行且保持 44dp。
- [x] 布局稳定性判据（按钮不因占位符出现而移动）仍绿。

## TDD execution record

### RED — failing test first
- Test/scenario: `theFailureBarStacksItsReasonAboveItsKey`——顺序、说明整宽、按钮另起一行三条同时钉住。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.ui.ChatMotionLayoutStabilityTest" --tests "com.treelearn.app.ui.ChatMessageRowLayoutTest"`
- Exit code: 1
- Observed failure: `ChatMotionLayoutStabilityTest > theFailureBarStacksItsReasonAboveItsKey FAILED`，`java.lang.AssertionError: the note owns the card's full width instead of the key's leftover`（1 failed / 24 tests）。
- Failure reason validated: 把布局还原到 HEAD 后判红，红点正是「说明不再是 match_parent」；顺序断言与按钮 margin 断言在同一条用例里，说明这次红不是文案或 id 缺失。
- Evidence: `artifacts/evidence/ui-readability-parity-2026-10-25/095-red.txt`。

### GREEN — minimum implementation
- Implementation: `chat_failure` 子树改为竖向三层，说明 `match_parent` + `space_s1` 上边距，按钮 `wrap_content` + 44dp + `space_s1` 上边距。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.ui.ChatMotionLayoutStabilityTest" --tests "com.treelearn.app.ui.ChatMessageRowLayoutTest"`
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL，24 tests（`095-green.txt`）。
- Regression evidence: Android 全量 108 套 / 949 用例绿；`ChatMotionLayoutStabilityTest` 的其它布局稳定性断言（跟随键、生成提示、取消标记）全部保持绿。

### REFACTOR — optional
- Cleanup: 无。
- Regression evidence: 不适用。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿。
- Failure identity and repeat count: 单次单缺陷。
- Stop condition hit: 三层顺序与整宽同时绿。
- Handoff: 105 的整跑包含本片。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 「说明整宽」是本片的核心：一旦它回到与按钮同行，任何关于「重试会不会重复消耗」的长句都会被压成窄条。
- 本片不动文案；说明句的措辞仍由既有字符串承担，避免把版式问题与文案问题混在一处。

## Verification report

- Verdict: PASS
- Evidence: `095-red.txt`／`095-green.txt`；Android 全量 949 用例绿（当次 `--rerun-tasks` 实跑）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 新增布局顺序钉子；无删除。
- Gaps: 未做真机截图（本轮真机核验只允许 `adb install -r` + 截图，见项目记忆）。
- Risks: 卡片高度上升。

## Handoff notes

- Completed: 中断卡改三层，说明独占整宽。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 无。
- Watch-outs: 别把说明塞回按钮右侧以「省高度」——那正是 AC1 的问题。
