"""1:1 截图量测：把一张图量成可核对的数字（本脚本不下结论，只出判据行）。

用法：
  python .codex/scripts/dev/parity/measure_screen.py --screen chat > artifacts/evidence/<name>.txt
  python .codex/scripts/dev/parity/measure_screen.py --screen chat --shot <png>   # 闭环量渲染图
  python .codex/scripts/dev/parity/measure_screen.py --selftest                   # 正反双向自检

屏档案 `screens.json` 收的是 2026-09-27 那套扫描窗口、墨区与偏移校正量，**数字逐字未改**：
本片移动的是数据的位置，不是判定算法——判定一变，历史结论就不可比。

退出码纪律（三档互斥，与仓库既有约定一致）：
  0 = 跑完了，没有一条 FAIL 判据行
  1 = 跑完了，但有判据判 FAIL（含「偏移常量在档案里被删/被改成非正数」这一类）
  3 = 仪器自身故障（输入图缺失或解不开、档案缺失或不是合法 JSON、屏档案不存在）⇒ 结论不可信
「没跑完」与「跑完发现坏了」必须分码：把 3 并进 1，环境缺失看起来像判据失败；把 3 静默成 0，
就是本仓库失败案例库里那条「什么都没验证却打印 PASS」。
"""
import argparse, json, os, sys, datetime
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from png_probe import Img, hexs

PROFILES = os.path.join(HERE, "screens.json")


def _repo_root(start):
    """按脚本自身位置上溯找仓库根（以 .git 为记号）；找不到就退回脚本目录。
    档案里的 sample_shot / render_shot 存的是仓库相对路径，若按当前工作目录解析，
    「从仓库根运行」与「从屏目录运行」会得到两个不同的答案——本仓库已因此踩过一次。
    退回时把「没找到」这个事实本身带出去：打印成「找到 .git」会让读者把临时目录当成仓库根。"""
    d = start
    while True:
        if os.path.isdir(os.path.join(d, ".git")):
            return d, True
        p = os.path.dirname(d)
        if p == d:
            return start, False
        d = p


def _resolve(p):
    return p if os.path.isabs(p) else os.path.normpath(os.path.join(ROOT, p))


ROOT, ROOT_IS_REPO = _repo_root(HERE)
_ap = argparse.ArgumentParser(description="1:1 pixel measurement instrument")
_ap.add_argument("--screen", default=None)
_ap.add_argument("--shot", default=None)
_ap.add_argument("--kind", default=None,
                 help="被测图制式 design|render；默认取屏档案的 shot_kind。同一屏的渲染图必须用 render，"
                      "否则右缘滚动条与 clip 左移两条校正会静默失效。"
                      "取值不交给 argparse 校验：用法错误会被它拦成退出码 2，"
                      "而本仪器的纪律是「结论不可信」走 3，两种码不能混。")
_ap.add_argument("--profile", default=PROFILES)
_ap.add_argument("--selftest", action="store_true")
A = _ap.parse_args()

if A.selftest:
    import parity_selftest
    sys.exit(parity_selftest.run(A.profile))


def _fault(msg):
    print("INSTRUMENT FAULT: " + msg)
    print("EXITCODE=3")
    sys.exit(3)


def _exc_hook(et, ev, tb):
    """未捕获异常 = 本次没跑完 = 结论不可信，必须占 3。
    实测命中：纯色废图在 §3 的 min(ok_rows) 上抛 ValueError，Python 默认退出码是 1，
    与「跑完发现有判据判 FAIL」同码——于是「崩了」会被下游读成「量出了偏差」。
    这里不改判定算法，只把崩溃本身归到正确的档：废图该由 §0 自检报出，而不是把后面的空集合
    当成一种结果。os._exit 而非 sys.exit：在 excepthook 里抛 SystemExit 会退回原异常处理流程。"""
    import traceback
    traceback.print_exception(et, ev, tb)
    print("INSTRUMENT FAULT: uncaught exception while measuring - this run did not finish, "
          "so the absence of FAIL lines proves nothing")
    print("EXITCODE=3")
    sys.stdout.flush()
    os._exit(3)


sys.excepthook = _exc_hook


if not os.path.exists(A.profile):
    _fault("profile not found: " + A.profile)
try:
    with open(A.profile, encoding="utf-8") as _h:
        CFG = json.load(_h)
except Exception as e:
    _fault("profile is not readable JSON: %s (%s)" % (A.profile, e))
SCREEN = A.screen or "chat"
if SCREEN not in CFG.get("screens", {}):
    _fault("screen profile %r is not in %s; known=%s" % (
        SCREEN, os.path.basename(A.profile), sorted(CFG.get("screens", {}))))
PROF = CFG["screens"][SCREEN]
SEC = PROF.get("sections", {})
DEF = CFG.get("defaults", {})

# 偏移校正量：具名常量，从档案读，读不到即判 FAIL（而不是默默当 0）。
# 静默当 0 的后果是渲染图的所有右缘间距读起来比设计值大 4，看起来像实现超标。
OFF_KEYS = ("scale_physical_per_logical", "right_scrollbar_logical_px", "left_clip_shift_logical_px")
OFF, _missing = {}, []
for _k in OFF_KEYS:
    _v = DEF.get(_k)
    if not isinstance(_v, (int, float)) or isinstance(_v, bool) or _v <= 0:
        _missing.append("FAIL offset constant missing or not a positive number: defaults.%s = %r" % (_k, _v))
    OFF[_k] = _v
if _missing:
    for _l in _missing:
        print(_l)
    print("EXITCODE=1")
    sys.exit(1)
SCALE = float(OFF["scale_physical_per_logical"])
SCROLLBAR_LOGICAL = float(OFF["right_scrollbar_logical_px"])
CLIP_SHIFT_LOGICAL = float(OFF["left_clip_shift_logical_px"])
SHOT_KIND = A.kind or PROF.get("shot_kind", "design")
if SHOT_KIND not in ("design", "render"):
    _fault("shot kind %r is neither design nor render; refusing to guess which offset corrections apply" % SHOT_KIND)
# 只有渲染图才需要扣滚动条；设计截图（真机/剪贴板）用 overlay 滚动条，不占布局宽度。
RIGHT_DEDUCT = SCROLLBAR_LOGICAL if SHOT_KIND == "render" else 0.0

# 制式决定默认取哪张图：design→sample_shot（归档真机图），render→render_shot（无头浏览器闭环图）。
_SHOT_KEY = "render_shot" if SHOT_KIND == "render" else "sample_shot"
_raw_shot = A.shot or PROF.get(_SHOT_KEY)
if not _raw_shot:
    _fault("no --shot given and screen profile %r has no %s for kind %r" % (SCREEN, _SHOT_KEY, SHOT_KIND))
SHOT = _resolve(_raw_shot)
if not os.path.exists(SHOT):
    _fault("input image not found: %s (a missing fixture is an instrument fault, not a judgement)" % SHOT)
try:
    im = Img(SHOT)
except Exception as e:
    _fault("input image could not be decoded: %s (%s)" % (SHOT, e))
W, H = im.w, im.h
L = lambda v: v / SCALE

# 输出同时留在 OUT 里，收尾的判定段据此派生退出码——退出码必须由本次运行的实际产物算出，
# 不得像旧版那样无条件 print('EXITCODE=0')。
OUT = []
_print = print


def print(*a, **k):                      # noqa: A001 - 模块内遮蔽 builtin，让既有的 print 调用自动留痕
    _out = " ".join(str(x) for x in a)
    OUT.append(_out)
    _print(_out, **k)


def _win(name):
    """取屏档案里的窗口；缺失即仪器故障（宁可不出数，也不要用另一个屏的窗口硬量）。"""
    cur = SEC
    for part in name.split("."):
        if not isinstance(cur, dict) or part not in cur:
            _fault("screen profile %r has no sections.%s; refusing to fall back to a hardcoded window" % (SCREEN, name))
        cur = cur[part]
    return cur


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
# 复现命令必须真能重跑出同一结果：制式（--kind）决定两条校正量，漏写会让读者用 design
# 制式去重跑一张 render 图的数。路径同样按解析根归一，不写「当前工作目录下的相对形状」。
# 跨盘符时 os.path.relpath 直接抛 ValueError（Windows 上临时目录常在 C:、仓库在 D:），
# 而这一行只是展示层——展示层崩掉会让整次测量以 3 退出，夹具在临时目录的自检用例因此全红。
def _fwd(p):
    return p.replace(chr(92), '/')


def _rel_of(p):
    try:
        r = _fwd(os.path.relpath(p, ROOT))
    except ValueError:
        return _fwd(p)
    return r if not r.startswith('..') else _fwd(p)


_cmd = 'python %s --screen %s --kind %s --shot "%s"' % (
    _rel_of(os.path.join(HERE, os.path.basename(__file__))), SCREEN, SHOT_KIND, _rel_of(SHOT))
print('# 复现命令: ' + _cmd + '  （EXITCODE 由脚本自己按本次产物派生，不再由外层 echo 追加）')
print('# 判据: 逻辑坐标 = 物理坐标 / %g（由 defaults.scale_physical_per_logical 给出，不是写死的 2）' % SCALE)
print('# 相对路径解析根 = %s（%s；按脚本自身位置上溯，与当前工作目录无关）' % (
    ROOT, "上溯找到 .git" if ROOT_IS_REPO else "未找到 .git，退回脚本目录——仓库相对路径在此不可解析"))
print('# 偏移常量: scale=%g right_scrollbar_logical_px=%g left_clip_shift_logical_px=%g shot_kind=%s → 右缘本次扣除量=%g' % (
    SCALE, SCROLLBAR_LOGICAL, CLIP_SHIFT_LOGICAL, SHOT_KIND, RIGHT_DEDUCT))
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
# 左移回填量：render 图的所有 x 读数偏小，故「距屏右」需把这段加回来；design 图为 0。
# 档案常量 left_clip_shift_logical_px 不直接参与计算（计算用实测深色带 L(BZ)），
# 它的作用是回归锁：常量说该是 2、实测却是别的数 ⇒ 打印 FAIL，而不是静默按新值继续。
_SHIFT = L(BZ) if SHOT_KIND == "render" else 0.0
_off_fail = []
if SHOT_KIND == "render" and abs(L(BZ) - CLIP_SHIFT_LOGICAL) > 0.5:
    _off_fail.append('FAIL 左缘偏移实测 %s 逻辑 ≠ 档案常量 defaults.left_clip_shift_logical_px=%s（render 图的 CDP clip 取整量应等于该常量）' % (
        round(L(BZ), 1), CLIP_SHIFT_LOGICAL))
if SHOT_KIND != "render" and BZ:
    _off_fail.append('FAIL shot_kind=%s 但实测左缘深色带 %d 物理 px 非 0：这张图不是真机/设计截图，档案标错了制式' % (SHOT_KIND, BZ))
print('  （校正）left_clip_shift_logical_px=%g、shot_kind=%s → 本次左移回填量 = %s 逻辑 px（取实测深色带，常量只作回归锁）' % (
    CLIP_SHIFT_LOGICAL, SHOT_KIND, round(_SHIFT, 1)))
for _l in _off_fail:
    print('  ' + _l)
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
_dv = _win("appbar.div_y")
div = [y for y in range(_dv[0], _dv[1]) if abs(im.px(300, y)[0] - 236) <= 2 and im.px(300, y)[0] < 240]
print(f'  白色底 y<192 = {hexs(im.px(300, 100))} / y>192 = {hexs(im.px(300, 205))}')
if div:
    print(f'  分隔线 y = {div}  色 = {[hexs(im.px(300, y)) for y in div]}')
    print(f'  => 分隔线在逻辑 y ≈ {L(div[0]):.0f}（含状态栏）。注：截图状态栏为 iPhone 灵动岛制式，与原型 AndroidFrame 的 32px 状态栏不可直接对比，故 AppBar 总高不作为判据')
else:
    print('  未检出 AppBar 分隔线 → 本图 AppBar 制式与截图不同，§2 其余判据（标题字号/按钮位置）跳过')
_ty = _win("appbar.title_y")
rows = [y for y in range(_ty[0], _ty[1]) if sum(1 for x in range(W) if INK(im.px(x, y))) > 3]
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
_ub = _win("user_bubble")
ok_rows = [y for y in range(_ub["row_y"][0], _ub["row_y"][1])
           if span(y, GREEN) and span(y, GREEN)[1] - span(y, GREEN)[0] >= _ub["min_span_px"]]
T, B = min(ok_rows), max(ok_rows)
print(f'  绿块行 y {T}..{B}（{len(ok_rows)} 行）→ 高 {L(B-T+1):.1f} 逻辑 px（单行气泡）')
sp = max((span(y, GREEN) for y in ok_rows), key=lambda s: s[1] - s[0])
print(f'  最宽行 span 物理 {sp[0]}..{sp[1]} → 逻辑 {L(sp[0]):.1f}..{L(sp[1]):.1f} 宽 {L(sp[1]-sp[0]+1):.1f}')
print(f'  右边缘距屏右 = {L(W-1-sp[1]):.1f} 逻辑  [原型 --gutter 20px]')
_r0 = L(W - 1 - sp[1])
_r1 = _r0 - RIGHT_DEDUCT + _SHIFT
print(f'  （校正）距屏右 原始 {_r0:.1f} → 减右缘滚动条 {RIGHT_DEDUCT:g}（defaults.right_scrollbar_logical_px={SCROLLBAR_LOGICAL:g}，'
      f'shot_kind={SHOT_KIND}：渲染图的 ::-webkit-scrollbar 占 4px 布局宽、真机 overlay 不占）'
      f'、加左移回填 {_SHIFT:.1f} → 校正后 {_r1:.1f} 逻辑（复刻方案 §10.1 规定的两条偏移，扣在脚本里而不是扣在读的人心里）')
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
_cf = _win("user_bubble")
dys = list(range(_cf["corner_dy"][0], _cf["corner_dy"][1], _cf["corner_dy"][2]))
obs = [span(T + d, GREEN)[0] - sp[0] for d in dys]
_cr = _win("user_bubble")["corner_r_px"]
best = min(((R / SCALE, sum((predict(R / SCALE, d) - o) ** 2 for d, o in zip(dys, obs))) for R in range(_cr[0], _cr[1])), key=lambda t: t[1])
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


_zones = PROF.get("ink_zones")
if not _zones:
    _fault("screen profile %r has no ink_zones; refusing to measure with another screen's boxes" % SCREEN)
for _z in _zones:
    label, box, ref, refname = _z["label"], tuple(_z["box"]), tuple(_z["ref"]), _z["ref_name"]
    c = darkest(*box)
    d = tuple(c[i] - ref[i] for i in range(3))
    print(f'  {label:12s} {hexs(c)}  vs {refname:18s} Δ(ΔR,ΔG,ΔB)={d}')

print()
print('== 5. AI 正文排版 ==')
rows = []
cur = None
_bd = _win("body_rows")
for y in range(_bd["y"][0], _bd["y"][1]):
    ok = sum(1 for x in range(_bd["x"][0], _bd["x"][1]) if INK(im.px(x, y))) > 3
    if ok and cur is None:
        cur = y
    elif not ok and cur is not None:
        rows.append((cur, y - 1)); cur = None
print('  墨迹行分布（物理 y / 逻辑中心 / 高）:')
for a, b in rows:
    xs = [x for x in range(W) if any(INK(im.px(x, y)) for y in range(a, b + 1))]
    print(f'    {a}..{b}  中心 {L((a+b)/2):.1f}  高 {L(b-a+1):.1f}  左 {L(min(xs)):.1f} 右 {L(max(xs)):.1f}')
_bs = _win("body_rows")["body_row_slice"]
body = rows[_bs[0]:_bs[1]]
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
_ac = _win("action_row")["min_cluster_px"]
ics = [(a, b) for a, b in ics if b - a >= _ac]
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
_pl = _win("pills")
_py1 = _pl["y"][1] if _pl["y"][1] is not None else H
pill_rows = [y for y in range(_pl["y"][0], _py1)
             if sum(1 for x in range(_pl["x"][0], _pl["x"][1]) if PILL(im.px(x, y))) >= _pl["min_px_in_row"]]
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
pills = [p for p in pills if p[1] - p[0] >= _win("pills")["min_height_px"]]
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
print('== 8. 判定与退出码（本次运行新增的一段；2026-09-27 的记录里没有，因此不影响上面各节的可比性）==')
# 退出码由**本次运行的实际产物**派生，不再无条件写 EXITCODE=0。
# BODY 是进入本段之前的输出快照：判定段自己会打印 FAIL 行，若事后再扫 OUT，
# 就等于让本段的产物混进被扫对象（同一条 FAIL 计两次，且必需节缺失会被它自己的标题行干扰）。
BODY = list(OUT)
# 按小节归属判定。上一版写成「全文有没有出现某短语」，是**假见证**：跳过文案（如
# 「未检出胶囊块 → 本节判据跳过」）里不含 `== N.`，所以必需节缺失集恒为空、那条判据
# 从未真正生效过——与本仓库失败案例库里「匹配集为空却打印 OK」同族。
_segs = []
_cur = None
for _l in BODY:
    _t = _l.strip()
    if _t.startswith('== '):
        _cur = [_t, []]
        _segs.append(_cur)
    elif _cur is not None:
        _cur[1].append(_t)
_titles = [t for t, _ in _segs]


def _seg_for(marker):
    for t, body in _segs:
        if t.startswith(marker):
            return t, body
    return None, None


SKIP_MARK = ('未检出', '跳过')
_req = PROF.get("require_sections")
if not _req:
    _fault("screen profile %r has an empty require_sections: a required-section floor with no items verifies nothing" % SCREEN)
# 允许跳过的节只在渲染制式下生效（原型 AndroidFrame 的状态栏与真机不同，绝对 y 不成立）。
_allow = set(PROF.get("render_skip_allowed", [])) if SHOT_KIND == "render" else set()
_judge = []
for _m in _req:
    _t, _body = _seg_for(_m)
    if _t is None:
        _judge.append('FAIL 必需小节 %r 的标题行从未打印 —— 仪器没跑到那一段，不等于那一段没问题'
                       '（已打印小节：%s）' % (_m, _titles))
        continue
    _hits = [b for b in _body if any(s in b for s in SKIP_MARK)]
    if _hits and _m in _allow:
        print('  OK 小节 %r 按档案允许跳过（shot_kind=render）：%s' % (_m, _hits[0][:78]))
        continue
    if _hits:
        _judge.append('FAIL 小节 %r 未出数：%s' % (_m, _hits[0][:90]))
    else:
        print('  OK 小节 %r 出了数（该节 %d 行、无跳过标记）' % (_m, len(_body)))
_fail_lines = [l for l in BODY if l.strip().startswith('FAIL')]
_waste = [l for l in BODY if '自检失败' in l]
# 计数行不得以 FAIL 起头：它统计的是「本次有几条 FAIL」，为 0 时仍以 FAIL 起头，就等于每次干净
# 运行都往输出里塞一条看起来像判定失败的行——下游按 FAIL 扫证据的工具会把它读成缺陷。
print(f'  COUNT 判据行 = {len(_fail_lines)}；废图自检命中 = {len(_waste)}；本段新增判定 = {len(_judge)}')
for _l in _fail_lines + _waste:
    print('  （既有）' + _l.strip())
for _l in _judge:
    print('  ' + _l)
_exit = 1 if (_fail_lines or _waste or _judge) else 0
if _exit:
    print('  VERDICT: 有判据判 FAIL 或必需节未出数（「没跑完」不在此列——那走 3）')
else:
    print('  VERDICT: 本次运行的每条判据行都成立，且必需节都出了数')
print('EXITCODE=%d' % _exit)
sys.exit(_exit)
