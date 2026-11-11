const crypto = require('node:crypto');
const { isAllowedNavigation } = require('./navigation.cjs');

/** Ask the trusted renderer to flush its pending draft writes before the service closes. */
function createCloseCoordinator({ win, ipcMain, entry, getService, clearService, showError, timeoutMs = 8000 }) {
  let allowed = false;
  let pending = null;

  function notifyCancelled() {
    if (!win.isDestroyed() && !win.webContents.isDestroyed()) win.webContents.send('treelearn:close-cancelled');
  }
  function refuse() {
    if (pending) clearTimeout(pending.timer);
    pending = null;
    notifyCancelled();
    showError('保存未完成', '草稿暂时无法确认保存。请检查磁盘空间和权限，稍后重试关闭。');
  }
  function onResult(event, nonce, accepted) {
    if (!pending || nonce !== pending.nonce || event.sender !== win.webContents ||
        event.senderFrame !== win.webContents.mainFrame || !isAllowedNavigation(event.senderFrame?.url, entry)) return;
    clearTimeout(pending.timer);
    pending = null;
    if (accepted !== true) { refuse(); return; }
    try {
      const service = getService();
      if (service) service.close();
      clearService();
    } catch { refuse(); return; }
    allowed = true;
    win.close();
  }
  ipcMain.on('treelearn:close-result', onResult);
  win.on('close', event => {
    if (allowed) return;
    event.preventDefault();
    if (pending) return;
    const nonce = crypto.randomUUID();
    pending = { nonce, timer: setTimeout(refuse, timeoutMs) };
    try { win.webContents.send('treelearn:close-request', nonce); } catch { refuse(); }
  });
  win.on('closed', () => {
    if (pending) clearTimeout(pending.timer);
    pending = null;
    ipcMain.removeListener('treelearn:close-result', onResult);
  });
  return { isPending: () => pending !== null };
}
module.exports = { createCloseCoordinator };
