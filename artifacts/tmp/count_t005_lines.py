# -*- coding: utf-8 -*-
import io

def count_code(path, is_xml=False):
    txt = io.open(path, encoding='utf-8').read()
    n = 0
    in_block = False
    for raw in txt.split('\n'):
        s = raw.strip()
        if not s:
            continue
        if is_xml:
            if in_block:
                if '-->' in s: in_block = False
                continue
            if s.startswith('<!--'):
                if '-->' not in s: in_block = True
                continue
            n += 1
        else:
            if in_block:
                if '*/' in s: in_block = False
                continue
            if s.startswith('//'): continue
            if s.startswith('/*'):
                if '*/' not in s: in_block = True
                continue
            n += 1
    return n

root = 'android/app/src/main/'
files = [
    (root+'java/com/treelearn/app/screen/RecommendationTag.java', False),
    (root+'res/drawable/ic_sparkle.xml', True),
    (root+'res/drawable/ic_chevron_right.xml', True),
    (root+'res/layout/item_recommendation.xml', True),
]
total = 0
for p, x in files:
    c = count_code(p, x); total += c
    print('%-5s %3d  %s' % ('XML' if x else 'JAVA', c, p))
print('SUBTOTAL(new/rewritten files): %d' % total)
