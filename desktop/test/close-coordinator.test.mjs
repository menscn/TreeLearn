import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { createCloseCoordinator } = require('../close-coordinator.cjs');

function setup({ timeoutMs = 200, serviceClose = () => {} } = {}) {
  const entry = path.resolve('desktop/app/index.html');
  const frame = { url: pathToFileURL(entry).href };
  const ipcMain = new EventEmitter();
  const win = new EventEmitter();
  win.webContents = { mainFrame: frame, isDestroyed: () => false, sent: [], send(name, value) { this.sent.push({ name, value }); } };
  win.isDestroyed = () => false;
  win.close = () => { const event = { prevented: false, preventDefault() { this.prevented = true; } }; win.emit('close', event); if (!event.prevented) win.emit('closed'); };
  let service = { close: serviceClose }, alerts = [];
  const coordinator = createCloseCoordinator({ win, ipcMain, entry, getService: () => service, clearService: () => { service = null; }, showError: (...args) => alerts.push(args), timeoutMs });
  const event = () => ({ sender: win.webContents, senderFrame: frame });
  return { win, ipcMain, event, alerts, coordinator, service: () => service };
}

test('close waits for the matching trusted renderer result and closes once', () => {
  let closes = 0;
  const s = setup({ serviceClose: () => { closes++; } });
  s.win.close(); s.win.close();
  assert.equal(closes, 0);
  assert.equal(s.win.webContents.sent.length, 1);
  const nonce = s.win.webContents.sent[0].value;
  s.ipcMain.emit('treelearn:close-result', { sender: {}, senderFrame: s.event().senderFrame }, nonce, true);
  s.ipcMain.emit('treelearn:close-result', s.event(), 'wrong-nonce', true);
  assert.equal(closes, 0);
  s.ipcMain.emit('treelearn:close-result', s.event(), nonce, true);
  assert.equal(closes, 1); assert.equal(s.service(), null);
});

test('failed flush, timeout and service failure keep the window open and cancel the freeze', async () => {
  const s = setup({ timeoutMs: 20, serviceClose: () => { throw new Error('private error'); } });
  s.win.close();
  let nonce = s.win.webContents.sent[0].value;
  s.ipcMain.emit('treelearn:close-result', s.event(), nonce, false);
  assert.equal(s.win.webContents.sent.at(-1).name, 'treelearn:close-cancelled');
  assert.equal(s.alerts.length, 1); assert.ok(s.service());
  s.win.close(); nonce = s.win.webContents.sent.at(-1).value;
  s.ipcMain.emit('treelearn:close-result', s.event(), nonce, true);
  assert.equal(s.alerts.length, 2); assert.ok(s.service());
  s.win.close(); const old = s.win.webContents.sent.at(-1).value;
  await new Promise(resolve => setTimeout(resolve, 50));
  assert.equal(s.alerts.length, 3); assert.equal(s.win.webContents.sent.at(-1).name, 'treelearn:close-cancelled');
  s.ipcMain.emit('treelearn:close-result', s.event(), old, true);
  assert.equal(s.alerts.length, 3); assert.ok(s.service());
  s.win.emit('closed');
});
