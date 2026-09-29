# Treelearn Harness Task: 对话回答数学公式渲染（父需求）

id: T-2026-09-28-100
title: 对话回答数学公式渲染（父需求）
template_version: 2
task_type: requirement
status: verifying
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: android
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 用户反馈「AI 回答中的公式，没有渲染出来」。核实为**能力缺口而非缺陷**：`android/app/src/main/java/com/treelearn/app/screen/MarkdownRenderer.java`（379 行）基于 `org.commonmark:commonmark:0.22.0`（`android/app/build.gradle:103`），节点分支只覆盖 Text / Code / FencedCodeBlock / IndentedCodeBlock / Heading / Emphasis / StrongEmphasis / Link / BlockQuote / List / TableBlock，没有任何数学节点；全仓搜 `latex|katex|mathjax|MathView` 只命中树布局几何公式（`TreeCanvasView.java`、`HomeMotion.java`），`android/app/src/main` 下无任何 `WebView`。`Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md:20` 第 1.3 项把「表格 / LaTeX / Mermaid 渲染」定为 defer / P2 / 降级为原文——表格已于 2026-09-28 落地（表格是视图，不是文本），LaTeX 一直未做。附带事实：公式源码在落到 TextView 前已被 commonmark 行内语法改过（`x * y` 的 `*` 变强调、`\_`/`\\` 被当转义吃掉反斜杠），所以用户看到的既是源码又是被改写的源码。
- Users/scenarios: 单人自用；学习类提问（数学、物理、统计、机器学习）时 AI 回答里的行内公式与独立公式块要能看懂，而不是看到 `\frac{1}{2}` 原始文本。
- Expected outcome: 4 个实现任务（101–104）交付并通过 verifier；答案区公式以排版后的数学样式呈现，画不出的降级为等宽底色源码，内容不丢；真机观感由用户自测。
- Core flow and reversibility: 答案 markdown → 扫描抽出公式并占位 → commonmark 解析（公式不再被行内语法改写）→ 回填自绘 span → TextView；块级公式独立成段居中；未闭合（流式半截）按源码显示不闪；朗读跳掉公式。
- Inputs/outputs: 输入为 AI 回答 markdown 原文（含 `$...$`、`$$...$$`、`\(...\)`、`\[...\]`）；输出为 Android Spannable（自绘 `ReplacementSpan`），不改变存储层与后端。
- State and exception handling: **[PART]** 未闭合公式 → 源码直出；**[PART]** 不支持的语法（矩阵 `\begin{matrix}`、多行对齐 `\begin{align}` 等）→ 等宽底色源码块，与代码块观感一致；代码块与行内代码内的 `$` 一律不解析；货币写法（`$5 and $10`）不当公式。
- Constraints and trade-offs: Java-only（禁 Kotlin，`.trae/rules/android.md`）；不引入 WebView / KaTeX / jlatexmath，不新增 Gradle 依赖；单任务非注释生产行 ≤200；不改设计 token 与 `colors.xml`；不改后端与存储。
- AI recommendation and rationale: 选自绘 `ReplacementSpan`（无新依赖、随 TextView 排版、保留选中复制、流式友好、APK 体积不变），复杂式降级为等宽源码；不选 WebView+KaTeX（列表里每公式一个 WebView 性能差、高度要 JS 回传、选中复制失效、+1.5MB assets），不选 jlatexmath（许可风险、Bitmap 缩放糊）。用户已采纳。
- User choice and custom additions: 用户 2026-09-28 四项拍板——① 行内范围「都支持」：`$...$`、`$$...$$`、`\(...\)`、`\[...\]` 全认；② 降级形态：等宽底色源码；③ 流式期间：显示源码，避免文字跳动；④ 朗读：跳掉公式或读成「公式」；⑤ 走 Harness。
- AI-autonomous decisions: 任务切分为四个垂直切片（101 扫描抽取 / 102 解析建树 / 103 布局与绘制 / 104 接入与降级），串行依赖；布局计算与 Canvas 绘制分离，使布局尺寸可在纯 JVM 单测断言、绘制只留给 androidTest 与真机证据；货币保护规则为「行内 `$...$` 内容须含数学特征字符（`\ ^ _ { }` 等）且同行闭合」。
- User-confirmation decisions: 上述四项拍板 + 走 Harness；2026-09-28 用户补充批准 T-102、T-103 两项作事后规模例外。审批时两项均记录为 369 行；当前实测 T-102 为 378 行（较原记录增加 9 行，超 200 行 178 行 / 89%，仍低于 500 行硬上限），T-103 为 369 行（超 200 行 169 行 / 84.5%）。这里的 378 行是现状复核，不声称用户另行批准了精确的 378 行数值。例外仅限这两个任务，其余任务仍按 ≤200 行目标执行；这是事后追认，记录为流程偏差，不扩展为全局豁免。
- Acceptance evidence: 101/102 用 `android/app/src/test` JVM 单测（RED→GREEN）；103 布局用 JVM 单测、绘制用 androidTest 截图或真机 screencap；104 用既有 `MarkdownRendererInstrumentedTest` 相邻场景回归 + 真机答案页截图。
- Facts: `MarkdownRenderer.renderSegments` 为答案路径（`ChatScreen.java:1076`），`render()` 仅用于思维链与译文（`ReasoningDisclosure.java:134`）；表格以 Segment 形式由 `ChatScreen.inflateTable` 落成真实 TableLayout；`ChatSpeaker.stripMarkdown`（`ChatSpeaker.java:132`）会把 `\frac{1}{2}` 整串念出（朗读入口已屏蔽，代码仍在）。
- Assumptions: AI 输出的公式分隔符为 LaTeX 常见四种；公式复杂度以中小学到大学本科通识为主；答案 TextView 为 `match_parent`、正文走 `--sans`。
- Unknowns: 真实回答里公式占比与复杂结构分布（无抓取样本，artifacts 内未搜到含 `\frac`/`$$` 的回答文本）；`\begin{}` 类结构的实际需求量。
- Risks: 自绘覆盖不全导致部分公式降级（已由用户接受降级形态）；`ReplacementSpan` 高度超过行高可能被裁（103 需同时处理行高）；与别线并发编辑 `ChatScreen`/`ChatPresenter` 同一 android 树（写完立即 `git add`）；货币误判（101 用数学特征字符规则缓解，仍需反例测试）。
- Deferred: 矩阵 / 多行对齐 / 交换图等 `\begin{}` 结构（降级源码）；代码高亮；Mermaid。
- Non-goals: 不做公式编辑与输入；不做公式搜索或复制 LaTeX 源码按钮；不改后端输出格式；不引入任何新依赖。
- Skipped non-critical questions and reason: 未追问「降级时是否显示提示文案」——用户已直接指定等宽底色源码；未追问「块级公式是否可横向滚动」——先按居中单行处理，过宽情形由降级与换行兜底。

## Goal

让 AI 回答中的数学公式在答案区以排版后的数学样式呈现，不支持的结构降级为等宽底色源码，内容不丢。

## Scope

### In scope
- 扫描并抽取 `$...$`、`$$...$$`、`\(...\)`、`\[...\]` 四种定界公式（跳过代码块与行内代码、跳过货币写法、未闭合保留源码）。
- LaTeX 子集解析为节点树：上下标、`\frac`、`\sqrt`、希腊字母与常用符号、`\sum \int \prod \lim` 等带上下限运算符、`\left\right` 定界符、`\vec \bar \hat` 等修饰、`\text{}`。
- 布局计算（尺寸与基线，纯 JVM 可测）与 `ReplacementSpan` 自绘（含行高处理）。
- 接入 `MarkdownRenderer` 与 `ChatScreen` 答案段渲染；块级公式独立成段居中；降级为等宽底色源码。
- `ChatSpeaker.stripMarkdown` 跳掉公式（读成「公式」或跳过）。
- 回写 `Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md` 第 1.3 项状态。

### Out of scope
- `\begin{matrix}` / `\begin{align}` 等环境结构（降级源码）；Mermaid；代码高亮；后端与存储改动；设计 token 与 `colors.xml` 改动；新增 Gradle 依赖。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增公式排版；**必须保留**的是现有 prose/表格/代码块/链接/引用/列表渲染、`render()` 思维链与译文路径、答案首段视图 id 与选中复制（记忆：答案路径走 `renderSegments`，读选区须遍历 `Row.answerViews`）、流式防闪烁（逐项 contentEquals 去抖）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者为 AI 流式回答 → `ChatScreen.renderAnswerBody`（`ChatScreen.java:1075`）；消费者 `MarkdownRenderer.renderSegments`（`:134`）与 `render`（`:112`）；同级入口 `ReasoningDisclosure.java:134`（思维链）、`ChatSpeaker.stripMarkdown`（`:132`，朗读）；平台分支 tablet landscape 需保留。
- Native bridge or independent-client compatibility/update requirements: 无跨端桥接；不涉及契约与持久化格式。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本需求是可见行为变更。

## Documentation impact record

- Changed runtime facts: 答案区新增公式渲染与降级行为；朗读跳过公式。
- Authoritative code/config/migration evidence: `android/app/src/main/java/com/treelearn/app/screen/MarkdownRenderer.java`、`ChatScreen.java:1075`、`android/app/src/main/java/com/treelearn/app/ui/ChatSpeaker.java:132`。
- Documentation surfaces checked: `android/README.md`；`Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md:20`（1.3 项）。
- Documentation files updated: 由 T-104 回写矩阵 1.3 项（LaTeX 由 defer/降级原文 → 子集已支持、`\begin{}` 降级），并按需补 `android/README.md`。
- Producer/consumer/sibling/compatibility paths checked: `ReasoningDisclosure`（思维链走 `render()`，须一并覆盖公式）、`ChatSpeaker`（朗读）、表格单元格内公式（`readTable` 走 `renderChildren`）。
- Superseded phrases/examples reverse-searched: 「无 LaTeX 渲染」「defer：学习主流程非必需，渲染成本高」「降级为原文」——在 `Docs/05-technical-design/` 与 `Docs/04-ux-design/` 内反向搜索后更新。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 矩阵为带日期的评估文档，按「标注当前态 + 链接当前权威」处理，不静默改写历史结论；若 PRD 有相关表述只报告不自动编辑。

## Acceptance criteria

- [ ] 101–104 全部 `verified`，各自 RED→GREEN 证据齐备（命令、退出码、观察失败/通过）。
- [ ] 真机/仪器证据：答案区含 `$...$` 与 `$$...$$` 的回答渲染为数学排版，非源码文本。
- [ ] 相邻场景不回归：无公式回答、纯表格回答、代码块内含 `$`、货币写法 `$5 and $10`、流式半截公式（未闭合）均按既定语义呈现。
- [ ] Java-only guard PASS；`gradlew lint` 与受影响单测通过。
- [ ] 文档回写完成且反向搜索无遗留旧表述。

## TDD execution record

- 父需求不实现代码，`tdd_mode: not-applicable`；RED/GREEN 证据落在 101–104 各自任务文件。

### RED — failing test first
- N/A（父需求）
### GREEN — minimum implementation
- N/A（父需求）
### REFACTOR — optional
- N/A（父需求）
### TDD or sizing exception — only with prior user approval
- T-102：用户于 2026-09-28 明确允许该任务作带说明的规模例外；事后追认。审批时记录 369 行；当前实测 378 行（增加 9 行，超 200 行 178 行 / 89%，低于 500 行硬上限）。此处如实更新现状，不声称用户另行批准精确 378 行。理由：按原确认范围保留解析器 API，并以独立验收和回归证据控制风险。剩余风险：单任务审阅负担增加；任务仍受 T-101 依赖阻塞。
- T-103：用户于 2026-09-28 明确允许对该项作带说明的特例批注；事后追认 369 行，超 200 行 169 行（84.5%），低于 500 行硬上限。理由：实现已由前序会话完成，保留紧密关联的测量、布局、绘制与行高代码，并以 JVM/真机测试及独立验收控制风险。剩余风险：单任务审阅负担增加。
- 该追认是流程偏差，不改变 Harness 默认规模规则，也不适用于 T-101、T-104 或批注任务。

## Implementation notes

- 子任务：101 扫描抽取 → 102 解析建树 → 103 布局与自绘 → 104 接入、降级、朗读、文档回写；串行依赖，verifier 串行。
- 布局与绘制分离（布局纯 Java 可测，绘制依赖 Canvas）。
- 与别线并发编辑同一 android 树：新文件写完立即 `git add`；改 XML 后全树扫 `--`（AAPT2 全仓失败）。

## Verification report

- Verdict: PARTIAL
- Evidence: T-104 定向 renderer 与朗读测试各 11/11；lint、Java-only guard、debug 构建通过；设备安装与主 Activity 启动证据见 `artifacts/evidence/T-2026-09-28-math-renderer-install-launch.txt`。
- Gaps: T-101 仍为 blocked/FAIL；T-102 为 PARTIAL；T-103 的字号缩放与 Paint 状态缺陷已修复并有 3/3 真机证据，但独立 verifier 对 Harness evidence 给出 FAIL；T-104 仍缺保留 RED 证据和完整答案页视觉截图；全量 connected instrumentation 当前为 275 项中 29 项失败。
- Risks: 公式链仍受 T-101 的规模/TDD 缺口和 T-104 独立验收缺口阻塞；不支持公式继续降级为源码，不把定向 11/11 推断为全量 PASS。

### Independent verifier addendum (latest)

- Verdict: PARTIAL. 定向公式与相关 UI 仪器测试共 17/17 通过，最新 Debug APK 已重新构建、安装、冷启动且本地/设备 SHA-256 一致；T-103 独立 verifier 仍对 Harness evidence 给出 FAIL。
- Remaining blockers: T-101 为 FAIL/BLOCKED；T-102 为 PARTIAL；T-103 缺可回读 RED 与同行中文基线证据；T-104 缺可复核 RED、真实答案页视觉截图与完整 Task v2 证据；全量 connected instrumentation 当前为 275 项中 29 项失败。

## Handoff notes

- Completed: 需求澄清与四项拍板、101–104 功能实现推进；T-104 debug APK 已安装并启动。用户明确授权 T-104 仅功能推进，父需求最终仅 PARTIAL。
- In progress: 上游缺口与 T-104 独立验收仍待闭环。
- Blocker: T-101 blocked/FAIL；T-102 PARTIAL；T-103 独立 verifier 对 Harness evidence FAIL；T-104 红测证据和答案页视觉证据缺失。
- Unblock condition: 按各任务契约补齐缺口；T-103 修复授权已取得，但仍需补齐可回读 RED/同行基线并完成重新验收；独立 verifier 确认所有验收项后才可考虑 verified。
- Next steps: 保持父需求与 T-104 verdict 为 PARTIAL；不再扩大 T-103 代码范围，优先补齐 T-101/T-102/T-104 的剩余证据。
- Watch-outs: 未闭合公式必须保持源码直出（流式不闪）；货币写法不可误判；代码块内 `$` 不解析；答案首段视图 id 与选中复制不能被破坏。
