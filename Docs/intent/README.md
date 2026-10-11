# Docs/intent —— 变更意图的权威存放处

> 本目录是 Stage 1（Plan）的产物位置。产品事实以 `Docs/01-demand`、`Docs/03-prd-draft`、`Docs/05-technical-design` 为准；本目录只承载「变更意图与收敛过程」。

## 命名规则

`INTENT-YYYY-MM-DD-<kebab-case>.md`，例如 `INTENT-2026-09-22-export-answer-image.md`。
日期取创建当天，名字用英文小写加连字符；一个想法一个文件，不要追加到旧文件里。

## 什么时候写到这里

- 有一个还没成形的想法、优化点或问题 → 调用 `intent-capturer`。
- 产品事故或使用中反复出现的问题要回流成需求 → 同样在这里新建一个文件。

## 状态取值

| status | 含义 | 下一步 |
| :--- | :--- | :--- |
| draft | 还在收敛中 | 继续追问 |
| needs-more-info | 缺关键信息，暂不可拆分 | 补齐 Open questions 标出的字段 |
| accepted | 已确认值得做且可以做 | 移交 requirement-decomposer 拆分 |
| rejected | 明确不做 | 保留文件与否决理由，不要删除 |

## 与其它事实源的分工

- 产品事实（需求边界、功能规则、技术方案）：`Docs/01-demand`、`Docs/03-prd-draft`、`Docs/05-technical-design`。
- 变更意图与收敛过程：`Docs/intent`（本目录）。
- Harness 任务状态、TDD 证据与验收结论：`.trae/tasks`。
- 工程约束与守门规则：`.trae/rules`。

## 交接

当前已确认的 Web 本地版范围与设计交付见 [2026-10-10 意图](INTENT-2026-10-10-local-web-edition.md)；两端数据独立，开发资料从 [AI 交接入口](../05-technical-design/TreeLearn_Web本地版_AI开发交接入口.md)读取。

- 模板：`.trae/templates/INTENT.md`
- 产出角色：`intent-capturer`（定义在 `.trae/agents/`）
- 收敛判据：`pm-discovery`（定义在 `.trae/skills/`）
- 下游：`requirement-decomposer`（定义在 `.trae/agents/`），它要求已有确认的父 requirement；`status: accepted` 的 intent 就是它的输入。
- 交付后：按 `.trae/rules/documentation.md` 把结论回写到 `Docs/03-prd-draft` 等当前态文档。
