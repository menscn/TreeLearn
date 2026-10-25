// @vitest-environment jsdom
import { act, createElement, useRef } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it } from "vitest";
import { Composer } from "../../src/components/Composer";
import { copyText } from "../../src/copy";

/**
 * 输入坞说明句（T-2026-10-25-087）。
 *
 * 用户拍板 A：说明是给「还不知道这里能不能发」的人看的，所以只在空态出现，一旦开始打字就退场。
 * 退场不能顺手带走能力边界——两个动作按钮必须仍然禁用、仍然在可访问名里写明「尚未接通」。
 */
(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let root: Root | null = null;
let host: HTMLElement | null = null;

function Host({ value }: { value: string }) {
  const inputRef = useRef<HTMLTextAreaElement>(null);
  return createElement(Composer, {
    value,
    placeholder: "输入你的根问题",
    model: "V4.1 Flash",
    inputRef,
    onChange: () => undefined
  });
}

async function render(value: string): Promise<HTMLElement> {
  host = document.createElement("div");
  document.body.appendChild(host);
  await act(async () => {
    root = createRoot(host!);
    root.render(createElement(Host, { value }));
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
  it("空草稿时说明句在场，文案来自 copy 表", async () => {
    const el = await render("");
    const note = el.querySelector(".composer-help");
    expect(note, "空态必须给出这条说明").not.toBeNull();
    expect(note?.textContent).toBe(copyText("chat_composer_note"));
  });

  it("纯空白也算空态：trim 之后才决定退场", async () => {
    const el = await render("   ");
    expect(el.querySelector(".composer-help")).not.toBeNull();
  });

  it("开始打字就退场，卡片里只剩输入与动作", async () => {
    const el = await render("你好");
    expect(el.querySelector(".composer-help"), "有草稿时不许再占一行说明").toBeNull();
    expect((el.querySelector(".composer textarea") as HTMLTextAreaElement).value).toBe("你好");
    expect(el.querySelectorAll(".composer-row .chip").length, "模型 chip 仍在").toBe(1);
  });

  it("说明退场后能力边界仍在：两个动作都禁用并写明尚未接通", async () => {
    const el = await render("你好");
    const send = el.querySelector<HTMLButtonElement>(".composer-send");
    const attach = el.querySelector<HTMLButtonElement>(".composer-attach");
    expect(send?.disabled).toBe(true);
    expect(attach?.disabled).toBe(true);
    const unavailable = copyText("chat_action_unavailable");
    expect(send?.getAttribute("aria-label")).toContain(unavailable);
    expect(attach?.getAttribute("aria-label")).toContain(unavailable);
  });
});
