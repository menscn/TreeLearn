// @vitest-environment jsdom
import { act, createElement, useRef } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it } from "vitest";
import { Composer } from "../../src/components/Composer";
import { copyText } from "../../src/copy";

/**
 * 输入坞说明句（T-2026-10-25-087，2026-10-25 的 098 改了它的宿主约定）。
 *
 * 087 时说明句是常驻组件的默认文案：对话页与首页各画一条。098 把同一句「尚未接通」从第二屏撤掉后，
 * 约定变成「宿主给 `note` 才画」——首页给，对话页不给。出现条件仍是空草稿：一旦开始打字，
 * 那句话回答的问题就不存在了，但能力边界不能跟着退场：两个动作按钮必须仍然禁用、仍然在可访问名里
 * 写明「尚未接通」。说明句的位置也从卡片内部移到了卡外（见 `workspace.css` 的 `.composer-help`）。
 */
(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let root: Root | null = null;
let host: HTMLElement | null = null;

function Host({ value, note }: { value: string; note?: string }) {
  const inputRef = useRef<HTMLTextAreaElement>(null);
  return createElement(Composer, {
    value,
    placeholder: "输入你的根问题",
    model: "V4.1 Flash",
    inputRef,
    note,
    onChange: () => undefined
  });
}

async function render(value: string, note?: string): Promise<HTMLElement> {
  host = document.createElement("div");
  document.body.appendChild(host);
  await act(async () => {
    root = createRoot(host!);
    root.render(createElement(Host, { value, note }));
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

describe("输入坞说明句只在空态出现", () => {
  it("宿主给了 note：空草稿时说明句在卡外，文案就是宿主给的那句", async () => {
    const note = copyText("home_composer_note");
    const el = await render("", note);
    const help = el.querySelector(".composer-help");
    expect(help, "空态必须给出这条说明").not.toBeNull();
    expect(help?.textContent).toBe(note);
    expect(help?.closest(".composer"), "说明句不再占卡片内部的地面").toBeNull();
  });

  it("纯空白也算空态：trim 之后才决定退场", async () => {
    const el = await render("   ", "说明");
    expect(el.querySelector(".composer-help")).not.toBeNull();
  });

  it("开始打字就退场，卡片里只剩输入与动作", async () => {
    const el = await render("你好", "说明");
    expect(el.querySelector(".composer-help"), "有草稿时不许再占一行说明").toBeNull();
    expect((el.querySelector(".composer textarea") as HTMLTextAreaElement).value).toBe("你好");
    expect(el.querySelectorAll(".composer-row .chip").length, "模型 chip 仍在").toBe(1);
  });

  it("宿主不给 note 就一句都不画：对话页不重复同一句尚未接通", async () => {
    const el = await render("");
    expect(el.querySelector(".composer-help")).toBeNull();
  });

  it("说明退场后能力边界仍在：两个动作都禁用并写明尚未接通", async () => {
    const el = await render("你好", "说明");
    const send = el.querySelector<HTMLButtonElement>(".composer-send");
    const attach = el.querySelector<HTMLButtonElement>(".composer-attach");
    expect(send?.disabled).toBe(true);
    expect(attach?.disabled).toBe(true);
    const unavailable = copyText("chat_action_unavailable");
    expect(send?.getAttribute("aria-label")).toContain(unavailable);
    expect(attach?.getAttribute("aria-label")).toContain(unavailable);
  });
});
