# Filename: T-2026-10-25-reading-glass-parity-requirement-080.md
# Codex Harness Task: 阅读页玻璃对齐 Android Scheme A 与选中文本浮条

id: T-2026-10-25-080
title: 阅读页玻璃对齐 Android Scheme A 与选中文本浮条
template_version: 2
task_type: requirement
status: verified
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: harness
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 用户 2026-10-25 指出 Web / Windows exe 阅读页两处问题：① 选中正文时没有文本操作浮条；② 输入框上方有一条横线，要求改成悬浮输入框、用间距而不是分隔线做隔离。同时要求 exe 与 Web 都按 UI 设计加玻璃质感，除输入框外其余区域参考 Android Scheme A。评审稿已产出并获用户「可以，使用 harness 开发」确认。
- Users/scenarios: 在 Windows 桌面与浏览器中使用知芽阅读回答的学习者；鼠标拖选正文后用浮条问 AI / 复制 / 全选；窄窗与键盘操作。
- Expected outcome: 阅读页按 Android Scheme A 结构浮起——胶囊顶栏、悬浮侧栏、玻璃阅读面、悬浮输入坞；输入框上方不再有横线；选中正文后出现六格图标浮条（问 AI / 新建主题 / 高亮 / 批注 / 复制 / 全选），顺序与 Android 一致；问 AI 后在输入坞显示引用条与「为什么 / 是什么 / 怎么用」三个 chip。
- Core flow and reversibility: 拖选正文 → 浮条出现在选区上方（空间不足则翻到下方）→ 复制 / 全选 / 问 AI 立即生效，其余三项显示「尚未接通」；问 AI → 引用条与 chips 出现 → 点 chip 把问题填进草稿并聚焦输入框 → 可清除引用。回退只需撤销 `frontend/**` 的 CSS 与组件改动，不涉及数据迁移。
- Inputs/outputs: 输入是用户选区、现有回答文本与草稿；输出是浮条、引用条、chips、草稿文本与复制结果。不新增持久化状态，不改变夹具与 API。
- State and exception handling: 选区折叠、离开正文、滚动或窗口缩放时浮条收起；剪贴板被拒时给出失败提示；高亮 / 批注 / 新建主题未接通时按钮禁用且可访问名说明原因；不伪造发送行为，发送仍禁用。
- Constraints and trade-offs: 只改 Web 前端与桌面打包，不改 Android / backend；不新增依赖；用户可见文案全部走 i18n 资源；颜色/圆角/阴影只用 tokens；点击热区不小于 44×44；图标格不写文字按钮；玻璃面必须保持正文可读，保留不支持 `backdrop-filter` 与 reduced-transparency 的降级。
- AI recommendation and rationale: 结构照 Android Scheme A 而不是另起一套 Web 布局，能让两端同一套判据验收；浮条沿用 Android 六格顺序，未接通能力按项目既有「未接通」语义禁用，避免在 Web 上出现只有样子没有语义的假动作；引用条与 chips 只填草稿、不触发发送，因为 Web 发送尚未接通。
- User choice and custom additions: 用户选择 Harness 开发；确认「输入框变玻璃、其他地方参考 Android 设计、也要玻璃效果」；确认评审稿方向后要求落地到 exe 与 Web。
- AI-autonomous decisions: 浮条限制在阅读正文（`.chat-reading`）内出现；问 AI 写入草稿而非直接发送；chip 只填问题文本并保留引用条；侧栏与顶栏统一改成悬浮玻璃；答案下方动作改成玻璃圆点；新增文案键沿用 Android `chat_word_*` / `chat_ask_chip_*` 命名；桌面重打包沿用现有 Electron 壳与安装路径。
- User-confirmation decisions: 方向与结构已由评审稿确认；Harness 由用户点名。
- Acceptance evidence: 组件 / 样式测试先红后绿；`pnpm --dir frontend lint`、`test`、`build` 全绿；真实浏览器交互截图证明浮条、引用、chips、无横线；重建 `dist-offline` 与 Windows 便携 exe 并真实启动截图。
- Facts: 当前 `.chat-dock` 的横线来自 `glass.css` 的 `box-shadow: 0 -1px 0 var(--glass-line)`；`glass.css` 已给阅读面 / 输入框 / 侧栏 / 顶栏真实模糊；前端没有任何选区监听或浮条实现；Android 六格顺序与文案在 `popup_word_action.xml` 与 `strings.xml`；Web 发送、附件、重置、新建分支当前禁用。
- Assumptions: Chromium / Electron 的 `backdrop-filter` 在浮动容器上可正常采样页面背景；鼠标选区在按钮按下时可通过 `preventDefault` 保留。
- Unknowns: 无会改变范围或验收的未知项。
- Risks: 顶栏由满宽改成胶囊后，滚动留白必须同步，否则内容会被压住或露出空带；浮条定位在窄窗与选区靠顶时容易越界；玻璃叠玻璃可能降低正文对比；引用条出现会改变输入坞高度，需确认不遮挡正文。
- Deferred: Web 端持久化高亮与批注、真实发送、新建主题；触屏长按菜单（Web 仍以浏览器选区为入口）。
- Non-goals: 不改 Android Scheme A 现状、不改后端、不新增高亮/批注存储、不引入暗色主题或新依赖、不自动提交或推送。
- Skipped non-critical questions and reason: 浮条中三项未接通能力的禁用样式沿用项目既有约定，不单独询问；chip 是否直接发送由「发送尚未接通」这一现存事实直接决定。

## Goal

在不改 Android 与后端的前提下，把 Web / exe 阅读页的结构与玻璃材质对齐 Android Scheme A，并交付可用的选中文本浮条、引用条与 ask chips。

## Scope

### In scope
- `frontend/**` 阅读页外壳、顶栏、侧栏、阅读面、输入坞、答案动作的玻璃与布局。
- 选中文本浮条、复制 / 全选 / 问 AI、引用条与三个 ask chips。
- 前端文案、图标、测试、浏览器与桌面启动证据。

### Out of scope
- Android 布局与资源、后端、持久化高亮/批注、真实发送、自动更新与签名。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增悬浮结构与文本浮条；必须保留草稿、滚动位置、主题切换、分支面板、知识树、人设弹层、复制整条回答、窄窗抽屉与两条玻璃降级路径。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 样式生产者 `frontend/src/styles/*.css`；组件消费者 `layouts/AppShell.tsx`、`pages/ChatPage.tsx`、`components/Composer.tsx`；文案生产者 `copy/zh-CN.ts`；桌面消费者 `desktop/` 的离线产物。
- Native bridge or independent-client compatibility/update requirements: 无原生桥接；桌面壳只消费同一份 `dist-offline` 产物，不需要版本协商。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: Web / exe 阅读页改成悬浮玻璃结构；新增选中文本浮条、引用条与 ask chips；输入坞横线移除；非对话页留白统一为栏高 + 12。
- Authoritative code/config/migration evidence: `frontend/src/styles/workspace.css`、`pages.css`、`forest.css`、`glass.css`；`frontend/src/utils/selection.ts`、`components/SelectionToolbar.tsx`、`hooks/useTextSelection.ts`、`pages/ChatPage.tsx`、`components/Composer.tsx`、`components/Icon.tsx`、`copy/zh-CN.ts`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`desktop/README.md`、根 `README.md`。
- Documentation files updated: `Docs/04-ux-design/design-system/README.md` 新增 2026-10-25 当前态变更记录（外壳、浮条、引用/chips、证据与未改动项）；`desktop/README.md` 的图标章节属于同日直接执行，不是本需求产物。
- Producer/consumer/sibling/compatibility paths checked: `layouts/AppShell.tsx`、`pages/ChatPage.tsx`、`components/Composer.tsx`；桌面 `desktop/scripts/sync-app.mjs` 与离线产物消费链。
- Superseded phrases/examples reverse-searched: `box-shadow: 0 -1px 0 var(--glass-line)`、`border-top` on `.chat-dock`、`height: 76px`、`padding-top: 76px/68px`。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写 2026-10-21/22 的玻璃改版记录；新增一条 2026-10-25 的当前态变更记录。

## Acceptance criteria

- [x] 阅读页顶栏、侧栏、阅读面、输入坞均呈现悬浮玻璃面，输入框上方不存在横向分隔线；浏览器截图为证。
- [x] 拖选正文出现六格浮条，顺序为问 AI / 新建主题 / 高亮 / 批注 / 复制 / 全选；复制、全选、问 AI 生效，其余三项禁用且可访问名含「尚未接通」。
- [x] 问 AI 后显示引用条与「为什么 / 是什么 / 怎么用」，点 chip 填入草稿并聚焦，引用可清除。
- [x] 既有草稿、滚动恢复、分支面板、知识树、人设、复制回答、窄窗与降级路径回归通过。
- [x] `pnpm --dir frontend lint`、`test`、`build` 全绿；`dist-offline` 重新生成。
- [x] Windows 便携 exe 重新打包并真实启动，窗口截图证明新界面生效。

## TDD execution record

### RED — failing test first
- Test/scenario: 父卡不直接实施；081 外壳 9 条失败、082 组件不存在、083 选区/引用 5 条新增用例先红。
- Command: 见 081–083 的 `pnpm --dir frontend exec vitest run …`
- Exit code: 1（三张子卡各自 RED）
- Observed failure: 081 旧几何与材质；082 模块不存在；083 选中无浮条、无引用/chips。
- Failure reason validated: 失败点逐条对应未实现的源码事实。证据：`081-red.txt`、`082-red.txt`、`083-red.txt`（及 `081-red-forest.txt`）。
- 第二轮：独立复核发现 `.forest-page` 旧栏高，081 补断言 RED 后修复。

### GREEN — minimum implementation
- Implementation: 见子卡 081–084；最终 `frontend/src/styles/{workspace,pages,forest,glass}.css` 与浮条/引用组件落地，`dist-offline` 与便携 exe 重建。
- Command: `pnpm --dir frontend exec vitest run test/styles` / `test/components/SelectionToolbar.test.tsx` / `test/pages/ChatPage.test.tsx`；`pnpm --dir frontend lint` / `test` / `build`
- Exit code: 0
- Passing evidence: 样式 27/27、浮条 11/11、对话页 14/14；全量 26 files / 180 tests；lint 退出码 0；离线产物重建。
- Regression evidence: `083-browser.txt` 6/6 PASS；安装版 `084-app-drive.txt` 17 项 PASS（聊天/浮条/森林）。

### REFACTOR — optional
- Cleanup: 选区几何/类型抽到 `utils/selection.ts`；测试读 CSS 改为 root 相对路径；forest 留白纳入统一断言。
- Regression evidence: 全量 180 例与 lint 0 保持。

### LOOP — optional
- Command and rounds: 1 轮独立复核回修：forest 留白 76/68 → 64/60，重建产物并重打 exe。
- Failure identity and repeat count: `forest-page 与顶栏高度不配套`，1 次。
- Stop condition hit: 安装版森林页 `padTop=64, barH=52`，全链证据重取。
- Handoff: 待 080–084 独立复核后统一翻 `verified`。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 父卡不实施代码。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 本卡为需求父卡，具体改动、测试与证据落在子卡 081–084。
- 六格浮条中高亮 / 批注 / 新建主题在 Web 无生产者，保持禁用而不是做临时假状态。
- 不引入第二个玻璃实现：所有面继续消费 `glass.css` 与 `tokens.css`。

## Verification report

- Verdict: PASS（独立复核通过，2026-10-25）
- Evidence: 081–084 卡面所列 RED/GREEN、浏览器 6/6、安装版 17 项、`084-build.txt` 哈希与截图。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删断言；留白期望从「等于栏高」改为「栏高 + 12」，并新增 forest 同步项。
- Gaps: 082 禁用格点击未单测；083 输入框内选区未单测；`backdrop-filter` 降级靠既有 `@supports` 断言。
- Risks: precommit `line-budget` 972 行判红属整树一次提交的提交形状（用户已拍板），不是单片超预算；exe 未签名。

## Handoff notes

- Completed: 悬浮玻璃外壳、选中浮条、复制/全选/问 AI、引用条与 chips、浏览器与安装版取证。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 080–084 复核通过后做整树提交（用户另行确认）。
- Watch-outs: 留白必须跟栏高同改；浮条不得抢走选区；六格中只有三项接通。
