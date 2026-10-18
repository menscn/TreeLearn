# Treelearn Harness Task: 流程迁移独立验证

id: T-2026-10-18-004
title: 流程迁移独立验证
template_version: 2
task_type: verification
status: verified
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: leader
owner: verifier
owner_lane: verification
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-10-18-web-stack-adaptation-003.md]
tdd_mode: not-applicable

## Requirement collaboration brief

- Parent summary: 验证 Treelearn 已切换到 `.codex` 唯一流程入口。
- Problem/background: 迁移必须证明旧入口删除、目标入口完整、Web 规则可用。
- Users/scenarios: Codex 后续在 Treelearn 中读取和执行流程。
- Expected outcome: 独立报告 PASS/PARTIAL/FAIL 和剩余风险。
- Core flow and reversibility: 只读检查，不修改实现。
- Inputs/outputs: 目标仓库当前差异、任务文件、流程资产。
- State and exception handling: 历史文档中的旧路径按历史处理，不把它们误判为当前入口。
- Constraints and trade-offs: 只验证流程资产，不跑无关业务构建。
- AI recommendation and rationale: 使用任务校验器、路径扫描、UTF-8 和 Git 范围审查。
- User choice and custom additions: 用户要求 `.codex` 唯一入口并删除 `.trae`。
- AI-autonomous decisions: 具体扫描命令和证据判定。
- User-confirmation decisions: 已确认。
- Acceptance evidence: 校验器输出、目录存在性、关键关键词扫描、Git 状态。
- Facts: 目标仓库已有大量未提交业务文件。
- Assumptions: 迁移只改变流程资产。
- Unknowns: 历史文档残留旧链接数量。
- Risks: 未提交流程文件已被用户授权随 `.trae` 删除；并行业务改动保持原状。
- Deferred: 无。
- Non-goals: 不验证业务功能。
- Skipped non-critical questions and reason: 范围明确。

## Goal

独立确认流程迁移满足用户确定的边界。

## Scope

### In scope
- `.codex` 入口与任务契约。
- `.codex` 删除状态。
- Android/frontend/backend owner、skill、rule 适配，保留 Android Java-only。
- Git 差异范围。

### Out of scope
- 业务构建、运行时 UI、Android 验证。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 验证流程入口，不验证产品行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): AGENTS/HARNESS/rules/agents/skills。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason, if applicable: 只读治理验证。

## Documentation impact record

- Changed runtime facts: `.codex` 是唯一入口。
- Authoritative code/config/migration evidence: 目标仓库 `.codex` 与根 `AGENTS.md`。
- Documentation surfaces checked: README、Web 规格入口。
- Documentation files updated: 由验证报告列出。
- Producer/consumer/sibling/compatibility paths checked: 任务校验器和关键引用。
- Superseded phrases/examples reverse-searched: `.codex/` 当前引用、Java-only、APK relay。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 历史引用单独列为风险，不改写历史。

## Acceptance criteria

- [x] 任务校验器对迁移任务和依赖任务无错误。
- [x] `.trae/` 不存在，`.codex` 关键入口存在。
- [x] Android Java-only、frontend 和规划 backend 路由可读；APK 中转未进入当前入口。
- [x] 迁移相关差异仅覆盖流程/文档入口；并行业务改动已单列为未归因，不纳入本任务结论。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（只读验证任务）。
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
- Exception type: not-applicable
- Reason: 只读验证。
- User approval: Harness 与范围已确认。
- Alternative verification: 独立 verifier 报告。

## Implementation notes

- 验证器只读，不修改任务或流程资产。

## Verification report

- Verdict: PASS
- Evidence: 独立只读 verifier 复核当前工作树：`check_roadmap_and_routing.py`、`check_intent_capturer_agent.py`、`checkers_selftest.py`、`validate_tasks.py`、15 项 Task 单测、UTF-8/链接扫描均通过；`android/scripts/no-kotlin-check.ps1` 输出 `VERDICT PASS (13 OK)`；`.trae/` 和 `.cursor/` 不存在，`.codex/` 存在。
- Changed/deleted assertion rationale and equivalent behavior coverage: 流程迁移未修改 Android/frontend 业务逻辑；并行工作树业务改动保持原状并单列，不纳入本任务归因。
- Gaps: backend 目录尚未落地，因此只验证规划路由，未执行 backend 启动。
- Risks: `.codex/references/` 中仍有源项目历史 HClaw/Core-Python 文字，但根入口明确其为历史参考。

## Handoff notes

- Completed: `.codex` 唯一入口、Treelearn 规则/agent/skill/context/templates/scripts、任务契约和独立验证已完成；`.trae/` 已删除。
- In progress: 无。
- Blocker: None。
- Unblock condition: N/A。
- Next steps: 后续业务任务按根 `AGENTS.md` 和 `.codex/HARNESS.md` 执行；backend 落地后再补真实启动验证。
- Watch-outs: 不恢复 `.trae/`；按实际路径触发 Android Java-only、frontend、backend 和契约 guard；不要把历史 references 当当前事实。

