# Treelearn Harness Task: RikkaHub 功能能力矩阵与吸收决策

id: T-2026-09-23-072
title: RikkaHub 功能能力矩阵与吸收决策
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

- Parent summary: RikkaHub 功能与对话页视觉分层吸收；本任务负责功能事实盘点和取舍，不负责 UI 实现。
- Problem/background: 067–070 主要记录交互/机制，未系统回答 RikkaHub 的功能哪些值得进入知芽、哪些与本地优先/Java-only 冲突。
- Users/scenarios: 用户 review 功能范围；后续 Android 实现者按矩阵中的 P0/P1 条目执行。
- Expected outcome: `Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md`，每项有事实出处、知芽现状、采用方式、优先级、依赖和明确理由。
- Core flow and reversibility: 读取 RikkaHub README/源码与知芽生产代码 → 建立能力清单 → 标记 adopt/adapt/defer/reject → 用户 review 后才进入实现。
- Inputs/outputs: 输入 RikkaHub 只读副本、知芽 Android 代码与 PRD；输出一份功能矩阵和后续任务建议。
- State and exception handling: 对每项至少说明离线/网络失败、无 Key、权限拒绝、取消、部分成功、重复操作和数据恢复语义；无法核对的标为 UNKNOWN。
- Constraints and trade-offs: 不引入 Kotlin/Compose/Room/DataStore/新未验证依赖；不改变 DeepSeek-only、本地 SQLite、树状分支和密钥边界；AGPL 只记录合规风险，不作法律结论。
- AI recommendation and rationale: 用四档决策（吸收、改造、延后、拒绝）加 P0/P1/P2 优先级；先吸收能直接增强学习主流程的消息/输入/可靠性能力。
- User choice and custom additions: 用户要求“功能也有吸收”，本任务因此把消息渲染、输入能力、生成可靠性、会话管理、助手/模型、附件/语音、主题/无障碍等全部纳入盘点。
- AI-autonomous decisions: 功能矩阵只做事实与建议，不直接改 PRD、schema 或生产代码；多 Provider、MCP、workspace、云同步默认为需明确理由的候选，不自动采纳。
- User-confirmation decisions: P0/P1 取舍、是否纳入附件/语音/收藏/翻译、是否接受额外依赖由用户 review 拍板。
- Acceptance evidence: 文档存在、UTF-8、无占位符；至少覆盖 8 个功能类别；每项有 RikkaHub 路径和知芽代码/文档证据；通过 `validate_tasks.py`。
- Facts: RikkaHub 具备 Markdown/代码/表格/LaTeX/附件、消息操作、流式/重试、队列、模型/助手、搜索/历史/收藏/备份、语音、主题和大屏布局等能力；知芽已有 Java 原生聊天、MarkdownRenderer、分支来源卡、思考披露和本地数据链路。
- Assumptions: 功能矩阵以当前本地副本为准，不联网追踪上游未来变化。
- Unknowns: 部分功能的真机体验、性能和依赖许可证需要后续实机/构建核对。
- Risks: 仅看 README 会遗漏失败态和权限边界；把 RikkaHub 功能名称直接复制到知芽会造成产品范围膨胀。
- Deferred: 具体实现拆分和新依赖评估由矩阵 review 后另立任务。
- Non-goals: 不写 Java/Kotlin 代码；不实现附件、语音、多 Provider、MCP、workspace、云同步。
- Skipped non-critical questions and reason: 本任务先输出完整候选集合，优先级由矩阵中的用户确认区集中处理。

## Goal

产出一份可审计的 RikkaHub 功能能力矩阵，为知芽后续功能实现提供唯一取舍入口。

## Scope

### In scope
- 盘点消息内容、消息操作、生成可靠性、输入/草稿、模型/助手、会话管理、附件/语音、主题/无障碍/大屏八类能力。
- 对每项记录 RikkaHub 事实、知芽当前状态、adopt/adapt/defer/reject、P0/P1/P2、实现依赖、失败/取消/权限语义和验证方式。
- 给出第一批实现任务的候选清单，并明确不自动进入实现的条目。

### Out of scope
- 修改 PRD、UX 原型、schema、生产代码或依赖。
- 以 RikkaHub 的线性消息节点替换知芽树状 Branch/Message 模型。
- 法律意见、许可证最终裁决或上游贡献。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增分析文档，无运行行为变化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 只读核对 `android/app/src/main/java/com/treelearn/app/screen/`、`ui/`、`:ai` 与 `:data` 的当前消费者。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不修改生产路径。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: RikkaHub README/源码只读副本、知芽 `.trae/context/project-brief.md`、`Docs/01-demand/`、`Docs/03-prd-draft/` 和 Android 当前代码。
- Documentation surfaces checked: `Docs/05-technical-design/`、`Docs/04-ux-cross-review/`、现有 067–070。
- Documentation files updated: `Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md`。
- Producer/consumer/sibling/compatibility paths checked: 按八类能力在文档中列出实际生产者/消费者；未找到的能力标 UNKNOWN，不得写成已存在。
- Superseded phrases/examples reverse-searched: 「完整吸收」「全量吸收」「作为基座」「仅交互吸收」等表述，统一改为分层取舍。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不覆盖 067–070 历史分析；矩阵 review 后如改变当前态范围，另立 PRD/UX 影响任务。

## Acceptance criteria

- [ ] 文档存在且 UTF-8、无占位符，覆盖八个功能类别。
- [ ] 每个候选能力同时有上游路径、知芽当前证据、取舍、优先级、依赖和异常语义。
- [ ] 至少列出 5 项明确吸收、5 项改造或延后、3 项拒绝，并说明理由；数量从文档表格回读，不凭记忆填写。
- [ ] 功能矩阵不把 RikkaHub 的 AGPL、Kotlin/Compose、Room/DataStore 或云能力当成可直接搬运资产。
- [ ] 通过 `python .trae/scripts/validate_tasks.py`，并由用户 review 后再进入实现任务。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（纯分析文档）。
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 产出《RikkaHub 功能吸收矩阵 V1.0》
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 2026-09-23 执行 `SUMMARY files=76 errors=0`；矩阵落盘 `Docs/05-technical-design/知芽（TreeLearn）RikkaHub功能吸收矩阵_V1.0.md`，八类 40 项，数量自第 9 节回读：adopt 6 / adapt 11（另 4 项已覆盖）/ defer 14 / reject 8
- Regression evidence: 不适用（无生产代码）

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（文档任务）。
- Reason: 结果由事实出处、矩阵完整性和用户 review 验证。
- User approval: 用户已明确要求生成包含功能吸收的任务。
- Alternative verification: 事实抽查、数量回读、路径与当前代码反向核对。

## Implementation notes

- 建议矩阵列：能力、上游事实、知芽现状、决策、优先级、用户价值、技术代价、异常语义、依赖、验证。
- “吸收”必须区分功能语义和实现资产：可以采用消息队列/停止/重试的行为，不等于引入 RikkaHub 的 Kotlin 类或依赖。
- provenance 审计记录与未来实现规格分离；实现者只看已批准规格，不以矩阵中的路径主动阅读上游源码。

## Verification report

- Verdict: PASS（用户 2026-09-24 00:39 指令「执行」＝对第 11 节四项拍板全部采纳推荐：① adopt 清单照单进 074–076；② 备份/恢复不提前；③ 自动重试不随第一批进 `:ai` 网关；④ 深色模式维持延后）
- Evidence: 矩阵存在（UTF-8）；八类功能各表含上游路径、知芽证据（2026-09-23 逐项核对 `ChatActions.java` 无 onStop、`screen_chat_body.xml` 无 stop 控件、`ChatViewState.canSubmit()` 等实测事实）、四档决策、优先级、依赖与异常语义代号（[OFF]/[NOKEY]/[CANCEL]/[PART]/[DUP]/[RECOVER]/[PERM]）；第 9 节数量回读满足验收线（adopt≥5、adapt/defer≥5、reject≥3）；第 10 节给出 074–076 映射边界。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用。
- Gaps: 知芽 MarkdownRenderer 覆盖面、`:ai` 网关重试现状标 UNKNOWN 待实现前核对；真机体验与依赖许可证未核。
- Risks: 若用户照单全收 defer 池会造成范围膨胀；矩阵已把 defer/reject 写成结论。

## Handoff notes

- Completed: 任务契约已生成。
- In progress: 待撰写功能矩阵。
- Blocker: 无。
- Unblock condition: 无；完成后需用户 review。
- Next steps: 撰写矩阵并据此更新 074/076 的最终功能边界。
- Watch-outs: 不把 README 功能列表当作已验证行为；不在本任务改生产代码。
