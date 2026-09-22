# T-022 独立数据库守门复核报告（db-migration-reviewer）

```text
[db-migration-reviewer 报告]
目标引擎: Android SQLite（Android framework SQLiteDatabase / SQLiteOpenHelper，真机 emulator-5554 API 36.1）
命中 schema/migration:
  - android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java（V1；DATABASE_VERSION=1；FORWARD_MIGRATIONS 空）
  - android/data/src/main/java/com/treelearn/data/db/dao/{TopicDao,BranchDao,MessageDao,Rows}.java（新增）
  - android/core/src/main/java/com/treelearn/core/domain/{Branch,Message}.java（新增 restore；Branch 私有构造新增 updatedAt 形参）
  - android/data/src/main/java/com/treelearn/data/db/DatabaseTransactionRunner.java（仅 javadoc）
已发布迁移被改: 否（V1 未发布：`git tag -l` 空、versionCode 1、无历史库；改 V1 DDL 合规。但见 L4）
版本与顺序: PASS
真实目标引擎执行证据: PASS（本报告作者独立复跑，见 §1）
新装/升级/重开/失败路径: PASS（新装 onCreate / 关闭重开 / 事务回滚 / 级联；升级 N/A 且 onUpgrade 委托断言已由 T-022 加固）
约束与完整性: FAIL（H1：anchor 完整性约束被放宽超出必要范围；ranged 读路径零覆盖）
UTC 时间规范: PASS
建表入场记录: PARTIAL（七表清单与实现一致；但 anchor_hash 空值性属字段语义变更，未回清单/技术方案同步 → M2）
文档同步: FAIL（清单 §2/§5.2 未同步；任务自述「已检查」与事实不符 → M2）
专项 guard: PASS（Java-only 守门 + 自检任务自述 40 例；`:core:javadoc` 由本报告独立补跑，exit 0）
结论: PARTIAL
失败修复指令: 见 §3 的 H1 / M1 / M2；修完 H1 的补偿约束并补一条 ranged 分叉往返用例后，可转 PASS。
```

---

## 1. 本次复核实际执行的独立验证（不采信任务自述）

| 验证 | 命令 | 结果 |
| :--- | :--- | :--- |
| 真机 instrumentation | `cd android && ./gradlew :app:connectedDebugAndroidTest --console=plain` | **BUILD SUCCESSFUL**，`Starting 37 tests` / `Finished 37 tests`，耗时 55s |
| JUnit XML 计数 | `grep -o 'tests="…"' android/app/build/outputs/androidTest-results/connected/debug/*.xml` | `tests="37" failures="0" errors="0" skipped="0"`（与任务自述一致） |
| 再水合契约检索 | `grep -rn 'BranchSource\.restore' android/data/src/main/java` | **仅 1 处**：`BranchDao.java:180`（结论成立，但证据文件本身不自证 → M2/H2） |
| 全仓三处 restore 检索 | `grep -rn 'BranchSource\.restore\|Branch\.restore\|Message\.restore' --include=*.java .` | `:data` 生产源码只有 3 处，全在 `BranchDao.read/readSource` 与 `MessageDao.read` |
| DAO 列名字面量 | `grep -n '"id"\|"branch_id"\|…' android/data/.../dao/*.java` | 无输出——DAO 未写死任何列名 |
| `:core:javadoc`（任务证据缺失项） | `cd android && ./gradlew :core:javadoc` | **BUILD SUCCESSFUL（exit 0）**，无 `{@link}` 悬空错误；仅 100 条既有的缺 `@param/@return` 警告 |

结论：T-022 声称的真机路径**可以复现**；`:core:javadoc` 这条本仓库历史上真实翻过车（`Branch.java:90-93` 的注释记录了「测试绿而 javadoc 红」）的风险，本次**不存在**。

---

## 2. 六个核心问题的结论

### Q1 去掉 `anchor_hash` NOT NULL 是否安全？

**方向正确，但放宽得比需要的多——必须补一条精确的补偿约束。**

支持去掉的证据（三条互相印证）：

1. `Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md:112`：**「整条回答分叉时偏移为空，但版本引用仍必填」**——原文只把**版本引用**（`sourceMessageId`/`sourceRevisionId`）列为必填，没有把 hash 列为必填。
2. `BranchAnchor.wholeAnswer()`（`android/core/.../BranchAnchor.java:33-35`）按设计产出 `(null, null, null, null)`；`BranchSource.fromOperation` 接受它；`BranchDao.values` 于是写入 `anchor_hash = NULL`。**旧 CHECK 会让领域唯一合法的「整条回答分叉」永远写不进去**——这是 schema 与领域模型互相矛盾，schema 服从领域模型是对的。
3. 建表入场清单 §2 的 `branches` 行只把 `anchorHash` 列为该表**持有**的字段，未标注必填；清单也未把 hash 列为约束。

**但「一条都不要求」不等于「只需去掉这一条」。** 旧 CHECK（`git diff` 可查）同时挡住了三类行，去掉后全部可写：

| 现在可写、修复前不可写的行 | `BranchDao` 读到它的后果 |
| :--- | :--- |
| `start≠NULL, end=NULL`（半截偏移，无 quote/hash） | `BranchDao.java:177` 把 `Integer end` 自动拆箱 → **NullPointerException** |
| `start≠NULL, end≠NULL`（ranged 但无 quote/hash） | `BranchAnchor.ofRange` 返回失败 → `.value()`（`DomainResult.java:51-56`）抛 **IllegalStateException**，不是 `ANCHOR_INVALID`；「丢失偏移又没有回退由领域层 `ANCHOR_INVALID` 拒绝」的写法与实际不符 |
| `start=NULL` 但 `quote`/`hash≠NULL` | `readSource` 只看 `start == null` → **静默按 `wholeAnswer()` 读回，quote/hash 被丢弃**，违反验收条件 5 的「逐字段相等」，也违反 `:core` 自己「损坏的行大声失败」的约定 |

现有 range CHECK（`TreeLearnSchema.java:249-252`）只保证 `start <= end`，不保证「偏移成对出现」，更不保证「有偏移就有回退」。**补偿约束应当把不变量写回库层**（这正是 schema 存在的意义：它必须挡住将来任何 DAO 或原始 SQL 写入的非法行，T-023 的夹具就在直接用 `execSQL` 写分支行）：

```java
// 替换 TreeLearnSchema.java:249-252 的三段式 CHECK
"CHECK ((" + Branches.ANCHOR_START_INCLUSIVE + " IS NULL AND "
        + Branches.ANCHOR_END_EXCLUSIVE + " IS NULL AND "
        + Branches.ANCHOR_QUOTE + " IS NULL AND " + Branches.ANCHOR_HASH + " IS NULL)"
        + " OR (" + Branches.ANCHOR_START_INCLUSIVE + " IS NOT NULL AND "
        + Branches.ANCHOR_END_EXCLUSIVE + " IS NOT NULL AND "
        + Branches.ANCHOR_QUOTE + " IS NOT NULL AND " + Branches.ANCHOR_HASH + " IS NOT NULL AND "
        + Branches.ANCHOR_START_INCLUSIVE + " < " + Branches.ANCHOR_END_EXCLUSIVE + "))"
```

这条 CHECK 同时做到：放行 `wholeAnswer`（四字段全空）、强制 ranged 锚点四字段齐全且范围正向、并让「半截偏移」在库层就死掉。若不愿把「hash 必填」写回 schema（可辩护：hash 是回退手段而非身份），**最低限度**也要在 `BranchDao.readSource` 里对 `(start == null) XOR (end == null)` 和「`start == null` 但 quote/hash 非空」显式 fail loud，并修掉 `ofRange(...).value()` 丢弃 `ANCHOR_INVALID` 的问题。

### Q2 `:core` 的 `restore` 是否被正确使用？

- **验收条件 7 的机械检索成立**：`:data` 生产源码中 `BranchSource.restore` 恰好 1 处（`BranchDao.java:180`），且 `Branch.restore`/`Message.restore` 各 1 处，都在只读映射内（`BranchDao.java:157`、`MessageDao.java:85`）。我独立复跑确认。
- **但检索范围比条件的措辞窄**：`BranchSource.restore` 在 `:app` 的 androidTest 里出现 3 次（`TopicBranchMessageDaoInstrumentedTest.java:193/306/362`），而这三处正是**把 restore 造出来的对象交给写路径**（`Branch.child(...)` → `BranchDao.insert`）——即条件要防的那种误用。因为是测试夹具，可接受；但「只允许出现在 `BranchDao` 只读映射内」这句话在字面上不成立，而任务把它记为 ✓ 未加限定。**这三处完全可以用真实创建入口**：`BranchSource.fromOperation(root.id, SOURCE_MESSAGE_ID, SNAPSHOT_ID, GenerationState.STABLE, SOURCE_REVISION_ID, BranchAnchor.wholeAnswer())`，既满足条件又能顺带覆盖创建路径。
- **新增的 `Branch.restore`/`Message.restore` 会不会被写路径误用**：命名与可见性**足够**。`restore` 是本包既有惯例（`BranchSource.restore`、`AnswerRevision.restore`），public 是 `:data` 跨模块复用的必要条件；风险在于**这条惯例目前没有任何可执行守门**——`android/README.md:217` 写下了「`BranchSource.restore` 在全 `:data` 只出现这一次」这样一条**永久维持性声明**，支撑它的却只是 `T-022-restore-location.txt` 里的一次性 shell grep。仓库自己的失败案例库要求「按模式识别 + 断言匹配集非空」，建议把这条并入 `android/scripts/` 的守门脚本（含一条「故意多写一处必须判 FAIL」的自检用例）。

### Q3 列集与约束

**一致，且没有自行拼列名。** 逐项核对：

| DAO | 写入列 | 与 schema / §6.1 |
| :--- | :--- | :--- |
| `TopicDao` | `id, last_visited_branch_id, created_at, updated_at` = 4/4 | 一致；`topics` 全部三列 NOT NULL 均覆盖 |
| `MessageDao` | 8/8（含 `user_content` 只在 USER 行写、AI 行显式写 NULL） | 一致；`messages` 全部 NOT NULL 均覆盖 |
| `BranchDao.insert/update` | 16/20 | 差 4 个 `reading_*`（见 M1）+ 1 个 `context_snapshot_id`（有意，已文档化） |

- `grep` 确认 DAO 生产代码里**没有任何列名字面量**，全部引用 `TreeLearnSchema` 常量；表名同样来自常量。
- 读路径查询用 `query(TABLE, null, …)`（`null` = 全部列），不漏列。
- `ContentValues` 的可空写入均为 `Integer`/`Long`/`String` 重载，不会出现「想写 NULL 却写进 0」的问题；`Rows.nullableInt/nullableLong` 也把「NULL vs 0」的判定收在一处。

### Q4 级联与删除语义

- `TopicDao.delete`（`TopicDao.java:56-62`）只发一条 `DELETE FROM topics WHERE id=?`，不手写递归，符合契约。级联链：`topics` → `branches.topic_id` → `messages.branch_id` / `context_snapshots.branch_id` / `generation_operations.*` / `answer_revisions.message_id`，全部 `ON DELETE CASCADE`。**没有发现会漏删的悬挂路径**（`ai_artifacts` 是有意不级联的，且被 `SqliteSchemaInstrumentedTest` 的 `deletingATopicCascadesTheWholeTreeWithoutDanglingRows` 明确断言「不级联、owner 负责清理」并同时断言 `based_on_revision_id` 被置空——这条边界是被钉住的，不是被忽略的）。
- `BranchDao.delete`（`BranchDao.java:94-100`）删单支，子树随 `parent_id` 级联；我确认 T-022 的 `deletingABranchCascadesOnlyItsOwnSubtree` 只覆盖「子分支没有出生快照」的情形，而 T-004 的 `deletingABranchSubtreeCascadesItsDescendantsOnly` 在 T-022 修好夹具后**已经让被删子树带上快照**，因此「分支 ↔ 快照的循环级联 + `TRIGGER_SNAPSHOTS_NO_DIRECT_DELETE` 不误伤合法级联」在真机上被实测通过（37 例里这两条都绿）。这条我不再列为缺陷，但记一条 watch-out：该触发器依赖 SQLite 处理级联时宿主行「已不可见」这一**未文档化**的时序事实，升级 SQLite/API 后应重跑这两条用例。
- **误删边界（L3）**：`branches.source_message_id`/`source_revision_id` 是 `ON DELETE CASCADE`，但 schema 与领域都**不保证** source message 属于同一个 `topic_id`。若某行越过同主题校验（本任务 DAO 不会，但 T-023 的 `execSQL` 夹具或未来 DAO 可能），删掉 topic A 会连带删掉 topic B 里的分支。建议明确记为已知边界（schema 注释 + Gaps），或在 T-023 的写入路径加同主题校验。

### Q5 两步写的契约

**表达基本清楚，但最该说明的那个方法上没有说明。**

- 契约被写在三处：类注释（`BranchDao.java:23-27`）、`setContextSnapshot` 的 javadoc（`:66-74`）、`read` 的 javadoc（`:142-153`，明确写成「调用方义务 + 本类无法观测」），README `:217`/`:226` 也复述了一遍——按「调用方义务必须显式写明」的规则，这是达标的。
- **缺口**：`insert`（`BranchDao.java:34-39`）**完全没有 javadoc**，而它才是调用方第一步调用的方法；契约只存在于读者不会先看的类注释里。
- **fail loud 的行为合理，但异常消息不可诊断**：读到半成品时抛的是 `IllegalArgumentException: contextSnapshotId must be a non-empty UUID string`（来自 `Guard.java:77-93`，经 `BranchSource.java:44`）。它确实拦住了半成品（这正是要的），但调用方从消息里读不出「你违反了 insert → 快照行 → setContextSnapshot 的两步写契约」。建议在 `readSource` 里对 `context_snapshot_id` 为 null 先判并抛出带契约原文的异常。
- 附带风险已被文档化：`DatabaseTransactionRunner` 只能**检测**工作体自行提交（提交已生效），README 与测试都钉住了这个语义；这对两步写是相关的——调用方若吞掉那个 `IllegalStateException`，库里可能留下半成品分支，而 `read` 会 fail loud，不会静默放过。

### Q6 回归风险

- 改 `Branch` 私有构造**在结构上不改变既有行为**：`root`（`Branch.java:76-78`）与 `child`（`:104-106`）都以 `(now, now)` 传入 `createdAt`/`updatedAt`，与修改前把 `updatedAt` 固定为 `createdAt` 等价；`new Branch(` 只有这三个调用点；新增的 `updatedAt >= createdAt` 校验与既有的 `validateStamp` 同义，只会拒绝此前**无法表达**的状态（`BranchTest.java:271-279` 覆盖）。
- **131 例全绿不足以单独支撑 PASS**，理由有三：① 它证明不了「新不变量真的会拦人」——这一条靠新增的 `restoreRefusesAnUpdatedAtOlderThanCreation` 才算证明；② 它不覆盖 `:core:javadoc`，而本仓库恰有「测试绿、javadoc 红」的前例（我补跑后 exit 0，风险已排除）；③ 它完全不覆盖 `:data`（DAO 的覆盖靠 37 例 instrumentation）。因此**判断应以「131 + 37 + lint + javadoc」整体为准**，而 T-022 的证据里缺 javadoc 一项。

---

## 3. 发现清单（按严重度）

### H1【严重】约束放宽超出必要范围，ranged 锚点读路径零覆盖，且存在三种坏故障形态
- **位置**：`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:247-252`（去掉了 `anchor_hash IS NOT NULL`）→ `android/data/src/main/java/com/treelearn/data/db/dao/BranchDao.java:172-186`（`readSource`，尤其 `:175-179`）→ `android/core/src/main/java/com/treelearn/core/domain/BranchAnchor.java:44-72`、`DomainResult.java:51-56`
- **为什么**：见 Q1 的表格。旧 CHECK 连带挡住了「半截偏移」和「有偏移无回退」两类行；去掉后它们变成合法行，而 `readSource` 对它们的反应分别是 **NPE**、**IllegalStateException（而不是 `ANCHOR_INVALID`）**、以及最坏的**静默丢字段**（`start=NULL` + `quote` 非空被当作整条回答分叉读回）。这不是「理论上的坏行」——`BranchDao.java:175-179` 这条 `ofRange` 分支**在任何一条测试里都没被执行过**（13 例里只有整条回答分叉的往返），T-023 的夹具已经在用 `execSQL` 直接写分支行，T-009 之后的写路径同样可能。
- **怎么修**：
  1. 用 §Q1 给出的四字段同生同灭 CHECK 替换 `:249-252`；这样三类坏行在库层即被拒绝，`readSource` 只剩两条合法输入。
  2. 若要保留「库层不判 hash」，则必须在 `readSource` 里显式处理：`(start == null) != (end == null)` → 抛带说明的异常；`start == null` 而 `quote`/`hash` 非 null → 抛异常（不得静默降级）；`ofRange` 返回失败时抛出携带 `error().toString()` 的异常，而不是 `.value()`。
  3. 给 `insert` 的 javadoc 补上两步写义务（与 L2 合并）。
  4. **必须补真实引擎证据**：新增一条 ranged 分叉（`BranchAnchor.ofRange(0, 4, "abcd", "hash")`）的往返用例，以及一条「四字段不全的锚点被库层拒绝、按 `code 275` 断成因」的用例。本报告的 H1 是**静态推导**（我按只读约束没有新增探针），修复时必须以真机用例收口，不能只靠复审。

### H2【中】再水合契约的证据文件不自证且已过期（对应验收条件 7）
- **位置**：`artifacts/evidence/T-022-restore-location.txt:4-19`
- **为什么**：今天用文件里记录的同一条命令复跑，结果对不上：Command 1 记录了 1 条匹配，实测是 **3 条**（`BranchDao.java:18`/`:148`/`:180`——`. ` 在 grep 里是通配符，会命中 javadoc 里的 `BranchSource#restore`）；Command 2 声称「widened to the whole android tree」，却**漏掉了 `BranchTest.java:249` 与 `:283`**（该文件 mtime 16:01:25 晚于证据文件的 15:59:15，即用例在出证据之后又被补过）；Command 4 打印了 16 条匹配，紧接着的结论行却写着「(no output above means the DAO layer emits no DDL)」，**与自己的输出直接矛盾**；四条命令**都没有记录退出码**（对比 `T-022-guard.txt:30` 有 `EXITCODE=0`、`T-022-green.txt` 有 `GRADLE_EXIT=0`）。
- **为什么重要**：结论本身**是对的**（我复跑确认 `:data` 生产源码里 `BranchSource.restore` 确实只有 `BranchDao.java:180` 一处），但验收条件 7 是**唯一**依赖这份证据的条件，而这份证据按它自己记录的命令无法复现——失败案例库已两次命中同类问题（「契约改动后必须同步重生成引用它的证据文件」、「每个数字都要写成从哪个文件的哪行得到，并在写完之后用同一命令回读一次」）。
- **怎么修**：重出该文件：4 条命令各带 `EXITCODE=`；Command 1/2 改用固定语义的模式（`grep -rn 'BranchSource\.restore'` 并**同时**给 `grep -rn 'BranchSource#restore'` 的结果，或统一搜 `BranchSource.{1}[.#]restore`）；删掉 Command 4 那句错误结论（改为「匹配集非空且全部落在 `TreeLearnSchema.java`，DAO 包内零匹配」）；并在命令旁标注被检文件的 SHA-256。

### M1【中】`reading_*` 四列零生产者零消费者，而对应用例名承诺了它没验证的事
- **位置**：`android/app/src/androidTest/java/com/treelearn/app/TopicBranchMessageDaoInstrumentedTest.java:424-437`；列定义在 `TreeLearnSchema.java:82-85`、`:212-217`；`Branch` 无任何对应字段
- **为什么**：`grep` 全仓确认 `reading_anchor_message_id`/`reading_anchor_revision_id`/`reading_list_item_offset`/`reading_viewport_offset` 在**生产代码里没有任何写入者或读取者**（只有 schema 定义、T-004 的列类型断言、以及本条 T-022 用例）。技术方案 §11 明确要求「阅读位置保存 anchorMessageId、可选 anchorRevisionId、列表项内偏移和视口相对偏移」，清单 §2 也把「语义阅读位置」列为 `branches` 的权威事实。而该用例（名字：`…ArePersistedAsOffsetsNotAsScrollState`）实际只跑 `PRAGMA table_info` 断言「列存在」，**从未验证任何持久化行为**，还用字面量列名（与任务自述「列名一律引用常量」相悖）——这是失败案例库「假见证」的形态：用例名给出的信心远超它断言的内容。任务的 Gaps 一节也完全没提这四列。
- **怎么修**：二选一并写进任务文件：① 明确记为**本任务的已知未覆盖面**（阅读位置读写属 T-009，`Branch` 需要先有对应字段），把用例改名为「四列存在且为 INTEGER」并改用 `TreeLearnSchema.Branches.*` 常量；② 或者在本任务补 `BranchDao` 的读写方法（引入领域字段，会扩大范围，需重新评估行数预算）。

### M2【中】schema 字段语义变更未回到建表入场清单/技术方案同步
- **位置**：改动在 `TreeLearnSchema.java:237-248`（只留下代码注释）；权威文档 `Docs/05-technical-design/知芽（TreeLearn）Android SQLite 建表入场确认清单_V1.0.md:25`（`branches` 行）与 `…技术架构方案_V1.0.md:101-112`（§5.2）**零改动**；清单 `:50` 明确写着「任何第八张表、**字段语义变更**或废弃，都须回到本清单重新确认并同步技术方案 §6」
- **为什么**：把 `branches.anchor_hash` 从「非根必填」改成「可空」，是对**已确认永久表**的字段语义变更（空值性即语义），依据是清单 `:50` 必须回清单确认并同步 §6。任务文件的 Documentation impact record 却写着「Documentation surfaces checked: 技术方案 §6.1、清单 §2/§3」，而 `git diff` 显示文档侧只改了 `android/README.md`（README 里也**没有**任何 `anchor_hash`/`CHECK` 的说明，`grep` 为空）——即「已检查」与「已同步」被混为一谈。清单 §4 的确认框至今仍是未勾选的 `[ ]`。
- **怎么修**：在清单 §2 的 `branches` 行「关键索引与约束」补一句「`anchorQuote`/`anchorHash` **仅在 ranged 锚点必填**，整条回答分叉时为空」；技术方案 §5.2 在「整条回答分叉时偏移为空，但版本引用仍必填」后加半句「此时 quote/hash 亦为空」；按清单 `:50` 的要求记录这次更正的确认来源（谁、何时），或在任务文件里明确标注「本条为待用户确认的字段语义修正」。若采用 H1 的补偿约束，一并写进清单（这正是入场记录该承载的内容）。

### L1【低】`theDaoNeverEmitsDdlAndNeverWritesNullableIdentityColumns` 名实不符，且不能充当被删用例的等价覆盖
- **位置**：`TopicBranchMessageDaoInstrumentedTest.java:406-421`；相关自述在任务文件「Changed/deleted assertion rationale」段
- **为什么**：用例名承诺两件事，方法体只做了一件——一段**手工 `db.execSQL("INSERT INTO messages …")`** 撞 `NOT NULL`（`code 1299`）。它既没有验证「DAO 不产出 DDL」，也**完全没有调用任何 DAO 写方法**，因此任务自述「本任务改以该用例覆盖『DAO 不得写出空身份』这一层」不成立；而且它断言的内容与 T-004 既有的 `requiredIdentityColumnsRejectNull` 重复。DAG 层面的「DAO 零 DDL」实际由一次性 shell 检索（即 H2 那份文件）承担。
- **怎么修**：改名以匹配实际断言（如 `theStoreRejectsANullIdentity…`）；若确实要覆盖「DAO 不写空身份」，断言应落在 DAO 上；「DAO 零 DDL」应从一次性检索升级为守门脚本的一条规则（见 Q2）。

### L2【低】`insert` 缺 javadoc，且半成品行的 fail-loud 消息读不出契约
- **位置**：`BranchDao.java:34-39`（无 javadoc）、`:180-185` + `BranchSource.java:44` + `Guard.java:77-93`（异常消息）
- **为什么**：见 Q5。契约写在类注释与第三个方法上，而调用方第一步调用的 `insert` 上什么都没有；读到半成品时抛的是 `contextSnapshotId must be a non-empty UUID string`，无法让调用方意识到自己违反了 insert → 快照行 → `setContextSnapshot`。
- **怎么修**：给 `insert` 加「本方法**有意不写** `context_snapshot_id`；非根分支必须在同一事务内完成 insert → 快照行 → `setContextSnapshot`」；在 `readSource` 里对 null 快照先判并抛出含契约原文的异常。

### L3【低】跨主题误删边界未记录
- **位置**：`TreeLearnSchema.java:220-223`（`source_message_id`/`source_revision_id` 为 `ON DELETE CASCADE`）、`:237-248`（只校验「非根必有版本引用」，不校验同主题）
- **为什么**：删 topic A 会连带删掉任何（越界写入的）以 A 的 message/revision 为 source 的 topic B 分支——属「误删不该删的东西」。本任务 DAO 无法制造这种行，但 T-023 的 `execSQL` 夹具与未来 DAO 可以。
- **怎么修**：在 schema 注释与本任务 Gaps 里明确写出这条边界，并把它交给 T-023 的写入期校验（或一条后续触发器）。

### L4【低】修复只对新装生效：`DATABASE_VERSION` 仍为 1、无迁移
- **位置**：`TreeLearnSchema.java:51`（`DATABASE_VERSION = 1`）、`:490`（`FORWARD_MIGRATIONS` 空）
- **为什么**：CHECK 的修正只走 `onCreate`。任何已经跑过旧 V1 库的设备（开发机/测试机残留库、已安装的调试包）会**永久保留旧 CHECK**，且 `user_version` 仍是 1，无法靠迁移修好——那些设备上「整条回答分叉写不进去」不会被这次修复覆盖。当前 V1 未发布（无 tag、versionCode 1），因此**可以接受**，但必须在首个发行版前明确处置。
- **怎么修**：在任务文件与清单的「发布/升级」栏记下这条限制；首个发行版前若需兼容既有 V1 库，改为 V2 + 前向迁移（SQLite 重建 `branches` 表并复制数据），并保持每次打开显式 `PRAGMA foreign_keys=ON` 的回读校验。

---

## 4. 值得记录的正向发现（避免下一轮重复劳动）

- T-022 顺带**加固了 T-004 的四条空见证断言**，方向与仓库规则一致，应当保留：`onUpgrade` 委托断言的版本对从 `(1,1)`（循环跑零次，空实现也能过）改为 `(1,2)` 并断言 `user_version` 与表集未被污染；`if (visited > 0)` 的永真守卫被去掉、链尾无条件断言到 `DATABASE_VERSION`；pragma 用例先写一行再断言（否则 `foreign_key_check` 在空库上恒真）；`insertChildBranch` 补上出生快照——**旧夹具原来在批量制造「`:core` 永远无法再水合」的行**，这是本轮最有价值的修正之一。
- 新增的「stolen commit 已持久」断言把「runner 只能检测不能阻止」这一反直觉语义钉死，配合 `DatabaseTransactionRunner` 的 javadoc 与 README，是可复用的文档范式。
- `README.md:217-230` 把「DAO 不开事务、不写 DDL、`restore` 只用于读路径、两步写是调用方义务」写成了可检索的入口表——除 M1/L1 的两处名实不符外，文档质量高于本项目平均水平。

---

## 5. 转 PASS 的条件

1. 按 H1 补上 anchor 四字段同生同灭的补偿约束（或等价的 fail-loud 读路径处理），并修掉 `.value()` 丢弃 `ANCHOR_INVALID`、以及 `Integer` 拆箱 NPE。
2. 新增真实引擎用例：ranged 锚点的写入—关闭重开—逐字段读回；以及「四字段不全的锚点被库层拒绝（`code 275`，并断言与两条既有 CHECK 互斥的具体文本以区分成因）」。
3. 按 H2 重出 `T-022-restore-location.txt`（带退出码、按当前树、结论与输出一致）。
4. 按 M2 同步建表入场清单 §2 与技术方案 §5.2，并记录该字段语义更正的用户确认来源。
5. 按 M1 处置 `reading_*` 四列（写进 Gaps 或补实现），并把该用例改名/改用常量。
