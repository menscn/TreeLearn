# 知芽（TreeLearn）· 按钮反馈与设置入口更新

| 项 | 值 |
|---|---|
| 日期 | 2026-10-09 |
| 状态 | 用户确认方案 A；Android 实现、构建和 ALP-AN00 安装记录见对应 Harness 任务 |
| 需求来源 | 用户要求按钮点击反馈整体优化、设置页隐藏数据边界说明、设置入口移到首页右上角 |
| 旧设计 | 2026-09-28 设置重设计见 [历史设计快照](知芽-TreeLearn-设置页重设计方案-V1.0.md)；配套交互稿只记录当日方案 |

## 当前方案

### 导航与设置

- 底部导航保留主页、学习库两个一级入口，手机与平板一致。
- 首页顶栏左侧保留抽屉按钮，右侧使用齿轮图标打开设置；触控区域沿用项目 touch_min。
- 设置是辅助页面，不显示底部导航。首页进入后返回首页；聊天页齿轮和缺少 Key 引导继续沿用原有设置路由，返回原聊天。
- 设置根页不显示“数据边界”标题和说明。此项只改变页面展示，不改本机存储、备份配置、Provider 请求数据或 API Key 安全策略。

### 按钮反馈

- 使用 Android RippleDrawable 提供平台原生按压反馈，不缩放、不移动控件。
- 普通浅色/透明控件以现有 tl_green token 的低透明度状态层表达；实心绿色主操作以 tl_green_deep 的半透明状态层形成可见按压差异；危险操作沿用 tl_red 语义。颜色通过 Android ColorStateList alpha 使用现有 token，不增加设计 token。
- RippleDrawable 叠加在现有主按钮、图标按钮、发送按钮、保存按钮及危险/ghost/chip 底色上；enabled/disabled 状态和静态底色保持。
- Theme 的 colorControlHighlight 统一平台 selectableItemBackground / selectableItemBackgroundBorderless 的波纹色；内容卡片的点击命令、导航目标和数据行为不变。

### 历史与契约

- 2026-09-28 设置页重设计稿及其交互原型保留原样，作为历史快照；当时的“设置一级 tab”和“数据边界展示”不再代表当前 Android UI。
- PRD V3.2 的三个并列底部入口未自动改写；当前 Android UI 按 2026-10-09 用户决定执行。此差异限于入口位置与文案呈现，不改变设置能力和数据处理契约。
- Android README 是当前运行行为索引；本记录说明本次 UX 决定与按钮状态设计。
