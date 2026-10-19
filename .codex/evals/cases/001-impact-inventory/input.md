# 001 影响面清单 —— 输入与来源

派工输入（一片要把「动手前查影响面」落成只读角色的任务，取片 026 当时的 Goal/Scope 与
`.codex/ROADMAP.md` 的 B4 执行提示词）：读被分配任务卡与它要改的产物，把四类面查一遍，
按 `.codex/tasks/_TEMPLATE.md` 的 `### Behavior impact` 四个字段写进那张卡，
每条结论要带命令与 `路径:行`，查不到就写「未找到」而不是沉默。

产物来源：`.codex/tasks/T-2026-10-19-codebase-researcher-impact-role-026.md` 的
`### Behavior impact` 小节逐字快照，快照日期 2026-10-19。转录步骤是从该小节标题之后
取到第一个 `## ` 标题为止、丢弃模板的引用行，只留四个字段条目及其续行；
本片转录后与源小节零差异（两条 `路径:行` 引用在片 028 里已就地补成仓库相对路径，
因为裸文件名按 basename 全库搜索会把「重名即命中」当成真话，见
`.codex/scripts/harness_check_lib.py` 的 `path_line_citations` docstring）。

为什么拿它当第一例：它是本仓库里唯一一份由角色产出、且当时靠人读一遍验收的 AI 产物。
它带着两条 `路径:行` 指向真实注册处（core 档那一行、基线元组的那一项），
所以 `pointers-alive` 判的是活的事实，不是写在期望文件里的一段回忆。
