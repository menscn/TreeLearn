import { payloadOf, type FixturePayload } from "../platform/fixtureNodes";
import type { ScreenName } from "../platform/RuntimePort";
import { useSnapshot, type SnapshotStatus } from "./useSnapshot";

export interface FixtureScreen {
  status: SnapshotStatus;
  payload: FixturePayload | null;
}

/** 页面读一屏基线的唯一入口：三类读取失败与「形状不合」都收敛成 payload=null，由页面给同一条文案。 */
export function useFixtureScreen(screen: ScreenName): FixtureScreen {
  const state = useSnapshot(screen);
  return { status: state.status, payload: state.status === "ready" ? payloadOf(state.envelope) : null };
}
