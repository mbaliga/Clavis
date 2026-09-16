"""Shared helpers for the Clavis enrichment stages (etymology, pronunciations, sentences).

Owned by the enrichment stages only; the core pipeline (extract/annotate/pack_assets) does not
import this module. Everything here is deterministic and network-free except
`commons_licences()` which is only used by enrich_pronunciations.py.

Contents
  - paths and defaults (all overridable by CLI arguments in the stage scripts)
  - the language registry (pipeline/languages.json when present, else a built-in map)
  - script-aware key normalisation shared by the Wiktionary and EtymDB matchers
  - lexicon-side match-candidate generation (Arabic clitics, Korean endings, Chinese variants)
  - tokenisers that mirror annotate.py (simplemma / fugashi / mecab-ko via wordfreq / jieba)
  - the kaikki (wiktextract) streaming matcher with an on-disk cache
  - licence classification for Commons / Tatoeba audio
  - PackManifest fragment helpers
"""
from __future__ import annotations

import gzip
import hashlib
import json
import os
import re
import sys
import time
import unicodedata
from collections import defaultdict
from pathlib import Path

# --------------------------------------------------------------------------------------
# Paths
# --------------------------------------------------------------------------------------
PIPELINE_DIR = Path(__file__).resolve().parent
BUILD_DIR = PIPELINE_DIR / "build"
ENRICH_DIR = BUILD_DIR / "enrich"
PACKS_DIR = BUILD_DIR / "packs"
SCRATCH = Path(os.environ.get(
    "CLAVIS_SCRATCH",
    "/tmp/claude-0/-home-user/a172edd5-f0ba-5ae2-9346-def044613729/scratchpad"))
DEFAULT_KAIKKI_DIR = SCRATCH / "data" / "kaikki"
DEFAULT_ETYMDB_DIR = SCRATCH / "etymdb" / "data" / "split_etymdb"
DEFAULT_TATOEBA_DIR = SCRATCH / "data" / "tatoeba"
DEFAULT_LEXICON = SCRATCH / "pipeline-run" / "lexicon.json"
REGISTRY_PATH = PIPELINE_DIR / "languages.json"

USER_AGENT = "Clavis-pipeline/0.1 (https://github.com/mbaliga/Clavis)"
PACK_VERSION = time.strftime("%Y.%m.%d")


def log(*a):
    print(*a, file=sys.stderr, flush=True)


# --------------------------------------------------------------------------------------
# Language registry
# --------------------------------------------------------------------------------------
# Built-in fallback used when pipeline/languages.json is absent or lacks the enrichment
# fields. iso -> (kaikki dump name, EtymDB language codes that count as this language,
# Tatoeba ISO-639-3, tokenizer, script)
BUILTIN = {
    "en": ("English", ["en"], "eng", "latin", "Latn"),
    "es": ("Spanish", ["es"], "spa", "latin", "Latn"),
    "fr": ("French", ["fr"], "fra", "latin", "Latn"),
    "it": ("Italian", ["it"], "ita", "latin", "Latn"),
    "de": ("German", ["de"], "deu", "latin", "Latn"),
    "nl": ("Dutch", ["nl"], "nld", "latin", "Latn"),
    "pt": ("Portuguese", ["pt"], "por", "latin", "Latn"),
    "la": ("Latin", ["la", "la-vul", "la-lat", "la-med", "la-new", "la-ecc", "la-ren"], "lat", "latin", "Latn"),
    "ru": ("Russian", ["ru"], "rus", "latin", "Cyrl"),
    "pl": ("Polish", ["pl"], "pol", "latin", "Latn"),
    "uk": ("Ukrainian", ["uk"], "ukr", "latin", "Cyrl"),
    "tr": ("Turkish", ["tr"], "tur", "latin", "Latn"),
    "hi": ("Hindi", ["hi"], "hin", "latin", "Deva"),
    "ja": ("Japanese", ["ja"], "jpn", "fugashi-unidic", "Jpan"),
    "ko": ("Korean", ["ko"], "kor", "mecab-ko", "Hang"),
    "zh": ("Chinese", ["zh", "cmn", "ltc", "och", "lzh"], "cmn", "jieba", "Hans"),
    "ar": ("Arabic", ["ar"], "ara", "arabic", "Arab"),
}


def load_registry(extra_languages: list[str] | None = None) -> dict[str, dict]:
    """Return {iso: {kaikkiName, etymdbCodes, tatoebaCode, tokenizer, script}}.

    Reads pipeline/languages.json when it exists (any of the field spellings
    kaikkiName/kaikkiLang, etymdbCode/etymCodes, tatoebaCode/tatoebaLang are accepted) and
    fills gaps from BUILTIN. `extra_languages` (from --languages) are added from BUILTIN.
    """
    reg: dict[str, dict] = {}
    if REGISTRY_PATH.exists():
        try:
            doc = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
            langs = doc.get("languages", doc) if isinstance(doc, dict) else doc
            if isinstance(langs, dict):
                langs = [dict(v, iso=k) for k, v in langs.items()]
            for l in langs:
                iso = l.get("iso") or l.get("code")
                if not iso:
                    continue
                b = BUILTIN.get(iso)
                codes = l.get("etymdbCodes") or l.get("etymCodes") or l.get("etymdbCode") or []
                if isinstance(codes, str):
                    codes = [codes]
                codes = list(codes)
                for c in (b[1] if b else [iso]):        # historical stages (ltc, la-vul …) always count
                    if c not in codes:
                        codes.append(c)
                tk = (l.get("tokenizer") or (b[3] if b else "latin")).lower()
                lem = l.get("lemmatizer") or ""
                if "fugashi" in tk or "unidic" in tk:
                    kind = "fugashi-unidic"
                elif "mecab" in tk:
                    kind = "mecab-ko"
                elif "jieba" in tk:
                    kind = "jieba"
                elif "arab" in tk:
                    kind = "arabic"
                elif tk in ("whitespace", "regex") and not lem.startswith("simplemma"):
                    kind = "whitespace"
                else:
                    kind = "latin"
                reg[iso] = {
                    "kaikkiName": l.get("kaikkiName") or l.get("kaikkiLang") or (b[0] if b else None),
                    "etymdbCodes": codes,
                    "tatoebaCode": l.get("tatoebaCode") or l.get("tatoebaLang") or l.get("tatoeba") or (b[2] if b else None),
                    "tokenizer": kind,
                    "simplemmaLang": lem.split(":", 1)[1] if lem.startswith("simplemma:") else iso,
                    "script": l.get("script") or (b[4] if b else "Latn"),
                    "enabled": l.get("enabled", True),
                }
        except Exception as ex:  # a half-written registry must not break the enrichment stages
            log(f"warning: could not parse {REGISTRY_PATH}: {ex}; using the built-in map")
            reg = {}
    def builtin(iso):
        b = BUILTIN[iso]
        return {"kaikkiName": b[0], "etymdbCodes": list(b[1]), "tatoebaCode": b[2], "tokenizer": b[3], "simplemmaLang": iso,
                "script": b[4], "enabled": True}

    for iso in (extra_languages or []):
        if iso not in reg and iso in BUILTIN:
            reg[iso] = builtin(iso)
    if not reg:
        for iso in BUILTIN:
            reg[iso] = builtin(iso)
    return reg


# --------------------------------------------------------------------------------------
# Normalisation
# --------------------------------------------------------------------------------------
AR_DIAC = re.compile(r"[ؐ-ًؚ-ْٰـۖ-ۭ]")
CF_CHARS = re.compile(r"[​-‏‪-‮⁠﻿]")
AR_PUNCT = re.compile(r"[،؛؟٪-٭.,!?;:\"'()\[\]«»…-]+")
HAN_RE = re.compile(r"[㐀-鿿豈-﫿]")
KANA_RE = re.compile(r"[぀-ヿ]")
HANGUL_RE = re.compile(r"[가-힯]")
_s2t = _t2s = None


def opencc(direction: str):
    """Lazy OpenCC converter ('s2t' or 't2s')."""
    global _s2t, _t2s
    import opencc as _oc
    if direction == "s2t":
        if _s2t is None:
            _s2t = _oc.OpenCC("s2t")
        return _s2t
    if _t2s is None:
        _t2s = _oc.OpenCC("t2s")
    return _t2s


def ar_norm(w: str) -> str:
    w = CF_CHARS.sub("", w)
    w = AR_DIAC.sub("", w)
    return re.sub("[إأآٱ]", "ا", w)


def strip_diac(w: str) -> str:
    """NFD, drop combining marks, lowercase (Latin macrons/accents, Cyrillic stress)."""
    return "".join(c for c in unicodedata.normalize("NFD", w) if unicodedata.category(c) != "Mn").lower()


def norm_key(lang: str, w: str) -> str:
    """The primary matching key for a word in `lang` — identical on both sides of every join."""
    w = CF_CHARS.sub("", w.strip())
    if lang == "ar":
        return ar_norm(w)
    if lang in ("ja", "ko", "zh"):
        return unicodedata.normalize("NFKC", w)
    return unicodedata.normalize("NFKC", w).lower()


def is_han(s: str) -> bool:
    return bool(s) and all(HAN_RE.match(c) for c in s)


# Verbal endings only: MeCab already splits nominal particles off, and stripping particles from a
# noun-like token would let 하고 ("and") be explained by the entry for 하 (河 …). A stripped stem is
# only ever looked up as stem+다 (a verb/adjective headword), never bare.
KO_ENDINGS = [
    "습니다", "ㅂ니다", "었어요", "았어요", "셨어요", "어요", "아요", "세요", "네요", "지요", "죠", "군요",
    "는데", "니까", "으니까", "면서", "으면", "면", "거든", "지만", "다가", "려고", "으려고",
    "고", "지", "어", "아", "게", "서",
]


def _ko_split(ch: str):
    code = ord(ch) - 0xAC00
    if code < 0 or code > 11171:
        return None
    return code // 588, (code % 588) // 28, code % 28


def _ko_join(l: int, v: int, t: int) -> str:
    return chr(0xAC00 + l * 588 + v * 28 + t)


def ko_candidates(tok: str) -> list[str]:
    """Dictionary-form candidates for a MeCab surface token (only accepted if they exist
    in Wiktionary — this generates lookups, never facts)."""
    out = [tok, tok + "다"]
    # past/honorific contraction: 갔 -> 가다, 먹었 -> 먹다, 마셨 -> 마시다, 했 -> 하다
    last = _ko_split(tok[-1]) if tok else None
    if last and last[2] == 20:  # ㅆ batchim
        l, v, _ = last
        base = tok[:-1] + _ko_join(l, v, 0)
        out.append(base + "다")
        if l == 11 and v in (0, 4) and len(tok) > 1:   # separate 았/었 syllable: 먹었 -> 먹다
            out.append(tok[:-1] + "다")
        if v == 6:   # ㅕ <- ㅣ+ㅓ
            out.append(tok[:-1] + _ko_join(l, 20, 0) + "다")
        if v == 1:   # ㅐ (했 <- 하였)
            out.append(tok[:-1] + _ko_join(l, 0, 0) + "다")
        if v == 9:   # ㅘ <- ㅗ+ㅏ
            out.append(tok[:-1] + _ko_join(l, 8, 0) + "다")
        if v == 14:  # ㅝ <- ㅜ+ㅓ
            out.append(tok[:-1] + _ko_join(l, 13, 0) + "다")
    for e in KO_ENDINGS:
        if len(tok) > len(e) and tok.endswith(e):
            out.append(tok[: -len(e)] + "다")
    seen, res = set(), []
    for c in out:
        if c and c not in seen:
            seen.add(c)
            res.append(c)
    return res


def ar_candidates(tok: str) -> list[str]:
    n = AR_PUNCT.sub("", ar_norm(tok))
    out = [n]
    if len(n) > 3 and n.startswith("و"):
        out.append(n[1:])
    if len(n) > 4 and n.startswith("ال"):
        out.append(n[2:])
    if len(n) > 5 and n.startswith("وال"):
        out.append(n[3:])
        out.append(n[1:])
    if len(n) > 4 and n.startswith("فال"):
        out.append(n[3:])
    if len(n) > 3 and n[0] in "بلكف":
        out.append(n[1:])
    seen, res = set(), []
    for c in out:
        if c and c not in seen:
            seen.add(c)
            res.append(c)
    return res


def lexicon_keys(lang: str, lemma: str) -> list[str]:
    """All normalised keys under which a corpus lemma may be found in Wiktionary."""
    k = norm_key(lang, lemma)
    if lang == "ar":
        return ar_candidates(lemma)
    if lang == "zh":
        out = [k]
        t = opencc("s2t").convert(k)
        if t != k:
            out.append(t)
        s = opencc("t2s").convert(k)
        if s not in out:
            out.append(s)
        return out
    if lang == "ko":
        return ko_candidates(k)
    if lang == "ja":
        return [k]
    out = [k]
    sd = strip_diac(k)
    if sd != k:
        out.append(sd)
    return out


# --------------------------------------------------------------------------------------
# Tokenisers (mirror annotate.py)
# --------------------------------------------------------------------------------------
WORD_RE = re.compile(r"[^\W\d_]+(?:[''’\-][^\W\d_]+)*")
AR_RE = re.compile(r"[؀-ۿ]+")


class Tokenizer:
    """tokens(text) -> list of (surface, lemma) using the same libraries as annotate.py.
    Lemmatisation is memoised per surface, which is what makes millions of Tatoeba
    sentences affordable."""

    def __init__(self, lang: str, kind: str, simplemma_lang: str | None = None):
        self.lang, self.kind = lang, kind
        self.simplemma_lang = simplemma_lang or lang
        self._cache: dict[str, str] = {}
        self._tagger = None

    def _latin_lemma(self, s: str) -> str:
        c = self._cache.get(s)
        if c is None:
            if self.kind == "whitespace":
                c = s
            else:
                import simplemma
                try:
                    c = simplemma.lemmatize(s, lang=self.simplemma_lang).lower()
                except Exception:
                    c = s
            self._cache[s] = c
        return c

    def tokens(self, text: str) -> list[tuple[str, str]]:
        k = self.kind
        if k == "fugashi-unidic":
            if self._tagger is None:
                import fugashi
                self._tagger = fugashi.Tagger()
            out = []
            for w in self._tagger(text):
                f = w.feature
                if getattr(f, "pos1", "") in ("補助記号", "記号", "空白") or not w.surface.strip():
                    continue
                lem = getattr(f, "orthBase", None) or getattr(f, "lemma", None) or w.surface
                if lem == "*":
                    lem = w.surface
                out.append((w.surface, lem))
            return out
        if k == "mecab-ko":
            import wordfreq
            return [(t, t) for t in wordfreq.tokenize(text, "ko") if HANGUL_RE.search(t)]
        if k == "jieba":
            import wordfreq
            simp = opencc("t2s").convert(text)
            return [(t, t) for t in wordfreq.tokenize(simp, "zh") if HAN_RE.search(t)]
        if k == "arabic":
            out = []
            for m in AR_RE.finditer(text):
                n = ar_norm(m.group())
                if len(n) >= 2:
                    out.append((m.group(), n))
            return out
        # latin / cyrillic / devanagari via simplemma
        return [(m.group(), self._latin_lemma(m.group().lower())) for m in WORD_RE.finditer(text)]


_TOK: dict[str, Tokenizer] = {}


def tokenizer(lang: str, registry: dict | None = None) -> Tokenizer:
    if lang not in _TOK:
        reg = registry or load_registry([lang])
        entry = reg.get(lang, {})
        kind = entry.get("tokenizer") or BUILTIN.get(lang, (None, None, None, "latin"))[3]
        _TOK[lang] = Tokenizer(lang, kind, entry.get("simplemmaLang"))
    return _TOK[lang]


# --------------------------------------------------------------------------------------
# kaikki streaming matcher
# --------------------------------------------------------------------------------------
KEEP_ENTRY_FIELDS = ("w", "p", "l", "tp", "f", "so", "en")
MATCH_VERSION = "m5"   # bump when the matching rules change so stale caches are not reused


def _lexicon_digest(lex_lang: dict) -> str:
    h = hashlib.sha1(MATCH_VERSION.encode())
    for k in sorted(lex_lang):
        h.update(k.encode("utf-8"))
        h.update(b"\0")
    return h.hexdigest()[:16]


def match_kaikki_language(args):
    """Worker: stream one compacted kaikki dump and return the entries that match the
    lexicon of that language, keyed by lemma. Returns (lang, {lemma: [entry...]}, stats).

    Matching order per lemma (first hit wins per key, all keys tried): headword key,
    then `f` forms. All matched entries (every POS / etymology section) are kept."""
    lang, kaikki_path, lemmas = args
    # key -> [(lemma, candidate rank)]; rank 0 = the lemma's own normalised key, 1.. = derived
    # candidates (clitic-stripped Arabic, Korean stem+다, traditional/simplified variant …)
    key2lemmas: dict[str, list[tuple[str, int]]] = defaultdict(list)
    for lemma in lemmas:
        for rank, key in enumerate(lexicon_keys(lang, lemma)):
            key2lemmas[key].append((lemma, min(rank, 1)))
    wanted = set(key2lemmas)
    by_lemma: dict[str, list[dict]] = defaultdict(list)
    head_hit: set[str] = set()
    n = 0
    t0 = time.time()
    with gzip.open(kaikki_path, "rt", encoding="utf-8") as f:
        for line in f:
            n += 1
            try:
                e = json.loads(line)
            except Exception:
                continue
            w = e.get("w")
            if not w:
                continue
            hk = norm_key(lang, w)
            hit_keys = []
            if hk in wanted:
                hit_keys.append((hk, "head"))
            for fo in e.get("f") or []:
                fw = fo.get("f")
                if not fw:
                    continue
                tags = fo.get("t") or []
                if "romanization" in tags or "inflection-template" in tags or "table-tags" in tags or "class" in tags:
                    continue
                fk = norm_key(lang, fw)
                if fk in wanted and fk != hk:
                    hit_keys.append((fk, "form"))
            if not hit_keys:
                continue
            slim = {k: e[k] for k in KEEP_ENTRY_FIELDS if k in e and e[k] is not None}
            slim["et"] = bool(e.get("et"))
            for key, how in hit_keys:
                for lemma, rank in key2lemmas[key]:
                    rec = dict(slim)
                    rec["_via"] = how
                    rec["_key"] = key
                    # 0 = the lemma's own key (headword or inflected form), 1 = derived candidate
                    rec["_prio"] = rank
                    by_lemma[lemma].append(rec)
    # Keep only the best match level per lemma: the lemma's own key (headword and form hits alike —
    # a simplified-Chinese soft-redirect headword must not hide the traditional entry reached
    # through its forms) beats a derived candidate (so Korean 단어 is not explained by the entry for 단).
    for lemma, entries in list(by_lemma.items()):
        best = min(e["_prio"] for e in entries)
        level = [e for e in entries if e["_prio"] == best]
        heads = [e for e in level if e["_via"] == "head"]
        forms = [e for e in level if e["_via"] != "head"]
        kept = heads or forms
        # a headword that is only a soft redirect (simplified Chinese 电话 → 電話, kana spellings)
        # carries neither templates nor sounds: fall through to the entries reached via forms
        if heads and forms and not any(e.get("tp") or e.get("so") or e.get("et") for e in heads):
            kept = heads + forms
        if heads:
            head_hit.add(lemma)
        # cap pathological cases (a very common inflected form can hit hundreds of entries)
        by_lemma[lemma] = kept[:24]
    stats = {"lang": lang, "entries_streamed": n, "lemmas": len(lemmas), "matched": len(by_lemma),
             "matched_headword": len(head_hit), "matched_form_only": len(by_lemma) - len(head_hit),
             "seconds": round(time.time() - t0, 1)}
    return lang, dict(by_lemma), stats


def collect_kaikki_matches(lexicon: dict, registry: dict, kaikki_dir: Path, cache_dir: Path,
                           workers: int = 2, langs: list[str] | None = None):
    """Match every lexicon lemma to its Wiktionary entries; cached per language and lexicon
    digest under cache_dir/kaikki_matches.<lang>.<digest>.json.gz."""
    from multiprocessing import Pool
    cache_dir.mkdir(parents=True, exist_ok=True)
    jobs, result, stats = [], {}, {}
    for lang in (langs or list(lexicon)):
        if lang not in registry:
            log(f"  {lang}: not in the registry — skipped")
            continue
        name = registry[lang].get("kaikkiName")
        path = kaikki_dir / f"{name}.jsonl.gz" if name else None
        if not path or not path.exists():
            log(f"  {lang}: no kaikki dump ({path}) — skipped")
            stats[lang] = {"lang": lang, "skipped": "no dump"}
            continue
        digest = _lexicon_digest(lexicon[lang])
        cpath = cache_dir / f"kaikki_matches.{lang}.{digest}.json.gz"
        if cpath.exists():
            with gzip.open(cpath, "rt", encoding="utf-8") as f:
                doc = json.load(f)
            result[lang], stats[lang] = doc["matches"], doc["stats"]
            stats[lang]["cached"] = True
            continue
        jobs.append((lang, str(path), list(lexicon[lang].keys()), cpath))
    if jobs:
        with Pool(min(workers, len(jobs))) as pool:
            for lang, matches, st in pool.imap_unordered(match_kaikki_language, [j[:3] for j in jobs]):
                cpath = next(j[3] for j in jobs if j[0] == lang)
                with gzip.open(cpath, "wt", encoding="utf-8") as f:
                    json.dump({"matches": matches, "stats": st}, f, ensure_ascii=False)
                result[lang], stats[lang] = matches, st
                log(f"  {lang}: {st['matched']}/{st['lemmas']} lemmas matched "
                    f"({st['matched_headword']} headword, {st['matched_form_only']} form-only) in {st['seconds']}s")
    return result, stats


# --------------------------------------------------------------------------------------
# Licences
# --------------------------------------------------------------------------------------
def permissive_licence(short: str | None) -> str | None:
    """Return a normalised short licence name if `short` is CC0 / CC BY / CC BY-SA /
    public domain; None for NC, ND, empty, unknown or anything else (D-7 licence rule)."""
    if not short:
        return None
    s = short.strip()
    if s in ("\\N", ""):
        return None
    u = s.upper().replace("CC-", "CC ").replace("_", " ")
    u = re.sub(r"\s+", " ", u)
    if "NC" in u.split() or "-NC" in u or " NC" in u or "NONCOMMERCIAL" in u or "NON-COMMERCIAL" in u:
        return None
    if "ND" in u.split() or "-ND" in u or " ND" in u or "NODERIV" in u:
        return None
    if u.startswith("CC0") or u == "CC ZERO" or "PUBLIC DOMAIN" in u or u.startswith("PD") or u == "PDM":
        return "CC0 1.0" if u.startswith("CC0") else "Public domain"
    if u.startswith("CC BY-SA") or u.startswith("CC BY SA"):
        m = re.search(r"(\d\.\d)", u)
        return f"CC BY-SA {m.group(1)}" if m else "CC BY-SA"
    if u.startswith("CC BY"):
        m = re.search(r"(\d\.\d)", u)
        tail = re.search(r"\d\.\d\s+([A-Z]{2})\b", u)
        return f"CC BY {m.group(1)}{' ' + tail.group(1) if tail else ''}" if m else "CC BY"
    if u in ("GFDL", "CC BY-SA 3.0 AND GFDL"):
        return None
    return None


def strip_html(s: str | None) -> str | None:
    if not s:
        return None
    s = re.sub(r"<[^>]+>", "", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s or None


# --------------------------------------------------------------------------------------
# Output helpers
# --------------------------------------------------------------------------------------
def load_lexicon(path) -> dict:
    """lexicon.json or lexicon.json.gz — {lang: {lemma: [count, zipf, surface, reading]}}."""
    p = Path(path)
    if p.suffix == ".gz":
        with gzip.open(p, "rt", encoding="utf-8") as f:
            return json.load(f)
    return json.loads(p.read_text(encoding="utf-8"))


def write_json_gz(path: Path, obj) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with open(path, "wb") as f:
        f.write(gzip.compress(raw, 9))
    return path.stat().st_size


def write_json(path: Path, obj, indent=1) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=indent), encoding="utf-8")
    return path.stat().st_size


ATTR_WIKTIONARY = {"name": "Wiktionary contributors (via wiktextract / kaikki.org)", "role": "etymology, IPA and audio references",
                   "url": "https://kaikki.org/dictionary/", "licence": "CC BY-SA 4.0"}
ATTR_ETYMDB = {"name": "EtymDB 2.1 — Clémentine Fourrier & Benoît Sagot (LREC 2020)", "role": "etymological links",
               "url": "https://github.com/clefourrier/EtymDB", "licence": "CC BY-SA 4.0"}
ATTR_TATOEBA = {"name": "Tatoeba contributors", "role": "example sentences",
                "url": "https://tatoeba.org/", "licence": "CC BY 2.0 FR"}
ATTR_TATOEBA_AUDIO = {"name": "Tatoeba audio contributors (per-clip licence, see ATTRIBUTION-tatoeba.json)", "role": "sentence audio",
                      "url": "https://tatoeba.org/en/audio", "licence": "CC BY 4.0 / CC BY-SA 4.0 / CC0 1.0 per clip"}
ATTR_COMMONS = {"name": "Wikimedia Commons contributors (per-file licence and author in the pack)", "role": "word audio",
                "url": "https://commons.wikimedia.org/", "licence": "CC0 / CC BY / CC BY-SA / public domain per file"}


def manifest_fragment(kind: str, pack_id: str, title: str, languages: list[str], licence: str,
                      source: str, attribution: list[dict], files: dict[str, int],
                      stats: dict[str, dict[str, int]]) -> dict:
    """A PackManifest (Packs.kt) as JSON."""
    return {
        "id": pack_id,
        "kind": kind,
        "version": PACK_VERSION,
        "title": title,
        "languages": sorted(languages),
        "licence": licence,
        "source": source,
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "attribution": attribution,
        "files": files,
        "stats": stats,
    }
