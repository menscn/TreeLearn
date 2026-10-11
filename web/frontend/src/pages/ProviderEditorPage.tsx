import { useEffect, useState } from 'react';

import { ApiError, FALLBACK_ERROR_KEY } from '../api/errors';
import type { Provider } from '../api/types';
import { Dialog } from '../components/Dialog';
import { GlassPanel } from '../components/GlassPanel';
import { IconButton } from '../components/IconButton';
import { Check, KeyRound, Pencil, Plus, Power, RefreshCw, Trash2, X } from '../components/icons';
import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { createProvider, deleteProvider, deleteProviderKey, loadProviders, refreshProviderModels, saveProviderKey, updateProvider } from '../store/settingsData';

// 提供商与密钥（任务 T-2026-10-10-039）：列表/空态/新建/编辑/启停/删除（含最后提供商
// 警示）/Key 录入（只写不读）/拉取模型。文案全走 i18n，按钮 icon-only。API Key 仅存在于
// 组件内局部 state，提交成功后立即清空，绝不写入 localStorage/URL/日志。
interface Action { (): Promise<void> }

function errorKey(error: unknown): TranslationKey {
  return error instanceof ApiError ? error.i18nKey : FALLBACK_ERROR_KEY;
}

export function ProviderEditorPage() {
  const { t } = useI18n();
  const [providers, setProviders] = useState<Provider[] | null>(null);
  const [loadError, setLoadError] = useState<TranslationKey | null>(null);
  const [editor, setEditor] = useState<string | null>(null);
  const [form, setForm] = useState({ name: '', baseUrl: '', key: '' });
  const [formError, setFormError] = useState<TranslationKey | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Provider | null>(null);
  const [keyDrafts, setKeyDrafts] = useState<Record<string, string>>({});
  const [refresh, setRefresh] = useState<Record<string, { added: number; total: number }>>({});
  const [actionError, setActionError] = useState<TranslationKey | null>(null);

  useEffect(() => {
    let active = true;
    loadProviders().then((value) => { if (active) setProviders(value); }).catch((error: unknown) => { if (active) { setProviders([]); setLoadError(errorKey(error)); } });
    return () => { active = false; };
  }, []);

  const replace = (next: Provider) => setProviders((prev) => (prev === null ? prev : prev.map((item) => (item.id === next.id ? next : item))));
  const nameTaken = (name: string) => (providers ?? []).some((item) => item.id !== editor && item.name === name.trim());
  const openEditor = (target: Provider | 'new') => { setFormError(null); setEditor(target === 'new' ? 'new' : target.id); setForm(target === 'new' ? { name: '', baseUrl: '', key: '' } : { name: target.name, baseUrl: target.baseUrl, key: '' }); };
  const closeEditor = () => { setEditor(null); setFormError(null); };

  async function submitForm() {
    const name = form.name.trim();
    const baseUrl = form.baseUrl.trim();
    if (name.length === 0 || baseUrl.length === 0) { setFormError('errors.PROVIDER_INVALID'); return; }
    if (nameTaken(name)) { setFormError('errors.PROVIDER_NAME_TAKEN'); return; }
    setFormError(null);
    setSubmitting(true);
    try {
      const saved = editor === 'new' ? await createProvider({ name, baseUrl, apiKey: form.key }) : await updateProvider(editor as string, { name, baseUrl });
      setProviders((prev) => (prev === null ? [saved] : editor === 'new' ? [...prev, saved] : prev.map((item) => (item.id === saved.id ? saved : item))));
      closeEditor();
      setForm({ name: '', baseUrl: '', key: '' });
    } catch (error: unknown) {
      setFormError(error instanceof ApiError && error.code === 'PROVIDER_NAME_TAKEN' ? 'errors.PROVIDER_NAME_TAKEN' : errorKey(error));
    } finally {
      setSubmitting(false);
    }
  }

  async function run(action: Action) {
    setActionError(null);
    try { await action(); } catch (error: unknown) { setActionError(errorKey(error)); }
  }

  const editorForm = (
    <div className="providers__form">
      <input className="providers__input" aria-label={t('providers.name')} value={form.name} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} />
      <input className="providers__input" aria-label={t('providers.baseUrl')} value={form.baseUrl} onChange={(event) => setForm((prev) => ({ ...prev, baseUrl: event.target.value }))} />
      <input className="providers__input" type="password" aria-label={t('providers.apiKey')} placeholder={t('providers.keyPlaceholder')} value={form.key} onChange={(event) => setForm((prev) => ({ ...prev, key: event.target.value }))} />
      {formError !== null ? <p className="settings-page__error" role="alert">{t(formError)}</p> : null}
      <div className="providers__actions">
        <IconButton icon={Check} label={t('providers.save')} variant="solid" disabled={submitting} onClick={submitForm} />
        <IconButton icon={X} label={t('providers.cancel')} onClick={closeEditor} />
      </div>
    </div>
  );

  const providerRow = (provider: Provider) => (
    <>
      <div className="providers__head">
        <span className="providers__name">{provider.name}</span>
        <IconButton icon={Power} label={t('providers.enabled')} aria-pressed={provider.enabled} onClick={() => run(async () => replace(await updateProvider(provider.id, { enabled: !provider.enabled })))} />
        <span className="providers__badge" aria-label={`${provider.modelCount} ${t('providers.modelsBadge')}`}>{provider.modelCount}</span>
        <span className="providers__key-status">{t(provider.hasKey ? 'providers.keyConfigured' : 'providers.keyMissing')}</span>
        <div className="providers__actions">
          <IconButton icon={Pencil} label={t('providers.edit')} onClick={() => openEditor(provider)} />
          <IconButton icon={RefreshCw} label={t('providers.refreshModels')} onClick={() => run(async () => { const result = await refreshProviderModels(provider.id); setRefresh((prev) => ({ ...prev, [provider.id]: { added: result.added, total: result.models.length } })); replace({ ...provider, modelCount: result.models.length }); })} />
          <IconButton icon={Trash2} label={t('providers.delete')} onClick={() => setDeleteTarget(provider)} />
        </div>
      </div>
      <div className="providers__key-row">
        <input className="providers__input" type="password" aria-label={t('providers.apiKey')} placeholder={t('providers.keyPlaceholder')} value={keyDrafts[provider.id] ?? ''} onChange={(event) => setKeyDrafts((prev) => ({ ...prev, [provider.id]: event.target.value }))} />
        <IconButton icon={KeyRound} label={t('providers.keySave')} disabled={(keyDrafts[provider.id] ?? '').length === 0} onClick={() => run(async () => { replace(await saveProviderKey(provider.id, keyDrafts[provider.id] ?? '')); setKeyDrafts((prev) => ({ ...prev, [provider.id]: '' })); })} />
        {provider.hasKey ? <IconButton icon={Trash2} label={t('providers.keyDelete')} onClick={() => run(async () => replace(await deleteProviderKey(provider.id)))} /> : null}
      </div>
      {refresh[provider.id] !== undefined ? (
        <p className="providers__notice" role="status">
          <span className="providers__notice-label">{t('providers.refreshAdded')}</span>
          <strong className="providers__notice-value">{refresh[provider.id].added}</strong>
          <span className="providers__notice-label">{t('providers.refreshTotal')}</span>
          <strong className="providers__notice-value">{refresh[provider.id].total}</strong>
        </p>
      ) : null}
    </>
  );

  if (loadError !== null) {
    return <GlassPanel className="settings-page"><p className="settings-page__error" role="alert">{t(loadError)}</p></GlassPanel>;
  }

  return (
    <GlassPanel className="settings-page">
      <div className="settings-page__head">
        <h2 className="settings-page__title">{t('settings.sections.providers')}</h2>
        <IconButton icon={Plus} label={t('providers.add')} onClick={() => openEditor('new')} />
      </div>
      {editor === 'new' ? editorForm : null}
      {providers !== null && providers.length === 0 ? <p className="settings-page__note">{t('providers.empty')}</p> : null}
      {actionError !== null ? <p className="settings-page__error" role="alert">{t(actionError)}</p> : null}
      <ul className="providers__list">
        {(providers ?? []).map((provider) => <li key={provider.id} className="providers__item">{editor === provider.id ? editorForm : providerRow(provider)}</li>)}
      </ul>
      {deleteTarget !== null ? (
        <Dialog open title={t('providers.deleteTitle')} closeLabel={t('providers.cancel')} onClose={() => setDeleteTarget(null)}>
          <p className="settings-page__note">{t('providers.deleteBody')}</p>
          {(providers ?? []).length <= 1 ? <p className="settings-page__warning" role="alert">{t('providers.deleteLastWarning')}</p> : null}
          <div className="providers__actions">
            <IconButton icon={Trash2} label={t('providers.deleteConfirm')} variant="solid" onClick={() => run(async () => { await deleteProvider(deleteTarget.id); setProviders((prev) => (prev === null ? prev : prev.filter((item) => item.id !== deleteTarget.id))); setDeleteTarget(null); })} />
          </div>
        </Dialog>
      ) : null}
    </GlassPanel>
  );
}
