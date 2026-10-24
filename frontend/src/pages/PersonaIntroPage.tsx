import { Icon } from "../components/Icon";
import { useCopy } from "../hooks/useCopy";
import { PERSONA_ORDER, personaDemoKey, personaNameKey, personaSubKey, personaTagKey } from "../store/persona";
import { back } from "../store/workspace";

/**
 * 回答风格介绍页：纯静态文案，不承载选择、不发请求、不需要 API Key。
 * 七张卡的档名、一句话定位与示例逐字来自文案表（与 Android `strings.xml` 同值）。
 */
export function PersonaIntroPage() {
  const t = useCopy();
  return (
    <section
      className="workspace-page persona-intro-page"
      data-screen="persona-intro"
      aria-label={t("persona_intro_title")}
    >
      <div className="page-scroll" data-slot="persona-intro-scroll">
        <div className="page">
          <header className="persona-intro-head">
            <button
              type="button"
              className="icon-btn"
              data-action="persona-intro-back"
              aria-label={t("cd_persona_intro_back")}
              title={t("cd_persona_intro_back")}
              onClick={() => back()}
            >
              <Icon name="nav_back" />
            </button>
            <div className="persona-intro-titles">
              <h1 className="page-title" data-node="persona_intro_title">{t("persona_intro_title")}</h1>
              <p className="persona-intro-subtitle">{t("persona_intro_subtitle")}</p>
            </div>
          </header>
          <p className="persona-intro-lead">{t("persona_intro_lead")}</p>
          <p className="persona-intro-question">{t("persona_intro_question")}</p>
          <div className="persona-cards">
            {PERSONA_ORDER.map((persona) => (
              <article className="persona-card" data-persona-card={persona} key={persona}>
                <header className="persona-card-head">
                  <h2 className="persona-card-title">{t(personaNameKey(persona))}</h2>
                  <span className="persona-card-tag">{t(personaTagKey(persona))}</span>
                </header>
                <p className="persona-card-sub">{t(personaSubKey(persona))}</p>
                <p className="persona-card-demo">{t(personaDemoKey(persona))}</p>
              </article>
            ))}
          </div>
          <p className="persona-intro-footer">{t("persona_intro_footer")}</p>
        </div>
      </div>
    </section>
  );
}
