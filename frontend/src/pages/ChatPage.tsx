import { firstById, groupsById, nodesById, pickChild } from "../platform/fixtureNodes";
import { useFixtureScreen } from "../hooks/useFixtureScreen";
import { NodeTap } from "../components/NodeTap";
import { Notice } from "../components/Notice";

const APPBAR_BACK = "chat_back";

/** 对话页：正文按采集顺序出段，建议行按容器分组，底部只读到当时那枚模型芯片。 */
export function ChatPage() {
  const { status, payload } = useFixtureScreen("chat");
  if (payload === null) {
    return status === "loading" ? null : <Notice copyKey="state_fixture_unavailable" />;
  }
  const n = payload.nodes;
  const body = n.filter((node) => node.id.startsWith("chat_answer") && node.text !== "");
  const asks = nodesById(n, "chat_ask_why").concat(nodesById(n, "chat_ask_how"), nodesById(n, "chat_ask_what"));
  return (
    <section className="screen" data-screen="chat">
      <header className="appbar" data-node="chat_appbar">
        <NodeTap node={firstById(n, APPBAR_BACK)} />
        <span className="appbar-title" data-node="chat_title">
          {firstById(n, "chat_title")?.text ?? ""}
        </span>
        <NodeTap node={firstById(n, "chat_nav")} />
        <NodeTap node={firstById(n, "chat_overview")} />
        <NodeTap node={firstById(n, "chat_settings")} />
      </header>

      <div className="chat-body" data-node="chat_messages">
        {body.map((node, i) => (
          <p className="chat-answer-run" data-node={node.id} key={`body-${i}`}>
            {node.text}
          </p>
        ))}
        <div className="chat-actions" data-node="chat_actions">
          {n.filter((node) => node.id.startsWith("act_")).map((node, i) => (
            <NodeTap node={node} key={`act-${i}`} />
          ))}
        </div>
        {asks.map((node, i) => (
          <span className="ask-chip" data-node={node.id} key={`ask-${i}`}>
            {node.text}
          </span>
        ))}
      </div>

      {groupsById(n, "suggestion_row").length > 0 && (
        <section className="section">
          <ul className="suggestion-list">
            {groupsById(n, "suggestion_row").map((group, i) => (
              <li className="suggestion-row" data-node="suggestion_row" key={`sug-${i}`}>
                <span className="suggestion-text" data-node="suggestion_text">
                  {pickChild(group.children, "suggestion_text")?.text ?? group.node.text}
                </span>
                <NodeTap node={pickChild(group.children, "suggestion_fork")} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <footer className="composer" data-node="chat_composer">
        <p className="composer-hint" data-node="chat_draft">
          {firstById(n, "chat_draft")?.text ?? ""}
        </p>
        <div className="composer-row">
          <span className="chip" data-node="model_chip_label">
            {firstById(n, "model_chip_label")?.text ?? ""}
          </span>
          <NodeTap node={firstById(n, "chat_attach")} />
          <span className="composer-send" data-node="chat_send" aria-label={firstById(n, "chat_send")?.desc ?? ""} />
        </div>
      </footer>
    </section>
  );
}
