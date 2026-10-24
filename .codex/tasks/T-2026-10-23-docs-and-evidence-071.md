# Filename: T-2026-10-23-docs-and-evidence-071.md
# Codex Harness Task: 人设与森林的文档回写与两端证据

id: T-2026-10-23-071
title: 人设与森林文档回写（PRD BASE-14、README、parity 说明）与两端证据归档
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-10-21-ai-persona-and-forest-requirement-038.md
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-10-23-web-forest-070.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary：父卡 038 的 Acceptance evidence 要求「PRD BASE-14 回写、`android/README.md`/Web parity 文档、两端截图证据、全量守门」；本片是 064–070 七片实现之后的收口片。
- Problem/background: 人设与森林落地后，PRD 的 BASE-14 仍写着旧口径（森林/人设不在本版本），`android/README.md` 与 Web parity 说明也没有这两个新面；证据散落在各片，缺一份索引。
- Users/scenarios: 后续会话或 review 者读 PRD/README 时不应再读到「森林尚未实现」「抽屉没有森林入口」这类过期现状。
- Expected outcome: PRD BASE-14 按实际落地回写（人设七档与主题级作用域、森林只读回顾面与归档口径）；`android/README.md` 补森林页与人设入口说明；Web parity 说明补 `persona-intro` 与 `forest` 两个视图及「只读 parity、不伪造统计」的口径；`artifacts/evidence/` 下两端截图与日志有索引可读。
- Core flow and reversibility: 只改文档与证据索引；回写以生产代码实测为准，逐条给路径引用；改完跑 `check_pointer_liveness.py`、`check_source_of_truth.py`、`validate_tasks.py`、`metrics_report.py` 与 `run_all_guards.py` 全量。
- Inputs/outputs: 输入是 064–070 的实现与证据；输出是回写后的 PRD/README/parity 说明与证据索引。
- State and exception handling: 文档与代码冲突时以代码实测为准并修正文档；发现过期短语做反向检索清零。
- Constraints and trade-offs: 不新增产品口径；同一约束只保留一份权威正文；UTF-8 无乱码；不自动提交。
- AI recommendation and rationale: 收口片不碰生产代码，避免与实现片互相污染验收面。
- User choice and custom additions: 无新增。
- AI-autonomous decisions: 证据索引以各片 evidence 目录为单元列一行说明，不复制截图内容。
- User-confirmation decisions: 无。
- Acceptance evidence: 反向检索「森林尚未实现」「抽屉没有森林入口」「Web 没有人设」零命中；四支守门脚本与全量 guard 退出码 0；`validate_tasks.py` errors=0。
- Facts: PRD 增量正文在 `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md`；Web parity 规格在 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`；证据目录 `artifacts/evidence/`。
- Assumptions: 064–070 全部 verified 后才开始本片。
- Unknowns: 无。
- Risks: 回写时若复制规格原文会造成第二份权威正文；只写结论与指针。

## Goal

把 064–070 的落地事实回写进 PRD、README 与 parity 说明，并给两端证据一份索引，使文档不再读到过期现状。

## Scope

### In scope
- PRD BASE-14 回写：人设七档、主题级作用域、未知档回落、森林只读回顾面、归档子树口径、Web parity 口径。
- `android/README.md`：抽屉森林入口、森林页四态、人设 chip/弹窗/介绍页与设置入口。
- Web parity 说明：`persona-intro`、`forest` 视图、会话内人设状态、诚实页脚口径。
- 证据索引：`artifacts/evidence/` 下 persona-066/067、forest-068、web persona/forest 目录与日志的一行式索引。
- 全量守门与 metrics 复跑。

### Out of scope
- 生产代码改动；新规格；新任务卡。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 仅文档与证据索引；生产行为零变化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 不适用——纯文档片，引用面为 PRD/README/parity 说明中被反向检索的过期短语。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 本片无运行时行为。

## Documentation impact record

- Changed runtime facts: 无（本片只回写文档）。被回写进去的落地事实来自 064–070，本片不新增任何一条运行时行为。
- Authoritative code/config/migration evidence: 064–070 各卡 Verification report 与 `artifacts/evidence/` 目录；正文引用的代码位置逐条以当前工作树实测（`Persona.java`、`TreeLearnSchema.java` V5→V6 迁移、`TopicDao.java:44/:52/:68`、`ChatWiring.java:543`、`SqliteChatStore.java:106/:115/:248`、`ForestLayout.java`、`ForestViewState.java`、`drawer_forest_entry.xml`、`frontend/src/store/{persona,forestLayout,workspace}.ts`、`frontend/src/pages/{ForestPage,PersonaIntroPage}.tsx`）。
- Documentation surfaces checked: `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md`（BASE-14 正文在此，卡片 Facts 原写的 V3.1 增量是 BASE-21~26，不是本条落点）、`Docs/01-demand/..._V3.1增量.md`、`Docs/01-demand/..._V3.3增量.md`（借用其「事实源」记录式写法）、`android/README.md`、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`、`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`、`Docs/spec/SPEC-2026-10-21-my-forest.md`、`Docs/spec/SPEC-2026-09-27-home-drawer-ux.md`、`.codex/context/source-of-truth.md`、`.codex/ROADMAP.md`、`Docs/03-prd-draft/V3.2-...PRD.md`（历史草稿，只检索不重写）。
- Documentation files updated: PRD V3.0（BASE-14 注记 + 版本修改记录一行）、`android/README.md`（人设/森林两节）、parity 规格（2026-10-23 条目）、人设规格（5 条现状回写）、抽屉规格（修订节 1 条）、`.codex/ROADMAP.md`（2 处时效）、`artifacts/evidence/persona-forest-2026-10-23-index.md`（新建索引）。
- Producer/consumer/sibling/compatibility paths checked: 文档引用路径逐条过 `check_pointer_liveness.py`；本片把 `android/README.md` 第一次送进被判定面（`holders` 5→6、`citations` 111→121），短写路径改成仓库根起算全路径后才计入。
- Superseded phrases/examples reverse-searched: 「森林尚未实现」「抽屉没有森林入口」「Web 没有人设」「归档分支不出现在任何回溯视图」，另加自拟的「没有人设 / 森林未实现 / 尚未落地」同族写法；全仓 `.md/.java/.ts/.tsx/.xml` 检索**零命中**（唯一命中是任务卡自己把这些短语登记为待清零目标，不是现状断言）。
- `documentation impact: none` reason and evidence, 若适用: 不适用。
- Historical/PRD handling: 旧口径保留冲突证据时以「已回写」注记说明，不删除历史段落。

## Acceptance criteria

- [x] PRD BASE-14 与相关段落按 064–070 实际落地回写，含 Web parity 口径。（`Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md:68` 的「已回写」子条目 + §十一 版本修改记录追加一行；权威指回 `Docs/spec/SPEC-2026-10-21-my-forest.md`，历史正文零删除）
- [x] `android/README.md` 与 Web parity 说明补全人设与森林两面，路径引用全部存活。（README 两节新增 10 条带行号的可判定指针，parity 规格追加 2026-10-23 条目，另补 `SPEC-2026-09-27-home-drawer-ux.md` 抽屉槽位时效一条与人设规格 5 条现状回写；`check_pointer_liveness.py` 收尾读数见下一条与 Verification report）
- [x] 反向检索过期短语零命中；证据索引覆盖 persona-066/067、forest-068 与 Web 两片证据。（四个短语 + 同族写法在全仓 `.md/.java/.ts/.tsx/.xml` 零命中；`artifacts/evidence/persona-forest-2026-10-23-index.md` 逐单元一行，含 064 无截图单元与 `figwright-forest/` 非验收证据两条边界说明）
- [x] `validate_tasks.py` errors=0；`check_pointer_liveness.py`、`check_source_of_truth.py`、`metrics_report.py`、`run_all_guards.py` 退出码 0。（收尾整跑读数：`validate_tasks` `files=72 errors=0`、`check_pointer_liveness` `holders=7 citations=129` 四项判红计数全 0、`check_source_of_truth` `scanned_files=68 entries=18`、`metrics_report` 0 项失败、`run_all_guards` `pass=39 fail=0 inconclusive=0 fault=0 timeout=0` 退出码 0；逐条原文见 Verification report，precommit 档 6 条由父卡 038 整跑）

## TDD execution record

### RED — failing test first
- Test/scenario: not-applicable（纯文档片，无测试先行判据）
- Command: not-applicable
- Exit code: not-applicable
- Observed failure: not-applicable
- Failure reason validated: not-applicable

### GREEN — minimum implementation
- Implementation: PRD V3.0 BASE-14「已回写」注记 + 版本修改记录一行；`android/README.md` 新增「AI 内置人设（T-064 / T-066 / T-067）」与「我的森林（T-068）」两节；`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` 追加 2026-10-23 修订条目；`Docs/spec/SPEC-2026-09-27-home-drawer-ux.md` 追加抽屉森林槽位条目；`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 五条「现状」指针与计数按落地实测回写；`artifacts/evidence/persona-forest-2026-10-23-index.md` 证据索引；`.codex/ROADMAP.md` 两处时效注记。
- Command: python -X utf8 .codex/scripts/check_pointer_liveness.py
- Exit code: 0
- Passing evidence: 修复前该命令**判红**（`landing_blank=['...DeepSeekRequest.java:42', '...TreeLearnSchema.java:874', '...TopicDao.java:42']`、`check_pointer_liveness: 1 check(s) failed`、退出码 1），修复后 `citations=121 landing_blank=0`、`0 check(s) failed`、退出码 0；`check_source_of_truth.py` 退出码 0（`entries=18 unattributed=0 generated_prose=0`）；`validate_tasks.py` `SUMMARY errors=0`。
- Regression evidence: 全量守门 `run_all_guards.py` 读数与 `metrics_report.py --write` 结果见 Verification report（本片零生产代码改动，Android/前端测试面不应有任何变化）。

### REFACTOR — optional
- Cleanup: 没有独立重构轮。本片两次就地修正已记在 Implementation notes：① 模块内短写引用改成仓库根起算全路径（否则 `CITATION_RE` 根本不计入判据）；② 两条无实测支撑的断言换成核实过的事实（Web 窄屏那条改成 Android `touch_min` 44dp + 布局用例，人设节标题的 `T-064 → T-067` 改成 `T-064 / T-066 / T-067`）。
- Regression evidence: 每次修正后复跑 `check_pointer_liveness.py` 与 `check_source_of_truth.py`，各 `0 check(s) failed`、退出码 0；最终读数见 Verification report。

### LOOP — optional
- Command and rounds: 不适用——本片无「反复跑到绿」的循环，两次判红各由一次定向修正消除（见 REFACTOR）。
- Failure identity and repeat count: `check_pointer_liveness` 一条 `landing_blank` 红，重复 1 次（修后转绿）；其余为撰写期自我核对，不产生命令级红。
- Stop condition hit: 四支文档守门与 core 档整跑全绿即停。
- Handoff: 见下方 Handoff notes。

### TDD or sizing exception — only with prior user approval
- Exception type: tdd-not-applicable（纯文档与证据索引片，无生产代码与测试判据）
- Reason: 无运行时行为，TDD 的红/绿判据不存在；验收由反向检索与守门脚本承载。
- User approval: 父卡 038 已登记本片为收口片。
- Alternative verification: 反向检索 + `check_pointer_liveness.py` + 全量 guard。

## Implementation notes

- **BASE-14 的权威落点不在本片最初登记的那份文件**：卡片 Facts 写「PRD 增量正文在 `..._V3.1增量.md`」，实测 BASE-14 正文在
  `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md:67`（V3.1 是 BASE-21~26、V3.3 是 BASE-27~29）。
  回写因此落在 V3.0：Must 列表里 BASE-14 之下加一条 6 空格缩进的「已回写（2026-10-23）」子条目 + §十一 版本修改记录追加一行，
  **不删、不改写任何历史正文**，收窄范围的权威指向 `Docs/spec/SPEC-2026-10-21-my-forest.md`。
- **V3.0 是冻结基线，所以注记里同时写明「不新增边界」**：§九 确认语记录了 2026-09-20 边界冻结，任何看起来像新口径的句子
  都会被读成边界变更；本子条目只说「隐藏范围 = 常规导航/默认画布/列表，森林是只读回顾面」，并声明森林中出现归档分支不违反本条。
- **`android/README.md` 在 SoT 扫描面上**，所以两节只写落地位置与不变量、把口径权威指回两份规格，
  不复述规格正文；`check_source_of_truth.py` 的 `no fact is defined a second time without an attribution pointer` 判绿即这条约束的机器面。
- **简写引用等于没有引用（本片第二次撞上）**：README 与 parity 条目初稿写的是 `wiring/ChatWiring.java:543`、`src/store/forestLayout.ts`
  这类模块内短写，`check_pointer_liveness.py` 的 `CITATION_RE` 只认 `Docs`/`.codex`/`android`/`frontend`/`artifacts`/`README` 起算的路径，
  于是这些指针**根本不被计入判据**——改错了没有任何命令会响。改成仓库根起算全路径后 `holders` 由 5 涨到 7（android/README.md 与抽屉规格第一次进入被判定面）、
  `citations` 由 111 涨到 129（收尾读数）。
- **片 064–067 落地把 `SPEC-2026-09-21-ai-persona-preference.md` 的「现状」指针集体弄歪**：`DeepSeekRequest.java:42`（现 `:44`）、
  `TreeLearnSchema.java:874`（迁移注册表现 `:905`，共 5 步、`DATABASE_VERSION = 6`）、`TopicDao.java:42`（整行写回现 `:44`，
  且主题行写入口已由 1 处变 2 处）、`topics` 4 列→5 列、`answer_revisions` 9 列→10 列 / `generation_operations` 14 列→15 列。
  处置方式是**就地更新为落地实测值并写出写作时读数**（「4 列 / 9 列 / 一处」是当时的真话，不能静默覆盖），
  这与本片「只改文档」的范围不冲突：这些正是会被当成现状读的正文。
- **抽屉槽位单独补一条时效**：`SPEC-2026-09-27-home-drawer-ux.md` 那句「热力图、历史摘要与设置入口」会被读成抽屉只有这三槽，
  而 068 在热力图之后插了只读森林入口。该规格原本没有修订节，追加 `## 修订记录（追加，不回改上文原判定）` 一节并只写这一条。
- **证据索引按「一个单元一行」写**：只说该单元里真有什么（几张截图、哪份日志、探针可否复跑），不复制读数内容；
  064 没有截图单元这一事实也写进索引，否则读者会以为设备上有过 V5→V6 的迁移证据。`figwright-forest/` 明确标为**设计探索、不作验收证据**。
- **`.codex/ROADMAP.md` 只在两处加时效**：`frontend/` 已落地那行补 2026-10-23 的两个新视图与片号；第一份规格那条「等人 review 的口径」
  下面追加一条注记，声明未知档回落、主题级作用域、切换不刷新活动时间三条已拍板并落地，同时声明 ROADMAP 不是产品口径的权威落点。

## Verification report

- Verdict: PASS
- Evidence:
  - **PRD**：`Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md:68` 的 BASE-14「已回写」子条目 + §十一 版本修改记录追加一行；
    历史正文与 §十 决策表逐字未改（回写只加注记与指针，权威落 `Docs/spec/SPEC-2026-10-21-my-forest.md`）。
  - **反向检索**：「森林尚未实现」「抽屉没有森林入口」「Web 没有人设」「归档分支不出现在任何回溯视图」以及同族写法
    （`没有人设|没有森林|森林.*未实现|森林.*尚未|人设.*尚未`）在全仓 `.md/.java/.ts/.tsx/.xml` **零命中**；
    唯一的命中族是任务卡把这些短语登记为待清零目标的那几行，属记录而非现状断言。
  - **`android/README.md`**：两节共 10 条带行号指针（`Persona.java:17`、`TreeLearnSchema.java:1075`、`TopicDao.java:44/:68`、
    `PersonaPrompts.java:30`、`ChatWiring.java:543`、`PersonaPicker.java:31`、`MainActivity.java:522/:883`、`HomeDrawerScreen.java:87`），
    逐条经 `check_pointer_liveness.py` 判定；写路径唯一性另经 `app/src/main` 全量检索复核（`setPersona`/`updatePersona` 只有
    `ChatPresenter.java:931 → ChatWiring.java:543 → SqliteChatStore.java:106/:115 → TopicDao.java:68` 一条链）。
  - **规格面**：parity 规格追加 2026-10-23 条目（含三条 parity 边界：Web 不持久化人设、回落提示归 Android、
    几何判据是同常量同算法而非同 id 同坐标）；抽屉规格追加修订节 1 条；人设规格 5 条「现状」按落地实测回写并保留写作时读数。
  - **证据索引**：`artifacts/evidence/persona-forest-2026-10-23-index.md`，逐单元一行，覆盖 persona-066（6 张）、persona-067（9 张）、
    `forest-068/`（5 图 + 3 份命令/读数原文）、`web-persona-069/`（8 图 + `probe.mjs` + `readings.json`）、`web-forest-070/`（6 图 + 同两件），
    并显式登记 064 无截图单元、`figwright-forest/` 属设计探索不作验收证据。
  - **守门**（文档定稿、状态翻转与 metrics 重生成之后的收尾复跑，均在仓库根执行）：
    `python -X utf8 .codex/scripts/validate_tasks.py` → `SUMMARY files=72 errors=0 warnings=25`、退出码 0；
    `python -X utf8 .codex/scripts/check_pointer_liveness.py` → `holders=7 citations=129 judged_dead=0 judged_beyond=0 judged_reversed=0 judged_blank=0`、`0 check(s) failed`、退出码 0；
    `python -X utf8 .codex/scripts/check_source_of_truth.py` → `scanned_files=68 entries=18`、`0 check(s) failed`、退出码 0；
    `python -X utf8 .codex/scripts/metrics_report.py` → `check_metrics_report: 0 check(s) failed`、退出码 0（报告由 `--write` 重生成，`lines=20 metrics=12`）；
    `python -X utf8 .codex/scripts/run_all_guards.py` → `RUN-ALL-GUARDS pass=39 fail=0 inconclusive=0 fault=0 timeout=0`、
    `VERDICT: PASS (subset only — 39/45 entry(ies) judged, not the whole roster)`、退出码 0。
    **那句 subset 是本片的边界，不是绿的一半**：默认档只整跑 core，未选中的 6 条属 precommit（依赖 Gradle/构建产物/未提交增量），
    本批由父卡 038 另跑 `--tier precommit` 取数，结果为 `pass=5 fail=1`——五条绿含 `no-kotlin-check` 与 `frontend verify`，
    唯一一条红是 `line-budget`（`业务变更行: 3637 > 500`），量的是整个混合工作树、属提交形状而非本片形状；读数与归因逐条记在父卡 038 的 Verification report。
    本片自己不需要那条判定面（零生产代码改动）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片不删任何正文，因此没有断言删除。两处**改写**需要说明：
  ① 卡片 Facts 原写「PRD 增量正文在 `..._V3.1增量.md`」，实测 BASE-14 在 V3.0 基线，故回写落点改到 V3.0——等价覆盖由「BASE-14 正文仍在原处、只多一条注记」承担；
  ② 人设规格那 5 条「现状」的行号/列数被就地更新为落地值，写作时读数同句保留，读者仍看得出改造前后的形状；
  卡片验收条目 6「loading 与全局读取失败」的措辞在片 070 已按实测改写，本片不重复处理。
- Gaps: ① Android 真机上的 V5→V6 迁移执行仍只有 JVM 契约测试背书（既有拍板：全量仪器测试会卸载应用并清本地数据，设备面用 `adb install -r` + 截图）；
  ② 证据索引里的 Web 两份 `readings.json` 是一次性 CDP 探针产物，重跑需 dev/preview 服务在线，索引已写明探针文件位置但不保证可复现的端口状态。
- Risks: ① `android/README.md` 进入被判定面后，任何模块文件行号变动都会让 `check_pointer_liveness.py` 出声——这是本片想要的效果，但后续实现片若只改代码不看 README 会新增一条红；
  ② PRD V3.0 仍写着「iOS、Web 和桌面端：用户明确首版仅 Android」的 Won't 条目——那是冻结基线，与本仓现有 Web parity 面的差异已由 parity 规格疑虑 1 显式登记，本片不再改写基线边界。

## Handoff notes

- Completed: PRD BASE-14 回写（V3.0 注记 + 版本记录行）、`android/README.md` 人设/森林两节、parity 规格 2026-10-23 条目、抽屉规格槽位时效、人设规格 5 条现状实测回写、`artifacts/evidence/persona-forest-2026-10-23-index.md` 证据索引、`.codex/ROADMAP.md` 两处时效；四支文档守门与全量守门复跑取数。
- In progress: 无。064–071 七片全部结案，父卡 038 已按本片与 064–070 的读数收口为 verified。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 父卡 038 按 064–071 的产物与守门读数收口；② 下一批候选看 `.codex/ROADMAP.md` 的 Phase B/C，产品面待办里仍未闭合的是 Android 真机上 V5→V6 的执行证据（需要一次允许卸载的窗口）与平板档基线截图采集（parity 阶段 3 之后的前置片）。
- Watch-outs: ① 回写只写结论与指针，不复制规格原文（`android/README.md` 与 ROADMAP 都在被判定/扫描面上）；② 改完任务卡状态后必须重跑 `metrics_report.py --write`，否则 `metrics_report` 判红并连带 `checkers_selftest` INCONCLUSIVE（本片实测走过一次：卡面改动后 `RUN-ALL-GUARDS pass=37 fail=1 inconclusive=1`，重生成报告后同两条转绿）；③ 带行号的引用一律写仓库根起算全路径，模块内短写不被 `CITATION_RE` 计入。
