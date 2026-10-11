# Treelearn Harness Task: 统一消息渲染器（Markdown/代码/表格/公式降级，XSS 安全）

id: T-2026-10-10-027
title: 统一消息渲染器（Markdown/代码/表格/公式降级，XSS 安全）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 450
shared_code_touched: false
depends_on: [T-2026-10-10-frontend-branch-workspace-031.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 矩阵要求「Markdown、代码、表格、公式：当前格式范围及不支持内容的可读降级；流式未闭合语法；安全链接；公式失败保留原文；XSS」。当前工作区为安全纯文本（T-031 明确受限）。
- Users/scenarios: 阅读与流式生成中的渲染（未闭合代码围栏/表格/公式在流式过程中常见）。
- Expected outcome: `src/components/markdown/` 统一渲染器：标题/段落/列表/引用/强调/行内代码/围栏代码（语言标签+复制按钮）/表格/链接（http(s) only + rel）/公式（`$...$`、`$$...$$` 以样式化原文呈现=可读降级）；**结构上不使用 `dangerouslySetInnerHTML`**（React 元素构建，天然 XSS 安全）；流式未闭合语法按「视为当前块延续」渲染（围栏未闭合→代码块；表格行不完整→普通段落；公式未闭合→原文）。
- Core flow and reversibility: 纯前端组件；可回退。
- Inputs/outputs: 输入=消息 Markdown 文本（可能流式中）；输出=React 元素树。
- State and exception handling: 恶意输入（`<script>`、`javascript:` 链接、`data:` 链接、HTML 注入）必须转义/拒绝；超长/深嵌套输入不崩溃（有界）。
- Constraints and trade-offs: **不新增依赖**（自写最小解析器，覆盖当前格式范围）；复制走既有剪贴板工具（若无则本任务加最小封装，含失败提示）；文案 i18n。
- AI recommendation and rationale: 自写 + React 元素输出（审查面小、XSS 结构性安全）；范围外语法（如嵌套列表深层、脚注）以降级为段落呈现并在任务记录列明范围。
- User choice and custom additions: 已批准方案（矩阵范围）。
- AI-autonomous decisions: 解析器结构、支持的强调子集、复制实现。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-027-*` RED/GREEN + 安全用例输出；覆盖测试清单。
- Facts: T-031 的 `MessageView` 为安全纯文本；`IconButton`/图标注册表/i18n 就绪。
- Assumptions: 无需与 Android 渲染像素级一致，但格式范围与降级语义要对齐矩阵。
- Unknowns: 无阻塞项。
- Risks: 自写解析器的边界（列表/表格畸形输入）→ 以表驱动测试覆盖畸形输入；后续如需升级到库渲染，须先做依赖验证（记录为可选演进）。
- Deferred: KaTeX 等富公式渲染（依赖未验证）；语法高亮（同）。
- Non-goals: 编辑/批注锚点（后续任务）；代码执行。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付统一消息渲染器：覆盖矩阵要求的格式范围与降级语义，结构上 XSS 安全，流式容忍未闭合语法。

## Scope

### In scope
- `src/components/markdown/`：解析器（块级：标题/段落/列表/引用/围栏代码/表格/分隔线/公式块；行内：强调/行内代码/链接/行内公式）+ 渲染组件（React 元素，代码块复用 IconButton 复制）+ 流式容忍。
- `CopyButton`（或复用既有工具；失败→i18n 提示）。
- 测试：格式矩阵表驱动（含畸形输入）、流式未闭合序列、安全用例（XSS 注入、危险链接协议、`<img onerror>` 等）、深嵌套/超长有界、复制成功/失败。
- 将 `MessageView` 接入新渲染器（替换纯文本路径；引用块与流式光标保持）。

### Out of scope
- 语法高亮、KaTeX、批注锚点、选区/引用动作。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: `MessageView` 由纯文本升级为 Markdown；旧行为（转义、换行保留）在新渲染器中必须仍成立（测试保证）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `BranchWorkspace`/`MessageView`。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 消息渲染能力扩大（用户可见）。
- Authoritative code/config/migration evidence: `web/frontend/src/components/markdown/**`。
- Documentation surfaces checked: `web/README.md` 状态节；功能矩阵「Markdown、代码、表格、公式」行。
- Documentation files updated: `web/README.md` 状态一行；矩阵该行后续由验收任务补证。
- Producer/consumer/sibling/compatibility paths checked: T-031 的实现说明（纯文本）已更新。
- Superseded phrases/examples reverse-searched: 「本轮为安全纯文本」表述。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `pnpm test`，记录退出码与计数）；业务行 ≤450。
- [ ] 格式覆盖：标题/段落/列表（有序无序）/引用/强调（粗斜）/行内代码/围栏代码（语言标签+复制）/表格（对齐+表头）/链接（http/https）/公式（行内与块级降级）——表驱动测试。
- [ ] 流式容忍：未闭合围栏→代码块、未闭合公式→原文、表格半行→段落——测试。
- [ ] 安全：无 `dangerouslySetInnerHTML`；`<script>`/事件属性/`javascript:`/`data:` 链接不产生可执行或可导航元素；测试含真实恶意样例。
- [ ] 有界：深嵌套/超长输入不崩溃（测试）。
- [ ] `MessageView` 接入且 T-031 既有断言仍绿；i18n/lint/build 守卫全过；无新依赖。
- [ ] 独立 verifier PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `markdown/*.test.tsx`、`MessageView` 断言更新。
- Command: `pnpm test`
- Exit code: 1
- Observed failure: 缺失 `./Markdown` 模块（模块解析失败）
- Failure reason validated: 缺失渲染器

### GREEN — minimum implementation
- Implementation: 解析器 + 渲染组件 + MessageView 接入。
- Command: `pnpm test`
- Exit code: 0
- Passing evidence: 122 passed / 15 files（新增 34）
- Regression evidence: 既有 88 项全绿（88→122）

### REFACTOR — optional
- Cleanup: 按 lint/评审整理（解析派发循环压缩）。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 不支持的语法（脚注/嵌套表格/定义列表/setext/缩进代码块等）统一降级为段落/原文；图片 `![alt](url)`→段落（alt 按普通链接文本处理，verifier 备注已记录）。
- 代码块复制：`navigator.clipboard` 失败→i18n 提示 + 不抛出。
- 安全：无 `dangerouslySetInnerHTML`（React 元素构建）；链接仅 http/https，外部 `target="_blank" rel="noopener noreferrer"`。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10；2 项非阻塞备注）
- Evidence: verifier 自跑 `pnpm test` 122/15、`lint`/`check:user-copy-i18n`（29 文件）/`build` 全 EXIT=0；RED（缺 `./Markdown`）有效。**安全独立验证**：grep 全 `src/` 无 `dangerouslySetInnerHTML/innerHTML/eval` 实际调用；verifier **亲自构造 10 个恶意样例**走解析层 + 11 例真实渲染——仅 https 生成链接，`javascript:/data:/vbscript:/混合大小写`、`<img onerror>`、`<svg/onload>`、注释注入、围栏内 `</code></pre><script>` 全无危险节点、全树无 `on*` 属性。
- 断言强度抽查 ≥8：标题/列表/围栏（语言标签+复制）/表格对齐/行内强调与代码/链接 rel/公式降级/流式三例/有界（20 万字符、深嵌套）/复制三态/MessageView 集成——均为真实元素树与属性断言。
- 降级清单核对：setext/脚注/定义列表/缩进代码→段落；深层嵌套列表→展平+段落；图片 alt 链接化（备注①，非安全问题）。
- Changed/deleted assertion rationale and equivalent behavior coverage: T-031 引用块/转义/流式断言仍绿；无削弱。
- 规模：verifier 独立计数 ≈445（自述 417）——≤500 硬上限、接近自定 450（verifier 备注②：新增即可能越线）。
- Gaps: 语法高亮与 KaTeX 富公式渲染（依赖未验证，属 Deferred）；范围外语法清单需在 README/矩阵注明（备注①）。
- Risks: 自写解析器边界由表驱动测试覆盖；规模贴近上限。

## Handoff notes

- Completed: 实施 + 独立 verifier PASS（2026-10-10）。产物：`components/markdown/{parse.ts,Markdown.tsx}` + 34 测试；`MessageView` 接入；122 项测试全绿。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: 选文/复制/引用动作与批注任务；README/矩阵补注范围外语法清单。
- Watch-outs: 不得引入依赖；不得使用 dangerouslySetInnerHTML；规模接近 450。