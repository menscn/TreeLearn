/**
 * 运行时边界的唯一正文（规格 §4）。
 *
 * 这里定义的是「快照夹具信封」，不是逐屏数据形状：payload 的确切形状属于阶段一的采集产物，
 * 端口刻意保持 unknown，以免前端先长出一份与采集器各写一遍的第二份契约。
 */
export type ScreenName = string;

export type SnapshotFailureKind = "unknown-screen" | "malformed-fixture" | "unreadable";

export interface FixtureEnvelope {
  schema_version: number;
  screen: ScreenName;
  source_commit: string;
  captured_at_ms: number;
  device: string;
  payload: unknown;
}

export interface SnapshotSuccess {
  ok: true;
  envelope: FixtureEnvelope;
}

export interface SnapshotFailure {
  ok: false;
  kind: SnapshotFailureKind;
  detail: string;
}

export type SnapshotResult = SnapshotSuccess | SnapshotFailure;

export interface RuntimePort {
  screens(): Promise<ScreenName[]>;
  snapshot(screen: ScreenName): Promise<SnapshotResult>;
}

const SOURCE_COMMIT = /^[0-9a-f]{40}$/;

function failure(kind: SnapshotFailureKind, detail: string): SnapshotFailure {
  return { ok: false, kind, detail };
}

/**
 * 解析并校验一份夹具文本。任何不合格式都大声失败并点名到字段——
 * 夹具是判据的输入，静默降级会让「还原不对」读成「没有基线」。
 */
export function parseEnvelope(text: string, screen: ScreenName): SnapshotResult {
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch (exc) {
    return failure("malformed-fixture", `${screen}: 不是合法 JSON（${String(exc)}）`);
  }
  if (typeof raw !== "object" || raw === null || Array.isArray(raw)) {
    return failure("malformed-fixture", `${screen}: 顶层必须是对象`);
  }
  const o = raw as Record<string, unknown>;
  for (const field of ["schema_version", "screen", "source_commit", "captured_at_ms", "device", "payload"]) {
    if (!(field in o)) return failure("malformed-fixture", `${screen}: 缺少必需字段 ${field}`);
  }
  if (!Number.isInteger(o.schema_version) || (o.schema_version as number) < 1) {
    return failure("malformed-fixture", `${screen}: schema_version 必须是正整数`);
  }
  if (o.screen !== screen) {
    return failure("malformed-fixture", `${screen}: screen 字段与文件名不一致（读到 ${String(o.screen)}）`);
  }
  if (typeof o.source_commit !== "string" || !SOURCE_COMMIT.test(o.source_commit)) {
    return failure("malformed-fixture", `${screen}: source_commit 必须是 40 位小写十六进制提交号`);
  }
  // 时间点只接受 epoch 毫秒：无 offset 的本地串会随时区漂移（.codex/rules/contracts.md 时间字段契约）。
  if (!Number.isSafeInteger(o.captured_at_ms) || (o.captured_at_ms as number) <= 0) {
    return failure("malformed-fixture", `${screen}: captured_at_ms 必须是正的 epoch 毫秒整数`);
  }
  if (typeof o.device !== "string" || o.device.length === 0) {
    return failure("malformed-fixture", `${screen}: device 必须是非空字符串（采集环境是覆盖面的一个维度）`);
  }
  return {
    ok: true,
    envelope: {
      schema_version: o.schema_version as number,
      screen: o.screen as ScreenName,
      source_commit: o.source_commit,
      captured_at_ms: o.captured_at_ms as number,
      device: o.device,
      payload: o.payload
    }
  };
}
