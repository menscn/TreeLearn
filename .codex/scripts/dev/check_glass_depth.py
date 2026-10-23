#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把「玻璃像不像浮在上面、背景有没有质感」量成可复核的数字与判决。

用法（从任意目录）：
    python -X utf8 .codex/scripts/dev/check_glass_depth.py --shot home.png --band bottom
    python -X utf8 .codex/scripts/dev/check_glass_depth.py --shot a.png --band bottom \
            --drift-before a.png --drift-after b.png
    python -X utf8 .codex/scripts/dev/check_glass_depth.py --selftest

退出码：0 判据全成立；1 有判据 FAIL；3 仪器故障（图读不了、悬浮面找不到、场景像素太少）。

为什么要它：2026-10-22 用户复核真机说「玻璃的感觉还不够，不像是浮在上面」。「像不像」不能靠
每次换人眼结论，否则改完再问一遍还是同一句。四条判据把观感拆成像素事实：
J1 玻璃与背景的亮度分离、J2 悬浮面下缘有没有投影暗带、J3 背景自身有没有明暗跨度与色相漂移、
J4 内容滚动时玻璃覆盖的那一行亮度会不会变。Android 与 Web 用同一取区规则与同一阈值，
端差必须显式登记，不给任何一端单独放宽门限。

取区规则（固定，不随图尺寸手挑）：
- 悬浮面：`--band bottom` 在下方 45% 行里、`--band top` 在上方 25% 行里找「比**同一行**左右留白亮
  ≥ 3 个亮度单位」的连续行段（≥8 行算一块面），按锚点方向取离窗口边缘最近的那块；左右缘用同一
  跳变在中线上找，找不到就是贴边全宽面。全宽面所在的行没有留白可取，退到上下缘跳变（下缘跨 0.6%
  高的窗口，因为投影是软坡）。顶部锚定反过来：先走上下缘跳变，再退到留白规则——Web 的全宽顶栏所在
  那行，左右留白要么是它自己、要么是另一层玻璃（常驻侧栏），留白规则在这种几何下读到的是投影恢复坡。
  不用「谁最亮」找面，是因为加深后的雾面本身就是一条大渐变，那样会把
  画面顶部读成一块玻璃（自检的第一版就是这么翻车的，那条负例现在仍在守着）。
- 左右缘的跳变要在中线上两侧各 `0.5% 宽（≥3 列）` 的窗口里都成立：Chromium 对雾面渐变做设备像素
  抖动，逐列 ±4 L 的交替会被单列比较读成面边界（Web 实测把 J1 从 +35 压到 2.6），Android 的 Canvas
  着色没有这层抖动，同一窗口规则在两端的真边缘上给出同一个框。
- 背景参照：面不贴边时取同一行高度的左右页面留白中位；面贴边（全宽）时该行没有非背景像素可取，
  退到四角场景中位。判据是「有没有贴边」这一版式事实，两端共用。
- J1 取悬浮面内部（上下各缩 6px、左右各缩 6% 面宽）中位亮度，减背景参照。
- J2 取悬浮面下缘 2%–5% 高的取样行，逐行减去**同一行**的无投影参照（面不贴边→左右邻位；贴满全宽→
  同列更远的行），读这些差的 P95。逐行对齐是为了不让雾面自身的竖向梯度冒充成影子。
- J3 取四角 5% 宽的方块里的场景像素（排除墨色 L≤150），算 L 与 R−B 的 P95−P5。四角在两端的
  版式里都落在玻璃面之外，是「一定是背景」的位置；这就是不用中部留白做量的原因。
- J4 取两张滚动前后截图在悬浮面内部的同像素亮度差，算 P95。
"""
import argparse
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "parity"))
from png_probe import Img  # noqa: E402  纯 Python PNG 读取，沿用量屏仪器的同一解码器

# 判据阈值（060 需求卡登记，改动必须同步卡片）
J1_MIN_LIFT = 10.0      # 玻璃面比背景至少亮 10 个亮度单位
J2_MIN_SHADOW = 6.0     # 悬浮面下缘暗带至少比背景低 6
J3_MIN_SPAN = 24.0      # 背景亮度 P95-P5 至少 24
J3_MIN_CHROMA = 6.0     # 背景 R-B 的 P95-P5 至少 6（不是均匀灰）
J4_MIN_DRIFT = 8.0      # 滚动时玻璃覆盖行的亮度变化至少 8
INK_FLOOR = 150.0       # 比这暗算正文墨色，不算背景
MIN_SCENE_PIXELS = 240  # 场景像素少于此说明取区被内容占满，判仪器故障

# 取区用的门槛，不是判据阈值：它们只决定「这一行算不算玻璃面」，不参与 PASS/FAIL。
FACE_MIN_LIFT = 3.0     # 一行比同一行的留白亮 3 个亮度单位才算贴在面上的那一段
MIN_FACE_HEIGHT = 8     # 短于此的行段是描边、分隔线或字，不是面
FACE_LOCAL_ROWS = 12    # 「这行比面自己低多少」里的「面自己」= 最近 12 个合格行
FACE_STEP_FALL = 8.0    # 比近旁的面低 8 个亮度单位以上，就是这层面的下缘，不是面里的一行


def _luma(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def _percentile(values, q):
    if not values:
        return None
    ordered = sorted(values)
    pos = (len(ordered) - 1) * q / 100.0
    lo = int(pos)
    hi = min(lo + 1, len(ordered) - 1)
    return ordered[lo] + (ordered[hi] - ordered[lo]) * (pos - lo)


def _median(values):
    return _percentile(values, 50)


def _edges(im, y_range, lo_x, hi_x, lag):
    """返回 (up[y], down[y])：该行比**上一行**更亮、比 `lag` 行后更暗的采样点占比。

    两条边的窗口故意不对称：上缘是背景直接换成更亮的玻璃面，一步到位，逐行差就够；下缘是投影软坡，
    必须跨 lag 行才读得到。这只服务 `_edge_bands` 那条退路，主路径见 `_bright_runs`。
    """
    width = hi_x - lo_x
    step = max(2, width // 240)
    xs = range(lo_x, hi_x, step)
    up, down = {}, {}
    for y in y_range:
        current = [_luma(im.px(x, y)) for x in xs]
        if y - 1 >= 1:
            before = [_luma(im.px(x, y - 1)) for x in xs]
            up[y] = sum(1 for a, b in zip(current, before) if a - b >= 4) / len(current)
        if y + lag < im.h:
            after = [_luma(im.px(x, y + lag)) for x in xs]
            down[y] = sum(1 for a, b in zip(current, after) if a - b >= 4) / len(current)
    return up, down


def _last_of_run(rows, start):
    """从 start 起沿连续行走到这条跳变的最后一行。

    下缘跨 lag 行比较，会把**面的最底 lag−1 行**都判成「下面更暗」，取第一条就提前收口，投影取样
    区于是落在面内部，J2 读成 0。跳变一旦开始就沿连续行走到尽头，收的那一下才是面的真实下缘。
    """
    y = start
    while y + 1 in rows:
        y += 1
    return y


def _gutter_xs(im):
    """同一行上必定属于背景的采样列：左右页面留白（外壳边距之外、四角之内）。"""
    w = im.w
    return list(range(int(w * 0.005), int(w * 0.03))) + list(range(int(w * 0.97), int(w * 0.995)))


def _bright_runs(im, y0, y1, lo_x, hi_x):
    """逐行读「这一行的中部比**同一行**的左右留白亮多少」，返回连续达标的行段 [(top, bottom), ...]。

    找面靠「比同高度的背景亮」而不是「上下缘跳变」，是因为**投影本身就是一条软坡**：加上真投影之后，
    胶囊下缘在十几行里连续变暗，逐行差够不到 4，旧检测器读不出下缘，把整块面当成「贴到画面底」，
    再把投影走完之后回到背景的软上坡读成一块新面——同一段像素被读来读去，取区彻底失真。
    参照取同一行的留白，雾面的竖向梯度（约 30 L / 2652 行）自己抵消，不需要给门槛打折。
    纸纹线（`PaperRuleBackdrop` 每 104 行一道横穿整幅的亮线）会把落在线上那几行的留白抬到和面一样
    亮，读数瞬时归零——那是背景自己的纹理，不是面断了，所以容 0.3% 高的空隙；投影暗带比这宽得多，
    仍然照断。
    「比留白亮」只说明这一行不是背景，不说明它还是不是**同一层**面：内容滚到胶囊底下之后（本文件为
    T-2026-10-22-062 改的这一条），胶囊和它下面、和它一样亮的卡片连成一路亮带直到画面底，只靠绝对
    门槛会把三层玻璃读成一张面，胶囊下缘就此消失、投影无处可取。所以再加一条相对判据：一行比它近旁
    `FACE_LOCAL_ROWS` 个合格行的面低 `FACE_STEP_FALL` 以上，就是这层面走完了（受光描边之后塌进投影）。
    面内部的缓坡由这个滚动窗口自己跟上——真机胶囊从 +34 L 一路降到 +8 L 也没被误断。
    """
    xs = range(lo_x, hi_x, max(2, (hi_x - lo_x) // 120))
    gut = _gutter_xs(im)
    tol = max(4, int(im.h * 0.003))   # 纸纹线每 104 行横穿整幅，落在线上那几行的留白会和面一样亮
    runs, start, last_good, recent = [], None, None, []
    for y in range(y0, y1 + 1):
        lift = (_median([_luma(im.px(x, y)) for x in xs])
                - _median([_luma(im.px(x, y)) for x in gut]))
        # 受光的描边是面**进入**时的尖峰，不是面的高度：刚进门几行拿它当参照会把面自己的头切掉，
        # 所以等这层面攒够一个窗口再开始量落差。
        settled = start is not None and last_good is not None and last_good - start + 1 >= FACE_LOCAL_ROWS
        on_face = lift >= FACE_MIN_LIFT and (not settled or lift >= _median(recent) - FACE_STEP_FALL)
        if on_face:
            start = y if start is None else start
            last_good = y
            recent = (recent + [lift])[-FACE_LOCAL_ROWS:]
        elif start is None or y - last_good > tol:
            if start is not None and last_good - start + 1 >= MIN_FACE_HEIGHT:
                runs.append((start, last_good))
            start, last_good, recent = None, None, []
    if start is not None and last_good - start + 1 >= MIN_FACE_HEIGHT:
        runs.append((start, last_good))
    return runs


def _edge_bands(im, anchor, y0, y1, lo_x, hi_x):
    """退路：只能靠上下缘跳变找面，给「全宽贴边面」用——那种面所在的行没有留白参照可取。

    下缘跨 lag 行比（软坡），上缘逐行比；一条跳变沿连续行走到尽头才收口，否则会把面的最底 lag−1 行
    当成下缘，投影取样区落在面内部，J2 读成 0。
    """
    lag = max(4, int(im.h * 0.006))
    up, down = _edges(im, range(y0, y1 + 1), lo_x, hi_x, lag)
    dark_below = {y for y, ratio in down.items() if ratio >= 0.5}
    bands, open_at = [], None
    for y in sorted(up):
        if open_at is None:
            if up[y] >= 0.5:
                open_at = y
            elif anchor == "top" and y in dark_below and y <= int(im.h * 0.25):
                bands.append((y0, _last_of_run(dark_below, y)))   # 贴上缘的全宽面：只认它的下缘
            continue
        if y in dark_below and y - open_at >= MIN_FACE_HEIGHT:
            bands.append((open_at, _last_of_run(dark_below, y)))
            open_at = None
    if open_at is not None and y1 - open_at >= MIN_FACE_HEIGHT:
        bands.append((open_at, y1))        # 贴窗口下缘的面：下缘在画面外
    return bands


def _sharpest(im, y, lo, hi, want):
    """在粗定位给出的邻域里取最陡的那一列。want="right" = 向右变亮最陡（面的左缘），
    want="left" = 向左变亮最陡（面的右缘）。找不到就返回 None，由调用方退回粗定位自己。"""
    best, edge = 0.0, None
    for x in range(max(1, lo), min(im.w - 1, hi)):
        d = (_luma(im.px(x + 1, y)) - _luma(im.px(x, y))) if want == "right" \
            else (_luma(im.px(x - 1, y)) - _luma(im.px(x, y)))
        if d > best:
            best, edge = d, (x + 1 if want == "right" else x - 1)
    return edge


def _step_edge(im, y, side):
    """从画面外向内找悬浮面的左/右缘：一条边缘必须在 `win` 列的窗口上都成立才算数。

    为什么不能只看相邻两列：Chromium 把 135° 的雾面渐变按设备像素做有序抖动，画面右半部逐列
    交替 ±4 L（实测 1236px 宽的截图在 x 1228..1235 交替 212.6/216.9），1px 的抖动被旧规则读成
    面的边界，取区于是多出几十列背景，`_background` 的「有没有贴边」判据跟着翻车，同一帧首页胶囊
    的 J1 从 33.7 L 读成 2.6 L。Android 那端由 Canvas 直接着色，没有这层抖动，所以同一规则在真
    机上一直对。两侧各取 `win` 列的中位再比跳变，抖动在窗口内互相抵消；真边缘（同一条线实测
    一步跳 38.0 L）照样一步到位。
    中位窗口只回答「这里有没有一条真边」，它跨在边界上时同样成立，所以落点还要回到窗口邻域里
    取最陡的那一列——`band geometry` 那条用例钉的就是这个，否则取区会往外多让两列。
    """
    w = im.w
    win = 2 * max(2, int(w * 0.003))   # 偶数列：抖动逐列交替，奇数窗口的中位会落在抖上去那一侧

    def med(a, b):
        return _median([_luma(im.px(x, y)) for x in range(max(0, a), min(w, b))])

    if side == "left":
        for x in range(win, int(w * 0.5)):
            if med(x, x + win) - med(x - win, x) >= 4:
                edge = _sharpest(im, y, x - win, x + win, "right")
                return x if edge is None else edge
        return 0
    for x in range(w - win, int(w * 0.5), -1):
        if med(x - win, x) - med(x, x + win) >= 4:
            edge = _sharpest(im, y, x - win, x + win, "left")
            return x - 1 if edge is None else edge
    return w - 1


def _band(im, anchor):
    """在锚定方向上找**贴着窗口边缘**的那块悬浮玻璃面。返回 (left, top, right, bottom)。

    两块都亮的面（中部历史卡 vs 底部胶囊）怎么分？悬浮面的定义性事实是「贴着展示区的边缘」，
    所以把所有候选按锚点方向排序，取离窗口边缘最近的那块。

    取区条件（062 之后必须写明白）：这一帧得是**静止帧**——末条停在面下方留白之上的那种状态。页面
    滚到列表开头时，内容会铺满整条带并一直亮到画面底，此时「离窗口边缘最近的面」读到的是内容列
    （真机实测 54,2444..1145,2586，J2 因此报 −3.9），不是胶囊。那是取景不对，不是材质变暗；
    J1-J3 换到静止帧读数，J4 仍然拿两帧对比，门槛一个都不动。

    为什么不能按绝对亮度找面：加深后的雾面本身就是一条大渐变，「谁最亮」会把画面顶部读成一块玻璃——
    自检的第一版就是这么翻车的，那条负例现在仍在守着。亮度的参照因此永远是**同一行的留白**，
    不是整张图的最大值。
    """
    w, h = im.w, im.h
    y0, y1 = (int(h * 0.55), h - 2) if anchor == "bottom" else (1, int(h * 0.25))
    lo_x, hi_x = int(w * 0.12), int(w * 0.88)
    if anchor == "top":
        # 顶部锚定的面在 Web 上是全宽顶栏：它所在的行里根本没有「同一行的页面留白」可取——左右留白
        # 要么就是它自己，要么是另一层玻璃（常驻侧栏）。`_bright_runs` 在这种几何下读到的是投影恢复
        # 坡（Web 宽窗实测 229..258，一条 14 逻辑 px 的带，J1 = −14.1），不是面。`_edge_bands` 里
        # 「贴上缘的全宽面：只认它的下缘」那一条本来就是为这种几何写的，所以顶部锚定先走它。
        # 底部锚定不变：Android 胶囊与 Web 输入卡都不贴左右缘，留白参照成立。
        runs = _edge_bands(im, anchor, y0, y1, lo_x, hi_x) or _bright_runs(im, y0, y1, lo_x, hi_x)
    else:
        runs = _bright_runs(im, y0, y1, lo_x, hi_x) or _edge_bands(im, anchor, y0, y1, lo_x, hi_x)
    # 悬浮面的定义性事实之一是「它下面还有别的东西接住它的投影」。一条一直亮到搜索窗口最后一行的
    # 带不是面，是滚到胶囊底下又继续流出屏幕的内容（T-2026-10-22-062 之后的常态）——留着它，投影
    # 取样区落在画面外，仪器只会报故障。
    # 高度门槛对两条检测器同视：`_bright_runs` 自己已经按 MIN_FACE_HEIGHT 收口，`_edge_bands` 的
    # 「只认下缘」那条退路没有（Web 窄窗实测它先给出一条 2 行的带，面内部因此取不到任何像素）。
    runs = [b for b in runs if b[1] < y1 and b[1] - b[0] + 1 >= MIN_FACE_HEIGHT]
    if not runs:
        raise RuntimeError("在 %s 带里找不到悬浮玻璃面（没有一行比同高度的留白亮 ≥ %.0f，"
                           "也没有上下缘跳变可退），说明材质或取区已变，不能读数"
                           % (anchor, FACE_MIN_LIFT))
    top, bottom = max(runs, key=lambda b: b[1]) if anchor == "bottom" else min(runs, key=lambda b: b[0])
    mid = (top + bottom) // 2
    left = _step_edge(im, mid, "left")
    right = _step_edge(im, mid, "right")
    if right - left < w * 0.5:
        raise RuntimeError("悬浮面宽度只有 %d px（< 50%% 画面宽），取区不可信" % (right - left))
    return left, top, right, bottom


def _background(im, box, scene):
    """该悬浮面所处高度的背景读数。

    不贴边的面（Android 胶囊）取同一行高度的左右页面留白；贴边的面（Web 全宽顶栏）在同一行里
    根本没有非背景像素可取，退到四角场景读数。判据是「面有没有贴边」这一版式事实，两端共用。
    """
    left, top, right, bottom = box
    w = im.w
    if left > int(w * 0.02) and right < int(w * 0.98):
        return _median(_gutter(im, top, bottom))
    return _median(scene)


def _inner(im, box):
    left, top, right = box[0], box[1], box[2]
    bottom = box[3]
    pad_x = int((right - left) * 0.06)
    xs = range(left + pad_x, right - pad_x)
    ys = range(top + 6, bottom - 5)
    return [_luma(im.px(x, y)) for y in ys for x in xs]


def _gutter(im, top, bottom):
    """悬浮面同一高度上的左右页面留白 = 该处的背景读数。列与 `_bright_runs` 用的是同一份。"""
    return [_luma(im.px(x, y)) for y in range(top, bottom + 1) for x in _gutter_xs(im)]


def _corners(im):
    w, h = im.w, im.h
    side = max(12, int(w * 0.05))
    boxes = [(0, 0), (w - side, 0), (0, h - side), (w - side, h - side)]
    luma, chroma = [], []
    for x0, y0 in boxes:
        for y in range(y0, y0 + side):
            for x in range(x0, x0 + side):
                c = im.px(x, y)
                value = _luma(c)
                if value > INK_FLOOR:
                    luma.append(value)
                    chroma.append(float(c[0] - c[2]))
    return luma, chroma


def _projection(im, box):
    """J2：悬浮面下缘的投影暗带，比它**同一行**上的无投影参照低多少。

    参照必须逐行对齐，否则读到的是雾面的竖向梯度而不是影子。面不贴边（Android 胶囊）时，同一行的
    左右邻位就是无投影参照；面贴满全宽（Web 顶栏）时左右没有背景可取，退到同列更远的行。走哪条路由
    「有没有贴边」这一版式事实决定，两端同一规则。第一版只有「更远行」这一条，底部锚定的胶囊下方只剩
    66 px，参照窗口出框，J2 报成「找不到可测的带」——那是仪器看不到，不是影子没有。
    """
    left, top, right, bottom = box
    w, h = im.w, im.h
    depth = max(6, int(h * 0.05))
    pad_x = int((right - left) * 0.05)
    xs = range(left + pad_x, right - pad_x)
    rows = list(range(bottom + 2, min(h, bottom + 2 + depth)))
    if not rows:
        raise RuntimeError("投影取样区（面下第 %d 行起）已在画面外，读数不成立" % (bottom + 2))
    face = [_median([_luma(im.px(x, y)) for x in xs]) for y in rows]
    if left > int(w * 0.02) and right < int(w * 0.98):
        side = int(w * 0.12)
        ref = [_median([_luma(im.px(x, y)) for x in range(max(0, left - side), left - 1)]
                       + [_luma(im.px(x, y)) for x in range(right + 2, min(w, right + 2 + side))])
               for y in rows]
    else:
        far = range(rows[-1] + 1, min(h, rows[-1] + 1 + max(4, int(h * 0.04))))
        far_ref = [_median([_luma(im.px(x, y)) for x in xs]) for y in far]
        ref = [v for v in far_ref if v is not None]
        ref = ref * len(rows) if ref else []
    deltas = [r - f for r, f in zip(ref, face) if r is not None and f is not None]
    if not deltas:
        raise RuntimeError("投影参照取不到（同一行的左右邻位与更远行都出了画面），读数不成立")
    return _percentile(deltas, 95)


def measure(im, anchor):
    scene_luma, scene_chroma = _corners(im)
    box = _band(im, anchor)
    background = _background(im, box, scene_luma)
    if background is None:
        raise RuntimeError("背景参照取不到（四角与留白都被内容占满），读数不成立")
    return {
        "box": box,
        "background": background,
        "j1": _median(_inner(im, box)) - background,
        "j2": _projection(im, box),
        "j3_span": _percentile(scene_luma, 95) - _percentile(scene_luma, 5),
        "j3_chroma": _percentile(scene_chroma, 95) - _percentile(scene_chroma, 5),
        "scene_pixels": len(scene_luma),
    }


def drift(before, after, box):
    """J4：同像素在两张滚动前后截图里的亮度差 P95（只在悬浮面内部取，背景不会自己动）。"""
    left, top, right, bottom = box[:4]
    if (before.w, before.h) != (after.w, after.h):
        raise RuntimeError("两张截图尺寸不同，无法逐像素比对")
    pad_x = int((right - left) * 0.06)
    xs = range(left + pad_x, right - pad_x, 2)
    ys = range(top + 6, bottom - 5, 2)
    deltas = [abs(_luma(before.px(x, y)) - _luma(after.px(x, y))) for y in ys for x in xs]
    return _percentile(deltas, 95)


def judge(reading, j4=None):
    out = []
    out.append((reading["j1"] >= J1_MIN_LIFT,
                "J1 玻璃—背景分离 = %.1f L（需 ≥ %.0f）" % (reading["j1"], J1_MIN_LIFT)))
    out.append((reading["j2"] >= J2_MIN_SHADOW,
                "J2 投影暗带 = %.1f L（需 ≥ %.0f）" % (reading["j2"], J2_MIN_SHADOW)))
    out.append((reading["j3_span"] >= J3_MIN_SPAN,
                "J3 背景亮度跨度 = %.1f L（需 ≥ %.0f）" % (reading["j3_span"], J3_MIN_SPAN)))
    out.append((reading["j3_chroma"] >= J3_MIN_CHROMA,
                "J3 背景彩度跨度 = %.1f（需 ≥ %.0f）" % (reading["j3_chroma"], J3_MIN_CHROMA)))
    if j4 is not None:
        out.append((j4 >= J4_MIN_DRIFT,
                    "J4 玻璃下内容经过 = %.1f L 变化（需 ≥ %.0f）" % (j4, J4_MIN_DRIFT)))
    return out


# ---- 合成图（自检用）：写一张真正的 PNG，不用 PIL，也不靠读数的人挑像素 ----

def write_png(path, w, h, paint):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(paint(x, y))
    head = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    body = zlib.compress(bytes(raw), 9)
    chunks = [b"\x89PNG\r\n\x1a\n",
              _chunk(b"IHDR", head),
              _chunk(b"IDAT", body),
              _chunk(b"IEND", b"")]
    with open(path, "wb") as handle:
        handle.write(b"".join(chunks))


def _chunk(kind, data):
    return (struct.pack(">I", len(data)) + kind + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))


def _scene(w, h, deep):
    """上亮下深的雾面 + 左上暖、右下冷的横向色相漂移；deep 控制跨度。"""
    def paint(x, y):
        t = y / max(1, h - 1)
        base = 250 - int((250 - deep) * t)
        r = min(255, base + int(6 * (1 - x / max(1, w - 1))))
        b = min(255, base - int(6 * (1 - x / max(1, w - 1))))
        return (r, base, b)
    return paint


def _composite(w, h, deep, pill_lift, shadow, scroll=None, under=False):
    DECOY = (90, 700, 500, 760)   # 中部诱饵卡：第一版检测器会把它读成悬浮面，从而 J1/J2 双双假绿

    def paint(x, y):
        scene = _scene(w, h, deep)(x, y)
        if DECOY[0] <= x < DECOY[2] and DECOY[1] <= y < DECOY[3]:
            lift = pill_lift
            return (min(255, scene[0] + lift), min(255, scene[1] + lift), min(255, scene[2] + lift))
        if PILL[0] <= x < PILL[2] and PILL[1] <= y <= PILL[3]:
            if scroll and (x // 24 + y) % 3 == 0:
                v = scene[1] - 26
                return (v, v, v)
            lift = pill_lift
            return (min(255, scene[0] + lift), min(255, scene[1] + lift), min(255, scene[2] + lift))
        base = scene
        if under and y > PILL[3] and 40 <= x < 560:
            # 内容滚到胶囊底下并且一路亮到画面底：它还比背景亮，绝对门槛分不开它和玻璃面
            base = tuple(min(255, c + max(FACE_MIN_LIFT + 1.0, pill_lift - 12)) for c in scene)
        if PILL[3] < y <= PILL[3] + 12 and PILL[0] <= x < PILL[2]:
            drop = int(shadow * (1 - (y - PILL[3] - 1) / 12.0))
            return tuple(max(0, c - drop) for c in base)
        return base
    return paint


def _topbar_scene(w, h, deep):
    """贴上缘的全宽玻璃面（Web 顶栏的形状）。三件事同时成立才钉得住顶部锚定这条规则：
    左侧另有一层常驻玻璃 chrome（同一行的「留白」不是背景）、面下方是一条平投影带、
    投影带下面还有一块比留白亮得多的中部卡——留白规则会先抓到它，把它当成顶栏。"""
    scene = _scene(w, h, deep)

    def paint(x, y):
        if x < 120:
            return (252, 252, 252)
        if y <= 60:
            return (250, 250, 250)
        base = scene(x, y)
        if y <= 120:
            base = tuple(max(0, c - 40) for c in base)
        if 150 <= y < 220 and x < 528:
            base = tuple(min(255, c + 30) for c in scene(x, y))
        return base
    return paint


def _dithered(paint):
    """把雾面按设备像素做 ±2 L 的列抖动（Chromium 对对角渐变做有序抖动时的样子），面本体不抖。
    抖动取零均值，否则这条用例会把「背景被抬亮」混进「取区被抖动骗走」两件事里。"""
    def scoped(x, y):
        c = paint(x, y)
        if PILL[0] <= x < PILL[2] and PILL[1] <= y <= PILL[3]:
            return c
        bump = 2 if x % 2 else -2
        return tuple(min(255, max(0, v + bump)) for v in c)
    return scoped


def selftest(tmp):
    global PILL
    w, h = 600, 1000
    PILL = (60, 840, 540, 900)
    cases, failures = 0, []

    def check(name, ok, detail=""):
        nonlocal cases
        cases += 1
        if not ok:
            failures.append("%s %s" % (name, detail))

    good = os.path.join(tmp, "good.png")
    write_png(good, w, h, _composite(w, h, deep=180, pill_lift=24, shadow=14))
    im = Img(good)
    reading = measure(im, "bottom")
    verdict = judge(reading)
    check("positives all pass", all(ok for ok, _ in verdict), str(verdict))
    check("band geometry matches the drawn pill", list(reading["box"]) == [60, 840, 539, 900],
          str(reading["box"]))

    flat = os.path.join(tmp, "flat.png")
    write_png(flat, w, h, _composite(w, h, deep=240, pill_lift=8, shadow=0))
    bad = judge(measure(Img(flat), "bottom"))
    check("flat backdrop fails J3", any(not ok and "J3" in text for ok, text in bad), str(bad))
    check("no shadow fails J2", any(not ok and "J2" in text for ok, text in bad), str(bad))
    check("thin lift fails J1", any(not ok and "J1" in text for ok, text in bad), str(bad))

    PILL = (60, 940, 540, 970)      # 面下缘只剩 30 行：旧规则在这里取不到参照，J2 直接不报数
    edge = os.path.join(tmp, "near-edge.png")
    write_png(edge, w, h, _composite(w, h, deep=180, pill_lift=24, shadow=14))
    edge_read = measure(Img(edge), "bottom")
    check("surface near the frame edge still gets J2", edge_read["j2"] >= J2_MIN_SHADOW,
          "j2=%.1f" % edge_read["j2"])
    PILL = (60, 840, 540, 900)

    still = os.path.join(tmp, "still.png")
    moving = os.path.join(tmp, "moving.png")
    write_png(still, w, h, _composite(w, h, deep=180, pill_lift=24, shadow=14))
    write_png(moving, w, h, _composite(w, h, deep=180, pill_lift=24, shadow=14, scroll=True))
    box = measure(Img(still), "bottom")["box"]
    d = drift(Img(still), Img(moving), box)
    check("content under glass registers J4", d >= J4_MIN_DRIFT, "drift=%.1f" % d)
    d0 = drift(Img(still), Img(still), box)
    check("identical shots register no drift", d0 < J4_MIN_DRIFT, "drift=%.1f" % d0)

    # 062 之后的常态：内容不只是从面底下经过，还在面底下继续亮到画面底。绝对亮度分不开它和玻璃面，
    # 检测器必须靠「面自己塌下去」和「面下方留得下投影」这两条把它请出去。
    # 这条只钉取区：内容比背景还亮时投影读数是负的，那是合成的版式而不是真机的情形，J2 交给实拍。
    under = os.path.join(tmp, "under.png")
    write_png(under, w, h, _composite(w, h, deep=180, pill_lift=24, shadow=6, under=True))
    under_read = measure(Img(under), "bottom")
    check("content running to the frame bottom does not steal the face",
          list(under_read["box"]) == [60, 840, 539, 900], str(under_read["box"]))
    check("the face keeps separating from the background over running content",
          under_read["j1"] >= J1_MIN_LIFT, "j1=%.1f" % under_read["j1"])

    try:
        _band(Img(still), "top")
        check("missing band raises", False, "top band should not exist here")
    except RuntimeError:
        check("missing band raises", True)

    # 063 取证时撞上的两条几何差，都在合成图上钉住：顶部锚定的全宽面只能靠下缘找（它同一行的
    # 「留白」在 Web 上是另一层玻璃），而 Chromium 的逐列抖动会把单列比较骗成面边界。
    # 门槛一个都没动，改的是「这块像素是不是那层面」。
    bar = os.path.join(tmp, "topbar.png")
    write_png(bar, w, h, _topbar_scene(w, h, deep=180))
    bar_box = list(measure(Img(bar), "top")["box"])
    check("a top-anchored full-width face is found by its lower edge",
          bar_box == [0, 1, 599, 60], str(bar_box))

    dither = os.path.join(tmp, "dither.png")
    write_png(dither, w, h, _dithered(_composite(w, h, deep=180, pill_lift=24, shadow=14)))
    dither_read = measure(Img(dither), "bottom")
    dither_clean = measure(Img(good), "bottom")
    check("a dithered backdrop does not move the face edges",
          list(dither_read["box"]) == [60, 840, 539, 900], str(dither_read["box"]))
    check("dither leaves the readings within its own amplitude",
          abs(dither_read["j1"] - dither_clean["j1"]) <= 2.0
          and abs(dither_read["j2"] - dither_clean["j2"]) <= 2.0,
          "j1 %.1f vs %.1f / j2 %.1f vs %.1f（抖动幅度 ±2 L）" % (
              dither_read["j1"], dither_clean["j1"], dither_read["j2"], dither_clean["j2"]))

    return cases, failures


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    ap = argparse.ArgumentParser(prog="check_glass_depth.py")
    ap.add_argument("--shot")
    ap.add_argument("--band", choices=("bottom", "top"), default="bottom")
    ap.add_argument("--drift-before")
    ap.add_argument("--drift-after")
    ap.add_argument("--selftest", action="store_true")
    try:
        opts = ap.parse_args(argv)
    except SystemExit:
        return 2
    if opts.selftest:
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            cases, failures = selftest(tmp)
        for line in failures:
            print("FAIL %s" % line)
        print("GLASS-DEPTH SELFTEST cases=%d failures=%d" % (cases, len(failures)))
        print("VERDICT: %s" % ("PASS" if not failures else "FAIL"))
        print("EXITCODE=%d" % (1 if failures else 0))
        return 1 if failures else 0
    try:
        if not opts.shot:
            raise RuntimeError("--shot 必填（或用 --selftest）")
        im = Img(opts.shot)
        reading = measure(im, opts.band)
        j4 = None
        if opts.drift_before or opts.drift_after:
            if not (opts.drift_before and opts.drift_after):
                raise RuntimeError("--drift-before 与 --drift-after 必须同时给出")
            j4 = drift(Img(opts.drift_before), Img(opts.drift_after), reading["box"])
    except Exception as exc:  # noqa: BLE001 - 仪器故障统一走 3，不与「判据不成立」的 1 混用
        print("GLASS-DEPTH INSTRUMENT FAULT: %s" % exc)
        print("EXITCODE=3")
        return 3
    if reading["scene_pixels"] < MIN_SCENE_PIXELS:
        print("GLASS-DEPTH INSTRUMENT FAULT: 场景像素只有 %d（<%d），四角被内容占满，读数不成立"
              % (reading["scene_pixels"], MIN_SCENE_PIXELS))
        print("EXITCODE=3")
        return 3
    left, top, right, bottom = reading["box"]
    print("GLASS-DEPTH shot=%s band=%s box=%d,%d..%d,%d scene_pixels=%d"
          % (os.path.basename(opts.shot), opts.band, left, top, right, bottom,
             reading["scene_pixels"]))
    bad = 0
    for ok, text in judge(reading, j4):
        print("%s %s" % ("OK  " if ok else "FAIL", text))
        if not ok:
            bad += 1
    print("VERDICT: %s" % ("PASS" if bad == 0 else "FAIL (%d 条未成立)" % bad))
    print("EXITCODE=%d" % (1 if bad else 0))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
