# Treelearn Harness Task: 研发流程迁移到 Codex Harness

id: T-2026-10-18-001
title: Treelearn 研发流程迁移到 Codex Harness
template_version: 2
task_type: requirement
status: verified
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: Treelearn 当前以 `.codex/` 为流程入口，流程副本与源项目当前 Harness 版本存在漂移，且用户希望在范围确认后由 AI 连续完成工作。
- Users/scenarios: Treelearn 多栈版本研发人员与 Codex；涉及前端 `frontend/`、规划中的本地 Web 后端 `backend/`、文档和流程资产。
- Expected outcome: `.codex/` 成为唯一研发流程入口，Harness、规则、agent、skill、任务契约和验证工具可在 Treelearn 中直接使用。
- Core flow and reversibility: 迁移流程资产 → 删除旧 `.trae/` → 适配 Treelearn 多栈 → 运行独立校验；业务代码不改，Git 可通过差异审查定位所有变更。
- Inputs/outputs: 输入为 Treelearn 当前 `.codex/` 与 Treelearn 当前仓库状态；输出为 Treelearn `.codex/`、根 `AGENTS.md`、必要的当前文档链接更新和验证结果。
- State and exception handling: 旧 `.trae/` 按用户明确要求删除；源项目历史任务、APK 中转、HClaw、Core-Python 和 CSPfix 不进入 Treelearn 运行面；Android Java-only 保留。
- Constraints and trade-offs: 保留 Web 后端 Java 规则，但保留 Android Java-only 约束；不改业务代码、依赖、提交或推送。
- AI recommendation and rationale: 使用 source `.codex` 的通用 Harness 能力，按 Treelearn Android、frontend、规划 backend 和现行规格做适配，避免复制 source 项目私有事实。
- User choice and custom additions: 用户选择“采用 Harness”；明确删除 `.trae/`；同意 `.codex` 唯一主入口；明确要求保留 Java-only 约束，项目包含 Android Java-only 客户端、frontend 与规划中的本机 backend。
- AI-autonomous decisions: 任务拆分、通用资产筛选、路径适配、验证命令和文档引用修复。
- User-confirmation decisions: 是否采用 Harness、是否删除 `.trae/`、是否保留 `.codex` 唯一入口与 Android Java-only，均已明确确认。
- Acceptance evidence: 任务校验通过；旧 `.trae/` 不存在；`.codex` 关键入口存在；流程文件无错误的 Treelearn 路径；Android、frontend 与规划 backend 路由可读。
- Facts: Treelearn 当前包含 `frontend/`、`android/`，Web 规格规划 `backend/`；当前工作树已有未提交业务和流程改动。
- Assumptions: 本次迁移只改变研发流程和当前流程文档入口，不改变 Android 业务实现；Web 后端按 `backend/` 目录路由。
- Unknowns: backend 目录当前是否已落地；部分历史文档仍可能保留 `.codex` 作为历史路径说明。
- Risks: 删除 `.trae/` 会删除其中未提交的旧流程文件；复制源规则可能携带旧项目路径；当前任务已做路径扫描并如实报告。
- Deferred: 不迁移 source `.codex/tasks/` 历史任务；不清理所有历史文档中的旧路径，只修复当前有效入口。
- Non-goals: 不改业务代码、依赖、数据库、API、构建产物、Android Java/Kotlin 实现或 Git 提交历史。
- Skipped non-critical questions and reason: 不逐项确认 skill 和规则，因为用户已授权采用源项目当前 Harness，并授权 AI 在确认范围内连续完成筛选和适配。

## Goal

将 Treelearn 的研发流程入口切换为适配 多栈版的 `.codex` Harness。

## Scope

### In scope
- 迁移通用 Harness 规则、agent、skill、任务模板和校验脚本。
- 删除 `.trae/` 并更新当前有效流程入口。
- 移除源项目专属 APK 中转、HClaw、Core-Python 和 CSPfix 流程；保留 Treelearn Android Java-only。
- 适配 `frontend/` 与 `backend/` Web 栈路由。

### Out of scope
- 业务代码、依赖、数据库、API 和构建产物。
- source 历史任务与 Treelearn 专属业务证据。
- Git commit、push、PR 或部署。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 仅改变 AI 研发流程入口；保留业务仓库内容与现有 Web 规格事实。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 根 `AGENTS.md`、`.codex/HARNESS.md`、`.codex/rules/*`、`.codex/agents/*`、`.codex/skills/*`。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: 迁移为配置、规则和文档资产，不执行业务运行时变更。

## Documentation impact record

- Changed runtime facts: 研发流程入口由 `.trae` 改为 `.codex`。
- Authoritative code/config/migration evidence: Treelearn 根 `AGENTS.md`、`.codex/`。
- Documentation surfaces checked: `README.md`、`README_EN.md`、Web 规格和当前流程入口引用。
- Documentation files updated: 子任务中记录。
- Producer/consumer/sibling/compatibility paths checked: 流程入口、任务校验器和 agent/skill 引用。
- Superseded phrases/examples reverse-searched: `.codex/` 当前入口引用、Java-only 与 APK 中转入口。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 历史文档不重写为当前事实；当前有效链接改为 `.codex`。

## Acceptance criteria

- [x] `.codex` 成为唯一流程入口，`AGENTS.md`、Harness、任务契约、agent、skill 和校验器可用。
- [x] `.trae/` 已按用户要求删除，`.codex/` 成为唯一入口，且未删除业务目录。
- [x] Treelearn 多栈流程按 android/** 变更要求 Android Java-only guard；frontend/backend 路由和验证命令与 Treelearn 当前规格一致。
- [x] 独立校验报告无关键失败，已知缺口如实列为 PARTIAL。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A；本任务是流程配置迁移，不存在业务行为测试。
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: 见子任务 002、003、004。
- Command: 见子任务验证记录。
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### REFACTOR — optional
- Cleanup: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 仅迁移规则、脚本和文档资产。
- User approval: 用户已确认 Harness 与迁移范围。
- Alternative verification: 任务契约校验、路径扫描、入口存在性和 Git 差异审查。

## Implementation notes

- 采用 `.codex` 为唯一运行入口。
- 保留 Android Java-only、frontend 和规划 backend 的 Treelearn 规则；排除源项目专属服务和历史任务。
- 不复制 source 历史任务，避免污染 Treelearn 任务状态。

## Verification report

- Verdict: PASS
- Evidence: 独立只读 verifier 与本地守门均通过：7 个 Task v2 errors=0/warnings=0，15 项流程单测 OK，完整守门自检 50/50 PASS，Android Java-only guard PASS，UTF-8/链接扫描通过，`.trae/` 删除且 `.codex/` 唯一入口可读。
- Changed/deleted assertion rationale and equivalent behavior coverage: 只迁移和适配研发流程资产；删除旧 `.trae/` 由用户明确授权，业务代码与并行改动未纳入本任务。
- Gaps: backend 仍为规划目录；历史 references 保留源项目案例但不作为当前契约。
- Risks: 当前工作树存在其他并行业务改动，后续提交前需按路径重新核对。

## Handoff notes

- Completed: `.codex` 唯一入口、Treelearn 规则/agent/skill/context/templates/scripts、任务契约和独立验证已完成；`.trae/` 已删除。
- In progress: 无。
- Blocker: None。
- Unblock condition: N/A。
- Next steps: 后续业务任务按根 `AGENTS.md` 和 `.codex/HARNESS.md` 执行；backend 落地后再补真实启动验证。
- Watch-outs: 不恢复 `.trae/`；按实际路径触发 Android Java-only、frontend、backend 和契约 guard；不要把历史 references 当当前事实。

