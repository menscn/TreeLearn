import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_probe import Img, near, hexs

P = r'C:\Users\23745\.workbuddy\clipboard-images\clipboard-2026-09-27T08-04-23-395Z-c2393438.png'
im = Img(P)
print(f'== 图片尺寸 {im.w}x{im.h}, channels={im.ch} ==')

print('\n-- 背景采样（x=8 竖列，每 40px）--')
for y in range(4, im.h, 40):
    print(f'  y={y:4d}  x=8:{hexs(im.px(8, y))}  x={im.w//2}:{hexs(im.px(im.w//2, y))}  x=w-8:{hexs(im.px(im.w-8, y))}')

print('\n-- AppBar 白底与分隔线探测（x=30 竖列 y=100..200）--')
for y in range(100, 215, 2):
    print(f'  y={y:4d} {hexs(im.px(30, y))}  {hexs(im.px(im.w//2, y))}  {hexs(im.px(im.w-30, y))}')
