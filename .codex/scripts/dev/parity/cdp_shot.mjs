#!/usr/bin/env node
/**
 * 1:1 闭环量测的「产图侧」仪器（T-2026-10-15-006，前身 artifacts/tmp/cdp_shot.mjs）。
 * 打开高保真原型 → 按屏档案回放点击 → 对 .screen 做 2x 截图。
 *
 * 产图约定是**外部契约**：`.codex/scripts/dev/parity/measure_screen.py --kind render` 的
 * 制式常量（2x 缩放、右侧滚动条占 4 个逻辑像素、整图左移 2 个逻辑像素）就是按这个约定读图的。
 * 因此本工具不只「截出一张图」，还要在退出前证明这张图仍符合档案声明的制式；不符合判 FAIL，
 * 而不是把一张不符合约定的图交给下游去量。
 *
 * 用法：node .codex/scripts/dev/parity/cdp_shot.mjs --screen chat --out <png>
 *       node .codex/scripts/dev/parity/cdp_shot.mjs --selftest
 */
import fs from "node:fs";
import path from "node:path";
import * as K from "./parity_cdp.mjs";
import { selftest as battery } from "./cdp_shot_selftest.mjs";

const SPEC = ["--screen", "--out", "--profile", "--browser", "--attach", "--url", "--scroll", "--scale", "--wait-ms"];

/** PNG 宽高读自 IHDR（签名 8 字节 + chunk 长度 4 + 类型 4 → 宽在偏移 16）。 */
function pngSize(absPath) {
  const b = fs.readFileSync(absPath);
  if (b.length < 24 || b.slice(0, 8).toString("hex") !== "89504e470d0a1a0a") throw new K.Fault(`产出的不是合法 PNG: ${absPath}`);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20), bytes: b.length };
}

export const USAGE = "usage: node .codex/scripts/dev/parity/cdp_shot.mjs --screen <名> --out <png> "
  + "[--scroll none|top|bottom] [--profile <json>] [--browser <exe>] [--attach <port>] "
  + "[--url <现成的 file:/// URL，不再二次转义>] [--scale N] [--wait-ms N] | --selftest";

export async function run(argv) {
  /* 打印自己的 argv，而不是 process.argv：自检在同进程里调 run()，
     若记成驱动那一条，产物会声称一次从未发生过的调用。 */
  const cmd = ["cdp_shot.mjs", ...argv].join(" ");
  const { opts } = K.parseArgs(argv, SPEC);
  if (!opts["--screen"]) throw new K.Usage("必须给 --screen <屏名>（点击序列与原型路径要从档案取）");
  if (!opts["--out"]) throw new K.Usage("必须给 --out <png>：产图路径显式给出，避免覆盖已归档的基线图");
  const scroll = opts["--scroll"] || "none";
  if (!["none", "top", "bottom"].includes(scroll)) throw new K.Usage(`--scroll 只认 none|top|bottom，收到 ${scroll}`);

  const profilePath = path.resolve(opts["--profile"] || K.DEFAULT_PROFILE);
  const profile = K.readJson(profilePath, "屏档案");
  const entry = K.pickScreen(profile, opts["--screen"]);
  const proto = K.prototypeOf(entry, opts["--screen"]);
  const defaults = profile.defaults || {};
  const declaredScale = defaults.scale_physical_per_logical || 2;
  const scale = opts["--scale"] ? parseInt(opts["--scale"], 10) : declaredScale;
  if (!Number.isInteger(scale) || scale < 1) throw new K.Usage(`--scale 必须是正整数，收到 ${opts["--scale"]}`);
  const waitMs = opts["--wait-ms"] ? parseInt(opts["--wait-ms"], 10) : 20000;

  const where = opts["--url"] ? { abs: opts["--url"], url: opts["--url"] } : K.htmlUrl(proto);
  /* --url 收的是**现成的 URL**，不再 encodeURI 一次：档案路径那支已由 htmlUrl 编过，
     这里二次编码会把 % 变成 %25，中文文件名就剩一层没解回来（实测被判「文件不存在」）。 */
  if (opts["--url"] && where.url.startsWith("file:")) {
    /* 逃生口也必须 fail-closed：--url 指的是本机文件却不存在，属「缺被核对象」(3)，
       不是「页面结构不符」(1)——让浏览器导航过去再等 .screen 会把前者读成后者。 */
    const p = decodeURI(where.url.replace(/^file:\/\/\/?/i, "")).replace(/\//g, path.sep);
    if (!fs.existsSync(p)) throw new K.Fault(`--url 指向的本机文件不存在: ${p}`);
  }
  const out = path.resolve(opts["--out"]);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  const header = [
    `profile ${K.fingerprint(profilePath, "screens.json")}`,
    opts["--url"] ? "html 由 --url 显式给出，未核档案路径" : K.fingerprint(where.abs, "prototype.html"),
    `scroll=${scroll} wait_ms=${waitMs} declared_scale=${declaredScale}`,
  ];

  const ledger = new K.Ledger();
  const browser = K.findBrowser(opts["--browser"]);
  const br = await K.openBrowser(browser, opts["--attach"] ? parseInt(opts["--attach"], 10) : 0);
  try {
    const version = await br.cdp.version();
    await br.cdp.send("Page.enable");
    await br.cdp.send("Runtime.enable");
    /* 导航是否真的生效必须自己证明，不能假定：实测在同一个 node 进程里连开多个无头实例时，
       紧跟 /json/new 的那次 Page.navigate 会偶发地不落进新文档，标签页停在 about:blank。
       about:blank 的 readyState 同样是 complete，于是旧写法把「本机竞态」读成「原型结构不符」(1)，
       而那条页面判据与真正想测的分支同码——电池里出现过假绿。 */
    let navRetries = 0;
    const navigated = async () => {
      const nav = await br.cdp.send("Page.navigate", { url: where.url });
      if (nav && nav.errorText) throw new K.Fault(`导航被浏览器拒绝: ${nav.errorText}（目标 ${where.url}）`);
      return br.cdp.waitUntil("location.href !== 'about:blank'", 8000);
    };
    if (!(await navigated())) {
      navRetries += 1;
      if (!(await navigated())) {
        const stuck = await br.cdp.eval("({href: location.href, ready: document.readyState})");
        throw new K.Fault(`导航两次都没生效：标签页仍在 ${stuck.href}（readyState=${stuck.ready}，目标 ${where.url}）`);
      }
    }
    /* 重试必须可见（与 browser-relaunch 同一做法）：静默重试把「本机有竞态」读成「一次跑通」。 */
    if (navRetries) ledger.ok(`navigation-retry = ${navRetries} 次（about:blank 未让位，Windows 启动竞态）`);
    if (!(await br.cdp.waitFor(".screen", 60000))) {
      const probe = await br.cdp.eval("({ready: document.readyState, href: location.href, title: document.title})");
      ledger.fail(`page-not-rendered .screen 在 60s 内没出现（readyState=${probe.ready} href=${probe.href} title=${probe.title}）`);
      return await K.finish(ledger, { command: cmd, header });
    }
    await K.sleep(1200);
    ledger.ok(`browser = ${version}`);
    /* 重试必须可见：静默重试的仪器把「本机有竞态」读成「一次跑通」。 */
    if (br.relaunches) ledger.ok(`browser-relaunch = ${br.relaunches} 次早期退出后起来（Windows 启动器交接竞态）`);
    ledger.ok(`html = ${where.abs}`);
    ledger.ok(`clicks = ${proto.clicks.join(" -> ")}`);

    if (!(await K.replay(br.cdp, proto, (m) => ledger.fail(m)))) {
      return await K.finish(ledger, { command: cmd, header });
    }
    /* 超时与仪器坏了必须落在不同判据行：页面已加载却等不到目标属「结构不符」(1)，
       等不到 CDP 应答才属仪器故障 (3)。这里能走到说明 CDP 仍在应答。 */
    if (!(await br.cdp.waitFor(proto.wait_for, waitMs))) {
      const ready = await br.cdp.eval("document.readyState");
      ledger.fail(`wait-for-absent ${proto.wait_for} 在 ${waitMs}ms 内没出现（readyState=${ready}）——页面已加载，属结构不符而非仪器故障`);
      return await K.finish(ledger, { command: cmd, header });
    }
    await K.sleep(1500);

    if (scroll !== "none") {
      await br.cdp.eval(scroll === "top"
        ? `(document.querySelector('.body-scroll').scrollTop = 0, true)`
        : `(function(){var e=document.querySelector('.body-scroll'); e.scrollTop=e.scrollHeight; return true;})()`);
      await K.sleep(700);
    }

    const rect = await br.cdp.eval(
      `(function(){ var el=document.querySelector('.screen'); if(!el) return null; var r=el.getBoundingClientRect(); return {x:r.x,y:r.y,w:r.width,h:r.height}; })()`
    );
    if (!rect || !rect.w) {
      ledger.fail("screen-rect-absent .screen 量不到几何（页面结构变了）");
    } else {
      ledger.ok(`screen-rect = ${rect.w}x${rect.h} @ ${rect.x},${rect.y} (逻辑 px)`);
    }

    /* 制式接缝：渲染侧的滚动条占位必须仍是档案声明的那个量。 */
    const sb = await br.cdp.eval(
      `(function(){var e=document.querySelector('.body-scroll'); if(!e) return null; return {offset:e.offsetWidth, client:e.clientWidth, occupied:e.offsetWidth-e.clientWidth};})()`
    );
    const declared = defaults.right_scrollbar_logical_px;
    if (!sb) ledger.fail("scrollbar-probe-absent 找不到 .body-scroll，无法证明右侧占位约定");
    else if (sb.occupied !== declared) {
      ledger.fail(`scrollbar-occupancy-drift 实测占位=${sb.occupied} 档案声明=${declared}；`
        + `measure_screen.py 的 --kind render 扣减量与此同源，改这里必须同步那条常量`);
    } else ledger.ok(`scrollbar-occupancy = ${sb.occupied} = 档案声明 ${declared}`);

    /* captureBeyondViewport 必须为 true：.screen 高 816 > 视口时只有整页渲染才截得全。
       反例（2026-09-27 踩过）：false 时 clip 按页面坐标解释但只截视口，输出是页面顶部一片
       纯色——看起来"截到了"其实是废图，所以截完必须回读尺寸。 */
    const shot = rect && rect.w ? await br.cdp.send("Page.captureScreenshot", {
      format: "png", captureBeyondViewport: true,
      clip: { x: rect.x, y: rect.y, width: rect.w, height: rect.h, scale },
    }) : null;
    /* 制式接缝二：产出比例必须等于档案声明的那个 scale。
       判据不能拿「自己刚用的 scale」和「产出的 scale」比——那样 --scale 1 永远自证清白，
       而下游 measure_screen.py --kind render 是按档案声明值读图的。 */
    if (scale !== declaredScale) {
      ledger.fail(`shot-scale-override 本次 scale=${scale} 而档案声明=${declaredScale}；`
        + `此图不可交给 --kind render（它按 ${declaredScale}x 反算逻辑像素），请用默认比例重跑或同步档案`);
    } else if (!shot) {
      ledger.fail("no-shot-skipped 没有可用几何，未产图");
    } else {
      fs.writeFileSync(out, Buffer.from(shot.data, "base64"));
      const got = pngSize(out);
      if (rect) {
        const expW = Math.round(rect.w * scale);
        const expH = Math.round(rect.h * scale);
        /* ±1 物理像素容忍 clip 原点的设备像素取整（复刻方案 §10.1 的第二条偏移）。 */
        if (Math.abs(got.w - expW) > 1 || Math.abs(got.h - expH) > 1) {
          ledger.fail(`shot-scale-mismatch 产出 ${got.w}x${got.h}，按 scale=${scale} 应为 ${expW}x${expH}`
            + `（档案声明 ${declaredScale}）——下游按该比例读图，此图不可用`);
        } else {
          ledger.ok(`shot = ${out} ${got.w}x${got.h} bytes=${got.bytes} scale=${scale}`);
        }
      }
    }
    return await K.finish(ledger, { command: cmd, header });
  } finally {
    await br.close();
  }
}

if (K.isMain(import.meta.url)) {
  const argv = process.argv.slice(2);
  process.exitCode = await K.mainTool(async () => {
    if (argv.includes("--help")) { console.log(USAGE); console.log("EXITCODE=0"); return K.EXIT.OK; }
    /* 电池住在独立伴随文件里（判定面与测试面分开的口径见该文件页首）。
       必须**静态**导入：伴随文件反过来 import 本文件的 run()，所以从本文件的顶层 await 里
       用 await import() 动态取它时，被导入模块的求值要等本模块求值结束、而本模块正卡在
       await 上——进程以退出码 13（unsettled top-level await）结束且 stdout 一行不写，
       只有 stderr 一句 Node 告警。该机理有独立最小复现（两文件、不依赖浏览器），
       由 GREEN 块 02 现场跑出（动态=13、静态=成功），不是推测。 */
    if (argv.includes("--selftest")) return battery();
    if (!argv.length) throw new K.Usage(USAGE);
    return run(argv);
  });
}
