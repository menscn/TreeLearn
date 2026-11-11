import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
const require=createRequire(import.meta.url);
const { readSelectedAttachment }=require('../attachment-reader.cjs');

test('selected text is decoded strictly and preserves all characters without exposing path', async () => {
  const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-attachment-read-'));
  try {
    const file=path.join(root,'合成资料.md'); const content='前文\r\n引用 "不可信"\n结尾'; writeFileSync(file,content,'utf8');
    const result=await readSelectedAttachment(file);
    assert.deepEqual(result,{name:'合成资料.md',content,size:Buffer.byteLength(content)});
    assert.equal(JSON.stringify(result).includes(root),false);
  } finally { rmSync(root,{recursive:true,force:true}); }
});

test('empty files and BOM survive intact while binary NUL is rejected', async () => {
  const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-attachment-encoding-'));
  try {
    const empty=path.join(root,'empty.txt'), bom=path.join(root,'bom.md'), binary=path.join(root,'binary.txt');
    writeFileSync(empty,''); writeFileSync(bom,Buffer.from([0xef,0xbb,0xbf,0xe4,0xb8,0xad])); writeFileSync(binary,Buffer.from([0x61,0,0x62]));
    assert.deepEqual(await readSelectedAttachment(empty),{name:'empty.txt',content:'',size:0});
    assert.deepEqual(await readSelectedAttachment(bom),{name:'bom.md',content:'\ufeff中',size:6});
    await assert.rejects(readSelectedAttachment(binary),/ATTACHMENT_ENCODING/);
  } finally { rmSync(root,{recursive:true,force:true}); }
});

test('after file is opened replacing its path cannot change which content is imported', async t => {
  const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-attachment-replace-'));
  const file=path.join(root,'selected.txt'), moved=path.join(root,'original.txt'); const open=fs.open; let swapped=false;
  try {
    writeFileSync(file,'原来选择的文件');
    fs.open=async (...args)=>{
      const handle=await open(...args);
      try { renameSync(file,moved); writeFileSync(file,'替换后的陌生文件'); swapped=true; }
      catch(error) { await handle.close(); if(['EPERM','EACCES','EBUSY'].includes(error.code)) { const unavailable=new Error('replacement unavailable'); unavailable.code='TEST_REPLACE_UNAVAILABLE'; throw unavailable; } throw error; }
      return handle;
    };
    let result;
    try { result=await readSelectedAttachment(file); }
    catch(error) { if(!swapped && /ATTACHMENT_READ_FAILED/.test(error.message)) { t.skip('Windows refused replacement while selected file was open'); return; } throw error; }
    assert.equal(swapped,true); assert.equal(result.content,'原来选择的文件');
  } finally { fs.open=open; rmSync(root,{recursive:true,force:true}); }
});

test('a different file substituted between inspection and opening is rejected', async () => {
  const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-attachment-open-race-'));
  const file=path.join(root,'selected.txt'), moved=path.join(root,'original.txt'); const open=fs.open; let swapped=false;
  try {
    writeFileSync(file,'原始内容');
    fs.open=async (...args)=>{ renameSync(file,moved); writeFileSync(file,'替换的内容'); swapped=true; return open(...args); };
    await assert.rejects(readSelectedAttachment(file),/ATTACHMENT_READ_FAILED/);
    assert.equal(swapped,true); assert.equal(readFileSync(file,'utf8'),'替换的内容'); assert.equal(readFileSync(moved,'utf8'),'原始内容');
  } finally { fs.open=open; rmSync(root,{recursive:true,force:true}); }
});

test('the entire 20,000 UTF-16 unit document is returned without truncation', async () => {
  const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-attachment-limit-'));
  try {
    const file=path.join(root,'limit.txt'), content='中'.repeat(19998)+'😊';
    writeFileSync(file,content,'utf8');
    const result=await readSelectedAttachment(file);
    assert.equal(result.content.length,20000); assert.equal(result.content,content); assert.equal(result.size,Buffer.byteLength(content));
  } finally { rmSync(root,{recursive:true,force:true}); }
});

test('rejects unsupported extension, invalid UTF-8, too many characters, oversized bytes and directories', async () => {
  const root=mkdtempSync(path.join(os.tmpdir(),'treelearn-attachment-invalid-'));
  try {
    const pdf=path.join(root,'fake.pdf'), invalid=path.join(root,'invalid.txt'), long=path.join(root,'long.txt'), bytes=path.join(root,'bytes.md'), directory=path.join(root,'dir.txt');
    writeFileSync(pdf,'synthetic'); writeFileSync(invalid,Buffer.from([0xc3,0x28])); writeFileSync(long,'中'.repeat(20001)); writeFileSync(bytes,Buffer.alloc(131073,0x61)); mkdirSync(directory);
    await assert.rejects(readSelectedAttachment(pdf),/ATTACHMENT_INVALID/);
    await assert.rejects(readSelectedAttachment(invalid),/ATTACHMENT_ENCODING/);
    await assert.rejects(readSelectedAttachment(long),/ATTACHMENT_TOO_LARGE/);
    await assert.rejects(readSelectedAttachment(bytes),/ATTACHMENT_TOO_LARGE/);
    await assert.rejects(readSelectedAttachment(directory),/ATTACHMENT_INVALID/);
  } finally { rmSync(root,{recursive:true,force:true}); }
});
