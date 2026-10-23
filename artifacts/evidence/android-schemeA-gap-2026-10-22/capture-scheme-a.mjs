import fs from "node:fs";
import path from "node:path";
import { findBrowser, openBrowser, sleep } from "../../../.codex/scripts/dev/parity/parity_cdp.mjs";

const OUT = path.resolve("artifacts/evidence/android-schemeA-gap-2026-10-22/img");
fs.mkdirSync(OUT, { recursive: true });
const html = path.resolve("Docs/04-ux-design/2026-10-21-玻璃质感重设计提案/A-晨雾玻璃.html");
const url = encodeURI("file:///" + html.split(path.sep).join("/"));
const browser = findBrowser();
const br = await openBrowser(browser);
const cdp = br.cdp;
const names = ["schemeA-home", "schemeA-chat", "schemeA-branch", "schemeA-library"];

try {
  await cdp.send("Page.enable");
  await cdp.send("Runtime.enable");
  await cdp.send("Emulation.setDeviceMetricsOverride", {
    width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false
  });
  await cdp.send("Page.navigate", { url });
  await cdp.waitFor(".phone-screen", 20000);
  await sleep(800);

  for (let i = 0; i < names.length; i += 1) {
    const rect = await cdp.eval(`(()=>{
      const el=[...document.querySelectorAll('.phone-screen')][${i}];
      el.scrollIntoView({block:'center'});
      const r=el.getBoundingClientRect();
      return {x:r.x+window.scrollX,y:r.y+window.scrollY,w:r.width,h:r.height};
    })()`);
    await sleep(350);
    const { data } = await cdp.send("Page.captureScreenshot", {
      format: "png",
      captureBeyondViewport: true,
      clip: { x: rect.x, y: rect.y, width: rect.w, height: rect.h, scale: 2 }
    });
    fs.writeFileSync(path.join(OUT, `${names[i]}.png`), Buffer.from(data, "base64"));
    console.log(`${names[i]} ${rect.w}x${rect.h}`);
  }
} finally {
  await br.close();
  // Edge child handles otherwise keep this one-shot capture alive after the work is done.
  process.exit(0);
}
