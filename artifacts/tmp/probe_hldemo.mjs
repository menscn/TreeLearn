const PORT = 9222;
const URL_ = encodeURI(process.argv[2]);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let logs = [];
class CDP {
  constructor(ws){ this.id=0; this.pending=new Map();
    ws.addEventListener('message',(e)=>{ const m=JSON.parse(e.data);
      if(m.method==='Runtime.consoleAPICalled') logs.push(['console', m.params.type, m.params.args.map(a=>a.value!==undefined?String(a.value):(a.description||'')).join(' ')]);
      if(m.method==='Runtime.exceptionThrown') logs.push(['EXC', m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text]);
      if(m.method==='Log.entryAdded') logs.push(['log', m.params.entry.level, m.params.entry.text]);
      if(m.method==='Runtime.bindingCalled') logs.push(['binding', m.params.name]);
      if(m.id && this.pending.has(m.id)){ const {res,rej}=this.pending.get(m.id); this.pending.delete(m.id); m.error?rej(new Error(JSON.stringify(m.error))):res(m.result); }
    }); }
  send(method,params={}){ const id=++this.id; return new Promise((res,rej)=>{ this.pending.set(id,{res,rej}); this.ws.send(JSON.stringify({id,method,params})); }); }
  async eval(expr){ const r=await this.send('Runtime.evaluate',{expression:expr,returnByValue:true}); return r.result.value; }
}
(async()=>{
  const tab = await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`,{method:'PUT'}).then(r=>r.json());
  const ws = new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((res,rej)=>{ws.addEventListener('open',res);ws.addEventListener('error',rej);});
  const cdp = new CDP(ws); cdp.ws = ws;
  await cdp.send('Page.enable'); await cdp.send('Runtime.enable'); await cdp.send('Log.enable');
  await cdp.send('Page.navigate',{url:URL_});
  await sleep(12000);
  const info = await cdp.eval(`JSON.stringify({
    root: !!document.getElementById('root'),
    rootChildren: document.getElementById('root') ? document.getElementById('root').childElementCount : -1,
    scripts: Array.from(document.scripts).map(s=>({src:s.src||'(inline)', ok:true})),
    react: typeof React, rdom: typeof ReactDOM, babel: typeof Babel,
    demo: typeof window.__demo
  })`);
  console.log('INFO:', info);
  console.log('LOGS:');
  logs.slice(0,40).forEach(l=>console.log(' ', JSON.stringify(l).slice(0,500)));
  ws.close();
  await fetch(`http://127.0.0.1:${PORT}/json/close/${tab.id}`).catch(()=>{});
  process.exit(0);
})().catch(e=>{console.error('FAILED:',e.message, e.cause?e.cause.message:'');process.exit(1);});
