# Treelearn Harness Task: 屏幕清单与 1:1 台账骨架生成器

id: T-2026-10-15-005
title: 屏幕清单与 1:1 台账骨架生成器
template_version: 2
task_type: implementation
status: in_progress
parent_requirement: T-2026-10-15-web-h5-android-parity-requirement-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 311
shared_code_touched: false
depends_on: [T-2026-10-15-parity-measurement-instruments-adoption-003.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 1:1 还原的范围（16 屏 + 6 浮层）目前是人在规格里写的数字，而 Android 侧有 32 个屏幕包文件、其中 4 个屏没有布局 XML 是 Java 构建的。靠 `res/layout` 文件列表盘点会系统性漏掉自绘屏，靠人写台账会漏屏且数字不可回读——旧线正是这样让「覆盖完成」变成自我声明。
- Users/scenarios: 阶段 1 夹具采集要按清单逐屏跑；阶段 3–6 每片要引用同一条台账行；收口回归要证明没有沉默条目。
- Expected outcome: 一份从代码可达性派生的屏幕/浮层清单，和一份三层判据台账骨架（结构/视觉/行为），条目文本全部由脚本从捕获输出机械生成。
- Core flow and reversibility: 扫 `android/app/src/main/java/com/treelearn/app/**` 的宿主分派与 `screen/` 包，结合 `res/layout*` 引用与 `PopupWindow`/浮层类，产出 `Docs/04-ux-design/parity/screen-inventory.json`；台账生成器读该 JSON + 仪器输出，产出 `artifacts/parity/ledger.md`。纯工具片，可整片回退。
- Inputs/outputs: 输入为 Android 源码与资源目录、仪器判据行；输出为 JSON 清单与 Markdown 台账。
- State and exception handling: 解析退化必须 fail-closed（发现屏幕数为 0 或低于阈值即判 FAIL）；匹配集为空不得退化成「什么都没验证却打印 PASS」；未知引用（分派到不存在的类）单独打印而不是静默丢弃。
- Constraints and trade-offs: 只读静态分析，不改 Android 代码、不加 Gradle 任务；预期屏数（16+6）作为比对行报出差异，不作为硬编码期望——否则新增一屏就得先改脚本。
- AI recommendation and rationale: 覆盖集由代码可达性算出而不是人抄列表，因为规格里的人写数字已经与实际不一致（原型 v1.1 的三标签叙事与 Android 的两标签现实就是例子）。
- User choice and custom additions: 用户批准阶段 1 首片。
- AI-autonomous decisions: 清单落 `Docs/04-ux-design/parity/`（入库、可被规格引用），台账落 `artifacts/parity/`（每次重生成，含来源提交号与清单哈希）。
- User-confirmation decisions: 无新增待确认项。
- Acceptance evidence: 生成器对当前代码的实测清单（逐条列出类名与布局文件/自绘标记），以及「人为把一个屏的引用改名后清单必须出现差异」的自检。
- Facts: 实测 `android/app/src/main/java/com/treelearn/app/screen/` 有 32 个 `.java`；其中 17 个引用 `inflate`；`ModelSettingsScreen`、`SearchServiceSettingsScreen`、`VoiceSettingsScreen`、`FileManagerScreen` 不在其中（自绘屏，正是必须靠代码可达性而不是布局文件盘点的证据）；资源限定目录只有 `layout-sw600dp`、`values-sw600dp`、`values-v35`。
- Assumptions: 所有屏都经 `MainActivity` 的换屏入口可达；浮层由屏内 `PopupWindow`/自绘浮层类承载。两条都由生成器的实测输出证实或证伪，不作为前提。
- Unknowns: 6 个浮层与代码中浮层类的一一对应关系（本片要产出答案，不是假设答案）。
- Risks: 静态分析漏掉反射/条件分派路径，导致清单比实际可达集小；因此台账必须打印「未被任何入口引用」的类清单，让人看见可疑遗漏。
- Deferred: 真机夹具采集片（需 ALP-AN00 在场），逐屏结构层比对片（阶段 3）。
- Non-goals: 不做 Web 侧代码、不做视觉判定算法、不把台账写成手填模板。
- Skipped non-critical questions and reason: 清单与台账的存放路径属工程细节，由架构师定。

## Goal

产出一份从 Android 代码可达性派生的屏幕与浮层清单，以及一条命令即可重生成的三层判据台账骨架，其条目与数字全部机械派生、可回读。

## Scope

### In scope
- 新增 `.trae/scripts/dev/gen_screen_inventory.py`：扫描宿主分派、`screen/` 包、布局引用与浮层类，输出 `Docs/04-ux-design/parity/screen-inventory.json`，并为每条目记录「承载类 / 布局文件或自绘标记 / 到达入口 / 是否存在 sw600dp 变体」。
- 新增 `.trae/scripts/dev/screen_inventory_selftest.py`：逐条判据配正反自检（合成源码树副本，不动工作区）与 fail-closed 阈值。
- 本片的产物即 T-2026-10-15-007 的输入：台账生成器 `gen_parity_ledger.py`（结构/视觉/行为三列 + 来源提交号 + 清单哈希）已从本片移出，由 007 交付。分片理由是实测：两片各按 `python -X utf8 .trae/scripts/dev/count_production_lines.py <该片的生产文件>` 读数，两个读数相加越过 500 硬上限，因此不合成一片；两片的读数分别登记在各自的 Implementation notes 规模小节，本条不复述数值（抄进第二处必然过期）。

### Out of scope
- 采集真机截图、实现前端组件、修改 Android 生产代码。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 零运行时改动；被改变的只有「覆盖范围由谁判定」——从人写的数字变为代码可达性计算，因此 4 个自绘屏从「可能被漏」变为「必然出现在清单」。
- Producer/consumer/sibling entrypoints and platform branches: 生产者是 Android 源码树（只读）；消费者为阶段 1 采集片、阶段 3–6 还原片与阶段 9 收口；姊妹入口是 003 的仪器（台账的视觉/行为列依赖它的判据行格式）。
- Native bridge or independent-client compatibility/update requirements: 无桥接；台账记录的来源提交号 + 清单哈希即未来「Android 改了界面就转红」这条防腐判据的载体。
- Behavior-neutral non-applicability reason, if applicable: 本片不改运行时，判据变更全部落在工具与文档，仍按规则记录。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无运行时变化；新增「屏幕与浮层的权威清单」这一当前态文档产物。
- Authoritative code/config/migration evidence: `gen_screen_inventory.py` 对 `android/app/src/main/**` 的实测输出。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`（16 屏 + 6 浮层的表述）、`Docs/intent/INTENT-2026-10-15-web-h5-android-parity.md`、`Docs/06-delivery/TreeLearn_V1_交付报告.md`（屏幕可用性叙述）。
- Documentation files updated: 规格 §10 台账小节改为指向生成的清单与台账；如实测数量与 16+6 不符，按实测改写规格数字并在任务里记录差异原因（不得反过来改脚本迎合文档）。
- Producer/consumer/sibling/compatibility paths checked: 检查所有引用「16 屏」「6 浮层」「4 个自绘屏」的文档位置，逐个改为指向清单。
- Superseded phrases/examples reverse-searched: 「按 `res/layout` 文件盘点屏幕」这一做法表述。
- documentation impact: none reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 交付报告属带日期材料，只加链接标注，不改写当年判定。

## Acceptance criteria

- [ ] 期望行为：`python .trae/scripts/dev/gen_screen_inventory.py` 退出码 0，输出的 JSON 每条含承载类、布局文件或自绘标记、到达入口，并逐条打印（数量由打印给出，不由人手写）。
- [ ] 保持行为：003 的仪器自检与既有全部守门（`validate_tasks.py`、`checkers_selftest.py`、`check_design_tokens.py`）保持绿；Android 源码未被修改（`git diff --name-only -- android | wc -l` 为 0）。
- [ ] 相邻失败：把某屏的布局引用在副本里改名 → 清单必须出现差异；人为清空屏幕包 → 生成器必须判 FAIL（解析退化），不得打印空的「全部覆盖」。
- [ ] 受影响入口：4 个无布局 XML 的自绘屏必须出现在清单里并带自绘标记；`layout-sw600dp` 变体存在性逐屏打印。
- [ ] 清单数字全部由打印给出：JSON 的每个 `counts` 项都能从 `screens` / `surfaces` 数组现场重数得出，且同一棵树两次导出的字节相同；清单文件含来源提交号与规格宣称比对行，UTF-8 合法。（台账文件的同等判据由 007 交付并验收。）

## TDD execution record

### RED — failing test first
- Test/scenario: `screen_inventory_selftest.py` 的全套正反自检——清单生成器缺席时不得有任何「用例通过」的读数，且必须落在仪器故障那一层而不是用例不符那一层
- Command: `python -X utf8 .trae/scripts/dev/screen_inventory_selftest.py`
- Exit code: 3
- Observed failure: INSTRUMENT FAULT: gen_screen_inventory.py is not importable next to this script (No module named 'gen_screen_inventory'); no case can run
- Failure reason validated: 成因实测而不是推断。七块 want/got 全对、`OVERALL_EXITCODE=0`、`unexpected count = 0`，末行 `DECODE ok file bytes=N` 由该次运行的 UTF-8 闸门现场打印（N 不抄进正文，回读 `tail -1 artifacts/evidence/T-2026-10-15-005-red.txt`；本文件任何一次编辑都会让红证据重生成时该行位移，抄值必然过期）。① 块 02 用 `os.replace` 把 `.trae/scripts/dev/gen_screen_inventory.py` 真移到 `artifacts/tmp/_red005/gen-aside.py` 后再跑上面那条命令，得 `A absent selftest rc=3` / `A names the missing instrument: True` / `A does not report a case mismatch: True`（stdout 里 `CASE ` 行数为 0，故「红」不可能被读成「断言不符」）/ `A prints its own exit-code line: True` / `A instrument-fault lines=1`，并把原样故障行落进产物：`A fault reading=INSTRUMENT FAULT: gen_screen_inventory.py is not importable next to this script (No module named 'gen_screen_inventory'); no case can run`——上面那条 `Observed failure` 因此是产物里可 grep 的一行，而不是凭记忆转述（`grep -n "A fault reading" artifacts/evidence/T-2026-10-15-005-red.txt`）；同一块 B 直接调被移开的生成器，Python 自己报 `can't open file`、rc=2，说明「跑不起来」与「跑起来判红」在产物里可分。② 缺席必须只有一个通道：块 02 的 D 把同一缺席喂给本片之外的两个消费者入口（`gen_parity_ledger.py --no-write` 与 `parity_ledger_selftest.py`），读数同为 tier 3、同一条 `INSTRUMENT FAULT` 文案，并有 `D same tier at both consumer entries: True` / `D same tier as the 005 selftest: True`（AGENTS.md「同一条规则写在两个入口」族对本片的要求）。③ 块 03 是这条 RED 的承重部分：在 `artifacts/tmp/_r005/` 的同深度副本上量测「把自检里的 ImportError 兜底去掉」后，同一缺席落在 rc=1 且 stdout 0 字节，与「人为把一条判据改成恒假」的 rc=1 **同一层**（`COLLISION rc(R1)==rc(R2) is True - both are the tier a case mismatch uses`），带兜底的生产版本才落在 3（`RESOLVED rc(R3)!=rc(R1) is True, rc(R3)=3`）；RED 因此不是声称加了守卫，而是给出了没有它的后果。④ 块 04 对照臂在仪器在场时跑同一套夹具，得 rc=0、`cases executed=expected`、`failures=0`、`INSTRUMENT FAULT` 行数 0。⑤ 基线独立性：`gen_screen_inventory.py` 本片之前从未入库（块 05 实测 `commits=0 tracked=False`），HEAD 版本无法作独立基线，故注入判据取「起点必须在场」——起点不在场即拒绝并判 3，上一次被打断的残留不会被当成本次基线；还原后 sha256、长度与 `py_compile` 三重比对（`RESTORE equals_pre=True equals_after_len=16373/16373 parses=True`），并由块 03 末尾对 `artifacts/tmp/_red005/prod-before.txt`（本次运行**开始前**的指纹）独立回读一次，不采信驱动自称。

### GREEN — minimum implementation
- Implementation: `.trae/scripts/dev/gen_screen_inventory.py`（判定值只登记在 front matter 与 Implementation notes 的复算命令处，本条不复述。该脚本做四路静态扫描：宿主分派 / `screen/` 包 / 布局引用 / 浮层类，产出 `Docs/04-ux-design/parity/screen-inventory.json` 并逐条打印，规格宣称只作比对行）与 `.trae/scripts/dev/screen_inventory_selftest.py`（19 例正反自检 + 退出码档覆盖断言）
- Command: `python -X utf8 .trae/scripts/dev/screen_inventory_selftest.py`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-15-005-green.txt`（八块全部 want=got、`unexpected count = 0`、`OVERALL_EXITCODE=0`）内的 `SUITE executed=19 expected=19 equal=True failures=0 rc=0` / `SUITE tiers seen=[0, 1, 3] instrument-fault-lines=0` / `SUITE verdict line present=True` / `USAGE rc=2 no judgement printed=True`（块 02：用例数由产物自己打印，本脚本不钉数）；块 01 的真实生产运行 `production run rc=0` 且 `android files changed by the working tree = 0`，三个被 watch 的文件逐条 `unchanged … -> True`；块 03 逐条自述与 `recount … same=True`（清单里每个 counts 项都从条目数组现场重数得出）；块 04 的 `self-drawn FileManagerScreen listed=True flag=True root=None` 与 `layout variant dirs seen by the census: ['layout', 'layout-sw600dp']`；块 06 的 `two exports byte-identical=True` / `measured face equal to the filed inventory=True` / `timestamp-shaped tokens=0`；块 07 的姊妹门 `sibling-gate 007 ledger selftest rc=0` / `sibling-gate 007 ledger generator rc=0`（并要求台账生成器现场打印「清单新鲜度：一致」，即消费者与生产者同意同一张脸）/ `sibling-gate 003 parity selftest rc=0` / `sibling-gate 004 design-token guard rc=0`，与本片的门禁读数 `this task: PASS count=1 WARNING count=1 (size band=1) ERROR count=0`
- Regression evidence: 三层托底，逐层可回读。① 19 例自检本身：`EXPECTED_CASES = 19` 是字面整数（不从 `CASES` 派生），少注册一条即判 3；`{0, 1, 3} <= tiers` 保证三档退出码都有用例，tier 2 由 GREEN 块 02 的真实子进程 `--definitely-not-an-option` 达到；`REQUIRED_ATTRS` 缺项即判 3（判据面不依赖用例）。② `artifacts/tmp/gen-005-mutation.sh` 的变异电池（`artifacts/evidence/T-2026-10-15-005-mutation.txt`，八块全部 want=got）在同深度副本 `artifacts/tmp/_m005/prod/` 上逐条把守卫人为退化，每条**只**让自己声明的用例转红，且 `new-red==declared: True` 逐条打印：M1 `MIN_SCREENS = 0` → `neg-screens-below-floor`；M2 `MIN_SURFACES = 0` → `neg-surfaces-below-floor`；M3 根布局判据 `this, true` 反向为 `this, false` → 四条（`pos-self-drawn` + `pos-hidden-include-proven` + `pos-renamed-layout-ref-diff` + `pos-sw600dp-variant`，实测如此并如实登记）；M4 `sw600dp` 列恒 False → `pos-sw600dp-variant`；M5 提交号加时戳 → `pos-determinism`；M6「方法体不闭合」的 `raise Fault` 换成同形 `print` → `neg-unbalanced-java-body`；M7 隐藏 include 的 `consumer_proven` 恒 True → `pos-hidden-include-unproven`；M8 注释型空变异必须 `caught=False`（否则「任何改动都会红」的电池与「守卫消失才红」的电池读起来一样）。基线红集由块 02 实测写入 `baseline.json`、块 03 现场读取，不假设「副本必然全绿」；块 05 用判定前的 sha256 独立复算「生产字节未动、副本字节等于生产、两份脚本仍可 `py_compile`」，块 06 用 git 现场重读改动面并与运行开始时的快照比对。③ 同一文件的块 04 单独量测「死 include」守卫：把 `raise Fault` 换成同形 `print` 之后，自检**不是**某条用例转红，而是按 `INSTRUMENT FAULT in case neg-dead-include: KeyError: 'nope'` 在 tier 3 中止（读数 `guard-off rc=3 fault-lines=1 names-the-case=True case-verdict-line-printed=False`，还原后 `after-restore copy rc=0 counting-line=True`）——这条红无法归因到单条判据，因此不混进电池，但它同样是「移除即失效」的实测，而不是「移除后静默放行」。④ GREEN 块 05 在真实树副本上做「相邻失败」：改名一处 `R.layout.<root>` → 该屏根布局移到 `_gone` 且孤儿布局被打印（`names the abandoned layout=True`）；清空 `screen/` 包 → `EMPTY rc=1 screens-zero line=True SCREEN lines printed=0`、`EMPTY floor judged-not-ok=True coverage wording=False exit line=True`，即解析退化不会打印「全部覆盖」。

### REFACTOR — optional
- Cleanup: 缺席通道的读数以**实际打印的形状**为准：块 02 原按 `fault=1` 写期望，而 `window005.py` 打印的是布尔判定行，实测令牌改为 `fault=True`；块 05 原写 `print("history %-58s …")`，填充宽度把 `grep -F` 的期望令牌从中间切断（AGENTS.md 既有的「长行失去它本来要证明的数字」形态），改为 `history %s` 后七块一次转绿
- Regression evidence: 电池与交叉核对在**建立过程中**抓到四处缺陷并当场修好，每条都有实测读数托底：① 每条变异的见证令牌原写作 `M1-… rc=1 want-rc=1 caught=True`，而实际打印行在这两者之间还有 `baseline=0`，`grep -F` 因此把八条判据全部报成 MISSING（既有的「令牌不得跨越可变中间段」形态，实测首次运行块 03 即此）；改为整段 `rc=1 want-rc=1 baseline=0 caught=True` 并另加逐条 `new-red==declared: True` 后八条齐。② M3 第一次只声明一条目标用例，实测 `new-red` 是四条 → 电池判 `failing set != declared` 并 FAIL；没有把期望改宽成「只要转红就行」，而是把四条实测值登记进期望集并注明成因（根布局判据反向后，一切依赖「这台屏有根布局」的读数一起失效）。③ 块 07 的按段核对最初用 `chunks.get("block 3")` 取窗，而分隔标记 `\n===== block ` 已经把 `block` 一词吃掉，真实键是 `3`/`4`——取到空串会让「电池窗内无仪器故障」与「块 04 窗内有具名故障」两条**同时空转成假绿**；现先打印 `block sections found=[…]` 并对空窗立即退出。④ `EMPTY floor ok=%s` 打印的是「FLOOR 行里出现 ok=False」这一判定，读起来却像「地板判成 ok」，令牌与打印一并改为 `EMPTY floor judged-not-ok=`。四处同属「证据行静默失去它本来要证明的东西」这一族

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: 分片（不是例外）——见 Implementation notes 的实测规模。TDD 顺序的实情如实登记在该小节：实现先于分片决定存在，RED 是把 `gen_screen_inventory.py` 用 `os.replace` 真移开原位后**真实重跑**同一套夹具得到的，不是补写的。
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 清单是「唯一覆盖判据」的载体，规格与文档只指向它、不复制条目（同一条规则只许一处正文）。
- 规模（判定值一律由仓库自带工具给出，不手搓过滤）：`python -X utf8 .trae/scripts/dev/count_production_lines.py .trae/scripts/dev/gen_screen_inventory.py` → **311**（判定值，本文件 `estimated_production_lines` 即取此值）；`screen_inventory_selftest.py` 按「测试不计入」排除（与 002 排除 `checkers_selftest.py`、003 排除 `parity_selftest.py` 同一口径）。本片与 007 的分片依据是实测而不是偏好：同一命令对 007 的台账生成器另有一个读数（登记在 007 的规模小节，本片不复述——那是对岸的申报值，由对岸负责回读；抄进第二处的必然结果就是本轮实际发生过的过期），两个读数相加越过 500 硬上限，因此按实测分片；分片后两片各自落在 200–500 的 verifier 警告带内。该口径对 `.py` 只做 Java/XML 的注释剥离，因此判定值把注释行也算进去——这是本仓库既有的待裁决项，本片不擅自修改工具。
- 判定值在本文件只出现两处：front matter 与上一条的复算命令读数。判据增加时它会变，其余位置不复述（抄进第二处必然过期，且过期方向不可控）；同一理由，本片的 sha256 与字节数一律写复算命令，不写值。
- 清单 JSON 与台账文件是生成物，不计入判定面（与 004 排除 `tokens.css`「生成器产物、可逐字节重生成」同一口径）。
- 「自绘屏」的定义是**代码层能判的那件事**：没有一个 `R.layout.X` 被以 `this, true` 充气成自己的根视图（`ROOT_INFLATE`）。实测 14 屏里 5 个如此：文档点名的 4 个（`FileManagerScreen` / `ModelSettingsScreen` / `SearchServiceSettingsScreen` / `VoiceSettingsScreen`）加 `HomeDrawerScreen`——后者确实调 `inflate()`，但目标是被 `addView(view, panel, false)` 挂进去的子视图，不是它自己的根，所以按「可达性」它必须出现在清单、按「有没有根布局」它没有。GREEN 块 04 因此做**双向**比对（代码派生集合 ⇄ 清单标记），而不是只核对文档那份名单，否则第四屏之外的成员会被当成脚本缺陷而不是事实。
- 数量全部现场打印：`counts` 的每一项都能从 `screens` / `surfaces` / `components` 数组重数得出（GREEN 块 03 逐条重数并要求 `same=True`，含 `screen_package_files = len(screens) + len(components)` 这类跨数组的合成值），规格宣称只作为 `SPEC_CLAIM … diff=-2/+4` 一行比对存在，脚本不迎合它。
- 实测与拍板仍不一致（**留给用户裁决，不改脚本凑数**）：清单给 14 屏 + 10 浮层，2026-10-15 的拍板与规格正文写 16 屏 + 6 浮层；脚本只把它登记成一行比对 `SPEC_CLAIM ... diff=-2/+4`（`spec_claims` 从规格正文取数、`spec_comparison` 只做减法，不迎合）。浮层一侧的构成现场打印：`popup_window 7 / dialog 1 / included_hidden 1 / code_drawn_host 1`。**差异原因本片不下结论**——`grep -n "16|屏幕清单|screen-inventory"` 在规格里只命中拍板数字本身（第 12、22、110、112、126 行），没有逐条屏名单，因此「16 与 14 具体差在哪两屏」无法从现有产物核实，须由采集片逐屏对照后再登记（见 Gaps）。「16+6 这个拍板数字要不要就地改成 14+10」同样待用户确认。
- 仪器缺席的通道问题是在建本片 RED 时**顺带**发现的真缺陷：`gen_parity_ledger.py`（007 的产物）在 `gen_screen_inventory.py` 被移开时走未捕获 `Fault` 追溯、rc=1，与「某条判据不成立」同层。已把兄弟仪器的装载移进故障守卫，并让本片 RED 块 03 在 `artifacts/tmp/_r005/` 的同深度副本上把这条碰撞**量测**出来（无守卫 rc=1 且 stdout 0 字节 ⇔ 强制恒假 rc=1；带守卫 rc=3），007 侧新增 `neg-inventory-instrument-absent` 用例与变异 M8 各自托底。这是「同一条规则（缺席）写在两个入口给出不同通道」那一族的第四次命中。
- 已知局限（如实登记）：① 静态可达集依赖 `MainActivity` 里能识别的三种分派形状（`new XScreen(this|getContext())`、`swap(field)`、`addView(field)`），反射或别的分派形状会漏，漏的方向是「清单少」而不是「假装覆盖」，并由 `UNREACHED_SCREEN` 行与 `counts.unreached_screens` 暴露（当前实测 0）；② `layout-sw600dp` 变体列只回答「有没有」，不回答「改了什么」，平板差异内容由后续采集片负责；③ `component_classes=18` 是 `screen/` 包内非 `*Screen` 类的全集，它们不自动等于「需要 1:1 复刻的界面」，台账不替人做这个判断。

## Verification report

- Verdict: PENDING — 本节其余四条已由实现者按实测填妥，但**判决只能由独立 verifier 复跑后给出**（与 004/007 同一做法：实现者自述不充当 PASS，`status` 因此停在 `in_progress`）。实现者侧可确证的部分：三份证据的总退出码全部由 want/got 期望表派生且 `unexpected count = 0`，本片不声称任何未由产物支撑的「全部通过」。
- Evidence: `artifacts/evidence/T-2026-10-15-005-red.txt`（7 块，`blocks=7 expectation_entries=7 floor=7`）——RED 由**真的把 `gen_screen_inventory.py` 移开**产生，不是模拟：块 03 量测「没有守卫时同一缺席落在哪一层」，给出 `COLLISION rc(R1)==rc(R2)`（仪器缺席与强制恒假判定同层）与 `RESOLVED rc(R3)!=rc(R1)`（移进故障守卫后独立成 3 档）；块 02 给出缺席在三处消费者入口同为 3 档、`MIN_SCREENS` 令牌失去唯一发射者，以及 `RESTORE equals_pre=True equals_after_len=16373/16373 parses=True`（byte-exact 还原，基线取本次开始前的在场起点——该片文件从未入库，HEAD 无法作基线，理由见 RED 段 ⑤）；块 04 是对照臂（仪器在场时 rc=0），块 05 实测三份产物 `commits=0 tracked=False`；块 02 的 `A fault reading=INSTRUMENT FAULT: gen_screen_inventory.py is not importable next to this script …` 是任务文件 RED 段 `Observed failure` 的原文来源（逐字可在产物里 grep，不是转述）。`artifacts/evidence/T-2026-10-15-005-green.txt`（8 块，`unexpected count = 0`）——逐块打印 `SUITE executed=19 expected=19 equal=True failures=0 rc=0`、`SUITE tiers seen=[0, 1, 3] instrument-fault-lines=0`、`USAGE rc=2 no judgement printed=True`、`copy files=322 longest path=113`、`EMPTY rc=1 screens-zero line=True SCREEN lines printed=0`、`two exports byte-identical=True`、`timestamp-shaped tokens=0`、`this task: PASS count=1 WARNING count=1 (size band=1) ERROR count=0`、`android diff lines=0`。`artifacts/evidence/T-2026-10-15-005-mutation.txt`（8 块）——电池基线 `BASELINE rc=0 … failures=0 instrument-fault-lines=0` 与 `BASELINE failing-cases=[]`，8 条变异逐条打印 `rc / want-rc / baseline / caught / new-red / expected-new` 加一行 `new-red==declared: True`，块 04 单独量测「拆掉守卫不是让一条用例变红，而是让仪器中止」，块 07 做 RED/GREEN/MUT 三份产物的跨文件一致性回读（含 `SUITE executed=19 expected=19` ⇔ `BASELINE rc=0 executed=19 expected=19`）。三份都由生成它们的运行自己过 UTF-8 闸门，末行 `DECODE ok file bytes=N` 由闸门现场打印；按「指纹类值只写复算命令不写值」，N 不抄进正文，回读 `tail -1 artifacts/evidence/T-2026-10-15-005-<red|green|mutation>.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除任何既有断言。改动集中在**通道**而不是判据：① 兄弟仪器 `gen_parity_ledger.py` 装载 `gen_screen_inventory.py` 的 try 注入进既有故障守卫（判据未动，新增的是 `neg-inventory-instrument-absent` 用例，因此 007 的自检用例数增加一例——现值只登记在 007 自己的 Implementation notes，本片不复述）；② 本片自检的 `EXPECTED_CASES = 19` 是与清单无关的裸字面量下限，且结构检查同时钉住 `{0, 1, 3} <= tiers` 与 `MIN_SCREENS`/`MIN_SURFACES` 两处字面量，所以「把下限调成 0」会被自己抓住——变异 M1/M2 实测：两处归零后 `neg-screens-below-floor` / `neg-surfaces-below-floor` 各自转红；③ 唯一一次判据**更名**是把 GREEN 块 05 的见证行从 `EMPTY floor ok=` 改为 `EMPTY floor judged-not-ok=`，值本身仍是 `"ok=False" in out2`，改的是易误读的反向措辞而不是判定；④「既有 19 例仍符合预期」不靠人眼：块 02 逐行比对执行数与期望数并打印 `equal=True`，块 06（GREEN）另外要求两份导出逐字节相同，覆盖 `pos-determinism` 所声称的那件事。
- Gaps: ① 规格 16 屏与实测 14 屏的差额**未逐条对应**（规格里没有屏清单，`grep -n "16|屏幕清单|screen-inventory"` 只命中拍板数字本身），因此本片只登记 `SPEC_CLAIM … diff=-2/+4` 与浮层构成，不解释成因，也不擅自把拍板值改成实测值——待用户裁决；② `layout-sw600dp` 只回答「有没有变体」（实测 6 屏有），**不回答改了什么**，平板内容属采集片；③ 静态可达集只认 `MainActivity` 的三种分派形状，反射或别的分派会漏，方向是「清单少」而非「假装覆盖」，由 `counts.unreached_screens=0` 与 `UNREACHED_SCREEN` 行暴露；④ `component_classes=18` 与「需要 1:1 复刻的界面」不是同一集合，台账不替人做这个判断；⑤ 本片不判浮层的**视觉**与**行为**，两列仍由 007 的台账标为缺证据。
- Risks: ① 清单是后续所有片「覆盖判据」的唯一载体，若有人手工编辑 `Docs/04-ux-design/parity/screen-inventory.json` 就会被下一次生成覆盖，而**当前没有提交钩子强制**——强制力要等 006 之后；② 静态分析的可达集偏小会让「全覆盖」结论虚高，兜底是逐条打印 + 未引用类清单让人看见可疑遗漏；③ 本片的规模申报落在 200–500 警告带（判定值见 front matter，复算命令见 Implementation notes 规模小节），与 007 的读数相加越过硬上限，分片理由已实测登记，若下一轮再加判据仍可能顶破，届时应再拆而不是求例外。

## Handoff notes

- Completed: 清单生成器（判定值见 front matter）+ 19 例正反自检 + 台账投影落地；`Docs/04-ux-design/parity/screen-inventory.json` 由脚本生成且两份导出逐字节相同；RED / GREEN / 变异三份证据各自自带命令行与退出码、块数与期望表条目数相等、`unexpected count = 0`。前置事实全部实测并留在 Implementation notes：32 个屏包文件、14 屏（其中 5 屏无根布局充气，含文档未点名的 `HomeDrawerScreen`）、10 个浮层按 `popup_window 7 / dialog 1 / included_hidden 1 / code_drawn_host 1` 分列、44 个布局文件、6 屏有 `sw600dp` 变体、未触达屏与未引用布局均为 0。电池顺带抓出并如实登记了一件**不是缺陷而是通道差异**的事实：拆掉 dead-include 守卫不会让某条用例变红，而是让仪器在 `neg-dead-include` 里抛 `KeyError: 'nope'` 并以 3 档中止（块 04 单独量测 `guard-off rc=3 fault-lines=1 names-the-case=True case-verdict-line-printed=False`，还原后 `rc=0`），因此这类判据的归属**不能**折进电池的单用例红集里——这是本仓库「harness 崩了 ≠ 用例不符」共用退出码那一族的又一次实测命中。
- In progress: 无。本片交付面已完成，状态停在 `in_progress` 等独立 verifier 复跑；对 007 的申报同步（规模判定值、自检用例数、合并读数三处都按现场回改写，出处见 007 的 Implementation notes 与 Handoff 回写段）与本片三份证据的重跑是同批收尾动作，本轮已全部做完。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: ① 独立 verifier 只读复跑本片三份证据；② 006 收编两支 Node 仪器（`verify_1to1.mjs` / `cdp_shot.mjs`，档位 3/3/3 + 2 条用法），台账的「未收编」行才会清空；③ 之后再建阶段 1 的真机夹具采集片（依赖 ALP-AN00 可连，仪器测试必须整包跑、不能用 `--tests` 过滤）；④ 用户裁决 16+6 与 14/10 的口径归一。
- Watch-outs: ① 不要为了凑「16 屏」这个数字去改脚本；数字来自打印，不来自规格。② 改清单字段前先想 007 的 `projection()`：字段改名或删字段必须回跑 007 自检，否则漂移只会在台账里以「空列」形态出现。③ 本片的 `MIN_SCREENS = 8` / `MIN_SURFACES = 3` / `EXPECTED_CASES = 19` 是与被判数据无关的裸字面量，把它们改成由清单推导（`len(...)`）等于取消下限——这是本仓库第四次命中「用被检清单算自己的下限」。④ 任务文件里任何数字被证据引用，重跑证据即在改动面上改变该文件字节：本片的申报值、验证报告与 Implementation notes 都写复算命令而不是抄值。
