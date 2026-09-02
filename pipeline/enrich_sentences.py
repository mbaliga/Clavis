#!/usr/bin/env python3
"""Tatoeba sentence pack restricted to the Clavis lexicon (text CC BY 2.0 FR; audio per clip).

Output  pipeline/build/packs/sentences/<lang>.json.gz
        [ {id, lang, text, en?, lemmas, audioUrl?, audioLicence?, audioBy?}, … ]      (Packs.kt Sentence)
        pipeline/build/packs/sentences/ATTRIBUTION-tatoeba.json     per-sentence author / audio credit
        pipeline/build/packs/sentences/manifest-fragment.json        (PackManifest, kind SENTENCES)
        pipeline/build/enrich/sentence_coverage.json

Method per enabled language (Tatoeba ISO-639-3 from the registry):
  1. stream sentences.csv once; keep the language's sentences plus every English sentence text;
  2. tokenise exactly like annotate.py (enrich_common.Tokenizer: simplemma / fugashi / mecab-ko /
     jieba / Arabic regex) and keep sentences with 3–14 tokens where ≥ 80 % of the tokens map to a
     lexicon lemma;
  3. attach an English translation through links.csv (direct links only);
  4. attach audio only when its licence is CC BY / CC BY-SA / CC0 (empty, \\N and NC rows excluded);
  5. rank by (has permissive audio, has English, coverage, distinct lexicon lemmas), run a greedy set
     cover so every lexicon lemma that occurs in any candidate is in at least one kept sentence, then
     fill up to the cap (3,000) in rank order.

usage: enrich_sentences.py [--lexicon PATH] [--languages …] [--tatoeba-dir DIR] [--out-dir DIR]
                           [--cap 3000] [--min-coverage 0.8] [--workers 2] [--include-en]
"""
from __future__ import annotations

import argparse
import heapq
import json
import re
import sys
import time
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from enrich_common import (  # noqa: E402
    load_lexicon,
    ATTR_TATOEBA, ATTR_TATOEBA_AUDIO, BUILD_DIR, DEFAULT_LEXICON, DEFAULT_TATOEBA_DIR, HAN_RE,
    load_registry, log, manifest_fragment, permissive_licence, tokenizer, write_json, write_json_gz,
)

AUDIO_URL = "https://audio.tatoeba.org/sentences/{lang3}/{sid}.mp3"   # verified 2026-09-01 (HEAD → 200 audio/mpeg)
SENTENCE_URL = "https://tatoeba.org/en/sentences/show/{sid}"
MIN_TOK, MAX_TOK = 3, 14
CJK_LANGS = {"ja", "zh", "ko"}


def prefilter(lang: str, text: str) -> bool:
    """Cheap gate before tokenising millions of lines."""
    if len(text) > 160 or "\n" in text:
        return False
    if lang in ("ja", "zh"):
        return 3 <= len(text) <= 60 and bool(HAN_RE.search(text) or lang == "ja")
    n = text.count(" ") + 1
    return MIN_TOK - 1 <= n <= MAX_TOK + 4


def score_language(args):
    """Worker: tokenise one language's candidate sentences and return the qualifying ones."""
    lang, rows, lemma_set, min_cov, registry = args
    tok = tokenizer(lang, registry)
    out = []
    t0 = time.time()
    for sid, text in rows:
        try:
            toks = tok.tokens(text)
        except Exception:
            continue
        n = len(toks)
        if n < MIN_TOK or n > MAX_TOK:
            continue
        hit = []
        for s, l in toks:
            if l in lemma_set:
                hit.append(l)
            elif s.lower() in lemma_set:
                hit.append(s.lower())
            elif lang == "zh" and s in lemma_set:
                hit.append(s)
        cov = len(hit) / n
        if cov >= min_cov:
            seen, lem = set(), []
            for l in hit:
                if l not in seen:
                    seen.add(l)
                    lem.append(l)
            out.append((sid, text, lem, round(cov, 3), n))
    return lang, out, round(time.time() - t0, 1)


def greedy_cover(cands: list[dict], cap: int) -> list[dict]:
    """Lazy greedy set cover over `lemmas`, ties broken by rank order, then fill by rank."""
    covered: set[str] = set()
    chosen: list[int] = []
    taken = [False] * len(cands)
    heap = [(-len(c["lemmas"]), i) for i, c in enumerate(cands)]
    heapq.heapify(heap)
    while heap and len(chosen) < cap:
        neg_gain, i = heapq.heappop(heap)
        gain = len(set(cands[i]["lemmas"]) - covered)
        if gain != -neg_gain:              # stale upper bound: re-queue with the true gain
            heapq.heappush(heap, (-gain, i))
            continue
        if gain == 0:                      # the true maximum gain is 0: everything coverable is covered
            break
        chosen.append(i)
        taken[i] = True
        covered.update(cands[i]["lemmas"])
    for i in range(len(cands)):
        if len(chosen) >= cap:
            break
        if not taken[i]:
            chosen.append(i)
            taken[i] = True
    chosen.sort()          # rank order in the output
    return [cands[i] for i in chosen]


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lexicon", default=str(DEFAULT_LEXICON))
    ap.add_argument("--languages", default=None)
    ap.add_argument("--tatoeba-dir", default=str(DEFAULT_TATOEBA_DIR))
    ap.add_argument("--out-dir", default=str(BUILD_DIR))
    ap.add_argument("--cap", type=int, default=3000)
    ap.add_argument("--min-coverage", type=float, default=0.8)
    ap.add_argument("--workers", type=int, default=2)
    ap.add_argument("--include-en", action="store_true", help="also build a pack for the bridge language")
    args = ap.parse_args(argv)

    t_all = time.time()
    timings = {}
    tdir = Path(args.tatoeba_dir)
    out_dir = Path(args.out_dir)
    enrich_dir, pack_dir = out_dir / "enrich", out_dir / "packs" / "sentences"
    pack_dir.mkdir(parents=True, exist_ok=True)
    enrich_dir.mkdir(parents=True, exist_ok=True)
    lexicon = load_lexicon(args.lexicon)
    want = args.languages.split(",") if args.languages else list(lexicon)
    registry = load_registry(want)
    app_langs = [l for l in want if l in lexicon and l in registry and registry[l].get("tatoebaCode")
                 and (l != "en" or args.include_en)]
    lang3_of = {l: registry[l]["tatoebaCode"] for l in app_langs}
    of_lang3 = defaultdict(list)
    for l, c in lang3_of.items():
        of_lang3[c].append(l)
    log(f"languages: {lang3_of}")

    # 1. sentences.csv
    t0 = time.time()
    rows = defaultdict(list)
    eng = {}
    n_lines = 0
    with open(tdir / "sentences.csv", encoding="utf-8", errors="replace") as f:
        for line in f:
            n_lines += 1
            p = line.rstrip("\n").split("\t", 2)
            if len(p) < 3:
                continue
            sid, l3, text = p
            if l3 == "eng":
                eng[int(sid)] = text
            for l in of_lang3.get(l3, ()):
                if prefilter(l, text):
                    rows[l].append((int(sid), text))
    timings["sentences_csv"] = round(time.time() - t0, 1)
    log(f"  sentences.csv: {n_lines} lines; english {len(eng)}; candidates after prefilter " + str({l: len(v) for l, v in rows.items()}))

    # 2. tokenise + coverage
    t0 = time.time()
    from multiprocessing import Pool
    jobs = [(l, rows[l], set(lexicon[l].keys()), args.min_coverage, registry) for l in app_langs if rows.get(l)]
    cands = {}
    with Pool(min(args.workers, max(1, len(jobs)))) as pool:
        for lang, out, secs in pool.imap_unordered(score_language, jobs):
            cands[lang] = out
            log(f"  {lang}: {len(out)} candidates (≥{args.min_coverage:.0%} lexicon coverage, {MIN_TOK}–{MAX_TOK} tokens) in {secs}s")
    rows.clear()
    timings["tokenise"] = round(time.time() - t0, 1)

    # 3. links.csv → English
    t0 = time.time()
    cand_ids = {sid for l in cands for sid, *_ in cands[l]}
    en_of = {}
    with open(tdir / "links.csv", encoding="utf-8", errors="replace") as f:
        for line in f:
            a, _, b = line.partition("\t")
            try:
                a, b = int(a), int(b)
            except ValueError:
                continue
            if a in cand_ids and b in eng:
                if a not in en_of or b < en_of[a]:
                    en_of[a] = b
    timings["links_csv"] = round(time.time() - t0, 1)
    log(f"  links.csv: {len(en_of)} of {len(cand_ids)} candidates have an English translation")

    # 4. audio (permissive only)
    t0 = time.time()
    audio = {}
    audio_seen = defaultdict(int)
    with open(tdir / "sentences_with_audio.csv", encoding="utf-8", errors="replace") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) < 4:
                continue
            try:
                sid = int(p[0])
            except ValueError:
                continue
            if sid not in cand_ids:
                continue
            lic = permissive_licence(p[3])
            audio_seen["permissive" if lic else "excluded"] += 1
            if lic and sid not in audio:
                audio[sid] = {"audioId": int(p[1]) if p[1].isdigit() else p[1], "by": p[2], "licence": lic,
                              "attributionUrl": p[4] if len(p) > 4 and p[4] not in ("", "\\N") else None}
    timings["audio_csv"] = round(time.time() - t0, 1)
    log(f"  audio rows for candidates: {dict(audio_seen)}")

    # 5. rank + set cover
    kept = {}
    for l in app_langs:
        lex_n = len(lexicon[l])
        cl = []
        for sid, text, lem, cov, n in cands.get(l, []):
            cl.append({"id": sid, "text": text, "lemmas": lem, "cov": cov, "n": n,
                       "en": en_of.get(sid), "audio": audio.get(sid)})
        cl.sort(key=lambda c: (0 if c["audio"] else 1, 0 if c["en"] is not None else 1, -c["cov"], -len(c["lemmas"]), c["id"]))
        kept[l] = greedy_cover(cl, args.cap)

    # 6. authors for attribution (sentences_detailed.csv, kept ids + their English ids)
    t0 = time.time()
    need = set()
    for l in kept:
        for c in kept[l]:
            need.add(c["id"])
            if c["en"] is not None:
                need.add(c["en"])
    author = {}
    det = tdir / "sentences_detailed.csv"
    if det.exists():
        with open(det, encoding="utf-8", errors="replace") as f:
            for line in f:
                a, _, rest = line.partition("\t")
                try:
                    sid = int(a)
                except ValueError:
                    continue
                if sid in need:
                    p = rest.rstrip("\n").split("\t")
                    if len(p) >= 3 and p[2] not in ("\\N", ""):
                        author[sid] = p[2]
    timings["detailed_csv"] = round(time.time() - t0, 1)

    # 7. emit
    per, files, attribution = {}, {}, {}
    for l in app_langs:
        lang3 = lang3_of[l]
        out, attr = [], {}
        covered = set()
        n_audio = n_en = 0
        for c in kept[l]:
            s = {"id": c["id"], "lang": l, "text": c["text"]}
            if c["en"] is not None:
                s["en"] = eng.get(c["en"])
                n_en += 1
            s["lemmas"] = c["lemmas"]
            covered.update(c["lemmas"])
            a = c["audio"]
            if a:
                s["audioUrl"] = AUDIO_URL.format(lang3=lang3, sid=c["id"])
                s["audioLicence"] = a["licence"]
                s["audioBy"] = a["by"]
                n_audio += 1
            out.append(s)
            rec = {"by": author.get(c["id"])}
            if c["en"] is not None:
                rec["en"] = {"id": c["en"], "by": author.get(c["en"])}
            if a:
                rec["audio"] = {"by": a["by"], "licence": a["licence"], "attributionUrl": a["attributionUrl"], "audioId": a["audioId"]}
            attr[str(c["id"])] = rec
        files[f"{l}.json.gz"] = write_json_gz(pack_dir / f"{l}.json.gz", out)
        attribution[l] = attr
        in_any = set()
        for _sid, _t, lem, _c, _n in cands.get(l, []):
            in_any.update(lem)
        per[l] = {"tatoeba_lang": lang3, "candidates": len(cands.get(l, [])), "kept": len(out), "with_audio": n_audio,
                  "with_english": n_en, "lemmas_covered": len(covered), "lemmas_in_any_candidate": len(in_any),
                  "lexicon_lemmas": len(lexicon[l]),
                  "pct_lexicon_covered": round(100 * len(covered) / max(1, len(lexicon[l])), 1)}
    write_json(pack_dir / "ATTRIBUTION-tatoeba.json", {
        "text_licence": "CC BY 2.0 FR (https://creativecommons.org/licenses/by/2.0/fr/)",
        "source": "https://tatoeba.org/ — weekly exports sentences.csv, links.csv, sentences_with_audio.csv, sentences_detailed.csv",
        "audio_licences": "per clip, only CC BY 4.0 / CC BY-SA 4.0 / CC0 1.0 referenced; empty, unspecified and NC-licensed clips excluded",
        "audio_url_scheme": AUDIO_URL,
        "sentence_url_scheme": SENTENCE_URL,
        "sentences": attribution,
    }, indent=None)
    timings["total"] = round(time.time() - t_all, 1)
    coverage = {"lexicon": args.lexicon, "per_language": per, "audio_rows_for_candidates": dict(audio_seen),
                "cap": args.cap, "min_coverage": args.min_coverage, "token_range": [MIN_TOK, MAX_TOK], "timings_seconds": timings}
    write_json(enrich_dir / "sentence_coverage.json", coverage)
    stats = {l: {"sentences": per[l]["kept"], "withAudio": per[l]["with_audio"], "withEnglish": per[l]["with_english"],
                 "lemmasCovered": per[l]["lemmas_covered"], "candidates": per[l]["candidates"]} for l in app_langs}
    frag = manifest_fragment("SENTENCES", "clavis-sentences", "Example sentences (Tatoeba)", app_langs,
                             "CC BY 2.0 FR (text); audio per clip CC BY 4.0 / CC BY-SA 4.0 / CC0 1.0",
                             "https://tatoeba.org/ (exports of 2026-08-29)", [ATTR_TATOEBA, ATTR_TATOEBA_AUDIO],
                             {**files, "ATTRIBUTION-tatoeba.json": (pack_dir / "ATTRIBUTION-tatoeba.json").stat().st_size}, stats)
    write_json(pack_dir / "manifest-fragment.json", frag)
    print(f"\n{'lang':4} {'tat':>4} {'candidates':>10} {'kept':>5} {'audio':>5} {'english':>7} {'lemmas covered':>14} {'of coverable':>12} {'% lexicon':>9}")
    for l in app_langs:
        p = per[l]
        print(f"{l:4} {p['tatoeba_lang']:>4} {p['candidates']:10d} {p['kept']:5d} {p['with_audio']:5d} {p['with_english']:7d} {p['lemmas_covered']:14d} {p['lemmas_in_any_candidate']:12d} {p['pct_lexicon_covered']:9.1f}")
    print(f"timings {timings}")
    return coverage


if __name__ == "__main__":
    main()
