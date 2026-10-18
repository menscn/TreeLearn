"""Count production lines the way this repository adjudicated the sizing rule:
non-blank, non-comment. See .codex/rules/harness-tasks.md ("计数口径").

Java: strips // line comments and /* */ block comments.
XML:  strips <!-- --> comments.
Tests, documents and generated files are NOT counted by this tool; pass only the
production files of the change you are measuring.

Usage:
  python -X utf8 .codex/scripts/dev/count_production_lines.py <file> [<file> ...]
"""
import os
import re
import sys


def strip_java(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def strip_xml(text):
    return re.sub(r"<!--.*?-->", "", text, flags=re.S)


def counted(path):
    with open(path, encoding="utf-8") as handle:
        text = handle.read()
    body = strip_xml(text) if path.lower().endswith((".xml", ".html")) else strip_java(text)
    return len([line for line in body.splitlines() if line.strip()])


def main(paths):
    total = 0
    for path in paths:
        if not os.path.isfile(path):
            print("MISSING %s" % path)
            return 3
        number = counted(path)
        total += number
        print("%6d  %s" % (number, path))
    print("%6d  TOTAL (non-blank, non-comment, production files only)" % total)
    if not paths:
        print("no files given: refusing to report an empty total as a result")
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))