# -*- coding: utf-8 -*-
"""Write a self-proving evidence file from a captured gradle log.

usage: python artifacts/tmp/gen_evidence.py <out> <log> <token|-> <dir> [<dir>...]

The gradle run itself is performed by the calling shell (real Git Bash); this script only
re-reads the captured log and the per-report XML counts. EXITCODE comes from the environment,
set by the shell from $? of that same run. CMD (the exact command line) also comes from the
environment so the file records what was actually run rather than what was intended.
"""
import glob
import io
import os
import re
import sys
import time

out_path = sys.argv[1]
log_path = sys.argv[2]
token = sys.argv[3]
dirs = sys.argv[4:]

exitcode = int(os.environ.get('EXITCODE', '-999'))
# The command line is rebuilt here rather than taken from the shell: Git Bash normalises
# backslashes in a shell variable to forward slashes, which would misreport what was run.
BS = chr(92)
gradle_args = os.environ.get('GRADLE_ARGS')
if gradle_args:
    cmd = ('JAVA_HOME=D:' + BS + 'Environment' + BS + 'Jdk' + BS + 'jdk21 ./gradlew '
           + gradle_args)
else:
    cmd = os.environ.get('CMD', '(not captured)')

out = io.open(log_path, encoding='utf-8', errors='replace').read() if os.path.exists(log_path) else ''

verdict = 'UNKNOWN'
for line in out.splitlines():
    if 'BUILD SUCCESSFUL' in line or 'BUILD FAILED' in line:
        verdict = line.strip()

pat = re.compile(r'tests="(\d+)"\s+skipped="(\d+)"\s+failures="(\d+)"\s+errors="(\d+)"')
attr_pat = re.compile(r'\b(tests|skipped|failures|errors)="(\d+)"')


def counts(head):
    """Per-report counts, order-independent: surefire writes tests/skipped/failures/errors while
    the device runner (UTP) writes tests/failures/errors/skipped. The attributes are read off the
    <testsuite> start tag, which is near the top of the file but after the <?xml ?> declaration."""
    start = head.find('<testsuite')
    if start < 0:
        return None
    end = head.find('>', start)
    got = dict(attr_pat.findall(head[start:end + 1]))
    if len(got) < 4:
        return None
    return (int(got['tests']), int(got['skipped']), int(got['failures']), int(got['errors']))


# A report only counts if it was (re)written by THIS run: the connected-test folder is not
# wiped by gradle, so a previous run's TEST-*.xml would otherwise be read as this run's result.
run_start = float(os.environ.get('RUN_START', '0'))
reports = []
stale = []
for d in dirs:
    for p in sorted(glob.glob(os.path.join(d, 'TEST-*.xml'))):
        if run_start and os.path.getmtime(p) < run_start - 1:
            stale.append(p)
        else:
            reports.append(p)
tests = skipped = failures = errors = 0
per_file = []
for p in reports:
    head = io.open(p, encoding='utf-8', errors='replace').read(1600)
    c = counts(head)
    if c:
        tests += c[0]; skipped += c[1]
        failures += c[2]; errors += c[3]
        if token != '-' and token in os.path.basename(p):
            per_file.append('  %s -> tests="%d" skipped="%d" failures="%d" errors="%d"'
                            % ((os.path.basename(p),) + c))

lines = [
    '# %s' % os.environ.get('TITLE', os.path.basename(out_path)),
    '# generated: %s  (写盘时刻＝本文件 mtime；由脚本当场跑命令并当场写盘)' % time.strftime(
        '%Y-%m-%d %H:%M:%S %z'),
    '# workdir: android/',
    '# command: %s' % cmd,
    'EXITCODE=%d' % exitcode,
    'MEASURED build verdict: %s' % verdict,
    '',
    '---- 报告核对（从构建产物 XML 逐份读，不读 BUILD 字样）----',
    'report files produced by THIS run: %d' % len(reports),
    '  -> tests="%d" skipped="%d" failures="%d" errors="%d"' % (tests, skipped, failures, errors),
]
if stale:
    lines.append('stale reports ignored (older than this run): %d' % len(stale))
    for p in stale:
        lines.append('  %s  (mtime %s)' % (
            os.path.basename(p), time.strftime('%H:%M:%S', time.localtime(os.path.getmtime(p)))))
if per_file:
    lines.append('')
    lines.append('---- 目标报告 ----')
    lines.extend(per_file)

io.open(out_path, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
print('EXITCODE=%d verdict=%s reports=%d tests=%d fail=%d err=%d'
      % (exitcode, verdict, len(reports), tests, failures, errors))
