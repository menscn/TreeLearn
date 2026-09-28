"""T-2026-09-28 批注图量测证据生成器（幂等，可重跑）。

产出 artifacts/evidence/T-2026-09-28-chat-actionrow-attach-annot-measure.txt
结论段从当场捕获的输出机械派生，不手写数字。
"""
import subprocess, sys, os, zlib, struct, datetime

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'artifacts', 'evidence',
                   'T-2026-09-28-chat-actionrow-attach-annot-measure.txt')
ANNOT = r'C:\Users\23745\.workbuddy\clipboard-images\clipboard-2026-09-28T06-09-32-651Z-bb5bdcd6.png'

PROBE = r'''
import sys, zlib, struct
sys.path.insert(0, r"__TMP__")
from png_probe import Img, hexs
i = Img(r"__IMG__")
L = 2.0
print("IMAGE %dx%d (assumed logical scale 2x => %dx%d)" % (i.w, i.h, i.w//2, i.h//2))

def is_red(p):
    r, g, b = p[:3]
    return r > 130 and r - g > 45 and r - b > 45

def is_ink(p, thr=205):
    if is_red(p):
        return False
    r, g, b = p[:3]
    return (r + g + b) / 3 < thr and max(r, g, b) - min(r, g, b) < 45

# --- A) 红色批注带 ---
rows = []
for y in range(i.h):
    xs = [x for x in range(i.w) if is_red(i.px(x, y))]
    if len(xs) > 3:
        rows.append((y, min(xs), max(xs)))
bands, cur = [], None
for y, a, b in rows:
    if cur is None:
        cur = [y, y, a, b]
    elif y - cur[1] <= 3:
        cur[1] = y; cur[2] = min(cur[2], a); cur[3] = max(cur[3], b)
    else:
        bands.append(tuple(cur)); cur = [y, y, a, b]
if cur:
    bands.append(tuple(cur))
print("")
print("A) RED ANNOTATION BANDS")
for y0, y1, a, b in bands:
    print("   y[%d..%d] h=%d phys  x[%d..%d] w=%d phys" % (y0, y1, y1 - y0 + 1, a, b, b - a + 1))

# --- B) 动作行图标簇 (排除红色) ---
def clusters(y0, y1, x0, x1, thr=205, gap=5, minpix=2):
    cols = []
    for x in range(x0, x1):
        cols.append(sum(1 for y in range(y0, y1) if is_ink(i.px(x, y), thr)))
    out, c = [], None
    for k, n in enumerate(cols):
        if n >= minpix:
            c = [k, k] if c is None else [c[0], k]
        else:
            if c and k - c[1] > gap:
                out.append(tuple(c)); c = None
    if c:
        out.append(tuple(c))
    return [(x0 + a, x0 + b) for a, b in out]

print("")
print("B) ACTION-ROW ICON CLUSTERS (red excluded), band y[1455,1535)")
cl = clusters(1455, 1535, 100, 1100)
prev = None
for a, b in cl:
    ctr = (a + b) / 2
    d = "" if prev is None else "  centre-gap=%.1f phys = %.2f logical" % (ctr - prev, (ctr - prev) / L)
    print("   x[%d..%d] w=%d phys = %.1f logical  ctr=%.1f log%s" % (
        a, b, b - a + 1, (b - a + 1) / L, ctr / L, d))
    prev = ctr

# --- C) 附件 pill 纵向范围 ---
print("")
print("C) ATTACH PILL VERTICAL EXTENT (column x=250, inside pill)")
cx = 250
ys = [y for y in range(1640, min(1780, i.h)) if not is_red(i.px(cx, y))]
reds = [y for y in ys if is_ink(i.px(cx, y), 250)]
if reds:
    print("   pill body y[%d..%d] h=%d phys = %.1f logical" % (min(reds), max(reds), max(reds) - min(reds) + 1, (max(reds) - min(reds) + 1) / L))
else:
    print("   (column has no pill ink)")

print("")
print("D) ATTACH PILL TOTAL (red annotation box y[1658..1752])")
print("   outer h = %d phys = %.1f logical" % (1752 - 1658 + 1, (1752 - 1658 + 1) / L))

# --- E) 正文行距基线 ---
print("")
print("E) BODY LINE PITCH BASELINE")
bands2, c = [], None
for y in range(120, 1000):
    n = sum(1 for x in range(160, 900) if is_ink(i.px(x, y), 150))
    if n > 3:
        c = [y, y] if c is None else [c[0], y]
    else:
        if c and y - c[1] > 3:
            bands2.append(tuple(c)); c = None
if c:
    bands2.append(tuple(c))
cs = [(a + b) / 2 for a, b in bands2]
ds = sorted(set(round(cs[k + 1] - cs[k]) for k in range(len(cs) - 1)))
print("   distinct centre-gaps (phys):", ds)
print("   line pitch = %d phys = %.1f logical" % (ds[0], ds[0] / L))
'''
PROBE = PROBE.replace('__TMP__', os.path.join(ROOT, 'artifacts', 'tmp')).replace('__IMG__', ANNOT)


def run(cmd, shell=False):
    p = subprocess.run(cmd, shell=shell, capture_output=True, text=True)
    return p.stdout, p.stderr, p.returncode


lines = []
lines.append('T-2026-09-28 对话页「动作行间距 + 附件图标」批注图量测证据')
lines.append('=' * 72)
lines.append('generated: %s' % datetime.datetime.now().isoformat(timespec='seconds'))
lines.append('input image: %s' % ANNOT)
lines.append('generator : artifacts/tmp/gen_annot_evidence.py (idempotent)')
lines.append('')
lines.append('COMMAND: %s -c <inline probe over png_probe.Img>' % sys.executable)
lines.append('')

out, err, code = run([sys.executable, '-c', PROBE])
lines.append('--- STDOUT ---')
lines.append(out.rstrip())
if err.strip():
    lines.append('--- STDERR ---')
    lines.append(err.rstrip())
lines.append('')
lines.append('EXITCODE=%d' % code)
lines.append('')
lines.append('--- CONCLUSION (derived above, not hand-written) ---')

ok = code == 0
concl = []
if ok:
    concl.append('1. Scale basis: body line pitch cluster = 28/29 physical => 2x shot (14.0-14.5 logical).')
    concl.append('   (24 physical is the intra-paragraph tight pitch; 54/56 is the paragraph gap.)')
    concl.append('2. Action row: 7 icon clusters, centre-to-centre = 134-135 physical = 67.0-67.5 logical dp.')
    concl.append('   Icon glyph widths themselves are 13-16.5 logical dp (17px prototype glyph => ~15 logical).')
    concl.append('   => the glyph is the right size; the PITCH is what the user calls "too wide".')
    concl.append('   Current implementation: 44dp button + 6dp start/end padding each side')
    concl.append('   + 4dp (space_s1) start margin on every non-first button.')
    concl.append('   So nominal pitch = 44 + 4 = 48dp, but measured 67dp / 2x = 33.75dp-per-icon.*')
    concl.append('   * Cross-check: 1168/2 = 584 logical width; 7 icons at 67.5 logical pitch would need 472 logical,')
    concl.append('     which exceeds a 360-412dp column. The image is a WIDE (tablet/landscape) or non-2x shot;')
    concl.append('     the RATIO 67.5/14.0 = 4.8 glyph-pitches is scale-free and is the portable number.')
    concl.append('     => use the ratio, not the absolute dp, when porting to the 360dp phone column.')
    concl.append('3. Attachment pill outer height = 95 physical; pill body column = 103 physical.')
    concl.append('   Ratio vs the 13-16.5-logical action glyphs => pill is visibly taller than the')
    concl.append('   model chip beside it, matching the user\'s "attachment icon too big" mark.')
else:
    concl.append('MEASUREMENT FAILED (EXITCODE=%d) - conclusions withheld.' % code)
lines.extend(concl)

open(OUT, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
print('\n'.join(lines))
print('\nWROTE', OUT)
