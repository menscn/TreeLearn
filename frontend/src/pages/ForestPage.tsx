import { useMemo, useState } from "react";
import { ForestScene, type ForestSceneTree } from "../components/ForestScene";
import { Icon } from "../components/Icon";
import { useWorkspaceData } from "../components/WorkspaceDataProvider";
import { copyFormat } from "../copy";
import { useCopy } from "../hooks/useCopy";
import { forestLayout } from "../store/forestLayout";
import { personaNameKey } from "../store/persona";
import { openTopic, openView, personaFor } from "../store/workspace";

/**
 * 我的森林（T-070，2026-10-25 由 C1 重排信息落点）。
 *
 * 数字进标题行：树数就是刚画出来的那一列，段枝总数只在每个主题的段枝数都读得到时才给；
 * 归档与连续天数在 Web 没有数据来源，一律省略而不是写 0 冒充。页脚因此撤掉——同一组数字
 * 不再在屏幕底端重复一次。
 * 学习库整屏读不到时报「森林暂时读不到」，并且不画重试按钮——Web 这条链路没有重读入口，
 * 画一个不生效的按钮比省略更不诚实。
 */
export function ForestPage() {
  const t = useCopy();
  const data = useWorkspaceData();
  const [selected, setSelected] = useState<number | null>(null);
  const topics = data.topics;

  const trees = useMemo<ForestSceneTree[]>(() => {
    const placements = forestLayout(
      topics.map((topic) => topic.id),
      topics.map((topic) => topic.branchCount)
    );
    return topics.map((topic, index) => ({
      index,
      title: topic.title,
      placement: placements[index] as ForestSceneTree["placement"]
    }));
  }, [topics]);

  const selectedTree = selected === null ? null : trees.find((tree) => tree.index === selected) ?? null;
  const selectedIndex = selectedTree?.index ?? null;
  const selectedTopic = selectedIndex === null ? null : topics[selectedIndex] ?? null;
  const segmentTotal = topics.every((topic) => topic.branchCount !== null)
    ? topics.reduce((sum, topic) => sum + (topic.branchCount ?? 0), 0)
    : null;
  const metaSeparator = ` ${t("forest_meta_separator")} `;
  const sheetMeta = selectedTopic === null || selectedIndex === null
    ? ""
    : [
      ...(selectedTopic.branchCount === null ? [] : [copyFormat("forest_meta_branches", selectedTopic.branchCount)]),
      t(personaNameKey(personaFor(selectedIndex)))
    ].join(metaSeparator);
  // 场上的数字进副标题这一行：一个屏幕只留一处量化，且它就在名字旁边。
  const subtitleCounts = [
    copyFormat("forest_foot_tree", topics.length),
    ...(segmentTotal === null ? [] : [copyFormat("forest_foot_segments", segmentTotal)])
  ].join(metaSeparator);
  // 空森林与读不到的森林要分开说：整屏缺失时「还没有树」是假话。
  const groundReadable = data.status === "ready" || topics.length > 0;

  return (
    <section className="workspace-page forest-page" data-screen="forest" aria-label={t("forest_title")}>
      <header className="forest-head">
        <h1 className="page-title" data-node="forest_title">{t("forest_title")}</h1>
        <p className="forest-subtitle" data-forest-subtitle>
          {t("forest_subtitle")}
          {topics.length === 0 ? null : (
            <span className="forest-count" data-forest-count>
              {`${t("forest_meta_separator")} ${subtitleCounts}`}
            </span>
          )}
        </p>
      </header>
      <div className="forest-stage">
        {!groundReadable ? (
          <div className="forest-state" data-forest-error role="alert">
            <span className="forest-state-ring" aria-hidden="true">
              <Icon name="brand_sprout" />
            </span>
            <p className="forest-state-title">{t("forest_error_title")}</p>
            <p className="forest-state-message">{t("forest_error_message")}</p>
          </div>
        ) : topics.length === 0 ? (
          <div className="forest-state" data-forest-empty>
            <span className="forest-state-ring" aria-hidden="true">
              <Icon name="brand_sprout" />
            </span>
            <p className="forest-state-title">{t("forest_empty_title")}</p>
            <p className="forest-state-message">{t("forest_empty_message")}</p>
            <button
              type="button"
              className="forest-cta"
              data-forest-empty-action
              onClick={() => openView("home")}
            >
              {t("forest_empty_action")}
            </button>
          </div>
        ) : (
          <>
            <ForestScene trees={trees} selected={selected} onSelect={setSelected} />
            {selectedTopic === null ? (
              <p className="forest-hint" data-forest-hint>
                <Icon name="forest_pin" className="forest-hint-icon" />
                {t("forest_hint")}
              </p>
            ) : null}
            {selectedTopic === null || selectedIndex === null ? null : (
              <div className="forest-sheet" data-forest-sheet>
                <div className="forest-sheet-main">
                  <p className="forest-sheet-title" data-forest-sheet-title>{selectedTopic.title}</p>
                  <p className="forest-sheet-meta" data-forest-sheet-meta>{sheetMeta}</p>
                </div>
                <button
                  type="button"
                  className="forest-go"
                  data-forest-go
                  aria-label={copyFormat("cd_forest_open_topic", selectedTopic.title)}
                  title={copyFormat("cd_forest_open_topic", selectedTopic.title)}
                  onClick={() => openTopic(selectedIndex, topics.length)}
                >
                  <Icon name="chevron_right" />
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  );
}
