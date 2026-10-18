import { interfaceLocale } from "../copy";

/**
 * 设备时间展示的唯一入口（`.codex/rules/frontend.md` 设备时区与时间展示）。
 *
 * 状态层保留原始时间点（epoch 毫秒），只在展示这一帧调用本模块；
 * 本地化后的时间文案不得持久化，否则时区变化后旧文案会以「已展示」的形态留在数据里。
 */

/** 只给跨时区对照用例与未来的业务日历工具使用；生产组件一律走设备时区。 */
export interface DeviceZoneOverride {
  readonly timeZone?: string;
}

const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/;

/** 每次调用现读设备时区，不缓存：缓存会让「恢复焦点时时区已变」这条规则失效。 */
export function deviceTimeZone(): string {
  return new Intl.DateTimeFormat(interfaceLocale).resolvedOptions().timeZone;
}

function pad(value: string): string {
  return value.length === 1 ? `0${value}` : value;
}

/** epoch 毫秒 → `YYYY-MM-DD HH:mm:ss`（设备时区、界面语言）。 */
export function formatDateTime(epochMs: number, override: DeviceZoneOverride = {}): string {
  if (!Number.isSafeInteger(epochMs) || epochMs <= 0) {
    throw new RangeError(`时间点只接受正的 epoch 毫秒，收到 ${String(epochMs)}`);  // i18n-ignore: 诊断 detail，只进日志与测试断言，不渲染到 UI（UI 状态文案走 src/copy）
  }
  const parts = new Intl.DateTimeFormat(interfaceLocale, {
    timeZone: override.timeZone ?? deviceTimeZone(),
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23"
  }).formatToParts(new Date(epochMs));
  const pick = (type: Intl.DateTimeFormatPartTypes): string => {
    const found = parts.find((p) => p.type === type);
    if (found === undefined) throw new RangeError(`时区 ${override.timeZone ?? deviceTimeZone()} 未给出 ${type} 分量`);  // i18n-ignore: 诊断 detail，只进日志与测试断言，不渲染到 UI（UI 状态文案走 src/copy）
    return found.value;
  };
  return `${pick("year")}-${pad(pick("month"))}-${pad(pick("day"))} `
    + `${pad(pick("hour"))}:${pad(pick("minute"))}:${pad(pick("second"))}`;
}

/** date-only 值保持原日期，不做任何时区换算（同一条规则的另外半边）。 */
export function formatDateOnly(value: string): string {
  if (!DATE_ONLY.test(value)) {
    throw new RangeError(`date-only 值必须是 YYYY-MM-DD，收到 ${value}`);  // i18n-ignore: 诊断 detail，只进日志与测试断言，不渲染到 UI（UI 状态文案走 src/copy）
  }
  return value;
}
