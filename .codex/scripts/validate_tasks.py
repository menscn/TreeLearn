#!/usr/bin/env python3
"""Read-only validator for Treelearn Harness Task v2 files under .codex/tasks/."""

from __future__ import annotations

import sys
import re
from dataclasses import dataclass, field
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
TASKS_DIR = REPO_ROOT / ".codex" / "tasks"
VALID_STATES = {
    "clarifying",
    "ready",
    "in_progress",
    "blocked",
    "verifying",
    "verified",
    "canceled",
}
CONFIRMED_STATES = VALID_STATES - {"clarifying", "canceled"}
VALID_TASK_TYPES = {"requirement", "implementation", "verification", "docs", "config"}
VALID_TDD_MODES = {"required", "approved-exception", "not-applicable"}
VALID_OWNER_LANES = {
    "frontend",
    "backend",
    "android",
    "contract",
    "docs",
    "harness",
    "verification",
}
VALID_STACKS = {"frontend", "backend", "android", "docs", "harness"}
REQUIRED_FIELDS = {
    "id",
    "title",
    "template_version",
    "task_type",
    "status",
    "parent_requirement",
    "requirements_confirmed",
    "generated_by",
    "owner",
    "owner_lane",
    "stack",
    "estimated_production_lines",
    "shared_code_touched",
    "depends_on",
    "tdd_mode",
}
REQUIRED_SECTIONS = {
    "Requirement collaboration brief",
    "Goal",
    "Scope",
    "Documentation impact record",
    "Acceptance criteria",
    "TDD execution record",
    "Implementation notes",
    "Verification report",
    "Handoff notes",
}
EMPTY_VALUES = {"", "none", "null", "pending", "unknown", "<item>"}
TASK_ID_RE = re.compile(r"^T-(\d{4}-\d{2}-\d{2})-(\d{3})$")
TASK_FILENAME_RE = re.compile(
    r"^T-(\d{4}-\d{2}-\d{2})-([a-z0-9]+(?:-[a-z0-9]+)*)-(\d{3})\.md$"
)


@dataclass
class ParsedTask:
    path: Path
    text: str
    fields: dict[str, str]
    sections: set[str]
    subsections: dict[str, dict[str, str]] = field(default_factory=dict)


@dataclass
class Result:
    path: Path
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


def parse_task(path: Path) -> ParsedTask:
    text = path.read_text(encoding="utf-8")
    fields: dict[str, str] = {}
    sections: set[str] = set()
    subsections: dict[str, dict[str, str]] = {}
    current_subsection: str | None = None
    before_sections = True

    for raw_line in text.splitlines():
        line = raw_line.strip()
        if line.startswith("## "):
            before_sections = False
            sections.add(line[3:].strip())
            current_subsection = None
            continue
        if line.startswith("### "):
            current_subsection = line[4:].strip()
            subsections.setdefault(current_subsection, {})
            continue
        if before_sections and ":" in line and not line.startswith("#"):
            key, value = line.split(":", 1)
            fields[key.strip()] = value.strip()
        elif current_subsection and line.startswith("- ") and ":" in line:
            key, value = line[2:].split(":", 1)
            subsections[current_subsection][key.strip()] = value.strip()

    return ParsedTask(path, text, fields, sections, subsections)


def is_present(value: str | None) -> bool:
    return value is not None and value.strip().lower() not in EMPTY_VALUES


def parse_bool(value: str | None) -> bool | None:
    if value is None:
        return None
    lowered = value.lower()
    if lowered == "true":
        return True
    if lowered == "false":
        return False
    return None


def parse_list(value: str) -> list[str]:
    stripped = value.strip()
    if not (stripped.startswith("[") and stripped.endswith("]")):
        raise ValueError
    body = stripped[1:-1].strip()
    if not body:
        return []
    return [item.strip().strip("'\"") for item in body.split(",") if item.strip()]


def subsection(task: ParsedTask, prefix: str) -> dict[str, str]:
    for heading, values in task.subsections.items():
        if heading.startswith(prefix):
            return values
    return {}


def index_tasks(paths: list[Path]) -> dict[str, ParsedTask]:
    candidates = set(paths)
    for path in paths:
        candidates.update(path.parent.glob("*.md"))
    indexed: dict[str, ParsedTask] = {}
    for path in candidates:
        if path.name == "_TEMPLATE.md" or not path.is_file():
            continue
        try:
            parsed = parse_task(path)
        except (OSError, UnicodeError):
            continue
        indexed[path.name] = parsed
    return indexed


def validate_tdd(task: ParsedTask, result: Result) -> None:
    mode = task.fields.get("tdd_mode")
    task_type = task.fields.get("task_type")
    status = task.fields.get("status")
    red = subsection(task, "RED")
    green = subsection(task, "GREEN")
    exception = subsection(task, "TDD or sizing exception")

    if task_type == "implementation" and mode == "not-applicable":
        result.errors.append("implementation task cannot use tdd_mode: not-applicable")
    if task_type in {"docs", "config"} and mode != "approved-exception":
        result.errors.append(f"{task_type} task requires a prior user-approved TDD exception")

    if mode == "approved-exception":
        for key in ("Exception type", "Reason", "User approval", "Alternative verification"):
            if not is_present(exception.get(key)):
                result.errors.append(f"approved exception requires {key}")

    if task_type != "implementation" or mode != "required":
        return

    if status in {"in_progress", "blocked", "verifying", "verified"}:
        for key in ("Test/scenario", "Command", "Exit code", "Observed failure", "Failure reason validated"):
            if not is_present(red.get(key)):
                result.errors.append(f"RED evidence requires {key}")
        try:
            if int(red.get("Exit code", "0")) == 0:
                result.errors.append("RED exit code must be non-zero")
        except ValueError:
            result.errors.append("RED exit code must be an integer")

    if status in {"verifying", "verified"}:
        for key in ("Implementation", "Command", "Exit code", "Passing evidence", "Regression evidence"):
            if not is_present(green.get(key)):
                result.errors.append(f"GREEN evidence requires {key}")
        try:
            if int(green.get("Exit code", "1")) != 0:
                result.errors.append("GREEN exit code must be zero")
        except ValueError:
            result.errors.append("GREEN exit code must be an integer")
        if is_present(red.get("Command")) and is_present(green.get("Command")):
            if red["Command"] != green["Command"]:
                result.errors.append("RED and GREEN must use the same targeted test command")


def validate_v2(task: ParsedTask, task_index: dict[str, ParsedTask]) -> Result:
    result = Result(task.path)
    missing_fields = sorted(REQUIRED_FIELDS - task.fields.keys())
    if missing_fields:
        result.errors.append("missing fields: " + ", ".join(missing_fields))
    missing_sections = sorted(REQUIRED_SECTIONS - task.sections)
    if missing_sections:
        result.errors.append("missing sections: " + ", ".join(missing_sections))
    if result.errors:
        return result

    status = task.fields["status"]
    task_type = task.fields["task_type"]
    confirmed = parse_bool(task.fields["requirements_confirmed"])
    parent_id = task.fields["parent_requirement"]
    tdd_mode = task.fields["tdd_mode"]
    task_id_match = TASK_ID_RE.fullmatch(task.fields["id"])
    filename_match = TASK_FILENAME_RE.fullmatch(task.path.name)

    if task_id_match is None:
        result.errors.append("id must use T-YYYY-MM-DD-NNN")
    if filename_match is None:
        result.errors.append(
            "filename must use T-YYYY-MM-DD-kebab-case-description-NNN.md"
        )
    elif task_id_match and (
        filename_match.group(1) != task_id_match.group(1)
        or filename_match.group(3) != task_id_match.group(2)
    ):
        result.errors.append("filename date and number must match task id")

    if status not in VALID_STATES:
        result.errors.append(f"invalid v2 status: {status}")
    if task_type not in VALID_TASK_TYPES:
        result.errors.append(f"invalid task_type: {task_type}")
    if tdd_mode not in VALID_TDD_MODES:
        result.errors.append(f"invalid tdd_mode: {tdd_mode}")
    if task.fields["owner_lane"] not in VALID_OWNER_LANES:
        result.errors.append(f"invalid owner_lane: {task.fields['owner_lane']}")
    if task.fields["stack"] not in VALID_STACKS:
        result.errors.append(f"invalid stack: {task.fields['stack']}")
    if confirmed is None:
        result.errors.append("requirements_confirmed must be true or false")
    elif status == "clarifying" and confirmed:
        result.errors.append("clarifying requires requirements_confirmed: false")
    elif status in CONFIRMED_STATES and not confirmed:
        result.errors.append(f"{status} requires requirements_confirmed: true")

    if status == "clarifying" and task_type != "requirement":
        result.errors.append("only a requirement task may be clarifying")
    if task_type == "requirement":
        if parent_id.lower() not in {"null", "none", ""}:
            result.errors.append("requirement task must use parent_requirement: null")
    else:
        if parent_id.lower() in {"null", "none", ""}:
            result.errors.append("child task requires parent_requirement")
        elif TASK_FILENAME_RE.fullmatch(parent_id) is None:
            result.errors.append("parent_requirement must be a descriptive task filename")
        elif parent_id not in task_index:
            result.errors.append(f"parent_requirement not found: {parent_id}")
        elif task_index[parent_id].fields.get("task_type") != "requirement":
            result.errors.append(f"parent_requirement is not a requirement task: {parent_id}")
        elif parse_bool(task_index[parent_id].fields.get("requirements_confirmed")) is not True:
            result.errors.append(f"child task requires a confirmed parent requirement: {parent_id}")

    try:
        estimated_lines = int(task.fields["estimated_production_lines"])
        if estimated_lines > 200:
            result.warnings.append(f"estimated production lines {estimated_lines} exceeds soft limit 200")
        if estimated_lines > 500:
            exception = subsection(task, "TDD or sizing exception")
            exception_type = exception.get("Exception type", "").lower()
            if "sizing" not in exception_type or not is_present(exception.get("User approval")):
                result.errors.append("estimated production lines exceeds 500 without user-approved sizing exception")
    except ValueError:
        result.errors.append("estimated_production_lines must be an integer")

    try:
        dependencies = parse_list(task.fields["depends_on"])
        for dependency in dependencies:
            if TASK_FILENAME_RE.fullmatch(dependency) is None:
                result.errors.append(
                    f"dependency must be a descriptive task filename: {dependency}"
                )
            elif dependency == task.path.name:
                result.errors.append("task cannot depend on itself")
            elif dependency not in task_index:
                result.errors.append(f"dependency not found: {dependency}")
    except ValueError:
        result.errors.append("depends_on must use bracket-list syntax")

    validate_tdd(task, result)
    return result


def validate(path: Path, task_index: dict[str, ParsedTask]) -> Result:
    try:
        task = parse_task(path)
    except (OSError, UnicodeError) as exc:
        return Result(path, errors=[f"cannot read UTF-8 task: {exc}"])

    version = task.fields.get("template_version")
    if version is None:
        result = Result(path, warnings=["LEGACY task: no template_version; migrate before resuming execution"])
        if task.fields.get("status") == "in_progress":
            result.errors.append("legacy in_progress task must migrate to Task v2 before execution")
        return result
    if version != "2":
        return Result(path, errors=[f"unsupported template_version: {version}"])
    return validate_v2(task, task_index)


def default_paths() -> list[Path]:
    return sorted(path for path in TASKS_DIR.glob("*.md") if path.name != "_TEMPLATE.md")


def main(argv: list[str]) -> int:
    paths = [Path(arg).resolve() for arg in argv] if argv else default_paths()
    if not paths:
        print("PASS: no task files selected")
        return 0

    task_index = index_tasks(paths)
    results = [validate(path, task_index) for path in paths]
    for result in results:
        label = result.path.name
        for warning in result.warnings:
            print(f"WARNING {label}: {warning}")
        for error in result.errors:
            print(f"ERROR {label}: {error}")
        if not result.errors:
            print(f"PASS {label}")

    error_count = sum(len(result.errors) for result in results)
    warning_count = sum(len(result.warnings) for result in results)
    print(f"SUMMARY files={len(results)} errors={error_count} warnings={warning_count}")
    return 1 if error_count else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
