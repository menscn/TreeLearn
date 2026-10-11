# Treelearn Harness Task: 前端视觉收口——毛玻璃模糊落地与侧栏品牌适配

id: T-2026-10-10-033
title: 前端视觉收口——毛玻璃模糊落地与侧栏品牌适配
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 150
shared_code_touched: false
depends_on: [T-2026-10-10-frontend-branch-workspace-031.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 真实浏览器验收（2026-10-10）发现两处视觉缺陷：① token 定义了 `--glass-blur`（18px）但**面板未应用 `backdrop-filter`**，毛玻璃只有半透明、无模糊——用户明确要求「多使用一些毛玻璃等效果」；② 英文界面下侧栏品牌「TreeLearn」溢出 88px 窄栏被裁切。
- Users/scenarios: 桌面浏览器（宽窗/窄窗）、中英双语。
- Expected outcome: 毛玻璃面板真正呈现模糊（`backdrop-filter: blur(var(--glass-blur))`，含 `-webkit-` 前缀与不支持时的降级）；侧栏品牌在窄窗下自适应（不裁切、不溢出；必要时按窗口宽度切换为图标/缩写，且仍有可访问名称）；设计 token 守卫与对比度守卫仍通过。
- Core flow and reversibility: 纯样式/组件调整；可回退。
- Inputs/outputs: 输入=既有 token 与组件；输出=真实模糊效果与自适应侧栏。
- State and exception handling: 浏览器不支持 `backdrop-filter` → 保留半透明背景（可读性不退化）。
- Constraints and trade-offs: 只用 token 变量（不得出现自由色值）；不新增依赖；毛玻璃不得损害文字对比度（对比度守卫须仍过；必要时提高面板不透明度或加描边）。
- AI recommendation and rationale: 把模糊集中到毛玻璃基元（`GlassPanel` 等）与侧栏/卡片，避免散落；用 `@supports not (backdrop-filter: blur(1px))` 做降级。
- User choice and custom additions: 用户要求「确保整体的美观度，可以多使用一些毛玻璃等效果，确保整体的简约美观」。
- AI-autonomous decisions: 模糊强度取值（复用 `--glass-blur`）、侧栏品牌呈现方式。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-033-*`：单测（样式类/结构断言）、守卫输出、`pnpm build`、**真实浏览器复验截图**（中/英、宽/窄窗）。
- Facts: `src/styles/{tokens.css,global.css}`、`components/{GlassPanel,IconButton}.tsx`、`layouts/AppShell.tsx`；T-006 已定义毛玻璃段 token；对比度守卫 `check:contrast` 已接入 build。
- Assumptions: 浏览器支持 `backdrop-filter`（Chrome/Edge 现代版本）。
- Unknowns: 无。
- Risks: 模糊影响性能（记录，必要时限制作用面）；对比度退化 → 守卫拦。
- Deferred: 其它视觉细化（图标一致性微调等）。
- Non-goals: 新增页面；改信息结构。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

让毛玻璃在真实浏览器中生效，并修复侧栏品牌溢出，保持设计 token/对比度守卫全绿。

## Scope

### In scope
- `GlassPanel`（及 AppShell 侧栏/主页卡片等浮层容器）应用 `backdrop-filter: blur(var(--glass-blur))` + `-webkit-backdrop-filter` + `@supports` 降级。
- `AppShell` 侧栏品牌自适应（窄窗不裁切；中英双语下均可读；保留可访问名称）。
- 测试：样式/结构断言（面板带模糊类或内联变量、侧栏品牌在窄窗的呈现策略有断言）、既有 122 项回归。
- 证据：`pnpm test`/`lint`/`check:user-copy-i18n`/`build`（含 token 与对比度守卫）+ 浏览器复验截图。

### Out of scope
- 新增页面/端点；信息结构变更；其它视觉细节。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 面板原半透明+描边+阴影保留；文字可读性不退化；导航 icon-only 与键盘可达不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `GlassPanel` 消费者（主页/工作区/对话框/Toast）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 毛玻璃模糊生效；侧栏在窄窗自适应。
- Authoritative code/config/migration evidence: `web/frontend/src/styles/global.css`、`components/GlassPanel.tsx`、`layouts/AppShell.tsx`。
- Documentation surfaces checked: `web/README.md` 状态节。
- Documentation files updated: `web/README.md` 一行（视觉修复）。
- Producer/consumer/sibling/compatibility paths checked: token 守卫与对比度守卫。
- Superseded phrases/examples reverse-searched: N/A。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `pnpm test`，记录退出码与计数）；业务行 ≤150。
- [ ] 毛玻璃面板真实应用 `backdrop-filter: blur(var(--glass-blur))`（含 `-webkit-` 与 `@supports` 降级），代码证据 + 浏览器复验截图可见模糊。
- [ ] 侧栏品牌在中/英、宽/窄窗均不裁切不溢出（浏览器复验截图）。
- [ ] `pnpm lint`、`check:user-copy-i18n`、`build`（含 token 漂移与对比度守卫）全过。
- [ ] 无自由色值/新依赖；既有 122 项测试全绿。
- [ ] 独立 verifier PASS（含真实浏览器证据）。

## TDD execution record

### RED — failing test first
- Test/scenario: 视觉相关测试（面板样式/侧栏品牌策略）。
- Command: `pnpm test`
- Exit code: 1
- Observed failure: 见证据
- Failure reason validated: 缺失模糊样式/溢出策略

### GREEN — minimum implementation
- Implementation: 毛玻璃 + 侧栏自适应。
- Command: `pnpm test`
- Exit code: 0
- Passing evidence: 见证据
- Regression evidence: 既有 122 全绿

### REFACTOR — optional
- Cleanup: 按 lint/评审整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 模糊只加在浮层/面板，避免整页模糊影响可读性与性能。
- 窄窗品牌策略须保留可访问名称（`aria-label`/`sr-only`），不得只留图形而无可访问名。

## Verification report

- Verdict: PASS（独立 verifier 复核代码与守卫 + 四轮真实浏览器复验收敛，2026-10-10）
- Evidence: `pnpm test` 130/130（15 文件）、`lint`/`check:user-copy-i18n`/`build`（含 token 漂移 54 属性 + 对比度 7 对全 AA）全 EXIT=0；证据 `artifacts/evidence/T-2026-10-10-033-{red,green,lint,user-copy,build,lines,round2-*,round3-*}.txt`。
- **四轮浏览器复验的收敛过程（如实记录，含一次根因误判纠正）**：
  1. 第一轮：报告「看不到模糊」，判为「面板未应用 backdrop-filter」；
  2. 第二轮：verifier 与源码核查显示**声明存在**，判为「降级缺失」并补 `@supports` 降级；浏览器仍报告不可见且误判「样式表无声明」；
  3. 第三轮（leader 亲自核查发行口 CSS 文本）：**定位真正根因**——源码写成「标准属性在前、`-webkit-` 前缀在后」，构建压缩器按 targets 只保留前缀，产出 CSS 仅剩 `-webkit-backdrop-filter`，而现代 Chrome 已移除该前缀支持 → computed `none`；
  4. 第四轮：修复为「前缀在前、标准在后」后，浏览器实测 computed `backdrop-filter` = `blur(18px)`（侧栏）/ `blur(24px)`（raised 面板），**毛玻璃可见**。
- 交付内容（三轮累计）：① 背景 3 层极淡薄荷 `radial-gradient` 层次（`--bg-*` token 派生，无写死色值）使毛玻璃可感知；② `--glass-panel` 0.72→0.62（合成玻璃底色对 ink 14.44:1，仍远超 AA）；③ `@supports` 降级（不支持时保留半透明 + 加强描边）；④ **前缀顺序修正（根因修复）**；⑤ 侧栏品牌两态（宽窗全名 / 88px 窄栏缩写 `知`/`TL`，`role="img"` + 完整 `aria-label` 保留可访问名）；⑥ 顺序契约测试（断言标准属性位于前缀之后，防同类回归）。
- 浏览器侧其他确认：无文字按钮（全部图标 + aria-label + title）；中英切换无漏翻；控制台无错误/警告；网络全 200（首轮报告的 `model-resolution` 404 已由 T-034 修复）。
- Changed/deleted assertion rationale and equivalent behavior coverage: `components.test.tsx` 由「断言存在」升级为「断言存在且顺序正确」（更强的防回归断言，非削弱）。
- 规模：三轮累计业务行 ≈70（AppShell 26 + global.css 约 30 + token/生成器约 14）≤150。
- Gaps: 真实生成需 Provider Key（未提供，属产品验证缺口，见终版报告）；窄窗以外的超窄/超宽视口未逐一截图（自动化视口受限，已记录）。
- Risks: 前缀顺序问题属「构建器行为」类缺陷，已由顺序契约测试钉住；其它第三方属性若同写法仍可能被剥离（当前仅 backdrop-filter 涉及）。

## Handoff notes

- Completed: 三轮修复 + 四轮浏览器复验收敛；130 项测试全绿；守卫全过；dist 已更新（`index-YiKsAapC.css`）。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: 终版交付报告；功能矩阵逐行补证。
- Watch-outs: 前缀属性必须「前缀在前、标准在后」；只用 token 色值；对比度守卫不得放宽。