---
name: secret-scan
description: Project Codex Harness skill migrated from `.cursor/skills/secret-scan/SKILL.md`.
---

# secret-scan - Codex Harness migration

## Codex adaptation
Use `gitleaks` when available. Refuse to proceed with commit guidance if a secret is found. Never print full secret values; redact them.

## Source
- Migrated from `.cursor/skills/secret-scan/SKILL.md`.
- `.cursor` remains reference material; this `.codex/skills` file is the Codex runtime skill surface.

## Original skill content adapted for Codex

# secret-scan —— 密钥扫描（gitleaks）

## 何时触发

- `commit-cn` skill 执行前自动调（强制）。
- 用户显式说"扫一下密钥"/"secret-scan"/"检查泄露"。
- PR 合并前手动跑一次完整 history 扫描。

## 前置：检查 gitleaks 是否安装

```powershell
gitleaks version
```

未装就给安装命令并提示用户安装后再来：

```powershell
# Windows 任选一种：
scoop install gitleaks
# 或
winget install gitleaks
```

> 不强制安装才能 commit（避免开发卡死），但如果 gitleaks 缺失 → **必须**给用户弹明显警告：『密钥扫描跳过，请尽快装 gitleaks，否则 .env / API Key 可能误入库』。

## 扫描命令

### 1. staged 模式（commit 前 / 默认）

```powershell
gitleaks protect --staged --redact --verbose
```

- exit 0 → PASS。
- exit 非 0 → 直接拒绝，把命中的文件 + 行号 + 规则名贴给用户。

### 2. 全 history 模式（PR 前 / 显式调）

```powershell
gitleaks detect --redact --verbose
```

## 失败时的修复指令模板

```text
[secret-scan FAIL]
命中 N 处疑似密钥泄露：
  - <file>:<line>  规则: <rule-id>  片段: <redacted>
  - ...

修复指令：
  1. 把真实值移到 .env（已在 .gitignore），代码里改读 process.env / pydantic-settings / @Value。
  2. 如已 commit 但未 push：git reset HEAD~1，重新 commit 不带敏感内容。
  3. 如已 push：立即吊销该凭据 + 用 git filter-repo / BFG 清 history（参考 .cursor/AGENTS.md 失败案例库）。
  4. 重跑 secret-scan 直到 PASS 才能 commit。
```

## 配置（可选）

仓库根可放 `.gitleaks.toml` 自定义规则；当前用 gitleaks 默认规则已覆盖：AWS / GitHub / OpenAI / Anthropic / DeepSeek / Stripe / 通用 high-entropy 字符串。

