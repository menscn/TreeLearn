#!/usr/bin/env node
/**
 * One-off Web capture driver for T-2026-10-22-063.
 *
 * Reuses the repo's CDP instrument (`parity_cdp.mjs`) instead of adding Playwright: the judgement
 * is `check_glass_depth.py` reading these PNGs, so all the driver has to guarantee is a viewport
 * screenshot at a declared size, taken after the page settled, optionally scrolled.
 *
 *   node web_capture.mjs --url http://127.0.0.1:5174/ --out home-top.png \
 *        --width 1440 --height 900 --scale 2 --scroll .page-scroll --to bottom
 *
 * `--click <selector>` runs before the settle wait so a route can be entered without a driver
 * per page. Exit codes follow the repo convention: 0 wrote the PNG, 3 the instrument could not.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import * as K from "../../../.codex/scripts/dev/parity/parity_cdp.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const SPEC = ["--url", "--out", "--width", "--height", "--scale", "--scroll", "--to", "--click", "--wait-ms"];

function arg(argv, name, fallback) {
  const i = argv.indexOf(name);
  return i >= 0 && argv[i + 1] !== undefined ? argv[i + 1] : fallback;
}

/** The scroll target is addressed by selector, and the driver proves the scroll actually moved. */
async function scrollTo(cdp, sel, to) {
  const moved = await cdp.eval(`(function(){
    var el = document.querySelector(${JSON.stringify(sel)});
    if (!el) return {err: 'no such scroller ' + ${JSON.stringify(sel)}};
    var before = el.scrollTop;
    el.scrollTop = ${to === "bottom" ? "el.scrollHeight" : "0"};
    return {before: before, after: el.scrollTop, range: el.scrollHeight - el.clientHeight};
  })()`);
  return moved;
}

export async function run(argv) {
  const url = arg(argv, "--url");
  const out = arg(argv, "--out");
  if (!url || !out) throw new K.Usage("--url and --out are required");
  const width = Number(arg(argv, "--width", "1440"));
  const height = Number(arg(argv, "--height", "900"));
  const scale = Number(arg(argv, "--scale", "2"));
  const waitMs = Number(arg(argv, "--wait-ms", "1500"));
  const abs = path.isAbsolute(out) ? out : path.resolve(HERE, out);

  const br = await K.openBrowser(K.findBrowser(null), null, 3);
  try {
    await br.cdp.send("Page.enable");
    await br.cdp.send("Emulation.setDeviceMetricsOverride", {
      width, height, deviceScaleFactor: scale, mobile: false,
    });
    /* 取景时关掉滚动条，材质与判据都不动。理由：`check_glass_depth.py` 把「画面左右 0.5%–3% 的列」
       当作背景参照，Chromium 常驻的 15px 经典滚动条正好占住右侧那几列，把参照抬成近白，J1 于是
       读成 4.4 L；右侧缘扫描也会被滚动条边界截住（实测 right=1233=w−3）。Android 那端的悬浮面
       没有这个构件，两端要用同一取区规则就必须先去掉这个只属于浏览器壳的像素来源。 */
    await br.cdp.send("Emulation.setScrollbarsHidden", { hidden: true });
    await br.cdp.send("Page.navigate", { url });
    if (!(await br.cdp.waitFor(".workspace-topbar", 45000))) {
      throw new K.Fault(`页面没起来：45s 内看不到 .workspace-topbar（${url}）`);
    }
    const click = arg(argv, "--click");
    if (click) {
      const hit = await br.cdp.eval(`(function(){
        var el = document.querySelector(${JSON.stringify(click)});
        if (!el) return {err: 'no such target ' + ${JSON.stringify(click)}};
        el.click();
        return {clicked: true};
      })()`);
      if (hit?.err) throw new K.Fault(hit.err);
    }
    await K.sleep(waitMs);
    const sel = arg(argv, "--scroll");
    if (sel) {
      const moved = await scrollTo(br.cdp, sel, arg(argv, "--to", "bottom"));
      if (moved?.err) throw new K.Fault(moved.err);
      console.log(`SCROLL ${sel} to=${arg(argv, "--to", "bottom")} before=${moved.before} `
        + `after=${moved.after} range=${moved.range}`);
      await K.sleep(400);
    }
    /* 不用 clip：本机 Edge 的无头实例在「设备像素比 2 + clip scale 2」下会让
       Page.captureScreenshot 永不返回（实测挂满 150s 超时）；视口截图本身已是
       width*scale × height*scale，够用且少一个会卡死的旋钮。产出尺寸回读把这条钉住。 */
    const shot = await br.cdp.send("Page.captureScreenshot", { format: "png" });
    const png = Buffer.from(shot.data, "base64");
    if (png.length < 1000 || png.subarray(0, 8).toString("hex") !== "89504e470d0a1a0a") {
      throw new K.Fault(`产出的不是合法 PNG（${png.length} 字节）`);
    }
    const w = png.readUInt32BE(16);
    const h = png.readUInt32BE(20);
    if (w !== width * scale || h !== height * scale) {
      throw new K.Fault(`产出 ${w}x${h}，按 ${scale}x 应为 ${width * scale}x${height * scale}`
        + "——判据按声明尺寸取区，此图不可用");
    }
    fs.writeFileSync(abs, png);
    console.log(`CAPTURED ${path.basename(abs)} ${w}x${h} bytes=${png.length} viewport=${width}x${height}@${scale}x`);
    return K.EXIT.OK;
  } finally {
    await br.close();
  }
}

if (K.isMain(import.meta.url)) {
  run(process.argv.slice(2))
    .then((code) => { console.log(`EXITCODE=${code}`); process.exitCode = code; })
    .catch((e) => {
      const kind = e instanceof K.Usage ? K.EXIT.USAGE : e instanceof K.Fault ? K.EXIT.FAULT : K.EXIT.FAIL;
      console.log(`ERROR ${e.message}`);
      console.log(`EXITCODE=${kind}`);
      process.exitCode = kind;
    });
}
