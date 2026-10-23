import fs from "node:fs";
import path from "node:path";
import { findBrowser, openBrowser, sleep } from "../../../.codex/scripts/dev/parity/parity_cdp.mjs";

const OUT = path.resolve("artifacts/evidence/web-chat-column-2026-10-22");
fs.mkdirSync(OUT, { recursive: true });

const browser = findBrowser();
const br = await openBrowser(browser);
const cdp = br.cdp;

try {
  await cdp.send("Page.enable");
  await cdp.send("Runtime.enable");
  await cdp.send("Emulation.setDeviceMetricsOverride", {
    width: 1440, height: 900, deviceScaleFactor: 1, mobile: false
  });
  await cdp.send("Page.navigate", { url: "http://127.0.0.1:5174/" });
  await cdp.waitFor('[data-screen="home"]', 20000);
  await sleep(500);

  await cdp.eval(`(()=>{document.querySelector('[data-topic="0"]').click(); return true;})()`);
  await cdp.waitFor('[data-screen="chat"]', 10000);
  await sleep(600);

  const measured = await cdp.eval(`(()=>{
    const rect = (sel) => {
      const el = document.querySelector(sel);
      if (el === null) return null;
      const r = el.getBoundingClientRect();
      return { left: Math.round(r.left), right: Math.round(r.right), width: Math.round(r.width) };
    };
    return {
      reading: rect(".chat-reading"),
      composer: rect(".chat-dock .composer"),
      textLeft: {
        reading: Math.round(document.querySelector(".chat-title").getBoundingClientRect().left),
        composer: Math.round(document.querySelector(".composer textarea").getBoundingClientRect().left)
      },
      viewport: window.innerWidth,
      overflowX: document.documentElement.scrollWidth > document.documentElement.clientWidth
    };
  })()`);

  const aligned = measured.reading !== null && measured.composer !== null
    && measured.reading.left === measured.composer.left
    && measured.reading.right === measured.composer.right
    && measured.reading.width === measured.composer.width
    && measured.overflowX === false;

  const { data } = await cdp.send("Page.captureScreenshot", { format: "png" });
  fs.writeFileSync(path.join(OUT, "desktop-chat.png"), Buffer.from(data, "base64"));
  fs.writeFileSync(path.join(OUT, "readings.json"), JSON.stringify({
    url: "http://127.0.0.1:5174/",
    viewport: "1440x900",
    measured,
    readingEqualsComposer: aligned
  }, null, 2));

  console.log(JSON.stringify(measured));
  console.log(aligned ? "ALIGNED" : "MISALIGNED");
  console.log(aligned ? "EXITCODE=0" : "EXITCODE=1");
  process.exitCode = aligned ? 0 : 1;
} finally {
  await br.close();
}
