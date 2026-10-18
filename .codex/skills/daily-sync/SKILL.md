---
name: daily-sync
description: 根据 Treelearn 当前 Git 与 Task 状态生成开发同步记录。
---

# daily-sync

用户明确要求日同步、进展汇报或交班时使用。先读取 `git log`、工作树状态和 `.codex/tasks/`，再把已验证事实、未完成项、阻塞和下一步写入用户指定的当前文档或 Task Handoff。

- 不凭记忆填写数字、状态或计划；每个结论保留来源命令。
- 不改写历史日报、产品规格或已完成 Task 的验收结论。
- 没有现成日报文档时，直接在当前 Task 的 Handoff notes 中记录，不创建重复台账。
- 不自动删除 Task 文件、不提交、不推送；只有用户明确要求归档时才执行。
