id: T-2026-10-14-130
title: 推荐即发送与返回按入口还原（对齐 ChatPresenter.onRecommendationFollowed）
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
depends_on: [T-2026-10-14-word-menu-129.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-13-web-android-alignment-123`。W3 对话页重排第三步。
- Problem/background（源码核对）:
  - Android 权威一：`android/app/src/main/java/com/treelearn/app/ui/ChatPresenter.java:745`
    `onRecommendationFollowed` = `render(state.withDraft(question.trim()))` 后**立刻 `onSend()`**；
    类上方注释写明 *"A tapped chip becomes the draft first, so a blocked send still shows what was
    tapped"*——先入草稿再发送，是为了发送被拦时输入框里仍留着被点的那句。
  - Android 权威二：`HomePresenter.java:97` `onRecommendationTapped` 同口径（`withDraft` + `onSubmit`）。
  - Android 权威三：`ChatScreen.java:79` 类注释 *"The two recommendation targets stay apart. The row
    asks a follow-up, its own 44dp zone opens a branch; neither is reachable from the other's gesture."*
  - Web 现状偏差一：`BranchWorkspace.tsx` 的推荐 chip `onClick={() => draft.setText(rec)}`——**只填草稿、
    不发送**，与 Android 差一步。
  - Web 现状偏差二：返回按钮写死 `navigate('/')`，从学习库搜索命中进来的也会被扔回主页。
- Expected outcome: 点推荐 chip 直接把该问题作为追问发出去；返回回到打开这一屏的那个入口。
- Facts: `ChatPresenter.onRecommendationForked`（:754）分叉时 question 作为 cargo、不锚定句子（quote 空、0,0），
  与追问是两个入口。
- AI-autonomous decisions:
  1. 发送抽出唯一出口 `submit(text, quote)`，composer 与推荐 chip 共用，守卫与副作用只有一份
     （现状 `handleSend` 读 `draft.text`，无法在不改状态的前提下带指定文本发送）。
  2. 返回目标记在 history 条目里（`navigate` pushState 时写 `{ from }`，新增 `router.historyFrom()`），
     等价于 Android 的系统返回栈；直接输入地址进来时 `from` 为空，兜底回首页。
  3. 顺带修 `navigate` 的相等判断：原来只比 `pathname`，导致 `?branch=` 切换被整条吞掉，
     而返回到另一个分支页正是靠查询串区分的——不修则返回不生效。
- Non-goals: 首页推荐跑马灯（T-131）、侧栏（T-132）、后端推荐生成（T-133）。

## Goal

点一下推荐就把那句问出去，而不是填进输入框等你再点发送；返回回到你从哪来，不是一律回主页。

## Scope

### In scope
- 推荐 chip 点击直接发送（先入草稿再发送）
- 追问发送抽出唯一出口 `submit(text, quote)`
- 返回目标按入口还原（`navigate` 写 `from`、`router.historyFrom()`、返回按钮读它）
- `navigate` 的相等判断改为完整位置（含 `?branch=`）
- 文案 `workspace.back`：「返回主页」→「返回」

### Out of scope
- 首页推荐跑马灯与首页结构
- 侧栏、后端

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 改变推荐 chip 的点击结果（发送而非仅填草稿）与返回按钮的
  目标；保留分叉热区的行为、`forkBranch` 的参数、追问发送的端点与命令 id、逐分支草稿。
- Producer/consumer/sibling entrypoints and platform branches (code evidence):
  `platform/router.ts`（`navigate` / 新增 `historyFrom`）、`pages/BranchWorkspace.tsx`
  （`submit` / `handleFollowRecommendation` / 返回按钮 / 推荐 chip）、`i18n/{zh-CN,en-US}.ts`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 推荐 chip 的点击结果；返回按钮的目标；`navigate` 会写入 `from`。
- Authoritative code/config/migration evidence: `ChatPresenter.java:745/754`、`HomePresenter.java:97`、
  `ChatScreen.java:79`、`platform/router.ts`、`pages/BranchWorkspace.tsx`。
- Documentation surfaces checked: `web/contracts/api.md`（发送与分叉端点未变）。
- Documentation files updated: 无。
- Producer/consumer/sibling/compatibility paths checked: `fork-recommendation-<i>` testid 保留，
  `pages/BranchWorkspace.fork.test.tsx` 的推荐分叉用例未改且全绿。
- Superseded phrases/examples reverse-searched: 全仓搜「返回主页 / Back to home」，仅两处 i18n 命中，已改。
- `documentation impact: none` reason and evidence, if applicable: 无端点、契约、文档语句变化。
- Historical/PRD handling: 不适用。

## Acceptance criteria

- [x] 点推荐 chip 直接发出该问题（POST `/api/branches/<id>/messages`，`text` 为推荐原文）
- [x] 发不出去时（无可用模型）草稿里仍是被点的那个问题
- [x] 推荐行的分叉热区不触发追问发送，只走 `/forks`
- [x] 返回回到打开这一屏的入口（从学习库进来回学习库）
- [x] 直接打开分支页（无入口记录）时兜底回首页
- [x] 全量门禁绿：vitest / tsc / eslint / build / 三守门
- [x] 新增断言经变异检验为可证伪

## TDD execution record

### RED — failing test first
- Test/scenario: `pages/BranchWorkspace.test.tsx` 新增 `describe('BranchWorkspace recommendation follow-through (T-130)')` 五条用例：点了就发；发不出去时草稿留着；分叉热区不发追问；返回按入口还原；无入口兜底首页。
- Command: npx vitest run src/pages/BranchWorkspace.test.tsx
- Exit code: 1
- Observed failure: 3 条红——「点了就发」没有任何 POST /messages；两条返回用例停在 `data-testid="workspace-back"`（当时返回按钮既没有该 testid，也写死 `navigate('/')`）。
- Failure reason validated: 证据 `artifacts/evidence/T-130-recommendation-send-red.txt`（EXITCODE=1，3 FAIL / 24 passed），以 HEAD（含 T-128/T-129、不含 T-130）的实现跑最终版测试。

### GREEN — minimum implementation
- Implementation: `router.ts`——`navigate` 写 `{ from: currentLocation() }`、相等判断改完整位置，新增 `historyFrom()`（只接受站内绝对路径）；`BranchWorkspace.tsx`——抽出 `submit(text, quote)` 作为发送唯一出口，`handleSend` 与新增的 `handleFollowRecommendation`（`draft.setText(question.trim())` + `submit(question.trim(), '')`）共用；推荐 chip 改接 `handleFollowRecommendation`；返回按钮 `navigate(historyFrom() ?? '/')` 并加 `data-testid="workspace-back"`；i18n `workspace.back` 改「返回」。
- Command: npx vitest run src/pages/BranchWorkspace.test.tsx
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-130-recommendation-send-green.txt`——目标文件 27/27；全量 `npx vitest run` 29 files / 228 tests（前值 223，新增 5 条）。
- Regression evidence: 同证据文件——tsc 0、eslint 0、三守门 PASS、`vite build` 0；`BranchWorkspace.fork.test.tsx` 的推荐分叉用例未改且全绿。

### REFACTOR — optional
- Cleanup: 无（`handleSend` 收敛为 `submit(draft.text, quote)` 一行，已是最简）。
- Regression evidence: 见 GREEN 证据文件的同一批门禁。

### Mutation verification（反恒真配对）
- 四个变异各改一处生产代码，重跑同一条命令；备份 `artifacts/tmp/t130/{BranchWorkspace,router}.green.bak`，
  已 `diff` 还原确认 RESTORED_CLEAN，并再跑一次全量 228/228。证据：`artifacts/evidence/T-130-recommendation-send-mutation.txt`。
- A（chip 退回只填草稿）→ 失败恰为「点了就发」；B（发送前不入草稿）→ 失败恰为「发不出去时草稿留着」；
  C（返回写死首页）→ 失败恰为「按入口还原」；D（跳转不写 from）→ 失败同为「按入口还原」。均为 `1 failed | 26 passed (27)`。
- 反恒真配对：正向（点了就发、按入口还原）配负向（发不出时草稿留着、分叉热区不发追问、无入口兜底首页）。

### TDD or sizing exception — only with prior user approval
- 无。

## Implementation notes

- 「先入草稿再发送」不是冗余动作：它决定发送被拦时用户看到什么。删掉 setText 只留 submit，
  功能测试照样全绿，只有「blocked send」这条会红——这正是变异 B 盯的。
- 返回目标放在 history 条目里而不是组件 state：刷新、前进/后退都靠浏览器自己的栈，
  与 Android 的「系统返回键回到上一屏」同构；用组件 state 会在刷新后丢目标。
- `navigate` 原来只比 `pathname`，`?branch=` 变化被当成「没变」直接 return——返回到另一个分支页
  恰好是同一 pathname，不修这条返回就静默失效。

## Verification report

- Verdict: PASS（独立 verifier 复核，2026-10-14）
- Verifier evidence: 复核确认「先入草稿再发送」与 `navigate(historyFrom() ?? '/')` 已落地，分叉热区仍独立；Android 权威 `ChatPresenter.java:745/754` 引文属实；重跑 `npx vitest run` 234/234 全绿。
- Evidence: `artifacts/evidence/T-130-recommendation-send-{red,green,mutation}.txt`
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除既有断言；新增五条覆盖推荐发送与返回目标；既有推荐分叉用例（`BranchWorkspace.fork.test.tsx`）继续承担分叉侧覆盖。
- Gaps: 首页推荐跑马灯（Android `RecommendationMarquee` 的双行反向漂移与触摸暂停）未实现，留给 T-131。
- Risks: ① `historyFrom()` 只认站内绝对路径，外部写入 history.state 的场景（浏览器扩展）一律回首页；② 生成进行中点推荐会被 `activeOperationId !== null` 守卫静默挡掉，Android 未明确此情形。

## Handoff notes

- Completed: Android 三处权威取证（ChatPresenter / HomePresenter / ChatScreen 类注释）；RED（3 红，用 HEAD 版实现重采）/ GREEN（27/27、全量 228/228、六项门禁 0）/ 四变异证伪并还原
- In progress: 交独立 verifier 复核
- Blocker: 无
- Unblock condition: 不适用
- Next steps: verifier 复核 → 提交 → T-131 首页结构 → T-132 侧栏收敛
- Watch-outs: 不要为了让测试好写把返回目标改回写死首页；`submit` 是唯一发送出口，新增入口请走它。
