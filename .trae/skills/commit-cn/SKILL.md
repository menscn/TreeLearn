---
name: commit-cn
description: 中文 Conventional Commits 提交流程。强制先跑 secret-scan，再检查 git diff，最后按 feat/fix/refactor/docs/chore/test 写中文消息。铁律：禁止 git commit --amend --no-edit，禁止 -f 强推 main。当用户说「提交 / commit / 打个提交 / 提交一下」，或实施者完成 task 且 verifier PASS 后准备落库时触发。
---

# commit-cn —— 中文 Conventional Commits 提交

## 何时触发

- 用户说「提交」「commit」「打个提交」「提交一下」。
- 主 Agent 完成一个 task 且 verifier PASS 后准备落库。

## 强制流程（不可跳步）

1. **先调 `secret-scan` skill** 扫 staged 改动；命中 → 立即停止，粘贴泄露条目，等处理完再回来。
2. `git status` + `git diff --cached`（Windows）看实际要入库的内容；未 add 的文件提示用户确认是否一并加入。
3. **不可入库的文件类型**（命中即拒绝）：
   - `.env` / `*credentials*` / `*secret*`（除 `.env.example`）
   - `node_modules/`、`dist/`、`build/`、`target/`、`.gradle/` 子文件
   - lockfile 要确认是依赖更新而非误改
4. 起草中文 Conventional Commits 消息（**正文必须中文**）：

   ```
   <type>(<scope>): <一句话中文摘要>

   - <要点 1>
   - <要点 2>

   关联 task: T-YYYY-MM-DD-NNN（如有）
   ```

   `type` 枚举（本技能精简）：`feat` / `fix` / `refactor` / `docs` / `chore` / `test`（需要时可再加 `build` / `perf` / `style`）。
   `scope` 例：`frontend` / `backend` / `android` / `docs` / `harness`。

5. 提交（PowerShell 用单引号包中文消息，避免变量展开）：

   ```powershell
   git commit -m " feat(android): 补首页卡片复用组件

   - 抽卡片为可复用组件并接数据源
   - 关联回归证据记录

   关联 task: T-2026-09-20-001 "
   ```

   > 中文消息里含反引号或 `$` 时，改用 Write 临时文件或 `-F` 参数传入，避免 PowerShell 解释。

6. `git status` 二次确认提交成功、工作树干净。

## 判定

- 全程无密钥命中、无禁用文件、提交成功 → **PASS**。
- 任一步命中 → **FAIL**，停在该步并给出修复指令。

## FAIL 时修复指令

- secret-scan 命中 → 移到 .env / 改用环境变量 / 撤回 history，处理后重跑 secret-scan 再回来。
- pre-commit hook 失败 → 解读报错、给出修复，**绝不**用 `--no-verify`。
- 文件在禁用列表 → 从暂存区移除（`git restore --staged <file>`），必要时补 `.gitignore`。

## 铁律（不可违反）

- **禁止** `git commit --amend --no-edit`（尤指已 push 的提交，禁止覆写）。
- **禁止** `git push -f` / `git push --force` 到 `main`（默认分支）。
- 不主动 push（用户没说就不 push）。
- 不写英文 commit 消息。

## 不要做的事

- 不写英文 commit 消息（违反用户规则）。
- 不 `--no-verify`、不覆盖已有提交、不强推 main。
- 归档/删除 `.trae/tasks/T-*.md` 不在本 skill 流程内，按其它编排规则执行。