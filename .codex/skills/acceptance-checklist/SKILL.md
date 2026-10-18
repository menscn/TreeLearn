---
name: acceptance-checklist
description: 为 Treelearn 的里程碑和 Harness Task 生成可核验的验收清单。
---

# acceptance-checklist

用户要求验收清单或里程碑验收时使用；不因普通直接执行而强制创建 Task。

- 读取 `.codex/rules/regression.md`、命中的专项规则和当前 Task 的 Acceptance criteria。
- 每项记录入口、平台、环境、精确命令或操作、预期、实际结果和缺口。
- 分开单测、契约、启动、设备/浏览器和真实数据库证据；构建成功或测试数量不能替代行为证据。
- 已知错误为 FAIL，必要证据缺失为 PARTIAL/未验证，环境阻断记录 blocked。
- 不修改原 Task 的验收条件，不替用户虚构手工验收。
