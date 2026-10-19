- Intended change and old behavior to preserve: 意图 = 派工前的影响面清查有格式、有判据、有落点。
  保留 = B1 守门 14 条判据的逐字读数（块 05 与块 01 对照片 024 证据）、`check_context_pack.py` 全部判据、
  `run_all_guards.py` 其余注册项（条数随每片交付变化，读数用 `--list` 现场取，本卡不复述）、`HARNESS.md` 第 1/2/4/5/6 步原文（接线只做在一句之内）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 = 角色正文与守门脚本；
  消费者 = `.codex/scripts/run_all_guards.py:89-91`（core 档）、`.codex/scripts/dev/checkers_selftest.py:87-89`（第 7 条基线所在元组；片 029 在其上插了 3 个常量、片 030 又插了 4 个并把元组撑到三行，行号由 `:71` 挪到这里，片 031 再插 4 个常量与第 12 条基线，由片 031 复算）、
  `.codex/HARNESS.md` 第 3 步。同级入口 = `check_code_reviewer_agent.py`，它与本片共用
  `harness_check_lib.agent_section_names()`（同一推导两份的那一处已合并）。
  平台分支 = 不适用：无 Android/前端生产代码、无 API、无 SQL；实测与推测在角色正文里的区分是**输出格式要求**，
  不是运行时分支。
- Native bridge or independent-client compatibility/update requirements: 不适用（无桥接字段被触碰；
  `frontend/` 与 `android/` 的代码一行未改，判定形状只作用于 `.codex/` 内的流程资产）。
- Behavior-neutral non-applicability reason, if applicable: 本片的可反驳理由是两条命令：
  `git diff --stat HEAD` 里 `android/**`、`frontend/**` 零命中；以及块 05 的 B1 守门逐字读数与片 024 相同。
