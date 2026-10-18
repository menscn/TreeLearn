from __future__ import annotations

import unittest
from pathlib import Path
from typing import Any

import wait_main_quality as gates


def check(
    name: str,
    *,
    status: str = "completed",
    conclusion: str = "success",
    check_id: int = 1,
) -> dict[str, Any]:
    return {
        "id": check_id,
        "name": name,
        "status": status,
        "conclusion": conclusion,
        "details_url": f"https://github.com/example/repo/actions/runs/77/job/{check_id}",
    }


def all_green() -> list[dict[str, Any]]:
    return [check(name, check_id=index) for index, name in enumerate(gates.EXPECTED_CHECKS, 1)]


class FakeClock:
    def __init__(self) -> None:
        self.value = 0.0

    def monotonic(self) -> float:
        return self.value

    def sleep(self, seconds: float) -> None:
        self.value += seconds


class WaitMainQualityTests(unittest.TestCase):
    def test_architecture_check_is_waited_and_failure_is_not_green(self) -> None:
        name = "Multi-device architecture boundaries"
        self.assertIn(name, gates.EXPECTED_CHECKS)
        checks = [item for item in all_green() if item["name"] != name]
        code, snapshot, _ = self.run_wait([{"check_runs": checks}], timeout=10)
        self.assertEqual(gates.BLOCKED, code)
        self.assertIn(name, snapshot.missing)
        for conclusion in ("failure", "cancelled"):
            with self.subTest(conclusion=conclusion):
                code, _, _ = self.run_wait(
                    [{"check_runs": checks + [check(name, conclusion=conclusion, check_id=99)]}]
                )
                self.assertEqual(gates.GATES_FAILED, code)
        # The quality gate, which must succeed, verifies whether the scope
        # actually allowed this shared job to skip.
        code, _, _ = self.run_wait(
            [{"check_runs": checks + [check(name, conclusion="skipped", check_id=99)]}]
        )
        self.assertEqual(gates.SUCCESS, code)

    def test_mobile_prototype_check_is_required(self) -> None:
        name = "Mobile prototype tests, Web and Android builds"
        self.assertIn(name, gates.EXPECTED_CHECKS)
        checks = [item for item in all_green() if item["name"] != name]
        code, snapshot, _ = self.run_wait([{"check_runs": checks}], timeout=10)
        self.assertEqual(gates.BLOCKED, code)
        self.assertIn(name, snapshot.missing)
        code, _, _ = self.run_wait(
            [{"check_runs": checks + [check(name, conclusion="failure", check_id=99)]}]
        )
        self.assertEqual(gates.GATES_FAILED, code)

    def test_mobile_workflow_checks_web_and_native_outputs_and_joins_gate(self) -> None:
        workflow = Path(__file__).resolve().parents[4] / ".github/workflows/quality.yml"
        content = workflow.read_text(encoding="utf-8")
        self.assertIn("    name: Mobile prototype tests, Web and Android builds", content)
        self.assertIn("pnpm build:android", content)
        self.assertIn("pnpm exec cap sync android", content)
        self.assertIn(":app:testDebugUnitTest :app:assembleDebug", content)
        needs = next(line for line in content.splitlines() if "needs: [changes, documentation" in line)
        self.assertIn("mobile", needs)

    def run_wait(
        self,
        payloads: list[dict[str, Any] | Exception],
        *,
        timeout: float = 30,
    ) -> tuple[int, gates.GateSnapshot | None, list[str]]:
        clock = FakeClock()
        outputs: list[str] = []
        calls = 0

        def fetch() -> dict[str, Any]:
            nonlocal calls
            item = payloads[min(calls, len(payloads) - 1)]
            calls += 1
            if isinstance(item, Exception):
                raise item
            return item

        code, snapshot = gates.wait_for_gates(
            fetch,
            timeout_seconds=timeout,
            poll_seconds=5,
            heartbeat_seconds=10,
            monotonic=clock.monotonic,
            sleep=clock.sleep,
            emit=outputs.append,
        )
        return code, snapshot, outputs

    def test_documentation_gate_missing_is_not_success(self) -> None:
        checks = [item for item in all_green() if item["name"] != "Documentation gate"]
        code, snapshot, _ = self.run_wait([{"check_runs": checks}], timeout=10)
        self.assertEqual(gates.BLOCKED, code)
        self.assertIn("Documentation gate", snapshot.missing)

    def test_all_scoped_checks_green_and_unrelated_check_is_ignored(self) -> None:
        payload = {"check_runs": all_green() + [check("Unrelated", check_id=99)]}
        code, snapshot, _ = self.run_wait([payload])
        self.assertEqual(gates.SUCCESS, code)
        self.assertIsNotNone(snapshot)
        self.assertEqual(set(gates.EXPECTED_CHECKS), set(snapshot.checks))

    def test_web_parser_check_is_required_and_failure_is_not_green(self) -> None:
        name = "Web parser checks, tests and build"
        self.assertIn(name, gates.EXPECTED_CHECKS)
        checks = [item for item in all_green() if item["name"] != name]
        code, snapshot, _ = self.run_wait([{"check_runs": checks}], timeout=10)
        self.assertEqual(gates.BLOCKED, code)
        self.assertIn(name, snapshot.missing)
        code, _, _ = self.run_wait(
            [{"check_runs": checks + [check(name, conclusion="failure", check_id=99)]}]
        )
        self.assertEqual(gates.GATES_FAILED, code)

    def test_web_parser_workflow_produces_the_expected_check_name(self) -> None:
        workflow = Path(__file__).resolve().parents[4] / ".github/workflows/quality.yml"
        self.assertIn("    name: Web parser checks, tests and build", workflow.read_text(encoding="utf-8"))

    def test_missing_check_appears_later(self) -> None:
        checks = all_green()
        code, _, _ = self.run_wait(
            [{"check_runs": checks[:-1]}, {"check_runs": checks}]
        )
        self.assertEqual(gates.SUCCESS, code)

    def test_in_progress_check_later_succeeds(self) -> None:
        running = all_green()
        running[0] = check(
            gates.EXPECTED_CHECKS[0],
            status="in_progress",
            conclusion="",
            check_id=1,
        )
        code, _, _ = self.run_wait(
            [{"check_runs": running}, {"check_runs": all_green()}]
        )
        self.assertEqual(gates.SUCCESS, code)

    def test_completed_failure_returns_gate_failure(self) -> None:
        checks = all_green()
        checks[2] = check(gates.EXPECTED_CHECKS[2], conclusion="failure", check_id=3)
        code, snapshot, _ = self.run_wait([{"check_runs": checks}])
        self.assertEqual(gates.GATES_FAILED, code)
        self.assertEqual("failure", snapshot.checks[gates.EXPECTED_CHECKS[2]].conclusion)

    def test_cancelled_module_or_skipped_gate_fails(self) -> None:
        for conclusion, name in (("cancelled", "Static analysis"), ("skipped", "Quality gate"), ("skipped", "Documentation gate"), ("failure", "Documentation gate")):
            with self.subTest(conclusion=conclusion):
                checks = all_green()
                index = gates.EXPECTED_CHECKS.index(name)
                checks[index] = check(name, conclusion=conclusion, check_id=index + 1)
                code, _, _ = self.run_wait([{"check_runs": checks}])
                self.assertEqual(gates.GATES_FAILED, code)

    def test_unrelated_module_can_be_skipped_when_gate_succeeds(self) -> None:
        checks = all_green()
        name = "Static analysis"
        index = gates.EXPECTED_CHECKS.index(name)
        checks[index] = check(name, conclusion="skipped", check_id=index + 1)
        code, snapshot, _ = self.run_wait([{"check_runs": checks}])
        self.assertEqual(gates.SUCCESS, code)
        self.assertEqual("skipped", snapshot.checks[name].conclusion)

    def test_duplicate_check_uses_highest_check_run_id(self) -> None:
        checks = all_green()
        duplicate_name = gates.EXPECTED_CHECKS[0]
        checks.append(check(duplicate_name, conclusion="failure", check_id=0))
        checks.append(check(duplicate_name, conclusion="success", check_id=100))
        code, snapshot, _ = self.run_wait([{"check_runs": checks}])
        self.assertEqual(gates.SUCCESS, code)
        self.assertEqual(100, snapshot.checks[duplicate_name].check_id)

    def test_only_checks_from_matching_main_push_runs_are_accepted(self) -> None:
        sha = "a" * 40
        workflow_payload = {
            "workflow_runs": [
                {"id": 77, "event": "push", "head_branch": "main", "head_sha": sha},
                {"id": 78, "event": "pull_request", "head_branch": "feature", "head_sha": sha},
                {"id": 79, "event": "push", "head_branch": "other", "head_sha": sha},
            ]
        }
        allowed = gates.main_push_run_ids(workflow_payload, sha)
        self.assertEqual({77}, allowed)
        push_check = check(gates.EXPECTED_CHECKS[0], check_id=1)
        pr_check = check(gates.EXPECTED_CHECKS[1], check_id=2)
        pr_check["details_url"] = "https://github.com/example/repo/actions/runs/78/job/2"
        filtered = gates.filter_checks_to_runs(
            {"check_runs": [push_check, pr_check]}, allowed
        )
        self.assertEqual([push_check], filtered["check_runs"])

    def test_three_consecutive_api_errors_are_blocked(self) -> None:
        error = gates.GateQueryError("offline")
        code, snapshot, outputs = self.run_wait([error, error, error])
        self.assertEqual(gates.BLOCKED, code)
        self.assertIsNone(snapshot)
        self.assertEqual(3, len(outputs))

    def test_missing_check_times_out(self) -> None:
        payload = {"check_runs": all_green()[:-1]}
        code, snapshot, outputs = self.run_wait([payload], timeout=10)
        self.assertEqual(gates.BLOCKED, code)
        self.assertIn(gates.EXPECTED_CHECKS[-1], snapshot.missing)
        self.assertTrue(any("timed out" in output for output in outputs))


if __name__ == "__main__":
    unittest.main()
