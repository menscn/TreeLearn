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

> **当前覆盖缺口**：`:core` 已由 T-003（领域身份与不可变版本）与 T-021（生成操作与提示词锚定）
> 填入 17 个领域类型，纯净性扫描对它已是真实强制；`:tree-layout` 仍**没有** `src/main/java`
> 源码，扫描在该模块上仍是空跑。该任务会打印
> `WARNING … purity contract is vacuous until T-003 / T-016 land code`，
> 由 T-016 填入代码后自动转为真实强制。

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
  实测 `app-debug.apk` 约 70 KB、3 个 dex、**零第三方依赖、零 Kotlin 字节码**
  （字节数每次构建会变，精确值以 `artifacts/evidence/` 中对应任务的守门输出为准）。
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
| `GenerationState` | 生成尝试的生命周期 | 终态封闭；只有 `STABLE` 可作分支来源；只有 `STREAMING_ANSWER` 能稳定化 |
| `GenerationCommand` | 一次生成请求，**提交前固定策略版本** | 版本必须非空且由声明用途拥有；否则返回领域错误 |
| `GenerationOperation` | 一次生成尝试（本包唯一可变类型） | 版本字段 `final`；缓冲区只在流式态可写；`STABLE` 只能经 `stabilize` 到达 |
| `PromptSpec` | 版本化系统提示词的供应商无关身份 | 只含 `purpose`/`policyVersion`/`systemContent`；`of` 返回 `DomainResult`；空正文被拒；提示词正文不进数据库 |
| `PromptPurpose` | 提示词用途枚举与版本命名空间 | 版本形如 `<prefix>/MAJOR.MINOR.PATCH`；禁止应用版本号或时间戳 |
| `GenerationModel` | 模型档位 | 供应商 model 名只出现在 `wireId` |
| `DomainResult` / `DomainError` / `DomainErrorCode` | 业务失败的返回通道与稳定错误码 | 每个错误码都必须有生产者 |
| `Guard` | 构造期不变量检查与三条错误通道规则 | 见下 |

**错误通道规则（改 `:core` 前必读）**：`Guard` 的类注释是唯一权威正文。三条通道按
「调用方做错了什么」划分——缺参数/格式非法抛 `IllegalArgumentException`；参数合法但接收者
状态不对抛 `IllegalStateException`；参数合法、状态有效而业务规则拒绝才返回 `DomainResult` 失败。
`DomainResult` 是**第三通道**，返回它的方法在前两种情况下仍须抛异常。

**已声明的例外清单也只在 `Guard` 类注释里维护**（不要在这里复制一份）：三条通道的**第一条**有
**三处**例外——`AnswerRevision.create`、`GenerationCommand.create`、`PromptSpec.of`，它们在**策略版本
缺失时**返回 `PROMPT_POLICY_VERSION_REQUIRED` 而非抛异常，因为提示词设计 §3.2 让版本成为请求契约的
必需项，「这次请求没有固定版本」是可上报的业务条件。新增同类入口必须登记到 `Guard` 的清单里。

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
| `DatabaseTransactionRunner` | 唯一的显式事务边界 | 嵌套开启被拒绝（`IllegalStateException`），不静默并入外层事务；失败整体回滚并原样向上抛 |

**在 schema 层被硬化的不变量**（不是「靠 DAO 自觉」）：`answer_revisions`、`context_snapshots`
整行不可 `UPDATE`；`generation_operations` 一旦进入终态不可再改；`messages` 中 `USER` 行的正文
不可改。因此一次误写会**当场失败**，而不是悄悄改写历史。四条都由触发器实现，见
`TreeLearnSchema.CREATE_TRIGGER_STATEMENTS`。

**两条偏索引（partial unique index）**必须保持「部分」语义，否则产品功能会直接坏掉：
`uq_branches_root`（每主题仅一个根，但同主题可有多棵子树）与 `uq_generation_operations_active`
（每分支最多一个进行中的生成，终态操作可与活动操作共存）。两者都由
`:app` 的 `SqliteSchemaInstrumentedTest` 用「允许的那一半必须成功」反向证明其部分性。

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

- Base URL：`https://api.deepseek.com`；仅 HTTPS。Manifest 的 `usesCleartextTraffic="false"`、
  network security config 与备份禁用由 **T-005 / T-018** 落地（T-002 骨架尚未包含，
  骨架 Manifest 目前只声明 `INTERNET` 权限）。
- 真实集成测试从进程环境变量 `TREELEARN_DEEPSEEK_API_KEY` 读取临时 Key，绝不打印其值。
- API Key 只经 Android Keystore + AES-GCM 保存，不进入 SQLite、日志、Bundle 或剪贴板（T-005 落地）。
