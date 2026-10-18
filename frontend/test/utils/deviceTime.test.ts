import { describe, expect, it } from "vitest";
import { deviceTimeZone, formatDateOnly, formatDateTime } from "../../src/utils/deviceTime";

const sameInstantDifferentDay = Date.UTC(2026, 2, 7, 15, 0, 0);

describe("设备时间展示", () => {
  it("设备时区现场读取，不写死", () => {
    const zone = deviceTimeZone();
    expect(zone.length).toBeGreaterThan(0);
    expect(() => new Intl.DateTimeFormat("zh-CN", { timeZone: zone })).not.toThrow();
  });

  it("同一时刻在两个设备时区落到不同自然日（UTC+14 与 UTC-11）", () => {
    const east = formatDateTime(sameInstantDifferentDay, { timeZone: "Pacific/Kiritimati" });
    const west = formatDateTime(sameInstantDifferentDay, { timeZone: "Pacific/Niue" });
    expect(east.startsWith("2026-03-08 05:00:00")).toBe(true);
    expect(west.startsWith("2026-03-07 04:00:00")).toBe(true);
    expect(east.slice(0, 10)).not.toBe(west.slice(0, 10));
  });

  it("夏令时地区在同一时区下随月份偏移一小时", () => {
    const winter = formatDateTime(Date.UTC(2026, 0, 12, 12, 0, 0), { timeZone: "America/New_York" });
    const summer = formatDateTime(Date.UTC(2026, 6, 12, 12, 0, 0), { timeZone: "America/New_York" });
    expect(winter.startsWith("2026-01-12 07:00:00")).toBe(true);
    expect(summer.startsWith("2026-07-12 08:00:00")).toBe(true);
  });

  it("下午时刻输出 24 小时制，不退化成 12 小时制", () => {
    // 只断言上午时刻无法区分 h23 与 h12：15:00 必须仍读作 15，而不是 03（AM/PM 分量根本不参与输出）。
    const afternoon = formatDateTime(Date.UTC(2026, 2, 7, 15, 4, 9), { timeZone: "UTC" });
    expect(afternoon).toBe("2026-03-07 15:04:09");
    expect(afternoon.slice(11, 13)).toBe("15");
    expect(afternoon).not.toMatch(/上午|下午|AM|PM/);
  });

  it("时间点不合法时拒绝展示，而不是退回某个固定时区", () => {
    for (const bad of [0, -1, Number.NaN, 1_700_000_000_000.5]) {
      expect(() => formatDateTime(bad, { timeZone: "UTC" })).toThrow(RangeError);
    }
  });

  it("date-only 值原样保留，非法形状拒绝", () => {
    expect(formatDateOnly("2026-03-07")).toBe("2026-03-07");
    expect(() => formatDateOnly("2026-3-7")).toThrow(RangeError);
    expect(() => formatDateOnly("2026-03-07T12:00:00Z")).toThrow(RangeError);
  });
});
