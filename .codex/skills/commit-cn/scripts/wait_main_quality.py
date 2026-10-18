#!/usr/bin/env python3
"""Wait for the scoped GitHub quality gate after a push to main."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from typing import Any, Callable, Iterable, Mapping, Sequence


SUCCESS = 0
GATES_FAILED = 1
BLOCKED = 2

EXPECTED_CHECKS: tuple[str, ...] = (
    "Change scope",
    "Documentation gate",
    "Multi-device architecture boundaries",
    "Java format, lint, tests and package",
    "Frontend lint, tests and build",
    "Admin lint, tests and build",
    "Android Java-only, i18n and telemetry guards",
    "Static analysis",
    "pytest (Python 3.12)",
    "Web parser checks, tests and build",
    "Mobile prototype tests, Web and Android builds",
    "Quality gate",
)
REQUIRED_SUCCESS = {"Change scope", "Documentation gate", "Quality gate"}

RUN_ID_PATTERN = re.compile(r"/actions/runs/(?P<run_id>\d+)(?:/|$)")


class GateQueryError(RuntimeError):
    """Raised when GitHub check runs cannot be queried."""


def print_flush(message: str) -> None:
    print(message, flush=True)


@dataclass(frozen=True)
class CheckState:
    name: str
    status: str
    conclusion: str
    details_url: str
    check_id: int

    @property
    def terminal(self) -> bool:
        return self.status == "completed"

    @property
    def successful(self) -> bool:
        return self.terminal and self.conclusion == "success"


@dataclass(frozen=True)
class GateSnapshot:
    checks: Mapping[str, CheckState]

    @property
    def missing(self) -> tuple[str, ...]:
        return tuple(name for name in EXPECTED_CHECKS if name not in self.checks)

    @property
    def all_terminal(self) -> bool:
        return not self.missing and all(
            self.checks[name].terminal for name in EXPECTED_CHECKS
        )

    @property
    def all_successful(self) -> bool:
        return not self.missing and all(
            self.checks[name].terminal
            and self.checks[name].conclusion
            in ({"success"} if name in REQUIRED_SUCCESS else {"success", "skipped"})
            for name in EXPECTED_CHECKS
        )

    @property
    def signature(self) -> tuple[tuple[str, str, str, str], ...]:
        return tuple(
            (
                name,
                self.checks[name].status if name in self.checks else "missing",
                self.checks[name].conclusion if name in self.checks else "",
                self.checks[name].details_url if name in self.checks else "",
            )
            for name in EXPECTED_CHECKS
        )


def _as_int(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return -1


def snapshot_from_payload(payload: Mapping[str, Any]) -> GateSnapshot:
    raw_runs = payload.get("check_runs")
    if not isinstance(raw_runs, list):
        raise GateQueryError("GitHub 响应缺少 check_runs 数组")

    selected: dict[str, CheckState] = {}
    expected = set(EXPECTED_CHECKS)
    for raw in raw_runs:
        if not isinstance(raw, Mapping):
            continue
        name = str(raw.get("name") or "")
        if name not in expected:
            continue
        state = CheckState(
            name=name,
            status=str(raw.get("status") or "unknown"),
            conclusion=str(raw.get("conclusion") or ""),
            details_url=str(raw.get("details_url") or ""),
            check_id=_as_int(raw.get("id")),
        )
        previous = selected.get(name)
        if previous is None or state.check_id > previous.check_id:
            selected[name] = state
    return GateSnapshot(checks=selected)


def main_push_run_ids(payload: Mapping[str, Any], sha: str) -> set[int]:
    raw_runs = payload.get("workflow_runs")
    if not isinstance(raw_runs, list):
        raise GateQueryError("GitHub 响应缺少 workflow_runs 数组")
    normalized_sha = sha.lower()
    return {
        _as_int(raw.get("id"))
        for raw in raw_runs
        if isinstance(raw, Mapping)
        and str(raw.get("event") or "") == "push"
        and str(raw.get("head_branch") or "") == "main"
        and str(raw.get("head_sha") or "").lower() == normalized_sha
        and _as_int(raw.get("id")) >= 0
    }


def filter_checks_to_runs(
    payload: Mapping[str, Any], allowed_run_ids: set[int]
) -> Mapping[str, Any]:
    raw_runs = payload.get("check_runs")
    if not isinstance(raw_runs, list):
        raise GateQueryError("GitHub 响应缺少 check_runs 数组")
    filtered: list[Mapping[str, Any]] = []
    for raw in raw_runs:
        if not isinstance(raw, Mapping):
            continue
        match = RUN_ID_PATTERN.search(str(raw.get("details_url") or ""))
        if match and int(match.group("run_id")) in allowed_run_ids:
            filtered.append(raw)
    return {"check_runs": filtered}


def format_snapshot(snapshot: GateSnapshot, *, prefix: str = "") -> str:
    lines = [f"{prefix}GitHub quality gates:"]
    for name in EXPECTED_CHECKS:
        state = snapshot.checks.get(name)
        if state is None:
            lines.append(f"- {name}: missing")
            continue
        result = state.conclusion if state.terminal else state.status
        url = f" {state.details_url}" if state.details_url else ""
        lines.append(f"- {name}: {result}{url}")
    return "\n".join(lines)


def wait_for_gates(
    fetch: Callable[[], Mapping[str, Any]],
    *,
    timeout_seconds: float,
    poll_seconds: float,
    heartbeat_seconds: float,
    monotonic: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
    emit: Callable[[str], None] = print_flush,
    max_consecutive_query_errors: int = 3,
) -> tuple[int, GateSnapshot | None]:
    start = monotonic()
    last_emit = float("-inf")
    last_signature: tuple[tuple[str, str, str, str], ...] | None = None
    consecutive_errors = 0
    last_snapshot: GateSnapshot | None = None

    while True:
        now = monotonic()
        try:
            last_snapshot = snapshot_from_payload(fetch())
            consecutive_errors = 0
        except (GateQueryError, json.JSONDecodeError) as exc:
            consecutive_errors += 1
            emit(
                f"GitHub quality gate query failed "
                f"({consecutive_errors}/{max_consecutive_query_errors}): {exc}"
            )
            if consecutive_errors >= max_consecutive_query_errors:
                return BLOCKED, last_snapshot
        else:
            signature = last_snapshot.signature
            if signature != last_signature or now - last_emit >= heartbeat_seconds:
                emit(format_snapshot(last_snapshot))
                last_signature = signature
                last_emit = now
            if last_snapshot.all_terminal:
                return (
                    SUCCESS if last_snapshot.all_successful else GATES_FAILED,
                    last_snapshot,
                )

        elapsed = monotonic() - start
        if elapsed >= timeout_seconds:
            emit(f"GitHub quality gate wait timed out after {elapsed:.0f}s")
            return BLOCKED, last_snapshot
        sleep(min(poll_seconds, timeout_seconds - elapsed))


def run_command(command: Sequence[str]) -> str:
    try:
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except OSError as exc:
        raise GateQueryError(f"无法执行 {command[0]}: {exc}") from exc
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip() or "未知错误"
        raise GateQueryError(f"{' '.join(command)} 失败: {detail}")
    return completed.stdout.strip()


def resolve_repository() -> str:
    repository = run_command(
        ("gh", "repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner")
    )
    if not re.fullmatch(r"[^/\s]+/[^/\s]+", repository):
        raise GateQueryError(f"无法识别 GitHub 仓库: {repository!r}")
    return repository


def fetch_check_runs(repository: str, sha: str) -> Mapping[str, Any]:
    workflow_endpoint = (
        f"repos/{repository}/actions/runs?head_sha={sha}&event=push&per_page=100"
    )
    workflow_output = run_command(
        ("gh", "api", "-H", "Accept: application/vnd.github+json", workflow_endpoint)
    )
    try:
        workflow_payload = json.loads(workflow_output)
    except json.JSONDecodeError as exc:
        raise GateQueryError(f"GitHub workflow runs 返回了无效 JSON: {exc}") from exc
    if not isinstance(workflow_payload, Mapping):
        raise GateQueryError("GitHub workflow runs 响应不是 JSON object")
    allowed_run_ids = main_push_run_ids(workflow_payload, sha)

    checks_endpoint = (
        f"repos/{repository}/commits/{sha}/check-runs?filter=latest&per_page=100"
    )
    checks_output = run_command(
        ("gh", "api", "-H", "Accept: application/vnd.github+json", checks_endpoint)
    )
    try:
        checks_payload = json.loads(checks_output)
    except json.JSONDecodeError as exc:
        raise GateQueryError(f"GitHub check runs 返回了无效 JSON: {exc}") from exc
    if not isinstance(checks_payload, Mapping):
        raise GateQueryError("GitHub check runs 响应不是 JSON object")
    return filter_checks_to_runs(checks_payload, allowed_run_ids)


def parse_args(argv: Iterable[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Wait for the scoped GitHub quality gate on a pushed main SHA."
    )
    parser.add_argument("--sha", required=True, help="Exact 40-character commit SHA")
    parser.add_argument("--repo", help="GitHub owner/repository; defaults to gh repo view")
    parser.add_argument("--timeout-seconds", type=float, default=45 * 60)
    parser.add_argument("--poll-seconds", type=float, default=15)
    parser.add_argument("--heartbeat-seconds", type=float, default=60)
    args = parser.parse_args(list(argv) if argv is not None else None)
    if not re.fullmatch(r"[0-9a-fA-F]{40}", args.sha):
        parser.error("--sha 必须是完整的 40 位十六进制提交 SHA")
    if args.timeout_seconds < 0:
        parser.error("--timeout-seconds 不能为负数")
    if args.poll_seconds <= 0:
        parser.error("--poll-seconds 必须大于 0")
    if args.heartbeat_seconds <= 0:
        parser.error("--heartbeat-seconds 必须大于 0")
    if args.repo and not re.fullmatch(r"[^/\s]+/[^/\s]+", args.repo):
        parser.error("--repo 必须使用 owner/repository 格式")
    return args


def main(argv: Iterable[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        repository = args.repo or resolve_repository()
        code, snapshot = wait_for_gates(
            lambda: fetch_check_runs(repository, args.sha.lower()),
            timeout_seconds=args.timeout_seconds,
            poll_seconds=args.poll_seconds,
            heartbeat_seconds=args.heartbeat_seconds,
        )
    except GateQueryError as exc:
        print(f"GitHub quality gate setup failed: {exc}", file=sys.stderr, flush=True)
        return BLOCKED

    if snapshot is not None:
        label = "PASS" if code == SUCCESS else "FAIL" if code == GATES_FAILED else "BLOCKED"
        print(format_snapshot(snapshot, prefix=f"[{label}] "), flush=True)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
