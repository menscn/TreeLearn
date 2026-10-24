# 交付指标报告

生成命令=python -X utf8 .codex/scripts/metrics_report.py --write
家族来源=`.codex/ROADMAP.md` 的 B6 目标句（`把…汇总成一页报告`），当前派生结果=交付节奏、退回次数、守门命中分布、失败案例库增量
单指标复算=python -X utf8 .codex/scripts/metrics_report.py --read <指标名>
窗口口径=所有「最近 N」窗口以数据里的最新日期为右端，不读系统时钟，故同一 HEAD 下重跑本文件字节相同
复算=否 的含义=仓库历史读数（提交数、案例库最近提交日）。自检副本是一份新 init 的 git 历史，那些数字在副本里必然不同，所以它们只判形状（命令必须是真 git 命令、当场必须能复算出读数），不判值

- 指标=commits-by-day | 家族=交付节奏 | 口径=提交数（按 AuthorDate 日历日分组，取数据里最新的 3 天，不数行数） | 命令=git log --date=short --pretty=%ad | 读数=2026-10-23=1;2026-10-21=1;2026-10-20=6 | 复算=否
- 指标=total-commits | 家族=交付节奏 | 口径=提交数（HEAD 可达的全部提交，不数行数） | 命令=git rev-list --count HEAD | 读数=91 | 复算=否
- 指标=cards-by-file-date | 家族=交付节奏 | 口径=任务卡片数（按文件名里的日期分组，取数据里最新的 3 天，不含 _TEMPLATE.md） | 命令=python -X utf8 .codex/scripts/metrics_report.py --read cards-by-file-date | 读数=2026-10-23=14;2026-10-22=12;2026-10-21=14 | 复算=值
- 指标=tasks-status-counts | 家族=退回次数 | 口径=任务卡片数（按 front matter 的 status 字段值分组，不含 _TEMPLATE.md） | 命令=python -X utf8 .codex/scripts/metrics_report.py --read tasks-status-counts | 读数=verified=69;in_progress=3 | 复算=值
- 指标=pending-verdict-cards | 家族=退回次数 | 口径=卡片数（Verification report 里 Verdict 以 PENDING 开头的卡片，一卡最多计一次） | 命令=python -X utf8 .codex/scripts/metrics_report.py --read pending-verdict-cards | 读数=2 | 复算=值
- 指标=no-verifier-round-cards | 家族=退回次数 | 口径=措辞出现行数（卡片里写「无独立 verifier 轮次」的行数；它是复核欠账的代用信号，不是退回次数本身，退回次数当前没有结构化登记面） | 命令=python -X utf8 .codex/scripts/metrics_report.py --read no-verifier-round-cards | 读数=9 | 复算=值
- 指标=selftest-cases-by-prefix | 家族=守门命中分布 | 口径=注入用例数（按 checkers_selftest 的 CASES 用例名前缀分组，取已被退役过滤后的清单） | 命令=python -X utf8 .codex/scripts/metrics_report.py --read selftest-cases-by-prefix | 读数=reporter=35;watch=35;keeper=29;intent=26;simplify=24;sot=24;research=19;metrics=19;roadmap=18;review=14;evals=14;spec=4;context=3;replay=2;template=2 | 复算=值
- 指标=guard-roster-by-tier | 家族=守门命中分布 | 口径=守门注册项数（按 run_all_guards 的 tiers 标签分组，另附注册表总条数） | 命令=python -X utf8 .codex/scripts/metrics_report.py --read guard-roster-by-tier | 读数=entries=45;core=39;precommit=6 | 复算=值
- 指标=baseline-checkers | 家族=守门命中分布 | 口径=基线守门脚本数（checkers_selftest 的 BASELINES 元组条数） | 命令=python -X utf8 .codex/scripts/metrics_report.py --read baseline-checkers | 读数=14 | 复算=值
- 指标=library-entries | 家族=失败案例库增量 | 口径=案例条目数（regression-cases.md 的表格数据行（不含表头与分隔行）+ 二三级标题行，不数正文行） | 命令=python -X utf8 .codex/scripts/metrics_report.py --read library-entries | 读数=rows=13;headings=6;total=19 | 复算=值
- 指标=library-last-commit | 家族=失败案例库增量 | 口径=最近一次提交日期（该案例库文件在仓库历史里最后一次被改动的 AuthorDate，不数行数） | 命令=git log -1 --date=short --pretty=%ad -- .codex/references/regression-cases.md | 读数=2026-10-18 | 复算=否
- 指标=recheckable-share | 家族=守门命中分布 | 口径=指标行数（按本报告 复算= 字段的取值分组，不数提交历史） | 命令=python -X utf8 .codex/scripts/metrics_report.py --read recheckable-share | 读数=值=9;否=3;total=12 | 复算=值
