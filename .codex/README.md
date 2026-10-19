# Treelearn · Codex Harness 研发流程

> `.codex/` 是 Treelearn 唯一的 AI 研发流程入口。用户确认范围后，主 Agent 在范围内连续编排、实现、验证和修复；只有重大决策或真实阻塞才再次询问。

## 怎么用

1. 先读根 `AGENTS.md`，按路径和行为读取命中的规则。
2. 小改动直接执行；大型或跨栈需求在用户选择 Harness 后进入 `.codex/HARNESS.md`。
3. Harness 任务写入 `.codex/tasks/`，由对应 owner 实现，独立 verifier 只读验收。
4. 提交前整跑只读守门并留证据：`python -X utf8 .codex/scripts/run_all_guards.py --tier core`（每片都要跑），
   提交动作前再跑 `--tier precommit`；判定规则与「怎么加一条守门」见 `.codex/skills/guard-runner/SKILL.md`。
5. 任务、规则和脚本只记录可复核的命令、结果和缺口；不自动提交或推送。

## 目录结构

```text
.codex/
├── HARNESS.md             # Harness 编排入口
├── README.md              # 本文件
├── .codex/ROADMAP.md      # Treelearn 遗留流程路线
├── .codex/context/        # Plan 阶段背景包
├── .codex/templates/      # intent/spec 等产物模板
├── rules/                 # 专项规则正文（唯一权威）
├── agents/                # Codex TOML 角色与兼容说明
├── tasks/                 # Task v2 状态文件
├── skills/                # 项目技能与机械守门
├── scripts/               # 任务、设计和 Android/前端守门脚本
└── tests/                 # 流程工具自测
```

权威边界：产品事实在 `Docs/**`，意图在 `Docs/intent/**`，代码复核报告在 `Docs/reviews/**`，任务状态在 `.codex/tasks/**`，工程约束在 `.codex/rules/**`。源项目专属 HClaw、Core-Python、CSPfix 和 APK 中转流程不属于 Treelearn 当前运行面。
