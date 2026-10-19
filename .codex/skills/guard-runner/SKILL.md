---
name: guard-runner
description: 一条命令整跑仓库全部只读守门，并把「退出码 0」与「判据行真的打印出来」绑成双条件。提交前、verifier 验收、或需要证明「守门其实运行过」时触发；按栈挑选最小检查集的目标仍由 lint-all 负责。
---

# guard-runner —— 只读守门一键整跑

## 何时触发

- 一片要提交前：整跑一次，把输出原样贴进任务证据。
- verifier 验收：需要「这条结论有运行过的证据」而不是「脚本存在」时。
- 有人（或 AI）报告「门禁全绿」而说不出跑了哪些脚本时。

按实际改动面**挑选**最小检查集的规则不在这里，见 `.codex/skills/lint-all/SKILL.md`；
本技能只负责「全部整跑」这一件事，两者不要互相替代。

## 跑法

```text
python .codex/scripts/run_all_guards.py [--tier core|precommit|all] [--only 子串] [--list]
```

- `core`（默认）：只读、确定性、不依赖构建产物或 git 增量的 25 条。任何一片提交前都要整跑。
- `precommit`：依赖未提交增量、`frontend/dist` 或 Gradle 的 6 条（`line-budget`、`no-kotlin-check`、
  `guard-selftest -Fast`、`frontend verify`、`check_offline_bundle` 及其自检）。
- `--list`：打印注册表（档位、守门名、要找的判据行片段），不运行任何东西。
- `--only 子串`：只跑名字含该子串的条目。跑出来的绿话会写明 `subset only`——
  子集绿不等于整表绿，别把它当成整跑的证据。

## 判定规则（为什么不是只看退出码）

每条守门要同时满足两件事才算 PASS：

1. 退出码落在它自己声明的 `pass_codes` 里（几乎都是 `0`）。
2. 它的 stdout/stderr 里真的出现了声明的判据行片段（`needles`，命中任一即可）。

第 2 条是这个汇总器存在的理由：解释器换成了别的、路径解析失败、被检集合变空，
都会让一支脚本「什么都不判却仍然返回 0」。所以「没打印判据行」直接判 FAIL，不看退出码。

汇总器自己的退出码：

| 码 | 含义 |
|---|---|
| 0 | 入选守门全部打印了判据行且全部判绿 |
| 1 | 至少一条判红，或至少一条没打印判据行 |
| 3 | 汇总器没能把每条入选守门都跑起来（脚本/解释器缺失、输出不是合法 UTF-8、注册表自相矛盾、过滤选中零条）→ 本次结论不成立 |

`3` 与 `1` 分开的理由和仓库其余守门一致：「无从认证」被读成「判红」还算诚实，
被读成「通过」才是事故。

## 加一条守门

在 `run_all_guards.py` 的 `GUARDS` 表里加一行，写清五件事：名字、怎么起、要找哪一行、
进哪一档、哪些退出码算绿/算红。

- `args[0]` 是相对仓库根的脚本路径，其余参数原样传递。
- `needles` 必须在该守门**判红时也一样出现**的那一行里取（例如 `Checks` 系列的
  `check(s) failed`、`VERDICT:`），否则一次判红会被误报成「没打印判据」。
- 新守门默认进 `precommit`，除非它确定不依赖构建产物、实机、本机服务端或未提交增量。
- 注册表有裸下限（`MIN_ROSTER`、`MIN_CORE`）：从表里删条目不会让汇总器更绿，只会让它判 3。

## 自检

```text
python .codex/scripts/check_run_all_guards.py
```

26 条用例，在合成注册表上逐条注入违规并核对判决（跑的是临时子进程，真实仓库文件一个都不改）：
rc=0 但静默 → FAIL、rc 落在判绿/判红集合外 → 无从认证、脚本缺失/解释器起不来/输出乱码 → 故障、
挂住不回 → 超时判红、空表与被挪空的 core 档 → 注册表故障、子集绿话必须写明 `subset only` 等。
改过 `run_all_guards.py` 的任何判定分支，都必须整跑这支自检并留输出。

## 不在这张表里的东西

需要实机或本机服务端才能判的走查不在只读汇总面内：`web_walk.mjs` 正文（只跑它的 `--selftest`）、
`capture_screen.py`、像素量测与安装冒烟属于 ROADMAP 的 C4 release-gate。
`dev/gen_*.py`、`inline_offline.mjs`、`make_t0xx_evidence.py` 会写文件，不是守门。
`dev/count_*.py`、`dev/review_*.py` 是给人看的打印工具，没有判绿语义。
