# -*- coding: utf-8 -*-
"""把六张真截图压成 base64 灌进评审板模板，产出单文件交付。

单文件 HTML 一律内嵌图片（相对路径挪目录就裂图）。Web 面 2880 宽降到 1400，
安卓面 1200 宽降到 700，JPEG q80——评审板上的截图不需要像素级可读。
"""
import base64
import io
import pathlib
from PIL import Image

HERE = pathlib.Path(__file__).parent
ROOT = HERE.parents[1]

SRC = {
    "IMG_WEB_HOME": "artifacts/evidence/web-reading-glass-2026-10-25/081-home-top.png",
    "IMG_WEB_CHAT": "artifacts/evidence/web-reading-glass-2026-10-25/081-chat-top.png",
    "IMG_WEB_FOREST": "artifacts/evidence/web-forest-070/forest-070-desktop.png",
    "IMG_AND_HOME": "artifacts/evidence/android-scheme-a-parity-2026-10-22/059-home.png",
    "IMG_AND_CHAT": "artifacts/evidence/android-scheme-a-parity-2026-10-22/059-chat.png",
    "IMG_AND_LIB": "artifacts/evidence/android-scheme-a-parity-2026-10-22/059-library.png",
}
WEB_W, AND_W = 1400, 720


def enc(rel, target_w):
    im = Image.open(ROOT / rel)
    if im.width > target_w:
        im = im.resize((target_w, round(im.height * target_w / im.width)), Image.LANCZOS)
    if im.mode != "RGB":
        im = im.convert("RGB")
    buf = io.BytesIO()
    im.save(buf, "JPEG", quality=80, optimize=True, progressive=True)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


tpl = (HERE / "board.template.html").read_text(encoding="utf-8")
missing = [k for k in SRC if "{{%s}}" % k not in tpl]
if missing:
    raise SystemExit(f"模板里没有这些占位符：{missing}")
extra = [t for t in ["IMG_WEB_HOME", "IMG_WEB_CHAT", "IMG_WEB_FOREST", "IMG_AND_HOME", "IMG_AND_CHAT", "IMG_AND_LIB"]]

out = tpl
for key, rel in SRC.items():
    data = enc(rel, WEB_W if key.startswith("IMG_WEB") else AND_W)
    out = out.replace("{{%s}}" % key, data)
    print(f"{key:16s} {rel.split('/')[-1]:26s} -> {len(data)//1024} KB")

left = out.count("{{IMG_")
if left:
    raise SystemExit(f"还有 {left} 个占位符没替换")

dst = HERE / "知芽界面可优化点-评审板-2026-10-25.html"
dst.write_text(out, encoding="utf-8")
print(f"\n写出 {dst}  {dst.stat().st_size // 1024} KB")
