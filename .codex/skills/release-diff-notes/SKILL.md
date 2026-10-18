---
name: release-diff-notes
description: Use when the user asks to compare two git refs, branches, tags, version points, or commits and produce a Chinese implementation document, release implementation notes, changelog, or grouped summary of changes; especially when the output must include API contracts, business flow diagrams, self-test cases, and documentation changes.
---

# Release Diff Notes

## Overview

Generate evidence-based Chinese change documentation from two git references. Support two deliverables:

- 开发实现文档：default for detailed requests; write a Markdown document under `dist-images/` that explains what changed, how it works, how it was tested, and which docs were updated.
- 发布说明/变更摘要：for release-note, changelog, announcement, or short summary requests; write concise user-facing Chinese notes grouped by 新增功能、修复问题、优化体验 unless the user gives another template.

## Trigger Inputs

Accept any two comparable git refs:

- Branches: `mvp_1.7_20260630` vs `mvp_1.6_20260623`
- Tags: `v1.7.0` vs `v1.6.0`
- Commits: `<old_sha>` vs `<new_sha>`
- Mixed refs: tag vs branch, branch vs commit

If the user says `对比 A 与 B` or `A 和 B`, infer the newer/later ref as target and the older/earlier ref as base using names, dates, and commit ancestry. Ask only when order cannot be inferred safely.

## Required Repository Context

Before git analysis, read repository instructions:

1. Read root `AGENTS.md` instructions if present in the conversation or filesystem.
2. In `Treelearn`, follow the root routing and read `.codex/rules/documentation.md`; add `.codex/rules/contracts.md` when API/contracts are in scope. Read `.codex/HARNESS.md` only when Harness orchestration is selected.
3. Respect safety boundaries: this skill is normally read-only except for the requested output document.

## Workflow

1. Resolve refs.
   - Prefer remote branches when local branches are stale or missing, for example `origin/<branch>`.
   - If either ref is missing, run `git fetch --all --prune` when network is available.
   - Verify with `git rev-parse --verify` and record full commit SHA, date, and subject.

2. Compare with `base..target`.
   - Sanity-check that the log represents changes introduced by the target after the base.
   - Gather:
     - `git rev-list --count base..target`
     - `git diff --shortstat base..target`
     - `git log --oneline --decorate --date=short --pretty=format:'%h %ad %s' base..target`
     - `git diff --stat base..target`
     - `git diff --name-status base..target`
     - For large diffs: `git diff --dirstat=files,0 base..target`

3. Cluster changes by shipped capability, not by commit prefix.
   - Merge small commits into functional themes.
   - Treat tests and docs as evidence unless they are the user-requested deliverable.
   - In this repo, common clusters include knowledge v3, document upload, PDF preview, annotations, QA/SSE, learning assets, Android shell, admin, telemetry, health checks, config, and scripts.

4. Inspect contracts and implementation evidence.
   - Java: controllers, request/response DTOs, services, DB migrations, tests.
   - Core Python: FastAPI routers, schemas, pipelines, DAO/lifecycle code, tests.
   - Frontend: API adapters, UI flows, scripts, package scripts.
   - Android: plugins, stores, bridge types, release artifacts.
   - Docs: `Docs/05-架构/`, `Docs/06-业务/`, `Docs/07-接口/`, `README.md`, `Docs/README.md`, `.env.example`, Docker files and compose files.

5. Choose output mode.
   - If the user asks for `开发实现文档`, `实现说明`, `接口说明`, `业务流程`, `自测试用例`, or asks to place a document under `dist-images`, use **Implementation Document Mode**.
   - If the user asks for `发布说明`, `变更说明`, `changelog`, `release notes`, `更新日志`, or wants copy for product/release communication, use **Release Notes Mode**.
   - If both are requested, produce both, or create one document with a release-note section followed by detailed implementation sections.
   - If ambiguous, prefer Implementation Document Mode in `Treelearn` because it preserves the release-note summary in the overview while satisfying detailed audit needs.

6. Produce the requested output.
   - Implementation document default path: `dist-images/<target>_vs_<base>_开发实现说明.md`.
   - Release notes default path when a file is requested: `dist-images/<target>_vs_<base>_发布说明.md`.
   - Sanitize filename characters if refs contain `/`, `:`, or spaces.
   - Use UTF-8. Never write mojibake or `????` placeholders.
   - If `dist-images/` is ignored by git, mention that in the final response.

7. Verify the output.
   - Re-read it as UTF-8.
   - For implementation docs, check required markers: `接口说明`, `业务流程`, `自测试用例`, `文档说明修改`.
   - For release notes, check required headings or the user's requested headings.
   - For implementation docs, check interface detail markers when applicable: `请求体`, `请求样例`, `响应格式`, `响应样例`, `参数说明`.
   - Check `git status --short` and do not touch unrelated files.

## Implementation Document Mode

Use this structure for implementation documents unless the user provides a stricter template:

```markdown
# <target> 对比 <base> 开发实现说明

生成时间：
对比范围：
基线版本：
目标版本：
差异规模：

## 1. 修改总览
用表格按功能簇归纳代表提交和主要内容。

## 2. 接口说明
只写有新增或变更的接口。每个接口包含：
- 定义：方法、路径、调用方、用途
- 请求体
- 请求样例
- 响应格式
- 响应样例
- 参数说明
- 兼容性、幂等、错误码或安全边界

## 3. 业务流程
按功能簇提供 Mermaid flowchart 或 sequenceDiagram。
补充关键逻辑要点、共享状态、幂等性、跨实例状态存放位置。

## 4. 自测试用例
按 backend、optional Python service、frontend、Android、数据库迁移等分组。
列测试文件、脚本、覆盖内容和建议执行命令。

## 5. 文档说明修改
按架构、业务、接口、环境启动、计划任务等分组列出同步修改。

## 6. 兼容性、风险与回滚关注
列跨栈风险、数据迁移、配置依赖、旧客户端兼容和回滚注意点。

## 7. 交付物
列 dist-images 或其它发布产物。
```

## Release Notes Mode

Use this structure for concise release notes unless the user gives a different template:

```markdown
对比范围：<base>..<target>

## 新增功能

- 新增「功能名」：说明它让哪类用户能做什么，以及带来的价值。

## 修复问题

- 修复某场景下异常、错误、失败、卡住、数据不一致或渲染错误的问题。

## 优化体验

- 优化某链路的速度、稳定性、清晰度、可维护性、观测能力或部署体验。
```

Release-note writing rules:

- Use product-facing Chinese, not commit-log Chinese.
- Collapse many technical commits into one user-facing item.
- Mention internal terms like Core、Java、PDF、Android、Redis、MySQL、MinIO、SSE only when they explain user/operator value.
- Do not include API request/response details unless the user asks for technical release notes.
- Do not list docs-only task notes unless documentation is the deliverable.
- Keep each bullet high-signal and readable by product, QA, ops, and developers.

## Interface Detail Rules

For each changed API, include enough detail for a developer/tester to call it:

- HTTP method and path.
- Auth or header requirements if visible.
- Query/path/body parameters.
- JSON request sample or binary/multipart description.
- JSON response sample or SSE event sample.
- Stable error codes when changed.
- State and idempotency semantics.

Do not invent contracts. If code only shows part of a contract, say the missing part is inferred from the implementation or omit it.

## Mermaid Guidance

Use Mermaid when explaining cross-service or async flows:

- `flowchart TD` for lifecycle and decision flows.
- `sequenceDiagram` for frontend -> Java -> Core -> storage/DB interactions.
- Keep node labels short and factual.
- Use Chinese labels where helpful.

## Evidence Commands

Useful PowerShell-friendly commands:

```powershell
git branch -a --list '*<ref>*' --format='%(refname:short) %(objectname:short) %(committerdate:iso8601)'
git ls-remote --heads --tags origin '*<ref>*'
git rev-parse --verify <ref>
git show -s --format='%H%n%ci%n%s' <ref>
git rev-list --count <base>..<target>
git diff --shortstat <base>..<target>
git log --oneline --decorate --date=short --pretty=format:'%h %ad %s' <base>..<target>
git diff --stat <base>..<target>
git diff --name-status <base>..<target>
git diff --dirstat=files,0 <base>..<target>
git show <target>:<path>
git diff <base>..<target> -- <path>
```

## Common Mistakes

- Reversing `base..target`.
- Comparing a stale local branch when a newer `origin/<branch>` exists.
- Listing every commit instead of grouping by capability.
- Treating docs/task notes as shipped features without implementation evidence.
- Missing API contract changes hidden in request/response DTOs or frontend adapters.
- Writing a release-note summary when the user asked for an implementation document.
- Omitting self-test evidence.
- Forgetting to mention ignored output directories such as `dist-images/`.
