# 复核报告归档

本目录存放 `code-reviewer` 角色的产物，一份复核一个文件：`REVIEW-YYYY-MM-DD-<kebab-case>.md`。

骨架、字段与写法只由 `Docs/REVIEW.md` 定义，本文件不复制它；只写三条本目录特有的规矩：

- 不复用、不覆盖旧报告：同一片代码重审时另起一份，旧报告留在原地作为当时读数。
- 报告只读不改：报告落地后发现结论有误，另起一份并在新报告里指回旧报告，不原地修订。
- 机械检查：`python -X utf8 .codex/scripts/check_code_reviewer_agent.py`（含「本目录必须在场」这条）。
