"""量测 2026-09-28 用户批注图：动作行图标簇 + 附件 pill。
纯像素量测，输出逻辑值（图是 1168x1760，逻辑基准 2x => 584x880）。
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_probe import Img, near, hexs

P = r'C:\Users\23745\.workbuddy\clipboard-images\clipboard-2026-09-28T06-09-32-651Z-bb5bdcd6.png'
img = Img(P)
print('== image %dx%d ch=%d ==' % (img.w, img.h, img.ch))

logical = 2.0


def dark(p, thr=190):
    return sum(p[:3]) / 3 < thr


def scan_row_clusters(y0, y1, x0, x1, thr=190, gap=6):
    """在 [y0,y1) x [x0,x1) 区域里按列统计暗像素，聚成簇。"""
    cols = []
    for x in range(x0, x1):
        n = 0
        for y in range(y0, y1):
            if dark(img.px(x, y), thr):
                n += 1
        cols.append(n)
    clusters = []
    cur = None
    for i, n in enumerate(cols):
        if n > 0:
            if cur is None:
                cur = [i, i]
            else:
                cur[1] = i
        else:
            if cur is not None:
                if i - cur[1] > gap:
                    clusters.append(tuple(cur))
                    cur = None
                else:
                    cur[1] = i
    if cur:
        clusters.append(tuple(cur))
    return [(x0 + a, x0 + b) for a, b in clusters]


# ---- 先找动作行：批注框（红框）大致在 y 1470..1530 (物理)
print('\n-- 扫描红框区域寻找图标簇 --')
for y0, y1, tag in [(1460, 1540, 'annotated action row')]:
    cl = scan_row_clusters(y0, y1, 0, img.w, thr=200)
    print(tag, 'y[%d,%d)' % (y0, y1))
    for a, b in cl:
        print('   x[%d..%d] w=%d  logical w=%.1f' % (a, b, b - a + 1, (b - a + 1) / logical))

# ---- 找图标墨色的纵向范围（取第一个簇）
print('\n-- 红框行内各簇的纵向范围 --')
cl = scan_row_clusters(1460, 1540, 0, img.w, thr=200)
for a, b in cl:
    yy0, yy1 = None, None
    for y in range(1440, 1560):
        hit = any(dark(img.px(x, y), 200) for x in range(a, b + 1))
        if hit:
            if yy0 is None:
                yy0 = y
            yy1 = y
    if yy0 is not None:
        print('   x[%d..%d] y[%d..%d] h=%d  logical h=%.1f' % (a, b, yy0, yy1, yy1 - yy0 + 1, (yy1 - yy0 + 1) / logical))

# ---- 附件 pill：底部区域
print('\n-- 底部附件 pill 区域 --')
for y in [1680, 1700, 1720, 1740]:
    print('row %d sample:' % y, [hexs(img.px(x, y)) for x in [60, 80, 100, 150, 200, 260]])
