"""设计 token 三方一致性守门：原型 HTML 的 :root ↔ tokens.json ↔ Android colors.xml。

用法（从任意目录）：
    python .trae/scripts/dev/check_design_tokens.py            # 校验真实文件
    python .trae/scripts/dev/check_design_tokens.py --selftest # 正反双向自检（不需要真实文件）

退出码：0 全过；1 有断言失败。

为什么需要它：原型 HTML 是设计事实源，tokens.json 是给 Figma 与 Android 共用的机器可读镜像，
colors.xml 是 Android 侧副本。三处各自手写必然漂移，所以把「一致」交给脚本判定，
文档只指向本脚本，不再复制一份数值清单。
"""

import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, os.path.join(REPO_ROOT, ".trae", "scripts"))
from harness_check_lib import Checks, enable_utf8_stdout, repo_path  # noqa: E402

PROTOTYPE = "Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html"
TOKENS = "Docs/04-ux-design/design-system/tokens.json"
COLORS = "android/app/src/main/res/values/colors.xml"

MIN_TOKENS = 40          # 实测 42 个 :root 自定义属性；低于此数说明解析退化，必须 FAIL
ROOT_RE = re.compile(r":root\s*\{(.*?)\n\}", re.S)
VAR_RE = re.compile(r"--([a-z0-9-]+)\s*:\s*([^;{}]+);")
COLOR_RE = re.compile(r'<color\s+name="([^"]+)"\s*>\s*([^<\s]+)\s*</color>')
HEX_RE = re.compile(r"^#[0-9A-Fa-f]{6}$")


def normalize(value):
    """只做「同一数值的不同写法」归一：去空白、hex 统一大写。

    刻意不归一 rgba 的小数点写法（`.075` 与 `0.075` 仍视为不同），
    因为那正是会漂移的地方，宁可报出来也不要被吞掉。
    """
    v = re.sub(r"\s+", "", value)
    return v.upper() if v.startswith("#") else v


def parse_root_block(text):
    m = ROOT_RE.search(text)
    if not m:
        return None
    out = {}
    for name, value in VAR_RE.findall(m.group(1)):
        out[name] = value.strip()
    return out


def parse_tokens_json(text):
    """结构：分组 -> 条目 -> {value, note}。返回 {token名: 值} 与重复名列表。"""
    data = json.loads(text)
    flat, dupes = {}, []
    for group, entries in data.items():
        if group.startswith("$") or not isinstance(entries, dict):
            continue
        for name, entry in entries.items():
            if not isinstance(entry, dict) or "value" not in entry:
                continue
            if name in flat:
                dupes.append(name)
            flat[name] = entry["value"]
    return flat, dupes


def parse_colors_xml(text):
    return dict(COLOR_RE.findall(text))


def resource_name(token):
    """--green-deep -> tl_green_deep（确定性映射，不手写清单）。"""
    return "tl_" + token.replace("-", "_")


def compare(root_tokens, tokens, colors):
    """返回失败描述列表；空列表表示三方一致。不读文件，便于自检复用。"""
    problems = []
    if root_tokens is None:
        return ["原型 HTML 中找不到 :root 区块"]
    if len(root_tokens) < MIN_TOKENS:
        problems.append("原型 :root 只解析出 {0} 个 token（< {1}），解析疑似退化".format(
            len(root_tokens), MIN_TOKENS))
    if len(tokens) < MIN_TOKENS:
        problems.append("tokens.json 只解析出 {0} 个 token（< {1}）".format(
            len(tokens), MIN_TOKENS))

    missing = sorted(set(root_tokens) - set(tokens))
    extra = sorted(set(tokens) - set(root_tokens))
    if missing:
        problems.append("tokens.json 缺少原型中的 token：{0}".format(missing))
    if extra:
        problems.append("tokens.json 多出原型没有的 token：{0}".format(extra))

    drifted = []
    for name in sorted(set(root_tokens) & set(tokens)):
        if normalize(root_tokens[name]) != normalize(tokens[name]):
            drifted.append("{0}: 原型={1} tokens.json={2}".format(
                name, root_tokens[name], tokens[name]))
    if drifted:
        problems.append("原型与 tokens.json 数值不一致：{0}".format(drifted))

    opaque = {n: v for n, v in tokens.items() if HEX_RE.match(normalize(v))}
    if not opaque:
        problems.append("tokens.json 里没有一个不透明 hex 颜色，资源映射无法校验")
    else:
        expected = {resource_name(n): v for n, v in opaque.items()}
        absent = sorted(set(expected) - set(colors))
        if absent:
            problems.append("colors.xml 缺少资源：{0}".format(absent))
        dangling = sorted(set(colors) - set(expected))
        if dangling:
            problems.append("colors.xml 存在无 token 对应的资源：{0}".format(dangling))
        mismatched = ["{0}: colors.xml={1} 应为 {2}".format(r, colors[r], v)
                      for r, v in sorted(expected.items())
                      if r in colors and normalize(colors[r]) != normalize(v)]
        if mismatched:
            problems.append("colors.xml 数值与 token 不一致：{0}".format(mismatched))
    return problems


def read(rel):
    with io.open(repo_path(rel), encoding="utf-8") as f:
        return f.read()


def selftest(c):
    """正反双向自检：合成输入必须判 PASS，且每一种单点违规都必须被判 FAIL。"""
    root = {"bg": "#F7F6F0", "green-deep": "#1F5C38", "sh-1": "0 1px 2px rgba(30,43,35,.04)",
            "kai": "'LXGW WenKai',serif", "s1": "4px", "r-lg": "24px",
            "ink": "#1E2B23", "line": "#E6E3D6", "blue": "#2F6BFF", "amber": "#B7791F",
            "red": "#C0392B", "surface": "#FFFFFF", "green": "#2E7D4F", "green-soft": "#E3F0E7",
            "green-faint": "#F0F7F1", "amber-soft": "#FBF3E2", "red-soft": "#FBEAE7",
            "ink2": "#5A6B60", "ink3": "#8A938E", "hairline": "rgba(30,43,35,.075)"}
    root = dict(root)
    # 合成集合必须自身满足解析下限，否则「一致 -> PASS」这条会被下限断言误杀
    for i in range(MIN_TOKENS + 1 - len(root)):
        root["pad-{0}".format(i)] = "{0}px".format(i)
    tokens = dict(root)
    colors = {resource_name(n): v for n, v in root.items() if HEX_RE.match(v)}
    c.check(compare(root, tokens, colors) == [], "selftest: 三方一致必须 PASS")

    m = dict(tokens); m["green"] = "#2E7D50"
    c.check(any("数值不一致" in p for p in compare(root, m, colors)),
            "selftest: token 值漂移必须 FAIL")

    m = dict(tokens); m.pop("blue")
    c.check(any("缺少原型中的 token" in p for p in compare(root, m, colors)),
            "selftest: 缺少 token 必须 FAIL")

    m = dict(tokens); m["invented"] = "#123456"
    c.check(any("多出原型没有的 token" in p for p in compare(root, m, colors)),
            "selftest: 凭空多出 token 必须 FAIL")

    m = dict(colors); m.pop("tl_blue")
    c.check(any("colors.xml 缺少资源" in p for p in compare(root, tokens, m)),
            "selftest: colors.xml 少资源必须 FAIL")

    m = dict(colors); m["tl_blue"] = "#2F6BFF"; m["tl_orphan"] = "#000000"
    c.check(any("无 token 对应的资源" in p for p in compare(root, tokens, m)),
            "selftest: colors.xml 多资源必须 FAIL")

    m = dict(colors); m["tl_blue"] = "#2F6BFF".lower().replace("2f", "3f")
    c.check(any("colors.xml 数值与 token 不一致" in p for p in compare(root, tokens, m)),
            "selftest: colors.xml 数值漂移必须 FAIL")

    m = dict(tokens)
    for k in list(m):
        if HEX_RE.match(normalize(m[k])):
            m.pop(k)
    c.check(any("资源映射无法校验" in p for p in compare(root, m, {})),
            "selftest: 全无色值（解析退化）必须 FAIL")

    c.check(any("找不到 :root" in p for p in compare(None, tokens, colors)),
            "selftest: :root 缺失必须 FAIL")
    c.check(parse_root_block(":root{\n  --a:1px;\n  --b:2px;\n}") == {"a": "1px", "b": "2px"},
            "selftest: 解析器能读同一行多条声明")
    return c


def main():
    enable_utf8_stdout()
    if "--selftest" in sys.argv[1:]:
        return selftest(Checks("check_design_tokens --selftest")).finish()

    c = Checks("check_design_tokens")
    for rel in (PROTOTYPE, TOKENS, COLORS):
        if not c.check(os.path.exists(repo_path(rel)), "file exists: " + rel):
            return c.finish()

    root_tokens = parse_root_block(read(PROTOTYPE))
    tokens, dupes = parse_tokens_json(read(TOKENS))
    colors = parse_colors_xml(read(COLORS))

    c.check(not dupes, "tokens.json token 名唯一", "measured_dupes={0}".format(dupes))
    c.check(root_tokens is not None and len(root_tokens) >= MIN_TOKENS,
            "原型 :root 解析出 >= {0} 个 token".format(MIN_TOKENS),
            "measured={0}".format(None if root_tokens is None else len(root_tokens)))
    c.check(len(tokens) >= MIN_TOKENS, "tokens.json 解析出 >= {0} 个 token".format(MIN_TOKENS),
            "measured={0}".format(len(tokens)))
    c.check(len(colors) > 0, "colors.xml 解析出 >= 1 个资源",
            "measured={0}".format(len(colors)))

    problems = compare(root_tokens, tokens, colors)
    if problems:
        for problem in problems:
            c.fail("三方一致", problem)
    else:
        c.ok("三方一致（原型 :root ↔ tokens.json ↔ colors.xml）",
             "measured_tokens={0} measured_resources={1}".format(len(tokens), len(colors)))
    return c.finish()


if __name__ == "__main__":
    sys.exit(main())