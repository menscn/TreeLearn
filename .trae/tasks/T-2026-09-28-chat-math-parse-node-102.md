# Treelearn Harness Task: LaTeX 子集解析与节点树

id: T-2026-09-28-102
title: LaTeX 子集解析与节点树
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-28-chat-math-formula-rendering-100.md
requirements_confirmed: true
generated_by: leader
owner: android-math-implementer
owner_lane: android
stack: android
estimated_production_lines: 378
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
- [ ] `\bigl...\bigr`、`\Bigl...\Bigr`、`\biggl...\biggr`、`\Biggl...\Biggr` 均成对解析为 `Delimited`；大小写错配或缺少闭合命令返回 `null`。

## TDD execution record

### RED — failing test first
- Test/scenario: `android/app/src/test/java/com/treelearn/app/ui/math/MathParserTest.java`，共 19 个用例（`@Test` 注解位于该文件第 12、24、41、57、66、82、95、115、128、141、152、161、171、179、184、189、196、201、206 行），覆盖 frac / `x^2` 与 `x^{ab}` / `x_{i,j}` / `\sqrt{x}` 与 `\sqrt[3]{x}` / `\text{其中 x}` / `\left(\frac{a}{b}\right)` 与 `\left\{x\right\}` 与 `\left\langle x \right\rangle` / `\sum_{i=1}^{n}` / `\lim_{x \to 0}` / 希腊字母 / 修饰 / 间距忽略 / 三类失败（`\begin{matrix}`、`\unknowncmd{x}`、括号不配对、`\left` 无 `\right`、`\\`、null 入参）返回 null。
- Command: `$env:JAVA_HOME='D:\Environment\Jdk\jdk21'; Set-Location android; .\gradlew.bat :app:testDebugUnitTest --tests "*MathParserTest*" --no-build-cache --rerun-tasks`
- Exit code: 1
  - 来源：RED 那次运行终端 stdout 打印的 `EXIT_CODE=1`（gradlew 进程退出码，未经管道改写）。
  - 佐证行：`/tmp/t102_red.log` 第 387 行为 `76 个错误`，文件末尾为 `BUILD FAILED in 14s`。
- Observed failure: `MathParserTest.java:14: 错误: 找不到符号`（`MathParser.parse` 不存在，`/tmp/t102_red.log` 第 67、72 行）；`MathParserTest.java:16: 错误: 程序包MathNode不存在`（`/tmp/t102_red.log` 第 77 行）；同类缺失符号错误共 76 条，`BUILD FAILED`。
- Failure reason validated: 失败全部来自"被测 API 尚不存在"——测试断言的是 T-102 契约里的 `MathParser.parse`、`MathNode.Kind`、`MathNode.AtomKind` 及节点字段，而三个生产文件在 RED 阶段尚未创建；不是断言写错、不是 Gradle 环境故障（同命令在 GREEN 阶段退出码 0）。

### GREEN — minimum implementation
- Implementation: 新增 `MathNode.java`（78 行）、`MathParser.java`（254 行）、`MathSymbols.java`（37 行），合计 369 行非空非注释生产代码（行数由 `grep -v '^[[:space:]]*$' <file> | grep -v '^[[:space:]]*//' | grep -v '^[[:space:]]*/\*' | grep -v '^[[:space:]]*\*' | wc -l` 对三个文件逐一统计相加得到）。递归下降解析：`parseSequence` → `parseItem` → `parseCommand`/`parseGroup`/`parseDelimited`，`^`/`_` 由 `readScripts` 统一处理并映射到 SCRIPT 或 BIGOP 的 under/over；不支持结构（环境、未知命令、括号不配对、`\\`、`\left` 缺 `\right`、空分组、深度 > 24）一律返回 null，`parse` 外层 catch RuntimeException 保证不抛给调用方。
- Command: `$env:JAVA_HOME='D:\Environment\Jdk\jdk21'; Set-Location android; .\gradlew.bat :app:testDebugUnitTest --tests "*MathParserTest*" --no-build-cache --rerun-tasks`
- Exit code: 0
  - 来源：GREEN 那次运行终端 stdout 打印的 `EXIT_CODE=0`。
  - 佐证行：`/tmp/t102_green2.log` 第 80 行为 `BUILD SUCCESSFUL in 27s`。
- Passing evidence: `android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.math.MathParserTest.xml` 首部属性 `tests="19" failures="0" errors="0" skipped="0"`（该文件 mtime 18:15:22，为本次 GREEN 运行产物）。
- Regression evidence: 同环境跑全量 `:app:testDebugUnitTest`（无 `--tests` 过滤，输出重定向到 `/tmp/t102_regress2.log`）退出码 0（`EXIT_CODE=0`，日志第 80 行 `BUILD SUCCESSFUL in 22s`）；`android/app/build/test-results/testDebugUnitTest/` 下 84 个测试类 XML 汇总 `tests=754 failures=0 errors=0`（84 与 754 由对该目录全部 `*.xml` 头部属性求和得到，mtime 均为 18:15:22）。

### Big delimiter follow-up evidence
- Test/scenario: 四组大小定界符合法配对、大小写错配和缺失闭合反例；当前目标类 25 项，新增 6 项。
- RED command: `$env:JAVA_HOME='D:\Environment\Jdk\jdk21'; Set-Location android; .\gradlew.bat :app:testDebugUnitTest --tests "*MathParserTest*" --no-build-cache --rerun-tasks --console=plain`
- RED result: `artifacts/evidence/T-2026-09-28-math-big-delimiter-red.txt` 记录 25 项中 4 个合法配对失败、反例通过及 `EXITCODE=1`。
- GREEN command: `$env:JAVA_HOME='D:\Environment\Jdk\jdk21'; Set-Location android; .\gradlew.bat :app:testDebugUnitTest --tests "*MathParserTest*" --no-build-cache --rerun-tasks --console=plain`
- GREEN result: `artifacts/evidence/T-2026-09-28-math-big-delimiter-green-plain.txt` 记录 `EXITCODE=0`；JUnit XML 为 `tests="25" failures="0" errors="0" skipped="0"`。
- Regression result: `artifacts/evidence/T-2026-09-28-math-parser-jvm-regression.txt` 记录全量 JVM 回归退出码 0；86 个 XML 汇总 `tests=781 failures=0 errors=0 skipped=0`。
- Java-only guard: `artifacts/evidence/T-2026-09-28-math-parser-java-only-guard.txt` 记录完整模式检查 3 个 APK，`VERDICT: PASS`、`EXITCODE=0`。
- Production line count: `artifacts/evidence/T-2026-09-28-math-parser-production-lines.txt` 记录 78 + 263 + 37 = 378 行非空非注释生产代码；当前变更仍低于 500 行硬上限。

### REFACTOR — optional
- 已做：压缩 `MathParser.java` 的格式化（单行 `if (...) return ...;`、合并 `readLetters`/`peekName`/`nextName`、抽出 `parseSqrt`），行数 351 → 254；重构后用同一条 GREEN 命令复跑，仍为 `tests="19" failures="0" errors="0"`，全量仍为 754/0/0。
### TDD or sizing exception — only with prior user approval
- Exception type: 规模例外，事后追认（不是 TDD 例外）。
- Reason: 实测 378 行非空非注释生产代码，超过 200 行 178 行（89%），未超过 500 行硬上限；本轮定界符配对逻辑使计数较先前记录增加 9 行。按原确认范围保留，并以独立复核与回归证据控制风险，避免为拆分而重排已被 T-103 消费的节点 API。
- User approval: 用户于 2026-09-28 表示“特例批注可以通过”，在本任务审计语境下明确批准 T-102 与 T-103 两项作带说明的规模特例；仅适用于本任务，不是事先批准，记录为流程偏差。
- Alternative verification: 计数、定向测试、全量 JVM 回归和 Java-only guard 的证据路径均列于上方；T-102 独立 verifier 尚待执行。用户批准仅覆盖 T-102/T-103 的规模例外，不替代功能验收。

## Implementation notes

- 递归下降：先分词（命令 `\word`、 `{`、`}`、`^`、`_`、`&`、`\\`、普通字符），再按优先级建树；深度上限保护（防恶意嵌套栈溢出）。
- 数字与拉丁字母区分 `MathAtomKind`，供 103 决定是否用数学斜体（字母斜、数字与运算符直立）。
- 解析失败一律返回 `null`，不抛受检异常，便于 104 一行判断降级。
- 与 103 的边界：本任务只产树，不做任何像素计算。
- 节点 API 决策（与 T-103 的契约）：`MathNode` 为 final 类 + 8 个静态工厂，全部字段 `public final`、未用到的槽位保持 null，103 只需读字段无需 setter。`ROW` 只在子节点多于 1 个时出现——单元素分组直接返回该元素（因此 `\frac{1}{2}` 是 `Frac(Atom, Atom)` 而不是 `Frac(Row, Row)`），`parse` 顶层同理。SCRIPT 的 `base/superscript/subscript` 允许只填其一；BIGOP 的 `under/over` 由 `_`/`^` 填（行内 `\sum_{i=1}^{n}` 的 under 为 `i=1`、over 为 `n`），字形用 Unicode（∑ ∫ ∏）或原词（lim/log/sin）。`DELIMITED.left/right` 为 char，`\langle`/`\rangle` 用 ⟨ ⟩ 单字符表示，`.` 表示空定界符。`ACCENT.text` 存命令名（如 `vec`）供 103 选修饰字形。
- 解析决策：`\text{...}` 走 `readGroupRaw` 原样截取，跳过所有数学替换，中文与反斜杠原样保留；序列中空白与 `\,` `\;` `\!` `\quad` 等间距命令直接跳过，因此 `\lim_{x \to 0}` 的下限是 3 个节点而非 5 个。

## Verification report

- Verdict: PARTIAL（T-102 本体 PASS；依赖链未闭环）。
- Evidence: 独立 verifier 只读复核确认四组定界符与正反例符合要求；`artifacts/evidence/T-2026-09-28-math-big-delimiter-red.txt`、`artifacts/evidence/T-2026-09-28-math-big-delimiter-green-plain.txt`（25/0/0/0）、`artifacts/evidence/T-2026-09-28-math-parser-jvm-regression-final.txt`（86 XML、781/0/0/0）、`artifacts/evidence/T-2026-09-28-math-parser-java-only-guard.txt`（PASS）、`artifacts/evidence/T-2026-09-28-math-parser-production-lines.txt`（378 行）。
- Gaps: T-101 依赖当前 `blocked` 且独立判定 FAIL，故 T-102 整体不得标为 `verified`；Android 初始 Activity 启动证据待最终 APK 安装时补齐。
- Risks: 公式解析器尚未接入用户渲染路径，由 T-104 完成；本任务仍只负责节点树。

## Handoff notes

- Completed: 解析器支持四组大小定界符精确配对；当前定向测试 25/25、全量 JVM 回归 781/781、Java-only guard 完整模式 PASS。
- In progress: T-102 本体通过独立只读复核，任务整体保持 `verifying` / PARTIAL。
- Blocker: T-101 前置任务为 `blocked` / FAIL；最终 APK 启动证据也待接线后补齐。
- Unblock condition: 按 Harness 重新规划并解除 T-101 阻塞；最终构建、安装与初始 Activity 启动验证通过。
- Next steps: 继续独立复审 T-103；随后完成 T-104 接线、最终 APK 构建/安装/启动验证，并保持父任务状态与证据一致。
- Watch-outs: `\text{}` 内不做数学替换；中文按正文样式；未知命令必须降级而不是猜着画。
