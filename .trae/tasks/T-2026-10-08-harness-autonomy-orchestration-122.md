# TreeLearn Harness Task: Harness 自主贯通与内部恢复

id: T-2026-10-08-122
title: Harness 自主贯通与内部恢复
template_version: 2
task_type: config
status: verified
parent_requirement: T-2026-10-08-harness-autonomous-completion-121.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 用户已确认 Harness 一次委托贯通与少打断；保留显式 Harness 选择、Task v2、独立 verifier、专项 guard 和证据要求。
- Problem/background: 当前澄清/拆分可能对已确认需求再次要求用户批准；`harness-run` 在 worker 重试失败后要求用户介入，破坏端到端连续执行。
- Users/scenarios: 用户选择 Harness 后，期望 AI 自主完成已确认范围内的普通执行和恢复。
- Expected outcome: 普通澄清/任务/阶段不触发重复审批；可恢复派工失败由 AI 内部处理；仅实质决策、无法安全继续或独立验收不可用时暂停。
- Core flow and reversibility: 更新权威入口、澄清/任务规则、拆分角色和 Harness 调度说明；只改 Markdown 规则/技能，可回滚。
- Inputs/outputs: 输入为 accepted intent 与本任务范围；输出为彼此一致的 Harness 执行策略文档。
- State and exception handling: 实质未知项保留 clarifying 并问用户；安全执行不可行或无独立 verifier 时 blocked，不能自验 PASS。
- Constraints and trade-offs: 不放宽业务代码边界、TDD、专项 guard、独立验收或自动提交限制。
- AI recommendation and rationale: 把一次 opt-in 与已确认需求作为本次执行授权，允许编排者内部重试/改派并在安全范围内接手，避免把可恢复问题交回单人开发者。
- User choice and custom additions: 用户于 2026-10-08 明确选择 Harness Task 流程实施，并批准本任务 docs/config-only TDD exception。
- AI-autonomous decisions: 保留一次明确需求确认；不再对相同范围的父任务摘要和常规阶段重复索取确认。
- User-confirmation decisions: 任何实质改变目标、边界、风险、不可逆操作的决定仍由用户作出；执行范围外的提交/推送仍须单独请求。
- Acceptance evidence: 定向 Task validator 通过；跨文档搜索和独立 verifier 确认新旧规则没有矛盾且保留安全验收门禁。
- Facts (pre-change baseline): 修改前 `harness-run` 明确要求 worker 重试失败后停下并请用户介入；任务拆分规则把用户确认父 requirement 作为额外关卡；Task validator 全局基线为 16 errors、8 warnings。
- Assumptions: 用户已确认的需求或 accepted intent 可作为父 requirement 内容的确认依据。
- Unknowns: 独立 worker/verifier 不可用时，平台是否能提供等价的独立执行者；不能假设可由主会话自我验收。
- Risks: 过度自动恢复可能越出 owner lane；恢复路径必须仍加载对应栈规则并保留只读独立 verifier。
- Deferred: 一次真实 Harness 运行的中断次数与完成率观察，不在规则文档变更本身伪造运行证据。
- Non-goals: 不移除质量门禁，不自动提交/推送，不要求用户执行 AI 可自行完成的重试/拆分/常规检查。
- Skipped non-critical questions and reason: agent 具体重试序列由实施者选择；只有安全或验收能力不足时才升级为用户阻塞。

## Goal

修改 Harness 规则与执行说明，使一次 Harness 选择后 AI 能在已确认范围内自主贯通，同时保留实质决策与独立验收边界。

## Scope

### In scope
- 对齐 `AGENTS.md`、`.trae/HARNESS.md`、`.trae/README.md`、`.trae/rules/harness-clarification.md`、`.trae/rules/harness-tasks.md`、`.trae/agents/requirement-decomposer.md`、`.trae/skills/harness-run/SKILL.md`、`.trae/skills/context-handoff/SKILL.md` 的同一套授权、确认、恢复与阻塞语义。
- 将 accepted intent/已确认用户需求作为父 requirement 内容的确认依据，避免同内容二次审批。
- 定义 worker 失败的内部恢复/改派/安全接手路径及不能恢复时的真实阻塞；独立 verifier 不得由实现者替代。

### Out of scope
- 业务代码、依赖、API、SQL、构建及用户功能。
- 自动提交、推送或绕过独立 verifier/guard。
- 修改已存在的历史任务或工作区中与本任务无关的文件。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 去除已确认需求的重复阶段确认和 worker 可恢复失败时的人工接力；保留必要需求确认、显式 Harness opt-in、lane/权限边界、独立 verifier 与专项 guard。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 根入口 `AGENTS.md`；`HARNESS.md` 与 `harness-run` 调度技能；澄清规则；Task 契约与 requirement-decomposer；`.trae/README.md` 用户说明。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用；本任务改变 Harness 协作行为。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无运行时代码事实变化；改变 Harness 规范和编排说明。
- Authoritative code/config/migration evidence: `AGENTS.md`、`.trae/rules/**`、`.trae/HARNESS.md`；任务状态仍以 `.trae/tasks/**` 为权威。
- Documentation surfaces checked: In-scope 的八个入口/规则/技能文件及本父需求意图。
- Documentation files updated: `AGENTS.md`、`.trae/HARNESS.md`、`.trae/README.md`、`.trae/rules/harness-clarification.md`、`.trae/rules/harness-tasks.md`、`.trae/agents/requirement-decomposer.md`、`.trae/skills/harness-run/SKILL.md`、`.trae/skills/context-handoff/SKILL.md`，以及标记用户批准状态的父意图文件 `Docs/intent/INTENT-2026-10-08-harness-autonomous-completion.md`。
- Producer/consumer/sibling/compatibility paths checked: 单次 opt-in → 澄清 → 父/子任务 → worker → guard → verifier → 汇总报告。
- Superseded phrases/examples reverse-searched: 旧“停在调度层请用户介入/人工拆 task”、相同意图需要再次确认父 requirement、按推荐后仍要求无条件最终确认。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改历史任务和历史报告；只改现行规则与入口。

## Acceptance criteria

- [x] 用户选择 Harness 且需求已确认后，规则允许直接建立父/子任务；不要求再次确认等价的父任务摘要或常规阶段交接。
- [x] 澄清流程保留会实质改变结果的问题；可由上下文确定的非关键细节记录为 AI 自主决策并继续；采纳推荐后不做无意义的摘要复核。
- [x] `harness-run` 指示编排者内部重试/缩小任务/改派；在 lane 规则允许且安全时可接手任务，不把可恢复的普通失败交给用户手工处理。
- [x] 无安全恢复路径、缺少必要权限或独立 verifier 时，报告明确 blocker；实现者不得替代 verifier，缺证据不得 PASS。
- [x] `Task v2`、TDD 适用规则、专项 guard、独立验收、无自动提交/推送的边界仍保留。
- [x] 全库搜索确认没有与新语义冲突的当前态指令；独立 verifier 抽查上述八个文档面并报告证据与风险。
- [x] 本任务作为真实运行样本记录 Harness opt-in 至终态报告、用户确认/实质决策请求数和验证证据。
- [x] `python .trae/scripts/validate_tasks.py .trae/tasks/T-2026-10-08-harness-autonomous-completion-121.md .trae/tasks/T-2026-10-08-harness-autonomy-orchestration-122.md` 输出 `SUMMARY files=2 errors=0 warnings=0` 并退出 0。

## TDD execution record

### RED — failing test first
- Test/scenario: 不适用；本任务只修改 Harness Markdown 规则/技能，没有可执行生产行为测试。
- Command: 不适用（已获 docs/config-only TDD exception）
- Exit code: 0
- Observed failure: 不适用
- Failure reason validated: 不适用。

### GREEN — minimum implementation
- Implementation: 对齐 Harness 入口、澄清、任务契约、拆分职责和调度恢复说明。
- Command: `python .trae/scripts/validate_tasks.py .trae/tasks/T-2026-10-08-harness-autonomous-completion-121.md .trae/tasks/T-2026-10-08-harness-autonomy-orchestration-122.md`
- Exit code: 0
- Passing evidence: 定向校验输出 `SUMMARY files=2 errors=0 warnings=0` 并退出 0；文档 UTF-8/替换字符/相对链接扫描覆盖 11 个文件通过；变更文件 `git diff --check` 通过。规则语义由独立 verifier 最终复核。
- Regression evidence: 全库 validator 扫描 60 个任务，16 errors、8 warnings，退出 1；本任务两份文件均 PASS，错误/警告数与新增任务前 58 个文件的既有基线相同；未修改无关旧任务。

### REFACTOR — optional
- Cleanup: 不适用
- Regression evidence: 不适用。

### TDD or sizing exception — only with prior user approval
- Exception type: TDD exception — documentation/configuration only。
- Reason: 本任务仅修改 Markdown 规范与 agent skill，没有可运行的业务代码供 RED/GREEN 验证；改用定向 Task v2 校验、现行规则交叉搜索和独立只读 verifier。
- User approval: 用户于 2026-10-08 在看到上述理由后明确回复“确定”，批准本次单一 docs/config Task 的 TDD exception。
- Alternative verification: 定向 `.trae/scripts/validate_tasks.py`；检查变更文件 UTF-8/替换字符与 whitespace；对旧冲突短语反向搜索；独立 verifier 核查要求与保留门禁。

## Implementation notes

- 实施结果：统一明确确认来源、父任务非审批关卡、按 lane 安全恢复，以及 verifier/权限不可用时 blocked；同步八个入口、规则或技能面和 accepted intent 状态。
- 恢复边界：普通、可恢复的 worker 故障不再暂停向用户求助；当独立验收不可用时必须保持 blocked。
- 定向 validator：`SUMMARY files=2 errors=0 warnings=0`，退出 0。文档检查：11 个 Markdown 文件 UTF-8/替换字符/相对链接 PASS；`git diff --check` PASS。
- 全库 validator：60 files、16 errors、8 warnings，退出 1；与任务新增前 58 files 的 16 errors / 8 warnings 基线相同，旧错误位于四个未修改历史任务。
- 全仓库 Markdown 反向搜索命令：`rg -n -i --glob '*.md' -e '仍须澄清和确认业务需求' -e '但仍需澄清和确认具体需求' -e '请求最终确认' -e '用户确认的父.*requirement.*任务' -e '停在调度层，请用户介入' -e '仍然失败.*请用户介入' -e '建议用户调.*context-handoff' -e '明确提示用户开新会话' .`；输出 `NO_MATCHES in repository Markdown`，预期退出码 `1`。
- 初次 Markdown 扫描未加 `--hidden`，覆盖不完整，不能作为全库证明；以下命令为权威复核：`rg --hidden -n -i --glob '*.md' --glob '!**/.git/**' --glob '!**/T-2026-10-08-harness-autonomy-orchestration-122.md' -e '仍须澄清和确认业务需求' -e '但仍需澄清和确认具体需求' -e '请求最终确认' -e '用户确认的父.*requirement.*任务' -e '停在调度层，请用户介入' -e '仍然失败.*请用户介入' -e '建议用户调.*context-handoff' -e '明确提示用户开新会话' .`；包含隐藏目录、排除 `.git` 与当前证据自引用，输出 `NO_MATCHES in repository Markdown (hidden paths included; self-reference and .git excluded)`，预期退出码 `1`。
- 真实 Harness 样本（本任务）：从用户 opt-in、意图/父子任务创建、规则实现、文档验证到独立 verifier 报告；实现前观察到 4 条显式确认回复（“是的”“确定。执行”及两次“确定”），确认范围与例外后未再提出实质决策问题。首轮 verifier 为 `PARTIAL`；补充样本与全库搜索证据后，跟进复核为 `PASS`。

## Verification report

- Verdict: PASS — independent follow-up review confirmed the corrected search and run evidence; no remaining gaps.
- Evidence: 定向 Task v2 validator 输出 `SUMMARY files=2 errors=0 warnings=0`；11 个 Markdown 文件 UTF-8/替换字符/相对链接检查 PASS；`git diff --check` PASS；独立 verifier 原样重跑权威 `rg --hidden` 搜索，输出 `NO_MATCHES` 且退出码 1 符合预期，并重跑定向 validator 通过。全库 validator 输出 60 files / 16 errors / 8 warnings，与原 58-file 基线错误/警告数相同。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本次为文档/技能流程调整，无生产断言删除或业务代码变更。
- Gaps: 无。
- Risks: 全库既有 16 个历史 Task 错误与 8 个软上限警告仍在；不属于本任务且未修改。

## Handoff notes

- Completed: 任务及 TDD exception 获得用户确认；八个规则/入口/技能面及 accepted intent 状态已修改并通过局部检查；全仓库搜索和本次 Harness 样本观察已记录。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 无；任务已独立验收并完成。
- Watch-outs: 现有工作区存在大量 Android/README/task/evidence 改动；严格限制写入范围。
