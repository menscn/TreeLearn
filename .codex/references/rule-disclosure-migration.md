# 项目规则渐进式披露迁移对照

日期：2026-09-08。本文是此次结构调整的历史验证记录，不是新增门禁；当前规则从 [根入口](../../AGENTS.md) 路由，不要求日常加载本记录。

## 迁移基线与边界

基线提交：`7b7843fa99527ce18028edb0ad9e4f7da4ecff92`。本次开始时 AGENTS/HARNESS/agent 正文与该提交一致；工作区已有业务代码及接口文档改动未纳入本次修改。
保留原约束强度、例外、审批和判定。只改变加载路径、合并重复正文；旧 HARNESS 标题保留兼容跳转。沿用原文语言，根路由和新增说明使用 UTF-8 中文。

## HARNESS 章节对照

| 原二级章节 | 权威位置 | 处理 |
|---|---|---|
| Exact triggers | [根入口](../../AGENTS.md) | 迁移正文；入口只保留跳转 |
| Entry decision | [根入口](../../AGENTS.md) | 迁移正文；入口只保留跳转 |
| Harness flow after opt-in | [Harness](../HARNESS.md)、[harness-clarification](../rules/harness-clarification.md)、[harness-tasks](../rules/harness-tasks.md)、[harness-verification](../rules/harness-verification.md) | 迁移正文；入口只保留跳转 |
| Regression prevention and behavior evidence | [regression](../rules/regression.md)、[历史案例](regression-cases.md) | 迁移正文；入口只保留跳转 |
| Route table | [contracts](../rules/contracts.md) | 迁移正文；入口只保留跳转 |
| Shared-code guards | [contracts](../rules/contracts.md) | 迁移正文；入口只保留跳转 |
| Code-document alignment guard | [documentation](../rules/documentation.md) | 迁移正文；入口只保留跳转 |
| Real MySQL execution guard | [sql](../rules/sql.md) | 迁移正文；入口只保留跳转 |
| Permanent table admission guard | [sql](../rules/sql.md) | 迁移正文；入口只保留跳转 |
| Module startup guard | [startup](../rules/startup.md) | 迁移正文；入口只保留跳转 |
| Regression lessons guard | [regression](../rules/regression.md) | 迁移正文；入口只保留跳转 |
| HClaw identity guard | [hclaw](../rules/hclaw.md) | 迁移正文；入口只保留跳转 |
| Cross-cutting telemetry guard | [observability](../rules/observability.md) | 迁移正文；入口只保留跳转 |
| Frontend user-copy i18n guard | [frontend](../rules/frontend.md) | 迁移正文；入口只保留跳转 |
| Task-state policy | [harness-tasks](../rules/harness-tasks.md) | 迁移正文；入口只保留跳转 |
| Verifier naming | [harness-verification](../rules/harness-verification.md) | 迁移正文；入口只保留跳转 |
| Source-to-target mapping: agents | [迁移历史](harness-migration-history.md) | 保留历史参考 |
| Source-to-target mapping: skills | [迁移历史](harness-migration-history.md) | 保留历史参考 |
| Non-goals for this migration | [迁移历史](harness-migration-history.md) | 保留历史参考 |

原 flow 的五个三级章节分别迁入任务契约（Universal task contract、Task v2 state machine、Task sizing and TDD contract）和需求协作（Requirement collaboration checklist、Collaborative options protocol），原锚点仍可访问。

## 根规则逐条对照

序号为原章节内条目顺序；标题或触发词块整体迁移。重复摘要归并到对应详细正文。

| 原章节 / 条目 | 权威位置 | 处理 |
|---|---|---|
| 第一入口 #1 | [根入口](../../AGENTS.md)、[harness-tasks](../rules/harness-tasks.md) | 等义迁移 / 合并重复摘要 |
| 第一入口 #2 | [根入口](../../AGENTS.md)、[harness-tasks](../rules/harness-tasks.md) | 等义迁移 / 合并重复摘要 |
| 第一入口 #3 | [根入口](../../AGENTS.md)、[harness-tasks](../rules/harness-tasks.md) | 等义迁移 / 合并重复摘要 |
| 第一入口 #4 | [根入口](../../AGENTS.md)、[harness-tasks](../rules/harness-tasks.md) | 等义迁移 / 合并重复摘要 |
| 第一入口 #5 | [根入口](../../AGENTS.md)、[harness-tasks](../rules/harness-tasks.md) | 等义迁移 / 合并重复摘要 |
| 第一入口 #6 | [根入口](../../AGENTS.md)、[harness-tasks](../rules/harness-tasks.md) | 等义迁移 / 合并重复摘要 |
| 执行入口判断 #1 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| 执行入口判断 #2 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| 执行入口判断 #3 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| 执行入口判断 #4 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| 执行入口判断 #5 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| Harness 触发词 #1 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| Harness 触发词 #2 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| Harness 触发词 #3 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| Harness 触发词 #4 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| Harness 触发词 #5 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| Harness 触发词 #6 | [根入口](../../AGENTS.md) | 等义迁移 / 合并重复摘要 |
| Harness 规则 #1 | [Harness](../HARNESS.md)、[harness-tasks](../rules/harness-tasks.md)、[harness-verification](../rules/harness-verification.md) | 等义迁移 / 合并重复摘要 |
| 防回归规则 #1 | [regression](../rules/regression.md) | 等义迁移 / 合并重复摘要 |
| 防回归规则 #2 | [regression](../rules/regression.md) | 等义迁移 / 合并重复摘要 |
| 前端双模式一致性规则 #1 | [frontend](../rules/frontend.md) | 等义迁移 / 合并重复摘要 |
| 前端双模式一致性规则 #2 | [frontend](../rules/frontend.md) | 等义迁移 / 合并重复摘要 |
| 前端双模式一致性规则 #3 | [frontend](../rules/frontend.md) | 等义迁移 / 合并重复摘要 |
| 前端双模式一致性规则 #4 | [frontend](../rules/frontend.md) | 等义迁移 / 合并重复摘要 |
| 前端双模式一致性规则 #5 | [frontend](../rules/frontend.md) | 等义迁移 / 合并重复摘要 |
| Frontend 用户文案 i18n 规则 #1 | [frontend](../rules/frontend.md) | 等义迁移 / 合并重复摘要 |
| Frontend 用户文案 i18n 规则 #2 | [frontend](../rules/frontend.md) | 等义迁移 / 合并重复摘要 |
| 代码—文档一致性规则 #1 | [documentation](../rules/documentation.md) | 等义迁移 / 合并重复摘要 |
| 代码—文档一致性规则 #2 | [documentation](../rules/documentation.md) | 等义迁移 / 合并重复摘要 |
| 代码—文档一致性规则 #3 | [documentation](../rules/documentation.md) | 等义迁移 / 合并重复摘要 |
| 代码—文档一致性规则 #4 | [documentation](../rules/documentation.md) | 等义迁移 / 合并重复摘要 |
| 代码—文档一致性规则 #5 | [documentation](../rules/documentation.md) | 等义迁移 / 合并重复摘要 |
| HClaw 用户身份边界 #1 | [hclaw](../rules/hclaw.md) | 等义迁移 / 合并重复摘要 |
| HClaw 用户身份边界 #2 | [hclaw](../rules/hclaw.md) | 等义迁移 / 合并重复摘要 |
| HClaw 用户身份边界 #3 | [hclaw](../rules/hclaw.md) | 等义迁移 / 合并重复摘要 |
| HClaw 用户身份边界 #4 | [hclaw](../rules/hclaw.md) | 等义迁移 / 合并重复摘要 |
| Java Worker Owner 标识规则 #1 | [shared-state](../rules/shared-state.md) | 等义迁移 / 合并重复摘要 |
| Java Worker Owner 标识规则 #2 | [shared-state](../rules/shared-state.md) | 等义迁移 / 合并重复摘要 |
| 安全边界 #1：新建永久数据库表前必须先提交“建表必要性证明… | [sql](../rules/sql.md) | 保留；摘要与正文分离 |
| 安全边界 #2：Harness 模式下，除非 Task 明确… | [Harness](../HARNESS.md) | 保留；摘要与正文分离 |
| 安全边界 #3：不要删除 `.cursor`；它仍作为参考材… | [根入口](../../AGENTS.md) | 保留；摘要与正文分离 |
| 安全边界 #4：未经明确要求并完成验证，不要新增依赖或自动提… | [根入口](../../AGENTS.md) | 保留；摘要与正文分离 |
| 安全边界 #5：修改 `frontend/`、`admin-… | [startup](../rules/startup.md) | 保留；摘要与正文分离 |
| 安全边界 #6：新增或修改任何 SQL 代码或 SQL 脚本… | [sql](../rules/sql.md) | 保留；摘要与正文分离 |
| 安全边界 #7：所有可横向扩容的服务必须保持无状态：不得把用… | [shared-state](../rules/shared-state.md) | 保留；摘要与正文分离 |
| 安全边界 #8：新增轮询、后台任务、SSE、WebSocke… | [shared-state](../rules/shared-state.md) | 保留；摘要与正文分离 |
| 安全边界 #9：允许进程内缓存的内容仅限配置、客户端连接、纯… | [shared-state](../rules/shared-state.md) | 保留；摘要与正文分离 |
| 安全边界 #10：前端展示错误时不得直接暴露后端原始异常、英文… | [frontend](../rules/frontend.md) | 保留；摘要与正文分离 |
| 安全边界 #11：写代码和文档时必须以 UTF-8 读写中文内… | [根入口](../../AGENTS.md) | 保留；摘要与正文分离 |
| 安全边界 #12：修改生产代码、接口契约、架构边界、业务流程、… | [documentation](../rules/documentation.md) | 保留；摘要与正文分离 |
| 安全边界 #13：修改 `frontend/`、`admin-… | [startup](../rules/startup.md) | 保留；摘要与正文分离 |
| 安全边界 #14：新增或改造处理流程时必须补充可排障日志：至少… | [observability](../rules/observability.md) | 保留；摘要与正文分离 |
| 安全边界 #15：根目录指导保持简短；详细行为放在 `.cod… | [根入口](../../AGENTS.md) | 保留；摘要与正文分离 |

特别核对：全部永久表事前明确确认、真实 MySQL、各模块实际启动、缺证据不报 PASS、HClaw 普通用户与管理员例外及 KB HMAC 域、credential_user_id 保存校验、跨实例状态、WorkerOwnerId、双模式、双语言基线只减、文档影响记录与专项 guard 均保留。
共享区“独立任务”限定在 Harness，依据原 Entry decision 的直接模式豁免；guard 并未豁免。`foreman mode` 已在旧 HARNESS/skill 中生效，本次仅补齐根摘要。

## Agent 与 skill 对照

所有 agent 的模型、推理等级、名称与描述保持不变；developer_instructions 保留角色入口，细则移到下面的唯一正文或已存在的同义正文。

| 原 agent | 原 rules 条目 | 权威位置 |
|---|---|---|
| android-dev | #1–#6 | [android](../rules/android.md) |
| android-no-kotlin-guard | #1–#4 | [android](../rules/android.md) |
| contract-guardian | #1–#8 | [contracts](../rules/contracts.md)、[regression](../rules/regression.md)、[documentation](../rules/documentation.md) |
| db-migration-reviewer | #1–#8 | [sql](../rules/sql.md) |
| frontend-dev | #1–#8 | [frontend](../rules/frontend.md)、[regression](../rules/regression.md) |
| java-dev | #1–#8 | [java](../rules/java.md)、[sql](../rules/sql.md)、[contracts](../rules/contracts.md) |
| python-dev | #1–#7 | [python](../rules/python.md) |
| requirement-decomposer | #1–#10 | [harness-tasks](../rules/harness-tasks.md)、[harness-clarification](../rules/harness-clarification.md) |
| verifier | #1–#18 | [harness-verification](../rules/harness-verification.md)、[harness-tasks](../rules/harness-tasks.md)、[regression](../rules/regression.md)、[documentation](../rules/documentation.md)、[sql](../rules/sql.md) |

- `harness-run` 只保留阶段流程，完整要求定向读取需求、任务与验收规则；独立 verifier 要求保留在验收正文。
- `acceptance-checklist`、`commit-cn`、`pr-prep`、Task 模板直接引用防回归正文。
- `release-diff-notes` 按文档/契约场景读取；不再无条件读 Harness。
- `context-handoff`、`daily-sync` 写任务状态前读取任务契约；修正被触及 skill 中原有的相对 docs 链接。
- `lint-all` 的新经验目标改为现行规则/历史案例；`entropy-gc` 对应改为新位置，按案例表数据行计数，保留原阈值和只读限制。

## 文档影响记录

改变的运行事实是项目规则的读取入口与角色/技能引用，不涉及应用 API、SQL、端口、启动命令或业务流程。
权威证据为根路由表、HARNESS 阶段路由、agent TOML 的 developer_instructions、相关 SKILL.md 与 Task 模板；上述 producer/consumer 在同一变更同步。
外部活动引用 `数据库表治理规范.md` 定向到 SQL 正文；`开发计划.md` 的旧 Cursor 工作流只加历史快照标识和当前入口，不重写历史计划、任务或报告。旧报告仍可经 HARNESS 兼容索引找到现行正文。
反向搜索检查 `HARNESS.md`、旧防回归锚点、无条件 `Follow ... HARNESS`、旧任务 `<id>.md` 入口和 `.cursor/AGENTS.md` 新增经验指令。余下 HARNESS 引用仅用于显式 Harness、条件入口或历史兼容；历史 `.cursor` 引用保留为参考。
本次不触及模块生产代码/启动配置和 SQL，因此无需应用启动或真实 MySQL；不修改 API 文档或路由索引，不触发 contract-guardian。

## 场景读取核对与大小

下表是人工按路由和调用语义核对的静态读取集合；不是独立 Codex 会话的实际工具读取轨迹，也不证明模型永不漏读。原基线只计根入口 + 无条件 HARNESS，不计旧 agent/skill 额外重复。

| 场景 | 根入口以外必读集合 | 正文总字节（UTF-8，LF） |
|---|---|---|
| 纯当前态文档修改 | documentation | 11,359 |
| 普通前端文案修改 | frontend, documentation, startup | 17,751 |
| 前端交互回归修复 | frontend, regression, documentation, startup | 22,778 |
| Java 内嵌 SQL 修复 | java, sql, regression, documentation, startup | 24,227 |
| Java/Core HClaw 异步生成流程改造 | java, python, hclaw, shared-state, contracts, regression, documentation, startup, observability | 30,243 |
| Web/Android 原生桥接修改 | frontend, android, contracts, regression, documentation, startup | 26,427 |
| Harness 文档任务从澄清到验收 | harness-clarification, harness-tasks, harness-verification, contracts, documentation；分阶段读取 HARNESS 和任务模板 | 27,819（不含按任务填写的模板） |

原固定读取量：49,198 字节。根入口由 79 行 / 13,463 字节变为 52 行 / 6,443 字节。跨栈复杂场景加载更多规则是预期行为，不能为了减少读取量漏掉约束。

反例核对：文案修改不加载 SQL/HClaw/任务细则；Java 内嵌 SQL 不因无 `.sql` 后缀漏掉 MySQL；纯文档不自动触发模块启动；原生桥接同时加载契约和 Android guard；普通工作不加载任务规则，进入下一 Harness 阶段再补读。

## 验证结果

- Task 校验：`python -m unittest discover -s .codex/tests -p test_validate_tasks.py`，15 项通过，模板结构和状态兼容保持不变。
- 修改的 9 个 skill：以 `python -X utf8` 运行 skill-creator `quick_validate.py`，全部通过。
- 9 个 agent TOML 可解析，非 instruction 字段与基线逐项一致。
- 41 个变更/新增文件通过严格 UTF-8 解码且无替换字符；296 个相对链接/锚点已检查，新规则与入口无失效链接，原 HARNESS 所有标题锚点均保留。旧开发计划存在 10 处既有失效链接（5 个唯一目标），基线同样存在；仅添加历史标识，不修复历史资料。字面量 `????` 仅作为原 skill 和本记录的禁止占位符示例保留。
- `git diff --check` 通过；未改变 Task 模板结构、应用启动配置或 SQL。静态检查不能代替未来真实任务对规则读取行为的观察。
- 额外 `.codex/tests` 全集：27 项中 26 项通过，1 项失败。`test_permissions_documents_deletion_and_errors_are_explicit` 要求已有工作区接口文档包含 `中文可行动提示`，当前文本未匹配；此测试及接口文档均未被本次修改。未宣称全仓测试通过。

## 后续修复：过时断言与历史失效链接（2026-09-08）

用户要求修复上述测试失败并标注历史失效链接。上节保留首次验证结果，本节记录修复后的状态。

- 原因：接口文档的资料勾选错误展示已明确“当前 locale 下可行动的本地化提示”，旧测试仍断言“中文可行动提示”。生产证据为 `frontend/src/api/documents.ts` 的 `documentsText` 与 `documentSelectionErrorMessage`，以及 `frontend/src/shared/i18n/locales/{zh-CN,en-US}/documents.json` 中 `api.selection`；四个资料勾选 API 共用错误适配。该测试验证的是文档契约，不是双模式运行时交互。
- 修改 `.codex/tests/test_document_selection_sync_contract.py`：将旧的中文限定断言更新为当前 locale、本地化和可行动要求，并把错误提示、安全日志及原始异常禁止展示的断言限定在资料勾选错误章节，防止被文档其它章节偶然满足。权限、删除、错误码与响应包络检查继续保留。
- 修改 `docs/02-计划/开发计划.md`：10 处失效链接（5 个唯一目标）均转为“历史引用：原路径，目标已不存在”，保留名称与路径，不创建占位文件或推测替代文档。
- 文档影响：本次不改变生产运行事实、API 契约或规则强度；仅修复文档校验的过时预期及历史引用展示。API 文档原文保持不变。旧断言反向搜索只保留在本历史记录；模块启动和 MySQL 验证不适用。
- RED：修复前资料勾选契约 4 项中 1 项失败；GREEN：`python -X utf8 -m unittest discover -s .codex/tests -p 'test_*.py'`，27 项全部通过。
- 反例：分别在内存副本中移除本地化提示、安全日志、禁止原始异常展示三条要求，更新后的测试均拒绝；没有修改实际契约文件。
- 历史计划的相对文件及锚点检查通过，UTF-8/替换字符和 `git diff --check` 通过。
