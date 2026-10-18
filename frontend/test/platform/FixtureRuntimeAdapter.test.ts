import { describe, expect, it } from "vitest";
import { FixtureRuntimeAdapter, createMockRuntime, memoryFixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import type { FixtureSource } from "../../src/platform/FixtureRuntimeAdapter";
import type { SnapshotFailure, SnapshotResult } from "../../src/platform/RuntimePort";
import { parseEnvelope } from "../../src/platform/RuntimePort";
import { injectRuntime, runtime } from "../../src/platform/runtime";

const COMMIT = "0123456789abcdef0123456789abcdef01234567";

function envelope(overrides: Record<string, unknown> = {}): string {
  const base: Record<string, unknown> = {
    schema_version: 1,
    screen: "ChatScreen",
    source_commit: COMMIT,
    captured_at_ms: 1_700_000_000_000,
    device: "ALP-AN00",
    payload: { title: "x" }
  };
  return JSON.stringify({ ...base, ...overrides });
}

function failing(result: SnapshotResult): SnapshotFailure {
  if (result.ok) throw new Error("expected a failure, got ok");
  return result;
}

describe("parseEnvelope", () => {
  it("接受一份完整的夹具并原样带出 payload", () => {
    const parsed = parseEnvelope(envelope({ payload: { rows: [1, 2] } }), "ChatScreen");
    expect(parsed.ok).toBe(true);
    if (parsed.ok) expect(parsed.envelope.payload).toEqual({ rows: [1, 2] });
  });

  it("screen 字段与名单条目不一致时判 malformed 并点名两边", () => {
    const bad = failing(parseEnvelope(envelope({ screen: "HomeScreen" }), "ChatScreen"));
    expect(bad.kind).toBe("malformed-fixture");
    expect(bad.detail).toContain("HomeScreen");
  });

  it("时间点写成无 offset 的本地串判 malformed", () => {
    expect(failing(parseEnvelope(envelope({ captured_at_ms: "2026-10-18 19:30" }), "ChatScreen")).kind)
      .toBe("malformed-fixture");
  });

  it("采集设备缺失判 malformed：环境是覆盖面的一个维度", () => {
    expect(failing(parseEnvelope(envelope({ device: "" }), "ChatScreen")).detail).toContain("device");
  });

  it("缺任一必需字段都判 malformed 且明细说出缺哪个", () => {
    for (const field of ["schema_version", "source_commit", "captured_at_ms", "payload"]) {
      const raw = JSON.parse(envelope());
      delete (raw as Record<string, unknown>)[field];
      const bad = failing(parseEnvelope(JSON.stringify(raw), "ChatScreen"));
      expect(bad.kind).toBe("malformed-fixture");
      // 必须点名「缺少必需字段」这一层，否则「类型检查顺带拒掉」会被读成「存在性检查生效」。
      expect(bad.detail).toContain("缺少必需字段");
      expect(bad.detail).toContain(field);
    }
  });
});

describe("FixtureRuntimeAdapter", () => {
  it("screens() 给出排序后的名单", async () => {
    const adapter = new FixtureRuntimeAdapter(memoryFixtureSource({
      HomeScreen: envelope({ screen: "HomeScreen" }),
      ChatScreen: envelope()
    }));
    expect(await adapter.screens()).toEqual(["ChatScreen", "HomeScreen"]);
  });

  it("三类失败互不混同：同一组输入逐条比对判决各不相同", async () => {
    const boom: FixtureSource = {
      list: async () => ["BrokenScreen"],
      read: async () => {
        throw new Error("disk gone");
      }
    };
    const nullListed: FixtureSource = { list: async () => ["ChatScreen"], read: async () => null };
    const cases = {
      absent: failing(await new FixtureRuntimeAdapter(memoryFixtureSource({})).snapshot("ChatScreen")),
      malformed: failing(await new FixtureRuntimeAdapter(memoryFixtureSource({
        ChatScreen: "{ not json"
      })).snapshot("ChatScreen")),
      throwing: failing(await new FixtureRuntimeAdapter(boom).snapshot("BrokenScreen")),
      nullListed: failing(await new FixtureRuntimeAdapter(nullListed).snapshot("ChatScreen"))
    };
    expect(cases.absent.kind).toBe("unknown-screen");
    expect(cases.malformed.kind).toBe("malformed-fixture");
    expect(cases.throwing.kind).toBe("unreadable");
    // 两种「读不到」来自不同生产者（抛异常 / 返回 null），必须落同一档且都不得降级成「不存在」。
    expect(cases.nullListed.kind).toBe("unreadable");
    expect(cases.nullListed.kind).toBe(cases.throwing.kind);
    expect(new Set([cases.absent.kind, cases.malformed.kind, cases.throwing.kind]).size).toBe(3);
  });

  it("在名单里却取不到文本判 unreadable，不得降级成 unknown-screen", async () => {
    const source = { list: async () => ["ChatScreen"], read: async () => null };
    expect(failing(await new FixtureRuntimeAdapter(source).snapshot("ChatScreen")).kind).toBe("unreadable");
  });

  it("零夹具时 snapshot 永远不给 ok：空名单不是「还原完成」", async () => {
    const adapter = new FixtureRuntimeAdapter(memoryFixtureSource({}));
    expect(await adapter.screens()).toEqual([]);
    expect((await adapter.snapshot("ChatScreen")).ok).toBe(false);
  });
});

describe("注入点", () => {
  it("未注入时大声失败而不是返回空端口", () => {
    expect(() => runtime()).toThrow(/not injected/);
  });

  it("注入 mock 端口后可读回同一个条目，卸载后回到未注入", () => {
    const mock = createMockRuntime();
    const restore = injectRuntime(mock);
    expect(runtime()).toBe(mock);
    restore();
    expect(() => runtime()).toThrow(/not injected/);
  });
});
