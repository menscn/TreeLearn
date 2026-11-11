const { app, BrowserWindow, ipcMain, safeStorage } = require('electron');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { createLearningService } = require('../learning-service.cjs');
const { registerDesktopIpc } = require('../desktop-ipc.cjs');
const { createCloseCoordinator } = require('../close-coordinator.cjs');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-close-electron-'));
app.setPath('userData', path.join(root, 'profile'));
app.disableHardwareAcceleration();
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const check = (condition, message) => { if (!condition) throw new Error(message); };

app.whenReady().then(async () => {
  let service, win;
  try {
    const entry = path.join(root, 'index.html');
    const database = path.join(root, 'db', 'learning.sqlite');
    fs.writeFileSync(entry, '<!doctype html><meta charset="utf-8"><title>test</title>', 'utf8');
    service = createLearningService({ database, secrets: safeStorage });
    win = new BrowserWindow({ show: false, webPreferences: { preload: path.join(__dirname, '..', 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true } });
    let alerts = 0;
    registerDesktopIpc({ ipcMain, service, entry, getWindow: () => win });
    createCloseCoordinator({ win, ipcMain, entry, getService: () => service, clearService: () => { service = null; }, showError: () => { alerts++; }, timeoutMs: 400 });
    await win.loadFile(entry);
    await win.webContents.executeJavaScript(`(() => {
      window.cancelCount=0;
      window.treelearnDesktop.onCloseCancelled(() => window.cancelCount++);
      window.treelearnDesktop.onBeforeClose(() => new Promise(resolve => { window.finishOld=resolve; }));
    })()`);
    win.close();
    await delay(600);
    check(!win.isDestroyed() && alerts === 1, 'timeout must retain window');
    check(await win.webContents.executeJavaScript('window.cancelCount') === 1, 'timeout must unfreeze renderer');
    await win.webContents.executeJavaScript(`(() => { window.treelearnDesktop.onBeforeClose(() => new Promise(resolve => { window.finishNew=resolve; })); return true; })()`);
    win.close();
    await delay(40);
    await win.webContents.executeJavaScript('window.finishOld(true)');
    await delay(40);
    check(!win.isDestroyed(), 'stale nonce must not close window');
    await win.webContents.executeJavaScript(`(async () => { await window.treelearnDesktop.saveDraft({text:'合成的关窗草稿'}); window.finishNew(true); })()`);
    await new Promise((resolve, reject) => { if (win.isDestroyed()) return resolve(); const timer = setTimeout(() => reject(new Error('window did not close')), 2000); win.once('closed', () => { clearTimeout(timer); resolve(); }); });
    win = null;
    check(service === null, 'service must close after flush');
    service = createLearningService({ database, secrets: safeStorage });
    check((await service.getState()).drafts.home === '合成的关窗草稿', 'reopened draft missing');
    service.close(); service = null;
    console.log('ELECTRON_CLOSE_HANDSHAKE_OK');
    app.exit(0);
  } catch (error) {
    if (service) { try { service.close(); } catch {} }
    if (win && !win.isDestroyed()) win.destroy();
    console.error('ELECTRON_CLOSE_HANDSHAKE_FAILED', error.message);
    app.exit(1);
  } finally { try { fs.rmSync(root, { recursive: true, force: true }); } catch {} }
}).catch(() => { console.error('ELECTRON_CLOSE_HANDSHAKE_FAILED startup'); app.exit(1); });
