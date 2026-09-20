---
name: lint-all
description: 按改动栈跑对应检查（代码风格 / 依赖方向 / no-Kotlin）。前端 frontend/ 跑 pnpm lint，后端 backend/ 跑 mvnw spotless+checkstyle，Android android/ 跑 gradlew lint + no-kotlin-check.ps1。任一 FAIL 必须给「文件+行号+人话解释+改写命令」的修复指令。当实施者声称完成 task 前、verifier 首个守门、或用户说「跑一下 lint」时触发。
---

# lint-all —— 按改动栈跑检查并给修复指令

## 何时触发

- implementer 声称完成 task 前先自跑一次。
- verifier 验收时触发的第一个守门。
- 用户显式说「跑一下 lint」「lint 一下」「检查检查」。

## 第一步：看 git diff 命中哪些栈

```powershell
git diff --name-only HEAD
```

只跑命中前缀对应的命令，避免全量空跑。

## 命令矩阵（Windows PowerShell）

### frontend/ → 前端

```powershell
cd frontend
pnpm lint
```

- 通过：exit 0。已含 eslint 依赖方向规则（pages → layouts → components → hooks → store → api → platform → utils），反向 import 会报 `boundaries/element-types`。

### backend/ → 后端（Java）

```powershell
cd backend
.\mvnw.cmd spotless:check
.\mvnw.cmd checkstyle:check
```

> 若项目没有 `mvnw.cmd` 只有 `mvnw`，用 `.\mvnw.bat`；以实际文件为准。

- 通过：两条都 BUILD SUCCESS。`checkstyle:check` 含 ImportControl，强制 controller → service → repository → entity 分层，controller 不可直连 entity。

### android/ → Android

```powershell
cd android
.\gradlew.bat lint
powershell -NoProfile -File scripts\no-kotlin-check.ps1
```

> `scripts\no-kotlin-check.ps1` 需另建（本项目 android 目录，Java-only 禁 Kotlin）：建议实现为扫描 `git diff --name-only HEAD` 命中的 android 源文件扩展名，出现 `.kt` / `.kts` 即报 FAIL 并 exit 1。此脚本不在本技能内自动生成，由用户按上一条规范编写。

- 通过：两条都 exit 0 / BUILD SUCCESSFUL。

## 判定

- 所有命中栈全部 PASS → 整体 **PASS**。
- 任一栈 / 任一命令 FAIL → 整体 **FAIL**，必须走下方修复指令，不能只贴原始报错。

## FAIL 时修复指令（强制：必须给「怎么改」，不只报错）

```text
[lint-all FAIL]
栈: <stack>
工具: <tool>
报错摘要: <核心 1-2 行>

修复指令：
  - 文件: <path>:<line>
  - 原因: <人话解释，为什么违反该栈约定>
  - 操作: <具体命令 / 改法>

示例：
  - android/.../MainActivity.kt:10 新增了 Kotlin 文件，违反「Java-only 禁 Kotlin」
    操作：把该实现改回 Java 版；或先在 task 里申请例外
  - backend/.../UserController.java:24 import 了 entity.User，违反 ImportControl
    操作：让 service 返回 dto.UserVO，controller 只接受 dto
  - backend 报 spotless 格式不一致：先跑 .\mvnw.cmd spotless:apply 自动修复，再 spotless:check
  - frontend/src/utils/foo.ts:12 import 了 ../components/Bar，违反 boundaries/element-types
    操作：把该函数移到 components 层，或把被引用逻辑下沉到 utils
```

## 教学闭环

每条失败都要解释 **为什么违反该栈约定**，让下一次 implementer 能自己避免；新型错误建议用户追加到 `.trae/HARNESS.md`（或 AGENTS.md）失败案例库，形成反馈循环。