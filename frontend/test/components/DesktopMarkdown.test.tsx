// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it } from "vitest";
import { DesktopMarkdown } from "../../src/components/DesktopMarkdown";

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

it("安全展示表格、链接地址和公式，不执行 HTML 或不可信协议", async () => {
  const host = document.createElement("div"); document.body.append(host);
  const root = createRoot(host);
  await act(async () => root.render(<DesktopMarkdown text={"| 概念 | 参考 |\n| --- | --- |\n| 概率 | [规范](https://example.com/doc) |\n\n[危险](javascript:alert(1))\n<img src=x>\n公式 $x^2$"} />));
  expect(host.querySelectorAll("table thead th")).toHaveLength(2);
  expect(host.querySelectorAll("table tbody td")).toHaveLength(2);
  expect(host.textContent).toContain("规范 (https://example.com/doc)");
  expect(host.textContent).toContain("javascript:alert(1)");
  expect(host.textContent).toContain("<img src=x>");
  expect(host.textContent).toContain("$x^2$");
  expect(host.querySelector("a")).toBeNull();
  expect(host.querySelector("img")).toBeNull();
  await act(async () => root.unmount()); host.remove();
});
