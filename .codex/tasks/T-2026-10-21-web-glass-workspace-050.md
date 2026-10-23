# Filename: T-2026-10-21-web-glass-workspace-050.md
# Codex Harness Task: Web 工作区晨雾玻璃材质层

id: T-2026-10-21-050
title: Web 工作区晨雾玻璃材质层
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-21-glass-material-requirement-047.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 380
shared_code_touched: true
depends_on: [T-2026-10-21-glass-token-chain-048.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 047 已确认 A 晨雾玻璃 + C 1px 高光边 + 更强通透；本片在 Web 工作区消费 048 的 token。
- Problem/background: Web 外壳与页面仍用不透明 `--bg`／`--surface`：`.app-workspace`、`.app-sidebar`、`.workspace-topbar`、`.chat-dock`、`.composer`、`.branch-panel`、`.tree-dialog`、`.suggestion`、`.search-field`、`.settings-page .field`、`.settings-action` 都是实色，没有场景与高光边。
- Users/scenarios: 桌面阅读、窄窗折叠侧栏、分支面板、知识树弹窗、设置表单；长正文必须保持可读。
- Expected outcome: 新增最后加载的 `glass.css`，在不动 DOM 的前提下给外壳、阅读面、卡片与字段套上玻璃：雾面场景背景＋光斑、半透明 chrome、86% 阅读面、1px 高光边、22/28 圆角；支持 `backdrop-filter` 时真实模糊，不支持或减少透明时回退到更高不透明度表面。
- Core flow and reversibility: `main.tsx` 在 `pages.css` 之后引入 `glass.css` → CSS 变量与选择器覆盖既有 surface → 浏览器截图。回退为删除 `glass.css` 与那一行 import，其余样式自动恢复。
- Inputs/outputs: 输入是 `tokens.css` 的 `glass-*`/`glow-*`/`mist-*`/`r-xl`/`r-2xl`；输出是一个样式文件与一个结构测试，不改组件、文案、状态或夹具。
- State and exception handling: `@supports not (backdrop-filter)` 与 `prefers-reduced-transparency: reduce` 下把玻璃改为更高不透明度；窄窗侧栏与分支面板保留既有覆盖层与 z-index。
- Constraints and trade-offs: 不新增依赖；不出现裸 hex（守门扫描 `frontend/src/**`）；只引用 CSS 变量；不改 DOM 与业务状态；不过度使用 `backdrop-filter`（只给外壳、dock、面板、弹窗、composer 等大面）。
- AI recommendation and rationale: 用独立的最后一层覆盖而不是改 5 个样式文件，避免把材质散落到组件规则里；所有玻璃表面共用同一组变量与同一段 `backdrop-filter` 声明，回退也只有一个分支。
- AI-autonomous decisions: ① 场景放在 `body`（固定渐变＋三处径向光斑＋纸感横格），工作区背景透明；② 侧栏与顶部栏用 chrome，阅读区用 reader，卡片/字段用 card/field；③ 只对 chrome、dock、panel、dialog、composer 启用 blur，小卡片不启用；④ 1px 边用 `box-shadow: inset 0 0 0 1px var(--glass-edge)`，避免 border 改变盒尺寸；⑤ 通过 `color-mix` 与 token 组合出 sheen，不新增裸色值。
- User-confirmation decisions: 见父卡 047。
- Acceptance evidence: Vitest 结构测试 RED/GREEN、`pnpm --dir frontend verify`、`check_design_tokens.py`、桌面与窄窗 Playwright 截图。
- Facts: `main.tsx` 顺序加载 tokens→app→snapshot→workspace→pages；`workspace.css` 里 `.app-workspace` 背景 `var(--bg)`；`.chat-reading` 无卡片；`.composer` 是 `--surface` + `--line`；窄窗侧栏在 `@media (max-width:760px)` 用 `var(--surface)`；`pages.css` 的 `.suggestion`、`.search-field`、`.settings-page .field`、`.settings-action` 都是实色。
- Assumptions: 现代 Chromium 支持 `backdrop-filter` 与 `color-mix`；`prefers-reduced-transparency` 在部分浏览器不识别时不影响默认分支。
- Unknowns: 多层 blur 在低端设备上的滚动帧率；窄窗覆盖侧栏的模糊取样范围。
- Risks: `backdrop-filter` 会创建包含块/合成层，可能影响窄窗覆盖层的定位；`box-shadow` 高光边在圆角处可能出现 1px 断点；阅读面 86% 仍需对比度复核。
- Deferred: 玻璃动效、深色模式、低端设备性能预算。
- Non-goals: 不改组件树、文案、状态、夹具、API；不新增依赖；不改 Android。
- Skipped non-critical questions and reason: 未追问光斑坐标与 sheen 角度；由统一场景与截图复核决定。

## Goal

在 Web 生产工作区以最后一个样式层落地晨雾玻璃材质，使桌面与窄窗都呈现雾面场景、半透明 chrome、独立阅读面与 1px 高光边，同时保持 DOM、状态与文案不变。

## Scope

### In scope
- `frontend/src/styles/glass.css`：场景、chrome、reader、cards、fields、dock、panel、dialog、fallback。
- `frontend/src/main.tsx`：在 `pages.css` 之后引入 `glass.css`。
- `frontend/test/styles/glass.test.ts`：结构测试（读取 CSS 与入口文件，断言变量、选择器、回退与加载顺序）。
- 桌面/窄窗真实浏览器截图（051 汇总）。

### Out of scope
- 组件、页面、store、夹具、文案、依赖。
- Android 材质（049）。
- 暗色主题与动效。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 仅覆盖颜色/背景/阴影/圆角；布局尺寸、断点、z-index、滚动容器、可点区域不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `main.tsx` 是样式加载唯一入口；`workspace.css`/`pages.css` 的既有选择器被后加载的 `glass.css` 覆盖；`AppShell` DOM 不变。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: Web 工作区背景变为雾面场景；外壳/阅读面/卡片/字段为玻璃；不支持 blur 时回退。
- Authoritative code/config/migration evidence: `frontend/src/styles/glass.css`、`main.tsx`、`tokens.css`。
- Documentation surfaces checked: `.codex/rules/frontend.md`、`.codex/rules/ux-design.md`、`Docs/04-ux-design/知芽-TreeLearn-Web学习工作区-交互示意-v1-说明.md`。
- Documentation files updated: 051 汇总时更新提案 README 与 Web 交互示意说明的落地状态；本片只写测试。
- Producer/consumer/sibling/compatibility paths checked: 窄窗侧栏覆盖层、分支面板覆盖层、tree overlay、composer focus。
- Superseded phrases/examples reverse-searched: 「工作区背景 var(--bg)」「composer surface」「侧栏 surface」——由后加载层覆盖，旧声明保留为回退。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 旧样式保留，历史记录不改写。

## Acceptance criteria

- [x] `glass.test.ts`：`glass.css` 只使用 `var(--glass-*|--glow-*|--mist-*|--r-xl|--r-2xl)` 等 token；包含 body 场景、chrome/reader/card/field 选择器、`backdrop-filter`、`@supports` 回退、`prefers-reduced-transparency` 回退、inset 高光边；`main.tsx` 在 `pages.css` 之后引入。
- [x] 测试文件对裸 hex 不做豁免：CSS 内不得出现 `#RRGGBB`。
- [x] `pnpm --dir frontend test -- test/styles/glass.test.ts` RED 非 0、GREEN 为 0，命令一致。
- [x] `pnpm --dir frontend verify` 退出码 0（tsc、lint、test、build、离线包检查）。
- [x] `python -X utf8 .codex/scripts/dev/check_design_tokens.py` 仍为 0（无裸 hex、tokens.css 未手改）。
- [x] 桌面与窄窗截图可见雾面场景、玻璃侧栏/顶栏、阅读面与高光边；窄窗侧栏仍可开合（工具偏差：沿用仓库既有 CDP/Edge 驱动而非 Playwright，见 Verification report）。
- [x] 既有 `AppShell.test.ts`、页面测试全部仍绿。

## TDD execution record

### RED — failing test first
- Test/scenario: `glass.test.ts` 的 4 条结构钉，在 `glass.css` 与入口 import 尚不存在时先跑。
- Command: `pnpm --dir frontend test -- test/styles/glass.test.ts`
- Exit code: 1
- Transcript: `artifacts/evidence/glass-material-2026-10-21/web-red.txt`
- Observed failure: `Test Files 1 failed | 16 passed (17)`、`Tests 4 failed | 107 passed (111)`；前三条均报 `glass.css must exist: expected false to be true`，第四条报 import 索引 `expected -1 to be greater than 642`。
- Failure reason validated: 失败直接指向缺失的样式层与 import，不是选择器或环境问题；落地后同一命令转绿。

### GREEN — minimum implementation
- Implementation: 新增 `frontend/src/styles/glass.css`（body 雾面场景、chrome/reader/card/field、共享 1px 高光边、顶部 sheen、`--glass-line` 分隔线、blur、`@supports not` 与 `prefers-reduced-transparency` 回退、窄窗侧栏玻璃），并在 `pages.css` 之后引入。
- Command: `pnpm --dir frontend test -- test/styles/glass.test.ts`
- Exit code: 0
- Transcript: `artifacts/evidence/glass-material-2026-10-21/web-green.txt`
- Passing evidence: `Test Files 17 passed (17)`、`Tests 111 passed (111)`；`--glass-line` 与 `--glass-sheen` 分别用于 dock 分隔线与玻璃顶部高光，不再有只在清单里存在的 token。
- Regression evidence: 同一轮全量 Vitest 里 `AppShell.test.ts`、主页/学习库/对话页等 16 个既有文件全部保持绿。

### REFACTOR — optional
- Cleanup: 首轮 GREEN 只差 `--glass-line` 未被消费；补上后把 `background` 简写改为 `background-color`，避免简写重置顶部 sheen。
- Regression evidence: 上述 111 例保持全绿。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 加载顺序是契约：`tokens.css` → `app.css` → `snapshot.css` → `workspace.css` → `pages.css` → `glass.css`。
- 场景用 `body` 的 `background-color: var(--mist-base)` + `background-image` 多层渐变；`background-attachment: fixed` 避免滚动时场景移动。
- 每个玻璃面：`background: linear-gradient(...var(--glass-sheen)...)` + `box-shadow: inset 0 0 0 1px var(--glass-edge), var(--glass-shadow)`；只在大面加 `backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate))`。
- 阅读面 `.chat-reading` 使用 `--glass-reader`；正文容器不再额外加边框。
- 回退优先级：`prefers-reduced-transparency` > `@supports`；两者都把面改为 `var(--surface)` 或更高不透明度，不移除高光边。
- 窄窗 `@media (max-width:760px)` 的侧栏覆盖层必须继续使用玻璃，且保持 `position:absolute` 与 z-index。

## Verification report

- Verdict: PASS
- Evidence: 定向 RED `rc=1`（4 红 / 107 绿）/ GREEN `rc=0`（111/111）；`pnpm --dir frontend verify` 16/16、`VERIFY PASS`、`OVERALL_EXITCODE=0`；`check_design_tokens` 与 `--selftest` 均 0；浏览器 8/8 读数（1440×900 与 390×844，Edge CDP）与 8 张截图见 `artifacts/evidence/glass-material-2026-10-21/browser-readings.json`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删改既有断言；新增结构钉只读 `glass.css` 与 `main.tsx`，既有 `AppShell.test.ts` 与页面测试在同一轮全量里 111/111 保持绿。
- Gaps: 本机未安装 Playwright，浏览器取证沿用仓库既有 CDP/Edge 驱动（与既有提案证据同一工具链），截图与读数等价但工具名不同；未在低端设备上测多层 `backdrop-filter` 的滚动帧率；未做正式对比度仪器测量。
- Risks: `backdrop-filter` 会创建合成层，未来若在窄窗覆盖层外层再加 transform，定位可能受影响；`prefers-reduced-transparency` 仍属实验特性，部分浏览器不识别时只能走默认玻璃分支；新增页面若直接写实色 surface 会绕过材质层。
- 050 事后补记（2026-10-22，用户复核反馈）：桌面对话页阅读面与输入区不是同一列（composer 顶到外层可用宽度），用户指出宽度不一致。按用户指令直接修复：`.chat-main` 以 `--chat-column: 784px` 统一 `.chat-reading` 与 `.chat-dock .composer` 的宽度并居中，`.chat-scroll` 加 `scrollbar-gutter: stable both-edges` 抵消滚动条位移，composer 桌面横向 padding 对齐阅读面（窄窗保持 `--s3`）。这是材质层之外的后续布局修订，本卡 Behavior impact 中「布局尺寸不变」只适用于落卡当时；证据见 `artifacts/evidence/web-chat-column-2026-10-22/`（reading 与 composer 同为 left 448 / width 784，正文与输入文字 left 均 480，无横向溢出），复跑 113 例测试与 `verify` 16/16 为绿。

## Handoff notes

- Completed: `glass.css` 材质层、入口加载顺序、结构测试、全量 verify、浏览器读数与截图。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 051 汇总证据。
- Watch-outs: 不要改 DOM；不要在 CSS 写裸 hex；不要让 blur 破坏窄窗覆盖层定位。
