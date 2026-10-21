# Codex Harness Task: 把仓库现存只读守门收成一条命令（ROADMAP B3）

id: T-2026-10-19-023
title: run_all_guards 汇总器、注入自检电池与 guard-runner 技能，并闭合 D9
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 332
shared_code_touched: false
depends_on: [T-2026-10-18-treelearn-process-migration-001.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 要求「完成结论必须匹配证据覆盖，工具没实际运行时不得报 PASS」。这条要求在此前只写在规则正文里，靠人记得逐条跑守门；没有一条命令能把「跑没跑」这件事变成机器判据。
- Problem/background: ROADMAP B3 的原话是「杜绝守门其实没运行却报告通过」。仓库里有 31 支只读检查（Python/Node/PowerShell 三种解释器），此前只能逐条手跑；漏跑一条不会有任何信号。更糟的形态是**某条守门返回 0 但其实什么都没判**——解释器换成了别的、路径解析失败、被检集合变空，这三种都会安静地打印 0。
- Users/scenarios: 使用者是写码的 AI 与用户本人。场景是「这一片要提交前，整跑一次并留一份能复算的证据」，以及「新会话开工前先确认门禁本来就是绿的」。
- Core flow and reversibility: 新增两个脚本与一个技能目录；不改任何被判的守门，也不改它们的判据。删除 `.codex/scripts/run_all_guards.py`、`.codex/scripts/check_run_all_guards.py`、`.codex/skills/guard-runner/` 即完全回到本片之前（注册表在被汇总器之外没有任何消费者，所以没有反向依赖）。
- Inputs/outputs: 输入 = 注册表里每条的（怎么跑、必须看到哪一行、什么退出码算绿）；输出 = 逐条 `PASS/FAIL/INCONCLUSIVE/FAULT` 行 + 一条判据摘录 + 汇总判决行 + 退出码 0/1/3。
- State and exception handling: 三档严重度分开——判红（守门判出问题）压不住被 FAULT（守门根本没跑起来）覆盖，因为「仪器坏了」比「工件是红的」更需要先看；`--only` 只跑子集时总话术必须写 `subset only`，不允许子集声明整表绿；一条都没选中打 `NO-SELECTION` + INCONCLUSIVE 而不是「没有 FAIL 所以 PASS」。
- Constraints and trade-offs: 只用标准库；不把 PowerShell 的 1/2 与 Python 的 1/3 抹平成同一档，改用每条自带 `pass_codes/fail_codes/rc_labels`（`line-budget` 的 1 是 WARN、`no-kotlin-check` 的 2 是 PARTIAL 且绝不是通过）。代价是注册表需要人工维护，收益是新守门进不了汇总面时会被 `core-keys` 那条自检咬住。
- AI recommendation and rationale: 判定用**退出码 + 判据行两个条件**，单靠退出码不足以证明「判过」。这不是多余的第二道闸：本片实测过程中真的出现过一条 rc=0 而 stdout 空的情形（见 Implementation notes）。
- User choice and custom additions: 用户要求一站到底、按片提交本地仓库、不中途请示。
- AI-autonomous decisions: ① 分档为 core/precommit 两级，依赖未提交增量或构建产物的那 6 支（`check_offline_bundle`、`frontend verify`、`line-budget`、`no-kotlin-check`、`guard-selftest -Fast`）不放进 core，因为 core 每片都要整跑，不能被 git 增量和 Gradle 拖住；② 需要实机或本机服务端的结果型走查（`web_walk` 正文、`capture_screen`、像素量测、release-gate）不写进注册表，登记到 C4 并在技能正文写明「不在册内容」；③ 汇总器不设「只打印失败项」模式，每条都要一行——静默本身就是本片要防的。
- User-confirmation decisions: 无新增（沿用 001 已确认的流程范围）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-19-guard-runner-one-command-roster-023.txt` 块 01–08。
- Facts: `--tier core` 25 条全绿（`pass=25 fail=0 inconclusive=0 fault=0`，`VERDICT: PASS (all 25 …)`，EXITCODE=0）；`--tier all` 31 条全绿（`selected=31/31 subset=no`，EXITCODE=0）；配套电池 26 例 `executed=26 floor=20 broken=0`；注入「没判据行→FAIL」这条分支为 PASS 后，同一条电池命令 `broken=2`、`OVERALL: FAIL`、EXITCODE=1，复原后 sha 与注入前同为 `95644f21aecf` 且再次 `broken=0`；`--list` 打 `ROSTER entries=31 core=25 precommit=6`。行数口径：`run_all_guards.py` 387 行文件 / 非空非纯注释 332 行（计入），`check_run_all_guards.py` 206 行属**测试**、`.codex/skills/guard-runner/SKILL.md` 属**文档**，按 `.codex/rules/harness-tasks.md:47`「Tests, documentation, and generated files do not count」不计入；332 > 200 因此带一条 verifier warning，本片按此口径如实声明而不把估算压到 200 以下。
- Assumptions: 无。注册表条数与档位读数全部现场取自 `--list`，本卡不复述它明天的形态。
- Unknowns: 汇总器在没有 node/powershell 的环境（CI 容器）会走 FAULT 分支，这条只有合成用例覆盖、没有真实无解释器环境实测；已在技能正文写明那属「无从认证」而不是「跳过」。
- Risks: 注册表里 31 条的判据行串抄自各守门当前输出，某条守门改了措辞会让汇总器判 FAIL（方向是响，不是漏）；`core-keys` 自检用四个名字钉住 core 档的关键覆盖，防止「删条目来变绿」。
- Deferred: 把「新增守门未登记」做成自动盘点（要靠扫描 `.codex/scripts/**` 反查注册表，误报面大）；把结果型走查收进一条 `--tier device`，等 C4 的 release-gate 一起做。
- Non-goals: 不改任何被判守门的判据；不引入 CI；不做并行跑（并行会让「哪条守门把 CPU 打满」这类问题不可读，且各守门本来就快）。
- Skipped non-critical questions and reason: 未追问要不要把证据文件也自动生成（`--tee` 之类）——那会让「写了证据」和「跑了守门」两件事混成一个开关，本片要求的是显式重定向，命令本身留在证据块里。

## Goal

一条命令能整跑仓库现存的只读守门，并且对「返回 0 但什么都没判」判红；配套电池对汇总器的每条检测分支各注入一次违规并要求那次违规被判。

## Scope

### In scope
- `.codex/scripts/run_all_guards.py`：注册表（31 条，`core`/`precommit` 两档）、`roster_problems`（形状自检）、`resolve`（三种解释器）、`execute`（Popen + 带 deadline 的 `communicate` + 裸字节解码）、`judge`（退出码与判据行两个条件）、`main`（0/1/3 三档退出码与 `subset only` 话术）。
- `.codex/scripts/check_run_all_guards.py`：26 例注入电池，跑合成注册表，不碰真实仓库文件。
- `.codex/skills/guard-runner/SKILL.md`：何时触发、怎么跑、判定规则、加一条守门的要求、不在册内容。
- `.codex/README.md` 的「怎么用」加一条整跑入口（第 4 步）。这一条超出 ROADMAP B3 声明的可写路径，属流程入口的必要登记：新命令若只写在技能正文里，读 README 的人不会知道有它。**AGENTS.md 不动**：其「工具与参考」第 62 行的 `.codex/skills/*/SKILL.md` glob 与第 64 行的 `.codex/scripts/` 目录行已覆盖这一入口，再加一条就是重复权威正文（documentation.md 的「同一约束只保留一份权威正文」）。
- D9 的四处活指针措辞修正（与本片合并提交，理由见该条「代价」行）。

### Out of scope
- 任何被判守门的判据、阈值与用例数；`check_androidtest_schema_expectations.py` 的白名单（片 022 已单独处理）。
- 需要实机/本机服务端的结果型走查（属 C4）；CI 集成；并行执行。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 无运行时产品行为变化（改的是流程工具面）。要保留的旧行为是「各守门单独跑时读数不变」——本片不 import 也不改写它们，只以子进程调用。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者=注册表 `.codex/scripts/run_all_guards.py:81-178`（`GUARDS`）；消费者=.codex/skills/guard-runner/SKILL.md 与 ROADMAP B3 条目的验收方式；兄弟入口 `.codex/skills/lint-all/SKILL.md`（按变更路径选检查）与汇总面（整跑）互补，技能正文显式指向它而不复述其内容。平台分支：`resolve()` 对 PY/NODE/PS 三种解释器各一条路径，PS 那条必须先把控制台切成 UTF-8，否则判据行按系统代码页落进管道而不可读。根 `AGENTS.md` 的路由表未新增触发词（本片不是一条新规则，而是既有「完成结论必须匹配证据」的执行工具），因此 `check_roadmap_and_routing` 的当前态路由判据不受影响。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本片确实新增了一条会被每片调用的门禁入口。

## Documentation impact record

- Changed runtime facts: 新增一条流程入口命令；未改任何产品运行事实。
- Authoritative code/config/migration evidence: `.codex/scripts/run_all_guards.py:1-24`（判据与退出码的权威说明就写在 docstring）、`:39-42`（裸下限）、`:231-243`（两个条件）、`:246-279`（Popen/deadline/裸字节解码）。
- Documentation surfaces checked: `AGENTS.md`（路由表是否需要新触发词）、`.codex/README.md`（守门叙述）、`.codex/rules/harness-verification.md`、`.codex/skills/lint-all/SKILL.md`、`.codex/ROADMAP.md`。
- Documentation files updated: `.codex/ROADMAP.md`（B3 条目加交付任务与实现要点回写；D9 标闭合并写明落点改判；新增 D10 条目）、`.codex/skills/guard-runner/SKILL.md`（新建）、`.codex/README.md`（怎么用加一条整跑入口）、D9 那四处活指针（`check_roadmap_and_routing.py`、`harness_check_lib.py`、`secret-scan/SKILL.md`）。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact 的 code evidence 行。
- Superseded phrases/examples reverse-searched: `grep -rnI "AGENTS.md 失败案例" .codex/scripts .codex/skills .codex/rules` 与 `grep -n 失败案例 AGENTS.md` 均 0 命中（D9 的回读命令，实测见证据块 06）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/run_all_guards.py --tier core` 逐条打印每个守门的名字与结论，25 条全部判绿且总话术声明「printed a judgement line and returned green」，EXITCODE=0。证据 = 块 01。
- [x] 「rc=0 但一个字都不打」必须判 FAIL：同一条电池命令 `python -X utf8 .codex/scripts/check_run_all_guards.py` 在把 `judge()` 的那条分支改成「→ PASS」后立刻 `broken=2`、EXITCODE=1，复原后（sha 字节相同）再次 `broken=0`、EXITCODE=0。这条是 B3 的核心判据，不是附带覆盖。证据 = 块 02 + 驱动 `artifacts/tmp/b3-redgreen.py`。
- [x] 电池对汇总器的每条检测分支各有一条注入用例，且用例数写在裸下限上：`MIN_CASES = 20`（实测 26 例），改成由 `len(CASES)` 推导等于没有下限。证据 = 块 03 的 `SELFTEST cases=26 executed=26 floor=20`。
- [x] 注册表形状六种自相矛盾（空表、core 被挪空、重名、判据行为空串、档位名写错、pass/fail 码重叠）与 `pass_codes` 不含 0、`timeout` 非正数各有一条用例，且都走退出码 3（INCONCLUSIVE），不是 1。证据 = 块 03 的九条 `expected=3`。
- [x] 「只跑子集」不允许冒充整表绿：`--only` 命中子集时话术必须含 `subset only`，一条都没选中打 `NO-SELECTION` + INCONCLUSIVE(3)；`--tier precommit` 该档为空同样 3。证据 = 块 03 的三条子集/选择用例。
- [x] 三档退出码互斥未被破坏：判红=1、仪器坏=3、全绿=0，且 FAULT 压过 FAIL（一条判红 + 一条起不来 → 3）。证据 = 块 03 的「故障压过判红」用例 + 块 01/04 的真实读数。
- [x] 全部门禁现场复算：`--tier all` 31/31 绿（块 04）、`check_run_all_guards.py` 26 例绿（块 03）、`validate_tasks.py`（块 05）、`check_roadmap_and_routing.py`（块 05）。
- [x] D9 的两条回读命令 0 命中，且未改动任何判据逻辑（本片对那三个文件的改动只在注释/docstring/技能正文措辞）。证据 = 块 06 + 本片的 diff。

## TDD execution record

### RED — failing test first
- Test/scenario: 汇总器最容易犯的错是「把没跑/只跑一部分/跑完但什么都没判都说成绿」。把 `judge()` 里「没有判据行 → FAIL」这条分支改成「→ PASS」（其余一个字节不动），跑覆盖它的两条电池用例。
- Command: `python -X utf8 .codex/scripts/check_run_all_guards.py`
- Exit code: 1
- Observed failure: `SELFTEST cases=26 executed=26 floor=20 broken=2`，两条 BROKEN 分别是 `rc=0 但一个字都不打 → FAIL（B3 的核心要求） — rc want=1 got=0 missing=['no judgement line printed', 'VERDICT: FAIL']` 与 `一条绿 + 一条哑：总体判红，但两条都要打印出来 — rc want=1 got=0 missing=['fail=1', 'VERDICT: FAIL']`，末行 `OVERALL: FAIL` / `EXITCODE=1`。
- Failure reason validated: 失败点正是本片要防的形态（安静被判绿），不是合成命令起不来——同一条命令在按 raw bytes 复原（sha `95644f21aecf` 前后相同）后立刻回到 `broken=0` / `EXITCODE=0`；且用例是「期望 rc + 必须出现的判决行 + 必须不出现的措辞」三元组，`forbidden=['VERDICT: PASS']` 那条也满足才记 Ok，所以红了不是因为缺输入。

### GREEN — minimum implementation
- Implementation: `run_all_guards.py` 的注册表 + `roster_problems` + `resolve` + `execute` + `judge` + `main` 三档退出码；`check_run_all_guards.py` 的 26 例注入电池（跑合成注册表与临时子进程，真实仓库文件一个都不改）。
- Command: `python -X utf8 .codex/scripts/check_run_all_guards.py`
- Exit code: 0
- Passing evidence: `SELFTEST cases=26 executed=26 floor=20 broken=0` / `OVERALL: PASS` / `EXITCODE=0`；真实读数 `python -X utf8 .codex/scripts/run_all_guards.py --tier core` → `RUN-ALL-GUARDS pass=25 fail=0 inconclusive=0 fault=0` / `VERDICT: PASS (all 25 selected guard(s) printed a judgement line and returned green)` / `EXITCODE=0`。
- Regression evidence: `--tier all` → `selected=31/31 subset=no` / `pass=31` / EXITCODE=0；`validate_tasks.py` 与 `check_roadmap_and_routing.py` 见块 05。

### REFACTOR — optional
- Cleanup: 开发中途两次改判不是「整理」而是错因修正：① `resolve()` 最初把每个参数都 `join` 到 REPO_ROOT，使 `--selftest`/`-Fast` 变成不存在的路径（core 首跑因此中断）——改为 `args[0]` 是脚本、其余原样传；② 15 条用例最初因 `MIN_ROSTER/MIN_CORE` 拒收小合成表，用 `pad_to_floor()` 补足而非去改下限常量（改下限等于仪器给自己开后门，D4 同族）。
- Regression evidence: 与 GREEN 同一条命令（`broken=0` 必须包含那两条下限用例，它们走 `NO_PAD`）。

## Implementation notes

- 判据行必须在该条守门的**红路径上也在**，否则「判红」会被读成「没判据」——注册表因此不是随便挑一串短语：`Checks` 纪律的七支取 `check(s) failed`（该短语与判决无关，红绿都打），走查类取 `VERDICT:`/`OVERALL:`/`SYNC CHECK`，扫描类取各自的 `… scan root=`。
- PowerShell 三条用 rc_labels 保留原义（`line-budget` 1=WARN/2=FAIL，`no-kotlin-check` 2=PARTIAL），判定仍只有 pass_codes/fail_codes/其余三档；PARTIAL 不在 `no-kotlin-check` 的判绿集合里，所以它跳过阶段时汇总器判 INCONCLUSIVE 而不是绿。
- 不用 `subprocess.run(timeout=)`：它超时后 kill 再无限等管道，守门的孙进程握着管道时汇总器跟着挂死，那比判红更糟。改 Popen + 每次读都带 deadline，并且先收裸字节再在进程内解码：Windows 的读取线程会把 `UnicodeDecodeError` 打在 stderr 后当成「没输出」，那样「乱码」会被读成「这条守门没判据」——仪器坏了与判据没打印是两个病因，不能混进同一条 FAIL。这条改判由 `artifacts/tmp/probe-utf8.py` 现场验证过（改前 `execute` 返回 FAIL，改后返回 FAULT/3）。
- 注册表下限 `MIN_ROSTER = 20` / `MIN_CORE = 18` 与 `CORE_KEYS` 四条关键守门名写在裸字面量上：删条目不会让表「变小但仍绿」，而是当场 `ROSTER FAULT`。
- **修订（2026-10-20，片 `T-2026-10-20-guard-diagnostics-timeout-lane-036.md`）——本卡的判定语义被改过，按 D12② 的要求写回交付卡而不是只留在 ROADMAP**：
  ① 「超时」原来算在 `fail` 里（`FAIL … timeout after 300s` / 退出码 1），现在单独成档 `TIMEOUT` 并把总退出码压到 3，
  措辞改成「本轮对这条没有判据」——因为超时是**没判**，不是产物判红，借判红那个词会让下一位实现者去查一个不存在的回归。
  ② 非 PASS 且非 FAULT 的条目现在附带 `DIAG`（子进程自己的原始输出，短则整段 `mode=full`、长则末尾 `mode=tail`），
  这一条补的是本卡验收方式没覆盖的那一半：判据行在场不等于**红因**在场。
  ③ 新增 `MAX_TIMEOUT` 上限（超过即 `ROSTER FAULT`）与 `external` 档（`cdp_shot_selftest` 移出 core，见 D11③/D14）。
  ④ `subset=` 那一行的含义从「有没有 `--only` 过滤」改成「整表是否都被判过」，所以 `--tier core` 现在也打印 `subset=yes`——
  原措辞会把一次档位整跑读成「全表已判」。
  ⑤ 本卡那句「26 例电池」的用例条数不再由 prose 承担：条数由 `check_run_all_guards.py` 自己打印，
  且这支自检现在注册在 core 档里（`check_run_all_guards`），「改过判定分支必须整跑自检」这句话从此有常驻仪器背书。
  修订前后的对照读数（同一支电池架在 `git show HEAD:` 取出的本卡实现上 `broken=8`，对修订后的实现 `broken=0`）
  在本片卡片的证据块 02/03。本卡其余判据、下限与期望标签一字未删。

## Verification report

- Verdict: PASS（真实两档整跑 + 同文件注入/还原字节证明 + 26 例电池 + 全局门禁读数；独立 verifier 轮次未做，见 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-guard-runner-one-command-roster-023.txt` 块 01（core 档 25 条逐条读数，末态）、块 02（RED/GREEN 注入对照与 sha 复原）、块 03（电池 26 例全量）、块 04（`--tier all` 31 条逐条读数，Popen 改判后重跑）、块 05（真实注册表上的一条真红 + `subset only` 话术）、块 06（选中零条 → INCONCLUSIVE/3）、块 07（`validate_tasks`/`check_roadmap_and_routing`/`checkers_selftest` 当前读数）、块 08（D9 两条回读命令 0 命中）。环境：Windows 10.0.26200 / x64 / Python（`sys.executable`，一律 `-X utf8`）/ Git Bash / node / PowerShell。文件由 `artifacts/tmp/gen-023-evidence.py` 从捕获文件逐字节缩进生成（324 行，`\ufffd` 计数 0），捕获文件留在 gitignore 的 `artifacts/tmp/`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片没有删除或改宽任何判据。对既有文件的改动只有 D9 的四处文字（脚本 docstring、脚本内注释、技能正文各一句），逐处见 diff；`check_run_all_guards.py` 的 `MIN_CASES`/`MIN_ROSTER`/`MIN_CORE` 与 `CORE_KEYS` 是新写的裸下限，不是对既有下限的替换。
- Gaps: ① 无 node 或无 powershell 的真实环境未实测，`resolve()` 的两条「解释器不在场」分支只由合成用例覆盖（`BAD_INTERPRETER` 那条走 FAULT/3）；② 两支注入驱动 `artifacts/tmp/b3-redgreen.py`、`artifacts/tmp/b3-rendred.py` 与证据生成器都在 gitignore 内，块 02/05 的一次性对照在别的克隆上跑不出来，长期复算靠已入库的 26 例电池；③ `--tier all` 依赖构建产物（`frontend/dist`、APK 输出）与 git 增量，产物缺失时 `check_offline_bundle`/`line-budget` 会红或 INCONCLUSIVE——这是设计如此（它们属 precommit 档），但意味着「all 绿」不等于「产物之外的东西也判过」；④ 独立 verifier 复核已于 2026-10-20 完成（本片属 harness 仪器面，每条判据都有对应的注入用例可现场复算），结论见本节末尾 Independent verification 行。
- Risks: 判据行串与各守门措辞耦合，措辞一变汇总器就判 FAIL（响，不静默）；注册表靠人工维护，新写的守门不登记就不会进整跑面——这条留给 Deferred 的自动盘点，当前的替代是 ROADMAP B3 条目的验收方式要求「新守门进册」。
- Independent verification（2026-10-20）：PASS。本期 verifier 按验收规则复核本卡的判据、证据与末态读数；片 037 闭合 D14 后 core 档已可稳定整跑，未发现与本卡结论冲突的读数。

## Handoff notes

- Completed: 汇总器 + 26 例电池 + `guard-runner` 技能；`--tier core`（25）与 `--tier all`（31）现场全绿并留证；ROADMAP 的 B3 回写、D9 闭合、D10 登记与闭合；证据文件块 01–06；本地提交两片（022 Android 面、023 harness+D9 面）。
- In progress: 无（本片已收口）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 后续每片提交前整跑 `--tier core`、提交时跑 `--tier precommit`，命令与读数直接进该片的证据块；结果型走查（web_walk 正文/capture_screen/像素/release-gate）等 C4 一起进册；D7 的 11 例既有仪器红仍未动，须用户在场跑仪器。
- Watch-outs: 不要为了「让汇总器绿」去改某条守门的判据行串或把它从注册表删掉——`core-keys` 与 `roster-shape` 两条自检会当场把这张表判成不可信（退出码 3）。判据行串应当从那条守门的**红路径**输出里取。
