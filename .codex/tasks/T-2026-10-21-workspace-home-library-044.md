# Filename: T-2026-10-21-workspace-home-library-044.md
# Codex Harness Task: 主页与学习库工作区（搜索、筛选、摘要与恢复）

id: T-2026-10-21-044
title: 主页与学习库工作区
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-21-web-learning-workspace-requirement-041.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 320
shared_code_touched: true
depends_on: [T-2026-10-21-workspace-shell-and-state-042.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 工作区外壳与状态仓库已由 042 交付。本片把主页与学习库改成示意稿的扫描型工作区，并让查询、筛选、摘要展开与滚动位置在切换后恢复。
- Problem/background: 现有 `HomePage` 是旧快照骨架：品牌行＋大输入卡＋推荐区＋主题卡，没有侧栏最近学习，也没有可恢复的草稿；现有 `LibraryPage` 只有静态搜索提示与静态分段，搜索、筛选、摘要展开都不可操作，离开再回来不保留任何条件。
- Users/scenarios: 用户打开主页直接继续最近主题或点推荐问题进入对话；在学习库输入关键词、切换「进行中/已完成」、展开摘要找主题；去对话读一段再回学习库，查询、筛选、滚动位置与展开状态仍在。
- Expected outcome: 主页使用真实 `home` 夹具的推荐与主题卡、真实 `chat` 建议与模型信息；输入框草稿按 `home` key 保存并在返回时恢复；学习库使用真实 `library` 夹具的两个主题、摘要与 meta，搜索匹配标题/摘要/来源问题，状态筛选以真实 meta 为准，摘要可展开，零结果显示空态；侧栏「最近学习」列真实主题；两页所有操作按钮图标化并有可访问名。
- Core flow and reversibility: 主页输入/推荐/主题卡 → `openTopic` 或填草稿；学习库搜索/筛选/展开 → 仓库写入 → 离开写回滚动 → 返回按 key 恢复。回退为恢复旧 `HomePage`/`LibraryPage` 与测试；不动仓库与夹具。
- Inputs/outputs: 输入是 `WorkspaceData.topics/recommendations/chat`、`WorkspaceState.libraryQuery/libraryFilter/libraryExpanded/drafts/scroll`；输出是两页 DOM 与仓库写入。不产生网络、存储或真实生成。
- State and exception handling: `library` 夹具缺失时显示 `state_fixture_unavailable`；查询无结果显示空态文案并提供清空入口；状态筛选没有匹配时同样空态；「已完成」在真实夹具里为 0 条，必须如实显示空态而不是伪造完成项；摘要展开状态按主题 id 记录；切换筛选不清查询，清空查询不清筛选；窗口 resize 不改任何状态。
- Constraints and trade-offs: 搜索只在已采集的标题、摘要、来源问题上做本地子串匹配，不做全文检索、不读回答正文之外的推断；筛选值只能是 `all/in_progress/done`，与夹具 meta 的真实状态对齐；不伪造「已完成」条目；不新增依赖、不改夹具；所有可见文案进 `src/copy`；图标按钮 44×44。
- AI recommendation and rationale: 把搜索/筛选/展开放进 042 的仓库而不是页面本地状态，正是因为用户明确要求「切换主题、查看分支及返回时合理恢复筛选条件和阅读位置」；页面只做受控呈现。最近学习复用主题模型，不另建数据源。
- User choice and custom additions: 图标优先（搜索、清空、展开/收起、状态筛选、进入主题均优先图标或图标＋短文字）；必要文字（导航名、筛选项、主题状态）保留；真实浏览器验证键盘与中文输入法；不改 Android；不新增依赖。
- AI-autonomous decisions: ① 搜索大小写不敏感、忽略首尾空白；② 展开按钮 `aria-expanded` 与 `aria-controls` 指向摘要区域；③ 分段筛选保留文字标签（含义不能只靠图标）；④ 「最近学习」取主题顺序前两条，不按伪造时间排序；⑤ 主页输入框 Enter 不发送（未接通），保留换行；⑥ 推荐点击进入对应主题并把问题填入对话草稿；⑦ 学习库滚动位置按 `library` 单 key 记录。
- User-confirmation decisions: 见父卡；本片不需要新的用户拍板。
- Acceptance evidence: 重写后的 `test/pages/HomePage.test.tsx`、`test/pages/LibraryPage.test.ts`（或 `.tsx`）RED/GREEN；搜索/筛选/展开/空态/恢复断言；真实浏览器证据在 046。
- Facts: `home` 夹具 60 节点，两条推荐、两张主题卡、`V4.1 Flash`、草稿占位；`library` 夹具 28 节点，两个主题（1 分支/2 分支，3天前），搜索占位「标题、问题、回答、锚定原文…」，分段「进行中 2」「已完成 0」；`chat` 夹具 3 条建议且属于主题 0；主题 1 无对话夹具；`Icon.tsx` 已有 search/chevron/filter 等相关字形需先核对。
- Assumptions: `library_topic` 两卡与 `home_topic_card` 两卡是同一集合且顺序一致；meta 中的「进行中」是唯一真实状态；推荐问题与主题 0 对应。
- Unknowns: 真实搜索索引与跨回答检索何时接通；「已完成」真实数据不存在。
- Risks: 全局查询状态每次键入重渲染整棵树（数据量小，本轮接受）；滚动恢复与筛选后列表高度变化可能越界，需夹到可滚动范围；旧主页/学习库测试断言大量依赖旧类名，必须同步改写并保留行为等价覆盖。
- Deferred: 真实全文搜索、完成状态数据、跨刷新保留筛选、排序选项。
- Non-goals: 不改夹具、不新增依赖、不改 Android/后端、不伪造已完成主题、不复制示意稿演示数据。
- Skipped non-critical questions and reason: 没有追问搜索防抖时长与摘要动画；不影响验收，交由实现按 token 决定。

## Goal

用真实夹具交付可扫描、可搜索、可筛选、可展开的主页与学习库，并让草稿、查询、筛选、展开与滚动位置在视图切换和窗口变化后仍可恢复。

## Scope

### In scope
- 重写 `frontend/src/pages/HomePage.tsx`：问题输入卡（草稿）、推荐问题、历史/最近主题、模型信息；进入主题走 `openTopic`。
- 重写 `frontend/src/pages/LibraryPage.tsx`：搜索输入、状态分段（全部/进行中/已完成）、主题列表、摘要展开、空态、清空入口。
- `frontend/src/layouts/AppShell.tsx` 侧栏「最近学习」用真实主题模型渲染（042 已留槽）。
- `frontend/src/styles/workspace.css` 的主页/学习库样式。
- `frontend/src/copy/zh-CN.ts` 新增文案。
- 重写 `frontend/test/pages/HomePage.test.tsx`；新增或重写 `frontend/test/pages/LibraryPage.test.tsx`。

### Out of scope
- 设置页（045）；对话正文/面板/树（043）。
- 真实搜索索引、真实发送、完成状态数据。
- 旧快照屏删除。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 主页仍显示两条推荐、两张主题卡与 `V4.1 Flash`；推荐与历史主题仍进入主题 0 对话；学习库仍列两个主题与真实摘要/meta；`data-node` 身份与夹具正文不被改写。旧静态分段与静态搜索提示被真实交互替代，这是用户确认的方向变化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `HomePage`/`LibraryPage` 读 `WorkspaceData`，写 `setDraft/openTopic/setLibraryQuery/setLibraryFilter/toggleSummary/rememberScroll`；`AppShell` 侧栏 recent 读同一 `topics`；`WorkspaceRouter` 接入两页；旧 `SnapshotScreen` 不受影响。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 夹具、`RuntimePort`、旧快照屏不改。

## Documentation impact record

- Changed runtime facts: 主页与学习库进入工作区主路径；搜索/筛选/展开成为真实本地交互；「已完成」为真实空集；最近学习来自真实主题模型。
- Authoritative code/config/migration evidence: `frontend/src/platform/fixtures/home-android.json`、`library-android.json`、`chat-android.json`；`frontend/src/store/workspaceData.ts`。
- Documentation surfaces checked: 父卡 041、`.codex/rules/frontend.md`、`.codex/rules/regression.md`、`.codex/rules/ux-design.md`。
- Documentation files updated: 本卡与父卡；权威规格修订留给 046。
- Producer/consumer/sibling/compatibility paths checked: 旧 `HomePage.test.tsx` 的 `.rec-list button[data-node="rec_chip"]` 等断言改写为工作区选择器；旧学习库静态分段断言改为真实筛选行为；等价覆盖保留。
- Superseded phrases/examples reverse-searched: 「静态搜索提示」「静态分段」「历史卡点击直接换屏」——本片改写。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 旧主页/学习库快照仍是 parity 证据的一部分，夹具与旧快照屏保留。

## Acceptance criteria

- [x] 主页：真实推荐两条、主题卡两张、`V4.1 Flash` 可见；推荐点击进入主题 0 且问题写入对话草稿；主题卡进入对应主题；草稿在离开再返回后恢复。
- [x] 学习库：两个真实主题可见，摘要默认收起、可展开且 `aria-expanded` 正确；搜索命中标题/摘要；无命中显示空态并可清空；「进行中 2」「已完成 0」按真实 meta 工作；离开再返回恢复查询、筛选、展开与滚动位置。
- [x] 侧栏「最近学习」列出真实主题并可进入；不出现伪造时间或主题。
- [x] 所有图标按钮复用 `Icon`、有可访问名与 `title`、44×44 热区；筛选项保留文字；无 emoji、无新依赖。
- [x] `pnpm --dir frontend test -- test/pages/HomePage.test.tsx test/pages/LibraryPage.test.tsx` 退出码 0；`pnpm --dir frontend lint` 退出码 0。
- [x] 旧 `SnapshotScreen.test.tsx` 仍绿；窗口 resize 后两页状态不变。

## TDD execution record

### RED — failing test first
- Test/scenario: 重写后的 `HomePage.test.tsx`（5 例）与新增 `LibraryPage.test.tsx`（7 例）：真实推荐/主题/模型、推荐回填对话草稿、主页草稿恢复、搜索/筛选/展开/空态/滚动恢复/resize/图标可访问名。
- Command: `pnpm --dir frontend test -- test/pages/HomePage.test.tsx test/pages/LibraryPage.test.tsx`
- Exit code: 1
- Observed failure: 两个文件均为红；同一轮全量汇总 `Test Files 2 failed | 13 passed (15)`、`Tests 11 failed | 86 passed (97)`。失败集中在两页：旧 `HomePage` 未接 `home` 草稿与真实推荐、旧 `LibraryPage` 没有可操作的搜索/筛选/摘要，新增文案 key 不存在。
- Failure reason validated: 失败由缺失的工作区页面与文案引起，而非环境或选择器笔误；同一选择器在实现后命中。

### GREEN — minimum implementation
- Implementation: 重写 `HomePage`/`LibraryPage`，接入 `WorkspaceData` 与 `workspace` 仓库；`Composer` 增加可复用插槽（不改变对话语义）；`workspaceData` 增加 `home` 模型与真实状态推断；`pages.css` 新增两页版式；文案进 `src/copy`；`WorkspaceRouter` 接入两页，`ScreenRouter` 只保留快照证据。
- Command: `pnpm --dir frontend test -- test/pages/HomePage.test.tsx test/pages/LibraryPage.test.tsx`
- Exit code: 0
- Passing evidence: `Test Files 2 passed (2)`、`Tests 12 passed (12)`；全量 `pnpm --dir frontend test` 为 `Test Files 15 passed (15)`、`Tests 99 passed (99)`；`pnpm --dir frontend lint` 退出码 0（依赖方向 34 文件、设备时间、文案、夹具同步 13/13 全绿）。
- Regression evidence: `SnapshotScreen.test.tsx` 5/5、`IconParity.test.tsx` 5/5 仍绿；旧快照路由与夹具未改。

### REFACTOR — optional
- Cleanup: ① `Composer` 抽出 `inputId/label/note/dock*` 插槽复用；② `SnapshotScreen` 卡片 key 加入 bounds，消除 `home_topic_card` 重复 id 的 React key 警告；③ 在 `.app-workspace` 作用域补 `box-sizing: border-box`，对齐示意稿的全局盒模型（否则页宽 1024、侧栏 273，而非 960/240），快照栈保持原盒模型。
- Regression evidence: 重跑全量 99 例与 lint 均绿；浏览器量得 `.page` 960、侧栏 240/收起 76。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 搜索匹配字段：主题标题、摘要、来源问题（home 推荐文本）；回答正文不参与本轮搜索（夹具正文属于主题 0，避免把跨主题结果说成能力）。
- 状态映射：夹具 meta 明确「进行中」→ `in_progress`；「已完成」→ `done`；未知 meta 不猜测状态，归入 `all` 可见但在筛选计数里不冒充。
- 摘要区域用 `id`＋`aria-controls`；展开状态按主题 id 存 `Set<string>` 的序列化数组，便于测试断言。
- 空态必须区分「无匹配」与「筛选后为空」，文案进 `src/copy`。
- 滚动恢复：页面根滚动容器 ref 在 `useEffect` 中读取 `scrollFor("library")` 并写入；卸载或视图切换前 `rememberScroll`。
- 最近学习只在侧栏展示，不在主页重复第三份主题列表；主页保留推荐＋历史（历史即主题卡）。
- 所有图标按钮通过 043 的 `IconButton`（若 043 尚未落地，本片先复用同组件接口，不重复实现）。

## Verification report

- Verdict: PASS
- Evidence:
  - 单测：`pnpm --dir frontend test` → 15 文件 / 99 例全绿；验收双文件 2 文件 / 12 例全绿。
  - 守门：`pnpm --dir frontend lint` 退出码 0（tsc、依赖方向、设备时间、用户文案、夹具同步）。
  - 真实浏览器（1440×900 与 390×844，CDP）：主页 2 推荐、2 主题卡、`V4.1 Flash`、真实占位与侧栏 2 条最近学习；推荐点击得到 `#/chat/0` 且对话草稿为推荐原文；主页草稿往返恢复；学习库 2 行、分段 `全部 2/进行中 2/已完成 0`、搜索「消融」＋进行中＋展开后 1 行、无命中出 `data-empty="search"` 并可清空；`.page` 960、侧栏 240（收起 76），390 窄窗无横向溢出。
  - 滚动取证：1440×480 短窗下 `scrollHeight 595 > clientHeight 404`，滚到 160 后往返学习库仍为 160（真实溢出，不是不可滚动的假读数）。
  - 截图：`artifacts/tmp/probe-044/01-home-desktop.png` 至 `11-home-narrow.png`（临时取证，046 归档正式证据）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 旧 `HomePage.test.tsx` 的旧类名与旧换屏断言改写为工作区行为断言；旧学习库没有测试的静态交互由新筛选/搜索测试覆盖；`IconParity` 两例改走工作区外壳，覆盖不变且新增搜索/摘要清空按钮。
- 046 正式证据补记（2026-10-21）：`artifacts/evidence/web-workspace-implementation-2026-10-21/` 的 22 项读数覆盖本片：桌面主页 960/240 与品牌图标、学习库两行、搜索「关键头」命中 1 行、「进行中」按下、摘要展开 `aria-expanded=true`、1440×520 下 `scrollHeight 570 > clientHeight 444` 滚到 126 并在往返后恢复、390×844 无横向溢出且筛选保持；截图 `desktop-library.png`、`desktop-library-filtered.png`、`narrow-library.png`。
- Gaps: 真实全文搜索索引、真实完成状态数据、跨刷新保留筛选与阅读位置未接通；发送/上传仍按既有语义禁用。
- Risks: 滚动恢复依赖内容高度，筛选后列表变短会被浏览器夹到可滚范围（已在短窗下验证恢复）；全局查询状态每次键入重渲染整棵树（当前数据量可接受）。

## Handoff notes

- Completed: 主页、学习库、侧栏最近学习、搜索/筛选/展开/空态、草稿与滚动恢复；盒模型对齐示意稿；单测与守门全绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 045 设置分类＋详情；046 正式浏览器取证（键盘/中文输入法/焦点/返回恢复）与规格修订。
- Watch-outs: ① 不伪造已完成主题；② 搜索不得声称覆盖回答正文；③ 恢复条件在仓库而非页面本地 state；④ 图标与文案守门；⑤ 046 的滚动取证同样要用真实溢出场景。
