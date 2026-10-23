# Filename: T-2026-10-21-workspace-browser-evidence-and-docs-046.md
# Codex Harness Task: 真实浏览器验证、规格修订与交付证据

id: T-2026-10-21-046
title: 真实浏览器验证、规格修订与交付证据
template_version: 2
task_type: verification
status: verified
parent_requirement: T-2026-10-21-web-learning-workspace-requirement-041.md
requirements_confirmed: true
generated_by: agent
owner: verifier
owner_lane: verification
stack: frontend
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-10-21-workspace-home-library-044.md, T-2026-10-21-workspace-settings-category-detail-045.md, T-2026-10-21-workspace-chat-and-branch-panel-043.md]
tdd_mode: not-applicable

## Requirement collaboration brief

- Parent summary: 042–045 已交付工作区外壳、状态、对话、主页/学习库与设置。本片只做真实浏览器验证、证据归档、权威规格的 dated 修订与整跑收尾，不再新增界面行为。
- Problem/background: 单元测试与 lint 不能证明布局在真实浏览器里的滚动、焦点、中文输入法、宽窄切换与返回恢复；用户明确要求提供实现截图并说明仍未接通的能力，不能只凭构建成功宣布交互完成。
- Users/scenarios: 桌面 1440×900 与窄窗 390×844 下走完主路径：侧栏切换 → 打开主题 → 阅读/滚动 → 打开分支面板与知识树 → 复制 → 切到学习库搜索/筛选 → 回对话恢复草稿与位置 → 浏览器后退/前进 → 窗口 resize。
- Expected outcome: 一份可复核的证据包（截图＋DOM/读数记录）落在 `artifacts/evidence/web-workspace-implementation-2026-10-21/`；权威规格追加 dated 修订，登记主路径变化、真实能力边界与旧快照屏过渡状态；全部守门命令退出码 0；父卡与各子卡验证结论与证据一致。
- Core flow and reversibility: 启动 Vite dev server → headless Edge + CDP 驱动；截图与读数只写 `artifacts/evidence/`；文档只追加不重写。回退为删除新增证据目录与规格修订段，不影响生产代码。
- Inputs/outputs: 输入是已实现的工作区、`pnpm` 守门、`.codex/scripts/dev/parity/parity_cdp.mjs` 与设计 token 检查脚本；输出是截图、读数字段、规格修订、任务卡验证记录。不产生生产代码改动（发现缺陷时回到对应子卡修复，不在本卡夹带）。
- State and exception handling: 窄窗必须用 CDP `Emulation.setDeviceMetricsOverride`（headless Chromium 有最小窗口宽度，不能靠 `--window-size=390`）；若浏览器不可用或某项操作无法真实驱动，证据必须写「未验证」而不是推断通过；发现不一致如实记入 Gaps 并回退到对应实现卡。
- Constraints and trade-offs: 不新增依赖；不自动提交或推送；不改 Android/后端；不改示意稿 HTML 与配套说明；不把模拟数据写进证据；截图与读数必须来自本次构建。
- AI recommendation and rationale: 先跑完整守门再进浏览器，避免在红构建上取证；浏览器脚本优先用仓库已有 CDP 工具，避免引入 Playwright/Puppeteer 依赖。证据以「操作步骤＋预期＋实际」记录，截图文件名按场景命名。
- User choice and custom additions: 用户要求桌面与窄窗口截图、键盘/中文输入法/滚动/焦点/返回恢复实测、说明必要差异与未接通能力。
- AI-autonomous decisions: ① 证据目录沿用既有命名习惯 `artifacts/evidence/web-workspace-implementation-2026-10-21/`；② 截图至少覆盖桌面主页/对话/分支面板/学习库/设置与窄窗对话/学习库；③ 中文输入法用 CDP `Input.imeSetComposition` 或等价组合事件验证，不以直接赋值代替；④ 焦点返回用 `document.activeElement` 读数证明；⑤ 规格只追加 2026-10-21 修订段。
- User-confirmation decisions: 见父卡。
- Acceptance evidence: 全量 `lint/test/verify` 与设计 token、任务校验退出码；截图清单；键盘/IME/焦点/滚动/resize/返回读数；规格修订文本；父卡与子卡 verdict 与证据一致。
- Facts: dev server 默认 `http://127.0.0.1:5173/`；`pnpm --dir frontend verify` 已含类型检查、测试、构建等守门；`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` 是 Web/Android parity 权威规格；`artifacts/evidence/` 已有历史证据目录。
- Assumptions: 本机 headless Edge 可启动；`navigator.clipboard` 在 localhost 安全上下文可用；截图工具可读取 1440×900 与 390×844。
- Unknowns: 平板 WebView 档不在本轮；真实生成/写入/上传/建分支仍未接通。
- Risks: 浏览器证据与单元测试可能暴露实现缺陷，需要回到 042–045 修复并重新取证；证据文件过大或命名混乱；规格修订与历史条目冲突。
- Deferred: 平板 WebView、无障碍读屏器实测、跨刷新持久化、真实后端能力。
- Non-goals: 不在本卡改生产代码（除证据脚本必要的只读驱动）、不改 Android/后端、不提交/推送、不删除设计参照。
- Skipped non-critical questions and reason: 没有追问截图压缩格式与证据目录是否加日期子目录；沿用既有证据目录习惯，不影响验收。

## Goal

用真实浏览器与完整守门证明工作区主路径可用，并把当前主路径、能力边界与过渡状态写入权威规格；证据与结论一致，未验证项如实列出。

## Scope

### In scope
- 启动 dev server，用 headless Edge + CDP 在 1440×900 与 390×844 驱动主路径并截图。
- 验证侧栏折叠、视图切换、主题进入/返回、对话滚动、分支面板宽窄、Esc/焦点返回、知识树缩放、复制结果、学习库搜索/筛选/展开/恢复、设置分类/恢复、窗口 resize 不清状态、中文输入法组合输入。
- 截图与读数写入 `artifacts/evidence/web-workspace-implementation-2026-10-21/`。
- `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` 追加 2026-10-21 dated 修订。
- 跑全部守门并汇总到父卡与子卡。

### Out of scope
- 新增界面行为或修复（缺陷回 042–045）。
- 平板 WebView、读屏器、跨刷新持久化。
- 修改设计参照、夹具、token、Android/后端。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 本卡不改运行时行为；验证覆盖旧行为回归（主题 0 回答、两条推荐、两个学习库主题、图标同源、旧快照屏测试）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 浏览器访问 `main.tsx` 编排的正式应用；CDP 驱动真实 DOM 事件；守门覆盖 `src/**` 与 `test/**`。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 只验证与记录，不改生产代码。

## Documentation impact record

- Changed runtime facts: Web 主路径为侧栏＋工作区；hash `#/<view>/<topic>` 可后退；发送/附件/保存/建分支/设置写入未接通；主题 1 无对话；设置只读；旧快照屏保留为证据路径而非主路径。
- Authoritative code/config/migration evidence: 实现后的 `frontend/src/main.tsx`、`layouts`、`pages`、`store`、`styles/workspace.css`；运行证据目录。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`、`.codex/context/source-of-truth.md`、`.codex/ROADMAP.md`、`.codex/rules/documentation.md`、`.codex/rules/ux-design.md`。
- Documentation files updated: 上述规格追加 2026-10-21 修订段；父卡 041 与子卡 042–046 的 Verification report 更新；`artifacts/evidence/web-workspace-implementation-2026-10-21/README.md` 记录环境、守门读数、22 项浏览器读数、截图清单与未接通能力。
- Producer/consumer/sibling/compatibility paths checked: 规格中旧的「底部两标签」描述被 dated 修订覆盖但历史条目保留；事实源清单若登记 Web 入口需同步检查；不修改示意稿与说明。
- Superseded phrases/examples reverse-searched: 「底部两标签」「逐屏快照即主界面」「发送后追加本地消息」「设置可写」——修订前反查，确保新修订明确替换。
- `documentation impact: none` reason and evidence, if applicable: 不适用——主路径变化必须写进规格。
- Historical/PRD handling: 规格只追加、不改写 2026-10-15 原文；示意稿与配套说明保持字节不变。

## Acceptance criteria

- [x] `pnpm --dir frontend lint`、`pnpm --dir frontend test`、`pnpm --dir frontend verify`、`python -X utf8 .codex/scripts/dev/check_design_tokens.py`、`python -X utf8 .codex/scripts/validate_tasks.py` 全部退出码 0，读数记录在案。
- [x] 1440×900 截图覆盖主页、对话、分支面板、学习库、设置；390×844 截图覆盖对话（含面板覆盖态）与学习库；截图文件落在证据目录且与场景命名一致。
- [x] 读数记录能证明：侧栏折叠后 `aria-expanded=false`；对话正文独立滚动且顶部/底部工具区位置不变；分支面板宽窗并排、窄窗覆盖，Esc 后面板关闭且焦点回触发按钮；知识树缩放夹在 50–150%；复制写入剪贴板；学习库查询/筛选/展开与滚动在返回后恢复；设置分类与滚动恢复；resize 后上述业务状态不变；中文输入法组合期间不触发额外动作。
- [x] 规格修订包含日期、用户确认来源、主路径变化、未接通能力、旧快照屏过渡状态；`git diff --name-only` 不出现 `android/**`、`backend/**` 或两份设计参照。
- [x] 父卡与 042–045 的 Verification report 与实际证据一致；未验证项明确写入 Gaps，不以「应可用」代替。
- [x] 示意稿 HTML 与配套说明字节不变（校验和记录）；工作区既有未提交修改未被回退。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（验证/文档卡，无新增被测实现；先跑全量守门，红项回对应实现卡）
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A（不新增生产实现）
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本卡只做验证、证据与文档，不新增生产实现。
- User approval: 用户 2026-10-21 已要求真实浏览器验证与截图；父卡登记。
- Alternative verification: 全量守门读数＋浏览器截图＋DOM/焦点/滚动读数＋规格修订。

## Implementation notes

- 浏览器工具：`.codex/scripts/dev/parity/parity_cdp.mjs`（headless Edge，CDP），窄窗用 `Emulation.setDeviceMetricsOverride`；不使用 `--window-size=390`。
- 截图清单建议：`desktop-home.png`、`desktop-chat.png`、`desktop-branch-panel.png`、`desktop-library.png`、`desktop-settings.png`、`narrow-chat.png`、`narrow-branch-overlay.png`、`narrow-library.png`。
- 读数文件：`readings.json` 或 `readings.md`，逐场景记录步骤、预期、实际与 DOM 证据；中文输入法记录 composition 事件与最终值。
- 复制验证读取 `navigator.clipboard.readText()` 或对成功状态文案取证；若权限不允许，记录 clipboard API 调用与页面成功状态，并注明限制。
- 规格修订用「### 2026-10-21 Web 工作区修订」段落追加，列出主路径、能力边界、旧路径状态；不编辑历史段落。
- 先跑守门再取证；若取证发现缺陷，回 042–045 修复并重新跑守门与取证，本卡不直接改生产代码。

## Verification report

- Verdict: PASS
- Evidence:
  - 守门（本卡运行时）：`lint` rc=0（依赖方向 35 文件 0 发现、设备时间 0、文案 0、夹具 13/13）；`test` 16 文件 / 107 例；`verify` 16/16、`VERIFY PASS`；`check_design_tokens` OK=10 FAIL=0（44 token、52 文件裸色 0）；`validate_tasks` 41 文件 errors=0。
  - 浏览器：headless Edge + CDP，`http://127.0.0.1:5199/`；`readings.json` 22/22 通过。关键读数：空格折叠 `aria-expanded=false` 且宽 76；正文滚到 600 时顶栏/输入区坐标不变；IME `compositionstart=1/end=1`、草稿「中文草稿」、hash 不变；复制状态 `copied` 且剪贴板 2156 字；宽窗面板 320 px 并排、窄窗 340 px 覆盖并贴右缘、Esc 还焦；知识树 150%/50%/100% 夹取；库滚动 570/444 滚到 126 往返保持；设置 816/444 滚到 140 往返保持；390 窄窗无页面横向溢出；宽度往返后库筛选与两行保持、页宽恢复 960。
  - 截图 11 张（5 桌面主路径 + 折叠态 + 过滤态 + 知识树 + 3 窄窗）落在 `artifacts/evidence/web-workspace-implementation-2026-10-21/`；README 记录环境、命令、逐项读数、必要差异与未接通能力。
  - 规格：`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` 追加 2026-10-21 修订段，写明用户确认来源（父卡 041）、主路径变化、能力边界、旧快照屏过渡状态与证据位置；历史段未改写。
  - 资产：示意稿 HTML `da1b4e961c44538bda13e411c89bb0697e692904aab038af9ccf4eb4eb289d6f`、说明 MD `b7f7935180698a45fd3599d7c8a557dad0ba562ac1577fe59a99ecd771743c65`（SHA-256，记录于证据 README）；本次会话未写入两份文件；`git status` 无 `android/**`、`backend/**` 改动，既有未提交修改未回退。
  - 卡片：父卡 041 与 042–045 的 Verification report 已补记本证据包对应读数；未验证项集中在下方 Gaps。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言变更；本卡汇总 042–045 的断言改写理由（旧底部标签→侧栏、旧路由→面板/覆盖层、旧三屏测试→工作区行为断言，旧快照屏测试保留）。
- Gaps: 平板 WebView/WebKit 第二环境、读屏器、跨刷新持久化、真实 IME 应用、真实后端/生成/上传/建分支/设置写入未验证；知识树仅根节点（夹具未采集子节点）；主题 1 无对话基线。
- Risks: 浏览器取证只覆盖本机 Edge 与两个视口；滚动恢复依赖内容高度，内容变短时会被浏览器夹到可滚上限；证据目录中的截图与读数需要随实现变更重新生成，不能当作永久基线。

## Handoff notes

- Completed: 全量守门、桌面/窄窗真实浏览器取证（22 项读数、11 张截图）、规格 2026-10-21 修订、父卡与 042–045 验证补记、证据 README。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 交付说明按证据 README 汇总已实现内容、与示意稿的必要差异、仍未接通能力；后续若接入后端/生成/设置写入，回到对应实现卡并重新取证。
- Watch-outs: ① 窄窗必须 CDP 模拟；② 不把「构建成功」写成「交互完成」；③ 不修改设计参照；④ 发现缺陷回实现卡，不在验证卡夹带修复；⑤ 证据读数随实现变化需重跑，不复制旧数字。
