# Treelearn Harness Task: 学习库列表项补上 PRD 要求的「摘要」字段

id: T-2026-09-22-061
title: 学习库列表项补上 PRD 要求的「摘要」字段
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-22-figma-ia-and-visual-migration-049.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 30
shared_code_touched: false
depends_on: [T-2026-09-22-bottom-tab-shell-and-library-screen-052.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 本片是 052 独立复核时**挖出的规格缺口**，不是新增需求。052 已 `verified`，它的学习库列表项实测只有「标题 / 分支数 / 最近使用时间 / 未读点 / 左侧强调条」，而当前态 PRD（V3.2 §6.2.16 功能规格 4）明文要求列表项显示 **根标题、摘要、分支数量与最近使用时间**——「摘要」一项缺失。用户为本次迁移给的红线是「只改交互与表现，功能规格一项不动」，本片是把一条**已写在 PRD 里但被漏掉的规格**补回实现，属修缺口而非改规格。
- Problem/background: 052 的验收标准只写了「标题 / 分支数 / 时间 / 未读点 / 强调条」，没有「摘要」，因此实现按验收标准做就是合规的——缺口在**作业单**而不在实现。这一条在 052 的独立复核里被点出为「目前无任何切片认领」，主会话核实后确认成立（PRD 原文逐字见下）。
- Expected outcome: 学习库「进行中」分段的每个列表项显示主题摘要；摘要缺失时**少一行信息、不渲染占位文案**。
- Core flow and reversibility: 纯展示变更，无写路径。摘要有则显示、无则不显示该行。
- Inputs/outputs: 输入是主题列表数据里已有的摘要字段；输出是列表项的一行文本。
- State and exception handling: **摘要缺失或为空时的处理是本片的重点**：必须「少一行信息」，不得出现「摘要将随本分支对话自动更新」之类的解释性占位文案（PRD 第 471 行明文禁止，该条点名了主题卡与分支行等同类展示位）。这与 053 归档行的同一条规则一致。
- Constraints and trade-offs: 只加一个展示字段，**不改数据模型**。若评估后发现摘要需要从新的查询取，优先复用既有查询（`TopicDao.listOverviews` 就是首页与学习库共用的那一条只读语句）；确需新增时只允许只读查询，且必须显式声明。宁可让摘要按 PRD 的既有规则降级为不显示，也不为它新增写路径或 schema 变更。
- Acceptance evidence: 由真实命令输出提供——仪器测试对「有摘要时显示、无摘要时少一行且无占位文案」的断言；真机上的运行结果；既有测试全绿的回归证据。
- Facts: V3.2 PRD §6.2.16 功能规格 4 原文：「主题列表由学习库的『进行中』分段承载，列表项显示根标题、**摘要**、分支数量与最近使用时间；主题列表继续复用根分支标题作为唯一显示身份。」052 的产物为 `res/layout/item_library_topic.xml`、`screen/LibraryScreen.java`、`library/LibraryPresenter.java`、`library/LibraryViewState.java`、`library/LibraryData.java`、`wiring/SqliteLibraryStore.java`（最后一个是只读 store，委托 `TopicDao.listOverviews`）。
- Assumptions: 主题摘要在既有数据里已存在（主题根分支的摘要/一句话描述），因此本片是「把已有字段显示出来」而不是「新造一个字段」。开工前须先确认这一点；若确认不存在，立即停下回报。
- Unknowns: 摘要在既有 `TopicDao.listOverviews` 的选择列里是否已经取出。开工第一步就要查清。
- Risks: (1) 若摘要需新增查询，会牵动 `android/data`，必须显式声明并不碰写路径；(2) 052 已 `verified`，本片在它产物上追加改动，**不得**借机改动 052 已验收的其它行为（标签栏、返回落点、行结构、id）；(3) 与 053 都要改学习库相关文件，**两者不得并行**（且都要跑 Gradle）。
- Deferred: 「载入示例主题的入口」（V3.2 §6.2.16 功能规格 5 的另一半）——它会**创建**一个示例主题，属新增功能，与迁移批次「不新增任何功能」的非目标冲突，**须由用户另行授权**后方可立片。本片不承接。
- Non-goals: 不新增任何功能或写入路径；不改 schema / migration；不改 052 已验收的其它行为；不做「已完成」分段（053）；不接搜索框（054）；不动首页（055）。
- Skipped non-critical questions and reason: 未追问「摘要要不要截断/几行」——按 PRD 既有规则与既有主题卡的显示做法取值即可，属观感细节，由人 review 判定。

## Goal

学习库「进行中」分段的列表项按 PRD 显示主题摘要，且摘要缺失时少一行信息、不渲染任何解释性占位文案。

## Scope

### In scope
- 开工第一步：确认主题摘要在既有数据/既有只读查询（`TopicDao.listOverviews`）里是否已可用。可用则直接用；不可用则**停下回报**，不得自行新增 schema。
- `library/LibraryData.java` / `LibraryViewState.java`：把摘要带进列表行的不可变状态（形制与既有字段一致，缺失用空值而非占位文案）。
- `library/LibraryPresenter.java`：只在摘要非空时产出该字段，判定仍集中在 presenter（屏不做判定，沿用 052 的约定）。
- `screen/LibraryScreen.java` 与 `res/layout/item_library_topic.xml`：渲染摘要；**摘要为空时该行不渲染、且不留下任何占位文案**。
- `res/values/strings.xml`：若需要新增文案键，只用 `library_` 前缀，不重排、不改动他人键。
- 仪器测试：断言「有摘要时显示」与「无摘要时少一行且无占位文案」两种情形。

### Out of scope
- 「已完成」分段的归档行与删除行为（053 的领地）
- 学习库搜索框（054 的领地）
- 首页与推荐区（055 的领地）
- `MainActivity`、`activity_main.xml`、`screen_tab_bar.xml`（052 已验收的外壳）
- 任何 schema / migration / 写路径变更
- 「载入示例主题的入口」（见 Deferred，须用户授权）

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**列表项多一行摘要文本**（PRD 已要求、现缺失）。必须原样保留的是 052 已验收的全部行为：三标签外壳与返回落点、标签栏显隐只在 `swap()` 一处、「进行中」列表的其它字段（标题 / 分支数 / 时间 / 未读点 / 强调条）与 id、空态文案、以及「屏不做判定」的分层。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是 `library/LibraryPresenter`，消费端口是 `library/LibraryData` 与只读的 `wiring/SqliteLibraryStore`（委托 `TopicDao.listOverviews`）。兄弟入口是首页主题卡（本片不动）。平台分支为 `layout/` 与 `layout-sw600dp/` 两套。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 本片不改 `android/core/`、`android/ai/`，且不碰写路径，故写侧无行为影响。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 学习库「进行中」列表项新增一行主题摘要；摘要缺失时该行不出现。
- Authoritative code/config/migration evidence: `Docs/03-prd-draft/V3.2-知芽（TreeLearn）产品需求文档（PRD）.md` §6.2.16 功能规格 4 与第 471 行；`android/app/src/main/res/layout/item_library_topic.xml`、`screen/LibraryScreen.java`、`library/LibraryViewState.java`、`library/LibraryPresenter.java`。
- Documentation surfaces checked: `android/README.md`（052 新增的「底部标签栏与学习库」一节）、`Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md`。
- Documentation files updated: `android/README.md` 的学习库一节补一句「列表项显示根标题、摘要、分支数量与最近使用时间；摘要缺失时不渲染该行、无占位文案」。若开工确认摘要需从新查询取，记录该只读查询的性质与所在文件。
- Producer/consumer/sibling/compatibility paths checked: `TopicDao.listOverviews`、`wiring/SqliteLibraryStore`、`library/*`、`screen/LibraryScreen.java`、`BottomTabShellInstrumentedTest`（本片不得让它变红）。
- Superseded phrases/examples reverse-searched: 「摘要」在 V3.2 PRD、`android/README.md`、`Docs/04-ux-design/` 中的位置；第 471 行的占位文案禁令原文。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次有 README 变更。
- Historical/PRD handling: 依 V3.2 PRD 实施；V3.1 不改。本片补的是 V3.2 已写明、052 作业单遗漏的一项。

## Acceptance criteria

- [ ] 开工第一步有证据证明主题摘要在既有数据里已可用（或已停下回报「不可用，需另议」）。
- [ ] 列表项显示摘要：仪器测试造一个有摘要的主题，断言学习库「进行中」的行内可见该摘要文本。
- [ ] 摘要缺失时不渲染占位：造一个无摘要（或摘要为空）的主题，断言该行**少一行信息**，且行内**不出现**任何解释性占位文案（对照 PRD 第 471 行点名的那类文案）。
- [ ] 052 已验收的行为零退化：`BottomTabShellInstrumentedTest` 仍全绿（含三标签切换与两条返回落点）；`main_root` / `home_draft` 仍在；标签栏显隐仍只在 `swap()` 一处（`grep` 证明）。
- [ ] 未越界：`git diff --name-only` 不含 053/054/055/056/057/058 的领地文件；`android/data/**/db/migration/**` 零改动；若新增查询，只有 SELECT。
- [ ] 两套平台布局都覆盖（`layout/` 与 `layout-sw600dp/`）。
- [ ] 回归与守门：仪器测试**在真机 `AWSS013807000356` 上跑**并记录退出码（基线 `178 tests / 0 failures`，本片新增用例后总数应大于 178、失败数为 0）；`.\gradlew.bat check` 与 Java-only 守门退出码 0；全量 JVM 单测零失败且不低于 `app suites=37 tests=364`。
- [ ] 规模实测：`python .trae/scripts/dev/count_production_lines.py`（**用仓库脚本，不要手搓过滤**）对新增/改动的生产文件实测行数落进证据文件，并给出与 `estimated_production_lines: 30` 的对比。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING（进入 `in_progress` 前补齐）——预期先写「有摘要时行内显示摘要」「无摘要时少一行且无占位文案」两条断言，在当前 `item_library_topic.xml` 没有摘要位时观察失败。
- Command: PENDING
- Exit code: PENDING
- Observed failure: PENDING
- Failure reason validated: PENDING

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING（须与 RED 同一条目标测试命令）
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: TDD 适用（「摘要显示 / 缺失时不渲染」是可先写、可独立失败的断言）；规模估算 30 行，未超 200 软上限。
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- **先查数据再写代码。** 本片的前提是「摘要已经存在，只是没显示」。开工第一件事就是到 `TopicDao.listOverviews` 与既有表里确认；**确认不了就停下回报**，不要去改 schema 或凭猜测造一个字段。
- **摘要缺失的处理是本片唯一容易做错的地方**：必须是「少一行信息」，不是「显示『暂无摘要』」「显示『摘要将随对话自动更新』」这类解释性文案。PRD 第 471 行对主题卡与分支行已明文禁止，学习库的行属同类展示位，053 的归档行也按同一规则做。
- **不要借机改 052 已验收的东西**：行的 id、结构、其它字段、空态文案、标签栏与返回落点一律保持原样。若发现「要显示摘要就不得不改行结构」，停下回报——那说明估算错了，需要重新评估而不是就地扩大改动。
- **判定仍集中在 `LibraryPresenter`**：屏只画，沿用 052 建立的「屏不做判定」约定（`NavigationScreen`/`LibraryScreen` 的类注释都是这么写的）。
- **跑测环境**：真机 `AWSS013807000356`（ALP-AN00 / Android 13）已连接，模拟器 `Medium_Phone_API_36.1`（API 36）可用；用 `ANDROID_SERIAL` 定向，**不得并发跑两次 Gradle**。本批已有教训：**只跑模拟器会漏掉真机上才出现的缺陷**，因此仪器测试必须真机跑。
- 证据文件写到 `artifacts/evidence/T-061-library-summary.txt`，自带命令行与 `EXITCODE=` 行，日志一律 **UTF-8**（不要写成 UTF-16，那会让复核者 grep 不到而误判「结论缺失」）。
- RED/GREEN 的五个键必须给**纯净值**：`Exit code: 1` 不带粗体、`Observed failure:` 后直接一句话不接代码块、RED 与 GREEN 的 `Command` 逐字符相等。改完任务文件立刻跑 `python .trae/scripts/validate_tasks.py`，目标 `errors=0`。

## Verification report

- Verdict: PENDING
- Evidence: 待提供——摘要可用性的确认证据；两条断言的仪器测试输出（真机）；`check` 与 Java-only 守门的退出码；全量 JVM 单测计数；`grep` 证明标签栏显隐仍只在一处；`git diff --name-only` 的越界证明；`count_production_lines.py` 的实测行数。全部落进 `artifacts/evidence/T-061-library-summary.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片不删除既有断言；新增两条断言覆盖摘要的显示与缺失两种情形，其中「缺失时不渲染占位」与 PRD 第 471 行的既有规则同源，不是新发明的判据。
- Gaps: 摘要的截断长度与行数属观感，由人 review 判定；是否还有其它「PRD 写明但作业单遗漏」的项，本片不声称已排查完毕。
- Risks: (1) 若摘要在既有数据里不可用，本片应立即停下而不是自行造字段；(2) 在 052 已 `verified` 的产物上追加改动，容易顺手扩大改动面——由「未越界」一条与「不要借机改」的明令拦住；(3) 与 053 都要改学习库文件，必须串行。

## Handoff notes

- Completed: 缺口已定位并核实（V3.2 PRD §6.2.16 功能规格 4 要求列表项显示「根标题、摘要、分支数量与最近使用时间」，052 的产物实测无摘要）；占位文案规则已定位（PRD 第 471 行）；本片作业单已落盘并通过机械校验。
- In progress: 待派工。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 等 053 完成后开工（与 053 都要改学习库文件、都要跑 Gradle，**必须串行**）。开工第一步是确认摘要在既有数据里可用。
- Watch-outs: (1) 不得改 schema / migration / 写路径；(2) 摘要缺失时**不得**渲染任何解释性占位文案；(3) 不得改 052 已验收的其它行为与行 id；(4) 判定必须留在 `LibraryPresenter`，屏不做判定；(5) 与 053 串行，不得并发跑 Gradle；(6) 仪器测试必须真机跑，不能只跑模拟器；(7) 不得把同属 V3.2 §6.2.16 功能规格 5 的「载入示例主题的入口」顺手做进来——它属新增功能，须用户另行授权。
