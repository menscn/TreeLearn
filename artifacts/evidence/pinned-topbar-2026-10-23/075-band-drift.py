# -*- coding: utf-8 -*-
"""075 仪器：常驻顶栏的两帧同位像素差。从仓库根执行。

    python -X utf8 artifacts/evidence/pinned-topbar-2026-10-23/075-band-drift.py

两个指标同时给，因为一次换面（chrome -> control）要能同时回答「栏里的字还在动吗」和
「动了多少」：
- `变化像素 %`：同位像素 Σ|ΔRGB| > 18 的比例，与 074 的滚动判据同一个阈值，可直接横比。
- `|dL|` 中位 / P95 / 最大：Rec.709 亮度差的分布。中位是「这条带稳不稳」，
  最大值用来定位残差到底长在谁身上（写在 075-reading.txt 第 3 节）。
"""
import os
import sys

sys.path.insert(0, os.path.join(".codex", "scripts", "dev", "parity"))
from png_probe import Img  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
STEP = 2
DELTA_SUM = 18
# home_appbar 在 dumpsys 里是 52,20-1148,189（父容器偏移 y=115 的状态栏）
BAR_Y0, BAR_Y1 = 135, 304
BODY_Y0, BODY_Y1 = 310, 900
PILL_Y0, PILL_Y1 = 2391, 2580
PILL_X0, PILL_X1 = 141, 1058
# 胶囊圆角半径 = 高度一半 ≈ 84px，弧只在四角；取直边段就是「一定在面里」的那块
FACE_Y0, FACE_Y1 = 150, 250
FACE_X0, FACE_X1 = 140, 1120


def luma(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def band(a, b, y0, y1, x0=0, x1=None):
    ia, ib = Img(os.path.join(HERE, a)), Img(os.path.join(HERE, b))
    x1 = x1 if x1 is not None else ia.w
    changed, total, dl = 0, 0, []
    for y in range(y0, y1, STEP):
        for x in range(x0, x1, STEP):
            pa, pb = ia.px(x, y), ib.px(x, y)
            total += 1
            if abs(pa[0] - pb[0]) + abs(pa[1] - pb[1]) + abs(pa[2] - pb[2]) > DELTA_SUM:
                changed += 1
            dl.append(abs(luma(pa) - luma(pb)))
    dl.sort()
    return (f"变化像素 {100.0 * changed / total:5.2f}%   |dL| 中位 {dl[len(dl) // 2]:6.2f}"
            f"  P95 {dl[int(len(dl) * 0.95)]:6.2f}  最大 {dl[-1]:6.2f}   样本 {total}")


def report(label, a, b, y0, y1, x0=0, x1=None):
    print(f"{label:<28} y={y0}..{y1} x={x0}..{x1 or '全宽'}")
    print(f"    {band(a, b, y0, y1, x0, x1)}")


HOME_A, HOME_B = "075-home-top.png", "075-home-scrolled.png"
CHROME_A, CHROME_B = "075-chrome-face-a.png", "075-chrome-face-b.png"
LIB_A, LIB_B = "075-library-top.png", "075-library-scrolled.png"
WEB_A, WEB_B = "075-web-top-s0.png", "075-web-top-sbottom.png"

print("一、chrome 面（--glass-chrome .74，072 的第六面还没上到常驻栏）")
report("顶栏带", CHROME_A, CHROME_B, BAR_Y0, BAR_Y1)

print("\n二、control 面（--glass-control .93，本片定稿）")
report("顶栏带", HOME_A, HOME_B, BAR_Y0, BAR_Y1)
report("顶栏带·胶囊直边内部", HOME_A, HOME_B, FACE_Y0, FACE_Y1, FACE_X0, FACE_X1)
report("栏下正文带（应当翻动）", HOME_A, HOME_B, BODY_Y0, BODY_Y1)
report("底部胶囊面内", HOME_A, HOME_B, PILL_Y0, PILL_Y1, PILL_X0, PILL_X1)

print("\n三、库页（同一枚 control 面，栏内没有常驻动画）")
report("顶栏带", LIB_A, LIB_B, BAR_Y0, BAR_Y1)
report("栏下正文带", LIB_A, LIB_B, BODY_Y0, BODY_Y1)

print("\n四、Web 顶栏（--glass-chrome .74 + backdrop-filter: blur(28px)，本片零改动）")
report("顶栏带 0..76", WEB_A, WEB_B, 0, 76)
report("栏下正文带", WEB_A, WEB_B, 80, 300)
