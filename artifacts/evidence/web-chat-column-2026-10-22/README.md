# Web 对话列宽对齐证据（2026-10-22）

## 问题

桌面对话页的阅读面与输入区不是同一列：阅读面按视口留白收窄，composer 顶到外层可用宽度，用户截图里两者左右边缘明显错位；此外正文滚动条出现时，居中内容还会再偏移一截。

## 改动

- `frontend/src/styles/workspace.css`：`.chat-main` 定义 `--chat-column: 784px`，`.chat-reading` 与 `.chat-dock .composer` 共用该 `max-width` 并居中。
- `.chat-scroll` 加 `scrollbar-gutter: stable both-edges`，滚动条槽位两侧对称，出现/消失不再推动居中的阅读面。
- 对话页 composer 横向 padding 与阅读面一致（`--s4` 上下、`--s8` 左右）；窄窗仍为 `--s3`，不压缩手机宽度下的输入区。
- 首页输入区不受影响：只匹配 `.chat-dock .composer`。
- `frontend/test/styles/chat-column.test.ts`：两条结构钉，锁住共享列宽变量与对称滚动条槽位。

## 取证

- 工具：仓库既有 Edge CDP 驱动 `.codex/scripts/dev/parity/parity_cdp.mjs`（本机无 Playwright）。
- 入口：`node artifacts/evidence/web-chat-column-2026-10-22/probe.mjs`（需先 `pnpm --dir frontend dev`，端口 5174）。
- 视口：1440×900，进入主题 0 的对话页。

读数（`readings.json`）：

| 对象 | left | right | width |
|---|---|---|---|
| `.chat-reading` | 448 | 1232 | 784 |
| `.chat-dock .composer` | 448 | 1232 | 784 |

正文首行 left 480，输入占位 left 480；`overflowX=false`。截图 `desktop-chat.png` 中卡片与输入区左右边缘重合。

## 回归

- `pnpm --dir frontend test`：18 文件 113 例全绿（新增 `chat-column.test.ts` 2 例）。
- `pnpm --dir frontend verify`：`verify steps=16 ok=16 bad=0 fault=0`、`VERIFY PASS`、`OVERALL_EXITCODE=0`。
- 截图复核：阅读面与输入区同宽，正文与输入文字同基线；窄窗规则未改动既有覆盖样式。
