# Treelearn Harness 遗留路线（Stage 2–6）

> 这份文件只回答一件事：**接下来还剩哪些阶段没做、怎么做、怎么验收。**
> 来源：Anthropic《The AI-Native SDLC playbook》的阶段划分，加上 2026-09-21 的开源技能调研。每条「开源来源」列的都是当时实测 HTTP 200 的仓库与文件路径。
> 用法：挑一条条目，把它的「执行提示词」整段粘给一个新会话即可开工。开工后把该条目替换成对应的 Task ID，不要让路线文档和任务状态各说各话。
> 纪律：Phase B 内部按依赖先后排列；前一条没做完，不要跳去后面的条目。

## Phase A（已完成）

方案 A 只做 Stage 1（Plan），已交付以下五件事，构成后续所有阶段的底座：

| ID | 交付物 | 状态 |
|---|---|---|
| A1 | Plan 阶段背景包（`.codex/context/project-brief.md`、`.codex/context/domain-glossary.md`） | 已交付 |
| A2 | 需求收敛技能（`.codex/skills/pm-discovery/SKILL.md`，开源二次优化） | 已交付 |
| A3 | intent 模板与存放约定（`.codex/templates/INTENT.md`、`Docs/intent/README.md`） | 已交付 |
| A4 | Plan 阶段入口角色（`.codex/agents/intent-capturer.md`） | 已交付 |
| A5 | 遗留路线与最高级路由登记（本文件 + `AGENTS.md` 路由表） | 已交付 |

阶段一到六的其余部分（设计、开发、测试、发布、观测）全部落在这份文件的 Phase B 与 Phase C。

## Phase B（下一批）

### B0 spec-author 角色与 SPEC.md 模板

> 本条目由原 C1 提前并扩容而来：原条目把 Stage 2 当成纯技术规格，而且卡在 B1、B4 之后；实际缺口是 Stage 2 的**界面一半**没有归属，且它不依赖前面任何条目。ID 用 B0 是因为它排在最前——它是最上游的阶段，其余 B 条目的产物都要读它。

- **ID**：B0
- **目标**：补上 Stage 2（Design）。把已 accepted 的 intent 写成一份可交付的规格，四类内容缺一不可：**需求规格**（范围、验收方向、非目标）、**界面方案**（信息架构、入口位置、状态与异常、数量与呈现方式）、**技术规格**（接口、数据结构、状态迁移、错误语义）、**疑虑点**（与现有代码、规则或互相矛盾的政策冲突之处，逐条标出来交人拍板）。界面方案必须引用已定义的 token 名，不得出现裸 hex 或临场 px。
- **范围（可写路径）**：`.codex/agents/spec-author.md`、`.codex/templates/SPEC.md`、`.codex/scripts/check_spec_author_agent.py`、`.codex/scripts/dev/checkers_selftest.py`、`Docs/spec/**`
- **依赖**：无前置条目即可开工需求规格与界面方案两部分。技术规格部分建议 B4（codebase-researcher）先落地，否则规格会脱离代码现状。
- **owner_lane**：harness
- **验收方式**：（2026-09-21 按用户决定简化为轻流程）`python .codex/scripts/check_spec_author_agent.py <规格路径>` 退出码 0——只查四件事：文件存在、四节标题齐全、`intent:` 指向真实文件、没有占位符与裸 hex / 临场 px。**不再要求**独立复核轮次、证据文件，以及旧版的「每条检测分支都配一条注入用例」自闸门；规格由人 review 拍板。四类内容（需求规格 / 界面方案 / 技术规格 / 疑虑点）仍是写作要求，见 `.codex/templates/SPEC.md` 与 `.codex/agents/spec-author.md`，只是不再由守门脚本逐条强制。
- **开源来源**：`https://github.com/openai/skills` → `skills/.curated/notion-spec-to-implementation/SKILL.md`；`https://github.com/anthropics/skills` → `skills/doc-coauthoring/SKILL.md`（2026-09-21 实测 HTTP 200）
- **交付任务**：`T-2026-09-21-035`（spec-author 角色、SPEC.md 模板与守门，status=verified）；`T-2026-09-21-036`（第一份规格：AI 内置人设，只出需求规格 / 界面方案 / 疑虑点三节，`Technical spec` 记 `DEFERRED:待 B4`）。本条已开工，后续只需等 B4 落地后补技术规格。
- **执行提示词**：先读 `.codex/templates/INTENT.md`、`.codex/rules/ux-design.md`、`.codex/rules/contracts.md`、`.codex/rules/documentation.md`、`.codex/ROADMAP.md` 五份，再挑一份已 accepted 的意图文件当实例，弄清 intent 的字段契约与现有 UX 政策（设计 token 四方比对脚本在 `.codex/scripts/dev/check_design_tokens.py`，前端 CSS 生成器在 `.codex/scripts/dev/gen_design_tokens_css.py`，权威数值在 `Docs/04-ux-design/design-system/tokens.json`）之后才动手。再参考上游 `https://github.com/openai/skills` 的 notion-spec-to-implementation 与 `https://github.com/anthropics/skills` 的 doc-coauthoring，取其「规格每条都要能追到来源」与「合作式成稿」的做法并二次优化。在 `.codex/agents/` 下新增 spec-author.md：只读上游、只写规格文件，不改代码、不改 intent、不建任务；在 `.codex/templates/` 下新增 SPEC.md，写清「规格里每一条都必须能追到 intent 条目、代码位置或设计 token」；再写一个纯标准库的**轻量**自检脚本（只查文件存在、四节标题、`intent:` 路径、占位符与裸 hex / 临场 px），跑通即可——本阶段不需要证据文件与自闸门。

注意：`.codex/rules/contracts.md` 已把 `frontend/**` 与 `backend/**` 分别授权给 `frontend-dev` 与 `backend-dev`，两目录尚未创建；Web 线方向与边界见 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`。写规格时不要把「仅本机环回可达的桌面服务」当成远程服务端，也不要假设横向扩容或多实例。规格完成后由人 review，接受才交给 requirement-decomposer 拆任务。

注意：`Docs/spec/` 是 Stage 2 的产物目录，模板本身要求四类内容齐全；但**第一份规格**（AI 人设，上游为 `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`）按用户 2026-09-21 的口径**只出需求规格、界面方案、疑虑点三节**，`Technical spec` 一栏写 `DEFERRED:待 B4`，等 B4（codebase-researcher）落地后再补。

### B1 code-reviewer 角色与 REVIEW.md 报告模板

- **ID**：B1
- **目标**：把「写完自检」和「独立复核」分开。verifier 管任务验收，code-reviewer 管代码本身——可读性、边界、错误处理、测试充分性，并产出一份可以归档的复核报告。
- **范围（可写路径）**：`.codex/agents/code-reviewer.md`、`Docs/REVIEW.md`、`.codex/scripts/check_code_reviewer_agent.py`
- **依赖**：`.codex/agents/verifier.md`、`.codex/rules/harness-verification.md`
- **owner_lane**：harness
- **验收方式**：`python .codex/scripts/check_code_reviewer_agent.py` 退出码 0；脚本断言小节名覆盖 `.codex/agents/verifier.md`、复核报告含模板与归档路径、且报告字段不与任务文件的 Verification report 重复定义。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/requesting-code-review/SKILL.md`、`skills/receiving-code-review/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：先读 `.codex/agents/verifier.md`、`.codex/rules/harness-verification.md`、`.codex/HARNESS.md`、`.codex/ROADMAP.md` 四份，弄清现有验收角色的小节结构与只读约束；再读上游 `https://github.com/obra/superpowers` 的 requesting-code-review 与 receiving-code-review 两份 SKILL.md，取其「复核要具体到行、要区分阻塞与非阻塞」的做法并二次优化。在 `.codex/agents/` 下新增 code-reviewer.md，在仓库根 Docs 下新增 REVIEW.md 报告模板，再写一个纯标准库校验脚本检查小节覆盖、报告字段完整与无占位符；跑通后把脚本输出贴进任务证据。

### B2 eval-keeper 角色与评测资产

- **ID**：B2
- **目标**：给提示词、AI 产物和守门规则建立可重跑的评测集，让每次改动都能证明「没变差」，而不是靠肉眼看一遍。
- **范围（可写路径）**：`.codex/agents/eval-keeper.md`、`.codex/scripts/check_eval_keeper_agent.py`，以及 `.codex/` 下新增的 `evals/` 评测目录
- **依赖**：`.codex/agents/verifier.md`、`.codex/rules/regression.md`
- **owner_lane**：harness
- **验收方式**：`python .codex/scripts/check_eval_keeper_agent.py` 退出码 0；脚本断言评测目录至少含一份样例输入、一份基线期望，且跑法是一条可以照抄的命令。
- **开源来源**：`https://github.com/anthropics/skills` → `skills/skill-creator/SKILL.md`；`https://github.com/contains-studio/agents` → `testing/test-results-analyzer.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：读 `.codex/agents/verifier.md`、`.codex/rules/regression.md`、`.codex/rules/observability.md`、`.codex/ROADMAP.md`，再参考上游 `https://github.com/anthropics/skills` 的 skill-creator 里「给技能写评测」的章节和 `https://github.com/contains-studio/agents` 的 test-results-analyzer。在 `.codex/agents/` 下新增 eval-keeper.md，并建立评测目录（样例输入 + 期望输出 + 跑法说明），再写一个纯标准库校验脚本并跑通，输出贴进任务证据。

### B3 守门自动化（全部只读守门一键跑）

- **ID**：B3
- **目标**：把散落的只读守门（Java-only、行数预算、任务契约、SQL）收成一条命令，提交前必须整跑并留证据，杜绝「守门其实没运行却报告通过」。
- **范围（可写路径）**：`.codex/scripts/run_all_guards.py`、`.codex/scripts/check_run_all_guards.py`、`.codex/skills/guard-runner/`
- **依赖**：`.codex/scripts/validate_tasks.py`、`.codex/skills/line-budget/line-budget.ps1`、`android/scripts/no-kotlin-check.ps1`
- **owner_lane**：harness
- **验收方式**：`python .codex/scripts/run_all_guards.py` 退出码 0，且逐条打印每个守门的名字和结论；任何守门没有打印判据行即判 FAIL，不允许只凭退出码 0 报通过。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/verification-before-completion/SKILL.md`、`skills/systematic-debugging/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：读 `.codex/HARNESS.md`、`.codex/rules/harness-verification.md`、`.codex/scripts/validate_tasks.py`、`.codex/README.md`，把仓库里现存的全部守门脚本列成一张表。按上游 `https://github.com/obra/superpowers` 的 verification-before-completion 里「先有证据再宣布完成」的次序，在 `.codex/scripts/` 下新增 run_all_guards.py 与配套校验脚本，硬要求是「判据必须有证据存在性断言」：子进程没打印判据行就算失败，并且给每条检测分支补一条注入违规必须判 FAIL 的自检用例；跑通后把输出贴进任务证据。

### B4 codebase-researcher 角色

- **ID**：B4
- **目标**：动手之前先摸清「这次改动会牵动谁」——producer、consumer、调用链、既有约束与证据，输出一份可复核的影响面清单，避免凭记忆改代码。
- **范围（可写路径）**：`.codex/agents/codebase-researcher.md`、`.codex/scripts/check_codebase_researcher_agent.py`
- **依赖**：`.codex/context/project-brief.md`、`.codex/rules/documentation.md`、`.codex/rules/regression.md`
- **owner_lane**：harness
- **验收方式**：`python .codex/scripts/check_codebase_researcher_agent.py` 退出码 0；脚本断言角色要求每条结论写成「命令 + 文件:行」，并规定找不到时必须显式写「未找到」而不是略过。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/dispatching-parallel-agents/SKILL.md`、`skills/subagent-driven-development/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：读 `.codex/context/project-brief.md`、`.codex/rules/documentation.md`、`.codex/rules/regression.md`、`.codex/agents/verifier.md`，再参考上游 `https://github.com/obra/superpowers` 的 dispatching-parallel-agents。在 `.codex/agents/` 下新增 codebase-researcher.md：每条影响面结论都必须写成「哪条命令 + 哪个文件第几行」，查不到时显式写「未找到」，并区分「实测到的」与「推测的」；配套一个纯标准库校验脚本，跑通后贴证据。

### B5 code-simplifier 角色

- **ID**：B5
- **目标**：在行为不变的前提下定期收敛重复实现与多余抽象。单人项目最容易长出「第二份实现」，这条角色是专门用来消掉它的。
- **范围（可写路径）**：`.codex/agents/code-simplifier.md`、`.codex/scripts/check_code_simplifier_agent.py`
- **依赖**：`.codex/agents/code-reviewer.md`（B1 未完成时本条的验收标准无处安放）、`.codex/rules/regression.md`
- **owner_lane**：harness
- **验收方式**：`python .codex/scripts/check_code_simplifier_agent.py` 退出码 0；脚本断言角色强制「改前先跑测试、改后同一套测试必须全绿」，且禁止把无生产者代码写成注释保留。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/test-driven-development/SKILL.md`、`skills/finishing-a-development-branch/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：读 `.codex/rules/regression.md`、`.codex/rules/documentation.md`、`.codex/agents/verifier.md`、`.codex/ROADMAP.md`，再参考上游 `https://github.com/obra/superpowers` 的 test-driven-development 与 finishing-a-development-branch。在 `.codex/agents/` 下新增 code-simplifier.md，写清两件事：行为不变的判据是「同一套测试改前改后都绿」；无生产者、不可达的代码必须删除，不许加注释保留。配套纯标准库校验脚本并跑通，输出贴进任务证据。

### B6 metrics-reporter 角色

- **ID**：B6
- **目标**：定期把交付节奏、退回次数、守门命中分布、失败案例库增量汇总成一页报告，让单人项目也看得见自己是在变快还是变慢。
- **范围（可写路径）**：`.codex/agents/metrics-reporter.md`、`.codex/scripts/check_metrics_reporter_agent.py`，以及仓库根 `artifacts/` 下新增的报告目录
- **依赖**：`.codex/tasks/`、`artifacts/evidence/`、`AGENTS.md` 的失败案例库
- **owner_lane**：harness
- **验收方式**：`python .codex/scripts/check_metrics_reporter_agent.py` 退出码 0；脚本断言每个指标都写成「哪条命令读出的哪个数」，不接受手抄数字。
- **开源来源**：`https://github.com/contains-studio/agents` → `studio-operations/analytics-reporter.md`、`project-management/experiment-tracker.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：读 `.codex/tasks/_TEMPLATE.md`、`.codex/rules/harness-verification.md`、`.codex/ROADMAP.md`、`.codex/scripts/validate_tasks.py`，再参考上游 `https://github.com/contains-studio/agents` 的 analytics-reporter 与 experiment-tracker。在 `.codex/agents/` 下新增 metrics-reporter.md：每个数字都要写清是哪条命令读出来的，并明确去重口径（同一口径不要既算物理行又算非空行）；报告写到仓库根 `artifacts/` 下的新目录，配套纯标准库校验脚本并跑通。

## Phase C（依赖外部条件）

Phase C 的条目都不是「现在缺人写」，而是**缺前置事实**：在它们各自写明的条件满足之前动手，产出的只是看起来完整的空壳。

编号说明：原 C1（spec-author）已提前并扩容为 Phase B 的 `B0`，因此本阶段从 C2 起、**没有 C1**。保留 C2–C4 原编号不重排，因为已有任务记录按 C2/C3/C4 指路，重排会让那些引用失效。

### C2 loop-watcher 角色

- **ID**：C2
- **目标**：给自动化回路配一个监督者：盯着「跑—失败—修—再跑」的循环是否在收敛，重复同一失败超过阈值就停下来上报，而不是无限重试烧时间。
- **范围（可写路径）**：`.codex/agents/loop-watcher.md`、`.codex/scripts/check_loop_watcher_agent.py`
- **依赖**：B3（守门自动化）先落地，否则没有可被监督的回路；同时需要真实的构建入口（`android/` 下的 Gradle 任务）已经可用。
- **owner_lane**：harness
- **验收方式**：`python .codex/scripts/check_loop_watcher_agent.py` 退出码 0；脚本断言角色给出重复失败的判定阈值、停止条件与上报格式，且禁止在无证据时宣布收敛。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/executing-plans/SKILL.md`；`https://github.com/openai/skills` → `skills/.curated/gh-fix-ci/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：前置条件——B3 已完成，且 `android/` 下的构建入口能跑通。读 `.codex/HARNESS.md`、`.codex/rules/observability.md`、`.codex/ROADMAP.md`、`.codex/scripts/validate_tasks.py`，再参考上游 `https://github.com/obra/superpowers` 的 executing-plans 与 `https://github.com/openai/skills` 的 gh-fix-ci。在 `.codex/agents/` 下新增 loop-watcher.md，写明循环的收敛判据、重复失败的阈值、必须停下来上报的条件，以及每条结论要附哪次运行的证据；配套纯标准库校验脚本并跑通。

### C3 事实源（SoT）全量声明

- **ID**：C3
- **目标**：把「同一个事实只在一个地方有权威版本」落成全仓库级别的清单，逐条写清权威文件、更新时机与同步检查方式，消掉当前分散在 `AGENTS.md`、规则、`Docs/**` 与任务文件里的重复表述。
- **范围（可写路径）**：`.codex/context/source-of-truth.md`、`.codex/scripts/check_source_of_truth.py`
- **依赖**：需要先有足够多的产物边界稳定下来（Stage 3–5 的规格、评测、报告至少各落地一份），否则清单会随产出形态反复重写；当前可参考 `.codex/context/project-brief.md` 已有的 SoT 声明章节。
- **owner_lane**：harness
- **验收方式**：`python .codex/scripts/check_source_of_truth.py` 退出码 0；脚本断言每条事实都有唯一权威路径、且该路径在仓库中真实存在，并对同一事实出现第二处定义时报 FAIL。
- **开源来源**：`https://github.com/anthropics/skills` → `skills/doc-coauthoring/SKILL.md`；`https://github.com/openai/skills` → `skills/.curated/notion-knowledge-capture/SKILL.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：前置条件——`.codex/context/project-brief.md` 里的 SoT 声明已被实际使用过至少一轮。读 `.codex/context/project-brief.md`、`.codex/rules/documentation.md`、`.codex/README.md`、`AGENTS.md`，再参考上游 `https://github.com/openai/skills` 的 notion-knowledge-capture。在 `.codex/context/` 下新增 source-of-truth.md，逐条写「事实 / 唯一权威路径 / 更新时机 / 漂移检查方式」，并写一个纯标准库校验脚本：权威路径必须存在，同一事实出现第二处定义即判 FAIL；跑通后贴证据。

### C4 发布闸门 release-gate

- **ID**：C4
- **目标**：把「能不能发版」写成一条可执行的只读闸门：构建、安装、关键冒烟、版本号与变更记录齐备，缺一项就拦住。
- **范围（可写路径）**：`.codex/skills/release-gate/`、`.codex/scripts/run_release_gate.py`
- **依赖**：需要真实设备或模拟器可用（`android/` 的 instrumentation 套件要能整包跑通），以及 B3 的守门汇总先落地。
- **owner_lane**：harness
- **验收方式**：`python .codex/scripts/run_release_gate.py` 退出码 0，且逐项打印闸门名与结论；任何一项没有打印判据行即判 FAIL。
- **开源来源**：`https://github.com/contains-studio/agents` → `project-management/project-shipper.md`、`engineering/devops-automator.md`（2026-09-21 实测 HTTP 200）
- **执行提示词**：前置条件——B3 已完成，且本机有可用的 Android 设备或模拟器。读 `.codex/rules/startup.md`、`.codex/rules/harness-verification.md`、`.codex/README.md`、`.codex/ROADMAP.md`，再参考上游 `https://github.com/contains-studio/agents` 的 project-shipper 与 devops-automator。在 `.codex/skills/` 下新增 release-gate 技能与 `.codex/scripts/` 下的 run_release_gate.py，闸门项至少包含构建、安装、关键冒烟、版本号与变更记录齐备；每项都要打印判据行，缺判据行即 FAIL，禁止只看退出码；跑通后把输出贴进任务证据。

## 已知欠账（门禁红项，2026-10-15 盘点）

这些不是「还没做的功能」，而是**当前就红着、且每片验收都会被它们绊住**的门禁。它们与本文件 Phase B/C 的区别是：B/C 缺前置条件，这里缺一次对判据本身的裁定。读数取 `T-2026-10-15-web-parity-doc-authority-writeback-002` 的 GREEN 证据块 05/06/07/11 与 `artifacts/evidence/T-2026-10-15-002-guards-baseline.txt`（同一条命令、回写前后逐项相同 → 证明本片没把它们改红，也没顺手改宽）。

〔**2026-10-16 追加 D7 / D8**：这两条的读数**不取上面那份证据**，而取
`artifacts/evidence/T-2026-10-16-first-topic-401-deadend.txt` 与真机连接结果 XML；每条自己写明回读命令，
名单与计数一律现场复算、不抄进正文。〕

〔**2026-10-18 更正读数来源**：上面那句「读数取 002 的 GREEN 证据块 05/06/07/11」是 2026-10-15 的盘点时点，
不是今天的门禁状态——D1 已在该日之后闭合。此后每条欠账的现状只取**它自己那条回读命令**；
把某个时点的捕获当成现状，正是 D1 的成因本身。〕

### D1 `check_roadmap_and_routing` 的一次性迁移断言永久为红（2026-10-18 已按修法 ① 闭合）

- 现象：两条 FAIL——`AGENTS.md adds >= 4 routing rows — measured=0`、`new routing rows cover all four triggers — missing=['intent-capturer', 'pm-discovery', 'project-brief', 'ROADMAP']`。
- 根因：判据写成「读 `git diff -- AGENTS.md`，要求新增行里含 ≥4 条路由表行」。那批路由行早在 2026-09-21 已提交进 AGENTS.md，工作树里不再有这种 diff，于是断言在任何后续片子上都恒为 0——**它验证的是一次历史动作，不是当前状态**。连带后果最重的一条：`checkers_selftest.py` 先跑各守门，遇到红即判 `OVERALL: INCONCLUSIVE` 并以退出码 3 中止，49 个注入用例根本不执行；这就是本仓库「自检跑不出信号」的机制，也是 002 片必须另写一次性驱动复现正控的原因。
- 修法（二选一，需裁定）：① 把迁移断言改成**当前态**断言（AGENTS.md 路由表里必须存在覆盖四个触发词的行，按正文而非按 diff 判），一次性语义删掉；② 保留一次性语义但把它从常跑守门里移出，并在 `checkers_selftest` 的依赖序里把它排除。
- 代价：①要重写判据并补正反自检；②要证明「排除的那条不会漏掉它本来要防的缺陷」。
- 处置（2026-10-18，选 ①）：上面那两条 FAIL 标签连同 `every new routing-row target exists` 一起从判据里删除，路由改判**当前文件全文**的三条——行数下限（`MIN_ROUTE_ROWS` 写在与那张表无关的裸字面量上，改成由表推导等于没有下限）、四个触发词在现行里的覆盖、每条链接目标存在。「本次新增了 N 行路由」不删证据而是降级为打印行，并写明基线与窗口；增量为零时打 `SKIPPED` 而不是 OK，因为「没有可判对象」被读成「判据通过」是同一族的第四种形态。上面那句「49 个注入用例根本不执行」随本条闭合。
- 回读：守门 `python -X utf8 .codex/scripts/check_roadmap_and_routing.py`、自检电池 `python -X utf8 .codex/scripts/dev/checkers_selftest.py`；两者的退出码与用例条数都按命令现场读，本条不抄值（抄了就过期——D1 本身就是这个教训的产物）。承重实测是自检里的四条注入用例，逐条标签见 `checkers_selftest.py` 的 `roadmap:routing-*`：改大下限常量必须红（证下限不来自那张表）、删空规则路由行必须红、去掉一个触发词必须红、把一个目标改不存在必须红。
- 未随之变更的两处（有意保留，避免把历史捕获当现状改写）：`T-2026-10-15-web-parity-doc-authority-writeback-002` 的 GREEN 证据块 06/07 记的是修复前那次运行的 `rc=1`/`rc=3`，它是该片窗口的捕获而不是今天的门禁读数；`artifacts/tmp/gen-002-green.sh` 的期望表因此也没有回改——今天重跑它会因一片改动面早已入库而多点转红，属时效而非回归。以今天的树为准的就是上面两条回读命令。

### D2 `check_context_pack` 的两条判据互斥

- 现象：`docs index equals the real Docs/ file set — listed=21 actual=57`。
- 根因：同脚本另一条判据把 project-brief 限制在 ≤60 行非空非标题行（实测 46），而要把 57 个 Docs 文件全列进索引就不可能只剩一页。**两条判据对同一文件给出互斥要求**，无论怎么改正文都必有一条红——这是 AGENTS.md 失败案例库里「同一条规则写在两个入口」的守门版：不是重复，是互斥。
- 修法（需裁定）：① 索引改为分目录抽样或只列 `Docs/spec/**` + `Docs/intent/**`（其余由目录级 README 承担），并相应改判据；② 给索引单独开预算（brief 只留指针，索引挪到 `.codex/context/docs-index.md`），brief 仍守一页。
- 代价：②多一个文件、要同步更新 AGENTS.md 路由与 `README_ENTRIES`；①要接受「索引不再穷尽」这个语义变化并写清哪些目录被排除。

### D3 `validate_tasks.py` 全目录 16 条 ERROR，落在 4 个历史任务文件

- 现象：`SUMMARY files=68 errors=16 warnings=8`；分布为 `T-2026-09-20-independent-harness-verification-019.md`（3 条依赖文件不存在）、`T-2026-09-20-tree-canvas-tablet-ui-017.md`（3 条依赖 + 5 条 RED 键缺失/退出码非整数）、`T-2026-09-28-chat-child-branch-popup-fix-106.md`（1 条 RED/GREEN 命令不一致）、`T-2026-09-28-chat-math-wire-renderer-104.md`（4 条）。
- 后果：任何人「跑一遍全目录」拿到的都是红，于是这条门禁在成片验收时被跳过——**恒红的门禁等于没有门禁**，与 D1 同病。
- 修法：4 个文件按 Task v2 契约补键或显式标注为「v2 之前的历史记录，契约不追溯」并加入豁免清单（豁免清单只存一处）。
- 代价：补键要还原当时的 RED/GREEN 命令，部分已无法重跑，只能如实标注为历史。

### D4 守门正控必须**现场派生**，不得手抄被检文本

- 现象：`.codex/scripts/dev/checkers_selftest.py` 把 `.codex/skills/pm-discovery/SKILL.md` 的原文手抄成 `STOLEN_LONG` / `STOLEN_SHORT` / `STOLEN_TRIM` 三段样本用作「40/60 字重叠阈值确实会抓抄袭」的正控。002 片改写那一行后，三段样本立刻失配。
- 现症：002 片已把样本重算为改写后文本的精确 60/44 字切片（用守门自己的窗口函数对现文件求命中数：样本长度 89/60/44，60 字档命中 30/1/0，40 字档命中 50/21/5；真实守门的三条 FAIL 判据行由 002 片 GREEN 证据的正控复现块给出），但**这仍是手抄**——下次再改 SKILL.md 会同样失配，而且失配方向是「正控失效、自检仍报绿」，属 fail-open。
- 修法：正控改为**从被判文件现场切片**（取该行前 N 个归一化字符构造样本），并在自检里加一条断言「样本必须能在被判文本里找到原文」，否则判 FAIL。同一改法适用于所有以被测正文为素材的正控。
- 代价：改 `checkers_selftest.py` 的构造逻辑并补一条「样本找不到原文必须判 FAIL」的反向用例；因 D1 挡住自检，还得先让自检能跑到底。〔2026-10-18：后半条前置已满足——D1 闭合后基线段四条守门全绿、注入用例真正执行；用例条数与结论取 `python -X utf8 .codex/scripts/dev/checkers_selftest.py` 的 Mode 行与末两行，本条不抄值。〕

### D5 失败案例库的「条目 > 30 → 整理合并去重」与「AGENTS.md 不得出现删除行」互斥

- 现象：AGENTS.md 的失败案例库开头写「条目 > 30 → 整理合并去重」，现 50 条并已在 002 片追加第 51 条；但 `check_roadmap_and_routing.py` 对 AGENTS.md 的改动形状断言里含「不得出现删除行」，而**合并去重必然产生删除行**。因此这条整理指令在当前守门下无法执行——002 片据此只做了纯追加，开头那句「现 50 条」保持原样未改（改它就得删一行）。
- 后果：一条写着要人做的动作，被另一条门禁拦住，且没人报错。读者按 AGENTS.md 执行会突然得到红，白跑一片。
- 修法（需裁定）：① 给该守门开一条**显式豁免**——只允许失败案例库小节的删除行走「整理片」，且该片必须同时给出条数前后对账；② 把整理改成纯追加式（新写一份汇总文件、旧条目就地标注「已并入某条」而不删），删掉「合并去重」的字面要求。
- 代价：①削弱一条已被两次真实缺陷证明有用的门禁，需要正反自检；②留下双份文本，靠标注维持唯一判定点。

### D6 `check_stack_facts` 只钉精确短语，不做反向搜索

- 现象：002 片的**第二轮**反向搜索才找到 `Docs/spec/SPEC-2026-09-23-providers-and-models.md:168` 这条同族主张——第一轮列出的 9 处是凭已知命中列的，守门也只按「文件 + 那一条精确短语」判，所以它**拦不住新出现的、别处的同族主张**。
- 修法：加一条与清单无关的兜底扫描——对被盘点文件集合之外的 `README*`、`.codex/rules/**`、`.codex/context/**`、`Docs/spec/**` 断言不出现「无服务端 / 不建设.*后端 / 目录不存在」这类**模式**（模式集合只存一处），命中即判 FAIL 并给行号。
- 代价：模式扫描有假阳性风险（改写后的「不做远程服务端」合法），需要像 002 片那样用精确短语与例外清单平衡，并配正反自检；这与 `check_stack_facts` 现有的「精确串而非关键词」取向是**互补**而非替换，两条判据要各自说明自己挡什么。

### D7 真机仪器套件有 11 例既有红，`connectedDebugAndroidTest` 因此不可能全绿

- 现象：在 `ALP-AN00` / API 33 上整包跑 `:app:connectedDebugAndroidTest` 得 **287 例 / 12 失败**。
  其中 11 例与 2026-10-16 的首题改动无关：把 12 个代码文件退回 HEAD（备份后 `git checkout HEAD --`，
  跑完逐文件 sha256 复原）再**隔离跑那 6 个仍失败的类**（该任务不支持 `--tests`，用
  `-Pandroid.testInstrumentationRunnerArguments.class=...` 过滤），得 11 条失败且**逐条同名**、
  全部出现在整包里。第 12 例
  `HomeTopicCardInstrumentedTest.aStoredTopicIsDrawnAsACardWithItsTitleAndMetaAndStillOpensTheConversation`
  只在整包出现、隔离跑（HEAD 与改动后各一次）都通过，是 10000ms 负载超时。名单与计数现场回读，不抄进本行：
  用 `python -X utf8` 解析 `android/app/build/outputs/androidTest-results/connected/debug` 下
  `TEST-ALP-AN00 - 13-_app-.xml` 里带 `<failure>` 的 `<testcase>`；判定与逐块读数见
  `artifacts/evidence/T-2026-10-16-first-topic-401-deadend.txt` 块 06/07。
- 后果：每一片要拿设备证据都得先穿过这 11 例红。恒红的门禁等于没有门禁——与 D1/D3 同病，而且这里
  的代价是**真的会漏**：读者会把「12 失败」读成「本片改坏了」，或者反过来直接跳过整包、让设备侧继续零覆盖。
- 修法（按失败文案分三类，各需一次裁定；文案由上条命令现场回读）：
  ① **产品缺陷，优先级最高**：`AttachmentStorageInstrumentedTest` 与 `ProviderModelDaoInstrumentedTest`
  各一例升级用例，同为 `SQLiteException: table message_annotations already exists (Sqlite code 1)`，
  发生在 `CREATE TABLE message_annotations` —— 升级路径把已存在的表又建了一遍。
  ② **首页/列表在真机上从未画出行**：`HomeMotionInstrumentedTest` ×5 与 `BottomTabShellInstrumentedTest` ×3
  的文案是 `no row arrived in view ... within 10000ms`、`the home never drew 3 card rows`、
  `the home's first card was never laid out`、`view 2131034244 is missing`。这与 ③ 同一个形状，
  **不能先归成"测试不稳"**——先要判定真机上首页历史卡到底画不画得出来（隔离跑能过，说明是负载/时序，
  但那条判据只覆盖 HomeTopicCard 一例）。
  ③ `TreeCanvasDeviceInstrumentedTest.theRealTouchStreamReachesTheCanvasCallbacks` 的
  `a drag must reach onPan` 是设备注入手势到 `onPan` 的通路，与前两类不同源。
- 代价：①要动建表/升级顺序，必须回跑 `db-migration-reviewer` 并在真机与模拟器各验一次（既有
  「跑测环境是覆盖面维度」的失败案例）；②要先做归因实验再决定改产品还是改等待方式，直接调大超时
  无效于根因、只消除假红；③可能要换成 `GestureDescription` 之外的注入手段。

### D8 `ChatGenerationStageTest` 有一对既有 flaky，JVM 套件因此不是 831 全绿

- 现象：`:app:testDebugUnitTest` 报 **831 例 / 2 失败**，固定为
  `ChatGenerationStageTest.aStopRequestTurnsTheTerminalReadIntoTheCanceledStage`（断言文案
  `the stop the attempt obeyed reads canceled, not failed expected:<CANCELED> but was:<FAILED>`）与
  `.anAcceptedSendShowsTheSendingStageUntilTheFirstRowArrives`（`expected:<SENDING> but was:<IDLE>`）。
  这对红在首题改动之前就有：同一轮里把 `ChatPresenter.java` 退回 HEAD、重跑同一任务，两例照旧（读数见
  `artifacts/evidence/T-2026-10-16-first-topic-401-deadend.txt` 块 05）。名单与文案现场回读：用
  `python -X utf8` 解析 `android/app/build/test-results/testDebugUnitTest/` 下带 `<failure>` 的 `<testcase>`。
- 两条失败**症状不同，尚未证实同源**：一条把终态读成 `FAILED` 而非 `CANCELED`，一条在首行到达前读到
  `IDLE` 而非 `SENDING`。可疑的共同点是呈现层时序——`ChatPresenter` 接受发送后先在后台线程
  `read()` 再 `publish(...)`，而 `awaitingFirstRow` 这类标志是在 `publish` 里、即读回**之后**才置位的，
  于是观察窗可能看不到「发送中等首行」这一帧。这只是**假设**：它解释得了第二条、解释不了第一条的
  `FAILED`/`CANCELED` 取值，所以先别按它动手。
- 修法：先分别定位——对第一条查「停止被受理后，终态读回时阶段函数拿到的是哪个输入」，对第二条查
  「首行到达前 `withSending(true)` 那一帧是否已进入过 ViewState」；两条都修完后补一条断言
  「同一批消息序列只能派生出一种阶段序列」，否则下一轮照样漂回两个入口。
- 代价：这是呈现层时序的改动，会碰到 T-117 的跟随判定与生成槽位可见性，需要真机复跑一次才能收；
  本片未做，因为它是既有缺陷、且修它会让首题那半的证据无法归因。
