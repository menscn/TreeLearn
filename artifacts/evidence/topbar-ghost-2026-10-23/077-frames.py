"""Presentation crop for slice 077: four frames, same box, stacked vertically.

The crop is the face interior only (device y 130..306, the bar's own bottom edge sits at
~303), so nothing scrolling below the bar can be mistaken for the ghost. Rows 256..284 are
the crossing band measured by 077-bar-ghost.py. The marquee row animates on its own, so a
single before/after frame is not a controlled comparison -- both frames of each version are
shown, and the pair that matters is frame B vs frame B.
"""
from PIL import Image, ImageDraw, ImageFont

EV = "artifacts/evidence/topbar-ghost-2026-10-23"
X0, Y0, X1, Y1 = 60, 130, 1140, 306
SCALE = 1.5
LABEL_H = 26
GAP = 10
FRAMES = [
    ("BEFORE  alpha .93   frame A (at top)", "077-before-93-t0.png"),
    ("BEFORE  alpha .93   frame B (scrolled)", "077-before-93-t1.png"),
    ("AFTER   alpha .98   frame A (at top)", "077-after-98-u0.png"),
    ("AFTER   alpha .98   frame B (scrolled)", "077-after-98-u1.png"),
]

font = ImageFont.load_default()
w = int((X1 - X0) * SCALE)
panels = []
for label, name in FRAMES:
    crop = Image.open(f"{EV}/{name}").convert("RGB").crop((X0, Y0, X1, Y1))
    crop = crop.resize((w, int(crop.height * SCALE)), Image.LANCZOS)
    head = Image.new("RGB", (w, LABEL_H), (245, 243, 238))
    d = ImageDraw.Draw(head)
    d.text((8, 7), label, fill=(34, 48, 39), font=font)
    strip = Image.new("RGB", (w, LABEL_H + crop.height), (0, 0, 0))
    strip.paste(head, (0, 0))
    strip.paste(crop, (0, LABEL_H))
    panels.append(strip)

h = sum(p.height for p in panels) + GAP * (len(panels) - 1)
out = Image.new("RGB", (w, h), (255, 255, 255))
y = 0
for p in panels:
    out.paste(p, (0, y))
    y += p.height + GAP
out.save(f"{EV}/077-bar-frames.png")
print(f"WROTE {EV}/077-bar-frames.png size={out.size} box=({X0},{Y0})-({X1},{Y1})")
