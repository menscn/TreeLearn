# Treelearn Harness Task: 公式接入渲染、降级与文档回写

id: T-2026-09-28-104
title: 公式接入渲染、降级与文档回写
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-28-chat-math-formula-rendering-100.md
requirements_confirmed: true
generated_by: leader
owner: android-math-implementer
owner_lane: android
stack: android
estimated_production_lines: 130
shared_code_touched: true
depends_on: [T-2026-09-28-chat-math-layout-span-103.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 T-2026-09-28-100；本任务把 101–103 接进答案渲染，并兑现四项拍板的可见语义：四种定界符都支持、不支持/未闭合降级为等宽底色源码、流式未闭合显示源码不闪、朗读跳掉公式。
- Expected outcome: 答案区公式以数学排版呈现；画不出或半截的按等宽底色源码显示，内容不丢；思维链与译文路径（`render()`）同样覆盖；朗读不再念 LaTeX；文档状态回写。
- Constraints and trade-offs: 必须保留答案首段视图 id、选中复制（`Row.answerViews`）、表格是视图不是文本、流式防闪烁去抖；不引入依赖；不改设计 token。
- Facts: `MarkdownRenderer.renderSegments`（`:134`）为答案路径，`render()`（`:112`）用于 `ReasoningDisclosure.java:134`；`ChatScreen.java:1075` 消费 segments；`ChatSpeaker.stripMarkdown`（`:132`）；`CODE_BACKGROUND` 与 `TypefaceSpan("monospace")`（`MarkdownRenderer.java:68/328`）为既有代码块样式，降级沿用同一观感。
- Risks: 占位符回填遗漏（表格单元格内公式）；回填位置偏移导致 span 错位；别线并发编辑 `ChatScreen`/`ChatPresenter`（写完立即 `git add`）。
- Deferred: `\begin{}` 环境（本任务走降级路径）。

## Goal

在 `MarkdownRenderer` 中完成「扫描占位 → commonmark 解析 → 回填自绘 span / 降级源码」的接线，块级公式独立成段居中，并让朗读跳过公式，最后回写技术设计文档第 1.3 项状态。

## Scope

### In scope
- 修改 `android/app/src/main/java/com/treelearn/app/screen/MarkdownRenderer.java`：`renderSegments`/`render` 先 `MathScanner.scan`，用 `masked` 文本进 commonmark；prose 段与表格单元格在生成后统一执行 `applyMath`（占位符 → 成功画就 `MathSpan`，失败/不支持就等宽底色源码）。
- 块级公式语义：独立成段、居中（`AlignmentSpan.Standard(ALIGN_CENTER)`）、前后成块换行；行内公式随文排版。
- 降级语义（用户拍板）：`TypefaceSpan("monospace")` + `BackgroundColorSpan(CODE_BACKGROUND)`，内容为**去掉定界符的内部 LaTeX 源码**；未闭合（流式半截）因 101 不抽取而自然按原文直出，不额外处理。
- 修改 `android/app/src/main/java/com/treelearn/app/ui/ChatSpeaker.java`：`stripMarkdown` 把四种定界公式替换为「公式」（朗读已屏蔽，代码保持一致，不新增入口）。
- 文档回写：`Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md:20` 第 1.3 项由「defer / P2 / 降级为原文」更新为「子集已支持（四种定界符），`\begin{}` 环境降级为等宽源码」，保留历史结论痕迹（不静默改写），并按需核 `android/README.md`。

### Out of scope
- 解析/布局/绘制实现（101–103）；Mermaid；代码高亮；后端与存储；设计 token。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 公式由源码→排版；**保留**：无公式回答完全不变、表格仍落成真实 TableLayout、代码块点击复制、答案首段视图 id 与 `answerViews` 遍历、流式逐项 contentEquals 去抖（防闪烁）、思维链 `render()` 行为除公式外不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 AI 流式回答 → `ChatScreen.renderAnswerBody`（`:1075`）；消费者 `MarkdownRenderer.renderSegments`（`:134`）、`render`（`:112`）；同级入口 `ReasoningDisclosure`（`:134` 思维链）、`ChatSpeaker.stripMarkdown`（`:132`）、`ChatScreen.inflateTable`（`:1124` 表格单元格内公式）；平台分支 tablet landscape 保留。
- Native bridge or independent-client compatibility/update requirements: 无跨端桥接；不涉及持久化格式。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本任务为用户可见行为变更。

## Documentation impact record

- Changed runtime facts: 答案与思维链/译文支持四种定界公式的数学排版；不支持与半截公式降级为等宽底色源码；朗读把公式读成「公式」。
- Authoritative code/config/migration evidence: `MarkdownRenderer.java`（scan→masked→applyMath）、`ChatSpeaker.java:132`、`ChatScreen.java:1075`。
- Documentation surfaces checked: `Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md`（1.3 项）、`Docs/04-ux-design/`（视觉相关表述）、`android/README.md`。
- Documentation files updated: 矩阵 1.3 项（状态 + 降级语义），必要时 `android/README.md`。
- Producer/consumer/sibling/compatibility paths checked: `ReasoningDisclosure`（`render()` 路径）、`ChatScreen.inflateTable`（单元格内公式）、`ChatSpeaker`（朗读）。
- Superseded phrases/examples reverse-searched: 「无 LaTeX 渲染」「defer：学习主流程非必需，渲染成本高」「[PART] 降级为原文」——在 `Docs/` 内反向搜索并更新为新状态，保留日期痕迹。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 矩阵为带日期评估文档，更新时标注当前态与日期，不静默改写历史结论；若 PRD 有冲突表述只报告不自动编辑。

## Acceptance criteria

- [ ] 含 `$x^2+y^2=r^2$` 与 `$$\frac{1}{2}$$` 的回答渲染为数学排版（仪器/真机截图留证），不出现 `\frac` 等源码文本。
- [ ] 块级公式独立成段并居中；行内公式与同行文字基线协调。
- [ ] 不支持结构（如 `\begin{matrix}a\end{matrix}`）与半截公式（流式 `$$\frac{1`）显示为等宽底色源码，内容不丢，无崩溃。
- [ ] 代码块内 `$x$`、货币 `$5 到 $10` 不被当成公式。
- [ ] 相邻场景不回归：无公式回答、纯表格回答、表格单元格内公式、代码块点击复制、答案首段视图 id 与选中复制均保持既有行为（复用既有 `MarkdownRendererInstrumentedTest` 与新增加法断言）。
- [ ] `ChatSpeaker.stripMarkdown` 对含公式文本输出「公式」占位，不再出现 `\frac` 片段。
- [ ] 文档回写完成，`Docs/` 反向搜索无遗留旧表述。
- [ ] Java-only guard PASS；`gradlew lint` 与受影响单测/仪器测试通过。

## TDD execution record

### RED — failing test first
- PENDING
### GREEN — minimum implementation
- PENDING
### REFACTOR — optional
- PENDING
### TDD or sizing exception — only with prior user approval
- 无例外。

## Implementation notes

- 回填统一入口 `applyMath(SpannableStringBuilder)`：prose 段 flush 前与表格 cell 生成后各调一次，避免单元格内公式漏画。
- 占位符按**字符串定位**替换（不用下标缓存，因 commonmark 可能把占位符拆到多个 span/节点）；替换时先取 `toString()` 定位再 `replace` 并设置 span。
- 降级与代码块共用 `CODE_BACKGROUND` 与 `monospace`，不新增颜色常量（不写裸 hex）。
- 与别线并发：新文件与改动文件写完立即 `git add`；改 XML 后全树扫 `--`。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 无。
- In progress: 无。
- Blocker: 待 101–103 完成。
- Unblock condition: T-103 verified。
- Next steps: 接线后跑既有 `MarkdownRendererInstrumentedTest` 相邻场景 + 真机答案页截图；再回写文档。
- Watch-outs: 别破坏答案首段视图 id 与 `answerViews`（选中复制读的是它）；表格单元格内公式要走同一回填；半截公式不得闪烁（未闭合即不抽取）。
