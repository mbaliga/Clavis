"""Stage 1 — extract aligned speech-bubble scenes from the Pepper&Carrot translation SVGs.

Input : corpus (framagit or 2017-snapshot layout, see config.py) + languages.json
Output: <work>/scenes_raw.json   aligned bubbles with per-language geometry
        <work>/credits.json      translator credits per language (from lang/<code>/info.json)
        <work>/extract-stats.json

One scene per English bubble that has >= 2 words and exists under the same SVG element id, with
text, in >= 4 of the selected languages. Geometry is decided PER ELEMENT:
  <flowRoot>  -> its <flowRegion><rect> is the balloon's real text box            (src = "flow")
  <text>      -> reconstructed from tspan baselines, font sizes and text-anchor    (src = "text")
so pages that mix both (ep18/24/27/29) are handled naturally. Boxes are captured per language
(Japanese needs a different box from Dutch in the same balloon) and normalised to the page.

The geometry maths is the reference extract.py's, unchanged: per-script mean advance widths
(CJK 0.98 em, Arabic 0.46, everything else 0.5), box top = y0 - fs*1.02, height = (y1-y0) + fs*1.34.
"""
from __future__ import annotations

import argparse
import json
import re
import time
import xml.etree.ElementTree as ET
from collections import Counter
from multiprocessing import Pool
from pathlib import Path

from config import Config, add_common_args, log, warn, write_json

# ----------------------------------------------------------------------------- geometry (reference)

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


CJK_RE = re.compile('[\u3040-\u30FF\u3400-\u9FFF\uAC00-\uD7AF]')
AR_RE2 = re.compile('[\u0600-\u06FF]')


SODIPODI_ROLE = '{http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd}role'


def char_width(s):
    """Mean advance as a fraction of the em, by script."""
    if CJK_RE.search(s): return 0.98
    if AR_RE2.search(s): return 0.46
    return 0.5


def bubbles(path):
    """Return ({element id: {'t': text, 'r': rect, 'src': 'flow'|'text'}}, (W, H)) or (None, error)."""
    try:
        root = ET.parse(path).getroot()
    except Exception as e:  # noqa: BLE001 — any parse failure drops this language for the page
        return None, str(e)
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
            # One line per Inkscape line tspan (sodipodi:role="line"); Inkscape 1.x nests styling tspans
            # inside them, and walking every tspan (as the reference did) counted that text twice.
            cands = [sp for sp in el.iter() if sp.tag.split('}')[-1] == 'tspan' and sp.get(SODIPODI_ROLE) == 'line']
            if not cands:
                cands = [sp for sp in el if sp.tag.split('}')[-1] == 'tspan']
            for sp in cands:
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


# ----------------------------------------------------------------------------- per page

def extract_page(job):
    """Worker: one page across all selected language folders. Returns a plain dict."""
    ep_num, page, png, corpus, folder_iso, svgs = job
    per = {}; sizes = {}; warnings = []
    for folder, svg in svgs:
        b, meta = bubbles(svg)
        if b is None:
            warnings.append(f'ep{ep_num:02d} {page} {folder}: unparseable SVG ({meta[:80]})')
            continue
        per[folder] = b; sizes[folder] = meta
    en_folder = next((f for f, iso in folder_iso if iso == 'en'), 'en')
    scenes = []
    if per.get(en_folder):
        en_mixed = {b['src'] for b in per[en_folder].values()}
        for bid, b in per[en_folder].items():
            if len(b['t'].split()) < 2:
                continue
            texts = {}; rects = {}
            for folder, iso in folder_iso:
                if per.get(folder) and bid in per[folder] and per[folder][bid]['t']:
                    texts[iso] = per[folder][bid]['t']
                    r = norm(per[folder][bid]['r'], *sizes[folder])
                    if r: rects[iso] = r
            if len(texts) < 4:
                continue
            W, H = sizes[en_folder]
            rect = norm(b['r'], W, H)
            if not rect:
                continue
            scenes.append({'ep': ep_num, 'page': page, 'bid': bid, 'img': str(Path(png).relative_to(corpus)),
                           'rect': rect, 'rects': rects, 'src': b['src'], 'texts': texts})
        mixed = len(en_mixed) > 1
    else:
        mixed = False
    return {'ep': ep_num, 'page': page, 'scenes': scenes, 'warnings': warnings,
            'mixed': mixed, 'langs_present': sorted(per)}


# ----------------------------------------------------------------------------- credits

def read_credits(ep, folder):
    """framagit lang/<code>/info.json -> {'translation': [...], 'contribution': [...]} (people as written)."""
    p = ep.info_json(folder)
    if not p:
        return None
    try:
        d = json.load(open(p, encoding='utf-8'))
    except Exception as e:  # noqa: BLE001
        warn(f'ep{ep.num:02d} {folder}/info.json unreadable: {e}')
        return None
    cr = d.get('credits') or {}
    return {k: [str(x) for x in (v or [])] for k, v in cr.items() if isinstance(v, list)}


# ----------------------------------------------------------------------------- main

def run(cfg: Config) -> dict:
    t0 = time.time()
    eps = cfg.episodes()
    folder_iso = [(l.pcFolder, l.iso) for l in cfg.languages]
    log('extract:', cfg.describe())
    log(f'extract: {len(eps)} episodes, layout {Counter(e.layout for e in eps)}')
    jobs = []
    credits: dict[str, dict] = {}
    for ep in eps:
        for folder, iso in folder_iso:
            if ep.has_folder(folder):
                c = read_credits(ep, folder)
                if c:
                    slot = credits.setdefault(iso, {'name': cfg.registry[iso].name, 'roles': {}, 'episodes': []})
                    slot['episodes'].append(ep.num)
                    for role, people in c.items():
                        lst = slot['roles'].setdefault(role, [])
                        for p in people:
                            if p not in lst:
                                lst.append(p)
        for page, png in ep.pages:
            svgs = [(folder, ep.svg_for(folder, page)) for folder, iso in folder_iso]
            svgs = [(f, str(s)) for f, s in svgs if s]
            jobs.append((ep.num, page, str(png), str(cfg.corpus), folder_iso, svgs))
    log(f'extract: {len(jobs)} pages to parse with {cfg.workers} worker(s)')
    if cfg.workers > 1:
        with Pool(cfg.workers) as pool:
            results = list(pool.imap(extract_page, jobs, chunksize=4))
    else:
        results = [extract_page(j) for j in jobs]

    scenes = []; warnings = []; mixed_pages = []
    for r in results:
        scenes.extend(r['scenes']); warnings.extend(r['warnings'])
        if r['mixed']:
            mixed_pages.append(f"ep{r['ep']:02d}/{r['page']}")
    for w in warnings:
        warn(w)
    # reading order: episode, page, then bubble y, x
    scenes.sort(key=lambda s: (s['ep'], s['page'], round(s['rect'][1], 2), s['rect'][0]))
    for i, s in enumerate(scenes):
        s['id'] = i
    write_json(cfg.scenes_raw, scenes)
    write_json(cfg.credits, credits, indent=1)
    per_lang = dict(Counter(l for s in scenes for l in s['texts']))
    stats = {
        'episodes': [e.num for e in eps], 'pages_total': len(jobs),
        'scenes': len(scenes), 'per_language': per_lang,
        'geometry_source': dict(Counter(s['src'] for s in scenes)),
        'pages_used': len({(s['ep'], s['img']) for s in scenes}),
        'mixed_format_pages': mixed_pages, 'warnings': warnings,
        'seconds': round(time.time() - t0, 1),
    }
    write_json(cfg.work / 'extract-stats.json', stats, indent=1)
    log(len(scenes), 'scenes; per-language:', per_lang)
    log('geometry source:', stats['geometry_source'], '| mixed-format pages:', len(mixed_pages))
    log('pages used:', stats['pages_used'], 'of', len(jobs), f'| {stats["seconds"]} s')
    return stats


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_common_args(ap)
    cfg = Config.from_args(ap.parse_args(argv))
    run(cfg)


if __name__ == '__main__':
    main()
