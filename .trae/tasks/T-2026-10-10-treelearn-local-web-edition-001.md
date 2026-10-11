# Treelearn Harness Task: 知芽（TreeLearn）本地 Web 版实现（父需求）

id: T-2026-10-10-001
title: 知芽（TreeLearn）本地 Web 版实现
template_version: 2
task_type: requirement
status: in_progress
parent_requirement: null
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Problem/background: 当前生产产品只有 Android；用户需要在 Windows 电脑启动本机服务后通过 localhost 浏览器完成同样的树状学习任务。此前 Android WebView 迁移意图不含浏览器正式端，无法直接满足。
- Users/scenarios: 单人本地使用；桌面键盘/鼠标/触控板；大屏阅读、长回答、树导航、多窗口宽度；不依赖手机或模拟器。
- Expected outcome: `web/` 下 React+TS+Vite 前端与本机 Java 服务；两端数据、附件、Key、偏好分别保存，不建同步/账户/公网/云存储；功能与活动 Android 生产调用链一致并按功能一致性矩阵逐行验收；A「专注阅读」默认，B/C 辅助；薄荷雾面 Web 配色、v1.1 轮廓图标、huashu-design 设计、毛玻璃视觉、icon-only 按钮（需在线检索图标）。
- Core flow and reversibility: 启动本机服务 → localhost → 配置 Provider → 新建主题获取稳定回答 → 选文/整条分叉 → 来源回溯与树总览 → 继续追问。刷新不重发命令；服务重启从本机持久化事实恢复；取消/失败/中断区分且可原位重试。
- Inputs/outputs: 问题、引用、附件、语音识别草稿；输出为回答、推理、来源、分支、要点、批注。时间以 UTC epoch 毫秒存储、按设备时区展示。
- State and exception handling: 可用/加载/无 Key/配置回落/生成中/稳定/失败/中断/取消/服务不可用；每种异常提供可执行下一步，禁止误报保存或生成成功。
- Constraints and trade-offs: 本机服务只绑定 loopback；密钥不进浏览器持久化、SQLite 明文、日志、URL、SSE 或模型可见上下文；共享业务判定唯一实现、禁止拷贝；Android 保持 Java-only，必要调整单独拆任务并完成真实消费者回归；先验证复用与依赖再固定版本；不新增未验证依赖。
- AI recommendation and rationale: 复用 `:core` 与 `:tree-layout`（已核实为纯 JVM `java-library`，Java 17）；共享模块迁入 `web/shared`，Android 与 Web 两端用 projectDir 引用同一份源码；后端采用 Spring Boot + 桌面 SQLite + JDK HttpClient；树图使用共享 tree-layout 坐标自渲染 SVG，避免再引入 React Flow 双份布局判定。
- User choice and custom additions: 用户确认整体方案（2026-10-10 对话）；补充要求：huashu-design 设计、毛玻璃效果、简约美观、全部按钮用图标（需在线检索选型）、目录统一到 `web/`、一次性完成到最终目标。
- AI-autonomous decisions: 端口与数据目录、内部库选型与版本锁定、任务切片粒度、验证顺序、图标库候选。
- User-confirmation decisions: 产品范围与数据边界（INTENT/SPEC 已确认）；桌面永久表准入（Phase 2 执行前呈报确认）；真实 Provider Key 提供（Phase 4 验收前呈报）。
- Acceptance evidence: 各子任务 RED/GREEN、lint、行数预算、真实引擎/浏览器/启动证据、独立 verifier 报告；最终功能矩阵逐行正常/失败/重建证据与交付报告。
- Facts: `android/core`、`android/tree-layout` 为纯 JVM（`java-library`、Java 17）；`android/ai/prompt` 包仅依赖 `core` 与 `java.*`；`android/ai/gateway`、`android/data`、`android/app` 与 Android 平台耦合。本机 JDK 21.0.10、Node 22.22.0、pnpm 11.5.2 可用，无 Maven；仓库 Gradle wrapper 8.14.3。
- Assumptions: Windows 为首发验证环境；Chrome/Edge 为首批正式目标浏览器；Maven Central 与 npm 网络可用（由 T-002 实测）。
- Unknowns: Spring Boot / sqlite-jdbc / JNA / 前端与图标库的精确版本与许可（T-002 实测）；浏览器语音识别能力与网络归属（后续任务探测）。
- Risks: 共享模块迁移影响 Android 构建（以 Android 全量回归兜底，可回退 projectDir 引用）；毛玻璃性能与对比度（真实浏览器实测）；语音无可用适配则如实标未验证。
- Deferred: 导入导出、云备份、人设选择器、新文件格式、自定义 Agent/RAG。
- Non-goals: 两端同步、账户、公网/局域网访问、云数据库、Android UI 全面迁移、新模型原生协议、与当前功能无关的 Agent/RAG 平台能力。
- Skipped non-critical questions and reason: 已确认目标不再重复确认；常规实现选择由 AI 决定；仅桌面表准入与真实 Provider Key 两项按规则集中呈报。

## Goal

交付可在 Windows 电脑经 localhost 使用的知芽 Web 版：功能与活动 Android 实现一致（按功能一致性矩阵逐行验收），两端数据独立、本机持久化，并给出真实启动/停止方式、数据目录、发行产物、实际运行环境与剩余缺口报告。

## Scope

### In scope
- `web/frontend` React+TS+Vite 客户端（A/B/C 布局、i18n 双语、设备时区、icon-only 按钮、毛玻璃视觉）。
- `web/backend` 本机 Java 服务（HTTP/SSE、桌面 SQLite、系统凭据、附件目录、静态托管）。
- `web/shared` 共享纯 Java 构件（domain、tree-layout、ai-protocol）。
- `web/contracts` 唯一 API/SSE 契约与共享 fixtures；`web/scripts` 启动与守卫脚本；`web/README.md`。
- 必要的 Android 调整（共享模块迁移、prompt 包抽取）：单独任务、Java-only、真实回归。
- 文档同步：规格、开发方案、交接入口、根/模块 README、表清单与启动说明。

### Out of scope
- 两端同步/账户/公网/云存储；Android UI 迁移；人设选择器；未核实的新文件格式/协议；与当前功能无关的平台能力。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: Android 运行行为不变（仅共享模块位置与构建引用变化）；Web 为新增独立入口，不影响 Android 数据与界面。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `android/settings.gradle`（共享模块引用）、`android/ai`→`:core` 依赖、Android 各模块消费者；Web 端 `web/backend`、`web/frontend` 为新增消费者。
- Native bridge or independent-client compatibility/update requirements: 不适用（两端独立数据、无桥接）。
- Behavior-neutral non-applicability reason, if applicable: 共享迁移的结构影响在子任务 T-2026-10-10-005 单独记录；本父需求不直接改代码。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新增 `web/` 产品运行时；共享模块位置变化；新增启动入口与数据目录。
- Authoritative code/config/migration evidence: 各子任务产物（`web/**`、`android/settings.gradle`）。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-10-local-web-edition.md`、`Docs/05-technical-design/TreeLearn_Web本地版_详细开发方案_V1.0.md`、AI 交接入口、根 `README.md`/`README_EN.md`、`android/README.md`、`.trae/context/project-brief.md`。
- Documentation files updated: 由子任务 T-2026-10-10-007 及各自任务记录；父需求汇总。
- Producer/consumer/sibling/compatibility paths checked: 由各子任务记录，父需求在验收报告汇总。
- Superseded phrases/examples reverse-searched: 旧方案根级 `frontend/`、`backend/`、`shared/`、`contracts/` 目标路径由 T-2026-10-10-007 反向检索。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 旧 WebView 草案已标记历史快照（2026-10-10 基线提交）；不改写历史材料。

## Acceptance criteria

- [ ] 功能一致性矩阵每一行具备 Android 对照 + Web 运行 + 正常/失败/重建四类证据，或明确标注未验证与原因。
- [ ] 真实 localhost 启动与停止方式、数据目录、发行产物、实际运行环境、剩余缺口写入最终交付报告，结论与运行证据一致。
- [ ] 全部子任务通过独立 verifier；触契约/数据库/Android 的变更分别通过 contract-guardian、db-migration-reviewer、android-no-kotlin-guard。
- [ ] 共享判定唯一实现：两端消费同一份源码，无复制类；Android 回归（构建 + 单测 + Java-only）通过。
- [ ] 预算、TDD、文档影响记录与任务机械校验全部通过。

## TDD execution record

### RED — failing test first
- Test/scenario: 子任务负责 RED/GREEN 记录。
- Command: N/A — parent requirement only.
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: 子任务负责实现记录。
- Command: N/A — parent requirement only.
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 推进顺序：运行边界与共享构件 → 存储和凭据 → 唯一 API/SSE 契约 → 生成与分叉 → Web 核心闭环 → 功能补齐 → 启动发行。
- 子任务滚动创建；每片垂直、可独立验收；触共享区的任务独立执行。
- 端口与数据目录等实现选择由 T-2026-10-10-002 验证后固定，本文不预写版本号。
- 遥测决策：release 不发送远程遥测；本机 debug 日志仅记录 operation/branch 标识、状态、计数、耗时与稳定失败类别，不含正文/Key。

## Verification report

- Verdict: **PARTIAL — 核心闭环 + 分叉 + 设置（Provider/Key/模型分工）+ 分支生命周期与删除 + 搜索/统计端点与前端学习库/统计页已交付并有运行证据；功能矩阵按 2026-10-11 重核为 17/34 行已交付、7 行部分、10 行未实现**。父需求仍未达到「与 Android 全量功能对齐」，不判 PASS。
- Evidence: 交付报告 [`Docs/05-technical-design/TreeLearn_Web本地版_交付报告_2026-10-10.md`](../../../Docs/05-technical-design/TreeLearn_Web本地版_交付报告_2026-10-10.md)（2026-10-11 收口修订：矩阵逐行重核、计数更正、缺口清单）；`artifacts/evidence/` 下逐任务证据（本批新增 `T-2026-10-10-042/043/044-*`）；`python .trae/scripts/validate_tasks.py` → `files=99 errors=0 warnings=29`；后端全量 `:check`（278 例 + checkstyle）exit 0；前端 173/173 + lint/build exit 0。
- 已交付并独立验收的子任务：T-002（可行性）、T-003（后端骨架）、T-004（前端骨架）、T-005（共享迁移）、T-006（设计系统）、T-007（文档同步）、T-009（桌面 schema/迁移）、T-010（对话树 Store）、T-011（操作/快照/派生 + 中断落定）、T-012（配置 Store）、T-013（Windows 凭据）、T-014（唯一契约 + 102 fixtures）、T-015（TS 契约客户端）、T-017（ai-protocol 抽取）、T-021（桌面网关适配 + 20 协议 fixtures）、T-023（上下文/规划/总线）、T-024（协调器 + 稳定化）、T-025（后端 DTO 层）、T-026（SSE 端点）、T-028（核心闭环 REST）、T-029（发行打包）、T-030（外壳 + 主页）、T-031（工作区 A/B + SSE）、T-033（视觉收口）、T-034（model-resolution）、T-035（命令持久幂等）；T-036/037/039/040/041（设置/分叉/契约收口，五项独立 verifier PASS）。
- 本批收口（2026-10-11，实现者自检 PASS、独立 verifier 待排）：T-042（分支生命周期与删除，13 例真实 HTTP+SQLite）、T-043（搜索/统计端点，14 例）、T-044（前端学习库/统计页，173/173 + 守卫误报修复）。
- 专项守卫：contract-guardian 多次发现并推动修复（`kind` 大小写、SSE 终态补帧、`active` 端点 200+null、§4 幂等落点、`endpoint` 兜底登记）；db-migration-reviewer 判定桌面 schema 与 Android V5 语义「无多无少无差异」；android-no-kotlin-guard 全程 PASS。
- Changed/deleted assertion rationale and equivalent behavior coverage: 各子任务记录；无削弱（`listForTopic` 顺序、`markTerminal` STABLE、`updateProgress` 终态等变更均按 Android/领域语义修正并注释理由）；本批测试仅修编译级辅助与选择器，断言语义零改动。
- Gaps: 见交付报告 §7——未实现 10 项（树图 C 视图、来源定位跳转、上下文压缩、推荐问题、高亮/批注及失效、联网搜索、翻译/朗读、语音、附件、文件管理、reset）；未验证项含**真实 Provider 生成**（未提供 Web Key）、多标签页竞争、浏览器重连、Android fixtures 等价执行。
- Risks: 规模越线三项（T-010/T-024/T-028）待用户裁定；TDD 回溯式补录两项已留痕；桌面表准入为「按推荐执行」性质；**仓库 `.git` 损坏（2026-10-11 发现）**——分支指针与全部提交对象丢失、工作树与暂存区完好、远端不可达，恢复前无法提交。

## Handoff notes

- Completed: 设计输入落库（d2c10c1）→ Phase 1（可行性/迁移/骨架/设计系统/文档）→ Phase 2（存储与凭据）→ Phase 3（唯一契约 + 双端编解码）→ Phase 4（共享协议抽取、桌面网关、上下文/规划/总线、协调器/稳定化、命令持久幂等）→ Phase 5（核心闭环 REST + SSE + 前端外壳/主页/工作区/Markdown 渲染）→ Phase 7（发行打包 + 真实浏览器四轮复验）→ 设置/分叉批（T-036/037/039/040/041）→ 2026-10-11 收口批（T-042/043/044）。历史提交最新 77ee6d0（现已丢失，见 Blocker）。
- 运行事实（实测）：`start-web.ps1` → `http://127.0.0.1:8733` 同源使用；数据目录 `%LOCALAPPDATA%\TreeLearn-Web`；`user_version=1`；jar 32.7 MiB + frontend dist；毛玻璃 computed `blur(18px)/blur(24px)`；中英双语；icon-only 按钮（含 aria-label）。
- In progress: 无——T-042/043/044 已收口（任务文件回填、`validate_tasks.py` errors=0、README/交付报告重核）。
- Blocker: **仓库 `.git` 损坏（2026-10-11）**：`refs/heads/master` 与全部提交对象丢失（`.git/logs/HEAD` 末记录 6d669c2「feat(web): 设置端点、分叉端点与前端设置页」），`git cat-file` 确认对象库 0 个提交；远端 `github.com/menscn/TreeLearn` 404（不存在或私有），沙箱代理 502 + 直连超时双路断。工作树、暂存区（全部文件已 `git add` 为 blob）与全部测试证据完好。**解除条件：用户提供本地备份克隆或恢复远端可达**；在那之前任何提交都不可能，代码与测试不受影响。
- Unblock condition: ① git 仓库恢复 → 补提交；② 用户提供 Web 端 API Key → 补真实生成端到端验收；③ 用户对规模项裁定 → 关闭流程偏差。
- Next steps: ① **git 仓库恢复（最优先，阻塞一切提交）** ② 独立 verifier 终验 T-042/043/044 ③ 浏览器视觉复核（学习库/统计页）④ 真实 Provider Key 端到端生成 ⑤ 后续功能波次：树图 C 视图（依赖树布局端点）→ 已读/阅读位置前端消费、来源定位、选文引用、高亮批注 → 附件与文件管理、推荐问题、上下文压缩、翻译、朗读、语音 → 多标签页竞争/浏览器重连/Android fixtures 等价验证。
- Watch-outs:
  - `web/README.md` 的「已知缺口」行、矩阵与契约应随每个后续任务同步（文档一致性守卫）。
  - 服务只绑定 loopback；数据与 Android 完全独立，无同步。
  - 前缀属性书写顺序（前缀在前、标准在后）——曾导致毛玻璃静默失效，已有顺序契约测试钉住。
  - 共享 Gradle 树并发构建：带 `--init-script`（隔离 build dir）+ `--project-cache-dir` + `--no-build-cache`。
  - 同一文件多处修改并行 Edit 会静默覆盖（本轮实测三次）：必须串行且每改必回读。

- 历史 Handoff（Phase 1~2 期间的中间记录，已由上方当前态取代，保留以便追溯）：Phase 1 六子任务全部完成并落库（T-002 2460c50、T-005 46479c6、T-003 d870bb7，T-004/T-006/T-007 随后落库）；三项集中呈报按「用户指示下按推荐执行」处置（行数口径=守卫/工具脚本不计业务行；T-006 TDD 偏差如实记录；桌面 12 张表准入按推荐继续，记录于准入文档 §4）；`ChatGenerationStageTest` 既有失败由 T-2026-10-10-008 跟进（未阻塞）。