# Filename: T-2026-10-25-reading-glass-shell-081.md
# Codex Harness Task: 阅读页悬浮玻璃外壳与无横线输入坞

id: T-2026-10-25-081
title: 阅读页悬浮玻璃外壳与无横线输入坞
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-reading-glass-parity-requirement-080.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 191
shared_code_touched: true
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 阅读页对齐 Android Scheme A 的悬浮结构与玻璃材质，去掉输入坞横线；浮条与引用流程在 082/083。
- Problem/background: `.workspace-topbar` 仍是满宽贴边条，`.app-sidebar` 仍贴左边，`.chat-reading` 与 `.chat-dock` 仍以边界线分隔；`glass.css` 还给 `.chat-dock` 画了 `box-shadow: 0 -1px 0 var(--glass-line)`，正是用户点名的那条横线。
- Users/scenarios: Windows 桌面与浏览器宽窗、760px 以下窄窗、`prefers-reduced-transparency` 与不支持 `backdrop-filter` 的降级。
- Expected outcome: 侧栏、胶囊顶栏、阅读面、输入坞四块都读成独立浮起的玻璃面；输入坞上方没有任何横向分隔线；正文从胶囊下方经过；答案动作改成玻璃圆点。
- Core flow and reversibility: 纯 CSS 结构与材质改动，回退为恢复 `workspace.css` / `pages.css` / `glass.css` 三处规则。
- Inputs/outputs: 输入为现有 DOM 与 tokens；输出为悬浮布局、滚动留白与新测试断言。
- State and exception handling: 不改变 store、草稿、滚动恢复、焦点顺序；窄窗抽屉与遮罩层级保持；两条降级继续生效。
- Constraints and trade-offs: 只消费 tokens，不新增裸色值或依赖；顶栏高度与滚动留白必须成对；44px 热区不缩；玻璃叠玻璃时正文仍要可读。
- AI recommendation and rationale: 用「外壳 12px 内缩 + 顶栏绝对定位胶囊 + 滚动容器付留白」实现 Android 的浮起关系，改动集中且不碰业务组件。
- User-confirmation decisions: 评审稿方向已确认；输入框玻璃、其他区域参考 Android。
- AI-autonomous decisions: 顶栏 52px、四边 12px 悬浮、胶囊圆角；外壳 12px 内缩；输入坞左右 12px、底 12px、圆角 `--r-2xl`；答案动作 44px 玻璃圆点；宽窗留白 64px、窄窗 60px（栏高 + 12px 间距，`.page-scroll` 与 `.forest-page` 同规则）。
- Acceptance evidence: 新样式测试先红后绿；既有玻璃/列宽测试更新后绿；浏览器截图与溢出实测。
- Facts: 横线在 `frontend/src/styles/glass.css` 的 `.chat-dock` 块；顶栏覆盖与留白规则在 `workspace.css` 与 `pages.css`；`glass.css` 已为 `.app-sidebar`、`.workspace-topbar`、`.chat-reading`、`.composer` 提供模糊与投影。
- Assumptions: 胶囊顶栏内部 44px 按钮在 52px 高度内可放下；侧栏圆角不会裁掉窄窗抽屉内容。
- Unknowns: 无会改变范围的未知项。
- Risks: 留白不同步会让正文被压住或露出空带；窄窗胶囊内工具按钮可能拥挤；侧栏圆角与滚动条可能相互影响。
- Deferred: 分支面板与知识树的独立浮层化；Web 高亮与批注。
- Non-goals: 不改组件业务逻辑、不改 Android、不新增 token。

## Goal

把阅读页的侧栏、顶栏、阅读面与输入坞改成悬浮玻璃结构，并彻底移除输入坞上方的横向分隔线，同时保持全部既有交互与降级路径。

## Scope

### In scope
- `frontend/src/styles/workspace.css`：外壳内缩、侧栏/顶栏/阅读面/输入坞/答案动作的几何与圆角、窄窗覆盖。
- `frontend/src/styles/pages.css`：非对话页滚动留白与顶栏高度同步。
- `frontend/src/styles/glass.css`：删掉 `.chat-dock` 的 `box-shadow` 横线，补输入坞与答案动作的玻璃面声明。
- `frontend/test/styles/reading-shell.test.ts` 新建；`glass-depth.test.ts` 的栏高/留白断言同步。
- 浏览器截图与溢出、降级实测。

### Out of scope
- 选中浮条、引用条与 chips（082/083）；业务组件与 store；Android；桌面打包（084）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 改变顶栏与侧栏的定位观感、输入坞分隔方式；保留草稿、滚动恢复、主题切换、面板、知识树、人设、复制整条回答、焦点顺序、窄窗抽屉与两条降级。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `.workspace-topbar` 规则被 `layouts/AppShell.tsx` 直接消费；`.chat-scroll` / `.page-scroll` 的留白由 `ChatPage` 与各页面消费；`.chat-dock` / `.composer` 由 `ChatPage` 与 `Composer` 消费；`.chat-answer-actions` 由 `ChatPage` 消费。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 阅读页外壳改成悬浮玻璃；输入坞横线移除；答案动作改为玻璃圆点。
- Authoritative code/config/migration evidence: `frontend/src/styles/workspace.css:11`（外壳内缩）、`:24`（侧栏玻璃）、`:186`（胶囊顶栏）、`:297`（聊天留白）、`:569`（输入坞）、`:576`（输入框）、`:430`（答案动作）；`pages.css:11`、`forest.css:102/:494`（非对话页留白）；`glass.css:123`（输入坞去横线）。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`desktop/README.md`。
- Documentation files updated: `Docs/04-ux-design/design-system/README.md` 追加 2026-10-25 当前态记录（与 082/083 同批一条）。
- Producer/consumer/sibling/compatibility paths checked: `layouts/AppShell.tsx`、`pages/ChatPage.tsx`、`components/Composer.tsx`、`pages/HomePage.tsx`、`pages/LibraryPage.tsx`。
- Superseded phrases/examples reverse-searched: `box-shadow: 0 -1px 0 var(--glass-line)`、`border-top: 1px solid var(--hairline)`、`height: 76px`。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改历史玻璃改版记录。

## Acceptance criteria

- [x] 新测试 `reading-shell.test.ts` 断言：外壳内缩；侧栏圆角玻璃；顶栏是 52px 胶囊且无下边框；宽窗/窄窗留白等于「上缘 + 栏高 + 间距」；`.chat-dock` 无 `box-shadow`/`border-top`；输入坞左右与底部内缩；答案动作是玻璃圆点。
- [x] `glass-depth.test.ts` 的栏高与留白断言同步到新数值并继续绿。
- [x] 浏览器实测：顶栏、侧栏、阅读面、输入坞四块浮起，输入框上方无横线；正文滚动经过胶囊下方；无横向溢出。
- [x] 760px 以下抽屉、遮罩、两条玻璃降级仍生效。
- [x] `pnpm --dir frontend exec vitest run test/styles`、`pnpm --dir frontend lint` 全绿。
- [x] 独立复核补漏：`.forest-page` 的宽/窄窗留白同步为 64/60，并由 `reading-shell.test.ts` 按「栏高 + 12」钉住；安装版森林页实测 `padTop=64, barH=52`。

## TDD execution record

### RED — failing test first
- Test/scenario: `frontend/test/styles/reading-shell.test.ts` 7 条新断言 + `glass-depth.test.ts` 两条留白断言改为「栏高 + 12」。
- Command: `pnpm --dir frontend exec vitest run test/styles`
- Exit code: 1
- Observed failure: 9 failed / 15 passed：外壳无 `--s3` 内缩、顶栏 76px 且带下边框、`.chat-scroll` / `.page-scroll` 留白 76/64、`.chat-dock` 仍有 `border-top` 与玻璃层 `box-shadow`、`.composer` 仍 `--r-lg` 实色、答案动作仍有 `border-top`、窄窗 `.app-workspace` 覆盖缺失。
- Failure reason validated: 失败点逐条对应 `workspace.css` / `pages.css` / `glass.css` 的旧几何与材质，不是测试写法问题。
- Evidence: `artifacts/evidence/web-reading-glass-2026-10-25/081-red.txt`
- 第二轮（独立复核发现遗漏）：`.forest-page` 仍是旧栏高 76/68，新测试纳入 forest 后 RED：`1 failed | 26 passed`，失败点为 `forest-page 与顶栏高度不配套`。证据 `081-red-forest.txt`。

### GREEN — minimum implementation
- Implementation: `frontend/src/styles/workspace.css:11` 外壳 `--s3` 内缩与间距；`:24` 侧栏圆角去右边；`:186` 顶栏 52px 胶囊、绝对定位、无下边框；`:297` `.chat-scroll` 留白 64；`:430` 答案动作去顶线、`:437` 44px 玻璃圆点；`:569` 输入坞去顶线并左右/底内缩；`:576` / `:586` 输入框圆角与内边距；`:1221` 窄窗 48px 栏、`:1227` 窄窗留白 60px。`frontend/src/styles/pages.css:11` 与 `:814` 同步非对话页留白。`frontend/src/styles/glass.css:123` 删除 `.chat-dock` 的 `box-shadow` 横线。
- Command: `pnpm --dir frontend exec vitest run test/styles`
- Exit code: 0
- Passing evidence: 目标命令 5 files / 27 tests passed；第二轮补 forest 后同命令 27/27 通过（`081-green-forest.txt`）；另跑全量 `vitest run` 26 files / 180 tests passed（含 082/083）。`pnpm --dir frontend run lint` EXITCODE=0（依赖方向、设备时间、可见文案、夹具同步全绿）。
- Regression evidence: 既有玻璃纵深、列宽、聊天/首页/学习库/设置页测试全绿；浏览器截图见 `artifacts/evidence/web-reading-glass-2026-10-25/081-visual-probe.txt`；安装版森林页 `084-app-drive.txt`。

### REFACTOR — optional
- Cleanup: 无额外重构；改动保持在三份样式表。
- Regression evidence: 同 GREEN 全量 180 例。

### LOOP — optional
- Command and rounds: 独立 verifier 复核发现 forest 留白遗漏 → 补断言得 RED → 改 `.forest.css` 得 GREEN，共 1 轮。
- Failure identity and repeat count: `forest-page 与顶栏高度不配套`，1 次。
- Stop condition hit: 目标命令 27/27 通过，安装版森林页实测 64/52 对齐。
- Handoff: 浮条与交互交给 082/083；留白规则统一由测试守住。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 只改材质与几何；不改 DOM 顺序与业务状态。
- 顶栏留白数值在 `workspace.css`、`pages.css` 与 `forest.css` 各一处，测试按「栏高 + 12」比较，不允许各写一个字面量后漂移。
- 玻璃面一律引用 tokens，答案动作圆点用 `--glass-card`，不用新色值。

## Verification report

- Verdict: PASS（独立复核通过，含 forest 回修，2026-10-25）
- Evidence: `artifacts/evidence/web-reading-glass-2026-10-25/081-red.txt`、`081-green.txt`、`081-visual-probe.txt` 与六张截图（1440×900 / 900×620 / 720×900 含抽屉、首页、学习库）；forest 轮次 `081-red-forest.txt` / `081-green-forest.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: `glass-depth.test.ts` 只把留白期望从「等于栏高」改为「栏高 + 12」，覆盖范围不变；新增 `reading-shell.test.ts` 钉住外壳内缩、胶囊、输入坞无横线与圆点热区，复核后把 `.forest-page` 纳入同一留白断言。
- Gaps: 不支持 `backdrop-filter` 的降级无法在本机浏览器模拟，依赖既有 `@supports` 断言；760px 断点恰好边界未单测。
- Risks: 窄窗胶囊内工具按钮在 720 以下中文标题会省略；侧栏圆角在极短窗口下仍靠滚动消化。

## Handoff notes

- Completed: 外壳/玻璃 081 全部验收项与证据。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 082 选中浮条组件与定位函数。
- Watch-outs: 留白与栏高必须同改；不要为视觉把顶栏改回满宽贴边；082 的浮条坐标必须夹在视口内。
