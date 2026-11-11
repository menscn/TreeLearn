import { createRoot } from "react-dom/client";
import { WorkspaceDataProvider } from "./components/WorkspaceDataProvider";
import { AppShell } from "./layouts/AppShell";
import { WorkspaceRouter } from "./pages/WorkspaceRouter";
import { DesktopWorkspace } from "./pages/DesktopWorkspace";
import { FixtureRuntimeAdapter } from "./platform/FixtureRuntimeAdapter";
import { bundledFixtureSource } from "./platform/bundledFixtures";
import { injectRuntime } from "./platform/runtime";
import { applyWorkspaceHash } from "./store/workspace";
import { copyText } from "./copy";
import "./styles/tokens.css";
import "./styles/fonts.css";
import "./styles/base.css";
import "./styles/snapshot.css";
import "./styles/workspace.css";
import "./styles/pages.css";
import "./styles/forest.css";
import "./styles/glass.css";
import "./styles/desktop.css";

const host = document.getElementById("root");
if (host === null) {
  throw new Error("index.html is missing #root");
}

document.title = copyText("app_name");
if (window.treelearnDesktop) {
  createRoot(host).render(<DesktopWorkspace api={window.treelearnDesktop} />);
} else {
  injectRuntime(new FixtureRuntimeAdapter(bundledFixtureSource()));
  window.addEventListener("popstate", () => applyWorkspaceHash(window.location.hash));
  window.addEventListener("hashchange", () => applyWorkspaceHash(window.location.hash));
  createRoot(host).render(
    <WorkspaceDataProvider>
      <AppShell>
        <WorkspaceRouter />
      </AppShell>
    </WorkspaceDataProvider>
  );
}
