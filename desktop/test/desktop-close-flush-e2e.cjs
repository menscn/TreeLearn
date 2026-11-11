/* Real renderer, preload, IPC, SQLite and close coordinator; synthetic profile only. */
const { app, BrowserWindow, ipcMain, safeStorage } = require('electron');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createLearningService } = require('../learning-service.cjs');
const { registerDesktopIpc } = require('../desktop-ipc.cjs');
const { createCloseCoordinator } = require('../close-coordinator.cjs');
const { buildWindowOptions } = require('../window-options.cjs');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-close-e2e-'));
const database = path.join(root, 'data', 'learning.sqlite');
const entry = path.resolve(__dirname, '../../frontend/dist-offline/index.html');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
app.setPath('userData', path.join(root, 'profile'));
app.disableHardwareAcceleration();
app.on('window-all-closed', () => {});

let win, service, releaseWrite, markWrite;
const writeGate = new Promise(resolve => { releaseWrite = resolve; });
const observed = new Promise(resolve => { markWrite = resolve; });
function assert(value, message) { if (!value) throw new Error(message); }

app.whenReady().then(async () => {
  try {
    assert(fs.existsSync(entry), 'frontend build missing');
    service = createLearningService({ database, secrets: safeStorage });
    const bridge = new Proxy({}, { get: (_target, key) => {
      if (key === 'saveDraft') return async (...args) => {
        markWrite();
        await writeGate;
        return service.saveDraft(...args);
      };
      return (...args) => service[key](...args);
    } });
    const options = buildWindowOptions();
    options.webPreferences.preload = path.resolve(__dirname, '../preload.cjs');
    win = new BrowserWindow(options);
    registerDesktopIpc({ ipcMain, service: bridge, entry, getWindow: () => win });
    createCloseCoordinator({ win, ipcMain, entry, getService: () => service,
      clearService: () => { service = null; }, showError: () => { throw new Error('close rejected'); } });
    await win.loadFile(entry);
    const deadline = Date.now() + 10000;
    while (!await win.webContents.executeJavaScript(`!!document.querySelector('.desktop-composer textarea')`) && Date.now() < deadline) await delay(50);
    assert(await win.webContents.executeJavaScript(`!!document.querySelector('.desktop-composer textarea')`), 'composer unavailable');
    await win.webContents.executeJavaScript(`(() => {
      const e = document.querySelector('.desktop-composer textarea');
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(e, '关窗瞬间的合成草稿');
      e.dispatchEvent(new Event('input', { bubbles: true }));
    })()`);
    await Promise.race([observed, delay(5000).then(() => { throw new Error('draft write not observed'); })]);
    const closing = new Promise(resolve => win.once('closed', resolve));
    win.close();
    await delay(300);
    assert(!win.isDestroyed() && service !== null, 'window closed before queued draft write finished');
    console.log('CLOSE_WAITED_FOR_QUEUED_DRAFT');
    releaseWrite();
    await Promise.race([closing, delay(10000).then(() => { throw new Error('close timed out'); })]);
    assert(service === null, 'service not closed by coordinator');
    win = null;
    service = createLearningService({ database, secrets: safeStorage });
    assert((await service.getState()).drafts.home === '关窗瞬间的合成草稿', 'draft missing after reopen');
    service.close(); service = null;
    console.log('DESKTOP_CLOSE_FLUSH_OK');
    app.exit(0);
  } catch (error) {
    releaseWrite();
    if (win && !win.isDestroyed()) win.destroy();
    if (service) { try { service.close(); } catch {} }
    console.error('DESKTOP_CLOSE_FLUSH_FAIL', error.message);
    app.exit(1);
  }
}).catch(() => { console.error('DESKTOP_CLOSE_FLUSH_FAIL startup'); app.exit(1); });
