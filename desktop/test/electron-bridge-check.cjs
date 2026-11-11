const { app, BrowserWindow, ipcMain, safeStorage } = require('electron');
const { mkdtempSync, mkdirSync, writeFileSync, readFileSync, rmSync } = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { createLearningService } = require('../learning-service.cjs');
const { registerDesktopIpc } = require('../desktop-ipc.cjs');
const { DatabaseSync } = require('node:sqlite');

const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-electron-check-'));
app.setPath('userData', path.join(root, 'profile'));
app.disableHardwareAcceleration();

app.whenReady().then(async () => {
  let service, win;
  try {
    if (!(await safeStorage.isAsyncEncryptionAvailable())) throw new Error('encryption unavailable');
    const entry = path.join(root, 'index.html');
    writeFileSync(entry, '<!doctype html><meta charset="utf-8"><title>test</title>', 'utf8');
    const database = path.join(root, 'data', 'learning.sqlite');
    mkdirSync(path.dirname(database));
    const promptResource = JSON.parse(readFileSync(path.join(__dirname, '..', 'app', 'learning-prompt.json'), 'utf8'));
    let systemPrompt;
    service = createLearningService({ database, secrets: safeStorage, tutorPrompt: promptResource.prompt, personaPrompts: promptResource.personas, transport: async (_url, options) => {
      systemPrompt = JSON.parse(options.body).messages[0].content;
      return new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"合成"}}]}\n\n'));
        setTimeout(() => { controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"回答"}}]}\n\ndata: [DONE]\n\n')); controller.close(); }, 40);
      }
    }), { status: 200, headers: { 'content-type': 'text/event-stream' } }); } });
    win = new BrowserWindow({ show: false, webPreferences: { preload: path.join(__dirname, '..', 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true } });
    registerDesktopIpc({ ipcMain, service, entry, getWindow: () => win });
    await win.loadFile(entry);
    const result = await win.webContents.executeJavaScript(`(async () => {
      const api = window.treelearnDesktop;
      const progress=[]; const unsubscribe=api.onProgress(value=>progress.push(value));
      await api.saveSettings({baseUrl:'https://example.test', model:'synthetic-model', apiKey:'synthetic-electron-token'});
      await api.setPersona({persona:'strict-mentor'});
      await api.saveDraft({text:'合成问题'});
      const sent = await api.sendMessage({text:'合成问题',requestId:'e45e52b8-3c0b-487e-8851-a8b7c97ad602'});
      unsubscribe(); return {state:await api.getState(), sent, progress, noNode:typeof require==='undefined'};
    })()`);
    if (!result.noNode || !result.state.settings.hasApiKey || JSON.stringify(result).includes('synthetic-electron-token')) throw new Error('bridge exposed secret or Node');
    if (result.state.settings.defaultPersona !== 'strict-mentor' || result.state.topics[0].persona !== 'strict-mentor' || result.state.topics[0].messages.some(m => m.persona !== 'strict-mentor')) throw new Error('persona state incorrect');
    if (systemPrompt !== `${promptResource.prompt}\n${promptResource.personas['strict-mentor']}`) throw new Error('persona prompt incorrect');
    if (result.state.topics[0].messages.map(m => m.content).join('|') !== '合成问题|合成回答') throw new Error('bridge result incorrect');
    if (result.progress.length < 2 || result.progress.at(-1).content !== '合成回答' || result.progress.some(p => p.requestId !== 'e45e52b8-3c0b-487e-8851-a8b7c97ad602')) throw new Error('progress events incorrect');
    const observer = new DatabaseSync(database);
    observer.prepare('UPDATE configuration SET default_persona=? WHERE id=1').run('future-style');
    observer.prepare('UPDATE topics SET persona=? WHERE id=?').run('future-style', result.sent.topicId);
    observer.prepare('UPDATE messages SET persona=? WHERE topic_id=?').run('future-style', result.sent.topicId);
    observer.close();
    const unknown = await win.webContents.executeJavaScript(`(async () => {
      const api = window.treelearnDesktop;
      const before = await api.getState();
      await api.sendMessage({topicId:before.topics[0].id,text:'未知风格后续问题',requestId:'f45e52b8-3c0b-487e-8851-a8b7c97ad602'});
      return before;
    })()`);
    if (unknown.settings.defaultPersona !== 'future-style' || unknown.topics[0].persona !== 'future-style' || unknown.topics[0].messages.some(m => m.persona !== 'future-style')) throw new Error('unknown persona not exposed to renderer');
    if (systemPrompt !== promptResource.prompt) throw new Error('unknown persona did not use default prompt');
    service.close(); service = null;
    if (readFileSync(database).includes(Buffer.from('synthetic-electron-token'))) throw new Error('secret in database');
    service = createLearningService({ database, secrets: safeStorage, personaPrompts: promptResource.personas });
    const reopened = await service.getState();
    if (reopened.topics[0].id !== result.sent.topicId || !reopened.settings.hasApiKey || reopened.settings.defaultPersona !== 'future-style' || reopened.topics[0].persona !== 'future-style' || reopened.topics[0].messages[0].persona !== 'future-style') throw new Error('restart lost state');
    service.close(); service = null;
    win.destroy(); win = null;
    console.log('ELECTRON_BRIDGE_SAFE_STORAGE_OK');
    app.exit(0);
  } catch (error) {
    if (service) service.close();
    if (win) win.destroy();
    console.error('ELECTRON_BRIDGE_TEST_FAILED', error.message);
    app.exit(1);
  } finally {
    try { rmSync(root, { recursive: true, force: true }); } catch {}
  }
}).catch(() => { console.error('ELECTRON_BRIDGE_TEST_FAILED startup'); app.exit(1); });
