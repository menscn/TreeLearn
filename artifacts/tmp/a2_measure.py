import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_probe import Img, hexs

P = r'C:\Users\23745\.workbuddy\clipboard-images\clipboard-2026-09-27T08-04-23-395Z-c2393438.png'
im = Img(P)


def is_green(c):
    r, g, b = c
    return g > r + 15 and g > b + 15 and g < 170 and r < 140


def is_pill(c):
    # 推荐胶囊底 #EBE9E5
    return abs(c[0] - 235) <= 5 and abs(c[1] - 233) <= 5 and abs(c[2] - 229) <= 5


def is_dark(c):
    return c[0] < 150 and c[1] < 150 and c[2] < 150


def bbox(pred, y0=0, y1=None, x0=0, x1=None):
    y1 = y1 or im.h
    x1 = x1 or im.w
    xs, ys = [], []
    for y in range(y0, y1):
        for x in range(x0, x1):
            if pred(im.px(x, y)):
                xs.append(x); ys.append(y)
    if not xs:
        return None
    return min(xs), min(ys), max(xs), max(ys)


print('==== 1. 用户气泡（绿色区域）====')
bb = bbox(is_green, 150, 600)
print('  bbox(left,top,right,bottom) =', bb, ' 宽高 =', bb[2] - bb[0] + 1, 'x', bb[3] - bb[1] + 1)
L, T, R, B = bb
print(f'  逻辑值 /2: left={L/2:.1f} top={T/2:.1f} right={R/2:.1f} bottom={B/2:.1f} 宽={(R-L+1)/2:.1f} 高={(B-T+1)/2:.1f}')
print(f'  右边缘距屏右 = {(im.w-1-R)/2:.1f} 逻辑px, 左边缘 = {L/2:.1f}')
print('  逐行绿色起始 x（估算圆角半径）：')
for k in range(0, 26, 2):
    y = T + k
    xs = [x for x in range(0, im.w) if is_green(im.px(x, y))]
    if xs:
        print(f'    top+{k:2d} 物理 (逻辑 {k/2:4.1f}): x从 {min(xs)} 到 {max(xs)}  inset_left={min(xs)-L} inset_right={(R-max(xs))}')

print('\n==== 2. 气泡内文字（白字反相：找亮像素）====')
def is_light(c):
    return c[0] > 200 and c[1] > 200 and c[2] > 200
xs, ys = [], []
for y in range(T, B + 1):
    for x in range(L, R + 1):
        if is_light(im.px(x, y)):
            xs.append(x); ys.append(y)
print(f'  白字 bbox: left={min(xs)} top={min(ys)} right={max(xs)} bottom={max(ys)}')
print(f'  → 文字左内边距 padL={(min(xs)-L)/2:.1f} 逻辑px, 上 padT={(min(ys)-T)/2:.1f}, 下 padB={(B-max(ys))/2:.1f}, 右 padR={(R-max(xs))/2:.1f}')
print(f'  → 字面高度(含中英混排) = {(max(ys)-min(ys)+1)/2:.1f} 逻辑px')

print('\n==== 3. 推荐胶囊 ====')
# 逐行判断该行是否有胶囊底色，聚合成块
rows = []
for y in range(800, im.h):
    cnt = sum(1 for x in range(0, im.w, 2) if is_pill(im.px(x, y)))
    rows.append((y, cnt))
blocks = []
cur = None
for y, cnt in rows:
    if cnt > 20:
        if cur is None:
            cur = y
    else:
        if cur is not None:
            blocks.append((cur, y - 1)); cur = None
if cur is not None:
    blocks.append((cur, im.h - 1))
for i, (a, b) in enumerate(blocks):
    bb2 = bbox(is_pill, a, b + 1)
    print(f'  胶囊{i+1}: y {a}..{b} (高 {(b-a+1)/2:.1f} 逻辑px)  x {bb2[0]}..{bb2[2]} (宽 {(bb2[2]-bb2[0]+1)/2:.1f} 逻辑px, 左 {bb2[0]/2:.1f})')
    for k in range(0, 16, 2):
        xs = [x for x in range(0, im.w) if is_pill(im.px(x, a + k))]
        if xs:
            print(f'      top+{k:2d}: inset_left={min(xs)-bb2[0]} inset_right={bb2[2]-max(xs)}')
    if i < len(blocks) - 1:
        print(f'      与下一个胶囊的垂直间隙 = {(blocks[i+1][0]-b-1)/2:.1f} 逻辑px')

print('\n==== 4. 正文文本行（暗像素行分布，x 40..790）====')
prev = False
for y in range(340, 830):
    c = sum(1 for x in range(40, 790) if is_dark(im.px(x, y)))
    has = c > 3
    if has and not prev:
        start = y
    if not has and prev:
        print(f'  行: y {start}..{y-1}  高 {(y-start)/2:.1f} 逻辑px  (中心 {((start+y-1)/2)/2:.1f})')
    prev = has

print('\n==== 5. 动作行 ====')
prev = False
for y in range(830, 1000):
    c = sum(1 for x in range(40, 790) if is_dark(im.px(x, y)))
    has = c > 3
    if has and not prev:
        start = y
    if not has and prev:
        print(f'  行: y {start}..{y-1}  高 {(y-start)/2:.1f} 逻辑px')
        xs = [x for x in range(0, im.w) if any(is_dark(im.px(x, yy)) for yy in range(start, y))]
        print(f'     暗像素 x 范围 {min(xs)}..{max(xs)}')
    prev = has
