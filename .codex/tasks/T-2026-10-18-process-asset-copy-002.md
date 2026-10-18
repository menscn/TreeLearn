# Treelearn Harness Task: 迁移通用流程资产

id: T-2026-10-18-002
title: 迁移通用流程资产
template_version: 2
task_type: config
status: verified
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 将 Treelearn 当前 Harness 通用能力迁移到 Treelearn，并以 `.codex` 为唯一入口。
- Problem/background: Treelearn 现有 `.codex` 流程需要被当前 Codex Harness 替换。
- Users/scenarios: Codex 在 Treelearn 中执行 Web 研发任务。
- Expected outcome: `.codex` 具备规则、agent、skill、任务模板和校验脚本。
- Core flow and reversibility: 复制筛选后的资产，删除 `.trae`，通过差异审查回溯。
- Inputs/outputs: source `.codex` → target `.codex`。
- State and exception handling: 排除历史任务与 Android/Java-only、APK、Core-Python 专属资产。
- Constraints and trade-offs: 不改业务代码、依赖和提交。
- AI recommendation and rationale: 复制通用资产，避免跨项目历史事实污染。
- User choice and custom additions: 用户已授权采用 Harness、删除 `.trae`、面向 Treelearn 多栈。
- AI-autonomous decisions: 资产筛选和复制方式。
- User-confirmation decisions: 上述范围已确认。
- Acceptance evidence: 关键文件存在且路径可解析。
- Facts: source `.codex` 为当前流程入口。
- Assumptions: 目标项目使用标准 Codex 根入口。
- Unknowns: 无影响本子任务执行的未知项。
- Risks: 旧流程已删除；当前入口引用已切换到 `.codex`，历史证据中的旧路径不作为运行入口。
- Deferred: 文档引用修复由子任务 003 处理。
- Non-goals: 业务代码和历史任务。
- Skipped non-critical questions and reason: 范围已明确。

## Goal

在 Treelearn 创建可运行的 `.codex` 流程资产集合。

## Scope

### In scope
- `.codex/HARNESS.md`、rules、agents、skills、scripts、tests、references、task template。
- 删除 `.trae/`。

### Out of scope
- 业务代码、依赖、历史 task 文件。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 流程入口改变，业务行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `.codex` 内部链接和根入口。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason, if applicable: 配置/文档资产变更。

## Documentation impact record

- Changed runtime facts: `.codex` 为流程入口。
- Authoritative code/config/migration evidence: `.codex` 文件集合。
- Documentation surfaces checked: `.codex` 内部链接。
- Documentation files updated: 根 `AGENTS.md` 在子任务 003 处理。
- Producer/consumer/sibling/compatibility paths checked: 任务模板、校验器、agent/skill 引用。
- Superseded phrases/examples reverse-searched: `.codex`、Java-only、Android release。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 历史任务不复制。

## Acceptance criteria

- [x] `.codex` 关键目录和入口文件存在。
- [x] `.trae/` 已删除。
- [x] source 历史 tasks、Android Java-only、APK relay 和 Core-Python 专属资产未进入目标。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A（配置迁移）。
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
- Exception type: configuration/documentation migration
- Reason: 本任务只修改流程资产和文档，不新增业务行为测试。
- User approval: 用户已明确选择 Harness，并授权迁移、适配和删除 `.trae/`。
- Alternative verification: 文件清单、路径扫描、UTF-8 检查、任务校验和独立 verifier。

## Implementation notes

- 保留 backend Java 普通 agent/rule，删除 Android Java-only 专属角色。

## Verification report

- Verdict: PASS
- Evidence: `python -X utf8 .codex/scripts/validate_tasks.py` → 7 Task v2 files, errors=0, warnings=0；`python -m unittest discover -s .codex/tests -p 'test_*.py'` → 15/15 OK；`check_roadmap_and_routing.py`、`check_intent_capturer_agent.py`、`checkers_selftest.py` → EXITCODE=0；`.trae/` 不存在，`.codex/` 关键入口、agents、skills、rules、scripts、tests 存在。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除 `.trae/` 是用户明确授权；历史 Task 不复制，Treelearn 专属 context、templates、脚本和 Android/UX guard 已迁入 `.codex/`。
- Gaps: `.codex/references/` 保留源项目历史案例，仅作历史参考，不进入运行路由。
- Risks: 工作树有并行业务改动，未将其归因于本迁移。

## Handoff notes

- Completed: `.codex` 唯一入口、Treelearn 规则/agent/skill/context/templates/scripts、任务契约和独立验证已完成；`.trae/` 已删除。
- In progress: 无。
- Blocker: None。
- Unblock condition: N/A。
- Next steps: 后续业务任务按根 `AGENTS.md` 和 `.codex/HARNESS.md` 执行；backend 落地后再补真实启动验证。
- Watch-outs: 不恢复 `.trae/`；按实际路径触发 Android Java-only、frontend、backend 和契约 guard；不要把历史 references 当当前事实。

