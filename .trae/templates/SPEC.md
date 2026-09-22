# Spec: <一句话标题>

> 这是骨架，不是门禁：四节顺序照用即可；某一节现在写不出来就写 `DEFERRED:待 <条目 ID>` 并说明落地后补什么。自检脚本只查四件事——文件在不在、四节标题在不在、`intent:` 是否指向真实文件、有没有占位符与裸 hex / 临场 px。

id: SPEC-YYYY-MM-DD-<kebab-case>
title: <一句话标题>
date: YYYY-MM-DD
intent: Docs/intent/INTENT-YYYY-MM-DD-<kebab-case>.md
author: spec-author
status: draft

## Requirement spec（需求规格）

<从已 accepted 的 intent 抄来，不改写它的结论。范围、验收方向、非目标都要能追回 intent 的哪一条。>

- 范围：
- 验收方向：
- 非目标：
- 来源：intent 文件路径 + 它里面的条目

## Interface plan（界面方案）

<信息架构、入口位置、状态与异常、数量与呈现方式。数值只引用 token 名，不写裸 hex、不写临场 px。>

- 信息架构：
- 入口位置：
- 状态与异常：
- 数量与呈现方式：
- 来源：引用的设计 token 名，以及原型里对应的小节

## Technical spec（技术规格）

<接口、数据结构、状态迁移、错误语义。前置条目还没落地时，这一栏写 `DEFERRED:待 <条目 ID>`，并说明落地后要补什么，不要留空。>

- 接口：
- 数据结构：
- 状态迁移：
- 错误语义：
- 来源：代码路径或 `.trae/ROADMAP.md` 的条目 ID

## Doubts（疑虑点）

<与现有代码、规则，或互相矛盾的政策冲突的地方，逐条列出来交人拍板。没有就写「无」，不要省略这一节。>

- 疑虑 1：<冲突是什么>（冲突对象：<文件/条目>；两种走法：<A / B>；谁拍板：<人>）

<!-- FIELDS: id | title | date | intent | author | status | Requirement spec（需求规格） | Interface plan（界面方案） | Technical spec（技术规格） | Doubts（疑虑点） -->
