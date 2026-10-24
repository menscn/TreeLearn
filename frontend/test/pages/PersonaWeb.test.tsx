// @vitest-environment jsdom
import { act, createElement, type ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { WorkspaceDataProvider } from "../../src/components/WorkspaceDataProvider";
import { copyText } from "../../src/copy";
import { AppShell } from "../../src/layouts/AppShell";
import { WorkspaceRouter } from "../../src/pages/WorkspaceRouter";
import { FixtureRuntimeAdapter, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import { injectRuntime } from "../../src/platform/runtime";
import { PERSONA_ORDER, personaDemoKey, personaNameKey, personaSubKey } from "../../src/store/persona";
import { openView, resetWorkspace, setSettingsCategory, workspaceSnapshot } from "../../src/store/workspace";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

async function render(host: HTMLElement, node: ReactNode): Promise<Root> {
  let root!: Root;
  await act(async () => {
    root = createRoot(host);
    root.render(node);
    await new Promise((resolve) => setTimeout(resolve, 0));
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  return root;
}

async function press(host: HTMLElement, selector: string): Promise<void> {
  const el = host.querySelector(selector);
  expect(el, selector).not.toBeNull();
  await act(async () => {
    el!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function pressKey(key: string): Promise<void> {
  await act(async () => {
    document.dispatchEvent(new KeyboardEvent("keydown", { key, bubbles: true }));
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function workspaceShell(): ReactNode {
  return createElement(
    WorkspaceDataProvider,
    null,
    createElement(AppShell, null, createElement(WorkspaceRouter))
  );
}

function rowNames(host: HTMLElement): string[] {
  return Array.from(host.querySelectorAll("[data-persona-row]")).map((row) =>
    row.querySelector("[data-persona-row-label]")?.textContent ?? ""
  );
}

describe("Web 人设：chip、七行弹窗、介绍页与设置入口", () => {
  let restore: (() => void) | undefined;
  let host: HTMLElement;
  let root: Root | undefined;

  beforeEach(() => {
    window.history.replaceState(null, "", "/");
    resetWorkspace();
    restore = injectRuntime(new FixtureRuntimeAdapter(memoryFixtureSource(bundledEntries())));
    host = document.createElement("div");
    document.body.append(host);
  });

  afterEach(() => {
    act(() => {
      root?.unmount();
    });
    root = undefined;
    restore?.();
    restore = undefined;
    host.remove();
    window.history.replaceState(null, "", "/");
  });

  it("聊天输入行的人设 chip 恒有档名，未选过是默认", async () => {
    root = await render(host, workspaceShell());
    await act(async () => {
      openView("chat");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    const chip = host.querySelector('[data-node="chat_persona_chip"]');
    expect(chip).not.toBeNull();
    expect(chip?.textContent).toContain(copyText(personaNameKey("default")));
    expect(chip?.getAttribute("aria-label")).toContain(copyText("cd_persona_chip").replace("%1$s", copyText(personaNameKey("default"))));
  });

  it("点 chip 弹出七行弹窗：顺序、当前档对勾、底行了解每种风格", async () => {
    root = await render(host, workspaceShell());
    await act(async () => {
      openView("chat");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await press(host, '[data-node="chat_persona_chip"]');
    const popover = host.querySelector("[data-persona-popover]");
    expect(popover).not.toBeNull();
    expect(popover?.getAttribute("aria-label")).toBe(copyText("persona_picker_label"));
    expect(rowNames(host)).toEqual(PERSONA_ORDER.map((persona) => copyText(personaNameKey(persona))));
    const current = host.querySelector('[data-persona-row="default"]');
    expect(current?.getAttribute("aria-selected")).toBe("true");
    expect(current?.querySelector("[data-persona-check]")).not.toBeNull();
    expect(host.querySelector('[data-persona-intro]')?.textContent).toContain(copyText("persona_intro_more"));
  });

  it("点某一档即切换该主题档位，chip 更新并给一次提示，弹窗关闭", async () => {
    root = await render(host, workspaceShell());
    await act(async () => {
      openView("chat");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await press(host, '[data-node="chat_persona_chip"]');
    await press(host, '[data-persona-row="strict-mentor"]');
    expect(host.querySelector("[data-persona-popover]")).toBeNull();
    expect(host.querySelector('[data-node="chat_persona_chip"]')?.textContent)
      .toContain(copyText(personaNameKey("strict-mentor")));
    const feedback = host.querySelector("[data-persona-feedback]");
    expect(feedback?.textContent).toBe(
      copyText("persona_switched").replace("%1$s", copyText(personaNameKey("strict-mentor")))
    );
  });

  it("Escape 与外部点击都能关掉弹窗", async () => {
    root = await render(host, workspaceShell());
    await act(async () => {
      openView("chat");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await press(host, '[data-node="chat_persona_chip"]');
    expect(host.querySelector("[data-persona-popover]")).not.toBeNull();
    await pressKey("Escape");
    expect(host.querySelector("[data-persona-popover]")).toBeNull();

    await press(host, '[data-node="chat_persona_chip"]');
    await act(async () => {
      document.body.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    expect(host.querySelector("[data-persona-popover]")).toBeNull();
  });

  it("弹窗底行进入介绍页：七张卡、页脚说明、返回回聊天", async () => {
    root = await render(host, workspaceShell());
    await act(async () => {
      openView("chat");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await press(host, '[data-node="chat_persona_chip"]');
    await press(host, "[data-persona-intro]");
    expect(workspaceSnapshot().view).toBe("persona-intro");
    const intro = host.querySelector('[data-screen="persona-intro"]');
    expect(intro).not.toBeNull();
    expect(intro?.textContent).toContain(copyText("persona_intro_title"));
    expect(intro?.textContent).toContain(copyText("persona_intro_subtitle"));
    expect(intro?.textContent).toContain(copyText("persona_intro_lead"));
    const cards = Array.from(host.querySelectorAll("[data-persona-card]"));
    expect(cards).toHaveLength(PERSONA_ORDER.length);
    for (const persona of PERSONA_ORDER) {
      const card = host.querySelector(`[data-persona-card="${persona}"]`);
      expect(card?.textContent).toContain(copyText(personaNameKey(persona)));
      expect(card?.textContent).toContain(copyText(personaSubKey(persona)));
      expect(card?.textContent).toContain(copyText(personaDemoKey(persona)));
    }
    expect(intro?.textContent).toContain(copyText("persona_intro_footer"));
    expect(intro?.textContent).toContain(copyText("persona_intro_question"));
    await press(host, '[data-action="persona-intro-back"]');
    expect(workspaceSnapshot().view).toBe("chat");
  });

  it("设置页有回答风格介绍入口，点入同一介绍页", async () => {
    root = await render(host, workspaceShell());
    await act(async () => {
      openView("settings");
      setSettingsCategory("api-key");
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    const entry = host.querySelector("[data-persona-intro-entry]");
    expect(entry).not.toBeNull();
    expect(entry?.textContent).toContain(copyText("persona_intro_title"));
    expect(entry?.textContent).toContain(copyText("settings_persona_intro_sub"));
    await press(host, "[data-persona-intro-entry]");
    expect(workspaceSnapshot().view).toBe("persona-intro");
  });
});
