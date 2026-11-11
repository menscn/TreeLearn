// Synthetic data only. This test bridge does not contact the desktop service.
const { contextBridge } = require('electron');
const content = Array.from({ length: 240 }, (_, index) => `合成资料 ${index + 1}: ${'可阅读内容'.repeat(10)}`).join('\n') + '\n合成资料结束标记';
const attachment = { id: 'synthetic-attachment', name: `${'很长的资料文件名'.repeat(17)}.md`, size: content.length, preview: content };
let hasAttachment = true;
let calls = 0;
contextBridge.exposeInMainWorld('treelearnDesktop', {
  getState: async () => ({ settings: { baseUrl: 'https://example.test', model: 'synthetic', hasApiKey: true, defaultPersona: 'default' }, topics: [], drafts: { home: '合成问题' }, attachmentDrafts: hasAttachment ? { home: attachment } : {} }),
  saveDraft: async () => {}, saveSettings: async () => {}, deleteApiKey: async () => {}, deleteTopic: async () => {}, setPersona: async () => {},
  sendMessage: async () => { throw new Error('REQUEST_FAILED'); }, retryMessage: async () => { throw new Error('REQUEST_FAILED'); }, cancelMessage: async () => ({ cancelled: true }), createBranch: async () => ({ branchId: 'synthetic' }),
  selectAttachment: async () => { calls += 1; if (calls % 2 === 1) throw new Error('ATTACHMENT_ENCODING'); hasAttachment = true; return { cancelled: false, attachment }; },
  removeAttachment: async () => { hasAttachment = false; }, onProgress: () => () => {}, onBeforeClose: () => () => {}, onCloseCancelled: () => () => {}
});
