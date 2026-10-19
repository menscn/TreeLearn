# Codex Harness Task: 让交付指标报告有一个看守它的角色（ROADMAP B6 后半）

id: T-2026-10-19-031
title: metrics-reporter 角色、成对守门与全库指针复算
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 189
shared_code_touched: false
depends_on: [T-2026-10-19-metrics-report-generator-030.md, T-2026-10-19-eval-keeper-baseline-role-029.md, T-2026-10-19-guard-runner-one-command-roster-023.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 要求「完成结论必须匹配证据覆盖」，也要求「同一约束只保留一份权威正文」。ROADMAP B6 被
  片 030/031 拆成两侧：片 030 交付指标面（生成器 + 那一页报告 + 「手抄数字当场转红」的执行式判定），本片交付
  角色面——把「有人按流程跑它」这件事从习惯变成一个有正文、有边界、有一条命令判它是否还在履职的角色。
  两侧判据面严格分开：片 030 判「报告里的数能不能被它自己那行命令复现」，本片判「角色正文有没有把这套流程
  写成可执行的约束」。
- Problem/background: 片 030 之后 `artifacts/metrics/report.md` 里的每个数都能被复算，但复算这件事本身没人守：
  角色缺席时，最常见的坏法是引用上一页留下的抄本（那其实是某个旧 HEAD 的读数）、把 `--write` 的退出码当成
  判据通过（它只落文件、不判东西）、把六个字段合并成一句人话、或为了让报告好看去调低下限。本片真实踩到的
  另一件事属于 D9 那一族且更大：给 `checkers_selftest.py` 顶部插 4 个常量、把 `BASELINES` 接成 3 行、追加
  35 例 `reporter:*` 之后，**九张既有卡片里 46 处 `文件:行` 区间引用同时变成谎话**，而 B2 的基线快照
  `product.md:5` 那条指针也跟着漂——快照与注入锚点 `evals:pointer-stale` 必须同轮改，否则整轮自检报
  `HARNESS — anchor not found`（仪器坏，不是判据红）。修这些引用的驱动自己还造了一句假话
  （同一半句被连印两遍，形如「（第 10 条基线所在元组；」×2），于是本片多了一条重复短语扫描。
- Users/scenarios: 使用者是后续每一片收尾时的主会话。场景四个：① 要一页当前交付报告 → 先 `--write` 重生成再
  转录；② 有人引用了一个没有对应命令行的数 → 用 `--read <指标名>` 复算并贴回那行；③ 报告与当前树不一致 →
  写明是哪一行、复算命令是什么，不悄悄改数；④ 想调低某个裸下限或增删家族/指标 → 那是判据改动，另开一片并补
  注入用例，角色不接单。
- Expected outcome: `.codex/agents/metrics-reporter.md`（113 行内容，`md_chars=4691`）与逐字相同的
  `.toml`（6 行包装，`toml_chars=4691 equal=True`）+ `.codex/scripts/check_metrics_reporter_agent.py`
  （250 行，28 条 `c.check` 判定 + 2 条只在派生/TOML 解析失败时触发的 `c.fail`，6 处 fail-closed 早退）+
  `checkers_selftest.py` 的 35 例 `reporter:*`、第 12 条基线与第 5 份抄检来源 + core 档注册三行 +
  `.codex/HARNESS.md` 第 7 步接线一句 + 九张卡片的指针复算 + B2 基线快照重转录（零差异）。
- Core flow and reversibility: 新增两份角色正文与一支守门，改四处既有条目（自检常量与基线元组、抄检来源与前缀
  闸门、`run_all_guards.py:106-108` 注册、`HARNESS.md:13` 一句），回写 ROADMAP B6 三节，重转录一份快照并跟随
  它的注入锚点，按现值改写九张卡片的区间引用。回退方式：删角色两份与守门脚本、撤 `run_all_guards.py:106-108`、
  撤 `checkers_selftest.py` 的 35 例 + 第 12 条基线 + 4 个常量 + 第 5 份抄检来源 + 前缀闸门那一项、还原
  `HARNESS.md:13` 那一行，即回到片 030 末态；快照重转录与九张卡片的指针改写**不属**该回退面（前者是当前树
  复算出来的事实，后者是把已被片 030/031 加行推歪的引用改回现值）。
- Inputs/outputs: 输入 = 角色正文与它引用的七份事实源（`metrics_report.py` 的 docstring 与派生入口、
  `verifier.md` 小节骨架、ROADMAP B6 条目、`.codex/README.md` 的 `artifacts/metrics/` 登记行、
  `run_all_guards.py` 注册表、报告本体、ROADMAP）；输出 = 28 行 `OK/FAIL` +
  `check_metrics_reporter_agent: N check(s) failed` + `EXITCODE=0/1`。
- Constraints and trade-offs: 只用标准库；判据面与片 030 不重叠，否则改一句报告措辞同时红两支脚本；家族清单、
  报告落点、生成命令与复算命令一律 `import metrics_report` 现场派生，守门与角色正文都不重抄（三份权威里任何
  一份改了都不会被发现，代价是角色正文与生成器之间只有「家族名 + 两条命令」这一层薄引用）；角色正文不抄
  生成器 docstring 与 ROADMAP 条目，抄检窗口沿用 60 字档。
- AI recommendation and rationale: 角色守门的重点不是「小节齐不齐」，而是三条会让那页报告失去意义的形状：
  引用数字前**先重生成**（写命令与复算命令必须逐字等于生成器自己拼出来的那两条）、重生成后**整跑 core**
  （报告里的数绑着仓库现状，别的片一改注册表或卡片，旧报告立刻变成没人核对过的抄本）、六个字段**逐字转录**。
  三条各配注入用例，其中「整跑 core」和「逐字转录」在本片写作过程中真的被自己的初稿漏掉过一次（下限读到
  `measured=1 floor=2`），所以它们不是设计出来的假想敌。
- User choice and custom additions: 用户要求一站到底、按片提交本地、不中途请示；未对本片提出额外定制。
- AI-autonomous decisions: ① 本片只做 B6 的**角色面**，指标面留在片 030，两侧不重叠用 RED-2/RED-4 读出来
  （抽掉角色的生成命令 → 角色守门红而 `metrics_report` 仍绿；抽掉 `.codex/README.md` 的登记行 → 角色守门红而
  `metrics_report` 仍绿），而不是声明；② 家族/落点/两条命令从 `import metrics_report` 现场取，派生失败算仪器坏
  （`c.fail` + 早退）而不是「跳过」；③ `MIN_FAMILY_MENTIONS = 3` 刻意低于派生实数 4，让「生成器把家族抽干」也响
  ——被检集合不能给自己定下限；④ 成对文件的单侧漂移用**追加一行**而不是替换措辞，替换总会碰到某个正则形状，
  红的就不只「.toml 与 .md 逐字相同」那一条，用例失去指向性——末态读数因此能写成 `fails=1`；
  ⑤ 指针复算由驱动量完再落笔（`b031-pointer-fix.py --apply` 打印 `applied=46 files=9`），并新写一支
  `b031-pointers.py`（214 行表、`MIN_POINTERS = 200`）取代片 030 的驱动；⑥ 补一条全库重复短语扫描
  `b031-dup-scan.py`，**自带正控**（把本片真实造出来的那条坏句喂回同一套匹配必须命中），因为它要防的正是
  「修正工具自己造出的谎」。
- User-confirmation decisions: 无新增需拍板项（不改产品口径，只新增流程资产与仪器）。
- Acceptance evidence: 块 00（角色守门末态 28 行逐字）、块 01（RED/GREEN 十断言五场景 + 字节还原 sha256-12 +
  「角色守门红 / 指标守门绿」两对不重叠读数）、块 02（209 例注入含本片 35 例 `reporter:*` 逐条 + 12 条基线）、
  块 03（`run_evals` / `metrics_report` / `--list` / `--tier core` / `--tier precommit` / `validate_tasks`）、
  块 04（指针驱动 214 条 + 九张卡片 46 处区间改写读数）、块 05（全库活引用扫描 + 重复短语扫描）、
  块 06（本片尺寸、快照刷新读数、`git diff --stat HEAD` 与 `android/**`/`frontend/**` 零命中）。
- Facts: `families=4 landing=artifacts/metrics/ report=artifacts/metrics/report.md`、
  `write_command=python -X utf8 .codex/scripts/metrics_report.py --write present=True`、
  `read_command=python -X utf8 .codex/scripts/metrics_report.py --read <指标名> present=True`、
  `scripts=['.codex/scripts/metrics_report.py', '.codex/scripts/run_all_guards.py'] missing=[]`、
  `landing_lines=1 all_named=True`、`measured_hits=0`、`toml_chars=4691 md_chars=4691 equal=True`、
  `SELFTEST cases=209 ok=209 mismatch=0 harness_errors=0 skipped=0`、`ROSTER entries=38 core=32 precommit=6`、
  `RUN-ALL-GUARDS pass=32 fail=0 inconclusive=0 fault=0`（core 档）与 `pass=6 fail=0`（precommit 档）、
  `POINTERS checked=214 stale=0`、`CITATIONS files_with_citations=17 citations=104 problems=0`、
  `DUP-SCAN scanned_files=141 scanned_lines=18687 raw_hits=166 cjk_prose_hits=0 problems=0 正控 fired=True`、
  `RED/GREEN assertions=10 bad=0`、`EDITS applied=46 bad=0 files=9`、`product.md bytes=1745 sha256_12=2e0d1cbef721 zero_diff_with_source=True`
  （刷新前 `changed_lines=[5]`）、`PRODUCTION_LINES total=189` 均为一次真实执行的打印。
  报告侧在卡片在场之后重生成一次（`--write` 与只判模式都退 0），`--read` 逐条复算的现值是
  `cards-by-file-date=2026-10-19=11;2026-10-18=15`、`tasks-status-counts=verifying=20;verified=4;in_progress=2`、
  `pending-verdict-cards=8`、`selftest-cases-by-prefix` 里 `reporter=35`、`guard-roster-by-tier=entries=38;core=32;precommit=6`、
  `baseline-checkers=12`、`recheckable-share=值=9;否=3;total=12`、`library-entries=rows=13;headings=6;total=19`。
- Assumptions: `verifier.md` 的小节骨架（4 个 `<tag>` + 1 个二级标题，实测 5 个名字）继续作为角色骨架的比对
  对象；该文件若被改写，本守门会先转红而不是安静跟随——那是「沿用同一套角色骨架」这条判据的用途。
- Unknowns: 报告里 `退回次数` 一族仍缺结构化登记面（片 030 已用「无独立 verifier 轮次」计数替代并写明替换
  关系），本片不解决它——它是口径问题，属另开一片。
- Risks: 角色正文的措辞与守门里的 17 条正则形状绑得很紧（`RUN_CMD_RE` / `REGEN_RE` / `RERUN_RE` /
  `TRANSCRIBE_RE` / `PROHIBITION_RE` / `TAMPER_RE` / `LANDING_MARK` 等），改写角色句子容易把某条合取判据的命中
  数打到下限以下；方向是想要的（判据有牙），代价是改措辞要重跑块 00。另有一条已被实测确认的风险：任何给
  `checkers_selftest.py` 顶部加行的后续片都会同时推歪九张卡片与快照里的引用，所以本片把「复算驱动 + 表自身的
  裸下限」留在了 `b031-pointers.py` 里供下一片直接接手。
- Deferred: ① 角色 `.toml` 与 `.md` 的逐字重复由判据钉住而不是由生成器消除（本仓库无生成器，与片 024/026/027/029
  同一处理）；② 「增删家族/指标属判据改动、要另开一片并补注入用例」写进角色 `<contract>`/`<禁区>` 而不是实现成
  一条自动分流；③ `b031-pointer-fix.py` 只保证「写进去的是量出来的数」，它自己不判「量出来的句子读起来是否
  重复」——那由 `b031-dup-scan.py` 补上，两支驱动没有合并。
- Non-goals: 不新增指标或家族，不改 `metrics_report.py` 的判据与裸下限，不动 `--write`/`--read` 的语义，
  不宣布任务验收。
- Skipped non-critical questions and reason: 未问「角色是否要配一份 skill」——B6 的跑法只有两条命令，已由
  角色正文与 `HARNESS.md` 第 7 步承载，再加一份 skill 正文就是第二个权威（与片 029 同一结论）。

## Goal

让「这一页交付报告的每个数都是刚被某条命令跑出来的」这件事有一个只读边界清楚的角色负责，并且有一条命令能判住
这个角色是否还在履职：`python -X utf8 .codex/scripts/check_metrics_reporter_agent.py` 退出码 0，28 条判据逐条
打印读数，其中「两条命令逐字等于生成器自己拼出来的那两条」「重生成后整跑 core」「六个字段逐字转录」「判据面
不许动」「唯一可写落点只在 `artifacts/metrics/`」各有注入用例兜着；同时把本片加行推歪的 46 处既有 `文件:行`
引用与 B2 基线快照一起改回当前树的真值。

## Scope

### In scope
- `.codex/agents/metrics-reporter.md` + `.codex/agents/metrics-reporter.toml`（逐字相同的运行时包装）。
- `.codex/scripts/check_metrics_reporter_agent.py`（28 条判定 + 2 条派生/解析失败的 `c.fail`，含从
  `import metrics_report` 现场派生的家族清单、报告落点与两条命令）。
- `.codex/scripts/dev/checkers_selftest.py`：4 个常量、第 12 条基线、35 例 `reporter:*`、第 5 份抄检取样来源
  （生成器 docstring）、`copy_check_prefixes` 的第 5 项。
- `.codex/scripts/run_all_guards.py` 的 core 档注册（三行）。
- `.codex/evals/cases/001-impact-inventory/product.md` 重转录（源小节行号被本片推下去）与
  `evals:pointer-stale` 锚点跟随。
- `.codex/HARNESS.md` 第 7 步接线一句；`.codex/ROADMAP.md` B6 的 `验收方式` / `交付任务` / `实现要点` 回写。
- 片 021/023/024/025/026/027/028/029/030 九张卡片的 `文件:行` 区间引用复算，与两支驱动
  `artifacts/tmp/b031-pointer-fix.py`、`artifacts/tmp/b031-pointers.py`、`artifacts/tmp/b031-dup-scan.py`。

### Out of scope
- `metrics_report.py` 的判据、指标登记表与裸下限（片 030 的面，本片一字未改）。
- 新增家族或指标（属判据改动，角色正文自己写明要另开一片并补注入用例）。
- `artifacts/metrics/` 在 `.codex/README.md` 与 `README_ENTRIES` 的登记（片 030 已做，本片只把它作为
  RED-4 的抽离对象）。
- `check_roadmap_and_routing.py` 的 `PHASE_B_KEYWORDS`：`"metrics-reporter"` 早在片 030 之前就随 ROADMAP B6
  标题在场（现值 `:36`，由块 04 复算），本片没改那支脚本。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图 = 「谁来守着那页报告」从没有人变成有一个只读边界清楚的角色，
  且该角色是否履职有一条命令判得住。保留 = 片 030 指标守门的逐字读数（块 03 与片 030 证据对照，
  `checked=9 mismatch=[]` / `report header names the command that writes it present=True` /
  `no metric reading is left as a placeholder hits=[]` 一字未变）、`run_all_guards.py` 其余 35 条注册项、
  `HARNESS.md` 第 1–6 步原文（接线只做在第 7 步那一条之内）、`checkers_selftest.py` 原有 174 例与 11 条基线
  一字未改（只追加）、九张卡片除区间行号之外的全部正文。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 = 角色正文两份与守门脚本；
  消费者 = `run_all_guards.py:106-108`（core 档）、`checkers_selftest.py:87-89`（12 条基线所在元组的末行
  `RUN_METRICS, CHECK_REPORTER)`）、`:741-845`（35 例起点与末点）、`:1028-1030`（抄检前缀闸门）、
  `.codex/HARNESS.md:13`（验收阶段之后的第 7 步）。同级入口 = `check_code_reviewer_agent.py` /
  `check_codebase_researcher_agent.py` / `check_code_simplifier_agent.py` / `check_eval_keeper_agent.py`，
  五者共用 `harness_check_lib.agent_section_names()`、`citable_paths()` 与 `max_overlap_with_files()`；本片只新增
  一个调用方，没改这层共享库（共享库改动会同时影响 4 支既有守门的抄检正控，那属于另开一片）。
  另有三个**本片之前就在场**的消费方：`check_roadmap_and_routing.py:36` 的 `PHASE_B_KEYWORDS` 含
  `metrics-reporter`（ROADMAP B6 正文删掉该词那支守门立刻转红）、`.codex/README.md:32` 的 `artifacts/metrics/`
  登记行（本片作为 RED-4 的抽离对象）、`metrics_report.py` 自身的三个派生入口 `:39-42` `:156-158` `:206-210`
  （本片把它的 `--read <指标名>` 那条拼法当成角色的**唯一**复算命令权威，所以派生失败即仪器坏）。
  平台分支 = 不适用：无 Android/前端生产代码、无 API、无 SQL；「Java-only 不得被抄检绕过」不在本片判定面内
  （`harness_check_lib` 的抄检窗口沿用片 021 的 60 字档，`OVERLAP_RUNS = 60` 现值 `:65`）。
- Native bridge or independent-client compatibility/update requirements: 不适用（无桥接字段被触碰；
  `android/` 与 `frontend/` 零改动，见块 06 的 `git diff --name-only HEAD | grep -E "^(android|frontend)/"` → 0）。
- Behavior-neutral non-applicability reason, if applicable: 可反驳证据两条：`git diff --stat HEAD` 末态
  15 files / 157 insertions(+) / 37 deletions(-) 里 `android/**`、`frontend/**` 零命中；块 03 的
  `metrics_report` 末态与片 030 证据逐字相同（生成器与被检报告正文都没动，只有报告里的历史读数随提交推进）。

## Documentation impact record

- Changed runtime facts: 未改产品运行事实；新增一个流程角色与一条 core 档守门，并按当前工作树刷新了 B2 的
  基线快照。
- Authoritative code/config/migration evidence: `.codex/agents/metrics-reporter.md:1-113`（角色正文；
  `<identity>` `:8-14`、`<source>` `:16-21`、`<contract>` `:23-43` 是唯一落点、先重生成、整跑 core、逐字转录、
  判据面不许动与不宣布验收的权威措辞，`<rules>` `:45-64` 是三种模式/落点提交面属性/窗口口径/家族权威的引用面，
  `## 触发时机` `:66` / `## 职责清单` `:74` / `## 输出格式` `:87` / `## 交接` `:97` / `## 禁区` `:106`）、
  `.codex/agents/metrics-reporter.toml:1-6`（`developer_instructions` 与 `.md` 逐字，
  `toml_chars=4691 md_chars=4691 equal=True`）、
  `.codex/scripts/check_metrics_reporter_agent.py:1-21`（docstring：为什么需要这支脚本 + 与片 030 的判据分界）、
  `:33-41`（七份输入路径 `INPUTS`）、`:43-65`（16 条模块级下限与常量，含 `MIN_ROLE_SECTIONS = 5`、
  `MIN_FAMILY_MENTIONS = 3`、`OVERLAP_RUNS = 60`）、`:67-83`（17 条形状正则）与 `:84-85`（两个记号 `LANDING_MARK` /
  `READ_PLACEHOLDER`）、`:88-91`（`lines_matching()`）、`:93-246`（判定主体）、
  `:97-100`（早退 ①：七份输入）、`:103-113`（单一权威派生 `import metrics_report`）、`:114-118`（早退 ②：
  派生结果自身可用）、`:123-126`（早退 ③：参照角色的骨架可派生）、`:215-218`（早退 ④：引用路径条数）、
  `:231-236`（早退 ⑤⑥：TOML 解析失败即仪器坏）、`:209-221`（README 登记行 + 引用路径两半）、
  `.codex/scripts/dev/checkers_selftest.py:72-75`（4 个常量）/ `:78-80`（12 条基线所在元组的后两行）/
  `:111-113`（5 份抄检取样来源，本片新增 `SAMPLE_SPEC_METRICS_SRC` 在 `:113`）/ `:741-845`（35 例 `reporter:*`）/
  `:575-578`（`evals:pointer-stale` 锚点，本片把它跟随快照改成 `:78-80`）/ `:1028-1030`（抄检前缀闸门），
  `.codex/scripts/run_all_guards.py:106-108`、`.codex/HARNESS.md:13`、`.codex/ROADMAP.md:108-119`（B6 条目，片 032 加行后下移一位，
  本片改 `验收方式` `:114` / `交付任务` `:117` / `实现要点` `:118`）、
  `.codex/evals/cases/001-impact-inventory/product.md:1-13`（刷新后的快照，`:5` 就是被推下去的那条指针）。
  行号是先实测再落笔的：`artifacts/tmp/b031-pointer-fix.py --apply` 打印 `EDITS applied=46 bad=0 files=9`，
  写入后由 `artifacts/tmp/b031-pointers.py` 复算，末态 `POINTERS checked=214 stale=0`（表自身有
  `MIN_POINTERS = 200` 的裸下限，抽干即红）。
- Documentation surfaces checked: `.codex/ROADMAP.md`（B6 条目全节 + B2 对照 + D9/D11 记账）、
  `.codex/HARNESS.md`（七步流程）、`.codex/README.md`（目录结构节 `artifacts/metrics/` 一行，未改）、
  `.codex/agents/eval-keeper.md`（最近一片的角色正文，用来对齐小节骨架与交接写法）、
  `.codex/agents/verifier.md`（骨架小节集与只读边界措辞）、`.codex/agents/code-reviewer.md` /
  `codebase-researcher.md` / `code-simplifier.md`（同级角色的落点/交接/禁区写法）、
  `.codex/scripts/metrics_report.py`（派生入口与 docstring，抄检对象之一）、
  `.codex/rules/regression.md`（抄检窗口与被检集合）、`.codex/rules/harness-verification.md`（验收归属，
  本片把「不写 Verdict、不宣布任务验收」写进角色而**没有**改该规则）、`AGENTS.md`（第 62 行的 glob 已覆盖新
  守门，未改）。
- Documentation files updated: `.codex/HARNESS.md`（第 7 步一句）、`.codex/ROADMAP.md`（B6 的 `验收方式` 补角色
  那条命令与被判行为、`交付任务` 把片 031 改成已交付读数、`实现要点` 追加五条教训：角色面不许造第二权威 /
  判据撞车时改正文而不是改判据 / 合取判据才有牙 / 指针驱动自己也会造谎 / 成对文件的单侧漂移用追加而不是替换）、
  片 021/023/024/025/026/027/028/029/030 九张卡片的行号指针、
  `.codex/evals/cases/001-impact-inventory/product.md`（快照重转录）。
- Producer/consumer/sibling/compatibility paths checked:
  `grep -rlI "metrics-reporter" .codex Docs` 末态命中 8 个文件（角色两份、守门、自检电池、注册表所在脚本不含——
  `run_all_guards.py` 那三行用的是脚本名 `check_metrics_reporter_agent`、`HARNESS.md`、ROADMAP、
  `check_roadmap_and_routing.py`）；`grep -rlI "metrics_report" .codex Docs` 末态命中更多，含本片卡片自身。
  **行数含卡片自身，卡片加长就会漂，所以这里只登记文件集，行数写命令让人现场取（片 023 定的规矩）**。
  `.codex/README.md` 的目录结构节登记行现值 `:32`（`artifacts/metrics/`），由块 01 RED-4 当作抽离对象验证过。
- Superseded phrases/examples reverse-searched: 被本片改写的旧句是九张卡片里 `checkers_selftest.py:74-75`（4 处，
  基线元组区间）、`:913`、`:811-830`、`:385-394`、`:768-773`、`:942-944`、`:81-170` 等区间引用与
  `run_all_guards.py:81-161`；块 04 逐条按现值改写（`:78-80`、`:1028-1030`、`:926-945`、`:392-401`、`:883-888`、
  `:1058-1060`、`:81-173`、`81-173`）并标注是被哪一片推下去的。ROADMAP B6 的 `交付任务` 原文把角色面写成
  「片 031 交付……B6 在片 031 之前不算交付完成」的**未来式**，现改为已交付读数并把条数措辞留在「以现场读数取，
  不写进本条」。旧句已完全替换：`grep -rn "B2 在片 029 之前不算交付完成\|B6 在片 031 之前不算交付完成" .codex Docs`
  末态 0 命中；被删掉的那条重复短语（同一半句连印两遍，形如「第 10 条基线所在元组；」×2）由
  `b031-dup-scan.py` 的 `KNOWN_DUP` 常量长期盯着，末态 `known_old_dup hits=0 floor=0`。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:98` 的
  `DEFERRED:待 B4` 仍由 B0 那片改写，本片不动。`.codex/references/regression-cases.md`
  未新增条目（本片的教训先落在角色正文与卡片，够不上「失败案例库」的入库门槛：它没有跨片复现的判据形状）。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/check_metrics_reporter_agent.py` 退出码 0，28 条 `OK`（块 00）。
- [x] 角色两份文件缺席时同一条命令退出码 1，FAIL 标签是 `the seven inputs exist` 且 `missing=` 同时列出
      `.md` 与 `.toml`；按原始字节摆回后同一条命令 `0 check(s) failed` / `rc=0`，还原前后
      `0577f244566a` / `f821018c317d` / `0fc0b71528da`（角色 `.md` / 角色 `.toml` / `.codex/README.md`）三个
      sha256-12 相同（块 01，驱动 `artifacts/tmp/b031-redgreen.py`，gitignore 内；不用 `git checkout --`，
      本工作树 `core.autocrlf=true`，那会把 LF 换成 CRLF）。
- [x] 两条命令不互相冒充，两对读数都在（块 01 RED-2 / RED-4）：把角色里的生成命令换成别的名字 →
      角色守门 `rc=1`（`agent carries the generator's write command verbatim … present=False`）而
      `metrics_report.py` **仍然 `rc=0`**；抽掉 `.codex/README.md:32` 的 `artifacts/metrics/` 登记行 →
      角色守门 `rc=1`（`registered=False`）而 `metrics_report.py` **仍然 `rc=0`**。这是「判据面不重叠」的读数，
      不是声明。
- [x] 单一权威派生有牙：`reporter:families-drained` 把生成器的家族表抽到只剩 1 项 →
      `families=1 floor=3`；`reporter:landing-drained` 把落点常量抽空 →
      `landing= report=report.md`（两侧都打印，读者能看出红在派生侧而不是文件侧）（块 02 CASE 182/183）。
- [x] 命令的两半各配正控：`reporter:generator-command-swapped` → `agent names the runnable metric command —
      measured=0 floor=2`；`reporter:write-command-drifted` / `reporter:read-command-drifted` → 逐字相等那两条
      各自转红；`reporter:command-dead-script` → `every command in the role points at a script that exists` 红并把
      死路径打在 `scripts=` / `missing=` 里（块 02 CASE 186/187/188/189）。
- [x] 三条流程形状有牙：`reporter:handcopy-allowed` 红 `agent forbids hand-copying the report readings —
      measured=0 floor=2`；`reporter:rerun-obligation-dropped` 红 `agent obliges a full core-battery re-run after
      regenerating — measured=0 floor=2`；`reporter:recheck-flag-dropped` 红 `requires single-metric rechecks
      instead of mental arithmetic — measured=0 floor=2`（块 02 CASE 192/191/190）。
- [x] 判据面不许动与分流义务：`reporter:floor-tampering-allowed` 把「不得调低下限」那句改成允许 →
      `measured=1 floor=2`（合取只满足一半）；`reporter:split-obligation-dropped` →
      `agent sends family or metric changes to a separate slice with injected cases — measured=1 floor=2`
      （块 02 CASE 194/195）。另有 `reporter:summary-only-allowed`（`measured=1 floor=2`）与
      `reporter:acceptance-claimed`（`measured=1 floor=2`）钉住「只交报告与逐字读数、不宣布验收」（CASE 196/197）。
- [x] 唯一落点与只读边界分开判：`reporter:landing-not-metrics` 红 `landing_lines=1 all_named=False`，
      `reporter:readonly-dropped` 红 `agent declares everything else READONLY — measured=0 floor=2`
      （块 02 CASE 198/199）。
- [x] 历史读数的「只判形状」义务单独承重：`reporter:shape-only-denied` 把那句改成否定 →
      `agent says out loud that history readings are judged by shape only — measured=0 floor=1`（CASE 193）。
- [x] 引用面与抄检有牙：`reporter:family-unnamed` 红 `families=4 missing=['交付节奏']`，
      `reporter:report-file-unnamed` 红 `present=False`，`reporter:cites-drained` 红 `measured=2 floor=4`，
      `reporter:dead-path-cited` 红 `dead=['.codex/scripts/nonexistent-metrics-judge.py']`，
      `reporter:generator-text-restated`（把生成器 docstring 抄进角色）红 `measured_hits=1` 并打印抄来的那段，
      `reporter:placeholder-injected` 红 `hits=['TODO']`（块 02 CASE 200/201/203/204/205/206）。
- [x] `.toml` 与 `.md` 的逐字相同有牙：`reporter:toml-md-drift` 红 `toml_chars=4691 md_chars=4725 equal=False`，
      `reporter:toml-name-missing` 红 `name=False description=True`，`reporter:toml-unparsable` 报
      `TOMLDecodeError('Invalid value (at end of document)')`（块 02 CASE 207/208/209）。
- [x] 七份输入的早退形状有牙：`reporter:generator-gone` / `reporter:roadmap-gone` / `reporter:codex-readme-gone` /
      `reporter:reference-role-gone` / `reporter:roster-gone` 五例各自红在 `the seven inputs exist` 并把被删那一份
      打在 `missing=` 里；`reporter:reference-sections-drained` 红 `measured=1 floor=5 names=['职责清单']`，
      `reporter:section-uncovered` 红 `missing=['职责清单']`（块 02 CASE 177–181/184/185）。
- [x] `checkers_selftest.py` → `cases=209 ok=209 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS`，
      12 条基线全 `BASELINE OK rc=0`（含新升的 `check_metrics_reporter_agent`）（块 02）。
- [x] 成对文件单侧漂移只红一条：块 01 RED-3 往 `.md` 末尾追加一行后，末态读数是
      `rc=1 fails=1 FAIL agent .toml body equals the agent markdown verbatim — toml_chars=4691 md_chars=4710
      equal=False`（`fails=1` 这条本身就是判据不撞车的证据）。
- [x] 快照刷新后 `run_evals` 仍绿且复算的是新位置：刷新前 `changed_lines=[5] zero_diff_with_source=False`，
      刷新后 `bytes=1745 sha256_12=2e0d1cbef721 zero_diff_with_source=True`，快照 `:5` 现值是
      `checkers_selftest.py:87-89`，同轮把 `evals:pointer-stale` 的锚点（`:575-578`）改到同一行，否则整轮会报
      `HARNESS — anchor not found`（块 03 / 块 06）。
- [x] `run_all_guards.py --list` 读数 `entries=38 core=32 precommit=6`，`--tier core` 整跑
      `pass=32 fail=0 inconclusive=0 fault=0` / `VERDICT: PASS`，`--tier precommit` 整跑 `pass=6 fail=0`，
      且 `check_metrics_reporter_agent` 打印自己的判据行（块 03）。
- [x] `validate_tasks.py` → `SUMMARY files=26 errors=0`（`warnings` 全是本片之前就已超预算的历史卡片，
      非本片）（块 03）。
- [x] 本片没把任何既有句子的行号引用改坏：驱动末态 `stale=0`，其中本片把九张卡片的 46 处区间引用按现值改写
      （`b031-pointer-fix.py --apply` 打印 `EDITS applied=46 bad=0 files=9`），改写后的每一条都由
      `b031-pointers.py` 复算（块 04）。
- [x] 修正工具自己造出的那句假话已被消除且长期有控：`b031-dup-scan.py` 末态
      `scanned_files=141 scanned_lines=18687 raw_hits=166 cjk_prose_hits=0 known_old_dup hits=0` /
      `positive_control fired=True` / `DUP-SCAN problems=0`（块 05）——没有正控的判据等于没有判据，所以这条
      扫描必须自己报命中过一次才算武装。它在本片内响过两轮：第一轮拦的是驱动写进卡片正文的重复半句，
      第二轮（整轮读数只留在会话输出、没落盘）拦的是本片 ROADMAP `实现要点` 里**引用**那条坏句的原文；
      两轮都改措辞、都没动 `floor=0`。
      第二轮已固化为可重跑场景：块 01 RED-5 把回避式描述换回坏句原文，末态读数
      `RED-5 bad sentence re-quoted -> dup-scan rc=1 DUP .codex/ROADMAP.md:118 repeat='第 10 条基线所在元组；'
      | known_old_dup hits=1 floor=0`，还原后 `sha12=f2f9b49c9141 identical=True`。
- [x] 文档侧活引用全库扫描 `CITATIONS files_with_citations=17 citations=104 problems=0` / `VERDICT: PASS`（块 05；
      扫描条数不写死——本片卡片自己就是被扫对象）。

## TDD execution record

### RED — failing test first
- Test/scenario: 判据写完、角色两份正文还没落地——即「B6 角色面只有守门，没有可被它检的角色」这一状态。
  跑守门，看它是否把「被检对象缺席」报成红而不是无话可说的绿。
- Command: `python -X utf8 .codex/scripts/check_metrics_reporter_agent.py`
- Exit code: 1
- Observed failure: `FAIL the seven inputs exist — missing=['.codex/agents/metrics-reporter.md', '.codex/agents/metrics-reporter.toml']`
  紧跟 `check_metrics_reporter_agent: 1 check(s) failed` 与 `EXITCODE=1`（块 01 RED-1 逐字）。
- Failure reason validated: 红来自被检产物缺席，不是命令没跑起来：把角色两份按原始字节摆回后**同一条命令**立刻
  `0 check(s) failed` / `rc=0` / `judgements=28`，还原前后四个被注入的文件（角色 `.md` / `.toml`、
  `.codex/README.md`、`.codex/ROADMAP.md`）的 sha256-12 与基线逐字节相同（块 01 的 `restore … identical=True` 四行：
  `0577f244566a / f821018c317d / 0fc0b71528da / f2f9b49c9141`，驱动 `artifacts/tmp/b031-redgreen.py`）。
  另外四个 RED 状态：①②③ 用同一条守门命令并**并列**跑片 030 的 `metrics_report.py`，④ 只跑重复短语扫描：
  ① 把角色里的 `--write` 那条命令换成别的名字 → 守门 `rc=1`、指标 `rc=0`；
  ② 往 `.md` 末尾追加一行 → 守门 `rc=1 fails=1`、指标 `rc=0`；
  ③ 抽掉 `.codex/README.md` 的 `artifacts/metrics/` 登记行 → 守门 `rc=1`、指标 `rc=0`；
  ④ 把 ROADMAP 回写里那句回避式描述换回坏句原文 → `b031-dup-scan.py` `rc=1`、`known_old_dup hits=1`、
     并打印 `DUP .codex/ROADMAP.md:118`（这一条不碰守门，它判的是「回写时把坏句抄回去会不会响」）。
  ①②③ 这三对读数用来证明两片判的是两半，不是一支脚本的两种写法；其中 ② 的 `fails=1` 同时证明成对判据不会牵连别的
  形状（这就是把漂移注入从「替换措辞」改成「追加一行」的原因，见 brief 的 AI-autonomous 决策 ④）。

### GREEN — minimum implementation
- Implementation: `.codex/agents/metrics-reporter.md`（113 行内容；`<identity>` `:8` / `<source>` `:16` /
  `<contract>` `:23-43` / `<rules>` `:45-64` / `## 触发时机` `:66` / `## 职责清单` `:74` / `## 输出格式` `:87` /
  `## 交接` `:97` / `## 禁区` `:106`）与由 `artifacts/tmp/gen_agent_toml.py` 生成的逐字相同 `.toml`（6 行）；
  core 档注册 `run_all_guards.py:106-108`；`.codex/HARNESS.md:13` 的第 7 步接线；
  `checkers_selftest.py` 的 4 个常量 `:72-75`、第 12 条基线 `:78-80`、第 5 份抄检来源 `:113`、
  35 例 `reporter:*` `:741-845`、前缀闸门 `:1028-1030`。
- Command: `python -X utf8 .codex/scripts/check_metrics_reporter_agent.py`
- Exit code: 0
- Passing evidence: 块 00 的 28 行逐字（含 `families=4 floor=3 landing=artifacts/metrics/ report=artifacts/metrics/report.md`、
  `write_command=… --write present=True`、`read_command=… --read <指标名> present=True`、
  `scripts=['.codex/scripts/metrics_report.py', '.codex/scripts/run_all_guards.py'] missing=[]`、
  `landing_lines=1 all_named=True`、`measured_hits=0`、`toml_chars=4691 md_chars=4691 equal=True`）。
- Regression evidence: 块 02（209 例注入 + 12 条基线，本片 35 例 `reporter:*` 全部命中期望标签、
  `harness_errors=0`）；块 03（`run_evals` 与 `metrics_report` 末态未变、`--tier core` `pass=32 fail=0`、
  `--tier precommit` `pass=6`、`validate_tasks errors=0`）；块 04（指针复算 `stale=0`，含本片改写的九张卡片
  46 处区间引用）；块 06（`git diff --stat HEAD` 里 `android/**` / `frontend/**` 零命中）。

### REFACTOR — optional
- Cleanup: ① 角色正文里「唯一可写面」那句与 `<rules>` 中「它只落文件、不判东西」撞了 `LANDING_MARK = "只写"`，
  当时的读数是 `landing_lines=2 all_named=False`（那一眼只在会话输出里、没落盘；在场的是修完之后的
  CASE 198 `landing_lines=1 all_named=False`）。这里改的是**措辞**（把落点那句独占 `只写`），没有放宽正则——
  放宽正则就能骗过一条本意是「唯一可写面只出现在一处」的判据。
  ② 三条最初各被 3–5 行同时命中的判据（`逐字转录`、`不许调低下限`、`属判据改动要另开一片`）改成**合取**
  （`字段 ∧ 转录词`、`禁止词 ∧ 判据面对象`、`另开一片 ∧ 注入用例`）并把下限抬到实测减一，
  「删一句就红」才成立；否则任何单条注入都证伪不了它。
  ③ 家族清单、报告落点、生成命令与复算命令改由 `import metrics_report` 现场派生（`:103-113`），
  守门里的 `MIN_FAMILY_MENTIONS = 3` 低于派生实数 4，让「生成器把家族抽干」也能响。
  ④ 抄检取样复用共享的 `derive_sample()`，只新增第 5 份来源（`:113`）与前缀闸门第 5 项（`:1028-1030`），
  没有为生成器 docstring 再造一个取样器。
  ⑤ 指针复算分成两支：`b031-pointer-fix.py`（量→写）与 `b031-pointers.py`（214 行表→判），
  再加一支 `b031-dup-scan.py` 管「写进去的话读起来是否重复」；片 030 的 `b030-pointers.py`（130 条、
  `MIN_POINTERS = 120`；本片加行后现场 `stale=38`，那是它已被取代的读数，不是新坏法）退役，
  其读数只算片 030 当时的现场证据——这一点已写回片 030 卡片。
- Regression evidence: 块 04 覆盖 ①③⑤（`POINTERS checked=214 stale=0`、`EDITS applied=46 bad=0 files=9`）；
  块 05 覆盖 ①的产物面（`known_old_dup hits=0` 且正控 `fired=True`）；块 02 覆盖 ②③④——35 例 `reporter:*` 里
  有 6 例专门打合取下限（CASE 190–197），4 例打派生（CASE 182/183/200/201），3 例打抄检与占位（CASE 205/206 及
  `keeper:*`/`metrics:*` 同族用例在同一轮里仍各自命中，证明共享取样器没被改坏）。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 判定的对象是「这个角色还在不在履职」，不是「角色正文写得是否漂亮」。所以四条形状优先：两条命令必须**逐字等于**
  生成器自己拼出来的那两条（`WRITE_COMMAND` / `read_command(READ_PLACEHOLDER)`，判的是相等而不是包含）、
  引用数字前先重生成与重生成后整跑 core 各自独立承重、六个字段逐字转录、唯一可写落点只出现一处。
- 单一权威的形状：家族/落点/报告文件名/两条命令全部从 `metrics_report` 现场取，取不到算仪器坏（`c.fail` + 早退
  `:113`），派生结果自身不可用（家族被抽干、落点被抽空）也算仪器坏并早退 `:118`。这与片 026/027/029 的立场一致：
  **被检集合不能给自己定下限**。
- 抄检窗口沿用 60 字档（`OVERLAP_RUNS = 60`），对象两份：生成器 docstring + ROADMAP 的 B6 条目。两个来源各配
  一条正控（片 027 给 B5 补 maintainability 来源是同一课：只喂一份时第二份坏了没人知道）。
- 角色正文不抄生成器字段定义与 ROADMAP 条目，只引用路径与家族名；这条让「单一权威」在措辞层面成立，也让
  `reporter:generator-text-restated` 能把「抄一份过去」这种最省事的写法当场判红（读数里打印抄来的那段）。
- `复算=否` 那三行（按天提交数、总提交数、案例库末次提交日）在角色里必须连带「只判形状」一起转述。这条单独
  有下限 `MIN_SHAPE_ONLY_LINES = 1`，因为它防的不是抄错数，而是**读者以为历史读数随时能复现**。
- 快照与注入锚点是**耦合**的：`evals:pointer-stale` 的 `old` 串钉在 `product.md` 的原文上，刷新快照必须同时改
  锚点，否则整轮报 `HARNESS — anchor not found`（仪器坏，不是判据红）。本片又走了一遍这一对，所以快照刷新与
  锚点跟随在同一轮里完成，并把两侧读数都留在块 06。
- core 档注册只加三行（`:101-103`），不新造档位：与片 027/028/029/030 同一结论——毫秒级文本判定属 core，
  precommit 档留给贵检查。
- 本片真实踩到并已修的一件事写在这里而不是只写在 ROADMAP：`b031-pointer-fix.py` 的补注模板把「整句」当替换值
  写进一行**已经带着那半句**的正文，落盘后同一半句被连印了两遍（形如「（第 10 条基线所在元组；」×2）。它读起来
  和真话一样，`文件:行` 扫描也放它过去——是回读时人眼发现的。所以模板改成子串替换，并新增 `b031-dup-scan.py`：
  全库找紧邻重复 8–24 字且含 ≥6 个汉字的子串，**自带正控**（把那条坏句喂回同一套匹配必须命中）。
  第一版不加汉字门槛时 `raw_hits=166` 那 166 行会被整批报成正文命中（缩进空白、表格分隔线与编号序列天然同形）——
  那种判据的下场就是被人调宽，收窄到措辞层才有意义；门槛换掉的到底是什么，由块 05 现场 import 同一套匹配分组复算承重。
- 这条扫描在写完 ROADMAP `实现要点` 之后立刻又响了一次，红因是我在描述那个缺陷时**把坏句原文抄进了正文**
  （ROADMAP 与本片卡片各有；那一轮的整轮读数只留在会话输出、没有落盘捕获，所以这里不报具体条数）。
  这次仍然改措辞（写成「同一半句连印两遍，形如…」），
  没有把 `floor=0` 改成「允许引用」——一条要靠例外才能过的判据，下次同类坏句照样过得去。
  这次转红已经做成可重跑场景（块 01 RED-5：把 `实现要点` 里的回避式描述换回坏句原文，重复短语扫描必须报
  `DUP .codex/ROADMAP.md:118` 与 `known_old_dup hits=1` / `dup-scan rc=1`），所以它不再依赖「后来有人看见了」。三处 `:118` 都是当时的现场读数：片 032 给 B0 加了 `实现要点` 一行，重跑该场景现在打印的是 119，本卡不把历史读数改成现值。

## Verification report

- Verdict: PASS（角色守门末态 28 条 + 一次同命令 RED/GREEN（五状态、两对不重叠读数、字节还原、坏句回插必响）+ 209 例注入含
  12 基线 + core/precommit 两档整跑 + 指针复算 214 条 + 全库活引用扫描 + 重复短语扫描（正控在场）+ 快照刷新
  零差异；见块 00–06 与 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-metrics-reporter-role-031.txt`
  块 00（角色守门 28 行末态逐字）、块 01（RED/GREEN 十断言五场景与 sha256-12 字节还原，含两对「角色守门红 /
  指标守门绿」不重叠读数）、块 02（209 例整轮 + 12 条基线 + 本片 35 例 `reporter:*` 逐字）、块 03（`run_evals` /
  `metrics_report` / `--list` / `--tier core` / `--tier precommit` / `validate_tasks`）、块 04（指针驱动 214 条与
  九张卡片 46 处区间改写读数）、块 05（全库 `文件:行` 活引用扫描 + 重复短语扫描）、块 06（本片尺寸、快照刷新
  读数、`git diff --stat HEAD` 与 `android/**`/`frontend/**` 零命中）。
  环境：Windows 10.0.26200 / x64 / Python（`sys.executable`，一律 `-X utf8`）/ Git Bash。
  捕获文件与驱动留在 gitignore 的 `artifacts/tmp/`。**末态一套用 `runF-` 前缀**（装配驱动只读这些）：
  `runF-reporter.txt`（块 00）、`runF-redgreen.txt`（块 01，十断言）、`runF-selftest.txt`（块 02，209 例 + 12 基线）、
  `runF-evals.txt` / `runF-metrics.txt` / `runF-write.txt` / `runF-reads.txt` / `runF-list.txt` / `runF-core.txt` /
  `runF-precommit.txt` / `runF-validate.txt` / `runF-roadmap.txt`（块 03）、`runF-pointers.txt` 与
  `run116-pointer-fix-apply.txt`（块 04）、`runF-citations.txt` 与 `runF-dupscan.txt`（块 05）、
  `runF-sizing.txt` 与 `runF-snapshot.txt`（块 06）。
  中途轮次同样留在原处可查：`run115-pointer-fix-dry.txt`（落盘前那轮 dry-run，`EDITS applied=46 bad=0 files=9`；
  它之前两轮仪器侧失败 `bad=10`（needles 少了反引号、`guards-list` 被条目里的 `]),` 骗走）与 `bad=2`（区间把空行算进去）
  **只留在会话输出里、没有落盘捕获**，所以证据文件不引用它们，只引用同驱动那两轮 `bad=0` 的 dry/apply）；
  `run119-pointers.txt`（卡片在场前 190 条）、
  `run120-selftest.txt`、`run128-dupscan.txt`（角色面判据刚落地那轮）、`run132-dupscan.txt`（措辞修完之后）、
  `run117-snapshot-diff.txt`（刷新前 `changed_lines=[5]`）与 `run118-snapshot-write.txt`（刷新后零差异）、
  `run130-redgreen.txt`（八断言版本，RED-5 之前）、`run133-*`/`run134-reads.txt`/`run135-*`–`run146-*`（卡片
  加长过程中的各轮）。驱动 `b031-redgreen.py` / `b031-sizing.py` / `b031-pointer-fix.py` /
  `b031-pointers.py` / `b031-dup-scan.py` / `b031-evidence.py`（都在 gitignore 的暂存目录里，不进版本库）。
  本片生产行数以 `artifacts/tmp/b031-sizing.py` 现场打印为准：守门 186 行 + 注册 3 行 = `total=189`，
  与 front matter 的 `estimated_production_lines: 189` 同源。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除或放宽任何既有判据。
  两处「形状」层面的改动都朝更严的方向：`LANDING_MARK` 撞车改的是角色措辞而不是正则（块 02 CASE 198 仍红）；
  三条合取判据把下限抬到实测减一（CASE 192/194/195 的 `measured=0/1 floor=2` 就是抬完之后仍然能被单条注入打红的
  读数）。片 030 的 19 例 `metrics:*`、片 029 的 29 例 `keeper:*` 与 11 条既有基线在本轮整跑里逐条仍 `OK`，
  覆盖「新增第 12 条基线与第 5 份抄检来源没有把既有家族的正控搞失灵」。
- Gaps: ① 本片没有让角色真的接一次单——`metrics-reporter` 的第一次真实产出仍是片 030 生成的那份报告，
  「角色正文里的动作被执行过」目前只有仪器侧证据（块 01 的 RED/GREEN 与块 02 的注入），没有独立会话派单记录。
  ② `--tier core` 整跑 32 条里 `check_metrics_reporter_agent` 与 `check_metrics_report` 各自判一半，两者同时红的
  归因（是报告坏了还是正文坏了）要靠读数标签，本片没做归因表。③ 重复短语扫描只判「紧邻重复」，判不出
  「同义重写两遍」那类；它的定位是拦住修正工具造出的机械重复，不覆盖语义冗余。④ 本片卡片的行数本身会推歪
  `b031-citations`/`metrics_report` 的卡片计数，所以块 03/块 06 的报告读数取的是**卡片在场之后**重生成的一次，
  并注明 `复算=否` 那几行随提交推进（`total-commits` 在末态报告里是 `git rev-list --count HEAD` 的现场值）。
- Risks: 角色正文措辞与 17 条正则绑得紧，后续改写角色句子的片必须重跑块 00（这条已写进角色 `<contract>` 的
  「逐字读数」义务，但没有机器强制「改了角色就必须重跑守门」——`run_all_guards.py --tier core` 会跑到守门本身，
  这就是它的兜底）。第二个风险是取样面继续加长：`checkers_selftest.py` 已被四片推歪过，
  `build_workspace()` 里凡是穿过它的区间都会漂，`b031-pointers.py` 的 214 行表是下一片的接手点。

## Handoff notes

- Completed: B6 角色面全部落地——角色两份（逐字相同，`toml_chars=4691 md_chars=4691 equal=True`）、
  成对守门 28 条判定全绿、同一条命令十断言 RED/GREEN（含坏句回插必响的 RED-5）、35 例 `reporter:*` 与第 12 条基线与第 5 份抄检来源、core 档注册三行、
  `HARNESS.md:13` 第 7 步接线、ROADMAP B6 三节回写（`check_roadmap_and_routing` 仍 `0 check(s) failed`）、
  B2 基线快照重转录零差异 + `evals:pointer-stale` 锚点跟随、九张卡片 46 处区间引用复算（`stale=0`）、
  全库重复短语扫描（含正控）。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① B6 两片（片 030 指标面 + 本片角色面）都在场，ROADMAP 的 B6 可标交付完成；下一片按
  `check_roadmap_and_routing.py` 与 ROADMAP 的顺序取 B0（`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:98`
  的 `DEFERRED:待 B4` 仍欠着）或 C2 `loop-watcher`。② 若下一片继续给 `checkers_selftest.py` 顶部加行，
  先接手 `b031-pointers.py`（把表里的 `SELFTEST` 区间行改成本片的实测值再跑），不要手写行号。
  ③ D11（成对 `.md`/`.toml` 的逐字重复由判据钉住而非生成器消除）仍开着，`code-simplifier` 是它的候选执行者。
- Watch-outs: 快照与注入锚点必须同轮改（否则 `HARNESS — anchor not found`，那是仪器坏不是判据红）；
  别用 `git checkout --` 还原本片注入过的文件（`core.autocrlf=true` 会把 LF 换成 CRLF，逐字节相同会在无语义
  变化时失败）；新增抄检来源时记得同时给前缀闸门加一项，否则抄检用例红得没有指向性；
  用脚本改写卡片正文时，替换值要取子串而不是整句——本片就是这么造出一句重复短语的。
