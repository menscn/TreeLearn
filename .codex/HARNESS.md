# Codex-native Harness for Treelearn

这是用户选择 Harness 后的编排入口。执行模式和跨场景规则由根 [`AGENTS.md`](../AGENTS.md) 路由；直接执行不需要读取本文件。

## Harness flow after opt-in

1. 需求澄清前读取 [协作协议](rules/harness-clarification.md)，完成需求对齐和确认后再拆分。
2. 创建、导入或恢复任务前读取 [Task v2 契约](rules/harness-tasks.md) 和 [模板](tasks/_TEMPLATE.md)。
3. 派工前读取 [负责人及专项 guard 路由](rules/contracts.md#route-table)，只加载实际使用的 agent；按根入口叠加命中的规则。影响面清查由 `codebase-researcher` 只读完成：结论逐条写成「命令 + `文件:行`」，查不到要显式写「未找到」，`实测` 与 `推测` 分开；产物只落在被分配任务卡的 `### Behavior impact` 四个字段里（`.codex/agents/codebase-researcher.md`）。
4. 实现保持在分配的 lane 内；主会话不越界修改其它 owner 的业务路径。执行证据写入当前 Task。行为不变前提下的收敛（消掉第二份实现、拆掉只剩一个实现的抽象层、删除无生产者代码）由 `code-simplifier` 做，判据是同一条测试命令改前改后都全绿，它只能删不能注释保留，记录只落在当前任务卡的 `### REFACTOR — optional` 小节（`.codex/agents/code-simplifier.md`）。
5. 验收前读取 [验收规则](rules/harness-verification.md)，由独立只读 verifier 复核任务、路径、契约、测试和证据。改动是否让 AI 产物变差由 `eval-keeper` 的评测集判：跑法只有 `python -X utf8 .codex/scripts/run_evals.py` 一条，基线是 `.codex/evals/cases/**/product.md` 的逐字快照而不是手抄，刷新快照后必须整轮重跑注入自检；该角色只交逐字读数，不宣布任务验收（`.codex/agents/eval-keeper.md`）。
6. 代码本身的质量由 `code-reviewer` 只读复核，维度、产物格式与归档位置以 `.codex/agents/code-reviewer.md` 和 `Docs/REVIEW.md` 为准，报告落在 `Docs/reviews/**`。两个结论不互相冒充：该角色不宣布任务验收，verifier 不替它评代码质量。

## Treelearn lanes

- `frontend/**` → `frontend-dev`
- `android/**` → `android-dev`，完成前叠加 `android-no-kotlin-guard`
- `backend/**` → `backend-dev`；目录落地后才执行实际 backend 构建/启动
- API、原生桥接、共享错误和跨端契约 → `contract-guardian`
- SQL、SQLite/Flyway schema → `db-migration-reviewer`
- 规则、任务、文档和流程资产 → `harness` / `docs`

## Non-goals

本 Harness 不引入源项目的 HClaw、Core-Python 业务事实、CSPfix 分支流程或 APK 中转签名流程。Treelearn 的 Android release 只按 `.codex/skills/release-android/SKILL.md` 处理。
