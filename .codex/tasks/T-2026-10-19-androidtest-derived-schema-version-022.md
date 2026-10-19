# Codex Harness Task: androidTest 的历史 schema 版本改用派生常量（欠账 D10）

id: T-2026-10-19-022
title: androidTest 两处历史 schema 版本裸字面量改为从 TreeLearnSchema 派生
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-android-phone-usability-requirement-017.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 24
shared_code_touched: false
depends_on: [T-2026-10-18-android-phone-usability-requirement-017.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 017 要求「手机端的每一条完成结论都能回读到一次真实运行或一条会转红的判据」。`check_androidtest_schema_expectations.py` 是这条要求里唯一会静态检查 androidTest 的门禁，而它在盘点时已经是红的——红线不会被跨过，只会被绕开。
- Problem/background: V5 落地时新增的两支 androidTest 用裸字面量标注它们各自模拟的历史版本（`AnnotationDaoInstrumentedTest` 写 `4`、`AttachmentStorageInstrumentedTest` 写 `3`），正好是 T-037 那次病因的复发形态：`DATABASE_VERSION` 再往上跳一次，这两个 fixture 就变成「名字说升级、实际按当前版本建库」的测试，而它仍然打印通过。同一棵树里 `ProviderModelDaoInstrumentedTest` 已经用派生常量避开了同一坑，所以修法不需要新发明，也不需要动守门。
- Users/scenarios: 学习者看不到这条片子。它服务的是「下一次 schema 升版时，升级测试还会不会真的测升级」。
- Core flow and reversibility: 只改两个 androidTest 文件的常量声明与两处断言/建库语句；不碰 `TreeLearnSchema`、不碰 helper、不碰守门白名单。删掉常量、退回字面量即回到本片之前（`artifacts/tmp/d10-redgreen.py` 就是按这个可逆性做的 RED 复现）。
- Inputs/outputs: 输入 = 当前 schema 常量（`DATABASE_VERSION=5`，`FORWARD_MIGRATIONS` = [V1→V2, V2→V3, V3→V4, V4→V5]）；输出 = 两个 java 文件的派生常量版本。
- State and exception handling: 派生表达式的选择写进各自的 javadoc——AnnotationDao 取「最后一条已注册迁移的 `fromVersion()`」（今天=4），AttachmentStorage 取「第二条到最后一条」（今天=3，即它所演示的 V3→V4 那一步的起点）。后者在下次升版时会因 fixture 的 DDL 列表与戳出的版本不符而**大声失败**，而不是安静地测另一件事，这个失败方式在注释里写明。
- Constraints and trade-offs: 守门的 `ALLOWED_USER_VERSION_EXPRESSIONS` 只认三个名字，因此两处都复用既有的 `PREVIOUS_SCHEMA_VERSION`（语义：本条测试所模拟版本 = 它所演示那一步的起点）。代价是同名常量在不同测试类里各指一个版本，换来的是**判定集合一个字节都没放宽**；另一种做法（往白名单里加 `V3_SCHEMA_VERSION`/`V4_SCHEMA_VERSION`）会让「加个名字」成为每次升版的例行放宽点。
- AI recommendation and rationale: 复用白名单名而不放宽白名单。放宽一次，就要为那条放宽补一条负例；负例只能证明「裸字面量仍被拦」，证明不了「新名字是真派生的」——这正是 D4 那族「仪器给自己开后门」的形状。
- User choice and custom additions: 用户要求一站到底、按片提交本地仓库、不中途请示。
- AI-autonomous decisions: ① 不动守门与白名单；② 把类级 javadoc 里「stamps it user_version = 4」改成指向常量（留 `4` 的旧说法会把「代码已派生」这件事说成还没做）；③ 不在无人值守下跑整包 instrumented 套件（会卸载应用、清掉本机数据与 API Key），静态判据 + 离线编译作为本片的覆盖边界并写明。
- User-confirmation decisions: 无新增（沿用 017 已确认范围）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-19-androidtest-derived-schema-version-022.txt` 块 01–05。
- Facts: 同一条命令 `python -X utf8 .codex/scripts/dev/check_androidtest_schema_expectations.py` 在注入回裸字面量时 `VERDICT: FAIL (4 problem(s))` / EXITCODE=1，改回派生后 `VERDICT: PASS` / EXITCODE=0，两次判绿的判定次数同为 `judged 19 PRAGMA user_version expectation(s)`；两个 java 文件按 raw bytes 存取复原，sha 前后同为 `39d31aec49a0` / `f1337ae04169`；守门 `--selftest` 13 条用例全 Ok、`0 broken`（含「裸字面量必须判红」「execSQL 赋值用字面量必须判红」，0/1/2 三档退出码各有用例钉住）；`android/gradlew.bat :app:compileDebugAndroidTestJavaWithJavac --offline --console=plain --no-daemon` 改前改后均 BUILD SUCCESSFUL（rc=0），再加 `--rerun-tasks` 强跑一次，`> Task :app:compileDebugAndroidTestJavaWithJavac` 不带 UP-TO-DATE；`android/scripts/no-kotlin-check.ps1` 在当前树 `VERDICT: PASS (13 OK line(s))`。
- Assumptions: 无（版本数值现场读自 `TreeLearnSchema.java`，不在本文复述其未来形态）。
- Unknowns: 设备上的实际升级路径行为未重测（本片只动测试侧的期望表达式，未动任何生产 SQL 或 helper）。
- Risks: `PREVIOUS_SCHEMA_VERSION` 一名两义（各自类内私有）；已在两处 javadoc 写明各自推导来源，读代码的人不会把 4 读成 3。
- Deferred: 把「白名单里的名字必须在 androidTest 里有派生 initializer」做成守门判据（本片不做，因为它要新增判据而不是消红线）。
- Non-goals: 不改 `TreeLearnSchema`、不改 `TreeLearnDatabaseHelper`、不改守门白名单、不跑设备冒烟。
- Skipped non-critical questions and reason: 未追问是否把两个 fixture 版本收成一个共享测试工具类——那会把两条独立病因合出一条新的（一处改坏两处失真），本片按类内私有常量各自派生。

## Goal

让 androidTest 里两处历史 schema 版本的期望表达式由 schema 常量派生，使 `check_androidtest_schema_expectations.py` 在当前树判绿，且不放宽它的判定集合。

## Scope

### In scope
- `android/app/src/androidTest/java/com/treelearn/app/AnnotationDaoInstrumentedTest.java`：新增 `PREVIOUS_SCHEMA_VERSION`（取 `FORWARD_MIGRATIONS` 最后一条的 `fromVersion()`），`execSQL` 戳版本与回读断言都用它；类级 javadoc 同步。
- `android/app/src/androidTest/java/com/treelearn/app/AttachmentStorageInstrumentedTest.java`：新增 `PREVIOUS_SCHEMA_VERSION`（取倒数第二条的 `fromVersion()`，即它所演示的 V3→V4 那一步的起点），两处同上。

### Out of scope
- `check_androidtest_schema_expectations.py` 的白名单、判据与自检用例；`TreeLearnSchema` 与 helper 的任何改动；设备上的 instrumented 套件整跑。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 运行时用户可见行为零变化（改的是 androidTest 源）。要保留的旧行为是「fixture 模拟 V4 / V3 数据库并由生产 helper 真跑 onUpgrade」——派生常量今天仍分别等于 4 与 3，所以断言对象与升级路径不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `TreeLearnSchema.FORWARD_MIGRATIONS`（`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java:874`，公开只读）；消费者是本片两个测试类；同一表达式的既有同类用法在 `ProviderModelDaoInstrumentedTest.java:58`（先例，已在真机跑过并被 T-2026-09-23-083 接受）。守门侧消费者 `check_androidtest_schema_expectations.py:112` 的白名单未改。Java/Kotlin 之外的平台分支：无。
- Native bridge or independent-client compatibility/update requirements: 无桥接或独立客户端契约变化；不改 SQL、不改数据库版本常量。
- Behavior-neutral non-applicability reason, if applicable: 无（本片确实改了一条门禁的读数）。

## Documentation impact record

- Changed runtime facts: 无运行时事实变化；变的是一条静态门禁的读数（红→绿）与两处测试期望表达式的写法。
- Authoritative code/config/migration evidence: `TreeLearnSchema.java:874`（`FORWARD_MIGRATIONS` 列表）、`AnnotationDaoInstrumentedTest.java:65-72`、`AttachmentStorageInstrumentedTest.java:58-66`、`check_androidtest_schema_expectations.py:107-116`（白名单，未改）。
- Documentation surfaces checked: `.codex/ROADMAP.md`（新增 D10 条目并写明回读命令）、`Docs/spec/**`、`Docs/05-technical-design/**`、`.codex/rules/sql.md`——后三者按「schema 版本期望必须由常量派生」的措辞反查，无命中，故无需回写。
- Documentation files updated: `.codex/ROADMAP.md`（D10 处置与回读）。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact 的 code evidence 行。
- Superseded phrases/examples reverse-searched: `PRAGMA user_version = 4`、`PRAGMA user_version = 3`、`assertEquals(4, rawInt`、`assertEquals("the fixture must look like V3", 3` 在 `android/**` 全文反查为 0 命中；历史任务卡与证据里的旧写法不回改（那是当时的捕获）。
- `documentation impact: none` reason and evidence, if applicable: 不适用，ROADMAP 有 D10 回写。
- Historical/PRD handling: `artifacts/evidence/T-037-*` 里那次同类红线的记录保持原样。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/dev/check_androidtest_schema_expectations.py` 在当前树退出码 0 并打印 `VERDICT: PASS`，判据行 `judged 19 PRAGMA user_version expectation(s) in androidTest`。证据 = 块 01（首末两次 GREEN 的判定次数同为 19，绿不是少判换来的）。
- [x] 同一条命令在把两处常量退回裸字面量后判红（4 条、逐条点名到文件），`EXITCODE=1`，且注入文件按字节复原后 sha 与注入前相同（`39d31aec49a0` / `f1337ae04169`）——证明红线可复现、恢复不是 `git checkout` 的重新 smudge。证据 = 块 01、驱动 `artifacts/tmp/d10-redgreen.py`（自身 rc=0）。
- [x] 守门自身的 `--selftest` 13 条用例（含「hardcoded user_version literal must FAIL」「an execSQL user_version assignment by literal must FAIL」）全部 Ok、`0 broken`，且 0/1/2 三档退出码各被一条用例钉住，证明判定集合没被放宽。证据 = 块 02。
- [x] `android/gradlew.bat :app:compileDebugAndroidTestJavaWithJavac --offline --console=plain --no-daemon` 在改前形态与本片实现各 BUILD SUCCESSFUL（rc=0），另以 `--rerun-tasks` 强跑一次，任务行不带 `UP-TO-DATE` 证明确实执行——派生表达式在 androidTest 编译期可用（`Migration.fromVersion()` 是公开接口方法），不需要为它放宽可见性。证据 = 块 03、驱动 `artifacts/tmp/d10-compile.py`。
- [x] 覆盖边界写明：未在真机重跑这两支 instrumented 测试（整包会卸载应用、清学习者本机数据与 API Key），设备侧证据仍停在 T-2026-09-23-083。证据 = 块 05，与本卡 Gaps 同一口径。

## TDD execution record

### RED — failing test first
- Test/scenario: 把 `AnnotationDaoInstrumentedTest` 与 `AttachmentStorageInstrumentedTest` 的派生常量退回裸字面量 `4` / `3`（4 处替换全部命中），跑静态守门。
- Command: `python -X utf8 .codex/scripts/dev/check_androidtest_schema_expectations.py`
- Exit code: 1
- Observed failure: `VERDICT: FAIL (4 problem(s))`，四条均为 `a PRAGMA user_version expectation does not name a constant (expected expression: "PRAGMA user_version = 4" / 4 / "PRAGMA user_version = 3" / 3)`。
- Failure reason validated: 失败点正是本片要消除的形态（历史版本用裸字面量标注），不是缺失输入或解析失败——同一条命令在复原后判绿，且 `--root` 换到临时树时仍按 `INCONCLUSIVE`(2) 报输入缺失，三种退出码互不混用。

### GREEN — minimum implementation
- Implementation: 两个测试类各加一个类内私有的 `PREVIOUS_SCHEMA_VERSION`（由 `TreeLearnSchema.FORWARD_MIGRATIONS` 派生），`execSQL` 的版本戳与 `rawInt` 回读断言都改为引用它；AnnotationDao 的类级 javadoc 把 `user_version = 4` 改述为引用该常量。
- Command: `python -X utf8 .codex/scripts/dev/check_androidtest_schema_expectations.py`
- Exit code: 0
- Passing evidence: `VERDICT: PASS`，判据行 `judged 19 PRAGMA user_version expectation(s) in androidTest`（注入前后同为 19 次判定，说明没有少判任何一处）。
- Regression evidence: 守门 `--selftest` 13 条用例全 Ok；`gradlew :app:compileDebugAndroidTestJavaWithJavac --offline` BUILD SUCCESSFUL。

### REFACTOR — optional
- Cleanup: 无额外重构——注释按「为什么这样派生」各写一段，不复述判据本身。
- Regression evidence: 与 GREEN 同一条命令。

## Implementation notes

- 复用白名单里的 `PREVIOUS_SCHEMA_VERSION` 而不是新增名字：新增名字等于放宽判定集合，而放宽点会随每次升版增长。
- 两处派生式不同（`size()-1` 与 `size()-2`）是刻意的：各自对应本条测试所演示的那一步迁移，注释写明了升版后不匹配时的失败方式（大声失败而非安静换对象）。
- 复原用 raw bytes 而不是 `git checkout`：本工作树 `core.autocrlf=true`，checkout 会重新换行，取证时看到的字节和复原后的不是同一份（片 021 的 块 07 教训）。

## Verification report

- Verdict: PASS（同一条命令的注入/还原字节证明 + 守门 13 例正反双向 + androidTest 强制重编译 + Java-only 守门当前读数；设备侧未重跑，见 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-androidtest-derived-schema-version-022.txt` 块 01（RED/GREEN 同命令对照 + 字节复原）、块 02（守门 13 条自检）、块 03（离线编译改前/改后）、块 04（Java-only 守门）、块 05（覆盖边界）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 没有删除或改宽任何断言；被改的是两处期望表达式的**来源**（字面量 → schema 派生），断言的对象（回读到的 `user_version`）与升级路径（生产 helper 的 `onUpgrade`）不变。
- Gaps: ① 未在真机重跑这两支 instrumented 测试——整包 instrumented 套件会卸载应用并清掉本机数据与 API Key，属不可回退的用户代价，不在无人值守范围。静态判据 + 编译期证明覆盖「表达式可用且派生」，不覆盖「设备上升级仍成立」。② 块 01/03 的注入驱动放在 `artifacts/tmp/`（gitignore 内），一次性对照在别的克隆上跑不出来；长期复算靠已入库的守门本身（它的 13 例自检在任何克隆都能跑）。③ 未做独立 verifier 复核轮次（用户要求一站到底；本片改的是测试源，判据全部可现场复算）。
- Risks: 若未来有人把 `FORWARD_MIGRATIONS` 的顺序当作可变列表（例如插入一条 V3.5），`size()-2` 的含义会漂移；那会让 fixture 的版本与 DDL 不匹配并大声失败，属可接受的失效方向。

## Handoff notes

- Completed: 两处裸字面量改为派生常量；静态守门在当前树判绿且红线可复算；守门 13 例自检、androidTest 强制重编译、Java-only 守门读数均已留证；证据文件 `artifacts/evidence/T-2026-10-19-androidtest-derived-schema-version-022.txt`（块 01–05）；ROADMAP 新增 D10 条目并写明回读命令。
- In progress: 无（本片已收口）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 这条守门已由片 023 收进 `run_all_guards.py` 的 core 档（判据行 `VERDICT:`），此后每片提交前整跑 core 就会带上它；需要真机时由用户在场单独跑 `AnnotationDaoInstrumentedTest` 与 `AttachmentStorageInstrumentedTest` 两支（不要整包跑）。
- Watch-outs: 不要再往 `ALLOWED_USER_VERSION_EXPRESSIONS` 里加名字来「消红」——那条路每加一个名字就少一个失败点。
