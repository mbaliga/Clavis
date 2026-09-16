"""Extract aligned speech-bubble scenes from Pepper&Carrot translation repos.

Input : /home/claude/corpus/epNN/{lang}/Pepper-and-Carrot_by-David-Revoy_ENNPMM.svg
        /home/claude/corpus/epNN/gfx_Pepper-and-Carrot_by-David-Revoy_ENNPMM.png
Output: /home/claude/pipeline/scenes_raw.json

Geometry notes
--------------
Episodes 01-12 letter their balloons with Inkscape <flowRoot>, which carries a
<flowRegion><rect> — the balloon's real text box. Episodes 13-23 were re-lettered
with plain <text> + per-line <tspan>, which has no box; there we reconstruct one
from the line baselines, the per-line font-size and the text-anchor, which is
accurate enough to letter into. Boxes are captured PER LANGUAGE (Japanese needs a
different box from Dutch in the same balloon), normalised to the page.
"""
import xml.etree.ElementTree as ET, re, glob, os, json, sys

ROOT = '/home/claude/corpus'
LANGS = ['en', 'es', 'de', 'fr', 'it', 'nl', 'ja', 'kr', 'cn', 'ar']   # folder codes
CODE = {'kr': 'ko', 'cn': 'zh'}                                          # -> ISO

def parse_transform(t):
    """Return (tx, ty, sx, sy) for translate/matrix/scale transforms (no rotation support)."""
    if not t:
        return 0.0, 0.0, 1.0, 1.0
    tx = ty = 0.0; sx = sy = 1.0
    for m in re.finditer(r'(translate|matrix|scale)\(([^)]*)\)', t):
        kind, args = m.group(1), [float(a) for a in re.split(r'[,\s]+', m.group(2).strip()) if a]
        if kind == 'translate':
            tx += args[0]; ty += (args[1] if len(args) > 1 else 0.0)
        elif kind == 'scale':
            sx *= args[0]; sy *= (args[1] if len(args) > 1 else args[0])
        elif kind == 'matrix' and len(args) == 6:
            sx *= args[0]; sy *= args[3]; tx += args[4]; ty += args[5]
    return tx, ty, sx, sy

def svg_size(root):
    w = root.get('width'); h = root.get('height'); vb = root.get('viewBox')
    def num(v):
        return float(re.sub(r'[a-z%]+$', '', v)) if v else None
    if vb:
        parts = [float(x) for x in re.split(r'[,\s]+', vb.strip())]
        return parts[2], parts[3]
    return num(w), num(h)

def style_get(style, key):
    if not style: return None
    m = re.search(key + r'\s*:\s*([^;]+)', style)
    return m.group(1).strip() if m else None

def font_size(style, dflt):
    v = style_get(style, 'font-size')
    if v:
        m = re.match(r'([\d.]+)', v)
        if m: return float(m.group(1))
    return dflt

CJK_RE = re.compile(r'[぀-ヿ㐀-鿿가-힯]')
AR_RE2 = re.compile(r'[؀-ۿ]')
def char_width(s):
    """Mean advance as a fraction of the em, by script."""
    if CJK_RE.search(s): return 0.98
    if AR_RE2.search(s): return 0.46
    return 0.5

def bubbles(path):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return None, None
    W, H = svg_size(root)
    parent = {c: p for p in root.iter() for c in p}
    out = {}
    for el in root.iter():
        tag = el.tag.split('}')[-1]
        if tag not in ('flowRoot', 'text'):
            continue
        # accumulate ancestor transforms
        tx = ty = 0.0; sx = sy = 1.0
        chain = []
        e = el
        while e is not None:
            chain.append(e)
            e = parent.get(e)
        for e in reversed(chain):
            a, b, c, d = parse_transform(e.get('transform'))
            tx = tx * c + a; ty = ty * d + b; sx *= c; sy *= d
        style = el.get('style') or ''
        rect = None
        if tag == 'flowRoot':
            paras = [''.join(p.itertext()) for p in el.iter() if p.tag.split('}')[-1] == 'flowPara']
            text = ' '.join(' '.join(paras).split())
            for ch in el:
                if ch.tag.split('}')[-1] == 'flowRegion':
                    for r in ch:
                        if r.tag.split('}')[-1] == 'rect':
                            rtx, rty, rsx, rsy = parse_transform(r.get('transform'))
                            rect = (float(r.get('x')) * sx * rsx + tx + rtx * sx,
                                    float(r.get('y')) * sy * rsy + ty + rty * sy,
                                    float(r.get('width')) * sx * rsx,
                                    float(r.get('height')) * sy * rsy)
            src = 'flow'
        else:
            base_fs = font_size(style, 40.0)
            anchor = style_get(style, 'text-anchor') or style_get(style, 'text-align') or 'start'
            lines = []
            for sp in el.iter():
                if sp.tag.split('}')[-1] != 'tspan':
                    continue
                t = ''.join(sp.itertext()).strip()
                if not t:
                    continue
                f = font_size(sp.get('style') or '', base_fs)
                try:
                    x = float((sp.get('x') or el.get('x') or '0').split()[0])
                    y = float((sp.get('y') or el.get('y') or '0').split()[0])
                except ValueError:
                    continue
                lines.append((x, y, f, t))
            if not lines:
                t = ' '.join(''.join(el.itertext()).split())
                if not t:
                    continue
                try:
                    x = float((el.get('x') or '0').split()[0]); y = float((el.get('y') or '0').split()[0])
                except ValueError:
                    continue
                lines = [(x, y, base_fs, t)]
            text = ' '.join(' '.join(l[3] for l in lines).split())
            fs = max(l[2] for l in lines)
            y0 = min(l[1] for l in lines); y1 = max(l[1] for l in lines)
            wid = max(len(l[3]) * l[2] * char_width(l[3]) for l in lines)
            xs = [l[0] for l in lines]
            if anchor in ('middle', 'center'): left = min(xs) - wid / 2
            elif anchor == 'end':              left = min(xs) - wid
            else:                              left = min(xs)
            rect = (left * sx + tx, (y0 - fs * 1.02) * sy + ty, wid * sx, ((y1 - y0) + fs * 1.34) * sy)
            src = 'text'
        if text:
            out[el.get('id')] = {'t': text, 'r': rect, 'src': src}
    return out, (W, H)

def norm(rect, W, H):
    if not rect or not W or not H: return None
    return [round(rect[0] / W, 4), round(rect[1] / H, 4), round(rect[2] / W, 4), round(rect[3] / H, 4)]

scenes = []
episodes = sorted(d for d in os.listdir(ROOT) if re.match(r'ep\d\d$', d))
for ep in episodes:
    pages = sorted(glob.glob(f'{ROOT}/{ep}/gfx_*P[0-9][0-9].png'))
    for pg in pages:
        pnum = re.search(r'(E\d+P\d+)', pg).group(1)
        per = {}; sizes = {}
        for l in LANGS:
            fs = glob.glob(f'{ROOT}/{ep}/{l}/*{pnum}.svg')
            if fs:
                per[l], sizes[l] = bubbles(fs[0])
        if not per.get('en'):
            continue
        for bid, b in per['en'].items():
            if len(b['t'].split()) < 2:
                continue
            texts = {}; rects = {}
            for l in LANGS:
                if per.get(l) and bid in per[l] and per[l][bid]['t']:
                    iso = CODE.get(l, l)
                    texts[iso] = per[l][bid]['t']
                    r = norm(per[l][bid]['r'], *sizes[l])
                    if r: rects[iso] = r
            if len(texts) < 4:
                continue
            W, H = sizes['en']
            rect = norm(b['r'], W, H)
            if not rect:
                continue
            scenes.append({'ep': int(ep[2:]), 'page': pnum, 'bid': bid, 'img': os.path.basename(pg),
                           'rect': rect, 'rects': rects, 'src': b['src'], 'texts': texts})

# reading order: episode, page, then bubble y, x
scenes.sort(key=lambda s: (s['ep'], s['page'], round(s['rect'][1], 2), s['rect'][0]))
for i, s in enumerate(scenes):
    s['id'] = i
json.dump(scenes, open('/home/claude/pipeline/scenes_raw.json', 'w'), ensure_ascii=False)
from collections import Counter
c = Counter(l for s in scenes for l in s['texts'])
print(len(scenes), 'scenes;', 'per-language:', dict(c))
print('geometry source:', dict(Counter(s['src'] for s in scenes)))
print('pages used:', len({(s['ep'], s['img']) for s in scenes}))
