"""对话页 1:1 复刻：截图实测脚本（唯一职责：把截图量成可核对的数字）。

用法：
  # 量设计截图（默认路径）
  python artifacts/tmp/measure_chat_1to1.py > artifacts/evidence/ux-chat-1to1-measure-2026-09-27.txt
  # 闭环：量原型渲染图，用同一支脚本、同一套判据
  python artifacts/tmp/measure_chat_1to1.py artifacts/evidence/ux-chat-1to1-render-top-2026-09-27.png

判据：图为 2x，逻辑坐标 = 物理坐标 / 2。
注意：渲染图的 AppBar 是原型 AndroidFrame（32px 状态栏），截图的 AppBar 是 iPhone 灵动岛制式，
      高度不同 ⇒ **所有绝对 y 不可跨图比较**；可比的是各元素自身的相对量（高、宽、间距、行距、色值）。
"""
import sys, os, subprocess, datetime
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_probe import Img, hexs

DEFAULT_SHOT = r'C:\Users\23745\.workbuddy\clipboard-images\clipboard-2026-09-27T08-04-23-395Z-c2393438.png'
SHOT = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_SHOT
im = Img(SHOT)
W, H = im.w, im.h
L = lambda v: v / 2.0


def span(y, pred):
    xs = [x for x in range(W) if pred(im.px(x, y))]
    if not xs:
        return None
    best = cur = None
    prev = None
    for x in xs:
        if prev is None or x - prev > 1:
            if cur and (best is None or cur[1] - cur[0] > best[1] - best[0]):
                best = cur
            cur = [x, x]
        else:
            cur[1] = x
        prev = x
    if cur and (best is None or cur[1] - cur[0] > best[1] - best[0]):
        best = cur
    return best


def blocks_rows(y0, y1, pred, minlen):
    out = []
    cur = None
    for y in range(y0, y1):
        ok = span(y, pred) and (span(y, pred)[1] - span(y, pred)[0]) >= minlen
        if ok:
            if cur is None:
                cur = y
        else:
            if cur is not None:
                out.append((cur, y - 1)); cur = None
    if cur is not None:
        out.append((cur, y1 - 1))
    return out


GREEN = lambda c: c[1] > c[0] + 15 and c[1] > c[2] + 15 and c[1] < 170 and c[0] < 140
# 胶囊底：截图为 #EBE9E5(235,233,229)，原型渲染为 #ECEAE4(236,234,228)——两者 Δ(1,1,1)。
# 用容差 ±2，同时显式排除纸感格线色（#EDEBE7 / #EEECE6 系），否则整行格线会被数成胶囊底而"串味"。
PILL = lambda c: all(abs(c[i] - v) <= 2 for i, v in enumerate((235, 233, 229))) and not (
    abs(c[0] - 237) <= 1 and abs(c[1] - 235) <= 1 and abs(c[2] - 231) <= 1)
INK = lambda c: c[0] < 150 and c[1] < 150 and c[2] < 150
GRID = lambda c: abs(c[0] - 237) <= 1 and abs(c[1] - 235) <= 1 and abs(c[2] - 231) <= 1

print('# 对话页 1:1 复刻 — 截图实测证据')
print('# 生成时间: ' + datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'))
print('# 被测对象: ' + SHOT)
_cmd = 'python artifacts/tmp/measure_chat_1to1.py' + ('' if SHOT == DEFAULT_SHOT else ' "' + SHOT.replace('\\', '/') + '"')
print('# 复现命令: ' + _cmd + '  （证据文件末尾的 EXITCODE 由该命令追加：; echo "EXITCODE=$?"）')
print('# 判据: 逻辑坐标 = 物理坐标 / 2（被测图为 2x）')
print('# 适用范围: §2 的 y 判据按「iPhone 灵动岛制式 AppBar」标定；原型渲染图（AndroidFrame 32px 状态栏）')
print('#           会跳过该节。跨图只比较元素自身的相对量（高/宽/间距/行距/色值），不比较绝对 y。')
print()
print('== 0. 图形基本参数 ==')
print(f'  尺寸 = {W}x{H} 通道={im.ch}  → 2x 截图，逻辑画布 {L(W):.0f}x{L(H):.0f}dp')
# 废图自检：整幅近纯色 ⇒ 截图没截到内容（cdp_shot.mjs 用错 captureBeyondViewport 会得到一片纯色）。
# 此时后面所有判据都无意义，必须先把这条报出来，不能让它混进证据文件当"通过"。
_probe = [im.px(10, 50), im.px(10, H // 2), im.px(10, H - 10), im.px(W - 10, 50)]
print(f'  取样点色 (10,50)/(10,H/2)/(10,H-10)/(W-10,50) = {[hexs(c) for c in _probe]}')
if len(set(_probe)) == 1:
    print('  !! 自检失败：整幅近纯色，疑为无效截图（未截到 .screen 内容），以下判据不可用 !!')
# 左缘深色带：CDP 的 clip 原点会按设备像素取整，原型渲染图实测整体左移 2 逻辑 px（4 物理 px），
# 于是图左缘露出设备外框的深色带。它会被 INK 判据当成"正文左边缘 0"，所以必须先量出宽度再校正。
BZ = 0
while BZ < 20 and sum(im.px(BZ, y)[0] for y in range(200, 1200, 50)) / 20 < 120:
    BZ += 1
print(f'  左缘深色带宽度 = {BZ} 物理 = {L(BZ):.1f} 逻辑 px  → 本文件所有"左边缘/距屏左"读数需减去 {L(BZ):.1f} 逻辑 px')
if BZ:
    print(f'     （成因：CDP clip 原点取整导致整图左移；已核对：胶囊左边缘读数 {L(BZ) + 20:.1f} - {L(BZ):.1f} = 20.0 = --gutter）')
print()

print('== 1. 页面底色与纸感横格线 ==')
bg = im.px(10, 1250)
print(f'  页面底色 (10,1250) = {hexs(bg)}   [原型 --bg #F4F1EB，ΔR={bg[0]-244},ΔG={bg[1]-241},ΔB={bg[2]-235}]')
gy = [y for y in range(1150, H) if GRID(im.px(10, y))]
if len(gy) >= 2:
    steps = [gy[i + 1] - gy[i] for i in range(len(gy) - 1) if gy[i + 1] - gy[i] > 4]
    print(f'  格线行 y = {gy[:8]}')
    print(f'  格线周期 = {steps[:4]} 物理 → {[L(s) for s in steps[:4]]} 逻辑  [原型 repeating-linear-gradient 32px]')
    print(f'  格线色 = {hexs(im.px(10, gy[0]))}')
else:
    print(f'  未检出格线（命中行 {gy}）')
print()

print('== 2. AppBar ==')
# 本节所有 y 判据都是按「iPhone 灵动岛制式 AppBar（分隔线在逻辑 y≈95）」标定的。
# 若被测图是原型渲染图（AndroidFrame 32px 状态栏），这些绝对 y 不成立，本节整体跳过，
# 不输出误导性数字（AppBar 总高本来就已声明不作为判据）。
div = [y for y in range(150, 260) if abs(im.px(300, y)[0] - 236) <= 2 and im.px(300, y)[0] < 240]
print(f'  白色底 y<192 = {hexs(im.px(300, 100))} / y>192 = {hexs(im.px(300, 205))}')
if div:
    print(f'  分隔线 y = {div}  色 = {[hexs(im.px(300, y)) for y in div]}')
    print(f'  => 分隔线在逻辑 y ≈ {L(div[0]):.0f}（含状态栏）。注：截图状态栏为 iPhone 灵动岛制式，与原型 AndroidFrame 的 32px 状态栏不可直接对比，故 AppBar 总高不作为判据')
else:
    print('  未检出 AppBar 分隔线 → 本图 AppBar 制式与截图不同，§2 其余判据（标题字号/按钮位置）跳过')
rows = [y for y in range(90, 190) if sum(1 for x in range(W) if INK(im.px(x, y))) > 3]
if rows:
    print(f'  标题/图标墨迹行 y {min(rows)}..{max(rows)} 高 {L(max(rows)-min(rows)+1):.1f} 逻辑')
    cols = []
    cur = None
    for x in range(0, W):
        n = sum(1 for y in range(min(rows), max(rows) + 1) if INK(im.px(x, y)))
        if n > 0 and cur is None:
            cur = x
        elif n == 0 and cur is not None:
            cols.append((cur, x - 1)); cur = None
    zh = [(a, b) for a, b in cols if b - a >= 8]
    print(f'  墨迹簇前 6 个（逻辑）: {[(round(L(a),1), round(L(b),1)) for a,b in zh[:6]]}')
    if zh:
        print(f'  第 1 簇 = 返回箭头（逻辑 {L(zh[0][0]):.1f}..{L(zh[0][1]):.1f}）；其后 3 簇 = 标题首 3 个汉字')
    if len(zh) >= 5:
        pitch = [zh[i + 1][0] - zh[i][0] for i in range(1, 4)]
        print(f'  中文标题字距 = {pitch} 物理 → {[L(p) for p in pitch]} 逻辑  [原型 --fs-h2 16px，中文字宽≈字号]')
    ics = [(a, b) for a, b in cols if b - a >= 8]
    print(f'  右侧图标簇（逻辑）: {[(round(L(a),1), round(L(b),1)) for a,b in ics[-2:]]}  [原型 ITree / IMap 两个 icon-btn]')
else:
    print('  未检出标题墨迹行 → 跳过标题/图标簇判据')
print()

print('== 3. 用户气泡 ==')
ok_rows = [y for y in range(150, 700) if span(y, GREEN) and span(y, GREEN)[1] - span(y, GREEN)[0] >= 300]
T, B = min(ok_rows), max(ok_rows)
print(f'  绿块行 y {T}..{B}（{len(ok_rows)} 行）→ 高 {L(B-T+1):.1f} 逻辑 px（单行气泡）')
sp = max((span(y, GREEN) for y in ok_rows), key=lambda s: s[1] - s[0])
print(f'  最宽行 span 物理 {sp[0]}..{sp[1]} → 逻辑 {L(sp[0]):.1f}..{L(sp[1]):.1f} 宽 {L(sp[1]-sp[0]+1):.1f}')
print(f'  右边缘距屏右 = {L(W-1-sp[1]):.1f} 逻辑  [原型 --gutter 20px]')
from collections import Counter
cnt = Counter()
for y in range(T + 20, B - 20):
    for x in range(sp[0] + 20, sp[1] - 20):
        cnt[im.px(x, y)] += 1
top3 = cnt.most_common(3)
print(f'  内部纯色 Top3 = {[(hexs(c), n) for c, n in top3]}')
bc = top3[0][0]
print(f'  => 气泡底色 = {hexs(bc)}  [原型 --green #2B6E45，ΔR={bc[0]-43},ΔG={bc[1]-110},ΔB={bc[2]-69}]')
print('  圆角：四角 inset 对称性（inset = 该行绿区起点相对最宽行左边缘的偏移）')
print('    top 侧 :', end=' ')
print(', '.join(str(span(T + k, GREEN)[0] - sp[0]) for k in range(0, 26, 4)))
print('    bottom :', end=' ')
print(', '.join(str(span(B - k, GREEN)[0] - sp[0]) for k in range(0, 26, 4)))
# 圆角半径：不要用解析反解或"inset 归零点"——抗锯齿让每个点都有 ±2 误差，
# 而 R 对误差极敏感（实测反解会在 8~24 物理之间乱跳）。正确做法是前向拟合：
# 对候选 R 预测整条 inset(dy) 曲线，取残差平方和最小者。
import math
def predict(R, dy):
    if dy >= R:
        return 0.0
    return R - math.sqrt(max(0.0, 2 * R * dy - dy * dy))
dys = list(range(0, 40, 2))
obs = [span(T + d, GREEN)[0] - sp[0] for d in dys]
best = min(((R / 2.0, sum((predict(R / 2.0, d) - o) ** 2 for d, o in zip(dys, obs))) for R in range(8, 100)), key=lambda t: t[1])
print(f'    圆角前向拟合：最佳 R = {best[0]:.1f} 物理 = {L(best[0]):.1f} 逻辑 px（残差平方和 {best[1]:.1f}）')
print('    拟合明细（dy 物理 / 实测 inset / 模型预测）:')
for d, o in zip(dys[:10], obs[:10]):
    print(f'      dy={d:2d}  实测={o:3d}  预测={predict(best[0], d):5.1f}')
print(f'    [原型 .msg-user border-radius 现值 14px（--r-md 四角统一）]')
print()

print('== 4. 墨色对照（各区最暗像素）==')


def darkest(x0, x1, y0, y1):
    best = None
    for y in range(y0, y1):
        for x in range(x0, x1):
            p = im.px(x, y)
            if best is None or sum(p) < sum(best):
                best = p
    return best


for label, box, ref, refname in [
    ('✦ 星形图标', (44, 71, 378, 406), (22, 64, 39), 'green-deep #164027'),
    ('「知芽」文字', (72, 119, 378, 406), (22, 64, 39), 'green-deep #164027'),
    ('正文文字', (45, 770, 465, 480), (22, 36, 28), 'ink #16241C'),
    ('动作行图标', (43, 71, 768, 800), (98, 112, 103), 'ink3 #627067'),
    ('胶囊内分叉图标', (352, 380, 875, 935), (98, 112, 103), 'ink3 #627067'),
    ('用户气泡', (300, 400, 260, 280), (43, 110, 69), 'green #2B6E45')]:
    c = darkest(*box)
    d = tuple(c[i] - ref[i] for i in range(3))
    print(f'  {label:12s} {hexs(c)}  vs {refname:18s} Δ(ΔR,ΔG,ΔB)={d}')

print()
print('== 5. AI 正文排版 ==')
rows = []
cur = None
for y in range(340, 830):
    ok = sum(1 for x in range(40, 790) if INK(im.px(x, y))) > 3
    if ok and cur is None:
        cur = y
    elif not ok and cur is not None:
        rows.append((cur, y - 1)); cur = None
print('  墨迹行分布（物理 y / 逻辑中心 / 高）:')
for a, b in rows:
    xs = [x for x in range(W) if any(INK(im.px(x, y)) for y in range(a, b + 1))]
    print(f'    {a}..{b}  中心 {L((a+b)/2):.1f}  高 {L(b-a+1):.1f}  左 {L(min(xs)):.1f} 右 {L(max(xs)):.1f}')
body = rows[1:6]
if len(body) >= 2:
    pitches = [body[i + 1][0] - body[i][0] for i in range(len(body) - 1)]
    print(f'  正文行距 = {pitches} 物理 → {[L(p) for p in pitches]} 逻辑  [原型 --lh-read 28px]')
if len(rows) >= 2:
    print(f'  正文左边缘 {L(min(x for x in range(W) if any(INK(im.px(x, y)) for y in range(rows[1][0], rows[1][1])))):.1f} 逻辑  [原型 --gutter 20px]')
    tagmid = L((rows[0][0] + rows[0][1]) / 2)
    b1mid = L((rows[1][0] + rows[1][1]) / 2)
    print(f'  ✦知芽标签中心 {tagmid:.1f} → 正文首行中心 {b1mid:.1f}；中心距 {b1mid-tagmid:.1f} 逻辑')
    print('    [原型 .ai-tag margin-bottom s3(12) + 行高 14 + 正文行高 28 → 中心距 33]')
else:
    print('  未检出足够墨迹行 → 跳过正文行距/左边缘判据')
print()

print('== 6. 回答动作行 ==')
act = rows[-1] if rows else None
if act is None:
    print('  无动作行候选（§5 未检出墨迹行）→ 本节判据跳过'); print()
    act = (0, 0)
GR = lambda c: c[0] < 215 and c[1] < 215 and c[2] < 215 and abs(c[0]-c[1]) < 25 and abs(c[1]-c[2]) < 25
cur = None
ics = []
for x in range(0, W):
    n = sum(1 for y in range(act[0] - 4, act[1] + 6) if GR(im.px(x, y)))
    if n > 0 and cur is None:
        cur = x
    elif n == 0 and cur is not None:
        ics.append((cur, x - 1)); cur = None
ics = [(a, b) for a, b in ics if b - a >= 10]
print(f'  图标簇（物理/逻辑/中心）:')
for a, b in ics[:4]:
    print(f'    {a}..{b}  逻辑 {L(a):.1f}..{L(b):.1f}  中心 {L((a+b)/2):.1f}')
if len(ics) >= 3:
    ctr = [L((a + b) / 2) for a, b in ics[:3]]
    print(f'  图标中心 {[round(c,1) for c in ctr]} 间距 {[round(ctr[i+1]-ctr[i],1) for i in range(len(ctr)-1)]} 逻辑  [原型 .ai-act 44px 宽]')
if len(rows) >= 2:
    print(f'  正文末行底 {L(rows[-2][1]):.1f} → 动作行图标顶 {L(act[0]):.1f}  [原型 .ai-actions margin-top s2(8)]')
print()

print('== 7. 推荐问题 ==')
# 胶囊块判定：该行在胶囊横向范围内至少有 60 个胶囊底色像素（文字打断不会低于该值）
pill_rows = [y for y in range(840, H) if sum(1 for x in range(40, 420) if PILL(im.px(x, y))) >= 100]
pills = []
cur = None
for y in pill_rows:
    if cur is None:
        cur = [y, y]
    elif y - cur[1] <= 10:
        cur[1] = y
    else:
        pills.append(tuple(cur)); cur = [y, y]
if cur:
    pills.append(tuple(cur))
pills = [p for p in pills if p[1] - p[0] >= 40]
print(f'  胶囊块数 = {len(pills)}')
for i, (a, b) in enumerate(pills):
    xs2 = [x for x in range(W) if any(PILL(im.px(x, y)) for y in (a + 6, (a + b) // 2, b - 6))]
    print(f'    胶囊{i+1}: y {a}..{b} 高 {L(b-a+1):.1f} 逻辑; x {min(xs2)}..{max(xs2)} 逻辑 {L(min(xs2)):.1f}..{L(max(xs2)):.1f} 宽 {L(max(xs2)-min(xs2)+1):.1f}')
if len(pills) >= 2:
    gaps = [pills[i + 1][0] - pills[i][1] - 1 for i in range(len(pills) - 1)]
    print(f'  胶囊垂直间距 = {gaps} 物理 → {[L(g) for g in gaps]} 逻辑  [原型 .sugg-list gap s2(8px)]')
if pills:
    first = pills[0]
    xs2 = [x for x in range(W) if any(PILL(im.px(x, y)) for y in (first[0] + 6, (first[0] + first[1]) // 2, first[1] - 6))]
    print(f'  首胶囊左边缘 {L(min(xs2)):.1f} 逻辑  [原型 --gutter 20px]')
    txt = [x for x in range(W) if any(INK(im.px(x, y)) for y in range(first[0], first[1]))]
    if txt:
        print(f'  首胶囊文字/图标墨迹 {L(min(txt)):.1f}..{L(max(txt)):.1f} 逻辑 → 左内边距 {L(min(txt)-min(xs2)):.1f}  [原型 s4 16px]')
    print(f'  动作行（逻辑）{L(act[0]):.1f}..{L(act[1]):.1f} → 首胶囊顶 {L(first[0]):.1f}')
    print(f'  动作行按钮盒底 = 图标中心 {L((act[0]+act[1])/2):.1f} + 22 = {L((act[0]+act[1])/2)+22:.1f} → 首胶囊顶距 {L(first[0]) - (L((act[0]+act[1])/2)+22):.1f} 逻辑  [原型 .sugg-block margin-top s4(16px)]')
else:
    print('  未检出胶囊块 → 本节判据跳过（可能：该图 y 区间内无推荐问题，或胶囊底色与截图不同）')
print()
print('EXITCODE=0')
