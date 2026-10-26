# Filename: T-2026-10-25-ui-asset-parity-judgment-088.md
# Codex Harness Task: 给「Web／exe 与 Android 的字面等价」和「类名存活率」装上会判红的守门

id: T-2026-10-25-088
title: 随包字体与类名存活率的静态判据
template_version: 2
task_type: implementation
status: in_progress
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: harness
owner_lane: harness
stack: harness
estimated_production_lines: 300
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 线要在 22 条评审发现落地前先建判据。本片是两片判据里的静态面：把「Web／exe 有没有随包衬线」「兜底栈在 Windows 上会落到哪」「样式表里的类名是不是活的」三件目前**零报警**的事，量成一条会判红的守门。
- Problem/background: 086 的 X1 与 X4 有一个共同形状——缺陷只在代码里，界面上一切正常。评审机装了 Noto Serif SC，所以 2026-10-25 那批 Web 截图的每一张衬线标题都是「对的」，而同一份产物拿到没装该字体的 Windows 上就是宋体；`app.css` 那 425 行更是直接被浏览器忽略，肉眼永远看不出它死了。仓库里颜色有 `check_design_tokens.py`、玻璃深度有 `check_glass_depth.py`，随包字体与类名存活率**没有任何守门**，所以这两条既不会被发现，也不会在被修掉之后防止复发。
- Users/scenarios: 下一位改字体栈、加样式表、或把图标／资源搬到 Web 面的人（含 AI）；以及 086 线的 090（X1 修复）与 093（X4 修复）两片，需要一个「先红后绿」的靶子而不是等人眼。
- Expected outcome: `python -X utf8 .codex/scripts/dev/check_ui_asset_parity.py` 在当前仓库上判出与 X1／X4 一一对应的红条，并给出可读的 file:line 级细节；`--selftest` 让每条判据都有至少一个负例，防止「判据永远绿」这种假仪器。
- Core flow and measurements: 本卡只新增一个只读脚本，不改任何运行时行为。回退＝删掉 `.codex/scripts/dev/check_ui_asset_parity.py`；脚本尚未进注册表，所以删掉不会在别处留下悬空引用。无数据迁移。
- Inputs/outputs: 输入是 `frontend/src/styles/tokens.css` 的 `--serif` 栈、`frontend/src/styles/*.css`、`frontend/src/**/*.{ts,tsx}`、`frontend/index.html`、`frontend/dist-offline/index.html`（存在才判）、`android/app/src/main/res/font/noto_serif_sc.otf`。输出是 `OK/FAIL/NOTE` 逐条 + `check_ui_asset_parity: N check(s) failed` + `EXITCODE=`。
- State and exception handling: 权威文件读不到、`tokens.css` 里没有 `--serif`、栈里第一个引号族名都不存在——这些是**仪器故障**（EXITCODE=3），不是产物判红；沿用仓库纪律「没判」不等于「通过」。产物面缺失走 `NOTE` 出声而不是 `OK`。
- Constraints and trade-offs: 不新增依赖（只用 stdlib 与仓库内 `harness_check_lib`）；不改 Android、不改后端、不动任何 CSS；豁免名单 `DEAD_CLASS_ALLOWLIST` 每条必须带 >8 字理由且由自检守住，防止用它吞掉真红；A4 在没有内联字体时打 NOTE 而不是第二条红——同一个成因只能由 A1 判一次，否则红条数会虚报。
- AI recommendation and rationale: 判据先行（086 的拍板口径）。理由是 X1／X4 都属于「修好之后更容易复发」的一类：字体栈少写一个族名、样式表再留一张死表，界面上都不会出错，只有机械判据能长期按住。
- User-confirmation decisions: 用户「采用 harness 进行优化」＋范围「22 条全做」，其中判据先行是 086 卡面写明并被认可的实施顺序。
- AI-autonomous decisions: ① 白名单 `WIN_SERIF_FAMILIES` 只收宋体族（SimSun／NSimSun／SimSun-ExtB／宋体），楷体仿宋是本项目显式放弃的身份、黑体雅黑是无衬线，收进来等于换设计意图；② 上限 6 MiB（子集口径 3–5 MiB + 余量）；③ `tokens.css` 从类名扫描里排除——它是四方链生成物，不拥有类名；④ 本片**不入** `run_all_guards.py` 注册表，注册由 104 片在全部判据转绿后统一做。
- Acceptance evidence: `--selftest` 14 例全绿；仓库面判出 4 条红且逐条能映射到 086 表的代号；`088-red.txt` 与 `088-dead-classes.txt` 落盘；抽样反查 8 个被判死的类名在 `.tsx`/`.ts` 命中全为 0（排除误报）。
- Facts: 首跑读数（`artifacts/evidence/ui-readability-parity-2026-10-25/088-red.txt`）——A1 红：`--serif` 首选族 `'Noto Serif SC'` 在 `frontend/src/styles` 没有任何 `@font-face`；A2 红：栈里没有 SimSun 族；A3 红：扫描 6 张样式表，`app.css` 29 个 + `glass.css` 1 个（`rec-tag`）无消费者，共 30 个；A5 红：产物面 `@font-face` 没有、外部字体引用 0 处；A6 绿：`noto_serif_sc.otf` 24,427,224 字节仍在。
- Assumptions: 一个类名只要在任一 `.tsx`/`.ts`/`frontend/*.html` 的字面量里作为整词出现过，就算有消费者。这个判据偏宽（宁可漏报死码也不误报），因此「判出来死的」可信度高。
- Unknowns: 无会改变范围或验收的未知项。
- Risks: A3 的宽判据意味着将来若真的用 `class={"a" + b}` 拼类名，可能仍被判活（漏报）；这是有意取舍，反过来会误报并诱使人放宽判据。
- Deferred: 图标 pathData 的两端单源判据不在本片，放 091（X2）——它需要先决定「单一来源放哪」，与本片的资产面判据不是同一个改动面。
- Non-goals: 不改任何 CSS／TSX／layout；不修 X1 与 X4 本身（那是 090 与 093）；不引入字体子集工具链。
- Skipped non-critical questions and reason: 未问「6 MiB 上限够不够」——由 090 真跑一次子集后回看，若不够再显式改这个裸字面量并在卡片记原因。

## Goal

让「Web／exe 与 Android 的字面是否真的等价」与「样式表类名是否还有消费者」变成一条不需要人眼、当前就判得出红的机械判据。

## Scope

### In scope
- 新增 `.codex/scripts/dev/check_ui_asset_parity.py`（6 条判据 A1–A6 + 14 例自检）。
- 证据落盘 `artifacts/evidence/ui-readability-parity-2026-10-25/`。

### Out of scope
- `run_all_guards.py` 注册表（→104）；任何被产物面的修改（→090／093）；图标 pathData 单源（→091）；渲染态判据（→089）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增只读脚本，运行时行为零变化；必须保留 `check_design_tokens.py` 对 `tokens.css` 的逐字节比对（本片不写 `tokens.css`）、`check_offline_bundle.mjs` 的 CDN 判据（本片与它同向但不复用它的判定，避免把「外部加载」与「随包缺失」混成一条）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `.codex/scripts/dev/check_ui_asset_parity.py`；消费者目前只有人／子卡（090、093 用它作 RED/GREEN 靶）；同族判据 `.codex/scripts/dev/check_design_tokens.py`（四方 token 链）、`frontend/scripts/check_offline_bundle.mjs:55`（`data:` 放行，正是本片 A1 允许内联的前提）。共享库 `harness_check_lib.Checks`／`enable_utf8_stdout`／`REPO_ROOT` 三段式与 `check_design_tokens.py:29-34` 同形。
- Native bridge or independent-client compatibility/update requirements: 无。A6 只是读 Android 资源是否存在，不触碰 Android 构建面。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 无运行时事实变化；新增一条「Web 面必须有随包衬线、类名必须有消费者」的**规则性判据**，后续片改这两面时必须过它。
- Authoritative code/config/migration evidence: `.codex/scripts/dev/check_ui_asset_parity.py`（A1 `judge()` 的 @font-face 分支、A2 `WIN_SERIF_FAMILIES`、A3 `dead_classes()`、A4 `INLINE_FONT_MAX`、A5 `BUNDLE` 分支、A6 `ANDROID_FONT`）；判据落点：`frontend/src/styles/tokens.css:71`、`frontend/src/styles/app.css`（425 行）、`frontend/src/styles/glass.css:172`、`frontend/src/main.tsx:11`、`frontend/dist-offline/index.html`、`android/app/src/main/res/font/noto_serif_sc.otf`。
- Documentation surfaces checked: `.codex/rules/ux-design.md`、`.codex/rules/frontend.md`、`.codex/ROADMAP.md`、`Docs/04-ux-design/design-system/README.md`、`.codex/scripts/run_all_guards.py` 的注册表与分档注释。
- Documentation files updated: 本卡的 Documentation impact record；`run_all_guards.py` 头注释在 104 片注册时一并改（本片不动它）。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact。
- Superseded phrases/examples reverse-searched: `tokens.json` 的 `$note` 现写「Web 侧仍由 --serif 回退栈与原型 CDN 承担」——本片判据把「CDN」定为不合法 src（A1 的 remote 分支），这句在 090 落地后变成假话，改写动作登记在 090 而不是本片（本片未动产物面）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写既有卡片；086 的落点表已把本片的编号从 087 更正为 088（087 被并行线占用）。

## Acceptance criteria

- [x] `--selftest` 全绿且每条判据至少一条负例（A1 四条、A2 两条、A3 两条、A4 两条、A6 一条）。
- [x] 在当前未修复的仓库上判红，红条与 086 的 X1／X4 一一对应：A1+A5=「Web／exe 没有随包字面」，A2=「兜底栈在 Windows 上不可控」，A3=「死样式表」。
- [x] 仪器故障与产物判红分开：`tokens.css` 缺 `--serif` 时 EXITCODE=3 且打 `FAULT`，不打 PASS。
- [x] A3 的 30 条判红抽样反查无误报（8 条抽样的 `.tsx`/`.ts` 命中数全为 0）。
- [x] 不改任何 CSS/TSX/layout/Android；`git status` 只出现新脚本与证据目录。
- [x] `python -X utf8 .codex/scripts/validate_tasks.py` errors=0。

## TDD execution record

### RED — failing test first
- Test/scenario: 自检的负例集合先行——「A1 无 @font-face 必须红」「A1 把 CDN 当随包字体必须红」「A4 超上限必须红」「A2 塞无衬线族名不能糊过」「A3 无消费者必须红」「A6 参照字体被删必须红」。
- Command: `python -X utf8 .codex/scripts/dev/check_ui_asset_parity.py --selftest`
- Exit code: 1
- Observed failure: 三轮真红。① 第一版 `TypeError: can only concatenate str (not "bool") to str`（自检把 `(名称, 成立)` 元组按 `(成立, 名称)` 解包，仪器自己崩在第一行判据之前）；② 修好后 `FAIL SELFTEST A4 超过上限时必须判红而不是沉默`——根因是 `src` 用 `src\s*:[^;]*;` 提取，而 `data:font/woff2;base64,…` 里本身带分号，6 MiB 载荷被读成 24 个字符，A4 会**永远读到一个极小的数而长期判绿**；③ 再修后 `FAIL SELFTEST A1 把 CDN 当随包字体时必须判红`——A1 只判「src 里有没有 url()」，于是 `url('https://…')` 这种最典型的违规被判成合格随包字体。
- Failure reason validated: 三条都不是测试写法问题：②③ 是判据自身的漏判，且都是「**该红不红**」这一类最危险的形状——如果先跑仓库面再看自检，仓库面的 A1 红会被误当成「判据有效」，而它对 CDN 与超限载荷其实完全不设防。负例先行正是为了在这里出声。
- Evidence: `artifacts/evidence/ui-readability-parity-2026-10-25/088-red.txt`（仓库面 4 条红）与 `088-dead-classes.txt`（30 个类名逐条）。

### GREEN — minimum implementation
- Implementation: `.codex/scripts/dev/check_ui_asset_parity.py`，6 条判据：A1 衬线身份随包（族名必须来自 `--serif` 第一个引号项；src 只能是 `data:` 或存在的包内路径；remote／缺失／无 url() 三种形状各自判红）；A2 兜底栈含 Windows 中文衬线（白名单 4 项）；A3 类名必须有消费者（扫 6 张样式表，`tokens.css` 排除，豁免名单每条带理由）；A4 内联字体 ≤ 6 MiB（无内联时打 NOTE 不重复计红）；A5 产物面同字面且外部字体引用为 0；A6 Android 参照字体必须仍在（防「删掉 APK 字体来让两端一致」的删除式达标）。
- Command: `python -X utf8 .codex/scripts/dev/check_ui_asset_parity.py --selftest`
- Exit code: 0
- Passing evidence: `selftest judgement cases executed = 14`、`judgement tally` 全 OK、`check_ui_asset_parity: 0 check(s) failed`、`EXITCODE=0`。
- Regression evidence: 仓库面复跑仍判 4 红（这是**期望结果**：判据已生效而缺陷仍在，由 090／093 消除）；`python -X utf8 .codex/scripts/run_all_guards.py --tier core` 不受影响（脚本未注册）；`validate_tasks.py` errors=0。

### REFACTOR — optional
- Cleanup: 自检从「手写元组顺序」改为具名 `judge_quiet(root)`，三处重复的 stdout 重定向收成一个函数；删掉不再使用的 `SRC_IS_DATA_RE`（A1 改成对 url() 分类后它就是死码）。
- Regression evidence: 复跑 `--selftest` 14 例仍全绿。

### LOOP — optional
- Command and rounds: `--selftest` 四轮 + 仓库面两轮。
- Failure identity and repeat count: 见 RED 的三条，各一次，是三个不同缺陷（解包顺序 / src 截断 / CDN 漏判），不是同一例重复失败。第四轮补出两条新负例（「src 指向不存在的包内文件」、「src 里一个 url() 都没有」）以钉住 A1 的分类面。
- Stop condition hit: 每条判据都有至少一条「该红必须红」的负例后停止；A3 抽样 8 个类名反查 `.tsx`/`.ts` 命中全 0，确认没有把活类名读成死码。
- Handoff: 090（X1）以 A1／A2／A4／A5 为 GREEN 靶；093（X4）以 A3 为 GREEN 靶；注册进 `run_all_guards.py` 由 104 统一做。
- Evidence on disk vs console-only: 落盘的是 `088-red.txt` 与 `088-dead-classes.txt`。**自检的三轮 RED 读数只在终端读出，没有落盘转录**——它们是修复过程的记录，不是当前判据的凭据；当前态由 `--selftest` 的 14 例在每次运行时重跑，其中「A4 超过上限时必须判红而不是沉默」与「A1 把 CDN 当随包字体时必须判红」两条就是那两个已修缺陷的常驻钉子。按 085 立的口径把这一点写明，免得下一位读者去证据目录找不存在的转录。

### TDD or sizing exception — only with prior user approval
- Exception type: sizing-warning（非例外，登记口径）
- Reason: 400 行（非空非注释 322 行）超 200 行的软目标，但它是**一个内聚的判据仪器**：A1–A6 共享同一套 `--serif` 解析与同一份 fixture 构造，拆两半会让「字体栈解析」这套实现出现两份，正是 085 之后规则反对的漂移源。自检 14 例与文档注释不计入生产行。
- User approval: 不适用（超 500 行的硬失败线未触及，软线只触发 WARNING）。
- Alternative verification: 不适用。

## Implementation notes

- `REPO_ROOT` 走 `os.path.dirname(os.path.dirname(os.path.dirname(HERE)))` 三段式（与 `check_design_tokens.py:30` 同形）。本片第一版把它算成了 `.codex`，于是 `frontend/src/styles` 找不到、脚本打 `FAULT … VERDICT: INCONCLUSIVE`——形状与「产物判红」完全不同，正好验证了退出码 3 这条路是真的在用。
- A4 与 A1 的分工是刻意的：A1 判「有没有随包字面」，A4 判「随包的字节有没有失控」。没有 A4 的 A1 允许有人把 23 MiB 全量 otf 直接 base64 进来「达标」，离线包会从 0.50 MiB 涨两个量级。
- A6 是反删除式达标：如果将来有人为了让「两端字面一致」而删掉 APK 里的字体，Web 面的 A1 可能已经绿了，而两端反而都没有字面。这条判据单独守这个方向。
- `Checks.note` 的语义就是为本片这种「前置条件不成立」准备的（`harness_check_lib.py:312` 的正文：没有可判对象时打 OK 会被读成认证通过）。
- 豁免名单当前为空表。093 若确认某个类名是运行时拼出来的，必须在这里带理由登记，而不是把 A3 改成「按文件白名单跳过」。

## Verification report

- Verdict: PENDING（待独立复核）
- Evidence: 静态判据面——`--selftest` EXITCODE=0、14 例；仓库面 `EXITCODE=1`、4 条 FAIL、1 条 NOTE、1 条 OK，逐条对应 086 的 X1／X4；抽样反查 8 个类名命中 0。运行时行为面：本片不改产物，故无浏览器／真机证据需求，`git status` 只见新脚本与证据目录。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片只新增判据，未改动或删除任何既有断言。
- Gaps: ① A3 的「消费者」按整词字面量匹配，动态拼接（`"chat-" + kind`）会被判活而漏报，不会误报；② A2 的白名单只有宋体族，若将来出现带中文衬线的其它 Windows 版本，需要显式扩表并写明依据；③ 脚本尚未进注册表，所以在 104 之前它不会被 `run_all_guards.py` 自动跑到，只能由子卡逐片调用。
- Risks: 无对运行时的影响。主要风险是**它长期判红而无人注册**——那等于判据白建，因此 104 片的注册前置条件已在 086 写明。

## Handoff notes

- Completed: 6 条判据 + 14 例自检，仓库面判出 4 条与 X1／X4 对应的红，两份读数落盘。
- In progress: 089（渲染判据）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 090 用 A1／A2／A4／A5 作 GREEN 靶（子集内嵌 + Windows 兜底栈）；093 用 A3 作 GREEN 靶（删 `app.css` 并处理 `glass.css:172` 的 `rec-tag`）；104 在所有判据转绿后注册进 `run_all_guards.py`。
- Watch-outs: ① 别为了转绿去改 `WIN_SERIF_FAMILIES` 或 `INLINE_FONT_MAX` 这两个裸字面量——它们是判据，动它们要按 085 的口径在卡片里写明理由；② `data:` 载荷的分号会截断 `src:` 解析，将来改 `font_faces()` 必须保持「src 取到块尾」这个修法，自检的超限负例守着它；③ 090 落地时会同时改 `tokens.css` 的生成源（`tokens.json` 的 `serif` 值），必须走 `gen_design_tokens_css.py` 重生成，不许手改生成物。
