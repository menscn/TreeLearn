import { memoryFixtureSource, type FixtureSource } from "./FixtureRuntimeAdapter";

/**
 * 打包进产物的夹具来源（阶段一到七的数据面入口之一）。
 *
 * `src/platform/fixtures/*-android.json` 是 `artifacts/fixtures/parity/` 的逐字节副本，
 * 由 `node frontend/scripts/sync_fixtures.mjs` 刷入、`--check` 判漂移。之所以要副本而不是
 * 运行时去仓库里读：H5 要能离线双击打开（阶段 2 的出口判据之一就是它），而 `fetch` 在
 * `file://` 下拿不到 src 之外的路径；依赖方向守门也只允许夹具经由本端口进来。
 */
const rawModules = import.meta.glob("./fixtures/*.json", {
  eager: true,
  import: "default",
  query: "?raw"
}) as Record<string, unknown>;

/** 条目名 = 文件名去掉 `-android.json`；夹具内部的 `screen` 字段与它不一致时 parseEnvelope 会大声失败。 */
export function bundledEntries(): Record<string, string> {
  const entries: Record<string, string> = {};
  for (const [rel, value] of Object.entries(rawModules)) {
    if (typeof value !== "string") {
      throw new Error(`夹具 ${rel} 没有按 ?raw 读成文本（收到 ${typeof value}）`);  // i18n-ignore: 诊断 detail，只在构建/加载期崩溃到控制台，不渲染到 UI（UI 状态文案走 src/copy）
    }
    const file = rel.slice(rel.lastIndexOf("/") + 1);
    entries[file.replace(/-android\.json$/, "")] = value;
  }
  return entries;
}

export function bundledFixtureNames(): string[] {
  return Object.keys(bundledEntries()).sort();
}

export function bundledFixtureSource(): FixtureSource {
  return memoryFixtureSource(bundledEntries());
}
