# Treelearn Harness Task: 1:1 闭环量测的 DOM 侧仪器收编入库

id: T-2026-10-15-009
title: 1:1 闭环量测的 DOM 侧仪器收编入库
template_version: 2
task_type: implementation
status: in_progress
parent_requirement: T-2026-10-15-web-h5-android-parity-requirement-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 345
shared_code_touched: true
depends_on: [T-2026-10-15-parity-node-instruments-adoption-006.md, T-2026-10-15-parity-measurement-instruments-adoption-003.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: T-2026-10-15-006 实际只交付了产图侧（`cdp_shot.mjs` + 共享层 `parity_cdp.mjs`），规格 §10 因此把剩下的 DOM 闭环量测显式改挂本片。前身 `artifacts/tmp/verify_1to1.mjs`（2026-09-27，207 行）仍是位置参数、写死 `const PORT = 9222`、写死点击序列 `.demo-link -> .topic-card`、只有 0/1 两档退出码，且住在会被清理的目录里——「渲染侧达绿」因此无法重跑。
- Users/scenarios: 阶段 3–6 逐屏校正（DOM 量测与像素量测逐项对照）、阶段 9 收口回归；任何想复核 1:1 结论的人都能用一条命令重跑并拿到逐条判据。
- Expected outcome: 仪器进入 `.trae/scripts/dev/parity/verify_1to1.mjs`，按屏名从 `screens.json` 的 `prototype` 段取 HTML 路径、点击序列、等待选择器与 `dom_checks` 名单；`dom_checks` 里每一项由一张判据词表实现，档案写了词表不认的名字判仪器故障（3）；退出码档位与另三支仪器一致；`--selftest` 正反双向。
- Core flow and reversibility: 前身字节归档到 `artifacts/evidence/` → 新路径落仪器本体与自检伴随文件 → 参数化并接档案 → 六条判据逐条落依据 → 电池 → 证据落盘；整片可回退，不影响 Android 与前端产物。
- Inputs/outputs: 输入是屏名（默认档案为同目录 `screens.json`）；输出是逐条 DOM 判据行与完整 DOM 读数 JSON（`--out`），头部带命令行、屏档案与原型 HTML 的字节/行数/SHA-256。
- State and exception handling: 0=跑完且判据全成立、1=跑完且有判据判 FAIL、2=用法错误、3=仪器自身故障（缺 HTML、缺浏览器、CDP 握手失败、档案缺 `prototype`/`dom_checks`、检查名不在词表内、探针抛错、未捕获异常）。空判据必须 3，不写 PASS。
- Constraints and trade-offs: 零第三方依赖（Node 内置 fetch/WebSocket + Edge headless CDP）；探针的**输出键名是外部契约**——2026-09-27 那份 `artifacts/evidence/ux-chat-1to1-render-dommeasure-2026-09-27.json` 就是这套键，所以只改调用形态与判定，不改探针键集与算法。
- AI recommendation and rationale: 沿用 006 已跑通的两文件结构（本体 + 独立自检伴随文件）与共享层，而不是给 DOM 侧另起一套 CLI 约定；判据词表按「每条都必须有可回读的权威」来设计，宁可打印也不发明常量。
- User choice and custom additions: 用户在 2026-10-17 的进度复核后选择「按建议执行」，其中第 2 条即「下一片主线 = Node 侧收编，因它不依赖真机」；006 交付面收窄与本片建立都在该已确认范围内。
- AI-autonomous decisions: ① 六条 `dom_checks` 各自的依据来源（token 等值、跨元素等值、内部等距）；② 格线周期的权威取 `--s8`（Android 侧 `PaperRuleBackdrop` 第 44 行读同名 `R.dimen.space_s8`）而不是发明一个常量；③ alpha 比对容差 0.0051 定为**读数分辨率**（浏览器把渐变里的颜色重序列化成两位小数）并写进判据行；④ 电池分两层，词表层从 pos 用例落盘的读数派生健康样本，而非手写夹具。
- User-confirmation decisions: 无新增待确认项；`--lh-read` 注释与实测格线周期的矛盾作为 Gaps 呈报，不在本片擅自裁定。
- Acceptance evidence: `--selftest` 的逐条判决行与退出码（条数以 `node .trae/scripts/dev/parity/verify_1to1.mjs --selftest` 打印的 `cases=/cli=/muts=` 为准，本卡不复述条目数——它随用例增删而变，抄进正文就立刻过期）；对 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 对话页一次跑通，读数与复刻方案 §9.1 的渲染侧记录逐项可比；相邻失败情形各落一档。
- Facts: 前身文件在 2026-10-17 之前已被 git 跟踪且工作树字节与 HEAD 相同（归档副本与 `git show HEAD:artifacts/tmp/verify_1to1.mjs` 逐字节相同）；`screens.json` 的 `chat.prototype.dom_checks` 六个名字由 003 落库；`--green-bubble` 已于 2026-09-28 退役（原型 `:root` 第 34 行注释、`tokens.json` 已无该键）。
- Assumptions: 本机 Edge 可执行文件在默认位置之一（由 `findBrowser` 现场探测并打印版本，不假设具体版本）。
- Unknowns: 平板档与 PC 档是否需要第三套裁剪参数（沿用 003/006 的未决项）。
- Risks: 判据依赖浏览器版本的 CSS 序列化形状（渐变里颜色的重序列化精度、`transparent` 是否序列化成 `rgba(0, 0, 0, 0)`）——电池的正控断言会在形状漂移时先出声，而不是让判据静默失配。
- Deferred: 逐屏夹具采集与跨端逐项比对（阶段 1/3，需真机与 ALP-AN00 基线）。
- Non-goals: 不引第三方自动化依赖、不改原型 HTML 的样式、不判「与 Android 是否等价」（那是阶段 3 拿这份读数去比的事）。
- Skipped non-critical questions and reason: 词表条目的中文措辞与判据行的具体句式不影响验收，由实现者定；格线周期的权威选择已实测到 Android 同名 dimen，无需再问。

## Goal

DOM/计算样式侧闭环量测仪器在 `.trae/scripts/dev/parity/` 下按屏名运行，与 003/006 共用同一份屏档案、同一套四档退出码，且 `screens.json` 点名的每一项 DOM 检查都有一条能回读依据的判据。

## Scope

### In scope
- 前身字节归档为 `artifacts/evidence/T-2026-10-15-009-pre-adoption-verify_1to1.mjs`，旧路径 `artifacts/tmp/verify_1to1.mjs` 由 `git rm` 移出跟踪。
- `verify_1to1.mjs`：`--screen/--out/--profile/--browser/--attach/--url/--wait-ms`，接 `parity_cdp.mjs` 共享层，四档退出码。
- `DOM_CHECKS` 词表：`body-bg`、`grid-period`、`user-bubble`、`body-pitch`、`action-pitch`、`pill-inset` 各一条判据，档案写了词表不认的名字或 `dom_checks` 缺席/为空一律 3。
- `verify_1to1_selftest.mjs`：两层——CLI 层每条一个期望退出码（含「等不到 `.screen`」的两种成因各一例：空 `#root` 桩落 3、有内容无 `.screen` 的桩落 1，都用本机 HTML 桩所以不打公网），空判据单元 1 例；词表层正控 1 例与逐条注入（每条词表项至少一条「注入该违规必须判红」，并声明「除本条外其它条不受影响」）。两层各有与清单内容无关的裸字面量下限。
- 证据文件写入 `artifacts/evidence/`（UTF-8、CR=0、含命令与退出码、被核文件哈希；总退出码由 want/got 期望表派生）。

### Out of scope
- 不改 DOM 判定的阈值口径之外的算法、不采集新屏基线、不引第三方依赖、不动 `screens.json` 的档案结构。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 保留 2026-09-27 闭环的**读数口径**——同一张对话页重跑，探针键集与逐项读数仍与那份 JSON 可比；变化只在调用形态、文件位置，以及把「只打印」升级成「有依据地判」。
- Producer/consumer/sibling entrypoints and platform branches: 生产者为本片两支 `.mjs`；消费者为复刻方案 §9.1 的对照表、003 的 `screens.json`、阶段 1 夹具采集；兄弟仪器 `cdp_shot.mjs` 与本仪器共用 `parity_cdp.mjs`，**共享层头注里「待收编的 verify_1to1.mjs」这句在本片落地后过期**，须同步；`measure_screen.py --kind render` 的制式与本仪器无直接耦合（它读产图侧的 2x 约定）。
- Native bridge or independent-client compatibility/update requirements: 不适用，无桥接。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本片改变工具可调用性与判定存在性，需按行为变更取证。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: DOM 侧仪器的路径、调用形态与退出码档位；六条 DOM 判据的依据来源。
- Authoritative code/config/migration evidence: 新路径下的两支 `.mjs`、`screens.json` 的 `prototype.dom_checks`、`--selftest` 输出与三份证据文件。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §10（两条 carve-out 写着「待 009」）、`Docs/04-ux-design/知芽-TreeLearn-对话页1比1复刻方案-V1.0.md` §9.1 工具表、`.trae/scripts/dev/parity/parity_cdp.mjs` 头注、`.trae/README.md` 若含工具索引、`.trae/tasks/T-2026-10-15-parity-node-instruments-adoption-006.md`（交付面与实际一致化）。
- Documentation files updated: 规格 §10 两条 carve-out 改为已入库并指向本片；复刻方案 §9.1 工具表加路径变更标注（当年的量测结论不改写）；`parity_cdp.mjs` 头注「待收编」措辞改为现况；006 卡登记交付面收窄。
- Producer/consumer/sibling/compatibility paths checked: 反向搜索 `artifacts/tmp/verify_1to1`——任务文件与 `artifacts/evidence/` 内的旧路径按当时状态归档、不改写，其余当前态文档命中必须清零或显式标注为历史。
- Superseded phrases/examples reverse-searched: 「只剩 DOM 闭环量测仍是 `artifacts/tmp/` 下的位置参数形态」「DOM 闭环那一支待 009」「待收编的 verify_1to1.mjs」。
- documentation impact: none reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 复刻方案是带日期的实测记录，只更新路径引用与变更标注，不改写当年读数。

## Acceptance criteria

- [ ] 期望行为：`node .trae/scripts/dev/parity/verify_1to1.mjs --screen chat --out <json>` 退出码 0，并打印本次使用的 HTML 路径、点击序列、浏览器版本、词表要求的 token 数与每条判据。
- [ ] 保持行为：对同一张对话页原型重跑，探针键集与逐项读数与 `artifacts/evidence/ux-chat-1to1-render-dommeasure-2026-09-27.json` 逐项可比；不可比必须给出原因而不是改期望值。
- [ ] 相邻失败：缺 HTML、缺浏览器（可执行文件不存在）、CDP 握手失败分别落 3/3/3；`dom_checks` 缺席、为空、名字不在词表内三条也落 3；用法错误落 2；等待目标缺失与点击目标缺失落 1；「什么都没判却打印 PASS」判 FAIL。
- [ ] 受影响入口：当前态文档里的旧路径引用清零或显式标注历史；`-M` 各阈值下配对情况的实测值与本片的替代谱系（仪器页首前身、任务卡「前身」、方案路径标注）一并记录。
- [ ] 证据文件自证（命令 + 退出码 + 被核文件哈希），UTF-8 合法且 CR=0；词表层每条判据都有「注入该违规必须判红」的注入用例，且注入只打红它自己那一条（其它条不受影响）。

## TDD execution record

### RED — failing test first
- Test/scenario: 承诺的调用形态 `node .trae/scripts/dev/parity/verify_1to1.mjs --selftest` 在收编发生之前给不出任何自检读数——前身把 `--selftest` 当第一个位置参数里的 URL，只能去 fetch 它；另配 7 个前置事实块（仪器故障与判据 FAIL 同码、没有 `--screen`/档案、写死端口 9222、词表手抄含已退役的 `--green-bubble`、完全没有机械判定、没有用例数下限），外加块 08 给每条判据要读的文件钉判定前指纹。
- Command: `node .trae/scripts/dev/parity/verify_1to1.mjs --selftest`
- Exit code: 1
- Observed failure: 只有 `FAILED: fetch failed` 一行——既没有 SELFTEST 判决行，也没有 `EXITCODE=` 档位行
- Failure reason validated: 是「收编尚未发生」而不是「实现有缺陷」，且这条读数**不是**在新路径上量的：RED 生成脚本跑时新路径已是实现后的字节，它按前身字节逐字相同的归档副本取数——`artifacts/tmp/gen-009-red.py` 执行 `node artifacts/evidence/T-2026-10-15-009-pre-adoption-verify_1to1.mjs --selftest`，产物 `artifacts/evidence/T-2026-10-15-009-red.txt:20-21` 记 `退出码=1 输出首行=FAILED: fetch failed`。今天复现：`node artifacts/evidence/T-2026-10-15-009-pre-adoption-verify_1to1.mjs --selftest; echo $?`。之所以这个 1 要紧，是块 02 量的那件事：前身 `process.exit` 取值集合只有 `[0, 1]`（`red.txt:30`），两次不同故障（把 `--selftest` 当 URL、连 URL 都没给）给出的退出码是 `[1, 1]`（`:31`）——「仪器自己坏了」与「跑完发现判据不符」在前身里是同一个数。其余各块的对照读数：`--screen`/档案行 0、写死 `9222` 1 行而共享层用端口 0 由浏览器自选、前身 token 名单里 `--green-bubble` 为 True 而原型现在已不定义它（退役注记为 True，即手抄名单保住了一个死名字）、机械判定标记命中 0/3 而词表 6 个键由 `node` 现场 import 取得、下限/计数标记前身 0/3 而新电池 3/3 且裸字面量下限为 `CASES=8`/`MUTS=8`。总判定由 want/got 期望表派生：`blocks=8 expectation_entries=8 faults=0`、`OVERALL_EXITCODE=0`（此 0 = 缺口如实复现，见该文件 `:10-11` 的判读约定；复算 `python -X utf8 artifacts/tmp/gen-009-red.py`）。

### GREEN — minimum implementation
- Implementation: 前身 `artifacts/tmp/verify_1to1.mjs` 的字节先归档成 `artifacts/evidence/` 里的前身副本，`git rm` 移出旧路径；新路径 `verify_1to1.mjs` 接 `parity_cdp.mjs` 共享层，改 `--screen/--out/--profile/--browser/--attach/--url/--wait-ms`，`dom_checks` 由 `DOM_CHECKS` 词表实现且档案写了词表不认的名字（或 `dom_checks` 缺席/为空）一律判 3；`--selftest` 委派给独立伴随文件 `verify_1to1_selftest.mjs`（静态导入，避免顶层 `await import()` 的 ESM 环）；电池分两层——CLI 层每条一个期望退出码，词表层把 CLI 层刚落盘的真实读数当健康样本、逐条注入一个字段喂回词表，并声明「除本条外其它条不受影响」。
- Command: `node .trae/scripts/dev/parity/verify_1to1.mjs --selftest`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-15-009-green.txt:36` 的电池读数 `rc=0 汇总行=SELFTEST PASS cases=40 cli=20 muts=18 failures=0 ok-cli=20 unit=1 ok-mut=18 FAIL行(电池)=0 tiers=[0, 1, 2, 3]`——打印的 `cli/muts` 与按行形状归位的判决行数一致（条数不是自述）、四个档位都被 CLI 层走到、两条与清单无关的裸字面量下限达成；块 03 另跑一次真实闭环 `rc=0 judgements=12 failed=0`，档案点名的 6 条检查各自打了一条判决、token 是现场回读且与词表要求同数、中文路径未坏。复算 `python -X utf8 artifacts/tmp/gen-009-green.py`（12 块，本轮 `OVERALL_EXITCODE=0`）。
- Regression evidence: ① 保持行为——块 04 用**一处实现的** `key_diff` 把新读数与 2026-09-27 归档 JSON 比顶层键集与嵌套键集：`归档顶层键在新读数里一个不缺`、`除 tokens 外每个嵌套字典的键集也与归档相同（差异: 无）`、且嵌套比对面非空（14 个顶层字典，否则那条判据是空见证）；`tokens` 的差异有据（归档名单含已退役的 `--green-bubble`，新名单等于词表要求且非空）。变异侧 M7 把这条比对退回「只比顶层」必须判红，并且它比的是 GREEN 导入的同一个函数而不是重写一遍。② 词表覆盖——块 05 四份负向夹具全部退 3、各点名不同成因、无一份打 `VERDICT: PASS`，外加一条未改动的正控退 0；块 06 四条用法用例全退 2 且**都没起浏览器**。③ 兄弟仪器未被共享层改动打穿——块 07：`cdp_shot.mjs --selftest` 与 `measure_screen.py --selftest` 各自 rc=0 且都打印了判决行。④ 规模——块 11 判定值 374 =（仪器本体 369）+（共享层新增行面 5），自检伴随文件 180 行按「Tests … NOT counted」排除，且该排除是承重的：374+180=554>500，不分文件就越线（回读 `sed -n '/block 11/,/block 11: want/p' artifacts/evidence/T-2026-10-15-009-green.txt`）。⑤ 判据区分力——`artifacts/evidence/T-2026-10-15-009-mutation.txt`：`blocks=10 mutations=9 floor=6 floor-met=True faults=0`、`空变异=0 被判输入位移=0`、`OVERALL_EXITCODE=0`，九条各拆一条守卫并判「有且只有该判据的结论改变」，含 M4（关掉 witness 守卫后 `neg-click-absent` 会以同样的 rc=1 绿着，见证行了无痕迹）与 M9（关掉 `#root` 分流后空桩从 3 变 1）。⑥ 本轮两处实测缺陷的回归锁各自会响：witness 守卫由 M3/M4 量，档位分流由 M9 量。

### REFACTOR — optional
- Cleanup: 前身的位置参数与写死常量全部消失；`--selftest` 的两条入口（经仪器委派、直驱伴随文件）判定一致；词表的 token 名单由 `DOM_CHECKS` 声明、注入与量测共用同一次求值结果（早期写法分两次 eval，于是一个只改第二支表达式的缺陷可以静默通过）；共享层头注「待收编的 verify_1to1.mjs」已改为现况，且这条同步由 GREEN 块 08 钉住（该文件必须提到 `verify_1to1` 且不得再出现「待收编/尚未/仍留在」）。
- Regression evidence: 块 01（三脚本 `node --check` 全 0 + 委派方向是静态导入且电池不反向导入仪器）；块 09 运行窗口增量（共享工作树里别人的在飞工作不算本片缺陷，本片自己的位移必须点名，并配一条内存正控）；块 10 改动面对 git 可见、旧路径只作为历史/前身出现；块 12 名单覆盖每一条判据读过的文件（`--no-write` 类只读复算不在此列）。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 规模判定值由 GREEN 块 11 现场算出并按「新增行面 + 整文件面」两口径留档：判定值 =（仪器本体整文件非空非注释）+（共享层 `parity_cdp.mjs` 本次新增行面），自检伴随文件按「Tests … NOT counted」不计。复算 `python -X utf8 .trae/scripts/dev/count_production_lines.py .trae/scripts/dev/parity/verify_1to1.mjs`，共享层那一半由 `git diff --numstat HEAD -- .trae/scripts/dev/parity/parity_cdp.mjs` 配合同一脚本算；两口径与「排除是否承重」都写在 `artifacts/evidence/T-2026-10-15-009-green.txt` 块 11 里，本行不复述数值——它随每次仪器改动位移，本轮已位移过一次。超 200 软上限、未超 500 硬上限，因此不需要规模例外。
- 两处本轮实测到的仪器缺陷与它们的修法（都不是「调用形态」之外的改动，但都属本片认证范围，因为原形态会让结论假绿）：① **witness 守卫**——期望 rc=1 的负向用例原先只比退出码，于是「页面 60s 没渲出来」能让 `neg-click-absent` 拿到 rc=1 却从没走到点击分支；现在每条这样的用例声明它要触发的那条判决行，并在电池里逐用例截住 `console.log` 核对它确实出现。② **渲染等待的档位分流**——`.screen` 等不到时有两种成因，`readyState=complete` 分不出来：原型的界面是 React+Babel 在**页面里**现场编译的（head 里三个 unpkg 脚本 + 两份公网字体），公网脚本没答应 = 被核对象根本没起来（仪器故障 3），界面渲出来了却没有 `.screen` 才是结构不符（1）。原形态一律判 1，把「仪器没拿到被核对象」读成了「实现不符」。现在按 `#root` 子元素数分流，并对「没起来」这一档做有界重载（`MAX_BOOT_RETRIES`，结构不符不重试，否则真缺陷会被重试掩盖）。两条都由变异电池量测（M3/M4、M9）。
- **流程偏差如实登记**：本卡的建立晚于仪器落地（同一会话内先写完 `.mjs` 再补卡与 RED）。RED 因此不是「实现前的一次历史运行」，而是**用归档的前身字节现场重跑**得到的缺陷复现——前身样本住在 `artifacts/evidence/`，不依赖旧路径仍在 HEAD 里。
- 判据的依据必须能回读：`body-bg`/`user-bubble`/`body-pitch`/`pill-inset`/`grid-period` 各自比对 `:root` 里真实存在的自定义属性；`action-pitch` 判的是内部等距与「中心距 = 按钮宽」。任何一条都不许拿上次读数当常量。
- 电池的词表层必须从 pos 用例落盘的**真实读数**派生健康样本；手写夹具的形状一旦与探针输出不同形，注入就成了空见证（本仓库栽过）。
- 中文路径经 argv 传给子进程时按 shell 变量传，不在 here-doc 里写字节转义。

## Verification report

- Verdict: PENDING（实现者自验三产物全绿、验收五条逐条有产物行；独立复核轮尚未跑）
- Evidence: 三份产物各由自己的生成器产出，总退出码一律由 want/got 期望表派生（不硬写），末行带 `DECODE ok utf8=True cr=0`。复算命令逐条：RED `python -X utf8 artifacts/tmp/gen-009-red.py`（8 块，`OVERALL_EXITCODE=0` = 缺口复现，不是「旧仪器可用」）；GREEN `python -X utf8 artifacts/tmp/gen-009-green.py`（12 块，`OVERALL_EXITCODE=0`）；变异 `python -X utf8 artifacts/tmp/gen-009-mutation.py`（10 块 / `mutations=9 floor=6 floor-met=True faults=0`、`空变异=0 被判输入位移=0`）。判定行定位：`grep -n "OVERALL_EXITCODE" artifacts/evidence/T-2026-10-15-009-*.txt`；电池读数在 `grep -n "SELFTEST PASS" artifacts/evidence/T-2026-10-15-009-green.txt`；一次真实闭环的判据行数与 token 回读在 `grep -n "judgements=" artifacts/evidence/T-2026-10-15-009-green.txt`；规模两口径在 `grep -n "判定值" artifacts/evidence/T-2026-10-15-009-green.txt`。这些行里的数值随仪器改动位移，因此本行只给命令、不复述。
- Changed/deleted assertion rationale and equivalent behavior coverage: ① 前身的位置参数、写死 `const PORT = 9222`、写死点击序列 `.demo-link -> .topic-card` 被「按屏名从 `screens.json` 的 `prototype` 段取」替代——口径未变的证据不是自述而是 GREEN 块 04：同一张对话页重跑，归档读数的顶层键与 14 个嵌套字典的键集逐项对得上（`嵌套比对面非空（已比对 14 个顶层字典…）`就是防止那条嵌套判据成空见证的），唯一差异 `--green-bubble` 由块 04 另一条判据给出「归档名单含已退役键」的依据。② 被删掉的判定是「等不到 `.screen` 一律判 1」——等价覆盖分成两条各带自己见证行的 CLI 用例（`neg-page-not-rendered` 要 1、`neg-app-not-booted` 要 3）加变异块 09：把分流拆掉的副本必须把同一份空桩从 3 读成 1，真层两条各落自己那档。③ 「只打印不判」升级为「有依据地判」——词表层 18 条注入各自绑定一条判据，且「注入只打红它自己那一条」由 MUTATION 块 10 之前的各块逐条量测。④ 「两次不同故障的退出码=[1, 1]」这类前身的档位缺陷（RED 块）在本片由 `tiers=[0, 1, 2, 3]` 那条电池汇总行认证四档都真被走到。
- Gaps: ① tokens.json 里 `lh-read` 的注释写着「阅读行高 == 格线间距」，而实测是 `--lh-read=28px` 与格线周期 32px（= `--s8`）——**两个值与那条等式互不相容**，仪器只打印不判，需设计侧裁定是改注释还是改渐变；② 平板档/PC 档裁剪参数未验证；③ 跨浏览器版本的几何与 CSS 序列化稳定性只在 Edge 一个版本上实测（版本读数由仪器每次现场打印，不写进本片正文）；④ 只有 `chat` 一屏有 `dom_checks`，其它屏进来时词表是否够用未验；⑤ **本片对公网有硬依赖且离线化未做**：原型界面是页面内的 React+Babel 现编译的（head 里三个 unpkg 脚本 + 两份公网字体），公网没答应时按新增分流落 3——本轮 GREEN 第一次跑就被它抓住（块 02 的 `pos-url-escape` rc=3、61.3s），加有界重载后才绿；「渲染侧达绿」因此隐含「本次网络可用」这一环境前提，该前提只在产物里打印、没有独立判据；⑥ 兄弟仪器 `cdp_shot.mjs` 仍保留本片刚修掉的同形缺陷（等不到 `.screen` 一律判 1），本片不越界替它改，登记为后续片。
- Risks: 判据依赖浏览器对渐变色值的重序列化精度（±0.0051 定为读数分辨率并写进判据行）；「按文案/形状取墨色」是版本相关的外部契约，正控断言负责在形状漂移时先出声。

## Handoff notes

- Completed: 前身字节归档并与 HEAD 逐字节比对；仪器本体与自检落地并跑通（一次真实闭环 rc=0、电池 rc=0 且四个档位 0/1/2/3 都被 CLI 层走到）；六条判据的依据逐条回读到原型 `:root`、`tokens.json` 与 Android `PaperRuleBackdrop`/`@dimen`；两处实测缺陷已修并各留一条会响的守卫（① 期望 rc=1/3 的用例必须有那条分支自己的见证行，② 「等不到 `.screen`」按 `#root` 读数把仪器故障与结构不符分档并对公网抖动做有界重载）。兄弟仪器 `parity_ledger_selftest.py` 里那条随本片入库必然反转的 `not-adopted` 断言已改为状态无关，并新增合成树用例与变异 M11（见 Watch-outs ⑧）；屏幕清单与台账已在安静窗口各重生成一次。
- In progress: 只剩本片提交，以及提交后的回读产物 `artifacts/tmp/gen-009-post-commit.py`（尚未写）。RED/GREEN/变异三份产物已全绿并入库。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 本片提交后写并跑 `artifacts/tmp/gen-009-post-commit.py`（谱系判据按「新路径必须**包含**移动前旧路径拥有的每条提交**并写明 `-M` 阈值**」写，参照 003 的教训而不是条数相等）；再排一片把 `cdp_shot.mjs` 的「等不到 `.screen`」档位与本仪器对齐并做公网依赖离线化；随后处理 007 只读复核在 2026-10-17 提出的其余两条（生成器里的空见证形状判据、台账卡里被引用却不存在于任何产物的见证行）；之后回到阶段 1 夹具采集（需 ALP-AN00，仪器测试整包跑、不能传 `--tests`）。
- Watch-outs: ① 不要把「浏览器没起来」压绿成「用例不符」——那是仪器故障（3）；② 本片的替代谱系靠文档，git 的 `-M` 在 20% 阈值下都不配对（与 006 同形），因此**删除前身必须留下归档字节**而不是只删；③ 时效性状态只打印不判，而且**两个前身的窗口现在并不相同**：`artifacts/tmp/cdp_shot.mjs` 已随 006 的提交从 HEAD 消失（`git show HEAD:artifacts/tmp/cdp_shot.mjs` 现场 rc=128），而 `artifacts/tmp/verify_1to1.mjs` 在 HEAD 里**仍然在**（本片的删除尚未提交，`git show HEAD:artifacts/tmp/verify_1to1.mjs` 现场 rc=0）——所以「复算请指向 `artifacts/evidence/` 的归档副本」这句话只在本片提交之后才成为唯一正确路径，提交前后各读一次即可分辨；④ 词表与 `screens.json` 的 `dom_checks` 名单是两处正文，加一项而不同步词表会判 3，这是设计如此，不要改成静默跳过；⑤ GREEN 块 02 的用例清单、块 11 的规模读数、块 04 的「14 个顶层字典」都随仪器与归档读数位移，改仪器后**必须重跑生成器**而不是把产物里的数字抄进本卡；⑥ 有界重载（`MAX_BOOT_RETRIES`）只作用于「`#root` 为空」这一档，结构不符不重试——把它改成对两档都重试会让真缺陷被掩盖，M9 的副本判据正是为此而留；⑦ 台账那一行（`artifacts/parity/ledger.md` 的「行为仪器（verify_1to1.mjs）」）是**生成物**，它随树的实际状态走：本片入库前它写 `not-adopted:artifacts/tmp/…`，入库（含一次 `gen_parity_ledger.py` 重生成）后写 `adopted`。GREEN 块 10 对这一行判的是「两态必居其一且互斥」，不是「必须等于某态」——原先写成「恰好一条派生命中」，那是只在重生成之前成立的条件，重生成之后会把「已自愈」读成「文档回写复发」（本轮实测命中：本次重跑给出命中=0 + adopted 行）；⑧ 兄弟仪器 `parity_ledger_selftest.py`（007 的自检）里原先有一条「台账必须点名 `not-adopted`」的断言，它**随本片入库必然反转**，因此本片把它改状态无关（模板形状 + 三档取值移到合成树用例 `pos-instrument-adoption-states` 量测）并补了变异 M11；`EXPECTED_CASES` 从 19 变 20，这条计数的现值权威在 007 自己的卡与产物里，本片不复述。
