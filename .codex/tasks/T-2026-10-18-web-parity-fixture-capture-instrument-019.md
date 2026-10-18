# Codex Harness Task: Web 阶段一——Android 真机结构夹具采集仪器与包内副本漂移守门

id: T-2026-10-18-019
title: Web 阶段一——Android 真机结构夹具采集仪器与包内副本漂移守门
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-web-h5-android-parity-requirement-005.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 775
shared_code_touched: false
depends_on: [T-2026-10-18-web-h5-android-parity-requirement-005.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 005 的 Next steps 第一条是「阶段一夹具采集补齐（清单全条目 → 路由表与 `artifacts/fixtures/parity/`，台账 NO-PROFILE 归零或写明可接受偏差）」。在此之前 Web 侧没有任何来自真机的结构读数，1:1 的「1」没有对象。
- Problem/background: 旧 Web 线的病因是「先长界面后补判据」。本片反过来：先把 Android 真机每屏的视图树变成可比对的字节，再让界面去贴它。采集必须满足三件事才可用——**到达**（每一步在当前这次 dump 里按 resource-id 定位，定位不到即 FAIL，不许写死像素坐标）、**归一**（包围盒按实测密度换算成 dp，视口 369.23x816 dp 而不是 1200x2652 px）、**可回读**（信封固定六字段 + `source_commit` 40 位十六进制 + 截图 sha256，节点保留条件与父链规则写死在仪器里）。
- Users/scenarios: 学习者看不到这条片子；它服务的是「Web 上每一屏是不是同一屏」这个判断本身。没有它，阶段三的绿色结论只能靠人眼比截图。
- Core flow and reversibility: 新增两支只读仪器（`capture_screen.py`、`sync_fixtures.mjs`）加一张路由表；不改 Android 生产代码、不改 SQL、不改任何用户可见文案。夹具与包内副本是产物，删掉即可回到本片之前。
- Inputs/outputs: 输入 = 真机（ALP-AN00，`adb` 可达、装的是当前 APK）+ 路由表 13 条；输出 = `artifacts/fixtures/parity/<屏>-android.{json,png}` 13 组、`frontend/src/platform/fixtures/<屏>-android.json` 13 份（脱敏后的字节拷贝）。
- State and exception handling: 动画缩放强制为 0 并在退出时复原（含异常路径），否则包围盒读数不可重复；`expect_root` 缺失判 FAIL（自绘屏没有根布局 id，改为点名它自己画出的控件 id 或留空由 `min_nodes` 兜）；`min_nodes` 是裸字面量下限，采到一小撮壳节点必须判 FAIL，不许留一份看起来完整的空夹具。
- Constraints and trade-offs: Python 只用标准库；Node 侧只用 `node:fs`/`node:path`；不引入 uiautomator2 之类依赖。脱敏只针对 URL 形状文本（`redacted=1` 落在 provideradmin 的端点上），其余字节原样照搬——「改写正文以求一致」正是本片要防的事。
- AI recommendation and rationale: 台账的两列（视觉层/行为层）由 `screens.json` 原型档驱动，量的是「原型量测记录/回放规格在不在」，与「真机结构夹具在不在」不是同一种证据。把夹具塞进那两列会让 `MEASURED` 一词同时承担两件事，属本仓库「同一约束两个入口」那族缺陷，因此走 005 允许的第二条路：写明可接受偏差（见 Gaps ①）。
- User choice and custom additions: 用户要求一站到底、按片提交本地仓库、不中途请示。
- AI-autonomous decisions: ① 路由表键名沿用台账的 `profile_key` 推导（类名去掉 `Screen` 后缀转小写），让 `gen_parity_ledger.py` 以后按同一个键找到条目；② `insets_dp` 从本次实测节点现场算（最靠上节点上沿 / 视口高减最靠下节点下沿），不读系统 API——代价是有全屏节点时 `status_bar_dp` 读作 0.0，这个形态在信封里可见而不是藏起来；③ 包内副本用「权威侧脱敏后逐字节比对」判漂移，不用语义 diff——语义 diff 会把「Web 在读旧基线」漏成「格式不同」。
- User-confirmation decisions: 无新增（沿用 005 已确认范围）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-18-web-fixture-capture-019-instrument.txt` 块 01–08b。
- Facts: `--all` 实跑 13 屏 `CAPTURE_TOTAL=13 BAD=0`，节点数 10–79，`EXITCODE=0`，动画缩放三项复原打印在末行；`--selftest` 11 条判据 `SECTIONS=11 PASS=11 FAIL=0`；`sync_fixtures.mjs --check` 在变异注入下 `bad=1 / SYNC CHECK FAIL / EXITCODE=1`（shell rc 实测 1，块 07 用不带管道的命令重跑），恢复后 `bad=0`；`sync_fixtures --selftest` 8 例全绿；重采后真实失配 13 条 DRIFT 全部由写入路径收敛（`redacted=1`）。
- Assumptions: 设备上的 APK 与当前树同源（块 08 用 `adb install -r` + `lastUpdateTime` 现场证明，不假设）。
- Unknowns: `StatsDetailScreen` 的到达路径未采（需要真实学习统计入口）；浮层/自绘弹层未采（阶段四）。
- Risks: 相对日期（「3天前」）由 Android 现场渲染，每次重采必然让包内副本漂移一次——这是守门按设计工作，但会让「重采 → 同步 → 重跑门禁」成为固定三步，忘记第三步就会被 `SYNC CHECK FAIL` 挡住。
- Deferred: 台账 NO-PROFILE 两列的接线（需先裁定「夹具」与「原型量测记录」是否同一种证据）；浮层与 `statsdetail` 采集。
- Non-goals: 不做像素量测（`measure_screen.py` 已有归属）、不改 Android 代码、不在 Web 侧造第二份文案。
- Skipped non-critical questions and reason: 未追问是否把 `min_nodes` 做成可配置，因为它的作用就是「不许由被检集合自己决定下限」。

## Goal

让 Android 真机 13 屏的结构与截图变成可比对、可回读、可判漂移的夹具，并把「Web 在读哪一份基线」变成一条会转红的门禁。

## Scope

### In scope
- 新增 `.codex/scripts/dev/parity/capture_screen.py`（到达—归一—可回读三段判据、四档退出码、`--all/--screen/--probe/--selftest`）与 `android_routes.json`（13 屏路由、`presets` 归一起点、`expect_root`/`min_nodes`）。
- 新增 `frontend/scripts/sync_fixtures.mjs`（权威→包内字节拷贝 + URL 脱敏 + `--check` 漂移守门 + 8 例自检）。
- 重采 13 组夹具与截图；重生成 `Docs/04-ux-design/parity/screen-inventory.json` 与 `artifacts/parity/ledger.md`（承 016/018 两片 Android 改动后的行号与哈希）。

### Out of scope
- Web 页面渲染与换屏接线（020）、像素量测、浮层与自绘弹层采集、Android 生产代码。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 不改任何运行时用户可见行为；新增的是证据面。`screen-inventory.json` 的行号位移与 `ledger.md` 的哈希更新是重导出结果，不是判据变化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `capture_screen.py`（唯一写 `artifacts/fixtures/parity/` 的地方）；消费者 `frontend/scripts/sync_fixtures.mjs` 与 020 的 `src/platform/bundledFixtures.ts`；`gen_parity_ledger.py` 的 `profile_key` 与路由表键名同源（`android_routes.json` 的 `_comment` 第 5 条写明该推导）。Android 侧无消费者。
- Native bridge or independent-client compatibility/update requirements: 无桥接改动。
- Behavior-neutral non-applicability reason, if applicable: 无。

## Documentation impact record

- Changed runtime facts: Web 侧「基线来自哪里」这一事实由「人眼比截图」变成「真机夹具 + 包内副本同源判据」。
- Authoritative code/config/migration evidence: `.codex/scripts/dev/parity/capture_screen.py` 的信封字段与 `parse_nodes` 保留条件；`android_routes.json` 的 `_comment`（坐标不写死、`optional` 只用于归一起点、`expect_root` 是到达判据、`min_nodes` 是裸下限）。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §10/§11（阶段一门禁原文「台账覆盖全部屏且数字可回读」）、`T-2026-10-18-web-h5-android-parity-requirement-005.md` Next steps ①、`artifacts/parity/ledger.md` 表头与两列语义、`.codex/rules/multi-device.md`（手机档基线口径，承疑虑 8 裁定）。
- Documentation files updated: 本卡 + SPEC 修订记录一条（020 卡同点登记）；`ledger.md`/`screen-inventory.json` 由生成命令重生成而非手改。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "fixtures/parity" .codex/scripts frontend/scripts` 只有本片的采集器与同步器；`screens.json`（原型档）与新路由表键名交叉核对过，两表并存且各司一职，未合并——合并会让「原型量测」与「真机夹具」两种证据混成一份。
- Superseded phrases/examples reverse-searched: 005 卡里「阶段一夹具采集补齐」的完成判据（NO-PROFILE 归零）在本片按第二条走法收窄，已在 Gaps ① 写明理由，未改 005 历史正文。
- `documentation impact: none` reason and evidence, 若适用：不适用。
- Historical/PRD handling: 历史任务与 PRD 的旧读数不改写；`ledger.md` 表头那句「MEASURED 只表示 Android 侧基线记录在位」保持原义，本片没有让任何一格从红变绿。

## Acceptance criteria

- [x] `--all` 实采 13 屏且逐屏通过到达/下限判据：`CAPTURE_TOTAL=13 BAD=0`、`EXITCODE=0`、动画缩放三项复原（块 01）。
- [x] 仪器自身 11 条判据全绿，含「定位不到即 FAIL（不猜坐标）」「空层级由 `min_nodes` 当场判 FAIL」「screen 与文件名不一致被抓」「`source_commit` 非 40 位十六进制被抓」「空 nodes 判 FAIL」（块 02）。
- [x] 信封字段与 dp 归一可回读：13 组夹具的 `schema_version/screen/source_commit/captured_at_ms/device/payload` 齐全，视口一致为 `[369.23, 816.0]`，`shot_sha256` 逐屏不同（块 04）。
- [x] 包内副本与权威夹具同源：`SYNC WRITE src=13 dst=13 bad=0 redacted=1`，随后 `SYNC CHECK ok`；漂移守门在变异注入下 `bad=1 / EXITCODE=1`（块 05–07）。
- [x] 门禁不红：`./gradlew :app:testDebugUnitTest` 结果 XML 现场计数 94 文件 / 848 tests / 0 failures / 0 errors（块 03）；设备上是当前 APK，`adb install -r` Success（块 08/08b）。
- [ ] 台账 NO-PROFILE 两列归零——按 005 允许的第二条走法处理为「写明可接受偏差」，理由见 Gaps ①。

## TDD execution record

### RED — failing test first
- Test/scenario: 漂移守门必须在「包内副本与权威夹具不同源」时转红。变异注入：把 `frontend/src/platform/fixtures/home-android.json` 的 `node_count` 由 60 改成 61，权威侧不动。
- Command: `cd frontend && node scripts/sync_fixtures.mjs --check`
- Exit code: 1
- Observed failure: `DRIFT home-android.json 包内副本与权威侧（脱敏后）字节不同` / `SYNC CHECK src=13 dst=13 bad=1 redacted=0 dir=frontend\src\platform\fixtures` / `SYNC CHECK FAIL` / `EXITCODE=1`（块 06 经 tee 管道，shell rc 被吞；块 07 用不带管道的同一条命令重跑，实测 `shell rc=1`）
- Failure reason validated: 失配发生在脱敏之后，说明差异不是「URL 被改写了」而是 Web 确实在读一份与真机不同源的基线——正是这条门禁要拦的状态。同一条命令在真实重采后同样报 13 条 DRIFT（块 05），不是只为造红而做的变异。

### GREEN — minimum implementation
- Implementation: `capture_screen.py` 的到达—归一—可回读三段与 11 条自检；`android_routes.json` 13 条路由；`sync_fixtures.mjs` 的脱敏字节拷贝 + `--check` 逐字节比对 + 8 条自检；重采 13 屏并写入包内副本
- Command: `cd frontend && node scripts/sync_fixtures.mjs --check`
- Exit code: 0
- Passing evidence: `SYNC CHECK src=13 dst=13 bad=0 redacted=0` / `SYNC CHECK ok` / `EXITCODE=0`（块 07 末段）；`node scripts/sync_fixtures.mjs --selftest` → `SELFTEST cases=8 bad=0 floor=1`
- Regression evidence: `python -X utf8 .codex/scripts/dev/parity/capture_screen.py --all` → `CAPTURE_TOTAL=13 BAD=0`、`EXITCODE=0`（块 01）；`--selftest` → `SECTIONS=11 PASS=11 FAIL=0`（块 02）；`:app` JVM 848/0（块 03）；`python -X utf8 .codex/scripts/dev/gen_parity_ledger.py` 重生成后台账条目数与状态合计仍自洽（`ledger.md` 汇总段）

### REFACTOR — optional
- Cleanup: 无额外清理。路由表里的 `presets`（`ensure_home`/`into_chat`/`into_settings`）在写第一版时就把重复的归一起点抽了出来，避免 13 条各抄一遍。
- Regression evidence: 同 GREEN 的 `--all` 与 `--selftest`。

### TDD or sizing exception — only with prior user approval
- Exception type: sizing（无 TDD 例外）
- Reason: 生产行 775（`capture_screen.py` 473 + `android_routes.json` 121 + `sync_fixtures.mjs` 181）超过 500 硬线。未按屏拆片的理由是：逐屏卡片不会新增任何判据——判据是「13 屏都通过同一套到达/下限规则」，拆成 13 张只会把同一条命令跑 13 遍。
- User approval: 用户 2026-10-18 指令原文「按照harness执行…过程中不要让我确定，你自行一站到底的完成」——按该 standing 指令连续执行，未就本片规模单独逐项请示；此处如实登记规模与未请示的事实，供事后裁定。
- Alternative verification: 仪器自带正反双向自检（11 例 / 8 例），且真实重采与变异注入两条路径的读数都留在证据文件里（块 01/02/06/07）。

## Implementation notes

- `estimated_production_lines: 775` 取三支新文件的行数（含路由表 JSON）；夹具与截图是产物不计入。
- 为什么不写死坐标：`tap()` 每次在**当前这次** dump 里按 id 找节点再点它的中心。写死坐标等于把一次读数冒充成判据——设备一转、字体一缩放就全错，而且错得很安静。
- `optional=true` 只允许出现在「把起点归一」的步上（force-stop 后 App 恢复的是上次离开那屏），其余步找不到即 FAIL。
- 脱敏只做 URL 形状文本替换，替换后的字节同时是 `--check` 的比对基准；这条链上没有任何一处允许「顺手改文案」。

## Verification report

- Verdict: PASS（实采 13/13 + 仪器自检 11 例 + 守门自检 8 例 + 变异注入转红 + 恢复转绿 + JVM 848/0 + 设备当前 APK 六层证据）
- Evidence: `artifacts/evidence/T-2026-10-18-web-fixture-capture-019-instrument.txt` 块 01–08b。环境：Windows / Python 标准库 / node 24.18 / 真机 AWSS013807000356（ALP-AN00 1200x2652@520dpi）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片未删除任何既有判据；`ledger.md` 与 `screen-inventory.json` 的变化全部由生成命令重导出（行号位移来自 016/018 两片 Android 改动）。
- Gaps: ① **台账 NO-PROFILE 两列未归零，按 005 允许的第二条走法登记为可接受偏差**——那两列量的是 `screens.json` 原型档的「量测记录/回放规格」在不在，真机结构夹具是另一种证据，填进去会让 `MEASURED` 一词承担两件事；② `StatsDetailScreen` 与全部浮层未采（前者缺可达路径，后者属阶段四）；③ `status_bar_dp` 在有全屏节点时读作 0.0（派生量的定义如此），需要系统栏真实高度的比对要在阶段三视觉档里用另一支仪器取。
- Risks: 每次重采必然触发 13 条 DRIFT（相对日期），流程上必须紧跟 `sync_fixtures.mjs` + 重跑 `verify`；否则 Web 读到的是旧基线而门禁会转红——这条风险的表现形式是可判定的，不是静默的。

## Handoff notes

- Completed: 阶段一采集仪器 + 路由表 + 13 组夹具落盘 + 包内副本同源守门（含变异证明）+ 清单/台账重生成 + 设备前提证据。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: ① 020 卡（夹具进包、逐屏渲染、换屏接线、走查仪器与单文件产物）；② 需要人裁定「夹具是否算作台账视觉层证据」时再动 `gen_parity_ledger.py` 与它的 598 例自检；③ 阶段四补浮层采集（路由表加条目即可，仪器不需改）。
- Watch-outs: ① 改 Android 界面后必须重采夹具，否则 1:1 的绿色结论指向一份旧视图树；② 设备验证只用 `adb install -r`——全量仪器套件会卸载应用并清掉学习者本地数据与 KEY；③ 卡片里的命令必须与实际执行逐字相同，块 06 那条经 tee 管道吞了 shell rc，所以另跑一条不带管道的（块 07）而不是事后改数字。
