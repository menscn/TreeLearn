import type { CSSProperties, ReactNode } from "react";
import { Icon, iconFor } from "../components/Icon";
import { Notice } from "../components/Notice";
import { useFixtureScreen } from "../hooks/useFixtureScreen";
import { useRoute } from "../hooks/useRoute";
import type { FixtureNode } from "../platform/fixtureNodes";
import type { ScreenName } from "../platform/RuntimePort";
import { linkFor, type ScreenLink } from "../store/screenLinks";

/**
 * 还没逐屏精修的屏：把采集夹具里的每个节点按它被量到的坐标摆回去。
 *
 * 判据来自夹具本身（`bounds_dp` 已是 dp，视口 369.23×816），这里不推算布局：
 * 文字、控件、图标的落点都取实测值，Web 只补 Android drawable 没被采集到的
 * 表面、字形与色彩（token 来自 tokens.css）。主页、学习库与对话走工作区，
 * 这里承接其余的采集屏与旧栈 parity 证据。
 */

const WRAPPERS = new Set([
  "content",
  "main_drawer_root",
  "shell_backdrop",
  "main_root",
  "shell_host",
  "statusBarBackground"
]);

/** 用转义写中文，避免生产代码里出现未经文案表的可见中文常量（本文件只比较夹具正文）。 */
const BACK_TEXT = "\u8FD4\u56DE";
const CLOSE_TEXT = "\u5173\u95ED";
const NOT_CONFIGURED_TEXT = "\u672A\u914D\u7F6E";

/** 这几屏在 Android 上是「一簇内容一张卡」，夹具没有卡面节点，于是按实测文本簇还原卡面。 */
const CLUSTER_CARD_SCREENS = new Set([
  "settingskey",
  "provideradmin",
  "modelsettings",
  "searchservicesettings",
  "voicesettings"
]);

interface Stage {
  dy: number;
  width: number;
  height: number;
}

interface Surface {
  key: string;
  style: CSSProperties;
  nodeId: string;
  dividers: number[];
}

function box(node: FixtureNode, dy: number): CSSProperties {
  const [left = 0, top = 0, width = 0, height = 0] = node.bounds_dp;
  return { left, top: top + dy, width, height };
}

function parentIds(nodes: FixtureNode[]): Map<string, string> {
  const map = new Map<string, string>();
  for (const node of nodes) {
    if (node.id !== "" && !map.has(node.id)) map.set(node.id, node.parent);
  }
  return map;
}

function isUnder(node: FixtureNode, rootId: string, parents: Map<string, string>): boolean {
  let at = node.parent;
  for (let hop = 0; at !== "" && hop < 40; hop += 1) {
    if (at === rootId) return true;
    at = parents.get(at) ?? "";
  }
  return false;
}

function hasArea(node: FixtureNode): boolean {
  return node.bounds_dp[2] > 0 && node.bounds_dp[3] > 0;
}

function titleOf(nodes: FixtureNode[]): FixtureNode | null {
  const named = nodes.find((node) => node.id.endsWith("_title") && node.text !== "");
  if (named !== undefined) return named;
  return nodes.find((node) => {
    const [, top = 0, , height = 0] = node.bounds_dp;
    return node.text !== "" && top < 100 && height >= 24 && height <= 30;
  }) ?? null;
}

function blankBackOf(nodes: FixtureNode[]): FixtureNode | null {
  if (nodes.some((node) => node.id.endsWith("_back") && node.clickable)) return null;
  return nodes.find((node) => node.clickable && (node.desc.startsWith(BACK_TEXT) || node.desc.startsWith(CLOSE_TEXT)))
    ?? null;
}

function isBackNode(node: FixtureNode): boolean {
  return node.id.endsWith("_back") && node.clickable;
}

/** 夹具里没被采集到的卡面按内容簇补：同一列、相邻缝隙不超过一行，且至少有一个 20dp 以上的标题。 */
function clusterSurfaces(nodes: FixtureNode[], stage: Stage): Surface[] {
  const eligible = nodes.filter((node) => {
    if (WRAPPERS.has(node.id)) return false;
    if (node.id.endsWith("_card")) return false;
    if (!hasArea(node)) return false;
    const [left = 0, top = 0, width = 0] = node.bounds_dp;
    if (top < 95 || left < 30 || left + width > stage.width - 4) return false;
    const cls = node.class;
    const isControl = cls.endsWith("TextView") || cls.endsWith("EditText") || cls.endsWith("Button")
      || cls.endsWith("Switch") || cls.endsWith("ImageView");
    return isControl && (node.text !== "" || node.desc !== "" || cls.endsWith("Switch"));
  });
  const sorted = [...eligible].sort((a, b) => a.bounds_dp[1] - b.bounds_dp[1]);
  const surfaces: Surface[] = [];
  let group: FixtureNode[] = [];
  let bottom = -Infinity;
  const flush = (): void => {
    if (group.length === 0) return;
    const anchored = group.some((node) => node.class.endsWith("TextView") && node.text !== ""
      && node.text !== "\u2713" && node.text !== "\u203A" && node.bounds_dp[3] >= 20);
    if (anchored) {
      const left = Math.max(8, Math.min(...group.map((node) => node.bounds_dp[0])) - 16);
      const right = Math.min(stage.width - 8, Math.max(...group.map((node) => node.bounds_dp[0] + node.bounds_dp[2])) + 16);
      const top = Math.min(...group.map((node) => node.bounds_dp[1]));
      const remove = group.find((node) => node.id.endsWith("_delete_link"));
      surfaces.push({
        key: `cluster-${top}`,
        nodeId: "",
        dividers: remove === undefined ? [] : [remove.bounds_dp[1] - 10 + stage.dy],
        style: { left, top: top + stage.dy - 16, width: right - left, height: bottom + 16 - (top - 16) }
      });
    }
    group = [];
  };
  for (const node of sorted) {
    const top = node.bounds_dp[1];
    // 删除链接与正文之间多留了一行，但在 Android 上仍属于同一张卡。
    const keepsCard = node.id.endsWith("_delete_link");
    if (group.length > 0 && top - bottom > 20 && !keepsCard) flush();
    group.push(node);
    bottom = group.length === 1 ? node.bounds_dp[1] + node.bounds_dp[3] : Math.max(bottom, node.bounds_dp[1] + node.bounds_dp[3]);
  }
  flush();
  return surfaces;
}

/** 抽屉里的「最近学习」是白底描边胶囊；夹具只给了文字行，这里补外圈。 */
function drawerRowSurfaces(nodes: FixtureNode[], stage: Stage): Surface[] {
  return nodes
    .filter((node) => node.clickable && node.text !== "" && node.bounds_dp[2] >= 200
      && node.bounds_dp[3] >= 40 && node.bounds_dp[3] <= 48)
    .map((node) => ({
      key: `drawer-row-${node.bounds_dp[1]}`,
      nodeId: "",
      dividers: [],
      style: { ...box(node, stage.dy), borderRadius: "var(--r-full)" }
    }));
}

/** 搜索服务页用一个浅琥珀色横条标出当前引擎，夹具没有对应节点。 */
function selectedEngineSurfaces(nodes: FixtureNode[], stage: Stage, screen: ScreenName): Surface[] {
  if (screen !== "searchservicesettings") return [];
  const check = nodes.find((node) => node.text === "\u2713");
  if (check === undefined) return [];
  const hint = nodes.find((node) => node.text.startsWith(NOT_CONFIGURED_TEXT)
    && node.bounds_dp[1] > check.bounds_dp[1] && node.bounds_dp[1] - check.bounds_dp[1] < 30);
  if (hint === undefined) return [];
  const [left = 0, top = 0, width = 0, height = 0] = hint.bounds_dp;
  return [{
    key: "selected-engine",
    nodeId: "",
    dividers: [],
    style: {
      left: left - 16,
      top: top - 8 + stage.dy,
      width: width + 32,
      height: height + 16,
      background: "color-mix(in srgb, var(--amber) 14%, transparent)",
      border: "0",
      borderRadius: "6px",
      boxShadow: "none"
    }
  }];
}

/** 连着的可点条目共用一张卡（设置页三段一组），分隔线画在条目交界处。 */
function entrySurfaces(nodes: FixtureNode[], stage: Stage): Surface[] {
  const entries = nodes
    .filter((node) => node.clickable && node.bounds_dp[2] >= 200 && node.bounds_dp[3] >= 48
      && linkFor(node.id) !== null)
    .sort((a, b) => a.bounds_dp[1] - b.bounds_dp[1]);
  const surfaces: Surface[] = [];
  let group: FixtureNode[] = [];
  let bottom = -Infinity;
  const flush = (): void => {
    if (group.length === 0) return;
    const first = group[0];
    if (first === undefined) return;
    const [left = 0, top = 0, width = 0] = first.bounds_dp;
    surfaces.push({
      key: `entry-${top}`,
      nodeId: "",
      dividers: group.slice(1).map((node) => node.bounds_dp[1] + stage.dy),
      style: { left, top: top + stage.dy, width, height: bottom - top }
    });
    group = [];
  };
  for (const node of entries) {
    const top = node.bounds_dp[1];
    if (group.length > 0 && top - bottom > 4) flush();
    group.push(node);
    bottom = group.length === 1 ? node.bounds_dp[1] + node.bounds_dp[3] : Math.max(bottom, node.bounds_dp[1] + node.bounds_dp[3]);
  }
  flush();
  return surfaces;
}

function buildSurfaces(nodes: FixtureNode[], stage: Stage, screen: ScreenName): Surface[] {
  const explicit = nodes
    .filter((node) => node.id.endsWith("_card") && hasArea(node))
    .map((node) => ({
      key: `card-${node.id}-${node.bounds_dp[0]}-${node.bounds_dp[1]}`,
      nodeId: node.id,
      dividers: [],
      style: box(node, stage.dy)
    }));
  const entries = entrySurfaces(nodes, stage);
  const clusters = CLUSTER_CARD_SCREENS.has(screen) ? clusterSurfaces(nodes, stage) : [];
  const rows = screen === "homedrawer" ? drawerRowSurfaces(nodes, stage) : [];
  const selected = selectedEngineSurfaces(nodes, stage, screen);
  return [...explicit, ...entries, ...clusters, ...selected, ...rows];
}

function textClass(node: FixtureNode, isTitle: boolean): string {
  if (isTitle) return "appbar-title snap-display";
  const text = node.text;
  const [left = 0, , width = 0, height = 0] = node.bounds_dp;
  if (text === "\u2713") return "snap-mark";
  if (text === "\u203A") return "snap-chevron-text";
  if (node.id.endsWith("_state") || (height > 22 && height < 24.5 && width < 100 && left > 150)) return "snap-chip";
  if (node.id.endsWith("_chip")) return "snap-chip snap-chip-plain";
  if (node.id.endsWith("_delete_link")) return "snap-danger";
  if (node.id.endsWith("_row_title")) return "snap-row-title";
  if (text.startsWith(NOT_CONFIGURED_TEXT)) return "snap-cap snap-warn";
  if (node.id.endsWith("_rows_section")) return "snap-section snap-section-strong";
  if (height >= 29) {
    if (!node.clickable && left <= 24 && text.length < 12) return "snap-section";
    return left <= 24 ? "snap-para snap-para-muted" : "snap-para";
  }
  if (height > 23) {
    if (left <= 24) return "snap-meta-line";
    return "snap-display";
  }
  if (height > 20.5) return "snap-row-title";
  if (height > 16) return narrow(text, width, 15) ? "snap-body snap-tight" : "snap-body";
  return narrow(text, width, 13) ? "snap-cap snap-tight" : "snap-cap";
}

/** 单行文字在当前宽度里放不下时，Android 侧用的是更小的一档字号，而不是把字裁掉。 */
function narrow(text: string, width: number, fontSize: number): boolean {
  let units = 0;
  for (const ch of text) units += /[\u0000-\u00ff]/.test(ch) ? 0.55 : 1;
  return units * fontSize > width * 0.98;
}

function truncate(text: string): string {
  return text.length > 8 ? `${text.slice(0, 8)}\u2026` : text;
}

export function SnapshotScreen({ screen }: { screen: ScreenName }) {
  const { status, payload } = useFixtureScreen(screen);
  const route = useRoute();
  if (payload === null) {
    return status === "loading" ? null : <Notice copyKey="state_fixture_unavailable" />;
  }
  const nodes = payload.nodes;
  // 夹具的 status_bar_dp 记的是「内容顶到状态栏的距离」，不是状态栏自身高度；
  // 系统栏在 H5 里不画（外壳没有状态栏），所以把内容整体上移到 0，只保留内容区高度。
  const statusInset = nodes.find((node) => node.id === "statusBarBackground")?.bounds_dp[3]
    ?? payload.insets_dp.status_bar_dp;
  const stage: Stage = {
    dy: -statusInset,
    width: payload.device.viewport_dp[0],
    height: payload.device.viewport_dp[1] - statusInset - payload.insets_dp.nav_bar_dp
  };
  const parents = parentIds(nodes);
  const homeLayer = nodes.filter((node) => isUnder(node, "main_root", parents));
  const drawerScreen = homeLayer.some((node) => (node.text !== "" || node.desc !== "") && hasArea(node))
    && nodes.some((node) => isUnder(node, "main_drawer_root", parents) && !isUnder(node, "main_root", parents)
      && (node.text !== "" || node.desc !== ""));
  const content = drawerScreen
    ? nodes.filter((node) => !isUnder(node, "main_root", parents) && isUnder(node, "main_drawer_root", parents))
    : nodes;
  const title = titleOf(nodes);
  const namedBack = nodes.find((node) => isBackNode(node)) ?? null;
  const blankBack = blankBackOf(nodes);
  const backNode = namedBack ?? blankBack;

  const open = (link: ScreenLink): void => {
    if (link === null) return;
    if (link.kind === "screen") route.open(link.screen);
    else if (link.kind === "tab") route.openTab(link.screen);
    else route.back();
  };

  const drawNode = (node: FixtureNode, layer: "content" | "underlay"): ReactNode => {
    if (WRAPPERS.has(node.id) || !hasArea(node)) return null;
    if (node.id.endsWith("_card")) return null;
    if (node.id === "drawer_heatmap_cells") {
      return (
        <div className="snap-heatmap" style={box(node, stage.dy)} data-node={layer === "content" ? node.id : undefined} key={`cells-${node.id}`}>
          {Array.from({ length: 42 }, (_, index) => <span className={index === 3 ? "is-lit" : ""} key={index} />)}
        </div>
      );
    }
    if (node.id === "tree_canvas") {
      const subject = nodes.find((candidate) => candidate.id === "tree_subtitle")?.text ?? node.desc;
      return (
        <div className="snap-canvas" style={box(node, stage.dy)} key="tree-canvas">
          <span className="snap-canvas-pill">{truncate(subject)}</span>
          <span className="snap-canvas-shoot" />
        </div>
      );
    }
    if (node.id.endsWith("_rule") && node.text === "" && node.desc === "") {
      return <span className="snap-rule" key={`rule-${node.id}`} style={box(node, stage.dy)} />;
    }
    if (node === backNode) {
      return (
        <button
          aria-label={node.desc}
          className="node-tap snap-icon snap-back"
          data-node={namedBack === null ? "state_back" : node.id}
          key={`back-${node.id}`}
          onClick={() => route.back()}
          style={box(node, stage.dy)}
          type="button"
        >
          <Icon name={node.desc.startsWith(CLOSE_TEXT) ? "chat_close" : "nav_back"} />
        </button>
      );
    }
    const glyph = iconFor(node);
    if (glyph !== null && node.text === "") {
      const tool = ["tree_zoom_in", "tree_zoom_out", "tree_fit_tool", "tree_center"].includes(node.id);
      const save = node.id === "settings_key_save" || node.id === "provider_key_save";
      const linked = layer === "content" && linkFor(node.id) !== null;
      return (
        <span
          aria-label={node.desc === "" ? undefined : node.desc}
          className={`snap-icon ${tool ? "is-tool" : ""} ${save ? "is-save" : ""}`}
          data-node={!linked && layer === "content" && node.id !== "" ? node.id : undefined}
          key={`icon-${node.id}-${node.bounds_dp[1]}`}
          role={node.desc === "" ? undefined : "img"}
          style={box(node, stage.dy)}
        >
          <Icon name={glyph} />
        </span>
      );
    }
    if (node.class.endsWith("Switch")) {
      // 夹具没有 checked 字段；采集时只有搜索服务的「联网搜索」是关闭态。
      const on = screen !== "searchservicesettings";
      return (
        <span aria-label={node.desc} className={`snap-switch ${on ? "is-on" : "is-off"}`} role="img" key={`switch-${node.bounds_dp[1]}`} style={box(node, stage.dy)}>
          <span className="snap-knob" />
        </span>
      );
    }
    if (node.class.endsWith("EditText")) {
      return (
        <span className="snap-field" data-node={layer === "content" && node.id !== "" ? node.id : undefined} key={`field-${node.bounds_dp[1]}`} style={box(node, stage.dy)}>
          {node.text}
        </span>
      );
    }
    if (node.class.endsWith("Button") && node.text !== "") {
      return (
        <span className="snap-button" key={`button-${node.bounds_dp[1]}`} style={box(node, stage.dy)}>
          {node.text}
        </span>
      );
    }
    if (node.text !== "") {
      const linked = layer === "content" && linkFor(node.id) !== null;
      const drawerRow = screen === "homedrawer" && layer === "content" && node.clickable;
      const style = box(node, stage.dy);
      if (node.id.endsWith("_section") && node.bounds_dp[0] === 0) style.paddingLeft = 20;
      return (
        <span
          className={`snap-text ${textClass(node, node === title)}${drawerRow ? " snap-drawer-row" : ""}`}
          data-node={!linked && layer === "content" && node.id !== "" ? node.id : undefined}
          key={`text-${node.bounds_dp[1]}-${node.bounds_dp[0]}-${node.text.slice(0, 8)}`}
          style={style}
        >
          <span className="snap-label">{node.text}</span>
        </span>
      );
    }
    return null;
  };

  const overlays = content
    .filter((node) => node.clickable && hasArea(node) && !isBackNode(node) && node !== blankBack && linkFor(node.id) !== null)
    .map((node) => (
      <button
        aria-label={node.id}
        className="node-tap snap-hit"
        data-node={node.id}
        key={`hit-${node.id}-${node.bounds_dp[1]}`}
        onClick={() => open(linkFor(node.id))}
        style={box(node, stage.dy)}
        type="button"
      />
    ));

  const surfaces = buildSurfaces(content, stage, screen);
  const drawerWidth = content
    .filter((node) => !WRAPPERS.has(node.id) && hasArea(node)
      && (node.text !== "" || node.desc !== "" || node.id.endsWith("_card")))
    .reduce((widest, node) => Math.max(widest, node.bounds_dp[0] + node.bounds_dp[2]), 0) + 20;
  return (
    <section
      className="screen snap-screen"
      data-screen={screen}
      style={{ width: stage.width, height: stage.height }}
    >
      {drawerScreen
        ? (
          <div className="snap-underlay">
            {nodes.filter((node) => isUnder(node, "main_root", parents)).map((node) => drawNode(node, "underlay"))}
          </div>
        )
        : null}
      {drawerScreen
        ? (
          <>
            <div className="snap-scrim" style={{ left: drawerWidth, top: 0, width: stage.width - drawerWidth, height: stage.height }} />
            <div className="snap-pane" style={{ left: 0, top: 0, width: drawerWidth, height: stage.height }} />
          </>
        )
        : null}
      {surfaces.map((surface) => (
        <div className="snap-surface" data-node={surface.nodeId === "" ? undefined : surface.nodeId} key={surface.key} style={surface.style}>
          {surface.dividers.map((top) => (
            <span className="snap-divider" key={top} style={{ top: top - (surface.style.top as number) }} />
          ))}
        </div>
      ))}
      {content.map((node) => drawNode(node, "content"))}
      {overlays}
    </section>
  );
}
