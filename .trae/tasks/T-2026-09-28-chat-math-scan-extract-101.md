# Treelearn Harness Task: 公式扫描抽取与占位

id: T-2026-09-28-101
title: 公式扫描抽取与占位
template_version: 2
task_type: implementation
status: blocked
parent_requirement: T-2026-09-28-chat-math-formula-rendering-100.md
requirements_confirmed: true
generated_by: leader
owner: android-math-implementer
owner_lane: android
stack: android
estimated_production_lines: 270
shared_code_touched: false
depends_on: [T-2026-09-28-chat-math-formula-rendering-100.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 T-2026-09-28-100（对话回答数学公式渲染，用户 2026-09-28 四项拍板：四种定界符都支持 / 降级为等宽底色源码 / 流式未闭合显示源码 / 朗读跳掉公式；走 Harness；自绘 ReplacementSpan，不引入依赖）。
- Problem/background: 公式源码今天直接进 commonmark，先被行内语法改写（`*` 变强调、`\_`/`\\` 被转义）才显示。必须在解析前把公式抽出来占位。
- Expected outcome: 一个可单测的扫描器：抽出四种定界公式，输出占位后的 markdown 与公式清单；不该抽的（代码块、行内代码、货币、未闭合）原样留在文本里。
- Constraints and trade-offs: Java-only；占位符必须由 ASCII 字母数字组成、不含 markdown 特殊字符（commonmark 规范会把 U+0000 替换为 U+FFFD，故不可用 NUL 做哨兵）；不处理 `$$` 跨段落的复杂嵌套。
- User-confirmation decisions: 四种定界符全认；未闭合按源码直出（流式不闪）。
- Facts: `ChatScreen.java:1075` 调用 `MarkdownRenderer.renderSegments`；`readTable` 单元格走 `renderChildren`，故表格内公式同样要经过本扫描器。
- Risks: 货币误判；围栏代码块与行内代码识别不准。
- Deferred: `\begin{}` 环境（不是扫描层职责，102 解析后降级）。

## Goal

从答案 markdown 中识别 `$...$`、`$$...$$`、`\(...\)`、`\[...\]` 四种定界公式，替换为稳定占位符，并返回公式清单（块级/行内 + LaTeX 源码）；未闭合、代码块内、行内代码内与货币写法一律不抽取。

## Scope

### In scope
- 新增 `android/app/src/main/java/com/treelearn/app/ui/math/MathScanner.java` 与同包数据类 `MathFormula.java`（`boolean block`、`String latex`、`String placeholder`）。
- 静态入口 `MathScanner.scan(String markdown)` 返回 `MathScanResult`（`String masked`、`List<MathFormula> formulas`）。
- 规则：跳过 ``` 围栏代码块与缩进代码块、跳过行内反引号代码；`$$` 优先于 `$`；行内 `$...$` 不得跨空行、闭合 `$` 前不得为空白、内容须含至少一个数学特征字符（`\`、`^`、`_`、 `{`、`}`）以避开 `$5 and $10`；`\(...\)` 与 `\[...\]` 同理（`\[` 为块级）；未闭合的定界符不抽取。
- 占位符形式：`TLMATH<index>TL`（大写字母数字，无 markdown 元字符）。

### Out of scope
- LaTeX 语法解析（102）；布局与绘制（103）；接入渲染与降级（104）；表格单元格特殊处理（随通用路径走）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增纯函数工具类，**不接进渲染链路**（104 才接），故对现有答案渲染零行为影响；保留现有文本原样输出语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者为答案 markdown；本期无消费者（104 起 `MarkdownRenderer` 消费）；同级入口 `ReasoningDisclosure`（思维链）与 `ChatSpeaker`（朗读）本期不调用。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 本期新增未接线的纯函数，无用户可见行为变更；仅以单测证明。

## Documentation impact record

- Changed runtime facts: 无运行时事实变化（工具类未接线）。
- Authoritative code/config/migration evidence: 新增 `android/app/src/main/java/com/treelearn/app/ui/math/MathScanner.java`、`MathFormula.java`（本任务创建前尚不存在）。
- Documentation surfaces checked: `android/README.md`（无需改动：未新增依赖、未改构建）。
- Documentation files updated: 无（文档回写集中在 T-104）。
- Producer/consumer/sibling/compatibility paths checked: `MarkdownRenderer`（未接线）、`ChatScreen`（未接线）。
- Superseded phrases/examples reverse-searched: 无历史表述涉及扫描器。
- `documentation impact: none` reason and evidence: 新增未接线的包内工具类，不改变任何运行时行为或构建面；证据为本任务不修改 `MarkdownRenderer.java`/`ChatScreen.java`/`build.gradle`。
- Historical/PRD handling: 不涉及。

## Acceptance criteria

- [x] `scan` 对 `$$E=mc^2$$` 抽出 1 条 block 公式，`masked` 中对应位置为占位符且不含 `$`。（`MathScannerTest.blockDoubleDollarIsExtractedAsBlockFormula`）
- [x] 行内 `设 $x^2+y^2=r^2$ 可得` 抽出 1 条行内公式，两侧中文原样保留。（`MathScannerTest.inlineDollarKeepsSurroundingChineseText`）
- [x] `\(a+b\)` 与 `\[a+b\]` 分别识别为行内与块级。（`MathScannerTest.backslashDelimitersAreRecognized`）
- [x] 代码块 ``` 内的 `$x$` 与行内 `` `$x$` `` 不被抽取，`masked` 中原样保留。（`MathScannerTest.fencedCodeBlockIsLeftUntouched` / `tildeFencedCodeBlockIsLeftUntouched` / `inlineCodeSpanIsLeftUntouched` / `indentedCodeBlockIsLeftUntouched`）
- [x] `价格 $5 到 $10` 不被抽取（内容无数学特征字符）。（`MathScannerTest.currencyAmountsAreNotExtracted`）
- [x] 未闭合 `$$\frac{1` 不被抽取，原文保留（流式场景）。（`MathScannerTest.unterminatedDelimiterIsLeftAsSource`；另有 `inlineDollarDoesNotCrossBlankLine` 覆盖跨空行）
- [x] 占位符不含 `\`、`*`、`_`、`` ` ``、`$`、`[`、`]` 等 markdown 元字符；同一输入两次 `scan` 结果稳定（幂等）。（`MathScannerTest.placeholdersAreAsciiSafeAndIndexed` / `scanIsIdempotentOnMaskedOutput`）
- [ ] 初始 13 项 JVM 单测有 RED→GREEN；后补 5 项当前通过但无先行 RED 记录，因此 TDD 验收未闭环。最新全量回归 781/0/0/0，详见最终证据；本任务未接线 `MarkdownRenderer`。

## TDD execution record

### RED — failing test first
- Test/scenario: `android/app/src/test/java/com/treelearn/app/ui/math/MathScannerTest.java` 的 13 个 JUnit4 用例：`blockDoubleDollarIsExtractedAsBlockFormula`（`$$` 块级）、`inlineDollarKeepsSurroundingChineseText`（`$...$` 行内 + 中文上下文）、`backslashDelimitersAreRecognized`（`\(...\)` 行内 / `\[...\]` 块级）、`doubleDollarWinsOverSingleDollar`（`$$` 先于 `$`）、`fencedCodeBlockIsLeftUntouched`（``` 围栏）、`tildeFencedCodeBlockIsLeftUntouched`（~~~ 围栏）、`indentedCodeBlockIsLeftUntouched`（4 空格缩进）、`inlineCodeSpanIsLeftUntouched`（`` `...` ``）、`currencyAmountsAreNotExtracted`（`价格 $5 到 $10`）、`unterminatedDelimiterIsLeftAsSource`（`$$\frac{1` 流式未闭合）、`inlineDollarDoesNotCrossBlankLine`、`placeholdersAreAsciiSafeAndIndexed`（`TLMATH<index>TL` 且无 markdown 元字符）、`scanIsIdempotentOnMaskedOutput`。用例数 13 来自 `android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.math.MathScannerTest.xml:2` 的 `tests="13"`（与该文件 `<testcase` 行数 13 一致）。
- Command: cd /d/Project/Android/Treelearn/android && JAVA_HOME=/d/Environment/Jdk/jdk21 ./gradlew --no-build-cache --project-cache-dir /d/Project/Android/Treelearn/android/.gradle-t101 :app:testDebugUnitTest --tests "*MathScannerTest*"
- Exit code: 1
- Observed failure: `android/app/build/tdd-t101/red-run.log:71` 起为 `D:\Project\Android\Treelearn\android\app\src\test\java\com\treelearn\app\ui\math\MathScannerTest.java:14: 错误: 找不到符号` / `符号: 类 MathScanResult`；同类错误涵盖 `MathScanner`、`MathFormula`；`red-run.log:246` 为 `35 个错误`，`red-run.log:254` 为 `> Compilation failed; see the compiler output below.`，`red-run.log:442` 为 `BUILD FAILED in 15s`。退出码 1 取自 `red-run.log:444` 的 `EXIT=1`。
- Failure reason validated: 失败原因是测试引用的三个生产类型（`MathScanner` / `MathFormula` / `MathScanResult`）此刻尚未创建，编译期失败，符合 RED 先行的预期；不是断言写错、不是构建环境故障（同一命令在 GREEN 阶段退出码为 0，见下）。

### GREEN — minimum implementation
- Implementation: 新增 `android/app/src/main/java/com/treelearn/app/ui/math/MathScanner.java`（单遍扫描主逻辑）、`MathFormula.java`（`block`/`latex`/`placeholder` 三个 final 字段）、`MathScanResult.java`（`masked`/`formulas`）。生产代码非空非注释行数：MathScanner 234（`android/app/src/main/java/com/treelearn/app/ui/math/MathScanner.java`，258 总行 - 16 空行 - 8 注释行）、MathFormula 11（22 - 5 - 6）、MathScanResult 11（20 - 5 - 4），合计 256 行。纯 Java，无 Android API、无新 Gradle 依赖、无正则。
- Command: cd /d/Project/Android/Treelearn/android && JAVA_HOME=/d/Environment/Jdk/jdk21 ./gradlew --no-build-cache --project-cache-dir /d/Project/Android/Treelearn/android/.gradle-t101 :app:testDebugUnitTest --tests "*MathScannerTest*"
- Exit code: 0
- Passing evidence: 退出码 0 取自 `android/app/build/tdd-t101/green-run.log:81` 的 `EXIT=0`，同文件 `green-run.log:79` 为 `BUILD SUCCESSFUL in 20s`、`green-run.log:69` 为 `> Task :app:testDebugUnitTest`（测试任务真实执行）。用例结果取自 `android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.math.MathScannerTest.xml:2`：`tests="13" skipped="0" failures="0" errors="0"`（该文件第 3 行起 13 个 `<testcase>` 均无 `<failure>`/`<error>`）。
- Regression evidence: 同一 Gradle 命令去掉 `--tests` 过滤跑全量 JVM 单测，退出码 0 取自 `android/app/build/tdd-t101/regression-run.log:82` 的 `EXIT=0`（`regression-run.log:80` 为 `BUILD SUCCESSFUL in 20s`）；汇总取自 `android/app/build/tdd-t101/regression-summary.txt:1` 的 `suites=83 tests=730 skipped=0 failures=0 errors=0`（由 `android/app/build/test-results/testDebugUnitTest/*.xml` 各 `<testsuite>` 属性聚合得出）。本任务未接线 `MarkdownRenderer`，`MarkdownRendererInstrumentedTest` 是 androidTest，不在该 JVM 任务内，未被本任务改动触及。

### REFACTOR — optional
- Cleanup: 主会话复核（非子代理自评）发现两处缺陷并修复，另放宽一处判定；改动落在 GREEN 之后，故记在本段而非 GREEN。
  1. `MathScanner.scan` 的换行分支没有把 `lineStart` 复位为 `true`（`android/app/src/main/java/com/treelearn/app/ui/math/MathScanner.java:49-53`）→ 只有**首行**会检测围栏/缩进代码块；正文之后的 ``` 代码块里的 `$x^2$` 会被误抽成公式。原 13 个用例的围栏/缩进样本全在文本开头，故未暴露。修复：换行分支加 `lineStart = true;`。
  2. `findClosing` 遇到「前一字符为空白」的候选闭合时**继续往后找**（`MathScanner.java:128-135`）→ `$x^2 $ 和 $y^2$` 会把中间正文吞成一个 latex 为 `x^2 $ 和 $y^2` 的公式。修复：遇到不可用候选即 `return -1`，放弃该段（`$x^2 $` 原样保留），后续 `$y^2$` 仍独立抽取。
  3. 判定放宽：`consumeDollar` 由 `hasMathSignal(latex)` 改为 `hasMathSignal(latex) || isAsciiToken(latex)`，新增 `isAsciiToken`（≤12 的可打印 ASCII 且无空白）。理由：仅靠数学特征字符会把 `$a+b$`、`$x$` 这类最常见的行内式留在源码态，与用户「都支持」的拍板不符；中文货币 `$5到$10` 因含非 ASCII 仍被拒绝（无空白可依托，靠字符范围挡住）。
- 新增 5 个用例覆盖上述三处：`fencedCodeBlockAfterProseIsLeftUntouched`、`indentedCodeBlockAfterProseIsLeftUntouched`、`spaceBeforeClosingDollarAbandonsOnlyThatRun`、`shortAsciiInlineIsExtractedWithoutSignalCharacter`、`chineseCurrencyWithoutSpaceIsNotExtracted`。
- Command: cd /d/Project/Android/Treelearn/android && JAVA_HOME=/d/Environment/Jdk/jdk21 ./gradlew :app:testDebugUnitTest --tests "*MathScannerTest*" --no-build-cache
- Exit code: 0
- Regression evidence: 退出码 0 与 `BUILD SUCCESSFUL in 2m 45s` 取自主会话本轮构建输出；用例汇总取自 `android/app/build/test-results/testDebugUnitTest/TEST-com.treelearn.app.ui.math.MathScannerTest.xml:2` 的 `tests="18" failures="0"`（原 13 + 新增 5，`grep -c '<testcase'` 得 18）。生产代码非空非注释行数复核为 270（MathScanner 248 + MathFormula 11 + MathScanResult 11，由 `grep -v '^[[:space:]]*$' | grep -v '^[[:space:]]*\*' | grep -v '^[[:space:]]*//' | grep -v '^[[:space:]]*/\*' | wc -l` 得），超 200 软上限 35%，按任务契约第 45 行记为警告级，未申请规模例外。

### TDD or sizing exception — only with prior user approval
- 无例外。

## Implementation notes

- 包 `com.treelearn.app.ui.math`；纯 Java，无 Android API 依赖（除零依赖），便于 `src/test` JVM 单测。
- 占位符设计为 102/103 回填的锚点：104 在 `SpannableStringBuilder` 中按占位符字符串定位并替换为 `MathSpan`。
- 单文件扫描、单遍推进，避免正则回溯；不用 `String.split` 处理嵌套围栏。
- 反例测试优先（货币、未闭合、代码块）再实现，符合 RED 先行。
- 结构决策：`MathScanResult` 做成同包独立文件 `MathScanResult.java`（而不是 `MathScanner` 的静态嵌套类）。理由：`scan` 的返回类型是 102/104 都要引用的公共 API，独立文件让 `MathFormula` / `MathScanResult` / `MathScanner` 三者在包级别平铺，104 接线时 `import com.treelearn.app.ui.math.MathScanResult;` 与 `MathFormula` 用法一致，不必写 `MathScanner.MathScanResult` 这种限定名；`formulas` 用 `Collections.unmodifiableList` 包装，避免消费方误改顺序（顺序即占位符下标）。
- 算法要点：外层按行首状态机推进 —— 行首先判 4 空格/制表符缩进代码块（整行原样复制）与 ``` / ~~~ 围栏（找同级闭合围栏，找不到就复制到文末）；行内先处理反引号代码段（等长反引号串配对，找不到则原样输出）；再处理 `\`（`\(...\)` 行内、`\[...\]` 块级，未闭合则按字面 `\` 前进 1）；最后处理 `$`（先试 `$$...$$` 块级，再试 `$...$` 行内）。
- 判定细节：`$...$` 行内的闭合位置用 `findClosing(..., requireNonSpaceBefore=true)` 逐字符推进，闭合 `$` 前一字符为空白则继续往后找；遇到空行（换行后仅空白再换行）直接判失败；内容须含 `\ ^ _ { }` 之一（`$5 到 $10` 两条规则都被拒绝：无特征字符且闭合前是空格）。`$$` / `\[` 为块级且允许跨行，`\(...\)` 不允许跨空行。
- 未闭合一律回退为字面输出（`$$` 前进 2、`$` 前进 1、`\(`/`\[` 前进 1 输出 `\`），保证流式半截输出原样显示、不产生跳动。
- 占位符：`TLMATH` + `formulas.size()` + `TL`，只含 ASCII 大写字母与数字，不含反斜杠、星号、下划线、反引号、美元符、方括号等 markdown 元字符，也不使用 U+0000（commonmark 规范会把 NUL 替换为 U+FFFD）。
- 转义兼容：`\$`、`\\`、`` \` `` 视为转义字面量原样输出，不会被误当成定界符。
- 幂等：`masked` 中不再含任何定界符，故对 `masked` 二次 `scan` 的 `formulas` 为空且 `masked` 不变（有单测覆盖）。
- 构建环境备注：本机 `gradlew` 在 `android/` 子目录（仓库根目录没有），故命令 `cd` 到 `android/`；并发工作线占用 `android/.gradle` 的 `fileHashes.lock`（`拒绝访问`），故加 `--no-build-cache` 并用独立 `--project-cache-dir android/.gradle-t101`；这两个前缀属于环境适配，未放宽任何断言。

## Verification report

- Verdict: FAIL
- Evidence: 独立 verifier Russell 两轮只读复核；父任务 `.trae/tasks/T-2026-09-28-chat-math-formula-rendering-100.md` 第 27、31、96 行限定单任务 ≤200 行且例外只覆盖 T-102/T-103；`artifacts/evidence/T-2026-09-28-math-scanner-production-lines.txt` 实测 270 行；`artifacts/evidence/T-2026-09-28-math-parser-jvm-regression-final.txt` 退出码 0，`MathScannerTest.xml` 为 18/0/0/0，全量 86 XML 为 781/0/0/0；`artifacts/evidence/T-2026-09-28-math-parser-java-only-guard.txt` 为 PASS。
- Gaps: T-101 超出父任务 ≤200 行范围 70 行，且没有获批例外；5 个后补测试没有先行 RED 证据。最终 18 项 XML 缺失已由本轮全量回归补齐，但不能补正上述规模与 TDD 流程缺口。
- Risks: T-102 依赖 T-101；在此任务重新规划并通过复核前，后续任务不得标为 verified，父任务也不得报完成。

## Handoff notes

- Completed: Scanner 当前 18 项定向测试与全量 781 项 JVM 测试通过；完整日志与 XML 已刷新。
- In progress: 代码功能已具备，但任务验收被独立 verifier 判 FAIL，等待重新规划。
- Blocker: 生产代码 270 行超过父任务 200 行上限；后补 5 项测试缺先行 RED，不能事后伪造或追认。
- Unblock condition: 重新规划符合父任务规模/TDD 契约的任务范围，并完成相应实现与独立复核；仅追加规模例外不能补正 TDD 缺口。
- Next steps: 保留本任务 FAIL；后续任务可继续做功能性复核与 APK 交付，但不得将依赖 T-101 的任务或父需求标记为 verified/complete。
- Watch-outs: 占位符必须 ASCII 安全；`$$` 须先于 `$` 匹配；`\[` 为块级不要当行内；104 接线时占位符定位要按 `TLMATH<index>TL` 全字匹配，避免把正文里的同形字符串误替换。
