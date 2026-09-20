---
name: context-handoff
description: 上下文接力。把当前进度写入所属 task 文件的「Handoff notes」段（进度 / 阻塞 / 下一步 / 注意事项），供开新会话从干净 context 续上。判断时机为上下文用量 ≥40%。当上下文用量偏高、用户说「交接一下 / 开新会话续上 / 存个进度」时触发。
---

# context-handoff —— 上下文接力（交接备忘）

## 何时触发

- **上下文用量 ≥ 40%**：主 Agent / 会话判断「这个上下文快不行了」，主动建议本技能。
- 用户说「交接一下」「存个进度」「开个新会话续上」「handoff」。
- 一个长 task 告一段落，需要保证下次能续上。

> 不要靠「压缩对话」硬撑到 80%，输出质量会下滑；提前在 40% 左右就交接。

## 判断时机（硬指标）

- 会话上下文 token 用量 ≥ 40% → 建议执行本技能。
- 或当前 task 已实现主体、仅剩验证/收尾，也可以交接（交接比丢弃强）。

## 操作步骤

1. 定位当前正在处理的 task：

   ```powershell
   Get-ChildItem -Path .\.trae\tasks\ -Filter "T-*.md"
   ```

2. 读取该 task 文件，目标段为 `## Handoff notes`（模板已含）：`Completed / In progress / Blocker / Unblock condition / Next steps / Watch-outs`，模板见 `.trae/tasks/_TEMPLATE.md`。

3. 把当前状态写入 `## Handoff notes`（**只写该段**，尊重 task 文件的既有结构）：

   ```markdown
   ## Handoff notes

   - Completed: <已完成的动作与证据/提交号>
   - In progress: <正在做到一半的东西，精确到文件与函数>
   - Blocker: <卡在哪、报什么错（贴关键报错原文）>
   - Unblock condition: <解除阻塞需要的条件：人 / 权限 / 外部依赖>
   - Next steps: <下一步按顺序要做的任务>
   - Watch-outs: <注意事项：哪个命令必须绕过 / 哪次试验废了别重试 / 哪些守门要点>
   ```

4. 写好并保存后，明确提示用户开新会话续上，并携带一句「从 task <id> 的 Handoff notes 续上」。
5. 新会话侧：先读 `.trae/tasks/<id>.md` 的 `## Handoff notes`，按 `Next steps` 顺序继续，先跑 `git status` 对齐工作树。

## 写作要点（交接质量决定续上质量）

- 写**结论**，不写过程流水账：已完成、未完成、卡点、下一步。
- 失败过的路径要留一行「别试这个」（避免新会话重踩坑）。
- 关键报错原文要贴，只留一行摘要不够。
- 优先保持紧凑；不相关的探索细节不写。

## Do / Don't

- 只更新目标 task 的 `## Handoff notes` 段，不改其它段落、不改代码。
- 不新建额外任务/文档文件（除非该 task 本来就没有文件且用户要求建）。
- 交接不代表提交完成——commit 仍按 commit-cn / 各守门流程走。