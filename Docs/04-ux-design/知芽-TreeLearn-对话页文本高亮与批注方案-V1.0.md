# 知芽（TreeLearn）对话页「文本高亮 + 批注」方案 V1.1

> 状态：**已确认 —— 8 项拍板全部落定（2026-09-28），配套交互 Demo 已验收（33/33 断言通过，零页面异常）**
> 交互 Demo：`Docs/04-ux-design/知芽-TreeLearn-高亮批注交互Demo-V1.1.html`（双击即开；huashu-design 技能产出，CDP 验证脚本 `artifacts/tmp/verify_hldemo.mjs`）
> 日期：2026-09-28（V1.0 同日；V1.1 按用户 15:07 拍板修订；15:23 全部确认并补「菜单格禁文字按钮」约束）
> 范围：对话页 AI 回答正文（`chat_answer_body` 内的 prose 段与**表格单元格**），不含思维链区、译文区、用户消息
> 上游：PRD 6.2.19（文本选择菜单）、6.2.20（动作行）；`Docs/04-ux-design/知芽-TreeLearn-对话页1比1复刻方案-V1.0.md`

## 变更记录

| 版本 | 变更 |
|---|---|
| V1.0 | 初稿，8 项待拍板 |
| V1.1 | 用户拍板三条：① **重生成后高亮、便签全部删除**（放弃锚点重定位，改 digest 清除）② **严格重合才算**（重复批注与「取消高亮」均要求区间精确相等）③ **支持表格单元格内高亮**。据此重写 §2.4/§3/§4，新增 §2.6，风险与验收相应更新 |
| V1.1（15:23 确认） | 余下 4 项（C/D/F/G）按建议确认执行；**新增硬约束：选择菜单格一律图标承载语义（24dp 网格 / 1.8 描边 / 圆角端点，与 `ic_action_*` 同族），不得使用文字按钮**；「取消高亮」为图标态切换（记号笔 → 带斜杠记号笔）。交互 Demo 产出并验收通过 |

---

## 0. 一页速览

| 项 | 结论 |
|---|---|
| 菜单格位 | 现有 4 格（问 AI / 新建主题 / 复制 / 全选）→ **6 格**，插入「高亮」「批注」 |
| 适用范围 | prose 段 + **表格单元格**（V1.1 起支持；单元格长按即起选区，同一套菜单） |
| 高亮视觉 | 黄色背景 `BackgroundColorSpan`，叠在 Markdown 样式层之上，不动字号字色 |
| 高亮二次点击 | **区间精确重合** → 自动恢复选区 + 弹菜单，首格变「取消高亮」；部分重叠 → 按新建处理并**合并区间** |
| 批注交互 | 点「批注」→ 弹输入框 → 关闭后选区右上角锚便签图标；**区间精确重合**才判重复 |
| 便签堆叠 | 末尾位置多个便签 → 点击弹「横线椭圆」卡片，纵向列出全部 |
| 重生成 | **回答内容一变，该消息的高亮与批注全部清除**（content-digest 失配清除，流式期间不比对） |
| 存储 | 单表 `message_annotations`，`container` 区分 PROSE/CELL，DB 版本 4 → 5 |
| 任务拆分 | 4 个 Task（T-A 数据层 / T-B 菜单+高亮 / T-C 单元格基建 / T-D 批注），估 370–460 行/个 |

---

## 1. 现有实现（落点，均已核实）

| 关注点 | 文件 | 关键事实 |
|---|---|---|
| 选择菜单回调 | `android/app/src/main/java/com/treelearn/app/screen/ChatScreen.java:1170` | `Row.SelectionMenuCallback implements ActionMode.Callback`，`onCreateActionMode` 里清空平台工具栏并 `showWordMenu()` |
| 菜单项规则 | `.../app/ui/WordMenu.java:33` | `Item` 枚举（QUOTE/FORK/COPY/SELECT_ALL）+ `entriesFor()` |
| 菜单绘制 | `.../app/screen/WordMenuPopup.java` | 只管测量与落位；构造入参就是「作用的那个 TextView」——**对单元格天然通用** |
| 菜单布局 | `res/layout/popup_word_action.xml` | 4 个等宽 `FrameLayout`（`@dimen/word_menu_cell` × `@dimen/touch_min`），`WordMenu.leftFor/topFor` 夹取算法无需改 |
| 选区读取 | `ChatScreen.java:1230` `selectingRun()` / `:1291` `selectedText()` | 遍历 `answerViews` 找带选区的那一个 —— **必须扩展为也扫描单元格**（工作区记忆红线） |
| 回答渲染 | `ChatScreen.java:1048` `renderAnswerBody()` | `renderSegments()` → prose 段是 `TextView`；`:1097` `inflateTable()` → 单元格是**普通 TextView，未开 `textIsSelectable`** |
| 表格布局 | `res/layout/item_chat_table.xml` | `HorizontalScrollView > TableLayout` 直挂；**插覆盖层需在中间加一层 `FrameLayout`** |
| 布局测试 | `android/app/src/test/.../ui/ChatMessageRowLayoutTest.java` | 引用 `item_chat_table` —— 结构变更必须同步该测试 |
| 单卡片仲裁 | `ChatScreen.java:826` `showWordMenu()` / `:838` `dismissPopups()` | 全页同时只允许一张卡片；新弹窗必须在此纳管 |
| DB schema | `android/data/.../db/TreeLearnSchema.java` | `DATABASE_VERSION = 4`；11 张表；`FORWARD_MIGRATIONS` 至 V3→V4；DDL 只许出现在 `onCreate`/`onUpgrade` |
| SDK | `android/app/build.gradle` | minSdk 28 / targetSdk 36 —— 无 API 兼容障碍 |

---

## 2. 交互设计

### 2.1 菜单从 4 格变 6 格

```
┌────────────────────────────────────────────────────┐
│ ✦　│ ➥　│ 🖍　│ 🗒　│ ⧉　│ ▤ │
│ 问AI │新建主题│ 高亮 │ 批注 │ 复制 │ 全选 │
└────────────────────────────────────────────────────┘
```

- `Group` 新增 `MARK`（高亮 / 批注），与 AI 组、SYSTEM 组并列（待拍板 C 默认项，未异议即执行）。
- 6 格总宽 ~288dp，`leftFor()` 居中 + 夹取自动贴边，窄屏无碍。
- **流式中（回答未 stable）**：高亮 / 批注随 QUOTE 规则 —— 画出但置灰（点了 store 也会拒绝，可见的拒绝优于静默吞掉，是本仓既有原则）。
- 菜单在**单元格内同样出现**（见 §2.6），条目与 prose 完全一致。

### 2.2 高亮

**建立**：选区非空 → 点「高亮」→ 在**所在容器**（prose run 或单元格）的 `Spannable` 上 `setSpan(YellowHighlightSpan, start, end, SPAN_EXCLUSIVE_EXCLUSIVE)` → 落库 → 收菜单、结束平台 ActionMode。

**黄色取色**（待拍板 D 默认项）：`#FFF3B0` 柔和暖黄。必须走 token 三方同步：`tokens.json` 新增 `yellow-highlight` + `colors.xml` 新增 `tl_yellow_highlight`（**不透明 hex → 资源数 16→17**）+ `design-system/README.md` 的 token 表/资源数/`:root` 行号/变更日志，跑 `check_design_tokens.py` 须 `0 check(s) failed`。

**与 Markdown 样式叠加**：`MarkdownRenderer.java:331` 的代码块灰底 `BackgroundColorSpan` 会盖住先插的高亮 —— 高亮统一由独立 `Highlighter` 在 Markdown 渲染**之后** flush（后插者绘制在上），不与 Markdown 混排。

**重叠合并规则**（V1.1 定稿）：新建高亮与已有高亮**相交**时，落库前合并为并集区间（旧行删除、新行落库）。理由：两个相交的 `ClickableSpan` 平台只让其一可点，不合并会留下「点不动」的死区。边界推论：选区**包含于**已有高亮时点「高亮」→ 合并后区间不变（视觉无变化），符合「严格重合才算已有」的字面语义。

### 2.3 高亮二次点击 → 自动选中 + 菜单变「取消高亮」

```
用户点击正文（prose 或单元格）
   └─ 命中已有高亮？（ClickableSpan.onClick）
        └─ ① Selection.setSelection(spannable, hl.start, hl.end)   // 自动选中
            ② showWordMenu(row)                                    // 菜单弹在选区上
            ③ 菜单第 3 格渲染为「取消高亮」，点击 → removeSpan + 落库删除
```

- **「取消高亮」是状态不是独立格**：判据 = 当前选区**精确等于**某条已有高亮的区间（V1.1 拍板：严格重合）。部分重叠选区 → 第 3 格仍是「高亮」（点击走 §2.2 的合并）。
- `ClickableSpan` 需要 `MovementMethod` 在位（prose 已有 `LinkMovementMethod`；单元格接入时同步挂）。
- 长按仍由 `textIsSelectable` 走 `ActionMode`，与短按可点区不冲突（验收 V-2/V-3 专项覆盖）。

### 2.4 批注

**建立**：点「批注」→ `AlertDialog`（`EditText` 多行，确定/取消）→ 非空则落库 → 关闭后在**选区末尾右上角**锚一个便签图标。

**去重（V1.1 拍板：严格重合）**：同容器 + 同 `kind` + 区间**精确相等** → 拒绝新建，直接打开已有批注的编辑弹窗（预填原文）。同起点异终点、部分重叠 → **允许**，各自独立锚点 —— 多个便签自然走 §2.5 堆叠。

**便签图标实现**（待拍板 F 默认项：覆盖层 + 绝对位移，不改文字流）：
- **prose 段**：`chat_answer_body` 上覆一层 `FrameLayout` 覆盖层，图标 `translationX/Y` 由 `Layout.getPrimaryHorizontal(end)` + `getLineTop(lineForOffset(end))` 算出 —— 复用 `WordMenuPopup.selectionBox()` 同一套坐标。
- **表格**：见 §2.6，图标放**每表一个**、位于 HSV 内容层内的覆盖层 —— 随横向滚动自然移动，无需滚动监听。
- 图标点击 → §2.5；列表为空（批注全删）→ 图标隐藏。

### 2.5 末尾多便签 → 横线椭圆列表

同一锚点位置存在 N 条批注（V1.1 拍板严格重合后，同起点异终点即产生此态）：

```
点击便签图标
   └─ N == 1 → 直接打开该条批注编辑弹窗
   └─ N >= 2 → 弹「横线椭圆」卡片（椭圆大圆角 + tl_surface 底 + 阴影 + tl_line 横线分隔标题与列表）
                点某条 → 打开该条编辑弹窗；可删除
```

弹窗复用 `WordMenuPopup.place()` 夹取算法，并纳入 `ChatScreen.dismissPopups()` 单卡片仲裁。

### 2.6 表格单元格内高亮与批注（V1.1 新增：拍板支持）

**交互**：长按单元格文本 → 起选区 → 同一套 6 格菜单（`WordMenuPopup` 构造入参本来就是「作用的 TextView」，把单元格传进去即可，落位/夹取/翻转全复用）。高亮、批注、二次点击取消、便签锚定，行为与 prose 完全一致。

**视图改造**（本拍板的主要成本）：
1. `inflateTable()`：单元格 `setTextIsSelectable(true)` + `setMovementMethod(LinkMovementMethod)` + `setCustomSelectionActionModeCallback(new SelectionMenuCallback())` + `setText(..., SPANNABLE)`（现为普通 setText，无法承载 span）。
2. `Row` 的选区扫描从 `answerViews` 扩展为 **prose runs + 单元格注册表**：新增 `List<CellRef>`（持 TextView + `(table_index,row,col)`），`selectingRun()` → `selectingTarget()` 返回 `(TextView, 定位器)`；`selectedText()` 同步扩展。**这是工作区记忆红线「任何读选区代码必须遍历 answerViews」的直接延伸。**
3. `item_chat_table.xml`：`HSV > FrameLayout(新增) > TableLayout`，`FrameLayout` 内叠批注覆盖层 —— 图标坐标随 HSV 内容滚动，横向滚动零监听。
4. `ChatMessageRowLayoutTest` 同步结构变更（已核实该测试引用 `item_chat_table`）。

**定位器**：单元格锚 = `(table_index, row_index, col_index, start_offset, end_offset)`，`table_index` = 该回答第 t 个表格段（0 起），行列由 Markdown 表格结构决定 —— 结构稳定，无偏移漂移问题（digest 清除兜底见 §3.3）。

**边界说明**：单元格内 QUOTE/FORK 沿用现有 run-local 语义（quote = 选区子串；fork 偏移相对所在视图 —— 与今天在非首 prose 段选区行为一致），不在本方案改动范围。

---

## 3. 数据模型

### 3.1 单表设计（V1.1 按单元格支持重写）

```sql
CREATE TABLE message_annotations (
  id            TEXT PRIMARY KEY NOT NULL,
  message_id    TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  container     TEXT NOT NULL,             -- 'PROSE' | 'CELL'
  prose_index   INTEGER NOT NULL,          -- PROSE: k>=0；CELL: -1
  table_index   INTEGER NOT NULL,          -- CELL: t>=0；PROSE: -1
  row_index     INTEGER NOT NULL,          -- CELL: r>=0；PROSE: -1
  col_index     INTEGER NOT NULL,          -- CELL: c>=0；PROSE: -1
  start_offset  INTEGER NOT NULL,
  end_offset    INTEGER NOT NULL,
  kind          TEXT NOT NULL,             -- 'HIGHLIGHT' | 'NOTE'
  content_digest TEXT NOT NULL,            -- 创建时整条回答 markdown 的 SHA-256，见 §3.3
  body          TEXT NOT NULL DEFAULT '',  -- 批注正文；HIGHLIGHT 恒为 ''
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  CHECK (end_offset > start_offset),
  CHECK (kind IN ('HIGHLIGHT','NOTE')),
  CHECK (kind <> 'NOTE' OR body <> ''),
  CHECK (kind <> 'HIGHLIGHT' OR body = ''),   -- F1（db-migration-reviewer 2026-09-28：HIGHLIGHT 恒空 body 的 DDL 承载）
  CHECK ((container = 'PROSE' AND prose_index >= 0 AND table_index = -1
          AND row_index = -1 AND col_index = -1)
      OR (container = 'CELL'  AND prose_index = -1 AND table_index >= 0
          AND row_index >= 0 AND col_index >= 0))
);

-- 同一容器、同 kind、同区间唯一（kind 入索引：同区间允许「高亮 + 批注」并存）
CREATE UNIQUE INDEX idx_annotations_anchor ON message_annotations(
  message_id, container, prose_index, table_index, row_index, col_index, kind, start_offset, end_offset);
CREATE INDEX idx_annotations_message ON message_annotations(message_id);
```

要点：
- **V1.0 的 `anchor_text` 列删除** —— 拍板「重生成即删」后，重定位逻辑不复存在，该列无消费者。
- **唯一索引含 `kind`**：同一段可以既有高亮又有批注（「先高亮再批注」是自然动线）；去重只在同 kind 内判。
- 严格重合判重 + 高亮相交合并，判定逻辑集中在 `app/ui/Annotation.java`（纯 JVM 可测）。

### 3.2 定位器总表

| 容器 | 定位键 | 偏移相对 |
|---|---|---|
| prose 段 | `(prose_index=k, start, end)` | 第 k 个 prose 段的渲染文本 |
| 单元格 | `(table_index=t, row=r, col=c, start, end)` | 该单元格文本（表头行 r=0 含在内） |

### 3.3 重生成清除（V1.1 拍板：删除，不做重定位）

机制：**content-digest 失配清除**。
1. 创建批注时记录 `content_digest` = SHA-256(该消息当前整条回答 markdown)。
2. store 读路径加载批注时：`DELETE WHERE message_id=? AND content_digest <> ?` 后再 SELECT —— **一处写路径，自愈**，不依赖「版本切换钩子」是否被正确调用。**F2（reviewer 建议，已采纳）：DELETE 与 SELECT 在同一事务内执行，且 SELECT 显式过滤 `content_digest = ?`，封死比对窗口内 digest 翻转返回失配行的并发窗口。**
3. **流式期间跳过比对**：生成中回答文本逐 token 变化，若此时比对会把旧稳定回答的批注提前删掉（用户可能取消重新生成回到旧文）；仅在 `message.isStable()` 时执行。**F3（reviewer 建议，已采纳）：insert 路径约定——仅在 `isStable()` 且 digest 取自当前显示修订时接受写入；失配写入依赖读路径自愈属预期行为。**
4. 单元格结构变化、段落重排 —— 全部被整条 markdown digest 覆盖，粒度够用（本产品的回答只有「整体替换」一种变更形态）。

### 3.4 迁移

1. 新增 `Annotations` 常量类（表/列/`*_DEFINITIONS`，同现有 11 张表写法）。
2. 加入 `TABLE_NAMES`、`CREATE_TABLE_STATEMENTS`、`CREATE_INDEX_STATEMENTS`。
3. `MIGRATION_V4_TO_V5_STATEMENTS` 与 fresh install **复用同一批字符串常量**（防两路漂移）。
4. 注册 `MigrationV4ToV5`，`DATABASE_VERSION` 4 → 5。
5. `android/data/.../db/dao/AnnotationDao.java`（`listFor(messageId, digest)` 内含清除、`insert`、`delete`、`updateBody`、`mergeRange`）。
6. app 侧经 `SqliteChatStore` / `ChatViewState` 暴露。
7. 门禁：`db-migration-reviewer` 必走；验收在 **API 28+ 真实 Android SQLite** 上跑。

---

## 4. 改动清单与任务拆分（V1.1 重估：4 个 Task）

| Task | 内容 | 主要文件 | 估行 |
|---|---|---|---|
| **T-A 数据层 + 纯逻辑** | schema + 索引 + V4→V5 迁移；`AnnotationDao`（含 digest 清除）；store 接线；`app/ui/Annotation.java`（定位器、严格重合判重、区间合并）+ JVM 测试 | `TreeLearnSchema.java`、`AnnotationDao.java`(新)、`SqliteChatStore.java`、`Annotation.java`(新) | ~460 |
| **T-B 菜单 6 格 + prose 高亮** | `WordMenu` +2 Item +`MARK` 组；`popup_word_action.xml` +2 格；`WordMenuPopup` Listener +2；`Highlighter`（prose flush + `ClickableSpan` 二次点击）；ChatScreen 定位器改造第一段（prose）；黄色 token 三方同步 | `WordMenu.java`、`WordMenuPopup.java`、`Highlighter.java`(新)、`ChatScreen.java`、tokens 三件套 | ~380 |
| **T-C 单元格基建 + 单元格高亮** | 单元格可选中文 + 菜单回调；`CellRef` 注册表 + `selectingTarget()`；`item_chat_table.xml` 加 wrapper + 覆盖层；`ChatMessageRowLayoutTest` 同步；`Highlighter` 扩展到单元格 | `ChatScreen.java`、`item_chat_table.xml`、`MarkdownRenderer`（不动，仅确认） | ~430 |
| **T-D 批注全链路** | `NoteEditorDialog`；便签图标（prose + 单元格覆盖层）；椭圆列表弹窗；严格重合判重的编辑/删除流；`dismissPopups()` 纳管；strings + 仪器测试 | `NoteEditorDialog.java`(新)、`NoteAnchorPopup.java`(新)、`popup_note_list.xml`(新) | ~430 |

依赖链：T-B ← T-A；T-C ← T-B；T-D ← T-C。

> T-A 估 460 行，超出 ≤400 目标线但低于 500 verifier 红线；若要守住 400，可把 SQLite 仪器测试移入 T-B 的前置验收，拆解时定。

---

## 5. 关键设计取舍（V1.1）

| 取舍 | 选择 | 代价 |
|---|---|---|
| 重生成处置 | **整条删除**（digest 失配清除），不做锚点重定位 | 用户重生成后批注全丢 —— 这是拍板选择，接受 |
| 清除时机 | 读路径比对 + 流式跳过 | 比对发生在 store 读路径；实现须保证只在 stable 时跑（否则误删） |
| 高亮相交 | 合并为并区间 | 「子区间新建」视觉无变化；换来单一可点区、无双 span 死区 |
| 单元格支持 | 全量接入（选择/高亮/批注） | 视图改造 ~430 行 + 布局测试同步；表格交互从静态变可选 |
| 便签图标 | prose 一层覆盖层 + **每表一层**覆盖层（在 HSV 内容内） | 比单一覆盖层多一类宿主；换来横向滚动零监听 |
| 表结构 | 单表 + `container` 哨兵列 + `kind` 入唯一索引 | 4 个 -1 哨兵列；换来定位逻辑单份、同区间高亮+批注可并存 |

---

## 6. 拍板状态：全部确认（2026-09-28）

| 项 | 决定 |
|---|---|
| B（重生成） | **高亮、便签全部删除**（§3.3 digest 机制）——用户 15:07 拍板 |
| E+A（重叠） | **严格重合才算**：重复批注与「取消高亮」均要求区间精确相等——用户 15:07 拍板 |
| H（表格） | **支持单元格内高亮**（§2.6）——用户 15:07 拍板 |
| C 菜单分组 | 新增 `MARK` 组——用户 15:23 确认 |
| D 黄色值 | `#FFF3B0` 柔和暖黄——用户 15:23 确认（Demo 内可切三档对比，落 Android 只取默认档） |
| F 图标形态 | 覆盖层 + 绝对位移——用户 15:23 确认 |
| G 单表 + kind | 接受——用户 15:23 确认 |
| **新增约束** | **菜单格一律图标，不得文字按钮**：语义由图标 + `contentDescription` + 顺序承载；「取消高亮」= 记号笔图标加斜杠的态切换。图标族规格 24dp 网格 / 1.8 描边 / 圆角端点（与 `ic_action_copy` / `ic_action_select_all` 同族）。记号笔字形 A/B 两案见 Demo，落 Android 前定一个 |

---

## 7. 风险（V1.1 更新）

| # | 风险 | 缓解 |
|---|---|---|
| R-1 | **digest 清除误删**：流式期间文本逐 token 变，若此时比对会删掉旧稳定回答的批注 | 比对只在 `message.isStable()` 时执行（§3.3 第 3 条）；仪器测试覆盖「流式中旧批注仍在」 |
| R-2 | Markdown `BackgroundColorSpan` 绘制顺序盖住高亮 | `Highlighter` 在 Markdown 渲染后统一 flush（§2.2） |
| R-3 | `ClickableSpan` 吞长按破坏现有选区 | 专项仪器测试 V-2/V-3；prose 与单元格各测一遍 |
| R-4 | 跨线并发写 `android/` 树 | 动手前确认源码静止；守门带 `--no-build-cache`（工作区记忆判据） |
| R-5 | 新弹窗未纳入单卡片仲裁 | 椭圆列表、编辑弹窗全部走 `dismissPopups()` |
| R-6 | 单元格启用 `textIsSelectable` 改变表格长按行为（此前长按无反应） | 预期行为即本特性；回归测试确认表格点击滚动不受影响 |
| R-7 | `item_chat_table.xml` 插 wrapper 层破坏布局断言 | `ChatMessageRowLayoutTest` 在 T-C 内同步修改（已核实其引用该布局） |
| R-8 | 列宽 stretch/换行变化后便签锚点错位 | 字符偏移不受布局影响（span 安全）；图标坐标挂 `onLayoutChange` 重算 |

---

## 8. 验收要点

| # | 判据 | 方式 |
|---|---|---|
| V-1 | 高亮可见、颜色正确 | 真机截图取色（本机 screencap 广色域偏色，黄色匹配留容差） |
| V-2 | 长按选区菜单仍 6 格正常打开（prose + 单元格各一遍） | 仪器测试 |
| V-3 | 精确重合点高亮 → 选区恢复 + 首格「取消高亮」；部分重叠 → 首格仍「高亮」 | 仪器测试断言 `getSelectionStart/End` 与菜单语义 |
| V-4 | 批注图标落位选区右上角，HSV 横向滚动时随内容移动 | 仪器测试 + 截图 |
| V-5 | 同容器同 kind 精确重合二次批注被拒（打开编辑）；异区间允许 | 纯 JVM 单测 |
| V-6 | 多便签 → 椭圆列表列出全部 | 仪器测试：造 3 条 → 断言列表项数 |
| V-7 | V4→V5 迁移在真实 SQLite 通过 | 仪器测试：`onUpgrade` → 断言表/索引/CHECK 存在 |
| V-8 | 守门全绿 | `check_design_tokens.py`、Java-only guard、`validate_tasks.py` |
| V-9 | **重生成后高亮与批注全部消失**（DB 层 `message_id` 下 0 行） | 仪器测试：建批注 → 触发重生成 → 断言清除 |
| V-10 | 流式期间旧批注不被误删 | 仪器测试：建批注 → 触发生成 → 生成中断言仍在 → 完成后按拍板语义判定 |
| V-11 | 相交高亮合并后只有一个可点区 | 仪器测试：两次相交建高亮 → 断言单行 + 单 span |
| V-12 | 单元格内高亮/批注全链路可用 | 仪器测试：cell 选区 → 高亮 → 重进页面仍在 |

---

## 9. 下一步

1. ~~拍板~~ —— 已全部确认（§6）；交互 Demo 已验收（33/33 断言、零异常，截图 `artifacts/tmp/hldemo-menu.png` / `hldemo-note.png`）。
2. 记号笔字形 **A/B 两案**（Demo 右侧可切）在 T-B 开工前定一个，随后落 `ic_highlight.xml` / `ic_highlight_off.xml` / `ic_note.xml`。
3. 按 §4 拆 T-A → T-B → T-C → T-D 四个 Task 开工；T-A 开工前先跑 `db-migration-reviewer` 对 §3.1 schema 过一遍。
