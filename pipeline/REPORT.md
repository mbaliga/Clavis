# Corpus pipeline — build report (2026-09-02)

Build machine: 4 vCPU shared, 2 worker processes. Corpus: framagit `peppercarrot/webcomics` sparse clone
(39 episodes, 363 page PNGs at 992 px, 69 language folders). Registry: `languages.json` — 69 entries,
17 enabled (es fr it de nl ja ko zh ar + en bridge + pt la ru pl uk tr hi), 9 on by default.
Outputs: `build/packs/` (18.9 MB, five manifests), `build/clavis.html` (20.7 MB), `build/work/` intermediates,
`golden/framagit-2026-09.json`. Every number below is read from `build/work/validate.json`,
`annotate-stats.json`, `hygiene-report.json`, `timings.json`, `packs/manifest.json` and `smoke-result.json`.

## 1. Stage timings

| stage | new corpus (39 ep, 17 languages) | old corpus (23 ep, 10 languages) | reference scripts (old corpus) |
|---|---:|---:|---:|
| extract | 26.4 s | 0.6 s | 1 s |
| annotate | 66.8 s (49.7 s tokenise + bridges, 17 s hygiene incl. loading 14 kaikki word sets) | 10.3 s | **397 s** |
| pseudo | 3.1 s | 1.1 s | 1 s |
| pack_assets | 6.5 s (page WebPs cached from the previous pass; 49 s cold for 305 pages) | 23.3 s | 28 s (pack.py, incl. html) |
| pack_web | 2.4 s | 1.2 s | — |
| validate | 0.5 s | 0.2 s | — |
| smoke (Playwright boot) | 18.0 s | 13.2 s | 14 s |
| **total** | **≈ 2 min** | **50 s** | ≈ 7.5 min |

The 397 s → 10 s annotate speed-up comes from bucketing bridge candidates by the initial letter that the
reference already required to match (`same_head`) plus a length bound that rules out sim < 0.67 before
computing Levenshtein, and from caching `strip_diac`. Results are byte-identical to the reference (all
649 scenes' token maps and the whole lexicon compared equal). A second hotspot appeared only with more
languages: simplemma's dictionary factory keeps 8 dictionaries and we cycle through 13, so it reloaded
one per scene (1.2 s each, 6 min for 40 scenes); `annotate._simplemma()` now holds one `Lemmatizer` with
a 64-entry cache.

## 2. Regression against the reference build (old 2017 snapshot, frozen ten languages, hygiene off)

`run.py --corpus <old> --languages core --no-hygiene --check golden/old-corpus.json` reproduced the
documented numbers exactly — 852 raw → 649 kept scenes, 163 → 156 pages, every per-language scene,
lemma, token and bridge count, credible-rect percentages, 10 × 5 foils — **with the reference's tspan
walk**. Field-by-field diff against the reference run's `scenes_raw.json`, `scenes.json`, `lexicon.json`:
0 differences.

The final code keeps a correctness fix that moves those numbers (expected and documented, not a
regression): the reference walked *every* `<tspan>` under a `<text>` element, and Inkscape 1.x nests a
styling `<tspan>` inside each `sodipodi:role="line"` tspan, so that text was counted twice
("Shkak ! ! ! Shkak ! ! !", ep39 balloons with every line doubled). `extract.py` now takes one line per
`role="line"` tspan (direct children as the fallback). On the old corpus this changes 57 scenes' texts
and 15 rects, and 28 sound-effect lines that only passed the ≥ 2-word gate because they were doubled
disappear:

| | reference (`golden/old-corpus.json`) | with the fix (`golden/old-corpus-fixed.json`) |
|---|---:|---:|
| raw scenes / pages | 852 / 163 | 824 / 162 |
| kept scenes / pages | 649 / 156 | 631 / 156 |
| kept per language es fr it de nl ja ko zh ar en | 634 634 648 642 641 648 329 625 177 649 | 616 616 630 624 623 630 326 607 177 631 |
| lemmas es fr it de nl ja ko zh ar en | 1828 1863 1845 1923 1857 2088 867 1424 605 1832 | 1805 1838 1821 1900 1835 2074 865 1410 605 1807 |
| tokens es / en / ja | 7557 / 7776 / 9140 | 7468 / 7688 / 9054 |
| credible rects (unchanged within 0.5 pt) | es 89.9 … ko 80.9 … ar 81.9 | ja 90.0, ko 80.7, zh 90.1, en 89.7 |

`old-corpus.json` stays the reference build's record; `old-corpus-fixed.json` is what the current code
must reproduce. On the new corpus the fix affected 30 raw scenes (ep13 ×8, ep14, ep16 ×2, ep38, ep39 ×18);
one scene with a genuinely repeated line remains.

**Hygiene deltas on the old corpus** (same run with hygiene on, `build/old-corpus-hygiene/`): kept scenes
631 → **623** — the eight balloons removed are the patron lists of ep03 P08, ep05 P05, ep06 P10, ep07 P06,
ep08 P07 and ep09 P08 (×3): 124–486 English tokens each, 95 % Title-Case names. Those eight scenes alone
held ~750 "lemmas" per Latin-script language: es 1805 → 1048 once they are gone, → 1011 after
attestation (−37 more: Chaosah, Shichimi, Komona …); fr 1838 → 1071 → 1037; it 1821 → 1052 → 997;
de 1900 → 1130 → 1064; nl 1835 → 1068 → 998; en 1807 → 1041 → 1007; ar 605 → 583; ja/ko/zh exempt.
So roughly 40 % of the v0.10 Latin-script lexicons were patron names — the "translator names and credit
tokens that survived into v0.10" of D-7, quantified.

## 3. New corpus — per-language table

Raw: **1,751 aligned balloons on 314 of 363 pages** (374 flowRoot / 1,377 text); kept after the English
junk/credits filters and the patron-list rule: **1,460 scenes on 305 pages** (278 flow / 1,182 text).
0 unparseable SVGs (the old snapshot's broken `ep20/nl/E20P06.svg` is fixed upstream).

| lang | episodes | scenes | pages | lemmas before | after hygiene | removed | tokens | credible rect % | bridged tokens | foils | tokenizer | freq source |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|
| es | 39 | 1442 | 304 | 1680 | 1627 | 53 | 9901 | 93.1 | 1938 | 10 | simplemma | wordfreq |
| fr | 39 | 1444 | 304 | 1826 | 1767 | 59 | 10105 | 93.4 | 1992 | 10 | simplemma | wordfreq |
| it | 38 | 1358 | 292 | 1680 | 1598 | 82 | 9205 | 92.8 | 1966 | 10 | simplemma | wordfreq |
| de | 39 | 1433 | 302 | 1872 | 1770 | 102 | 9676 | 93.2 | 1383 | 10 | simplemma | wordfreq |
| nl | 39 | 1411 | 301 | 1819 | 1700 | 119 | 9906 | 93.1 | 1395 | 10 | simplemma | wordfreq |
| ja | 39 | 1423 | 299 | 2062 | 2062 | exempt | 12798 | 93.8 | 1088 | — | fugashi | wordfreq |
| ko | 33 | 470 | 137 | 1115 | 1115 | exempt | 4327 | 83.8 | 0 | — | mecab-ko | wordfreq |
| zh | 39 | 1403 | 298 | 2439 | 2439 | exempt | 10300 | 93.4 | 1116 | — | jieba | wordfreq |
| ar | 8 | 177 | 48 | 605 | 583 | 22 | 1099 | 80.8 | 0 | — | arabic | wordfreq |
| en | 39 | 1460 | 305 | 1651 | 1603 | 48 | 10450 | 93.1 | — | — | simplemma | wordfreq |
| pt | 37 | 1299 | 282 | 1621 | 1557 | 64 | 9244 | 92.5 | 2839 | 10 | simplemma | wordfreq |
| la | 17 | 389 | 106 | 766 | 642 | 124 | 2020 | 86.1 | 291 | 10 | simplemma | corpus |
| ru | 35 | 1067 | 250 | 1833 | 1757 | 76 | 6541 | 90.9 | 1210 | 10 | simplemma | wordfreq |
| pl | 38 | 1357 | 293 | 1967 | 1863 | 104 | 7383 | 92.5 | 653 | 10 | simplemma | wordfreq |
| uk | 27 | 780 | 185 | 1559 | 1431 | 128 | 4785 | 88.8 | 1154 | 10 | simplemma | wordfreq |
| tr | 8 | 223 | 54 | 475 | 437 | 38 | 1075 | 87.0 | 43 | 10 | simplemma | wordfreq |
| hi | 2 | 16 | 6 | 49 | 45 | 4 | 57 | 37.5 | 0 | — | simplemma | wordfreq |

"lemmas before" is after the patron-list scenes are gone and before attestation. Latin Zipf values are
corpus-derived (`6.5 + log10(count / maxCount)`, range 4.0–6.5) and not comparable with wordfreq; Latin
hygiene therefore uses attestation alone (macron-insensitive against Wiktionary Latin). Credible rects
improve on every language versus v0.10 (≈ 90 → 93 %) because the text-era reconstruction no longer
counts nested tspans twice. Hindi (2 episodes, 16 scenes, 37.5 % credible rects) is data-starved and
should stay opt-in; Arabic is still only ep01–08 upstream.

## 4. What the 16 new episodes (ep24–39) added

| | ep01–23 | ep24–39 | total |
|---|---:|---:|---:|
| kept scenes | 635 | **825** | 1460 |
| pages with scenes | 157 | 148 | 305 |
| kept scenes per language | — | es 822 · fr 825 · it 724 · de 808 · nl 809 · ja 789 · zh 792 · pt 669 · pl 727 · ru 433 · uk 153 · ko 63 · tr 66 · **ar 0 · la 0 · hi 0** | |

The new episodes more than double the corpus for the Romance/Germanic lanes, Japanese and Chinese
(ep34–39 alone contribute 102, 73, 159, 78, 91 and 123 raw balloons). Korean gains only 63 scenes
(ep24–26, 28, 31 carry `kr`), and Arabic, Latin and Hindi gain nothing — their coverage cliffs are upstream
facts the app must show per language (D-4/D-6). ep01–23 themselves are *not* identical to the 2017
snapshot: on the frozen ten languages the framagit copies yield 807 raw balloons where the snapshot gave
852 (87 English element ids lost, 44 gained; `E02P04` was renumbered `E02P02`, `E07P01` no longer has
text), because several English pages were re-lettered upstream after 2017.

## 5. Mixed-format pages

41 pages carry both `<flowRoot>` and `<text>` balloons in the English SVG (ep02 P02, ep04 P07, ep13 P06,
ep18 ×4, ep19 ×3, ep21 ×4, ep22 ×6, ep26 ×2, ep27 ×2, ep28, ep29 ×5, ep30–39 one or two each). The
extractor decides per element (flowRoot → `flowRegion/rect`; text → tspan reconstruction), so all 41 are
handled without an episode switch; the kept scenes split 278 flow / 1,182 text.

## 6. Lexicon hygiene (D-7) — rule, counts, audit sample

Rule: a lemma is dropped when wordfreq has never seen lemma or surface (zipf 0) **and** neither the lemma
nor its first surface is a headword or inflected form in the kaikki Wiktionary extract for that language;
CJK exempt; corpus-frequency languages (Latin) use attestation alone. Dropped lemmas leave the lexicon and
their tokens leave the scene token lists (balloon text untouched). Plus the scene-level rule for patron
lists (≥ 20 English tokens, ≥ 70 % Title-Case). Both are off under `--no-hygiene`.

**Removed: 1,023 lemmas (2,338 tokens) + 8 patron-list scenes.** Most frequent removals per language
(from `build/work/hygiene-report.json`, 40 per language kept there):

- es 1680 → 1627: chaosah ×29, shichimi ×17, komona ×16, celandria ×14, hippiah ×9, microdimensión ×7, caliciudad ×5, hereva ×5, artance ×4, g'mic ×4, valvin ×4
- fr 1826 → 1767: chaosah, shichimi, komona, hippiah, microdimension ×5, qualicity ×5, artance, g'mic, valvin, d'hereva
- de 1872 → 1770: chaosāh ×24, komona, shichimi, coriander ×14, mikrodimension ×7, zauberwettbewerb ×6, qualicity, einheitsbaum ×4
- nl 1819 → 1700: chaosah, shichimi, komona, hippiah, toverdrankwedstrijd ×8, thym ×7, magiewedstrijd ×6, microdimensie ×6, kwalistad ×5
- en 1651 → 1603: chaosah, komona, hippiah, hereva, qualicity, artance, valvin, g'mic, shichimi's, zombiah, aquah
- la 766 → 642: episodium ×36, episodio ×12, chaosahis ×10, shhh ×10, maecenatu ×8, krita ×7, linux ×7, gnu ×5
- ru 1833 → 1757: морквик ×29, ситими ×16, комоны ×10, patrons ×7, зельевар ×7, maloney ×5, шшух ×5 · uk 1559 → 1431: спонсується ×15, krita ×12, морквич ×10, комони ×8
- pl 1967 → 1863: open-source'owy ×34, shichimi, coriander, szzz ×10, hippiah, herevy · pt 1621 → 1557: chaosah, shichimi, coriander, komona, camomile ×4
- tr 475 → 437: carrot ×15, komona ×11, şıkır ×10, krita ×5 · ar 605 → 583: لفلفل ×4, لكفلائي ×3, مفتحوح ×3 · hi 49 → 45

Audit verdict: the removals are character/place names (Chaosah, Shichimi, Komona, Hippiah, Hereva,
Qualicity), credit tokens (Krita, G'MIC, Valvin, Artance, Linux) and onomatopoeia. Known false positives:
genuine compounds/neologisms the dictionaries lack — `microdimensión`, `toverdrankwedstrijd`,
`zauberwettbewerb`, `einheitsbaum`, Russian `зельевар`, Ukrainian `спонсується` (a real verb form) — a
few per language, listed in the report file so they can be whitelisted later.

## 7. Packs and web build

`build/packs/` = **18.89 MB**: comics 14.87 MB (305 WebP pages at 640 px q55 = 13.3 MB, `scenes.json.gz`
1.4 MB, `pages.json`), lexicon 0.27 MB, and the enrichment packs merged from their manifest fragments —
sentences 3.27 MB, pronunciations 0.21 MB, etymology 0.16 MB — plus `manifest.json` (5 PackManifest
objects, 332 files, all present on disk), `languages.json`, `ATTRIBUTION.md` (David Revoy, 215 translator
credits from `info.json`, wordfreq, tokenisers, and one section per enrichment pack).
**Nine-language subset** (the frozen ten incl. en, comics + lexicon): 1,459 scenes on the same 305 pages,
scenes 1.06 MB gz + lexicon 0.19 MB gz + pages 13.32 MB = **14.57 MB**; all 17 languages comics + lexicon
+ manifest = 15.25 MB — the extra languages cost 0.7 MB because page art dominates and is shared.

Web build (`pack_web.py`, filtered to the frozen ten): 1,459 of 1,460 scenes, 305 pages, JSON 4,773 KB →
gz 1,366 KB; `clavis.html` 20,698 KB (v0.10: 11,077 KB — twice the pages). **Smoke test: PASS** —
0 page errors, 0 JS console errors, onboarding "Nine languages. One graph." rendered, `D.scenes` = 1459
with sequential ids, 305 images = 305 pages, etymology present for all ten languages, load 14.3 s
(file parse of a 21 MB page; the only failed request is the template's Google Fonts link, offline).

## 8. Problems

1. **`languages.json` colour is a `"0xAARRGGBB"` string** (as briefed) while `LanguageInfo.colour` is a
   `Long`, and the registry carries pipeline-only keys — the app's loader needs a colour parser (or the
   pipeline must emit a number) and `ignoreUnknownKeys`. No loader exists in `:shared` yet to test against.
2. **The enrichment packs were built against the v0.10 lexicon**, not this run's: their manifest stats
   say `lemmas: 1832` (en) and they cover the nine languages only. They are merged as delivered; rerun
   `run.py --enrich` (offline) against `build/work/lexicon.json` for the 17-language, post-hygiene lexicon.
3. **`EtymEdge` serialisation**: the pack writes `[lang, lemma, type]` triples (web format); the Kotlin
   data class defaults to an object form (noted in ENRICHMENT.md §5) — needs a custom serializer.
4. ep01–23 framagit SVGs differ from the 2017 snapshot (807 vs 852 balloons on the ten languages); the
   v0.10 numbers are therefore not reachable from the new source even with the reference code.
5. Hindi has 2 episodes and unusable rect quality; Arabic and Latin gain nothing after ep08/ep17.
6. Turkish lower-casing is now registry-driven (`caseFolding: "turkic"`); other locale-specific case rules
   (none needed for the enabled set) would need the same field.
7. Korean lemmas are still mecab surface tokens (inflected), as in the reference; Arabic has no morphology.

## 9. Next steps

- App side: registry loader (colour string → Long, unknown keys), pack loader for the flat `E05P03` page
  keys and `comics/pages.json`, `EtymEdge` array serializer; then `pack_assets.py --install`.
- Rerun the enrichment stages on the new lexicon (`run.py --enrich`), commit the refreshed fragments.
- Whitelist file for hygiene false positives (compounds), and consider a token-level Title-Case name
  filter for short credit balloons that the ≥ 20-token rule does not catch.
- Korean dictionary-form lemmatisation (MeCab `Inflect` feature) and Arabic CAMeL lemmas (spec §5f) —
  both change golden numbers, so land them with a new golden file.
- Optional HD page pack (992 px native) once the base APK budget is known; page art is 70 % of the pack.
- `--episodes` and `--languages` make partial rebuilds cheap; a CI job should run the old-corpus check
  (`golden/old-corpus-fixed.json`) and `--check golden/framagit-2026-09.json` on every pipeline change.
