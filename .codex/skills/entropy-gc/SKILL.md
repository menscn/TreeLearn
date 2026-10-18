---
name: entropy-gc
description: Project Codex Harness skill migrated from `.cursor/skills/entropy-gc/SKILL.md`.
---

# entropy-gc - Codex Harness migration

## Codex adaptation
This is a project-local Codex skill migrated from Cursor. Prefer Codex-native tools/subagents where available, preserve safety constraints, and keep `.cursor` as reference material.

## Source
- Migrated from `.cursor/skills/entropy-gc/SKILL.md`.
- `.cursor` remains reference material; do not write new Codex Harness task state there; this `.codex/skills` file is the Codex runtime skill surface.

## Original skill content adapted for Codex

# entropy-gc —— 熵管理（L6 OpenAI 模式）

## 何时触发

- 每周一早或迭代切换时主动跑一次。
- 用户说"系统在朽坏"/"清理一下"/"gc"/"看看哪些可以删"。
- .codex/references/regression-cases.md 历史案例 > 30 条时强制跑（合并去重）。

## 扫描清单 + 输出报告（不做删除，只产报告）

### 1. 死 TODO

```powershell
git log --since="60.days.ago" --oneline -- "**/*.md"
```

扫所有 markdown 中 `TODO` / `FIXME` / `XXX`：

- 创建超过 30 天还在的 TODO。
- 状态文件 `.codex/tasks/*.md` 中 `status: in_progress` 但最近 7 天无 commit 关联的。

### 2. .codex/tasks/ 一致性

- Task v2 中 `status: verified` 的 task：检查其 `## Acceptance criteria` 与 `## Verification report` 是否有对应证据；找不到 → 警告"可能伪完成"。
- `status: blocked` 超过 14 天 → 建议用户：复活 / 拆分 / 关闭。
- 重复 task（标题或验收点几乎一致）→ 合并建议。
- 无 `template_version: 2` 的旧 task 使用 `planned/done/completed/complete/implemented/accepted` 只读兼容；若为 `in_progress`，报告“恢复执行前必须迁移 v2”。

### 3. .codex/references/regression-cases.md 历史案例

- 数历史案例表的数据行（不含表头与分隔行）；> 30 → 输出"建议合并的相似条目对"。
- 同一规则反复以不同措辞出现 → 列出来让用户人工合并。

### 4. 死引用

- README.md / docs/**/*.md 中的相对路径链接：跑一遍 `Test-Path`，404 的列出来。
- `.codex/agents/*.toml`、`.codex/rules/*.md` 与 `.codex/skills/*/SKILL.md` 内引用的文件路径同样校验。

### 5. 过期文档

- `Docs/` 中超过 90 天未改且当前已不指向有效路径的文件。
- 与 README §1.1 工具版本基线不一致的版本号（例：md 写 Java 17 但 README 是 Java 21）。

## 输出格式

```text
[entropy-gc 报告 / YYYY-MM-DD]
死 TODO: <count>
  - <file>:<line>  内容: ...  存在天数: ...

.codex/tasks/ 异常:
  - T-xxx 已 verified 但找不到对应 commit
  - T-yyy 已 blocked 21 天

.codex/references/regression-cases.md 历史案例: <count> 条（建议合并 N 对）
  - 条目 i 和 j 似乎重复

死引用: <count>
  - <file>:<line>  指向不存在的 <path>

过期文档: <count>
  - <file>  最近改动: <date>

建议处理顺序：
  1. ...
  2. ...
（本 skill 不自动删除任何文件，请用户人工确认后处理。）
```

## 不要做的事

- **不自动删除任何文件**——熵管理的关键是降熵，但要让人工拍板，避免误删。
- 不修改 task 状态——由 implementer 主动改。
