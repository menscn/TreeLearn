import fs from "node:fs";
import path from "node:path";
import { findBrowser, openBrowser, sleep } from "../../../.codex/scripts/dev/parity/parity_cdp.mjs";

const OUT = path.resolve("artifacts/evidence/web-forest-070");
const URL = process.env.WEB_URL ?? "http://127.0.0.1:5173/";
fs.mkdirSync(OUT, { recursive: true });

const browser = findBrowser();
const br = await openBrowser(browser);
const cdp = br.cdp;
const readings = { url: URL, shots: [] };

async function shot(name) {
  const { data } = await cdp.send("Page.captureScreenshot", { format: "png" });
  fs.writeFileSync(path.join(OUT, name), Buffer.from(data, "base64"));
  readings.shots.push(name);
}

async function click(sel) {
  const ok = await cdp.eval(`(()=>{const el=document.querySelector(${JSON.stringify(sel)});if(el===null)return false;el.dispatchEvent(new MouseEvent('click',{bubbles:true}));return true;})()`);
  if (ok !== true) throw new Error(`click missed: ${sel}`);
  await sleep(500);
}

async function metrics(label, expr) {
  const value = await cdp.eval(expr);
  readings[label] = value;
  return value;
}

/** 每棵树的命中圈实测像素：44px 是可点区域下限，必须真量而不是靠常量推。 */
const TREES_EXPR = `[...document.querySelectorAll('[data-forest-tree]')].map(g=>{
  const hit=g.querySelector('.fs-hit').getBoundingClientRect();
  const crown=g.getBoundingClientRect();
  return {index:Number(g.dataset.forestTree),label:g.getAttribute('aria-label'),selected:g.dataset.selected,
    hitW:Math.round(hit.width),hitH:Math.round(hit.height),crownW:Math.round(crown.width),crownH:Math.round(crown.height),
    cx:Math.round(hit.left+hit.width/2),cy:Math.round(hit.top+hit.height/2)};
})`;

const SHEET_EXPR = `(()=>{const s=document.querySelector('[data-forest-sheet]');if(s===null)return null;const tag=document.querySelector('[data-forest-tag]');const box=s.getBoundingClientRect();
  return {title:s.querySelector('[data-forest-sheet-title]').textContent,meta:s.querySelector('[data-forest-sheet-meta]').textContent,
    go:s.querySelector('[data-forest-go]').getAttribute('aria-label'),goSize:Math.round(s.querySelector('[data-forest-go]').getBoundingClientRect().height),
    left:Math.round(box.left),right:Math.round(box.right),bottom:Math.round(box.bottom),
    tag:tag===null?null:{text:tag.textContent,top:Math.round(tag.getBoundingClientRect().top),left:Math.round(tag.getBoundingClientRect().left),right:Math.round(tag.getBoundingClientRect().right)},
    insideViewport:box.left>=0&&box.right<=window.innerWidth&&box.bottom<=window.innerHeight};})()`;

try {
  await cdp.send("Page.enable");
  await cdp.send("Runtime.enable");
  await cdp.send("Emulation.setDeviceMetricsOverride", { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
  await cdp.send("Page.navigate", { url: URL });
  await cdp.waitFor('[data-screen="home"]', 20000);
  await sleep(700);

  await metrics("entry", `(()=>{const el=document.querySelector('[data-forest-entry]');const r=el.getBoundingClientRect();
    return {aria:el.getAttribute('aria-label'),title:el.querySelector('[data-forest-entry-title]').textContent,
      text:el.textContent.replace(/\\s+/g,' ').trim(),height:Math.round(r.height),width:Math.round(r.width),
      prev:el.previousElementSibling.className,next:el.nextElementSibling.className};})()`);
  await shot("forest-070-entry.png");

  await click("[data-forest-entry]");
  await cdp.waitFor('[data-screen="forest"]', 10000);
  await sleep(800);
  await metrics("desktop", `(()=>{
    const scene=document.querySelector('[data-forest-scene]');
    const stage=document.querySelector('.forest-stage').getBoundingClientRect();
    const ground=document.querySelector('.forest-scene').getBoundingClientRect();
    return {hash:location.hash,crumb:document.querySelector('[data-slot="breadcrumb"]').textContent,
      title:document.querySelector('[data-node="forest_title"]').textContent,
      subtitle:document.querySelector('[data-forest-subtitle]').textContent,
      hint:document.querySelector('[data-forest-hint]').textContent.trim(),
      compass:document.querySelector('[data-forest-compass]').getAttribute('aria-label'),
      sceneAria:scene.getAttribute('aria-label'),viewBox:scene.getAttribute('viewBox'),
      stage:{w:Math.round(stage.width),h:Math.round(stage.height)},ground:{w:Math.round(ground.width),h:Math.round(ground.height)},
      contours:document.querySelectorAll('.fs-contour').length,grass:document.querySelectorAll('.fs-grass').length,
      stones:document.querySelectorAll('.fs-stone').length,canopyLobes:document.querySelectorAll('.fs-canopy').length,
      trunks:document.querySelectorAll('.fs-trunk').length,trees:${TREES_EXPR},
      foot:document.querySelector('[data-forest-foot]').textContent,sheet:${SHEET_EXPR}};})()`);
  await shot("forest-070-desktop.png");

  await click('[data-forest-tree="0"]');
  await sleep(500);
  await metrics("selected", `(()=>({trees:${TREES_EXPR},sheet:${SHEET_EXPR},foot:document.querySelector('[data-forest-foot]').textContent}))()`);
  await shot("forest-070-selected.png");

  await click('[data-forest-tree="1"]');
  await sleep(500);
  await metrics("switched", `(()=>({hash:location.hash,trees:${TREES_EXPR},sheet:${SHEET_EXPR}}))()`);
  await shot("forest-070-selected-second.png");

  await click("[data-forest-go]");
  await cdp.waitFor('[data-screen="chat"]', 10000);
  await sleep(600);
  await metrics("goToTopic", `(()=>({hash:location.hash,screen:document.querySelector('[data-slot="screen"]').firstElementChild.dataset.screen,crumb:document.querySelector('[data-slot="breadcrumb"]').textContent}))()`);

  await click('[data-action="workspace-back"]');
  await cdp.waitFor('[data-screen="forest"]', 10000);
  await sleep(600);
  await metrics("backToForest", `(()=>({hash:location.hash,sheet:${SHEET_EXPR}}))()`);

  await cdp.send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await sleep(400);
  await cdp.send("Page.navigate", { url: `${URL}#/forest/0` });
  await cdp.waitFor('[data-screen="forest"]', 20000);
  await sleep(900);
  await metrics("narrow", `(()=>({viewport:window.innerWidth,ground:(()=>{const r=document.querySelector('.forest-scene').getBoundingClientRect();return {w:Math.round(r.width),h:Math.round(r.height)};})(),trees:${TREES_EXPR},foot:document.querySelector('[data-forest-foot]').textContent,overflowX:document.documentElement.scrollWidth>document.documentElement.clientWidth}))()`);
  await shot("forest-070-narrow.png");

  await click('[data-forest-tree="0"]');
  await sleep(500);
  await metrics("narrowSelected", `(()=>({trees:${TREES_EXPR},sheet:${SHEET_EXPR},overflowX:document.documentElement.scrollWidth>document.documentElement.clientWidth}))()`);
  await shot("forest-070-narrow-selected.png");

  fs.writeFileSync(path.join(OUT, "readings.json"), JSON.stringify(readings, null, 2));
  console.log(JSON.stringify(readings, null, 2));

  const d = readings.desktop;
  const s = readings.selected;
  const w = readings.switched;
  const narrow = readings.narrowSelected;
  const hitOk = (list) => list.every((tree) => tree.hitW >= 44 && tree.hitH >= 44);
  const inside = (box, viewport) => box !== null && box.insideViewport === true
    && (box.tag === null || (box.tag.left >= 0 && box.tag.right <= viewport));
  const ok = readings.entry.height >= 44
    && readings.entry.next.includes("side-heading")
    && d.trees.length === 2
    && d.sceneAria === "2 棵树"
    && d.contours === 8 && d.grass === 5 && d.stones === 3 && d.trunks === 2
    && d.foot === "2 棵树 · 3 段枝"
    && d.sheet === null
    && hitOk(d.trees)
    && s.sheet !== null && inside(s.sheet, 1440)
    && s.sheet.meta === "1 段枝 · 默认"
    && s.trees.filter((tree) => tree.selected === "true").length === 1
    && hitOk(s.trees)
    && w.sheet.title !== s.sheet.title
    && w.sheet.meta === "2 段枝 · 默认"
    && w.hash.startsWith("#/forest")
    && readings.goToTopic.screen === "chat"
    && readings.backToForest.hash.startsWith("#/forest")
    && hitOk(narrow.trees)
    && narrow.overflowX === false
    && inside(narrow.sheet, 390);
  console.log(ok ? "FOREST_WEB_OK" : "FOREST_WEB_BAD");
  console.log(`EXITCODE=${ok ? 0 : 1}`);
  process.exitCode = ok ? 0 : 1;
} finally {
  await br.close();
}
