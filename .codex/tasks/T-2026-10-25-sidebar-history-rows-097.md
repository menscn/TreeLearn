# Filename: T-2026-10-25-sidebar-history-rows-097.md
# Codex Harness Task: 侧栏历史行把标题、时间与箭头收进一行，摘要在下一行（WH1 + WH3）

id: T-2026-10-25-097
title: 侧栏历史行的一列三种性质（WH1）与视线落点（WH3）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: web-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 90
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 WH1（重要）——侧栏一列混了路由、路由卡、历史内容三种性质；WH3（优化）——历史行视线落点与操作落点分家。
- Problem/background: 「最近学习」与首页／学习库／设置共用一块 hover 底色，读起来像第四个同级入口；历史行内部标题在上一行、`N 个分支 · 3天前` 在下一行、箭头又只在点进去之后才出现，眼睛找标题、手找落点，中间隔着一行正文密度的灰字。
- Users/scenarios: 从侧栏回到正在读的主题的人。
- Expected outcome: 历史行 hover 只提字色；标题、meta 与箭头排在同一行（meta 与箭头不参与压缩）；摘要另起一行并收 1 行。
- Core flow and reversibility: 只改 `AppShell.tsx` 的历史行结构与 `workspace.css` 的三条规则。回退＝恢复 `display:block` 与两行结构；无状态改动。
- Inputs/outputs: 输入是评审板 WH1／WH3 原文；输出是 `.recent-head`（flex 行）、`.recent-meta`／`.recent-chevron`（`flex:none`）、`.recent-summary`（`-webkit-line-clamp:1`）。
- State and exception handling: 没有摘要的主题不渲染 `.recent-summary`，行高自然回落；标题超长只截标题，meta 与箭头不被挤走。
- Constraints and trade-offs: 历史行仍是整块可点按钮；箭头是装饰性方向提示，不新增独立可点区域，也不进可访问名。
- AI recommendation and rationale: 把「回到哪儿」与「去哪儿」用不同的 hover 语言分开，比加一条分隔线更轻，也不会把侧栏切碎。
- User-confirmation decisions: 22 条全做。
- AI-autonomous decisions: ① hover 用 `color: var(--ink)` 提字色而不是加左侧色条；② 箭头 14px、`opacity:.7`、颜色 ink3，属于「可去」的提示；③ 摘要用 `-webkit-line-clamp:1` 而不是 `max-height`，保证单行省略号与行高一致。
- Acceptance evidence: `recent-row.test.ts`（3 条 CSS 判据）+ `AppShell.test.ts`（历史行结构判据），先红后绿。
- Facts: `AppShell.tsx:141`（`.recent-head`）、`:147`（条件摘要）；`workspace.css:103`（hover 只提字色）、`:154`（head flex）、`:169`（meta `flex:none`）、`:176`（chevron `flex:none`）、`:184`（摘要 clamp 1）；测试 `AppShell.test.ts:91`、`recent-row.test.ts`（新文件）。
- Assumptions: 历史行的数据形状（`topic.summary` 可缺省）已存在；本片不造字段。
- Unknowns: 无。
- Risks: 窄侧栏（抽屉宽度）下标题可用宽度更小；省略号会更早出现，这是预期行为。
- Deferred: 历史行的多选／删除不做——侧栏不是管理面。
- Non-goals: 不改路由项与森林入口卡的视觉；不改侧栏折叠行为。
- Skipped non-critical questions and reason: 未问「箭头要不要可点」——它指向整行的点击区，单独再做一个点只会制造两个落点。

## Goal

让侧栏历史行在视觉上与路由分开，并把标题、时间与箭头收进同一视线行。

## Scope

### In scope
- `frontend/src/layouts/AppShell.tsx` 的最近学习行结构。
- `frontend/src/styles/workspace.css` 的 `.recent-link`／`.recent-head`／`.recent-meta`／`.recent-chevron`／`.recent-summary`。
- `frontend/test/styles/recent-row.test.ts`（新）与 `frontend/test/shell/AppShell.test.ts` 的结构判据。

### Out of scope
- 侧栏路由项、森林入口卡、折叠状态；其它页面的列表行。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 历史行从两行块级布局变为「一行头 + 一行摘要」。必须保留：整行点击进入主题、`aria-current`、折叠时侧栏整体隐藏、窄窗抽屉的遮罩与焦点行为、`.side-link:hover` 的绿色底不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `AppShell.tsx:130-151`；消费者 `workspace.css` 的 `.recent-*` 规则；同一 DOM 在窄窗抽屉与宽窗固定列复用，无平台分支。
- Native bridge or independent-client compatibility/update requirements: 无；Web 与桌面壳共用同一份构建。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 侧栏历史行有了自己的 hover 语言与单行头结构；摘要限 1 行。
- Authoritative code/config/migration evidence: `AppShell.tsx:141-149`；`workspace.css:103`、`:154`、`:169`、`:176`、`:184`。
- Documentation surfaces checked: `.codex/rules/frontend.md`、`.codex/rules/ux-design.md`（无 token 新增）；`frontend/README.md`（不述侧栏行结构）。
- Documentation files updated: 无需改文档；理由写在 CSS 与测试注释里。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "recent-link\|recent-meta\|recent-chevron" frontend/src` 只命中 AppShell 与 workspace.css。
- Superseded phrases/examples reverse-searched: 旧注释「历史行与路由共用 hover 底色」已删除并替换为本片理由。
- `documentation impact: none` reason and evidence: 纯版式与结构改动，不改契约、路由或数据形状。
- Historical/PRD handling: 不改写 074「最近学习列表」的历史记录。

## Acceptance criteria

- [x] `.recent-link:hover` 只写 `color`，不写 `background`；`.side-link:hover` 仍保留 `--green-faint` 底。
- [x] `.recent-head` 是 flex 行，`.recent-title` 单行省略，`.recent-meta` 与 `.recent-chevron` 均 `flex:none`。
- [x] `.recent-summary` 收 1 行（`-webkit-line-clamp:1`）且用 `--fs-cap`。
- [x] DOM 里 `.recent-head` 是历史行的直接子节点，含标题、meta 与箭头。

## TDD execution record

### RED — failing test first
- Test/scenario: 三条 CSS 判据（hover 无底色／头行 flex 与不压缩／摘要 1 行）加一条 DOM 结构判据（`.recent-head` 是直接子节点且含三件）。
- Command: `cd frontend && npx vitest run test/shell/AppShell.test.ts test/styles/recent-row.test.ts`
- Exit code: 1
- Observed failure: `Tests 4 failed | 7 passed (11)`——`AssertionError: .recent-link:hover 必须存在`、`expected '' to match /display:\s*flex/`、`expected '' to match /-webkit-line-clamp:\s*1/`、`标题行是 recent-head: expected null not to be null`（`097-red.txt`）。
- Failure reason validated: 四条红分别对应四条新写的判据；把工作树前的 `workspace.css`／`AppShell.tsx` 读回来即全部消失，说明红来自缺规则与缺节点，不是测试自身的解析错误（同文件其余 7 条旧判据在红态即为绿）。

### GREEN — minimum implementation
- Implementation: `AppShell.tsx` 历史行改为 `.recent-head`（title／meta／chevron）+ 条件 `.recent-summary`；`workspace.css` 拆分 hover、头行与摘要规则。
- Command: `cd frontend && npx vitest run test/shell/AppShell.test.ts test/styles/recent-row.test.ts`
- Exit code: 0
- Passing evidence: `Tests 11 passed (11)`、`EXITCODE=0`（`097-green.txt`）。
- Regression evidence: 前端全量 30 文件 / 210 用例绿（当次整跑）；`AppShell.test.ts` 其余 7 条（抽屉、面包屑、折叠、图标可访问名）保持绿。

### REFACTOR — optional
- Cleanup: 把历史行的三种文字统一到一个 `.recent-head` 容器里，删掉旧的 `.recent-link small` 规则与 `margin-top`。
- Regression evidence: 重跑同一条命令，11 条保持绿。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿。
- Failure identity and repeat count: 四条新判据各红一次，无重复。
- Stop condition hit: 11 条全绿。
- Handoff: 105 的整跑包含本片。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 路由是「去哪儿」，历史是「回到哪儿」：两者用同一个 hover 底色，会把历史行读成第四个入口。只提字色是最小的区分，不引入新的颜色层级。
- 摘要必须另起一行：它与标题不是同一层信息，挤进头行会让标题只剩一半宽度。
- 箭头用 `flex:none` 且 14px：它是方向提示，不是可点目标；给 44dp 会把整行切成两半。

## Verification report

- Verdict: PASS
- Evidence: `097-red.txt`（4 failed / 11）、`097-green.txt`（11 passed / EXITCODE=0）；前端全量 210 用例绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除旧 `.recent-link small` 规则；其「meta 是 cap 字号灰字」的作用由 `.recent-meta` 等价承担并被新判据钉住。
- Gaps: 未做真机／桌面截图；窄侧栏下的省略号位置待 105 的统一截图核验。
- Risks: 标题可用宽度变小后更早省略；如需更宽，应优先收 meta 文案，而不是让 meta 参与压缩。

## Handoff notes

- Completed: 历史行头行结构、hover 区分、摘要单行。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 无。
- Watch-outs: 不要再把 hover 底色加回 `.recent-link`——那正是 WH1 的成因。
