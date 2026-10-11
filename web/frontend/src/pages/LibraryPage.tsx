import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError, FALLBACK_ERROR_KEY } from '../api/errors';
import type { Branch, SearchField, SearchHit, SearchResult, TopicSummary } from '../api/types';
import { GlassPanel } from '../components/GlassPanel';
import { IconButton } from '../components/IconButton';
import { ChevronRight, RotateCcw, Search } from '../components/icons';
import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { formatDateTime, resolveDeviceTimeZone } from '../platform/datetime';
import { navigate } from '../platform/router';
import { loadRecentTopics } from '../store/homeData';
import { archivedRoots, restoreBranch, searchLibrary, unreadRollup } from '../store/libraryData';
import { loadBranchTree } from '../store/topicData';

// 学习库（任务 T-2026-10-10-044）：应用内搜索（三范围分组 + 命中高亮 + Ctrl/Cmd+K）、
// 主题列表（未读汇总来自分支树）与归档区（恢复动作）。文案全走 i18n，按钮 icon-only，
// 时间按设备时区经 platform/datetime 展示；归档分支按可见性规则从默认列表隐藏。
type Scope = 'topic' | 'branch' | 'message';

const SCOPES: readonly Scope[] = ['topic', 'branch', 'message'];

function errorKey(error: unknown): TranslationKey {
  return error instanceof ApiError ? error.i18nKey : FALLBACK_ERROR_KEY;
}

// 命中范围按字段归入三范围：标题→主题、引用锚点→分支、其余→消息。
function scopeOf(field: SearchField): Scope {
  if (field === 'TITLE') return 'topic';
  if (field === 'ANCHOR') return 'branch';
  return 'message';
}

// 命中区间为相对 snippet 的 UTF-16 半开区间；越界时收敛，避免切串越界。
function highlight(hit: SearchHit): { before: string; match: string; after: string } {
  const start = Math.max(0, Math.min(hit.start, hit.snippet.length));
  const end = Math.max(start, Math.min(hit.end, hit.snippet.length));
  return { before: hit.snippet.slice(0, start), match: hit.snippet.slice(start, end), after: hit.snippet.slice(end) };
}

export function LibraryPage() {
  const { t, locale } = useI18n();
  const timeZone = resolveDeviceTimeZone();
  const inputRef = useRef<HTMLInputElement>(null);
  const [topics, setTopics] = useState<TopicSummary[] | null>(null);
  const [trees, setTrees] = useState<Record<string, Branch[]>>({});
  const [loadError, setLoadError] = useState<TranslationKey | null>(null);
  const [query, setQuery] = useState('');
  const [result, setResult] = useState<SearchResult | null>(null);
  const [searchError, setSearchError] = useState<TranslationKey | null>(null);
  const [actionError, setActionError] = useState<TranslationKey | null>(null);

  const load = useCallback(async () => {
    try {
      const list = await loadRecentTopics();
      const entries = await Promise.all(list.map(async (topic) => [topic.topicId, (await loadBranchTree(topic.topicId)).nodes] as const));
      setTopics(list);
      setTrees(Object.fromEntries(entries));
      setLoadError(null);
    } catch (error: unknown) {
      setTopics([]);
      setTrees({});
      setLoadError(errorKey(error));
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  // Ctrl/Cmd+K 聚焦搜索框。
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        inputRef.current?.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, []);

  async function handleSearch() {
    const trimmed = query.trim();
    setSearchError(null);
    if (trimmed.length === 0) {
      setResult(null);
      return;
    }
    try {
      setResult(await searchLibrary(trimmed));
    } catch (error: unknown) {
      setResult(null);
      setSearchError(errorKey(error));
    }
  }

  async function handleRestore(branchId: string) {
    setActionError(null);
    try {
      await restoreBranch(branchId);
      await load();
    } catch (error: unknown) {
      setActionError(errorKey(error));
    }
  }

  const archived = Object.values(trees).flatMap((nodes) => archivedRoots(nodes));
  const trimmedQuery = query.trim();
  const grouped = result === null ? [] : SCOPES.map((scope) => ({ scope, hits: result.hits.filter((hit) => scopeOf(hit.field) === scope) }));

  return (
    <div className="library">
      <section className="library__search" aria-label={t('library.search.placeholder')}>
        <form className="library__search-bar" onSubmit={(event) => { event.preventDefault(); void handleSearch(); }}>
          <input ref={inputRef} className="library__search-input" type="search" aria-label={t('library.search.placeholder')} placeholder={t('library.search.placeholder')} value={query} onChange={(event) => setQuery(event.target.value)} />
          <IconButton icon={Search} label={t('library.search.submit')} type="submit" />
        </form>
        {searchError !== null ? <p className="library__error" role="alert">{t(searchError)}</p> : null}
        {result === null && searchError === null && trimmedQuery.length === 0 ? <p className="library__note">{t('library.search.empty')}</p> : null}
        {result !== null && result.hits.length === 0 ? <p className="library__note">{t('library.search.noResults')}</p> : null}
        {grouped.map(({ scope, hits }) => (hits.length === 0 ? null : (
          <div className="library__group" key={scope}>
            <h3 className="library__group-title">{t(`library.search.group.${scope}` as TranslationKey)}</h3>
            <ul className="library__hits">
              {hits.map((hit) => {
                const parts = highlight(hit);
                return (
                  <li key={`${hit.field}-${hit.topicId}-${hit.branchId}-${hit.messageId ?? ''}-${hit.revisionId ?? ''}-${hit.start}`} className="library__hit">
                    <p className="library__snippet">{parts.before}<mark className="library__mark">{parts.match}</mark>{parts.after}</p>
                    {hit.messageId !== null ? <span className="library__location"><span>{t('library.search.ordinal')}</span><strong>{hit.messageOrdinal}</strong></span> : null}
                    <IconButton icon={ChevronRight} label={t('library.search.open')} onClick={() => navigate(`/topic/${hit.topicId}?branch=${hit.branchId}`)} />
                  </li>
                );
              })}
            </ul>
          </div>
        )))}
      </section>

      <section className="library__section" aria-label={t('library.topics.title')}>
        <h3 className="library__section-title">{t('library.topics.title')}</h3>
        {loadError !== null ? <p className="library__error" role="alert">{t(loadError)}</p> : null}
        {topics !== null && topics.length === 0 && loadError === null ? <p className="library__note">{t('library.topics.empty')}</p> : null}
        <ul className="library__topic-list">
          {(topics ?? []).map((topic) => {
            const title = topic.displayTitle ?? t('library.topics.untitled');
            return (
              <li key={topic.topicId} className="library__topic">
                <GlassPanel className="library__topic-card">
                  <span className="library__topic-title">{title}</span>
                  <span className="library__meta"><span>{t('library.topic.lastUsed')}</span><strong>{formatDateTime(topic.lastUsedAtMillis, locale, timeZone)}</strong></span>
                  <span className="library__meta"><span>{t('library.topic.unread')}</span><strong>{unreadRollup(trees[topic.topicId] ?? [])}</strong></span>
                  <IconButton icon={ChevronRight} label={t('library.topic.open')} onClick={() => navigate(`/topic/${topic.topicId}`)} />
                </GlassPanel>
              </li>
            );
          })}
        </ul>
      </section>

      <section className="library__section" aria-label={t('library.archive.title')}>
        <h3 className="library__section-title">{t('library.archive.title')}</h3>
        {actionError !== null ? <p className="library__error" role="alert">{t(actionError)}</p> : null}
        {archived.length === 0 ? <p className="library__note">{t('library.archive.empty')}</p> : null}
        <ul className="library__archive-list">
          {archived.map((branch) => (
            <li key={branch.id} className="library__archive-item">
              <span className="library__topic-title">{branch.title ?? t('library.archive.untitled')}</span>
              <span className="library__meta">{formatDateTime(branch.updatedAtMillis, locale, timeZone)}</span>
              <IconButton icon={RotateCcw} label={t('library.archive.restore')} onClick={() => void handleRestore(branch.id)} />
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
