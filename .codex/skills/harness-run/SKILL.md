---
name: harness-run
description: Opt-in Codex-native Harness orchestration for large, high-risk, or multi-deliverable project work; explicit trigger phrases count as user approval to use Harness Task flow.
---

# harness-run - Codex-native Harness orchestration

After the user selects Harness, orchestrate clarification, task decomposition, stack-owner implementation and independent verification. Mode selection and exact trigger phrases are defined in [root AGENTS.md](../../../AGENTS.md#harness-触发词); do not enroll direct work merely because it crosses stacks or exceeds a line estimate.

## Progressive workflow
1. Read [Harness orchestration](../../HARNESS.md) after opt-in; follow its stage-specific links only when entering that stage.
2. Before clarification, read [collaboration protocol](../../rules/harness-clarification.md). Never treat unknowns as confirmed facts; return current understanding, recommendation, viable options and free-form input, then resolve material unknowns.
3. Before creating/importing/resuming/routing tasks, read [task contract](../../rules/harness-tasks.md) and the [template](../../tasks/_TEMPLATE.md). Normalize incomplete imported tasks and explicitly mark missing information before routing.
4. Read [owner/guard routing](../../rules/contracts.md#route-table) before dispatch; keep implementation in assigned lanes. Each owner loads only task-relevant root routes.
5. Before acceptance, read [verification](../../rules/harness-verification.md). The verifier must be independent of implementers; leader self-review or raw lint output cannot replace it.
6. Report changed files, commands/checks, PASS/FAIL/PARTIAL, blockers and remaining risks.

## References
Historical Cursor migration context is available [here](../../references/harness-migration-history.md) only when tracing migration history. Do not create task state in historical or plugin-owned directories.
