# MarkdownRenderer：表格从字符画改为真表格视图

日期：2026-09-28
起因：用户在真机截图里发现对话页的 GFM 表格渲染成 ASCII 框线，且**列完全对不齐**。

## 结论（先说原因，因为这是设计缺陷不是 bug）

字符画方案在手机上**必然**错，原因两条，都不是调参能修的：

1. **盒子只在「不被折行」时才对齐**。`│`/`─` 靠等宽字体保证字符advance相等，但 `chat_answer` 是
   `match_parent` 的 `TextView`，长行一软换行，后面所有 `│` 的列位置就全错开。等宽只保证字符等宽，
   **不保证盒子不断**。
2. **中文按 1 个 UTF-16 unit 算，实占 2 个字符格**。旧 `renderTable` 用 `String.length()` 求列宽，
   中文列本来就短了一半。

## 改法

`MarkdownRenderer` 不再把表格拍成一段文本，而是输出**有序的 segment 序列**：

- `MarkdownRenderer.Segment`：`isTable` / `text` / `table`（行×列的 `CharSequence`）/ `header` / `hasHeader`
- `renderSegments(md, codeClick)` → `List<Segment>`，prose 段与 table 段按文档顺序
- `render(md, codeClick)` 保留（思考链、译文用），把 table 段 `flattenTable` 拍回文本
- `ChatScreen.Row.renderAnswerBody` 按段挂载到新的 `chat_answer_body` 宿主：
  - prose 段 → `TextView`（第一段复用布局自带的 `chat_answer`，后续 inflate `view_chat_answer_text`）
  - table 段 → `inflateTable()` 生成 `HorizontalScrollView > TableLayout > TableRow > TextView`
- 表格的**行间发丝线**由 `TableLayout` 底色 `tl_line` 透过单元格 1dp 间隙形成，不画额外 View
- 选中/追问/复制改成扫 `answerViews` 列表（表格把答案拆成多个 TextView，选区只落在其中一个）

## 新增资源（全部走既有 token，未新增 token）

| 文件 | 作用 |
|---|---|
| `layout/item_chat_table.xml` | `HorizontalScrollView` + `TableLayout`（宽表横向滚，不挤压正文） |
| `layout/item_chat_table_row.xml` | `TableRow` |
| `layout/item_chat_table_cell.xml` | 表体单元格：`tl_surface` 底 / `tl_ink` / `text_sm` |
| `layout/item_chat_table_header_cell.xml` | 表头单元格：`tl_bg` 底 / `tl_ink2` / `text_sm` / bold |
| `layout/view_chat_answer_text.xml` | 第一个表格之后的 prose 段 |

## 验收（证据：`artifacts/evidence/T-2026-09-28-md-table-as-view.txt`）

- `:app:compileDebugJavaWithJavac` BUILD SUCCESSFUL
- `:app:testDebugUnitTest`（`ChatMessageRowLayoutTest` + `screen.*`）BUILD SUCCESSFUL
- `verifyNoKotlinDex` PASS（3 APK 检查，无 Kotlin 运行时字节码）
- 真机 `ANRBBB5427000010` 装 debug APK，打开既有会话「卡诺模型的介绍与面试应用」：
  四列表（类型/满足时/不满意时/曲线形状）表头灰底、列对齐、行间发丝线正常；
  最长行 `只在"缺失"侧急剧下跌` **在自身单元格内换行**而列不错位 —— 这正是旧方案做不到的点。
  截图：`artifacts/tmp/device/compose.png`、`artifacts/tmp/device/table_wide.png`

## 同步改动的既有测试（不是放宽断言，是换了正确的契约）

- `MarkdownRendererInstrumentedTest.gfmTableBecomesReadableAlignedText` → 拆成
  `gfmTableBecomesATableSegmentNotBoxCharacters`（**断言旧行为已消失**）+ `aTableIsASeparateSegmentWithItsRowsAsData`
  + `aTableThatDoesNotSitBetweenProseIsTheWholeBody`
- `ChatMessageRowLayoutTest.tappingACodeBlockOffersCopy`：`MarkdownRenderer.render(message.text,` 已不存在，
  改为断言 `renderSegments(` + `onSelectionCopyPressed(code)`；新增 `aTableIsRenderedAsATableAndNotAsText`

## 环境教训（本次踩到，值得记）

- **别线正在并发改同一棵树**：本次 `AnswerActionBar.java` / `view_answer_actions.xml` / `popup_word_action.xml` /
  `strings.xml` 被另一条线大改，`popup_word_action.xml` 一度引用尚未加入 `strings.xml` 的
  `chat_word_group_ai` 等 4 个字符串 → `:app:processDebugResources` FAILED。
  **这是别线的中间态，不是本改动的缺陷**。判据：`git status` 里那些文件不是本线所改 + 文件 mtime 就在当下。
  处理方式：按项目约定**等源码静止**（本次 20s 采样 ×7 轮，最后一轮 45s 无写入）再跑构建。
- `./gradlew testDebugUnitTest --tests "..."` **不带模块前缀会对所有模块生效**，`ai` 模块找不到该类即整体失败；
  必须写 `:app:testDebugUnitTest`。
