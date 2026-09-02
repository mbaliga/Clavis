#!/usr/bin/env python3
"""Etymology graph for the Clavis lexicon: Wiktionary (wiktextract / kaikki.org, CC BY-SA 4.0)
unioned with EtymDB 2.1 (CC BY-SA 4.0).

Output  pipeline/build/packs/etymology/etym.json.gz
        {lang: {lemma: {o?: "Latin clavis", ot?: "inh"|"bor", e?: [[lang, lemma, "cog"|"bor"|"der"], …≤8],
                        src: "wiktionary"|"etymdb"|"wiktionary+etymdb", ipa?: "…"}}}
        pipeline/build/packs/etymology/manifest-fragment.json      (PackManifest, kind ETYMOLOGY)
        pipeline/build/enrich/etym_provenance.jsonl                 one line per emitted fact
        pipeline/build/enrich/etym_coverage.json                    the numbers printed at the end
        pipeline/build/enrich/sinographic.json                      shared hanzi/hanja compounds

Rules (docs/07-etymology.md §8, docs/DECISIONS.md D-5): nothing is emitted that does not trace to
a specific Wiktionary template / descendants row or EtymDB link row. Origins are chosen exactly
like reference-prototype/etym.py: nearest ancestor first, then the ANC_PRIO priority list.
Edges: direct links, shared attested ancestor within combined distance ≤ 4, ancestor descendants
lists, cognate templates; hub guard (> 40 corpus descendants ⇒ ancestor skipped for edges);
≤ 8 edges per lemma in deterministic order.

usage: enrich_etymology.py [--lexicon PATH] [--languages es,fr,…] [--kaikki-dir DIR]
                           [--etymdb-dir DIR] [--desc-dir DIR] [--out-dir DIR] [--workers 2]
                           [--no-etymdb] [--no-wiktionary]
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from enrich_common import (  # noqa: E402
    load_lexicon,
    ATTR_ETYMDB, ATTR_WIKTIONARY, BUILD_DIR, CF_CHARS, DEFAULT_ETYMDB_DIR, DEFAULT_KAIKKI_DIR,
    DEFAULT_LEXICON, ENRICH_DIR, HAN_RE, PACKS_DIR, ar_norm, collect_kaikki_matches, is_han,
    lexicon_keys, load_registry, log, manifest_fragment, norm_key, opencc, strip_diac,
    write_json, write_json_gz,
)

# --------------------------------------------------------------------------------------
# Language names and origin priority (etym.py's tables, extended with the codes that exist in
# the data: la-vul etc. instead of the non-existent VL./LL./ML., sit-pro, ojp, okm, oko)
# --------------------------------------------------------------------------------------
LANGNAME = {
    'la': 'Latin', 'grc': 'Ancient Greek', 'sa': 'Sanskrit', 'fa': 'Persian', 'ar': 'Arabic', 'fro': 'Old French',
    'frm': 'Middle French', 'enm': 'Middle English', 'ang': 'Old English', 'pt': 'Portuguese', 'hi': 'Hindi', 'ur': 'Urdu',
    'ota': 'Ottoman Turkish', 'tr': 'Turkish', 'nds': 'Low German', 'dum': 'Middle Dutch', 'odt': 'Old Dutch',
    'goh': 'Old High German', 'gmh': 'Middle High German', 'non': 'Old Norse', 'osp': 'Old Spanish',
    'VL.': 'Vulgar Latin', 'LL.': 'Late Latin', 'ML.': 'Medieval Latin', 'la-vul': 'Vulgar Latin', 'la-lat': 'Late Latin',
    'la-med': 'Medieval Latin', 'la-new': 'New Latin', 'la-ecc': 'Ecclesiastical Latin', 'la-ren': 'Renaissance Latin',
    'ine-pro': 'Proto-Indo-European', 'gem-pro': 'Proto-Germanic', 'gmw-pro': 'Proto-West Germanic',
    'itc-pro': 'Proto-Italic', 'sla-pro': 'Proto-Slavic', 'ltc': 'Middle Chinese', 'och': 'Old Chinese', 'oko': 'Old Korean',
    'okm': 'Middle Korean', 'ojp': 'Old Japanese', 'arc': 'Aramaic', 'he': 'Hebrew', 'es': 'Spanish', 'fr': 'French',
    'it': 'Italian', 'de': 'German', 'nl': 'Dutch', 'ja': 'Japanese', 'ko': 'Korean', 'zh': 'Chinese', 'en': 'English',
    'grk-pro': 'Proto-Hellenic', 'sem-pro': 'Proto-Semitic', 'ine-bsl-pro': 'Proto-Balto-Slavic',
    'roa-opt': 'Old Galician-Portuguese', 'xno': 'Anglo-Norman', 'frk': 'Frankish', 'got': 'Gothic', 'egy': 'Egyptian',
    'cop': 'Coptic', 'san': 'Sanskrit', 'pal': 'Middle Persian', 'peo': 'Old Persian', 'nan': 'Min Nan', 'yue': 'Cantonese',
    'cmn': 'Mandarin', 'mul': 'Translingual', 'sit-pro': 'Proto-Sino-Tibetan', 'lzh': 'Literary Chinese', 'ru': 'Russian',
    'pl': 'Polish', 'uk': 'Ukrainian', 'orv': 'Old East Slavic', 'cu': 'Old Church Slavonic', 'zlw-opl': 'Old Polish',
    'ml': 'Malayalam', 'ta': 'Tamil', 'jpx-pro': 'Proto-Japonic', 'kor-pro': 'Proto-Koreanic', 'afa-pro': 'Proto-Afroasiatic',
    'ber-pro': 'Proto-Berber', 'syc': 'Classical Syriac', 'fa-cls': 'Classical Persian', 'nah': 'Nahuatl', 'nci': 'Classical Nahuatl',
    'qu': 'Quechua', 'tpw': 'Old Tupi', 'ain': 'Ainu', 'oc': 'Occitan', 'pro': 'Old Occitan', 'ca': 'Catalan', 'gl': 'Galician',
    'mnc': 'Manchu', 'mn': 'Mongolian', 'lad': 'Ladino', 'yi': 'Yiddish', 'gml': 'Middle Low German', 'osx': 'Old Saxon',
    'fy': 'West Frisian', 'da': 'Danish', 'sv': 'Swedish', 'no': 'Norwegian', 'is': 'Icelandic', 'vi': 'Vietnamese',
    'ms': 'Malay', 'id': 'Indonesian', 'tl': 'Tagalog', 'haw': 'Hawaiian', 'sw': 'Swahili', 'hu': 'Hungarian', 'fi': 'Finnish',
    'el': 'Greek', 'gkm': 'Byzantine Greek', 'ine-ana-pro': 'Proto-Anatolian', 'iir-pro': 'Proto-Indo-Iranian',
    'ira-pro': 'Proto-Iranian', 'inc-pro': 'Proto-Indo-Aryan', 'cel-pro': 'Proto-Celtic', 'gaulish': 'Gaulish', 'cel-gau': 'Gaulish',
    'xcl': 'Old Armenian', 'hy': 'Armenian', 'ka': 'Georgian', 'ku': 'Kurdish', 'ps': 'Pashto', 'bn': 'Bengali', 'pa': 'Punjabi',
    'mr': 'Marathi', 'gu': 'Gujarati', 'ne': 'Nepali', 'pli': 'Pali', 'pra': 'Prakrit', 'inc-ash': 'Ashokan Prakrit',
    'inc-ohi': 'Old Hindi', 'psu': 'Sauraseni Prakrit', 'inc-sap': 'Sauraseni Apabhramsa', 'th': 'Thai', 'km': 'Khmer',
    'my': 'Burmese', 'bo': 'Tibetan', 'ug': 'Uyghur', 'az': 'Azerbaijani', 'trk-pro': 'Proto-Turkic', 'otk': 'Old Turkic',
}

# Origin priority — etym.py's list with the codes that actually occur in the data inserted next
# to their intended siblings. Ranking only breaks ties at equal distance; the nearest attested
# ancestor always wins. Languages not in this list are still eligible as origins for Wiktionary
# hops (the template names them explicitly); they rank last.
ANC_PRIO = ['la', 'la-vul', 'la-lat', 'la-med', 'la-new', 'la-ecc', 'la-ren', 'VL.', 'LL.', 'ML.', 'grc', 'sa', 'fa', 'ar',
            'ltc', 'och', 'lzh', 'fro', 'frm', 'xno', 'enm', 'ang', 'non', 'goh', 'gmh', 'dum', 'odt', 'frk', 'got', 'osp',
            'roa-opt', 'pt', 'ota', 'tr', 'he', 'arc', 'egy', 'ojp', 'okm', 'oko', 'orv', 'cu', 'zlw-opl',
            'gem-pro', 'gmw-pro', 'itc-pro', 'ine-pro', 'sem-pro', 'sla-pro', 'sit-pro', 'jpx-pro', 'kor-pro']
ANC_RANK = {l: i for i, l in enumerate(ANC_PRIO)}
UNLISTED_RANK = len(ANC_PRIO)

# Template name → hop type. Everything else in `tp` is ignored for facts.
HOP_TYPES = {
    'inh': 'inh', 'inh+': 'inh', 'inh-lite': 'inh', 'inherited': 'inh',
    'bor': 'bor', 'bor+': 'bor', 'bor-lite': 'bor', 'borrowed': 'bor', 'lbor': 'bor', 'slbor': 'bor', 'obor': 'bor',
    'ubor': 'bor', 'calque': 'bor', 'clq': 'bor', 'cal': 'bor', 'psm': 'bor', 'sl': 'bor', 'semantic loan': 'bor',
    'pcal': 'bor', 'translit': 'bor', 'learned borrowing': 'bor', 'orthographic borrowing': 'bor',
    'unadapted borrowing': 'bor', 'semi-learned borrowing': 'bor', 'phono-semantic matching': 'bor',
    'der': 'der', 'der+': 'der', 'der-lite': 'der', 'derived': 'der', 'uder': 'der',
}
TREE_TYPES = {':inh': 'inh', ':bor': 'bor', ':lbor': 'bor', ':slbor': 'bor', ':obor': 'bor', ':ubor': 'bor', ':clq': 'bor',
              ':calque': 'bor', ':psm': 'bor', ':sl': 'bor', ':der': 'der', ':uder': 'der'}
COG_TYPES = {'cog', 'cognate', 'cog-lite'}
NONCOG_TYPES = {'ncog', 'noncog', 'noncognate'}
LINK_TEMPLATES = {'l', 'm', 'm+', 'l-lite', 'm-lite', 'link', 'mention', 'ltc-l', 'och-l', 'okm-l', 'zh-l', 'ja-l', 'ko-l',
                  'zh-m', 'ja-r', 'ja-l/helper'}
HAN_LANGS = {'zh', 'ltc', 'och', 'lzh', 'cmn', 'yue', 'nan', 'nan-hbl', 'hak', 'wuu', 'gan', 'cdo', 'cjy', 'hsn', 'zhx-teo', 'zhx-min-pro'}
SINITIC_VARIETIES = HAN_LANGS - {'ltc', 'och', 'lzh'}
HUB_LIMIT = 40
EDGE_CAP = 8
MAX_COMBINED_DIST = 4
ETYMDB_DEPTH = 3


def edge_type_of_hop(t: str) -> str:
    return 'bor' if t == 'bor' else ('cog' if t == 'cog' else 'der')


def clean_term(term: str | None) -> str | None:
    if not term:
        return None
    t = CF_CHARS.sub('', term)
    t = re.sub(r'<[^>]*>', '', t)           # <id:…>, <t:…> qualifiers in etymon/ety trees
    t = t.replace('%', '')                   # ja-r syllable separators
    t = t.strip().strip('<>').strip()
    if t in ('-', '', '?', '*'):
        return None
    if (t.startswith('-') or t.endswith('-')) and not t.startswith('*'):
        return None                          # affixes are not origins (reconstructed roots *kalb- are kept)
    return t


def anc_key(lang: str, term: str) -> tuple[str, str] | None:
    """Canonical ancestor key. Han-script terms from any Sinitic stage share one namespace
    ('han', traditional form) so Middle Chinese 單位, Chinese 單位 and Japanese kyūjitai 單位 meet."""
    term = clean_term(term)
    if not term:
        return None
    if lang in HAN_LANGS or (is_han(term) and lang in ('ja', 'ko', 'vi')):
        if is_han(term):
            return ('han', opencc('s2t').convert(term))
    if lang == 'ar':
        return (lang, ar_norm(term))
    if lang in ('ja', 'ko'):
        return (lang, norm_key(lang, term))
    return (lang, strip_diac(term.lstrip('*')))


def lang_name(code: str, learned: dict) -> str:
    return LANGNAME.get(code) or learned.get(code) or code


def learn_name(code: str, term: str, expansion: str | None, learned: dict):
    """Language names are taken from the template expansion ("Latin grātīs") when the table
    lacks the code — the data names its own languages."""
    if code in LANGNAME or code in learned or not expansion or not term:
        return
    i = expansion.find(term)
    if i > 0:
        name = expansion[:i].strip()
        if 0 < len(name) <= 40 and not name.startswith('[') and '\n' not in name:
            learned[code] = name


# --------------------------------------------------------------------------------------
# Wiktionary side
# --------------------------------------------------------------------------------------
def hops_of_entry(entry: dict, own_lang: str, learned: dict) -> tuple[list[dict], list[dict]]:
    """Return (chain, cognates) from one entry's etymology templates.
    chain: [{type, lang, term, tpl}] in text order (distance = position + 1);
    cognates: [{lang, term, tpl}]."""
    tp = entry.get('tp') or []
    chain, cogs = [], []
    seen = set()
    cur = 0   # distance of the last hop added; successive templates continue the chain

    def add_hop(typ, lang, term, tpl, x=None, dist=None):
        nonlocal cur
        term = clean_term(term)
        if not lang or not term:
            return
        lang = lang.strip()
        k = (lang, term)
        if k in seen:
            return
        seen.add(k)
        learn_name(lang, term, x, learned)
        # A Japanese/Korean/Vietnamese word "derived from" a Sinitic stage is a borrowing (different
        # family); Wiktionary's {{der}} is only vaguer, not a claim of inheritance.
        if typ == 'der' and own_lang in ('ja', 'ko', 'vi') and lang in HAN_LANGS:
            typ = 'bor'
        if dist is None:
            dist = cur + 1
        cur = max(cur, dist)
        chain.append({'type': typ, 'lang': lang, 'term': term, 'tpl': tpl, 'dist': dist})

    for i, t in enumerate(tp):
        n, a = t.get('n'), t.get('a') or {}
        if not n:
            continue
        x = t.get('x')
        if n in HOP_TYPES:
            lang, term = a.get('2'), a.get('3')
            if clean_term(term) is None:
                # {{der|ja|ltc|-}} followed by {{ltc-l|學生}} / {{l|la|clavis}}: take the linked term
                for u in tp[i + 1:i + 3]:
                    un, ua = u.get('n'), u.get('a') or {}
                    if un in LINK_TEMPLATES:
                        if un in ('l', 'm', 'm+', 'l-lite', 'm-lite', 'link', 'mention'):
                            if ua.get('1') == lang:
                                term, x = ua.get('2'), u.get('x')
                        elif un.split('-')[0] == lang or (un in ('ja-r', 'ja-l', 'ja-l/helper') and lang == 'ja') \
                                or (un == 'ko-l' and lang == 'ko') or (un in ('zh-l', 'zh-m') and lang in HAN_LANGS):
                            term = ua.get('2') if un in ('ja-l', 'ja-l/helper') else ua.get('1')
                            if un == 'ko-l' and ua.get('2') and is_han(ua['2']):
                                term = ua['2']
                            x = u.get('x')
                        break
            add_hop(HOP_TYPES[n], lang, term, n, x)
        elif n in ('ety', 'etymon'):
            # {{etymon|en|inh|enm:word>ang:word>gmw-pro:*word}} — '>' chains successive ancestors;
            # args 4… carry further etymons (compound parts) at the same distance
            typ = TREE_TYPES.get((a.get('2') or '').strip())
            if typ:
                base = cur + 1
                for j in range(3, 8):
                    spec = re.sub(r'<[^>]*>', '', a.get(str(j)) or '')
                    d = base
                    for hop in spec.split('>'):
                        if ':' in hop:
                            lang, term = hop.split(':', 1)
                            add_hop(typ, lang, term, n, dist=d)
                            d += 1
        elif n in ('ko-etym-sino', 'ko-etym-Sino'):
            # {{ko-etym-sino|單位|unit}} or the older {{ko-etym-Sino|女|female|子|offspring}}:
            # the hanja of the whole word is the concatenation of the odd-numbered arguments
            parts = [a.get(str(j)) for j in range(1, 16, 2)]
            parts = [p for p in parts if p]
            if parts and all(is_han(p) for p in parts):
                add_hop('bor', 'zh', ''.join(parts), n, x)
        elif n in COG_TYPES:
            lang, term = a.get('1'), clean_term(a.get('2'))
            if lang and term and lang != own_lang:
                learn_name(lang, term, x, learned)
                cogs.append({'lang': lang.strip(), 'term': term, 'tpl': n})
        # NONCOG_TYPES, Han compound, liushu, categorize, ... deliberately ignored
    return chain, cogs


def han_forms_of_entry(entry: dict, lang: str) -> list[tuple[str, str]]:
    """Traditional Han spellings attested for this entry: (form, provenance)."""
    out = []
    w = entry.get('w') or ''
    if lang in ('zh', 'ja') and is_han(w):
        # zh headwords are traditional; ja shinjitai headwords are identical to the traditional form
        # for most compounds (飛行, 時間) and the kyūjitai form below covers the rest (単位 → 單位)
        out.append((opencc('s2t').convert(w), 'headword'))
    for fo in entry.get('f') or []:
        f, tags = fo.get('f'), fo.get('t') or []
        if not f or not is_han(f):
            continue
        if lang == 'ko' and 'hanja' in tags:
            out.append((f, 'form:hanja'))
        elif lang == 'ja' and 'kyūjitai' in tags:
            out.append((f, 'form:kyūjitai'))
        elif lang == 'zh' and any('Traditional' in t for t in tags):
            out.append((f, 'form:traditional'))
    return out


def flatten_descendants(items, depth=1, borrowed=False):
    for d in items or []:
        if not isinstance(d, dict):
            continue
        b = borrowed or ('borrowed' in (d.get('raw_tags') or [])) or ('borrowed' in (d.get('tags') or []))
        w, lc = d.get('word'), d.get('lang_code')
        if w and lc:
            w = re.sub(r'\s*[（(][^()（）]*[)）]\s*$', '', w).strip() or w   # Korean "학생(學生)" → 학생
            yield lc, w, depth, b
        if d.get('descendants'):
            yield from flatten_descendants(d['descendants'], depth + 1, b)


def build_wiktionary(lexicon, app_langs, registry, matches, desc_paths, prov_out):
    """Return ({node: {'o','ot','o_prov'}}, {node: {other: (type, prov)}}, sinographic, learned)."""
    learned: dict[str, str] = {}
    alias2app = {}
    for l in app_langs:
        for c in registry[l]['etymdbCodes']:
            alias2app[c] = l
        alias2app[l] = l
    # corpus node lookup by (lang, key)
    node_by_key: dict[tuple[str, str], list[tuple[str, str]]] = defaultdict(list)
    han_of_node: dict[tuple[str, str], list[tuple[str, str]]] = defaultdict(list)
    for l in app_langs:
        for lemma in lexicon[l]:
            node = (l, lemma)
            for k in lexicon_keys(l, lemma):
                node_by_key[(l, k)].append(node)
            if l == 'zh' and is_han(lemma):
                han_of_node[node].append((opencc('s2t').convert(lemma), 'lexicon'))
    # Korean homograph guard: a MeCab token such as 하 or 사 matches one Wiktionary headword with
    # several etymology sections (河, 下, 夏 …). When the matched entries attest ≥ 2 different hanja
    # spellings the lemma is ambiguous and gets no Sinographic facts at all (D-5: never invent).
    ambiguous: set[tuple[str, str]] = set()
    for l in app_langs:
        for lemma, entries in matches.get(l, {}).items():
            forms = []
            for e in entries:
                for form, how in han_forms_of_entry(e, l):
                    if (form, how) not in forms:
                        forms.append((form, how))
            if l == 'ko':
                hanja = {f for f, how in forms}
                for e in entries:
                    for t in e.get('tp') or []:
                        if t.get('n') in ('ko-etym-sino', 'ko-etym-Sino'):
                            parts = [(t.get('a') or {}).get(str(j)) for j in range(1, 16, 2)]
                            parts = [p for p in parts if p]
                            if parts and all(is_han(p) for p in parts):
                                hanja.add(''.join(parts))
                if len(hanja) >= 2:
                    ambiguous.add((l, lemma))
                    continue
            han_of_node[(l, lemma)].extend(forms)
    log(f"  ambiguous Korean homographs excluded from Sinographic facts: {len(ambiguous)}")

    def nodes_for(lang_code: str, term: str) -> list[tuple[str, str]]:
        app = alias2app.get(lang_code)
        if not app:
            return None
        term = clean_term(term)
        if not term:
            return []
        res = []
        for k in lexicon_keys(app, term):
            res.extend(node_by_key.get((app, k), []))
        if app == 'zh' and is_han(term):
            for k in {opencc('t2s').convert(term), opencc('s2t').convert(term)}:
                res.extend(node_by_key.get((app, k), []))
        return sorted(set(res))

    # anc_index[key] -> {node: (dist, first_hop_type, prov)}
    anc_index: dict[tuple, dict] = defaultdict(dict)
    origins: dict[tuple, dict] = {}
    direct: dict[tuple, dict] = defaultdict(dict)     # node -> {other: (type, prov)}

    def note_anc(key, node, dist, first_type, prov):
        cur = anc_index[key].get(node)
        if cur is None or dist < cur[0]:
            anc_index[key][node] = (dist, first_type, prov)

    def self_nodes(key):
        """Corpus lemmas that ARE the ancestor form (distance 0)."""
        lang, term = key
        if lang == 'han':
            return [n for n in han_nodes.get(term, []) if n[0] == 'zh']
        return [n for n in (nodes_for(lang, term) or []) if n[0] == alias2app.get(lang)]

    def self_insert(key):
        for n in self_nodes(key):
            note_anc(key, n, 0, None, 'self')

    han_nodes: dict[str, list] = defaultdict(list)
    for node, forms in han_of_node.items():
        for form, how in forms:
            if node not in han_nodes[form]:
                han_nodes[form].append(node)

    # 1. templates on the corpus lemmas' own entries
    node_hops: dict[tuple, list] = defaultdict(list)   # node -> [(key, dist, first_type, prov)]
    for l in app_langs:
        own_aliases = {l} | set(registry[l]['etymdbCodes'])
        if l == 'zh':
            own_aliases |= SINITIC_VARIETIES
        for lemma, entries in matches.get(l, {}).items():
            node = (l, lemma)
            best = None
            for e in entries:
                chain, cogs = hops_of_entry(e, l, learned)
                if not chain and not cogs:
                    continue
                if node in ambiguous:
                    chain = [h for h in chain if h['lang'] not in HAN_LANGS]
                    cogs = [c for c in cogs if c['lang'] not in HAN_LANGS]
                first_type = chain[0]['type'] if chain else None
                for hop in chain:
                    dist = hop['dist']
                    key = anc_key(hop['lang'], hop['term'])
                    if key is None:
                        continue
                    prov = f"wiktionary:{e['l']}:{e['w']}#{e.get('p')}:{hop['tpl']}|{hop['lang']}|{hop['term']}"
                    if hop['lang'] in own_aliases and not (l == 'zh' and hop['lang'] in ('ltc', 'och')):
                        continue  # same-language derivation is not an origin
                    note_anc(key, node, dist, first_type, prov)
                    node_hops[node].append((key, dist, first_type, prov))
                    rank = ANC_RANK.get(hop['lang'], UNLISTED_RANK)
                    cand = (dist, rank, hop['lang'], hop['term'], first_type, prov)
                    if best is None or (cand[0], cand[1]) < (best[0], best[1]):
                        best = cand
                for c in cogs:
                    for other in nodes_for(c['lang'], c['term']) or []:
                        if other != node and other[0] != l:
                            prov = f"wiktionary:{e['l']}:{e['w']}#{e.get('p')}:{c['tpl']}|{c['lang']}|{c['term']}"
                            direct[node].setdefault(other, ('cog', prov))
                            direct[other].setdefault(node, ('cog', prov))
            if best:
                dist, rank, alang, aterm, first_type, prov = best
                origins[node] = {'o': f"{lang_name(alang, learned)} {aterm}", 'ot': 'bor' if first_type == 'bor' else 'inh',
                                 'lang': alang, 'prov': prov}

    # 2. descendants lists of ancestor entries (Latin, Chinese …) — ancestor → descendant edges
    n_desc_links = 0
    for path in desc_paths:
        if not path.exists():
            continue
        with open(path, encoding='utf-8') as f:
            for line in f:
                try:
                    e = json.loads(line)
                except Exception:
                    continue
                w, lc = e.get('w'), e.get('l')
                if not w or not lc:
                    continue
                key = anc_key(lc, w)
                if key is None:
                    continue
                for dlc, dw, depth, borrowed in flatten_descendants(e.get('de')):
                    targets = nodes_for(dlc, dw)
                    if not targets:
                        continue
                    for node in targets:
                        if node[0] == alias2app.get(lc) or node in ambiguous:
                            continue
                        prov = f"wiktionary-descendants:{lc}:{w}#{e.get('p')}→{dlc}:{dw}"
                        note_anc(key, node, depth, 'bor' if borrowed else 'inh', prov)
                        n_desc_links += 1
    log(f"  descendants rows linked to corpus lemmas: {n_desc_links}")

    # 3. self-insertion (distance 0) for every ancestor key that is itself a corpus lemma, and one
    #    level of attested transitivity through corpus lemmas: A ← B (B a corpus lemma) and B ← C
    #    puts A under C at distance d(A,B) + d(B,C). Both hops are Wiktionary statements.
    for key in list(anc_index):
        self_insert(key)
    n_trans = 0
    for node, hops in list(node_hops.items()):
        for key, d_ab, t_a, p_a in hops:
            for b in self_nodes(key):
                if b == node:
                    continue
                for key2, d_bc, _t_b, p_b in node_hops.get(b, []):
                    if d_ab + d_bc > MAX_COMBINED_DIST or key2 == key:
                        continue
                    if node not in anc_index.get(key2, {}):
                        note_anc(key2, node, d_ab + d_bc, t_a, f"{p_a} → {p_b}")
                        n_trans += 1
    log(f"  transitive ancestor links through corpus lemmas: {n_trans}")

    # 4. shared-ancestor edges with the hub guard
    edges: dict[tuple, dict] = defaultdict(dict)   # node -> {other: (type, prov, direct?)}
    for node, others in direct.items():
        for other, (typ, prov) in others.items():
            edges[node][other] = (typ, prov, True)
    hubs = []
    for key, members in anc_index.items():
        if len(members) < 2:
            continue
        lst = sorted(members.items())
        langs_here = {n[0] for n, _ in lst}
        if len(langs_here) < 2:
            continue
        hub = len(lst) > HUB_LIMIT
        if hub:
            hubs.append((key, len(lst)))
        for i in range(len(lst)):
            for j in range(i + 1, len(lst)):
                (n1, (d1, t1, p1)), (n2, (d2, t2, p2)) = lst[i], lst[j]
                if n1[0] == n2[0] or d1 + d2 > MAX_COMBINED_DIST:
                    continue
                is_direct = d1 == 0 or d2 == 0
                if hub and not is_direct:
                    continue
                if is_direct:
                    t_other = t2 if d1 == 0 else t1
                    typ = 'bor' if t_other == 'bor' else 'der'
                else:
                    typ = 'cog'
                prov = f"shared-ancestor:{key[0]}:{key[1]} [{p1} | {p2}]"
                for a, b in ((n1, n2), (n2, n1)):
                    cur = edges[a].get(b)
                    if cur is None or (is_direct and not cur[2]):
                        edges[a][b] = (typ, prov, is_direct)
    log(f"  hub ancestors skipped for cognate edges (> {HUB_LIMIT} corpus descendants): {len(hubs)}"
        + (f" e.g. {hubs[:5]}" if hubs else ''))

    # 5. sinographic triples: corpus lemmas in different CJK languages attested with the same Han form
    sino = []
    for form, nodes in sorted(han_nodes.items()):
        by_lang = defaultdict(list)
        for n in nodes:
            by_lang[n[0]].append(n[1])
        if len(by_lang) >= 2:
            rec = {'han': form}
            for l in sorted(by_lang):
                rec[l] = sorted(set(by_lang[l]))
            rec['via'] = sorted({how for n in nodes for f, how in han_of_node[n] if f == form})
            sino.append(rec)
    return origins, edges, sino, learned, anc_index


# --------------------------------------------------------------------------------------
# EtymDB side — port of reference-prototype/etym.py (depth 3, ANC_PRIO, hub 40, cap 8)
# --------------------------------------------------------------------------------------
def build_etymdb(lexicon, app_langs, registry, etymdb_dir: Path):
    alias2app = {}
    for l in app_langs:
        for c in registry[l]['etymdbCodes']:
            alias2app[c] = l
        alias2app[l] = l
    s2t = opencc('s2t')

    def key_of(app, lex):
        if app == 'ar':
            return ar_norm(lex)
        if app in ('ja', 'ko', 'zh'):
            return CF_CHARS.sub('', lex)
        return CF_CHARS.sub('', lex).lower()

    id2 = {}
    appkey2ids = defaultdict(list)
    t0 = time.time()
    with open(etymdb_dir / 'etymdb_values.csv', encoding='utf-8') as f:
        for line in f:
            p = line.rstrip('\n').split('\t')
            if len(p) < 4:
                continue
            try:
                ix = int(p[0])
            except ValueError:
                continue
            lang, lex = p[1], p[3]
            if not lex:
                continue
            id2[ix] = (lang, lex)
            app = alias2app.get(lang)
            if app:
                appkey2ids[(app, key_of(app, lex))].append(ix)
    parents = defaultdict(list)
    children = defaultdict(list)
    multi = {}
    with open(etymdb_dir / 'etymdb_links_index.csv', encoding='utf-8') as f:
        for line in f:
            p = line.rstrip('\n').split('\t')
            try:
                multi[int(p[0])] = [int(x) for x in p[1:] if x]
            except ValueError:
                continue
    with open(etymdb_dir / 'etymdb_links_info.csv', encoding='utf-8') as f:
        for line in f:
            p = line.rstrip('\n').split('\t')
            if len(p) < 3:
                continue
            typ = p[0]
            try:
                c, pa = int(p[1]), int(p[2])
            except ValueError:
                continue
            for x in (multi.get(pa, [pa]) if pa < 0 else [pa]):
                parents[c].append((typ, x))
                children[x].append((typ, c))
    log(f"  EtymDB loaded: {len(id2)} values in {time.time() - t0:.1f}s")

    node_ids = {}
    for lang in app_langs:
        for lemma in lexicon[lang]:
            keys = set()
            if lang == 'ar':
                keys.add(ar_norm(lemma))
            elif lang == 'zh':
                keys.update([lemma, s2t.convert(lemma)])
            elif lang in ('ja', 'ko'):
                keys.add(lemma)
            else:
                keys.add(lemma.lower())
            ids = set()
            for k in keys:
                ids.update(appkey2ids.get((lang, k), []))
            if ids:
                node_ids[(lang, lemma)] = ids
    matched = defaultdict(int)
    for (lang, _) in node_ids:
        matched[lang] += 1
    log(f"  EtymDB matched per lang: {dict(matched)}")

    def up(ix, depth=ETYMDB_DEPTH):
        out = {}
        frontier = [(ix, 0, None)]
        seen = {ix}
        while frontier:
            cur, d, t0_ = frontier.pop()
            if d >= depth:
                continue
            for typ, pa in parents.get(cur, []):
                if pa in seen:
                    continue
                seen.add(pa)
                out[pa] = (d + 1, t0_ or typ)
                frontier.append((pa, d + 1, t0_ or typ))
        return out

    id2node = defaultdict(list)
    for node, ids in node_ids.items():
        for ix in ids:
            id2node[ix].append(node)

    origins, edges = {}, defaultdict(dict)
    anc_index = defaultdict(list)
    for node, ids in node_ids.items():
        best = None
        for ix in ids:
            anc_index[ix].append((node, 0, None))          # self-insertion (spec §5e fix 2)
            al0, alex0 = id2.get(ix, (None, None))
            # a zh lemma matched to a Middle/Old Chinese row IS that row's modern reflex (spec §5e fix 3)
            if node[0] == 'zh' and al0 in ('ltc', 'och') and al0 in ANC_RANK:
                cand = (ANC_RANK[al0], 0, al0, alex0, 'inh', ix)
                if best is None or (cand[1], cand[0]) < (best[1], best[0]):
                    best = cand
            for aix, (d, typ) in up(ix).items():
                al, alex = id2.get(aix, (None, None))
                if al is None:
                    continue
                anc_index[aix].append((node, d, typ))
                if (alex.startswith('-') or alex.endswith('-')) and not alex.startswith('*'):
                    continue      # EtymDB affix rows (-ité, -tion) are not origins
                if al != node[0] and alias2app.get(al) != node[0] and al in ANC_RANK:
                    cand = (ANC_RANK[al], d, al, alex, typ, aix)
                    if best is None or (cand[1], cand[0]) < (best[1], best[0]):
                        best = cand
            for typ, pa in parents.get(ix, []) + [(t, c) for t, c in children.get(ix, [])]:
                for other in id2node.get(pa, []):
                    if other != node and other[0] != node[0]:
                        edges[node].setdefault(other, (edge_type_of_hop(typ), f"etymdb:link:{typ}:{ix}~{pa}"))
        if best:
            origins[node] = {'o': f"{LANGNAME.get(best[2], best[2])} {CF_CHARS.sub('', best[3])}",
                             'ot': 'bor' if best[4] == 'bor' else 'inh', 'lang': best[2], 'prov': f"etymdb:value:{best[5]}"}
    for aix, lst in anc_index.items():
        if len(lst) < 2:
            continue
        if len({n[0] for n, d, t in lst}) < 2:
            continue
        if len(lst) > HUB_LIMIT:
            continue
        for i in range(len(lst)):
            for j in range(i + 1, len(lst)):
                (n1, d1, t1), (n2, d2, t2) = lst[i], lst[j]
                if n1[0] == n2[0] or d1 + d2 > MAX_COMBINED_DIST:
                    continue
                if d1 == 0 or d2 == 0:
                    t_other = t2 if d1 == 0 else t1
                    typ = 'bor' if t_other == 'bor' else 'der'
                else:
                    typ = 'cog'
                prov = f"etymdb:shared-ancestor:{aix}"
                edges[n1].setdefault(n2, (typ, prov))
                edges[n2].setdefault(n1, (typ, prov))
    return origins, edges, dict(matched)


# --------------------------------------------------------------------------------------
# Union, emit, report
# --------------------------------------------------------------------------------------
def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--lexicon', default=str(DEFAULT_LEXICON))
    ap.add_argument('--languages', default=None, help='comma-separated ISO codes (default: every lexicon language in the registry)')
    ap.add_argument('--kaikki-dir', default=str(DEFAULT_KAIKKI_DIR))
    ap.add_argument('--etymdb-dir', default=str(DEFAULT_ETYMDB_DIR))
    ap.add_argument('--desc-dir', default=str(DEFAULT_KAIKKI_DIR / 'raw'), help='dir with <Language>.descendants.jsonl')
    ap.add_argument('--out-dir', default=str(BUILD_DIR))
    ap.add_argument('--workers', type=int, default=2)
    ap.add_argument('--no-etymdb', action='store_true')
    ap.add_argument('--no-wiktionary', action='store_true')
    ap.add_argument('--edge-objects', action='store_true',
                    help='emit edges as {"lang","lemma","type"} objects (kotlinx default for EtymEdge) instead of [lang, lemma, type] arrays')
    args = ap.parse_args(argv)

    t_all = time.time()
    timings = {}
    out_dir = Path(args.out_dir)
    enrich_dir, pack_dir = out_dir / 'enrich', out_dir / 'packs' / 'etymology'
    enrich_dir.mkdir(parents=True, exist_ok=True)
    pack_dir.mkdir(parents=True, exist_ok=True)

    lexicon = load_lexicon(args.lexicon)
    want = args.languages.split(',') if args.languages else list(lexicon)
    registry = load_registry(want)
    app_langs = [l for l in want if l in lexicon and l in registry]
    log(f"languages: {app_langs}")

    # ---- Wiktionary
    matches, kstats = {}, {}
    w_origins, w_edges, sino, learned, w_anc = {}, {}, [], {}, {}
    if not args.no_wiktionary:
        t0 = time.time()
        log("matching lexicon against kaikki extracts …")
        matches, kstats = collect_kaikki_matches(lexicon, registry, Path(args.kaikki_dir), enrich_dir, args.workers, app_langs)
        timings['kaikki_match'] = round(time.time() - t0, 1)
        t0 = time.time()
        desc_paths = [Path(args.desc_dir) / f"{registry[l]['kaikkiName']}.descendants.jsonl" for l in ('la', 'zh')
                      if l in registry and registry[l].get('kaikkiName')]
        desc_paths += sorted(set(Path(args.desc_dir).glob('*.descendants.jsonl')) - set(desc_paths))
        w_origins, w_edges, sino, learned, w_anc = build_wiktionary(lexicon, app_langs, registry, matches, desc_paths, None)
        timings['wiktionary_graph'] = round(time.time() - t0, 1)
        log(f"  wiktionary: {len(w_origins)} origins, {sum(len(v) for v in w_edges.values())} edge endpoints")

    # ---- EtymDB
    e_origins, e_edges, e_matched = {}, {}, {}
    if not args.no_etymdb and Path(args.etymdb_dir).exists():
        t0 = time.time()
        log("EtymDB …")
        e_origins, e_edges, e_matched = build_etymdb(lexicon, app_langs, registry, Path(args.etymdb_dir))
        timings['etymdb_graph'] = round(time.time() - t0, 1)
        log(f"  etymdb: {len(e_origins)} origins, {sum(len(v) for v in e_edges.values())} edge endpoints")

    # ---- IPA from the matched entries (first IPA, prefer untagged)
    ipa_of = {}
    for l in app_langs:
        for lemma, entries in matches.get(l, {}).items():
            best = None
            for e in entries:
                for s in e.get('so') or []:
                    if s.get('ipa'):
                        cand = (1 if s.get('tags') else 0, s['ipa'])
                        if best is None or cand < best:
                            best = cand
            if best:
                ipa_of[(l, lemma)] = best[1]

    # ---- union
    nodes = set(w_origins) | set(w_edges) | set(e_origins) | set(e_edges)
    out = defaultdict(dict)
    prov_lines = []
    edges_by_type = defaultdict(int)
    edge_src = defaultdict(int)
    origin_src = defaultdict(int)
    for node in sorted(nodes):
        lang, lemma = node
        rec = {}
        srcs = set()
        o = w_origins.get(node) or e_origins.get(node)
        if o:
            rec['o'], rec['ot'] = o['o'], o['ot']
            src = 'wiktionary' if node in w_origins else 'etymdb'
            srcs.add(src)
            origin_src[src] += 1
            prov_lines.append({'lang': lang, 'lemma': lemma, 'fact': 'origin', 'value': o['o'], 'type': o['ot'], 'src': src, 'via': o['prov']})
        merged = {}
        for other, (typ, prov, *_rest) in sorted(w_edges.get(node, {}).items()):
            merged[other] = (typ, prov, 'wiktionary', 0 if (_rest and _rest[0]) else 1)
        for other, (typ, prov) in sorted(e_edges.get(node, {}).items()):
            if other not in merged:
                merged[other] = (typ, prov, 'etymdb', 2)
        ordered = sorted(merged.items(), key=lambda kv: (kv[1][3], kv[0][0], kv[0][1]))[:EDGE_CAP]
        if ordered:
            if args.edge_objects:
                rec['e'] = [{'lang': o_[0], 'lemma': o_[1], 'type': t} for o_, (t, _p, _s, _r) in ordered]
            else:
                rec['e'] = [[o_[0], o_[1], t] for o_, (t, _p, _s, _r) in ordered]
            for o_, (t, p, s, _r) in ordered:
                srcs.add(s)
                edges_by_type[t] += 1
                edge_src[s] += 1
                prov_lines.append({'lang': lang, 'lemma': lemma, 'fact': 'edge', 'value': [o_[0], o_[1], t], 'src': s, 'via': p})
                if s == 'wiktionary' and o_ in e_edges.get(node, {}):
                    # the same edge is also attested in EtymDB: record the corroborating row
                    srcs.add('etymdb')
                    prov_lines.append({'lang': lang, 'lemma': lemma, 'fact': 'edge', 'value': [o_[0], o_[1], t], 'src': 'etymdb',
                                       'via': e_edges[node][o_][1], 'corroborates': True})
        if not rec:
            continue
        if node in e_origins and 'o' in rec and node in w_origins:
            srcs.add('etymdb')       # EtymDB also has an origin for this lemma (Wiktionary's was preferred)
            prov_lines.append({'lang': lang, 'lemma': lemma, 'fact': 'origin', 'value': e_origins[node]['o'], 'type': e_origins[node]['ot'],
                               'src': 'etymdb', 'via': e_origins[node]['prov'], 'superseded_by': 'wiktionary'})
        rec['src'] = '+'.join(s for s in ('wiktionary', 'etymdb') if s in srcs)
        if node in ipa_of:
            rec['ipa'] = ipa_of[node]
        out[lang][lemma] = rec

    # ---- write
    etym_path = pack_dir / 'etym.json.gz'
    size = write_json_gz(etym_path, {l: out.get(l, {}) for l in app_langs})
    with open(enrich_dir / 'etym_provenance.jsonl', 'w', encoding='utf-8') as f:
        for line in prov_lines:
            f.write(json.dumps(line, ensure_ascii=False) + '\n')
    write_json(enrich_dir / 'sinographic.json', {
        'note': 'Corpus lemmas in different CJK languages attested (Wiktionary headword / hanja form / kyūjitai form) with the same traditional Han string.',
        'triples': [r for r in sino if all(k in r for k in ('ja', 'ko', 'zh'))],
        'pairs': [r for r in sino if not all(k in r for k in ('ja', 'ko', 'zh'))],
    })

    # ---- coverage report
    edb_doc_numbers = {'en': 51.4, 'es': 36.8, 'fr': 35.0, 'nl': 33.1, 'it': 31.5, 'de': 27.4, 'ar': 4.5, 'ja': 2.9, 'ko': 2.8, 'zh': 0.6}
    any_ety_text = {'ko': 70.1, 'ar': 40.3, 'ja': 27.6, 'zh': 24.5, 'es': 51.9, 'fr': 48.8, 'it': 47.3, 'de': 49.9, 'nl': 49.1, 'en': 75.8}
    per = {}
    top_origin_lang = defaultdict(lambda: defaultdict(int))
    for l in app_langs:
        recs = out.get(l, {})
        n = len(lexicon[l])
        with_o = sum(1 for r in recs.values() if 'o' in r)
        with_e = sum(1 for r in recs.values() if r.get('e'))
        ebt = defaultdict(int)
        for r in recs.values():
            for _, _, t in r.get('e', []):
                ebt[t] += 1
        wo = sum(1 for k in w_origins if k[0] == l)
        eo = sum(1 for k in e_origins if k[0] == l)
        matched = kstats.get(l, {}).get('matched', 0)
        with_any_tpl = sum(1 for lemma, es in matches.get(l, {}).items() if any(e.get('tp') or e.get('et') for e in es))
        for lemma, r in recs.items():
            if 'o' in r:
                o = w_origins.get((l, lemma)) or e_origins.get((l, lemma))
                top_origin_lang[l][lang_name(o['lang'], learned)] += 1
        per[l] = {
            'lemmas': n, 'kaikki_matched': matched, 'kaikki_with_etymology_section': with_any_tpl,
            'with_origin': with_o, 'with_edges': with_e, 'in_graph': len(recs),
            'pct_origin': round(100 * with_o / max(1, n), 1), 'pct_edges': round(100 * with_e / max(1, n), 1),
            'pct_in_graph': round(100 * len(recs) / max(1, n), 1),
            'pct_any_etymology_text_measured': round(100 * with_any_tpl / max(1, n), 1),
            'origins_wiktionary': wo, 'origins_etymdb': eo,
            'edges_by_type': dict(ebt), 'edge_endpoints': sum(ebt.values()),
            'top_origin_languages': sorted(top_origin_lang[l].items(), key=lambda kv: -kv[1])[:8],
            'etymdb_only_docs_pct': edb_doc_numbers.get(l), 'any_etymology_text_docs_pct': any_ety_text.get(l),
            'etymdb_matched': e_matched.get(l, 0),
        }
    # global
    all_top = defaultdict(int)
    for l in top_origin_lang:
        for k, v in top_origin_lang[l].items():
            all_top[k] += v
    triples = sum(1 for r in sino if all(k in r for k in ('ja', 'ko', 'zh')))
    coverage = {
        'lexicon': str(args.lexicon), 'languages': app_langs, 'per_language': per,
        'edges_by_type': dict(edges_by_type), 'edges_by_source': dict(edge_src), 'origins_by_source': dict(origin_src),
        'origin_type': {'inh': sum(1 for l in out for r in out[l].values() if r.get('ot') == 'inh'),
                        'bor': sum(1 for l in out for r in out[l].values() if r.get('ot') == 'bor')},
        'top_origin_languages': sorted(all_top.items(), key=lambda kv: -kv[1])[:16],
        'sinographic': {'triples_ja_ko_zh': triples, 'pairs': len(sino) - triples},
        'kaikki_match_stats': kstats, 'etymdb_matched': e_matched,
        'hub_limit': HUB_LIMIT, 'edge_cap': EDGE_CAP, 'max_combined_distance': MAX_COMBINED_DIST,
        'timings_seconds': timings, 'etym_json_gz_bytes': size,
    }
    timings['total'] = round(time.time() - t_all, 1)
    write_json(enrich_dir / 'etym_coverage.json', coverage)

    # manifest fragment
    stats = {l: {'lemmas': per[l]['lemmas'], 'origins': per[l]['with_origin'], 'withEdges': per[l]['with_edges'],
                 'edgeEndpoints': per[l]['edge_endpoints']} for l in app_langs}
    frag = manifest_fragment('ETYMOLOGY', 'clavis-etymology', 'Etymology graph (Wiktionary + EtymDB)', app_langs,
                             'CC BY-SA 4.0', 'https://kaikki.org/dictionary/ (wiktextract, Wiktionary CC BY-SA 4.0); https://github.com/clefourrier/EtymDB (EtymDB 2.1, CC BY-SA 4.0)',
                             [ATTR_WIKTIONARY, ATTR_ETYMDB], {'etym.json.gz': size}, stats)
    write_json(pack_dir / 'manifest-fragment.json', frag)

    # table
    print(f"\n{'lang':4} {'lemmas':>6} {'kaikki':>6} {'origin':>6} {'%':>5} {'edges':>6} {'%':>5} {'graph%':>6} {'cog':>5} {'bor':>5} {'der':>5} {'EtymDB-docs%':>12} {'anyEtyText%':>11} top origins")
    for l in app_langs:
        p = per[l]
        e = p['edges_by_type']
        top = ', '.join(f"{k} {v}" for k, v in p['top_origin_languages'][:4])
        print(f"{l:4} {p['lemmas']:6d} {p['kaikki_matched']:6d} {p['with_origin']:6d} {p['pct_origin']:5.1f} {p['with_edges']:6d} {p['pct_edges']:5.1f} {p['pct_in_graph']:6.1f} "
              f"{e.get('cog', 0):5d} {e.get('bor', 0):5d} {e.get('der', 0):5d} {str(p['etymdb_only_docs_pct'] or '-'):>12} {str(p['any_etymology_text_docs_pct'] or '-'):>11} {top}")
    print(f"\nedges by type {dict(edges_by_type)}; by source {dict(edge_src)}; origins by source {dict(origin_src)}; "
          f"sinographic triples {triples} (+{len(sino) - triples} pairs); {etym_path} {size} B; timings {timings}")
    return coverage


if __name__ == '__main__':
    main()
