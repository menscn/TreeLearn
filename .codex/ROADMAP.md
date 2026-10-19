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

注意：`.codex/rules/contracts.md` 已把 `frontend/**` 与 `backend/**` 分别授权给 `frontend-dev` 与 `backend-dev`；`frontend/` 已落地（阶段 2 骨架 011–015、阶段一夹具 019、阶段三逐屏渲染与走查 020），`backend/` 目录尚未落地，不要把规划事实写成已运行服务。Web 线方向与边界见 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`。写规格时不要把「仅本机环回可达的桌面服务」当成远程服务端，也不要假设横向扩容或多实例。规格完成后由人 review，接受才交给 requirement-decomposer 拆任务。

注意：`Docs/spec/` 是 Stage 2 的产物目录，模板本身要求四类内容齐全；但**第一份规格**（AI 人设，上游为 `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`）按用户 2026-09-21 的口径**只出需求规格、界面方案、疑虑点三节**，`Technical spec` 一栏写 `DEFERRED:待 B4`，等 B4（codebase-researcher）落地后再补。

### B1 code-reviewer 角色与 REVIEW.md 报告模板

- **ID**：B1
- **目标**：把「写完自检」和「独立复核」分开。verifier 管任务验收，code-reviewer 管代码本身——可读性、边界、错误处理、测试充分性，并产出一份可以归档的复核报告。
- **范围（可写路径）**：`.codex/agents/code-reviewer.md`、`.codex/agents/code-reviewer.toml`、`Docs/REVIEW.md`、`Docs/reviews/`、`.codex/scripts/check_code_reviewer_agent.py`、`.codex/scripts/dev/checkers_selftest.py`、`.codex/scripts/run_all_guards.py`、`.codex/HARNESS.md`、`.codex/README.md`
- **依赖**：`.codex/agents/verifier.md`、`.codex/rules/harness-verification.md`
- **owner_lane**：harness
- **验收方式**：`python -X utf8 .codex/scripts/check_code_reviewer_agent.py` 退出码 0；脚本断言角色小节名覆盖 `.codex/agents/verifier.md` 现文并点名四个复核维度、报告骨架有归档位置且该目录在场、区分阻塞与非阻塞、每条 finding 要 `文件:行`、报告字段不与任务文件的 Verification report 重复定义、无占位符、`.toml` 正文与 `.md` 逐字相同。判据条数与注入用例条数以 `python -X utf8 .codex/scripts/dev/checkers_selftest.py` 的现场读数为准，本条不复述。
- **开源来源**：`https://github.com/obra/superpowers` → `skills/requesting-code-review/SKILL.md`、`skills/receiving-code-review/SKILL.md`（2026-09-21 实测 HTTP 200）
- **交付任务**：`T-2026-10-19-024`（`.codex/agents/code-reviewer.md` + 同名 `.toml` 运行时包装、`Docs/REVIEW.md` 骨架、`Docs/reviews/` 归档目录、`check_code_reviewer_agent.py` 与其 `review:*` 注入用例，并登记进 `run_all_guards.py` 的 core 档；status=verifying）。执行提示词里「检查无占位符」照做了，但**没有**照它只做三件事：小节覆盖改成对 verifier.md 现文现场派生（抄来的清单会随 verifier 演化而失真），并补了归档目录在场、阻塞/非阻塞二分、每条 finding 给 `文件:行`、`.toml` 与 `.md` 逐字相同四条——都是本片交付后真实会坏的形态。
- **实现要点（交付后回写，不是验收方式的替代）**：两个角色的**字段名不能重叠**，所以「报告骨架字段」与「任务卡 Verification report 字段」都从各自文件现场派生再比对，两侧各配一条裸下限（`MIN_REVIEW_FIELDS` / `MIN_TASK_FIELDS`）：只有下限能挡住「两边一起删到只剩一个字段，于是重叠判定自动成立」这种协同缩表。`.md` 是权威正文、`.toml` 是 Codex 运行时包装，本仓库没有生成器，所以这条重复由判据钉住（逐字相同）而不是由脚本消除。**注入用例必须覆盖被改词的全部出现处**：把「测试充分性」只替换第一处时判据仍放行，因为角色正文的输出格式小节里还有第二处——该用例因此改成全替。同一类漂移在交付后回写时又发生了一次：B1 的 `范围` 行新增了 `.codex/HARNESS.md`，抢走了 `roadmap:prompt-dead-path` 用例里"只替换第一处"的目标（`范围` 不是被判字段，于是注入变成空操作、判据 rc=0），改成全替后注入落回 `执行提示词`，用例恢复可复算。**判据字段之外的文本也会挪动注入的落点**，所以这两条都归到同一条规矩：用全替，或把 old 串锚到被判字段上。
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
- **交付任务**：`T-2026-10-19-023`（`run_all_guards.py` 汇总器 + `check_run_all_guards.py` 26 例注入电池 + `.codex/skills/guard-runner/SKILL.md`，status=verifying）。与 B0 同样只加这行、条目本身留在 Phase B，因为**注册表会过期**：条目里的表是 2026-09-21 的设想，实际那张表的条数随每一片交付变化，读数一律用 `python -X utf8 .codex/scripts/run_all_guards.py --list` 现场取。同片闭合 D9。
- **实现要点（交付后回写，不是验收方式的替代）**：判定用**两个条件**——退出码在该条的判绿集合里 **且** 输出里有它自己的判据行；只满足退出码的那条守门算「哑」并判 FAIL，两个都不满足而「根本没跑起来」（解释器缺失、脚本不在场、输出非 UTF-8）算 FAULT 并把总退出码压到 3，因为仪器坏了比判红更该被看见。子进程一律 Popen + 带 deadline 的 `communicate`，且先收裸字节再自己解码：Windows 上 `subprocess.run` 超时后会无限等管道（守门的孙进程握着管道时汇总器跟着挂死），而其读取线程会把解码异常吞成「没有输出」，于是「乱码」被读成「这条没判据」——两个病因不能混在一条 FAIL 里。
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
- **依赖**：`.codex/agents/code-reviewer.md`（B1 已于 `T-2026-10-19-024` 交付：本条角色的只读契约与报告字段边界都沿它对齐，简化角色不得再另立一套验收措辞）、`.codex/rules/regression.md`
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

〔**2026-10-19 状态回写**：D4、D6 已实施（片 `T-2026-10-19-harness-guard-judgements-d4-d6-021.md`，
正反双向见 `artifacts/evidence/T-2026-10-19-harness-guards-d4-d6.txt`）；D2、D3、D5 经复核**已不在当前树里成立**，
各自的闭合方式写在该条的「处置」行（D2 = 判据改为合法子集，D3 = 被检集合随 `.trae/` 删除而换位，
D5 = 互斥的两边同时消失），都是「复核后如实回写」而不是「本片修好的」，别把这三条算进 021 的交付。
同日登记 D9（过期指针）。标题里的「门禁红项」从此应读作「盘点台账」：**每条的红绿只由它自己的回读命令决定**，
今天重跑本节的每一条命令，得到的可能全是绿——那不是本节过期，而是它按设计工作。〕

〔**2026-10-19 第二批**：D9 随片 023 闭合（落点改为「正文里真的有那段话的地方」，见该条处置）；
同日登记并闭合 **D10**（androidTest 用裸字面量标注历史 schema 版本，使静态门禁在当前树恒红），
实施片 `T-2026-10-19-androidtest-derived-schema-version-022.md`。D1–D8 未因这两片变动判据。〕

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
- 处置（2026-10-19 复核，按 ① 的语义闭合，非本片所做）：判据标签已从 `docs index equals the real Docs/ file set` 改成
  `docs index is a valid curated subset of the real Docs/ file set`，实现只要求「索引非空 ∧ 每条列出的路径真实存在 ∧
  listed ≤ actual」（`.codex/scripts/check_context_pack.py:56`），互斥由此消解：一页预算与穷尽不再同时被要求。
  「哪些目录被排除」没有写成白名单，而是把差额**打印成 `unlisted=[...]`**——排除是可见的观测而不是隐藏的宽免，
  与 D1 里「增量降级为打印行」同一处理。入库记录：commit `57198af`。
- 回读：`python -X utf8 .codex/scripts/check_context_pack.py`；`listed/actual/unlisted` 三个读数按该命令现场取，
  本条不抄值。承重判据是紧随其后的 `every indexed path exists — dead=[]`：子集化只放松「全不全」，不放松「指不指得到」。
  注意这条与 D9 无关：本条正文里「AGENTS.md 失败案例库」那个说法已经过期。

### D3 `validate_tasks.py` 全目录 16 条 ERROR，落在 4 个历史任务文件

- 现象：`SUMMARY files=68 errors=16 warnings=8`；分布为 `T-2026-09-20-independent-harness-verification-019.md`（3 条依赖文件不存在）、`T-2026-09-20-tree-canvas-tablet-ui-017.md`（3 条依赖 + 5 条 RED 键缺失/退出码非整数）、`T-2026-09-28-chat-child-branch-popup-fix-106.md`（1 条 RED/GREEN 命令不一致）、`T-2026-09-28-chat-math-wire-renderer-104.md`（4 条）。
- 后果：任何人「跑一遍全目录」拿到的都是红，于是这条门禁在成片验收时被跳过——**恒红的门禁等于没有门禁**，与 D1 同病。
- 修法：4 个文件按 Task v2 契约补键或显式标注为「v2 之前的历史记录，契约不追溯」并加入豁免清单（豁免清单只存一处）。
- 代价：补键要还原当时的 RED/GREEN 命令，部分已无法重跑，只能如实标注为历史。
- 处置（2026-10-19 复核，闭合方式为**换位而不是补键**）：这 16 条 ERROR 落在 `.trae/tasks/` 的 68 张旧卡上，
  而 `validate_tasks.py` 只扫 `.codex/tasks/*.md`（`.codex/scripts/validate_tasks.py:322`）。流程迁移把整棵
  `.trae/` 按用户授权删除（commit `57198af`），被检集合因此换成迁移后的卡片全集，恒红随之消失。
  如实写明三点：①**没有**给那 4 个文件补键，也没有建立豁免清单——它们不在本仓库历史里，无从补证；
  ②上面那些读数（`files=68 errors=16 warnings=8`）是 2026-10-15 的盘点时点，今天复算不出同一形状，
  这与 D1 的「把某时点的捕获当成现状」是同一个陷阱，故此条只留命令不留值；
  ③本条真正要防的是「恒红的门禁在成片验收时被跳过」，那个风险对**当前**卡片集同样成立，
  所以回读命令必须每次成片验收现场跑，而不是引用本条历史。
- 回读：`python -X utf8 .codex/scripts/validate_tasks.py` 的 `SUMMARY files=… errors=… warnings=…` 行；
  `errors` 必须为 0，任何非 0 都是本片窗口的待修项而不是「历史噪音」。

### D4 守门正控必须**现场派生**，不得手抄被检文本

- 现象：`.codex/scripts/dev/checkers_selftest.py` 把 `.codex/skills/pm-discovery/SKILL.md` 的原文手抄成 `STOLEN_LONG` / `STOLEN_SHORT` / `STOLEN_TRIM` 三段样本用作「40/60 字重叠阈值确实会抓抄袭」的正控。002 片改写那一行后，三段样本立刻失配。
- 现症：002 片已把样本重算为改写后文本的精确 60/44 字切片（用守门自己的窗口函数对现文件求命中数：样本长度 89/60/44，60 字档命中 30/1/0，40 字档命中 50/21/5；真实守门的三条 FAIL 判据行由 002 片 GREEN 证据的正控复现块给出），但**这仍是手抄**——下次再改 SKILL.md 会同样失配，而且失配方向是「正控失效、自检仍报绿」，属 fail-open。
- 修法：正控改为**从被判文件现场切片**（取该行前 N 个归一化字符构造样本），并在自检里加一条断言「样本必须能在被判文本里找到原文」，否则判 FAIL。同一改法适用于所有以被测正文为素材的正控。
- 代价：改 `checkers_selftest.py` 的构造逻辑并补一条「样本找不到原文必须判 FAIL」的反向用例；因 D1 挡住自检，还得先让自检能跑到底。〔2026-10-18：后半条前置已满足——D1 闭合后基线段四条守门全绿、注入用例真正执行；用例条数与结论取 `python -X utf8 .codex/scripts/dev/checkers_selftest.py` 的 Mode 行与末两行，本条不抄值。〕
- 处置（2026-10-19，片 `T-2026-10-19-harness-guard-judgements-d4-d6-021.md`）：三段手抄常量
  （`STOLEN_LONG` / `STOLEN_SHORT` / `STOLEN_TRIM`）已从脚本里**删除**，代之以 `derive_sample()`：
  在 `normalize` 后的 pm-discovery 现文上逐窗口滑动取样，样本必须同时满足三条——① 逐字存在于现文
  （就是上面要求的「找不到原文即失效」），② 不含路径片段与占位符词（否则 `mask_paths` 与
  「no placeholder tokens」两条判据会掺进命中数），③ 落在预期档位（`fire60` 要求 60 字档命中 > 1，
  `quiet60` 要求 ≤ 1，两档都要求 40 字档至少 1 命中）。取不到样本时 `run_case` 返回 HARNESS，
  聚合为 `OVERALL: INCONCLUSIVE` / rc=3，即「没跑完」而不是「用例通过」。
  **另加一层本条原文没要求的防护**：不在运行时断言样本内容（派生自现文，查了恒真、等于没查），
  改为在 `main()` 开头断言**用例形状**——三条 `intent:copied-methodology*` 的操作必须是 `append_sample`，
  一旦有人把它改回写死文本，电池在建副本之前就 rc=3。阈值本身一位未动（60/40 字档、允许命中 1/0），
  样本长度沿用历史实测的 89/60/44，避免把「阈值改宽」混进「样本改派生」。
- 回读：`python -X utf8 .codex/scripts/dev/checkers_selftest.py`（末三行给用例数与结论；三条抄检用例的
  样本串与命中数打在各自 CASE 行的 FAIL 文本里，可直接与现文对读）；变异实测见
  `artifacts/evidence/T-2026-10-19-harness-guards-d4-d6.txt` 块 05（改回字面量 → rc=3）与块 00 B 段
  （改写正文 → 旧电池 3 条 MISMATCH、新电池全绿）。同一批驱动脚本在 gitignore 的 `artifacts/tmp/`，
  长期可复算的部分是上面那条命令与脚本内的闸门，不是那块一次性对照。

### D5 失败案例库的「条目 > 30 → 整理合并去重」与「AGENTS.md 不得出现删除行」互斥

- 现象：AGENTS.md 的失败案例库开头写「条目 > 30 → 整理合并去重」，现 50 条并已在 002 片追加第 51 条；但 `check_roadmap_and_routing.py` 对 AGENTS.md 的改动形状断言里含「不得出现删除行」，而**合并去重必然产生删除行**。因此这条整理指令在当前守门下无法执行——002 片据此只做了纯追加，开头那句「现 50 条」保持原样未改（改它就得删一行）。
- 后果：一条写着要人做的动作，被另一条门禁拦住，且没人报错。读者按 AGENTS.md 执行会突然得到红，白跑一片。
- 修法（需裁定）：① 给该守门开一条**显式豁免**——只允许失败案例库小节的删除行走「整理片」，且该片必须同时给出条数前后对账；② 把整理改成纯追加式（新写一份汇总文件、旧条目就地标注「已并入某条」而不删），删掉「合并去重」的字面要求。
- 代价：①削弱一条已被两次真实缺陷证明有用的门禁，需要正反自检；②留下双份文本，靠标注维持唯一判定点。
- 处置（2026-10-19 复核，**互斥的两边都不再存在**，因此无需裁定）：
  ① AGENTS.md 里已经没有「失败案例库」小节，也没有「条目 > 30 → 整理合并去重」这句要求
  （用户按当前工作树为准删除了那两节，见 D9；现行历史案例的归属地是 `.codex/references/regression-cases.md`）；
  ② `check_roadmap_and_routing.py` 里「AGENTS.md 改动不得出现删除行」这条形状断言已随 D1 的修法降级为
  `c.note` 打印行（`.codex/scripts/check_roadmap_and_routing.py:193-215`：增量只在窗口内成立，故不当判据），
  自检里对应的两条用例 `roadmap:agents-row-deleted` / `roadmap:agents-existing-row-vanished` 也已从
  `CASES` 中退役。两侧同时消失 ⇒ 本条描述的「写着要人做、却被另一条门禁拦住」在当前树里无从发生。
  这条**不是**被修复成「允许删除行」，而是被检文本与被检形状各自换了归属；保留本条正文是为了留住成因形状。
- 回读：`grep -n 失败案例 AGENTS.md`（退出码 1 = 无命中）与
  `python -X utf8 .codex/scripts/check_roadmap_and_routing.py`（看 `SKIPPED`/`NOTE` 行而非删除线判据）；
  若将来把案例库放回 AGENTS.md，必须连同②一起重新裁定，不要只恢复文本。

### D6 `check_stack_facts` 只钉精确短语，不做反向搜索

- 现象：002 片的**第二轮**反向搜索才找到 `Docs/spec/SPEC-2026-09-23-providers-and-models.md:168` 这条同族主张——第一轮列出的 9 处是凭已知命中列的，守门也只按「文件 + 那一条精确短语」判，所以它**拦不住新出现的、别处的同族主张**。
- 修法：加一条与清单无关的兜底扫描——对被盘点文件集合之外的 `README*`、`.codex/rules/**`、`.codex/context/**`、`Docs/spec/**` 断言不出现「无服务端 / 不建设.*后端 / 目录不存在」这类**模式**（模式集合只存一处），命中即判 FAIL 并给行号。
- 代价：模式扫描有假阳性风险（改写后的「不做远程服务端」合法），需要像 002 片那样用精确短语与例外清单平衡，并配正反自检；这与 `check_stack_facts` 现有的「精确串而非关键词」取向是**互补**而非替换，两条判据要各自说明自己挡什么。
- 处置（2026-10-19，片 `T-2026-10-19-harness-guard-judgements-d4-d6-021.md`）：按上面那条修法落地，判据由四条增至六条。
  ① `STALE_PATTERNS`（8 条，短语与正则混排，模式集合只存这一处）+ 裸字面量下限 `MIN_PATTERNS = 6`——
  与 `MIN_INVENTORY` 同一理由：被检集合不能给自己定下限，写成 `len(STALE_PATTERNS)` 就等于「清空模式表也照样绿」。
  ② 扫描集按**前缀**取（`PATTERN_SCAN_PREFIXES = README* / .codex/rules/** / .codex/context/** / Docs/spec/**`），
  与 `STACK_FACTS` 那份十条枚举**刻意不同源**——复用清单就等于这条判据不存在。
  ③ 命中不直接判红：同一行含权威规格名（`AUTHORITY`）算「引用历史」，计入 `attributed` 观测；
  不带指针才算「把旧方向当现状」并给到 `文件:行号`。权威规格自身所在文件整体豁免，因为它是这条事实的定义处、
  必然要引用被它取代的旧主张。这条区分是刻意的：若凡提及即红，唯一变绿办法是删掉纠正性引用，
  等于让守门逼人销毁证据——所以自检必须同时配「裸命中判红」与「带指针判绿」两条用例（`pattern-bare` /
  `pattern-attributed`），只配前者可以靠「更宽的守门」蒙过自检。
  ④ 自检夹具额外拷入一份**清单外**文件（`PATTERN_SCAN_SAMPLE = .codex/rules/sql.md`）：没有它，
  这条判据在自检里只被清单内文件证到，恰好没证到它要防的那件事。用例数断言随之从 `2N+2` 改为 `2N+5`，
  少跑一个分支即判 rc=3。
- 回读：`python -X utf8 .codex/scripts/dev/check_stack_facts.py`（六行判据，末两行是本条新增的两条）与
  `python -X utf8 .codex/scripts/dev/check_stack_facts.py --selftest`（用例数与 `expected=` 对读）；
  缺陷复现的一次性对照见 `artifacts/evidence/T-2026-10-19-harness-guards-d4-d6.txt` 块 00
  （同一份副本、同一注入行：改前脚本 rc=0 看不见，改后脚本 rc=1）与块 03（真实树上的注入→判红→字节级还原→判绿）。
- 覆盖面边界（写在这里，免得下一位把它当成穷尽）：扫描集四个前缀**之外**新写出的同族句子（例如
  `Docs/05-technical-design/**` 里）不在兜底范围内，那类文件由精确串那条管；两条判据各挡一件事，
  合并成一条会让 002 片的第 10 处命中重新变成看不见。

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

### D8 `ChatGenerationStageTest` 有一对既有 flaky，JVM 套件因此不是 831 全绿（2026-10-18 已闭合，见处置）

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
- 处置（2026-10-18，片 `T-2026-10-18-android-chat-stage-determinism-018.md`）：上面那条**假设成立了一半**——
  `awaitingFirstRow` 确实在读回之后才置位，但两条红真正同源的不是「观察窗看不到一帧」，而是**阶段是第几次读的函数**：
  `startGenerationPolling()` 起的真实线程在 t=0 立刻排一次 `read()`，这条读能否被手工驱动的执行器捞到全看线程调度；
  捞到就补出 `SENDING` 帧（否则 `IDLE`），落在第二次 drain 里就把同一批终态行读两次、第一次消费掉停止意图（于是 `FAILED`）。
  修法不是放宽超时，而是把状态归位：标志移到 `send()` 里读之前、重复读挪到 200ms 之后、`CANCELED` 绑定到它解释的那条
  终态行 id 并在任何非终态读上释放（`retry`/`reset` 走 `replace(messageId, …)` 原地复用同一 id，因此这一条必须有判据）。
  修法要求的「同一批消息序列只能派生出一种阶段序列」已落成三条断言，两路变异各自验红。回读命令
  `./gradlew :app:testDebugUnitTest`（在 `android/` 下），读数从 846 例 / 2 失败变为 **848 例 / 0 失败 / 0 错误**，
  证据 `artifacts/evidence/T-2026-10-18-web-android-slice-018-d8-stage-determinism.txt`。
  **未闭合的一半**：在线真实发送一次的 SENDING→REASONING→ANSWERING 观感仍未实测（会消耗学习者 API 额度并写入真实
  主题数据，需授权后单独做），所以本条闭合的是「门禁可判」，不是「生成流已验证」。

### D9 「AGENTS.md 失败案例库」这个指针指向一个当前树里不存在的章节（2026-10-19 已按「改指活正文」闭合，见处置）

- 现象：`.codex/` 里有若干处正文把某条理由写成「见 AGENTS.md 失败案例库 …」，而 AGENTS.md 现在只在一处提到
  历史案例，且明确把归属地指给 `.codex/references/`（`AGENTS.md:65`：「`.codex/references/` 只保存迁移映射和历史案例」）。
  现行案例文件是 `.codex/references/regression-cases.md`，其开头还写着「历史参考与学习材料，不是新增门禁或当前运行契约；
  现行要求见[防回归规则](.codex/rules/regression.md)」。落点本身还不止一种写法：其中一处写的是
  「`.cursor/AGENTS.md` 失败案例库」，而 `.cursor/` 在当前树里也不承担规则职责（AGENTS.md 开头就说明了这点）。
- 后果：按指针去读的人（或 AI）在 AGENTS.md 里找不到那一节，于是一条**真实存在过的成因**变成无法回读的说法——
  与 D1 同源，只是这里过期的是指针而不是读数。它不影响判据红绿，所以没有任何门禁会替我报出来，只能靠盘点。
- 修法（不需要裁定，属措辞修正）：把这些指针的落点从「AGENTS.md 失败案例库」改成
  `.codex/references/regression-cases.md`（要写「现行要求」时改指 `.codex/rules/regression.md`），一次改完，
  不改任何判据逻辑。历史任务卡与各 D 条目正文里的旧说法**不回改**（那是当时的写法，回改等于改写历史捕获）。
- 代价：涉及脚本 docstring 与规则正文的行，会让 `--help` 式说明文字变化；因此这片必须与任何判据改动分开提交，
  或者与 B3（`run_all_guards`）合并——B3 本来就要动这些脚本的入口说明。
- 回读：`grep -rnI "AGENTS.md 失败案例" .codex/scripts .codex/skills .codex/rules`——这几处是**活指针**
  （脚本注释与技能正文里的「理由见 …」），命中清零即闭合；ROADMAP 各 D 条目正文与任务卡里的旧说法不计入，
  那是当时的写法。同时 `grep -n 失败案例 AGENTS.md` 应保持无命中（见 D5）。命中条数现场读，本条不抄值。
- 归属：登记于 2026-10-19（片 `T-2026-10-19-harness-guard-judgements-d4-d6-021.md` 的盘点副产品），不在该片实施。
- 处置（2026-10-19 闭合，随片 `T-2026-10-19-guard-runner-one-command-roster-023.md` 提交）：活指针四处改完，但**落点不是原修法写的
  `.codex/references/regression-cases.md`**——逐条回读后确认那几处所指的成因并不在那份历史案例里，把它们改指过去等于
  用一个新的空指针替换旧的空指针。实际落点改成「正文里现在真的有这段话的地方」：`check_roadmap_and_routing.py` 的改判说明
  指回 ROADMAP 的 D1 本体，`harness_check_lib.py` 的自检纪律写成「权威正文就是本文件，rules 与 references 不复述它」，
  `secret-scan/SKILL.md` 把「见失败案例库」整句删掉、只留下顺序要求本身（先吊销再改写历史）。判据逻辑一个字节没动。
- 回读结果：上面两条回读命令当前均为 0 命中（`.codex/scripts`、`.codex/skills`、`.codex/rules` 三处活指针清零，
  `AGENTS.md` 里 `失败案例` 也无命中，与 D5 一致）。命中条数仍按回读命令现场读，本条不代它保持有效。

### D10 androidTest 用裸字面量标注历史 schema 版本，使静态门禁在当前树恒红（2026-10-19 登记并闭合）

- 现象：`python -X utf8 .codex/scripts/dev/check_androidtest_schema_expectations.py` 在 2026-10-19 的树上直接
  `VERDICT: FAIL`，四条问题分别指向 `AnnotationDaoInstrumentedTest`（写 `4`）与 `AttachmentStorageInstrumentedTest`
  （写 `3`）各自的 `PRAGMA user_version` 建库语句与回读断言。
- 根因：V5 落地时新增这两支升级测试，把它们各自模拟的历史版本当**常量**抄进了测试，而不是从
  `TreeLearnSchema.FORWARD_MIGRATIONS` 派生。这与 T-2026-09-23-037 那次病因同形：`DATABASE_VERSION` 每往上跳一次，
  这类 fixture 就安静地变成「名字说升级、实际按当前版本建库」的测试，**而它照样打印通过**。
  同一棵树里 `ProviderModelDaoInstrumentedTest` 已用派生常量避开同一坑，所以修法不需要新发明。
- 后果（不修的话）：门禁本身是红的，跨不过去只会被绕开——要么有人给它加白名单名字（每加一个名字就少一个失败点，
  正是 D4 那族「仪器给自己开后门」），要么有人把它当噪音略过，而下一次 schema 升版真的不会有人报。
- 修法（不需要裁定，属既有先例的复用）：两处改用各类内私有的 `PREVIOUS_SCHEMA_VERSION`，表达式由
  `FORWARD_MIGRATIONS` 派生（AnnotationDao 取最后一条的 `fromVersion()`，AttachmentStorage 取倒数第二条的，
  即它所演示那一步的起点），复用守门白名单 `ALLOWED_USER_VERSION_EXPRESSIONS` 里已有的名字。
  代价是同名常量在不同类里各指一个版本，换来的是**判定集合一个字节都没放宽**。
- 处置（2026-10-19，片 `T-2026-10-19-androidtest-derived-schema-version-022.md`）：两处按上面落地，类级 javadoc 里
  「stamps it user_version = 4」同步改述为引用该常量；不动守门、不动白名单、不动 `TreeLearnSchema` 与 helper。
- 回读：`python -X utf8 .codex/scripts/dev/check_androidtest_schema_expectations.py`（判绿时仍打印
  `judged 19 PRAGMA user_version expectation(s)`，次数与注入态相同 → 绿不是少判换来的）、
  其 `--selftest`（13 例正反双向，含「裸字面量必须判红」两条），以及
  `android/gradlew.bat :app:compileDebugAndroidTestJavaWithJavac --offline`（证派生式在 androidTest 编译期可用）。
  证据 `artifacts/evidence/T-2026-10-19-androidtest-derived-schema-version-022.txt` 块 01–05。
- 未闭合的一半（写明，不含糊过去）：**没有在真机重跑这两支 instrumented 测试**。整包 `connectedDebugAndroidTest`
  会卸载应用并清掉学习者本机数据与 API Key，属不可回退的用户代价，须由用户在场时单独跑那两支。
  本片证明的是「表达式可用且由 schema 派生」，不证明「设备上的 onUpgrade 仍成立」——后者沿用 T-2026-09-23-083 的真机运行。
- 归属：登记并闭合于 2026-10-19（片 022），发现路径是 B3 把这条守门收进 `run_all_guards.py` core 档时的整跑读数——
  在此之前它的红只出现在单跑里，没有汇总面会把它和其他 30 条一起摆出来。
