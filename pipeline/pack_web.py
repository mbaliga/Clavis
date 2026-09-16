"""Stage 5 — rebuild the reference single-file web app (clavis.html) from the SAME packs dir.

    python pack_web.py --packs DIR --out build/clavis.html [--languages core] [--template ...] [--g6 ...]

Reads <packs>/comics/scenes.json.gz, comics/pages.json + comics/pages/*.webp, lexicon/lexicon.json.gz,
lexicon/pseudo.json and, when the enrichment stage produced it, etymology/etym.json.gz; fills the
placeholders of reference-prototype/template.html (__DATA_B64__ gzip+base64 JSON, __IMAGES__ data
URIs, __SIZES__, __G6_JS__) exactly as the reference pack.py did, and also writes the standalone
wrapper (clavis-standalone.html, docs/09-handoff.md §2) used by the smoke test.

The template is FROZEN at nine lanes + English (D-1: "the template is frozen at v0.10 behaviour"):
its LANGS/LANE_ORDER/DEFAULT_MODE/LOCALE tables are hardcoded, so the web build is filtered to
those ten languages (--languages core, the default) whatever the packs contain. Scenes are kept
when >= --min-languages (default 4, the extractor's gate) of the ten remain, and re-numbered so
that scene.i == array index, which the app's indices rely on.
"""
from __future__ import annotations

import argparse
import base64
import gzip
import json
import time
from pathlib import Path

from config import PIPE, REPO, Registry, log, read_json, write_json

TEMPLATE = REPO / 'reference-prototype' / 'template.html'
G6 = REPO / 'reference-prototype' / 'vendor-g6.min.js'


def read_gz_json(path: Path):
    with gzip.open(path, 'rt', encoding='utf-8') as f:
        return json.load(f)


def wrap_standalone(html: str) -> str:
    return ('<!doctype html><html><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>Clavis</title></head><body style="margin:0">' + html + '</body></html>')


def run(packs: Path, out: Path, langs: list[str], template: Path, g6: Path, min_languages: int = 4) -> dict:
    t0 = time.time()
    allowed = set(langs)
    scenes = read_gz_json(packs / 'comics' / 'scenes.json.gz')
    lex = read_gz_json(packs / 'lexicon' / 'lexicon.json.gz')
    pseudo = read_json(packs / 'lexicon' / 'pseudo.json')
    etym_path = packs / 'etymology' / 'etym.json.gz'
    etym = read_gz_json(etym_path) if etym_path.exists() else {}
    pages_idx = read_json(packs / 'comics' / 'pages.json')

    kept = []
    for sc in scenes:
        x = {l: t for l, t in sc['x'].items() if l in allowed}
        if 'en' not in x or len(x) < min_languages:
            continue
        kept.append({'i': len(kept), 'e': sc['e'], 'p': sc['p'], 'm': sc['m'], 'r': sc['r'],
                     'q': {l: r for l, r in sc['q'].items() if l in allowed}, 'x': x,
                     'k': {l: ts for l, ts in sc['k'].items() if l in allowed}})
    data = {'scenes': kept,
            'lex': {l: d for l, d in lex.items() if l in allowed},
            'pseudo': {l: v for l, v in pseudo.items() if l in allowed},
            'etym': {l: d for l, d in etym.items() if l in allowed}}
    raw = json.dumps(data, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    gz = gzip.compress(raw, compresslevel=9)
    b64 = base64.b64encode(gz).decode('ascii')
    log(f'pack_web: {len(kept)} scenes of {len(scenes)} for {sorted(allowed)}; json {len(raw) // 1024} KB -> gz {len(gz) // 1024} KB -> b64 {len(b64) // 1024} KB'
        + ('' if etym else ' (no etymology pack present: etym = {})'))

    images, sizes = {}, {}
    for key in sorted({sc['m'] for sc in kept}):
        w, h, rel = pages_idx[key]
        images[key] = 'data:image/webp;base64,' + base64.b64encode((packs / 'comics' / rel).read_bytes()).decode('ascii')
        sizes[key] = [w, h]
    img_js = json.dumps(images, separators=(',', ':'))
    size_js = json.dumps(sizes, separators=(',', ':'))
    log(f'pack_web: images {len(images)} pages, {len(img_js) // 1024} KB base64')

    tpl = template.read_text(encoding='utf-8')
    g6js = g6.read_text(encoding='utf-8')
    for ph in ('__DATA_B64__', '__IMAGES__', '__SIZES__', '__G6_JS__'):
        if ph not in tpl:
            raise SystemExit(f'template {template} has no {ph} placeholder')
    html = tpl.replace('__DATA_B64__', b64).replace('__IMAGES__', img_js).replace('__SIZES__', size_js).replace('__G6_JS__', g6js)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(html, encoding='utf-8')
    standalone = out.with_name(out.stem + '-standalone' + out.suffix)
    standalone.write_text(wrap_standalone(html), encoding='utf-8')
    stats = {'scenes': len(kept), 'scenes_in_pack': len(scenes), 'languages': sorted(allowed), 'pages': len(images),
             'json_bytes': len(raw), 'gz_bytes': len(gz), 'b64_bytes': len(b64), 'images_b64_bytes': len(img_js),
             'html_bytes': out.stat().st_size, 'standalone_bytes': standalone.stat().st_size,
             'lex': {l: len(d) for l, d in data['lex'].items()}, 'etym': {l: len(d) for l, d in data['etym'].items()},
             'pseudo': {l: len(v) for l, v in data['pseudo'].items()}, 'seconds': round(time.time() - t0, 1)}
    write_json(out.with_name('pack_web-stats.json'), stats, indent=1)
    log(f'pack_web: wrote {out} {stats["html_bytes"] // 1024} KB and {standalone.name} | {stats["seconds"]} s')
    return stats


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--packs', type=Path, required=True)
    ap.add_argument('--out', type=Path, default=PIPE / 'build' / 'clavis.html')
    ap.add_argument('--languages', default='core', help="'core' (frozen ten) or a comma list; the template only knows the ten")
    ap.add_argument('--registry', type=Path, default=PIPE / 'languages.json')
    ap.add_argument('--template', type=Path, default=TEMPLATE)
    ap.add_argument('--g6', type=Path, default=G6)
    ap.add_argument('--min-languages', type=int, default=4)
    a = ap.parse_args(argv)
    reg = Registry(a.registry)
    langs = [l.iso for l in reg.select(a.languages)]
    run(a.packs, a.out, langs, a.template, a.g6, a.min_languages)


if __name__ == '__main__':
    main()
