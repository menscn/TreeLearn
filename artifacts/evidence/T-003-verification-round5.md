# T-003 第 5 轮独立复核报告（verifier: 独立会话，非交付方）

- 任务文件：`.trae/tasks/T-2026-09-20-model-immutable-answer-domain-003.md`（T-2026-09-20-003，status: `verifying`）
- 复核范围：① 第 5 轮常规复核；② **N10 专项重新覆盖**（`GenerationState` 删边 + 删 `DomainErrorCode.OPERATION_TERMINAL`）
- 复核时间：2026-09-21（本机时区 UTC+8）
- 工作目录：`D:\Project\Android\Treelearn`（Windows 11 / Git Bash；PowerShell 未用于本轮探针）
- 探针落点：`D:\Project\_vtmp\`（**仓库之外**，见 §5-G4）。本报告只新增自身的探针文件与报告文件，**未修改任何生产代码、测试代码或任务文件**。
- 交付方证据一律视为「待核事实」；下文每条结论后均附本轮自跑的命令与输出片段。

---

## 1. Verdict

**FAIL**（**文档 / 自述层失败，不是行为层失败**）

三点必须先说清楚，避免误读：

1. **行为层全部通过。** T-003 的 5 条验收条件、以及 N10 的两处删除，本轮均被**独立复现为真**：全库 `:core:test --rerun-tasks` → **121 用例 / 0 失败 / 0 错误 / 0 跳过 / exit 0**；自写 29 条验收探针 29/29 PASS；自写可达性探针在 **402 234** 条序列上未找到任何从 `STREAMING_REASONING` 稳定化的路径；`:core` 纯净性守门经**注入违规**验证为真守门（exit 1 并逐行点名）。
2. **N10 成立。** 被删的边确不可达；被删断言有等价覆盖；9 个错误码全部有生产者、无多余；83 个既有用例在**方法名层面** 82 条逐字保留 + 1 条已声明的改名。
3. **FAIL 由三件事驱动**，都可一次性修完、且都不涉及领域逻辑：
   - `android/core/src/main/java/com/treelearn/core/domain/Branch.java:77` 的 `{@link DomainErrorCode#BRANCH_SOURCE_INVALID}` **悬空**，`:core:javadoc` 实测 **exit 1**（`error: reference not found`）——这是「当前态文档与生产事实矛盾」，`documentation.md` 明确要求校验器 **必须返回 FAIL**；
   - 任务文件的**当前态自述与实测不一致**（用例数 83/116 → 实测 86/121；生产代码行 741/1301 → 实测 762/1403；「三处偏离」→ 实际只剩两处）；
   - 规模例外（生产代码 762 行 vs 500 行硬上限）**未获用户追认**，`harness-tasks.md:45` 的字面口径即「失败」。

**若把三件事都修掉并取得用户对规模例外的批准，本报告可翻为 PASS；此外无任何行为性阻塞。** 详见 §6。

---

## 2. 逐项核实

### 2.1 环境与基线

| 项 | 命令 | 观察输出 | 结论 |
| :--- | :--- | :--- | :--- |
| 全库测试真实执行 | `export JAVA_HOME='D:\Environment\Jdk\jdk21'; cd android; ./gradlew --no-daemon --console=plain :core:test --rerun-tasks` | `BUILD SUCCESSFUL in 13s` / `3 actionable tasks: 3 executed` / `EXITCODE=0` | **成立**（`--rerun-tasks` 强制真跑） |
| 逐类用例数（BOM 安全解析） | `python3 -c "...open(p,'rb').read().decode('utf-8-sig')..."` 于 `core/build/test-results/test/TEST-*.xml` | `AnswerRevisionTest 11 / BranchSourceTest 12 / BranchTest 12 / GenerationCommandTest 8 / GenerationOperationTest 20 / GenerationStateTest 15 / GuardTest 6 / MessageTest 13 / PromptPurposeTest 11 / PromptSpecTest 7 / TopicTest 6` → `TOTAL tests=121 failures=0 errors=0 skipped=0 classes=11`；XML `timestamp` = `2026-09-21T03:51:26Z`（=本机 11:51，即本轮运行） | **成立**：121 用例全绿，且是本轮实测值而非交付方数字 |

### 2.2 用例拆分「83 + 38 = 121」是否成立

| 项 | 命令 | 观察输出 | 结论 |
| :--- | :--- | :--- | :--- |
| 历史 83 条 vs 当前 | `awk '/ > .* PASSED/' artifacts/evidence/T-003-green.txt \| sed 's/ > /\|/; s/ PASSED//' \| sort` 与当前 XML 名集合做差 | `old= 83  new(t003-classes)= 86`；`REMOVED: GenerationStateTest\|nullTargetIsRefused`；`ADDED: aNullTransitionTargetIsACallerBugNotABusinessFailure / aReasoningPhaseThatProducedNoAnswerCannotBecomeStable / PromptPurposeTest\|absenceOfAVersionHasOneDefinition / PromptPurposeTest\|theVersionDecisionIsExpressedAsADomainError` | **部分成立**：83 条里 **82 条方法名逐字保留**，1 条改名（N8 已声明）；当前 T-003 的 **8 个测试类实际持有 86 条** |
| 121 = ? | 同上，按类归属 | T-003 的 8 类 = 86；T-021 的 3 类 = 35（`GenerationCommandTest 8 + GenerationOperationTest 20 + PromptSpecTest 7`） | **86 + 35 = 121**。按**作者归属**则 83（T-003 原）+ 38（T-021：35 + 加在 T-003 文件里的 3 条）= 121，与 T-021 任务文件 `- Regression evidence: T-003 已有的 83 个用例全部保留并通过（121 − 38 新增 = 83）` 一致 |
| 最新证据是否过期 | `awk` 提取 `T-021-green.txt` 的 PASSED 名与当前 XML 名做集合差 | `T-021-green names = 121  current names = 121`；`only in T-021-green: []`；`only in current: []` | **成立**：自最近一次记录的运行（11:47）至今，**没有任何用例被改名/删除/新增** |

### 2.3 验收条件（T-003 Acceptance criteria）

自写探针 `D:\Project\_vtmp\AcceptanceProbe.java`，只用 `:core` 已编译类，不读交付方任何测试：

| 核实项 | 命令 | 观察输出 | 结论 |
| :--- | :--- | :--- | :--- |
| AC3 `AnswerRevision` 全字段 final | `javac -cp android/core/build/classes/java/main -d . AcceptanceProbe.java; java -cp ".;…" AcceptanceProbe` | `PASS AC3 AnswerRevision: every instance field is final` | **成立**（9 个实例字段全 final） |
| AC3 无任何 `set*` | 同上 | `PASS AC3 AnswerRevision: no set*/with* method` | **成立** |
| AC3 无公开构造器 | 同上 | `PASS AC3 AnswerRevision: every constructor is private` | **成立** |
| AC3 缺失/空白策略版本 → 错误码 | 同上（输入 `null, "", "   ", "\t", "\n"`） | `PASS AC3 AnswerRevision: null/blank/\t version -> PROMPT_POLICY_VERSION_REQUIRED` | **成立** |
| AC3 无主版本 → MISMATCH | 同上（`tl-not-a-purpose/1.0.0`、`1.0.0`、`tl-learning-answer/1.0`、`01.0.0`、ISO 时间戳） | `PASS AC3 AnswerRevision: version owned by nobody -> PROMPT_POLICY_VERSION_MISMATCH` | **成立** |
| AC3 版本原样复制 | 同上 | `PASS AC3 AnswerRevision: policy version is copied verbatim`（`tl-learning-answer/1.1.0`） | **成立** |
| AC3 `restore` 只作再水合且仍校验 | 同上 | `PASS …tolerates empty reasoning`、`PASS …non-UUID id throws` | **成立**（但见 §3-V6 的口径缺口） |
| AC2 分支来源只能来自 `STABLE` | 同上：对 9 个状态逐个调 `BranchSource.fromOperation` | `PASS AC2 BranchSource.fromOperation: ok only for STABLE, otherwise OPERATION_NOT_STABLE` | **成立** |
| AC2 构造器不可绕过 | 同上（反射） | `PASS AC2 BranchSource: no public constructor` | **成立** |
| AC2 非法迁移返回领域错误 | 同上 | `PASS AC2 transitionTo: illegal move -> ILLEGAL_STATE_TRANSITION domain error` | **成立** |
| AC3 ID 规范形式 | 同上（`1-1-1-1-1`、大写 UUID、无连字符、`m-1`、空串） | `PASS AC3 Guard.id: non-canonical UUID spellings and readable strings are refused` | **成立** |
| AC3 时间戳/锚点规则 | 同上 | `PASS BranchAnchor.ofRange: quote length must equal the offset span`；`PASS Branch: a rejected mutation leaves title/updatedAt untouched`；`PASS Branch.markSeenThrough: -1 与 latest+1 均拒`；`PASS no independent unread boolean/counter field exists` | **成立** |
| PromptPurpose 与权威文档一致 | 同上 + `grep -n "tl-root-title\|tl-branch-summary\|…" Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md` | 探针 `PASS PromptPurpose: exactly the seven documented purposes, in document order`、`PASS prefixes match the design document`；文档 §3.1 列出 7 个用途、§6 表列出 6 个前缀 + §4 首版 `tl-learning-answer/1.0.0` | **成立**（7 个用途与顺序、前缀逐字一致） |
| AC4 `:core` 无 Android/SQLite/HTTP/Kotlin | `cat android/core/build.gradle`；`grep -rn "org.jetbrains.kotlin\|-ktx" --include=*.gradle`；`find . -name "*.kt" -o -name "*.kts"` | `plugins { id 'java-library' }`、唯一依赖 `junit:junit:4.13.2`；Kotlin 坐标与 `.kt/.kts` **零命中** | **成立** |
| AC4 纯净性扫描不是空跑（**正反双向**） | ① `./gradlew verifyDependencyDirection`；② 把 `android/` 复制到 `D:\Project\_vtmp\android-inject`，在 `:core` 注入 `import android.util.Log;` 后重跑 | ① `verifyDependencyDirection: PASS … ; purity NOT yet enforced for [:tree-layout] (no sources)`（`:core` 已不在 vacuous 名单）② 副本中 `Task :verifyDependencyDirection FAILED` → `:core purity: Injected.java:3 imports the Android platform: import android.util.Log;`、`EXITCODE=1` | **成立**：`:core` 的纯净性契约已被真实强制，且守门 fail-closed |
| AC5 `:core:test` exit 0 | 2.1 第一条 | `EXITCODE=0` | **成立** |
| 规模 | 见 §2.4 | — | **不成立**（数字对不上，且超上限） |

### 2.4 规模独立复算（生产代码行）

自写 `D:\Project\_vtmp\count_lines.py`，口径与任务文件自述一致（「非空非注释代码行」）：先剥离 `/* … */` 与 `//`，再计非空行；`comment` = 剩余内容全为注释的行，`blank` = 剥离后为空的行。

| 文件 | 交付方代码行 | 本轮实测代码行 | 物理行（实测） | 注释（实测） | 空行（实测） |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Branch.java | 136 | **137** | 203 | 43 | 24 |
| GenerationState.java | 89 | 89 | 219 | 111 | 20 |
| Message.java | 86 | 86 | 125 | 26 | 14 |
| AnswerRevision.java | 75 | 75 | 124 | 42 | 8 |
| Guard.java | 65 | 65 | 148 | 71 | 13 |
| BranchSource.java | 58 | 58 | 113 | 48 | 8 |
| PromptPurpose.java | **35** | **56** | 137 | 64 | 16 |
| BranchAnchor.java | 51 | 51 | 84 | 23 | 11 |
| DomainResult.java | 46 | 46 | 70 | 12 | 13 |
| DomainError.java | 35 | 35 | 44 | 1 | 9 |
| Topic.java | 30 | 30 | 46 | 9 | 8 |
| GenerationModel.java | 17 | 17 | 31 | 8 | 7 |
| DomainErrorCode.java | **13** | **12** | 48 | 26 | 10 |
| MessageRole.java | 5 | 5 | 11 | 3 | 4 |
| **合计（14 文件）** | **741** | **762** | **1403** | **488** | **153** |

- 交叉校验口径可信：`artifacts/evidence/T-021-sizing.txt` 用的是同一口径（`Command: python (non-blank, non-comment line counts per file)`），对 `GenerationState` 记 `code= 89 comment= 111 blank= 19 physical= 219`、对 `DomainErrorCode` 记 `code= 12 comment= 26 blank= 10 physical= 48` —— **与本脚本逐格相同**。
- `PromptPurpose.java` 的 +21 完全由 T-021 解释：T-021 把 `isAbsent`（3 行）+ `versionFailureFor`（18 行）加进了这个文件，`56 − 21 = 35`，正好等于任务文件记录的值。
- 结论：**交付方自述的「唯一权威值 741 代码行 / 1301 物理行」不可复现**，实测 762 / 1403；注释与空行的合计（409/151）同样不可复现（实测 488/153）。超预算的结论不受影响，反而更差：`762 / 500 ≈ 152%`。
- **无法核对项（如实记录）**：`android/` 未被 git 跟踪 —— `git ls-files android | wc -l` = `0`，`git check-ignore -v …/Guard.java` 无输出（不是被忽略，而是从未纳入），`git log --oneline` 只有 2 个提交且都不含 android 源码。因此**无法用版本库核对任何「改了几行/哪条断言被改写」**，只能靠证据文件与探针交叉核对。这一限制对 §2.2、§2.3 的「原样保留」判定同样生效：我确认的是**方法名层面**的保留，**无法核对方法体是否被改写**。

### 2.5 文档一致性（`android/README.md` + 各类型 javadoc）

| 项 | 命令 | 观察输出 | 结论 |
| :--- | :--- | :--- | :--- |
| README 是否随 N10 更新 | `grep -n "GenerationState\|领域\|AnswerRevision" android/README.md` | 第 152 行：`` `GenerationState` … 终态封闭；只有 `STABLE` 可作分支来源；只有 `STREAMING_ANSWER` 能稳定化 `` | **成立**（README 已反映删边后的实际图） |
| README 错误码规则 | 同上 | 第 158 行：`` `DomainResult`/`DomainError`/`DomainErrorCode` … 每个错误码都必须有生产者 `` | **成立** |
| 例外清单是否只存一处 | `grep -n "三处\|例外" android/README.md`、`Guard.java:38-62` | `Guard` 类注释有完整清单与「新增同类入口必须登记」；README 第 166-169 行**又逐字列出**了 `AnswerRevision.create`/`GenerationCommand.create`/`PromptSpec.of` 三个名字 | **部分成立**（见 §3-V7） |
| `GenerationState` 偏离清单是否自洽 | `grep -rn "three deviations\|two deviations\|三处偏离\|两处偏离" android/core/src android/README.md` | 仅 `GenerationState.java:34: * three deviations are allowed here.`；`two deviations`/`两处偏离` **零命中** | **不成立**（见 §3-V1） |
| 悬空 javadoc 链接 | `./gradlew --no-daemon --console=plain :core:javadoc --rerun-tasks` | `Branch.java:77: error: reference not found` / `{@link DomainErrorCode#BRANCH_SOURCE_INVALID}` / `1 error 100 warnings` / `> Task :core:javadoc FAILED` / `EXITCODE=1` | **不成立**（见 §3-V2） |
| 编码/替换字符 | `python3` 扫描 `android/README.md` + `core/src/**/*.java` | `files scanned: 29  problems: 0` | **成立** |
| `git diff --check` 类最低验证 | —— | `android/` 未跟踪，`documentation.md:42` 要求的 `git diff --check` **对本交付面不适用**（无 diff 可查） | **不适用**（限制，见 §5） |

---

## 3. 发现表

级别口径：**中** = 与生产事实矛盾或自述与实际不符，必须修完才能判 PASS；**轻** = 不阻塞但属同类缺陷，建议修；**阻塞** = 需用户决定。

| # | 级别 | 发现 | 为什么不满足契约（引用规则原文） | 建议处置 | 最小复现命令 |
| :-- | :--- | :--- | :--- | :--- | :--- |
| **V1** | 中 | `GenerationState` 类注释第 34 行仍写 `three deviations are allowed here`，而同一清单的**第三条 bullet 自己说的是「该边已删除」**（`absent here although the diagram draws it — removed as unreachable`）。N10 自述「`GenerationState` 类注释的『三处偏离』改为『两处偏离』并写入可达性论证」——**只写了可达性论证，计数没改**。删边后实际只剩**两处**偏离（`SUBMITTING -> STREAMING_ANSWER`、`STAGED -> CANCELED`）。 | `documentation.md:5-7`「代码与文档一致性 guard …是双向的：…当前态文档变更必须用生产来源证明」；`documentation.md:42`「与活动的生产者/消费者事实相矛盾的**当前态主张**」必须 FAIL。此处的当前态主张（「允许三处偏离」）与同一 bullet、与 `canTransitionTo` 的真实边集矛盾；同时属于 `AGENTS.md` 已登记的「javadoc 声称了代码没做的事」。 | 把 `GenerationState.java:34` 改为「two deviations」（或改写为「下面两条是偏离，第三条是**对图示的一条删除**」），并保持与 bullet 数量一致；同步任务文件 N10 行的自述。 | `grep -n "three deviations" android/core/src/main/java/com/treelearn/core/domain/GenerationState.java` → `34: * three deviations are allowed here…` |
| **V2** | 中 | `Branch.java:77` 保留 `{@link DomainErrorCode#BRANCH_SOURCE_INVALID}` —— 该常量已在 N8 删除。这既是**悬空链接**，也是当前态「该枚举常量存在」的隐含断言；`:core:javadoc` 因此**真实失败**（exit 1）。同时 N8 登记的复核证据 `grep -rn "BRANCH_SOURCE_INVALID" android/` **零命中** 现已为假。 | `documentation.md:42`「与活动的生产者/消费者事实相矛盾的当前态主张」→ 必须 FAIL；`harness-tasks.md:47` 要求执行记录展示**真实观察结果**，而该 grep 的「零命中」结论已不再成立。`AGENTS.md` 亦有「证据文件必须自带命令行与退出码，否则不能自证」的同类要求。 | 把该处 `{@link DomainErrorCode#BRANCH_SOURCE_INVALID}` 改为 `{@code BRANCH_SOURCE_INVALID}`（它是**历史**事实，不是现存符号）；修完重跑 `:core:javadoc` 应为 exit 0。建议把 `:core:javadoc` 加入交付检查链，否则同类悬空链接永远无人发现。 | `cd android && ./gradlew --no-daemon --console=plain :core:javadoc --rerun-tasks`（exit 1，`error: reference not found`）；`grep -rn "BRANCH_SOURCE_INVALID" android/core/src/` |
| **V3** | 中 | **规模数字不可复现**：任务文件「唯一权威值」741 代码行 / 1301 物理行 / 409 注释 / 151 空行，同一口径实测为 **762 / 1403 / 488 / 153**。逐文件表有两格错：`DomainErrorCode` 记 13（实测 12，而 T-021 自己的证据文件也记 12）、`PromptPurpose` 记 35（实测 56，差额 21 恰为 T-021 向该文件加入的 `isAbsent`+`versionFailureFor`）。 | `harness-tasks.md:45` 的预算只算生产代码行，数字必须可信；`AGENTS.md` 失败案例库要求「任务文件里每个数字都要写成『从哪个文件的哪行得到』」并在**写完之后用同一命令回读一次**。此处回读不通过。 | 用同一脚本重算并同步：741→762、1301→1403、409→488、151→153，逐文件表改 2 格；在表头写明「T-021 已向 `PromptPurpose.java` 增加 21 行代码，故本任务口径需注明是否计入」。超预算结论不变（152%）。 | `python3 D:/Project/_vtmp/count_lines.py`（输出见 §2.4） |
| **V4** | 中 | **当前态用例数过期**：任务文件 Verification report 与 Handoff 写「全库 116 用例（含 T-021 新增 33 个）」「本任务 8 个测试类 / 83 个」；实测全库 **121**、本任务 8 类 **86**、T-021 自有类 **35**（按作者归属 T-021 = 38）。而且它引用的证据 `artifacts/evidence/T-021-green.txt`（11:47）与同轮 `T-021-green-counts.txt`（11:48）记录的正是 **121**，与正文的 116 **不同源**。同文件内部也自相矛盾：N10 行写「`GenerationStateTest` 15 用例」，而 83 的算式要求它是 14。 | `harness-tasks.md:47`（执行记录须展示真实观察结果）；`AGENTS.md`「交付自述里的计数与**证据文件**不同源 …规则：每个数字都要写成从哪个文件的哪行得到」；`documentation.md:42`「把未标注的历史文档当作当前态」→ 必须 FAIL。 | 把 116→121、33→35（并注明另有 3 条加在 T-003 文件里，故作者归属为 38）、83→86，并加注「83 指 T-003 原始写入的用例数，其中 82 条方法名逐字保留、1 条按 N8 改名」；`T-021-green.txt` 与 `T-021-green-counts.txt` 同源引用。 | 见 §2.1 / §2.2 的 Python 解析命令；`grep -n "116" .trae/tasks/T-2026-09-20-model-immutable-answer-domain-003.md` |
| **V5** | 轻 | **T-003 自有证据文件整体过期**：`T-003-green.txt`（10:44）、`T-003-checks.txt`（10:45）均早于 N8/N9/N10 修复；无一份反映当前树（8 类 86 用例；测试物理行由自述 1320 变为实测 **1400**）。当前态只能靠引用 T-021 的证据文件。 | `harness-tasks.md:47`「执行记录必须展示…相关回归证据」；`AGENTS.md`「证据文件过期」属历史命中类型。 | 修完 V1/V2 后重录 `T-003-green.txt` / `T-003-checks.txt`（带命令行、`EXITCODE=`、逐类用例数与生成时间），并在任务文件里把「83 用例证据」标注为历史快照。 | `ls -l --time-style=full-iso artifacts/evidence/T-003-*.txt`；`wc -l android/core/src/test/java/com/treelearn/core/domain/{AnswerRevision,BranchSource,Branch,GenerationState,Guard,Message,PromptPurpose,Topic}Test.java` |
| **V6** | 轻 | `AnswerRevision.restore` 与 `create` 对「合法策略版本」口径不一致：`create` 拒绝 `garbage-not-a-version` / `tl-learning-answer/01.0.0` / `"  tl-learning-answer/1.0.0  "`，而 `restore` **全部接受**（只经 `Guard.nonBlank`）。restore 的 javadoc 说「Identity fields are still validated so a corrupt row fails loudly」，对策略版本字段不成立。后果：T-004 的写路径可以落一行 `create` 会拒绝的数据，`restore` 会静默再水合。 | `documentation.md:5-7`（当前态文档须与生产事实一致）；`regression.md:14`「不得仅为让测试变绿而削弱断言」的反面同样适用：这里不是削弱测试，而是 **javadoc 的「corrupt row fails loudly」比代码更强**。 | 二选一：① 把 javadoc 明确限定为「身份字段」（策略版本不校验归属，已声明）；② 在 `restore` 里只做**格式**（非归属）校验，与 `create` 的可接受集合对齐。建议 ②，并加一条用例锁住 `create`/`restore` 的可接受集合一致。 | `java -cp "D:/Project/_vtmp;android/core/build/classes/java/main" RestoreProbe` → `create=refused/PROMPT_POLICY_VERSION_MISMATCH \| restore=accepted` |
| **V7** | 轻 | `android/README.md:166-169` 在**指向** `Guard` 类注释的同时，又**逐字列出了**三处通道例外的名称。登记在案的规则要求清单只存一处。 | `AGENTS.md` 失败案例库（2026-09-21 [domain]）：「② 例外清单**只存一处**（`Guard` 类注释），README 等文档只**指向**它、**不复制**」。 | README 该段删去三个类型名，只留「例外清单与判定正文见 `Guard` 类注释；当前共三处」并给出检索入口。 | `sed -n '164,170p' android/README.md` |
| **V8** | 阻塞 | 规模例外（**762** 生产代码行 vs 500 硬上限，≈152%）**未取得用户事前批准**；任务文件自述「待用户确认」，且第 4 轮 verifier 已明确该追认不能由执行方自行作出。 | `harness-tasks.md:45`「超过 500 行**除非已记录用户批准的规模例外**，否则为失败」；`harness-verification.md:9`「强制预先批准例外，拒绝事后批准」。 | 由**用户**决定：批准规模例外，或在后续任务中继续拆分至 ≤500 行。在此之前本任务不得判 `verified`。 | `grep -n "User approval" .trae/tasks/T-2026-09-20-model-immutable-answer-domain-003.md` → `**待用户确认**` |

**未发现的问题（已逐项排除，供对照）**：错误码生产者缺失/多余（9 个全部有生产者）；`immutableCopy`/`BRANCH_SOURCE_INVALID` 常量本身残留；Kotlin 违规；`:core` 纯净性空跑；UTF-8/替换字符；README 对 `GenerationState`/`Message`/`BranchSource`/`AnswerRevision` 的不变量描述与代码不符；`PromptPurpose` 用途名单与文档不符。

---

## 4. N10 专项复核

N10 = T-021 在实现期删除了 `GenerationState` 的 `STREAMING_REASONING -> STABLE` 边，并删除了 `DomainErrorCode.OPERATION_TERMINAL`。要求我**独立判断两处删除是否正确、是否损失 T-003 的既有语义与断言覆盖**。

### 4.1 被删的边是否真的不可达（我自己论证 + 穷举）

**论证（不引用 T-021 的理由）**：`STABLE` 的唯一实际到入口是 `GenerationOperation.stabilize`，它在返回成功前要求 `partialAnswer.trim()` 非空。`partialAnswer` 的**唯一写入点**是 `appendAnswer`（`grep -n "partialAnswer" GenerationOperation.java` → 声明 52、读 88、判空 92、append 183），而该写入点在 append 之前把状态推进到 `STREAMING_ANSWER`：

```java
180:        if (state == GenerationState.SUBMITTING || state == GenerationState.STREAMING_REASONING) {
181:            state = GenerationState.STREAMING_ANSWER;
182:        }
183:        partialAnswer.append(delta);
```

状态**单调向前**（`canTransitionTo` 从 `STREAMING_ANSWER` 只指向 `STABLE`/终态；`appendReasoning` 在 `STREAMING_ANSWER` 时只追加不改状态，`:148-155` 与 `:180-185`）。因此「非空回答」在所有可达点上**蕴含** `state == STREAMING_ANSWER`。

**穷举（自写探针）**：`D:\Project\_vtmp\N10Probe.java` 把 `appendReasoning("r"|"")`、`appendAnswer("a"|"")`、`advanceTo(每个状态)` 作为动作字母表，对**深度 ≤ 5 的全部前缀**逐一在新 `GenerationOperation` 上重放，并在每个前缀上调用 `stabilize`：

```
sequences explored (prefixes included) : 402234
prefixes whose stabilize(...) returned ok: 894
states observed just before a successful stabilize: [STREAMING_ANSWER]
prefixes with a non-empty answer buffer outside STREAMING_ANSWER: 0

STREAMING_REASONING.canTransitionTo(STABLE) = false
STREAMING_REASONING.transitionTo(STABLE).isOk() = false , code=ILLEGAL_STATE_TRANSITION
states with an edge into STABLE             : [STREAMING_ANSWER]

reasoning-only stream: state=STREAMING_REASONING answer=<> stabilize.isOk=false code=OPERATION_NOT_COMPLETABLE
reasoning then answer: after appendReasoning=STREAMING_REASONING, appendAnswer→稳定化 ok=true, reasoning kept=true
```

四条独立结论：
1. **894 次成功稳定化全部来自 `STREAMING_ANSWER`，零反例**；
2. **不变量「非空回答 ⇒ 已在 `STREAMING_ANSWER`」在 402 234 个可达点上零违反** —— 这比任何深度上界都强，故**与搜索深度无关**；
3. 图中**只有 `STREAMING_ANSWER` 有指向 `STABLE` 的边**；
4. 该边**唯一可能服务的那条运行**（只有推理、没有回答的流）被 §9.3 的要求挡住：`OPERATION_NOT_COMPLETABLE`。

⇒ **被删的边确不可达，删除正确。** 它也**不是**「保留成本低所以留着」的情形：按本仓库已登记的规则（`AGENTS.md` [domain] 条目），无生产者的规则必须删除而不是加注释保留。

### 4.2 被删断言的等价行为覆盖是否真的存在

- 删掉的是 `happyPathTransitionsAreLegal` 里的 `assertTrue(STREAMING_REASONING.canTransitionTo(STABLE))`。该测试**仍保留** `assertTrue(STREAMING_ANSWER.canTransitionTo(STABLE))`（`:23`）与 `assertTrue(SUBMITTING.canTransitionTo(STREAMING_ANSWER))`（`:24`）——即**可到达的那条无推理路径**（`SUBMITTING -> STREAMING_ANSWER -> STABLE`）仍在同一测试中被断言。
- 新增的 `aReasoningPhaseThatProducedNoAnswerCannotBecomeStable`（`GenerationStateTest:42-46`）同时断言 `assertFalse(STREAMING_REASONING.canTransitionTo(STABLE))` 与 `assertTrue(STREAMING_REASONING.canTransitionTo(STREAMING_ANSWER))`，把「该边不可用」与「唯一前进方向」都锁住，**该边无法静默回归**。
- 操作级等价覆盖由 T-021 的 `GenerationOperationTest` 提供（`anAnswerDeltaMovesSubmittingStraightIntoTheAnswerPhase`、`aThinkingDisabledAttemptStabilizesWithEmptyReasoning`、`reachingStableDirectlyIsRefusedBecauseStabilizeOwnsThatMove`），本轮由我的穷举探针独立复现（上表最后一行：走推理再走回答仍可稳定化，且 `hasReasoning()=true`，推理与回答**同属一个稳定版本**没有丢失）。
- **结论：等价覆盖存在且更强**（原来只断言一条边合法；现在断言该边非法 + 唯一前进方向 + 操作级路径）。

### 4.3 `OPERATION_TERMINAL` 删除与「每个错误码都有生产者」

机械检索 `src/main`（排除枚举定义文件自身）：

```
=== ILLEGAL_STATE_TRANSITION ===  GenerationState.java:179 ; GenerationOperation.java:114,231,263,273 ; Message.java:86,109
=== OPERATION_NOT_STABLE ===      BranchSource.java:71
=== OPERATION_NOT_COMPLETABLE === GenerationOperation.java:241
=== REVISION_IMMUTABLE ===        Message.java:114
=== PROMPT_POLICY_VERSION_REQUIRED === AnswerRevision.java:80 ; PromptPurpose.java:122
=== PROMPT_POLICY_VERSION_MISMATCH === AnswerRevision.java:84 ; PromptPurpose.java:128
=== ANCHOR_INVALID ===            BranchAnchor.java:47,51,55,59,67
=== SEQUENCE_OUT_OF_ORDER ===     Branch.java:193,197
=== MESSAGE_ROLE_MISMATCH ===     Message.java:82,104
=== OPERATION_TERMINAL ===        （零命中）
=== BRANCH_SOURCE_INVALID ===     Branch.java:77   ← 仅 javadoc {@link}，见 V2
```

⇒ **9 个现存枚举常量全部至少有一个生产者，无遗漏、无多余**；`OPERATION_TERMINAL` 删除正确（终态晚到回调按验收条件 4 走 `ILLEGAL_STATE_TRANSITION`，`GenerationOperation.refusalsFor`/`stabilize` 已覆盖）。唯一残留是 `Branch.java:77` 的悬空 `{@link}`（V2）。

### 4.4 T-003 的 83 个既有用例是否原样保留

- **方法名层面**：83 条中 **82 条逐字保留**；1 条为 N8 已声明的改名（`GenerationStateTest.nullTargetIsRefused` → `aNullTransitionTargetIsACallerBugNotABusinessFailure`）。自 T-021 最近一次记录（`T-021-green.txt`，121 名）到本轮实测（121 名），名集合**完全一致，零增删改名**。
- **方法体层面：无法核对。** `android/` 未被 git 跟踪（`git ls-files android` = 0），没有任何历史可 diff，「断言是否被改写后才通过」在本仓库**不可机械判定**。我能做的最强交叉核对是：① 名集合逐条比对（已完成）；② 用自写探针**重新独立验证这些断言所声称的行为**（§2.3，29/29 PASS）。交付方对此也无更强的证据形态——**这是仓库级的证据能力缺口，不是交付方造假**，如实记入 §5。
- **未发现「改写后通过」的迹象**：唯一被替换的断言（N10）与其替换理由、等价覆盖均已在任务文件与测试 javadoc 中声明，且替换方向是**收紧**（从断言合法改为断言非法）。

### 4.5 N10 改动是否影响了 T-021 对 T-003 面其他文件的修改

- `PromptPurpose.isAbsent` / `versionFailureFor` + `AnswerRevision.create` 改用 `isAbsent`：三条通道的「缺失策略版本必须返回错误码」语义**未被削弱**。三处入口实测行为（`null/""/"   "` → `PROMPT_POLICY_VERSION_REQUIRED`）分别由 `AnswerRevisionTest.missingOrBlankPolicyVersionIsRefused`、`GenerationCommandTest.aMissingVersionIsRefusedWithThePromptPolicyCode`、`PromptSpecTest.aBlankVersionIsReportedAsTheMissingPolicyCode` 覆盖，且 `PromptPurposeTest.absenceOfAVersionHasOneDefinition` 与 `PromptSpecTest.theVersionDecisionIsTheSameOneGenerationCommandMakes` 提供**跨入口一致性断言**（8 种输入逐一比对成败与错误码）。我的探针独立复现了 `AnswerRevision` 一侧（含 `\t`、`\n` 两个 `isAbsent` 独有的空白形态，`create` 与 `versionFailureFor` 判定一致）。**口径未漂移。**
- 副作用（已计入 V3）：T-021 向 `PromptPurpose.java` 增加了 21 行生产代码，使 T-003 记录的该文件行数过期。

---

## 5. Gaps

- **G1（设备/模拟器验证已暂停）**：按用户 2026-09-21 的决定，模拟器/设备侧验证暂停，由用户自行安装 APK 实测。**本报告的任何 PASS 均不表示设备行为已验证**；`T-002-device-evidence.txt` 与 `T-003-guard.txt` 中的 APK/dex 证据均为静态产物证据，不含真机运行。不因此判 FAIL，但「设备侧行为」在 T-003 面**仍未验证**。
- **G2（版本库不可核对）**：`android/` 未被 git 跟踪（`git ls-files android` = 0），无法核对「改了几行」「哪些断言被改过」。本报告所有「保留/未改」结论都只到**方法名层面**，断言内容只能靠探针重新验证行为，不能证明「未被改写」。
- **G3（非本任务范围，计划内）**：`ContextSnapshot`/`ContextEntry`/`ContextPlan`（T-007）、`AiArtifact`（T-011）不在本任务；对话轮数派生与「重试/重置不增轮数」的机械保证属 T-015。
- **G4（复核副作用）**：本轮为独立复核创建了 `D:\Project\_vtmp\`（4 个探针/脚本 + `android-inject/` 注入副本，均在仓库之外），并运行了 `:core:javadoc`（会在 `android/core/build/docs/javadoc/` 写入产物）。**未修改任何生产代码、测试代码或任务文件。**
- **G5（本任务未做的独立守门）**：Java-only 守门的**字节码/dex** 面向未在本轮重跑（需要构建 APK）。本轮只做了词法初筛（`.kt/.kts` 与 Kotlin 坐标零命中，§2.3）。可接受的替代证据是 `T-021-guard.txt`（11:48，`VERDICT: PASS`、3 个 APK 全覆盖、`GUARD EXITCODE=0`），但它由交付方录制。
- **G6（`:core` 无 lint）**：`:core` 是 `java-library`，`./gradlew :core:tasks --all` 只列出 `javadoc`，**没有 lint 任务**；且 `javadoc` 不在 `:core:check`/`build` 的依赖链上（`:core:test` exit 0 而 `:core:javadoc` exit 1 已实证）。因此 V2 类缺陷在现有检查链中**结构性不可见**。
- **G7（T-021 侧未复核项）**：T-021 的规模证据 `T-021-sizing.txt` 只列了 5 个「touched files」，**未包含它实际修改过的 `PromptPurpose.java`**；这是 T-021 面的口径缺口，本轮只作为 T-003 数字不可复现的成因记录，未对 T-021 任务文件作完整复核。

---

## 6. 结论与翻盘条件

**结论**：T-003 的领域实现本身**经独立复现为真**——不可变回答版本、`STABLE` 才能作分支来源、错误通道三段规则、UUID 规范形式、锚点一致性、未读派生、提示词用途与文档一致，全部由我自己的探针与命令复现（29/29 + 121/121 用例 + 注入式守门自检）。**N10 的两处删除都成立**，且删除后语义与断言覆盖**没有损失、只有收紧**。

但它**不能判 PASS**，因为当前态自述与实测不一致（V1/V3/V4/V5）、`documentation.md:42` 明令 FAIL 的「当前态主张与生产事实矛盾」真实存在（V2，且有 `:core:javadoc` exit 1 的机械证据）、以及规模例外尚未经用户批准（V8，`harness-tasks.md:45`）。

**翻盘为 PASS 的条件（必须全部满足）**

1. 修复 **V2**：`Branch.java:77` 的 `{@link}` 改为 `{@code}`，`:core:javadoc` 重跑至 exit 0（建议同时把 `:core:javadoc` 纳入检查链）；
2. 修复 **V1**：`GenerationState.java:34` 的「three deviations」与实际偏离数一致；
3. 用同一脚本重算并同步 **V3** 的全部数字（741→762、1301→1403、409→488、151→153、逐文件 2 格）；
4. 同步 **V4** 的用例数（83→86、116→121、33→35/38）并消除同文件内自相矛盾，**重录 T-003 自有证据**（V5）；
5. 处置 **V6**、**V7**（择一方案即可，需在任务文件记录）；
6. **用户批准规模例外**（V8）。若用户选择继续拆分至 ≤500 行，则本条以拆分替代。

**若用户不批准规模例外**：按 `harness-tasks.md:45` 的字面口径，仅规模一项即构成**失败**，本报告应直接由 FAIL 维持不变、不得转 `verified`。

**建议追加到根 `AGENTS.md` 失败案例库的条目文本**

```
- [2026-09-21] [doc/guard] 删除性修复只改了「实现」而漏了「注释里的引用与计数」→ ① 删掉
  `DomainErrorCode.BRANCH_SOURCE_INVALID` 后，`Branch.java:77` 仍留着
  `{@link DomainErrorCode#BRANCH_SOURCE_INVALID}`，`javadoc` 直接 `error: reference not found`
  退出码 1；② 删掉 `STREAMING_REASONING -> STABLE` 边后，`GenerationState` 类注释仍写
  `three deviations are allowed here`，而同一条清单的第三个 bullet 自己写着「该边已删除」。
  两处都躲过了四轮复核，因为交付检查链里**没有 javadoc 任务**（`java-library` 的 `check`/`build`
  不依赖 `javadoc`，`:core:test` exit 0 时 `:core:javadoc` 已经 exit 1）。规则：① 删除任何公开符号
  或规则后，必须在**同一次提交**内检索三样东西——`{@link}`/`@see` 引用、正文里的「N 处/N 条」计数、
  测试与 README 的复述，并把 `javadoc`（或等价链接校验）纳入检查链，否则悬空引用永远静默；
  ② 正文里不要写「N 处」这类可失效的计数，改写为逐条列举并让每条自带「存在/已删除」状态。
```

**给用户的一句话决策提示**：N10 是对的、行为是对的；卡住 `verified` 的是一个悬空 javadoc 链接、一句话里的过时计数、一张对不上的行数/用例数表，以及那份仍待您签字的规模例外。
