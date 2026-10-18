import { subtreeOf } from "../platform/fixtureNodes";
import type { FixtureNode } from "../platform/fixtureNodes";
import type { ScreenName } from "../platform/RuntimePort";
import { copyText } from "../copy";
import { linkFor } from "../store/screenLinks";
import { useFixtureScreen } from "../hooks/useFixtureScreen";
import { useRoute } from "../hooks/useRoute";
import { NodeTap } from "../components/NodeTap";
import { Notice } from "../components/Notice";

/** 一条可点容器在它自己与子树里能读到的文字，去重后按采集顺序排（同一行常有两份同名标签）。 */
function textsOf(node: FixtureNode, nodes: FixtureNode[]): string[] {
  const out: string[] = [];
  for (const candidate of [node, ...subtreeOf(nodes, node)]) {
    const label = candidate.text || candidate.desc;
    if (label !== "" && !out.includes(label)) out.push(label);
  }
  return out;
}

function isTapTarget(node: FixtureNode): boolean {
  return node.clickable && linkFor(node.id) !== null;
}

/**
 * 还没逐屏精修的屏：按采集顺序把「有字或有无障碍标签」的节点排成行，可点的行保留换屏动作。
 *
 * 这一支存在的理由是——阶段一只给出节点表，逐屏 1:1 要一屏一屏做（阶段三），
 * 而 H5 要「可打开、可读到内容、可走到下一屏」不能等到第 N 片才成立。
 * 精修过的屏（主页、对话、学习库）在 ScreenRouter 里各走各的组件，不走这里。
 */
export function FixtureListPage({ screen }: { screen: ScreenName }) {
  const { status, payload } = useFixtureScreen(screen);
  const route = useRoute();
  if (payload === null) {
    return status === "loading" ? null : <Notice copyKey="state_fixture_unavailable" />;
  }
  const nodes = payload.nodes;
  const taps = nodes.filter(isTapTarget);
  const hidden = new Set<FixtureNode>();
  taps.forEach((node) => subtreeOf(nodes, node).forEach((child) => hidden.add(child)));
  const back = nodes.find((node) => node.id.endsWith("_back")) ?? null;
  // 标题只认 Android 自己命名成 `*_title` 的那个身份（treecanvas 那屏用的是 tree_title）。
  // 自绘屏的标题是没有 resource-id 的普通文字，「取第一条有字的」会读到返回键的 content-desc，
  // 把「返回」当屏名画出来——于是宁可留空，让那一行按采集顺序自己出字。
  const title = nodes.find((node) => node.id.endsWith("_title")) ?? null;
  return (
    <section className="screen" data-screen={screen}>
      <header className="appbar" data-node={`${screen}_appbar`}>
        {back === null
          ? (route.canGoBack
            ? (
              <button className="node-tap" data-node="state_back" type="button" onClick={route.back}>
                {copyText("state_back")}
              </button>
            )
            : null)
          : <NodeTap node={back} />}
        <span className="appbar-title" data-node={title?.id ?? ""}>
          {title?.text ?? ""}
        </span>
      </header>
      <ul className="node-list">
        {nodes.map((node, index) => {
          if (hidden.has(node) || node === back || node === title) return null;
          if (isTapTarget(node)) {
            // 入口自己常常不带文字，标签在它的子树里（设置页六条全是这样）。
            const lines = textsOf(node, nodes);
            if (lines.length === 0) return null;
            return (
              <li key={`row-${index}`}>
                <NodeTap node={node}>
                  <span className="row-stack">
                    {lines.map((line, i) => (
                      <span className={i === 0 ? "row-title" : "row-sub"}
                        data-node={i === 0 ? node.id : ""} key={`line-${i}`}>
                        {line}
                      </span>
                    ))}
                  </span>
                </NodeTap>
              </li>
            );
          }
          const label = node.text || node.desc;
          if (label === "") return null;
          return (
            <li key={`row-${index}`}>
              <p className="row" data-node={node.id}>
                {label}
              </p>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
