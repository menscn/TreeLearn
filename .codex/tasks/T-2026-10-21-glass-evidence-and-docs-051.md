# Filename: T-2026-10-21-glass-evidence-and-docs-051.md
# Codex Harness Task: 玻璃材质整跑、真机取证与文档收口

id: T-2026-10-21-051
title: 玻璃材质整跑、真机取证与文档收口
template_version: 2
task_type: verification
status: verified
parent_requirement: T-2026-10-21-glass-material-requirement-047.md
requirements_confirmed: true
generated_by: agent
owner: verifier
owner_lane: verification
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-10-21-android-glass-surfaces-049.md, T-2026-10-21-web-glass-workspace-050.md]
tdd_mode: not-applicable

## Requirement collaboration brief

- Parent summary: 047 确认方向与取值；048–050 分别落地 token、Android 与 Web；本片只整跑、取证与文档收口，不再改生产代码。
- Problem/background: 材质改动横跨设计 token、Android 资源与 Web 样式，单卡各自为绿不足以证明两侧消费同一批值、既有守门未被破坏。
- Users/scenarios: 用户验收桌面浏览器与真机四屏；后续维护者读文档知道材质与降级规则。
- Expected outcome: 一份可复跑的整跑记录、真机 `adb install -r` 截图、Playwright 桌面与窄窗截图、设计系统/Android/提案文档同步，以及 047–051 状态收口。
- Core flow and reversibility: 汇总 048–050 读数 → 跑 token/任务/核心守门 → 浏览器截图 → 真机安装截图 → 写证据与文档 → 标记任务。只读验证，可重复执行。
- Inputs/outputs: 输入是 048–050 的产物与命令；输出是 `artifacts/evidence/glass-material-2026-10-21/` 下的截图、`readings.json`/`README.md` 与文档修订。
- State and exception handling: 任一守门红或截图缺失都如实记录为 GAP，不把未执行项写成 PASS；真机数据未被清理需在截图/读数中说明。
- Constraints and trade-offs: 不跑会卸载应用的全量 instrumentation；不自动提交或推送；不改业务代码。
- AI recommendation and rationale: 先把可机械裁决的守门跑完，再做视觉取证；文档只写当前态与降级边界，不复述 token 数值。
- AI-autonomous decisions: ① 证据目录固定为 `artifacts/evidence/glass-material-2026-10-21/`；② 真机四屏用 `adb install -r` 后逐屏截图，不跑 connected 测试；③ 文档只追加 2026-10-21 条目；④ 若既有守门有红项，按既有基线如实记录并在 Gaps 说明。
- User-confirmation decisions: 见父卡 047。
- Acceptance evidence: 见本卡 Acceptance criteria 与 Verification report。
- Facts: 设备 `ANRBBB5427000010`，2136×3200，density 420；包名 `com.treelearn`；Web 证据脚本与 Playwright 在既有证据目录已有先例。
- Assumptions: 设备仍在线且应用已安装；浏览器截图可用既有工具链。
- Unknowns: 真机厂商色偏；Web blur 在截图中的实际强度。
- Risks: 全量 instrumentation 会清数据，必须避免；`adb install -r` 失败时不能改为卸载重装。
- Deferred: 性能基准、深色模式、Figma 同步。
- Non-goals: 不改生产代码、不改文案、不新增依赖、不提交。
- Skipped non-critical questions and reason: 未追问截图具体屏数；四屏（首页/对话/学习库/设置）足以覆盖共享表面。

## Goal

用可复跑的守门、真机与浏览器证据证明玻璃材质在 Android 与 Web 上一致落地，并完成文档与任务状态收口。

## Scope

### In scope
- 跑 `validate_tasks.py`、`check_design_tokens.py`（含 `--selftest`）、`gen_design_tokens_css.py --check`、`run_all_guards.py --tier core`、`pnpm --dir frontend verify`、Android 构建/单测/无 Kotlin 守门。
- `adb install -r` 后四屏截图与数据保留说明。
- Playwright 桌面与窄窗截图（含侧栏开合）。
- `artifacts/evidence/glass-material-2026-10-21/` 的 README 与读数。
- 设计系统 README、Android README、提案 README 的落地状态。
- 047–051 状态与 Verification report。

### Out of scope
- 生产代码修改（发现缺陷时回到对应子卡修复）。
- 全量 instrumentation。
- 自动提交/推送。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 仅取证与文档；不改变运行时。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 048 的 token 四方、049 的 Android 资源/场景、050 的 Web 样式层。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: 本片不修改生产代码。

## Documentation impact record

- Changed runtime facts: 设计系统新增玻璃材质；Android 静态柔焦场景与玻璃表面；Web `backdrop-filter` 与回退。
- Authoritative code/config/migration evidence: 原型 `:root`、`tokens.json`、`colors.xml`、`glass.xml`、共享 drawable、`PaperRuleBackdrop`、`glass.css`。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`android/README.md`、`Docs/04-ux-design/2026-10-21-玻璃质感重设计提案/README.md`、`Docs/04-ux-design/知芽-TreeLearn-Web学习工作区-交互示意-v1-说明.md`、`.codex/context/source-of-truth.md`。
- Documentation files updated: 上述文件追加 2026-10-21 落地状态与证据路径；不复述 token 数值。
- Producer/consumer/sibling/compatibility paths checked: 设计系统四方、Android phone/tablet、Web 桌面/窄窗。
- Superseded phrases/examples reverse-searched: 「未落地生产」「纸感不透明表面」等状态描述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 保留历史提案与旧材质记录，只追加落地状态。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/validate_tasks.py` 退出码 0。
- [x] `python -X utf8 .codex/scripts/dev/check_design_tokens.py` 与 `--selftest` 退出码 0；`gen_design_tokens_css.py --check` 退出码 0。
- [x] `python -X utf8 .codex/scripts/run_all_guards.py --tier core` 退出码 0，或如实列出既有红项及基线。（实际退出码 3：37 绿 / 0 红 / 0 连带 / 2 超时。两条浏览器仪器在打印 `SELFTEST PASS` 与 `EXITCODE=0` 后不退出，撞 600s 上限；2026-10-20 基线为 39/39 绿，原始读数与处置见 `artifacts/evidence/glass-material-2026-10-21/051-core-guards-final.txt` 与证据 README。）
- [x] `pnpm --dir frontend verify` 退出码 0。
- [x] Android `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`verifyNoKotlinDex` 退出码 0。
- [x] 真机四屏截图存在且可见玻璃场景/高光边；`adb install -r` 未清理既有数据（有读数为证）。
- [x] Playwright 桌面与窄窗截图存在；窄窗侧栏开合正常。（工具偏差：本机未安装 Playwright，改用仓库既有 CDP/Edge 驱动，8/8 读数与 8 张截图等价，见 Verification report。）
- [x] 证据 README 写明环境、命令、预期/实际、GAP 与风险。
- [x] 047–051 状态与 Verification report 更新；未提交、未推送。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（验收卡）
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
- Reason: 验收卡不写生产代码；其判据是 048–050 的 TDD 与整跑读数。
- User approval: 用户 2026-10-21 已确认 Harness 调度。
- Alternative verification: 本卡 Acceptance criteria 的命令与截图。

## Implementation notes

- 证据目录：`artifacts/evidence/glass-material-2026-10-21/`。
- Android 真机步骤：`./gradlew :app:assembleDebug` → `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` → 逐屏 `adb shell screencap`。
- Web 步骤：`pnpm --dir frontend dev` 后 Playwright 打开桌面 1440×900 与窄窗 390×844，截侧栏开合与对话页。
- 文档只追加；不复述 token 数量与数值。
- 若任一项无法执行，在 Verification report 的 Gaps 写明原因，不标 PASS。

## Verification report

- Verdict: PASS（核心整跑的非绿项为两条浏览器仪器超时，已按基线如实登记，不记为绿）
- Evidence: `validate_tasks.py` 46 文件 0 错误；`check_design_tokens.py` 63/63/20/63、自检 41 例、生成物 2065 字节一致；`pnpm --dir frontend verify` 16/16；Android 全量 JVM 859 例 0 失败、`assembleDebug` + `assembleDebugAndroidTest` + `verifyNoKotlinDex` 0；真机 `adb install -r` 四屏截图与原始库恢复读数；浏览器 8/8 读数与 8 张桌面/窄窗截图；核心整跑 `pass=37 fail=0 inconclusive=0 fault=0 timeout=2`、`EXITCODE=3`（`051-core-guards-final.txt`）。以上原始记录均在 `artifacts/evidence/glass-material-2026-10-21/`，索引见其 `README.md`。
- Changed/deleted assertion rationale and equivalent behavior coverage: `HomeBackdropInstrumentedTest` 的 tab bar 判据由「透明或 null」改为「非 null 且 `getOpacity() != PixelFormat.OPAQUE`」，保留「页面不各自拥有第二份背景」的断言；`ChatMotionLayoutStabilityTest` 的失败条仍断言软红 token，承载从 `bg_card + tint` 换为独立 `bg_semantic_alert`。两者都未削弱原行为覆盖。
- Gaps: 核心整跑无法在本机退出 0——`verify_1to1_selftest` 与 `cdp_shot_selftest` 打印 `SELFTEST PASS`/`EXITCODE=0` 后，Node 事件循环被 Edge 句柄保持，撞 600s 上限；2026-10-20 基线同两条为 217.1s / 39.8s 且全绿，单独复跑同样打印 PASS 后挂住并手动终止。本机未安装 Playwright，浏览器取证使用仓库既有 CDP/Edge 驱动。真机只覆盖手机竖屏；对话/学习库来自临时种子库并已恢复；未做厂商色偏、正式对比度仪器测量、低端帧率基准与深色模式。
- Risks: 半透明白在厂商屏可能色偏；多层半透明与 `backdrop-filter` 可能有帧率损失；新增表面若用实色 drawable 会绕过玻璃层；`backgroundTint` 会抹掉 1px 高光边；`backdrop-filter` 合成层与窄窗覆盖层的 transform 可能冲突；生成型计数写进正文会让事实源守门再红。

## Handoff notes

- Completed: 整跑与轮后复跑、真机 `adb install -r` 四屏与数据恢复、浏览器 8/8 读数与截图、证据 README/readings、文档同步、047–051 状态收口。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 若后续要恢复核心整跑全绿，先解决本机浏览器仪器打印 PASS 后不退出的事件循环问题，再重跑；其余材质扩展按 049/050 的共享层执行。
- Watch-outs: 不跑全量 instrumentation；不改生产代码来凑证据；不要把两条超时记成 PASS。
