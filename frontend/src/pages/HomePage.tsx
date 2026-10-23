import { useRef, useState } from "react";
import { Composer } from "../components/Composer";
import { Icon } from "../components/Icon";
import { useWorkspaceData } from "../components/WorkspaceDataProvider";
import { useCopy } from "../hooks/useCopy";
import {
  draftFor,
  openTopic,
  setDraft
} from "../store/workspace";

const HOME_DRAFT_KEY = "home";

/** 主页：输入根问题、顺推荐进入主题 0，或直接回到某个真实历史主题。 */
export function HomePage() {
  const t = useCopy();
  const data = useWorkspaceData();
  const [draft, setDraftState] = useState(() => draftFor(HOME_DRAFT_KEY));
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const updateDraft = (value: string): void => {
    setDraftState(value);
    setDraft(HOME_DRAFT_KEY, value);
  };

  const enterRecommendation = (text: string, topicIndex: number): void => {
    setDraft(`chat:${topicIndex}`, text);
    openTopic(topicIndex, data.topics.length);
  };

  const recommendationsTitle = data.home?.recommendationsTitle || t("home_recommendations_title");
  const recentTitle = data.home?.recentTitle || t("home_recent_title");
  const recentCount = data.home?.recentCount ?? "";

  return (
    <section className="workspace-page" data-screen="home" aria-label={t("tab_home")}>
      <div className="page-scroll" data-slot="home-scroll">
        <div className="page">
          <p className="page-eyebrow">
            <Icon name="brand_sprout" />
            <span>{t("home_eyebrow")}</span>
          </p>
          <h1 className="home-title">{t("home_headline")}</h1>
          <Composer
            value={draft}
            placeholder={data.home?.draftPlaceholder || t("home_composer_placeholder")}
            model={data.home?.model ?? ""}
            inputRef={inputRef}
            onChange={updateDraft}
            inputId="home-composer-input"
            label={t("home_composer_label")}
            note={t("home_composer_note")}
            dockClassName="home-dock"
            dockDataNode="home_composer"
            modelNode="home_model_chip"
          />

          {data.recommendations.length > 0 ? (
            <section aria-labelledby="home-recommendations-heading">
              <h2
                className="home-heading"
                data-node="home_recommendations_title"
                id="home-recommendations-heading"
              >
                <Icon name="sparkle" />
                <span>{recommendationsTitle}</span>
              </h2>
              <div className="home-suggestions">
                {data.recommendations.map((item, index) => (
                  <button
                    className="suggestion"
                    data-node="rec_chip"
                    data-recommendation={index}
                    key={`recommendation-${index}`}
                    type="button"
                    onClick={() => enterRecommendation(item.text, item.topicIndex)}
                  >
                    <Icon name="action_fork" />
                    <span className="suggestion-copy">
                      <span className="suggestion-tag" data-node="rec_tag">{item.tag}</span>
                      <span className="suggestion-text" data-node="rec_text">{item.text}</span>
                    </span>
                  </button>
                ))}
              </div>
            </section>
          ) : null}

          {data.topics.length > 0 ? (
            <section aria-labelledby="home-recent-heading">
              <h2 className="home-heading" data-node="home_recent_title" id="home-recent-heading">
                <span>{recentTitle}</span>
                {recentCount === "" ? null : (
                  <span className="home-count" data-node="home_recent_count">{recentCount}</span>
                )}
              </h2>
              <ul className="topic-list">
                {data.topics.map((topic, index) => (
                  <li key={topic.id}>
                    <article className="topic-row" data-node="home_topic_card">
                      <button
                        className="topic-open"
                        data-topic={index}
                        type="button"
                        onClick={() => openTopic(index, data.topics.length)}
                      >
                        <strong data-node="home_topic_title">{topic.title}</strong>
                        <Icon name="chevron_right" />
                      </button>
                      <p className="topic-excerpt" data-node="home_topic_summary">{topic.summary}</p>
                      <p className="topic-meta" data-node="home_topic_meta">
                        <Icon name="nav_tree" />
                        <span>{topic.meta}</span>
                      </p>
                    </article>
                  </li>
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      </div>
    </section>
  );
}
