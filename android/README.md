# Treelearn Android (`android/`)

纯原生 Android、Java-only、本地优先的知芽（TreeLearn）客户端。
构建产物为个人侧载 APK，无后端、无账号、无云同步。

## 工具链

| 项 | 值 |
| :--- | :--- |
| Gradle | 8.14.3（wrapper，`distribution-type=all`） |
| Android Gradle Plugin | 8.13.2 |
| JDK | 17（`JAVA_HOME` 指向 JDK 17；源码/字节码级别 17） |
| compileSdk / targetSdk | 36 |
| minSdk | 28 |
| build-tools | 36.0.0 |
| 语言 | Java-only（禁止 Kotlin 源码、插件、`org.jetbrains.kotlin`、`-ktx`） |

`AGP 9.x` 被刻意排除：它内置 Kotlin 支持，与 Java-only 守门直接冲突。

## 模块与依赖方向

```text
:app          -> :core :data :ai :tree-layout
:data  :ai    -> :core
:core         -> (无依赖；纯 JVM，不引用 android.*)
:tree-layout  -> (无依赖；纯 JVM，不引用 android.*)
```

`verifyDependencyDirection` 机械化执行该契约：检查所有可承载项目依赖的配置
（含 `compileOnly` / `runtimeOnly` / `annotationProcessor` / 测试配置），
并对 `:core` / `:tree-layout` 做纯净性扫描（`import android.*`、`import androidx.*`
以及无 import 的全限定用法如 `android.util.Log`）。

> **覆盖状态（2026-09-21 更新）**：`:core` 已由 T-003（领域身份与不可变版本）、T-007（出生快照与上下文组装）、
> T-020/T-021（提示词与生成操作）与 T-022（锚点与消息）填入 28 个领域类型（28 个 .java 文件，
> 每个文件一个顶层类型）；`:tree-layout` 已由 T-016 填入 12 个纯 JVM 类型（树几何、标签避碰、
> 命中测试、视野拟合）。两个纯 JVM 模块现在都有真实源码，纯净性扫描对二者均为真实强制，
> 不再打印 `WARNING … purity contract is vacuous …`。实测
> `.\gradlew.bat verifyDependencyDirection` → `verifyDependencyDirection: PASS (downward-only module graph +`
> `pure-JVM module purity)`，退出码 0（证据：`artifacts/evidence/T-016-doc-sync-2026-09-21.txt`）。

| 模块 | 职责 | 禁止事项 |
| :--- | :--- | :--- |
| `:app` | Activity、XML 视图、导航、屏幕控制器、应用级生成协调器、依赖装配 | 不写 SQL，不拼 DeepSeek JSON |
| `:core` | 领域实体、状态机、用例、Repository/AiGateway 接口、供应商无关 `PromptPurpose/PromptSpec` | 不依赖 Android、SQLite、HTTP、Kotlin |
| `:data` | SQLite schema/DAO/事务/迁移、搜索、阅读状态、Keystore 密钥 | 不决定业务流程，不调用 DeepSeek |
| `:ai` | DeepSeek HTTPS/SSE、上下文编码、版本化提示词注册表与请求组装 | 不操作 View/数据库，不接受用户自定义 system 模板 |
| `:tree-layout` | 树几何、标签避碰、命中测试、视野拟合 | 不读数据库，不持久化像素坐标 |

## 依赖策略（重要）

App 主代码**零 AndroidX 依赖**。原因：`androidx.appcompat:1.7.0`、`androidx.core:core:1.13.1`、
`androidx.test.ext:junit:1.2.1`、`androidx.test:core:1.6.1`、`espresso-core:3.6.1` 以及
`androidx.test:monitor:1.7.x` 都会传递引入 `org.jetbrains.kotlin:kotlin-stdlib` 与 `-ktx`，
按技术方案 §4「出现即拒绝」不可使用。

因此：

- 主代码使用平台 API：`android.app.Activity`、平台控件、`android:Theme.Material.Light.NoActionBar`、矢量/自适应图标。
  实测 `app-debug.apk` **零第三方依赖、零 Kotlin 字节码**。字节数与 dex 个数每次构建都会变，因此**不在此处复制具体数字**
  （此前这里手写的「约 71 KB、4 个 dex」已经过期：随 `:data` 入库变成 5 个 dex，而没有人会记得改这一行）。
  权威值以 `artifacts/evidence/T-004-guard.txt` 的 `verifyNoKotlinDex:` 行为准——那里是守门从实际产物解包实测的
  （2026-09-21 快照：debug 84174 B / 5 dex，release 64552 B / 1 dex）。
- 测试栈选用字节码级核验为 Kotlin-free 的版本组合：`androidx.test:runner:1.5.1`
  （其 monitor 需求恰为 `[1.6.0]`）+ `androidx.test:monitor:1.6.0` + `junit:junit:4.13.2`。
- **含 Android 测试栈的三个模块**（`:app`、`:data`、`:ai`）用
  `resolutionStrategy.force 'androidx.test:monitor:1.6.0'` 钉住该版本；`:core` 与 `:tree-layout`
  是纯 JVM 模块（`java-library`），没有 Android 测试栈，因此不需要该语句。写成「各模块」会让人
  以为五处都有（第六轮 F10 实测：`grep -rn "monitor" --include=*.gradle . | grep -v /build/` 只有三处）。

### 为什么不能升到 monitor 1.6.1

两个版本要分开说，因为它们的失败方式不同：

- **`androidx.test:monitor:1.7.2`**：POM **显式声明** `org.jetbrains.kotlin:kotlin-stdlib:1.8.20`，
  因此依赖报告会直接命中 Kotlin，第一道守门就能拦住（与本文件上文「1.7.x 会传递引入 kotlin-stdlib」一致）。
- **`androidx.test:monitor:1.6.1`**：POM **不声明**任何 Kotlin 依赖，依赖报告是干净的，
  但其 `androidx/test/platform/io/OutputDirCalculator.class` 等 2 个类是 Kotlin 编译产物。
  设备运行时会抛：

```text
java.lang.ClassNotFoundException: Didn't find class "kotlin.jvm.internal.Lambda"
    at androidx.test.platform.io.FileTestStorage.<init>(FileTestStorage.java:45)
```

1.6.1 这条路径**只看依赖图发现不了**，必须做字节码/dex 级校验。因此守门包含
`verifyNoKotlinDex` 任务：直接扫描构建产物 APK 的 dex 字符串表。
（1.7.2：95 类 / 4 个 Kotlin 类；1.6.1：97 类 / 2 个；1.6.0：94 类 / 0 个。）

### 已登记例外（唯一一条）

androidTest APK 的 `classes.dex` 含 Kotlin 描述符（无 `kotlin/jvm/internal` 链接）：
来源是 `androidx.annotation:annotation-experimental:1.1.0`，经 `androidx.test:annotation:1.0.1`
进入测试图。它是「Kotlin 编译但不链接 Kotlin 运行时」的注解类，设备上不需要 `kotlin-stdlib`，
不会崩溃。守门把它作为 `NOTICE (accepted)` 显式打印，而不是静默放过。

**例外边界是完整列举的，不是只列一个代表。** `verifyNoKotlinDex` 在 NOTICE 行里列出该 APK 中
**全部** `Lkotlin/...;` 类型描述符与计数。当前实测 **8 个**：
`Lkotlin/Deprecated;`、`Lkotlin/Metadata;`、`Lkotlin/ReplaceWith;`、
`Lkotlin/annotation/AnnotationRetention;`、`Lkotlin/annotation/AnnotationTarget;`、
`Lkotlin/annotation/Retention;`、`Lkotlin/annotation/Target;`、`Lkotlin/reflect/KClass;`。
全部是注解与反射元数据类，**没有任何 `kotlin/jvm/internal` 链接**，因此设备不需要 Kotlin 运行时。

> 此前 NOTICE 只写 `Lkotlin/Metadata;`：结论正确，但审计者无法从日志判断例外的真实范围，
> 只能自己再解包 dex 扫描一遍才能确认边界。第六轮 F11 把范围改为完整列举。

**主 APK / release APK 不存在该例外，两个 needle 任一命中即 FAIL。**

### 守门为什么是 fail-closed 的

守门脚本本身被独立复核打穿过多轮，最近一轮是用**合法 Groovy** 构造出「任何逐行文本扫描都
看不见的 Kotlin 坐标」。因此当前设计既不依赖任何单点判断，也**不依赖任何手写清单**
（两处历史盲区的根因都是手写枚举）：

1. **词法器终态自检（fail-closed 措施 1）**：Groovy 注释剥离器在处理完一个文件后回传
   终态。若终态不是 `code`（或收尾的行注释），说明解析没回到同步点、后续行可能被静默
   吞掉，守门**直接判 FAIL** 并明说「该文件的剥离扫描不可信」。
2. **与词法器无关的原文兜底扫描（fail-closed 措施 2）**：另有一条正则直接在**未剥离
   注释的原文**上匹配「依赖声明行 + 带引号的 `org.jetbrains.kotlin` 或 `-ktx` 坐标」。
   即使注释剥离再次失步，Kotlin 依赖也不可能被藏起来。
3. **注释降级判据不询问词法器**：只有「原文行以 `//` 开头」才允许降级为 `NOTICE`。
   曾用「剥离后为空 **且** 词法器干净收尾」作判据，等于让解析器为自己的漏判背书——
   `def s = $/ /* /$` 让词法器进入块注释态、`def t = $/ */$` 干净重同步，其间真实的
   `compileOnly 'org.jetbrains.kotlin:…'` 被剥成空行并打印 `PASS`。
   代价是块注释内的 Kotlin 坐标现在判 FAIL（fail-closed 方向，有意为之）。
4. **依赖段按模式选配置，不写死清单**：单次调用取 `:app`、`:core`、`:data`、`:ai`、
   `:tree-layout` 的完整依赖报告，按配置名模式匹配**全部变体 classpath**（实测 53 个），
   并按模式排除 AGP/Gradle 自身的构建工具配置（实测 36 个——它们本身就是 Kotlin 写的，
   不随应用分发）。此前手写三个 runtime 配置，漏掉了 `compileOnly`。
   **匹配集为空即判 FAIL**，而不是「什么都没检查却通过」。
5. **APK 段递归发现，不写死目录**：递归遍历 `app/build/outputs/apk` 全树，三个期望产物
   只作为**覆盖断言**而非扫描边界。此前只 glob 三个硬编码目录，放在 `staging/` 下的
   含 Kotlin APK 从未被扫。严格度按路径判定：`androidTest` 路径允许 `Lkotlin/Metadata;`，
   其余 shipped APK 两个 needle 任一命中即 FAIL。
6. **子进程失败必须可诊断**：外部命令失败时回显末 6 行非空输出摘要，避免 Gradle 守护
   进程持锁之类的环境问题被误读成 Kotlin 违规。

`-SkipDependencyStage` 与 `-SkipApkStage` **仅供自检探针使用**，两者都只产生
`VERDICT: PARTIAL` 与退出码 2，**永不产生 PASS**。

**改这个脚本时的硬性禁忌**（都来自被复核打穿的真实缺陷）：

- 字符集合成员判定**只能用 `.IndexOf()` 或数组 `-contains`**。PowerShell 对字符串用
  `-contains` 是「集合包含」语义（`'abc' -contains 'a'` 恒为 `False`），会让检测分支
  变成永不执行的死代码。
- 外部进程调用**必须**走 `Invoke-NativeCaptured`，不得用 `$ErrorActionPreference='Stop'`
  包裹；否则 javac 的无害 stderr 提示会升级成终止性 `NativeCommandError`，守门假失败。
- `-f` 比 `+` 结合更紧：`'a{0}' + 'b{2}' -f 1,2,3` 得到 `a{0}b3`。把 `-f` 内联进
  `"a" + "b" -f args` 会让前半段保留 `{0}`/`{1}` 字面量，证据行**静默失去它要证明的数字**。
  需要格式化时用 `$(...)` 插值。
- 证据文件**不要**用 `>>` 直接重定向原生命令输出：PowerShell 5.1 写的是 UTF-16LE，
  文件会变成二进制。用 `2>&1 | Out-File -Encoding utf8 -Append`（仍然逐行流式写，
  被中断时保留已打印内容）。
- **新增任何检测分支，必须同时补一条「注入该违规必须被判 FAIL」的自检用例。**
  自检已落盘为可复现脚本 `scripts/dev/guard-selftest.ps1`（在工作树之外的临时副本上运行，
  从不修改工作树）：`-Fast` 模式 38 例、含真实 Gradle 的完整模式 40 例（CASE 7/8 是那 2 个 Gradle 用例）。
  最近一次**完整模式**运行结果见 `artifacts/evidence/T-002-guard-selftest.txt`（文件头写明运行模式与
  被核脚本的 SHA-256，引用方**必须**核对该模式与自述一致——曾出现「引用完整模式、文件其实是 `-Fast`」）。
- **自检的退出码有三档，引用方必须区分**：`0` = 全部用例匹配；`1` = 有用例不匹配（检测分支可疑）；
  `3` = **INCONCLUSIVE**，harness 自身没跑完（临时副本拷贝/清理失败、路径超限）。`3` 是刻意与 `1`
  分开的：曾经两者同为 `1`，于是「临时副本清理失败」被读成「某个检测分支坏了」，排查方向一开始就错。
  **`3` 既不是 PASS，也不是守门的结论**，不得被任何报告当作其中之一引用。
- 自检的临时副本位于 `%TEMP%\tls`（取短前缀），且**刻意不拷贝**深层逐用例产物
  （`app/build/outputs/androidTest-results`、`app/build/reports`、`app/build/test-results`、
  `app/build/outputs/logs`）：设备名 + 测试类名 + `logcat-` 前缀会让单条产物路径达到 ~240 字符，
  加上副本前缀就会越过 Windows 259 上限并让清理抛 `DirectoryNotFoundException`。
  APK 产物（`app/build/outputs/apk`）保留，dex 阶段依赖它。每次拷贝后会**实测并打印**最长深层路径
  长度（当前 234/259），越限即 fail closed（退出码 `3`），让余量可见而不是等它崩。

## 领域层入口索引（`:core`）

`:core` 只放供应商无关的领域契约，位于 `core/src/main/java/com/treelearn/core/domain/`。
新增领域类型时**同步补进本表**，否则下一次复核只能靠读代码猜入口。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `Topic` | 主题身份 | 不保存独立主题名 |
| `Branch` / `BranchSource` / `BranchAnchor` | 分支身份、非根分支的来源与锚点 | 来源只能由 `STABLE` 的尝试产生；`restore` 仅用于只读再水合 |
| `Message` / `MessageRole` | 分支内固定逻辑位置 | 用户正文创建后不可变；AI 消息只存当前展示版本与当前操作引用 |
| `AnswerRevision` | 一次成功生成的不可变稳定版本 | 全字段 `final`、无 setter；`promptPolicyVersion` 非空且必须归属于某个 `PromptPurpose` |
| `ContextSnapshot` / `Source` / `SourceKind` | 非根分支的不可变出生上下文清单（只引用上游行，不复制回答正文） | 全字段 `final`、来源列表构造时拷贝并只读交出、无 setter；`payload`/`digest` 由构造路径派生，**调用方无法提供 digest**；`SCHEMA_VERSION` 只存在于列，不重复进载荷 |
| `ContextSnapshotCodec` | `:core` 唯一的快照载荷编解码：规范化 JSON + 整份 SHA-256 | 载荷每项只有 `kind`/`ref`；`decode` 按「版本 → 结构 → digest」固定顺序拒绝；digest 覆盖规范化字节，不依赖哈希遍历顺序 |
| `GenerationState` | 生成尝试的生命周期 | 终态封闭；只有 `STABLE` 可作分支来源；只有 `STREAMING_ANSWER` 能稳定化 |
| `GenerationCommand` | 一次生成请求，**提交前固定策略版本** | 版本必须非空且由声明用途拥有；否则返回领域错误 |
| `GenerationOperation` | 一次生成尝试（本包唯一可变类型） | 版本字段 `final`；缓冲区只在流式态可写；`STABLE` 只能经 `stabilize` 到达 |
| `ContextPlan` / `Entry` / `EntryKind` | 供应商无关的有序上下文（用户消息 / 稳定版本 / 摘要） | 没有任何字段能装下 system 消息；`chars()` 是精确内容字符数、不是 token 估算；本类不裁剪、不压缩 |
| `BuildContextPlanUseCase` | 用出生快照 + 本分支稳定轮次组装有序 `ContextPlan` | 出生半段**只**跟快照钉住的引用，绝不读 `Message.activeRevisionId()`；本分支自己的轮次才跟当前展示版本；源解析不到、摘要缺失、超调用方已预留容量一律 fail closed，**不靠丢推理凑窗口** |
| `PromptSpec` | 版本化系统提示词的供应商无关身份 | 只含 `purpose`/`policyVersion`/`systemContent`；`of` 返回 `DomainResult`；空正文被拒；提示词正文不进数据库 |
| `PromptPurpose` | 提示词用途枚举与版本命名空间 | 版本形如 `<prefix>/MAJOR.MINOR.PATCH`；禁止应用版本号或时间戳 |
| `GenerationModel` | 模型档位 | 供应商 model 名只出现在 `wireId` |
| `DomainResult` / `DomainError` / `DomainErrorCode` | 业务失败的返回通道与稳定错误码 | 每个错误码都必须有生产者 |
| `Guard` | 构造期不变量检查与三条错误通道规则 | 见下 |
| `JsonText` | `:core` 唯一的结构化 JSON 读写（纯 JVM，不引第三方依赖） | 只接受对象/数组/字符串/整数；**布尔与 `null` 一律拒绝**（读不出就替它编一个值，等于猜）；数字语法收窄为「可选负号 + `0` 或非零数字串」，分数、指数与前导零一律拒绝，因此读一个数不必在 `Long` 与 `Double` 之间二选一；重复键、原始控制字符、未知转义、根值之后的多余文本一律拒绝；写出顺序由 `LinkedHashMap` 决定，故 digest 可复现 |
| `SearchQuery` | 搜索输入的归一化与 LIKE 转义（`:core` 唯一的查询参数产地） | `trim` + 空白折叠 + `toLowerCase(Locale.ROOT)`，归一化后为空即拒绝；`%`/`_`/`\` 逐个转义并显式 `ESCAPE '\'`；**空查询不可能退化成 `%%` 全表扫描**（构造时即抛，不靠调用方自觉） |
| `SearchHit` | 一条命中：命中字段、定位三元组与片段 | 字段枚举穷尽（标题 / 锚点 / 回答 / 思考 / 失败尝试的部分回答 / 部分思考）；`DISPLAY_ORDER` 是确定性全序，**不依赖 SQL 的返回顺序**；`locatorAnchor()` 给出跳转锚点，失败尝试的命中**不冒充**某个 revision |
| `ReadingAnchor` / `ReadingPosition` | 语义阅读位置：message/revision + 列表项偏移 + 视口偏移 | **不保存绝对 `scrollY`**（版本切换或字号变化后必然失效）；`resolve` 先按稳定 id 定位再夹取偏移，锚点失效时回退到**当前活动末端**并显式标记 `atLiveEnd` |
| `UnreadRollup` / `ViewportSeen` | 未读序号聚合与「真的看过」判定 | 未读只由稳定回答序号推导，**布尔标记表达不了**这件事；折叠祖先吸收子孙未读，归档分支连同子树离开树且**不泄漏给父级**；只有「最新且带 revision 的 AI 位置」真的进入视口才推进 `lastSeen`，推荐与摘要**不制造未读** |

**错误通道规则（改 `:core` 前必读）**：`Guard` 的类注释是唯一权威正文。三条通道按
「调用方做错了什么」划分——缺参数/格式非法抛 `IllegalArgumentException`；参数合法但接收者
状态不对抛 `IllegalStateException`；参数合法、状态有效而业务规则拒绝才返回 `DomainResult` 失败。
`DomainResult` 是**第三通道**，返回它的方法在前两种情况下仍须抛异常。

**已声明的例外清单也只在 `Guard` 类注释里维护**（**不要在这里复制一份**，失败案例库要求「例外清单只存一处」）：
三条通道的**第一条**存在已声明的例外，涉及**哪些入口、共几处、理由是什么**一律以 `Guard` 类注释的
「The declared exceptions to the first bullet」段为唯一权威正文，本文件不复制入口名与数量。新增同类入口
必须登记到该段，不要让 README 或第二个类持有副本。

**供应商边界（不要放进 `:core`）**：上下文封装标签 `<treelearn_reasoning>` / `<treelearn_answer>` 与引用信封 `<treelearn_quote>` 只存在于 `:ai` 的 `DeepSeekContextEncoder`；`ContextPlan` 没有能装下它们的字段，UI 也不显示它们。system 提示词恰好一条且位于首位，由 `:ai` 的请求组装器（`DeepSeekRequestAssembler`）负责，出生快照与 `ContextPlan` 都不含 system。

**校验顺序**：参数形状先于业务判定，按参数声明顺序执行。因此一次同时带「畸形参数」与「不可用版本」
的调用抛 `IllegalArgumentException`，不会用业务码遮住调用方的 bug。

## 持久化入口索引（`:data`）

`:data` 的本地 SQLite 入口位于 `data/src/main/java/com/treelearn/data/db/`。
**DDL 只有一处产地**：任何其它文件（含 DAO）都不允许出现 `CREATE`/`ALTER`/`DROP`，
列名与表名一律引用这里的常量，不写字面量。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `TreeLearnSchema` | 七张永久表的唯一 DDL 常量源：表名/列名常量、`CREATE_STATEMENTS`、索引、触发器、前向迁移注册表 | DDL 由列常量拼出（改名不会漂移）；时间列一律 `INTEGER` UTC epoch millis，禁用 SQLite 时钟；`DATABASE_VERSION` 与迁移链一致 |
| `TreeLearnDatabaseHelper` | 唯一可建库/升级的入口：`onCreate` 执行 `CREATE_STATEMENTS`，`onUpgrade` 走前向迁移，`onConfigure` 开启外键 | 每次打开都**回读** `PRAGMA foreign_keys` 并要求为 `1`，否则 fail closed；拒绝降级（`onDowngrade` 抛异常而不是带着半懂的 schema 跑） |
| `DatabaseTransactionRunner` | 唯一的显式事务边界 | 嵌套开启被拒绝（`IllegalStateException`），不静默并入外层事务；失败整体回滚并原样向上抛；**工作体若自行结束事务，runner 事后抛错——但那次提交已经生效**（检测而非阻止，见下） |

行级访问位于 `data/src/main/java/com/treelearn/data/db/dao/`。DAO 只做「一行 ↔ 一个 `:core` 实体」，
不开事务（跨表原子性由 `DatabaseTransactionRunner` 在调用方提供），也不写任何 DDL。
**本包有三个例外**（都不是 DAO，而是跨表用例、自己持有事务边界）：`GenerationStabilizer`、`FirstTopicCreation` 与 `BranchCreation`，理由见下表。

| 类型 | 覆盖 | 关键不变量 |
| :--- | :--- | :--- |
| `TopicDao` | `topics` | `lastVisitedBranchId` 由真实导航填写，插入时为 `null`；删除主题后整棵树靠外键级联，**本类不含任何递归删除**；`listOverviews`（T-032 新增，只读）一次取回主题的根标题、首个问题、摘要信封、分支数、派生未读总数与最近使用时间，**不建表、不改 schema、不写迁移** |
| `BranchDao` | `branches` | **`BranchSource.restore` 在 `:data` 生产源码里只出现这一次**（只读映射；检索命令与输出见 `artifacts/evidence/T-022-restore-location.txt`）；`insert` **不写** `context_snapshot_id`（与快照互为外键），所以它**不是**创建分叉的入口——分叉只走 `BranchCreation.create`（见下一行），`insert` 与 `setContextSnapshot` 仍公开，但只是验收套件直接驱动的底层步骤；读路径遇到半截锚点或空快照**大声失败**，不修数据、不降级 |
| `BranchCreation` | `branches` + `context_snapshots`（跨表） | **非根分支的唯一创建入口**：分支行 → 快照行 → 指针三步在**一个** `DatabaseTransactionRunner` 事务里写完，任一步被拒则三步整体回滚，因此调用方不必「记得补第二步」。跨主题来源判定复用 `FirstTopicCreation.sourceRefusal`，本入口不写第二份。**不变量由两半承担，且都不在 DDL**：① 生产代码里只有本入口写非根分支行；② 读路径 `BranchDao.findById` 对指针为 `NULL` 的非根行大声失败。库层表达不了它——CHECK 是立即约束、不能延迟，而快照行必须引用正在插入的分支行；延迟外键或加列属 T-004 的 schema 变更，**已考虑并刻意不采用** |
| `MessageDao` | `messages` | `user_content` 只对 `USER` 行写入，且创建后不可变（触发器兜底）；`updateActiveReferences` 只移动当前版本/尝试引用，不覆盖任何历史；**§6.2.19 引用文字的两种拼法（域的 `''` ↔ 列的 `NULL`）在本类各存一处**（`quoteColumnValue` / `quoteFromColumn`），「引用只许出现在非空 `USER` 行」由库层触发器判定，本类不重复校验 |
| `Rows` | — | 共享的类型化 `Cursor` 读取，把「可空整数」这类区分 `NULL` 与 0 的约定收在一处 |
| `AnswerRevisionDao` | `answer_revisions` | **`AnswerRevision.restore` 在其只读映射 `read(Cursor)` 里，是全仓唯一的生产调用点**（检索命令与输出见 `artifacts/evidence/T-023-restore-location.txt`）；写路径一律走 `AnswerRevision.create`，即 `insert` 不接受「已存在的实体」以外的形态。边界写明在 javadoc 里：库层只要求 `length(answer_content) > 0`，领域要求非空白，因此**纯空白回答会被本映射以 `IllegalArgumentException` 大声拒绝**，而不是作为空回答落库 |
| `GenerationOperationRow` | `generation_operations` | **整行只读映射**，不进 `:core` 实体；全部字段 `public final`，构造器包私有，只有同包的 DAO 能造。`toString()` 只打印正文的长度、不打印正文 |
| `GenerationOperationDao` | `generation_operations` | 按**行**读写，`GenerationOperation` 上游**没有 `restore`**（全仓零命中），行形态是读取一次尝试的唯一方式；`recordProgress` 用 `SQLiteStatement` 的 `col = col \|\| ?` **原子原地追加**，不 read-modify-write；`markStable` 是**包私有**的，外部没有第二条路径可以把尝试置为 `STABLE` |
| `GenerationStabilizer` | `answer_revisions` + `generation_operations`（跨表） | **跨表用例之一，自己持有事务边界**：它写的那对（revision 行 + 尝试置 `STABLE`）只有在一起才有意义，把边界留给调用方等于把中间态变成调用方的 bug。**拒绝必须抛异常**（`DatabaseTransactionRunner` 会提交工作体返回的任何东西，从工作体返回=提交），且两次写**顺序为 state 先、revision 后**——顺序的选择是为了让「万一退回从工作体返回」这个缺陷**可观测**，该配对由 `stabilizeRollsBackBothWritesWhenTheRevisionNumberIsAlreadyTaken` 钉住 |
| `ContextSnapshotDao` | `context_snapshots` | 只搬「行 + 不透明载荷」：`payload` 原样写入、原样读回，**本层不解析、不截取、不重建 JSON**（载荷 schema 属 `:core` 的 `ContextSnapshotCodec`，见上表）。`uq_context_snapshots_branch` 保证每个分支至多一行；唯一冲突与 NOT NULL 都由库层拒绝且**不给领域码**——`:core` 没有「该分支已有出生快照」这类业务码，硬造一个就是无生产者的码，诊断成因是调用方的事。快照不可改写、不可单独删除（触发器），只随所属分支级联消失 |
| `AiArtifactDao` | `ai_artifacts` | 派生结果的**基线 CAS**：写回是单条 `UPDATE … WHERE kind=? AND scope_id=? AND based_on_revision_id IS NOT NULL AND based_on_revision_id=? AND input_digest=?`；受影响行数 1 → `APPLIED`、0 → `DISCARDED_STALE`（**正常路径，不是失败**）、>1 → 抛错。**禁止「先 SELECT 再 UPDATE」**——那会把基线复核移到真正改写数据的语句之外。`insert` 自带 `WHERE NOT EXISTS`，因此「每个 (kind, scope) 只有一行」是事实而非注释。被 `ON DELETE SET NULL` 清空基线的行**永远判 stale**并保留旧 payload（取舍见 T-024 交接备忘）。`scope_id` 是多态键、**没有外键**：删除 scope 不会自动清理 artifact，`deleteByScope` 是调用方义务 || `ConstraintFailures` | — | 约束冲突 → 领域错误的**唯一**共享翻译点；**刻意不建「扩展码 → 错误码」总表**，因为同一个扩展码（2067 UNIQUE）在两张表上含义不同，只能按「扩展码 + 冲突列清单」一起判 |
| `FirstTopicCreation` | `topics` + 根 `branches` + 两条 `messages` + `generation_operations`（跨表） | **首题的五类行在一个事务里全成或全败**：复用 `DatabaseTransactionRunner`，不新造事务机制；留下「有 topic 没有根分支」或「有分支没有 attempt」都会让 T-008 的恢复推导出不可重试的空树。**首题不写快照**——根分支没有父分支来源，`context_snapshot_id`/`source_*` 必须是 `NULL`，不要为了「看起来完整」补一行快照（分叉的两步写属 `BranchCreation`）。**跨主题 source 判定是全仓唯一一处**（`sourceRefusal` → 纯判定点 `decideSourceRefusal`）：`source_*` 是 `ON DELETE CASCADE`，跨表规则写不出 CHECK，所以必须在**同一事务内**判定，`BranchCreation` 复用同一入口而不是再写一份。重复 topic id 按**实测扩展码 1555 + SQLite 自己的小写约束文本**判定（纯判定点 `classifyStorageRefusal`），既不只看码（同码还有 `messages.id` 等主键）也不只看措辞（首版写成大写，编译正常却永不匹配，重复创建全被降级成 STORAGE_REFUSED）。失败一律以分类 `Refusal` 返回，不抛裸异常穿透调用方 |
| `SearchDao` | `branches` + `messages` + `answer_revisions` + `generation_operations`（七臂 `UNION ALL` 跨表） | 本仓唯一读多表的 SQL（技术方案 §6.3）。七个字段各一臂：标题、用户问题、分叉锚点引文、当前展示 revision 的回答与思考、失败尝试的部分正文。**通配符不内联**——七处全是 `LIKE ? ESCAPE '\'` 加参数绑定；回答/思考臂经 `messages.active_revision_id` 连接，所以「没有位置在展示的过期 revision」在结构上不可达，仍被引用的那条只能经**分叉自己的锚点臂**（`b.source_revision_id`）找到；七臂一律 `b.archived_at IS NULL`。union 列名取自第一臂而三张表都有 `id`，因此 branch/message/revision 键必须别名，否则 `Rows.string(cursor, "id")` 会静默取错键 |
| `ReadingPositionDao` | `branches` 的四个 `reading_*` 列 | 阅读位置是**语义锚点**（message + revision + 两个相对偏移），不是滚动像素；四列同生同灭，`reading_anchor_message_id` 为 `NULL` 即「没有位置」（正是 `ON DELETE SET NULL` 之后的形态）；消息在而偏移缺 = 半截行，**大声失败**而不是当成「从顶部开始」 |

**stabilize 的两个不变量，都是「用夹具篡改过的行」才可区分的那种**：

- **版本从行原样复制，不重新推导**。`prompt_policy_version` 取自 operation 行，**不是**从用途常量、
  实时注册表或「该用途当前版本」推出来的。行完好时这些写法产生同一个字符串——正因如此，钉住这条的
  夹具**必须篡改行**（把行的版本改成另一个用途拥有的版本），否则测不出区别。提示词设计 §3.2 要求一次
  回答只有在记录下**真正产生它的那个版本**时才可复现；重新推导会在注册表前进的第一次就悄悄改写历史。
  真机用例：`stabilizeCopiesThePinnedVersionFromTheRowVerbatim`。
- **调用方义务：增量必须已经落盘**。revision 由行拼出，还留在内存里的 delta 会被冻成截断的回答。
  那次必须前置的 flush 属于协调器的批量窗口（T-008），本入口观测不到——因此这条写成 javadoc 里的
  「调用方义务 + 本类无法观测」，而不是让本入口「自己管住」。

**再水合的入口与边界**：读路径用 `BranchSource.restore` / `Branch.restore` / `Message.restore`
（`Topic` 有公开构造，直接使用）。这些方法**只做身份与形状校验，不重做业务判定**——重发一次
「创建」判定就等于把一次读取变成一次状态变更。因此**写路径禁用 `restore`**：它会让「先有一个合法
状态」这一步被绕过，正是 `restore` 命名刻意显眼的原因。测试夹具同样受此约束，并改走真实创建入口
`BranchSource.fromOperation(..., GenerationState.STABLE, ...)`。

**「非根分支必有出生快照」由谁保证**：不是 DDL，而是**单一写入口 + 读路径大声失败**这两半。写侧在
生产代码里只有 `BranchCreation.create` 写非根分支行，它在**同一个**事务里补齐快照行与指针，因此
「指针为 `NULL` 的半截行」在正常路径上不可达；读侧 `BranchDao.findById` 读到这样的行**直接抛异常**
（消息里含 `no birth snapshot`）而不是静默容错——那样的分支来源无法解析，是创建用例绝不该暴露的中间态。
两半都由 `BranchCreationInstrumentedTest` 覆盖：本轮按用户指令（2026-09-21）不使用模拟器，该套件只做
**编译验证、未执行**，设备结论按 deferred 记录，见 `artifacts/evidence/T-009-green.txt`。
**DDL 侧为什么空着**：`branches.context_snapshot_id` 与 `context_snapshots.branch_id` 互为外键，而
SQLite 的 CHECK 是**立即**约束、不能延迟——插入分支行的那一刻快照行还不存在，写不出「非根 ⇒ 指针非空」；
改用延迟外键或新增列属于 T-004 的 schema 变更，**已被考虑并刻意不在本次采用**。原始 SQL、未来迁移或
还原备份仍能把半截行写进库里，所以读路径那一半不是冗余。

**锚点四字段同生同灭**：`branches` 的 `anchor_start_inclusive` / `anchor_end_exclusive` /
`anchor_quote` / `anchor_hash` **要么一起为空**（整条回答分叉），**要么一起非空且 `start < end`**
（ranged 分叉）。划界是刻意的，且**只有方向这一条是两层都判的**：

- **库层判分组与方向**。分组没有第二个判定点——写路径上没有任何领域对象把四列当一个整体校验，而
  这张表也会被原始 SQL 与未来 DAO 写。方向（`start < end`）库层也判，**这是刻意的重叠**：让方向
  颠倒的行在写入时就带着指向写方的堆栈被拒，而不是先落库、再让整个主题读不出来（读路径遇到被拒的
  锚点会抛异常，`findByTopic` 会因此整列失败）。重叠的代价有界——两层对任何输入结论一致，调用方
  观察不到两种结果。
- **内容规则只在 `BranchAnchor.ofRange` 一处判定**：起点非负、quote/hash 非空、quote 长度恰好等于
  偏移跨度。**不要把它们抄进 DDL**——那会造出第二个必须同步维护的判据来源，本仓库已经为此付过两次
  代价。库层放行而被 `ofRange` 拒绝的行（`start = -1`、`quote = ''`、长度不符）只能以**大声失败**
  被读出：`BranchDao.readSource` 把领域层的拒绝连同 `ANCHOR_INVALID` 与原因一起抛出，真机用例见
  `TopicBranchMessageDaoInstrumentedTest.readSourceFailsLoudOnARowTheCheckWouldHaveRejected`。
  「库层确实放行」这半边在该用例里是**可执行事实，不是对 DDL 的推断**：那条样本是在 CHECK **打开**
  的状态下用 `execSQL` 种进去的，若库层拒它，插入语句自己会抛 `SQLiteConstraintException`，用例当场
  失败。同用例里另外两行（半截锚点）才需要 `PRAGMA ignore_check_constraints=ON`——那正是「未来迁移 /
  手工 sqlite3 会话 / 还原备份」把坏行写进来的样子。

**在 schema 层被硬化的不变量**（不是「靠 DAO 自觉」）：`answer_revisions`、`context_snapshots`
整行不可 `UPDATE`；`generation_operations` 一旦进入终态不可再改；`messages` 中 `USER` 行的正文
不可改；`branches` 的锚点四字段同生同灭。因此一次误写会**当场失败**，而不是悄悄改写历史。
四条不可变性由触发器实现、锚点约束由 CHECK 实现，见 `TreeLearnSchema.CREATE_TRIGGER_STATEMENTS`
与 `TreeLearnSchema.BRANCHES_DEFINITIONS`。

另有**两条「禁止单独删除」触发器**：`answer_revisions` 与 `context_snapshots` 只允许随其宿主
（message / branch）一起消失，不允许 `DELETE FROM` 直接删。原因是 `branches.source_revision_id`
带 `ON DELETE CASCADE`——直接删一条稳定回答会**连带删掉以它为来源的整棵分叉子树**，这是远超
「删除该子树」授权范围的数据丢失。两条触发器只在宿主行仍存在时 abort，因此「先删宿主、子行跟随」
的合法级联不受影响，两半都由 `SqliteSchemaInstrumentedTest.immutableRowsRefuseDirectDeleteButAllowTheOwnerCascade`
实测。

**事务 runner 的能力边界**（不要读成「无法被绕过」）：工作体拿到的是 runner 自己开事务的那个
`SQLiteDatabase`，平台 API 允许它自行 `setTransactionSuccessful()` + `endTransaction()` 提前提交，
runner **阻止不了**，只能在事后检测到并抛 `IllegalStateException`。此时调用方收到的异常意味着
「事务边界被破坏、库里可能已有部分写入」，**不等于**「什么都没发生」。该语义由
`transactionRunnerRejectsNestedOpenAndStolenTransactions` 钉死（断言那行真的可见），不是靠类名推断。

**两条偏索引（partial unique index）**必须保持「部分」语义，否则产品功能会直接坏掉：
`uq_branches_root`（每主题仅一个根，但同主题可有多棵子树）与 `uq_generation_operations_active`
（每分支最多一个进行中的生成，终态操作可与活动操作共存）。两者都由
`:app` 的 `SqliteSchemaInstrumentedTest` 用「允许的那一半必须成功」反向证明其部分性。

## 应用层入口索引（`:app`）—— 生成协调器（T-008）

生成的生命周期由应用级协调器持有，页面只提交与观察。
`app/src/main/java/com/treelearn/app/generation/` 的九个类型就是全部入口（重试/重置/切版另加 2 个，见下文 T-010 小节）：下表六个生产入口，
外加三个**纯 JVM 决策缝**——`LiveAttempt`（单次尝试的字段）、`LiveAttempts`（晚到事件判定）、
`RecoveryScan`（启动恢复判定）——时钟与调度都可注入，不需要模拟器即可测试。
**SQLite 是权威**：协调器里的 `Map` 只是「此刻哪一路在飞」的调度缓存，进程重启后它为空而行还在，
启动恢复据此重建结论。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `GenerationCoordinator` | 提交、调度、批量落盘、取消、启动恢复、观察接口 | 每个分支同时只有一个 attempt（跑着或等着）；每个 attempt 只写一次终态；stable 由领域判定，协调器只问不判 |
| `GenerationScheduler` | 并发上限与 FIFO 的纯决策 | 上限 2；等待按到达顺序；`BRANCH_BUSY` 的拒绝**不占槽位、不占队列位**；只有跑着的那一路离开才放槽并唤醒最老的等待者 |
| `StreamBatch` | 250 ms / 256 字符写窗口 | 两路字符合计计数；窗口自**第一个未刷盘 delta** 开始（空批次永不到期）；`onFlushed` 关闭窗口，下一个 delta 重新开窗 |
| `GenerationGateway` | 协调器与 `:ai` 之间的端口 | 签名里没有 API key：密钥由装配层注入，协调器不可能把它带进日志或事件 |
| `AiGatewayStreams` | 生产装配：`AiGateway` + `KeystoreSecretStore` | 读出的 key 用后 `Arrays.fill` 擦除；`SecretStoreException` → `onTerminal(FAILED, keyMissing())`，不抛进网络线程 |
| `GenerationTelemetry` | 脱敏事件出口 | 接口只能携带 operationId / branchId / 字符数 / 错误类别，**正文进不来**：脱敏由类型保证，不靠调用方自觉 |
| `LiveAttempts` | 「这个回调还是当前 attempt 的吗」的注册表 | 一有结论就离开注册表，此后同 operationId 的事件 `isCurrent=false` 被丢弃；同一命令只有一个活动 attempt，原位重试天然让旧 operation 变陈旧 |
| `RecoveryScan` | 启动恢复的判定（事务之外） | 目标只来自领域的 `GenerationState.recoveryTarget()`；被扫的遗留行必须变成终态，已是终态的行**拒绝**而不是编一个新结论 |

**运行时事实（技术方案 §7 / §9.2 / §8）：**

- 应用内并发上限 **2**，其余按到达顺序 FIFO 等待；同一分支第二次提交被 `BRANCH_BUSY` 拒绝。
- 部分 reasoning/answer 按 **250 ms 或 256 字符**（先到者）批量写库；不足阈值的内容留在内存，
  终态之前一定会 flush。
- 提交顺序：先组装（失败 → `PROMPT_BUILD`，不开连接、不占槽位）→ 校验请求记录的版本与命令钉住的
  版本一致（不一致 → `VERSION_MISMATCH`）→ 占位 → **先写 operation 行再开连接**。
  首次根问题没有可写的行，走 `submitFirstTopic`：行在 provider 2xx 的 accepted 回调里写，
  写不成功就关连接、不读正文。
- 启动恢复 `recover()` 在一个事务里扫完：`STAGED` → `CANCELED`（从未到达 provider），
  其余进行中 → `INTERRUPTED`（保留部分内容、可原位重试）；**不自动重发**。
- 终态唯一：只有「流正常结束且最终回答非空」才可能 STABLE，这条判定属于
  `GenerationOperation.stabilize` 与 `GenerationStabilizer`，协调器只负责把结果落下去；
  失败、取消、进程中断都不产生 `answer_revisions`。
- 取消先中止连接再写 `CANCELED`；此后同一 operationId 的 delta / completed 一律按
  「已不是当前 attempt」丢弃，并记一条脱敏的 `STALE_DISCARDED`。
- 上述四类决策（并发与 FIFO、写窗口、晚到丢弃、恢复判定）都是**纯函数**：`Clock` 注入、无 sleep、
  不引 Android 类型，由 `:app:testDebugUnitTest` 的 JVM 套件固定；必须落到 SQLite 事务里的那一半
  留在 instrumentation 源码中。
- 从不记录：API key、system 提示词正文、问题、回答、推理正文、快照 JSON。

**已知缺口与记录的偏差（不要读成已解决）：**

- **本切片还没有生产消费者**：仓库里没有 `Application` 子类，装配层也没有任何位置 new 出协调器，
  页面、重试与首题建树属后续任务。协调器的可执行证据因此分两层：调度、刷盘、晚到丢弃与恢复判定
  由 JVM 套件证明；端到端路径（真机 SQLite 上「先落库再开连接」、`recover()` 的原子收敛）
  本轮**没有执行**，见下一条。
- **本轮不使用模拟器**（用户指令 2026-09-21）：`GenerationCoordinatorInstrumentedTest` 只做
  **编译验证**（`:app:assembleDebugAndroidTest`），不运行。凡必须上真机才能证明的验收项，
  一律记为 `deferred — emulator not used this round (user instruction 2026-09-21)`，
  既不算 PASS，也不是「看起来通过」。
- 稳定化（`GenerationStabilizer` 自己持有事务边界）与位置记账（`settlePosition`）是**两个事务**，
  中间会出现「revision 已落库、位置尚未指向它」的窗口；合并需要一次嵌套提交，故保留并记录。
- `complete()` 中 stabilizer 抛出存储异常时按失败收敛（释放槽位、不重发），
  **该分支没有测试覆盖**——是声明的表面，不是已验证的事实。
- 规模：本切片 **765 判定行 / 1116 物理行**（`artifacts/evidence/T-008-sizing.txt`），
  超 200 行软上限、也超 500 行硬上限；**没有用户批准的规模例外**，属待裁定偏差，
  不得当成已批准例外引用。

## 应用层入口索引（`:app`）—— AI 派生产物与过期保护（T-011）

标题、分支摘要与要点、回答推荐、首页推荐和上下文压缩都是**派生物**，不是权威事实：它们可以重算，
因此也**会过期**。`app/src/main/java/com/treelearn/app/artifact/` 的十三个类型就是这一层的全部入口。
判据在技术方案 §10，字段与用途在提示词设计 V1.0 §6；本层不写 SQL（只调用 `:data` 的 DAO），
也不组装 provider JSON（只把结构化信封交给 `:ai` 的组装器）。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `ArtifactKind` | 六个派生产物 kind ↔ `PromptPurpose` 的**穷尽映射**，以及每个 kind 的 scope 与基线类别 | 无默认回退：`LEARNING_ANSWER` 取不到 kind（消息里点名它），未知 wire id 抛错；kind 与用途前缀分列两栏存储（用途在 `prompt_purpose` 列） |
| `ArtifactInputs` | 六种结构化输入的唯一产地，全部由 `JsonText` 渲染 | 输入是 JSON 值树，不是拼接字符串；每种用途只拿到技术方案允许的字段——首页推荐只拿轮廓与上限，**没有能装下对话正文的字段** |
| `TreeOutline` / `Entry` / `Level` | 推荐输入能看到的树轮廓 | 每项只有 `level`/`title`/`source_quote`/`summary` 四个字段，没有消息、回答或推理字段，因此"只提交轮廓"是形状而非约定 |
| `ArtifactPayloadCodec` | 六种输出信封的严格解析 | 围栏、解释性前后缀、两个对象、数组根、裸字符串、尾逗号、未加引号键、截断一律 `PAYLOAD_INVALID`；接受字段集合**按相等比较**，多一个字段即拒；数字上限 `itemCeiling` < 1 是调用方 bug 而非空数组 |
| `ArtifactPayload` / `DerivedResult` / `ArtifactOutcome` | 解析后的字段、结果或分类拒绝 | 拒绝结果**不携带半个载荷**（"从文本里猜字段"正是设计禁止的行为）；`ArtifactPayload.canonicalJson()` 按设计字段顺序重编码，provider 的拼写与多余键进不了库 |
| `DerivedRequest` | 一次派生调用：kind、scope、基线、信封、固定用途版本 | 版本从用途自己的注册表读出，调用方无法钉错；派生的 system 正文永远是该用途自己的，**教学正文不可达**；信封必须是**一个** JSON 对象，否则组装器拒绝 |
| `DerivedOperation` | 端到端一次派生：组装 → 调用 → 校验 → 条件写回 | 只有 `APPLIED` 写过行；构建失败、调用失败、载荷非法、压缩丢推理或没缩小，全部**保留旧 artifact**且不降级到主回答提示词 |
| `DerivedStore` / `DerivedCaller` / `DerivedTelemetry` | 写回端口、调用端口、脱敏出口 | 写回端口的读基线与写库在同一事务内；调用端口是唯一网络缝；脱敏出口**没有参数能携带正文**，只有用途、随机 id、digest 前 8 位与状态 |
| `SqliteDerivedStore` | 写回端口的 SQLite 实现：按 kind 回答"当前基线"并委托 `AiArtifactDao` | 分支不存在、主题不存在回答 `null`，`null` 永不写库——这正是"删除 scope 后的回调不能重建孤儿 artifact"；不新增任何 SQL 语句 |

**「结果写回时再比较当前基线」怎么落地**（§10）：`DerivedOperation.writeBack` 在**一个事务**里问
`DerivedStore.liveBaseline(...)`（分支的最新稳定 revision、或主题当前轮廓 digest、或 scope 已消失），
与请求记录的基线相同才写，不同即 `STALE_DISCARDED` 并保留旧 payload。基线是 revision 的（摘要、回答推荐、
上下文压缩）以 `based_on_revision_id` 为准；输入本身即基线的（根标题、分支标题、首页推荐）以 `input_digest`
为准。**基线前移**时写回走 `deleteByScope` + `insert`（T-024 明确要求"重新派生必须显式 delete + insert"），
同一基线上则走 `AiArtifactDao.applyIfBaselineMatches` 的单语句条件更新。

**上下文压缩的失败关闭**（提示词设计 §6.4）：输入必须同时带问题、reasoning 与 answer，缺 reasoning 直接
`REASONING_OMITTED`（**不以删掉 reasoning 降级**）；解析失败/空 summary 为 `SUMMARY_EMPTY`；压缩结果不小于
它要替换的内容为 `OVER_WINDOW`。三种失败都不写库、都保留旧 artifact，并把结论交给调用方去关闭本轮主回答。
`OVER_WINDOW` 的判据是「summary 字符数 >= 被压缩轮次的 问题+reasoning+answer 字符数之和」（T-011 的解释，
见 `.trae/tasks/T-2026-09-20-derived-ai-artifacts-011.md` 的 Implementation notes）。

- **本轮不使用模拟器**（用户指令 2026-09-21）：本层的 SQLite 路径**没有执行**——
  `SqliteDerivedStore` 只做**编译验证**，其 `liveBaseline` 的折叠与 `write` 的 insert/replace 选择
  没有任何测试跑过；底层 CAS 语句属 T-024，另有真机套件（本轮同样 deferred）。
  凡必须上真机才能证明的验收项一律记为 `deferred — emulator not used this round (user instruction 2026-09-21)`，
  既不算 PASS，也不是"看起来通过"。
- **端口还没有生产消费者**：装配层（谁调用派生用例、`OutlineDigest` 由谁提供）属首页/对话 lane；
  在那之前 `DerivedStore` 的生产实现是"已写好但未接线"，这条是声明的缺口，不是已验证的事实。
- 规模：本切片 **726 判定行（13 个新文件 694 + `JsonText` 新增 32）/ 1136 物理行**
  （`artifacts/evidence/T-011-sizing.txt`），超 200 行软上限、也超 500 行硬上限；
  **没有用户批准的规模例外**，属待裁定偏差，不得当成已批准例外引用。

## 应用层入口索引（:app）—— 首页与设置（T-012）

首屏是**一个页面**（PRD 6.2.16 取消了「空态页 + 新建主题页」的拆分），因此 `MainActivity` 是唯一
Activity、唯一宿主，首页与设置页是同一个 Activity 的两个状态，靠替换宿主子视图切换；对话页
（T-013）、树画布（T-038）与导航页（T-039）都用同一种方式接上（各见对应小节）；搜索页的入口仍由搜索 lane
继续加进来（T-040，已于 2026-09-22 落地，见「搜索页与首页搜索入口（T-040）」）。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `AppContainer` | 依赖装配：真实 `SettingsRepository` + `KeystoreSecretStore`、单后台线程、主 Looper 的 `UiThread` | 只持 application context，不持 Activity；所有 store 调用串行在一条线程上 |
| `MainActivity` | 应用外壳：在 `R.id.shell_host` 里换屏，实现 `HomeSink` + `SettingsSink` + `ChatSink` + `TreeCanvasSink` + `NavigationSink` + `SearchPresenter.Sink` | 自己不持草稿、不持状态、不做决策；每个屏幕的系统返回由它统一接管（见「系统返回」） |
| `ui.HomePresenter` | 首页的全部决策 | 草稿只在 accepted 分支清空；推荐点击先写入草稿再走同一条 `onSubmit()`；缺 Key 时只提示并跳设置，**不写任何行** |
| `ui.HomeViewState` | 首页可绘制的全部状态（相位/草稿/Key/模型/思考/列表/推荐/提交中/提示） | 四相位 LOADING/EMPTY/ERROR/READY；`Notice` 是分类不是句子；两个列表不可变 |
| `ui.SettingsPresenter` | 设置页决策：读 Key 状态、保存/替换、删除 | Key 文本从不进入任何 ViewState；缓冲区归它所有并 `finally` 擦除，成功失败都擦 |
| `ui.SettingsViewState` | 设置页可绘制状态（`keyLine`/`keyMask`/`notice`） | **没有模型与思考字段**——见下「交互事实」 |
| `ui.KeySnapshot` | Key 的三态（无 / 不可读 / 可读）+ 掩码 | 掩码是唯一能离开本类的形式；短于 12 字符的 Key 全掩，不部分泄露 |
| `ui.HomeData` / `ui.RootQuestionSubmitter` / `ui.KeyVault` / `ui.SettingsStore` / `ui.UiThread` | 首页与设置页的端口 | 页面不直接访问 DAO / 网关；`HomeData.recommendations` 失败**返回**兜底条而不是抛错 |
| `screen.HomeScreen` / `screen.SettingsScreen` | 两个视图：只转发手势 + 按 ViewState 绘制 | 唯一本地状态是「回填抑制」（渲染自己写进去的文本不再回调 presenter，避免光标跳动） |
| `screen.ModelThinkingPicker` | 输入框内的模型/思考贴附式弹层 | 锚定当前标识；下方空间不足时向上翻转，始终不遮挡触发它的标识 |
| `screen.RecommendationMarquee` | 首页推荐条 | 每条 chip 画两份、按一份宽度回跳，接缝永不可见；触摸暂停、离手 2.6 s 恢复；离屏/未附加/窄到不可滚动时停滚 |
| `wiring.AndroidSettingsStore` | 真实存储：`SettingsRepository` + `KeystoreSecretStore` | app 层唯一接触密钥材料的地方，且只为生成掩码；`read()` 的缓冲在 `finally` 擦除 |
| `wiring.HomeWiring` | 首页的**真实**数据路径（T-032）：主题列表 + 首题提交，实现 `HomeData` 与 `RootQuestionSubmitter` | 列表是 `TopicDao.listOverviews` 的一条只读 SQL；首题经 `FirstTopicCreation` 单事务写入、经 `GenerationCoordinator.submitFirstTopic` 受理；缺 Key 时返回可见的 `KEY_MISSING` 且**一行都不写** |
| `wiring.HomeStore` / `wiring.SqliteHomeStore` | 首页的端口与它的 SQLite 实现 | 端口只做存取、不重写判定；`recentTopics()` 不兜底、不造行，空库就是空列表 |
| `wiring.BranchHeading` | 卡片标题与对话标题的**唯一**判定点（存了标题用标题，否则用根问题） | 首页与对话读同一个方法，一个主题不可能两处叫两个名字 |

**请求预算（T-032 的一次决定）：**

- 技术方案 §9.2 与提示词设计 §5 规定了机制——先预留 system、当前问题与**最小回答**，再看历史放不放得下，
  放不下就失败关闭——但**没有给出任何权威数字**。接线时必须显式选定，于是这两个数字写在
  `wiring.PromptLimits` 一处，而不是散在调用点上：
  - `CAPACITY_CHARS = 60000`：`PromptBudget` 数的是 **UTF-16 字符**而不是 token（V1 不带 tokenizer）。
    60000 字符无论中英混排都留在 64K-token 上下文之内（一个汉字约一个 token、一个拉丁字符约四分之一），
    同时给回答本身留出余量。只在 provider 端超限意味着「在用户设备上失败」而不是「在屏幕上失败」，
    这正是预留要防的。
  - `MIN_ANSWER_CHARS = 512`：短于一段话的回答不是可用的学习回答，所以给回答留 512 字符，历史不得侵占。
    生成侧测试本来就用的这个数，于是全树只有一个 512。

**资源与尺寸事实：**

- `res/values/dimens.xml` 是这一层的令牌表（间距 4/8/12/16/24/32、`page_gutter` 20dp、
  `touch_min` 44dp、字阶 `text_display`…`text_cap`）。布局只引用名字，不内联 dp。
- 手机与平板**共用同一份列**：`layout/screen_home_body.xml`、`layout/screen_settings_body.xml`；
  `layout/screen_*.xml` 是整宽单列，`layout-sw600dp/screen_*.xml` 把同一个列 include 成
  `@dimen/content_measure`（640dp）并水平居中，两种配置因此不可能漂移。
- `content_measure` 声明在 **base** `values/dimens.xml`：只写在 `values-sw600dp` 会让 lint 的
  `MissingDefaultResource` 失败，进而拖垮 release 构建（`lintVitalRelease`）与 Java-only 守门的
  APK 级校验——本任务实际命中过一次，见 `artifacts/evidence/T-012-guard.txt` 记录的首轮 FAIL。
- 所有用户可见文案都在 `values/strings.xml`，且不含 Key、问题、回答或提示词正文。

**系统返回（不要在 `MainActivity` 里重写 `onBackPressed`）：**

- API 33+ 走框架的 `getOnBackInvokedDispatcher().registerOnBackInvokedCallback(...)`；API 28–32 走
  `dispatchKeyEvent(KEYCODE_BACK)` 回落；两条路都收敛到同一个 `onBackRequested()`（设置页 → 导航页 → 画布 →
  对话页 → 退出应用，见「导航页与归档 / 恢复 / 永久删除（T-039）」）。
- **不能用 AndroidX 的 `getOnBackPressedDispatcher()`**：lint 的 `GestureBackNavigation` 会这么建议，但
  `:app` 主代码的编译类路径解析出来是**零 AndroidX 坐标**（只有四个 project 依赖，见
  `artifacts/evidence/T-012-lint.txt` 的 `:app:dependencies` 输出），而 `androidx.activity` 会连同
  `androidx.core`/`androidx.lifecycle` 引入 `org.jetbrains.kotlin:kotlin-stdlib`，被 Java-only 规则当场拒绝。
- `android/app/build.gradle` 是 `abortOnError true`，所以这个 lint 错误会直接让 `gradlew check` 失败；
  本轮实测：带旧覆盖的副本 1 error / BUILD FAILED，改用框架路径后 0 errors / BUILD SUCCESSFUL
  （`artifacts/evidence/T-012-lint.txt` 的一红一绿）。

**交互事实（PRD 权威，与「设置页放模型开关」的直觉相反）：**

- V3.1 6.2.18 point 2 与 point 3 明确：模型与思考模式的入口**内嵌在问答输入框内**，首页根问题输入框
  与对话页追问输入框都提供该入口，**设置页不提供模型配置项**。因此 `SettingsViewState` 没有模型/思考
  字段；模型与思考由 `HomePresenter.onModelSelected` / `onThinkingToggled` 写进 `SettingsRepository`，
  设置页只负责凭据与数据边界说明。
- 切换只影响之后的新生成；已生成回答不被追溯改写。本层的写法就是「写偏好 + 改 ViewState」，
  不触碰任何回答或分支。

**已知缺口与记录的偏差（不要读成已解决）：**

- **首页数据路径已接线（T-032，2026-09-22）**：`TopicDao.listOverviews` 新增一条只读查询，一次取回
  「主题 + 根标题 + 首个问题 + 当前摘要 + 分支数 + 最近使用时间」；`wiring.HomeWiring` 用它画列表，用
  `FirstTopicCreation` + `GenerationCoordinator.submitFirstTopic` 建首个主题。占位类已整体删除，
  生产装配里不再有 `NOT_WIRED`。读者要注意的**残留缺口**：
  1. **按历史派生的首页推荐已接入，失败仍回退启动集合**。有主题且已配置 Key 时，
     `HomeWiring.recommendations(hasTopics)` 以主题 outline 触发 `HOME_RECOMMENDATIONS` 派生并读取
     当前 artifact；无 Key、网络失败或载荷非法时遵循 PRD 6.2.17 的兜底，因此「有主题」不再必然
     与「无主题」相同，但失败时仍会相同。
  2. **列表排序键是 `topics.updated_at`**：`listOverviews` 按 `updated_at DESC, id ASC` 排，相等时用 id
     兜底成全序；`updated_at` 由会话访问分支时刷新，所以「最近使用」与卡片上的排序是同一个事实。
  3. **新 SQL 的行级证据是 deferred，不是 PASS**：按用户 2026-09-21 的决定（放弃 emulator/真机轮次、
     由用户本人装 release APK 实测），`TopicDao.listOverviews` 与 `messages.quoted_text` 的真实 SQLite
     行为记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`，用例已写好并通过编译，
     但本轮没有执行（`artifacts/evidence/T-032-instrumentation-compile.txt`）。
- **本轮不使用模拟器**（用户指令 2026-09-21）：`:app:assembleDebugAndroidTest` 只做**编译验证**，
  不安装、不运行；`app/build/outputs/androidTest-results/connected/debug` 保持为空。必须上设备才能
  证明的验收项一律记为 `deferred — emulator not used this round (user instruction 2026-09-21)`：
  手机竖屏 / 平板横屏无重叠、字体放大可读、44dp 热区实测、推荐条动效，以及 `AppLaunchInstrumentedTest` 本身。
- **共享树在 T-012 测量时曾短暂不可编译，现已恢复**：当时 `generation/PositionCommandsTest.java`
  （T-010 的在飞 RED）引用尚不存在的 `PositionCommands`，28 个编译错误出自这一个文件
  （`artifacts/evidence/T-012-in-repo-suite.txt`）。T-012 的 RED/GREEN 因此在仓库外的
  `%TEMP%\t012-red` 副本里测量（命令字符串与 repo 内完全一致），证据为
  `artifacts/evidence/T-012-red.txt` 与 `artifacts/evidence/T-012-green.txt`；T-010/T-011 落地后，
  `:app:testDebugUnitTest` 在共享树上已实测为 BUILD SUCCESSFUL
  （`artifacts/evidence/T-013-green-repo.txt`）。
- 规模：本切片 **1428 非空非注释生产行（Java，26 个文件）+ 884 行资源**，超 200 行软上限、也超
  500 行硬上限（`artifacts/evidence/T-012-sizing.txt`）；**没有用户批准的规模例外**，属待裁定偏差，
  不得当成已批准例外引用。
## 应用层入口索引（`:app`）—— 搜索、未读与阅读位置（T-015）

搜索与阅读位置都在 `app/src/main/java/com/treelearn/app/{search,reading}/`。
**「中文默认 FTS 不可靠」是设计前提**：本切片不依赖 FTS，走归一化 + 参数化 `LIKE`（技术方案 §6.3/§11）；
将来换 n-gram 时替换的是 `SearchRepository` 的实现，页面与用例不动。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `SearchRepository` / `SqliteSearchRepository` | 检索端口与 SQLite 实现 | 端口只暴露 `search(SearchQuery)` 与 `isStillReachable(SearchHit)`；实现层**自己不写 SQL**（列名只出现在 `:data` 的 DAO 里）；读不出来的分支行**照实往上抛**，不为了「多返回一条」糊过去 |
| `SearchViewState` | 搜索结果页状态（`IDLE`/`SEARCHING`/`RESULTS`/`ERROR`） | 状态封闭；空白输入是 `IDLE` 而不是「零结果」——对用户这是两件事 |
| `SearchPresenter` | 草稿 → 查询 → 结果 → 跳转 | 空查询**从不触达存储**；用草稿比对丢弃过期响应（旧请求晚到不覆盖新结果）；已经打不开的命中从列表剔除；跳转经 `Sink.openAnchor` 交出**锚点**而不是适配器位置 |
| `ReadingStore` / `SqliteReadingStore` | 阅读位置的读写端口与实现 | `null` 表示「没有位置」，与「位置在第一条」区分开 |
| `ReadingSession` | 打开分支时恢复、随视口推进未读、离开时记住位置 | 恢复优先按 `message/revision + 偏移`，失效则回退最近合法位置；未读只在**目标回答真的进入视口**时按稳定序号推进，**打开分支不清未读**；首帧布局完成也算一次视口事件——一屏装得下且最新回答可见时首帧即清未读，判定仍是同一处 `ViewportSeen`、没有第二个计数器；写库被拒（DAO 的失败关闭）只渲染 `notice_reading_position`，不把异常抛给后台线程 |

**已知缺口与记录的偏差（不要读成已解决）：**

- **搜索页的 UI 已接线（2026-09-22，T-040）**：`SearchPresenter` 不再只被验收套件引用——宿主 `MainActivity`
  实现了 `SearchPresenter.Sink`，`render` 交给新屏幕 `screen.SearchScreen`，`openAnchor` 复用 T-042 已接线的
  `MainActivity.showChat(branchId, explicitAnchor)`（见下条），**没有第二套定位路径**；首页搜索按钮也不再是
  死按钮。见「搜索页与首页搜索入口（T-040）」，原文扫描 `artifacts/evidence/T-040-source-scan.txt`。
- **阅读位置恢复与「看过即清未读」已接线（2026-09-22，T-042）**：`ReadingStore` / `ReadingSession` 不再只被
  测试引用——宿主 `MainActivity.showChat(String, ReadingAnchor)` 在打开分支时经 `AppContainer.readingStore()`
  恢复位置、离开时保存；`screen/ChatScreen` 只上报滚动事实（`onRowsLaidOut` / `onViewportChanged` /
  `onLeaving`），读写库一律走 `AppContainer.background()`、不在 UI 线程做 IO。原文扫描见首轮 `artifacts/evidence/T-042-source-scan.txt` 与修复轮 `artifacts/evidence/T-042-fix1-source-scan.txt`（行号以修复轮扫描当时的实测值为准，此前证据里的行号被并行 T-040 lane 挪走过）。**2026-09-22 修复轮（D1–D5）**：① 保存阅读位置与推进未读两条后台写路径接住 `ReadingPositionDao.write` / `BranchDao.update` 的失败关闭异常，失败只渲染阅读位置自己的 notice `notice_reading_position`（`screen/NoticeText` 是 `Notice` → 文案的唯一映射），不再把异常抛给后台线程、不再可能崩进程；② 首帧布局完成后补一次可见集上报（`ChatActions.onRowsLaidOut(int, Set<String>)`），一屏装得下且最新回答可见时首帧即清未读，判定仍是同一处 `ViewportSeen`、不新增计数器；③ 删掉无生产者的 `ChatData.Snapshot.of` 4 参重载。真机滚动观感与像素定位仍为
  `deferred — 用户自行装机验证（user decision 2026-09-21）`。
- **归档（导航）页已不在此缺口内**：它已由 T-039 接上真实入口，见「导航页与归档 / 恢复 / 永久删除（T-039）」。
- **真机结论全部 deferred**：本轮按用户指令（2026-09-21）不使用模拟器/真机，`:app:assembleDebugAndroidTest`
  只做**编译验证**、不安装不运行，`app/build/outputs/androidTest-results/connected/debug` 保持为空。
  `SearchUnreadReadingInstrumentedTest` 的 10 个用例（中文子串、`%`/`_` 字面量、引号是数据、过期 revision 与
  分叉、归档不可搜、展示顺序、阅读位置四列往返、`ON DELETE SET NULL`、清空）**只编译、未执行**，一律记为
  `deferred — emulator not used this round (user instruction 2026-09-21)`。
- **SQL 行为在 JVM 上只证明了「语句形状」**：`SearchDaoStatementTest` 明确不执行 SQL（真机执行归
  `SearchUnreadReadingInstrumentedTest`），它钉住的是本轮两件不能出错的事——查询文本**从不内联**（六处
  `LIKE ? ESCAPE '\'`、语句里不含查询串）与过期 revision 不可达。**行级搜索结果本身未经真机验证。**
- 规模：本切片 **807 判定行 / 1411 物理行**（15 个新文件，`artifacts/evidence/T-015-sizing.txt`），
  超 200 行软上限、也超 500 行硬上限；**没有用户批准的规模例外**，属待裁定偏差，不得当成已批准例外引用。

## 应用层入口索引（`:app`）—— 搜索页与首页搜索入口（T-040）

搜索页给 T-015 已有的检索端口接上一个真实屏幕与入口（PRD 6.2.8）。屏幕**不做判定**：能不能搜到、命中是
哪几条、按什么顺序，全由仓储与页面对象回答；屏幕只画它拿到的东西。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `screen.SearchScreen` | 搜索页视图（输入、状态窗格、命中行） | 只画 `SearchPage` 给的窗格与行；**不重新过滤、不重新排序**；每次输入经 `afterTextChanged` 转发给 presenter |
| `search.SearchPage` | 搜索页的纯 JVM 页面对象（`Pane` / `Row`） | 五个窗格（引导/搜索中/列表/空/失败）互斥；每个分支标题只向 `BranchTitles` 问一次；标题查不到给空串而不是把行丢掉 |
| `search.BranchTitles` | 「这个分支叫什么」的唯一端口 | 页面不直接依赖对话存储；命中行不因此多一个数据源 |
| `wiring.SqliteSearchStore` | 搜索库的惰性持有者 | 容器构造期**不开库**；第一次真正提问时才开，调用方都是后台线程 |

- **首页搜索入口不再是死按钮（2026-09-22，T-040）**：`screen_home_body.xml` 的 `home_search` 不再带
  `android:enabled="false"`，`HomeScreen` 为它挂了监听器并走 `HomeActions.onSearchPressed()`。原文扫描见
  `artifacts/evidence/T-040-source-scan.txt`，其中带**正控**——同一个提取器仍能看见 `home_send` 上真实存在的
  `android:enabled`，所以「`home_search` 上没有 disabled」不是提取器看不见造成的假通过。
- **跳转复用 T-042 的同一道门**：`MainActivity.openAnchor` 调 `showChat(branchId, anchor)`，不另造第二套定位。
- **宽屏镜像**：`res/layout-sw600dp/screen_search.xml`。
- 真机输入法下的即时搜索体验与像素证据仍为 `deferred — 用户自行装机验证（user decision 2026-09-21）`。

## 应用层入口索引（`:app`）—— 重试、重置与版本切换（T-010）

重试/重置/切版由两个新增类型承担：一个**纯 JVM 判定缝**（不需要模拟器就能测）和一个**事务型用例**。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `PositionCommands` | 重试/重置/切版**能否执行**的判定，加会话语义的 `Baseline` | 判定只有一处定义，且顺序与领域自身守卫一致（先位置、再角色、再在跑尝试）；`codeFor` 是「拒绝 → 错误码」的**唯一**定义点 |
| `AnswerVersions` | `retry` / `reset` / `selectRevision` / `versions` 四个用例 | 拒绝时**一行都不写**（拒绝靠回滚事务抛出，而不是从 work body 返回——runner 会提交 work body 的返回值）；位置以数据库行为准，不采信调用方手里的对象 |

**运行时事实（技术方案 §5.1 / §7）：**

- 重试与重置都是**同一个位置**上的新尝试：复用原 AI message 行，所以既不复制学习者消息，也不增加轮数；
  两者只差一个条件——重试可以替换「什么都没产生的失败尝试」，重置要求该位置当前就有稳定版本。
- `retry` / `reset` 本身**不写 operation 行**：返回的新 `GenerationCommand` 就是这次新尝试，行由协调器提交时写。
  于是「重试只新增一个 operation」由构造保证，而不是靠这里第二次插入。
- 权限判定在**写任何行之前**完成；活动生成、跨消息 revision、重复命令都返回稳定错误码且不动当前版本：
  `MESSAGE_NOT_FOUND` / `REVISION_NOT_OF_THIS_POSITION` → `OPERATION_NOT_COMPLETABLE`；
  `NOT_AN_AI_POSITION` → `MESSAGE_ROLE_MISMATCH`；`ATTEMPT_ALREADY_RUNNING` → `ILLEGAL_STATE_TRANSITION`；
  `NO_STABLE_VERSION` → `OPERATION_NOT_STABLE`；`REVISION_ALREADY_ACTIVE` → `REVISION_IMMUTABLE`。
- 「是否有尝试在跑」问的是**存储**：`generation_operations` 的部分唯一索引保证每个分支最多一条活动行，
  所以不看位置上的指针，指针在今天的代码里根本没有生产者。
- 切版只改 `messages.active_revision_id`；子分支的 `branches.source_revision_id` 是另一列，没有任何级联会碰它，
  因此父分支切版（含手动切回旧版）之后，旧子分支仍指向它被分叉时的 revision 与正文。
- `versions(messageId)` 按 `revision_no` 升序返回历史，只读、不开事务。
- 从不记录：API key、提示词正文、问题、回答、推理正文。

**已知缺口与记录的偏差（不要读成已解决）：**

- **重试的「原尝试基线」目前由调用方给出**：要求沿用原 attempt 固定的 model / thinking，
  但 `android/data/**` 的 `GenerationOperationDao` 只暴露 `findById` / `findActiveByBranch` / `findRecoverable`，
  没有「某个 message 最近一次 operation」的访问器，而该包属于另一条 lane，本任务不改。
  于是 `retry` / `reset` 的 model 与 thinking 由调用方传入并原样写进新命令；补一个按 message 取最近 operation 的读取，
  是数据层 lane 的后续工作。
- **本轮不使用模拟器**（用户指令 2026-09-21）：`AnswerVersionsInstrumentedTest` 只做**编译验证**
  （`:app:assembleDebugAndroidTest`），源码保留、**不执行**。「拒绝不写任何行」「重试不增加消息与轮数」
  「子分支保持原 `sourceRevisionId`」三条只能在真机 SQLite 上证明，因此一律记为
  `deferred — emulator not used this round (user instruction 2026-09-21)`，既不算 PASS 也不算 FAIL。
- `REVISION_NOT_OF_THIS_POSITION` 与 `MESSAGE_NOT_FOUND` 共用 `OPERATION_NOT_COMPLETABLE`：
  `:core` 现有错误码区分不了「这个位置没了」与「这个位置没有这个版本」，新增错误码要改 `:core`，属另一条 lane。

## 应用层入口索引（`:app`）—— 对话、思考披露与回答操作（T-013）、长按划词与子分支列表（T-041）

对话页沿用 T-012 的外壳：同一个 `MainActivity`、同一个 `R.id.shell_host`，`ChatScreen` 是换进宿主的
回答与 reasoning 使用 `screen.MarkdownRenderer` 将 CommonMark 转成原生 `Spannable`：标题、强调、列表、
引用、代码块和链接均不把 Markdown 标记原样显示；回答仍保持可选择复制，链接使用 Android 原生
`URLSpan`。解析依赖只在 `:app`，不进入领域层或数据层。
第三个子视图，因此没有第二个 Activity、没有导航库。消息、思考、版本与推荐全部来自 `ChatViewState`，
View 自己不持内容。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `ui.ChatMessage` | 一行对话、它的阶段与它允许的操作 | 操作谓词**就是存储的判定**：`canRetry`/`canReset`/`canSelectRevision` 与 T-010 的 `PositionCommands` 同条件，`ChatPositionAgreementTest` 用同一组输入驱动两边逐一比对，因此「按钮可点」等于「用例接受」 |
| `ui.ChatViewState` | 对话页可绘制的全部状态（相位/分支/消息/草稿/引用/Key/模型/思考/**展开集合**/推荐/提示/发送中） | 展开集合按 **messageId** 键，不是列表下标；`withMessages` 只换列表，草稿、引用与展开态原样带走；失败分类**复用** `HomeViewState.Notice` |
| `ui.ChatData` / `ui.ChatCommands` / `ui.ChatActions` | 读、命令、用户操作三个端口 | 页面不直接访问 DAO、网关或协调器 |
| `ui.ChatSink` | 导航、剪贴板、子分支列表与版本菜单的出口 | 复制文本交出去即忘：实现只写剪贴板，不落盘、不打印 |
| `ui.CopyLabels` | 复制文本的两个小标题 | 由 `AppContainer` 从 `strings.xml` 取值注入；presenter 没有 Context，也不内联文案 |
| `ui.ChatPresenter` | 对话页的全部决策 | 重试/重置/切版只**转发**给 T-010 的用例，UI 层没有任何路径能移动 revision 指针；一次 send 不在这里追行，而是清草稿后重新读 |
| `screen.ChatScreen` | 列表 + 引用条 + 输入区（含模型/思考入口） | 只转发手势、按 ViewState 绘制 |
| `screen.ReasoningDisclosure` | 思考披露：44dp 折叠头 + 正文 | 默认折叠；无 reasoning 且无在跑尝试时整块隐藏；展开态不在这里存 |
| `screen.AnswerActionBar` | 引用/复制/重置/新建分支/子分支/版本 chip | 按 `ChatMessage` 谓词启用；不可用是**禁用**而不是隐藏，流式过程中行不重排 |
| `ui.TextSelection` | 一段文字的选区，与它的两个取法（整条回答 / 长按所在的那一句） | 纯 JVM 值类型，不含任何 Android 类型；文本视图未被动过时报的 `(-1,-1)` 与「空选区」是同一个答案 |
| `ui.WordMenu` | 长按菜单的**唯一**判定点：四项里哪些出现、哪些可按，以及菜单落在哪里 | 每一项的 flag 都读 `ChatMessage` 自己的谓词；未完成或失败的回答**不出现**「新建分支」，而不是画出来再禁用（PRD 6.2.19 异常 1） |
| `ui.ChildBranch` | 子分支弹层的一行：去哪、叫什么、还欠多少 | 标题由装配层解析后传入，弹层不自己从可能为空的存储标题推第二个名字 |
| `screen.WordMenuPopup` | 四项菜单的承载与落点测量（`PopupWindow` + 一份布局） | 只画、只转发，不判定；「全选」就地改的**就是**回答自己的选区，因此高亮、`getSelectionStart()` 与交出去的那段文字恒为同一个值 |
| `screen.ChildBranchPopup` | 子分支列表弹层（`PopupWindow` + `showAsDropDown`） | 行是**传进来**的，弹层不读库；只有点行才导航，点外面或按返回只关列表 |
| `wiring.BranchChildren` | 「这条回答的直接子分支」与「每个位置各有几个子分支」的唯一判定点 | 子 = 非根、带 source message、未归档；chips 与弹层两处读同一份，因此不会出现「数量说有、列表却拿不出」 |
| `wiring.ChatWiring` | 对话的**真实**数据路径（T-032）：读回 + 五条命令，实现 `ChatData` 与 `ChatCommands` | 读回走 `MessageDao.findByBranch` / `AnswerRevisionDao` / `GenerationOperationDao`；`send`/`retry`/`reset` 装配请求后交协调器、`selectRevision` 直接落库、`fork` 走 `BranchCreation`；每条拒绝都以可见 `Notice` 返回 |
| `wiring.ChatStore` / `wiring.SqliteChatStore` | 对话的端口与它的 SQLite 实现 | 端口**委托** T-010 的 `AnswerVersions`/`PositionCommands`，不复制它们的判定；`history()` 用编码器自己的 `ContextPlan.Entry` 词汇表达「问题之前的行」，历史因此就是存储的行 |
| `wiring.PromptLimits` | 请求预算的两个数字，全应用唯一取值点 | `AppContainer` 用 `PromptLimits.budget()` 构造那一个 `PromptBudget`；数字与理由见 `:app` 首页一节的「请求预算（T-032 的一次决定）」 |
| `wiring.LogcatTelemetry` | 协调器的遥测出口 | 只记状态与计数，**不记** Key、system prompt、问题、回答或 reasoning 正文 |

**这一层的交互事实：**

- **思考与回答分开呈现**：`layout/view_reasoning.xml` 的披露头与正文是两块，正文默认 `visibility="gone"`，
  由 `ChatViewState.expandedReasoning`（messageId 集合）驱动。展开态只活在页面会话里，不落库、不跨重启，
  也**不按 adapter position 记忆**——列表每次读都会重建，按 id 键才不会把 A 的展开态串到 B。
- **复制全文按「思考过程 + 最终回答」组合**：格式只定义在一处（`ChatMessage.copyText`），两个小标题来自
  `strings.xml` 的 `chat_copy_reasoning` / `chat_copy_answer`；没有 reasoning 时复制内容就是回答本身。
- **推荐条只挂「最新且稳定」的那条回答**：读到的 `recommendationsRevisionId` 必须等于最新一行的
  `activeRevisionId` 且该行 stable。于是追问之后、生成中、切版之后三种情况都由同一条规则自动隐藏（BASE-16），
  没有第二处判断。
- **失败保留草稿与引用**：只有 accepted 才清，和首页输入框同一条规则。
- **引用真正进请求（T-044，2026-09-22）**：`messages.quoted_text` 里的那句话会随请求发给模型——它编进
  **当前问题**的正文（`<treelearn_quote>` 信封、换行、转义后的引文、换行、`</treelearn_quote>`、换行、问题原文），
  而不是作为第二条 user 消息进历史：提示词设计 §5 把「当前问题」定为恰好一条且最后一条，引用是对这句话的限定，
  放进历史既破坏该不变式、又要求 `:core` 的 `ContextPlan.Entry` 认识模型协议。判定「有没有引用」（null/空白归一化）
  **只有一处**——`DeepSeekContextEncoder.currentInput`；发送与重试/重置两条入口都只转发存行里的原文，
  所以「重新生成」不会把引用丢掉（两个入口逐字符一致的断言见 `ChatWiringTest`）。引用随 `currentInput` 一起
  计入既有字符预算（`PromptLimits.CAPACITY_CHARS`），过长时按 `RESERVE_EXCEEDS_BUDGET` 失败关闭，不截断、
  不静默丢弃。**只转义引用、不转义问题**：问题要同时落库与界面原样展示；代价是问题自身若含信封字面量会原样
  发给 provider——该残留风险与理由写在提示词设计 V1.0 §5.1（引文的线格式）。
- **长按划词弹的是应用自己的菜单（T-041）**：`item_chat_message.xml` 的 `textIsSelectable` 仍在，但长按由
  `ChatScreen.Row` 的 `OnLongClickListener` 接管——回调返回 `true`，平台因此不再启动它自己的文本工具条；
  它把按下坐标换算成字符偏移、取该字符所在的**整句**，交给 `WordMenuPopup`。菜单分「AI 操作」（追问 / 新建分支）
  与「系统操作」（复制 / 全选）两组，每行都是 44dp 且整宽，四个热区因此不可能重叠；落点由 `WordMenu` 计算，
  优先落在选中句**上方**（上方放不下才翻到下方）并夹在窗口内，故不会遮住它要操作的那句话。
  「复制」在这里复制的是**选中的那一段**（PRD 6.2.19 第 5 条），与回答行上复制整条回答的按钮不是同一个动作。
- **「子分支」弹层列出的是这条回答直接分出、且未归档的子分支（T-041）**：分支在点开之前就读好了，
  `ChatSink.openChildren` 把列表一起交出来，所以弹层在 UI 线程上不读库；点一行经 `ChatSink.openBranch` 进入该分支，
  这条路径与画布/首页进入分支是同一条。本轮把原先那句「子分支列表还没接」的占位文案从 `strings.xml` 整体删掉了，
  `android/` 下不再有任何形式的引用（本句刻意不写出它的字符串名与原文，否则等于又把它引回来一次；
  那条断言与扫描输出见 `artifacts/evidence/T-041-scans.txt`）。
- **按钮叫「新建分支」，数据仍叫「分叉」**：`chat_act_fork` 与相关 `contentDescription` 统一为「新建分支」
  （PRD 6.2.19 第 4 条），`app/src/main` 下已无「分叉」二字；本节在描述 `anchor_quote` / `source_revision_id` /
  级联这类**数据概念**时仍写「分叉」，因为那是 schema 与既有文档里的名词，不是按钮文案。

**已知缺口与记录的偏差（不要读成已解决）：**

- **对话数据路径已接线（T-032，2026-09-22）**：读回走 `MessageDao.findByBranch` +
  `AnswerRevisionDao.findById` + `GenerationOperationDao.findActiveByBranch`；五条命令分别委托给已落地的
  用例（`AnswerVersions` 的 retry/reset/selectRevision、`BranchCreation`、`GenerationCoordinator.submit`），
  装配层本身不重写任何判定。读者要注意的**残留缺口**（都不是「以后再说」，而是本 build 里确实没有生产者的部分）：
  1. **追问推荐条的生命周期已接入，但仍依赖真实 provider 返回**：`ChatWiring.read` 在有 Key 且
     当前 revision 没有匹配 artifact 时通过 `DerivedService` 请求 `ANSWER_RECOMMENDATIONS`；失败仍保留
     旧的空集合，因此无 Key/网络失败时推荐条继续隐藏，而不是显示伪数据。
  2. **已结束但没有稳定版本的尝试读不到半截正文**：这种位置读回为 `FAILED`、正文为空。终态尝试的 partial
     文本在本 build 没有带调用方的查询能读到，而现造一条就是给同一批行开第二条读路径；`FAILED` 的整个
     作用正是「不让半截回答被当成回答」。
  3. **`fork` 不发起生成**：新建分支只建子分支与它的出生快照，子分支的第一问由学习者自己提（父分支的自动生成
     属 T-039 范围）；并且只允许从「显示着稳定版本」的 AI 位置新建分支，与 `ChatMessage.canFork()` 同条件。
- **平台自己的文本工具条可能与应用菜单并存（T-041，无设备，未验证）**：本切片只保证「长按」这一条手势被应用接管
  （`OnLongClickListener` 返回 `true`）。**拖动选择手柄**或**双击选词**仍可能唤起平台自己的悬浮工具条，从而与
  `WordMenuPopup` 同屏出现两套操作。零 AndroidX 约束下没有可用的 `setCustomSelectionActionModeCallback`
  等价物，也没有主题级的 `windowActionModeOverlay` 开关，所以这一条**只能上设备才能判定**，本轮记为
  `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得读成已解决。
- **T-041 的规模超了 500 行硬上限**：本切片 **544 非空非注释生产行**（新增 9 个文件 502 行 + 既有文件改动 42 行，
  判定值以外的物理行为 727 行；命令与逐文件明细见 `artifacts/evidence/T-041-sizing.txt`）。口径按
  `.trae/rules/harness-tasks.md` 第 45 行「新增文件按全部非空非注释行，既有文件只按本次改动行」。
  **没有用户批准的规模例外**，属待裁定偏差，不得当成已批准例外引用。
- **设备侧观感与像素证据未取得（T-041）**：菜单落点、弹层在窄屏是否被裁切、与平台工具条并存的观感，
  本轮一律记为 `deferred — 用户自行装机验证（user decision 2026-09-21）`。
- **动作标签是文字，不是图标资产**：`chat_act_copy` 等五个操作按钮用文字标签；本轮没有设备确认矢量图标渲染，
  选型图标留给后续切片。
- **失败分类复用了 T-012 的枚举名**：`ChatViewState.notice` 的类型是 `HomeViewState.Notice`。同一个 401
  不该有两种说法，所以没有第二份映射；但这个名字已经比它的第一个用户大了，行为无关的重命名
  （`HomeViewState.Notice` → `FailureNotice`）记在这里作为后续工作。**`Notice.NOT_WIRED` 已随 T-032 一并
  删除**：生产装配里不再有任何一条路径会返回它，枚举里留着它就等于留一条永远不可达的分支。
- **对比度已按 token 修好（2026-09-21）**：展开正文用的次级灰 `tl_ink2` 在 `tl_surface` 上是 **5.67:1**、
  在 `tl_bg` 上是 **5.23:1**；最浅一档 `tl_ink3` 原本只有 **3.16:1 / 2.92:1**，低于 4.5:1 的可读底线，
  已改**调色板 token**（`#8A938E` → `#636F66`，现为 **5.26:1 / 4.85:1**），不是它的 27 个调用点中的任何一个；
  三档灰（`tl_ink` > `tl_ink2` > `tl_ink3`）的层次不变。四个数字与测量命令见
  `artifacts/evidence/T-013-static-ui-checks.txt` 的 CHECK 3 及其复跑块。
- **本轮不使用模拟器**（用户指令 2026-09-21）：`:app:assembleDebugAndroidTest` 只做**编译验证**，
  `app/build/outputs/androidTest-results/connected/debug` 保持为空。必须上设备才能证明的验收项一律记为
  `deferred — emulator not used this round (user instruction 2026-09-21)`：真机上的流式刷新节奏、展开态在
  列表复用下不串行、旋转/切分支后生成继续且无重复消息、手机竖屏与平板横屏无重叠、字体放大可读、44dp 热区
  实测（本轮只有静态计算：16 个可交互元素全部 ≥44dp）。
- 规模：本切片新增 **1584 非空非注释生产行**（Java 1105 / 13 个文件 + 资源 479 / 7 个布局），另有测试
  911 行 / 5 个文件与对 T-012 既有文件的 +131 行扩展，超 200 行软上限与 500 行硬上限
  （`artifacts/evidence/T-013-sizing.txt`）；**没有用户批准的规模例外**，属待裁定偏差，不得当成已批准例外引用。
## 应用层入口索引（`:app`）—— 树画布的生产装配与入口（T-038）

画布本体（T-017 的 `screen.TreeCanvasScreen` / `screen.TreeCanvasView` / `ui.TreeCanvasPresenter` /
`ui.TreeCanvasViewState` 与 `layout/screen_tree*.xml`、`layout-sw600dp/screen_tree.xml`）早已交付并被单测覆盖，
但在 T-038 之前**没有任何生产构造点**：`new TreeCanvasScreen(` 全仓只出现在它自己的文件里，
`ui.TreeCanvasData` 与 `ui.TreeCanvasCommands` 只有测试替身——一段被完整测试却永远不会被执行的代码。
本节记录它现在由谁驱动、从哪里进入。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `ui.TreeCanvasData` / `ui.TreeCanvasCommands` / `ui.TreeCanvasActions` / `ui.TreeCanvasSink` | 读、写、手势、出口四个端口 | 画布屏幕**不读库**：全量行由 `Data.load(topicId)` 一次取回，几何需要全量才能算布局 |
| `wiring.TreeCanvasStore` / `wiring.SqliteTreeCanvasStore` | 画布端口与它的 SQLite 实现 | 端口**不重写判定**；`branchesOf` 连归档行一起读回，过滤是画布的规则；折叠经领域自己的 `Branch.setCollapsed`，0/1 列的含义只有一处；写失败以 `OPERATION_NOT_COMPLETABLE` **返回**，不带驱动原文 |
| `wiring.TreeCanvasWiring` | 画布的**真实**数据路径：一次读该主题全部非归档枝干 + 每个枝干的摘要首行与已提交轮次，实现 `TreeCanvasData` 与 `TreeCanvasCommands` | 「归档枝干不进画布」与「摘要取首行」是本层**仅有**的两个判定点；摘要解码复用 `ArtifactPayloadCodec`，被拒的信封读作空（与首页同一答案）；屏幕拿到的只有几何要用的 `Row` |
| `AppContainer.treeCanvasData()` / `treeCanvasCommands()` | 组合根暴露的两个访问器 | 两个访问器返回**同一个** `TreeCanvasWiring` 实例，装配只发生一次 |
| `MainActivity.showTree(branchId)` / `leaveTree()` / `openOverview(branchId)` | 画布宿主：进入、离开与宿主渲染 | 画布按分支构造、离开即丢（与对话页同一条规则）；`openBranch` 同时满足 `ChatSink` 与 `TreeCanvasSink` |
| `screen.ChatScreen` 的 `chat_overview` | 对话页的「总览」入口 | 只改「哪个屏幕在上面」，不换对话的当前分支 |

**这一层的交互事实：**

- **入口在对话页，返回回到那段对话**：对话页顶栏的 `chat_overview`（文案 `strings.xml` 的「总览」）进入画布，
  画布把对话所处的那条枝设为当前分支（因此高亮这条路径），首帧把整棵树拟合进视野；画布自身的返回按钮与系统返回
  都回到**进来时那段对话**，与原型 v2.5 画布顶栏的返回（`setScreen('chat')`）一致。首页**不加**画布入口。
- **折叠是画布唯一的写操作，且只有一个判定点**：`TreeCanvasScreen` 只把 `onCollapseToggled(branchId)` 转发给
  presenter，presenter 只决定「这一次是折叠还是展开」，落库与 0/1 语义在 `SqliteTreeCanvasStore.setCollapsed` 一处。
  屏幕里没有 SQL、没有 DAO、没有 `DatabaseTransactionRunner`。
- **画布与导航页对「哪些枝干可见」同口径**：两边都是「该主题的非归档枝干」，归档行在画布上不出现。
- **首帧视野用宿主真实尺寸做种子**：`showTree` 用 `host.getWidth()/getHeight()` 构造首帧 viewport，
  画布视图随后用 `onViewportChanged` 报告自己的真实盒子。**刻意不用 0×0 种子**——先返回的读取会把树拟合进空盒子，
  而后来那一次 resize 按设计只记录新尺寸、不重新拟合，树会被画在屏幕外。

**已知缺口与记录的偏差（不要读成已解决）：**

- **真机触控、像素观感与平板横屏仍是 deferred**：按用户 2026-09-21 的决定，本轮不跑 emulator/真机，
  画布的真实手势节奏、未读芽的观感、`layout-sw600dp/screen_tree.xml` 的横屏排布一律记为
  `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得读成 PASS。
- **叶子上的摘要依赖派生产物，而派生在本 build 没有生产调用方**：读取侧（`ArtifactPayloadCodec` 的
  `BRANCH_SUMMARY`）已接上，但写入侧 `DerivedOperation` 仍无调用者，所以画布叶子在当前 build 实际读作空——
  这是**声明的缺口**，不是「已实现但没生效」。
- **大主题的一次性布局耗时未在真机测过**：`load(topicId)` 一次读全部枝干、一次算完整几何，这是有意的取舍
  （分批读会让画面在补数据时跳动），耗时风险记为未验证。
- **规模与验证证据**：见 `.trae/tasks/T-2026-09-21-tree-canvas-production-wiring-038.md` 的 TDD 执行记录与
  验收报告（`artifacts/evidence/T-038-*.txt`）。

## 应用层入口索引（`:app`）—— 导航页与归档 / 恢复 / 永久删除（T-039）

导航页（PRD 6.2.6 逐层导航与 6.2.10 非根子树归档 / 永久删除）此前**只有 presenter 与 SQLite 存储，没有屏幕、
没有布局、也没有任何入口**：`navigation/NavigationPresenter` 在 `src/main` 内只被它自己和 `NavigationActions`
引用，构造点只在测试里；首页的「归档」按钮在 `res/layout/screen_home_body.xml` 里写死 `android:enabled="false"`。
本节记录它现在由谁驱动、从哪里进入。

| 类型 | 职责 | 关键不变量 |
| :--- | :--- | :--- |
| `navigation.NavigationData` / `NavigationActions` / `NavigationSink` | 读、手势、出口三个端口 | 屏幕**不读库也不判定**：哪些分支可见、能否归档、删除覆盖哪棵子树一律读 `NavigationViewState`，页面只转发动作 |
| `wiring.NavigationWiring` | 导航页的**真实**数据路径：持有单例 `SqliteNavigationStore`，把 `NavigationData` 的方法原样委托 | 本层**不重写判定**，也**不实现** `NavigationActions`（那是 presenter 的手势契约，宿主每次进入时构造）——第二份「什么可见」正是列表与画布漂移的来源 |
| `AppContainer.navigationData()` | 组合根暴露的端口 | 装配只发生一次，页面拿到的是全应用同一条连接 |
| `screen.NavigationScreen` + `res/layout/screen_navigation*.xml`（含 `layout-sw600dp/` 镜像） | 导航页视图：非归档 / 已归档两个分组、当前选中、分支级 AI 总结（可折叠）、行内菜单（折叠 / 归档 / 永久删除） | 归档与删除两个菜单项**只在 `row.archivable` 为真时出现**，因此根分支没有归档；屏幕里没有 SQL、没有 DAO |
| `MainActivity.showNavigation` / `enterNavigation` / `render` / `leaveNavigation` / `openNavigation` / `openArchived` | 导航页宿主：进入、宿主渲染、离开与两条出口 | 页面按分支构造、离开即丢（与对话页、画布同一条规则）；`openNavigation` 与 `openArchived` 是同一条 `showNavigation` 的两个出口 |

**这一层的交互事实：**

- **三条入口**：对话页顶栏的 `chat_nav`、画布顶栏的 `tree_nav`、首页的 `home_archived` 都进入导航页。
  前两条回**进来时那段对话**；首页那条把列表首位的主题按其 `resumeBranchId` 打开。首页按钮的
  `android:enabled="false"` 已去掉，改由 `HomePresenter` 在**还没有主题**时禁用（有主题才可点）。
- **永久删除必须先经确认，且确认前不写库**：行的「永久删除」只发 `onDeleteRequested(branchId, SUBTREE)`，
  由 presenter 数出这次删除覆盖的分支数并给出 `pendingDelete` 计划；屏幕据此弹**一次**系统对话框，
  文案用 `navigation_delete_body` 的 `%2$d` 写出受影响子树的分支数量（含目标自身）；
  只有「永久删除」被按下才发 `onDeleteConfirmed`，取消、点外面与系统返回都只发 `onDeleteCancelled`，
  **一条行都不会动**。
- **系统返回与恢复的优先序**：设置页 → 导航页 → 画布 → 对话页 → 退出应用；导航页返回回到进来时的那段对话
  （`showChat(navigationBranchId)`）；`onResume` 里导航页优先于画布与对话页恢复数据。

**已知缺口与记录的偏差（不要读成已解决）：**

- **真机观感与像素证据仍是 deferred**：按用户 2026-09-21 的决定，本轮不跑 emulator/真机，列表滚动、
  对话框文案换行与 `layout-sw600dp/screen_navigation.xml` 的横屏排布一律记为
  `deferred — 用户自行装机验证（user decision 2026-09-21）`，不得读成 PASS。
- **接线本身没有 JVM 接缝**：屏幕构造与入口监听是 Android View 代码，`NavigationRootGateTest` 覆盖的是
  状态口径（根不可归档、`childCount` 计数），不是接线；接线的证据是「编译通过 + 生产源码里存在构造点与
  三条入口」的原文扫描（`artifacts/evidence/T-039-wiring.txt`）——这是**替代证据**，不是端到端执行。
- **规模超硬上限**：本切片 **646 非空非注释生产行**（新增 531 + 改动 115，`artifacts/evidence/T-039-sizing-final.txt`），
  超 200 行软上限、也超 500 行硬上限；**没有用户批准的规模例外**，属待裁定偏差，不得当成已批准例外引用。

## 常用命令

```bash
# Gradle 启动 JVM 可用 JDK 17 或 21；编译固定使用 Java 17 toolchain。
export JAVA_HOME=/d/Environment/Jdk/jdk21

cd android

# 构建
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease

# lint
./gradlew :app:lintDebug

# Java-only 守门（静态扫描 + 依赖报告 + APK dex 字符串表）
powershell -NoProfile -File scripts/no-kotlin-check.ps1

# 守门双向自检：-Fast 模式不需要真实 Gradle，完整模式额外跑 2 个 Gradle 用例
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev/guard-selftest.ps1 -Fast
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/dev/guard-selftest.ps1

# lint 报告摘要（默认读 :app 的报告；:core 是纯 java-library，没有 lint 任务）
python scripts/dev/lint_summary.py

# 模块依赖方向 + 纯 JVM 模块纯净性
./gradlew verifyDependencyDirection

# APK dex 级 Java-only 校验（单独调用时）
./gradlew verifyNoKotlinDex

# 纯 JVM 测试
./gradlew :core:test :tree-layout:test :ai:test :app:testDebugUnitTest

# 设备测试（需已连接设备/模拟器）
# 约定：所有 instrumentation 测试都放在 :app 的 androidTest 源集，
# 这样只需一份 test APK 与一个测试栈，避免多模块重复引入测试依赖。
./gradlew :app:connectedDebugAndroidTest

# 全部检查（含依赖方向与 dex 级 Java-only 守门）
./gradlew check
```

## 设备环境

- 本机 Android SDK：`D:\Environment\Android\SDK`，通过 gitignored 的 `local.properties`
  的 `sdk.dir` 指定。
- 模拟器 AVD：`Medium_Phone_API_36.1`（API 36，x86_64）。
- **SDK 决策（用户 2026-09-21 确认）**：统一以 API 36 / 36.1 为开发与验收目标，
  不再建设 API 28 环境。`minSdk 28` 仍作为声明的最低兼容版本，
  由 `compileSdk 36 + minSdk 28` 的 lint `NewApi` 检查覆盖。

## Release 签名

- keystore 存放在**仓库之外**，凭据只从进程环境变量读取，其次读取 gitignored 的
  `android/keystore.properties`；两者都缺失时 `assembleRelease` 产出未签名 APK。
- 相关环境变量：`TREELEARN_KEYSTORE_PATH`、`TREELEARN_KEYSTORE_PASSWORD`、
  `TREELEARN_KEY_ALIAS`、`TREELEARN_KEY_PASSWORD`。
- 密钥与口令不得进入仓库、日志、APK 或交付报告。

## DeepSeek

- Base URL：`https://api.deepseek.com`；仅 HTTPS。
- 真实集成测试从进程环境变量 `TREELEARN_DEEPSEEK_API_KEY` 读取临时 Key，绝不打印其值。

## API Key 存储与本地设置

T-005 已落地。下表是**当前运行事实**，权威来源是 `android/data` 的
`com.treelearn.data.secret` / `com.treelearn.data.settings` 与 `android/app/src/main` 的配置；
文档与代码不一致时以代码为准。

| 项 | 值 |
| :--- | :--- |
| 密钥算法 | Android Keystore 生成 AES-256-GCM（`AES/GCM/NoPadding`，128 位 tag）|
| Keystore alias | `com.treelearn.secret.deepseek-api-key`（只有 `KeystoreSecretStore` 使用）|
| 落盘内容 | 仅 envelope：`<schemaVersion>:<base64 IV>:<base64 密文>`，位于 `treelearn_secret.xml` |
| 每次保存 | 重新生成 IV（`setRandomizedEncryptionRequired(true)`），旧密文被覆盖 |
| 删除 | 同时删 Keystore 条目与密文 |
| 失败语义 | 信封损坏 → `CORRUPTED_ENVELOPE`；alias 丢失 → `KEY_ENTRY_MISSING`；密文被篡改 → `DECRYPTION_FAILED`；三种都 fail closed 且要求重录，错误文案只描述失败类别 |
| 存在性 ≠ 可读性 | `hasSecret()` 只回答「有没有信封」；信封损坏或 alias 丢失时它仍为 true，由 `read()` 报告失败 |
| 非敏感偏好 | `treelearn_settings.xml`：模型档位 + 思考开关。默认 `deepseek-flash` + 思考**关闭**（PRD/技术方案未固定默认值，属实现决定；T-012 未改动默认值）；思考过程展开状态**不持久化** |
| 明文边界 | Key 不进入 SQLite、日志、报告、Bundle extras、剪贴板，也不进入任何 `toString()`；测试 fixture 只用明显合成的占位值 |

**Manifest 与备份（T-005 已落地）：**

- `android:allowBackup="false"`：Auto Backup 整体关闭。
- `android:usesCleartextTraffic="false"`：禁止明文流量。
- `res/xml/backup_rules.xml`（API ≤ 30）与 `res/xml/data_extraction_rules.xml`（API 31+，
  `cloud-backup` 与 `device-transfer` 两段）都显式排除 `treelearn_secret.xml`，作为
  「将来有人把 allowBackup 改回 true」时的第二道防线。
- **`android:networkSecurityConfig` 尚未声明**：该文件属于 T-018。在它真正写进 Manifest 之前，
  本文档不得声称存在（`SecretBackupConfigInstrumentedTest` 只断言上面四条属性）。

**自动化覆盖**：

- `android/app/src/androidTest/java/com/treelearn/app/SecretStoreInstrumentedTest.java`：保存 / 新实例
  读取 / 替换 / 删除 / 损坏信封 / 篡改密文 / alias 丢失，并扫描整个应用 data 目录确认没有明文，
  且该扫描必须**先找到密文信封**才算通过（否则「没找到明文」可能只是扫描失效）。
- `android/app/src/androidTest/java/com/treelearn/app/SecretBackupConfigInstrumentedTest.java`：从
  **已安装 APK** 的二进制 Manifest 字符串表读回属性，并解析打包后的两份备份规则。
- `android/data/src/test/java/com/treelearn/data/secret/SecretEnvelopeTest.java`（纯 JVM）：envelope
  往返、损坏输入分类、未知 schemaVersion、`toString()` 不泄露。

**本轮验证状态（用户 2026-09-21 指令：本轮不使用模拟器）**：T-005 以 JVM 套件 +
instrumentation 源码的**仅编译**（`:app:assembleDebugAndroidTest`）作为 RED/GREEN；
上面两个设备套件保留在源码里但**本轮未在设备上执行**，其结论记为
`deferred — emulator not used this round (user instruction 2026-09-21)`，不得当作通过。
安全配置的当前态事实（Manifest 四条属性、两份备份规则排除密钥 Preferences）本轮由源码与
打包产物检查支撑，**已安装 APK 上的回读断言**同样属于该 deferred 项。

**已知限制**：Keystore 条目在系统凭据变更等情况下可能被平台作废，此时只能重新录入 Key；
`SettingsRepository` 的运行时消费者是 `:app` 的 `wiring.AndroidSettingsStore`（T-012 落地）：模型档位与思考开关经它读写，值域由 `GenerationModel` 约束。
- `save()` 用 `commit()` 而不是 `apply()`：密钥写入必须落盘后才算保存成功，因此 lint 的
  `ApplySharedPref` 警告（Warning 级）是本文件的刻意取舍，已在代码注释里写明。
