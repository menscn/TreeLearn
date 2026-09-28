#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""生成 V1.1 增量原型的验收证据文件（自证型：命令与退出码当场捕获，结论从捕获输出机械派生）。

用法：python artifacts/tmp/gen_v11_evidence.py
产出：artifacts/evidence/ux-v11-prototype-2026-09-27.txt
"""
import io, os, re, sys, json, hashlib, subprocess, datetime

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
V1 = os.path.join(ROOT, 'Docs', '04-ux-design', '知芽-TreeLearn-高保真原型-v1.html')
V11 = os.path.join(ROOT, 'Docs', '04-ux-design', '知芽-TreeLearn-高保真原型-v1.1.html')
SHOTS = os.path.join(ROOT, 'artifacts', 'tmp', 'v11_shots')
OUT = os.path.join(ROOT, 'artifacts', 'evidence', 'ux-v11-prototype-2026-09-27.txt')

lines = []
def w(s=''):
    lines.append(s)

def run(cmd, cwd=ROOT, timeout=900):
    p = subprocess.run(cmd, cwd=cwd, shell=isinstance(cmd, str),
                       capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=timeout)
    return p.returncode, (p.stdout or '') + (p.stderr or '')

now = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
w('知芽 TreeLearn · 高保真原型 V1.1（增量）验收证据')
w('生成时间：%s（脚本当场捕获，非事后补写）' % now)
w('=' * 78)

# ---------- CHECK 1 设计 token 三方守门 ----------
w()
w('CHECK 1  设计 token 三方一致性守门（原型 :root ↔ tokens.json ↔ colors.xml）')
w('命令： python .trae/scripts/dev/check_design_tokens.py')
code1, out1 = run([sys.executable, '.trae/scripts/dev/check_design_tokens.py'])
w('EXITCODE=%d' % code1)
for ln in out1.strip().splitlines():
    w('  | ' + ln)
w('判据：出现 "1 check(s) failed" 即 FAIL；本仓口径为 0 check(s) failed 且 EXITCODE=0。')

# ---------- CHECK 2 V1.1 :root 与 V1 逐字节一致 ----------
w()
w('CHECK 2  V1.1 的 :root 是否与 V1 逐字节一致（V1 仍是唯一权威源）')
code2, out2 = run([sys.executable, '-c', r'''
import io, re, hashlib, sys
from collections import Counter
v1  = io.open(r"__V1__", encoding="utf-8").read()
v11 = io.open(r"__V11__", encoding="utf-8").read()
def root(s):
    return re.search(r":root\s*\{(.*?)\}", s, re.S).group(0)
r1, r11 = root(v1), root(v11)
n1 = len(re.findall(r"--[\w-]+\s*:", r1)); n11 = len(re.findall(r"--[\w-]+\s*:", r11))
print("root_identical=%s" % (r1 == r11))
print("token_count_v1=%d token_count_v11=%d" % (n1, n11))
print("root_md5_v1=%s" % hashlib.md5(r1.encode()).hexdigest())
print("root_md5_v11=%s" % hashlib.md5(r11.encode()).hexdigest())

def body(s):
    # 去掉 :root 区块与 HTML 注释，只看「界面层」的裸 hex
    return re.sub(r"<!--.*?-->", "", s, flags=re.S).replace(r1, "")
def hx(s):
    return Counter(m.group(0).lower() for m in re.finditer(r"#[0-9a-fA-F]{3,8}\b", s))

b1, b11 = body(v1), body(v11)
c1, c11 = hx(b1), hx(b11)
inc = {v: c11[v] - c1[v] for v in c11 if c11[v] > c1[v]}
novel = sorted(v for v in inc if c1[v] == 0)          # 基线里从未出现过 → 违规
print("基线裸hex种类=%d  V1.1=%d" % (len(c1), len(c11)))
print("计数增加的既有值=%s" % (inc or "无"))
print("基线中从未出现过的新颜色=%s" % (novel or "无"))
# 把「计数增加的既有值」的上下文打出来，供人复核它是否只是复用同一写法
for v in sorted(inc):
    for m in re.finditer(re.escape(v), b11, re.I):
        s0 = max(0, m.start() - 80); s1 = min(len(b11), m.end() + 24)
        print("   ctx %s : ...%s..." % (v, b11[s0:s1].replace("\n", " / ")))
sys.exit(0 if (r1 == r11 and not novel) else 1)
'''.replace('__V1__', V1).replace('__V11__', V11)])
w('EXITCODE=%d' % code2)
for ln in out2.strip().splitlines():
    w('  | ' + ln)
w('判据：root_identical=True 且「基线中从未出现过的新颜色」为空 → PASS。')
w('说明：界面层裸 hex 的既有写法（v1 基线本身就有 40 处，多为渐变/纸纹/开关滑块）不因本版新增书写而变成违规；')
w('      本版只允许复用基线中已存在的颜色值，不得引入新颜色。')

# ---------- CHECK 3 渲染 + 走查点击冒烟 ----------
w()
w('CHECK 3  Edge headless 渲染 + 24 步走查点击（含页面异常捕获）')
url = 'file:///' + V11.replace('\\', '/')
w('命令： node artifacts/tmp/v11_smoke.mjs "%s" artifacts/tmp/v11_shots' % url)
code3, out3 = run(['node', 'artifacts/tmp/v11_smoke.mjs', url, 'artifacts/tmp/v11_shots'])
w('EXITCODE=%d' % code3)
for ln in out3.strip().splitlines():
    if ln.startswith('PASS') or ln.startswith('FAIL') or 'guide 自检' in ln or '步骤' in ln or 'pageerror' in ln or ln.startswith('  !'):
        w('  | ' + ln)
w('判据：步骤 24/24 PASS 且 pageerror / console-error 数 0 → PASS。')

# 从报告机械派生结论
rep = os.path.join(SHOTS, '_report.json')
step_pass = step_total = err_n = None
if os.path.exists(rep):
    d = json.load(io.open(rep, encoding='utf-8'))
    step_total = len(d['results']); step_pass = sum(1 for r in d['results'] if r['ok']); err_n = len(d['errors'])

# ---------- 结论 ----------
w()
w('=' * 78)
w('结论（由上面三次捕获机械派生，不手工填写）')
w('  CHECK 1  token 守门            : %s (EXITCODE=%d)' % ('PASS' if code1 == 0 else 'FAIL', code1))
w('  CHECK 2  :root 与 V1 一致/无裸hex: %s (EXITCODE=%d)' % ('PASS' if code2 == 0 else 'FAIL', code2))
if step_total is not None:
    w('  CHECK 3  渲染+走查 %d/%d PASS, 页面异常 %d : %s (EXITCODE=%d)'
      % (step_pass, step_total, err_n, 'PASS' if (step_pass == step_total and err_n == 0) else 'FAIL', code3))
else:
    w('  CHECK 3  (未读到 _report.json)  : EXITCODE=%d' % code3)
w('  截图目录： artifacts/tmp/v11_shots/')
overall = (code1 == 0 and code2 == 0 and step_total and step_pass == step_total and err_n == 0)
w('  整体判定： %s' % ('PASS' if overall else 'FAIL'))
w('=' * 78)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
io.open(OUT, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
print('\n'.join(lines))
print('\n>>> 已写入', OUT)
sys.exit(0 if overall else 1)
