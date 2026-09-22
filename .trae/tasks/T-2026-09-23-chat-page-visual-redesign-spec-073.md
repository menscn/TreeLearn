# Treelearn Harness Task: 对话页视觉重做规格

id: T-2026-09-23-073
title: 对话页视觉重做规格
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-09-23-rikkahub-functional-visual-absorption-071.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-23-rikkahub-functional-visual-absorption-071.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: RikkaHub 功能与对话页视觉分层吸收；本任务只定义最终视觉与交互状态，不做现有原型对比。
- Problem/background: 当前知芽对话页已有纸感 token、消息流、思考披露、来源卡和输入栏，但 AI 消息层级、生成态动效、输入栏状态和错误/取消/滚动反馈还没有一份可直接实现的统一规格。
- Users/scenarios: 学习者阅读长回答、观察思考流式输出、上滑查阅旧内容、从句子或整条回答分支、在键盘弹起和生成失败时继续操作。
- Expected outcome: `Docs/04-ux-design/知芽（TreeLearn）对话页视觉重做规格_V1.0.md`，直接描述手机/平板最终视觉，包含状态矩阵、组件尺寸/层级、动效时序、文案槽位、触达与无障碍要求。
- Core flow and reversibility: 进入对话 → 阅读消息 → 生成中 → 手动上滑/恢复跟随 → 结束/失败/取消 → 通过动作或输入栏继续。规格不改数据模型，允许后续视觉实现逐片回滚。
- Inputs/outputs: 输入 RikkaHub 的可借鉴视觉与行为事实、知芽 token/Android 约束；输出视觉规格和实现任务引用。
- State and exception handling: 必须覆盖 empty/loading/reasoning/answering/stable/canceled/failed/archived/no-key/keyboard-open/low-motion/large-screen 状态；每个状态要说明视觉、可操作性和恢复路径。
- Constraints and trade-offs: 只用知芽 token；触达区 ≥44dp；Java/XML；不引入 Compose；动效必须可关闭或降低；不把 AI 消息默认做成层层嵌卡；保留树状来源可见性。
- AI recommendation and rationale: AI 消息采用“轻容器 + 明确元信息 + Markdown 内容层”的层级，生成中用小幅局部动效而不是整卡跳动；输入栏固定底部、随 IME 安全区移动，状态由 send/stop/disabled 三态表达。
- User choice and custom additions: 用户要求重点重做 AI 消息卡、生成动效、输入栏，并明确无需现有原型对比；本任务按此直接写目标规格。
- AI-autonomous decisions: 不新增暗色主题；动效默认克制、低动效模式静态化；视觉规格中的具体 token 名优先复用当前 token，缺失才列新增 token 候选。
- User-confirmation decisions: AI 消息最终采用无卡片/轻卡片的取舍、生成动效强度、输入栏是否显示附加能力入口，均在规格 review 时拍板。
- Acceptance evidence: 文档存在且 UTF-8；覆盖所有状态/组件/尺寸/动效/无障碍验收；每一项可映射到 074–076 的实现文件与测试观察。
- Facts: 知芽当前 `ChatScreen` 使用 XML/View，`item_chat_message.xml` 已有 reasoning/answer/failure/actions，`screen_chat_body.xml` 已有 composer/source/scroll；RikkaHub 的消息组件包含 Markdown、思考卡、动作条、分支选择器、动画内容尺寸和毛玻璃输入栏。
- Assumptions: 视觉规格不要求本轮输出截图或重写现有原型；实现任务会使用真实 Android 布局和 Java 状态。
- Unknowns: 真机上的字体度量、长答案重绘成本、键盘厂商差异需实现后测量。
- Risks: 规格过于像素化而无法复用 token；动效与流式重绘互相打架；只定义正常态而遗漏失败/取消/归档态。
- Deferred: 真机截图、动效帧率和具体图标形状由实现任务验收；新字体和深色主题另立任务。
- Non-goals: 不比较现有原型优劣；不修改生产代码；不改变分支/提示词/存储契约。
- Skipped non-critical questions and reason: 本任务先定义可实现的体验骨架，具体视觉偏好集中在用户 review 的有限拍板项中。

## Goal

产出一份可直接指导 Android 实现的对话页视觉重做规格，覆盖 AI 消息卡、生成态动效、输入栏及其所有关键状态。

## Scope

### In scope
- AI 消息：身份标识、正文/Markdown、reasoning、代码块、引用/来源、动作条、版本/分支入口、失败与部分回答。
- 生成体验：首字出现、reasoning→answer 阶段、光标/微动效、停止、取消、网络重试、结束折叠、局部重绘和低动效降级。
- 输入栏：多行高度、IME 安全区、草稿、引用/分支上下文、模型/思考入口、send/stop/disabled 三态、错误和归档态。
- 滚动与响应式：自动跟随、上滑让位、恢复跟随、新消息提示、阅读位置、手机/平板横屏、44dp 热区、TalkBack 标签。
- 生成 074–076 的文件/状态/验收映射。

### Out of scope
- 修改现有 HTML 原型或建立原型对比报告。
- 数据库/网络协议/提示词/分支模型修改。
- 暗色主题、字体打包、新 UI 依赖。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 本任务只新增视觉规格；后续实现必须保留现有消息顺序、分支来源、版本切换、草稿和阅读位置语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatViewState` 生产状态，`ChatPresenter` 做决策，`ChatScreen`/XML 消费；手机与 `layout-sw600dp` 均需映射。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 纯文档任务。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无；后续实现会改变对话页表现。
- Authoritative code/config/migration evidence: `android/app/src/main/java/com/treelearn/app/screen/ChatScreen.java`、`ui/ChatViewState.java`、`ui/ChatPresenter.java`、相关 XML、`Docs/04-ux-design/design-system/tokens.json`。
- Documentation surfaces checked: `Docs/04-ux-design/`、`Docs/03-prd-draft/`、T-066、T-065 和 067–070。
- Documentation files updated: `Docs/04-ux-design/知芽（TreeLearn）对话页视觉重做规格_V1.0.md`。
- Producer/consumer/sibling/compatibility paths checked: 规格内逐组件列出状态生产者、布局消费者和 Android 版本/屏幕分支。
- Superseded phrases/examples reverse-searched: 「AI 消息卡」「生成中」「输入栏」「自动跟随」「思考默认折叠」等当前描述，标注本规格是目标态还是历史基线。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 规格 review 通过后，若与 PRD 冻结条款有冲突，必须另立口径澄清任务，不静默覆盖 PRD。

## Acceptance criteria

- [ ] 规格不引用现有原型对比，不把 RikkaHub 像素或文案当作知芽要求。
- [ ] 有完整状态矩阵：空、生成、思考、回答、稳定、取消、失败、归档、无 Key、键盘、低动效、平板。
- [ ] AI 消息、生成动效、输入栏每项都有层级/尺寸/颜色 token/动效/交互/无障碍说明。
- [ ] 规格能逐条映射到 074–076 的实现文件、单测和人工观察证据。
- [ ] 通过 UTF-8/占位符检查和 `python .trae/scripts/validate_tasks.py`。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（视觉规格文档）。
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 产出《对话页视觉重做规格 V1.0》
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 2026-09-23 执行 `SUMMARY files=76 errors=0`；规格落盘 `Docs/04-ux-design/知芽（TreeLearn）对话页视觉重做规格_V1.0.md`，含 12 态矩阵、AI 消息六层结构、生成动效三节（含布局稳定性总则）、输入栏六目、滚动四态、无障碍、074–076 映射表与新增 token 候选清单
- Regression evidence: 不适用（无生产代码）

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（文档任务）。
- Reason: 规格正确性由覆盖矩阵、token 映射和用户 review 验证。
- User approval: 用户已明确要求重点重做对话页视觉。
- Alternative verification: 文档结构检查、token/状态反向核对、实现映射审查。

## Implementation notes

- 文档中每个视觉数字必须引用 token 或明确列为待新增 token 候选，不写孤立裸 hex/px。
- 生成动效必须定义“内容变化时的布局稳定性”：光标、点状指示器和错误条不得改变相邻控件命中位置。
- 输入栏必须定义草稿保留、IME 调整、旋转/重建和归档分支的行为；不能只写静态布局。

## Verification report

- Verdict: PASS（用户 2026-09-24 00:39 指令「执行」＝对第 10 节四项拍板全部采纳推荐：① AI 消息用「无卡片+左缘标识」；② 动效采用克制档全量；③ 「回到最新」用浮钮；④ 长列表跳转浮条不阻塞、可选项）
- Evidence: 规格存在（UTF-8、无占位符）；状态矩阵覆盖 12 态且每态有触发/视觉/可操作性/恢复；全部视觉数值引用 tokens.json 既有 token（bg/surface/ink*/line/green*/red*/amber*/space/radius/fontSize/shadow/lh-read），动效时长以「新增 token 候选」形式列出（§9）未写死；实现映射表逐条落到 074–076 的文件与验证方式；未引用现有原型对比，未复制 RikkaHub 像素/文案。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用。
- Gaps: 真机字体度量、流式重绘帧率、IME 厂商差异待实现任务验证；知芽现状的 UNKNOWN 项（Markdown 覆盖面等）已标出。
- Risks: 状态矩阵若未锁定，074–076 会各自发明状态——已通过 §8 映射表把 12 态指派到任务；动效与流式重绘的冲突由布局稳定性总则约束。

## Handoff notes

- Completed: 任务契约已生成。
- In progress: 待撰写视觉规格。
- Blocker: 无。
- Unblock condition: 规格 review 通过。
- Next steps: 规格确认后按 074 → 075 → 076 执行。
- Watch-outs: 不把当前 HTML 原型当作对比对象；它只能作为设计 token 的事实源，视觉目标以本规格为准。
