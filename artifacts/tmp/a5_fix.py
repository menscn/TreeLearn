import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_probe import Img, hexs
from a4_crop import crop_zoom, W, H, im, L2

print('== 补裁 ==')
crop_zoom(30, 755, 420, 815, 4, 'z_actions2.png')     # 真正的动作行
crop_zoom(30, 855, 480, 955, 3, 'z_pill1b.png')       # 胶囊1 完整
crop_zoom(30, 1180, 400, 1220, 5, 'z_??.png') if False else None

def is_grayish(c):
    r, g, b = c
    return r < 215 and g < 215 and b < 215 and abs(r - g) < 25 and abs(g - b) < 25

def clusters(y0, y1, x0=0, x1=None, minw=2, pred=is_grayish):
    x1 = x1 or W
    runs = []
    cur = None
    for x in range(x0, x1):
        n = sum(1 for y in range(y0, y1) if pred(im.px(x, y)))
        if n > 0:
            if cur is None: cur = x
        else:
            if cur is not None: runs.append((cur, x - 1)); cur = None
    if cur is not None: runs.append((cur, x1 - 1))
    return [(a, b) for a, b in runs if b - a >= minw]

print('\n== 动作行 y 755..815：灰色簇 ==')
cl = clusters(755, 815)
for a, b in cl:
    print(f'  物理 {a}..{b}  逻辑 {L2(a):.1f}..{L2(b):.1f}  宽 {L2(b-a+1):.1f}')
ys = [y for y in range(750, 820) if sum(1 for x in range(W) if is_grayish(im.px(x, y))) > 1]
if ys:
    print(f'  墨迹 y {min(ys)}..{max(ys)}  逻辑中心 {L2((min(ys)+max(ys))/2):.1f}  高 {L2(max(ys)-min(ys)+1):.1f}')

print('\n== 胶囊1 y 861..948：灰色簇 ==')
cl = clusters(861, 948)
for a, b in cl:
    print(f'  物理 {a}..{b}  逻辑 {L2(a):.1f}..{L2(b):.1f}  宽 {L2(b-a+1):.1f}')

def is_pill(c):
    return abs(c[0]-235) <= 8 and abs(c[1]-233) <= 8 and abs(c[2]-229) <= 8
print('\n== 胶囊1 精确 bbox ==')
xs = [x for x in range(W) if sum(1 for y in range(861, 949) if is_pill(im.px(x, y))) > 40]
print(f'  x {min(xs)}..{max(xs)} → 逻辑 {L2(min(xs)):.1f}..{L2(max(xs)):.1f} 宽 {L2(max(xs)-min(xs)+1):.1f}')

print('\n== "刚刚" 探测（y 755..815, x 600..）==')
from collections import Counter
cnt = Counter(); xs = []
for y in range(750, 820):
    for x in range(600, W):
        c = im.px(x, y)
        if c[0] < 245 and not (abs(c[0]-c[1]) < 3 and c[0] > 240):
            cnt[c] += 1; xs.append(x)
print(f'  候选墨色: {[(hexs(c), n) for c, n in cnt.most_common(5)]}')
if xs:
    print(f'  x {min(xs)}..{max(xs)} → 逻辑 {L2(min(xs)):.1f}..{L2(max(xs)):.1f}')
