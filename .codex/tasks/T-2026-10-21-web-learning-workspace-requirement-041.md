# Filename: T-2026-10-21-web-learning-workspace-requirement-041.md
# Codex Harness Task: Web 轻量学习工作区需求父卡（正式界面落地）

id: T-2026-10-21-041
title: Web 轻量学习工作区需求父卡
template_version: 2
task_type: requirement
status: verified
parent_requirement: null
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: harness
stack: frontend
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 用户已确认采用 `Docs/04-ux-design/知芽-TreeLearn-Web学习工作区-交互示意-v1.html`（下称「示意稿」）的「轻量学习工作区」方向。当前 `frontend/` 是夹具快照骨架：主页、对话、学习库三屏各有一份精修组件，其余屏按采集坐标还原，外壳只有底部两枚标签，没有侧栏、没有分支面板、没有分类＋详情设置，也没有跨主题的草稿/筛选/阅读位置恢复。示意稿是独立提案，配套说明明确它不是正式运行规格；用户现在把它确认为 Web 界面的设计参照，但要求正式产品与原型隔离，不得导入示意稿或其演示脚本。
- Users/scenarios: 单人自用、平板横屏优先，桌面浏览器为主要验证面。场景：从侧栏进入主页或学习库 → 打开一个主题继续阅读 → 在顶部打开分支导航或知识树 → 回到学习库继续找另一个主题 → 回到设置查看当前配置。窄窗口下侧栏收成可展开抽屉，分支面板改为可关闭的覆盖层。
- Expected outcome: 在不新增依赖、不接后端、不伪造业务能力的前提下，把正式 Web 界面落成侧栏＋工作区：① 左侧可折叠导航（主页、学习库、最近学习、设置）；② 对话正文居中限宽，顶部工具区与底部输入区稳定，正文独立滚动；③ 分支导航默认关闭，宽窗口右侧并排、窄窗口覆盖；④ 学习库可扫描列表、摘要可展开、搜索与状态筛选；⑤ 设置分类＋详情；⑥ 主题/分支/返回切换恢复草稿、筛选与阅读位置，窗口变化不清空业务状态；⑦ 操作优先图标、44×44 热区、可访问名称与清楚的聚焦/选中/禁用反馈。
- Core flow and reversibility: 侧栏选视图 → 工作区换内容 → 打开主题（写入历史）→ 阅读/复制/展开分支面板/打开知识树 → 返回或浏览器后退。每个切片的回退是删除该切片新增的组件与样式并恢复对应测试；数据读取仍走 `RuntimePort` 与既有夹具，不引入新的持久化或网络路径。
- Inputs/outputs: 输入是 13 屏采集夹具（`frontend/src/platform/fixtures/*-android.json`）、`Icon.tsx` 的 26 枚 Android drawable 字形、`tokens.css` 的 44 个设计 token、文案表 `src/copy/zh-CN.ts`。输出是工作区组件、状态仓库、样式、测试与浏览器证据；不产生真实 AI 回答、不写设置、不上传附件、不创建分支。
- State and exception handling: 工作区状态（视图、主题、分支、面板、侧栏折叠、草稿、滚动位置、学习库搜索/筛选/展开、设置分类）只存内存；刷新后草稿与位置不保留，这一点在交付说明里明确写出。读取不到某屏夹具时显示既有 `state_fixture_unavailable`，不以空白或假数据顶替。主题 1 没有对话夹具，进入后显示「该主题还没有对话基线」并保留主题摘要，不拿主题 0 的回答冒充。设置六类详情来自真实夹具，全部只读；开关、输入、保存、删除、试听均禁用并说明尚未接通，不模拟成功。
- Constraints and trade-offs: 只改 `frontend/**` 与本任务卡/证据/相关规格修订；不动 `android/**`、`backend/**` 与示意稿/配套说明；不新增依赖；不自动提交或推送。复用现有图标、token、夹具与 `RuntimePort`，不另建设计系统、不把示意稿的 `demo-data` 或 JS 搬进生产。图标优先，但导航名、分类名、筛选项与必要状态文字保留；含义不清的操作保留可访问名称与悬停提示。旧快照渲染器（`SnapshotScreen`/`ScreenRouter`/`ScreenRoute`）保留为逐屏夹具证据，不在本轮主路径上；删除它们会超出用户授权并丢失既有parity能力，退役另案。
- AI recommendation and rationale: 先把数据模型与工作区状态仓库做成可单测的纯函数/模块，再落外壳和三屏，避免把状态塞进组件的 `useState` 后无法跨主题恢复。分支模型只承认夹具里有证据的东西：根分支、`0 子分支`、AI 总结；子分支数量只在学习库 meta 明说时展示并标注「内容尚未采集」。知识树用真实 `treecanvas` 夹具的主题与缩放语义，不画示意稿里 demo 生成的孩子节点。发送/附件/建分支/设置写入一律 disabled＋原因，不用演示行为。
- User choice and custom additions: 用户 2026-10-21 确认采用示意稿的轻量学习工作区方向；明确要求按钮优先图标、复用现有 SVG、不使用 emoji、不新增图标依赖、热区至少 44×44、可访问名称与悬停提示、清楚反馈；明确要求保留示意稿与配套说明、保留工作区未提交修改、不新增依赖、不自动提交或推送、不顺带改 Android；明确要求实际浏览器验证键盘/中文输入法/滚动/焦点/返回恢复/桌面与窄窗口，并提供截图；不能只凭构建成功宣布完成。
- AI-autonomous decisions: ① 用真实夹具作为唯一数据源，主题列表以学习库夹具为主、主页夹具兜底；② 只有主题 0 有对话夹具，主题 1 显示诚实的「无对话基线」；③ 分支面板固定为根分支＋已知子分支数，创建入口禁用；④ 设置详情按六类夹具做只读结构化呈现，不写回；⑤ 知识树用覆盖层展示真实根节点与真实缩放，不生成示意节点；⑥ 侧栏折叠、面板开关、搜索筛选、草稿与滚动位置进单一工作区仓库，宽度变化只影响 CSS；⑦ 浏览器内后退/前进用 hash 同步，刷新后仅恢复视图/主题，不恢复内存草稿；⑧ 旧快照屏保留但不在主路径。
- User-confirmation decisions: 采用示意稿方向；图标优先；保留资产与未提交修改；不新增依赖；不自动提交/推送；不改 Android；必须做真实浏览器验证与截图。
- Acceptance evidence: 每个实现片的 RED→GREEN 测试读数；`pnpm --dir frontend lint`、`pnpm --dir frontend test`、`pnpm --dir frontend verify`、`python -X utf8 .codex/scripts/dev/check_design_tokens.py`、`python -X utf8 .codex/scripts/validate_tasks.py` 全部退出码 0；桌面 1440×900 与窄窗 390×844 的实际浏览器截图落在 `artifacts/evidence/`；截图与 DOM 读数能证明侧栏折叠、分支面板、滚动、焦点、草稿/筛选/位置恢复、图标热区与禁用原因。
- Facts: 当前 `frontend/src` 生产代码 25 个文件、约 2.5k 行；`AppShell` 只有内容槽与底部 `tab_home`/`tab_library`；`ScreenRouter` 只精修 home/chat/library，其余走 `SnapshotScreen`；夹具视口统一 369.23×816 dp；`chat` 夹具只有主题 0 的回答与 3 行建议；`navigation` 夹具只有根分支、`当前 · 0 子分支` 与 AI 总结；`treecanvas` 夹具只有总览标题/主题副标题与四个缩放工具；`settings` 及其六个子页夹具提供真实只读配置文本；没有后端、没有生成接口、没有设置写入接口、没有附件上传接口、没有分支创建接口；`Icon.tsx` 已有 26 枚 Android drawable 字形与 id 映射；`tokens.css` 有 44 个 token，禁止手改。
- Assumptions: 示意稿的 240/76 px 侧栏、784 px 阅读列、720 px 输入卡、320 px 分支面板与 1181/760 两个断点视为设计意图，落地时用 token 与 `minmax`/`clamp` 表达而不是照抄示意稿的行内值；`V4.1 Flash` 与主题文案来自夹具，不当作可切换能力；浏览器剪贴板 API 可用时复制是真实能力。
- Unknowns: 真实生成/设置写入/附件/分支创建何时接入（依赖尚未落地的后端与 Android 侧能力）；平板 WebView 的第二浏览器档本轮不测；示意稿的 `speechSynthesis` 试听是否算正式语音能力未拍板，本轮按未接通处理。
- Risks: 一次性替换三屏会撞上现有快照测试与图标判据，必须同步改写断言并保留等价覆盖；`app.css` 已 425 行，新增工作区样式容易越过 800 行硬门禁，需拆独立样式文件；学习库与主页夹具的 meta 只有「3天前」，不能伪造更精确的时间；主题 1 的分支数 meta 写 2 但夹具没有子分支内容，面板必须区分「已知数量」与「已采集内容」；旧快照路径与工作区并存可能被读成两套界面，文档必须写明主路径。
- Deferred: 真实发送/生成/流式、附件上传与删除、设置写入与校验、创建/切换子分支、主题 1 的对话与分支内容、刷新后保留草稿/位置、跨设备同步、后端与账号、示意稿的语音试听、旧快照屏退役。
- Non-goals: 不改 Android、不改后端、不新增依赖、不导入或复制示意稿代码/演示数据、不新建设计 token、不做英文或其他语言、不做远程服务/账号/遥测、不把 SQLite/密钥/生成权威搬进浏览器、不删除示意稿或配套说明、不自动提交或推送。
- Skipped non-critical questions and reason: 没有追问侧栏折叠动画时长、卡片阴影档位或知识树节点间距；这些不改变目标、架构、验收或风险，交给 token 与 CSS 在实现片决定。没有追问「已完成」筛选的空态措辞之外的文案；属于实现片按文案表落地的细节。

## Goal

把已确认的「轻量学习工作区」示意稿落成正式 Web 界面：侧栏＋工作区、居中阅读列与稳定输入区、宽窄两态分支面板、可扫描学习库、分类＋详情设置、跨主题/返回的草稿与位置恢复，以及图标优先的可访问操作；同时把未接通的真实能力明确标注，不以演示行为冒充。

## Scope

### In scope
- 片 042：工作区状态仓库、数据视图模型、`hash` 后退同步、图标补充（侧栏/加号等）、`AppShell` 侧栏＋顶部栏＋主区骨架、响应式与可访问基础样式；替换 `main.tsx` 的入口编排；TDD 测试。
- 片 043：对话工作区（居中限宽正文、稳定顶部工具区与底部输入区、复制、建议预填、禁用动作说明）、分支面板（宽并排/窄覆盖、Esc/焦点返回）、知识树覆盖层（真实根节点与缩放）；TDD 测试。
- 片 044：主页（问题输入、推荐、历史主题）与学习库（搜索、状态筛选、摘要展开、空态、恢复筛选/滚动）；侧栏「最近学习」；TDD 测试。
- 片 045：设置分类＋详情（六类真实夹具、只读说明、禁用控件、分类与滚动恢复）；TDD 测试。
- 片 046：桌面与窄窗真实浏览器验证、键盘/中文输入法/滚动/焦点/返回恢复证据、规格修订与交付说明；跑全部前端守门与任务校验。
- 重写受影响的 `AppShell`/`HomePage`/`ChatPage` 测试与 `test/pages/IconParity.test.tsx` 的入口，使它们断言工作区而不是旧外壳；保留旧 `SnapshotScreen` 测试。

### Out of scope
- 任何 `android/**`、`backend/**` 代码或配置改动。
- 新增依赖、图标库、路由库、状态库、CSS 框架。
- 真实 AI 生成、流式回答、附件上传、设置写入、分支创建/切换、跨刷新持久化。
- 修改或删除示意稿 HTML 与配套说明。
- 删除 `SnapshotScreen`、`ScreenRouter`、`screenRoute` 及其测试；旧快照屏退役另案。
- 英文文案、远程部署、账号、同步、遥测。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 主页推荐与历史主题仍可进入对话；对话仍读出主题 0 的完整回答、3 行建议与 `V4.1 Flash`；学习库仍列两个主题；所有夹具正文与 `data-node` 身份不被改写；图标继续来自 Android drawable 同源副本。旧底部标签导航被侧栏替代，`settings` 主路径由六类详情替代旧设置入口；这是用户确认的方向变化，需在交付说明与规格修订中写明。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `main.tsx`（入口编排）、`AppShell`（外壳）、`ScreenRouter`（旧屏路由）、`useFixtureScreen`/`useSnapshot`/`RuntimePort`（数据读取）、`Icon.tsx`/`NodeTap.tsx`（字形与旧可点面）、`screenLinks.ts`（旧换屏映射）、`src/copy`（可见文案）、`tokens.css`（设计值）、`test/pages/*` 与 `test/shell/*`（断言）。工作区新增仓库与视图模型只依赖 `platform` 与 React，不改 `RuntimePort` 契约。
- Native bridge or independent-client compatibility/update requirements: 不适用——不改原生桥、不改夹具采集器、不改 Android。Web 与 Android 的数据/文案继续以夹具为共同证据。
- Behavior-neutral non-applicability reason, if applicable: 旧快照屏及其测试不改行为，只保留；本轮不改 `RuntimePort`、夹具字节或 token 生成链。

## Documentation impact record

- Changed runtime facts: Web 主路径从「底部两标签＋逐屏快照」变为「侧栏＋工作区」；发送/附件/建分支/设置写入在 Web 端仍未接通，必须明确显示而不是演示成功；主题 1 无对话基线；设置详情只读；分支面板只有根分支与已知子分支数量。
- Authoritative code/config/migration evidence: `frontend/src/platform/fixtures/*.json`（13 屏真实采集）、`frontend/src/main.tsx`、`frontend/src/layouts/AppShell.tsx`、`frontend/src/components/Icon.tsx`、`frontend/src/styles/tokens.css`、`frontend/scripts/*` 守门；对照 `Docs/04-ux-design/知芽-TreeLearn-Web学习工作区-交互示意-v1.html` 与配套说明（只读参照）。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`、`.codex/ROADMAP.md`、`.codex/rules/frontend.md`、`.codex/rules/ux-design.md`、`.codex/rules/regression.md`、`.codex/rules/maintainability.md`、`.codex/context/source-of-truth.md`、`AGENTS.md`。
- Documentation files updated: 片 046 已在 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` 追加 2026-10-21 修订段，登记用户对 Web IA 的确认、主路径变化、仍未接通的能力与旧快照屏的过渡状态；历史条目未改写。证据说明落在 `artifacts/evidence/web-workspace-implementation-2026-10-21/README.md`。
- Producer/consumer/sibling/compatibility paths checked: `ScreenRouter`/`SnapshotScreen`/`screenRoute` 保留但不在主路径；`screenLinks` 仅供旧快照；`Icon.tsx` 与 `NodeTap.tsx` 继续被旧屏使用，工作区直接复用 `Icon`；`useFixtureScreen` 继续是读屏入口，新增工作区读取走同一 `RuntimePort`。
- Superseded phrases/examples reverse-searched: 「底部两枚标签」「逐屏快照即界面」「示意保存」「示意创建分支」「交互示意」「发送后追加本地消息」——实现前反查，禁止把演示语义带进生产。
- `documentation impact: none` reason and evidence, if applicable: 不适用——主路径与未接通能力都需要写进权威规格。
- Historical/PRD handling: 示意稿与配套说明按用户要求原样保留，不修改、不搬入；旧快照屏与 parity 台账作为历史证据保留。规格修订只追加，不改写 2026-10-15 的原始范围叙述。

## Acceptance criteria

- [x] 片 042 的工作区状态与数据视图模型有独立单测：视图/主题/分支切换、返回栈、草稿按主题保存、滚动位置、学习库筛选、设置分类、窗口 resize 不清状态；`pnpm --dir frontend test -- test/store/workspace.test.ts` 通过。
- [x] 片 043 的对话页在真实夹具下渲染完整回答、3 行建议、复制可用；发送/附件/重置/新建分支明确 disabled 且可访问名称说明未接通；分支面板宽窄两态、Esc 关闭并归还焦点；知识树覆盖层缩放真实可读。
- [x] 片 044 的主页与学习库使用真实夹具：推荐与历史主题可进入对应主题；搜索/状态筛选/摘要展开/空态成立；离开再返回恢复查询、筛选与滚动位置。
- [x] 片 045 的设置分类＋详情覆盖六类真实配置，全部写入控件禁用并有只读说明；分类切换与返回恢复分类与滚动位置。
- [x] 所有工作区图标按钮复用 `Icon.tsx`，无 emoji、无新增依赖；可访问名称、`title`、44×44 热区、`focus-visible`、选中/禁用反馈在测试或真实浏览器中可复核。
- [x] 旧行为回归：主题 0 的回答正文逐字仍在，推荐/历史主题入口仍进对话，学习库仍列两个主题，图标同源判据仍通过，旧快照屏测试仍通过。
- [x] `pnpm --dir frontend lint`、`pnpm --dir frontend test`、`pnpm --dir frontend verify`、`python -X utf8 .codex/scripts/dev/check_design_tokens.py`、`python -X utf8 .codex/scripts/validate_tasks.py` 全部退出码 0。
- [x] 桌面 1440×900 与窄窗 390×844 的真实浏览器截图落在 `artifacts/evidence/`；键盘、中文输入法组合键、焦点返回、滚动恢复与窗口 resize 有实测记录，不以构建成功代替。
- [x] 示意稿 HTML 与配套说明字节不变；`git diff --name-only` 不出现 `android/**`、`backend/**` 或上述两份设计参照。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（需求父卡，无被测实现；各实现片分别记录 RED）
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 父卡只承载已确认需求与编排；实现由 042–046 逐片 TDD。
- User approval: 用户 2026-10-21 已确认采用示意稿方向、Harness 调度与验证要求。
- Alternative verification: 各实现片的 RED/GREEN 读数与 046 的真实浏览器证据。

## Implementation notes

- 编号：现有任务最高 040，本卡取 041，实现片顺延 042–046。
- 依赖顺序：042（仓库/外壳）→ 043（对话/分支/树）与 044（主页/学习库）→ 045（设置）→ 046（证据/文档）。
- 数据边界：工作区只读夹具，不写后台；主题模型来自学习库夹具，主页夹具兜底；只有主题 0 有对话夹具；分支模型只承认根分支与已知数量；设置六类详情来自各自夹具。
- 旧快照路径：`SnapshotScreen`/`ScreenRouter`/`screenRoute`/`screenLinks` 与 `snapshot.css` 保留，供逐屏 parity 证据；主入口不再使用它们，交付说明必须写明这是过渡状态而不是双活界面。
- 样式：新增工作区样式拆到独立文件（例如 `workspace.css`），避免把 `app.css` 推向 800 行硬门禁；所有颜色/间距/字号/圆角/阴影引用 token，不写裸 hex 或临场 px（44×44 热区是交互下限，不是设计 token 的替代）。
- 图标：优先复用 `Icon.tsx`；需要新字形时继续从 `android/app/src/main/res/drawable/ic_*.xml` 取 pathData，并在同源判据里登记；不使用 emoji、不引入图标依赖。
- 浏览器：headless Edge + CDP 仅作取证工具；窄窗必须用 `Emulation.setDeviceMetricsOverride`，不能依赖 `--window-size=390`（Chromium headless 有最小窗口宽度）。

## Verification report

- Verdict: PASS
- Evidence: 042–045 各自 `status: verified` 且 Verification report 记录 RED/GREEN 与浏览器补记；046 完成最终整跑与取证：`lint`/`test`/`verify`/`check_design_tokens`/`validate_tasks` 全部退出码 0（16 文件 107 例、verify 16/16、token OK=10 FAIL=0、任务 errors=0）；`artifacts/evidence/web-workspace-implementation-2026-10-21/` 的 22/22 浏览器读数与 11 张桌面/窄窗截图覆盖侧栏、滚动、IME、复制、宽窄分支面板、知识树、库筛选/滚动恢复、设置恢复与 resize；规格已追加 2026-10-21 修订段。
- Changed/deleted assertion rationale and equivalent behavior coverage: 旧 `AppShell` 底部标签与旧三屏精修测试随主路径改写；等价覆盖保留在 `workspace` 系列测试与图标同源判据中。旧 `SnapshotScreen` 测试不改，旧快照路径保留为 parity 证据。
- Gaps: 真实生成/设置写入/附件/建分支/刷新持久化未接通；平板 WebView 档、读屏器与真实 IME 应用未测；主题 1 无对话夹具；知识树仅根节点。
- Risks: 样式总量与新组件可能触发行数告警（当前 13 条软上限 warning）；旧快照与工作区并存需要文档解释（已写入规格修订）；主题 1 的 meta 分支数与实际内容不一致，已在 UI 上区分。

## Handoff notes

- Completed: 042–046 全部 verified；需求方向、实现、单测、守门、真实浏览器证据、规格修订与交付说明均已落地。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 按证据 README 向用户交付已实现内容、与示意稿的必要差异与仍未接通能力；后续接入后端/生成/设置写入时回到对应实现卡并重新取证。
- Watch-outs: ① 不得把示意稿的发送/保存/建分支/附件演示当正式能力；② 不得删除示意稿或配套说明；③ 不得回退工作区已有未提交修改；④ 主题 1 不得复用主题 0 的回答；⑤ 旧快照屏保留但不要接回主路径；⑥ 新增可见文案必须进 `src/copy`，否则文案守门转红。
