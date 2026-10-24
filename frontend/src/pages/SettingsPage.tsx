import { useLayoutEffect, useRef } from "react";
import { Icon, type GlyphName } from "../components/Icon";
import { Notice } from "../components/Notice";
import { useWorkspaceData } from "../components/WorkspaceDataProvider";
import { useCopy } from "../hooks/useCopy";
import { useWorkspace } from "../hooks/useWorkspace";
import { openView, rememberScroll, scrollFor, setSettingsCategory } from "../store/workspace";
import type { SettingsCategory, SettingsField } from "../store/workspaceData";

const SCROLL_PREFIX = "settings:";
const SAVE = "\u4FDD\u5B58";
const DELETE = "\u5220\u9664";
const PREVIEW = "\u8BD5\u542C";

function actionGlyph(label: string): GlyphName | null {
  if (label.includes(SAVE)) return "check";
  if (label.includes(DELETE)) return "chat_close";
  if (label.includes(PREVIEW)) return "nav_chat";
  return null;
}

function SettingsFieldRow({ field }: { field: SettingsField }) {
  const t = useCopy();
  const unavailable = t("chat_action_unavailable");
  if (field.control === "switch") {
    const stateText = field.state === "on"
      ? t("settings_switch_on")
      : field.state === "off"
        ? t("settings_switch_off")
        : t("settings_switch_unknown");
    const label = `${field.label}：${stateText} · ${unavailable}`;
    return (
      <div className="setting-field setting-switch">
        <div className="setting-switch-copy">
          <span className="setting-label">{field.label}</span>
          {field.description ? <p className="field-note">{field.description}</p> : null}
        </div>
        <span
          className="switch-badge"
          data-control="switch"
          data-state={field.state ?? "unknown"}
          role="img"
          aria-label={label}
          title={label}
        >
          <span className="switch-badge-dot" aria-hidden="true" />
          {stateText}
        </span>
      </div>
    );
  }
  if (field.action) {
    const label = `${field.label} · ${unavailable}`;
    return (
      <button
        className="setting-row-action"
        data-settings-action={field.label}
        type="button"
        disabled
        aria-label={label}
        title={label}
      >
        <span>{field.label}</span>
        <Icon name="chevron_right" />
      </button>
    );
  }
  if (field.disabled && field.value === undefined) {
    const label = `${field.label} · ${unavailable}`;
    return (
      <input
        className="field setting-input"
        type="password"
        disabled
        placeholder={field.label}
        aria-label={label}
        title={label}
      />
    );
  }
  return (
    <div className="setting-field" data-node="settings_field" data-label={field.label}>
      <span className="setting-label">{field.label}</span>
      {field.value ? <span className="setting-value">{field.value}</span> : null}
      {field.selected ? <Icon name="check" /> : null}
    </div>
  );
}

function SettingsActions({ actions }: { actions: SettingsField[] }) {
  const t = useCopy();
  if (actions.length === 0) return null;
  const unavailable = t("chat_action_unavailable");
  return (
    <div className="settings-actions">
      {actions.map((action, index) => {
        const glyph = actionGlyph(action.label);
        const label = `${action.label} · ${unavailable}`;
        return (
          <button
            className="settings-action"
            data-settings-action={action.label}
            key={`${action.label}-${index}`}
            type="button"
            disabled
            aria-label={label}
            title={label}
          >
            {glyph ? <Icon name={glyph} /> : null}
            <span>{action.label}</span>
          </button>
        );
      })}
    </div>
  );
}

function SettingsDetail({ category }: { category: SettingsCategory }) {
  const t = useCopy();
  return (
    <article
      className="settings-detail"
      data-settings-detail={category.id}
      aria-labelledby="settings-detail-title"
    >
      <header className="settings-detail-head">
        <h2 id="settings-detail-title">{category.title}</h2>
        {category.summary !== "" ? <p className="settings-summary">{category.summary}</p> : null}
        <p className="settings-readonly" data-node="settings_readonly" role="note">
          {t("settings_readonly")}
        </p>
      </header>
      {category.available ? (
        <>
          {category.groups.map((group, groupIndex) => (
            <section
              className="setting-block"
              data-settings-group={group.heading}
              key={`${category.id}-group-${groupIndex}`}
            >
              {group.heading ? <h3 className="setting-heading">{group.heading}</h3> : null}
              {group.fields.map((field, fieldIndex) => (
                <SettingsFieldRow
                  field={field}
                  key={`${category.id}-field-${groupIndex}-${fieldIndex}`}
                />
              ))}
              {group.notes.map((note, noteIndex) => (
                <p className="field-note" key={`${category.id}-note-${groupIndex}-${noteIndex}`}>{note}</p>
              ))}
            </section>
          ))}
          <SettingsActions actions={category.actions} />
        </>
      ) : (
        <div className="settings-missing" data-slot="settings-missing">
          <Notice copyKey="state_fixture_unavailable" />
        </div>
      )}
    </article>
  );
}

/** 设置：分类列表＋只读详情；配置值只来自采集夹具，写入入口一律禁用。 */
export function SettingsPage() {
  const t = useCopy();
  const state = useWorkspace();
  const data = useWorkspaceData();
  const scrollRef = useRef<HTMLDivElement>(null);
  const settings = data.settings;
  const active = settings.find((category) => category.id === state.settingsCategory)
    ?? settings[0]
    ?? null;
  const scrollKey = active === null ? "" : `${SCROLL_PREFIX}${active.id}`;
  const keyRef = useRef(scrollKey);
  keyRef.current = scrollKey;

  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (element === null) return;
    return () => {
      if (keyRef.current !== "") rememberScroll(keyRef.current, element.scrollTop);
    };
  }, []);

  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (element === null || scrollKey === "") return;
    element.scrollTop = scrollFor(scrollKey);
  }, [scrollKey]);

  if (active === null) {
    return (
      <section className="workspace-page settings-page" data-screen="settings" aria-label={t("nav_settings")}>
        <div className="page-scroll" data-slot="settings-scroll">
          <div className="page">
            <h1 className="page-title" data-node="settings_title">{t("nav_settings")}</h1>
            <Notice copyKey="state_fixture_unavailable" />
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="workspace-page settings-page" data-screen="settings" aria-label={t("nav_settings")}>
      <div
        className="page-scroll"
        data-slot="settings-scroll"
        ref={scrollRef}
        onScroll={(event) => {
          if (scrollKey !== "") rememberScroll(scrollKey, event.currentTarget.scrollTop);
        }}
      >
        <div className="page">
          <h1 className="page-title" data-node="settings_title">{t("nav_settings")}</h1>
          <div className="settings-layout">
            <nav className="settings-categories" aria-label={t("settings_categories_label")}>
              {settings.map((category) => {
                const selected = category.id === active.id;
                return (
                  <button
                    className={`settings-category${selected ? " active" : ""}`}
                    data-settings-category={category.id}
                    key={category.id}
                    type="button"
                    aria-current={selected ? "page" : undefined}
                    onClick={() => setSettingsCategory(category.id)}
                  >
                    <span className="settings-category-head">
                      <span className="settings-category-title">{category.title}</span>
                      <Icon name="chevron_right" />
                    </span>
                    <span className="settings-category-summary">{category.summary}</span>
                  </button>
                );
              })}
            </nav>
            <SettingsDetail category={active} />
          </div>
          <section className="settings-persona">
            <p className="settings-persona-caption">{t("persona_intro_title")}</p>
            <button
              className="settings-persona-entry"
              data-persona-intro-entry
              type="button"
              aria-label={t("persona_intro_title")}
              title={t("persona_intro_title")}
              onClick={() => openView("persona-intro")}
            >
              <span className="settings-persona-copy">
                <span className="settings-persona-title">{t("persona_intro_title")}</span>
                <span className="settings-persona-sub">{t("settings_persona_intro_sub")}</span>
              </span>
              <Icon name="chevron_right" />
            </button>
          </section>
        </div>
      </div>
    </section>
  );
}
