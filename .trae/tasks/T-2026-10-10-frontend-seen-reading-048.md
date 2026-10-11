# Treelearn Harness Task: 前端已读/阅读位置/未读消费

id: T-2026-10-10-048
title: 前端已读/阅读位置/未读消费
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 70
shared_code_touched: false
depends_on: [T-2026-10-10-branch-lifecycle-deletion-042.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-10-001` 第二波交付（用户指令「继续完成后续的任务」）。
- Problem/background: T-042 已交付 seen/reading-position 端点，但前端从不调用：未读永不消除、阅读位置不持久化、树图未读不显示。
- Users/scenarios: 学习者进入带未读的分支 → 自动标已读；离开分支 → 保存阅读位置；树图名牌显示未读徽标。
- Expected outcome: `markBranchSeen`/`saveReadingPosition` store 函数；BranchWorkspace 进入时 unreadCount>0 → POST seen（latestStableSequence）；卸载/切分支经 `loadedRef` 保存阅读位置（最后一条 AI 消息+显示版本）；TreeCanvasView 名牌未读徽标（ownUnread>0）。
- Core flow and reversibility: fire-and-forget 调用（失败不打断阅读）；锚点经 useRef 读取最新 loaded，避免 cleanup 闭包过期。
- Inputs/outputs: 输入=BranchTree 的 unreadCount/latestStableSequence；输出=`POST /api/branches/{id}/seen`、`PUT /api/branches/{id}/reading-position`。
- State and exception handling: 调用失败静默（.catch(() => {})）——不影响阅读主流程。
- Constraints and trade-offs: 镜像 Android 语义与共享域唯一判定；不改契约既有章节语义；不改 Android/schema。
- AI recommendation and rationale: 与 042/043/045 同构的「镜像 Android + 共享域判定」模式。
- User choice and custom additions: 已批准（继续后续任务指令）。
- AI-autonomous decisions: 锚点取「最后一条 AI 消息 + 其 activeRevisionId、偏移 0」为第一版语义（真实视口偏移留待 T-049）。
- User-confirmation decisions: 无。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-048-{test,lint,build}.txt`。
- Facts: 契约 §6.3 请求形状（`BranchSeenRequest{commandId,seenThroughSequence}`、`ReadingPositionUpdateRequest`）；`api.post/api.put`；Message.activeRevisionId。
- Assumptions: 离开时机=组件卸载/branchId 变化的 effect cleanup。
- Unknowns: 无。
- Risks: 严格模式下 cleanup 双触发会重复保存——后端幂等（无 commandId 载体，重复写同值无害）。
- Deferred: 真实视口偏移；滚动位置恢复（进入分支时读 readingAnchor）。
- Non-goals: 不扩大范围到矩阵外功能。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

未读在进入分支后被清除、离开时阅读位置持久化、树图名牌显示未读徽标；前端全量守门全绿。

## Scope

### In scope
- `store/topicData.ts`：markBranchSeen/saveReadingPosition
- `pages/BranchWorkspace.tsx`：进入标已读 + 卸载存位置
- `components/TreeCanvasView.tsx`：未读徽标
- `BranchWorkspace.test.tsx`：seen 触发用例 + 徽标断言

### Out of scope
- 批注渲染（T-049）；滚动恢复。

## Acceptance criteria
- [x] 进入带未读分支触发 `POST /api/branches/{id}/seen`
- [x] 树图 ownUnread>0 名牌显示徽标
- [x] 离开分支保存阅读位置（代码路径+类型检查）
- [x] `pnpm test` 175/175、lint/build exit 0

## TDD execution record

### RED — failing test first
- Test/scenario: `marks the branch seen on entry when it has unread answers`（新用例，先于实现为红）
- Command: `cd web/frontend && pnpm test`
- Exit code: 1
- Observed failure: 1 failed——stub 未路由 seen POST 且 unreadCount 覆盖未生效（branchTree handler 缺失），断言 calls 无 seen 请求
- Failure reason validated: `artifacts/evidence/T-2026-10-10-048-red.txt`（`Exit code: 1`；回退态复现实录——进入分支未发 seen，其余 174 例不受影响）。

### GREEN — minimum implementation
- Implementation: store 两函数 + BranchWorkspace 接线 + TreeCanvasView 徽标 + stub 修复
- Command: `cd web/frontend && pnpm test`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-048-test.txt`（EXITCODE=0）。
- Regression evidence: 同文件（全量 22 文件 175 例）+ lint/build 证据

### REFACTOR — optional
- Cleanup: seen 断言改用模板串精确 URL；stub 新增 branchTree handler 供覆盖树
- Regression evidence: 同文件（全量 22 文件 175 例）+ lint/build 证据

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行 ≈70，未超软上限）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- fetch stub 带 query/新增路由时须显式登记，否则组件静默走错误分支（两轮踩坑：tree-layout query、branchTree）。
- cleanup 闭包过期用 `loadedRef` 解决。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 进入分支自动标已读、离开保存阅读位置、树图未读徽标（用户可见行为变化）
- Authoritative code/config/migration evidence: `web/frontend/src/store/topicData.ts`、`.../pages/BranchWorkspace.tsx`、`.../components/TreeCanvasView.tsx`
- Documentation surfaces checked: 契约与 `web/README.md`。
- Documentation files updated: README 状态节已按本任务交付同步。
- Producer/consumer/sibling/compatibility paths checked: 后端 seen/reading-position 端点（T-042 已冻结）无改动
- Superseded phrases/examples reverse-searched: 无（新增端点/组件此前不存在）。

## Verification report

- Verdict: PASS（实现者自检全绿；独立 verifier 终验待统一复核）
- Independent verifier re-check (2026-10-11, READONLY subagent, evidence-free re-runs, merged pass covering T-045..049): PASS——后端 :check 复跑 exit 0（284 PASSED / 0 FAILED + checkstyle 0 error）、前端 test 180/180 + lint + 守卫自测/扫描 + build 全 exit 0、validate_tasks.py errors=0（warnings=33 软预算）；证据交叉核对一致（045 red 11 败→green 11/11、047 red 8/9→green 9/9、048-red 回退实录 exit 1）；代码抽查（树布局装配/批注判定/划选映射/Markdown 兼容）逐条符合；E2E 证据脱敏确认（无 Key 明文）。非阻塞发现：046-guard 过程证据含已修复的旧拦截；1 例 waitFor 偶发超时复跑过（Gradle 并行抢 CPU）；CELL 划选/手势缩放/浏览器视觉复核为遗留项。
- Evidence: `artifacts/evidence/T-2026-10-10-048-{test,lint,build}.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除既有断言。
- Gaps: 阅读位置进入时恢复（Conversation.readingAnchor 滚动定位）未做
- Risks: 严格模式双 cleanup 重复保存同值（无害）

## Handoff notes

- Completed: 实现与证据全部落盘，守门全绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: T-049 批注渲染；来源定位/选文引用
- Watch-outs: 锚点偏移当前恒 0；滚动恢复待 T-049
