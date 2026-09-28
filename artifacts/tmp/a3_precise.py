import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_probe import Img, hexs

P = r'C:\Users\23745\.workbuddy\clipboard-images\clipboard-2026-09-27T08-04-23-395Z-c2393438.png'
im = Img(P)
W, H = im.w, im.h
L2 = lambda v: v / 2.0


def is_green(c):
    r, g, b = c
    return g > r + 15 and g > b + 15 and g < 170 and r < 140


def is_pill(c):
    return abs(c[0] - 235) <= 6 and abs(c[1] - 233) <= 6 and abs(c[2] - 229) <= 6


def is_ink(c, t=160):
    return c[0] < t and c[1] < t and c[2] < t


def is_gray(c, lo=110, hi=215):
    r, g, b = c
    return lo <= r <= hi and lo <= g <= hi and lo <= b <= hi and abs(r - g) < 18 and abs(g - b) < 18


print('==== A. 用户气泡（逐行绿色像素计数）====')
rows = []
for y in range(150, 700):
    xs = [x for x in range(W) if is_green(im.px(x, y))]
    rows.append((y, len(xs), min(xs) if xs else None, max(xs) if xs else None))
big = [(y, n, a, b) for y, n, a, b in rows if n > 300]
print(f'  绿色块行数={len(big)}  y {big[0][0]}..{big[-1][0]}  高={L2(big[-1][0]-big[0][0]+1):.1f} 逻辑px')
bubT, bubB = big[0][0], big[-1][0]
maxrow = max(big, key=lambda t: t[1])
print(f'  最宽行 y={maxrow[0]} 宽={maxrow[1]} → 气泡 left={maxrow[2]} right={maxrow[3]}  宽={L2(maxrow[3]-maxrow[2]+1):.1f} 逻辑px')
bubL, bubR = maxrow[2], maxrow[3]
print(f'  → 左边缘 {L2(bubL):.1f} / 右边缘 {L2(bubR):.1f} (距屏右 {L2(W-1-bubR):.1f}) / 屏宽 {L2(W):.1f}')
print('  圆角：逐行 left 起点与 inset')
for y in range(bubT, bubT + 32, 2):
    xs = [x for x in range(W) if is_green(im.px(x, y))]
    if xs:
        print(f'    y={y} (top+{y-bubT:2d} / 逻辑{L2(y-bubT):4.1f}): left={min(xs)} inset={min(xs)-bubL} right={max(xs)} insetR={bubR-max(xs)}')

print('\n  气泡内白字（限制在气泡内）：')
xs, ys = [], []
for y in range(bubT, bubB + 1):
    for x in range(bubL, bubR + 1):
        c = im.px(x, y)
        if c[0] > 190 and c[1] > 190 and c[2] > 190:
            xs.append(x); ys.append(y)
print(f'    白字 bbox x {min(xs)}..{max(xs)} y {min(ys)}..{max(ys)}')
print(f'    padL={L2(min(xs)-bubL):.1f} padR={L2(bubR-max(xs)):.1f} padT={L2(min(ys)-bubT):.1f} padB={L2(bubB-max(ys)):.1f}  字高={L2(max(ys)-min(ys)+1):.1f}')

print('\n==== B. 气泡内纯色采样（排除文字）====')
from collections import Counter
cnt = Counter()
for y in range(bubT + 20, bubB - 20):
    for x in range(bubL + 20, bubR - 20):
        cnt[im.px(x, y)] += 1
for c, n in cnt.most_common(4):
    print(f'    {hexs(c)}  {n}')

print('\n==== C. 页面底色 & 是否含横格纹理 ====')
cnt2 = Counter()
for y in range(700, 800):
    for x in range(5, 40):
        cnt2[im.px(x, y)] += 1
print('    左侧空白区最常见色:', [(hexs(c), n) for c, n in cnt2.most_common(3)])
col = [im.px(10, y) for y in range(1200, 1300)]
print('    x=10 竖列 y1200..1300 颜色序列:', [hexs(c) for c in col[::4]])

print('\n==== D. AppBar ====')
for y in range(190, 215):
    print(f'    y={y} x=8:{hexs(im.px(8, y))} x=300:{hexs(im.px(300, y))} x=700:{hexs(im.px(700, y))}')
print('  标题行暗像素 x 分布：')
ys0, ys1 = None, None
for y in range(100, 200):
    n = sum(1 for x in range(0, W) if is_ink(im.px(x, y)))
    if n > 3:
        if ys0 is None:
            ys0 = y
        ys1 = y
print(f'    标题/图标行 y {ys0}..{ys1} 高={L2(ys1-ys0+1):.1f} 逻辑px')
cols = []
for x in range(W):
    n = sum(1 for y in range(ys0, ys1 + 1) if is_ink(im.px(x, y)))
    cols.append((x, n))
runs = []
cur = None
for x, n in cols:
    if n > 0:
        if cur is None:
            cur = x
    else:
        if cur is not None:
            runs.append((cur, x - 1)); cur = None
if cur is not None:
    runs.append((cur, W - 1))
print('    AppBar 内的元素簇（物理 x 区间 / 逻辑）：')
for a, b in runs:
    if b - a >= 2:
        print(f'      {a}..{b}  逻辑 {L2(a):.1f}..{L2(b):.1f}  宽 {L2(b-a+1):.1f}')
