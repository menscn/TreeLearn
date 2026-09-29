# Spec: 对话文本选中与高亮反馈提速

id: SPEC-2026-09-29-selection-flow-responsiveness
title: 对话文本选中与高亮反馈提速
date: 2026-09-29
intent: Docs/intent/INTENT-2026-09-29-selection-flow-responsiveness.md
author: spec-author
status: accepted

## Requirement spec（需求规格）

- 范围：稳定回答的 prose 文本长按触发与高亮点击后的即时反馈。
- 验收方向：真机长按成功率和响应体感改善；高亮点击后立即可见，异步持久化成功后保持，失败后回滚。
- 非目标：单击选词、表格单元格选中、菜单信息架构、SQLite schema、后端 API。
- 来源：`Docs/intent/INTENT-2026-09-29-selection-flow-responsiveness.md` 的 Expected outcome、Core flow、Constraints。

## Interface plan（界面方案）

- 信息架构：仍由现有 prose TextView 承载系统选区句柄，现有六格 WordMenuPopup 承载动作。
- 入口位置：长按 prose 文本进入选区；点击六格菜单中的高亮格触发即时视觉投影。
- 状态与异常：长按候选有按下、移动取消、触发、松手和页面销毁状态；高亮有 optimistic、persisted、rollback 三种内部状态，用户只看到及时的黄色反馈或既有存储错误提示。
- 数量与呈现方式：菜单格数量与顺序不变；高亮仍使用现有黄色设计 token，不新增颜色值。
- 来源：现有对话页选择菜单方案、`tl_yellow_highlight` token、`ChatScreen` 的 ActionMode 回调路径。

## Technical spec（技术规格）

- 接口：不修改对外 API；Android view 层新增可取消的短延迟触摸候选，Presenter 保留现有 `onHighlightPressed` 入口。
- 数据结构：高亮预览使用当前 `Annotation` 列表的内存投影；SQLite 行和内容 digest 仍由现有 store 写入并作为最终权威。
- 状态迁移：按下 → 候选；移动或取消 → 结束；保持按住 → 选择词并启动 ActionMode；高亮点击 → 预览 → 后台写入 → 权威读回确认或回滚。
- 错误语义：后台写入失败不得留下假高亮；由既有 `HomeViewState.Notice.STORAGE` 路径提示，权威读回负责恢复。
- 来源：`ChatScreen.Row`、`Highlighter`、`ChatPresenter.onHighlightPressed`、`ChatWiring.applyHighlight`、`SqliteChatStore.applyHighlight`。

## Doubts（疑虑点）

- 疑虑 1：不同 Android 设备对 TextView 的默认长按与 ClickableSpan 仲裁可能不同（冲突对象：`LinkMovementMethod` 与 ActionMode；两种走法：短延迟触摸桥或完全依赖系统长按；谁拍板：Android owner，采用短延迟触摸桥并用真机回归约束）。
