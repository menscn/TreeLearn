# Treelearn Harness 遗留路线（Stage 2–6）

> 这份文件只回答一件事：**接下来还剩哪些阶段没做、怎么做、怎么验收。**
> 来源：Anthropic《The AI-Native SDLC playbook》的阶段划分，加上 2026-09-21 的开源技能调研。每条「开源来源」列的都是当时实测 HTTP 200 的仓库与文件路径。
> 用法：挑一条条目，把它的「执行提示词」整段粘给一个新会话即可开工。开工后把该条目替换成对应的 Task ID，不要让路线文档和任务状态各说各话。
> 纪律：Phase B 内部按依赖先后排列；前一条没做完，不要跳去后面的条目。

## Phase A（已完成）

方案 A 只做 Stage 1（Plan），已交付以下五件事，构成后续所有阶段的底座：

| ID | 交付物 | 状态 |
|---|---|---|
| A1 | Plan 阶段背景包（`.trae/context/project-brief.md`、`.trae/context/domain-glossary.md`） | 已交付 |
| A2 | 需求收敛技能（`.trae/skills/pm-discovery/SKILL.md`，开源二次优化） | 已交付 |
| A3 | intent 模板与存放约定（`.trae/templates/INTENT.md`、`Docs/intent/README.md`） | 已交付 |
| A4 | Plan 阶段入口角色（`.trae/agents/intent-capturer.md`） | 已交付 |
| A5 | 遗留路线与最高级路由登记（本文件 + `AGENTS.md` 路由表） | 已交付 |

阶段一到六的其余部分（设计、开发、测试、发布、观测）全部落在这份文件的 Phase B 与 Phase C。

## Phase B（下一批）

### B0 spec-author 角色与 SPEC.md 模板

> 本条目由原 C1 提前并扩容而来：原条目把 Stage 2 当成纯技术规格，而且卡在 B1、B4 之后；实际缺口是 Stage 2 的**界面一半**没有归属，且它不依赖前面任何条目。ID 用 B0 是因为它排在最前——它是最上游的阶段，其余 B 条目的产物都要读它。

- **ID**：B0
- **目标**：补上 Stage 2（Design）。把已 accepted 的 intent 写成一份可交付的规格，四类内容缺一不可：**需求规格**（范围、验收方向、非目标）、**界面方案**（信息架构、入口位置、状态与异常、数量与呈现方式）、**技术规格**（接口、数据结构、状态迁移、错误语义）、**疑虑点**（与现有代码、规则或互相矛盾的政策冲突之处，逐条标出来交人拍板）。界面方案必须引用已定义的 token 名，不得出现裸 hex 或临场 px。
- **范围（可写路径）**：`.trae/agents/spec-author.md`、`.trae/templates/SPEC.md`、`.trae/scripts/check_spec_author_agent.py`、`.trae/scripts/dev/checkers_selftest.py`、`Docs/spec/**`
- **依赖**：无前置条目即可开工需求规格与界面方案两部分。技术规格部分建议 B4（codebase-researcher）先落地，否则规格会脱离代码现状。
- **owner_lane**：harness
- **验收方式**：（2026-09-21 按用户决定简化为轻流程）`python .trae/scripts/check_spec_author_agent.py <规格路径>` 退出码 0——只查四件事：文件存在、四节标题齐全、`intent:` 指向真实文件、没有占位符与裸 hex / 临场 px。**不再要求**独立复核轮次、证据文件，以及旧版的「每条检测分支都配一条注入用例」自闸门；规格由人 review 拍板。四类内容（需求规格 / 界面方案 / 技术规格 / 疑虑点）仍是写作要求，见 `.trae/templates/SPEC.md` 与 `.trae/agents/spec-author.md`，只是不再由守门脚本逐条强制。
- **开源来源**：`https://github.com/openai/skills` → `skills/.curated/notion-spec-to-implementation/SKILL.md`；`https://github.com/anthropics/skills` → `skills/doc-coauthoring/SKILL.md`（2026-09-21 实测 HTTP 200）
- **交付任务**：`T-2026-09-21-035`（spec-author 角色、SPEC.md 模板与守门，status=verified）；`T-2026-09-21-036`（第一份规格：AI 内置人设，只出需求规格 / 界面方案 / 疑虑点三节，`Technical spec` 记 `DEFERRED:待 B4`）。本条已开工，后续只需等 B4 落地后补技术规格。
- **执行提示词**：先读 `.trae/templates/INTENT.md`、`.trae/rules/ux-design.md`、`.trae/rules/contracts.md`、`.trae/rules/documentation.md`、`.trae/ROADMAP.md` 五份，再挑一份已 accepted 的意图文件当实例，弄清 intent 的字段契约与现有 UX 政策（设计 token 三方比对脚本在 `.trae/scripts/dev/check_design_tokens.py`，权威数值在 `Docs/04-ux-design/design-system/tokens.json`）之后才动手。再参考上游 `https://github.com/openai/skills` 的 notion-spec-to-implementation 与 `https://github.com/anthropics/skills` 的 doc-coauthoring，取其「规格每条都要能追到来源」与「合作式成稿」的做法并二次优化。在 `.trae/agents/` 下新增 spec-author.md：只读上游、只写规格文件，不改代码、不改 intent、不建任务；在 `.trae/templates/` 下新增 SPEC.md，写清「规格里每一条都必须能追到 intent 条目、代码位置或设计 token」；再写一个纯标准库的**轻量**自检脚本（只查文件存在、四节标题、`intent:` 路径、占位符与裸 hex / 临场 px），跑通即可——本阶段不需要证据文件与自闸门。

注意：`.trae/rules/contracts.md` 在 `frontend/**` 与 `backend/**` 都不存在时仍以 Android 侧契约为准，写规格时不要假设有服务端。规格完成后由人 review，接受才交给 requirement-decomposer 拆任务。

注意：`Docs/spec/` 是 Stage 2 的产物目录，模板本身要求四类内容齐全；但**第一份规格**（AI 人设，上游为 `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`）按用户 2026-09-21 的口径**只出需求规格、界面方案、疑虑点三节**，`Technical spec` 一栏写 `DEFERRED:待 B4`，等 B4（codebase-researcher）落地后再补。

### B1 code-reviewer 角色与 REVIEW.md 报告模板

- **ID**：B1
- **目标**：把「写完自检」和「独立复核」分开。verifier 管任务验收，code-reviewer 管代码本身——可读性、边界、错误处理、测试充分性，并产出一份可以归档的复核报告。
- **范围（可写路径）**：`.trae/agents/code-reviewer.md`、`Docs/REVIEW.md`、`.trae/scripts/check_code_reviewer_agent.py`
- **依赖**：`.trae/agents/verifier.md`、`.trae/rules/harness-verification.md`
- **owner_lane**：harness
- **验收方式**：`python .trae/scripts/check_code_reviewer_agent.py` 退出码 0；脚本断言小节名覆盖 `.trae/agents/verifier.md`、复核报告含模板与归档路径、且报告字段不与任务文件的 Verification report 重复定义。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/requesting-code-review/SKILL.md`、`skills/receiving-code-review/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：先读 `.trae/agents/verifier.md`、`.trae/rules/harness-verification.md`、`.trae/HARNESS.md`、`.trae/ROADMAP.md` 四份，弄清现有验收角色的小节结构与只读约束；再读上游 `https://github.com/obra/superpowers` 的 requesting-code-review 与 receiving-code-review 两份 SKILL.md，取其「复核要具体到行、要区分阻塞与非阻塞」的做法并二次优化。在 `.trae/agents/` 下新增 code-reviewer.md，在仓库根 Docs 下新增 REVIEW.md 报告模板，再写一个纯标准库校验脚本检查小节覆盖、报告字段完整与无占位符；跑通后把脚本输出贴进任务证据。

### B2 eval-keeper 角色与评测资产

- **ID**：B2
- **目标**：给提示词、AI 产物和守门规则建立可重跑的评测集，让每次改动都能证明「没变差」，而不是靠肉眼看一遍。
- **范围（可写路径）**：`.trae/agents/eval-keeper.md`、`.trae/scripts/check_eval_keeper_agent.py`，以及 `.trae/` 下新增的 `evals/` 评测目录
- **依赖**：`.trae/agents/verifier.md`、`.trae/rules/regression.md`
- **owner_lane**：harness
- **验收方式**：`python .trae/scripts/check_eval_keeper_agent.py` 退出码 0；脚本断言评测目录至少含一份样例输入、一份基线期望，且跑法是一条可以照抄的命令。
- **开源来源**：`https://github.com/anthropics/skills` → `skills/skill-creator/SKILL.md`；`https://github.com/contains-studio/agents` → `testing/test-results-analyzer.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：读 `.trae/agents/verifier.md`、`.trae/rules/regression.md`、`.trae/rules/observability.md`、`.trae/ROADMAP.md`，再参考上游 `https://github.com/anthropics/skills` 的 skill-creator 里「给技能写评测」的章节和 `https://github.com/contains-studio/agents` 的 test-results-analyzer。在 `.trae/agents/` 下新增 eval-keeper.md，并建立评测目录（样例输入 + 期望输出 + 跑法说明），再写一个纯标准库校验脚本并跑通，输出贴进任务证据。

### B3 守门自动化（全部只读守门一键跑）

- **ID**：B3
- **目标**：把散落的只读守门（Java-only、行数预算、任务契约、SQL）收成一条命令，提交前必须整跑并留证据，杜绝「守门其实没运行却报告通过」。
- **范围（可写路径）**：`.trae/scripts/run_all_guards.py`、`.trae/scripts/check_run_all_guards.py`、`.trae/skills/guard-runner/`
- **依赖**：`.trae/scripts/validate_tasks.py`、`.trae/skills/line-budget/line-budget.ps1`、`android/scripts/no-kotlin-check.ps1`
- **owner_lane**：harness
- **验收方式**：`python .trae/scripts/run_all_guards.py` 退出码 0，且逐条打印每个守门的名字和结论；任何守门没有打印判据行即判 FAIL，不允许只凭退出码 0 报通过。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/verification-before-completion/SKILL.md`、`skills/systematic-debugging/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：读 `.trae/HARNESS.md`、`.trae/rules/harness-verification.md`、`.trae/scripts/validate_tasks.py`、`.trae/README.md`，把仓库里现存的全部守门脚本列成一张表。按上游 `https://github.com/obra/superpowers` 的 verification-before-completion 里「先有证据再宣布完成」的次序，在 `.trae/scripts/` 下新增 run_all_guards.py 与配套校验脚本，硬要求是「判据必须有证据存在性断言」：子进程没打印判据行就算失败，并且给每条检测分支补一条注入违规必须判 FAIL 的自检用例；跑通后把输出贴进任务证据。

### B4 codebase-researcher 角色

- **ID**：B4
- **目标**：动手之前先摸清「这次改动会牵动谁」——producer、consumer、调用链、既有约束与证据，输出一份可复核的影响面清单，避免凭记忆改代码。
- **范围（可写路径）**：`.trae/agents/codebase-researcher.md`、`.trae/scripts/check_codebase_researcher_agent.py`
- **依赖**：`.trae/context/project-brief.md`、`.trae/rules/documentation.md`、`.trae/rules/regression.md`
- **owner_lane**：harness
- **验收方式**：`python .trae/scripts/check_codebase_researcher_agent.py` 退出码 0；脚本断言角色要求每条结论写成「命令 + 文件:行」，并规定找不到时必须显式写「未找到」而不是略过。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/dispatching-parallel-agents/SKILL.md`、`skills/subagent-driven-development/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：读 `.trae/context/project-brief.md`、`.trae/rules/documentation.md`、`.trae/rules/regression.md`、`.trae/agents/verifier.md`，再参考上游 `https://github.com/obra/superpowers` 的 dispatching-parallel-agents。在 `.trae/agents/` 下新增 codebase-researcher.md：每条影响面结论都必须写成「哪条命令 + 哪个文件第几行」，查不到时显式写「未找到」，并区分「实测到的」与「推测的」；配套一个纯标准库校验脚本，跑通后贴证据。

### B5 code-simplifier 角色

- **ID**：B5
- **目标**：在行为不变的前提下定期收敛重复实现与多余抽象。单人项目最容易长出「第二份实现」，这条角色是专门用来消掉它的。
- **范围（可写路径）**：`.trae/agents/code-simplifier.md`、`.trae/scripts/check_code_simplifier_agent.py`
- **依赖**：`.trae/agents/code-reviewer.md`（B1 未完成时本条的验收标准无处安放）、`.trae/rules/regression.md`
- **owner_lane**：harness
- **验收方式**：`python .trae/scripts/check_code_simplifier_agent.py` 退出码 0；脚本断言角色强制「改前先跑测试、改后同一套测试必须全绿」，且禁止把无生产者代码写成注释保留。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/test-driven-development/SKILL.md`、`skills/finishing-a-development-branch/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：读 `.trae/rules/regression.md`、`.trae/rules/documentation.md`、`.trae/agents/verifier.md`、`.trae/ROADMAP.md`，再参考上游 `https://github.com/obra/superpowers` 的 test-driven-development 与 finishing-a-development-branch。在 `.trae/agents/` 下新增 code-simplifier.md，写清两件事：行为不变的判据是「同一套测试改前改后都绿」；无生产者、不可达的代码必须删除，不许加注释保留。配套纯标准库校验脚本并跑通，输出贴进任务证据。

### B6 metrics-reporter 角色

- **ID**：B6
- **目标**：定期把交付节奏、退回次数、守门命中分布、失败案例库增量汇总成一页报告，让单人项目也看得见自己是在变快还是变慢。
- **范围（可写路径）**：`.trae/agents/metrics-reporter.md`、`.trae/scripts/check_metrics_reporter_agent.py`，以及仓库根 `artifacts/` 下新增的报告目录
- **依赖**：`.trae/tasks/`、`artifacts/evidence/`、`AGENTS.md` 的失败案例库
- **owner_lane**：harness
- **验收方式**：`python .trae/scripts/check_metrics_reporter_agent.py` 退出码 0；脚本断言每个指标都写成「哪条命令读出的哪个数」，不接受手抄数字。
- **开源来源**：`https://github.com/contains-studio/agents` → `studio-operations/analytics-reporter.md`、`project-management/experiment-tracker.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：读 `.trae/tasks/_TEMPLATE.md`、`.trae/rules/harness-verification.md`、`.trae/ROADMAP.md`、`.trae/scripts/validate_tasks.py`，再参考上游 `https://github.com/contains-studio/agents` 的 analytics-reporter 与 experiment-tracker。在 `.trae/agents/` 下新增 metrics-reporter.md：每个数字都要写清是哪条命令读出来的，并明确去重口径（同一口径不要既算物理行又算非空行）；报告写到仓库根 `artifacts/` 下的新目录，配套纯标准库校验脚本并跑通。

## Phase C（依赖外部条件）

Phase C 的条目都不是「现在缺人写」，而是**缺前置事实**：在它们各自写明的条件满足之前动手，产出的只是看起来完整的空壳。

编号说明：原 C1（spec-author）已提前并扩容为 Phase B 的 `B0`，因此本阶段从 C2 起、**没有 C1**。保留 C2–C4 原编号不重排，因为已有任务记录按 C2/C3/C4 指路，重排会让那些引用失效。

### C2 loop-watcher 角色

- **ID**：C2
- **目标**：给自动化回路配一个监督者：盯着「跑—失败—修—再跑」的循环是否在收敛，重复同一失败超过阈值就停下来上报，而不是无限重试烧时间。
- **范围（可写路径）**：`.trae/agents/loop-watcher.md`、`.trae/scripts/check_loop_watcher_agent.py`
- **依赖**：B3（守门自动化）先落地，否则没有可被监督的回路；同时需要真实的构建入口（`android/` 下的 Gradle 任务）已经可用。
- **owner_lane**：harness
- **验收方式**：`python .trae/scripts/check_loop_watcher_agent.py` 退出码 0；脚本断言角色给出重复失败的判定阈值、停止条件与上报格式，且禁止在无证据时宣布收敛。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/executing-plans/SKILL.md`；`https://github.com/openai/skills` → `skills/.curated/gh-fix-ci/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：前置条件——B3 已完成，且 `android/` 下的构建入口能跑通。读 `.trae/HARNESS.md`、`.trae/rules/observability.md`、`.trae/ROADMAP.md`、`.trae/scripts/validate_tasks.py`，再参考上游 `https://github.com/obra/superpowers` 的 executing-plans 与 `https://github.com/openai/skills` 的 gh-fix-ci。在 `.trae/agents/` 下新增 loop-watcher.md，写明循环的收敛判据、重复失败的阈值、必须停下来上报的条件，以及每条结论要附哪次运行的证据；配套纯标准库校验脚本并跑通。

### C3 事实源（SoT）全量声明

- **ID**：C3
- **目标**：把「同一个事实只在一个地方有权威版本」落成全仓库级别的清单，逐条写清权威文件、更新时机与同步检查方式，消掉当前分散在 `AGENTS.md`、规则、`Docs/**` 与任务文件里的重复表述。
- **范围（可写路径）**：`.trae/context/source-of-truth.md`、`.trae/scripts/check_source_of_truth.py`
- **依赖**：需要先有足够多的产物边界稳定下来（Stage 3–5 的规格、评测、报告至少各落地一份），否则清单会随产出形态反复重写；当前可参考 `.trae/context/project-brief.md` 已有的 SoT 声明章节。
- **owner_lane**：harness
- **验收方式**：`python .trae/scripts/check_source_of_truth.py` 退出码 0；脚本断言每条事实都有唯一权威路径、且该路径在仓库中真实存在，并对同一事实出现第二处定义时报 FAIL。
- **开源来源**：`https://github.com/anthropics/skills` → `skills/doc-coauthoring/SKILL.md`；`https://github.com/openai/skills` → `skills/.curated/notion-knowledge-capture/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：前置条件——`.trae/context/project-brief.md` 里的 SoT 声明已被实际使用过至少一轮。读 `.trae/context/project-brief.md`、`.trae/rules/documentation.md`、`.trae/README.md`、`AGENTS.md`，再参考上游 `https://github.com/openai/skills` 的 notion-knowledge-capture。在 `.trae/context/` 下新增 source-of-truth.md，逐条写「事实 / 唯一权威路径 / 更新时机 / 漂移检查方式」，并写一个纯标准库校验脚本：权威路径必须存在，同一事实出现第二处定义即判 FAIL；跑通后贴证据。

### C4 发布闸门 release-gate

- **ID**：C4
- **目标**：把「能不能发版」写成一条可执行的只读闸门：构建、安装、关键冒烟、版本号与变更记录齐备，缺一项就拦住。
- **范围（可写路径）**：`.trae/skills/release-gate/`、`.trae/scripts/run_release_gate.py`
- **依赖**：需要真实设备或模拟器可用（`android/` 的 instrumentation 套件要能整包跑通），以及 B3 的守门汇总先落地。
- **owner_lane**：harness
- **验收方式**：`python .trae/scripts/run_release_gate.py` 退出码 0，且逐项打印闸门名与结论；任何一项没有打印判据行即判 FAIL。
- **开源来源**：`https://github.com/contains-studio/agents` → `project-management/project-shipper.md`、`engineering/devops-automator.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：前置条件——B3 已完成，且本机有可用的 Android 设备或模拟器。读 `.trae/rules/startup.md`、`.trae/rules/harness-verification.md`、`.trae/README.md`、`.trae/ROADMAP.md`，再参考上游 `https://github.com/contains-studio/agents` 的 project-shipper 与 devops-automator。在 `.trae/skills/` 下新增 release-gate 技能与 `.trae/scripts/` 下的 run_release_gate.py，闸门项至少包含构建、安装、关键冒烟、版本号与变更记录齐备；每项都要打印判据行，缺判据行即 FAIL，禁止只看退出码；跑通后把输出贴进任务证据。
