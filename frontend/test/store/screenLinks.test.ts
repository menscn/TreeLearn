import { describe, expect, it } from "vitest";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import type { FixtureNode } from "../../src/platform/fixtureNodes";
import { linkFor, screenLinkKeys } from "../../src/store/screenLinks";

/** 夹具正文 → 节点表，直接从包内字节读，不另建一份样例数据（样例数据会假绿）。 */
function nodesOf(screen: string): FixtureNode[] {
  const envelope = JSON.parse(bundledEntries()[screen] ?? "{}") as {
    payload?: { nodes?: FixtureNode[] };
  };
  return envelope.payload?.nodes ?? [];
}

const SCREENS = Object.keys(bundledEntries());

describe("换屏映射表", () => {
  it("键的读法：tab 归 tab、*_back 归返回、其余按表换屏、表外没有映射", () => {
    expect(linkFor("tab_home")).toEqual({ kind: "tab", screen: "home" });
    expect(linkFor("tab_library")).toEqual({ kind: "tab", screen: "library" });
    expect(linkFor("settings_back")).toEqual({ kind: "back" });
    expect(linkFor("tree_back")).toEqual({ kind: "back" });
    expect(linkFor("chat_settings")).toEqual({ kind: "screen", screen: "settings" });
    expect(linkFor("drawer_heatmap_card")).toBeNull();
  });

  /**
   * Android 侧出过一次同类缺陷：setter 写了、没有任何调用者绑上去，入口在手面上是死的
   * （MainActivity 的五个子设置项）。Web 侧同构的错误是「映射表里有这个键，夹具里根本没有
   * 那个可点节点」——于是页面画得出字，但那条入口永远点不动。这里按夹具逐条量测。
   */
  it("每条映射在真实夹具里都落在一个可点节点上", () => {
    const dead: string[] = [];
    for (const key of screenLinkKeys()) {
      const hit = SCREENS.some((screen) =>
        nodesOf(screen).some((node) => node.id === key && node.clickable));
      if (!hit) dead.push(key);
    }
    expect(dead).toEqual([]);
  });

  it("每条映射的目标屏也已经在夹具名单里（点得动但读不到基线同样是死路）", () => {
    const missing: string[] = [];
    for (const key of screenLinkKeys()) {
      const link = linkFor(key);
      if (link === null || link.kind === "back") continue;
      if (!SCREENS.includes(link.screen)) missing.push(`${key} → ${link.screen}`);
    }
    expect(missing).toEqual([]);
  });

  it("从主页出发能走到的屏，逐条点名", () => {
    const reachable = new Set<string>();
    for (const key of screenLinkKeys()) {
      const link = linkFor(key);
      if (link !== null && link.kind === "screen") reachable.add(link.screen);
    }
    expect([...reachable].sort()).toEqual([
      "chat", "filemanager", "homedrawer", "modelsettings", "navigation",
      "provideradmin", "searchservicesettings", "settings", "settingskey", "treecanvas", "voicesettings"
    ]);
  });
});
