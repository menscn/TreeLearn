# Filename: T-2026-10-25-forest-info-density-103.md
# Codex Harness Task: 森林的数字进标题行、撤掉页脚与罗盘、等高线抬到可读档（C1 + WF2 + WF3）

id: T-2026-10-25-103
title: 森林信息进标题行、罗盘退役、等高线可读（C1 + WF2 + WF3）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: web-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 110
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 C1（重要）——森林隐喻到位但信息没进来，全场唯一量化是 11px 计数；WF2（优化）——右上角圆圈里的「N」无解释、无 hover、无 aria；WF3（优化）——等高线压到几乎看不见，与项目自己量过 mist-base 的尺子不自洽。
- Problem/background: 树数与段枝总数写在屏幕最底端的一行小字里，视线要在「看树」与「读数字」之间来回；右上角罗盘只画一个「N」，没有说明也没有提示，读起来像一个坏掉的按钮；等高线的 `.10`／`.06` 在白底上几乎不可见，隐喻退化成空白。
- Users/scenarios: 打开「我的森林」回顾学习轨迹的人。
- Expected outcome: 树数与段枝总数跟在副标题后（`一题一树，慢慢长 · 2 棵树 · 3 段枝`）；页脚撤掉；罗盘连同 `cd_forest_compass` 文案一起退役；等高线远景 `.30`、近景 `.42`；提示胶囊只在未选中树时出现；名牌降到 400 字重与 ink2、8 字后截断。
- Core flow and reversibility: 改 `ForestPage.tsx`、`ForestScene.tsx`、`forest.css`、`zh-CN.ts` 与 `ForestWeb.test.tsx`。回退＝恢复页脚与罗盘、把透明度改回 `.10`／`.06`。
- Inputs/outputs: 输入是评审板 C1／WF2／WF3；输出是标题行数字、无罗盘、两档等高线透明度、条件提示。
- State and exception handling: 没有主题时不画数字（`topics.length === 0` 时只留副标题）；任一段枝数读不到时只写树数，不写总和；学习库读不到时仍是错误态，不画无效重试。
- Constraints and trade-offs: 等高线透明度是**未经真机复测**的保守值（`.30`／`.42`），先按板上可读性取值。
- AI recommendation and rationale: 数字进入标题行是「一个屏幕只留一处量化」；罗盘没有任何指路功能，退役比补 aria 更诚实；等高线抬亮是让隐喻可见的最小手段，不新增装饰。
- User-confirmation decisions: 22 条全做；方向题（C1）现在就改，取保守值。
- AI-autonomous decisions: ① 页脚整段删除而不是移到别处；② 名牌 `max-width: min(8em, 220px)` 且 8 字截断；③ 提示胶囊在选中树后退出（纸条已说明该树）；④ `fs-contour-faint` 重命名为 `fs-contour-near`，避免「faint」继续描述已不存在的低透明档。
- Acceptance evidence: `ForestWeb.test.tsx` 四条（数字在标题行、提示与纸条、缺失段枝时省略、页脚撤掉），先红后绿。
- Facts: `ForestPage.tsx:52`（`subtitleCounts`）、`:66-68`（标题行数字）、`:101`（条件提示）；`ForestScene.tsx:259`（`fs-contour-near`）；`forest.css:121`（`.forest-count`）、`:170-181`（两档透明度）、`:271-292`（名牌）、`:295`（提示注释）；测试 `ForestWeb.test.tsx:120`、`:194`、`:203`、`:206`。
- Assumptions: `topics` 的 `branchCount` 可缺省；`null` 表示读不到，而不是 0。
- Unknowns: 真机上 `.30`／`.42` 在两档绿色底上的实际对比度未复测。
- Risks: 等高线过亮会与树的填色抢注意力；如果真机反馈如此，优先把近景档降回 `.36`，而不是整体回退到 `.10`。
- Deferred: 归档数与连续天数仍不显示——Web 没有数据来源，写 0 是假话。
- Non-goals: 不改树的布局算法、选中逻辑、纸条动作与森林以外的页面。
- Skipped non-critical questions and reason: 未问罗盘要不要改成可点指南——它本来没有目的地方向，补交互是给装饰发明用途。

## Goal

让森林的数字与名字在同一视线行、撤掉无意义的罗盘与页脚，并把等高线抬到可读档位。

## Scope

### In scope
- `frontend/src/pages/ForestPage.tsx`、`frontend/src/components/ForestScene.tsx`、`frontend/src/styles/forest.css`、`frontend/src/copy/zh-CN.ts`。
- `frontend/test/pages/ForestWeb.test.tsx` 的四条判据。

### Out of scope
- 树的几何与选中算法；纸条动作；森林以外的页面。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 数字从页脚移到标题行；罗盘删除；等高线两档变亮；提示条件化。必须保留：一题一树的渲染、点树出选中标记与纸条、纸条的段枝数与当前人设、箭头进入主题、空态与错误态的区分、森林只读（不改工作区状态）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `ForestPage`／`ForestScene` 与 `forest.css`；消费者侧栏森林入口、路由与 `personaFor`；无平台分支。
- Native bridge or independent-client compatibility/update requirements: 无；桌面壳复用同一构建。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 森林只有一处量化，位于副标题行；右上角不再有罗盘；等高线有远景／近景两档可读透明度。
- Authoritative code/config/migration evidence: `ForestPage.tsx:52-68`、`:101`；`forest.css:121`、`:170-181`；`zh-CN.ts` 删除 `cd_forest_compass`。
- Documentation surfaces checked: `.codex/rules/frontend.md`、`.codex/rules/ux-design.md`（无新 token）；`frontend/README.md`（不述森林内部）。
- Documentation files updated: 无需改文档；等高线的「未经真机复测」写在 `forest.css` 注释里。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "forest-compass\|cd_forest_compass\|forest-foot" frontend/src` 无命中；`forest-hint` 只剩条件渲染一处。
- Superseded phrases/examples reverse-searched: 旧注释「提示胶囊与罗盘」改为「提示胶囊（罗盘已撤）」。
- `documentation impact: none` reason and evidence: 页面内部信息落点与视觉值，无契约变化。
- Historical/PRD handling: 不改写 070 森林线的实现记录；本片按 2026-10-25 板重排其信息落点。

## Acceptance criteria

- [x] 有主题时副标题行写作「一题一树，慢慢长 · N 棵树 · M 段枝」；页脚节点不存在。
- [x] 段枝总数在任一主题读不到时省略，只留树数；没有主题时只留副标题。
- [x] 罗盘 DOM 与 `cd_forest_compass` 文案都不存在；提示胶囊只在未选中树时渲染。
- [x] `.fs-contour` 为 `.30`、`.fs-contour-near` 为 `.42`；名牌 400 字重、ink2、`min(8em, 220px)`。
- [x] 森林只读、空态与错误态的既有判据保持绿。

## TDD execution record

### RED — failing test first
- Test/scenario: 四条——数字与副标题同在一行；选中后出现纸条且提示退场；缺失段枝时只写树数；页脚节点被删除。
- Command: `cd frontend && npx vitest run test/pages/ForestWeb.test.tsx`
- Exit code: 1
- Observed failure: `Tests 4 failed | 5 passed (9)`——`expected '一题一树，慢慢长' to be '一题一树，慢慢长· 2 棵树 · 3 段枝'`、`expected <p class="forest-hint" …> to be null`、`expected null to be '· 2 棵树 · 3 段枝'`（`ForestWeb.test.tsx:129`、`:168`、`:200`）（`103-red.txt`）。
- Failure reason validated: 四条红分别落在「数字位置」「提示退场」「缺失时省略」「页脚撤掉」；其余五条（入口卡、纸条人设、空态、错误态、只读）在红态即绿，说明本片没有动它们的语义。

### GREEN — minimum implementation
- Implementation: 数字拼进副标题；页脚与罗盘删除；提示条件化；等高线两档抬亮并重命名；名牌降字重与限宽。
- Command: `cd frontend && npx vitest run test/pages/ForestWeb.test.tsx`
- Exit code: 0
- Passing evidence: `Tests 9 passed (9)`、`EXITCODE=0`（`103-green.txt`）。
- Regression evidence: 前端全量 30 文件 / 210 用例绿（`frontend-full-suite.txt`）。

### REFACTOR — optional
- Cleanup: 删除 `footSeparator` 与 `footnote` 变量；`fs-contour-faint` 改名为 `fs-contour-near`。
- Regression evidence: 同一条命令保持绿。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿。
- Failure identity and repeat count: 四条各红一次。
- Stop condition hit: 9 条全绿。
- Handoff: 等待用户过目森林截图。

### TDD or sizing exception — only with prior user approval
- Exception type: 方向题例外（无机械判决）
- Reason: 等高线「可见到什么程度」没有可自动判定的阈值；本片按保守值 `.30`／`.42` 落地，并在 CSS 注释里写明未经真机复测。
- User approval: 用户已拍板「C1 现在就改，取保守值」（2026-10-25）。
- Alternative verification: 真机与桌面截图由用户过目；本卡在用户确认前保持 `verifying`，不标 PASS。

## Implementation notes

- 页脚撤掉是「一个屏幕只留一处量化」：数字在标题行已经被读到，底端再写一次只是重复。
- 罗盘退役：它没有指路能力，补 aria 只会把一个无用途的圆说得更清楚；删掉比解释更诚实。
- 提示胶囊只回答「点一棵树看看」，选中之后纸条已经承担说明，提示继续挂着只会压住纸条上沿。
- 名牌 8em 截断是保守值：CJK 一字约 1em，8 字是斜俯瞰林地能够读清的上限；真实标题仍可在纸条里读全。

## Verification report

- Verdict: IMPLEMENTED — 等待用户过目截图（方向题，不标 PASS）
- Evidence: `103-red.txt`（4 failed / 9 / EXITCODE=1）、`103-green.txt`（9 passed / EXITCODE=0）；前端全量绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: 页脚断言改为「页脚节点不存在」；数字仍被断言，只是换了位置。
- Gaps: 未做真机／桌面截图核验等高线对比度与纸条遮挡；这是本卡保持 `verifying` 的原因。
- Risks: 等高线可能在真机上偏亮，与树填色抢视线；回退手段是只降近景档。

## Handoff notes

- Completed: 数字进标题行、页脚与罗盘退役、等高线两档、条件提示、名牌降重。
- In progress: 等待用户对 C1 截图方向的确认。
- Blocker: 用户过目（方向题例外）。
- Unblock condition: 用户确认截图方向后把状态翻 `verified`，并补一次 `metrics_report.py --write`。
- Next steps: 105 的统一截图。
- Watch-outs: 不要以「装饰需要」为由把罗盘加回来；也不要整体回退 `.10`／`.06`。
