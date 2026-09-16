"""Annotate scenes: tokenize, lemmatize, frequency-rank, and draw heuristic bridge edges.

Reads  scenes_raw.json  ->  writes scenes.json (+ lexicon.json)
"""
import json, re, unicodedata, sys
from collections import defaultdict, Counter
import wordfreq, simplemma, fugashi, opencc
from pypinyin import pinyin as _pinyin, Style as _Style

# ---------- romanization helpers ----------
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
    try: return ' '.join(x[0] for x in _pinyin(w, style=_Style.TONE))
    except Exception: return ''

RAW = json.load(open('/home/claude/pipeline/scenes_raw.json'))
LATIN = ['en', 'es', 'de', 'fr', 'it', 'nl']
CLUSTER = {'es': ['fr', 'it'], 'fr': ['es', 'it'], 'it': ['es', 'fr'], 'de': ['nl'], 'nl': ['de']}
tagger = fugashi.Tagger()
s2t = opencc.OpenCC('s2t')

AR_DIAC = re.compile(r'[\u0610-\u061A\u064B-\u0652\u0670\u0640\u06D6-\u06ED]')
def ar_norm(w):
    w = AR_DIAC.sub('', w)
    w = re.sub('[إأآٱ]', 'ا', w)
    return w

def strip_diac(w):
    return ''.join(c for c in unicodedata.normalize('NFD', w) if unicodedata.category(c) != 'Mn').lower()

WORD_RE = re.compile(r"[^\W\d_]+(?:[''’\-][^\W\d_]+)*", re.UNICODE)
AR_RE = re.compile(r'[؀-ۿ]+')

def zipf(w, lang):
    return round(wordfreq.zipf_frequency(w, lang), 2)

def tok_latin(text, lang):
    toks = []
    for m in WORD_RE.finditer(text):
        s = m.group(0)
        lem = simplemma.lemmatize(s.lower(), lang=lang) if lang != 'en' else simplemma.lemmatize(s.lower(), lang='en')
        lem = lem.lower()
        z = max(zipf(lem, lang), zipf(s.lower(), lang))
        toks.append({'s': s, 'l': lem, 'z': z})
    return toks

def tok_ja(text):
    toks = []
    for w in tagger(text):
        pos = w.feature.pos1
        if pos in ('補助記号', '記号', '空白') or not w.surface.strip():
            continue
        lem = getattr(w.feature, 'orthBase', None) or w.feature.lemma or w.surface
        if not lem or lem == '*': lem = w.surface
        z = max(zipf(lem, 'ja'), zipf(w.surface, 'ja'))
        kana = getattr(w.feature, 'pron', None) or getattr(w.feature, 'kana', None) or ''
        toks.append({'s': w.surface, 'l': lem, 'z': z, 'p': pos, 'r': kata2roma(kana) if kana and kana != '*' else ''})
    return toks

def tok_zh(text):
    toks = []
    for s in wordfreq.tokenize(text, 'zh'):
        if not re.search(r'[一-鿿]', s):
            continue
        toks.append({'s': s, 'l': s, 'z': zipf(s, 'zh'), 't': s2t.convert(s), 'r': zh2pinyin(s)})
    return toks

def tok_ko(text):
    toks = []
    for s in wordfreq.tokenize(text, 'ko'):
        if not re.search(r'[가-힣]', s):
            continue
        toks.append({'s': s, 'l': s, 'z': zipf(s, 'ko'), 'r': hangul2rr(s)})
    return toks

def tok_ar(text):
    toks = []
    for m in AR_RE.finditer(text):
        s = m.group(0)
        n = ar_norm(s)
        if len(n) < 2:
            continue
        z = zipf(n, 'ar')
        # try stripping proclitic waw / definite article for a frequency fallback
        alt = []
        if n.startswith('و') and len(n) > 3: alt.append(n[1:])
        if n.startswith('ال') and len(n) > 4: alt.append(n[2:])
        if n.startswith('وال') and len(n) > 5: alt.append(n[3:])
        zalt = max([zipf(a, 'ar') for a in alt], default=0)
        toks.append({'s': s, 'l': n, 'z': max(z, zalt), 'r': ar2roman(s)})
    return toks

def lev(a, b):
    if a == b: return 0
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]

def sim(a, b):
    a, b = strip_diac(a), strip_diac(b)
    if not a or not b: return 0.0
    return 1 - lev(a, b) / max(len(a), len(b))

TOK = {l: (lambda t, l=l: tok_latin(t, l)) for l in LATIN}
TOK.update({'ja': tok_ja, 'zh': tok_zh, 'ko': tok_ko, 'ar': tok_ar})

scenes = []
lexicon = defaultdict(lambda: defaultdict(lambda: {'n': 0, 'z': 0, 's': None}))
for sc in RAW:
    en_toks = tok_latin(sc['texts']['en'], 'en')
    if sum(1 for t in en_toks if t['z'] >= 2.0 and len(t['s']) >= 2) < 2:
        continue   # sound effects / junk
    if re.search(r'creative commons|http|www\.|patreon|revoy|translat|proofread|correction|license|licence|font|sources? files|special thanks|credits', sc['texts']['en'], re.I):
        continue   # credits / meta text
    toks = {}
    for lang, text in sc['texts'].items():
        toks[lang] = TOK[lang](text)
    # bridges
    for lang in toks:
        if lang == 'en':
            continue
        for t in toks[lang]:
            br = []
            if lang in LATIN:
                if len(t['l']) < 4:
                    continue
                for bl in ['en'] + CLUSTER.get(lang, []):
                    if bl not in toks: continue
                    best = None
                    for u in toks[bl]:
                        if len(u['l']) < 4: continue
                        s1 = sim(t['l'], u['l']); s2 = sim(t['s'], u['s'])
                        s = max(s1, s2)
                        same_head = strip_diac(t['l'])[:1] == strip_diac(u['l'])[:1]
                        if s >= 0.67 and same_head and (best is None or s > best[1]):
                            best = (u['s'], s)
                    if best:
                        br.append({'v': bl, 'w': best[0], 'k': round(best[1], 2)})
            elif lang == 'ja' and 'zh' in toks:
                kanji = set(re.findall(r'[一-鿿]', t['l']))
                if kanji:
                    zh_chars = set(''.join(u['s'] + u.get('t', '') for u in toks['zh']))
                    shared = ''.join(sorted(kanji & zh_chars))
                    if shared:
                        br.append({'v': 'zh', 'w': shared, 'k': 1.0})
            elif lang == 'zh' and 'ja' in toks:
                chars = set(t['s']) | set(t.get('t', ''))
                ja_chars = set(''.join(u['l'] + u['s'] for u in toks['ja']))
                shared = ''.join(sorted(chars & ja_chars))
                if shared:
                    br.append({'v': 'ja', 'w': shared, 'k': 1.0})
            if br:
                t['b'] = br
    for lang, ts in toks.items():
        for t in ts:
            e = lexicon[lang][t['l']]
            e['n'] += 1; e['z'] = t['z']; e['s'] = e['s'] or t['s']
            if t.get('r') and not e.get('r'): e['r'] = t['r']
    clampr = lambda r: [min(max(v, 0.0), 1.0) for v in r]
    scenes.append({'id': len(scenes), 'ep': sc['ep'], 'page': sc['page'], 'img': sc['img'],
                   'rect': clampr(sc['rect']),
                   'rects': {l: clampr(r) for l, r in sc.get('rects', {}).items()},
                   'src': sc.get('src', 'flow'),
                   'texts': sc['texts'], 'toks': toks})

json.dump(scenes, open('/home/claude/pipeline/scenes.json', 'w'), ensure_ascii=False)
lex_out = {l: {w: [v['n'], v['z'], v['s'], v.get('r', '')] for w, v in d.items()} for l, d in lexicon.items()}
json.dump(lex_out, open('/home/claude/pipeline/lexicon.json', 'w'), ensure_ascii=False)
print(len(scenes), 'scenes')
for l, d in lexicon.items():
    print(l, 'lemmas:', len(d), 'tokens:', sum(v['n'] for v in d.values()))
nb = Counter(l for s in scenes for l, ts in s['toks'].items() for t in ts if t.get('b'))
print('bridged tokens:', dict(nb))
