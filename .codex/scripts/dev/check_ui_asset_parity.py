#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把「Web／exe 与 Android 的界面资产是否真的等价」量成可复核的判决。

用法（从任意目录）：
    python -X utf8 .codex/scripts/dev/check_ui_asset_parity.py             # 判仓库当前态
    python -X utf8 .codex/scripts/dev/check_ui_asset_parity.py --selftest  # 判仪器自己

退出码（与仓库其余守门同一纪律）：0 判据全成立；1 有判据 FAIL；
2 用法错误；3 仪器故障（权威文件读不到、tokens.css 里找不到 --serif）。

为什么要它：2026-10-25 评审抓到一条**只有代码能看见**的严重项——APK 里放着
24,427,224 字节的 `noto_serif_sc.otf`，而同一时刻 `frontend/dist-offline/index.html`
里 `@font-face` 出现 0 次（519,350 字节，字体 CDN 也是 0 次）。没装 Noto Serif SC 的
Windows——正是 1.0.0 便携 exe 的目标机——会沿 `'Noto Serif SC','Songti SC',Georgia,
serif` 落到 generic serif，也就是宋体。同一份 tokens.json，两端两种字面。
仓库当时没有任何守门会报警：颜色有 `check_design_tokens.py`，玻璃深度有
`check_glass_depth.py`，随包字体与类名存活率没有。这条守门补的就是这两个洞。

判据（编号与 T-2026-10-25-087/089/092 子卡一致）：
  A1 衬线身份必须随包。`--serif` 的第一个引号族名（=设计意图的那个字面）必须在
     `frontend/src/styles/*.css` 里有一处 `@font-face` 声明它，`src` 只能是 `data:`
     或包内相对路径，且相对路径指向的文件必须真实存在。
  A2 兜底栈必须含一个 Windows 真实存在的中文衬线。栈里只写 Google/Apple 的族名，
     等于把最终字面交给宿主的随机库存；补一个明确的 Windows 族名是把「降级」变成
     「决定」。白名单见 WIN_SERIF_FAMILIES。
  A3 样式表里的类名必须有消费者。死样式表不只是体积：`frontend/src/styles/app.css`
     （425 行，`main.tsx:11` 仍在 import）让已声明放弃的 `--kai` 一直挂着两个假消费者
     （`appbar-brand`、`chat-answer-run`），任何按 CSS grep 判字体存活率的守门都会
     因此误报，退役永远做不掉。
  A4 随包字体字节必须有上限。子集内嵌是把体积从 CDN 换进包里，没有上限的 A1
     等于允许把 23 MiB 全量 otf 直接 base64 进去——离线包会从 0.50 MiB 涨两个量级。
     没有任何内联字体时打 NOTE 而不是第二条红：同一个成因只能由 A1 判一次。
  A5 产物面必须与源面同字面（`frontend/dist-offline/index.html` 存在才判，
     缺文件打 NOTE——core 档不依赖构建产物，precommit 档能判到它）。
  A6 参照面：Android 那份字体文件必须还在。两端都没有字面时这条先红，
     防止「删掉 APK 字体来让 Web 与 Android 一致」这种反向达标。
  A7 图标两端同源且画的是它声称的东西。`chat_gear` 曾是两端逐字相同的一条
     「中心圆 + 八条放射短线」——一致，但那是太阳。这条比对两端 pathData
     归一化后逐字相同，并要求轮廓顶点数够多（齿要有齿顶，不能退回放射线）。
"""

import argparse
import base64
import io
import os
import re
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
sys.path.insert(0, os.path.join(REPO_ROOT, ".codex", "scripts"))
sys.path.insert(0, HERE)
from harness_check_lib import Checks, enable_utf8_stdout  # noqa: E402

CHECK_NAME = "check_ui_asset_parity"
EXIT_USAGE = 2
EXIT_FAULT = 3

TOK_CSS = "frontend/src/styles/tokens.css"
STYLE_DIR = "frontend/src/styles"
SRC_DIR = "frontend/src"
BUNDLE = "frontend/dist-offline/index.html"
ANDROID_FONT = "android/app/src/main/res/font/noto_serif_sc.otf"
ICON_TSX = "frontend/src/components/Icon.tsx"
GEAR_XML = "android/app/src/main/res/drawable/ic_chat_gear.xml"
# 2026-10-25 之前两端共用的那条 pathData 的签名：一条圆心路径 + 一条八向放射线。
SUN_SIGNATURE = "12 2.5v3"
MIN_GEAR_VERTICES = 24

# Windows 自带、且确实是中文衬线（宋体族）的族名。楷体/仿宋/黑体/雅黑不算：
# 前者是本项目显式放弃的身份（见 tokens.json 的 kai note），后两者是无衬线，
# 写进兜底栈等于换了设计意图，A2 会把它当糊弄判红。
WIN_SERIF_FAMILIES = ("SimSun", "NSimSun", "SimSun-ExtB", "宋体")

# A4 上限：GB2312 常用 6763 字 + 拉丁与标点的子集口径是 3–5 MiB，留一档余量到 6 MiB。
# 超过就判红——那意味着有人把全量 otf（实测 24,427,224 字节）直接内联了。
INLINE_FONT_MAX = 6 * 1024 * 1024

# A3 的显式豁免：这些类名不是死的，而是运行时拼出来或宿主注入的。
# 每条必须带 >8 字的理由（自检守这条），空表是常态而不是缺陷。
DEAD_CLASS_ALLOWLIST = {}

CSS_CLASS_RE = re.compile(r"(?<![\w-])\.([A-Za-z_][\w-]*)")
FONT_FACE_RE = re.compile(r"@font-face\s*\{([^}]*)\}", re.S)
FAMILY_TOKEN_RE = re.compile(r"--serif\s*:\s*([^;]+);")
QUOTED_RE = re.compile(r"'([^']+)'|\"([^\"]+)\"")
URL_RE = re.compile(r"url\(\s*(?:'([^']+)'|\"([^\"]+)\"|([^')\s]+))\s*\)")
BASE64_RE = re.compile(r"data:font/[^;]*;base64,([A-Za-z0-9+/=]+)")
EXTERNAL_FONT_RE = re.compile(r"https?://[^\s\"')]*\.(?:woff2?|ttf|otf)", re.I)

SELFTEST_MIN_CASES = 9  # 用例数掉下来说明有分支被静默删空，同型缺陷仓库里命中过三次


def files_with_suffix(rel_dir, suffixes, root=None):
    """列出目录下的文件（仓库相对路径、正斜杠、已排序），不跟构建产物目录。"""
    base = os.path.join(root or REPO_ROOT, rel_dir.replace("/", os.sep))
    out = []
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = [d for d in dirnames
                       if d not in ("node_modules", "__pycache__", "dist", "dist-offline")]
        for name in sorted(filenames):
            if name.endswith(suffixes):
                out.append(os.path.relpath(os.path.join(dirpath, name), root or REPO_ROOT)
                           .replace(os.sep, "/"))
    return sorted(out)


def read_rel(rel, root=None):
    with io.open(os.path.join(root or REPO_ROOT, rel.replace("/", os.sep)), encoding="utf-8") as fh:
        return fh.read()


def exists_rel(rel, root=None):
    return os.path.exists(os.path.join(root or REPO_ROOT, rel.replace("/", os.sep)))


def put_rel(root, rel, text):
    path = os.path.join(root, rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8") as fh:
        fh.write(text)
    return path


def serif_stack(tokens_text):
    """从 tokens.css 取 --serif 的栈；取不到就是仪器故障，不许当成「产物判红」。"""
    m = FAMILY_TOKEN_RE.search(tokens_text)
    if not m:
        raise IOError("%s 里找不到 --serif 声明" % TOK_CSS)
    return m.group(1).strip()


def stack_parts(stack):
    return [p.strip() for p in stack.split(",") if p.strip()]


def first_family(stack):
    """设计意图的那个字面 = 栈里第一个引号族名；裸 generic 关键字不算声明了身份。"""
    for part in stack_parts(stack):
        m = QUOTED_RE.match(part)
        if m:
            return m.group(1) or m.group(2)
    return None


def font_faces(text, origin=""):
    """解析 @font-face。

    `src` 取「从 src: 到块尾」而不是「到第一个分号」：`data:font/woff2;base64,...`
    里本身就带分号，按分号截断会把 6 MiB 的载荷读成 24 个字符，A4 的上限判据
    会因此永远读到一个极小的数而长期判绿——自检的「超过上限必须判红」那条负例
    守的就是这个坑。src 之后若还跟着别的声明，多读到的部分不会造出假的 url()。
    """
    faces = []
    for body in FONT_FACE_RE.findall(text):
        fam = re.search(r"font-family\s*:\s*([^;]+);", body)
        src = re.search(r"src\s*:", body)
        faces.append({
            "family": (fam.group(1).strip().strip("'\"") if fam else ""),
            "src": body[src.start():] if src else "",
            "file": origin,
        })
    return faces


def inlined_font_bytes(texts):
    total = 0
    for text in texts:
        for m in BASE64_RE.finditer(text):
            payload = m.group(1)
            total += len(base64.b64decode(payload + "=" * (-len(payload) % 4)))
    return total


def all_refs(faces):
    """返回 [(来源文件, 引用串)]，不区分 data: 与路径——由调用方分类。"""
    refs = []
    for face in faces:
        for m in URL_RE.finditer(face["src"]):
            refs.append((face["file"], next(g for g in m.groups() if g)))
    return refs


def is_remote(ref):
    return ref.startswith(("http://", "https://", "//"))


def consumers_haystack(root):
    """把所有「可能写类名」的字面量摊平成一个空格分隔的串。

    宁可宽（连 `className={` 这种片段也进来了）也不要窄：漏一个消费者就会误报死码，
    误报死码的下一步通常是把判据放宽，那才是这条守门真正的失效方式。
    """
    blobs = [read_rel(rel, root) for rel in files_with_suffix(SRC_DIR, (".tsx", ".ts"), root)]
    for html in files_with_suffix("frontend", (".html",), root):
        if "dist-offline" in html:
            continue
        blobs.append(read_rel(html, root))
    return set(re.sub(r"[^A-Za-z0-9_-]", " ", "\n".join(blobs)).split())


def dead_classes(root):
    """返回 [(样式表, 类名)]，以及扫描过的样式表张数。"""
    tokens = consumers_haystack(root)
    dead = []
    scanned = 0
    for rel in files_with_suffix(STYLE_DIR, (".css",), root):
        if rel.endswith("tokens.css"):
            continue  # 四方链生成物，类名不是它的职责
        text = read_rel(rel, root)
        scanned += 1
        for name in sorted(set(CSS_CLASS_RE.findall(text))):
            if name in DEAD_CLASS_ALLOWLIST or name in tokens:
                continue
            dead.append((rel, name))
    return dead, scanned


def gear_paths(root):
    """从两端取出 `chat_gear` 的 pathData 列表；缺文件返回 None（判 NOTE，不判红）。"""
    if not (exists_rel(ICON_TSX, root) and exists_rel(GEAR_XML, root)):
        return None
    tsx = read_rel(ICON_TSX, root)
    block = re.search(r"chat_gear:\s*\((.*?)\),", tsx, re.S)
    if block is None:
        return None
    svg = [m.group(1) for m in re.finditer(r'<path\s+d="([^"]+)"', block.group(1))]
    xml = [m.group(1) for m in re.finditer(r'android:pathData="([^"]+)"', read_rel(GEAR_XML, root))]
    return ([normalize_path(p) for p in svg], [normalize_path(p) for p in xml])


def normalize_path(data):
    """逗号与多余空白不进比对：两端书写习惯不同，但几何必须是同一条。"""
    return " ".join(data.replace(",", " ").split())


def judge(ck, root=None):
    tokens_text = read_rel(TOK_CSS, root)
    stack = serif_stack(tokens_text)
    wanted = first_family(stack)
    if wanted is None:
        raise IOError("%s 的 --serif 栈里没有带引号的族名，无法确定设计意图的字面" % TOK_CSS)

    style_files = [rel for rel in files_with_suffix(STYLE_DIR, (".css",), root)
                   if not rel.endswith("tokens.css")]
    faces = []
    for rel in style_files:
        faces.extend(font_faces(read_rel(rel, root), rel))

    matching = [f for f in faces if f["family"] == wanted]
    if not matching:
        ck.fail("A1 衬线身份随包",
                "--serif 首选族 %r 在 %s 没有任何 @font-face（当前栈：%s）" % (wanted, STYLE_DIR, stack))
    else:
        refs = all_refs(matching)
        empty = [f["file"] for f in matching if not f["src"] or not URL_RE.search(f["src"])]
        remote = [(f, r) for f, r in refs if is_remote(r)]
        local = [(f, r) for f, r in refs if not is_remote(r) and not r.startswith("data:")]
        missing = [(f, r) for f, r in local
                   if not exists_rel(os.path.join(os.path.dirname(f), r).replace(os.sep, "/"), root)]
        if empty:
            ck.fail("A1 衬线身份随包", "%r 的 @font-face 里没有任何 src url()：%s" % (wanted, ", ".join(empty)))
        elif remote:
            ck.fail("A1 衬线身份随包",
                    "src 指向远端，产物离线时拿不到：%s" % ", ".join("%s→%s" % pair for pair in remote))
        elif missing:
            ck.fail("A1 衬线身份随包", "src 指向的包内文件不存在：%s" % ", ".join(
                "%s→%s" % pair for pair in missing))
        else:
            ck.ok("A1 衬线身份随包", "%r 有 %d 处 @font-face，src 全部可离线拿到" % (wanted, len(matching)))

    names = [p.strip("'\"").lower() for p in stack_parts(stack)]
    hit = next((w for w in WIN_SERIF_FAMILIES if w.lower() in names), None)
    if hit is None:
        ck.fail("A2 兜底栈含 Windows 中文衬线",
                "栈里没有任何 %s；没装首选族的机器会落到 generic serif，最终字面由宿主决定" % (list(WIN_SERIF_FAMILIES),))
    else:
        ck.ok("A2 兜底栈含 Windows 中文衬线", "命中 %s" % hit)

    dead, scanned = dead_classes(root)
    if dead:
        grouped = {}
        for rel, name in dead:
            grouped.setdefault(rel, []).append(name)
        ck.fail("A3 样式表类名有消费者", "扫描 %d 张样式表，%s" % (
            scanned, "；".join("%s：%d 个无消费者（%s…）" % (rel, len(ns), ", ".join(ns[:6]))
                              for rel, ns in sorted(grouped.items()))))
    else:
        ck.ok("A3 样式表类名有消费者", "扫描 %d 张样式表，显式豁免 %d 条" % (scanned, len(DEAD_CLASS_ALLOWLIST)))

    size_texts = [f["src"] for f in matching]
    bundle_rel = BUNDLE
    if exists_rel(bundle_rel, root):
        size_texts.append(read_rel(bundle_rel, root))
    inlined = inlined_font_bytes(size_texts)
    if inlined == 0:
        ck.note("A4 随包字体字节有上限",
                "没有任何内联字体字节，无从判上限（该缺陷已由 A1 判红，不重复计一条）")
    elif inlined > INLINE_FONT_MAX:
        ck.fail("A4 随包字体字节有上限", "内联字体 %s 字节 > 上限 %s" % (inlined, INLINE_FONT_MAX))
    else:
        ck.ok("A4 随包字体字节有上限", "内联字体 %s 字节 ≤ 上限 %s" % (inlined, INLINE_FONT_MAX))

    if not exists_rel(bundle_rel, root):
        ck.note("A5 产物面与源面同字面", "%s 不存在：本轮没判（core 档不依赖构建产物）" % bundle_rel)
    else:
        html = read_rel(bundle_rel, root)
        has_face = any(f["family"] == wanted for f in font_faces(html, bundle_rel))
        cdn = EXTERNAL_FONT_RE.findall(html)
        if has_face and not cdn:
            ck.ok("A5 产物面与源面同字面", "%s 的 @font-face 在包内，外部字体引用 0 处" % wanted)
        else:
            ck.fail("A5 产物面与源面同字面",
                    "产物面 %s：@font-face %s，外部字体引用 %d 处" % (
                        wanted, "有" if has_face else "没有", len(cdn)))

    if exists_rel(ANDROID_FONT, root):
        ck.ok("A6 参照面 Android 字体仍在", "%s %s 字节" % (
            ANDROID_FONT, os.path.getsize(os.path.join(root or REPO_ROOT, ANDROID_FONT.replace("/", os.sep)))))
    else:
        ck.fail("A6 参照面 Android 字体仍在",
                "%s 不见了：两端都没字面不是「一致」，是删除式达标" % ANDROID_FONT)

    pair = gear_paths(root)
    if pair is None:
        ck.note("A7 齿轮图标两端同源", "%s / %s 缺一，本轮没判" % (ICON_TSX, GEAR_XML))
    else:
        svg, xml = pair
        outline = next((p for p in svg if p.count("L") >= MIN_GEAR_VERTICES), "")
        if not outline:
            ck.fail("A7 齿轮图标两端同源",
                    "Web 侧没有一条顶点数 ≥ %d 的齿轮轮廓：放射线画的是太阳" % MIN_GEAR_VERTICES)
        elif SUN_SIGNATURE in outline:
            ck.fail("A7 齿轮图标两端同源", "Web 侧仍是旧的八向放射线 pathData")
        elif svg != xml:
            ck.fail("A7 齿轮图标两端同源",
                    "两端 pathData 不等价：Web %d 条，Android %d 条，首条差异在 %r vs %r" % (
                        len(svg), len(xml), svg[0][:40] if svg else "", xml[0][:40] if xml else ""))
        else:
            ck.ok("A7 齿轮图标两端同源", "%d 条 pathData 逐字相同，轮廓 %d 个顶点" % (
                len(svg), outline.count("L")))


def selftest(ck):
    cases = []
    base_tokens = "--serif: 'Noto Serif SC','Songti SC',Georgia,serif%s;"

    def run(stack_extra="", face_src=""):
        tmp = tempfile.mkdtemp(prefix="uap-selftest-")
        try:
            put_rel(tmp, TOK_CSS, base_tokens % stack_extra)
            if face_src:
                put_rel(tmp, STYLE_DIR + "/a.css",
                        "@font-face{font-family:'Noto Serif SC';src:%s}" % face_src)
            put_rel(tmp, STYLE_DIR + "/live.css", ".live{color:red}")
            put_rel(tmp, SRC_DIR + "/App.tsx", 'export const A = () => <div className="live" />;')
            return judge_quiet(tmp)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    def judge_quiet(root):
        sink = Checks("fixture")
        original = sys.stdout
        sys.stdout = io.StringIO()
        try:
            judge(sink, root)
        finally:
            sys.stdout = original
        return sink.failures

    fails = run()
    cases.append(("A1 没有 @font-face 时必须判红", "A1 衬线身份随包" in fails))
    cases.append(("A3 在全夹具绿时不得误报（活类名不能被读成死码）", "A3 样式表类名有消费者" not in fails))

    fails = run(face_src="url('https://fonts.example/x.woff2')")
    cases.append(("A1 把 CDN 当随包字体时必须判红", "A1 衬线身份随包" in fails))

    fails = run(face_src="url('fonts/subset.woff2')")
    cases.append(("A1 包内相对路径指向不存在的文件时必须判红", "A1 衬线身份随包" in fails))
    cases.append(("A1 的 CDN 负例不得同时把 A4 判成沉默以外的形状",
                  "A4 随包字体字节有上限" not in fails))

    fails = run(face_src="local('宋体')")
    cases.append(("A1 的 src 里一个 url() 都没有时必须判红而不是判绿", "A1 衬线身份随包" in fails))

    small = base64.b64encode(b"x" * 4096).decode()
    big = base64.b64encode(b"\0" * (INLINE_FONT_MAX + 1024)).decode()
    fails = run(face_src="url(data:font/woff2;base64,%s)" % big)
    cases.append(("A4 超过上限时必须判红而不是沉默", "A4 随包字体字节有上限" in fails))
    fails = run(face_src="url(data:font/woff2;base64,%s)" % small)
    cases.append(("A4 在上限内时必须转绿", "A4 随包字体字节有上限" not in fails))
    cases.append(("A1 在 data: 内联后必须转绿", "A1 衬线身份随包" not in fails))

    fails = run(stack_extra=",'SimSun'", face_src="url(data:font/woff2;base64,%s)" % small)
    cases.append(("补上 Windows 中文衬线后 A2 必须转绿", "A2 兜底栈含 Windows 中文衬线" not in fails))

    fails = run(stack_extra=",'Microsoft YaHei'", face_src="url(data:font/woff2;base64,%s)" % small)
    cases.append(("把无衬线族名塞进兜底栈不能糊过 A2", "A2 兜底栈含 Windows 中文衬线" in fails))

    tmp = tempfile.mkdtemp(prefix="uap-selftest-")
    try:
        put_rel(tmp, TOK_CSS, base_tokens % "")
        put_rel(tmp, STYLE_DIR + "/a.css", ".dead-one{color:red}.live{color:blue}")
        put_rel(tmp, SRC_DIR + "/App.tsx", 'export const A = () => <div className="live" />;')
        cases.append(("A3 类名没有消费者时必须判红",
                      "A3 样式表类名有消费者" in judge_quiet(tmp)))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    cases.append(("A6 Android 参照字体不在时必须判红（不许删除式达标）",
                  any(f.startswith("A6") for f in run())))

    def icon_run(tsx_block, xml_paths):
        tmp = tempfile.mkdtemp(prefix="uap-selftest-")
        try:
            put_rel(tmp, TOK_CSS, base_tokens % ",'SimSun'")
            put_rel(tmp, STYLE_DIR + "/live.css", ".live{color:red}")
            put_rel(tmp, SRC_DIR + "/App.tsx", 'export const A = () => <div className="live" />;')
            put_rel(tmp, ICON_TSX, "const G = {\n" + tsx_block + "\n};")
            put_rel(tmp, GEAR_XML,
                    "<vector>" + "".join('<path android:pathData="%s" />' % p for p in xml_paths)
                    + "</vector>")
            return judge_quiet(tmp)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    teeth = " ".join("L%d 2" % i for i in range(30))
    cases.append(("A7 两端齿轮逐字相同必须转绿",
                  "A7 齿轮图标两端同源" not in icon_run(
                      '  chat_gear: (<g><path d="M1 1 %s Z" /></g>),' % teeth, ["M1 1 %s Z" % teeth])))
    cases.append(("A7 两端 pathData 不一致必须判红",
                  "A7 齿轮图标两端同源" in icon_run(
                      '  chat_gear: (<g><path d="M1 1 %s Z" /></g>),' % teeth, ["M2 2 %s Z" % teeth])))
    cases.append(("A7 退回八向放射线必须判红（一致不等于画对了）",
                  "A7 齿轮图标两端同源" in icon_run(
                      '  chat_gear: (<g><path d="M12 2.5v3M12 18.5v3" /></g>),',
                      ["M12 2.5v3M12 18.5v3"])))

    cases.append(("A3 的豁免名单必须每条带 >8 字理由（不许用来吞掉真红）",
                  all(isinstance(r, str) and len(r) > 8 for r in DEAD_CLASS_ALLOWLIST.values())))

    for name, ok in cases:
        ck.check(ok, "SELFTEST " + name)
    print("selftest judgement cases executed = {0}".format(len(cases)))
    ck.check(len(cases) >= SELFTEST_MIN_CASES, "SELFTEST 用例数不得掉到裸下限以下",
             "实测 %d，下限 %d" % (len(cases), SELFTEST_MIN_CASES))


def main(argv):
    enable_utf8_stdout()
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--selftest", action="store_true", help="判仪器自己：每条判据都有负例")
    args = ap.parse_args(argv)

    ck = Checks(CHECK_NAME)
    try:
        if args.selftest:
            selftest(ck)
        else:
            judge(ck)
    except (IOError, OSError, UnicodeDecodeError) as exc:
        print("FAULT {0}".format(exc))
        print("EXITCODE={0}".format(EXIT_FAULT))
        return EXIT_FAULT
    return ck.finish()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
