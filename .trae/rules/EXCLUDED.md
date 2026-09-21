# 排除说明（EXCLUDED）

从源工程 knowledge_space（`.codex/rules/`）迁移到 Treelearn（`.trae/rules/`）时，以下规则被明确排除，未写入目标 `.trae/rules/`。各方如需在 Treelearn 中启用对应能力，需另行按 Treelearn 技术栈补写规则。

## 一、绑定特定外部服务/流程而整体排除

- `hclaw.md`：绑定特定 AI 服务（HClaw）。Treelearn 不依赖该服务，规则预案不适用，故整体排除。若项目后续确有该服务相关约定，应作为项目私有契约重新定义，而非照搬。
- `cspfix-sync.md`：绑定源工程 CSPfix 分支的归档/同步流程。该流程与 Treelearn 的分支与发布策略无关，整体排除。

## 二、源工程已有但 Treelearn 曾无对应技术而排除、后续恢复或并入

- `sql.md`、`contracts.md`：迁移初期曾因 Treelearn 无对应一级规范而排除；现已按 Treelearn 的后端 MySQL/Flyway、Android SQLite/SQLiteOpenHelper 和跨端契约边界恢复为独立规则。当前权威正文分别为 [`sql.md`](sql.md) 与 [`contracts.md`](contracts.md)，本文件仅保留历史迁移说明。
- 源工程 `java.md` 中「Java/Core 数据库表级读写所有权」「Python Core 层所有权」相关条目：Treelearn 无 `core-py` 处理层，无此表级所有权分区模型，故剥离（相应职责收敛回 backend 自身）。
- 源工程 `java.md` 中「Elasticsearch 向量索引由 Python 独写」条目：Treelearn 无 Python 服务、未启用 Es 向量索引，故剥离。

## 三、领域特化内容（不在规则正文，随文本适配时删除）

以下属于源工程 AI/知识产品领域的特化描述，Treelearn 无对应业务，适配各栈规则时已从正文删除（不单独成文）：

- 前端双模式（书架 AI 导学模式 / 阅读模式）一致性要求。
- `com.knowledgespace` 包名、`v2 会话 / Keystore / Capacitor` 网页镜像与包装相关条目。
- `core-py` / `backend-java` / `admin-frontend` 等源工程目录名（均已映射到 Treelearn 的 `frontend/`、`backend/`、`android/`）。

## 四、源工程存在、Treelearn 未发现对应内容

- 除上述外，源工程 `frontend.md` 中的回归证据链、SSE 统一客户端、设备时区等通用要求已在 `frontend.md` 保留；无其他需排除项。
