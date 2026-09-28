# -*- coding: utf-8 -*-
"""Write the T-005 regression evidence file from a captured gradle log.

The gradle run itself is performed by the calling shell (real Git Bash); this script
only re-reads the captured log and the per-report XML counts, then writes the evidence
file. EXITCODE is taken from the environment, set by the shell from $? of that same run.
"""
import glob
import io
import os
import re
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
LOG = os.path.join(ROOT, 'artifacts', 'tmp', 't005-regression.log')
EVID = os.path.join(ROOT, 'artifacts', 'evidence', 'T-2026-09-27-005-regression.txt')
RESULTS = os.path.join(ROOT, 'android', 'app', 'build', 'test-results', 'testDebugUnitTest')

BS = chr(92)
CMD = ('JAVA_HOME=D:' + BS + 'Environment' + BS + 'Jdk' + BS + 'jdk21 '
       './gradlew --console=plain :app:testDebugUnitTest --rerun')

exitcode = int(os.environ.get('EXITCODE', '-999'))
out = io.open(LOG, encoding='utf-8', errors='replace').read()

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
lines = [
    '# T-2026-09-27-005 回归证据 — 首页推荐区视觉与标签映射',
    '# generated: %s  (由脚本当场写盘，非手抄)' % stamp,
    '# workdir: android/',
    '# command: %s' % CMD,
    'EXITCODE=%d' % exitcode,
    'MEASURED build verdict: %s' % verdict,
    '',
    '---- 全量单测报告核对（从构建产物 XML 逐份读，不读 BUILD 字样）----',
    'report files: %d' % len(reports),
    '  -> tests="%d" skipped="%d" failures="%d" errors="%d"' % (tests, skipped, failures, errors),
    '',
    '---- 本任务新增报告 ----',
]
for p in reports:
    if 'RecommendationTagTest' in p:
        head = io.open(p, encoding='utf-8').read(800)
        m = re.search(r'tests="\d+"\s+skipped="\d+"\s+failures="\d+"\s+errors="\d+"', head)
        lines.append('  %s -> %s' % (os.path.basename(p), m.group(0) if m else 'n/a'))

io.open(EVID, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
print('EXITCODE=%d verdict=%s reports=%d tests=%d fail=%d err=%d'
      % (exitcode, verdict, len(reports), tests, failures, errors))
