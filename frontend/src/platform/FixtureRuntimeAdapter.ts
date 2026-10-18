import type { RuntimePort, ScreenName, SnapshotResult } from "./RuntimePort";
import { parseEnvelope } from "./RuntimePort";

/** 夹具来源端口：把「怎么拿到文本」与「文本意味着什么」分开，换宿主只动这里与注入点。 */
export interface FixtureSource {
  list(): Promise<ScreenName[]>;
  read(screen: ScreenName): Promise<string | null>;
}

export function memoryFixtureSource(entries: Record<string, string>): FixtureSource {
  return {
    async list() {
      return Object.keys(entries).sort();
    },
    async read(screen) {
      return Object.prototype.hasOwnProperty.call(entries, screen) ? (entries[screen] ?? null) : null;
    }
  };
}

/**
 * 夹具适配器（阶段一到六的数据面）。
 * `screens()` 返回空数组只说明「还没有夹具」，不说明任何屏已还原——覆盖结论的权威在台账，不在这里。
 */
export class FixtureRuntimeAdapter implements RuntimePort {
  constructor(private readonly source: FixtureSource) {}

  screens(): Promise<ScreenName[]> {
    return this.source.list();
  }

  async snapshot(screen: ScreenName): Promise<SnapshotResult> {
    const names = await this.source.list();
    if (!names.includes(screen)) {
      return { ok: false, kind: "unknown-screen", detail: `夹具里没有条目 ${screen}` };  // i18n-ignore: 诊断 detail，只进日志与测试断言，不渲染到 UI（UI 状态文案走 src/copy）
    }
    let text: string | null;
    try {
      text = await this.source.read(screen);
    } catch (exc) {
      return { ok: false, kind: "unreadable", detail: `${screen}: 读取失败（${String(exc)}）` };  // i18n-ignore: 诊断 detail，只进日志与测试断言，不渲染到 UI（UI 状态文案走 src/copy）
    }
    if (text === null) {
      return { ok: false, kind: "unreadable", detail: `${screen}: 在名单里却取不到文本` };  // i18n-ignore: 诊断 detail，只进日志与测试断言，不渲染到 UI（UI 状态文案走 src/copy）
    }
    return parseEnvelope(text, screen);
  }
}

/** 手工最小样例，仅供组件单测；任何 1:1 判定都不得引用它（规格 §4 的矩阵行约束）。 */
export function createMockRuntime(screen: ScreenName = "mock"): RuntimePort {
  const envelope = JSON.stringify({
    schema_version: 1,
    screen,
    source_commit: "0".repeat(40),
    captured_at_ms: 1_700_000_000_000,
    device: "mock",
    payload: { title: "" }
  });
  return new FixtureRuntimeAdapter(memoryFixtureSource({ [screen]: envelope }));
}
