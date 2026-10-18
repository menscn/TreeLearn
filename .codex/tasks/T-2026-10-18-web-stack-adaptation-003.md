# Treelearn Harness Task: Treelearn 多栈流程适配

id: T-2026-10-18-003
title: Treelearn 多栈流程适配
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-10-18-process-asset-copy-002.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 目标项目同时维护 Android Java-only 客户端、frontend，并规划本机 backend，保留 Android Java-only 流程。
- Problem/background: 源项目规则包含 Android、Core-Python 和 `backend/` 路径事实，需要适配 Treelearn 的 `frontend/` 与 `backend/`。
- Users/scenarios: Android、frontend 和规划中的本机 Java backend 研发。
- Expected outcome: Android 路径保留 Java-only guard；frontend/backend 路径按实际目录触发各自规则，不把 Android guard 错套到 Web 任务。
- Core flow and reversibility: 修改流程文档和入口路由，业务代码不动。
- Inputs/outputs: source 通用规则 + Treelearn Web 规格。
- State and exception handling: Android 任务触发 Java-only guard；frontend/backend 任务不触发 Android guard；backend 目录尚未落地时只保留规划路由。
- Constraints and trade-offs: 保留 Android Java-only、frontend 和规划 backend 规则；排除源项目 APK 中转和其它专属服务。
- AI recommendation and rationale: 以 `android/`、`frontend/`、`backend/` 为实际路径路由，避免无 owner 或错误 guard。
- User choice and custom additions: 用户明确要求适配 Treelearn 实际多栈并保留 Android Java-only。
- AI-autonomous decisions: 具体路径替换和历史入口处理。
- User-confirmation decisions: Treelearn 多栈方向与保留 Java-only 已确认。
- Acceptance evidence: `rg` 无错误的 Web 路由；Android Java-only owner/guard、frontend owner、backend owner 均可解析。
- Facts: README 与 Web parity 规格声明 `frontend/` 和规划中的 `backend/`，同时 Android 已实际落地。
- Assumptions: 后端继续使用 Java；Android 路径保留 Java-only，frontend/backend 不继承 Android 专属 guard。
- Unknowns: backend 当前实现进度。
- Risks: 历史文档仍可能保留旧项目事实；当前入口已按 Treelearn 事实适配。
- Deferred: 历史文档不全部重写。
- Non-goals: 不修改产品规格和业务代码。
- Skipped non-critical questions and reason: 用户已明确目标栈。

## Goal

把流程入口适配为 Treelearn 的 Android Java-only、frontend 和规划 backend 研发。

## Scope

### In scope
- 根 `AGENTS.md`、`.codex/HARNESS.md`、contracts、task template、lint/harness skill。
- `frontend/` 与 `backend/` owner 路由。

### Out of scope
- 业务代码和产品规格正文。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 仅调整研发规则路由。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 根入口、Web 规格、agent/skill 引用。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason, if applicable: 文档/配置变更。

## Documentation impact record

- Changed runtime facts: Treelearn 多栈流程入口和 owner 路由。
- Authoritative code/config/migration evidence: 根 `AGENTS.md`、`.codex`、Web 规格。
- Documentation surfaces checked: README 与当前 Web 规格中的流程链接。
- Documentation files updated: PENDING
- Producer/consumer/sibling/compatibility paths checked: frontend/backend route references。
- Superseded phrases/examples reverse-searched: `.codex/`、Android Java-only、APK relay。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 历史记录保留原文，当前入口修正。

## Acceptance criteria

- [x] 按实际路径触发 frontend/backend/android/contract/docs/harness/verification 相关流程。
- [x] 不存在要求 Treelearn 任务执行 Android Java-only 的入口规则。
- [x] 当前有效 README/规格链接指向 `.codex`。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（流程文档适配）。
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: documentation migration
- Reason: 本任务只调整 Treelearn 流程入口和规则路由，不新增业务行为。
- User approval: 用户已明确要求 `.codex` 唯一入口并删除 `.trae/`。
- Alternative verification: 路径与关键词扫描、入口存在性和独立 verifier。

## Implementation notes

- Java 后端规则保留为 backend 规则；backend 尚未落地时只保留规则和 owner，不声称已有服务。

## Verification report

- Verdict: PASS
- Evidence: `check_stack_facts.py --selftest`、`check_design_tokens.py --selftest`、`check_context_pack.py`、`check_intent_template.py`、`check_pm_discovery_skill.py`、`check_spec_author_agent.py`、`check_roadmap_and_routing.py` 均 EXITCODE=0；活动规则/agent/skill 链接扫描无缺失；根入口覆盖 Android、frontend、规划 backend、project-brief 和 ROADMAP。
- Changed/deleted assertion rationale and equivalent behavior coverage: 只改流程、文档入口和守门脚本；Android Java-only 保留，frontend/backend 路由按实际目录触发。
- Gaps: backend 目录尚未落地，未执行 backend 构建或启动检查。
- Risks: 历史文档可能保留旧项目事实，但不属于当前入口。

## Handoff notes

- Completed: `.codex` 唯一入口、Treelearn 规则/agent/skill/context/templates/scripts、任务契约和独立验证已完成；`.trae/` 已删除。
- In progress: 无。
- Blocker: None。
- Unblock condition: N/A。
- Next steps: 后续业务任务按根 `AGENTS.md` 和 `.codex/HARNESS.md` 执行；backend 落地后再补真实启动验证。
- Watch-outs: 不恢复 `.trae/`；按实际路径触发 Android Java-only、frontend、backend 和契约 guard；不要把历史 references 当当前事实。
