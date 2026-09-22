# Treelearn Harness Task: 搭建 Java-only Android 模块骨架

id: T-2026-09-20-002
title: 搭建 Java-only Android 模块骨架
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 160
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 构建纯 Java、本地优先、手机竖屏优先并兼容平板横屏的 Android V1。
- Problem/background: 仓库尚无 Android 工程，后续任务需要稳定模块、构建和依赖守门。
- Users/scenarios: 开发者可在 API 28+ 模拟器/设备构建和启动空壳应用。
- Expected outcome: 建立 `:app/:core/:data/:ai/:tree-layout`、Java 17、minSdk 28 和基础资源。
- Core flow and reversibility: 新建工程；失败可删除本任务新增骨架后重建，不触及产品数据。
- Inputs/outputs: 输入为技术方案；输出为可构建、可启动的模块骨架。
- State and exception handling: 初始 Activity 启动失败必须由 smoke test 暴露。
- Constraints and trade-offs: 无 Kotlin 插件、源码、依赖或 KTX；依赖只能指向 `:core`。
- AI recommendation and rationale: 先建立最小骨架和机械化守门，再叠加功能。
- User choice and custom additions: 用户已选纯原生 Android 与个人 APK。
- AI-autonomous decisions: Gradle Groovy DSL、Java 17、XML/View。
- User-confirmation decisions: Java-only、minSdk 28、手机/平板范围。
- Acceptance evidence: 构建、lint、启动、依赖图和 no-kotlin guard。
- Facts: 当前无 `android/` 目录。
- Assumptions: 本机可用 Android SDK/JDK；缺失时任务转 blocked。
- Unknowns: applicationId 可按项目命名确定，不改变产品契约。
- Risks: 新版 AndroidX 间接引入 Kotlin runtime。
- Deferred: 业务页面和网络数据库实现。
- Non-goals: 不实现任何产品功能。
- Skipped non-critical questions and reason: 主题细节由后续 UI 任务决定。

### 执行期需求变更（用户已确认，2026-09-21）

用户指示：**「SDK 使用 android-36/36.1，不在考虑 28」**。本任务的启动验证环境因此由
「API 28 模拟器」改为「API 36 手机模拟器 `Medium_Phone_API_36.1`」，`minSdk 28` 继续作为
声明的最低兼容版本，其兼容性由 `compileSdk 36 + minSdk 28` 的 lint `NewApi` 分析覆盖。
该变更同时适用于 T-004/T-005/T-018/T-019 中所有「API 28 设备」措辞。

## Goal

提供通过 Java-only 守门且可启动的五模块 Android 工程。

## Scope

### In scope
- Gradle settings、模块 build、Manifest、基础 Activity/XML、依赖方向测试和 no-kotlin 脚本。

### Out of scope
- 数据库、网络、真实业务 UI。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增 Android 启动入口；不修改现有 Docs/Harness 行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `:app` 消费其他模块；手机/平板资源目录均可解析。
- Native bridge or independent-client compatibility/update requirements: 无桥接客户端。
- Behavior-neutral non-applicability reason, if applicable: 无旧生产行为。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新增 Android 模块、SDK、构建与启动方式。
- Authoritative code/config/migration evidence: `android/settings.gradle`、各模块 build、Manifest。
- Documentation surfaces checked: `android/README.md`、技术方案。（仓库根**不存在** `README.md`——已确认 `ls README.md` 返回 `No such file or directory`，故没有根入口需要同步。此处不写「根 README 已检查」这类对应不到真实文档面的断言。）
- Documentation files updated: 新建 `android/README.md`，记录构建、测试、启动、依赖策略、Java-only 命令与 API 36 决策。
- Producer/consumer/sibling/compatibility paths checked: 五模块依赖图和启动 Activity。
- Superseded phrases/examples reverse-searched: 不存在旧 Android 启动命令。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不修改历史 PRD。

## Acceptance criteria

- [x] `cd android; ./gradlew :app:assembleDebug :app:lintDebug` 返回 0。
- [x] `powershell -NoProfile -File android\scripts\no-kotlin-check.ps1` 返回 0，依赖报告无 `org.jetbrains.kotlin`/`-ktx`。
- [x] 启动初始 Activity，首屏正常渲染且无启动崩溃。（环境由 API 28 模拟器改为 API 36 模拟器，见上文执行期需求变更）**deferred — emulator not used this round (user instruction 2026-09-21)**：记录修复轮不使用模拟器，该条在本轮**既不判 PASS 也不判 FAIL**；勾选只表示 T-002 阶段成立，见下条证据范围注记。
  - **证据范围注记（不得读作当前产物已通过设备验证）**：该项证据对应 T-002 阶段的 **15 KB / 2 dex 骨架 APK** 与 `artifacts/evidence/T-002-device-evidence.txt`（截图 `T-002-phone-first-screen.png`）。其后 `:core` 等模块已填入代码（`find android/core/src -type f -newermt "2026-09-21 01:01" | wc -l` → 28），当前产物为 **70 966 B / 3 dex**，**未做**设备验证——用户 2026-09-21 决定暂停模拟/设备侧验证并自行安装实测。勾选仅表示 T-002 阶段成立。
- [x] 机械化依赖检查证明 `:core` 不反向依赖 app/data/ai/tree-layout。

## TDD execution record

### RED — failing test first
- Test/scenario: 目标测试 = `android/scripts/no-kotlin-check.ps1`（Java-only 守门：源码扫描 + Gradle 脚本扫描 + 依赖报告断言 + APK dex 表）。Android 工程骨架不存在时该命令必须非零退出。
- Command: `powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\no-kotlin-check.ps1`
- Exit code: 1
- Observed failure: `VERDICT: FAIL (2 problem(s))` —— 守门报 `No Gradle build scripts found under ...\android` 与 `Gradle wrapper not found ... ; cannot produce the dependency report`。
- Failure reason validated: 失败原因确为「Android 工程骨架不存在」——`settings.gradle` 缺失（故 0 个 Gradle 脚本）、wrapper 缺失（故无法产出依赖报告）。`ls android` 返回 `No such file or directory`，`ls android/scripts/no-kotlin-check.ps1` 同样不存在，与断言一致。

```
== Kotlin source files ==
OK   no .kt/.kts source files
== Gradle build scripts ==
== Resolved dependency report ==
== Result ==
FAIL No Gradle build scripts found under D:\Project\Android\Treelearn\android
FAIL Gradle wrapper not found at D:\Project\Android\Treelearn\android\gradlew.bat ; cannot produce the dependency report
VERDICT: FAIL (2 problem(s))
```

- 说明（诚实记录执行顺序）：本任务先建立骨架、后补 RED 证据。为取得**真实**的 RED，把整个 `android/` 移走后用**同一条命令**重跑守门，得到上述退出码 1；随后还原骨架、用同一条命令取得 GREEN。RED 与 GREEN 的命令与断言完全一致。
- 证据文件：`artifacts/evidence/T-002-no-kotlin-guard.txt`（最终状态）。
- RED 原始输出**无留存**，而且这不是「见早期版本历史」可以交代的：`android/` 与 `artifacts/` **均未被 git 跟踪**（`git ls-files android | wc -l` → 0；`git ls-files artifacts | wc -l` → 0），仓库里不存在任何可引用的版本历史。可复现的等价取证方式：把整个 `android/` 移走后用**本节同一条命令**重跑守门，应得到上文记录的退出码 1 与同两条 FAIL 行。

### GREEN — minimum implementation
- Implementation: `settings.gradle` + 根 `build.gradle`（AGP 8.13.2、`verifyDependencyDirection`）+ 五模块 build 文件 + `AndroidManifest.xml` + `MainActivity` + 布局/主题/颜色/字符串/自适应图标 + Gradle wrapper 8.14.3 + `scripts/no-kotlin-check.ps1`。（备份/网络安全的资源与 Manifest 属性属 T-005/T-018，本任务不实现，避免越界与重复证据。）
- Command: `powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\no-kotlin-check.ps1`
- Exit code: 0
- Passing evidence: 守门由 RED 的 `VERDICT: FAIL (2 problem(s))` 转为 `VERDICT: PASS`、exit 0；骨架侧 `./gradlew :app:assembleDebug verifyDependencyDirection` → `BUILD SUCCESSFUL in 41s` / `70 actionable tasks: 62 executed, 8 from cache`，`verifyDependencyDirection: PASS (downward-only module graph + pure-JVM module purity)`。
  （守门此后经五轮独立复核加固，行数/哈希已变；当前身份见 `Handoff notes`，最终输出见 `artifacts/evidence/T-002-no-kotlin-guard.txt`。）
- Regression evidence: 见下表。

| 回归面 | 命令 | 结果 |
| :--- | :--- | :--- |
| 全量构建 + lint + 依赖方向 + dex 守门 | `./gradlew :app:assembleDebug :app:lintDebug verifyDependencyDirection verifyNoKotlinDex :core:test :tree-layout:test` | `BUILD SUCCESSFUL`；lint 在 `abortOnError` 下 **0 error**（warning 条数随 AGP/lint 版本漂移，**不固化**；本轮实测 11） |
| 主代码依赖面 | `:app:dependencies --configuration debugRuntimeClasspath` | `:core :data :ai :tree-layout`，**零第三方依赖** |
| 测试依赖面 | `:app:dependencies --configuration debugAndroidTestRuntimeClasspath` | `androidx.test:runner:1.5.1 → monitor:1.6.0 / storage:1.4.2 / annotation:1.0.1` + `junit:4.13.2`；`org.jetbrains.kotlin` / `-ktx` 命中数 **0** |
| dex 级 Java-only | `./gradlew verifyNoKotlinDex` | `app-debug.apk` 15 140 B / 2 dex → 零 Kotlin；`app-debug-androidTest.apk` 754 229 B / 3 dex → 仅 `Lkotlin/Metadata;`（已登记例外），无 `kotlin/jvm/internal` |
| 设备冒烟（T-002 阶段） | `adb shell am start -W` + `:app:connectedDebugAndroidTest` | `emulator-5554`（API 36 / x86_64）；`com.treelearn` versionCode 1 / versionName 1.0 / minSdk 28 / targetSdk 36；冷启动 `LaunchState: COLD`、`TotalTime: 827ms`、`topResumedActivity` 为本应用、`FATAL EXCEPTION` 计数 0；connectedDebugAndroidTest → 1 test / 0 failure。证据：`artifacts/evidence/T-002-device-evidence.txt`、截图 `artifacts/evidence/T-002-phone-first-screen.png`。注：`emulator-5554` 序列号出自技术方案文档，**不在**证据文件内——该文件只记录 `sdk=36 abi=x86_64 model=sdk_gphone64_x86_64` |
| 守门双向自检（第六轮加固后，**完整模式**） | `powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\dev\guard-selftest.ps1`（不加 `-Fast`） | **40 例全 PASS** / `SELFTEST EXITCODE=0`，含两个需要真实 Gradle 的用例（CASE 7 GString `compileOnly`、CASE 8 `staging/` 下含 Kotlin APK）与新增的 CASE 11 自解析闸门；**记录修复轮注**：该 40/40 只在**无并发构建**的窗口可复现（本文件「第八轮」重录，`T-002-guard-selftest.txt` 第 158 行 `OVERALL = SELFTEST PASS (40 case(s))`）；若其它 lane 正在写 `build/`，CASE 0 会因副本继承撕裂产物而红（39/40，见 `VERIFY-T002-guard-selftest*.txt`）。证据：`artifacts/evidence/T-002-guard-selftest.txt` |

### REFACTOR — optional
- Cleanup: 把「所有 instrumentation 测试统一放在 `:app` 的 androidTest」定为约定，避免每模块重复测试 runner 与 test APK；`mipmap-anydpi-v26` 并入 `mipmap-anydpi`（minSdk 28 下 v26 限定符冗余）。
- Regression evidence: 重构后 `:app:lintDebug` 仍 0 error，`verifyDependencyDirection` 仍 PASS，设备测试仍 1/1 通过。

### 独立守门发现与修复（VERIFY 阶段，两轮）

| # | 来源 | 发现 | 处置 | 复核证据 |
| :-- | :--- | :--- | :--- | :--- |
| 1 | verifier | `android/README.md` 依赖策略段写 `runner:1.5.2` / `monitor:1.6.1`，与实际代码（1.5.1 / 1.6.0）相反，且正好指向会崩溃的版本 | 改 README 为 1.5.1 / 1.6.0，并写明 1.6.1+ 崩溃原因 | `grep -n "1.5.2\|1.6.1" android/README.md` → 仅出现在「为什么不能升到 1.6.1+」说明段 |
| 2 | verifier | `README.md` 列出 `:data:connectedDebugAndroidTest`，但 `:data` 无 androidTest 源集 | 改为只列 `:app:connectedDebugAndroidTest`，并把「测试统一放 `:app`」写成约定 | README 常用命令段 |
| 3 | verifier | `lint.xml` 的忽略理由（PropertyEscape 报错且建议值与现值相同）无法复现 | 对照实验证明是过期 lint 缓存；**删除 `lint.xml` 与全部 `lintConfig` 引用** | 移走 `lint.xml` + 清空 lint 中间产物后 `:app:lintDebug` → `BUILD SUCCESSFUL`，0 error |
| 4 | verifier | `:core`/`:tree-layout` 尚无 `src/`，`verifyDependencyDirection` 的 purity 分支实为空跑 | 记为已知覆盖缺口；verifier 已用探针（植入 `:core → :app` 与 `import android.util.Log`）实证该分支能正确 exit 1；由 T-003 / T-016 填入代码后自然生效 | verifier 探针实验（仓库外副本） |
| 5 | no-kotlin-guard | 守门脚本 4 个检测漏洞（插件模式覆盖不全 / 漏 `.toml` / 块注释失步 / 无字节码校验） | 重写脚本：真实 Groovy 词法剥离注释、纳入 `.toml`、补齐模式；新增 `verifyNoKotlinDex` 扫 APK dex 字符串表 | 守门 `VERDICT: PASS` exit 0；`verifyNoKotlinDex: PASS (2 APK(s) inspected...)` |
| 6 | no-kotlin-guard | `androidx.annotation:annotation-experimental:1.1.0` 含 `Lkotlin/Metadata;` 类 | 判定为「Kotlin 编译但不链接运行时」，不会崩溃；登记为唯一例外并让守门显式打印 `NOTICE (accepted)` | dex 扫描输出：仅 `Lkotlin/Metadata;`，无 `kotlin/jvm/internal` |

- 首轮 verifier 判定 `FAIL`，唯一失败面为文档与事实不一致（其原话：「代码正确，失败面仅为文档对齐」）；第 1–3 项修复后需重新验证。

### 独立守门发现与修复（VERIFY 阶段，第二轮）

第二轮由一位全新独立 verifier 复核修复结果，判定 `PARTIAL`，指出 6 项：

| # | 发现 | 处置 | 复核证据 |
| :-- | :--- | :--- | :--- |
| 7 | **守门脚本会假失败（严重）**：`$ErrorActionPreference='Stop'` + `2>&1` 把 Gradle 的原生 stderr（javac 的「注: …使用或覆盖了已过时的 API。」）升级成终止性 `NativeCommandError`，脚本在第 222 行中断、**不输出 VERDICT、exit 1**。任何 androidTest 重编译都会触发 | 新增 `Invoke-NativeCaptured`，在调用外部进程前后临时把 `$ErrorActionPreference` 置为 `Continue`，只依赖 `$LASTEXITCODE` | 先复现条件：`:app:compileDebugAndroidTestJavaWithJavac --rerun-tasks --no-build-cache` 的 stderr 实测 198 字节（含两条 `注:`）；再在该状态下运行守门 → `VERDICT: PASS`、`EXITCODE=0`（证据：`artifacts/evidence/T-002-guard-stderr-regression.txt`） |
| 8 | 守门仍有漏检：`plugins { kotlin("android") }`、`kotlinOptions { }`、Groovy slashy 字符串 `/x/*y/` 致词法失步 | 补 `(?<![\w.])kotlin\s*\(`、`\bkotlinOptions\b`、`\bkotlin\s+version\b`、`\bkapt\b`/`\bksp\b` 等模式；词法器新增 slashy-string 状态（`/` 紧跟 `=` `(` `,` `:` `[` `~` `!` 时进入） | 守门对 11 个脚本扫描 PASS；`verify-no-kotlin.gradle` 自身不再触发（`org.jetbrains.kotlin` 字面量改为运行时拼接） |
| 9 | 守门误报：`exclude group: 'org.jetbrains.kotlin'`（这是**排除** Kotlin 的正当 Java-only 手段）与字符串中的 `build.gradle.kts` | `exclude` + Kotlin group 的行降级为 `NOTICE`；删除冗余的 `\.kts\b` 内容模式（真实 `.kts` 文件已由扩展名扫描覆盖） | 守门 PASS 且未出现该两类误报 |
| 10 | 架构守门漏检：`compileOnly project(':app')`（只看 `implementation`/`api`）、无 import 的全限定 `android.util.Log` | `verifyDependencyDirection` 改为检查全部可承载项目依赖的配置（含 `compileOnly`、`compileOnlyApi`、`runtimeOnly`、`annotationProcessor`、测试配置），纯度扫描同时匹配 Android 包名全限定用法 | `verifyDependencyDirection: PASS`；对空模块打印 WARNING（见 #12） |
| 11 | README 事实错误：称 monitor `1.7.2` 的 POM 不声明 kotlin-stdlib；实际 1.7.2 **声明** `org.jetbrains.kotlin:kotlin-stdlib:1.8.20`，只有 1.6.1 不声明 | 重写该段，按版本分别说明：1.7.2 会被依赖报告拦下；1.6.1 只能靠 dex 级校验发现 | README「为什么不能升到 monitor 1.6.1」段 |
| 12 | `:core`/`:tree-layout` 纯净性扫描空跑，但任务/README 表述得像已强制执行 | `verifyDependencyDirection` 对无源码模块打印 `WARNING … purity contract is vacuous until T-003 / T-016 land code` 并在 PASS 行追加 `purity NOT yet enforced for [:core, :tree-layout]`；README 增设「当前覆盖缺口」小节 | 任务输出实测：两条 WARNING + PASS 行含 `purity NOT yet enforced` |
| 13 | `:data`/`:ai` 仍声明 androidTest 依赖与 runner，但无 androidTest 源集 → 其 test APK 永不构建也永不被扫，且是 Kotlin 渗入通道 | 移除两模块的 `androidTestImplementation` 与 `testInstrumentationRunner`，并把「所有 instrumentation 测试统一放 `:app` androidTest」写成约定 | `:app:dependencies --configuration debugAndroidTestRuntimeClasspath` 仍是唯一含测试栈的配置；设备测试 1/1 通过 |
| 14 | API 36 取代 API 28 的偏离只在 T-002 记录，`Docs/.../全量开发交付提示词.md` 与 T-001/004/005/012/017/018/019 的「API 28 设备」措辞未同步 | 在提示词文档顶部加「已被用户 2026-09-21 指令取代」横幅并逐条标注；同步 7 个 Task 的验收/假设措辞为 API 36，保留 `minSdk 28` 作为声明兼容下限；技术方案 §6.2 同步 | 全仓库 `grep "API 28"` 剩余命中仅为「`minSdk 28` 兼容下限」与已被取代的原文保留段 |

- 第 7 项是本轮最重要的修复：它把守门从「CI 假红或被人绕过」变成可信守门。

### 独立守门发现与修复（VERIFY 阶段，第三轮）

第三轮由又一位全新独立 verifier 复核，判定 `FAIL`，指出 3 项（三轮中**质量最高**的一轮：它打穿了前两轮都放过的**真实绕过路径**）：

| # | 发现 | 处置 | 复核证据 |
| :-- | :--- | :--- | :--- |
| 15 | **守门可被完全静默绕过（严重）**：第二轮第 8 项宣称的 slashy 词法加固**实为死代码**。verifier 在仓库外副本给 `app/build.gradle` 追加合法 Groovy 行 `def re = /a\/*b/` 与 `releaseImplementation 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0'`，守门仍输出 `VERDICT: PASS`、exit 0。根因（verifier 用微实验隔离证明）：`no-kotlin-check.ps1:103` 的 `'=(,:[' -contains $prev` 在 PowerShell 中是**集合包含**判断而非子串匹配，对该字符串恒为 `False`（实测 `'=(,:[' -contains '='` → `False`，`@('=','(',',',':','[') -contains '='` → `True`），slashy 状态永不可达；同时 `/*` 判定先于 slashy 且不处理 `\/` 转义，于是 `\/*` 被当块注释起点，吞掉该文件其后所有行。三条兜底同时失效：依赖段只查 debug 配置（release 专属依赖不可见）、dex 段对未构建的 release APK 打印 `SKIP`。 | ① 改用 `$slashyPreceders.IndexOf($prev) -ge 0`；② **fail-closed 措施 1**：词法器回传终态，若未回到 `code`/`line` 则判 FAIL 并明说「该文件的剥离扫描不可信」；③ **fail-closed 措施 2**：新增**不经过注释词法器**的原文全文扫描 `$rawKotlinDependencyRe`，命中带引号的 `org.jetbrains.kotlin` 或 `-ktx` 坐标的依赖声明行即 FAIL，使「注释剥离出错」不再可能隐藏 Kotlin 依赖；④ 依赖段补 `releaseRuntimeClasspath`；⑤ 子进程失败时回显 stderr 摘要（见 #16） | `artifacts/evidence/T-002-guard-selftest.txt`：`CASE1 round-3 bypass caught = True`、`CASE2 slashy+plugin caught = True`；CASE 3 注入矩阵 11/11 `[CAUGHT]`；CASE 4 正当写法 5/5 `[NO-FALSE-FAIL]`；CASE 5 `fail-closed lexer trust = True`；CASE 0 仓库全量 `EXIT=0 / VERDICT: PASS`；`OVERALL = SELFTEST PASS` |
| 16 | 子进程失败原因被吞：一次运行因 Gradle 守护进程持有 `journal-1.lock`（`拒绝访问。`）触发 3 条 `FAIL Dependency report failed`、exit 1，与 Kotlin 无关，且不可 100% 稳定复现 | 新增 `Get-FailureExcerpt`，依赖段/dex 段失败时回显子进程输出的末 6 行非空摘要 | 守门源码新增 `Get-FailureExcerpt`，失败分支打印 `excerpt:` 与缩进摘要；正向路径输出未变 |
| 17 | lint warning 数由任务原先记录的 13 条漂移到 11 条 | 决定不追因（warning 集合随 AGP/lint 版本与资源微调变化），改在任务/README 中不再固化 warning 条数，只保留「0 error」这一硬事实 | 本轮与会话内多次运行均为「0 Error / 全 Warning」 |

- 第 15 项是三轮复核中**最重要**的发现：前两轮都在「守门是否误报／是否漏配某个模式」的层面，这一轮直接证明守门存在**完全静默通过**的路径，等于在最需要它的场景下没有守门能力。修复后守门的可信性不再依赖单点词法器正确，而由「词法器终态自检 + 与词法器无关的原文扫描」双保险保证。
- 教训已写入根 `AGENTS.md` 失败案例库：守门脚本的每条检测分支必须配「注入必须被拦住」的自检用例；解析/词法类守门必须 fail-closed。
- 尚未闭环（已知、已记录）：androidTest APK 的 `Lkotlin/Metadata;` 例外未在代码中绑定到具体构件（dex 无法回溯归属），只以 `NOTICE (accepted)` 显式打印并在 README 登记为唯一例外。

### 独立守门发现与修复（VERIFY 阶段，第四轮）

第四轮独立 verifier 复核第三轮修复，确认第三轮三项修复**真实有效**（CASE1/CASE2 攻击脚本重放已被拦、fail-closed 措施在实测中生效），但给出 1 项严重 + 1 组「静态层不是证明」的结构性发现 + 1 项误报：

| # | 级别 | 发现 | 处置 | 复核证据 |
| :-- | :--- | :--- | :--- | :--- |
| 18 | **严重** | **release APK 从不被扫描**：`verify-no-kotlin.gradle` 把产物名硬编码为 `app-release.apk` 并标 `optional: true`，而 AGP 实际产物是 `app-release-unsigned.apk` → dex 阶段对 release 打印 `SKIP`。即「发布产物」这一最该被守的构件根本不在守门范围内，第三轮修复的「release 缺口」并未真正闭环 | 改为**按目录 glob 发现全部 `*.apk`**（兼容未来改名与多产物），并把三个 group 全部改为**强制**（`dependsOn` 三个 assemble 任务，产物缺失即 FAIL，不再有 optional/SKIP 分支） | `T-003-guard.txt`：`verifyNoKotlinDex: app release APK (shipped artifact) [app/build/outputs/apk/release/app-release-unsigned.apk] = 31580 bytes, 1 dex file(s) [classes.dex]`；`verifyNoKotlinDex: PASS (3 APK(s) inspected, no Kotlin runtime bytecode)` |
| 19 | 中（结构性） | **静态文本扫描在原理上不可能是证明**：verifier 用 8 种**完全合法**的 Groovy 写法构造出任何逐行静态扫描都看不见的 Kotlin 坐标 —— `\uXXXX` 转义、GString 插值（`"${'org.jetbrains'}.kotlin:..."`）、字符串拼接、坐标跨行拆分、`$/.../$` dollar-slashy 令词法器失步后又在 `*/` 处重同步、`kotlin.code.style` 版本目录键、把 `exclude group:` 反用作声明、`sourceSets 'src/main/kotlin'` | **不试图把静态层做成完备**（做不到，且越改越脆）。改为把分层写进脚本头 `SCOPE HONESTY` 与 `android/README.md`：**静态层只是预筛，权威证明只能是 stage 3（每条 classpath 的解析后坐标）+ stage 3b（真实 APK 的 dex 字符串表）**；并明确禁止以 `-SkipDependencyStage` 运行后把结果当 PASS（该模式 exit 2 / PARTIAL）。另补 `^\s*kotlin\.` 模式收 `kotlin.code.style`，`exclude` 判定收紧为「必须含 `group:`/`module:` 值且不含 Kotlin 坐标」才算正当移除 | 上述 8 种写法逐一注入仓库外副本：stage 2 有漏有拦，但 **stage 3 / 3b 全部拦下**；脚本头 SCOPE HONESTY 段与 README 新增小节；`sourceSets 'src/main/kotlin'` 另由 stage 1 兜住（stage 1 对 `android/` 全树递归找 `.kt`/`.kts`，与目录名无关） |
| 20 | 轻（误报） | 原文兜底扫描（fail-closed 措施 2）**不区分注释与代码**，把 `// implementation 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0'` 这种**注释掉的**历史行判为 FAIL | 降级为 `NOTICE`：仅当**词法器干净收尾**（终态为 `code`/`line`，即注释剥离可信）且该行在剥离后为空时才降级。若词法器失步，措施 1 已先判 FAIL，真实违规无法借这条降级隐藏 | 自检探针新增该用例（注释行 → `NOTICE` 而非 FAIL）；CASE 4 正当写法 5/5 `[NO-FALSE-FAIL]` 保持 |

- 第 19 项是本轮最有价值的结论：它把守门的**可信边界**讲清楚了，而不是继续在静态模式上打补丁。三轮里我一直在「补一个漏掉的模式」，verifier 用合法 Groovy 证明这条路没有终点。真正的修复是**承认静态层的能力上限，并把权威证明归位到解析后依赖报告与 dex 字符串表**——这两层无法用文本技巧绕过，因为它们读的是构建真正产生的东西。
- 第 18 项与第 19 项合起来的教训：**「已修复」必须以「修复后的守门真的扫到了那个构件 / 那一层」为证据**，不能以「我改了代码」为证据。第 18 项正是我第三轮声称修了 release 缺口、实际却仍打印 `SKIP` 的例子。
- 守门脚本当前身份：`android/scripts/no-kotlin-check.ps1`，**SHA-256 `BDBCC52DBA13022921F44C15E05DBFF198E33F9CB7FD85FB7D234264F38AD779`，439 行**（第四轮修复后的值；此前记录的 `7c6a34fb…` / 371 行为第三轮后已过期值，此处更正）。

### 独立守门发现与修复（VERIFY 阶段，第五轮）

第五轮独立 verifier 复核第四轮修复，用**合法 Groovy** 再次打穿守门，给出 2 项高危 + 1 项中 + 1 组证据问题 + 2 项自述不实。其中第 21 项是**我第四轮的修复自己引入的新绕过**。

| # | 级别 | 发现 | 处置 | 复核证据 |
| :-- | :--- | :--- | :--- | :--- |
| 21 | **高（自引入）** | 第 20 项的「注释降级」判据是「剥离后该行为空 **且** 词法器干净收尾」——等于**让解析器为自己的漏判背书**。verifier 用合法 Groovy 复现：`def s = $/ /* /$` 令词法器进入块注释态，中间放真实的 `compileOnly 'org.jetbrains.kotlin:kotlin-stdlib:2.0.0'`，再 `def t = $/ */$` 干净重同步 → 真实依赖行被剥成空行、终态为 `code`，守门打印 `NOTICE` 后 `VERDICT: PASS`（exit 0） | 降级判据改为**只看原文行是否以 `//` 开头**，完全不询问词法器，因此无法被任何词法技巧伪造。代价（有意接受）：块注释内的 Kotlin 坐标现在判 FAIL——fail-closed 方向 | 自检新增 CASE 6：dollar-slashy + `compileOnly` 必须 FAIL。同时 CASE 4 的「行注释」正当写法仍必须不误报，两条一起把它钉在两个方向上 |
| 22 | **高** | 依赖段只查 `debugRuntimeClasspath`/`releaseRuntimeClasspath`/`debugAndroidTestRuntimeClasspath` **三个手写配置**。verifier 用 GString 形态 `compileOnly "org.jetbrains.${'kotlin'}:kotlin-stdlib:2.0.0"`（静态层原理上看不见）注入后，**完整守门仍 PASS** | 改为**按模式识别而非手写清单**：单次调用取 `:app`/`:core`/`:data`/`:ai`/`:tree-layout` 的完整依赖报告，按配置名模式 `(Compile|Runtime|AnnotationProcessor)Classpath$` 选**全部变体 classpath**，按 `^(_internal|detachedConfiguration|classpath$)` 排除 AGP/Gradle 自身的构建工具配置，并**断言匹配集非空**（模式过期就响亮失败，而不是「什么都没验证却通过」） | 自检新增 CASE 7（GString + `compileOnly` 必须被 stage 3 拦下）。实测输出：`no Kotlin coordinates on 53 variant classpath configuration(s) across :app, :core, :data, :ai, :tree-layout (build tooling configurations excluded, 4201 report lines)` / `excluded 36 build-tooling configuration(s)` |
| 23 | 中 | APK 段只 glob **三个硬编码目录**（`app/build/outputs/apk/{debug,androidTest/debug,release}`）。verifier 把含 Kotlin 的真实 APK 放到 `app/build/outputs/apk/staging/`，守门仍打印 `PASS (3 APK(s) inspected)`——staged 产物永不被扫 | 改为**递归遍历 `app/build/outputs/apk` 全树**，三个期望产物降级为**覆盖断言**（每组必须仍有产物，否则 FAIL）而非扫描边界；严格度按**路径**判定（`androidTest` 路径允许 `Lkotlin/Metadata;`，其余 shipped APK 两个 needle 任一命中即 FAIL） | 自检新增 CASE 8：把 androidTest APK 复制到 `staging/staged.apk` 后必须 FAIL |
| 24 | 中（证据） | ① `artifacts/evidence/T-002-guard-selftest.txt` 仍是第三轮的值（`7c6a34fb…` / 371 行），已被后续两轮改动作废；② 更根本：第 20 项声称「自检探针新增该用例」，但**该探针从未落盘**——它只是被内联跑过一次，verifier 在仓库里找不到任何可复现它的东西 | 把自检**落盘为可复现脚本** `android/scripts/dev/guard-selftest.ps1`（在工作树**之外**的临时副本上运行，从不修改工作树；`-Fast` 模式 23 例不需要真实 Gradle，完整模式 25 例含 2 个 Gradle 用例）；证据文件改为**流式写出 + UTF-8**：原先用 `>>` 重定向原生命令输出，PowerShell 5.1 写的是 **UTF-16LE**，证据文件在评审工具里显示为二进制；同时把「捕获到变量再一次性写出」改为逐行流式写，被超时中断时也能保留已打印内容（上一轮就因此得到一个空的证据文件） | `artifacts/evidence/T-002-guard-selftest.txt`（完整模式 25 例全 PASS / `SELFTEST EXITCODE=0`）；`artifacts/evidence/T-002-no-kotlin-guard.txt`（`VERDICT: PASS` / `GUARD EXITCODE=0`） |
| 25 | 轻（自述不实） | Handoff 里「Gradle 构建配置 **529 行**」不可复现 | 更正为实测值，并**不再把它当作冻结数字**（守门的 Gradle helper `verify-no-kotlin.gradle` 本身也被本轮重写，数字还会动）。当前实测（**记录修复轮再测**）：逐文件回读 **606 行 / 8 文件**（`artifacts/evidence/T-002-gradle-size-2026-09-21-repair.txt`，39+108+121+27+40+219+25+27=606）。第六轮当时为 **599**；此后 leader 于 21:36 调整 `android/app/build.gradle` 的 `signingConfigs` 顺序（+17/−10 = +7 行），**文件数 `8` 不变**。原句：`find . -name '*.gradle' -not -path '*/build/*' -type f \| xargs wc -l` → **599 total**；`find . -name '*.gradle' -not -path '*/build/*' -type f \| wc -l` → **8 个文件**（旧的「10 个」把 `wc -l` 的 `total` 行与 `./.gradle` 目录条目一起数了进去，见第六轮 F4） | 上述两条命令输出；`local.properties` 不计入（gitignored、含机器路径） |
| 26 | 轻（自述不实） | 「`:core` / `:tree-layout` 纯净性扫描空跑」「debug APK 15 KB / 2 dex」已半失效：T-003 已给 `:core` 填 14 个类型（T-021 后为 17 个），纯 Java-only 扫描对它**已是真实强制**；debug APK 也已不是 15 KB / 2 dex | 同步 `android/README.md`：纯净性缺口只剩 `:tree-layout`（待 T-016）；APK 尺寸改为「约 70 KB / 3 dex」并注明字节数随构建变化、精确值以证据文件为准 | `android/README.md` 对应小节；本任务 Handoff 同步 |

- 第 21–23 项合起来的教训，比第四轮第 19 项更进一步：**「不再手写清单」本身必须被机械保证**。三轮里我两次因为手写枚举而留盲区（手写 3 个 runtime classpath、手写 3 个 APK 目录），两次都是 verifier 往枚举之外放东西就打穿。现在的做法是**按模式识别 + 断言匹配集非空**：模式过期时守门**响亮失败**，而不是退化成「什么都没检查却打印 PASS」。这条已写进脚本头的 `SCOPE HONESTY`。
- 第 21 项还留下一条可复用的判据：**凡解析器的输出既充当判据、又充当豁免依据者，必须再用一条不共享该解析器状态的独立判据复核**。原判据同时问「剥完是不是空的」和「词法器收尾干净吗」，两个问题都由同一个可能失步的词法器回答，因此它可以自证清白。
- 守门脚本当前身份（第六轮修复后实测；命令：`Get-FileHash -Algorithm SHA256` + `(Get-Content <file>).Count`）：
  - `android/scripts/no-kotlin-check.ps1` — SHA-256 `5F3FDB52F9EF8E6F0B20D016065A06F4A925F18353B5DE1AFC28D6277C7A3C32`，**692 行**
    （历史值：`B9D342E8…` / 577 行为第五轮，`BDBCC52D…` / 439 行为第四轮，均已过期）
  - `android/scripts/lib/evidence-integrity.ps1` — SHA-256 `DD2E4A55A6641D949C735AFDDBCE1A6D8654BD158F07119E59099786CA65B426`，169 行
  - `android/scripts/dev/guard-selftest.ps1` — SHA-256 `9D680E26CCF291D8CAB5B818B6851A9B6B21EDDB21C7826F1E5806388FA98828`，**570 行**（记录修复轮实测回读；mtime 2026-09-21 12:38:05）

### 第六轮独立复核发现与修复（VERIFY 阶段，第 3 轮）

第六轮 verifier 判 **FAIL**，但判词对工程实体是肯定的：字节码级 Java-only、五模块、构建/lint/测试全 exit 0、守门 PASS、自检全 PASS **均被独立复现、未发现回归**；判 FAIL 的原因只有两类——**证据引用与当前态数字失真**（F1 阻塞）与守门自身一条**静默退化路径**（F3）。逐条处置：

| 编号 | 级别 | 发现 | 处置 | 复核证据 |
| :-- | :--- | :--- | :--- | :--- |
| F1 | **中（阻塞）** | 任务与 `android/README.md` 把 `artifacts/evidence/T-002-guard-selftest.txt` 当作「**完整模式 25 例**、含 CASE 7/8」的证据，而该文件**自身**写的是 `-Fast` / `CASE 7-8 skipped` / `23 case(s)`——被引用的恰好是它跳过的两个用例 | 以**完整模式**重跑并落盘到同一路径（见下方「本轮证据刷新」）；任务与 README 只引用完整模式那份，且运行模式写进文件头，引用方必须核对该模式 | 见下方证据行 |
| F2 | 中 | 同一文件缺 AGENTS 要求的头部：无 `Generated:`、无被核脚本 `SHA-256` 与行数，无法判定出自哪个版本 | `guard-selftest.ps1` 输出头增加 `Generated:` / `Command:`（含是否 `-Fast`）/ `Guard` 与 `Library` 的 `SHA-256` 与行数；捕获封装再补 `ExitCode` / `Seconds` / `CapturedAt` / `Mode` | 该文件头 6 行 |
| F3 | 中 | 守门 stage 3b 只看 `gradlew verifyNoKotlinDex` 的**退出码**，不断言判据行存在：零输出时仍可打印 `OK no built APK contains Kotlin runtime bytecode` 并 `VERDICT: PASS` | 新增共享库 `Get-ApkDexStageEvidence`：要求 ≥1 条 `^verifyNoKotlinDex:` 判据行、出现最终 `PASS (N APK(s)`、且 N 可解析且 ≥1；任一不满足即 FAIL。守门与自检**导入同一文件**，不再各写一份 | 自检 CASE 10 的 11 条方向性断言；本轮守门输出 `5 judgement line(s)` |
| F4 | 轻 | 「Gradle 构建配置 574 行 / **10 个文件**」实测 **8**（`wc -l` 的 `total` 行与 `./.gradle` 目录条目被当成文件） | 见下方变更规模：**606 行 / 8 个 `*.gradle` 文件**（记录修复轮再测；第六轮为 599），并给出可复算的两条命令 | `find … -type f \| wc -l` → 8 |
| F5 | 轻 | 「自检探针 261 行」实测 317 行（写任务时数字已过期） | 见下方变更规模：**570 行**（记录修复轮实测回读。第六轮当时是 467 行，此后脚本又被改过，因此 467 已过期）。**不再凭记忆写行数**，一律改完后回读 | 该行给出的实测命令 |
| F6 | 轻 | 回归表写 lint「0 error（**13 条**全为 warning）」，与「不再固化 warning 条数」的决定及实测 **11** 自相矛盾 | 改为「0 error（warning 条数随 AGP/lint 版本漂移，不固化；本轮实测 11）」 | 本轮实测 11 |
| F7 | 轻 | 「RED 原始输出见该文件的早期版本历史」指向**不存在物**：`android/` 与 `artifacts/` 均未被 git 跟踪 | 直说 RED 原始输出无留存，并给出可复现的等价取证方式（移除 `android/` 后重跑同一条命令） | `git ls-files android \| wc -l` → 0 |
| F8 | 轻 | 写 `assets/evidence/T-002-phone-first-screen.png`，实际在 `artifacts/evidence/`；设备行未引用真正的证据文件 | 路径改为 `artifacts/evidence/…`，并在设备冒烟行补上 `T-002-device-evidence.txt` | 两文件均存在 |
| F9 | 轻 | 「Documentation surfaces checked: 根 README」——仓库根没有 `README.md` | 改为明确记录「根不存在 `README.md`，无根入口需同步」 | `ls README.md` → No such file |
| F10 | 低 | `android/README.md` 说「**各模块**」用 `force 'androidx.test:monitor:1.6.0'`，实际只有 `:app`/`:data`/`:ai` 三处 | README 改为三个模块，并说明 `:core`/`:tree-layout` 为纯 JVM 模块、无 Android 测试栈故不需要 | `grep -rn "monitor" --include=*.gradle` |
| F11 | 低 | 「已登记例外」只列 `Lkotlin/Metadata;`，而该 APK 实含 8 个 Kotlin 描述符，例外边界描述不完整 | `verify-no-kotlin.gradle` 的 NOTICE 行改为列出该 APK 中**全部** Kotlin 类型描述符 + 计数 | 本轮守门输出：`Kotlin type descriptors in this APK (8): [Lkotlin/Deprecated;, Lkotlin/Metadata;, Lkotlin/ReplaceWith;, Lkotlin/annotation/{AnnotationRetention,AnnotationTarget,Retention,Target};, Lkotlin/reflect/KClass;]` |
| F12 | 低 | 自检 CASE 8 在前置 APK 缺失时把用例记为 `Ok = $true`，完整模式仍报 PASS——「跳过」被算成「通过」 | CASE 8 前置缺失时**按需构建** `:app:assembleDebugAndroidTest`，仍缺失则判 MISMATCH；SKIP-as-PASS 路径消除 | 自检 CASE 8 |
| F13 | 低 | 验收 #84（首屏渲染无崩溃）被勾选，但证据对应 15 KB 骨架 APK；当前产物 70 KB 且 `:core` 下 28 个源文件更新 | 在 #84 后加**证据范围注记**（见「Acceptance criteria」），明确勾选只表示 T-002 阶段成立 | `find android/core/src -type f -newermt … \| wc -l` → 28 |

**本轮自行发现（verifier 未报）——形状错配的假见证**：把共享库接进守门后，实测发现守门用 `| Out-String` 捕获子进程输出（得到**一个多行字符串**），而自检喂的是**行数组**；`foreach ($line in @($Text))` 对字符串只迭代一次，于是 Gradle 的 **5 行判据被算成「1 judgement line(s)」**，那条 OK 行实际上是整个 blob，并因内含 `NOTICE` 子串而触发了一条**假 NOTICE**。这正是「探针测的形状不是生产路径的形状」这一类假见证。修法：在共享库里加 `ConvertTo-EvidenceLines` **在生产路径上归一化**，并给自检补上**两种形状必须等价**的断言（CASE 10 新增 4 条），而不是在调用点各改一次。

另有一条同类修正：守门 stage 3 的两条证明行原先用 `Write-Host` 直接打印、**没走 `Write-Ok`**，因此既不计入 `VERDICT: PASS (N OK line(s))` 的规模计数，也逃过了 verdict 段的 `{N}` 占位符检查。已改为 `Write-Ok`（本轮 PASS 行计数由 7 升为 13）。

### 本轮证据刷新（第六轮 VERIFY 后）

| 证据文件 | 命令 | 结果 |
| :--- | :--- | :--- |
| `artifacts/evidence/T-002-no-kotlin-guard.txt` | `powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\no-kotlin-check.ps1 -AndroidRoot android` | `VERDICT: PASS (13 OK line(s))` / `GUARD EXITCODE=0`；含 stage 0（兄弟脚本自解析）与 dex 段 **5 条判据行**（3 个 APK + NOTICE + PASS），NOTICE 列出全部 8 个 Kotlin 描述符 |
| `artifacts/evidence/T-002-guard-selftest.txt` | `powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\dev\guard-selftest.ps1`（**完整模式**，不加 `-Fast`） | 见 F1 翻盘条件与文件内 `OVERALL` 行；文件头含 `Generated:` / `Command:` / `Guard` 与 `Library` 的 `SHA-256` 与行数 |

> 证据捕获方式说明：两个文件都由「逐行拼接子进程输出」写出，**不用 `Out-String`**——`Out-String` 会按宿主宽度重新折行，把长证明行从中间截断。捕获封装同时写入 `ExitCode` / `Seconds` / `CapturedAt` / `Command` / `Mode` / `Capture`。**审计时以文件内容为准，不以控制台回显为准。**

### 新增守门闸门：脚本自解析（stage 0）

修 F3/F12 的过程中，我自己把守门的续行写法改坏过：把 `+` 从**上一行行尾**挪到**下一行行首**（且在未闭合括号内），导致 `scripts/` 下**三个 `.ps1` 同时无法解析**，守门直接以 `ParseException` 退出、**零证据**，而周边工作看起来都已完成。PowerShell 的规则是：表达式在换行处若已完整即结束语句，续行必须让运算符留在**上一行行尾**（已用最小复现 P1/P5 验证）。

因此新增两道互补闸门（脚本无法自检自身——解析不了就永远到不了自己的第一行）：守门 **stage 0** 解析全部**兄弟** `.ps1`；`guard-selftest.ps1` **CASE 11** 解析**守门**，并断言 stage 0 真的跑过（**读守门运行输出**，而不是 grep 源码——否则一句仅仅提到它的注释也能骗过断言）。

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- **工具链**：Gradle 8.14.3（wrapper，`distribution-type=all`）+ AGP 8.13.2 + JDK 17 + `compileSdk 36` / `targetSdk 36` / `minSdk 28` / build-tools 36.0.0。**刻意不采用 AGP 9.x**：其内置 Kotlin 支持与 Java-only 守门直接冲突。
- **零 AndroidX 主代码**：字节码级核验证明 `androidx.appcompat:1.7.0`、`androidx.core:core:1.13.1`、`androidx.test.ext:junit:1.2.1`、`androidx.test:core:1.6.1`、`espresso-core:3.6.1` 均传递引入 `kotlin-stdlib`/`-ktx`。因此 `:app` 主代码改用平台 API（`android.app.Activity` + 平台控件 + `android:Theme.Material.Light.NoActionBar` + 矢量自适应图标），APK 最终零第三方依赖。
- **测试栈的 Kotlin 陷阱（本轮最关键发现）**：`androidx.test:monitor:1.6.1` 与 `1.7.2` 的 POM **不声明** `kotlin-stdlib`，但其 `androidx/test/platform/io/OutputDirCalculator*.class` 是 Kotlin 编译产物。设备运行时报 `ClassNotFoundException: kotlin.jvm.internal.Lambda`，栈顶为 `androidx.test.platform.io.FileTestStorage.<init>`。仅看依赖图无法发现，必须做字节码扫描。最终采用 `runner:1.5.1`（monitor 需求恰为 `[1.6.0]`）并 `force 'androidx.test:monitor:1.6.0'`（字节码核验 0 个 Kotlin 类）。
- **lint `PropertyEscape` 与 `local.properties`**：正确形式是完全转义 `sdk.dir=D\:\\Environment\\Android\\SDK`。
  第一轮我曾加过一个 `lint.xml` 忽略该检查；独立 verifier 无法复现失败，我随后做了对照实验
  （移走 `lint.xml`、清空全部 lint 中间产物后重跑）得到 `BUILD SUCCESSFUL` 且 0 条 `PropertyEscape`，
  证明**该忽略掩盖的是过期 lint 缓存而不是真实缺陷**，因此已删除 `lint.xml` 与全部 `lintConfig` 引用。
  未转义的 `sdk.dir` 会让构建直接失败（`IOException 文件名、目录名或卷标语法不正确`），故转义形式确为必需。
- **Java-only 守门自身的 4 个检测漏洞（第二轮独立 guard 实证后已修复）**：
  1. 只匹配 `kotlin-android` 与 `id\s+['"]kotlin`，漏掉 `id("kotlin")`、`apply plugin: 'kotlin'`、
     `kotlin { }`、`kapt`、`ksp`、`kotlin-kapt`、`kotlin-parcelize`、`alias(libs.plugins.kotlin.android)`。
  2. 只扫 `.gradle/.properties`，**完全漏掉 `gradle/libs.versions.toml`** 这一 Kotlin 插件入口。
  3. 块注释状态机以「行首 `/*`」为准，遇到字符串里的 `/*`（如 Groovy 三引号模板）会失步，
     导致后续真实依赖行被静默跳过 → 假阴性。
  4. 只做构件名 grep，**不做字节码校验**——正是这一点让 `monitor:1.6.1` 能骗过守门却在设备上崩溃。
  修复：注释剥离改为真正的 Groovy 词法状态机（处理 `'` `"` `'''` `"""` 与 `//` `/* */`）；
  `.toml` 纳入扫描；补齐插件/工具链模式；新增 `verifyNoKotlinDex` 任务直接扫 APK dex 字符串表。
- **唯一登记例外**：androidTest APK 的 dex 含 `Lkotlin/Metadata;`（来自 `androidx.annotation:annotation-experimental:1.1.0`），
  但无 `kotlin/jvm/internal` 链接，设备不需要 Kotlin 运行时，不会崩溃。守门以 `NOTICE (accepted)` 显式打印；
  主 APK / release APK 无此例外，两个 needle 任一命中即 FAIL。
- 完成前必须由 `android-no-kotlin-guard` 只读复核。
- 若任一候选库传递引入 Kotlin，改用平台 Java API或合规版本。

## Verification report
> **最新结论：见本段末尾「第九轮独立复核结论」（verifier #2，2026-09-21 第四轮）。下方第一个 - Verdict: 是历史轮次的记录，不是当前判定。**

> 本节由**独立 verifier** 填写。以下为交付方自述的待核事实与已知缺口，**不构成 PASS**。

- Verdict: 第 6 轮 **FAIL（记录层）**、第 7 轮 **FAIL（记录层；工程实体层未发现回归）**（见下方「第七轮独立复核结论」，2026-09-21）。两轮判词对工程实体都是肯定的：字节码级 Java-only、五模块、构建/lint/测试 exit 0、守门 PASS、自检全 PASS 均被独立复现且未发现回归；判 FAIL 只因**证据引用与当前态数字失真**（第六轮 F1 阻塞）与守门一条**静默退化路径**（第六轮 F3）以及**自检证据无法绑定到当前脚本**（第七轮①②）。F1–F13 与第七轮两条未通过项已全部修复并刷新证据，见「第八轮：记录修复轮」；**等待下一轮独立复核**。**不得把自述读作验收结论。**
- Evidence:
  - `artifacts/evidence/T-002-no-kotlin-guard.txt` — 真实仓库完整守门，`VERDICT: PASS` / `GUARD EXITCODE=0`，
    含 stage 1（源码）、stage 2（Gradle 脚本预筛）、stage 3（53 个变体 classpath）、stage 3b（递归发现的 3 个 APK）。
  - `artifacts/evidence/T-002-guard-selftest.txt` — 双向自检，**完整模式 40 例全 PASS** / `SELFTEST EXITCODE=0`（**第八轮记录修复轮再次重录**：文件头现含自检脚本**自身**的 SHA-256/行数/mtime 与运行环境说明，并同时保留一次 harness 中止（RUN A exit 3）；40/40 只在无并发构建的窗口可复现）
    （第六轮以完整模式重录，文件头含 `Generated:` / `Command:`（无 `-Fast`）/ `Guard` 与 `Library` 的 `SHA-256` 与行数；
    此前该文件是一次 `-Fast` 运行，被引用为「完整模式 25 例」的证据，而它恰恰跳过了 CASE 7/8 —— 见第六轮 F1），
    含两个需要真实 Gradle 的用例（CASE 7 GString `compileOnly`、CASE 8 非标准目录下的含 Kotlin APK）。
  - `artifacts/evidence/T-002-runtime-classpath.txt`、`T-002-androidtest-runtime-classpath.txt` — 早期依赖报告。
  - `artifacts/evidence/T-002-guard-stderr-regression.txt` — `NativeCommandError` 回归对照。
- Changed/deleted assertion rationale and equivalent behavior coverage:
  自检用例相对第四轮有净增与**一处判据反转**——第 20 项的「注释行降级为 NOTICE」在 CASE 4 中保留
  （正当写法不得误报），同时新增 CASE 6 要求「块注释内的坐标必须 FAIL」（第 21 项修复的直接后果）。
  两者方向相反、同时存在，因此不能靠「把所有坐标都判 FAIL」或「都判 NOTICE」其中任一种走捷径通过。
- Gaps:
  - **静态层的完备性仍未实现，且不被声称**。stage 2 只是预筛；合法 Groovy（`\uXXXX`、GString 插值、
    字符串拼接、坐标跨行、dollar-slashy、`exclude` 反用、`sourceSets 'src/main/kotlin'`）可以构造出
    逐行文本扫描看不见的坐标。权威证明是 stage 3 + stage 3b，二者读的是构建真正产生的东西。
  - `:tree-layout` 仍无生产源码，纯净性扫描在该模块上仍是空跑（会打印 WARNING）；`:core` 已由
    T-003/T-021 填入 17 个类型，对其已是真实强制。
  - 本任务未做设备侧验证（用户 2026-09-21 决定：模拟与安装实测由用户自行进行）；
    `artifacts/evidence/T-002-phone-first-screen.png`（截图）与 `artifacts/evidence/T-002-device-evidence.txt`（冒烟输出）为 T-002 阶段的设备证据，其后代码已变化；当前 70 966 B / 3 dex 产物**未做**设备验证。
- Risks: 依赖树漂移（任何一次依赖升级都可能引入 Kotlin，必须重跑守门与 APK dex 扫描）。


### 第七轮独立复核结论（verifier，2026-09-21；本轮不使用模拟器）

- Verdict: **FAIL（记录层；工程实体层未发现回归）**。状态保持 `verifying`，不置 `verified`。
- 本轮亲自复现（证据文件均在 `artifacts/evidence/`）：
  - `powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\no-kotlin-check.ps1`（仓库根执行）→ `VERDICT: PASS (13 OK line(s))`、exit 0（`VERIFY-T002-guard.txt`）。
  - 守门身份：692 行 / SHA-256 `5F3FDB52F9EF8E6F0B20D016065A06F4A925F18353B5DE1AFC28D6277C7A3C32`，与本文件记录逐字一致（`VERIFY-T002-verify-readback.txt`）。
  - `cd android; .\gradlew.bat :app:assembleDebug :app:lintDebug` → `BUILD SUCCESSFUL`、exit 0；lint 0 error / 12 warning（`VERIFY-T002-assemble-lint.txt`）。
  - Gradle 构建配置逐文件回读相加 = **599 行 / 8 个 `*.gradle`**，与本文件一致（`VERIFY-T002-gradle-size.txt`）。
- 未通过项（两条，均无涉生产代码）：
  1. `android/scripts/dev/guard-selftest.ps1` 现为 **570 行**（mtime 12:38:05），本文件 Handoff 写 **467 行**；被引用的 `artifacts/evidence/T-002-guard-selftest.txt` 捕获于 **12:20:25**，早于脚本最后一次修改，且该文件头只记录 Guard/Library 的 SHA-256、**不记录自检脚本自身**，故无法把这份证据绑定到当前脚本。修法：改完脚本后重录该证据，并把 467 改为实测行数。
  2. 完整模式自检独立重跑 **2 次**均为 `OVERALL = SELFTEST FAIL (1 mismatch(es))`，唯一红点是 CASE 0（`untouched copy => PASS`，实测 1）；其余 **39/40** 条断言通过。根因已定位并实测：`New-GuardCopy` 刻意继承 `build/`，在实现方并发写 `:data`/`:app` 产物时副本继承到撕裂的中间产物（`D8: Class BranchCreation$Refusal requires its nest host BranchCreation`），副本自身的 Gradle 构建因此失败；同一脚本在**安静时刻新建的副本**上 `verifyNoKotlinDex: PASS`、exit 0。属并发窗口效应，不是检测分支失效，但"完整模式 40 例全 PASS"目前**无法复现**。证据：`VERIFY-T002-guard-selftest.txt`、`VERIFY-T002-guard-selftest-run2.txt`、`VERIFY-scratch-copy-build.txt`、`VERIFY-scratch-copy-run3.txt`。
- 验收条件 3（启动初始 Activity、首屏正常渲染且无启动崩溃）：**deferred — emulator not used this round (user instruction 2026-09-21)**。本轮不使用模拟器，该条既不判 PASS 也不判 FAIL，**仍未决**（先前 `T-002-device-evidence.txt` 对应的是已变化的 15 KB 骨架 APK，不能代表当前产物）。
- 翻转条件：① 重录自检证据（文件头含自检脚本自身 SHA-256 与行数）并把 467 改为实测值；② 在无并发构建的窗口重跑自检取得全 PASS；③ 验收条件 3 留待后续设备轮次。
### 第八轮：记录修复轮（record repair，2026-09-21 下；本轮不使用模拟器）

> 本节由**记录修复 lane**（与实现同方，**不是**独立 verifier）填写：只修记录、引用与证据重录，不改生产代码、不改守门脚本与自检脚本。
> 上方「第七轮独立复核结论」原样保留，本节是回应而不是覆盖。

第七轮列出的两条未通过项与一条验收事项，逐条处置：

| 第七轮发现 | 处置 | 修复后实测（命令 + 读数位置） |
| :--- | :--- | :--- |
| ① `guard-selftest.ps1` 现为 570 行而本文件写 467 行；被引证据捕获于 12:20:25、早于脚本最后一次修改，且该文件头不记录自检脚本自身 | 把 467 一律改为实测 **570**（并更正 SHA-256）；**以完整模式重录** `artifacts/evidence/T-002-guard-selftest.txt`，文件头新增「自检脚本自身的 SHA-256 + 行数 + mtime」 | `(Get-Content android\scripts\dev\guard-selftest.ps1).Count` → **570**；`(Get-FileHash -Algorithm SHA256 android\scripts\dev\guard-selftest.ps1).Hash` → `9D680E26CCF291D8CAB5B818B6851A9B6B21EDDB21C7826F1E5806388FA98828`；证据文件第 14–15 行 |
| ② 完整模式自检此前两次独立重跑都是 `39/40`（唯一红点 CASE 0：副本继承 `build/` 后被并发构建撕成不一致） | 在**无并发构建**的窗口重跑：第一次因本 lane 的环境问题（本会话的 PowerShell 7 把 `PSModulePath` 泄漏进子进程 PS 5.1，遮蔽 `Microsoft.PowerShell.Utility`，`Get-FileHash` 不可用）沿自检**自身的 `INCONCLUSIVE` 通道**退出 3，属 harness 中止而非用例不匹配；修正模块路径后**第二次取得完整模式 40/40 PASS** | `artifacts/evidence/T-002-guard-selftest.txt` 第 158 行 `OVERALL = SELFTEST PASS (40 case(s))`、第 159 行 `#### RUN B EXITCODE=0` |
| ③ 验收条件 3（首屏渲染无崩溃）需要 deferred 标注 | 在该验收条件行内直接写入 `deferred — emulator not used this round (user instruction 2026-09-21)`，既不判 PASS 也不判 FAIL | 本文件 Acceptance criteria 第 3 条 |
| ④（本节自查）Gradle 构建配置本文件写 **599 行**，记录修复轮再测为 **606 行** | 把 Handoff 与相关表格更新为 606，并说明差值来源（不把它当冻结数字） | `artifacts/evidence/T-002-gradle-size-2026-09-21-repair.txt` 末行 `files=8 total=606`；`android/app/build.gradle` mtime 21:36:33（leader 的 signingConfigs 顺序修复，+17/−10） |

- **CASE 0 的并发敏感性没有被消除，只是被绕开**：本节的重跑取自一个没有其它 lane 写 `build/` 的窗口。`guard-selftest.ps1` 的 `New-GuardCopy` **刻意保留 `build/`** 以便 Gradle 增量；并发构建时副本可能继承到撕裂的中间产物（第七轮实测 `D8: Class BranchCreation$Refusal requires its nest host BranchCreation`），CASE 0 因此会红。这是**自检脚手架在并发下的已知脆弱点**（`AGENTS.md` 失败案例库有同类条目），本任务只记录、**不改脚本**——改自检脚本超出记录修复的范围。
- 本轮**未**执行任何设备命令（无 `connectedDebugAndroidTest`、无 `adb`、无模拟器）。
### 第九轮独立复核结论（verifier #2，第四轮；2026-09-21；本轮不使用模拟器）

> 本轮只针对**第八轮：记录修复轮**的四项处置做独立复核。verifier 只读：未改动任何生产代码、守门脚本、自检脚本或他人证据。
> 自产证据：`VERIFY4-T002-guard.txt`、`VERIFY4-T002-assemble-lint.txt`、`VERIFY5-jvm-suites.txt`、`VERIFY5-androidtest-javadoc-depdirection.txt`。

- Verdict: **PARTIAL**（记录层四项处置已逐条复核为真；工程实体层本轮未发现回归；**验收条件 3 是设备判据，按用户 2026-09-21 指令 deferred，故本任务不得闭合**）。
- 第八轮四项处置的独立复核（命令 + 读数位置）：

| 第八轮处置 | 独立复核结果（verifier 亲自读回） |
| :--- | :--- |
| ① `guard-selftest.ps1` = 570 行 / 新 SHA-256；证据头新增自检脚本自身指纹 | **为真**。`(Get-Content android\scripts\dev\guard-selftest.ps1).Count` → **570**；`(Get-FileHash -Algorithm SHA256 …).Hash` → `9D680E26CCF291D8CAB5B818B6851A9B6B21EDDB21C7826F1E5806388FA98828`，与 `T-002-guard-selftest.txt` 第 14–17 行逐字一致 |
| ② 完整模式 40/40 | **为真**。`T-002-guard-selftest.txt:158` = `OVERALL = SELFTEST PASS (40 case(s))`、`:159` = `#### RUN B EXITCODE=0`；RUN A（INCONCLUSIVE，exit 3，PSModulePath 环境中止）原样保留，未被冒充为用例不匹配 |
| ③ 验收条件 3 加 deferred 标注 | **为真**。本文件第 84 行含 `deferred — emulator not used this round (user instruction 2026-09-21)`，并附「15 KB / 2 dex 骨架 APK」的证据范围注记 |
| ④ Gradle 配置 599 → 606 | **为真**。verifier 逐文件回读 39+108+121+27+40+219+25+27 = **606**（8 个 `*.gradle`），与 `T-002-gradle-size-2026-09-21-repair.txt` 末行 `files=8 total=606` 及本文件 Handoff 一致 |

- 本轮亲自复现（自产证据）：
  - `powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\no-kotlin-check.ps1`（仓库根）→ `VERDICT: PASS (13 OK line(s))`、exit 0；`vacuous` 命中 **0**（`VERIFY4-T002-guard.txt`）。
  - `cd android; .\gradlew.bat :app:assembleDebug :app:lintDebug` → `BUILD SUCCESSFUL`、exit 0；lint 报告回读 **0 error / 18 warning**（`VERIFY4-T002-assemble-lint.txt`）。
  - `verifyDependencyDirection` → `PASS (downward-only module graph + pure-JVM module purity)`，**不再打印任何 `vacuous` WARNING**（`VERIFY5-androidtest-javadoc-depdirection.txt:404`）。
- **基线漂移（引用方须注意，非本任务新缺陷）**：本文件第 122 行「本轮实测 11（warning）」与 T-003/T-021 引用的「lint 0 error / 11 warning」基线**均已过期**。原因是 `MainActivity.java:76` 的 `GestureBackNavigation` lint 错误已被 T-012 lane 修好（该文件现只在第 110 行 javadoc 里提到 `onBackPressed`），此后 warning 数为 18。
- **未决（阻塞闭合）**：验收条件 3「启动初始 Activity、首屏正常渲染且无启动崩溃」= `deferred — emulator not used this round (user instruction 2026-09-21)`。既不判 PASS 也不判 FAIL。**要让本任务闭合，用户须显式豁免该条，或另开一次设备轮次。**
- 规模：`estimated_production_lines: 160`。记录中的 **606 行是 Gradle 构建配置（开发脚手架，非生产代码）**，不计入行数预算，故本任务**不在规模越线清单内**。
- RED/GREEN 键卫生：RED `Exit code: 1`、GREEN `Exit code: 0`，两条 `Command` 逐字节相同（`powershell -NoProfile -ExecutionPolicy Bypass -File android\scripts\no-kotlin-check.ps1`），符合契约。
- 残留（不阻塞，如实记录）：① 第 125 行与 `T-002-runtime-classpath*.txt` 的产物字节数（15 140 B / 2 dex）对应 T-002 阶段旧骨架产物，当前产物更大（本文件第 85 行自述为 70 966 B / 3 dex），引用时不得当作当前值；② 第 318 行（第七轮小节）仍写 599，属历史记录，第 336/344 行已更正为 606。

## Handoff notes

- Completed: `android/` 五模块骨架、Gradle wrapper、Java-only 守门脚本（**第六轮后：692 行 / SHA-256 `5F3FDB52F9EF8E6F0B20D016065A06F4A925F18353B5DE1AFC28D6277C7A3C32`**）、依赖方向守门任务、dex 级 Java-only 守门任务、守门双向自检探针（`scripts/dev/guard-selftest.ps1`，**570 行**）、共享证据完整性库（`scripts/lib/evidence-integrity.ps1`，169 行）、`android/README.md`。
- 变更规模（第六轮守门修复后重新点过，全部为实测回读值）：**生产代码 175 行**（Java 24 + res XML 123 + Manifest 28）；测试 52 行；
  Gradle 构建配置 **606 行 / 8 个 `*.gradle` 文件**（**记录修复轮再测**：逐文件回读 39+108+121+27+40+219+25+27 = 606，`artifacts/evidence/T-002-gradle-size-2026-09-21-repair.txt`；第六轮 verifier 于 21:21 读数为 `599 total`，此后 leader 于 21:36 调整 `android/app/build.gradle` 的 `signingConfigs` 顺序（+17/−10）使总数 599→606。**文件数 `8` 不变**。旧值 `574 行 / 10 个文件` 的文件数已由第六轮 F4 证伪）；
  守门脚本 692 行（SHA-256 `5F3FDB52…`）；共享证据库 `scripts/lib/evidence-integrity.ps1` 169 行；自检探针 **570 行**（三者均属开发脚手架，不计入生产代码）。
- In progress: 无。第五轮 6 项（#21 注释降级可伪造、#22 依赖段漏 `compileOnly`、#23 APK 段漏扫、#24 证据过期且探针未落盘、#25/#26 自述不实）已全部修复并刷新证据，等待第六轮独立 verifier + `android-no-kotlin-guard` 判定。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 完成后解锁 T-003（`:core` 领域模型，已由后续任务推进）、T-004（SQLite）、T-005（Keystore）、T-006（DeepSeek 网关）、T-016（树布局）。
- Watch-outs:
  - 不要使用 Compose、Kotlin DSL 插件或 KTX。
  - 不要升级 `androidx.test:monitor` 到 1.6.1+，否则设备测试会在运行时因缺失 Kotlin 运行时崩溃（POM 干净，只有 dex 级校验能发现）。
  - 所有 instrumentation 测试写在 `:app` 的 androidTest 源集，其它模块不要声明 androidTest 依赖。
  - 修改守门脚本时**不得**用 `$ErrorActionPreference='Stop'` 包裹外部进程调用；必须走 `Invoke-NativeCaptured`，否则 javac 的 stderr 会让守门假失败。
  - 修改守门脚本时**不得**用 `-contains` 判字符集合成员（PowerShell 对字符串是集合包含语义，恒 False）；必须用 `.IndexOf()`。新增任何检测分支时，必须在 `android/scripts/dev/guard-selftest.ps1` 里补一条「注入必须被拦住」用例——**探针是脚本，不是一次性命令**（第 24 项就是这样丢的）。
  - 守门脚本里**不得再引入任何手写清单**（写死的 classpath 名、写死的产物目录都曾成为盲区）。要按模式识别，并**断言匹配集非空**；匹配集为空必须判 FAIL，绝不能退化成「什么都没验证却 PASS」。
  - 不得把解析器的输出同时当作判据与豁免依据（第 21 项：让词法器自证清白，结果它可以把真实依赖藏成注释）。需要豁免时，豁免依据必须来自**不共享该解析器状态**的另一条判据。
  - 用 `-f` 格式化时不要内联进 `"a" + "b" -f args`：`-f` 比 `+` 结合更紧，会只格式化后半段，前半段保留 `{0}`/`{1}` 字面量，证据行静默失去它要证明的数字（已由自检 CASE 9 锁住，连这个运算符事实本身也被断言）。改用 `$(...)` 插值。
  - 证据文件不要用 `>>` 重定向原生命令输出（PS 5.1 写 UTF-16LE，评审侧会看到二进制）；用 `2>&1 | Out-File -Encoding utf8 -Append`，并把长任务写成**流式逐行输出**，这样超时中断也不会丢掉全部证据。
  - `:tree-layout` 目前在纯净性扫描上仍是空跑（会打印 WARNING）；`:core` 已由 T-003/T-021 填入代码，该模块的守门已真正生效（`:tree-layout` 待 T-016）。
  - `local.properties` 的 `sdk.dir` 必须保持完全转义形式 `D\:\\Environment\\Android\\SDK`。
