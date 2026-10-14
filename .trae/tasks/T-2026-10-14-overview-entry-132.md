id: T-2026-10-14-132
title: 移除 A/B/C 布局档位，树回到 Android 的总览入口（/topic/:id/tree）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-13-web-android-alignment-123.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: frontend
stack: frontend
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-10-14-home-structure-131.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-13-web-android-alignment-123`。W4 首页与外壳第二步。
- Problem/background（源码核对）:
  - Android 权威一：`ChatActions.java:144` *"The overview entry: opens the whole topic's canvas,
    standing on this branch."*；`ChatPresenter.java:860` `onOverviewPressed()` → `sink.openOverview(branchId)`；
    `ChatScreen.java:305-306` 由顶栏 `chat_overview` 触发。**树是入口（另一屏），不是布局档位。**
  - Android 权威二：`ChatActions.java:147` `onNavigationPressed()` → 打开分支列表（另一屏）。
    对话页顶栏还有 `chat_back` / `chat_settings`，没有任何「切换布局」控件。
  - Web 现状偏差：`BranchWorkspace` 顶栏有 A（专注阅读）/ B（并排分支）/ C（树图）三档切换，
    并把选择写进 localStorage —— Android 不存在这个概念；C 档把整棵树塞进对话页里。
- Expected outcome: 对话页固定单列阅读；整棵树从顶栏「总览」入口打开，占自己那一屏。
- Facts: `TreeCanvasView` 只消费服务端 `tree-layout` 端点（契约 §6.11），前端不推导几何——
  把它搬到独立页面不需要新的几何逻辑。
- AI-autonomous decisions:
  1. 新路由 `/topic/:id/tree`（`?branch=` 仍带），新页面 `pages/TopicTreePage.tsx` 只做
     「顶栏（返回 → 所在分支的对话页）+ TreeCanvasView」，返回目标是**那条分支的对话页**而不是首页。
  2. B 并排档位随三档一起移除：`SideBySideView` 组件与其测试保留在仓库（未引用），改为 Gap——
     Android 对应的是 `onSourceCardToggled` 的来源卡（可折叠卡片，不是并排），形状不同，
     不在本任务凭空改。
  3. `layoutPreference` store 与其单测保留（未引用），一并记为 Gap；`workspace:layout` 不再写入。
- Non-goals: 侧栏快捷分支列表（见 Gaps）、来源卡、后端。

## Goal

对话页不再有 A/B/C 三档切换；想看整棵树就从顶栏「总览」进去，回来还是刚才那条分支。

## Scope

### In scope
- 移除 A/B/C 切换控件与 `layoutPreference` 依赖，对话页固定单列阅读
- 顶栏加「总览」入口 → `/topic/<id>/tree?branch=<id>`
- 新增 `topicTree` 路由 + `pages/TopicTreePage.tsx` + Router 挂载
- i18n：`workspace.layout.*`（5 个键）→ `workspace.overview`

### Out of scope
- 侧栏内容与快捷分支列表
- 并排来源视图 / 来源卡
- 后端

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 改变树的到达方式（入口而非档位）与布局持久化；
  保留阅读列、消息位、流式、追问、推荐、分叉、子分支列表、阅读恢复与未读口径。
- Producer/consumer/sibling entrypoints and platform branches (code evidence):
  `pages/BranchWorkspace.tsx`、`platform/router.ts`、`pages/Router.tsx`、`pages/TopicTreePage.tsx`（新）、
  `components/TreeCanvasView.tsx`（aria-label 换键）、`layouts/AppShell.tsx`（TITLE_KEYS 补 topicTree）、
  `i18n/{zh-CN,en-US}.ts`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 树的到达方式；对话页没有布局档位；`/topic/<id>/tree` 是总览屏。
- Authoritative code/config/migration evidence: `ChatActions.java:144/147`、`ChatPresenter.java:860/866`、
  `ChatScreen.java:305-308`、`platform/router.ts`、`pages/Router.tsx`。
- Documentation surfaces checked: `web/contracts/api.md`（`tree-layout` 端点未变）。
- Documentation files updated: 无。
- Producer/consumer/sibling/compatibility paths checked: `TreeCanvasView` 的 props 未变，
  仅 `aria-label` 由 `workspace.layout.tree` 换成 `workspace.overview`；`AppShell` 的 TITLE_KEYS 补了
  `topicTree`（漏了会 tsc 报错，已补）。
- Superseded phrases/examples reverse-searched: 全仓搜 `workspace.layout`，仅 i18n 与
  `TreeCanvasView` 命中，已处理。
- `documentation impact: none` reason and evidence, if applicable: 无端点、契约、文档语句变化。
- Historical/PRD handling: 不适用。

## Acceptance criteria

- [x] 对话页顶栏不存在任何布局档位按钮（专注阅读 / 并排分支 / 树图都没有）
- [x] 不再往 localStorage 写 `workspace:layout`
- [x] 顶栏「总览」入口跳到 `/topic/<id>/tree` 并带上当前 branch
- [x] `/topic/<id>/tree` 渲染出服务端树布局（名牌与摘要）
- [x] 分叉分支上不再出现来源并排（面包屑仍点出根主题与当前分支）
- [x] 全量门禁绿：vitest / tsc / eslint / build / 三守门
- [x] 新增断言经变异检验为可证伪

## TDD execution record

### RED — failing test first
- Test/scenario: `BranchWorkspace.test.tsx` 新增 `describe('BranchWorkspace overview entry (T-132)')` 三条（无布局档位 / 总览入口带 branch 跳转 / 分叉分支走面包屑且无并排）；`Router.test.tsx` 新增一条（`/topic/t1/tree` 渲染树画布）。同时删除与 Android 口径相反的旧三条（`persists the layout and renders the C tree canvas...`、`shows the empty note when the server layout has no branches`、`shows the fork source side by side when layout B is chosen...`）。
- Command: npx vitest run src/pages/BranchWorkspace.test.tsx src/pages/Router.test.tsx
- Exit code: 1
- Observed failure: 4 条红——三条找不到 `workspace-overview` / 断言布局按钮存在，一条 `/topic/t1/tree` 走 404 分支渲染不出树。
- Failure reason validated: 证据 `artifacts/evidence/T-132-overview-entry-red.txt`（EXITCODE=1，4 FAIL / 31 passed），以 HEAD（含 T-128~T-131、不含 T-132）的实现跑最终版测试。

### GREEN — minimum implementation
- Implementation: `BranchWorkspace.tsx`——删 `workspace__layout` 段与 `LAYOUT_ICONS/LAYOUT_LABELS/chooseLayout/layout state/sourceMessages/source/parentTitle`，顶栏加 `IconButton(Network, workspace.overview, data-testid=workspace-overview)` → `navigate('/topic/<id>/tree?branch=<id>')`，阅读列去掉三元直接渲染；`router.ts`——`Route` 加 `topicTree`，`matchRoute` 识别 `/tree` 后缀；`pages/TopicTreePage.tsx`（新）——顶栏返回到所在分支对话页 + `TreeCanvasView`；`Router.tsx` 挂载；`TreeCanvasView` aria-label 换 `workspace.overview`；`AppShell` TITLE_KEYS 补 `topicTree`；i18n 删 5 个 layout 键、加 `workspace.overview`。
- Command: npx vitest run src/pages/BranchWorkspace.test.tsx src/pages/Router.test.tsx
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-132-overview-entry-green.txt`——两个文件 35/35；全量 `npx vitest run` 29 files / 234 tests（前值 233：新增 4、删除 3）。
- Regression evidence: 同证据文件——tsc 0、eslint 0、三守门 PASS、`vite build` 0；T-126/T-127 的终态闭环与未读/阅读恢复用例未改且全绿。

### REFACTOR — optional
- Cleanup: 清理因删档位而失效的 `LucideIcon` 引入、`sourceMessages` 加载 effect、`source`/`parentTitle` 派生（tsc 逐条报出来后删的）。
- Regression evidence: 见 GREEN 证据文件的同一批门禁。

### Mutation verification（反恒真配对）
- 三个变异各改一处生产代码，重跑对应测试文件；证据 `artifacts/evidence/T-132-overview-entry-mutation.txt`。
  A（matchRoute 不识别 `/tree`）→ 失败恰为「/topic/:id/tree 渲染树画布」；
  B（总览不带 branch）→ 失败恰为「入口带上所在分支」；
  C（Router 不挂树页）→ 失败同为「渲染树画布」。
- **本机坑（已记录）**：备份文件 `router.green.bak` 与 `Router.green.bak` 在 Windows 上落到同一个文件
  （大小写不敏感），后写的 Router.tsx 覆盖了 router.ts 的备份。变异 A 已用 Edit 反向改回并逐行核对。
  以后备份同目录不同大小写名时，必须改名（如 `platform-router.ts.bak`）。
- 反恒真配对：正向（入口带 branch、/tree 渲染出树）配负向（无任何档位按钮、不写 workspace:layout、分叉分支无并排）。

### TDD or sizing exception — only with prior user approval
- 无。

## Implementation notes

- 树搬走后 `TreeCanvasView` 一行没动几何，只换了 aria-label 的键——它是纯消费者，这正说明
  「树作为布局档位」本来就是多余的耦合：几何从来不依赖对话页。
- 总览返回的目标是**所在分支的对话页**（`/topic/<id>?branch=<id>`），不是首页：总览是一次跳转，
  不是一次离开，这一点与 Android 注释 *"the conversation keeps the branch it was showing"* 一致。
- 删掉的三条旧断言不是「改测试让它过」，是与 Android 口径相反（Android 没有 layout 概念），
  其等价覆盖由新三条 + Router 那条承担。

## Verification report

- Verdict: PENDING（待独立 verifier 复核）
- Evidence: `artifacts/evidence/T-132-overview-entry-{red,green,mutation}.txt`
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除三条依赖 A/B/C 档位的旧断言（与 Android 无 layout 概念的口径相反）；新增四条覆盖「无档位 / 总览入口带 branch / 树页渲染 / 分叉分支走面包屑无并排」；树画布的空布局提示（`workspace.tree.empty`）现由树页承担，等价覆盖见 Router 用例的同一组件路径。
- Gaps: ① 侧栏快捷分支列表（Android `onNavigationPressed` 的分支列表屏）未做；② `SideBySideView` 与 `layoutPreference` 保留在仓库但已无引用，Android 对应的来源卡形状不同；③ 侧栏在主题页上下文的内容收敛未做。
- Risks: ① 树页是新页面，`workspace__tree-canvas` 在独立屏里的尺寸/滚动未做浏览器实测；② 删掉并排后分叉分支暂时只能靠面包屑表达来源，信息量下降。

## Handoff notes

- Completed: Android 三处权威取证（ChatActions / ChatPresenter / ChatScreen）；RED（4 红）/ GREEN（35/35、全量 234/234、六项门禁 0）/ 三变异证伪并还原
- In progress: 交独立 verifier 复核
- Blocker: 无
- Unblock condition: 不适用
- Next steps: verifier 复核 → 提交 → T-133 后端派生能力补齐（含首页推荐条接线）→ T-134 学习库/树/设置补齐
- Watch-outs: 不要为了「保留一个入口」把布局切换加回来；`/topic/<id>/tree` 与 `/topic/<id>` 的 branch 参数语义要一致。
