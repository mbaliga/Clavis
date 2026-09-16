"""Pack scenes + lexicon + pseudowords + page art into the single-file app.

template.html placeholders:  __DATA_B64__ (gzip+base64 JSON)   __IMAGES__ (JS object literal of data URIs)
"""
import json, gzip, base64, io, sys
from PIL import Image

W, Q = 640, 55
scenes = json.load(open('/home/claude/pipeline/scenes.json'))
lex = json.load(open('/home/claude/pipeline/lexicon.json'))
pseudo = json.load(open('/home/claude/pipeline/pseudo.json'))
etym = json.load(open('/home/claude/pipeline/etym.json'))

def compact_tok(t):
    s, l, z = t['s'], t['l'], t['z']
    row = [s, 0 if l == s.lower() else l, z,
           [[b['v'], b['w'], b['k']] for b in t['b'][:2]] if t.get('b') else 0]
    if t.get('r'):
        row.append(t['r'])
    return row

out_scenes = []
for sc in scenes:
    rx, ry, rw, rh = sc['rect']
    rw = min(rw, 1 - rx); rh = min(rh, 1 - ry)
    q = {}
    for l, r in sc.get('rects', {}).items():
        qx, qy, qw, qh = r
        qw = min(qw, 1 - qx); qh = min(qh, 1 - qy)
        q[l] = [round(qx, 3), round(qy, 3), round(qw, 3), round(qh, 3)]
    out_scenes.append({'i': sc['id'], 'e': sc['ep'], 'p': sc['page'], 'm': f"ep{sc['ep']:02d}/{sc['img']}",
                       'r': [rx, ry, round(rw, 4), round(rh, 4)], 'q': q, 'x': sc['texts'],
                       'k': {l: [compact_tok(t) for t in ts] for l, ts in sc['toks'].items()}})
# lexicon: keep lemma -> [count, zipf, surface]
data = {'scenes': out_scenes, 'lex': lex, 'pseudo': pseudo, 'etym': etym}
raw = json.dumps(data, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
gz = gzip.compress(raw, compresslevel=9)
b64 = base64.b64encode(gz).decode('ascii')
print('json', len(raw) // 1024, 'KB -> gz', len(gz) // 1024, 'KB -> b64', len(b64) // 1024, 'KB')

images = {}
pages = sorted({sc['m'] for sc in out_scenes})
sizes = {}
for key in pages:
    im = Image.open('/home/claude/corpus/' + key).convert('RGB')
    h = round(im.height * W / im.width)
    im = im.resize((W, h), Image.LANCZOS)
    b = io.BytesIO(); im.save(b, 'WEBP', quality=Q, method=6)
    images[key] = 'data:image/webp;base64,' + base64.b64encode(b.getvalue()).decode('ascii')
    sizes[key] = [W, h]
img_js = json.dumps(images, separators=(',', ':'))
size_js = json.dumps(sizes, separators=(',', ':'))
print('images', len(pages), 'pages,', len(img_js) // 1024, 'KB base64')

tpl = open('/home/claude/app/template.html', encoding='utf-8').read()
g6 = open('/home/claude/app/vendor-g6.min.js', encoding='utf-8').read()
html = tpl.replace('__DATA_B64__', b64).replace('__IMAGES__', img_js).replace('__SIZES__', size_js)
html = html.replace('__G6_JS__', g6)
open('/home/claude/app/clavis.html', 'w', encoding='utf-8').write(html)
print('wrote clavis.html', len(html.encode('utf-8')) // 1024, 'KB')
