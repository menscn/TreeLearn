# Codex Harness Task: Web 阶段三——夹具进包、逐屏渲染与换屏接线、走查仪器与单文件离线产物

id: T-2026-10-18-020
title: Web 阶段三——夹具进包、逐屏渲染与换屏接线、走查仪器与单文件离线产物
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-web-h5-android-parity-requirement-005.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 1576
shared_code_touched: true
depends_on: [T-2026-10-18-web-parity-fixture-capture-instrument-019.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 005 的 Next steps 第二条是「阶段 3 逐屏 1:1」。本片把 019 采到的夹具变成 Web 上真实可点开的 13 屏，并给「1:1 是否成立」一个机械判据；出口是用户那句「手机版和 web 均可以正常使用」里的后半句。
- Problem/background: 骨架（011–015）只有外壳与运行时边界，屏幕内容一处没有。直接照截图长界面会重演旧线病因——所以先立判据再画：每屏的判据是**夹具里每一段可见正文都必须在 H5 上出现**（`missing` 为空），行数与像素只留证不判定。判据立起来的第一轮就抓到两处真缺陷：HomePage 把推荐位的 `rec_tag`（「顺着你学的」）整段丢掉，且 `rec_chip` 在 Android 上是可点的、Web 上只是文字。
- Users/scenarios: 学习者在手机上用到的 13 屏（主页、对话、学习库、分支导航、谱系画布、设置、KEY、服务商、模型、检索、语音、文件、主页抽屉）在浏览器里都要能看到同一批文字、按同一条路径点进去、且不需要起服务器也能打开（`file://` 双击）。
- Core flow and reversibility: 数据流单向——`artifacts/fixtures/parity/` →（`sync_fixtures.mjs` 脱敏字节拷贝）→ `frontend/src/platform/fixtures/` → `bundledFixtures.ts` 静态映射 → `fixtureNodes.ts` 按 id/父链取节点 → 页面层渲染 → `store/screenRoute.ts` 决定换屏 → `ScreenRouter.tsx` 选页。全部新增文件，可整片删除回到 019；对既有生产面只改了 `main.tsx`（标题 + 路由挂载）、`AppShell.tsx`（换屏容器）、`app.css`（新增段）、`copy/zh-CN.ts`（新增键）。
- Inputs/outputs: 输入 = 13 份夹具 JSON（含 `nodes[]` 的 id/class/depth/parent/bounds_dp/clickable/text/desc）；输出 = 13 屏 DOM（每屏节点带 `data-node`、可点面带 `data-tab`/`NodeTap`）+ 单文件产物 `dist-offline/index.html`。
- State and exception handling: 夹具缺屏或某屏读不出任何正文 → 走查仪器判该屏不成立（「夹具读不出任何正文，覆盖判据无从成立」）；URL 形状文本按规则豁免（包内脱敏后的端点/模型地址），豁免不过半，过半要人来判是不是漏画；未知路由回落到主页；`data-screen` 与期望屏不符即算这一步没走到。
- Constraints and trade-offs: 不新增依赖（React/TS/Vite 之外零引入）；Java-only 不受影响；不为凑绿放宽断言——本片把「行数下限」这种自造判据整个删掉换成覆盖判据，因为自造下限会随实现漂移；文案一律走 `src/copy`，夹具正文按 §7 单一来源直接取 Android 读数（不在 Web 侧另立一份文案）。
- AI recommendation and rationale: 三处结构性决定——① 渲染由夹具驱动而不是由人写死 JSX（13 屏里 8 屏用通用 `FixtureListPage`，主页/对话/学习库三屏按 Android 布局手工整形）；② 换屏链接表 `screenLinks.ts` 与 Android 的 `show*` 入口逐条对齐，键是夹具里的 `resource-id`；③ `file://` 的空白页不在产物里打补丁，而是另出一份单文件产物——浏览器对**外部** ES 模块按 CORS 取回，`file://` 的 origin 是 `null`，这是浏览器语义不是打包缺陷。
- User choice and custom additions: 用户要求一站到底、按片提交本地仓库、不中途请示；目标是「手机版和 web 均可以正常使用」。
- AI-autonomous decisions: ① 判据从「行数下限」换成「夹具正文逐条覆盖」，并把被删掉的 4 条自造下限登记为判据变更（见 Verification report）；② 图标按钮先渲染 `content-desc` 文本而不是矢量图形（矢量收编属阶段四），并在卡片里如实记为偏差；③ `favicon.ico` 用 `<link rel="icon" href="data:,">` 声明「无图标」而不是留一条 404；④ 单文件产物走「构建后再导出」而不是换打包模式（不改 `vite build` 的 code-splitting 语义，避免影响 4173 那条已验过的路径）。
- User-confirmation decisions: 无新增（沿用 005 已确认范围与疑虑 8「手机档唯一基线」裁定）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-18-web-render-walk-020.txt` 块 00–08；截图 `T-2026-10-18-web-020-{home,chat,homedrawer}-file.png` 与 Android 侧回归 `T-2026-10-18-device-home-after-020.png`。
- Facts: 三面走查同判据同结果——dev 5173、preview 4173（`dist/`）、`file://`（`dist-offline/index.html`）各 `judgements=29 failed=0 VERDICT: PASS`；24 步全部落在 13 屏夹具的可点名单上（`walked=13/13`）；控制台 0 条错误；`tab-title = "知芽"`；`pnpm --dir frontend test` 11 文件 50 例全绿；`node scripts/verify.mjs` 16 步 `ok=16 bad=0 fault=0 VERIFY PASS`；`inline_offline --selftest` 7 例；变异证明：单文件产物里抹掉「顺着你学的」→ 同一命令 `FAIL step0 home 少了 2/17 段`、`failed=4`、`EXITCODE=1`，重导出后转绿。Android 侧回归（块 08）：`adb install -r` 同一 debug APK 后 `--probe` 仍读回 `nodes=60`（与 019 采夹具时同值），首页截图正常且本机数据未清（历史主题 2 个）——本片的改动面没有漂移到原生界面。
- Assumptions: 夹具是 Android 当前实现的读数（019 已证 `source_commit` 与 HEAD 同源）；浏览器行为以本机 Edge/Chromium 155 为准，双环境第二档（移动 WebView/WebKit）按 SPEC §10 属阶段 9 收口。
- Unknowns: 在线真实发送后的流式回放（需要 `backend/`，阶段七）——本片 Web 侧内容是夹具快照，不是活数据。
- Risks: 覆盖判据只看「文字有没有出现」，看不见截断与行数——Android 用 `maxLines` 省略号收掉的正文，Web 侧目前是整段展开；这条差异由视觉档（`measure_screen.py` + 成对截图）负责，本片不冒充它判过。
- Deferred: 自绘屏/浮层/图标矢量（阶段四）、画布与公式（阶段五）、行为接线与流式回放（阶段六/七）、PC 外壳（阶段八）。
- Non-goals: 不改 Android 任何文件、不接后端、不做平板档差异复刻、不把 `frontend/` 改成 SSR 或多入口。
- Skipped non-critical questions and reason: 未追问「Web 要不要做真实数据接入」——`backend/` 是阶段七且需要单独裁定，本片先把「同面同判据」做实。

## Goal

让 13 屏在浏览器里按 Android 现状真实可用（文字逐条同源、路径逐条可点、双击可开），并把「1:1 是否成立」变成一条会转红的机械判据。

## Scope

### In scope
- `frontend/src/platform/{bundledFixtures.ts,fixtureNodes.ts}` 与 `fixtures/*.json`（019 的权威侧脱敏字节）。
- `frontend/src/pages/`（`HomePage`/`ChatPage`/`LibraryPage`/`FixtureListPage`/`ScreenRouter`）、`store/{screenRoute.ts,screenLinks.ts}`、`hooks/{useRoute.ts,useSnapshot.ts,useFixtureScreen.ts}`、`components/{NodeTap.tsx,Notice.tsx}`。
- `frontend/src/styles/app.css` 新增段、`main.tsx` 标题与路由挂载、`AppShell.tsx` 换屏容器、`copy/zh-CN.ts` 新增键、`index.html` 图标声明、`vite.config.ts` base 相对路径。
- `.codex/scripts/dev/parity/web_walk.mjs`（24 步走查 + 覆盖判据 + 控制台/网络读数 + 6 例自检）、`frontend/scripts/inline_offline.mjs`（单文件导出 + 7 例自检）、`verify.mjs` 加两步、`check_offline_bundle.mjs` 放行 `data:`。

### Out of scope
- 视觉层量测、自绘浮层复刻、图标矢量资源、`backend/`、平板档差异、Android 侧任何文件。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: `frontend/` 既有骨架行为（离线判定、依赖方向、文案守门、设备时间口径）全部保持；`build` 的产物形状不变，只在其后追加一次单文件导出。Android 侧行为零改动。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `capture_screen.py`（019）→ `sync_fixtures.mjs` → `bundledFixtures.ts`（唯一静态映射点）→ `fixtureNodes.ts`（唯一按 id/父链取节点处，页面层不得自己解析 JSON）；消费者 `pages/*`；`screenLinks.ts` 的键与 Android `MainActivity.show*()` 入口逐条对齐，`web_walk.mjs` 的走查表是它的机器复核方。平板档不在此链上（疑虑 8 裁定手机档唯一基线）。
- Native bridge or independent-client compatibility/update requirements: 无桥接；`RuntimePort` 仍是唯一运行时边界，本片只加夹具实现，未新增端口方法。
- Behavior-neutral non-applicability reason, if applicable: 无。

## Documentation impact record

- Changed runtime facts: Web 侧从「只有骨架」变成「13 屏可读可点、可离线双击打开」；`file://` 的真实语义（外部 ES 模块被 CORS 拦）由本片实测确定。
- Authoritative code evidence: `web_walk.mjs` 的覆盖判据段与 `checkWalk()`；`inline_offline.mjs` 的 `STILL_LOADS` 与标签级残留扫描；`fixtureNodes.ts` 的 `firstById/groupsById/pickChild`。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §3（离线口径）、§7（单一来源）、§10（判据分工）、§11（阶段 3 门禁）；`T-2026-10-18-web-user-copy-guard-and-verify-entry-015.md`（其 `file://` 主张被本片实测证伪）；`T-2026-10-18-web-h5-android-parity-requirement-005.md` 第 118 行同一主张；`.codex/rules/frontend.md`。
- Documentation files updated: SPEC「修订记录」追加一条（含对 015/005 那两处 `file://` 读数的更正指针）；本卡；015 卡不改写历史正文，只在 SPEC 修订记录里被点名。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "dist/index.html" .codex/tasks Docs/spec` 找到两处旧主张并逐处登记；`grep -rn "fixtures/parity" frontend/scripts` 确认包内副本只有一个写入者。
- Superseded phrases/examples reverse-searched: 015 卡「`file://` 打开 `frontend/dist/index.html` 得到 RootWebArea「知芽」+ 两枚标签，控制台零消息」——同一 URL 今天用 CDP 复现得到 7 条 FAIL（含两条 CORS 拦截原文），该句已被证伪，处理方式见 SPEC 修订记录与本片 RED/GREEN 之外的块 04。
- `documentation impact: none` reason and evidence, 若适用：不适用。
- Historical/PRD handling: 015/005 的历史正文与旧证据文件**不改写**（它们是当时那次浏览器工具的读数）；只在 SPEC 修订记录里加 dated 更正指针，并给出可复现的新命令。

## Acceptance criteria

- [x] 覆盖判据在缺陷存在时为红、修复后为绿，且是同一条命令（块 06/07：`failed=4 EXITCODE=1` → `judgements=29 failed=0 EXITCODE=0`）。
- [x] 三面同判据同结果：dev 5173、preview 4173、`file://` 单文件各 `VERDICT: PASS`、`console = 0 条错误`（块 02/03/05）。
- [x] 13 屏全被走到、24 步全部落在夹具的可点名单上：`WEB-WALK SELFTEST screens=13 steps=24 … cases=6 bad=0`（块 01）。
- [x] `file://` 双击可开：`dist/` 复现 7 条 FAIL 并点名 CORS（块 04/04b），`dist-offline/index.html` 同判据 PASS（块 05）——修的是产物形状，不是判据。
- [x] 门禁不红：`node scripts/verify.mjs` 16 步 `VERIFY PASS`；`pnpm --dir frontend test` 11 文件 50 例全绿；`:app` JVM 848/0（019 块 03）。
- [x] 单一来源不破：包内夹具字节由 019 的守门判定同源（`SYNC CHECK ok`），Web 侧未新增任何手写正文；文案仍只经 `src/copy`。
- [ ] 视觉层逐屏达绿（行高、截断省略号、图标字形）——属阶段三视觉档与阶段四，本片未做，见 Gaps ①②。

## TDD execution record

### RED — failing test first
- Test/scenario: 覆盖判据必须能抓到「Android 有、Web 没画」的正文。变异在**产物**上做（源码与夹具不动）：把 `dist-offline/index.html` 里 4 处「顺着你学的」字面量清空，其中 2 处属于主页的 `rec_tag`。
- Command: `node .codex/scripts/dev/parity/web_walk.mjs --url file:///D:/Project/Android/Treelearn/frontend/dist-offline/index.html --out artifacts/tmp/walk-mutation`
- Exit code: 1
- Observed failure: `FAIL step0 home 少了 2/17 段 Android 正文（rec_tag:顺着你学的 / rec_tag:顺着你学的）` / `judgements=29 failed=4` / `VERDICT: FAIL` / `EXITCODE=1`
- Failure reason validated: 缺的是夹具点名要的第 0 屏正文，不是断言写错——同一条命令在产物恢复后立刻 `failed=0`（块 07）。开发过程中真实的第一轮红就是这条判据抓到的 `HomePage` 漏画 `rec_tag` 与 `rec_chip` 不可点，随后才有现在的推荐位实现与 `screenLinks.ts` 里的 `rec_chip: "chat"`。

### GREEN — minimum implementation
- Implementation: `HomePage.tsx` 推荐位改为 `groupsById(n,"rec_chip")` + `rec_tag`/`rec_text` 两截（整枚芯片是可点面）；`screenLinks.ts` 加 `rec_chip: "chat"`；`app.css` 加 `.rec-list .node-tap`/`.rec-tag`/`.rec-text`；`main.tsx` 设 `document.title`；`index.html` 声明 `data:` 图标；`inline_offline.mjs` 产出单文件
- Command: `node .codex/scripts/dev/parity/web_walk.mjs --url file:///D:/Project/Android/Treelearn/frontend/dist-offline/index.html --out artifacts/tmp/walk-mutation`
- Exit code: 0
- Passing evidence: `OK step0 home 正文全覆盖 17 段 rows=9 chars=144 taps=6 title=""` / `OK console = 0 条错误` / `judgements=29 failed=0` / `VERDICT: PASS` / `EXITCODE=0`（块 07）
- Regression evidence: 同判据在 dev 5173 与 preview 4173 各 `judgements=29 failed=0`（块 02/03）；`node scripts/verify.mjs` → `verify steps=16 ok=16 bad=0 fault=0 / VERIFY PASS`；`pnpm --dir frontend test` → `Test Files 11 passed (11) / Tests 50 passed (50)`；`inline_offline --selftest` → `SELFTEST cases=7 ok=7 fail=0`

### REFACTOR — optional
- Cleanup: 删掉走查表里的 `rows` 自造下限字段与对应判据（4 条假红由此消失），改为夹具派生覆盖；`.rec-chip` 单一类名拆成 `node-tap + rec-tag + rec-text`，与夹具节点身份一一对应。
- Regression evidence: 清理后同一条走查命令与 `verify` 全绿（块 02/03/05 与 GREEN 段）。

### TDD or sizing exception — only with prior user approval
- Exception type: sizing（无 TDD 例外）
- Reason: 生产行 1576（新增页面/存储/钩子/组件/夹具层 701 + 两支仪器 531 + 既有文件新增 344）远超 500。未逐屏拆片的理由：判据是「13 屏同一套覆盖规则」，按屏拆只会把同一条走查命令跑 13 遍，且不改变任何一次红或绿；仪器与渲染分两张卡也不会减少任何一次改动面。
- User approval: 用户 2026-10-18 指令原文「按照harness执行…过程中不要让我确定，你自行一站到底的完成」——按该 standing 指令连续执行，未就本片规模单独逐项请示；此处如实登记规模与未请示的事实，供事后裁定（若裁定要拆，本片可按「渲染层 / 走查仪器 / 单文件产物」三块重切，提交边界已按此顺序排列）。
- Alternative verification: 判据本身有正反双向自检（`web_walk --selftest` 6 例、`inline_offline --selftest` 7 例），并有产物级变异证明（块 06/07）与三面独立执行（块 02/03/05）。

## Implementation notes

- `estimated_production_lines: 1576` = 新增生产文件行数 + 既有文件净新增；测试另计（`frontend/test/**` 新增 663 行）。
- 覆盖判据为什么按「正文逐条出现」而不是按行数：行数下限是人造阈值，实现改一行就要重设一次，且它看不见「丢了哪一段」；覆盖判据的期望值直接来自夹具，缺哪段就点名哪段的 `id` 与文本。
- `file://` 的三种情形实测过：内联 `<script type="module">` 执行；外部经典脚本执行；外部 ES 模块与 `crossorigin` 样式表被 CORS 拦（origin `null`）。所以单文件导出只需要内联这两类引用，不需要改运行时。
- `inline_offline.mjs` 的残留引用扫描只在**标签面**上做，且只掏空自己写出的那两种包裹——通用 `</script` 规则会把没能内联的外部引用一起掏掉（实测漏判过一条 unpkg），库代码里的 `<img src="…">` 字符串则会被误伤（实测假阳性 `+pn(e)+`）。两条都固化成自检用例。
- `scripts === 0` 判 FAIL：零集合不构成通过。这条与 019 的 `min_nodes` 同源。

## Verification report

- Verdict: PASS（三面走查 + 变异证明 + 门禁 16 步 + 单测 50 例 + 截图人眼复核五层；视觉层未判，见 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-18-web-render-walk-020.txt` 块 00–08，`artifacts/evidence/T-2026-10-18-web-020-{home,chat,homedrawer}-file.png`，Android 侧对照 `artifacts/fixtures/parity/*-android.png`、`T-2026-10-18-device-home-019.png` 与块 08 的装后回归 `T-2026-10-18-device-home-after-020.png`。环境：Windows / node 24.18 / Edge 155（CDP，viewport 369x816@2）/ ALP-AN00。
- Changed/deleted assertion rationale and equivalent behavior coverage: **删除**了走查表的 `rows` 下限判据（4 条假红：home 4/6、library 2/4 等），替换为夹具派生的逐条正文覆盖 + 「每屏都还剩得出正文」+「单屏豁免不过半」三条；被删判据防的是「屏幕空了」，新判据防的是同一件事且能点名缺哪段，故为等价或更强。`check_offline_bundle.mjs` 新增 `data:` 放行分支，同时补一条正控（内联放行）与一条反控（缺协议主机引用仍被点名）。
- Gaps: ① 视觉层未量测——Android 用 `maxLines`+省略号收掉的正文（主页历史卡摘要、推荐位问题句）在 Web 侧整段展开，覆盖判据看不见这种差异；② 图标按钮目前渲染 `content-desc` 文本（「打开菜单」「设置」），发送按钮是纯色圆，矢量图形与字形属阶段四；③ 主页/对话/学习库之外的 10 屏是通用夹具渲染，未按 Android 的卡片整形逐屏手工贴齐（结构同源、形状未校）；④ Web 内容是夹具快照，真实发送需要 `backend/`（阶段七）；⑤ 双环境的第二档（移动 WebView/WebKit）未跑；⑥ provideradmin 屏显示脱敏后的端点占位文本，这是 019 的脱敏规则而非渲染缺陷。
- Risks: 覆盖判据以夹具文本为准，若将来 Android 侧改用 `content-desc` 承载正文（`text` 为空），该判据会退化成只判 `text`——`web_walk.mjs` 的 `fixtureAuthority()` 需要同步把 `desc` 纳入；本片已在 selftest 里留了「每屏都还剩得出正文」这条兜底，退化时会转红而不是静默。

## Handoff notes

- Completed: 13 屏渲染与换屏接线、走查仪器（含 6 例自检与覆盖判据）、单文件离线产物（含 7 例自检）、`verify` 16 步、`file://` 真实语义定案、015 卡旧主张的证伪与登记。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: ① 视觉档：用 `measure_screen.py` 对成对截图逐屏量（先补 `maxLines`/省略号与行高，这是人眼比对里最刺眼的两处）；② 阶段四：自绘屏与浮层、图标矢量资源；③ 阶段六行为接线前，先把 `screenLinks.ts` 与 Android `show*()` 的差分做成守门（目前只有走查表在机器复核）。
- Watch-outs: ① 重采夹具后必须 `node scripts/sync_fixtures.mjs` 再 `pnpm --dir frontend build`，否则走查读的是旧字节（019 的守门会先转红）；② 别把 `dist/index.html` 的 `file://` 空白当成产物坏了——那是浏览器语义，要双击可开就喂 `dist-offline/index.html`；③ 覆盖判据 PASS 不等于视觉层 PASS，报结论时不要把 29/0 说成「1:1 已达成」；④ 卡片里的命令必须与实际执行逐字相同，本片 RED/GREEN 是同一条走查命令，块 04 与块 05 是同一条判据的两种产物形状。
