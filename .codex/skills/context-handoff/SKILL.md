---
name: context-handoff
description: Project Codex Harness skill migrated from `.cursor/skills/context-handoff/SKILL.md`.
---

# context-handoff - Codex Harness migration

## Codex adaptation
Before creating, resuming or updating task/handoff state, read [task contract](../../rules/harness-tasks.md); this does not authorize creating a task for direct execution.
Future Codex Harness handoff state writes to descriptively named Task v2 files under `.codex/tasks/`. the legacy Cursor task directory may be mentioned only as historical source/reference material.

## Source
- Migrated from `.cursor/skills/context-handoff/SKILL.md`.
- `.cursor` remains reference material; this `.codex/skills` file is the Codex runtime skill surface.

## Original skill content adapted for Codex

# context-handoff —— 上下文接力（L4 Anthropic Context Resets 模式）

## 何时触发

- 主 Agent 自感上下文用量 ≥ 40%（无 token API → 凭"对话长度 + 工具调用次数"估算）。
- 一条线索越查越深、越追越散，输出质量明显下滑（典型 context anxiety 表现）。
- 用户显式说"接力一下"/"新开会话"/"context 满了"/"context handoff"。

## 流程

1. 按 task `id` 找到当前正在执行的 `.codex/tasks/T-YYYY-MM-DD-任务描述-NNN.md`。
2. 把当前进度写进 Task v2 的 `## Handoff notes` 区（覆盖 / 更新，不要追加废话）：

   ```markdown
   ## Handoff notes

   - Completed:
     - <具体改动 1，含文件路径 + 函数名>
     - <具体改动 2>
   - In progress:
     - <正在写的文件 + 已写到哪一步 + 卡在什么>
   - Blocker:
     - <当前阻塞；无则写 none>
   - Unblock condition:
     - <解除阻塞所需条件>
   - Next steps:
     - 第一步读：<file:line>
     - 第二步跑：<command>
   - Watch-outs:
     - 注意陷阱：<…>
   ```

3. 仅对 `status: in_progress` 或 `status: blocked` 的 Task v2 执行 handoff。进行中保持 `in_progress`；临时阻塞改为 `blocked`，并填写 Blocker、Unblock condition 和 Next steps。旧 task 必须先迁移到 v2。

4. 不要 commit；告诉用户：

   ```
   交接已写入 .codex/tasks/T-... 的 "Handoff notes" 区。
   建议新开会话后第一句话：
     『继续 task T-...，先读 .codex/tasks/T-....md，然后从"下一步建议"开始。』
   ```

## 不要做的事

- 不在 Handoff notes 里贴大段代码（占体积没价值，新会话直接读源码）。
- 不靠"压缩对话"硬撑——这是反模式，输出会更糟。
- 不在 `verified` 或 `canceled` 的 task 上做 handoff（终态任务不需要接力）。
