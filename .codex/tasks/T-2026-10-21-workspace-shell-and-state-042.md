# Filename: T-2026-10-21-workspace-shell-and-state-042.md
# Codex Harness Task: 工作区状态仓库、数据视图模型与外壳骨架

id: T-2026-10-21-042
title: 工作区状态仓库、数据视图模型与外壳骨架
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-21-web-learning-workspace-requirement-041.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 360
shared_code_touched: true
depends_on: [T-2026-10-21-web-learning-workspace-requirement-041.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 用户已确认示意稿的轻量学习工作区方向；本片只做状态仓库、夹具视图模型、数据 Provider 与外壳骨架，不实现对话/学习库/设置的完整页面。
- Problem/background: 现有 `AppShell` 只有内容槽＋底部标签，`screenRoute` 只记屏名，无法承载主题、草稿、滚动位置、筛选与分支面板；页面各自读夹具，侧栏「最近学习」与面包屑没有共享数据源。
- Users/scenarios: 桌面与窄窗下从侧栏切换主页/学习库/设置/最近主题；在任何视图改变草稿、滚动、筛选或面板状态后，换视图再返回仍能恢复；浏览器后退/前进只恢复视图与主题。
- Expected outcome: 一个模块级工作区仓库（视图、主题、返回栈、面板、侧栏折叠、草稿、位置、学习库筛选/查询/展开、设置分类）＋一套纯夹具视图模型（主题、推荐、回答块、分支、设置）＋一个数据 Provider＋一个可折叠侧栏/顶部栏/主区外壳。页面通过 `useWorkspace`/`useWorkspaceData` 读写，不再各自持有跨视图状态。
- Core flow and reversibility: 侧栏/推荐/学习库调用 `openView`/`openTopic` → 仓库写入栈与当前位置、同步 hash → 外壳与页面重渲染 → 页面卸载前把滚动位置写回。回退为删除本片新增仓库/模型/Provider/样式并恢复旧 `AppShell`；夹具与 `RuntimePort` 不动。
- Inputs/outputs: 输入是 `bundledFixtureSource()` 提供的 13 屏夹具信封；输出是 `WorkspaceState`、`WorkspaceActions`、`WorkspaceData`（topics/recommendations/chat/branches/settings 模型）、`AppShell` 的侧栏与顶部栏。不产生网络、存储或写入副作用。
- State and exception handling: 状态只在内存；`DEFAULTS` 固定初值，`resetWorkspace()` 仅供测试与 hash 初始化。夹具读取失败时 `status="error"` 且对应模型为 `null`，页面显示 `state_fixture_unavailable`。主题索引越界收敛到 0；分支索引越界收敛到 0。`openView` 同视图同主题不重复压栈；`back()` 栈底不动。滚动位置写入不触发订阅（避免滚动导致整树重渲染）；草稿写入不触发订阅（由输入框本地状态驱动，换主题时按 key 重新读）。
- Constraints and trade-offs: 不新增依赖；不引入 context 之外的第二份导航状态（hash 只是序列化，不是权威）；不把布局宽度写进状态（窗口变化只影响 CSS）；不修改 `RuntimePort` 契约；不删除旧 `ScreenRouter`/`SnapshotScreen`。模块级单例与 `screenRoute` 一致，测试用 `resetWorkspace()` 隔离。
- AI recommendation and rationale: 把「状态」与「夹具到视图模型」分开：仓库只管导航与恢复，纯函数只管把节点读成主题/回答/分支/设置。这样 TDD 可以在不渲染 UI 的情况下钉住恢复语义，页面只做呈现。数据 Provider 一次并行读取所需屏幕，避免每屏各读一份导致侧栏与页面不同步。
- User choice and custom additions: 用户要求保留示意稿与说明、保留未提交修改、不使用新依赖、不改 Android、操作图标化并 ≥44×44、真实浏览器验证。
- AI-autonomous decisions: ① hash 采用 `#/<view>/<topic>`，只恢复视图与主题；② `navCollapsed` 初值在窄窗（≤760）为 true；③ 分支索引本轮固定 0，但仓库仍保留字段为后续子分支留位；④ 数据 Provider 读取 home/library/chat/navigation/treecanvas/settings 及六个设置子页共 12 份夹具；⑤ 旧 `screenRoute` 不复用于工作区，避免两套栈；⑥ 新样式拆 `workspace.css`。
- User-confirmation decisions: 见父卡；本片不新增需要用户拍板的决策。
- Acceptance evidence: 本卡 RED/GREEN 命令读数；`resetWorkspace` 后各测试独立；`pnpm --dir frontend test -- test/store/workspace.test.ts test/store/workspaceData.test.ts test/shell/AppShell.test.ts` 全绿；`pnpm --dir frontend lint` 绿。
- Facts: `AppShell` 当前 33 行，只有 `main[data-slot='screen']` 与两个 `.app-tab`；`screenRoute` 是模块级 store；`useFixtureScreen(screen)` 按屏读；夹具 `library_topic` 两卡、`home_topic_card` 两卡、`chat` 只有主题 0 的回答、`navigation` 只有根分支、`settings` 六入口；`Icon.tsx` 26 字形；`tokens.css` 44 token。
- Assumptions: 学习库两卡与主页两卡是同一主题集合且顺序一致；`chat` 回答属于主题 0（夹具标题与主页第一卡一致）；`navigation_row_meta` 的「当前 · 0 子分支」是主题 0 的真实分支数；设置子页文本可线性读取，不需要坐标。
- Unknowns: 设置写入和真实生成何时接通；主题 1 的子分支内容不存在；刷新后的草稿持久化未拍板。
- Risks: Provider 并行读取 12 份夹具会让首屏等待 Promise.all，测试需等待宏任务；hash 同步在 jsdom 中需要重置；把 `libraryQuery` 放进全局状态会让每次键入重渲染整棵树，若出现卡顿需改为局部状态＋失焦写回（本轮数据量小，先按全局）。
- Deferred: 真实数据写入、刷新持久化、子分支、旧快照屏退役。
- Non-goals: 本片不实现对话/学习库/设置的完整视觉；不改夹具；不改 Android/后端；不新增依赖。
- Skipped non-critical questions and reason: 没有追问侧栏动画曲线与顶部栏高度；由 token 与 CSS 在实现中决定，不影响验收。

## Goal

交付一个可单测的模块级工作区状态仓库、纯夹具视图模型、并行数据 Provider 与侧栏＋顶部栏外壳，使后续页面只负责呈现，并让主题/草稿/滚动/筛选/面板状态在视图切换与窗口变化中不丢失。

## Scope

### In scope
- `frontend/src/store/workspace.ts`：状态、动作、订阅、`resetWorkspace()`、hash 序列化/解析。
- `frontend/src/store/workspaceData.ts`：`topicsFrom`、`recommendationsFrom`、`answerBlocks`、`chatFrom`、`branchesFrom`、设置模型提取。
- `frontend/src/hooks/useWorkspace.ts`：`useWorkspace()` 订阅＋动作。
- `frontend/src/components/WorkspaceDataProvider.tsx`：并行读夹具、`useWorkspaceData()`。
- `frontend/src/layouts/AppShell.tsx`：侧栏（主页/学习库/最近学习/设置、折叠）、顶部栏（折叠、返回、面包屑、页面工具槽）、主区、分支面板槽。
- `frontend/src/pages/WorkspaceRouter.tsx`：按工作区视图选择页面（本片先接一个最小页面占位，043 起替换）。
- `frontend/src/main.tsx`：Provider＋AppShell 编排。
- `frontend/src/styles/workspace.css`、`frontend/src/styles/app.css` 的旧底部标签样式退役（保留必要基础类）。
- `frontend/src/copy/zh-CN.ts` 新增本片文案；`frontend/src/components/Icon.tsx` 补侧栏与加号字形（如缺）。
- 测试：`test/store/workspace.test.ts`、`test/store/workspaceData.test.ts`、`test/shell/AppShell.test.ts` 重写。

### Out of scope
- 对话正文/分支面板/知识树视觉（043）；主页/学习库（044）；设置（045）。
- 真实发送/保存/上传/建分支。
- 旧快照屏删除或改造。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 底部两枚 `.app-tab` 被侧栏替代；`main[data-slot='screen']` 保留；`document.title` 仍为 `app_name`；夹具读取仍经 `RuntimePort`；旧三屏组件在本片仍可编译（043/044 才替换其内容）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `main.tsx` → `AppShell` → `WorkspaceRouter`；`useFixtureScreen` 继续服务旧快照屏；`WorkspaceDataProvider` 新增 `runtime().snapshot` 消费者，必须复用 `payloadOf`，不得自写解析；`Icon.tsx` 新增字形只追加，不改旧 id 映射。
- Native bridge or independent-client compatibility/update requirements: 不适用；不改夹具信封或原生桥。
- Behavior-neutral non-applicability reason, if applicable: 旧快照屏、`screenRoute`、`screenLinks` 不动。

## Documentation impact record

- Changed runtime facts: Web 主路径开始由工作区仓库驱动；hash `#/<view>/<topic>` 成为可分享/可后退的视图位置；侧栏折叠与面板状态只在内存。
- Authoritative code/config/migration evidence: `frontend/src/platform/fixtures/*.json`、`frontend/src/platform/fixtureNodes.ts`、`frontend/src/platform/RuntimePort.ts`、`frontend/src/main.tsx`。
- Documentation surfaces checked: 父卡、`.codex/rules/frontend.md`、`.codex/rules/regression.md`、`.codex/rules/maintainability.md`。
- Documentation files updated: 本卡与父卡；权威规格修订留给 046。
- Producer/consumer/sibling/compatibility paths checked: `screenRoute` 与工作区仓库不互相调用；两套栈不共享状态；`AppShell` 的 children 槽保留以便注入 Router 与测试探针。
- Superseded phrases/examples reverse-searched: 「底部标签」「app-tabs」「app-tab」——旧断言与样式在本片同步改写/退役。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 旧底部标签是 Android 单 Activity 的 Web 过渡形态，历史提交保留；本片不写进规格，等 046 汇总。

## Acceptance criteria

- [x] `workspace.test.ts`：`openView`/`openTopic`/`back` 的栈语义；同位置不重复压栈；主题索引收敛；面板/折叠切换；草稿按 `chat:<topic>` 与 `home` 分开保存；滚动位置按 `view:topic` 保存；学习库查询/筛选/展开与设置分类可恢复；`window` resize 后状态不变；`resetWorkspace` 清空。
- [x] `workspaceData.test.ts`：从真实 bundled 夹具读出两个主题、两条推荐、主题 0 的非空回答块与 3 行建议、`V4.1 Flash`；主题 1 的分支模型 `captured=false` 且不伪造对话；分支模型根标题、已知子分支数、AI 总结；六类设置模型均有标题与至少一个真实字段；夹具缺失时返回 null 而不是空壳。
- [x] `AppShell.test.ts`：侧栏包含主页/学习库/最近学习/设置；折叠按钮 `aria-expanded` 与可访问名随状态变化；`main[data-slot='screen']` 仍在；底部 `.app-tabs` 不再渲染；面包屑与当前视图一致；所有图标按钮有可访问名与 `title`。
- [x] `pnpm --dir frontend test -- test/store/workspace.test.ts test/store/workspaceData.test.ts test/shell/AppShell.test.ts` 退出码 0。
- [x] `pnpm --dir frontend lint` 退出码 0（依赖方向、文案、夹具同步、设备时间）。
- [x] 旧 `SnapshotScreen.test.tsx` 仍绿；夹具字节与 token 文件无改动。

## TDD execution record

### RED — failing test first
- Test/scenario: `workspace.test.ts`（仓库与 hash）、`workspaceData.test.ts`（夹具视图模型）、`AppShell.test.ts`（侧栏/顶部栏/内容槽）。
- Command: `pnpm --dir frontend test -- test/store/workspace.test.ts test/store/workspaceData.test.ts test/shell/AppShell.test.ts`
- Exit code: 1
- Observed failure: 三个套件在收集期即失败——`Failed to resolve import "../../src/store/workspace" / "../../src/store/workspaceData" / "../../src/components/WorkspaceDataProvider"`；汇总为 `Test Files 3 failed | 11 passed (14)`、`Tests 55 passed (55)`。
- Failure reason validated: 失败点是生产模块尚未创建，不是环境、选择器或夹具读取问题；同一命令下其余 11 个套件全绿。
- Assertion correction after measurement: 回答长度的初始下限 `>3000` 是估算值，实测夹具正文 2078 字；改为 `>1500` 以证明未截断，同时保留逐字首尾与块结构计数（3 个 h2、≥10 个 h3、≥4 个 list）作为更强判据。

### GREEN — minimum implementation
- Implementation: 新增 `src/store/workspace.ts`、`src/store/workspaceData.ts`、`src/hooks/useWorkspace.ts`、`src/components/WorkspaceDataProvider.tsx`、`src/pages/WorkspaceRouter.tsx`、`src/styles/workspace.css`；重写 `src/layouts/AppShell.tsx` 与 `src/main.tsx`；补 `sidebar` 字形与 8 个工作区文案键。
- Command: `pnpm --dir frontend test -- test/store/workspace.test.ts test/store/workspaceData.test.ts test/shell/AppShell.test.ts`
- Exit code: 0
- Passing evidence: `Test Files 14 passed (14)`、`Tests 82 passed (82)`；本片三支用例 27 条全过。
- Regression evidence: 同一轮内 `SnapshotScreen.test.tsx`、`HomePage.test.tsx`、`ChatPage.test.tsx`、`IconParity.test.tsx`、`FixtureRuntimeAdapter.test.ts` 等旧套件全部通过。
- Lint: `pnpm --dir frontend lint` 退出码 0；`dependency direction ok`（29 文件）、`device time ok`、`user copy ok`（cjk-lines=0）、`SYNC CHECK ok`（13/13）。
- TS fix: `voiceCategory` 的 notes 数组由 `texts[6]` 可能为 undefined 收窄为显式长度判断，避免 `(string | undefined)[]`。

### REFACTOR — optional
- Cleanup: 无额外重构；`workspaceData.ts` 的设置子页解析按类别拆成独立构建函数，共用 `spokenNodes` 与文本节点读取辅助，未引入第二套夹具解析器。
- Regression evidence: 同 GREEN 命令复跑即可覆盖。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 仓库动作命名：`openView(view)`、`openTopic(index)`、`back()`、`setPanelOpen(open)`、`toggleNav()`、`setNavCollapsed(value)`、`setDraft(key, text)`、`rememberScroll(key, top)`、`setLibraryQuery`、`setLibraryFilter`、`toggleSummary`、`setSettingsCategory`。
- `WorkspaceState` 的 `stack` 存 `{view, topic}`，不存 panel/draft；面板与草稿是跨视图的 UI 状态。
- hash 解析只接受 `home|library|settings|chat`；非法值回落 home；topic 收敛到 0。Provider/AppShell 的 effect 监听 `popstate`，卸载时移除。
- 数据 Provider 读取屏幕名单常量：`home`、`library`、`chat`、`navigation`、`treecanvas`、`settings`、`settingskey`、`provideradmin`、`modelsettings`、`searchservicesettings`、`voicesettings`、`filemanager`。任一失败不影响其他屏；`status` 在全部结束后变 ready/error。
- 主题模型以 `library` 为主；`home` 缺失时仍可从 library 得到标题/摘要/meta；推荐只来自 home。
- 回答块解析：空行分段；`一、` 开头为 h2；`1.`/`方法 N` 为 h3；`•`/`·` 开头为 bullet；其余为 p。正文逐字保留，不改写。
- 图片/视频/附件不涉及。所有图标按钮使用 `.icon-btn`，CSS 保证 44×44。
- 新字形：`sidebar`（矩形＋竖线，用于侧栏折叠）；`plus` 本片未使用，留到确有「新建」入口的切片再加，避免先造无主字形。
- `workspace.css` 只引用 token；44px 热区是交互下限，允许在 CSS 中作为常量出现但不得用作配色/间距设计值。
- hash 监听器在 `main.tsx` 注入 `popstate`/`hashchange`，随应用生命周期常驻；`applyWorkspaceHash` 幂等，组件不各自注册。

## Verification report

- Verdict: PASS
- Evidence: RED `rc=1`（3 个套件因生产模块缺失而收集失败，其余 11 套件 55 条全过）；GREEN `rc=0`（14 套件 82 条）；`pnpm --dir frontend lint` `rc=0`；夹具同步 13/13，文案守门 cjk-lines=0。
- Changed/deleted assertion rationale and equivalent behavior coverage: 旧 `AppShell.test.ts` 的两枚底部标签断言由侧栏导航、折叠与面包屑断言替代（`.app-tabs`/`.app-tab` 仍显式断言为不存在）；回答长度阈值由估算的 3000 调整为实测可证的 1500，逐字首尾与结构计数不变。旧三屏测试本片不改，043/044 才替换页面内容。
- 046 正式证据补记（2026-10-21）：`artifacts/evidence/web-workspace-implementation-2026-10-21/` 的 `readings.json` 22/22 通过，其中「桌面主页布局」「空格键折叠/展开侧栏」「浏览器后退/前进恢复对话与草稿」「宽度往返不清业务状态」覆盖本片外壳与状态；截图 `desktop-home.png`、`desktop-home-nav-collapsed.png`。
- Gaps: 页面内容仍是空槽；分支面板、知识树、主页、学习库、设置详情分别在 043–045 落地；真实浏览器截图与键盘/输入法证据在 046。
- Risks: 12 份夹具并行读取的首屏时序（测试已用双宏任务等待覆盖）；hash 在测试间污染（`beforeEach` 重置路径与仓库）；`workspace.css` 与旧 `app.css` 的类名目前无重叠。

## Handoff notes

- Completed: 仓库、视图模型、Provider、可折叠侧栏外壳、hash 位置同步、三支 TDD 用例与 lint 守门。
- In progress: 无（本片已验收）。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 043 接对话页（正文、Composer、分支面板、知识树）；044 接主页与学习库。
- Watch-outs: ① 不要把布局宽度写进状态；② 不要让滚动写入触发整树重渲染；③ 不要复用 `screenRoute` 形成第二套栈；④ 旧快照屏测试必须继续绿；⑤ 设置子页解析依赖夹具文本顺序，改夹具必须同步 `workspaceData.test.ts`。
