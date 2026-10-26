import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * 对话阅读面与底部输入区必须共用同一列宽。两处各写一个数字时，
 * 784 / 720 的漂移会让输入区比正文窄一圈。
 *
 * 2026-10-25（T-2026-10-25-094）：列宽由 784 收到 684。`.chat-reading` 的 max-width
 * 含两侧各 32px 内边距，正文内宽 = 684 − 64 = 620px，15px 字号下一行约 41 个汉字
 * （判据 R2 的上限）。下面把这两个数一起钉住，避免只改外层、内宽又漂回去。
 */
const workspacePath = fileURLToPath(new URL("../../src/styles/workspace.css", import.meta.url));
const workspace = readFileSync(workspacePath, "utf8");

describe("对话列宽", () => {
  it("阅读面与输入区共享 --chat-column，且正文内宽落在 620px", () => {
    expect(workspace).toMatch(/\.chat-main\s*\{[^}]*--chat-column:\s*684px/s);
    expect(workspace).toMatch(/\.chat-reading\s*\{[^}]*max-width:\s*var\(--chat-column\)/s);
    expect(workspace).toMatch(/\.chat-reading\s*\{[^}]*padding:[^;]*var\(--s8\)/s);
    expect(workspace).toMatch(/\.chat-dock \.composer\s*\{[^}]*max-width:\s*var\(--chat-column\)/s);
  });

  it("两侧预留对称滚动条槽位，输入区不因滚动条偏移", () => {
    expect(workspace).toMatch(/\.chat-scroll\s*\{[^}]*scrollbar-gutter:\s*stable both-edges/s);
  });
});
