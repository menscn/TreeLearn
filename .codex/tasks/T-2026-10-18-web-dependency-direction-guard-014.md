# Codex Harness Task: `frontend/` 依赖方向与端口边界守门

id: T-2026-10-18-014
title: frontend 依赖方向与端口边界守门
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-web-h5-android-parity-requirement-005.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 144
shared_code_touched: false
depends_on: [T-2026-10-18-web-runtime-port-fixture-adapter-012.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 阶段 2 要交付「可构建、可离线打开、带单一运行时边界与机械守门」的 `frontend/` 骨架；父需求见 `T-2026-10-18-web-h5-android-parity-requirement-005.md`。
- Problem/background: 012 卡把「组件层绕过 `RuntimePort` 直读夹具」列为风险并写明「该风险由 014 的依赖方向守门承担，尚未落地前不能宣称已被机械挡住」。本片就是解除那句话的那片。
- Users/scenarios: 阶段 3 起逐屏写代码时，反向 import 与越出 `src/` 的取数必须在 `pnpm lint` 当场变红，而不是等 1:1 判据被污染后回溯。
- Expected outcome: 一条按当前树真实判定的方向守门——分层名单单向、入口文件白名单、越出 `src/` 即违规——含正反双向自检与裸字面量下限。
- Core flow and reversibility: 净新增一个脚本并接入 `lint`；回退即删除脚本与 `package.json` 里的那一段。
- Inputs/outputs: 输入规格 §2 的分层方向与 011 卡固定的目录名单；输出 `frontend/scripts/check_dependency_direction.mjs` 与 `pnpm lint` 的一条判据。
- State and exception handling: 扫描根缺失、可判文件低于下限、自检用例低于下限三类都判 3（仪器故障），不判 1，更不判 0。
- Constraints and trade-offs: 名单只存本文件一处（任务卡与规格都指向它）；包名 import 不在本片判定范围，交给离线守门与依赖清单。
- AI recommendation and rationale: 判据先行——先让「绕过端口」成为一条会当场变红的机械事实，阶段 3 的绿色结论才有承载物。
- User choice and custom additions: 用户 2026-10-18 要求按 Harness 一站到底执行、过程中不再确认；本片拆分（014 方向守门 / 015 文案守门与 `verify`）由 AI 按 200 行规模口径自主决定。
- AI-autonomous decisions: ① 把原 014 一片拆成 014/015 两片以守住规模口径；② `ENTRY_ALLOWLIST` 只点名 `main.tsx`，不用通配；③ 越出 `src/` 的判定按 posix 归一后的前缀 `../` 而不是按字符串包含。
- User-confirmation decisions: 阶段 2 范围与「一次性搞完」沿用父需求 005 的确认。
- Acceptance evidence: `artifacts/evidence/T-2026-10-18-web-frontend-slice-014-015-gate.txt` 块 01/02。
- Facts: 当前树实测可判文件 9 个、findings 0；自检 10 例全绿；`pnpm --dir frontend verify` 11 步 OVERALL_EXITCODE=0。
- Assumptions: `copy` 归最下游层（只有 hooks/utils 及以上消费它）；阶段 3 若出现新层目录，改的是本片常量而不是任务卡。
- Unknowns: `store/` 与 `api/` 的实际消费者何时出现（阶段 6/7），本片只固定方向不固定内容。
- Risks: 用正则解析 import 而非 AST，极端写法（拼接的动态 import）可能漏判；缓解是漏判方向为「少红」，由 `--selftest` 的动态 import 用例托底。
- Deferred: 阶段 8 的 PC 外壳改动面守门（规格 §11 要求「不得触碰组件层」）。
- Non-goals: 不做 ESLint 接入、不引入打包期依赖分析、不改任何 `src/**` 的现有 import。
- Skipped non-critical questions and reason: 未追问是否把 CSS import 也纳入方向判定——CSS 只从 token 链来，已由设计 token 守门覆盖，重复判定即第二份正文。

## Goal

`frontend/src/**` 的分层方向与「夹具只能经 `platform` 端口取」成为一条可当场复算的门禁，且它自己的正控/反控都在自检里真实执行。

## Scope

### In scope
- `frontend/scripts/check_dependency_direction.mjs`（判决 + `--selftest`）。
- `frontend/package.json` 的 `lint` 链接入该守门、新增 `check:dependency-direction`。

### Out of scope
- 文案守门与 `verify` 汇总（015）、任何屏内容、`backend/`。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: `pnpm lint` 从「只做类型检查」变成「类型检查 + 三支静态守门」；类型检查本身的行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `check_dependency_direction.mjs:scan()`；消费者 `frontend/package.json` 的 `lint` 与 `verify.mjs` 的步骤表；与 Android 无桥接。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason, if applicable: 无。

## Documentation impact record

- Changed runtime facts: `pnpm --dir frontend lint` 的判定面扩大；新增 `pnpm check:dependency-direction`。
- Authoritative code/config/migration evidence: `frontend/package.json` 的 `scripts` 段与守门文件头注。
- Documentation surfaces checked: 规格 §2（分层方向）、`.codex/rules/frontend.md`（依赖边界那条）、011 卡的层级目录名单。
- Documentation files updated: 本卡；层级名单不在文档复述，只由守门持有。
- Producer/consumer/sibling/compatibility paths checked: `verify.mjs` 的步骤表逐条点名四支守门，无按文件名子串的排除。
- Superseded phrases/examples reverse-searched: 012 卡「尚未落地前不能宣称已被机械挡住」那句由本片转成事实，未改写其历史正文。
- `documentation impact: none` reason and evidence, 若适用: 不适用——新增门禁命令属当前态事实。
- Historical/PRD handling: 不恢复 `.trae/tasks/` 的旧 014 卡（迁移线裁定），本片按现存代码与实测重建。

## Acceptance criteria

- [x] 守门对当前树判 rc=0 且打印判决行：`node frontend/scripts/check_dependency_direction.mjs`（证据块 01）。
- [x] `--selftest` 10 例全绿且含反向对照（去掉两处违规后同一棵树判 0）：`node frontend/scripts/check_dependency_direction.mjs --selftest`（证据块 02）。
- [x] 承重实测：注入「store→pages 反向 import」与「组件直读 `../../artifacts/fixtures`」两条违规，守门必须逐条点名并判 1；把 `src/` 清空必须判 3 而不是判 0。

## TDD execution record

### RED — failing test first
- Test/scenario: 移开—还原实验：把本片守门文件移出 `frontend/scripts/` 后重跑同一条汇总命令（本片实现与自检同段写完，非时间顺序的首次失败）
- Command: `pnpm --dir frontend verify`
- Exit code: 3
- Observed failure: want/got 表 `lint` 记 `rc=1`（`Cannot find module .../check_dependency_direction.mjs`，Node v25.9.0 抛出）、`check_dependency_direction` 的判决与 `--selftest` 两步各记 `rc=3 (fault)`，末三行为 `verify steps=11 ok=8 bad=3 fault=2` / `VERIFY FAIL` / `OVERALL_EXITCODE=3`
- Failure reason validated: 是——红在「被测实现不存在」本身，不是环境故障；其余 8 步在同一次运行里仍为绿，说明失败可归因到本片而不是全局噪声。还原后同一命令转绿（证据块 03），六个文件按 sha256 逐一 `OK`（`sha256sum -c` 全绿，含被移开的那只）

### GREEN — minimum implementation
- Implementation: `check_dependency_direction.mjs` 的 `LAYERS`/`RANK`/`importsOf`/`resolveInside`/`scan`/`verdict` 与 `--selftest`
- Command: `pnpm --dir frontend verify`
- Exit code: 0
- Passing evidence: 证据块 03 的 want/got 表：11 步全 `rc=0`、判决行全部命中、`VERIFY PASS`、`OVERALL_EXITCODE=0`
- Regression evidence: 同表含 011/012/013 的入口（build/lint/test）与其余三支守门的判决+自检；`moved during this run` 由 `verify` 不改写源码保证（它只跑命令）

### REFACTOR — optional
- Cleanup: 删除 `scan()` 里一段先 `continue` 再判 `kind` 的死分支（它会让 `external` 规格永远走不到 `inside` 判定），改为 if/else 双分支
- Regression evidence: 重构后重跑 `--selftest` 与 `verify`，两侧仍全绿

### TDD or sizing exception — only with prior user approval
- Exception type: none
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 规模判定值 144，命令：`python -X utf8 .codex/scripts/dev/count_production_lines.py frontend/scripts/check_dependency_direction.mjs`。
- 分层名单 `pages→layouts→components→hooks→store→api→platform→utils→copy` 的唯一正文在本文件 `LAYERS`；自检里另有一条用例逐字比对当前态字符串，改名单必须同时让那条用例转红，防止「守门和被检文本各说各话」。
- `SCAN_ROOT_REL` 与自检夹具根 `LITERAL_ROOT` 之间刻意不建立派生关系：若夹具根从常量派生，改动那个常量时夹具会跟着挪，正控就自证清白了（这条形状已在 013 的设备时间守门里实测过）。

## Verification report

- Verdict: PENDING（待独立 verifier）
- Evidence: 单元/契约证据 = 自检 10 例（含 3 条仪器故障分支与 1 条反向对照）真实执行；启动证据 = `pnpm --dir frontend verify` 11 步 rc=0；真实交互证据 = 见 015 卡的 `file://` 浏览器实测，本片无 UI 面。环境：本机 Windows / node 24.18 / pnpm 11.5.2。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言被删；`lint` 的断言面被扩大。
- Gaps: ① import 解析走正则，拼接式动态 import 可能漏判；② 独立复核轮未跑。
- Risks: 名单变更时若只改文档不改守门，`LAYERS` 逐字比对用例会红——这是设计如此。

## Handoff notes

- Completed: 方向守门 + 端口边界守门 + `lint`/`verify` 接入 + 正反双向自检。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: 015（文案守门与 `verify` 汇总）→ 阶段 2 出口整体收口 → 阶段一夹具采集。
- Watch-outs: ① 新增层目录要改本片常量并让逐字比对用例同步；② `pnpm lint` 现在会跑三支守门，报红时先看是哪支；③ 本片的 `--selftest` 用例数下限 8 是裸字面量，删用例不会让它变绿。
