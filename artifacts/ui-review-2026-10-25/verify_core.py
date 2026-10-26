# -*- coding: utf-8 -*-
"""渲染 v3 改后核心界面板，量溢出、关键元素边界、最小字号。"""
import pathlib
from playwright.sync_api import sync_playwright

D = pathlib.Path(__file__).parent
F = D / "改后核心界面-2026-10-25.html"

JS = """() => {
  const r = e => { const b = e.getBoundingClientRect(); return {x:+b.x.toFixed(1), y:+b.y.toFixed(1), w:+b.width.toFixed(1), h:+b.height.toFixed(1), b:+b.bottom.toFixed(1)}; };
  const out = {page: {sw: document.documentElement.scrollWidth, sh: document.body.scrollHeight, iw: innerWidth}};
  out.webs = [...document.querySelectorAll('.web')].map(f => {
    const fr = f.getBoundingClientRect();
    const sc = f.querySelector('.wscroll');
    const last = f.querySelector('.wpage') ? [...f.querySelectorAll('.wpage > *')].pop() : null;
    const scroll = sc ? {client: sc.clientHeight, scroll: sc.scrollHeight, pad: getComputedStyle(sc).paddingTop} : null;
    return {frame: r(f), scroll, lastBottom: last ? +(last.getBoundingClientRect().bottom - fr.top).toFixed(1) : null,
      right: f.scrollWidth > f.clientWidth, dom: f.parentElement.parentElement.id};
  });
  out.phones = [...document.querySelectorAll('.ph')].map(p => {
    const pb = p.getBoundingClientRect();
    const tabs = p.querySelector('.ptabs');
    const lastH = p.querySelector('.phist') ? p.querySelector('.phist') : null;
    const reader = p.querySelector('.creader');
    const dock = p.querySelector('.cdock');
    const items = [...p.children].map(c => ({c: c.className, ...r(c)}));
    return {id: p.closest('.pwrap') ? p.closest('.pwrap').querySelector('.ptag').innerText : p.closest('section').id,
      h: +pb.height.toFixed(1), tabsTop: tabs ? +(tabs.getBoundingClientRect().top - pb.top).toFixed(1) : null,
      histBottom: lastH ? +(lastH.getBoundingClientRect().bottom - pb.top).toFixed(1) : null,
      readerOverflow: reader ? reader.scrollHeight - reader.clientHeight : null,
      dockTop: dock ? +(dock.getBoundingClientRect().top - pb.top).toFixed(1) : null,
      items};
  });
  const sizes = [...document.querySelectorAll('body *')].filter(e => e.childElementCount === 0 && (e.innerText||'').trim())
    .map(e => ({s: parseFloat(getComputedStyle(e).fontSize), t: (e.innerText||'').trim().slice(0,24), c: e.className}))
    .filter(o => o.s < 11).sort((a,b)=>a.s-b.s);
  out.small = sizes.slice(0, 12);
  out.imgs = [...document.images].filter(i=>!i.complete||!i.naturalWidth).length;
  return out;
}"""

with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": 1560, "height": 1100}, device_scale_factor=1)
    errs = []
    pg.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
    pg.goto(F.resolve().as_uri())
    pg.wait_for_timeout(700)
    import json
    print(json.dumps(pg.evaluate(JS), ensure_ascii=False, indent=1))
    print("console", errs[:3])
    pg.screenshot(path=str(D / "v3-full.png"), full_page=True)
    b.close()
