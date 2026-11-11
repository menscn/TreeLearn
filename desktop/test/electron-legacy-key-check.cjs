const { app, safeStorage } = require('electron');
const { DatabaseSync } = require('node:sqlite');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createLearningService } = require('../learning-service.cjs');
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-legacy-key-'));
app.setPath('userData', path.join(root, 'profile'));
app.whenReady().then(async () => {
  let service, observer;
  try {
    const database = path.join(root, 'data', 'learning.sqlite');
    let usedCorrectKey = false;
    service = createLearningService({ database, secrets: safeStorage, transport: async (_url, options) => {
      usedCorrectKey = options.headers.authorization === 'Bearer synthetic-old-key';
      return new Response(JSON.stringify({ choices: [{ message: { content: 'synthetic answer' } }] }), { status: 200 });
    } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test' });
    observer = new DatabaseSync(database);
    observer.prepare('UPDATE configuration SET encrypted_key=? WHERE id=1').run(safeStorage.encryptString('synthetic-old-key'));
    observer.close(); observer = null;
    await service.sendMessage({ text: 'synthetic question', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' });
    if (!usedCorrectKey) throw new Error('legacy encrypted key did not decrypt');
    service.close(); service = null;
    console.log('ELECTRON_LEGACY_KEY_ASYNC_OK'); app.exit(0);
  } catch (error) {
    if (observer) observer.close(); if (service) service.close();
    console.error('ELECTRON_LEGACY_KEY_ASYNC_FAILED', error.message); app.exit(1);
  } finally { try { fs.rmSync(root, { recursive: true, force: true }); } catch {} }
}).catch(() => { console.error('ELECTRON_LEGACY_KEY_ASYNC_FAILED startup'); app.exit(1); });
