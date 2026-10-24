# Filename: T-2026-10-24-desktop-electron-exe-requirement-078.md
# Codex Harness Task: 桌面端 exe（Electron 壳）

id: T-2026-10-24-078
title: 桌面端 exe：复用 Web 产物的 Electron 壳
template_version: 2
task_type: requirement
status: verified
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: harness
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 现有交付只有 Android APK 与 Web 产物（`frontend/dist-offline/index.html`）。用户希望把 Web 版变成可双击的 Windows exe，且不重做界面。
- Users/scenarios: 单人使用者在 Windows 桌面上直接打开知芽，不经过浏览器地址栏与书签；需要与 Web 版同一套界面与交互。
- Expected outcome: 新建一级目录 `desktop/`，产出 `desktop/release/Treelearn-1.0.0-portable.exe`；窗口为标准 Windows 标题栏，内容即 Web 产物原样渲染。
- Core flow and reversibility: 构建 frontend → 同步单文件产物到 desktop/app → 启动/打包 exe；删除 `desktop/` 与 `desktop/release` 即可回退，frontend 不受影响。
- Inputs/outputs: 输入为 frontend 构建产物；输出为桌面窗口、便携 exe、逐屏渲染报告与截图。
- State and exception handling: 缺少 frontend 产物时给出明确报错并退出；渲染失败/控制台错误计入验收失败；重复启动聚焦已有窗口。
- Constraints and trade-offs: 布局、字体、颜色、间距、断点全部沿用 Web 版，`frontend/**` 不改；不新增网络、存储、账号边界；Electron 版本锁定。
- AI recommendation and rationale: Electron 自带 Chromium，渲染一致、落地最快；Tauri 体积更小但需 Rust 工具链与系统 WebView2。
- User choice and custom additions: 选择 Electron；窗口采用 Windows 桌面样式（标准标题栏）；要求新建一级目录；要求按 Harness 开发与测试并保证质量与整个样式；要求严格重写 main.js 以满足 TDD。
- AI-autonomous decisions: 目录名 `desktop/`；默认窗口 1180×820、最小 900×620；移除 Electron 默认菜单；禁止外部导航与新窗口；单实例；便携版为默认打包目标。
- User-confirmation decisions: Electron 方案、Windows 桌面窗口形态、一级目录、严格 TDD 重写、全屏 × 两档样式验收。
- Acceptance evidence: 单元测试、逐屏矩阵报告与截图、打包产物与真实启动证据。
- Facts: `frontend` 产物零网络请求；`dist/index.html` 在 file:// 下因 CORS 不可用，必须用 `dist-offline/index.html`；Web 现有 6 个视图，学习库 3 个筛选态、对话 2 个主题态、设置 6 个分类态，去重后共 14 个可验证屏幕状态。
- Assumptions: Windows 10/11 桌面；本机可运行 Electron；品牌图标按阶段四另行收口。
- Unknowns: 无会改变范围或验收的未知项。
- Risks: Electron 未签名可能触发 SmartScreen；便携版体积约 100MB。
- Deferred: 应用图标、NSIS 安装包、自动更新、代码签名。
- Non-goals: 不改 Web 布局与样式；不引入 remote 内容；不做多窗口与托盘。
- Skipped non-critical questions and reason: 图标资源与安装包形态属后续交付，不阻断本次 exe 落地。

## Goal

在 `desktop/` 交付一个 Electron 壳，加载 `frontend` 的离线单文件产物，产出可双击的 Windows 便携 exe，并以逐屏矩阵证明布局与样式原样保留。

## Scope

### In scope
- `desktop/` 目录、窗口配置、导航拦截、单实例、启动脚本与打包配置。
- 单元测试与 14 屏 × 2 尺寸的渲染矩阵、截图证据。
- 根 `README.md`、`README_EN.md`、模块 README、`AGENTS.md`、背景包与 `source-of-truth` 的现状同步；`artifacts/metrics/report.md` 由指标脚本重算（生成物）。
- 守卫夹具行号同步：`.codex/scripts/dev/checkers_selftest.py`、`.codex/scripts/harness_check_lib.py` 中的 `AGENTS.md:34→35`。

### Out of scope
- `frontend/**` 的任何改动；Android/backend 改动；图标与签名。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增桌面交付面；Web 与 Android 行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费 `frontend/dist-offline/index.html`；生产者仍为 `frontend/scripts/inline_offline.mjs`。
- Native bridge or independent-client compatibility/update requirements: 无原生桥接。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 新增 `desktop/` 构建与启动入口、exe 产物路径、窗口尺寸与行为约定。
- Authoritative code/config/migration evidence: `desktop/main.js`、`desktop/window-options.cjs`、`desktop/navigation.cjs`、`desktop/package.json`、`desktop/pnpm-workspace.yaml`、`desktop/.npmrc`、`desktop/.gitignore`。
- Documentation surfaces checked: 根 `README.md`、`README_EN.md`、`desktop/README.md`、`AGENTS.md`、`.codex/context/project-brief.md`、`.codex/context/source-of-truth.md`、`artifacts/metrics/report.md`。
- Documentation files updated: 同批更新（`source-of-truth.md` 仅同步 `AGENTS.md` 行号位移；`artifacts/metrics/report.md` 由指标脚本重算，属生成物）。
- Guard fixture sync: `.codex/scripts/dev/checkers_selftest.py` 与 `.codex/scripts/harness_check_lib.py` 的 `AGENTS.md:34→35` 夹具随行号位移同步，属守卫夹具维护而非行为变更。
- Producer/consumer/sibling/compatibility paths checked: `frontend/package.json`、`frontend/scripts/inline_offline.mjs`。
- Superseded phrases/examples reverse-searched: 无（新增面）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改历史文档。

## Acceptance criteria

- [x] `node --test desktop/test/*.test.mjs` 退出码 0，覆盖窗口选项、导航白名单与屏幕矩阵完整性。
- [x] 桌面壳在 14 个屏幕状态 × 1180×820/900×620 下渲染成功：根节点非空、标题为「知芽」、无控制台错误、无横向溢出，并留截图。
- [x] `pnpm --dir desktop run package:win` 产出 `desktop/release/Treelearn-1.0.0-portable.exe`，真实启动后存在标题为「知芽」的窗口。
- [x] `frontend/**` 无任何改动，桌面目录不新增 CSS。

## TDD execution record

### RED — failing test first
- Test/scenario: 父卡不直接实施代码；子卡 079 先写窗口选项、导航白名单与矩阵完整性测试。
- Command: `node --test desktop/test/*.test.mjs`
- Exit code: 1
- Observed failure: tests 5 / pass 3 / fail 2，缺 `window-options.cjs` 与 `navigation.cjs`（MODULE_NOT_FOUND）。
- Failure reason validated: 矩阵测试同步通过，失败仅来自尚未实现的生产模块。

### GREEN — minimum implementation
- Implementation: 见子卡 079 的窗口配置、导航白名单、主进程与脚本。
- Command: `node --test desktop/test/*.test.mjs`
- Exit code: 0
- Passing evidence: tests 11 / pass 11 / fail 0。
- Regression evidence: `pnpm --dir desktop run verify` 28 个状态零失败；便携 exe 构建并真实启动，窗口标题「知芽」。

## Implementation notes

- 本卡为需求父卡，具体实现与证据落在子卡。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-25；父卡结论由子卡 079 证据支撑）
- Evidence: 11/11 单测；`report.json` 28 态、`failures: []`、`consoleErrors: []`；两档内容区 1167×785 / 887×586 且无横向溢出；28 张 JPEG；`exe-window.png` 显示标准标题栏「知芽」；`desktop/release/Treelearn-1.0.0-portable.exe` 110,432,811 字节且内嵌 `app/index.html` 与 `frontend/dist-offline/index.html` 同 hash；`git status --porcelain frontend` 为空、`desktop/` 无 CSS；core guards 39/39 PASS。
- Changed/deleted assertion rationale and equivalent behavior coverage: 屏幕清单由 12 态修正为 14 态（学习库 1 态扩为 3 个筛选态，净增 2），并在矩阵中真实点击后断言 `aria-pressed="true"`；窗口尺寸改用成品外框设定，另断言内容区宽 >760 与无横向溢出。
- Gaps: TDD RED 无原始转录，强度依赖卡面记录；900×620 仅离屏矩阵截图，无可见窗口截图；`line-budget` 整树 2512 行为提交形状红，未在本卡提交。
- Risks: 未签名 exe 可能触发 SmartScreen；默认 Electron 图标；`file://` 导航白名单较宽；`desktop/release-build/` 旧打包残留仍在磁盘（已忽略）。

## Handoff notes

- Completed: 需求范围冻结；子卡实现、测试、14×2 矩阵、便携 exe、真实启动与文档同步全部完成，独立 verifier PASS。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 提交前需就 `line-budget` >500 行取得用户明确例外批准，或先获准调整 `pnpm-lock.yaml` 排除规则；本次未提交。
- Watch-outs: 必须使用 `dist-offline/index.html`；不得为桌面另写样式。
