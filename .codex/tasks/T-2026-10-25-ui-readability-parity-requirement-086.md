# Filename: T-2026-10-25-ui-readability-parity-requirement-086.md
# Codex Harness Task: 界面可读性与两端一致性优化（22 条评审发现）的需求基准

id: T-2026-10-25-086
title: 界面可读性与两端一致性优化（22 条评审发现）
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: harness
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 用户 2026-10-25 要求用升级后的 huashu-design 3.0 评审安卓原生客户端与 Web／桌面客户端界面，产出 22 条可优化点（评审板 `artifacts/ui-review-2026-10-25/知芽界面可优化点-评审板-2026-10-25.html`，2 严重／10 重要／10 优化），随后要求「采用 harness 进行优化」。最集中的两类问题不是配色而是：① **同一份设计资产在两端不等价**（衬线字面只有 APK 随包分发，Web 与 exe 由宿主系统兜底；「设置」图标的 pathData 手抄在两端且画的是一轮太阳；`fs-cap` 11px／`text_cap` 11sp 低于中文小字可读地板）；② **可读性与信息落点被版式吃掉**（阅读正文实测 48 个汉字一行、安卓首页可操作元素落在中线以下、森林两棵树没有名字、错误态把风险说明排成按钮副标题）。
- Users/scenarios: ① Windows 桌面上用 1.0.0 便携 exe 阅读长回答的学习者——目标机大概率没装 Noto Serif SC，品牌与主标题会落到宋体；② 1080p 手机上读底部标签栏与 meta 小字的用户；③ 在 Web 阅读面拖选正文、复制、追问的人；④ 生成中断后需要读懂「重试会怎样」的人；⑤ 在「我的森林」判断自己学到哪了的人。
- Expected outcome: 22 条全部处置。字面与图标两端等价（Web／exe 随包内嵌子集衬线，「设置」是真齿轮或滑杆且两端同源）；小字层不低于 12px／12sp 且导航标签不落在最小档；阅读正文每行汉字数落回 32–41；`app.css` 这张死样式表与它维持的 `--kai` 假消费者一起退役；每条评审发现要么落成改动、要么落成显式登记的非目标，不留「看过但没动」。
- Core flow and reversibility: 本线不改任何状态流。回退按片撤销：判据片删脚本与注册表条目；字面片删 `frontend/src/styles/fonts/` 与 `@font-face` 块并还原 `--serif`；图标片还原 `ic_*.xml` 与 `Icon.tsx` 的 pathData；字号片还原 `tokens.json` 并重生成 `tokens.css` 与 `dimens.xml`；版式片还原对应 CSS／layout。**唯一需要特别登记的回退面**：`tokens.json` 是四方链的第二环，任何 token 级改动必须四处同批（原型 `:root` → `tokens.json` → 生成物 `tokens.css` → `dimens.xml`），只回退一处会让 `check_design_tokens.py` 判红而不是让界面回原样。无数据迁移、无 schema 改动、无 API 变更。
- Inputs/outputs: 输入是评审板的 22 条发现与用户在四个问题上的拍板；输出是判据脚本（新增守门）、样式与布局改动、四端 token 同步、以及每条发现的落点。不新增持久化状态，不改夹具与 API 契约。
- State and exception handling: 判据必须能区分「产物判红」与「仪器没判」——沿用 `run_all_guards.py` 的 0／1／3 三档退出码纪律。字面内嵌失败（子集工具不可用）时不许退回 CDN，因为 `check_offline_bundle.mjs:55` 的既有判据禁止「要联网才拿得到」，`data:` 内联才是被放行的路径。真机 bounds 抓不到时（本日 `uiautomator dump` 失败已发生一次）数值只能取保守值并显式登记为未经实测，不许把目测写成实测。
- Constraints and trade-offs: 不新增依赖（字体子集只能用已装的 Python 工具链或直接在打包脚本里做 base64，不引入 fonttools 之外的安装步骤——若必须引入，按「不新增依赖」提交例外给用户裁决）；`--serif` 兜底栈只能补 Windows 真实存在的族名，不许写猜测名；颜色／圆角／阴影／字号继续只用 tokens，`tokens.css` 是生成物不得手改（085 已因此判红过一次）；Android 保持 Java-only；桌面壳不另写界面与样式，产物面仍由 `frontend/dist-offline/index.html` 单源供给；不为「好看」放宽任何既有可读性地板。
- AI recommendation and rationale: 判据先行，两片先落（静态资产判据 + 渲染可读性判据），再落修复片。理由是这条线里最容易复发的是**两端不等价**，而它现在没有任何机械判据——图标手抄零报警、字体缺失零报警、`--kai` 假消费者会让任何 grep 存活率守门误报。先让「改漏一端」变成红灯，修复片才不会又长出新的不等价。
- User choice and custom additions: 四项拍板原文——范围=「22 条全做」；X1 字面=「子集内嵌 + 兜底栈（推荐）」；安卓面=「全纳入，含首页纵向重排」；方向题（D1 首页纵向预算 / C1 森林信息密度）=「现在就改，取保守值」。
- AI-autonomous decisions: ① 卡片代号沿用评审板的 X1–X4，并把逐屏第 1 条沿用用户已见过的 B1／C1／D1（与逐屏代号 WC1／WF1／AH1 一一对应，见下表）；② `fs-cap` 走「提到 12px」而不是「导航标签降到 13px」，因为 38 处 Web 消费者 + 84 处安卓消费者里 meta 类才是多数，单点降导航会让 meta 仍留在 11；③ 子集范围取 GB2312 常用 6763 字 + 拉丁与标点，命中不了的字回落兜底栈而不是丢字；④ `app.css` 整表删除而非逐条清（抽样 12 类名 10 个零消费者，剩下 2 个已在活样式表里有等价规则）；⑤ 图标方向选齿轮，因为「滑杆」在本产品语义里更像筛选。
- User-confirmation decisions: 「采用 harness 进行优化」＝授权进入 Harness 并建卡；四项范围拍板＝授权全量处置，含需要人眼裁决的两个方向题。
- Acceptance evidence: 每片按 TDD 出 RED／GREEN 落盘转录；判据片必须**在当前产物上真判红**（11px、48 字/行、pathData 手抄、`@font-face` 缺失四条都是现存事实，判据第一版就应红）；修复片由 `run_all_guards.py --tier core` 与 `--tier precommit` 整跑读数 + 真实浏览器／真机截图为证；两个方向题由用户过目截图裁决（无机械判决，按 `### TDD or sizing exception` 登记）。
- Facts: 逐条实测，全部可在 `artifacts/evidence/ui-readability-parity-2026-10-25/` 复核——`frontend/dist-offline/index.html` 519,350 字节且 `@font-face` 出现 0 次、字体 CDN 0 次；`android/app/src/main/res/font/noto_serif_sc.otf` 24,427,224 字节（23.30 MiB）；`desktop/release/Treelearn-1.0.0-portable.exe` 110,493,305 字节（105.37 MiB），故 3 MiB／5 MiB 子集分别是 +2.8%／+4.7%（`size-baseline.txt`）；`var(--fs-cap)` 在 `frontend/src` 出现 38 次、`@dimen/text_cap` 在 `android/app/src/main` 出现 84 次（`token-consumers.txt`）；`--serif` = `'Noto Serif SC','Songti SC',Georgia,serif`（`tokens.json:268`、`tokens.css:71`）；`ic_chat_gear.xml` 与 `Icon.tsx:143` 共用同一条「中心圆 + 八条放射短线」pathData；`app.css` 唯一引用点是 `main.tsx:11`，另有 `glass.css:171` 一句注释指向它；Playwright 量得 `.chat-answer` 宽 720px @15px ≈ 48 汉字/行、`.page` 960、`.app-sidebar` 240；`frontend/scripts/check_offline_bundle.mjs:55` 明确放行 `data:` 内联；088 片的守门首跑判出 **30 个无消费者类名**（`app.css` 29 个 + `glass.css:172` 的 `rec-tag`——它在 `app.css:268` 也声明了一次，两边都没有 TSX 消费者），逐条名单见 `088-dead-classes.txt`，抽样 8 个类名反查 `.tsx`/`.ts` 命中全为 0，误报面已排除。
089 片的渲染判据首跑（`089-red.txt`）另给出两条：**R1** 合成首页与阅读面共 97 个可见文本节点、最小 11px、低于 12px 地板的选择器恰好 8 个（`forest-entry-sub`／`side-heading`／无类名的 `small`「1 个分支 · 3天前」/无类名的 `span`「知芽 · TreeLearn」／`composer-help`／`suggestion-tag`／`home-count`／`chat-meta`）；**R2** `.chat-answer` 内宽 720px ÷ 15px = 每行 48 个汉字（评审板那个 48 是独立复现的）
- Assumptions: GB2312 常用 6763 字子集能覆盖本项目全部可见文案（界面文案为现代汉语常用字，专有名词除外）；Windows 上「真实存在的中文衬线」以 SimSun（宋体）为最稳的一档，它正是当前 generic serif 会落到的那个字面——补进栈是把随机降级变成明确决定，不是假装换了一种字。
- Unknowns: 安卓首页纵向预算的真实 bounds 未取到（本日 `uiautomator dump` 对 `com.treelearn` 失败，前台被 `com.knowledgespace.mobileprototype` 占住），因此 D1 的数值只能取保守值；森林等高线的 ΔL 跨度未跑 `check_glass_depth.py`。两者都不改变范围或验收方式，只改变「这两片的判决由谁给出」，已按例外登记。
- Risks: ① 子集内嵌会把体积推进离线包与 exe，若子集失控到全量 23 MiB，离线包会从 0.50 MiB 涨两个量级——判据必须给体积上限而不只给「有没有 `@font-face`」；② `fs-cap` 11→12 是 token 级改动，38 + 84 处消费者里若有按像素对齐的几何（如图标间距、徽标宽度）会跟着漂；③ 删 `app.css` 会让任何按 CSS grep 判字体存活率的既有守门读数变化，需先确认没有守门把它当活表；④ 整树混合工作树下 `line-budget` 会判红（既有登记，属提交形状而非本片缺陷）。
- Deferred: 阅读面第三级标题档位（16→20 之间的空缺）等真出现需求再定；高亮／批注／新建主题的实接通不在本线；exe 重打包等本线改动收齐后统一做一次而不是每片一次。
- Non-goals: 不改后端（`backend/` 未落地）；不引入远程账号、同步或遥测；不为了视觉效果放宽任何既有可读性地板或玻璃深度判据；不把 `--brush` 拉回使用面；不重写选区浮条与引用流程的行为语义。
- Skipped non-critical questions and reason: 未询问「先做哪几条／是否分批」——用户已答「22 条全做」，顺序属 AI 自主范围；未询问子集字数与许可细节，OFL 许可允许再分发与子集化，`assets/licenses/` 已随 APK 带着许可文本。

## 22 条发现与落点（本卡的权威清单）

代号沿用评审板。逐屏第 1 条保留用户拍板时见过的旧别名。
片号从 **088** 起：087 已被并行线占用（`T-2026-10-25-composer-focus-note-087.md`，
`status: in_progress`，owner `frontend-dev`，改 `workspace.css` / `Composer.tsx` / `zh-CN.ts`），
本线不复用该号，且 WH2／WC3 那条与它正面重叠，已改为依赖它而不是抢同一行。

| 代号 | 严重度 | 发现 | 落点片 |
|---|---|---|---|
| X1 | 严重 | Web／exe 无随包字体，衬线由宿主兜底成宋体 | 088 判据 + 090 修复 |
| X2 | 重要 | 「设置」图标是一轮太阳，且 pathData 两端手抄 | 088 判据 + 091 修复 |
| X3 | 重要 | 标签层 11px／11sp（**089 实测更正**：Web 侧栏导航标签已是 13px 不达标的是 Android 底部标签栏，见 `screen_tab_bar.xml:52`/`:80` 用 `@dimen/text_cap`） | 089 判据 + 092 修复 |
| X4 | 优化 | `app.css` 425 行死样式表仍打进离线包，维持 `--kai` 假消费者 | 088 判据 + 093 修复 |
| WH1 | 重要 | 侧栏一列混了路由、路由卡、历史内容三种性质 | 097 |
| WH2 | 重要 | 主输入卡下沿那句「尚未接通」写在动作容器里 | 098（依赖并行片 087） |
| WH3 | 优化 | 历史行视线落点与操作落点分家 | 097 |
| B1（=WC1） | 重要 | 阅读正文实测 48 汉字一行 | 089 判据 + 094 修复 |
| WC2 | 优化 | 字号档中段空缺，meta 与导航挤同一档 | 092 |
| WC3 | 优化 | 同一句「未接通」在第二屏又出现 | 098（依赖并行片 087） |
| C1（=WF1） | 重要 | 森林隐喻到位但信息没进来，全场唯一量化是 11px 计数 | 103 |
| WF2 | 优化 | 右上角圆圈里的「N」无解释、无 hover、无 aria | 103 |
| WF3 | 优化 | 等高线压到几乎看不见，与项目自己量过 mist-base 的尺子不自洽 | 103 |
| D1（=AH1） | 重要 | 安卓首页可操作元素落在中线以下 | 100 |
| AH2 | 重要 | 推荐区右列裁成半张卡、文字断成半句 | 099 |
| AH3 | 优化 | 四张推荐卡挂同一个「顺着你学的」 | 101 |
| AC1 | 严重 | 中断卡把风险说明排成按钮副标题 | 095 |
| AC2 | 重要 | 顶栏标题截到约 10 字还挤三个图标 | 096 |
| AC3 | 优化 | 空态只有文字没有出口 | 096 |
| AL1 | 重要 | 「已完成 0」也占一整段 44dp 分段位 | 102 |
| AL2 | 优化 | 搜索 placeholder 写的是字段清单 | 101 |
| AL3 | 优化 | 卡片摘要按字符硬截，出现「符…」 | 099 |

判据片（088／089）先把「什么算坏」写成一个会判红的脚本，但**先不入 `run_all_guards.py` 的注册表**：
一条当前判红的守门直接进 core 档，会让本线每一片的整跑读数都失去信号（同 085 处理
`guard-selftest` 天花板时立的口径：仪器要给出判决，而不是给出恒定的红）。注册放在 104
「判据入库并整跑转绿」，前置条件是 A1–A6 与渲染两判全部转绿。

## Goal

作为本线家长需求卡，持有 22 条评审发现与用户四项拍板的完整确认清单，并固定判据先行的拆分顺序；不产出代码。

## Scope

### In scope
- 22 条发现的处置与落点登记（088–104）。
- 两片先行判据：静态资产与类名存活率判据（088）、渲染可读性判据（089）。
- 修复面：`frontend/src/styles/**`、`frontend/src/components/**`、`frontend/src/pages/**`、`frontend/scripts/inline_offline.mjs`、`android/app/src/main/res/**`（layout／drawable／dimens／font）、`Docs/04-ux-design/design-system/tokens.json` 与原型 `:root`、`.codex/scripts/**` 判据注册。

### Out of scope
- `backend/**`（目录尚未落地）、Android 业务语义与 schema、Web 发送／上传／分支创建的实接通。
- 远程字体 CDN（与 `check_offline_bundle.mjs` 判据直接冲突）。
- 逐片重打包 exe（本线收齐后统一做一次）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 改的是资产、字号地板、图标身份、版式与文案落点；必须保留草稿、引用条与 chips、选区浮条六格顺序、滚动位置恢复、人设弹层、复制整条回答、窄窗抽屉、两条玻璃降级路径、Android Java-only 与本地优先边界。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 字面生产者 `frontend/src/styles/tokens.css:71`（生成物）与 `Docs/04-ux-design/design-system/tokens.json:267-269`；消费者 `workspace.css`／`pages.css`／`forest.css` 里所有 `var(--serif)`；同族消费者 APK 内 `res/font/noto_serif_sc.otf`。小字层生产者 `tokens.json` 的 `fs-cap` → `tokens.css` → `android/.../values/dimens.xml` 的 `text_cap`，消费者 38 + 84 处。图标生产者 `android/app/src/main/res/drawable/ic_chat_gear.xml` 与 `frontend/src/components/Icon.tsx:143`，消费者 `Icon.tsx:50`（`home_settings_button`）、`:62`（`chat_settings`）。死表生产者 `frontend/src/main.tsx:11`，注释指向 `glass.css:171`。
- Native bridge or independent-client compatibility/update requirements: 无原生桥接改动；桌面壳消费同一份 `dist-offline/index.html`，字面与图标改动会自动进 exe，不需要版本协商，但 `desktop/app/index.html` 由 `desktop/scripts/sync-app.mjs` 刷新，产物面代次差要按 085 的 Gap ④ 口径写清。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 本线把「Web／exe 与 Android 用同一套字面与同一套图标」从假设变成有判据的事实；把小字可读地板从 11px 抬到 12px；把 `--kai` 从「声明放弃但仍有假消费者」推进到真退役。
- Authoritative code/config/migration evidence: 见上表 22 条与 Facts 段的 file:line 与 `artifacts/evidence/ui-readability-parity-2026-10-25/`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`Docs/04-ux-design/design-system/tokens.json` 的 `$note`（`fonts` 段）、`frontend/scripts/check_offline_bundle.mjs` 头注释、`.codex/rules/ux-design.md`、`.codex/rules/multi-device.md`、根 `README.md`、`desktop/README.md`。
- Documentation files updated: 待子片执行时逐片更新；家长卡不替子片写文档账。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact。
- Superseded phrases/examples reverse-searched: `tokens.json` 的 `$note` 现写「Web 侧仍由 --serif 回退栈与原型 CDN 承担」——这句在 090 落地后**变成假话**（原型 CDN 不参与产物面），必须在同片改写；`glass.css:171` 注释「文字仍由 app.css 的 green-deep 表达语义」在 093 删表后指向不存在的文件，必须同片改写。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写 080／085 的历史记录；本卡只做需求基准，评审板作为不可变证据留在 `artifacts/ui-review-2026-10-25/`。

## Acceptance criteria

- [ ] 088／089 两片判据在**未修复的当前产物**上判红，且红条数与 X1／X2／X3／X4／B1 一一对应（不许出现「判据第一版就绿」）。
- [ ] 22 条每条有落点：改动卡片号，或显式 Deferred／Non-goal 并说明理由；无「评审过但没登记」。
- [ ] Web／exe 与 Android 的衬线字面同源，且 `--serif` 兜底栈含 Windows 真实存在的中文衬线；离线包体积有上限判据而不是只判「有没有 `@font-face`」。
- [ ] 「设置」图标两端同源且有判据；pathData 不再手抄。
- [ ] 最小可见字号 ≥12px（中文界面地板），导航标签不落在最小档；四处 token 同批且 `check_design_tokens.py` 绿。
- [ ] 阅读正文每行汉字数落回 32–41，由渲染判据给出实测值。
- [ ] `app.css` 与 `main.tsx:11` 的 import 一并删除，`--kai` 退役路径打通，`glass.css:171` 注释不再指向不存在的文件。
- [ ] D1／C1 两片按 `### TDD or sizing exception` 登记「无机械判决、取保守值、待用户过目截图」，不得标 PASS 而只标已实现。
- [ ] `run_all_guards.py --tier core` 与 `--tier precommit` 整跑读数有落盘；`metrics_report.py --write` 在状态翻 `verified` 前重生成一次。

## TDD execution record

### RED — failing test first
- Test/scenario: 家长卡不产代码。
- Command: 不适用。
- Exit code: 不适用。
- Observed failure: 不适用。
- Failure reason validated: 不适用。

### GREEN — minimum implementation
- Implementation: 不适用。
- Command: 不适用。
- Exit code: 不适用。
- Passing evidence: 不适用。
- Regression evidence: 不适用。

### REFACTOR — optional
- Cleanup: 不适用。
- Regression evidence: 不适用。

### LOOP — optional
- Command and rounds: 不适用。
- Failure identity and repeat count: 不适用。
- Stop condition hit: 不适用。
- Handoff: 不适用。

### TDD or sizing exception — only with prior user approval
- Exception type: 无机械判决的视觉裁决（仅适用于 100／D1 与 103／C1 两条方向题）
- Reason: 这两条改的是「纵向预算怎么分配」「留白与信息密度取哪一档」，项目现有判据（`check_glass_depth.py`、字号地板、行长上限）能判**边界**但判不出**这一档是不是用户要的**。用户 2026-10-25 明确选择「现在就改，取保守值」，等于授权在没有实测 bounds 的情况下先落一版并过人眼。
- User approval: 本会话 AskUserQuestion 的方向题回答「现在就改，取保守值」，属事前授权而非事后补批。
- Alternative verification: 落保守值 + 真机与浏览器前后截图 + 判据边界仍必须绿（最小字号、行长、玻璃深度）；卡片状态停在 `verifying` 直到用户过目，不写 PASS。

## Implementation notes

- 拆分口径：判据片先落、修复片后落，每片一个可独立验证的行为；token 级改动必须四处同批，不许拆成「先 Web 后 Android」两片——那样中间态一定判红且没人知道该谁负责。
- 本线唯一的新增判据落点是 `.codex/scripts/dev/`，注册进 `run_all_guards.py` 的 `GUARDS` 后必须同步跑 `check_run_all_guards.py`（注册表形状与裸下限），并按 085 的口径给足 `timeout=`——上限要盖过最长那次判决，别让它贴着天花板。
- 「实测 vs 推测」在每片子卡里分开写：D1 的纵向数值今天没量到，任何写成实测的读数都是假账。
- 字体子集的许可面：OFL 允许子集化与再分发，但随包必须带许可文本；APK 侧已有 `assets/licenses/`，Web 侧要不要同样带一份由 090 片决定并记录理由。

## Verification report

- Verdict: PENDING
- Evidence: 家长卡由子片证据聚合；本卡自身只验证清单完整性（22 条无遗漏、每条有落点）与事实可复核性（`artifacts/evidence/ui-readability-parity-2026-10-25/` 三份落盘）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本卡不改动任何既有断言。
- Gaps: ① 评审板 X3 那句「导航标签不该是字号最小的一行」在两端的成立情况不同，089 的 R3 实测 Web 侧栏标签 13px 已达标，红只在 Android 底部标签栏——这条更正已写进上表，092 片的判据面因此分两端；
  ② D1 的安卓首页 bounds 未取到；② WF3 的等高线 ΔL 未跑；③ 子集字数是否覆盖全部可见文案，要等 090 真跑一次产物才能判。
- Risks: 见 brief 的 Risks；最大的执行风险是 token 级改动（092）的 122 处消费者里出现按像素对齐的几何漂移。
  **并行面冲突（实测）**：写本卡期间 `.codex/tasks/` 里出现了 `T-2026-10-25-composer-focus-note-087.md`（`status: in_progress`，owner `frontend-dev`，parent 080），它改的正是 `workspace.css`、`Composer.tsx`、`copy/zh-CN.ts`，且它的「说明句只在空态」与本线 WH2／WC3 是同一条问题的另一种处置。本线因此让出 087 号，并把 098 片的 WH2／WC3 改为**依赖 087 完成后的实际形态**再判，而不是并行改同一段代码。

## Handoff notes

- Completed: 需求基准与 22 条落点表确立；体积、消费者计数、token 定义三类事实落盘。
- In progress: 088（静态判据，脚本已落盘并判出 4 条红）、089（渲染判据）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 按表建 088–104 子卡并逐片 TDD；本线全部子片 `verified` 后统一重出 `dist-offline` 并重打包 Windows 便携 exe。
- Watch-outs: ① 任何新写的守门要进注册表并复跑 `check_run_all_guards.py`；② 改 `tokens.json` 就要重生成 `tokens.css`，不许手改生成物；③ `desktop/app/index.html` 的产物面代次差要每片写明；④ 卡片里的每个数字都要能在证据目录 grep 到。
