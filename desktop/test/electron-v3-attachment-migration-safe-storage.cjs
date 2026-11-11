/* Real OS encryption plus synthetic identified V3 branch data migrated by production. */
const { app, safeStorage } = require('electron');
const { DatabaseSync } = require('node:sqlite');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { createLearningService } = require('../learning-service.cjs');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-v3-safe-migration-'));
const database = path.join(root, 'learning.sqlite');
const evidence = path.resolve(__dirname, '../../artifacts/evidence', `desktop-v3-safe-migration-${path.basename(root)}`);
const key = 'synthetic-v3-migration-credential';
const uid = number => `${number.toString(16).padStart(8,'0')}-0000-4000-8000-000000000000`;
const digest = value => crypto.createHash('sha256').update(value,'utf8').digest('hex');
const inputs = ['desktop/learning-service.cjs','desktop/branch-context.cjs','desktop/attachment-data.cjs','desktop/test/electron-v3-attachment-migration-safe-storage.cjs'];
const hashes = () => Object.fromEntries(inputs.map(name => [name,crypto.createHash('sha256').update(fs.readFileSync(path.resolve(__dirname,'../..',name))).digest('hex')]));
const assert=(condition,message)=>{if(!condition)throw new Error(message);};
app.setPath('userData',path.join(root,'profile'));app.disableHardwareAcceleration();

app.whenReady().then(async()=>{
  let service;
  try {
    fs.mkdirSync(evidence,{recursive:true});const before=hashes();fs.writeFileSync(path.join(evidence,'hashes-before.json'),JSON.stringify(before,null,2));
    assert(process.platform==='win32' && await safeStorage.isAsyncEncryptionAvailable(),'Windows OS encryption unavailable');
    const encrypted=await safeStorage.encryptStringAsync(key);assert(Buffer.isBuffer(encrypted)&&!encrypted.includes(Buffer.from(key)),'plaintext ciphertext');
    const snapshot=JSON.stringify([{id:uid(41),role:'user',digest:digest('合成旧问题')},{id:uid(43),role:'assistant',digest:digest('合成旧回答')}]);
    const snapshotHash=digest(snapshot), db=new DatabaseSync(database);
    try {
      db.exec(`PRAGMA foreign_keys=ON; BEGIN;
        CREATE TABLE configuration (id INTEGER PRIMARY KEY CHECK(id=1), base_url TEXT NOT NULL, model TEXT NOT NULL, encrypted_key BLOB, default_persona TEXT NOT NULL DEFAULT 'default');
        CREATE TABLE topics (id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, persona TEXT NOT NULL DEFAULT 'default');
        CREATE TABLE messages (id TEXT PRIMARY KEY, topic_id TEXT NOT NULL REFERENCES topics(id), role TEXT NOT NULL CHECK(role IN ('user','assistant')), content TEXT NOT NULL, created_at INTEGER NOT NULL, status TEXT NOT NULL, request_id TEXT, draft_scope TEXT, persona TEXT NOT NULL DEFAULT 'default', branch_id TEXT REFERENCES branches(id));
        CREATE UNIQUE INDEX message_request ON messages(request_id) WHERE role='user';
        CREATE TABLE drafts (scope TEXT PRIMARY KEY, text TEXT NOT NULL);
        CREATE TABLE branches (id TEXT PRIMARY KEY, topic_id TEXT NOT NULL REFERENCES topics(id), parent_id TEXT REFERENCES branches(id), source_message_id TEXT, created_at INTEGER NOT NULL, creation_request TEXT UNIQUE, snapshot_json TEXT NOT NULL, snapshot_digest TEXT NOT NULL);
        PRAGMA application_id=1414287921; PRAGMA user_version=3; COMMIT;`);
      db.prepare('INSERT INTO configuration VALUES(1,?,?,?,?)').run('https://example.test','synthetic-model',encrypted,'strict-mentor');
      db.prepare('INSERT INTO topics VALUES(?,?,?,?,?)').run(uid(40),'合成旧主题',100,200,'late-night');
      db.prepare('INSERT INTO branches VALUES(?,?,NULL,NULL,?,NULL,?,?)').run(uid(40),uid(40),100,'[]',digest('[]'));
      db.prepare('INSERT INTO messages VALUES(?,?,?,?,?,?,?,?,?,?)').run(uid(41),uid(40),'user','合成旧问题',100,'complete',uid(42),uid(40),'late-night',uid(40));
      db.prepare('INSERT INTO messages VALUES(?,?,?,?,?,?,?,?,?,?)').run(uid(43),uid(40),'assistant','合成旧回答',101,'complete',uid(42),null,'late-night',uid(40));
      db.prepare('INSERT INTO branches VALUES(?,?,?,?,?,?,?,?)').run(uid(44),uid(40),uid(40),uid(43),102,uid(45),snapshot,snapshotHash);
      db.prepare('INSERT INTO drafts VALUES(?,?)').run(uid(44),'合成子分支草稿');
    } finally {db.close();}
    assert(!fs.readFileSync(database).includes(Buffer.from(key)),'v3 plaintext key');console.log('V3_SAFE_STORAGE_ENCRYPTED_OK');
    let sent=false;
    const options={database,secrets:safeStorage,tutorPrompt:'合成导师规则',personaPrompts:{default:'','late-night':'合成深夜风格'},
      transport:async(url,request)=>{
        assert(url==='https://example.test/chat/completions'&&request.headers.authorization===`Bearer ${key}`,'decrypted key unavailable');
        const messages=JSON.parse(request.body).messages;
        assert(messages[0].content==='合成导师规则\n合成深夜风格','persona not preserved');
        assert(JSON.stringify(messages.filter(item=>item.role!=='system').map(item=>item.content))===JSON.stringify(['合成旧问题','合成旧回答','合成子分支追问']),'snapshot context changed');
        sent=true;return new Response(JSON.stringify({choices:[{message:{content:'合成迁移后回答'},finish_reason:'stop'}]}),{status:200,headers:{'content-type':'application/json'}});
      }};
    service=createLearningService(options);let state=await service.getState();const topic=state.topics[0],child=topic.branches.find(item=>item.id===uid(44));
    assert(state.settings.hasApiKey&&state.settings.defaultPersona==='strict-mentor'&&topic.persona==='late-night','settings/persona lost');
    assert(topic.id===uid(40)&&topic.branches.length===2&&child.parentBranchId===uid(40)&&child.sourceMessageId===uid(43)&&state.drafts[uid(44)]==='合成子分支草稿','branch/draft lost');
    assert(topic.branches.find(item=>item.id===uid(40)).messages.map(item=>item.content).join('|')==='合成旧问题|合成旧回答','old messages changed');
    const raw=new DatabaseSync(database,{readOnly:true});
    try {
      assert(raw.prepare('PRAGMA user_version').get().user_version===4&&raw.prepare('PRAGMA application_id').get().application_id===1414287921,'database identity');
      assert(raw.prepare('PRAGMA integrity_check').get().integrity_check==='ok'&&raw.prepare('PRAGMA foreign_key_check').all().length===0,'database integrity');
      assert(Buffer.from(raw.prepare('SELECT encrypted_key FROM configuration').get().encrypted_key).equals(encrypted),'ciphertext changed');
      const row=raw.prepare('SELECT snapshot_json,snapshot_digest FROM branches WHERE id=?').get(uid(44));assert(row.snapshot_json===snapshot&&row.snapshot_digest===snapshotHash,'snapshot changed');
    } finally {raw.close();}
    console.log('V3_TO_V4_DATA_SNAPSHOT_PRESERVED_OK');
    await service.sendMessage({topicId:uid(40),branchId:uid(44),text:'合成子分支追问',requestId:crypto.randomUUID(),attachmentId:null});assert(sent,'synthetic transport not called');
    service.close();service=null;service=createLearningService(options);state=await service.getState();
    const reopened=state.topics[0].branches.find(item=>item.id===uid(44));
    assert(state.settings.hasApiKey&&state.settings.defaultPersona==='strict-mentor'&&state.topics[0].persona==='late-night'&&state.drafts[uid(44)]==='合成子分支草稿','reopen state');
    assert(reopened.parentBranchId===uid(40)&&reopened.sourceMessageId===uid(43)&&reopened.messages.some(item=>item.content==='合成迁移后回答'),'reopen branch');
    assert(!fs.readFileSync(database).includes(Buffer.from(key)),'v4 plaintext key');service.close();service=null;
    const after=hashes();fs.writeFileSync(path.join(evidence,'hashes-after.json'),JSON.stringify(after,null,2));assert(JSON.stringify(before)===JSON.stringify(after),'inputs changed');
    fs.writeFileSync(path.join(evidence,'result.json'),JSON.stringify({exitCode:0,inputsStable:true,sqlite:process.versions.sqlite,realNetwork:false},null,2));
    console.log('V3_MIGRATION_KEY_SEND_REOPEN_OK');console.log(`EVIDENCE_DIR ${evidence}`);app.quit();
  } catch(error) {if(service){try{service.close();}catch{}}console.error('V3_MIGRATION_SAFE_STORAGE_FAIL',error.message);app.exit(1);}
}).catch(()=>{console.error('V3_MIGRATION_SAFE_STORAGE_FAIL startup');app.exit(1);});
