# Codex Harness Task: Web（H5）阶段 2 需求父卡（`.codex` 任务册重挂）

id: T-2026-10-18-005
title: Web（H5）阶段 2 需求父卡（.codex 任务册重挂）
template_version: 2
task_type: requirement
status: in_progress
parent_requirement: null
requirements_confirmed: true
generated_by: agent
owner: leader
owner_lane: harness
stack: frontend
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 流程资产在同一天由 `.trae/` 迁到 `.codex/`（`T-2026-10-18-treelearn-process-migration-001.md` 及其 002/003/004），该线按用户裁定「历史任务不复制」，因此 Web 复刻线在 `.trae/tasks/` 里**尚未提交**的阶段 2 任务卡（011–014）随目录一起消失，而 `frontend/` 代码与 `artifacts/` 证据不受影响。本卡把阶段 2 重新挂到 `.codex/tasks/` 下，使在飞工作重新有可校验的父需求。
- Users/scenarios: 用户要在本机打开一个与 Android 现状 1:1 的 Web（H5）界面；阶段 2 交付的是「能构建、能起、离线可用、有运行时边界」的骨架，屏幕内容属阶段 3+。
- Expected outcome: `pnpm --dir frontend build/lint/test/verify` 全绿、产物不含任何需联网加载的脚本或字体、依赖方向与文案守门机械成立，`RuntimePort` 与夹具适配器只有一份正文。
- Core flow and reversibility: 建骨架 → 端口与适配器 → 文案与时区 → 守门与 `verify` 汇总入口；全部为新增文件，回退即删除 `frontend/**` 与本任务册条目，不触碰 Android 与后端。
- Inputs/outputs: 输入 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §11 阶段 2、`Docs/04-ux-design/design-system/tokens.json`（经生成链）；输出 `frontend/` 工程、`frontend/scripts/` 守门、`artifacts/evidence/T-2026-10-18-web-frontend-slice-*-*.txt`。
- State and exception handling: 夹具缺失、格式不合格、读取失败三类失败必须互不混同；零夹具时任何屏都不许给出 ok；空集合一律判 FAIL 而不是判通过。
- Constraints and trade-offs: 只做简中、不要求第二语言齐备（沿用 2026-10-15 疑虑 4 的用户裁定）；1:1 基线只有手机档（疑虑 8 裁定）；不引入 router 等暂无消费者的依赖；范围条目数以实测清单为唯一权威，本卡不复述屏数。
- AI recommendation and rationale: 阶段 2 出口门禁本身可机械判定，先立判据再进阶段 3 逐屏校正，避免旧线「先长界面后补判据」的病因。
- User choice and custom additions: 用户选择「按 Harness 执行、一次性搞完」，路径选 A（阶段 2），并授权在确认范围内连续建片执行。
- AI-autonomous decisions: ① 不装 `react-router-dom`（消费者要到阶段六才出现）；② `index.html` 的 `<title>` 留空由文案表设置；③ 离线判定只由 `frontend/scripts/check_offline_bundle.mjs` 一处承载。
- User-confirmation decisions: 采用 Harness、选路径 A、D1 提交（已完成 `ecf0123`）。
- Acceptance evidence: 每片一份 `T-2026-10-18-web-frontend-slice-<NNN>-gate.txt`（want/got 表派生总退出码）；承重断言另配变异电池；守门均含正反双向自检与裸字面量条目下限。
- Facts: `pnpm@11.5.2`、`vite@8.3.4`、`typescript@7.0.2`、`vitest@5.0.5` 实测可安装可运行；`python .codex/scripts/validate_tasks.py` 在本次重挂前 `SUMMARY files=4 errors=0 warnings=0`。
- Assumptions: `.codex/rules/*` 与原 `.trae/rules/*` 判定口径一致（已逐文件 diff 确认，差异仅为目录名与 `harness-tasks.md` 的英文补充行）。
- Unknowns: 迁移线是否会把历史任务册重新导入 `.codex/tasks/`；若导入，本卡与 011–014 的编号需与之对齐。
- Risks: 同一工作树有两条线在飞（迁移线与本复刻线），任何提交都会把对方未提交的东西一并卷入，因此本线不做提交，只在完成时报告改动面。
- Deferred: 阶段 3 逐屏校正、平板档差异片、`backend/` 与契约差分。
- Non-goals: 不做远程部署、不做 iOS、不新增英文文案、不把 SQLite/密钥/生成权威搬进 JavaScript、不复用被删除的旧 `web/**` 代码。
- Skipped non-critical questions and reason: 未逐项追问守门命令的命名细节，因为 `pnpm verify` 的汇总口径由 014 一片收敛即可。

## Goal

在 `.codex` 任务册下恢复并连续执行 Web（H5）阶段 2：交付可构建、可离线打开、带单一运行时边界与机械守门的 `frontend/` 骨架。

## Scope

### In scope
- 子片 011（工程骨架与构建入口）、012（`RuntimePort` 与夹具适配器）、013（文案资源表与设备时区，本轮补登卡）、014（依赖方向守门）、015（文案守门与 `verify` 汇总入口）。原计划把三支守门合并在 014 一片，实际按「依赖方向」与「文案＋汇总入口」拆成两片，拆分理由与各自规模记在 014/015 卡内。
- 证据统一落 `artifacts/evidence/T-2026-10-18-web-frontend-slice-*.txt`，生成器共用一支 `artifacts/tmp/gen-fe-gate.py`。

### Out of scope
- 逐屏 1:1 校正（阶段 3 起）、`backend/`（阶段 7）、Android 侧任何代码、历史任务册的恢复。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 净新增 `frontend/**`；Android 与后端行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `frontend/src/**`，消费者 `frontend/scripts/*.mjs` 守门与 `pnpm --dir frontend` 四个入口；与 Android 无桥接（阶段七才接 `backend/`）。
- Native bridge or independent-client compatibility/update requirements: N/A（本阶段不产出跨端契约正文）。
- Behavior-neutral non-applicability reason, if applicable: 无。

## Documentation impact record

- Changed runtime facts: 研发流程入口目录由 `.trae/` 变为 `.codex/`（由迁移线负责回写）；本卡只把在飞任务状态重新落在新目录。
- Authoritative code/config/migration evidence: `git status --short` 的 `.trae/**` 删除项与 `AGENTS.md` 第 3、34 行（已写 `.codex/` 为唯一入口）。
- Documentation surfaces checked: `AGENTS.md`、`.codex/rules/{frontend,harness-tasks,regression,documentation,contracts}.md`（逐文件与 `git show HEAD:.trae/...` diff）。
- Documentation files updated: 仅 `.codex/tasks/` 下本卡与四张子卡。
- Producer/consumer/sibling/compatibility paths checked: 规则口径 diff 结果只含目录名差异，因此既有判据无需改写。
- Superseded phrases/examples reverse-searched: 本卡不复述 `.trae/tasks/` 里的旧卡内容，避免形成第二份正文。
- `documentation impact: none` reason and evidence, 若适用：不适用——任务状态目录本身就是文档面。
- Historical/PRD handling: 历史任务册按迁移线裁定「不复制」，保持仅在 git 历史中可读。

## Acceptance criteria

- [x] `.codex/tasks/` 下 005 与 011–015 通过 `python -X utf8 .codex/scripts/validate_tasks.py`（errors=0）。
- [x] 每片的 gate 产物含 want/got 期望表、位移检测与 `OVERALL_EXITCODE=`，且判定行存在性被断言（`...-011-gate.txt`、`...-013-gate.txt`、`...-014-015-gate.txt`）。
- [x] 阶段 2 出口门禁可复现：`pnpm --dir frontend verify` → `verify steps=11 ok=11 bad=0 fault=0` / `OVERALL_EXITCODE=0`，其 11 步即覆盖 build、lint、test 与四条 `check:*`（含离线判定与其自检）。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（需求卡，无被测实现）
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 需求卡只承载已确认需求与子片编排。
- User approval: N/A
- Alternative verification: 子片各自留 RED/GREEN 与门禁产物。

## Implementation notes

- 编号说明：`.codex/tasks/` 当日已有 001–004（迁移线），本线沿用 011–014 以保持既有证据文件名 `T-2026-10-18-web-frontend-slice-011-gate.txt` 不被改写（证据按当时状态归档）。
- 判据只有一处：范围条目数继续以 `Docs/04-ux-design/parity/screen-inventory.json` 的 `counts` 为唯一权威，本卡与子卡都不写屏数。

## Verification report

- Verdict: PENDING
- Evidence: 子片各自给出；本卡的复现命令为 `python -X utf8 .codex/scripts/validate_tasks.py` 与 `pnpm --dir frontend verify`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（无断言被删）。
- Gaps: ① 011–014 的旧卡正文无法逐字恢复（从未提交），重建后的记录以现存代码、产物与本轮实测为准；② 独立复核轮次仍为 PENDING。
- Risks: 迁移线若在 `frontend/**` 上做路径回写，可能与本线改动面重叠；缓解为提交前逐片核对 `git diff --name-only`。

## Handoff notes

- Completed: 阶段 2 五片（011–015）全部交付并有卡片与 gate 产物；`pnpm --dir frontend verify` 成为阶段 2 出口的唯一汇总入口且 rc=0；011 卡 Gaps ①（`file://` 双击可开）已用浏览器真实证据关闭。
- In progress: 无——阶段 2 出口已闭合，尚未进入阶段 3。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 阶段一夹具采集补齐（`Docs/04-ux-design/parity/screen-inventory.json` 全条目 → `.codex/scripts/dev/parity/screens.json` 与 `artifacts/fixtures/parity/`，台账 NO-PROFILE 归零或写明可接受偏差）→ 阶段 3 逐屏 1:1（含 `src/copy` 与 `strings.xml` 的键差分守门）→ 阶段六行为接线。
- Watch-outs: ① 本工作树同时有 Android 线在飞，提交需整树一次；② `frontend/src/styles/tokens.css` 是生成物，手改会被 `python .codex/scripts/dev/check_design_tokens.py` 判 FAIL；③ 任何写进卡片的命令必须与真正执行过的那条逐字相同；④ 阶段 2 的绿色只证明「骨架、边界与判据成立」，不证明任何一屏渲染与 Android 一致。
- 时效（2026-10-18，承 `T-2026-10-18-web-parity-fixture-capture-instrument-019.md` 与 `T-2026-10-18-web-render-wiring-walk-instrument-020.md`）：上面 Completed 那句「`file://` 双击可开已用浏览器真实证据关闭」**不成立**——同一 URL 今天用 CDP 复现得 7 条 FAIL（外部 ES 模块在 `origin=null` 下被 CORS 拦），真实闭合由 020 的单文件产物 `frontend/dist-offline/index.html` 完成，判据与可复现读数见 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` 的 2026-10-18 修订四条与 `artifacts/evidence/T-2026-10-18-web-render-walk-020.txt` 块 04/04b/05。本卡第 118 行按「历史读数不改写」保留原样，以本条为准。Next steps 的两步已交付：阶段一夹具=019（NO-PROFILE 走「写明可接受偏差」那条走法，理由在 019 卡 Gaps ①），阶段 3 逐屏渲染与换屏接线=020；仍未做的是 `src/copy` 与 `strings.xml` 的键差分守门、视觉层量测与阶段六行为接线。
