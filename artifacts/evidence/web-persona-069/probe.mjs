import fs from "node:fs";
import path from "node:path";
import { findBrowser, openBrowser, sleep } from "../../../.codex/scripts/dev/parity/parity_cdp.mjs";

const OUT = path.resolve("artifacts/evidence/web-persona-069");
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
  const ok = await cdp.eval(`(()=>{const el=document.querySelector(${JSON.stringify(sel)});if(el===null)return false;el.click();return true;})()`);
  if (ok !== true) throw new Error(`click missed: ${sel}`);
  await sleep(450);
}

async function metrics(label, expr) {
  const value = await cdp.eval(expr);
  readings[label] = value;
  return value;
}

try {
  await cdp.send("Page.enable");
  await cdp.send("Runtime.enable");
  await cdp.send("Emulation.setDeviceMetricsOverride", { width: 1440, height: 900, deviceScaleFactor: 1, mobile: false });
  await cdp.send("Page.navigate", { url: URL });
  await cdp.waitFor('[data-screen="home"]', 20000);
  await sleep(600);

  await click('[data-recent-topic="0"]');
  await cdp.waitFor('[data-screen="chat"]', 10000);
  await sleep(600);
  await metrics("chipDefault", `(()=>{const el=document.querySelector('[data-node="chat_persona_chip"]');return el===null?null:{label:el.querySelector('[data-persona-chip-label]').textContent,aria:el.getAttribute('aria-label'),height:Math.round(el.getBoundingClientRect().height)};})()`);
  await shot("persona-069-chip.png");

  await click('[data-node="chat_persona_chip"]');
  await cdp.waitFor("[data-persona-popover]", 10000);
  await sleep(400);
  await metrics("popover", `(()=>{
    const box=document.querySelector('[data-persona-popover]').getBoundingClientRect();
    const rows=[...document.querySelectorAll('[data-persona-row]')].map(r=>({id:r.dataset.personaRow,label:r.querySelector('[data-persona-row-label]').textContent,selected:r.getAttribute('aria-selected'),height:Math.round(r.getBoundingClientRect().height),check:r.querySelector('[data-persona-check]')!==null}));
    return {aria:document.querySelector('[data-persona-popover]').getAttribute('aria-label'),left:Math.round(box.left),right:Math.round(box.right),top:Math.round(box.top),bottom:Math.round(box.bottom),rows,introRow:document.querySelector('[data-persona-intro]').textContent.trim(),insideViewport:box.left>=0&&box.right<=window.innerWidth&&box.top>=0};
  })()`);
  await shot("persona-069-popover.png");

  await click('[data-persona-row="strict-mentor"]');
  await sleep(300);
  await metrics("switched", `(()=>{
    const fb=document.querySelector('[data-persona-feedback]');
    return {popoverClosed:document.querySelector('[data-persona-popover]')===null,chip:document.querySelector('[data-persona-chip-label]').textContent,aria:document.querySelector('[data-node="chat_persona_chip"]').getAttribute('aria-label'),feedback:fb===null?null:fb.textContent};
  })()`);
  await shot("persona-069-switched.png");

  await click('[data-node="chat_persona_chip"]');
  await cdp.waitFor("[data-persona-popover]", 10000);
  await sleep(300);
  await click("[data-persona-intro]");
  await cdp.waitFor('[data-screen="persona-intro"]', 10000);
  await sleep(600);
  await metrics("intro", `(()=>{
    const cards=[...document.querySelectorAll('[data-persona-card]')].map(c=>({id:c.dataset.personaCard,title:c.querySelector('.persona-card-title').textContent,tag:c.querySelector('.persona-card-tag').textContent}));
    const scroll=document.querySelector('[data-slot="persona-intro-scroll"]');
    return {hash:location.hash,title:document.querySelector('[data-node="persona_intro_title"]').textContent,subtitle:document.querySelector('.persona-intro-subtitle').textContent,cards,scrollHeight:scroll.scrollHeight,clientHeight:scroll.clientHeight};
  })()`);
  await shot("persona-069-intro.png");

  await cdp.eval(`(()=>{const s=document.querySelector('[data-slot="persona-intro-scroll"]');s.scrollTop=s.scrollHeight;return true;})()`);
  await sleep(500);
  await shot("persona-069-intro-end.png");

  await click('[data-action="persona-intro-back"]');
  await cdp.waitFor('[data-screen="chat"]', 10000);
  await sleep(500);
  await metrics("backToChat", `(()=>({hash:location.hash,chip:document.querySelector('[data-persona-chip-label]').textContent}))()`);

  await click('[data-nav-view="settings"]');
  await cdp.waitFor('[data-screen="settings"]', 10000);
  await sleep(500);
  await cdp.eval(`(()=>{const el=document.querySelector('[data-persona-intro-entry]');el.scrollIntoView({block:'center'});return true;})()`);
  await sleep(400);
  await metrics("settingsEntry", `(()=>{const el=document.querySelector('[data-persona-intro-entry]');const r=el.getBoundingClientRect();return {text:el.textContent.replace(/\\s+/g,' ').trim(),height:Math.round(r.height),aria:el.getAttribute('aria-label')};})()`);
  await shot("persona-069-settings-entry.png");

  await click("[data-persona-intro-entry]");
  await cdp.waitFor('[data-screen="persona-intro"]', 10000);
  await sleep(500);
  await shot("persona-069-settings-intro.png");

  await cdp.send("Emulation.setDeviceMetricsOverride", { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await sleep(400);
  await cdp.send("Page.navigate", { url: `${URL}#/chat/0` });
  await cdp.waitFor('[data-screen="chat"]', 20000);
  await sleep(700);
  await click('[data-node="chat_persona_chip"]');
  await cdp.waitFor("[data-persona-popover]", 10000);
  await sleep(400);
  await metrics("narrowPopover", `(()=>{
    const box=document.querySelector('[data-persona-popover]').getBoundingClientRect();
    return {viewport:window.innerWidth,left:Math.round(box.left),right:Math.round(box.right),width:Math.round(box.width),insideViewport:box.left>=0&&box.right<=window.innerWidth,overflowX:document.documentElement.scrollWidth>document.documentElement.clientWidth};
  })()`);
  await shot("persona-069-narrow-popover.png");

  fs.writeFileSync(path.join(OUT, "readings.json"), JSON.stringify(readings, null, 2));
  console.log(JSON.stringify(readings, null, 2));

  const p = readings.popover;
  const ok = p !== null
    && p.rows.length === 7
    && p.rows.every((r) => r.height >= 44)
    && p.rows[0].selected === "true"
    && p.rows[0].check === true
    && p.rows.slice(1).every((r) => r.selected === "false" && r.check === false)
    && p.insideViewport === true
    && readings.switched.popoverClosed === true
    && readings.switched.feedback !== null
    && readings.intro.cards.length === 7
    && readings.backToChat.chip === readings.switched.chip
    && readings.narrowPopover.insideViewport === true
    && readings.narrowPopover.overflowX === false;
  console.log(ok ? "PERSONA_WEB_OK" : "PERSONA_WEB_BAD");
  console.log(`EXITCODE=${ok ? 0 : 1}`);
  process.exitCode = ok ? 0 : 1;
} finally {
  await br.close();
}
