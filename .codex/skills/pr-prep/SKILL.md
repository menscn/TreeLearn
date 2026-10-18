---
name: pr-prep
description: Project Codex Harness skill migrated from `.cursor/skills/pr-prep/SKILL.md`.
---

# pr-prep - Codex Harness migration

## Codex adaptation
This is a project-local Codex skill migrated from Cursor. Prefer Codex-native tools/subagents where available, preserve safety constraints, and keep `.cursor` as reference material.

## Source
- Migrated from `.cursor/skills/pr-prep/SKILL.md`.
- `.cursor` remains reference material; this `.codex/skills` file is the Codex runtime skill surface.

# PR 准备

## 何时使用

用户要求准备或创建 PR 时使用。是否实际提交、推送或创建 PR，以已有用户授权为准；准备说明本身不授权这些动作。

## 流程

1. 查看当前分支、目标分支及实际差异，确认 PR 范围；不要把其他任务的工作区修改混入本次变更。
2. 按 [HARNESS 防回归与行为证据规则](../../rules/regression.md) 核对实际影响面，列出必须保留的行为及相邻失败场景。共享区包括 API 与原生桥接契约、共享错误语义、Flyway、Compose 和环境配置，不能只按文件目录认定低风险。
3. 核对适用 lint、测试、构建、启动、真实交互与专项 guard 的证据。Harness 按既有规则要求 verifier；直接执行不强制 Task、行数预算或 verifier，但仍须满足公共防回归要求与适用 guard。不要为了 PR 重复运行没有新增疑点的全部检查。
4. 起草中文 PR 说明，先讲用户问题和结果，再列适用信息：

   - 预期变化与必须保留的旧行为。
   - 生产者、消费者、同族入口、平台分支及共享契约影响。
   - 新增行为、保留行为、相邻失败场景的验证证据，区分单测／契约／启动／真实交互。
   - 删除或修改断言的依据及等价行为覆盖。
   - 文档影响记录、未验证项、版本兼容和部署／客户端更新要求。
   - 关联 Task 与 verifier 结果（仅适用时）。

5. 引用已完成的适用 guard/review 结论，明确尚缺的证据。已知错误为 FAIL，缺少必要证据为 PARTIAL／未验证；可以如实起草说明，不能写成已经验收通过。遵守现有共享区审查要求，不因引用本流程新增例行用户审批。

## 边界

- 不自动推送、提交或创建 PR；已有明确授权时继续完成授权动作，不重复询问。
- 不将测试数、构建成功或静态源码匹配写成浏览器／Android 行为验证。
- 不把历史迁移文件、旧 Task ID 或过期接口路径当成当前可执行验收示例。
