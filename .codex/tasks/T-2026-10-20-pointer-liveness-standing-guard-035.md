# Filename: T-2026-10-20-pointer-liveness-standing-guard-035.md
# Codex Harness Task: 把「活指针复算」从 gitignore 里的一次性驱动升成常驻仪器（ROADMAP D13）

id: T-2026-10-20-035
title: 活指针形状守门、指针复算器常驻化、10 例双向自检与本片自己被抓到的一条号段
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 391
shared_code_touched: false
depends_on: [T-2026-10-19-guard-runner-one-command-roster-023.md, T-2026-10-19-evals-inventory-and-runner-028.md, T-2026-10-19-metrics-reporter-role-031.md, T-2026-10-19-spec-author-technical-section-032.md, T-2026-10-20-loop-watcher-role-033.md, T-2026-10-20-source-of-truth-inventory-034.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary：`T-2026-10-18-treelearn-process-migration-001` 确认把 `.codex/` 作为 Treelearn 唯一 AI 研发流程入口，
并按 ROADMAP 逐片补齐流程资产。本片不来自 Phase B/C 的任何一条条目，而是用户在片 034 收尾时批准的那条**登记欠账**
（原话：「若设备暂时不可用，我按卡片登记的欠账改做『difflib 指针映射上收进 `harness_check_lib.py` + 给自检例数补一支
带裸下限的漂移脚本（顺带把 `22 例` 那句收进清单）』」），对应 ROADMAP 里第三次登记的同一缺陷类 D13。

- Problem/background: 「给文档里的 `文件:行` 声称复算行号」从片 026 起每片现写一支驱动，登记时点普查得
  13 支 `*pointer*.py`、合计 2,660 行（`artifacts/tmp/run298-driver-census.txt`），全部在 gitignore 的暂存目录里，
  没有一支进过 `.codex/scripts/`。`mapping()` 的 difflib 映射抄过两份，`SKIP-FRESH` 只在片 034 出现过一次。
- Users/scenarios: 下一位给 `run_all_guards.py` 或任一被引用文件加行的实现者——他加完行不会想起要跑一支
  不存在于版本库里的脚本；以及按行号跳到另一段内容的读者。
- Expected outcome: 两条命令常驻且注册进 core 档：形状判据 `check_pointer_liveness.py`（三种必死形状各一条红）
  与复算器 `dev/remap_pointers.py`（默认干跑、`--apply` 才写、算不出即 REVIEW 并拒绝写）。
- Core flow and reversibility: 纯标准库只读判据 + 一个显式 `--apply` 的写工具；本轮所有正文改动逐文件可回退，
  不动业务代码、不动任何既有下限或期望标签。
- Inputs/outputs: 输入是当前态扫描面（`SCAN_PREFIXES`，实测 68 个文件；声称条数随本片写入逐轮为 101→109→110，
  清单自身 15→16→17，末态 110/17 见 `run324`）与历史面（`run293` 那轮 215 条、卡片写出后 219 条，只报不判）；
  输出是 8 条判据行 + 末行读数，以及自检的 10 例双向用例。
- State and exception handling: 夹具不健康 / 用例数对不上 → 退出码 3 的仪器故障档，不把「前置条件缺失」读成 PASS；
  被检集合（扫描面、匹配集、清单条目、历史面）各有裸下限，抽干即红。
- Constraints and trade-offs: **只判形状、不判语义**——「行号还在但那行已不是那件事」要求把权威正文抄进文档才可判，
  与 C3 清单冲突，所以那一半交给复算器对着 diff 做。代价：复算器只在「本片相对 HEAD 有改动」时才有意义，
  它不是每轮全跑的判据（判据才是）。
- AI recommendation and rationale: 按用户批准的欠账范围动手；范围里额外收了三点同源的（解析字符类吞掉中文名路径的
  缺陷、复算器把「拒绝」变成带落点内容的输出、清单里那条 生成型 权威行号的实际过期），因为它们就是本片要防的那件事。
- User choice and custom additions: 沿用片 021–034 已确认的「一站到底、每片本地提交、不 push」方式，本片无新增用户裁定。
- AI-autonomous decisions: ① 新守门走**自带 `--selftest`** 那一族而不是 `checkers_selftest.py` 的 BASELINES——
  实测到 `build_workspace()` 只拷 `.codex/`、`Docs/`、`artifacts/metrics/` 并把其余顶层目录建成空壳，
  指向 `android/**` 的 78 条声称在那种副本里全部读成死指针（`run313-red-diag.txt`），把它做成基线等于交付一条恒红判据；
  ② 判据保留**自己那份** `SCAN_PREFIXES`（与清单守门同形但独立失败），而复算器 **import 判据的 `scan_files`**；
  ③ 自检例数与判据数不写进散文，由脚本自己打印；④ 生产行口径按 `harness-tasks.md` 那句「Tests… do not count」
  排除自检注入面，并同场打印含注入面的合计数（见块 06）。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `check_pointer_liveness.py` 直跑绿 + `--selftest` 十例双向绿 + core 档整跑内两条 PASS +
  RED 对照（同一条死声称：旧的两支 rc=0、新的 rc=1）。
- Facts: 当前态面形状零缺陷（`judged_dead=0 judged_beyond=0 judged_reversed=0 judged_blank=0`，末态 `run324`）；
  历史面残留 `run293` 那轮是 `citations=215 dead=3 beyond_eof=3 landing_blank=2`，卡片写出后那两轮
  （`run322`/`run324`）是 `citations=219 dead=3 beyond_eof=4 landing_blank=2`——多出的 4 条声称与 1 条越界就是这张
  卡片自己（越界那条是正文引用的注入靶 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:9999`），而本条每多写
  一次这种引用，这两个数就再各加一：历史面「只报不判」就是为了不让读数追着卡片跑。按片 034 的裁定保留为读数。
- Assumptions: 无。
- Unknowns: `.workbuddy/` 该不该继续留在仓库里（片 034 Gaps ② 延续，仍无人裁定）。
- Risks: 见 Verification report 的 Risks；本片把「下一片加行不跑驱动就留活指针」这条风险换成了一条会响的判据，
  但复算器本身的 `--apply` 仍需要有人跑（它不在 core 档里，core 档里的是判据与判据的自检）。
- Deferred: 生成型条目的权威行号无人核对内容（本片实测到一次过期并手工改正）；`22 例正反双向自检` 那句手抄计数；
  历史面残留死指针的处置。
- Non-goals: 不回填历史面与卡片里的行号；不动那 13 支 gitignore 驱动；不改 `checkers_selftest.py`。
- Skipped non-critical questions and reason: 是否把复算器也注册进 core 档——它需要「相对 HEAD 的 diff」才有意义，
  在没有改动的轮次里永远打印 `nothing to remap`，注册它只会给汇总面增加一条恒绿噪音，故不注册（写成 Gaps 供裁定）。

## Goal

让 `python -X utf8 .codex/scripts/check_pointer_liveness.py` 成为「当前态面上这条 `文件:行` 声称还活着吗」的
常驻回答者：三种必死形状（路径不在场、行号越界含区间倒置、落点是空行）各自一条判据、每种坏法都能被注入证伪，
并把片 026–034 每片现写的复算逻辑上收成一支常驻修复器 `.codex/scripts/dev/remap_pointers.py`——
它 import 判据的扫描面，保证「搬」和「判」对同一个面动手。

## Scope

### In scope
- `.codex/scripts/check_pointer_liveness.py`：新增，纯标准库，8 条判据行、4 个裸下限
  （`MIN_SCAN_FILES=40`、`MIN_CITATIONS=40`、`MIN_HIST_CITATIONS=20`、`MIN_INDEX_CLAIMS=6`）与自带
  `--selftest`（4 个夹具文件 / 60 个持有者 / 25 张卡 / 10 例注入）。
- `.codex/scripts/dev/remap_pointers.py`：新增，difflib HEAD→工作树映射 + 内容核验（逐字 → 子序列两级回退）、
  `SKIP-FRESH` / `SKIP-RULES` / `VERIFY-FRESH-TARGET` 三类拒绝各自附带被拒的东西、`REVIEW` 未清空时拒绝写入。
- `.codex/scripts/harness_check_lib.py`：`repo_path/exists/read_text` 增加可选 `root`；新增 `blank_landings()`；
  `citation_problems()` 增 `root`；`CITATION_RE` 两处口径修正——前缀表加 `AGENTS`，路径字符类从
  `` [^\s:`（），、；] `` 放宽为 `` [^\s:`] ``。
- `.codex/scripts/run_all_guards.py`：core 档注册判据本体与它的 `--selftest`（各 1 条，共 5 行）。
- `.codex/context/source-of-truth.md`：新增一条 生成型 条目「当前态面的活指针声称条数」，覆盖面表 `.codex/scripts/` 由 3 改 4。
- `.codex/HARNESS.md` 第 8 步接线（命令，不含数字）；`.codex/ROADMAP.md` D13 登记并闭合、D12 里三处只写文件名的声称补全。
- `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:48/:79` 两条被本片加行推歪的声称（由复算器搬动）。
- `artifacts/metrics/report.md` 重生成（注册表条数与卡片计数随本片变化）。

### Out of scope
- 历史面（`.codex/tasks/**`、`artifacts/**`、`.codex/references/**`、`Docs/intent/**`）的死指针回填——实测
  `run293` 那轮 `dead=3 beyond_eof=3 landing_blank=2`，按片 034 裁定保留为读数。
- 业务代码（`android/**`、`frontend/**`、`backend/**`）：本片零命中，`android/README.md` 与 `frontend/**` 一行未动。
- 把复算器注册进 core 档（理由与后果写在 brief 的 Skipped 项，留待裁定）。
- 「语义漂移」进判据（明确不可判，见 Goal 上方的边界）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增两支只读/显式写入的仪器 + core 档两条注册；被保留的旧行为是
  所有既有判据、下限、期望标签与档位语义一字未动——`--tier core` 由片 034 的 34 条变 36 条（`run309`/`run315`），
  `--tier precommit` 仍是 6 条。业务代码零命中由 `git -c core.pager=cat diff --numstat HEAD -- android frontend`
  的空输出证明（本片没碰 `android/README.md`，它只作为被声称路径被读取，见块 07 当场重读）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 声称的**解析**只有一份，
  `harness_check_lib.path_line_citations`，消费者是本片判据、`run_evals.py` 的 `pointers-alive` 与
  `check_source_of_truth.py` 的权威行号解析——所以字符类放宽会同时改变三者的判定集合（实测当前面 101→110 条、
  历史面 204→215 条），末态三者的读数都在块 04。复算器的持有者集合 import 自判据的 `scan_files`（同一个面）。
  反向搜索被改写句子的读者：`.codex/HARNESS.md` 第 8 步由 `check_context_pack.py` 与 `check_roadmap_and_routing.py`
  读，两者末态 rc=0；`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:48/:79` 的号段由 `run_evals` 的
  `pointers-alive` 与被搬动行自身的形状检查共同兜住。
- Native bridge or independent-client compatibility/update requirements: 不适用——未触碰 `android/**` 原生桥接、
  SQLite、资源或前端运行时；副本实验（`b035-red.py`）里 `android/**` 只作为被声称路径被读取，从不写入。
- Behavior-neutral non-applicability reason: 本片产物是判据资产与文档，运行时行为面为空。

## Documentation impact record

- Changed runtime facts: 没有运行时事实变更；变更的是「哪条命令有权回答『这条指针还活着吗』」——从「某片临时写的
  一支 gitignore 脚本」变成「core 档里的两条判据」。
- Authoritative code/config/migration evidence: `.codex/scripts/check_pointer_liveness.py`（判据本体，
  下限在同文件 `MIN_*` 四行）、`.codex/scripts/dev/remap_pointers.py`（复算本体）、
  `.codex/scripts/harness_check_lib.py:178`（唯一的解析口径）、`.codex/scripts/run_all_guards.py:123-127`（core 档注册）。
- Documentation surfaces checked: 判据自己的当前态面（实测 68 个文件）+ 历史面（110 个文件）+
  `b035-probe.py` 的两面形状探靶与 `b035-paren-probe.py` 的解析吞句普查。
- Documentation files updated: 以 `git -c core.pager=cat diff --stat HEAD` 末行为准（块 07 现场重读），
  新增未跟踪文件为两支脚本、本卡片与证据文件；散文面只动了 `.codex/HARNESS.md`（同句扩写，不挪行号）、
  `.codex/ROADMAP.md`（D12 三处指针补全 + 追加 D13 一节）、`Docs/spec/` 两条号段、`.codex/context/source-of-truth.md`（一个条目 + 一行计数）。
- Producer/consumer/sibling/compatibility paths checked: `AGENTS.md`（路由表未新增行——本片不引入新角色或新规则文件，
  判据走 `.codex/HARNESS.md` 第 8 步这条既有路径）；`.codex/README.md`（无新增登记条目，`check_roadmap_and_routing.py`
  的 `registered-entry list` 判据末态 `entries=6`）；`artifacts/metrics/report.md` 重生成后由 `metrics_report` 判据复算。
- Superseded phrases/examples reverse-searched: 本片没删任何既有句子；`b031-dup-scan.py` 末态
  `raw_hits=166 cjk_prose_hits=0 non_prose_hits=166 known_old_dup hits=0 positive_control fired=True problems=0`（块 06）。
  被写死进散文的生成数由清单守门现场复扫：`no generated count is written into prose — measured=0 hits=[]`。
- `documentation impact: none` reason and evidence: 不适用——本片主体就是文档与判据面。
- Historical/PRD handling: 卡片与 evidence 里的行号是当时读数，不回填；那 13 支 gitignore 驱动保留原样
  （删掉它们只会让 D13 的登记读数失去出处），`Docs/01-demand/**` 冻结基线一个字没动。

## Acceptance criteria

- [ ] `python -X utf8 .codex/scripts/check_pointer_liveness.py` 退出码 0：8 条 `OK` 判据行逐字进证据，末行
      `check_pointer_liveness: 0 check(s) failed`（`run287` 放宽前 / `run293` 放宽后 / `run311` 经 runner / `run315` 整跑内）。
- [ ] 三种必死形状各自会响：`--selftest` 的 CASE 01–04 分别注入「删掉被声称的文件」「行号越界」「区间倒置」
      「落点改成空行」，除退出码外还断言**具体那条 FAIL 标签**出现（`run288` / `run294`，`cases=10 mismatch=0`）。
- [ ] 集合被抽干即红，不许「零命中即绿」：CASE 05–08 各抽干扫描面、匹配集、清单声称集、历史面四条下限。
- [ ] 反向不误伤：JVM 栈帧里的 `File.java:45` 不算声称（CASE 09）、历史面的缺陷只报不判（CASE 10），两例期望退出码 0。
- [ ] 缺口是真的而不是自说自话：同一条越界声称注入到副本后，`check_source_of_truth.py` 与 `run_evals.py` 仍 rc=0，
      本片新判据 rc=1 并点名 `line beyond eof=110`（`b035-red.py`，`run314`）；副本未注入时三支基线全绿，
      否则驱动打印 `INSTRUMENT FAULT` 并退出码 3（`run312`/`run313` 就是这条前置条件真的响过一次）。
- [ ] 复算器搬得动本片自己造成的漂移且不越改越错：`EDITS applied=2`（两条指向 `harness_check_lib.py:174→178`、
      `run_all_guards.py:124→129`，`run291`），复跑幂等（`MOVED 声称=0`，`run297`/`run299`）。
- [ ] 复算器的每一类**拒绝**都可读：`SKIP-FRESH` 六行全部带落点内容（`run305`），指向本片新建文件的声称单独枚举
      （`VERIFY-FRESH-TARGET claims=1`，`run306`）——那一条就是清单里 `check_pointer_liveness.py:58` 的过期号段，
      改正为 `:59` 后复跑确认落点内容变成 `MIN_CITATIONS = 40`（`run307`）。
- [ ] 档位面不回归：`--tier core` `pass=36 fail=0 inconclusive=0 fault=0`、`--tier precommit`
      `pass=6 fail=0 inconclusive=0 fault=0`，两轮串行单独跑、不与任何命令并发（D12 纪律）。
- [ ] 解析口径仍是一份：`run_evals` 的 `pointers-alive` 与 `check_source_of_truth` 的权威行号解析在放宽字符类后
      双双 rc=0（`run295`），清单守门读数 `entries=17 kinds={'声明型': 14, '生成型': 3}`（`run296`）。

## TDD execution record

### RED — failing test first
- Test/scenario: 目标命令 `python -X utf8 .codex/scripts/check_pointer_liveness.py` 在写实现之前跑一次——它要判的
  那件事当时没有任何常驻仪器在做。另附一条**对照**（不是同一命令的第二次跑，所以单列）：`b035-red.py` 在一份整树
  副本里往 `.codex/HARNESS.md` 注入一条越界声称（指向真实存在的 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:9999`，
  该文件实测 110 行），问当时在场的两支批判据会不会响。
- Command: `python -X utf8 .codex/scripts/check_pointer_liveness.py`
- Exit code: 2
- Observed failure: ① 目标命令没有输出任何判据行——打印是
  `can't open file '.codex\scripts\check_pointer_liveness.py': [Errno 2] No such file or directory`，脚本还不存在。
  ② 对照读数：注入后 `check_source_of_truth.py` rc=0
  （`0 check(s) failed`）、`run_evals.py` rc=0（`0 check(s) failed`），两支都把这条死声称当没看见；
  ③ 13 支 / 2,660 行的一次性驱动普查（`run298`）证明这件事一直在做，只是从来不是仪器。
  对照驱动 `python -X utf8 artifacts/tmp/b035-red.py` 末态退出码 0 = 对照成立（期望 rc 全部命中），
  前置条件不满足时它自己返回 3——`run312`/`run313` 就是那条出口真的响过两次。
- Failure reason validated: 失败原因是「没有常驻判据」而不是「命令写错」：同一条注入在两支旧仪器上零反应且各自
  打印了全绿读数，本片判据在场后对同一条注入精确点名 `line beyond eof=110`（`run314`）。
  `run312`/`run313` 两轮把「基线不绿就不许继续」这条仪器故障档跑成了真出口，RED 的对照面因此可信——
  那两轮的不绿来自夹具（副本里没有 `android/**`，78 条声称读成死指针），改的是夹具而不是判据。

### GREEN — minimum implementation
- Implementation: 判据 8 条 `c.check`（下限 4 条 + 形状 4 条）+ 一条 `c.note`（历史面只报不判）；
  复算器按「逐字相等 → 子序列且首行相同」两级核验候选号段，映射来自 difflib opcodes，
  `replace` 块映到替换段末行、增删映到邻行；清单加一条 生成型 条目；HARNESS 第 8 步接两条命令。
- Command: `python -X utf8 .codex/scripts/check_pointer_liveness.py`
- Exit code: 0
- Passing evidence: 判据 GREEN 那一轮（`run293`）`holders=4 citations=109 index_claims=16 judged_* 全 0
  hist_citations=215`，写出卡片后的末态（`run324`）是 `citations=110 index_claims=17 hist_citations=219`、
  `check_pointer_liveness: 0 check(s) failed` 且 `judged_*` 仍全 0；自检 `BASELINE fixture rc=0 checks_failed=0` + CASE 01–10 全 `ok` +
  `SELFTEST cases=10 mismatch=0 expected=10` + `OVERALL: PASS` + `EXITCODE=0`（`run294`）；复算器干跑打
  `VERDICT: DRY RUN`、`--apply` 轮打 `EDITS applied=2` + `VERDICT: PASS`（`run291`）。
- Regression evidence: `check_source_of_truth` / `run_evals` / `check_roadmap_and_routing` / `check_context_pack` /
  `check_stack_facts` / `validate_tasks` 六条直跑 rc=0（`run295`/`run296`），经 runner 的 10 条子集
  `pass=10 fail=0`（`run311`），以及末态两档整跑（`run315` core / `run316` precommit，串行单独）。

### REFACTOR — optional
- Cleanup: ① 复算器第一稿带着起草脚手架（一处恒不执行的 `if False` 改写表达式、`ALWAYS = None`、加载了不用的
  `claims_in`、`exclude` 读了不用），整份重写为现在的结构；② 判据夹具原先让 `.codex/scripts/dummy.py` 在第 9/18 行
  留空行，而合成的 10 条清单声称落在奇数行——健康的夹具自己跑不过，10 个反向用例因此全都不可信；改成全行有正文并写明原因；
  ③ `_capture` 之前把 `run()` 的返回码丢在原地、`build_fixture` 被改成返回 `None` 会让「夹具不健康」这条出口
  永远为真，两处一并修正；④ 注入用的定位串从 `` `:3` `` 改成 `:3`` `——反引号在冒号前的那种写法在夹具里不存在。
- Regression evidence: 重构后重跑同一组命令，读数不变（`run294` 与 `run288` 的十例逐项相同、`run293` 与 `run287`
  的判据行集合相同，只有声称条数因解析放宽从 101 变 110），两档整跑 `run315`/`run316` 覆盖末态。

### LOOP — optional
- Command and rounds: 按片 033 的 `loop-watcher` 口径记回路收敛：`b035-red.py` 3 轮（`run312` 夹具故障 →
  `run313` 诊断输出 → `run314` 对照成立）、`remap_pointers.py` 干跑 6 轮（`run289`/`run290`/`run297`/`run299`/
  `run304`/`run306`/`run307`）、判据直跑 3 轮（`run287`/`run293`/`run311`）。
- Failure identity and repeat count: 同一身份的失败最多重复 1 次就换打法——`run312` 的「副本里 78 条死指针」
  没有被当成判据缺陷重跑第二次，而是当场改夹具；`VERIFY-FRESH-TARGET` 那条号段被 `:58→:59` 一次改正后复跑确认。
- Stop condition hit: 判据绿 + 自检十例绿 + RED 对照成立 + 复算器幂等（`MOVED 声称=0`）+ 两档整跑绿，
  且没有为了变绿动过任何下限或期望标签。
- Handoff: 三条残余写进 Gaps，其中「生成型条目的权威行号无人核对内容」本片已用输出层（`VERIFY-FRESH-TARGET`）
  变成看得见的一行，但**仍未**是判据。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（本片不需要例外；`estimated_production_lines: 391` 走的是既有口径的软告警档，
  `harness-tasks.md:47` 那句「Tests, documentation, and generated files do not count toward this estimate」
  是排除自检注入面的依据，含注入面的合计 540 同场打印在块 06，不做隐藏）。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 判据为什么**保留自己那份** `SCAN_PREFIXES`：与 `check_source_of_truth.py` 共用一份常量会让两片守门同时死于
  同一个改动，而「扫描面被改窄」正是它们各自要防的事；片 034 已按这个理由写过一份，本片沿用并写明。
  复算器反过来**必须** import 判据的那一份——修复器和判据对不同的面动手，就会出现「搬了但判据看不见」或反之。
- 为什么 `--selftest` 里反向用例除退出码外还断言 FAIL 标签：只判「非 0」的用例能被任何一条不相干的判据红蒙过去。
  这是片 030/031 立的口径，本片十例全按它写。
- `blank_landings()` 为什么放进 lib 而不是判据私有：落点空行这件事，`citation_problems()` 的「在范围内」判据
  结构上看不见；放进 lib 后任何消费解析结果的判据都能同口径复用，本片与片 034 的临时探靶读到的就是同一件事。
- 解析放宽 `` [^\s:`（），、；] `` → `` [^\s:`] `` 的依据：反引号成对已经把一条声称关进一个 token，
  真正的边界是空白与反引号本身；全角括号是 `Docs/01-demand/知芽（TreeLearn）…` 这批文件名的合法字符。
  放宽前后各跑一次 `b035-paren-probe.py` 与判据（块 04），证明它只让 2 条中文名声称进入被判集合、没引入新的误判。
- 为什么 `AGENTS` 进前缀表：清单把「任务状态唯一可写目录」的唯一权威直接写在 `AGENTS.md:34` 上，
  原前缀表没有这一项，等于把一条权威声称放在判据视野外。根级入口文件没有目录分隔，但全仓只有一个同名文件，不歧义。
- 复算器不注册进 core 档（写成 Gaps 供裁定）：它读的是 `git diff --name-only HEAD`，在没有改动的轮次里
  永远打印 `nothing to remap`，注册它等于给汇总面加一条恒绿噪音——那类噪音的结局一定是被人调宽。

## Verification report

- Verdict: PASS（判据在场、三种死形状各有会响的注入、抽干集合各有下限、反向两例不误伤、RED 对照显示旧仪器集
  对同一条死声称零反应、复算器搬对了本片自己造成的两条并抓住了一条真实过期的号段；未覆盖的部分逐条写在 Gaps，
  不算已覆盖）
- Evidence: `artifacts/evidence/T-2026-10-20-pointer-liveness-standing-guard-035.txt`
  （driver=`artifacts/tmp/b035-evidence.py`，只做拼接与现场补跑，不改写任何读数。块 00 判据直跑的 8 条判据行逐字
  与一次当场重跑；块 01 的 RED 对照（含两轮 `INSTRUMENT FAULT` 的真实出口）与 13 支驱动的普查；块 02 的十例
  自检两轮与仪器故障档；块 03 复算器的干跑/`--apply`/幂等/`SKIP-FRESH` 落点内容/`VERIFY-FRESH-TARGET` 抓到的
  那条 `:58→:59`；块 04 解析放宽前后两面读数；块 05 清单与同族守门；块 06 尺寸与重复短语；块 07 两档整跑、
  runner 子集复跑、`git status` / `git diff --stat`；块 08 写出之后的复跑与一份作废读数）。原始捕获在 gitignore 的
  `artifacts/tmp/run*.txt`（号段 run286–run325，装配驱动核对 38 份捕获全部在场）可重跑；没落盘的轮次在对应块里写明没落盘。
  末态两档整跑是 `run315`（core，`pass=36`）与 `run316`（precommit，`pass=6`），串行单独跑、不与任何命令并发；
  这两轮在**卡片与重生成后的报告都在场**时跑的。证据文件本身在那之后写出，那一段没有再整跑，只复跑会读它们的：
  `run320`（报告第三次 `--write`，证据文件在场）、`run322`（七条只读判据 + 复算器）、`run323`
  （「再 `--write` 一次是字节 no-op」的对照，`report_bytes_identical_after_second_write=True`——这条是
  run315/run316 判到的报告字节仍然有效的理由，不是「重生成后要整跑」那句的豁免）、
  `run324`（九条只读判据加复算器完整干跑，全部 rc=0，`VERIFY-FRESH-TARGET claims=1` 的落点内容当场是
  `MIN_CITATIONS = 40`）、`run325`（改掉下面那条手抄计数错之后的同口径复跑，九条仍全 rc=0）。第一次尝试 `run321`
  **当场作废并原样留在块 08**：装配命令带了 `tail -12`，
  把复算器输出最上面的 `VERIFY-FRESH-TARGET` 截掉了，还写了一支不存在的 `check_metrics_report.py`
  （只判入口是 `metrics_report.py` 不带 `--write`）。
  收尾复跑还抓到**这张卡片自己**的一次手抄计数错：三处把 `run293` 的 `beyond_eof=3` 抄成了 `1`
  （见 Facts、Out of scope 与 Gaps ③ 的改正）。没有任何仪器会为它响——卡片在历史面上、只报不判，
  而那三处是散文里的数字不是声称行号；它是靠「复跑一遍把读数逐字对回来」这个动作抓出来的，
  也正是本片把复算器与判据常驻化的理由。
  设备/模拟器面未触及（D13 不需要）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除任何既有判据、下限或期望标签；
  `run_all_guards.py` 只加条目（`MIN_ROSTER`/`MIN_CORE` 两个下限一字未动，档位语义不变）。
  两处是**改变判定集合**的口径修正，都附了改前改后的读数而不是「看起来更严」：`CITATION_RE` 前缀表加 `AGENTS`、
  路径字符类放宽（当前面声称 101→110、历史面 204→215，被判死指针数量前后均为 0）。
  一处是**放宽**且写明了为什么不是开后门：字符类放宽只可能让声称**更可见**，不可能让一条已判红的声称变绿——
  判红条件（路径在场性、行数、空行）与字符类无关，`--selftest` 的 CASE 06（抹掉反引号使匹配集为空即判红）
  盯的就是「放宽到看不见东西」这一侧。
- Gaps: ① **生成型条目的权威行号没有任何仪器核对内容**：`check_source_of_truth.py` 的
  「锚点必须在被引那一行开火」只判声明型，本片实测到后果——清单里指向 `check_pointer_liveness.py:58` 的号段在
  同一片内因加行变成 `:59`，形状判据与清单判据都不响，是本片新加的 `VERIFY-FRESH-TARGET` 输出把它摆出来后才人工改正。
  修法要么让生成型也带一条「权威行形状」锚点（与「prose 复述形状」是同一字段的两种用途，属字段语义裁定），
  要么给清单条目增设第七字段；两者都超出本片范围。② 复算器不在 core 档里：搬没搬仍取决于人跑不跑，
  判据只能保证「搬错成死形状时响」，搬成另一种合法但错的内容不响（③ 同因）。③ 历史面残留（`run293`：
  `dead=3 beyond_eof=3 landing_blank=2`）无人处置，且 `.workbuddy/` 是否留在仓库仍无裁定。④ 片 034 记下的那句手抄计数
  「22 例正反双向自检」本片没动——它需要的是一支带裸下限的自检例数漂移脚本，仍在下一片。⑤ `MIN_CITATIONS=40`
  与实测 110 之间余量很大，放宽解析或新增文档都不会触底；反过来把 `SCAN_PREFIXES` 改窄会，这条只能靠注入
  （CASE 05）而不是靠 review 提醒。⑥ 复算器每轮都报 `alias_unresolved=3`——三处**裸文件名声称**，
  即反引号里形如 `` `metrics_report.py:12` `` 的「文件名 + 行号」但没有目录前缀的形状（`count_alias()`
  的口径：无 `/`、含 `:`、前段有点、后段是数字或区间）。`path_line_citations` 的前缀表不认它，
  于是判据不判、复算器也不搬，只报一个数。让它进视野要么放宽解析到无目录前缀的名称（会把 JVM 栈帧
  `FileTestStorage.java:45` 那一类逐字粘贴的读数重新拖进来，片 034 刚为这点付过代价），要么加一条
  「裸文件名声称必须补全路径」的判据；两者都需要新裁定，本片只把它数出来。
- Risks: 判据读的是「当前态面 + 历史面」两套前缀，任何一片新增顶层文档目录而不同时改 `SCAN_PREFIXES`，
  那条目录的声称就不在任何仪器视野里——这是本片把「面」写死两处（判据与 `check_source_of_truth.py`）换来的代价，
  两处的下限会分别响，但只在被抽干时响，不在被绕开时响。D11/D12 那两条与并发有关的假红仍未闭合，
  本片末态两档因此坚持串行单独跑。

## Handoff notes

- Completed: D13 的登记与闭合——常驻形状判据（8 条判据 / 4 个裸下限 / 10 例双向自检）、常驻复算器
  （映射 + 内容核验 + 三类可读拒绝）、core 档两条注册、清单一条 生成型 条目与覆盖面计数、HARNESS 第 8 步接线、
  D12 三处裸文件名声称补全、本片造成的两条 `文件:行` 漂移由复算器搬对、`VERIFY-FRESH-TARGET` 抓到并改正的
  清单号段、解析口径的中文名吞句缺陷、指标报告重生成、证据汇编（8 块 / 37 份捕获）与 RED 对照、
  卡片与证据写出后的九条只读判据复跑（`run324`，全 rc=0）。
- In progress: 无（等独立 verifier 复核后置 `verified`）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 给 Gaps ① 的那处字段语义做一次裁定（生成型要不要也核对权威行）；② 片 034 留下的「22 例」手抄计数
  与它的自检例数漂移脚本；③ Gaps ⑥ 的 `alias_unresolved` 要不要进判据视野（两条路都有代价，需要裁定）；
  ④ 回到 Phase C 的 C4 与 D7①（那条需要用户在场跑真机）。
- Watch-outs: 不要为了让复算器「有东西搬」而去改判据的 `SCAN_PREFIXES`——判据和复算器共用同一个面的 import 关系
  是本片的承重结构；搬完正文后一定要用 `--apply` **复跑一次干跑**看 `MOVED 声称=0`，幂等没复跑等于没搬完。
