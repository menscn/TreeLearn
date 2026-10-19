# Codex Harness Task: 让 AI 产物有一条能重跑的基线判定（ROADMAP B2 前半）

id: T-2026-10-19-028
title: 评测集资产、期望语法与 run_evals 运行器
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 155
shared_code_touched: false
depends_on: [T-2026-10-19-codebase-researcher-impact-role-026.md, T-2026-10-19-code-simplifier-role-027.md, T-2026-10-19-guard-runner-one-command-roster-023.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 要求「完成结论必须匹配证据覆盖」。B2 要的是「每次改动都能证明没变差」——
  规则、角色正文、守门脚本都已有只读守门，唯独 **AI 产物本身**没有：片 026 那份影响面清单当时的验收
  方式是「人读一遍 + 一堆 `文件:行` 指针复算」，而指针复算是一次性驱动（写在 `artifacts/tmp/`，跑完就没了），
  下一次改动没有任何东西会重跑它。
- Problem/background: 缺的不是「再写一份检查」，而是**基线**这个对象不在场：没有期望语法，就没有「产物相对
  期望变差」这种可读状态；`.codex/evals/` 目录在 ROADMAP 里登记为 B2 的落点但从未建立；`.codex/README.md`
  的目录结构节因此少一项，而那一项正是 `check_roadmap_and_routing.py` 的 `README_ENTRIES` 要逐条查在场的。
  另一个真实缺口是共享派生：`文件:行` 形态的引用此前只在一次性驱动里解析过（`b026-pointers.py` 那一族），
  要在运行器里复算指针就必须再写一份解析器——那是本片必须避免的「第二份实现」。
- Users/scenarios: 使用者是后续每一片的主会话与评测看守角色（片 029）。场景有两个：① 改动完成后跑
  `python -X utf8 .codex/scripts/run_evals.py`，看基线产物是否仍满足期望；② 新增一条基线 = 新建一个
  `cases/NNN-slug/` 目录放三份资产，运行器自动把它纳入判定，不需要改运行器。
- Expected outcome: `.codex/evals/README.md`（52 行，期望语法与跑法的唯一权威正文）+
  `cases/001-impact-inventory/`（input 17 行 / expected 9 条 / product 13 行快照）+
  `.codex/scripts/run_evals.py`（18 条判据 + 1 条 NOTE，core 档注册）+
  `harness_check_lib.py` 新增共享的 `path_line_citations()` / `citation_problems()` +
  `check_roadmap_and_routing.py` 把 `.codex/evals/` 登记进 `README_ENTRIES` 并给这张清单本身加裸下限 +
  `checkers_selftest.py` 14 例 `evals:*` 与第 9 条基线。
- Core flow and reversibility: 新增两个目录与两份脚本改动、一处注册、两处文档。回退方式：删 `.codex/evals/`、
  删 `run_evals.py`、撤 `run_all_guards.py:95-96`、撤 `checkers_selftest.py` 的 14 例与第 9 条基线、
  还原 `README_ENTRIES` 那行与 `.codex/README.md` 那一行，即回到本片之前；上收进 `harness_check_lib.py` 的
  两个函数回退方式是搬回 `run_evals.py` 本地（判据读数不变，但那正是片 027 立誓要消灭的形状）。
- Inputs/outputs: 输入 = `cases/*/` 三份资产（期望文件由人写、产物快照由脚本转录自任务卡小节）；
  输出 = 19 行 `OK/FAIL/NOTE` + `run_evals: N check(s) failed` + `EXITCODE=0/1`。
- Constraints and trade-offs: 只用标准库；期望快照是**快照**而不是活引用（否则期望会随源卡片一起漂），
  代价是源卡片改写后快照不会自动跟上，靠 029 的角色职责「刷新过期快照」补；正向期望的载荷是中文子串，
  同义改写会转红——这是想要的行为，但意味着产物措辞改动要连着看这些计数读数。
- AI recommendation and rationale: 判据不查「写得好不好」，查三条结构性质：期望能被完整解析（认不出的行必须红，
  否则静默跳过等于期望被删）、每条正向期望真的钉住产物内容（`must`/`shape` 逐条打命中数）、
  **产物每条 bullet 至少被一条正向期望钉住**（这条是整套期望的防删结构：删一条期望既不破条数下限也不移除形状，
  运行器本来无从发现，而「这条 bullet 不再有任何期望判它」必然发生）。
- User choice and custom additions: 用户要求一站到底、按片提交本地、不中途请示；未对本片提出额外定制。
- AI-autonomous decisions: ① 本片只做 B2 的**评测资产面**，角色面（`eval-keeper.md` + 成对守门）留给片 029，
  两片判据面严格不重叠——否则改一处措辞同时红两支脚本；② 期望语法取五种形状而不是通用 DSL（通用比较器
  没有现存产物可验，五种形状都能在本仓库里立刻被真实用到）；③ 第一个 case 的产物选片 026 的
  `### Behavior impact`——它是本仓库唯一一份「AI 产出、被别人复核过、且已因指针漂移修过一次」的产物；
  ④ `文件:行` 解析上收进共享库而不是本地复制（片 027 立的规矩）；⑤ `.codex/evals/` 进 `README_ENTRIES`
  时顺手给那张表加 `MIN_README_ENTRIES = 4`——被检清单不能给自己定下限。
- User-confirmation decisions: 无新增需拍板项（不改产品口径，只新增流程资产与仪器）。
- Acceptance evidence: 块 00（`run_evals` 末态 19 行逐字）、块 01（同命令 RED/GREEN + 字节还原 sha）、
  块 02（126 例注入含本片 14 例 `evals:*` 逐条 + 9 条基线）、块 03（`--list` / `--tier core` /
  `validate_tasks` / `check_roadmap_and_routing`）、块 04（55 条 `文件:行` 指针复算，含本片修的 26 处历史漂移）、
  块 05（全库活引用扫描 `problems=0`）、块 06（本片尺寸读数与没覆盖之物）。
- Facts: `parsed=9`、`must: 逐字读数 bullets=2`、`pointers-alive citations=2 problems=[]`、
  `paths-exist cited=5 dead=[]`、`SELFTEST cases=126 ok=126 mismatch=0`、`ROSTER entries=35 core=29 precommit=6`、
  `POINTERS checked=55 stale=0`、全库活引用扫描 `problems=0`、`PRODUCTION_LINES total=155` 均为一次真实执行的打印。
- Assumptions: 片 026 的 `### Behavior impact` 小节继续作为基线产物的形状对齐对象；该小节若被重写，
  快照要么同步刷新要么整条 case 退役（不能留旧快照假装还在判）。
- Unknowns: 快照刷新由谁在什么时机做，属角色契约（片 029）；本片只保证「过期快照里的指针会被运行器复算」。
- Risks: 期望语法写进 `.codex/evals/README.md` 之后，改形状就是改判定语义——运行器不认识的新行会以
  「认不出的期望行」立刻转红，这是想要的行为，但意味着加形状必须同时改正则、README 与本片的注入用例。
- Deferred: 角色面（`eval-keeper.md`/`.toml` + `check_eval_keeper_agent.py`）留给片 029；
  再加第二、第三个 case（如设计 token 清单、parity 夹具清单）不在本片——它们需要各自的产物快照流程。
- Non-goals: 不做「由脚本自动从卡片生成并跟踪快照」的机制（那是同步器，不是评测）；不动 `android/**`、
  `frontend/**` 一行生产代码；不把 `run_evals.py` 写成通用比较器。
- Skipped non-critical questions and reason: 未问用户「基线要不要放 `Docs/`」——ROADMAP B2 已把落点写成
  `.codex/evals/`，且守门脚本的注册表只扫 `.codex/scripts/`，放别处要新造一条注册路径。

## Goal

`.codex/evals/cases/001-impact-inventory/` 三份资产在场且
`python -X utf8 .codex/scripts/run_evals.py` 退出码 0；当被检产物缺席、期望行认不出、阈值写成 `>= 0`、
删掉一条正向期望、产物缺一枚形状词、快照里出现死指针或 `待填` 时，同一条命令转红并指名是哪一条。

## Scope

### In scope
- `.codex/evals/README.md`（52 行）：跑法、三份资产表、五种期望形状、防删规则、快照与活引用的边界。
- `.codex/evals/cases/001-impact-inventory/{input,expected,product}.md`（17/17/13 行，9 条期望、4 条产物 bullet）。
- `.codex/scripts/run_evals.py`：183 行文件 / 122 行代码 / 18 条判据 + 1 条 NOTE / 4 处 fail-closed 早退 /
  2 条裸下限（`MIN_CASES`、`MIN_EXPECTATIONS`）+ 期望文件自带的阈值。
- `.codex/scripts/harness_check_lib.py`：新增 `CITATION_RE` / `path_line_citations()` / `citation_problems()`
  （174-207，+24 行代码；文件 297 行）。
- `.codex/scripts/check_roadmap_and_routing.py`：`README_ENTRIES` 增 `.codex/evals/`（39-43）+
  该清单自身的裸下限与判定（226-231，+7 行代码）。
- `.codex/scripts/dev/checkers_selftest.py`：14 例 `evals:*`（513-552）、`RUN_EVALS` 常量（59-63）、
  第 9 条基线（66-67）、`roadmap:*` 组新增两例（245-255）。
- `.codex/scripts/run_all_guards.py`：core 档注册两行（95-96）。
- `.codex/README.md`：目录结构节新增一行。`.codex/ROADMAP.md`：B2 条目的 `范围`/`验收方式`/`交付任务`。
- 本片复算并改回现值的历史卡片行号指针（片 021/024/025/026/027 五张卡共 26 处，非本片造成）。

### Out of scope
- `eval-keeper` 角色两份正文与其成对守门（片 029）。
- 第二、第三个评测 case；`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 的技术规格节（B0）。
- `check_spec_author_agent.py` 自建的 `repo_path`/`read_text`/`section_titles`/`front_matter`
  ——那四处才是 `code-simplifier` 第一次真实使用的候选，与评测面无关。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图 = 「产物是否变差」从一次性人工复算变成一条可重跑命令的读数，
  且期望被删时运行器必须知道（防删结构）。保留 = `run_all_guards.py` 其余 34 条注册项、
  `check_roadmap_and_routing.py` 原有全部判据（含 Phase B 七关键词那条）、`harness_check_lib.py` 既有 21 个
  模块级函数的签名与语义（本片只 +2，未改任何既有函数）、片 024/025/026/027 四支配对守门的判据读数、
  `checkers_selftest.py` 原有 112 例与 8 条基线一字未改。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 = `.codex/evals/cases/*/`
  三份资产（由人或 029 角色写）；消费者 = `.codex/scripts/run_all_guards.py:95-96`（core 档）、
  `.codex/scripts/dev/checkers_selftest.py:70-71`（第 9 条基线；片 029 在其上插了 3 个常量，行号由 `:66-67` 下推到
  这里，片 029 复算）、`.codex/evals/README.md:11`（跑法那行）。
  同级入口 = `check_code_simplifier_agent.py` 与 `check_codebase_researcher_agent.py`，三者共用
  `harness_check_lib.citable_paths()`；本片把「`文件:行` 区间解析」也上收到同一库，正是为了避免第四份实现。
  另有一个**本片之前就在场**的消费方：`check_roadmap_and_routing.py` 的 `README_ENTRIES`
  （`:39-43`）逐条要求 `.codex/README.md` 的目录结构节列出登记项——所以新增评测目录而不改 README 会当场转红，
  本片把这条 RED 也留成用例（`roadmap:evals-entry-removed`）。
  平台分支 = 不适用：无 Android/前端生产代码、无 API、无 SQL；`run_evals.py` 只读 `.codex/` 内文本。
- Native bridge or independent-client compatibility/update requirements: 不适用（无桥接字段被触碰；
  `android/` 与 `frontend/` 零改动）。
- Behavior-neutral non-applicability reason, if applicable: 可反驳证据两条：`git diff --stat HEAD` 里
  `android/**`、`frontend/**` 零命中；块 04 的 55 条指针复算 `stale=0` 证明本片没把任何既有句子的行号引用改坏。

## Documentation impact record

- Changed runtime facts: 未改产品运行事实；新增一个评测目录、一条 core 档守门、两个共享派生函数。
- Authoritative code/config/migration evidence: `.codex/evals/README.md:1-52`（期望语法的唯一权威正文）、
  `.codex/scripts/run_evals.py:33-49`（路径、2 条裸下限与三种形状的正则）、`:52-89`（`judged_units()` 与
  `parse_expectations()`，逻辑判定单元而不是物理行）、`:92-181`（18 条判据与 4 处早退）、
  `.codex/scripts/harness_check_lib.py:174-207`（`path_line_citations()` / `citation_problems()`）、
  `.codex/scripts/check_roadmap_and_routing.py:39-43` / `:226-231`、
  `.codex/scripts/dev/checkers_selftest.py:59-63` / `:70-71` / `:268-270` / `:528-566`（后三条由片 029 复算：
  那一片新增 3 个常量、29 例 `keeper:*` 和 4 条抄检前缀，把这三处整体下推）、
  `.codex/scripts/run_all_guards.py:95-96`、`.codex/evals/cases/001-impact-inventory/product.md:1-13`。
  行号是先实测再落笔的：`artifacts/tmp/b028-pointers.py` 把本片与片 021/024/025/026/027 的 55 条引用逐条复算，
  末态 `POINTERS checked=55 stale=0`；本片同时修掉那五张卡里 26 处**本片之前就已漂**的区间（块 04）。
  片 029 之后该驱动已被 `artifacts/tmp/b029-pointers.py` 取代（同一形状，把本片造成的漂移与 029 自己的
  新增区间一起复算），旧驱动不再重跑，其 55 条读数只算本片当时的现场证据。
- Documentation surfaces checked: `.codex/ROADMAP.md`（B2 条目 + D1/D9 记账）、`.codex/README.md`（目录结构节）、
  `.codex/HARNESS.md`（第 5 步验收，未新增落点故未改）、`.codex/tasks/_TEMPLATE.md`（`## Acceptance criteria`
  与 `### Behavior impact` 标题，快照的转录对象）、`.codex/rules/regression.md` 与
  `.codex/rules/documentation.md`（抄检窗口与「同一约束只保留一份权威正文」）、`AGENTS.md`（第 62 行 glob 覆盖）。
- Documentation files updated: `.codex/evals/README.md`（新建）、`.codex/evals/cases/001-impact-inventory/*`（新建）、
  `.codex/README.md`（目录结构节一行）、`.codex/ROADMAP.md`（B2 `范围`/`验收方式`/`交付任务`）、
  片 021/024/025/026/027 五张卡的行号指针（按块 04 现值校正，并把「曾经对、后来漂」写进卡片正文）。
- Producer/consumer/sibling/compatibility paths checked:
  `grep -rlI "run_evals" .codex Docs` 末态命中 7 个文件（本片卡片、ROADMAP、`run_evals.py`、`run_all_guards.py`、
  `checkers_selftest.py`、`.codex/evals/README.md`、case 的 `expected.md`）；
  `grep -rlI "\.codex/evals" .codex Docs` 末态命中 8 个文件（前七项去掉 `run_evals.py` 那条不算，加上
  片 027 卡、`check_roadmap_and_routing.py`、`.codex/README.md`）。两个模式的**行数**含本片卡片自身，
  卡片加长就会漂，所以这里只登记文件集，行数写命令让人现场取（片 023 定的规矩）。
  `README_ENTRIES` 现值 4 项，`.codex/README.md` 目录结构节四项全在场（`missing=[]`）。
  规则正文与 `AGENTS.md` 路由表不按脚本名枚举守门（`AGENTS.md:62` 的 glob 已覆盖），新守门只需进注册表与 ROADMAP。
- Superseded phrases/examples reverse-searched: 被本片改写的旧句是 ROADMAP B2 的 `验收方式` 一行——原文把
  「评测跑一遍看看」写成一句无命令的意图，现改为两条命令加各自判定面，并把条数措辞改成「以现场读数取，
  不写进本条」（片 023/027 定的「卡片不写死会漂的计数」规矩）。`docs index is a valid curated subset` 那类
  既有判据未改写；本片没有把旧写法留在别处（逐字读数见块 03）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:48` 的
  `DEFERRED:待 B4（codebase-researcher）` 仍由 B0 那片改写，本片不动。`.codex/references/` 未新增条目
  （评测语法是当前事实，不是迁移映射）。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/run_evals.py` 退出码 0，18 条 `OK` + 1 条 `NOTE`（块 00）。
- [x] 同一条命令在 `cases/` 缺席时退出码 1，FAIL 标签是 `eval cases directory exists` 且打印 `path=`；
      按原始字节摆回后同一条命令立刻 `0 check(s) failed` / `rc=0`，还原前后 5 个 sha256-12 相同
      （块 01，`BYTE-RESTORED: True`）。
- [x] 认不出的期望行转红：`evals:unknown-expectation-line` 打印
      `unknown=['- 这条大概是好的，看一眼就知道。'] parsed=9`（块 02 CASE 118）。
- [x] 恒真期望被拒：`evals:threshold-zero` 把 `>= 1` 改成 `>= 0` 后转红，打印
      `恒真期望（>=0）不该被允许: [('must', '(code evidence)')]`（块 02 CASE 120）。
- [x] 防删结构有牙：`evals:bullet-unpinned` 删掉 `- must: 逐字读数` 一条期望，条数下限（8≥5）与形状覆盖
      都不响，只有 `pins every product bullet` 转红 `unpinned=['Intended change and old behavior to '] bullets=4`
      （块 02 CASE 121）。
- [x] 三种正向形状各被抽干一次：`must` 由 `bullet-unpinned` 侧证、`shape` 由 `evals:shape-kind-dropped`
      （整形删除，`missing=['shape']`）与 `evals:shape-token-lost`（产物缺一枚词，`bullets=0 floor=1 tokens=3`）
      两侧各证一次（块 02 CASE 122/123）。
- [x] `forbid` 命中即红：`evals:forbid-hit` 往产物追加 `待填` → `bullets=1 expected=0`（块 02 CASE 124）。
- [x] 指针与路径复算作用于**快照**：`evals:pointer-stale` 把 `:57` 改成 `:99999` →
      `problems=[(('.codex/scripts/dev/checkers_selftest.py', 99999, 99999), 'line beyond eof=802')]`；
      `evals:path-dead` 造一个不存在的路径 → `dead=['.codex/HARNESX.md']`（块 02 CASE 125/126）。
- [x] 五种形状都要有 case 用到：`evals:shape-kind-dropped` 同时让 `every expectation kind is exercised` 转红
      （块 02 CASE 122）。
- [x] 裸下限承重：`cases/` 删空（`evals:cases-dir-deleted`，CASE 113）、期望清空
      （`evals:expected-emptied`，`measured=0 floor=5`，CASE 119）、产物空白
      （`evals:product-blanked`，CASE 117）三例各自转红而不是「无话可判地报绿」。
- [x] `.codex/evals/` 未登记进 `.codex/README.md` 时 `check_roadmap_and_routing` 转红
      （`roadmap:evals-entry-removed`，CASE 38）；把 `README_ENTRIES` 抽到下限以下时
      `registered-entry list is not drained below its floor` 转红 `entries=3 floor=4`（CASE 39）。
- [x] `checkers_selftest.py` → `cases=126 ok=126 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS`，
      9 条基线全 `BASELINE OK rc=0`（含新升的 `run_evals`）（块 02）。
- [x] `run_all_guards.py --list` 读数 `entries=35 core=29 precommit=6`，`--tier core` 整跑全绿且 `run_evals`
      打印自己的判据行（块 03）。
- [x] `validate_tasks.py` → `SUMMARY files=23 errors=0 warnings=4`（块 03）。
- [x] 本片新增的共享派生没有把任何既有句子的行号引用改坏：55 条 `文件:行` 复算 `stale=0`，
      其中本片修掉的 26 处历史漂移逐条列出（块 04）。
- [x] 文档侧活引用全库扫描 `CITATIONS problems=0` / `VERDICT: PASS`（块 05；扫描条数不写死——本片卡片自己就是被扫对象，
      卡片加长那条数就会漂，权威读数由命令现场取）。

## TDD execution record

### RED — failing test first
- Test/scenario: 判据写完、`.codex/evals/` 还没建立——即「B2 只写了运行器，评测资产还没落地」这一状态。
  跑运行器，看它是否把「被检对象缺席」报成红而不是无话可说的绿。
- Command: `python -X utf8 .codex/scripts/run_evals.py`
- Exit code: 1
- Failure reason validated: 红来自被检产物缺席，不是命令没跑起来：把 `cases/` 按原始字节摆回后**同一条命令**立刻
  `0 check(s) failed` / `rc=0`，还原前后 `.codex/evals` 四个文件与 `.codex/README.md` 的 sha256-12 完全相同
  （`a9bdb2fdc512, 677f5f3b9eee, 7e8380892972, 883d28923e7e / ff5175ffbabc`，块 01，驱动
  `artifacts/tmp/b028-redgreen.py`，gitignore 内）。第二个 RED 状态（目录已建、`.codex/README.md` 的登记行还没加）
  用的是另一条命令 `python -X utf8 .codex/scripts/check_roadmap_and_routing.py`，它不在本 RED/GREEN 对照里充当
  「同一条命令」，只作为同驱动里的 RED-2/GREEN-2 场景记录（`missing=['.codex/evals/']` → `missing=[]`），
  并留成注入用例 `roadmap:evals-entry-removed`（块 02 CASE 38）。
  不用 `git checkout --` 撤注入：本工作树 `core.autocrlf=true`，checkout 会按 smudge 重写行尾而使
  「字节相同」无从证明（片 021/025/026/027 记的同一课）。
  开发中途还撞过两次仪器自身的缺陷而不是判据红：`EXPECT_RE` 写死冒号让 `pointers-alive` / `paths-exist`
  两条计数型期望以「认不出的行」出现（那是作者少打一个标点而红，不是判据红）；`CITATION_RE` 的贪婪跨度把
  `` `.codex/evals/cases/*.md`（`.codex/scripts/validate_tasks.py:322` `` 吞成一条假引用。两者都先修形状再取读数。
- Observed failure: `FAIL eval cases directory exists — path=.codex/evals/cases` / `run_evals: 1 check(s) failed`。

### GREEN — minimum implementation
- Implementation: 新增 `.codex/evals/README.md`（52 行）与 `cases/001-impact-inventory/` 三份资产
  （9 条期望、4 条产物 bullet）；新增 `run_evals.py`（183 行文件 / 122 行代码 / 18 条判据 / 4 处早退 /
  2 条裸下限）；把 `文件:行` 区间解析上收进 `harness_check_lib.py`（+24 行代码）；
  `check_roadmap_and_routing.py` 登记 `.codex/evals/` 并给 `README_ENTRIES` 加裸下限（+7 行代码）；
  `checkers_selftest.py` 加 14 例 `evals:*`、`roadmap:*` 两例、第 9 条基线；`run_all_guards.py` core 档注册两行；
  `.codex/README.md` 目录结构节加一行；ROADMAP B2 三条字段回写。
- Command: `python -X utf8 .codex/scripts/run_evals.py`
- Exit code: 0
- Passing evidence: `run_evals: 0 check(s) failed` / `EXITCODE=0`，19 行逐字见块 00，其中关键几条为
  `OK cases/ holds at least MIN_CASES case dirs — measured=1 floor=1 cases=['001-impact-inventory']`、
  `OK case 001-impact-inventory expectations all parse — unknown=[] parsed=9`、
  `OK case 001-impact-inventory declares at least MIN_EXPECTATIONS expectations — measured=9 floor=5`、
  `OK case 001-impact-inventory must: 逐字读数 — bullets=2 floor=1`、
  `OK case 001-impact-inventory shape: 生产者 & 消费者 & 同级入口 — bullets=1 floor=1 tokens=3`、
  `OK case 001-impact-inventory forbid: 待填 — bullets=0 expected=0`、
  `OK case 001-impact-inventory pointers-alive — citations=2 floor=2 problems=[]`、
  `OK case 001-impact-inventory paths-exist — cited=5 floor=3 dead=[]`、
  `OK case 001-impact-inventory pins every product bullet — unpinned=[] bullets=4`、
  `OK every expectation kind is exercised by some case — missing=[] seen=['forbid', 'must', 'paths-exist', 'pointers-alive', 'shape']`、
  `NOTE case and expectation inventory is printed, not judged — EVALS cases=1 expectations=9`。
- Regression evidence: `python -X utf8 .codex/scripts/dev/checkers_selftest.py` →
  `SELFTEST cases=126 ok=126 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS` / `EXITCODE=0`，
  9 条基线全 `BASELINE OK rc=0`，其余 112 例（intent/roadmap/template/replay/spec/review/context/research/simplify）
  一字未动（块 02）；`--tier core` 整跑全绿、`validate_tasks` `errors=0`（块 03）；
  本片造成的行号位移由块 04 的 55 条复算全部校正。

### REFACTOR — optional
- Cleanup: ① **`CITATION_RE` 的字符类从 `[^\s:]*?` 收紧**：全库扫描第一次跑把两条相邻引用读成一条
  （反引号与中文括号被当成路径字符），于是 `paths-exist` 判出一个不存在的「死路径」。这不是判据松，
  是解析器假阳性——修法是排除 `` ` ``、`（）`、`，`、`、`、`；`，修完全库扫描 `problems=0`（条数不写死，见块 05）。
  ② `paths-exist` 判定最初把快照自己的 `路径:行` 引用当死路径报红：共享的 `citable_paths()` 会整段收下反引号里的
  `run_all_guards.py:89-90`。修法是在 `run_evals.py` 本地加 `CITED_LINE_RE` 去行号后缀，
  **不改** `citable_paths()`——那函数被 B1/B4/B5 三支守门共用，为一个消费方的形状去动它会把另三支的读数牵动。
  ③ 删掉一个死常量 `EVALS_DIR`（写了没读）与一条过期注释（「实测 7 条期望」，实际 9 条）——
  注释里的数字就是另一种写死计数，块 00 的 `parsed=9` 才是权威。
  ④ 尺寸驱动 `b028-sizing.py` 对新增文件报 `added=0`：未跟踪文件在 `git diff HEAD` 里一行都不出现。
  补 `git status --porcelain` 的 `??` 回退（整份文件计为新增），末态 `total=155`。
  ⑤ 本片把 5 张卡里 26 处 `:行` 区间改回现值：片 026 卡 7 处、片 024 卡 6 处、片 027 卡 5 处、片 025 卡 4 处
  （另把那处 `…技术架构方案_V1.0.md` 的省略号写成全名）、片 021 卡 4 处
  （`git diff HEAD --stat -- .codex/tasks` 末态 20 insertions / 17 deletions，逐条旧值→新值见块 04）。
  这些不是本片造成的——是片 025/026/027 逐次加行把 021/024/025/026 的引用顶下去而没人重算。
  指针驱动第一次跑的 `stale=9` 全部是**驱动作者自己没实测就写下的猜测区间**：卡片侧的漂移先逐行复算修完，
  再把修好的区间作为断言交给驱动，末态 `stale=0`。
  ⑥ 快照被刷新过一次之后，一条注入用例的锚点跟着漂了：`evals:pointer-stale` 原以快照里的
  `.codex/scripts/dev/checkers_selftest.py:57` 为替换目标，而片 026 卡片那处指针在本片被改成 `:67` 后
  快照随之重生成，于是整轮自检报 `CASE 125 HARNESS — anchor not found` / `harness_errors=1` /
  `OVERALL: INCONCLUSIVE`（不是判据红，是仪器找不到注入点）。锚点改成 `:67` 后同一轮 `ok=126 harness_errors=0`。
  这条留在这里而不是悄悄修掉：**基线快照里的字符串也是活引用**，注入用例锚在快照原文上就会跟着快照漂，
  下一次刷新快照时必须连着复算所有 `evals:*` 的锚点。
  ⑥′ 这条预言在下一片就兑现了：片 029 给 `checkers_selftest.py` 顶部插了 3 个常量，把基线元组从 `:67`
  推到 `:71`，片 026 卡片随之改写，快照必须重生成（`artifacts/tmp/b029-refresh-snapshot.py`，
  `changed_lines=[5]`、`zero_diff_with_source=True`），`evals:pointer-stale` 的锚点同步改成 `:71`，
  然后整轮自检重跑（片 029 块 02）。这就是 B2 角色面把「刷新快照 → 整轮重跑」写进合同的理由。
- Regression evidence: 块 02 的 126 例必须含 113–126 这 14 条 `evals:*` 且 9 条基线全 `OK`；
  块 05 覆盖 ①；块 00 的 `paths-exist cited=5 dead=[]` 覆盖 ②；块 00 末态 19 行覆盖 ③；
  块 06 覆盖 ④；块 04 覆盖 ⑤；块 02 里 CASE 125 的两次读数（`HARNESS anchor not found` → `OK`）覆盖 ⑥。

## Implementation notes

- 判定的对象是「产物相对期望是否变差」，不是「产物写得好不好」。所以三条结构判据优先：解析完整性
  （认不出的行不判定任何东西，静默跳过 = 期望被悄悄删）、阈值不得恒真（`>= 0` 看起来是判据，实际什么都判不了）、
  产物每条 bullet 都要被正向期望钉住（唯一能让「删一条期望」暴露出来的结构）。
- `judged_units()` 把 bullet 与其续行合成逻辑判定单元：按物理行判定会造出假 FAIL——一条 bullet 折成三行后
  「生产者 + 消费者 + 同级入口」这种多词形状被拆散，内容与判据都没变而读数变了。
- 期望的载荷是中文子串而不是英文 slug：基线产物本身是中文，用英文词会把「措辞翻译成英文」当成内容变化。
  反向的风险记在 Gaps ③。
- `forbid` 与两种计数形状（`pointers-alive` / `paths-exist`）是**期望存在才判**的形状：
  整形删掉时它们安静消失，所以每条都有跨 case 的下限兜着（`MIN_EXPECTATIONS`）与形状覆盖判据兜着
  （`every expectation kind is exercised`），后者由 `evals:shape-kind-dropped` 证明有牙。
- 快照 vs 活引用：`product.md` 是片 026 小节的逐字快照（含其 `文件:行` 引用），但 `pointers-alive` /
  `paths-exist` 两条**每次运行都对当前工作树复算**那些引用。这样快照不会烂成「一份记录过去位置的记忆」——
  源文件被移动或删掉时，快照即使一字未改也会转红（`evals:pointer-stale` 就是这个形状的用例）。
- 转录由 `artifacts/tmp/b028-mkproduct.py` 完成而不是手抄：手抄一份基线等于造一条无法复算的产物；
  驱动里显式列出两处替换（去掉 `Follow [regression…]` 那行、把「平台分支 = 不适用」保留为逐字），
  替换必须能被读出来。
- core 档注册只加两行（`:95-96`），不新造档位：`run_evals` 与被检产物同在 `.codex/`，precommit 档的语义
  是「提交前必跑的贵检查」，这条是毫秒级文本判定，属 core（与片 027 同一结论）。

## Verification report

- Verdict: PASS（运行器末态 18+1 条 + 一次同命令 RED/GREEN（两状态）+ 126 例注入含 9 基线 + core 档整跑 +
  55 条指针复算 + 全库活引用扫描；见块 00–06 与 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-evals-inventory-and-runner-028.txt`
  块 00（`run_evals` 19 行末态逐字）、块 01（RED/GREEN 四场景与 sha256-12 字节还原）、
  块 02（126 例整轮 + 9 条基线 + 本片 14 例 `evals:*` 与两例 `roadmap:*` 逐字，含中途那轮
  `harness_errors=1` / `OVERALL: INCONCLUSIVE` 的原始读数与快照刷新前的对照）、块 03（`--list` / `--tier core` / `validate_tasks` / `check_roadmap_and_routing`）、
  块 04（55 条行号指针复算与本片校正的 26 处历史漂移）、块 05（全库 `文件:行` 扫描，逐条 `problems=0` 读数与条数）、
  块 06（本片尺寸读数与没覆盖之物）。
  环境：Windows 10.0.26200 / x64 / Python（`sys.executable`，一律 `-X utf8`）/ Git Bash。
  捕获文件与驱动留在 gitignore 的 `artifacts/tmp/`：`run040-selftest.txt`（快照刷新前的整轮）、
  `run044-selftest.txt`（注入锚点漂移那轮）、`run045-selftest.txt`（末态整轮）、`run041-redgreen.txt`、
  `run042-list.txt` 与 `run046-list.txt`、`run043-core.txt`（并发导致的 INCONCLUSIVE，见 Gaps ④）、
  `run046-core.txt` 与 `run047-core.txt`（无并发末态，两次读数相同）、`run046-evals.txt`、`run046-roadmap.txt`、
  `run046-pointers.txt`、`run046-citations.txt` 与 `run047-citations.txt`（卡片最后一次改字后复算，仍 `problems=0`）、
  `run046-sizing.txt`；驱动 `b028-mkproduct.py` / `b028-redgreen.py` /
  `b028-sizing.py` / `b028-pointers.py` / `b028-citations.py` / `gen-028-evidence.py`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片没有删除任何既有判据、没有放宽任何
  断言、没有往任何白名单加名字。被收紧的有两处：`README_ENTRIES` 从「只查漏报」变成「同时查自己被抽干」
  （新增 `MIN_README_ENTRIES = 4`），`checkers_selftest.py` 的 `roadmap:readme-entry-removed` 标签随之从
  「four new entries」改为中性措辞（旧标签把条目数写进了判定标签，那是会漂的字符串）。
  唯一被改写的既有实现是 `CITATION_RE` 的字符类——它在片 028 之前不存在（本片新增），收紧方向是减少假阳性，
  方向不改变判定语义（`path_line_citations()` 仍然只认带仓库相对前缀的路径）。
- Gaps: ① 只有 1 个 case：`MIN_CASES = 1` 现在是「存在性下限」而不是「多基线覆盖」，加第二个 case 时这条
  下限不会自动变严（要改的是下限本身，而它不能由 case 数推导——D1 的同一族成因）。
  ② 快照不会自动刷新：片 026 的 `### Behavior impact` 若被改写，`product.md` 仍是旧文，运行器仍然绿。
  这条缺口由片 029 的角色职责（「刷新过期快照」）与其成对守门关，本片明确不重叠。
  ③ `must`/`shape` 的载荷是中文子串，对英文同义改写不设防；产物若改用英文书写，四条正向期望要一起重做。
  ④ `--tier core` 第一次整跑（`run043-core.txt`）报 `INCONCLUSIVE … pass=28 fail=0 fault=0 / EXITCODE=3`，
  病因是**我自己**并发跑了 `b028-redgreen.py`（它把 `.codex/evals/cases` 临时改名），自检复制 `.codex` 时
  撞上被搬走一半的树。聚合器把它报成 INCONCLUSIVE 而不是假 PASS，这一半是好的；末态 `run046-core.txt` 在无并发
  下全绿（`pass=29 fail=0 inconclusive=0 fault=0`）。这条不关 D11（D11 的判据是「复发时能否一眼看到失败用例名」，仍未修）。
  ⑤ 无独立 verifier 轮次（用户要求一站到底；本片属 harness 仪器面，18 条判据有 14 例注入覆盖）。
- Risks: `run_evals.py` 与被检快照同居 `.codex/`，而 `.codex/` 里每一片都会移动行号——所以本片之后
  任何加行都必须重跑块 04 的指针驱动，否则 `product.md` 里那 2 条 `文件:行` 会先响（这是想要的），
  但卡片正文里的区间不会自动响（那要靠人重跑）。

## Handoff notes

- Completed: B2 的评测资产面（评测目录、期望语法权威正文、第一个 case、运行器 18 条判据、core 档注册、
  第 9 条基线、14 例注入）、`文件:行` 区间解析的单份化（上收进 `harness_check_lib.py`）、
  `README_ENTRIES` 的自限下限、片 021/024/025/026/027 五张卡共 26 处历史行号漂移校正、
  ROADMAP B2 的 `范围`/`验收方式`/`交付任务` 回写。
- In progress: 无（B2 的角色面在片 029，那片开始前 B2 不算交付完成——已在 ROADMAP 的 `交付任务` 里写明）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 片 029 = `eval-keeper` 角色两份正文 + `check_eval_keeper_agent.py`（判据面对齐 `verifier.md` 小节集、
  唯一可写落点 + READONLY、跑法那行必须指向真实脚本、`README_ENTRIES` 已登记的评测目录、无占位符、
  toml 与 md 逐字相同），第 10 条基线 + `keeper:*` 注入 + core 档注册。随后 B6（metrics-reporter）、
  B0（`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 的技术规格节，`codebase-researcher` 第一次真实使用）。
- Watch-outs: ① **不要把 `--tier core` 与任何会动 `.codex/` 的驱动并发跑**（本片 Gaps ④ 就是这条）：
  自检会把整个 `.codex` 复制到临时树，中途改名会造出 INCONCLUSIVE 而不是真红。
  ② `MIN_EXPECTATIONS = 5` 而实测 9，且这条下限**不**高于「任一单独形状被整体删掉后的条数」——三条 `shape`
  被删光后仍剩 6 条，此时接住失效的是形状覆盖判据（`evals:shape-kind-dropped`，CASE 122 有牙），
  条数下限接住的是「整片期望被清空 / 删到 5 以下」（CASE 119）。加第二条 case 时这两条判据各自还承不承重
  要重算，不能沿用（片 027 的下限教训同一课：让一条判据假装覆盖另一条的形状，等于两条都没覆盖）。
  ③ 再加一条 core 档守门会让 `--list` 的 `entries/core` 继续漂（现值 `entries=35 core=29`），
  卡片里不要写死，写命令让人现场取（片 023 定的规矩）。
  ④ 本片之后，`.codex/evals/cases/*/product.md` 里的 `文件:行` 是**活引用**：任何给 `harness_check_lib.py`、
  `run_all_guards.py`、`checkers_selftest.py` 加行的改动都可能让它转红，那是运行器在履行职责，
  不是运行器坏了——修法是刷新快照（并记录为什么刷新），不是把期望里的阈值调低。
  ⑤ 刷新快照之后必须整轮重跑 `checkers_selftest.py`：本片 ⑥ 那条 `HARNESS anchor not found` 就是快照与注入锚点
  脱钩的形状，它只在整轮里暴露，单跑 `run_evals.py` 仍然是绿的。
