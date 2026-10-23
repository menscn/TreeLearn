import { useLayoutEffect, useRef } from "react";
import { Icon } from "../components/Icon";
import { IconButton } from "../components/IconButton";
import { Notice } from "../components/Notice";
import { useWorkspaceData } from "../components/WorkspaceDataProvider";
import { useCopy } from "../hooks/useCopy";
import { useWorkspace } from "../hooks/useWorkspace";
import {
  openTopic,
  rememberScroll,
  scrollFor,
  setLibraryFilter,
  setLibraryQuery,
  toggleSummary
} from "../store/workspace";

const LIBRARY_SCROLL_KEY = "library";

const FILTERS = [
  { value: "all", label: "library_filter_all" },
  { value: "in_progress", label: "library_filter_in_progress" },
  { value: "done", label: "library_filter_done" }
] as const;

/** 学习库：搜索与状态筛选是本地真实交互，摘要展开与滚动位置写回仓库。 */
export function LibraryPage() {
  const t = useCopy();
  const state = useWorkspace();
  const data = useWorkspaceData();
  const scrollRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (element === null) return;
    element.scrollTop = scrollFor(LIBRARY_SCROLL_KEY);
    return () => rememberScroll(LIBRARY_SCROLL_KEY, element.scrollTop);
  }, []);

  const counts = {
    all: data.topics.length,
    in_progress: data.topics.filter((topic) => topic.status === "in_progress").length,
    done: data.topics.filter((topic) => topic.status === "done").length
  };

  const query = state.libraryQuery.trim().toLowerCase();
  const hasQuery = state.libraryQuery.trim() !== "";
  const visible = data.topics
    .map((topic, index) => ({ topic, index }))
    .filter(({ topic }) => {
      if (state.libraryFilter === "all") return true;
      return topic.status === state.libraryFilter;
    })
    .filter(({ topic }) => {
      if (query === "") return true;
      return [topic.title, topic.summary, ...topic.questions]
        .some((text) => text.toLowerCase().includes(query));
    });

  const clearSearch = (): void => {
    setLibraryQuery("");
    searchRef.current?.focus();
  };

  if (data.topics.length === 0) {
    return (
      <section className="workspace-page" data-screen="library" aria-label={t("tab_library")}>
        <div className="page-scroll" data-slot="library-scroll">
          <div className="page">
            <h1 className="page-title" data-node="library_title">{t("tab_library")}</h1>
            <Notice copyKey="state_fixture_unavailable" />
          </div>
        </div>
      </section>
    );
  }

  const emptyKey = hasQuery
    ? "library_empty_search"
    : state.libraryFilter === "done"
      ? "library_empty_done"
      : "library_empty_filter";
  const emptyKind = hasQuery ? "search" : "filter";

  return (
    <section className="workspace-page" data-screen="library" aria-label={t("tab_library")}>
      <div
        className="page-scroll"
        data-slot="library-scroll"
        ref={scrollRef}
        onScroll={(event) => rememberScroll(LIBRARY_SCROLL_KEY, event.currentTarget.scrollTop)}
      >
        <div className="page">
          <h1 className="page-title" data-node="library_title">{t("tab_library")}</h1>

          <div className="library-tools">
            <div className="search-field">
              <Icon name="search" />
              <input
                data-node="library_search_input"
                id="library-search"
                ref={searchRef}
                type="search"
                aria-label={t("library_search_label")}
                placeholder={t("library_search_placeholder")}
                value={state.libraryQuery}
                onChange={(event) => setLibraryQuery(event.target.value)}
              />
              {hasQuery ? (
                <IconButton
                  action="library-clear-search"
                  className="search-clear"
                  glyph="chat_close"
                  label={t("library_clear_search")}
                  onClick={clearSearch}
                />
              ) : null}
            </div>
            <div className="segments" role="group" aria-label={t("library_filter_label")}>
              {FILTERS.map((filter) => {
                const active = state.libraryFilter === filter.value;
                return (
                  <button
                    className="segment"
                    data-filter={filter.value}
                    data-node={filter.value === "all"
                      ? undefined
                      : filter.value === "in_progress"
                        ? "library_segment_in_progress"
                        : "library_segment_done"}
                    key={filter.value}
                    type="button"
                    aria-pressed={active}
                    onClick={() => setLibraryFilter(filter.value)}
                  >
                    {`${t(filter.label)} ${counts[filter.value]}`}
                  </button>
                );
              })}
            </div>
          </div>

          {visible.length === 0 ? (
            <div className="empty-state" data-empty={emptyKind} role="status">
              <p>{t(emptyKey)}</p>
              {hasQuery ? (
                <IconButton
                  action="library-empty-clear"
                  glyph="chat_close"
                  label={t("library_clear_search")}
                  onClick={clearSearch}
                />
              ) : null}
            </div>
          ) : (
            <div className="topic-rows" data-slot="library-rows">
              {visible.map(({ topic, index }) => {
                const expanded = state.libraryExpanded.includes(topic.id);
                const summaryId = `library-summary-${topic.id}`;
                const toggleLabel = expanded ? t("library_collapse") : t("library_expand");
                return (
                  <article
                    className="topic-row"
                    data-node="library_topic"
                    data-topic-id={topic.id}
                    key={topic.id}
                  >
                    <button
                      className="topic-open"
                      data-topic={index}
                      type="button"
                      onClick={() => openTopic(index, data.topics.length)}
                    >
                      <strong data-node="library_topic_title">{topic.title}</strong>
                      <Icon name="chevron_right" />
                    </button>
                    <p
                      className={`topic-excerpt${expanded ? " is-expanded" : ""}`}
                      data-expanded={expanded ? "true" : "false"}
                      data-node="library_topic_summary"
                      id={summaryId}
                    >
                      {topic.summary}
                    </p>
                    <div className="topic-foot">
                      <span className="topic-meta" data-node="library_topic_meta">{topic.meta}</span>
                      <button
                        className="summary-toggle"
                        data-summary-toggle={topic.id}
                        type="button"
                        aria-expanded={expanded}
                        aria-controls={summaryId}
                        aria-label={`${toggleLabel}：${topic.title}`}
                        title={toggleLabel}
                        onClick={() => toggleSummary(topic.id)}
                      >
                        <Icon name={expanded ? "chevron_down" : "chevron_right"} />
                        <span>{toggleLabel}</span>
                      </button>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
