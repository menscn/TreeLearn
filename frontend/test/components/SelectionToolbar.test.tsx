// @vitest-environment jsdom
import { readFileSync } from "node:fs";
import { act, createElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  SelectionToolbar,
  TOOLBAR_SIZE,
  menuPosition,
  type SelectionSnapshot
} from "../../src/components/SelectionToolbar";
import { copyText } from "../../src/copy";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const RECT = { left: 400, top: 500, right: 600, bottom: 520 };
const SELECTION: SelectionSnapshot = { text: "核心先说结论", rect: RECT };
const ACTIONS = ["ask_ai", "fork", "highlight", "note", "copy", "select_all"] as const;
const DISABLED = ["fork", "highlight", "note"] as const;

let root: Root | null = null;
let host: HTMLElement | null = null;

async function render(selection: SelectionSnapshot | null): Promise<HTMLElement> {
  host = document.createElement("div");
  document.body.appendChild(host);
  await act(async () => {
    root = createRoot(host!);
    root.render(createElement(SelectionToolbar, {
      selection,
      onAskAi: () => undefined,
      onCopy: () => undefined,
      onSelectAll: () => undefined
    }));
  });
  return host;
}

afterEach(async () => {
  await act(async () => {
    root?.unmount();
  });
  host?.remove();
  root = null;
  host = null;
});

function button(name: (typeof ACTIONS)[number], el: HTMLElement): HTMLButtonElement {
  const found = el.querySelector<HTMLButtonElement>(`[data-word-action="${name}"]`);
  expect(found, name).not.toBeNull();
  return found!;
}

describe("选中文本浮条", () => {
  it("六格按问 AI / 新建主题 / 高亮 / 批注 / 复制 / 全选排列，只画图标", async () => {
    const el = await render(SELECTION);
    const bar = el.querySelector('[role="toolbar"]');
    expect(bar).not.toBeNull();
    const buttons = Array.from(bar!.querySelectorAll("button"));
    expect(buttons.map((b) => b.dataset.wordAction)).toEqual([...ACTIONS]);
    expect(buttons.every((b) => b.textContent === "")).toBe(true);
    expect(buttons.every((b) => b.querySelector("svg") !== null)).toBe(true);
    expect(bar!.querySelectorAll(".selection-toolbar-sep")).toHaveLength(5);
  });

  it("可访问名来自文案表；未接通的三种说明尚未接通且不可点", async () => {
    const el = await render(SELECTION);
    expect(el.querySelector('[role="toolbar"]')!.getAttribute("aria-label"))
      .toBe(copyText("chat_word_menu"));
    expect(button("ask_ai", el).getAttribute("aria-label")).toBe(copyText("chat_word_ask_ai_desc"));
    expect(button("copy", el).getAttribute("aria-label")).toBe(copyText("chat_word_copy_desc"));
    expect(button("select_all", el).getAttribute("aria-label"))
      .toBe(copyText("chat_word_select_all_desc"));
    for (const action of DISABLED) {
      expect(button(action, el).disabled, action).toBe(true);
      expect(button(action, el).getAttribute("aria-label"), action)
        .toContain(copyText("chat_action_unavailable"));
    }
  });

  it("可用的三格各自回调，并带上选区文本；禁用的三格不回调", async () => {
    const onAskAi = vi.fn();
    const onCopy = vi.fn();
    const onSelectAll = vi.fn();
    host = document.createElement("div");
    document.body.appendChild(host);
    root = createRoot(host);
    await act(async () => {
      root!.render(createElement(SelectionToolbar, {
        selection: SELECTION,
        onAskAi,
        onCopy,
        onSelectAll
      }));
    });
    for (const action of ACTIONS) {
      await act(async () => {
        button(action, host!).dispatchEvent(new MouseEvent("click", { bubbles: true }));
      });
    }
    expect(onAskAi).toHaveBeenCalledTimes(1);
    expect(onAskAi).toHaveBeenCalledWith(SELECTION.text);
    expect(onCopy).toHaveBeenCalledTimes(1);
    expect(onCopy).toHaveBeenCalledWith(SELECTION.text);
    expect(onSelectAll).toHaveBeenCalledTimes(1);
    expect(onSelectAll).toHaveBeenCalledWith(SELECTION.text);
  });

  it("按下浮条不塌陷浏览器选区", async () => {
    const el = await render(SELECTION);
    const bar = el.querySelector<HTMLElement>('[role="toolbar"]')!;
    const down = new MouseEvent("mousedown", { bubbles: true, cancelable: true });
    bar.dispatchEvent(down);
    expect(down.defaultPrevented).toBe(true);
  });

  it("没有选区时不渲染浮条", async () => {
    const el = await render(null);
    expect(el.querySelector('[role="toolbar"]')).toBeNull();
  });
});

describe("menuPosition", () => {
  const size = { width: TOOLBAR_SIZE.width, height: TOOLBAR_SIZE.height };
  const viewport = { width: 1200, height: 800 };

  it("默认贴在选区上方 8px，并水平居中", () => {
    expect(menuPosition(RECT, size, viewport)).toEqual({
      top: RECT.top - 8 - size.height,
      left: (RECT.left + RECT.right) / 2 - size.width / 2,
      placement: "above"
    });
  });

  it("上方放不下时翻到选区下方", () => {
    const nearTop = { left: 400, top: 20, right: 600, bottom: 40 };
    expect(menuPosition(nearTop, size, viewport)).toEqual({
      top: 48,
      left: (RECT.left + RECT.right) / 2 - size.width / 2,
      placement: "below"
    });
  });

  it("左右夹在视口内，不越出 8px 边距", () => {
    const atLeft = menuPosition({ left: 0, top: 500, right: 10, bottom: 520 }, size, viewport);
    const atRight = menuPosition({ left: 300, top: 500, right: 320, bottom: 520 }, size, { width: 320, height: 800 });
    expect(atLeft.left).toBe(8);
    expect(atRight.left).toBe(320 - 8 - size.width);
  });

  it("翻到下方后也不越出视口底边", () => {
    const short = { width: 1200, height: 300 };
    const atBottom = menuPosition({ left: 400, top: 20, right: 600, bottom: 250 }, size, short);
    expect(atBottom.placement).toBe("below");
    expect(atBottom.top).toBe(short.height - 8 - size.height);
  });
});

describe("浮条样式", () => {
  // vitest 的 root 是 frontend/；jsdom 环境里 import.meta.url 不是 file:，只能按 root 相对读。
  const css = readFileSync("src/styles/workspace.css", "utf8");
  const rule = (selector: string): string => {
    const at = css.indexOf(`${selector} {`);
    expect(at, `missing rule: ${selector}`).toBeGreaterThanOrEqual(0);
    const open = css.indexOf("{", at);
    return css.slice(open + 1, css.indexOf("}", open));
  };

  it("每格 44×44，分隔线 1×20，热区不缩", () => {
    const cell = rule(".selection-toolbar button");
    expect(cell).toMatch(/width:\s*44px/);
    expect(cell).toMatch(/height:\s*44px/);
    const sep = rule(".selection-toolbar-sep");
    expect(sep).toMatch(/width:\s*1px/);
    expect(sep).toMatch(/height:\s*20px/);
  });

  it("玻璃控制面只消费 token，不写裸色值", () => {
    const bar = rule(".selection-toolbar");
    expect(bar).toContain("var(--glass-control)");
    expect(bar).toContain("var(--glass-blur)");
    expect(bar).not.toMatch(/#[0-9a-fA-F]{3,8}/);
  });
});
