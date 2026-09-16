"""Stage 4 — build the content packs the KMP app ships (D-7).

Reads <work>/{scenes,lexicon,pseudo,credits}.json + languages.json + page art  ->  <packs>/ :

    manifest.json                 JSON array of PackManifest objects (core/Packs.kt)
    comics/scenes.json.gz         compact scenes [{i,e,p,m,r,q,x,k}]; m = flat page key "E05P03"
    comics/pages.json             {"E05P03": [w, h, "pages/E05P03.webp"]}
    comics/pages/E05P03.webp      page art, 640 px wide, WebP quality 55, method 6
    lexicon/lexicon.json.gz       {iso: {lemma: [count, zipf, surface, reading]}}
    lexicon/pseudo.json           {iso: [foil, ...]}
    languages.json                registry copy
    ATTRIBUTION.md                Pepper&Carrot author + translators, wordfreq, tokenisers, and the
                                  enrichment sources (Wiktionary / EtymDB / Tatoeba / Commons)
    etymology/ sentences/ pronunciations/   produced by the enrichment stage (enrich_*.py); when
                                  present their manifest-fragment.json files are merged into manifest.json

Token tuple (unchanged from the web build): [surface, lemma|0, zipf, bridges|0, reading?] where 0 in
slot 1 means lemma == surface.lower() and slot 3 holds at most two [lang, word, similarity] bridges.
Scene text `x` for languages that are not space-separated (ja, zh) has the pipeline-introduced
spaces between two CJK characters removed (the web template did this at display time).

--install copies the finished packs dir into the app's compose resources.
"""
from __future__ import annotations

import argparse
import datetime as dt
import gzip
import io
import json
import re
import shutil
import time
from collections import Counter
from pathlib import Path

from PIL import Image

from config import REPO, Config, add_common_args, log, read_json, warn, write_json

W, Q, METHOD = 640, 55, 6
INSTALL_DIR = REPO / 'composeApp' / 'src' / 'commonMain' / 'composeResources' / 'files' / 'packs'
ENRICH_DIRS = ('etymology', 'sentences', 'pronunciations')
CJK = '[\u3040-\u30FF\u3400-\u9FFF\uAC00-\uD7AF]'
JOIN_CJK = re.compile(f'({CJK}) (?={CJK})')
PC_URL = 'https://www.peppercarrot.com'
PC_SRC = 'https://framagit.org/peppercarrot/webcomics'


def joincjk(s: str) -> str:
    return JOIN_CJK.sub(r'\1', s)


def compact_tok(t):
    s, l, z = t['s'], t['l'], t['z']
    row = [s, 0 if l == s.lower() else l, z,
           [[b['v'], b['w'], b['k']] for b in t['b'][:2]] if t.get('b') else 0]
    if t.get('r'):
        row.append(t['r'])
    return row


def gz_json(path: Path, obj) -> int:
    raw = json.dumps(obj, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + '.tmp')
    with open(tmp, 'wb') as f:
        f.write(gzip.compress(raw, compresslevel=9, mtime=0))
    tmp.replace(path)
    return path.stat().st_size


def build_scenes(scenes, space_sep: dict[str, bool]):
    out = []
    for sc in scenes:
        rx, ry, rw, rh = sc['rect']
        rw = min(rw, 1 - rx); rh = min(rh, 1 - ry)
        q = {}
        for l, r in sc.get('rects', {}).items():
            qx, qy, qw, qh = r
            qw = min(qw, 1 - qx); qh = min(qh, 1 - qy)
            q[l] = [round(qx, 3), round(qy, 3), round(qw, 3), round(qh, 3)]
        out.append({'i': sc['id'], 'e': sc['ep'], 'p': sc['page'], 'm': sc['page'],
                    'r': [rx, ry, round(rw, 4), round(rh, 4)], 'q': q,
                    'x': {l: (t if space_sep.get(l, True) else joincjk(t)) for l, t in sc['texts'].items()},
                    'k': {l: [compact_tok(t) for t in ts] for l, ts in sc['toks'].items()}})
    return out


def encode_page(src: Path, dst: Path) -> tuple[int, int]:
    """Resize to W px wide and write WebP; skipped when the output is newer than the input (idempotent)."""
    if dst.exists() and dst.stat().st_mtime >= src.stat().st_mtime:
        with Image.open(dst) as im:
            return im.size
    im = Image.open(src).convert('RGB')
    h = round(im.height * W / im.width)
    im = im.resize((W, h), Image.LANCZOS)
    dst.parent.mkdir(parents=True, exist_ok=True)
    b = io.BytesIO(); im.save(b, 'WEBP', quality=Q, method=METHOD)
    tmp = dst.with_suffix('.tmp')
    tmp.write_bytes(b.getvalue()); tmp.replace(dst)
    return W, h


def person(s: str) -> tuple[str, str | None]:
    """'Name <url>' -> (Name, url)."""
    m = re.match(r'\s*(.*?)\s*<([^>]+)>\s*$', s)
    return (m.group(1), m.group(2)) if m else (s.strip(), None)


def load_fragments(packs: Path) -> tuple[list[dict], dict[str, str]]:
    """Enrichment manifest fragments -> (manifest objects, {section title: markdown}) ."""
    manifests, sections = [], {}
    for d in ENRICH_DIRS:
        frag = packs / d / 'manifest-fragment.json'
        if not frag.exists():
            continue
        try:
            data = read_json(frag)
        except Exception as e:  # noqa: BLE001
            warn(f'pack_assets: cannot read {frag}: {e}'); continue
        items = data if isinstance(data, list) else [data]
        for m in items:
            if not isinstance(m, dict):
                continue
            m = dict(m)
            md = m.pop('attributionMarkdown', None) or m.pop('attribution_md', None)
            if md:
                sections[m.get('title') or d] = md
            if 'files' not in m or not m['files']:
                m['files'] = {str(p.relative_to(packs)): p.stat().st_size for p in sorted((packs / d).rglob('*'))
                              if p.is_file() and p.name != 'manifest-fragment.json'}
            # fragments name files relative to their own dir; manifest.json paths are relative to the packs root
            m['files'] = {(k if k.startswith(d + '/') else f'{d}/{k}'): v for k, v in m['files'].items()}
            manifests.append(m)
        log(f'pack_assets: merged manifest fragment {frag.relative_to(packs)} ({len(items)} pack(s))')
    return manifests, sections


def attribution_md(cfg: Config, credits: dict, manifests: list[dict], sections: dict[str, str], stats: dict) -> str:
    lines = ['# Attribution', '',
             'This content pack is distributed separately from the Clavis application code. Every source below keeps its own licence; ',
             'the pack as a whole is CC BY-SA 4.0 because it contains share-alike data (wordfreq-derived frequencies, Wiktionary/EtymDB).', '',
             '## Pepper&Carrot (comics pack) — CC BY 4.0', '',
             f'*Pepper&Carrot* is a webcomic by **David Revoy** ({PC_URL}), licensed under the Creative Commons Attribution 4.0 International licence ',
             f'(https://creativecommons.org/licenses/by/4.0/). Page art and dialogue come from {PC_SRC}. ',
             f'The pack contains {stats["scenes"]} balloons from {stats["pages"]} pages of {len(stats["episodes"])} episodes; ',
             'the balloon text has been re-lettered and tokenised by the Clavis pipeline; no page was modified other than being scaled to 640 px.', '',
             '### Translators', '',
             'Per language, as credited in each episode\'s `lang/<code>/info.json` (translation and contribution roles):', '']
    for L in cfg.languages:
        c = credits.get(L.iso)
        if not c:
            continue
        people = []
        for role, lst in c.get('roles', {}).items():
            for p in lst:
                name, url = person(p)
                if not name:
                    continue  # a blank credit is not a person
                people.append(f'{name} ({url})' if url else name)
        eps = c.get('episodes', [])
        lines.append(f'- **{L.name}** ({L.iso}; {len(eps)} episode{"s" if len(eps) != 1 else ""}): ' + ', '.join(dict.fromkeys(people)))
    lines += ['', '## Word frequencies (lexicon pack) — CC BY-SA 4.0', '',
              'Zipf frequencies are derived from **wordfreq** by Robyn Speer (https://github.com/rspeer/wordfreq), whose data is licensed CC BY-SA 4.0 ',
              'and aggregates sources including OpenSubtitles, Wikipedia, Google Books Ngrams and Common Crawl (see the wordfreq README for the full list). ',
              'Languages without a wordfreq list (Latin) carry a corpus-derived Zipf-like value, marked `freqSource: "corpus"` in `languages.json`.', '',
              '## Tokenisers and lemmatisers (build-time tools; their dictionaries are not redistributed)', '',
              '- simplemma (MIT) — lemmatisation for Latin/Cyrillic/Devanagari-script languages',
              '- fugashi + unidic-lite (MIT / BSD) — Japanese segmentation and readings',
              '- mecab-python3 + mecab-ko-dic (BSD / Apache-2.0), via wordfreq — Korean segmentation',
              '- jieba (MIT), via wordfreq — Chinese segmentation; OpenCC (Apache-2.0) for Simplified→Traditional; pypinyin (MIT) for pīnyīn',
              '- indic-transliteration (MIT) — Devanagari → IAST readings', '']
    # enrichment packs: each fragment manifest once, with every attribution line it carries
    lines += ['## Enrichment packs (Wiktionary · EtymDB · Tatoeba · Wikimedia Commons)', '']
    if manifests:
        lines += ['Optional packs produced by the enrichment stage (`ENRICHMENT.md`); each keeps its own licence:', '']
        for m in manifests:
            lines.append(f'### {m.get("title", m.get("id"))} — {m.get("licence")}')
            lines.append('')
            lines.append(f'Pack `{m.get("id")}` ({m.get("kind")}, version {m.get("version")}), languages {", ".join(m.get("languages", []))}. Source: {m.get("source")}.')
            for a in m.get('attribution', []) or []:
                lines.append(f'- {a.get("name")} — {a.get("role")}' + (f' — {a.get("url")}' if a.get('url') else '') + f' — {a.get("licence")}')
            extra = [f for f in m.get('files', {}) if f.upper().startswith(m.get('kind', '').upper()[:0]) and 'ATTRIBUTION' in f.upper()]
            for f in extra:
                lines.append(f'- Per-item attribution (authors, URLs, per-clip licences) is in `{f}` inside the pack.')
            lines.append('')
        for title, md in sections.items():
            lines += [f'### {title}', '', md.strip(), '']
    blob = json.dumps(manifests, ensure_ascii=False).lower()
    missing = [t for t in ('Wiktionary', 'EtymDB', 'Tatoeba', 'Wikimedia Commons') if t.split()[0].lower() not in blob]
    if missing:
        lines += [f'_Not included in this build (the enrichment stage did not run or produced no data for it): {", ".join(missing)}._', '']
    lines += ['## Generator', '', f'Built {stats["generatedAt"]} by the Clavis pipeline (`pipeline/`, Apache-2.0) from corpus `{cfg.corpus.name}`.', '']
    return '\n'.join(lines)


def run(cfg: Config, version: str | None = None, install: bool = False) -> dict:
    t0 = time.time()
    packs = cfg.packs
    packs.mkdir(parents=True, exist_ok=True)
    scenes = read_json(cfg.scenes)
    lex = read_json(cfg.lexicon)
    pseudo = read_json(cfg.pseudo) if cfg.pseudo.exists() else {}
    credits = read_json(cfg.credits) if cfg.credits.exists() else {}
    reg = cfg.registry
    isos = [l.iso for l in cfg.languages]
    space_sep = {l.iso: l.spaceSeparated for l in reg.languages}
    generated_at = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace('+00:00', 'Z')
    version = version or dt.date.today().strftime('%Y.%m.%d')
    log('pack_assets:', cfg.describe())

    files: dict[str, int] = {}
    # ---- comics
    out_scenes = build_scenes(scenes, space_sep)
    files['comics/scenes.json.gz'] = gz_json(packs / 'comics' / 'scenes.json.gz', out_scenes)
    page_src = {}
    for sc in scenes:
        page_src.setdefault(sc['page'], cfg.corpus / sc['img'])
    pages = {}
    for key in sorted(page_src):
        dst = packs / 'comics' / 'pages' / f'{key}.webp'
        w, h = encode_page(page_src[key], dst)
        pages[key] = [w, h, f'pages/{key}.webp']
        files[f'comics/pages/{key}.webp'] = dst.stat().st_size
    # remove page files that no longer belong to the build (idempotent output dir)
    for stale in (packs / 'comics' / 'pages').glob('*.webp'):
        if stale.stem not in pages:
            stale.unlink()
    files['comics/pages.json'] = write_json(packs / 'comics' / 'pages.json', pages).stat().st_size
    log(f'pack_assets: {len(out_scenes)} scenes, {len(pages)} pages -> comics/ ({sum(v for k, v in files.items() if k.startswith("comics/")) / 1e6:.2f} MB)')

    # ---- lexicon
    files['lexicon/lexicon.json.gz'] = gz_json(packs / 'lexicon' / 'lexicon.json.gz', lex)
    files['lexicon/pseudo.json'] = write_json(packs / 'lexicon' / 'pseudo.json', pseudo).stat().st_size

    # ---- registry
    files['languages.json'] = write_json(packs / 'languages.json', reg.to_pack_json(), indent=1).stat().st_size

    # ---- stats
    hyg = read_json(cfg.hygiene_report) if cfg.hygiene_report.exists() else {'languages': {}}
    per_scene = Counter(l for s in scenes for l in s['texts'])
    per_pages = {l: len({s['page'] for s in scenes if l in s['texts']}) for l in isos}
    rect_ok = lambda r: bool(r) and 0.045 <= r[2] <= 0.70 and 0.010 <= r[3] <= 0.45
    comics_stats, lex_stats = {}, {}
    for l in isos:
        have = [s for s in scenes if l in s['texts']]
        if not have:
            continue
        comics_stats[l] = {'scenes': len(have), 'pages': per_pages[l],
                           'episodes': len({s['ep'] for s in have}),
                           'credibleRectPct': round(100 * sum(rect_ok(s['rects'].get(l)) for s in have) / len(have))}
        lex_stats[l] = {'lemmas': len(lex.get(l, {})), 'tokens': sum(v[0] for v in lex.get(l, {}).values()),
                        'pseudo': len(pseudo.get(l, [])), 'hygieneRemoved': hyg['languages'].get(l, {}).get('removed', 0)}
    episodes = sorted({s['ep'] for s in scenes})
    stats = {'scenes': len(scenes), 'pages': len(pages), 'episodes': episodes, 'generatedAt': generated_at}

    # ---- attribution entries
    attribution = [{'name': 'David Revoy', 'role': 'author of Pepper&Carrot (art and story)', 'url': PC_URL, 'licence': 'CC-BY-4.0'}]
    for L in cfg.languages:
        c = credits.get(L.iso)
        if not c: continue
        for role, lst in c.get('roles', {}).items():
            for p in lst:
                name, url = person(p)
                if not name:
                    continue
                attribution.append({'name': name, 'role': f'{role} ({L.name})', 'url': url, 'licence': 'CC-BY-4.0'})
    comics_files = {k: v for k, v in files.items() if k.startswith('comics/')} | {'languages.json': files['languages.json']}
    manifests = [{
        'id': 'clavis-comics', 'kind': 'COMICS', 'version': version, 'title': 'Pepper&Carrot scenes',
        'languages': [l for l in isos if l in comics_stats], 'licence': 'CC-BY-4.0', 'source': PC_SRC,
        'generatedAt': generated_at, 'attribution': attribution, 'files': comics_files, 'stats': comics_stats,
    }, {
        'id': 'clavis-lexicon', 'kind': 'LEXICON', 'version': version, 'title': 'Corpus lexicon and frequencies',
        'languages': [l for l in isos if l in lex_stats], 'licence': 'CC-BY-SA-4.0',
        'source': 'wordfreq 3.1.1 (CC BY-SA 4.0) + Pepper&Carrot corpus counts',
        'generatedAt': generated_at,
        'attribution': [{'name': 'Robyn Speer', 'role': 'wordfreq word frequencies', 'url': 'https://github.com/rspeer/wordfreq', 'licence': 'CC-BY-SA-4.0'},
                        {'name': 'Adrien Barbaresi', 'role': 'simplemma lemmatiser', 'url': 'https://github.com/adbar/simplemma', 'licence': 'MIT'},
                        {'name': 'NINJAL', 'role': 'UniDic (unidic-lite) Japanese dictionary', 'url': 'https://github.com/polm/unidic-lite', 'licence': 'BSD-3-Clause'},
                        {'name': 'Eunjeon project', 'role': 'mecab-ko-dic Korean dictionary', 'url': 'https://bitbucket.org/eunjeon/mecab-ko-dic', 'licence': 'Apache-2.0'},
                        {'name': 'Sun Junyi', 'role': 'jieba Chinese segmenter', 'url': 'https://github.com/fxsjy/jieba', 'licence': 'MIT'}],
        'files': {k: v for k, v in files.items() if k.startswith('lexicon/')}, 'stats': lex_stats,
    }]
    frag_manifests, sections = load_fragments(packs)
    manifests += frag_manifests

    # ---- attribution + manifest
    md = attribution_md(cfg, credits, frag_manifests, sections, stats)
    (packs / 'ATTRIBUTION.md').write_text(md, encoding='utf-8')
    manifests[0]['files']['ATTRIBUTION.md'] = (packs / 'ATTRIBUTION.md').stat().st_size
    write_json(packs / 'manifest.json', manifests, indent=1)
    total = sum(p.stat().st_size for p in packs.rglob('*') if p.is_file())
    log(f'pack_assets: {len(manifests)} manifests; pack total {total / 1e6:.2f} MB at {packs} | {time.time() - t0:.1f} s')

    if install:
        INSTALL_DIR.parent.mkdir(parents=True, exist_ok=True)
        if INSTALL_DIR.exists():
            shutil.rmtree(INSTALL_DIR)
        shutil.copytree(packs, INSTALL_DIR)
        log(f'pack_assets: installed into {INSTALL_DIR}')
    return {'dir': str(packs), 'total_bytes': total, 'files': sorted(files), 'manifests': [m['id'] for m in manifests],
            'seconds': round(time.time() - t0, 1)}


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_common_args(ap, episodes=False)
    ap.add_argument('--version', default=None, help='pack version string (default: today, YYYY.MM.DD)')
    ap.add_argument('--install', action='store_true', help=f'copy the packs dir into {INSTALL_DIR}')
    a = ap.parse_args(argv)
    run(Config.from_args(a), version=a.version, install=a.install)


if __name__ == '__main__':
    main()
