import sys, os, zlib, struct
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_probe import Img, hexs
from collections import Counter

P = r'C:\Users\23745\.workbuddy\clipboard-images\clipboard-2026-09-27T08-04-23-395Z-c2393438.png'
OUT = r'D:\Project\Android\Treelearn\artifacts\tmp'
im = Img(P)
W, H = im.w, im.h
L2 = lambda v: v / 2.0


def write_png(path, w, h, rgb):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw += rgb[y * w * 3:(y + 1) * w * 3]
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 6))
    png += chunk(b'IEND', b'')
    open(path, 'wb').write(png)


def crop_zoom(x0, y0, x1, y1, scale, name):
    cw, chh = x1 - x0, y1 - y0
    ow, oh = cw * scale, chh * scale
    buf = bytearray(ow * oh * 3)
    for oy in range(oh):
        sy = y0 + oy // scale
        for ox in range(ow):
            sx = x0 + ox // scale
            c = im.px(sx, sy)
            i = (oy * ow + ox) * 3
            buf[i:i + 3] = bytes(c)
    write_png(os.path.join(OUT, name), ow, oh, buf)
    print(f'  输出 {name}  {ow}x{oh}')


print('== 裁剪放大 ==')
crop_zoom(30, 370, 260, 420, 4, 'z_tag.png')          # 知芽标签
crop_zoom(40, 440, 790, 500, 2, 'z_body1.png')        # 正文第一行
crop_zoom(260, 100, 620, 145, 3, 'z_title.png')       # 标题中英混排
crop_zoom(30, 860, 420, 960, 2, 'z_pill1.png')        # 第一个推荐胶囊
crop_zoom(40, 875, 420, 935, 3, 'z_actions.png')      # 动作行图标
crop_zoom(600, 100, 824, 150, 3, 'z_appbar_r.png')    # 右侧图标

print('\n== 动作行：灰色图标簇（阈值放宽）==')
def is_grayish(c):
    r, g, b = c
    return r < 200 and g < 200 and b < 200 and abs(r - g) < 22 and abs(g - b) < 22
cols = []
for x in range(0, W):
    n = sum(1 for y in range(880, 935) if is_grayish(im.px(x, y)))
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
print('  簇（物理 / 逻辑 / 宽逻辑）:')
for a, b in runs:
    if b - a >= 2:
        print(f'    {a}..{b}  {L2(a):.1f}..{L2(b):.1f}  宽 {L2(b-a+1):.1f}')
ys = [y for y in range(860, 950) if sum(1 for x in range(0, W) if is_grayish(im.px(x, y))) > 2]
print(f'  动作行墨迹 y {min(ys)}..{max(ys)}  逻辑中心 {L2((min(ys)+max(ys))/2):.1f}  高 {L2(max(ys)-min(ys)+1):.1f}')

print('\n== 正文左边缘 ==')
for y in (460, 516, 572, 627, 684, 771):
    xs = [x for x in range(0, W) if any(im.px(x, yy)[0] < 150 for yy in range(y, y + 26))]
    print(f'  行 y={y}: 墨迹 x {min(xs)}..{max(xs)} → 逻辑左 {L2(min(xs)):.1f} 右 {L2(max(xs)):.1f}')

print('\n== 横格线探测（x=10，y 1150..1367 逐行）==')
vals = [(y, hexs(im.px(10, y))) for y in range(1150, H)]
odd = [(y, v) for y, v in vals if v != '#F3F1EC']
print(f'  非底色行数 = {len(odd)} / {len(vals)}')
for y, v in odd[:20]:
    print(f'    y={y} {v}')

print('\n== "刚刚" 文字颜色与位置 ==')
cnt = Counter()
xs = []
for y in range(880, 935):
    for x in range(600, W):
        c = im.px(x, y)
        if c[0] < 210:
            cnt[c] += 1; xs.append(x)
print(f'  最深色: {[ (hexs(c), n) for c,n in cnt.most_common(3)]}')
if xs:
    print(f'  x 范围 {min(xs)}..{max(xs)} → 逻辑 {L2(min(xs)):.1f}..{L2(max(xs)):.1f}')

print('\n== 知芽标签 ==')
cnt = Counter()
xs = []; ys = []
for y in range(370, 425):
    for x in range(20, 300):
        c = im.px(x, y)
        if c[0] < 190:
            cnt[c] += 1; xs.append(x); ys.append(y)
print(f'  墨迹 x {min(xs)}..{max(xs)} 逻辑 {L2(min(xs)):.1f}..{L2(max(xs)):.1f}; y {min(ys)}..{max(ys)} 逻辑 {L2(min(ys)):.1f}..{L2(max(ys)):.1f}')
print(f'  常见墨色: {[(hexs(c), n) for c, n in cnt.most_common(5)]}')
