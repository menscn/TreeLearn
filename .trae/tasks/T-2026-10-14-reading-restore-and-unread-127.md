id: T-2026-10-14-127
title: 阅读恢复与未读口径——未读只在最新回答进入视口后清除，打开分支回到上次阅读位置
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-13-web-android-alignment-123.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: frontend
stack: frontend
estimated_production_lines: 60
shared_code_touched: false
depends_on: [T-2026-10-14-generation-closure-126.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-13-web-android-alignment-123`（回退纸上书房 + 逐屏重排）。W2 可用性闭环的第二步；父需求验收标准第 4 条「进入分支不清未读；看到最新回答后标记已读」落在本任务。
- Problem/background（源码核对，全部为实测行号）:
  - Android 权威规则在共享域 `web/shared/domain/src/main/java/com/treelearn/core/domain/ViewportSeen.java:38-51`：`unreadCount == 0` 直接返回 false；否则取**最后一条 AI 且带 `activeRevisionId` 的消息**为「最新回答」，只有它落在 `visibleMessageIds` 里才 `branch.markSeenThrough(branch.latestStableSequence())`。类注释明确写了两个错误捷径：「屏幕打开过」会把没上屏的回答标成已读；「有任意回答可见」会在最新一条还在折叠下方时清掉徽标。
  - Android 阅读位置在 `android/app/src/main/java/com/treelearn/app/reading/ReadingSession.java:46-54`：`open()` 先 `store.anchorOf(branch.id)`，无锚点 → `ReadingPosition.liveEnd(messages)`（到最新端）；有锚点 → `stored.resolve(messages, itemHeightPx)`。锚点解析不出来时也回 liveEnd。
  - Web 现状：`web/frontend/src/pages/BranchWorkspace.tsx:76-79` **进入分支即** `markBranchSeen(branch.id, branch.latestStableSequence)`——正是 Android 明令否掉的捷径。`loadConversation` 返回的 `readingAnchor`（`api/types.ts:78`）**从未被读取**，所以「回到上次读到的地方」不存在。
  - 旧断言 `BranchWorkspace.test.tsx:244-249`「marks the branch seen on entry when it has unread answers」钉住的就是这个错误口径，本任务必须改写（父需求 Verification report 已预告）。
- Expected outcome: 未读只在最新回答进入视口后清除；打开分支时若有阅读锚点则回到该消息位，无锚点则到最新端；离开时仍保存位置。
- Facts: `markBranchSeen(branchId, seenThroughSequence)` 与 `saveReadingPosition(branchId, anchor)` 已在 `store/topicData.ts:23/28`，端点与语义不变。契约 fixture `conversation()` 已带 `readingAnchor` 字段可直接给值。
- AI-autonomous decisions: 用 `IntersectionObserver` 观察「最新回答」这一个元素（Android 是集合判定，Web 只需判定那一条，等价）；`IntersectionObserver` 在环境不可用时（jsdom/旧浏览器）静默降级为「不自动清除未读」，不刷新页面、不发请求；阅读位置恢复只用锚点的 `messageId`（锚点消息不在当前消息列表里 → 回最新端），像素级 `listItemOffset/viewportOffset` 不在本任务内（见 Gaps）。
- Non-goals: 回答动作行与划词浮条（T-128/T-129）、推荐即发送（T-130）、首页与侧栏（T-131/T-132）。

## Goal

打开一个分支不会因为「打开过」就把未读清掉；只有真的看到最新那条回答才清。下次进来回到上次读到的位置。

## Scope

### In scope
- 移除「进入分支即标记已读」，改为「最新回答进入视口才标记已读」
- 打开分支按 `readingAnchor` 恢复到对应消息位；无锚点/锚点失效则到最新端
- 离开分支时仍保存阅读位置（已有逻辑保留）

### Out of scope
- 像素级滚动偏移还原（`listItemOffset` / `viewportOffset`）
- 未读徽标的视觉形制（属 T-131/T-134 页面重排）
- 后端任何改动

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 改变「何时清除未读」与「打开时滚到哪里」；保留端点、`commandId`、离开保存、布局切换、生成闭环（T-126）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `pages/BranchWorkspace.tsx`（唯一改动面）、`store/topicData.ts`（只读调用）、`components/SideBySideView.tsx`（B 布局不参与视口判定）。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 未读清除时机、打开分支的初始滚动位置。
- Authoritative code/config/migration evidence: `ViewportSeen.java`、`ReadingSession.java`、`BranchWorkspace.tsx`。
- Documentation surfaces checked: `web/contracts/api.md`（端点与字段未变，无需同步）。
- Documentation files updated: 无。
- Producer/consumer/sibling/compatibility paths checked: `SideBySideView` 不读 `readingAnchor`、不参与未读判定。
- Superseded phrases/examples reverse-searched: 旧断言标题「marks the branch seen on entry」已定位并改写。
- `documentation impact: none` reason and evidence, if applicable: 无端点、契约、文档语句变化。
- Historical/PRD handling: 不适用。

## Acceptance criteria

- [x] 打开未读分支**不**立即发 `/api/branches/:id/seen`
- [x] 最新回答进入视口后才发一次 seen，且 `seenThroughSequence` 取 `latestStableSequence`
- [x] 未读数为 0 的分支不发送 seen 请求
- [x] 有 `readingAnchor` 时打开分支定位到该消息位；锚点消息不在列表里则到最新端
- [x] 无 `readingAnchor` 时到最新端
- [x] 离开分支仍保存阅读位置
- [x] 全量门禁绿：vitest / tsc / eslint / build / 三守门
- [x] 改写后的断言经变异检验为可证伪

## TDD execution record

### RED — failing test first
- Test/scenario: `web/frontend/src/pages/BranchWorkspace.test.tsx` 删除旧断言「marks the branch seen on entry when it has unread answers」（与 Android `ViewportSeen` 口径相反），新增 `describe('BranchWorkspace unread and reading restore (T-127)')` 四条用例：进入不发 seen、最新回答进视口后才发且 `seenThroughSequence` 取 `latestStableSequence`、未读 0 永不发、按 `readingAnchor` 恢复位置、无锚点/锚点失效到最新端。
- Command: npx vitest run src/pages/BranchWorkspace.test.tsx
- Exit code: 1
- Observed failure: 三条失败（第四条为负向断言，实现前天然成立）——`expected [] to include 'msg-a1'`（观察器未建立/未滚动）、`expected [] to include 'msg-a1'`（有锚点也不恢复）、`expected [] to include 'msg-a2'`（不到最新端）。
- Failure reason validated: 证据 `artifacts/evidence/T-127-reading-restore-unread-red.txt`（EXITCODE=1，3 FAIL）。该证据用 **HEAD（T-127 实现之前）的 `BranchWorkspace.tsx`** 跑**最终版测试**重采——探针在 RED 之后被加固过，重采是为了让 RED 与最终断言同版本。

### GREEN — minimum implementation
- Implementation: `BranchWorkspace.tsx:37` `Loaded` 增加 `anchor`（来自 `conversation.readingAnchor`）；`:72-76` 载入时写入 anchor 并把进入即 `markBranchSeen` 的两行删掉；`:90-100` 新增恢复 effect（`restoredRef` 保证每分支只恢复一次，`anchored ?? liveEnd`，锚点消息不在列表里即回最新端）；`:101-121` 新增「最新回答」判定（最后一条 AI 且 `activeRevisionId !== null`，对齐 `ViewportSeen.newestStableAnswer`）+ `IntersectionObserver` 视口 effect（`unreadCount === 0` 直接返回、只观察那一条、进入视口后 `markBranchSeen(branch.id, branch.latestStableSequence)` 并 disconnect）。测试侧新增 `installIntersectionObserver()` / `installScrollSpy()` 两个探针，`installFetch` 补 `/seen` 与 `/reading-position` 两条路由。
- Command: npx vitest run src/pages/BranchWorkspace.test.tsx
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-127-reading-restore-unread-green.txt`——目标文件 22/22；全量 `npx vitest run` 29 files / 216 tests（前值 213：新增 4 条、删除 1 条）。
- Regression evidence: 同证据文件——`npx tsc --noEmit`、`npx eslint .`、三个守门、`npx vite build` 均 EXITCODE=0。T-126 的生成闭环用例、分叉、批注、草稿、布局切换全部保持绿；端点与 `commandId` 语义未变。

### REFACTOR — optional
- Cleanup: 未做额外重构。旧断言删除处留一行注释指向新的 T-127 describe，避免后来者以为「进入即已读」仍被覆盖。
- Regression evidence: 见 GREEN 证据文件的同一批门禁。

### Mutation verification（反恒真配对）
- 三个变异各改一处生产代码，重跑同一条命令；备份 `artifacts/tmp/t127-mutation/`，已还原并 grep 回读确认无残留。证据：`artifacts/evidence/T-127-reading-restore-unread-mutation.txt`。
- MUTATION A：`anchored ?? liveEnd` → `liveEnd` → `1 failed | 21 passed`，失败恰为「按锚点恢复」。
- MUTATION B：去掉 `isIntersecting` 守卫 → **第一轮 22 passed（未被侦测）**。根因是我的 `MockObserver` 只在手动 `enter()` 时回调，没有像真实浏览器那样在 `observe()` 时投递一次「尚未进入视口」的初始回调，于是「进入分支即已读」在测试里根本不会被触发，**断言是恒真**。已加固探针（见下）后重跑 → `1 failed | 21 passed`，失败恰为「进入不发 seen」。
- MUTATION C：删掉 `branch.unreadCount === 0` 的 return → `1 failed | 21 passed`，失败恰为「未读 0 永不发 seen」。
- 反恒真配对：每条负向断言都配正向触发（进入不发 ↔ 进视口后发且带正确 sequence；未读 0 不发 ↔ 未读 2 会发；锚点失效到最新端 ↔ 有锚点到锚点）。**MUTATION B 暴露的教训**：mock 必须复刻真实 API 的初始回调，否则负向断言不成立。

### TDD or sizing exception — only with prior user approval
- 无。

## Implementation notes

- 「最新回答」的判定照抄 `ViewportSeen.newestStableAnswer`：从后往前找**第一条 AI 且 `activeRevisionId !== null`** 的消息。不能写成「最后一条消息」——最后一条可能还是用户追问或尚在生成的占位。
- 只观察那一条元素，不观察整列：Android 的集合判定在 Web 上等价于「最新回答是否可见」，观察整列反而会在「有任意回答可见但最新一条在下方」时误清徽标（正是 `ViewportSeen` 类注释点名的错误捷径）。
- `IntersectionObserver` 缺失时静默降级为「不自动清未读」：宁可留着徽标，也不谎报已读；绝不能退回「进入即已读」。
- 恢复位置用 `restoredRef` 钉住「每分支只恢复一次」，载入分支时置 false：否则生成中读回权威消息会反复把视口拽回锚点。
- 像素级偏移（`listItemOffset` / `viewportOffset`）不还原：Web 没有 Android 的条目高度模型，恢复到消息粒度是本次可承诺的精度，已写入 Gaps。

## Verification report

- Verdict: PASS（2026-10-14 独立 verifier）
- Evidence: `artifacts/evidence/T-127-reading-restore-unread-{red,green,mutation}.txt`；verifier 独立复核 7 项全部成立，并确认 MUTATION B 的恒真事故已真修（探针在 `observe()` 投递 `isIntersecting:false`，推演「进入即已读」必使 entry 断言失败）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 旧断言「进入分支即已读」与 Android `ViewportSeen` 口径相反，本任务改写为「进入不发、见到最新回答才发」，并配「未读 0 不发」的反恒真断言；等价覆盖由视口触发用例承担。
- Gaps: 像素级滚动偏移（`listItemOffset` / `viewportOffset`）未还原，Web 没有 Android 的条目高度模型；当前只恢复到消息粒度。
- Risks: ① 「最新回答进入视口」在 Web 上是**元素级**判定，与 Android 的视口集合判定不完全等价：极长回答只露出顶部也算进入视口。可接受（Android 也是「该消息在可见集合里」），若要更严可加阈值比例。② B（并排）布局与 C（树图）布局下阅读列不渲染，`IntersectionObserver` 不会建立，未读不会自动清除——与旧行为一致，属 T-132 布局收敛范围。③ 像素级滚动偏移未还原，长回答内部的位置会丢（见 Gaps）。④ 探针事故已修但同类风险仍存：任何「X 不发生」的断言都要确认 mock 会真实投递触发条件。

## Handoff notes

- Completed: Android 规则取证（`ViewportSeen.java:38-51`、`ReadingSession.java:46-54`）+ Web 缺口定位；RED（3 红，用 HEAD 版实现重采）/ GREEN（22/22、全量 216/216、六项门禁 0）/ 三变异证伪（B 首轮恒真、加固探针后成立）并还原
- In progress: 交独立 verifier 复核
- Blocker: 无
- Unblock condition: 不适用
- Next steps: verifier 复核 → 提交本任务改动 → T-128 回答动作行（4 图标 + 时间，纯图标按 `view_answer_actions.xml`）→ T-129 划词浮条（6 格）→ T-130 推荐即发送 → T-131/T-132 首页与外壳 → T-133/T-134 后端与其余页面
- Watch-outs: verifier 请重点看 MUTATION B 的恒真事故是否真的修好（探针是否投递初始回调）；不要把「任意回答可见」当成触发条件。
- Watch-outs: `IntersectionObserver` 在 jsdom 下不存在，生产代码必须做能力检测并静默降级；不要把「任意回答可见」当成触发条件（Android 明令否掉）。
