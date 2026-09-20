# 前端交互与文案

读取时机：frontend 分析、修改或复核前。仅加载本任务命中的规则；正文约束沿用原适用范围，不因读取而进入 Harness。

## Frontend user-copy i18n guard

This guard applies to direct execution and Harness work under `frontend/`. Every user-visible string must be read from the existing i18n resources with matching `zh-CN` and `en-US` keys. This includes JSX text and visible props, dialog/Toast/notification content, accessible labels, and safe frontend mappings for backend error codes. Tests may assert localized output, but production code must not hardcode either Chinese or English UI copy.

- Run `pnpm check:user-copy-i18n` for every production frontend change. The command is also part of frontend dev, lint, Web build, and Android build entrypoints; a failure blocks completion.
- The tracked legacy baseline freezes pre-guard debt and is decrement-only. When a legacy literal is migrated, remove its baseline entry in the same change. Never regenerate, expand, or increase the baseline merely to accept new or moved hardcoded copy.
- Locale resources, test expectations, comments, and documented non-user-visible protocol/diagnostic constants are treated separately. A production-code exception requires an adjacent `i18n-ignore` with a concrete reason and must never cover text that can reach the UI, accessibility tree, exported user content, clipboard, or system notification.
- Feature tests must exercise the affected copy through i18n. When behavior depends on the active locale or persisted state, include both `zh-CN` and `en-US` and prove translated text is resolved at render time rather than persisted as business/session state.

## 设备时区与时间展示

- 所有用户可见的真实时间点必须按当前设备时区展示，格式语言使用当前界面语言；禁止固定为服务器、数据库、账号默认时区、UTC 或 `Asia/Shanghai`。
- 时间点只接受 epoch 秒、epoch 毫秒或带 `Z`/明确 offset 的 ISO。前端不得猜测无 offset 日期时间字符串的来源时区。
- 状态层保留原始时间点，展示时通过共享设备时间模块格式化；不得持久化本地化后的时间文案。应用重新获得焦点或从后台恢复时必须感知设备时区变化。
- `YYYY-MM-DD` 等 date-only 值保持原日期，不进行时区换算。固定业务时区仅允许存在于明确命名、受静态守卫白名单约束的业务日历工具中。
- 生产代码不得绕过共享模块直接使用 `toLocale*`、`Intl.DateTimeFormat`、`Intl.RelativeTimeFormat`、固定 locale/timezone 或 ISO 字符串截断生成展示时间。
- 时间展示变更必须覆盖至少两个会跨自然日的设备时区、夏令时地区、以及 `zh-CN`/`en-US`。

## 用户错误提示

- 前端展示错误时不得直接暴露后端原始异常、英文堆栈、网络库报错、内部错误码或非人话型 message；必须在前端边界或 API 适配层转成用户能理解、可行动的中文提示，并把原始错误仅用于安全日志或排障上下文。

## frontend-dev 执行与复核职责

- Inside Harness, own frontend/** only when assigned; direct execution follows the root mode decision.
- Tablet landscape first; do not assume portrait mobile.
- Use @microsoft/fetch-event-source for SSE; do not use native EventSource for authenticated streams.
- Use the unified HTTP/API client; do not scatter ad-hoc axios/fetch calls.
- Do not call Python services directly from frontend; route through Java /api where applicable.
- For behavior changes, read relevant runtime evidence for scrolling, focus, layout and native interactions, and verify real mode/platform entrypoints.
- Respect existing dependency boundaries (pages→layouts→components→hooks→store→api→platform→utils) and run frontend lint/build checks when relevant.