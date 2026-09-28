import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_probe import Img, hexs
from a4_crop import crop_zoom, W, H, im

print('== 字体细节放大 ==')
crop_zoom(40, 445, 340, 505, 6, 'z_font_zh.png')      # 中文「收到，我们开始系统学习」
crop_zoom(340, 445, 640, 505, 6, 'z_font_latin.png')  # 「Transformer」

print('\n== 气泡底部圆角对称性 ==')
def is_green(c):
    r, g, b = c
    return g > r + 15 and g > b + 15 and g < 170 and r < 140
B = 316
for k in range(0, 30, 2):
    y = B - k
    xs = [x for x in range(W) if is_green(im.px(x, y))]
    if xs:
        print(f'  y={y} (bottom-{k:2d} / 逻辑{k/2:4.1f}): left={min(xs)} inset={min(xs)-164} right={max(xs)} insetR={783-max(xs)}')

print('\n== 气泡上下圆角半径对照 ==')
print('  top 侧: top+0 inset=23, top+24 inset=0')
for k in (0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24):
    y = B - 24 + k
    xs = [x for x in range(W) if is_green(im.px(x, y))]
    if xs:
        print(f'  y={y}: inset_left={min(xs)-164}')
