# 001 影响面清单 —— 基线期望

评测对象是 `codebase-researcher` 交付到任务卡 `### Behavior impact` 小节的那份清单。
`product.md` 是片 026 该小节的逐字快照（转录说明见 `input.md`）。
下面每一行都是一条可复算的判据：运行器按「逻辑条目」（bullet 连同其续行）计数，不按物理行计数；
阈值由本文件给定，运行器另钉三条不退让的规矩（阈值 >= 1、五种形状都要被用到、
每条 bullet 至少被一条正向期望钉住），写法与语义见 `.codex/scripts/run_evals.py` 正文。

- must: (code evidence) >= 1
- must: 平台分支 >= 1
- must: 逐字读数 >= 1
- shape: 生产者 & 消费者 & 同级入口 >= 1
- shape: 不适用 & 一行未改 >= 1
- shape: 理由 & 命令 >= 1
- forbid: 待填
- pointers-alive >= 2
- paths-exist >= 3
