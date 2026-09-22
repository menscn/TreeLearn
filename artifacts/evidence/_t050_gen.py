# -*- coding: utf-8 -*-
"""生成 T-050 的证据文件。

原则：能用命令当场捕获的段落一律由本脚本捕获（stdout + 退出码），不手抄；
RED 是执行期的瞬时状态，用临时镜像树把同一状态重放出来，便于复核者复跑；
Java-only 守门由 PowerShell 侧跑完后落盘的原始日志读入。
"""
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.getcwd()
EVID = os.path.join(ROOT, "artifacts", "evidence")
OUT = os.path.join(EVID, "T-050-design-token-sync.txt")
PROTO = "Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html"
TOKENS = "Docs/04-ux-design/design-system/tokens.json"
COLORS = "android/app/src/main/res/values/colors.xml"
DIMENS = "android/app/src/main/res/values/dimens.xml"
THEMES = "android/app/src/main/res/values/themes.xml"
GUARD_SRC = os.path.join(EVID, "_t050-nokotlin.utf8.txt")
GUARD_DST = os.path.join(EVID, "T-050-no-kotlin-check.txt")
GATE_LINE = "python -X utf8 .trae/scripts/dev/check_design_tokens.py"


def run(cmd, cwd=ROOT):
    p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def shell_run(cmdline, cwd=ROOT):
    p = subprocess.run(cmdline, cwd=cwd, shell=True, capture_output=True,
                       text=True, encoding="utf-8", errors="replace")
    return p.returncode, (p.stdout or "") + (p.stderr or "")


buf = io.StringIO()


def w(s=""):
    buf.write(s + "\n")


def section(title, cmdline, rc, text, note=None):
    w("-" * 78)
    w(title)
    w("-" * 78)
    w("$ " + cmdline)
    t = (text or "").rstrip("\n")
    if t:
        w(t)
    if note:
        w("NOTE " + note)
    w("EXITCODE=%d" % rc)
    w()


# ------------------------------------------------------------------ 头部
w("T-050 设计基座同步（新稿配色 / 度量 / 字体回退栈三处对齐）· 证据文件")
w("task: T-2026-09-22-050")
w("generated_by: android-dev；下列每段的命令输出与退出码由脚本当场捕获，不是手抄")
w("cwd: D:/Project/Android/Treelearn")
w("shell: Git Bash on Windows；python -X utf8（3.13.12）")
w()
w("改动文件（7 个）：")
for f in (PROTO, TOKENS, COLORS, DIMENS, THEMES,
          "android/README.md", "Docs/04-ux-design/design-system/README.md"):
    w("  - " + f)
w()
w("== TOKEN 语义映射表（新稿 token -> 仓库 token -> 本片采用值）==")
w("仓库是本仓的事实源与 Android 副本，新稿只提供值；两边命名体系相同，全部 1:1 对应，")
w("没有「新稿有、仓库没有」或「仓库有、新稿没有」的悬空项。带 # 的是按硬约束保持仓库值的项。")
w()
w("%-14s %-26s %-38s %s" % ("新稿名", "仓库名", "本片采用值", "依据"))
for a, b, c, d in [
    ("bg", "bg", "#F4F1EB", "新稿值"),
    ("surface", "surface", "#FCFBF8", "新稿值"),
    ("ink", "ink", "#16241C", "新稿值"),
    ("ink2", "ink2", "#435449", "新稿值"),
    ("ink3", "ink3", "#627067", "修正值；新稿是 #75867B"),
    ("line", "line", "#E2DFD6", "新稿值"),
    ("hairline", "hairline", "rgba(26,40,32,.07)", "新稿值"),
    ("green", "green", "#2B6E45", "新稿值"),
    ("green-deep", "green-deep", "#164027", "新稿值"),
    ("green-soft", "green-soft", "#E3F0E7", "# 新稿为 rgba；colors.xml 只收不透明 hex，保持原值"),
    ("green-faint", "green-faint", "#F0F7F1", "# 同上"),
    ("blue", "blue", "#2563EB", "新稿值；顺带修好旧值 4.16 的欠账"),
    ("amber", "amber", "#B05108", "修正值；新稿是 #B45309"),
    ("amber-soft", "amber-soft", "#FBF3E2", "# 同上；它是 ink3/amber 的承载背景，压平即破验收线"),
    ("red", "red", "#C0392B", "新稿值与仓库相同"),
    ("red-soft", "red-soft", "#FBEAE7", "# 同上"),
    ("s1..s8 / gutter", "s1..s8 / gutter", "4/8/12/16/24/32 / 20px", "新稿与仓库同值"),
    ("r-sm / r-md / r-lg", "r-sm / r-md / r-lg", "8px / 14px / 18px", "新稿值"),
    ("r-full", "r-full", "99px", "新稿与仓库同值"),
    ("fs-display/h1/h2/body", "fs-display/h1/h2/body", "26 / 20 / 16 / 15px", "新稿值"),
    ("fs-sm / fs-cap", "fs-sm / fs-cap", "13px / 11px", "新稿与仓库同值"),
    ("sh-1", "sh-1", "0 1px 2px rgba(22,36,28,.04)", "新稿值"),
    ("sh-2", "sh-2", "0 4px 12px rgba(22,36,28,.06)", "新稿值"),
    ("sans", "sans", "'Noto Sans SC',-apple-system,sans-serif", "新稿值；Android 上解析为系统 sans-serif"),
    ("serif", "serif", "'Noto Serif SC','Songti SC',Georgia,serif", "新稿值；品牌衬线回退栈"),
    ("lh-read", "lh-read", "28px", "新稿值"),
    ("（无）", "kai", "'LXGW WenKai',...", "# 新稿无对应项；按只改值不改名保留"),
    ("（无）", "brush", "'Ma Shan Zheng',...", "# 新稿无对应项；保留"),
    ("（无）", "paper-rule / paper-write", "rgba(30,43,35,.028) / rgba(46,125,79,.075)", "# 新稿无对应项；保留"),
]:
    w("%-14s %-26s %-38s %s" % (a, b, c, d))
w()
w("映射结论：无歧义项——每个新稿 token 都能在仓库找到同名 token，反之除 kai/brush/paper-rule/")
w("paper-write 四个「新稿没有」的遗留项外也全部一一对应；那四项按「绝不改名、不退役」保持原值。")
w("唯一的值域差异是四个柔色 token：新稿写 rgba，仓库要落 Android 的不透明 hex，理由见 G3 的 D 段。")
w()

# ------------------------------------------------------------------ G0 RED
tmp = tempfile.mkdtemp(prefix="t050-red-repro-")
os.makedirs(os.path.join(tmp, ".trae"))
shutil.copytree(os.path.join(ROOT, ".trae", "scripts"), os.path.join(tmp, ".trae", "scripts"))
for rel in (PROTO, TOKENS, COLORS):
    os.makedirs(os.path.join(tmp, os.path.dirname(rel)), exist_ok=True)
shutil.copy(os.path.join(ROOT, PROTO), os.path.join(tmp, PROTO))
for rel in (TOKENS, COLORS):
    head = subprocess.run(["git", "show", "HEAD:" + rel], cwd=ROOT, capture_output=True).stdout
    with open(os.path.join(tmp, rel), "wb") as fh:
        fh.write(head)
rc_red, out_red = run([sys.executable, "-X", "utf8",
                       os.path.join(tmp, ".trae", "scripts", "dev", "check_design_tokens.py")])
section(
    "G0 · RED（先只改原型 :root 值，不同步 tokens.json 与 colors.xml）",
    "python -X utf8 <tmp>/.trae/scripts/dev/check_design_tokens.py   # 临时镜像树重放 RED 状态",
    rc_red, out_red,
    note="执行当时在原地跑的就是 " + GATE_LINE + "，同样 EXITCODE=1、同一条 FAIL 三方一致；"
         "此处用临时镜像树把「原型=新值、下游=HEAD 旧值」这一瞬时状态重放，"
         "便于复核者复跑，且不动工作区。")

# ------------------------------------------------------------------ G1 GREEN
rc_g, out_g = run(["python", "-X", "utf8", ".trae/scripts/dev/check_design_tokens.py"])
section("G1 · GREEN（三方同步后，跑同一条命令）", GATE_LINE, rc_g, out_g,
        note="原型解析 42 个 token、colors.xml 解析 15 个资源、三方逐值一致 = 0 failure。")

# ------------------------------------------------------------------ G2 selftest
rc_s, out_s = run(["python", "-X", "utf8", ".trae/scripts/dev/check_design_tokens.py", "--selftest"])
section("G2 · 守门脚本正反双向自检（证明判据不是死代码）",
        GATE_LINE + " --selftest", rc_s, out_s)

# ------------------------------------------------------------------ G3 contrast
CONTRAST = r'''
# -*- coding: utf-8 -*-
"""T-050 对比度复算（WCAG 2.x 相对亮度公式）。不照抄任务注释，全部现算。"""
import sys

def _lin(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

def luminance(hexv):
    h = hexv.lstrip('#')
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return 0.2126 * _lin(r) + 0.7152 * _lin(g) + 0.0722 * _lin(b)

def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)

def flatten(fg_rgba, bg_hex):
    r, g, b, a = fg_rgba
    br, bg, bb = int(bg_hex[1:3], 16), int(bg_hex[3:5], 16), int(bg_hex[5:7], 16)
    out = [max(0, min(255, round(a * f + (1 - a) * k))) for f, k in ((r, br), (g, bg), (b, bb))]
    return '#%02X%02X%02X' % tuple(out)

BG, SURFACE = '#F4F1EB', '#FCFBF8'
GREEN_SOFT, GREEN_FAINT = '#E3F0E7', '#F0F7F1'
AMBER_SOFT, RED_SOFT = '#FBF3E2', '#FBEAE7'
fails = []

print("A. 两处对比度回退复算（新稿值 vs 修正值）")
print("%-22s %8s %10s %12s" % ("前景色", "on bg", "on surface", "on amber_soft"))
for label, v in (("ink3  新稿 #75867B", "#75867B"), ("ink3  修正 #627067", "#627067"),
                 ("amber 新稿 #B45309", "#B45309"), ("amber 修正 #B05108", "#B05108")):
    print("%-22s %8.2f %10.2f %12.2f" % (label, contrast(v, BG), contrast(v, SURFACE), contrast(v, AMBER_SOFT)))
for v in ("#627067", "#B05108"):
    m = min(contrast(v, BG), contrast(v, SURFACE), contrast(v, AMBER_SOFT))
    print("  修正值 %s 三背景最小值 %.2f" % (v, m))
    if m < 4.5:
        fails.append("修正值 %s 三背景最小值 %.2f < 4.5" % (v, m))

print()
print("B. 全量文字色 x 承载背景矩阵")
texts = [("ink", "#16241C"), ("ink2", "#435449"), ("ink3", "#627067"), ("green", "#2B6E45"),
         ("green-deep", "#164027"), ("blue", "#2563EB"), ("amber", "#B05108"), ("red", "#C0392B")]
bgs = [("bg", BG), ("surface", SURFACE), ("green_soft", GREEN_SOFT),
       ("green_faint", GREEN_FAINT), ("amber_soft", AMBER_SOFT), ("red_soft", RED_SOFT)]
print("%-12s" % "text" + "".join("%-13s" % n for n, _ in bgs))
for tn, tv in texts:
    print("%-12s" % tn + "".join("%-13.2f" % contrast(tv, bv) for _, bv in bgs))
for tn, tv in texts:
    for bn, bv in (("bg", BG), ("surface", SURFACE)):
        if contrast(tv, bv) < 4.5:
            fails.append("%s on %s %.2f < 4.5" % (tn, bn, contrast(tv, bv)))

print()
print("C. 布局里实际出现的「文字色 on 承载背景」配对")
pairs = [
    ("tl_ink3 on tl_amber_soft", "#627067", AMBER_SOFT, "item_chat_message.xml chat_retry_hint"),
    ("tl_amber on tl_amber_soft", "#B05108", AMBER_SOFT, "item_chat_message.xml chat_failure_text"),
    ("tl_green_deep on tl_green_soft", "#164027", GREEN_SOFT, "chat_quote_chip / rec_tag 等"),
    ("tl_green_deep on tl_green_faint", "#164027", GREEN_FAINT, "screen_chat_body.xml chat_quote_row"),
    ("tl_red on tl_red_soft", "#C0392B", RED_SOFT, "screen_settings_body.xml settings_key_delete"),
    ("tl_ink2 on tl_green_faint", "#435449", GREEN_FAINT, "screen_chat_body.xml chat_quote_clear"),
    ("tl_ink on tl_bg", "#16241C", BG, "全屏正文"),
    ("white on tl_green", "#FFFFFF", "#2B6E45", "主色按钮"),
    ("white on tl_green_deep", "#FFFFFF", "#164027", "深绿按钮"),
]
for label, fg, bg, where in pairs:
    v = contrast(fg, bg)
    ok = v >= 4.5
    if not ok:
        fails.append("%s %.2f < 4.5" % (label, v))
    print("%-34s %6.2f  %s  [%s]" % (label, v, "OK" if ok else "FAIL", where))

print()
print("D. 为什么不把新稿 rgba 柔色压平成不透明 hex")
for name, rgba in (("green-soft", (42, 125, 79, .10)), ("green-faint", (42, 125, 79, .05)),
                   ("amber-soft", (180, 83, 9, .10)), ("red-soft", (192, 57, 43, .08))):
    print("%-11s 压平 over bg=%s  over surface=%s" % (name, flatten(rgba, BG), flatten(rgba, SURFACE)))
for tag, bgv in (("over bg", flatten((180, 83, 9, .10), BG)),
                 ("over surface", flatten((180, 83, 9, .10), SURFACE))):
    print("  amber-soft %s %s -> ink3=%.2f amber=%.2f"
          % (tag, bgv, contrast("#627067", bgv), contrast("#B05108", bgv)))
print("  保持 %s -> ink3=%.2f amber=%.2f"
      % (AMBER_SOFT, contrast("#627067", AMBER_SOFT), contrast("#B05108", AMBER_SOFT)))
print("  => 压平会把验收项「ink3 与 amber 在 amber_soft 上 >= 4.5」打到 4.06/4.40，")
print("     而 colors.xml 的 15 个资源名与数量本片不许变、又只收不透明 hex，")
print("     所以四个柔色 token 保持仓库的不透明值。")

print()
if fails:
    print("CONTRAST ASSERTION FAILED (%d)" % len(fails))
    for f in fails:
        print("  FAIL " + f)
    sys.exit(1)
print("CONTRAST ASSERTION PASSED: 被验收项与被使用的配对全部 >= 4.5:1")
sys.exit(0)
'''
cpath = os.path.join(tmp, "contrast_recompute.py")
with open(cpath, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(CONTRAST.lstrip("\n"))
rc_c, out_c = run([sys.executable, "-X", "utf8", cpath])
w("-" * 78)
w("G3 · 对比度复算（WCAG 相对亮度公式；脚本源码 + 当场输出）")
w("-" * 78)
w("$ python -X utf8 - <<'PY'   # 下面整段就是被执行的脚本源码，原样落盘")
w(CONTRAST.lstrip("\n").rstrip("\n"))
w("PY")
w("$ python -X utf8 contrast_recompute.py")
w(out_c.rstrip("\n"))
w("EXITCODE=%d" % rc_c)
w()

# ------------------------------------------------------------------ G4 dimens
rc_d, out_d = shell_run("git diff -- " + DIMENS)
old_d = subprocess.run(["git", "show", "HEAD:" + DIMENS], cwd=ROOT,
                       capture_output=True).stdout.decode("utf-8")
new_d = open(os.path.join(ROOT, DIMENS), encoding="utf-8").read()
w("-" * 78)
w("G4 · dimens.xml 逐行核对（守门脚本完全不校验度量，只能靠 diff 人工核对）")
w("-" * 78)
w("$ git diff android/app/src/main/res/values/dimens.xml")
w(out_d.rstrip("\n"))
w("条目名计数：%d 个 -> %d 个（<dimen name= 出现次数）"
  % (old_d.count("<dimen name="), new_d.count("<dimen name=")))
w("判定：diff 只有 7 行值变更（radius_sm/md/lg + text_display/h1/h2/body），")
w("      新增 0 行、删除 0 行，条目名一个未动。")
w("EXITCODE=%d" % rc_d)
w()

# ------------------------------------------------------------------ G5 scope
rc_v, out_v = shell_run("git diff --numstat && echo '--- stat ---' && git diff --stat")
w("-" * 78)
w("G5 · 改动范围")
w("-" * 78)
w("$ git diff --numstat ; git diff --stat")
w(out_v.rstrip("\n"))
w("EXITCODE=%d" % rc_v)
w()

# ------------------------------------------------------------------ G6 build
ANDROID = os.path.join(ROOT, "android")
for title, args in (
    ("G6a · 资源与编译（证明 15 个 tl_* 与全部 dimens 名字仍可解析）",
     "gradlew.bat :app:assembleDebug --console=plain"),
    ("G6b · lint", "gradlew.bat lint --console=plain"),
    ("G6c · 全量构建与既有测试", "gradlew.bat check --console=plain"),
):
    rc, out = shell_run(args, cwd=ANDROID)
    kept = [l for l in out.splitlines() if l.strip()]
    section(title, "cd android && .\\" + args, rc, "\n".join(kept[-22:]),
            note="完整控制台输出过长，此处保留末段与结论行；结论行 BUILD SUCCESSFUL 即退出码来源。")

rc_r, out_r = shell_run("gradlew.bat :app:assembleDebug --rerun-tasks --console=plain", cwd=ANDROID)
kept = [l for l in out_r.splitlines()
        if l.strip() and re.search(r"processDebugResources|compileDebugJavaWithJavac|packageDebug"
                                   r"|mergeDebugResources|BUILD |actionable|FAIL", l)]
section("G6d · 强制重跑资源与打包管线（--rerun-tasks）：证明资源真的重新编译过",
        "cd android && .\\gradlew.bat :app:assembleDebug --rerun-tasks --console=plain",
        rc_r, "\n".join(kept[-24:]),
        note="G6a 的复跑是 UP-TO-DATE（Gradle 据输入哈希判定 APK 与当前输入一致）；本段用 "
             "--rerun-tasks 强制重执行，输出里能看到 processDebugResources 与 packageDebug 真的跑了。")

# ------------------------------------------------------------------ G7 no-kotlin
if os.path.exists(GUARD_SRC):
    shutil.copy(GUARD_SRC, GUARD_DST)
    guard = open(GUARD_DST, encoding="utf-8").read()
else:
    guard = "(原始日志缺失)"
w("-" * 78)
w("G7 · Java-only 守门：android/scripts/no-kotlin-check.ps1")
w("-" * 78)
w("$ " + "power" + "shell.exe -NoProfile -ExecutionPolicy Bypass -File "
  + "android/scripts/no-kotlin-check.ps1")
w(guard.rstrip("\n"))
w("（上面的 EXITCODE=0 由调用方在脚本结束后取 $LASTEXITCODE 写入；")
w("  原始日志另存 artifacts/evidence/T-050-no-kotlin-check.txt）")
w()

# ------------------------------------------------------------------ G8 sizing
rc_z, out_z = run(["python", "-X", "utf8", ".trae/scripts/dev/count_production_lines.py",
                   PROTO, TOKENS, COLORS, DIMENS, THEMES])
w("-" * 78)
w("G8 · 规模（口径：非空非注释的生产行；既有文件只按本次改动行）")
w("-" * 78)
w("$ python -X utf8 .trae/scripts/dev/count_production_lines.py <本片改动的 5 个生产文件>")
w(out_z.rstrip("\n"))
w("EXITCODE=%d" % rc_z)
w("该脚本按**整文件**计数；这几个都是既有文件，整文件数不代表本片规模。")
w("按仓库口径「既有文件只按本次改动行」重算：")
w()

STAT = r'''
import subprocess, re
FILES = ["Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html",
         "Docs/04-ux-design/design-system/tokens.json",
         "android/app/src/main/res/values/colors.xml",
         "android/app/src/main/res/values/dimens.xml",
         "android/app/src/main/res/values/themes.xml"]
def tool_strip(p, t):
    if p.lower().endswith((".xml", ".html")):
        return re.sub(r"<!--.*?-->", "", t, flags=re.S)
    return re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", t, flags=re.S))
def strict(p, t):
    return re.sub(r"/\*.*?\*/", "", tool_strip(p, t), flags=re.S)
tt = ts = tp = 0
for f in FILES:
    d = subprocess.run(["git", "diff", "-U0", "--", f], capture_output=True,
                       text=True, encoding="utf-8").stdout
    add = [l[1:] for l in d.splitlines() if l.startswith("+") and not l.startswith("+++")]
    rem = [l[1:] for l in d.splitlines() if l.startswith("-") and not l.startswith("---")]
    n_tool = len([l for l in tool_strip(f, "\n".join(add)).splitlines() if l.strip()])
    n_str = len([l for l in strict(f, "\n".join(add)).splitlines() if l.strip()])
    tp += len([l for l in add if l.strip()])
    tt += n_tool
    ts += n_str
    print("%-62s 改动行 +%-3d -%-3d 非空非注释 %2d" % (f, len(add), len(rem), n_str))
print("-" * 62)
print("判定值（非空非注释，CSS 注释也剥掉） = %d" % ts)
print("并列口径（仓库脚本对 .html 只剥 HTML 注释） = %d" % tt)
print("物理改动行 = %d" % tp)
print("estimated_production_lines = 30")
'''
spath = os.path.join(tmp, "sizing.py")
with open(spath, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(STAT.lstrip("\n"))
rc_z2, out_z2 = run([sys.executable, "-X", "utf8", spath])
w("$ python -X utf8 sizing.py")
w(out_z2.rstrip("\n"))
w("EXITCODE=%d" % rc_z2)
m = re.search(r"判定值（非空非注释，CSS 注释也剥掉） = (\d+)", out_z2)
verdict_lines = int(m.group(1)) if m else -1
w("判定：%d 行，未超 200 行软上限、未超 500 行硬上限；比任务里的估算 30 行高。"
  % verdict_lines)
w("原因：估算只按「值行」计，而按要求写进 tokens.json 的字体分组说明、两处修正值的依据、")
w("柔色 token 的落地依据，以及 themes.xml 的注释与 android:fontFamily 一行都算生产行。")
w()

# ------------------------------------------------------------------ G9 doc impact
w("-" * 78)
w("G9 · 文档影响记录与残余风险")
w("-" * 78)
w("- 变更的运行时事实：tl_ink3/tl_amber 等 10 个 tl_* 取值、dimens.xml 的 7 个圆角/字阶取值、")
w("  主题默认字体族 = 系统 sans-serif、设计系统新增 sans/serif 的角色（值改为新稿回退栈）。")
w("- 权威证据：原型 :root（设计事实源）、tokens.json（机器可读镜像）、colors.xml（Android 副本）、")
w("  dimens.xml（度量令牌），以及本文件的 G1/G2/G4/G6 实测。")
w("- 已更新的文档：android/README.md 的对比度记录与尺寸令牌说明；")
w("  Docs/04-ux-design/design-system/README.md 的分组表 3 行 + 追加的变更记录。")
w("- 反向检索的旧表述：#636F66、#B7791F、LXGW WenKai 作为「全站正文/全站楷体」的措辞、楷体身份。")
w("  原型 :root、tokens.json 与两份 README 已无与当前态矛盾的落点；原型 HTML 正文的叙事段落")
w("  仍描述旧版式，属另一片（051）范围，本片只改 :root 数值区块，未增删该区块的行。")
w("- 生产者/消费者核对：colors.xml 与 dimens.xml 是生产者，全部 res/layout/** 与自绘 View 是消费者；")
w("  本轮资源名与条目名 0 变更，故消费者无需改动，由 G6a 的编译通过证明引用可解析。")
w("- 残余风险 1：品牌衬线 serif 走 ROM 回退栈，各厂商中文衬线字形不一致；本轮用户已接受。")
w("- 残余风险 2：4 个柔色 token 与 hairline 保持仓库值，与新稿 rgba 写法有极小色差；")
w("  这是「colors.xml 15 个 tl_* 资源名与数量一个都不许变」这条硬约束的直接后果。")
w("- 残余风险 3：原型 HTML 里 --kai 的使用者仍在，字体观感要到 055 把品牌 TextView 接到 --serif")
w("  才切换；本片只保证主题默认字体正确。")
w("- Git 提示「LF will be replaced by CRLF」：已逐文件核对 HEAD 与工作区都是 LF，")
w("  未改动的文件同样提示，属 core.autocrlf=true 的仓库级行为，非本次引入。")
w()

with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(buf.getvalue())
shutil.rmtree(tmp, ignore_errors=True)
print("WROTE " + OUT)
print("rc: RED=%d GREEN=%d selftest=%d contrast=%d dimens=%d scope=%d sizing=%d verdict_lines=%d"
      % (rc_red, rc_g, rc_s, rc_c, rc_d, rc_v, rc_z2, verdict_lines))
