---
name: secret-scan
description: 提交前密钥扫描。优先用 gitleaks 扫暂存改动；未装 gitleaks 时退化为 Grep 扫描常见密钥模式（私钥、AK/SK、password 真值、JWT 等），任一命中即判 FAIL 并拒绝提交。当用户要提交 / 写 commit / 检查泄露，或 commit-cn 触发时，必须先执行本技能。
---

# secret-scan —— 密钥扫描（gitleaks / Grep 兜底）

## 何时触发

- `commit-cn` 提交前强制先跑本技能（不可跳步）。
- 用户说「扫一下密钥」「secret-scan」「检查泄露」「有没有密钥入库」。
- PR 合并前手动扫一次完整 history。

## 前置：检查 gitleaks 是否已装

```powershell
gitleaks version
```

未装 → 弹警告并给安装命令（二选一）：

```powershell
winget install gitleaks
# 或
scoop install gitleaks
```

> 不强制装完才能 commit；但 gitleaks 缺失时必须 **退化为下方「模式 B：Grep 兜底」**，不能直接跳过扫描或假装 PASS（密钥可能入库）。

## 模式 A：gitleaks（优先）

### A1. staged（commit 前 / 默认）

```powershell
gitleaks protect --staged --redact --verbose
```

- exit 0 → PASS。
- exit 非 0 → FAIL，把命中的「文件:行号 + 规则名 + 脱敏片段」贴给用户。

### A2. 全 history（PR 前 / 用户显式调）

```powershell
gitleaks detect --redact --verbose
```

## 模式 B：gitleaks 缺失 → Grep 兜底（只扫未提交改动）

```powershell
# 列出暂存 + 工作树未提交的改动文件
git diff --cached --name-only; git diff --name-only
```

对这些文件逐个跑 Grep，扫以下常见密钥模式（**任一命中即 FAIL**）：

- 私钥：`-----BEGIN.*PRIVATE KEY-----`
- 云服务 AK / SK：`(?i)(access[_]?key|secret[_]?key)\s*[:=]\s*["'][^"']{16,}["']`
- password 赋了真实值：`(?i)password\s*[:=]\s*["'][^"']+["']`（排除占位符如 `xxx`、`<your-pass>`、`changeme`、`test`）
- JWT：`eyJ[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}`
- 通用密钥/令牌：`(?i)(api[_-]?key|token|secret|client[_-]?secret|sk-)\s*[:=]\s*["'][A-Za-z0-9_+\/-]{20,}["']`

排除项（不判 FAIL）：`.env.example`、`*.md` 文档示例、明确占位的 mock 值（如 `sk-test-*`、`test`、`changeme`）。

## 判定

- 任一真实密钥命中 → 整体 **FAIL**，禁止提交。
- 全部干净 → **PASS**。

## FAIL 时修复指令模板

```text
[secret-scan FAIL]
命中 N 处疑似密钥泄露：
  - <file>:<line>  规则: <rule>  片段: <redacted>

修复指令：
  1. 把真实值移到 .env（已在 .gitignore），代码改为读环境变量（pydantic-settings / @Value / BuildConfig）。
  2. 若已 commit 但未 push：git reset HEAD~1 后重新 commit，不带敏感内容。
  3. 若已 push：立即吊销该凭据；再用 git filter-repo / BFG 清理 history。
  4. 重跑 secret-scan 直到 PASS 才能提交。
```

## 参考配置（可选）

仓库根可放 `.gitleaks.toml` 自定义规则；默认规则已覆盖 AWS / GitHub / OpenAI / Anthropic / DeepSeek / Stripe / 通用高熵字符串。