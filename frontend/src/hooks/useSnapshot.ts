import { useEffect, useState } from "react";
import { runtime } from "../platform/runtime";
import type { FixtureEnvelope, ScreenName } from "../platform/RuntimePort";

export type SnapshotStatus = "loading" | "ready" | "unknown-screen" | "malformed-fixture" | "unreadable";

export interface SnapshotState {
  status: SnapshotStatus;
  envelope: FixtureEnvelope | null;
  detail: string;
}

const LOADING: SnapshotState = { status: "loading", envelope: null, detail: "" };

/**
 * 读一屏的夹具快照。三类失败互不混同（规格 §4 的状态矩阵）：
 * 名单里没有这屏、夹具不合格式、读不到文本——各自给各自的文案键。
 */
export function useSnapshot(screen: ScreenName): SnapshotState {
  const [state, setState] = useState<SnapshotState>(LOADING);
  useEffect(() => {
    let alive = true;
    setState(LOADING);
    runtime()
      .snapshot(screen)
      .then((result) => {
        if (!alive) return;
        if (result.ok) {
          setState({ status: "ready", envelope: result.envelope, detail: "" });
          return;
        }
        setState({ status: result.kind, envelope: null, detail: result.detail });
      })
      .catch((exc: unknown) => {
        if (!alive) return;
        setState({ status: "unreadable", envelope: null, detail: String(exc) });
      });
    return () => {
      alive = false;
    };
  }, [screen]);
  return state;
}
