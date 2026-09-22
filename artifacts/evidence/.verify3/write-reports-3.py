import io,os

PREAMBLE = """> 独立 verifier 复核（verifier #2，2026-09-21 第三轮），只读：未修改任何生产代码、测试或他人证据。
> 本轮按用户 2026-09-21 指令**不使用模拟器**；需要真机的验收项一律记为
> `deferred — emulator not used this round (user instruction 2026-09-21)`，既不算 PASS 也不算 FAIL。
> verifier 自产证据见 `artifacts/evidence/VERIFY3-*.txt`。
"""

T005 = PREAMBLE + """
- Verdict: PARTIAL
- 审阅文件与证据: 本任务文件；`android/data/.../secret/**`、`.../settings/SettingsRepository.java`、
  `android/app/src/main/AndroidManifest.xml`、`android/app/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`；
  证据 `T-005-{red,green,red-compile-only,green-compile-only,attempts,jvm-tests,mutation,guard,lint,source-checks,
  green-revert-check,red-stash}.txt` 与 `T-008/T-009-crosslane-stash-recovery.txt`；
  verifier 证据 `VERIFY3-T005-green-rerun.txt`、`VERIFY3-jvm-rerun.txt`。
- 代码量: **271** 非空非注释行（verifier 复算 5 个生产文件：80+128+38+16+9，逐文件与任务自报一致；物理行我 409 / 它 406，
  差异来自末尾换行计法，不作判定）/ 500 硬上限 → 在预算内（超 200 软上限，警告）。
- 任务合规: PASS（`validate_tasks.py` 对本文件 PASS；RED/GREEN 键为纯值）。
- **RED/GREEN 对的取证质量（本轮重点，逐条核过）**:
  - 每份记录只含**一次**运行：各 1 行 `Generated:`、1 行 `Command:`、1 行 `BUILD`、1 行 `EXITCODE=`。
  - 两份都**未截断**：RED 539 行，以 attribution 段完整收尾；GREEN 143 行，以 `EXITCODE=0` 收尾。
  - **真实树状态写明**：RED 的 `WorkingDirectory` 是 `%TEMP%\\t005-red`，`Subject` 写「仓库外 160/160 镜像、
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
    SQLite 0 命中；`SYNTHETIC` 占位串 39 处**只**出现在两个测试文件；Manifest 同时有 `allowBackup="false"`、
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
"""

T009 = PREAMBLE + """
- Verdict: PARTIAL
- 审阅文件与证据: 本任务文件；`android/data/.../db/dao/{BranchCreation,BranchDao,FirstTopicCreation}.java`、
  `android/data/.../db/TreeLearnSchema.java`、`android/app/src/androidTest/.../BranchCreationInstrumentedTest.java`、
  `android/README.md`；证据 `T-009-{red,green,jvm,tests,guard,device-era-2026-09-21,crosslane-stash-recovery}.txt`；
  verifier 证据 `VERIFY3-T009-green-rerun.txt`、`VERIFY3-jvm-rerun.txt`。
- 代码量: `BranchCreation.java` **109** 非空非注释（物理 226 / 非空 199）；verifier 复算逐字一致 → 在预算内。
- 任务合规: PASS（`validate_tasks.py` PASS；RED/GREEN 键为纯值；两条 `Command` 逐字节相同）。
- 验收点对照:
  - [x] **唯一原子写入口** — verifier 读源码确认：`BranchCreation.createInside` 在**一个** `DatabaseTransactionRunner`
    事务内依次执行 `FirstTopicCreation.sourceRefusal` → `BranchDao.insert` → `ContextSnapshotDao.insert` →
    `BranchDao.setContextSnapshot`；任一拒绝 `throw refuse(...)` 触发回滚并返回分类 `Outcome`；
    `Refusal` 两个值都有生产者；调用方 bug（null、根分支、快照与分支不一致）在开事务前抛 `IllegalArgumentException`。
  - [x] **读路径大声失败** — `BranchDao.readSource` 对半截锚点抛 `IllegalStateException`；`BranchDao` 第 233 行对
    「非根分支没有出生快照」抛 `IllegalStateException`（消息含 no birth snapshot）。未放宽。
  - [x] 跨主题来源判定**复用** `FirstTopicCreation.sourceRefusal`，源码中未写第二份。
  - [x] **DDL 未被本任务改动** — `TreeLearnSchema.java` mtime **18:08:17**，早于本任务工作时刻
    （`BranchCreation.java` mtime 21:01:03）；schema 自己的注释也把同主题规则判给写路径。verifier 读 `BRANCHES_DEFINITIONS`
    后同意「立即 CHECK + 互为外键 ⇒ DDL 表达不了该不变量」这一判断。
  - [x] RED 数字可核：20 条 `错误:` 行 = 10 条去重 × 2 遍；3 条 `找不到符号` + 7 条 `程序包BranchCreation不存在` = 10，
    与 javac 自报 10 一致；出错文件去重后只有 `BranchCreationInstrumentedTest.java`；行号与任务所写十处逐一吻合。
  - [x] **GREEN 我亲自复跑并强制重编译**（先清空 `app/build/intermediates/javac/debugAndroidTest`）：
    `:app:compileDebugAndroidTestJavaWithJavac` **真实执行**、`BUILD SUCCESSFUL`、`EXITCODE=0`，产物含
    `BranchCreationInstrumentedTest.class` 与 `$Seeded.class`（该套件 10 个 `@Test`）。这消除了 lane 自述的
    「本次 GREEN 里该任务 UP-TO-DATE，退出码不足以自证」这一缺口。
  - [x] JVM 套件 verifier 复跑：core 154 / ai 72 / data 6 / tree-layout 34，全部 0 失败（XML 回读）。
  - [ ] 行为结论（仅稳定版本可分叉、父分支移动后子分支仍钉住、10 例真机断言）—
    **deferred — 本轮不使用模拟器（用户指令 2026-09-21）**。
- 专项守门路由: android-no-kotlin-guard PASS；`verifyDependencyDirection` PASS（verifier 复跑）。
- 文档影响记录: PASS — `android/README.md:224/229/230/259-262` 已把 BranchCreation 行与「不变量由两半承担、都不在 DDL」
  写成现行事实，verifier 逐行复核存在。
- 启动证据: N/A。
- 结论: **PARTIAL**
- 修复指令: 无代码级修复。设备恢复后跑 `:app:connectedDebugAndroidTest` 取 10 例行为结论；可择机更新 `BranchDao`
  javadoc 里「调用方欠第二步」的旧措辞（当前刻意保留，以免使 T-022 钉住该文件 SHA 的证据失效）。
- 阻塞点与剩余风险: 设备执行 deferred；绕过入口写原始 SQL 仍能造出半截行——这正是读路径那一半不可省的原因，
  已由 `aNonRootRowWithoutItsSnapshotIsRefusedByTheReadPath` 设计覆盖（未执行）；本任务 compile-only RED 曾用
  「把生产类移出共享树约 11 秒」的做法，任务已自评不该再用且有两条 lane 的复原记录。
"""

T016 = PREAMBLE + """
- Verdict: PARTIAL（功能判据 PASS；**文档判据本轮由 FAIL 转 PASS**；规模判据 OPEN，未获批）
- 审阅文件与证据: 本任务文件；`android/README.md:34-40`、`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md:54`、
  `android/build.gradle`、`android/tree-layout/src/main/java/**`；证据 `T-016-{red,green,guard,sizing,doc-sync-2026-09-21}.txt`；
  verifier 证据 `VERIFY3-verifyDependencyDirection.txt`、`VERIFY3-jvm-rerun.txt`。
- **文档一致性（上一轮判 FAIL 的那条，本轮重点复核）**:
  - `android/README.md:34-40` 已换成「覆盖状态（2026-09-21 更新）」：`:core` 22 类型、`:tree-layout` **12** 个纯 JVM 类型、
    「纯净性扫描对二者均为真实强制」「不再打印 `WARNING … purity contract is vacuous …`」，并附复现命令与证据文件。
  - **verifier 亲自复跑** `.\gradlew.bat verifyDependencyDirection --no-daemon --console=plain` →
    `verifyDependencyDirection: PASS (downward-only module graph + pure-JVM module purity)`、`BUILD SUCCESSFUL in 11s`、
    `EXITCODE=0`，输出中**没有任何 `vacuous` 字样**（`VERIFY3-verifyDependencyDirection.txt`）。旧的「空跑」断言已被真实行为取代。
  - 《技术方案》第 54 行已改为「`:data`、`:ai` 只依赖 `:core`，`:tree-layout` 不依赖任何模块（纯 JVM）」，
    与 `android/build.gradle` 的 `':tree-layout': []` 一致。
  - 结论：文档缺口**已闭环**，不再构成 FAIL。
- 代码量: **861–862** 非空非注释行（verifier 两种口径：裁定 grep 口径 **861** / 字符串感知 **862**；物理 1259）/ 500 硬上限
  → **规模判据 OPEN，未获批**。`sizing-adjudication-2026-09-21.txt` 的已批准例外表只含 T-003，不含本任务。
- 功能判据: `:tree-layout` JVM 34 例（15+11+8）verifier 复跑 0 失败；`android/build.gradle:27` 允许 `:tree-layout` 零项目依赖，
  `src/main` 无 `import android|androidx|kotlin`。
- 任务合规: PASS（`validate_tasks.py` PASS；RED/GREEN 键为纯值）。
- 专项守门路由: android-no-kotlin-guard PASS；`verifyDependencyDirection` PASS（verifier 复跑）。
- 启动证据: N/A。
- 结论: **PARTIAL**（功能 PASS；文档 PASS；规模未获批）
- 修复指令: 只剩规模一项——由用户明确裁定「批准 861–862 行例外（若在实现完成之后批准，须如实记为事后追认）」，
  或要求拆成「输入模型 + 校验」与「几何 + 标签条 + 命中 + 拟合」两片。
- 阻塞点与剩余风险: 规模例外未批准，verifier 不得自行追认；文档过时断言已清除，不再有误导风险。
"""

T025 = PREAMBLE + """
- Verdict: PARTIAL
- 审阅文件与证据: 本任务文件；`android/data/.../db/dao/FirstTopicCreation.java`、
  `android/app/src/test/.../FirstTopicCreationRulesTest.java`、`android/app/src/androidTest/.../FirstTopicCreationInstrumentedTest.java`、
  `android/data/.../db/TreeLearnSchema.java`（branches CHECK）、`android/data/.../db/DatabaseTransactionRunner.java`；
  证据 `T-025-{red,green,tests,jvm-rerun,guard,constraint-code-probe,device-runs-pre-directive,supplement-data-suite}.txt`；
  verifier 证据 `VERIFY3-T025-jvm-direct.txt`、`VERIFY3-T025-green-rerun.txt`。
- 代码量: `FirstTopicCreation.java` **173** 非空非注释（物理 375 / 非空 338），低于本任务 180 估算与 500 硬上限。
- **缺陷（必须修）**: 任务文件第 107-109 行把 RED 分项写成「`decideSourceRefusal(String,String,String)` **7 处** +
  `classifyStorageRefusal(int,String)` **7 处** = 14」。verifier 实测：14 条去重错误里 **decideSourceRefusal 6 条 +
  classifyStorageRefusal 8 条**（`符号:` 行每遍 6+8，两遍共 28 行；测试源码调用点同样是 6 个 decide、8 个 classify）。
  总数 14 正确、分项写错——正是 `AGENTS.md` 失败库中「凭印象补齐的分项看似自洽」的形态。
- 验收点对照:
  - [x] 先 RED 后 GREEN（JVM + 仅编译）：两条 `# command:` 逐字节相同；RED 28 条 `错误:` 行 = 14 条去重 × 2，
    javac 自报 14，出错文件去重后只有本任务自己的 `FirstTopicCreationRulesTest.java`。
  - [x] **跨主题 source 判定** — verifier **亲自用 javac + JUnitCore 独立执行**该用例类（绕开 Gradle 与其它 lane 的源集）：
    `JUnit version 4.13.2` … `OK (11 tests)`、`EXITCODE=0`（`VERIFY3-T025-jvm-direct.txt`）。判定点本身是 5 行纯函数。
  - [x] **重复 topic id 分类** — `CODE_PRIMARY_KEY = 1555` + 小写 `UNIQUE constraint failed: topics.id`（由 schema 常量拼装），
    来自实测探针 `T-025-constraint-code-probe.txt`；`Refusal` 三个值都有生产者，无不可达码。
  - [x] **五类行同事务**（无设备可证的部分）— 读 `FirstTopicCreation.createInside`：`TopicDao` → 跨主题判定 →
    `BranchDao` → `MessageDao` ×2 → `GenerationOperationDao`，全在 `runner.run(...)` 的**一个**工作体内；任一步
    `SQLiteException` → `throw refuse(...)` 触发回滚 → `create` 捕获后返回分类 `Outcome`，不抛裸异常穿透。
  - [x] **根分支不带出生快照/来源**（无设备可证的部分）— DDL 已强制：`BRANCHES_DEFINITIONS` 的
    `CHECK (parent_id IS NOT NULL OR (source_message_id IS NULL AND source_revision_id IS NULL AND anchor_* IS NULL AND
    context_snapshot_id IS NULL))`；且 `BranchDao.insert` 根本不写 `context_snapshot_id`。
  - [ ] 五类行可读 + 回滚后行数为 0 + `PRAGMA foreign_key_check` 无结果 + 关闭重开一致 + `db-migration-reviewer` 复核 —
    **deferred — 本轮不使用模拟器（用户指令 2026-09-21）**。
- 独立复跑状态: 本任务自己的 GREEN 命令 **verifier 连试三次都失败**，但失败点全在**别人写的文件**上
  （`app/src/test/.../artifact/{ArtifactInputTest,ArtifactSchemaTest,DerivedRequestTest}.java` 与
  `generation/PositionCommandsTest.java`，即 T-011/T-010 的在途 lane）→ 记为跨 lane 干扰，非本任务缺陷。
  因此以 javac/JUnitCore 直跑（上）作为独立执行证据。
- 专项守门路由: android-no-kotlin-guard PASS（lane 记录）。
- 文档影响记录: PASS（`android/README.md` 已把首题事务入口、跨主题 source 唯一判定点写成当前态；
  但 Handoff 里那条 watch-out 需按时效修订，见修复指令②）。
- 启动证据: N/A。
- 结论: **PARTIAL**
- 修复指令: ① 把第 107-109 行的分项改为实测的 **6 + 8 = 14**；
  ② Handoff 中「`:data` 单测被另一任务的 `SecretEnvelopeTest.java` 挡住」的 watch-out 已过时（第 121 行的补充证据
  与 verifier 复跑都显示 `:data:testDebugUnitTest` 正常执行 6 例、0 失败），请注明其时效或删除；
  ③ 设备恢复后跑整包，把五类行/回滚/重开三条从 deferred 转成实测。
- 阻塞点与剩余风险: 设备执行 deferred；`:app` 单测源集与其它 lane 共用，任一 lane 在途 RED 都会让本任务命令变红（本轮实测三次）。
"""

SUBJECTS = [
    (".trae/tasks/T-2026-09-20-keystore-secret-settings-005.md", T005),
    (".trae/tasks/T-2026-09-20-topic-branch-lifecycle-009.md", T009),
    (".trae/tasks/T-2026-09-20-pure-java-tree-layout-016.md", T016),
    (".trae/tasks/T-2026-09-21-first-topic-atomic-creation-025.md", T025),
]

for path, body in SUBJECTS:
    lines = io.open(path, encoding="utf-8", newline="").readlines()
    starts = [i for i, l in enumerate(lines) if l.startswith("## Verification report")]
    assert len(starts) == 1, (path, starts)
    s = starts[0]
    nxt = [i for i in range(s + 1, len(lines)) if lines[i].startswith("## ")]
    assert nxt, path
    e = nxt[0]
    old = "".join(lines[s:e])
    assert "PENDING" in old, ("expected a PENDING stub", path)
    new = "## Verification report\n\n" + body.strip() + "\n\n"
    lines[s:e] = [new]
    io.open(path, "w", encoding="utf-8", newline="").write("".join(lines))
    print("OK", path, "replaced", len(old.splitlines()), "->", len(new.splitlines()), "lines")