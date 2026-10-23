# Filename: T-2026-10-21-workspace-chat-and-branch-panel-043.md
# Codex Harness Task: 对话工作区、分支面板与知识树覆盖层

id: T-2026-10-21-043
title: 对话工作区、分支面板与知识树覆盖层
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-21-web-learning-workspace-requirement-041.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 380
shared_code_touched: true
depends_on: [T-2026-10-21-workspace-shell-and-state-042.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 工作区外壳与状态已就绪。本片把对话主路径做成居中限宽阅读列＋稳定顶部工具区/底部输入区，分支导航宽并排/窄覆盖，知识树用真实根节点与缩放。
- Problem/background: 现有 ChatPage 把回答、动作、建议、输入区顺序堆在一个滚动屏里，输入区会随正文滚走；`chat_nav` 走旧导航快照，`chat_overview` 走旧画布快照；动作按钮只是静态节点，没有复制、没有禁用原因，也没有面板。
- Users/scenarios: 用户在主题 0 阅读长回答，正文独立滚动；复制整条回答；打开分支导航看根分支与 AI 总结；打开知识树缩放查看；在输入框写草稿，切到学习库再回来草稿仍在；窄窗下面板覆盖且可 Esc 关闭、焦点回到触发按钮。
- Expected outcome: ChatPage 从真实 `chat` 夹具读标题、回答、建议、模型、时间；回答按段落/小标题/列表渲成可读结构；复制调用剪贴板并有成功/失败反馈；重置/新建分支/发送/附件禁用且可访问名说明「尚未接通」；建议点击把问题填入草稿并聚焦；分支面板列出根分支（当前）、已知子分支数、AI 总结与未采集说明；知识树覆盖层显示主题根节点与真实缩放，不画 demo 子节点。
- Core flow and reversibility: 顶部工具按钮打开面板/树；Esc/关闭按钮/遮罩返回并还焦；输入框写入仓库草稿；离开对话写回滚动位置，回来按主题恢复。回退为删除新组件与测试，恢复旧 ChatPage。
- Inputs/outputs: 输入是 `WorkspaceData.chat`、`WorkspaceData.branches`、`WorkspaceData.topics`、`WorkspaceState`；输出是对话 DOM、复制状态、草稿、面板/树开关。不产生回答、不创建分支、不上传文件。
- State and exception handling: 主题 0 无 `chat` 夹具时显示 `state_fixture_unavailable`；主题 1（`hasChat=false`）显示「该主题还没有对话基线」＋学习库摘要，不显示假回答，动作与输入禁用；复制失败显示「复制未完成，请选中文字后复制」；面板打开时焦点进关闭按钮，关闭回触发按钮；开关状态写仓库，窗口 resize 不重置。
- Constraints and trade-offs: 不使用示意稿的 `demo-data`/JS；不新增依赖；复制只在 `navigator.clipboard` 或 `execCommand` 可用时成功，否则明确失败；分支创建只留禁用入口；树只画根节点与「0 子分支」，不生成示意稿里的子节点；长回答仍逐字来自夹具。
- AI recommendation and rationale: 阅读列与输入区用 flex 分成 `.chat-scroll` 与 `.chat-dock`，滚动只发生在正文；面板与树用覆盖层组件而不是新路由，避免用户丢失阅读位置；禁用动作统一用 `title`＋`aria-label`＋可见提示，避免「看起来能点」。
- User choice and custom additions: 图标优先：分支导航、知识树、关闭、复制、发送、附件、重置、新建分支全部用 `Icon`；44×44 热区；可访问名称与悬停提示；含义不清的必要文字（如「分支导航」）通过 `aria-label`/`title` 保留，不常驻大段文字。用户要求真实浏览器验证键盘/中文输入法。
- AI-autonomous decisions: ① 回答渲染器只做轻量结构识别，不引入 Markdown 依赖；② 复制文本取原始 `chat_answer` 全文而不是 DOM 文本，避免丢换行；③ 建议点击是「填入草稿」而不是「新建主题」，与真实能力一致；④ 支面板在 ≥1181 并排、≤1180 覆盖；⑤ 知识树缩放 0.5–1.5、重置 1；⑥ 主题 1 不伪造回答。
- User-confirmation decisions: 见父卡。
- Acceptance evidence: `test/pages/ChatPage.test.tsx` 重写后的 RED/GREEN；复制成功/失败、禁用原因、面板 Esc/焦点、树缩放、主题 1 空态断言；真实浏览器截图在 046。
- Facts: `chat` 夹具 46 节点，`chat_answer` 为完整长回答，`suggestion_text` 3 条，`act_copy/act_reset/act_fork` 3 个动作，`act_time`=「3天前」，`model_chip_label`=「V4.1 Flash」；`navigation` 有根分支、`当前 · 0 子分支`、AI 总结；`treecanvas` 有 `tree_title`=「总览画布」、`tree_subtitle`=主题名、四个缩放工具；`home` 推荐第一条是答案对应的问题。
- Assumptions: 主题 0 的回答与第一条推荐问题对应；`navigation` 的 AI 总结属于主题 0；主题 1 的 meta「2 个分支」是真实数量但内容未采集。
- Unknowns: 真实生成/分支创建/附件上传的接口；复制在无安全上下文时的行为（本地 http 应可用，`file://` 可能失败）。
- Risks: 长回答 345dp 文本在 Web 上重排后阅读位置恢复需按主题 key；面板宽窄切换时焦点不能丢；`<dialog>` 在 jsdom 不可用，改用受控覆盖层以保证测试与无障碍；禁用按钮的 `title` 在触屏不可见，需可见的「发送尚未接通」提示。
- Deferred: 真实发送/流式、附件、创建子分支、版本对比、书签/划词、语音。
- Non-goals: 不接入后端、不生成回答、不改 Android、不复制示意稿演示逻辑、不新增依赖。
- Skipped non-critical questions and reason: 没有追问回答标题层级的具体字号与行距；由 token 与 `lh-read` 决定，不影响验收。

## Goal

在真实夹具与工作区状态之上交付可阅读、可复制、可恢复的对话工作区，以及能表达真实分支/树状态且可键盘关闭的面板与覆盖层；所有未接通动作明确禁用并给出原因。

## Scope

### In scope
- 重写 `frontend/src/pages/ChatPage.tsx` 为工作区对话页；新增 `frontend/src/components/ChatAnswer.tsx`（回答块渲染）、`Composer.tsx`（草稿/附件/发送/模型芯片）、`BranchPanel.tsx`、`TreeOverlay.tsx`、`IconButton.tsx`（统一 44×44 图标按钮与提示）。
- `frontend/src/layouts/AppShell.tsx` 接入分支面板槽与树覆盖层触发；`WorkspaceRouter` 接入 ChatPage。
- `frontend/src/styles/workspace.css` 的对话/面板/树/输入区样式。
- `frontend/src/copy/zh-CN.ts` 新增文案。
- 重写 `frontend/test/pages/ChatPage.test.tsx`、新增/更新 `frontend/test/pages/IconParity.test.tsx` 的工作区对话断言。

### Out of scope
- 主页/学习库/设置的完整页面（044/045）。
- 真实生成、创建分支、附件上传。
- 旧 `SnapshotScreen` 的对话快照删除。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 旧对话页仍读出主题 0 的完整回答、3 行建议、`V4.1 Flash` 与「3天前」；`data-screen="chat"` 保留；夹具文本不改写。旧动作节点只读，新动作中复制可点，其余禁用——这是用户要求的能力诚实化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatPage` 读 `WorkspaceData`；`Composer` 写 `workspace.setDraft`；`BranchPanel` 读 `WorkspaceData.branches`；`TreeOverlay` 读 `treecanvas` 模型；`AppShell` 提供触发按钮；旧 `SnapshotScreen` 仍用 `navigation`/`treecanvas` 夹具，不改夹具。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 旧快照屏、`screenLinks`、`screenRoute` 不改。

## Documentation impact record

- Changed runtime facts: Web 对话页改为工作区布局；复制是真实能力；发送/附件/重置/新建分支仍未接通；主题 1 无对话基线；知识树覆盖层只展示真实根节点；窄窗（≤760px）侧栏初始收起、resize 自动收起，可用遮罩或 Esc 关闭。
- Authoritative code/config/migration evidence: `frontend/src/platform/fixtures/chat-android.json`、`navigation-android.json`、`treecanvas-android.json`、`home-android.json`；`frontend/src/store/workspaceData.ts`。
- Documentation surfaces checked: 父卡 041、`.codex/rules/frontend.md`、`.codex/rules/regression.md`、`.codex/rules/ux-design.md`。
- Documentation files updated: 本卡与父卡；权威规格修订留给 046。
- Producer/consumer/sibling/compatibility paths checked: 旧 `ChatPage` 测试断言的 `chat_nav→navigation`、`chat_overview→treecanvas` 被面板/树覆盖层替代；旧 `SnapshotScreen` 的 `navigation`/`treecanvas` 测试仍覆盖快照能力。
- Superseded phrases/examples reverse-searched: 「节点表里没有动作映射的入口画成静态块」「act_time 是静态 DIV」「chat_nav 打开 navigation 屏」——这些旧断言在本片改写。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 旧导航/画布快照作为 parity 证据保留；工作区面板/覆盖层是新的主路径。

## Acceptance criteria

- [x] `ChatPage.test.tsx`：主题 0 渲染标题、`V4.1 Flash`、「3天前」、完整回答块（含 h2/h3/列表）与 3 条建议；`.chat-scroll` 与 `.chat-dock` 同级；建议点击后输入框值等于建议问题且获得焦点。
- [x] 复制按钮可点，`navigator.clipboard.writeText` 收到原始回答全文；成功后出现「已复制回答」状态；剪贴板拒绝时出现失败文案；发送/附件/重置/新建分支为 disabled 且 `aria-label`/`title` 含「尚未接通」。
- [x] 分支面板：默认关闭；点击顶部按钮 `aria-expanded=true`，面板列出根分支与「当前」、子分支数、AI 总结；Esc 关闭且焦点回到触发按钮；窄窗遮罩可点关闭。
- [x] 知识树：从顶部按钮打开 `role="dialog"` 覆盖层，显示真实主题与根节点、无假子节点；放大/缩小/适应改变百分比并夹在 50–150%；Esc 关闭并还焦。
- [x] 主题 1：显示「该主题还没有对话基线」与学习库摘要，不渲染主题 0 回答；发送/复制/建分支禁用。
- [x] 所有图标按钮复用 `Icon`、有可访问名与 `title`、CSS 热区 44×44；无 emoji、无新依赖。
- [x] `pnpm --dir frontend test -- test/pages/ChatPage.test.tsx` 退出码 0；`pnpm --dir frontend lint` 退出码 0。
- [x] 旧 `SnapshotScreen.test.tsx` 仍绿；真实浏览器证据在 046。

## TDD execution record

### RED — failing test first
- Test/scenario: `frontend/test/pages/ChatPage.test.tsx` 重写为 9 条工作区对话用例（结构/建议/滚动与草稿/复制成败/禁用动作/分支面板/窄窗遮罩/知识树缩放/主题 1 空态）；同时把 `IconParity.test.tsx` 的对话断言指向工作区对话页。
- Command: `pnpm --dir frontend test -- test/pages/ChatPage.test.tsx`
- Exit code: 1
- Observed failure: `Test Files 1 failed | 13 passed (14)`，`Tests 9 failed | 79 passed (88)`；旧 ChatPage 没有 `.chat-scroll`/`.chat-dock` 同级结构、没有分支面板/知识树覆盖层，动作也不是带「尚未接通」的禁用按钮，9 条新用例全部未通过。
- Failure reason validated: 失败来自目标行为缺失（旧顺序堆叠页与静态动作节点），不是测试环境或选择器笔误；改写前先确认夹具与旧实现不具备这些结点。

### GREEN — minimum implementation
- Implementation: 重写 `ChatPage.tsx`；新增 `ChatAnswer`、`Composer`、`BranchPanel`、`TreeOverlay`、`IconButton`；`AppShell` 接入面板/树触发与窄窗侧栏收起（resize 自动收起、遮罩关闭、Esc 收起）；`workspace` 仓库增加 `treeOpen` 与面板互斥；沿用现有 `Icon` 与设计 token。真实能力边界：仅复制与草稿落库可用，发送/附件/重置/新建分支禁用并说明「尚未接通」；主题 1 不伪造回答；知识树只画真实根节点。
- Command: `pnpm --dir frontend test -- test/pages/ChatPage.test.tsx`
- Exit code: 0
- Passing evidence: `Test Files 14 passed (14)`，`Tests 90 passed (90)`；其中 `ChatPage.test.tsx` 9/9、`AppShell.test.ts` 7/7、`IconParity.test.tsx` 5/5。`pnpm --dir frontend lint` 退出码 0（依赖方向 34 文件 0 发现、用户文案 0 行中文直写、设备时间 0 发现、夹具 13/13 同步）。
- Regression evidence: `SnapshotScreen.test.tsx` 5/5 仍绿；全量 14 个测试文件 90 条全部通过；`icon-parity` 的首页/学习库/设置/画布断言未改且通过。

### REFACTOR — optional
- Cleanup: `ScreenRouter` 的 chat 分支改走 `SnapshotScreen`（保留旧快照与测试），工作区 `WorkspaceRouter` 独立接 `ChatPage`；`BranchPanel` 用 `[hidden]` 规则盖住作者样式，避免关闭后仍显示。
- Regression evidence: 同上全量与 `SnapshotScreen` 绿；窄窗截图复核后补齐侧栏收起与遮罩，`AppShell.test.ts` 新增两条窄窗用例并通过。

### Browser check (scratch, formal evidence in 046)
- 工具：仓库 CDP 仪器 + 全新无头 Edge；1440×900 与 390×844。
- 分支面板（1440）：打开后 `hidden=false`、`aria-expanded=true`、焦点 `branch-close`、宽 321px、遮罩 `display:none`（宽窗并排）；Esc 后 `hidden=true`、`aria-expanded=false`、焦点回 `branch-toggle`。
- 知识树（1440）：`role="dialog"` 打开，焦点 `tree-close`，只出现根节点与「夹具只采集到根节点」；连点放大 100%→120%；Esc 关闭、焦点回 `tree-toggle`。
- 草稿/返回：用 `Input.insertText` 写入中文草稿，点「学习库」后 `history.back()` 回到 `#/chat/0`，草稿原样恢复。
- 窄窗（390）：初始侧栏 `data-nav=collapsed`、`visibility:hidden`、内容宽 390；点折叠按钮展开且 `.nav-scrim` 出现，点遮罩收回；分支面板宽 341px、遮罩 `display:block`，点遮罩关闭并还焦；390 直接重载仍为收起态。首次窄窗截图暴露「侧栏展开挡住内容且无关闭入口」，已按示意稿 `go()/resize` 的收起策略修正并补遮罩。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- `ChatAnswer` 接受 `AnswerBlock[]`；h2/h3 用语义标题，bullet 用 `<ul><li>`，段落用 `<p>`；不解析行内 Markdown。
- `Composer`：textarea 受控于本地 `useState`，初值 `workspace.draftFor("chat:"+topic)`；`onChange` 写本地并 `setDraft`；`Ctrl/Cmd+Enter` 不发送（未接通），Enter 保留换行；忽略组合输入（`isComposing`/229）不触发任何逻辑。发送按钮 `disabled`，`title`/`aria-label` 说明未接通。
- `IconButton` 统一 `type="button"`、`className="icon-btn"`、`aria-label`、`title`、可选 `aria-expanded`/`aria-controls`；`disabled` 时保留可访问名。
- 复制：优先 `navigator.clipboard.writeText`，否则创建临时 textarea + `document.execCommand("copy")`；结果写到 `role="status"` 文本；成功/失败文案来自文案表，3 秒后清除。
- `BranchPanel`：`panelOpen` 由仓库控制；面板关闭时 `hidden`（CSS 用 `[hidden]` 保证生效），遮罩只在打开时挂载；打开时 effect 聚焦关闭按钮并监听 Escape，关闭时聚焦 `#branch-toggle`；覆盖/并排只由 CSS 媒体查询决定，组件不读窗口宽度。
- `TreeOverlay`：`role="dialog" aria-modal="true" aria-labelledby`；打开时聚焦关闭；Escape 关闭；缩放按钮更新本地 state，百分比 `role="status"`；根节点是 `<div>`，不是伪造的可点分支。
- `AppShell`：窄窗挂载/resize 时把 `navCollapsed` 置真；展开时渲染 `.nav-scrim`（仅窄窗可点，宽窗 `display:none`）；面板/树都关闭时按 Esc 也会收起窄窗侧栏。
- 文案键（实际新增）：`chat_branch_title/open/close/root/current/children_none/children_known/unavailable/create`、`chat_summary`、`chat_topic_summary`、`chat_copy/copied/copy_failed`、`chat_action_reset/fork/unavailable`、`chat_send/attach/composer_note/composer_label/composer_placeholder`、`chat_no_chat_baseline/chat_no_chat_body`、`chat_suggestions`、`chat_tree_open/close/only_root`、`tree_zoom_in/out/fit`、`common_close`。所有新增可见文案进 `src/copy`。
- 主题 1 的 ChatPage 不渲染 `ChatAnswer`；渲染摘要卡与 `chat_no_chat_body`，输入区仍可写草稿但发送/复制/新建分支禁用。

## Verification report

- Verdict: PASS
- Evidence: `pnpm --dir frontend test -- test/pages/ChatPage.test.tsx` 退出码 0（14 文件/90 条）；`pnpm --dir frontend lint` 退出码 0；无头 Edge 1440×900/390×844 实测分支面板、知识树、Esc 还焦、中文草稿返回恢复、窄窗侧栏与面板遮罩（截图暂存 `artifacts/tmp/probe-043/`，正式归档在 046）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 旧 `chat_nav`/`chat_overview` 路由断言改为面板/覆盖层断言；旧 `act_copy` 不是 button 的断言改为「复制是 button 且可用，其余动作 disabled」；旧 `.chat-answer-run` 断言改为语义块断言，正文逐字覆盖仍在。
- 046 正式证据补记（2026-10-21）：`artifacts/evidence/web-workspace-implementation-2026-10-21/` 的 22 项读数覆盖本片：正文独立滚动（滚到 600 时顶栏/输入区坐标不变）、复制写入剪贴板（2156 字逐字比对）、中文 IME composition 事件与草稿、宽窗面板并排 320 px、窄窗覆盖 340 px 贴右缘、Esc 还焦 `#branch-toggle`、知识树缩放夹 150%/50%/100% 并还焦；截图 `desktop-chat.png`、`desktop-branch-panel.png`、`desktop-tree.png`、`narrow-chat.png`、`narrow-branch-overlay.png`。
- Gaps: 真实发送/建分支/附件未接通；复制在 `file://` 下可能不可用；知识树缩放只作用于根节点视图，真实子节点待采集后才能验证。
- Risks: 覆盖层焦点管理在浏览器与 jsdom 的差异已用真实浏览器补证；窄窗侧栏靠 `resize` 自动收起，触摸用户依赖遮罩关闭；长回答滚动恢复目前按主题 key 记录，跨分支的独立位置要等分支内容可读后再补。

## Handoff notes

- Completed: 对话阅读列（独立滚动＋固定输入区）、完整回答结构、三条建议填草稿、复制成功/失败、未接通动作禁用、分支面板（宽并排/窄覆盖、Esc/遮罩、还焦）、知识树（真实根节点、50–150% 缩放、Esc 还焦）、主题 1 空态、中文草稿跨视图恢复、窄窗侧栏自动收起与遮罩。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 片 044 接入主页与学习库，复用 042 的查询/筛选/展开/滚动状态；046 再用 1440×900/390×844 归档正式截图并修订规格。
- Watch-outs: ① `IconButton` 的禁用态必须保留 `aria-label`/`title` 里的「尚未接通」；② 面板与树不能同时打开（仓库已互斥）；③ 窄窗下不要再用「展开的侧栏」占内容宽度；④ 主题 1 与知识树不得补造夹具里没有的节点。
