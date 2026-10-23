import type { ReactNode } from "react";
import type { FixtureNode } from "../platform/fixtureNodes";
import { linkFor } from "../store/screenLinks";
import { useRoute } from "../hooks/useRoute";
import { Icon, iconFor } from "./Icon";

/**
 * 一个被采集到的可点节点在 Web 侧的落点。
 *
 * 有映射的走换屏，没映射的读成静态块——把「点了没反应」画成一个按钮是假可点，
 * 阶段六行为接线才会真给它动作。图标节点画 Android 同名 drawable 的字形，
 * content-desc 只作可访问名，不再当可见文字贴出来。
 */
export function NodeTap({ node, children }: { node: FixtureNode | null; children?: ReactNode }) {
  const route = useRoute();
  if (node === null) return null;
  const link = linkFor(node.id);
  const glyph = iconFor(node);
  const iconOnly = glyph !== null && node.text === "";
  const compound = glyph !== null && node.text !== "";
  const aria = iconOnly && node.desc !== "" ? node.desc : undefined;
  const shape = iconOnly ? " node-icon" : compound ? " node-inline" : "";
  const body = children ?? (
    <>
      {glyph === null ? null : <Icon name={glyph} />}
      {node.text === "" ? null : <span className="node-label">{node.text}</span>}
    </>
  );
  if (link === null) {
    return (
      <div
        aria-label={aria}
        className={`node-plain${shape}`}
        data-node={node.id}
        role={aria === undefined ? undefined : "img"}
      >
        {body}
      </div>
    );
  }
  return (
    <button
      aria-label={aria}
      className={`node-tap${shape}`}
      data-node={node.id}
      type="button"
      onClick={() => {
        if (link.kind === "screen") route.open(link.screen);
        else if (link.kind === "tab") route.openTab(link.screen);
        else route.back();
      }}
    >
      {body}
    </button>
  );
}
