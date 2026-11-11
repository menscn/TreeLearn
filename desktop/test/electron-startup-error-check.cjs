const { app, dialog } = require('electron');
const { DatabaseSync } = require('node:sqlite');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-startup-error-'));
const userData = path.join(root, 'profile');
const directory = path.join(userData, 'desktop-learning-v1');
const database = path.join(directory, 'learning.sqlite');
const prompt = path.join(__dirname, '..', 'app', 'learning-prompt.json');
app.setName('知芽');
app.setPath('userData', userData);
fs.mkdirSync(directory, { recursive: true });
const foreign = new DatabaseSync(database);
foreign.exec("CREATE TABLE messages(status TEXT); INSERT INTO messages VALUES('pending'); PRAGMA user_version=1");
foreign.close();
const hash = () => crypto.createHash('sha256').update(fs.readFileSync(database)).digest('hex');
const originalHash = hash();

dialog.showErrorBox = (title, message) => {
  try {
    if (!fs.existsSync(prompt)) throw new Error('prompt fixture missing');
    if (hash() !== originalHash) throw new Error('unknown database changed');
    const reader = new DatabaseSync(database, { readOnly: true });
    try { if (reader.prepare('SELECT status FROM messages').get().status !== 'pending') throw new Error('unknown data changed'); }
    finally { reader.close(); }
    if (title !== '知芽启动失败' || !message.includes('应用版本') || !message.includes('不会被重置')) throw new Error('startup guidance incomplete');
    if (message.includes(root) || message.includes('SQLITE') || message.includes('STORAGE_ERROR')) throw new Error('startup error exposes internals');
    console.log('ELECTRON_STARTUP_UNKNOWN_DB_SAFE_OK');
    app.exit(0);
  } catch (error) {
    console.error('ELECTRON_STARTUP_UNKNOWN_DB_SAFE_FAILED', error.message);
    app.exit(1);
  }
};
setTimeout(() => { console.error('ELECTRON_STARTUP_UNKNOWN_DB_SAFE_FAILED timeout'); app.exit(1); }, 15000).unref();
require('../main.js');
