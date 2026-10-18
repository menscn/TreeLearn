import { createRoot } from "react-dom/client";
import { AppShell } from "./layouts/AppShell";
import "./styles/tokens.css";
import "./styles/app.css";

const host = document.getElementById("root");
if (host === null) {
  throw new Error("index.html is missing #root");
}
createRoot(host).render(<AppShell />);
