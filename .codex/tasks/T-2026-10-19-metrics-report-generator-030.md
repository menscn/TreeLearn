# Codex Harness Task: 把「这阵子是在变快还是变慢」变成一条命令读得出的报告（ROADMAP B6 前半）

id: T-2026-10-19-030
title: B6 指标面：metrics_report 生成器、报告落点与手抄数字的反证
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 317
shared_code_touched: false
depends_on: [T-2026-10-19-eval-keeper-baseline-role-029.md, T-2026-10-19-evals-inventory-and-runner-028.md, T-2026-10-19-guard-runner-one-command-roster-023.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 要求「完成结论必须匹配证据覆盖」，并要求规则/文档同步回归说明。ROADMAP B6 要的是一页
  能看的交付报告，而它那句验收方式里最硬的一条是**不接受手抄数字**。片 030 把这一条从主张变成判据：报告里
  每一个声明可机检的数，都必须由**执行它自己那一行印出的命令**复现出来；判不过就转红。角色面（`metrics-reporter`
  两份正文 + 成对守门）留到片 031，两侧判据面严格分开。
- Problem/background: 本仓库有一整套「产物有没有变差」的仪器（片 023 的注册表、片 028/029 的评测面），却没有
  任何一处把**交付本身**的量汇总起来：提交节奏、卡片退回/等待裁决的状态分布、注入用例在家族间的命中分布、
  失败案例库的增量，全都散在 `git log`、`.codex/tasks/` 与 `checkers_selftest.py` 里，靠人想起来才数一次。
  散着的第二个问题是口径不可复算：同一件事既可能按物理行数也可能按条目数，写完的读数下次没人能复现。
  本片真实撞到的两处，都记在 TDD 记录里：① 执行式判据落地后立刻抓到生成器自己两处过期（注册表 36→37、
  基线 10→11），说明这条判据不是摆设；② 给 `README_ENTRIES` 加第 6 项之后，`roadmap:readme-entries-drained`
  的旧锚点（删 2 项）不再破下限，整轮自检以 `CASE 39 MISMATCH … rc=0` 报**用例不匹配**而不是判据红。
- Users/scenarios: 使用者是每一片收尾时的主会话（尤其是单人项目里「我是不是在原地打转」这个问题的提问者）。
  场景三个：① 想看最近的交付节奏 → `--write` 生成后读 `artifacts/metrics/report.md`；② 怀疑某个数是手抄的 →
  跑只读判定，那条「执行印出的命令」的判据把它揪出来；③ 只想复算一个指标 → `--read <指标名>` 打印现场读数。
- Expected outcome: `.codex/scripts/metrics_report.py`（417 行 / 18 条判据 / 12 个指标 / 4 个家族，默认只读判定、
  `--write` 生成、`--read` 单指标复算）+ `artifacts/metrics/report.md`（20 行，含表头与口径说明）+
  `checkers_selftest.py` 的 19 例 `metrics:*`、第 11 条基线与 4 个常量 + `build_workspace` 定点带报告正文那一段 +
  core 档注册两行 + `README_ENTRIES` 登记判据与 `.codex/README.md` 一行 + ROADMAP B6 的 `范围`/`依赖`/`验收方式`
  改写与 `交付任务`/`实现要点` 回写 + 片 021/024/025/026/027/028/029 七张卡的行号指针复算 + 基线快照重转录。
- Core flow and reversibility: 新增一支生成器与一份报告产物，改四处既有条目（基线元组、core 档注册、
  README 登记清单与那一句、ROADMAP B6 一节），刷新一个快照，改一段副本构建逻辑。回退方式：删
  `metrics_report.py` 与 `artifacts/metrics/`、撤 `run_all_guards.py:99-100`、撤 `checkers_selftest.py` 的
  19 例与第 11 条基线与那 4 个常量、把 `README_ENTRIES` 的第 6 项与 `.codex/README.md:32` 那半句还原、
  ROADMAP B6 五行回退为原三条，即回到片 029 末态。快照重转录与七张卡的行号改写**不属**该回退面：
  它们是复算出来的事实，回退它们等于把活指针留在原地。
- Inputs/outputs: 输入 = `.codex/ROADMAP.md` 的 B6 目标句（家族来源，正则现场派生）、`.codex/tasks/*.md`、
  `.codex/references/regression-cases.md`、`.codex/scripts/dev/checkers_selftest.py`、
  `.codex/scripts/run_all_guards.py`、`git log` / `git rev-list`。输出 = 18 行 `OK/FAIL` +
  `check_metrics_report: N check(s) failed` + `EXITCODE=0/1`；`--write` 另输出报告文件本体。
- Constraints and trade-offs: 只用标准库；同一条命令必须既能当判定又能当基线（默认模式取只读判定，
  否则它进不了 `BASELINES`）；报告落在仓库根 `artifacts/` 下，而自检副本只镜像 `.codex` 与 `Docs` 的正文，
  所以要么给副本加定点拷贝、要么把报告搬进 `.codex/`——选了前者，代价是 `build_workspace` 多一段特例。
  第二个代价是 `复算=否` 这一类：git 历史读数在自检副本里必然不同（副本是一份新 init 的历史），
  与其让它每轮假红，不如显式声明「这类只判形状、不判值」，并把理由写在报告表头而不是藏进代码注释。
- AI recommendation and rationale: 报告的判据重心放在「复算」而不是「有没有写口径」：一条主张性的
  「每个数字都要写清来源」正则数得出来，但手抄一个错数照样绿。所以判据是**执行印出的命令取回读数**，
  并给它配一条自己的注入用例（`metrics:hand-copied-reading`）；口径唯一性、字段齐全、家族派生、
  登记表与报告行数互钉各配一条，缺一侧就只剩自我声明。
- User choice and custom additions: 用户要求一站到底、按片提交本地、不中途请示；未对本片提出额外定制。
- AI-autonomous decisions: ① B6 拆两片（指标面 030 / 角色面 031），沿用 B2 的 028/029 分法，两侧判据不重叠，
  并用 RED-3（抽掉 `.codex/README.md` 的登记行 → 路线图守门红而指标守门仍绿）把「不重叠」读出来而不是声明；
  ② 家族表不写死在脚本里，从 ROADMAP B6 目标句 `把…汇总成一页报告` 正则派生（单一权威），ROADMAP 改词
  脚本立刻跟着转红，这是 `metrics:roadmap-family-dropped` 的用途；
  ③ `退回次数` 这一族没有结构化登记面（卡片 `status` 只有三态，裁决散在正文里），改用两个代用信号
  （`Verdict: PENDING` 的卡片数、写了「无独立 verifier 轮次」的行数）并在口径栏里明写它们是代用而不是退回次数本身；
  ④ 历史类读数标 `复算=否` 而不是硬造一个「在副本里也能复算」的假机检；
  ⑤ 占位符判据只扫表头与读数，不扫口径——口径栏要复述被数的那件事，里面合法出现任务卡里真实存在的
  状态词字面量，全文扫会把真话判成占位（本片写完 B6 回写后，路线图守门正是用同一条判据把我写进 ROADMAP 的
  那个字面量判红的，见块 01 的 RED-4）；
  ⑥ `metrics_report.py` 一支文件承担生成 + 判定 + 单指标复算三件事，不拆成两支，因为拆开就要在两份文件里
  各存一份指标登记表——那正是本片要防的第二份权威。
- User-confirmation decisions: 无新增需拍板项（不改产品口径，只新增流程资产与仪器）。
- Acceptance evidence: 块 00（指标守门末态 18 行逐字）、块 01（RED/GREEN 八断言 + 字节还原 sha256-12 +
  两半不重叠对照 + ROADMAP 回写误落占位词的那一次真实转红）、块 02（174 例注入含本片 19 例 `metrics:*` 逐字 +
  11 条基线 + 锚点失效那轮的 `CASE 39 MISMATCH` 原始读数）、块 03（`run_evals` / `--list` / `--tier core` /
  `validate_tasks`）、块 04（`文件:行` 指针复算，含本片改回的七张卡区间）、块 05（全库活引用扫描）、
  块 06（快照刷新读数、本片尺寸、`android/**`/`frontend/**` 零命中）。
- Facts: `registered=12 floor=8`、`derived=4 families=['交付节奏', '退回次数', '守门命中分布', '失败案例库增量']`、
  `值=9;否=3;total=12`、`entries=37;core=31;precommit=6`、`baseline-checkers=11`、
  `SELFTEST cases=174 ok=174 mismatch=0 harness_errors=0 skipped=0`、
  `RUN-ALL-GUARDS pass=31 fail=0 inconclusive=0 fault=0`、`POINTERS checked=130 stale=0`、
  `CITATIONS files=16 citations=90 problems=0`、`changed_lines=[] zero_diff_with_source=True`、
  `PRODUCTION_LINES total=317` 均为一次真实执行的打印。
- Assumptions: 交付节奏以 git 的 AuthorDate 为准（不是提交时间戳）；`.codex/tasks/*.md` 仍是任务状态的唯一
  登记面；`regression-cases.md` 是失败案例库的现居地（ROADMAP 原条目写的 `AGENTS.md` 已按当前工作树更正）。
- Unknowns: 第二个指标家族要不要引「每片耗时」这种需要时间戳配对的量——需要卡片时间戳与提交时间对齐，
  本片不做判断，留给角色面第一次真实接单。
- Risks: 报告里 9 个机检读数与仓库现状绑得很紧，任何一片收尾后不重跑 `--write` 就会在下一轮的
  「执行印出的命令」判据上转红（方向正确，代价是 `--write` 要进每片收尾清单）；`checkers_selftest.py` 继续长下去
  会让 19 例 `metrics:*` 的锚点区间再次漂。
- Deferred: ① 「报告必须定期重生成」目前是主会话的动作，没有机械触发点——角色面（片 031）把它写成契约；
  ② `复算=否` 那一类在两次 `--write` 之间会静默过期，本片只在表头声明这件事，不假装能判住它。
- Non-goals: 不做角色正文与成对守门（片 031），不改运行器的 18 条判据与裸下限，不动历史卡片的结论，
  不宣布任务验收。
- Skipped non-critical questions and reason: 未问「报告要不要出 CSV / 图表」——一页 markdown 已被 B6 目标句
  限定为「汇总成一页报告」，加一种格式就是加一份要被复算的产物，而本片要减的是散着的数。

## Goal

让「交付节奏、退回信号、守门命中分布、案例库增量」这四族量有一条命令能读得出、并且每个数都能被同一条命令
复算：`python -X utf8 .codex/scripts/metrics_report.py` 退出码 0，18 条判据逐条打印读数，其中「报告每指标一行且
六字段齐」「家族从 ROADMAP 派生」「机检读数必须由印出的命令复现」「历史类读数真的带 git 命令」各有注入用例兜着。

## Scope

### In scope
- `.codex/scripts/metrics_report.py`：12 个指标注册表 + 18 条判据 + `--write` / `--read` 三种模式。
- `artifacts/metrics/report.md`：由该脚本生成的一页报告（表头声明生成命令、家族来源、单指标复算方式、
  窗口口径与 `复算=否` 的含义）。
- `.codex/scripts/dev/checkers_selftest.py`：4 个常量、第 11 条基线、19 例 `metrics:*`、
  `build_workspace` 定点拷 `artifacts/metrics/`、`evals:pointer-stale` 与 `roadmap:readme-entries-drained`
  两条锚点跟随现状。
- `.codex/scripts/run_all_guards.py` 的 core 档注册（两行）；`.codex/scripts/check_roadmap_and_routing.py` 的
  `README_ENTRIES` 增项与那条「清单不许被抽干」的下限说明。
- `.codex/README.md` 权威边界一句；`.codex/evals/cases/001-impact-inventory/product.md` 重转录；
  `.codex/ROADMAP.md` B6 的 `范围`/`依赖`/`验收方式` 更正与 `交付任务`/`实现要点` 回写；
  片 021/024/025/026/027/028/029 七张卡的行号指针复算。

### Out of scope
- `metrics-reporter` 角色正文、`.codex/scripts/check_metrics_reporter_agent.py`、`.codex/HARNESS.md` 接线（片 031）。
- 评测运行器与 `eval-keeper` 的判据面（片 028/029 的面，本片只重转录了快照内容，未改判据）。
- 「每片耗时」这类需要跨产物时间戳配对的指标，以及任何需要新依赖的图表输出。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图 = 四族交付量有一页可复算的报告，且「不接受手抄数字」
  有一条会响的判据。保留 = 片 028/029 的运行器与角色守门逐字读数（块 03 与片 029 证据对照，`parsed=9` /
  `pointers-alive citations=2 problems=[]` / `paths-exist cited=5 dead=[]` 一字未变）、
  `run_all_guards.py` 其余注册项（条数随每片变化，读数由块 03 现场取）、`HARNESS.md` 第 1/2/3/4/5/6 步原文
  （本片没有接任何线）、`checkers_selftest.py` 原有 155 例与 10 条基线一字未改（只追加，另两条锚点跟随现状）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 = 指标注册表与
  `build()`；消费者 = `run_all_guards.py:99-100`（core 档）、`checkers_selftest.py:78-80`（第 11 条基线所在元组）、
  `artifacts/metrics/report.md`（人读的那一页）；同级入口 = `check_eval_keeper_agent.py` /
  `check_code_reviewer_agent.py`，它们与本片共用 `harness_check_lib` 的在场性与占位符判定，但指标注册表是
  本片独有的第二份「集合」——所以家族不另写一份，从 ROADMAP 目标句派生。两个**本片之前就在场**的消费方：
  `check_roadmap_and_routing.py:35-36` 的 `PHASE_B_KEYWORDS`（B6 正文被删词立刻转红）与
  `.codex/README.md:32` 的登记行（本片把它作为 RED-3 的抽离对象，见块 01）。平台分支 = 不适用：
  无 Android/前端生产代码、无 API、无 SQL；`复算=值`/`复算=否` 是**输出格式要求**，不是运行时分支。
- Native bridge or independent-client compatibility/update requirements: 不适用（无桥接字段被触碰；
  `frontend/` 与 `android/` 的代码一行未改，判定形状只作用于 `.codex/` 内的流程资产与一页报告）。
- Behavior-neutral non-applicability reason, if applicable: 可反驳证据两条：`git diff --stat HEAD` 里
  `android/**`、`frontend/**` 零命中；以及块 03 的 `run_evals` 逐字读数与片 029 证据相同。

## Documentation impact record

- Changed runtime facts: 未改产品运行事实；新增一页交付指标报告与其生成/判定器，并把 B6 的依赖从
  `AGENTS.md` 的失败案例库更正为 `.codex/references/regression-cases.md`（案例库现在住在这里）。
- Authoritative code/config/migration evidence: `.codex/scripts/metrics_report.py:34-45`（六份输入路径与
  `FIELDS` 六字段形状）、`:49-52`（四条裸下限，`MIN_FAMILIES = 4` / `MIN_METRICS = 8` / `MIN_RECHECKABLE = 6` /
  `TOP_DAYS = 3`）、`:54-62`（家族派生与表格形状正则，含跳过分隔行的 `SEPARATOR_RE`）、
  `:84-155`（12 个读数函数，一个指标一个）、`:161-200`（指标登记表：家族、口径、复算类别、单位一次声明）、
  `:202-213`（`metric_command()` 回落到 `read_command()`，报告印的就是这里）、`:214-240`（`build()`：表头 + 逐行）、
  `:241-260`（`parse()`：把报告读回结构化行，判据面读它而不是读内存里的登记表）、
  `:261-277`（`fresh_reading()` 与 `run_printed_command()`：执行印出的命令）、
  `:278-390`（18 条判据）、`:391-417`（三模式入口）；
  `.codex/scripts/dev/checkers_selftest.py:68-71`（4 个常量）/ `:78-80`（11 条基线元组）/
  `:675-737`（19 例 `metrics:*`）/ `:875-879`（副本定点带报告正文）/ `:282-285`（抽干锚点跟随清单形状）/
  `:575-578`（`evals:pointer-stale` 锚点跟随快照）；`.codex/scripts/run_all_guards.py:99-100`、
  `.codex/scripts/check_roadmap_and_routing.py:39-44`、`.codex/README.md:32`、
  `.codex/ROADMAP.md:112-119`（B6 五行回写；片 032 在 B0 加了一行，区间随之下移）、`artifacts/metrics/report.md:1-20`、
  `.codex/evals/cases/001-impact-inventory/product.md:1-13`（重转录后的快照，`:5` 仍是那条会漂的指针）。
  行号是先实测再落笔的：`artifacts/tmp/b030-pointers.py` 把本片与片 021/024/025/026/027/028/029 的 130 条引用
  逐条复算，末态 `POINTERS checked=130 stale=0`（表自身有 `MIN_POINTERS = 120` 的裸下限，抽干即红）。
  其中本片把七张卡里 12 处 `checkers_selftest.py` / `run_all_guards.py` / `harness_check_lib.py` /
  `check_roadmap_and_routing.py` 的区间引用按现值改写——改写前它们是片 029 当时复算出来的正确值，
  被本片插入的 4 个常量与 19 例整体下推后成了活指针。
- Documentation surfaces checked: `.codex/ROADMAP.md`（B6 全节 + B2/B3/B4/B5 的相邻条目 + Phase C 编号说明）、
  `.codex/HARNESS.md`（六步流程，本片未改）、`.codex/README.md`（权威边界与目录结构）、
  `.codex/evals/README.md`（期望语法权威正文，未改）、`.codex/agents/eval-keeper.md`（成对角色面的写法基线）、
  `.codex/rules/regression.md` 与 `.codex/rules/maintainability.md`（抄检窗口与「被检集合不许给自己定下限」）、
  `.codex/references/regression-cases.md`（案例库现居地，也是 `失败案例库增量` 那一族的被数对象）、
  `AGENTS.md`（第 62 行 glob 已覆盖新脚本，未改）。
- Documentation files updated: `.codex/README.md`（权威边界一句加 `artifacts/metrics/`）、
  `.codex/ROADMAP.md`（B6 的 `范围`/`依赖`/`验收方式` 按当前事实更正，新增 `交付任务` 与 `实现要点` 两行）、
  片 021/024/025/026/027/028/029 七张卡的行号指针、
  `.codex/evals/cases/001-impact-inventory/product.md`（快照重转录，源小节里那句「其余 32 条注册项」
  同时改成不复述条数的写法）。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/metrics_report.py` 退出码 0，18 条 `OK`（块 00），其中
      `registered=12 floor=8`、`derived=4`、`measured=9 floor=6`、`lines=12 duplicated=[] hand_added=[] missing_from_report=[]`。
- [x] 报告文件缺席时同一条命令退出码 1，FAIL 标签是 `metrics report file exists` 而
      `the report landing directory exists` **仍然 `OK`**（块 01 RED-1）：删文件与删目录是两件事，各有一条判据。
      按原始字节摆回后同一条命令 `0 check(s) failed` / `rc=0`，还原前后 `4dec79a38083` / `0fc0b71528da`
      两个 sha256-12 相同（块 01，驱动 `artifacts/tmp/b030-redgreen.py`，gitignore 内；不用 `git checkout --`，
      那会把 LF 换成 CRLF）。
- [x] 手抄数字会转红，且红得能自证：把报告里 `library-entries` 的 `total=19` 手改成 `total=20` →
      `FAIL each machine-recheckable reading is produced by running the command on its own line — checked=9
      mismatch=[['library-entries', 'rows=13;headings=6;total=20', 'rows=13;headings=6;total=19']]`（块 01 RED-2）。
      报告里写的数与现场执行的数**同时打印**，所以不需要读者再跑一遍才知道差在哪。
- [x] 两条命令不互相冒充：抽掉 `.codex/README.md` 的 `artifacts/metrics/` 登记行时
      `check_roadmap_and_routing.py` 转红（`FAIL 目录结构 section lists every registered entry —
      missing=['Docs/intent/', 'artifacts/metrics/']`）而 `metrics_report.py` **仍然 `rc=0`**（块 01 RED-3）。
      这是「判据面不重叠」的读数，不是声明。
- [x] 家族不许在脚本里另抄一份：`metrics:roadmap-family-dropped` 删掉 ROADMAP 目标句里的一个家族名 →
      `FAIL metric families derive from the ROADMAP B6 goal sentence — derived=3 floor=4`（块 02 CASE 159）；
      报告里把家族改名 → `FAIL every family required by the roadmap appears in the report —
      required=4 missing=['交付节奏']`（CASE 165）。派生侧与产物侧各一条，缺一侧就只剩自我声明。
- [x] 报告与登记表互相钉住：删一行 → `lines=11 … missing_from_report=[…]`（CASE 162），
      把一行改成不认识的指标名 → `hand_added=['library-entries…']`（CASE 161），
      六字段改一个 → `bad=['tasks-status-counts']`（CASE 163），同一行塞两个单位 →
      `duplicated_fields=[['total-commits', ['口径']]]`（CASE 164）。
- [x] 命令栏不许是空的、也不许是装饰：删掉命令 → `blank=['total-commits']`（CASE 166），
      把印出的命令换成别的 → `wrong=[['library-entries', …]]`（CASE 170），
      历史类读数换成非 git 命令 → `not_git=['total-commits']`（CASE 167）。
- [x] `复算=` 标签与注册表实际类别一致：单行换标签 → `disagree=[('cards-by-file-date', …)]`（CASE 168），
      全表翻面 → `FAIL enough metric lines are declared machine-recheckable — measured=0 floor=6`（CASE 169）。
- [x] 读数不是占位符：`读数=待填` → `hits=['待填'] scanned=readings+header`（CASE 173）；
      表头的生成命令被改 → `present=False`（CASE 174）。
- [x] 报告过期有牙（这条是本片最想要的性质，也被自己的两处过期证明过）：源在被判之下漂移时
      `metrics:source-drifted-under-report` 往案例库追加一行 → 复算差值当场打印 `total=19` vs `total=20`
      （CASE 172）；生成器自己两处过期（注册表 36→37、基线 10→11）是同一判据在真实交付中先响过两次。
- [x] 裸下限承重：登记表下限被抬到 99 时 `registered=12 floor=99` 转红（CASE 160），
      指标源目录被删时 `missing=['.codex/references/regression-cases.md']` 转红（CASE 158），
      报告目录被删时 `the report landing directory exists` 转红（CASE 156）。
- [x] `checkers_selftest.py` → `cases=174 ok=174 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS`，
      11 条基线全 `BASELINE OK rc=0`（含新升的 `metrics_report`）（块 02）。
- [x] 仪器被自己的改动改坏过一次并被整轮抓到：加 `README_ENTRIES` 第 6 项那一轮
      `run080-selftest.txt` 打印 `CASE 39 MISMATCH roadmap:readme-entries-drained - expected FAIL label, rc=0, seen=[]`
      与 `ok=173 mismatch=1 / OVERALL: FAIL`；锚点改成删整行后同一轮 `174/174`（块 02 两侧读数都在）。
- [x] 快照刷新后 `run_evals` 仍绿且复算的是新位置：`parsed=9`、`pointers-alive citations=2 floor=2 problems=[]`、
      `paths-exist cited=5 dead=[]`，与片 029 证据逐字相同；快照由 `artifacts/tmp/b030-refresh-snapshot.py`
      从片 026 源小节重转录（`zero_diff_with_source=True`，`8d9575841245`）（块 03 / 块 06）。
- [x] `run_all_guards.py --list` 读数 `entries=37 core=31 precommit=6`，`--tier core` 整跑
      `pass=31 fail=0 inconclusive=0 fault=0` / `VERDICT: PASS`，且 `metrics_report` 打印自己的判据行（块 03）。
- [x] `validate_tasks.py` → `SUMMARY files=25 errors=0 warnings=5`：第五条 WARNING 是本片的
      `estimated_production_lines=317` 超软上限 200（>500 才算失败），其余四条与片 029 相同，都是本片之前
      就已超预算的历史卡片（块 03）。
- [x] 本片没把任何既有句子的行号引用改坏：指针驱动末态 `stale=0`，其中本片把片 021/024/025/026/027/028/029
      七张卡的区间引用按现值改写，改写后的每一条都由驱动复算（块 04）。
- [x] 文档侧活引用全库扫描 `CITATIONS files=16 citations=90 problems=0` / `VERDICT: PASS`（块 05）。

## TDD execution record

### RED — failing test first
- Test/scenario: 判据写完、报告产物还没落地——即「有一支指标守门，但它要判的那一页不存在」这一状态。
  跑守门，看它是否把「被检产物缺席」报成红而不是无话可说的绿，并且**不要**顺带把目录判据也报红。
- Command: `python -X utf8 .codex/scripts/metrics_report.py`
- Exit code: 1
- Observed failure: `FAIL metrics report file exists — path=artifacts/metrics/report.md`，紧跟
  `check_metrics_report: 1 check(s) failed` 与 `EXITCODE=1`；同一轮里
  `the report landing directory exists` 仍打印 `OK`（块 01 RED-1 逐字）。
- Failure reason validated: 红来自被检产物缺席，不是命令没跑起来：把报告按原始字节摆回后**同一条命令**立刻
  `0 check(s) failed` / `rc=0`，判据数从 17 变回 18，还原前后 `artifacts/metrics/report.md` 与
  `.codex/README.md` 的 sha256-12 分别是 `4dec79a38083` 与 `0fc0b71528da`，逐字节相同（块 01，
  驱动 `artifacts/tmp/b030-redgreen.py`）。第二个 RED 状态（报告在场、读数被手抄改成一个错的数）用
  同一条命令，判的是另一面：`FAIL each machine-recheckable reading is produced by running the command on
  its own line — checked=9 mismatch=[['library-entries', 'rows=13;headings=6;total=20',
  'rows=13;headings=6;total=19']]`（块 01 RED-2）。第三个 RED 状态抽掉 `.codex/README.md` 的登记行，
  并列跑两支脚本：路线图守门 `rc=1`、指标守门 `rc=0`——这一对读数证明两条命令判的是两半
  （块 01 RED-3），末态复跑两支都 `rc=0`（POST）。
  另有一次**不是设计出来的**红：B6 的 `实现要点` 回写落笔后 `check_roadmap_and_routing.py` 立刻
  `FAIL no placeholder tokens in ROADMAP — measured=['PENDING']` / `EXITCODE=1`，
  因为那句话里正当出现了一个被禁占位词的字面量。它由同一族的在场/占位判据抓到，改写措辞后同一条命令转绿；
  这件事已做成块 01 的 RED-4 场景（把 `PENDING` 写进 B6 的 `实现要点` 句 → 路线图守门
  `FAIL no placeholder tokens in ROADMAP — measured=['PENDING']`，还原后复绿），不再只是卡片里的一句叙述。

### GREEN — minimum implementation
- Implementation: `.codex/scripts/metrics_report.py`（417 行；`:34-45` 输入与字段、`:49-52` 四条裸下限、
  `:84-155` 12 个读数函数、`:161-200` 登记表、`:202-213` `metric_command()`、`:214-240` `build()`、
  `:241-260` `parse()`、`:261-277` 执行印出的命令、`:278-390` 18 条判据、`:391-417` 三模式入口）+
  `artifacts/metrics/report.md`（由 `--write` 生成，20 行）；core 档注册 `run_all_guards.py:99-100`；
  `checkers_selftest.py:68-71` 常量与 `:78-80` 第 11 条基线；`README_ENTRIES` 第 6 项与 `.codex/README.md:32`。
- Command: `python -X utf8 .codex/scripts/metrics_report.py`
- Exit code: 0
- Passing evidence: 块 00 的 18 行逐字（含 `registered=12 floor=8`、`derived=4 floor=4`、`checked=9 mismatch=[]`、
  `measured=9 floor=6`、`scanned=readings+header`）。
- Regression evidence: 块 02（174 例注入 + 11 条基线，本片 19 例 `metrics:*` 全部命中期望标签、
  `harness_errors=0`）；块 03（`run_evals` 末态与片 029 逐字未变、`--tier core` `pass=31 fail=0`、
  `validate_tasks errors=0`）；块 04（指针复算 `stale=0`，含本片校正的七张卡区间引用）；
  块 06（`git diff --stat HEAD` 里 `android/**` / `frontend/**` 零命中）。

### REFACTOR — optional
- Cleanup: ① `check()` 里对 `命令` 字段的所有取值改走 `row.get("命令") or ""`：报告行缺该字段时原来会以
  `KeyError` 收场（仪器故障被读成判据红之外的第三种东西），改成缺字段由「命令栏不许空」那条判据判红，
  病因归位。② 表格计数加 `SEPARATOR_RE` 跳过 markdown 分隔行，`library-entries` 从 16（把表头当数据）
  变 13，口径栏同步写明「不含表头与分隔行」。③ 生成器里一段死占位循环（写完就没人读的 `for` ）删除，
  占位这件事由六字段/唯一单位两条判据承担。④ 家族表从「脚本里再写四行」改成从 ROADMAP 目标句派生，
  消掉本片自带的那份第二权威——这条与本仓库 B2/B4/B5 的「同一约束只保留一份权威正文」同形。
- Regression evidence: 块 02 覆盖 ①（`metrics:command-blanked` 与 `metrics:field-renamed` 各自命中期望标签，
  说明缺字段/改字段是判据红而不是 `HARNESS`）；块 00 的 `rows=13;headings=6;total=19` 与块 02 的
  `metrics:source-drifted-under-report` 打印的差值覆盖 ②；块 00 的 18 行覆盖 ③（判据数与形状未减）；
  块 02 CASE 159/165 覆盖 ④（派生侧与产物侧都响过）。

### TDD or sizing exception — only with prior user approval
- Exception type: 尺寸（soft limit）。`estimated_production_lines=317` 超 200 行软上限，由
  `validate_tasks.py` 以 WARNING 报出（块 03：`warnings=5`，第五条即本片）；未到 500 行的失败线，
  且没有用户批准的豁免申请——这里记录理由，不请求例外：一支生成器 + 一份判定 + 一个指标注册表
  拆成两片会立刻造出第二份登记表，而「不许有第二份权威」正是本片要判的东西。
- Reason it is justified: 317 行里 314 行是新增仪器（12 个读数函数 + 18 条判据 + 三模式入口），
  对既有文件的改动只有 3 行（core 档注册 2 行、`README_ENTRIES` 1 行），影响面与行数不同向。

## Implementation notes

- 判定的对象是「这一页报告的数能不能被复算」，不是「报告写得全不全」。所以最硬的一条是
  `each machine-recheckable reading is produced by running the command on its own line`：它把每行印出的
  命令真的执行一遍（`run_printed_command()` 只接受 `python -X utf8 .codex/scripts/metrics_report.py --read <key>`
  这一种形状，六段之外一律不算可复算），拿执行结果与读数比。这条一落地就抓到生成器自己两处过期。
- `parse()` 读的是报告文件正文，不是内存里的登记表：判据面必须读**产物**，否则「登记表有 12 条」
  这种话在报告缺行时仍然绿（`the report holds exactly one line per registered metric` 才有意义）。
- 家族从 ROADMAP B6 目标句 `把(.+?)汇总成一页报告` 现场派生（`FAMILY_RE`），`MIN_FAMILIES = 4` 是派生实数
  之下的下限——被检集合不给自己定上限，也不把自己抄一遍。ROADMAP 改词脚本立刻转红，这是想要的耦合方向。
- `复算=否` 那一类不是放宽：它只判两件事——命令必须是真的 `git` 命令（`history readings are read from a
  real git command`），且当场必须能产出读数（`every metric produces a reading when re-run`）。原因是
  `build_workspace` 造的副本是一份新 init 的 git 历史，历史类读数在副本里必然不同；把这种差异写成
  「机检」会让每轮自检假红，藏进代码注释又等于没人知道，所以写进报告表头。
- 占位符判据只扫表头 + 读数（`scanned=readings+header`），不扫口径：口径栏要复述被数的那件事，
  里面合法出现任务卡里真实存在的状态词字面量。本仓库的 `PLACEHOLDERS` 是四个词，
  `metrics_report.py` 与 `check_roadmap_and_routing.py` 共用 `harness_check_lib` 那一份，不另立清单。
- `退回次数` 这一族是代用信号，不是同名登记表：任务卡状态只有三态，裁决散在正文里。所以口径栏写清楚
  「`Verdict: PENDING` 的卡片数」与「写了『无独立 verifier 轮次』的行数」，并在括号里说明它不是退回次数本身。
  把代用信号冒充原指标是本片最可能骗过读者的一处，所以把它写在读数旁边而不是脚注。
- 窗口一律锚在数据里的最新日期（`top_days()`），不读系统时钟：这样同一 HEAD 下重跑 `--write`
  字节相同，报告才可当基线用；读时钟的报告每轮都漂，判据就分不清是数据变了还是日期变了。
- core 档注册只加两行（`:99-100`），不新造档位：与片 027/028/029 同一结论——秒级文本判定属 core。

## Verification report

- Verdict: PASS（指标守门末态 18 条 + 一次同命令 RED/GREEN（四状态、含两半不重叠对照与占位词落 ROADMAP
  的真实转红）+ 174 例注入含 11 基线 + core 档整跑 + 指针复算 + 全库活引用扫描 + 快照重转录零差异；
  见块 00–06 与 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-metrics-report-generator-030.txt`
  块 00（指标守门 18 行末态逐字）、块 01（RED/GREEN 八断言与 sha256-12 字节还原，含 RED-3 的
  「路线图守门红 / 指标守门绿」对照，以及 RED-4 把 B6 回写误落占位词那次真实转红做成可重跑场景）、
  块 02（174 例整轮 + 11 条基线 + 本片 19 例 `metrics:*` 逐字，另附锚点失效那轮 `run080-selftest.txt` 的
  `CASE 39 MISMATCH` 与 `ok=173 mismatch=1` 原始读数）、块 03（`run_evals` / `--list` / `--tier core` /
  `--write` / `validate_tasks` / 路线图守门独立复跑）、
  块 04（行号指针复算与本片改回的七张卡区间引用）、块 05（全库 `文件:行` 扫描逐条读数）、
  块 06（快照刷新读数、本片尺寸、`android/**`/`frontend/**` 零命中）。
  环境：Windows 10.0.26200 / x64 / Python（`sys.executable`，一律 `-X utf8`）/ Git Bash。
  捕获文件与驱动留在 gitignore 的 `artifacts/tmp/`：**末态一轮是连续跑完的 `runF-*`**——
  `runF-metrics.txt`（块 00）、`runF-redgreen.txt`（块 01，含新增的 RED-4）、`runF-selftest.txt`
  （块 02 的 174 例整轮）、`runF-evals.txt` / `runF-list.txt` / `runF-core.txt` / `runF-write.txt` /
  `runF-validate.txt`（块 03）、`runF-pointers.txt`（块 04）、`runF-citations.txt`（块 05）、
  `runF-refresh.txt` / `runF-sizing.txt`（块 06）；中途轮留在 `run080-selftest.txt`
  （锚点失效那轮）、`run081-selftest.txt`（接线中途轮）、`run083-selftest.txt`（快照重转录后那轮）、
  `run084-metrics.txt` / `run085-redgreen.txt` / `run086-evals.txt` / `run087-selftest.txt` /
  `run082-core.txt`（同一形状在接线前后的读数，用于对照末态没有变）；
  卡片每改一次，块 00/04/05 与任务校验就整轮重跑一次，改前的各轮留在 `run097/098/088/089/092/104/105/106`
  /`109/110/111/112/114` ——引用条数从 89 变 90 的那一步就是 Gaps ③ 里指向 `…-029.txt:558` 的更正，
  `POINTERS checked=130 stale=0` 与 `problems=0` 在改前改后都对得上；
  驱动自己的两次错也在场：`run100-redgreen.txt` 与 `run102-redgreen.txt` 是 RED-4 注入锚点写歪的
  两轮（前者命中 6 行、后者命中 0 行），两轮都停在 `BAD PRE condition` 且还原后 `POST restored`
  两支 `rc=0`——判据一次没红，红的是这台驱动，所以它们进块 01 的对照而不算 RED 状态；
  `run099-core.txt` 是 `--tier core` 被同一工作树上的并发自检跑断的那轮（只读到 20 条就退出），
  教训是这几台仪器不许并发跑，块 03 因此用的是单跑完的一轮（`pass=31 fail=0 inconclusive=0 fault=0`）；
  驱动 `b030-redgreen.py` / `b030-refresh-snapshot.py` / `b030-pointers.py` / `b030-sizing.py` /
  `b030-evidence.py`（块装配，只拼接与现场跑只读命令）/ `b030-locate.py`（定位用，不含断言）
  都在 gitignore 的 `artifacts/tmp/` 里，可重跑。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片没有删除任何既有判据、没有放宽任何
  断言、没有往任何白名单加名字、没有调低任何裸下限。被收紧的有两处：① `roadmap:readme-entries-drained` 的
  注入锚点从「删清单里的 2 项」改成「删清单的第一整行」（剩 2 项 < 下限 4）。原因不是旧锚点判错，而是
  `README_ENTRIES` 加了第 6 项之后它不再能破下限——`run080-selftest.txt` 的
  `CASE 39 MISMATCH … rc=0, seen=[]` 是这件事唯一把它逼出来的读数；改后同一用例命中期望标签（块 02），
  等效性由 `roadmap:evals-entry-removed` / `roadmap:readme-entry-removed` 两条既有用例仍各自转红证明。
  ② `build_workspace` 新增一段定点拷贝（`:760-764`），方向是让副本更像真仓库而不是让判据更容易过——
  没有它，`metrics_report` 进基线后每轮都会以「报告不在场」假红。快照的改动（那句「其余 32 条注册项」
  换成不复述条数的写法、`:5` 的行号）不是判据改动，而是对当前工作树的重新转录，零差异结论由
  `b030-refresh-snapshot.py` 打印。
- Gaps: ① 「什么时候该重生成报告」仍是人（或主会话）的判断：判据能保证旧报告在手抄时转红，
  但仓库变进而报告未重生成时，`复算=值` 那 9 行会红、`复算=否` 那 3 行**不会**——它们只判形状。
  这条留在片 031 的角色契约里（「引用报告前先 `--write`」），本片不假装机械覆盖了它。
  ② 指标注册表本身没有「够不够」的判据：12 个指标覆盖 ROADMAP 点名的四族，但「这族该不该再加一个指标」
  是判断，不是判据；`MIN_METRICS = 8` 只防抽干。
  ③ 报告落点在 `artifacts/metrics/`（B6 原条目就这么要求）。写完这段时我按「`artifacts/` 整片是 gitignore
  的运行期面」把它写成「不进版本库」，`git check-ignore` 与 `git ls-files artifacts/evidence`（1283 个已跟踪
  文件）证伪了这条：`.gitignore` 只忽略 `artifacts/tmp/`、`artifacts/recovery/`、`artifacts/web-shots*/`
  和 `artifacts/evidence/.captures/`。结论反过来：`report.md` **必须**随片提交，否则新克隆上 core 档会直接
  红在「报告文件在场」那条判据上，`--write` 也就成了只有作者本机跑得通的动作。代价因此变硬：每片收尾都要
  `--write` 并把新报告一起提交，`复算=值` 那 9 行才会跟着当前树走。同时更正片 029 证据文件里的同一句措辞
  （`artifacts/evidence/T-2026-10-19-eval-keeper-baseline-role-029.txt:558` 把 `artifacts/**` 整片说成被忽略；
  片 028 那句写的是 `artifacts/tmp/**`，是对的）——那只改一句标题措辞，不动任何读数。
  ④ 无独立 verifier 轮次（用户要求一站到底；本片属 harness 仪器面，18 条判据有 19 例注入覆盖）。
  ⑤ D11 仍开（cdp flake 与「运行器不打印失败用例名」）：本片没动那两台仪器，`harness_errors=0`
  只说明这一轮锚点都对得上。
  ⑥ `metrics:*` 只覆盖 18 条判据里的 19 个注入面，两条「在场性」判据（源目录、报告目录）各有一条删除用例，
  但 `SOURCES` 里 `.codex/tasks/` 与 `checkers_selftest.py` 两份没有单独的缺席用例——它们被删时同一条
  判据仍会响（`exists()` 对整个元组做），缺的是**证明它响过**的用例。这条与片 029 Gaps ⑥ 同形。
- Risks: `checkers_selftest.py` 现值 991 行（本片 +106 行，HEAD 是 802 行）。它继续长下去时的第一个收敛对象
  仍是 4 份 `SAMPLE_SPEC_*` 取样规格的形状重复；第二个是 `CASES` 里 `metrics:*` 与 `keeper:*` 之间那批
  只为改锚点而存在的多行元组——都不属本片判定面，留给 `code-simplifier` 第一次真实接单。

## Handoff notes

- Completed: B6 的指标面（`metrics_report.py` 的 12 指标 / 18 判据 / 三模式、`artifacts/metrics/report.md`
  初版、19 例 `metrics:*` 注入、第 11 条基线、core 档注册、`README_ENTRIES` 与 `.codex/README.md` 登记、
  ROADMAP B6 三条按当前事实更正 + `交付任务`/`实现要点` 回写）+ 片 029 之后七张卡的行号指针复算 +
  基线快照第三次重转录。
- Next slice (片 031, B6 角色面): `.codex/agents/metrics-reporter.md` + 同名 `.toml` +
  `check_metrics_reporter_agent.py` + `reporter:*` 注入 + 第 12 条基线与 core 档注册 + `.codex/HARNESS.md` 接线。
  角色契约要判的三件事已由本片给出形状：① 引用报告前必须先 `--write`（补本片 Gaps ①）；
  ② 指标家族增减属判据改动，要另开一片并补注入用例；③ 报告读数只能逐字转录，不许手抄——
  判据面与本片严格分开（本片判「报告 vs 现场复算」，角色面判「角色正文 vs 事实源」），
  否则改一处措辞同时红两支脚本。
- Things a future slice must not silently rely on: `README_ENTRIES` 再加第 7 项就会再次让
  `roadmap:readme-entries-drained` 的锚点失去杀伤力（本片已撞过一次，见块 02 两侧的读数）；
  `BASELINES` 元组上方再加常量就会再推一次 `:74-76`，而那条指针同时被片 026 卡片、快照与
  `evals:pointer-stale` 的锚点引用——三处要一起跟，只改一处会以 `HARNESS — anchor not found` 收场。
- Verification debt this slice leaves: D11 未闭合（本片未触碰）；片 031 收尾时应把「`--write` 后整轮重跑」
  同时写进角色契约与 `metrics:*` 的一侧，避免它只活在 `## Handoff notes` 里。
