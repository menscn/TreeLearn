#!/usr/bin/env python3
"""生成知芽玻璃质感提案的静态页面。

这个脚本只写提案目录，不触碰生产 token 或应用代码。
"""

from pathlib import Path

SRC = Path(__file__).resolve().parent
OUT = SRC.parent
CSS = (SRC / "glass.css").read_text(encoding="utf-8")
ICONS = (SRC / "icons.svg").read_text(encoding="utf-8").strip()
SCREEN_KEYS = ["home", "chat", "branch", "library"]
SCREENS = {
    key: (SRC / "screens" / f"{key}.html").read_text(encoding="utf-8").strip()
    for key in SCREEN_KEYS
}

SCENE = """<div class="scene" aria-hidden="true">
        <span class="glow glow-a"></span>
        <span class="glow glow-b"></span>
        <span class="glow glow-c"></span>
        <span class="light-shaft"></span>
        <span class="canopy canopy-a"></span>
        <span class="canopy canopy-b"></span>
        <span class="sun"></span>
      </div>"""

STATUSBAR = """<div class="statusbar">
        <span class="time">9:41</span>
        <span class="status-icons">
          <svg aria-hidden="true"><use href="#i-signal"></use></svg>
          <svg aria-hidden="true"><use href="#i-wifi"></use></svg>
          <svg class="battery" aria-hidden="true"><use href="#i-battery"></use></svg>
        </span>
      </div>"""

DIRECTIONS = [
    {
        "id": "A",
        "theme": "mist",
        "file": "A-晨雾玻璃.html",
        "name": "晨雾玻璃",
        "en": "MIST GLASS",
        "tag": "推荐 · 最稳",
        "line": "白玻璃、薄雾绿、几乎无色的边缘。最接近 iOS 原生材质，也最容易从现有纸感平滑迁移。",
        "lead": "A 把玻璃做得克制：背景是低饱和的雾绿与暖沙，玻璃本身只有 62% 白度和一层 28px 模糊。它不抢内容，只在顶栏、输入框和卡片边缘留下光的厚度。阅读区单独提高到 86%，长文不会因为通透而失去对比。",
        "tagline": "把清晨的雾，压在纸和屏幕之间。",
        "specs": [
            ("场景底", "雾绿 → 暖沙"),
            ("玻璃", "白玻璃 62%"),
            ("模糊 / 饱和", "28px / 150%"),
            ("阅读底", "86%"),
            ("圆角", "22 / 28"),
            ("文字", "宋体标题 + 系统黑体"),
        ],
        "palette": ["#f3f4ee", "#cfdfd1", "#f6e5c9", "#2b6e45", "#16241c"],
        "pros": [
            ("阅读优先", "阅读区固定高不透明度，玻璃只发生在容器上。"),
            ("迁移最稳", "与现有生长绿、暖纸底和宋体品牌字冲突最小。"),
            ("降级宽容", "模糊不可用时换成半透明渐变，层级仍然成立。"),
        ],
        "cons": [
            ("存在感偏弱", "第一眼不如 B / C 有个性，需要背景光撑住质感。"),
            ("依赖光斑", "去掉场景光后会退化成普通半透明卡片。"),
        ],
        "defaults": (28, 62),
        "notes": [
            ("阅读底", "正文不跟着变透", "正文区不跟随通透度滑杆，固定 86% 白玻璃，保证 13px 正文在浅底上的对比。"),
            ("Android", "先保真，再谈模糊", "API 31+ 直接使用 RenderEffect backdrop blur；低版本回退为高不透明度渐变，不做假模糊。"),
            ("Web / 平板", "不支持就退回实色", "backdrop-filter 用 @supports 包裹；不支持时退回 86% 实色，不牺牲可读性。"),
        ],
    },
    {
        "id": "B",
        "theme": "moss",
        "file": "B-青苔温室.html",
        "name": "青苔温室",
        "en": "MOSS HOUSE",
        "tag": "最大胆",
        "line": "青苔绿底、树冠阴影和更厚的青玻璃。品牌辨识度最高，也最挑使用场景。",
        "lead": "B 把「生长」推到材质层：背景是更深的青苔绿与暖黄光斑，玻璃带一点绿色玻璃砖的厚度，饱和度和模糊都更高。它适合展示、Pad 和品牌页；放到长时间阅读的主界面，需要重新校准正文、状态色和对比度。",
        "tagline": "把整块界面，放进温室的绿荫里。",
        "specs": [
            ("场景底", "青苔 → 暖光"),
            ("玻璃", "青玻璃 52%"),
            ("模糊 / 饱和", "26px / 170%"),
            ("阅读底", "80%"),
            ("圆角", "26 / 32"),
            ("文字", "宋体标题 + 系统黑体"),
        ],
        "palette": ["#e8f0e2", "#a9c9a5", "#f4ddb0", "#1f6b44", "#10251a"],
        "pros": [
            ("品牌最强", "青苔、生长绿与树冠阴影，和「知芽」的名字直接对应。"),
            ("折射明显", "玻璃厚度、绿色投影和高饱和背景让材质一眼可见。"),
            ("展示友好", "在发布会、Pad 和营销页上更有记忆点。"),
        ],
        "cons": [
            ("阅读疲劳", "高饱和背景和更大的模糊面积会干扰长文阅读。"),
            ("迁移最大", "与当前低饱和纸感差异最大，状态色需要整体重校。"),
        ],
        "defaults": (26, 52),
        "notes": [
            ("阅读底", "青苔底要先重校正文", "阅读区保持 80%，但青色背景会把整体色温拉深；正文色需改到 #10251A 后复测对比。"),
            ("Android", "低端机去掉树冠阴影", "绿色投影和更高饱和的 blur 在低端机上更吃力；API < 31 时应去掉树冠阴影，退回静态青玻璃。"),
            ("状态色", "红 / 琥珀 / 蓝全部重测", "状态色要在青苔底上重新过一遍 AA，不能沿用纸感主题的取值。"),
        ],
    },
    {
        "id": "C",
        "theme": "lumen",
        "file": "C-浮光.html",
        "name": "浮光",
        "en": "LUMEN",
        "tag": "最轻",
        "line": "玻璃只有 30% 白度，靠 1px 高光边缘和散景定义边界。最通透，也最考验背景控制。",
        "lead": "C 把玻璃推到接近消失：18px 模糊、135% 饱和、30% 白度，界面像直接浮在光里。它最接近「高级留白」的观感，但正文和顶栏的可读性几乎完全依赖背景光；适合浏览、封面和短内容，不适合把透明度再往下调。",
        "tagline": "让内容浮在光里，玻璃几乎消失。",
        "specs": [
            ("场景底", "米金 → 雾绿"),
            ("玻璃", "超薄玻璃 30%"),
            ("模糊 / 饱和", "18px / 135%"),
            ("阅读底", "72%"),
            ("圆角", "20 / 26"),
            ("文字", "宋体标题 + 系统黑体"),
        ],
        "palette": ["#f7f3e9", "#fff1d2", "#cfe4da", "#2b6e45", "#142019"],
        "pros": [
            ("最通透", "内容和场景光几乎连成一层，没有厚重容器。"),
            ("留白高级", "弱边界让页面更像编辑设计，而不是卡片堆叠。"),
            ("层级轻", "适合短内容、封面、结果页和展示态。"),
        ],
        "cons": [
            ("可读性最险", "阅读区只有 72%，正文对比依赖背景光控制。"),
            ("降级差异大", "没有 blur 时，30% 白玻璃会直接失去边界。"),
        ],
        "defaults": (18, 30),
        "notes": [
            ("阅读底", "72% 已经是下限", "阅读区 72% 是下限；如果再降低，13px 正文在米金背景上会掉到 AA 以下。"),
            ("Android", "最依赖 blur，也最该降级", "低端机 blur 成本最高，而 C 又最依赖 blur；建议在低端机直接切换到 88% 实色版本。"),
            ("Web / 平板", "薄玻璃只在支持时启用", "只在支持 backdrop-filter 的浏览器启用薄玻璃；不支持时保留 1px 高光边缘 + 88% 实色。"),
        ],
    },
]

SCREEN_LABELS = {
    "home": "首页",
    "chat": "对话",
    "branch": "分支导航",
    "library": "学习库",
}

CONTROL_SCRIPT = """<script>
(function () {
  var board = document.querySelector('[data-board]');
  if (!board) return;
  var blur = document.getElementById('ctrl-blur');
  var alpha = document.getElementById('ctrl-alpha');
  var scene = document.getElementById('ctrl-scene');
  var reset = document.getElementById('ctrl-reset');
  var outBlur = document.getElementById('out-blur');
  var outAlpha = document.getElementById('out-alpha');
  var defaults = {
    blur: Number(board.dataset.defaultBlur),
    alpha: Number(board.dataset.defaultAlpha)
  };
  function apply() {
    var b = Number(blur.value);
    var a = Number(alpha.value);
    board.style.setProperty('--glass-blur', b + 'px');
    board.style.setProperty('--glass-a1', (a / 100).toFixed(2));
    board.classList.toggle('scene-off', scene.checked);
    outBlur.textContent = b + 'px';
    outAlpha.textContent = a + '%';
  }
  blur.addEventListener('input', apply);
  alpha.addEventListener('input', apply);
  scene.addEventListener('change', apply);
  reset.addEventListener('click', function () {
    blur.value = defaults.blur;
    alpha.value = defaults.alpha;
    scene.checked = false;
    apply();
  });
  apply();
})();
</script>"""


def phone(screen_key: str, caption: str) -> str:
    return f"""<figure class="phone-figure">
      <div class="phone">
        <div class="phone-screen">
          {SCENE}
          {STATUSBAR}
          <div class="punch" aria-hidden="true"></div>
          {SCREENS[screen_key]}
          <div class="home-indicator" aria-hidden="true"></div>
        </div>
      </div>
      <figcaption class="phone-caption">{caption}</figcaption>
    </figure>"""


def nav(active: str) -> str:
    items = [
        ("index.html", "总览", "总览"),
        ("A-晨雾玻璃.html", "A 晨雾", "A"),
        ("B-青苔温室.html", "B 青苔", "B"),
        ("C-浮光.html", "C 浮光", "C"),
    ]
    links = []
    for href, label, key in items:
        cls = "nav-home " if key == "总览" else ""
        if key == active:
            cls += "is-active "
        links.append(f'<a class="{cls.strip()}" href="{href}">{label}</a>')
    return f"""<header class="proposal-nav">
    <a class="proposal-brand" href="index.html">
      <svg class="icon" aria-hidden="true"><use href="#i-sprout"></use></svg>
      <span>知芽</span>
      <span class="tag">GLASS STUDY</span>
    </a>
    <nav>{"".join(links)}</nav>
  </header>"""


def document(title: str, body_class: str, content: str, script: str = "") -> str:
    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light">
<title>{title}</title>
<style>
{CSS}
</style>
</head>
<body class="{body_class}">
{ICONS}
{content}
{script}
</body>
</html>
"""


def swatches(colors: list[str]) -> str:
    return "".join(
        f'<span class="swatch" style="background:{color}" title="{color}"></span>'
        for color in colors
    )


def index_page() -> str:
    cards = []
    for direction in DIRECTIONS:
        specs = "".join(f"<span>{value}</span>" for _, value in direction["specs"])
        cards.append(
            f"""<article class="dir-card" data-theme="{direction['theme']}">
      <header class="dir-head">
        <div class="dir-top">
          <span class="dir-num">{direction['id']}</span>
          <span class="dir-name">{direction['name']}</span>
          <span class="dir-tag">{direction['tag']}</span>
        </div>
        <div class="dir-en">{direction['en']}</div>
        <p class="dir-line">{direction['line']}</p>
        <div class="dir-specs">{specs}</div>
        <div class="dir-palette">{swatches(direction['palette'])}</div>
      </header>
      {phone('chat', f"对话 · {direction['id']} {direction['name']}")}
      <a class="dir-link" href="{direction['file']}">进入方向页
        <svg class="icon" aria-hidden="true"><use href="#i-chevron-right"></use></svg>
      </a>
    </article>"""
        )

    rows = [
        ("第一眼", "安静、接近 iOS 原生", "温室感、品牌存在感最强", "最轻、几乎无边界"),
        ("玻璃读数", "62% · 28px · 150%", "52% · 26px · 170%", "30% · 18px · 135%"),
        ("场景光", "雾绿 + 暖沙", "青苔 + 树冠阴影", "米金 + 日光"),
        ("阅读区", "86%，对比最稳", "80%，需要重校", "72%，已经是下限"),
        ("迁移成本", "低", "高", "中"),
        ("最适合", "默认主题", "品牌展示 / Pad", "浏览 / 封面"),
        ("主要风险", "玻璃存在感偏弱", "长读容易疲劳", "可读性依赖背景"),
    ]
    body_rows = "".join(
        f"<tr><td>{label}</td><td>{a}</td><td>{b}</td><td>{c}</td></tr>"
        for label, a, b, c in rows
    )

    recos = [
        ("主推荐", "A · 晨雾玻璃", "作为默认主题落地。阅读区的对比最稳，最接近 iOS 的原生材质，也最容易从现有纸感迁移。先落 A，再用 B 的光斑和 C 的边缘高光做局部增强。"),
        ("品牌强化", "B · 青苔温室", "留给发布会、Pad 展示和品牌页。青苔底与树冠阴影能把「生长」讲清楚；不建议直接作为长文主界面。"),
        ("轻量展示", "C · 浮光", "适合封面、短内容和营销页。它最通透，但 72% 阅读区是下限，不应再往下调。"),
    ]
    reco_cards = "".join(
        f'<article class="reco-card"><div class="k">{k}</div><h3>{title}</h3><p>{body}</p></article>'
        for k, title, body in recos
    )

    content = f"""{nav("总览")}
  <main>
    <section class="proposal-hero">
      <div class="eyebrow">TREELEARN · 材质重设计提案 · 2026-10-21</div>
      <h1>把纸的安静，<br>换成<span class="accent">玻璃的通透</span>。</h1>
      <p>知芽已经有自己的骨架：芽形标识、生长绿、分支隐喻，以及以阅读为中心的层级。这次不改骨架，只换材料——让光、雾和透明度成为新的界面语言。三套方向共用同一组真实界面与文案，只改变玻璃、背景光与色温。</p>
      <div class="hero-meta">
        <span>3 个方向</span>
        <span>4 屏 × 3 = 12 个界面</span>
        <span>真实 Android 文案</span>
        <span>不改生产 token</span>
      </div>
      <p class="hero-note">先看 A，再用 B / C 校准气质。每个方向页都有通透度与模糊的实时滑杆。</p>
    </section>

    <section class="direction-strip" aria-label="三套设计方向">
      {''.join(cards)}
    </section>

    <section class="compare">
      <div class="section-title">
        <h2>三套方向，怎么选</h2>
        <p>四屏、文案和层级完全一致，只把材质这一个变量拉开。所有读数都能在方向页用滑杆复现。</p>
      </div>
      <div class="compare-wrap">
        <table>
          <thead>
            <tr>
              <th>维度</th>
              <th>A 晨雾玻璃 <span class="rec-label">推荐</span></th>
              <th>B 青苔温室</th>
              <th>C 浮光</th>
            </tr>
          </thead>
          <tbody>{body_rows}</tbody>
        </table>
      </div>
    </section>

    <section class="recommend">
      <div class="section-title">
        <h2>如果今天就要选</h2>
        <p>先确定主界面，再决定展示面。玻璃不是越透越好，阅读区必须留一层不透明的底线。</p>
      </div>
      <div class="recommend-grid">
        {reco_cards}
        <div class="reco-hybrid">
          <svg class="icon" aria-hidden="true"><use href="#i-sprout"></use></svg>
          <p><strong>混合建议：</strong>A 的结构 + B 的一层树冠阴影 + C 的 1px 高光边缘。混合后仍沿用 A 的阅读底，不改变正文对比。</p>
        </div>
      </div>
    </section>
  </main>

  <footer class="proposal-footer">
    <p>提案边界：三个方向页都是静态高保真演示，不修改 tokens.json、colors.xml、tokens.css 或任何 Android / Web 代码。选定方向后，数值仍需按「原型 :root → tokens.json → colors.xml → tokens.css → 守门」落盘。</p>
    <p>依据：现有四屏 Android fixture 文案、Docs/04-ux-design 设计 token、ic_brand_sprout 官方芽形标识。浅色主题；深色主题不在本次范围。</p>
  </footer>"""
    return document("知芽 · 玻璃质感重设计提案", "proposal-page", content)


def direction_page(direction: dict) -> str:
    blur, alpha = direction["defaults"]
    specs = "".join(
        f'<div class="spec-line"><span>{key}</span><b>{value}</b></div>'
        for key, value in direction["specs"]
    )
    pros = "".join(
        f'<div class="pc"><b>{title}：</b>{body}</div>'
        for title, body in direction["pros"]
    )
    cons = "".join(
        f'<div class="pc neg"><b>{title}：</b>{body}</div>'
        for title, body in direction["cons"]
    )
    notes = "".join(
        f'<article class="landing-note"><div class="k">{k}</div><h3>{title}</h3><p>{body}</p></article>'
        for k, title, body in direction["notes"]
    )
    board = "".join(
        phone(key, f"{SCREEN_LABELS[key]} · {direction['id']} {direction['name']}")
        for key in SCREEN_KEYS
    )

    content = f"""{nav(direction['id'])}
  <main>
    <section class="direction-hero">
      <div class="direction-title">
        <div class="eyebrow">DIRECTION {direction['id']} · {direction['en']}</div>
        <h1>{direction['name']}</h1>
        <div class="en">{direction['en']} / {direction['id']}</div>
        <p class="lead">{direction['lead']}</p>
      </div>
      <p class="direction-tagline">{direction['tagline']}</p>
    </section>

    <div class="direction-layout">
      <aside class="direction-aside">
        <section class="aside-block">
          <h2>材质参数</h2>
          <div class="spec-list">{specs}</div>
        </section>
        <section class="aside-block">
          <h2>取舍</h2>
          <div class="pc-list">{pros}{cons}</div>
        </section>
        <section class="aside-block">
          <h2>实时观察</h2>
          <div class="control-row">
            <label class="control-label" for="ctrl-blur"><span>背景模糊</span><output id="out-blur">{blur}px</output></label>
            <input id="ctrl-blur" type="range" min="6" max="40" step="1" value="{blur}">
          </div>
          <div class="control-row">
            <label class="control-label" for="ctrl-alpha"><span>玻璃不透明度</span><output id="out-alpha">{alpha}%</output></label>
            <input id="ctrl-alpha" type="range" min="24" max="80" step="1" value="{alpha}">
          </div>
          <label class="switch-row" for="ctrl-scene"><span>关闭场景光</span><input id="ctrl-scene" type="checkbox"></label>
          <button class="reset" id="ctrl-reset" type="button">恢复方向默认值</button>
          <p class="control-note">滑杆只改外框玻璃；阅读区保持固定不透明度，不拿正文对比度换通透感。</p>
        </section>
      </aside>

      <section class="board-wrap">
        <div class="board-top">
          <span>四屏同构 · 只改变材质</span>
          <span>390 × 844</span>
        </div>
        <div class="board" data-theme="{direction['theme']}" data-board data-default-blur="{blur}" data-default-alpha="{alpha}">
          {board}
        </div>
      </section>
    </div>

    <footer class="direction-footer">
      {notes}
    </footer>

    <footer class="proposal-footer">
      <p>这不是可执行的生产方案，也不替代 Docs/04-ux-design 的权威原型。选定后再走 token 落盘顺序；滑动值只用于判断材质容忍度。</p>
      <p>{direction['id']} · {direction['name']} / {direction['en']} · 提案稿 2026-10-21</p>
    </footer>
  </main>"""
    return document(
        f"{direction['id']} {direction['name']} · 知芽玻璃提案",
        "direction-page",
        content,
        CONTROL_SCRIPT,
    )


def main() -> None:
    (OUT / "index.html").write_text(index_page(), encoding="utf-8")
    for direction in DIRECTIONS:
        (OUT / direction["file"]).write_text(direction_page(direction), encoding="utf-8")
    print("已生成：")
    for name in ["index.html"] + [d["file"] for d in DIRECTIONS]:
        path = OUT / name
        print(f"  {path.name}  ({path.stat().st_size / 1024:.1f} KB)")


if __name__ == "__main__":
    main()
