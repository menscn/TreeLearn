---
name: entropy-gc
description: 熵管理——定期扫描仓库熵堆积：死 TODO/FIXME、.trae/tasks/ 状态不一致（伪完成/长期 blocked/重复 task）、AGENTS.md 失败案例库膨胀（>30 条去重合并）、文档死引用、过期文档。只输出清理报告，不自动删除，任何改动需用户确认。每周/迭代切换、用户说「系统在朽坏 / 清理一下 / gc」，或失败案例库 >30 条时触发。
---

# entropy-gc —— 熵管理（只出报告，不自动删）

## 何时触发

- 每周一次或迭代切换时主动跑。
- 用户说「系统在朽坏」「清理一下」「gc」「看看哪些可以删」。
- 根 `AGENTS.md` 失败案例库 > 30 条时强制跑（合并去重）。

## 扫描清单 + 输出报告（不自动删除，只产报告）

### 1. 死 TODO / FIXME / XXX

扫所有 Markdown 与源码中的 `TODO` / `FIXME` / `XXX`：

- 结合 git 历史找出创建超过 30 天仍存在的条目：

```powershell
git log --since="60.days.ago" --oneline -- "**/*.md"
```

- `.trae/tasks/*.md` 中 `status: in_progress` 但最近 7 天无关联 commit 的任务。

### 2. .trae/tasks/ 一致性

- `status: verified` 的 task：其 Acceptance criteria 在近期 commit 中找不到对应改动 → 警告「可能伪完成」。
- `status: blocked` 超过 14 天 → 建议用户：复活 / 拆分 / 关闭（canceled）。
- 标题或验收点几乎一致的重复 task → 合并建议。
- 结构性问题可借助 `python .trae/scripts/validate_tasks.py` 全目录扫描辅助定位。

### 3. AGENTS.md 失败案例库

- 数「失败案例库」区条目数量；> 30 → 输出「建议合并的相似条目对」。
- 同一规则以不同措辞反复出现 → 列出请用户人工合并。

### 4. 死引用

- 根 `README.md`、`docs/**/*.md`、`.trae/**/*.md` 中的相对路径链接：逐条 `Test-Path`（或等价检查），404 的列出。
- `.trae/agents/*.md` 与 `.trae/skills/*/SKILL.md` 内引用的规则/任务/脚本路径同样校验。

### 5. 过期文档

- 超过 90 天未改、且所指向路径已失效的文档。
- 与当前生产代码/配置事实不一致的版本号、命令、路径表述（只列疑点，不擅自改写历史文档；历史报告应加快照说明而非静默改写）。

## 输出格式

```text
[entropy-gc 报告 / YYYY-MM-DD]
死 TODO: <count>
  - <file>:<line>  内容: ...  存在天数: ...

.trae/tasks/ 异常:
  - T-... 已 verified 但找不到对应 commit（可能伪完成）
  - T-... 已 blocked N 天（建议 复活/拆分/关闭）

AGENTS.md 失败案例库: <count> 条（建议合并 N 对）
  - 条目 i 与 j 疑似重复

死引用: <count>
  - <file>:<line>  指向不存在的 <path>

过期文档: <count>
  - <file>  最近改动: <date>  疑点: ...

建议处理顺序:
  1. ...
  2. ...
（本 skill 不自动删除任何文件；以上处置均需用户确认后执行。）
```

## 不要做的事

- **不自动删除任何文件**——降熵必须人工拍板，避免误删。
- 不修改 task 状态——由 implementer / 主会话按流程改。
- 不把历史带日期报告静默改写为当前态；需要时加历史快照说明并链接当前权威。
