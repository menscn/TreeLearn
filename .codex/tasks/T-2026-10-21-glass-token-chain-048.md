# Filename: T-2026-10-21-glass-token-chain-048.md
# Codex Harness Task: 玻璃材质设计 token 链扩展

id: T-2026-10-21-048
title: 玻璃材质设计 token 链扩展
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-21-glass-material-requirement-047.md
requirements_confirmed: true
generated_by: agent
owner: design-system
owner_lane: contract
stack: harness
estimated_production_lines: 140
shared_code_touched: true
depends_on: [T-2026-10-21-glass-material-requirement-047.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 把用户选定的 A 晨雾玻璃 + C 1px 高光边 + 更强通透落成生产材质；本片只扩 token 链，不动界面。
- Problem/background: 现有 token 只有纸感不透明色与 3 档圆角，没有玻璃面、雾面场景、光斑、模糊/饱和与 22/28 圆角；Android 与 Web 若各自发明数值，会形成第二套视觉事实。
- Users/scenarios: 后续 049（Android）与 050（Web）在同一批 token 上消费；设计改稿只改原型 `:root` 一处。
- Expected outcome: 原型 `:root`、`tokens.json`、`colors.xml`、生成的 `tokens.css` 四方一致地新增玻璃与雾面 token；不透明 mist 子集进入 `colors.xml`，rgba 玻璃值留在原型/镜像/CSS，由 Android `glass.xml` 在 049 做 ARGB 副本。
- Core flow and reversibility: 原型 `:root` 增 token → 同步 `tokens.json` → `colors.xml` 增 4 个 mist hex 资源 → 重新生成 `tokens.css` → 四方守门。回退为按相反顺序删除新增 token 与资源并重新生成。
- Inputs/outputs: 输入是 047 记录的取值；输出是 19 个新 token（10 个 rgba 颜色、4 个 mist hex、2 个圆角、3 个材质参数）。
- State and exception handling: 只改静态设计事实；不涉及运行时状态、网络、存储或迁移。
- Constraints and trade-offs: 不改既有 token 名与值；不改 `colors.xml` 既有 16 个资源；rgba 不写入 `colors.xml`（守门只收 6 位 hex）；`tokens.css` 只能由生成器产出。
- AI recommendation and rationale: 玻璃面是共享语义（chrome/card/reader/field/edge/line/sheen），雾面场景是共享背景（4 个 hex + 3 个光斑），模糊/饱和/投影是共享参数；一次全部建模，避免两个消费者各自补值。
- AI-autonomous decisions: ① 命名统一为 `glass-*`/`glow-*`/`mist-*`；② 圆角新增 `r-xl=22px`、`r-2xl=28px`，不改旧三档；③ 投影 `glass-shadow` 只用于玻璃卡片，不复用 `sh-2`；④ mist 四色进 `colors.xml`，glass 透明色由 049 的 `values/glass.xml` 承载。
- User-confirmation decisions: 见父卡 047。
- Acceptance evidence: `check_design_tokens.py` RED→GREEN 同命令；`gen_design_tokens_css.py --check`；`--selftest`；`validate_tasks.py`。
- Facts: 原型 `:root` 当前 44 个 token；`colors.xml` 当前 16 个资源；守门按 token 名确定性映射 `tl_*`，缺失、悬空或数值漂移都判 FAIL；`tokens.css` 由 `render_css` 逐字节校验。
- Assumptions: 新增 token 都可由现有解析器读取（`VAR_RE` 支持 `%`、px、rgba 与多重投影）。
- Unknowns: Figma 画布尚未消费新 token；本轮只保证仓库内四方一致。
- Risks: 新 token 若先在 `colors.xml` 落地会被判「无 token 对应的资源」；`tokens.css` 手改会被逐字节校验拦红。
- Deferred: Figma 变量同步、暗色模式下的玻璃值。
- Non-goals: 不改任何界面、drawable、CSS 覆层或业务代码。
- Skipped non-critical questions and reason: 未追问玻璃阴影第二档；单一 `glass-shadow` 已覆盖本轮所有玻璃面。

## Goal

在原型 `:root`、`tokens.json`、`colors.xml` 与生成的 `tokens.css` 中一致地加入晨雾玻璃 token，并通过四方守门。

## Scope

### In scope
- `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `:root`：新增玻璃、光斑、雾面、圆角与材质参数。
- `Docs/04-ux-design/design-system/tokens.json`：同批 token 与用途 note。
- `android/app/src/main/res/values/colors.xml`：新增 `tl_mist_base`、`tl_mist_deep`、`tl_mist_warm`、`tl_mist_cool` 四个不透明 hex。
- `frontend/src/styles/tokens.css`：由生成器重新产出。
- `Docs/04-ux-design/design-system/README.md`：更新源行号、分组表、rgba 说明与 2026-10-21 变更记录。
- `artifacts/evidence/`（051 汇总时引用）。

### Out of scope
- Android `values/glass.xml`、drawable、布局（049）。
- Web `glass.css` 与组件（050）。
- 任何业务代码、文案、依赖或接口。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 令牌集合扩大，既有 44 个 token 的名称与值不变；新 token 只增不改。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是原型 `:root`；消费者是 `tokens.json`、`colors.xml`、`tokens.css`；`check_design_tokens.py` 是唯一判据。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: 本片只改设计事实文件，运行时界面在 049/050 才消费。

## Documentation impact record

- Changed runtime facts: 设计系统新增玻璃材质 token；`colors.xml` 资源 16→20；原型 `:root` 行号范围变化。
- Authoritative code/config/migration evidence: 原型 `:root`、`tokens.json`、`colors.xml`、`tokens.css`、`check_design_tokens.py`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`.codex/context/source-of-truth.md`。
- Documentation files updated: 设计系统 README 的源行号、分组表、rgba 规则与变更记录。
- Producer/consumer/sibling/compatibility paths checked: 生成器、守门、Android 资源名映射、前端变量消费。
- Superseded phrases/examples reverse-searched: 「44 个 token」「16 个资源」「第 22–75 行」——以后只由守门打印数量，README 不再写死当前数量。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 旧变更记录保留；新增 2026-10-21 条目，不改写历史。

## Acceptance criteria

- [ ] `python -X utf8 .codex/scripts/dev/check_design_tokens.py` 在新增 token 后、同步下游前退出码非 0（RED）。
- [ ] 同一命令在四方同步后退出码 0（GREEN），并打印新增后的 token 数与资源数。
- [ ] `python -X utf8 .codex/scripts/dev/gen_design_tokens_css.py --check` 退出码 0。
- [ ] `python -X utf8 .codex/scripts/dev/check_design_tokens.py --selftest` 退出码 0。
- [ ] `python -X utf8 .codex/scripts/validate_tasks.py` 退出码 0。
- [ ] 既有 44 个 token 的名称、值与 `colors.xml` 既有 16 个资源未被改动。

## TDD execution record

### RED — failing test first
- Test/scenario: 原型 `:root` 与 `tokens.json` 先加新 token，`colors.xml` 与 `tokens.css` 未同步，四方守门应报缺失。
- Command: `python -X utf8 .codex/scripts/dev/check_design_tokens.py`
- Exit code: 1
- Observed failure: `colors.xml 缺少资源：['tl_mist_base', 'tl_mist_cool', 'tl_mist_deep', 'tl_mist_warm']`；`tokens.css 缺少 tokens.json 中的 token`（19 个）；`tokens.css 与重新生成的结果不逐字节相同`（44 vs 63）。输出见 `artifacts/evidence/glass-material-2026-10-21/token-red.txt`。
- Failure reason validated: 失败点是下游副本尚未同步，不是解析退化——原型与 tokens.json 均解析出 63 个 token，colors.xml 仍为 16、tokens.css 仍为 44，且其余 9 项判据为 OK。

### GREEN — minimum implementation
- Implementation: `colors.xml` 新增 4 个 mist 不透明色（资源 16→20）；`gen_design_tokens_css.py` 重新生成 `tokens.css`（63 个变量）。
- Command: `python -X utf8 .codex/scripts/dev/check_design_tokens.py`
- Exit code: 0
- Passing evidence: `measured_prototype=63 measured_tokens_json=63 measured_resources=20 measured_css=63`，`judgement tally: OK=10 FAIL=0`；输出见 `artifacts/evidence/glass-material-2026-10-21/token-green.txt`。
- Regression evidence: 既有 44 个 token 与既有 16 个资源名/值未改；`gen_design_tokens_css.py --check` 退出码 0（2065 bytes / 63 tokens）；`check_design_tokens.py --selftest` 退出码 0（`token-selftest.txt`）。

### REFACTOR — optional
- Cleanup: 无重构；仅扩 token 集合并同步四方。
- Regression evidence: 同 GREEN 命令复跑即可覆盖。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 新增 token：`glass-chrome`、`glass-card`、`glass-reader`、`glass-field`、`glass-edge`、`glass-line`、`glass-sheen`、`glow-green`、`glow-sand`、`glow-white`、`mist-base`、`mist-deep`、`mist-warm`、`mist-cool`、`r-xl`、`r-2xl`、`glass-blur`、`glass-saturate`、`glass-shadow`。
- 取值以 047 的 AI-autonomous decisions 为准；rgba 的透明度写法保持两位小数（`.54` 等），不归一成 `0.54`。
- mist 四色是场景兜底色，必须是不透明 hex，才能进入 `colors.xml`。
- README 不再写死 token/资源数量，只指向守门输出。

## Verification report

- Verdict: PASS
- Evidence: RED `rc=1`（缺 4 个资源与 19 个 CSS 变量，逐字节再生 44↔63 漂移）；GREEN `rc=0`（63/63/20/63，OK=10 FAIL=0）；`--check` 与 `--selftest` 均 0。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用；未删改既有判据。
- Gaps: Figma 画布未消费新 token；本轮只保证仓库内四方一致。
- Risks: 下游若手改 tokens.css 会被逐字节校验拦红；rgba 玻璃值不进入 colors.xml，Android 副本必须走 values/glass.xml。

## Handoff notes

- Completed: 19 个玻璃/雾面/圆角/材质 token；四方同步与变更记录。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 049/050 消费新 token（可并行）。
- Watch-outs: 不要手改生成的 `tokens.css`；不要把 rgba 写进 `colors.xml`。
