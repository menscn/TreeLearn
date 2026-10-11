import { useEffect, useState } from 'react';

import { ApiError, FALLBACK_ERROR_KEY } from '../api/errors';
import type { Stats } from '../api/types';
import { GlassPanel } from '../components/GlassPanel';
import { IconButton } from '../components/IconButton';
import { ChevronLeft, ChevronRight } from '../components/icons';
import type { TranslationKey } from '../i18n';
import { useI18n } from '../i18n/I18nProvider';
import { currentMonth, formatMonthLabel, resolveDeviceTimeZone, shiftMonth } from '../platform/datetime';
import { loadStats } from '../store/libraryData';

// 学习统计（任务 T-2026-10-10-044）：累计提问/消息/连续学习天数 + 当月热力；月份可切换。
// 请求按设备时区计算自然月；`month` 为 date-only，展示不做时区换算。空月显示零值态。
function errorKey(error: unknown): TranslationKey {
  return error instanceof ApiError ? error.i18nKey : FALLBACK_ERROR_KEY;
}

function cellClass(value: number): string {
  if (value < 0) return 'stats__cell stats__cell--out';
  return value > 0 ? 'stats__cell stats__cell--active' : 'stats__cell';
}

// timeZone/month 可显式注入以便测试跨设备时区；缺省时读取设备时区与当前自然月。
export function StatsPage({ timeZone, month }: { timeZone?: string; month?: string }) {
  const { t, locale } = useI18n();
  const zone = timeZone ?? resolveDeviceTimeZone();
  const [selected, setSelected] = useState(() => month ?? currentMonth(zone));
  const [stats, setStats] = useState<Stats | null>(null);
  const [error, setError] = useState<TranslationKey | null>(null);

  useEffect(() => {
    let active = true;
    setStats(null);
    setError(null);
    loadStats(zone, selected).then((value) => { if (active) setStats(value); }).catch((err: unknown) => { if (active) setError(errorKey(err)); });
    return () => { active = false; };
  }, [zone, selected]);

  const emptyMonth = stats !== null && stats.monthCells.every((cell) => cell <= 0);

  return (
    <GlassPanel className="stats">
      <div className="stats__head">
        <h2 className="stats__title">{t('stats.title')}</h2>
        <div className="stats__months">
          <IconButton icon={ChevronLeft} label={t('stats.prevMonth')} onClick={() => setSelected((value) => shiftMonth(value, -1))} />
          <span className="stats__month">{formatMonthLabel(selected, locale)}</span>
          <IconButton icon={ChevronRight} label={t('stats.nextMonth')} onClick={() => setSelected((value) => shiftMonth(value, 1))} />
        </div>
      </div>
      {error !== null ? <p className="stats__error" role="alert">{t(error)}</p> : null}
      {stats === null ? (error === null ? <p className="stats__note">{t('stats.loading')}</p> : null) : (
        <>
          <ul className="stats__totals">
            <li className="stats__total"><span className="stats__total-label">{t('stats.questions')}</span><strong className="stats__total-value">{stats.totalQuestions}</strong></li>
            <li className="stats__total"><span className="stats__total-label">{t('stats.messages')}</span><strong className="stats__total-value">{stats.totalMessages}</strong></li>
            <li className="stats__total"><span className="stats__total-label">{t('stats.streak')}</span><strong className="stats__total-value">{stats.currentStreakDays}</strong></li>
          </ul>
          <ul className="stats__heatmap" aria-label={t('stats.heatmap')}>
            {stats.monthCells.map((cell, index) => <li key={index} className={cellClass(cell)} />)}
          </ul>
          {emptyMonth ? <p className="stats__note">{t('stats.emptyMonth')}</p> : null}
        </>
      )}
    </GlassPanel>
  );
}
