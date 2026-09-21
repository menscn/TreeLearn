# Treelearn 项目规则入口

> 本工程复刻自 `knowledge_space` 的 Harness 工程开发流程，按 Treelearn 的「Android + 后端(Java/Spring Boot) + 前端(React+TS)」三端适配。
> 详细背景与使用说明见 [.trae/README.md](.trae/README.md)。

项目规则统一维护在 `.trae/`；详细规则只有一份权威正文，按下表触发读取。

## 读取协议
- 先按任务目标、预计修改路径和实际调用链匹配下表，在相关分析、设计、修改或复核前读取命中的文件。路径与行为触发取并集，不只看文件后缀。
- 范围扩大、发现新的 producer/consumer 或进入下一阶段时补读；本会话已读且未变化的规则不重复加载。
- 不要预读全部 rules、agents、skills 或参考资料；普通任务不要求读取完整 HARNESS。链接是读取指令的目标，不代表工具自动加载了正文。
- 跨栈工作叠加所需规则；未命中不代表可绕过适用约束。无法判断时先检查实际调用链；必需规则不可读时报告缺口，不自行豁免。
- 下表是索引摘要；具体适用范围、例外、证据和判定以目标正文为准。新增细则写入对应主题，入口仅补必要触发条件。

## 执行入口判断
- **直接执行**：改动很小，或者目标、边界和验收方式非常明确，明显不需要多个独立任务协调时，默认直接处理，不创建 Task，也不强制运行 verifier。
- **先询问用户**：面对大型需求、大型改动、高风险改造或包含多个独立交付项的工作，先说明判断理由，并询问用户是否采用 Harness Task 流程；用户确认前不得创建父需求或实现 Task。
- **进入 Harness**：用户选择采用 Harness，或明确使用下列 Harness 触发词时，按完整 Harness 流程执行。显式触发词视为已经同意采用 Harness，无需重复询问是否列 Task，但仍需澄清和确认具体需求。
- 是否需要询问主要依据独立交付项数量、协调复杂度、需求宽泛程度，以及架构、数据、权限、迁移等风险判断。跨栈或预计代码行数本身不自动触发 Harness。
- 用户选择不采用 Harness 时，不创建 Task，不应用 Harness 的任务拆分、lane 分配、行数预算或 verifier 强制要求；但仍必须遵守本文件的安全边界、必要测试、文档同步和适用的专项 guard。

## Harness 触发词
以下表达表示用户选择 Harness，仍须澄清和确认业务需求：
`按 harness-run 做这个跨栈需求`、`工头模式`、`按 harness 调度`、`harness-run`、`Harness mode`、`foreman mode`、`cross-stack request`。

## 通用底线
- 文件以 UTF-8 读写，写入中文前确认工具链编码；不得写入问号占位、替换字符或乱码。
- 完成结论必须匹配证据覆盖，已知错误和缺少必要验证不得报完成或 PASS。
- 当前运行事实以生产代码、活动配置、migration 和实际 producer/consumer 为首要依据；历史材料不作为现行契约，安全规范不得为迁就违规代码而降低。
- 变更必须按下表完成相应文档影响记录与验证；直接执行不免除适用规则，也不因此新增 Task 或例行审批。
- 只有用户选择 Harness、明确要求任务或其它已获准流程实际创建任务时，才创建 Task；任务及执行计划、交接、状态统一放在 `.trae/tasks/`，写入前读取任务规则。
- 不新增未经验证的依赖，不做未经验证的自动提交。

## 按需规则路由
| 触发条件 | 必读文件 | 读取时机 |
|---|---|---|
| 行为修改、共享逻辑、回归修复；生成、重生成/删除/保存、轮询、幂等、计量或日志噪声修复 | [防回归](.trae/rules/regression.md) | 影响分析、修改及行为验收前 |
| 生产代码、API、配置、SQL、架构、用户流程变更，或当前态文档修改 | [文档一致性](.trae/rules/documentation.md) | 事实核对、修改及完成前 |
| `frontend/**`；用户可见文案、错误提示、用户可见时间或设备时区 | [前端](.trae/rules/frontend.md) | 分析、修改及复核前 |
| `backend/**`（Java/Spring Boot） | [后端边界](.trae/rules/backend.md)、[契约](.trae/rules/contracts.md) | 分析、修改及复核前 |
| `backend/**/src/main/resources/db/migration/**` 或其它后端 SQL | [SQL 与建表](.trae/rules/sql.md)、[契约](.trae/rules/contracts.md) | 设计及执行前；真实 MySQL/Flyway 验证后方可完成 |
| `android/**` | [Android](.trae/rules/android.md) | 分析、修改前；完成前执行 Java-only guard |
| 任意 SQL、Flyway、SQLiteOpenHelper、建表、维护或数据脚本；数据库时间或时区 | [SQL 与建表](.trae/rules/sql.md) | 设计及执行前；按实际生产引擎验证（后端 MySQL / Android SQLite）后方可完成 |
| 外部 AI/LLM/Embedding 服务调用边界、生成相关 | [后端边界](.trae/rules/backend.md) 之 AI 边界 | 调用链分析及修改前 |
| 状态存储、缓存、租约/锁/Worker owner；轮询、后台/异步任务、SSE、WebSocket、文件处理、补偿或定时任务 | [共享状态](.trae/rules/shared-state.md) | 设计、修改及跨实例验证前 |
| API/DTO/SSE/请求头、时间戳、前后端桥接、共享错误语义、契约文档或路由索引 | [契约与 guard](.trae/rules/contracts.md) | 影响分析、修改及复核前 |
| 受影响模块生产代码、构建入口、依赖、启动配置、端口、代理、健康检查、环境变量或安装方式 | [启动与部署](.trae/rules/startup.md) | 修改前；完成前提供各模块启动证据 |
| 新增/改造处理流程、前端/Android 用户功能、日志或遥测 | [日志与遥测](.trae/rules/observability.md) | 设计、修改及验收前 |
| 用户已选择 Harness | [Harness 入口](.trae/HARNESS.md) | 编排开始时，按阶段继续读取 |
| 已授权创建、导入、恢复、拆分或执行任务，含外部 skill/plugin 产物 | [任务契约](.trae/rules/harness-tasks.md) | 产生任务文件或执行任务前 |
| Harness 验收，或用户明确要求 verifier | [验收](.trae/rules/harness-verification.md) | 验收前，叠加本次变更命中的专项规则 |

## 工具与参考
- 项目 agent（派 subagent 时的角色提示词）：`.trae/agents/*.md`；项目技能：`.trae/skills/*/SKILL.md`。仅加载实际使用的角色或技能。
- 任务文件机械校验：`.trae/scripts/validate_tasks.py`（Task v2 契约校验，verifier 与提交前可跑）。
- [排除说明](.trae/rules/EXCLUDED.md)：列出从 knowledge_space 迁移时明确排除的规则（HClaw、CSPfix 归档等）及理由。

## 失败案例库（不要再犯）
> verifier 发现新型错误或守门多次命中时 → 必须建议追加一条到本节。条目 > 30 → 整理合并去重。

- [2026-09-21] [domain] 把**同一条规则**写在两个入口（各写一份实现）→ 两份实现**对同一输入给出不同通道**：`PromptSpec.of` 对「版本合法但不属于声明用途」抛 `IllegalArgumentException`，`GenerationCommand.create` 对**同一输入**返回 `PROMPT_POLICY_VERSION_MISMATCH`，于是调用方必须知道手里是哪个工厂才知道该 catch 还是该查错误码；第二轮又发现同一缺陷类的第二种形态：多缺陷输入下业务码**遮住**调用方 bug（畸形 id + 缺失版本 → 返回业务码而非抛异常）。规则：① 判定收敛到**唯一判定点**（此处 `PromptPurpose.versionFailureFor`，共享谓词 `isAbsent`），共享的是**判定**而不只是措辞；② 例外清单**只存一处**（`Guard` 类注释），README 等文档只指向它、不复制；③ 修完必须加一条**跨入口比对断言**（同一组输入逐个比对两个入口的成功/失败与错误码），否则下一轮照样漂移。
- [2026-09-21] [domain] 把**调用方义务**当成领域层已校验的前提 → 「流已正常终止」在纯领域类型里**完全不可表达**：`GenerationOperation.stabilize` 只见「非终态 + 回答非空」，流仍在进行中时调用它同样成功，而验收条件却写着「正常结束**且**回答非空」。javadoc 只写 "Closes a normally finished attempt"，读者会以为它校验了。规则：凡验收条件里由**上游模块**负责的前置条件，必须在类型的 javadoc 里显式写成「调用方义务 + 本类型无法观测」并同步进 Gaps 与 Handoff watch-outs；不要为了「让类型自己管住」而把上游状态（流状态标志）塞进纯领域对象——那正是模块边界要挡的耦合。
- [2026-09-21] [harness] 交付自述里的计数与**证据文件**不同源 → RED 记录写「40 处 `cannot find symbol`、两个测试文件」，实测是 **49 errors、三个测试文件**；GREEN 记录声称证据含退出码，而证据文件既没有命令行也没有退出码行。两个数字都不是编造的，是**凭记忆写的**，且都过了自己的眼睛。规则：任务文件里每个数字都要写成「从哪个文件的哪行得到」（如 `grep -n "errors$" artifacts/evidence/…`），并在**写完之后**用同一命令回读一次；证据文件必须自带命令行与退出码（`EXITCODE=`），否则不能自证。另：**契约改动后必须同步重生成引用它的证据文件**——第六轮实测：任务与 README 都引用 `artifacts/evidence/T-002-guard-selftest.txt` 作为「完整模式 25 例」的证据，而该文件是一次 `-Fast` 运行（23 例、且自述 `CASE 7-8 skipped`），被引用的恰好是它跳过的两个用例。证据文件必须记录**产生它的那条命令与运行模式**，引用方必须核对该模式与自述一致。

- [2026-09-21] [harness] 把任务文件的 RED/GREEN 记录写成「人读友好」格式 → **机械校验器读不了**：`Exit code: **1**` 的粗体让 `int()` 抛 `ValueError`；`Observed failure:` 后面接代码块意味着该键取值为空；详细回归证据写成「`- ` 开头的续行」后既没有 `Regression evidence` 这个键、又多出一堆以反引号为名的垃圾键；RED 与 GREEN 的 `Command` 只因一句括注（`（执行时把 … 移出仓库）`）而不相等。结果是两个任务带着 **10 条校验错误**停在 `verifying`，而任务正文看上去很完整。规则：RED/GREEN 的五个键必须给**纯净值**（`Exit code: 1`、`Observed failure: <一句话>`），细节放进代码块或表格；并且**每次改完任务文件立刻跑 `.trae/scripts/validate_tasks.py`**，不要等到 verifier 才跑——正文写得好不等于契约通过。

- [初始] [contract] 改接口契约文档不派 `contract-guardian` 复查 → 前后端字段对不齐；规则：触契约必走 contract-guardian。
- [初始] [android] 误引入 Kotlin 文件或依赖 → APK 体积膨胀 + 团队约定违反；规则：android 提交前必走 Java-only guard。
- [初始] [backend] 直接调 LLM/Embedding 或写不归属本服务的共享索引 → 越权；规则：AI 调用必须走明确的服务边界。
- [初始] [budget] 一个 task 涌出 > 500 行 diff → verifier 拒绝；规则：拆解阶段就估算 ≤ 400 行，留 100 行缓冲。
- [初始] [shared] 改 `V1__init.sql` 不走 `db-migration-reviewer` → schema drift；规则：迁移脚本必走该守门。
- [2026-09-20] [android-db] 把 Android SQLite SQL 强制送到无生产消费者的 MySQL 验证 → 证据与真实运行时不一致；规则：先判定生产数据库引擎，后端 SQL 用真实 MySQL/Flyway，Android SQL 用 API 28+ 真实 Android SQLite，并共同接受 `db-migration-reviewer` 复核。
- [2026-09-21] [guard] 守门脚本用 `'=(,:[' -contains $prev` 判字符集合成员（PowerShell 对字符串是**集合包含**语义而非子串匹配，恒 False）→ 该检测分支成死代码，Kotlin 依赖可**完全静默**通过守门，连续两轮复核都没发现；规则：① 字符集合成员判定只用 `.IndexOf()` / 数组 `-contains`；② 守门脚本的**每条检测分支**必须配一条「注入该违规必须被判 FAIL」的自检用例（正反双向自检，见 `artifacts/evidence/T-002-guard-selftest.txt`）；③ 词法/解析类守门必须 fail-closed：解析器终态不干净即判 FAIL，且关键判据要有一条**不依赖解析器**的原文兜底扫描。
- [2026-09-21] [android] 用「POM 干净」推断构件无 Kotlin → `androidx.test:monitor:1.6.1` POM 无任何 Kotlin 依赖却在设备上抛 `ClassNotFoundException: kotlin.jvm.internal.Lambda`；规则：Kotlin 合规判定必须以**字节码/dex 字符串表**为最终依据，依赖图只作初筛。
- [2026-09-21] [tooling] 把要在 Windows PowerShell 5.1 下执行的 `.ps1` 存成 **UTF-8 无 BOM** → PS 5.1 按系统 ANSI 代码页（简中机器上是 GBK）解码，中文串变乱码后直接报语法错误（「表达式中缺少右")"」）。这不是「结果不准」，是**脚本根本执行不了、连退出码都拿不到**。实测命中：`.trae/skills/line-budget/line-budget.ps1` 长期处于不可用状态而无人察觉。规则：Windows 上执行的 `.ps1` 必须 **UTF-8 with BOM**（或刻意纯 ASCII，见 `android/scripts/no-kotlin-check.ps1` 的做法），并在脚本头写明该前提与补救命令。
- [2026-09-21] [tooling] 统计变更规模只跑 `git diff --numstat` → ① **新 task 新建的文件天然全是未跟踪的，git diff 完全看不见**，工具对最主要的变更形态（新增文件）系统性失明（实测出现「业务变更 2000 行却只扫描到 1 个文件」）；② git 对含非 ASCII 的路径会加双引号并八进制转义（`"Docs/04-ux-design/\347\237\245...html"`），前导引号使 `^` 锚定的排除正则失配，本该排除的文档目录被**算进业务行**（实测虚增 2088 行）。规则：规模统计必须同时覆盖 `git diff` 与 `git ls-files --others --exclude-standard`，并对所有 git 调用统一加 `-c core.quotepath=false`。
- [2026-09-21] [tooling] 原生命令的 stderr 被 `$ErrorActionPreference='Stop'` + `2>&1` 升级为终止性 `NativeCommandError` → 脚本在拿到结果前中断且**不输出任何结论**。同一根因先后命中 Java-only 守门（Gradle 的 javac 提示）与 `line-budget`（git 的 LF/CRLF 警告），说明这是**工具链级**而非单脚本的问题。规则：所有 `.ps1` 调外部命令统一走一个 `Invoke-NativeCaptured` 包装（临时把 `$ErrorActionPreference` 置为 `Continue`，只依赖 `$LASTEXITCODE`），并在失败分支回显子进程输出的末几行摘要，避免「失败了但不知道原因」。
- [2026-09-21] [tooling] 把 `-f` **内联**进 `"a {0}" + "b {2}" -f x, y, z` → PowerShell 中 `-f` 比 `+` **结合更紧**，只格式化第二个操作数，前半段保留 `{0}`/`{1}` 字面量。实测 `'a{0}' + 'b{2}' -f 1,2,3` 得 `a{0}b3`。后果不是报错，而是**证据行静默失去它本来要证明的数字**（守门打印「在 {0} 个变体 classpath 上无 Kotlin 坐标」，评审无法据此核验）。规则：格式化一律用 `$(...)` 插值，不要混用 `+` 与 `-f`；并给证据加一条机械断言「OK 行不得含未替换的 `{N}` 占位符」（已落在 `android/scripts/dev/guard-selftest.ps1` 的 CASE 9，连该运算符事实本身也被断言）。
- [2026-09-21] [tooling] 用 `>>` 直接重定向原生命令输出来写证据文件 → PS 5.1 写的是 **UTF-16LE**，评审工具与 Read 都把证据文件当**二进制**，人无法复核；叠加「先把输出捕获进变量、跑完再一次性写出」的写法，任务被超时中断时证据文件是**空的**（上一轮真实出现）。规则：`2>&1 | Out-File -LiteralPath <f> -Encoding utf8 -Append`（仍逐行流式写，中断也保留已打印内容），并在证据文件头部记录生成时间、命令、被核脚本的 SHA-256 与行数。
- [2026-09-21] [guard] 让**同一个解析器**同时充当判据与豁免依据 → 解析器可以**自证清白**。实例：注释降级判据写成「剥离后该行为空 **且** 词法器干净收尾」，于是 `def s = $/ /* /$` 令词法器进入块注释态、`def t = $/ */$` 干净重同步，中间的**真实** `compileOnly 'org.jetbrains.kotlin:…'` 被剥成空行并打印 `VERDICT: PASS`。规则：凡解析器输出既作判据又作豁免依据者，**必须再用一条不共享该解析器状态的独立判据复核**；需要豁免时优先选与解析器无关的原始输入判据（此处改为「原文行是否以 `//` 开头」）。
- [2026-09-21] [guard] 在守门里保留**手写清单**并逐个打补丁 → 三次盲区全部源于手写枚举：写死 3 个 runtime classpath（漏掉 `compileOnly` 上的 GString 坐标）、写死 3 个 APK 目录（`app/build/outputs/apk/staging/` 下的含 Kotlin APK 从未被扫）、写死产物名（`app-release.apk` vs 实际 `app-release-unsigned.apk`，release 长期打印 `SKIP`）。每次都是 verifier 往枚举之外放东西就打穿。规则：**按模式识别 + 断言匹配集非空**；匹配集为空必须判 FAIL，绝不能退化成「什么都没验证却打印 PASS」。
- [2026-09-21] [domain] 保留**无生产者 / 不可达**的领域规则 → 错误码与状态迁移边的调用方会 branch 到永远不可达的分支，读代码的人会以为那条路径存在。同一仓库已连续命中三次：`DomainErrorCode.BRANCH_SOURCE_INVALID`（唯一生产者改为抛异常后失去生产者）、`DomainErrorCode.OPERATION_TERMINAL`（终态回调契约规定走 `ILLEGAL_STATE_TRANSITION`）、`GenerationState` 的 `STREAMING_REASONING -> STABLE`（稳定化要求回答非空，而回答缓冲的唯一写入者会先把状态推进到 `STREAMING_ANSWER`，故该边不可达）。规则：新增错误码/迁移边时**连着返回或触发它的代码路径一起加**；发现无生产者时删除，而不是加注释保留。
- [2026-09-21] [tooling] dev 脚本的默认路径按**当前工作目录**解析，而脚本自己的用法说明教人**从仓库根**运行 → 两者矛盾，照文档执行必然得到「找不到文件」。实例：`android/scripts/dev/lint_summary.py` 默认 `app/build/reports/...` 相对 CWD，而 docstring 写的是 `python android/scripts/dev/lint_summary.py`。规则：dev 脚本的默认路径按**脚本自身位置**解析（Python 用 `os.path.dirname(os.path.abspath(__file__))`，PowerShell 用 `$PSScriptRoot`），并让 docstring 与默认值互相印证。
- [2026-09-21] [tooling] 在**未闭合括号内**跨行写表达式时，把二元运算符放在**下一行行首** → PowerShell 的规则是「表达式在换行处若已完整即结束语句，续行要求运算符留在上一行行尾」，于是 `$p += ('a '` 换行 `+ 'b')` 被判为「缺少右括号」，**三个 `.ps1` 同时无法解析**：守门以 `ParseException` 退出、**零证据行**，而周边工作看起来都已完成（最危险的是「没有输出」读起来像「没有问题」）。最小复现已收敛为两条文件（`('x'` 换行 `+ 'y')` 失败；`('x' +` 换行 `'y')` 通过）。规则：续行一律把运算符留在**行尾**；并且**每次改完 `.ps1` 立刻用 `[System.Management.Automation.Language.Parser]::ParseFile` 复检全部脚本**——脚本无法自检自身（解析不了就永远到不了第一行），因此守卫要两道互补：守门 stage 0 解析**兄弟**脚本，自检 CASE 11 解析**守门**。
- [2026-09-21] [tooling] 用 `| Out-String` 捕获子进程输出并写进证据文件 → `Out-String` 按**宿主宽度**（默认 120）重新折行，把长证明行从**中间**截断（实测 NOTICE 行在 `contains` / `s Kotlin-compiled` 之间被切开，533 字符的行在文件里变成多行）。规则：证据捕获用**逐行拼接**（`… 2>&1 | ForEach-Object { [string] $_ }` 再 `-join`），不用 `Out-String`；捕获封装同时写入 `ExitCode` / `Seconds` / `CapturedAt` / `Command` / `Mode`，并在**回读文件**后核对（以文件为准，不以控制台回显为准）。
- [2026-09-21] [guard] 用「子进程**退出码 0**」或「前置条件缺失」代替「**证据存在性**」→ 两种同源形态：① 守门 stage 3b 只看 `gradlew verifyNoKotlinDex` 的退出码，不检查它是否真的打印了 `verifyNoKotlinDex:` 判据行，于是子任务静默退化（up-to-date / skipped / 改名）时守门仍打印 `OK no built APK contains Kotlin runtime bytecode` 并 `VERDICT: PASS`（实测：零判据行 + shipped 路径下含 `Lkotlin/Metadata;` 的 APK → PASS / exit 0）；② 自检探针 CASE 8 在源 APK 缺失时把该用例记为 `Ok = $true`，完整模式照样报 `SELFTEST PASS`，等于该检测分支**从未被验证**。规则：**判据必须有「证据存在性」断言**——子进程判据至少要断言出现 ≥1 条判据行（更好：断言 `PASS (N …)` 且 N 与递归发现的构件数一致）；探针的前置条件缺失必须判 FAIL，或从用例总数中扣除并单独打印 `SKIPPED`，**绝不能算 PASS**。
- [2026-09-21] [guard] 探针测的**形状**不是生产路径的形状 → 假见证。实例：守门用 `| Out-String` 捕获子进程输出得到**一个多行字符串**，而自检合成的是**行数组**；`foreach ($line in @($Text))` 对字符串只迭代一次，于是 Gradle 的 **5 行判据被算成「1 judgement line(s)」**，那条 OK 行实际上是整个 blob，并因内含 `NOTICE` 子串而多打印了一条**假 NOTICE**——三个现象共用一个根因，而 CASE 10 的 7 条断言全部「通过」。规则：**共享的判定函数必须放在共享文件里按生产路径的真实输入形状归一化**（此处加 `ConvertTo-EvidenceLines`），并给自检补上「**同一份内容以两种形状喂入必须等价**」的断言；只在一个调用点修，等于让探针继续认证一个生产路径从不使用的形状。
- [2026-09-21] [harness] 拆分任务时给父编号加字母后缀（`004b` / `004c`）→ Task v2 的 `id` 只接受 `T-YYYY-MM-DD-NNN`（**三位数字**），`validate_tasks.py` 直接判 2 条 ERROR（`id must use T-YYYY-MM-DD-NNN` + `filename must use T-YYYY-MM-DD-kebab-case-description-NNN.md`）。后果不是「格式不好看」，而是**新任务根本无法进入 `ready`**，且如果没人跑校验，它会一直躺在那儿当参考。规则：子切片一律用**连续三位序号**（`022`/`023`，文件名带描述性 kebab-case，日期与序号必须与 `id` 一致），不要发明 `NNNb` 式编号；**每次新建或拆分任务后立刻跑 `.trae/scripts/validate_tasks.py`**，把 error 数当门禁，而不是只看人写的任务正文。
- [2026-09-21] [sqlite] 用**约束的措辞**去判定**约束的类别** → 等于让被判对象自己定义判据，两种形态先后命中。① 原始形态：`assertThrows(SQLiteConstraintException.class, …)` 只断言「抛了约束异常」——`foreignKeysAreEnforcedNotMerelyDeclared` 里那条 role CHECK 用例在**分支行不存在**时是**因外键**而通过的，断言过了、不变量从未被触及。② 加固后仍被打穿：断言 `msg.contains("CHECK") && !msg.contains("NOT NULL")`，而该 CHECK 的文本是 `role = 'AI' OR (user_content IS NOT NULL AND length(...) > 0)`——**两个方向同时满足**，判据自身失效（这是改完①之后真实跑出来的失败）。规则：成因判定必须用**与措辞无关**的判据——SQLite **扩展错误码**（本仓库实测：`1299` NOT NULL / `275` CHECK / `2067` UNIQUE / `787` FOREIGN KEY / `1811` TRIGGER，证据 `artifacts/evidence/T-004-constraint-code-probe.txt`），且**实测取码、不凭记忆**；一次失败只带一个码，断言正确的码即自动排除其它成因，不需要脆弱的「不含某子串」从句。同一类别下有多条约束时，再断言一段**互斥**的具体约束文本以分辨是哪条生效（两条都以 `parent_id` 起头的分支 CHECK 必须靠 `source_message_id IS NOT NULL` vs `source_message_id IS NULL` 区分）。**取码的临时探针用完即删**，不得留在验收套件里当用例（否则它每次都 fail，把真结果淹掉）。
- [2026-09-21] [tooling] 给 AGP 的 `connectedDebugAndroidTest` 传 `--tests` 过滤 → `Problem configuring task … Unknown command-line option '--tests'`（该任务不支持按类名过滤），白白丢一轮 10 秒。规则：instrumentation 只能**整包跑**；需要单独取某个用例的输出时，在该用例里 `fail(报告串)` 再从 **JUnit XML**（`app/build/outputs/androidTest-results/connected/debug/TEST-<device>-_app-.xml`）回读，比过滤更快也更可复核。另：`gradlew` 偶发 `Could not create service of type GeneralCompileCaches … javaCompile.lock (拒绝访问)`，在 `Get-Process | Where-Object { $_.ProcessName -match 'java|gradle' }` 确认**无僵尸进程**时，是缓存锁残留，**直接重跑即恢复**（实测第二次跑成功）——这不是构建脚本的问题，不要去改 `.gradle` 配置。
- [2026-09-21] [harness] 让**临时工作副本**继承仓库的深层构建产物，并且让**清理失败**与「用例失败」共用退出码 → 自检被自己打死，且死因被误读。实测链：`guard-selftest.ps1` 用 robocopy 把整个 `android/`（含 `build/`）拷到 `%TEMP%\tl-guard-selftest\`，设备名 + 测试类名 + `logcat-` 前缀把某条产物路径顶到**正好 260 字符**，越过 Windows 259 上限，`Remove-Item -LiteralPath <root> -Recurse -Force` 抛 `DirectoryNotFoundException`；在 `$ErrorActionPreference='Stop'` 下这个异常**在 CASE 1 中断了整个自检**——后面 38 个用例从未执行、没有 `OVERALL` 行，而退出码是 **1**，与「某个检测分支失效」**完全同码**。于是「harness 崩了」被读成「守门坏了」，排查方向一开始就是错的（这次是靠 `RemoveFileSystemItemIOError` 才认出来）。规则：① 临时副本只拷贝它真正需要的部分，深层逐用例产物（本次为 `androidTest-results`/`reports`/`test-results`/`outputs/logs`）一律排除；② 拷贝后用**实测**的最长路径长度做 fail-closed 断言，并**每次打印该数字**让余量可见（本次修后为 234/259）；③ 清理必须稳健且**不能改变判定**（失败只告警 + 保留现场）；④ **harness 自身故障必须使用独立退出码**（本处新增 `3 = INCONCLUSIVE`），绝不能与「用例不匹配」的 `1` 复用——「没跑完」与「跑完发现坏了」是两种结论，任何一方被当成另一方都是假证据。
