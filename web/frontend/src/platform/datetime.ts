// 设备时区时间展示的唯一入口（任务 T-2026-10-10-044）。
// 真实时间点（UTC epoch 毫秒）在渲染时按当前设备时区格式化；date-only（month
// "YYYY-MM"、day "YYYY-MM-DD"）只做字符串处理，绝不做时区换算。页面不得直接使用
// toLocale*/Intl.DateTimeFormat 生成展示时间（rules/frontend.md 设备时区与时间展示）。
let overrideTimeZone: string | null = null;

// 测试注入不同设备时区；传 null 恢复为读取浏览器时区。
export function setDeviceTimeZone(timeZone: string | null): void {
  overrideTimeZone = timeZone;
}

export function resolveDeviceTimeZone(): string {
  if (overrideTimeZone !== null) return overrideTimeZone;
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

// 真实时间点：按设备时区与界面语言格式化。
export function formatDateTime(millis: number, locale: string, timeZone: string = resolveDeviceTimeZone()): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(new Date(millis));
}

// date-only 月：以 UTC 正午构造，保证不因时区偏移跨月，再按界面语言显示。
export function formatMonthLabel(month: string, locale: string): string {
  const match = /^(\d{4})-(\d{2})$/.exec(month);
  if (match === null) return month;
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, 1, 12));
  return new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'long', timeZone: 'UTC' }).format(date);
}

// 设备时区下的当前自然月（date-only "YYYY-MM"）。
export function currentMonth(timeZone: string = resolveDeviceTimeZone(), nowMillis: number = Date.now()): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit' }).formatToParts(new Date(nowMillis));
  const year = parts.find((part) => part.type === 'year')?.value ?? '1970';
  const month = parts.find((part) => part.type === 'month')?.value ?? '01';
  return `${year}-${month}`;
}

// date-only 月份加减：纯字符串运算，不经过时区。
export function shiftMonth(month: string, delta: number): string {
  const match = /^(\d{4})-(\d{2})$/.exec(month);
  if (match === null) return month;
  const total = Number(match[1]) * 12 + (Number(match[2]) - 1) + delta;
  return `${String(Math.floor(total / 12)).padStart(4, '0')}-${String((total % 12) + 1).padStart(2, '0')}`;
}
