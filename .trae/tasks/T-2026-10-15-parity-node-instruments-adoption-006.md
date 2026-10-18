# Treelearn Harness Task: 1:1 闭环量测的两支 Node 工具收编入库

id: T-2026-10-15-006
title: 1:1 闭环量测的两支 Node 工具收编入库
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-15-web-h5-android-parity-requirement-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 260
shared_code_touched: false
depends_on: [T-2026-10-15-parity-measurement-instruments-adoption-003.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: T-2026-10-15-003 只收编了 Python 侧（像素实测 + 读图探针）。1:1 判据的另一半——把高保真原型渲染出来并量 DOM 几何/计算样式——仍住在 `artifacts/tmp/`：`verify_1to1.mjs` 与 `cdp_shot.mjs`。它们仍是位置参数、只认一条硬编码 URL、缺浏览器时不给「结论不可信」的退出码，因此「渲染侧达绿」同样无法重跑。切片理由与规模约束见 003 的 TDD 记录：两片合计的判定行数会越过 500 硬上限，因此按语言侧分片。
- Users/scenarios: 阶段 1 采集夹具（原型渲染图）、阶段 3–6 逐屏校正（DOM 量测对照像素量测）、阶段 9 收口回归。
- 交付面校正（执行期决定，如实登记）: 本片实际交付的是**产图侧一支**——`artifacts/tmp/cdp_shot.mjs`（前身路径，现为 `.trae/scripts/dev/parity/cdp_shot.mjs`）与它新抽出的共享层 `parity_cdp.mjs`。DOM 侧那支（前身 `artifacts/tmp/verify_1to1.mjs`）改由 T-2026-10-15-009 收编，理由写在下面：两支一起收编的判定行数会越过 500 硬上限（单支就已 441），且 DOM 侧的收编还要动 `screens.json` 的词表接线，属独立可交付物。因此本文 Goal/Scope/Acceptance 里「两支」的表述**以本条为准**：本片只对产图侧负责，DOM 侧的验收归 009。当前态文档已按此回写（`Docs/04-ux-design/知芽-TreeLearn-对话页1比1复刻方案-V1.0.md` 的路径变更说明与 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §10「仪器收编」条）。
- Expected outcome: 两支工具进入 `.trae/scripts/dev/parity/`，按屏名从 `screens.json` 的 `prototype` 段取 HTML 路径、点击序列、等待选择器与 DOM 检查项；保留显式参数逃生口；退出码纪律与 Python 侧一致；缺浏览器/缺 HTML 走 3。
- Core flow and reversibility: `git mv` 保留历史 → 参数化并接档案 → 自检 → 证据落盘；整片可回退，不影响 Android 与前端产物。
- Inputs/outputs: 输入为原型 HTML 的 `file:///` 路径（由档案给出）与屏名；输出为逐条 DOM 判据（JSON 或文本）与 2x 渲染截图，均带命令行、退出码、被核文件哈希。
- State and exception handling: 0=跑完且判据全成立、1=跑完且有判据判 FAIL、2=用法错误、3=仪器自身故障（缺 HTML、缺浏览器、CDP 握手失败、未捕获异常）。「浏览器不可用却打印 PASS」判 FAIL。
- Constraints and trade-offs: 零第三方依赖（Node 内置 fetch/WebSocket + Edge headless CDP），不引 Playwright/Puppeteer；不改判定逻辑，只改调用形态。
- AI recommendation and rationale: 与 003 同一做法——沿用已在 2026-09-27 验证过的闭环，而不是新造一套 DOM 量测。
- User choice and custom additions: 用户批准采用 Harness 与阶段 0 范围；本片是 003 拆分出的后半，属已确认范围内的切片。
- AI-autonomous decisions: 切片边界按语言侧分（Python/Node）；`--screen` 与 `--url` 的优先级；握手失败归 3。
- User-confirmation decisions: 无新增待确认项。
- Acceptance evidence: `--selftest` 正反用例清单与退出码；对 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的对话页闭环一次跑通，读数与复刻方案 §9.1 的渲染侧记录逐项可比。
- Facts: 两支文件存在且均已纳入 git（`artifacts/tmp/verify_1to1.mjs`、`artifacts/tmp/cdp_shot.mjs`）；`screens.json` 的 `chat.prototype` 段已在 003 落库，含 `html/clicks/wait_for/dom_checks`；`cdp_shot.mjs` 已有 `exit(2)` 的用法分支，`verify_1to1.mjs` 没有。
- Assumptions: 本机 Edge 可执行文件仍在默认路径、CDP 端口 9222 未被占用（需实测，不假设）。
- Unknowns: 平板档与 PC 档是否需要第三套裁剪参数（沿用 003 的未决项）。
- Risks: 渲染侧判据依赖浏览器版本，跨版本几何可能漂移；因此判据必须同时打印浏览器版本，并把「版本相关文案/几何」当外部契约处理。
- Deferred: 逐屏夹具采集（阶段 1，需真机）。
- Non-goals: 不做 CI 集成、不引浏览器自动化库、不改原型 HTML 的样式。
- Skipped non-critical questions and reason: 工具命名沿用 Python 侧的 `snake_case` 习惯，不实质影响验收，由架构师定名。

## Goal

两支 Node 闭环量测工具在 `.trae/scripts/dev/parity/` 下可按屏名运行，与 003 的像素实测共用同一份屏档案与同一套退出码纪律，任何渲染侧结论都能由自证证据文件重跑。

## Scope

### In scope
- `git mv` 两支工具到 `.trae/scripts/dev/parity/`（保留历史）。
- 参数化：`--screen`（从 `screens.json` 取 `prototype.html/clicks/wait_for/dom_checks`）、`--url`/`--out`/`--profile` 逃生口、输出目录。
- 退出码纪律 0/1/2/3；缺 HTML、缺浏览器、CDP 握手失败一律 3；未捕获异常归 3。
- `--selftest`：每条检测分支各配「注入该违规必须判 FAIL」与「健康样本必须判 PASS」；浏览器不可用分支必须给出可复现的负向注入（如指定的浏览器可执行文件不存在），不得只打印结论。
- 证据文件写入 `artifacts/evidence/`（UTF-8，含命令、退出码、被核文件 SHA-256 与行数，逐行流式写）。

### Out of scope
- 不改 DOM 判定的阈值与算法、不采集新屏的基线、不引第三方自动化依赖。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 保留 2026-09-27 闭环的读数口径（对话页渲染图的 DOM 几何必须仍与像素实测对照表逐项成立）；变化只在调用形态与文件位置。
- Producer/consumer/sibling entrypoints and platform branches: 生产者为两支脚本；消费者为复刻方案 §9.1 的对照表、003 的 `screens.json`、阶段 1 夹具采集脚本；`measure_screen.py` 的 `--kind render` 制式依赖 `cdp_shot.mjs` 的 2x + 滚动条占位这一产图约定，改产图约定必须同步该制式常量。
- Native bridge or independent-client compatibility/update requirements: 不适用，无桥接。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本片改变工具可调用性，需按行为变更取证。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 两支工具的路径与调用方式。
- Authoritative code/config/migration evidence: 新路径下的脚本文件本身、`screens.json` 的 `prototype` 段与其 `--selftest` 输出。
- Documentation surfaces checked: `Docs/04-ux-design/知芽-TreeLearn-对话页1比1复刻方案-V1.0.md`（工具表 §281–292 仍写着 `artifacts/tmp/verify_1to1.mjs`、`artifacts/tmp/cdp_shot.mjs`）、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §10（003 留下的「剩下两支仍在 artifacts/tmp」的 carve-out）、`.trae/tasks/T-2026-10-15-parity-measurement-instruments-adoption-003.md` 与本片自身的范围说明。
- Documentation files updated: 复刻方案工具表的路径行与「路径变更」标注段、规格 §10 的 carve-out 句子（解除「剩下两支」的现在时表述）、`.trae/README.md` 若有工具索引。
- Producer/consumer/sibling/compatibility paths checked: 反向搜索 `artifacts/tmp/verify_1to1`、`artifacts/tmp/cdp_shot` 两串，命中必须清零或显式标注为历史。
- Superseded phrases/examples reverse-searched: 「剩下两支仍未收编」「位于 artifacts/tmp 的 Node 工具」。
- documentation impact: none reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 复刻方案是带日期的实测记录，只更新工具路径引用与变更标注，不改写当年的量测结论。

## Acceptance criteria

- [x] 期望行为：`node .trae/scripts/dev/parity/cdp_shot.mjs --screen chat --out <png>` 退出码 0，并打印本次使用的 HTML 路径、点击序列、浏览器版本与每条判据。（DOM 侧那支的同一形态归 T-2026-10-15-009，见「交付面校正」。）
- [x] 保持行为：对同一张对话页原型闭环重跑，产图读数与复刻方案 §9.1 的渲染侧记录逐项可比；不可比必须给出原因而不是改期望值。
- [x] 相邻失败：缺 HTML 文件、缺浏览器（指定的可执行文件不存在）、CDP 握手失败三种情形分别落到 3/3/3，用法错误落 2，「什么都没验证却打印 PASS」判 FAIL。
- [x] 受影响入口：文档内旧路径不再被写成现位置（只作历史与前身注记）；移动而非重写按「集合包含 + 阈值打印」判定，默认阈值不携带时由三处替代谱系托底。
- [x] 证据文件自证（命令 + 退出码 + 被核文件哈希），UTF-8 合法且无替换字符；屏档案缺 `prototype` 段时判 3 而不是静默空跑。

## TDD execution record

### RED — failing test first
- Test/scenario: 承诺的调用形态 `node .trae/scripts/dev/parity/cdp_shot.mjs --selftest` 在收编发生之前给不出任何自检读数——前身仪器把 `--selftest` 当位置参数里的 URL，只能落用法分支；另配 4 个前置事实块（仪器故障与判据 FAIL 同码、完全没有 `--screen`、写死调试端口 9222、没有用例数下限）。
- Command: `node .trae/scripts/dev/parity/cdp_shot.mjs --selftest`
- Exit code: 2
- Observed failure: 只有 `usage: node cdp_shot.mjs <url> <out.png> [clickSelector...]` 一行，没有任何 SELFTEST 判决行、也没有 `EXITCODE=` 档位行
- Failure reason validated: 成因是「收编尚未发生」而不是「实现有缺陷」，且该读数**不是**在新路径上量的——RED 生成脚本跑时新路径已是实现后的字节，所以它按前身字节逐字相同的归档副本取数：`artifacts/tmp/gen-006-red.py:92` 执行 `node artifacts/evidence/T-2026-10-15-006-pre-adoption-cdp_shot.mjs --selftest`，产物 `artifacts/evidence/T-2026-10-15-006-red.txt:18-20` 记录 `退出码=2 输出首行=usage: …` 与三条 ok（rc 恰好 2 / 无 SELFTEST 判决行 / 无 EXITCODE 行）。该副本与 HEAD 里前身文件的关系由 `gen-006-red.py:61` 现场取 `git show HEAD:artifacts/tmp/cdp_shot.mjs` 落盘（**窗口已过期**：本片提交后 HEAD 不再含该路径，`git show HEAD:artifacts/tmp/cdp_shot.mjs` 现场 rc=128，而生成本产物时归档副本已在，第 61 行的取数分支根本不再触发——复算请指向 `artifacts/evidence/` 的归档字节），提交后由 `gen-006-post-commit.py` 块 03 再判一次「归档副本 == 移除那次提交的父提交上的字节」。今天复现这条读数用 `node artifacts/evidence/T-2026-10-15-006-pre-adoption-cdp_shot.mjs --selftest; echo $?`（应为 2）。其余四块（`T-2026-10-15-006-red.txt` 块 02–05）各给一条对照读数：旧形态对「连不上调试端口」退 1 与判据 FAIL 同码、旧仪器里 `--screen` 命中 0 行而新仪器 21 行、写死 `9222` 1 行而共享层改用端口 0 由浏览器自选、旧形态 `cases=`/下限标记命中 0 处而新形态 3 处。总判定由 want/got 期望表派生：`blocks=5 expectation_entries=5`、`OVERALL_EXITCODE=0`（此 0 表示「缺口已如实复现」，不表示旧仪器可用，见该文件 `:8-10` 的判读约定）。

### GREEN — minimum implementation
- Implementation: `git mv` 前身产图仪器到 `.trae/scripts/dev/parity/cdp_shot.mjs`，把浏览器启动/CDP 客户端/台账/退出码归一抽成共享层 `parity_cdp.mjs`（Python 侧与 DOM 侧共用同一套判定而不是各写一份），改为 `--screen` 从 `screens.json` 的 `prototype` 段取 HTML 路径、点击序列、等待选择器，保留 `--url`/`--out`/`--browser`/`--profile` 逃生口；退出码收成四档 0/1/2/3（仪器自身故障一律 3，含缺 HTML、缺浏览器、CDP 握手失败、未捕获异常）；新增伴随电池 `cdp_shot_selftest.mjs` 走正反双向，并加与清单内容无关的裸字面量下限 `MIN_SELFTEST_CASES=12`。
- Command: `node .trae/scripts/dev/parity/cdp_shot.mjs --selftest`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-15-006-green.txt` 块 02（回读 `grep -n "电池读数\|block 02" artifacts/evidence/T-2026-10-15-006-green.txt`）：`rc=0 cases=17 failures=0 电池判决行=17（ok=17 FAIL=0） tiers=[0, 1, 2, 3]`，且打印的 `cases` 与按行形状归位的 ok-行数一致（条数不是自述）、下限 12 达成、三个档位分支各有见证、伴随文件单独跑给出同一退出码。块 03 另跑一次真实产图闭环：`rc=0 判据行数=6`、`VERDICT: PASS` 与 `EXITCODE=0` 同在、产图尺寸符合 2x 约定 824x1632、滚动条占位与档案声明对上。
- Regression evidence: ① 保持行为——块 04 把本次 DOM/像素读数与 2026-09-27 的当年记录逐行比对，`compared-lines=74 record-lines=74 identical=True first-diff=(none)`（回读 `grep -n "比对读数" artifacts/evidence/T-2026-10-15-006-green.txt`）；② 判据区分力——`artifacts/evidence/T-2026-10-15-006-mutation.txt` 七块、`mutations=6 floor=6 floor-met=True`、`OVERALL_EXITCODE=0`，每条变异都是「拆掉一条守卫，必须有且只有对应判据转红」，含 M1 把「设计内夹具见证」与「本片缺陷」按行形状分开的判据（旧形态命中 3、新形态命中 0、合成真失败仍命中 1）；③ 规模——块 10 判定值 441（`cdp_shot.mjs` 新增行面 144 + `parity_cdp.mjs` 297），伴随电池 74 行按「Tests NOT counted」排除，且该排除是承重的：441+74=515>500，即不分文件就越线；整文件口径 513 一并留档但不混用（回读 `sed -n '/block 10/,/block 10: want/p' artifacts/evidence/T-2026-10-15-006-green.txt`）；④ 提交后历史——`artifacts/evidence/T-2026-10-15-006-post-commit.txt`（回读 `bash`/`python -X utf8 artifacts/tmp/gen-006-post-commit.py`，四块；块 02 按「新路径必须携带移动前旧路径拥有的每一条提交」判，并把配对阈值逐个打印）。

### REFACTOR — optional
- Cleanup: 前身仪器的 136 行位置参数形态全部消失，旧路径引用在文档里只作为「当年的路径」表项与注记存在；`--selftest` 的两条入口（经仪器委派、直驱伴随文件）必须给出同一退出码，因此委派关系不能改成动态导入——ESM 顶层 `await import()` 的环会让动态入口 rc=13 且 stdout 空，该机理由独立夹具现场量出而不是写在注释里。
- Regression evidence: 块 01 三脚本 `node --check` 全 0；块 07 反向搜索 `artifacts/tmp/cdp_shot` 的命中逐个归类（任务/证据/仪器页首前身注记属历史，其余为残留即判红），`git grep` rc 只许 0/1；块 09 改动面对 git 可见且改名形态只在 `-M` 阈值下观测、不当时效判据（同 `T-2026-10-15-006-post-commit.txt`）。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 与 003 共用的纪律：总退出码由 want/got 期望表派生，不得硬写 0；每个判定块除退出码外还要有一条输出存在性见证（003 的块 12 实测证明「只比退出码」会把「变异从未执行」读成「变异被判红」）。
- Edge 可执行文件路径不得写死单一位置；至少要允许 `--browser` 覆盖，并把实际使用的版本打印进证据。
- 中文路径经 argv 传给子进程时按 shell 变量传，不在 here-doc 里写 `\xNN` 字节转义（Python 的 str 字面量里 `\xe7` 是字符而非 UTF-8 字节，实测把文件名拼成 mojibake 后判据退化成「零命中 = 干净」）。

## Verification report

- Verdict: PENDING（等独立复核；实现与证据已齐，四份产物与本片声明逐条对得上）
- Evidence: 四份产物各由自己的生成器产出，总退出码一律由 want/got 期望表派生（不硬写），末行带 `DECODE ok utf8=True cr=0` 与产物自身哈希。复算命令逐条：RED `python -X utf8 artifacts/tmp/gen-006-red.py`（5 块，`OVERALL_EXITCODE=0` = 缺口复现）；GREEN `python -X utf8 artifacts/tmp/gen-006-green.py`（10 块，`OVERALL_EXITCODE=0`）；变异 `python -X utf8 artifacts/tmp/gen-006-mutation.py`（7 块 / `mutations=6 floor=6`）；提交后 `python -X utf8 artifacts/tmp/gen-006-post-commit.py`（4 块；块 01 是前置——该片未提交时判 3「现在还不能认证」而不是判 0）。判定行定位：`grep -n "OVERALL_EXITCODE" artifacts/evidence/T-2026-10-15-006-*.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 验收第 1、2 条的「两支」范围按「交付面校正」缩为产图侧一支，DOM 侧的等价覆盖不落在本文，而由 T-2026-10-15-009 的 `verify_1to1.mjs --selftest`（CLI 层 20 例 + 词表层 18 例）与 `artifacts/evidence/T-2026-10-15-009-green.txt` 给出；被移动的那支前身文件由 `artifacts/evidence/T-2026-10-15-006-pre-adoption-cdp_shot.mjs` 留字节副本，因此 RED 的 rc=2 今天仍可复现。`--selftest` 的两条入口（经仪器委派 / 直驱伴随文件）判定一致，这条替代了「只测一条入口」的旧写法。
- Gaps: ① 平板档/PC 档裁剪参数未验证（沿用 003 的未决项）；② 跨浏览器版本的几何稳定性只在 Edge 一个版本上实测；③ **本片的电池不覆盖公网依赖**：产图侧的 `pos` 用例同样要等页面内的 React+Babel 编译出来，公网脚本没答应时它落 3（009 实测命中，见 009 的 Gaps），因此「渲染侧达绿」隐含「本次网络可用」这一环境前提，该前提目前只在产物头注里打印、没有独立判据。
- Risks: 渲染侧判据依赖浏览器版本；握手失败与页面未渲染完成的区分必须落到不同判据行，否则「超时」会被读成「实现不符」——本片按档位把仪器故障与判据 FAIL 分开（3 与 1），但 `.screen` 等不到时的「没起来 vs 结构不符」二档是 009 才补上的，本片只做到「等不到就判 1 并打印 readyState/href/title」。

## Handoff notes

- Completed: 前置事实核实（前身文件存在且已跟踪、`chat.prototype` 档案段已在 003 落库、原型 HTML 实际存在）；`git mv` + 共享层抽取 + 档案驱动调用 + 四档退出码 + 伴随电池；四份证据产物（RED/GREEN/变异/提交后）；文档回写（复刻方案路径注记、SPEC §10「仪器收编」条按实际交付面改写）。
- In progress: 无——本片交付面已闭合；DOM 侧的收编与证据链在 T-2026-10-15-009。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 009 收编 DOM 侧后，阶段 1 的夹具采集（需真机 ALP-AN00，`adb install -r`，整包跑仪器测试）才有两侧都可重跑的仪器；屏清单与台账的重新生成排在提交后的安静窗口（顺序见 005/007 的 Watch-outs）。
- Watch-outs: ① 不要把「浏览器没起来」压绿成「用例不符」——那是仪器故障（3），不是判据失败（1）；② 本片的提交后产物**只有在该片已提交时才可复算**，块 01 会把「尚未提交」判 3 而不是 0，读者勿把「重跑变红」当回归复发；③ 前身路径 `artifacts/tmp/cdp_shot.mjs` 在任务文件与本产物里是**谱系记录**（配合块 02 的「集合包含」判据与三处替代谱系托底），不是现位置，别按「旧路径引用未清零」去判它；④ 判定值 441 是**移动新增行面 + 共享层**之和，与整文件口径 513 不可混用，也不可再按目录复算（`parity/` 目录现含 003/005/007/009 的产物）；⑤ `gen-006-green.py` 块 07 的第 3/4 条判据要求复刻方案与 SPEC 里都留着「T-2026-10-15-009 收编」这句**过渡期注记**——它量的是「别再写两支都待 006」，代价是把一句会随 009 完结而过期的短语钉成了判据。本轮实测该短语在两处均在位（`python -X utf8 -c` 复算见下），故未放宽；将来清理 §10 的过渡措辞时必须同步把这两条改成「要么在位、要么旧措辞也不在位」的形状，否则本片 GREEN 重跑会假红（复算：`python -X utf8 -c "print('T-2026-10-15-009 收编' in open('Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md', encoding='utf-8').read())"`）。
