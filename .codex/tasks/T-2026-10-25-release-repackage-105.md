# Filename: T-2026-10-25-release-repackage-105.md
# Codex Harness Task: 用本轮产物重打桌面便携 exe，并让矩阵证据按运行日分目录

id: T-2026-10-25-105
title: 桌面便携 exe 重打包与证据分日（收官片）
template_version: 2
task_type: config
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: harness
owner_lane: harness
stack: harness
estimated_production_lines: 60
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 086 线把 22 条评审发现落进 Web／Android，桌面壳是 Web 产物的唯一交付形态；产物没重打包，用户双击 exe 看到的还是改前界面。
- Problem/background: `desktop/release/Treelearn-1.0.0-portable.exe` 停在 2026-10-25 10:27（110,493,305 B），内嵌的 `app/index.html` 是 518,464 B 的改前包；同目录的 `app/index.html` 也还是旧的。第二次问题：矩阵脚本 `verify-matrix.cjs:9` 把证据目录写死成 `2026-10-24`，这一轮跑 verify 会把上一轮的 28 张 JPEG 与 `report.json` 覆盖掉——历史证据只能去 git 里捞。
- Users/scenarios: 拿到便携 exe 试用的人；以及下一轮要跑桌面验收、又不想毁掉既有截图的人。
- Expected outcome: exe 内嵌的就是本轮 5,871,820 B 的离线包（与 `frontend/dist-offline/index.html` 逐字节同 hash）；矩阵证据落在 `artifacts/evidence/desktop-shell/2026-10-27/`，`2026-10-24/` 一字节不动；exe 真实启动、窗口标题与尺寸可复核。
- Core flow and reversibility: `sync-app.mjs` → `package-win.mjs`（临时目录构建、只把成品拷回 `desktop/release/`）→ `verify-shell.mjs`（sync + smoke + 14 屏 × 2 尺寸）→ 真实启动并截图。回退＝删 `scripts/evidence-dir.cjs` 与其测试、把 `verify-matrix.cjs` 的那行改回写死目录；exe 不入库，回退只需重跑一次旧构建。
- Inputs/outputs: 输入是 `frontend/dist-offline/index.html` 与 `desktop/app/`；输出是 `desktop/release/Treelearn-1.0.0-portable.exe`、`artifacts/evidence/desktop-shell/2026-10-27/`（28 张 JPEG、`report.json`、`verify.txt`、`exe-window.png`、`exe-launch.txt`）。
- State and exception handling: 证据日期解析在 `evidenceDate()` 一处：`TREELEARN_EVIDENCE_DATE` 非空时用它复跑某一档，否则取本地当天；空字符串与纯空白按未设置处理。打包脚本任一步非零即整链失败，不写半成品。
- Constraints and trade-offs: 不新增依赖（asar 读取复用 electron-builder 已带的 `@electron+asar`）；`desktop/app/` 与 `desktop/release/` 都不入库；不改窗口参数、不改矩阵状态表。
- AI recommendation and rationale: 把「日期」收进单一函数并配 4 条测试，是因为这次覆盖历史证据的根因就是「目录是字面量」；只把字面量换成 `new Date()` 而没有测试，下次有人再写死也拦不住。
- User choice and custom additions: 用户「使用 harness 进行修改，修改完成后提交代码 push」；范围沿用 086 的 22 条全做。
- AI-autonomous decisions: ① 默认按运行当天分目录，而不是把默认值改成「今天」再留一份写死的回退；② 保留 `TREELEARN_EVIDENCE_DATE` 作为复跑口，方便对照旧档；③ 真实启动用 `PrintWindow` 截图（与 2026-10-24 那份同一手法），不引入截图依赖。
- User-confirmation decisions: 不动 `2026-10-24/` 的历史证据（项目记忆里已登记的副作用，本片把它闭合）。
- Acceptance evidence: `node --test test/evidence-dir.test.mjs` 先红后绿；`sync-app` 打印 5,871,820 B；`PACKAGE_OK ... 114592941`；从运行中的便携 exe 解出的 `app/index.html` sha256 与离线包一致；窗口 `2360x1640`（200% 缩放下 1180×820）、标题「知芽」。
- Facts: 改前 exe 110,493,305 B / 2026-10-25 10:27；改后 114,592,941 B / 2026-10-27 01:27（+4,099,636 B，与随包衬线子集 4,018,044 B 同量级）；`frontend/dist-offline/index.html` 5,871,820 B、sha256 `8dc53a86…6735`；`verify-shell` → `VERIFY_OK`，`report.json` 28 态、`failures: []`、`consoleErrors: []`，两档内容区 1167×785 / 887×586；`2026-10-27/` 1.6 MB。
- Assumptions: 便携 exe 的自解压目录就是它运行时真正加载的 `resources/app.asar`——本次在进程存活时读到的 asar 与截图同源，故按同一份产物判。
- Unknowns: 无会改变结论的未知项。
- Risks: exe 体积上涨约 4.1 MB 是随包字体的代价；`desktop/release/` 不入库，换机器需要重跑 `package:win`。
- Deferred: 便携 exe 的自动更新与签名不在本片（当前无签名证书，electron-builder 明确跳过）。
- Non-goals: 不改界面、不改窗口参数、不改矩阵的 14 个状态、不动 `2026-10-24/` 历史证据。
- Skipped non-critical questions and reason: 未问「exe 要不要压缩到更小」——体积主要来自 Electron 本体与字体子集，压缩收益与启动时间互为代价，且用户未提出体积要求。

## Goal

让用户双击拿到的便携 exe 就是本轮改后的界面，并且这一轮桌面验收的证据落在新目录、不覆盖上一轮。

## Scope

### In scope
- 新增 `desktop/scripts/evidence-dir.cjs` 与 `desktop/test/evidence-dir.test.mjs`；`verify-matrix.cjs` 改用它。
- 重跑 `sync-app.mjs`、`package-win.mjs`、`verify-shell.mjs`，真实启动 exe 并截图。
- 证据落在 `artifacts/evidence/desktop-shell/2026-10-27/`。

### Out of scope
- 界面、窗口参数、导航白名单、矩阵状态表。
- `artifacts/evidence/desktop-shell/2026-10-24/`（历史证据，保持原样）。
- exe 签名与自动更新。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只改「矩阵证据写到哪个目录」与「exe 里装哪份离线包」；必须保留窗口 1180×820 / 最小 900×620、只允许 `file://` 导航、单实例、`--smoke` 无头断言，以及矩阵原有的 7 项逐屏检查。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `desktop/scripts/sync-app.mjs`（拷包）、`desktop/scripts/package-win.mjs`（临时目录构建）、`desktop/scripts/evidence-dir.cjs`（目录）；消费者 `desktop/scripts/verify-matrix.cjs:9`（唯一读目录处）、`desktop/main.js`（加载 `app/index.html`）、`desktop/test/evidence-dir.test.mjs`。
- Native bridge or independent-client compatibility/update requirements: 无；Android 与 Web 面不在本片。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 桌面矩阵证据目录从写死 `2026-10-24` 改为「默认当天、可用 `TREELEARN_EVIDENCE_DATE` 复跑某一档」；便携 exe 更新为 114,592,941 B。
- Authoritative code/config/migration evidence: `desktop/scripts/evidence-dir.cjs`、`desktop/scripts/verify-matrix.cjs:9`、`desktop/scripts/package-win.mjs:20-40`、`desktop/package.json` 的 `build.win.icon` 与 `portable.artifactName`。
- Documentation surfaces checked: `desktop/README.md`（命令、证据路径、打包说明）、`Docs/04-ux-design/design-system/README.md`（变更记录里登记的桌面事实）、`.codex/rules/startup.md`、`.codex/rules/multi-device.md`。
- Documentation files updated: `desktop/README.md` 的证据路径与复跑开关、`Docs/04-ux-design/design-system/README.md` 的 2026-10-27 变更记录。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "desktop-shell" desktop/` 只命中 `scripts/verify-matrix.cjs` 与 `scripts/evidence-dir.cjs`；`grep -rn "2026-10-24" desktop/` 不再命中任何脚本。
- Superseded phrases/examples reverse-searched: `desktop/README.md` 原写「截图与报告在 `artifacts/evidence/desktop-shell/<日期>/`」——本片把「算法」补成可复跑的事实，未与它冲突。
- `documentation impact: none` reason and evidence: 不适用。
- Historical/PRD handling: 不复写 2026-10-24 那一轮的读数，只在新目录另记一轮。

## Acceptance criteria

- [x] `node --test test/evidence-dir.test.mjs` 4 例绿；把实现改回写死 `2026-10-24` 时 3 例红（`105-red.txt` / `105-green.txt`）。
- [x] `sync-app.mjs` 打印 `synced ../frontend/dist-offline/index.html -> app/index.html (5871820 bytes)`；从运行中的便携 exe 解出 `app/index.html` 的 sha256 与离线包同为 `8dc53a86…6735`。
- [x] `package-win.mjs` 打印 `PACKAGE_OK ... 114592941`，产物时间 2026-10-27 01:27。
- [x] `verify-shell.mjs` 三行 PASS + `VERIFY_OK`；`report.json` 28 态、`failures: []`、`consoleErrors: []`；两档内容区 1167×785 / 887×586。
- [x] 真实启动 exe：`WINDOW_OK title=知芽`、`RECT 356,92 2360x1640`、`SHOT_OK`，截图在 `2026-10-27/exe-window.png`。
- [x] `artifacts/evidence/desktop-shell/2026-10-24/` 内容未被改写（本轮未向其写入）。

## TDD execution record

### RED — failing test first
- Test/scenario: `desktop/test/evidence-dir.test.mjs` 的四条——默认按本地当天、月日补零、显式日期只用于复跑、根目录固定在 `artifacts/evidence/desktop-shell`。
- Command: `cd desktop && node --test test/evidence-dir.test.mjs`
- Exit code: 1
- Observed failure: `3 failed / 1 passed`；`actual: '2026-10-24', expected: '2026-10-27'`（`105-red.txt`）。
- Failure reason validated: 把 `evidenceDate()` 的实现临时换成 `return "2026-10-24";` 后同一条命令判红，红点正是「目录不随运行日走」；`Evidence root` 那条仍绿，说明红不是模块缺失或路径错。

### GREEN — minimum implementation
- Implementation: 新增 `desktop/scripts/evidence-dir.cjs`（`evidenceDate()` 一处解析、`evidenceDir()` 拼根目录），`verify-matrix.cjs` 的 `OUT` 改为 `evidenceDir()`。
- Command: `cd desktop && node --test test/evidence-dir.test.mjs`
- Exit code: 0
- Passing evidence: `4 passed`、`EXITCODE=0`（`105-green.txt`）。
- Regression evidence: `cd desktop && node --test "test/*.test.mjs"` → 15 passed / 0 failed（`105-desktop-suite.txt`，原 11 例 + 新增 4 例）；`verify-shell` 的 28 态矩阵全绿。

### REFACTOR — optional
- Cleanup: 证据日期只在 `evidence-dir.cjs` 一处解析，`verify-matrix.cjs` 不再持有日期字面量。
- Regression evidence: 重跑 15 例与 28 态矩阵，读数不变。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿；打包与矩阵各跑一次。
- Failure identity and repeat count: 单次单缺陷（目录字面量），无回潮。
- Stop condition hit: 4 例绿 + 矩阵 28 态绿 + exe 内嵌 hash 一致。
- Handoff: 本片是 086 线的收官片，之后只有整树提交与推送。

### TDD or sizing exception — only with prior user approval
- Exception type: release packaging / evidence path configuration
- Reason: 本片的主体是重打包与证据落盘，不是业务行为；只有「证据日期」这一处新逻辑带测试，其余靠真实构建、真实启动与矩阵读数证明。
- User approval: 用户「使用 harness 进行修改，修改完成后提交代码 push」，并在 086 确认「22 条全做」，桌面交付是该范围内的最后一环。
- Alternative verification: 15 例桌面单测、`verify-shell` 28 态矩阵、exe 真实启动截图、asar 内嵌包 sha256 比对。

## Implementation notes

- 覆盖历史证据的根因是字面量而不是「忘了改」：`verify-matrix.cjs:9` 原来直接拼 `"2026-10-24"`。把日期收进 `evidenceDate(env, now)` 后，测试可以用固定 `now` 判「跨年跨月也读本地日期」，不必等到真跨月。
- `TREELEARN_EVIDENCE_DATE` 是复跑口，不是让日期变成可变行为：空白值必须回落当天，这一条由第 3 例钉住。
- 打包链的产物校验点有两个：`package-win.mjs` 只认 `Treelearn-${version}-portable.exe`；本片再加一个「从运行中的 exe 里读 asar」的字节比对，避免「exe 更新了但里面还是旧包」这种只有双击才发现的问题。
- 便携 exe 解压目录在进程退出后仍在 `%TEMP%`，本轮在进程存活时读取，读的是它真正加载的那份 `resources/app.asar`。

## Verification report

- Verdict: PASS
- Evidence: `105-red.txt`（3 failed / EXITCODE=1）、`105-green.txt`（4 passed / EXITCODE=0）、`105-desktop-suite.txt`（15 passed）、`105-package.txt`（`PACKAGE_OK … 114592941`）、`artifacts/evidence/desktop-shell/2026-10-27/verify.txt`（`VERIFY_OK`）、同目录 `report.json`（28 态、0 failure、0 console error）、`exe-launch.txt`（`WINDOW_OK` / `BUNDLE bytes=5871820 sha256=8dc53a86…6735` / `RECT 2360x1640` / `SHOT_OK`）、`exe-window.png`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除；新增 4 例覆盖日期解析的三条分支与根目录。
- Gaps: 未在 900×620 档拍 exe 实窗截图（矩阵的 887×586 内容区是同一份渲染的读数）；未做 exe 签名。
- Risks: exe 体积 +4.1 MB；`desktop/release/` 与 `desktop/app/` 不入库，换机器需重跑 `package:win`。

## Handoff notes

- Completed: 便携 exe 重打包并核对内嵌包；矩阵证据分日；`2026-10-24/` 历史证据保持原样。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 整树提交与推送（本线最后一步）。
- Watch-outs: 别把 `verify-matrix.cjs` 的目录改回字面量；要复跑旧档请用 `TREELEARN_EVIDENCE_DATE=2026-10-24`，不要改默认值。
