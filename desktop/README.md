# 知芽 Treelearn 桌面端（Electron 壳）

桌面端不另写界面：它把 `frontend/` 构建出的离线单文件产物装进一个标准 Windows 窗口，布局、样式、交互与 Web 版完全一致。

## 结构

```text
desktop/
├─ main.js                 Electron 主进程：窗口、导航拦截、单实例、smoke 模式
├─ window-options.cjs      窗口参数的唯一来源（尺寸、标题、隔离选项）
├─ navigation.cjs          导航白名单：只允许 file://
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
pnpm --dir desktop test            # 单元测试（15 例）
pnpm --dir desktop start           # 构建产物同步 + 打开桌面窗口（开发态）
pnpm --dir desktop run smoke       # 无头启动一次并断言页面已渲染
pnpm --dir desktop run verify      # sync + smoke + 14 屏 × 2 尺寸矩阵
pnpm --dir desktop run package:win # 产出 desktop/release/Treelearn-1.0.0-portable.exe
```

`verify` 的截图与报告在 `artifacts/evidence/desktop-shell/<运行日>/`：每个状态两档尺寸各一张 JPEG，`report.json` 记录标题、根节点、文本长度、横向溢出、控制台错误与筛选/分类命中。目录按**运行当天**分（`scripts/evidence-dir.cjs` 一处解析），重跑不会再覆盖前一天的历史证据；要复跑某一档时用 `TREELEARN_EVIDENCE_DATE=2026-10-24` 这类显式日期，空白值回落当天。矩阵按成品窗口外框设置 1180×820 / 900×620，内容区实测为 1167×785 / 887×586，两档都高于 Web 的 760 折叠断点。

当前成品（2026-10-27 重打包）：`release/Treelearn-1.0.0-portable.exe` 114,592,941 字节，内嵌 `app/index.html` 与 `frontend/dist-offline/index.html` 同 sha256（`8dc53a86…6735`，5,871,820 字节）；真实启动窗口标题「知芽」，1180×820 逻辑尺寸（200% 缩放下外框 2360×1640）。

## 行为约定

- 窗口为标准 Windows 标题栏，可缩放；默认 1180×820，最小 900×620（保证侧栏为展开态）。
- 移除 Electron 默认菜单，不开放新窗口，不允许导航到 `file://` 以外的地址。
- 单实例：重复双击便携版会聚焦已有窗口，不再开第二个进程。
- `--smoke` 模式不显示窗口，断言 `#root` 有子节点、标题非空、无渲染错误后自行退出。

## 图标

- 源文件是 `assets/icon.svg`：深绿 `#164027` 底、象牙白 `#F4F1EB` 芽、生长绿年轮与一点琥珀 `#B05108` 种子，全部取自设计 token。
- `assets/icon.ico` 含 16/24/32/48/64/128/256 七档；打包配置 `build.win.icon` 指向它，16px 任务栏仍能读成一颗白芽。
- Android 启动图标仍使用原有 `android/app/src/main/res/drawable/ic_launcher_foreground.xml`，本次未改动。

## 打包说明

- 构建输出放在系统临时目录，只把成品 exe 拷回 `desktop/release/`。原因：IDE 的文件索引会长期占用工作区内的 `.asar`，electron-builder 重复清理时会报 `EBUSY`。
- 打包器二进制默认走 `ELECTRON_BUILDER_BINARIES_MIRROR=https://npmmirror.com/mirrors/electron-builder-binaries/`，可用同名环境变量覆盖；Electron 本体由 `.npmrc` 的 `electron_mirror` 指向镜像。
- `pnpm-workspace.yaml` 将 `@electron/get` 覆盖为 5.1.0：electron-builder 26.18.0 的依赖范围仍是 ^3，但其代码引用了 5.x 才有的 `ElectronDownloadCacheMode`。
