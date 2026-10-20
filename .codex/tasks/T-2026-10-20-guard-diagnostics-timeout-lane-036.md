# Filename: T-2026-10-20-guard-diagnostics-timeout-lane-036.md
# Codex Harness Task: 整跑读数自己说清哪条红、哪一例红；超时不再借判红的词（ROADMAP D11+D12）

id: T-2026-10-20-036
title: 守门汇总面的诊断与超时档——DIAG、TIMEOUT 独立档、MAX_TIMEOUT 上限、external 档与自检自身入册
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 207
shared_code_touched: false
depends_on: [T-2026-10-19-guard-runner-one-command-roster-023.md, T-2026-10-20-loop-watcher-role-033.md, T-2026-10-20-source-of-truth-inventory-034.md, T-2026-10-20-pointer-liveness-standing-guard-035.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary：`T-2026-10-18-treelearn-process-migration-001` 确认 `.codex/` 是 Treelearn 唯一 AI 研发流程入口，
并按 ROADMAP 逐片补齐流程资产。本片吃的是 ROADMAP 里**仅剩的两条未闭合且不需要的用户在场**的欠账 D11 与 D12；
D13 已由片 035 闭合，C4/D7① 需要实机与用户在场，仍挂着。

- Problem/background: 两条都关于「汇总面说的话与事实不符」。D11：`--tier core` 报 `FAIL cdp_shot_selftest` 时
  只打印被 needle 命中的那一行，子进程逐用例输出被丢弃，「哪一例红」在整跑读数里没有答案；登记时把它猜成墙钟时序。
  D12：最重的 `verify_1to1_selftest` 贴着 `DEFAULT_TIMEOUT = 300`，与任何命令并发即被掐断，而那次打印的是
  `nothing was judged` 却算进 `fail=1` ——「没判」借了「判红」那个词。
- Users/scenarios: 下一位读到一份红整跑读数的实现者（他要能立刻知道红在哪、为什么），以及改
  `run_all_guards.py` 判定分支的人（他需要一支常驻仪器替他证明分支没坏）。
- Expected outcome: 三条读数形状变成仪器事实：非 PASS 附带原始输出（`DIAG`）、超时自成一档走退出码 3、
  `timeout=` 有上限且自检自己注册在 core 档里。
- Core flow and reversibility: 只改汇总器与它的注入电池，不动任何一条被汇总的守门的判据；
  档位语义新增 `external`（把 `cdp_shot_selftest` 移出 core），不改判据内容只改它在哪个判定面里。全部可逐文件回退。
- Inputs/outputs: 输入是注册表（`--list` 现场打印条数）与每条守门的真实输出；输出是
  `RUN-ALL-GUARDS pass=/fail=/inconclusive=/timeout=` 汇总行、`DIAG` 段、以及电池的用例读数。
- State and exception handling: 超时后先 `kill` 再带 deadline 地排空管道；排不空就把「排不空」写进读数，
  不用空串冒充「它一个字都没打」。
- Constraints and trade-offs: 不把「并发就红」用放宽上限的方式消音（D4 那族仪器给自己开后门）；
  为此新增 `MAX_TIMEOUT` 让「把上限写成 24 小时」这条路本身就是注册表故障。
- AI recommendation and rationale: ①DIAG、②TIMEOUT 独立档、③自检入册、④`external` 档，四条同族且都是
  「读数必须自己说清」这一条纪律的落点，合一片做；不顺手改 Web 仪器本体。
- User choice and custom additions: 用户不在场（沿用「一站到底 + 常规技术决策直接执行」的授权）；
  本片没有需要用户独有的事实。
- AI-autonomous decisions: 把 D11 的 修法③ 实现成一个新档位而不是塞进 `precommit`（`precommit` 的定义是
  「依赖未提交增量/构建产物/Gradle」，网络依赖混进去会让那档的含义第二次变质）；`subset=` 的含义改成
  「整表是否都被判过」；给 `verify_1to1_selftest` 定 `timeout=600`（依据见 ROADMAP D12 处置 ③ 的两次对照）；
  把两条 needles 补上「红路径也在」的第二形状；把三面网络探针从 `artifacts/tmp/` 的一次性驱动上收成
  `.codex/scripts/dev/net_probe.py`（D13 立的规矩：会被下一片再写一遍的东西不该留在 gitignore 里）。
- User-confirmation decisions: 无。需要裁定的一件事另登记为 D14（原型走 CDN，收编进仓库还是钉 `direct://`），
  本片不动 Web 本体。**本片收尾时新增第二个待裁定**：`verify_1to1_selftest` 与 `cdp_shot_selftest` 共用同一条
  外网依赖，是否把它也移出 core——那是验收面变更，按纪律不由本片自己决定，见 D14 范围栏。
- Acceptance evidence: 见 Acceptance criteria 每条后的命令与 `artifacts/evidence/T-2026-10-20-guard-diagnostics-timeout-lane-036.txt`。
- Facts: D11 那次「间歇红」的真实病因是外网可达性，不是墙钟——证据链在 ROADMAP D11 处置 ② 与证据块 04/05；
  D12 登记时那句「与任何命令并发即假红」也被同一批读数推翻，串行与并发各跑一轮同值（证据块 04）。
- Assumptions: 无承重假设。（原假设「时序敏感」已被本轮读数推翻并写明。）
- Unknowns: 两次读数一次 `failures=1`、一次 `failures=2` 的差异未解释；不影响结论，因为两例的名字与原因行现在都会进读数。
- Risks: 判据行串与守门措辞耦合（措辞一改汇总器就判「没打印判据」，会响不静默）；`external` 档现在只有一条，
  档位这套机制的复杂度换的是「core 不再被环境抖动污染」这一件事。
- Deferred: 见 ROADMAP D14（CDN 依赖，需裁定）、片 035 Gaps ①④⑥。
- Non-goals: 不改 `cdp_shot.mjs`/`parity_cdp.mjs`/`verify_1to1*.mjs` 任何一行；不抬任何既有下限；
  不动 C4/release-gate；不把 `.workbuddy/` 的归属问题卷进来。
- Skipped non-critical questions and reason: 未追问「是否顺手把 `web_walk` 正文也纳入」——它属 C4 走查，
  需要实机与本机服务，纳入只会把刚拆出来的环境依赖又请回来。

## Goal

`python -X utf8 .codex/scripts/run_all_guards.py` 的一份读数能自己回答三件事：哪条守门红、红在那一例（为什么），
以及「被自己的超时上限掐断」为什么不是一条判红；并且这三件事由一支注册进 core 档的常驻仪器复算。

## Scope

### In scope
- `run_all_guards.py`：非 PASS 条目附 `DIAG`（`mode=full`/`mode=tail`、行宽截断）；超时独立成 `TIMEOUT` 档并走退出码 3；
  `MAX_TIMEOUT` 上限进注册表形状检查；新增 `external` 档；`subset=` 改为「整表是否都被判过」；
  `verify_1to1_selftest` 按实测设 `timeout=`；两条 Web 条目的 needles 补「红路径也在」的第二形状；把
  `check_run_all_guards` 自己注册进 core 档。
- `check_run_all_guards.py`：新增超时/DIAG/上限/档位四类注入用例，`MIN_CASES` 抬到新面之下，
  `external-keys` 探针与 `core-keys` 配成一对。
- `guard-runner/SKILL.md`、ROADMAP 的 B3/D11/D12、D14（新登记）、`loop-watcher.md`+`.toml` 那句被改写的措辞、
  片 023 卡片的修订节、事实源清单的锚点覆盖面、`.codex/README.md` 第 4 步补上 `external` 档的责任归属。
- `.codex/scripts/dev/net_probe.py`：三面网络探针从 `artifacts/tmp/b036-net-probe.py` 上收为常驻打印工具
  （不判绿不判红，取不到读数即 `NO-READING` + 退出码 3）。上收的理由是 D13：D11②、D12②、D14 三条都为这件事
  现写过驱动，而那份驱动没人能再跑到。

### Out of scope
- Web 仪器本体的网络策略（D14）；C4 与 D7①；任何业务代码；`cdp_shot_selftest` 的等待上界（它的红不是墙钟）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 改的是**汇总面的读法语义**，不是任何被汇总守门的判定。
  保留：两条件判定（退出码 + 判据行）、`pass_codes`/`fail_codes` 语义、`MIN_ROSTER`/`MIN_CORE` 两个下限、
  FAULT 压过 FAIL 的次序、`--only` 子集绿话。变动：超时从 `fail` 移到 `timeout` 且退出码 1→3；
  非 PASS 多了 `DIAG` 段；`subset=` 含义；`cdp_shot_selftest` 不再被 `--tier core` 跑到。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `.codex/scripts/run_all_guards.py`；
  消费者按「谁会读这份输出/这个模块」逐个查过——`metrics_report.py` 的 `m_roster_by_tier()`（import `GUARDS`，
  按 tiers 分组，新增档位会多一个分组，故报告须重生成）、`check_run_all_guards.py`（import 同一模块，
  用 `argv` 逃生口跑合成表）、`check_loop_watcher_agent.py` 与其角色正文（角色正文逐字引用了旧的
  `timeout after 300s, so nothing was judged`，措辞变更后必须同步，否则角色教的是不存在的读数）、
  `.codex/README.md` 第 4 步与 `.codex/HARNESS.md` 第 7 步（都只写 `--tier core`，语义不变）、
  `guard-runner/SKILL.md`（判定规则的权威正文）。没有任何脚本解析 `RUN-ALL-GUARDS pass=` 那一行（grep 全仓零命中）。
- Native bridge or independent-client compatibility/update requirements: 不适用——纯 `.codex/` 仪器面，
  不碰 `android/`、`frontend/`、任何 SQL/API 契约或原生桥接。

## Documentation impact record

- Changed runtime facts: 汇总器的退出码语义（3 多包含超时）、非 PASS 的输出形状、档位集合与 `cdp_shot_selftest`
  所在档、`subset=` 含义、`verify_1to1_selftest` 的超时上限。
- Authoritative code/config/migration evidence: `.codex/scripts/run_all_guards.py`（`TIERS`/`MAX_TIMEOUT`/
  `DIAG_*`/`execute()`/`main()`）与 `.codex/skills/guard-runner/SKILL.md`（判定规则正文）。
- Documentation surfaces checked: `.codex/README.md`、`.codex/HARNESS.md`、`.codex/rules/*.md`、
  `.codex/agents/loop-watcher.md`+`.toml`、`.codex/agents/metrics-reporter.md`、ROADMAP B3/D11/D12/D13/D8、
  `.codex/context/source-of-truth.md`、片 023/033 卡片。
- Documentation files updated: SKILL.md、ROADMAP（B3 交付任务行、D11 闭合、D12 闭合、新增 D14）、
  loop-watcher 成对两份、source-of-truth 的档位锚点、片 023 修订节、本片卡片。
- Producer/consumer/sibling/compatibility paths checked: 见上节逐项；`grep -rn "so nothing was judged"` 把被取代
  措辞的四处命中全部定位（角色两份已改；ROADMAP 与片 033 是历史读数，按「历史记录不改写」保留并标注）。
- Superseded phrases/examples reverse-searched: `so nothing was judged`、`filtered-out`、`subset only` 三条逐一反向搜索。
- Historical/PRD handling: 片 023 卡片加修订节而不是改它的验收条目；D11/D12 的「现象」节保留改前读数原文。

## Acceptance criteria

- [ ] 一条判红的守门在整跑读数里带着它自己的用例名与原因行：`--tier core` 出现 `FAIL` 时紧跟
      `DIAG … mode=full` 且红用例名在段内；证据 = 电池用例「判红时把子进程自己的用例名一起打印」+ 真实 cdp 单跑对照。
- [ ] 长输出只取末尾并写明 `mode=tail`；判绿条目**不带** `DIAG`；证据 = 电池对应用例（含「必须不出现」侧）。
- [ ] 超时不再算判红：合成守门挂住不回 → 状态列 `TIMEOUT`、汇总行 `timeout=1`、总退出码 3、
      措辞含「本轮对这条没有判据」，且读数里**不得**出现 `fail=1`/`VERDICT: FAIL`；真实一轮在并发姿势下给出同形读数。
- [ ] 「永不超时」进不了注册表：`timeout` 超过 `MAX_TIMEOUT` → `ROSTER FAULT`、退出码 3。
- [ ] `verify_1to1_selftest` 的 `timeout=` 由串行与并发两种姿势的实测分布定，读数逐轮贴在证据块里；
      原 D12 回读里「并发仍全绿」那句若被实测否证，必须改写而不是保留。
- [ ] `cdp_shot_selftest` 不在 core 档：`--tier core` 整跑不再跑到它且 `external-keys=1/1 core-leak=0`；
      挪回 core 时该探针判红（注入用例给出）。
- [ ] 汇总器自己的判定分支有常驻仪器：`check_run_all_guards` 在 core 档表内（`--list` 读数），
      且它跑的是合成表、不递归跑真实表。
- [ ] RED 对照成立：同一支电池一字不改架在 `git show HEAD:` 取出的旧实现上，逐条 BROKEN 且每条都能归因到
      本片新增的一处语义；改后 `broken=0`。
- [ ] 被取代的措辞不留成死指针：`loop-watcher` 成对两份同步且其成对守门 0 条失败。
- [ ] 全部门禁现场复算：`--tier core` 连续多轮全绿（串行单跑）、`--tier precommit` 一轮、
      `validate_tasks`、`check_source_of_truth`、`check_pointer_liveness`、`check_roadmap_and_routing`、
      `run_evals`、`metrics_report` 重生成后只判复算。

## TDD execution record

### RED — failing test first
- Test/scenario: 电池 `.codex/scripts/check_run_all_guards.py` 一字未改，架在两版实现上各跑一次——
  RED 那一次由 `python -X utf8 artifacts/tmp/b036-red.py` 把 `run_all_guards` 换成 `git show HEAD:` 取出的
  旧副本（`artifacts/tmp/_g036/`，副本里 `REPO_ROOT` 指回真实仓库，驱动先断言电池 import 到的就是那份模块对象），
  GREEN 那一次跑的是工作树里的当前实现。用例集、期望标签与断言在两版上逐字相同，变的只有被测实现。
- Command: `python -X utf8 .codex/scripts/check_run_all_guards.py`
- Exit code: 1
- Observed failure: 见证据块 02 —— `SELFTEST cases=33 executed=33 floor=26 broken=8`，八条 BROKEN 分别是
  超时档（2 条）、DIAG（2 条）、`MAX_TIMEOUT`（1 条）、`--tier external` 选择（1 条）、
  `core-keys` 与 `external-keys` 两条探针（2 条）。
- Failure reason validated: 每条的 `missing=` 都是本片才引入的措辞或状态名（`TIMEOUT`/`timeout=1`/`DIAG`/
  `mode=full`/`mode=tail`/`ROSTER FAULT`/`tier=external selected=1`/`core-keys=5/5`/`external-keys=1/1`），
  旧实现里这些分支不存在或走的是另一条 lane——不是用例拼错。
  另有一处 RED 先打到电池自己身上（`TypeError: %i format`：`LONG_OUT` 的子进程代码里 `%03d`/`% i`
  被外层 `% GREEN` 当成自己的转换符），证据块 01 保留那次原文，修正后才有块 02 那份可用的 RED。

### GREEN — minimum implementation
- Implementation: `run_all_guards.py` 的 `diag()`/`_decode()`/`TIMEOUT` 档/`MAX_TIMEOUT`/`external` 档/
  `subset` 口径/两条 needles/`check_run_all_guards` 入册。
- Command: `python -X utf8 .codex/scripts/check_run_all_guards.py`
- Exit code: 0
- Passing evidence: 见证据块 03（末行 `SELFTEST cases=33 executed=33 floor=26 broken=0` + `OVERALL: PASS`）。
- Regression evidence: 既有用例一字未删（`判绿被认下来`、`rc=0 但静默 → FAIL`、`rc=2 → 无从认证`、
  `脚本不在场 → FAULT`、`解释器起不来 → FAULT`、`输出非 UTF-8 → FAULT`、`空表/挪空 core → 注册表故障` 等仍在表内），
  `MIN_ROSTER`/`MIN_CORE` 未动；真实整跑两档读数在证据块 06/07。

### REFACTOR — optional
- Cleanup: `--list` 的档位计数改为遍历 `TIERS` 生成，避免再新增档位时要改第二处措辞。
- Regression evidence: 电池的 `--list` 用例（`ROSTER entries=` 在内）与 `roster-shape` 探针。

### LOOP — optional
- 未使用：本片没有「同一失败身份重复」的回路。中途出现过两次非绿读数，各自一次改动后即消解，
  逐轮贴在证据块 01/05（一次是电池自身的格式化缺陷，一次是 `--tier external` 用例对 `subset` 含义的误设）。

## Implementation notes

- 超时的输出不能丢：`kill` 之后再给一次**带 deadline** 的 `communicate`，排得空就把那份部分输出交给 `DIAG`，
  排不空就在读数里写「管道没排空」。「取不到」和「它一个字都没打」是两件事。
- `DIAG` 的分界按**非空行**数：判红输出常见形态是十几行逐用例 + 一行汇总，整段才留得住红因；
  构建类守门动辄几千行，只能取末尾。判绿条目不带 `DIAG`——否则汇总面会被绿条目的流水输出灌满。
- `MAX_TIMEOUT` 与 `MIN_*` 是同一族设计：把「放宽上限来消音」这条路本身判成注册表故障。
  数值 1800 的依据是现存最重那条 `frontend verify` 的 `timeout=900` 的两倍，仍在「一轮最多被一条挂死的守门
  拖走半小时」以内。
- `external` 档不是「precommit 的别名」：`precommit` 的定义是依赖未提交增量/构建产物/Gradle，
  把网络依赖并进去会让那档第二次变质，也没法回答「这条红该不该算产物回归」。
- needles 必须在那条守门的**故障路径**上也在：`verify_1to1_selftest` 的 `vocabulary-layer-aborted` 分支
  提前返回，不打 `cases=` 汇总行——只登记 `cases=` 会把一句诚实的「本轮没材料」读成「没打印判据＝判红」。
  `cdp_shot_selftest` 的裸下限分支同一形状。这条是本片实测中撞出来的，不是推测。
- 角色正文与判据串是耦合的：`loop-watcher.md` 逐字引用了旧措辞，改判据不同步它就会教一条不存在的读数。

## Verification report

- Verdict: PARTIAL（四条读数形状都成了仪器事实，并由一支注册进 core 档的常驻电池复算：
  `SELFTEST cases=33 executed=33 floor=26 broken=0` / `OVERALL: PASS`。未闭合的那一条点名写在 Gaps ① 里——
  验收条目要求的「`--tier core` 连续多轮全绿」在本机当前网络状态下取不到，而取不到的形状恰好就是本片刚立起来的
  `TIMEOUT`（不是判红、是「本轮对这条没有判据」），根因归 D14，需要一次用户裁定；本片不靠抬上限或改判据把它抹成绿。）
- Evidence: `artifacts/evidence/T-2026-10-20-guard-diagnostics-timeout-lane-036.txt`
  （driver=`artifacts/tmp/b036-evidence.py`，只做拼接与现场补跑只读命令，一条读数也没改写。块 00 注册表现场
  `--list` 与落盘对照；块 01 保留了 RED 之前先打到电池自己的那份作废读数（`TypeError: %i format`，
  一条用例都没跑）；块 02 的 RED 对照 `cases=33 executed=33 floor=26 broken=8` 与八条 BROKEN 的逐条归因；
  块 03 的中间那次绿（`broken=1`，本片自己把 `--tier external` 用例的 `subset` 期望写错）与末态绿；
  块 04 并发/串行两种姿势的同值对照与作废的管道姿势；块 05 三面网络探针上收前后逐字同值与末态轮次之前那一次；
  块 06/07 三档整跑；块 08 指针复算、报告重生成与全部门禁；块 09 规模口径与改动面。原始捕获在 gitignore 的
  `artifacts/tmp/run*.txt`（号段 run327–run358 共 34 份，装配驱动逐条核对全部在场，缺任何一份就在表头点名）。
  末态两轮 `run356`（core）与 `run357`（precommit）在卡片 Verdict 写完并重生成报告（`run355`）之后跑，
  按 D12③ 串行单独跑、不与任何命令并发；`external` 档那条末态读数是 `run351`，它跑在卡片之前，
  表长与判据串与末态一致，这一点在块 07 里标明而不是当作末态轮次。证据文件写出之后只复跑会读它的门禁。
  设备/模拟器面未触及（D11/D12 不需要）。
- 逐条验收覆盖（每条给的是当场读数，不是「应该能」）：
  ① 判红带自己的用例名与原因行 — 电池 `CASE 判红时把子进程自己的用例名一起打印（D11…） expected=1 actual=1 Ok`；
  真实一轮 `DIAG verify_1to1_selftest lines=58/58 mode=full` 段内逐字出现
  `INSTRUMENT FAULT: #root 是空的或不存在（root=0 nodes=16 …）`（`run346`，末态 `run356` 同形）。
  ② `mode=tail` 与「判绿条目不带 DIAG」— 电池两条用例（取末尾的那条含「必须不出现」侧）均 `Ok`；
  真实一轮 `DIAG cdp_shot_selftest lines=30/91 mode=tail`（`run351`），同轮 35 条 PASS 行没有一条带 DIAG。
  ③ 超时不借判红的词 — 电池 `守门挂住不回 → … expected=3 actual=3 Ok`；真实两轮分别给出
  `TIMEOUT cdp_shot_selftest … timeout after 300s`（`run348`，抬上限之前）与
  `TIMEOUT verify_1to1_selftest … timeout after 600s`（`run346`，抬之后；末态 `run356` 复现同一读数），
  汇总行 `timeout=1`、`fail=0`、`VERDICT: INCONCLUSIVE`、退出码 3。
  ④ 「永不超时」进不了注册表 — 电池 `超时上限写超过 MAX_TIMEOUT → 注册表故障 expected=3 actual=3 Ok`
  （读数含 `ROSTER FAULT`）。
  ⑤ `verify_1to1_selftest` 的 `timeout=` 由实测分布定 — 串行单跑 `real 11m16.258s / rc=3`（`run335`）与
  并发四负载 `676.5s`（`run334`）同值，故 600s；D12 原回读里「并发仍全绿」那句已被这两轮否证并改写，
  作废说明留在 ROADMAP D12 处置 ② 与标题括注里。
  ⑥ `cdp_shot_selftest` 不在 core — `ROSTER entries=43 core=36 precommit=6 external=1`（`run338`）、
  电池 `external-keys=1/1 core-leak=0 missing=[] Ok` 与 `core-keys=5/5 missing=[] Ok`（`run350`），
  且「挪回 core 就会在这里断」由同支电池的注入用例双向盯住。
  ⑦ 汇总器自己有常驻仪器 — `core check_run_all_guards "SELFTEST cases="` 在 `--list` 表内（`run338`），
  它跑的是合成表、不递归真实表（电池内那条 `escape=0` 探针 + 本片全程没有出现自我递归）。
  ⑧ RED 对照成立 — 同一支电池一字不改架在 `git show HEAD:` 取出的旧实现上 `broken=8`，改后 `broken=0`；
  八条的 `missing=` 串全是本片才引入的措辞或状态名。
  ⑨ 被取代的措辞不留死指针 — `loop-watcher.md` 那句已按实测改写、`.toml` 重新生成字节相同，
  成对守门 `check_loop_watcher_agent` 在末态 core 轮里 PASS；`grep -rn "so nothing was judged"` 的四处命中
  两处已改、两处是历史读数（按「历史记录不改写」保留并标注）。
  ⑩ 门禁现场复算 — 见下面 Gaps ①，其余全部当场复算：`validate_tasks`、`check_source_of_truth`、
  `check_pointer_liveness`（`citations=114 judged_dead=0`）、`check_roadmap_and_routing`、`run_evals`、
  `metrics_report.py --write` 之后的只判复算、重复短语扫描。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除任何既有判据、下限或期望标签；
  `MIN_ROSTER=20` / `MIN_CORE=18` 一字未动，两条件判定（退出码 + 判据行）、`pass_codes`/`fail_codes` 语义、
  FAULT 压过 FAIL 的次序、`--only` 子集话都保留。三处是**改变读法语义**的，都附了改前改后而不是「看起来更好」：
  超时从 `fail` 移到 `timeout` 且退出码 1→3（改前 `run327` 的 `fail=1` 把一条没判的东西算进判红，
  改后 `run346` `fail=0 timeout=1`）；`subset=` 改成「整表是否都被判过」（改前 `run327` 的 `subset=no` 与
  改后 `run340` 的 `subset=yes` 是同一档位不同表长，语义变更由电池的 `NOTE not-selected` 用例钉住）；
  `cdp_shot_selftest` 换档（判据内容一字未动，变的只有它出现在哪个判定面）。两处 `timeout=` 抬高
  （`verify_1to1` 与 `cdp` 都到 600）不是把红压下去：抬之后它们分别给出 `TIMEOUT`（不给判决）与
  `SELFTEST FAIL cases=17 failures=2`（给判决），两条都是本片要的形状。
- Gaps: ① **「`--tier core` 连续多轮全绿」未证明**，且当前不可证明：`verify_1to1_selftest` 与 `cdp_shot_selftest`
  共用同一条 unpkg CDN 依赖（D14），本机系统代理 `127.0.0.1:7897` 一不通，core 就被这一条拖成
  `pass=35 … timeout=1` / `VERDICT: INCONCLUSIVE`（`run346` 与卡片写完后的末态 `run356` 两轮同形，
  末轮墙钟 11m18s）。`run354` 的探针把这条依赖钉在「走注册表代理」而不是
  「没网」上：三面里 `via-registry` 四个 URL 一律 `WinError 10061`，`no-proxy` 一律 `200`
  （react 109,931 / react-dom 1,080,227 / babel 3,137,752 / example 577 字节）。三种处置——收编 CDN、
  给浏览器实例钉 `--proxy-server=direct://`、把 `verify_1to1_selftest` 移出 core——前两种改 Web 本体、
  第三种改验收面，都超出本片授权，已登记为 D14 的两条待裁定。② **`mode=tail` 的下 30 行不保证包含红用例的名字行**：
  `run351` 里红因行 `FAIL page-not-rendered .screen 在 60s 内没出现…` 在场，但两例红的具体名字落在窗口之外；
  `mode=full` 那一支（`run346`）覆盖全部。修它要定「末尾窗口内必须保留带 FAIL/FAULT 标记的名字行」这种选择规则，
  属新判据设计，不在本片。③ 电池对超时档的断言用的是合成守门（挂住不回的那条），真实 TIMEOUT 只在整跑里取到过一次
  每种上限姿势各一轮，没有做成常驻可复跑的对照——它的失效方向是「汇总器改了但真实路径没人验」。
  ④ 配对 `.md`/`.toml` 的重生成器仍是 `artifacts/tmp/` 里的一次性驱动（D13 那一族没上收），
  下一个人改角色正文时仍要重写它。⑤ 复算器每轮报 `alias_unresolved=7`（裸文件名声称，判据不判也不搬），
  沿用片 035 Gaps ⑥，本片把 `run_all_guards.py` 加行后这个数从 3 涨到 7 的原因未逐条核。
  ⑥ `run334` 那份捕获正文是从 runner 的回传逐字转写落盘的（当时的后台命令没带重定向，文件 0 字节），
  块 04 里已标明出处；它是「并发读数」唯一的落盘来源。
- Risks: 汇总器的判据行串与守门措辞耦合——措辞一改汇总就判「没打印判据」，这条会响不静默，但响的时候人得知道
  去看两边。`external` 档现在只有一条，档位这套机制换来的只是「core 不被环境抖动污染」这一件事；如果 D14 按
  「移出 core」裁定，这个档位会扩大到两条，那时要重定 `MIN_CORE`。TIMEOUT 走退出码 3 之后，任何把
  「非 0」当红的调用方（人或脚本）都要重读一次语义——全仓 grep 只有 `metrics_report.py` import 这张表，
  没有解析汇总行的消费者。

## Handoff notes

- Completed: `run_all_guards.py` 的 `DIAG`（`mode=full`/`mode=tail` 两档、行宽截断）、`TIMEOUT` 独立档 +
  退出码 3 + `MAX_TIMEOUT` 上限进注册表形状检查、`external` 档位与 `subset=` 新语义、两条 Web 条目的第二判据行、
  `check_run_all_guards` 自注册进 core；`check_run_all_guards.py` 新增四类注入用例并把 `MIN_CASES` 抬到 26；
  常驻探针 `.codex/scripts/dev/net_probe.py`；SKILL/README/ROADMAP（D11 闭合、D12 闭合并推翻自己的登记归因、
  新增 D14）/`loop-watcher` 成对两份/片 023 修订节的同步改写。D11、D12 两条欠账闭合，D14 新登记未闭合。
- In progress: 无。工作树里本片之外的改动只有此前各片留下的未提交文档与仪器（`git status` 逐字在证据块 08）。
- Blocker: 本片范围内没有阻塞；「core 连续多轮全绿」这条验收被 D14 挡在外面，等的是一次裁定，不是等实现。
- Unblock condition: 用户对 D14 三选一（收编 CDN / 给 `parity_cdp.mjs` 那个实例钉 `--proxy-server=direct://` /
  把 `verify_1to1_selftest` 也移出 core）给出选择，或本机 `127.0.0.1:7897` 代理客户端被重新跑起来
  （后者只算取绿轮次的操作条件，不构成闭环，见 D14 修法 ③）。
- Next steps: ① D14 裁定之后那一半（改 Web 本体或改验收面）；② 片 035 Gaps ①④⑥ 三条仍未有人认领，其中 ④
  「22 例正反双向自检」那句手抄计数需要的是一支带裸下限的自检例数漂移脚本；③ C4 与 D7① 需要实机与用户在场，
  跑之前先按 `device-verify-install-r-only` 的规矩确认；④ `external` 档如果按 D14 扩到两条，`MIN_CORE` 要重定。
- Watch-outs: ① 本片规模已越过任务尺寸口径里「>200 生产行 → verifier 提醒」那条软线（实测 207 行，
  `run_all_guards.py` +113/−33 与 `net_probe.py` 94 行，电池与文档按口径不计），下一片别再往这个方向加。
  ② 两条 600s 上限的条目贴着 `MAX_TIMEOUT=1800`，但**抬上限的判据只有一条**：盖过「能给出判决的最长一次实测」，
  不是盖过「最慢的那一次」——照默认 300s 掐掉 `cdp_shot_selftest` 就是把它能给的判决丢了（`run348` vs `run351`）。
  ③ 判据行串与守门措辞耦合：改任何一条守门的打印，先看 `run_all_guards.py` 里它的 needles 覆盖到没覆盖故障分支
  （`vocabulary-layer-aborted` / `selftest-case-floor` 这两条第二判据行就是为这个存在的）。
  ④ 别把 `TIMEOUT` 当红去查回归：它的整个语义是「本轮对这条没有判据」，处置只有重跑或换姿势。
  ⑤ 三档整跑末态仍按 D12③ 串行单独跑，不与任何命令并发。
