#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为 Web／桌面产物生成衬线子集字体（086 线 X1 的生产脚本）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/dev/gen_serif_subset.py            # 生成/覆盖子集
    python -X utf8 .codex/scripts/dev/gen_serif_subset.py --check    # 只比对，不写文件

退出码：0 成立；1 产物不符（子集缺失、覆盖不足、体积超上限）；
3 仪器故障（fontTools 不可用、权威 otf 读不到）。

为什么子集是**签入仓库的资产**而不是构建时生成：
`fontTools` 在本机可用，但把它写成构建链的隐式依赖，等于让「没有 fontTools 的人
打不出离线包」。APK 那份 24,427,224 字节的 otf 也是签入仓库的资产，两端同构。
本脚本因此是**维护者工具**：改 `--serif` 覆盖范围时手动跑一次，日常构建只读资产。

子集口径：GB2312 一级字 6,763 + 二级字 3,008 + ASCII 可见字符 + 中英混排常用标点，
再加仓库里全部可见文案实际用到的字（`frontend/src/copy/zh-CN.ts` 与 Android
`strings.xml`）。为什么不直接用「界面文案集」：`.chat-title`（workspace.css:326）与
`.forest-sheet-title`（forest.css:367）渲染的是**用户自己起的主题名**，
任何静态集合都会漏；GB2312 两级的覆盖让漏字落到栈里的下一档（SimSun）而不是变成豆腐块，
这是子集化的标准取舍，已按 Gap 登记在 090 卡上。

格式选 WOFF1（zlib）而不是 WOFF2（brotli）：本机没有 brotli 模块，引入它才是新增依赖；
Chromium 与 Electron 对 woff1 的支持自 2013 年起就是完备的，`format("woff")` 声明齐全。
"""
import argparse
import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))

SOURCE_OTF = "android/app/src/main/res/font/noto_serif_sc.otf"
OUT_WOFF = "frontend/src/fonts/noto-serif-sc-subset.woff"
OUT_LICENSE = "frontend/src/fonts/OFL.txt"
COPY_FILES = ["frontend/src/copy/zh-CN.ts", "android/app/src/main/res/values/strings.xml"]

# 与 check_ui_asset_parity.py 的 INLINE_FONT_MAX 同一档：子集本身也要留得下余量。
SUBSET_MAX = 6 * 1024 * 1024
# 下限卡在「GB2312 两级不能整段丢」：实测 gb2312=7445、ascii+标点=260、仓库文案=653，
# 与源 otf 的 cmap 相交后为 7564（差的 64 个全在 U+2000–U+206F 排版控制区，源字体本身没有）。
# 写成 9000 是我第一次拍脑袋的错值——它比字符集本身还大，脚本永远判红；这种判据必须跟着实测走。
MIN_COVERAGE_CODEPOINTS = 7400


def rel(p):
    return os.path.join(REPO, p.replace("/", os.sep))


def gb2312_chars():
    """解出 GB2312 全部汉字与图形符号（按字节对枚举，不依赖任何码表文件）。"""
    out = set()
    for hi in range(0xA1, 0xFF):
        for lo in range(0xA1, 0xFF):
            try:
                ch = bytes([hi, lo]).decode("gb2312")
            except (UnicodeDecodeError, LookupError):
                continue
            out.add(ch)
    return out


def ascii_and_punct():
    out = set(chr(c) for c in range(0x20, 0x7F))
    out |= set(" 、。，；：？！“”‘’（）《》〈〉【】〔〕—…・·\u3000\t\n")
    out |= set("°±×÷≤≥≠≈∞→←↑↓①②③④⑤⑥⑦⑧⑨⑩")
    out |= set("ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩ")  # 术语里出现的希腊字母（α-β 注意机制等）
    out |= set(chr(c) for c in range(0x2000, 0x2060))
    return out


def repo_copy_chars():
    out = set()
    for rel_path in COPY_FILES:
        path = rel(rel_path)
        if not os.path.exists(path):
            continue
        text = io.open(path, encoding="utf-8", errors="replace").read()
        out |= {c for c in text if ord(c) > 0x7F}
    # 界面会渲染主题名，主题名来自夹具；夹具里的中文也一并纳入。
    for root_dir in ("frontend/src", "android/app/src/main/assets"):
        base = rel(root_dir)
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [d for d in dirnames if d not in ("node_modules", "__pycache__")]
            for name in filenames:
                if not name.endswith((".ts", ".tsx", ".json", ".xml")):
                    continue
                try:
                    text = io.open(os.path.join(dirpath, name), encoding="utf-8",
                                   errors="replace").read()
                except OSError:
                    continue
                if "fixture" in name.lower() or "copy" in name.lower():
                    out |= {c for c in text if ord(c) > 0x7F}
    return out


def build_charset():
    chars = gb2312_chars() | ascii_and_punct() | repo_copy_chars()
    chars.discard("\ufffd")  # 替换字符不是文案，是读失败的痕迹
    return chars


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--check", action="store_true", help="只比对已签入的子集是否仍是最新")
    args = ap.parse_args(argv[1:])

    try:
        from fontTools import subset  # noqa: F401
        from fontTools.ttLib import TTFont
    except ImportError as exc:
        print("FAULT fontTools 不可用（%s）：本脚本是维护者工具，不在构建链上" % exc)
        print("EXITCODE=3")
        return 3

    src = rel(SOURCE_OTF)
    if not os.path.exists(src):
        print("FAULT 权威字体不存在：%s" % SOURCE_OTF)
        print("EXITCODE=3")
        return 3

    chars = build_charset()
    want = {ord(c) for c in chars}

    probe = TTFont(src, lazy=True)
    source_cmap = set()
    for table in probe["cmap"].tables:
        source_cmap |= set(table.cmap.keys())
    probe.close()

    absent = sorted(want - source_cmap)
    take = sorted(want & source_cmap)
    print("取字 %s 个码位（GB2312 两级 + ASCII + 中英标点 + 仓库可见文案），源字体实际有 %s 个" % (
        len(want), len(take)))
    if absent:
        print("NOTE 源 otf 的 cmap 本身没有 %s 个请求码位（TAB 与 U+2000 区排版符号这类不成字的字符），不入子集" % len(absent))
    if len(take) < MIN_COVERAGE_CODEPOINTS:
        print("FAIL 覆盖码位数 %s < 下限 %s：取字逻辑退化" % (len(take), MIN_COVERAGE_CODEPOINTS))
        print("EXITCODE=1")
        return 1

    options = subset.Options()
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    options.recalc_bounds = True
    options.flavor = "woff"

    font = subset.load_font(src, options)
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=take)
    subsetter.subset(font)
    payload = io.BytesIO()
    font.flavor = "woff"
    font.save(payload)
    blob = payload.getvalue()

    # 子集化不是无损操作：字形可能被 hint/复用逻辑悄悄丢掉，丢了要在这里报警，
    # 不能等到用户在界面上看到一个豆腐块。
    out_cmap = set()
    for table in font["cmap"].tables:
        out_cmap |= set(table.cmap.keys())
    dropped = sorted(set(take) - out_cmap)
    if dropped:
        print("FAIL 子集丢掉 %s 个已请求码位（首例 U+%04X）：判据要的是覆盖，不是体积好看" % (
            len(dropped), dropped[0]))
        print("EXITCODE=1")
        return 1

    glyphs = len(font.getGlyphOrder())
    print("子集 %s 字节（%s 个字形，覆盖 %s 码位），源文件 %s 字节" % (
        len(blob), glyphs, len(out_cmap), os.path.getsize(src)))
    if len(blob) > SUBSET_MAX:
        print("FAIL 子集体积 %s 超过上限 %s：判据 A4 也会判同一条红" % (len(blob), SUBSET_MAX))
        print("EXITCODE=1")
        return 1

    out = rel(OUT_WOFF)
    if args.check:
        if not os.path.exists(out):
            print("FAIL 签入的子集资产不存在：%s" % OUT_WOFF)
            print("EXITCODE=1")
            return 1
        same = os.path.getsize(out) == len(blob)
        print("CHECK 签入资产字节数 %s vs 本次生成 %s → %s" % (
            os.path.getsize(out), len(blob), "一致" if same else "不一致（需重跑本脚本）"))
        print("EXITCODE=%d" % (0 if same else 1))
        return 0 if same else 1

    os.makedirs(os.path.dirname(out), exist_ok=True)
    with io.open(out, "wb") as fh:
        fh.write(blob)
    print("WROTE %s" % OUT_WOFF)

    lic_src = rel("android/app/src/main/assets/licenses/NotoSerifCJK-OFL.txt")
    if os.path.exists(lic_src):
        with io.open(lic_src, encoding="utf-8") as fh:
            lic = fh.read()
        with io.open(rel(OUT_LICENSE), "w", encoding="utf-8", newline="\n") as fh:
            fh.write(lic)
        print("WROTE %s（随包再分发必须带许可文本，OFL 允许子集化与再分发）" % OUT_LICENSE)
    else:
        print("FAIL 许可文本不存在：%s" % lic_src)
        print("EXITCODE=1")
        return 1
    print("EXITCODE=0")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
