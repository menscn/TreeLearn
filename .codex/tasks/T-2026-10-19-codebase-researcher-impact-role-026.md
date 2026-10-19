# Codex Harness Task: 把「动手前摸清影响面」变成一个独立角色（ROADMAP B4）

id: T-2026-10-19-026
title: codebase-researcher 角色、影响面清单落点与成对守门
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 131
shared_code_touched: false
depends_on: [T-2026-10-19-code-reviewer-role-and-review-template-024.md, T-2026-10-19-project-brief-dir-state-guard-025.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 要求「完成结论必须匹配证据覆盖」，并把规则、任务、文档与流程资产归 `harness` lane。
  B4 是这条要求在「动手之前」环节的落点：影响面必须由命令查出来，不能由实现者的记忆给出。
- Problem/background: 仓库里此前有三个会读代码的角色——实现者自己、`verifier`（判任务验收）、
  `code-reviewer`（判代码好坏，片 024 交付）。没有一个角色负责回答「这次改动牵动谁」：生产者是谁、
  消费者是谁、有没有同级入口、平台分支走哪条。这几问在本仓库的真实事故里反复出现（片 016 的分支导航、
  片 019 的夹具字节同源），而任务卡的 `### Behavior impact` 四个字段一直在被填，填的内容没有格式约束，
  也没有一条判据要求它带命令与行号。
- Users/scenarios: 使用者是派工前的主会话与实现者本人。场景有两个：① 一片任务确认后、动手前，让这个角色
  把四类面查一遍并写进同一张卡；② 用户点名「先摸清影响面」「这段代码谁在用」时，有明确落点与格式。
- Expected outcome: 一个只读角色（`.codex/agents/codebase-researcher.md` + 同名 `.toml`）、一支 15 条判据的
  成对守门（`.codex/scripts/check_codebase_researcher_agent.py`）、19 条 `research:*` 注入用例、该守门升为
  `checkers_selftest.py` 的第 7 条基线并登记 core 档，以及 `.codex/HARNESS.md` 第 3 步的一行接线。
- Core flow and reversibility: 新增角色两份、守门一份、用例与注册表各一处、流程入口加一句。删除
  `.codex/agents/codebase-researcher.md`/`.toml`、`.codex/scripts/check_codebase_researcher_agent.py`、
  `checkers_selftest.py` 的 19 例与第 7 条基线、`run_all_guards.py` 的注册两行、还原 `HARNESS.md` 第 3 步一句，
  即回到本片之前。上收进 `harness_check_lib.py` 的 `agent_section_names()` 是本片唯一动到既有代码的地方，
  回退方式是把它搬回 `check_code_reviewer_agent.py`（行为等价，见块 05）。
- Inputs/outputs: 输入 = 角色正文、`check_code_reviewer_agent.py` 现文（派生「小节集」的既有实现）、
  `.codex/tasks/_TEMPLATE.md` 的 `### Behavior impact` 四个字段名、`.codex/agents/verifier.md` 现文的小节集、
  `.codex/rules/regression.md` 现文（抄检正控样本的来源）；输出 = 15 行 `OK/FAIL` 判据 +
  `<name>: N check(s) failed` + `EXITCODE=0/1`。
- State and exception handling: 六处 fail-closed 早退：七份输入缺任一即停；verifier 小节集低于裸下限时停
  （等于比对对象消失）；模板字段任一侧低于下限时停；角色引用路径低于下限时停（无引用可判）；
  `.toml` 解析失败单独一条 FAIL，不与「正文不相同」混为一条——那是两个病因。
- Constraints and trade-offs: 只用标准库（`tomllib`）；不引入「从 .md 生成 .toml」的构建步骤，代价与片 024
  相同——同一份角色正文有两个人类可读拷贝，收益是这条重复被一条逐字判据钉住。产物**不落新文件**，
  只落进被分配任务卡的那一节，代价是这个角色的输出没有独立归档目录，收益是不给「影响面清单」造第二份权威。
- AI recommendation and rationale: 这个角色的价值全在「每条结论能不能被复算」，所以判据不查措辞而查形状：
  「命令 + `文件:行`」拆成两侧各一条裸计数下限（只查一侧时另一侧删空仍然报绿）；四个字段名从模板现场派生
  （模板改名而角色没跟就是死指针）；被引路径必须在场（引用死掉比引用少更糟）。
- User choice and custom additions: 用户要求一站到底、按片提交本地仓库、不中途请示；未对本片提出额外定制。
- AI-autonomous decisions: ① 角色正文写成 `.md`（权威）+ `.toml`（运行时包装）两份，沿用仓库既有约定与片 024
  的判据形状；② 产物落点选任务卡的 `### Behavior impact` 一节而不是新建 `Docs/research/**`——那四个字段
  本来就要求同样四类内容，另立目录会造出第二份权威；③ 把 `agent_section_names()` 上收进
  `harness_check_lib.py`，让 B1 与 B4 两支守门共用一份小节集推导；④ 抄检正控样本的形状从
  `(档位, 长度, 档位)` 扩成 `(档位, 来源, 长度, 档位)`，因为取样来源此前被写死成 pm-discovery，
  而本片新增的抄检判据要拿 `regression.md` 现文取样；⑤ 接线落在 `HARNESS.md` 第 3 步（派工前）而不是
  新插一步：新插一步会把 code-reviewer 从第 6 步顶成第 7 步，让片 024 里「第 6 步」那句引用变成活指针。
- User-confirmation decisions: 无新增需拍板项（不改产品口径，只新增流程角色与仪器）。
- Acceptance evidence: 块 01（守门末态逐字）、块 02（同命令 RED/GREEN + sha）、块 03（86 例注入与 7 条基线）、
  块 04（`--list` 与 `--tier core` 读数 + `validate_tasks` + `check_roadmap_and_routing`）、块 05（B1 守门在上收共用派生后的行为等价读数）、
  块 06（24 条 `文件:行` 指针的复算，含本片造成的第三轮漂移）、块 07（本片没覆盖之物）。
- Facts: 15 条判据、19 例注入、`ROSTER entries=33 core=27 precommit=6`、`SELFTEST cases=86 ok=86`、
  `POINTERS checked=24 stale=0` 均为一次真实执行的打印，逐字在 `artifacts/evidence/` 对应文件里。
- Assumptions: `verifier.md` 的小节集继续作为角色形状的对齐目标；它若改名，本角色的判据会先响。
- Unknowns: `.toml` 是否真被 Codex 运行时加载没有运行时证据（与片 024 同一条未知，未新增）。
- Risks: 角色与模板字段名的比对是「剥掉括号注脚后的子串包含」，模板若把一个字段改名而保留另一半文字，
  判定会响；这是想要的行为，但会让「顺手改模板措辞」变成一次跨文件改动。
- Deferred: 不给 `code-reviewer` / `verifier` 补「读同一份影响面清单」的判据；不在 `.codex/rules/communication.md`
  的阶段→角色表里登记本角色（那张表当前不列 B1 角色，属片 024 已记的同一条欠账）。
- Non-goals: 不实现「由脚本代查影响面」的仪器（那是 Explore 类能力，不是流程角色）；不动 `Docs/spec/**` 的
  历史口径；不把 B5/B6 的角色一起建起来。
- Skipped non-critical questions and reason: 未问用户「产物要不要单独归档到 `Docs/research/`」——落进任务卡
  与另立目录相比，前者不新增权威源，且片 001 的「同一约束只保留一份权威正文」直接支持它。

## Goal

`.codex/agents/codebase-researcher.md` 与同名 `.toml` 成对在场且正文逐字相同，
且 `python -X utf8 .codex/scripts/check_codebase_researcher_agent.py` 能在角色缺命令、缺行号、
缺「未找到」纪律、缺实测/推测区分、或引用死掉的路径时转红，在角色末态时转绿。

## Scope

### In scope
- `.codex/agents/codebase-researcher.md` + `.codex/agents/codebase-researcher.toml`（运行时包装）。
- `.codex/scripts/check_codebase_researcher_agent.py`：15 条判据、6 处 fail-closed 早退、7 条裸下限/阈值常量。
- `.codex/scripts/harness_check_lib.py`：新增 `agent_section_names()`（自 `check_code_reviewer_agent.py` 上收）。
- `.codex/scripts/check_code_reviewer_agent.py`：改用共享派生，删掉本文件的 `verifier_section_names()`
  与一处内联的 `## ` 标题重算。
- `.codex/scripts/dev/checkers_selftest.py`：19 例 `research:*`、`CHECK_RESEARCH` 升为第 7 条基线、
  抄检样本形状加入取样来源、D4 形状守卫的前缀从单条改为两条。
- `.codex/scripts/run_all_guards.py`：core 档注册两行。`.codex/HARNESS.md`：第 3 步补一句。
- `.codex/ROADMAP.md`：B4 条目的 `范围`/`验收方式` 改写 + `交付任务`/`实现要点` 回写；
  本片造成的卡片行号指针校正（片 024、片 025 各一处区间）。

### Out of scope
- B5（code-simplifier）与 B2/B6 的角色资产。
- 角色的真实使用（第一次清查落在 B0 的技术规格补全那片）。
- `check_spec_author_agent.py` 里自建的 `repo_path`/`read_text`/`section_titles`/`front_matter`
  四份同形实现——本片只收 `agent_section_names` 这一条被两次真实使用逼出来的，其余留作独立一片。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图 = 派工前的影响面清查有格式、有判据、有落点。
  保留 = B1 守门 14 条判据的逐字读数（块 05 与块 01 对照片 024 证据）、`check_context_pack.py` 全部判据、
  `run_all_guards.py` 其余 32 条注册项、`HARNESS.md` 第 1/2/4/5/6 步原文（接线只做在一句之内）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 = 角色正文与守门脚本；
  消费者 = `.codex/scripts/run_all_guards.py:89-91`（core 档）、`.codex/scripts/dev/checkers_selftest.py:71`（第 7 条基线）、
  `.codex/HARNESS.md` 第 3 步。同级入口 = `check_code_reviewer_agent.py`，它与本片共用
  `harness_check_lib.agent_section_names()`（同一推导两份的那一处已合并）。
  平台分支 = 不适用：无 Android/前端生产代码、无 API、无 SQL；实测与推测在角色正文里的区分是**输出格式要求**，
  不是运行时分支。
- Native bridge or independent-client compatibility/update requirements: 不适用（无桥接字段被触碰；
  `frontend/` 与 `android/` 的代码一行未改，判定形状只作用于 `.codex/` 内的流程资产）。
- Behavior-neutral non-applicability reason, if applicable: 本片的可反驳理由是两条命令：
  `git diff --stat HEAD` 里 `android/**`、`frontend/**` 零命中；以及块 05 的 B1 守门逐字读数与片 024 相同。

## Documentation impact record

- Changed runtime facts: 未改产品运行事实；新增一个流程角色、一条 core 档守门和一句派工前接线。
- Authoritative code/config/migration evidence: `.codex/agents/codebase-researcher.md:8-11`（identity）、
  `:22-34`（contract：命令 + `文件:行`、「未找到」、实测/推测、单一可写落点）、
  `.codex/scripts/check_codebase_researcher_agent.py:47-66`（7 条裸下限/阈值常量）、
  `:84-179`（15 条判定与 6 处早退；027 把 `cited_paths()` 上收成共享的 `citable_paths()` 后本文件又从 197 行短到 182 行）、
  `.codex/scripts/harness_check_lib.py` 的 `agent_section_names()`（新函数，紧接 `section_titles` 之后）、
  `.codex/scripts/dev/checkers_selftest.py:86-101`（含取样来源的样本形状）、`:387-449`（19 例 `research:*`）、
  `:682-690`（顶层目录名镜像）、`:755-756`（`delete_dir` 分派），
  `.codex/scripts/run_all_guards.py:89-91`（core 档新行；整表 `:81-168`）。
  本片写下的这些行号在片 027、片 028、片 029 之后各重测过一次：027 与 028 都往 `checkers_selftest.py` 与 `run_all_guards.py` 里加了内容，
  并把 `check_codebase_researcher_agent.py` 缩短 15 行（B4 自带的 `cited_paths()` 上收成共享的 `citable_paths()`），
  原引 `:99-196` / `:353-412` / `:440-448` / `:513-514` / `:89-90` / `:81-161` 在被引用当时是对的，之后成为活指针。
  条数与用例数以片 027 卡片与其现场读数为准，本卡不复述。
- Documentation surfaces checked: `.codex/ROADMAP.md`（B4 条目与 B0 的「依赖 B4」两处）、
  `.codex/HARNESS.md`（六步流程）、`.codex/README.md`（权威边界一句，未新增落点故未改）、
  `.codex/tasks/_TEMPLATE.md`（四个字段名与 `### Behavior impact` 标题）、`.codex/agents/verifier.md`、
  `.codex/agents/code-reviewer.md`（结论不互相冒充的措辞对齐）、`.codex/rules/regression.md`、
  `.codex/rules/documentation.md`、`AGENTS.md`（路由表不逐脚本登记，故不需新行）。
- Documentation files updated: `.codex/ROADMAP.md`（B4 四行）、`.codex/HARNESS.md`（第 3 步一句）、
  `.codex/tasks/T-2026-10-19-code-reviewer-role-and-review-template-024.md`（本片按当时实测位移了它的
  `checkers_selftest.py` 常量/基线区间与 `run_dir_case`、`delete_dir` 分派区间，并把「三处早退」校正为实测的 6 处；
  片 027 又把这些区间整体下推一次，现值以 `artifacts/tmp/b027-pointers.py` 的读数为准）、
  `.codex/tasks/T-2026-10-19-project-brief-dir-state-guard-025.md`（同样是本片位移、027 再位移，
  涉及 3 例 `context:*`、顶层目录名镜像、tag 派生与 `CHECK_CONTEXT` 常量区间，
  以及卡片正文的块号引用与 Gaps ⑤）。
- Producer/consumer/sibling/compatibility paths checked: 反查「谁引用本角色的落点」——
  `grep -rnI "Behavior impact" .codex Docs` 命中 `_TEMPLATE.md`、`regression.md`、各任务卡与本片新文件；
  `grep -rnI "codebase-researcher" .codex Docs` 实测 32 行 / 11 个文件：角色两份、守门、自检、注册表、
  `harness_check_lib.py`（新函数 docstring）、`HARNESS.md`、`ROADMAP.md`、片 024/025/026 三张卡，
  另有两个**本片之前就在场**的消费方：`check_roadmap_and_routing.py:36` 把 `codebase-researcher` 列进
  `PHASE_B_KEYWORDS`（`:170` 要求 Phase B 正文含每个关键词——所以 ROADMAP 的 B4 条目一旦删掉这个角色名，
  那支守门立刻转红），以及 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:48` 的
  `DEFERRED:待 B4（codebase-researcher）。`——B4 已交付，但那句 DEFERRED 要由 B0 那片改写，本片不动它。
  24 条 `文件:行` 指针逐条复算：`python -X utf8 artifacts/tmp/b026-pointers.py` → `POINTERS checked=24 stale=0`。
- Superseded phrases/examples reverse-searched: 被本片改写的旧句是 B4 的 `验收方式` 一行（原文只要求
  「命令 + 文件:行」与「未找到」两件事）与 `范围` 一行（原文只列两个路径）。反查
  `grep -rnI "check_codebase_researcher_agent" .codex Docs` 在本片之前命中 `.codex/ROADMAP.md:83` 与 `:86`
  两行（`git -c core.pager=cat grep -n "check_codebase_researcher_agent" HEAD -- .codex` → `HEAD:.codex/ROADMAP.md` 2 处），
  两处都被本片按新判据面改写，别处零命中；所以失配面只有 ROADMAP 的 B4 条目自身，已随本片回写闭合。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 的
  `## Technical spec（技术规格）` 仍写 `DEFERRED:待 B4`；B4 已交付，但那份规格的补全是 B0 那片的工作，
  本片不动它（改了就要按规格模板重排四类内容，属另一片）。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/check_codebase_researcher_agent.py` 退出码 0，15 条判据全部打出 `OK`（块 01）。
- [x] 同一条命令在角色正文与打包不在场时退出码 1，FAIL 标签是 `the seven inputs exist`
      且 `missing` 逐字列出两个路径（块 02，字节级还原后 sha256-12 前后相同）。
- [x] 角色小节名覆盖 `verifier.md` 现文（5/5），且逐条点名 `_TEMPLATE.md` 的四个 `Behavior impact` 字段（块 01）。
- [x] 「命令 + `文件:行`」两侧各被一条注入用例证伪过：`research:location-dropped`、`research:command-dropped`
      都打出同一条 FAIL 标签且各自主体归零（块 03 CASE 74 / 75）。
- [x] `research:absent-claim-dropped` 证明「未找到」纪律承重；`research:provenance-merged` 证明实测/推测之分承重。
- [x] `research:impact-field-renamed` 与 `research:impact-fields-drained` 分别证明改名与缩表两个方向；
      `research:dead-path-cited` 与 `research:cites-drained` 分别证明死指针与引用裸下限。
- [x] 抄检正控样本从 `regression.md` 现文派生：`research:rules-restated` 打印出被复制的那一段（块 03 CASE 82），
      样本不在场时报 HARNESS 而不是悄悄少跑一例。
- [x] `.toml` 与 `.md` 逐字相同，且 `research:toml-md-drift` / `research:toml-name-missing` /
      `research:toml-unparsable` 三例各自转红。
- [x] `checkers_selftest.py` → `cases=86 ok=86 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS`，
      7 条基线全 `BASELINE OK rc=0`（含新升的 `check_codebase_researcher_agent`）（块 03）。
- [x] `run_all_guards.py --tier core` 全绿且新条目打印自己的判据行；`--list` 读数 `entries=33 core=27 precommit=6`（块 04）。
- [x] `harness_check_lib.agent_section_names()` 上收后，B1 守门 14 条判据读数与片 024 证据逐字相同（块 05）。
- [x] `validate_tasks.py` `errors=0`（块 04）。
- [x] `check_roadmap_and_routing.py` 转绿且 `OK Phase B covers the seven planned capabilities — missing=[]`：
      本角色名已在那支守门的 `PHASE_B_KEYWORDS`（`:36`）里被要求，删掉 ROADMAP 的 B4 条目会立刻转红（块 04）。
- [x] 卡片里的 24 条 `文件:行` 指针复算 `stale=0`（块 06）。

## TDD execution record

### RED — failing test first
- Test/scenario: 判据写完、角色正文与运行时包装不在场——即「B4 只写了守门，角色还没建」这一状态。
  跑 `check_codebase_researcher_agent.py`，看它是否把「被检对象缺席」报成红而不是无话可说的绿。
- Command: `python -X utf8 .codex/scripts/check_codebase_researcher_agent.py`
- Exit code: 1
- Observed failure: `FAIL the seven inputs exist — missing=['.codex/agents/codebase-researcher.md', '.codex/agents/codebase-researcher.toml']`、
  `check_codebase_researcher_agent: 1 check(s) failed`。
- Failure reason validated: 红来自被检产物缺席而不是命令没跑起来：把两份产物按原始字节摆回后同一条命令立刻
  `0 check(s) failed` / `rc=0`，还原前后 `sha256-12=5329c1268c8d,e0dd3e753c1e` 相同（块 02，驱动
  `artifacts/tmp/b026-redgreen.py`，gitignore 内）。不用 `git checkout --` 撤注入：本工作树
  `core.autocrlf=true`，checkout 会改写行尾而使「字节相同」无从证明（片 021/025 记的同一课）。
  开发中途另有两次真实红灯先于这次正式 RED：写角色正文时先跑一次守门，`agent names every impact field
  from the task template` 转红（`missing=['Behavior-neutral non-applicability reason, if applicable']`），
  因为它按模板字段名派生；补上该字段后才绿。这两次都是判据认出了真实缺陷，不是为绿而改判据。

### GREEN — minimum implementation
- Implementation: 新增角色 `.md`（96 行）+ 同名 `.toml`（逐字包装）、`check_codebase_researcher_agent.py`
  （197 行文件 / 129 行代码 / 15 条判据 / 6 处早退 / 7 条裸下限）；把 `agent_section_names()` 上收进
  `harness_check_lib.py` 并让 `check_code_reviewer_agent.py` 改用它；`checkers_selftest.py` 加 19 例
  `research:*`、第 7 条基线、抄检样本形状加入取样来源、D4 形状守卫前缀扩为两条；
  `run_all_guards.py` core 档注册两行；`HARNESS.md` 第 3 步补一句派工前清查。
- Command: `python -X utf8 .codex/scripts/check_codebase_researcher_agent.py`
- Exit code: 0
- Passing evidence: `check_codebase_researcher_agent: 0 check(s) failed` / `EXITCODE=0`，15 行 `OK` 逐字见块 01，
  其中关键几条为 `OK agent names every impact field from the task template — template_fields=4 missing=[]`、
  `OK agent requires each conclusion as command + file:line — location_hits=2 floor=2 command_hits=16 floor=3`、
  `OK agent requires an explicit 未找到 instead of silence — hits=8 floor=2`、
  `OK every path cited by the role exists on disk — dead=[] cited=6`、
  `OK role text does not restate the rules it cites — measured_hits=0 sample=[]`、
  `OK agent .toml body equals the agent markdown verbatim — toml_chars=3798 md_chars=3798 equal=True`。
- Regression evidence: `python -X utf8 .codex/scripts/dev/checkers_selftest.py` →
  `SELFTEST cases=86 ok=86 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS` / `EXITCODE=0`，
  7 条基线全 `BASELINE OK rc=0`，其余 67 例（intent/roadmap/template/replay/spec/review/context）一字未动（块 03）；
  `--tier core` 整跑 27 条全绿、`validate_tasks` errors=0（块 04）；
  B1 守门在上收共用推导后 `0 check(s) failed` 且 14 行判据与片 024 证据逐字相同（块 05）。

### REFACTOR — optional
- Cleanup: ① `agent_section_names()` 上收进 `harness_check_lib.py`：片 024 的
  `check_code_reviewer_agent.py` 里已有一份同样的推导（`^<([a-z]+)>$` + `## ` 标题），本片再加一份就是
  「沿用 verifier 骨架」这条判据本身成为第二份实现；`check_code_reviewer_agent.py` 里还有一处内联重算
  agent 的 `## ` 标题，一并合掉。合并前后 B1 的 14 行读数逐字相同（块 05）。
  ② 抄检正控样本从「取样来源写死成 pm-discovery」改成随用例声明 `(档位, 来源, 长度, 档位)`：
  本片新加的 `research:rules-restated` 要从 `regression.md` 取样，写死会让它拿错文件派生正控——
  那正是 D4 记过的 fail-open 形状。D4 的形状守卫随之从前缀单条改为 `(intent:copied-methodology, research:rules-restated)`，
  仍然只查形状（用例必须走 `append_sample`），不查样本本身。
  ③ 本片的指针校正是被驱动逼出来的：`artifacts/tmp/b026-pointers.py` 第一次跑就报 `stale=2`，
  两处是我自己刚写进卡片的区间（`<identity>` 与 `READONLY` 的行号猜错了）。改成先实测再落笔后 `stale=0`。
  ④ 收口时又抓到本卡两处「反查结论」写得不实：一处把 `codebase-researcher` 的消费方列漏了两个本片之前就在场的
  （`check_roadmap_and_routing.py:36` 的 `PHASE_B_KEYWORDS`、`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:48`
  的 DEFERRED 行），一处声称 `check_codebase_researcher_agent` 在 HEAD 侧零命中（实测 `HEAD:.codex/ROADMAP.md`
  两处）。两条都按现场读数改写，并把这三个 `文件:行` 一并入驱动复算，末态 `POINTERS checked=24 stale=0`。
- Regression evidence: 块 03 的 86 例必须包含 68–86 这 19 条 `research:*` 且 7 条基线全 `OK`；
  块 05 覆盖 ①；块 03 的 CASE 82 覆盖 ②；块 06 覆盖 ③④。

## Implementation notes

- 判据的形状：不查「有没有写影响面」，查「每条影响面能不能复算」。所以「命令 + `文件:行`」是两条计数下限，
  「未找到」也是一条，实测/推测/证实三者齐备又是一条——它们各自都能被一条注入用例抽干。
- 四个字段名从 `_TEMPLATE.md` 现场派生并剥掉 `(code evidence)` 这类注脚后缀：注脚是模板的说明，不是字段名的一部分；
  不剥就要在角色正文里照抄注脚，那是把说明当契约。
- 产物不落新文件：角色唯一可写落点是被分配任务卡的 `### Behavior impact` 四个字段，判据要求所有含「只写」的行
  都必须点名 `Behavior impact`（`research:landing-not-a-section` 证伪：把那一行改成「里的其它小节」即转红）。
- 接线为什么不新开一步：`HARNESS.md` 的步骤号被片 024 的卡片正文引用着（「第 6 步」），插一步就会让那句
  变成活指针——本项目的 D9 缺陷类就是这个。所以把派工前清查写进第 3 步（本来就是「派工前」）而不是新增一步。
- `MIN_CITED_PATHS = 5` 而实测 6：下限故意不等于实测值，留一格给「以后删掉一条引用」的反应空间，
  同时 `research:cites-drained` 删到 4 条即响。

## Verification report

- Verdict: PASS（守门末态 + 一次同命令 RED/GREEN + 86 例注入含 7 基线 + core 档整跑 + B1 行为等价复算 +
  24 条指针复算；见块 01–06 与 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-codebase-researcher-impact-role-026.txt`
  块 01（守门 15 条判据末态逐字）、块 02（RED/GREEN 对照与 sha256-12）、块 03（86 例注入 + 7 条基线 +
  `research:*` 19 例逐条）、块 04（`--list` / `--tier core` / `validate_tasks` / `check_roadmap_and_routing`）、
  块 05（B1 守门在上收共用派生后的逐字读数）、块 06（24 条行号指针复算与本片造成的漂移）、
  块 07（本片没覆盖之物）。环境：Windows 10.0.26200 / x64 / Python（`sys.executable`，一律 `-X utf8`）/ Git Bash。
  捕获文件与驱动留在 gitignore 的 `artifacts/tmp/`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片删除的实现只有一处：
  `check_code_reviewer_agent.py` 的 `verifier_section_names()` 与那处内联 `## ` 标题重算，等价行为由
  `harness_check_lib.agent_section_names()` 提供，判据一条未删、未改宽（块 05 的 14 行读数与片 024 证据相同）。
  对 `checkers_selftest.py` 的两处行为改动都朝**更严格**方向：抄检样本必须显式声明来源；形状守卫覆盖第二条抄检用例。
  其余既有判据一字未动。
- Gaps: ① 角色从未真的产出一份 `Behavior impact` 清单——判据只证明「产物成对、形状会响」，
  不证明按这套字段真能查出一片代码的影响面；第一次真实清查要落在 B0 那片。
  ② `citable paths` 只认 `backticked_paths()` 的已知前缀（Docs/.codex/android/artifacts/README），
  所以 `` `frontend/` `` 这类顶层目录引用不在死指针判定内；背景包那一条由 `check_context_pack.py` 管，两处不重叠。
  ③ `MIN_VERIFIER_SECTIONS` 的下限用例（`research:verifier-sections-drained`）证的是角色侧那份；
  把 `verifier.md` 自己删到 3 个小节以下没有独立用例（与片 024 同名下限同形，未再补一份）。
  ④ `.toml` 是否真被 Codex 运行时加载没有运行时证据。
  ⑤ 无独立 verifier 轮次（用户要求一站到底；本片属 harness 仪器面，15 条判据有 19 例注入覆盖）。
- Risks: 共用 `agent_section_names()` 之后，`verifier.md` 的小节改名会同时让 B1 与 B4 两支守门转红——
  这是想要的耦合，但它让「顺手改 verifier 骨架」变成一次跨文件改动，改之前要先看这两支守门的 FAIL 标签。

## Handoff notes

- Completed: B4 全部交付物（角色两份、守门 15 条、19 例注入、第 7 条基线、core 档注册、HARNESS 第 3 步接线）、
  片 024/025 被本片位移的行号指针校正、`agent_section_names()` 的单份化。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: B5（`code-simplifier`）——它的依赖里已经写明「沿用 B1 的只读契约与报告字段边界」，
  现在 B4 也在场，简化角色的「无生产者代码」判据可以直接引用清查形状。
  随后 B0 补 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 的 `## Technical spec（技术规格）`，
  那一片是本角色第一次真实产出 `Behavior impact` 清单的机会。
- Watch-outs: 本片收口时 `--tier core` 出现一次 `cdp_shot_selftest` 间歇转红（随后单跑与整跑各一次均绿），
  已登记为 ROADMAP **D11** 并写明未闭合——Web 侧仪器与本片的判据面无交集，但汇总面红一次就要记账，
  不能靠重跑到绿为止。再加一条 core 档守门会让 `--list` 的 `entries/core` 计数继续漂；卡片里不要写死那两个数字，
  写命令让人现场取（片 023 已把这条定为规矩）。另外 `check_spec_author_agent.py` 还自建着
  `repo_path`/`read_text`/`section_titles`/`front_matter` 四份同形实现，将来碰到它时应一并上收，
  不要在本片顺手改（那会超出本片声明的可写路径）。
