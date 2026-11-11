import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import path from 'node:path';
const require = createRequire(import.meta.url);
const { registerDesktopIpc } = require('../desktop-ipc.cjs');

test('desktop bridge accepts only the app main frame and sanitizes failures', async () => {
  const handlers = new Map();
  const entry = path.resolve('desktop/app/index.html');
  const frame = { url: `${pathToFileURL(entry).href}#/library` };
  const webContents = { mainFrame: frame };
  let calls = 0;
  const service = { getState() { calls++; return { settings: { hasApiKey: true } }; }, saveSettings() { throw new Error('private synthetic provider detail'); } };
  registerDesktopIpc({ ipcMain: { handle: (name, fn) => handlers.set(name, fn) }, service, entry, getWindow: () => ({ webContents }) });
  const invoke = handlers.get('treelearn:getState');
  assert.deepEqual(await invoke({ sender: webContents, senderFrame: frame }), { ok: true, value: { settings: { hasApiKey: true } } });
  for (const event of [
    { sender: {}, senderFrame: frame },
    { sender: webContents, senderFrame: { url: frame.url } },
    { sender: webContents, senderFrame: { url: pathToFileURL(path.resolve('desktop/app/other.html')).href } }
  ]) assert.deepEqual(await invoke(event), { ok: false, error: 'REQUEST_INVALID' });
  assert.equal(calls, 1);
  assert.deepEqual(await handlers.get('treelearn:saveSettings')({ sender: webContents, senderFrame: frame }, {}), { ok: false, error: 'STORAGE_ERROR' });
});
