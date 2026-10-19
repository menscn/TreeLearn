"""交付指标报告：一页报告 + 「每个数字是哪条命令读出的」写在同一行（ROADMAP B6 的指标面，片 030）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/metrics_report.py                # 只判，不写（默认；core 档注册这条）
    python -X utf8 .codex/scripts/metrics_report.py --write        # 重生成 artifacts/metrics/report.md
    python -X utf8 .codex/scripts/metrics_report.py --read <指标名>  # 只复算一个指标并打印读数

为什么需要这支脚本：ROADMAP B6 要的「看得见自己是在变快还是变慢」，落成文字就是人手抄进报告的一堆数字，
而手抄数字的失效方向本仓库已经登记过（「任务文件里每个数字都要写成『从哪个文件的哪行得到』，写完再回读一次」）。
所以这里判的形状是：① 每行六个字段齐全、每个指标恰好一行、每个指标只声明一个口径（ROADMAP 执行提示词点名
的去重口径——同一口径不许既算物理行又算非空行）；② 每个数字都带一条**能被本脚本当场执行的命令**，
`复算=值` 的行必须等于当场重跑那条命令的输出（手抄、改一个字符、留一份过期数字都会立刻响）；
③ 四类家族不许由报告自己报数——家族清单从 ROADMAP 的 B6 目标句现场派生，派生集合另有裸下限兜着。

`复算=否` 只留给仓库历史读数（提交数、案例库最近提交日）：自检副本是一份新 init 的 git 历史，
那些数字在副本里必然不同，所以它们只判形状（命令必须是真 git 命令、当场必须能复算出读数），
不判值。这条取舍写进报告抬头，不藏在注释里。

退出码：0 = 通过；1 = 有 FAIL。（注入用例在 .codex/scripts/dev/checkers_selftest.py 的 `metrics:*` 条目里。）
"""

import os
import re
import subprocess
import sys
from collections import Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "dev"))

from harness_check_lib import (  # noqa: E402
    REPO_ROOT, Checks, enable_utf8_stdout, exists, read_text, walk_files)

ROADMAP = ".codex/ROADMAP.md"
LIBRARY = ".codex/references/regression-cases.md"
TASKS_DIR = ".codex/tasks/"
SELFTEST = ".codex/scripts/dev/checkers_selftest.py"
ROSTER = ".codex/scripts/run_all_guards.py"
REPORT_DIR = "artifacts/metrics/"
REPORT = REPORT_DIR + "report.md"
SCRIPT = ".codex/scripts/metrics_report.py"
WRITE_COMMAND = "python -X utf8 {0} --write".format(SCRIPT)

SOURCES = (ROADMAP, LIBRARY, TASKS_DIR, SELFTEST, ROSTER)
FIELDS = ("指标", "家族", "口径", "命令", "读数", "复算")

# 裸下限：家族与条数都不许由被检集合自己报数（ROADMAP D1 的同一族成因）。
# 下限刻意低于实测值，好让「单独删掉一个家族的全部指标」由家族判据接住，而「整片被抽干」由这里接住。
MIN_FAMILIES = 4        # 实测 4（派生自 B6 目标句）
MIN_METRICS = 8         # 实测 12
MIN_RECHECKABLE = 6     # 实测 复算=值 9 行
TOP_DAYS = 3            # 「最近」窗口一律锚在数据里的最新日期，不读系统时钟

FAMILY_RE = re.compile(r"把(.+?)汇总成一页报告")
STATUS_RE = re.compile(r"^status:\s*(\S+)", re.M)
PENDING_RE = re.compile(r"^- Verdict: PENDING", re.M)
ROUND_RE = re.compile(r"无独立 verifier 轮次")
CARD_RE = re.compile(r"T-(\d{4}-\d{2}-\d{2})-")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
TABLE_ROW_RE = re.compile(r"^\| (?![-|])")
SEPARATOR_RE = re.compile(r"^\|[\s:-]+\|")
HEADING_RE = re.compile(r"^#{2,3} ")


def sh(*argv):
    """执行一条真实命令并返回 stdout；命令失败返回空串（空读数由判据接住，不当通过）。"""
    proc = subprocess.run(list(argv), cwd=REPO_ROOT, capture_output=True,
                          text=True, encoding="utf-8", errors="replace")
    return proc.stdout if proc.returncode == 0 else ""


def cards():
    return [p for p in walk_files(TASKS_DIR)
            if os.path.basename(p).startswith("T-") and p.endswith(".md")]


def top_days(dates):
    """按日历日计数并取数据里最新的 TOP_DAYS 天（降序），口径唯一：条数，不数行数。"""
    counts = Counter(dates)
    newest = sorted(counts, reverse=True)[:TOP_DAYS]
    return ";".join("{0}={1}".format(d, counts[d]) for d in newest)


def m_commits_by_day():
    return top_days([l.strip() for l in sh("git", "log", "--date=short", "--pretty=%ad").split()
                     if DATE_RE.match(l.strip())])


def m_total_commits():
    return sh("git", "rev-list", "--count", "HEAD").strip()


def m_cards_by_file_date():
    return top_days([d for p in cards() for d in CARD_RE.findall(os.path.basename(p))])


def m_tasks_status_counts():
    statuses = []
    for path in cards():
        match = STATUS_RE.search(read_text(path))
        if match:
            statuses.append(match.group(1))
    pairs = Counter(statuses).most_common()
    return ";".join("{0}={1}".format(k, n) for k, n in pairs)


def m_pending_verdict_cards():
    return str(sum(1 for p in cards() if PENDING_RE.search(read_text(p))))


def m_no_verifier_round_cards():
    return str(sum(len(ROUND_RE.findall(read_text(p))) for p in cards()))


def m_cases_by_prefix():
    import checkers_selftest
    pairs = Counter(c[0].split(":")[0] for c in checkers_selftest.CASES).most_common()
    return ";".join("{0}={1}".format(k, n) for k, n in pairs)


def m_roster_by_tier():
    import run_all_guards
    counts = Counter(tier for entry in run_all_guards.GUARDS for tier in entry.tiers)
    return ";".join(["entries={0}".format(len(run_all_guards.GUARDS))] +
                    ["{0}={1}".format(k, n) for k, n in sorted(counts.items())])


def m_baseline_checkers():
    import checkers_selftest
    return str(len(checkers_selftest.BASELINES))


def m_library_entries():
    lines = read_text(LIBRARY).splitlines()
    rows = 0
    for i, line in enumerate(lines):
        if not TABLE_ROW_RE.match(line):
            continue
        # 表头行也满足「以 | 开头」，把它当条目就是把口径写错：表头是紧跟分隔行（|---|）的那一行。
        if i + 1 < len(lines) and SEPARATOR_RE.match(lines[i + 1]):
            continue
        rows += 1
    headings = len([l for l in lines if HEADING_RE.match(l)])
    return "rows={0};headings={1};total={2}".format(rows, headings, rows + headings)


def m_library_last_commit():
    return sh("git", "log", "-1", "--date=short", "--pretty=%ad", "--", LIBRARY).strip()


def m_recheckable_share():
    values = sum(1 for m in METRICS if m["recheck"] == "值")
    return "值={0};否={1};total={2}".format(values, len(METRICS) - values, len(METRICS))


def read_command(key):
    return "python -X utf8 {0} --read {1}".format(SCRIPT, key)


# 一份注册表就是报告的权威行集：加指标改这里，删指标也改这里，正文不手写第二份清单。
METRICS = [
    dict(key="commits-by-day", family="交付节奏", recheck="否", read=m_commits_by_day,
         unit="提交数（按 AuthorDate 日历日分组，取数据里最新的 {0} 天，不数行数）".format(TOP_DAYS),
         command="git log --date=short --pretty=%ad"),
    dict(key="total-commits", family="交付节奏", recheck="否", read=m_total_commits,
         unit="提交数（HEAD 可达的全部提交，不数行数）",
         command="git rev-list --count HEAD"),
    dict(key="cards-by-file-date", family="交付节奏", recheck="值", read=m_cards_by_file_date,
         unit="任务卡片数（按文件名里的日期分组，取数据里最新的 {0} 天，不含 _TEMPLATE.md）".format(TOP_DAYS),
         command=None),
    dict(key="tasks-status-counts", family="退回次数", recheck="值", read=m_tasks_status_counts,
         unit="任务卡片数（按 front matter 的 status 字段值分组，不含 _TEMPLATE.md）",
         command=None),
    dict(key="pending-verdict-cards", family="退回次数", recheck="值", read=m_pending_verdict_cards,
         unit="卡片数（Verification report 里 Verdict 以 PENDING 开头的卡片，一卡最多计一次）",
         command=None),
    dict(key="no-verifier-round-cards", family="退回次数", recheck="值", read=m_no_verifier_round_cards,
         unit="措辞出现行数（卡片里写「无独立 verifier 轮次」的行数；它是复核欠账的代用信号，"
              "不是退回次数本身，退回次数当前没有结构化登记面）",
         command=None),
    dict(key="selftest-cases-by-prefix", family="守门命中分布", recheck="值", read=m_cases_by_prefix,
         unit="注入用例数（按 checkers_selftest 的 CASES 用例名前缀分组，取已被退役过滤后的清单）",
         command=None),
    dict(key="guard-roster-by-tier", family="守门命中分布", recheck="值", read=m_roster_by_tier,
         unit="守门注册项数（按 run_all_guards 的 tiers 标签分组，另附注册表总条数）",
         command=None),
    dict(key="baseline-checkers", family="守门命中分布", recheck="值", read=m_baseline_checkers,
         unit="基线守门脚本数（checkers_selftest 的 BASELINES 元组条数）",
         command=None),
    dict(key="library-entries", family="失败案例库增量", recheck="值", read=m_library_entries,
         unit="案例条目数（regression-cases.md 的表格数据行（不含表头与分隔行）+ 二三级标题行，不数正文行）",
         command=None),
    dict(key="library-last-commit", family="失败案例库增量", recheck="否", read=m_library_last_commit,
         unit="最近一次提交日期（该案例库文件在仓库历史里最后一次被改动的 AuthorDate，不数行数）",
         command="git log -1 --date=short --pretty=%ad -- .codex/references/regression-cases.md"),
    dict(key="recheckable-share", family="守门命中分布", recheck="值", read=m_recheckable_share,
         unit="指标行数（按本报告 复算= 字段的取值分组，不数提交历史）",
         command=None),
]


def metric_command(metric):
    return metric["command"] or read_command(metric["key"])


def families():
    """四类家族从 ROADMAP 的 B6 目标句现场派生：那里删掉一项，报告与判据立刻对不上。"""
    match = FAMILY_RE.search(read_text(ROADMAP))
    if not match:
        return []
    return [f.strip() for f in match.group(1).split("、") if f.strip()]


def line_for(metric):
    value = metric["read"]()
    return "- 指标={0} | 家族={1} | 口径={2} | 命令={3} | 读数={4} | 复算={5}".format(
        metric["key"], metric["family"], metric["unit"], metric_command(metric),
        value or "读数取不到", metric["recheck"])


def build():
    wanted = families()
    lines = [
        "# 交付指标报告",
        "",
        "生成命令={0}".format(WRITE_COMMAND),
        "家族来源=`.codex/ROADMAP.md` 的 B6 目标句（`把…汇总成一页报告`），当前派生结果={0}".format(
            "、".join(wanted)),
        "单指标复算=python -X utf8 {0} --read <指标名>".format(SCRIPT),
        "窗口口径=所有「最近 N」窗口以数据里的最新日期为右端，不读系统时钟，"
        "故同一 HEAD 下重跑本文件字节相同",
        "复算=否 的含义=仓库历史读数（提交数、案例库最近提交日）。自检副本是一份新 init 的 git 历史，"
        "那些数字在副本里必然不同，所以它们只判形状（命令必须是真 git 命令、当场必须能复算出读数），不判值",
        "",
    ]
    lines += [line_for(m) for m in METRICS]
    lines.append("")
    return "\n".join(lines)


def parse(text):
    """把报告里的指标行还原成字段字典；字段不齐或口径重复的行由判据分别接住。"""
    rows = []
    for raw in text.splitlines():
        if not raw.startswith("- 指标="):
            continue
        cells = raw[2:].split(" | ")
        row = {}
        repeats = []
        for cell in cells:
            if "=" not in cell:
                continue
            key, value = cell.split("=", 1)
            if key in row:
                repeats.append(key)
            row[key] = value
        rows.append((raw, row, sorted(set(repeats))))
    return rows


def fresh_reading(metric):
    try:
        return metric["read"]() or ""
    except Exception as exc:  # noqa: BLE001 — 读数取不到就是 FAIL，不是 traceback 收尾
        return ""


def run_printed_command(command):
    """真的执行报告行上写的那条 `--read` 命令，返回它的首行输出；命令形状不认识就返回 None。"""
    parts = command.split()
    if len(parts) != 6 or parts[:5] != ["python", "-X", "utf8", SCRIPT, "--read"]:
        return None
    out = sh(sys.executable, "-X", "utf8", parts[3], parts[4], parts[5])
    lines = out.splitlines()
    return lines[0] if lines else ""


def check():
    c = Checks("check_metrics_report")
    missing = [p for p in SOURCES if not exists(p)]
    if not c.check(not missing, "every metric source is present",
                   "missing={0}".format(missing)):
        return c.finish()
    wanted = families()
    if not c.check(len(wanted) >= MIN_FAMILIES,
                   "metric families derive from the ROADMAP B6 goal sentence",
                   "derived={0} floor={1} families={2}".format(
                       len(wanted), MIN_FAMILIES, wanted)):
        return c.finish()
    if not c.check(len(METRICS) >= MIN_METRICS,
                   "registered metric list is not drained below its floor",
                   "registered={0} floor={1}".format(len(METRICS), MIN_METRICS)):
        return c.finish()
    if not c.check(exists(REPORT_DIR), "the report landing directory exists",
                   "dir={0}".format(REPORT_DIR)):
        return c.finish()
    if not c.check(exists(REPORT), "metrics report file exists",
                   "path={0}".format(REPORT)):
        return c.finish()

    text = read_text(REPORT)
    rows = parse(text)
    keys = [row.get("指标") for _raw, row, _dup in rows]
    registered = [m["key"] for m in METRICS]
    extra = sorted(set(keys) - set(registered))
    gone = sorted(set(registered) - set(keys))
    c.check(len(keys) == len(set(keys)) and not extra and not gone,
            "the report holds exactly one line per registered metric",
            "lines={0} duplicated={1} hand_added={2} missing_from_report={3}".format(
                len(keys), sorted({k for k in keys if keys.count(k) > 1}), extra, gone))
    c.check(all(set(row) == set(FIELDS) for _r, row, _d in rows),
            "every metric line carries exactly the six required fields",
            "bad={0}".format([row.get("指标") for _r, row, _d in rows
                              if set(row) != set(FIELDS)][:3]))
    c.check(all(not dup for _r, _row, dup in rows),
            "every metric line declares one unit, never two",
            "duplicated_fields={0}".format([[row.get("指标"), dup] for _r, row, dup in rows
                                            if dup][:3]))

    present = {row.get("家族") for _r, row, _d in rows}
    c.check(not [f for f in wanted if f not in present],
            "every family required by the roadmap appears in the report",
            "required={0} missing={1}".format(
                len(wanted), [f for f in wanted if f not in present]))

    blank = [row.get("指标") for _r, row, _d in rows if not row.get("命令", "").strip()]
    c.check(not blank, "every metric line names a command",
            "blank={0}".format(blank[:3]))
    value_rows = [(row.get("指标"), row) for _r, row, _d in rows if row.get("复算") == "值"]
    c.check(len(value_rows) >= MIN_RECHECKABLE,
            "enough metric lines are declared machine-recheckable",
            "measured={0} floor={1}".format(len(value_rows), MIN_RECHECKABLE))

    registry = {m["key"]: m for m in METRICS}
    disagree = [(row.get("指标"), row.get("复算")) for _r, row, _d in rows
                if row.get("指标") in registry and row.get("复算") != registry[row["指标"]]["recheck"]]
    c.check(not disagree, "each line declares the recheck class its registry entry really has",
            "disagree={0}".format(disagree[:3]))

    history = [(row.get("指标"), str(row.get("命令") or "")) for _r, row, _d in rows
               if row.get("复算") == "否"]
    not_git = [k for k, cmd in history if not cmd.startswith("git ")]
    c.check(not not_git, "history readings are read from a real git command",
            "not_git={0}".format(not_git))

    unreadable = []
    wrong_command = []
    mismatch = []
    by_key = {row.get("指标"): row for _raw, row, _d in rows}
    for metric in METRICS:
        reading = fresh_reading(metric)
        if not reading:
            unreadable.append(metric["key"])
        row = by_key.get(metric["key"])
        if row is None:
            continue
        if row.get("命令") != metric_command(metric):
            wrong_command.append([metric["key"], row.get("命令")])
        if row.get("复算") == "值":
            # 这里**真的执行**行上写的那条命令，而不是在进程内调同一个函数：
            # 「数字是这条命令读出来的」只有把命令跑一遍才算证过，否则改一个字符的行照样能对上函数。
            produced = run_printed_command(row.get("命令") or "")
            if produced is None:
                mismatch.append([metric["key"], "命令形状不认识", (row.get("命令") or "")[:60]])
            elif produced != row["读数"]:
                mismatch.append([metric["key"], row["读数"], produced])
    c.check(not wrong_command, "every line prints the command the generator would print",
            "wrong={0}".format(wrong_command[:3]))
    c.check(not unreadable, "every metric produces a reading when re-run",
            "unreadable={0}".format(unreadable))
    c.check(not mismatch,
            "each machine-recheckable reading is produced by running the command on its own line",
            "checked={0} mismatch={1}".format(len(value_rows), mismatch[:3]))

    head = "\n".join(text.splitlines()[:8])
    c.check(WRITE_COMMAND in head and "生成命令=" in head,
            "report header names the command that writes it",
            "needle={0} present={1}".format(WRITE_COMMAND, WRITE_COMMAND in head))

    from harness_check_lib import placeholder_hits
    # 占位符只扫「抬头 + 读数」：口径正文里出现 `PENDING` 是在**描述被数的那个东西**（Verdict 以 PENDING
    # 开头的卡片），不是没填的空位。扫全文会把这种正当写法判成占位符，逼人为措辞绕道——那属于把判据
    # 调松，本片不做。
    scanned = "\n".join([row.get("读数", "") for _r, row, _d in rows] + text.splitlines()[:8])
    hits = sorted(set(placeholder_hits(scanned)))
    c.check(not hits, "no metric reading is left as a placeholder",
            "hits={0} scanned=readings+header".format(hits))
    return c.finish()


def main(argv):
    enable_utf8_stdout()
    if argv and argv[0] == "--write":
        text = build()
        os.makedirs(os.path.dirname(os.path.join(REPO_ROOT, REPORT_DIR)), exist_ok=True)
        with open(os.path.join(REPO_ROOT, REPORT.replace("/", os.sep)), "w",
                  encoding="utf-8", newline="\n") as handle:
            handle.write(text)
        print("WROTE {0} lines={1} metrics={2}".format(
            REPORT, len(text.splitlines()), len(METRICS)))
        print("EXITCODE=0")
        return 0
    if argv and argv[0] == "--read":
        key = argv[1] if len(argv) > 1 else ""
        metric = next((m for m in METRICS if m["key"] == key), None)
        if metric is None:
            print("UNKNOWN metric={0} registered={1}".format(key, [m["key"] for m in METRICS]))
            print("EXITCODE=1")
            return 1
        print(fresh_reading(metric))
        print("EXITCODE=0")
        return 0
    return check()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
