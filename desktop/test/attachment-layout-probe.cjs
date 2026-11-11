/* Independent visual probe. Synthetic preload, no production IPC, no model/network. */
const { app, BrowserWindow } = require('electron');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { buildWindowOptions } = require('../window-options.cjs');

const workspace = path.resolve(__dirname, '../..');
const entry = path.join(workspace, 'frontend/dist-offline/index.html');
const preload = path.join(__dirname, 'attachment-layout-probe-preload.cjs');
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-layout-probe-'));
const evidence = fs.mkdtempSync(path.join(workspace, 'artifacts/evidence/attachment-layout-probe-'));
const inputs = ['frontend/dist-offline/index.html', 'frontend/src/styles/desktop.css', 'frontend/src/components/DesktopAttachment.tsx', 'frontend/src/pages/DesktopWorkspace.tsx', 'desktop/window-options.cjs', 'desktop/test/attachment-layout-probe-preload.cjs', 'desktop/test/attachment-layout-probe.cjs'];
const digest = file => crypto.createHash('sha256').update(fs.readFileSync(path.join(workspace, file))).digest('hex');
const hashes = () => Object.fromEntries(inputs.map(file => [file, digest(file)]));
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const requireCondition = (condition, label) => { if (!condition) throw new Error(label); };
app.setPath('userData', path.join(profile, 'profile'));
app.disableHardwareAcceleration();
app.on('window-all-closed', () => {});

async function waitFor(win, expression, label) {
  for (let attempt = 0; attempt < 150; attempt++) {
    if (await win.webContents.executeJavaScript(expression)) return;
    await delay(50);
  }
  throw new Error(`TIMEOUT_${label}`);
}
async function frame(win) {
  await win.webContents.executeJavaScript('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))');
  await delay(150);
}
async function read(win) {
  return win.webContents.executeJavaScript(`(() => {
    const rect = selector => { const element = document.querySelector(selector); if (!element) return null; const r = element.getBoundingClientRect(); return {top:r.top,bottom:r.bottom,left:r.left,right:r.right,width:r.width,height:r.height}; };
    const section=document.querySelector('.desktop-attachment'), preview=document.querySelector('[data-attachment-preview]'), details=document.querySelector('.desktop-attachment details');
    let tailRect=null;
    if (preview?.firstChild?.nodeType===Node.TEXT_NODE) {
      const node=preview.firstChild, position=node.textContent.lastIndexOf('合成资料结束标记');
      if (position>=0) { const range=document.createRange(); range.setStart(node,position); range.setEnd(node,position+'合成资料结束标记'.length); const r=range.getBoundingClientRect(); tailRect={top:r.top,bottom:r.bottom,left:r.left,right:r.right}; }
    }
    return { viewport:{width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth},
      composer:rect('.desktop-composer'), section:rect('.desktop-attachment'), textarea:rect('.desktop-composer textarea'), send:rect('.desktop-composer button[aria-label="发送问题"]'),
      select:rect('[data-action="desktop-select-attachment"]'), remove:rect('[data-action="desktop-remove-attachment"]'), summary:rect('.desktop-attachment summary'),
      alert:rect('[role="alert"]'), preview:rect('[data-attachment-preview]'), sectionScroll:section&&{top:section.scrollTop,height:section.scrollHeight,client:section.clientHeight,overflow:getComputedStyle(section).overflowY},
      previewScroll:preview&&{top:preview.scrollTop,height:preview.scrollHeight,client:preview.clientHeight,tail:preview.textContent.endsWith('合成资料结束标记')}, tailRect, open:!!details?.open,
      focused:document.activeElement?.getAttribute('data-action')||document.activeElement?.tagName };
  })()`);
}
function fullyVisible(rect, viewport) { return rect && rect.top >= -1 && rect.left >= -1 && rect.bottom <= viewport.height + 1 && rect.right <= viewport.width + 1; }
async function record(win, label, manifest, assertion) {
  await frame(win);
  const reading = await read(win);
  reading.bounds = win.getBounds();
  reading.zoom = win.webContents.getZoomFactor();
  fs.writeFileSync(path.join(evidence, `${label}.json`), JSON.stringify(reading, null, 2));
  fs.writeFileSync(path.join(evidence, `${label}.png`), (await win.webContents.capturePage()).toPNG());
  manifest.push({ label, reading });
  assertion(reading);
}
async function exercise(win, width, height, zoom, manifest) {
  win.setSize(width, height);
  win.webContents.setZoomFactor(zoom);
  await frame(win);
  await win.webContents.executeJavaScript(`document.querySelector('[data-action="desktop-select-attachment"]').click()`);
  await waitFor(win, `!!document.querySelector('[role="alert"]')`, 'ERROR_NOTICE');
  await win.webContents.executeJavaScript(`document.querySelector('.desktop-attachment details').open=true`);
  await waitFor(win, `!!document.querySelector('.desktop-attachment details[open] [data-attachment-preview]')`, 'PREVIEW');
  await win.webContents.executeJavaScript(`(() => { const p=document.querySelector('[data-attachment-preview]'); p.scrollTop=p.scrollHeight; })()`);
  await record(win, `${width}x${height}-zoom${zoom}-open`, manifest, reading => {
    requireCondition(reading.open && reading.previewScroll?.tail, 'PREVIEW_TEXT_MISSING');
    requireCondition(reading.previewScroll.top + reading.previewScroll.client >= reading.previewScroll.height - 2, 'PREVIEW_TAIL_UNREACHABLE');
    requireCondition(reading.viewport.scrollWidth <= reading.viewport.width, 'HORIZONTAL_OVERFLOW');
    requireCondition(fullyVisible(reading.textarea, reading.viewport), 'TEXTAREA_NOT_VISIBLE');
    requireCondition(fullyVisible(reading.send, reading.viewport), 'SEND_NOT_VISIBLE');
    requireCondition(reading.alert !== null, 'EXTRA_STATUS_NOT_VISIBLE');
  });
  // Keyboard focus should scroll each attachment control into its own scroll area when needed.
  for (const [name, selector] of [['select','[data-action="desktop-select-attachment"]'], ['remove','[data-action="desktop-remove-attachment"]'], ['summary','.desktop-attachment summary']]) {
    await win.webContents.executeJavaScript(`document.querySelector(${JSON.stringify(selector)}).focus()`);
    await record(win, `${width}x${height}-zoom${zoom}-focus-${name}`, manifest, reading => {
      requireCondition(fullyVisible(reading[name], reading.viewport), `FOCUS_${name.toUpperCase()}_OUTSIDE_VIEWPORT`);
      requireCondition(reading[name].top >= reading.section.top - 1 && reading[name].bottom <= reading.section.bottom + 1, `FOCUS_${name.toUpperCase()}_CLIPPED`);
    });
  }
  // Verify the last actual glyph, not just scroll offsets, after both nested areas scroll.
  await win.webContents.executeJavaScript(`(() => {const section=document.querySelector('.desktop-attachment'),p=document.querySelector('[data-attachment-preview]');section.scrollTop=section.scrollHeight;p.scrollTop=p.scrollHeight;})()`);
  await record(win, `${width}x${height}-zoom${zoom}-tail-visible`, manifest, reading => {
    requireCondition(fullyVisible(reading.tailRect, reading.viewport), 'PREVIEW_TAIL_NOT_IN_VIEWPORT');
    requireCondition(reading.tailRect.top >= reading.section.top - 1 && reading.tailRect.bottom <= reading.section.bottom + 1, 'PREVIEW_TAIL_CLIPPED_BY_ATTACHMENT');
    requireCondition(reading.tailRect.top >= reading.preview.top - 1 && reading.tailRect.bottom <= reading.preview.bottom + 1, 'PREVIEW_TAIL_CLIPPED_BY_PREVIEW');
    requireCondition(fullyVisible(reading.textarea, reading.viewport) && fullyVisible(reading.send, reading.viewport), 'TAIL_CONTROLS_NOT_VISIBLE');
  });
  await win.webContents.executeJavaScript(`document.querySelector('.desktop-attachment details').open=false`);
  await record(win, `${width}x${height}-zoom${zoom}-closed`, manifest, reading => {
    requireCondition(!reading.open && fullyVisible(reading.textarea, reading.viewport) && fullyVisible(reading.send, reading.viewport), 'COLLAPSED_CONTROLS_NOT_VISIBLE');
  });
  // Real Tab input, after focusing a known control, advances to the next control.
  await win.webContents.executeJavaScript(`document.querySelector('[data-action="desktop-select-attachment"]').focus()`);
  win.webContents.sendInputEvent({ type: 'keyDown', keyCode: 'Tab' });
  win.webContents.sendInputEvent({ type: 'keyUp', keyCode: 'Tab' });
  await delay(100);
  requireCondition((await read(win)).focused === 'desktop-remove-attachment', 'KEYBOARD_TAB_DID_NOT_REACH_REMOVE');
  console.log(`LAYOUT_CASE_OK size=${width}x${height} zoom=${zoom}`);
}

app.whenReady().then(async () => {
  let win; const manifest=[]; const before=hashes();
  fs.writeFileSync(path.join(evidence, 'hashes-before.json'), JSON.stringify(before, null, 2));
  try {
    const options=buildWindowOptions(); options.webPreferences.preload=preload;
    win=new BrowserWindow(options); win.showInactive();
    await win.loadFile(entry); await waitFor(win, `!!document.querySelector('[data-screen="home"] [data-action="desktop-select-attachment"]')`, 'HOME');
    await exercise(win, 900, 620, 1, manifest);
    // Restore an attachment after exercising the no-attachment state.
    await win.webContents.executeJavaScript(`document.querySelector('[data-action="desktop-remove-attachment"]').click()`);
    await waitFor(win, `!document.querySelector('[data-action="desktop-remove-attachment"]')`, 'REMOVE');
    await record(win, 'no-attachment', manifest, reading => requireCondition(fullyVisible(reading.textarea,reading.viewport) && fullyVisible(reading.send,reading.viewport), 'EMPTY_CONTROLS_NOT_VISIBLE'));
    await win.webContents.executeJavaScript(`document.querySelector('[data-action="desktop-select-attachment"]').click()`);
    await waitFor(win, `!!document.querySelector('[data-action="desktop-remove-attachment"]')`, 'RESELECT');
    // The second call succeeds; next one is arranged to show the same extra alert again.
    await exercise(win, 1180, 820, 1.25, manifest);
    const after=hashes(); fs.writeFileSync(path.join(evidence, 'hashes-after.json'), JSON.stringify(after,null,2));
    requireCondition(JSON.stringify(before)===JSON.stringify(after), 'SOURCE_CHANGED');
    fs.writeFileSync(path.join(evidence, 'result.json'), JSON.stringify({exitCode:0, cases:manifest.length, sourceHashesStable:true, profile, networkUsed:false},null,2));
    console.log('ATTACHMENT_LAYOUT_PROBE_OK'); console.log(`EVIDENCE_DIR ${evidence}`);
    win.destroy(); app.exit(0);
  } catch (error) {
    fs.writeFileSync(path.join(evidence, 'failure.json'), JSON.stringify({error:error.message, cases:manifest.length, profile},null,2));
    if (win && !win.isDestroyed()) { try { fs.writeFileSync(path.join(evidence, 'failure.png'), (await win.webContents.capturePage()).toPNG()); } catch {} win.destroy(); }
    console.error('ATTACHMENT_LAYOUT_PROBE_FAIL',error.message); console.error(`EVIDENCE_DIR ${evidence}`); app.exit(1);
  }
}).catch(() => { console.error('ATTACHMENT_LAYOUT_PROBE_FAIL startup'); app.exit(1); });
