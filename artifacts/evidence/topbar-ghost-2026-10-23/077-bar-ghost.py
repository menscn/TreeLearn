# -*- coding: utf-8 -*-
"""077 仪器：常驻顶栏的「透印」读数。从仓库根执行。

    python -X utf8 artifacts/evidence/topbar-ghost-2026-10-23/077-bar-ghost.py

量的是**穿过栏的内容还剩多少**。两帧同位、同一个取样框，改的只有面的 alpha：

- `变化像素 %`：同位像素 |ΔL| > 6 的比例（与 074/075 同一个阈值，可横比）。
- `|dL| max`：那一行里最响的一个像素。控制面的判据就落在它身上——
  `check_glass_depth.py` 用 `J4_MIN_DRIFT = 8.0` 判「玻璃下有内容经过」，
  那是通透面要拿到的分；对控制面来说同一把尺是**上限**，越过 8 就是用户看到的「重叠」。

取样框 x=200..1000 是胶囊面内的中段：home_appbar 自己的控件只占 x=78..195（抽屉钮）
与 x=1005..1122（设置钮），所以这段里出现的任何墨都只可能来自栏下穿过的内容。
"""
import os
import sys

sys.path.insert(0, os.path.join(".codex", "scripts", "dev", "parity"))
from png_probe import Img  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
J4_MIN_DRIFT = 8.0          # 与 .codex/scripts/dev/check_glass_depth.py 同一个数，方向相反
STEP = 1
X0, X1 = 200, 1000          # 胶囊面内中段：栏自身在这段没有控件
ROWS = range(256, 285, 4)   # 推荐语整行穿过的那几条

PAIRS = [
    ("修前 --glass-control .93", "077-before-93-t0.png", "077-before-93-t1.png"),
    ("修后 --glass-control .98", "077-after-98-u0.png", "077-after-98-u1.png"),
]


def luma(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def row(a, b, y):
    dl = sorted(abs(luma(a.px(x, y)) - luma(b.px(x, y))) for x in range(X0, X1, STEP))
    n = len(dl)
    changed = sum(1 for v in dl if v > 6)
    return 100.0 * changed / n, dl[int(n * 0.95)], dl[-1], dl[n // 2]


for label, fa, fb in PAIRS:
    a, b = Img(os.path.join(HERE, fa)), Img(os.path.join(HERE, fb))
    print(f"\n{label}   取样框 x={X0}..{X1}（胶囊面内中段）")
    print("      y   变化%    P95     max    中位   判据（|dL| max < 8.0 = 不透印）")
    worst = 0.0
    for y in ROWS:
        pc, p95, mx, med = row(a, b, y)
        worst = max(worst, mx)
        print(f"    {y:5d}  {pc:5.2f}  {p95:5.2f}  {mx:5.2f}  {med:5.2f}"
              f"   {'PASS' if mx < J4_MIN_DRIFT else 'FAIL'}")
    print(f"    该行最大值 {worst:.2f} L  ->  {'PASS' if worst < J4_MIN_DRIFT else 'FAIL'}"
          f"（泄漏线 {J4_MIN_DRIFT} L）")
