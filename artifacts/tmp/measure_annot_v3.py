"""量测批注图 v3：排除红色批注笔画，只找灰色图标墨迹。"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_probe import Img, hexs

P = r'C:\Users\23745\.workbuddy\clipboard-images\clipboard-2026-09-28T06-09-32-651Z-bb5bdcd6.png'
img = Img(P)
L = 2.0


def is_red(p):
    r, g, b = p[:3]
    return r > 130 and r - g > 45 and r - b > 45


def is_ink(p, thr=205):
    """灰/黑墨迹，且不是红批注。"""
    if is_red(p):
        return False
    r, g, b = p[:3]
    return (r + g + b) / 3 < thr and max(r, g, b) - min(r, g, b) < 45


def clusters(y0, y1, x0, x1, thr=205, gap=5, minpix=3):
    cols = []
    for x in range(x0, x1):
        n = sum(1 for y in range(y0, y1) if is_ink(img.px(x, y), thr))
        cols.append(n)
    out, cur = [], None
    for i, n in enumerate(cols):
        if n >= minpix:
            cur = [i, i] if cur is None else [cur[0], i]
        else:
            if cur and i - cur[1] > gap:
                out.append(tuple(cur)); cur = None
    if cur:
        out.append(tuple(cur))
    return [(x0 + a, x0 + b) for a, b in out]


def vext(xa, xb, y0, y1, thr=205):
    ys = [y for y in range(y0, y1) if any(is_ink(img.px(x, y), thr) for x in range(xa, xb + 1))]
    return (min(ys), max(ys)) if ys else None


print('== 动作行区域（红框内），只取灰墨迹 ==')
for y0, y1 in [(1455, 1535)]:
    cl = clusters(y0, y1, 100, 1100, minpix=2)
    print('y[%d,%d) 簇:' % (y0, y1))
    prev = None
    for a, b in cl:
        v = vext(a, b, y0 - 15, y1 + 15)
        ctr = (a + b) / 2
        d = '' if prev is None else ' gap=%.1f' % ((ctr - prev) / L)
        print('  x[%d..%d] w=%d(log %.1f) ctr=%.1f(log %.1f) vert=%s%s'
              % (a, b, b - a + 1, (b - a + 1) / L, ctr, ctr / L, v, d))
        prev = ctr

print()
print('== 动作行上下：末行正文 / 首个卡片 ==')
# 找该区域的分隔（纸张色横带）
for y in range(1420, 1560, 4):
    row = [img.px(x, y) for x in range(200, 900, 7)]
    bg = sum(1 for p in row if abs(p[0]-236) < 12 and abs(p[1]-234) < 12 and abs(p[2]-228) < 14)
    print(' y=%d bgcount=%d/%d sample=%s' % (y, bg, len(row), hexs(img.px(400, y))))
