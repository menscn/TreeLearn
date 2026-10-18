import { describe, expect, it } from "vitest";
import {
  canGoBack,
  currentScreen,
  goBack,
  navigate,
  replaceStack,
  routeSnapshot,
  subscribeRoute
} from "../../src/store/screenRoute";

describe("换屏栈与 Android 单 Activity 的 swap 对齐", () => {
  it("navigate 压栈、goBack 缩栈、栈底不再退", () => {
    replaceStack("home");
    expect(currentScreen()).toBe("home");
    expect(canGoBack()).toBe(false);
    navigate("chat");
    navigate("settings");
    expect(routeSnapshot().stack).toEqual(["home", "chat", "settings"]);
    expect(canGoBack()).toBe(true);
    goBack();
    expect(currentScreen()).toBe("chat");
    goBack();
    goBack();
    expect(currentScreen()).toBe("home");
    expect(canGoBack()).toBe(false);
  });

  it("同屏重复点击不压栈（否则返回要按好几次才退得掉）", () => {
    replaceStack("home");
    navigate("home");
    navigate("home");
    expect(routeSnapshot().stack).toEqual(["home"]);
  });

  it("换 tab 把栈折叠成单屏，回到那一 tab 时不会看见上一个 tab 的残栈", () => {
    replaceStack("home");
    navigate("chat");
    navigate("settings");
    replaceStack("library");
    expect(routeSnapshot().stack).toEqual(["library"]);
    expect(canGoBack()).toBe(false);
  });

  it("订阅者收到每一次栈顶变化，退订之后不再收到", () => {
    replaceStack("home");
    const seen: string[] = [];
    const off = subscribeRoute(() => seen.push(currentScreen()));
    navigate("chat");
    navigate("navigation");
    goBack();
    off();
    navigate("settings");
    expect(seen).toEqual(["chat", "navigation", "chat"]);
  });
});
