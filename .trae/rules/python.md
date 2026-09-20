# Python 服务层（可选）

读取时机：Python 服务层分析、修改或复核前；本栈当前未启用，仅当引入 Python 服务时遵循本规则。

## python-dev 执行与复核职责

- 本栈当前未启用。引入 Python 服务时遵循以下规则。
- Inside Harness, own python/** only when assigned; direct execution follows the root mode decision.
- Expose HTTP/SSE to Java backend; do not face frontend directly.
- Configuration must come from environment/settings, not hardcoded secrets.
- SSE source events must preserve snippet source semantics.