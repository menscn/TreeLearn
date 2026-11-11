const { isAllowedNavigation } = require('./navigation.cjs');

const METHODS = ['getState', 'saveSettings', 'deleteApiKey', 'setPersona', 'saveDraft', 'sendMessage', 'retryMessage', 'cancelMessage', 'createBranch', 'deleteTopic', 'removeAttachment'];
const ERRORS = new Set(['INVALID_SETTINGS', 'REQUEST_INVALID', 'KEY_MISSING', 'KEY_UNAVAILABLE', 'ENCRYPTION_UNAVAILABLE', 'REQUEST_TIMEOUT', 'REQUEST_FAILED', 'AUTH_FAILED', 'BALANCE_EXHAUSTED', 'RATE_LIMITED', 'NETWORK_ERROR', 'STORAGE_ERROR', 'BUSY', 'STALE_RETRY', 'REQUEST_CANCELLED', 'INCOMPLETE_RESPONSE', 'CONTEXT_CHANGED', 'CONTEXT_TOO_LARGE', 'ATTACHMENT_INVALID', 'ATTACHMENT_TOO_LARGE', 'ATTACHMENT_ENCODING', 'ATTACHMENT_CHANGED', 'ATTACHMENT_PENDING', 'ATTACHMENT_READ_FAILED']);

function registerDesktopIpc({ ipcMain, service, entry, getWindow, chooseAttachment, readAttachment }) {
  const revisions = new Map(), pending = new Map();
  const trusted = (event, win) => !!win && !!event.senderFrame && event.sender === win.webContents && event.senderFrame === win.webContents.mainFrame && isAllowedNavigation(event.senderFrame.url, entry);
  const errorResult = error => ({ ok: false, error: ERRORS.has(error?.message) ? error.message : 'STORAGE_ERROR' });
  ipcMain.handle('treelearn:selectAttachment', async (event, value) => {
    const win = getWindow();
    if (!trusted(event, win)) return { ok: false, error: 'REQUEST_INVALID' };
    let scope, revision;
    try {
      scope = service.attachmentScope(value);
      revision = (revisions.get(scope) || 0) + 1;
      revisions.set(scope, revision); pending.set(scope, revision);
      if (typeof chooseAttachment !== 'function' || typeof readAttachment !== 'function') throw new Error('ATTACHMENT_READ_FAILED');
      const selected = await chooseAttachment(win);
      if (revisions.get(scope) !== revision || !trusted(event, getWindow()) || win.webContents.isDestroyed()) throw new Error('ATTACHMENT_CHANGED');
      if (selected.canceled) return { ok: true, value: { cancelled: true } };
      if (!Array.isArray(selected.filePaths) || selected.filePaths.length !== 1) throw new Error('ATTACHMENT_INVALID');
      const attachment = await readAttachment(selected.filePaths[0]);
      if (revisions.get(scope) !== revision || !trusted(event, getWindow()) || win.webContents.isDestroyed()) throw new Error('ATTACHMENT_CHANGED');
      const saved = await service.saveAttachment({ topicId: value?.topicId, branchId: value?.branchId, name: attachment.name, content: attachment.content });
      return { ok: true, value: { cancelled: false, attachment: saved } };
    } catch (error) { return errorResult(error); }
    finally { if (scope && pending.get(scope) === revision) pending.delete(scope); }
  });
  for (const method of METHODS) {
    ipcMain.handle(`treelearn:${method}`, async (event, value) => {
      const win = getWindow();
      if (!trusted(event, win)) {
        return { ok: false, error: 'REQUEST_INVALID' };
      }
      try {
        if (method === 'removeAttachment') {
          const scope = service.attachmentScope(value);
          revisions.set(scope, (revisions.get(scope) || 0) + 1); pending.delete(scope);
        }
        if (method === 'sendMessage' && pending.has(service.attachmentScope(value))) throw new Error('ATTACHMENT_PENDING');
        const progress = data => {
          if (getWindow() === win && !win.webContents.isDestroyed() && isAllowedNavigation(win.webContents.mainFrame.url, entry)) {
            win.webContents.send('treelearn:progress', data);
          }
        };
        return { ok: true, value: await service[method](value, progress) };
      }
      catch (error) { return errorResult(error); }
    });
  }
}
module.exports = { registerDesktopIpc };
