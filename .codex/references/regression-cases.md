# 防回归历史案例

历史参考与学习材料，不是新增门禁或当前运行契约；现行要求见[防回归规则](../rules/regression.md)和[资源安全规则](../rules/resource-safety.md)。按本次风险读取相关案例，历史结果不替代当前验证。

| Case | Required evidence and decision |
|---|---|
| Frontend emits five resource URLs while Android requires six (`f782d218`) | Compare producer output with native validation and exercise configuration failure. Known mismatch is FAIL even when frontend tests pass. |
| Shared `QUOTA_EXCEEDED` mapped entirely to document count (`5a5fd0c6`) | Exercise both storage and count failures through classification and user-visible handling. Misclassification is FAIL; a missing counterexample leaves verification incomplete. |
| Old citation navigation removed together with scrolling (`79bb0a47`, repaired by `cf3be347`) | Retain target visibility and verify offscreen citation switching in a real message viewport. Missing scrolling is FAIL; source-only checks cannot establish behavior. |
| Behavior-neutral copy or formatting | Record why behavior is unchanged and run applicable existing gates, such as i18n. No unrelated interaction suite or Harness enrollment is required. |
| Two entrypoints share the same controller | Reuse controller tests plus evidence that both entrypoints reach it with correct parameters. A mode name or dataset-only test is not that evidence. |

## 引用转换中的内存放大（2026-10-09）

### 问题与原理

保护引用：`505f1a9ffa3460587049b87cf05e434b64099c5a:完整引用的峰值内存`；对照基线为 `1e21dfac5b154fc19169dd09939bfd50e6d4c946`。修复消息和旧实现可用 `git show` 核对，测量见[Worker 故障恢复验证](../../Docs/04-报告/Worker故障恢复验证.md#引用完整性与压力对照)。这是历史复盘，不证明当前代码或任意部署规模已经通过。

旧引用转换在修复标题、公式和表格时反复生成完整段列表，索引缓存又强持有历次列表与索引。局部变量虽然替换为新列表，旧对象仍可从缓存到达，不能释放。若 N 个段经历 K 次编辑，保留这些副本可能使额外工作集接近 O(N × K)；小文档的段数、编辑数都少，文本结果正确也容易掩盖放大效应。

修复将可批量处理的编辑合并，复用当前索引并及时释放旧版本；交叠编辑沿用原顺序。理解修复可从[引用转换](../../core-py/src/core_py/parse/citation_transforms.py)、[来源映射](../../core-py/src/core_py/parse/citation_provenance.py)和[索引缓存](../../core-py/src/core_py/parse/citation_index.py)入手，并对照 Git 基线。完整产物本身仍可能随数据量增长，优化目标是减少重复中间对象，同时保留既有引用语义。

### 历史测量与结果完整性

2026-10-09 的固定合成样本按 688 页构造标题、公式、表格及来源区域；基线和优化后转换分别在独立子进程运行。测量使用 `tracemalloc` 与整进程峰值 RSS，完整规范化结果使用 SHA-256 对照。原报告该压力段未单列操作系统、Python 版本或容器内存配额，不能据此推断目标部署容量；报告中的 Windows 连续上传和 Linux Worker 强杀实验是不同证据。

| 指标 | 基线 | 修复后 |
|---|---:|---:|
| 峰值 Python 分配 | 1,047,134,412 B | 2,835,381 B |
| 整进程峰值 RSS | 2,488,438,784 B | 88,244,224 B |
| 引用阶段耗时 | 62.6531 秒 | 0.1585 秒 |
| 完整规范化结果摘要 | 与修复后相同 | 与基线相同 |

资源测量证明该固定样本的分配和耗时下降；完整结果对照验证该样本的文本、偏移、页码、区域和来源信息一致。它不是生产原文件的 DocForge 端到端测试，也不证明任意并发或容器限额下不会 OOM。生产日志中的 signal 9 仍需内核或 cgroup 证据确认具体原因。

### 正反例

- 不足：只验证几页文档；采用生成器后仍将所有产物放入列表；依赖变量重新赋值或强制 GC，却未检查缓存持有者；用截断引用换取内存下降。
- 合适：说明必要产物和额外副本的生命周期，用有代表性的段数与编辑数检验放大因素，按实际并发测量资源，并核对完整结果和交叠编辑等相邻场景。
- 随改动讲解示例：“这次保留必要的完整引用产物，只让缓存持有当前索引，减少旧列表继续存活造成的放大。大样本资源测量检查峰值，完整结果对照检查引用没有丢失；这两项仍不能替代目标容器配置下的并发容量验证。”

### 验证入口与证据边界

在 `core-py/` 工作目录核对现有[定向测试](../../core-py/tests/test_citation_provenance.py)、[兼容测试](../../core-py/tests/test_citation_compatibility.py)、[大文档测试](../../core-py/tests/test_citation_large_documents.py)和[压力脚本](../../core-py/scripts/benchmark-worker-citations.py)，再按本次影响选择执行：

```powershell
uv run pytest tests/test_citation_provenance.py tests/test_citation_compatibility.py tests/test_citation_large_documents.py -q
uv run python scripts/benchmark-worker-citations.py --baseline 1e21dfac5b154fc19169dd09939bfd50e6d4c946 --pages 128
uv run python scripts/benchmark-worker-citations.py --baseline 1e21dfac5b154fc19169dd09939bfd50e6d4c946 --pages 688
```

运行前确认基线可解析、现有依赖可用，并按基线内存消耗安排隔离的验证资源。脚本构造标题/公式/表格，在两个子进程比较结果摘要，并断言 Python 分配下降至少 80%、耗时下降至少 50%；这是该回归基线的既有阈值，不是全项目资源标准。脚本记录 RSS，但不自动验收目标容器限额、并发任务或连续处理的容量。

这些命令是后续命中该保护时的复核入口；本次规则材料修改没有重跑业务压力测量。执行后另记当前版本、环境、配置和结果，必要的并发或释放场景沿资源安全正文补充，不能复用本节历史数字宣称通过。

## 按风险读取与验收示例

以下用于理解规则触发和证据层级，实际判定以权威正文和本次调用链为准。

| 改动 | 分析与读取 | 合适的验证或不适用理由 |
|---|---|---|
| 按钮文案更清晰，handler 与请求不变 | 防回归与适用文案规则；说明运行行为不变 | 原有文案检查；资源分析可说明不适用，不制造大文档测试 |
| 引用转换只修改一个缓存函数 | 防回归与资源安全；追踪大文档调用方及对象持有者 | 大规模资源测量、完整引用对照和适用交叠场景；小样本不足以证明资源安全 |
| 调整缓存条目或淘汰方式 | 资源安全与共享状态；区分派生缓存和权威状态 | 大小/数量/生命周期及适用重复执行或并发，失效后正确性；TTL 不能单独证明容量有界 |
| 批处理增加并发 | 资源安全；含共享调度或恢复时叠加共享状态 | 同时执行和排队对象的实际持有量、目标并发峰值、失败/取消释放；信号量不能证明全部预创建对象有界 |
