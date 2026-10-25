# Filename: T-2026-10-25-desktop-glass-rebuild-084.md
# Codex Harness Task: 重建离线产物与 Windows 便携 exe

id: T-2026-10-25-084
title: 重建离线产物与 Windows 便携 exe
template_version: 2
task_type: config
status: verified
parent_requirement: T-2026-10-25-reading-glass-parity-requirement-080.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: frontend
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-10-25-reading-glass-shell-081.md, T-2026-10-25-selection-toolbar-082.md, T-2026-10-25-selection-quote-flow-083.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 081–083 改完前端后，Web 产物与 Windows exe 必须吃同一份新界面。
- Problem/background: 桌面壳加载 `frontend/dist-offline/index.html`；只改源码不重建，exe 仍是旧界面。
- Users/scenarios: 双击已安装的「知芽」或在浏览器打开离线产物的用户。
- Expected outcome: `pnpm --dir frontend build` 重新生成 `dist-offline`；`pnpm --dir desktop run verify` 与 `package:win` 产出新 exe；安装目录中的 exe 更新并真实启动，窗口截图证明新界面生效。
- Core flow and reversibility: 构建 → 同步 → 验证 → 打包 → 覆盖安装副本；回退为重装上一版 exe。
- Inputs/outputs: 输入是 081–083 的前端产物；输出是 `desktop/release/Treelearn-1.0.0-portable.exe`、安装副本与截图。
- State and exception handling: 构建失败即停；打包被文件占用时按既有临时目录策略处理；安装副本只覆盖 exe，不动用户数据。
- Constraints and trade-offs: 不为桌面另写样式；不新增依赖；不签名；安装路径沿用 `%LOCALAPPDATA%\\Programs\\Treelearn`。
- AI recommendation and rationale: 复用既有 Electron 壳与安装脚本，只做产物重建与证据，不引入第二条构建链。
- User-confirmation decisions: 用户要求 exe 与 Web 同改。
- AI-autonomous decisions: 覆盖已安装 exe；保留桌面与开始菜单快捷方式；截图落在 `artifacts/evidence/desktop-shell/2026-10-25/`。
- Acceptance evidence: 构建命令退出码 0；14×2 矩阵报告零失败；exe 文件时间与大小；真实窗口截图。
- Facts: 桌面入口 `frontend/dist-offline/index.html`；矩阵 14 态 × 1180×820 / 900×620；安装路径与快捷方式已存在。
- Assumptions: 本机 Electron 工具链仍可用；无管理员权限也能覆盖用户目录 exe。
- Unknowns: 无。
- Risks: 打包器二进制占用导致 EBUSY；旧进程未退出导致覆盖失败；未签名 exe 触发 SmartScreen。
- Deferred: 签名、安装包、自动更新。
- Non-goals: 不改桌面壳代码；不改 Android；不改安装路径。

## Goal

让 Web 离线产物与已安装的 Windows exe 都运行 081–083 的新界面，并用构建、矩阵与真实窗口截图证明。

## Scope

### In scope
- 重建 `frontend/dist-offline/index.html`。
- `pnpm --dir desktop test`、`run verify`、`run package:win`。
- 覆盖 `C:\\Users\\23745\\AppData\\Local\\Programs\\Treelearn\\Treelearn.exe` 并启动截图。
- 证据落 `artifacts/evidence/desktop-shell/2026-10-25/`。

### Out of scope
- 桌面壳源码、Android、签名与安装包形态。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 桌面壳行为不变，只替换其加载的前端产物；保留单实例、导航白名单、窗口尺寸与快捷方式。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `frontend/scripts/inline_offline.mjs`；消费者 `desktop/scripts/sync-app.mjs` 与 `desktop/main.js`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 桌面壳代码零改动。

## Documentation impact record

- Changed runtime facts: 已安装 exe 承载的前端产物更新为新玻璃界面（含 forest 留白回归修复）。
- Authoritative code/config/migration evidence: `frontend/dist-offline/index.html` 与 `desktop/app/index.html` 同为 sha256 `55053a70…`；`desktop/release/Treelearn-1.0.0-portable.exe` 与安装副本同为 sha256 `142208c0…`，110493305B。
- Documentation surfaces checked: `desktop/README.md` 的构建命令与安装路径未变；该文件中的「图标」章节来自同日图标直接执行，不属于 084。
- Documentation files updated: 无，`documentation impact: none`，理由是 084 只重建产物、覆盖安装副本并取证，未改壳行为或文档化命令。
- Producer/consumer/sibling/compatibility paths checked: `desktop/scripts/sync-app.mjs`、`package-win.mjs`、`verify-shell.mjs`；`frontend/scripts/inline_offline.mjs`。
- Superseded phrases/examples reverse-searched: 无。
- Historical/PRD handling: 不改历史发布记录。
- 取证副作用：`desktop run verify` 的矩阵脚本把 28 张 JPEG 与 `report.json` 固定写入 `artifacts/evidence/desktop-shell/2026-10-24/`。084 运行后被覆盖，已从 `HEAD` 恢复，带日期的历史证据保持原样；硬编码路径属 079 已知风险，本卡不改脚本。
- Unattributed working-tree items (not 084): `desktop/package.json` 的 `build.win.icon`、`desktop/assets/icon.*` 与 `desktop/README.md` 图标章节来自同日图标直接执行，随整树提交但不计入本卡范围。

## Acceptance criteria

- [x] `pnpm --dir frontend build` 退出码 0，`dist-offline/index.html` 时间戳更新。
- [x] `pnpm --dir desktop test` 0 失败；`pnpm --dir desktop run verify` 的 14 态 × 2 尺寸矩阵零失败、零控制台错误。
- [x] `pnpm --dir desktop run package:win` 产出新 `Treelearn-1.0.0-portable.exe`。
- [x] 安装目录 exe 被覆盖，双击/启动后窗口标题为「知芽」，截图显示悬浮胶囊顶栏、无横线输入坞。
- [x] 安装路径、桌面与开始菜单快捷方式保持不变。

## TDD execution record

### RED — failing test first
- Test/scenario: 构建与打包任务不适用 TDD。
- Command: 不适用
- Exit code: 不适用
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation
- Implementation: 重建 `frontend/dist-offline/index.html`（518464B，sha256 55053a70…），同步到 `desktop/app/index.html` 后哈希一致；`desktop/release/Treelearn-1.0.0-portable.exe`（110493305B，sha256 142208c0…）覆盖安装副本，安装副本与 release 哈希一致；桌面与开始菜单快捷方式未改动。
- Command: `pnpm --dir frontend build` → `pnpm --dir desktop test` → `pnpm --dir desktop run verify` → `pnpm --dir desktop run package:win` → 启动安装副本取证
- Exit code: 0 / 0 / 0 / 0 / 0
- Passing evidence: `084-frontend-build.txt`、`084-build.txt`（产物与快捷方式指纹）、`084-desktop-test.txt`（11/11）、`084-desktop-verify.txt`（smoke + 14 屏 × 2 尺寸 PASS）、`084-package-win.txt`（PACKAGE_OK）、`084-launch.txt`（UTF-8 窗口标题「知芽」，2360×1640）、`084-installed-exe.png`、`084-app-drive.txt`（17 项 PASS）、`084-installed-chat.png`、`084-installed-toolbar.png`、`084-installed-forest.png`。
- Regression evidence: 安装版聊天页实测顶栏距窗口顶/右与侧栏右各 12px、高 52px、圆角 99px、`blur(28px) saturate(1.5)`；`.chat-dock` border-top 0px、box-shadow none；输入框圆角 28px、玻璃 0.86；无横向溢出；选中正文出现 277×52 六格浮条（3 格 disabled，placement=above）；森林页 `padTop=64, barH=52` 且无空带、无横向溢出。

### REFACTOR — optional
- Cleanup: 无生产代码改动；取证脚本只改证据输出（`084-installed-capture.ps1` 控制台改 UTF-8、`084-app-drive.mjs` 增加森林页检查），留在 `artifacts/tmp/`。
- Regression evidence: 同 GREEN。

### LOOP — optional
- Command and rounds: 1 轮：独立 verifier 发现 `.forest-page` 仍用旧栏高 76/68 → 081 补 RED/GREEN 修样式 → 重新 `frontend build` / `desktop verify` / `package:win` / 覆盖安装副本 / 重拍证据。
- Failure identity and repeat count: 「forest-page 与顶栏高度不配套」，1 次。
- Stop condition hit: 安装版森林页实测 `padTop=64, barH=52`，`084-app-drive.txt` 全 PASS。
- Handoff: 待独立复核后翻 `verified`。

### TDD or sizing exception — only with prior user approval
- Exception type: config/构建取证任务无生产代码
- Reason: 本卡无可测行为，只重建既有前端产物、覆盖安装副本并取证；生产代码的 RED/GREEN 落在 081（含 forest 回归）。
- User approval: 用户 2026-10-25 在执行前选择 Harness 并确认「exe 与 Web 同改」；config 任务按项目既有先例（`T-2026-10-18-process-asset-copy-002`）以 approved-exception 记录，非事后补批。
- Alternative verification: `pnpm --dir frontend build`、`pnpm --dir desktop run verify` 的 14 态 × 2 尺寸矩阵、便携 exe 打包与真实窗口/安装版 CDP 取证。

## Implementation notes

- 只重建与拷贝，不改 `desktop/**` 源码；桌面图标资产属于同日直接执行，不在本卡范围。
- 覆盖安装副本前先确认旧进程已退出，避免 `EBUSY`。
- 证据截图文件名带日期与范围。

## Verification report

- Verdict: PASS（独立复核通过，2026-10-25）
- Evidence: `084-frontend-build.txt`、`084-package-win.txt`、`084-build.txt`（dist-offline = app/index.html `55053a70…`；release = installed `142208c0…`，110493305B）、`084-desktop-test.txt`（11/11）、`084-desktop-verify.txt`（smoke + 14×2 PASS）、`084-launch.txt`（标题「知芽」）、`084-installed-exe.png`、`084-app-drive.txt`（17 项 PASS）、`084-installed-chat.png`、`084-installed-toolbar.png`、`084-installed-forest.png`、`084-precommit-guards.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不删断言；081 新增 `.forest-page` 留白断言，等价覆盖非对话页的旧栏高。
- Gaps: 082「禁用格点击不触发回调」与 083「输入框内选区不出浮条」只有组件层间接覆盖，未单测；`backdrop-filter` 降级仍靠既有 `@supports` 断言。
- Risks: precommit `line-budget` 判红——`python -X utf8 .codex/scripts/run_all_guards.py --tier precommit` 退出码 1，「业务变更行: 972 > 500」，成因是用户拍板的整树一次提交（混合工作树），各片单独均低于 500，按既有偏差登记不拆提交；同档其余 5 项 PASS（`084-precommit-guards.txt`）；exe 未签名，SmartScreen 可能提示；矩阵脚本的日期目录仍硬编码为 2026-10-24，后续整跑会再次覆盖，已登记为 079 风险。

## Handoff notes

- Completed: 前端产物重建、桌面 11 例与 14×2 矩阵、便携 exe 打包、安装副本覆盖（哈希一致）、安装版窗口/聊天/浮条/森林页取证。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 复核通过后做整树提交（用户另行确认）。
- Watch-outs: 必须使用 `dist-offline/index.html`；不要为桌面另写样式；下次重建后要重新覆盖安装副本并重拍证据，旧哈希即失效。
