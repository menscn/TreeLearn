# Filename: T-2026-10-25-selection-toolbar-082.md
# Codex Harness Task: 选中文本六格玻璃浮条

id: T-2026-10-25-082
title: 选中文本六格玻璃浮条
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-reading-glass-parity-requirement-080.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 243
shared_code_touched: true
depends_on: [T-2026-10-25-reading-glass-shell-081.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 用户选正文时没有任何浮条；本卡交付与 Android 同序的六格图标浮条，接线在 083。
- Problem/background: 前端没有任何选区监听或文本菜单组件；Android 的 `popup_word_action.xml` 已有六格（问 AI / 新建主题 / 高亮 / 批注 / 复制 / 全选），图标注解与顺序可直接复用。
- Users/scenarios: 鼠标拖选或键盘选中阅读正文；窄窗下浮条需要夹在视口内；选区靠顶部时翻到下方。
- Expected outcome: 浮条以玻璃控制面呈现，六格 44×44、1px 分隔、仅图标；问 AI / 复制 / 全选可用，新建主题 / 高亮 / 批注禁用并说明「尚未接通」；浮条在选区上方 8px，上方空间不足时翻到下方，左右夹取不越界。
- Core flow and reversibility: 本卡只交付可复用的浮条组件与定位函数；回退为删除组件、图标与样式，不影响页面。
- Inputs/outputs: 输入是选区文本与 `DOMRect`；输出是工具栏 DOM 与三个回调。无持久化。
- State and exception handling: `selection === null` 时不渲染；禁用格不触发回调；按下浮条时 `preventDefault` 防止选区塌陷；Esc 由宿主关闭。
- Constraints and trade-offs: 图标不配文字；可访问名来自 i18n；不许新增颜色字面量；44×44 热区；玻璃面用 `--glass-control`，保证压在正文上仍读得清。
- AI recommendation and rationale: 先把浮条做成纯展示组件并单独测定位，避免把选区生命周期塞进组件；宿主接线放在 083。
- User-confirmation decisions: 结构与六格顺序来自已确认评审稿与 Android。
- AI-autonomous decisions: 浮条固定定位；宽 6×44 + 分隔 + 8px 内边距；上下翻转与左右夹取阈值 8px；三项未接通能力保持禁用。
- Acceptance evidence: 组件测试先红后绿，覆盖六格顺序、禁用语义、回调、定位翻转与夹取。
- Facts: Android 文案 `chat_word_*_desc` 在 `strings.xml`；图标为 `ic_sparkle`、`ic_action_fork`、`ic_action_highlight`、`ic_action_note`、`ic_action_copy`、`ic_action_select_all`。
- Assumptions: 浮条以 `position: fixed` 渲染在视口坐标上，不被滚动容器裁切。
- Unknowns: 无。
- Risks: 浮条宽度在 320px 视口下可能贴边；玻璃面与控制面对比不足。
- Deferred: 更多子菜单与触屏长按；真实高亮/批注/新建主题。
- Non-goals: 不改 Android；不实现选区监听（083）；不做发送。

## Goal

交付一个六格、图标-only、玻璃控制面的选中文本浮条，并用纯函数钉住「上方优先、放不下翻转、左右夹取」的定位规则。

## Scope

### In scope
- 新建 `frontend/src/components/SelectionToolbar.tsx` 与 `menuPosition` 纯函数。
- `frontend/src/components/Icon.tsx` 增加 3 枚 Android 同源字形（高亮 / 批注 / 全选）。
- `frontend/src/copy/zh-CN.ts` 增加六格可访问名与浮条名。
- `frontend/src/styles/workspace.css` 增加浮条样式。
- `frontend/test/components/SelectionToolbar.test.tsx`。

### Out of scope
- ChatPage 选区监听、复制 / 全选 / 问 AI 接线、引用条与 chips（083）。
- 桌面打包（084）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增一个不挂载时零副作用的组件；既有页面与交互不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 组件消费者将在 `pages/ChatPage.tsx`（083）；图标映射 `components/Icon.tsx`；文案表 `copy/zh-CN.ts`；样式 `styles/workspace.css`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 本卡组件尚未接入页面，业务行为中性；定位函数由单测覆盖。

## Documentation impact record

- Changed runtime facts: 新增选中文本浮条组件与三枚图标字形。
- Authoritative code/config/migration evidence: `frontend/src/components/SelectionToolbar.tsx`（浮条渲染与再导出）、`frontend/src/utils/selection.ts`（`TOOLBAR_SIZE` / `menuPosition`，为守依赖方向从组件下沉）、`frontend/src/components/Icon.tsx:205` 起三枚字形、`frontend/src/styles/workspace.css:454` 起浮条样式。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`。
- Documentation files updated: 与 081/083 同批追加一条 2026-10-25 记录。
- Producer/consumer/sibling/compatibility paths checked: Android `popup_word_action.xml`、`strings.xml`、六枚 drawable。
- Superseded phrases/examples reverse-searched: 无（新增面）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改历史文档。

## Acceptance criteria

- [x] 六个按钮按问 AI / 新建主题 / 高亮 / 批注 / 复制 / 全选排列，均为 `button`，热区 44×44，可访问名来自 i18n。
- [x] 新建主题 / 高亮 / 批注为禁用态，可访问名含「尚未接通」；问 AI / 复制 / 全选触发对应回调并携带选区文本。
- [x] `menuPosition`：默认在选区上方 8px；上方空间不足翻到下方；左右夹在视口内。
- [x] 按下浮条不改变浏览器选区（`mousedown` 被阻止默认行为）。
- [x] `pnpm --dir frontend exec vitest run test/components/SelectionToolbar.test.tsx` 与 `pnpm --dir frontend lint` 全绿。

## TDD execution record

### RED — failing test first
- Test/scenario: `frontend/test/components/SelectionToolbar.test.tsx` 11 条：六格顺序/图标、可访问名与禁用、回调携带文本、`mousedown` 阻止默认、空选区不渲染、定位四例、44×44 与分隔线、token-only。
- Command: `pnpm --dir frontend exec vitest run test/components/SelectionToolbar.test.tsx`
- Exit code: 1
- Observed failure: 模块不存在，Vite 在 `import ... from "../../src/components/SelectionToolbar"` 处失败，1 file failed / no tests。
- Failure reason validated: 组件、图标、文案、样式均未落地，属于功能缺失而非测试写法问题。
- Evidence: `artifacts/evidence/web-reading-glass-2026-10-25/082-red.txt`

### GREEN — minimum implementation
- Implementation: 新建 `frontend/src/components/SelectionToolbar.tsx`（`TOOLBAR_SIZE`、`menuPosition`、六格渲染、禁用语义、`mousedown` 阻止默认）；`frontend/src/components/Icon.tsx:25` 新增三枚字形、`:205` / `:212` / `:218` 按 Android drawable 落 pathData；`frontend/src/copy/zh-CN.ts:52` 起新增浮条与六格可访问名（逐字对齐 `strings.xml`）；`frontend/src/styles/workspace.css:454` 起新增玻璃控制面、44px 热区、1×20 分隔与禁用态。
- Command: `pnpm --dir frontend exec vitest run test/components/SelectionToolbar.test.tsx`
- Exit code: 0
- Passing evidence: 11 tests passed；随后全量 `vitest run` 26 files / 175 tests passed。
- Regression evidence: `pnpm --dir frontend run lint` EXITCODE=0（依赖方向 42 文件、设备时间、可见文案、夹具同步全绿）。

### REFACTOR — optional
- Cleanup: 测试读取 CSS 改用 `fileURLToPath(new URL(...))`，与既有样式测试一致；无生产代码重构。
- Regression evidence: 全量 26 files / 175 tests passed。

### LOOP — optional
- Command and rounds: 一次循环：`menuPosition` 的「底部夹取」用例原设定在仍有上方空间的选区，改为 300px 矮视口后转绿。
- Failure identity and repeat count: 1 次，测试场景设定错误，非实现缺陷。
- Stop condition hit: 是。
- Handoff: 组件尚未挂载，081 外壳已完成；083 负责选区与回调接线。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 图标 pathData 逐条取自 Android drawable，不按看起来像另画。
- 浮条自身带 `role="toolbar"` 与可访问名；按钮用 `data-word-action` 便于宿主与测试定位。
- 样式只用 tokens；玻璃面与控制面沿用 `--glass-control`、`--glass-edge`、两档投影。

## Verification report

- Verdict: PASS（独立复核通过，2026-10-25）
- Evidence: `artifacts/evidence/web-reading-glass-2026-10-25/082-red.txt`、`082-green.txt`；测试文件 11 例。
- Changed/deleted assertion rationale and equivalent behavior coverage: 新增测试，无删除断言；定位用例覆盖上方优先、翻转、左右夹取、底部夹取四例。
- Gaps: 组件尚未挂载，视觉与真实选区行为由 083 浏览器实测补齐；极窄视口（<300px）浮条宽度大于视口时只能靠左夹取，未单测。
- Risks: 内联 `TOOLBAR_SIZE` 与 CSS 的 44/1/4 几何若分头漂移会让定位偏差；测试用 CSS 规则钉住了格子与分隔尺寸。

## Handoff notes

- Completed: 组件、定位函数、三枚图标、七条文案与浮条样式；11 例与最终全量 180 例绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 083 接入 ChatPage：选区监听、复制/全选/问 AI、引用条与追问标签。
- Watch-outs: 不要把选区读取逻辑放进展示组件；不要让禁用格仍可聚焦触发；浮条坐标来自宿主传入的视口矩形。
