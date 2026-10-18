---
name: lint-all
description: 按 Treelearn 实际变更栈选择 lint、测试、Java-only 和设计守门。
---

# lint-all

先读取根 `AGENTS.md` 和命中规则，再按实际变更路径运行最小但完整的检查集合。不要把未启用的 backend 或 Python 服务当作已验证。

## 路径选择

- `android/**`：在 `android/` 使用项目 Gradle lint/test 入口，必要时运行 `android/scripts/no-kotlin-check.ps1`。
- `frontend/**`：在 `frontend/` 使用 `package.json` 中现有的 lint、类型检查、测试和 build 脚本，并运行 `.codex/scripts/dev/check_design_tokens.py`（命中设计 token 时）。
- `backend/**`：只有目录和入口实际存在时，使用项目自己的 Maven/Gradle 检查；规划中的目录不能被报告为已通过。
- `.codex/**`、`Docs/**`、任务和规则改动：运行任务校验、UTF-8/链接扫描和相关守门脚本，不执行无关业务构建。

## 证据要求

每条检查记录精确命令、环境、退出码和关键判据行。退出码为 0 但没有判据输出时，按未验证处理；缺少环境时记录 PARTIAL/blocked。lint 不替代契约、启动、设备交互或真实数据库验证。
