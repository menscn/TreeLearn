#!/usr/bin/env python3
"""Fail-closed static check: androidTest schema expectations must follow TreeLearnSchema.

WHY THIS EXISTS
---------------
`gradlew check` is JVM-only, so the device-only instrumentation suite is never compiled by it.
That blind spot let T-037 bump `TreeLearnSchema.DATABASE_VERSION` 1 -> 2 while
`SqliteSchemaInstrumentedTest` kept hardcoded literals for the version, the `messages` column
set and the trigger set. The suite was statically red and nothing in the build said so.

The durable fix is to derive those expectations from the schema constants. This script is the
guard for that property: it fails if a hardcoded copy reappears, so the next version bump
cannot silently invert a device test again.

It checks PROPERTIES, not exact text, so legitimate edits do not trip it.

EXIT CODES (fail-closed; mirrors the repo guard convention)
  0  PASS
  1  FAIL       - a property is violated
  2  INCONCLUSIVE - an input is missing/unreadable, so nothing was proven

Default paths resolve from THIS SCRIPT's location, never from the current working directory
(`.trae/rules`: dev scripts must work when run from anywhere).
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile

EXIT_PASS = 0
EXIT_FAIL = 1
EXIT_INCONCLUSIVE = 2

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "..", ".."))

SCHEMA_REL = os.path.join(
    "android", "data", "src", "main", "java", "com", "treelearn", "data", "db",
    "TreeLearnSchema.java",
)
ANDROID_TEST_REL = os.path.join("android", "app", "src", "androidTest")
SUITE_REL = os.path.join(
    ANDROID_TEST_REL, "java", "com", "treelearn", "app", "SqliteSchemaInstrumentedTest.java",
)

SCHEMA = os.path.join(REPO_ROOT, SCHEMA_REL)
ANDROID_TEST_DIR = os.path.join(REPO_ROOT, ANDROID_TEST_REL)
SCHEMA_SUITE = os.path.join(REPO_ROOT, SUITE_REL)

failures: list[str] = []
notes: list[str] = []


def fail(message: str) -> None:
    failures.append(message)


def read(path: str) -> str:
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


def strip_java_comments(text: str) -> str:
    """Remove block/javadoc and line comments so prose cannot trip a code check."""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def rel(path: str) -> str:
    """Repo-relative for readability, but never crash when the target is on another drive
    (the self-test builds its throwaway trees under the system temp directory)."""
    try:
        return os.path.relpath(path, REPO_ROOT)
    except ValueError:
        return path


def android_test_java_files() -> list[str]:
    found: list[str] = []
    for root, _dirs, files in os.walk(ANDROID_TEST_DIR):
        for name in files:
            if name.endswith(".java"):
                found.append(os.path.join(root, name))
    return sorted(found)


def check_inputs_exist() -> bool:
    """INCONCLUSIVE beats a vacuous PASS: no input means nothing was proven."""
    ok = True
    for path in (SCHEMA, SCHEMA_SUITE):
        if not os.path.isfile(path):
            fail("missing input: %s" % path)
            ok = False
    if not android_test_java_files():
        fail("no .java files under %s; the scan would be vacuous" % ANDROID_TEST_DIR)
        ok = False
    return ok


JUDGED_CALLS = ("assertEquals", "execSQL")
ALLOWED_USER_VERSION_EXPRESSIONS = ("TreeLearnSchema.DATABASE_VERSION", "V1_SCHEMA_VERSION")


def _literal_mask(flat: str) -> list:
    """True where a character sits inside a Java string/char literal.

    Parens and commas inside a literal must not affect call or argument parsing: an assertion
    message such as "reopen (again), must not rebuild" would otherwise invent a nesting level.
    """
    mask = [False] * len(flat)
    quote = ""
    index = 0
    while index < len(flat):
        char = flat[index]
        if not quote:
            if char == '"' or char == "'":
                quote = char
                mask[index] = True
        else:
            mask[index] = True
            if char == "\\":
                if index + 1 < len(flat):
                    mask[index + 1] = True
                    index += 2
                    continue
            elif char == quote:
                quote = ""
        index += 1
    return mask


def _identifier_before(flat: str, mask: list, opener: int) -> str:
    """The (possibly dotted) identifier immediately left of the '(' at `opener`."""
    end = opener - 1
    while end >= 0 and flat[end] == " ":
        end -= 1
    start = end
    while start >= 0 and not mask[start] and (flat[start].isalnum() or flat[start] in "_."):
        start -= 1
    return flat[start + 1:end + 1]


def _top_level_arguments(flat: str, mask: list, opener: int):
    """Split the argument list opened at `opener` on top-level commas.

    Returns (arguments, closer), or (None, None) when the list does not balance; the caller fails
    closed on that, so a truncated or unbalanced file can never be read as "fine".
    """
    arguments = []
    depth = 0
    start = opener + 1
    index = opener + 1
    while index < len(flat):
        if not mask[index]:
            char = flat[index]
            if char in "([{":
                depth += 1
            elif char in ")]}":
                if char == ")" and depth == 0:
                    arguments.append(flat[start:index])
                    return arguments, index
                depth -= 1
            elif char == "," and depth == 0:
                arguments.append(flat[start:index])
                start = index + 1
        index += 1
    return None, None


def _open_paren_stack(flat: str, mask: list, index: int) -> list:
    """The '(' indices still open immediately before `index`, innermost last.

    A left-to-right scan of the real nesting. A right-to-left walk with a depth counter cannot do
    this: the closers of a PREVIOUS statement inflate the depth, so the walk steps past the current
    statement and can adopt a NEIGHBOURING assertion as the owner -- reading its legitimate argument
    as this occurrence's expectation. That is the same fail-open class as the window this function
    replaced (T-037 fix round 2).
    """
    stack = []
    for position in range(index):
        if mask[position]:
            continue
        if flat[position] == "(":
            stack.append(position)
        elif flat[position] == ")" and stack:
            stack.pop()
    return stack


def _enclosing_call(flat: str, mask: list, index: int):
    """The innermost enclosing JUDGED call around `index`, as (name, arguments).

    Nested calls are skipped rather than judged. Returns ("UNPARSEABLE", None) when the judged call's
    own argument list does not balance, and None when no judged call encloses the occurrence at all --
    both are FAILED CLOSED by the caller.
    """
    for opener in reversed(_open_paren_stack(flat, mask, index)):
        name = _identifier_before(flat, mask, opener).rsplit(".", 1)[-1]
        if name in JUDGED_CALLS:
            arguments, _closer = _top_level_arguments(flat, mask, opener)
            if arguments is None:
                return "UNPARSEABLE", None
            return name, arguments
    return None


def _strip_outer_parens(text: str) -> str:
    """`(EXPR)` and `EXPR` must judge the same; anything else keeps its parens."""
    text = text.strip()
    while text.startswith("(") and text.endswith(")"):
        depth = 0
        redundant = True
        for char in text[1:-1]:
            if char == "(":
                depth += 1
            elif char == ")":
                depth -= 1
                if depth < 0:
                    redundant = False
                    break
        if not redundant or depth != 0:
            break
        text = text[1:-1].strip()
    return text


def _judged_expression(call_name: str, arguments: list):
    """(expected expression, message expression) of a judged call, or (None, None) if unparseable.

    `assertEquals` overloads differ in arity, so the arity decides which argument is the expected
    value -- it is NOT always the first argument. `execSQL` is judged on its SQL expression, where
    the assigned version must be supplied by a constant rather than a literal.
    """
    if call_name == "execSQL":
        if len(arguments) == 1:
            return arguments[0], None
        return None, None
    if len(arguments) == 3:
        return arguments[1], arguments[0]
    if len(arguments) == 2:
        return arguments[0], None
    return None, None


def _is_untouched_zero(expected: str, message) -> bool:
    """SQLite's own default for a file no schema has touched -- not a schema constant.

    The exemption is read from the SAME call: its expected expression must be exactly 0 AND its own
    message must say what that 0 means. It is never read from text around the call, because a window
    near a call is not evidence about that call (T-037 fix round 2).
    """
    return _strip_outer_parens(expected) == "0" and "untouched" in (message or "").lower()


def _user_version_is_derived(call_name: str, expected: str) -> bool:
    """Whether this call's OWN argument derives the version from a schema constant.

    The two judged shapes judge differently, because the argument means different things:
    `assertEquals`'s expected argument must BE the constant, while `execSQL`'s argument is a whole
    SQL expression, so the constant has to supply the ASSIGNED value -- it must appear in that
    argument after the '='. A literal (`... = " + 7`) leaves the assigned part without a constant and
    is rejected, and an argument with no '=' at all is rejected too (fail-closed).
    """
    if call_name == "execSQL":
        assigned = expected.split("=", 1)[1] if "=" in expected else ""
        return any(name in assigned for name in ALLOWED_USER_VERSION_EXPRESSIONS)
    return _strip_outer_parens(expected) in ALLOWED_USER_VERSION_EXPRESSIONS


def check_no_hardcoded_user_version() -> None:
    """Every `PRAGMA user_version` expectation must be a schema constant, judged PER ASSERTION.

    Comments are stripped and whitespace collapsed before scanning, because an expected value may sit
    on a line ABOVE the `rawInt(..., "PRAGMA user_version")` call; a line-based scan would watch the
    wrong line and would also flag documentation that merely mentions the pragma.

    The raw occurrence scan is the parser-independent half: every occurrence it finds MUST be judged,
    and an occurrence whose expected expression cannot be parsed FAILS CLOSED. Legitimacy is decided
    only by the enclosing call's own argument -- never by text near the call, because a second
    hardcoded expectation placed next to a legitimate one hides in any such window.
    """
    judged = 0
    for path in android_test_java_files():
        flat = re.sub(r"\s+", " ", strip_java_comments(read(path)))
        mask = _literal_mask(flat)
        for match in re.finditer(r"PRAGMA user_version", flat):
            call_name, arguments = _enclosing_call(flat, mask, match.start())
            if call_name is None:
                fail("%s: a PRAGMA user_version expectation is not inside a %s call, so its "
                     "expected value cannot be judged (fail-closed)"
                     % (rel(path), "/".join(JUDGED_CALLS)))
                continue
            if arguments is None:
                fail("%s: the %s call enclosing a PRAGMA user_version expectation has an unbalanced "
                     "argument list (fail-closed)" % (rel(path), call_name))
                continue
            expected, message = _judged_expression(call_name, arguments)
            if expected is None:
                fail("%s: the %s call enclosing a PRAGMA user_version expectation has no parseable "
                     "expected-value argument (fail-closed, %d argument(s))"
                     % (rel(path), call_name, len(arguments)))
                continue
            judged += 1
            if _user_version_is_derived(call_name, expected):
                continue
            if _is_untouched_zero(expected, message):
                continue
            fail("%s: a PRAGMA user_version expectation does not name a constant "
                 "(expected expression: %s)" % (rel(path), expected.strip()[:70]))
    notes.append("judged %d PRAGMA user_version expectation(s) in androidTest" % judged)


def check_no_restated_schema_identifiers() -> None:
    """Trigger names and the new column name must come from TreeLearnSchema, not be retyped."""
    for path in android_test_java_files():
        text = read(path)
        where = rel(path)
        for literal in re.findall(r'"trg_[a-z_]*"', text):
            fail("%s restates a trigger name as a literal: %s" % (where, literal))
        # Any literal that spells the column name, not just the exact `"quoted_text"`: the
        # realistic restatement is the declaration form `"quoted_text:TEXT:0"`.
        for literal in re.findall(r'"quoted_text[^"]*"', text):
            fail("%s restates the quote column as a literal: %s" % (where, literal))


def schema_trigger_constants() -> set[str]:
    # Only name constants (`TRIGGER_X = "trg_..."`), never the DDL array `TRIGGER_STATEMENTS`.
    return set(re.findall(r"\b(TRIGGER_[A-Z0-9_]+)\s*=\s*\"", read(SCHEMA)))


def check_every_schema_trigger_is_referenced() -> None:
    """A trigger added to the schema must be referenced by the suite, or the suite goes stale."""
    declared = schema_trigger_constants()
    if not declared:
        fail("no TRIGGER_* constants found in %s; the check would be vacuous" % rel(SCHEMA))
        return
    referenced = set(re.findall(r"TreeLearnSchema\.(TRIGGER_[A-Z0-9_]+)", read(SCHEMA_SUITE)))
    missing = sorted(declared - referenced)
    if missing:
        fail(
            "%s does not reference every declared trigger constant; missing: %s"
            % (rel(SCHEMA_SUITE), ", ".join(missing))
        )
    notes.append("schema declares %d trigger constant(s); suite references %d"
                 % (len(declared), len(referenced)))


def check_trigger_set_is_derived() -> None:
    """EXPECTED_TRIGGERS must be built by expectedTriggers(), not by an inline literal list."""
    text = read(SCHEMA_SUITE)
    if not re.search(r"EXPECTED_TRIGGERS\s*=\s*expectedTriggers\(\)\s*;", text):
        fail("EXPECTED_TRIGGERS is not initialised from expectedTriggers()")
    if not re.search(r"Messages\.QUOTED_TEXT\b", text):
        fail("the suite does not reference TreeLearnSchema.Messages.QUOTED_TEXT")


def check_upgrade_case_is_not_bound_to_a_registered_pair() -> None:
    """The fail-closed case must use an UNREGISTERED, derived version pair."""
    text = read(SCHEMA_SUITE)
    if not re.search(r"applyForwardMigrations", text):
        fail("the suite no longer exercises applyForwardMigrations")
    if re.search(r"applyForwardMigrations\(\s*\w+\s*,\s*\d+\s*,", text):
        fail("applyForwardMigrations is called with a hardcoded literal version pair")
    if not re.search(r"TreeLearnSchema\.DATABASE_VERSION\s*\+\s*1", text):
        fail("the fail-closed case is not bound to the derived unregistered pair "
             "(DATABASE_VERSION + 1)")


def configure(root: str) -> None:
    """Point the checks at `root`; used by --root and by the self-test's throwaway trees."""
    global SCHEMA, ANDROID_TEST_DIR, SCHEMA_SUITE
    SCHEMA = os.path.join(root, SCHEMA_REL)
    ANDROID_TEST_DIR = os.path.join(root, ANDROID_TEST_REL)
    SCHEMA_SUITE = os.path.join(root, SUITE_REL)


def run_checks() -> int:
    failures.clear()
    notes.clear()

    if not check_inputs_exist():
        print("INCONCLUSIVE: required inputs are missing")
        for message in failures:
            print("  - %s" % message)
        return EXIT_INCONCLUSIVE

    check_no_hardcoded_user_version()
    check_no_restated_schema_identifiers()
    check_every_schema_trigger_is_referenced()
    check_trigger_set_is_derived()
    check_upgrade_case_is_not_bound_to_a_registered_pair()

    for note in notes:
        print("OK   %s" % note)
    if failures:
        print("VERDICT: FAIL (%d problem(s))" % len(failures))
        for message in failures:
            print("  - %s" % message)
        return EXIT_FAIL

    print("OK   no hardcoded schema version literal in androidTest")
    print("OK   no restated trigger/column name literals in androidTest")
    print("OK   EXPECTED_TRIGGERS is derived from expectedTriggers()")
    print("OK   the fail-closed case uses the derived unregistered pair")
    print("VERDICT: PASS")
    return EXIT_PASS


# ---------------------------------------------------------------------------
# Self-test: every detection branch must be shown able to FAIL. A guard whose
# branches have never been observed rejecting a violation is an unverified guard.
# ---------------------------------------------------------------------------

SELFTEST_CASES = (
    ("baseline (unmodified tree) must PASS", None, EXIT_PASS),
    ("hardcoded user_version literal must FAIL",
     ('assertEquals("reopen must not rebuild or migrate", TreeLearnSchema.DATABASE_VERSION,',
      'assertEquals("reopen must not rebuild or migrate", 7,'), EXIT_FAIL),
    # Adjacency is the whole point of the next pair: the SAME position, the SAME call shape, differing
    # only in whether the expected value is derived. A window around the pragma cannot tell them apart,
    # which is exactly the fail-open hole this pair was added for (T-037 fix round 2).
    ("a SECOND hardcoded user_version expectation adjacent to a legitimate one must FAIL",
     ('rawInt(reopened, "PRAGMA user_version"));',
      'rawInt(reopened, "PRAGMA user_version")); '
      'assertEquals("adjacent hardcoded expectation", 7, rawInt(reopened, "PRAGMA user_version"));'),
     EXIT_FAIL),
    ("a SECOND derived user_version expectation adjacent to a legitimate one must PASS",
     ('rawInt(reopened, "PRAGMA user_version"));',
      'rawInt(reopened, "PRAGMA user_version")); '
      'assertEquals("adjacent derived expectation", TreeLearnSchema.DATABASE_VERSION, '
      'rawInt(reopened, "PRAGMA user_version"));'),
     EXIT_PASS),
    # The remaining two judgement paths of this check need their own negative case, or a mistake in
    # either could only ever make the guard LOOSER than it claims.
    ("an execSQL user_version assignment by literal must FAIL",
     ('"PRAGMA user_version = " + V1_SCHEMA_VERSION', '"PRAGMA user_version = " + 7'), EXIT_FAIL),
    ("a user_version expectation outside any judged call must FAIL",
     ('rawInt(reopened, "PRAGMA user_version"));',
      'rawInt(reopened, "PRAGMA user_version")); '
      'assertTrue("adjacent non-judged expectation", 0 == rawInt(reopened, "PRAGMA user_version"));'),
     EXIT_FAIL),
    ("a user_version expectation whose assertion arity cannot be judged must FAIL",
     ('assertEquals("reopen must not rebuild or migrate", TreeLearnSchema.DATABASE_VERSION,',
      'assertEquals("a four-argument assertion cannot be judged", 0, TreeLearnSchema.DATABASE_VERSION,'),
     EXIT_FAIL),
    ("restated trigger name literal must FAIL",
     ('TreeLearnSchema.TRIGGER_MESSAGES_QUOTE_INSERT,',
      '"trg_messages_quote_requires_user_row",'), EXIT_FAIL),
    ("restated quote column literal must FAIL",
     ('TreeLearnSchema.Messages.QUOTED_TEXT + ":TEXT:0"', '"quoted_text:TEXT:0"'), EXIT_FAIL),
    ("unreferenced schema trigger constant must FAIL",
     ("TreeLearnSchema.TRIGGER_MESSAGES_QUOTE_UPDATE",
      "TreeLearnSchema.TRIGGER_REVISIONS_IMMUTABLE"), EXIT_FAIL),
    ("inline EXPECTED_TRIGGERS literal must FAIL",
     ("private static final Set<String> EXPECTED_TRIGGERS = expectedTriggers();",
      "private static final Set<String> EXPECTED_TRIGGERS = new LinkedHashSet<>();"), EXIT_FAIL),
    ("hardcoded migration pair must FAIL",
     ("TreeLearnSchema.applyForwardMigrations(db,\n                        TreeLearnSchema.DATABASE_VERSION + 1, TreeLearnSchema.DATABASE_VERSION)",
      "TreeLearnSchema.applyForwardMigrations(db, 3, 2)"), EXIT_FAIL),
    ("missing androidTest tree must be INCONCLUSIVE", "REMOVE_TREE", EXIT_INCONCLUSIVE),
)


def _build_tree(destination: str) -> None:
    for rel in (SCHEMA_REL, SUITE_REL):
        target = os.path.join(destination, rel)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(os.path.join(REPO_ROOT, rel), target)


def run_selftest() -> int:
    script = os.path.abspath(__file__)
    problems = 0
    for name, mutation, expected in SELFTEST_CASES:
        workdir = tempfile.mkdtemp(prefix="t037-static-selftest-")
        try:
            _build_tree(workdir)
            if mutation == "REMOVE_TREE":
                shutil.rmtree(os.path.join(workdir, ANDROID_TEST_REL))
            elif mutation is not None:
                find, replace = mutation
                target = os.path.join(workdir, SUITE_REL)
                text = read(target)
                if find not in text:
                    print("SELFTEST BROKEN %s: injection anchor not found" % name)
                    problems += 1
                    continue
                with open(target, "w", encoding="utf-8") as handle:
                    handle.write(text.replace(find, replace, 1))
            result = subprocess.run(
                [sys.executable, script, "--root", workdir],
                capture_output=True, text=True,
            )
            verdict = "Ok" if result.returncode == expected else "BROKEN"
            if result.returncode != expected:
                problems += 1
            print("CASE %-52s expected=%d actual=%d  %s"
                  % (name, expected, result.returncode, verdict))
            if result.returncode != expected:
                for stream in (result.stdout, result.stderr):
                    for line in (stream or "").splitlines():
                        print("      | %s" % line)
        finally:
            shutil.rmtree(workdir, ignore_errors=True)

    print("OVERALL %s (%d case(s), %d broken)"
          % ("PASS" if problems == 0 else "FAIL", len(SELFTEST_CASES), problems))
    return EXIT_PASS if problems == 0 else EXIT_FAIL


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=REPO_ROOT,
                        help="repository root to check (default: this script's repo)")
    parser.add_argument("--selftest", action="store_true",
                        help="prove every detection branch can fail")
    args = parser.parse_args()

    if args.selftest:
        configure(REPO_ROOT)
        return run_selftest()

    configure(os.path.abspath(args.root))
    return run_checks()


if __name__ == "__main__":
    sys.exit(main())
