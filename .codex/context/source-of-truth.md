# Treelearn 事实源全量清单（SoT inventory）

> 读取时机：改动任何「会被当成现状读」的正文之前（`AGENTS.md`、`.codex/**` 的入口/规则/技能、`Docs/spec/**`、`Docs/05-technical-design/**`、`Docs/04-ux-design/design-system/README.md`、`android/README.md`），以及把同一句话写到第二处之前。
> 本文件只写指针、锚点与检查方式；正文权威在被指向的那一处。清单与权威冲突时以权威那一处为准，并当场修本清单。
> 新增一条事实＝新增一个条目（六个字段各一次）。把权威正文抄进本文件会让 60 字抄检窗口转红，所以这里只有域名、指针与锚点。

## 条目形状与两类事实

- **声明型**：这条约束由人写定在「唯一权威」那一处。扫描面上别处再出现「锚点」认得出的形状，同一行必须带「归属标记」指向权威；不带标记就是另立权威 → FAIL。允许带标记的引用：纠正性引用（「已由某处收窄」）本身就是证据，把它判红只会逼人删证据来变绿。
- **生成型**：这个数由脚本产出。它在扫描面任何一处被写死，那处就已经是第二权威 → 要求零命中，并要求「漂移检查」指向一支带裸下限常量的守门；否则「以实测为准」又只是一句主张。
- 六个字段各出现一次；同一字段在同一个条目里写两遍，就是清单内部的第二处定义。
- 「唯一权威」不许落在历史面（`.codex/tasks/**`、`artifacts/**`、`.codex/references/**`、`Docs/intent/**`、`Docs/01-demand/**`、`Docs/03-prd-draft/**`、`Docs/06-delivery/**`）：那里的句子是记录，不该指导下一位实现者。
- 本清单自己不算权威：任何条目把「唯一权威」指向本文件，判 FAIL。

<!-- FIELDS: 类型 | 唯一权威 | 更新时机 | 漂移检查 | 锚点 | 归属标记 -->

## 覆盖面

每张表的数字都由守门现场复算条目数比对，改条目不改这张表就会红。

| 权威根 | 条目数 |
| :--- | ---: |
| `.codex/` | 2 |
| `.codex/agents/` | 1 |
| `.codex/context/` | 2 |
| `.codex/rules/` | 4 |
| `.codex/scripts/` | 4 |
| `AGENTS.md` | 1 |
| `Docs/` | 3 |

## 条目

### 流程入口唯一
- **类型**：声明型
- **唯一权威**：`.codex/README.md:3`
- **更新时机**：`.codex/` 的角色变化、或新增与之并列的流程入口目录时
- **漂移检查**：`python -X utf8 .codex/scripts/check_roadmap_and_routing.py`
- **锚点**：`唯一的 AI 研发流程入口`
- **归属标记**：`\.codex/README\.md|source-of-truth\.md`

### 任务状态唯一可写目录
- **类型**：声明型
- **唯一权威**：`AGENTS.md:34`
- **更新时机**：新增任务状态目录、或任务落点约定改变时
- **漂移检查**：`python -X utf8 .codex/scripts/validate_tasks.py`
- **锚点**：`唯一可写任务状态目录`
- **归属标记**：`AGENTS\.md`

### 同一事实只允许一处权威
- **类型**：声明型
- **唯一权威**：`.codex/context/project-brief.md:61`
- **更新时机**：新增一个权威面、或正要把同一约束写到第二处之前
- **漂移检查**：`python -X utf8 .codex/scripts/check_source_of_truth.py`
- **锚点**：`同一事实只允许一处权威|同一约束只保留一份权威正文`
- **归属标记**：`project-brief\.md|source-of-truth\.md`

### 当前态文档的证据方向
- **类型**：声明型
- **唯一权威**：`.codex/rules/documentation.md:40`
- **更新时机**：改「代码与文档一致性」判据、或新增一类当前态文档时
- **漂移检查**：`python -X utf8 .codex/scripts/check_source_of_truth.py`
- **锚点**：`不得把另一份文档当作唯一权威`
- **归属标记**：`documentation\.md`

### Java-only 约束
- **类型**：声明型
- **唯一权威**：`.codex/rules/android.md:8`
- **更新时机**：Android 语言栈、Kotlin 相关坐标策略或 no-kotlin 判据变化时
- **漂移检查**：`python -X utf8 .codex/scripts/run_all_guards.py --only no-kotlin`
- **锚点**：`^(?=.*(?:Kotlin|\.kt)).*(?:不写 Kotlin 源码|禁止 Kotlin 源码|do not introduce \.kt)`
- **归属标记**：`\.codex/rules/android\.md`

### 设计 token 权威链
- **类型**：声明型
- **唯一权威**：`Docs/04-ux-design/design-system/README.md:9`
- **更新时机**：新增一类设计数值消费者（新的 token 落点）、或改链的顺序时
- **漂移检查**：`python -X utf8 .codex/scripts/dev/check_design_tokens.py`
- **锚点**：`^(?=.*:root)(?=.*权威)(?=.*原型)`
- **归属标记**：`design-system/README\.md`

### 设计 token 个数
- **类型**：生成型
- **唯一权威**：`.codex/scripts/dev/check_design_tokens.py:42`
- **更新时机**：新增或退役 token 时由守门读数体现，散文里不写死
- **漂移检查**：`python -X utf8 .codex/scripts/dev/check_design_tokens.py`
- **锚点**：`^(?=.*\d+ 个 )(?=.*(?:token|CSS 变量|tl_))`
- **归属标记**：`不适用（生成型要求扫描面零命中）`

### 单任务生产代码规模口径
- **类型**：声明型
- **唯一权威**：`.codex/rules/harness-tasks.md:47`
- **更新时机**：改任务拆分尺度、或用户裁定新的规模例外口径时
- **漂移检查**：`python -X utf8 .codex/scripts/dev/count_production_lines.py`
- **锚点**：`^(?=.*(?:200|两百))(?=.*(?:500|五百|生产代码|production-code)).*(?:目标|预算|不超过|上限为|at most|sizing target)`
- **归属标记**：`harness-tasks\.md`

### 时间落库单位与时区口径
- **类型**：声明型
- **唯一权威**：`.codex/rules/sql.md:16`
- **更新时机**：新增持久化时间点、或改建表与迁移的时间口径时
- **漂移检查**：`python -X utf8 .codex/scripts/check_source_of_truth.py`
- **锚点**：`^(?=.*(?:CURRENT_TIMESTAMP|epoch milli))(?=.*(?:时区|本地|UTC))`
- **归属标记**：`rules/sql\.md`

### 领域术语索引
- **类型**：声明型
- **唯一权威**：`.codex/context/domain-glossary.md:3`
- **更新时机**：新增术语、或某条术语的定义与出处冲突被裁决时
- **漂移检查**：`python -X utf8 .codex/scripts/check_context_pack.py`
- **锚点**：`定义与出处冲突时以出处为准`
- **归属标记**：`domain-glossary\.md`

### 下一批工作的所在阶段
- **类型**：声明型
- **唯一权威**：`.codex/ROADMAP.md:22`
- **更新时机**：该阶段条目全部交付、或阶段划分被调整时
- **漂移检查**：`python -X utf8 .codex/scripts/check_roadmap_and_routing.py`
- **锚点**：`Phase B（下一批）`
- **归属标记**：`\.codex/ROADMAP\.md`

### 守门档位条数
- **类型**：生成型
- **唯一权威**：`.codex/scripts/run_all_guards.py:40`
- **更新时机**：注册表增删条目后不需要改任何散文，条数只由 `--list` 打印
- **漂移检查**：`python -X utf8 .codex/scripts/run_all_guards.py --list`
- **锚点**：`^(?=.*(?:core|precommit|合成注册表)).*(?:的 \d+ 条[。（]|\d+ 条用例)`
- **归属标记**：`不适用（生成型要求扫描面零命中）`

### 当前态面的活指针声称条数
- **类型**：生成型
- **唯一权威**：`.codex/scripts/check_pointer_liveness.py:59`
- **更新时机**：文档面增删带行号的引用后由守门读数体现，散文与卡片都不写死这个数
- **漂移检查**：`python -X utf8 .codex/scripts/check_pointer_liveness.py`
- **锚点**：`^(?=.*\d+)(?=.*(?:citations=|条活指针|条声称|活指针声称条数))`
- **归属标记**：`不适用（生成型要求扫描面零命中）`

### 交付指标报告的落点
- **类型**：声明型
- **唯一权威**：`.codex/scripts/metrics_report.py:5`
- **更新时机**：报告目录、文件名或生成命令改动时
- **漂移检查**：`python -X utf8 .codex/scripts/metrics_report.py`
- **锚点**：`artifacts/metrics/report\.md`
- **归属标记**：`metrics_report\.py`

### 默认不引入远程服务端
- **类型**：声明型
- **唯一权威**：`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md:77`
- **更新时机**：要引入远程账号、云同步、远程遥测或多实例服务之前
- **漂移检查**：`python -X utf8 .codex/scripts/dev/check_stack_facts.py`
- **锚点**：`不监听公网接口|不做远程部署`
- **归属标记**：`SPEC-2026-10-15-web-h5-android-parity`

### backend 仅监听本机环回
- **类型**：声明型
- **唯一权威**：`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md:77`
- **更新时机**：`backend/` 目录落地、或绑定地址与会话令牌口径变化时
- **漂移检查**：`python -X utf8 .codex/scripts/dev/check_stack_facts.py`
- **锚点**：`仅监听本机环回地址|仅绑定环回地址`
- **归属标记**：`SPEC-2026-10-15-web-h5-android-parity`

### 复核报告归档位置
- **类型**：声明型
- **唯一权威**：`.codex/agents/code-reviewer.md:6`
- **更新时机**：复核产物的命名、归档目录或「一次复核一个文件」口径改动时
- **漂移检查**：`python -X utf8 .codex/scripts/check_code_reviewer_agent.py`
- **锚点**：`Docs/reviews/\*\*`
- **归属标记**：`code-reviewer\.md`
