import { describe, expect, it } from "vitest";
import { parseEnvelope } from "../../src/platform/RuntimePort";
import { bundledEntries, bundledFixtureNames } from "../../src/platform/bundledFixtures";

/**
 * 包内夹具是 Web 侧唯一的数据来源：这里逐条量测「打进包里的字节自己能不能读」，
 * 而不是相信采集日志说它写成功了。夹具名单与键名不一致、或某一条解析不出 envelope，
 * 在浏览器里都会读成「这一屏没有基线」，所以在包内就要判红。
 */
describe("包内夹具每一条都能被自己的端口读回", () => {
  const entries = bundledEntries();
  const names = bundledFixtureNames();

  it("名单非空且与条目键一一对应", () => {
    expect(names.length).toBeGreaterThanOrEqual(13);
    expect(names).toEqual(Object.keys(entries).sort());
  });

  it("逐条 parseEnvelope 判 ok，且 envelope 内的 screen 与条目名一致", () => {
    const bad: string[] = [];
    for (const name of names) {
      const result = parseEnvelope(entries[name] ?? "", name);
      if (!result.ok) bad.push(`${name}: ${result.kind} ${result.detail}`);
      else if (result.envelope.screen !== name) bad.push(`${name}: 内部 screen=${result.envelope.screen}`);
    }
    expect(bad).toEqual([]);
  });

  it("逐条带得出节点与设备事实（bounds 已是 dp，前端不再除密度）", () => {
    for (const name of names) {
      const result = parseEnvelope(entries[name] ?? "", name);
      expect(result.ok, name).toBe(true);
      if (!result.ok) continue;
      const payload = result.envelope.payload as { nodes?: unknown; device?: { scale?: number } };
      const list = Array.isArray(payload.nodes) ? payload.nodes : [];
      expect(Array.isArray(payload.nodes), name).toBe(true);
      expect(list.length, name).toBeGreaterThan(0);
      expect(typeof payload.device?.scale, name).toBe("number");
    }
  });
});
