import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
const require=createRequire(import.meta.url);
const { registerDesktopIpc }=require('../desktop-ipc.cjs');

test('only the native picker supplies paths and stale selections cannot overwrite a newer choice', async () => {
  const entry=path.resolve('desktop/app/index.html'), frame={url:pathToFileURL(entry).href};
  const webContents={mainFrame:frame,isDestroyed:()=>false}, win={webContents}; const handlers=new Map(), pending=[];
  let saved=[], readPaths=[], removed=0, sent=0;
  const service={ attachmentScope:value=>value?.branchId || value?.topicId || 'home',
    saveAttachment:async value=>{saved.push(value);return {id:'id',name:value.name,size:Buffer.byteLength(value.content),preview:value.content};},
    removeAttachment:async()=>{removed++;}, sendMessage:async()=>{sent++; return {};} };
  registerDesktopIpc({ipcMain:{handle:(name,fn)=>handlers.set(name,fn)},service,entry,getWindow:()=>win,
    chooseAttachment:()=>new Promise(resolve=>pending.push(resolve)),readAttachment:async file=>{readPaths.push(file);return {name:'资料.txt',content:'合成资料'};}});
  const event={sender:webContents,senderFrame:frame};
  const select=handlers.get('treelearn:selectAttachment'), remove=handlers.get('treelearn:removeAttachment'), send=handlers.get('treelearn:sendMessage');
  const old=select(event,{topicId:null}), newer=select(event,{topicId:null});
  assert.deepEqual(await send(event,{text:'问题'}),{ok:false,error:'ATTACHMENT_PENDING'});
  pending[1]({canceled:false,filePaths:['chosen-two']});
  const accepted=await newer;
  assert.equal(accepted.ok,true); assert.equal(accepted.value.attachment.name,'资料.txt');
  assert.equal(JSON.stringify(accepted).includes('chosen-two'),false);
  assert.deepEqual(readPaths,['chosen-two']);
  pending[0]({canceled:false,filePaths:['chosen-one']});
  assert.deepEqual(await old,{ok:false,error:'ATTACHMENT_CHANGED'});
  assert.equal(saved.length,1); assert.equal(sent,0);
  const third=select(event,{topicId:null});
  assert.equal((await remove(event,{})).ok,true);
  pending[2]({canceled:false,filePaths:['chosen-three']});
  assert.deepEqual(await third,{ok:false,error:'ATTACHMENT_CHANGED'});
  assert.equal(saved.length,1); assert.equal(removed,1);
  const cancelled=select(event,{}); pending[3]({canceled:true,filePaths:[]});
  assert.deepEqual(await cancelled,{ok:true,value:{cancelled:true}});
  assert.deepEqual(await select({sender:{},senderFrame:frame},{}),{ok:false,error:'REQUEST_INVALID'});
});

test('removal and window close while a selected file is being read discard the late result', async () => {
  const entry=path.resolve('desktop/app/index.html'), frame={url:pathToFileURL(entry).href};
  const webContents={mainFrame:frame,isDestroyed:()=>false}; const win={webContents}; const handlers=new Map();
  let activeWindow=win, resolveRead, saved=0;
  const service={attachmentScope:()=> 'home',saveAttachment:async()=>{saved++;},removeAttachment:async()=>{}};
  registerDesktopIpc({ipcMain:{handle:(name,fn)=>handlers.set(name,fn)},service,entry,getWindow:()=>activeWindow,
    chooseAttachment:async()=>({canceled:false,filePaths:['private-path']}),readAttachment:()=>new Promise(resolve=>{resolveRead=resolve;})});
  const event={sender:webContents,senderFrame:frame};
  const select=handlers.get('treelearn:selectAttachment'), remove=handlers.get('treelearn:removeAttachment');
  let pending=select(event,{}); await new Promise(resolve=>setImmediate(resolve));
  await remove(event,{}); resolveRead({name:'资料.txt',content:'内容'});
  assert.deepEqual(await pending,{ok:false,error:'ATTACHMENT_CHANGED'}); assert.equal(saved,0);
  pending=select(event,{}); await new Promise(resolve=>setImmediate(resolve));
  activeWindow=null; resolveRead({name:'资料.txt',content:'内容'});
  assert.deepEqual(await pending,{ok:false,error:'ATTACHMENT_CHANGED'}); assert.equal(saved,0);
});
