import type { KeyboardEvent } from 'react';

import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { GlassPanel } from './GlassPanel';
import { IconButton } from './IconButton';
import { ArrowUp, Lightbulb, Settings, Square, X } from './icons';

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
  placeholderKey?: TranslationKey;
  sendKey?: TranslationKey;
}

export function Composer({ value, onValueChange, onClear, thinkingEnabled, onToggleThinking, canSend, submitting, blocked, onSend, onStop, onOpenSettings, quote = '', onClearQuote, placeholderKey = 'home.composer.placeholder', sendKey = 'home.composer.send' }: ComposerProps) {
  const { t } = useI18n();

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      if (canSend && !submitting) onSend();
    } else if (event.key === 'Escape') event.currentTarget.blur();
  }

  return (
    <GlassPanel variant="raised" className="composer">
      {quote.length > 0 ? (
        <div className="composer__quote">
          <span className="composer__quote-text">{quote}</span>
          <IconButton icon={X} label={t('workspace.quote.clear')} onClick={onClearQuote} />
        </div>
      ) : null}
      <textarea className="composer__input" rows={3} value={value} onKeyDown={handleKeyDown} onChange={(event) => onValueChange(event.target.value)} aria-label={t(placeholderKey)} placeholder={t(placeholderKey)} />
      <div className="composer__row">
        <div className="composer__tools">
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