import { firstById, groupsById, pickChild } from "../platform/fixtureNodes";
import { useFixtureScreen } from "../hooks/useFixtureScreen";
import { NodeTap } from "../components/NodeTap";
import { Notice } from "../components/Notice";

export function HomePage() {
  const { status, payload } = useFixtureScreen("home");
  if (payload === null) {
    return status === "loading" ? null : <Notice copyKey="state_fixture_unavailable" />;
  }
  const n = payload.nodes;
  const recTitle = firstById(n, "home_recommendations_title");
  const recentTitle = firstById(n, "home_recent_title");
  return (
    <section className="screen" data-screen="home">
      <header className="appbar appbar-plain" data-node="home_appbar">
        <NodeTap node={firstById(n, "home_drawer_button")} />
        <span className="appbar-brand" data-node="home_brand">
          {firstById(n, "home_brand")?.text ?? ""}
        </span>
        <NodeTap node={firstById(n, "home_settings_button")} />
      </header>

      <div className="composer" data-node="home_composer">
        <p className="composer-hint" data-node="home_draft">
          {firstById(n, "home_draft")?.text ?? ""}
        </p>
        <div className="composer-row">
          <span className="chip" data-node="model_chip_label">
            {firstById(n, "model_chip_label")?.text ?? ""}
          </span>
          <span className="composer-send" data-node="home_send" aria-label={firstById(n, "home_send")?.desc ?? ""} />
        </div>
      </div>

      {recTitle !== null && (
        <section className="section">
          <h2 className="section-title" data-node="home_recommendations_title">
            {recTitle.text}
          </h2>
          <ul className="rec-list">
            {groupsById(n, "rec_chip").map((group, i) => (
              <li key={`rec-${i}`}>
                <NodeTap node={group.node}>
                  <span className="rec-tag" data-node="rec_tag">
                    {pickChild(group.children, "rec_tag")?.text ?? ""}
                  </span>
                  <span className="rec-text" data-node="rec_text">
                    {pickChild(group.children, "rec_text")?.text ?? ""}
                  </span>
                </NodeTap>
              </li>
            ))}
          </ul>
        </section>
      )}

      {recentTitle !== null && (
        <section className="section">
          <h2 className="section-title" data-node="home_recent_title">
            {recentTitle.text}
            <span className="section-count" data-node="home_recent_count">
              {firstById(n, "home_recent_count")?.text ?? ""}
            </span>
          </h2>
          <ul className="topic-list">
            {groupsById(n, "home_topic_card").map((group, i) => (
              <li key={`card-${i}`}>
                <NodeTap node={group.node}>
                  <article className="topic-card">
                    <h3 className="topic-title" data-node="home_topic_title">
                      {pickChild(group.children, "home_topic_title")?.text ?? ""}
                    </h3>
                    <p className="topic-summary" data-node="home_topic_summary">
                      {pickChild(group.children, "home_topic_summary")?.text ?? ""}
                    </p>
                    <p className="topic-meta" data-node="home_topic_meta">
                      {pickChild(group.children, "home_topic_meta")?.text ?? ""}
                    </p>
                  </article>
                </NodeTap>
              </li>
            ))}
          </ul>
        </section>
      )}
    </section>
  );
}
