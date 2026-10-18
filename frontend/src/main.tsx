import { createRoot } from "react-dom/client";
import { AppShell } from "./layouts/AppShell";
import { ScreenRouter } from "./pages/ScreenRouter";
import { FixtureRuntimeAdapter } from "./platform/FixtureRuntimeAdapter";
import { bundledFixtureSource } from "./platform/bundledFixtures";
import { injectRuntime } from "./platform/runtime";
import { copyText } from "./copy";
import "./styles/tokens.css";
import "./styles/app.css";

const host = document.getElementById("root");
if (host === null) {
  throw new Error("index.html is missing #root");
}

document.title = copyText("app_name");
injectRuntime(new FixtureRuntimeAdapter(bundledFixtureSource()));
createRoot(host).render(
  <AppShell>
    <ScreenRouter />
  </AppShell>
);
