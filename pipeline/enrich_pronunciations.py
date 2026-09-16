#!/usr/bin/env python3
"""Pronunciation pack: IPA and Wikimedia Commons audio *references* (never the audio itself)
for every Clavis lexicon lemma found in Wiktionary (kaikki.org extracts, CC BY-SA 4.0).

Output  pipeline/build/packs/pronunciations/<lang>.json.gz
        {lemma: {lemma, ipa?, audioUrl?, audioLicence?, audioBy?}}         (Packs.kt Pronunciation)
        pipeline/build/packs/pronunciations/manifest-fragment.json          (PackManifest, PRONUNCIATIONS)
        pipeline/build/enrich/commons_cache.json                            Commons API answers (per file)
        pipeline/build/enrich/pronunciation_coverage.json

Licence rule (D-7): kaikki does not carry the per-file licence, so every candidate file is looked
up once through the Commons API (prop=imageinfo&iiprop=extmetadata, 50 titles per request, one
request every few seconds, User-Agent "Clavis-pipeline/0.1"). Only CC0 / CC BY / CC BY-SA /
public domain files are referenced; NC, ND, unknown and un-verifiable files are excluded and
counted. The app fetches audio on explicit user action only (D-13).

usage: enrich_pronunciations.py [--lexicon PATH] [--languages …] [--kaikki-dir DIR] [--out-dir DIR]
                                [--offline] [--max-candidates 2] [--interval 6]
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from enrich_common import (  # noqa: E402
    load_lexicon,
    ATTR_COMMONS, ATTR_WIKTIONARY, BUILD_DIR, DEFAULT_KAIKKI_DIR, DEFAULT_LEXICON, USER_AGENT,
    collect_kaikki_matches, load_registry, log, manifest_fragment, permissive_licence, strip_html,
    write_json, write_json_gz,
)

COMMONS_API = "https://commons.wikimedia.org/w/api.php"

# --------------------------------------------------------------------------------------
# Second audio source: Lingua Libre (SPARQL) + a Commons category scan, for languages
# kaikki carries almost no {{audio}} for (es/it/pl/ja/ko/la — see ENRICHMENT.md §6.4).
# Off by default (--lingualibre / --commons-categories); the record schema, licence check,
# cache and output paths are unchanged so nothing downstream needs to change.
# --------------------------------------------------------------------------------------
LINGUALIBRE_SPARQL = "https://lingualibre.org/bigdata/namespace/wdq/sparql"
LINGUALIBRE_LANG_CACHE = "lingualibre_languages.json"
NEW_SOURCE_CAP = 1   # candidates appended per lemma from Lingua Libre/categories combined, to keep the licence pass short
# Scoped to the languages kaikki has almost no audio for (ENRICHMENT.md §6.4); the other eleven
# already have good kaikki/Commons coverage and must not be touched by this second source.
# Our iso -> (Lingua Libre ISO 639-3 code [prop:P13], English label [rdfs:label], fallback if the code lookup misses)
LINGUALIBRE_ISO3 = {"es": "spa", "it": "ita", "pl": "pol", "ja": "jpn", "ko": "kor", "la": "lat"}
LINGUALIBRE_LABEL = {"es": "Spanish", "it": "Italian", "pl": "Polish", "ja": "Japanese", "ko": "Korean", "la": "Latin"}
# Small generic Commons categories only (a few thousand files each); Polish is deliberately omitted
# (Category:Polish pronunciation has ~24k files — Lingua Libre already covers Polish well).
COMMONS_CATEGORIES = {"es": "Spanish pronunciation", "it": "Italian pronunciation", "ja": "Japanese pronunciation",
                      "ko": "Korean pronunciation", "la": "Latin pronunciation"}
AUDIO_EXT_RE = re.compile(r"\.(ogg|oga|flac|wav|mp3)$", re.IGNORECASE)
FNAME_LLQ = re.compile(r"^LL-Q\d+\s*\([a-z]{3}\)-(.+)$", re.IGNORECASE)
FNAME_ISO_PAREN = re.compile(r"^\([a-z]{3}\)-(.+)$", re.IGNORECASE)
FNAME_PREFIX = re.compile(r"^([A-Za-z]{2})-(.+)$")


def category_word(lang: str, filename: str) -> str | None:
    """Extract the transcribed word from a Commons pronunciation-category filename, normalised
    NFC + lowercase, or None when the filename does not match a known convention.

    Patterns: 'LL-Q<n> (iso)-<speaker>-<word>.ext', '(iso)-<speaker>-<word>.ext' (an older
    per-language batch upload convention), 'Xx-<word>.ext' and 'Xx-<region>-<word>.ext' — the
    last hyphen-separated segment is always the transcribed word in every pattern, so a single
    rule covers both 'Xx-<word>' and 'Xx-<region>-<word>'."""
    stem = AUDIO_EXT_RE.sub("", filename)
    m = FNAME_LLQ.match(stem)
    if m:
        rest = m.group(1)
    else:
        m = FNAME_ISO_PAREN.match(stem)
        if m:
            rest = m.group(1)
        else:
            m = FNAME_PREFIX.match(stem)
            if not m or m.group(1).lower() != lang:
                return None
            rest = m.group(2)
    word = rest.rsplit("-", 1)[-1].strip()
    return unicodedata.normalize("NFC", word).lower() if word else None


def lexicon_word_index(lexicon_lang: dict) -> dict[str, str]:
    """{word (NFC, lowercase) -> lemma}, covering the lemma and its surface form (lexicon tuple
    slot 2) so a category filename spelled like the corpus surface still matches."""
    idx: dict[str, str] = {}
    for lemma, v in lexicon_lang.items():
        surface = v[2] if isinstance(v, list) and len(v) > 2 else None
        for w in (lemma, surface):
            if not w:
                continue
            k = unicodedata.normalize("NFC", w).lower()
            idx.setdefault(k, lemma)
    return idx


def esc_sparql_string(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def group_lingualibre_rows(rows: list[dict], lexicon_lang: dict) -> dict[str, list[str]]:
    """SPARQL rows ({'file': 'https://.../Special:FilePath/<name>', 'transcription': <lemma>}) ->
    {lemma: [filename, ...]} — a SPARQL VALUES match is exact, so `transcription` is already one of
    our own lemma strings; only membership and dedup remain to check here (offline, no network)."""
    per_lemma: dict[str, list[str]] = defaultdict(list)
    for r in rows:
        raw = r.get("file") or ""
        fname = urllib.parse.unquote(raw.rsplit("/", 1)[-1])
        lemma = r.get("transcription")
        if not fname or lemma is None or lemma not in lexicon_lang:
            continue
        if fname not in per_lemma[lemma]:
            per_lemma[lemma].append(fname)
    return dict(per_lemma)


def add_extra_candidates(cands: dict, lang: str, lemma_to_files: dict[str, list[str]], source: str,
                         cap: int, max_total: int) -> int:
    """Append up to `cap` not-yet-seen files per lemma (from `source`) to `cands[lang]`, creating
    the lemma entry if kaikki never matched it; never exceeds `max_total` candidates per lemma and
    never reorders/removes an existing (kaikki) candidate — new candidates always land after them."""
    added = 0
    lang_cands = cands.setdefault(lang, {})
    for lemma, files in lemma_to_files.items():
        entry = lang_cands.setdefault(lemma, {"ipa": None, "audio": []})
        existing = {a["file"] for a in entry["audio"]}
        n = 0
        for f in files:
            if f in existing:
                continue
            if n >= cap or len(entry["audio"]) >= max_total:
                break
            entry["audio"].append({"file": f, "ogg": None, "mp3": None, "tags": [], "penalty": 0, "source": source})
            existing.add(f)
            n += 1
            added += 1
    return added


def audio_url_for(a: dict, commons: "Commons") -> str | None:
    """mp3/ogg transcode when kaikki supplied one, else the Commons original file URL from the
    already-fetched extmetadata (Lingua Libre/category candidates carry no transcode guess)."""
    if a.get("mp3") or a.get("ogg"):
        return a["mp3"] or a["ogg"]
    rec = commons.cache.get(commons_title(a["file"]))
    return (rec or {}).get("url")


class LinguaLibre:
    """Rate-limited SPARQL client for the Lingua Libre endpoint. `available` turns False after
    the endpoint fails outright (down/blocked from this egress), so callers can fall back to the
    Commons category scan only, as the brief requires."""

    def __init__(self, interval: float, offline: bool):
        self.interval, self.offline = interval, offline
        self.last = 0.0
        self.requests = 0
        self.failed_requests = 0
        self.available = not offline

    def _post(self, query: str) -> list[dict] | None:
        if self.offline or not self.available:
            return None
        data = query.encode("utf-8")
        req = urllib.request.Request(LINGUALIBRE_SPARQL, data=data,
                                     headers={"User-Agent": USER_AGENT, "Accept": "application/sparql-results+json",
                                              "Content-Type": "application/sparql-query; charset=utf-8"})
        backoff = 5.0
        for _ in range(5):
            wait = self.interval - (time.time() - self.last)
            if wait > 0:
                time.sleep(wait)
            self.last = time.time()
            self.requests += 1
            try:
                with urllib.request.urlopen(req, timeout=90) as r:
                    ctype = r.headers.get("Content-Type", "")
                    body = r.read()
                if "json" not in ctype:
                    raise ValueError(f"unexpected content-type {ctype!r}")
                doc = json.loads(body)
                return [{k: v.get("value") for k, v in b.items()} for b in doc["results"]["bindings"]]
            except urllib.error.HTTPError as ex:
                self.failed_requests += 1
                if ex.code == 429 or ex.code >= 500:
                    log(f"    lingualibre {ex.code}; sleeping {backoff:.0f}s")
                    time.sleep(backoff)
                    backoff = min(backoff * 2, 120)
                    continue
                log(f"    lingualibre HTTP {ex.code}; giving up on this chunk")
                return None
            except Exception as ex:  # noqa: BLE001 — network hiccup, malformed/oversized-query response, …
                self.failed_requests += 1
                log(f"    lingualibre error ({ex}); sleeping {backoff:.0f}s")
                time.sleep(backoff)
                backoff = min(backoff * 2, 120)
        self.available = False
        log("    lingualibre: endpoint unresponsive after 5 attempts; disabling for the rest of this run")
        return None

    def records_for(self, qid: str, lemmas: list[str], chunk: int = 300) -> tuple[list[dict], int]:
        """(rows, failed_chunk_count) for one language's Q-item, chunked ~300 VALUES per request."""
        if self.offline:
            return [], 0
        rows: list[dict] = []
        failed = 0
        consecutive = 0
        for i in range(0, len(lemmas), chunk):
            batch = lemmas[i:i + chunk]
            values = " ".join(esc_sparql_string(w) for w in batch)
            query = (f"PREFIX prop: <https://lingualibre.org/prop/direct/>\n"
                     f"PREFIX entity: <https://lingualibre.org/entity/>\n"
                     f"SELECT ?file ?transcription WHERE {{\n"
                     f"  VALUES ?transcription {{ {values} }}\n"
                     f"  ?record prop:P2 entity:Q2 ; prop:P4 entity:{qid} ; prop:P3 ?file ; prop:P7 ?transcription .\n"
                     f"}}")
            r = self._post(query)
            if r is None:
                failed += 1
                consecutive += 1
                if consecutive >= 3 or not self.available:
                    log(f"    lingualibre: giving up on this language after {consecutive} consecutive chunk failures")
                    break
                continue
            consecutive = 0
            rows.extend(r)
        return rows, failed


def discover_lingualibre_languages(langs: list[str], ll: LinguaLibre, cache_path: Path) -> dict[str, str]:
    """{iso: Lingua Libre entity QID}, discovered from the endpoint's own ISO 639-3 property
    (found by inspecting the property list: P13 'ISO 639-3 code' on items that are P2 Q4
    'language'), falling back to an English rdfs:label match; cached on disk across runs."""
    cache: dict[str, str] = {}
    if cache_path.exists():
        try:
            cache = json.loads(cache_path.read_text(encoding="utf-8"))
        except Exception:
            cache = {}
    missing = [l for l in langs if l not in cache]
    if not missing or ll.offline:
        return {l: cache[l] for l in langs if l in cache}
    iso_to_app = {LINGUALIBRE_ISO3[l]: l for l in missing if l in LINGUALIBRE_ISO3}
    found: dict[str, str] = {}
    if iso_to_app:
        values = ", ".join(esc_sparql_string(c) for c in iso_to_app)  # SPARQL IN(...) needs commas, unlike VALUES
        rows = ll._post(  # noqa: SLF001 — internal client, only this module uses it
            "PREFIX prop: <https://lingualibre.org/prop/direct/>\n"
            "PREFIX entity: <https://lingualibre.org/entity/>\n"
            "SELECT ?lang ?iso WHERE {\n"
            "  ?lang prop:P2 entity:Q4 ; prop:P13 ?iso .\n"
            f"  FILTER(?iso IN ({values}))\n"
            "}") or []
        found.update(_langs_from_iso_rows(rows, iso_to_app))
    remaining = [l for l in missing if l not in found]
    label_to_app = {LINGUALIBRE_LABEL[l]: l for l in remaining if l in LINGUALIBRE_LABEL}
    if label_to_app and ll.available:
        values = ", ".join(esc_sparql_string(lb) for lb in label_to_app)  # SPARQL IN(...) needs commas, unlike VALUES
        rows = ll._post(  # noqa: SLF001
            "PREFIX prop: <https://lingualibre.org/prop/direct/>\n"
            "PREFIX entity: <https://lingualibre.org/entity/>\n"
            "SELECT ?lang ?label WHERE {\n"
            "  ?lang prop:P2 entity:Q4 ; rdfs:label ?label .\n"
            '  FILTER(LANG(?label) = "en")\n'
            f"  FILTER(?label IN ({values}))\n"
            "}") or []
        found.update(_langs_from_label_rows(rows, label_to_app))
    if found:
        cache.update(found)
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_text(json.dumps(cache, ensure_ascii=False, indent=1), encoding="utf-8")
    return {l: cache[l] for l in langs if l in cache}


def _langs_from_iso_rows(rows: list[dict], iso_to_app: dict[str, str]) -> dict[str, str]:
    out = {}
    for r in rows:
        app = iso_to_app.get(r.get("iso"))
        qid = (r.get("lang") or "").rsplit("/", 1)[-1]
        if app and qid:
            out[app] = qid
    return out


def _langs_from_label_rows(rows: list[dict], label_to_app: dict[str, str]) -> dict[str, str]:
    out = {}
    for r in rows:
        app = label_to_app.get(r.get("label"))
        qid = (r.get("lang") or "").rsplit("/", 1)[-1]
        if app and qid:
            out.setdefault(app, qid)
    return out


def fetch_category_files(category: str, interval: float, offline: bool) -> list[str]:
    """Every file title (without the 'File:' prefix) in a Commons category, paged with cmcontinue."""
    if offline:
        return []
    titles: list[str] = []
    cont = None
    last = 0.0
    while True:
        params = {"action": "query", "list": "categorymembers", "cmtitle": f"Category:{category}",
                  "cmtype": "file", "cmnamespace": "6", "cmlimit": "500", "format": "json", "formatversion": "2"}
        if cont:
            params["cmcontinue"] = cont
        q = urllib.parse.urlencode(params)
        req = urllib.request.Request(f"{COMMONS_API}?{q}", headers={"User-Agent": USER_AGENT, "Api-User-Agent": USER_AGENT})
        wait = interval - (time.time() - last)
        if wait > 0:
            time.sleep(wait)
        last = time.time()
        backoff = 20.0
        doc = None
        while doc is None:
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    doc = json.loads(r.read().decode("utf-8"))
            except urllib.error.HTTPError as ex:
                if ex.code == 429 or ex.code >= 500:
                    log(f"    commons-cat {ex.code}; sleeping {backoff:.0f}s")
                    time.sleep(backoff)
                    backoff = min(backoff * 2, 180)
                    continue
                raise
            except Exception as ex:  # noqa: BLE001
                log(f"    commons-cat error {ex}; sleeping {backoff:.0f}s")
                time.sleep(backoff)
                backoff = min(backoff * 2, 180)
        for m in doc.get("query", {}).get("categorymembers", []) or []:
            t = m.get("title", "")
            if t.startswith("File:"):
                titles.append(t[5:])
        cont = (doc.get("continue") or {}).get("cmcontinue")
        if not cont:
            break
    return titles
DIALECT_TAGS = {"US", "UK", "Received-Pronunciation", "General-American", "Australia", "Canada", "Scotland", "Ireland",
                "New-Zealand", "India", "South-Africa", "Quebec", "Belgium", "Switzerland", "Austria", "Bavaria", "Berlin",
                "Netherlands", "Flanders", "Brazil", "Portugal", "Latin-America", "Spain", "Mexico", "Argentina",
                "Cantonese", "Hakka", "Min-Nan", "Wu", "Gan", "Xiang", "Jin", "Egyptian", "Levantine", "Gulf", "Hijazi",
                "Iraqi", "Moroccan", "Tunisian", "Algerian", "Sudanese", "Yemeni", "Najdi", "Hejazi", "Sichuanese", "Dungan",
                "Taishan", "Wiktionary-specific", "Bopomofo", "Pinyin", "Jyutping", "Peng'im", "Wugniu", "Phak-fa-su",
                "Hakka-Romanization-System", "Cyrillic", "Cheongju", "Gyeongsang", "Jeolla", "Chungcheong", "Pyongan",
                "Hamgyong", "Hwanghae", "Gangwon", "Jeju", "Gyeonggi", "dialectal", "obsolete", "archaic", "rare",
                "nonstandard", "colloquial", "informal", "regional", "Southern", "Northern", "Cockney"}


def tag_penalty(tags) -> int:
    tags = tags or []
    return sum(1 for t in tags if t in DIALECT_TAGS) + (0 if not tags else 1)


def pick_ipa(entries) -> str | None:
    best = None
    for e in entries:
        for s in e.get("so") or []:
            ipa = s.get("ipa")
            if not ipa:
                continue
            cand = (tag_penalty(s.get("tags")), 0 if ipa.startswith("/") else 1, ipa)
            if best is None or cand < best:
                best = cand
    return best[2] if best else None


def audio_candidates(entries, limit: int) -> list[dict]:
    seen, out = set(), []
    for e in entries:
        for s in e.get("so") or []:
            name = s.get("audio")
            if not name or name in seen:
                continue
            seen.add(name)
            out.append({"file": name, "ogg": s.get("ogg_url"), "mp3": s.get("mp3_url"), "tags": s.get("tags") or [],
                        "penalty": tag_penalty(s.get("tags")), "source": "kaikki"})
    out.sort(key=lambda c: (c["penalty"], c["file"]))
    return out[:limit]


def commons_title(name: str) -> str:
    return "File:" + name.replace("_", " ").strip()


class Commons:
    """Batched, rate-limited, cached extmetadata lookups."""

    def __init__(self, cache_path: Path, interval: float, offline: bool):
        self.cache_path, self.interval, self.offline = cache_path, interval, offline
        self.cache = {}
        if cache_path.exists():
            try:
                self.cache = json.loads(cache_path.read_text(encoding="utf-8"))
            except Exception:
                self.cache = {}
        self.requests = 0
        self.cached_hits = 0
        self.last = 0.0

    def save(self):
        self.cache_path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.cache_path.with_suffix(".tmp")
        # offline placeholders ("unverified") are never persisted: a later networked run must fetch them
        real = {k: v for k, v in self.cache.items() if not (isinstance(v, dict) and v.get("unverified"))}
        tmp.write_text(json.dumps(real, ensure_ascii=False, indent=0), encoding="utf-8")
        tmp.replace(self.cache_path)

    def _fetch(self, titles: list[str]) -> dict:
        q = urllib.parse.urlencode({"action": "query", "prop": "imageinfo", "iiprop": "extmetadata|url",
                                    "iiextmetadatafilter": "LicenseShortName|License|LicenseUrl|Artist|Credit|AttributionRequired|UsageTerms",
                                    "titles": "|".join(titles), "format": "json", "formatversion": "2", "maxlag": "5"})
        req = urllib.request.Request(f"{COMMONS_API}?{q}", headers={"User-Agent": USER_AGENT, "Api-User-Agent": USER_AGENT})
        backoff = 20
        while True:
            wait = self.interval - (time.time() - self.last)
            if wait > 0:
                time.sleep(wait)
            self.last = time.time()
            self.requests += 1
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    return json.loads(r.read().decode("utf-8"))
            except urllib.error.HTTPError as ex:
                if ex.code == 429 or ex.code >= 500:
                    log(f"    commons {ex.code}; sleeping {backoff}s")
                    time.sleep(backoff)
                    backoff = min(backoff * 2, 180)
                    self.interval = min(self.interval * 1.5, 30)
                    continue
                raise
            except Exception as ex:  # network hiccup
                log(f"    commons error {ex}; sleeping {backoff}s")
                time.sleep(backoff)
                backoff = min(backoff * 2, 180)

    def lookup(self, names: list[str]) -> None:
        todo = []
        for n in names:
            t = commons_title(n)
            if t in self.cache and not self.cache[t].get("unverified"):
                self.cached_hits += 1
            else:
                todo.append(t)
        todo = sorted(set(todo))
        if not todo or self.offline:
            for t in todo:
                self.cache.setdefault(t, {"unverified": True})
            return
        for i in range(0, len(todo), 50):
            batch = todo[i:i + 50]
            doc = self._fetch(batch)
            q = doc.get("query", {})
            norm = {}
            for n in q.get("normalized", []) or []:
                norm[n.get("to")] = n.get("from")
            got = set()
            for page in q.get("pages", []) or []:
                title = page.get("title")
                orig = norm.get(title, title)
                got.add(orig)
                if page.get("missing"):
                    self.cache[orig] = {"missing": True}
                    continue
                ii = (page.get("imageinfo") or [{}])[0]
                em = ii.get("extmetadata") or {}
                rec = {k: (em.get(k) or {}).get("value") for k in ("LicenseShortName", "License", "LicenseUrl", "Artist", "Credit", "AttributionRequired", "UsageTerms")}
                rec["url"] = ii.get("url")
                self.cache[orig] = rec
            for t in batch:
                if t not in got and t not in self.cache:
                    self.cache[t] = {"missing": True}
            if (i // 50) % 5 == 4:
                self.save()
                log(f"    commons: {self.requests} requests, {len(self.cache)} files cached")
        self.save()


def licence_of(rec: dict) -> tuple[str | None, str | None, str]:
    """(normalised permissive licence or None, author, status)."""
    if not rec or rec.get("missing"):
        return None, None, "missing"
    if rec.get("unverified"):
        return None, None, "unverified"
    short = rec.get("LicenseShortName") or rec.get("License") or rec.get("UsageTerms")
    lic = permissive_licence(short)
    if lic is None and rec.get("License"):
        lic = permissive_licence(rec.get("License"))
    author = strip_html(rec.get("Artist")) or strip_html(rec.get("Credit"))
    if lic is None:
        return None, author, f"excluded:{short or 'unknown'}"
    return lic, author, "ok"


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lexicon", default=str(DEFAULT_LEXICON))
    ap.add_argument("--languages", default=None)
    ap.add_argument("--kaikki-dir", default=str(DEFAULT_KAIKKI_DIR))
    ap.add_argument("--out-dir", default=str(BUILD_DIR))
    ap.add_argument("--offline", action="store_true", help="never call the Commons API; use the cache only")
    ap.add_argument("--max-candidates", type=int, default=2, help="audio files checked per lemma")
    ap.add_argument("--interval", type=float, default=6.0, help="seconds between Commons API requests")
    ap.add_argument("--workers", type=int, default=2)
    ap.add_argument("--lingualibre", action="store_true",
                    help="also query the Lingua Libre SPARQL endpoint for a second audio candidate source")
    ap.add_argument("--commons-categories", action="store_true",
                    help="also scan the small Commons pronunciation categories (es/it/ja/ko/la) for a second audio candidate source")
    ap.add_argument("--ll-interval", type=float, default=1.5, help="seconds between Lingua Libre SPARQL requests")
    args = ap.parse_args(argv)

    t_all = time.time()
    out_dir = Path(args.out_dir)
    enrich_dir, pack_dir = out_dir / "enrich", out_dir / "packs" / "pronunciations"
    pack_dir.mkdir(parents=True, exist_ok=True)
    lexicon = load_lexicon(args.lexicon)
    want = args.languages.split(",") if args.languages else list(lexicon)
    registry = load_registry(want)
    app_langs = [l for l in want if l in lexicon and l in registry]

    t0 = time.time()
    matches, kstats = collect_kaikki_matches(lexicon, registry, Path(args.kaikki_dir), enrich_dir, args.workers, app_langs)
    timings = {"kaikki_match": round(time.time() - t0, 1)}

    # candidates
    cands = {}      # lang -> lemma -> {"ipa":…, "audio":[…]}
    for l in app_langs:
        cands[l] = {}
        for lemma, entries in matches.get(l, {}).items():
            ipa = pick_ipa(entries)
            audio = audio_candidates(entries, args.max_candidates)
            if ipa or audio:
                cands[l][lemma] = {"ipa": ipa, "audio": audio}

    # second audio source: Lingua Libre (SPARQL) and/or a Commons category scan, for languages
    # kaikki carries almost no {{audio}} for (ENRICHMENT.md §6.4); off by default.
    t0 = time.time()
    ll_stats, cat_stats = {}, {}
    if args.lingualibre:
        try:
            ll = LinguaLibre(args.ll_interval, args.offline)
            want_ll = [l for l in app_langs if l in LINGUALIBRE_ISO3 or l in LINGUALIBRE_LABEL]
            qids = discover_lingualibre_languages(want_ll, ll, enrich_dir / LINGUALIBRE_LANG_CACHE)
            if not args.offline and not qids and ll.requests:
                log("  lingualibre: endpoint unreachable/blocked from this egress; falling back to Commons categories only")
            for l in app_langs:
                qid = qids.get(l)
                if not qid:
                    ll_stats[l] = {"available": False}
                    continue
                rows, failed_chunks = ll.records_for(qid, list(lexicon[l]))
                per_lemma = group_lingualibre_rows(rows, lexicon[l])
                added = add_extra_candidates(cands, l, per_lemma, "lingualibre", NEW_SOURCE_CAP, args.max_candidates)
                ll_stats[l] = {"available": True, "records": len(rows), "lemmas_matched": len(per_lemma),
                               "candidates_added": added, "failed_chunks": failed_chunks}
                log(f"  lingualibre {l}: {len(rows)} records, {len(per_lemma)} lemmas matched, {added} candidates added"
                    + (f" ({failed_chunks} chunk failures)" if failed_chunks else ""))
        except Exception as ex:  # noqa: BLE001 — a dead/blocked endpoint must not fail the whole stage
            log(f"  lingualibre: unexpected error ({ex}); falling back to Commons categories only")
    if args.commons_categories:
        for l in app_langs:
            cat_name = COMMONS_CATEGORIES.get(l)
            if not cat_name:
                cat_stats[l] = {"skipped": "no category configured for this language"}
                continue
            idx = lexicon_word_index(lexicon[l])
            titles = fetch_category_files(cat_name, args.interval, args.offline)
            per_lemma = defaultdict(list)
            matched_files = 0
            for fname in titles:
                word = category_word(l, fname)
                lemma = idx.get(word) if word else None
                if lemma:
                    per_lemma[lemma].append(fname)
                    matched_files += 1
            added = add_extra_candidates(cands, l, dict(per_lemma), "category", NEW_SOURCE_CAP, args.max_candidates)
            cat_stats[l] = {"category": cat_name, "files_scanned": len(titles), "files_matched": matched_files,
                            "lemmas_matched": len(per_lemma), "candidates_added": added}
            log(f"  commons category {l} ({cat_name}): {len(titles)} files scanned, {matched_files} matched, "
                f"{len(per_lemma)} lemmas, {added} candidates added")
    timings["second_source"] = round(time.time() - t0, 1)

    all_files = [a["file"] for l in cands for c in cands[l].values() for a in c["audio"]]
    # first-choice files first so a slow run still yields the most useful subset
    firsts = [cands[l][w]["audio"][0]["file"] for l in cands for w in cands[l] if cands[l][w]["audio"]]
    rest = [f for f in all_files if f not in set(firsts)]
    commons = Commons(enrich_dir / "commons_cache.json", args.interval, args.offline)
    t0 = time.time()
    log(f"commons lookups: {len(set(firsts))} first-choice files, {len(set(rest))} alternates"
        + (" (offline: cache only)" if args.offline else ""))
    commons.lookup(firsts)
    # alternates only for lemmas whose first choice is not permissive
    need = []
    for l in cands:
        for w, c in cands[l].items():
            a = c["audio"]
            if a and licence_of(commons.cache.get(commons_title(a[0]["file"])))[2] != "ok":
                need.extend(x["file"] for x in a[1:])
    commons.lookup(need)
    timings["commons"] = round(time.time() - t0, 1)

    # emit
    per, files = {}, {}
    excluded_reasons = defaultdict(int)
    audio_by_source = defaultdict(lambda: defaultdict(int))
    for l in app_langs:
        recs = {}
        n_ipa = n_audio = n_excl = 0
        for lemma, c in cands[l].items():
            rec = {"lemma": lemma}
            if c["ipa"]:
                rec["ipa"] = c["ipa"]
                n_ipa += 1
            chosen = None
            statuses = []
            for a in c["audio"]:
                lic, author, status = licence_of(commons.cache.get(commons_title(a["file"])))
                statuses.append(status)
                if lic:
                    chosen = (a, lic, author)
                    break
            url = audio_url_for(chosen[0], commons) if chosen else None
            if chosen and url:
                a, lic, author = chosen
                rec["audioUrl"] = url
                rec["audioLicence"] = lic
                if author:
                    rec["audioBy"] = author
                rec["audioFile"] = a["file"]
                n_audio += 1
                audio_by_source[l][a.get("source", "kaikki")] += 1
            elif c["audio"]:
                n_excl += 1
                for s in statuses:
                    excluded_reasons[s] += 1
                if chosen and not url:
                    excluded_reasons["no-url"] += 1
            if len(rec) > 1:
                recs[lemma] = rec
        path = pack_dir / f"{l}.json.gz"
        files[f"{l}.json.gz"] = write_json_gz(path, recs)
        per[l] = {"lemmas": len(lexicon[l]), "kaikki_matched": kstats.get(l, {}).get("matched", 0), "records": len(recs),
                  "ipa": n_ipa, "audio_ok": n_audio, "audio_excluded": n_excl,
                  "pct_ipa": round(100 * n_ipa / max(1, len(lexicon[l])), 1),
                  "pct_audio": round(100 * n_audio / max(1, len(lexicon[l])), 1),
                  "audio_by_source": dict(audio_by_source.get(l, {}))}
    timings["total"] = round(time.time() - t_all, 1)
    coverage = {"lexicon": args.lexicon, "per_language": per, "excluded_reasons": dict(excluded_reasons),
                "commons_api": {"requests": commons.requests, "cached": commons.cached_hits, "files_cached": len(commons.cache),
                                "offline": args.offline},
                "lingualibre": ll_stats, "commons_categories": cat_stats, "timings_seconds": timings}
    write_json(enrich_dir / "pronunciation_coverage.json", coverage)
    stats = {l: {"lemmas": per[l]["lemmas"], "ipa": per[l]["ipa"], "audio": per[l]["audio_ok"], "audioExcluded": per[l]["audio_excluded"]} for l in app_langs}
    frag = manifest_fragment("PRONUNCIATIONS", "clavis-pronunciations", "Pronunciations (IPA + Commons audio references)", app_langs,
                             "CC BY-SA 4.0 (IPA); audio per-file CC0 / CC BY / CC BY-SA / public domain",
                             "https://kaikki.org/dictionary/ (wiktextract, Wiktionary CC BY-SA 4.0); https://commons.wikimedia.org/ (per-file licence via API)",
                             [ATTR_WIKTIONARY, ATTR_COMMONS], files, stats)
    write_json(pack_dir / "manifest-fragment.json", frag)
    print(f"\n{'lang':4} {'lemmas':>6} {'IPA':>6} {'%':>5} {'audio':>6} {'%':>5} {'excluded':>8}  by source")
    for l in app_langs:
        p = per[l]
        print(f"{l:4} {p['lemmas']:6d} {p['ipa']:6d} {p['pct_ipa']:5.1f} {p['audio_ok']:6d} {p['pct_audio']:5.1f} {p['audio_excluded']:8d}  {p['audio_by_source']}")
    print(f"excluded reasons: {dict(excluded_reasons)}\ncommons API: {coverage['commons_api']}; timings {timings}")
    if args.lingualibre:
        print(f"lingualibre: {ll_stats}")
    if args.commons_categories:
        print(f"commons categories: {cat_stats}")
    return coverage


if __name__ == "__main__":
    main()
