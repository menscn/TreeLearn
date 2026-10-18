# Intent: <一句话标题>

id: INTENT-YYYY-MM-DD-<kebab-case>
title: <一句话标题>
date: YYYY-MM-DD
author: <提出人>
origin: <用户想法 / 产品事故 / AI 建议 / 其他>
status: draft

## Problem（问题与背景）

<现在是什么状态，哪里疼。把「观察到的现象」和「推测的原因」分开写。>

- 问题/背景：
- 用户/场景：

## Users and scenarios（用户与场景）

<谁在什么情况下会遇到；本期是不是只有你自己用。>

## Expected outcome（预期结果）

<做完之后哪件事变得可能了，用可观察的变化描述。>

- 预期结果：

## Core flow（核心流程）

<主路径，以及被打断、撤销、重试时分别怎么办。>

- 核心流程（含中断、撤销、重试）：
- 输入/输出：
- 状态与异常：

## Constraints and non-goals（约束与非目标）

- 约束：
- 权衡偏好：
- 非目标（non-goals）：

## Open questions（开放问题）

<没有答案的先留在这里，不要用猜测填空。>

- 已确认事实、假设、未知项：

## Convergence verdict（收敛判定）

每问都要给「结论 + 证据」；证据必须能被别人查到。

### 有价值吗

结论：是/否/需要更多信息
证据：

### 值得做吗

结论：是/否/需要更多信息
证据：

### 可以做吗

结论：是/否/需要更多信息
证据：

## Decision record（决策记录）

- 决策权归属：
- 决策人：
- 决策日期：
- 终态：accepted / rejected / needs-more-info
- 否决理由（status 为 rejected 时必填）：
- 验收标准与证据：
- 风险与后续范围：

## Next step（下一步）

- accepted：移交 requirement-decomposer，并附「移交清单」，写明子任务切片建议、依赖顺序和不可并行的部分。
- rejected：保留本文件并写清否决理由，不要删除。
- needs-more-info：列出 Open questions 里还缺哪些字段；补齐之前不产出可执行需求。

<!-- FIELDS: id | title | date | author | origin | status | Problem（问题与背景） | Users and scenarios（用户与场景） | Expected outcome（预期结果） | Core flow（核心流程） | Constraints and non-goals（约束与非目标） | Open questions（开放问题） | Convergence verdict（收敛判定） | Decision record（决策记录） | Next step（下一步） -->
