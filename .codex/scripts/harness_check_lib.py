"""Harness 文档类任务的共享校验函数。

约定：
- 路径一律按脚本自身位置解析，不依赖调用者的当前工作目录
  （这条要求的权威正文就是本文件；.codex/rules 与 references 不复述它）。
- 所有函数只读，不写任何文件。
- 纯标准库实现。
- `repo_path/exists/read_text` 可选 `root` 参数：默认解析到真仓库，传入别的根就读那棵树。
  判据必须在**被判的那棵树**里读文件，否则守门没法在副本里证「每条判据都会响」——
  写死 REPO_ROOT 的守门只能对着真树跑，它的反向用例就只剩下「改真树」这一种做法。
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


def repo_path(rel, root=None):
    base = root or REPO_ROOT
    return os.path.join(base, rel.replace("/", os.sep))


def exists(rel, root=None):
    return os.path.exists(repo_path(rel, root))


def read_text(rel, root=None):
    with io.open(repo_path(rel, root), encoding="utf-8") as f:
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


def agent_section_names(text):
    """取一个 agent 文件自己声明的小节名（`<tag>` 块 + 二级标题）。

    两个角色成对检查（`check_code_reviewer_agent.py`、`check_codebase_researcher_agent.py`）
    都要拿 verifier.md 的小节集当比对对象。写两份就会一份改了另一份没跟，
    于是「沿用 verifier 骨架」这条判据本身成了第二份实现。
    """
    tags = re.findall(r"^<([a-z]+)>$", text, re.M)
    return tags + [l[3:].strip() for l in text.splitlines() if l.startswith("## ")]


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


def citable_paths(text):
    """角色正文里反引号引出的、能判定在场性的仓库路径。

    通配写法（`android/**`）指的是「这一类路径」而不是某一个文件，无法用 exists() 判，
    所以跳过——它由 `check_stack_facts.py` 那类按目录内容的守门管。
    两份角色守门（B4、B5）都要这一份筛选，各写一遍就成了本仓库要消掉的第二份实现。
    """
    out = []
    for raw in backticked_paths(text):
        token = raw.strip().rstrip("/")
        if "*" in token or "?" in token:
            continue
        out.append(token)
    return out


CITATION_RE = re.compile(
    r"`((?:Docs|\.codex|android|frontend|artifacts|README|AGENTS)[^\s:`]+?):(\d+)"
    r"(?:\s*[-–]\s*(\d+))?`")


def path_line_citations(text):
    """把 `路径:行` / `路径:起-止` 形态的引用解析成 (rel, lo, hi) 三元组（按出现顺序）。

    只认带仓库相对前缀的路径：裸文件名 `run_all_guards.py:89` 到底指哪一个文件无法确定，
    按 basename 全库搜索会把「重名即命中」当成真话，所以这里不认——需要复算时写全路径。
    前缀表里 `AGENTS` 与 `README` 是根级入口文件本身（清单与 ROADMAP 直接写 `AGENTS.md:35`），
    它们没有目录分隔，但全仓库只有一个同名文件，指认不歧义。
    路径体只排除空白与反引号：反引号成对已经把一条声称关在一个 token 里，体内的全角
    括号、逗号、顿号、分号都是合法文件名字符——`Docs/01-demand/…（TreeLearn）_V3.0.md:70`
    这类中文名引用曾经被旧字符类（多排除了 `（），、；`）整条吞掉，而「不被计入的引用改错了
    不会有任何命令变红」正是本片要消掉的那件事（`ROADMAP.md` D5 那条回写记过同形事故）。
    """
    out = []
    for m in CITATION_RE.finditer(text):
        rel, lo, hi = m.group(1), int(m.group(2)), m.group(3)
        out.append((rel, lo, int(hi) if hi else lo))
    return out


def citation_problems(citations, root=None):
    """返回 [(citation, 原因)]：路径不在场、行号超出该文件实际行数、起止倒置。

    只判形状（指没指到、越没越界、反没反序），不判语义——「行号还在但那一行已经不是当初
    说的那件事」要对着 diff 复算，那是 `.codex/scripts/dev/remap_pointers.py` 的活。
    """
    problems = []
    counts = {}
    for rel, lo, hi in citations:
        if not exists(rel, root):
            problems.append(((rel, lo, hi), "file absent"))
            continue
        if rel not in counts:
            counts[rel] = len(read_text(rel, root).splitlines())
        n = counts[rel]
        if lo > hi:
            problems.append(((rel, lo, hi), "range reversed"))
        elif hi > n:
            problems.append(((rel, lo, hi), "line beyond eof={0}".format(n)))
    return problems


def blank_landings(citations, root=None):
    """返回落在空行上的声称：读者按行号到达时看到的是一段空白。

    `citation_problems` 判不出这一类——行号在文件范围内，形状完全合法，但那一行没有正文。
    片 034 的形状扫描实测到它（当前态 0 处、历史面 7 处），这里升成常驻判据。
    """
    bad = []
    counts = {}
    for rel, lo, _hi in citations:
        if not exists(rel, root):
            continue
        if rel not in counts:
            counts[rel] = read_text(rel, root).splitlines()
        lines = counts[rel]
        if 1 <= lo <= len(lines) and not lines[lo - 1].strip():
            bad.append((rel, lo, _hi))
    return bad



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
