import { firstById, groupsById, nodesById, pickChild } from "../platform/fixtureNodes";
import { useFixtureScreen } from "../hooks/useFixtureScreen";
import { NodeTap } from "../components/NodeTap";
import { Notice } from "../components/Notice";

export function LibraryPage() {
  const { status, payload } = useFixtureScreen("library");
  if (payload === null) {
    return status === "loading" ? null : <Notice copyKey="state_fixture_unavailable" />;
  }
  const n = payload.nodes;
  return (
    <section className="screen" data-screen="library">
      <header className="appbar" data-node="library_appbar">
        <NodeTap node={firstById(n, "library_back")} />
        <span className="appbar-title" data-node="library_title">
          {firstById(n, "library_title")?.text ?? ""}
        </span>
      </header>
      <p className="search-hint" data-node="library_search_input">
        {firstById(n, "library_search_input")?.text ?? ""}
      </p>
      <div className="segments">
        {nodesById(n, "library_segment_in_progress")
          .concat(nodesById(n, "library_segment_done"))
          .map((node, i) => (
            <span className="segment" data-node={node.id} key={`seg-${i}`}>
              {node.text}
            </span>
          ))}
      </div>
      <ul className="topic-list">
        {groupsById(n, "library_topic").map((group, i) => (
          <li key={`topic-${i}`}>
            <NodeTap node={group.node}>
              <article className="topic-card">
                <h3 className="topic-title" data-node="library_topic_title">
                  {pickChild(group.children, "library_topic_title")?.text ?? ""}
                </h3>
                <p className="topic-summary" data-node="library_topic_summary">
                  {pickChild(group.children, "library_topic_summary")?.text ?? ""}
                </p>
                <p className="topic-meta" data-node="library_topic_meta">
                  {pickChild(group.children, "library_topic_meta")?.text ?? ""}
                </p>
              </article>
            </NodeTap>
          </li>
        ))}
      </ul>
    </section>
  );
}
