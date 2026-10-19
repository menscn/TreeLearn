# Codex Harness Task: 给「跑—失败—修—再跑」的回路本身装一个只读监督者（ROADMAP C2）

id: T-2026-10-20-033
title: loop-watcher 角色、阈值上限与「没判不计入重复」的成对守门
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 214
shared_code_touched: false
depends_on: [T-2026-10-19-guard-runner-one-command-roster-023.md, T-2026-10-19-code-reviewer-role-and-review-template-024.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 要求「完成结论必须匹配证据覆盖」，并把规则、任务、流程资产归 `harness` lane。
  C2 是这条要求在**回路自身**上的落点：一条命令被重跑第二次、第三次时，「在收敛」这件事也要有判据行，
  不能是一句「我再跑一次试试」。
- Problem/background: 本仓库已经为这类失效付过学费——D12 记的 `verify_1to1_selftest timeout after 300s,
  so nothing was judged` 就是「回路把没判读成失败」的现场：拿它当结论只有两种坏走法（重跑到绿、或改宽超时）。
  前六个角色量的全是**产物**（任务卡、代码、评测集、指标），没有一个量「跑—失败—修—再跑」这个循环是否在收敛。
  B3（片 023）已经把守门汇成一条命令，C2 依赖的「可被监督的回路」从此在场；`android/` 的构建入口在本片实跑通过
  （块 07），C2 依赖行的第二个前置条件也不再是推测。
- Users/scenarios: 使用者是重跑同一条命令的主会话。两个场景：① 一片实现里某个注入用例连续两轮不转红、
  同一条守门命令被重跑；② 收尾跑 `--tier core` / `--tier precommit`（D12 的并发场景：旁边有人跑别的脚本就会超时）。
- Expected outcome: 一个只读监督回路本身的角色（`.codex/agents/loop-watcher.md` 113 行 + 同名 `.toml` 6 行，
  正文逐字相同，`toml_chars=4688 md_chars=4688 equal=True`）、一支 24 条判据的成对守门
  （`.codex/scripts/check_loop_watcher_agent.py` 305 行）、35 例 `watch:*` 注入用例、该守门升为
  `checkers_selftest.py` 的第 13 条基线并登记 core 档、`.codex/tasks/_TEMPLATE.md` 的 `### LOOP — optional`
  四个字段，以及 `.codex/HARNESS.md` 第 4 步的一句接线。
- Core flow and reversibility: 新增角色两份 + 守门一份，改四处登记（用例与基线、core 档 1 条 3 行、模板小节 6 行、
  HARNESS 第 4 步行内半句），另收掉本片自己造出的 33 处 `文件:行` 位移。回退方式：删角色 `.md`/`.toml` 与守门，
  撤 `checkers_selftest.py:79`、`:82-84`、`:121`、`:856-978` 的用例块与 `:1160-1162` 的前缀闸门第 6 项，
  撤 `run_all_guards.py:95-97`，还原 `_TEMPLATE.md` 的 LOOP 小节与 `HARNESS.md:10` 那半句，即回到本片之前。
- Inputs/outputs: 输入 = 角色 `.md`/`.toml`、`.codex/agents/code-reviewer.md`（小节集对齐对象）、
  `.codex/agents/verifier.md`、`.codex/agents/code-simplifier.md`、`.codex/tasks/_TEMPLATE.md`（LOOP 小节标题与
  四个字段名从这里现场派生）、`.codex/rules/regression.md`、`.codex/rules/harness-verification.md`、
  `.codex/rules/observability.md`（抄检窗口与正控样本来源）、`.codex/scripts/run_all_guards.py`（回路名册）——
  守门里写的是「the ten inputs exist」，缺任一即早退。输出 = 24 行 `OK/FAIL` +
  `check_loop_watcher_agent: N check(s) failed` + `EXITCODE=0/1`。
- State and exception handling: 四处 fail-closed 早退：十份输入缺任一即停；对齐对象的小节集低于 `floor=6` 即停
  （没有可比对象不等于通过）；模板的 LOOP 小节标题派生不出来即停；`append_sample` 正控派不出来算 `HARNESS`
  （rc=3）而不是「用例通过」。`.toml` 解析失败单独一条 FAIL，不与「正文不相同」混为一条。
- Constraints and trade-offs: 只用标准库（`tomllib`）；不引入「从 .md 生成 .toml」的构建步骤；**不新建归档目录**——
  回路记录只落被分配任务卡的 `### LOOP — optional`，代价是它没有独立可检索面，收益是不给「回路记录」造第二份权威。
  阈值上限 `max=5`：判据接受的是「数值存在且落在可监督范围」，把阈值写成 40 不判为「给了阈值」——代价是将来真要
  放宽到 6 次以上属判据改动，得另开一片。
- AI recommendation and rationale: 验收句只给了三个名词加一条禁令，所以判据全部落在**形状**上而不是措辞：
  阈值数 `连续 N 次` 的行数与取值域（多处出现 + 上限）；「同一失败」要求命令、判据行、读数三个部件同时在场；
  「没判」必须与处置词（不进/不算/先修）同时命中；停止条件数缩进编号行的条数；收敛声明必须是「禁止措辞 ×
  逐字读数」的合取。理由与片 027 相同：只数名词的判据可以被一句空话满足，也就无法被单个注入证伪。
- User choice and custom additions: 用户要求一站到底、按片本地提交、不中途请示、不推远端；本片未收到额外定制要求。
- AI-autonomous decisions: ① 阈值定为「同一失败身份连续 2 次」——C2 目标句写的是「超过阈值就停下来」，
  取 1 会把单次抖动当停止，取 3 以上与「无限重试烧时间」的距离太大；② 对齐对象选 `code-reviewer.md`（实测 9 个
  小节名）而不是 `verifier.md`，沿用它不会降低要求；③ 落点小节标题与四个字段名从 `_TEMPLATE.md` 现场派生，
  守门里不重抄；④ 「没判」的形状判据直接钉住 `nothing was judged` 与「超时」两个真实字符串（D12 的原文），
  而不是写一句「要区分未判定与失败」；⑤ 注册进 core 档（只读、秒级）而不是 precommit 档。
- Acceptance evidence: 汇编在 `artifacts/evidence/T-2026-10-20-loop-watcher-role-033.txt`（块 00–08），
  下面的号段就是它的来源。块 00 角色守门 24 条逐字（`run222-watch-checker.txt`）、块 01 红/绿
  （`run223-redgreen.txt`）、块 02 注入电池（`run210-watch-cases.txt` 单族 35 例、末态全轮
  `run257-selftest-full.txt` 244 例 / 13 基线 `OVERALL: PASS`；此前 `run229` 是修 needle 前、`run239` 是修
  needle 后、`run242` 与 `run250` 是改引用号段后的同口径读数，`run238-metrics-cases.txt` 是被修的那一族单独重验）、
  块 03 档位轮（末态 `run255-tiers-core.txt` `pass=33`、`run256-tiers-precommit.txt` `pass=6`；
  中途 `run236` 的 `inconclusive=1` 是同一段回路的现场）、块 04 指针复算（`run217-pointer-fix.txt`、
  `run219-pointer-docs-dry.txt`、`run254-fastgates.txt` 末态 `citations=60` 无 `SHORT`；
  `run226-cite-scan.txt` 的 53 处是本片卡片写出之前的计数）、
  块 05 指标面（`run227-selftest-full.txt` 的第一次 INCONCLUSIVE、`run228-metrics-refresh.txt`、
  `run235-post-card.txt`、`run249-fastgates.txt` 的第二次过期）、块 06 重复短语扫描（`run224-dupscan.txt`、
  `run254-fastgates.txt` 末态 `cjk_prose_hits=0`、正控 `fired=True`）、块 07 前置条件读数（`run221-gradle-rerun.txt`、
  `run248-gradle-xml.txt` 的新写 XML 合计）、块 08 产量口径（`run234-sizing.txt`，`PRODUCTION_LINES total=214`）。
- Facts: ROADMAP C2 依赖行的两个前置条件都已被实测满足（块 07）；core 档位现为 33 条、名册 39 条、
  基线 13 条（`run228-metrics-refresh.txt` 里 `--write` 前后的漂移读数）。
- Assumptions: 无。本片不依赖任何未实测的外部事实。
- Unknowns: `loop-watcher` 的阈值 2 在真实多轮重跑里会不会太敏感——需要下一片用出第一条真实 LOOP 记录才能判。
- Risks: 指针复算驱动不是 core 守门（D9 登记的缺口），本片只能靠现场跑驱动收口；下次给 `checkers_selftest.py`
  加行的片子必须重复这一步，否则文档面会留下「读起来和真话一样的活指针」。
- Deferred: 把 difflib 映射上收进 `.codex/scripts/harness_check_lib.py`（本片两份临时驱动各留一份，见 REFACTOR）；
  `b028-pointers.py` / `b031-pointers.py` 两张冻结表退役。
- Non-goals: 不做自动重试器、不引入任何跑构建/装设备的回路编排、不给回路记录造独立归档目录。
- Skipped non-critical questions: 上游 `executing-plans` / `gh-fix-ci` 的「谁负责重试」没有照搬——本仓库的
  lane 里只有单人会话，重试责任就是主会话，写进角色只会变成一句没有判据的分工宣言。

## Goal

`python -X utf8 .codex/scripts/check_loop_watcher_agent.py` 退出码 0，且它判的是：角色给出可解析且在可监督范围内的
重复阈值、定义了「同一失败」的身份、把「没判」排除在重复计数之外、列出 ≥5 条停止条件、给 `### LOOP — optional`
四个字段逐字命名、并把「无证据宣布收敛」判红。

## Scope

### In scope
- `.codex/agents/loop-watcher.md` + 逐字相同的 `loop-watcher.toml`。
- `.codex/scripts/check_loop_watcher_agent.py`（24 条判据，纯标准库，十份输入）。
- `.codex/scripts/dev/checkers_selftest.py`：35 例 `watch:*`、第 13 条基线、第 6 份抄检来源与前缀闸门项。
- `.codex/scripts/run_all_guards.py`：core 档 1 条 3 行。
- `.codex/tasks/_TEMPLATE.md`：`### LOOP — optional` 小节四个字段（6 行）。
- `.codex/HARNESS.md` 第 4 步行内接线半句（不加行，保住 `HARNESS.md:10/11/13` 三处活引用）。
- `.codex/ROADMAP.md` C2 的 `范围` 扩写与 `交付任务` / `实现要点` 回写。
- 本片造出的 33 处 `文件:行` 位移的机械复算，含与 `evals:pointer-stale` 耦合的评测快照那行。

### Out of scope
- 不动 `android/**`、`frontend/**` 的生产代码；不改任何既有判据的下限或超时。
- 不做回路编排、不新增角色归档目录、不把指针复算驱动升为常驻守门（本片登记为缺口，不悄悄做）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增只读资产，无生产行为变化。要保全的是既有事实：
  `HARNESS.md` 第 4 步仍是一行（行内追加，`.codex/HARNESS.md:10` 被三张卡片引用，加行会让它们集体变谎）；
  `_TEMPLATE.md` 的既有小节与字段一字不动，只在 `### REFACTOR — optional` 之后插入 6 行。
- Producer/consumer/sibling entrypoints and platform branches (code evidence):
  读 `_TEMPLATE.md` 的守门有 `check_intent_template.py` 与本片新守门（后者只取 LOOP 小节，实测
  `title='LOOP — optional'`、`fields=['Command and rounds', 'Failure identity and repeat count', 'Stop condition hit', 'Handoff']`）；
  读名册的有 `check_loop_watcher_agent.py`（ROSTER，回路名在场）与 `metrics_report.py`（按档位计数，
  实测漂移 `entries=38;core=32 → entries=39;core=33`、`baseline-checkers 12 → 13`）；
  `checkers_selftest.py` 的用例总数进 `metrics_report.py` 的家族计数（`watch=35` 是新增的一族）。
  证据：`artifacts/tmp/run227-selftest-full.txt` 的 `BASELINE FAIL metrics_report` 差量三元组与
  `run228-metrics-refresh.txt` 的重生成后 `0 check(s) failed`。
- Native bridge or independent-client compatibility/update requirements: 不适用——未触及 API、SQLite、原生桥接与
  跨端契约面。
- Behavior-neutral non-applicability reason, if applicable: 同上；本片改动全在 `.codex/**`、`Docs/spec` 的一处行内
  行号与 `artifacts/metrics/report.md` 的重生成。

## Documentation impact record

- Changed runtime facts: 新增一个只读角色与其判据面；任务卡模板多一个可选小节；core 档多一条守门；指标报告数字刷新。
- Authoritative code/config/migration evidence: `.codex/agents/loop-watcher.md`（权威正文）+ 同名 `.toml`（运行时包装，
  逐字相同由判据 `toml_chars=4688 md_chars=4688 equal=True` 钉住）；`.codex/scripts/check_loop_watcher_agent.py`；
  `.codex/scripts/run_all_guards.py:95-97`；`.codex/tasks/_TEMPLATE.md:100-105`。
- Documentation surfaces checked: `AGENTS.md`（路由表未新增条目：角色由 `HARNESS.md` 第 4 步按名加载，不是新触发条件）、
  `.codex/HARNESS.md`、`.codex/README.md`、`.codex/rules/observability.md` 与 `regression.md`（抄检窗口，
  新角色不得复述其正文——实测 `measured_hits=0`）、`Docs/reviews/**`、`.codex/evals/cases/**/product.md`（快照里的
  活指针随本片一起搬）。
- Documentation files updated: `.codex/HARNESS.md:10`（行内半句）、`.codex/ROADMAP.md` C2 条目（`范围` + 两条回写）、
  `.codex/tasks/_TEMPLATE.md`、`artifacts/metrics/report.md`（`--write` 重生成，5 行）。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact；另用
  `artifacts/tmp/b033-cite-scan.py` 扫全部指向两个被改脚本的文档引用，`citations=53 SHORT=0`。
- Superseded phrases/examples reverse-searched: 反向搜索 `checkers_selftest.py:78-80`、`run_all_guards.py:95-96`
  等旧区间串，命中处即本片 33 个 FIX（`run217-pointer-fix.txt`）；`grep -rn "78-80" .codex/` 现仅剩
  `checkers_selftest.py` 里那条 `evals:pointer-stale` 用例的新串 `82-84`。
- `documentation impact: none` reason and evidence, if applicable: 不适用，本片确实更新了四个文档面。
- Historical/PRD handling: `.codex/references/**` 与历史卡片只改行号不改叙述；`b028-pointers.py`（`stale=20`）与
  `b031-pointers.py`（`stale=41`）两张冻结表按「片内现场证据」处理，不回填（见 Handoff 的 watch-outs）。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/check_loop_watcher_agent.py` 退出码 0，24 行判据逐行打印
  （块 01 GREEN-0/GREEN-1 与 `run222-watch-checker.txt`）。
- [x] 判据真的给出阈值、停止条件与上报格式，且禁止在无证据时宣布收敛：`run223-redgreen.txt` 的四个 RED 状态
  ——角色两份缺席（`missing=['.codex/agents/loop-watcher.md', '.codex/agents/loop-watcher.toml']`）、
  阈值改「连续若干次」（`lines=0 floor=2 values=[]`）、抽干 `没判`（`measured=0 floor=2`，同时
  `stop conditions measured=4 floor=5`）、模板小节标题被改名（`landing_lines=1 all_named=False`）——
  每个都 `rc=1`，还原后同一条命令 `rc=0 OK=24`。
- [x] 回路记录只落当前任务卡：判据 `landing_lines=1 all_named=True`，标题从 `_TEMPLATE.md` 现场派生（`title='LOOP — optional'`）。
- [x] 注入电池会响且不误伤：35 例 `watch:*` 全部命中期望标签（`run210`：`cases=35 ok=35`）；
  全轮 244 例 + 13 基线 `OVERALL: PASS`（末态：`run257`）。
- [x] 档位轮绿：`--tier core pass=33 fail=0 inconclusive=0 fault=0`（`run255`）、
  `--tier precommit pass=6 fail=0 inconclusive=0 fault=0`（`run256`），两轮串行单独跑（D12）。
- [x] 本片没把别的判据改宽：`git diff` 里没有任何 `MIN_*` 下限被调低，也没有任何 `timeout=` 被加大；
  被改的三条 needle 是**抬高**或换形（`MIN_ROLE_SECTIONS` 由对齐对象实测 9 个小节驱动）。
- [x] 前置条件有读数：`android/` 构建入口实跑 `GRADLE_RC=0`，本轮新写 XML 合计
  `AGGREGATE suites=114 tests=1004 failures=0 errors=0 skipped=0`（`run248-gradle-xml.txt`，块 07）。

## TDD execution record

### RED — failing test first
- Test/scenario: 判据写完、角色两份还没落地——即「C2 只有守门、没有可被它检的角色」这一状态。跑守门，看它把
  「被检对象缺席」报成红，还是空集通过。整段在临时副本里做，真实工作树不动。
- Command: `python -X utf8 .codex/scripts/check_loop_watcher_agent.py`
- Exit code: 1
- Observed failure: `FAIL the ten inputs exist — missing=['.codex/agents/loop-watcher.md',
  '.codex/agents/loop-watcher.toml']` → `check_loop_watcher_agent: 1 check(s) failed` / `rc=1`
  （四个 RED 状态与还原后的 GREEN 由 `python -X utf8 artifacts/tmp/b033-redgreen.py` 在**临时副本**里逐状态
  调用上面同一条命令得到，真实工作树不动；`run223-redgreen.txt` 逐字；另有 RED-2 `lines=0 floor=2 values=[] sane=[] max=5`、
  RED-3 `measured=0 floor=2` 加 `measured=4 floor=5`、RED-4 `landing_lines=1 all_named=False`）。
- Failure reason validated: 红来自被检产物缺席/被抽干，不是命令跑不起来——同一条命令在还原后立刻
  `0 check(s) failed` / `rc=0` / `OK=24`，且四个文件还原后 sha256-12 与仓库逐字节相同
  （`3bb6f95df68e / 4e817dd52b60 / 1fdd772b1813 / 4b395fbbdafa`，`identical=True` 四行）。
  RED-4 还确认了一件事：改标题时「模板小节在场」那条判据**不**转红（标题是派生的，不是冻结串），
  转红的是「角色必须点名派生标题」——所以驱动注释按实测写了这一句，没写成「早退」。
  另有两次现场缺陷（不是判据红，是仪器坏）：`replace_line` 的 needle 前缀取错导致 IndexError；
  五条候选注入（单行 notjudged / privacy / identity 编辑）在探测里读数仍 ≥ 下限、根本不会转红，
  于是换成由 `c033-probe*.py` 实测过的 needle（`失败身份`→`同类失败`、drop `prompt`、`只读`→`可写`）。
  这两条只留在会话输出里、没落盘，所以此处不写它们的条数，只写它们促成的最终 needle。

### GREEN — minimum implementation
- Implementation: `.codex/agents/loop-watcher.md`（113 行：`<identity>` / `<source>` / `<contract>` / `<rules>` /
  `## 触发时机` / `## 职责清单` / `## 输出格式` / `## 交接` / `## 禁区`，9 个小节名覆盖对齐对象全集）+
  逐字相同 `.toml`（6 行）；`check_loop_watcher_agent.py`（305 行、24 条判据、10 份输入）；
  `checkers_selftest.py` 的 `CHECK_WATCH` `:79`、第 13 条基线 `:82-84`、第 6 份抄检来源 `:121`、
  35 例 `watch:*` `:856-978`、`copy_check_prefixes` 第 6 项 `:1160-1162`；`run_all_guards.py:95-97`；
  `_TEMPLATE.md:100-105`；`HARNESS.md:10` 行内半句；ROADMAP C2 回写。
- Command: `python -X utf8 .codex/scripts/check_loop_watcher_agent.py`
- Exit code: 0
- Passing evidence: `run222-watch-checker.txt` 的 24 行逐字，承重读数：`lines=2 floor=2 values=[2] sane=[2] max=5`、
  `measured=5 floor=2`（失败身份）、`keeps 没判 … measured=3 floor=2`、`stop conditions measured=5 floor=5`、
  `title='LOOP — optional'`、`fields=4 missing=[]`、`measured=5 floor=2`（收敛绑定读数）、
  `forbids declaring convergence … measured=2 floor=1`、`round_lines=4 floor=2 exitcode_hits=4`、
  `landing_lines=1 all_named=True`、`verifier_refs=4 reviewer_refs=3 simplifier_refs=3 floor=2`、
  `measured=7 floor=6 cited=[…]`、`dead=[]`、`measured_hits=0 sample=[]`、`hits=[]`、
  `toml_chars=4688 md_chars=4688 equal=True`、`check_loop_watcher_agent: 0 check(s) failed`。
- Regression evidence: 块 02（`run210` 35 例单独一轮 + 末态 `run257` 全轮 244 例 / 13 基线 `OVERALL: PASS`、
  `harness_errors=0`、`skipped=0`；修 needle 前的 `run229` 与修完后的 `run239` 是同一条命令的两轮，都绿）；
  块 03（末态 core `pass=33`（`run255`）、precommit `pass=6`（`run256`），两轮之间不并发；
  唯一会读卡片正文的守门 `validate_tasks` 就在这 33 条里，块 00 的证据装配又当场重跑了一次）；
  块 04（指针复算：`EDITS applied=33`、文档面 `声称条目数=10 站点数=23 MOVED=0`、
  末态 `citations=60 SHORT=0`（`run254`；本片卡片自己新增的 7 处引用也在同一口径下复算过，
  此前 `run226` 的 53 处是卡片写出前的计数））；
  块 06（重复短语扫描 `cjk_prose_hits=0`、正控 `fired=True`）；块 05（指标报告重生成后 `0 check(s) failed`）。

### REFACTOR — optional
- Cleanup: ① 抄检取样沿用共享的 `derive_sample()`，只新增第 6 份来源（`SAMPLE_SPEC_OBSERVABILITY`，`:121`）
  与前缀闸门第 6 项，没有为 observability 再造一个取样器；② 小节名、可引用路径、占位符、重叠窗口全部走
  `harness_check_lib`（`agent_section_names` / `citable_paths` / `placeholder_hits` / `overlap_hits` / `normalize` /
  `mask_paths`），守门里没有第二份实现——这是片 027 把 `cited_paths()` 上收之后自然拿到的东西。
  ③ **本片自己留下了两处第二份实现，登记不自消**：`b033-pointer-fix.py` 与 `b033-pointer-fix2.py` 各带一份
  difflib 映射（`mapping()` / `same_stmt()` / `subseq()` / `blank_pad()`）。两份都是 gitignore 面的临时驱动，
  不上 core 档；上收目标是 `.codex/scripts/harness_check_lib.py`，触发条件按 C2 角色的判据面规则——
  「同一条命令改前改后都全绿」得有真测试可跑，而指针驱动本身不是守门（这正是 D9 登记的缺口）。
- Regression evidence: ①② 由块 02 的 `watch:rules-restated`（CASE 31，正控从 observability 现派）与
  `measured_hits=0` 覆盖；③ 由 `git diff --stat` 证明本次只多了两份 `artifacts/tmp/` 临时件、
  `.codex/scripts/` 侧没有新增同名函数（块 03 的 core 轮里 `check_code_simplifier_agent` 仍绿）。

### LOOP — optional
- Command and rounds: 两段回路，都用同一条命令反复跑。
  ① `python -X utf8 artifacts/tmp/b033-pointer-fix.py`（干跑）4 轮：`run213`（`:92-94 → :92-97` 撑宽）→
  `run214`（`REVIEW needed=3`，`ANCHOR-AMBIGUOUS starts=[92] ends=[86, 88, 91]`）→ `run215`（`NO-CANDIDATE` 4 条 /
  `REVIEW needed=4`）→ `run216`（候选全部对上，`SAME/anchor+span`）→ `run217`（`--apply`，`EDITS applied=33`、
  `VERDICT: PASS`）。文档面第二支 `b033-pointer-fix2.py` 单独一轮 `run219`：`声称条目数=10 站点数=23 MOVED=0 SAME=10`。
  ② `python -X utf8 .codex/scripts/run_all_guards.py --tier core` 两段：`run230` `pass=33 fail=0`（卡片尚未写）→
  `run236` `pass=32 fail=0 inconclusive=1`（红在 `checkers_selftest` rc=3）→ `run237` 直跑 selftest 复现
  `CASE 173 HARNESS anchor not found` → `run238` 用只跑 `metrics:*` 的驱动验新串 `ok=19/19` → `run239` 全轮
  `cases=244 ok=244 OVERALL: PASS` → 单独重跑档位：`run240-tiers-core.txt` `pass=33 fail=0 inconclusive=0 fault=0`、
  `run241-tiers-precommit.txt` `pass=6 fail=0`（两轮串行各跑一次，D12）。卡片引用号段继续改动后又收了
  `run242`（全轮 244 例 / 13 基线 PASS）与 `run243`/`run244`、`run246`/`run247`（两档，读数一致）。
  写 Verdict 那一行本身又让指标面响了一次：`pending-verdict-cards` 由 9 变 8，`run249-fastgates.txt` 打
  `FAIL each machine-recheckable reading is produced by running the command on its own line — checked=9 mismatch=[['pending-verdict-cards', '9', '8']]`。
  它的判据行与 `run227` 那次（`missing_from_report`）不同，所以按角色规则算**两个失败身份各 1 次**，
  不构成连续 2 次；处置同样只有一条——`metrics_report.py --write`（`WROTE artifacts/metrics/report.md lines=20 metrics=12`），
  判据一字未动。`run250`/`run251`/`run252` 那一组（全轮 + 两档）随后被 ROADMAP 的措辞修正作废——那次修正起因是
  新写进 ROADMAP 的一句里带了 `PENDING`，被 `check_roadmap_and_routing` 的占位符判据抓住（`run253`：
  `FAIL no placeholder tokens in ROADMAP — measured=['PENDING']`，身份第一次出现，未达连续 2 次）。
  末态三条因此是：`run255` core `pass=33 fail=0 inconclusive=0 fault=0` → `run256` precommit
  `pass=6 fail=0 inconclusive=0 fault=0` → `run257` 全轮 `cases=244 ok=244` / 13 基线 `OVERALL: PASS`，
  仍按串行单独跑。
- Failure identity and repeat count: 身份 = 「命令 + 判据行 + 读数」。①的三轮判据行分别是撑宽、锚点歧义、无候选，
  同一条命令下**不是同一失败身份**，各计 1 次，未达连续 2 次。②里 `inconclusive=1`（rc=3）本身按角色的处置规则
  **不进重复计数**——它打印的是「本次无从认证」而不是「产物变差」；进计数的是 `CASE 173 HARNESS anchor not found`
  这条判据行，它在 `run236`（core 档里）与 `run237`（直跑 selftest）两次读数里逐字相同，
  判它「算同一身份」的两条读数就是这两行，连续次数=2。本片另两次指标过期各是一种身份、各只出现一次
  （`run227` 的 `missing_from_report` 与 `run249` 的 `pending-verdict-cards` 9→8），按同一条规则都不进连续计数，
  也就都不构成停止条件——它们是「产物没重生成」，不是「回路不收敛」。
- Stop condition hit: ①未命中停止条件——每轮病因不同（候选顺序、`same_stmt` 前缀容差、`blank_pad` 各修一次），
  且 `--apply` 前有 REVIEW 就挡住不写，这正是「不许靠改判据让回路收敛」的同族走法。②命中**第 1 条**（同一失败身份
  连续 2 次），所以停下上报 = 本小节；随后按第 5 条的方向修仪器而非重跑碰运气，并在此声明**没有命中第 3 条**：
  改的只有注入串（`metrics:*` 族两条 needle），`metrics_report.py` 的判据、下限、期望标签一字未动
  （`run238` 的 19 例与 `run239` 的 244 例都仍以期望标签转红）。第 4 条未命中：本片不需要设备或整包仪器跑。
  另记一次真实的 rc=3：`run227` 全轮 `BASELINE FAIL metrics_report rc=1 → OVERALL: INCONCLUSIVE`（报告过期未重生成），
  它按角色规则同样**不进重复计数**，处置是 `metrics_report.py --write` 重生成（`run228`）而不是改判据。
- Handoff: 交给下一片（C3）的是三条会漂的东西：① 给 `checkers_selftest.py` 加行会同时挪动 33 处文档引用与
  `metrics_report.py` 的三档计数——每次都要跑 `b033-pointer-fix.py`（脚本面）与 `b033-pointer-fix2.py`（文档面）
  加 `metrics_report.py --write`，不能凭印象改；② 注入 needle 里**不许抄会被生成物改动的值**——本片两次撞上这件事
  （`读数=8`、`total=19`），要抄就抄指针串或改成追加合成行；③ `run_all_guards` 的整跑只在收尾单独跑。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用。
- Reason: 不适用。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 行数口径写清以免互相冒充：卡片里的文件行数取 `wc -l`（= 换行符数，角色 `.md` 113、守门 305），
  `artifacts/tmp/b033-sizing.py` 打印的 `present_lines` 取 `split("\n")` 口径，恒比前者多 1。
  `estimated_production_lines=214` 同样出自 `b033-sizing.py` 的一条真实打印
  （`PRODUCTION_LINES total=214` = 守门 `added_code=211` + core 档注册 `added_code=3`），
  口径沿用片 029/030/031 的同一份 `added_lines()` / `docstring_spans()`，本片没有另造计量器；
  超 200 行软上限只出 WARNING，不出 ERROR，也不需要尺寸豁免。
- 判据形状与用例的一一对应关系写在守门脚本的注释里，不抄进卡片：本片沿用「条数与用例数只从
  `checkers_selftest.py` 现场取」的规矩，卡片里只写**实测值**（24 条判据、35 例、13 基线、244 例全轮），
  这些数字在下一片加行时必然过期，出处是同一条命令的输出而不是本文。
- 阈值取 2 的理由不是「经验值」：C2 目标句要求「超过阈值就停下来」，而本仓库真实重跑回路的抖动是
  一次性的（块：LOOP 记录里三轮各是一种病因），1 次会把抖动当收敛失败，>2 则与「无限重试烧时间」距离过大。
- 「没判不进重复计数」这条是本片唯一直接从欠账条目（D12）搬进角色正文的规则：它把
  `nothing was judged` 与「超时」当作**形状**去数，而不是当作一句话去信——所以抽干任一形状都会红
  （CASE 12/13），而把处置词从「不进」换成「算进」也同样红（判据要合取，不要名词）。
- 角色不写 Verdict、不宣布任务验收、不改判据面、不粘贴 prompt/响应体：四条禁区分别由
  `verifier_refs/reviewer_refs/simplifier_refs` 下限、`readonly measured=2 floor=1`、`stop-item-*-demoted` 两例、
  `privacy-drained` 一例钉住；这些都只是「动作还在场」的代理判据，真实约束力来自它每次重跑都要贴判据行。

## Verification report

- Verdict: PASS（角色守门末态 24 条判据 + 同一条命令的四个 RED 状态与字节还原 + 35 例 `watch:*` 注入与
  244 例整轮 13 基线 + core/precommit 两档整跑（`run255` `pass=33`、`run256` `pass=6`，串行单独跑）+
  本片造成的 33 处指针位移复算与文档面 `MOVED=0` + 全库 `文件:行` 扫描 `citations=60` 无越界 +
  重复短语扫描（正控在场）+ 依赖句从推测变成读数（`GRADLE_RC=0` 与本轮新写 XML 合计）；
  未覆盖的部分写在 Gaps，不算已覆盖）
- Evidence: `artifacts/evidence/T-2026-10-20-loop-watcher-role-033.txt`（driver=`artifacts/tmp/b033-evidence.py`，
  只做拼接与现场补跑：块 00 角色守门 24 行逐字与一次当场重跑、块 01 的 `RED-1`…`RED-4` 与 `GREEN-END rc=0 OK=24`
  及四份 sha12 字节还原、块 02 的单族 35 例 + 末态整轮 + 13 条基线逐字 + 被修那一族的 19 例、块 03 的两档整跑
  与 `validate_tasks`/`check_roadmap_and_routing` 当场重跑、块 04 的 `EDITS applied=33` 与 REVIEW 挡住不写的
  两轮原样读数与全库扫描两次计数、块 05 的 INCONCLUSIVE→`--write`→只判、块 06 的重复短语两次、
  块 07 的 Gradle 两轮与只算本轮新写 XML 的合计、块 08 的尺寸与 `git status`/`git diff --stat`）。
  验收场景 = ROADMAP C2 的 `验收方式` 一句，逐条落在块 00–08。原始捕获在 gitignore 的
  `artifacts/tmp/run2*.txt`（号段 run202–run258）可重跑。末态三条之后卡片仍改过引用号段与措辞，
  那一段没有再整跑，只单独重跑唯一会读卡片正文的 `validate_tasks`（`run258-postcard.txt`：
  `SUMMARY files=28 errors=0 warnings=6`，rc=0）——拿旧整跑充数而不补这一步，正是本片角色要拦的动作。
  设备/模拟器面未触及（C2 不需要）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除任何既有断言。三处 needle 被改写：
  `evals:pointer-stale` 的引用串 `checkers_selftest.py:78-80` → `:82-84`（它与评测快照
  `product.md:5` 是同一个事实的两个副本，必须一起走；快照那行由 `run217` 的 33 个 FIX 之一搬动，
  `run_evals` 的 `pointers-alive` 只查路径在场、查不出行号，所以这一对只能由驱动保证）。
  另两条是**本片把仪器逼出来后顺手修的过期锚点**（都在 `metrics:*` 族，不动任何判据，只换注入串）：
  `metrics:placeholder-in-reading` 的注入串是手抄的某个读数 `读数=8 | 复算=值`，报告一重生成该串就不在场了，
  全轮因此以 `CASE 173 HARNESS anchor not found` 收在 `OVERALL: INCONCLUSIVE` / rc=3
  （`run236` 的 core 档 `inconclusive=1` → `run237` 直接跑 selftest 复现同一读数）；改成指针串
  `--read <指标名>` → `--read 待填` 后由 `run238` 的 CASE 18 验到 `hits=['待填']`。
  同族的 `metrics:hand-copied-reading` 原本抄的是 `total=19`——那是案例库行数，下一次加行就会以同样方式炸掉整轮，
  所以一并换成「追加一条同名 synthetic 读数行」的形状（`run238` CASE 16：`checked=10 mismatch=[['library-entries',
  '…total=20', '…total=19']]`）。这两条改的是**注入串**而不是判据：判据的下限、口径、期望标签一字未动。
- Gaps: ① 指针复算驱动不是常驻守门（D9 缺口），文档行号腐烂仍靠每片手动跑；② `loop-watcher` 自己还没有一条
  真实的多轮重跑记录来验证阈值 2 是否过敏感；③ `b028-pointers.py` / `b031-pointers.py` 两张冻结表已与实际
  行号脱节（`stale=20` / `stale=41`），本片只登记退役，不回填；④ 证据装配驱动（`b033-evidence.py`）与本轮的
  XML 合计驱动（`b033-xmlcount.py`）都留在 gitignore 的 `artifacts/tmp/`，仓库内不常驻——证据文件里点名了它们，
  但下一片要复算得自己重写，这与片 031 的处置相同，也是 D9 那条「指针/装配类驱动不是常驻守门」缺口的又一例。
- Risks: 下一片若给 `checkers_selftest.py` 加行而不跑指针驱动，就会留下一批活指针；这是本仓库已登记过的缺陷类，
  不是本片新增的风险。

## Handoff notes

- Completed: C2 的角色、守门、35 例注入、core 档注册、模板 LOOP 小节、HARNESS 接线、ROADMAP 回写、
  本片造成的 33 处指针位移复算、指标报告重生成、证据汇编 `artifacts/evidence/T-2026-10-20-loop-watcher-role-033.txt`
  与 C2 依赖句的 Gradle/XML 读数。
- In progress: 无（等独立 verifier 复核后置 `verified`）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 下一片按 ROADMAP 顺序取 C3（事实源全量声明）。开工先跑一次
  `python -X utf8 .codex/scripts/run_all_guards.py --tier core` 建立基线；若那片也给 `checkers_selftest.py` 加行，
  就顺手把两份临时驱动的 difflib 映射上收进 `harness_check_lib.py`（REFACTOR ③ 登记的欠账）。
- Watch-outs: ① 收尾两轮必须串行单独跑（D12），超时是「没判」，不是产物变差；② 给 HARNESS 加步骤请写进行内，
  加一行会同时挪 `HARNESS.md:10/11/13` 三处活引用；③ `metrics_report.py` 的默认只读判定会在基线里先红给你看——
  `run227` 就是这么抓到报告过期的，重生成用 `--write`，不要改判据；④ 两份冻结指针表（b028/b031）的读数只算
  各自片内的现场证据，别再拿它们当全库指针健康度。
