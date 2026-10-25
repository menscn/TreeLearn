# Filename: T-2026-10-25-composer-focus-note-087.md
# Codex Harness Task: 输入卡里那道直角框与说明句——焦点归玻璃卡，说明只在空态

id: T-2026-10-25-087
title: 输入卡直角焦点框与空态说明句
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-reading-glass-parity-requirement-080.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 18
shared_code_touched: true
depends_on: [T-2026-10-25-dock-float-feather-085.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 080 线的两端玻璃语言对齐。081/082/083 落外壳与选中流程，085 把输入坞改成真悬浮；本卡处理用户随后补发的两处坞区问题。
- Problem/background: 用户 2026-10-25 发第二张问题截图（首页 `http://127.0.0.1:5174/#/`），红笔圈了两处并各问一句。① 「边框是怎么回事？」——28px 圆角的玻璃卡内部出现一个**直角矩形**：那是页面级焦点框 `pages.css:801-804` 的 `.workspace-page textarea:focus-visible` 与 `workspace.css:1119-1121` 的 `.chat-screen textarea:focus-visible`（各 `outline: 2px solid var(--green)`，特异性 0-2-1）压过了 `workspace.css:677-684` 的 `.composer textarea { outline: none }`（0-1-1）。② 「这句话呢？」——卡片下方那条居中的灰字「发送与上传尚未接通；点推荐问题或历史主题进入对话。」，居中 + 独立成行使它读起来像表单的系统提示而不是提示。
- Users/scenarios: 键盘 Tab / 点击进入输入框的学习者（焦点反馈必须仍然可见）；第一次打开首页、想知道这里能不能发送的人；开始打字后不希望卡里留一条与其动作无关的说明的人。
- Expected outcome: 焦点由玻璃卡自己那圈跟随 28px 圆角的绿环承担，卡内不再出现第二个方框；说明句只在空草稿时出现，一旦开始打字即退场，退场后卡片变矮且不留空行；首页那条文案收短，不再横在卡片里当标题。
- Core flow and measurements: 本卡只改渲染条件与样式作用域，不改任何状态流。回退：删 `workspace.css:668-675` 那条作用域规则、把 `Composer.tsx:92-94` 恢复成无条件 `<p>`、还原 `zh-CN.ts:86` 那条文案即可，无数据迁移。
- Inputs/outputs: 输入是 `Composer` 已有的 `value`（草稿）与 `note`（宿主覆盖文案）两个 props；输出是 `.composer-help` 的有无。不新增 props、不新增状态、不写 store。
- State and exception handling: 纯空白（`"   "`）按空态处理，说明句在场；退场后两个动作按钮仍然 `disabled`，「尚未接通」仍写在可访问名里——能力边界不靠这条文字维持。对话页坞高变化由 085 的 `useDockHeight` 实测写回，本卡不加任何测量代码。
- Constraints and trade-offs: 不新增依赖；不改 Android 与后端；颜色/圆角/阴影继续只用 tokens；修法必须靠**特异性**而不是靠书写顺序（靠顺序则任何一次 `@import` 调整都会让它复发）；不得整体删掉页面级 `textarea:focus-visible`（按钮与 chips 的焦点框要留着，那是可达性底线）。
- AI recommendation and rationale: 三个方向出过一张对照图（A 只在空态出现 / B 常驻左对齐 / C 移到卡片上方）。用户先选 C，随即改口拍板 A：「改成 A，空态出现，开始打字就退场」。A 之所以成立，是因为这句话回答的是「这里能不能发」，一旦开始打字这个问题就不再存在；能力边界已由按钮禁用与可访问名承担，不需要一行常驻文字。
- User-choice and custom additions: 「改成 A，空态出现，开始打字就退场」（覆盖先前所选 C）。
- AI-autonomous decisions: 首页那条文案由「发送与上传尚未接通；点推荐问题或历史主题进入对话。」收短为「点推荐问题或历史主题进入对话 · 发送与上传尚未接通」，把动作指引放前面、边界说明放后面；直角框用 `.workspace-page .composer` / `.chat-screen .composer` 两档作用域反向压回，而不是给输入格加 `!important`。
- Acceptance evidence: 判据共 7 条新增（样式 3 条：焦点作用域、卡片绿环仍在、说明句不居中；组件 4 条：空态在场、纯空白算空态、打字退场、退场后边界仍在），其中 3 条跑出过 RED（1 条 + 2 条，两份转录）；真实 Chromium 键盘焦点态实测（`Input.dispatchKeyEvent` 打真键，`focus()` 不足以触发 `:focus-visible`）；空态/有草稿两态截图与卡片高度、`--dock-h` 读数。
- Facts: `glass.css:136` 的 `.composer:focus-within` 已画出 `0 0 0 2px` 绿环，所以删内框不会让焦点变不可见；坞面类名是 `Composer` 的默认 prop（`Composer.tsx:44` `dockClassName = "chat-dock"`，`:54` 渲染），`HomePage.tsx:52-53` 传 `note={t("home_composer_note")}` 与 `dockClassName="home-dock"` 复用同一个组件，所以说明句的文案由宿主覆盖而渲染条件共用。
- Assumptions: 无——两态实测都过。
- Unknowns: 无会改变范围或验收的未知项。
- Risks: 说明句退场会让卡片由 164px 变 128px，进而改变 `--dock-h`（实测 176px → 140px）；羽化带与末行留白 var 同一个数，所以自动跟随，但任何绕过实测写死坞高的地方都会在这里分叉。作用域规则如果只写一档页面（`.workspace-page` 或 `.chat-screen`），另一档页面仍有直角框。
- Deferred: 用户问题截图里若还有坞区之外的条目（本轮这两处之外），另开卡处理；exe 与 `dist-offline` 重打包等本线收齐后统一做。
- Non-goals: 不改 Android、不改后端、不动发送/附件的禁用语义、不引入新的测量代码、不删页面级焦点框规则本身。

## Goal

让玻璃输入卡的焦点反馈只剩卡自己那圈圆角绿环，并把坞内说明句改成「空态出现、打字即退场」。

## Scope

### In scope
- `frontend/src/styles/workspace.css`：新增两档页面作用域的 `textarea:focus-visible { outline: none }`；`.composer-help` 去掉居中。
- `frontend/src/components/Composer.tsx`：`.composer-help` 改为按 `value.trim()` 条件渲染，并在文档注释里写明退场语义。
- `frontend/src/copy/zh-CN.ts`：`home_composer_note` 收短。
- 判据：`frontend/test/styles/reading-shell.test.ts` 两个新 describe ＋ 新建 `frontend/test/components/Composer.test.tsx`。
- 真实浏览器实测与截图。

### Out of scope
- Android 布局与资源、后端、发送与附件语义、`--dock-h` 测量机制（085 已交付）、exe 重打包。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 改的是焦点框的归属与说明句的出现条件；必须保留按钮/chips 的键盘焦点框、玻璃卡的 `focus-within` 绿环与模糊、草稿与引用条、非对话页复用 `Composer` 的路径。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 直角框的两位生产者是 `pages.css:801-804`（选择器 801-803，`outline: 2px solid var(--green)` 在 804）与 `workspace.css:1119-1121`（选择器 1119-1120，声明在 1121；本卡都不改，只被反向压回）；反向压回的规则写在 `workspace.css:672-675`；说明句唯一生产者 `components/Composer.tsx:92-94`，文案生产者 `copy/zh-CN.ts:74`（对话页默认）与 `:86`（首页覆盖）；消费者 `pages/HomePage.tsx:52-53`（传 `note` 与 `dockClassName`）与 `pages/ChatPage.tsx`（用默认文案）；卡片绿环 `glass.css:136-142`。桌面壳与 Web 消费同一份 `dist-offline`，无平台分支。
- Native bridge or independent-client compatibility/update requirements: 无原生桥接；桌面壳不需要版本协商。
- Behavior-neutral non-applicability reason: 不适用。

## Documentation impact record

- Changed runtime facts: 玻璃输入卡内不再画直角焦点框，焦点反馈唯一由 `.composer:focus-within` 的圆角绿环承担；坞内说明句从常驻改为空草稿时出现、打字即退场；首页那条说明文案收短。
- Authoritative code/config/migration evidence: `frontend/src/styles/workspace.css:668`（成因注释）、`:672-675`（作用域规则，`outline: none` 在 `:674`）、`:741-743`（注释）与 `:744-748`（`.composer-help` 不再居中）；`frontend/src/components/Composer.tsx:28-29`（语义注释）、`:92-94`（条件渲染）；`frontend/src/copy/zh-CN.ts:74`、`:86`；`frontend/src/styles/glass.css:136-142`（保留的绿环）；`frontend/src/styles/pages.css:801-804` 与 `frontend/src/styles/workspace.css:1119-1121`（未改的两条页面级焦点框，选择器在后、`outline: 2px solid var(--green)` 分别在 804 与 1121）。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`Docs/04-ux-design/2026-10-25-阅读页玻璃方案/知芽-阅读页玻璃方案-v1.html`、`desktop/README.md`、根 `README.md`。
- Documentation files updated: `Docs/04-ux-design/design-system/README.md` 变更记录新增 2026-10-25「输入卡里那道直角框归了玻璃卡，说明句改成空态才出现」一条。
- Producer/consumer/sibling/compatibility paths checked: 说明句的唯一渲染点就是 `Composer.tsx:92`，两档页面共用，无第二处生产者；`grep -rn "composer-help" frontend/src frontend/test` 只命中样式（`workspace.css:744`）、组件（`Composer.tsx:93`）与两份判据（`Composer.test.tsx` 三处、`reading-shell.test.ts:205`），不加这个作用域时还会命中构建物 `frontend/dist-offline/index.html` 与 `frontend/dist/assets/style-*.css`（那两处由本轮 `frontend verify` 重出，不是生产者）；`grep -rn "textarea:focus-visible" frontend/src/styles` 共 5 行（两条既有 + 本卡新增两条 + 一条注释）。
- Superseded phrases/examples reverse-searched: 历史方案稿 `Docs/04-ux-design/2026-10-25-阅读页玻璃方案/知芽-阅读页玻璃方案-v1.html:515-519` 的 `.composer-help { margin-top: 6px; text-align: center; font-size: 10px }` 是该面的原设计，属历史稿件——按规则不改写，只在 README 新条目里写明它被本卡推翻（那里连 `--fs-cap` 都是裸 10px，生产侧早已落在 token 上）；反向搜索生产样式里 `.composer-help` 的 `text-align` 残留 = 0（`grep -rn "composer-help"` 只命中 `workspace.css:744`、`Composer.tsx:93` 与两份判据）。
- `documentation impact: none` reason and evidence: 不适用。
- Historical/PRD handling: 不改写 081/085 与方案稿的历史记录，只追加 2026-10-25 当前态一条并指回被推翻处。

## Acceptance criteria

- [x] 键盘焦点进入输入框时，卡内只有跟随 28px 圆角的绿环，没有第二个直角方框；按钮与其他输入的焦点框不受影响。
- [x] 说明句仅在空草稿（含纯空白）时渲染；开始打字即退场，卡片变矮且不留空行。
- [x] 退场后两个动作按钮仍然禁用且可访问名仍写明尚未接通。
- [x] 首页说明文案收短并左对齐，不再居中横在卡里。
- [x] 说明句出现/退场引起的坞高变化由 085 的实测机制自动吸收，本卡未新增测量代码。
- [x] `pnpm exec vitest run`（28 文件 / 197 例）与 `pnpm run lint` 全绿。
- [x] 真实 Chromium 首页与对话页两档路由的空态/打字态截图与读数为证。

## TDD execution record

### RED — failing test first
- Test/scenario: 两片各自先红。第一片（直角框）`frontend/test/styles/reading-shell.test.ts:178` 新增 `describe("输入框焦点由玻璃卡承担，输入格自身不画框")` 2 条——① 两条页面级规则仍在，且必须存在一条 `.workspace-page .composer textarea:focus-visible, .chat-screen .composer textarea:focus-visible { … outline: none }` 的三级作用域规则；② `glass.css` 的 `.composer:focus-within` 仍画 `0 0 0 2px` 的 `var(--green)` 环（防止「删掉内框」被误做成「删掉焦点反馈」）。第二片（说明句）新建 `frontend/test/components/Composer.test.tsx`（79 行，jsdom ＋ `createRoot`/`act`）4 条——空草稿说明句在场且文案取自 copy 表、纯空白也算空态、`"你好"` 时 `.composer-help` 为 `null` 且 chip 数不变、退场后两个按钮仍 `disabled` 且可访问名含「尚未接通」；并在 `reading-shell.test.ts:203` 新增 `describe("空态说明句是提示而不是标题")` 1 条，断言 `.composer-help` 不含 `text-align: center`。
- Command: `pnpm exec vitest run test/components/Composer.test.tsx test/styles/reading-shell.test.ts`
- Exit code: 1
- Observed failure: 第一片 `Tests 1 failed | 13 passed (14)`，`AssertionError: 缺少压过页面级焦点框的作用域规则: expected null not to be null`（`reading-shell.test.ts:192`）。第二片 `Tests 2 failed | 17 passed (19)`——`AssertionError: 有草稿时不许再占一行说明: expected <p class="composer-help"></p> to be null`（`Composer.test.tsx:64`，收到的正是那条对话页默认文案）与 `AssertionError: 居中的 11px 灰字读起来像表单系统提示: expected '\r\n  margin: var(--s2) 0 0;\r\n col…' not to match /text-align:\s*center/`（`reading-shell.test.ts:206`）。
- Failure reason validated: 第一片，改前 `workspace.css` 里根本没有带 `.composer` 的三级选择器，正则取回 `null`——红的是实现缺失，不是判据写法。第二片，这份 RED 是把 `Composer.tsx` 与 `workspace.css` 临时还原成改前形状（无条件 `<p>` ＋ `text-align: center`）后跑出来的，跑完在同一条命令里从备份复制回实现并跑 `md5sum -c`。**这次 `md5sum -c` 的回显当时只在终端读过、没有进两份 RED 转录**（两份 RED 转录里 grep `md5` 为 0 命中），所以复核时把恢复锚点固化成了磁盘证据：`artifacts/evidence/web-composer-focus-note-2026-10-25/post-change-shape/{Composer.tsx,workspace.css,md5.txt,verify.txt}`——前两份是改后（GREEN）形状，`md5.txt` 记 `dc91a26a…` / `4e2b0b0d…` 并写明复现命令，`verify.txt` 是那条命令的落盘转录（20:59:31 跑出 `OK` / `OK` / `EXITCODE(md5sum -c)=0` 与两条 `cmp` 退出码 0，即锚点与 `frontend/src/` 现文件逐字节相同；第二轮复核也独立跑到相同结果）。当时的临时备份目录曾叫 `artifacts/tmp/086bak/`（前缀沿用改名前的 086），它与锚点两份文件逐字节相同，锚点固化后已 `rm -rf`，scratch 目录不再承担证据。**证据**：`artifacts/evidence/web-composer-focus-note-2026-10-25/087-red.txt` 与同目录 `087b-red.txt`，恢复锚点与复现转录在同目录 `post-change-shape/`
- 口径补充：上面 `Command` 那行是两片的共同标靶命令，但**第一片的 RED 实际是用更窄的 `pnpm exec vitest run test/styles/reading-shell.test.ts` 跑出的**（`087-red.txt` 里只有这一个文件、14 例），第二片才用标靶命令；`087b-red.txt` 与 GREEN 的 `087-green-target.txt` 都是标靶命令那条，逐字同一条命令才对得上。第一片那条既有的绿环断言（②）首跑即绿，因为 `glass.css:136` 本来就在——它不是 RED，是给「只删内框」划的地板。第二片改前/改后形状曾经只在 `artifacts/tmp/086bak/`（前缀沿用改名前的 086），现已固化成磁盘锚点 `post-change-shape/`，scratch 目录本卡结案时已删除。

### GREEN — minimum implementation
- Implementation: `frontend/src/styles/workspace.css:672-675` 新增两档作用域 `outline: none`（`:668-671` 注释记下特异性算术：0-2-1 压过 0-1-1，故用 0-3-1 反向压回，不依赖 import 顺序）；`.composer-help`（`:744`）删 `text-align: center`，成因注释写在 `:741-743`；`frontend/src/components/Composer.tsx:92-94` 改 `{value.trim() === "" ? <p className="composer-help">…</p> : null}`，文档注释 `:28-29` 补退场语义；`frontend/src/copy/zh-CN.ts:86` 收短为「点推荐问题或历史主题进入对话 · 发送与上传尚未接通」。
- Command: `pnpm exec vitest run test/components/Composer.test.tsx test/styles/reading-shell.test.ts`
- Exit code: 0
- Passing evidence: 标靶命令 2 files / 19 tests passed（`087-green-target.txt`）。此外同一实现另跑三条：`pnpm exec vitest run` → 全量 28 files / 197 tests passed（`087-green-full.txt`）；`pnpm run lint` 五步全过（`tsc --noEmit`、依赖方向 45 文件、设备时间、可见文案 i18n、夹具同步 `src=13 dst=13 bad=0`，`087-lint.txt`）；`node artifacts/tmp/087-focus-probe.mjs` → `PROBE pass=10 fail=0`、`node artifacts/tmp/087-note-probe.mjs` → `PROBE pass=15 fail=0`（两份转录，`EXITCODE=0`）。
- Regression evidence: 既有 `reading-shell.test.ts` 前 12 条（081 外壳 7 条 ＋ 085 悬浮羽化 5 条）未改仍绿；`ChatPage.test.tsx` 14 条（草稿、引用、浮条、滚动恢复）与 `HomePage.test.tsx` 全绿，证明首页 `note` 覆盖路径与 `home-dock` 未受影响；焦点探针里「按钮/条目的焦点框没有被误伤」两档页面各 PASS，`cardBlur` 仍读回 `blur(28px) saturate(1.5)`。

### REFACTOR — optional
- Cleanup: 未新增文件；条件渲染直接用已有 `value` prop，没有引入新状态或 prop。组件文档注释同步改写而不是另起一段。
- Regression evidence: 同 GREEN 全量 197 例 ＋ `087-lint.txt` 退出码 0。

### LOOP — optional
- Command and rounds: `node artifacts/tmp/087-focus-probe.mjs` 两轮 ＋ `node artifacts/tmp/087-note-probe.mjs` 一轮。焦点探针第一轮**全绿但结论不成立**，第二轮才是 10 PASS / 0 FAIL（`EXITCODE=0`）；说明句探针首跑即 15 PASS / 0 FAIL，复跑一次同形。
- Failure identity and repeat count: 一处，且缺陷在仪器不在实现——首版探针用 `document.querySelector('.composer textarea').focus()` 取焦点后读 `getComputedStyle`，两档页面都报 `outlineStyle: "none"`，三条「直角框已消失」全绿，与用户截图上那道方框直接矛盾。成因：程序化 `focus()` 不会把浏览器「最后一次交互来自键盘」这个状态翻真，`:focus-visible` 根本没命中，量到的是未命中时的样式，也就是**假绿**。改法两条：① `focus()` 之后再用 CDP `Input.dispatchKeyEvent` 打一次真实按键（`artifacts/tmp/087-focus-probe.mjs:90-94`，注释写明「程序 focus() 不翻，那样量到的是 :focus-visible 未命中的假绿」）；② 把「命中 `:focus-visible`」本身升成断言（`:101`），让地基不成立时探针直接红而不是静默绿。
- Stop condition hit: 改后复跑，两档页面第一行都是 `PASS …：探针确实处于 :focus-visible 态 {"active":true}`，其后 `taOutlineStyle: "none"`、`cardRingSpreads: ["0px 0px 0px 1px","0px 0px 0px 2px"]`、`cardBlur: "blur(28px) saturate(1.5)"`、按钮 `outlineStyle: "solid" / 2px` 才是有效读数；两张焦点图重出为 `087-*-1440-focus.png`。
- Handoff: `dist-offline` 重出与 Windows 便携 exe 重打包不在本卡；等用户确认整树提交时一并处理。
- Evidence on disk vs console-only: 落盘的有 `087-red.txt`（第一片主判据 1 failed / 13 passed）、`087b-red.txt`（第二片 2 failed / 17 passed）、`087-green-target.txt`、`087-green-full.txt`、`087-lint.txt`、`087-focus-probe.txt`（终轮 10/0）、`087-note-probe.txt`（15/0）、`087-guards-core.txt` 与翻状态后的复跑 `087-guards-core-final.txt`（两份同判 `pass=39 fail=0`、`VERDICT: PASS (subset only — 39/45 ...)`、`EXITCODE=0`）、`087-guards-precommit.txt`、`post-change-shape/{md5.txt,verify.txt,Composer.tsx,workspace.css}`，以及七张图 `087-home-1440-focus.png`、`087-chat-1440-focus.png`、`087-home-1440-full.png`、`087-home-empty.png`、`087-home-typed.png`、`087-chat-empty.png`、`087-chat-typed.png`。焦点探针**首轮那份假绿转录没有留在盘上**（第二轮把同一个文件覆盖了），那条缺陷的可读证据是探针源码 `:90-94` 的注释与终轮第一行断言，不是某份历史转录——当前态由每轮都重跑的「确实处于 `:focus-visible` 态」守住，假绿不会静默回来。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 两片都按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 特异性算术是本卡的全部：`.composer textarea`（0-1-1）里的 `outline: none` 从来就压不过页面级的 `textarea:focus-visible`（0-2-1），所以那道直角框一直都在，只是没人用键盘停在那里看。修法用 `.workspace-page .composer textarea:focus-visible` / `.chat-screen .composer textarea:focus-visible`（0-3-1）反向压回，两档页面都要写，缺一条另一条就仍在。
- 不能整体删页面级那条规则：它同时给 `button` 与其他 `input` 画焦点框，是键盘可达性的地板；`reading-shell.test.ts:187-188` 因此先把「两条规则仍然存在」钉住，`:189-193` 再钉「存在一条压过它的三级作用域」。
- 删内框不等于删焦点反馈——`glass.css:136` 那圈 `0 0 0 2px color-mix(in srgb, var(--green) 26%, transparent)` 跟随 `--r-2xl` 圆角，实测 `cardBlur` 仍是 `blur(28px) saturate(1.5)`，模糊与绿环都没被这条改动碰掉。
- `:focus-visible` 不能用 `element.focus()` 取证：程序化聚焦不匹配该伪类，读到的是「框不存在」的假绿。必须打真实按键（CDP `Input.dispatchKeyEvent`），并且把 `focusVisible: true` 本身断言出来。
- 说明句的退场条件是 `value.trim() === ""`，不是 `value === ""`：纯空白仍算空态，否则删干净空格会闪一次框。
- 退场不需要任何测量代码：085 的 `useDockHeight` 已经把 `.chat-dock` 的实测高度单点写回 `--dock-h`，本卡实测说明句在/退两态的 `--dock-h` 是 176px / 140px，卡片高度 164px / 128px，羽化带与末行留白 var 同一个数，跟着走。这也反过来验证了 085 那句「一个数只有两处消费者」站得住。
- 卡片矮 36px 会不会让末行留白过紧：实测对话页两态 `cardBottom` 都是 876（坞贴底缘，变的是高度不是位置），且两态都无横向溢出；末行是否被压由 085 的窄窗断言与探针 `lastContentBottom < dockTop` 继续守。
- 本卡编号是 087 而不是 086：`.codex/tasks/T-2026-10-25-ui-readability-parity-requirement-086.md`（22 条评审线的需求基准）已占用 086，是在本卡证据目录建好之后才发现的碰撞。证据文件先前以 `086-` 为前缀，已全部重命名为 `087-` 并重跑两个探针，使转录里的 `SHOT` 行与磁盘文件名逐字一致；旧 `086-*` 图与探针转录已删除，`086-red.txt`/`086b-red.txt` 两份 vitest 转录（内容不含前缀）直接改名保留。同一时段那条评审线还在继续起号（088 于 20:07、089 于 20:15 落盘），087 与它们不撞——但「先 `ls .codex/tasks/` 再命名证据」这条动作以后要放在写第一份证据之前，不是之后。

## Verification report

- Verdict: PASS（独立复核通过，2026-10-25）——两轮只读复核。第一轮判 **PARTIAL**：实现、判据、七份转录读数、产物代次对比、README 未被吞行（`git diff` 只有新增 0 删除）、077 条目首尾完整、`validate_tasks errors=0` 全部核对成立，但抓到 7 处「卡片与磁盘现状不符」——① `.composer-help` 成因注释在 `:741-743` 而非 `:740-742`（740 是空行）；② 作用域规则应是 `:672-675`（`outline` 在 674、闭括号 675）；③ 「两条既有规则仍在」的断言在 `reading-shell.test.ts:187-188` 而非 `:188-189`；④ 七张截图不能统一说成「1440×900 scale 2」——五张整屏 `2880×1800`，两张焦点特写是卡片区域裁切（clip 外扩 16px、`scale: 2` 叠 `deviceScaleFactor: 2`，有效 4×）故为 `3712×784` / `3264×784`；⑤ core 档判据行被截断引用；⑥ `timeout=600` 字面在 `run_all_guards.py:225`；⑦ 第二片那次 `md5sum -c` 的回显根本没进转录，必须标 console-only 并给出磁盘旁证（备份目录存的是改后形状、`cmp` 与工作树逐字节相同）。七条全部按复核者打印的磁盘内容改正后送第二轮。第二轮判 **PASS**：七条逐条改对（含 PNG 头字节实测 `(896+32)×4=3712`、`(784+32)×4=3264` 与 `SHOT` 行逐字一致），并另抓到 2 处——本卡写的 `dist-offline` 20:12/519457/`34f66d3e…` 已被并行 089 线在 20:33 重出覆盖为 5876993/`165837e9…`（代次读数须标 console-only），以及 `pages.css` / `workspace.css` 的引用只到选择器行而未含 `outline` 声明行（应为 `801-804` / `1119-1121`，实测声明在 804 与 1121）。两处亦已改正，无遗留的判据放宽、也没有删除或放宽任何既有断言。
- Evidence: 单测/样式判据见上；真实交互证据在 `artifacts/evidence/web-composer-focus-note-2026-10-25/`——`087-focus-probe.txt`（首页与对话页 `#/chat/0` 各 4 项 ＋ 按钮焦点态各 1 项 ＋ 说明句 NOTE 读数 2 项，`PROBE pass=10 fail=0`、`VERDICT: PASS`、`EXITCODE=0`）、`087-note-probe.txt`（两档路由空态/打字态/清空回到原位，`PROBE pass=15 fail=0`、`EXITCODE=0`）与七张截图：五张整屏 `2880×1800`（1440×900 视口、`deviceScaleFactor: 2`）＝ `087-home-1440-full.png`、`087-home-empty.png`、`087-home-typed.png`、`087-chat-empty.png`、`087-chat-typed.png`；两张焦点特写是**卡片区域裁切**，`Page.captureScreenshot` 的 `clip` 取卡框外扩 16px 再叠 `scale: 2`（探针 `:57`），叠在 `deviceScaleFactor: 2` 上有效 4×，故为 `3712×784`（首页卡 896×164）与 `3264×784`（对话页卡 784×164）＝ `087-home-1440-focus.png`、`087-chat-1440-focus.png`，文件名里的 1440 指视口宽而不是像素宽，转录的 `SHOT` 行逐字记着这两组像素数。探针驱动 `artifacts/tmp/087-*-probe.mjs` 复用仓库内 `.codex/scripts/dev/parity/parity_cdp.mjs`，未新增依赖；被测页面是本机 dev server `http://127.0.0.1:5174`。守门两轮：`087-guards-core.txt`（终态复跑为 `087-guards-core-final.txt`）`RUN-ALL-GUARDS pass=39 fail=0 inconclusive=0 fault=0 timeout=0`、`VERDICT: PASS (subset only — 39/45 entry(ies) judged, not the whole roster)`、`EXITCODE=0`；`087-guards-precommit.txt` `pass=5 fail=1`、`VERDICT: FAIL (1 guard(s) red or silent)`、`EXITCODE=1`，唯一红是 `line-budget`（`业务变更行: 1385` > 500，混合工作树的提交形状，非本片缺陷，既有登记），其余五条 `check_offline_bundle`(0.2s) / `--selftest`(0.2s) / `frontend verify`(32.6s，`steps=16 ok=16 bad=0`) / `no-kotlin-check`(79.0s) / `guard-selftest -Fast`(326.3s) 全 PASS。`guard-selftest -Fast` 这次用 326.3s 才打出判决——比 085 抬天花板前的 300s 默认上限还长，是那条改动（`.codex/scripts/run_all_guards.py:224-225`，`timeout=600` 字面在 `:225`）第六份落盘依据。
- 副作用（跑守门带来的产物面变化，必须记下来）：precommit 档的 `frontend verify` 内含 `pnpm run build`（`frontend/scripts/verify.mjs:19` 把 `inline_offline` 列进脚本、`:26` 那条 build 步骤要 `needle: "offline ok"`），所以这一轮重出了 `frontend/dist-offline/index.html`，产物里 `grep -c "workspace-page .composer textarea:focus-visible"` = 1（本卡那条作用域规则已进产物，同轮 `check_offline_bundle` PASS）。**读数要按代次读，别当成静态事实**：本卡那轮（20:12）出的是 519457 字节、md5 `34f66d3e699a76fcfc606626456149cc`；复核时（20:33）同一文件已被并行的 089 线（改 `frontend/scripts/inline_offline.mjs` 内嵌字体后重跑构建）覆盖成 5876993 字节、md5 `165837e93d32e93855be432d441a770f`，作用域规则那条命中数仍是 1。两代 md5 都只是终端 `md5sum` 读数、没有任何转录佐证（console-only），能自证的只有「当前产物含这条规则」那句 grep 与同轮 `check_offline_bundle` / `frontend verify` 的绿。这不是本卡的交付，是仪器的写盘副作用加上并行线的覆盖——读起来别把它当成「产物已交付」。
- Changed/deleted assertion rationale and equivalent behavior coverage: 没有删除或放宽任何既有断言；全部为新增（样式 3 条 ＋ 组件 4 条）。既有的 `text-align: center` 是被删的**实现**而不是断言——此前没有任何判据钉住它居中。
- Gaps: ① 说明句退场只有 CSS/渲染层取证，未做动画过渡（直接消失），是否符合用户观感待其过目截图；② 焦点环用 `color-mix` 26% 绿，在减少透明度降级面（`prefers-reduced-transparency`）下未单独取证——那条路径本卡没碰，环的可见性沿用 085 的降级实测；③ `home_composer_note` 收短是文案判断，未经产品复核；④ 桌面壳那一代产物还没跟上：Web 离线单文件 `frontend/dist-offline/index.html` 已含本卡作用域规则（复核时 20:33、5876993 字节、md5 `165837e9…`，由并行的 089 线重出；本卡那轮 20:12 出的是 519457 字节、`34f66d3e…`，代次口径见上一条），但它的派生副本 `desktop/app/index.html` 仍是 10:22 的上一代（518464 字节、md5 `f20d7e25…`、同一条规则 0 命中，由 `desktop/scripts/sync-app.mjs` 在 start/smoke/build 时刷新，本卡未跑），Windows 便携 exe 因此也还没用本卡产物重打包。这几组字节数与 md5 都是终端 `ls`＋`md5sum` 读数、未落盘（console-only）；能自证的只有产物里那条 `grep -c` 命中数与同轮 `check_offline_bundle` / `frontend verify` 的绿。
- Risks: `--dock-h` 只有一个生产者，说明句退场把它从 176px 拉到 140px；若将来有人在别处写死坞高，这条退场会立刻让末行留白与羽化带分叉。作用域规则依赖 `.composer` 这个类名，改卡片类名会让直角框静默复发——`reading-shell.test.ts:189-193` 把选择器原文与 `outline: none` 一起钉在正则里，改一处就红。

## Handoff notes

- Completed: 直角焦点框（两档页面作用域反向压回、绿环与模糊保留实测）、说明句改成空态出现/打字退场（含纯空白语义）、首页文案收短、`.composer-help` 去居中；判据 7 条新增、全量 197 例与 lint 全绿、浏览器 10/15 双探针 PASS、七张截图。
- In progress: 无；两轮独立只读复核已过（第一轮 PARTIAL 7 条全部改正、第二轮 PASS），翻 `verified` 后补跑的那轮 core 档收尾转录见 `087-guards-core-final.txt`。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 已跑：`validate_tasks.py`（`errors=0`）、`metrics_report.py --write`、`run_all_guards.py --tier core`（39/39 PASS）与 `--tier precommit`（唯一红 `line-budget`，混合树提交形状）；② 两轮独立只读复核已过；③ 翻 `verified` 后再补一次 `metrics_report.py --write` 与 core 档收尾轮（`087-guards-core-final.txt`）；④ 用户确认后整树一次提交（不推送），再统一跑 `desktop/scripts/sync-app.mjs` 与 Windows 便携 exe 重打包（注意 `desktop verify` 会覆盖 `artifacts/evidence/desktop-shell/2026-10-24/`，跑完要从 HEAD 恢复并把副作用写进当片风险）；⑤ scratch 备份目录 `artifacts/tmp/086bak/` 已在锚点固化后删除（`cmp` 证明它与 `post-change-shape/` 两份逐字节相同，删除不丢信息）。
- Watch-outs: 卡片编号 086 已被「22 条评审」需求占用，本卡是 087；`:focus-visible` 的取证必须打真实按键，任何后续用 `focus()` 读焦点框的探针都会假绿。
