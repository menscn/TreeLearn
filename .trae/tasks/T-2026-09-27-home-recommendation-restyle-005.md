# Treelearn Harness Task: 首页推荐区视觉与标签映射

id: T-2026-09-27-005
title: 首页推荐区视觉与标签映射
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-09-27-home-visual-fidelity-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 50
shared_code_touched: false
depends_on: [T-2026-09-27-home-composer-restyle-004.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 首页视觉 1:1 还原（父需求 T-2026-09-27-001）。用户已拍板四项。
- 本任务在链路上的位置：链路第四个切片，只改推荐区内部（头部与卡片形态、标签映射、轨道参数）。
- Inputs/outputs: 输入为 `HomeViewState.recommendations`（`Recommendation` 与它的 `Kind` 三态）；输出为头部文案、卡片三段结构与三类标签的唯一映射。
- State and exception handling: 无主题时头部为「试试这些起点」、有主题时为「猜你想学」（这与 003 的 `hasTopics()` 读同一判定，不新增谓词）；推荐列表为空时整段不显示（既有行为保持）。
- Constraints and trade-offs: 可点区域不小于 `touch_min`；卡片高度因此大于原型的实际像素高，这是项目规则的让步，已在规格的偏差表登记。
- AI recommendation and rationale: `Kind` → 标签文案的唯一映射抽成纯类并单测，因为评审 M7 要求「三类标签齐全」而现状只有一处隐式映射——把映射收成一个可测的判定点，才能保证不会某一类落到默认分支。
- User-confirmation decisions: 无（视觉细节按规格）。
- Facts: 三类 `Kind`（STARTER / FOLLOW_UP / NEW_DIRECTION）已存在于 `ui/Recommendation.java`；三条标签字符串已存在于 `resources/values/strings.xml`（`home_rec_tag_starter` / `home_rec_tag_follow` / `home_rec_tag_fresh`）；原型头部与卡片形态见 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 第 339-351 行与第 1623-1641 行；`screen/RecommendationMarquee.java` 已实现两条独立轨道与触摸暂停。
- Risks: 卡片内新增 chevron 后，整项点击热区与「整卡即发送」语义可能被误读成「进入详情」——必须保持点击即发送（PRD 6.2.17 point 4）。
- Out of scope for this task: **评审 M7 的后半「就绪文案动态化」**（默认提供商名没有既有端口可读，需另立条目，见父需求 Deferred）；历史主题区（006）；动效（007）。

## Goal

首页推荐区与原型同构：头部为「星芒 ＋ 短标签」，卡片为「类别胶囊 ＋ 文本 ＋ 右侧箭头」，且三类标签的映射是一个被单测钉住的唯一判定点。

## Scope

### In scope

- `res/layout/item_recommendation.xml`：卡片三段结构、间距与圆角。
- `res/layout/screen_home_body.xml` 的推荐区内部（头部结构与字号，不动列顺序）。
- `app/src/main/java/com/treelearn/app/screen/RecommendationMarquee.java`：与新卡片结构的绑定与尺寸。
- 新增 `app/src/main/java/com/treelearn/app/screen/RecommendationTag.java`：`Kind` → 文案与底色的唯一映射。
- 新增图标：星芒（头部）与右箭头（卡片内）。
- 补充测试：`app/src/test/java/com/treelearn/app/screen/RecommendationTagTest.java`。

### Out of scope

- M7 的动态就绪文案；推荐内容的来源与生成（`HomeData.recommendations` 不动）；历史主题区；动效。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 卡片视觉改变；「点推荐的问答文本原样作为根问题提交」这一语义必须保持（PRD 6.2.17 point 4），轨道反向滚动与触摸暂停保持。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `RecommendationMarquee` 是 `item_recommendation.xml` 的唯一消费者；`HomeScreen` 转发点击到 `HomeActions.onRecommendationTapped`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（有可见变化）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 推荐区头部结构与卡片结构；三类标签的映射成为独立判定点。
- Authoritative code/config/migration evidence: `screen/RecommendationTag.java`、`item_recommendation.xml`、`screen/RecommendationMarquee.java`。
- Documentation surfaces checked: `android/README.md`（首页一节的推荐区描述）。
- Documentation files updated: 由 006 统一回写 `android/README.md`。
- Producer/consumer/sibling/compatibility paths checked: `ui/Recommendation.java`、`ui/StarterRecommendations.java`。
- Superseded phrases/examples reverse-searched: 反查 README 中推荐区条目名「推荐学习」并登记为待改写。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 评审产物 `Docs/04a-ux-cross-review/知芽-TRELEARN-PRD截图对齐分析_2026-09-27.md` 的 M7 前半在本任务闭环；后半不闭环，需在该处标注状态（只加状态，不改评审结论）。

## Acceptance criteria

- [x] 单元测试钉住标签映射：三类 `Kind` 各自映射到不同的标签文案与底色档，且没有落到默认分支的输入；命令与证据见 TDD 记录。
- [ ] 卡片点击仍是「把推荐问题原样作为根问题提交」，不因新增箭头变成进入详情（仪器测试或单元证据 + 真机观感）。
- [ ] 真机截图证据：推荐区头部与两条反向轨道的卡片形态与原型一致。

## TDD execution record

### RED — failing test first
- Test/scenario: 新增 `RecommendationTagTest`（5 例）钉住「`Kind` → 标签文案 + 底色档」的唯一映射与推荐区头部的两选一文案；首次运行时被测类 `RecommendationTag` 尚不存在。
- Command: JAVA_HOME=D:\Environment\Jdk\jdk21 ./gradlew --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.screen.RecommendationTagTest"
- Exit code: 1
- Observed failure: `> Task :app:compileDebugUnitTestJavaWithJavac FAILED`；`RecommendationTagTest.java` 报「错误: 找不到符号」，**30 行「找不到符号」、15 处不同错误**，每条错误的符号均为 `变量 RecommendationTag`，位置全部是测试里对 `RecommendationTag.labelRes / textColorRes / backgroundRes` 的引用；`BUILD FAILED in 1s`。捕获时间 **17:35:35**，早于实现类写入时间 17:35:58。
- Failure reason validated: 失败在**编译期**，且每条错误只指向尚不存在的类 `RecommendationTag`（不是测试写法或既有 API 的问题）；写入实现类后**同一条命令**转绿（见 GREEN）。顺序证据（文件系统 mtime）：测试源 17:34:51 → 本 RED 17:35:35 → 实现 `RecommendationTag.java` 17:35:58 → GREEN 17:37:54。证据 `artifacts/evidence/T-2026-09-27-005-red.txt`（**该文件头部原误写 `generated: 17:34:59`，已在文末「元数据更正」段更正为写盘时刻 17:35:35 并留痕**）。

### GREEN — minimum implementation
- Implementation: 新增 `screen/RecommendationTag.java`（`Kind` → 文案/底色的**唯一判定点**：`labelRes` 三态全列、**无 `default:` 分支**，`brandTinted` 只认 `FOLLOW_UP`，底色/文字色分别落 `tl_green_soft`+`tl_green_deep`（品牌档）与 `tl_line`+`tl_ink3`（中性档））；新增矢量 `ic_sparkle.xml`（头部星芒）与 `ic_chevron_right.xml`（卡片尾箭头）；重写 `item_recommendation.xml` 为「类别胶囊 + 文本 + 箭头」三段式，且**移除原先钉死的 `backgroundTint` / `textColor`**（把着色交还给判定点）；`screen/RecommendationMarquee.java` 的 `newChip` 改为向 `RecommendationTag` 取文案与两色、删掉自带的 `tagLabelRes`（旧法带 `default:` 兜底，正是评审 M7 读不出「三类是否都接上」的根因）；`screen_home_body.xml` 推荐区头部改为「星芒图标 + 短标题」；`HomeScreen.render()` 按 `hasTopics()` 在 `home_rec_starter`/`home_rec_history` 间切换（读同一判定，不新写谓词）；`values/strings.xml` 移除已被取代的 `home_recommendations_title` 字符串（视图 id 保留）。
- Command: JAVA_HOME=D:\Environment\Jdk\jdk21 ./gradlew --console=plain :app:testDebugUnitTest --tests "com.treelearn.app.screen.RecommendationTagTest"
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-09-27-005-green.txt` —— `BUILD SUCCESSFUL in 5s`；报告 XML `TEST-com.treelearn.app.screen.RecommendationTagTest.xml` 为 **tests="5" skipped="0" failures="0" errors="0"**（`everyKindIsNamedAndNothingFallsThroughToADefault` / `theTwoTintsAreThePrototypesRatherThanAnEvenSplit` / `theStripReadsTheJudgementInsteadOfCarryingItsOwn` / `theChipCarriesThePrototypesThreePartsAndLeavesTheTintToTheJudgement` / `theStripStillSaysWhichOfTheTwoHeadingsItIsShowing`）。**口径说明：为满足「RED 与 GREEN 必须用同一条目标测试命令」，本条命令与 RED 逐字相同（已核 IDENTICAL=True），因此只覆盖 `RecommendationTagTest`；全量回归另列。**
- Regression evidence: `artifacts/evidence/T-2026-09-27-005-regression.txt` —— 全量 `:app:testDebugUnitTest --rerun` **EXITCODE=0**、`BUILD SUCCESSFUL in 4s`；**78 个报告文件 / tests=671 / skipped=0 / failures=0 / errors=0**（计数由脚本遍历全部报告 XML 求和，不读 BUILD 字样；相对 004 的 77 份 +1，即本任务新增的 `RecommendationTagTest`）。

### REFACTOR
- 无独立重构轮。本任务的核心动作本身就是一次重构——把原先内联在 `RecommendationMarquee` 里的 `Kind → 文案` 隐式映射（且带 `default:` 兜底）收敛成 `RecommendationTag` 一个可测判定点，并让标记（`item_recommendation.xml`）不再自带着色。收敛后 `RecommendationMarquee` 只「问」不「判」，旧 `tagLabelRes` 已删除，未出现新的重复。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用（已按 TDD 执行，RED/GREEN 如上）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

### 规模记录（判定口径：非空非注释生产代码行）
量测命令：`python artifacts/tmp/count_t005_lines.py`（遍历文件按「非空且不以 `//` `/*` `*` `<!--` 开头」计数）；与全工作线共用的文件取本任务 hunk 行，不用 `git --numstat`（该输出是整条首页线的并集，无法归因）。实测输出：新增四文件小计 **104**。

| 组成 | 非空非注释 | 备注 |
| :--- | ---: | :--- |
| 新增 `RecommendationTag.java`（全文） | 30 | 新增文件按全文计 |
| 新增 drawable `ic_sparkle` / `ic_chevron_right` | 32 | 19+13 |
| 重写 `item_recommendation.xml`（全文） | 42 | 三段式 + 去钉死着色 |
| `RecommendationMarquee.java`（import + `newChip` 取判定点 + 删 `tagLabelRes`） | +7 / −9 | 与全工作线共用文件，取本任务 hunk |
| `screen_home_body.xml` 推荐区头部块 | 约 20 | 与 003/004 共用文件，取本块改动行 |
| `HomeScreen.java`（字段 + findViewById + setText） | +4 | 同上取本任务行 |
| `values/strings.xml`（移除 `home_recommendations_title`） | −1 | 视图 id 保留 |
| **判定值** | **约 134** | 测试、证据、文档不计入 |

- 超出 `estimated_production_lines: 50`（低估 2.7×），但**低于 200 软上限 → 不构成例外、不触发警告**（500 硬上限未触及）。低估原因如实记录：估算按「只调卡片视觉」，实际新增了一个判定点类（`RecommendationTag`）、两个矢量，并把整张卡片重写为三段式；这三块在初稿 Scope 里被当成「改布局」。
- 本任务未触及 `colors.xml` / `tokens.json`，未新增 token（中性档取既有 `line`/`ink3`，品牌档取既有 `green-soft`/`green-deep`），与 Scope 约束一致。

## Implementation notes

- 头部：星芒图标与短标签同一行，标签文案随有无主题二选一（读 `hasTopics()`，不得新写一个谓词）。
- 卡片：胶囊（类别）＋文本＋箭头；类别胶囊两档底色——入门与顺着你学的取品牌淡底，换个方向取中性淡底。中性档同样只引用既有 token。
- 轨道参数沿用现有实现（上行向左、下行向右、触摸暂停后恢复），本任务只调卡片尺寸与间距。

## Verification report

- Verdict: PENDING
- Evidence: 目标单测 `RecommendationTagTest`（5 例）与全量回归见 TDD 记录，证据三份在 `artifacts/evidence/T-2026-09-27-005-{red,green,regression}.txt`；真机截图（推荐区头部 + 两条反向轨道）待 008 复核时一并取 —— **本会话未连设备执行，不得读成已通过**
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。`item_recommendation.xml` 的 `rec_tag`/`rec_text` 两个 id 保留，新增 `rec_chevron`；着色从标记移到 `RecommendationTag`，覆盖断言改为「标记不得自带 tint」（`theChipCarriesThePrototypesThreePartsAndLeavesTheTintToTheJudgement`）；`RecommendationMarquee` 自带的 `tagLabelRes`（带 `default:` 兜底）删除，等价覆盖由 `everyKindIsNamedAndNothingFallsThroughToADefault`（断言实现里不存在 `default:` 分支）承担
- Gaps: ① 真机截图未取；② 「点卡片仍是原样提交根问题」未在设备上验（仪器/真机观感）；③ M7 后半「就绪文案动态化」未闭环（属父需求 Deferred）
- Risks: 卡片新增右侧箭头后，「整卡即发送」的语义可能被读成「进入详情」（PRD 6.2.17 point 4）——箭头已在标记里标为装饰且 `importantForAccessibility="no"`，仍需真机观感确认
- 规模: 判定值约 134 行，超 `estimate: 50` 但**低于 200 软上限**（见 TDD 记录规模表），未触及 500 硬上限

## Handoff notes

- Completed: `screen/RecommendationTag.java`（唯一判定点，无 default）；`ic_sparkle.xml`、`ic_chevron_right.xml`；`item_recommendation.xml` 三段式重写（去钉死着色）；`RecommendationMarquee` 改问判定点、删 `tagLabelRes`；`screen_home_body.xml` 头部星芒 + 短标题；`HomeScreen` 按 `hasTopics()` 切换文案；`strings.xml` 移除 `home_recommendations_title` 字符串；`RecommendationTagTest`（5 例）；RED/GREEN/回归三份证据
- In progress: 待 008 独立验证
- Blocker: 无
- Unblock condition: 不适用
- Next steps: 006（历史主题卡片与空态 + 统一回写 `android/README.md`，依赖本任务）
- Watch-outs: 不得改推荐内容的生成口径；不得复制一份「有无主题」的判定（改读 `hasTopics()`）；`android/README.md` 里推荐区条目名「推荐学习」已登记为待改写，由 006 统一处理
