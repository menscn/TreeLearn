"""规格自检（轻量版）。

用法（从任意目录）：
    python .codex/scripts/check_spec_author_agent.py
    python .codex/scripts/check_spec_author_agent.py Docs/spec/SPEC-YYYY-MM-DD-x.md

不带参数时检查规格骨架 `.codex/templates/SPEC.md`；带参数时检查那一份规格。

为什么只有这几条：规格与交互设计阶段按**轻流程**走（用户 2026-09-21 决定），产物是给人
review 的文档，不是门禁对象。旧版有 17 条判据，外加「每条检测分支都要配一条注入用例」的
自闸门，判定过重，已删。这里只留下便宜、且能挡住真实事故的几条：

  1. 文件在不在（写错路径也要报，fail-closed）；
  2. 四类内容的小节标题在不在：需求规格 / 界面方案 / 技术规格 / 疑虑点；
  3. 文件头的 intent: 指向真实存在的文件（防止规格悬空）；
  4. 没有裸 hex、没有临场 px（防止设计数值脱离 tokens.json 自行漂移）；
  5. 没有占位符（PENDING / TBD / TODO / 待填）——占位符会让人以为已经写完。

故意**不做**的事：不检查行文结构、不要求每节写「来源」、不要求证据文件、不要求独立复核。
退出码：0 = 通过；1 = 有 FAIL。
"""

import io
import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))

TEMPLATE = ".codex/templates/SPEC.md"
CONTENT_CLASSES = ["需求规格", "界面方案", "技术规格", "疑虑点"]
PLACEHOLDERS = ("PENDING", "TBD", "TODO", "待填")
HEX_RE = re.compile(r"#[0-9A-Fa-f]{3,8}\b")
PX_RE = re.compile(r"[0-9]+px")


def repo_path(rel):
    return os.path.join(REPO_ROOT, rel.replace("/", os.sep))


def read_text(rel):
    with io.open(repo_path(rel), encoding="utf-8") as f:
        return f.read()


def section_titles(text):
    return [l[3:].strip() for l in text.splitlines() if l.startswith("## ")]


def front_matter(text):
    values = {}
    for line in text.splitlines():
        if not line.strip():
            if values:
                break
            continue
        m = re.match(r"^([a-z_]+):\s*(.+)$", line)
        if m:
            values[m.group(1)] = m.group(2).strip()
    return values


def finish(failures):
    print("")
    print("check_spec_author_agent: {0} check(s) failed".format(len(failures)))
    print("EXITCODE={0}".format(1 if failures else 0))
    return 1 if failures else 0


def main():
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    target = sys.argv[1] if len(sys.argv) > 1 else TEMPLATE
    is_template = target.replace("\\", "/").endswith(TEMPLATE)
    failures = []

    def report(cond, label, detail=""):
        print("{0} {1}{2}".format("OK  " if cond else "FAIL", label,
                                  " — " + detail if detail else ""))
        if not cond:
            failures.append(label)

    print("target: " + target + (" (template skeleton)" if is_template else " (instance)"))

    if not os.path.exists(repo_path(target)):
        report(False, "spec file exists", "missing: " + target)
        return finish(failures)

    text = read_text(target)

    titles = section_titles(text)
    missing = [c for c in CONTENT_CLASSES if not any(c in t for t in titles)]
    report(not missing, "declares the four content classes",
           "sections={0}; missing={1}".format(len(titles), missing))

    if not is_template:
        intent = front_matter(text).get("intent", "")
        report(bool(intent) and os.path.exists(repo_path(intent)),
               "front-matter intent points at a real file",
               "intent={0}".format(intent or "<absent>"))

    hits = [p for p in PLACEHOLDERS if p in text]
    report(not hits, "no placeholder tokens", "measured={0}".format(hits))

    hexes = [m.group(0) for m in HEX_RE.finditer(text)]
    pxs = [m.group(0) for m in PX_RE.finditer(text)]
    report(not hexes and not pxs, "no bare hex or ad-hoc px values",
           "hex={0} px={1}".format(hexes[:3], pxs[:3]))

    return finish(failures)


if __name__ == "__main__":
    sys.exit(main())