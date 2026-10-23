import fs from "node:fs";
import path from "node:path";
import { findBrowser, openBrowser, sleep } from "../../../.codex/scripts/dev/parity/parity_cdp.mjs";

const OUT = path.resolve("artifacts/evidence/android-schemeA-gap-2026-10-22");
const html = path.join(OUT, "index.html");
const url = encodeURI("file:///" + html.split(path.sep).join("/"));
const browser = findBrowser();
const br = await openBrowser(browser);
const cdp = br.cdp;
const readings = [];

try {
  await cdp.send("Page.enable");
  await cdp.send("Runtime.enable");
  await cdp.send("Emulation.setDeviceMetricsOverride", {
    width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false
  });
  await cdp.send("Page.navigate", { url });
  await cdp.waitFor(".shot img", 15000);
  await sleep(800);

  const images = await cdp.eval(`[...document.images].map(i=>({src:i.getAttribute('src'),w:i.naturalWidth,h:i.naturalHeight}))`);
  readings.push({ name: "截图全部加载", pass: images.length >= 6 && images.every((i) => i.w > 0), images });

  const before = await cdp.eval(`(()=>{const d=document.getElementById('demo');const s=getComputedStyle(d);const c=getComputedStyle(document.querySelector('.demo-card'));return {glow:d.dataset.glow,tint:d.dataset.tint,radius:d.dataset.radius,background:s.backgroundImage,cardRadius:c.borderRadius};})()`);
  await cdp.eval(`document.querySelector('[data-step="glow"]').click()`);
  await cdp.eval(`document.querySelector('[data-step="tint"]').click()`);
  await cdp.eval(`document.querySelector('[data-step="radius"]').click()`);
  await sleep(500);
  const after = await cdp.eval(`(()=>{const d=document.getElementById('demo');const s=getComputedStyle(d);const c=getComputedStyle(document.querySelector('.demo-card'));const t=getComputedStyle(document.querySelector('.demo-hero h4'));const band=getComputedStyle(document.querySelector('.demo-band'));return {glow:d.dataset.glow,tint:d.dataset.tint,radius:d.dataset.radius,type:d.dataset.type,background:s.backgroundImage,cardRadius:c.borderRadius,titleFont:t.fontFamily,band:band.backgroundColor};})()`);
  readings.push({
    name: "ROI 三步开关",
    pass: after.glow === "on" && after.tint === "on" && after.radius === "on" && after.type === "on"
      && after.cardRadius === "28px" && after.band !== "rgba(0, 0, 0, 0)" && before.cardRadius === "17px",
    before, after
  });
  await cdp.eval(`document.getElementById('reset').click()`);
  await sleep(300);
  const reset = await cdp.eval(`(()=>{const d=document.getElementById('demo');return {glow:d.dataset.glow,tint:d.dataset.tint,radius:d.dataset.radius,type:d.dataset.type};})()`);
  readings.push({ name: "重置回 A 基线", pass: reset.glow === "off" && reset.tint === "off" && reset.radius === "off" && reset.type === "off", reset });

  const height = await cdp.eval(`document.documentElement.scrollHeight`);
  await cdp.eval(`window.scrollTo(0,0)`);
  await sleep(300);
  const { data } = await cdp.send("Page.captureScreenshot", {
    format: "png", captureBeyondViewport: true,
    clip: { x: 0, y: 0, width: 1440, height, scale: 0.55 }
  });
  fs.writeFileSync(path.join(OUT, "img", "preview.png"), Buffer.from(data, "base64"));
  readings.push({ name: "整页预览", pass: true, height });

  const tops = await cdp.eval(`[...document.querySelectorAll('section')].map(s=>Math.round(s.offsetTop))`);
  for (let i = 0; i < tops.length; i += 1) {
    await cdp.eval(`window.scrollTo(0,${Math.max(0, tops[i] - 16)})`);
    await sleep(250);
    const snap = await cdp.send("Page.captureScreenshot", { format: "png" });
    fs.writeFileSync(path.join(OUT, "img", `snap-${i + 1}.png`), Buffer.from(snap.data, "base64"));
  }

  const consoleErrors = await cdp.eval(`window.__errors || []`).catch(() => []);
  fs.writeFileSync(path.join(OUT, "readings.json"), JSON.stringify({
    generatedAt: new Date().toISOString(), url, readings, consoleErrors
  }, null, 2));
  const failed = readings.filter((r) => !r.pass);
  console.log(`PAGE height=${height} readings=${readings.length} failed=${failed.length}`);
  if (failed.length) console.log(`FAILED: ${failed.map((f) => f.name).join(" | ")}`);
} finally {
  await br.close();
  process.exit(0);
}
