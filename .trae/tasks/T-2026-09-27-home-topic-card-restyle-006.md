# Treelearn Harness Task: 首页历史主题卡片与空态

id: T-2026-09-27-006
title: 首页历史主题卡片与空态
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-27-home-visual-fidelity-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 90
shared_code_touched: false
depends_on: [T-2026-09-27-home-recommendation-restyle-005.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首页视觉 1:1 还原（父需求 T-2026-09-27-001）。用户已拍板四项。
- 本任务在链路上的位置：链路第五个切片，改首页最后一节（区头、卡片、空态），并作为**文档回写任务的承担者**——`android/README.md` 首页一节由本任务统一回写，避免 002–007 各写一份。
- Inputs/outputs: 输入为 `HomeViewState.topics`（`TopicCard`：标题、摘要、分支数、未读数、最近使用时间）与 `hasTopics()`；输出为区头计数、卡片三段文本与附属标记、空态文案。
- State and exception handling: 无主题时显示空态文案且不显示计数器；摘要为空时不显示摘要行（不写占位文字）；未读数为零时不画蓝点；最近使用时间按设备时区与本地化格式化（沿用学习库的既有做法）。
- Constraints and trade-offs: 信息架构保持 V3.2——首页只画最近若干条（现为三条），不是全量列表；卡片不提供改名/删除菜单（这些动作的正典入口在学习库）；可点区域不小于 `touch_min`。
- AI recommendation and rationale: 卡片的附属元素（摘要、蓝点、临时标题、元信息行）都是「有才画」的可见性判定，全部由既有 `TopicCard` 字段直接决定，不新增派生规则；计数与主标语共享 `hasTopics()` 这一个判定点，避免两个互为反义的谓词各写一遍。
- User-confirmation decisions: 无菜单、非全量列表（用户拍板的信息架构口径）。
- Facts: 原型历史主题区与卡片结构见 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 第 157-171 行与第 1705-1753 行；学习库的元信息格式串 `library_topic_meta` 与相对时间格式化先例见 `screen/LibraryScreen.java` 第 283-287 行；`BottomTabShellInstrumentedTest` 通过 `R.id.home_recent_list` 与其行子视图点击首页卡片（第 136 行）。
- Risks: `home_recent_list` 的行结构将被重写，仪器测试对「行可点」的假设必须保持；`TopicCard.branchCount` 的语义（是否含根）需与学习库显示一致，不能两处口径不同。
- Out of scope for this task: 全量主题列表（归学习库）；改名/AI 刷新/删除（归学习库）；动效（007）。

## Goal

首页历史主题区与原型同构：区头为标题＋计数（无主题时不显示计数），卡片含未读点、衬线标题、两行截断摘要与元信息行，且卡片仍可点进入对话；无主题时显示空态文案。

## Scope

### In scope

- 新增 `res/layout/item_home_topic.xml`、`res/drawable/bg_topic_card.xml`。
- `app/src/main/java/com/treelearn/app/screen/HomeScreen.java`：历史主题区渲染（区头计数、卡片、空态）。
- `res/values/strings.xml`：区头改「历史主题」、新增元信息格式串与空态文案。
- 新增仪器测试 `app/src/androidTest/java/com/treelearn/app/HomeTopicCardInstrumentedTest.java`。
- `android/README.md` 首页一节回写（本轮 002–007 的全部新增资源与顺序事实）。

### Out of scope

- 其它页面的列表形态；卡片菜单；动效；`LibraryScreen` 与学习库的数据口径。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 卡片视觉与结构重写；点击卡片进入该主题的对话这一行为必须保持，`home_recent_list` 容器 id 必须保持。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `HomeScreen.renderRecent` 是唯一绘制点；消费者是 `BottomTabShellInstrumentedTest`（点行 → 对话）与 `LibraryScreen`（同一 `TopicCard` 数据、同一分支数语义）。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（有可见变化）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 首页历史主题区的结构、卡片元素与空态；首页新增若干专属资源；首页列顺序（003）与输入卡形态（004）与推荐区形态（005）需一并进入 README。
- Authoritative code/config/migration evidence: `screen_home_body.xml`、`screen/HomeScreen.java`、`res/drawable/` 与 `res/layout/` 的新增文件清单。
- Documentation surfaces checked: `android/README.md` 的「应用层入口索引（:app）—— 首页与设置（T-012）」一节（含资源与尺寸事实段、交互事实段）。
- Documentation files updated: `android/README.md`（本任务）。
- Producer/consumer/sibling/compatibility paths checked: `screen/LibraryScreen.java` 的元信息格式与相对时间、`ui/TopicCard.java` 的字段语义。
- Superseded phrases/examples reverse-searched: 反查 README 与 `Docs/spec/` 内「最近学习」「推荐学习」「发送」等旧文案名，改写或登记。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: PRD 不动；评审 M 批次状态在 005 与 006 各自闭环/未闭环处标注。

## Acceptance criteria

- [x] 仪器测试钉住两态：有主题时首页出现历史主题卡片（含元信息行）且可点进入对话；无主题时出现空态文案且不出现计数器。命令与证据见 TDD 记录。
- [x] 卡片的分支数语义与学习库一致（同一字段、同一格式串风格），不出现两处口径不同。
- [x] `android/README.md` 首页一节已回写，且其描述与 `screen_home_body.xml`、新增资源清单一致（反向核对）。

## TDD execution record

### RED — failing test first
- Test/scenario: 新增仪器测试 `HomeTopicCardInstrumentedTest`（2 例：有主题时首页画出卡片、标题与元信息行且整卡可点进对话；无主题时画空态句且不画计数器）；首次运行时它引用的 8 个新 id/string 尚不存在。
- Command: JAVA_HOME=D:\Environment\Jdk\jdk21 ./gradlew --console=plain :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.HomeTopicCardInstrumentedTest
- Exit code: 1
- Observed failure: `> Task :app:compileDebugAndroidTestJavaWithJavac FAILED`；`HomeTopicCardInstrumentedTest.java` 报 **9 处「错误: 找不到符号」、8 个不同符号**，全部是要新建的 id/string：`R.id.home_topic_title`(110) / `home_topic_meta`(112) / `home_topic_unread`(119) / `home_topic_summary`(121) / `home_topics_empty`(136) / `home_recent_count`(140)，以及 `R.string.home_topic_meta`(114,116) / `home_topics_empty`(137)；`BUILD FAILED in 1s`。
- Failure reason validated: 失败在**编译期**，且每条错误只指向尚不存在的新 id/string（不是测试写法或既有 API 的问题）；补齐布局/字符串后**同一条命令**转绿（见 GREEN）。**这次失败没有触碰设备**——编译失败先于安装，因此 RED 不产生任何装机副作用。证据 `artifacts/evidence/T-2026-09-27-006-red.txt`（该次运行报告 0 份；目录里那份 17:39 的旧报告被脚本按运行时刻排除，已在该文件里显式登记为 stale）。

### GREEN — minimum implementation
- Implementation: 新增 `res/layout/item_home_topic.xml`（卡片三段：未读点＋衬线标题的顶行 / 两行截断摘要 / 「树图标＋分支数＋相对时间」元信息行）、`bg_topic_card.xml`（`surface` + `radius_md`——**不动**共用的 `bg_card`，后者是 `radius_lg`）、`bg_unread_dot.xml`、`ic_tree_branch.xml`（原型 `ITree` 的弧线写法，矢量图无 `<circle>` 元素）；`screen_home_body.xml` 历史区改为「区头（标题＋计数）＋空态句＋卡片列」三件，去掉原先写死的 `text_h2` 区头；`HomeScreen` 的 `renderRecent` 改吃整个 `HomeViewState`（不再只吃 `topics`），新增 `topicCard` / `metaText` / `description`，删掉原先那份手搓 `LinearLayout` 的 `recentRow`；`strings.xml` 把 `home_recent_title` 由「最近学习」改为「历史主题」，并新增 `home_recent_count` / `home_topic_meta` / `home_topics_empty` / `home_topic_unread_cd`。
- Command: JAVA_HOME=D:\Environment\Jdk\jdk21 ./gradlew --console=plain :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.HomeTopicCardInstrumentedTest
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-09-27-006-green.txt` —— 真机 `ALP-AN00 - 13` 上 `BUILD SUCCESSFUL in 14s`；报告 `TEST-ALP-AN00 - 13-_app-.xml` 的 `testsuite` 为 **tests="2" failures="0" errors="0" skipped="0"**（属 `com.treelearn.app.HomeTopicCardInstrumentedTest`，脚本按本次运行时刻过滤，报告 1 份）。**口径说明：为满足「RED 与 GREEN 必须用同一条目标测试命令」，本条命令与 RED 逐字相同（已核 IDENTICAL=True）。** 该命令是仪器测试，故 GREEN 会在真机上装卸一次被测应用。
- Regression evidence: （a）`artifacts/evidence/T-2026-09-27-006-regression-device.txt` —— 真机 `:app:connectedDebugAndroidTest --tests BottomTabShellInstrumentedTest,AppLaunchInstrumentedTest` **EXITCODE=0**、`BUILD SUCCESSFUL in 20s`、**tests="6" skipped="0" failures="0" errors="0"**：其中 `BottomTabShellInstrumentedTest.aConversationOpenedFromHomeHidesTheBarAndBackLandsOnHome` 点的正是被本任务重写的 `home_recent_list` 第一行，卡片的可点语义因此是被真机验过的、不是自述；（b）`artifacts/evidence/T-2026-09-27-006-regression-jvm.txt` —— 全量 `:app:testDebugUnitTest --rerun` **EXITCODE=0**、**78 个报告文件 / tests=671 / skipped=0 / failures=0 / errors=0**（与 005 的 78/671 相同：本任务只加了仪器测试，JVM 用例数不变）。

### REFACTOR
- 无独立重构轮，但本任务含一次结构收敛：原先 `HomeScreen.recentRow(TopicCard)` 用手写 `LinearLayout` 逐控件拼装行（含 `bg_card`、`text_h2` 标题、`「›」` 文本箭头），本轮改成 inflate `item_home_topic.xml` 再按字段着色。形状因此从代码搬回声明式布局，`recentRow` 删除；卡片的两处「由字段决定」的逻辑（摘要有无、未读有无）与两处文案组装（`metaText` / `description`）各自抽成小方法，都是单一职责且不再重复。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（已按 TDD 执行，RED/GREEN 如上）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

### 规模记录（判定口径：非空非注释生产代码行）
量测命令：`python artifacts/tmp/count_t006_lines.py`（新文件按全文计）+ 对 `HomeScreen.java` 的本任务段落按同一口径计；与全工作线共用的文件取本任务 hunk 行，不用 `git --numstat`（该输出是整条首页线的并集）。

| 组成 | 非空非注释 | 备注 |
| :--- | ---: | :--- |
| 新增 `item_home_topic.xml`（全文） | 73 | 卡片三段 |
| 新增 `bg_topic_card` / `bg_unread_dot` / `ic_tree_branch` | 34 | 5+4+25 |
| `HomeScreen.java` 的 T-006 段（`renderRecent`/`topicCard`/`metaText`/`description`） | 55 | 替代原 `renderRecent`+`recentRow`（约 70 行），下同 |
| `screen_home_body.xml` 历史区（区头＋计数＋空态＋列表） | 约 25 | 与 003/004/005 共用文件 |
| `values/strings.xml`（4 新增 + 1 改写） | 5 | |
| **判定值** | **约 192** | 测试、证据、README 不计入 |

- 超出 `estimated_production_lines: 90`（低估约 2.1×），但**低于 200 软上限 → 不构成例外、不触发警告**（500 硬上限未触及）。低估原因如实记录：估算按「改渲染 + 两个小布局」，实际新增了一个完整卡片布局（73 行，其中大半是逐项注释）与一个矢量，并把 `renderRecent` 的输入从 `List` 升成整个 `HomeViewState`（相位判定进了这里）。
- 本任务未触及 `colors.xml` / `tokens.json`，未新增 token（未读点取既有 `blue`、卡片取既有 `surface`/`radius_md`、树图标取既有 `ink3`），与 Scope 约束一致；守门复跑见 Verification report。

## Implementation notes

- 元信息行＝分支数＋相对时间，格式串走 `strings.xml`；相对时间用平台格式化以跟随设备时区与语言（与学习库同源）。
- 未读点、临时标题胶囊都是「有才画」，不写占位。
- 区头计数只在有主题时出现，读 `hasTopics()`。
- 卡片圆角取卡片档（`radius_md`），不使用 `bg_card`（那是 `radius_lg`，且被全站 12 个布局共用）。
- 重写后立刻跑 `BottomTabShellInstrumentedTest`；若其行点击断言因结构变化失配，按等价覆盖改写并登记。

## Verification report

- Verdict: PENDING
- Evidence: 目标仪器测试两态 + 真机回归 + JVM 全量，四份证据在 `artifacts/evidence/T-2026-09-27-006-{red,green,regression-device,regression-jvm}.txt`；设计 token 守门复跑 `check_design_tokens.py` → `0 check(s) failed`、EXITCODE=0（44 token / 16 资源不变）。**真机截图仍未取**，待 008 复核时一并取 —— 不得读成已通过。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。`home_recent_list`（仪器测试的点击容器）与被点行的**可点语义**都保留，`BottomTabShellInstrumentedTest` 未改一行即通过，等价覆盖由真机回归证明；`home_recent_title` 的 id 保留、只改文字取值。`HomeScreen.recentRow` 删除属实现内部重构（无对外断言指向它）。
- Gaps: ① 真机截图未取（头部＋卡片两态）；② 「临时标题」胶囊未实现 —— `ui.TopicCard` 无临时标题字段，卡片不猜测（本任务边界内无法闭环，需数据面另立条目）；③ 摘要**出现的**方向未在本任务断言（fixture 不产派生摘要），该方向由 `LibraryTopicSummaryInstrumentedTest` 在库侧覆盖同一字段。
- Risks: ① 首页卡片与学习库行是两套形态（信息架构允许：前者是快捷面、后者是正典列表），但两处的「分支数」必须继续同读 `TopicCard.branchCount` —— 若将来有人给首页另算一个数，本条与库侧会分叉；② 未读事实现在同时由蓝点与卡片 content description 承载，若将来有人去掉描述，无障碍就成了「只有形状」。
- 规模: 判定值约 192 行，超 `estimate: 90` 但**低于 200 软上限**（见 TDD 记录规模表），未触及 500 硬上限

## Handoff notes

- Completed: `item_home_topic.xml` / `bg_topic_card.xml` / `bg_unread_dot.xml` / `ic_tree_branch.xml`；`screen_home_body.xml` 历史区（区头＋计数＋空态＋卡片列）；`HomeScreen` 的 `renderRecent`（改吃 `HomeViewState`）＋ `topicCard` / `metaText` / `description`，删 `recentRow`；`strings.xml` 区头改「历史主题」并新增 4 条；`android/README.md` 首页一节回写（版式顺序、新增资源、文案事实、有意偏差、已知未闭环）；`HomeTopicCardInstrumentedTest`（2 例）；四份证据
- In progress: 待 008 独立验证（含真机截图）
- Blocker: 无
- Unblock condition: 不适用
- Next steps: 007（入场与卡片错峰动效，依赖本任务）
- Watch-outs: `home_recent_list` 与 `home_draft` 的 id 不可改（仪器测试按 id 找）；不得把首页做成第二份全量列表（仍只画 `MAX_RECENT` 条）；`branchCount` 必须继续读 `TopicCard` 的同一字段，不要在首页另算；README 首页一节**只在本任务写一次**（002–007 不再各写一份）
