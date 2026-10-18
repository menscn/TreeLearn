# Harness 验收

读取时机：Harness 验收或用户明确要求 verifier 时。仅加载本任务命中的规则；正文约束沿用原适用范围，不因读取而进入 Harness。

## Verifier naming

- **project Harness verifier** means `.codex/agents/verifier.toml`, which applies this repo's task-file, stack, lint, guard, and diff-budget rules.
- **global OMX verifier** remains available for generic completion-evidence checks. When running Harness mode in this repo, prefer the project Harness verifier for task acceptance.

## verifier 执行与复核职责

- READONLY: refuse code and task edits. The verifier must be independent from implementers; leader self-review or raw lint output cannot replace Harness verification.
- Apply Harness task compliance only when Harness was selected, a task was explicitly requested, or the user independently requested this verifier. Direct execution without Task v2 is valid and must not fail for task absence.
- When a task is in scope, read [task contract](harness-tasks.md), run `.codex/scripts/validate_tasks.py` for the target task and its parent/dependencies, and verify filenames, state/confirmation, required sections, dependencies, scope, sizing and TDD evidence. Dormant legacy tasks receive warnings, not failures; resumed legacy/private/foreign tasks must be normalized. Enforce prior approval of exceptions and reject post-hoc approval.
- Read only the root routes matched by the actual change. For behavior evidence use [regression](regression.md); for code or current documentation use [documentation alignment](documentation.md); for changed SQL use [SQL](sql.md); for production/build/startup changes use [startup](startup.md); load the other matched identity, frontend, state and telemetry rules as applicable.
- For applicable hotspots, module/responsibility changes, shared business rules, state boundaries and compatibility retirement, apply [maintenance and change-cost governance](maintainability.md), including its review questions, benefit evidence and FAIL/PARTIAL criteria. Numeric gate success alone does not prove governance benefit; ordinary business changes may document a reason to defer governance under that rule. Keep the existing verifier activation conditions.
- Apply each loaded rule's exact evidence and FAIL/PARTIAL/blocked criteria. Verify acceptance criteria independently, and never infer completion from test counts, build success or unsupported `documentation impact: none`.
- Check relevant lint/test/build evidence and forbidden path constraints. Do not replace contract, migration or Android Kotlin guards; use [guard routing](contracts.md) when those surfaces are involved.
- Report PASS, FAIL, or PARTIAL with files reviewed, validation evidence, blockers and remaining risks.

- Apply the executable documentation gate in [documentation alignment](documentation.md): require current blob-bound impact evidence and selected contract checks. Do not duplicate its schema here or infer semantic correctness from a machine PASS.
