/* The orchestrator starts these two phases sequentially using one synthetic profile. */
const { app, safeStorage } = require('electron');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { createLearningService } = require('../learning-service.cjs');

const phase = process.argv[2];
const root = path.resolve(process.argv[3] || '');
if (!['write', 'reopen'].includes(phase) || !/^treelearn-cold-e2e-[A-Za-z0-9]+$/.test(path.basename(root))
    || fs.realpathSync(path.dirname(root)) !== fs.realpathSync(os.tmpdir())) {
  console.error('COLD_RESTART_PHASE_FAIL invalid isolated profile');
  app.exit(1);
} else {
  app.setName('TreelearnColdRestartTest');
  app.setPath('userData', path.join(root, 'profile'));
  app.whenReady().then(async () => {
    let service;
    try {
      if (!await safeStorage.isAsyncEncryptionAvailable()) throw new Error('OS encryption unavailable');
      const transport = async (_url, options) => {
        if (options.headers.authorization !== 'Bearer synthetic-cold-restart-key') throw new Error('incorrect key');
        return new Response(JSON.stringify({ choices: [{ message: { content: '合成冷启动回答' }, finish_reason: 'stop' }] }), {
          status: 200, headers: { 'content-type': 'application/json' }
        });
      };
      service = createLearningService({ database: path.join(root, 'data', 'learning.sqlite'), secrets: safeStorage, transport });
      if (phase === 'write') {
        await service.saveSettings({ baseUrl: 'https://example.test', model: 'synthetic-model', apiKey: 'synthetic-cold-restart-key' });
        const sent = await service.sendMessage({ text: '冷重启前问题', requestId: crypto.randomUUID() });
        await service.saveDraft({ topicId: sent.topicId, text: '冷重启草稿' });
      } else {
        const state = await service.getState();
        const topic = state.topics[0];
        if (!state.settings.hasApiKey || !topic || state.drafts[topic.id] !== '冷重启草稿'
            || !topic.messages.some(message => message.content === '合成冷启动回答')) {
          throw new Error('persisted history or draft unavailable');
        }
        await service.sendMessage({ topicId: topic.id, text: '冷重启后追问', requestId: crypto.randomUUID() });
        const reopened = await service.getState();
        if (!reopened.topics[0].messages.some(message => message.content === '冷重启后追问')) throw new Error('follow-up unavailable');
      }
      service.close(); service = null;
      console.log(`COLD_RESTART_${phase.toUpperCase()}_OK`);
      app.quit();
    } catch (error) {
      if (service) { try { service.close(); } catch {} }
      console.error('COLD_RESTART_PHASE_FAIL', error.message);
      app.exit(1);
    }
  }).catch(() => { console.error('COLD_RESTART_PHASE_FAIL startup'); app.exit(1); });
}
