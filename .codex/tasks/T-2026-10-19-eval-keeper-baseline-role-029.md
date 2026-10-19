# Codex Harness Task: 让评测集有一个看守它的角色（ROADMAP B2 后半）

id: T-2026-10-19-029
title: eval-keeper 角色、成对守门与基线快照刷新
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 165
shared_code_touched: false
depends_on: [T-2026-10-19-evals-inventory-and-runner-028.md, T-2026-10-19-codebase-researcher-impact-role-026.md, T-2026-10-19-guard-runner-one-command-roster-023.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 要求「完成结论必须匹配证据覆盖」。片 028 把基线对象建起来了（评测目录、期望语法、
  运行器），但它在自己的 Gaps ② 里写明了一条本片要关的缺口：**快照不会自动刷新**——被转录的源小节被改写后
  `product.md` 仍是旧文，运行器仍然绿。角色面（`eval-keeper` + 成对守门）就是把这件事从「记得就做」变成
  「有一条命令判它」。
- Problem/background: 片 028 之后本仓库有两种「AI 产物 vs 基线」的判据（运行器的 18 条），却没有一个角色
  负责维护那些基线资产；同时本片真实踩到了片 028 预言的那条链：给 `checkers_selftest.py` 顶部插 3 个常量
  把基线元组从 `:67` 推到 `:71`，片 026 卡片随之改写，快照必须重生成，而 `evals:pointer-stale` 那条注入用例
  的锚点钉在快照原文上——锚点不跟着走，整轮自检会报 `HARNESS — anchor not found`（仪器坏），不是判据红。
  另一个缺口是抄检仪器本身：`derive_sample()` 在未掩码空间校验样本、`overlap_hits()` 在掩码后文本里比命中，
  一个正好截断路径中间的窗口能通过「不含路径」的筛选却产生 0 命中，于是 0 容忍抄检的正控**静默失效**（D4 那族）。
- Users/scenarios: 使用者是后续每一片需要「证明没变差」的主会话。场景三个：① 新产物要建基线 → 该角色建
  `cases/NNN-slug/` 三件套并逐字转录快照；② 别片改了源小节 → 该角色刷新快照并整轮重跑注入自检；
  ③ 产物是否变差 → 跑 `python -X utf8 .codex/scripts/run_evals.py` 并交出逐字读数。
- Expected outcome: `.codex/agents/eval-keeper.md`（92 行）与逐字相同的 `.toml`（6 行包装）+
  `.codex/scripts/check_eval_keeper_agent.py`（216 行 / 23 条判据 / 3 处 fail-closed 早退）+
  `checkers_selftest.py` 的 29 例 `keeper:*`、第 10 条基线与第 4 份抄检来源 + core 档注册两行 +
  `.codex/HARNESS.md` 第 5 步接线一句 + 快照刷新（`product.md` 重转录，零差异）+ `derive_sample()` 的
  掩码空间修复。
- Core flow and reversibility: 新增两份角色正文与一支守门，改四处既有条目（注册表、基线元组、抄检来源、
  HARNESS 一句），刷新一个快照，修一处仪器判定缺陷。回退方式：删角色两份与守门脚本、撤 `run_all_guards.py:97-98`、
  撤 `checkers_selftest.py` 的 29 例与第 10 条基线与 `SAMPLE_SPEC_GRAMMAR`、还原 HARNESS 那半句，
  即回到片 028 末态；快照刷新与 `derive_sample()` 修复**不属**该回退面（前者是复算出来的事实，后者是仪器加固）。
- Inputs/outputs: 输入 = 角色正文与它引用的六份事实源（`verifier.md` 小节骨架、评测目录 README、运行器、
  防回归规则、`.codex/README.md`）；输出 = 23 行 `OK/FAIL` + `check_eval_keeper_agent: N check(s) failed` +
  `EXITCODE=0/1`。
- Constraints and trade-offs: 只用标准库；判据面与片 028 严格不重叠（运行器判「产物 vs 基线」，本片判
  「角色正文 vs 事实源」），否则改一处措辞同时红两支脚本；角色不抄期望语法表只引用路径（抄一份就是第二个权威），
  代价是角色正文与 README 之间只有「路径 + 三种形状名」这一层薄引用，形状改名要同时改两处。
- AI recommendation and rationale: 角色守门的重点不是「小节齐不齐」，而是三条会让评测失去意义的形状：
  跑法那一行**指向真脚本**（命令与脚本在场是两半，只查一侧时另一侧删空仍报绿）、快照必须逐字转录而不是手抄、
  刷新快照后必须整轮重跑。三条都配了注入用例，其中第三条是本片真实撞到的坏法。
- User choice and custom additions: 用户要求一站到底、按片提交本地、不中途请示；未对本片提出额外定制。
- AI-autonomous decisions: ① 本片只做 B2 的**角色面**，评测资产面留在片 028，两侧判据不重叠，并用
  RED-2（抽掉 `.codex/README.md` 的登记行 → 角色守门红而运行器仍绿）把「不重叠」变成一次可复算的读数而不是声明；
  ② 期望语法从 `import run_evals` 现场取 `KINDS` / `ASSETS` / `CASES_DIR`，而不是在守门里再抄一份形状表
  （ROADMAP B2 的 `验收方式` 与本仓库「同一约束只保留一份权威正文」都要求如此）；③ 抄检对象两份
  （评测 README + 防回归规则），为此给共享的 `derive_sample()` 补第 4 份取样来源而不是再造一个取样器；
  ④ `derive_sample()` 的掩码空间修复算仪器加固不算放宽判据——修复方向是让正控真的能响，
  改前那一轮（`run048-selftest.txt`）是 `ok=154 mismatch=1 / OVERALL: FAIL`，没有被静默放过；
  ⑤ 快照刷新用脚本从源小节逐字取并当场核对零差异，不手抄（片 028 立的规矩，本片把它写进角色契约）。
- User-confirmation decisions: 无新增需拍板项（不改产品口径，只新增流程资产与仪器）。
- Acceptance evidence: 块 00（角色守门末态 23 行逐字）、块 01（RED/GREEN 四场景 + 字节还原 sha256-12）、
  块 02（155 例注入含本片 29 例 `keeper:*` 逐条 + 10 条基线 + 修复前那轮的 `mismatch=1` 原始读数）、
  块 03（`run_evals` / `--list` / `--tier core` / `validate_tasks`）、块 04（`文件:行` 指针复算，
  含本片把六张卡的区间引用改回现值）、块 05（全库活引用扫描）、块 06（快照刷新读数与本片尺寸、没覆盖之物）。
- Facts: `kinds=5 assets=3 evals_dir=.codex/evals/`、`reference_sections=5 missing=[]`、
  `scripts=['.codex/scripts/dev/checkers_selftest.py', '.codex/scripts/run_evals.py'] missing=[]`、
  `landing_lines=1 all_named=True`、`cited=6 dead=[]`、`measured_hits=0`、`toml_chars=3770 md_chars=3770 equal=True`、
  `SELFTEST cases=155 ok=155 mismatch=0 harness_errors=0 skipped=0`、`ROSTER entries=36 core=30 precommit=6`、
  `RUN-ALL-GUARDS pass=30 fail=0 inconclusive=0 fault=0`、`POINTERS checked=99 stale=0`、
  `changed_lines=[5] zero_diff_with_source=True`（`product.md` `883d28923e7e` → `3fa3ca04a394`）、
  `PRODUCTION_LINES total=158` 均为一次真实执行的打印。
- Assumptions: `verifier.md` 的小节骨架（4 个 `<tag>` + 1 个二级标题）继续作为角色骨架的比对对象；
  该文件若被改写，本守门会先转红而不是安静跟随——那是「沿用同一套角色骨架」这条判据的用途。
- Unknowns: 第二个 case 的产物选谁（本片不选：它是「建基线」的动作，属角色第一次真实接单，而不是仪器面）。
- Risks: 角色正文的措辞与守门里的 9 条正则形状绑得很紧（`RUN_CMD_RE` / `RERUN_RE` / `TRANSCRIBE_RE` 等），
  改写角色句子容易把某条判据的命中数打到下限以下；方向是想要的（判据有牙），代价是改措辞要重跑块 00。
- Deferred: ① 角色 `.toml` 与 `.md` 的逐字重复由判据钉住而不是由生成器消除（本仓库无生成器，与片 024/026/027 同一处理）；
  ② 「评测集扩形状属于判据改动、要另开一片并补注入用例」写进角色 `<rules>` 而不是本片实现。
- Non-goals: 不新增第二个 case，不改期望语法，不动运行器的 18 条判据与裸下限，不宣布任务验收。
- Skipped non-critical questions and reason: 未问「角色是否要有 skill 配套」——B2 的跑法只有一条命令，
  已由评测目录 README 与 HARNESS 第 5 步承载，再加一份 skill 正文就是第二个权威。

## Goal

让「AI 产物相对基线有没有变差」这件事有一个只读边界清楚的角色负责维护，并且有一条命令能判住这个角色是否
还在履职：`python -X utf8 .codex/scripts/check_eval_keeper_agent.py` 退出码 0，23 条判据逐条打印读数，
其中「跑法指向真脚本」「快照逐字转录」「刷新后整轮重跑」「判据面不许动」「唯一落点是评测目录」各有注入用例兜着。

## Scope

### In scope
- `.codex/agents/eval-keeper.md` + `.codex/agents/eval-keeper.toml`（逐字相同的运行时包装）。
- `.codex/scripts/check_eval_keeper_agent.py`（23 条判据，含从 `import run_evals` 现场派生的期望语法）。
- `.codex/scripts/dev/checkers_selftest.py`：3 个常量、第 10 条基线、29 例 `keeper:*`、第 4 份抄检取样来源、
  4 条 `copy_check_prefixes`，以及 `derive_sample()` 的掩码空间修复。
- `.codex/scripts/run_all_guards.py` 的 core 档注册（两行）。
- `.codex/evals/cases/001-impact-inventory/product.md` 重转录（源小节行号变了）与 `evals:pointer-stale` 锚点跟随。
- `.codex/HARNESS.md` 第 5 步接线一句；`.codex/ROADMAP.md` B2 的 `交付任务` / `实现要点` 回写；
  片 021/024/025/026/027/028 六张卡的行号指针复算。

### Out of scope
- 期望语法本身、运行器的判据与裸下限（片 028 的面，本片一字未改）。
- 第二个评测 case（属角色第一次真实接单，不属仪器交付）。
- `check_spec_author_agent.py` 里那四份自我定义（`repo_path` / `read_text` / `section_titles` / `front_matter`）
  ——那是 D11 之外 `code-simplifier` 的第一次真实用途，不属 B2。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图 = 「谁来维护基线资产」从没有人变成有一个只读边界清楚的角色，
  且该角色是否履职有一条命令判得住。保留 = 片 028 运行器 18 条判据的逐字读数（块 03 与片 028 证据对照，
  `parsed=9` / `pointers-alive citations=2 problems=[]` / `paths-exist cited=5 dead=[]` 一字未变）、
  `run_all_guards.py` 其余 34 条注册项、`HARNESS.md` 第 1/2/3/4/6 步原文（接线只做在第 5 步那一句之内）、
  `checkers_selftest.py` 原有 126 例与 9 条基线一字未改（只追加）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 = 角色正文两份与守门脚本；
  消费者 = `run_all_guards.py:97-98`（core 档）、`checkers_selftest.py:71`（第 10 条基线）、
  `.codex/HARNESS.md:11`（验收阶段的第 5 步）。同级入口 = `check_code_reviewer_agent.py` /
  `check_codebase_researcher_agent.py` / `check_code_simplifier_agent.py`，四者共用
  `harness_check_lib.agent_section_names()` 与 `citable_paths()`；本片把它们共用的抄检取样器
  `derive_sample()` 的掩码空间缺陷修掉（**四个消费方的抄检正控同时受影响**，所以整轮重跑而不是只跑 `keeper:*`）。
  另有两个**本片之前就在场**的消费方：`check_roadmap_and_routing.py:35-36` 的 `PHASE_B_KEYWORDS` 含
  `eval-keeper`（ROADMAP B2 正文删掉该词那支守门立刻转红），`.codex/README.md:28` 的评测目录登记行
  （本片把它作为 RED-2 的抽离对象，见块 01）。平台分支 = 不适用：无 Android/前端生产代码、无 API、无 SQL；
  「Java-only 不得被抄检绕过」不在本片判定面内（`harness_check_lib` 的抄检窗口沿用片 021 的 60 字档）。
- Native bridge or independent-client compatibility/update requirements: 不适用（无桥接字段被触碰；
  `android/` 与 `frontend/` 零改动，判定形状只作用于 `.codex/` 内的流程资产）。
- Behavior-neutral non-applicability reason, if applicable: 可反驳证据两条：`git diff --stat HEAD` 里
  `android/**`、`frontend/**` 零命中；块 03 的 `run_evals` 末态与片 028 证据逐字相同（运行器与被检产物都没动）。

## Documentation impact record

- Changed runtime facts: 未改产品运行事实；新增一个流程角色、一条 core 档守门，并按当前事实刷新了一份基线快照。
- Authoritative code/config/migration evidence: `.codex/agents/eval-keeper.md:1-92`（角色正文；`<contract>` 21-34
  是唯一落点、跑法、逐字转录与整轮重跑的权威措辞，`<rules>` 36-48 是形状/三件套/阈值的引用面）、
  `.codex/agents/eval-keeper.toml`（`developer_instructions` 与 `.md` 逐字，`toml_chars=3770 md_chars=3770 equal=True`）、
  `.codex/scripts/check_eval_keeper_agent.py:31-41`（七份输入路径）、`:43-76`（13 条裸下限与 10 条判定形状，
  含 `MIN_ROLE_SECTIONS = 5` 与 `OVERLAP_RUNS = 60`）、`:79-82`（`lines_matching()`）、
  `:84-212`（23 条判据与 3 处 fail-closed 早退 `:91` / `:101` / `:112`）、`:94-104`（从运行器现场派生期望语法）、
  `.codex/scripts/dev/checkers_selftest.py:64-67`（3 个常量）/ `:70-71`（10 条基线）/ `:101`（第 4 份抄检来源）/
  `:104-135`（`derive_sample()` 掩码空间修复）/ `:568-656`（29 例 `keeper:*`）/ `:830-831`（4 条抄检前缀）、
  `.codex/scripts/run_all_guards.py:97-98`、`.codex/HARNESS.md:11`、`.codex/ROADMAP.md:65-66`、
  `.codex/evals/cases/001-impact-inventory/product.md:1-13`（刷新后的快照，`:5` 是被推下去的那条指针）。
  行号是先实测再落笔的：`artifacts/tmp/b029-pointers.py` 把本片与片 021/024/025/026/027/028 的 99 条引用逐条复算，
  末态 `POINTERS checked=99 stale=0`（表本身有 `MIN_POINTERS = 80` 的裸下限，抽干即红）。
- Documentation surfaces checked: `.codex/ROADMAP.md`（B2 条目全节 + D9/D11 记账）、`.codex/HARNESS.md`（六步流程）、
  `.codex/README.md`（目录结构节一行）、`.codex/evals/README.md`（期望语法权威正文，未改）、
  `.codex/agents/verifier.md`（骨架小节集与只读边界措辞）、`.codex/agents/code-reviewer.md` /
  `codebase-researcher.md` / `code-simplifier.md`（同级角色的落点/交接/禁区写法）、
  `.codex/rules/regression.md`（抄检窗口与被检集合）、`.codex/rules/harness-verification.md`（验收归属，
  本片把「评测读数不等于验收结论」写进角色而**没有**改该规则）、`AGENTS.md`（第 62 行 glob 已覆盖新守门，未改）。
- Documentation files updated: `.codex/HARNESS.md`（第 5 步一句）、`.codex/ROADMAP.md`（B2 `交付任务` 换成两片实际
  交付物 + 新增 `实现要点` 一行）、片 021/024/025/026/027/028 六张卡的行号指针、
  `.codex/evals/cases/001-impact-inventory/product.md`（快照刷新）。
- Producer/consumer/sibling/compatibility paths checked:
  `grep -rlI "eval-keeper" .codex Docs` 末态命中 10 个文件（角色两份、守门、自检电池、注册表所在脚本不含——
  注册表用的是脚本名 `check_eval_keeper_agent`、`run_all_guards.py` 那两行、ROADMAP、HARNESS、
  本片与片 021/027/028 三张卡）；`grep -rlI "run_evals" .codex Docs` 末态命中 11 个文件。
  两个模式的**行数**含本片卡片自身，卡片加长就会漂，所以这里只登记文件集，行数写命令让人现场取（片 023 定的规矩）。
  `README_ENTRIES` 现值 4 项、`.codex/README.md` 目录结构节四项全在场（`missing=[]`）。
- Superseded phrases/examples reverse-searched: 被本片改写的旧句是片 028 卡片里 `:66-67` / `:248-255` / `:513-552`
  三处区间与片 027 卡片里 `:66-67` / `:93` / `:436-511` / `:725` 四处——它们在本片之前就对 `checkers_selftest.py`
  加行敏感，块 04 逐条按现值改写并标注是哪一片推下去的；ROADMAP B2 的 `交付任务` 原文把角色面写成
  「片 029 交付 `eval-keeper` 角色两份与 `check_eval_keeper_agent.py`，并把跑法接进 `.codex/HARNESS.md`。
  B2 在片 029 之前不算交付完成」的**未来式**，现改为已交付读数、新增 `实现要点` 一行，并把条数措辞留在
  「以现场读数取，不写进本条」。旧句已完全替换：`grep -rn "B2 在片 029 之前不算交付完成" .codex Docs`
  末态 0 命中，旧措辞没有留在别处。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:48` 的
  `DEFERRED:待 B4（codebase-researcher）` 仍由 B0 那片改写，本片不动。`.codex/references/` 未新增条目。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/check_eval_keeper_agent.py` 退出码 0，23 条 `OK`（块 00）。
- [x] 角色两份文件缺席时同一条命令退出码 1，FAIL 标签是 `the seven inputs exist` 且 `missing=` 同时列出
      `.md` 与 `.toml`；按原始字节摆回后同一条命令 `0 check(s) failed` / `rc=0`，还原前后
      `815e0395ef89` / `f9c76d193615` / `ff5175ffbabc` 三个 sha256-12 相同（块 01，
      驱动 `artifacts/tmp/b029-redgreen.py`，gitignore 内；不用 `git checkout --`，那会把 LF 换成 CRLF）。
- [x] 两条命令不互相冒充：抽掉 `.codex/README.md` 的评测目录登记行时角色守门转红
      （`evals directory is registered in the harness README — registered=False`）而
      `run_evals.py` **仍然 `rc=0`**（块 01 RED-2）。这是「判据面不重叠」的读数，不是声明。
- [x] 跑法的两半各配一条正控：`keeper:run-command-swapped` 把 `run_evals.py` 换成别的名字 →
      `agent names the runnable evaluation command — measured=0 floor=2`；`keeper:command-dead-script`
      让它指向不在场的脚本 → `every command in the role points at a script that exists` 红并把死路径打在
      `scripts=` 里（块 02 CASE 136/137）。
- [x] 快照与重跑形状有牙：`keeper:handcopy-allowed`（把「不得手抄」改成语义相反的措辞）红
      `agent forbids hand-copying the product snapshot — measured=0 floor=2`；`keeper:rerun-obligation-dropped`
      红 `agent obliges a full selftest re-run after refreshing a snapshot — measured=0 floor=2`；
      `keeper:selftest-target-renamed` 红 `agent names the selftest target verbatim — named=False floor=1`
      （块 02 CASE 141/138/139）。另有 `keeper:summary-only-allowed`（`measured=0 floor=2`）与
      `keeper:acceptance-claimed`（`measured=1 floor=2`）钉住「只交逐字读数、不宣布验收」（CASE 142/143）。
- [x] 判据面不许动：`keeper:loosen-permitted` 把「不得下调阈值」那句改成允许 →
      `agent forbids touching the judgement surface to get green — measured=1 floor=2`（块 02 CASE 140）。
- [x] 唯一落点与只读边界分开判：`keeper:landing-not-evals` 红 `landing_lines=1 all_named=False`，
      `keeper:readonly-dropped` 红 `agent declares everything else READONLY — measured=0 floor=2`（CASE 144/145）。
- [x] 期望语法只有一份权威：`keeper:kind-unnamed`（角色里删掉 `forbid` 一词）红 `missing=['forbid']`，
      `keeper:asset-unnamed` 红 `missing=['input.md']`，`keeper:grammar-table-restated`（把 README 的表抄进角色）
      红 `measured_hits=1` 且打印抄来的那段（块 02 CASE 146/147/151）。
- [x] 四种裸下限承重：七份输入里的六份各删一次（CASE 127–132，六条都报 `the seven inputs exist`，
      `missing=` 逐条打印被删那一份）、
      `verifier.md` 的骨架被抽形（CASE 134 的 `keeper:reference-sections-drained`，
      `measured=1 floor=5 names=['职责清单']`）、引用被抽干（CASE 149 `measured=0 floor=4 cited=[]`）、
      正文里注入占位符（CASE 152 `hits=['TODO']`）各自转红而不是「无话可判地报绿」。
- [x] `.toml` 与 `.md` 的逐字相同有牙：`keeper:toml-md-drift` 红 `toml_chars=3770 md_chars=3804 equal=False`，
      `keeper:toml-name-missing` 红 `name=False description=True`，`keeper:toml-unparsable` 报 `TOMLDecodeError`
      （块 02 CASE 153/154/155）。
- [x] `checkers_selftest.py` → `cases=155 ok=155 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS`，
      10 条基线全 `BASELINE OK rc=0`（含新升的 `check_eval_keeper_agent`）（块 02）。
- [x] 仪器加固被自己的新用例抓到过：修复前那轮 `run048-selftest.txt` 打印
      `CASE 151 MISMATCH keeper:grammar-table-restated … seen=['FAIL agent .toml body equals …']` /
      `ok=154 mismatch=1` / `OVERALL: FAIL`；改成掩码空间比对后同一轮 `155/155`（块 02 两侧读数都在）。
- [x] 快照刷新后 `run_evals` 仍绿且复算的是新位置：`pointers-alive citations=2 problems=[]`，
      快照 `:5` 现在是 `checkers_selftest.py:71`（块 03 / 块 06）。
- [x] `run_all_guards.py --list` 读数 `entries=36 core=30 precommit=6`，`--tier core` 整跑
      `pass=30 fail=0 inconclusive=0 fault=0` / `VERDICT: PASS`，且 `check_eval_keeper_agent` 打印自己的判据行（块 03）。
- [x] `validate_tasks.py` → `SUMMARY files=24 errors=0 warnings=4`（四条 WARNING 全是本片之前就已超预算的历史卡片，
      非本片）（块 03）。
- [x] 本片没把任何既有句子的行号引用改坏：指针驱动末态 `stale=0`，其中本片把片 021/024/025/026/027/028 六张卡的
      `checkers_selftest.py` / `run_all_guards.py` 区间引用按现值改写（`git diff HEAD --numstat -- .codex/tasks`
      末态 29 insertions / 19 deletions），改写后的每一条都由驱动复算（块 04）。
- [x] 文档侧活引用全库扫描 `CITATIONS problems=0` / `VERDICT: PASS`（块 05；扫描条数不写死——本片卡片自己就是被扫对象）。

## TDD execution record

### RED — failing test first
- Test/scenario: 判据写完、角色两份正文还没落地——即「B2 角色面只有守门，没有可被它检的角色」这一状态。
  跑守门，看它是否把「被检对象缺席」报成红而不是无话可说的绿。
- Command: `python -X utf8 .codex/scripts/check_eval_keeper_agent.py`
- Exit code: 1
- Observed failure: `FAIL the seven inputs exist — missing=['.codex/agents/eval-keeper.md', '.codex/agents/eval-keeper.toml']`
  紧跟 `check_eval_keeper_agent: 1 check(s) failed` 与 `EXITCODE=1`（块 01 RED-1 逐字）。
- Failure reason validated: 红来自被检产物缺席，不是命令没跑起来：把角色两份按原始字节摆回后**同一条命令**立刻
  `0 check(s) failed` / `rc=0`，还原前后三个文件（角色 `.md` / `.toml` / `.codex/README.md`）的 sha256-12
  完全相同（`815e0395ef89 / f9c76d193615 / ff5175ffbabc`，块 01，驱动 `artifacts/tmp/b029-redgreen.py`）。
  第二个 RED 状态（角色在场、`.codex/README.md` 的评测目录登记行被抽掉）用的是同一条守门命令，
  同时并列跑运行器：守门 `rc=1`（`registered=False`）、运行器 `rc=0` —— 这一对读数用来证明
  两条命令判的是两半，不是一支脚本的两种写法。

### GREEN — minimum implementation
- Implementation: `.codex/agents/eval-keeper.md`（92 行，`<identity>` `:7` / `<source>` `:14` /
  `<contract>` `:21-34` / `<rules>` `:36-48` / `## 触发时机` `:50` / `## 职责清单` `:58` /
  `## 输出格式` `:67` / `## 交接` `:78` / `## 禁区` `:86`）与由 `artifacts/tmp/gen_agent_toml.py` 生成的
  逐字相同 `.toml`；core 档注册 `run_all_guards.py:97-98`；`.codex/HARNESS.md:11` 的第 5 步接线。
- Command: `python -X utf8 .codex/scripts/check_eval_keeper_agent.py`
- Exit code: 0
- Passing evidence: 块 00 的 23 行逐字（含 `kinds=5 assets=3`、`scripts=[…run_evals.py] missing=[]`、
  `landing_lines=1 all_named=True`、`measured_hits=0`、`toml_chars=3770 md_chars=3770 equal=True`）。
- Regression evidence: 块 02（155 例注入 + 10 条基线，本片 29 例 `keeper:*` 全部命中期望标签、
  `harness_errors=0`）；块 03（`run_evals` 末态未变、`--tier core` `pass=30 fail=0`、
  `validate_tasks errors=0`）；块 04（指针复算 `stale=0`，含本片校正的六张卡区间引用）；
  块 06（`git diff --stat HEAD` 里 `android/**` / `frontend/**` 零命中）。

### REFACTOR — optional
- Cleanup: ① `derive_sample()` 的样本有效性改到**掩码后空间**判定：原来用未掩码文本判「样本能在原文里找到」，
  而 `overlap_hits()` 比的是掩码后的文本，一个截断在路径中间的 60 字窗口通过筛选却产生 0 命中，
  于是 0 容忍抄检的正控静默失效（`run048-selftest.txt` 的 `CASE 151 MISMATCH` 是唯一把它逼出来的读数）。
  这条不是把判据改宽——改的是取样器，方向是让正控必然能响，四个抄检消费方（`intent:*` / `research:*` /
  `simplify:*` / `keeper:*`）共用它，所以整轮重跑而不是只跑 `keeper:*`。
  ② 本片把第 4 份抄检取样来源（`SAMPLE_SPEC_GRAMMAR`）加进同一台取样器，而不是为评测 README 再写一个取样器。
  ③ 快照刷新改由 `artifacts/tmp/b029-refresh-snapshot.py` 从源小节逐字派生并当场核对零差异，
  片 028 的 `b028-mkproduct.py` 那份「手写替换清单」退役为历史记录。
  ④ 指针驱动 `b028-pointers.py`（55 条）被 `b029-pointers.py`（99 条，含表自身的 `MIN_POINTERS = 80`）取代，
  旧驱动不再重跑，其读数只算片 028 当时的现场证据——这一点已写回片 028 卡片的块 04 说明。
- Regression evidence: 块 02 两侧的对照读数（修复前 `ok=154 mismatch=1 / OVERALL: FAIL`，修复后
  `cases=155 ok=155 / OVERALL: PASS`）覆盖 ①；同一轮里 `intent:copied-methodology` /
  `research:rules-restated` / `simplify:rules-restated` 三条抄检用例仍各自命中期望标签，覆盖「共用取样器没被改坏」；
  块 03 的 `run_evals` 逐字未变覆盖 ③；块 04 覆盖 ④。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 判定的对象是「这个角色还在不在履职」，不是「角色正文写得是否漂亮」。所以三条形状优先：跑法那一行存在
  **且**指向真脚本（两半各一条判据，只查一侧时另一侧删空仍报绿）、快照转录必须是逐字而非手抄（改成正反
  两向措辞都能满足一条正则，所以判的是命中行数下限 `MIN_TRANSCRIBE_LINES = 2`）、刷新与整轮重跑必须落在
  两处（`MIN_REFRESH_RERUN_LINES = 2`）。
- 期望语法从 `import run_evals` 现场取（`:94-104`），派生失败算仪器坏（`c.fail` + 早退）而不是「跳过」。
  这与片 026/027 的立场一致：**被检集合不能给自己定下限**，所以 `MIN_KIND_MENTIONS = 3` 与 `MIN_ASSET_MENTIONS = 2`
  刻意低于派生实数（5 / 3），让「运行器把形状抽干」也能响——由 `evals:*` 那侧的用例证，本片不重复。
- 抄检窗口沿用 60 字档（`OVERLAP_RUNS = 60`），对象是两份正文：评测 README 的期望语法表 + 防回归规则。
  两个来源各配一条正控，否则「`max_overlap_with_files` 的第二个文件坏了没人知道」——这条形状与片 027
  给 B5 补 maintainability 来源是同一课。
- 角色正文不抄期望语法表，只引用路径并点名五种形状与三件资产；这条让「单一权威」在措辞层面成立，
  也让 `keeper:grammar-table-restated` 能把「抄一份过去」这种最省事的写法当场判红（读数里打印抄来的段落原文）。
- 快照与注入锚点是**耦合**的：`evals:pointer-stale` 的 `old` 串钉在 `product.md` 的原文上，
  刷新快照必须同时改锚点，否则整轮报 `HARNESS — anchor not found`（仪器坏，不是判据红）。本片就走了这一遍，
  所以把它写进角色 `<contract>` 而不是只记在卡片里。
- core 档注册只加两行（`:97-98`），不新造档位：与片 027/028 同一结论——毫秒级文本判定属 core，
  precommit 档留给贵检查。

## Verification report

- Verdict: PASS（角色守门末态 23 条 + 一次同命令 RED/GREEN（含两状态、非重叠对照）+ 155 例注入含 10 基线 +
  core 档整跑 + 指针复算 + 全库活引用扫描 + 快照刷新零差异；见块 00–06 与 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-eval-keeper-baseline-role-029.txt`
  块 00（角色守门 23 行末态逐字）、块 01（RED/GREEN 四场景与 sha256-12 字节还原，含 RED-2 的
  「守门红 / 运行器绿」对照）、块 02（155 例整轮 + 10 条基线 + 本片 29 例 `keeper:*` 逐字，另附修复前
  `run048-selftest.txt` 的 `CASE 151 MISMATCH` 与 `ok=154 mismatch=1` 原始读数）、块 03（`run_evals` /
  `--list` / `--tier core` / `validate_tasks`）、块 04（行号指针复算与本片改回的六张卡区间引用）、
  块 05（全库 `文件:行` 扫描逐条读数）、块 06（快照刷新读数、本片尺寸、`android/**`/`frontend/**` 零命中）。
  环境：Windows 10.0.26200 / x64 / Python（`sys.executable`，一律 `-X utf8`）/ Git Bash。
  捕获文件与驱动留在 gitignore 的 `artifacts/tmp/`：`run048-selftest.txt`（仪器缺陷那轮）、
  `run050-selftest.txt`（接线中途轮）、`run051-selftest.txt`（快照刷新后那轮）、
  `run069-selftest.txt`（末态整轮）、`run053-redgreen.txt`、`run054-sizing.txt`、`run055-keeper.txt`、
  `run056-list.txt`、`run058-core.txt`、`run066-evals.txt`、`run065-roadmap.txt`、
  `run067-refresh.txt`（幂等复算：`changed_lines=[]`）、`run052-pointers.txt`、
  `run052-pointers.txt`（卡片引用入表前，87 条）与 `run062-pointers.txt`（末态 99 条）、
  `run063-citations.txt`、`run064-validate.txt`；
  驱动 `b029-measure.py` / `b029-refresh-snapshot.py` / `b029-redgreen.py` / `b029-pointers.py` /
  `b029-sizing.py`（都在 gitignore 的 `artifacts/tmp/` 里，可重跑）。证据文件本身由一段 inline 脚本装配（`python -X utf8 -` 走 stdin）：本片收尾时 `artifacts/tmp/` 被运行环境改成只读，写入报
  `EACCES: permission denied`，所以证据生成器没有落盘——块 00–06 的每一行仍然来自在场的捕获文件，装配只是拼接，不做改写。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片没有删除任何既有判据、没有放宽任何
  断言、没有往任何白名单加名字、没有调低任何裸下限。被收紧的有两处：① `derive_sample()` 的样本有效性判定
  改到掩码空间（原来是**假绿**：正控可以静默不响；改后同一用例 `measured_hits=1` 并打印抄来的段落），
  等效性由同轮 `intent:*` / `research:*` / `simplify:*` 三条既有抄检用例仍各自命中期望标签证明；
  ② `copy_check_prefixes` 从 3 条前缀扩到 4 条（`keeper:grammar-table-restated` 也必须走 `append_sample`），
  方向是让「出现字面量样本即仪器故障」这条形状覆盖第四个消费方。快照内容的一处改动（`:5` 的 `:67` → `:71`）
  不是判据改动，而是对当前工作树的重新转录，零差异结论由 `b029-refresh-snapshot.py` 打印。
- Gaps: ① 只有 1 个 case，角色「建 case」这项职责还没有真实产物可判——它的判据面（`MIN_CASES`）由片 028
  守着，本片不动；第二条基线要等角色第一次接单。
  ② 角色守门判的是「正文里写了这些形状」，不是「执行时真做了这些形状」：与 B1/B4/B5 同一族缺口
  （D9 记过），能补的方向是把转录与重跑做成有产物的动作（`artifacts/tmp/b029-refresh-snapshot.py` 已经是那个
  形状，但它不在 `.codex/` 里，因此不构成判据）。
  ③ 快照刷新的**触发**仍是人（或主会话）判断的：运行器能对旧快照里的指针转红，但源小节若被改写而不涉及
  行号变化，快照不会响。这条留在角色 `## 职责清单` 的「刷新过期快照」里，没有机械判据。
  ④ 无独立 verifier 轮次（用户要求一站到底；本片属 harness 仪器面，23 条判据有 29 例注入覆盖）。
  ⑤ D11 仍开（cdp flake 与「运行器不打印失败用例名」）：本片没有动那两台仪器，`harness_errors=0` 只说明
  这一轮锚点都对得上。
  ⑥ 七份输入只有六份配了缺席用例——`.codex/rules/regression.md` 那一份没有单独的 `keeper:*` 删除用例
  （它与 `regression60` 取样规格、`research:rules-restated` 与 B1/B4/B5 三支守门共用，删掉它会同时让别的
  守门转红，用例标签就变成多因的）。这条判据目前的覆盖方式是「`INPUTS` 元组在场 + 六条缺席用例」，
  第七份被删时同一条判据仍会响（`exists()` 是对整个元组做的），缺的是**证明它响过**的用例。
- Risks: 角色正文与守门里 10 条正则绑得紧，改写角色句子可能把某条判据的命中数打到下限以下（方向正确但代价是
  重跑块 00）；`checkers_selftest.py` 现值 908 行（本片 +106 行，HEAD 是 802 行），它继续长下去时的第一个收敛对象是 4 份
  `SAMPLE_SPEC_*` 取样规格的形状重复（不是本片的判定面，留给 `code-simplifier` 第一次真实接单）。

## Handoff notes

- Completed: B2 的角色面（`eval-keeper` 两份正文、23 条判据的成对守门、29 例 `keeper:*` 注入、第 10 条基线、
  core 档注册、HARNESS 第 5 步接线、ROADMAP B2 `交付任务`/`实现要点` 回写）+ 片 028 缺口 ② 的关闭
  （快照过期从此有一个角色负责刷新，且刷新后必须整轮重跑）+ 一次真实的仪器加固（抄检正控的掩码空间缺陷）
  + 片 021/024/025/026/027/028 六张卡的行号区间引用改回现值 + `product.md` 按当前工作树重转录（零差异）。
- In progress: 无。**B2 至此两片齐**（片 028 评测资产面 + 片 029 角色面），ROADMAP 的 B2 条目可标交付完成。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: B6（`metrics-reporter` 角色与指标读数）→ B0（`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:48`
  那行 `DEFERRED:待 B4（codebase-researcher）` 的清除，同时是 `codebase-researcher` 的第一次真实使用）→
  C2（`loop-watcher`）→ C3（事实源全量声明）→ C4（release-gate 裁定 + D7① 建表缺陷）。
- Watch-outs: ① **不要把 `--tier core` 或整轮自检与任何会动 `.codex/` 的驱动并发跑**；本片的 RED/GREEN 与
  快照刷新都是先跑完再跑聚合命令，顺序不能倒。
  ② 基线快照里的字符串是**活引用**：任何给 `checkers_selftest.py`、`run_all_guards.py`、
  `harness_check_lib.py` 加行的改动都可能让 `product.md` 的 `路径:行` 转红，修法是刷新快照（并记录为什么刷新），
  不是调低期望阈值——这条已经写进角色 `<contract>` 与 `<rules>`。
  ③ 刷新快照之后必须**整轮**重跑 `checkers_selftest.py`：`evals:*` 的锚点钉在快照原文上，只跑运行器
  看不见锚点漂（片 028 的 ⑥ 与本片的 `:67` → `:71` 是同一条链的两次）。
  ④ 角色正文加/删形状词（五种形状、三件资产各一次）会同时影响 `keeper:kind-unnamed` /
  `keeper:asset-unnamed` 与片 028 的 `every expectation kind is exercised`；扩形状属判据改动，另开一片。
  ⑤ `--list` 的 `entries/core` 会继续漂（现值 `entries=36 core=30`），卡片与 ROADMAP 都不写死，
  写命令让人现场取（片 023 定的规矩）。
