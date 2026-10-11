import type { KeyboardEvent } from 'react';

import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { GlassPanel } from './GlassPanel';
import { IconButton } from './IconButton';
import { ArrowUp, Lightbulb, Mic, Paperclip, Settings, Square, X } from './icons';
import { startListening, voiceInputAvailable } from '../lib/voiceInput';
import { useEffect, useRef, useState } from 'react';

// composer（任务 T-2026-10-10-030 首问；T-2026-10-10-031 扩展工作区追问）：多行输入、
// 思考开关、发送/停止状态机、可选引用 chip。组件只渲染并回传意图，文案全走 i18n；
// 追问场景通过 placeholderKey/sendKey 覆盖默认文案。Enter 发送、Shift+Enter 换行、Escape 失焦。
interface ComposerProps {
  value: string;
  onValueChange: (value: string) => void;
  onClear: () => void;
  thinkingEnabled: boolean;
  onToggleThinking: () => void;
  canSend: boolean;
  submitting: boolean;
  /** 无可用模型时禁用发送并给可执行引导。 */
  blocked: boolean;
  onSend: () => void;
  onStop: () => void;
  onOpenSettings: () => void;
  /** 当前引用片段（非空时显示引用 chip）。 */
  quote?: string;
  onClearQuote?: () => void;
  /** T-055：待发附件（上传成功后的条目）；null=无附件。 */
  attachment?: { attachmentId: string; displayName: string | null } | null;
  onClearAttachment?: () => void;
  onPickFile?: (file: File) => void;
  placeholderKey?: TranslationKey;
  sendKey?: TranslationKey;
}

export function Composer({ value, onValueChange, onClear, thinkingEnabled, onToggleThinking, canSend, submitting, blocked, onSend, onStop, onOpenSettings, quote = '', onClearQuote, attachment = null, onClearAttachment, onPickFile, placeholderKey = 'home.composer.placeholder', sendKey = 'home.composer.send' }: ComposerProps) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const { t } = useI18n();
  const [listening, setListening] = useState(false);
  const handleRef = useRef<{ stop: () => void } | null>(null);
  useEffect(() => () => handleRef.current?.stop(), []);

  function toggleDictation() {
    if (listening) {
      handleRef.current?.stop();
      handleRef.current = null;
      setListening(false);
      return;
    }
    const handle = startListening({
      onFinal: (text) => onValueChange((value === '' ? '' : `${value} `) + text),
      onInterim: (text) => { if (value === '') onValueChange(text); },
      onError: () => { handleRef.current = null; setListening(false); },
    });
    if (handle !== null) {
      handleRef.current = handle;
      setListening(true);
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      if (canSend && !submitting) onSend();
    } else if (event.key === 'Escape') event.currentTarget.blur();
  }

  return (
    <GlassPanel variant="raised" className="composer">
      {attachment !== null ? (
        <div className="composer__attachment" data-testid="composer-attachment">
          <span className="composer__attachment-name">{attachment.displayName ?? t('home.composer.attachment.unnamed')}</span>
          {onClearAttachment ? <IconButton icon={X} label={t('home.composer.attachment.clear')} onClick={onClearAttachment} /> : null}
        </div>
      ) : null}
      {quote.length > 0 ? (
        <div className="composer__quote">
          <span className="composer__quote-text">{quote}</span>
          <IconButton icon={X} label={t('workspace.quote.clear')} onClick={onClearQuote} />
        </div>
      ) : null}
      <textarea className="composer__input" rows={3} value={value} onKeyDown={handleKeyDown} onChange={(event) => onValueChange(event.target.value)} aria-label={t(placeholderKey)} placeholder={t(placeholderKey)} />
      <div className="composer__row">
        <div className="composer__tools">
          {onPickFile ? (
          <>
            <input ref={fileInputRef} type="file" className="composer__file-input" data-testid="composer-file-input"
              aria-hidden="true" tabIndex={-1}
              onChange={(event) => {
                const file = event.currentTarget.files?.[0];
                event.currentTarget.value = '';
                if (file) onPickFile(file);
              }} />
            <IconButton icon={Paperclip} label={t('home.composer.attachment.attach')} onClick={() => fileInputRef.current?.click()} />
          </>
        ) : null}
        {voiceInputAvailable() ? (
          <IconButton icon={Mic} label={t(listening ? 'home.composer.dictation.stop' : 'home.composer.dictation.start')}
            aria-pressed={listening} onClick={toggleDictation} />
        ) : null}
        <IconButton icon={Lightbulb} label={t('home.composer.thinking')} aria-pressed={thinkingEnabled} onClick={onToggleThinking} />
          {value.length > 0 ? <IconButton icon={X} label={t('home.composer.clear')} onClick={onClear} /> : null}
        </div>
        {submitting
          ? <IconButton icon={Square} label={t('home.composer.stop')} variant="solid" onClick={onStop} />
          : <IconButton icon={ArrowUp} label={t(sendKey)} variant="solid" disabled={!canSend} onClick={onSend} />}
      </div>
      {blocked ? (
        <p className="composer__notice" role="status">
          {t('home.noKey')}
          <IconButton icon={Settings} label={t('home.gotoSettings')} onClick={onOpenSettings} />
        </p>
      ) : null}
    </GlassPanel>
  );
}