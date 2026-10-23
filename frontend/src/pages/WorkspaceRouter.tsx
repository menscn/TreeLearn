import { useWorkspaceData } from "../components/WorkspaceDataProvider";
import { useCopy } from "../hooks/useCopy";
import { useWorkspace } from "../hooks/useWorkspace";
import { ChatPage } from "./ChatPage";
import { HomePage } from "./HomePage";
import { LibraryPage } from "./LibraryPage";
import { SettingsPage } from "./SettingsPage";

/** 工作区视图分发；四个主视图都已接入真实夹具。 */
export function WorkspaceRouter() {
  const t = useCopy();
  const state = useWorkspace();
  const data = useWorkspaceData();
  if (data.status === "loading") {
    return <p className="workspace-status" role="status">{t("workspace_loading")}</p>;
  }
  if (data.status === "error") {
    return <p className="workspace-status" role="alert">{t("workspace_error")}</p>;
  }
  if (state.view === "home") return <HomePage />;
  if (state.view === "library") return <LibraryPage />;
  if (state.view === "settings") return <SettingsPage />;
  if (state.view === "chat") return <ChatPage />;
  return <section className="workspace-stage" data-view={state.view} aria-label={t("workspace_content")} />;
}
