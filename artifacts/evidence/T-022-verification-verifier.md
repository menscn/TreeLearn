# T-022 独立 verifier 复核报告

> 由独立 verifier（read-only，独立于实现者）出具；主会话原样归档，未改动结论。
> 该 verifier 被明确要求"不要采信任务自述"，并自行复跑了全部计数、独立复算了规模、对两处承担**唯一证据**的断言做了变异测试。

```text
[verifier 报告]
task: T-2026-09-21-022
审阅文件: .trae/agents/verifier.md; .trae/rules/{harness-verification,harness-tasks,regression,documentation,sql}.md; AGENTS.md;
  .trae/tasks/T-2026-09-21-…-022.md 与依赖 …-004.md;
  android/data/src/main/java/com/treelearn/data/db/dao/{BranchDao,MessageDao,TopicDao,Rows}.java;
  android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java;
  android/app/src/androidTest/java/com/treelearn/app/{TopicBranchMessageDao,SqliteSchema}InstrumentedTest.java;
  android/{core/src/main/java/com/treelearn/core/domain/{Branch,Message}.java,README.md};
  Docs/05-technical-design/…技术架构方案_V1.0.md、…建表入场确认清单_V1.0.md;
  artifacts/evidence/T-022-*.txt、T-022-verification-db-review.md
代码量: 非空非注释 386 行（349 DAO + 37 core，实测）/ 物理 600 行（531 + 69）/ 500 行硬预算
任务合规: PARTIAL（front matter/分区/模板/validate_tasks 0 error 均合规；状态字段与规模证据两项见下）
Lint:
  - android :app:lintDebug: PASS（T-022-checks.txt，GRADLE_EXIT=0）
  - android Java-only guard: PASS（VERDICT: PASS (13 OK line(s))，exit 0，复跑一致）
依赖方向: PASS（dao 包仅 import android.* / com.treelearn.core.domain.* / 同模块 TreeLearnSchema，无反向 import）
验收点对照:
  - [x] 1 先 RED 后 GREEN —— RED exit 1（编译级，任务已如实标注）、GREEN exit 0 复现
  - [x] 2 (branch_id, ordinal) 重复被拒且不留半行 —— 断言 code 2067 + 被拒 id 查不到
  - [x] 3 同主题第二根被拒 —— code 2067 + branches.topic_id
  - [x] 4 级联清空 + foreign_key_check 无结果 —— 另有「只清自己子树」一条
  - [x] 5 关闭重开逐字段相等 —— 三实体各一条，字段先改成非默认值
  - [x] 6 非根分支带完整 BranchSource、整条回答分叉偏移为空版本引用非空
  - [x] 7 再水合契约 —— 独立复算：BranchSource.restore 调用点全 :data 仅 1 处，在 BranchDao.readSource
  - [x] 8 db-migration-reviewer 复核 —— 首轮 PARTIAL 且其 H1/H2/M1/M2/L1–L4/Q2 在报告中确实存在
专项守门路由: db-migration-reviewer = PARTIAL（首轮 PARTIAL + 修复自述，缺该守门的复评；verifier 不代判，见 F2）
文档影响记录: PASS（README / 技术方案 §5.2 / 清单 §2、§3、§4 均已同步且与代码一致；§4 新增项未勾选、明写「尚未追认」，无伪造用户确认）
启动证据: N/A（:app 启动测试在同一批 40 例内通过：mainActivityRendersFirstScreenWithoutCrash）
结论: PARTIAL
修复指令: 见 F1/F2/F3/F4
阻塞点与剩余风险: 规模口径待用户裁定；DB 守门复评未出；restore 单点声明无机械守门（已记 Gaps）
新型错误（建议追加 AGENTS.md 失败案例库）: 见 F3 —— 「同一份自述里两个口径的行数被相加成一个不存在的数」
```

## 1. 独立复算（全部 verifier 自己跑的，不采信任务自述）

| 复核项 | 命令 | 观察结果 |
| :--- | :--- | :--- |
| instrumentation | `cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain` | `Starting 40 tests` / `Tests 40/40 completed (0 skipped) (0 failed)` / `BUILD SUCCESSFUL` / exit 0；XML 独立解析 `AppLaunchInstrumentedTest 1` + `SqliteSchemaInstrumentedTest 24` + `TopicBranchMessageDaoInstrumentedTest 15` = 40，`failures=0 errors=0 skipped=0` |
| `:core:test` | `./gradlew :core:test --rerun-tasks` | `BUILD SUCCESSFUL`；XML 汇总 `files=11 tests=131 failures=0 errors=0 skipped=0`（真执行，非 UP-TO-DATE） |
| `:core:javadoc` | `./gradlew :core:javadoc --rerun-tasks` | exit 0（任务证据里当时只是 UP-TO-DATE，强制重跑后仍绿） |
| Java-only guard | `android/scripts/no-kotlin-check.ps1` | `VERDICT: PASS (13 OK line(s))`，exit 0，逐行与 `T-022-guard.txt` 一致 |
| guard 自检 | `android/scripts/dev/guard-selftest.ps1` | `OVERALL = SELFTEST PASS (40 case(s))`，exit 0 |
| `validate_tasks.py` | `python .trae/scripts/validate_tasks.py` | `SUMMARY files=23 errors=0 warnings=3`，exit 0 |

**两个头条声明，独立推翻尝试均失败（即声明成立）：**

- (a) 40 = 15 + 24 + 1，按 XML 的 `classname` 分组复算，与 `T-022-tests.txt` 逐名一致。
- (b) `grep -rnE 'BranchSource\s*\.\s*restore\s*\(' android/data/src/main` 只命中 `BranchDao.java:235`（在 `readSource` 内）；静态导入写法与裸 `restore(` 均零命中。`:224` 是注释，`:19`/`:162` 是 javadoc，不是调用。

**变异测试（针对两处承担唯一证据的断言，真实执行；用 SHA-256 证明还原）**

| 变异 | 结果 | 还原校验 |
| :--- | :--- | :--- |
| `BranchDao.readSource` 的半截锚点守卫改为 `if (false && !wholeAnswer && !ranged)` | `tests 40 failures 1`，**唯一红点**是 `readSourceFailsLoudOnARowTheCheckWouldHaveRejected`，异常 `NullPointerException ... Integer.intValue() on a null object reference @ BranchDao.readSource:215`（正是该守卫要挡的历史形态） | 还原后 `sha256=89b8f8b7…c58e2` 与变异前一致 |
| `TreeLearnSchema` 的锚点分组 CHECK 改为 `CHECK (1 = 1)` | `tests 40 failures 1`，**唯一红点**是 `forkAnchorColumnsAreAllOrNothing`（T-004），其余 39 绿 | 还原后 `sha256=3409c70b…d0f1` 与变异前一致；重跑 40/40 绿 |

两处都**不是**假见证。另外四个被点名的用例也逐个读过：`nonRootBranchRehydratesWithProvenanceAndWholeAnswerAnchor`（真实 `fromOperation` + 两步写 + 回读）、`rangedForkRoundTripsBothLocators`（首次真正执行 `ofRange` 读路径）、`readingPositionColumnsExistAsIntegerOffsets`（PRAGMA 断言列存在 + 两列为 INTEGER，javadoc 已明说零生产者零消费者）、`theStoreRejectsANullIdentityOnANotNullColumn`（直接 `execSQL` 撞 NOT NULL，按 `code 1299` 归因，不经 DAO，名实相符）。均无法在空表或删除生产代码后通过。

## 2. 发现与处置

| 编号 | 级别 | 内容 | 处置 |
| :--- | :--- | :--- | :--- |
| F1 | FAIL 级判据 | 规模：统一**非空非注释 = 386 行**、统一**物理 = 600 行**；物理口径超 500 硬上限且无**事先**批准（任务自述为 PENDING） | **待用户裁定**（见 F1 详述）。不得由实现者自行追认 |
| F2 | PARTIAL | 「H1/H2/M1/M2/L1–L4 已收口」只有实现者自己的套件与检索作证，缺第二轮 `db-migration-reviewer` | 已派第二轮专项守门，报告见 `T-022-verification-db-review-round2.md` |
| F3 | LOW | RED 自述「去重后 63 条：×3 + ×60」自相矛盾（3+60≠66）。实测构成为 **×3 + ×63 = 66** | 已改正 `:112`，并按失败案例库规则写明复算命令与读数位置；同时注明 RED 记录于 13 例版本 |
| F4 | LOW | `status: ready` 应为 `verifying`；RED/GREEN 两份证据缺 `Generated:`/`Command:` 头；RED 的行号对不上交付版测试文件 | 已改状态；已用 `.gen-t022-red-green-headers.sh` 补**标注为补记**的来历头（不伪造运行时间）；已在 RED 段注明版本差异 |
| 跨任务 LOW | LOW | `android/README.md` 声称 `app-debug.apk 约 71 KB、4 个 dex`，实测 84174 B / **5 个 dex**，与同批守门输出一致 | 已修，并改为**不在 README 复制易漂移的数字**，改指守门证据为准 |

### F1 详述（唯一阻塞项）

`harness-tasks.md:45`：超过 500 行除非已记录用户批准的规模例外，否则为失败。本任务自述 `estimated_production_lines: 320`，实测非空非注释 386（DAO 349 + `:core` 37）、物理 600。

verifier 明确反对"把口径留给用户去猜"，给出两条互斥路径：
- (a) 申请**书面**规模例外，写明 H1/L2 带来的增量不可分割；
- (b) 按仓库 `>500 即 FAIL` 的既有行为，把 `Rows` 或 `:core` 的两个 `restore` 拆成带显式依赖的独立切片。

同时要求把 `estimated_production_lines` 修正到实测量级。

**实现者立场（不改判定）**：这属治理决策，连同 T-003（762 行）、T-021（210 行）、T-004（722 / 458 行）一起，需要用户一次性裁定口径。实现者不自行追认例外，也不在无授权的情况下拆分已验证的切片。

## 3. 明确干净的部分（verifier 原话）

> 证据自证性整体高于本仓库历史水平：`T-022-restore-location.txt` 每条命令带 `EXITCODE=`、被检文件带 SHA-256（`BranchDao` `89b8f8b7…` 与 `TreeLearnSchema` `3409c70b…` 现场复算完全一致，未过期）、结论逐条只陈述输出里的事实、Command 5 的 `EXITCODE=1` 被正确解释为「无匹配」。`T-022-tests.txt` 的名单由脚本从 XML 提取而非手抄。清单 §4 的待确认项保持未勾选并注明「尚未追认」，没有伪造用户确认。`:data` 零 DDL（Command 5 零匹配、Command 6 对照非空）成立。
