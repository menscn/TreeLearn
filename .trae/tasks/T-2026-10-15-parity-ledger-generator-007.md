# Treelearn Harness Task: 1:1 三层判据台账生成器

id: T-2026-10-15-007
title: 1:1 三层判据台账生成器
template_version: 2
task_type: implementation
status: in_progress
parent_requirement: T-2026-10-15-web-h5-android-parity-requirement-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 299
shared_code_touched: false
depends_on: [T-2026-10-15-screen-inventory-parity-ledger-005.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 005 已经让「有哪些屏」变成从代码可达性算出来的一份 JSON，但规格 §10 要的还有第二件事——一张逐屏的**判据台账**，让「还原到什么程度」这件事在产物里可见，而不是等到阶段 9 由人回忆。旧 Web 线的失败形态正是这个：每片各自宣称做完，收口时拿不出「哪一屏哪一层从未量过」。
- Users/scenarios: 阶段 1 夹具采集按台账行排队；阶段 3–6 每片把某一行从缺证据态改成本侧记录；阶段 9 收口只认台账里剩余缺证据条数为零。
- Expected outcome: 一条命令重生成 `artifacts/parity/ledger.md`，条目与数字全部由清单与仪器记录现场派生；每格要么是证据给出的值，要么是一个显式的缺证据令牌。
- Core flow and reversibility: 读 `Docs/04-ux-design/parity/screen-inventory.json`（005 产物）+ `.trae/scripts/dev/parity/screens.json`（003 屏档案）+ 档案指向的量测记录文件，产出 Markdown 台账；并**重新导出**一次清单的「实测面」与已落盘清单比对，作为规格 §10 的防腐条。纯工具片，可整片回退。
- Inputs/outputs: 输入为清单 JSON、屏档案、量测记录；输出为 `artifacts/parity/ledger.md` 与 stdout 的判据行。
- State and exception handling: 退出码分层 0/1/2/3；台账条目低于字面下限、清单过期、行计数与清单计数不符、存在未到达屏或未引用布局，一律判 1；输入缺失或不可解析判 3（仪器故障不是覆盖率结论）。
- Constraints and trade-offs: 只读，不改 Android、不改 005 的清单内容；防腐比对刻意**排除** `source_commit` 字段，否则任何一次无关提交都会把台账判红（比对的是「界面自己说了什么」）。
- AI recommendation and rationale: 视觉/行为两列读的是**证据在不在**而不是「像不像」，因为本片没有 Web 侧，任何「已还原」的措辞都会是无证据断言；缺证据必须是一种取值，不能是空格。
- User choice and custom additions: 用户批准阶段 1 首片并按 Harness 连续执行。
- AI-autonomous decisions: 从 005 分片出来（理由见 Implementation notes 的实测规模）；台账落 `artifacts/parity/`；屏档案键名按「类名去 Screen 后缀小写」约定匹配。
- User-confirmation decisions: 无新增待确认项；范围数字已由用户 2026-10-16 裁定「以实测清单为准」，本仓库既有的那条待裁决项因此关闭（成因与实测值登记在 005 的 Implementation notes，那片是范围的唯一权威；本片不复述条目数，只消费清单）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-15-007-red.txt`、`-green.txt`、`-mutation.txt` 三份各自带 7 个判定块的 want/got 表与 `OVERALL_EXITCODE=0`；变异电池（`artifacts/tmp/gen-007-mutation.sh`）声明 8 条、下限 8，实测七条守卫退化各自只让自己的用例转红、空变异 `caught=False`、副本还原后仍 17/17 绿。
- Facts: 台账条目数 = 清单的屏数 + 浮层数（两个数都由 005 的打印给出，本片不复述；回读 `python -X utf8 .trae/scripts/dev/gen_parity_ledger.py` 的 `LEDGER rows=… screens=… surfaces=…` 或 `grep -n "data rows" artifacts/evidence/T-2026-10-15-007-green.txt`）；视觉/行为两列按令牌计数，`MEASURED` 与 `REPLAY-SPEC` 各 1（chat 是唯一有档案的屏），其余全为 `NO-PROFILE`；`artifacts/evidence/ux-chat-1to1-measure-2026-09-27.txt` 里 `^== N.` 判据行 8 条，屏档案必需节 7 条全在；`verify_1to1.mjs` 仍在 `artifacts/tmp/`（未收编，属 006）。
- Assumptions: 屏档案里 `record_reference` / `sample_shot` 相对仓库根的路径就是被判对象；由 `neg-*` 用例（记录缺失、样图缺失、缺节）证实这三条分支各给出自己的令牌，不会把「查不到」并成「通过」。
- Unknowns: 阶段 3 之后 Web 侧记录如何命名与挂载（本片只保证台账有地方放它，不预先发明格式）。
- Risks: 「一列里除已测到的那一屏之外全部标着缺证据」容易被读成「还剩 N 个屏没做」——台账表头因此明写每格含义是证据在不在，不是还原对不对；格数本身不写进任何叙述性文字，它随 005 的清单走。
- Deferred: 双环境（Chrome 与移动 WebView）与真机基线截图属采集片；DOM 闭环仪器收编属 006。
- Non-goals: 不做视觉相似度算法、不做 Web 侧代码、不手写台账条目。
- Skipped non-critical questions and reason: 台账是 Markdown 还是 JSON 属呈现细节，选 Markdown（人直接读，机器按行数）。

## Goal

产出一条命令即可重生成的三层判据台账：条目来自 005 的可达性清单，格值来自屏档案与量测记录的实测读数，缺证据是一种取值而不是空格，并让「Android 改了界面而清单没重生成」这件事在台账这里转红。

## Scope

### In scope
- 新增 `.trae/scripts/dev/gen_parity_ledger.py`：读清单 JSON + 屏档案 + 档案指向的量测记录，写 `artifacts/parity/ledger.md`（结构/视觉/行为三列 + 生成命令 + 来源提交号 + 清单哈希 + 退出码），并打印**规格指针**行：登记侧与现场重读侧都要求规格指向本清单，规格不再书写范围数字（2026-10-16 回写轮口径）。
- 新增 `.trae/scripts/dev/parity_ledger_selftest.py`：正反双向自检，含清单过期、行下限、行计数交叉核对、缺证据令牌、退出码四档。
- 防腐判据：用当前源码树重新导出清单的「实测面」哈希并与已落盘清单比对；不一致即判 1 并要求先重生成清单。

### Out of scope
- 修改 005 的清单生成器口径；采集真机截图；实现 Web 侧组件；给视觉/行为下「等价」结论。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 零运行时改动；改变的是「还原到什么程度由谁说」——从每片自述变为一张重生成即回读的台账。既有行为不变：005 的清单与 003 的仪器仍按各自口径出数。
- Producer/consumer/sibling entrypoints and platform branches: 生产者 005（清单 JSON）与 003（屏档案、量测记录）；消费者阶段 1 采集片与阶段 3–6 还原片；姊妹入口 006（DOM 闭环仪器收编后台账的行为列才有本侧记录可读）。
- Native bridge or independent-client compatibility/update requirements: 无桥接。Android 侧改界面 → 清单重导出与已落盘清单哈希不等 → 台账转红，这就是「等 Android 继续改之后仍保持 1:1」的可执行形式。
- Behavior-neutral non-applicability reason, if applicable: 不适用，本片改工具与产物。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无运行时变化；新增「1:1 进度由台账给出」这一当前态产物。
- Authoritative code/config/migration evidence: `gen_parity_ledger.py` 对当前树的实测输出（stdout 判据行 + 台账文件）。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §10（度量方法与防腐条款）、`Docs/intent/INTENT-2026-10-15-web-h5-android-parity.md`、`.trae/ROADMAP.md`。
- Documentation files updated: 规格 §10 的「台账由构造派生」与「防腐烂」两条改为**指向** `artifacts/parity/ledger.md` 与其生成命令，不复述条目数（条目数由 005 的清单指向、台账再指向清单）。
- Producer/consumer/sibling/compatibility paths checked: 反查所有写「逐屏台账」「1:1 台账」的文档位置，统一指向本产物路径；`artifacts/parity/ledger.md` 自身只由脚本写。
- Superseded phrases/examples reverse-searched: 「台账留待人工填写」「按截图人工比对」这一类表述（若存在）。
- documentation impact: none reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 2026-09-27 那份对话页记录是历史证据，台账只引用它「在不在、含几节」，不改写它当年的结论。

## Acceptance criteria

- [ ] 期望行为：`python -X utf8 .trae/scripts/dev/gen_parity_ledger.py` 退出码 0，写出台账，条目数 = 清单屏数 + 浮层数（24），三列各有取值，文件含生成命令、来源提交号、清单哈希、末行退出码。
- [ ] 保持行为：005 的清单自检（19 例）与既有全部守门（`validate_tasks.py`、`checkers_selftest.py`、`check_design_tokens.py`）保持绿；`git diff --name-only -- android` 为空；已落盘清单不被本片改写。
- [ ] 相邻失败：① 清单落盘后源码树再变一屏 → 台账必须打印 `INVENTORY STALE` 且退出码 1；② 台账条目低于字面下限 12 → 退出码 1 且把 measured 与 literal-min 两个数都打出来；③ 清单计数与行数不符、存在未到达屏或未引用布局 → 各自判 1；④ 清单缺失 / 非 JSON / 屏档案缺失 → 一律 3。
- [ ] 受影响入口：缺证据必须各自成令牌（`NO-PROFILE`、`NO-RECORD`、`RECORD-MISSING`、`RECORD-INCOMPLETE`、`SHOT-MISSING`、`NO-REPLAY-SPEC`、`REPLAY-SPEC-INCOMPLETE`），并在汇总里计数；汇总计数必须等于台账条目数。
- [ ] 台账数字全部由捕获输出派生；`--no-write` 不落盘但把同一批判据行打到 stdout；产物 UTF-8 合法；退出码由判定结果派生，不硬写 0。

## TDD execution record

### RED — failing test first
- Test/scenario: `parity_ledger_selftest.py` 的全套正反自检——台账生成器缺席时不得有任何「用例通过」的读数，且必须落在仪器故障那一层而不是用例不符那一层
- Command: `python -X utf8 .trae/scripts/dev/parity_ledger_selftest.py`
- Exit code: 3
- Observed failure: INSTRUMENT FAULT: gen_parity_ledger.py not present next to this script, so no case can run
- Failure reason validated: 成因实测而不是推断。三个读数各管一件事（全部见 `artifacts/evidence/T-2026-10-15-007-red.txt`，七块 want/got 全对、`OVERALL_EXITCODE=0`）：块 A 把 `.trae/scripts/dev/gen_parity_ledger.py` 用 `os.replace` 移到 `artifacts/tmp/_red007/gen-aside.py` 后真跑上面那条命令，得 rc=3、`EXITCODE=3` 收尾行、且 stdout 里 `CASE ` 行数为 0（没有任何用例被跑过，故「红」不可能被读成「断言不符」）；块 B 直接调被移开的生成器，Python 自己报 `can't open file`，说明「跑不起来」与「跑起来判红」在产物里可分；块 D 对照臂在仪器在场时跑同一套夹具，得 rc=0、`cases executed=expected`、`failures=0`、`INSTRUMENT FAULT` 行数 0。缺席基线用「起点必须在场」代替 HEAD 哈希：本片之前该文件从未入库（这一事实在 RED 建立时实测；块 05 现已按「时效性状态只许打印」改为打印行，见 Evidence 段与 Watch-outs ④），HEAD 版本无法作独立基线，注入残留因此不可能被当成本次基线；还原后 sha256 与长度都须等于起点且 `py_compile` 可解析（块 C/收尾实测 `equals_pre=True`）。

### GREEN — minimum implementation
- Implementation: `.trae/scripts/dev/gen_parity_ledger.py`（三层台账：条目取 005 清单、结构列取同一 JSON、视觉列档案指向的量测记录、行为列取回放规格；缺证据是令牌不是空格）与 `.trae/scripts/dev/parity_ledger_selftest.py`（17 例正反自检 + 四档退出码覆盖断言）
- Command: `python -X utf8 .trae/scripts/dev/parity_ledger_selftest.py`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-15-007-green.txt`（七块全部 want=got，`unexpected count = 0`，`OVERALL_EXITCODE=0`）内的 `SUITE executed=17 expected=17 equal=True failures=0 rc=0` / `SUITE tiers seen=[0, 1, 2, 3] instrument-fault-lines=0`；同一文件另含块 01 的生产运行（`production run rc=0 last line=EXITCODE=0`、`android files changed by the working tree = 0`，五个被 watch 文件逐条 `unchanged … -> True`）、块 03 的行数与空格核对（`table header lines=1 data rows=30 inventory screens+surfaces=30`、`empty-cell-count=0`，`MEASURED` 在 cell tally 里计数为 1）、块 04 的现场 HEAD 与清单哈希回读、块 05 的两次重导出与已落盘逐字节相同（唯一允许不同的行是记录本次命令行那一行，且三处各不相同）。**本轮纠正一处引用失实**：本条此前写「块 01 的生产运行（`LEDGER rows=24`…）」，而 `LEDGER rows=` 这个令牌**在该产物里从不存在**（`grep -rn "LEDGER rows" artifacts/evidence/` 命中 0）——它是台账生成器在控制台打印的行，块 01 捕获的是退出码与末行，行数由块 03 以自己的形状打印。这属 AGENTS.md「自述里的每条已加守卫都必须能对上一条 grep」那一族的**引用侧**形态：引号里看着像从证据抄的，实际 grep 不到，读者据此复核会得出「证据缺行」的错误结论。现改为产物里真实存在、且行数随清单走的令牌；`data rows` 的值本轮从 24 变 30，因为 005 的清单浮层数从 10 补到 16（见 005 的 Implementation notes），不是台账改了算法。
- Regression evidence: 三层托底，逐层可回读。① 自检 17 例本身：`EXPECTED_CASES` 是字面整数（不从 `CASES` 派生），少注册一条即判 3；`{0,1,2,3} <= tiers` 的结构断言保证四档退出码都有用例。② `artifacts/tmp/gen-007-mutation.sh` 的变异电池（`artifacts/evidence/T-2026-10-15-007-mutation.txt`，七块全部 want=got）在同深度副本 `artifacts/tmp/_m007/prod/` 上逐条把守卫人为退化，实测每条**只**让自己的用例转红：M1 防腐比对 `if False` → `neg-stale-inventory`；M2 指针比对恒等 → `neg-pointer-drift`；M3 `MIN_ROWS = 0` → `neg-row-floor`；M4 视觉恒 `MEASURED` → `pos-absence-is-a-token-not-a-blank`（连带 `pos-real-tree`，如实声明在期望集里）；M5 仪器恒 `adopted` → `pos-real-tree`；M6 把 FAIL 行扫描退回相邻式 → `pos-fail-line-scan-catches-a-leading-fail`；M7 注释型空变异必须 `caught=False`（否则「任何改动都会红」的电池与「守卫消失才红」的电池读起来一样，前者其实什么都没认证）；M8 把兄弟仪器 `gen_screen_inventory.py` 的装载从 `_instrument_fault` 守卫里提到外面 → `neg-inventory-instrument-absent` 转红（该用例期望 3、退化后得 1，因此这条判据在电池里是**单用例可归因**的）。基线红集由块 02 实测写入 `baseline.json`、块 03 现场读取，不假设「副本必然全绿」；本轮重跑读数 `after-restore copy rc=0 failing=[] counts=('17', '17', '0')`。③ 块 04 用另一双眼睛复算：生产两文件 sha256 等于判定前指纹、副本字节等于生产、`py_compile` 仍可解析；块 05 用运行开始时的生产面快照判「本次运行期间没有位移」（工作树里 004 留下的 `frontend/src/styles/tokens.css` 因此不会被算成本片缺陷）；块 06 现场从 GREEN 与 MUTATION 读用例数并比对，不抄值。

### REFACTOR — optional
- Cleanup: 合成时留下的两处死分支（`... if False else ...`）与把「本次实际参数」读自 `sys.argv` 的错源（被程序内调用时会记录从未发生过的命令行）已在自检 `pos-written-artifact-selfdescribes` 抓到后改掉；判据取值文案（`STALE`、`一致`）按实测核对。
- Regression evidence: REFACTOR 阶段的两次真实回归都由变异电池在建电池时**顺带**抓到，两处都已修并各有用例与变异各自托底：① 宣称比对原本写作 `fresh["claims"] == inv["spec_claims"]`（该函数与键名已在 2026-10-16 回写轮改为 `link_disagree` / `LINK_KEYS`，本条保留旧名是因为它记录的是当时的缺陷形状），把「在哪里读到这两个数」（`spec_claims.source`）也算进宣称——实测在副本树上直接打印 `FAIL CLAIM DRIFT`，即任何一次换目录的检出都会被判成「规格与清单互相不同意」，而这条判据要守的是数字不一致。现收敛为 `claims_disagree()` 只比 `CLAIM_KEYS = ("screens", "surfaces")`；用例 `pos-drift-ignores-the-source-path` 把清单里的 source 改成外来路径后要求仍判 0，变异 M2 把该函数退化为恒等即转红。**回写轮把这条判据的承重对象换成了指针**：只比 `LINK_KEYS = ("inventory_cited",)`，`count_shaped_lines` 的行号刻意不比（行号随任何改写文档的动作位移，比它等于把「时效已到」写成「回归」），并把「规格指针缺失」拆成一条**独立**判定；`neg-pointer-drift` 因此做正交拆分（改登记侧只有 DRIFT 红、改规格侧只有 MISSING 红），M2 的锚点与见证用例名同步。② 自检的「没有 FAIL 行」原写作 `"\nFAIL " not in out`，而 `--no-write` 把判据行打在最前面，于是**首行**就是 FAIL 时该检查恰好看不见它——副本上实测打印 `check no FAIL line printed ok` 而同一次运行的 rc=1、stdout 第一行就是 `FAIL CLAIM DRIFT`。现改为按行形状定位的 `fail_lines()`，用例 `pos-fail-line-scan-catches-a-leading-fail` 用「FAIL 位于第一行」这个真实形状作正控，并把旧的相邻式一并断言为「当时确实漏检」；变异 M6 退回相邻式即转红。两条同属既有规则的形态：判据被它本该拦住的东西绕过。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: 分片（不是例外）——见 Implementation notes 的实测规模。TDD 顺序的实情如实登记在该小节：实现先于分片决定存在，RED 是把文件移开原位后**真实重跑**得到的，不是补写的。
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 规模（判定值一律由仓库自带工具给出，不手搓过滤）：`python -X utf8 .trae/scripts/dev/count_production_lines.py .trae/scripts/dev/gen_parity_ledger.py` → **299**（判定值，本文件 `estimated_production_lines` 即取此值；本值由 2026-10-16 回写轮从上一读数抬升，原因是宣称比对改成指针比对（`CLAIM_KEYS`→`LINK_KEYS`）并新增「规格指针缺失」这条独立判定；本片早先申报过一个较小读数，它随「把兄弟仪器 `gen_screen_inventory.py` 的装载移进 `_instrument_fault` 守卫」那次改动过期——按本条命令回读即可复现，不在此处并列旧值，理由见下一条）；`parity_ledger_selftest.py` 按「测试不计入」排除（与 002 排除 `checkers_selftest.py`、003 排除 `parity_selftest.py` 同一口径）。本片从 005 分出来的依据是实测而不是偏好：同一命令对 005 的清单生成器另有一个读数（登记在 005 的规模小节，本片不复述——对岸的申报值由对岸回读，抄进第二处就会过期，本轮已实际发生一次），两个读数相加越过 500 硬上限；分片后两片各自落在 200–500 的 verifier 警告带内。非判定口径留档（不参与任何结论）：同一文件按「非空且不以 `#` 起首」手工统计给 275——差的行是缩进的 `#` 注释与 docstring 收尾行，仓库工具对 `.py` 只做 Java/XML 的注释剥离，因此判定值把注释行也算进去。该口径问题是本仓库既有的待裁决项，本片不擅自修改工具。
- 该判定值在本文件只出现两处：front matter 的 `estimated_production_lines` 与上一条的复算命令读数。判据增加时它会变，因此其余位置不再复述（抄进第二处必然过期，且过期方向不可控）；同一理由，本片的 sha256 与字节数一律写复算命令，不写值。
- 清单 JSON 是生成物，不计入判定面（与 004 排除 `tokens.css`「生成器产物、可逐字节重生成」同一口径）；这条排除是承重的，去掉它判定值会再涨两千余行。
- 防腐比对的口径：比的是「实测面」哈希（`schema_version / screens / surfaces / components / layout_census / unreferenced_layouts / unreached_screens / counts`），**不含** `source_commit` 与 `generated_by`。理由写在脚本 docstring 里：含进去等于任何一次无关提交都把台账判红，而 §10 要守的是「界面改了」。副作用如实登记：如果有人在同一提交里既改了界面又重生成清单，台账不会红——那是正常流程该做的事，红也没意义。
- 屏档案键名约定：屏行取「类名去掉 Screen 后缀并小写」，浮层行取布局名小写。这既是 003 档案（`chat`）的现况，也让「档案里没有这个键」成为可判定的取值；`ChatScreen → chat` 命中即 `MEASURED`，其余行 `NO-PROFILE` 而不是空格（行数随 005 的清单，本条不钉数）。
- 视觉列读的是**记录在不在**：档案的 `record_reference` 指向的文件里数 `^== N.` 判据行，再与 `require_sections` 对照（当前对话页记录实测 8 条、必需 7 条全在）。这一步不共享清单解析器的状态，是独立判据。
- 行为列读的是回放规格（`prototype.clicks` / `dom_checks` / `wait_for`）在不在，并打印 `verify_1to1.mjs` 的收编位置（现在仍是 `not-adopted:artifacts/tmp/…`，属 006）——仪器没入库，这一列就不许出现「通过」字样。
- 自检夹具复用 005 的 `build()`（`importlib` 载入，不 fork 第二份夹具构造器）：同一形状在两处各写一份，正是本仓库「同一条规则写在两个入口」那一族的形态。
- 仪器缺席必须是**打印出来的**仪器故障，不是 traceback：`_load()` 找不到被判脚本时返回 None，`main()` 第一道就打印 `INSTRUMENT FAULT: … not present …` 并返回 3。这样 RED 那一轮（把 `gen_parity_ledger.py` 移到暂存位置后真跑）留下的是可回读的判据行，而不是一段没有退出码的栈。
- **同一类守卫在两片里的归因通道不同，这是实测而不是设计**：本片的兄弟仪器装载被提进 `_instrument_fault` 之后，`neg-inventory-instrument-absent` 期望 3、守卫退化后得到 1，所以 M8 是电池里一条**单用例可归因**的变异；而 005 那边的 dead-include 守卫被拆掉时抛的是 `KeyError`，落在用例之外、直接把整套自检打到 3 档并打印 `INSTRUMENT FAULT in case neg-dead-include`，因此**不能**折进电池的红集，005 用独立块量测（见 005 的 MUTATION 块 04）。规则相同、结论不同形的两半都要留档，否则下一片会把「守卫拆掉一定让某条用例红」当成前提。
- 退出码四档在自检里都有用例对上（现 17 例，`EXPECTED_CASES` 是与 `CASES` 无关的字面整数）：`pos-*` 七条给 0、`neg-row-floor` / `neg-stale-inventory` / `neg-pointer-drift`（原名 `neg-claim-drift`）/ `neg-rows-mismatch` / `neg-unreached-screen` 五条给 1、`neg-unknown-arg` 给 2、`neg-missing-inventory` / `neg-inventory-not-json` / `neg-missing-profile` / `neg-inventory-instrument-absent` 四条给 3（7+5+1+4=17，与 `EXPECTED_CASES` 现场读数一致；本条上一版写「六条给 0…三条给 3」，分项之和 15 与它自己声明的 16 不相容——是「自述里的数字与同一句话里的另一个数字互不相容」那一族的实测命中，根因是补用例时只改了总数没改分项，因此分项现在由 `grep -n "@case(" ` 逐条回读而不是顺手写）。`main()` 里另有「用例集必须覆盖 0/1/2/3 四档」的结构性断言，缺档即判 3。`neg-claim-drift` 与两条 `pos-` 新用例是先前轮次补上的覆盖缺口：前者补「宣称漂移」判据此前**没有负向用例**的洞（该判据在回写轮更名为 POINTER DRIFT 并换承重对象，用例名随之改为 `neg-pointer-drift`），后两条见 REFACTOR 小节；`neg-inventory-instrument-absent` 是 005 那轮为「兄弟仪器缺席」新增的一档用例。
- 变异电池跑在副本上而不是跑在生产文件上：`.trae/scripts/dev/` 深度为四，副本沿用**同样的相对路径段数**（`artifacts/tmp/_m007/prod/.trae/scripts/dev/…`），因为被测脚本按自身位置上溯算仓库根——第一次尝试把副本放 `mktemp -d` 时算出的根跳出仓库、夹具读不到、stdout 空而 rc=1，红得合理却是假见证（既有规则）。副本因此**留在仓库内**，`git rev-parse HEAD` 才仍然可用；副本根因此同时是 `REPO`，所以清单、屏档案、量测记录、样图与 `artifacts/tmp/verify_1to1.mjs` 都要按仓库相对路径复制过去（`pos-real-tree` 会去 `artifacts/tmp/` 找那支未收编的仪器，缺了就打印 `absent` 而不是 `not-adopted:…`，用例即红——这是建基线时实测到的，不是猜的）。副本绿与否由块 02 **实测**后写文件，块 03 读它，不假设基线必绿。
- 已知局限（如实登记）：① `neg-rows-mismatch` 与 `neg-unreached-screen` 同时会命中防腐判据（改计数就是改实测面），所以那两条用例断言的是**自己那条消息出现**，不假设别的消息不出现；② 台账不判断「像不像」，它只保证「哪一格没有证据」是可见的计数；③ `projection()` 的字段清单与 005 的 `render()` 是**约定耦合**，005 若新增被打印的字段而不同步这里，实测面比对会把它当成差异转红（方向安全，但要在 005 变更时回读本片自检）。

## Verification report

- Verdict: PENDING — 判决只能由独立 verifier 复跑后给出（与 004/005 同一做法：实现者自述不充当 PASS，`status` 因此停在 `in_progress`）。实现者侧可确证的部分：三份证据的总退出码都由 want/got 期望表派生、`unexpected count = 0`、`OVERALL_EXITCODE=0`，且三份都在 2026-10-15 因仪器变更整体重跑过一次（旧捕获记的是 16 例 / 7 条变异，与当前仪器字节不一致，沿用即假见证）。
- Evidence: `artifacts/evidence/T-2026-10-15-007-red.txt`（7 块）——块 02 的 `A absent selftest rc=3` / `RESTORE equals_pre=True equals_after_len=<N>/<N> parses=True`（byte-exact 还原；N 是字节数，按「指纹类值只写复算命令不写值」不抄进正文，回读 `grep -n "RESTORE equals_pre" artifacts/evidence/T-2026-10-15-007-red.txt`）证明 RED 是**真的把被判生成器移开**得到的、且还原 byte-exact；块 04 对照臂 `CONTROL executed=17 expected=17 equal=True failures=0 tier3-by-design=4` 与 `CONTROL verdict=… instrument-fault-lines=0 rc=0`，说明 3 档那四条是**设计的**而不是仪器崩了；块 05 本轮**由判据改为打印**（与 005 的 RED 块 05 同一处置、同一理由）：它原先要求三个产物 `commits=0 tracked=False`，片进入历史后按定义转红，现在打印 `info <路径> commits=… tracked=… (printed not judged …)` 且总退出码与之无关；「产物以前不存在」这件事的可复现路径是块 02/03 的移开—还原实验，与提交状态无关。`artifacts/evidence/T-2026-10-15-007-green.txt`（7 块）——`SUITE executed=17 expected=17 equal=True failures=0 rc=0`、`SUITE tiers seen=[0, 1, 2, 3] instrument-fault-lines=0`、`production run rc=0 last line=EXITCODE=0`、`table header lines=1 data rows=30 inventory screens+surfaces=30`、`empty-cell-count=0`、`live HEAD length=40 matches the ledger line=True`、`re-export equals the filed artefact on every other byte=True`、`android files changed by the working tree = 0`。`artifacts/evidence/T-2026-10-15-007-mutation.txt`（7 块）——`baseline read from the measured file: rc=0 failures=0 failing=[]`、`mutations declared=8 floor=8 floor-met=True`、八条变异逐条 `caught=… new-red=[…] expected-new=[…]` 全等、`after-restore copy rc=0 failing=[] counts=('17', '17', '0')`。三份的末行 `DECODE ok file bytes=N` 由生成它们的那次运行的 UTF-8 闸门现场打印，N 不抄进正文（回读 `tail -1 artifacts/evidence/T-2026-10-15-007-<red|green|mutation>.txt`）。**本轮新增下限的实测托底**：把 `MIN_MUTS` 从 8 改成 9 后同一电池立即 `mutations declared=8 floor=9 floor-met=False` + FAIL 行并以 **rc=3** 退出（复算命令：`sed 's/^MIN_MUTS = 8$/MIN_MUTS = 9/' artifacts/tmp/_m007g/battery.py > artifacts/tmp/_m007g/_floorprobe.py && python -X utf8 artifacts/tmp/_m007g/_floorprobe.py; echo rc=$?`）——下限不是装饰，它少一条变异就当场中止。
- Changed/deleted assertion rationale and equivalent behavior coverage: 除 2026-10-16 回写轮那一处**明示的口径变更**（见本条末尾③）之外未删除任何既有断言，另外两条判据被**加固**而未改口径：① `gen_parity_ledger.py` 装载兄弟仪器 `gen_screen_inventory.py` 的语句从裸 `try` 移进既有 `_instrument_fault` 守卫（判据不变，变的是通道：缺席落在 3 档并由 `neg-inventory-instrument-absent` 托底、变异 M8 托住守卫本身）；② 电池新增与声明清单无关的裸字面量下限 `MIN_MUTS = 8`（此前只靠 WANT_TEXT 逐条点名，删掉一条变异会让电池「少验一项而全绿」——这是本仓库「匹配集为空必须判 FAIL」那一族在变异电池上的形态）。既有 16 例改 17 例只做**增加**，`EXPECTED_CASES` 仍是字面整数，旧 16 例的期望与结论逐条未动，其「仍然全绿」由块 02 的逐条比对（`equal=True failures=0`）而非人眼认证；台账列的取值令牌与屏档案键名约定未改动；③ 防腐比对在 2026-10-16 回写轮由 `CLAIM_KEYS = ("screens", "surfaces")` 改为 `LINK_KEYS = ("inventory_cited",)`，这是**口径变更**而非加固：被删掉的是「从规格正文正则读出两个范围数字并与实测做差」这条判定，删掉它的理由是承重对象变了——规格不再持有范围数字（用户 2026-10-16 裁定「以实测清单为准」，数字的唯一权威是清单的 `counts`），因此没有第二个数字可比。替代覆盖有两层：`inventory_cited` 并入退出码（指针缺失即 rc=1，由 `neg-pointer-drift` 的正交拆分与变异 M2 托底），以及 `SPEC_RESTATED` 行把规格里残留的「N 个屏幕」式形状连同**行号打印出来**交由人来标注是否为历史引用（刻意不并入退出码：行号随任何文档改写位移，把它当判据等于把「时效已到」写成「回归」）。该片等价性证据是块 03 的 `empty-cell-count=0` 与块 05 的逐字节重导出，二者在新口径下重跑仍为真。
- Gaps: Web 侧记录命名与挂载方式待阶段 3 定；双环境（Chrome / 移动 WebView）与真机基线属采集片；规格拍板范围与实测清单的差异已由用户 2026-10-16 裁定「以实测清单为准」并在 005 侧关闭（该片同时发现并修复了仪器少计浮层的真缺陷），本片只消费清单、不复述数字。
- Risks: 台账每轮重生成，若有人手工改 `artifacts/parity/ledger.md` 会被下一次生成覆盖——这是设计意图，但需要 006 之后把它接进提交钩子才算有强制力。

## Handoff notes

- Completed: 台账生成器与 17 例正反自检落地；`artifacts/parity/ledger.md` 已由脚本生成（条目行数由 005 的清单决定、本片不复述，视觉/行为两列按令牌计数，末行退出码）；RED / GREEN / 变异电池三份证据各自自带命令行与退出码且全部 want=got；电池顺带抓出的两处真实回归（宣称比对含路径、FAIL 行扫描按相邻字符判定）已修并各有用例与变异托底。【回写 2026-10-16：005 那轮把浮层词表补全后，本片 `parity_ledger_selftest.py` 的 `pos-real-tree` **转红了一条**，而根因不是台账算法——它把 `rows=24` 与 `"NO-PROFILE": 23` 两个**属于 005 清单的数字**抄进了自己的断言，清单一变就必然过期。处置是把这两个期望改为现场从 `Docs/04-ux-design/parity/screen-inventory.json` 读出 `len(screens)` / `len(surfaces)` 再算 `rows`，profiled 数（唯一有档案的屏）留在测试里并写明它是本测试自己引入的前提；改完 `cases executed=17 expected=17 failures=0 / VERDICT: PASS`，且**没有**把 24 改成 30 了事——那只是把同一个复述推到下一个过期点。这是 AGENTS.md「自述里的数字与另一处同源数字互不相容」「同一条规则两处正文」在**测试断言**上的形态：机械同一性必须只有一个来源。同轮把 005 的 RED 块 05 那类「提交前才成立」的判据在本片 RED 块 05 一并改为打印（理由与处置同 005），三份证据全部重跑并 `OVERALL_EXITCODE=0`，另纠正本文件 Passing evidence 里一处 grep 不到的引用（见该条末尾）。】【回写 2026-10-15：005 那轮为「兄弟仪器缺席」改了本片的装载守卫并新增 `neg-inventory-instrument-absent` 与变异 M8，因此三份证据全部重跑过——旧证据记的是 16 例、7 条变异，与本片的当前仪器字节不一致，沿用即假见证。重跑读数：RED 块 04 `CONTROL executed=17 expected=17 … tier3-by-design=4`、GREEN 块 02 `SUITE executed=17 expected=17 equal=True failures=0 rc=0`、MUT 块 03 八条变异逐条 `new-red==declared`、三份均 `unexpected count = 0` 且 `OVERALL_EXITCODE=0`。同一轮还发现本片的用例分项之和（旧写 6+5+1+3=15）与它自己声明的总数 16 不相容，已按 `@case(` 逐条回读改为 7+5+1+4=17。】
- In progress: 无。本片交付面已完成，状态停在 `in_progress` 等独立 verifier 复跑；005 的规模申报同步（合并判定值 = 两片各自 `count_production_lines.py` 读数相加，两个读数分别登记在两片自己的规模小节，谁也不复述谁）与 006 的仪器收编是后续片。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: ① 独立 verifier 只读复跑；② 006 收编两支 Node 仪器后，行为列才可能从「回放规格在不在」升级为本侧记录；③ 阶段 1 采集片按台账行排队取夹具。
- Watch-outs: ① 别把台账的 `MEASURED` 读成「已还原」——它只说 Android 侧基线记录在位，表头那条「每格的含义」就是为此而写；② 改 005 的清单字段时回跑本片自检（`projection()` 的耦合）；③ 不要为了少几格缺证据去伪造屏档案条目。④ **本片 RED 块 05 的时效问题本轮已就地处置**（与 005 同一做法、同一理由）：它原先判「三个产物 `commits=0 tracked=False`」，片进入历史后按定义转红；现改为打印行 `info <路径> commits=… tracked=… (printed not judged …)`，`bash artifacts/tmp/gen-007-red.sh` 因此在任何一次克隆上都能整跑到底。「仪器缺席必须判 3」的可复现证据是块 2 的移开—还原实验与块 6 的档位对照，两者都与提交状态无关。⑤ 台账自检的 `pos-real-tree` **不得再抄清单数字**：本轮它因写死 `rows=24` / `NO-PROFILE: 23` 而在 005 补词表后转红，正确处置是现场从 `G.OUT_DEFAULT` 读 `len(screens)` / `len(surfaces)` 再算（已改），把「唯一有档案的屏」这类**本测试自己引入的前提**留在测试里。看到这类红时把它改回一个新数字，等于把同一颗定时炸弹换个日期重新埋好。
