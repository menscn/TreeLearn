# Treelearn Harness Task: Web（H5）按 Android 现状 1:1 还原（需求）

id: T-2026-10-15-001
title: Web（H5）按 Android 现状 1:1 还原与本地 JVM 服务承载
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 用户要求启动 Web 版开发，把 Android 现有 XML/View 界面按 H5 方案 1:1 还原，之后再适配 PC 形态；明确这是「适配而非重新开发」。观察到的现象：2026-09 的一条旧 Web 线（`web/**`，React + 本地后端 + 桌面 SQLite）交付了一套与 Android 不一致、形同重新实现的界面，用户判定「调整回来不如重开」，已在 `35bb6ce` 整体移除。推测的原因（现已核实为病因）：同一规则在设计值、SQL/DDL、领域判定、树几何、枚举与错误语义五处各长出第二份实现。
- Users/scenarios: 单人开发者（用户本人）。阶段一至六在浏览器里看夹具驱动的还原结果；阶段七起在手机导出归档、由本机 `backend/` 服务导入并在 PC 浏览器真实使用。
- Expected outcome: 屏幕清单（`Docs/04-ux-design/parity/screen-inventory.json`，条目数以其 `counts` 为准，本文件不复述数字）的每一条目逐屏有三层判据（结构/视觉/行为）台账且结论可重跑；Android 改界面时台账守门转红强制同步；PC 端由本机服务真实启动跑通一次导入导出。
- Core flow and reversibility: Android 导出单一归档（数据库副本 + 附件目录 + 清单 JSON）→ 服务导入即覆盖 PC 副本 → 浏览器打开本机前端 → 查询/命令/流式事件走 `RuntimePort`。单向搬迁、不回传、不合并；归档格式一经发布为长期契约。界面还原阶段不依赖宿主，可随时停用而不影响 Android。
- Inputs/outputs: 输入为 Android 侧成对采集的「真机截图 + 状态与几何快照」夹具，以及阶段七的归档文件；输出为 `frontend/` 构建产物、逐屏台账、`backend/` 本机服务。
- State and exception handling: 每屏必须区分加载中、可用、夹具/数据不可用、服务不可达、Provider 无 Key，以及生成的空闲/提交/推理流/回答流/稳定/失败/中断/取消；异常文案由前端资源表按 key 提供，前端不猜原因。
- Constraints and trade-offs: Java-only（Android 侧禁 Kotlin，判定以字节码为准）；本地优先，不做远程部署/账号/云同步/远程遥测；API Key 留在手机 Keystore，永不下发浏览器；token 权威链为 Android 实现 → 原型 `:root` → `tokens.json` → 生成的 CSS（第 4 个消费者，禁止手抄）；界面事实以 Android 现状为唯一权威，原型与之冲突时 Android 胜。取舍：接受为「单一来源」改动 Android 生产代码（回归面覆盖生成链路与数据层），而不接受服务侧长第二份网关或第二份建表语句。
- AI recommendation and rationale: 架构师建议夹具先行（把界面还原与宿主决策解耦）、量测沿用仓库内已验证过的 1:1 方法而非新造仪器、契约用生成类型 + 共享 JSON fixture 双向差分（把「两处实现」变成可证伪的差分测试）。
- User choice and custom additions: 用户 2026-10-15 拍板四项——阶段一数据用快照夹具驱动、PC 数据与密钥宿主选本地 JVM 服务、首批范围全 16 屏 + 6 浮层、流程采用 Harness；并追加三项裁定——① 允许为单一来源改 Android 生产代码，② 旧 WebView 规格整份删除，③ 删除前端中英双语齐备守门口径。英文本期不做。其中第三项的「16 屏 + 6 浮层」是盘点仪器建立前的人写估计，**已被用户 2026-10-16 的裁定「以实测清单为准」取代**；此处保留原话是为了不篡改当时的拍板记录，引用范围时以 `screen-inventory.json` 为准。
- AI-autonomous decisions: 后端采用零框架（JDK 17 `com.sun.net.httpserver` + 桌面 SQLite，不引 Spring Boot）与服务薄壳边界；桌面 SQLite 裁定为本服务生产引擎、不走 MySQL/Flyway；阶段 1 重采一套真机基线截图；目录名用 `frontend/` 与 `backend/`（治理路由表与 lane/stack 取值已内置，自定义目录会落到无 owner 可写的真空）；树几何在阶段一至六只渲染 Android 采集的坐标快照，禁止手抄成 TypeScript。
- User-confirmation decisions: 上述四项主拍板与三项追加裁定；疑虑 8（1:1 基线取手机档还是平板档）仍待用户在阶段 3 前拍板。【时效（2026-10-17）：该条已由用户裁定为**手机档是本规格 1:1 校正的唯一基线**，判定正文只在 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` 的疑虑 8 一条（§11 阶段 3 行与修订记录各有一句指向它）；本行保留本片验收当时的状态，按「原始发现与处置结果分时可辨」不改写。】
- Acceptance evidence: 逐屏三层台账与偏差台账（机械派生，不手写数字）、`check_design_tokens.py` 四方一致与正反自检、`validate_tasks.py` 零错误、Android 侧 Java-only 守门与既有套件未退化、阶段七的跨引擎差分与双环境跑测记录。
- Facts: `:core`（29 文件）与 `:tree-layout`（12 文件）已是 `java-library` 且被 `android/build.gradle:34 pureJvmModules` 守门；`:ai` 21 个文件仅 2 个引用 Android 类型；`TreeLearnSchema.DATABASE_VERSION` 当前为 5；`screen/` 下实测 14 个屏，其中 5 个没有作为自己根视图的布局充气（`FileManagerScreen`、`ModelSettingsScreen`、`SearchServiceSettingsScreen`、`VoiceSettingsScreen` 四个文档原点名项，加 `HomeDrawerScreen`——它调 `inflate()` 但产物是被 `addView` 挂进去的子视图，按「有没有根布局」它没有；此判定与清单的 `self_drawn` 标记双向核对，见 T-2026-10-15-005 的 Implementation notes）；`check_design_tokens.py` 当前三方一致 44 token 全绿；三支 1:1 量测工具位于会被清理的 `artifacts/tmp/`。
- Assumptions: 高保真原型 v1/v1.1 的样式与 Android 现状基本一致（用户 2026-10-15 提示，并以原型 `:root` 为 token 权威）；工程化后原型冻结、不再作第二份实现维护。
- Unknowns: 桌面 SQLite JDBC 驱动的版本/许可证/原生库与启动开销未核实；Android 在线备份语义在 minSdk 28 的可用性未核实；ALP-AN00 真机本轮是否可连（阶段 1 采集前提）；后端 Gradle 引用 `android/` 下模块的写法未验证。
- Risks: 界面再度与 Android 分叉（旧线病因）；schema/SQL 单一来源触碰共享代码区；桌面密钥强度低于 Keystore；量测环境差异导致结论不可比；文档当前态主张与新方向冲突未回写。
- Deferred: PC 外壳适配（阶段 8）、英文文案、iOS、远程部署、双活编辑与冲突合并、WebView 承载路线。
- Non-goals: 不复用被移除旧线的任何代码；不删除或替换 Android 原生界面；不把 SQLite、密钥保管或生成/流式权威实现搬进 JavaScript；不做远程遥测。
- Skipped non-critical questions and reason: 阶段 3–9 的逐片拆分未预建任务文件——按契约优先小垂直切片，等阶段 0–1 出口门禁结清再拆，避免一次性铺开几十个空任务。

## Goal

交付一条与 Android 现状 1:1 对齐、由单一来源纪律约束的 Web（H5）线：`frontend/` 还原屏幕清单（`Docs/04-ux-design/parity/screen-inventory.json`，条目数只取该文件的 `counts`，本文件不复述）的每一条目并有可重跑的三层判据台账，`backend/` 本机服务在阶段七接入并跑通一次导出导入。

## Scope

### In scope
- 阶段 0：权威链入库、当前态文档回写、量测仪器收编、CSS token 生成链与裸色值守门。
- 阶段 1：夹具采集仪器与逐屏台账生成器（覆盖清单 `counts` 的每一条目，以及清单 `self_drawn` 标记的每一个屏；数字由清单打印，本文件不复述）。
- 阶段 2–9 按 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §11 的门禁表逐阶段拆分并另行建片。

### Out of scope
- Android 业务功能新增、后端远程部署、账号与同步、iOS、英文文案、WebView 承载路线。
- 复用 `35bb6ce` 之前 `web/**` 的任何代码。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 本父需求自身不改运行时行为；其子片中触碰 Android 生产代码的只有阶段 7 两片（`:ai` 提纯、DDL/SQL 文本外提），Android 现有数据、密钥、生成与流式行为必须逐项保持不变并被守门证明。
- Producer/consumer/sibling entrypoints and platform branches: 现有生产者/消费者链为 `MainActivity.swap(View)` 与 `com/treelearn/app/screen/` 下 32 个屏幕文件、`:data` 的 `TreeLearnSchema`/DAO、`:ai` 网关与 SSE 解析、`:tree-layout` 几何；新建消费者为 `frontend/` 与 `backend/`（尚不存在，故本阶段无既有代码需兼容）。
- Native bridge or independent-client compatibility/update requirements: 无原生桥（WebView 路线已删除）；独立客户端即浏览器，其契约正文落在 `contracts/` 单一来源并由阶段 7 建立；阶段 0–1 不产出任何对外接口，因此不存在兼容负担。
- Behavior-neutral non-applicability reason, if applicable: 阶段 0–1 的子片只新增工具与文档、不改动运行时，行为中性。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 本任务不改变运行时事实；改变的是「本仓库是否存在前端/后端」这一当前态主张——它即将为真，而 11 处文档仍主张其为假。
- Authoritative code/config/migration evidence: `frontend/` 与 `backend/` 目录当前确实不存在（`ls` 可证）；`Docs/spec/SPEC-2026-10-01-progressive-web-ui.md` 已于 2026-10-15 删除（`git status` 记为 ` D`）；`.trae/rules/frontend.md` 的双语齐备口径已删除。
- Documentation surfaces checked: `README.md`、`README_EN.md`、`.trae/context/project-brief.md`、`.trae/ROADMAP.md`、`.trae/rules/{frontend,contracts,shared-state,observability}.md`、`.trae/agents/frontend-dev.md`、`.trae/skills/{lint-all,pm-discovery}/SKILL.md`、`Docs/05-technical-design/*`、`Docs/01-demand/*`、`Docs/spec/*`、`Docs/intent/*`。
- Documentation files updated: 由子片 T-2026-10-15-002 执行；本任务只登记待回写清单。
- Producer/consumer/sibling/compatibility paths checked: 已反向检索 `SPEC-2026-10-01`、`zh-CN`/`en-US`、`user-copy-i18n`、`无服务端`/`不建设`/`目录目前不存在` 四组短语；剩余命中均为「已删除/已裁定」的显式记录，不是悬空引用。
- Superseded phrases/examples reverse-searched: 「不交付浏览器正式客户端」「不新增 Spring Boot」「Web 桌面端 reject」「`frontend/` 与 `backend/` 目录目前不存在」「中 English 双语齐备」——前三项由 002 回写，第四项由 002 回写，第五项已随 `.trae/rules/frontend.md` 删除。
- documentation impact: none reason and evidence, if applicable: 不适用，本任务确有文档影响。
- Historical/PRD handling: `Docs/01-demand/**` 属 PRD 基准，按规则只报告漂移不自动编辑；`Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md` 等带日期的历史评估加可见历史快照说明并链接当前权威，不改写当时判定。

## Acceptance criteria

- [ ] 权威链（Android 实现 → 原型 `:root` → `tokens.json` → 生成 CSS → `contracts/` 一份正文 → `:core`/`:tree-layout`/共享 schema 模块）以文字与守门双重落地，且每条规则判定点唯一。
- [ ] 逐屏三层判据台账覆盖清单 `counts` 的每一条目与清单 `self_drawn` 标记的每一个屏（无布局文件的自绘屏成员由该标记给出，不由本文件点名或计数），每项结论可由命令回读，偏差项要么达绿要么显式登记为接受。
- [ ] Android 侧数据、密钥、Java-only 与既有业务规则保持不变，并由既有守门（Java-only dex、依赖方向、`validate_tasks.py`、`checkers_selftest.py`）证明未退化。
- [ ] PC 端由本机服务真实启动并跑通一次导出导入（阶段 7 出口）；本父需求在此之前不得被报为 verified。
- [ ] 同一规则在服务侧或 Web 侧不存在第二份实现（设计值、SQL/DDL、领域判定、树几何、枚举与错误语义）。

## TDD execution record

### RED — failing test first
- Test/scenario: 子任务各自负责 RED/GREEN 记录。
- Command: N/A — parent requirement only.
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: 子任务负责实现记录。
- Command: N/A — parent requirement only.
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A（父需求不产出生产代码）
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 权威文档：`Docs/intent/INTENT-2026-10-15-web-h5-android-parity.md`（accepted）与 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`（accepted）。
- 本任务树的片编号使用连续三位序号，不发明 `NNNb` 式后缀（Task v2 的 `id` 只接受三位数字）。
- 规模判定一律用 `.trae/scripts/dev/count_production_lines.py`（非空非注释；新增文件计全文、既有文件只计本次改动行），不得手搓过滤；引用他任务规模时取该任务当时的实测值，不按共享目录复算。
- 待用户拍板的实质决策只剩疑虑 8（1:1 基线取手机档还是平板档），阻塞点是阶段 3 开工，不阻塞阶段 0–1。【时效（2026-10-17）：已裁定为手机档唯一基线，该阻塞点解除；平板档差异复刻改为阶段 3 之后的独立片，前置是平板基线截图采集。出处同上。】

## Verification report

- Verdict: PENDING
- Evidence: 阶段 0–1 由子片 T-2026-10-15-002/003/004/005 提供单元与守门证据；阶段七之后再派独立 verifier。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A（尚未改动任何断言）。
- Gaps: 真机基线采集依赖 ALP-AN00 可连；桌面 SQLite JDBC 驱动、Android 在线备份语义、后端引用 `android/` 模块的写法三项前置核实未完成。
- Risks: 见规格 §12 风险登记表（分叉、共享代码区、工具位置、桌面密钥、副本分叉、新增依赖、跨目录 Gradle 引用）。

## Handoff notes

- Completed: 意图与规格入库并通过各自校验器；三项追加裁定已落到 `.trae/rules/frontend.md`、`.trae/agents/frontend-dev.md`、旧规格删除与配对意图的归档说明。阶段 0 第 1 项 T-2026-10-15-002（当前态文档回写 + `check_stack_facts.py` 方向事实守门）经第六轮独立复核判 PASS、状态已置 `verified`。验收标准第 1 条**不打勾**：它要求 `tokens.json` → 生成 CSS → `contracts/` → 共享模块整条链以文字与守门双重落地，而 002 只交付了「文字与方向守门」这一段，其余属 004/005 与阶段 7——按字面打勾会让下游把「方向事实已钉住」读成「权威链已闭环」。
- In progress: 阶段 0 的 003（仪器收编）与 004（token 生成链与裸色值守门），阶段 1 首片 005（屏幕清单与台账骨架，依赖 003）。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: 按 002 → 003/004 → 005 顺序执行；005 完成后才进入夹具真机采集片；阶段 2 前端骨架片在 004 绿之后建。
- Watch-outs: 阶段 7 的「schema 与 SQL 文本单一来源」与「`:ai` 提纯」两片触碰共享代码区，必须各自单独成任务、串行、并叠加 `db-migration-reviewer` 与 `android-no-kotlin-guard`；契约正文未定稿前，前端适配器与服务实现不得并行开工；`MockRuntimeAdapter` 的绿永远不能用来支撑 1:1 结论。
