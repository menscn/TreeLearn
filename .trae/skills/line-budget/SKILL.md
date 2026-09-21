---
name: line-budget
description: 500 行硬预算检查。用 PowerShell 脚本 git diff --numstat HEAD 统计未提交业务变更行，排除生成 / 迁移 / 文档目录；>500 判 FAIL 并提示拆 task，>200 提示警告。当 task 声称完成、verifier 验收、或用「budget / 行数 / 预算」检查变更大小时触发。
---

# line-budget —— 500 行硬预算检查

## 何时触发

- implementer 完成 task 声称「done」前自查一次规模。
- verifier 验收守门（配合 lint 一起跑）。
- 用户说「check budget」「看看改动多大」「行数超了没」。

## 运行脚本

本技能自带的 PowerShell 脚本：`.\line-budget.ps1`（与本 SKILL.md 同目录）。

```powershell
cd D:\Project\Android\Treelearn   # 在仓库内任一位置均可，脚本自定位 git 根
powershell -NoProfile -ExecutionPolicy Bypass -File .trae\skills\line-budget\line-budget.ps1
```

可选：对照指定基点（默认 HEAD）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .trae\skills\line-budget\line-budget.ps1 origin/develop
```

可选：只统计某个子树（判断单个 task 的规模时很有用，否则全仓未提交改动会混在一起）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .trae\skills\line-budget\line-budget.ps1 HEAD android/core
```

> 脚本用 `git rev-parse --show-toplevel` 自定位仓库根，**不硬编码任何绝对路径**，可随技能目录整体搬运。

## 脚本自身的三个硬性前提（改脚本前必读）

这三点都来自实测踩坑，脚本内也各有一段注释；被破坏时脚本不是「结果不准」而是**完全不可用**。

1. **`line-budget.ps1` 必须保存为 UTF-8 with BOM。** Windows PowerShell 5.1 对无 BOM 的 `.ps1` 按系统 ANSI 代码页解码，中文串变乱码后直接报语法错误（如「表达式中缺少右")"」），脚本根本执行不了、连退出码都拿不到。
2. **所有 git 调用必须带 `-c core.quotepath=false`。** git 默认把含非 ASCII 的路径加双引号并做八进制转义（`"Docs/04-ux-design/\347\237\245\350\212\275...html"`），前导引号会让排除正则的 `^` 锚点失配，本该排除的文档目录被**算进业务变更行**（实测虚增 2088 行）。
3. **必须显式统计未跟踪文件。** `git diff` 只覆盖已跟踪路径，而「新 task 新建的文件」天然全是未跟踪的；只跑 `git diff` 会对最常见的变更形态**系统性失明**（实测出现「业务变更 2000 行却只扫描到 1 个文件」）。

## 判定标准

脚本输出 `业务变更行 = <已跟踪 added+deleted 与 未跟踪文件全部行>` 并退出：

- 业务变更行 **≤ 200** → **PASS**（绿）。
- **> 200** → 黄色 **WARN**：提示「变更偏大，建议自查是否可拆小」，但可提交。
- **> 500** → 红色 **FAIL**：变更超出 500 行硬预算，禁止直接落库，必须拆 task。

> 工具目录（.trae、.github）、纯文档（Docs/、*.md）、证据与素材（artifacts/、.superpowers/）、构建产物、缓存与依赖锁、二进制资源都会在脚本内被排除，不计入业务行。

**注意本脚本与 task 预算的口径差异**：本脚本统计的是「业务变更行」（含空行、注释；用第二参数收窄范围时也可能含测试），用于**提交前规模自检**；而 Task v2 的 200/500 行预算按 `.trae/rules/harness-tasks.md` 定义为「被修改的**生产代码行**，测试与文档不计入该估算」。两者不能互相替代——写进 task 文件的应是后者，跑本脚本时请用第二参数把范围收窄到对应生产代码目录，并在 task 文件里写明所用口径。

## 排除范围（写入脚本）

- 生成/产物目录：`build`、`out`、`dist`、`target`、`.gradle`、`.idea`、`node_modules`、`generated`。
- 迁移目录：`migrations`、`migration`。
- 文档：`docs`、`Docs`、`*.md`。
- 证据与素材：`artifacts`、`.superpowers`。
- 锁文件 / 压缩源码 / 二进制资源：`*.lock`、`*.min.js`、`*.min.css`、`*.map`、`png|jpg|jpeg|gif|webp`、`apk|jar|so|dex`。

## FAIL 时修复指令

```text
[line-budget FAIL]
业务变更行 = <N>（> 500）

修复指令：
  1. 把改动按逻辑拆成多个小 task，每个 ≤ 500 行、各自有独立验收点。
  2. 每个子 task 独立实现 + lint + 提交（commit-cn），不要在同一个大 diff 里塞全部逻辑。
  3. 若确有合理原因需一次性大改 → 先在 task 文件里申请超预算例外（写明理由与验收证据），未获批准前不许提交。
  4. 重跑 line-budget 确认落入 ≤ 500 后再提交。
```

## 判定汇总

- 任一命中栈超 500 → 整体 FAIL。
- 仅 200–500 的 WARN 不阻塞提交，但要口头提示。