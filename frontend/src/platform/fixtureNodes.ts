import type { FixtureEnvelope } from "./RuntimePort";

/**
 * 阶段一采集夹具的负载形状（正文在 `.codex/scripts/dev/parity/capture_screen.py` 的写出侧）。
 *
 * 这里只做「读侧」的形状声明，不复制采集算法：bounds 已经是 dp（由采集器按 display density 归一），
 * 前端不再除密度，否则两边各归一一次，漂移就成了看不见的第二份正文。
 */
export interface FixtureNode {
  id: string;
  class: string;
  depth: number;
  parent: string;
  bounds_dp: [number, number, number, number];
  clickable: boolean;
  scrollable: boolean;
  enabled: boolean;
  text: string;
  desc: string;
}

export interface FixturePayload {
  device: { width_px: number; height_px: number; dpi: number; scale: number; viewport_dp: [number, number] };
  insets_dp: { status_bar_dp: number; nav_bar_dp: number };
  root_id: string;
  shot: string;
  shot_sha256: string;
  node_count: number;
  nodes: FixtureNode[];
}

function isNode(value: unknown): value is FixtureNode {
  const n = value as Record<string, unknown>;
  return typeof n === "object" && n !== null && typeof n.id === "string" && Array.isArray(n.bounds_dp);
}

/** 形状不合就返回 null：页面把它读成「这一屏还没有基线」，而不是拿空数组画一张像模像样的空屏。 */
export function payloadOf(envelope: FixtureEnvelope | null): FixturePayload | null {
  if (envelope === null) return null;
  const payload = envelope.payload as Partial<FixturePayload> | undefined;
  if (!payload || !Array.isArray(payload.nodes) || !payload.nodes.every(isNode)) return null;
  const { device, insets_dp: insets } = payload;
  if (!device || !Number.isFinite(device.scale) || !Array.isArray(device.viewport_dp)) return null;
  if (!insets || !Number.isFinite(insets.status_bar_dp)) return null;
  return {
    device: { ...device, viewport_dp: [device.viewport_dp[0] ?? 0, device.viewport_dp[1] ?? 0] },
    insets_dp: insets,
    root_id: typeof payload.root_id === "string" ? payload.root_id : "",
    shot: typeof payload.shot === "string" ? payload.shot : "",
    shot_sha256: typeof payload.shot_sha256 === "string" ? payload.shot_sha256 : "",
    node_count: payload.nodes.length,
    nodes: payload.nodes
  };
}

export function nodesById(nodes: FixtureNode[], id: string): FixtureNode[] {
  return nodes.filter((node) => node.id === id);
}

export function firstById(nodes: FixtureNode[], id: string): FixtureNode | null {
  return nodes.find((node) => node.id === id) ?? null;
}

/** 同 id 重复出现的容器（历史卡片、建议行）分组：子树按 `parent` 链取，顺序即实测顺序。 */
export interface FixtureGroup {
  node: FixtureNode;
  children: FixtureNode[];
}

/**
 * 一个节点的整棵子树（含孙代），按采集顺序返回。
 *
 * 两条界一起用才划得准。①`parent` 链：真机 dump 里有一批没有 resource-id 的中间容器，
 * 采集器只保留有身份或有文字的节点，深度会跳；设置页六个入口就是这么被「深度窗口」读成
 * 前一条的子节点的。②同 id 的下一次出现：列表项复用同一个 id，只按 `parent` 归集会把
 * 第二张卡的标题也归给第一张卡。
 */
export function subtreeOf(nodes: FixtureNode[], target: FixtureNode): FixtureNode[] {
  const from = nodes.indexOf(target);
  if (from < 0 || target.id === "") return [];
  let end = nodes.length;
  for (let i = from + 1; i < nodes.length; i += 1) {
    if (nodes[i]?.id === target.id) {
      end = i;
      break;
    }
  }
  const parentOf = new Map<string, string>();
  nodes.forEach((node) => {
    if (node.id !== "" && !parentOf.has(node.id)) parentOf.set(node.id, node.parent);
  });
  const reachesTarget = (node: FixtureNode): boolean => {
    let at: string | undefined = node.parent;
    for (let hop = 0; at !== undefined && at !== "" && hop < nodes.length; hop += 1) {
      if (at === target.id) return true;
      at = parentOf.get(at);
    }
    return false;
  };
  return nodes.slice(from + 1, end).filter((node) => node !== target && reachesTarget(node));
}

export function groupsById(nodes: FixtureNode[], id: string): FixtureGroup[] {
  return nodes
    .filter((node) => node.id === id)
    .map((node) => ({ node, children: subtreeOf(nodes, node) }));
}

export function pickChild(children: FixtureNode[], id: string): FixtureNode | null {
  return children.find((child) => child.id === id) ?? null;
}
