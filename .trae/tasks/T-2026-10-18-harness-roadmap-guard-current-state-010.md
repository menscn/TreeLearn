# Treelearn Harness Task: 路线守门的 AGENTS.md 路由判据改判当前态

id: T-2026-10-18-010
title: 路线守门的 AGENTS.md 路由判据改判当前态
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-15-web-h5-android-parity-requirement-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 95
shared_code_touched: true
depends_on: [T-2026-10-15-web-parity-doc-authority-writeback-002.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: ROADMAP 已知欠账 D1。`.trae/scripts/check_roadmap_and_routing.py` 有三条判据取自 `git diff --unified=0 -- AGENTS.md` 的**新增行**，于是「AGENTS.md adds >= 4 routing rows」「new routing rows cover all four triggers」只在四行路由尚未提交的窗口内可能为真；路由进入历史后 `git diff` 为空，两条各以 `measured=0` 与 `missing=[四个触发词全缺]` 判红，而这四行在文件里逐字都在。第三条同族判据 `every new routing-row target exists` 更隐蔽：增量为零时它的被检集合为空、恒打印 `OK — measured_dead=[]`。后果不只是门禁常红——`.trae/scripts/dev/checkers_selftest.py` 把该守门列为基线，基线不绿即以 rc=3 中止，于是本仓库唯一的文档类守门双向自检**长期一跑就崩**，49 条注入用例从未执行过（004 已把这条如实登记为债务）。
- Users/scenarios: 任何跑 `.trae` 门禁的人（verifier、dev 自检、新会话选题）；以及任何想改 AGENTS.md 路由表而不被一条过期判据挡住的人。
- Expected outcome: 路由判定改判**当前文件全文**（行数下限 + 四个触发词覆盖 + 链接目标存在，三条各有非空匹配集下限），「本次改动新增了几行」降级为打印并写明基线与窗口，空窗口打 `SKIPPED` 而不打 `OK`；`checkers_selftest.py` 因此能跑完并且每条注入用例都实测判红，含四条新加的「拆掉这条守卫必须响」的用例。
- Core flow and reversibility: 守门脚本与共享库改判据 → 电池补用例并跑全绿 → 消费端（004/005/007 的 GREEN 生成器里那条姊妹判据）先做档位中性化 → ROADMAP D1 闭合回写 → RED/GREEN 两份产物；整片只动 `.trae/scripts` 三份文件加文档，不改 Android/前端/后端产物，可整体回退。
- Inputs/outputs: 输入是 `.trae/ROADMAP.md`、`AGENTS.md`、`.trae/README.md` 的当前字节与 `git show HEAD:AGENTS.md`；输出是逐条 OK/FAIL/NOTE 判决行与 `EXITCODE=`，以及两份 UTF-8 证据产物。
- State and exception handling: 守门退出码沿用既有两档（0 全成立 / 1 结构不符）；本片的仪器产物另用四档（0/1/2/3），其中「归档守门跑不出旧判决」「镜像盘建不出来」一律落 3，不得压成 0 或 1。空匹配集必须判 FAIL，不写 PASS。
- Constraints and trade-offs: 不删除 AGENTS.md 既有行（该文件受本守门的 append-only 约束），因此历史里那条「当前 `EXITCODE=1`」的时点读数只能在 ROADMAP 里加括注、不能回改；`MIN_ROUTE_ROWS` 必须是与清单无关的裸字面量，否则「用被检清单算自己的下限」等于没有下限（该缺陷本仓库已栽过三次）。
- AI recommendation and rationale: 选 ROADMAP D1 列出的修法 ①（改判当前态），而不是 ②（把窗口判据降级为只打印并另立一条当前态判据）或 ③（接受常红）——因为只有当前态判据有永久强制力，且它让那条一直被打死的电池第一次能跑完。
- User choice and custom additions: 用户在 2026-10-17 进度复核后选择「按你的来」，其中第 1 条即本片的修法 ①；四条路由行入库这一事实由已确认范围覆盖，不重复询问。
- AI-autonomous decisions: ① 下限字面量取 12（当前读数 20 的一半以下、且明确小于表内实际行数，留出行数减少时的出声空间）；② 窗口形状改为「added 与 removed 皆空 → `NOTE SKIPPED`，否则判 no-deletion」，并把基线 `base=<rev-parse> window=uncommitted-only` 写进每条见证行；③ 电池里对「增量为零时旧判据恒 OK」不再用 `git_checkout` 造形（那要求仓库处于特定 git 状态），改为四条直接注进当前态判据的用例（抬下限、截表、丢触发词、指死目标）。
- User-confirmation decisions: 无新增待确认项。
- Acceptance evidence: 守门对今天的树 `EXITCODE=0` 且三条当前态判据的匹配集非空（复算 `python -X utf8 .trae/scripts/check_roadmap_and_routing.py`）；电池跑完并绿（复算 `python -X utf8 .trae/scripts/dev/checkers_selftest.py`，条数以它自己打印的 `SELFTEST cases=` 为准，本卡不复述——它随用例增删位移）；四条 `roadmap:routing-*` 用例各判红一次；RED/GREEN 两份产物各自绿、总退出码由 want/got 表派生。
- Facts: 四行路由已在历史里（`git log --oneline -- AGENTS.md` 可见），AGENTS.md 失败案例库 `[2026-10-16] [guard]` 一条已把该缺陷与「第 163 行起」的位置写下；004 的 Gaps ② 登记了「电池因此落 rc=3」这条债务；修复前的守门与共享库字节已归档为本片自己的 `artifacts/evidence/T-2026-10-18-010-pre-fix-*.py` 两份副本，RED 产物靠运行它们复现缺陷。
- Assumptions: 归档副本与当前文件确实分属修复前后两态（RED 块 01 判这一点，不成立判 3）。
- Unknowns: 若将来把路由表拆到 `.trae/rules/` 下多个文件，`MIN_ROUTE_ROWS` 该按哪个口径设——本片不预测，只在守门注释里写明该常量的口径是「AGENTS.md 单文件内的带链接行」。
- Risks: 当前态判据不看「谁新增的」，因此一次误删整表仍会因行数仍 ≥ 下限而绿——由「HEAD 的每一行必须逐字存活」那条判据（本片保留、且它读 `git show HEAD:` 而非工作树快照）与四条注入用例共同兜住。
- Deferred: D2–D8 其余欠账；007 只读复核提的另两处空见证形状（本任务 #7）。
- Non-goals: 不改 ROADMAP 的条目内容与其它判据、不改 AGENTS.md 既有行、不动 002 已入库的 GREEN 证据块。
- Skipped non-critical questions and reason and evidence, if applicable: 不适用。
- Skipped non-critical questions and reason: 判据行的中文措辞与 `NOTE` 行的字段顺序不影响验收，由实现者定。

## Goal

`.trae/scripts/check_roadmap_and_routing.py` 对 AGENTS.md 路由的三条判据改为按当前文件全文判定且各有非空匹配集下限，「本次改动形状」降级为打印，`.trae/scripts/dev/checkers_selftest.py` 因此能跑完全绿并让每条注入用例实测判红。

## Scope

### In scope
- 守门：删掉 `MIN_NEW_ROUTES`，新增裸字面量 `MIN_ROUTE_ROWS` 与 `routing_rows()` 助手；三条当前态判据（行数下限、四个触发词覆盖、链接目标存在且集合非空）；窗口块改为 `base=… window=uncommitted-only` 的打印 + 空窗口 `NOTE SKIPPED`；保留「HEAD 每一行逐字存活」判据。
- 共享库 `harness_check_lib.py`：新增 `Checks.note()`——只打印不参与判定的行，用于「没有可判对象时不得打 OK」。
- 电池 `checkers_selftest.py`：移除依赖特定 git 状态的 `git_checkout` 用例，新增 `drop_matching` 操作与四条当前态注入用例；正控现场从被判文本派生，不手抄。
- 消费端中性化：004/005/007 三支 GREEN 生成器里那条「姊妹守门 checkers_selftest」判据改为档位现场读、只打印不断言（本片改判据会让姊妹的固定档位预期反转，那是谱系缺陷不是缺陷复发）。
- 文档回写：ROADMAP D1 标为已按修法 ① 闭合并给复算命令；004 的 Unblock condition 记录条件已成立（不改写它的 PARTIAL 判定）；005 的一条 Watch-out 把「现 EXITCODE=1」标注为时点读数。
- 证据：`artifacts/tmp/gen-010-d1-red.py` / `gen-010-d1-green.py` 两支生成器与对应两份产物（UTF-8、CR=0、含命令与退出码、总退出码由 want/got 表派生、正文不含提交号形状的裸十六进制词）。

### Out of scope
- 不重跑 `gen-002-green.sh`（其块 06/07 是修复前的捕获，作为历史保留并在 ROADMAP 里显式声明未随之变更）。
- 不改 Android / 前端 / 后端任何生产代码，不改路由表内容本身。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变化是「判据基线从工作树-vs-HEAD 增量换成当前文件全文」。保留行为：ROADMAP 侧全部判据（Phase B/C 条目数、字段名、提示词长度、开源来源格式、占位符、README 目录结构）一字不改其判定；AGENTS.md 的 append-only 语义在**有增量时**仍判删除行；四个触发词的覆盖要求不变，只是判定对象换成全文。
- Producer/consumer/sibling entrypoints and platform branches: 生产者是被改的守门与共享库；消费者是 `.trae/scripts/dev/checkers_selftest.py`（基线）、各片 GREEN 生成器里的姊妹判据（004/005/007）、以及 `harness_check_lib` 的其它使用者（`check_intent_capturer_agent.py`、`check_intent_template.py`、`check_spec_author_agent.py`、`check_stack_facts.py` 等——新增 `note()` 是纯增量方法，不改任何既有函数签名）。平台分支：Windows 与 Git Bash 下 git 调用一律带 `-c core.quotepath=false` 的既有做法不变。
- Native bridge or independent-client compatibility/update requirements: 不适用，无桥接。
- Behavior-neutral non-applicability reason and evidence, if applicable: 不适用——本片改变门禁判定存在性，需按行为变更取证。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 守门对 AGENTS.md 的判据基线；`note()` 这一新的「只打印」通道；文档类守门自检的可运行性。
- Authoritative code/config/migration evidence: `.trae/scripts/check_roadmap_and_routing.py`、`.trae/scripts/harness_check_lib.py`、`.trae/scripts/dev/checkers_selftest.py` 当前字节与两份产物。
- Documentation surfaces checked: `.trae/ROADMAP.md` 的「已知欠账」D1 段与小节导语（导语原写「读数取 002 的 GREEN 证据块 05/06/07/11」，对 D1 已不成立）、`AGENTS.md` 失败案例库 `[2026-10-16] [guard]` 与 `[2026-10-16] [harness]` 两条（append-only，不回改）、`.trae/tasks/T-2026-10-15-css-token-generation-and-bare-color-guard-004.md` 的 Gaps ②/Unblock condition、`.trae/tasks/T-2026-10-15-screen-inventory-parity-ledger-005.md` 的 Watch-outs、`.trae/README.md` 的门禁清单。
- Documentation files updated: ROADMAP D1 加「已按修法 ① 闭合」标题、处置/回读两条与导语括注；004 卡 Unblock condition 记条件成立；005 卡一处时点读数加括注；AGENTS.md 失败案例库**追加**一条 [2026-10-18] [tooling]（形状类判据只喂单向样本 → 合法输入上也响；该文件受 append-only 约束，因此只追加不回改既有任何条目）。
- Producer/consumer/sibling/compatibility paths checked: 反向搜索 `MIN_NEW_ROUTES`、「AGENTS.md adds >=」、「new routing rows cover」——`.trae/scripts/**` 与三支姊妹生成器的活动引用必须清零，历史捕获（`artifacts/evidence/` 旧产物、`artifacts/tmp/_*` 临时件、已入库任务文本的引号内旧标签）按当时状态保留。
- Superseded phrases/examples reverse-searched: 「AGENTS.md adds >= 4 routing rows」（作为判据）、`MIN_NEW_ROUTES`、「判据在今天的树上必然红」。
- documentation impact: none reason and evidence, if applicable: 不适用。
- Historical/PRD handling: AGENTS.md 与已入库任务卡里的旧 FAIL 标签是带日期的缺陷记录，只追加现状、不改写当时判定。

## Acceptance criteria

- [x] 期望行为：`python -X utf8 .trae/scripts/check_roadmap_and_routing.py` 在当前树上 `EXITCODE=0`，三条当前态判据各打印 `measured=`，且路由行数、触发词缺失集、链接目标数三者都落在「非空且有下限」的形状上。
- [x] 保持行为：ROADMAP 侧判据、README 目录结构判据、`no placeholder tokens in ROADMAP` 与「HEAD 每一行逐字存活」判据的结论与修复前一致；`git diff` 有增量时删除行仍判 FAIL；新增 `note()` 不参与任何判定。
- [x] 相邻失败：把 `MIN_ROUTE_ROWS` 抬到超过当前读数必须判红且打印真实读数；截断路由表必须判红；丢掉一个触发词必须判红；把一条链接指向不存在的目标必须判红；增量为零时不得出现「什么都没判却打印 OK」。
- [x] 受影响入口：`checkers_selftest.py` 跑完并 `OVERALL: PASS`、`EXITCODE=0`；四条 `roadmap:routing-*` 用例逐条判红；004/005/007 三支 GREEN 生成器的姊妹判据不再钉死档位。
- [x] 证据文件自证（命令 + 退出码 + 判定前指纹与同窗口位移），UTF-8 合法且 CR=0，正文无提交号形状的裸十六进制词，总退出码由 want/got 期望表派生且判定块条数有与块清单无关的裸字面量下限。

## TDD execution record

### RED — failing test first
- Test/scenario: 承诺的形状是「守门对今天的树绿，且文档类守门双向自检能跑完」。修复前它给不出这个结论：守门两条判据以 `measured=0` / 四触发词全缺判红，第三条在空集合上恒打印 `OK — measured_dead=[]`；把这份守门当基线的电池因此 abort。RED 用**修复前的归档字节现场重跑**复现这三条形状，并另起一个镜像盘把真实电池喂给它，量测「基线红 → 用例一条没跑」。
- Command: `python -X utf8 .trae/scripts/dev/checkers_selftest.py`
- Exit code: 3
- Observed failure: 基线阶段就中止——`BASELINE FAIL check_roadmap_and_routing rc=1` 之后没有一条 `CASE` 判决行，末行 `OVERALL: INCONCLUSIVE` / `EXITCODE=3`
- Failure reason validated: 不是「实现有缺陷」而是「判据基线已过期」：四行路由已入库，`git diff` 对 AGENTS.md 为空，而判据要的是增量。该结论不靠记忆也不靠本卡的自述——`python -X utf8 artifacts/tmp/gen-010-d1-red.py` 的块 02 直接运行归档守门字节并把三条判决行逐字打进产物，块 03 在镜像盘里跑**真实**电池并把 `rc=3 / BASELINE FAIL 行=1 / CASE 行=0 / OVERALL: INCONCLUSIVE` 四个读数打进产物；块 01 判「归档存在、与当前字节不同、三处旧标签只在归档、三处新标签只在当前文件」，不成立判 3 而不是 0（把「还没归档」读成「归档过且没问题」是假见证）。产物 `artifacts/evidence/T-2026-10-18-harness-roadmap-guard-current-state-010-red.txt` 汇总行 `blocks=4 floor=4 expectation_entries=4 faults=0`、`OVERALL_EXITCODE=0`（此处 0 = 缺陷如实复现，判读约定写在生成器头注）。本条 Command 的 rc=3 在修复前的树上可复算；今天复算同一命令给出的是修复后的 0，那是本片要修的正是它的反面——两者的差由 GREEN 块 04 与 RED 块 03 分别托底。

### GREEN — minimum implementation
- Implementation: 守门把三条判据的判定对象从 `git diff --unified=0 -- AGENTS.md` 的新增行换成 AGENTS.md **全文**：行数下限（`MIN_ROUTE_ROWS` 为与那张表无关的裸字面量）、四个触发词在现行里的覆盖（`measured_rows=… missing=[…]`）、每条链接目标存在且集合非空（`measured_targets=… dead=[…]`，空集合不再算通过）；`MIN_NEW_ROUTES` 与两条增量标签删除，「本次新增几行路由」降级为 `NOTE routing-row increment is printed, not judged`；窗口块每条见证行带 `base=<rev-parse> window=uncommitted-only`，无增量时打 `NOTE SKIPPED …` 而不打 `OK`。共享库新增 `Checks.note()`（只打印、不参与判定）。电池删掉依赖 git 状态的 `git_checkout` 用例，加 `drop_matching` 操作与四条 `roadmap:routing-*` 注入用例。三支姊妹 GREEN 生成器的姊妹判据改为档位现场读。ROADMAP D1 就地标闭合并给复算命令。
- Command: `python -X utf8 .trae/scripts/dev/checkers_selftest.py`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-18-harness-roadmap-guard-current-state-010-green.txt` 块 01 的守门读数 `守门 rc=0 结论行=0` 与三条 OK 行（本轮 `measured=20`、`measured_rows=20 missing=[]`、`measured_targets=19 dead=[]`，下限本轮为 12——三个值都随 AGENTS.md 位移，复算 `python -X utf8 .trae/scripts/check_roadmap_and_routing.py`）；块 04 的电池汇总行 `SELFTEST cases=52 ok=52 mismatch=0 harness_errors=0 skipped=0 基线绿=4 OVERALL:PASS=1 末条退出码行=0`（条数权威是它自己打印的 `cases=`，本卡不复述条目数——它随用例增删过期；定位 `grep -n "SELFTEST cases" artifacts/evidence/T-2026-10-18-harness-roadmap-guard-current-state-010-green.txt`），且四条 `roadmap:routing-*` 各命中恰好一条 `CASE … OK` 判决行。总判定 `blocks=9 floor=9 expectation_entries=9 faults=0` / `OVERALL_EXITCODE=0` / `DECODE ok utf8=True cr=0 commit-shaped-tokens=0`，复算 `python -X utf8 artifacts/tmp/gen-010-d1-green.py`。
- Regression evidence: ① 下限承重——块 02 在**与生产同深度**的镜像盘里把 `MIN_ROUTE_ROWS` 抬到 999，量到 `rc=1 该条判红=True 读数=20（真树同文件读数=20）` 且另外两条当前态判据仍 OK，即红只归因于下限、不是整表失效；同一块还判「赋值行恰好 1 且整行形状是常量=数字」，并留一条形状正控（本仓库早先写的 `\s*=\s*[^0-9]` 会在合法行上也响，因为 `\s*` 能吃掉等号后的空格，正控就是把两种形状喂给同一判据说理）。② 窗口三档——块 03 不判真实工作树（别人的在飞 AGENTS.md 追加会让档位随别人的写入变化），而在镜像里摆出三种形状各测一次：无增量 → `rc=0`、`NOTE SKIPPED` 行恰好 1、被判决的 no-deletion 行恰好 0；只新增 → `rc=0` 且 no-deletion 与逐字存活两条各判 OK、增量打印行存在；删一行 → `rc=1` 且两条窗口判据各判红、三条当前态判据仍全 OK。锚点命中数（本轮 1）也进判据，防止「删不掉东西的删除实验」空转。③ 消费端谱系——块 05 先给探测器三条正控（钉档位行必被点名、中性行必被认、无中性标记行必不被认），再逐条量测 004/005/007 三支生成器：提到本电池的行必须存在、其中钉死档位的必须为 0；同块对生产脚本数旧标签/旧常量的活动引用（本轮三文件各三词全 0）并配一条合成正控证明该计数会响。④ 文档回写承重——块 06 按 `### D1` 小节切片（整文件子串扫描会把 D2 的 `处置` 行也算进来，切片形状对同一合成输入给 1 而全文扫描给 2，这条对照就钉住这点）判闭合标题/处置行/回读行各恰好 1、回读行写明「现场读、不抄值」、闭合三行里没有 `measured=N` 或 `EXITCODE=`，以及全文件无占位符。⑤ 契约同一性——块 07 判本片卡 `PASS` 且零 `ERROR`；校验器的全局 `rc` 与 `SUMMARY files= errors= warnings=` 只打印不判，因为它的档位由别人那 16 条预存债务决定，把它钉死正是本片要消灭的「只在某个 git 状态下成立」同形缺陷。⑥ 规模与指纹——块 08 由归档字节与新字节现场做差分，但**行数由仓库自带的 `count_production_lines.py` 的 `counted()` 读出**（差分得到的新增行片段落成临时 `.py` 再交给它），并要求「同一片段按非空行数」与「按 `counted()`」相等——这条对照防止读者以为那个计数器对 `.py` 也剥注释（它只剥 `//` 与块注释），也防止本片另起一套过滤；本轮判定值 82 = 47+6+29，整文件口径 205/175/401 只作对照，另有一条「加一行必须记 1」的差分正控。块 09 对 12 个只读输入取判定前指纹、末块比同窗口位移（本轮 `movers=[]`），配「缺席->出现必须点名」的正控，另给一条形状对照说明为什么产物只打印整段 64 位哈希而不打印 12 位前缀（前缀会被 scrub，因为它与提交号同形）。

### REFACTOR — optional
- Cleanup: 旧常量 `MIN_NEW_ROUTES` 与两条增量标签在活动生产文件里的引用全部清零（不是改名保留；被删判据的替代谱系记在下一段与 GREEN 的 Regression evidence 里）；`routing_rows()` 助手把「AGENTS.md 单文件内带链接的路由行」这一口径收敛到一处，三条当前态判据与增量打印行共用它，而「HEAD 每一行逐字存活」判据**刻意不共用**——它的覆盖面还包括失败案例库条目（`- [` 起头行），比路由行更宽，写进注释以免后来者「顺手统一」把 append-only 的范围改窄；无增量时窗口块一条判定都不打（`NOTE SKIPPED`），有增量时 no-deletion 仍是真判据并计入退出码，因此永久强制力在三条当前态判据、条件强制力在窗口块；`Checks.note()` 给「设计内要打印但不判定」的行一条显式通道，读者不必再靠 `OK` 行的措辞去猜它到底判没判。
- Regression evidence: 块 05 的旧标签计数（三个生产文件 × 三个旧词全 0，并有合成样本正控证明该计数会响）；块 03 的三档量测（窗口块在**有增量**的两种形状里仍判红删除行，因此 append-only 语义没有因「无增量打 SKIPPED」而丢）；块 04 除了读汇总行的 `cases=` 自述，还按行形状把 `CASE nn <状态>` 判决行归位数了一遍并要求与 `cases` 相等、且五字段恰好等于「条数/条数/0/0/0」——清理若打穿任何一条基线守门，电池会落 `rc=3` 而不是静默少跑用例，若少跑则两个计数立刻不等；块 07 的卡片机械校验；块 09 的同窗口位移（本轮 `movers=[]`）证明清理没有顺手改别人的文件。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 规模判定值按「既有文件只计本次新增行」口径现场算，且**计数走仓库自带的权威函数**而不是再生成一份过滤器：复算 `python -X utf8 artifacts/tmp/gen-010-d1-green.py` 后读规模块 `grep -n "判定值（仓库自带计数器" artifacts/evidence/T-2026-10-18-harness-roadmap-guard-current-state-010-green.txt`。该块用 `difflib.unified_diff` 比较归档的 pre-fix 字节与当前字节得到新增行片段，把片段落成临时 `.py` 后交给 `count_production_lines.py` 的 `counted()`，并判「同一份片段按非空行数与按 `counted()` 相等」——这条对照是必要的，因为那个计数器的注释剥离只对 Java/XML 生效，读者无法从名字看出它对 `.py` 实际只统计非空行；整文件口径（205/175/401）在同一块里作为对照打印。本行不复述数值——它随改动位移；本轮判定值在软上限 200 与硬上限 500 之内，因此不需要规模例外，量级与本卡 `estimated_production_lines` 一致。
- `MIN_ROUTE_ROWS` 是裸字面量而不是 `len(rows)`：用被检清单算自己的下限等于没有下限（AGENTS.md 失败案例库 [2026-10-15] [guard] ①）。它的下限值口径是「AGENTS.md 单文件内带链接的路由行」，取 12 的理由写在守门注释里。
- 窗口块不再决定总退出码：增量为零时打 `NOTE SKIPPED …`，有增量时打 `base=<rev-parse> window=uncommitted-only` 并判 no-deletion。永久强制力由三条当前态判据承担。
- 电池里原先有一条用 `git checkout -- AGENTS.md` 造形的用例（`roadmap:agents-additions-reverted`）——它只在增量窗口内可成立，与本片要消灭的缺陷同形，因此删除；替换为四条直接注进当前态判据的用例，其中抬下限那条的注入门是守门自己的源文件（`MIN_ROUTE_ROWS = 12` → `999`），属于「多成因注入」，注释里已写明。
- 三支姊妹 GREEN 生成器的姊妹判据本片一并改成档位中性（`SIBLING TIER=` / `tier read live, printed not asserted`）。原因：本片让 `checkers_selftest.py` 从 rc=3 变 rc=0，任何钉死「必须是 3」或「必须等于某档」的消费端会在本片入库那一转必然反转——那是谱系缺陷，不是缺陷复发（AGENTS.md [2026-10-16] [evidence] ① 同形）。

## Verification report

- Verdict: PENDING（实现者自验两份产物全绿、验收五条逐条有产物行；独立只读复核轮尚未跑，实现者自验不充当 PASS）
- Evidence: 两份产物各由自己的生成器产出，总退出码一律由 want/got 期望表派生（不硬写），末行带 `DECODE ok utf8=True cr=0 commit-shaped-tokens=0`。复算命令逐条：RED `python -X utf8 artifacts/tmp/gen-010-d1-red.py`（4 块，`OVERALL_EXITCODE=0` = 缺陷如实复现，不是「旧守门可用」）；GREEN `python -X utf8 artifacts/tmp/gen-010-d1-green.py`（9 块，`OVERALL_EXITCODE=0`）。判定行定位：`grep -n "OVERALL_EXITCODE" artifacts/evidence/T-2026-10-18-harness-roadmap-guard-current-state-010-*.txt`；三条当前态判据的 OK 行 `grep -n "current routing" artifacts/evidence/T-2026-10-18-harness-roadmap-guard-current-state-010-green.txt`；电池汇总 `grep -n "SELFTEST cases" artifacts/evidence/T-2026-10-18-harness-roadmap-guard-current-state-010-green.txt`；窗口三档 `grep -n "档 " artifacts/evidence/T-2026-10-18-harness-roadmap-guard-current-state-010-green.txt`；规模两口径 `grep -n "判定值（仓库自带计数器" artifacts/evidence/T-2026-10-18-harness-roadmap-guard-current-state-010-green.txt`。这些行里的数值随 AGENTS.md/ROADMAP/脚本位移，因此本段只给命令、不复述。
- Changed/deleted assertion rationale and equivalent behavior coverage: ① 「AGENTS.md adds >= 4 routing rows」被删，等价覆盖 = 当前态行数下限判据（GREEN 块 01 要求 `measured` 既非空又 ≥ 源里的裸字面量）+ 块 02 的「抬到 999 必须判红且只判红这一条」+ 电池的 `roadmap:routing-table-truncated`（截掉表体必须红）。原判据要防的缺陷是「路由表没有覆盖四个触发词的行」，现在的判定对象换成全文，因此它对**任何一次**改动都出声，而不是只对尚未提交的那一次。② 「new routing rows cover all four triggers」被换成全文版并把缺失集打进 `missing=[…]`；等价覆盖是电池的 `roadmap:routing-trigger-lost`。③ `every new routing-row target exists` 被换成 `every current routing-row target exists`，且判定式从「死链集合为空」改成「链接目标集合非空**且**无死链」——原形态在增量为零时被检集合为空、恒打 `OK — measured_dead=[]`，这是本仓库「匹配集为空必须判 FAIL」的第四次命中；等价覆盖是电池的 `roadmap:routing-target-dead` 与块 01 的非空下限。④ 「无增量」这一形状原来判 OK，现降级为 `NOTE SKIPPED` 且一条判定都不打——块 03 档 1 判「SKIPPED 行恰好 1、被判决的 no-deletion 行恰好 0」，防止「无从认证」被读成「认证通过」。⑤ 电池里原先那条靠 `git checkout -- AGENTS.md` 造形的用例被删（它只在增量窗口内可成立，与本片要消灭的缺陷同形），等价覆盖是块 03 在镜像里把三种窗口形状各摆出来测一次——判据不再要求真仓库处于某个 git 状态。
- Gaps: ① 真实工作树此刻的窗口形状本片没有判，也不该判：共享工作树里别人的在飞 AGENTS.md 追加会让档位随别人的写入变化（既有规则「一个窗口绿、一个窗口红不是回归复发而是此刻无法认证」），因此三档量测全在镜像里做，真实树的那一条由守门自己在下次运行时现场判。② `MIN_ROUTE_ROWS` 的口径是「AGENTS.md 单文件内带链接的路由行」；若将来把路由表拆到 `.trae/rules/` 下多个文件，该常量必须重设，本片只在守门注释里写明口径、不预测。③ `checkers_selftest.py` 汇总行的 `skipped` 字段恒为 0（该电池没有跳过通道），块 04 仍要求它等于 0——它是「以后有人加了跳过就被抓住」的占位，不是当前功能。④ ROADMAP 的 D2–D8 其余欠账未动；007 只读复核提的两处空见证形状与 005 的退出码档位仍是任务 #7。⑤ 本片的「电池首次跑完全绿」是**可运行性**结论，不是「四条基线守门从此可靠」——四条基线各自的健康样本形状仍由 004/005/007 的产物分别托底。⑥ 独立复核轮未跑；本片自验不充当 PASS。
- Risks: ① 当前态判据不看「谁新增」，一次误删整表若仍留 ≥ 下限行数它会绿——兜住它的是「HEAD 每一行逐字存活」判据（读 `git show HEAD:AGENTS.md`）与 `routing-table-truncated` 注入用例，两者都不看行数下限；② 块 02 的注入改的是守门自己的源文件，属多成因注入，因此它的期望 FAIL 标签绑在那条判红行的**完整文案**上，将来判据文案一改该块会以「镜像里没能把这条判成红」的形式判 3 而不是静默通过；③ 用例条数下限（本轮 50）是**证据生成器里的裸字面量**，与清单无关；`checkers_selftest.py` 自己没有条数下限（它只打印 `len(CASES)`），所以「删掉一条用例」在这一层没有信号，能抓住它的是块 04 的下限与四条 `roadmap:routing-*` 各恰好一条 OK 判决行的归因——把下限改成 `len(CASES)` 等于没有下限——块 04 的判决行把该常量的身份打印在案（`条数下限（与本清单无关的裸字面量）=`），复核者按它回读生成器即可分辨。

## Handoff notes

- Completed: 守门改判当前态（三条 + 裸字面量下限 + 空集合不再算通过）、`Checks.note()` 通道、电池删掉 git 状态依赖用例并新增四条 `roadmap:routing-*`、三支姊妹生成器的档位中性化、ROADMAP D1 闭合回写与 004/005 两处时点读数括注、两份 pre-fix 归档副本、RED 与 GREEN 两份产物各跑绿（RED `blocks=4 floor=4 faults=0` / `OVERALL_EXITCODE=0`；GREEN `blocks=9 floor=9 expectation_entries=9 faults=0` / `OVERALL_EXITCODE=0`，条数权威见上面 Evidence 的 grep 入口）。文档类守门双向自检**首次跑完全绿**是本片的交付物之一，不是一句自述：GREEN 块 04 同时要求汇总行自述的 `cases` 与按行形状归位的判决行数相等。
- In progress: 无——本片自验完成，剩入库与独立只读复核轮（与 005/007/009 同一口径：实现者自验不充当 PASS）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 整树一次提交（含 `.trae/scripts/**` 三个文件、ROADMAP、本卡、两份产物、两支生成器；`artifacts/tmp/**` 里除两支 `gen-010-d1-*.py` 生成器以外的临时件与那批含本机 API Key 的 `.db` 副本不入库）；提交后排一轮本片独立复核；随后任务 #7（007 生成器三处「零集合也算通过」与 005 的退出码档位）；再排 `cdp_shot.mjs` 档位对齐与原型离线化；最后回到阶段 1 夹具采集（ALP-AN00，`adb install -r`、整包仪器测试、不传 `--tests`）。
- Watch-outs: ① 归档副本住在 `artifacts/evidence/`（与被检脚本同为仓库根下三级），RED 块 02 运行归档守门时必须用 `artifacts/_010lib` 这一**与生产同深度**的 shim 递送归档共享库——`harness_check_lib` 的 `REPO_ROOT` 由库自身位置算出，深度错一级它就因「ROADMAP 不存在」而红在别的东西上；import 失败同样退 1，会把「仪器崩」伪装成「判据红」。② 本片不改 AGENTS.md 既有行；若要追加失败案例条目，注意那会让工作树与 HEAD 的增量非空，守门窗口块因此从 `SKIPPED` 改判 no-deletion——两种形状都由 GREEN 块 03 的镜像三档量测，不依赖真实工作树状态。**本片收尾时真的追加了一条**（失败案例库里「形状判据只喂该响那一侧样本」那条），真实树因此从档 1 变档 2：现场回读 `python -X utf8 .trae/scripts/check_roadmap_and_routing.py` 给出 `OK AGENTS.md change contains no deletion line` 与 `EXITCODE=0`（`added` 与 `deletions` 的现值由该行自己打印，本卡不抄），且 `added_rows=0` 证明那条 bullet 不会被 `routing_rows()`（要求行首是竖线且含链接）算进路由行数——追加不会把行数下限判据顶高。③ 时效性读数只打印不判：`EXITCODE=1`（修复前）、`measured=0`（增量已入库）都是历史形状，复现入口是归档字节的运行（RED 块 02）而不是 `git diff`。④ 电池的四条新用例是多成因注入（抬下限那条会让行数下限先红），它们的期望 FAIL 标签各自绑在**真正会失败**的那条断言上，不要顺手改成「任何非 0」。⑤ 本卡 RED 与 GREEN 的 Command 必须逐字相同（Task v2 校验器的硬要求），所以「运行归档守门字节复现三条旧判决」这件事登记在 RED 产物的块 02 里而不是写成 RED 的 Command；读者若把 Command 换成 `check_roadmap_and_routing.py` 会让两半不再相等并把卡判 ERROR。⑥ 校验器的全局 `rc=1` 与 `errors=16` 是别人那四张历史卡的债务（ROADMAP D3），GREEN 块 07 因此只判本片那一行是 `PASS` 且零 `ERROR`；不要为了让它整体绿去改别人的卡。⑦ 本片的规模判定值由 GREEN 块 08 从**归档字节 vs 当前字节**做差分现场算出，不走 `git diff`：`git diff` 在这个共享工作树里同时躺着别人的在飞改动，用它算规模正是本片要消灭的那条判据的成因。
