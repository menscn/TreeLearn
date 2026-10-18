import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
VALIDATOR = REPO_ROOT / ".codex" / "scripts" / "validate_tasks.py"


def task_text(**overrides: str) -> str:
    values = {
        "id": "T-2026-07-16-900",
        "title": "Validator fixture",
        "template_version": "2",
        "task_type": "requirement",
        "status": "clarifying",
        "parent_requirement": "null",
        "requirements_confirmed": "false",
        "generated_by": "leader",
        "owner": "harness-leader",
        "owner_lane": "harness",
        "stack": "harness",
        "estimated_production_lines": "0",
        "shared_code_touched": "false",
        "depends_on": "[]",
        "tdd_mode": "not-applicable",
    }
    values.update(overrides)
    header = "\n".join(f"{key}: {value}" for key, value in values.items())
    return f"""# Codex Harness Task: {values['title']}

{header}

## Requirement collaboration brief
- Parent summary: fixture

## Goal
One behavior.

## Scope
### In scope
- fixture
### Out of scope
- other

## Documentation impact record
- Changed runtime facts: fixture only
- Authoritative code/config/migration evidence: fixture
- Documentation surfaces checked: none
- Documentation files updated: none
- Producer/consumer/sibling/compatibility paths checked: none
- Superseded phrases/examples reverse-searched: none
- documentation impact: none reason and evidence: validator fixture has no runtime behavior
- Historical/PRD handling: none

## Acceptance criteria
- [ ] fixture passes

## TDD execution record
### RED — failing test first
- Test/scenario: validator behavior
- Command: python -m unittest
- Exit code: 1
- Observed failure: missing behavior
- Failure reason validated: true
### GREEN — minimum implementation
- Implementation: minimum
- Command: python -m unittest
- Exit code: 0
- Passing evidence: test passed
- Regression evidence: suite passed
### TDD or sizing exception — only with prior user approval
- Exception type: none
- Reason: none
- User approval: none
- Alternative verification: none

## Implementation notes
- fixture

## Verification report
- Verdict: PENDING

## Handoff notes
- Completed:
- In progress:
- Blocker:
- Unblock condition:
- Next steps:
- Watch-outs:
"""


class ValidateTasksCliTest(unittest.TestCase):
    def run_validator(
        self, files: dict[str, str], *, normalize_names: bool = True
    ) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = []
            for name, content in files.items():
                if normalize_names:
                    task_id = next(
                        (
                            line.split(":", 1)[1].strip()
                            for line in content.splitlines()
                            if line.startswith("id:")
                        ),
                        None,
                    )
                    if task_id:
                        date_part, number = task_id[2:].rsplit("-", 1)
                        name = f"T-{date_part}-validator-fixture-{number}.md"
                path = root / name
                path.write_text(content, encoding="utf-8")
                paths.append(str(path))
            return subprocess.run(
                [sys.executable, str(VALIDATOR), *paths],
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
                encoding="utf-8",
                check=False,
            )

    def test_accepts_clarifying_requirement(self) -> None:
        result = self.run_validator({"requirement.md": task_text()})
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("PASS", result.stdout)

    def test_rejects_simple_filename_without_description(self) -> None:
        result = self.run_validator(
            {"T-2026-07-16-900.md": task_text()}, normalize_names=False
        )
        self.assertEqual(1, result.returncode)
        self.assertIn("filename", result.stdout)

    def test_rejects_filename_date_or_number_mismatch_with_id(self) -> None:
        result = self.run_validator(
            {"T-2026-07-17-validator-fixture-901.md": task_text()},
            normalize_names=False,
        )
        self.assertEqual(1, result.returncode)
        self.assertIn("must match task id", result.stdout)

    def test_rejects_ready_when_requirements_are_unconfirmed(self) -> None:
        result = self.run_validator({"task.md": task_text(status="ready")})
        self.assertEqual(1, result.returncode)
        self.assertIn("requirements_confirmed", result.stdout)

    def test_accepts_confirmed_parent_and_child_reference(self) -> None:
        parent = task_text(
            id="T-2026-07-16-901", status="ready", requirements_confirmed="true"
        )
        child = task_text(
            id="T-2026-07-16-902",
            task_type="docs",
            status="ready",
            parent_requirement="T-2026-07-16-validator-fixture-901.md",
            requirements_confirmed="true",
            tdd_mode="approved-exception",
        ).replace("- Exception type: none", "- Exception type: tdd").replace(
            "- Reason: none", "- Reason: documentation-only"
        ).replace("- User approval: none", "- User approval: approved by user").replace(
            "- Alternative verification: none", "- Alternative verification: markdown checks"
        )
        result = self.run_validator({"parent.md": parent, "child.md": child})
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_rejects_child_of_unconfirmed_parent(self) -> None:
        parent = task_text(id="T-2026-07-16-903")
        child = task_text(
            id="T-2026-07-16-904",
            task_type="verification",
            status="ready",
            parent_requirement="T-2026-07-16-validator-fixture-903.md",
            requirements_confirmed="true",
        )
        result = self.run_validator({"parent.md": parent, "child.md": child})
        self.assertEqual(1, result.returncode)
        self.assertIn("confirmed parent requirement", result.stdout)

    def test_rejects_id_only_parent_reference(self) -> None:
        parent = task_text(
            id="T-2026-07-16-905", status="ready", requirements_confirmed="true"
        )
        child = task_text(
            id="T-2026-07-16-906",
            task_type="verification",
            status="ready",
            parent_requirement="T-2026-07-16-905",
            requirements_confirmed="true",
        )
        result = self.run_validator({"parent.md": parent, "child.md": child})
        self.assertEqual(1, result.returncode)
        self.assertIn("descriptive task filename", result.stdout)

    def test_rejects_invalid_owner_lane_and_stack(self) -> None:
        result = self.run_validator(
            {"task.md": task_text(owner_lane="bogus-lane", stack="bogus-stack")}
        )
        self.assertEqual(1, result.returncode)
        self.assertIn("owner_lane", result.stdout)
        self.assertIn("stack", result.stdout)

    def test_rejects_missing_dependency(self) -> None:
        result = self.run_validator(
            {
                "task.md": task_text(
                    depends_on="[T-2026-07-16-does-not-exist-999.md]"
                )
            }
        )
        self.assertEqual(1, result.returncode)
        self.assertIn("dependency not found", result.stdout)

    def test_rejects_verified_implementation_without_red_evidence(self) -> None:
        content = task_text(
            task_type="implementation",
            status="verified",
            requirements_confirmed="true",
            tdd_mode="required",
        ).replace("- Exit code: 1", "- Exit code:", 1)
        result = self.run_validator({"task.md": content})
        self.assertEqual(1, result.returncode)
        self.assertIn("RED", result.stdout)

    def test_rejects_different_red_and_green_commands(self) -> None:
        content = task_text(
            task_type="implementation",
            status="verified",
            requirements_confirmed="true",
            tdd_mode="required",
        ).replace(
            "- Command: python -m unittest\n- Exit code: 0",
            "- Command: python -m pytest\n- Exit code: 0",
        )
        result = self.run_validator({"task.md": content})
        self.assertEqual(1, result.returncode)
        self.assertIn("same targeted test command", result.stdout)

    def test_rejects_over_500_lines_without_sizing_approval(self) -> None:
        result = self.run_validator(
            {"task.md": task_text(estimated_production_lines="501")}
        )
        self.assertEqual(1, result.returncode)
        self.assertIn("500", result.stdout)

    def test_rejects_approved_exception_without_user_approval(self) -> None:
        content = task_text(
            task_type="docs",
            status="ready",
            requirements_confirmed="true",
            tdd_mode="approved-exception",
        ).replace("- Exception type: none", "- Exception type: tdd").replace(
            "- Reason: none", "- Reason: documentation-only"
        )
        result = self.run_validator({"task.md": content})
        self.assertEqual(1, result.returncode)
        self.assertIn("User approval", result.stdout)

    def test_warns_but_passes_legacy_task(self) -> None:
        result = self.run_validator(
            {"legacy.md": "# Legacy\n\nstatus: planned\n\n## Goal\nOld task\n"}
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("LEGACY", result.stdout)

    def test_rejects_resumed_legacy_task(self) -> None:
        result = self.run_validator(
            {
                "legacy.md": "# Legacy\n\nstatus: in_progress\n\n## Goal\nOld task\n"
            }
        )
        self.assertEqual(1, result.returncode)
        self.assertIn("migrate", result.stdout.lower())


if __name__ == "__main__":
    unittest.main()
