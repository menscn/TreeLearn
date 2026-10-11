import { useEffect, useState } from 'react';

import { ApiError, FALLBACK_ERROR_KEY } from '../api/errors';
import { MODEL_BUDGETS, MODEL_ROLE_KEYS, type Model, type ModelBudget, type ModelFallback, type ModelResolution, type ModelRoleKey, type ModelRoles } from '../api/types';
import { GlassPanel } from '../components/GlassPanel';
import { IconButton } from '../components/IconButton';
import { Check, Lightbulb } from '../components/icons';
import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { loadModelResolution, loadModelRoles, loadProviderModels, loadProviders, saveModelRoles } from '../store/settingsData';

// 模型分工（任务 T-2026-10-10-039）：CHAT/FAST/TRANSLATION 的模型选择、预算档位与建议开关，
// 保存调用 `PUT /api/settings/model-roles`；用 `GET /api/settings/model-resolution` 展示回落
// 状态。`model-roles` 读/写已由 T-2026-10-10-036 实现（数据目录非敏感偏好文件，未新增 schema）；
// `404 NOT_FOUND` 分支保留为防御性处理——端点意外缺失时显示明确不可用提示，绝不静默成功。文案全走 i18n。
const ROLE_LABEL_KEYS: Record<ModelRoleKey, TranslationKey> = {
  CHAT: 'modelRoles.role.CHAT', FAST: 'modelRoles.role.FAST', TRANSLATION: 'modelRoles.role.TRANSLATION',
};
const BUDGET_LABEL_KEYS: Record<ModelBudget, TranslationKey> = {
  OFF: 'modelRoles.budget.OFF', AUTO: 'modelRoles.budget.AUTO', LOW: 'modelRoles.budget.LOW', MID: 'modelRoles.budget.MID', HIGH: 'modelRoles.budget.HIGH',
};
const FALLBACK_LABEL_KEYS: Record<ModelFallback, TranslationKey> = {
  NONE: 'modelFallback.NONE', BUILTIN: 'modelFallback.BUILTIN', EARLIEST: 'modelFallback.EARLIEST', EMPTY: 'modelFallback.EMPTY',
};

interface DraftEntry { providerId: string | null; modelId: string | null }
type DraftRoles = Record<ModelRoleKey, DraftEntry>;

function emptyDraft(): DraftRoles {
  return { CHAT: { providerId: null, modelId: null }, FAST: { providerId: null, modelId: null }, TRANSLATION: { providerId: null, modelId: null } };
}

function optionValue(entry: DraftEntry): string {
  return entry.providerId === null || entry.modelId === null ? '' : `${entry.providerId}::${entry.modelId}`;
}

function parseOptionValue(value: string): DraftEntry {
  const parts = value.split('::');
  return parts.length === 2 ? { providerId: parts[0], modelId: parts[1] } : { providerId: null, modelId: null };
}

export function ModelRolesPage() {
  const { t } = useI18n();
  const [roles, setRoles] = useState<ModelRoles | null>(null);
  const [resolution, setResolution] = useState<ModelResolution | null>(null);
  const [models, setModels] = useState<Model[]>([]);
  const [draft, setDraft] = useState<DraftRoles>(emptyDraft());
  const [budget, setBudget] = useState<ModelBudget>('AUTO');
  const [suggestions, setSuggestions] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  const [loadError, setLoadError] = useState<TranslationKey | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState<TranslationKey | null>(null);

  useEffect(() => {
    let active = true;
    const loadModels = loadProviders()
      .then((providers) => Promise.all(providers.filter((provider) => provider.enabled).map((provider) => loadProviderModels(provider.id).catch(() => [] as Model[]))))
      .then((lists) => lists.flat())
      .catch(() => [] as Model[]);
    Promise.all([loadModelRoles(), loadModelResolution().catch(() => null), loadModels])
      .then(([loadedRoles, loadedResolution, loadedModels]) => {
        if (!active) return;
        setRoles(loadedRoles);
        const next = emptyDraft();
        for (const role of MODEL_ROLE_KEYS) next[role] = { providerId: loadedRoles.roles[role].providerId, modelId: loadedRoles.roles[role].modelId };
        setDraft(next);
        setBudget(loadedRoles.budget);
        setSuggestions(loadedRoles.suggestions);
        setResolution(loadedResolution);
        setModels(loadedModels);
      })
      .catch((error: unknown) => {
        if (!active) return;
        setRoles(null);
        if (error instanceof ApiError && error.code === 'NOT_FOUND') setUnavailable(true);
        else setLoadError(error instanceof ApiError ? error.i18nKey : FALLBACK_ERROR_KEY);
      });
    return () => { active = false; };
  }, []);

  async function handleSave() {
    setSaveError(null);
    setSaved(false);
    setSaving(true);
    try {
      const result = await saveModelRoles({ roles: draft, budget, suggestions });
      setRoles(result);
      setSaved(true);
    } catch (error: unknown) {
      setSaveError(error instanceof ApiError && error.code === 'NOT_FOUND' ? 'modelRoles.unavailable' : error instanceof ApiError ? error.i18nKey : FALLBACK_ERROR_KEY);
    } finally {
      setSaving(false);
    }
  }

  if (unavailable) return <GlassPanel className="settings-page"><p className="settings-page__warning" role="alert">{t('modelRoles.unavailable')}</p></GlassPanel>;
  if (loadError !== null) return <GlassPanel className="settings-page"><p className="settings-page__error" role="alert">{t(loadError)}</p></GlassPanel>;
  if (roles === null) return <GlassPanel className="settings-page"><p className="settings-page__note">{t('modelRoles.loading')}</p></GlassPanel>;

  return (
    <GlassPanel className="settings-page">
      <div className="settings-page__head">
        <h2 className="settings-page__title">{t('settings.sections.modelRoles')}</h2>
      </div>
      {models.length === 0 ? <p className="settings-page__warning" role="status">{t('modelRoles.noProviders')}</p> : null}
      <ul className="model-roles__list">
        {MODEL_ROLE_KEYS.map((role) => (
          <li key={role} className="model-roles__row">
            <div className="model-roles__field">
              <span className="model-roles__label">{t(ROLE_LABEL_KEYS[role])}</span>
              <select className="model-roles__select" aria-label={t(ROLE_LABEL_KEYS[role])} value={optionValue(draft[role])} onChange={(event) => setDraft((prev) => ({ ...prev, [role]: parseOptionValue(event.target.value) }))}>
                <option value="">{t('modelRoles.selectNone')}</option>
                {models.map((model) => <option key={`${model.providerId}::${model.modelId}`} value={`${model.providerId}::${model.modelId}`}>{`${model.providerName} / ${model.modelId}`}</option>)}
              </select>
            </div>
            {resolution !== null ? <span className="model-roles__fallback">{t(FALLBACK_LABEL_KEYS[resolution.roles[role].fallback])}</span> : null}
          </li>
        ))}
      </ul>
      <div className="model-roles__controls">
        <div className="model-roles__field">
          <span className="model-roles__label">{t('modelRoles.budgetLabel')}</span>
          <select className="model-roles__select" aria-label={t('modelRoles.budgetLabel')} value={budget} onChange={(event) => setBudget(event.target.value as ModelBudget)}>
            {MODEL_BUDGETS.map((value) => <option key={value} value={value}>{t(BUDGET_LABEL_KEYS[value])}</option>)}
          </select>
        </div>
        <IconButton icon={Lightbulb} label={t('modelRoles.suggestions')} aria-pressed={suggestions} onClick={() => setSuggestions((value) => !value)} />
        <IconButton icon={Check} label={t('modelRoles.save')} variant="solid" disabled={saving} onClick={handleSave} />
      </div>
      {saved ? <p className="model-roles__saved" role="status">{t('modelRoles.saved')}</p> : null}
      {saveError !== null ? <p className="settings-page__error" role="alert">{t(saveError)}</p> : null}
    </GlassPanel>
  );
}
