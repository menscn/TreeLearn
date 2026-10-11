# Treelearn Harness Task: 批注前端消费 + 真实 Key E2E

id: T-2026-10-10-049
title: 批注前端消费（§6.10 划选/渲染/列表）+ 真实 Provider Key 端到端验证
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 355
shared_code_touched: false
depends_on: [T-2026-10-10-annotation-endpoints-047.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-10-001` 第二波收尾——批注消费闭环 + 解除「真实 Provider Key 端到端生成」外部阻塞。
- Problem/background: T-047 批注端点已就绪但前端无任何消费；`Markdown` 只支持单高亮区间（并排视图用）；真实 Key 验证缺专用 Key（用户本轮提供）。
- Users/scenarios: 学习者在 AI 答案上划选文字 → 浮条「高亮/批注/取消」→ 高亮即渲染 `<mark>`，批注经弹窗输入正文保存；批注列表随消息显示可删除；重生成后失效批注由后端 digest 清理（T-047 语义）。
- Expected outcome: `Markdown` 支持多高亮数组与 `data-md-start/end` 原文偏移标注；`MessageView` 的 `annotationsEnabled` 模式加载 §6.10 批注、渲染 PROSE 高亮、划选→`selectionToRange` 映射原文偏移→POST、NOTE 弹窗、列表与 DELETE；对话页接线启用。
- Core flow and reversibility: 纯前端新增（`lib/selectionRange.ts`、MessageView 扩展、Markdown 扩展、store 三函数、i18n 10 key）；`annotationsEnabled` 默认关闭，并排视图等既有用法零改动，可回退。
- Inputs/outputs: 输入=划选 DOM Selection；输出=§6.10 请求（PROSE 容器、原文 UTF-16 半开区间、proseIndex=块索引）与渲染态（mark/列表）。
- State and exception handling: 批注加载失败静默为空（不阻塞消息渲染）；NOTE 写入失败保留弹窗内容可重试；删除乐观等待响应后移除。
- Constraints and trade-offs: i18n 守卫实测再拦 3 处（全角引号 `「」` 与行内 `/* 中文 */`）——引文改 `<q>` 语义标签（引号交给 CSS quotes）、注释改 `//` 独立行；CELL 容器划选本轮未做（表格内划选留待后续，后端已支持）。
- AI recommendation and rationale: `parseMarkdown` 的 TextNode 本就携带原文偏移，DOM→原文映射零额外解析；高亮与偏移标注共用同一切分路径，保证渲染与选区一致。
- User choice and custom additions: 用户指令「与下一波合并成一次复核」+ 提供真实 Key。
- AI-autonomous decisions: 浮条采用 inline 工具条（非绝对定位浮层），可访问性用 role=toolbar；NOTE 正文经既有 `Dialog` 组件；测试改用容器内定向查询（mark 与列表 `<q>` 引文并存同文是预期行为，`screen.findByText` 会双匹配）。
- User-confirmation decisions: 无。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-049-{test,guard,lint,build}.txt`、`artifacts/evidence/T-2026-10-10-e2e-realkey.txt`。
- Facts: 契约 §6.10 字段（container=PROSE/CELL、start/end 半开 UTF-16）；`AnnotationRequest` 服务端校验（ANCHOR_INVALID/NOTE_BODY_REQUIRED）；`Markdown` 既有 `highlight` prop（并排视图分叉锚点）。
- Assumptions: proseIndex 取 `.md` 直接子块索引（服务端存档但不参与区间校验）。
- Unknowns: 真实 Key 的供应商归属按既有 Provider（DeepSeek, baseUrl=api.deepseek.com）验证成功，Key 本体不入库不入证据（脱敏）。
- Risks: CELL 内划选未做；`selectionToRange` 依赖 `Range.compareBoundaryPoints`（jsdom 与主流浏览器均支持）。
- Deferred: 表格单元格内划选批注；浮条跟随选区定位；批注高亮的悬浮预览。
- Non-goals: 不改后端/契约/Android；不改 design tokens。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

对话页 AI 答案支持完整批注消费：划选→高亮/批注/删除全链路（§6.10），前端四项守门全绿；并用用户提供的真实 Key 完成 Web 端端到端生成验证（配置→模型刷新→首问→SSE 流→权威读回），解除父任务最大未验证项。

## Scope

### In scope
- `web/frontend/src/lib/selectionRange.ts`（新）：`selectionToRange`/`proseIndexForRange`。
- `web/frontend/src/components/markdown/Markdown.tsx`：`highlights[]` 数组、`withOffsets` 偏移标注（旧 `highlight` prop 兼容）。
- `web/frontend/src/components/MessageView.tsx`：`annotationsEnabled` 模式（加载/浮条/NOTE 弹窗/列表/删除）。
- `web/frontend/src/api/types.ts` + `store/topicData.ts`：Annotation 类型与三函数。
- `web/frontend/src/i18n/{zh-CN,en-US}.ts`：`annotation.*` 10 key。
- `web/frontend/src/pages/BranchWorkspace.tsx`：对话页接线 `annotationsEnabled`。
- `web/frontend/src/components/MessageView.annotations.test.tsx`（新，5 例）。
- 真实 Key E2E 验证（运行时操作，证据落盘；Key 脱敏，不写入任何仓库文件）。

### Out of scope
- CELL 容器划选；浮条跟随选区定位；批注悬浮预览；后端/契约改动。

## Acceptance criteria
- [x] 划选 AI 答案 → 操作条（高亮/批注/取消）出现，操作条可访问（role=toolbar + aria-label）。
- [x] 高亮写入后原文区间渲染 `<mark>`；批注列表引文与 NOTE 正文显示、可删除。
- [x] NOTE 经弹窗提交（正文非空）；POST/DELETE 请求带 `commandId`。
- [x] i18n 双语 key 成对；i18n 守卫、lint、build 全过。
- [x] 前端全量 180/180（新增 5 例 + 既有 175 例零回归）。
- [x] 真实 Key E2E：写 Key→模型刷新（真实供应商调用 added=1）→分工解析 NONE→首问→SSE `stable` 终态（model=deepseek-v4-pro）→权威读回持久化，证据 `artifacts/evidence/T-2026-10-10-e2e-realkey.txt`（Key 脱敏）。

## TDD execution record

### RED — failing test first
- Test/scenario: `MessageView.annotations.test.tsx` 5 例（PROSE mark 渲染 / 划选映射 / HIGHLIGHT POST / NOTE 弹窗提交 / DELETE）。
- Command: `cd web/frontend && pnpm test`
- Exit code: 1
- Observed failure: 首轮 2 failed / 3 passed——划选浮条未出现（fetch stub 未匹配带 `?currentBranchId` 之外的 URL 无关，此处为 stub 未路由 POST 时组件抛错）与 stub 未路由 annotations GET；另 build 期 TS2345（container 可空）。
- Failure reason validated: `artifacts/evidence/T-2026-10-10-049-test.txt`（全量轮次含历史失败痕迹；单文件轮 `Tests  1 failed | 4 passed (5)` 与 `2 failed | 3 passed (5)` 会话内实录）。

### GREEN — minimum implementation
- Implementation: `selectionRange.ts` + `Markdown` 扩展 + `MessageView` 扩展 + store/types/i18n/接线。
- Command: `cd web/frontend && pnpm test`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-049-test.txt`（`Test Files 23 passed (23)`、`Tests 180 passed (180)`、`TEST-EXIT=0`）。
- Regression evidence: 同文件（全量 23 文件含既有 175 例零改动全绿）。

### REFACTOR — optional
- Cleanup: i18n 守卫拦截 3 处（`「」` 全角引号、2 处行内 `/* 中文 */`）→ 引文改 `<q>`（引号交 CSS）、注释改 `//` 独立行；TS2345 container 判空收窄。
- Regression evidence: `artifacts/evidence/T-2026-10-10-049-{guard,lint,build}.txt` 全 exit 0。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行 ≈355，超软上限 200、未超 500 硬上限；仅软预算警告）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- **`<q>` 与 `<mark>` 同文并存**：高亮后原文同时出现在答案 mark 与列表引文 q 中——`screen.findByText` 会双匹配；组件测试一律在 `.message__answer` 容器内定向查询 mark。
- **`selectionToRange`**：用 `Range.compareBoundaryPoints` 判相交、按文本节点局部偏移换算绝对偏移；选区起止在 span 边界时（Selection 容器为父元素）自动按节点全长处理。
- **i18n 守卫规则补充**：`//` 独立行注释放行，`/* */` 行内注释与字符串字面量中的 CJK 拦截；全角标点属 CJK 区间。
- **真实 Key E2E 环境坑**：会话注入 `SERVER__PORT=59338`（Spring relaxed binding 生效）且该端口被无关进程占用——bootRun 须显式 `export SERVER__PORT=8733`；curl 必须 `--noproxy "*"`（会话代理 502）。
- Key 只写入 Windows 凭据管理器（`Treelearn-Web/provider/<id>`）与运行时内存；不写入仓库任何文件，证据全部脱敏。

## Verification report

- Verdict: PASS（实现者自检全绿；独立 verifier 合并复核随本轮进行）
- Independent verifier re-check (2026-10-11, READONLY subagent, evidence-free re-runs, merged pass covering T-045..049): PASS——后端 :check 复跑 exit 0（284 PASSED / 0 FAILED + checkstyle 0 error）、前端 test 180/180 + lint + 守卫自测/扫描 + build 全 exit 0、validate_tasks.py errors=0（warnings=33 软预算）；证据交叉核对一致（045 red 11 败→green 11/11、047 red 8/9→green 9/9、048-red 回退实录 exit 1）；代码抽查（树布局装配/批注判定/划选映射/Markdown 兼容）逐条符合；E2E 证据脱敏确认（无 Key 明文）。非阻塞发现：046-guard 过程证据含已修复的旧拦截；1 例 waitFor 偶发超时复跑过（Gradle 并行抢 CPU）；CELL 划选/手势缩放/浏览器视觉复核为遗留项。
- Evidence: `artifacts/evidence/T-2026-10-10-049-{test,guard,lint,build}.txt` 全 exit 0；`artifacts/evidence/T-2026-10-10-e2e-realkey.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除既有断言；`Markdown` 旧 `highlight` prop 保持兼容（SideBySideView 5 例零改动全绿）。
- Gaps: CELL 划选、浮条跟随定位未做（Deferred）；多标签页并发批注竞争未验证。
- Risks: C 树图/批注视觉未经真实浏览器截图复核。

## Handoff notes

- Completed: 批注消费全链路 + 真实 Key E2E 验证 + 四项守门。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: 合并 verifier 终验 045–049；第三波（来源定位跳转、选文引用、推荐问题、附件等）。
- Watch-outs: `q` 元素引号由 CSS 渲染（样式表需有 `annotation-list__quote` 规则才显示引号）；`SERVER__PORT` 会话注入影响任何本地 bootRun/服务类启动。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 对话页 AI 答案可划选批注/高亮/删除（用户可见）；真实 Key 端到端生成已验证（记录在案）。
- Authoritative code/config/migration evidence: `web/frontend/src/components/MessageView.tsx`、`.../lib/selectionRange.ts`、`.../markdown/Markdown.tsx`、`.../store/topicData.ts`。
- Documentation surfaces checked: 契约 §6.10（T-047 已落稿，前端消费不新增契约面）；`web/README.md` 状态节。
- Documentation files updated: README 与交付报告已按本轮交付同步。
- Producer/consumer/sibling/compatibility paths checked: `Markdown.highlight` 旧消费方（SideBySideView）零改动兼容（测试全绿佐证）；`annotationsEnabled` 默认关闭不影响既有调用。
- Superseded phrases/examples reverse-searched: 无（能力此前不存在）。
