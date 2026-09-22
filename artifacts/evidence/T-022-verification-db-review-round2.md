# T-022 数据库守门第二轮复核报告（db-migration-reviewer）

> 目的：确认首轮 PARTIAL 的 H1/H2/M1/M2/L1–L4 是否真的收口，以及修复是否引入新问题。
> 该轮**不**复核测试计数与变异测试（已由独立 verifier 完成），也不代判 verifier 的轴。
> 首轮报告见 `T-022-verification-db-review.md`。

> **归档时序说明（阅读前必读）**：本轮评审**当次返回的产物是 N1/N2/N3 三条「发现」**，`§2` 里的处置文字是**归档时补写的**，因此它描述的是**第三轮复核确认 CLOSED 之后**的状态，不是第二轮评审当时的树状态。下文各处 `已修` 一律读作「修完并经第三轮确认」。特别地，`§2 N2` 的样本替换（`(4,4,"abcd",…)` → `(0,4,"ab",…)`）与「真机补入用例」这两件事都发生在第三轮期间；第二轮当时该子集是零覆盖，这一点已如实写在 `§3` 并由第三轮继续追出 N6（样本虽已换，但当时仍种在 `PRAGMA ignore_check_constraints=ON` 块内，「过 CHECK」这半边只能静态推导）。

```text
目标引擎: Android SQLite（Android framework SQLiteDatabase / SQLiteOpenHelper；emulator-5554 "Medium_Phone_API_36.1(AVD) - 16"）
命中 schema/migration:
  - android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java（V1；DATABASE_VERSION=1；FORWARD_MIGRATIONS 空）
  - android/data/src/main/java/com/treelearn/data/db/dao/BranchDao.java（readSource 重写 + insert javadoc）
  - android/app/src/androidTest/.../TopicBranchMessageDaoInstrumentedTest.java、SqliteSchemaInstrumentedTest.java（用例与夹具）
  - Docs/05-technical-design/技术架构方案 §5.2、建表入场确认清单 §2/§3/§4、android/README.md
已发布迁移被改: 否（`git tag -l` 空、versionCode 1；改 V1 DDL 合规，但见 L4）
版本与顺序: PASS
真实目标引擎执行证据: PASS（XML tests="40" failures="0" errors="0" skipped="0"，2026-09-21T08:48:46Z；7 个被检文件 SHA-256 与当前树逐一相符）
新装/升级/重开/失败路径: PASS（新装 / 关闭重开 / 回滚 / 级联；升级 N/A，V1 未发布）
约束与完整性: PASS
UTC 时间规范: PASS（本轮无变化）
建表入场记录: PASS（本轮）
文档同步: PASS
专项 guard: N/A（本轮未重跑，由既有证据承担）
结论: PARTIAL（数据库守门维度全部 PASS；剩余为 3 处低严重度的文档/断言精度缺陷，按「已知错误不得报 PASS」不转 PASS）
失败修复指令: 见 N1（必须修，同一 hunk 内的自相矛盾）、N2、N3
```

## 1. 逐条判定（守门本人观察到的最小充分证据）

| 发现 | 判定 | 证据 |
| :--- | :--- | :--- |
| **H1** | **CLOSED** | `TreeLearnSchema.java:272-279` 四字段同生同灭 CHECK，原独立 range CHECK 已删除并被替换（`git diff` 显示旧 `start <= end` 三段式消失，`<=` 收紧为 `<`，与 `ofRange:50` 一致，无规则丢失）。`BranchDao.java:202-209` 显式区分两种合法形态，用 `Integer`（不再拆箱）；`:215-222` 用 `parsed.error()` 而非 `.value()`，消息经 `DomainError.toString` 带码带 detail。真机由 T-004 的 `forkAnchorColumnsAreAllOrNothing`（两种合法形态可写 + 六种按 `code 275` 拒绝，`:673-679`）与 T-022 的 `rangedForkRoundTripsBothLocators`（首次执行 `ofRange` 读路径）收口。无任何仍可写且读路径静默降级的行。 |
| **H2** | **CLOSED** | 重算 7 个 SHA-256，与 `T-022-restore-location.txt:15-21` **逐字符相同**；逐字复跑 Command 1/3/5/6，匹配集与退出码完全一致（Cmd5 无匹配 `EXITCODE=1` 与文件一致）；Cmd3 覆盖到 androidTest 且 `TopicBranchMessageDaoInstrumentedTest` 已零命中，证明「创建用法改走 `fromOperation`」属实；结论段不再与自身输出矛盾。 |
| **M1** | **CLOSED** | 用例改名 `readingPositionColumnsExistAsIntegerOffsets`（XML 已见），改用 `TreeLearnSchema.Branches.*` 常量，并断言两个偏移列为 INTEGER（`:563-566`）；Gaps 第 4 条与 Handoff「T-009 接手 reading_*」齐备。§11 的读写属 T-009（需 `Branch` 先有字段），本任务记为已知未覆盖面可接受。 |
| **M2** | **CLOSED** | 技术方案 §5.2:112、清单 §2:25、README 三处措辞与 DDL 一致；清单 §4:48 为**未勾选** `[ ]`，写「已落库，尚未追认」「请确认该语义，或要求改回」，并保留空白确认人；未伪造任何用户追认。§6.1 不含锚点空值性表述，故「同步 §6」无漂移（更正落在 §5.2）。「六种形态 / `code 275`」与 T-004 实测一致。 |
| **L1** | **CLOSED** | 用例改名 `theStoreRejectsANullIdentityOnANotNullColumn` 与断言一致；javadoc 明说「DAO 零 DDL 是源码属性，行为测试无法见证」，并由 DDL 检索承担。 |
| **L2** | **PARTIAL** | `insert` javadoc（`:35-47`）与 `readSource` 空快照前置判（`:228-234`）均已落地；**但同一处的 `read` javadoc（`:159-166`）仍写着该状态由 `BranchSource#restore` 以 `IllegalArgumentException` 拒绝——该路径已被前置判取代**（→ N1） |
| **L3** | **CLOSED** | `TreeLearnSchema.java:220-226` 写入「同主题规则属写入路径（023），库层无法用 CHECK 表达」；Gaps 同款记录。 |
| **L4** | **CLOSED（记录充分）** | `DATABASE_VERSION=1` 未变（`:51`），`FORWARD_MIGRATIONS` 空（`:517`）；Gaps + Handoff + 清单 §3 三处同记「只对新装生效，首发前必须清库或升 V2 重建 `branches`」。**对首个发行版足够的理由**：V1 未发布（无 tag、versionCode 1），无用户数据可比对，故不是数据丢失风险；不足在于动作无 owner（→ N3）。 |

## 2. 新发现与处置

### N1【低】`BranchDao.read` 的 javadoc 描述了已被本类删除的异常路径（同一 hunk 内的自相矛盾）— **已修（第三轮复核判 CLOSED）**

- 位置：`android/data/src/main/java/com/treelearn/data/db/dao/BranchDao.java:159-166`（对照 `:228-234`）。
- 为什么错：javadoc 说空 `context_snapshot_id` 由 `BranchSource#restore` 抛 `IllegalArgumentException`；修复后 `readSource` 先判并抛 `IllegalStateException`，`restore` 在该状态下**永不到达**。调用方按 javadoc 写的 catch 会漏接，而这句同时是本仓库最强调的「调用方义务」条款，错在这里代价最高。
- 处置：已改为「本类在 `readSource` 中先判并抛 `IllegalStateException`（含契约原文与分支 id）；`BranchSource.restore` 只在快照非空时才被调用」，并以过去时保留这次变更的原因（通用消息不可诊断）。

### N2【低】「分组归库层 / 内容归领域层」的表述与实际判定不一致，且「过 CHECK 却被领域拒」的一支仍无真机用例 — **已修（第三轮复核判 CLOSED，另立 N6 追其残余）**

- 位置：`TreeLearnSchema.java:265-271`、`android/README.md`（同生同灭段）、`Docs/.../技术架构方案_V1.0.md:112`，对照 `BranchAnchor.java:46-70` 与 `TreeLearnSchema.java:278`。
- 为什么错：
  1. `ofRange` 除了「非空 / 长度一致」还判定 `end > start`，而库层 CHECK 也判 `start < end`——**同一谓词两处判定**（两层结论一致，不产生分叉，但与「内容规则只在 ofRange 一处」的表述矛盾）；而库层放行、领域拒绝的子集（`start = -1`、`quote = ''`、`quote.length() != end - start`）此前零覆盖。
  2. T-022 用例里唯一一条「领域拒绝」的样本是 `(4,4,"abcd","hash")`，而它**正是 CHECK 所拒的逆向/空区间**，因此该用例注释把「组内四列齐全、但方向/内容不被领域接受」分类错了，真正「过 CHECK 却被领域拒」的子集仍零覆盖。
- 可控性：读路径对这三类行一律大声失败，消息含码与 detail（`BranchDao.java:215-222` + `DomainError.toString` = `ANCHOR_INVALID: <原因>`），无静默降级、无 NPE、无数据丢失。（守门按只读约束以静态推导得出，未上机。）
- 处置：
  - **如实划界**，不再声称"内容规则只在领域层"：`TreeLearnSchema` 的 CHECK 注释、`android/README.md`、技术方案 §5.2 三处同步改写为——库层判**分组**与**方向**（方向是**刻意且唯一**的重叠：让方向颠倒的行在写入时即被拒，而不是先落库再让整个主题读不出来；两层结论一致故调用方观察不到分叉）；**内容**规则（起点非负、quote/hash 非空、quote 长度等于跨度）只在 `BranchAnchor.ofRange` 一处判定。
  - **补齐真实覆盖**：把 T-022 那条样本换成 `(start=0, end=4, quote="ab", hash="…")`——它**通过 CHECK**（四列齐全且 `0 < 4`）而**只被领域层拒绝**（quote 长度 2 ≠ 跨度 4），并改正注释。这才是「分层」可被检验的地方。

### N3【低】L4 的处置动作无 owner — **已修（第三轮复核判 CLOSED）**

- 位置：任务 Gaps、Handoff、建表入场清单 §3。
- 为什么错：写的是「首发前必须处置」，但没有任何任务、守门或发布检查项会再把它翻出来。
- 处置：已在清单 §3 点明承接者——首个发行版的版本切割任务（`T-2026-09-20-security-release-core-hardening-018` 的发布检查项），并写明「该任务必须在本限制被处置或明确豁免并记录之前，不得宣告可发布」。

## 3. 本轮未验证的部分（守门原话）

- 本轮**未上机**，未跑 `connectedDebugAndroidTest`（采信既有 XML `tests="40"` 已 green、时间戳晚于最后一次源码改动——7 个 SHA-256 与当前树相同可证此后无改动）；未复核 `:core:test` 131、lint、Java-only 守门、`:core:javadoc` 四个证据文件的内容；未做变异测试（由独立 verifier 另行完成两项）。
- 未执行 `.gen-t022-restore-location.sh` 本身（只逐字复跑其中 4 条命令）。
- 未在设备上验证 N2 的「过 CHECK 而领域拒绝」样本；该结论来自对 CHECK 文本与 `ofRange` 的直接推导。**该样本的真机用例是在第三轮期间才补入的（见 N2 处置第 2 点与 N6）——第二轮评审当时此子集零覆盖，本文件 `§2` 的这一句是归档时补写的，不代表第二轮已覆盖。**
