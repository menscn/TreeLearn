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

### A0. 正控自检（每次会话第一次扫描前跑一次）

先用一个**形状合法**的样本确认扫描器真的会命中——否则你无法区分「没有密钥」与「扫描器根本没在工作」：

```powershell
$probe = Join-Path $env:TEMP ("tl-secretctl-" + (Get-Date -Format 'HHmmssfff'))
New-Item -ItemType Directory -Path $probe | Out-Null
@('-----BEGIN RSA PRIVATE KEY-----',
  'MIIEowIBAAKCAQEAx7Vn0ZmXq1cQq5m3kQeY2Jm7pC4m8vT1s6rL0hN2bYq9wZ3dKfAqLmA0rPq1cQq8yF5nQe2vJ7uG3sT6xY9zB4cD1eF8gH5iJ2kL9mN6oP3qR0sT7uV4wX1yZ8aB5cD2eF3gH6iJ9kL0mN7oP4qR1sT8uV5wX2yZ9aB6cD3eF4gH7iJ0kL1mN8oP5qR2sT9uV6wX3yZ0aB7cD4eF5gH8iJ1kL2mN9oP6qR3sT0uV7wX4yZ1aB8cD5eF6gH9iJ2kL3mN0oP7qR4sT1uV8wX5yZ2aB9cD6eF7gH0iJ3kL4mN1oP8qR5sT2uV9wX6yZ3aB0cD7eF8gH1iJ4kL5mN2oP9qR6sT3uV0wX7yZ4aB1c',
  '-----END RSA PRIVATE KEY-----') | Set-Content -LiteralPath (Join-Path $probe 'control.pem') -Encoding ascii
gitleaks dir $probe --redact --no-banner
```

- exit **非 0**（`leaks found: 1`）→ 扫描器工作正常，继续模式 A。
- exit **0**（`no leaks found`）→ 扫描器在本机不工作，**必须退化为模式 B**，**不得**据此判 PASS。

**样本形状必须是完整 PEM 块**（头 + base64 body + END 行）。只写一行 `-----BEGIN RSA PRIVATE KEY-----` 是**无效正控**：规则本就不该命中它（实测 `no leaks found`、exit 0），你只会得到「工具坏了」的假结论——2026-09-22 真实发生过，见 `AGENTS.md` 失败案例库里「探针测的形状不是生产路径的形状」条目下的镜像形态。

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