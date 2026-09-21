# T-002 第六轮独立复核报告

- 任务：`.trae/tasks/T-2026-09-20-bootstrap-java-android-modules-002.md`（T-2026-09-20-002，Android 五模块骨架 + Java-only 硬约束）
- 复核者：独立 verifier（第 6 轮；不复述交付方自述，不修改任何生产代码/测试/任务文件）
- 复核日期：2026-09-21
- 工作目录：`D:\Project\Android\Treelearn`；仓库 HEAD `897873f`
- 环境：`JAVA_HOME=D:\Environment\Jdk\jdk21`，`JAVA_TOOL_OPTIONS=-Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8`
- 设备侧：按用户 2026-09-21 决定**暂停**，本报告不含任何设备行为结论（见 §4 Gaps）

---

## 1. Verdict

**FAIL**

工程实体本身**已通过独立验证**：五模块可配置、`:app:assembleDebug`/`:app:lintDebug`/`:core:test` 全部 exit 0、
Java-only 约束在**字节码层**（三个 APK 的 dex 字符串表，我自己的扫描方法）成立、历轮五轮修复的检测分支
（CASE 1–9、fail-closed 措施、按模式选配置、APK 递归发现、`-Skip*` 只能 PARTIAL）**全部复现有效，未发现回归**。

判定 FAIL 的阻塞面**不是代码或守门能力**，而是**证据文件与任务自述不一致**与**一处守门静默退化路径**：

1. 任务文件与 `android/README.md` 都把 `artifacts/evidence/T-002-guard-selftest.txt` 当作「完整模式 25 例全 PASS、
   含 CASE 7 / CASE 8 两个真实 Gradle 用例」的证据，而该文件自身写的是 `-Fast` 运行、`CASE 7-8 skipped`、
   `SELFTEST PASS (23 case(s))` —— 它**没有**这两个用例的任何输出（发现 F1）。这正是本仓库已登记两次的
   「自述与证据不同源 / 证据过期」缺陷类在第 5 轮修复后**再次复发**。
2. 同一证据文件缺少 AGENTS.md 要求的头部（生成时间、精确命令、被核脚本 SHA-256 与行数），
   而守门脚本本轮已是第 5 个版本，因此无法判定该证据出自哪个版本（F2）。
3. 守门 stage 3b 只看 `gradlew verifyNoKotlinDex` 的**退出码**，不断言它真的打印了判据行；
   我在仓库外副本上实测可以让它**打印 PASS 且 exit 0，而 APK 段零证据行、shipped 路径上放着含
   `Lkotlin/Metadata;` 的 APK**（F3）。这与守门自己在 stage 3 采用的「匹配集为空即 FAIL」不对称。

其余为数字/路径类自述失真（F4–F9）与两处低级别观察（F10–F11、F13）。
**这些全部是「刷新证据 + 改文档数字」即可关闭的机械修复；不需要改任何生产代码或守门逻辑。**

---

## 2. 逐项核实

### V1 完整 Java-only 守门（真实仓库，我自己跑）

```
powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1 -AndroidRoot android
```

观察输出（节选，与 `artifacts/evidence/T-002-no-kotlin-guard.txt` 逐行一致）：

```
OK   no .kt / .kts / .gradle.kts source files
OK   scanned 11 Gradle script/catalog file(s), comments excluded
OK   raw-text dependency scan completed (lexer-independent)
OK   lexer trust check completed (fail-closed on desync)
OK   no Kotlin coordinates on 53 variant classpath configuration(s) across :app, :core, :data, :ai, :tree-layout
     (build tooling configurations excluded, 4201 report lines)
OK   excluded 36 build-tooling configuration(s)
OK   verifyNoKotlinDex: PASS (3 APK(s) inspected across app/build/outputs/apk, no Kotlin runtime bytecode)
VERDICT: PASS
GUARD EXITCODE=0
```

结论：**通过**。`53 / 4201 / 36` 三个数字与任务文件第 196 行、README 第 109–110 行**完全一致**，可复现。
唯一差异是 APK 字节数（我这次 debug 70966 B / release 37788 B，证据文件 70742 / 37596）——README 第 58–59 行
已声明「字节数每次构建会变」，不算失真。

### V2 守门脚本身份（SHA-256 / 行数）

```
sha256sum android/scripts/no-kotlin-check.ps1          -> b9d342e846255c200830cc1e43174986bfbbbc7d4126f55d279dd18c4958c2f9
wc -l android/scripts/no-kotlin-check.ps1              -> 577
sha256sum android/scripts/dev/guard-selftest.ps1       -> 3ca0659def1ab50fe1d87836acd78eb3d83d4c9b039dc3781c822fa861393992
wc -l android/scripts/dev/guard-selftest.ps1           -> 317
```

结论：守门脚本 `577 行 / B9D342E8…` 与任务第 204、265 行**一致**（通过）。
但自检探针实测 **317 行**，任务第 268 行写「自检探针 **261 行**」→ **F5**。
探针文件 mtime `11:12:51` 早于任务文件 mtime `11:27:13`，即写任务时该数字已不对。

### V3 守门编码前提（AGENTS「.ps1 必须 UTF-8 with BOM 或纯 ASCII」）

```
python -c "print('non-ascii bytes:', sum(1 for x in open('android/scripts/no-kotlin-check.ps1','rb').read() if x>127))"
  -> non-ascii bytes: 0 ; BOM: b'#re'
同法 guard-selftest.ps1 -> non-ascii bytes: 0 ; BOM: b'#re'
```

结论：**通过**。两个脚本都是**刻意纯 ASCII 且无 BOM**，与脚本头 `NOTE:` 声明的做法一致，
在 PS 5.1 的 GBK 解码陷阱下安全。

### V4 守门双向自检 `-Fast`（23 例）

```
powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/dev/guard-selftest.ps1 -Fast
```

观察输出：23 例全 `OK`（CASE 0–6 + CASE 9），`OVERALL = SELFTEST PASS (23 case(s))`，`SELFTEST EXITCODE=0`。

结论：**通过**，与 `artifacts/evidence/T-002-guard-selftest.txt` 内容一致——但这也正是 F1 的证据：
该文件就是这次 `-Fast` 运行，包含 `CASE 7-8 skipped (-Fast: they need a real Gradle run)`。

### V5 守门双向自检完整模式（25 例，独立复现）

```
powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/dev/guard-selftest.ps1     # 约 5 分钟
```

观察输出（关键三行）：

```
  OK       GString compileOnly kotlin => FAIL (stage 3) (expected 1, got 1)
  OK       Kotlin-carrying APK in app/build/outputs/apk/staging => FAIL (expected 1, got 1)
  OK       untouched copy => PASS (expected 0, got 0)
OVERALL = SELFTEST PASS (25 case(s))
SELFTEST EXITCODE=0
```

结论：**结论本身通过**（第 5 轮 #22 / #23 的两个修复确实生效），但**现有证据文件记录的不是这一次运行**（F1）。
补充实测事实：该完整模式首次运行时被 2 分钟级的执行上限**中断**（我遇到一次，输出停在 CASE 8 标题、无 SUMMARY），
第二次用更长超时跑完。这解释了「为什么落盘的是 `-Fast`」——也说明这条证据如果不显式记录命令与耗时，极易被
下一代评审误读为完整模式证据。

### V6 `-SkipDependencyStage` / `-SkipApkStage` 永不 PASS

```
powershell ... -File android/scripts/no-kotlin-check.ps1 -AndroidRoot android -SkipDependencyStage
  -> VERDICT: PARTIAL (skipped by request: dependency report)      EXIT=2
powershell ... -File android/scripts/no-kotlin-check.ps1 -AndroidRoot android -SkipApkStage
  -> SKIP verifyNoKotlinDex requested (-SkipApkStage); the verdict will be PARTIAL, never PASS
     VERDICT: PARTIAL (skipped by request: APK dex scan)           EXIT=2
```

结论：**通过**。两个开关都只产生 exit 2 / PARTIAL；`no-kotlin-check.ps1:568` 的
`if ($skipStage -or $SkipApkStage.IsPresent)` 覆盖了两条路径，无 PASS 逃逸。

### V7 stage 3 的「匹配集非空」断言确实会响亮失败（fail-closed 实证）

我在仓库外副本把 `gradlew.bat` 换成一个**不回显任何配置头**的桩后运行守门，得到：

```
FAIL No variant classpath configuration matched '(Compile|Runtime|AnnotationProcessor)Classpath$' across
     :app, :core, :data, :ai, :tree-layout; the classpath pattern is stale and the dependency stage verified nothing
VERDICT: FAIL (1 problem(s))     GUARD EXITCODE=1
```

结论：**通过**。模式过期时守门**不会**退化成「什么都没验证却 PASS」，而是 FAIL——第 5 轮 #22 的
「断言匹配集非空」在真实执行中可被观测到。

### V8 依赖层：我用自己的方法独立核对（不同实现，不引用交付方证据）

```
./gradlew -q :app:dependencies > %TEMP%\r6-app-deps.txt      # 1420 行，exit 0
# 我自己的解析器：按 ^<name>\s+-\s 切配置，只对 +--- / \--- 树行匹配 kotlin 坐标
```

我的观察结果：

```
my own count: variant classpaths matched = 15      （:app 单模块）
my own count: build-tooling configs   = 12
configs with kotlin hits = 12
    _internal-unified-test-platform-android-device-provider-ddmlib -> 11
    _internal-unified-test-platform-android-test-plugin-host-emulator-control -> 16
    ... （其余 10 个 _internal-unified-test-platform-* 同上）
any hit on a matched variant classpath: []
```

结论：**通过**。`grep` 原始报告会看到 **273 行** `org.jetbrains.kotlin:*`，但它们**全部**落在
`_internal-unified-test-platform-*` 这 12 个构建工具配置里（AGP/Gradle 自身是 Kotlin 写的），
**任一变体 classpath 上零命中**——这独立证实了守门排除策略的必要性与正确性，也证实
「按模式识别 + 排除构建工具配置」不是作弊。

### V9 Java-only 最终依据：字节码 / dex 字符串表（我自己的扫描方法）

```
python: zipfile 读取 android/app/build/outputs/apk/**/*.apk，逐个 dex 拼接后统计 needle
```

```
APK: .../androidTest/debug/app-debug-androidTest.apk  749885 bytes, 3 dex
    needle b'Lkotlin/Metadata;' count 1 ;  b'kotlin/jvm/internal' count 0
APK: .../debug/app-debug.apk                          70966 bytes, 3 dex
    （无任何 kotlin needle）
APK: .../release/app-release-unsigned.apk             37788 bytes, 1 dex
    （无任何 kotlin needle）
```

再把 androidTest dex 里所有含 `kotlin` 的可打印串全量列出：

```
classes.dex  ['$Lkotlin/annotation/AnnotationTarget;', "'Lkotlin/annotation/AnnotationRetention;",
              'Lkotlin/Deprecated;', 'Lkotlin/Metadata;', 'Lkotlin/ReplaceWith;',
              'Lkotlin/annotation/Retention;', 'Lkotlin/annotation/Target;', 'Lkotlin/reflect/KClass;']
classes2.dex []   classes3.dex []
```

结论：**通过**（这是本报告最关键的正向结论）。debug/release 两个 shipped APK **零 Kotlin 字节码**；
androidTest APK 只有注解类引用、**没有 `kotlin/jvm/internal`**（即无运行时链接），与「已登记例外」的
实质判断一致——这正是 `monitor:1.6.1` 那类「POM 干净但 dex 脏」的唯一可靠判据，且是我自己扫出来的。

### V10 「已登记例外」的归属与版本对照（README 第 68–81 行）

```
python: 解压 ~/.gradle/caches/.../androidx.test/monitor/{1.6.0,1.6.1}/*.aar 内的 classes.jar
```

```
monitor-1.6.0.aar : total classes 94 ; kotlin-compiled 0
monitor-1.6.1.aar : total classes 97 ; kotlin-compiled 2
                     androidx/test/platform/io/OutputDirCalculator.class
                     androidx/test/platform/io/OutputDirCalculator$outputDir$2.class
```

结论：**部分通过**。README 的「1.6.1：97 类 / 2 个；1.6.0：94 类 / 0 个」与「`OutputDirCalculator` 是 Kotlin
编译产物」经我独立解包**完全属实**。README 里的 `1.7.2：95 类 / 4 个 Kotlin 类` 在本机 Gradle 缓存中不存在
（未下载），本轮**无法验证**（记入 §4 Gaps，不计为失败）。

### V11 构建与模块配置（五个模块）

```
./gradlew --no-daemon --console=plain :app:assembleDebug :app:lintDebug          -> BUILD SUCCESSFUL, EXIT=0
./gradlew --no-daemon --console=plain verifyDependencyDirection :core:test       -> BUILD SUCCESSFUL, EXIT=0
./gradlew --no-daemon --console=plain :core:test --rerun-tasks                   -> BUILD SUCCESSFUL, EXIT=0
```

`:app:assembleDebug :app:lintDebug` 是任务第 82 行验收标准里的**原命令**，exit 0 → 该条验收标准**通过**。
五个模块的存在与可配置由 `verifyDependencyDirection`（对声明模块做 `project(path)` 解析）与
守门 stage 3（一次性对 `:app :core :data :ai :tree-layout` 取依赖报告，exit 0）共同证明。

### V12 lint 结果

```
python android/scripts/dev/lint_summary.py     （从仓库根运行，按脚本自身位置解析 => OK）
  total=11   by severity: {'Warning': 4}
grep -c "<issue" android/app/build/reports/lint-results-debug.xml        -> 12（含根节点匹配）
grep -o 'severity="[^"]*"' ... | sort | uniq -c                          -> 11 severity="Warning"
```

结论：**0 error / 11 warning**。任务第 120 行仍写「0 error（**13 条**全为 warning）」，与第 169 行（#17）的
处置「不再固化 warning 条数」以及实测 11 条**自相矛盾** → **F6**（硬事实 0 error 成立）。

### V13 `verifyDependencyDirection` 的真实输出与纯净性缺口

```
> Task :verifyDependencyDirection
verifyDependencyDirection: WARNING :tree-layout has no src/main/java sources yet, so its purity contract is
  vacuous until T-003 / T-016 land code
verifyDependencyDirection: PASS (downward-only module graph + pure-JVM module purity) ; purity NOT yet enforced for [:tree-layout] (no sources)
```

```
find android/core/src/main/java -name '*.java' | wc -l      -> 17
ls android/tree-layout/src                                  -> No such file or directory
```

结论：**通过**。README 第 34–38 行与任务第 200 行（#26）的描述与实测**逐字一致**：`:core` 的纯净性扫描
已是真实强制（17 个类型），只剩 `:tree-layout` 空跑并打印真实 WARNING。

### V14 `:core` 类型数、测试行数、生产行数

```
find core/src/main/java -name '*.java' -not -path '*/build/*' | wc -l          -> 17
find app/src/main -name '*.java' | xargs wc -l                                -> 24  (MainActivity.java)
find app/src/main/res -name '*.xml' | xargs wc -l | tail -1                   -> 123
find app/src/main -name AndroidManifest.xml | xargs wc -l                     -> 28
find app/src/androidTest -name '*.java' | xargs wc -l                         -> 52
```

结论：**通过**。「生产代码 175 行（Java 24 + res XML 123 + Manifest 28）」= 175 ✓ 可完全复现；
「测试 52 行」✓；「`:core` 17 个类型（T-021 后）」✓；`:tree-layout` 无源码 ✓。

### V15 Gradle 构建配置行数/文件数

```
cd android && find . -name '*.gradle' -not -path '*/build/*' | wc -l      -> 9     （8 个文件 + ./.gradle 目录）
              find . -name '*.gradle' -not -path '*/build/*' -type f | wc -l -> 8
              find . -name '*.gradle' -not -path '*/build/*' | xargs wc -l | tail -1 -> 574 total
```

结论：**574 行可复现 ✓**，但**文件数是 8 不是 10** → **F4**。
（`xargs wc -l` 的输出共 10 行 = 9 个条目 + `total` 行；「10 个文件」疑似把 `total` 当成一个文件。）

### V16 守门 stage 2 的扫描面（11 个文件）

```
find . -type f \( -name '*.gradle' -o -name '*.properties' -o -name '*.toml' \) \
     -not -path '*/build/*' -not -path './.gradle/*' | wc -l      -> 11
（8 个 .gradle + gradle.properties + gradle/wrapper/gradle-wrapper.properties + local.properties）
```

结论：**通过**，与守门打印的 `scanned 11 Gradle script/catalog file(s)` 精确一致。
注意：本仓库**没有** `gradle/libs.versions.toml`，所以守卫的 `.toml` 分支在本仓库中处于未覆盖状态——
但这只是覆盖缺口，不是缺陷（stage 3 仍会拦下经目录引入的 Kotlin 坐标）。

### V17 stage 2 静态层的绕过面（我的对抗性尝试）

- 注释里的坐标：`guard-selftest.ps1` CASE 4 `legitimate line comment` 必不误报 ✓（我跑过，实测 `expected 2, got 2`）。
- 块注释里的坐标：CASE 6 要求必须 FAIL ✓（第 5 轮 #21 的有意 fail-closed 方向）。
- slashy / dollar-slashy 令词法器失步：CASE 1/2/5/6 ✓。
- `compileOnly` 上的 GString 坐标（静态层原理上不可见）：CASE 7 由 **stage 3** 拦下 ✓（我实测 `expected 1, got 1`）。
- `staging/` 等非标准产物目录的含 Kotlin APK：CASE 8 由 **stage 3b** 拦下 ✓。
- 新构建类型：`stagingCompileClasspath` 一类配置名天然匹配 `(Compile|Runtime|AnnotationProcessor)Classpath$`，
  不需要改守门代码；模式过期时 V7 会响亮失败。

结论：**通过**（未发现新的绕过路径）。`SCOPE HONESTY` 段的自我定位（静态层只是预筛、权威证明在 stage 3/3b）
与实测行为一致。

### V18 任务文件机械契约

```
"C:\Users\23745\.workbuddy\binaries\python\versions\3.13.12\python.exe" .trae/scripts/validate_tasks.py
```

```
PASS T-2026-09-20-bootstrap-java-android-modules-002.md
SUMMARY files=21 errors=0 warnings=1     (warning 属 T-021，不属 T-002)
```

结论：**通过**（0 error）。任务状态 `verifying` + `requirements_confirmed: true` + `template_version: 2`
符合 `harness-tasks.md` 的状态机；文件名/`id`/序号自洽；`estimated_production_lines: 160` 对实测 175 行
仍在 200 行软上限内。

### V19 设备证据（只做「文件是否存在 + 数字是否自洽 + 是否已被取代」，不读作设备通过）

```
artifacts/evidence/T-002-device-evidence.txt   （2026-09-20T17:01:11Z = 本地 2026-09-21 01:01）
  sdk=36 abi=x86_64 / versionCode=1 minSdk=28 targetSdk=36 / versionName=1.0
  LaunchState: COLD / TotalTime: 827 / topResumedActivity=...MainActivity / FATAL EXCEPTION count 0
artifacts/evidence/T-002-phone-first-screen.png   存在（55907 B）
android/app/build/reports/androidTests/connected/debug/index.html  -> counter 1 tests / 0 failures
```

结论：设备证据文件**存在且自洽**，与任务第 124 行的 `827ms / COLD / 0 FATAL / 1 test 0 failure` 一致；
app 侧源码 mtime（00:35–00:42）**早于**该证据（01:01），所以「T-002 阶段设备冒烟」这一行本身可信。
**但**该证据对应的是 15 KB / 2 dex 的 debug APK（任务第 123 行、`T-002-guard-stderr-regression.txt` 第 16–17 行），
而**当前** debug APK 是 70 966 B / 3 dex，`:core` 下 **28 个 src 文件**的 mtime 晚于该证据
（`find core/src -type f -newermt "2026-09-21 01:01" | wc -l` → 28）。
即：**当前产物从未在设备上启动过**。按用户指令不因此判 FAIL，但见 §4 Gaps 与 F13；
本报告**不得**被读作「设备行为已通过」。

### V20 守门自身可信度：stage 3b 的「证据存在性」缺失（对抗性发现）

在仓库外副本 `%TEMP%\r6-apkstage-probe\android` 上：

1. 把 `gradlew.bat` 换成只回显一份**格式合法、171 行、零 Kotlin 坐标**的依赖报告、其余什么都不打印、`exit /b 0` 的桩；
2. 把含 `Lkotlin/Metadata;` 的 `app-debug-androidTest.apk` **复制成** `app/build/outputs/apk/debug/app-debug.apk`
   与 `app/build/outputs/apk/release/app-release-unsigned.apk`（**shipped 路径**），androidTest 路径也放一份；
3. 运行守门。

观察输出：

```
OK   no Kotlin coordinates on 50 variant classpath configuration(s) ... (build tooling configurations excluded, 171 report lines)
OK   excluded 0 build-tooling configuration(s)
== Built APK dex tables ==
OK   no built APK contains Kotlin runtime bytecode
== Result ==
VERDICT: PASS
GUARD EXITCODE=0
```

结论：**不通过（F3）**。APK 段**一行 `verifyNoKotlinDex:` 证据都没有**，守门却打印
`OK no built APK contains Kotlin runtime bytecode` 并给出 `VERDICT: PASS`——因为该分支只看子进程退出码
（`no-kotlin-check.ps1:544-550`），没有任何「证据行非空」断言，与 stage 3 的
`$reportLines -lt 50` / `$scannedConfigurations.Count -eq 0` 两条断言**不对称**。
（说明：本探针需要改动 `gradlew.bat` 才能触发，因此这不是「普通开发者会踩的坑」，
而是**静默退化**路径——一旦 Gradle 侧输出被抑制/改名/改写，守门会给出无证据的 PASS。
它与本仓库已登记的 `[guard] 匹配集为空必须判 FAIL` 规则冲突，属同类缺陷的未覆盖角落。）

---

## 3. 发现表

| 编号 | 级别 | 发现 | 为什么不满足契约（规则原文） | 建议处置 | 最小复现命令 |
| :-- | :-- | :-- | :-- | :-- | :-- |
| **F1** | **中（阻塞）** | 任务第 125、245–246 行与 `android/README.md:139` 都称 `artifacts/evidence/T-002-guard-selftest.txt` 是「完整模式 **25 例** 全 PASS，含 CASE 7（GString `compileOnly`）、CASE 8（`staging/` 下含 Kotlin APK）两个真实 Gradle 用例」；该文件**自身**写的是 `Command: … -Fast`、`CASE 7-8 skipped (-Fast: they need a real Gradle run)`、`OVERALL = SELFTEST PASS (23 case(s))` —— 它**没有任何** CASE 7/8 输出。第 5 轮 #24 修的就是「证据过期/探针未落盘」这一类，本轮**同类复发**，且被引用的正是它声称修好的那两个用例的唯一证据 | `harness-tasks.md:47`「执行记录必须展示…观察到的失败…相关回归证据」；`documentation.md`「项目 Harness 校验器在以下情况必须返回 FAIL：…与活动的生产者/消费者事实相矛盾的当前态主张」；`harness-verification.md`「缺失实质证据/文档影响记录视同未核对，不通过」；根 `AGENTS.md` 失败案例库「交付自述里的计数与证据文件不同源…证据文件必须自带命令行与退出码」 | 在**完整模式**下重跑一次自检（实测约 5 分钟，注意执行上限会截断，需用更长超时或流式落盘），把完整模式输出写回 `artifacts/evidence/T-002-guard-selftest.txt`（保留 `-Fast` 那次为独立文件或删掉），并让 README/任务只引用完整模式那份 | `grep -n "Command:\|CASE 7-8\|23 case" artifacts/evidence/T-002-guard-selftest.txt` 对比 `grep -n "25 例\|25 case" .trae/tasks/T-2026-09-20-bootstrap-java-android-modules-002.md android/README.md` |
| **F2** | **中** | 同一文件缺少 AGENTS 要求的头部：无 `Generated:` 时间、无被核脚本 `SHA-256`、无行数；只有一行手工拼的 `Command:`。守门脚本现已第 5 版（`B9D342E8…`），因此**无法判定该证据出自哪个版本**。相邻的 `T-002-no-kotlin-guard.txt` 反而有完整头部（Generated/Command/SHA-256/lines/EXITCODE） | 根 `AGENTS.md` 失败案例库「…并在证据文件头部记录生成时间、命令、被核脚本的 SHA-256 与行数」 | 给 `guard-selftest.ps1` 增加输出头部（脚本自身 `$PSScriptRoot` 定位的 `no-kotlin-check.ps1` 的 SHA-256 与行数、自身 SHA-256、生成时间、精确命令行），或在生成证据的封装命令里补上；把该要求写进脚本头 | `head -3 artifacts/evidence/T-002-guard-selftest.txt`（无 Generated / SHA-256 行）vs `head -6 artifacts/evidence/T-002-no-kotlin-guard.txt` |
| **F3** | **中** | 守门 stage 3b 只依据 `gradlew verifyNoKotlinDex` 的退出码判定，**不断言**子进程真的打印了判据行。实测可在「零 `verifyNoKotlinDex:` 输出 + shipped 路径存在含 `Lkotlin/Metadata;` 的 APK」下打印 `OK no built APK contains Kotlin runtime bytecode` 并 `VERDICT: PASS`（exit 0）。与 stage 3 的「匹配集为空即 FAIL」不对称 | 根 `AGENTS.md` 失败案例库 `[guard]`「按模式识别 + 断言匹配集非空；匹配集为空必须判 FAIL，绝不能退化成『什么都没验证却打印 PASS』」；任务第 279 行 watch-out 同款要求 | 在 `no-kotlin-check.ps1` stage 3b 增加证据存在性断言：至少 1 条 `^verifyNoKotlinDex:` 行且必须出现 `verifyNoKotlinDex: PASS (N APK(s)`，否则判 FAIL；并在 `guard-selftest.ps1` 补一条「子任务零输出必须 FAIL」的正向用例（连同 F12 一起做） | 见 §2 V20 的三步探针（仓库外副本；`%TEMP%\r6-apkstage-probe`） |
| **F4** | **轻** | 任务第 199、267 行「Gradle 构建配置 **574 行 / 10 个文件**」：行数 574 ✓，文件数**实测 8**。`find` 返回 9 个条目（8 文件 + `./.gradle` 目录），`xargs wc -l` 输出 10 行（9 条目 + `total`）→ 疑似把 `total` 行当成一个文件 | 根 `AGENTS.md` 失败案例库「任务文件里每个数字都要写成『从哪个文件的哪行得到』…并在**写完之后**用同一命令回读一次」 | 改为「574 行 / 8 个 `*.gradle` 文件（`… -type f \| wc -l`）」，并区分 `find` 条目数与文件数 | `cd android && find . -name '*.gradle' -not -path '*/build/*' -type f \| wc -l` → 8 |
| **F5** | **轻** | 任务第 268 行「自检探针 **261 行**」；实测 `android/scripts/dev/guard-selftest.ps1` = **317 行**（SHA-256 `3ca0659d…`）。文件 mtime 11:12:51 早于任务 mtime 11:27:13，即写任务时该数字已不对 | 同上（凭记忆写的数字） | 更正为 317 行并给出命令；或直接删掉该行（它不是契约数字） | `wc -l android/scripts/dev/guard-selftest.ps1` → 317 |
| **F6** | **轻** | 任务第 120 行回归表仍写 lint「0 error（**13 条**全为 warning）」，与第 169 行（#17）已决定的「不再固化 warning 条数」及实测 **11 条**矛盾——同一文件内自相矛盾 | `documentation.md`「与活动的生产者/消费者事实相矛盾的当前态主张」类；`harness-tasks.md:47` 要求执行记录反映真实观察结果 | 改为「0 error（warning 条数随 AGP/lint 版本漂移，不固化；本轮实测 11）」 | `python android/scripts/dev/lint_summary.py \| tail -3` → `total=11` |
| **F7** | **轻** | 任务第 108 行称「RED 阶段原始输出见该文件的**早期版本历史**」，但 `android/` 与 `artifacts/` **都未被 git 跟踪**，不存在任何版本历史；RED 原始输出在当前仓库中不可获取。这是一个**指向不存在物**的取证说明 | `harness-verification.md`「缺失实质证据…视同未核对」；`documentation.md` 要求文档主张由生产来源证明 | 直说「RED 原始输出无留存（`android/` 与 `artifacts/` 均未纳入 git，无版本历史）；`android/` 移除重跑的等价命令与断言见本节」——或把 RED 输出落盘成一个小的历史证据文件 | `git ls-files android \| wc -l` → 0；`git ls-files artifacts \| wc -l` → 0 |
| **F8** | **轻** | 任务第 260 行写 `assets/evidence/T-002-phone-first-screen.png`，实际路径是 `artifacts/evidence/…`（`assets/` 目录不存在）；同时设备冒烟那一行（第 124 行）没有引用真正的证据文件 `T-002-device-evidence.txt` | `documentation.md`「针对被取代短语/示例的反向查找」「最低限度的文档验证是…相对链接检查」 | 改路径为 `artifacts/evidence/T-002-phone-first-screen.png`，并在第 124 行的证据列补上 `T-002-device-evidence.txt` | `ls artifacts/evidence/T-002-phone-first-screen.png`（存在）/ `ls assets/evidence/`（不存在） |
| **F9** | **轻** | 任务第 73 行「Documentation surfaces checked: **根 README**、`android/README.md`、技术方案」——仓库根**没有** `README.md`，该面实际不存在 | `documentation.md`「空泛的『无需文档』断言无效」的反向要求：影响记录必须对应真实文档面 | 改为「仓库根无 `README.md`（已确认不存在，故无根入口需同步）；`android/README.md` 已更新」 | `ls README.md` → No such file or directory |
| **F10** | **低** | `android/README.md:62`「**各模块**用 `resolutionStrategy.force 'androidx.test:monitor:1.6.0'` 钉住该版本」——实际只有 `:app`、`:ai`、`:data` 三处，`:core`/`:tree-layout`（java-library）没有该语句 | `documentation.md`「当前态文档变更必须用生产来源证明，而非从另一份文档复制」 | 改为「`:app`/`:data`/`:ai`（三个含 Android 测试栈的模块）…；`:core`/`:tree-layout` 为纯 JVM 模块，无 Android 测试栈故不需要」 | `cd android && grep -rn "monitor" --include=*.gradle . \| grep -v /build/` |
| **F11** | **低** | 「已登记例外（唯一一条）」只列举 `Lkotlin/Metadata;`，而同一 dex 还引用 `Lkotlin/Deprecated;`、`Lkotlin/ReplaceWith;`、`Lkotlin/reflect/KClass;`、`Lkotlin/annotation/{Target,Retention,AnnotationTarget,AnnotationRetention}`。实质结论（无 `kotlin/jvm/internal`、设备不需运行时）**经我验证成立**，但例外范围描述不完整，审计者需自行再扫一遍才能确认例外边界 | `documentation.md`「可执行的示例…都是契约性文档」（此处 NOTICE 行是审计契约） | 让 `verifyNoKotlinDex` 在 NOTICE 行里列出该 APK 中**全部** `kotlin/` 描述符（或至少 `Lkotlin/*` 计数），README 同步为「所有 Kotlin 引用均为注解类，无 `kotlin/jvm/internal` 链接」 | §2 V9 的 dex 字符串提取脚本 |
| **F12** | **低（静态判读，未执行）** | `guard-selftest.ps1:266-270`：当 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` 不存在时，CASE 8 走 `Write-Host 'SKIP …'` 并把结果记为 `Ok = $true`，完整模式仍会报 `SELFTEST PASS (25 case(s))`。「跳过」被算成「通过」，与「探针必须双向真跑」的自我要求冲突 | 根 `AGENTS.md` 失败案例库「守门脚本的**每条检测分支**必须配一条『注入该违规必须被判 FAIL』的自检用例（正反双向自检）」——跳过即等于该分支未被验证 | 前置条件缺失时判 **FAIL**（或从用例总数中扣除并单独打印 `SKIPPED n`，且 SUMMARY 明确不是 PASS）；CASE 8 需要的 APK 由 selftest 自己先跑 `:app:assembleDebugAndroidTest` | `grep -n -A6 "sourceApk" android/scripts/dev/guard-selftest.ps1`（代码判读；本轮 androidTest APK 存在，故未触发） |
| **F13** | **低** | 验收标准第 84 行「启动初始 Activity，首屏正常渲染且无启动崩溃」被勾为 `[x]`，但其证据对应的是 15 KB / 2 dex 的旧 debug APK；当前 APK 为 70 966 B / 3 dex，且 `:core` 下 28 个 src 文件 mtime 晚于设备证据。勾选语义上等于「已由当前产物验证」 | `harness-verification.md`「独立核对验收标准，绝不从测试数量、构建成功或…推断完成」；`startup.md`「校验器不得从其它测试证据推断启动成功」 | 在第 84 行的勾选后加注「**证据对应 T-002 阶段 15 KB 骨架 APK；当前 70 KB 产物未做设备验证（用户 2026-09-21 暂停设备验证）**」，或把该条回退为未勾选并在 Gaps 中登记 | `find android/core/src -type f -newermt "2026-09-21 01:01" \| wc -l` → 28；证据里 `app debug APK (shipped artifact) = 15140 bytes, 2 dex` |

---

## 4. Gaps

1. **设备侧验证未做（用户 2026-09-21 决定暂停，由用户自行安装实测）**。本机 `adb` 不在 PATH，
   `ANDROID_HOME` 未设置，我**无法**做任何启动/首屏/崩溃验证。
   本报告**不得**被读作设备行为已通过。已有设备证据仅覆盖 T-002 阶段的 15 KB 骨架 APK（见 F13）。
2. **stage 2 静态层不完备且不被声称**（`SCOPE HONESTY`）：合法 Groovy 可构造出逐行文本扫描看不见的坐标。
   我本轮未再尝试新的文本技巧（历轮已证伪该方向）；权威证明仍是 stage 3 + stage 3b。
3. **`:tree-layout` 纯 JVM 纯净性扫描仍是空跑**（真实打印 WARNING，`purity NOT yet enforced for [:tree-layout]`），
   待 T-016 填码后才转为真实强制（任务与 README 均已登记）。
4. **`.toml` 分支未被覆盖**：本仓库不存在 `gradle/libs.versions.toml`，守门 stage 2 的 `.toml` 扫描路径
   在当前仓库中不可被观测（`guard-selftest.ps1` CASE 3 只覆盖 `gradle.properties` 与 `app/build.gradle`），
   属覆盖缺口。
5. **README 的 monitor 1.7.2 数据（95 类 / 4 个 Kotlin 类）不可验证**：该构件不在本机 Gradle 缓存中，
   我没有联网重新下载；1.6.0 / 1.6.1 的同类数据我已独立解包核实（见 V10）。
6. **`emulator-5554` 序列号不可从证据文件核对**：`T-002-device-evidence.txt` 只记录 `sdk=36 abi=x86_64
   model=sdk_gphone64_x86_64`，任务第 124 行写的 `emulator-5554` 来自技术方案文档，不在该证据文件内。
7. **F3 的完整闭环未做**：我只证明了「零证据行时守门仍 PASS」这一退化路径，没有去构造「真实 Gradle
   正常输出被判据行读取失败」的自然触发场景（例如输出流被 Gradle 配置改写）；该风险的现实概率未评估。
8. **F12 仅为代码判读**，我未删除 APK 实测跳过路径（避免动动构建产物）。

---

## 5. 结论与翻盘条件

### 结论

- **工程实体（可执行部分）已独立验证通过**：Java-only 在字节码层成立（三个 APK 的 dex 表零 Kotlin 运行时）、
  五模块可配置、`:app:assembleDebug`/`:app:lintDebug`/`:core:test`/`verifyDependencyDirection` 全部 exit 0、
  守门 `VERDICT: PASS` / exit 0、自检 `-Fast` 23/23 与完整 25/25 全 PASS、
  `-Skip*` 只能 PARTIAL、stage 3 与 stage 3b 的历轮修复（#21/#22/#23）**在真实执行中可观测且有效，未发现回归**。
  → **第 5 轮 #21–#26 的修复没有回归。**
- **但 task 不能推进到 `verified`**：其证据引用与若干当前态数字**与实测不符**（F1 为阻塞项），
  且守门自身存在一条**静默退化路径**（F3）。按 `documentation.md`（与活动事实矛盾的当前态主张须 FAIL）
  与 `harness-verification.md`（缺失实质证据视同未核对，不通过），本轮判 **FAIL**。

### 翻盘条件（全部为文档/证据刷新，不需要改生产代码或守门逻辑）

1. **[必须]** 以**完整模式**重跑 `guard-selftest.ps1`（`--` 不加 `-Fast`），把完整输出（含 `CASE 7`、`CASE 8`、
   `OVERALL = SELFTEST PASS (25 case(s))`、`SELFTEST EXITCODE=0`）写回 `artifacts/evidence/T-002-guard-selftest.txt`；
   头部必须有 `Generated:` / `Command:`（完整模式命令行）/ 被核脚本 `SHA-256` 与行数（F1 + F2）。
   若执行上限会截断，请改用流式落盘并分两次取证，不要退回 `-Fast` 后仍声称 25 例。
2. **[必须]** 在守门 stage 3b 增加证据存在性断言（≥1 条 `^verifyNoKotlinDex:` 行，且出现
   `verifyNoKotlinDex: PASS (N APK(s)`），并在 `guard-selftest.ps1` 补「子任务零输出 ⇒ 必须 FAIL」的用例；
   同时把 CASE 8 的「前置条件缺失」由 `Ok = $true` 改为 FAIL 或显式 SKIPPED（F3 + F12）。
3. **[必须]** 更正可复现性失真的数字/路径：构建配置文件数 8（F4）、探针行数 317（F5）、
   lint warning 11 且不再固化（F6）、`assets/` → `artifacts/` 并补 `T-002-device-evidence.txt` 引用（F8）、
   根 README 不存在的表述（F9）、RED 取证路径不存在的表述（F7）。
4. **[建议]** `android/README.md:62` 的「各模块」改为实际三个模块（F10）；NOTICE 行列举全部 `kotlin/` 描述符（F11）；
   第 84 行勾选项加注「证据对应旧 15 KB 骨架 APK，当前产物未做设备验证」（F13）。
5. **[必须]** 设备侧仍按用户决定暂停；恢复条件与 F13 的注记写进 Gaps 即可，不得据此声称设备行为通过。

以上 1–3 完成后可重跑第六轮复核，预期判定 PASS；4 属同批低风险修正，建议一并完成。

### 建议追加到根 `AGENTS.md` 失败案例库

失败案例库现有 22 条（阈值 30）。F1/F7/F8 属**已存在条目**覆盖的缺陷类（「自述计数与证据文件不同源」
「证据文件必须自带命令行与退出码」），建议**强化既有条目**而不是新增重复条目，加一句：

> 另：**契约改动后必须同步重生成引用它的证据文件**。本轮实测：任务与 README 都引用
> `T-002-guard-selftest.txt` 作为「完整模式 25 例」的证据，而该文件是一次 `-Fast`（23 例、且自述
> `CASE 7-8 skipped`）运行——被引用的用例恰好是它跳过的两个。证据文件要么记录**产生它的那条命令**，
> 要么在头部写明运行模式，引用方必须核对该模式与自述一致。

F3/F12 属**新缺陷类**（「用子进程退出码 / 用「跳过」代替证据存在性」），建议**新增 1 条**：

> - [2026-09-21] [guard] 把「子进程退出码 0」或「前置条件缺失」当成「守门通过」的证据 → 两种同源形态：
>   ① 守门 stage 3b 只看 `gradlew verifyNoKotlinDex` 的退出码，不检查它是否真的打印了 `verifyNoKotlinDex:` 判据行，
>   于是子任务静默退化时守门仍打印 `OK no built APK contains Kotlin runtime bytecode` 并 `VERDICT: PASS`
>   （实测：把 `gradlew.bat` 换成只回显依赖报告的桩 + 在 `app/build/outputs/apk/debug|release` 放含
>   `Lkotlin/Metadata;` 的 APK → PASS / exit 0，APK 段零证据行）；
>   ② 自检探针 CASE 8 在源 androidTest APK 缺失时把该用例记为 `Ok = $true`，完整模式照样报 `SELFTEST PASS (25 case(s))`，
>   等于该检测分支从未被验证。规则：**判据必须有「证据存在性」断言**——子进程判据至少要断言出现 ≥1 条判据行
>   （更好：断言 `PASS (N APK(s)` 且 N 等于自己递归发现的构件数）；探针的前置条件缺失必须判 FAIL，
>   或从用例总数中扣除并单独打印 `SKIPPED`，绝不能算 PASS。

---

## 附：本轮复核执行过的命令清单（便于复算）

```bash
cd /d/Project/Android/Treelearn
export JAVA_HOME='D:\Environment\Jdk\jdk21'
export JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8'

# 守门 / 自检
powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1 -AndroidRoot android
powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1 -AndroidRoot android -SkipDependencyStage
powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1 -AndroidRoot android -SkipApkStage
powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/dev/guard-selftest.ps1 -Fast
powershell -NoProfile -ExecutionPolicy Bypass -File android/scripts/dev/guard-selftest.ps1          # 完整 25 例，约 5 分钟

# 构建 / 测试 / lint
cd android
./gradlew --no-daemon --console=plain :app:assembleDebug :app:lintDebug
./gradlew --no-daemon --console=plain verifyDependencyDirection :core:test
./gradlew --no-daemon --console=plain :core:test --rerun-tasks
./gradlew --no-daemon --console=plain -q :app:dependencies > "$TEMP/r6-app-deps.txt"
python scripts/dev/lint_summary.py

# 身份 / 计数
sha256sum android/scripts/no-kotlin-check.ps1 android/scripts/dev/guard-selftest.ps1
wc -l android/scripts/no-kotlin-check.ps1 android/scripts/dev/guard-selftest.ps1
find . -name '*.gradle' -not -path '*/build/*' -type f | wc -l
find core/src/main/java -name '*.java' | wc -l
"…/python.exe" .trae/scripts/validate_tasks.py
git ls-files android artifacts        # 均为 0 → 无版本历史
```

字节码/dex 与 monitor AAR 的扫描用 Python `zipfile`（见 §2 V9 / V10 的脚本），
对抗性探针在 `%TEMP%\r6-apkstage-probe`（仓库外，未改动工作树任何文件）。
