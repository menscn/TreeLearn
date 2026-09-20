# Treelearn · AI 开发流程体系（Harness 工程）

> 本目录是从 `knowledge_space` 复刻并适配的 AI 协作开发流程，解决"为什么不让一个 AI 直接干到底"的问题。

## 一句话：装修队
- **业主**（你）：提需求。
- **工头（主 Agent）**：听懂需求，决定要不要拆活、要不要分包。
- **设计师（requirement-decomposer）**：把大需求拆成一张张小工单（Task），每张 ≤ 400 行、一个行为。
- **工人（各栈 dev）**：前端 / 后端 / Android 各管一栈，互不越界。
- **质检员（verifier）**：每张工单做完，由**另一个独立的 AI** 跑 lint、数行数、逐条核对验收点。
- **督察（guard）**：改契约、改 SQL、改 Android 这种共享设施，先过只读 guard。
- **技能（skills）**：干一件具体小事的工具（提交流、扫密钥、lint、数行数）。

## 为什么这么麻烦（三个坑）
1. **会忘事**：context 有上限，塞多了出错（context anxiety）。
2. **会越界**：改前端顺手把数据库也改了，没人 review 就炸。
3. **会说谎**：AI 说自己干完了其实没，缺客观质检。

解法：**每个 AI 只在自己的小窗口干一件小事，做完必须由另一个独立 AI 检查。**

## 关键机制
- **机械化优于 prompt**：能用 lint 拦依赖方向、用 secret-scan 拦密钥、用脚本数行数，就不只写在文档里。
- **独立验收**：verifier 只读、不边检边改，报告由主会话落盘到 Task 文件。
- **状态文件即记忆**：进度、决策、验收都写进 `.trae/tasks/<id>.md`，新会话从干净 context 读它续上。
- **失败案例库**：犯过的错记到根 `AGENTS.md` 末尾，下次一开局就提醒。
- **500 行硬预算**：单 task 业务 diff > 500 行即失败。

## 怎么用
1. 读根 [AGENTS.md](../AGENTS.md)（入口 + 规则路由表）。
2. 小改 → 直接做；大改/跨栈/模糊 → 问我是否进 Harness，或用触发词（`harness-run` / `工头模式`）显式选择。
3. 进 Harness → 读 [HARNESS.md](HARNESS.md)。

## 目录结构
```
.trae/
├── README.md              # 本文件
├── HARNESS.md             # 用户选择 Harness 后的编排入口
├── rules/                 # 专项规则正文（唯一权威）
├── tasks/                 # Task 状态文件（Task v2）+ _TEMPLATE
└── skills/                # 项目技能（机械化守门/原子动作）
```