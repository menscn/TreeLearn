# -*- coding: utf-8 -*-
"""Generate figwright batch commands for the My-Forest prototype (切片 0)."""
import json

V = json.load(open("artifacts/figwright/var_ids.json", encoding="utf-8"))

HEX = {
    "bg": (0.9569, 0.9451, 0.9216), "surface": (0.9882, 0.9843, 0.9725),
    "ink": (0.0863, 0.1412, 0.1098), "ink2": (0.2627, 0.3294, 0.2863),
    "ink3": (0.3843, 0.4392, 0.4039), "line": (0.8863, 0.8745, 0.8392),
    "green": (0.1686, 0.4314, 0.2706), "green-deep": (0.0863, 0.2510, 0.1529),
    "green-soft": (0.8902, 0.9412, 0.9059), "green-faint": (0.9412, 0.9686, 0.9451),
    "amber": (0.6902, 0.3176, 0.0314), "amber-soft": (0.9843, 0.9529, 0.8863),
    "blue": (0.1451, 0.3882, 0.9216), "red": (0.7529, 0.2235, 0.1686),
}

ops = []
def op(tool, args):
    ops.append({"tool": tool, "args": args})
    return len(ops) - 1

def paint(name, opacity=None):
    r, g, b = HEX[name]
    p = {"type": "SOLID", "color": {"r": r, "g": g, "b": b},
         "boundVariables": {"color": V["color/" + name]}}
    if opacity is not None:
        p["opacity"] = opacity
    return [p]

def fills(ref, name, opacity=None):
    op("set_fills", {"nodeId": "$ref:%d" % ref, "fills": paint(name, opacity)})

def text(parent, chars, size, color, x=None, y=None):
    a = {"parentId": "$ref:%d" % parent, "characters": chars, "fontSize": size}
    if x is not None: a["x"] = x
    if y is not None: a["y"] = y
    t = op("create_text", a)
    fills(t, color)
    return t

def frame(parent, name, x=None, y=None, w=None, h=None):
    a = {"name": name}
    if parent is not None: a["parentId"] = "$ref:%d" % parent
    if x is not None: a["x"] = x
    if y is not None: a["y"] = y
    if w is not None: a["width"] = w
    if h is not None: a["height"] = h
    return op("create_frame", a)

def rect(parent, name, x, y, w, h):
    return op("create_rectangle", {"parentId": "$ref:%d" % parent, "name": name, "x": x, "y": y, "width": w, "height": h})

def ellipse(parent, name, x, y, w, h, color):
    e = op("create_ellipse", {"parentId": "$ref:%d" % parent, "name": name, "x": x, "y": y, "width": w, "height": h})
    fills(e, color)
    return e

SH1 = [{"type": "DROP_SHADOW", "visible": True, "radius": 2, "offset": {"x": 0, "y": 1},
        "color": {"r": 0.0863, "g": 0.1412, "b": 0.1098, "a": 0.04}}]
SH2 = [{"type": "DROP_SHADOW", "visible": True, "radius": 12, "offset": {"x": 0, "y": 4},
        "color": {"r": 0.0863, "g": 0.1412, "b": 0.1098, "a": 0.06}}]

# ---------- page ----------
op("navigate_to_page", {"pageId": "16:395"})

# annotation captions (page-level)
op("create_text", {"characters": "A · 森林页：全屏观赏，从抽屉扩展位进入（INTENT-2026-09-23-my-forest 切片0 原型 v0.1）", "x": 0, "y": -36, "fontSize": 13})

# ---------- Frame A: forest page ----------
f1 = frame(None, "森林页 · 全屏 393×852", 0, 0, 393, 852)
fills(f1, "bg")
op("set_auto_layout", {"nodeId": "$ref:%d" % f1, "layoutMode": "VERTICAL", "paddingTop": 24, "paddingBottom": 24,
                       "paddingLeft": 20, "paddingRight": 20, "itemSpacing": 16, "counterAxisAlignItems": "MIN"})

header = frame(f1, "header")
op("set_auto_layout", {"nodeId": "$ref:%d" % header, "layoutMode": "VERTICAL", "itemSpacing": 4})
op("set_layout_props", {"nodeId": "$ref:%d" % header, "layoutSizingHorizontal": "FILL", "layoutSizingVertical": "HUG"})
text(header, "我的森林", 20, "ink")
text(header, "12 棵树 · 连续学习 6 天 · 最近：英语单词", 13, "ink3")

stats = frame(f1, "stats-row")
op("set_auto_layout", {"nodeId": "$ref:%d" % stats, "layoutMode": "HORIZONTAL", "itemSpacing": 8})
op("set_layout_props", {"nodeId": "$ref:%d" % stats, "layoutSizingHorizontal": "FILL", "layoutSizingVertical": "HUG"})

def stat_card(parent, num, label):
    c = frame(parent, "stat-card")
    op("set_auto_layout", {"nodeId": "$ref:%d" % c, "layoutMode": "VERTICAL", "itemSpacing": 2,
                           "paddingTop": 12, "paddingBottom": 12, "paddingLeft": 16, "paddingRight": 16})
    op("set_layout_props", {"nodeId": "$ref:%d" % c, "layoutSizingHorizontal": "FILL", "layoutSizingVertical": "HUG"})
    fills(c, "surface")
    op("set_corner_radius", {"nodeId": "$ref:%d" % c, "radius": 14})
    op("set_effects", {"nodeId": "$ref:%d" % c, "effects": SH1})
    text(c, num, 20, "green")
    text(c, label, 11, "ink3")

stat_card(stats, "12", "主题")
stat_card(stats, "6 天", "连续学习")
stat_card(stats, "48", "累计提问")

canvas = frame(f1, "森林画布")
op("set_auto_layout", {"nodeId": "$ref:%d" % canvas, "layoutMode": "NONE"})
op("set_layout_props", {"nodeId": "$ref:%d" % canvas, "layoutSizingHorizontal": "FILL", "layoutSizingVertical": "FILL"})
fills(canvas, "green-faint")
op("set_corner_radius", {"nodeId": "$ref:%d" % canvas, "radius": 18})

ground = rect(canvas, "ground", 0, 616, 353, 48)
fills(ground, "green-soft")
op("set_corner_radius", {"nodeId": "$ref:%d" % ground, "bottomLeftRadius": 18, "bottomRightRadius": 18})

def tree(cx1, cy1, c1s, cx2, cy2, c2s, tx, ty, th, tw, label, lx, ly, lsize=11, lcolor="ink3"):
    ellipse(canvas, "canopy", cx1, cy1, c1s, c1s, "green" if c1s >= 100 else None or "green")
    ellipse(canvas, "canopy2", cx2, cy2, c2s, c2s, "green-soft")
    trunk = rect(canvas, "trunk", tx, ty, tw, th)
    fills(trunk, "line")
    text(canvas, label, lsize, lcolor, lx, ly)

# T1 大树
ellipse(canvas, "canopy", 24, 350, 104, 104, "green")
ellipse(canvas, "canopy2", 12, 392, 66, 66, "green-soft")
tr = rect(canvas, "trunk", 71, 454, 10, 162); fills(tr, "line")
text(canvas, "线性代数", 11, "ink3", 30, 622)
# T2 大树（最近学习）
ellipse(canvas, "canopy", 142, 310, 124, 124, "green")
ellipse(canvas, "canopy2", 128, 384, 80, 80, "green-soft")
tr = rect(canvas, "trunk", 199, 434, 10, 182); fills(tr, "line")
text(canvas, "英语单词", 11, "ink2", 150, 622)
# T3 中树
ellipse(canvas, "canopy", 262, 352, 84, 84, "green-soft")
ellipse(canvas, "canopy2", 252, 394, 52, 52, "green")
tr = rect(canvas, "trunk", 299, 446, 10, 170); fills(tr, "line")
text(canvas, "项目复盘", 11, "ink3", 268, 622)
# T4 小树
ellipse(canvas, "canopy", 84, 452, 52, 52, "green")
tr = rect(canvas, "trunk", 106, 500, 8, 116); fills(tr, "line")
text(canvas, "育儿笔记", 11, "ink3", 142, 556)
# T5 小树（已归档，同样式）
ellipse(canvas, "canopy", 216, 486, 44, 44, "green-soft")
tr = rect(canvas, "trunk", 234, 526, 8, 90); fills(tr, "line")
text(canvas, "读书摘录 · 已归档", 11, "ink3", 240, 560)
# T6 新芽
ellipse(canvas, "canopy", 120, 560, 26, 26, "green")
st = rect(canvas, "stem", 131, 584, 4, 32); fills(st, "line")
text(canvas, "AI agents", 11, "ink3", 106, 598)

# ---------- Frame B: drawer open state ----------
op("create_text", {"characters": "B · 首页抽屉展开态：「我的森林」= 扩展位入口", "x": 450, "y": -36, "fontSize": 13})

f2 = frame(None, "首页 · 抽屉展开态 393×852", 450, 0, 393, 852)
fills(f2, "surface")
op("create_rectangle", {"parentId": "$ref:%d" % f2, "name": "dim", "x": 0, "y": 0, "width": 393, "height": 852})
# re-fill dim with ink at 35%
dim_idx = len(ops) - 1
op("set_fills", {"nodeId": "$ref:%d" % dim_idx, "fills": paint("ink", 0.35)})

drawer = frame(f2, "抽屉", 0, 0, 280, 852)
fills(drawer, "surface")
op("set_effects", {"nodeId": "$ref:%d" % drawer, "effects": SH2})
op("set_auto_layout", {"nodeId": "$ref:%d" % drawer, "layoutMode": "VERTICAL", "paddingTop": 24, "paddingBottom": 24,
                       "paddingLeft": 16, "paddingRight": 16, "itemSpacing": 12})

text(drawer, "知芽", 20, "ink")
text(drawer, "对话", 11, "ink3")

def drawer_row(label, dot_color, label_color="ink2"):
    r = frame(drawer, "row")
    op("set_auto_layout", {"nodeId": "$ref:%d" % r, "layoutMode": "HORIZONTAL", "itemSpacing": 8,
                           "paddingTop": 10, "paddingBottom": 10, "paddingLeft": 12, "paddingRight": 12,
                           "counterAxisAlignItems": "CENTER"})
    op("set_layout_props", {"nodeId": "$ref:%d" % r, "layoutSizingHorizontal": "FILL", "layoutSizingVertical": "HUG"})
    ellipse(r, "dot", 0, 0, 10, 10, dot_color)
    text(r, label, 15, label_color)
    return r

drawer_row("蒙台梭利敏感期", "blue")
drawer_row("Project Montage 架构", "line")

# forest entry (highlighted)
fr = frame(drawer, "row-forest")
op("set_auto_layout", {"nodeId": "$ref:%d" % fr, "layoutMode": "HORIZONTAL", "itemSpacing": 8,
                       "paddingTop": 10, "paddingBottom": 10, "paddingLeft": 12, "paddingRight": 12,
                       "counterAxisAlignItems": "CENTER"})
op("set_layout_props", {"nodeId": "$ref:%d" % fr, "layoutSizingHorizontal": "FILL", "layoutSizingVertical": "HUG"})
fills(fr, "green-soft")
op("set_corner_radius", {"nodeId": "$ref:%d" % fr, "radius": 8})
ellipse(fr, "mini-tree", 0, 0, 16, 16, "green")
col = frame(fr, "col")
op("set_auto_layout", {"nodeId": "$ref:%d" % col, "layoutMode": "VERTICAL", "itemSpacing": 2})
text(col, "我的森林", 15, "ink")
text(col, "12 棵树 · 连续 6 天", 11, "ink3")

drawer_row("统计详情", "green-faint")
drawer_row("设置", "line")

# ---------- evidence ----------
out_dir = "D:\\\\Project\\\\Android\\\\Treelearn\\\\artifacts\\\\evidence\\\\figwright-forest"
op("save_screenshots", {"nodeIds": ["$ref:%d" % f1, "$ref:%d" % f2], "outDir": out_dir, "scale": 2})

json.dump(ops, open("artifacts/figwright/build_forest.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("ops:", len(ops))
