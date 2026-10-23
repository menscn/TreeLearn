import { useSyncExternalStore } from "react";
import { subscribeWorkspace, workspaceSnapshot, type WorkspaceState } from "../store/workspace";

export function useWorkspace(): WorkspaceState {
  return useSyncExternalStore(subscribeWorkspace, workspaceSnapshot, workspaceSnapshot);
}
