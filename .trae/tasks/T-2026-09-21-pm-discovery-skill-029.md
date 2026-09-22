# Treelearn Harness Task: pm-discovery 专业技能（开源二次优化）

id: T-2026-09-21-029
title: pm-discovery 专业技能（开源二次优化）
template_version: 2
task_type: config
status: verified
parent_requirement: T-2026-09-21-ai-native-sdlc-agent-team-026.md
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

Parent summary: 用户是单人开发者，要把 AI-native SDLC 各阶段做成可调用角色；首批（方案 A）只做 Stage 1 Plan。专业技能要求「去网上开源的找，然后二次优化」。`AGENTS.md` 为最高级规则文件。

本任务相关细节：
- Problem/background: 用户要的核心价值不是「有个 agent」，而是「需求能被收敛到一个有价值、值得做、可做的状态」。这属于产品经理的专业判断，必须作为可复用技能单独承载，而不是散落在角色提示词里。
- Users/scenarios: intent-capturer 主用；后续 spec-author、requirement-decomposer 可复用同一判据。
- Expected outcome: `.trae/skills/pm-discovery/SKILL.md`，含可判定的收敛判据（不是口号）、提问清单、明确的「建议不做」标准、反模式清单。
- Constraints and trade-offs: 必须基于开源二次优化并留痕来源；不得整段照搬。
- AI-autonomous decisions: 入选来源、结构、字段名。
- Acceptance evidence: 结构断言 + 来源留痕断言 + 照搬抽查。

## Goal

新增 `.trae/skills/pm-discovery/SKILL.md`：把「问题优先于方案」的发现流程、收敛三问（有价值 / 值得做 / 可以做）与「建议不做」标准，落成可被角色加载的判定型技能。

## Scope

### In scope
- 新建 `.trae/skills/pm-discovery/SKILL.md`。
- 新增校验脚本 `.trae/scripts/check_pm_discovery_skill.py`。

### Out of scope
- 不改造现有 9 个 skill；不修改 `brainstorming` 等已安装的全局 skill。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只新增一个技能文件与一个只读校验脚本，现有 skill 行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费者为 028（intent-capturer 执行前必读）；证据为 028 任务文件引用。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯文档新增，无可运行分支。

## Documentation impact record

Follow [code-doc alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: `.trae/rules/harness-clarification.md`（澄清清单是既有权威，本技能必须覆盖其字段，不得另立一套）。
- Documentation surfaces checked: `.trae/rules/harness-clarification.md`、`.trae/rules/harness-tasks.md`、`.trae/agents/requirement-decomposer.md`、开源来源文件。
- Documentation files updated: 无。
- Producer/consumer/sibling/compatibility paths checked: 技能中的判据与 `harness-clarification.md` 的字段清单是否冲突（要求：不冲突，本技能只补充判据，不替换清单）。
- Superseded phrases/examples reverse-searched: 不适用。
- `documentation impact: none` reason and evidence, if applicable: 不改变运行时事实与既有规范。
- Historical/PRD handling: 保持原样。

## Acceptance criteria

- [ ] `.trae/skills/pm-discovery/SKILL.md` 存在，front matter 含 `name: pm-discovery` 与 `description`，且 description 写明触发时机。
- [ ] 含三个收敛判据小节，标题逐字为：`有价值吗`、`值得做吗`、`可以做吗`；每节下含 ≥3 条可判定判据（脚本检查每节 `- ` 条目数 ≥3）。
- [ ] 含「建议不做」小节，含 ≥3 条显式否决标准。
- [ ] 含「提问清单」小节，覆盖 `.trae/rules/harness-clarification.md` 列出的全部字段；脚本比对字段名集合，缺失即 FAIL。
- [ ] 含「反模式」小节，≥4 条。
- [ ] 含「溯源与改动」小节，逐条列出二手来源（仓库 + 文件路径 + 访问日期），并写明本次做了哪些增删改。
- [ ] **照搬检查**：与来源原文的 60 字连续子串重合命中数 ≤ 2；脚本输出命中数与位置。
- [ ] 含项目适配段，出现 ≥4 个 Treelearn 具体约束（本地优先、Java-only、单任务 ≤200 行垂直切片、单人开发、平板横屏等）。
- [ ] 全文不含 `PENDING`、`TBD`、`TODO`、`待填`。

## TDD execution record

### RED — failing test first

- Test/scenario: 不适用（纯文档新增）。
- Command: 不适用
- Exit code: 0
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation

- Implementation: 新增 `.trae/skills/pm-discovery/SKILL.md` 与校验脚本。
- Command: python .trae/scripts/check_pm_discovery_skill.py
- Exit code: 0
- Passing evidence: 脚本打印各小节存在性、条目计数、字段集合差集、照搬命中数。
- Regression evidence: `git status --porcelain` 仅显示新增文件。

### REFACTOR — optional

- Cleanup: 无。
- Regression evidence: 无。

### TDD or sizing exception — only with prior user approval

- Exception type: TDD 不适用（文档/配置类任务，无生产代码）。
- Reason: 交付物为技能定义，不存在可先写的失败测试；等价验证为结构断言脚本。
- User approval: 用户于 2026-09-21 明确批准本批 5 个文档类任务的 TDD 例外（执行前批准，非事后追认）。
- Alternative verification: 上述九条验收标准。

## Implementation notes

- 一手对象是「已核实的具体文件」：`obra/superpowers` 的 `skills/brainstorming/SKILL.md` 与 `skills/writing-plans/SKILL.md`、`contains-studio/agents` 的 `product/*`、`anthropics/skills` 的 `doc-coauthoring`。
- 本机已安装的 `brainstorming` skill 与本批来源同源，可作对照，但不得直接复制安装目录内容。
- 校验脚本纯标准库。

## Verification report

- Verdict: PASS
- Evidence: `python .trae/scripts/check_pm_discovery_skill.py` → 22 条 OK / 0 FAIL / `EXITCODE=0`；重叠判据行为 `shares at most 2 runs of 60 chars with its sources`。独立复核（verifier）判 PASS：9 条验收标准全绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除或放宽；60 字档额度 2、比对来源集合（`harness-clarification.md` + 已安装的 brainstorming 技能）都保持原设计，未因复核意见改动。
- Gaps: ① 60 字档「≤2」允许照抄一条 61 字的完整句子（verifier 实测：59→hits=0、60→hits=1、**61→hits=2 判 PASS**、62→hits=3 判 FAIL）；这是既定口径而非缺陷，但「抄整句在额度内合法」需要评审知情；② 比对来源之一是**本机绝对路径** `C:/Users/23745/.agents/skills/brainstorming/SKILL.md`，文件不存在时脚本打印 `SKIPPED`（换机即静默失去这条覆盖）；③ 断言并不比对验收标准里列的 4 个上游仓库——真实交付与上游为 0 命中，但断言位置与 AC 文字有偏差。
- Risks: 若日后把上游仓库原文直接粘进技能正文，现有断言不会拦住（它不比对上游）。

## Handoff notes

- Completed: 无。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 供 028 引用；Phase B 的 spec-author 可复用同一判据。
- Watch-outs: 判据必须是可判定的，否则角色会退化成「顺着用户说」。
