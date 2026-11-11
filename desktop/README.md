# 知芽 Treelearn 桌面端（Electron 壳）

桌面端加载 `frontend/` 的离线单文件界面，通过隔离桥接使用本机学习数据；普通浏览器仍使用演示夹具。用户可在桌面设置模型服务地址、模型与 API Key，提问、追问、选择回答风格、查看和搜索学习记录、复制回答，以及停止或重试生成。

学习记录和草稿保存在当前 Windows 用户的应用数据目录下 `desktop-learning-v1/learning.sqlite`；模型密钥由 Electron `safeStorage` 加密后保存，不会回传到界面。首次使用需要在桌面设置中输入自己的 API Key；Android 数据不会自动导入。问题和对话上下文会发送到用户配置的模型服务商。

桌面学习工作区可基于完整回答创建学习分支，通过层级导航切换、返回来源并保存各分支草稿；分支继承创建时固定的上下文。回答风格按主题保存，历史回答保留生成时的风格。每次可选择一份 UTF-8 的 TXT 或 Markdown 文本资料，文件最多 128 KiB、正文最多 20,000 个 UTF-16 编码单元；选择后正文会保存在本机数据库，发送时提供给当前模型服务商。当前不支持 PDF、图片、Word、划词锚点或单分支删除；模型生成质量和可用性取决于所选服务与模型。

## 结构

```text
desktop/
├─ main.js                 Electron 主进程：窗口、导航拦截、单实例、smoke 模式
├─ window-options.cjs      窗口参数的唯一来源（尺寸、标题、隔离选项）
├─ navigation.cjs          导航白名单：只允许应用入口页面
├─ preload.cjs             仅暴露受限桌面接口的隔离桥
├─ desktop-ipc.cjs         校验窗口、来源并映射接口错误
├─ learning-service.cjs    本机 SQLite、加密凭据和流式模型请求
├─ scripts/
│  ├─ sync-app.mjs         把 frontend/dist-offline/index.html 复制成 desktop/app/index.html
│  ├─ matrix.cjs           逐屏矩阵：14 个状态 × 两档尺寸
│  ├─ evidence-dir.cjs     矩阵证据目录：默认按运行日分目录，可用 TREELEARN_EVIDENCE_DATE 复跑某一档
│  ├─ verify-matrix.cjs    在真实窗口里逐屏断言并截图
│  ├─ verify-shell.mjs     sync → smoke → matrix 一次跑完
│  └─ package-win.mjs      便携 exe 打包：临时目录构建，成品回 release/
├─ test/                   Node 内置测试：窗口选项、导航白名单、矩阵完整性
├─ assets/
│  ├─ icon.svg             图标源文件（深绿种子，取品牌 token）
│  ├─ icon.png             1024px 主图
│  └─ icon.ico             Windows 多尺寸图标（16–256）
├─ app/index.html          构建副产物，不入库（由 sync-app.mjs 生成）
└─ release/                便携 exe，不入库
```

## 命令

前置：先在仓库根运行 `pnpm --dir frontend install` 与 `pnpm --dir frontend build`（后者同时产出 `dist-offline/index.html`）。

```bash
pnpm --dir desktop install         # 首次安装 Electron 工具链
pnpm --dir desktop test            # 桌面单元与契约测试
pnpm --dir desktop start           # 构建产物同步 + 打开桌面窗口（开发态）
pnpm --dir desktop run smoke       # 无头启动一次并断言页面已渲染
pnpm --dir desktop run verify      # sync + smoke + 14 屏 × 2 尺寸矩阵
pnpm --dir desktop run package:win # 在 desktop/release/ 生成带时间戳的便携 exe
```

`verify` 的截图与报告在 `artifacts/evidence/desktop-shell/<运行日>/`：每个状态两档尺寸各一张 JPEG，`report.json` 记录标题、根节点、文本长度、横向溢出、控制台错误与筛选/分类命中。目录按**运行当天**分（`scripts/evidence-dir.cjs` 一处解析），重跑不会再覆盖前一天的历史证据；要复跑某一档时用 `TREELEARN_EVIDENCE_DATE=2026-10-24` 这类显式日期，空白值回落当天。矩阵按成品窗口外框设置 1180×820 / 900×620，内容区实测为 1167×785 / 887×586，两档都高于 Web 的 760 折叠断点。

2026-10-27 的便携包是历史产物，不能代表当前源码或真实学习能力；发布前需重新构建并验证新的便携包。

## 行为约定

- 窗口为标准 Windows 标题栏，可缩放；默认 1180×820，最小 900×620（保证侧栏为展开态）。
- 移除 Electron 默认菜单，不开放新窗口，仅允许导航至应用自身的入口页面及其 hash 路由。
- 单实例：重复双击便携版会聚焦已有窗口，不再开第二个进程。
- `--smoke` 模式不显示窗口，检查夹具页面渲染；该模式不证明真实桌面数据、凭据或模型请求。

## 图标

- 源文件是 `assets/icon.svg`：深绿 `#164027` 底、象牙白 `#F4F1EB` 芽、生长绿年轮与一点琥珀 `#B05108` 种子，全部取自设计 token。
- `assets/icon.ico` 含 16/24/32/48/64/128/256 七档；打包配置 `build.win.icon` 指向它，16px 任务栏仍能读成一颗白芽。
- Android 启动图标仍使用原有 `android/app/src/main/res/drawable/ic_launcher_foreground.xml`，本次未改动。

## 打包说明

- 构建输出放在本次专属系统临时目录，只把带时间戳的成品 exe 拷回 `desktop/release/`，保留已有构建；构建前须先运行前端 build。
- 打包器二进制默认走 `ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/`，可用同名环境变量覆盖；Electron 本体由 `.npmrc` 的 `electron_mirror` 指向镜像。
- `pnpm-workspace.yaml` 将 `@electron/get` 覆盖为 5.1.0：electron-builder 26.18.0 的依赖范围仍是 ^3，但其代码引用了 5.x 才有的 `ElectronDownloadCacheMode`。
