import { describe, expect, it } from "vitest";
import type { FixtureEnvelope } from "../../src/platform/RuntimePort";
import { bundledEntries } from "../../src/platform/bundledFixtures";
import {
  firstById, groupsById, nodesById, payloadOf, pickChild, subtreeOf
} from "../../src/platform/fixtureNodes";
import type { FixtureNode } from "../../src/platform/fixtureNodes";

function envelope(payload: unknown): FixtureEnvelope {
  return {
    schema_version: 1,
    screen: "home",
    source_commit: "0".repeat(40),
    captured_at_ms: 1700000000000,
    device: "ALP-AN00",
    payload
  } as FixtureEnvelope;
}

const DEVICE = { width_px: 1200, height_px: 2652, dpi: 520, scale: 3.25, viewport_dp: [369.2, 816] };
const INSETS = { status_bar_dp: 36.9, nav_bar_dp: 35.4 };

function node(id: string, depth: number, extra: Partial<FixtureNode> = {}): FixtureNode {
  return {
    id, class: "android.view.View", depth, parent: "", bounds_dp: [0, 0, 10, 10],
    clickable: false, scrollable: false, enabled: true, text: "", desc: "", ...extra
  };
}

describe("夹具负载的读侧形状", () => {
  it("缺设备事实或缺内嵌距时判「读不出这一屏」，不返回半份负载", () => {
    const nodes = [node("home_root", 2)];
    expect(payloadOf(envelope({ insets_dp: INSETS, root_id: "home_root", nodes }))).toBeNull();
    expect(payloadOf(envelope({ device: DEVICE, root_id: "home_root", nodes }))).toBeNull();
    expect(payloadOf(envelope({ device: DEVICE, insets_dp: INSETS, nodes: "不是数组" }))).toBeNull();
    expect(payloadOf(null)).toBeNull();
  });

  it("形状齐时 node_count 以实测节点数为准，不采信夹具自报的数", () => {
    const payload = payloadOf(envelope({
      device: DEVICE, insets_dp: INSETS, root_id: "home_root", node_count: 999,
      nodes: [node("home_root", 2), node("home_draft", 3)]
    }));
    expect(payload?.node_count).toBe(2);
    expect(payload?.root_id).toBe("home_root");
    expect(payload?.device.scale).toBe(3.25);
  });

  it("真实夹具逐条都能读出负载（写侧改字段名时这里先红）", () => {
    const bad: string[] = [];
    for (const [screen, text] of Object.entries(bundledEntries())) {
      const parsed = JSON.parse(text) as FixtureEnvelope;
      if (payloadOf(parsed) === null) bad.push(screen);
    }
    expect(bad).toEqual([]);
  });
});

describe("同 id 容器的分组读取", () => {
  // 形状照抄真机：卡片复用同一个 id，标题隔一层带 id 的容器，行标签的深度可以比子树还浅。
  const nodes = [
    node("home_recent_list", 4),
    node("home_topic_card", 5, { clickable: true, text: "第一张", parent: "home_recent_list" }),
    node("home_topic_top", 6, { parent: "home_topic_card" }),
    node("home_topic_title", 7, { text: "第一张", parent: "home_topic_top" }),
    node("home_topic_summary", 6, { text: "摘要一", parent: "home_topic_card" }),
    node("home_topic_card", 5, { clickable: true, text: "第二张", parent: "home_recent_list" }),
    node("home_topic_top", 6, { parent: "home_topic_card" }),
    node("home_topic_title", 7, { text: "第二张", parent: "home_topic_top" }),
    node("row_label", 6, { text: "不属于任何一张卡", parent: "home_recent_list" }),
    node("tab_bar", 4)
  ];

  it("groupsById 按采集顺序出组，子树只收 parent 链能走到本节点的行", () => {
    const groups = groupsById(nodes, "home_topic_card");
    expect(groups.map((g) => g.node.text)).toEqual(["第一张", "第二张"]);
    expect(groups[0]?.children.map((c) => c.text)).toEqual(["", "第一张", "摘要一"]);
    expect(groups[1]?.children.map((c) => c.text)).toEqual(["", "第二张"]);
    expect(nodesById(nodes, "home_topic_card").length).toBe(2);
    expect(firstById(nodes, "home_topic_card")?.text).toBe("第一张");
    expect(firstById(nodes, "no_such_id")).toBeNull();
  });

  it("pickChild 只在本组子树里找，不会串到下一张卡", () => {
    const groups = groupsById(nodes, "home_topic_card");
    expect(pickChild(groups[1]?.children ?? [], "home_topic_summary")).toBeNull();
    expect(pickChild(groups[0]?.children ?? [], "home_topic_title")?.text).toBe("第一张");
    expect(pickChild(groups[1]?.children ?? [], "home_topic_title")?.text).toBe("第二张");
  });

  it("没有 id 的节点取不出子树；同 id 的下一实例不会把后面的行吞进来", () => {
    expect(subtreeOf(nodes, node("", 5))).toHaveLength(0);
    const cards = nodesById(nodes, "home_topic_card");
    const second = cards[1];
    expect(second).toBeDefined();
    if (second !== undefined) {
      expect(subtreeOf(nodes, second).some((n) => n.text === "不属于任何一张卡")).toBe(false);
    }
  });
});
