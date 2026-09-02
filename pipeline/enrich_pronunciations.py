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
import sys
import time
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
                        "penalty": tag_penalty(s.get("tags"))})
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
        tmp.write_text(json.dumps(self.cache, ensure_ascii=False, indent=0), encoding="utf-8")
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
            if t in self.cache:
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
    all_files = []
    for l in app_langs:
        cands[l] = {}
        for lemma, entries in matches.get(l, {}).items():
            ipa = pick_ipa(entries)
            audio = audio_candidates(entries, args.max_candidates)
            if ipa or audio:
                cands[l][lemma] = {"ipa": ipa, "audio": audio}
                all_files.extend(c["file"] for c in audio)
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
            if chosen:
                a, lic, author = chosen
                rec["audioUrl"] = a["mp3"] or a["ogg"]
                rec["audioLicence"] = lic
                if author:
                    rec["audioBy"] = author
                rec["audioFile"] = a["file"]
                n_audio += 1
            elif c["audio"]:
                n_excl += 1
                for s in statuses:
                    excluded_reasons[s] += 1
            if len(rec) > 1:
                recs[lemma] = rec
        path = pack_dir / f"{l}.json.gz"
        files[f"{l}.json.gz"] = write_json_gz(path, recs)
        per[l] = {"lemmas": len(lexicon[l]), "kaikki_matched": kstats.get(l, {}).get("matched", 0), "records": len(recs),
                  "ipa": n_ipa, "audio_ok": n_audio, "audio_excluded": n_excl,
                  "pct_ipa": round(100 * n_ipa / max(1, len(lexicon[l])), 1),
                  "pct_audio": round(100 * n_audio / max(1, len(lexicon[l])), 1)}
    timings["total"] = round(time.time() - t_all, 1)
    coverage = {"lexicon": args.lexicon, "per_language": per, "excluded_reasons": dict(excluded_reasons),
                "commons_api": {"requests": commons.requests, "cached": commons.cached_hits, "files_cached": len(commons.cache),
                                "offline": args.offline}, "timings_seconds": timings}
    write_json(enrich_dir / "pronunciation_coverage.json", coverage)
    stats = {l: {"lemmas": per[l]["lemmas"], "ipa": per[l]["ipa"], "audio": per[l]["audio_ok"], "audioExcluded": per[l]["audio_excluded"]} for l in app_langs}
    frag = manifest_fragment("PRONUNCIATIONS", "clavis-pronunciations", "Pronunciations (IPA + Commons audio references)", app_langs,
                             "CC BY-SA 4.0 (IPA); audio per-file CC0 / CC BY / CC BY-SA / public domain",
                             "https://kaikki.org/dictionary/ (wiktextract, Wiktionary CC BY-SA 4.0); https://commons.wikimedia.org/ (per-file licence via API)",
                             [ATTR_WIKTIONARY, ATTR_COMMONS], files, stats)
    write_json(pack_dir / "manifest-fragment.json", frag)
    print(f"\n{'lang':4} {'lemmas':>6} {'IPA':>6} {'%':>5} {'audio':>6} {'%':>5} {'excluded':>8}")
    for l in app_langs:
        p = per[l]
        print(f"{l:4} {p['lemmas']:6d} {p['ipa']:6d} {p['pct_ipa']:5.1f} {p['audio_ok']:6d} {p['pct_audio']:5.1f} {p['audio_excluded']:8d}")
    print(f"excluded reasons: {dict(excluded_reasons)}\ncommons API: {coverage['commons_api']}; timings {timings}")
    return coverage


if __name__ == "__main__":
    main()
