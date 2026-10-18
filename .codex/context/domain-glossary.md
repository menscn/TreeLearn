# TreeLearn 领域词表（L2）

> 用途：让角色在不读完整 PRD 的前提下对齐术语。每条都给出权威出处；定义与出处冲突时以出处为准。

| 术语 | 定义（不超过 40 字） | 权威出处 |
| :--- | :--- | :--- |
| 知芽（TreeLearn） | 产品标准名称，中文与英文必须同时出现 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md |
| 主题 Topic | 一棵学习树；显示身份只来自根分支标题 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md |
| 根分支 | 主题的根节点；标题即主题身份，不可归档 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md |
| 分支 Branch | 一条独立连续对话单元；普通追问不增枝 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md |
| 稳定回答 | 生成成功后的不可变回答版本，只有它能被分叉 | Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md |
| 部分回答 | 生成中或失败时已产生的内容，可读但不可分叉 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md |
| 思考过程 | 思考模式下与回答同版本的推理内容，要持久化并进后续上下文 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md |
| 创建快照 | 子分支出生时冻结的不可变上下文清单 | Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md |
| 生成操作 | 一次提交、重试或重置的持久状态，提交前固定提示词版本 | Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md |
| 上下文压缩 | 较早内容转为摘要；压缩失败应暂停而不是丢内容 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md |
| PromptPurpose | 供应商无关的提示词用途契约，派生任务与教学回答隔离 | Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md |
| 非根归档 | 仅非根父分支可归档，整棵子树一起隐藏并可整体恢复 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md |
| 未读状态 | 仅当出现用户未见的 AI 回答时点亮，折叠祖先汇总 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md |
| 创建原子性 | 无可用 Key 时不产生主题与根分支，只暂存当前输入 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md |
| Key 守卫 | Key 缺失、无效或额度不足时拦住发送并引导处理 | Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md |
