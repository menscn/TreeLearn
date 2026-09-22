# Treelearn Harness Task: intent 模板与存放位置

id: T-2026-09-21-030
title: intent 模板与存放位置
template_version: 2
task_type: config
status: verified
parent_requirement: T-2026-09-21-ai-native-sdlc-agent-team-026.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: approved-exception

## Requirement collaboration brief

Parent summary: 用户是单人开发者，要把 AI-native SDLC 各阶段做成可调用角色；首批（方案 A）只做 Stage 1 Plan。`AGENTS.md` 为最高级规则文件，遗留事项必须落成可被其它 AI 执行的文档。

本任务相关细节：
- Problem/background: PDF 的 Stage 1 产物是 `intent.md`，且要求「提交到共享位置、作者与时间戳进入记录」。本仓库尚无该产物的模板与存放约定。
- Users/scenarios: 用户提出想法后，intent-capturer 按模板产出文件，用户确认后移交 requirement-decomposer。
- Expected outcome: 一份模板 + 一处存放约定，使产物格式稳定、可被后续角色机械引用。
- Inputs/outputs: 输入既有 `_TEMPLATE.md` 风格与 `harness-clarification.md` 的字段清单；输出 `.trae/templates/INTENT.md`、`Docs/intent/README.md`。
- Constraints and trade-offs: 存放位置承载两个角色（人读 + 机读），文件名保持 ASCII kebab-case，正文中文。
- AI-autonomous decisions: 文件命名规则、字段集合、目录位置。
- User-confirmation decisions: SoT 分工——intent 管「变更意图与收敛过程」，`Docs/01-demand` 与 `Docs/03-prd-draft` 管「当前产品事实」，交付后按 `documentation.md` 回写。
- Acceptance evidence: 字段集合提取比对、路径存在性断言、占位符扫描。

## Goal

建立 Stage 1 Plan 的产物契约：`.trae/templates/INTENT.md`（模板）与 `Docs/intent/README.md`（存放约定与 SoT 分工），使 intent 产物格式稳定且可被其它角色机械引用。

## Scope

### In scope
- 新建 `.trae/templates/INTENT.md`、`Docs/intent/README.md`。
- 新增校验脚本 `.trae/scripts/check_intent_template.py`。

### Out of scope
- 不创建长期保留的 intent 实例文件。
- 不改写 `Docs/01-demand`、`Docs/03-prd-draft`。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只新增 Markdown 与一个只读校验脚本，现有文档行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 intent-capturer（028）；消费者 requirement-decomposer；证据为 028 与 026 的引用。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯文档新增。

## Documentation impact record

Follow [code-doc alignment](../rules/documentation.md).
- Changed runtime facts: 无（新增产物契约，不改变运行时行为）。
- Authoritative code/config/migration evidence: `.trae/tasks/_TEMPLATE.md`（Task v2 分区风格）、`.trae/rules/harness-clarification.md`（字段清单权威）。
- Documentation surfaces checked: 上述两文件 + `Docs/` 现有目录结构与命名。
- Documentation files updated: 新增 `Docs/intent/README.md`；`Docs/**` 既有正文不动。
- Producer/consumer/sibling/compatibility paths checked: 模板字段是否为 `harness-clarification.md` 字段清单的超集（要求：是）。
- Superseded phrases/examples reverse-searched: 不适用。
- `documentation impact: none` reason and evidence, if applicable: 不改变运行时事实。
- Historical/PRD handling: 保持原样。

## Acceptance criteria

- [ ] `.trae/templates/INTENT.md` 存在，含字段（标题行逐字）：`id`、`title`、`date`、`author`、`origin`、`status`、`Problem（问题与背景）`、`Users and scenarios（用户与场景）`、`Expected outcome（预期结果）`、`Core flow（核心流程）`、`Constraints and non-goals（约束与非目标）`、`Open questions（开放问题）`、`Convergence verdict（收敛判定）`、`Decision record（决策记录）`、`Next step（下一步）`。
- [ ] 「Convergence verdict」节含三问条目，标题逐字为：`有价值吗`、`值得做吗`、`可以做吗`，每问后跟 `结论：是/否/需要更多信息` 与 `证据：`。
- [ ] 「Decision record」节支持三种终态：`accepted`、`rejected`、`needs-more-info`；`rejected` 必须要求填写否决理由字段。
- [ ] 模板字段集合 ⊇ `.trae/rules/harness-clarification.md` 列出的字段清单；脚本输出差集必须为空。
- [ ] `Docs/intent/README.md` 存在，写明：命名规则 `INTENT-YYYY-MM-DD-<kebab-case>.md`、SoT 分工四条、与 requirement-decomposer 的交接方式、`status` 取值表。
- [ ] `Docs/intent/README.md` 中出现的每个仓库内路径均存在。
- [ ] 两个文件均不含 `PENDING`、`TBD`、`TODO`、`待填`。
- [ ] 模板末尾含 `<!-- FIELDS: ... -->` 注释行，原样列出字段名，供 028 做集合相等断言。
- [ ] `<!-- FIELDS: ... -->` 的集合必须**恰好等于**「6 个元数据键 + 模板正文的全部 `## ` 小节标题」——注释是同一份契约的第二个版本，漏一个就与正文漂移。实测曾漏掉 `Core flow（核心流程）`（正文 9 个小节 vs 注释 8 个），由独立复核发现后补齐为 15 项；脚本的 `FIELDS marker lists exactly the declared field names` 与 028 的 `listed body sections equal the template ## titles` 共同守住这条。**第二轮独立复核（C-4）指出这条不变量此前没有任何独立断言**：注记只与脚本里的常量比对，于是「协同缩表」（模板正文 + 注记 + 028 列表 + 常量一起删一个字段）可以零告警降字段。已补一条读模板正文的独立判据 `FIELDS marker equals the template own metadata keys + ## titles`（注记 == front matter 键 ∪ 模板 ## 标题），并配自检用例 `template:marker-vs-section-drift`（实测：把 `## Core flow（核心流程）` 降级为 `###` 后判据行 `marker=15 derived=14 diff=[Core flow（核心流程）]`）。同时把 `template declares all 15 field names` 这个把数字写死在标签里的断言改成 `"template declares all {0} field names".format(len(FIELD_NAMES))`，避免标签与度量脱节。**但这条独立判据只覆盖「注记与正文不一致」（漂移），不覆盖协同缩表**：把模板正文、模板注记、028 的列表与脚本常量一起删一个字段时等号两边同步下移（独立复核第二轮 B-4 实测 14 个字段仍 rc=0）。协同缩表改由一条**读规格**的下界锚挡住：`REQUIRED_BODY_SECTIONS`（9 条）与 `REQUIRED_META_KEYS`（6 条）写在守门里、不由模板推导，配自检用例 `template:required-section-dropped`（删掉正文一行 `## ` 标题即判 FAIL，判据行 `measured=8 missing=[Core flow（核心流程）]`）。

## TDD execution record

### RED — failing test first

- Test/scenario: 不适用（纯文档新增）。
- Command: 不适用
- Exit code: 0
- Observed failure: 不适用
- Failure reason validated: 不适用

### GREEN — minimum implementation

- Implementation: 新增 `.trae/templates/INTENT.md`、`Docs/intent/README.md` 与校验脚本。
- Command: python .trae/scripts/check_intent_template.py
- Exit code: 0
- Passing evidence: 脚本打印字段差集（应为空）、三问存在性、rejected 分支存在性、占位符命中数、失效路径数；本轮共 21 条 OK / 0 条 FAIL / `EXITCODE=0`，其中新增判据的实测判定行为 `FIELDS marker equals the template own metadata keys + ## titles — marker=15 derived=15 diff=[]`。
- Regression evidence: `git status --porcelain` 仅显示新增文件。

### REFACTOR — optional

- Cleanup: 无。
- Regression evidence: 无。

### TDD or sizing exception — only with prior user approval

- Exception type: TDD 不适用（文档/配置类任务，无生产代码）。
- Reason: 交付物为模板与约定文档，不存在可先写的失败测试；等价验证为结构断言脚本。
- User approval: 用户于 2026-09-21 明确批准本批 5 个文档类任务的 TDD 例外（执行前批准，非事后追认）。
- Alternative verification: 上述八条验收标准。

## Implementation notes

- `Docs/intent/` 需要目录占位文件（Git 不跟踪空目录）。
- 模板面向人读，字段名保留中英对照；机器引用以 `<!-- FIELDS: ... -->` 为准。
- 校验脚本纯标准库。

## Verification report

- Verdict: PASS
- Evidence: `python .trae/scripts/check_intent_template.py` → 21 条 OK / 0 FAIL / `EXITCODE=0`，实测判据行：`template body has exactly the 9 required ## sections — measured=9 missing=[] extra=[]`、`template front matter has the 6 required metadata keys — missing=[]`、`FIELDS marker equals the template own metadata keys + ## titles — marker=15 derived=15 diff=[]`。独立复核（verifier 第二轮）判 PASS（9 条事实全绿），并指出 AC9 的不变量当时没有独立断言、且标签把「15」写死（其 C-4 / D-1）；第四轮复核确认这两项字面项已 CLOSED，但指出协同缩表仍未关闭——已在第五轮补下界锚。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。**新增**一条读模板正文的独立判据 `marker == front_matter_keys(tpl) ∪ set(section_titles(tpl))`：原判据只把注记与脚本常量比对，「协同缩表」（模板正文 + 模板注记 + 028 的注记与列表 + 脚本常量一起删一个字段）可以零告警把字段降到 14（verifier 注入实测）。等价覆盖由自检用例 `template:marker-vs-section-drift` 钉住：把 `## Core flow（核心流程）` 降级为 `###` 后判据行 `marker=15 derived=14 diff=[Core flow（核心流程）]`，而「注记 vs 常量」那条仍为 OK。同时把写死在标签里的「15」改为 `"template declares all {0} field names".format(len(FIELD_NAMES))`。
- Gaps: 字段契约仍分散在四处（模板正文、模板注记、028 的注记与输出格式列表、脚本常量）。现在有两条互补判据：`marker == 元数据键 ∪ ## 标题`（挡漂移）与 `REQUIRED_BODY_SECTIONS` / `REQUIRED_META_KEYS` 下界锚（挡协同缩表）；但下界锚本身是写在守门里的规格副本，改动模板结构时必须同步它，否则守门会以「extra=」形式转红。本任务的加固发生在 verifier 第二轮报告之后，其字面项已获第四轮独立复核确认，协同缩表一项的修复属第五轮范围（结论未回）。
- Risks: 四处字段清单若被**同时**改错，两条判据都会放行；只有「模板正文」这一处是本题的最终依据，改动模板必须从正文出发。

## Handoff notes

- Completed: 无。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 028 依赖本任务的字段集合。
- Watch-outs: 字段一旦漂移，028 的输出校验会失败；改模板必须同步改 028 与校验脚本。
