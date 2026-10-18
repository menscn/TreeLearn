---
name: env-bootstrap
description: 初始化和核对 Treelearn 的本地 Android、frontend 与可选 backend 开发环境。
---

# env-bootstrap

只读取并核对项目现有入口，不自动安装依赖、不写入密钥、不启动远程服务。

- Android：核对 JDK、Android SDK、`android/local.properties` 和 Gradle wrapper。
- Frontend：核对 Node/pnpm 与 `frontend/package.json` 的脚本。
- Backend：只有 `backend/` 已存在并有明确入口时才核对其 Java 运行时；否则记录为规划中。
- 将缺失的前置条件、实际版本和可复现命令写入当前 Task 的 Handoff/Verification 区。
