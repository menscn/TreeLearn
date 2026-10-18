"""Harness 文档类任务的共享校验函数。

约定：
- 路径一律按脚本自身位置解析，不依赖调用者的当前工作目录
  （见 AGENTS.md 失败案例库：dev 脚本的默认路径不得按 CWD 解析）。
- 所有函数只读，不写任何文件。
- 纯标准库实现。
"""

import io
import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
TRAE_DIR = os.path.dirname(SCRIPT_DIR)
REPO_ROOT = os.path.dirname(TRAE_DIR)

PLACEHOLDERS = ("PENDING", "TBD", "TODO", "待填")


def enable_utf8_stdout():
    """Windows 控制台默认按系统代码页编码，会让中文证据行变成乱码。"""
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


def repo_path(rel):
    return os.path.join(REPO_ROOT, rel.replace("/", os.sep))


def exists(rel):
    return os.path.exists(repo_path(rel))


def read_text(rel):
    with io.open(repo_path(rel), encoding="utf-8") as f:
        return f.read()


def walk_files(rel_dir):
    """返回目录下全部文件的仓库相对路径（正斜杠），已排序。"""
    root = repo_path(rel_dir)
    out = []
    for base, _dirs, files in os.walk(root):
        for name in files:
            full = os.path.join(base, name)
            rel = os.path.relpath(full, REPO_ROOT).replace(os.sep, "/")
            out.append(rel)
    return sorted(out)


def nonempty_nonheader_lines(text):
    return [l for l in text.splitlines() if l.strip() and not l.lstrip().startswith("#")]


def section_titles(text, level=2):
    prefix = "#" * level + " "
    return [l[len(prefix):].strip() for l in text.splitlines() if l.startswith(prefix)]


def section_body(text, title, level=2):
    lines = text.splitlines()
    prefix = "#" * level + " "
    start = None
    for i, l in enumerate(lines):
        if l.startswith(prefix) and l[len(prefix):].strip() == title:
            start = i + 1
            break
    if start is None:
        return None
    out = []
    for l in lines[start:]:
        if l.lstrip().startswith("#"):
            break
        out.append(l)
    return "\n".join(out)


def bullets(text):
    return [l for l in text.splitlines() if l.lstrip().startswith(("- ", "* "))]


def normalize(text):
    return re.sub(r"\s+", "", text)


PATH_RE = re.compile(r"(?:Docs|\.codex|android|artifacts)/[^\s`）)，。；、|]+")


def mask_paths(text, mapping):
    """把仓库路径替换成稳定占位符。

    引用同一条路径不等于「复制正文」。若不先屏蔽路径，
    「列出 Docs/ 下全部文件」这条要求本身就会制造大量假命中
    （实测：未屏蔽时 265 处命中，全部来自路径本身）。
    """
    def repl(m):
        s = m.group(0)
        if s not in mapping:
            mapping[s] = "\u0001{0}\u0002".format(len(mapping))
        return mapping[s]
    return PATH_RE.sub(repl, text)


def overlap_hits(source, other, n, mapping=None):
    """返回 source 中与 other 相同的 n 字连续子串（去空白、屏蔽路径后）。"""
    if mapping is None:
        mapping = {}
    a = normalize(mask_paths(source, mapping))
    b = normalize(mask_paths(other, mapping))
    if len(a) < n or len(b) < n:
        return []
    windows = set(b[i:i + n] for i in range(len(b) - n + 1))
    return [a[i:i + n] for i in range(len(a) - n + 1) if a[i:i + n] in windows]


def max_overlap_with_files(source, rel_paths, n):
    """返回 (命中总数, [(文件, 子串)])；路径引用不计入。"""
    total = 0
    detail = []
    mapping = {}
    for rel in rel_paths:
        hits = overlap_hits(source, read_text(rel), n, mapping)
        if hits:
            total += len(hits)
            detail.append((rel, hits[0]))
    return total, detail


def placeholder_hits(text):
    return [p for p in PLACEHOLDERS if p in text]


def backticked_paths(text):
    """提取反引号内、看起来指向仓库文件的路径。"""
    out = []
    for m in re.finditer(r"`([^`]+)`", text):
        s = m.group(1).strip()
        if re.match(r"^(Docs|\.codex|android|artifacts|README)", s):
            out.append(s)
    return out


def field_names_from_marker(text, marker="FIELDS:"):
    """读取 <!-- FIELDS: a | b | c --> 注解除列的字段名集合。"""
    m = re.search(r"<!--\s*" + re.escape(marker) + r"(.*?)-->", text, re.S)
    if not m:
        return None
    raw = m.group(1).strip()
    return set(x.strip() for x in raw.split("|") if x.strip())


FRONT_MATTER_RE = re.compile(r"^([a-z_]+):\s*(.+)$")


def front_matter(text):
    """解析文档首部的 key: value 段（遇到空行即结束）。

    模板与实例共用这一处判定：两个脚本各写一份实现，就会出现「同一条规则两处实现」。
    """
    values = {}
    for line in text.splitlines():
        if not line.strip():
            if values:
                break
            continue
        m = FRONT_MATTER_RE.match(line)
        if m:
            values[m.group(1)] = m.group(2).strip()
    return values


def front_matter_keys(text):
    return set(front_matter(text))


CLARIFICATION_RULE = ".codex/rules/harness-clarification.md"


def clarification_fields():
    """从澄清规则正文解析字段清单。

    唯一权威是 .codex/rules/harness-clarification.md；不在脚本里手写一份，
    否则规则一改就会出现「同一条规则两处实现」的漂移。
    解析失败返回 None，由调用方判 FAIL。
    """
    if not exists(CLARIFICATION_RULE):
        return None
    text = read_text(CLARIFICATION_RULE)
    m = re.search(r"澄清记录必须覆盖：(.*?)。", text, re.S)
    if not m:
        return None
    keys = [seg.split("（")[0].strip() for seg in m.group(1).split("；") if seg.strip()]
    if "非目标" in text:
        keys.append("非目标")
    keys = [k for k in keys if k]
    return keys or None


class Checks(object):
    def __init__(self, name):
        self.name = name
        self.failures = []

    def ok(self, label, detail=""):
        print("OK   {0}{1}".format(label, " — " + detail if detail else ""))

    def fail(self, label, detail=""):
        self.failures.append(label)
        print("FAIL {0}{1}".format(label, " — " + detail if detail else ""))

    def note(self, label, detail=""):
        """只打印、不参与判定的行（前置条件不成立、或语义上已降级为观测）。

        它存在的理由与既有的「前置条件缺失不得算 PASS」同源：
        没有可判对象时打 OK，读者会把「无从认证」读成「认证通过」。
        """
        print("NOTE {0}{1}".format(label, " — " + detail if detail else ""))

    def check(self, cond, label, detail=""):
        if cond:
            self.ok(label, detail)
        else:
            self.fail(label, detail)
        return bool(cond)

    def finish(self):
        print("")
        print("{0}: {1} check(s) failed".format(self.name, len(self.failures)))
        print("EXITCODE={0}".format(1 if self.failures else 0))
        return 1 if self.failures else 0
