import { useCallback, useEffect, useState } from 'react';

import type { Attachment } from '../api/types';
import { useI18n } from '../i18n/I18nProvider';
import { GlassPanel } from '../components/GlassPanel';
import { cleanupOrphanAttachments, deleteAttachment, listAttachments } from '../store/topicData';

/**
 * T-055 文件管理页（settings 分区 /settings/files）：附件列表（引用计数/解析状态）、
 * 全局用量、删除（仅无引用）与孤儿清理（幂等）。删除/清理失败统一走错误条。
 */
export function FilesPage() {
  const { t } = useI18n();
  const [entries, setEntries] = useState<Attachment[]>([]);
  const [usageBytes, setUsageBytes] = useState(0);
  const [error, setError] = useState(false);
  const [busy, setBusy] = useState(false);

  const reload = useCallback(() => {
    listAttachments().then((result) => {
      setEntries(result.attachments);
      setUsageBytes(result.usageBytes);
      setError(false);
    }).catch(() => setError(true));
  }, []);

  useEffect(() => { reload(); }, [reload]);

  function remove(entry: Attachment) {
    if (busy) return;
    setBusy(true);
    deleteAttachment(entry.attachmentId).then(reload).catch(() => setError(true)).finally(() => setBusy(false));
  }

  function cleanup() {
    if (busy) return;
    setBusy(true);
    cleanupOrphanAttachments().then(reload).catch(() => setError(true)).finally(() => setBusy(false));
  }

  return (
    <GlassPanel className="files-page">
      <h2 className="settings-page__title">{t('settings.sections.files')}</h2>
      <p className="files-page__usage" data-testid="files-usage">
        {t('files.usage').replace('{count}', String(entries.length))}
        {' · '}
        {usageBytes.toLocaleString()} {t('files.bytes')}
      </p>
      {error ? <p className="files-page__error" role="alert">{t('files.error')}</p> : null}
      {entries.length === 0 ? (
        <p className="files-page__empty">{t('files.empty')}</p>
      ) : (
        <ul className="files-page__list" data-testid="files-list">
          {entries.map((entry) => (
            <li key={entry.attachmentId} className="files-page__item">
              <a className="files-page__name" href={`/api/attachments/${entry.attachmentId}/content`}
                download={entry.displayName ?? undefined}>{entry.displayName ?? t('home.composer.attachment.unnamed')}</a>
              <span className="files-page__meta">
                {entry.mimeType} · {entry.sizeBytes.toLocaleString()} {t('files.bytes')}
                {' · '}
                {t(entry.referenceCount === 0 ? 'files.unreferenced' : 'files.references').replace('{n}', String(entry.referenceCount))}
                {' · '}
                {t(entry.parseState === 'parsed' ? 'files.state.ready' : entry.parseState === 'parse_failed' ? 'files.state.failed' : 'files.state.pending')}
              </span>
              {entry.referenceCount === 0 ? (
                <button type="button" className="files-page__delete" data-testid={`files-delete-${entry.attachmentId}`}
                  disabled={busy} onClick={() => remove(entry)}>{t('files.delete')}</button>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      <button type="button" className="files-page__cleanup" data-testid="files-cleanup" disabled={busy} onClick={cleanup}>
        {t('files.cleanup')}
      </button>
    </GlassPanel>
  );
}
