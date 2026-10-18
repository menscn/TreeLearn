# Harness 任务与执行契约

读取时机：已授权的任务创建、导入、恢复、拆分或执行前。仅加载本任务命中的规则；正文约束沿用原适用范围，不因读取而进入 Harness。

## Task-state policy

- When Harness is selected or a task is explicitly requested, new and resumed Codex Harness task state lives under `.codex/tasks/` using Task v2.
- Direct execution does not require a Task v2 file, and the absence of one is not a compliance failure.
- Legacy tasks remain readable without migration; migration is mandatory only when execution resumes.
- the legacy Cursor task directory is historical Cursor task state and should not receive new actionable Codex Harness task writes unless the user explicitly asks for Cursor compatibility.

## Universal task contract

This contract applies when the user chooses Harness, explicitly requests a task, or another approved workflow actually creates a task artifact. Direct execution is valid without a task file.

Every task that is produced for this repository—whether produced by the leader, an internal project skill, a bundled or external skill, a plugin, an agent, or any other orchestration tool—must be written as a Markdown task file under `.codex/tasks/` and conform to `.codex/tasks/_TEMPLATE.md`. Task v2 filenames use `T-YYYY-MM-DD-<kebab-case-description>-NNN.md`; the date and sequence must match the stable `id: T-YYYY-MM-DD-NNN`. A filename containing only the date and sequence is invalid. The producer must preserve the template sections and fill them with confirmed content or an explicit `PENDING`/`UNKNOWN` marker; it must not substitute a private task format.

At minimum, generated tasks must retain the front matter fields, requirement collaboration brief, scope, documentation impact record, acceptance criteria, implementation notes, verification report, and handoff notes. A task from another system must be adapted into this template before it is accepted, routed, or executed. `.cursor/` and plugin-owned task directories are reference or source locations only, not actionable task-state locations.

Task v2 applies to every newly created task and every legacy task resumed for execution. A file without `template_version: 2` is legacy: it may be read as history, but it must be normalized to v2 before entering `ready` or `in_progress`. Do not bulk-rewrite dormant or completed legacy tasks.

## Task v2 state machine

Valid v2 states are `clarifying`, `ready`, `in_progress`, `blocked`, `verifying`, `verified`, and `canceled`.

```text
clarifying -> ready -> in_progress -> verifying -> verified
                           |              ^
                           v              |
                        blocked ----------+
clarifying | ready | in_progress -> canceled
```

- `clarifying` requires `requirements_confirmed: false`; it is not executable.
- `ready`, `in_progress`, `blocked`, `verifying`, and `verified` require `requirements_confirmed: true`.
- `ready` replaces legacy `planned`; `verified` is the only normal v2 completion state.
- `blocked` records the blocker, unblock condition, and next action in Handoff notes.
- Legacy `planned`, `done`, `completed`, `complete`, `implemented`, and `accepted` are read-only compatibility aliases and are invalid in v2 files.
- A parent `requirement` task stores the full collaboration brief. Child tasks reference its descriptive `.md` filename with `parent_requirement`, reference dependency filenames in `depends_on`, and keep only a task-relevant summary. Stable task IDs are not file references.

## Task sizing and TDD contract

This section applies only after Harness has been selected and Task v2 files are being created or executed. The 200/500-line thresholds are task-sizing controls inside Harness, not automatic triggers for entering Harness.

Task decomposition must prefer small vertical slices over broad technical phases. A task should normally describe one behavior, one narrow contract, or one independently deliverable documentation/configuration change. “Frontend + backend + core” is not a valid single task merely because the feature spans stacks; create bounded tasks with explicit dependencies and separate owners, except for a deliberately isolated contract task.

The sizing target is at most 200 changed production-code lines. Tests, documentation, and generated files do not count toward this estimate. More than 200 lines is a verifier warning; more than 500 lines is a failure unless the task records a user-approved sizing exception. Semantic boundaries remain mandatory even below the line limit.

For code and behavior changes, the task execution record must show RED (test added first, command, non-zero exit code, observed failure, and confirmation that the failure is caused by the missing behavior), GREEN (minimum implementation, the same targeted test command, zero exit code, passing output, and relevant regression evidence), and optional REFACTOR evidence. If a task cannot follow TDD, it must state the exception, reason, user approval, and alternative verification evidence before execution; post-hoc approval is invalid.

## 任务说明与状态目录

- 一旦决定创建任务，不论任务使用外部 skill、plugin、项目内 skill、agent 或其它任务编排能力，任务文件、任务说明、执行计划、交接说明和状态记录都必须统一放在 `.codex/tasks/` 下；不得写入 `.cursor` 或其它任务目录，除非用户明确要求兼容旧系统。

## requirement-decomposer 执行与复核职责

- Do not implement business code. Act only after authorized Harness/task creation; otherwise return `HARNESS_NOT_SELECTED` without creating files.
- Apply the task contract, filename, state, sizing and TDD requirements above. Implementation tasks use `tdd_mode: required` unless a prior approved exception applies.
- Require evidence of the user's Harness choice and a user-confirmed parent `requirement` task before creating child tasks. If material information is missing, return `NEEDS_CLARIFICATION` with current understanding, recommendation, 2–4 viable options, and free-form input; do not create executable children. Read [collaboration protocol](harness-clarification.md) for this stage.
- Return task IDs, descriptive filenames, owners, dependencies, estimated production lines, and acceptance criteria.
