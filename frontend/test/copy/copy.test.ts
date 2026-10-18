import { describe, expect, it } from "vitest";
import { MissingCopyKey, copyKeys, copyText, hasCopy, interfaceLocale } from "../../src/copy";

describe("文案表", () => {
  it("每个键都取到非空文案，且文案不等于键名", () => {
    const keys = copyKeys();
    expect(keys.length).toBeGreaterThan(0);
    expect(keys).toEqual([...keys].sort());
    for (const key of keys) {
      const text = copyText(key);
      expect(text.length).toBeGreaterThan(0);
      expect(text).not.toBe(key);
    }
  });

  it("缺键时大声失败并在消息里点名该键", () => {
    const missing = "tab_settings";
    expect(hasCopy(missing)).toBe(false);
    expect(() => copyText(missing)).toThrow(MissingCopyKey);
    expect(() => copyText(missing)).toThrow(new RegExp(missing));
  });

  it("界面语言只有一个，本期不做第二语言齐备", () => {
    expect(interfaceLocale).toBe("zh-CN");
  });
});
