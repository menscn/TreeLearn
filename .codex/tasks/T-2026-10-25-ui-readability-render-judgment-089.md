# Filename: T-2026-10-25-ui-readability-render-judgment-089.md
# Codex Harness Task: 把「最小可见字号」与「每行汉字数」量成渲染态判据

id: T-2026-10-25-089
title: 可读性渲染判据（最小字号地板与阅读行长上限）
template_version: 2
task_type: implementation
status: in_progress
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: harness
owner_lane: harness
stack: harness
estimated_production_lines: 230
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md, T-2026-10-25-ui-asset-parity-judgment-088.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 线的第二片判据。静态面（088）能判「声明了什么」，判不了「落到像素上是什么」；本片把 X3 与 B1 变成渲染态读数——真实 Chromium 里遍历可见文本节点取最小计算字号，并用容器内宽 ÷ 计算字号得出每行汉字数。
- Problem/background: 086 的 X3 是 token 声明（`fs-cap: 11px`），但声明值不等于生效值：一条局部覆盖就能把 12 拉回 11，静态比对看不出来。B1「48 个汉字一行」更是纯几何量，CSS 里根本没有这个数——它由 `.chat-answer` 的容器宽与 `--fs-body` 共同决定，只有渲染后才存在。两支既有判据（`check_design_tokens.py` 管取值、`check_glass_depth.py` 管像素观感）都不覆盖这两件事。
- Users/scenarios: 092（X3 字号档）与 094（B1 行长）两片需要一个「先红后绿」的靶；以及任何之后改字号或版式的人，需要一个会在他改小之后报警的东西。
- Expected outcome: `node .codex/scripts/dev/check_ui_readability.mjs` 在离线产物上直接 `file://` 起页面（不起 dev server、不联网），判出 R1（最小可见字号）与 R2（每行汉字数）两条红，并**逐条点名**低于地板的选择器与其上的实际文字。
- Core flow and measurements: 本卡只新增只读脚本。回退＝删掉 `.codex/scripts/dev/check_ui_readability.mjs`；未进注册表，删掉不留悬空引用。无数据迁移。
- Inputs/outputs: 输入 `frontend/dist-offline/index.html`（不存在＝仪器故障 3，不判 PASS）、`#/chat/0` 路由（离线包自带夹具会话，实测 `answers: 1`）。输出两行「读数」+ R1/R2/R3 判决 + `check_ui_readability: N check(s) failed` + `EXITCODE=`。
- State and exception handling: 页面没渲染出 `body`、渲染不出 `.side-label`、可见文本节点为 0、要求 `.chat-answer` 却没有——全部走 `FAULT` + EXITCODE=3（「没判」不是「通过」）；某页确实没有阅读面而调用方不要求时走 `NOTE` 出声跳过。
- Constraints and trade-offs: 不新增依赖（复用仓库内 `.codex/scripts/dev/parity/parity_cdp.mjs`，与 085 探针同一条路）；不启动 dev server；不在脚本里写 CSS 选择器之外的任何视觉期望；每行汉字数用「内宽 ÷ 计算字号」这个全角等宽近似，**并把它写在脚本头注释里**——换成 Range/矩形逐行测量的精度更高但会把判据绑死在具体字体度量上，本项目阅读正文是中文，近似成立且更可解释。
- AI recommendation and rationale: 判据合成两页一次判（`combine()`）。第一版逐页判，R1 在首页和阅读面各红一次，`check(s) failed` 数不再等于缺陷数——这与 088 里「A4 在没有内联字体时打 NOTE 而不是第二条红」是同一条纪律：**红条数必须能当缺陷数读**。
- User-confirmation decisions: 沿用 086 的「22 条全做」与「判据先行」；地板 12px／上限 41 字取自 086 卡面（评审板给的舒适区间 32–41 与「12px 是中文小字下限」）。
- AI-autonomous decisions: ① 视口 1440×900（桌面与 Web 的主用档，窄窗另有静态断言）；② 遍历 `SHOW_TEXT` 而非按 class 白名单——白名单会让「下一个新出现的小字」漏网；③ R3 用 `.side-label` 计算值判导航档，但**只在 Web 面判**，Android 底部标签栏由 092 的静态断言与真机截图负责，两端各自有判据不互换。
- Acceptance evidence: `--selftest` 11 例全绿；仓库面判出 R1＋R2 两条红并点名 8 个选择器；`089-red.txt` 落盘；R2 的 48 独立复现评审板 Playwright 的 48（两份不同脚本同一读数）。
- Facts: 首跑（`089-red.txt`）——首页最小 11px／可见文本 29 个／导航标签 13px；阅读面正文 15px ÷ 内宽 720px = 每行 48 个汉字；合成 97 个可见文本节点；低于地板的选择器 8 个：`span.forest-entry-sub`「全部主题长成一片林子」、`p.side-heading`「最近学习」、无类名 `small`「1 个分支 · 3天前」、无类名 `span`「知芽 · TreeLearn」、`p.composer-help`「发送与上传尚未接通；点推荐问题或历史主题进入对话」、`span.suggestion-tag`「顺着你学的」、`span.home-count`「2 个」、`p.chat-meta`「3天前」。**R3 判绿**：Web 侧栏标签 13px ≥ 13——这更正了 086 的 X3 表述，X3 的「导航文字落在最小档」只在 Android 成立（`android/app/src/main/res/layout/screen_tab_bar.xml:52` 与 `:80` 的 `android:textSize="@dimen/text_cap"`）。
- Assumptions: 离线包在 `file://` 下能走到有回答的会话（已实测 `#/chat/0` → `answers: 1`）；全角汉字宽 ≈ 1 em 在 `--sans` 下成立。
- Unknowns: 无会改变范围或验收的未知项。
- Risks: `combine()` 之后，若只有阅读面有小字，首页读数变化不会体现在 R1 上——已由「合成 N 页」这句读数说明覆盖；R2 的近似在正文里插入大段拉丁（代码块、URL）时会低估每行字数，方向上是**漏报而不是误报**。
- Deferred: 窄窗（<760）与小屏档的渲染判据；Android 面的字号渲染判据（走 `check_glass_depth.py --shot` 那条真机像素路）。
- Non-goals: 不改任何 CSS／layout／token；不修 X3 与 B1 本身（092、094）；不把 `--fs-cap` 从 11 改到任何值。
- Skipped non-critical questions and reason: 未问「地板取 12 还是 13」——12 是中文小字下限（评审板正文），13 是 `fs-sm`；对导航另设 R3 单独用 13，两档各有各的判据，不需要用户裁决。

## Goal

让「界面上真实落到眼睛前的最小字号」与「阅读正文每行多少个汉字」变成一条不需要人眼、当前就判得出红并点名对象的渲染判据。

## Scope

### In scope
- 新增 `.codex/scripts/dev/check_ui_readability.mjs`（R1/R2/R3 + `combine()` + 11 例自检）。
- 证据落盘 `artifacts/evidence/ui-readability-parity-2026-10-25/089-red.txt`。

### Out of scope
- 注册表（→104）；任何被产物改动（→092／094）；Android 与窄窗档（→092 静态断言与真机截图）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增只读脚本，运行时行为零变化；必须保留 085 建立的 `parity_cdp.mjs` 复用路（本脚本只 import 不修改它），保留 `desktop/verify-matrix` 的 14 屏 × 2 尺寸判据不受干扰（脚本不写产物、不占 9222 端口——`launchOnce` 用 `--remote-debugging-port=0`）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `.codex/scripts/dev/check_ui_readability.mjs`；消费者目前是人／092／094；同族仪器 `artifacts/tmp/085-dock-probe.mjs`（同一个 CDP 驱动的既有先例）、`.codex/scripts/dev/parity/cdp_shot.mjs`；被测面 `frontend/dist-offline/index.html` 与 `frontend/src/styles/workspace.css` 的 `.chat-answer`、`pages.css` 的 `.side-label`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 无；新增一条「渲染面最小字号与阅读行长有地板／上限」的规则性判据。
- Authoritative code/config/migration evidence: `.codex/scripts/dev/check_ui_readability.mjs`（`CJK_FLOOR_PX`／`MAX_CJK_PER_LINE`／`FS_SM_PX`／`VIEWPORT` 四个裸常量、`SCAN_JS` 的遍历与 `charsPerLine` 口径、`combine()`、`judgeReadings()`、`measure()` 的三处 Fault 分支）；被测面 `frontend/src/styles/workspace.css` 的 `.chat-answer`、`pages.css` 的 `.side-label` 与 `composer-help`／`suggestion-tag`／`home-count`／`chat-meta`／`forest-entry-sub`。
- Documentation surfaces checked: `.codex/rules/ux-design.md`、`.codex/rules/frontend.md`、`.codex/rules/multi-device.md`、`Docs/04-ux-design/design-system/README.md`。
- Documentation files updated: `Docs/04-ux-design/design-system/README.md` 的变更记录留给 092（本片未改设计事实）；本卡 Documentation impact record。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact。
- Superseded phrases/examples reverse-searched: 086 表里 X3 那句「且用在导航文字上」经 R3 实测更正为「只在 Android 成立」，更正已写回 086 的表与 Gaps；评审板原文作为不可变证据保留在 `artifacts/ui-review-2026-10-25/`，不改写。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写 085／086 的历史记录。

## Acceptance criteria

- [x] `--selftest` 全绿：R1 两条边界（低于地板红 / 恰等于地板不红）、R2 两条、R3 一条、四处「仪器必须报故障而不是判绿」、`combine()` 三条。
- [x] 在离线产物上判出 R1＋R2 两条红，且 R1 点名到选择器与实际文字。
- [x] R2 的 48 与评审板独立读数一致（两个不同脚本同一个数）。
- [x] 缺陷与红条数一一对应：两页同因只判一条 R1（`combine()` 的三条自检钉住这件事）。
- [x] 产物缺失、页面没渲染、无可见文本、要求 `.chat-answer` 却没有——四种情况全部 EXITCODE=3 且打 `FAULT`，不打 PASS。
- [x] 不新增依赖、不起 dev server、不联网；`git status` 只见新脚本与证据目录。

## TDD execution record

### RED — failing test first
- Test/scenario: 先写自检的边界负例（「R1 低于地板必须红」「R1 恰好等于地板不得红」「R2 超上限必须红」「R3 导航掉到最小档必须红」「没有阅读面且要求它时必须报仪器故障而不是判绿」「没有可见文本节点时必须报仪器故障」），再让它跑真实页面。
- Command: `node .codex/scripts/dev/check_ui_readability.mjs`
- Exit code: 1
- Observed failure: `check_ui_readability: 3 check(s) failed`——R1 在首页与阅读面**各判一条**，加上 R2 共三条，而缺陷只有两个（11px 那一档、行长 48）。
- Failure reason validated: 不是产物问题，是判据形状问题：逐页判让同一个 `--fs-cap` 成因产出两条红，`check(s) failed` 不再能当缺陷数读。与 088 的 A4／A1 分工是同一条纪律。首跑还顺带读出一条**比原判断更准的事实**：R3 判绿（Web 侧栏导航标签 13px），说明 086 的 X3「导航文字落在最小档」只在 Android 成立——已按更正写回 086 的表与 Gaps，而不是把 R3 改成能红的形状。
- Evidence: `artifacts/evidence/ui-readability-parity-2026-10-25/089-red.txt`（终态 2 红 1 绿）。

### GREEN — minimum implementation
- Implementation: `.codex/scripts/dev/check_ui_readability.mjs`。R1 遍历 `NodeFilter.SHOW_TEXT`，跳过 `display:none`／`visibility:hidden`／`opacity:0`／矩形小于 1px 的节点，取 `getComputedStyle().fontSize` 的最小值并按「类名@字号」去重点名；R2 取最宽的 `.chat-answer`，`内宽 = rect.width − paddingLeft − paddingRight`，`charsPerLine = floor(内宽 ÷ fontSize)`；R3 取 `.side-label` 的计算字号；`combine()` 把多页读数合成一份后再判（取更小字号、按选择器去重命中、文本节点数相加、阅读面取第一个存在的那页）。四路 Fault：`!textNodes`、`min === null`、`requireReading` 却无 `.chat-answer`、`nav === null`。复用 `parity_cdp.mjs` 的 `findBrowser`/`openBrowser`/`Cdp.eval`，用 `Emulation.setDeviceMetricsOverride` 固定 1440×900。
- Command: `node .codex/scripts/dev/check_ui_readability.mjs --selftest` 与 `node .codex/scripts/dev/check_ui_readability.mjs`
- Exit code: 0（自检）／1（仓库面，**期望的红**）
- Passing evidence: 自检 `selftest judgement cases executed = 11`、`check_ui_readability: 0 check(s) failed`、`EXITCODE=0`；仓库面 `2 check(s) failed`、R1 点名 8 个选择器、R3 `OK — 实测 13px`。
- Regression evidence: 088 的 `check_ui_asset_parity --selftest` 14 例复跑仍绿；`--attach`／`--browser` 两条逃生口未动；`node --test desktop/test/*.test.mjs` 与 `frontend` 全量未受本片影响（本片刻意未跑它们，因为零运行时改动——见 Verification report 的 Gaps ①）。
 
### REFACTOR — optional
- Cleanup: 逐页判 → `combine()` 合成判；R1 明细行去掉 `.slice(0, 8)` 截断，改为全量点名（红线的用途就是告诉下一位改哪些，截断会把「正好 8 个」这个事实藏起来）；`MIN_SELFTEST_CASES` 6 → 9 并把理由写成注释。
- Regression evidence: 复跑自检 11 例绿；仓库面读数从 3 红变 2 红且 R1 明细的选择器数不变（实测就是 8 个，此前没有被截断藏住）。

### LOOP — optional
- Command and rounds: `--selftest` 两轮 + 仓库面三轮（第一版 / 去掉 `JSON.stringify` 双重编码后 / 合成后）。
- Failure identity and repeat count: 三处各一次，是三个不同缺陷——① `cdp.eval` 内部已经 `JSON.stringify` 一次，`SCAN_JS` 自己又 stringify 一次，读数会变成「字符串的字符串」，`JSON.parse` 出来是字符串而不是对象，判据把 `read.min` 读成 `undefined`；② `SCAN_JS` 里 `__FLOOR__` 出现两次而 `.replace(字符串)` 只替换第一处，第二个判断拿到的是未替换的裸标识符，页面侧会抛 `ReferenceError`；③ 逐页判导致 R1 重复计红（见 RED）。都不在同一个失败上重试。
- Stop condition hit: ①② 用「先让自检跑假读数、再让真页面跑一次」的顺序抓出来：自检绿不代表仪器接上了真页面，所以主流程坚持「渲染不出 `body`/`.side-label` 就报 FAULT 而不是判绿」，这条本身也是一处判据。③ 按「红条数=缺陷数」重构成合成判并补三条 `combine()` 自检。
- Handoff: 092 用 R1 作 GREEN 靶（`fs-cap` 抬到 12px 后那 8 个选择器必须全部离开 11px）；094 用 R2 作 GREEN 靶；注册进 `run_all_guards.py` 由 104 统一做。
- Evidence on disk vs console-only: 落盘的是终态 `089-red.txt`（2 红 1 绿、R1 全量点名）。**①② 两个仪器缺陷与「3 check(s) failed」那一轮只在终端读出**，第三轮把同一个文件覆盖了；它们是修复过程的记录，不是当前判据的凭据。当前态由 `--selftest` 的 11 例在每次运行时重跑，其中「没有可见文本节点时必须报仪器故障」钉住 ①的形状（读数坏掉时必须出声），合成三例钉住 ③。按 085／088 立的口径写明，免得下一位读者去证据目录找一份不存在的转录。

### TDD or sizing exception — only with prior user approval
- Exception type: sizing-warning（非例外，登记口径）
- Reason: 292 行（非注释 227 行）超 200 行的软目标。它是单一判据仪器：`SCAN_JS`、`combine()`、`judgeReadings()` 三者共享同一份读数形状，拆开会让「什么算可见文本」这套定义出现两份。11 例自检与头注释不计入生产行。
- User approval: 不适用（未触及 500 行硬失败线，软线只触发 WARNING）。
- Alternative verification: 不适用。

## Implementation notes

- 红条数必须能当缺陷数读。这是本片与 088 共同立下的口径：同一个成因（一个 token 档、一个缺失的随包字体）在多个页面／多条判据上重复计数，会让「4 check(s) failed」不再告诉任何人该改几处。
- `charsPerLine` 用全角等宽近似，不是精确排版测量：脚本头注释里写明了这一点与它的不成立条件（大段拉丁时低估）。把近似写成近似，比让它看起来像精确测量安全得多。
- 直接 `file://` 打开离线包而不是起 dev server，是为了让这条判据能进 core 档（`run_all_guards.py` 的 core 定义是「不依赖构建产物或 git 增量」——它依赖 `dist-offline` 存在，所以实际会落 precommit 档，104 注册时按 precommit 归档并写明原因）。
- 三处 Fault 分支比判红更重要：这台仪器最容易出的事故不是「误报小字」，而是「页面没加载、拿到空读数、于是安静地判绿」。
- R3 判绿不是这条判据失败，而是它把 086 的一个表述证伪了一半。Android 那半边由 092 的静态断言接住（`screen_tab_bar.xml:52`/`:80` 已知用 `@dimen/text_cap`）。

## Verification report

- Verdict: PENDING（待独立复核）
- Evidence: 渲染态——真实 Edge（`Edg/155.0.4283.62`，`--headless=new`，自起临时 profile、`--remote-debugging-port=0`）加载 `frontend/dist-offline/index.html` 与 `#/chat/0`，视口 1440×900；读数两行打印后合成判据。自检——11 例假读数注入，覆盖 R1/R2/R3 的两侧边界与四处「必须报故障」。仪器故障面——产物不存在时打 `FAULT … EXITCODE=3`（已在缺 `dist-offline` 的分支上核过写法，未删文件实测，见 Gaps ②）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 只新增判据，未改动或删除任何既有断言；`parity_cdp.mjs` 一字未动（只 import）。
- Gaps: ① 本片零运行时改动，因此没有复跑 `frontend test`／`desktop verify`／Gradle——如果独立复核要求「全绿」证据，那是 092 之后的事；② 三条 Fault 分支里只有「渲染不出 body/`.side-label`/无可见文本」在真实路径上有机会触发，`dist-offline` 缺失那条是通过代码路径核对而非删文件实测的；③ 只测 1440×900 一档视口，窄窗与 Pad 档没有渲染判据；④ R1 遍历的是 Web 产物面，Android 端的小字（含 11sp 底部标签）本片判不到。
- Risks: 若将来离线包没有夹具会话，`.chat-answer` 会读不到而报 FAULT（不是判绿），那时 094 的 GREEN 就跑不出来——这是好事，但需要在 094 卡片里写明「必须先有离线产物」。

## Handoff notes

- Completed: 渲染判据 R1/R2/R3 与 `combine()` 落盘，11 例自检全绿，仓库面判出与 X3／B1 对应的 2 条红并点名 8 个选择器；顺带用 R3 的实测把 086 的 X3 更正为「只在 Android 成立」。
- In progress: 090（X1 字体子集内嵌 + Windows 兜底栈）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 092 以 R1 为 GREEN 靶（`fs-cap` 11→12 四处同批 + Android `text_cap` 标签档）；094 以 R2 为 GREEN 靶（`.chat-answer` 行长上限）；104 注册时本片归 precommit 档（依赖 `dist-offline` 构建产物）。
- Watch-outs: ① 别为了让 R1 转绿而把地板降到 11——那正是本片要按住的动作；② `SCAN_JS` 里的占位符（`__FLOOR__`/`__NAV__`/`__READING__`）每个只能出现一次，加判断时改成 `replace(/__X__/g, …)`；③ `cdp.eval` 已经 stringify 一次，页面侧不要再 stringify；④ 改字号档时先看 088 的 A3 与 086 的 8 个选择器名单，别只改 token 不核对生效值。
