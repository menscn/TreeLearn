import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { mkdtempSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
const require=createRequire(import.meta.url);
const { createLearningService }=require('../learning-service.cjs');
const secrets={isAsyncEncryptionAvailable:async()=>true,encryptStringAsync:async value=>Buffer.from(value),decryptStringAsync:async value=>({result:Buffer.from(value).toString(),shouldReEncrypt:false})};
const answer=()=>new Response(JSON.stringify({choices:[{message:{content:'合成回答'}}]}),{status:200});

test('a malformed attachment draft remains visible as a storage error and cannot be deleted or overwritten',async()=>{
  const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-malformed-draft-')), database=path.join(root,'learning.sqlite'); let service, observer;
  try {
    service=createLearningService({database,secrets,transport:async()=>answer()});
    await service.saveSettings({baseUrl:'https://example.test',model:'test',apiKey:'synthetic'});
    await service.saveDraft({text:'待保留草稿'});
    observer=new DatabaseSync(database);
    observer.prepare('UPDATE drafts SET attachment_id=NULL,attachment_name=?,attachment_content=? WHERE scope=?').run('资料.txt','必须保留的合成正文','home');
    const original=observer.prepare('SELECT * FROM drafts WHERE scope=?').get('home');
    await assert.rejects(service.getState(),/STORAGE_ERROR/);
    await assert.rejects(service.saveDraft({text:''}),/STORAGE_ERROR/);
    await assert.rejects(service.removeAttachment({}),/STORAGE_ERROR/);
    await assert.rejects(service.saveAttachment({name:'新.txt',content:'新资料'}),/STORAGE_ERROR/);
    await assert.rejects(service.sendMessage({text:'待保留草稿',requestId:'8e15d40d-5b17-46d5-a059-4e7765b6f4ac',attachmentId:null}),/STORAGE_ERROR/);
    assert.deepEqual(observer.prepare('SELECT * FROM drafts WHERE scope=?').get('home'),original);
    service.close(); service=null;
    assert.throws(()=>{service=createLearningService({database,secrets});},/STORAGE_ERROR/);
    assert.deepEqual(observer.prepare('SELECT * FROM drafts WHERE scope=?').get('home'),original);
  } finally { if(observer) observer.close(); if(service) service.close(); rmSync(root,{recursive:true,force:true}); }
});

test('malformed historical attachment metadata or digest blocks reads and deletion while preserving data',async()=>{
  for (const defect of ['partial','bad-id','bad-digest','assistant-attachment']) {
    const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-malformed-message-')), database=path.join(root,'learning.sqlite'); let service, observer;
    try {
      service=createLearningService({database,secrets,transport:async()=>answer()});
      await service.saveSettings({baseUrl:'https://example.test',model:'test',apiKey:'synthetic'});
      const attachment=await service.saveAttachment({name:'资料.txt',content:'合成资料'});
      const {topicId}=await service.sendMessage({text:'问题',requestId:'8e15d40d-5b17-46d5-a059-4e7765b6f4ac',attachmentId:attachment.id});
      observer=new DatabaseSync(database);
      if(defect==='partial') observer.prepare("UPDATE messages SET attachment_id=NULL WHERE topic_id=? AND role='user'").run(topicId);
      else if(defect==='bad-id') observer.prepare("UPDATE messages SET attachment_id='not-an-id' WHERE topic_id=? AND role='user'").run(topicId);
      else if(defect==='bad-digest') observer.prepare("UPDATE messages SET attachment_digest='bad' WHERE topic_id=? AND role='user'").run(topicId);
      else observer.prepare("UPDATE messages SET attachment_id=?,attachment_name=?,attachment_content=?,attachment_digest=? WHERE topic_id=? AND role='assistant'").run(attachment.id,'资料.txt','合成资料','digest',topicId);
      const original=observer.prepare('SELECT * FROM messages ORDER BY rowid').all();
      await assert.rejects(service.getState(),/STORAGE_ERROR/);
      await assert.rejects(service.deleteTopic({topicId}),/STORAGE_ERROR/);
      await assert.rejects(service.saveDraft({topicId,text:'保留输入'}),/STORAGE_ERROR/);
      assert.deepEqual(observer.prepare('SELECT * FROM messages ORDER BY rowid').all(),original);
      service.close(); service=null;
      assert.throws(()=>{service=createLearningService({database,secrets});},/STORAGE_ERROR/);
      assert.deepEqual(observer.prepare('SELECT * FROM messages ORDER BY rowid').all(),original);
    } finally { if(observer) observer.close(); if(service) service.close(); rmSync(root,{recursive:true,force:true}); }
  }
});

test('normal empty and nonempty drafts with or without an attachment remain editable',async()=>{
  const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-valid-draft-')); let service;
  try {
    service=createLearningService({database:path.join(root,'learning.sqlite'),secrets});
    await service.saveDraft({text:'普通草稿'}); await service.saveDraft({text:''});
    assert.equal((await service.getState()).drafts.home,undefined);
    const attachment=await service.saveAttachment({name:'空.txt',content:''});
    await service.saveDraft({text:'有附件草稿'}); await service.saveDraft({text:''});
    let state=await service.getState(); assert.equal(state.drafts.home,''); assert.equal(state.attachmentDrafts.home.id,attachment.id);
    await service.removeAttachment({}); state=await service.getState(); assert.equal(state.drafts.home,undefined);
  } finally { if(service) service.close(); rmSync(root,{recursive:true,force:true}); }
});

test('only corruption of a target branch ancestor maps to context changed',async()=>{
  const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-corrupt-scope-')), database=path.join(root,'learning.sqlite'); let service, observer, calls=0;
  try {
    service=createLearningService({database,secrets,transport:async()=>{calls++;return answer();}});
    await service.saveSettings({baseUrl:'https://example.test',model:'test',apiKey:'synthetic'});
    const attachment=await service.saveAttachment({name:'资料.txt',content:'资料原文'});
    await service.saveDraft({text:'第一主题'});
    const first=await service.sendMessage({text:'第一主题',requestId:'8e15d40d-5b17-46d5-a059-4e7765b6f4ac',attachmentId:attachment.id});
    const second=await service.sendMessage({text:'第二主题',requestId:'9e15d40d-5b17-46d5-a059-4e7765b6f4ac'});
    const state=await service.getState(), source=state.topics.find(t=>t.id===first.topicId).messages.find(m=>m.role==='assistant');
    const branch=(await service.createBranch({topicId:first.topicId,parentBranchId:first.topicId,sourceMessageId:source.id,requestId:'ae15d40d-5b17-46d5-a059-4e7765b6f4ac'})).branchId;
    observer=new DatabaseSync(database);
    observer.prepare("UPDATE messages SET attachment_content='合成损坏' WHERE topic_id=? AND role='user'").run(first.topicId);
    const old=observer.prepare('SELECT * FROM messages ORDER BY rowid').all();
    await assert.rejects(service.sendMessage({topicId:first.topicId,branchId:branch,text:'分支追问',requestId:'be15d40d-5b17-46d5-a059-4e7765b6f4ac'}),/CONTEXT_CHANGED/);
    await assert.rejects(service.sendMessage({topicId:second.topicId,text:'不相关主题',requestId:'ce15d40d-5b17-46d5-a059-4e7765b6f4ac'}),/STORAGE_ERROR/);
    await assert.rejects(service.sendMessage({topicId:first.topicId,text:'根分支',requestId:'de15d40d-5b17-46d5-a059-4e7765b6f4ac'}),/STORAGE_ERROR/);
    await assert.rejects(service.getState(),/STORAGE_ERROR/);
    assert.equal(calls,2); assert.deepEqual(observer.prepare('SELECT * FROM messages ORDER BY rowid').all(),old);
  } finally { if(observer) observer.close(); if(service) service.close(); rmSync(root,{recursive:true,force:true}); }
});

test('corrupt ancestor detected while waiting for settings returns context changed without overwriting data',async()=>{
  const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-corrupt-queue-')), database=path.join(root,'learning.sqlite');
  let service, observer, release, beginEncrypt, blocked=false, calls=0, settingsWrite, sending;
  const gate=new Promise(resolve=>{release=resolve;}), started=new Promise(resolve=>{beginEncrypt=resolve;});
  const gatedSecrets={
    isAsyncEncryptionAvailable:async()=>true,
    encryptStringAsync:async value=>{if(blocked){beginEncrypt();await gate;}return Buffer.from(value);},
    decryptStringAsync:async value=>({result:Buffer.from(value).toString(),shouldReEncrypt:false})
  };
  try {
    service=createLearningService({database,secrets:gatedSecrets,transport:async()=>{calls++;return answer();}});
    await service.saveSettings({baseUrl:'https://example.test',model:'test',apiKey:'synthetic'});
    const attachment=await service.saveAttachment({name:'资料.txt',content:'资料原文'});
    await service.saveDraft({text:'第一主题'});
    const {topicId}=await service.sendMessage({text:'第一主题',requestId:'8e15d40d-5b17-46d5-a059-4e7765b6f4ac',attachmentId:attachment.id});
    const source=(await service.getState()).topics[0].messages.find(m=>m.role==='assistant');
    const branchId=(await service.createBranch({topicId,parentBranchId:topicId,sourceMessageId:source.id,requestId:'9e15d40d-5b17-46d5-a059-4e7765b6f4ac'})).branchId;
    await service.saveDraft({topicId,branchId,text:'保留分支输入'});
    blocked=true;
    settingsWrite=service.saveSettings({baseUrl:'https://example.test',model:'test',apiKey:'synthetic-new'});
    settingsWrite.catch(()=>{});
    await started;
    sending=service.sendMessage({topicId,branchId,text:'保留分支输入',requestId:'ae15d40d-5b17-46d5-a059-4e7765b6f4ac'});
    sending.catch(()=>{});
    const pending=await Promise.race([sending.then(()=>false,()=>false),new Promise(resolve=>setTimeout(()=>resolve(true),20))]);
    assert.equal(pending,true,'send must wait behind the in-progress settings operation');
    observer=new DatabaseSync(database);
    observer.prepare("UPDATE messages SET attachment_content='合成损坏' WHERE topic_id=? AND role='user'").run(topicId);
    const oldMessages=observer.prepare('SELECT * FROM messages ORDER BY rowid').all();
    const oldDraft=observer.prepare('SELECT * FROM drafts WHERE scope=?').get(branchId);
    release();
    await assert.rejects(settingsWrite,/STORAGE_ERROR/); settingsWrite=null;
    await assert.rejects(sending,/CONTEXT_CHANGED/); sending=null;
    assert.equal(calls,1);
    assert.deepEqual(observer.prepare('SELECT * FROM messages ORDER BY rowid').all(),oldMessages);
    assert.deepEqual(observer.prepare('SELECT * FROM drafts WHERE scope=?').get(branchId),oldDraft);
  } finally {
    if(release) release(); if(settingsWrite) await settingsWrite.catch(()=>{}); if(sending) await sending.catch(()=>{});
    if(observer) observer.close(); if(service) service.close(); rmSync(root,{recursive:true,force:true});
  }
});
