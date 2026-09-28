# Treelearn Harness Task: LaTeX 子集解析与节点树

id: T-2026-09-28-102
title: LaTeX 子集解析与节点树
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-28-chat-math-formula-rendering-100.md
requirements_confirmed: true
generated_by: leader
owner: android-math-implementer
owner_lane: android
stack: android
estimated_production_lines: 190
shared_code_touched: false
depends_on: [T-2026-09-28-chat-math-scan-extract-101.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 T-2026-09-28-100；本任务承接 101 抽出的 LaTeX 源码，产出可布局的节点树。用户拍板范围「都支持」指的是四种定界符，语法子集按本任务列表，超出即降级源码。
- Expected outcome: 纯 Java 解析器：LaTeX 子集 → `MathNode` 树；不支持的结构（含 `\begin{}` 环境、未知命令、括号不配对）返回明确的「不支持」结果，供 104 降级。
- Constraints and trade-offs: 不引入依赖；不做完整 TeX；符号用 Unicode 映射（希腊字母、运算符、箭头、关系符），不打包字体。
- Facts: 101 抽出的是**未**被 commonmark 改写的原始 LaTeX（前提是 101 在解析前完成占位）。
- Risks: 命令表覆盖不足导致过度降级；`\text{}` 内中文需按正文样式而非数学斜体。
- Deferred: `\begin{matrix}` / `\begin{align}` / `\substack` 等环境结构。

## Goal

把 LaTeX 源码解析成节点树（分组、上下标、`\frac`、`\sqrt`、带上下限运算符、`\left\right` 定界符、`\text{}`、符号与希腊字母），并对无法解析的输入给出「不支持」信号。

## Scope

### In scope
- 新增 `android/app/src/main/java/com/treelearn/app/ui/math/MathParser.java`、`MathNode.java`、`MathSymbols.java`。
- `MathParser.parse(String latex)` → `MathNode`（失败返回 `null`，不抛异常给调用方）。
- 节点类型：`Row`（序列）、`Atom`（文本/数字/符号，`MathAtomKind`：ORDINARY / NUMBER / OPERATOR / RELATION / PUNCT / TEXT）、`Frac`、`Sqrt`（含可选 `[n]` 次数）、`Script`（base + 上标/下标）、`BigOp`（`\sum \int \prod \lim \log \sin` 等，带上下限）、`Accent`（`\vec \bar \hat \dot \tilde`）、`Delimited`（`\left..\right` 或 `\big` 系列）。
- 语法：`{...}` 分组、`^`/`_` 上标下标（含 `^{...}`）、`\frac{a}{b}`、`\sqrt{x}`、`\sqrt[n]{x}`、`\text{...}`、`\left(`/`\right)` 及其 `[]{}|. ⟨⟩` 变体、空格与 `\,` `\;` `\!` 间距、`\\` 换行（降级）、未知命令 → 不支持。
- 符号表：`\alpha`…`\omega` 希腊字母、`\infty \partial \nabla \approx \neq \leq \geq \times \cdot \pm \to \rightarrow \Rightarrow \in \subset \cup \cap \forall \exists` 等常用符号 → Unicode 码点。

### Out of scope
- `\begin{}...\end{}` 环境（返回不支持 → 降级源码）；布局与绘制（103）；接入与降级 UI（104）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增未接线纯函数；不改变既有渲染。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者为 101 的 `MathFormula.latex`；本期无消费者（103 起消费）；同级入口无。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 未接线纯解析，行为中性；以单测证明。

## Documentation impact record

- Changed runtime facts: 无（未接线）。
- Authoritative code/config/migration evidence: 新增 `MathParser.java` / `MathNode.java` / `MathSymbols.java`（位于 `android/app/src/main/java/com/treelearn/app/ui/math/`）。
- Documentation surfaces checked: `android/README.md`（无新依赖，无需改）。
- Documentation files updated: 无（集中在 T-104）。
- Producer/consumer/sibling/compatibility paths checked: 101 输出契约（`MathFormula.latex`）；103 输入契约（`MathNode` 树）在任务内以注释固定。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence: 未接线纯解析新增，不改运行时行为；证据为不修改 `MarkdownRenderer.java` / `ChatScreen.java`。
- Historical/PRD handling: 不涉及。

## Acceptance criteria

- [ ] `\frac{1}{2}` 解析为 `Frac(Atom(1), Atom(2))`；`x^2` 为 `Script(x, sup=2)`；`x_{i,j}` 为 `Script(x, sub=Row(i,j))`。
- [ ] `\sqrt{x}` 与 `\sqrt[3]{x}` 分别得到无次数与次数为 3 的 `Sqrt`。
- [ ] `\sum_{i=1}^{n}` 得到 `BigOp` 且上下限节点正确；`\lim_{x \to 0}` 下限在正下方（由 103 解释）。
- [ ] `\left(\frac{a}{b}\right)` 得到 `Delimited`，左右定界符保留。
- [ ] `\alpha\beta` → 两个希腊字母 Atom；`\text{其中 x}` → 一个 `TEXT` Atom 保留中文。
- [ ] `\begin{matrix}a\end{matrix}`、`\unknowncmd{x}`、括号不配对 → `parse` 返回 `null`（不支持，供降级），且不抛异常。
- [ ] JVM 单测 RED→GREEN 证据齐备；解析结果与 101 契约一致（输入为原始 LaTeX 源码）。

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

- 递归下降：先分词（命令 `\word`、 `{`、`}`、`^`、`_`、`&`、`\\`、普通字符），再按优先级建树；深度上限保护（防恶意嵌套栈溢出）。
- 数字与拉丁字母区分 `MathAtomKind`，供 103 决定是否用数学斜体（字母斜、数字与运算符直立）。
- 解析失败一律返回 `null`，不抛受检异常，便于 104 一行判断降级。
- 与 103 的边界：本任务只产树，不做任何像素计算。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Gaps: PENDING
- Risks: PENDING

## Handoff notes

- Completed: 无。
- In progress: 无。
- Blocker: 待 101 完成（`MathFormula` 契约）。
- Unblock condition: T-101 verified。
- Next steps: 101 完成后按 `MathNode` 树契约实现解析并跑单测。
- Watch-outs: `\text{}` 内不做数学替换；中文按正文样式；未知命令必须降级而不是猜着画。
