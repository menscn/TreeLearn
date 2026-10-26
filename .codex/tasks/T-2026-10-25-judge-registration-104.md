# Filename: T-2026-10-25-judge-registration-104.md
# Codex Harness Task: 把两支 UI 判据注册进守门表，并让整跑重新给得出判决

id: T-2026-10-25-104
title: UI 两支判据入库并整跑转绿（收官）
template_version: 2
task_type: config
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: harness
owner_lane: harness
stack: harness
estimated_production_lines: 20
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 086 线的 088／089 两处判据先建后不注册，理由写在 086 卡面：一条当前判红的守门直接进 core 档，会让本线每一片整跑失去信号。现在修复面都已落地、两支判据在仓库面转绿，注册与整跑是收口动作。
- Problem/background: `check_ui_asset_parity.py` 与 `check_ui_readability.mjs` 此前只能靠人记得手动跑；`run_all_guards.py --list` 里看不到它们，任何一次「整跑绿」都不覆盖这两面。第二件事：Android 资源面新增了 `tree_empty_action`、`view_answer_actions` 的几何与若干 dimens／strings，屏幕清单与 parity 账本还是旧树，`parity_ledger_selftest` 判「清单新鲜度：STALE」。
- Users/scenarios: 下一位改字体栈、样式表、资源或布局的人；以及任何一次要拿「整跑读数」当结论的会话。
- Expected outcome: 两支判据（含各自 `--selftest`）出现在 `--tier precommit` 名单里并判绿；屏幕清单与账本按当前树重生、`parity_ledger_selftest` 20/20 绿；core 与 precommit 两档整跑读数落盘，剩余红只有提交形状带来的 `line-budget`。
- Core flow and reversibility: 四条注册项追加在 `check_offline_bundle --selftest` 之后；回退＝删掉这四条并重跑 `check_run_all_guards.py`。清单重生用 `gen_screen_inventory.py`，账本用 `gen_parity_ledger.py`，两者都是「从当前树重推」，回退＝`git checkout` 两个生成物。
- Inputs/outputs: 输入是 `.codex/scripts/run_all_guards.py`、`frontend/dist-offline/index.html`、Android 资源树；输出是注册表四条、`Docs/04-ux-design/parity/screen-inventory.json`、`artifacts/parity/ledger.md`、两档整跑转录。
- State and exception handling: 判据行按各自真实输出选——两支判据打 `check(s) failed`，两个 selftest 打 `selftest judgement cases executed =`；后者与 `SELFTEST cases=` 不是同一个字符串，写错会让一条绿的守门被判「没打印判据」。
- Constraints and trade-offs: 不新增依赖；不进 core 档（两支都要构建产物或浏览器）；不改判据本身的判定；不为了让整跑变绿而放宽 `line-budget`。
- AI recommendation and rationale: 放 precommit 是读数形状决定的，不是难度决定——core 的契约是「确定性、无构建产物、无浏览器」，把需要浏览器的判据塞进去会让 core 的失败不再指向代码。
- User choice and custom additions: 用户「使用 harness 进行修改，修改完成后提交代码 push」；范围 22 条全做。
- AI-autonomous decisions: ① 两支判据各注册「本体 + selftest」，共 4 条；② 档位 `precommit`；③ 清单与账本按当前树重生而不是手改 JSON；④ 整跑里的 `line-budget` 红按「混合工作树的提交形状」原样记录，不改阈值。
- User-confirmation decisions: 不因 `line-budget` 判红而临时放宽预算或拆分提交。
- Acceptance evidence: `--tier precommit` 选中 10/49，前 6 条 PASS（两条判据本体 + 两条 selftest 全在其中）；`check_run_all_guards.py` `SELFTEST cases=33 executed=33 floor=26 broken=0`；`parity_ledger_selftest` 20/20 PASS；core 与 precommit 两档整跑转录落盘。
- Facts: `check_ui_asset_parity` → `0 check(s) failed`、selftest `17`；`check_ui_readability` → `0 check(s) failed`（15.8s）、selftest `11`；重生后清单 `screens=16 surfaces=17 layout_files=51`、账本 `rows=33`；`metrics_report.py --write` 已重生成。
- Assumptions: precommit 档默认会在提交前跑一次，够用来接住「字体栈少一个族名」「样式表又留一张死表」这类回归；不需要进每一次快速自检。
- Unknowns: 无会改变结论的未知项。
- Risks: precommit 档从此多约 16 秒（两支本体 + 两个自检）；`line-budget` 在提交前后读数不同，属预期而不是回归。
- Deferred: 把 `line-budget` 的「已提交」形状也纳入同一张表，不在本片。
- Non-goals: 不改两支判据的判定逻辑；不改 core 档名单；不改提交策略。
- Skipped non-critical questions and reason: 未问「要不要顺势把两支判据也放进 CI」——仓库当前没有 CI 面，写了也没有消费者。

## Goal

让「字体／类名存活」和「渲染可读性」两件事进入默认守门面，并让整跑在混合工作树上给出可归因的读数，而不是被一份过期清单或过期 metrics 拖成 INCONCLUSIVE。

## Scope

### In scope
- `.codex/scripts/run_all_guards.py` 追加 4 条 precommit 注册项。
- 重生 `Docs/04-ux-design/parity/screen-inventory.json` 与 `artifacts/parity/ledger.md`。
- 重生成 `artifacts/metrics/report.md`。
- 两档整跑与 Android no-kotlin 守门的读数落盘。

### Out of scope
- 两支判据的实现与阈值（088／089 已定）。
- core 档名单、`line-budget` 阈值。
- 任何界面改动。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增 4 条注册项与两份重生数据；必须保留 `check_run_all_guards.py` 的自检天花板（注册表增删后 selftest 仍 33/33）、core 档 39 条的判定面，以及 `check_offline_bundle` 原有的 CDN 判据。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `run_all_guards.py` 的 `GUARDS` 表；消费者 `check_run_all_guards.py`（读表做自检）、`guard-runner` skill 的档位说明、`metrics_report.py` 的 `guard-roster-by-tier` 读数；数据生产者 `gen_screen_inventory.py` → `screen-inventory.json` → `gen_parity_ledger.py` → `artifacts/parity/ledger.md`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 守门表从 45 条增至 49 条，precommit 档从 6 条增至 10 条；屏幕清单 `surfaces=17`（新增空态出口动作后重推）、账本 `rows=33`。
- Authoritative code/config/migration evidence: `.codex/scripts/run_all_guards.py`（4 条新注册项与 tier 注释）、`Docs/04-ux-design/parity/screen-inventory.json`、`artifacts/parity/ledger.md`、`artifacts/metrics/report.md`。
- Documentation surfaces checked: `.codex/skills/guard-runner/SKILL.md`（档位说明只写「名单与条数看 `--list`」，不写死条数）、`Docs/04-ux-design/design-system/README.md`、`.codex/rules/ux-design.md`。
- Documentation files updated: `Docs/04-ux-design/design-system/README.md` 的 2026-10-27 变更记录写明「两支判据已入库、按 precommit 档跑」。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "check_ui_asset_parity\|check_ui_readability" .codex/` 只命中注册表、两个脚本与 086 线卡片；`metrics_report.py --read guard-roster-by-tier` 读数与注册表一致。
- Superseded phrases/examples reverse-searched: 086 卡面那句「先不入 `run_all_guards.py` 的注册表」是**当时的实施顺序登记**，本片落地后由 104 卡与本条记录接住，未回改 086 的历史表述。
- `documentation impact: none` reason and evidence: 不适用。
- Historical/PRD handling: 不改写 088／089 的验收读数。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/run_all_guards.py --list` 里两支判据各两条，档位为 `precommit`。
- [x] `check_ui_asset_parity --selftest` → `selftest judgement cases executed = 17`；`check_ui_readability --selftest` → `11`。
- [x] `check_run_all_guards.py` → `SELFTEST cases=33 executed=33 floor=26 broken=0`，`OVERALL: PASS`。
- [x] `gen_screen_inventory.py` 重推 `surfaces=17`，`gen_parity_ledger.py` 无 STALE，`parity_ledger_selftest` → `cases executed=20 expected=20 failures=0`。
- [x] `metrics_report.py --write` 重生成后 `check_metrics_report` 转绿（`cards-by-file-date`、`tasks-status-counts` 等读数与现场一致）。
- [x] 两档整跑落盘：`precommit-run.txt`（10/49，9 PASS + `line-budget` 红）、`core-run.txt`（见 Verification report）。

## TDD execution record

### RED — failing test first
- Test/scenario: 本片是注册与数据重生，没有业务行为；等价的红是「仪器判不出判决」——注册前 `--tier precommit` 只有 6 条，两支判据不在名单；重生前 `parity_ledger_selftest` 判 `INVENTORY STALE`；`check_metrics_report` 判 `cards-by-file-date` 读数不符。
- Command: `python -X utf8 .codex/scripts/dev/parity_ledger_selftest.py`
- Exit code: 1
- Observed failure: `CASE pos-real-tree rc=1 want=0 FAIL`、`INVENTORY STALE: the tree re-derives to a different measurement face than the filed inventory`、`cases executed=20 expected=20 failures=3`（`104-red-parity.txt`）。
- Failure reason validated: 先确认红不是仪器坏了——同一条命令在重生清单后立刻 `failures=0`，且中间只动了两份生成物；`check_metrics_report` 的红点逐字给出两列读数，`2026-10-25=10` vs `2026-10-25=24`，是卡片增加造成的过期而不是算法错。

### GREEN — minimum implementation
- Implementation: `run_all_guards.py` 追加 4 条 precommit 注册项；`gen_screen_inventory.py` 与 `gen_parity_ledger.py` 按当前树重生；`metrics_report.py --write` 重生成。
- Command: `python -X utf8 .codex/scripts/dev/parity_ledger_selftest.py`
- Exit code: 0
- Passing evidence: `cases executed=20 expected=20 failures=0`、`VERDICT: PASS`。
- Regression evidence: `check_run_all_guards.py` 33/33；`check_metrics_report` 0 failed；`check_ui_asset_parity` 0 failed（selftest 17）、`check_ui_readability` 0 failed（selftest 11）；`no-kotlin-check.ps1` → `VERDICT: PASS (13 OK line(s))`。

### REFACTOR — optional
- Cleanup: 无。
- Regression evidence: 不适用。

### LOOP — optional
- Command and rounds: 清单重生一轮、账本重生一轮、metrics 一轮、两档整跑各一轮。
- Failure identity and repeat count: 三个红各一次，且各有唯一根因（注册缺失／清单过期／metrics 过期）。
- Stop condition hit: 两档整跑只剩 `line-budget`，且其红可归因到未提交的混合工作树。
- Handoff: 105 的整跑与重打包包含本片。

### TDD or sizing exception — only with prior user approval
- Exception type: configuration registration / generated-data refresh
- Reason: 本片没有业务行为可测；它改的是「守门表」与「从当前树重生的生成物」，判定靠整跑读数与两份生成物自检，而不是新写一条业务用例。
- User approval: 用户「使用 harness 进行修改，修改完成后提交代码 push」，并确认 22 条全做；086 卡面已把「注册放 104」写成实施顺序。
- Alternative verification: `check_run_all_guards.py` 的 33 例注册表自检、`parity_ledger_selftest` 20 例、`metrics_report` 的逐行复算、两档整跑转录。

## Implementation notes

- 注册项的判据行必须按各自真实输出写：两支判据打 `N check(s) failed`，两个 selftest 打 `selftest judgement cases executed =`。写成 `SELFTEST cases=` 会让一条绿的守门被判「没打印判据」——这与 roster 里 `verify_1to1_selftest` 的双判据行是同一个教训。
- `line-budget` 的红不是本片的缺陷：它量的是「相对 HEAD 的业务变更行」，混合工作树在提交前必然超 500；本项目已把这条登记为提交形状问题，提交后重跑即归零。本片按原样记录，不动阈值。
- `checkers_selftest` 的 `INCONCLUSIVE` 是 `check_metrics_report` 判红的上游后果（它把基线守门也跑一遍），所以修 metrics 的顺序必须是「先重生成、再整跑」，否则整跑会以 `EXITCODE=3` 结束。
- 屏幕清单的新鲜度判据抓到的是一次真实的资源面变化：空态出口按钮让 `surfaces` 从 16 变 17。手改 JSON 也能让它变绿，但那是把「从树重推」改成「抄读数」，本片坚持重生。

## Verification report

- Verdict: PASS
- Evidence: `104-red-parity.txt`（3 failures / EXITCODE=1）、`104-green-parity.txt`（20/20 / EXITCODE=0）、`precommit-run.txt`（`RUN-ALL-GUARDS tier=precommit selected=10/49`，9 PASS + `line-budget` FAIL）、`core-run.txt`（`RUN-ALL-GUARDS pass=39 fail=0 inconclusive=0 fault=0 timeout=0`、`VERDICT: PASS (subset only — 39/49 entry(ies) judged)`、`EXITCODE=0`）、`checkers_selftest` 33/33、`no-kotlin.txt`（`VERDICT: PASS (13 OK line(s))`）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除；新增 4 条注册项，每条都带自己的判据 needle，正反两面由 `check_run_all_guards.py` 的自检覆盖。
- Gaps: `line-budget` 在提交前恒红，本片不闭合它；core 档整跑耗时约 5 分钟（含浏览器判据）。
- Risks: precommit 档 +16 秒；清单/账本是生成物，下一次资源变化需要再重生。

## Handoff notes

- Completed: 两支判据入库、清单与账本重生、metrics 重生成、两档整跑落盘。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 105 的整跑与重打包包含本片；之后整树提交与推送。
- Watch-outs: 改任务卡后先 `metrics_report.py --write` 再整跑，否则 `check_metrics_report` 会把 `checkers_selftest` 一起拖成 INCONCLUSIVE。
