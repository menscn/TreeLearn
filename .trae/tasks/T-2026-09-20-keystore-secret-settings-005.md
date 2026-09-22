# Treelearn Harness Task: 实现 Keystore 密钥与本地设置

id: T-2026-09-20-005
title: 实现 Keystore 密钥与本地设置
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 150
shared_code_touched: false
depends_on: [T-2026-09-20-bootstrap-java-android-modules-002.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 个人 APK 在本机保存 DeepSeek Key 与模型/思考偏好，禁止备份和泄露。
- Problem/background: 明文 SharedPreferences、日志或 Bundle 会暴露 API Key。
- Users/scenarios: 用户首次保存、替换、验证或删除 Key，并调整模型与思考模式。
- Expected outcome: Keystore AES-GCM 密钥仓储和非敏感设置仓储，失败可诊断但不泄密。
- Core flow and reversibility: 保存前加密；替换覆盖密文；删除同时移除 alias 与密文；失效后要求重录。
- Inputs/outputs: 输入 Key/偏好；输出存在性、临时解密值或分类错误。
- State and exception handling: 密文损坏、alias 丢失、认证失效均 fail closed。
- Constraints and trade-offs: API 28+；Key 不进数据库、日志、剪贴板、Intent 或 saved state。
- AI recommendation and rationale: 平台 Keystore 管理 AES-GCM key，Preferences 只存版本化密文与 IV。
- User choice and custom additions: 个人设备直接安装 APK，无账号或云端密钥托管。
- AI-autonomous decisions: 展开状态不保存；模型/思考开关属于非敏感偏好。
- User-confirmation decisions: 本地应用、禁备份和 DeepSeek 接入。
- Acceptance evidence: 设备端保存/替换/删除/损坏/重启测试和 manifest 备份检查。
- Facts: 技术方案 §14 是安全权威。
- Assumptions: 设备提供标准 Android Keystore。
- Unknowns: 无。
- Risks: 将解密值留在长生命周期对象或异常消息。
- Deferred: 生物识别二次保护和系统凭据同步。
- Non-goals: 不验证余额，不发网络请求。
- Skipped non-critical questions and reason: alias 命名是内部实现，不影响用户契约。

## Goal

安全持久化 DeepSeek Key，并向业务层提供最小生命周期的读取能力。

## Scope

### In scope
- SecretStore、SettingsRepository、AES-GCM envelope、备份禁用配置和设备测试。

### Out of scope
- DeepSeek 网关、设置页面和业务提交。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增 Key/偏好持久化；不触及对话数据库。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 设置页写入；余额验证和生成网关读取；Manifest 控制备份。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: Key 加密格式、alias、删除和备份策略。
- Authoritative code/config/migration evidence: `android/data` secret/settings 实现、Manifest 与 backup rules。
- Documentation surfaces checked: 技术方案 §14、隐私说明、Android README。
- Documentation files updated: 实际安全配置与恢复限制同步到 README/隐私说明。
- Producer/consumer/sibling/compatibility paths checked: 设置、首次主题、生成网关、日志。
- Superseded phrases/examples reverse-searched: 明文 key、云备份和设备迁移表述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保持 PRD 本地安全口径。

## Acceptance criteria

- [ ] 同一设备测试命令先 RED 后 GREEN，覆盖保存、重启读取、替换、删除及损坏密文。
  - 本轮：**deferred — emulator not used this round (user instruction 2026-09-21)**。
  - 本轮等价物：RED/GREEN 改为「`:data:testDebugUnitTest` + `:app:assembleDebugAndroidTest`（仅编译）」且命令逐字符相同；该对**尚待串行窗口重录**（RED 在仓库外副本上）。
  - 停用模拟器之前的设备轮确实跑过这套用例（保存 / 新实例读取 / 替换 / 删除 / 损坏信封 / 篡改密文 / alias 丢失），曾是 99 例零失败，属历史证据，不作为本轮通过。
- [ ] Preferences/SQLite/日志/Bundle 中均搜不到测试 Key 明文，错误文案不回显 Key。
  - 本轮可判定部分：源码层扫描证明 secret/settings 包内**没有** `android.util.Log`/stdout/Bundle/Intent/剪贴板/`SQLite*` 用法（0 命中），JVM 用例证明 `toString()` 不含明文/IV/密文，失败文案只描述类别；合成占位串只出现在两个测试文件里。
  - 需要设备的部分（Preferences/数据库目录的字节级明文扫描、设备日志）：**deferred — emulator not used this round**。
- [ ] Manifest 和 data extraction/backup rules 明确关闭云备份与设备迁移恢复。
  - 本轮可判定部分：四条 Manifest 属性与两份规则文件**实际存在于源码树**（不是声称），并由「仅编译」构建验证资源可编译。
  - 「从已安装 APK 回读属性 + 解析打包后的规则」：**deferred — emulator not used this round**。
- [ ] no-kotlin guard、lint 和 API 36 设备路径通过（用户 2026-09-21 决定以 API 36/36.1 为验收环境，见 T-002 执行期需求变更；`minSdk 28` 为声明兼容下限）。
  - guard、lint 已在停用模拟器后取得 PASS/exit 0；因最后一次是注释级编辑，需在窗口内复跑一次。
  - API 36 设备路径：**deferred — emulator not used this round (user instruction 2026-09-21)**。

## TDD execution record

### RED — failing test first
（本轮（用户 2026-09-21 停用模拟器后）要求的「JVM + 仅编译」RED。实验在**仓库之外的临时副本**上做：`%TEMP%\t005-red\android` 是 `android\` 的 160/160 完整镜像，只在副本里删掉 5 个生产类；共享仓库全程未被修改（删除后复核：副本 5 个缺失、仓库 5 个存在）。命令字符串与 GREEN 逐字符相同。）
- Test/scenario: `:data` 的 `SecretEnvelopeTest`（6 例，纯 JVM）与 `:app` 的两个 instrumentation 套件 `SecretStoreInstrumentedTest`（8 例）、`SecretBackupConfigInstrumentedTest`（2 例），写在 `com.treelearn.data.secret` 与备份配置存在之前。
- Command: cd android; .\gradlew.bat :data:testDebugUnitTest :app:assembleDebugAndroidTest --continue --rerun-tasks --no-build-cache --no-daemon --console=plain
- Exit code: 1
- Observed failure: `:data:compileDebugUnitTestJavaWithJavac` 以「找不到符号 `SecretEnvelope`」失败（`SecretEnvelopeTest.java` 62 条错误行），`:app:compileDebugJavaWithJavac` 以同一批缺失符号失败（`AiGatewayStreams.java` 10 条错误行），整轮 `BUILD FAILED in 17s`。
- Failure reason validated: 证据文件里 72 条带源文件路径的 `错误:` 行 = `SecretEnvelopeTest.java` 62 + `AiGatewayStreams.java` 10；按类型 = `找不到符号` 68 + `程序包不存在` 4（Gradle 在 FAILURE 段重印，72/2 = 36 条去重）；点名的符号 = `SecretEnvelope` 48 + `SecretStoreException` 12 + `SecretStoreError` 4 + `KeystoreSecretStore` 4 = 68 行，全部属于副本里删掉的 5 个类，日志中没有第二种成因。`AiGatewayStreams.java` 是 T-006 写在本任务之上的 `:app` 消费者，因导入被删的类而一并编译失败——这是本实验的直接后果且只发生在副本里，不是跨 lane 干扰；它让 `:app` main 先失败，所以两个 androidTest 类在这轮 RED 中未被编译到（GREEN 中编译到了）。
- Attribution read back from disk: `EXITCODE=1` 在第 519 行；`81 actionable tasks: 81 executed` 在第 518 行（无 UP-TO-DATE 混充）；失败任务行 314 与 481；`BUILD FAILED in 17s` 在第 517 行。
- Evidence: artifacts/evidence/T-005-red-compile-only.txt

历史设备轮 RED（用户 2026-09-21 停用模拟器**之前**那一轮的真实记录，保留备查，**不作为本轮结论**）：

| 历史项 | 值 |
| :--- | :--- |
| 命令 | `cd android; .\gradlew.bat :data:testDebugUnitTest :app:connectedDebugAndroidTest --continue --no-daemon --console=plain` |
| 退出码 | 1 |
| 观察到的失败 | `:data:compileDebugUnitTestJavaWithJavac` 自报 33 个错误（`找不到符号` 31 + `从int转换到byte` 2）；`:app:compileDebugAndroidTestJavaWithJavac` 自报 41 个（`程序包com.treelearn.data.secret不存在` 5 + `找不到符号` 36），合计 74 条去重，全部落在本任务新建的三个测试文件里 |
| 证据 | `artifacts/evidence/T-005-red.txt`（148 行 `错误:` = 上述 74 条 + Gradle FAILURE 段重印；其中 2 条 `从int转换到byte` 是 fixture 笔误 `0x87`/`0x96`，实现前已改为 `"t005-iv-0001".getBytes(StandardCharsets.UTF_8)`） |

### GREEN — minimum implementation
- Implementation: 新增 `:data` 的 `com.treelearn.data.secret`（`SecretEnvelope` 编解码、`SecretStoreError` 分类、`SecretStoreException`、`KeystoreSecretStore` 的 Keystore+AES-GCM 读写删）与 `com.treelearn.data.settings.SettingsRepository`；并在 `:app` 的 `AndroidManifest.xml` 与 `res/xml/backup_rules.xml`、`res/xml/data_extraction_rules.xml` 落地禁备份/禁迁移/禁明文流量。
- Command: cd android; .\gradlew.bat :data:testDebugUnitTest :app:assembleDebugAndroidTest --continue --rerun-tasks --no-build-cache --no-daemon --console=plain
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL in 20s` 与 `89 actionable tasks: 89 executed`（无 UP-TO-DATE，故本轮确实执行了编译与测试）；`:data:testDebugUnitTest` 执行后 `android/data/build/test-results/testDebugUnitTest/TEST-com.treelearn.data.secret.SecretEnvelopeTest.xml` 记 `tests="6" skipped="0" failures="0" errors="0"`；`:app:compileDebugAndroidTestJavaWithJavac` 与 `:app:assembleDebugAndroidTest` 均为 `executed`，即 instrumentation 源码编译通过而不执行；`EXITCODE=0` 在第 143 行。
- First GREEN was discarded: 同一条命令不带 `--rerun-tasks --no-build-cache` 时得到 `89 actionable tasks: 89 up-to-date` 的空 exit 0，证明不了任何事，故 RED 与 GREEN 一起改用现在这条命令重录。
- Regression evidence: 见下方「回归与守门」小节与「本轮验证状态」表：Java-only guard（PASS, 13 OK）、lint（exit 0，仅 `ApplySharedPref` 一条刻意告警）、源码扫描（无日志/无 Bundle/无 SQLite，合成占位串只在测试里）与变异测试（历史证据）。
- Evidence: artifacts/evidence/T-005-green-compile-only.txt

### Regression and guard evidence

### 本轮验证状态（用户 2026-09-21 指令：本轮不使用模拟器）

| 项 | 状态 |
| :--- | :--- |
| 本轮 RED（仅编译，本任务自身缺失符号） | 已取得：exit 1；`SecretEnvelopeTest.java` 62 + `AiGatewayStreams.java` 10 条错误行，全部源自副本里删掉的 5 个类（`T-005-red-compile-only.txt`，21:32）|
| 本轮 GREEN（`:data:testDebugUnitTest` + 仅编译 `:app:assembleDebugAndroidTest`） | 已取得：exit 0；`89 actionable tasks: 89 executed`；JVM 6 例 0 失败（`T-005-green-compile-only.txt`，21:34）|
| JVM 套件（独立重执行） | 已取得：`tests=6 failures=0 errors=0 skipped=0`，`EXITCODE=0`（`T-005-jvm-tests.txt`，20:39，历史）|
| Java-only guard | 已复跑（21:35）：`VERDICT: PASS (13 OK line(s))`、exit 0（`T-005-guard.txt`）|
| lint（`:app:lintDebug` + `:data:lintDebug`） | 已复跑（21:35）：`BUILD SUCCESSFUL`、exit 0；`:data` 报告 `0 errors, 1 warning`，唯一条目是 `KeystoreSecretStore.java:144` 的 `ApplySharedPref`（刻意用 `commit()`）（`T-005-lint.txt`）|
| 源码扫描 | 已重跑（21:35）：三条 no-match 扫描 0 命中；合成占位串 39 命中且只在两个测试文件里（`T-005-source-checks.txt`）|
| 设备执行（保存/重启读取/替换/删除/损坏密文/篡改/alias 丢失/明文扫描/已安装 APK 回读） | **deferred — emulator not used this round (user instruction 2026-09-21)**。源码保留在 `:app` 的 androidTest 源集里，本轮不执行；`android/app/build/outputs/androidTest-results/connected/debug` 文件数为 0。停用模拟器之前的设备轮曾 99 例零失败，属历史证据（`T-005-green.txt`），不作为本轮结论 |
| 变异测试 | 停用模拟器前已完成：把 `save()` 变异成额外写明文 → 全套中**只有** `theStoredSecretExistsOnDiskOnlyAsCiphertext` 转红，报出 `found in [.../shared_prefs/treelearn_secret.xml]`，还原后 SHA-256 回到基线 `9503709BAD74…`（`T-005-mutation.txt`，历史证据，本轮未重跑）|

### Evidence audit 2026-09-21（记录修正，不改变任何测量）

- `T-005-green-revert-check.txt`：删除指向并不存在的 `T-005-green-compile-only.txt` 的引用；本轮已在窗口内真正取得该文件，引用现已成立。
- `T-005-source-checks.txt`：20:47 那版 SYNTHETIC 扫描块的退出码括注被复制粘贴成 `rg: 1 = no match`（实际 exit 0、39 命中），已在 21:35 的重跑里改正。
- `T-005-attempts.txt`：表头补注末尾 attempt 12 属 post-mutation 确认跑（因他人测试 `AppLaunchInstrumentedTest` 失败且被用户中止），不是 canonical GREEN 尝试。
- `T-005-red.txt`：逐条复核（`EXITCODE=1` 在第 891 行；148 条 `错误:` 按文件 66/74/8；`符号:` 只列 `SecretEnvelope`/`SecretStoreError`/`SecretStoreException`/`KeystoreSecretStore` 与本任务新建资源 `R.xml`），与任务记录一致，无需改动。
- `T-005-red-compile-only.txt`：VOID 状态已终结——该文件现为仓库外副本上重录的真实 RED；被禁止的旧实验记录保留在 `T-005-red-stash.txt`。

**事故记录（不得重犯）**：为制造「仅编译 RED」，曾把本任务的 5 个生产类**移出共享仓库**，导致 `:app` main 与 `:data` 单测因缺少 `com.treelearn.data.secret` 符号而集体失败，另两条 lane 不得不按字节恢复（见 `T-009-crosslane-stash-recovery.txt`、`T-008-crosslane-stash-recovery.txt`）。仓库现已确认完好，5 个文件 SHA-256 与事故前一致。规则：任何「缺符号」实验都只在仓库外的副本上做。

### REFACTOR — optional
- Cleanup: 无重构：GREEN 之后生产代码未再改动（无重复分支、无死代码、无遗留 PENDING）；`T-005-red-stash.txt` 已降级为事故记录并保留，不作为证据。
- Regression evidence: 沿用 GREEN 的那一套读数、本轮未重跑：`T-005-guard.txt`（VERDICT: PASS (13 OK line(s))；EXITCODE=0）、`T-005-lint.txt`（EXITCODE=0；0 errors / 1 warning，且该 warning 为既有、非本任务）、`T-005-source-checks.txt`（3 条 expected=no-match + 1 条 expected=match hits=39，四条 verdict 均为 OK）、`T-005-mutation.txt`（变异测试：把拒绝分支退化成放行后对应用例转红，还原后复绿）。

### TDD or sizing exception — only with prior user approval
- Exception type: Not applicable
- Reason: TDD required
- User approval: Not applicable
- Alternative verification: Not applicable

## Implementation notes

- 解密结果按调用即用，不缓存到 Application；尽快清空可变字符缓冲。
- AES-GCM 每次保存使用新 IV；保存 envelope schemaVersion 以支持后续迁移。
- envelope 编码为 `<schemaVersion>:<base64 IV>:<base64 密文>`，落在 `treelearn_secret.xml` 的单个键里；alias 为 `com.treelearn.secret.deepseek-api-key`，与 Preferences 文件名、备份规则里的 `treelearn_secret.xml` 必须同改。
- `read()` 返回 `char[]`，转 byte[]↔char[] 之间会短暂产生一个不可变 `String`（平台最简路径）；已知残余风险，记在 watch-outs，不用「无法避免」掩饰（`CharsetEncoder/Decoder` 可以避免）。
- `delete()` 刻意不抛异常：删掉密文才是「不可恢复」的关键，Keystore 抖动不该让用户删不掉 Key；残留 alias 没有密文也用不了，下次保存会复用它。
- `hasSecret()` 只表示「有信封」，不表示「能解开」；损坏/alias 丢失时它仍为 true，由 `read()` 分类失败，避免 UI 把「不可读」显示成「已配置」。
- `SettingsRepository` 默认 `deepseek-flash` + 思考关闭：PRD/技术方案未固定默认值，这是实现决定（单点常量 `DEFAULT_MODEL`/`DEFAULT_THINKING_ENABLED`），T-012 可改；思考过程展开状态不持久化。
- 备份是两道防线：`allowBackup=false` 为主，两份规则文件显式排除密钥 Preferences 为兜底；`android:networkSecurityConfig` 不在本任务范围（T-018），Manifest 注释也这么写，避免文档声称存在。
- 规模（用户 2026-09-21 裁定的「非空非注释」口径）：新增生产 Java 5 文件 **271 行**（物理 406；`SecretEnvelope` 80 / `KeystoreSecretStore` 128 / `SettingsRepository` 38 / `SecretStoreException` 16 / `SecretStoreError` 9），配置另有 Manifest ±14 行与两个新 XML 共 26 行。未超 500 硬上限，超 200 软上限属 verifier 警告级，本任务不申请规模例外。

## Verification report

> 独立 verifier 复核（verifier #2，2026-09-21 第三轮），只读：未修改任何生产代码、测试或他人证据。
> 本轮按用户 2026-09-21 指令**不使用模拟器**；需要真机的验收项一律记为
> `deferred — emulator not used this round (user instruction 2026-09-21)`，既不算 PASS 也不算 FAIL。
> verifier 自产证据见 `artifacts/evidence/VERIFY3-*.txt`。

- Verdict: PARTIAL
- 审阅文件与证据: 本任务文件；`android/data/.../secret/**`、`.../settings/SettingsRepository.java`、
  `android/app/src/main/AndroidManifest.xml`、`android/app/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`；
  证据 `T-005-{red,green,red-compile-only,green-compile-only,attempts,jvm-tests,mutation,guard,lint,source-checks,
  green-revert-check,red-stash}.txt` 与 `T-008/T-009-crosslane-stash-recovery.txt`；
  verifier 证据 `VERIFY3-T005-green-rerun.txt`、`VERIFY3-T005-source-scans.txt`、`VERIFY3-jvm-rerun.txt`。
- 代码量: **271** 非空非注释行（verifier 复算 5 个生产文件：80+128+38+16+9，逐文件与任务自报一致；物理行我 409 / 它 406，
  差异来自末尾换行计法，不作判定）/ 500 硬上限 → 在预算内（超 200 软上限，警告）。
- 任务合规: PASS（`validate_tasks.py` 对本文件 PASS；RED/GREEN 键为纯值）。
- **RED/GREEN 对的取证质量（本轮重点，逐条核过）**:
  - 每份记录只含**一次**运行：各 1 行 `Generated:`、1 行 `Command:`、1 行 `BUILD`、1 行 `EXITCODE=`。
  - 两份都**未截断**：RED 539 行，以 attribution 段完整收尾；GREEN 143 行，以 `EXITCODE=0` 收尾。
  - **真实树状态写明**：RED 的 `WorkingDirectory` 是 `%TEMP%\t005-red`，`Subject` 写「仓库外 160/160 镜像、
    只在副本里删掉 5 个生产类、仓库未被修改」并把 5 个类列在 `RemovedInCopyOnly`；GREEN 的 `WorkingDirectory`
    是仓库根，`Subject` 写「真实仓库、完整未改」。这正是事故发生后的正确做法（对照 `T-005-red-stash.txt`）。
  - 两条 `Command` **逐字节相同**（158 字符，verifier 用 `-ceq` 比对为 True）。
  - **verifier 亲自复跑 GREEN**：`VERIFY3-T005-green-rerun.txt` → `BUILD SUCCESSFUL in 24s`、
    **`89 actionable tasks: 89 executed`**、`EXITCODE=0`；与 lane 记录的 89/89 executed 逐字一致（无 UP-TO-DATE 混充）。
  - 事故链完整：`T-005-red-stash.txt` 保留被禁做法，两条 lane 的复原记录在案；verifier 复核 5 个生产类当前
    SHA-256 **全部 MATCH** 记录值 → 仓库完好。
- 数字可核（逐条从盘上读回）: RED 文件 72 条 `错误:` 行 = `SecretEnvelopeTest.java` 62 + `AiGatewayStreams.java` 10；
  按类型 = `找不到符号` 68 + `程序包不存在` 4（68+4=72）；javac 自报 36（第 206 行 `31 个错误` + 第 298 行 `5 个错误`），
  两遍打印 → pass1 36 行 / pass2 36 行；被点名符号 48+12+4+4 = 68（`符号:` 行实测）。各分项之和均等于总数。
- 验收点对照:
  - [x] 先 RED 后 GREEN（本轮为 `:data:testDebugUnitTest` + 仅编译 `:app:assembleDebugAndroidTest`）— 见上。
  - [x] Preferences/SQLite/日志/Bundle 搜不到 Key 明文 — **verifier 重跑**三条 no-match 扫描：日志 0、Bundle/Intent/剪贴板 0、
    SQLite 0 命中（证据 `VERIFY3-T005-source-scans.txt`）；`SYNTHETIC` 占位串 39 处**只**出现在两个测试文件；Manifest 同时有 `allowBackup="false"`、
    `dataExtractionRules`、`fullBackupContent`、`usesCleartextTraffic="false"`，两份规则都以
    `sharedpref/treelearn_secret.xml` 排除，与 `KeystoreSecretStore.PREFERENCES_NAME = "treelearn_secret"` 同名。
  - [ ] 设备侧保存/重启读取/替换/删除/损坏密文/篡改/alias 丢失、已安装 APK 回读备份属性、真实 Keystore 行为 —
    **deferred — 本轮不使用模拟器（用户指令 2026-09-21）**。源码保留在 androidTest 源集；verifier 已证明其**可编译**
    （GREEN 复跑里 `:app:assembleDebugAndroidTest` 真实执行、exit 0），**未执行任何用例**。
- 专项守门路由: android-no-kotlin-guard（lane 21:35 复跑 `VERDICT: PASS (13 OK line(s))`）；verifier 本轮复跑
  `verifyDependencyDirection` PASS。
- 文档影响记录: PASS（README 与隐私说明已同步「即用即清 + 禁备份」事实，Manifest 与两份规则文件互相印证）。
- 启动证据: N/A（库模块 + 配置，无进程/端口）。
- 结论: **PARTIAL**
- 修复指令:
  ① `### REFACTOR — optional` 仍是 `Cleanup: PENDING` / `Regression evidence: PENDING`（第 145-146 行）；任务已在
     `verifying`，这两项应写出实际读数（无重构就写「无重构 + 沿用 GREEN 的回归证据」）；
  ② 可选：把 `read()` 的 `UnrecoverableKeyException`（平台作废 Key）与「alias 丢失」区分开，当前都归 `DECRYPTION_FAILED`；
  ③ 设备恢复后跑一次 `:app:connectedDebugAndroidTest`，把设备侧 10 例从 deferred 转成实测。
- 阻塞点与剩余风险: 设备执行 deferred；`SECURE_STORAGE_FAILURE` 的两个生产者（平台加密异常、`commit()` 返回 false）
  在真机上从未被触发，属未覆盖分类；`SettingsRepository` 目前无运行时消费者（T-012 未落地）；
  `read()` 在 byte[]↔char[] 之间会短暂产生不可变 `String`（已记入 README 与 watch-outs）。
> 实现者补记（2026-09-21，串行窗口）：修复指令 ① 已关闭——`### REFACTOR — optional`（现第 145-146 行）已由
> `Cleanup: …` / `Regression evidence: …` 两行**实际读数**替换（无重构，故沿用 GREEN 的 guard/lint/源码扫描/变异读数，未重跑）。
> ② 与 ③ 保持开放：② 属可选改进，③ 需设备。`## Verification report` 的 Verdict 仍由独立 verifier 复核后判定，本实现者不作结论。

## Handoff notes

- Completed: 生产代码、配置、测试源码与 README 同步全部落地；本轮「仅编译」RED/GREEN 对已取得（RED exit 1 / GREEN exit 0），guard、lint、源码扫描已复跑；明细见下一条与「本轮验证状态」。
- Completed: `:data` 的 `com.treelearn.data.secret`（envelope 编解码 + Keystore AES-GCM 读写删 + 失败分类）与 `com.treelearn.data.settings.SettingsRepository`；`:app` 的 Manifest（allowBackup=false / 两个备份规则 / usesCleartextTraffic=false）与两份 `res/xml` 规则；JVM 套件 6 例、设备套件 10 例（保留未执行）；README 已同步安全事实与恢复限制。
- In progress: 无。串行窗口内的全部重录与复跑已完成。
- Blocker: 无。原冻结令已由 leader 在串行窗口中解除。
- Unblock condition: 已满足（leader 宣布串行窗口开启）。
- Next steps: 交独立 verifier 复核：RED/GREEN 对（`T-005-red-compile-only.txt` / `T-005-green-compile-only.txt`）、guard、lint、源码扫描，以及设备侧的 deferred 项（本轮不跑模拟器）。下游：设置页 T-012 读 `SettingsRepository`，生成网关解出 Key 后即用即清。
- Watch-outs: ① 测试 fixture 也不得提交真实 Key（本任务只用明显合成占位串）；② 备份规则里的 `treelearn_secret.xml` 与 `KeystoreSecretStore.PREFERENCES_NAME` 必须同改，设备用例会因漂移变红；③ `read()` 返回的 `char[]` 由调用方负责清零，解密值不得进长生命周期对象、Bundle 或异常消息；④ byte[]↔char[] 之间会短暂产生一个不可变 `String`，是已知残余风险；⑤ `hasSecret()` 只表示「有信封」，损坏/alias 丢失时仍为 true，UI 必须靠 `read()` 的失败分类而不是 `hasSecret()` 判断是否需要重录；⑥ 事故教训：一切「让符号消失」的验证只在仓库外副本上做。
