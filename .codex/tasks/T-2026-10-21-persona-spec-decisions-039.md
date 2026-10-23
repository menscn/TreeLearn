# Filename: T-2026-10-21-persona-spec-decisions-039.md
# Codex Harness Task: 人设规格收口：把两条疑虑拍板、未知值回落与窄写路径写成可拆边界

id: T-2026-10-21-039
title: 人设规格收口与行为样例矩阵
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-10-21-ai-persona-and-forest-requirement-038.md
requirements_confirmed: true
generated_by: agent
owner: docs-dev
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary：`T-2026-10-21-ai-persona-and-forest-requirement-038` 确认先闭合设计再进入实现；本片吃人设设计面的最后两条未决疑虑与缺失的行为样例资产，不改生产代码。
- Problem/background: `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 已把接口、数据结构、状态迁移与错误语义写实，但「Doubts」仍挂着两条未决疑虑：① 未知 wire 值该抛还是回落；② 切换人设算不算一次主题活动。两者都会直接改变实现形状：前者决定 `TopicDao` 读取边界，后者决定是否新增只写 `persona` 的写路径。用户 2026-10-21 已分别拍板「回落默认并提示」与「不刷新活动时间」。此外，intent 把各档行为样例的具体用例留给设计阶段，规格目前只有「按提示词设计 §8.2 做行为样例」这一句，没有可执行的矩阵。
- Users/scenarios: 后续实现者要按规格直接拆迁移、生成链路和 UI 片；评测维护者要按行为样例建立 `.codex/evals/` 用例；用户要在实现前 review 这两条边界是否符合预期。
- Expected outcome: 规格的「Doubts」只剩已拍板记录，未知值回落与窄写路径各有明确落点；新增一节七档 × 六轴的行为样例矩阵与受控问题集，使后续评测有可复算的输入；规格仍保持 `draft`，等用户 review 后再升格。
- Core flow and reversibility: 读规格与 intent → 改写 Doubts 与 Technical spec 的对应条目 → 补行为样例矩阵 → 跑 `check_spec_author_agent.py` 与 `check_pointer_liveness.py` → 交用户 review。回退即恢复规格原两段，不触碰生产代码。
- Inputs/outputs: 输入是 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`、`Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`、`Docs/04-ux-design/design-system/tokens.json`、`android/core/src/main/java/com/treelearn/core/domain/PromptPurpose.java`、`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java`、`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java`、`android/data/src/main/java/com/treelearn/data/db/dao/GenerationStabilizer.java`；输出是更新后的规格正文与守门读数。
- State and exception handling: 未知 wire 值只在读取边界回落默认档，**不进入 `Topic` 构造器抛异常**；回落发生时携带一个可向上传递的信号，供聊天页只 toast 一次，主题列表仍完整可读。写入侧没有未知值，能进列映射的只有枚举 `wireId`。切换人设走只写 `persona` 的窄写路径，不碰 `updated_at`、`last_visited_branch_id` 或整行写回。
- Constraints and trade-offs: spec-author 只写 `Docs/spec/**`；不改生产代码、不改 intent、不改规则、不改 token、不建实现任务；规格 `status` 保持 `draft`；界面方案不得出现裸 hex 或临场 px；行为样例是设计资产，不是模型行为的逐字断言。
- AI recommendation and rationale: 未知值回落放在 DAO/映射边界而不是 `Topic` 构造器：`TopicDao.findById` 被聊天发送链路直接调用，构造器抛错会把「一行坏值」升级成「整条发送链路失败」。窄写路径单独新增 `updatePersona`，不复用 `TopicDao.update()` 的整行写回，因为后者的 `values(topic)` 会同时写 `updated_at`，首页按该列倒序排，点一次人设就会把主题挪到顶部。行为样例矩阵按「起点假设、信息密度、是否反问、是否催继续、情绪表达、是否主动延伸」六轴逐档写，配三组受控问题（概念解释、错误前提、挫败卡壳），只做行为方向断言，不做逐字断言。
- User choice and custom additions: 用户 2026-10-21 拍板未知档位 **回落默认并提示**；切换人设 **不刷新活动时间 / 首页排序**。用户 2026-09-21 已确认 7 档、主题级作用域、介绍页两处入口、静态示例、回答上不标注人设。
- AI-autonomous decisions: ① 规格里新增「行为样例矩阵」作为 `Technical spec` 的子节，而不是新开第五节；② 回落信号命名为「主题读取结果里的一个布尔/枚举标记」，具体 Java 形状留给实现片，规格只写语义；③ 七个 wireId 的命名采用稳定小写 kebab-case，具体值在实现片定，规格只要求稳定、可版本化、不可复用；④ 不把「模型与思考模式仍是全局」的已知缺口塞进本片，它保持 intent 已登记的中间态。
- User-confirmation decisions: 未知档位回落默认并提示；切换人设不刷新活动时间。两项均记录为 2026-10-21 用户拍板。
- Acceptance evidence: `python -X utf8 .codex/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 退出码 0；`python -X utf8 .codex/scripts/check_pointer_liveness.py` 退出码 0；规格中「未决疑虑」为 0，「已拍板」含上述两条；行为样例矩阵覆盖 7 档 × 6 轴与至少 3 组受控问题。
- Facts: 人设在生产代码里尚不存在；`PromptPurpose` 现在是 8 项；`DeepSeekPromptRegistry.systemContent(purpose, thinkingEnabled)` 只对 `LEARNING_ANSWER` 追加静态段，派生用途索要附加段会抛 `THINKING_NOT_APPLICABLE_FOR_PURPOSE`；`TopicDao.update()` 是整行写回且要求恰好命中一行；`TopicDao.findById` 直接 `new Topic(...)`；`GenerationStabilizer` 从操作行拷贝身份到稳定回答；`SettingsRepository` 的模型与思考模式仍是全局设置。
- Assumptions: 用户会 review 行为样例矩阵的区分度；未知值的回落信号可以在不改变 `Topic` 公共构造器的前提下由 DAO 返回；窄写路径可以在 `TopicDao` 内以一条 `UPDATE topics SET persona=? WHERE id=?` 实现，且不需要触发器。
- Unknowns: 回落的「只 toast 一次」应由 DAO、Presenter 还是 ChatScreen 持有，规格只写语义，具体归属在实现片定；「学霸搭子」与「深夜陪读」的区分度是否足够，要用矩阵样例验证；介绍页七档示例文案的最终措辞要用户 review。
- Risks: 若把回落放在 `Topic` 构造器，整屏主题列表会被一行坏值拖垮；若复用整行写回，切换人设会改变首页排序；若行为样例写成逐字断言，模型正常波动会被误判成回归；若规格把 wireId 写成可复用，旧回答的人设身份会漂移。
- Deferred: 生产实现、V5→V6 迁移、行为评测运行器、介绍页文案的最终视觉、用户自建人设。
- Non-goals: 不实现人设、不建实现任务、不改 intent、不改 `AGENTS.md`、不改 token、不写逐字回答断言、不解决模型/思考模式的全局作用域缺口。
- Skipped non-critical questions and reason: 没有追问回落 toast 的具体文案与展示时长；它不改变目标、架构、验收或风险，沿用既有 toast 模式即可。没有追问七档 wireId 的字面值；它属于实现片的稳定命名，规格只要求不可复用。

## Goal

把 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 收口到「两条疑虑已拍板、未知值回落与窄写路径有明确边界、七档行为样例矩阵可执行」的状态，同时保持规格 `draft` 等人 review。

## Scope

### In scope
- 把「疑虑 1（未知档位）」改写为已拍板：读到未知 wire 值时在读取边界回落默认档，携带一次性提示信号；一行坏值不得让整个主题列表读失败；写入侧不接受未知值。
- 把「疑虑 2（切换人设算不算主题活动）」改写为已拍板：新增只写 `persona` 的窄写路径，不刷新 `updated_at`、不改首页排序、不复用整行写回。
- 在 `Technical spec` 下补「行为样例矩阵」子节：7 档 × 6 轴；受控问题集至少覆盖概念解释、错误前提、挫败卡壳；写明只做行为方向断言、不做逐字断言。
- 更新规格的「已拍板」记录，写入用户 2026-10-21 的拍板日期与决策。
- 跑 `check_spec_author_agent.py` 与 `check_pointer_liveness.py`，把读数写进本卡 Verification report。

### Out of scope
- 任何 `android/**` 代码、迁移、资源或测试改动。
- 修改 intent、`AGENTS.md`、`.codex/rules/**`、token 或原型。
- 创建人设实现切片任务。
- 为七档写最终回答样例正文（行为矩阵只写方向与判据，具体示例留介绍页文案片）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 本片只改规格，不改变运行时行为。需要保留的旧行为在实现片验收：旧回答不改写、system 消息恰好一条且位于第一位、派生用途不携带人设段、日志不记录提示词正文。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 规格引用的生产入口是 `TopicDao`（主题读取/写回）、`DeepSeekPromptRegistry`（附加段出口）、`GenerationStabilizer`（身份拷贝）、`GenerationCoordinator`（身份一致性拒绝）。本片不修改它们。
- Native bridge or independent-client compatibility/update requirements: 不适用——本片不产出跨端契约。
- Behavior-neutral non-applicability reason, if applicable: 纯设计文档收口，无运行时行为。

## Documentation impact record

- Changed runtime facts: 不改变运行时事实；把两条待拍板口径固定为设计事实，并明确归档口径不涉及人设。
- Authoritative code/config/migration evidence: `android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:42`（整行写回）、`android/data/src/main/java/com/treelearn/data/db/dao/TopicDao.java:70`（`findById`）、`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekPromptRegistry.java:50`（附加段出口）、`android/data/src/main/java/com/treelearn/data/db/dao/GenerationStabilizer.java:126`（身份拷贝）。
- Documentation surfaces checked: 人设 intent、人设规格、`.codex/rules/ux-design.md`、`.codex/context/source-of-truth.md`、`Docs/04-ux-design/design-system/tokens.json`、行为样例相关的提示词设计 §8.2（路径在规格内引用）。
- Documentation files updated: 仅 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`；父卡与子卡的 Verification report 同步回写。
- Producer/consumer/sibling/compatibility paths checked: 规格内的现有代码引用逐条保持有效；新增的窄写路径与回落信号只描述语义，不声称已实现。
- Superseded phrases/examples reverse-searched: 「未知档位」「抛」「整行写回」「刷新 updated_at」「主题活动」——改完后在规格里反查，确保旧的未决措辞不再出现。
- `documentation impact: none` reason and evidence, 若适用: 不适用——本片就是规格变更。
- Historical/PRD handling: intent 的 2026-09-21 决策不改写；规格新增 2026-10-21 拍板记录，保留两条疑虑的历史文本作为已解决记录，不删除冲突证据。

## Acceptance criteria

- [ ] `python -X utf8 .codex/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 退出码 0，且输出含四节齐全、无占位符、无裸 hex/临场 px 的判据行。
- [ ] 规格里未决疑虑为 0；两条原疑虑都以「已拍板（2026-10-21，用户本人）」的形式保留冲突对象、走法与后果，没有被静默删除。
- [ ] 规格明确：未知值只在读取边界回落默认档并携带一次性提示信号；`Topic` 构造器不因未知人设抛错；一行坏值不影响主题列表其他行。
- [ ] 规格明确：切换人设新增只写 `persona` 的窄写路径，不刷新 `updated_at`、不改变首页排序、不复用 `TopicDao.update()` 整行写回。
- [ ] 规格含七档 × 六轴行为样例矩阵与至少三组受控问题；矩阵只写行为方向，不含逐字回答断言。
- [ ] `python -X utf8 .codex/scripts/check_pointer_liveness.py` 退出码 0；规格引用的 `路径:行` 未因本片加行而失效。
- [ ] `python -X utf8 .codex/scripts/validate_tasks.py` 全目录 errors=0；本卡状态与依赖可解析。
- [ ] `git diff --name-only` 不出现 `android/**`、`frontend/**`、`backend/**`。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（docs 片，无被测实现；可执行判据是规格守门与行号复算）
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
- Exception type: documentation-only change（无生产代码，RED/GREEN 不适用）
- Reason: 本片只改规格正文，可执行判据是 `check_spec_author_agent.py` 与 `check_pointer_liveness.py`，没有单元或契约测试对象。
- User approval: 用户 2026-10-21 选择 Harness 并确认四项产品决策；规格 `draft` 状态下只做设计收口，不执行生产实现。
- Alternative verification: 规格守门 + 行号复算 + 用户 review；实现片各自补 RED/GREEN。

## Implementation notes

- 未知值回落的语义：`TopicDao.findById` 读到 `persona` 后先尝试 `Persona.fromWireId`；失败时使用默认档，并在返回结果里携带 `fallback=true` 的信号。`Topic` 的新字段仍只接受合法枚举，不把坏字符串塞进领域对象。规格不规定信号是布尔、枚举还是结果对象，只要求它可被聊天页消费一次且不阻断主题列表。
- 窄写路径的语义：`TopicDao.updatePersona(database, topicId, persona, ...)` 只更新 `persona` 一列；不写 `updated_at`、不写 `last_visited_branch_id`；更新影响行数必须恰好为 1。规格不规定方法名，只规定「只写一列」的行为。
- 行为样例矩阵的六轴来自 intent 的「Open questions」：起点假设、信息密度、会不会反问你、会不会催你继续、表达情绪、会不会主动延伸。七档每档一行；三组受控问题固定为「解释一个陌生概念」「用户带着错误前提提问」「用户说学不动了」。
- 规格中保留两条疑虑的旧文本作为「已解决」记录，不删除；只把标题从「未决」改为「已拍板」，并补上决策与后果。这样后续读者能看到为什么选 B 而不是 A。
- 不把「图省事」写成理由：回落与窄写都是用户拍板的产品边界，规格要写清代价（多一个回落信号、多一条写路径），不写成技术偏好。

## Verification report

- Verdict: PASS
- Evidence: `python -X utf8 .codex/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 退出码 0（四类小节齐全、intent 可达、无占位符、无裸 hex 与临场 px）；`python -X utf8 .codex/scripts/check_pointer_liveness.py` 退出码 0。规格改动：`status` draft→accepted；未决疑虑 2→0，两条原疑虑按 2026-10-23 用户授权保留冲突对象、走法与代价；新增七个稳定 wireId、附加段边界与七档 × 六轴行为矩阵（Q1 概念解释、Q2 错误前提、Q3 挫败卡壳）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（无代码断言被删）；两条原疑虑的旧文本作为已拍板记录保留，未静默删除冲突证据。
- Gaps: 行为样例的区分度仍需真机与用户体验确认；回落信号的具体 Java 形状在片 064 落地并由测试固化。
- Risks: 规格把回落放在读取边界、窄写单独成路，已避开「整屏读取被一行坏值拖垮」与「点人设把主题挪到顶部」两个已知坑。

## Handoff notes

- Completed: 规格收口完成；两条疑虑已拍板，行为矩阵与稳定 wireId 已写入；两条守门退出码 0。
- In progress: 无。
- Blocker: 无。
- Next steps: 片 064 按本规格实现人设核心（已完成），片 071 回写规格中的「现状」行号。
- Watch-outs: 不要为了让守门变绿而删掉两条疑虑的冲突证据；实现时附加段只追加在 `LEARNING_ANSWER`。
