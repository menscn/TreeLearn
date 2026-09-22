# Treelearn Harness Task: 设计基座同步（新稿配色、度量与字体回退栈三处对齐）

id: T-2026-09-22-050
title: 设计基座同步（新稿配色、度量与字体回退栈三处对齐）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-22-figma-ia-and-visual-migration-049.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 30
shared_code_touched: true
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把 Figma 精修稿（暖白纸感底、深绿墨、衬线品牌字、线性图标、克制描边）**有机**迁移进 Android 工程，红线是「只改交互与表现，功能规格一项不动」。父需求 `T-2026-09-22-figma-ia-and-visual-migration-049.md`；规格见 `Docs/spec/SPEC-2026-09-22-figma-ia-migration.md`。
- Problem/background: 工程表现层停在 v1「生长绿」设计系统，配色与度量与新稿存在整体差距。颜色已 100% token 化（`tl_*`）、间距/圆角/字号已 100% 走 `res/values/dimens.xml`，所以「换底」的成本极低——改四个文件即可让**全部屏幕**一起迭代，且不触碰任何交互逻辑。
- Expected outcome: 原型 `:root`、`tokens.json`、`colors.xml` 三方逐值一致；`dimens.xml` 度量对齐新稿；字体分组补上正文 `sans-serif` 与品牌衬线回退两个 token；**新稿自身的两处对比度回退被修正**，正文与次要文字全部达到 WCAG AA 4.5:1。
- Constraints and trade-offs: 只改值，不改 token 名与资源名——`colors.xml` 的 15 个 `tl_*` 资源名与 `dimens.xml` 的条目名是全部布局的引用目标，改名会引发全仓断裂。宁可保留两个修正值与新稿有极小色差，也不让对比度掉到 AA 以下。
- Acceptance evidence: 由真实命令输出提供，不自述——`check_design_tokens.py` 与 `--selftest` 的退出码；对比度审计对承载背景的实测值；`dimens.xml` 的 `git diff` 证明只有值变更。
- Facts: 新稿 `:root` 的 `ink3:#75867B` 在 `bg` 上实测对比度 **3.42**、`amber:#B45309` 实测 **4.45**，两者都低于小字所需的 4.5；工程旧值 `ink3:#636F66` 为 4.66、`amber:#B7791F` 为 3.23。新稿其余 token（`ink 14.29`、`ink2 7.16`、`green 5.45`、`green_deep 10.37`、`red 4.82`、`blue 4.58`）全部达标，其中 `blue` 还修好了旧稿 4.16 的缺陷。
- Non-goals: 不改任何布局与 Java；不打包字体；不退役旧 token `--kai`（保留待后续清理）；不改 `themes.xml` 之外的样式。

## Goal

让全部屏幕的配色、间距、圆角、字号与字体在**不改一行交互代码**的前提下迭代到新稿，同时把新稿自带的 `ink3` 与 `amber` 两处对比度回退修正到 WCAG AA 之上，并使设计 token 三方同步守门脚本零失败。

## Scope

### In scope
- `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `:root` 区块：更新颜色/度量/字体**值**；新增 `--sans` 与 `--serif` 两个 token（非 hex，不牵动 `colors.xml`）。
- `Docs/04-ux-design/design-system/tokens.json`：同步上条的全部值；`fontFamily` 分组补 `sans`/`serif` 并注明本轮不打包字体的落地方式与残余风险。
- `android/app/src/main/res/values/colors.xml`：仅改 15 个 `tl_*` 的**值**（含 `tl_ink3`、`tl_amber` 的修正值），资源名与数量不变。
- `android/app/src/main/res/values/dimens.xml`：字号/圆角/间距对齐新稿档位，条目名不变。
- `android/app/src/main/res/values/themes.xml`：把主题默认字体显式设为系统 `sans-serif`（正文零成本），品牌衬线由各屏品牌 TextView 自行引用 `--serif` 对应档位——后者属布局任务（055），本片只保证主题默认值正确。

### Out of scope
- 任何 `res/layout/**`（含 `layout-sw600dp/`）与任何 `java/**`
- 字体文件与 `res/font/`（本轮不打包字体）
- 画布、图标、信息架构（分别见 058、056/057、052–055）
- `android/README.md` 的对比度条目更新由本片一并完成（见文档影响记录），但 README 其他章节不动

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图变更是**颜色、间距、圆角、字号、字体族的取值**；必须原样保留的是 15 个 `tl_*` 资源名、`dimens.xml` 的全部条目名、以及所有布局对这些名字的引用关系（改名即全仓断裂）。本片不含任何 Java 改动，故无行为分支变化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是 `res/values/colors.xml` 与 `res/values/dimens.xml`；消费者是全部 `res/layout/**`（27 个 `layout/` + 6 个 `layout-sw600dp/`）与全部自绘 View（`screen/TreeCanvasView.java` 等）。平台分支为手机与 `layout-sw600dp/` 两套，本片只改值，两套同时受益，无需分别改动。
- Native bridge or independent-client compatibility/update requirements: 不适用——单一 Android 客户端，无跨端契约与外部客户端。
- Behavior-neutral non-applicability reason, if applicable: 本片为纯资源值变更，不含控制流；证据由守门脚本的逐值比对与对比度实测提供，而不是由单元测试提供。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `tl_ink3`、`tl_amber` 及其他 `tl_*` 的取值；`dimens.xml` 的字号/圆角/间距取值；主题默认字体族为系统 `sans-serif`；设计系统新增 `sans` 与 `serif` 两个字体 token。
- Authoritative code/config/migration evidence: `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `:root`（设计事实源）、`Docs/04-ux-design/design-system/tokens.json`（机器可读镜像）、`android/app/src/main/res/values/colors.xml`（Android 侧副本）、`android/app/src/main/res/values/dimens.xml`。
- Documentation surfaces checked: `android/README.md`（UI 约定、尺寸令牌表与对比度记录）、`Docs/04-ux-design/design-system/README.md`（设计系统变更记录）、`README.md` 与 `README_EN.md`（若含配色描述）。
- Documentation files updated: `android/README.md` 的对比度记录与尺寸令牌说明两条；`Docs/04-ux-design/design-system/README.md` 追加一条变更记录（含 `ink3`、`amber` 的修正值与实测对比度）。
- Producer/consumer/sibling/compatibility paths checked: `res/values/` 三文件与 `Docs/04-ux-design/` 两文件的引用关系；`.trae/scripts/dev/check_design_tokens.py` 的三方比对规则（含 `MIN_TOKENS` 解析下限与「`colors.xml` 不得出现无 token 对应的资源」这条硬约束）。
- Superseded phrases/examples reverse-searched: 「`#636F66`」「`#B7791F`」「LXGW WenKai」「楷体」四处旧值/旧字体措辞，反查 `android/README.md`、`Docs/04-ux-design/design-system/README.md`、`tokens.json` 与原型 `:root`。
- `documentation impact: none` reason and evidence, if applicable: 不适用——本次确有当前态文档变更。
- Historical/PRD handling: 原型 HTML 的**叙事部分**（版式描述、信息架构说明）不在本片改写范围，其被新稿取代的部分由 051 加指向性批注；本片只动它的 `:root` 数值。

## Acceptance criteria

- [ ] 三方同步守门零失败：`python .trae/scripts/dev/check_design_tokens.py` 退出码 0，且输出含「三方一致」与 `measured_tokens`。
- [ ] 守门脚本自身正反双向自检通过：`python .trae/scripts/dev/check_design_tokens.py --selftest` 退出码 0（确认判据不是死代码）。
- [ ] 对比度达标：`tl_ink3` 与 `tl_amber` 在 `bg`、`surface`、`amber_soft` 三种承载背景上的实测对比度均 ≥ 4.5；其余 `tl_*` 作为文字色时同样 ≥ 4.5。判据以脚本实测输出为准，不以目测为准。
- [ ] 度量条目零增删：`git diff android/app/src/main/res/values/dimens.xml` 只出现值变更行，不出现条目名的增删；同一命令的原始输出与退出码存入证据文件。
- [ ] 回归：`Android` 全量构建与既有测试不退化——`.\gradlew.bat check` 退出码 0；Java-only 守门 `android/scripts/no-kotlin-check.ps1` 退出码 0。
- [ ] 平台分支覆盖：`layout-sw600dp/` 下 6 个屏在同一组 token 下渲染无资源缺失（资源名未变即成立，由构建通过证明）。

## TDD execution record

### RED — failing test first
- Test/scenario: 以既有的三方同步守门脚本为判据。RED 由设计系统规定的改动顺序真实产生：只把原型 :root 的值改成新稿值（含 ink3 与 amber 的修正值），此时 tokens.json 与 colors.xml 仍是旧值，脚本必须报出「原型与 tokens.json 数值不一致」并以非零退出码结束；若它报不出，说明守门失效，必须先修守门再改值。
- Command: python -X utf8 .trae/scripts/dev/check_design_tokens.py
- Exit code: 1
- Observed failure: FAIL 三方一致 — 原型与 tokens.json 数值不一致：amber#B05108/B7791F、bg#F4F1EB/F7F6F0、blue#2563EB/2F6BFF、fs-body 15px/16px、fs-display 26px/30px、fs-h1 20px/22px、fs-h2 16px/17px、green#2B6E45/2E7D4F、green-deep#164027/1F5C38、hairline rgba(26,40,32,.07)/rgba(30,43,35,.075)、ink#16241C/1E2B23、ink2#435449/5A6B60、ink3#627067/636F66、lh-read 28px/32px、line#E2DFD6/E6E3D6、r-lg 18px/24px、r-md 14px/16px、r-sm 8px/10px、sans、serif、sh-1、sh-2、surface#FCFBF8/FFFFFF 共 23 个 token 漂移。
- Failure reason validated: 失败确由「改了上游原型、未同步下游镜像」造成，不是解析退化——同一轮输出里原型解析出 42 个 token、tokens.json 解析出 42 个、colors.xml 解析出 15 个资源，均高于下限；把下游两个文件同步后，同一条命令转绿，见 GREEN。

### GREEN — minimum implementation
- Implementation: 按固定顺序同步下游。tokens.json：全部值对齐原型，柔色 token 与字体分组补说明，字体分组补 $note 记录本轮不打包字体的落地方式与 ROM 衬线残余风险。colors.xml：只改 10 个 tl_* 的值，15 个资源名与数量不变。dimens.xml：只改 radius_sm / radius_md / radius_lg 与 text_display / text_h1 / text_h2 / text_body 共 7 个值，条目名一个未动。themes.xml：新增 android:fontFamily=sans-serif 作为主题默认字体。
- Command: python -X utf8 .trae/scripts/dev/check_design_tokens.py
- Exit code: 0
- Passing evidence: OK 三方一致（原型 :root ↔ tokens.json ↔ colors.xml） — measured_tokens=42 measured_resources=15；check_design_tokens: 0 check(s) failed。
- Regression evidence: 见 artifacts/evidence/T-050-design-token-sync.txt：守门自检 10/10 OK 退出码 0；WCAG 对比度复算断言 PASSED 退出码 0；dimens.xml 的 git diff 只有 7 行值变更、条目名 23 到 23；:app:assembleDebug、lint、check 均 BUILD SUCCESSFUL 退出码 0；no-kotlin-check.ps1 VERDICT: PASS 退出码 0。

### REFACTOR — optional
- Cleanup: 本轮不做重构。只改值不改名不改结构，原型 :root 区块保持 52 行、tokens.json 保持 42 个 token、colors.xml 保持 15 个资源、dimens.xml 保持 23 个条目。
- Regression evidence: 与 GREEN 同一轮证据，见 artifacts/evidence/T-050-design-token-sync.txt 的 G1 到 G6。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: TDD 适用——既有守门脚本即判据，RED 由「只改原型、未同步下游」这一步真实产生，GREEN 用同一条命令判定；本片规模 30 行，未超 200 软上限，未超 500 硬上限。
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 改动顺序是固定的，不得颠倒：原型 `:root` → `tokens.json` → `colors.xml` → `dimens.xml` → 跑守门。守门脚本只覆盖**颜色与 token 名一致性**，**不校验** `dimens.xml` 的间距/字号/字体——`dimens.xml` 没有脚本兜底，因此它的验收必须靠 `git diff` 逐行核对条目名零增删。
- 两个修正值的实测依据（本次已用 WCAG 公式复算，脚本可重跑）：`ink3` 新稿 `#75867B` 在 `bg` 上 3.42 → 修正为 **`#627067`**（`bg` 4.62 / `surface` 5.03 / `amber_soft` 4.72）；`amber` 新稿 `#B45309` 在 `bg` 上 4.45 → 修正为 **`#B05108`**（`bg` 4.63 / `surface` 5.04 / `amber_soft` 4.72）。执行时必须用对比度脚本**重新实测并留余量**，若上游值有变以实测为准，不以本条注释为准。
- `colors.xml` 有一条硬约束来自守门脚本：它**不允许存在无 token 对应的颜色资源**（多了会判 FAIL）。所以任何新增颜色都必须同时在原型 `:root` 与 `tokens.json` 里成对出现；反过来，纯字体/度量 token 因为不是不透明 hex，**不需要**在 `colors.xml` 里建对应资源。
- 字体落地方式：`--sans` 写系统 `sans-serif` 栈，`--serif` 写品牌衬线**回退栈**（本轮不打包字体）。残余风险已登记：各厂商 ROM 的中文衬线字形不一致，观感会有差异，本轮接受。`--kai`（旧楷体 token）保留不删，避免牵动 `tokens.json` 历史记录与 README；其退役记为 deferred。
- 本片触碰 `res/values/` 与 `Docs/04-ux-design/` 的 token 文件，属本批的**共享代码区**：与 052–055 串行，不得并行。

## Verification report

- Verdict: PASS
- Evidence: 由独立 verifier 在本机重跑，全部命令与退出码为本轮实测（不是引用实现者的证据文件）。

  ```text
  [verifier 报告]
  task: T-2026-09-22-050
  审阅文件: 原型 HTML / tokens.json / colors.xml / dimens.xml / themes.xml / android/README.md /
            Docs/04-ux-design/design-system/README.md / artifacts/evidence/T-050-design-token-sync.txt
  代码量: 83 行非空非注释（粗糙解析上界 84）/ 200 软上限 / 500 硬上限；并列物理行 88
  任务合规: PASS（validate_tasks.py → PASS errors=0 warnings=0；RED/GREEN 的 Command 逐字符相等；
            Exit code 键为纯整数；五个键齐全、无加粗/无代码块/无垃圾键）
  Lint:
    - android gradlew lint: PASS（BUILD SUCCESSFUL，112 tasks，exit 0）
    - android gradlew check: PASS（BUILD SUCCESSFUL，295 tasks，exit 0）
    - android gradlew verifyDependencyDirection: PASS（1 task executed，exit 0）
    - android no-kotlin-check.ps1: PASS（VERDICT: PASS (13 OK line(s))，exit 0）
  依赖方向: PASS（本片未触碰任何 .java/.gradle/.kts/.toml，无依赖边变更；守门单跑仍绿）
  验收点对照:
    - [x] 1 三方同步守门零失败 —— `python -X utf8 .trae/scripts/dev/check_design_tokens.py` → exit 0，
          输出含「三方一致（原型 :root ↔ tokens.json ↔ colors.xml）」与 `measured_tokens=42 measured_resources=15`
    - [x] 2 守门自检双向 —— `... --selftest` → exit 0，10/10 OK（含值漂移/缺 token/多 token/colors 少多漂移/
          解析退化/:root 缺失），判据非死代码
    - [x] 3 对比度达标 —— 独立实现 WCAG 相对亮度公式复算：ink3 #75867B 3.42/3.72/3.49 → 修正 #627067
          4.62/5.03/4.72；amber #B45309 4.45/4.85/4.55 → 修正 #B05108 4.63/5.04/4.72；两值三背景全部 ≥4.5
          且均优于新稿原值；8 文字色 × 6 承载背景矩阵中 bg/surface/amber_soft 三列全 ≥4.5
    - [x] 4 度量条目零增删 —— 自写解析器比对 HEAD vs 工作区：dimens.xml 23→23 条目、名字集合与顺序完全一致、
          只 7 条值变更；colors.xml 15→15 资源、名字集合与顺序一致、只 10 条值变更
    - [x] 5 回归 —— gradlew check exit 0；no-kotlin-check.ps1 exit 0
    - [x] 6 平台分支覆盖 —— layout/ 27 个 + layout-sw600dp/ 6 个（与任务描述一致）；资源名未变，
          `:app:assembleDebug --rerun-tasks` 73/73 tasks executed、BUILD SUCCESSFUL、exit 0（资源真重编译过）
  专项守门路由: android-no-kotlin-guard → PASS（本 verifier 只转述其 VERDICT 与退出码，未越权代判）；
               contract-guardian / db-migration-reviewer → N/A（无契约、无迁移）
  文档影响记录: PASS（android/README.md 的对比度记录 + 尺寸令牌说明两条均已更新；
               design-system/README.md 已追加变更记录；四个被取代措辞反查后无未标注的当前态残留）
  启动证据: N/A（本片为纯资源值变更，不含启动路径改动；构建与资源编译证据见上）
  结论: PASS
  ```

  独立复现的关键数字（全部为本轮实测，非引用）：
  - 三方同步守门 `check_design_tokens.py` → `0 check(s) failed`，exit 0；`--selftest` → `0 check(s) failed`，exit 0。
  - token 名集合：原型 `:root` 42 个 == tokens.json 42 个，且与 HEAD 相比**新增 0、移除 0**；`tokens.json` 无重名。
  - `colors.xml` 双向核对：期望映射（所有不透明 hex token）15 个 == 实际 15 个，`absent=[] dangling=[] mismatch=[]`。
    `sans`/`serif` 等 27 个非 hex token 一个都没进 `colors.xml`。
  - 原型 HTML 的全部 8 个 diff hunk（-U0）都落在 `:root` 区块内（HEAD 与工作区同为第 21–71 行），
    **区块外 hunk 数为 0**，叙事/版式/信息架构段落零改写。
  - 对比度（自写公式）：见表；额外补做了实现者未覆盖的 Java 自绘面配对 —— `HomeScreen:192/194` 的
    amber on amber_soft = 4.72、`SettingsScreen:84/89` 同值 4.72、`TreeCanvasView:92` 的 tl_ink3 是**描边**
    而非文字（bg 上 4.62 ≥ 3:1 非文字阈值），`leafUnread/accent` 的 tl_amber 是填充图形（4.63 ≥ 3:1）。
    全仓 XML 中 ink3/blue/amber 与 green_soft/red_soft 的组合（4.40–4.48）**未被任何布局实现**：
    green_soft 只作药丸 tint（内含 green_deep 9.95），red_soft 只作危险按钮底（内含 tl_red 4.67）。
  - `tl_blue` 在 layout/ 与 java/ 中引用数均为 0（仅存在于 colors.xml，属既有悬空资源，守门禁止其被删）；
    `tl_ink3` 全仓 43 处引用。
  - 规模：非空非注释 83/84（口径差异仅来自 XML 多行注释的一行），物理 88；未超 200 软上限，未超 500 硬上限。
  - 密钥：`gitleaks detect --no-git` 扫本片全部 7 个改动文件 → `no leaks found`，exit 0。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片不新增或删除测试断言，故无需等价覆盖说明。
  既有测试全部保留：`gradlew check` 已在本轮独立重跑为 exit 0。改动为纯资源**值**变更，行为等价性由
  「资源名/条目名集合与顺序逐项等于 HEAD」直接证明（见验收点 4 的实测），而不是由测试数量推断。
- Gaps:
  1. 实现者自报的两项「额外判断」我逐条复核并给出结论：
     - **移除 3 处机械提取残留 note（未在 Scope 里授权）→ 判为可接受、非越界**。三处都是被错挂的小节标题
       （`red-soft` 挂着「间距系统…」、`r-full` 挂着「字号系统…」、`sh-2` 挂着「字体：全站楷体…」），
       只动 `note` 字段，**不牵动任何 token 名与数值**（已用名/值集合独立核对）；其中 `sh-2` 那条含
       「全站楷体」这一已被本次变更取代的**当前态表述**，若保留则与文档一致性要求直接冲突，属必须清掉。
       唯一的流程瑕疵是它只写进了 design-system 的变更记录、没写进本任务的 Scope 段——建议后续把这类
       「为满足文档一致性而必须顺带清理的旧表述」预先写进 Scope，避免事后解释。
     - **4 个柔色 token + hairline「保持仓库现值」→ 前半句成立，后半句**（注：指「hairline 保持仓库现值」）**不成立**：
       hairline 其实**已吸收新稿值** `rgba(26,40,32,.07)`（原型与 tokens.json 都改了），它只是非 hex、
       不进 `colors.xml`，与柔色 token 是两回事——实现者的措辞把两类混为一谈。
       4 个柔色 token 保持不透明值**是遵守硬约束、不是「该吸收的没吸收」**：`colors.xml` 的 15 个资源名与
       数量被冻结，而 rgba 的合成结果**依赖承载背景**（amber-soft 压在 bg 上是 #EEE1D4、压在 surface 上是
       #F5EAE0），根本不存在能同时表示两者的单个不透明资源；且我复算确认按新稿压平会把 `amber_soft` 上的
       ink3/amber 打到 4.06/4.40，**直接击穿本任务自己的验收点 3**。所以「保持」是唯一同时满足硬约束与
       验收线的解，代价（与新稿极小色差、且是背景相关的近似）已登记在 tokens.json note + 变更记录 + 残余风险 2。
  2. **RED 的证明力（实现者的自述我不同意其强度）**：G0 是在**临时镜像树里重放**出来的构造状态，不是我
     亲眼在真实工作区看到的瞬时失败，因此它**不能证明时间顺序上「测试先失败」**——这一点完全依赖实现者自述。
     它**能**证明的是：守门脚本确实抓得住「上游改了、下游没同步」这一情形。我用同一套解析器把
     「工作区原型 + HEAD 的两份下游」喂给 `compare()`，**逐字复现了 G0 的 23 项漂移清单与 42/42/15 的解析计数**，
     所以这条证明力是真实可复现的，而不是一个随便构造的场景；再叠加我重跑 `--selftest` 的 10/10 双向断言，
     「判据不是死代码」已独立成立。结论：按本任务**写明的验收标准**（它们锚定在 `--selftest` 而非 RED 时序上）
     证据充分，判为通过；但「先失败」这一条在证据上只能算自述级，若后续任务要做真实时序 RED，
     需在改向下游**之前**把守门输出落盘，而不是事后重放。
  3. `dimens.xml` 的字号/圆角/字体**没有脚本兜底**（守门只覆盖颜色与 token 名），本片靠 `git diff` 逐行核对
     成立；这是本任务已登记的结构性缺口，下一次有人改 `dimens.xml` 时同样只能靠人工核对。
  4. 原型 HTML 第 2403 行的叙事段落仍写「全站正文用霞鹜文楷（手写与印刷之间的楷体）」、第 15 行仍有楷体
     加载注释——这是**已被新稿取代的当前态表述**。它不在 `:root` 内，本任务的 Scope 与「Historical/PRD
     handling」已明确把它交给 051 加指向性批注，故**不算本片未达**；但它确实还留在仓库里，需在 051 收口。
     我另外反查了 `#636F66`／`#B7791F`／`#8A938E`／`LXGW WenKai`／`楷体` 五个旧表述，其余命中都带
     历史标注（android/README.md 的「历史：」句、design-system README 的 2026-09-21 变更记录、PRD 修订
     历史行、迁移方案对照表、守门脚本的 selftest 合成夹具），无未标注的当前态残留。
  5. 证据目录留下了 3 个未 `.gitignore` 的临时文件：`artifacts/evidence/_t050_gen.py`、
     `_t050-nokotlin.log`、`_t050-nokotlin.utf8.txt`。生成脚本本身（当场捕获输出+退出码、可重跑）是好实践，
     但这三个 scratch 文件会被一起提交成噪声，建议移入被忽略目录或删除后再提交。
- Risks:
  1. `green_soft` / `red_soft` 上的 ink3/blue/amber 实测 4.40–4.48，**低于 4.5**，只是当前没有任何布局这样用。
     本任务与守门都不会拦住未来第一次这么用的布局——判据「其余 tl_* 作为文字色时同样 ≥ 4.5」的承载背景边界
     靠约定而非机械约束维持。建议把「柔色底只用 green_deep/red 系文字」写进 `android/README.md` 的对比度条目。
  2. 品牌衬线 serif 走 ROM 回退栈，各厂商中文衬线字形不一致（用户已接受，本片登记在案）。
  3. 规模估算 30 行 vs 实测 83/84 行（2.8 倍）。未触发任何阈值（≤200 为警告线），不影响判定；但说明
     「只改值」类任务的估算容易漏算镜像里的说明性行，下次估算宜按文件粒度而非「值行」粒度。
  4. `tl_blue` 是定义但零消费的资源，却被守门的「不得有悬空资源」规则强制保留（删除会让守门转红）。
     本片不改名不改数量，属既有状态；但它让「未使用资源」无法通过删除来自然清理，需在 052+ 的取舍里显式处理。
- 新型错误（建议追加 AGENTS.md 失败案例库）:
  1. **对比度审计只覆盖 XML 可见配对，漏掉 Java 自绘面**：本片 `HomeScreen`/`SettingsScreen` 用
     `setTextColor`+`setBackgroundTintList` 在代码里组出「文字色 on 承载背景」配对，
     `TreeCanvasView` 还有描边/填充图形。实现者的配对表全是 XML。本轮我补做后全部达标，但审计模板
     应把「Java 侧动态 tint/自绘」列为必查项，否则下一次会在代码里悄悄产生不达标配对。
  2. **「保持仓库现值」的自述清单把两类东西混为一谈**：把「因 rgba 值域限制而保持」的 4 个柔色 token
     与「根本不属于 colors.xml 的 rgba token」的 hairline 并列成一句话，导致 hairline 实际已吸收新稿值却被
     自报成「保持原值」。自述与 diff 不符，verifier 必须回 diff 逐项核对，不能采信分类句。

## Handoff notes

- Completed: 新稿与工程的配色差异已逐值实测；两处对比度回退已定位并复算修正值；守门脚本的规则（含资源名与 token 名双向约束、`MIN_TOKENS` 下限）已读通。
- In progress: 待派工。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 本片无依赖，可立即开工。完成后 052、056、057、058 均可启动；052 另需 051 完成。
- Watch-outs: (1) 只改值，绝不改名；(2) 改完必须跑 `--selftest`，否则无法排除判据退化成死代码；(3) `dimens.xml` 没有脚本兜底，别假设构建通过就等于度量正确；(4) 本片与 052–055 争夺同一批 `res/values/` 文件，必须串行。
