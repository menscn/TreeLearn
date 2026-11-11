const { contextBridge, ipcRenderer } = require('electron');
let closeHandler = null;
ipcRenderer.on('treelearn:close-request', async (_event, nonce) => {
  let accepted = false;
  try { accepted = closeHandler !== null && (await closeHandler()) === true; } catch {}
  ipcRenderer.send('treelearn:close-result', nonce, accepted);
});

async function call(name, value) {
  const result = await ipcRenderer.invoke(`treelearn:${name}`, value);
  if (!result || result.ok !== true) throw new Error(result?.error || 'REQUEST_FAILED');
  return result.value;
}

contextBridge.exposeInMainWorld('treelearnDesktop', Object.freeze({
  getState: () => call('getState'),
  saveSettings: value => call('saveSettings', value),
  deleteApiKey: () => call('deleteApiKey'),
  setPersona: value => call('setPersona', value),
  saveDraft: value => call('saveDraft', value),
  sendMessage: value => call('sendMessage', value),
  retryMessage: value => call('retryMessage', value),
  cancelMessage: value => call('cancelMessage', value),
  createBranch: value => call('createBranch', value),
  selectAttachment: value => call('selectAttachment', value),
  removeAttachment: value => call('removeAttachment', value),
  deleteTopic: value => call('deleteTopic', value),
  onBeforeClose: callback => {
    if (typeof callback !== 'function') throw new TypeError('callback required');
    closeHandler = callback;
    return () => { if (closeHandler === callback) closeHandler = null; };
  },
  onCloseCancelled: callback => {
    if (typeof callback !== 'function') throw new TypeError('callback required');
    const listener = () => callback();
    ipcRenderer.on('treelearn:close-cancelled', listener);
    return () => ipcRenderer.removeListener('treelearn:close-cancelled', listener);
  },
  onProgress: callback => {
    if (typeof callback !== 'function') throw new TypeError('callback required');
    const listener = (_event, value) => callback(value);
    ipcRenderer.on('treelearn:progress', listener);
    return () => ipcRenderer.removeListener('treelearn:progress', listener);
  }
}));
