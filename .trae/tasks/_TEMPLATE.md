# Filename: T-YYYY-MM-DD-<kebab-case-description>-NNN.md
# Treelearn Harness Task: <title>

id: <T-YYYY-MM-DD-NNN>
title: <short title>
template_version: 2
task_type: <requirement|implementation|verification|docs|config>
status: clarifying
parent_requirement: <T-YYYY-MM-DD-parent-description-NNN.md|null>
requirements_confirmed: false
generated_by: <leader|internal-skill|plugin|external-skill|agent|other>
owner: <agent-name>
owner_lane: <frontend|backend|android|contract|docs|harness|verification>
stack: <frontend|backend|android|docs|harness>
estimated_production_lines: 0
shared_code_touched: false
depends_on: [<T-YYYY-MM-DD-dependency-description-NNN.md>]
tdd_mode: <required|approved-exception|not-applicable>

## Requirement collaboration brief

For a parent `requirement`, record the complete confirmed brief. For a child task, record `Parent summary` and only details relevant to this task.

- Problem/background:
- Users/scenarios:
- Expected outcome:
- Core flow and reversibility:
- Inputs/outputs:
- State and exception handling:
- Constraints and trade-offs:
- AI recommendation and rationale:
- User choice and custom additions:
- AI-autonomous decisions:
- User-confirmation decisions:
- Acceptance evidence:
- Facts:
- Assumptions:
- Unknowns:
- Risks:
- Deferred:
- Non-goals:
- Skipped non-critical questions and reason:

## Goal

<One independently verifiable behavior or deliverable.>

## Scope

### In scope
- <item>

### Out of scope
- <item>

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve:
- Producer/consumer/sibling entrypoints and platform branches (code evidence):
- Native bridge or independent-client compatibility/update requirements:
- Behavior-neutral non-applicability reason, if applicable:

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts:
- Authoritative code/config/migration evidence:
- Documentation surfaces checked:
- Documentation files updated:
- Producer/consumer/sibling/compatibility paths checked:
- Superseded phrases/examples reverse-searched:
- `documentation impact: none` reason and evidence, if applicable:
- Historical/PRD handling:

## Acceptance criteria

- [ ] <intended behavior: expected result and appropriate command or UI observation>
- [ ] <preserved behavior and adjacent failure scenario; reuse existing tests or explain non-applicability>
- [ ] <affected entrypoint/platform connections and contract compatibility; evidence must execute real branches>

## TDD execution record

### RED — failing test first
- Test/scenario:
- Command:
- Exit code:
- Observed failure:
- Failure reason validated:

### GREEN — minimum implementation
- Implementation:
- Command:
- Exit code:
- Passing evidence:
- Regression evidence:

### REFACTOR — optional
- Cleanup:
- Regression evidence:

### TDD or sizing exception — only with prior user approval
- Exception type:
- Reason:
- User approval:
- Alternative verification:

## Implementation notes

- <decisions, constraints, references>

## Verification report

- Verdict: PENDING
- Evidence: <scenario, environment, command/steps, expected and observed result; distinguish unit, contract, startup, and real interaction evidence>
- Changed/deleted assertion rationale and equivalent behavior coverage:
- Gaps:
- Risks:

## Handoff notes

- Completed:
- In progress:
- Blocker:
- Unblock condition:
- Next steps:
- Watch-outs: