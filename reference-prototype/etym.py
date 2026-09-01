"""Extract real etymological relations for the corpus lemmas from EtymDB 2.1 (CC BY-SA 4.0).

Output: etym.json  {lang: {lemma: {"o": "Latin clavis", "e": [[lang2, lemma2, type], ...]}}}
"""
import json, re, unicodedata, sys
from collections import defaultdict
import opencc

SCR = '/tmp/claude-0/-home-claude/644890e0-610b-5c9b-a531-2c56573a8958/scratchpad/etymdb/data/split_etymdb'
LEX = json.load(open('/home/claude/pipeline/lexicon.json'))
APP = ['es','fr','it','de','nl','ja','ko','zh','ar','en']
s2t = opencc.OpenCC('s2t')

LANGNAME = {'la':'Latin','grc':'Ancient Greek','sa':'Sanskrit','fa':'Persian','ar':'Arabic','fro':'Old French','frm':'Middle French',
 'enm':'Middle English','ang':'Old English','pt':'Portuguese','hi':'Hindi','ur':'Urdu','ota':'Ottoman Turkish','tr':'Turkish',
 'nds':'Low German','dum':'Middle Dutch','odt':'Old Dutch','goh':'Old High German','gmh':'Middle High German','non':'Old Norse',
 'osp':'Old Spanish','VL.':'Vulgar Latin','LL.':'Late Latin','ML.':'Medieval Latin','ine-pro':'Proto-Indo-European',
 'gem-pro':'Proto-Germanic','gmw-pro':'Proto-West Germanic','itc-pro':'Proto-Italic','sla-pro':'Proto-Slavic',
 'ltc':'Middle Chinese','och':'Old Chinese','oko':'Old Korean','ojp':'Old Japanese','arc':'Aramaic','he':'Hebrew',
 'es':'Spanish','fr':'French','it':'Italian','de':'German','nl':'Dutch','ja':'Japanese','ko':'Korean','zh':'Chinese','en':'English',
 'grk-pro':'Proto-Hellenic','sem-pro':'Proto-Semitic','ine-bsl-pro':'Proto-Balto-Slavic','roa-opt':'Old Portuguese','xno':'Anglo-Norman',
 'frk':'Frankish','got':'Gothic','egy':'Egyptian','cop':'Coptic','san':'Sanskrit','pal':'Middle Persian','peo':'Old Persian','nan':'Min Nan','yue':'Cantonese','cmn':'Mandarin','mul':'translingual'}

AR_DIAC = re.compile(r'[\u0610-\u061A\u064B-\u0652\u0670\u0640\u06D6-\u06ED]')
def ar_norm(w):
    w = AR_DIAC.sub('', w); return re.sub('[إأآٱ]', 'ا', w)
def strip_diac(w):
    return ''.join(c for c in unicodedata.normalize('NFD', w) if unicodedata.category(c) != 'Mn').lower()

# ---- 1. load values
id2 = {}
appkey2ids = defaultdict(list)   # (applang, key) -> [ids]
print('loading values...', file=sys.stderr)
with open(f'{SCR}/etymdb_values.csv', encoding='utf-8') as f:
    for line in f:
        p = line.rstrip('\n').split('\t')
        if len(p) < 4: continue
        try: ix = int(p[0])
        except ValueError: continue
        lang, lex = p[1], p[3]
        if not lex: continue
        id2[ix] = (lang, lex)
        if lang in APP:
            if lang == 'ar': key = ar_norm(lex)
            elif lang in ('ja','ko','zh'): key = lex
            else: key = lex.lower()
            appkey2ids[(lang, key)].append(ix)
print('values', len(id2), file=sys.stderr)

# ---- 2. links (child, parent)
parents = defaultdict(list); children = defaultdict(list)
multi = {}
with open(f'{SCR}/etymdb_links_index.csv', encoding='utf-8') as f:
    for line in f:
        p = line.rstrip('\n').split('\t')
        try: multi[int(p[0])] = [int(x) for x in p[1:] if x]
        except ValueError: continue
nlinks = 0
with open(f'{SCR}/etymdb_links_info.csv', encoding='utf-8') as f:
    for line in f:
        p = line.rstrip('\n').split('\t')
        if len(p) < 3: continue
        typ = p[0]
        try: c = int(p[1]); pa = int(p[2])
        except ValueError: continue
        pas = multi.get(pa, [pa]) if pa < 0 else [pa]
        for x in pas:
            parents[c].append((typ, x)); children[x].append((typ, c)); nlinks += 1
print('links', nlinks, file=sys.stderr)

# ---- 3. match corpus lemmas to ids
node_ids = {}   # (lang, lemma) -> set(ids)
for lang, entries in LEX.items():
    for lemma in entries:
        keys = set()
        if lang == 'ar': keys.add(ar_norm(lemma))
        elif lang == 'zh': keys.update([lemma, s2t.convert(lemma)])
        elif lang in ('ja','ko'): keys.add(lemma)
        else: keys.add(lemma.lower())
        ids = set()
        for k in keys: ids.update(appkey2ids.get((lang, k), []))
        if lang != 'en' and not ids and lang not in ('ja','ko','zh','ar'):
            # try diacritic-stripped match (EtymDB has accents; we should too, but be lenient)
            pass
        if ids: node_ids[(lang, lemma)] = ids
matched = defaultdict(int)
for (lang, lemma) in node_ids: matched[lang] += 1
print('matched per lang:', dict(matched), 'of', {l: len(v) for l, v in LEX.items()}, file=sys.stderr)

# ---- 4. ancestor walks
def up(ix, depth=3):
    """Return {ancestor_id: (dist, type_of_first_hop)} walking child->parent."""
    out = {}; frontier = [(ix, 0, None)]
    seen = {ix}
    while frontier:
        cur, d, t0 = frontier.pop()
        if d >= depth: continue
        for typ, pa in parents.get(cur, []):
            if pa in seen: continue
            seen.add(pa)
            out[pa] = (d+1, t0 or typ)
            frontier.append((pa, d+1, t0 or typ))
    return out

id2node = defaultdict(list)
for node, ids in node_ids.items():
    for ix in ids: id2node[ix].append(node)

ANC_PRIO = ['la','VL.','LL.','ML.','grc','sa','fa','ar','ltc','och','fro','frm','xno','enm','ang','non','goh','gmh','dum','odt','frk','got','osp','roa-opt','pt','ota','tr','he','arc','egy','gem-pro','gmw-pro','itc-pro','ine-pro','sem-pro']
anc_rank = {l: i for i, l in enumerate(ANC_PRIO)}

origins = {}; edges = defaultdict(set)
anc_index = defaultdict(list)   # ancestor id -> [(node, dist, typ)]
for node, ids in node_ids.items():
    best = None
    for ix in ids:
        ups = up(ix)
        for aix, (d, typ) in ups.items():
            al, alex = id2.get(aix, (None, None))
            if al is None: continue
            anc_index[aix].append((node, d, typ))
            if al != node[0] and al in anc_rank:
                cand = (anc_rank[al], d, al, alex, typ)
                if best is None or (cand[1], cand[0]) < (best[1], best[0]): best = cand
        # direct links to other app-language corpus nodes
        for typ, pa in parents.get(ix, []) + [(t, c) for t, c in children.get(ix, [])]:
            for other in id2node.get(pa, []):
                if other != node and other[0] != node[0]:
                    edges[node].add((other, 'bor' if typ == 'bor' else ('cog' if typ == 'cog' else 'der')))
    if best:
        origins[node] = (best[2], best[3], best[4])

# shared-ancestor cognate edges
for aix, lst in anc_index.items():
    if len(lst) < 2: continue
    langs_here = {n[0] for n, d, t in lst}
    if len(langs_here) < 2: continue
    if len(lst) > 40: continue   # hub ancestors (e.g. PIE roots) create hairballs; skip giant hubs
    for i in range(len(lst)):
        for j in range(i+1, len(lst)):
            (n1, d1, t1), (n2, d2, t2) = lst[i], lst[j]
            if n1[0] == n2[0]: continue
            if d1 + d2 > 4: continue
            edges[n1].add((n2, 'cog')); edges[n2].add((n1, 'cog'))

# ---- 5. emit
out = defaultdict(dict)
for node, ids in node_ids.items():
    lang, lemma = node
    rec = {}
    if node in origins:
        al, alex, typ = origins[node]
        rec['o'] = f"{LANGNAME.get(al, al)} {alex}"
        rec['ot'] = 'bor' if typ == 'bor' else 'inh'
    ee = sorted(edges.get(node, set()), key=lambda x: (x[0][0], x[0][1]))[:8]
    if ee:
        rec['e'] = [[o[0], o[1], t] for (o, t) in ee]
    if rec:
        out[lang][lemma] = rec
json.dump(out, open('/home/claude/pipeline/etym.json', 'w'), ensure_ascii=False)
tot_o = sum(1 for l in out for w in out[l] if 'o' in out[l][w])
tot_e = sum(len(out[l][w].get('e', [])) for l in out for w in out[l])
print('nodes with origin:', tot_o, 'edge endpoints:', tot_e, file=sys.stderr)
for l in APP:
    if l in out:
        no = sum(1 for w in out[l] if 'o' in out[l][w]); ne = sum(1 for w in out[l] if out[l][w].get('e'))
        print(l, 'origins', no, 'with-edges', ne, file=sys.stderr)
