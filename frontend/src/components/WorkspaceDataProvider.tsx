import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { payloadOf } from "../platform/fixtureNodes";
import { runtime } from "../platform/runtime";
import {
  WORKSPACE_SCREENS,
  workspaceDataFrom,
  type PayloadMap,
  type ScreenKey,
  type WorkspaceData,
  type WorkspaceStatus
} from "../store/workspaceData";

const WorkspaceDataContext = createContext<WorkspaceData | null>(null);

/**
 * 一次读取工作区需要的全部夹具屏，再交给纯函数拼视图模型。
 * 单屏读取失败只让状态降为 partial，不把整页读成空白。
 */
export function WorkspaceDataProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<WorkspaceData>(() => workspaceDataFrom({}, "loading"));

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      let payloads: PayloadMap;
      try {
        const port = runtime();
        const entries = await Promise.all(
          WORKSPACE_SCREENS.map(async (screen) => {
            try {
              const snapshot = await port.snapshot(screen);
              return [screen, snapshot.ok ? payloadOf(snapshot.envelope) : null] as const;
            } catch {
              return [screen, null] as const;
            }
          })
        );
        payloads = {};
        for (const [screen, payload] of entries) payloads[screen as ScreenKey] = payload;
      } catch {
        if (!cancelled) setData(workspaceDataFrom({}, "error"));
        return;
      }
      if (cancelled) return;
      const available = WORKSPACE_SCREENS.filter((screen) => payloads[screen] !== null).length;
      const status: WorkspaceStatus = available === 0
        ? "error"
        : available === WORKSPACE_SCREENS.length
          ? "ready"
          : "partial";
      setData(workspaceDataFrom(payloads, status));
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return <WorkspaceDataContext.Provider value={data}>{children}</WorkspaceDataContext.Provider>;
}

export function useWorkspaceData(): WorkspaceData {
  const data = useContext(WorkspaceDataContext);
  if (data === null) {
    throw new Error("useWorkspaceData must be used inside WorkspaceDataProvider");
  }
  return data;
}
