# Codex Harness Task: 给「行为不变的收敛」装上可复算的判据（ROADMAP B5）

id: T-2026-10-19-027
title: code-simplifier 角色、判据面禁令与成对守门
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
depends_on: [T-2026-10-19-codebase-researcher-impact-role-026.md, T-2026-10-19-code-reviewer-role-and-review-template-024.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 要求「完成结论必须匹配证据覆盖」，并把规则、任务、文档与流程资产归 `harness` lane。
  B5 是这条要求在「代码已经写完之后再收一次」环节的落点：收敛必须能被同一条命令复算，不能是一句自评。
- Problem/background: 本仓库的欠账里有一整类是「第二份实现」——D 类记账里 `check_spec_author_agent.py` 自建
  `repo_path`/`read_text`/`section_titles`/`front_matter` 四份同形工具、片 024/025/026 三轮都把同一个推导写了两遍、
  `.codex/tasks/` 与 `Docs/` 里并存着已退役流程的措辞。前面三个角色（`verifier`、`code-reviewer`、
  `codebase-researcher`）全是只读的：没有一个角色被授权去删。而「会删代码」的角色有两个真实失效方向，
  都是本仓库登记过的病——① 「行为不变」退化成自我声明（没有改前改后同一条命令的读数，注释掉的旧实现也算不变）；
  ② 为了让绿灯保住而去动判据自己（D4 的「仪器给自己开后门」、`regression.md` 第 6 条的「不得仅为让测试变绿而削弱断言」）。
- Users/scenarios: 使用者是实现完成后的主会话与实现者本人。场景有两个：① 一片实现完成后，把某个具体重复点交给
  这个角色收敛，产物落回那张卡的 `### REFACTOR — optional`；② ROADMAP/任务卡上记着「兼容分支退役」「第二份实现」
  这类欠账要清时，有明确的触发时机、判据与禁区。
- Expected outcome: 一个会动代码的收敛角色（`.codex/agents/code-simplifier.md` 93 行 + 同名 `.toml` 包装）、
  一支 19 条判据的成对守门（`.codex/scripts/check_code_simplifier_agent.py`）、24 例 `simplify:*` 注入用例、
  该守门升为 `checkers_selftest.py` 的第 8 条基线并登记 core 档、`.codex/HARNESS.md` 第 4 步的一句接线，
  以及本片自己带来的那次收敛（B4 的 `cited_paths()` 上收成 `harness_check_lib.citable_paths()`）。
- Core flow and reversibility: 新增角色两份、守门一份、用例与注册表各一处、流程入口加一句、共享库加一个函数。
  回退方式：删 `.codex/agents/code-simplifier.md`/`.toml`、删 `check_code_simplifier_agent.py`、
  撤 `checkers_selftest.py` 的 24 例与第 8 条基线、撤 `run_all_guards.py:92-94`、还原 `HARNESS.md` 第 4 步那半句，
  即回到本片之前；上收进 `harness_check_lib.py` 的 `citable_paths()` 回退方式是把它搬回
  `check_codebase_researcher_agent.py`（行为等价证据见块 05，判据读数一字未变）。
- Inputs/outputs: 输入 = 角色正文、`.codex/agents/code-reviewer.md` 现文（小节集对齐对象）、
  `.codex/tasks/_TEMPLATE.md` 的 `### REFACTOR — optional` 标题、`.codex/rules/maintainability.md` 与
  `.codex/rules/regression.md` 现文（抄检窗口与正控样本来源）；输出 = 19 行 `OK/FAIL` 判据 +
  `<name>: N check(s) failed` + `EXITCODE=0/1`。
- State and exception handling: 四处 fail-closed 早退：六份输入缺任一即停；对齐对象（`code-reviewer.md`）的小节集
  低于裸下限时停——那是「没有可比对象」而不是「通过」；模板的 REFACTOR 小节标题派生不出来时停；
  角色引用路径低于下限时停（无引用可判，死指针判定会空集通过）。`.toml` 解析失败单独一条 FAIL，
  不与「正文不相同」混为一条——那是两个病因。
- Constraints and trade-offs: 只用标准库（`tomllib`）；不引入「从 .md 生成 .toml」的构建步骤，代价与片 024/026 相同
  ——同一份角色正文两个人类可读拷贝，收益是这条重复被一条逐字判据钉住。角色**不新建归档目录**：收敛记录只落进
  被分配任务卡的 `### REFACTOR — optional` 两条字段，收益是不给「重构记录」造第二份权威，代价是它没有独立可检索面。
- AI recommendation and rationale: 这条角色的价值全在「行为不变的声称能不能被复算」，所以判据不查措辞而查形状：
  改前/改后各一条计数下限（只数一侧时另一侧删空仍报绿）、「同一」一条（两侧不是同一套时各自绿也证不了不变）、
  「全绿」一条；禁令判据数**行形状**（禁止措辞 × 判据面词汇同行）而不是某个名词出现；删除义务要求
  「无生产者/不可达」与「只能删」同句出现，因为只数名词时把义务改成「可以留」而名词全留仍然绿。
- User choice and custom additions: 用户要求一站到底、按片提交本地仓库、不中途请示；未对本片提出额外定制。
- AI-autonomous decisions: ① 角色正文写成 `.md`（权威）+ `.toml`（运行时包装）两份，沿用仓库既有约定与片 024/026
  的判据形状；② 对齐对象选 `code-reviewer.md` 而不是 `verifier.md`——B5 的依赖行就写着「沿用 B1 的只读契约与
  报告字段边界」，且 `code-reviewer.md` 的小节集是 9 个（比 verifier 的 5 个更严），沿用它不会降低要求；
  ③ 落点选任务卡的 `### REFACTOR — optional` 小节而不是新建 `Docs/refactor/**`，标题从模板现场派生；
  ④ 接线落在 `HARNESS.md` 第 4 步（实现保持在分配的 lane 内）而不是新插一步——新插一步会把 code-reviewer 从
  第 6 步顶成第 7 步，让片 024 卡片里「第 6 步」那句引用变成活指针（D9 缺陷类）；⑤ 顺手把 B4 自带的
  `cited_paths()` 上收成共享的 `citable_paths()`：一条专门消灭第二份实现的角色，自己的守门不该留着第二份实现。
- User-confirmation decisions: 无新增需拍板项（不改产品口径，只新增流程角色与仪器）。
- Acceptance evidence: 块 01（守门末态 19 条逐字）、块 02（同命令 RED/GREEN + sha）、块 03（110 例注入与 8 条基线，
  含本片 24 例 `simplify:*` 逐条）、块 04（`--list` 与 `--tier core` 读数 + `validate_tasks` +
  `check_roadmap_and_routing`）、块 05（上收共享派生后 B4/B1 两支守门的行为等价读数）、块 06（49 条 `文件:行`
  指针复算，含本片造成的第四轮漂移）、块 07（本片没覆盖之物，含那条下限不承重的注入事故）。
- Facts: 19 条判据、24 例注入、`ROSTER entries=34 core=28 precommit=6`、`SELFTEST cases=110 ok=110`、
  `POINTERS checked=49 stale=0`、`toml_chars=3183 md_chars=3183 equal=True` 均为一次真实执行的打印，
  逐字在 `artifacts/evidence/` 对应文件里。
- Assumptions: `code-reviewer.md` 的小节集继续作为角色形状的对齐目标；它若改名或缩表，本角色的判据会先响。
- Unknowns: `.toml` 是否真被 Codex 运行时加载没有运行时证据（与片 024/026 同一条未知，未新增）。
- Risks: 角色正文里的「改前/改后」「同一」「全绿」是中文子串计数，把这三组词换成同义英文会让判据响——
  这是想要的行为，但意味着角色正文的措辞改动必须连着看这三条读数。
- Deferred: 不给本角色配一条「真的删掉一处第二份实现」的交付（那是它第一次使用时的产物，本片只造仪器）；
  `check_spec_author_agent.py` 的四份同形自建工具不在本片合并（见 Out of scope）。
- Non-goals: 不实现「由脚本代找重复实现」的仪器（那是语义检索能力，不是流程角色）；不动 `android/**`、
  `frontend/**` 一行生产代码；不把 B2/B6 的角色一起建起来。
- Skipped non-critical questions and reason: 未问用户「对齐对象要不要用 verifier.md」——B5 的依赖行已写明沿 B1，
  且选 `code-reviewer.md` 的对齐要求更严，没有会让边界变松的地方。

## Goal

`.codex/agents/code-simplifier.md` 与同名 `.toml` 成对在场且正文逐字相同，且
`python -X utf8 .codex/scripts/check_code_simplifier_agent.py` 能在角色缺改前或改后任一侧读数、两侧不是同一套、
缺「判据面不许动」禁令、把删除义务改成可以保留、落点小节名与模板不符、或引用死掉的路径时转红，在角色末态时转绿。

## Scope

### In scope
- `.codex/agents/code-simplifier.md`（93 行）+ `.codex/agents/code-simplifier.toml`（运行时包装，正文逐字）。
- `.codex/scripts/check_code_simplifier_agent.py`：216 行文件 / 144 行代码 / 19 条判据 / 4 处 fail-closed 早退 /
  10 条裸下限 + 1 个抄检窗口（`OVERLAP_RUNS = 60`）。
- `.codex/scripts/harness_check_lib.py`：新增 `citable_paths()`（158-171，自 `check_codebase_researcher_agent.py` 上收）。
- `.codex/scripts/check_codebase_researcher_agent.py`：删掉本文件的 `cited_paths()` 改用共享派生，
  文件由 197 行短到 182 行。
- `.codex/scripts/dev/checkers_selftest.py`：24 例 `simplify:*`（`:423-498`）、`CHECK_SIMPLIFY` 升为第 8 条基线
  （`:61-62`）、0 容忍抄检的正控样本来源换成 `maintainability.md`（`:88`）、D4 形状守卫前缀扩为三条（`:672`）。
- `.codex/scripts/run_all_guards.py`：core 档注册三行（`:92-94`）。`.codex/HARNESS.md`：第 4 步补一句（`:10`）。
- `.codex/ROADMAP.md`：B5 条目的 `范围`（补两条本片真实改到的路径）/`验收方式` 改写 + `交付任务`/`实现要点` 回写；
  本片造成的卡片行号指针校正（片 024、片 025、片 026 各若干区间）。

### Out of scope
- B2（eval-keeper）与 B6（metrics-reporter）的角色资产。
- 角色的真实使用（第一次收敛落在哪一处重复实现由后续那片决定；本片只造仪器与判据）。
- `check_spec_author_agent.py` 里自建的 `repo_path`/`read_text`/`section_titles`/`front_matter` 四份同形实现——
  本片只收 `citable_paths` 这一条被两次真实使用逼出来的，其余留作独立一片（它同时还是 168 行的另一支守门）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图 = 「行为不变」从自我声明变成同一条命令两侧读数可复算，
  且判据面不许被这个角色自己动。保留 = B4 守门 15 条判据与 B1 守门 14 条判据的逐字读数（块 05 对照片 026/024 证据，
  `identical=True`）、`run_all_guards.py` 其余 31 条注册项、`HARNESS.md` 第 1/2/3/5/6 步原文（接线只做在一句之内）、
  `checkers_selftest.py` 原有 86 例与 7 条基线一字未改。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 = 角色正文与守门脚本；
  消费者 = `run_all_guards.py:92-94`（core 档）、`checkers_selftest.py:78-80`（第 8 条基线所在元组；片 029 在其上插了
  3 个常量、片 030 又插了 4 个并把元组撑到三行，行号从 `:66-67` 经 `:70-71` 挪到这里，由片 030 复算）、`.codex/HARNESS.md:10`
  （派工/实现阶段的第 4 步）。同级入口 = `check_codebase_researcher_agent.py`，它与本片共用
  `harness_check_lib.citable_paths()`（同一筛选两份的那一处已合并）。
  另有两个**本片之前就在场**的消费方：`check_roadmap_and_routing.py:36` 把 `code-simplifier` 列进
  `PHASE_B_KEYWORDS`（`:170` 要求 Phase B 正文含每个关键词——删掉 ROADMAP 的 B5 条目那支守门立刻转红），
  以及 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:98` 那句 DEFERRED 只提到 B4，与本片无关。
  平台分支 = 不适用：无 Android/前端生产代码、无 API、无 SQL；「Java-only 不得被收敛破坏」写在角色 `<rules>` 里，
  那是**输出格式要求**，不是运行时分支。
- Native bridge or independent-client compatibility/update requirements: 不适用（无桥接字段被触碰；
  `frontend/` 与 `android/` 的代码一行未改，判定形状只作用于 `.codex/` 内的流程资产）。
- Behavior-neutral non-applicability reason, if applicable: 本片的可反驳理由是两条命令：
  `git diff --stat HEAD` 里 `android/**`、`frontend/**` 零命中；以及块 05 的 B4/B1 守门逐字读数与片 026/024 证据相同。

## Documentation impact record

- Changed runtime facts: 未改产品运行事实；新增一个流程角色、一条 core 档守门、一个共享库函数和一句实现阶段接线。
- Authoritative code/config/migration evidence: `.codex/agents/code-simplifier.md:1-93`（角色正文；`<contract>` 22-31
  是两侧读数与禁令的权威措辞）、`.codex/agents/code-simplifier.toml`（`developer_instructions` 与 `.md` 逐字，
  `toml_chars=3183 md_chars=3183 equal=True`）、
  `.codex/scripts/check_code_simplifier_agent.py:32-68`（路径与 10 条裸下限，含本片校正过的 `MIN_ROLE_SECTIONS = 6`）、
  `:70-81`（11 条判定形状的正则）、`:103-212`（19 条判定与 4 处早退）、
  `.codex/scripts/harness_check_lib.py:158-171`（`citable_paths()`）、
  `.codex/scripts/check_codebase_researcher_agent.py:84-179`（改用共享派生后的 `main()`）、
  `.codex/scripts/dev/checkers_selftest.py:55-58` / `:79-80` / `:103` / `:466-541` / `:1028-1030`、
  `.codex/scripts/run_all_guards.py:92-94`、`.codex/HARNESS.md:10`。
  行号是先实测再落笔的：`artifacts/tmp/b027-pointers.py` 把本片与片 024/025/026 的 49 条引用逐条复算，
  末态 `POINTERS checked=49 stale=0`；片 029 交付后由 `b029-pointers.py` 再复算一次并改成本页的读数
  （那一片往同一文件插了 3 个常量、10 条基线之一和 29 例 `keeper:*`，把这里的行号整体下推）；
  片 030 又插了 4 个常量、第 11 条基线和 19 例 `metrics:*`，本页的五处区间由 `b030-pointers.py` 第三次复算后改写。
- Documentation surfaces checked: `.codex/ROADMAP.md`（B5 条目与 B1/B4 的依赖关系两处）、
  `.codex/HARNESS.md`（六步流程）、`.codex/tasks/_TEMPLATE.md`（`### REFACTOR — optional` 标题与两条字段名）、
  `.codex/agents/code-reviewer.md`（小节集与「结论不互相冒充」的措辞对齐）、`.codex/agents/verifier.md`、
  `.codex/rules/maintainability.md`、`.codex/rules/regression.md`、`.codex/README.md`（权威边界不逐脚本登记，未新增落点故未改）、
  `AGENTS.md`（第 62 行 glob 已覆盖 `.codex/agents/*.toml`）。
- Documentation files updated: `.codex/ROADMAP.md`（B5 `范围` 补两条路径 + `验收方式`/`交付任务`/`实现要点`）、
  `.codex/HARNESS.md`（第 4 步一句）、
  `.codex/tasks/T-2026-10-19-code-reviewer-role-and-review-template-024.md` 与
  `T-2026-10-19-project-brief-dir-state-guard-025.md` 与
  `T-2026-10-19-codebase-researcher-impact-role-026.md`（本片把这四张卡引用的区间整体下推，按块 06 现值校正）。
- Producer/consumer/sibling/compatibility paths checked:
  `grep -rnI "code-simplifier" .codex Docs` 末态实测 31 行 / 9 个文件：本卡 12、`checkers_selftest.py` 4、
  `ROADMAP.md` 4、`check_code_simplifier_agent.py` 3、片 026 卡 2、角色 `.toml` 2 与 `.md` 2、
  `check_roadmap_and_routing.py` 1、`HARNESS.md` 1；`Docs/` 侧零命中。
  `grep -rnI "check_code_simplifier_agent" .codex Docs` 实测 22 行 / 5 个文件（本卡 14、ROADMAP 3、
  守门自身 2、注册表 2、自检 1）。本片之前就在场的消费方是 `check_roadmap_and_routing.py:36` 的
  `PHASE_B_KEYWORDS`（`:170` 逐关键词判定），HEAD 侧另两处是 `ROADMAP.md` 的 B5 条目与片 026 卡的两处提及
  （`git -c core.pager=cat grep -c "code-simplifier" HEAD -- .codex Docs` → 3 / 1 / 2）。
  `grep -rnI "REFACTOR" .codex Docs` 实测 53 行：绝大多数是各任务卡自带的 `### REFACTOR — optional` 小节，
  本片没有新增落点目录。规则正文与 `AGENTS.md` 路由表不按脚本名枚举守门（`AGENTS.md:62` 的
  `.codex/agents/*.toml` glob 已覆盖本角色），所以新守门只需进注册表与 ROADMAP，不需新增规则条目
  （与片 024/026 同一结论，本片复算一次）。
- Superseded phrases/examples reverse-searched: 被本片改写的旧句是 B5 的 `范围` 一行（原文漏列
  `harness_check_lib.py` 与 `check_codebase_researcher_agent.py`，而这两条正是本片实际改到的路径）与
  `验收方式` 一行（写死条数的措辞改为「以 `checkers_selftest.py` 现场取」）。HEAD 侧那 6 处 `code-simplifier`
  命中全部指向已存在的对象（ROADMAP 的 B5 条目、`PHASE_B_KEYWORDS`、片 026 卡的指针校正句），
  本片没有把旧写法留在别处；逐字读数见块 06。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:98` 的
  `DEFERRED:待 B4` 仍由 B0 那片改写，本片不动（B4 已交付但那句属规格补全的待办，
  不是角色登记）。`.codex/rules/communication.md` 的阶段→角色表当前不列 B1/B4 角色，本片同样不单独加 B5
  （片 024 已记这条欠账）。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/check_code_simplifier_agent.py` 退出码 0，19 条判据全部打出 `OK`（块 01）。
- [x] 同一条命令在角色正文与打包不在场时退出码 1，FAIL 标签是 `the six inputs exist`
      且 `missing` 逐字列出两个路径（块 02，字节级还原后 `sha256-12 before=0c2e522819d5,4b5f6c5a8b35` 与 after 相同）。
- [x] 角色小节名覆盖 `code-reviewer.md` 现文（9/9，`missing=[]`），对齐对象不是更松的 `verifier.md`（块 01）。
- [x] 「改前 / 改后」两侧各被一条注入用例证伪：`simplify:before-dropped`（`before_hits=0`）与
      `simplify:after-dropped`（`after_hits=0`）打出同一条 FAIL 标签（块 03 CASE 91 / 92）。
- [x] `simplify:same-suite-dropped`（`hits=0 floor=2`）与 `simplify:green-dropped`（`hits=0 floor=2`）
      分别证明「同一套」与「两侧全绿」各自承重（块 03 CASE 93 / 94）。
- [x] 禁令判据是行形状判定：`simplify:loosen-permitted` 把「不得为了让测试变绿…」的 `不得` 换成 `可以` 后
      以 `measured=1 floor=2` 转红（块 03 CASE 95）——只数名词的写法在这里仍然会报绿。
- [x] 删除义务同句判定：`simplify:dead-code-kept`（`只能删`→`可以留`，`measured=0 floor=2`）与
      `simplify:comment-preservation-allowed`（`注释`→`说明`，`measured=0 floor=1`）各证一侧（块 03 CASE 96 / 97）。
- [x] 落点小节标题从 `_TEMPLATE.md` 现场派生：`simplify:template-subsection-gone`、
      `simplify:template-heading-renamed`、`simplify:landing-dropped`、`simplify:landing-not-a-section`
      四例分别转红（块 03 CASE 98–101）。
- [x] 分工判据只数反引号里的角色名：`simplify:verifier-unreferenced` / `simplify:reviewer-unreferenced`
      把 `` `verifier` `` 换成 `验收人` 后各自转红，而路径引用 `.codex/agents/verifier.md` 不在计数内（块 03 CASE 102 / 103）。
- [x] 引用路径：`simplify:cites-drained`（`measured=0 floor=4`）与 `simplify:dead-path-cited`
      （`dead=['.codex/rules/nonexistent-simplify-rule.md']`）各证缩表与死指针（块 03 CASE 104 / 105）。
- [x] 抄检正控样本从 `maintainability.md` 现文派生：`simplify:rules-restated` 打印出被复制的那一段（块 03 CASE 106），
      样本不在场时报 HARNESS 而不是悄悄少跑一例；D4 形状守卫现在覆盖三条抄检用例（`:672`）。
- [x] `.toml` 与 `.md` 逐字相同，且 `simplify:toml-md-drift` / `toml-name-missing` / `toml-unparsable`
      三例各自转红（块 03 CASE 108–110）。
- [x] `checkers_selftest.py` → `cases=110 ok=110 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS`，
      8 条基线全 `BASELINE OK rc=0`（含新升的 `check_code_simplifier_agent`）（块 03）。
- [x] `run_all_guards.py --tier core` 全绿且新条目打印自己的判据行；`--list` 读数 `entries=34 core=28 precommit=6`（块 04）。
- [x] `harness_check_lib.citable_paths()` 上收后，B4 守门 16 行判据读数与片 026 证据相同、
      B1 守门 15 行读数与片 024 证据相同（块 05，`identical=True`）。
- [x] `validate_tasks.py` → `SUMMARY files=22 errors=0 warnings=4`（块 04）。
- [x] `check_roadmap_and_routing.py` 转绿且 `OK Phase B covers the seven planned capabilities — missing=[]`：
      本角色名已在那支守门的 `PHASE_B_KEYWORDS`（`:36`）里被要求，删掉 ROADMAP 的 B5 条目会立刻转红（块 04）。
- [x] 卡片里的 49 条 `文件:行` 指针复算 `stale=0`（块 06）。

## TDD execution record

### RED — failing test first
- Test/scenario: 判据写完、角色正文与运行时包装不在场——即「B5 只写了守门，角色还没建」这一状态。
  跑 `check_code_simplifier_agent.py`，看它是否把「被检对象缺席」报成红而不是无话可说的绿。
- Command: `python -X utf8 .codex/scripts/check_code_simplifier_agent.py`
- Exit code: 1
- Failure reason validated: 红来自被检产物缺席而不是命令没跑起来：把两份产物按原始字节摆回后同一条命令立刻
  `0 check(s) failed` / `rc=0`，还原前后 `sha256-12=0c2e522819d5,4b5f6c5a8b35` 相同（块 02，驱动
  `artifacts/tmp/b027-redgreen.py`，gitignore 内）。不用 `git checkout --` 撤注入：本工作树 `core.autocrlf=true`，
  checkout 会按 smudge 重写行尾而使「字节相同」无从证明（片 021/025/026 记的同一课）。
  开发中途先撞过三次仪器自身的缺陷而不是判据红：docstring 的用法行写错脚本名、一条 FAIL 读数的格式串占位符写错
  （`"… lines={1 and 0 or 0}"`）、`.toml` 路径手拼而没用 `repo_path()`。三者都让脚本抛异常而不是给出判定，
  修完才有上面的 GREEN；它们不涉及判据内容，所以不构成「为了让判据变绿而改判据」。
- Observed failure: `FAIL the six inputs exist — missing=['.codex/agents/code-simplifier.md', '.codex/agents/code-simplifier.toml']`、
  `check_code_simplifier_agent: 1 check(s) failed`。

### GREEN — minimum implementation
- Implementation: 新增角色 `.md`（93 行）+ 同名 `.toml`（逐字包装）、`check_code_simplifier_agent.py`
  （216 行文件 / 144 行代码 / 19 条判据 / 4 处早退 / 10 条裸下限）；把 `citable_paths()` 上收进
  `harness_check_lib.py` 并让 `check_codebase_researcher_agent.py` 改用它；`checkers_selftest.py` 加 24 例
  `simplify:*`、第 8 条基线、0 容忍抄检的取样来源换成 `maintainability.md`、D4 形状守卫前缀扩为三条；
  `run_all_guards.py` core 档注册三行；`HARNESS.md` 第 4 步补一句实现阶段内的收敛边界。
- Command: `python -X utf8 .codex/scripts/check_code_simplifier_agent.py`
- Exit code: 0
- Passing evidence: `check_code_simplifier_agent: 0 check(s) failed` / `EXITCODE=0`，19 行 `OK` 逐字见块 01，
  其中关键几条为
  `OK reference role exposes a usable set of section names — measured=9 floor=6 names=[…]`、
  `OK agent section names cover the reference role — reference_sections=9 missing=[]`、
  `OK agent names a measurement point on both sides of the change — before_hits=6 floor=2 after_hits=5 floor=2`、
  `OK agent requires one and the same suite/command on both sides — hits=8 floor=2`、
  `OK agent requires that suite to be green on both sides — hits=3 floor=2`、
  `OK agent forbids touching the judgement surface to get green — measured=3 floor=2`、
  `OK agent obliges deleting 无生产者/不可达 code — measured=3 floor=2`、
  `OK agent forbids keeping dead code as comments — measured=2 floor=1`、
  `OK agent names the template REFACTOR subsection verbatim — title='REFACTOR — optional' named=True`、
  `OK agent defers acceptance and code quality to the existing roles — verifier_refs=3 floor=2 reviewer_refs=3 floor=2`、
  `OK agent cites >= 4 checkable repo paths — measured=6 floor=4 cited=[…]`、
  `OK role text does not restate the rules it cites — measured_hits=0 sample=[]`、
  `OK agent .toml body equals the agent markdown verbatim — toml_chars=3183 md_chars=3183 equal=True`。
- Regression evidence: `python -X utf8 .codex/scripts/dev/checkers_selftest.py` →
  `SELFTEST cases=110 ok=110 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS` / `EXITCODE=0`，
  8 条基线全 `BASELINE OK rc=0`，其余 86 例（intent/roadmap/template/replay/spec/review/context/research）一字未动（块 03）；
  `--tier core` 整跑 28 条全绿、`validate_tasks` `errors=0`（块 04）；
  B4/B1 两支守门在上收共享派生后判据读数与片 026/024 证据逐字相同（块 05）。

### REFACTOR — optional
- Cleanup: ① **本片自己的一次「下限不承重」事故**：整轮注入自检第一次跑打印
  `CASE 89 MISMATCH simplify:reference-sections-drained - expected FAIL label, rc=0, seen=[]` 并
  `SELFTEST cases=110 ok=109 mismatch=1` / `OVERALL: FAIL`。病因是 `MIN_ROLE_SECTIONS = 4` 低于对齐对象的
  任一单独形状——`code-reviewer.md` 的 9 个小节名由 4 个 `<tag>` 小节 + 5 个 `## ` 标题拼成，注入只把 `<` 换成 `[`
  （抽掉尖括号那一形状），剩下的 5 仍然 ≥ 4，于是那条「裸下限承重」的正控根本不会响。改成 6（现场实测：两种形状各
  自消失都只剩 5 或 4）后同一例转红 `measured=5 floor=6`，整轮 `ok=110 mismatch=0`。这条不是为了让用例过而调下限：
  下限的作用正是在「比对对象整体消失」时认出来，取 4 时它对本片唯一的抽形注入免疫。
  ② `citable_paths()` 上收进 `harness_check_lib.py`：片 026 的 `check_codebase_researcher_agent.py` 里已有一份
  `cited_paths()`（`backticked_paths()` + 去通配 + strip），本片要共用同一筛选，再加一份就是「消灭第二份实现」
  的角色自己带着第二份实现。中途先留过一个一行转发壳 `def cited_paths(text): return citable_paths(text)`，
  那正是 `maintainability.md` 禁止的「只有一次调用的抽象」，删掉后调用点直接用共享函数。合并前后两支守门的判据
  读数逐字相同（块 05）。
  ③ 0 容忍抄检的正控样本来源从「写死 `regression.md`」改为随用例声明：本片的 `simplify:rules-restated` 要拿
  `maintainability.md` 现文取样，写死会让它拿错文件派生正控——D4 记过的 fail-open 形状。D4 形状守卫随之从两条
  前缀扩为三条 `(intent:copied-methodology, research:rules-restated, simplify:rules-restated)`，
  仍然只查形状（用例必须走 `append_sample`），不查样本本身。
  ④ ROADMAP B5 的 `范围` 一行原文漏列本片实际改到的两条路径（`harness_check_lib.py`、
  `check_codebase_researcher_agent.py`），按真实可写面补齐；`验收方式` 一行改为「判据条数与注入用例条数以
  `checkers_selftest.py` 现场取，不写进本条」，避免又一处会漂的写死条数。
  ⑤ 本片的指针校正依然是被驱动逼出来的：`artifacts/tmp/b027-pointers.py` 第一次跑报 `stale=1`
  （`checkers_selftest.py` 的顶层目录名镜像区间我写的是 `:440-448`，实测在 `:524-532`），修完 `stale=0`。
     本片同时把片 024/025/026 三张卡里被 027 位移的区间按现值校正，并把「本片之前是对的、之后成为活指针」的
     过程写进各卡正文而不是悄悄替换数字。
- Regression evidence: 块 03 的 110 例必须包含 87–110 这 24 条 `simplify:*` 且 8 条基线全 `OK`；块 03 CASE 89 覆盖 ①
  的转红读数；块 05 覆盖 ②；块 03 CASE 106 与 `:672` 覆盖 ③；块 04 的 `check_roadmap_and_routing` 覆盖 ④；
  块 06 覆盖 ⑤。

## Implementation notes

- 判据的形状：不查「有没有写简化」，查「行为不变的声称能不能被复算」。所以两侧各一条计数下限、
  「同一」与「全绿」各一条、禁令查行形状、删除义务要求两个词同句——它们每条都能被一条注入用例抽干。
- 为什么禁令判据数**行形状**而不是名词：`PROHIBITION_RE`（不得/禁止/不许）与 `JUDGEMENT_RE`
  （测试/判据/断言/白名单/下限）必须出现在同一行。单数「测试」二字时，把「不得为了让测试变绿而改测试」
  换成「为了让测试变绿可以改测试」判据仍然绿；行形状判定在 `simplify:loosen-permitted` 下立刻 `measured=1 floor=2`。
- 删除义务同理：`simplify:dead-code-kept` 只把「只能删」换成「可以留」，三个死代码名词一个没动，
  计数判据从 `measured=3` 掉到 `0` 而转红。「注释保留」另立一条下限 1 的判据，因为整段措辞都在场而
  唯独这一句被删时，前面的同句判定仍然绿。
- 分工判据只数反引号里的角色名（`` `verifier` `` / `` `code-reviewer` ``）：角色正文里
  `.codex/agents/verifier.md` 这种路径引用也含 `verifier` 六个字母，裸子串判定会让「删光分工句、只剩一条路径引用」
  照样报绿（片 026 的同一课，这次落在角色分工上）。
- `MIN_ROLE_SECTIONS = 6` 而实测 9：下限取在「两种形状任一整体消失」之后（尖括号 4 个、二级标题 5 个），
  这样 `:428` 那条抽形注入有牙；同时保留 3 格余量给 `code-reviewer.md` 的正常增删小节。
- 落点标题从 `_TEMPLATE.md` 现场派生（`REFACTOR_HEAD_RE` 抓 `^###\s+REFACTOR…$`），角色正文必须点名它且
  所有含「只写」的行都必须指向它——模板改名而角色没跟就是死指针，这条由判据而不是自觉。
- `.md` → `.toml` 仍由 `artifacts/tmp/gen_agent_toml.py` 一次性生成（仓库没有构建步骤，片 024 已记这条代价），
  生成后由「正文逐字相同」判据钉住漂移。

## Verification report

- Verdict: PASS（守门末态 19 条 + 一次同命令 RED/GREEN + 110 例注入含 8 基线 + core 档整跑 + B4/B1 行为等价复算 +
  49 条指针复算；见块 01–06 与 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-code-simplifier-role-027.txt`
  块 01（守门 19 条判据末态逐字）、块 02（RED/GREEN 对照与 sha256-12）、块 03（110 例注入 + 8 条基线 +
  `simplify:*` 24 例逐字，含第一次整轮 `mismatch=1` 的原始读数）、块 04（`--list` / `--tier core` /
  `validate_tasks` / `check_roadmap_and_routing`）、块 05（上收共享派生后 B4 与 B1 守门的逐字对比读数）、
  块 06（49 条行号指针复算与本片造成的漂移）、块 07（本片没覆盖之物）。
  环境：Windows 10.0.26200 / x64 / Python（`sys.executable`，一律 `-X utf8`）/ Git Bash。
  捕获文件与驱动留在 gitignore 的 `artifacts/tmp/`（`run031-selftest.txt`＝下限校正前的整轮原始 FAIL 读数、
  `run032-selftest.txt`＝末态整轮、`run033-redgreen.txt`、`run033-b5.txt`、`run033-b4.txt`、`run033-b1.txt`、
  `run033-equiv.txt`、`run033-list.txt`、`run033-core.txt`、`run033-validate.txt`、`run033-roadmap.txt`、
  `run033-grep.txt`、`run033-pointers.txt`、`run034-core.txt`、`run034-precommit.txt`，
  驱动 `b027-redgreen.py` / `b027-pointers.py` / `b027-equivalence.py` / `b027-sizing.py` / `gen-027-evidence.py`）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片删除的实现只有一处：
  `check_codebase_researcher_agent.py` 的本地 `cited_paths()`，等价行为由 `harness_check_lib.citable_paths()` 提供，
  判据一条未删、未改宽（块 05：B4 16 行读数、B1 15 行读数与片 026/024 证据 `identical=True`）。
  对 `checkers_selftest.py` 的两处行为改动都朝**更严格**方向：抄检样本必须显式声明来源、形状守卫多管一条用例。
  本片**收紧**了一条既有下限：`MIN_ROLE_SECTIONS` 4 → 6，方向是提高要求（原来那条正控根本不会响），
  未放宽任何判据、未改宽任何断言、未往任何白名单加名字。
- Gaps: ① 角色从未真的删掉一处第二份实现——判据只证明「产物成对、形状会响」，不证明按这套契约真能安全收敛；
  第一次真实收敛要落在后续那片（候选就是 `check_spec_author_agent.py` 的四份同形自建工具）。
  ② `citable_paths()` 只认 `backticked_paths()` 的已知前缀（Docs/.codex/android/artifacts/README），
  所以 `` `frontend/` `` 这类顶层目录引用不在死指针判定内；背景包那条由 `check_context_pack.py` 管，两处不重叠。
  ③ `MIN_CITED_PATHS = 4` 而实测 6：路径下限没有第二条「介于两者之间」的用例（`simplify:cites-drained` 直接删到 0），
  这是有意的——下限的作用就是挡住空集通过，不需要逐格试。
  ④ 角色正文的三条中文计数判据（改前/改后/同一/全绿）对英文同义改写不设防：把 `<contract>` 翻译成英文写仍然
  满足不了判定，但反过来如果将来允许英文正文，这四处下限要一起重做（未做，属已知松处）。
  ⑤ `.toml` 是否真被 Codex 运行时加载没有运行时证据。
  ⑥ 无独立 verifier 轮次（用户要求一站到底；本片属 harness 仪器面，19 条判据有 24 例注入覆盖，
  且下限不承重那次事故是自检抓出来的，不是自检之外的人抓的）。
- Risks: 共用 `citable_paths()` 与 `agent_section_names()` 之后，`_TEMPLATE.md`、`verifier.md`、`code-reviewer.md`
  的小节/字段改名会同时让 B1/B4/B5 三支守门转红——这是想要的耦合，但它让「顺手改模板措辞」变成一次跨文件改动，
  改之前要先看这三支的 FAIL 标签。

## Handoff notes

- Completed: B5 全部交付物（角色两份、守门 19 条、24 例注入、第 8 条基线、core 档注册、HARNESS 第 4 步接线）、
  `citable_paths()` 的单份化（B4 守门 −15 行）、片 024/025/026 被本片位移的行号指针校正、
  ROADMAP B5 的 `范围` 补齐与条数措辞去写死、一条不承重下限的现场校正（4 → 6）。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: B2（eval-keeper，`.codex/evals`）与 B6（metrics-reporter）；随后 B0 补
  `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 的 `## Technical spec（技术规格）`，
  那一片是 `codebase-researcher` 第一次真实产出 `Behavior impact` 清单的机会。
  本角色第一次真实使用时，候选收敛点是 `check_spec_author_agent.py` 自建的
  `repo_path`/`read_text`/`section_titles`/`front_matter` 四份同形工具（`harness_check_lib.py` 里都已有对应件）。
- Watch-outs: ① 本片第一次整轮自检 `OVERALL: FAIL`（`mismatch=1`）——那类「正控不会响」的失效只有整轮跑才会暴露，
  加用例时必须重跑整轮而不是只跑新条目。② 再加一条 core 档守门会让 `--list` 的 `entries/core` 计数继续漂
  （现为 `entries=34 core=28`），卡片里不要写死这两个数字，写命令让人现场取（片 023 定的规矩）。
  ③ ROADMAP **D11** 仍未闭合：`--tier core` 里 `cdp_shot_selftest` 会间歇转红而整跑读数不打印失败用例名。
  本片末态连着两次整跑 core（`artifacts/tmp/run033-core.txt` 与 `run034-core.txt`）都 `28 selected guard(s)` 全绿、
  没有复发；但这条记账不因两次绿而关闭——它的判据是「复发时能不能一眼看到用例名」，那一条还没修。
  ④ 本片的下限教训（下限必须高于被检集合的任一单独形状，否则单形状抽干不响）值得在下一支配对守门
  （B2/B6）设计时直接套用。
