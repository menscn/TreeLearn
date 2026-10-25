# Filename: T-2026-10-25-dock-float-feather-085.md
# Codex Harness Task: 输入坞改真悬浮，底缘用羽化代替色带

id: T-2026-10-25-085
title: 输入坞改真悬浮，底缘用羽化代替色带
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-reading-glass-parity-requirement-080.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 58
shared_code_touched: true
depends_on: [T-2026-10-25-reading-glass-shell-081.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 080 线交付的阅读玻璃外壳里，输入坞视觉上仍像一条压在正文上的底部栏；本卡把它改成真正悬浮的输入框，底缘改用羽化过渡。
- Problem/background: 用户 2026-10-25 在 127.0.0.1:5174 圈出 `.chat-dock`（bounds 264,477 867×176）说明：「上面是增加羽化过渡，而不是有个明显的背景，我需要的是这个输入框盖在输出文本上面，而不是感觉整个这个底部栏盖在输出内容上」。核对源码后确认成因不是颜色——`glass.css:123` 已把坞背景设为 transparent，真正的硬边来自 `workspace.css:569` 的 `flex: none`：坞与 `.chat-scroll` 各占一行，阅读面板被迫在它上方平口截断。
- Users/scenarios: 桌面与浏览器里滚动长回答；正文末行需要能滚到输入框之上；宽窗时坞两侧空白不得吃掉文字选区与点击。
- Expected outcome: 输入框作为独立玻璃面悬浮在正文之上；底缘没有平口色带，正文从坞顶开始淡出、到坞底仍留一档残影供玻璃采样；引用条与 chips 出现时羽化带与末行留白自动跟随坞高。
- Core flow and measurements: 本卡只改布局与材质，不改任何状态流。回退撤销 `frontend/src/styles/workspace.css` 与 `ChatPage.tsx`/`Composer.tsx` 的 ref 接线、删掉 `frontend/src/hooks/useDockHeight.ts` 即可，`tokens.css` 与四方链不在回退面内（它们最终未被改动），无数据迁移。
- Inputs/outputs: 输入是 `.chat-dock` 的实测高度；输出是 `.chat-main` 上的 `--dock-h` 自定义属性。不新增持久化状态，不改夹具与 API。
- State and exception handling: `ResizeObserver` 不存在时保持 CSS 声明的 `--dock-h` 默认值，界面仍可用；坞出流后 `pointer-events: none`，只有 `.composer` 与其中的控件接收指针事件。
- Constraints and trade-offs: 不新增依赖；不改 Android 与后端；颜色/圆角/阴影继续只用 tokens；羽化必须用 `mask-image` 而不是盖一层色带（色带在白色阅读卡与米色页面两种底色上会显灰）；末行留白与羽化高度共用同一个 `--dock-h`，不允许出现第二个字面量。
- AI recommendation and rationale: 用户已在三个方案中拍板 A（坞出流 + `mask-image` 羽化）。B（`::before` 渐变色带）只作为 `prefers-reduced-transparency` 的备选，本卡不实现，因为 mask 淡出与透明度降级并不冲突，多写一层色带反而制造第二个背景。
- User-confirmation decisions: 「选择A」——坞脱离文档流悬浮，羽化用遮罩淡出正文本身。
- AI-autonomous decisions: 羽化终点用 `--dock-feather-ghost` 一档残影而不是全透明；`--dock-h` 由 `useLayoutEffect` + `ResizeObserver` 写回 `.chat-main`；坞 `z-index: 2`，压在窄窗分支面板（8）、侧栏抽屉（20）、选区浮条（30）之下。
- Acceptance evidence: `reading-shell.test.ts` 新断言先红后绿；真实浏览器截图证明底缘无平口色带、末行可滚到输入框之上、宽窗坞两侧仍可划选正文；引用条出现时羽化带跟随。
- Facts: `.chat-dock` 现为 `position: relative; flex: none; background: var(--bg)`；`.chat-reading` 底部内边距是 `var(--s8)`；`.persona-feedback` 用 `bottom: calc(100% + var(--s2))` 挂在坞上方，不参与坞高；`ForestScene.tsx:206` 已有 `typeof ResizeObserver !== "function"` 的守卫先例。
- Assumptions: Chromium 的 `mask-image` 与兄弟元素的 `backdrop-filter` 不冲突——遮罩只作用于 `.chat-scroll` 自身绘制，坞是兄弟节点，不在被遮罩的盒子里。
- Unknowns: 无会改变范围或验收的未知项。
- Risks: 坞出流后不再撑高布局，若 `--dock-h` 测不准，末行会被输入框压住；`mask-image` 会连带淡出滚动条；`pointer-events: none` 漏给子元素会让整条坞挡住正文选区。
- Deferred: 色带降级、坞内说明文字的位置重排、滚动到顶时羽化带收起。
- Non-goals: 不改 Android、不改后端、不动选区浮条与引用流程的行为语义、不重新打包 exe（等本线其余问题一并处理）。

## Goal

把阅读页输入坞从「占一行的底部栏」改成「悬浮在正文之上的玻璃输入框」，用遮罩羽化取代底缘的平口色带。

## Scope

### In scope
- `frontend/src/styles/workspace.css` 的 `.chat-main`、`.chat-scroll`、`.chat-reading`、`.chat-dock`、`.composer` 几何与遮罩。
- `frontend/src/pages/ChatPage.tsx` 与 `frontend/src/components/Composer.tsx` 的坞高测量接线。
- 羽化残影那档数值最终落在 `workspace.css` 的 `.chat-main` 局部自定义属性上，**不是** `tokens.css`——原计划「`tokens.css` 新增一档 token」被 LOOP ③ 判红后撤销（`tokens.css` 是四方链生成物，手改破坏逐字节比对，且 Android 无遮罩消费者）。
- `frontend/test/styles/reading-shell.test.ts` 断言与浏览器证据。

### Out of scope
- Android 布局与资源、后端、发送与附件语义、色带降级路径、exe 重打包。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 改的是坞的定位方式与底缘过渡；必须保留草稿、引用条与 chips、选区浮条、滚动位置恢复、人设弹层、复制整条回答、窄窗抽屉与两条玻璃降级路径。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 样式生产者 `frontend/src/styles/workspace.css`；消费者 `pages/ChatPage.tsx:165` 的 `.chat-main`、`:168` 的 `.chat-scroll`、`:242` 的 `Composer`（`:247` 传 `dockRef`）；`components/Composer.tsx:52` 的 `.chat-dock` 同时被 `pages/HomePage.tsx` 复用（`dockClassName` 可换），必须确认非对话页不受影响。
- Native bridge or independent-client compatibility/update requirements: 无原生桥接；桌面壳消费同一份 `dist-offline`，不需要版本协商。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 阅读页输入坞改为悬浮层（`position: absolute`，不再占正文的行），底缘用 `mask-image` 羽化取代平口截断；末行留白与羽化高度由实测的 `--dock-h` 单点供给。
- Authoritative code/config/migration evidence: `frontend/src/styles/workspace.css:288`（`.chat-main` 定位父级）、`:292`（`--dock-h: 168px` 首帧兜底）、`:294`（`--dock-feather-ghost`）、`:302`/`:310`/`:311`（`.chat-scroll` 遮罩）、`:318`/`:321`（阅读面底部留白）、`:579`/`:586`（坞出流与指针穿透）、`:599`/`:602`（输入框收回指针）、`:1133`（窄窗留白同源）；`frontend/src/hooks/useDockHeight.ts`（新文件，1–27）；`frontend/src/components/Composer.tsx:11`、`:37`、`:52`（`dockRef`）；`frontend/src/pages/ChatPage.tsx:14`、`:76`/`:77`、`:102`、`:165`、`:247`。`frontend/src/styles/tokens.css` 最终未改：它是四方链的生成物，且遮罩残影是 Web 独有构件、Android 无消费者。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`desktop/README.md`、根 `README.md`。
- Documentation files updated: `Docs/04-ux-design/design-system/README.md` 变更记录新增 2026-10-25「输入坞从占一行的底栏改成真悬浮，底缘改用遮罩羽化」一条，并在 080/081 那条末尾加「后账（085）」指回。
- Producer/consumer/sibling/compatibility paths checked: 样式生产者 `workspace.css` / `tokens.css`；消费者 `pages/ChatPage.tsx`、`components/Composer.tsx`；同族消费者 `pages/HomePage.tsx:44` 起挂 `<Composer`、`:53` 传 `dockClassName="home-dock"`，`.chat-dock` 的出流与 `pointer-events` 全部限定在 `.chat-dock` / `.chat-dock .composer` 两个选择器内，首页不受影响；`test/styles/chat-column.test.ts`、`glass-depth.test.ts`、`glass.test.ts` 三条既有断言未改仍绿。
- Superseded phrases/examples reverse-searched: 「输入坞靠内缩与间距和正文分离」（`reading-shell.test.ts` 用例标题已改为「玻璃面自身不画背景」；README 080/081 条目原文保留并加后账指回）；反向搜索 `background: var(--bg)` 在 `.chat-dock` 的残留=0；`flex: none` 在 `.chat-dock` 的残留=0。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写 080/081 的历史记录，只追加 2026-10-25 当前态一条。

## Acceptance criteria

- [x] `.chat-dock` 脱离文档流悬浮，`.chat-main` 成为定位父级；坞自身不再画背景。
- [x] `.chat-scroll` 底缘用 `mask-image` 淡出，色带与平口截断消失；末行留白与羽化高度同源于 `--dock-h`（含窄窗那条覆盖）。
- [x] 坞出流后 `pointer-events` 只落在输入框上，宽窗两侧空白仍可划选正文。
- [x] 引用条与 chips 出现时坞高变化被实测并写回，末行不被压住。
- [x] 非对话页复用 `Composer` 的路径不受影响；窄窗抽屉、分支面板、选区浮条层级正确。
- [x] `pnpm --dir frontend exec vitest run test/styles`、`pnpm --dir frontend test`、`pnpm --dir frontend lint` 全绿。
- [x] 真实浏览器 1440×900 与 720×900 截图为证。

## TDD execution record

### RED — failing test first
- Test/scenario: `frontend/test/styles/reading-shell.test.ts` 新增 `describe("输入坞悬浮与底缘羽化")` 5 条（悬浮出流与指针穿透、坞高单点声明、遮罩羽化不角色带、残影落 token 且样式无裸色、层级压在浮层之下）+ 新建 `frontend/test/hooks/useDockHeight.test.ts` 5 条（首帧写入、观察对象是坞、长高改写、量不到不写 0、无 `ResizeObserver` 不报错）。
- Command: `pnpm exec vitest run test/styles test/hooks`
- Exit code: 1
- Observed failure: `Test Files 2 failed | 4 passed (6)`、`Tests 5 failed | 27 passed (32)`，其中 `test/hooks/useDockHeight.test.ts` 是整档失败（转录 `085-red.txt:63` 打的是 `FAIL test/hooks/useDockHeight.test.ts [ test/hooks/useDockHeight.test.ts ]`，导入即不成立）；五条样式失败逐条为 `position: absolute`、`--dock-h`、`mask-image`、`--dock-feather-ghost`、`z-index` 缺失（`085-red.txt:29` 的原文是 `AssertionError: 坞必须浮在正文之上: expected NaN to be greater than 0`）。
- Failure reason validated: 失败点全部对应改前 `workspace.css` 旧坞的 `position: relative; flex: none; background: var(--bg)`（改前中间态没有落盘快照，具体行号不再声称；现位在 `:579`/`:586`）与断言要求的那个 token 并不存在，不是测试写法问题。**另注**：hook 首轮还有 5 例 `Cannot read properties of undefined (reading 'offsetHeight')`，成因是测试把假节点当 ref 传（缺 `{ current: … }` 包装），属测试自身写法、当场已修——这一条只读出在终端，`085-red.txt` 里没有它，落盘的是上面那份整档导入失败的转录。
- Evidence: `artifacts/evidence/web-dock-feather-2026-10-25/085-red.txt`

### GREEN — minimum implementation
- Implementation: `frontend/src/styles/workspace.css:288` `.chat-main` 加 `position: relative`、`--dock-h: 168px` 首帧兜底与 `:294` `--dock-feather-ghost`；`:302`/`:310`/`:311` `.chat-scroll` 双前缀 `mask-image: linear-gradient(to bottom, var(--ink) calc(100% - var(--dock-h)), var(--dock-feather-ghost))`；`:318`/`:321` 阅读面底部留白改 `calc(var(--dock-h) + var(--s6))`；`:579` 坞改 `position: absolute; right/left/bottom: 0; z-index: 2; pointer-events: none`（`:586`）并删 `flex: none` 与 `background: var(--bg)`；`:599`/`:602` `.chat-dock .composer` 收回 `pointer-events: auto`；`:1133` 窄窗 `.chat-reading` 留白同源。新建 `frontend/src/hooks/useDockHeight.ts`（`useLayoutEffect` + `ResizeObserver`，守卫 `typeof ResizeObserver !== "function"`，高度 0 不写）。`frontend/src/components/Composer.tsx:11`/`:37`/`:52` 增可选 `dockRef`；`frontend/src/pages/ChatPage.tsx:14`/`:76`/`:77`/`:102`/`:165`/`:247` 接线。`tokens.css` 最终未改（见 LOOP 第三轮）。
- Command: `pnpm exec vitest run test/styles test/hooks`
- Exit code: 0
- Passing evidence: 目标命令 6 files / 37 tests passed；全量 27 files / 190 tests passed；`pnpm run lint` EXITCODE=0（`tsc --noEmit`、依赖方向 45 文件、设备时间、可见文案、夹具同步全绿）。`artifacts/evidence/web-dock-feather-2026-10-25/085-green-styles.txt`、`085-green-full.txt`、`085-lint.txt`
- Regression evidence: 081 的 7 条外壳断言、`glass-depth.test.ts` 9 条、`glass.test.ts` 6 条、`chat-column.test.ts` 2 条全部未改仍绿；`ChatPage.test.tsx` 14 条（草稿、引用、浮条、滚动恢复）与 `HomePage.test.tsx` 全绿，证明 `home-dock` 路径未受影响。浏览器侧 15 项 PASS 见 `085-visual-probe.txt`。

### REFACTOR — optional
- Cleanup: 无额外重构；测量逻辑单独成 `useDockHeight`，避免 `ChatPage.tsx` 继续变长（301 → 307 行）。
- Regression evidence: 同 GREEN 全量 190 例。

### LOOP — optional
- Command and rounds: `node artifacts/tmp/085-dock-probe.mjs` 两轮 + `python -X utf8 .codex/scripts/run_all_guards.py --tier core` 一轮。探针第一轮 14 PASS / 1 FAIL，第二轮 15 PASS / 0 FAIL（`EXITCODE=0`）；core 第一轮 38/39（`check_design_tokens` 红），改后 39/39 PASS。precommit 第一轮 `guard-selftest -Fast` 打成 `TIMEOUT after 300s / VERDICT: INCONCLUSIVE`（`085-precommit.txt:17`，DIAG 停在 CASE 3，判据行始终没出），而同一条命令 085 串行单跑 265.8s（`085-precommit-selftest.txt`）、冷跑 `real 4m25.206s`（`085-guard-selftest-cold.txt`）都打出 `OVERALL = SELFTEST PASS (38 case(s))`，084 那轮 precommit 整档并行面上它给出判决用了 276.0s（`artifacts/evidence/desktop-shell/2026-10-25/084-precommit-guards.txt:17`，占原上限 92%）——判决时长已经贴着 300s 天花板，一次贴不住就是 `TIMEOUT`，所以红不在判据而在注册表给这条挂的天花板。按片 036 给 D12 立的口径「上限要盖过最长那次」把 `run_all_guards.py:224` 抬到 `timeout=600`；`check_run_all_guards` 复跑 `SELFTEST cases=33 broken=0 OVERALL: PASS`、注册表 `entries=45 core=39 problems=0`，整档复跑 `pass=5 fail=1 timeout=0`、`guard-selftest -Fast PASS rc=0 266.3s`，唯一红仍是 `line-budget`（见 `085-precommit-rerun.txt`）。动的是仪器自己的天花板，`needles=["OVERALL ="]` 与 `pass_codes` 一字未动。**口径如实记下**：最早那次 337.6s 的冷跑只在终端读出、当时没落盘，之后每次复跑都在 249–276s（249.1s / 265.2s / 265.8s / 266.3s / 276.0s），那个数既没被证伪也没被复现——本卡把它当辅助观察，天花板抬高的依据只用上面四份落盘读数。
- Failure identity and repeat count: 四处各一次，都不是重复失败而是四个不同缺陷——① `FAIL 滚动容器延伸到底缘 {scrollBottom:888, dockBottom:888}` 是探针把「铺到底」写成严格大于，实测两者恰好相等，判据自身错，改 `>=` 并加「坞顶在容器底缘之上 100px」；② `FAIL 720 窄窗末行不被压住 {lastContentBottom:852, dockTop:601, readingPadBottom:16px}` 是真缺陷：窄窗那条 `.chat-reading` 覆盖把留白写回固定 `var(--s4)`，坞从 176px 长到 291px 时末行被输入框压住；③ `FAIL 四方一致 — tokens.css 多出 tokens.json 没有的 token：['dock-feather-ghost']` 也是真缺陷：我把羽化残影直接写进了 `tokens.css`，而它是原型 `:root` → `tokens.json` → `colors.xml` → 生成物这条链的末端，手改既破坏逐字节比对，又给 Android 造了一个没有构件可消费的死数值；④ `TIMEOUT guard-selftest -Fast` 是仪器天花板低于它自己给出判决所需的时长（~265s 才是 PASS），不是本片缺陷，也不是那条守门的红。
- Stop condition hit: ②按 TDD 补断言（`rule(narrow(workspace), ".chat-reading")` 必须含 `calc(var(--dock-h)`），RED 现场跑出 `expected 'padding: var(--s6) var(--s4) …' to match`，改 `workspace.css:1133` 后留白 307px、末行 561 < 601。③同样先改判据再改实现：把「残影落在 token 上」换成「羽化终点单点声明，且不进四方 token 链」，RED 为 `expected '…--chat-column: 784px;…' to match /--dock-feather-ghost:/`，随后撤销 `tokens.css` 那两行、把变量挪到 `.chat-main:294`，并断言 `workspace.css` + `glass.css` 全文只剩这一处 `rgba(`。复跑 `check_design_tokens: 0 check(s) failed`、样式 32 例、全量 190 例、`lint` 0、core 39/39、探针 15/15，遮罩计算值逐字符不变（`linear-gradient(rgb(22, 36, 28) calc(100% - 176px), rgba(0, 0, 0, 0.26))`）。
- Handoff: 桌面 exe 重打包不在本卡，等本线其余问题收齐后统一做。
- Evidence on disk vs console-only（独立复核后补的口径）: 落盘的转录只有 `085-red.txt`（主判据 RED，5 failed / 27 passed 加 hook 整档未过）、`085-green-styles.txt`、`085-green-full.txt`、`085-lint.txt`、`085-visual-probe.txt`（终轮 15/15）、`085-precommit.txt`、`085-precommit-selftest.txt`、`085-precommit-rerun.txt`、`085-guard-selftest-cold.txt` 与四张截图。①②③ 三条的**现场 RED**（探针首轮的 `{scrollBottom:888, dockBottom:888}`、窄窗的 `lastContentBottom 852`、`check_design_tokens` 的 `['dock-feather-ghost']`）与探针首轮的 14/1 只在终端读出，第二轮把探针文件覆盖了——它们是修复过程的记录，不是当前判据的凭据；当前态由 `reading-shell.test.ts:140`（窄窗那条也含 `calc(var(--dock-h)`）与 `:160`（`tokens.css` 不含 `dock-feather-ghost`）两条断言在每次全量里重跑，所以缺陷不会静默回来。把这一点写明白，免得下一位读者去证据目录找一份不存在的转录。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- `--dock-h` 只有一个生产者（ChatPage 的测量）与两个消费者（`.chat-scroll` 的 mask、`.chat-reading` 的底部留白），任何一处写字面量都算漂移。
- 坞的 `pointer-events: none` 与 `.composer` 的 `auto` 必须成对出现，漏一侧就是选区被吃掉或输入框点不动。
- 遮罩终点留一档残影而不是全透明，否则玻璃面下方没有可采样的内容，模糊会失去意义。
- 起点用 `var(--ink)` 而不是 `#000`：`mask-image` 读的是 alpha，任何不透明色等价，取 token 就不用在样式里留裸色。
- `--dock-feather-ghost` 是 Web 独有构件，故意不进 `tokens.css` / `tokens.json` / `colors.xml` 那条四方链——链末端是生成物，手改会被 `check_design_tokens` 的逐字节比对判红，而 Android 根本没有遮罩这个消费者。它和 `--dock-h` 一样属于 `.chat-main` 的局部变量。
- 窄窗那条 `.chat-reading` 覆盖与宽窗那条是两处生产者，改留白必须同改，否则引用态在 760 以下必翻车；断言已把两条一起钉住。
- 本片顺带改了一个文件不在 Web 界面上：`.codex/scripts/run_all_guards.py:224` 的 `guard-selftest -Fast` 加 `timeout=600`。理由是这条守门给出判决本身就要 249–276s（五次落盘读数：249.1 / 265.2 / 265.8 / 266.3 / 276.0s）：084 的整档并行面跑出过 `PASS rc=0 276.0s`（占默认 300s 的 92%），085 同一档位同一位置则跑满 300s 只留下一个 `TIMEOUT`——一个「本轮没判」的形状，很容易被下一次读到这张表的人当成真红去查不存在的回归。改法遵循口径而不放宽判据：只抬 `timeout=`，`needles=["OVERALL ="]`、`pass_codes` 一字未动，注册表形状检查仍 `problems=0`。

## Verification report

- Verdict: PASS（独立复核通过，2026-10-25）——三轮只读复核：第一轮判 PARTIAL，抓到四处「卡片声称超出磁盘证据」（337.6s 无转录、README 用例数 6≠5、Gap ④ 与实际产物代次不符、Scope/Behavior-impact 的 `tokens.css` 与旧行号）；第二轮确认修订但发现「两次跑满 300s」只有一份落盘、「最长判决 276.0s」这份更强依据没被引用、`HomePage.tsx:44` 应为 `:53`、RED 段引了两串不在 `085-red.txt` 的原文；第三轮除 `run_all_guards.py:223` 应为 `:224` 外逐条成立，该行号与「`:577` 起」这句已由复核者自己打印的磁盘内容定正。实现、判据、守卫、文档面在第三轮全部对上，无遗留的判据放宽。
- Evidence: `artifacts/evidence/web-dock-feather-2026-10-25/` 下 `085-red.txt`、`085-green-styles.txt`、`085-green-full.txt`、`085-lint.txt`、`085-visual-probe.txt`（15 项 PASS，`EXITCODE=0`）、`085-precommit.txt`（第一轮 `timeout=1 / INCONCLUSIVE`，本卡改天花板的起因）、`085-precommit-selftest.txt`（runner 内串行 `PASS rc=0 265.8s`）、`085-guard-selftest-cold.txt`（不经并行面的冷跑 `real 4m25.206s` + `EXITCODE=0`）、`085-precommit-rerun.txt`（整档 `pass=5 fail=1 timeout=0`）、`085-precommit-final.txt`（状态翻 `verified` 并补跑 metrics 之后的最后一轮，同一形状 `pass=5 fail=1 timeout=0`，`guard-selftest -Fast PASS rc=0 249.1s`）与四张截图 `085-chat-1440-top.png`、`085-chat-1440-bottom.png`、`085-chat-1440-quote.png`、`085-chat-720.png`（2880×1800 / 1440×1800，scale 2 视口图）。探针驱动 `artifacts/tmp/085-dock-probe.mjs` 复用仓库内 `.codex/scripts/dev/parity/parity_cdp.mjs`，未新增依赖。
- Changed/deleted assertion rationale and equivalent behavior coverage: 只改了一条既有用例的**标题**——`reading-shell.test.ts`「输入坞没有横线，靠内缩与间距和正文分离」→「玻璃面自身不画背景」，因为「靠内缩与间距分离」正是本卡推翻的做法；其三条断言（无 `border-top`、`padding: 0 var(--s3) var(--s3)`、`glass.css` 的 `background: transparent` 且无 `box-shadow`）原样保留，覆盖范围不变。其余全部是新增断言，没有删除或放宽任何既有判据。
- Gaps: ① `--dock-h` 的 168px 首帧兜底值只在 JS 未跑或首帧前生效，`useLayoutEffect` 保证绘制前被实测覆盖，但真机上若首帧前就有绘制，兜底值与真实 176px 差 8px 留白——未做像素级取证；② 不支持 `mask-image` 的浏览器（本项目 Web 面只跑 Chromium）会退回到「坞悬浮但底缘不平口淡出」，未加 `@supports` 分支，因为无消费者；③ 色带降级路径按拍板不在本卡范围；④ 产物面分两代：`frontend/dist-offline/index.html` 已随本片重出（15:18、`55497409…`，内含 `--dock-feather-ghost`），但桌面壳的派生副本 `desktop/app/index.html` 由 `desktop/scripts/sync-app.mjs` 在 start/smoke/build 时刷新，本卡未跑它，仍是 084 的 `55053a70…`，Windows 便携 exe 也就还没用本片产物重打包。
- Risks: 引用条出现时羽化带随坞从 176px 涨到 291px，正文淡出区变长，视觉上「可读带」变窄——实测末行仍完整可见（549 < 597），但这是主观观感，留待用户过目截图裁决；`line-budget` 在混合工作树整树提交时仍会判红，属提交形状而非本片缺陷（既有登记）。

## Handoff notes

- Completed: 悬浮坞、遮罩羽化、`--dock-h` 单点测量、指针穿透、窄窗留白同源，含 15 项浏览器实测与四张截图；另把 `guard-selftest -Fast` 的天花板从 300s 抬到 600s（`.codex/scripts/run_all_guards.py:224`），使 precommit 整档从 `INCONCLUSIVE` 变成有判决。
- In progress: 无；本卡已 `verified`。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 用户补发第二张问题截图（本轮消息里图片未送达），核对是否同属坞区；若同属，另开一卡一并处理，之后再统一重打包 `dist-offline` 与 Windows 便携 exe。
- Watch-outs: `--dock-h` 只有一个生产者，任何地方再写死一个坞高像素值都会让末行留白与羽化带分叉；窄窗那条 `.chat-reading` 覆盖是第一次实测才抓到的，后续加断点要一起检查它。
