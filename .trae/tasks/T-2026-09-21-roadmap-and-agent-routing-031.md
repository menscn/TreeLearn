# Treelearn Harness Task: 遗留路线文档与最高级路由登记

id: T-2026-09-21-031
title: 遗留路线文档与最高级路由登记
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
depends_on: [T-2026-09-21-plan-stage-context-pack-027.md, T-2026-09-21-pm-discovery-skill-029.md, T-2026-09-21-intent-template-and-home-030.md, T-2026-09-21-intent-capturer-agent-028.md]
tdd_mode: approved-exception

## Requirement collaboration brief

Parent summary: 用户是单人开发者，要把 AI-native SDLC 各阶段做成可调用角色；首批（方案 A）只做 Stage 1 Plan。`AGENTS.md` 为最高级规则文件，遗留事项必须落成可被其它 AI 执行的文档。

本任务相关细节：
- Problem/background: 用户明确说「你需要把遗留的事项给写下来，不然我容易忘，写成文档后其他 AI 能够调用直接执行」。单人开发者没有团队记忆，未交付的阶段会彻底丢失。
- Users/scenarios: 任何后续会话（人或 AI）需要知道「还剩哪些阶段没做、怎么开始、验收什么」。
- Expected outcome: `.trae/ROADMAP.md` 把 Phase B/C 的每一项写成可**直接粘贴执行**的条目，并从 `AGENTS.md` 路由表可达。
- Inputs/outputs: 输入 026–030 的产出与本次开源调研结论；输出 `.trae/ROADMAP.md`，并修改 `AGENTS.md`、`.trae/README.md`。
- Constraints and trade-offs: `AGENTS.md` 是最高级规则文件，本任务对它的改动必须是最小登记，不得重写既有规则正文。
- AI-autonomous decisions: 条目字段设计、Phase 划分、条目 ID 规则（B1/B2…、C1/C2…）。
- User-confirmation decisions: 遗留事项必须成文档；`AGENTS.md` 为最高级规则文件。
- Acceptance evidence: ROADMAP 条目字段完整性脚本、路由行可达性脚本、路径存在性脚本。

## Goal

把 Stage 2–6 的遗留事项固化为 `.trae/ROADMAP.md`，并从最高级规则文件 `AGENTS.md` 的路由表可达，使任意后续会话无需回忆即可继续执行。

## Scope

### In scope
- 新建 `.trae/ROADMAP.md`。
- 修改 `AGENTS.md`：路由表新增触发行 + 工具与参考节新增一条指针。
- 修改 `.trae/README.md`：目录结构节同步。
- 新增校验脚本 `.trae/scripts/check_roadmap_and_routing.py`。
- 复用 028 交付的自检脚本 `.trae/scripts/dev/checkers_selftest.py`，为本任务的守门补齐注入用例（含 AGENTS.md 出现删除行、既有路由行消失、路由增幅回退、README 目录项缺失四类）。

### Out of scope
- 不实现 Phase B/C 的任何条目。
- 不改写 `AGENTS.md` 的失败案例库与既有规则正文。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: `AGENTS.md` 既有路由表行、失败案例库、所有既有规则正文**逐字保留**（证据：删除行数为 0）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费者为任意后续会话；证据为 `AGENTS.md` 路由表新增行。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不改变代码行为；对 `AGENTS.md` 的改动为纯新增。

## Documentation impact record

Follow [code-doc alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: `AGENTS.md`（最高级规则）、`.trae/README.md`（目录说明）。
- Documentation surfaces checked: `AGENTS.md` 全文、`.trae/README.md` 全文、`.trae/HARNESS.md`、026–030 全部子任务文件。
- Documentation files updated: `AGENTS.md`、`.trae/README.md`。
- Producer/consumer/sibling/compatibility paths checked: `AGENTS.md` 路由表新增行与既有行的触发条件是否冲突（要求：不抢占同一触发条件）。
- Superseded phrases/examples reverse-searched: 需检索 `AGENTS.md` 与 `.trae/README.md` 中是否有过时表述需同步。
- `documentation impact: none` reason and evidence, if applicable: 不适用（本任务确有文档改动）。
- Historical/PRD handling: 保持原样。

## Acceptance criteria

- [ ] `.trae/ROADMAP.md` 存在，含三节：`Phase A（已完成）`、`Phase B（下一批）`、`Phase C（依赖外部条件）`。
- [ ] Phase B 与 Phase C 的每个条目含 **8 个字段**且字段名逐字为：`ID`、`目标`、`范围（可写路径）`、`依赖`、`owner_lane`、`验收方式`、`开源来源`、`执行提示词`；脚本逐条断言，缺任一字段即 FAIL。
- [ ] 每个条目的「执行提示词」是一段可直接粘贴给新会话的完整指令（≥40 字），且其中出现的每个仓库内路径均存在（脚本提取反引号内路径逐条断言）。
- [ ] Phase B 至少含 6 个条目：code-reviewer+REVIEW.md、eval-keeper、守门自动化、codebase-researcher、code-simplifier、metrics-reporter。
- [ ] Phase C 至少含 4 个条目：spec-author、loop-watcher、事实源全量声明、发布闸门 release-gate。
- [ ] 每个条目的「开源来源」列出已核实的具体仓库与文件路径；脚本断言 Phase B/C 每条都含 `github.com` 或本机 `skills` 路径。
- [ ] `AGENTS.md` 路由表新增 ≥4 行，触发条件分别覆盖：调用 intent-capturer 做需求收敛、加载 pm-discovery 技能、读取 Plan 阶段背景包、查看遗留路线。每行目标文件均存在。
- [ ] `AGENTS.md` 改动为**纯新增**：`git diff --unified=0 AGENTS.md` 中不存在以 `-` 开头的非 `---` 行（脚本断言删除行数为 0）。
- [ ] `.trae/README.md` 目录结构节含 `.trae/context/`、`.trae/templates/`、`.trae/ROADMAP.md`、`Docs/intent/` 四项。
- [ ] 交付的 `ROADMAP.md` 全文不含 `PENDING`、`TBD`、`TODO`、`待填`（脚本对路线文件本体做占位符扫描，实测命中为空）。
- [ ] 条目里的「开源来源」不是凭印象写的：每条都是 2026-09-21 实测 HTTP 200 的仓库与文件路径（`obra/superpowers`、`anthropics/skills`、`openai/skills`、`contains-studio/agents`）。
- [ ] 守门双向自检：`python .trae/scripts/dev/checkers_selftest.py` 中 14 个 `roadmap:` 注入用例全部 `OK`（含删除行、既有路由行消失、路由增幅回退、目录项缺失），整体 `OVERALL: PASS`。

## TDD execution record

### RED — failing test first

- Test/scenario: 校验脚本的首条断言——目标路线文件必须存在。
- Command: python .trae/scripts/check_roadmap_and_routing.py
- Exit code: 1
- Observed failure: 脚本打印 FAIL file exists: .trae/ROADMAP.md，随即以 1 退出，条目与路由断言未执行。
- Failure reason validated: 失败由目标文件缺失引起；同一脚本在文件写好后全绿（见 GREEN）。期间另修过一处脚本缺陷（`section_body` 在 `###` 处就截断，把 6 个条目读成 0 个），修复后条目数实测为 6/4。

### GREEN — minimum implementation

- Implementation: 新增 `.trae/ROADMAP.md`；`AGENTS.md` 新增 4 行路由 + 1 条指针；`.trae/README.md` 目录结构节同步；新增校验脚本 `.trae/scripts/check_roadmap_and_routing.py`。
- Command: python .trae/scripts/check_roadmap_and_routing.py
- Exit code: 0
- Passing evidence: 28 条断言全部 OK，其中可核的实测值：Phase B 条目 6、Phase C 条目 4、逐条 8 字段缺失列表为空、提示词失效路径 measured_dead=[]、开源来源 measured_weak=[]、AGENTS.md 新增路由 measured=4、删除行 measured_deletions=0、HEAD 既有路由/案例行 47 条全部原样保留（vanished=[]）。
- Regression evidence: 三条可复核读数——① `git diff --unified=0 -- AGENTS.md` 中以 `-` 开头且非 `---` 的行数为 0（守门判据行 `AGENTS.md change contains no deletion line — measured_deletions=0`）；② HEAD 版的 47 条 `|`/`- [` 行在工作区文件中原样存在（判据行 `every pre-existing routing row and case record survives verbatim — measured_head_rows=47 vanished=[]`）；③ 自检 `python .trae/scripts/dev/checkers_selftest.py` 报 `SELFTEST cases=44 ok=44 mismatch=0 harness_errors=0 skipped=0`（其中 14 个 `roadmap:` 用例、2 个回放用例、2 个模板用例、3 条基线）。既有角色与规则未被本任务改动：`.trae/rules/**` 相对 HEAD 的唯一改动是 17:20:37 的规模口径裁定（非本任务），`.trae/agents/**` 的新增只有本批的 `intent-capturer.md`——本任务自身只写了 `.trae/ROADMAP.md`、`AGENTS.md`、`.trae/README.md` 与校验脚本。
- 追记（2026-09-21 22:03）：本任务写下的 Phase B（6 条）/ Phase C（4 条）已同日变更——原 C1（Stage 2 / spec-author）提前并扩容为 `B0 spec-author 角色与 SPEC.md 模板`，Phase B 现为 7 条、Phase C 现为 3 条；`check_roadmap_and_routing.py` 的 `MIN_PHASE_B`/`MIN_PHASE_C` 与 `PHASE_B_KEYWORDS`/`PHASE_C_KEYWORDS` 已同步（`spec-author` 从 C 移入 B），判据文案改为 `Phase B covers the seven planned capabilities` / `Phase C covers the three planned capabilities`。自检已重跑；读数以 `artifacts/evidence/T-028-031-checkers-selftest.txt` 的 `# Mode:` 行与 `SELFTEST cases=` 行为准（活文档，每次守门变更重出）。

### REFACTOR — optional

- Cleanup: 无。
- Regression evidence: 无。

### TDD or sizing exception — only with prior user approval

- Exception type: TDD 不适用（文档/配置类任务，无生产代码）。
- Reason: 交付物为路线文档与规则登记，不存在可先写的失败测试；等价验证为结构断言脚本。
- User approval: 用户于 2026-09-21 明确批准本批 5 个文档类任务的 TDD 例外（执行前批准，非事后追认）。
- Alternative verification: 上述十条验收标准。

## Implementation notes

- `AGENTS.md` 的改动位置：`## 按需规则路由` 表的触发条件列新增行；`## 工具与参考` 新增一条指向 `.trae/ROADMAP.md`。
- 执行提示词必须自带上下文（读哪个文件、产出什么、怎么验收），因为新会话没有本次对话的记忆。
- 校验脚本纯标准库。

## Verification report

- Verdict: PASS
- Evidence: `python .trae/scripts/check_roadmap_and_routing.py` → 28 条 OK / 0 FAIL / `EXITCODE=0`。三条互不依赖的回归读数：① `git diff --unified=0 -- AGENTS.md` 中以 `-` 开头且非 `---` 的行数为 0（判据行 `AGENTS.md change contains no deletion line — measured_deletions=0`）；② HEAD 版的 47 行（17 条 `|` 路由行 + 30 条 `- [` 案例行）在工作区文件中原样存在（判据行 `measured_head_rows=47 vanished=[]`）；③ 双向自检 `cases=44 ok=44 mismatch=0 harness_errors=0`、3 条基线、`OVERALL: PASS`。独立复核（verifier 第二轮）判 PASS 并逐条复现了这三条读数（F1 已关闭）。
- 追记（2026-09-21 22:03）：本任务写下的 Phase B（6 条）/ Phase C（4 条）已同日变更——原 C1（Stage 2 / spec-author）提前并扩容为 `B0 spec-author 角色与 SPEC.md 模板`，Phase B 现为 7 条、Phase C 现为 3 条；`check_roadmap_and_routing.py` 的 `MIN_PHASE_B`/`MIN_PHASE_C` 与 `PHASE_B_KEYWORDS`/`PHASE_C_KEYWORDS` 已同步（`spec-author` 从 C 移入 B），判据文案改为 `Phase B covers the seven planned capabilities` / `Phase C covers the three planned capabilities`。自检已重跑；读数以 `artifacts/evidence/T-028-031-checkers-selftest.txt` 的 `# Mode:` 行与 `SELFTEST cases=` 行为准（活文档，每次守门变更重出）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除或放宽。`AGENTS.md` 受守门硬约束「0 条删除行」，因此本任务与后续会话对它的改动**只能是追加**；本轮追加的失败案例库条目也走同一条路径。
- Gaps: `PROMPT_PATH_RE` 只认 `Docs|.trae|android|artifacts|AGENTS.md` 前缀，某条执行提示词若引用 `backend/**` 或 `frontend/**` 的具体文件，不会被存在性检查（当前 10 条提示词未触及）。
- Risks: 因 `AGENTS.md` 只能追加、不能改写，失败案例库末尾注记里的条目数会随每次追加漂移；本轮追加后该节实测 42 条（机械计数：`## 失败案例库` 之后以 `- [` 开头的行数），与注记「现 42 条」一致，但下一次追加必须同时处理这个数。

## Handoff notes

- Completed: 无。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 用户随时可从 `AGENTS.md` 找到 `.trae/ROADMAP.md` 并挑选 Phase B 条目开工。
- Watch-outs: 若 Phase B 条目被执行，必须把该条目改为对应 Task ID，避免路线文档与任务状态各说各话。
