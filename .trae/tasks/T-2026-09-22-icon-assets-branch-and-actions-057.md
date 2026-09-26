# Treelearn Harness Task: 图标资产（对话、分支与动作）

id: T-2026-09-22-057
title: 图标资产（对话、分支与动作）
template_version: 2
task_type: implementation
status: verified
# 2026-09-22 22:22: leader 直接实现（同 056）。实测动作位 10 处、新增 7 枚资产（3 处复用 056 文件），
# 而非约 14 枚——按「先数动作位再定枚数」执行。依用户指示跳过仪器测试，回归为全量 JVM 单测 + 构建 + 守门。
# 领地内四个文件（view_model_chip / popup_child_branches / item_child_branch / popup_word_action）
# 经逐一核对无图标化动作位（词菜单另有 PRD 热区约束），本片零改动——这是核对后的结论，不是遗漏。
parent_requirement: T-2026-09-22-figma-ia-and-visual-migration-049.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: false
depends_on: [T-2026-09-22-design-token-baseline-sync-050.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把 Figma 精修稿的信息架构与视觉**有机**迁移进 Android 工程，红线是「只改交互与表现，功能规格一项不动」。新稿的「简约高级」观感中线性图标是主要来源之一；工程 `res/drawable/` 目前**没有任何 UI 图标**，对话页与各类弹层里的动作全部是文字按钮。
- Problem/background: 与 056 同源（`res/drawable/` 只有 7 个背景形状与 `ic_launcher_foreground`），但领地不同：本片负责**对话屏、答案动作条、子分支弹层、词操作菜单与模型档位标识**这一组，因为这些文件与 056 的导航/设置组不重叠，两片可以并行。
- Expected outcome: 对话与分支/动作相关的动作位由文字变为统一的线性图标，观感对齐新稿；资产按与 056 相同的网格与描边约定交付，避免两套风格。
- Inputs/outputs: 输入为既有布局里的文字动作位；输出为 `res/drawable/` 下的图标资产与布局中的引用。
- Constraints and trade-offs: 网格、描边、端点与着色约定必须与 056 完全一致（见 056 的 Implementation notes），否则两片各出一套风格。同样地，资产内不写业务色，着色由使用处 `android:tint` 决定。宁可把不确定的图标跳过，也不引入新动作。
- Acceptance evidence: 由真实命令输出提供——构建退出码；资源结构断言；`count_production_lines.py` 的实测行数；Java-only 守门退出码；既有对话与画布相关测试的回归证据。
- Facts: 同一组事实——Vector Drawable 计入生产代码行预算（`.trae/scripts/dev/count_production_lines.py` 第 30 行对 `.xml` 走 `strip_xml`），故约 28 枚图标分两片，每片约 14 枚。本片领地的布局文件与 056 无交集，故两片可并行。
- Non-goals: 不做标签栏图标（052）；不做导航/设置图标（056）；不做画布控制条图标与点阵网格（058）；不改任何行为、不加新动作、不改布局结构（只把文字位换成图标位并保留原 id）。

## Goal

对话屏、答案动作条、子分支弹层与词操作菜单的动作位由文字变为统一的线性图标，资产与 056 同一套约定，且既有行为（含 `contentDescription` 无障碍信息与热区不重叠的既有约束）零退化。

## Scope

### In scope
- 新建约 14 枚 Vector Drawable 到 `res/drawable/`，命名一律 `ic_chat_*` / `ic_branch_*` / `ic_action_*` 前缀。按既有界面上的真实动作位取材（先数动作位再定枚数）。
- 在下列**本片独占**的布局文件里把文字动作位换成图标位，**保留原有 id**：`res/layout/screen_chat_body.xml`、`res/layout/view_answer_actions.xml`、`res/layout/popup_child_branches.xml`、`res/layout/popup_word_action.xml`、`res/layout/view_model_chip.xml`、`res/layout/item_child_branch.xml`。
- 图标着色一律用使用处的 `android:tint="@color/tl_*"`；触达区不小于 `@dimen/touch_min`。
- `res/values/strings.xml`：为被替换的图标动作补齐 `contentDescription`（`cd_` 前缀），**不删除**原文字键。

### Out of scope
- `res/layout/screen_tab_bar.xml` 与标签栏图标（052 的领地）
- `res/layout/screen_home_body.xml`、`res/layout/screen_library*.xml`、`res/layout/activity_main.xml`、`res/layout/item_topic.xml`（IA 链条 052–055 的领地）
- **`res/layout/item_recommendation.xml`**（推荐区属首页，055 的领地；本片不得改动）
- `res/layout/screen_navigation_body.xml`、`res/layout/item_navigation_row.xml`、`res/layout/screen_settings_body.xml`（056 的领地）
- `res/layout/screen_tree_body.xml` 及 `layout-sw600dp/` 下全部文件（058 的领地）
- `res/values/` 下的 token 文件（050 的领地）
- 任何 Java 行为改动、任何新增动作

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**动作位的视觉载体**（文字 → 图标）。必须原样保留的行为是：每个动作的 id、点击回调绑定、可用/不可用状态、`contentDescription` 的可读性。特别地，PRD 第 978 行对词操作菜单有一条既有约束——「分叉入口为 44×44 圆形热区、无分隔线、与文字热区不重叠」——换图标时**不得**改变热区尺寸与重叠关系。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费者是本片列出的六个布局；动作的生产者仍是 `ui/ChatPresenter.java`、`screen/AnswerActionBar.java`、`screen/ChildBranchPopup.java`、`screen/WordMenuPopup.java`、`screen/ModelThinkingPicker.java`，本片不触碰它们的逻辑。平台分支为 `layout/` 与 `layout-sw600dp/`——本片列出的文件均在 `layout/` 下（其中 body 文件被两套外壳 include），一次改动覆盖两套。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 本片不含 Java 改动，故无控制流变化；新增 drawable 是纯资源。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 对话屏与各类弹层的动作位由文字改为线性图标；新增约 14 枚图标资产。
- Authoritative code/config/migration evidence: `android/app/src/main/res/drawable/` 下新增文件；本片 In scope 列出的六个布局。
- Documentation surfaces checked: `android/README.md`（图标资产约定一节，由 056 建立；本片须与之一致，不另立一套）、`Docs/04-ux-design/design-system/README.md`。
- Documentation files updated: `android/README.md` 的图标约定一节补登本片的图标前缀与领地（若 056 尚未建立该节，本片负责建立并保持同一格式）。
- Producer/consumer/sibling/compatibility paths checked: `screen/ChatScreen.java`、`screen/AnswerActionBar.java`、`screen/ChildBranchPopup.java`、`screen/WordMenuPopup.java`、`screen/ModelThinkingPicker.java`、`ChatPresenterTest`、`WordMenuTest`、`ChatViewStateTest`。
- Superseded phrases/examples reverse-searched: `android:text="@string/` 在本片六个布局中的出现位置；`android:text="@string/action_new_branch"`（PRD 第 979 行规定该文案为「新建分支」）——若它被图标替换，须确认文案约束不被误伤。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次有 README 变更（图标约定登记的补充）。
- Historical/PRD handling: 不涉及 PRD 口径变化；V3.1/V3.2 均不改。

## Acceptance criteria

- [x] 资产可解析且风格与 056 一致：约 14 枚新增 drawable 全部为合法 Vector Drawable，24dp 网格、描边 1.8、圆头端点，参数与 056 的新增资产逐项一致（可用同一断言脚本比对两组文件）。〔实测动作位 10 处、交付 7 枚（3 处复用 056 文件），`IconAssetsTest` 9/9 断言通过，见 `T-057-icon-assets-b.txt`〕
- [x] 着色走 token：`grep -rn "tl_" res/drawable/ic_chat_*.xml res/drawable/ic_branch_*.xml res/drawable/ic_action_*.xml` 零命中；六个布局里的使用处均有 `android:tint="@color/tl_*"`。〔GREEN 断言覆盖〕
- [x] 原 id 与回调不变：六个布局中被替换的动作位保留原有 id；`screen/ChatScreen.java`、`screen/AnswerActionBar.java`、`screen/ChildBranchPopup.java`、`screen/WordMenuPopup.java`、`screen/ModelThinkingPicker.java` 五个 Java 文件 `git diff` 零改动。〔GREEN 记录；领地内四个文件经逐一核对无图标化动作位，零改动是核对结论而非遗漏（头部注记）〕
- [x] 词操作菜单的既有约束不变：分叉入口仍为 44×44 圆形热区、无分隔线、与文字热区不重叠（PRD 第 978 行）；「新建分支」的文案约束（PRD 第 979 行）不被误伤——若该按钮改为图标，须保留可读的 `contentDescription` 并确认文案规则仍被满足。〔词菜单经核对**保持文字行**：`wordMenuRowsStayTextualAndKeepTheirHotspotHeights` + `javaDrivenTextualActionsStayTextual` 两条断言钉住〕
- [x] 触达与无障碍不退化：每个图标动作位触达尺寸 ≥ `@dimen/touch_min`；每个图标动作有非空 `contentDescription`。〔strings.xml 追加 10 个 `cd_*` 键〕
- [x] 回归：`ChatPresenterTest`、`ChatViewStateTest`、`WordMenuTest`、`ChatMessageTest`、`TextSelectionTest` 及全量 `:app` 单测全绿；`.\gradlew.bat check` 退出码 0；Java-only 守门退出码 0。〔GREEN 全量 :app 单测 + assembleDebug；check：`P3-B4-gates-2026-09-24.txt` EXITCODE=0；guard：`P3-B3-no-kotlin-2026-09-24.txt` PASS〕
- [x] 规模实测：`python .trae/scripts/dev/count_production_lines.py` 对本片新增/改动的 XML 实测行数落进证据文件（非空非注释口径）。〔2026-09-24 复核 #3 补测：新资产 139 行 / 7 枚（低于 200 估算），见 `T-057-verify3-2026-09-24.txt`〕

## TDD execution record

### RED — failing test first
- Test/scenario: 在 `IconAssetsTest` 追加四个用例：chatIconAssetsFollowTheSharedDraftingConvention（7 枚新资产的约定断言 + 零业务色）、chatReplacedActionKeepsItsOriginalIdIconTintAndDescription（10 个动作位的 id/图标/tint/contentDescription）、wordMenuRowsStayTextualAndKeepTheirHotspotHeights（词菜单保持文字行与 44dp 热区，PRD 6.2.19）、javaDrivenTextualActionsStayTextual（act_children/act_version/chat_retry 保持文字）。首跑在资产缺失状态观察失败。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.IconAssetsTest"
- Exit code: 1
- Observed failure: 9 例中 2 失败——7 枚资产缺失与动作位无图标引用；其余 7 例保持通过
- Failure reason validated: 失败原因与目标断言一致

### GREEN — minimum implementation
- Implementation: 新增 7 枚 Vector Drawable（ic_action_quote/copy/fork、ic_chat_send/close/gear/overview）；screen_chat_body.xml 五个顶栏与引用清除位改 ImageView、chat_send 保留 Button 改复合 drawable；view_answer_actions.xml 的 quote/copy/reset/fork 改复合 drawable（children/version 不动）；strings.xml 追加 10 个 cd_* 键。reset 复用 056 的 ic_nav_restore（同一原型图形 IRefresh，不重复建文件）。词菜单与另三个弹层布局经核对无动作位，零改动。
- Command: ./gradlew.bat :app:testDebugUnitTest --tests "com.treelearn.app.IconAssetsTest"
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL（9/9）；证据文件 artifacts/evidence/T-057-icon-assets-b.txt
- Regression evidence: 全量 :app:testDebugUnitTest + assembleDebug BUILD SUCCESSFUL；真机安装 Success。五个 Java 文件（ChatScreen/AnswerActionBar/ChildBranchPopup/WordMenuPopup/ModelThinkingPicker）零改动。

### REFACTOR — optional
- Cleanup: 无独立重构轮（7 枚资产 + 5 个图标位替换，reset 复用 056 的 ic_nav_restore 避免重复文件）。
- Regression evidence: 修正后同命令 9/9 通过；2026-09-24 全量 `check` 复跑 EXITCODE=0。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: TDD 适用（资源类改动的可执行判据为资产与布局的结构断言）；规模实测新资产 139 行（7 枚），
  **未超** 200 软上限（阈值判定为 `> 200`），故无需例外（2026-09-24 复核 #3 补测确认）。
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 约定与 056 **完全一致**（24dp 网格、`strokeWidth 1.8`、`strokeLineCap round`、资产内中性源色、使用处 `android:tint`）。若 056 先完成，直接照它的资产与 README 约定做；若本片先完成，则本片负责在 README 里建立该节，056 跟随。两片不得各出一套。
- 保留原 id 是本片的硬约束：五个 Java 文件都按 id 查找动作位，换 id 会让 `findViewById` 返回 null 而在运行时崩溃（编译期发现不了）。因此本片对这五个 Java 文件应当**零改动**。
- **不要动 `item_recommendation.xml`**：推荐区属首页，是 055 的领地；本片只列出的六个文件是边界。
- 词操作菜单有 PRD 明文约束（第 978–979 行），换图标时不得改变热区尺寸与重叠关系，也不得让「新建分支」这类有文案规则的按钮失去可读性。这是本片最容易被「顺手美化」破坏的地方。
- 不新增动作：新稿里可能存在工程没有的动作图标（例如新稿新组件才有的），一律跳过，不为其新增动作位。

## Verification report

- Verdict: **PASS**（2026-09-24 复核 #3 收口，见下方「复核记录 #3」）
- Evidence: GREEN 证据 `artifacts/evidence/T-057-icon-assets-b.txt`（9/9 结构断言 + 全量 :app 单测 +
  assembleDebug + 真机安装 Success + 五个 Java 文件零改动）；复核 #3 补强
  `artifacts/evidence/T-057-verify3-2026-09-24.txt`（规模实测 139 行 + 门禁刷新）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片不删除断言；动作位 id 与回调不变，故既有断言语义不变。词操作菜单的既有热区断言保持原样通过。
- Gaps: 图标观感最终由人 review 判定；结构断言只能证明统一参数被遵守。
- Risks: 与 056 同——误改 id 会导致运行时崩溃且编译期无错。另一处风险是「顺手美化」破坏了词操作菜单的热区约束，因此该条单列验收。

## 复核记录 #3（独立 verifier，2026-09-24）— 收口

- 验收 7 条逐条核对：全部已勾且框内标注证据。规模项由复核 #3 补测（新资产 139 行 / 7 枚，
  低于 200 估算；`T-057-verify3-2026-09-24.txt` EXITCODE=0）。
- 词菜单约束核对：本片对词操作菜单**零改动**（保持文字行），PRD 第 978–979 行约束由
  `wordMenuRowsStayTextualAndKeepTheirHotspotHeights` 断言钉住——「不改」是核对后的结论（头部注记）。
- 枚数偏差核对：交付 7 枚而非约 14 枚系「先数动作位再定枚数」执行结果，3 处复用 056 资产。
- 门禁刷新：全量 `check` EXITCODE=0、no-kotlin guard PASS、token 守门 0 failed、
  `validate_tasks.py` errors=0（均 2026-09-24）。
- 结论：验收全勾 + 规模实测在案 + 门禁全绿 → **verified**。

## Handoff notes

- Completed: 图标现状与行数口径已确认；分片领地与资产写入约定已定义；词操作菜单的既有 PRD 约束已定位（第 978–979 行）。
- In progress: 待派工。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 等 050 verified 后即可开工；与 056、058 无**技术**依赖，但三片**必须串行**——三片都要写 `res/values/strings.xml`（各补自己的 `cd_*` 键）与 `android/README.md`（图标约定一节），且都需在同一个工作树里运行 `.\gradlew.bat check`，构建目录会互相争抢。058 只依赖 050，不依赖本片。
- Watch-outs: (1) 不得改动 IA 链条的布局文件与 `item_recommendation.xml`；(2) 不得改动作位的 id；(3) 不得改变词操作菜单的热区尺寸与重叠关系；(4) 不得在资产里写业务色；(5) 不新增动作。
