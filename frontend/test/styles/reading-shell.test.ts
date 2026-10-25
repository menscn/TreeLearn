import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * 阅读页悬浮玻璃外壳（T-2026-10-25-081）。
 *
 * jsdom 不渲染 CSS，判据从落盘源码读：四块面必须是独立浮起的关系，输入坞上方不许再有一条线。
 * 顶栏高度与滚动留白必须成对——留白比栏矮会压住正文，比栏高会在胶囊下缘露一条空带。
 */
const workspacePath = fileURLToPath(new URL("../../src/styles/workspace.css", import.meta.url));
const pagesPath = fileURLToPath(new URL("../../src/styles/pages.css", import.meta.url));
const forestPath = fileURLToPath(new URL("../../src/styles/forest.css", import.meta.url));
const glassPath = fileURLToPath(new URL("../../src/styles/glass.css", import.meta.url));
const tokensPath = fileURLToPath(new URL("../../src/styles/tokens.css", import.meta.url));

function read(path: string): string {
  return readFileSync(path, "utf8");
}

function rule(css: string, selector: string): string {
  const at = css.indexOf(`${selector} {`);
  expect(at, `missing rule: ${selector}`).toBeGreaterThanOrEqual(0);
  const open = css.indexOf("{", at);
  return css.slice(open + 1, css.indexOf("}", open));
}

function narrow(css: string): string {
  const at = css.indexOf("@media (max-width: 760px)");
  expect(at, "missing narrow breakpoint").toBeGreaterThanOrEqual(0);
  return css.slice(at);
}

describe("阅读页悬浮玻璃外壳", () => {
  const workspace = read(workspacePath);

  it("外壳四边内缩，侧栏成为独立浮起的玻璃面", () => {
    const shell = rule(workspace, ".app-workspace");
    expect(shell).toMatch(/padding:\s*var\(--s3\)/);
    expect(shell).toMatch(/gap:\s*var\(--s3\)/);
    const sidebar = rule(workspace, ".app-sidebar");
    expect(sidebar).toMatch(/border-radius:\s*var\(--r-2xl\)/);
    expect(sidebar).not.toMatch(/border-right:\s*1px/);
  });

  it("顶栏是胶囊，不再贴满窗口也不画下边框", () => {
    const bar = rule(workspace, ".workspace-topbar");
    expect(bar).toMatch(/height:\s*52px/);
    expect(bar).toMatch(/border-radius:\s*var\(--r-full\)/);
    expect(bar).toMatch(/border-bottom:\s*0/);
    expect(bar).toMatch(/top:\s*0/);
  });

  it("滚动留白等于栏高加 12px 间距，两种断点都不留空带", () => {
    const wideBar = rule(workspace, ".workspace-topbar");
    const wideHeight = Number(wideBar.match(/height:\s*(\d+)px/)?.[1]);
    expect(wideHeight).toBeGreaterThan(0);
    for (const [scroller, css] of [
      [".chat-scroll", workspace],
      [".page-scroll", read(pagesPath)],
      [".forest-page", read(forestPath)]
    ] as const) {
      const pad = Number(rule(css, scroller).match(/padding-top:\s*(\d+)px/)?.[1]);
      expect(pad, `${scroller} 与顶栏高度不配套`).toBe(wideHeight + 12);
    }

    const narrowWorkspace = narrow(workspace);
    const narrowPages = narrow(read(pagesPath));
    const narrowForest = narrow(read(forestPath));
    const narrowHeight = Number(rule(narrowWorkspace, ".workspace-topbar").match(/height:\s*(\d+)px/)?.[1]);
    expect(narrowHeight).toBeGreaterThan(0);
    expect(rule(narrowWorkspace, ".chat-scroll")).toMatch(new RegExp(`padding-top:\\s*${narrowHeight + 12}px`));
    expect(rule(narrowPages, ".page-scroll")).toMatch(new RegExp(`padding-top:\\s*${narrowHeight + 12}px`));
    expect(rule(narrowForest, ".forest-page")).toMatch(new RegExp(`padding-top:\\s*${narrowHeight + 12}px`));
  });

  it("输入坞没有横线，玻璃面自身不画背景", () => {
    const dock = rule(workspace, ".chat-dock");
    expect(dock).not.toMatch(/border-top/);
    expect(dock).toMatch(/padding:\s*0 var\(--s3\) var\(--s3\)/);
    const glassDock = rule(read(glassPath), ".chat-dock");
    expect(glassDock).not.toContain("box-shadow");
    expect(glassDock).toContain("background: transparent");
  });

  it("输入框是悬浮玻璃卡：圆角与内边距落在 token 上", () => {
    expect(rule(workspace, ".composer")).toMatch(/border-radius:\s*var\(--r-2xl\)/);
    const dockComposer = rule(workspace, ".chat-dock .composer");
    expect(dockComposer).toMatch(/padding:\s*var\(--s3\) var\(--s4\) var\(--s2\)/);
  });

  it("答案动作是玻璃圆点，不再用一条线收尾", () => {
    const actions = rule(workspace, ".chat-answer-actions");
    expect(actions).not.toMatch(/border-top/);
    const dot = rule(workspace, ".chat-answer-actions .icon-btn");
    expect(dot).toMatch(/width:\s*44px/);
    expect(dot).toMatch(/height:\s*44px/);
    expect(dot).toMatch(/border-radius:\s*var\(--r-full\)/);
    expect(dot).toMatch(/background-color:\s*var\(--glass-card\)/);
  });

  it("窄窗外壳再收一档，抽屉与顶栏仍是浮面", () => {
    const narrowWorkspace = narrow(workspace);
    expect(rule(narrowWorkspace, ".app-workspace")).toMatch(/padding:\s*var\(--s2\)/);
    expect(rule(narrowWorkspace, ".app-sidebar")).toMatch(/inset:\s*var\(--s2\) auto var\(--s2\) var\(--s2\)/);
    expect(rule(narrowWorkspace, ".app-sidebar")).toMatch(/border-radius:\s*var\(--r-2xl\)/);
  });
});

/**
 * 输入坞悬浮与底缘羽化（T-2026-10-25-085）。
 *
 * 坞一旦脱离文档流就不再撑高布局：末行留白和羽化带高度必须是同一个数——
 * 只有 `.chat-main` 上那一个 `--dock-h`，两处都 calc 它。写第二个字面量就会漂移，
 * 结果是引用条出现时末行被输入框压住，或者正文底部凭空多一段空玻璃卡。
 */
describe("输入坞悬浮与底缘羽化", () => {
  const workspace = read(workspacePath);

  it("输入坞脱离文档流悬浮，只有输入框接收指针事件", () => {
    expect(rule(workspace, ".chat-main")).toMatch(/position:\s*relative/);
    const dock = rule(workspace, ".chat-dock");
    expect(dock).toMatch(/position:\s*absolute/);
    expect(dock).toMatch(/right:\s*0/);
    expect(dock).toMatch(/bottom:\s*0/);
    expect(dock).toMatch(/left:\s*0/);
    expect(dock).not.toMatch(/flex:\s*none/);
    expect(dock).not.toMatch(/background:\s*var\(--bg\)/);
    expect(dock).toMatch(/pointer-events:\s*none/);
    expect(rule(workspace, ".chat-dock .composer")).toMatch(/pointer-events:\s*auto/);
  });

  it("坞高是单点声明：默认值写在 .chat-main，其余两处只 var 它", () => {
    const main = rule(workspace, ".chat-main");
    expect(main).toMatch(/--dock-h:\s*\d+px/);
    expect(rule(workspace, ".chat-scroll")).toMatch(/var\(--dock-h\)/);
    expect(rule(workspace, ".chat-reading")).toMatch(/calc\(var\(--dock-h\)/);
    expect(rule(workspace, ".chat-dock")).not.toMatch(/--dock-h:\s*\d+px/);
    /* 窄窗另有一条 .chat-reading 覆盖：它一旦把留白写回固定值，引用条出现时末行就被压住。 */
    expect(rule(narrow(workspace), ".chat-reading")).toMatch(/calc\(var\(--dock-h\)/);
  });

  it("底缘用遮罩淡出正文，不盖一层色带", () => {
    const scroll = rule(workspace, ".chat-scroll");
    expect(scroll).toMatch(/-webkit-mask-image:\s*linear-gradient\(to bottom/);
    expect(scroll).toMatch(/mask-image:\s*linear-gradient\(to bottom/);
    expect(scroll).toMatch(/calc\(100% - var\(--dock-h\)\)/);
    expect(scroll).toContain("var(--dock-feather-ghost)");
    const dock = rule(workspace, ".chat-dock");
    expect(dock).not.toMatch(/linear-gradient/);
    expect(dock).not.toMatch(/box-shadow:/);
  });

  it("羽化终点单点声明，且不进四方 token 链", () => {
    const main = rule(workspace, ".chat-main");
    expect(main).toMatch(/--dock-feather-ghost:\s*rgba\(0,\s*0,\s*0,\s*\.26\)/);
    expect(rule(workspace, ".chat-scroll")).toContain("var(--dock-feather-ghost)");
    /* 遮罩是 Web 独有构件，Android 没有对应消费者；写进 tokens.css 就是造没人消费的死数值，
       而且那份文件是生成物，手改会被 check_design_tokens 的逐字节比对拦下。 */
    expect(read(tokensPath)).not.toContain("dock-feather-ghost");
    expect(`${workspace}${read(glassPath)}`.match(/rgba\(/g) ?? []).toHaveLength(1);
  });

  it("悬浮坞压在分支面板、抽屉与选区浮条之下", () => {
    const dock = rule(workspace, ".chat-dock");
    const z = Number(dock.match(/z-index:\s*(\d+)/)?.[1]);
    expect(z, "坞必须浮在正文之上").toBeGreaterThan(0);
    const narrowWorkspace = narrow(workspace);
    const branch = Number(workspace.match(/\.branch-panel\s*\{[^}]*z-index:\s*(\d+)/s)?.[1]);
    const drawer = Number(rule(narrowWorkspace, ".app-sidebar").match(/z-index:\s*(\d+)/)?.[1]);
    const toolbar = Number(rule(workspace, ".selection-toolbar").match(/z-index:\s*(\d+)/)?.[1]);
    expect(z).toBeLessThan(branch);
    expect(z).toBeLessThan(drawer);
    expect(z).toBeLessThan(toolbar);
  });
});

describe("输入框焦点由玻璃卡承担，输入格自身不画框", () => {
  const workspace = read(workspacePath);
  const pages = read(pagesPath);
  const glass = read(glassPath);

  it("两档页面作用域都压过输入格上的直角 outline", () => {
    /* 缺陷形状：pages.css 与 workspace.css 各有一条 `textarea:focus-visible` 画 2px 直角框，
       特异性 0-2-1 压过 `.composer textarea { outline: none }` 的 0-1-1，于是 28px 圆角玻璃卡里
       留下一个方框。修法必须靠特异性而不是靠书写顺序，否则任何一次 import 调整都会让它复发。 */
    expect(pages).toMatch(/\.workspace-page\s+textarea:focus-visible/);
    expect(workspace).toMatch(/\.chat-screen\s+textarea:focus-visible/);
    const kill = workspace.match(
      /\.workspace-page \.composer textarea:focus-visible,\s*\.chat-screen \.composer textarea:focus-visible\s*\{([^}]*)\}/,
    );
    expect(kill, "缺少压过页面级焦点框的作用域规则").not.toBeNull();
    expect(kill?.[1]).toMatch(/outline:\s*none/);
  });

  it("卡片自己给出焦点环，去掉内框后仍有可见反馈", () => {
    const focus = rule(glass, ".composer:focus-within");
    expect(focus).toMatch(/0 0 0 2px/);
    expect(focus).toMatch(/var\(--green\)/);
  });
});

describe("空态说明句是提示而不是标题", () => {
  it("左对齐贴在输入下方，不再居中横在卡片里", () => {
    const help = rule(read(workspacePath), ".composer-help");
    expect(help, "居中的 11px 灰字读起来像表单系统提示").not.toMatch(/text-align:\s*center/);
  });
});
