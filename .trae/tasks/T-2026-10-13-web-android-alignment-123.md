id: T-2026-10-13-123
title: Web 对齐 Android 体验基线（回退纸上书房 + 逐屏重排）
template_version: 2
task_type: requirement
status: ready
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: Web 本地版自 T-030 起按后端任务自底向上搭建，并落成一套独立视觉（《纸上书房》，色板在 `Docs/04-ux-design/web-local-design-tokens.json`）。用户 2026-10-13 00:18 反馈「明明 Android 已有可用代码，Web 却像重写的一套，效果变了、交互变了」，要求以 Android 为基线做桌面适配，而不是再造一套。
- Users/scenarios: 单人本机用户，在电脑浏览器完成「首页首问 → 对话追问 → 划词/分叉 → 学习库检索 → 设置」的完整学习链路；期望与 Android 同名屏幕产生同样结果。
- Expected outcome: 同一状态下的同一操作，Web 与 Android 结果一致；桌面只额外承担「侧栏常驻 + 更宽阅读区」。
- Core flow and reversibility: 首页首问 → 分支对话（流式/停止/重试）→ 划词与分叉 → 学习库与设置 → 返回原处继续阅读。全程不停服、不重建数据库。
- Inputs/outputs: 输入为 Android 当前源码（布局 + Presenter/Screen）作为行为与视觉基线；输出为 Web 前端页面、样式与少量后端派生端点补齐。
- State and exception handling: 生成终态（stable/failed/interrupted/canceled）必须分别解除限制或给出重试；阅读位置与未读以视口可见为准。
- Constraints and trade-offs: Android UI 为 Java+XML、Web 为 React+TS，**UI 层无法字面复用**；可复用的是「每个 Android 屏幕作为结构/状态/动作契约」+ `web/shared` 领域模块（已共享，见 `android/settings.gradle:22-31`）。取舍结果是「按屏幕逐个重排」，而不是把某份 Android 文件搬到浏览器里跑。
- AI recommendation and rationale: 建议「回退 Web 独立视觉 + 以 Android 屏幕为契约逐屏重排」。理由：两套色板必然持续漂移，差异已在主色上肉眼可见（Android green `#2B6E45` / green-deep `#164027` vs Web 统一 `#3E5C41`）。代价：今天已落地的纸感 CSS 需按 Android token 重校对，200+ 测试中有部分与新口径相反需一并改写成可证伪断言。
- User choice and custom additions: **用户 2026-10-13 00:18 拍板：「回退纸上书房 + 逐屏重排，按 harness+goal 方式执行」。**
- AI-autonomous decisions: ① 保留 2026-10-12 落地的中性基建（`IconButton showLabel` 等），不与本次目标冲突的部分不回退；② 任务按波次创建，波内任务文件在本父需求下逐个建立并记录 `depends_on`；③ 每屏移植以「Android 布局 + Presenter」为契约来源。
- User-confirmation decisions: 《纸上书房》token、整站 Overview HTML、`知芽-Web本地版-交互原型-V0.1.html` 三件转为历史说明，不再作为当前 Web 目标；Web 不再定义自有色值，颜色唯一来源为 `Docs/04-ux-design/design-system/tokens.json`。
- Acceptance evidence: 各子任务的 RED/GREEN 记录 + 证据文件；最终以真实浏览器学习链路 + 与 Android 同场景对比收口。
- Facts（本轮 23:55 源码核对，可复查）:
  - 推荐 chip：Android 点击即发送 `android/app/src/main/java/com/treelearn/app/ui/ChatPresenter.java:745-751`；Web 只填草稿 `web/frontend/src/pages/BranchWorkspace.tsx:269`。
  - 回答动作行：Android 4 图标 + 时间 `android/app/src/main/res/layout/view_answer_actions.xml:29-129`；Web 7 个带文字按钮 `web/frontend/src/components/MessageView.tsx:182-231`。
  - 划词菜单：Android 6 格 `android/app/src/main/java/com/treelearn/app/screen/WordMenuPopup.java:124-136`；Web 4 个文字按钮且无「问 AI」 `MessageView.tsx:233-261`。
  - 生成闭环：`activeOperationId` 终态后无清除点（唯一清除点 `BranchWorkspace.tsx:127`），`canSend` 恒要求 `activeOperationId === null`（`:288`）。
  - 未读：Web 进分支即清 `BranchWorkspace.tsx:76-79`，与 Android 视口规则相反。
  - 首页结构：Android `res/layout/screen_home_body.xml:9-285`（品牌 lockup、hero、书写格线、模型胶囊、推荐条、最近主题带数量）；Web `HomePage.tsx:66-92`。
  - 后端缺口：`DerivedController.java:42-44,77-80` 对 ROOT_TITLE/BRANCH_TITLE/BRANCH_SUMMARY/ANSWER_RECOMMENDATIONS/HOME_RECOMMENDATIONS/CONTEXT_COMPRESSION 返 400；`CoreApiController.java:94-96` 的 `recommendations` 恒为空数组。
  - 侧栏已存在：`AppShell.tsx:80-105` + `global.css:170`（88px，≥1024px 展宽 220px）→ 不需要新建，改内容即可。
- Assumptions: Android 当前代码即期望行为（未被 2026-10-09 及之后的决定推翻的部分）。
- Unknowns: 分支导航页是否独立成 Web 路由、树图入口的最终位置、派生任务失败后的用户可见重试形态。
- Risks: ① CSS 大面积改动可能短期视觉半成品 → 以波次隔离，每波终态可运行；② 既有测试断言与新口径相反（如「进入分支即已读」）→ 必须在同一任务内改成可证伪断言；③ 后端派生补齐涉及契约变更，需独立任务并走 contract 复核。
- Deferred: 分支导航页独立路由、文件/语音设置页补齐、图标资产按 Android drawable 逐个同形替换。
- Non-goals: 新视觉风格、账号/同步/云端、重建数据库、改 Android 源码与其 token。
- Skipped non-critical questions and reason: 未再询问「是否保留侧栏常驻 + 折叠」——现有 `AppShell` 已具备该能力（`AppShell.tsx:80-105`、`global.css:170`），本需求只改其内容，不改变是否存在。

## Goal

以 Android 当前屏幕为结构与行为契约，重排 Web 前端（首页 / 对话 / 划词 / 学习库 / 树 / 设置 / 外壳），并回退 Web 独立视觉《纸上书房》到 Android 基线；最终「同一操作同结果」。

## Scope

### In scope
- Web 视觉回到 Android 基线（色板、表面层次、控件形制）
- 生成闭环、阅读恢复与未读口径、返回语义
- 对话页 / 首页 / 划词浮条 / 回答动作行 / 推荐 chip 的重排
- 外壳与侧栏内容调整（去布局 A/B/C 切换，树图回 Android 入口语义）
- 后端派生能力缺口（推荐/标题/摘要/压缩）补齐

### Out of scope
- Android 源码与其 design token 的改动
- Android 与 Web 数据互通/同步
- 新功能平台、账号体系

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 改变 Web 页面结构与交互语义；保留已持久化的 SQLite 数据、契约 §6.x 端点与既有 Bug 气泡。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 前端 `pages/*`→`components/*`→`store/*`→`api/*`；后端 `DerivedController`、`CoreApiController`； Android 侧同消费 `web/shared`。
- Native bridge or independent-client compatibility/update requirements: 无原生桥；Web 为独立本机客户端，数据与 Android 互不共享（沿用既定边界）。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: Web 不再持有独立色板；Web 交互口径改为「跟随 Android 屏幕」。
- Authoritative code/config/migration evidence: `web/frontend/scripts/lib/build-tokens.mjs`、`Docs/04-ux-design/web-local-design-tokens.json`、`web/frontend/src/styles/tokens.css`。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-12-web-android-experience-alignment.md`、`Docs/04-ux-design/知芽-Web本地版-原型说明-V0.1.md`、`Docs/05-technical-design/TreeLearn_Web本地版_详细开发方案_V1.0.md:160`、`Docs/04-ux-design/web-local-assets/brand-spec.md:14`。
- Documentation files updated: 随子任务逐项记录（本轮暂未改）。
- Producer/consumer/sibling/compatibility paths checked: token 三方守门 `.trae/scripts/dev/check_design_tokens.py`、Web 守门 `check-design-tokens.mjs`、`check-contrast.mjs`。
- Superseded phrases/examples reverse-searched: 待各波结束时统一反向搜索「纸上书房 / mint / Web token 独立定义」。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 《纸上书房》整套产物转为历史说明并显式标注作废日期与替代入口。

## Acceptance criteria

- [ ] Web 颜色唯一来源为 `Docs/04-ux-design/design-system/tokens.json`，令牌文件不含任何独立色值（证据：`tokens.css` 无 `--web-*` 命名空间 + 三方守门 0 failed）
- [ ] 首页首问 → 生成完成 → 再追问，无刷新完成连续问答，且不会再出现「生成后输入框永久禁用」（证据：vitest 场景 + 真实浏览器）
- [ ] 回答动作行为 4 个图标 + 时间（纯图形），与 `view_answer_actions.xml` 同序
- [ ] 划词浮条为 6 格：问 AI / 分叉 / 高亮 / 批注 / 复制 / 全选
- [ ] 推荐 chip 点击直接发送追问（对齐 `ChatPresenter.onRecommendationFollowed`）
- [ ] 进入分支不清未读；看到最新回答后标记已读
- [ ] 返回按入口还原目标，而非固定回主页
- [ ] 各波结束时 `pnpm test` / `pnpm lint` / `pnpm build` / 三方守门全绿，且每条新断言经变异检验

## TDD execution record

不适用（父需求不实现代码）；RED/GREEN 记录在子任务。

## Implementation notes

- 波次计划：
  - W1 视觉基座：T-124 颜色回到 Android 基线；T-125 全局样式形制回退
  - W2 可用性闭环：T-126 生成终态闭环（恢复追问 / 流式进回答位 / 停止保留 / 失败原位重试）；T-127 阅读恢复与未读口径
  - W3 对话页重排：T-128 回答动作行、T-129 划词浮条、T-130 推荐即发送与返回语义
  - W4 首页与外壳：T-131 首页结构、T-132 侧栏内容与布局切换收敛
  - W5 后端与其余页面：T-133 派生能力补齐、T-134 学习库/树/设置补齐
- 任务文件按波逐个建立，均引用本父需求。

## Verification report

- Verdict: PENDING
- Evidence: 待子任务完成后汇总（每任务自带命令、退出码与观察结果）
- Changed/deleted assertion rationale and equivalent behavior coverage: 与 Android 口径相反的旧断言（如「进入分支即已读」）将在 T-127 改写并做变异检验。
- Gaps: 首轮未运行画面/设备对比，需浏览器与 Android 同场景证据。
- Risks: 见 Risks。

## Handoff notes

- Completed: 需求核对与决策落地（父需求记录）
- In progress: W1 T-124
- Blocker: 无
- Unblock condition: 不适用
- Next steps: 建立并执行 T-124（Web 颜色回到 Android 基线）
- Watch-outs: 《纸上书房》作废时必须同步历史文档指向，否则后续会话会把它读成当前目标。
