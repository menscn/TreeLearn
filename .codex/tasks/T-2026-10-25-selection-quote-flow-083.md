# Filename: T-2026-10-25-selection-quote-flow-083.md
# Codex Harness Task: 选区接线、引用条与 ask chips

id: T-2026-10-25-083
title: 选区接线、引用条与 ask chips
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-reading-glass-parity-requirement-080.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 247
shared_code_touched: true
depends_on: [T-2026-10-25-selection-toolbar-082.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把 082 的浮条接到真实选区上，并交付引用条与三个 ask chips；发送仍禁用。
- Problem/background: 阅读页没有选区状态；用户拖选后需要浮条、复制、全选与问 AI。Android 在问 AI 后显示引用条和「为什么 / 是什么 / 怎么用」。
- Users/scenarios: 鼠标拖选、键盘选择、点击浮条、点 chip 追问、清除引用、滚动或按 Esc 收起。
- Expected outcome: 选区落在阅读正文内才出现浮条；问 AI 把选中文字变成引用条并聚焦输入框；引用条出现时显示三个 chips，点 chip 把问题填进草稿；复制给出成功/失败反馈；全选把整条回答选中。
- Core flow and reversibility: 选区 → 浮条 → 动作 → 引用/复制/全选；清除引用或换选区即回退。无持久化，刷新即清空。
- Inputs/outputs: 输入是 DOM 选区与回答文本；输出是引用状态、草稿与剪贴板。
- State and exception handling: 选区折叠、离开正文、滚动、窗口缩放、Esc 时收起；剪贴板失败提示；无选区时不渲染浮条；引用只存在组件内。
- Constraints and trade-offs: 发送尚未接通，因此 chip 只填草稿不发送；不新增高亮/批注/分支的假实现；所有文案走 i18n；引用条不得改变正文滚动位置的恢复语义。
- AI recommendation and rationale: 浮条只在阅读正文出现，避免在侧栏或输入框上弹菜单；引用条放在输入坞内、chips 紧随，结构照 Android。
- User-confirmation decisions: 结构与引用/chips 来自评审稿。
- AI-autonomous decisions: 浮条范围限定 `.chat-reading`；滚动即收起浮条；复制反馈复用输入坞状态行；chip 只填草稿并聚焦输入框；引用文字按用户内容原样展示，可访问名用「引用『…』」资源模板。
- Acceptance evidence: ChatPage 测试先红后绿，覆盖浮条出现/收起、复制、全选、问 AI、chips、清引用与 Esc；浏览器真实交互截图。
- Facts: `ChatPage` 已有 `writeClipboard` 与 `feedback` 状态；`Composer` 已支持 `statusSlot`；夹具主题 0 有完整回答，主题 1 无回答。
- Assumptions: jsdom 的 Selection API 支持 `addRange`/`removeAllRanges`，可通过 stub `getBoundingClientRect` 提供非零矩形。
- Unknowns: 无。
- Risks: 全选后选区更新可能再次触发 selectionchange 造成闪烁；输入坞高度变化可能挤压正文；浮条在窄窗遮挡文字。
- Deferred: 触屏长按菜单、真实发送、持久化高亮/批注。
- Non-goals: 不改 Android；不改发送与附件禁用语义；不持久化引用。

## Goal

在对话阅读页接入选区生命周期，交付可工作的复制、全选、问 AI、引用条与三个 ask chips，且不伪造未接通能力。

## Scope

### In scope
- 新建 `frontend/src/hooks/useTextSelection.ts`（选区读取、收起、全选）。
- `frontend/src/pages/ChatPage.tsx`：挂载浮条、复制 / 全选 / 问 AI、引用状态、chips 与反馈。
- `frontend/src/components/Composer.tsx`：新增引用条插槽。
- `frontend/src/styles/workspace.css`：引用条与 chips 样式。
- `frontend/src/copy/zh-CN.ts`：引用、chips、选中复制的文案。
- `frontend/test/pages/ChatPage.test.tsx` 增补选区用例。

### Out of scope
- 浮条组件本身与定位（082）；桌面打包（084）；真实发送/高亮/批注/新建主题。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增选区与引用状态；保留草稿、滚动恢复、主题切换、分支面板、知识树、人设、复制整条回答、窄窗与降级。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `pages/ChatPage.tsx` 消费 `components/SelectionToolbar.tsx`、`hooks/useTextSelection.ts`、`components/Composer.tsx`；文案生产者 `copy/zh-CN.ts`；样式 `styles/workspace.css`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 对话阅读页新增选中文本浮条、复制选中、全选、问 AI 引用与 ask chips。
- Authoritative code/config/migration evidence: `frontend/src/pages/ChatPage.tsx:73`（quote 状态）、`:75`（readingRef）、`:79`（useTextSelection）、`:121`（复制选中）、`:126`（问 AI）、`:132`（全选）、`:167`（阅读根节点）、`:255` 起（引用条与 chips）、`:290`（浮条挂载）；`components/Composer.tsx:20/:52`（quoteSlot）；`styles/workspace.css:454`（浮条）、`:592` 起（引用与 chips）。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`Docs/04-ux-design/2026-10-25-阅读页玻璃方案/知芽-阅读页玻璃方案-v1.html`。
- Documentation files updated: 与 081/082 同批追加一条 2026-10-25 记录。
- Producer/consumer/sibling/compatibility paths checked: Android `ChatScreen` 的引用/ask 行为、`strings.xml`；Web `ChatPage` 既有复制与草稿路径。
- Superseded phrases/examples reverse-searched: 「选中文字后复制」（旧失败提示）；无旧实现可退。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改历史文档。

## Acceptance criteria

- [x] 选区在 `.chat-reading` 内时出现浮条；选区在侧栏/输入框或折叠时不出现。
- [x] 浮条「复制」写入选中文字并显示成功反馈；剪贴板被拒显示失败反馈。
- [x] 浮条「全选」选中整条回答，浮条仍在。
- [x] 浮条「问 AI」后引用条显示选中文字、chips 出现、输入框聚焦；点「为什么 / 是什么 / 怎么用」填入同名草稿；清除引用后引用条与 chips 消失。
- [x] 滚动、Esc、窗口缩放后浮条收起。
- [x] 既有对话页测试全绿；`pnpm --dir frontend lint` 全绿。

## TDD execution record

### RED — failing test first
- Test/scenario: `frontend/test/pages/ChatPage.test.tsx` 增补 5 条：浮条出现/范围/折叠、复制选中成功与失败、全选整条回答、问 AI 引用条 + 三 chip + 草稿 + 清除、滚动/Esc/缩放收起。
- Command: `pnpm --dir frontend exec vitest run test/pages/ChatPage.test.tsx`
- Exit code: 1
- Observed failure: 5 failed / 9 passed；新用例均卡在 `[data-word-action="ask_ai"]` 或 `.selection-toolbar` 不存在，既有 9 条不受影响。
- Failure reason validated: 选区 hook、ChatPage 接线、引用条与文案都未落地，属功能缺失。
- Evidence: `artifacts/evidence/web-reading-glass-2026-10-25/083-red.txt`

### GREEN — minimum implementation
- Implementation: 新建 `frontend/src/hooks/useTextSelection.ts`（范围限定、滚动/缩放/Esc 收起、`selectAll`）；`frontend/src/pages/ChatPage.tsx:75` 阅读面 ref、`:79` 挂 hook、`:121` 复制选中、`:255` 引用条与 chips、`:290` 浮条挂载；`frontend/src/components/Composer.tsx:20` 新增 `quoteSlot`；`frontend/src/copy/zh-CN.ts:59` 起引用与 chips 文案；`frontend/src/styles/workspace.css:592` 起引用条、chips 与清除热区。几何与 `menuPosition` 下沉到 `frontend/src/utils/selection.ts`，避免 hooks→components 反向依赖（lint 首轮实测发现后修正）。
- Command: `pnpm --dir frontend exec vitest run test/pages/ChatPage.test.tsx`
- Exit code: 0
- Passing evidence: 14 tests passed；全量 `vitest run` 26 files / 180 tests passed；`pnpm --dir frontend run lint` EXITCODE=0（44 文件依赖方向 0 违规）。
- Regression evidence: 既有对话页 9 条（夹具、建议、滚动恢复、整条复制、禁用语义、分支面板、知识树、无基线主题）全绿；浏览器实测 6 项 PASS，见 `artifacts/evidence/web-reading-glass-2026-10-25/083-browser.txt`。

### REFACTOR — optional
- Cleanup: 选区几何/类型移到 `utils/selection.ts` 并由浮条组件再导出；测试读取 CSS 改为相对 vitest root（jsdom 下 `import.meta.url` 不是 `file:`）。
- Regression evidence: 全量 26 files / 180 tests、lint 退出码 0。

### LOOP — optional
- Command and rounds: 1 轮：lint 首跑报 `hooks/useTextSelection.ts → components/SelectionToolbar` 反向依赖，抽到 utils 后转绿；全量首跑另发现浮条样式测试在 jsdom 下读不到 file URL，改为 root 相对路径。
- Failure identity and repeat count: 2 类各 1 次，均为工程约束/测试环境问题，不是功能回归。
- Stop condition hit: 是。
- Handoff: 084 重建前端产物并重打 Windows 便携 exe。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 选区快照只保存文本与矩形，不保存 Range；全选通过回答根节点重建范围。
- 引用状态只属于当前对话页实例，不写 store、不写 localStorage。
- 点 chip 只 `updateDraft` 并 `focus()`，不调用发送。
- 引用条文字用 `line-clamp` 两行，可访问名用 `chat_quoted` 模板；清除按钮 44×44。
- 热点成本：`ChatPage` 只新增一个局部 `quote` 状态与 `readingRef`，没有跨模块共享规则、兼容分支或新的状态边界；不为它引入 store 或新模块，职责仍留在页面内。

## Verification report

- Verdict: PASS（独立复核通过，2026-10-25）
- Evidence: `artifacts/evidence/web-reading-glass-2026-10-25/083-red.txt`、`083-green.txt`、`083-browser.txt` 与 `083-toolbar.png` / `083-quote.png` / `083-quote-chip.png`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 新增 5 条用例，无删除；既有 9 条原样保留。
- Gaps: 键盘选择只依赖 `selectionchange`，未做 Shift+方向键的浏览器级回归；触屏长按菜单与真实发送不在本期。
- Risks: 引用条出现后输入坞变高，极矮窗口下正文可视区被压缩；引用/选区状态在换主题时清空，刷新不保留（符合非持久化约定）。

## Handoff notes

- Completed: 选区生命周期、复制选中、全选、问 AI 引用条与 chips、清除与收起；14 例与最终全量 180 例绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 084 用新前端产物重打 Windows 便携 exe 并覆盖安装副本。
- Watch-outs: 不要让全选触发的 selectionchange 把浮条闪掉；不要用 chip 触发发送；引用不写 store。
