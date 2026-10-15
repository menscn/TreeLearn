"""设计 token 四方一致性守门：原型 :root ↔ tokens.json ↔ Android colors.xml ↔ 前端 tokens.css。

用法（从任意目录）：
    python .trae/scripts/dev/check_design_tokens.py            # 校验真实文件
    python .trae/scripts/dev/check_design_tokens.py --selftest # 正反双向自检（不需要真实文件）

退出码：0 全过；1 有断言失败。

为什么需要它：原型 HTML 是设计事实源，tokens.json 是给 Figma 与 Android 共用的机器可读镜像，
colors.xml 是 Android 侧副本，tokens.css 是前端侧副本（只能由 gen_design_tokens_css.py 生成）。
四处各自手写必然漂移，所以把「一致」交给脚本判定，文档只指向本脚本，不再复制一份数值清单。

权威顺序固定为 原型 :root → tokens.json → {colors.xml, tokens.css}；反向改下游一律视为漂移。

前端裸色值守门（同一条口径，不另建平行判据）：`frontend/src/**` 里出现 `#RGB/#RGBA/#RRGGBB/#RRGGBBAA`
即判 FAIL，唯一豁免是生成物 tokens.css 本身，且豁免只来自**文件路径**这一被判对象自身的属性——
不按「距离允许名多少个字符」放行（AGENTS.md 已记录的 ±160 字符窗口打穿形态）。
刻意**不剥离注释、不识别字符串字面量**：一旦让「解析器认为这是注释/字符串」同时充当判据与豁免依据，
解析器就能自证清白（同一根因在本仓库命中过三次）；字符串里的 hex 一律报，因为 React 内联样式
`style={{color:'#FFFFFF'}}` 正是最常见的绕道写法。
"""

import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, os.path.join(REPO_ROOT, ".trae", "scripts"))
sys.path.insert(0, HERE)
from harness_check_lib import Checks, enable_utf8_stdout, repo_path  # noqa: E402
from gen_design_tokens_css import render_css  # noqa: E402

PROTOTYPE = "Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html"
TOKENS = "Docs/04-ux-design/design-system/tokens.json"
COLORS = "android/app/src/main/res/values/colors.xml"
CSS = "frontend/src/styles/tokens.css"
FRONTEND_SRC = "frontend/src"

MIN_TOKENS = 40          # 实测 44 个 :root 自定义属性；低于此数说明解析退化，必须 FAIL
MIN_SELFTEST_CASES = 41  # 与 token 数无关的裸字面量：用例数掉下来必须判 FAIL，否则分支被静默删空
                         # 时守门会「零用例全过」地打印 PASS（本仓库已命中过三次的同型缺陷）。
                         # 增删判据分支时必须显式改这个数，改前先跑 --selftest 读实测值。
ROOT_RE = re.compile(r":root\s*\{(.*?)\n\}", re.S)
VAR_RE = re.compile(r"--([a-z0-9-]+)\s*:\s*([^;{}]+);")
COLOR_RE = re.compile(r'<color\s+name="([^"]+)"\s*>\s*([^<\s]+)\s*</color>')
HEX_RE = re.compile(r"^#[0-9A-Fa-f]{6}$")
# 只认 CSS 真实支持的 3/4/6/8 位形式；`#12345` 这类非颜色片段必须不命中，
# 否则锚点 id、git 短哈希会伪装成「设计数值」。
BARE_HEX_RE = re.compile(r"#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{4}|[0-9A-Fa-f]{3})"
                         r"(?![0-9A-Za-z])")
# 跳过这些扩展名只因它们按字节而非按字符解释：读进去没有「设计数值」可言。
# 这是一条**收录**判据（命中即不扫），不写进去的文件一律照扫，因此新增二进制类型不会静默扩大盲区。
# .svg 刻意**不在**此列：它是文本且 fill 里就是颜色，把它当二进制跳过等于给守门开一个无声的洞。
BINARY_EXT = (".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".bmp", ".avif",
              ".ttf", ".otf", ".woff", ".woff2", ".eot")


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


def compare(root_tokens, tokens, colors, css_tokens):
    """返回失败描述列表；空列表表示四方一致。不读文件，便于自检复用。

    `css_tokens` 传 None 表示第 4 消费者没有可判内容（文件不存在，或存在却解析不出 :root 区块）——
    那必须判 FAIL 而不是跳过：「前端侧副本还没生成」本身就是一个未通过的状态，
    把它读成通过会让整条前端线失去约束。
    """
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

    if css_tokens is None:
        problems.append("tokens.css 没有可判内容（文件不存在，或存在但解析不出 :root 区块）")
    elif len(css_tokens) < MIN_TOKENS:
        problems.append("tokens.css 只解析出 {0} 个 token（< {1}），解析疑似退化".format(
            len(css_tokens), MIN_TOKENS))
    else:
        css_missing = sorted(set(tokens) - set(css_tokens))
        css_extra = sorted(set(css_tokens) - set(tokens))
        if css_missing:
            problems.append("tokens.css 缺少 tokens.json 中的 token：{0}".format(css_missing))
        if css_extra:
            problems.append("tokens.css 多出 tokens.json 没有的 token：{0}".format(css_extra))
        css_drift = ["{0}: tokens.css={1} tokens.json={2}".format(
            name, css_tokens[name], tokens[name])
            for name in sorted(set(tokens) & set(css_tokens))
            if normalize(tokens[name]) != normalize(css_tokens[name])]
        if css_drift:
            problems.append("tokens.css 数值与 tokens.json 不一致：{0}".format(css_drift))
    return problems


def regeneration_problems(tokens, shipped_text):
    """第 4 消费者的「只能生成、不能手抄」判据：重新生成后必须与已提交内容逐字节相同。

    用手抄检查代替字节比对是无效的——错的写法无穷，而相同是唯一可机械裁决的形式。
    生成物刻意不含时间戳，否则这条判据永远红（红到没人把它当判据，就等于没有）。
    `shipped_text` 由调用方保证已读到内容；文件缺失由 compare() 的第 4 消费者分支负责，
    这里不留 None 分支，否则是一条没有生产者的判据。
    """
    expected = render_css(tokens)
    if shipped_text == expected:
        return []
    detail = []
    shipped_lines = shipped_text.splitlines()
    expected_lines = expected.splitlines()
    for i in range(max(len(shipped_lines), len(expected_lines))):
        a = shipped_lines[i] if i < len(shipped_lines) else "<缺行>"
        b = expected_lines[i] if i < len(expected_lines) else "<多行>"
        if a != b:
            detail.append("第 {0} 行 已落盘={1!r} 重新生成={2!r}".format(i + 1, a, b))
            if len(detail) >= 5:
                detail.append("（其余差异略，共 {0} 行可读比对上限）".format(len(detail)))
                break
    return ["tokens.css 与重新生成的结果不逐字节相同（说明有人手改了生成物）：{0}".format(detail)]


def scan_bare_hex(text):
    """在**原文**上扫，不用 CSS AST，也不剥注释、不识别字符串。

    返回 [(行号, 命中片段)]。豁免一律由调用方按文件路径决定，判据本身不接受上下文豁免——
    否则解析器就能自证清白（AGENTS.md 已记录的 ±160 字符窗口打穿形态）。
    """
    hits = []
    for lineno, line in enumerate(text.splitlines(), 1):
        for m in BARE_HEX_RE.finditer(line):
            hits.append((lineno, m.group(0)))
    return hits


def is_scannable(rel):
    """该文件是否要按文本读。命中 BINARY_EXT 即不读；其余一律读（读不动就是缺陷）。"""
    return not rel.lower().endswith(BINARY_EXT)


def bare_hex_checks(sources, frontend_exists):
    """返回 (问题列表, SKIPPED 原因, 实际扫描文件数)。

    `sources` 是 {仓库相对路径: 文本 或 None}；None 表示该文件读不出 UTF-8。
    唯一豁免是生成物 `CSS` 本身，且豁免来自**路径**这个被判对象自身的属性；它被排除后
    不计入扫描数，所以「只扫了生成物」会退化成 scanned==0 → SKIPPED，而不是 PASS。
    """
    if not frontend_exists:
        return [], "frontend/src 目录不存在（尚未生成 tokens.css），该分支本次未执行", 0
    problems = []
    scanned = 0
    for rel in sorted(sources):
        if rel == CSS:
            continue
        scanned += 1
        text = sources[rel]
        if text is None:
            problems.append("{0} 不是可读的 UTF-8 文本：无法判定其色值来源，判 FAIL".format(rel))
            continue
        for lineno, token in scan_bare_hex(text):
            problems.append("{0}:{1} 出现裸色值 {2}（只能引用 var(--token)）".format(rel, lineno, token))
    if scanned == 0:
        return [], "frontend/src 下除生成物 tokens.css 之外没有可扫描文件，扫描未覆盖任何东西", 0
    return problems, None, scanned


def collect_frontend_sources(base=None):
    """按生产形状读 frontend/src：返回 (sources, frontend_exists)。

    frontend_exists 判定用目录本身而不是「sources 非空」——目录在但为空，与目录不存在，
    在「本次到底验了什么」上是两种结论，都必须落到 SKIPPED 而非 PASS。
    """
    root = os.path.join(base or REPO_ROOT, FRONTEND_SRC.replace("/", os.sep))
    if not os.path.isdir(root):
        return {}, False
    sources = {}
    for dir_path, _dirs, names in os.walk(root):
        for name in sorted(names):
            full = os.path.join(dir_path, name)
            rel = os.path.relpath(full, base or REPO_ROOT).replace(os.sep, "/")
            if not is_scannable(rel):
                continue
            try:
                with io.open(full, encoding="utf-8") as handle:
                    sources[rel] = handle.read()
            except (UnicodeDecodeError, OSError):
                sources[rel] = None
    return sources, True


def read(rel):
    with io.open(repo_path(rel), encoding="utf-8") as f:
        return f.read()


def read_verbatim(rel):
    """生成物的比对必须按原字节读：通用换行归一会把 CRLF 文件读成 LF，于是「逐字节相同」
    在换行符差异上失明——那正是手改最容易留下的痕迹之一。"""
    with io.open(repo_path(rel), encoding="utf-8", newline="") as f:
        return f.read()


def selftest(c):
    """正反双向自检：合成输入必须判 PASS，且每一种单点违规都必须被判 FAIL。"""
    executed = [0]

    def _case(cond, label, detail=""):
        executed[0] += 1
        return c.check(cond, label, detail)

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
    # 既有的三方用例把 css 传成 tokens 的镜像：让「第 4 消费者」这一段恒不产生问题，
    # 每用例期望的标签因此只可能来自它自己要验的那一段（期望标签落在错断言上=判据从未生效）。
    _case(compare(root, tokens, colors, dict(tokens)) == [], "selftest: 三方一致必须 PASS")

    m = dict(tokens); m["green"] = "#2E7D50"
    _case(any("原型与 tokens.json 数值不一致" in p for p in compare(root, m, colors, dict(m))),
            "selftest: token 值漂移必须 FAIL")

    m = dict(tokens); m.pop("blue")
    _case(any("缺少原型中的 token" in p for p in compare(root, m, colors, dict(m))),
            "selftest: 缺少 token 必须 FAIL")

    m = dict(tokens); m["invented"] = "#123456"
    _case(any("多出原型没有的 token" in p for p in compare(root, m, colors, dict(m))),
            "selftest: 凭空多出 token 必须 FAIL")

    m = dict(colors); m.pop("tl_blue")
    _case(any("colors.xml 缺少资源" in p for p in compare(root, tokens, m, dict(tokens))),
            "selftest: colors.xml 少资源必须 FAIL")

    m = dict(colors); m["tl_blue"] = "#2F6BFF"; m["tl_orphan"] = "#000000"
    _case(any("无 token 对应的资源" in p for p in compare(root, tokens, m, dict(tokens))),
            "selftest: colors.xml 多资源必须 FAIL")

    m = dict(colors); m["tl_blue"] = "#2F6BFF".lower().replace("2f", "3f")
    _case(any("colors.xml 数值与 token 不一致" in p for p in compare(root, tokens, m, dict(tokens))),
            "selftest: colors.xml 数值漂移必须 FAIL")

    m = dict(tokens)
    for k in list(m):
        if HEX_RE.match(normalize(m[k])):
            m.pop(k)
    _case(any("资源映射无法校验" in p for p in compare(root, m, {}, dict(m))),
            "selftest: 全无色值（解析退化）必须 FAIL")

    _case(any("找不到 :root" in p for p in compare(None, tokens, colors, dict(tokens))),
            "selftest: :root 缺失必须 FAIL")
    _case(parse_root_block(":root{\n  --a:1px;\n  --b:2px;\n}") == {"a": "1px", "b": "2px"},
            "selftest: 解析器能读同一行多条声明")

    # --- 第 4 个消费者：生成的 tokens.css ----------------------------------------------------
    css = dict(root)
    _case(compare(root, tokens, colors, css) == [], "selftest: 四方一致必须 PASS")

    m = dict(css); m["green"] = "#2E7D50"
    _case(any("tokens.css 数值与 tokens.json 不一致" in p for p in compare(root, tokens, colors, m)),
            "selftest: 生成 CSS 数值漂移必须 FAIL")

    m = dict(css); m.pop("blue")
    _case(any("tokens.css 缺少" in p for p in compare(root, tokens, colors, m)),
            "selftest: 生成 CSS 少 token 必须 FAIL")

    m = dict(css); m["invented"] = "#123456"
    _case(any("tokens.css 多出" in p for p in compare(root, tokens, colors, m)),
            "selftest: 生成 CSS 凭空多出 token 必须 FAIL")

    _case(any("tokens.css 没有可判内容" in p for p in compare(root, tokens, colors, None)),
            "selftest: 生成 CSS 文件缺失必须 FAIL 而不是跳过")

    tiny = {"bg": "#FFFFFF", "ink": "#000000"}
    _case(any("tokens.css 只解析出" in p for p in compare(root, tokens, colors, tiny)),
            "selftest: 生成 CSS 解析退化（条目低于下限）必须 FAIL")

    # 判据必须吃生产路径的真实形状：同一段 token 以 :root 原文与生成 CSS 两种形态喂入，
    # 解析结果必须相等；否则探针认证的是生产路径从不使用的形状（AGENTS.md 既有失败形态）。
    rendered = render_css(tokens)
    _case(parse_root_block(rendered) == tokens,
            "selftest: 同一内容以 token 字典与生成 CSS 两种形态喂入必须解析相等")
    _case(render_css(tokens) != render_css(dict(reversed(list(tokens.items())))),
            "selftest: 输出顺序跟随 token 来源顺序，不重排、不排序")
    _case(re.search(r"\d{4}-\d{2}-\d{2}", rendered) is None,
            "selftest: 生成物不得含日期或时间戳（否则「逐字节相同」的漂移校验永远红）")

    # 重新生成比对：健康必须 PASS，且四种「手改」形态必须分别被它自己的标签判红。
    _case(regeneration_problems(tokens, rendered) == [],
            "selftest: 逐字节相同的生成物必须 PASS")
    _case(any("不逐字节相同" in p for p in regeneration_problems(tokens, "")),
            "selftest: 生成物被清空必须判 FAIL（内容归零与删除文件同属漂移）")
    one_char = rendered.replace("#F7F6F0", "#F7F6F1", 1)
    _case(any("不逐字节相同" in p for p in regeneration_problems(tokens, one_char)),
            "selftest: 改动一个字符必须判 FAIL")
    _case(any("不逐字节相同" in p for p in regeneration_problems(tokens, rendered.replace("\n}", "\r\n}"))),
            "selftest: 行尾换行差异也必须判 FAIL（比对不得被换行归一吞掉）")
    _case(any("不逐字节相同" in p for p in regeneration_problems(tokens, rendered + "\n")),
            "selftest: 末尾多一个空行也必须判 FAIL")
    _case(any("不逐字节相同" in p for p in regeneration_problems(
        dict(tokens, **{"green": "#2E7D51"}), rendered)),
            "selftest: 上游 token 改了值而生成物没跟着重生成必须判 FAIL")
    moved = rendered.replace("  --bg: #F7F6F0;\n", "", 1) + "  --bg: #F7F6F0;\n"
    _case(any("不逐字节相同" in p for p in regeneration_problems(tokens, moved)),
            "selftest: 声明顺序被重排必须判 FAIL（顺序也是生成物的一部分）")
    detail = "".join(regeneration_problems(tokens, one_char))
    _case("第 " in detail and "#F7F6F1" in detail,
            "selftest: 漂移判定必须给出可定位的行号与两侧取值", "measured={0}".format(detail[:120]))

    _case(len(scan_bare_hex("a{color:#FF0000}")) == 1, "selftest: 裸 hex 必须被扫出")
    _case(len(scan_bare_hex("a{color:var(--red)}")) == 0, "selftest: var() 引用不得算裸 hex")
    _case(len(scan_bare_hex("a{background:#f7f6f080}")) == 1, "selftest: 8 位 hex 必须被扫出")
    _case(len(scan_bare_hex("a{background:#12345}")) == 0,
            "selftest: 5 位非颜色片段（CSS 不支持）不得误报")
    _case(len(scan_bare_hex("a{color:#FFF3B0 /* 注释里的色 */ }")) == 1,
            "selftest: 注释里的 hex 同样判 FAIL（剥注释会让解析器自证清白）")
    _case(len(scan_bare_hex("h1{content:'#abcdef'}")) == 1,
            "selftest: 字符串字面量里的 hex 必须判 FAIL —— React 内联样式 style={{color:'#FFF'}} 就是字符串，"
                      "豁免字符串等于给守门开主通道")
    hits = scan_bare_hex("a{color:#FF0000}\nb{color:\n  #00FF00;\n}")
    _case([n for n, _ in hits] == [1, 3] and [t for _, t in hits] == ["#FF0000", "#00FF00"],
            "selftest: 命中必须带真实行号（报 1 行会让按行修的人扑空）",
            "measured={0}".format(hits))
    one = "a{color:#FF0000}"
    flat_hits = len(scan_bare_hex(one))
    dict_hits = len(bare_hex_checks({"frontend/src/x.css": one}, True)[0])
    _case(flat_hits == dict_hits,
            "selftest: 同一内容以字符串与文件字典两种形状喂入必须给出相同命中数")
    problems, skipped, scanned = bare_hex_checks({}, False)
    _case(skipped and not problems and scanned == 0,
            "selftest: frontend 不存在必须记 SKIPPED，不得记 PASS 也不得记 FAIL")
    problems, skipped, scanned = bare_hex_checks({"frontend/src/x.css": one}, True)
    _case(not skipped and problems and any("#FF0000" in p for p in problems),
            "selftest: 前端源码裸 hex 必须 FAIL")
    problems, skipped, scanned = bare_hex_checks(
        {CSS: rendered, "frontend/src/x.css": "a{color:var(--green)}"}, True)
    _case(not skipped and not problems and scanned == 1,
            "selftest: 生成的 tokens.css 是唯一豁免文件，且它被扣除出扫描数（豁免来自被判对象自身）")
    problems, skipped, scanned = bare_hex_checks({CSS: rendered}, True)
    _case(skipped and not problems and scanned == 0,
            "selftest: frontend 里除生成物外没有文件必须 SKIPPED，不得因「零命中」判 PASS")
    problems, skipped, scanned = bare_hex_checks({"frontend/src/y.css": None}, True)
    _case(problems and any("UTF-8" in p for p in problems) and scanned == 1,
            "selftest: 读不出 UTF-8 的前端文件必须判 FAIL（不得静默跳过）")
    _case(not is_scannable("frontend/src/assets/logo.png") and is_scannable("frontend/src/a.svg"),
            "selftest: 只有真二进制扩展名不读；SVG 是文本且 fill 里就是颜色，必须照扫")

    # 用例数必须是**恰好**这个数：少一条说明有判据分支被删或从未接入，多一条说明该下限被过期钉住。
    # 用 == 而不是 >= 的理由与 MIN_TOKENS 相反——这里没有「合理增长」的余量，增长必须是一次显式编辑。
    c.check(executed[0] == MIN_SELFTEST_CASES,
            "selftest: 执行用例数恰好 = {0}（增删判据都要显式改这个数）".format(MIN_SELFTEST_CASES),
            "measured={0}".format(executed[0]))
    print("selftest judgement cases executed = {0}".format(executed[0]))
    return c



def main():
    enable_utf8_stdout()
    if "--selftest" in sys.argv[1:]:
        return selftest(Checks("check_design_tokens --selftest")).finish()

    c = Checks("check_design_tokens")
    # 判定行计数：SKIPPED 必须能从计数里被看见并区别于 OK，否则「零命中所以通过」
    # 与「什么都没扫所以没验证」在产物里长得一模一样（本仓库既有的假见证形态）。
    tally = {"OK": 0, "FAIL": 0, "SKIPPED": 0}

    def check(cond, label, detail=""):
        tally["OK" if cond else "FAIL"] += 1
        return c.check(cond, label, detail)

    def ok(label, detail=""):
        tally["OK"] += 1
        c.ok(label, detail)

    def fail(label, detail=""):
        tally["FAIL"] += 1
        c.fail(label, detail)

    for rel in (PROTOTYPE, TOKENS, COLORS):
        if not check(os.path.exists(repo_path(rel)), "file exists: " + rel):
            return c.finish()

    root_tokens = parse_root_block(read(PROTOTYPE))
    tokens, dupes = parse_tokens_json(read(TOKENS))
    colors = parse_colors_xml(read(COLORS))
    # 生成物存在与否不在这里提前 return：缺失是**判定**（FAIL），不是「没跑成」（跳过）。
    css_text = read_verbatim(CSS) if os.path.exists(repo_path(CSS)) else None
    css_tokens = None if css_text is None else parse_root_block(css_text)

    check(not dupes, "tokens.json token 名唯一", "measured_dupes={0}".format(dupes))
    check(root_tokens is not None and len(root_tokens) >= MIN_TOKENS,
          "原型 :root 解析出 >= {0} 个 token".format(MIN_TOKENS),
          "measured={0}".format(None if root_tokens is None else len(root_tokens)))
    check(len(tokens) >= MIN_TOKENS, "tokens.json 解析出 >= {0} 个 token".format(MIN_TOKENS),
          "measured={0}".format(len(tokens)))
    check(len(colors) > 0, "colors.xml 解析出 >= 1 个资源",
          "measured={0}".format(len(colors)))
    check(css_tokens is not None, "tokens.css 存在且解析出 :root 区块",
          "measured={0}".format(None if css_tokens is None else len(css_tokens)))

    problems = compare(root_tokens, tokens, colors, css_tokens)
    # 门控用「读到内容没有」而不是「解析出 token 没有」：文件在但 :root 被写坏时，
    # 逐字节比对仍能给出行号，而以解析结果门控会把它整段跳过（= 只剩一条含糊的缺失结论）。
    if css_text is not None:
        problems += regeneration_problems(tokens, css_text)
    if problems:
        for problem in problems:
            fail("四方一致", problem)
    else:
        ok("四方一致（原型 :root ↔ tokens.json ↔ colors.xml ↔ 生成的 tokens.css，含逐字节重新生成比对）",
           "measured_prototype={0} measured_tokens_json={1} measured_resources={2} measured_css={3}".format(
               len(root_tokens), len(tokens), len(colors), len(css_tokens)))

    sources, frontend_exists = collect_frontend_sources()
    hex_problems, skipped_reason, scanned = bare_hex_checks(sources, frontend_exists)
    if skipped_reason:
        # 单独打印 SKIPPED 行而不是 OK 行：让「按 OK 行数统计通过项」的下游不会把它算成一次通过。
        tally["SKIPPED"] += 1
        print("SKIPPED 前端裸色值守门 — 原因：{0}".format(skipped_reason))
        print("        本轮扫描文件数={0}（该分支未覆盖任何东西，不计入通过）".format(scanned))
    elif hex_problems:
        for problem in hex_problems:
            fail("前端无裸色值", problem)
    else:
        ok("前端无裸色值（frontend/src 全量原文扫描）",
           "measured_files={0} measured_hits=0".format(scanned))
    print("judgement tally: OK={OK} FAIL={FAIL} SKIPPED={SKIPPED}".format(**tally))
    return c.finish()


if __name__ == "__main__":
    sys.exit(main())