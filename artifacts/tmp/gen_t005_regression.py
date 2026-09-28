# -*- coding: utf-8 -*-
"""Generate the T-005 regression evidence file.

Runs the full :app:testDebugUnitTest (--rerun) in android/, captures exit code and
the per-report XML counts, and writes them straight into the evidence file. The
"generated" header is written by this script at the moment of the run, so it always
matches the file mtime.
"""
import glob
import io
import os
import re
import subprocess
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ANDROID = os.path.join(ROOT, 'android')
EVID = os.path.join(ROOT, 'artifacts', 'evidence', 'T-2026-09-27-005-regression.txt')
RESULTS = os.path.join(ANDROID, 'app', 'build', 'test-results', 'testDebugUnitTest')

BS = chr(92)
CMD = 'JAVA_HOME=D:' + BS + 'Environment' + BS + 'Jdk' + BS + 'jdk21 ./gradlew --console=plain :app:testDebugUnitTest --rerun'

env = dict(os.environ)
env['JAVA_HOME'] = 'D:' + BS + 'Environment' + BS + 'Jdk' + BS + 'jdk21'

proc = subprocess.run(['bash', '-lc', CMD], cwd=ANDROID, env=env,
                      stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
out = proc.stdout.decode('utf-8', 'replace')
exitcode = proc.returncode

verdict = 'UNKNOWN'
for line in out.splitlines():
    if 'BUILD SUCCESSFUL' in line or 'BUILD FAILED' in line:
        verdict = line.strip()

reports = sorted(glob.glob(os.path.join(RESULTS, 'TEST-*.xml')))
tests = skipped = failures = errors = 0
for p in reports:
    head = io.open(p, encoding='utf-8').read(800)
    m = re.search(r'tests="(\d+)"\s+skipped="(\d+)"\s+failures="(\d+)"\s+errors="(\d+)"', head)
    if m:
        tests += int(m.group(1)); skipped += int(m.group(2))
        failures += int(m.group(3)); errors += int(m.group(4))

stamp = time.strftime('%Y-%m-%d %H:%M:%S %z')
lines = []
lines.append('# T-2026-09-27-005 回归证据 — 首页推荐区视觉与标签映射')
lines.append('# generated: %s  (由本脚本当场跑命令并当场写盘，非手抄)' % stamp)
lines.append('# workdir: android/')
lines.append('# command: %s' % CMD)
lines.append('EXITCODE=%d' % exitcode)
lines.append('MEASURED build verdict: %s' % verdict)
lines.append('')
lines.append('---- 全量单测报告核对（从构建产物 XML 逐份读，不读 BUILD 字样）----')
lines.append('report files: %d' % len(reports))
lines.append('  -> tests="%d" skipped="%d" failures="%d" errors="%d"' % (tests, skipped, failures, errors))
lines.append('')
lines.append('---- 本任务新增报告 ----')
for p in reports:
    if 'RecommendationTagTest' in p:
        head = io.open(p, encoding='utf-8').read(800)
        m = re.search(r'tests="(\d+)"\s+skipped="(\d+)"\s+failures="(\d+)"\s+errors="(\d+)"', head)
        lines.append('  %s -> %s' % (os.path.basename(p), m.group(0) if m else 'n/a'))

io.open(EVID, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
print('EXITCODE=%d verdict=%s reports=%d tests=%d fail=%d err=%d'
      % (exitcode, verdict, len(reports), tests, failures, errors))
print('wrote %s' % EVID)
