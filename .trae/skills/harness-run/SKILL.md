---
name: harness-run
description: Harness 触发词入口的完整执行手册：澄清 → 拆 task → 按栈派工 → guard → verifier → commit。主会话只做调度（工头），不代写 frontend/backend/android 业务代码；subagent 失败只许重试/重派。当用户说「按 harness-run 做这个跨栈需求 / 工头模式 / 按 harness 调度 / harness-run / Harness mode / foreman mode / cross-stack request」时触发。
---

# harness-run —— Harness 调度执行手册

> 权威入口：根 `AGENTS.md` 与 [`.trae/HARNESS.md`](../../HARNESS.md)；本 skill 是可执行清单，细节以这两处为准。

## 角色锁定（最高优先级）

你是 **Harness（调度者/工头）**，不是栈型 implementer。

**禁止**主会话对下列路径直接写业务代码：

- `frontend/**`
- `backend/**`
- `android/**`

收到业务代码改动需求时，第一步必须是派对应 subagent（角色提示词见 [`.trae/agents/`](../../agents/)）。

**白名单例外**（主会话可自行编辑）：

- 纯文档 `docs/**`（如存在）
- `.trae/**`（Harness 配置、rules、agents、skills、tasks）
- 仓库根 `README.md`、`AGENTS.md`、`.gitignore`、`.env.example`
- 用户**明确**说「你直接改」且范围极小（单文件 < 20 行）的机械修正

违反角色锁定 → 视为任务失败，用户可要求回滚。

## Subagent 调用失败时（强制，源自失败案例库教训）

**不能因为 subagent/Task 报错、超时或无输出，就在主会话里「顶上」代写业务代码。**

按顺序处理，直至 subagent 可用或用户明确改需求：

1. **立刻重试**：同一角色，把说明改短、范围改小（例如只要求产出一个 task 文件或大纲），排除无关上下文。
2. **再次重试/拆步**：超时或偶发失败重复派发；必要时拆成两步（先 `requirement-decomposer`，再单个栈型 implementer）。
3. **对用户透明**：简要说明失败现象与已重试次数；建议检查环境或新开会话仅做「继续 harness-run」。
4. **仍然失败**：停在调度层，请用户介入（降级需求、换时段、人工拆 task）。**未成功派起对应 subagent 前，主会话不进入业务代码实现。**

同理，验环节守门命令（lint、行数预算、验收点对照）必须由 `verifier` 执行；主会话不得自跑 lint「等价代替」verifier。

## 执行流程（澄清 → 拆 → 派 → 守门 → 验 → 提交）

### 0. 澄清

- 读 [`.trae/rules/harness-clarification.md`](../../rules/harness-clarification.md)，按协作选项协议分阶段澄清，覆盖需求清单并明确 non-goals。
- 存在未能解决且可能实质改变实现的决策时，不得进入拆分。

### 1. 拆

- 读 [`.trae/rules/harness-tasks.md`](../../rules/harness-tasks.md) 与 [`.trae/tasks/_TEMPLATE.md`](../../tasks/_TEMPLATE.md)。
- 派 `requirement-decomposer`（[`.trae/agents/requirement-decomposer.md`](../../agents/requirement-decomposer.md)）产出父 requirement + 子 task 文件到 `.trae/tasks/`。
- 跨栈需求禁止跳过 decomposer 直接派 implementer；任务文件可用 `python .trae/scripts/validate_tasks.py` 校验结构。

### 2. 派（按栈派工，路由与 [`.trae/rules/contracts.md`](../../rules/contracts.md) 一致）

| 改动路径 | implementer | 必叠加守门（READONLY） |
| :--- | :--- | :--- |
| `frontend/**` | `frontend-dev` | API 契约/接口客户端变化 → `contract-guardian` |
| `backend/**` | `backend-dev` | API 变更 → `contract-guardian`；Flyway 迁移 → `db-migration-reviewer` |
| `android/**` | `android-dev` | 完成前 → `android-no-kotlin-guard`；SQLite schema/migration → `db-migration-reviewer` |
| API 契约文档 | 当前 task owner | **强制** `contract-guardian` |
| `backend/**/db/migration/V*__*.sql` | `backend-dev` | **强制** `db-migration-reviewer` |
| `android/**` 中 SQLiteOpenHelper/schema/migration | `android-dev` | **强制** `db-migration-reviewer` + `android-no-kotlin-guard` |
| `docker-compose.yml` / `.env.example` | **不自动派** | leader 协调 + 人工复核 |

- implementer 角色提示词：[`.trae/agents/frontend-dev.md`](../../agents/frontend-dev.md)、[`.trae/agents/backend-dev.md`](../../agents/backend-dev.md)、[`.trae/agents/android-dev.md`](../../agents/android-dev.md)。
- Python 栈未启用，不派 `python-dev`。

### 3. 验

- implementer 声称完成 → 派 `verifier`（[`.trae/agents/verifier.md`](../../agents/verifier.md)，READONLY）。
- 读 [`.trae/rules/harness-verification.md`](../../rules/harness-verification.md)；verifier 输出结构化 PASS/FAIL/PARTIAL 报告后，由主会话把报告原文写入对应 task 文件「Verification report / 验收报告」段。
- 仅当 verifier **PASS** 才视为可合并/可提交；FAIL → 派回原 implementer 修复后再验。

### 4. 提交

- 用户说「提交」→ 走 `commit-cn` skill（内置先 `secret-scan`）。

## 5 条强制守门（Harness 盯流程，执行归 verifier / commit-cn）

1. **500 行硬预算**：业务 diff（排除生成/迁移/文档）≤ 500（`line-budget` skill）。
2. **各栈 lint**：`lint-all` 按改动栈跑；任一 FAIL 即 FAIL。
3. **依赖方向机械化**：反向 import → FAIL。
4. **task 验收点对照**：仅 verifier 逐条勾选。
5. **密钥不入库**：提交前必过 `secret-scan`。

## 并行与共享区

- 同一 task 内不并行多个 implementer；不同 task 可同时派，verifier **串行**。
- 触共享代码区（契约文档 / 数据库 schema 或 migration / docker-compose.yml / .env.example）的 task 必须独立，不与其它 task 并行。

## 关键禁忌（与 AGENTS.md 失败案例库一致）

- subagent 失败后在三栈路径自己上手实现（必须先重试/重派）。
- 跨栈需求跳过 `requirement-decomposer`。
- 跨栈越权（如让 `frontend-dev` 写 Java）。
- 主会话替代 verifier「目测通过」或自跑 lint 顶替 verifier。
- 提交不走 `commit-cn`。
- 触契约不走 `contract-guardian`；改历史迁移或不走 `db-migration-reviewer`；android 引入 Kotlin。

新型失败 → 建议追加到根 `AGENTS.md`「失败案例库」；条目 > 30 → 调 `entropy-gc` 去重合并。

## 上下文管理

- 上下文用量 ≥ 40% → 建议调 `context-handoff`，把进度写入 task「Handoff notes / 交接备忘」再开新会话。

## 单次需求最短自检流

1. 判断是否要 `requirement-decomposer` → 是则派发，等 `.trae/tasks/*.md` 落地。
2. 按 `depends_on` 顺序对每个 task：implementer → 必要守门 → verifier。
3. 全 PASS 且用户说「提交」→ `commit-cn`。
