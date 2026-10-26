# -*- coding: utf-8 -*-
"""渲染评审板，量最小字号与溢出，截三张检查图。"""
import pathlib

from playwright.sync_api import sync_playwright

D = pathlib.Path(__file__).parent
JS_MIN = """() => {
  let min = 999, who = '';
  for (const e of document.querySelectorAll('body *')) {
    const t = (e.innerText || '').trim();
    if (!t) continue;
    const s = parseFloat(getComputedStyle(e).fontSize);
    if (s < min) { min = s; who = e.className + ' :: ' + t.slice(0, 28); }
  }
  return {min, who};
}"""

with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": 1440, "height": 1000})
    errs = []
    pg.on("console", lambda m: errs.append(m.text) if m.type == "error" else None)
    pg.goto((D / "知芽界面可优化点-评审板-2026-10-25.html").resolve().as_uri())
    pg.wait_for_timeout(900)
    print("页高", pg.evaluate("document.body.scrollHeight"))
    print("横向溢出", pg.evaluate("document.documentElement.scrollWidth > innerWidth"))
    print("图片 加载失败数", pg.evaluate("[...document.images].filter(i=>!i.complete||!i.naturalWidth).length"))
    print("最小字号", pg.evaluate(JS_MIN))
    for i, y in enumerate([0, 1400, 4200, 7000]):
        pg.evaluate("y => window.scrollTo(0, y)", y)
        pg.wait_for_timeout(260)
        pg.screenshot(path=str(D / f"check-{i+1}.png"))
    print("console errors", errs[:3])
    b.close()
