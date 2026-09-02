"""Stage 2 — tokenise, lemmatise, frequency-rank, draw heuristic bridges, apply lexicon hygiene.

Reads  <work>/scenes_raw.json  ->  writes <work>/scenes.json, <work>/lexicon.json,
                                          <work>/hygiene-report.json, <work>/annotate-stats.json

Everything language-specific comes from languages.json:
  tokenizer     simplemma | fugashi | jieba | mecab-ko | arabic | whitespace
  script        picks the word regex (Devanagari runs, Arabic runs, else Unicode letters) and
                which bridge rule applies (Levenshtein for LATIN/CYRILLIC, shared Han characters for CJK)
  wordfreqLang  Zipf source; freqSource "corpus" derives a Zipf-like value from corpus counts
  siblings      bridge languages (same script only) in addition to English for Latin scripts
  kaikkiName    Wiktionary extract used by lexicon hygiene (D-7)

Scene filters (English only, unchanged from the reference): sound-effect/junk lines with fewer than
two real words (zipf >= 2 and length >= 2), and credits/meta lines matched by a keyword regex.

Bridge heuristic is the reference one (lemma length >= 4, max(sim(lemma), sim(surface)) >= 0.67,
same initial letter after diacritic stripping, best per bridge language) made O(n*k) by bucketing
candidates on that initial letter and pruning pairs whose length difference alone rules out 0.67.
Results are identical to the reference implementation.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import time
import unicodedata
from collections import Counter, defaultdict
from functools import lru_cache
from multiprocessing import Pool

import wordfreq

from config import Config, Language, add_common_args, log, warn, write_json
from hygiene import Attestor

# ---------- romanization helpers (reference) ----------
KATA = {'ア':'a','イ':'i','ウ':'u','エ':'e','オ':'o','カ':'ka','キ':'ki','ク':'ku','ケ':'ke','コ':'ko','サ':'sa','シ':'shi','ス':'su','セ':'se','ソ':'so','タ':'ta','チ':'chi','ツ':'tsu','テ':'te','ト':'to','ナ':'na','ニ':'ni','ヌ':'nu','ネ':'ne','ノ':'no','ハ':'ha','ヒ':'hi','フ':'fu','ヘ':'he','ホ':'ho','マ':'ma','ミ':'mi','ム':'mu','メ':'me','モ':'mo','ヤ':'ya','ユ':'yu','ヨ':'yo','ラ':'ra','リ':'ri','ル':'ru','レ':'re','ロ':'ro','ワ':'wa','ヲ':'o','ン':'n','ガ':'ga','ギ':'gi','グ':'gu','ゲ':'ge','ゴ':'go','ザ':'za','ジ':'ji','ズ':'zu','ゼ':'ze','ゾ':'zo','ダ':'da','ヂ':'ji','ヅ':'zu','デ':'de','ド':'do','バ':'ba','ビ':'bi','ブ':'bu','ベ':'be','ボ':'bo','パ':'pa','ピ':'pi','プ':'pu','ペ':'pe','ポ':'po','ヴ':'vu'}
SMALL = {'ャ':'ya','ュ':'yu','ョ':'yo','ァ':'a','ィ':'i','ゥ':'u','ェ':'e','ォ':'o'}
def kata2roma(k):
    out=[]; i=0
    while i < len(k):
        c=k[i]
        if c=='ッ':
            nxt = KATA.get(k[i+1]) if i+1<len(k) else None
            out.append(nxt[0] if nxt else ''); i+=1; continue
        if c=='ー':
            if out and out[-1] and out[-1][-1] in 'aiueo': out.append(out[-1][-1])
            i+=1; continue
        if i+1<len(k) and k[i+1] in SMALL and c in KATA:
            base=KATA[c]; sm=SMALL[k[i+1]]
            if base.endswith('i'): out.append(base[:-1]+ (sm if base[:-1] in ('sh','ch','j') else 'y'+sm[-1]) if len(sm)==2 else base[:-1]+sm)
            else: out.append(base+sm)
            i+=2; continue
        out.append(KATA.get(c, c)); i+=1
    return ''.join(out)

L_RR=['g','kk','n','d','tt','r','m','b','pp','s','ss','','j','jj','ch','k','t','p','h']
V_RR=['a','ae','ya','yae','eo','e','yeo','ye','o','wa','wae','oe','yo','u','wo','we','wi','yu','eu','ui','i']
T_RR=['','k','k','k','n','n','n','t','l','k','m','p','l','l','p','l','m','p','p','t','t','ng','t','t','k','t','p','t']
def hangul2rr(w):
    out=[]
    for ch in w:
        o=ord(ch)
        if 0xAC00 <= o <= 0xD7A3:
            o-=0xAC00; out.append(L_RR[o//588]+V_RR[(o%588)//28]+T_RR[o%28])
        else: out.append(ch)
    return ''.join(out)

AR_MAP={'ا':'a','ب':'b','ت':'t','ث':'th','ج':'j','ح':'h','خ':'kh','د':'d','ذ':'dh','ر':'r','ز':'z','س':'s','ش':'sh','ص':'s','ض':'d','ط':'t','ظ':'z','ع':'ʿ','غ':'gh','ف':'f','ق':'q','ك':'k','ل':'l','م':'m','ن':'n','ه':'h','ة':'a','و':'w','ي':'y','ى':'a','ء':'ʾ','أ':'a','إ':'i','آ':'ā','ئ':'ʾ','ؤ':'ʾ','َ':'a','ِ':'i','ُ':'u','ً':'an','ٍ':'in','ٌ':'un','ْ':'','ّ':'','ٰ':'ā','ـ':''}
def ar2roman(w):
    return ''.join(AR_MAP.get(c,'') for c in w)

def zh2pinyin(w):
    try:
        from pypinyin import pinyin as _pinyin, Style as _Style
        return ' '.join(x[0] for x in _pinyin(w, style=_Style.TONE))
    except Exception:  # noqa: BLE001
        return ''

def hi2roman(w):
    """IAST via indic_transliteration when installed, else ''."""
    try:
        from indic_transliteration import sanscript
        return sanscript.transliterate(w, sanscript.DEVANAGARI, sanscript.IAST)
    except Exception:  # noqa: BLE001
        return ''

# ---------- normalisation (reference) ----------
AR_DIAC = re.compile('[\u0610-\u061A\u064B-\u0652\u0670\u0640\u06D6-\u06ED]')
ALEFS = re.compile('[\u0625\u0623\u0622\u0671]')   # alef variants -> bare alef
def ar_norm(w):
    w = AR_DIAC.sub('', w)
    w = ALEFS.sub('ا', w)
    return w

def turkic_lower(s: str) -> str:
    """Turkish/Azeri case folding: dotted İ -> i, dotless I -> ı (str.lower would yield i + U+0307)."""
    return s.replace('İ', 'i').replace('I', 'ı').lower()


@lru_cache(maxsize=200000)
def strip_diac(w):
    return ''.join(c for c in unicodedata.normalize('NFD', w) if unicodedata.category(c) != 'Mn').lower()

WORD_RE = re.compile(r"[^\W\d_]+(?:['\u2019\-][^\W\d_]+)*", re.UNICODE)
AR_RE = re.compile('[\u0600-\u06FF]+')
DEV_RE = re.compile('[\u0900-\u0963\u0970-\u097F]+')   # Devanagari runs incl. matras, excluding danda + digits
HAN_RE = re.compile('[\u4E00-\u9FFF]')
HANGUL_RE = re.compile('[\uAC00-\uD7A3]')
JUNK_RE = re.compile(r'creative commons|http|www\.|patreon|revoy|translat|proofread|correction|license|licence|font|sources? files|special thanks|credits', re.I)


@lru_cache(maxsize=500000)
def zipf(w, lang):
    if not lang:
        return 0.0
    return round(wordfreq.zipf_frequency(w, lang), 2)


# ---------- tokenizers ----------
_TOK = {}
_TAGGER = None
_S2T = None
_LEMMATIZER = None


def _simplemma():
    """One simplemma Lemmatizer whose dictionary cache holds every language we use. The module-level
    simplemma.lemmatize() keeps only 8 dictionaries (LRU) and reloads one per call once more than
    eight languages are cycled through — 1.2 s per reload, which made annotate 40x slower."""
    global _LEMMATIZER
    if _LEMMATIZER is None:
        from simplemma import Lemmatizer
        from simplemma.strategies import DefaultStrategy
        from simplemma.strategies.dictionaries.dictionary_factory import DefaultDictionaryFactory
        _LEMMATIZER = Lemmatizer(lemmatization_strategy=DefaultStrategy(dictionary_factory=DefaultDictionaryFactory(cache_max_size=64)))
    return _LEMMATIZER


def _tagger():
    global _TAGGER
    if _TAGGER is None:
        import fugashi
        _TAGGER = fugashi.Tagger()
    return _TAGGER


def _s2t():
    global _S2T
    if _S2T is None:
        import opencc
        _S2T = opencc.OpenCC('s2t')
    return _S2T


def word_regex(lang: Language):
    if lang.script == 'DEVANAGARI':
        return DEV_RE
    if lang.script == 'ARABIC':
        return AR_RE
    return WORD_RE


def make_tokenizer(lang: Language):
    wfl = lang.wordfreqLang
    tok = lang.tokenizer
    if tok == 'simplemma':
        lemmatize = _simplemma().lemmatize
        code = lang.lemmatizer.split(':', 1)[1] if ':' in lang.lemmatizer else lang.iso
        rx = word_regex(lang)
        reading = hi2roman if lang.script == 'DEVANAGARI' else None
        lower = turkic_lower if lang.caseFolding == 'turkic' else str.lower

        def tok_simplemma(text):
            toks = []
            for m in rx.finditer(text):
                s = m.group(0)
                low = lower(s)
                lem = lemmatize(low, lang=code).lower()
                z = max(zipf(lem, wfl), zipf(low, wfl))
                t = {'s': s, 'l': lem, 'z': z}
                if reading:
                    r = reading(s)
                    if r: t['r'] = r
                toks.append(t)
            return toks
        return tok_simplemma

    if tok == 'whitespace':
        rx = word_regex(lang)

        def tok_plain(text):
            return [{'s': m.group(0), 'l': m.group(0).lower(), 'z': zipf(m.group(0).lower(), wfl)} for m in rx.finditer(text)]
        return tok_plain

    if tok == 'fugashi':
        def tok_ja(text):
            toks = []
            for w in _tagger()(text):
                pos = w.feature.pos1
                if pos in ('補助記号', '記号', '空白') or not w.surface.strip():
                    continue
                lem = getattr(w.feature, 'orthBase', None) or w.feature.lemma or w.surface
                if not lem or lem == '*': lem = w.surface
                z = max(zipf(lem, wfl), zipf(w.surface, wfl))
                kana = getattr(w.feature, 'pron', None) or getattr(w.feature, 'kana', None) or ''
                toks.append({'s': w.surface, 'l': lem, 'z': z, 'p': pos, 'r': kata2roma(kana) if kana and kana != '*' else ''})
            return toks
        return tok_ja

    if tok == 'jieba':
        def tok_zh(text):
            toks = []
            for s in wordfreq.tokenize(text, wfl or 'zh'):
                if not HAN_RE.search(s):
                    continue
                toks.append({'s': s, 'l': s, 'z': zipf(s, wfl), 't': _s2t().convert(s), 'r': zh2pinyin(s)})
            return toks
        return tok_zh

    if tok == 'mecab-ko':
        def tok_ko(text):
            toks = []
            for s in wordfreq.tokenize(text, wfl or 'ko'):
                if not HANGUL_RE.search(s):
                    continue
                toks.append({'s': s, 'l': s, 'z': zipf(s, wfl), 'r': hangul2rr(s)})
            return toks
        return tok_ko

    if tok == 'arabic':
        def tok_ar(text):
            toks = []
            for m in AR_RE.finditer(text):
                s = m.group(0)
                n = ar_norm(s)
                if len(n) < 2:
                    continue
                z = zipf(n, wfl)
                alt = []
                if n.startswith('و') and len(n) > 3: alt.append(n[1:])            # و
                if n.startswith('ال') and len(n) > 4: alt.append(n[2:])      # ال
                if n.startswith('وال') and len(n) > 5: alt.append(n[3:])  # وال
                zalt = max([zipf(a, wfl) for a in alt], default=0)
                toks.append({'s': s, 'l': n, 'z': max(z, zalt), 'r': ar2roman(s)})
            return toks
        return tok_ar

    raise SystemExit(f'unknown tokenizer {tok!r} for {lang.iso}')


def tokenizer(lang: Language):
    f = _TOK.get(lang.iso)
    if f is None:
        f = _TOK[lang.iso] = make_tokenizer(lang)
    return f


# ---------- similarity ----------
def lev(a, b):
    if a == b: return 0
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def sim_n(a, b):
    """sim on already diacritic-stripped strings."""
    if not a or not b: return 0.0
    return 1 - lev(a, b) / max(len(a), len(b))


def length_bound(a, b):
    """Upper bound of sim(a, b) from lengths alone (same float expression as sim_n)."""
    if not a or not b: return 0.0
    m = max(len(a), len(b))
    return 1 - abs(len(a) - len(b)) / m


def bridge_index(toks):
    """Bucket a language's tokens (lemma length >= 4) by initial letter of the stripped lemma."""
    idx = defaultdict(list)
    for u in toks:
        if len(u['l']) < 4: continue
        nl = strip_diac(u['l']); ns = strip_diac(u['s'])
        idx[nl[:1]].append((u, nl, ns))
    return idx


def best_bridge(t, idx):
    nl = strip_diac(t['l']); ns = strip_diac(t['s'])
    best = None
    for u, unl, uns in idx.get(nl[:1], ()):
        s = 0.0
        if length_bound(nl, unl) >= 0.67:
            s = sim_n(nl, unl)
        if length_bound(ns, uns) >= 0.67:
            s2 = sim_n(ns, uns)
            if s2 > s: s = s2
        if s >= 0.67 and (best is None or s > best[1]):
            best = (u['s'], s)
    return best


# ---------- per-scene worker ----------
_CTX = {}


def _init_worker(lang_dicts, credits_filter=False):
    """Pool initializer: rebuild Language objects and the per-language rules in the worker."""
    langs = {d['iso']: Language(**{k: v for k, v in d.items() if k in Language.__dataclass_fields__}) for d in lang_dicts}
    _CTX['langs'] = langs
    _CTX['en'] = langs['en']
    _CTX['credits_filter'] = credits_filter


def is_name_list(toks, min_tokens=20, share=0.7):
    """Patron/credits lists: long balloons made almost entirely of Title-Case words ("Addison Lewis -
    Adrian Lord - ..."). All-caps lettering (ep39) is not Title Case and stays."""
    if len(toks) < min_tokens:
        return False
    title = sum(1 for t in toks if len(t['s']) >= 2 and t['s'][0].isupper() and t['s'][1:].islower())
    return title / len(toks) >= share


def process_scene(sc):
    """Tokenise one raw scene. Returns None when filtered out ('credits' for a patron list), else the token map."""
    langs = _CTX['langs']
    en_toks = tokenizer(langs['en'])(sc['texts']['en'])
    if sum(1 for t in en_toks if t['z'] >= 2.0 and len(t['s']) >= 2) < 2:
        return None   # sound effects / junk
    if JUNK_RE.search(sc['texts']['en']):
        return None   # credits / meta text
    if _CTX.get('credits_filter') and is_name_list(en_toks):
        return 'credits'   # patron lists (hygiene only: the reference pipeline kept them)
    toks = {}
    for iso, text in sc['texts'].items():
        toks[iso] = en_toks if iso == 'en' else tokenizer(langs[iso])(text)
    # bridges
    indices = {}
    for iso in toks:
        if iso == 'en':
            continue
        L = langs[iso]
        if L.script in ('LATIN', 'CYRILLIC'):
            bls = (['en'] if L.script == 'LATIN' else []) + [s for s in L.siblings if s in langs and langs[s].script == L.script and s != iso]
            for t in toks[iso]:
                if len(t['l']) < 4:
                    continue
                br = []
                for bl in bls:
                    if bl not in toks: continue
                    if bl not in indices: indices[bl] = bridge_index(toks[bl])
                    best = best_bridge(t, indices[bl])
                    if best:
                        br.append({'v': bl, 'w': best[0], 'k': round(best[1], 2)})
                if br:
                    t['b'] = br
        elif L.script == 'CJK':
            others = [o for o in toks if o != iso and langs[o].script == 'CJK']
            for t in toks[iso]:
                br = []
                for o in others:
                    if iso == 'ja':
                        mine = set(HAN_RE.findall(t['l']))
                        theirs = set(''.join(u['s'] + u.get('t', '') for u in toks[o]))
                    else:
                        mine = set(t['s']) | set(t.get('t', ''))
                        theirs = set(''.join(u['l'] + u['s'] for u in toks[o]))
                    shared = ''.join(sorted(mine & theirs))
                    if shared:
                        br.append({'v': o, 'w': shared, 'k': 1.0})
                if br:
                    t['b'] = br
    return toks


# ---------- main ----------
def run(cfg: Config, hygiene: bool = True) -> dict:
    t0 = time.time()
    raw = json.load(open(cfg.scenes_raw, encoding='utf-8'))
    langs = {l.iso: l for l in cfg.languages}
    lang_dicts = [l.raw | {'iso': l.iso} for l in cfg.languages]
    log('annotate:', cfg.describe())
    log(f'annotate: {len(raw)} raw scenes, hygiene={"on" if hygiene else "off"}, workers={cfg.workers}')

    if cfg.workers > 1:
        with Pool(cfg.workers, initializer=_init_worker, initargs=(lang_dicts, hygiene)) as pool:
            results = list(pool.imap(process_scene, raw, chunksize=8))
    else:
        _init_worker(lang_dicts, hygiene)
        results = [process_scene(sc) for sc in raw]

    scenes = []
    credits_scenes = []
    lexicon = defaultdict(lambda: defaultdict(lambda: {'n': 0, 'z': 0, 's': None, 'zmax': 0.0}))
    clampr = lambda r: [min(max(v, 0.0), 1.0) for v in r]
    for sc, toks in zip(raw, results):
        if toks == 'credits':
            credits_scenes.append(f"ep{sc['ep']:02d}/{sc['page']}")
            continue
        if toks is None:
            continue
        for iso, ts in toks.items():
            for t in ts:
                e = lexicon[iso][t['l']]
                e['n'] += 1; e['z'] = t['z']; e['s'] = e['s'] or t['s']
                e['zmax'] = max(e['zmax'], t['z'])
                if t.get('r') and not e.get('r'): e['r'] = t['r']
        scenes.append({'id': len(scenes), 'ep': sc['ep'], 'page': sc['page'], 'img': sc['img'],
                       'rect': clampr(sc['rect']),
                       'rects': {l: clampr(r) for l, r in sc.get('rects', {}).items()},
                       'src': sc.get('src', 'flow'),
                       'texts': sc['texts'], 'toks': toks})
    t_tok = time.time() - t0
    log(f'annotate: {len(scenes)} scenes kept of {len(raw)} in {t_tok:.1f} s')

    # corpus-derived Zipf-like frequencies (languages without a wordfreq list, e.g. Latin)
    corpus_freq = {}
    for iso, L in langs.items():
        if L.freqSource != 'corpus' or iso not in lexicon:
            continue
        maxc = max(e['n'] for e in lexicon[iso].values())
        zmap = {lem: round(6.5 + math.log10(e['n'] / maxc), 2) for lem, e in lexicon[iso].items()}
        for lem, e in lexicon[iso].items():
            e['z'] = e['zmax'] = zmap[lem]
        for sc in scenes:
            for t in sc['toks'].get(iso, ()):
                t['z'] = zmap[t['l']]
        corpus_freq[iso] = {'lemmas': len(zmap), 'maxCount': maxc, 'zipfRange': [min(zmap.values()), max(zmap.values())]}
        log(f'annotate: {iso}: corpus-derived Zipf for {len(zmap)} lemmas (max count {maxc} -> 6.5)')

    # lexicon hygiene (D-7)
    report = {'enabled': hygiene, 'rule': 'drop lemma if zipf == 0 (wordfreq) AND neither lemma nor first surface is a kaikki headword/form; '
                                         'CJK exempt; languages with corpus-derived frequencies (no wordfreq list) use attestation alone',
              'languages': {}}
    before = {iso: len(d) for iso, d in lexicon.items()}
    if hygiene:
        att = Attestor(cfg.kaikki, cfg.cache / 'kaikki-words', log)
        for iso in list(lexicon):
            L = langs[iso]
            if L.is_cjk:
                report['languages'][iso] = {'skipped': 'CJK exempt', 'before': before[iso], 'after': before[iso], 'removed': 0}
                continue
            if not att.available(L.kaikkiName):
                report['languages'][iso] = {'skipped': f'no kaikki extract ({L.kaikkiName})', 'before': before[iso], 'after': before[iso], 'removed': 0}
                continue
            dropped = {}
            wordfreq_evidence = L.freqSource != 'corpus'   # corpus-derived Zipf is never 0: attestation decides alone
            for lem, e in lexicon[iso].items():
                if wordfreq_evidence and e['zmax'] > 0:
                    continue
                forms = [lem, e['s'], (e['s'] or '').lower()]
                if att.attested(L.kaikkiName, L.script, forms):
                    continue
                dropped[lem] = e
            for lem in dropped:
                del lexicon[iso][lem]
            ntok = 0
            for sc in scenes:
                ts = sc['toks'].get(iso)
                if ts:
                    kept = [t for t in ts if t['l'] not in dropped]
                    ntok += len(ts) - len(kept)
                    sc['toks'][iso] = kept
            sample = sorted(dropped.items(), key=lambda kv: (-kv[1]['n'], kv[0]))
            report['languages'][iso] = {
                'before': before[iso], 'after': len(lexicon[iso]), 'removed': len(dropped), 'tokensRemoved': ntok,
                'sample': [{'lemma': k, 'surface': v['s'], 'count': v['n']} for k, v in sample[:40]],
            }
            log(f'annotate: hygiene {iso}: {before[iso]} -> {len(lexicon[iso])} lemmas (-{len(dropped)}, {ntok} tokens); '
                f'e.g. {", ".join(k for k, _ in sample[:8])}')
    report['removed_total'] = sum(v.get('removed', 0) for v in report['languages'].values())
    report['creditsScenesRemoved'] = credits_scenes
    if credits_scenes:
        log(f'annotate: hygiene dropped {len(credits_scenes)} patron-list scenes: {", ".join(credits_scenes)}')
    write_json(cfg.hygiene_report, report, indent=1)

    write_json(cfg.scenes, scenes)
    lex_out = {l: {w: [v['n'], v['z'], v['s'], v.get('r', '')] for w, v in d.items()} for l, d in lexicon.items()}
    write_json(cfg.lexicon, lex_out)
    nb = Counter(l for s in scenes for l, ts in s['toks'].items() for t in ts if t.get('b'))
    stats = {'raw_scenes': len(raw), 'scenes': len(scenes),
             'pages': len({(s['ep'], s['img']) for s in scenes}),
             'per_language': {l: sum(1 for s in scenes if l in s['texts']) for l in langs},
             'lemmas': {l: len(d) for l, d in lex_out.items()},
             'lemmas_before_hygiene': before,
             'tokens': {l: sum(v[0] for v in d.values()) for l, d in lex_out.items()},
             'bridged_tokens': dict(nb), 'corpus_freq': corpus_freq,
             'hygiene_removed': {l: v.get('removed', 0) for l, v in report['languages'].items()},
             'credits_scenes_removed': len(credits_scenes),
             'seconds': round(time.time() - t0, 1), 'seconds_tokenise': round(t_tok, 1)}
    write_json(cfg.work / 'annotate-stats.json', stats, indent=1)
    log(len(scenes), 'scenes')
    for l, d in lex_out.items():
        log(l, 'lemmas:', len(d), 'tokens:', stats['tokens'][l])
    log('bridged tokens:', dict(nb), f'| {stats["seconds"]} s')
    return stats


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_common_args(ap)
    ap.add_argument('--no-hygiene', action='store_true', help='skip lexicon hygiene (regression runs)')
    a = ap.parse_args(argv)
    run(Config.from_args(a), hygiene=not a.no_hygiene)


if __name__ == '__main__':
    main()
