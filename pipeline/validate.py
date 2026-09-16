"""Stage 6 — validation statistics and golden checks.

    python validate.py --work DIR [--packs DIR] [--check golden.json] [--write-golden golden.json]

Reads the stage intermediates in <work> (scenes_raw.json, scenes.json, lexicon.json, pseudo.json,
hygiene-report.json) and, when present, the pack in <packs>; prints a per-language table
(scenes, pages, lemmas before/after hygiene, credible-rect %, pseudo count, tokenizer, frequency
source) and writes <work>/validate.json.

--check compares against a golden JSON (same shape as validate.json plus a "tolerance" block) and
exits 1 on drift. Integer counts must match exactly unless the golden file gives a per-key
relative tolerance; percentages use the absolute point tolerance.

Credible rect = the web build's rectOK gate: 0.045 <= w <= 0.70 and 0.010 <= h <= 0.45, evaluated on
the language's own box (scenes carrying that language).
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter
from pathlib import Path

from config import Config, add_common_args, log, read_json, write_json


def rect_ok(r):
    if not r: return False
    x, y, w, h = r
    return 0.045 <= w <= 0.70 and 0.010 <= h <= 0.45


def collect(cfg: Config, packs: Path | None) -> dict:
    res: dict = {'languages': cfg.isos}
    work = cfg.work
    if cfg.scenes_raw.exists():
        raw = read_json(cfg.scenes_raw)
        res['raw_scenes'] = len(raw)
        res['raw_pages'] = len({(s['ep'], s['img']) for s in raw})
        res['raw_per_lang'] = dict(Counter(l for s in raw for l in s['texts']))
        res['raw_src'] = dict(Counter(s['src'] for s in raw))
        res['episodes'] = sorted({s['ep'] for s in raw})
        xs = work / 'extract-stats.json'
        if xs.exists():
            es = read_json(xs)
            res['mixed_format_pages'] = es.get('mixed_format_pages', [])
            res['pages_total'] = es.get('pages_total')
    if cfg.scenes.exists() and cfg.lexicon.exists():
        sc = read_json(cfg.scenes); lex = read_json(cfg.lexicon)
        langs = [l for l in cfg.isos if any(l in s['texts'] for s in sc)]
        res['kept_scenes'] = len(sc)
        res['kept_pages'] = len({(s['ep'], s['img']) for s in sc})
        res['kept_per_lang'] = {l: sum(1 for s in sc if l in s['texts']) for l in langs}
        res['pages_per_lang'] = {l: len({s['img'] for s in sc if l in s['texts']}) for l in langs}
        res['lemmas'] = {l: len(lex.get(l, {})) for l in langs}
        res['tokens'] = {l: sum(v[0] for v in lex.get(l, {}).values()) for l in langs}
        cr = {}
        for l in langs:
            have = [s for s in sc if l in s['texts']]
            ok = sum(1 for s in have if rect_ok(s['rects'].get(l)))
            cr[l] = {'n': len(have), 'ok': ok, 'pct': round(100.0 * ok / max(1, len(have)), 1)}
        res['rect_ok'] = cr
        res['rect_ok_pct'] = {l: v['pct'] for l, v in cr.items()}
        res['bridged_tokens'] = dict(Counter(l for s in sc for l, ts in s['toks'].items() for t in ts if t.get('b')))
        res['kept_src'] = dict(Counter(s['src'] for s in sc))
        st = work / 'annotate-stats.json'
        if st.exists():
            a = read_json(st)
            res['lemmas_before_hygiene'] = a.get('lemmas_before_hygiene', {})
            res['hygiene_removed'] = a.get('hygiene_removed', {})
    if cfg.pseudo.exists():
        res['pseudo'] = {l: len(v) for l, v in read_json(cfg.pseudo).items()}
    if cfg.hygiene_report.exists():
        hr = read_json(cfg.hygiene_report)
        res['hygiene'] = {'enabled': hr.get('enabled'), 'removed_total': hr.get('removed_total', 0),
                          'sample': [x['lemma'] for l, v in hr.get('languages', {}).items() for x in v.get('sample', [])[:3]][:30]}
    sizes = {}
    for n in ('scenes_raw.json', 'scenes.json', 'lexicon.json', 'pseudo.json'):
        p = work / n
        if p.exists(): sizes[n] = round(p.stat().st_size / 1024, 1)
    if packs and packs.exists():
        total = 0; per_top = Counter()
        for p in packs.rglob('*'):
            if p.is_file():
                total += p.stat().st_size
                per_top[p.relative_to(packs).parts[0]] += p.stat().st_size
        res['pack_bytes'] = total
        res['pack_bytes_by_dir'] = dict(per_top)
        for n in ('clavis.html', 'clavis-standalone.html'):
            p = packs.parent / n
            if p.exists(): sizes[n] = round(p.stat().st_size / 1024, 1)
    res['sizes_KB'] = sizes
    return res


def table(res: dict, cfg: Config) -> str:
    langs = list(res.get('kept_per_lang', {}))
    if not langs:
        return '(no annotate output yet)'
    hd = ['lang', 'scenes', 'pages', 'lemmas(before)', 'lemmas', 'rect%', 'pseudo', 'tokenizer', 'freq']
    rows = [hd]
    for l in langs:
        L = cfg.registry.get(l)
        rows.append([l, res['kept_per_lang'][l], res.get('pages_per_lang', {}).get(l, ''),
                     res.get('lemmas_before_hygiene', {}).get(l, res['lemmas'][l]), res['lemmas'][l],
                     res['rect_ok_pct'][l], res.get('pseudo', {}).get(l, '-'),
                     L.tokenizer if L else '?', L.freqSource if L else '?'])
    w = [max(len(str(r[i])) for r in rows) for i in range(len(hd))]
    return '\n'.join('  '.join(str(c).ljust(w[i]) for i, c in enumerate(r)) for r in rows)


def compare(res: dict, golden: dict) -> list[str]:
    """Return a list of drift messages (empty = pass)."""
    tol = golden.get('tolerance', {})
    pct_tol = tol.get('pct_points', 0.5)
    rel = tol.get('relative', {})   # key -> fraction allowed, e.g. {"tokens": 0.02}
    problems = []

    def cmp(key, exp, act, path):
        if isinstance(exp, dict):
            if not isinstance(act, dict):
                problems.append(f'{path}: missing'); return
            for k, v in exp.items():
                cmp(key, v, act.get(k), f'{path}.{k}')
        elif isinstance(exp, (int, float)) and not isinstance(exp, bool):
            if act is None:
                problems.append(f'{path}: missing (expected {exp})'); return
            if key.startswith('rect_ok') or key.endswith('pct'):
                if abs(float(act) - float(exp)) > pct_tol:
                    problems.append(f'{path}: {act} vs golden {exp} (tolerance ±{pct_tol} pt)')
            elif key in rel:
                if abs(act - exp) > rel[key] * max(1, abs(exp)):
                    problems.append(f'{path}: {act} vs golden {exp} (tolerance ±{rel[key]*100:.0f}%)')
            elif act != exp:
                problems.append(f'{path}: {act} vs golden {exp} (exact)')
        elif isinstance(exp, list):
            if act != exp:
                problems.append(f'{path}: {act} vs golden {exp}')

    for key in golden.get('check', []):
        if key not in golden:
            continue
        cmp(key, golden[key], res.get(key), key)
    return problems


def run(cfg: Config, packs: Path | None, check: Path | None, write_golden: Path | None) -> dict:
    res = collect(cfg, packs)
    write_json(cfg.work / 'validate.json', res, indent=1)
    print(table(res, cfg))
    print(f"raw {res.get('raw_scenes')} scenes / {res.get('raw_pages')} pages -> kept {res.get('kept_scenes')} scenes / {res.get('kept_pages')} pages; "
          f"src {res.get('kept_src')}; mixed-format pages {len(res.get('mixed_format_pages', []))}; hygiene removed {res.get('hygiene', {}).get('removed_total', 0)}")
    if res.get('pack_bytes'):
        print(f"pack {res['pack_bytes'] / 1e6:.2f} MB {res.get('pack_bytes_by_dir')}")
    if write_golden:
        g = {k: res[k] for k in ('raw_scenes', 'raw_pages', 'raw_per_lang', 'raw_src', 'kept_scenes', 'kept_pages',
                                 'kept_per_lang', 'lemmas', 'tokens', 'bridged_tokens', 'rect_ok_pct', 'pseudo', 'hygiene_removed') if k in res}
        g['check'] = list(g.keys())
        g['tolerance'] = {'pct_points': 0.5, 'relative': {}}
        g['note'] = 'generated by validate.py --write-golden'
        g['languages'] = res['languages']
        write_json(write_golden, g, indent=1)
        log('validate: wrote golden', write_golden)
    res['golden_check'] = None
    if check:
        golden = read_json(check)
        problems = compare(res, golden)
        res['golden_check'] = {'file': str(check), 'pass': not problems, 'problems': problems}
        write_json(cfg.work / 'validate.json', res, indent=1)
        if problems:
            print(f'GOLDEN CHECK FAILED against {check}:')
            for p in problems: print('  -', p)
        else:
            print(f'GOLDEN CHECK PASSED against {check} ({len(golden.get("check", []))} keys)')
    return res


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_common_args(ap)
    ap.add_argument('--check', type=Path, default=None)
    ap.add_argument('--write-golden', type=Path, default=None)
    ap.add_argument('--no-packs', action='store_true', help='do not look at the packs dir')
    a = ap.parse_args(argv)
    cfg = Config.from_args(a)
    res = run(cfg, None if a.no_packs else cfg.packs, a.check, a.write_golden)
    if res.get('golden_check') and not res['golden_check']['pass']:
        sys.exit(1)


if __name__ == '__main__':
    main()
