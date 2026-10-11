import { afterEach, describe, expect, it } from 'vitest';

import { currentMonth, formatDateTime, formatMonthLabel, resolveDeviceTimeZone, setDeviceTimeZone, shiftMonth } from './datetime';

// Task T-2026-10-10-044: the shared device-time-zone entry point. Real time
// points are formatted in the device zone; date-only values (month/day) are
// never shifted by a time zone.
afterEach(() => setDeviceTimeZone(null));

describe('device time zone formatting', () => {
  // 1700000000000 = 2023-11-14T22:13:20Z -> Shanghai 11-15, Los Angeles 11-14.
  const MILLIS = 1700000000000;

  it('formats a real timestamp by the device time zone', () => {
    const shanghai = formatDateTime(MILLIS, 'zh-CN', 'Asia/Shanghai');
    const losAngeles = formatDateTime(MILLIS, 'zh-CN', 'America/Los_Angeles');
    expect(shanghai).not.toBe(losAngeles);
    expect(shanghai).toContain('15');
    expect(losAngeles).toContain('14');
  });

  it('keeps a date-only month label unshifted across time zones', () => {
    const label = formatMonthLabel('2026-10', 'zh-CN');
    expect(label).toContain('2026');
    expect(label).toBe(formatMonthLabel('2026-10', 'zh-CN'));
  });

  it('derives the current month in the device time zone', () => {
    // 2023-10-31T20:00:00Z -> Shanghai 2023-11, Los Angeles 2023-10.
    const boundary = 1698782400000;
    expect(currentMonth('Asia/Shanghai', boundary)).toBe('2023-11');
    expect(currentMonth('America/Los_Angeles', boundary)).toBe('2023-10');
  });

  it('shifts a date-only month without a time zone', () => {
    expect(shiftMonth('2026-01', -1)).toBe('2025-12');
    expect(shiftMonth('2026-12', 1)).toBe('2027-01');
  });

  it('honours an injected device time zone', () => {
    expect(resolveDeviceTimeZone()).not.toBe('');
    setDeviceTimeZone('Asia/Shanghai');
    expect(resolveDeviceTimeZone()).toBe('Asia/Shanghai');
  });
});
