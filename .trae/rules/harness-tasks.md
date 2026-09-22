# Harness 任务与执行契约

读取时机：已授权的任务创建、导入、恢复、拆分或执行前。仅加载本任务命中的规则；正文约束沿用原适用范围，不因读取而进入 Harness。

## Task-state policy

- 用户选择 Harness、明确要求任务或其它已获准流程实际创建任务时，新任务与恢复任务的状态统一放在 `.trae/tasks/`（Task v2）。
- 直接执行不需要 Task 文件，缺文件不构成合规失败。

## Universal task contract

本契约适用于用户选择 Harness、明确要求任务或其它已获准流程实际创建任务产物时。直接执行没有任务文件也是合法的。

任何为本仓库产出的任务——无论由主 Agent、项目内技能、外部技能、插件、agent 还是其它编排工具产生——都必须写成 `.trae/tasks/` 下的 Markdown 任务文件，并符合 `.trae/tasks/_TEMPLATE.md`。Task v2 文件名用 `T-YYYY-MM-DD-<kebab-case-description>-NNN.md`；日期与序号必须与稳定 `id: T-YYYY-MM-DD-NNN` 一致。仅含日期和序号的文件名无效。必须保留模板各分区，用已确认内容或显式 `PENDING`/`UNKNOWN` 标记填充；不得改用私有任务格式。

至少必须保留 front matter 字段、需求协作记录、范围、文档影响记录、验收标准、实现笔记、验收报告、交接备忘。来自其它系统的任务必须先适配进该模板才能被接受、路由或执行。

Task v2 适用于每个新建任务和每个恢复执行的历史任务。没有 `template_version: 2` 的文件视为历史：可作历史读取，但必须在进入 `ready` 或 `in_progress` 前规范化为 v2。

## Task v2 state machine

合法 v2 状态：`clarifying`、`ready`、`in_progress`、`blocked`、`verifying`、`verified`、`canceled`。

```text
clarifying -> ready -> in_progress -> verifying -> verified
                           |              ^
                           v              |
                        blocked ----------+
clarifying | ready | in_progress -> canceled
```

- `clarifying` 要求 `requirements_confirmed: false`，不可执行。
- `ready`、`in_progress`、`blocked`、`verifying`、`verified` 要求 `requirements_confirmed: true`。
- `ready` 取代历史 `planned`；`verified` 是唯一正常完成态。
- `blocked` 在交接备忘中记录阻塞点、解除条件和下一步。
- 历史 `planned/done/completed/complete/implemented/accepted` 仅为只读兼容别名，在 v2 文件中无效。
- 父 `requirement` 任务保存完整协作记录；子任务用其描述性文件名引用 `parent_requirement`，在 `depends_on` 中引用依赖文件名，只保留与本任务相关的摘要。稳定任务 ID 不是文件引用。

## Task sizing and TDD contract

本节约束仅在用户选择 Harness 且创建/执行 Task v2 文件时生效。200/500 行阈值是 Harness 内部的任务规模控制，不是进入 Harness 的自动触发器。

任务拆分优先采用小的垂直切片而非宽泛的技术阶段。一个任务通常描述一个行为、一个窄契约或一个独立交付的文档/配置变更。"前端 + 后端 + 契约"不应仅因功能跨栈就合为一个任务；应拆成有显式依赖和独立负责人的有界任务，除非是刻意隔离的契约任务。

规模目标：被修改的生产代码行 ≤ 200。测试、文档、生成文件不计入该估算。**计数口径：非空非注释的生产代码行**——新增文件按全部非空非注释行，既有文件只按本次改动行；注释与空行不计入。物理行（含注释与空行）只作参考并列记录，不作为判定依据。超过 200 行是 verifier 警告；超过 500 行除非已记录用户批准的规模例外，否则为失败。即使低于行数上限，语义边界仍强制保留。

**口径已由用户于 2026-09-21 裁定为「非空非注释」，四个已完成任务因此一次性结清。** 裁定依据与实测复算见 `artifacts/evidence/sizing-adjudication-2026-09-21.txt`（该文件的比对表由脚本从命令捕获输出生成，不手抄任务正文）。同一任务不得用两套口径给出两个「是否越线」的结论；口径存疑时并列两个数并指明哪一个是判定值。

| 任务 ID | 非空非注释（判定值） | 物理行 | 判定 |
| :--- | ---: | ---: | :--- |
| `T-2026-09-20-model-immutable-answer-domain-003` | 741（14 文件） | 1301 | 超 500 硬上限 48% → **例外已批准** |
| `T-2026-09-20-sqlite-schema-dao-transactions-004` | 458 | 722（后因注释增写为 741） | 未超 500；超 200 软上限（警告） |
| `T-2026-09-21-generation-operation-prompt-anchoring-021` | 230（新增 227 + 改动 3） | 763 | 未超 500；超 200 软上限（警告） |
| `T-2026-09-21-sqlite-dao-topic-branch-message-022` | 386（DAO 349 + `:core` 37） | 600 | 未超 500；超 200 软上限（警告） |

记录例外的格式（缺项即无效）：批准日期、批准人、任务 ID、判定口径下的实测行数与超出比例、剩余风险。**例外必须由用户明确批准，实现者自行追认无效**。若批准发生在实现完成之后，属流程缺陷，必须在任务文件中如实记为「事后追认」，**不得写成「事先批准」**——例外成立的依据是用户的明确批准，而不是记录补得齐。例外按任务逐项记录，不因本次追认而自动适用于其它任务。

**T-003 的 741 不可按目录复算**：它认领的 `android/core/src/main/java/com/treelearn/core/domain/` 后来被 T-021（新增 3 个文件）与 T-022（改 `Branch`/`Message`）继续编辑，直接测目录得到的是并集（17 文件 / 1029 行），不是 T-003 的残余。引用该任务的规模时，数只能取自其第 4 轮实测值，不要重测目录。

代码与行为变更的执行记录必须展示 RED（先加测试，命令，非零退出码，观察到的失败，确认失败确由缺失行为导致）、GREEN（最小实现，同一目标测试命令，零退出码，通过输出，相关回归证据），以及可选 REFACTOR 证据。若任务无法遵循 TDD，必须在执行前说明例外、理由、用户批准和替代验证证据；**这里的「事后批准无效」仅限 TDD 例外**。规模例外不适用本句，按本文件上文的规模条款处理（必须有用户明确批准；若批准落在实现之后，如实记为「事后追认」即可成立）。两者互不矛盾；verifier 对任何例外都无追认权，只判定批准记录是否存在且由用户作出。

## 任务说明与状态目录

- 一旦决定创建任务，无论使用外部 skill、plugin、项目内技能、agent 还是其它编排能力，任务文件、说明、执行计划、交接和状态都必须统一放在 `.trae/tasks/` 下；不得写入其它任务目录。

## requirement-decomposer 执行与复核职责

- 不实现业务代码。只在已授权创建/拆分 Harness 任务后动作；否则返回 `HARNESS_NOT_SELECTED`，不创建文件。
- 应用上述任务契约、文件名、状态、规模与 TDD 要求。实现任务用 `tdd_mode: required`，除非此前有已批准的例外。
- 拆分前要求用户 Harness 选择证据和用户确认的父 `requirement` 任务；缺少实质信息时返回 `NEEDS_CLARIFICATION`（含当前理解、推荐、2–4 个可行选项、自由输入），不创建可执行子任务。
- 返回任务 ID、描述性文件名、负责人、依赖、估算生产行数和验收标准。