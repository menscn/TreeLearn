import { useState, type FormEvent } from "react";
import { useCopy } from "../hooks/useCopy";
import type { DesktopApi, DesktopState } from "../platform/desktop";

interface Props {
  api: DesktopApi;
  settings: DesktopState["settings"];
  refresh: () => Promise<unknown>;
  reportError: (error: unknown) => void;
  disabled: boolean;
}

export function DesktopSettings({ api, settings, refresh, reportError, disabled }: Props) {
  const t = useCopy();
  const [baseUrl, setBaseUrl] = useState(settings.baseUrl);
  const [model, setModel] = useState(settings.model);
  const [apiKey, setApiKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || disabled) return;
    setBusy(true);
    setNotice("");
    try {
      await api.saveSettings({ baseUrl, model, apiKey });
      setApiKey("");
      await refresh();
      setNotice(t("desktop_saved"));
    } catch (error) { reportError(error); }
    finally { setBusy(false); }
  }

  async function removeKey() {
    if (busy || disabled || !window.confirm(t("desktop_delete_key_confirm"))) return;
    setBusy(true);
    try {
      await api.deleteApiKey();
      setApiKey("");
      await refresh();
      setNotice(t("desktop_key_deleted"));
    } catch (error) { reportError(error); }
    finally { setBusy(false); }
  }

  return (
    <section className="page desktop-settings settings-page" data-screen="settings">
      <h1 className="page-title">{t("desktop_settings_title")}</h1>
      <p className="settings-summary">{t("desktop_settings_description")}</p>
      <form onSubmit={(event) => { void save(event); }}>
        <label className="desktop-field">{t("desktop_base_url")}
          <input className="field" value={baseUrl} disabled={disabled} onChange={(event) => setBaseUrl(event.target.value)} required />
        </label>
        <label className="desktop-field">{t("desktop_model")}
          <input className="field" value={model} disabled={disabled} onChange={(event) => setModel(event.target.value)} required />
        </label>
        <label className="desktop-field">{t("desktop_api_key")}
          <input className="field" type="password" autoComplete="off" value={apiKey} disabled={disabled}
            placeholder={t("desktop_key_placeholder")} onChange={(event) => setApiKey(event.target.value)} />
        </label>
        <p className="field-note">{settings.hasApiKey ? t("desktop_key_saved") : t("desktop_key_missing")}</p>
        <div className="settings-actions">
          <button className="settings-action desktop-primary" type="submit" disabled={busy || disabled}>{t("desktop_save")}</button>
          {settings.hasApiKey ? <button className="settings-action" type="button" disabled={busy || disabled} onClick={() => { void removeKey(); }}>{t("desktop_delete_key")}</button> : null}
        </div>
        <p role="status" className="field-note">{notice}</p>
      </form>
    </section>
  );
}
