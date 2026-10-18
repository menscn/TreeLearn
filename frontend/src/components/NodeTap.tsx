import type { ReactNode } from "react";
import type { FixtureNode } from "../platform/fixtureNodes";
import { linkFor } from "../store/screenLinks";
import { useRoute } from "../hooks/useRoute";

/**
 * 一个被采集到的可点节点在 Web 侧的落点。
 *
 * 有映射的走换屏，没映射的读成静态块——把「点了没反应」画成一个按钮是假可点，
 * 阶段六行为接线才会真给它动作。图标在 Android 侧是矢量 drawable，本阶段先按
 * content-desc 出文字标签（已知偏差，记录在任务卡，留给阶段四用图形资源收口）。
 */
export function NodeTap({ node, children }: { node: FixtureNode | null; children?: ReactNode }) {
  const route = useRoute();
  if (node === null) return null;
  const link = linkFor(node.id);
  const label = node.text || node.desc;
  if (link === null) {
    return (
      <div className="node-plain" data-node={node.id}>
        {children ?? label}
      </div>
    );
  }
  return (
    <button
      className="node-tap"
      data-node={node.id}
      type="button"
      onClick={() => {
        if (link.kind === "screen") route.open(link.screen);
        else if (link.kind === "tab") route.openTab(link.screen);
        else route.back();
      }}
    >
      {children ?? label}
    </button>
  );
}
