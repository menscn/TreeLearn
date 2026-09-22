# Treelearn Harness Task: AI-native SDLC 角色编队（Plan 阶段首批）

id: T-2026-09-21-026
title: AI-native SDLC 角色编队（Plan 阶段首批）
template_version: 2
task_type: requirement
status: verified
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

- Problem/background:
  - 用户是**单人开发者**，独立承担产品定义、优化设计、开发、测试与发布全流程，没有团队成员分担评审、QA、发布等环节。
  - 依据来源：Anthropic《The AI-Native SDLC playbook》（2026-08-21，Louis Claxton）主张「代码不再是瓶颈，瓶颈移到 plan / review-test / deploy 这些人类速度环节」，解法是把线性流程改成「每个阶段提交一个 artifact、下一阶段读它」的循环，人类守在闸门上而不是盯着每一行。
  - 现状：本仓库已有高度同构的 Harness（9 agents / 9 skills / 16 rules / Task v2 / 失败案例库 42 条），但存在 7 处缺口：无 Plan 入口、无通用代码评审遍、无 eval 套件、守门非自动触发、无 Maintain 回路、无 leading/lagging 指标、事实源未声明权威。
- Users/scenarios:
  - 用户本人。典型场景：脑中有一个产品想法或优化点，希望**直接调用一个角色**完成需求收敛，产出可直接进入拆分的 artifact，而不是自己从零写 PRD。
- Expected outcome:
  - 每个 SDLC 阶段配一个可调用的**角色**（agent），角色背后有可复用的**专业技能**（skill）与分层的**项目背景知识**（context），三者分离、各自演进。
  - 首批（方案 A）把 **Stage 1 Plan** 做透：从「模糊想法」到「已收敛、可进入拆分」的闭环，产物可直接被 requirement-decomposer 接续。
- Core flow and reversibility:
  - 用户提出想法 → intent-capturer 加载 L0 背景 + pm-discovery 技能 → 逐轮追问收敛 → 产出 `Docs/intent/INTENT-*.md` → 用户确认「有价值 / 值得做 / 可以做」→ 移交 requirement-decomposer 拆 Task。
  - 可逆性：全部产物是新增 Markdown 文件，删除即回滚，不触碰业务代码与数据库。
- Inputs/outputs:
  - 输入：用户自然语言需求、`.trae/context/**` 背景包、`Docs/**` 权威正文、开源 skill 二次优化基线。
  - 输出：`.trae/agents/intent-capturer.md`、`.trae/skills/pm-discovery/SKILL.md`、`.trae/context/project-brief.md`、`.trae/context/domain-glossary.md`、`.trae/templates/INTENT.md`、`Docs/intent/README.md`、`.trae/ROADMAP.md`，以及 `AGENTS.md` / `.trae/README.md` 的登记。
- State and exception handling:
  - 想法被判定为「不值得做」时，intent-capturer 必须写出否决策略与理由并存档，而不是硬凑成需求。
  - 背景知识缺失或过时时，角色必须报告缺口，不得凭印象编造产品事实。
- Constraints and trade-offs:
  - **`AGENTS.md` 是最高级规则性文件**：任何新增角色/技能/文档都必须能从 `AGENTS.md` 路由表到达（用户明确要求）。
  - 背景知识分层加载，L0 常驻入口必须 ≤ 1 页（PDF：过长会把 context 占满，过期内容只有负收益）。
  - 专业技能以**开源二次优化**为准（用户明确要求），必须记录来源与改动，不新造未经验证的方法论。
  - 本批不引入新依赖、不做自动提交、不改业务代码。
- AI recommendation and rationale:
  - 推荐方案 A（只把 Plan 阶段做透）：PDF 明确「从没有入边的 play 开始」，Plan 是流水线唯一入口，且所有后续阶段都读它的产物；先把入口打磨好，后续每阶段都受益。
  - 推荐「三件套」模型（角色 + 专业技能 + 分层背景），理由：PDF 把 skill 定义为制度知识、agent 定义为角色，两者混写会导致同一方法论无法被多个角色复用。
- User choice and custom additions:
  - 用户选择 **方案 A**。
  - 用户补充要求一：专业技能「可以去网上开源的找，然后二次优化」。
  - 用户补充要求二：**遗留事项必须写成文档**，让其它 AI 能直接读取并执行（对抗单人开发者的遗忘）。
  - 用户补充要求三：**以 `AGENTS.md` 为最高级的规则性文件**。
- AI-autonomous decisions:
  - 新增文件的具体命名与目录（`.trae/context/`、`.trae/templates/`、`Docs/intent/`）。
  - 任务拆分粒度与依赖顺序（027 → 029 → 030 → 028 → 031）。
  - 每个角色的权限边界表述沿用现有 9 个 agent 的写法。
- User-confirmation decisions:
  - 首批范围取方案 A（2026-09-21 确认）。
  - `Docs/intent` 与 `Docs/01-demand`、`Docs/03-prd-draft` 的权威分工：intent 管「变更意图与收敛过程」，Docs 管「当前产品事实」，交付后按 `documentation.md` 回写。
  - 新增角色/skill 的验收方式：结构契约机械校验 + 1 个历史需求回放。
  - 5 个文档类任务的 TDD 例外（2026-09-21 用户以「执行」明确批准，执行前批准）。
- Acceptance evidence:
  - `python .trae/scripts/validate_tasks.py` 报 error=0。
  - 每个子任务的验收标准逐条机械核对（见各子任务）。
  - 历史需求回放：用 `T-2026-09-21-first-topic-atomic-creation-025` 的真实需求跑一遍 intent-capturer，产出可直接进入拆分。
- Facts:
  - 仓库当前只有 `android/` 模块存在，`frontend/`、`backend/`、`python/` 均不存在。
  - 已核实开源来源：`obra/superpowers`（含 brainstorming / writing-plans / requesting-code-review / verification-before-completion 等）、`anthropics/skills`（doc-coauthoring / brand-guidelines / skill-creator 等）、`contains-studio/agents`（product / project-management / testing 等角色）、`openai/skills` curated（define-goal / notion-spec-to-implementation / security-threat-model 等）。
  - 仓库内已有 `.superpowers/`（brainstorm 产物），本机已装 `brainstorming` skill（与 superpowers 同源）。
  - 2026-09-21 20:25–20:28 期间，本任务与 5 个子任务文件被仓库外部的并发写者删除一次（校验器文件数由 31 退回 25），已重建。
- Assumptions:
  - 后续阶段（Design/Build/Test/Deploy/Maintain）的落地仍需各自澄清，本任务只交付 Plan 阶段与全局遗留清单。
- Unknowns:
  - Stage 6 Maintain 的监测数据源（崩溃上报 / Play Console / 本地日志）尚未确定，因此列入 Phase C 而非本批。
- Risks:
  - 背景包写成第二份 PRD 会引入重复事实源 → 用「L0 只做索引与常驻事实、正文一律指向 Docs」规避。
  - 角色提示词写得像方法论散文会漂移 → 用 pm-discovery 承载方法论，角色只写触发/权限/输出契约。
  - 存在并发写者导致未提交文件丢失的风险 → 交付后建议尽快提交。
- Deferred:
  - Phase B：code-reviewer + REVIEW.md、eval-keeper、codebase-researcher、code-simplifier、metrics-reporter、守门自动化。
  - Phase C：loop-watcher（Maintain 回路）、spec-author（Design 阶段）。
  - 全部记录在 `.trae/ROADMAP.md`。
- Non-goals:
  - 不实现 Stage 2–6 的角色（本批只出遗留清单）。
  - 不改动 `android/**`、不引入新依赖、不创建 `frontend/` 或 `backend/`。
  - 不重构现有 9 个 agent 与 16 个 rules。
- Skipped non-critical questions and reason:
  - 未追问用户偏好用哪个开源仓库，因为用户授权「去开源的找，然后二次优化」，选型属 AI 自主决策且已在 Facts 中留痕。

## Goal

把 AI-native SDLC 的 Plan 阶段落地为一套可直接调用的「角色 + 专业技能 + 分层背景知识」，并把 Stage 2–6 的遗留事项写成其它 AI 可直接执行的路线文档；全部入口登记到最高级规则文件 `AGENTS.md`。

## Scope

### In scope

- 子任务 027–031（背景包、pm-discovery 技能、intent-capturer 角色、intent 模板与存放位置、遗留路线文档与路由登记）。
- `AGENTS.md` 与 `.trae/README.md` 的最小登记改动。

### Out of scope

- Stage 2–6 角色的实现。
- 任何 `android/**` 业务代码改动。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 仅新增 Markdown 配置与文档，现有 9 agents / 16 rules / Task 契约行为完全保持不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 新增角色与既有 `requirement-decomposer` 的交接点为唯一新增接口面，证据为 `.trae/agents/requirement-decomposer.md` 的触发条件段。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不触碰 `android/**`、不新增依赖、不改构建入口，无可运行分支受影响。

## Documentation impact record

Follow [code-doc alignment](../rules/documentation.md).
- Changed runtime facts: 无（纯文档/配置新增）。
- Authoritative code/config/migration evidence: `.trae/agents/*.md`、`.trae/skills/*/SKILL.md`、`.trae/rules/*.md` 为现状权威。
- Documentation surfaces checked: `AGENTS.md`、`.trae/README.md`、`.trae/HARNESS.md`、`Docs/**`。
- Documentation files updated: `AGENTS.md`（路由表 + 说明）、`.trae/README.md`（目录结构）。
- Producer/consumer/sibling/compatibility paths checked: 新增角色与既有 `requirement-decomposer` 的交接点（intent → Task 拆分）。
- Superseded phrases/examples reverse-searched: 待子任务 031 执行时核对。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: `Docs/01-demand`、`Docs/03-prd-draft` 保持当前态正文地位，本批不改写其内容。

## Acceptance criteria

- [ ] 027–031 五个子任务全部 `verified`。
- [ ] `python .trae/scripts/validate_tasks.py` 输出 error=0，且本父任务与全部子任务在列。
- [ ] `AGENTS.md` 路由表新增可到达 `.trae/ROADMAP.md`、`.trae/agents/intent-capturer.md`、`.trae/skills/pm-discovery/SKILL.md`、`.trae/context/project-brief.md` 的触发行。
- [ ] 历史需求回放：以 `T-2026-09-21-first-topic-atomic-creation-025` 的真实需求为输入，intent-capturer 产出可被 requirement-decomposer 直接接续的 `INTENT-*.md`。证据：`artifacts/evidence/T-026-replay-intent.txt`——回放实例 80 行、sha256=9A4694F591B9DEB0F2B909BA3AB6EEE28D8389A75FE6D9B96F0AA3D7C908CC51（该文件内嵌可无损重建的正文，重建后哈希一致），13 条断言全 OK、EXITCODE=0。

## TDD execution record

### RED — failing test first

- Test/scenario: 不适用（纯文档与配置任务，无生产代码行为）。
- Command: 不适用
- Exit code: 0
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation

- Implementation: 见子任务 027–031。
- Command: python .trae/scripts/validate_tasks.py
- Exit code: 0
- Passing evidence: 见子任务验收报告；历史需求回放的独立读数见 `artifacts/evidence/T-026-replay-intent.txt`（13 条 OK、EXITCODE=0），守门双向自检见 `artifacts/evidence/T-028-031-checkers-selftest.txt`（cases=44 ok=44、EXITCODE=0）。
- 追记（2026-09-21）：以上 `cases=NN` 是写下当时的读数，此后自检用例随守门新增而增加（同日新增 `roadmap:phase-b-spec-author-lost` 与 17 条 `spec:` 用例，基线由 3 条增至 4 条）。`artifacts/evidence/T-028-031-checkers-selftest.txt` 是活文档，每次守门变更都会重出；**当前读数以该文件自己的 `# Mode:` 行与结尾的 `SELFTEST cases=` 行为准，不要引用旧正文里的数字。**文件头记录了本次运行的被测脚本与 fixture 的 SHA-256，运行后复检为 `Post-run recheck of subject hashes: STABLE`。
- Regression evidence: 既有 9 agents / 16 rules 未被改动。

### REFACTOR — optional

- Cleanup: 无。
- Regression evidence: 无。

### TDD or sizing exception — only with prior user approval

- Exception type: TDD 不适用（文档/配置类任务，无生产代码）。
- Reason: 交付物为 Markdown 角色与技能定义，不存在可先写的失败测试；等价验证为「结构契约机械校验 + 历史需求回放」。
- User approval: 用户于 2026-09-21 批准本批 5 个文档类任务的 TDD 例外（执行前批准，非事后追认）。
- Alternative verification: `validate_tasks.py` + 结构校验脚本 + 历史需求回放。

## Implementation notes

- 交付顺序：027 → 029 → 030 → 028 → 031。
- 027/029/030 相互独立，可并行；028 依赖三者；031 依赖全部。
- 所有新增文件为 UTF-8 无 BOM、LF 行尾，与仓库既有 Markdown 一致。

## Verification report

- Verdict: PASS
- Evidence: 五条验收标准逐条核对——① 027/029/030/028/031 五个子任务已全部迁移为 `verified`（本轮完成）；② `python .trae/scripts/validate_tasks.py` → `SUMMARY files=31 errors=0 warnings=5`；③ `AGENTS.md` 新增 4 条路由行（`.trae/agents/intent-capturer.md`、`.trae/skills/pm-discovery/SKILL.md`、`.trae/context/project-brief.md`、`.trae/ROADMAP.md`）且删除行为 0（判据行 `measured_deletions=0`）；④ 历史需求回放见 `artifacts/evidence/T-026-replay-intent.txt`（回放实例 80 行、sha256=9A4694F591B9DEB0F2B909BA3AB6EEE28D8389A75FE6D9B96F0AA3D7C908CC51，13 条断言 OK、`EXITCODE=0`，内嵌正文可无损重建且重建后哈希一致）；⑤ 五个结构守门 + 双向自检读数：10 / 22 / 19 / 31 / 28 条 OK、各自 0 FAIL、自检 `cases=44 ok=44`、3 条基线、`OVERALL: PASS`。独立复核（verifier 第二轮 + 补充复核，全程只读）在五个子任务上判 PASS，对父任务判 PARTIAL，唯一未满足项就是当时的 AC1（子任务仍为 `verifying`）；本轮迁移后 AC1 成立。
- 追记（2026-09-21）：以上 `cases=NN` 是写下当时的读数，此后自检用例随守门新增而增加（同日新增 `roadmap:phase-b-spec-author-lost` 与 17 条 `spec:` 用例，基线由 3 条增至 4 条）。`artifacts/evidence/T-028-031-checkers-selftest.txt` 是活文档，每次守门变更都会重出；**当前读数以该文件自己的 `# Mode:` 行与结尾的 `SELFTEST cases=` 行为准，不要引用旧正文里的数字。**文件头记录了本次运行的被测脚本与 fixture 的 SHA-256，运行后复检为 `Post-run recheck of subject hashes: STABLE`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言被删除。子任务内两处判据被**加强**而非放宽：028 的 40 字重叠档由 ≤2 收紧为 = 0；030 新增「注记 == 模板自身元数据键 ∪ ## 标题」的独立判据。两次加强都各配了一条注入违规必须判 FAIL 的自检用例。
- Gaps: 首批只交付 Stage 1（Plan）；Stage 2–6 的遗留事项写在 `.trae/ROADMAP.md`（Phase B 6 条、Phase C 4 条，每条含目标、可写路径、依赖、验收方式、已核实的开源来源与可直接粘贴给新会话的执行提示词）。verifier 指出的 C-2 / C-4 两处判据质量问题：第四轮复核确认「报告型」已关闭（C-2 的同行否定绕过、C-4 的独立断言与标签），但指出**同类仍未闭合**（C-2 是名单制启发式、C-4 的协同缩表因判据以模板正文自我为锚而放行）；第五轮据此补了「扫描全部相关行 + 线索词正则」与「读规格的下界锚」两组结构性加固，复核结论未回。
- Risks: 并发会话正在改 `.trae/tasks/**` 与 `Docs/**`，本任务全部读数都带时间点；`Docs/**` 索引已两次因外部新增文件而需要同步（10 → 11 → 13），并发期间守门可能再次转红，属预期行为而非回归。

## Handoff notes

- Completed: 需求澄清与方案 A 确认；开源来源调研。
- In progress: 子任务 027–031 文件创建与执行。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 按 027 → 029 → 030 → 028 → 031 顺序执行并逐个验收。
- Watch-outs: 背景包必须保持「薄」，一旦超过 1 页且开始复制 Docs 正文，就变成第二份事实源。
