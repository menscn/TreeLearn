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

> 脚本用 `git rev-parse --show-toplevel` 自定位仓库根，**不硬编码任何绝对路径**，可随技能目录整体搬运。

## 判定标准

脚本输出 `业务变更行 = <added+deleted>` 并退出：

- 业务变更行 **≤ 200** → **PASS**（绿）。
- **> 200** → 黄色 **WARN**：提示「变更偏大，建议自查是否可拆小」，但可提交。
- **> 500** → 红色 **FAIL**：变更超出 500 行硬预算，禁止直接落库，必须拆 task。

> 不含新增/删除为 0 或检索无改动的目录；工具目录（.trae、.github）、纯文档（Docs/、*.md）、构建产物、依赖锁都会在脚本内被排除，不计入业务行。

## 排除范围（写入脚本）

- 生成/产物目录：`build`、`out`、`dist`、`target`、`.gradle`、`.idea`、`node_modules`、`generated`。
- 迁移目录：`migrations`、`migration`。
- 文档：`docs`、`Docs`、`*.md`。
- 锁文件 / 压缩源码：`*.lock`、`*.min.js`、`*.min.css`、`*.map`。

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