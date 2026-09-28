# -*- coding: utf-8 -*-
import io

def count_code(path, is_xml=False):
    txt = io.open(path, encoding='utf-8').read()
    n=0; in_block=False
    for raw in txt.split('\n'):
        s=raw.strip()
        if not s: continue
        if is_xml:
            if in_block:
                if '-->' in s: in_block=False
                continue
            if s.startswith('<!--'):
                if '-->' not in s: in_block=True
                continue
            n+=1
        else:
            if in_block:
                if '*/' in s: in_block=False
                continue
            if s.startswith('//'): continue
            if s.startswith('/*'):
                if '*/' not in s: in_block=True
                continue
            n+=1
    return n

root='android/app/src/main/'
files=[
 (root+'res/layout/item_home_topic.xml', True),
 (root+'res/drawable/bg_topic_card.xml', True),
 (root+'res/drawable/bg_unread_dot.xml', True),
 (root+'res/drawable/ic_tree_branch.xml', True),
]
tot=0
for p,x in files:
    c=count_code(p,x); tot+=c
    print('%-5s %3d  %s' % ('XML' if x else 'JAVA', c, p))
print('SUBTOTAL(new files): %d' % tot)
