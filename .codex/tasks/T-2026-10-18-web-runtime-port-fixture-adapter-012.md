# Codex Harness Task: `RuntimePort` 与快照夹具适配器

id: T-2026-10-18-012
title: RuntimePort 与快照夹具适配器
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-18-web-h5-android-parity-requirement-005.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 132
shared_code_touched: false
depends_on: [T-2026-10-18-web-frontend-skeleton-build-entry-011.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 规格 §4 要求组件层不感知自己跑在哪个适配器上，阶段 1–6 由快照夹具驱动，因此前端需要先有**一份**运行时边界正文；Parent summary 见父需求 005。
- Users/scenarios: 阶段 3 起逐屏渲染时只调用 `runtime().screens()` / `runtime().snapshot(screen)`；换宿主（阶段七的 `JvmRuntimeAdapter`）只改 `platform/` 与注入点。
- Expected outcome: `RuntimePort` 契约、`FixtureRuntimeAdapter`（含夹具信封 fail-closed 解析）、`MockRuntimeAdapter` 等价物与模块级注入点，配 11 例单元/契约用例。
- Core flow and reversibility: 新增三个源文件与一份测试；回退即删除，`main.tsx` 尚未依赖它们（013/014 才接线），因此不影响骨架构建。
- Inputs/outputs: 输入为夹具文本（阶段一采集产物的信封形状）；输出为 `SnapshotResult`（成功带信封，失败带 `kind` 与 `detail`）。
- State and exception handling: 三类失败互不混同——`unknown-screen`（名单里没有）、`malformed-fixture`（有文本不合格式）、`unreadable`（在名单里但取不到：抛异常或返回 null）；零夹具时任何屏都不给 ok。
- Constraints and trade-offs: 信封的 `payload` 刻意保持 `unknown`——逐屏数据形状属阶段一采集产物，前端先写一份就等于长出第二份契约；时间字段只接受正的 epoch 毫秒（`.codex/rules/contracts.md` 时间字段契约）。
- AI recommendation and rationale: 判据先行——先让三类失败可机械区分并做承重实测，再让屏去消费它。
- User choice and custom additions: 用户选路径 A 并要求连续执行完。
- AI-autonomous decisions: ① 夹具源用 `FixtureSource` 接口（内存实现供测试，文件系统实现留 013/阶段一）；② `createMockRuntime()` 作为 `MockRuntimeAdapter` 的最小实现，且在注释与用例里标注「不得用于任何 1:1 判定」。
- User-confirmation decisions: 阶段 2 范围与适配器矩阵口径由规格 §4 与用户选 A 确认。
- Acceptance evidence: `artifacts/evidence/T-2026-10-18-web-frontend-slice-012-gate.txt`（4 块）与 `...-012-red.txt`（2 块）。
- Facts: `pnpm --dir frontend test` 实测 `Test Files 1 passed (1)` / `Tests 11 passed (11)`；变异电池 3 条全部逐条归因成立。
- Assumptions: 阶段一夹具将按本信封形状产出；若采集器给出别的字段名，改的是这一处解析正文。
- Unknowns: `schema_version` 的取值域与演进规则（规格 §9 归档格式同类问题），留待阶段一定义。
- Risks: 端口若被组件绕过（直接 import 夹具文件），1:1 判定的可换宿主性失效；由 014 的依赖方向守门拦截 `src/**` 对 `src/fixtures` 的直读。
- Deferred: 文件系统夹具源、逐屏 payload 形状。
- Non-goals: 不实现服务适配器、不做任何屏内容、不引入状态管理库。
- Skipped non-critical questions and reason: 未追问是否需要 `AbortSignal`（阶段六回放流式事件时再定，现在没有生产者）。

## Goal

前端只经一份 `RuntimePort` 读取快照夹具，且「屏不存在 / 夹具不合格式 / 夹具取不到」三类失败在判决上互不混同。

## Scope

### In scope
- `frontend/src/platform/RuntimePort.ts`、`FixtureRuntimeAdapter.ts`、`runtime.ts`。
- `frontend/test/platform/FixtureRuntimeAdapter.test.ts`（11 例）。

### Out of scope
- 文案与时区（013）、守门与 `verify`（014）、真实夹具目录的读取实现。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 净新增；骨架的构建与类型检查行为不变（回跑 011 门禁 `OVERALL_EXITCODE=0` 为证）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 = `parseEnvelope`（唯一解析点，`RuntimePort.ts` 内）与 `FixtureRuntimeAdapter.snapshot`（两个「取不到」入口：`read()` 抛异常、`read()` 返回 null）；消费者 = `test/platform/FixtureRuntimeAdapter.test.ts`，跨入口比对断言同时喂这两条入口。
- Native bridge or independent-client compatibility/update requirements: 不适用——本端口是前端内部边界，阶段七的服务适配器实现同一 `RuntimePort`，届时契约差分归 014 之后的服务片。
- Behavior-neutral non-applicability reason, if applicable: 无。

## Documentation impact record

- Changed runtime facts: 新增前端测试入口 `pnpm --dir frontend test`（`package.json` 的 `test` 脚本在 011 已声明，本片是其第一个消费者）。
- Authoritative code/config/migration evidence: `frontend/src/platform/RuntimePort.ts` 的 `FixtureEnvelope` 与 `parseEnvelope`；`.codex/rules/contracts.md` 的时间字段契约。
- Documentation surfaces checked: 规格 §4 适配器矩阵、`.codex/rules/frontend.md` 的依赖方向、`.codex/rules/contracts.md`。
- Documentation files updated: 仅本卡；规格 §4 的矩阵原文未改写（本片是其首个落地，不与原文冲突）。
- Producer/consumer/sibling/compatibility paths checked: 两处「取不到」入口逐条比对；`screens()` 的排序契约由用例钉住。
- Superseded phrases/examples reverse-searched: 未在文档/代码里复制信封字段表，README 不写第二份正文。
- `documentation impact: none` reason and evidence, 若适用：不适用。
- Historical/PRD handling: 本片不引用被删除旧线（`35bb6ce` 之前的 `web/**`）的任何类型。

## Acceptance criteria

- [x] 11 例全部通过且真实执行到三条失败分支：`pnpm --dir frontend test` rc=0（门禁 block 01；2026-10-20 全量复跑 `FixtureRuntimeAdapter.test.ts (11 tests)` 仍绿）。
- [x] 承重实测：拆掉「epoch 毫秒守卫 / null-取不到降级 / 必需字段存在性」任一条，对应用例必须转红且新红集合恰好等于申报集合（门禁 block 03，`unattributed=[]`；证据 `artifacts/evidence/T-2026-10-18-web-frontend-slice-012-gate.txt` 的 `mutations declared=3 floor=3 unattributed=[] instrument-faults=0`）。
- [x] 时间字段不接受无 offset 的本地串；`device` 为空判 malformed（采集环境是覆盖面的一个维度；对应用例在 11 例集合内）。
- [x] 零夹具（`screens()==[]`）时 `snapshot()` 永远不给 ok，且本卡的覆盖结论指向台账而非端口自述（`bundledFixtures.test.ts` 3 例持续覆盖）。

## TDD execution record

### RED — failing test first
- Test/scenario: 把 `frontend/src/platform/` 整目录移开后运行同一套用例（移开—还原实验；本片实现与用例同段写完，非时间顺序的首次失败）
- Command: pnpm --dir frontend test
- Exit code: 1
- Observed failure: Cannot find module '../../src/platform/FixtureRuntimeAdapter'，且 `Tests  no tests`
- Failure reason validated: 是——红在「被测实现不存在」本身；还原后同一命令转绿（`artifacts/evidence/T-2026-10-18-web-frontend-slice-012-red.txt` 块 02），移开前后按字节快照一致（`还原 byte-exact=True`）

### GREEN — minimum implementation
- Implementation: `RuntimePort.ts` 契约与 fail-closed 解析、`FixtureRuntimeAdapter.ts` 两入口 + `memoryFixtureSource` + `createMockRuntime`、`runtime.ts` 注入点
- Command: pnpm --dir frontend test
- Exit code: 0
- Passing evidence: 门禁产物 block 01 的 `Test Files  1 passed (1)` 与 `Tests  11 passed (11)`，判决行存在性由 want/got 表断言
- Regression evidence: 同产物 block 02（`pnpm --dir frontend lint` rc=0）、block 03（变异电池 `new-red==declared:True` ×3、`restored suite rc=0`、`OVERALL_EXITCODE=0`）、block 04（字节卫生双向样本）、`moved during this run=0`

### REFACTOR — optional
- Cleanup: 两条由承重实测逼出的加固——① 跨入口比对补入「在名单里但 `read()` 返回 null」这条支路（M2 首轮只让一条用例转红，说明「三类互不混同」断言当时并未覆盖该生产者）；② 必需字段用例改为点名「缺少必需字段」这一层（M3 首轮红仅由 `payload` 撑起，其余三个字段是被类型检查顺带拒掉的）
- Regression evidence: 加固后重跑电池，M2 的新红集合等于申报的两条；`python -X utf8 artifacts/tmp/gen-012-mutation.py` rc=0

### TDD or sizing exception — only with prior user approval
- Exception type: none
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 规模判定值 132，逐个文件（不绑目录）：`RuntimePort.ts` 69、`FixtureRuntimeAdapter.ts` 49、`runtime.ts` 14。命令：`python -X utf8 .codex/scripts/dev/count_production_lines.py frontend/src/platform/RuntimePort.ts frontend/src/platform/FixtureRuntimeAdapter.ts frontend/src/platform/runtime.ts`。
- 变异电池的判决名解析器只认 `FAIL <文件> > <describe> > <用例>` 这一行取末段，并自带双向样本（两行 FAIL 解析出 2 名、绿输出解析出 0 名）；颜色由 `NO_COLOR`/`FORCE_COLOR` 关掉，否则 ANSI 码会把判决行切成两段。
- 电池就地改写生产文件并 `rb`/`wb` 按字节还原：本片第一次实现用的是文本模式，真实把两份 `.ts` 写成 CRLF（「还原成功」而字节已变），已改回并记入失败案例库待办。
- 一次未解释的 2 字节位移：`RuntimePort.ts` 在两次「字节快照一致」的运行之间末尾多出 `\r\n`（3332→3334 字节），成因未定位；处置不是解释它而是让它不可能再静默——门禁新增 block 04 的字节卫生判定（CR 与 UTF-8 各自成判据、并配合成正控），当场可抓。

## Verification report

- Verdict: PASS（独立复核，2026-10-20：四条验收全部有当期读数；端口已被阶段 3 的 13 屏真实消费，交互证据见 020 的双浏览器走查。）
- Evidence: 单元/契约证据 = 11 例真实执行（含三条失败分支与两种「取不到」生产者；`artifacts/evidence/web-parity-2026-10-20/frontend-gates.txt` 的 `FixtureRuntimeAdapter.test.ts (11 tests)`）；启动证据 = 011 门禁与本期 `verify` 16 步 rc=0；承重证据 = `artifacts/evidence/T-2026-10-18-web-frontend-slice-012-gate.txt` 的变异电池（`unattributed=[]`）；真实交互证据 = Edge/Chromium 各 24 步、29 条判定全过（`artifacts/evidence/web-parity-2026-10-20*/…-walk.txt`）。环境：本机 Windows / node 24.18 / vitest 5.0.5。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言被删；两条断言被加固（见 REFACTOR），加固方向是让证据落到真正被测的那一层。
- Gaps: ① 文件系统夹具源已由 019 落地（`frontend/src/platform/fixtures` + `sync_fixtures --check`），本项关闭；② `schema_version` 演进规则仍未定义，当前 13 组夹具同为现版本，升级时需补映射；③ 独立复核轮已由本期 verifier 完成，结论即本行 Verdict。
- Risks: 组件层若直接读夹具文件绕过端口，本卡的「单一边界」结论失效——该风险已由 014 的依赖方向守门机械挡住（core 整跑 `check_dependency_direction` 绿，注入反向 import 判红）。

## Handoff notes

- Completed: 端口契约、夹具适配器（两类失败生产者）、注入点、11 例、承重电池、RED 复现实验、门禁产物。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 013 文案资源表与设备时区模块；014 三支守门 + `verify`，并把「绕过端口直读夹具」列为方向守门的必测样本。
- Watch-outs: ① 变异电池与门禁共用生产文件，重跑任一支之前确认另一支没在跑（同目录并发写会互相踩）；② 卡片里的命令必须与执行过的逐字相同，改动电池后请重跑门禁而不是沿用旧产物；③ 电池申报的红是**用例名**，改名会让申报失配（这是设计如此，不是缺陷）。
