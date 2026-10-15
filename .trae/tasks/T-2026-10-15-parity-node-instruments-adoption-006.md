# Treelearn Harness Task: 1:1 闭环量测的两支 Node 工具收编入库

id: T-2026-10-15-006
title: 1:1 闭环量测的两支 Node 工具收编入库
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-10-15-web-h5-android-parity-requirement-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 260
shared_code_touched: false
depends_on: [T-2026-10-15-parity-measurement-instruments-adoption-003.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: T-2026-10-15-003 只收编了 Python 侧（像素实测 + 读图探针）。1:1 判据的另一半——把高保真原型渲染出来并量 DOM 几何/计算样式——仍住在 `artifacts/tmp/`：`verify_1to1.mjs` 与 `cdp_shot.mjs`。它们仍是位置参数、只认一条硬编码 URL、缺浏览器时不给「结论不可信」的退出码，因此「渲染侧达绿」同样无法重跑。切片理由与规模约束见 003 的 TDD 记录：两片合计的判定行数会越过 500 硬上限，因此按语言侧分片。
- Users/scenarios: 阶段 1 采集夹具（原型渲染图）、阶段 3–6 逐屏校正（DOM 量测对照像素量测）、阶段 9 收口回归。
- Expected outcome: 两支工具进入 `.trae/scripts/dev/parity/`，按屏名从 `screens.json` 的 `prototype` 段取 HTML 路径、点击序列、等待选择器与 DOM 检查项；保留显式参数逃生口；退出码纪律与 Python 侧一致；缺浏览器/缺 HTML 走 3。
- Core flow and reversibility: `git mv` 保留历史 → 参数化并接档案 → 自检 → 证据落盘；整片可回退，不影响 Android 与前端产物。
- Inputs/outputs: 输入为原型 HTML 的 `file:///` 路径（由档案给出）与屏名；输出为逐条 DOM 判据（JSON 或文本）与 2x 渲染截图，均带命令行、退出码、被核文件哈希。
- State and exception handling: 0=跑完且判据全成立、1=跑完且有判据判 FAIL、2=用法错误、3=仪器自身故障（缺 HTML、缺浏览器、CDP 握手失败、未捕获异常）。「浏览器不可用却打印 PASS」判 FAIL。
- Constraints and trade-offs: 零第三方依赖（Node 内置 fetch/WebSocket + Edge headless CDP），不引 Playwright/Puppeteer；不改判定逻辑，只改调用形态。
- AI recommendation and rationale: 与 003 同一做法——沿用已在 2026-09-27 验证过的闭环，而不是新造一套 DOM 量测。
- User choice and custom additions: 用户批准采用 Harness 与阶段 0 范围；本片是 003 拆分出的后半，属已确认范围内的切片。
- AI-autonomous decisions: 切片边界按语言侧分（Python/Node）；`--screen` 与 `--url` 的优先级；握手失败归 3。
- User-confirmation decisions: 无新增待确认项。
- Acceptance evidence: `--selftest` 正反用例清单与退出码；对 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的对话页闭环一次跑通，读数与复刻方案 §9.1 的渲染侧记录逐项可比。
- Facts: 两支文件存在且均已纳入 git（`artifacts/tmp/verify_1to1.mjs`、`artifacts/tmp/cdp_shot.mjs`）；`screens.json` 的 `chat.prototype` 段已在 003 落库，含 `html/clicks/wait_for/dom_checks`；`cdp_shot.mjs` 已有 `exit(2)` 的用法分支，`verify_1to1.mjs` 没有。
- Assumptions: 本机 Edge 可执行文件仍在默认路径、CDP 端口 9222 未被占用（需实测，不假设）。
- Unknowns: 平板档与 PC 档是否需要第三套裁剪参数（沿用 003 的未决项）。
- Risks: 渲染侧判据依赖浏览器版本，跨版本几何可能漂移；因此判据必须同时打印浏览器版本，并把「版本相关文案/几何」当外部契约处理。
- Deferred: 逐屏夹具采集（阶段 1，需真机）。
- Non-goals: 不做 CI 集成、不引浏览器自动化库、不改原型 HTML 的样式。
- Skipped non-critical questions and reason: 工具命名沿用 Python 侧的 `snake_case` 习惯，不实质影响验收，由架构师定名。

## Goal

两支 Node 闭环量测工具在 `.trae/scripts/dev/parity/` 下可按屏名运行，与 003 的像素实测共用同一份屏档案与同一套退出码纪律，任何渲染侧结论都能由自证证据文件重跑。

## Scope

### In scope
- `git mv` 两支工具到 `.trae/scripts/dev/parity/`（保留历史）。
- 参数化：`--screen`（从 `screens.json` 取 `prototype.html/clicks/wait_for/dom_checks`）、`--url`/`--out`/`--profile` 逃生口、输出目录。
- 退出码纪律 0/1/2/3；缺 HTML、缺浏览器、CDP 握手失败一律 3；未捕获异常归 3。
- `--selftest`：每条检测分支各配「注入该违规必须判 FAIL」与「健康样本必须判 PASS」；浏览器不可用分支必须给出可复现的负向注入（如指定的浏览器可执行文件不存在），不得只打印结论。
- 证据文件写入 `artifacts/evidence/`（UTF-8，含命令、退出码、被核文件 SHA-256 与行数，逐行流式写）。

### Out of scope
- 不改 DOM 判定的阈值与算法、不采集新屏的基线、不引第三方自动化依赖。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 保留 2026-09-27 闭环的读数口径（对话页渲染图的 DOM 几何必须仍与像素实测对照表逐项成立）；变化只在调用形态与文件位置。
- Producer/consumer/sibling entrypoints and platform branches: 生产者为两支脚本；消费者为复刻方案 §9.1 的对照表、003 的 `screens.json`、阶段 1 夹具采集脚本；`measure_screen.py` 的 `--kind render` 制式依赖 `cdp_shot.mjs` 的 2x + 滚动条占位这一产图约定，改产图约定必须同步该制式常量。
- Native bridge or independent-client compatibility/update requirements: 不适用，无桥接。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本片改变工具可调用性，需按行为变更取证。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 两支工具的路径与调用方式。
- Authoritative code/config/migration evidence: 新路径下的脚本文件本身、`screens.json` 的 `prototype` 段与其 `--selftest` 输出。
- Documentation surfaces checked: `Docs/04-ux-design/知芽-TreeLearn-对话页1比1复刻方案-V1.0.md`（工具表 §281–292 仍写着 `artifacts/tmp/verify_1to1.mjs`、`artifacts/tmp/cdp_shot.mjs`）、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §10（003 留下的「剩下两支仍在 artifacts/tmp」的 carve-out）、`.trae/tasks/T-2026-10-15-parity-measurement-instruments-adoption-003.md` 与本片自身的范围说明。
- Documentation files updated: 复刻方案工具表的路径行与「路径变更」标注段、规格 §10 的 carve-out 句子（解除「剩下两支」的现在时表述）、`.trae/README.md` 若有工具索引。
- Producer/consumer/sibling/compatibility paths checked: 反向搜索 `artifacts/tmp/verify_1to1`、`artifacts/tmp/cdp_shot` 两串，命中必须清零或显式标注为历史。
- Superseded phrases/examples reverse-searched: 「剩下两支仍未收编」「位于 artifacts/tmp 的 Node 工具」。
- documentation impact: none reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 复刻方案是带日期的实测记录，只更新工具路径引用与变更标注，不改写当年的量测结论。

## Acceptance criteria

- [ ] 期望行为：`node .trae/scripts/dev/parity/verify_1to1.mjs --screen chat` 与 `node .trae/scripts/dev/parity/cdp_shot.mjs --screen chat --out <png>` 退出码 0，并打印本次使用的 HTML 路径、点击序列、浏览器版本与每条判据。
- [ ] 保持行为：对同一张对话页原型闭环重跑，DOM 读数与复刻方案 §9.1 的渲染侧记录逐项可比；不可比必须给出原因而不是改期望值。
- [ ] 相邻失败：缺 HTML 文件、缺浏览器（指定的可执行文件不存在）、CDP 握手失败三种情形必须分别落到 3/3/3，用法错误落 2，「什么都没验证却打印 PASS」判 FAIL。
- [ ] 受影响入口：文档内旧路径引用清零；`git log --follow` 能证明是移动而非重写（阈值不达时按 003 的做法记录能配上的 `-M` 分数）。
- [ ] 证据文件自证（命令 + 退出码 + 被核文件哈希），UTF-8 合法且无替换字符；`screens.json` 新增字段与 003 的档案兼容（旧屏档案缺 `prototype` 段时必须判 3 而不是静默空跑）。

## TDD execution record

### RED — failing test first
- Test/scenario: 两支 Node 工具的新路径不存在、无 `--screen`、缺浏览器不给 3。
- Command: PENDING
- Exit code: PENDING
- Observed failure: PENDING
- Failure reason validated: PENDING

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 与 003 共用的纪律：总退出码由 want/got 期望表派生，不得硬写 0；每个判定块除退出码外还要有一条输出存在性见证（003 的块 12 实测证明「只比退出码」会把「变异从未执行」读成「变异被判红」）。
- Edge 可执行文件路径不得写死单一位置；至少要允许 `--browser` 覆盖，并把实际使用的版本打印进证据。
- 中文路径经 argv 传给子进程时按 shell 变量传，不在 here-doc 里写 `\xNN` 字节转义（Python 的 str 字面量里 `\xe7` 是字符而非 UTF-8 字节，实测把文件名拼成 mojibake 后判据退化成「零命中 = 干净」）。

## Verification report

- Verdict: PENDING
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: 平板档/PC 档裁剪参数未验证；跨浏览器版本的几何稳定性只在 Edge 一个版本上实测。
- Risks: 渲染侧判据依赖浏览器版本；握手失败与页面未渲染完成的区分必须落到不同判据行，否则「超时」会被读成「实现不符」。

## Handoff notes

- Completed: 前置事实核实（两支文件存在且均已跟踪、`chat.prototype` 档案段已在 003 落库、原型 HTML 在 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 实际存在）。
- In progress: 无。
- Blocker: 依赖 003 落库的 `screens.json` 档案结构。
- Unblock condition: 003 的档案与退出码纪律定型。
- Next steps: 006 完成后，复刻方案 §10 的「instrument 未入库」carve-out 全部解除，阶段 1 的夹具采集（005 之后的屏档案扩展）才有可重跑的两侧仪器。
- Watch-outs: 不要把「浏览器没起来」压绿成「用例不符」——那是仪器故障（3），不是判据失败（1）。
