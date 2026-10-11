import { useEffect, useRef, useState } from 'react';

import { subscribeOperationEvents } from '../api/sse';
import { useI18n } from '../i18n/I18nProvider';
import { loadDerivedArtifact, operationEventsUrl, startTranslation } from '../store/topicData';

type Phase = 'idle' | 'running' | 'done' | 'failed';

/**
 * T-053 translation block: clicking the button starts a MESSAGE_TRANSLATION
 * derived operation (T-052 slice endpoint), subscribes via
 * `subscribeOperationEvents` until the terminal frame, then reads back the
 * artifact. Self-contained subscription (detached on unmount); failures are
 * retryable.
 */
export function TranslationBlock({ messageId, revisionId }: { messageId: string; revisionId: string }) {
  const { t } = useI18n();
  const [phase, setPhase] = useState<Phase>('idle');
  const [text, setText] = useState('');
  const unsubscribeRef = useRef<(() => void) | null>(null);

  useEffect(() => () => { unsubscribeRef.current?.(); }, []);

  function run() {
    if (phase === 'running') return;
    setPhase('running');
    setText('');
    startTranslation(revisionId).then((operation) => {
      unsubscribeRef.current = subscribeOperationEvents({
        operationId: operation.id,
        url: operationEventsUrl(operation.id),
        handlers: {
          onTerminal: (terminal) => {
            unsubscribeRef.current?.();
            unsubscribeRef.current = null;
            if (terminal.kind !== 'stable') {
              setPhase('failed');
              return;
            }
            loadDerivedArtifact('MESSAGE_TRANSLATION', revisionId, revisionId).then((artifact) => {
              const payload = artifact.payload as { text?: string };
              setText(typeof payload?.text === 'string' ? payload.text : JSON.stringify(artifact.payload));
              setPhase('done');
            }).catch(() => setPhase('failed'));
          },
        },
        onError: () => {
          unsubscribeRef.current?.();
          unsubscribeRef.current = null;
          setPhase('failed');
        },
      });
    }).catch(() => setPhase('failed'));
  }

  if (phase === 'done') {
    return (
      <div className="translation-block" data-testid={`translation-${messageId}`}>
        <p className="translation-block__label">{t('workspace.translation.label')}</p>
        <p className="translation-block__text">{text}</p>
      </div>
    );
  }
  return (
    <div className="translation-block">
      <button type="button" className="translation-block__button" data-testid={`translation-start-${messageId}`}
        disabled={phase === 'running'} onClick={run}>
        {phase === 'running' ? t('workspace.translation.running') : t('workspace.translation.action')}
      </button>
      {phase === 'failed' ? <span className="translation-block__error">{t('workspace.translation.failed')}</span> : null}
    </div>
  );
}
