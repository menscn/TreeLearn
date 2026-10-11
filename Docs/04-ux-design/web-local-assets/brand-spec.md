# 知芽 Web · 视觉与资产约定

采集日期：2026-10-10。资产来源：本项目 Android brand vector、v1.1 HTML 原型、用户提供的薄荷雾面截图。资产完整度：本轮 Web 原型所需品牌、参考图、图标和截图已具备。

## 核心资产

- [主标志](logo-main.svg)来自 `android/app/src/main/res/drawable/ic_brand_sprout.xml`，与 v1.1 ISprout 同源；原型为离线交付将其等价内嵌。
- [深底使用标志](logo-on-dark.svg)仅为资产版本，不代表新增深色模式。
- [用户配色截图](mint-palette-reference.png)是本轮 Web 色板来源。
- [桌面对话](web-chat-desktop.jpg)、[树图](web-tree-desktop.jpg)、[设置](web-settings-desktop.jpg)、[窄窗口](web-narrow.jpg)为本轮原型渲染检查图。

## 设计系统

- 色值唯一清单：[Web token](../web-local-design-tokens.json)；基线间距、字型、圆角与阴影引用既有 [tokens](../design-system/tokens.json)。
- `web-white` 承载内容，`web-mist` 用于安静的雾面底，`web-mint` 表达选择，`web-sage` 用于主操作/控件边界；文字使用确定的深色对比 token。
- 正文字体沿用系统 sans 回退，品牌使用既有 serif 回退。主标题保持克制，长回答使用既有 `lh-read`。
- 图标源码来自 [v1.1](../知芽-TreeLearn-高保真原型-v1.1.html)的 Ic 与 I*；不重新画品牌，不增加 emoji 图标体系，不用新图标库稀释原型识别。
- 气质：轻、安静、清晰，留出连续阅读的空间。容器与分隔服务于内容，避免无意义的大号装饰卡和数据填充。

## 禁区与验证

- 不把新 Web 色值塞回 Android 既有 token 映射，也不把两端功能一致误解为颜色必须相同。
- 不复制参考开源项目的紫色/暗色品牌、账户栏或 Agent 功能。
- 主按钮和次级文字按实际前后景计算对比；薄荷/鼠尾草的名字不是可读性证据。
- 示例数据与真实统计区分；原型页成功状态不代表生产后端已完成。
