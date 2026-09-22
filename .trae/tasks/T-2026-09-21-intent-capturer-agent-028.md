# Treelearn Harness Task: intent-capturer 角色

id: T-2026-09-21-028
title: intent-capturer 角色
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
depends_on: [T-2026-09-21-plan-stage-context-pack-027.md, T-2026-09-21-pm-discovery-skill-029.md, T-2026-09-21-intent-template-and-home-030.md]
tdd_mode: approved-exception

## Requirement collaboration brief

Parent summary: 用户是单人开发者，要把 AI-native SDLC 各阶段做成可调用角色；首批（方案 A）只做 Stage 1 Plan。`AGENTS.md` 为最高级规则文件，遗留事项必须落成可被其它 AI 执行的文档。

本任务相关细节：
- Problem/background: 用户的核心诉求是「当我有需求时，直接调用一个角色把它收敛成值得做的需求」。目前唯一的入口是 requirement-decomposer，而它要求「已有确认的父 requirement」，即入口在它之前是空的。
- Users/scenarios: 用户带着一个模糊想法调用本角色；产物移交 requirement-decomposer。
- Expected outcome: 一个自带背景包与产品判据的 Plan 阶段入口角色。
- Inputs/outputs: 输入用户自然语言 + `.trae/context/project-brief.md` + `.trae/skills/pm-discovery/SKILL.md` + `.trae/templates/INTENT.md`；输出 `Docs/intent/INTENT-*.md`。
- State and exception handling: 必须在三种终态上都有明确行为——accepted（移交拆分）、rejected（记录否决理由并存档）、needs-more-info（列出还缺什么，不产出可执行需求）。
- Constraints and trade-offs: 角色文件只写触发/权限/输出契约与对技能、模板的引用，不复制方法论正文（避免与 pm-discovery 双份实现）。
- AI-autonomous decisions: 小节结构沿用既有 agent 写法；权限表述。
- User-confirmation decisions: 首批范围；验收方式含历史需求回放。
- Acceptance evidence: 小节名集合比对、引用路径存在性、字段集合与模板相等、回放。

## Goal

新增 `.trae/agents/intent-capturer.md`：Plan 阶段的唯一入口角色，加载背景包与 pm-discovery 技能，产出符合 `.trae/templates/INTENT.md` 的 intent 文件，并显式处理 accepted / rejected / needs-more-info 三种终态。

## Scope

### In scope
- 新建 `.trae/agents/intent-capturer.md`。
- 新增校验脚本 `.trae/scripts/check_intent_capturer_agent.py`。
- 新增自检脚本 `.trae/scripts/dev/checkers_selftest.py`（与 031 共用）：把守门需要的那部分拷进临时副本，注入违规后断言守门必须判 FAIL，再断言还原后转绿；全程不动工作区。
- 新增 `.trae/scripts/dev/check_intent_replay.py`：校验任意一份 intent 实例能否直接交接给 requirement-decomposer（父任务 026 验收标准第 4 条的可复跑形式）。

### Out of scope
- 不实现 Stage 2–6 角色；不改 requirement-decomposer、verifier 等既有角色。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只新增一个角色文件与一个只读校验脚本；既有 9 个角色行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 上游为 `harness-clarification.md` 的澄清协议；下游为 `.trae/agents/requirement-decomposer.md`；证据为两文件的触发条件段。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯文档新增，无可运行分支。

## Documentation impact record

Follow [code-doc alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: `.trae/agents/verifier.md`（小节结构参照）、`.trae/rules/harness-clarification.md`（澄清协议）、`.trae/tasks/_TEMPLATE.md`（分区风格）。
- Documentation surfaces checked: 上述三文件 + `.trae/HARNESS.md` 编排流程。
- Documentation files updated: 无（登记在 031）。
- Producer/consumer/sibling/compatibility paths checked: 与 requirement-decomposer 的触发条件是否重叠（要求：intent-capturer 面向未确认想法，requirement-decomposer 面向已确认 requirement，二者不得互相抢占）。
- Superseded phrases/examples reverse-searched: 不适用。
- `documentation impact: none` reason and evidence, if applicable: 不改变运行时事实；路由登记由 031 统一处理。
- Historical/PRD handling: 保持原样。

## Acceptance criteria

- [ ] `.trae/agents/intent-capturer.md` 存在，且二级小节名集合覆盖 `.trae/agents/verifier.md` 的必需小节（脚本提取 `^## ` 标题，断言含：定位与权限、触发时机、执行前必读、职责清单、输出格式、禁区）。
- [ ] 含显式权限声明：**可写 `Docs/intent/**`**；对 `android/**`、`frontend/**`、`backend/**`、`.trae/tasks/**` 只读。
- [ ] 「执行前必读」引用的三条路径 `.trae/context/project-brief.md`、`.trae/skills/pm-discovery/SKILL.md`、`.trae/templates/INTENT.md` 均存在（脚本逐条断言）。
- [ ] 输出字段名集合与 `.trae/templates/INTENT.md` 尾部 `<!-- FIELDS: ... -->` 注解除列出的集合**相等**（脚本做集合相等断言并打印两侧差集，必须为空）。
- [ ] 含三种终态的处理：`accepted`（移交 requirement-decomposer，附移交清单）、`rejected`（写出否决理由并保留文件，不得删除）、`needs-more-info`（列出缺失字段，不产出可执行需求）。
- [ ] 含「背景缺口上报」路径：当 `.trae/context/**` 与 `Docs/**` 均无法回答某产品事实时，必须报告缺口并标注为未知项，不得编造。
- [ ] 含与 requirement-decomposer 的交接点，写明移交物、判定条件与不满足时的行为。
- [ ] 交付的角色文件全文不含 `PENDING`、`TBD`、`TODO`、`待填`（脚本对角色文件本体做占位符扫描，实测命中为空）。
- [ ] 不复制 pm-discovery 的方法论正文：与 `.trae/skills/pm-discovery/SKILL.md` 的 60 字连续子串重合命中数 ≤ 1（脚本输出命中数）。
- [ ] 上述重合判定同时给两档窗口：60 字档命中数 ≤ 1，并加严一档 40 字档命中数 = 0。理由：60 字档只挡得住 ≥60 字的整句，抄 59 字会静默漏过（实测见自检用例 `intent:copied-methodology-short`）。
- [ ] 40 字档收成 0 命中：它原本允许 2 个窗口，而 41 字的整句照抄恰好只产生 2 个窗口，等于「抄一句」仍能过（独立复核用 41 字粘贴实测打穿过，见自检用例 `intent:copied-methodology-44-chars`）。
- [ ] 「背景缺口上报」必须写成同一子句内的禁令：同时含 `未知项` + `缺口` + 禁止词（`不得`/`不许`/`禁止`）。纯关键词判定挡不住语义取反（独立复核实测：把该子句改成「按常识补一个合理的答案即可」可骗过旧断言，现已被自检用例 `intent:gap-semantics-reversed` 钉住）。
- [ ] 缺口禁令必须「逐字保留」，且**任何提到 `缺口`/`未知项` 的行都不得把它否定、降级或列例外**。两轮独立复核给出的四种绕过都必须被挡住：① 删掉禁令词（`intent:gap-semantics-reversed`）；② 同行把禁令否定掉（`intent:gap-prohibition-negated`）；③ 换成名单外的同义词（「该要求已作废」，`intent:gap-prohibition-synonym`）；④ 原句不动、把降级写在**另一行**（`intent:gap-prohibition-crossline`）。实现：**扫描全文每一行**（不再只取首行、也不再只扫「提到 `缺口`/`未知项` 的行」）+ 线索词正则 `不再|不予|无需|不必|不适用|作废|失效|例外|忽略|跳过|允许|可以按|不受此限|放宽|废止|取消|停止|无约束|免于|松绑|解除`。第四种形态是**耦合洞**：把否定写在「不含 `缺口`/`未知项` 的行」上时旧扫描够不到，连名单内的 `作废` 都能逃（`intent:gap-waiver-uncoupled-line`）。
- [ ] 凡判据写成「词法三词同行」这类 tripwire，验收标准里必须同时写明「它挡得住哪一类、挡不住哪一类」，并给挡不住的那一类补一条注入用例——否则下游会把 tripwire 读成语义判据。
- [ ] 守门双向自检：`python .trae/scripts/dev/checkers_selftest.py` 的 44 个注入用例全部 `OK`（26 个 `intent:` + 2 个 `replay:` + 2 个 `template:` + 14 个 `roadmap:`；14 个 `roadmap:` 由 031 提供。每个用例注入后必须出现对应 FAIL 标签且退出码为 1），3 条基线转绿，末行 `OVERALL: PASS`、`EXITCODE=0`。
- [ ] 回放校验器必须有负向对照：把 `.trae/templates/INTENT.md` 自己当实例喂给 `check_intent_replay.py`，必须判 FAIL。理由：该脚本原先用「`终态：`+取值」的全文子串匹配判定终态，模板把三种取值并列写在同一行，会被读成 `["accepted"]`——「不是实例的模板」因此被判成实例（实测见自检用例 `replay:template-is-not-an-instance`，判据行 `measured=[accepted / rejected / needs-more-info]`）。
- [ ] 终态判定必须取「`终态：`」该行的行内取值、并要求它恰好等于一种取值，不得由全文子串匹配决定；另加一条独立判据：front matter 的 `status` 必须与该取值一致（读另一处来源，避免解析器自证清白）。

## TDD execution record

### RED — failing test first

- Test/scenario: 校验脚本的首条断言——目标角色文件必须存在（文件是交付物的主体，缺它后面全部断言都无意义）。
- Passing evidence: 31 条断言全部 OK，其中可核的实测值：三条前置路径存在性、字段集合相等 diff=[]（两侧各 15 个）、模板 9 个 ## 小节逐字对齐、三终态命中、60 字档 0 命中、40 字档 0 命中；缺口条款三条判据同为 OK（同行三词判定 + 禁令逐字保留 + 该行无否定措辞，判据行 `measured=no negation marker`）。完整输出见 `artifacts/evidence/T-028-031-checkers-selftest.txt` 的 3 条 BASELINE OK 行。
- Exit code: 1
- Observed failure: 脚本打印 FAIL file exists: .trae/agents/intent-capturer.md，随即以 1 退出，其余断言未执行。
- Failure reason validated: 失败由目标文件缺失引起，而不是脚本自身错误——同一脚本在文件写好后全绿（见 GREEN）。

### GREEN — minimum implementation

- Implementation: 新增 `.trae/agents/intent-capturer.md`、校验脚本 `.trae/scripts/check_intent_capturer_agent.py`，以及共用自检脚本 `.trae/scripts/dev/checkers_selftest.py`。
- Command: python .trae/scripts/check_intent_capturer_agent.py
- Passing evidence: 31 条断言全部 OK，其中可核的实测值：三条前置路径存在性、字段集合相等 diff=[]（两侧各 15 个）、模板 9 个 ## 小节逐字对齐、三终态命中、60 字档 0 命中、40 字档 0 命中；缺口条款三条判据同为 OK（同行三词判定 + 禁令逐字保留 + 该行无否定措辞，判据行 `measured=no negation marker`）。完整输出见 `artifacts/evidence/T-028-031-checkers-selftest.txt` 的 3 条 BASELINE OK 行。
- Regression evidence: `.trae/agents/**` 的新增只有本批的 `intent-capturer.md`，既有 9 个 agent 文件未被本任务改动；`python .trae/scripts/dev/checkers_selftest.py` 报 `cases=44 ok=44 mismatch=0 harness_errors=0`（含回放负向对照与终态跨行 CASE 36/44、缺口条款五条 CASE 37–41、模板漂移与下界锚 CASE 42–43）。
- 追记（2026-09-21）：以上 `cases=NN` 是写下当时的读数，此后自检用例随守门新增而增加（同日新增 `roadmap:phase-b-spec-author-lost` 与 17 条 `spec:` 用例，基线由 3 条增至 4 条）。`artifacts/evidence/T-028-031-checkers-selftest.txt` 是活文档，每次守门变更都会重出；**当前读数以该文件自己的 `# Mode:` 行与结尾的 `SELFTEST cases=` 行为准，不要引用旧正文里的数字。**文件头记录了本次运行的被测脚本与 fixture 的 SHA-256，运行后复检为 `Post-run recheck of subject hashes: STABLE`。

### REFACTOR — optional

- Cleanup: 无。
- Regression evidence: 无。

### TDD or sizing exception — only with prior user approval

- Exception type: TDD 不适用（文档/配置类任务，无生产代码）。
- Reason: 交付物为角色定义，不存在可先写的失败测试；等价验证为结构断言脚本 + 父任务要求的历史需求回放。
- User approval: 用户于 2026-09-21 明确批准本批 5 个文档类任务的 TDD 例外（执行前批准，非事后追认）。
- Alternative verification: 上述九条验收标准 + 父任务验收标准第 4 条的历史需求回放。

## Implementation notes

- 角色的职责是「加载正确的材料并守住输出契约」，方法论一律委托给 `pm-discovery`。
- 与用户交互遵循 `harness-clarification.md` 的协作选项协议。
- 校验脚本纯标准库。

## Verification report

- Verdict: PASS
- Evidence: `python .trae/scripts/check_intent_capturer_agent.py` → 31 条 OK / 0 FAIL / `EXITCODE=0`（缺口条款三条判据：同行禁令、逐字保留、全部相关行不得否定）；历史需求回放的独立读数见 `artifacts/evidence/T-026-replay-intent.txt`（回放实例 80 行、13 条 OK、`EXITCODE=0`，内嵌正文可无损重建）。独立复核（verifier 第二轮）判 PASS：15 条验收标准全绿，并指出缺口条款的「否定式同行禁令」绕过（其 C-2）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除。40 字档额度由 ≤2 **收紧为 = 0**：41 字的整句照抄恰好只产生 2 个窗口，原额度等于「抄一句仍能过」（verifier 用 41 字粘贴实测打穿）；当前正文实测 0 命中、最长公共连续串 24 字，收紧不误伤。缺口条款新增两条判据：禁令原句必须逐字出现、该行不得出现 `不再适用`/`不适用`/`无需`/`不必`。等价覆盖由自检用例 `intent:gap-prohibition-negated`（把禁令换成「『不得编造』这条规矩不再适用」）与 `intent:gap-prohibition-hedged`（保留原句再追加否定）钉住，两条注入后各自转红且退出码为 1。
- Gaps: ① 缺口条款是**名单制启发式**，两个边界都如实写明：**挡得住**「删掉禁令词」「同行否定」「跨行降级」「耦合洞（否定写在未提及缺口/未知项的行上）」四类（各配一条自检用例），**挡不住**未列入名单的同义改写——独立复核第三轮实测 `放宽`/`废止`/`取消`/`停止适用`/`无约束力`/`免于`/`松绑` 共 7 串 rc=0，其中前 6 串已并入正则、`停止` 以词根形式覆盖，但名单永远可以再被扩展出新的同义词；② 扫描已放宽到**全文每一行**（当前正文零命中，实测无误报），代价是日后若在角色文件里合法地使用「例外」「允许」等词，需要改写措辞而不是放宽判据；② 回放校验器的终态判据已收紧为「取值必须落在 `终态：` 同一行」（旧正则 `\\s*` 跨行会让 `- 终态：` 换行后的取值也算合规），并配自检用例 `replay:terminal-value-on-next-line`；③ 本任务的第三、四轮加固均发生在对应 verifier 报告之后，第四轮已获独立复核（判 PASS，仍指出名单制边界），第五轮增量复核结论未回。
- Risks: 用关键词守语义天然不完备；若要升级为结构判据，应把该条款改成固定模板句并断言整句，而不是继续叠加关键词。

## Handoff notes

- Completed: 无。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 031 负责把本角色登记进 `AGENTS.md` 路由表。
- Watch-outs: 若日后修改 `.trae/templates/INTENT.md` 的字段，必须同步本角色的输出格式与校验脚本。
