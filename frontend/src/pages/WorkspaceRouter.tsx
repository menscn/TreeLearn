import { useWorkspaceData } from "../components/WorkspaceDataProvider";
import { useCopy } from "../hooks/useCopy";
import { useWorkspace } from "../hooks/useWorkspace";
import { ChatPage } from "./ChatPage";
import { ForestPage } from "./ForestPage";
import { HomePage } from "./HomePage";
import { LibraryPage } from "./LibraryPage";
import { PersonaIntroPage } from "./PersonaIntroPage";
import { SettingsPage } from "./SettingsPage";

/** 工作区视图分发；四个主视图与森林都已接入真实夹具，介绍页是静态页。 */
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
  if (state.view === "persona-intro") return <PersonaIntroPage />;
  if (state.view === "forest") return <ForestPage />;
  if (state.view === "chat") return <ChatPage />;
  return <section className="workspace-stage" data-view={state.view} aria-label={t("workspace_content")} />;
}
