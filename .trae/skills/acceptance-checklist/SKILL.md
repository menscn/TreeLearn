---
name: acceptance-checklist
description: 验收清单。读取 .trae/tasks/<id>.md 的「Acceptance criteria」段逐条勾选，结合 lint / budget / guard 结果给 PASS / FAIL / PARTIAL。verifier 只读不改代码。当用户说「验收」「check acceptance」「verify task T-...」、或 verifier 被派去验收某 task 时触发。
---

# acceptance-checklist —— 任务验收清单

## 角色约束

**verifier 只读不写**：本技能只读取证据并判定，**不得修改任何源码 / 测试 / task 文件**。需要修正时把结论写进验收意见，交由 implementer 处理。

## 何时触发

- verifier 被派去验收某个 task（用户说「验收一下 T-...」「verify」「check acceptance」）。
- 主 Agent 在 commit 落库前的最后守门。

## 第一步：定位并读取 task 文件

```powershell
# 列出任务文件（按任务 ID：T-YYYY-MM-DD-NNN）
Get-ChildItem -Path .\.trae\tasks\ -Filter "T-*.md"
```

打开目标 `.trae/tasks/<id>.md`。

## 第二步：提取 Acceptance criteria 段

在 task 文件里找 `## Acceptance criteria` 段，其中是 `- [ ] <验收点>` 列表。把全部验收点逐条列出。

## 第三步：逐条勾选判定

对每条验收点执行对应证据动作（只跑、不改）后判定：

- **达成** → `[x] PASS`：给出执行到的命令/观察证据（命令 + 退出码 + 期望与实得结果；适用场景指 unit / contract / real-interaction）。
- **未达 / 缺证据** → `[ ] FAIL`：说明缺什么、要补的命令或观察。
- **部分达成或证据不充分** → `[ ] PARTIAL`：列出还差的部分。

所有证据只读取，不写回 task 文件（写回是 orchestrator / implementer 的事）。

## 第四步：并入其它守门结果，给总体判定

| 守门 | 来源 | 结果 |
|---|---|---|
| 键盘：secret-scan | secret-scan 技能 | PASS / FAIL |
| 行数：line-budget | line-budget 技能 | PASS / WARN / FAIL |
| lint：lint-all | lint-all 技能 | PASS / FAIL |
| 验收点逐条 | 本技能 | PASS / FAIL / PARTIAL |

**总体判定合并规则**：
- 任一守门 FAIL → 总体 **FAIL**（附失败项与修复指令）。
- 所有 PASS 但存在 PARTIAL → 总体 **PARTIAL**。
- 全部 PASS → 总体 **PASS**。

## 判定汇总模板

```text
[acceptance-checklist]
task: <id> —— <title>

验收点逐条：
  ✓ (PASS) <验收点1> —— 证据: <命令 + 退出码 + 实得结果>
  ✗ (FAIL) <验收点2> —— 缺: <缺失证据 / 未实现行为>

守门汇总：secret-scan=PASS  line-budget=PASS  lint-all=PASS

总体判定: FAIL  /  PARTIAL  /  PASS

若 FAIL/PARTIAL：给 implementer 的修复指令（只建议，不代改）：
  - <要补的验收点对应改法与验证命令>
```

## 只读铁律

- 不改代码、不改测试、不改 `.trae/tasks/<id>.md` 内容。
- 只输出「判定 + 证据 + 待办建议」，实际改动交回 implementer。