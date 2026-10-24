# Filename: T-2026-10-24-desktop-electron-shell-079.md
# Codex Harness Task: Electron 桌面壳、逐屏矩阵与便携 exe

id: T-2026-10-24-079
title: Electron 桌面壳、逐屏矩阵与便携 exe
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-24-desktop-electron-exe-requirement-078.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 431
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 新建 `desktop/`，用 Electron 加载 `frontend/dist-offline/index.html`，产出 Windows 便携 exe，布局与样式零改动。
- Task-scoped details: 窗口默认 1180×820、最小 900×620；标准标题栏；移除默认菜单；禁止外部导航与新窗口；单实例；14 屏 × 2 尺寸逐屏验证。
- User choice: Electron + Windows 桌面窗口 + 严格 TDD 重写 main.js + 全屏两档验收。

## Goal

桌面壳可启动、可打包，并在 14 个屏幕状态 × 两个窗口尺寸下原样渲染现有 Web 界面。

## Scope

### In scope
- `desktop/main.js`、`desktop/window-options.cjs`、`desktop/navigation.cjs`、`desktop/scripts/*`、`desktop/test/*`、`desktop/package.json`、`desktop/pnpm-lock.yaml`、`desktop/pnpm-workspace.yaml`、`desktop/.npmrc`、`desktop/.gitignore`、`desktop/README.md`。
- 逐屏矩阵报告与截图证据；`artifacts/metrics/report.md` 由指标脚本重算（生成物）。
- 根 `README.md`、`README_EN.md`、`desktop/README.md`、`AGENTS.md`、`.codex/context/project-brief.md`、`.codex/context/source-of-truth.md` 的现状同步。
- 守卫夹具行号同步：`.codex/scripts/dev/checkers_selftest.py`、`.codex/scripts/harness_check_lib.py` 中的 `AGENTS.md:34→35`。

### Out of scope
- `frontend/**` 修改；图标、签名、安装包与自动更新。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 只新增桌面入口；Web/Android 渲染、路由与文案不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费 `frontend/dist-offline/index.html`（生产者 `frontend/scripts/inline_offline.mjs:1`）；不触碰 `frontend/src/**`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 桌面启动/打包命令、窗口尺寸下限、导航与单实例约定；`desktop/.gitignore` 只忽略可重建的 `app/`、`release/` 与打包残留 `release-build/`，根 `.gitignore` 未动。
- Authoritative code/config/migration evidence: `desktop/main.js`、`desktop/window-options.cjs`、`desktop/package.json`、`desktop/pnpm-workspace.yaml`、`desktop/.npmrc`、`desktop/.gitignore`。
- Documentation surfaces checked: 根 `README.md`、`README_EN.md`、`desktop/README.md`、`AGENTS.md`、`.codex/context/project-brief.md`、`.codex/context/source-of-truth.md`、`artifacts/metrics/report.md`。
- Documentation files updated: 同批更新（`source-of-truth.md` 仅同步 `AGENTS.md` 行号位移；`artifacts/metrics/report.md` 由指标脚本重算，属生成物）。
- Guard fixture sync: `.codex/scripts/dev/checkers_selftest.py` 与 `.codex/scripts/harness_check_lib.py` 的 `AGENTS.md:34→35` 夹具随行号位移同步，属守卫夹具维护而非行为变更。
- Producer/consumer/sibling/compatibility paths checked: `frontend/package.json`、`frontend/scripts/inline_offline.mjs`。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改历史文档。

## Acceptance criteria

- [x] `node --test desktop/test/*.test.mjs` 退出码 0：窗口选项、导航白名单、屏幕矩阵完整性。
- [x] `pnpm --dir desktop run verify` 对 14 屏 × 2 尺寸全部渲染成功，无控制台错误与横向溢出，产出报告与截图。
- [x] `pnpm --dir desktop run package:win` 产出便携 exe；真实启动后窗口标题为「知芽」。
- [x] `git status --porcelain frontend` 为空，`desktop/` 内无 CSS 文件。

## TDD execution record

### RED — failing test first
- Test/scenario: 先写 `desktop/test/window-options.test.mjs`、`desktop/test/navigation.test.mjs`、`desktop/test/matrix.test.mjs`，生产模块尚未存在
- Command: `node --test desktop/test/*.test.mjs`
- Exit code: 1
- Observed failure: tests 5 / pass 3 / fail 2；两份测试分别报 `Cannot find module '...desktop/window-options.cjs'` 与 `...desktop/navigation.cjs`（MODULE_NOT_FOUND）
- Failure reason validated: matrix 测试通过证明 runner 与路径正常，失败仅来自尚未实现的窗口选项与导航白名单

### GREEN — minimum implementation
- Implementation: `desktop/window-options.cjs`、`desktop/navigation.cjs`、`desktop/main.js`，以及同步/矩阵/验收/打包脚本；测试保持 RED 阶段断言。
- Command: `node --test desktop/test/*.test.mjs`
- Exit code: 0
- Passing evidence: `tests 11 / pass 11 / fail 0`；覆盖窗口默认与最小尺寸、标准标题栏、隔离选项、导航白名单、14 态矩阵完整性。
- Regression evidence: `pnpm --dir desktop run verify` → `PASS sync-app`、`PASS smoke`、`PASS matrix (14 屏 × 2 尺寸)`、`VERIFY_OK`；矩阵按成品窗口外框设尺寸（`new BrowserWindow(buildWindowOptions())` + `setSize`，非内容区尺寸），实测内容区 1167×785 与 887×586，两档均 >760 且无横向溢出；`report.json` 记录 28 个状态、`failures: []`、`consoleErrors: 0`，28 张 JPEG 落盘。`pnpm --dir desktop run package:win` → `PACKAGE_OK ...\desktop\release\Treelearn-1.0.0-portable.exe 110432811`；真实启动窗口标题为「知芽」，外框 2360×1640 物理像素（200% 缩放下即 1180×820 逻辑尺寸），`exe-window.png` 可复核样式；`git status --porcelain frontend` 为空，`desktop/` 内无 CSS 文件。

### REFACTOR — optional
- Cleanup: 逐屏清单由 12 态修正为 14 态：学习库由 1 态扩为 3 个筛选态（净增 2），并在矩阵中点击后断言 `aria-pressed="true"`；打包改为在系统临时目录构建、成品回拷 `desktop/release/`，规避 IDE 文件索引占用 `.asar` 导致的 `EBUSY`。
- Regression evidence: 修正后重跑同一测试命令 11 例全绿，`verify` 重新产出 28 态报告（窗口改为按外框设定，另断言内容区宽 >760）；打包一次成功。

### LOOP — optional
- Command and rounds: PENDING
- Failure identity and repeat count: PENDING
- Stop condition hit: PENDING
- Handoff: PENDING

## Implementation notes

- `main.js` 在 Harness 选择前曾有草稿；按用户裁定草稿移入 `artifacts/tmp/`，生产实现从测试开始重写。
- `frontend/dist/index.html` 在 file:// 下会被 CORS 拦截，桌面壳只消费 `dist-offline/index.html`。
- 实测生产代码 431 行（壳本体 153 行 = `main.js` 117 + `window-options.cjs` 30 + `navigation.cjs` 6；同步/矩阵/验收/打包脚本 278 行；非空行 387），超过 200 行目标、低于 500 行上限；差额来自用户选定的 14 屏 × 2 尺寸逐屏验收与打包编排，记录为 verifier 警告项。
- `desktop/.gitignore` 只忽略可重建的 `app/`、`release/` 与打包残留 `release-build/`；根 `.gitignore` 未改。`artifacts/metrics/report.md` 由指标脚本重算，属生成物，非手写文档。
- precommit `line-budget` 实测 exit 2（整树 2512 行；其中 `desktop/pnpm-lock.yaml` 1909 行，desktop 非锁文件 603 行；生产代码 431 行）。这是整树提交形状而非运行时失败；本次不提交。提交前若仍按整树一次提交，须取得用户对 >500 行例外的明确批准；仅靠拆分无法通过（lockfile 单文件 1909 行已超预算），若不愿批准例外，则须先获准调整 line-budget 对 `pnpm-lock.yaml` 的排除规则。
- front matter `estimated_production_lines` 已按实测回填为 431（实施前估算为 110）。
- electron-builder 26.18.0 声明的 `@electron/get` 范围是 ^3，但其代码引用 5.x 才有的 `ElectronDownloadCacheMode`；`desktop/pnpm-workspace.yaml` 覆盖为 5.1.0。打包输出改到系统临时目录，规避 IDE 文件索引长期占用工作区 `.asar` 导致的 `EBUSY`。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-25；前两轮 PARTIAL 的记录缺口已修复）
- Evidence: `node --test desktop/test/*.test.mjs` → 11/11；`pnpm --dir desktop run verify` → 28 态、`failures: []`、`consoleErrors: []`、内容区 1167×785 / 887×586、28 张 JPEG；`package:win` → 110,432,811 字节，exe 内 `app/index.html` 与 `frontend/dist-offline/index.html` 同 hash；`exe-window.png` 标题「知芽」；`validate_tasks.py` errors=0；core guards 39/39 PASS；`git status --porcelain frontend` 为空、无 CSS。
- Changed/deleted assertion rationale and equivalent behavior coverage: 12→14 态（学习库 1→3 筛选态，净增 2）；矩阵对筛选态真实点击并断言 `aria-pressed="true"`；窗口按成品外框 `setSize` 设定并新增 `wideViewport`（>760）与无横向溢出断言，覆盖隐藏窗口与可见窗口的尺寸口径差异。
- Gaps: TDD RED 无原始转录，强度依赖卡面记录；900×620 仅有离屏矩阵截图，无可见窗口截图；`line-budget` 整树 2512 行为提交形状红，本次未提交。
- Risks: 未签名 exe 可能触发 SmartScreen；默认 Electron 图标；`file://` 白名单较宽；矩阵证据目录日期在 `verify-matrix.cjs` 中固定为 `2026-10-24`；`startup.md` 的 guard 正文尚未点名 `desktop/`（规则修订需用户授权）；`desktop/release-build/` 旧残留仍在磁盘（已忽略）。

## Handoff notes

- Completed: 实现、11 例单测、14 屏 × 2 尺寸矩阵、便携 exe、真实启动证据、文档与守卫夹具同步，独立 verifier PASS。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 提交前须就 `line-budget` >500 行取得用户明确例外批准，或先获准调整 `pnpm-lock.yaml` 排除规则；提交时再按用户要求处理整树范围。
- Watch-outs: 不在 `desktop/` 引入任何界面样式；截图与报告写入 `artifacts/evidence/desktop-shell/`；打包不要在仓库内直接产出（IDE 索引会锁 `.asar`）。
