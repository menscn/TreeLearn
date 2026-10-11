import { useCallback, useEffect, useRef, useState } from 'react';

import { ApiError, FALLBACK_ERROR_KEY } from '../api/errors';
import type { TopicSummary } from '../api/types';
import { Composer } from '../components/Composer';
import { GlassPanel } from '../components/GlassPanel';
import { IconButton } from '../components/IconButton';
import { ChevronRight } from '../components/icons';
import { useDraft } from '../hooks/useDraft';
import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { navigate } from '../platform/router';
import { HOME_DRAFT_KEY } from '../store/drafts';
import { loadHomeConfig, loadRecentTopics, submitFirstQuestion, type HomeConfig } from '../store/homeData';

function errorKey(error: unknown): TranslationKey {
  return error instanceof ApiError ? error.i18nKey : FALLBACK_ERROR_KEY;
}

/**
 * 主页（任务 T-2026-10-10-030）：hero + 首问 composer + 最近主题卡。首问 POST /api/topics
 * 接受后跳转 /topic/:id；真实流式与工作区在后续任务，故“停止”在此只取消客户端等待并抑制结果，
 * 不涉及 SSE。主题卡显示 `TopicSummary.displayTitle`（= 根分支标题，缺失时回退根问题；契约 §5.1b）。
 */
export function HomePage() {
  const { t } = useI18n();
  const { text, setText, clear } = useDraft(HOME_DRAFT_KEY);
  const [thinkingEnabled, setThinkingEnabled] = useState(false);
  const [config, setConfig] = useState<HomeConfig | null>(null);
  const [topics, setTopics] = useState<TopicSummary[] | null>(null);
  const [topicsError, setTopicsError] = useState<TranslationKey | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<TranslationKey | null>(null);
  const cancelled = useRef(false);

  useEffect(() => {
    let active = true;
    loadHomeConfig().then((value) => active && setConfig(value)).catch(() => active && setConfig({ usable: false, model: null }));
    loadRecentTopics().then((value) => active && setTopics(value)).catch((error: unknown) => { if (active) { setTopics([]); setTopicsError(errorKey(error)); } });
    return () => { active = false; };
  }, []);

  const canSend = config?.usable === true && text.trim().length > 0 && !submitting;
  const blocked = config !== null && !config.usable;

  const handleSend = useCallback(async () => {
    const model = config?.model ?? null;
    if (model === null || text.trim().length === 0) return;
    setSubmitError(null);
    setSubmitting(true);
    cancelled.current = false;
    try {
      const result = await submitFirstQuestion({ question: text, model, thinkingEnabled });
      if (cancelled.current) return;
      clear();
      navigate(`/topic/${result.topic.id}`);
    } catch (error: unknown) {
      if (!cancelled.current) setSubmitError(errorKey(error));
    } finally {
      if (!cancelled.current) setSubmitting(false);
    }
  }, [config, text, thinkingEnabled, clear]);

  const handleStop = useCallback(() => { cancelled.current = true; setSubmitting(false); }, []);

  return (
    <div className="home">
      <section className="home__hero">
        <h2 className="home__title">{t('home.title')}</h2>
        <p className="home__subtitle">{t('home.subtitle')}</p>
        <Composer value={text} onValueChange={setText} onClear={clear} thinkingEnabled={thinkingEnabled} onToggleThinking={() => setThinkingEnabled((value) => !value)} canSend={canSend} submitting={submitting} blocked={blocked} onSend={handleSend} onStop={handleStop} onOpenSettings={() => navigate('/settings/providers')} />
        {submitError ? <p className="home__error" role="alert">{t(submitError)}</p> : null}
      </section>
      <section className="home__recent" aria-label={t('home.recent.title')}>
        <h3 className="home__recent-title">{t('home.recent.title')}</h3>
        {topicsError ? <p className="home__error" role="alert">{t(topicsError)}</p> : null}
        {topics !== null && topics.length === 0 && topicsError === null ? <p className="home__empty">{t('home.recent.empty')}</p> : null}
        <ul className="home__topics">
          {(topics ?? []).map((topic) => {
            const title = topic.displayTitle ?? t('home.recent.topic');
            return (
              <li key={topic.topicId} className="home__topic">
                <GlassPanel className="home__topic-card">
                  <span className="home__topic-label">{title}</span>
                  <IconButton icon={ChevronRight} label={title} onClick={() => navigate(`/topic/${topic.topicId}`)} />
                </GlassPanel>
              </li>
            );
          })}
        </ul>
      </section>
    </div>
  );
}