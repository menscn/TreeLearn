---
name: commit-cn
description: Create Chinese Conventional Commits for this repository, including Git-history regression protection references, scope review, automatic logic-neutral Spotless/Checkstyle cleanup, staged secret scanning, required Lore trailers, and mandatory GitHub quality-gate waiting after an explicitly requested push to main. Use when the user asks to commit, create a commit, or commit and push.
---

# commit-cn - Codex Harness migration

## Codex adaptation
Before committing, normalize authorized backend Java files with the Java style preflight below, then run `secret-scan` against the final staged diff. Follow the [regression prevention and behavior evidence guard](../../rules/regression.md). Direct execution does not require a Task or verifier; Harness retains its existing requirements. Commit wording may use Chinese Conventional Commit style, but this repo also requires the Lore Commit Protocol trailers: `Constraint:`, `Rejected:`, `Confidence:`, `Scope-risk:`, `Directive:`, `Tested:`, and `Not-tested:` when applicable. Never use `--no-verify`.

## Source
- Migrated from `.cursor/skills/commit-cn/SKILL.md`.
- `.cursor` remains reference material; do not write new Codex Harness task state there; this `.codex/skills` file is the Codex runtime skill surface.

## Original skill content adapted for Codex

# commit-cn —— 中文 Conventional Commits 提交

## 何时触发

- 用户说"提交"/"commit"/"打个提交"/"提交一下"。
- 主 Agent 完成一个 task 且 verifier PASS 后准备落库。

## 强制流程（不可跳步）

1. `git status` + `git diff --staged` 看实际入库的内容；仅纳入用户已授权范围内的文件，范围明确时不重复询问，不混入其他任务修改。
2. 若提交范围包含 `backend/**/*.java`，提交前必须执行下方“Java 样式自动处理”，并在处理后重新审查和暂存最终差异。
3. **不可入库的文件类型**（命中即拒绝）：
   - `.env` / `.env.android` / `*credentials*` / `*secret*`（除 `.env.example`）
   - `**/*.lock` 中明显异常的（lockfile 通常 OK，但要确认是依赖更新而不是误改）
   - `node_modules/`、`dist/`、`build/`、`target/` 子文件
4. 起草中文 Conventional Commits 消息（**必须中文**），按下方[回归保护提交记录](#回归保护提交记录)记录新保护或已有保护的引用：
   - `Scope-risk:` 基于真实调用链、共享契约及平台分支，不能仅因文件少或只改前端而写低风险。
   - `Constraint:` 对应实际保留的行为；`Tested:` 记录本次场景、证据层级、环境与结果；`Not-tested:` 如实列出必要证据缺口及影响。历史保护的检索、继承、断言修改和阶段性提交判定统一遵循[防回归规则](../../rules/regression.md#git-提交驱动的回归棘轮)，不以历史 PASS 替代当前验证。

   ```
   <type>(<scope>): <一句话中文摘要>

   - <要点 1>
   - <要点 2>

   关联 task: T-YYYY-MM-DD-NNN（如有）
   ```

   `type` 枚举：`feat` / `fix` / `chore` / `docs` / `refactor` / `test` / `build` / `ci` / `perf` / `style`。
   `scope` 例：`frontend` / `java` / `python` / `android` / `harness` / `docs`。

   **与每日同步**：若 task 要点已写入 [`Docs/02-计划/每日同步.md`](../../tasks/)，commit 仍可保留脚注 `关联 task: T-...`；**是否删除** `.codex/tasks/T-*.md` 不在本 skill 强制流程内，按 **daily-sync** skill「Task 归档与删文件」执行。

5. 最终暂存及消息定稿后，按[最终拦截](../../rules/regression.md#验证与最终拦截)核对实际 staged diff、命中保护、当前验证和提交记录；未修复缺陷时不强填修复条目，未命中保护时给出检索范围与具体依据。之后按[文档一致性规则](../../rules/documentation.md)执行 `python -X utf8 scripts/ci/check_documentation.py --staged --run-checks`；文档影响记录、关联检查或必要只读复核缺失时不得提交。随后 **调 `secret-scan` skill** 扫最终 staged 改动；命中 → 立即停止，输出泄露条目并要求处理后再来。样式处理、暂存内容或相关证据变化（含 merge/rebase）后重新执行受影响核对和门禁，不复用过期扫描或验证。

6. 用明确 UTF-8 编码的消息文件和 `git commit --file <消息文件>` 提交，保留真实换行及要求的 trailers；不要跨 shell 拼接或通过命令替换构造消息。消息文件使用临时位置，不纳入提交。

7. `git status` 二次确认提交成功。
8. 用户明确要求 push 时才 push；若目标为 `main`，必须继续执行“main push 远端质量门禁”，不能把 push 成功当作任务完成。其它目标分支维持普通 push 后核对，不自动触发或等待远端质量门禁。

## 回归保护提交记录

字段放在提交正文，原有 Lore trailers 保留。每个独立修复使用一个本提交内唯一的条目名；语义、必跑范围、继承和缺口处理以[防回归规则](../../rules/regression.md#git-提交驱动的回归棘轮)为准。

```text
回归保护：<表达问题的条目名>
问题：<用户可见的错误及触发条件>
范围：<仓库相对路径、方法/入口、相关调用方及平台分支>
复现：<前置状态、输入及必要操作顺序>
结果：<修复前错误结果> → <修复后正确结果>
正常/边界：<输入和期望输出；包含适用的相邻反例>
验证：<测试文件及用例、工作目录、命令或操作步骤、必要环境>
关联：<原保护引用及保留、补验证、迁移或替代说明；无则省略>
```

仅复用已有保护时用短记录，不复制旧条目：

```text
回归复核：<完整提交 SHA>:<条目名或旧提交的问题/场景摘要>
命中：<本次实际改动如何影响原保护>
结果：<必跑/与本次无关/已被授权替代/尚待核实；依据及当前验证结果>
```

补验证、代码或测试迁移、授权替代只追加变化部分及原引用；同一场景去重。具体输入、大样本、验证入口和结果的要求见规则正文。无新增修复的功能、重构或样式提交沿用复核记录；没有相关保护时在正文给出检索范围及排除依据，不编造历史问题。不新增修复台账，不批量回填或改写历史消息。

## main push 远端质量门禁

仅在用户明确要求 `commit + push` 且 push 目标为 `main` 时执行。门禁绑定实际推送的提交 SHA，不绑定本地分支名或“最新一次”模糊状态。

### 锁定并等待精确 SHA

1. push 后立即记录 `git rev-parse HEAD`，并用 `git ls-remote --heads origin refs/heads/main` 确认远端 `main` 正好指向该 SHA；不一致时停止，不能等待或汇报另一个提交的结果。
2. 使用本 skill 自带脚本等待该 SHA 的 GitHub check runs：

   ```powershell
   python -X utf8 .codex/skills/commit-cn/scripts/wait_main_quality.py --sha <40位提交SHA>
   ```

   脚本只接受该 SHA 在 `main` 上由 `push` 事件创建的 Actions runs，不能拿同一 SHA 之前的 PR checks 冒充本次 push 门禁。默认每 15 秒轮询、每 60 秒至少输出一次进度，45 分钟超时。调用脚本时保持可轮询的执行会话，并至少每 60 秒向用户同步一次仍在等待的状态，不用单次长时间阻塞调用隐藏进度。
3. 只有该 SHA 上的 `Change scope`、`Documentation gate` 和 `Quality gate` 均为 `success`，且下列模块及共享检查全部出现、结束，结论为 `success` 或按范围跳过的 `skipped`，才可报告 `commit + push` 完成。`Quality gate` 会核对每个模块及独立共享作业的应跑状态：
   - `Multi-device architecture boundaries`
   - `Java format, lint, tests and package`
   - `Frontend lint, tests and build`
   - `Admin lint, tests and build`
   - `Android Java-only, i18n and telemetry guards`
   - `Static analysis`
   - `pytest (Python 3.12)`
   - `Web parser checks, tests and build`
   - `Mobile prototype tests, Web and Android builds`
4. 脚本退出码：`0` 表示范围判定与总门禁通过、模块及共享检查均已成功或按范围跳过；`1` 表示已结束但门禁或相关检查失败；`2` 表示检查缺失/超时、连续 GitHub 查询错误或输入无效。`Change scope`、`Documentation gate` 与 `Quality gate` 不接受 `skipped`；任何检查的缺失、queued、in_progress、failure、cancelled、timed_out、action_required、neutral、stale、查询失败都不能算完成。
5. 最终答复必须列出远端 `main` 的 SHA，以及范围判定、文档门禁、总门禁和各模块及共享检查的结论与 GitHub details URL，明确哪些检查按范围跳过。没有总门禁成功证据时只能报告失败或阻塞，不能只说“已 push”“已完成”。

### 门禁失败后的处理

先汇总所有失败项，再从脚本输出的 job URL 提取 `actions/runs/<run-id>`，按 run 去重执行 `gh run view <run-id> --log-failed`，保留失败检查名、URL、关键错误和根因判断。不要只修第一个失败就忽略其它已结束的失败。

- **可自动处理的低风险范围**：仅限可证明行为不变的格式化、空白/换行、import 排序、确认未使用 import 的删除，以及本 skill“Java 样式自动处理”已有白名单。修复后运行对应本地失败命令和必要回归，重新检查实际 diff，重新暂存并调用 `secret-scan`；使用新的中文 Conventional Commit 追加提交后 push，禁止 amend 已 push 的提交，然后对新 SHA 重新等待范围判定与总门禁。
- **瞬时外部失败**：有明确 runner、网络或依赖下载瞬时证据时，可以对失败 run 执行 `gh run rerun <run-id> --failed`，随后仍按同一 SHA 等待范围判定与总门禁；没有瞬时证据时不得用 rerun 掩盖真实失败。
- **必须询问用户的范围**：任何生产逻辑、测试断言/快照、依赖或 lockfile、workflow 配置、API/DTO、数据库/数据结构、配置语义、错误语义，或无法证明行为不变的修改。询问前给出失败证据、影响面、建议选项及各自风险；得到答复前不改这些内容。
- 自动低风险修复和瞬时失败 rerun 合计最多 2 轮；同类失败重复出现时即使未满 2 轮也停止自动处理并询问用户。不得无限追加提交或重复消耗 Actions。
- 同时存在低风险问题和逻辑问题时，可以准备相互独立的低风险修复，但在逻辑方案确认前不追加新的 push，也不得宣称门禁已解决。

## Java 样式自动处理

目标是让本次授权范围内的 Java 文件通过 Spotless/Checkstyle，同时严格保持行为不变。样式处理不是重构授权，也不能顺带修复业务问题。

### 范围选择

- 普通提交：只对本次已授权、准备提交的 `backend/src/main/java/**/*.java` 与 `backend/src/test/java/**/*.java` 运行 scoped Spotless；根据最终 staged 路径构造 `-DspotlessFiles=<backend 相对路径逗号列表>`。不要因全仓历史样式债务扩大提交。
- 用户明确要求“全库 Spotless/Checkstyle”“统一 Java 样式基线”等全库清理时，才可运行 `cd backend && .\mvnw.cmd -DskipTests spotless:apply`，并把所有新增格式化文件作为显式授权范围重新审查。
- 自动处理前记录 `git status --short` 和授权文件列表。若格式化器意外改到范围外文件，停止提交，不暂存这些文件，也不覆盖或丢弃用户改动；报告具体路径后再处理。

### 自动修复白名单

- Spotless 产生的 Google Java Format 排版、尾随空格、文件末尾换行和未使用 import 删除。
- Checkstyle `LineLength`：只换行；无法自动换行的字符串只能做编译期字符串拼接，并逐字保持最终字符串内容。
- Checkstyle import 类问题：只允许删除确认未使用/重复的 import；不要以业务类型替换来绕过规则。
- Checkstyle `ConstantName`：仅可重命名私有 logger 或经全仓引用检查证明不属于公共契约的私有常量，并同步其引用。
- 其它规则，特别是 `ImportControl`、公共符号命名、依赖方向或需要改变表达式/控制流的违规，不属于纯样式自动修复；停止并给出文件、行号、规则名和修复建议。

### 禁止事项与差异审计

- 不修改 Spotless/Checkstyle 配置，不新增 suppression，不调整规则阈值，不借格式化改变 API、DTO、SQL、配置、分支、调用顺序、异常或日志文本。
- 修复后检查 `git diff --check` 和实际 diff。非空白变化只能是白名单中的 import 删除、私有标识符等价重命名或等值字符串拼接；出现其它 token 变化必须停止提交并恢复为行为等价实现。
- 运行 scoped 校验；条件允许时再运行全量：

  ```powershell
  cd backend
  .\mvnw.cmd -DskipTests spotless:check
  .\mvnw.cmd -DskipTests checkstyle:check
  ```

- 全量检查被范围外既有债务阻断时，不自动格式化或暂存无关文件。确保本次文件的 scoped 检查通过，并在 `Not-tested:` 写明全量失败的工具、规则和无关文件。
- 样式处理不替代任务原本需要的测试、构建、真实启动或专项 guard；`Tested:` 必须区分样式门禁与行为证据。

## 失败时

- secret-scan 命中 → 输出泄露行号 + 文件 + 建议（移到 .env / 改用环境变量 / 撤回 git history 的方法）。
- pre-commit hook 失败 → 解读失败原因，给出修复指令；**绝不**用 `--no-verify`。
- main push 的远端总门禁未通过 → 按上文提取远端日志并分类；能证明行为不变的修复最多自动处理 2 轮，其余先询问用户。

## 不要做的事

- 不写英文 commit 消息（违反用户规则）。
- 不主动 push 到远端（用户没说就不 push）。
- 不 `git commit --amend` 已 push 的提交。
