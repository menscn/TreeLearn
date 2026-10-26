# Filename: T-2026-10-25-retire-app-css-093.md
# Codex Harness Task: 退役死样式表 app.css 与 --kai 的假消费者（X4）

id: T-2026-10-25-093
title: 退役 app.css 与 --kai（X4）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 60
shared_code_touched: true
depends_on: [T-2026-10-25-ui-asset-parity-judgment-088.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 X4——`app.css` 425 行仍被 `main.tsx` 无条件打进离线包，且替已放弃的 `--kai` 楷体身份维持着最后一个假消费者。
- Problem/background: 088 的 A3 首跑判出 30 个无消费者类名（`app.css` 29 个 + `glass.css` 的 `rec-tag`）。死表在浏览器里永远不报错，肉眼也看不出；它同时让任何「grep 字体存活率」的守门把 `--kai` 当成活的。
- Users/scenarios: 下一位改样式表、查字体栈、或在离线包里找体积的人（含 AI）。
- Expected outcome: `app.css` 与 `main.tsx` 的 import 一起删除；仍在工作的四条基础规则搬进新的 `base.css`；`glass.css` 的 `rec-tag` 退役且注释不再指向已不存在的文件；`--kai` 从 token 链中删除（token 数 72 → 71）。
- Core flow and reversibility: 删表、搬规则、改 import、改注释、删 token 并重生成 `tokens.css`。回退＝恢复 `app.css` 与 import 并还原 `--kai`，但 A3 会重新判红。
- Inputs/outputs: 输入是 A3 的 30 条名单与抽样反查；输出是 `base.css`（47 行）与 `glass.css` 的注释改写。
- State and exception handling: 搬规则时只搬「仍在工作」的四条；颜色与字号仍只读 tokens，不新增数值。
- Constraints and trade-offs: 整表删除而不是逐条清（086 的 AI 自主决定 ④）；样板面允许少量重复，换掉一整张化石表更划算。
- AI recommendation and rationale: 抽样 12 个类名里 10 个零消费者，剩下 2 个（`html/body` 撑满、`.icon` 基准）已在活样式表里有等价规则——先确认「删了会坏什么」，再删整表。
- User-confirmation decisions: 22 条全做。
- AI-autonomous decisions: ① 搬四条基础规则到 `base.css` 而不是塞回 `workspace.css`；② `--brush` 保留为历史记录，只删 `--kai`；③ `glass.css` 的 `rec-tag` 与 `app.css` 同批退役，避免「同一类名两处都死」的残留。
- Acceptance evidence: A3 从「扫 8 张表 / 30 个无消费者」转绿为「扫 7 张表 / 0 个」。
- Facts: `093-red.txt` 的 A3 行点名 `app.css` 29 个；`093-green.txt` 的 A3 行「扫描 7 张样式表，显式豁免 0 条」。
- Assumptions: `app.css` 里没有正在生效但未被类名扫描覆盖的规则（如元素选择器的影响由 `base.css` 的四条承担）。
- Unknowns: 无。
- Risks: 若某个页面依赖 `app.css` 里未搬走的元素选择器，会静默失去样式；已由 30 文件 / 210 用例与人工核对四条搬运覆盖。
- Deferred: 无。
- Non-goals: 不重排活样式表；不改颜色与字号取值。
- Skipped non-critical questions and reason: 未问「要不要保留 app.css 作为参考」——版本库里本来就有，留一份活表只会让下一位继续引用它。

## Goal

把一张 425 行的死样式表与它维持的 `--kai` 假消费者一起退役，同时把仍在工作的四条基础规则搬到有名字的 `base.css`。

## Scope

### In scope
- 删除 `frontend/src/styles/app.css`；新增 `frontend/src/styles/base.css`；改 `frontend/src/main.tsx` 的 import。
- `frontend/src/styles/glass.css` 的 `.rec-tag` 与指向 `app.css` 的注释。
- `Docs/04-ux-design/design-system/tokens.json` 的 `fontFamily.kai` 与 `$note`，重生成 `tokens.css`。

### Out of scope
- `--brash`／`--brush` 的其它历史面；`pages.css` 的活规则；判据注册（→104）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 删除死码；必须保留 `html/body/#root` 撑满、body 排版基线、`.icon` 24px 基准、`.screen` 列布局，以及推荐标签在 `pages.css` 的活样式 `.suggestion-tag`。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `main.tsx` 的 import 序列；消费者四条基础规则；`glass.css` 的注释曾指向 `app.css:171`。
- Native bridge or independent-client compatibility/update requirements: 无；桌面壳随产物面自动更新。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: `--kai` 退役完成（不再是「声明放弃但仍有假消费者」）；样式表数量 8 → 7。
- Authoritative code/config/migration evidence: `tokens.json` 的 `fontFamily.$note`、`base.css` 的头注释、`glass.css` 的退役注释。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`.codex/rules/frontend.md`。
- Documentation files updated: `tokens.json` 的 `$note`（「kāi 保留不删，退役待后续清理」→「已于 2026-10-25 退役」）。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "app.css" frontend/src frontend/scripts` 无残留。
- Superseded phrases/examples reverse-searched: `glass.css` 的「文字仍由 app.css 的 green-deep 表达语义」已重写。
- `documentation impact: none` reason and evidence: 不适用。
- Historical/PRD handling: 不改写 053 的楷体退役声明历史。

## Acceptance criteria

- [x] `app.css` 与 `main.tsx` 的 import 删除；`frontend/src` 内无任何 `app.css` 引用。
- [x] A3 整跑 0 个无消费者类名（扫描 7 张表）。
- [x] `glass.css` 不再包含 `.rec-tag`，注释不再指向已删除文件。
- [x] `--kai` 从 `tokens.json`／`tokens.css` 消失，token 数 71，四方链 0 failed。
- [x] 前端全量 30 文件 / 210 用例绿。

## TDD execution record

### RED — failing test first
- Test/scenario: A3「样式表类名必须有消费者」在未删表的仓库上判红。
- Command: `python -X utf8 .codex/scripts/dev/check_ui_asset_parity.py`
- Exit code: 1
- Observed failure: `FAIL A3 样式表类名有消费者 — 扫描 8 张样式表，frontend/src/styles/app.css：29 个无消费者（app-main, app-shell, app-tab, app-tabs, appbar, appbar-brand…）`。
- Failure reason validated: 名单里的类名在 `frontend/src/**` 命中为 0（088 抽样反查 8 个全 0），所以红不是判据把活类名读成死码；红点确实在删表这一侧。
- Evidence: `artifacts/evidence/ui-readability-parity-2026-10-25/093-red.txt`。

### GREEN — minimum implementation
- Implementation: 删 `app.css`；把四条仍工作的规则搬进 `base.css`；`main.tsx` 改为引 `fonts.css` + `base.css`；`glass.css` 删 `.rec-tag` 并改写注释；`tokens.json` 删 `fontFamily.kai` 并重生成 `tokens.css`。
- Command: `python -X utf8 .codex/scripts/dev/check_ui_asset_parity.py`
- Exit code: 0
- Passing evidence: `OK A3 样式表类名有消费者 — 扫描 7 张样式表，显式豁免 0 条`（`093-green.txt`）。
- Regression evidence: 前端 30 文件 / 210 用例绿；`check_design_tokens.py` 0 failed（token 数 71）；`check_offline_bundle.mjs` violations=0（见 090 的构建读数）。

### REFACTOR — optional
- Cleanup: `base.css` 头注释记录「哪些搬了、为什么只搬这四条」，防止下一位把整表「找回来」。
- Regression evidence: 复跑 A3 仍 0 个。

### LOOP — optional
- Command and rounds: 两轮（首轮删表后 `base.css` 尚未搬全，页面高度塌；补齐后 A3 绿、用例绿）。
- Failure identity and repeat count: 单次单缺陷（搬漏 `html/body/#root` 的 100% 高度）。
- Stop condition hit: A3 绿 + 全量用例绿。
- Handoff: 104 注册 A3。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 整表删除的口径来自 086 的 AI 自主决定 ④：逐条清会留下「这条到底还活不活」的持续疑问，而四张活规则可以一句话说清。
- `--kai` 与 `app.css` 必须同批处理：只删表不删 token 会留下无消费者 token，只删 token 不删表会让 A3 继续把 `--kai` 读成活着。
- `.rec-tag` 在 `glass.css:172` 与 `app.css:268` 各声明过一次，两处都没有 TSX 消费者；活的推荐标签样式是 `pages.css` 的 `.suggestion-tag`。

## Verification report

- Verdict: PASS
- Evidence: `093-red.txt`（A3 红，8 张表 / 29 个）／`093-green.txt`（A3 绿，7 张表 / 0 个）；token 链 0 failed；前端全量绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: `morandi-material.test.ts` 断言的类名从 `glass.css` 的 `.rec-tag` 改为活样式表里的消费者；覆盖的语义（推荐标签有 tint 表面）未减少。
- Gaps: 未做逐屏截图比对「删表后没有样式丢失」；由四条搬运的人工核对 + 全量 DOM 用例承担。
- Risks: 若存在只靠 `app.css` 的元素选择器生效的样式，本片会静默移除；已逐条核对四条搬运。

## Handoff notes

- Completed: 死表与假消费者退役，A3 转绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 104 注册判据；105 重建产物。
- Watch-outs: 别把 `app.css` 从历史里「找回来」当参考——`base.css` 的头注释已经写明为什么只留四条。
