# Enrichment stages: etymology, pronunciations, sentences

Three stages that run after `annotate.py` has produced `lexicon.json` and turn the Clavis lexicon
into the three optional content packs of D-7: **etymology** (Wiktionary via kaikki.org unioned with
EtymDB 2.1), **pronunciations** (IPA + Wikimedia Commons audio *references* with per-file licences)
and **sentences** (Tatoeba, restricted to the lexicon, with permissively licensed audio references).

Files owned by these stages:

| File | Role |
|---|---|
| `enrich_common.py` | registry loading, script-aware normalisation, lexicon-side match candidates, tokenisers that mirror `annotate.py`, the kaikki streaming matcher (cached), licence classification, manifest helpers |
| `enrich_etymology.py` | Wiktionary graph + EtymDB port + union → `build/packs/etymology/etym.json.gz` |
| `enrich_pronunciations.py` | IPA + Commons audio with licence lookup → `build/packs/pronunciations/<lang>.json.gz` |
| `enrich_sentences.py` | Tatoeba selection + set cover → `build/packs/sentences/<lang>.json.gz` |
| `tests/test_enrich_*.py` | 80 pytest tests on synthetic fixtures (20-line kaikki extract, mini lexicon, mini EtymDB, mini Tatoeba CSVs) |
| `build/enrich/` | provenance, coverage reports, caches (kaikki matches, Commons answers) |

Inputs are never committed: the compacted kaikki extracts (`<Language>.jsonl.gz`, produced by
`kaikki_compact.py` from the per-language kaikki.org dumps), two raw-descendants side files
(`raw/Latin.descendants.jsonl`, `raw/Chinese.descendants.jsonl`, see §1.3), the EtymDB 2.1 split
CSVs, and the Tatoeba weekly exports. All paths are CLI arguments with scratchpad defaults.

---

## 0. Run commands and runtime

```bash
. $SCRATCH/pipeenv/bin/activate
cd /home/user/Clavis/pipeline

# A. etymology (≈ 50 s cold; ≈ 15 s when the kaikki match cache is warm)
python enrich_etymology.py --lexicon build/lexicon.json \
    --kaikki-dir $SCRATCH/data/kaikki --etymdb-dir $SCRATCH/etymdb/data/split_etymdb \
    --desc-dir $SCRATCH/data/kaikki/raw --out-dir build

# B. pronunciations (network: Commons API; measured 117 requests / 5,243 files ≈ 30 min at the rate
#    Commons tolerates from this egress, seconds afterwards thanks to build/enrich/commons_cache.json;
#    --offline never calls the API and treats uncached files as unverified = excluded)
python enrich_pronunciations.py --lexicon build/lexicon.json --kaikki-dir $SCRATCH/data/kaikki --out-dir build

# B2. second audio source for es/it/pl/ja/ko/la (§6.4), off by default, on top of B:
python enrich_pronunciations.py --lexicon build/lexicon.json --kaikki-dir $SCRATCH/data/kaikki --out-dir build \
    --lingualibre --commons-categories
# or via run.py: python run.py --enrich --network --lingualibre --commons-categories

# C. sentences (≈ 80 s: one pass over sentences.csv 756 MB, links.csv 455 MB, sentences_detailed.csv 1.4 GB)
python enrich_sentences.py --lexicon build/lexicon.json --tatoeba-dir $SCRATCH/data/tatoeba --out-dir build

python -m pytest tests/test_enrich_*.py -q     # 80 passed
```

Every stage takes `--languages es,fr,…` (default: every lexicon language that the registry knows),
`--workers` (≤ 2, the streaming passes are the only parallel part) and `--out-dir`. All three are
deterministic for a given lexicon and input snapshot (sorted iteration, fixed tie-breaks).

---

## 1. Etymology (`enrich_etymology.py`)

### 1.1 Matching corpus lemmas to Wiktionary entries

Each compacted kaikki dump is streamed once per language and only entries that hit a lexicon key are
kept (cached as `build/enrich/kaikki_matches.<lang>.<digest>.json.gz`, digest = lexicon lemmas +
matcher version). Keys are generated on the lexicon side (`enrich_common.lexicon_keys`) and applied
identically to headwords and to `forms[]` (`f`):

| Script | Keys tried, in rank order (an exact-key hit suppresses derived-candidate hits) |
|---|---|
| Latin/Cyrillic | NFKC + lowercase; then diacritic-stripped |
| Arabic | strip diacritics, tatweel, Quranic marks and Cf characters, fold `إأآٱ → ا`, strip trailing punctuation; then without `و` / `ال` / `وال` / `فال` / one-letter proclitic |
| Chinese | headword; OpenCC `s2t`; OpenCC `t2s` — plus every `f` form (kaikki's "Simplified-Chinese" forms are what map the simplified P&C lexicon onto traditional headwords) |
| Japanese | headword (UniDic `orthBase` is already a dictionary form); `f` forms cover hiragana and kyūjitai spellings |
| Korean | token; token + `다`; past/honorific contraction undone (`갔→가다`, `먹었→먹다`, `마셨→마시다`, `했→하다`); verbal endings stripped → stem + `다`. Only verb/adjective headwords are ever derived — bare stripped stems are not looked up, so 하고 cannot be explained by the entry for 하 |

The Korean homograph guard: when a Korean lemma's matched entries attest two or more different
hanja spellings (a MeCab token like 사 matches one headword with 沙/四/事/死 sections) the lemma is
marked ambiguous and receives no Sinographic facts at all (109 lemmas on the nine-language lexicon).

### 1.2 From templates to typed parent links

Only `etymology_templates` are read — never the free text (`et`), which is used solely as a
"has an etymology section" flag for the coverage report.

| Template names | Hop type |
|---|---|
| `inh`, `inh+`, `inh-lite`, `inherited` | `inh` |
| `bor`, `bor+`, `bor-lite`, `lbor`, `slbor`, `obor`, `ubor`, `calque`/`clq`/`cal`, `psm`, `sl`, `pcal`, `translit` | `bor` |
| `der`, `der+`, `der-lite`, `uder` | `der` (a `der` from a Sinitic stage into Japanese/Korean/Vietnamese is recorded as `bor`) |
| `ety` / `etymon` trees | arg 2 gives the type (`:inh`, `:bor`, `:lbor`, `:clq`, …), arg 3.. give `lang:term>lang:term` chains; `<id:…>` qualifiers dropped |
| `ko-etym-sino` / `ko-etym-Sino` | `bor` from `zh` with the hanja = concatenation of the odd-numbered arguments (`女|female|子|offspring → 女子`) |
| `{{der|ja|ltc|-}}` followed by `ltc-l`/`och-l`/`okm-l`/`zh-l`/`l`/`m` | the linked term fills the missing term |
| `cog`, `cognate` | cognate hint (used for direct edges only) |
| `ncog`/`noncog`, `Han compound`, `liushu`, `ar-root`, `ja-r`, `categorize`, … | ignored |

Affixes (`-ité`, `-tion`) are never origins; reconstructed roots (`*kalb-`) are. Same-language hops
(`der|fr|fr|…`, Chinese variety codes for `zh`) are skipped. Successive templates in one entry form a
chain: hop *k* is at distance *k*; parallel etymons of an `etymon` tree share a distance.

Language names come from `LANGNAME` (etym.py's table, extended with the codes that actually occur:
`la-vul` etc., `ojp`, `okm`, `sit-pro`, …); for any other code the name is learned from the template's
own expansion ("Central Atlas Tamazight ⴰⴼⵣⴰ"), so the data names its own languages.

### 1.3 Origin selection — exactly etym.py's rule

For each lemma, over all hops of all matched entries: the candidate minimising `(distance, rank)`
wins, where rank is the position in `ANC_PRIO` (Latin, Vulgar/Late/Medieval Latin, Ancient Greek,
Sanskrit, Persian, Arabic, Middle/Old Chinese, Old/Middle French, Anglo-Norman, Middle/Old English,
Old Norse, OHG/MHG, Middle/Old Dutch, Frankish, Gothic, Old Spanish, Old Galician-Portuguese, …,
proto-languages) and languages not in the list rank last. The nearest attested ancestor therefore
always wins; the list only breaks ties at equal distance. Rendered as `"<Language name> <lexeme>"`,
`ot = "bor"` if the first hop is a borrowing, else `"inh"` (der collapses to inh as in etym.py).
Unlike the EtymDB matcher, modern languages *are* eligible as Wiktionary origins because the
template names them explicitly (`{{bor|ja|en|episode}}` → "English episode"); the EtymDB half keeps
etym.py's list because EtymDB's `ja ← inh en` rows are known template noise.

### 1.4 Edges

`anc_index[key] → {node: (distance, first-hop type)}` with `key = (lang, normalised term)`; all
Han-script terms from any Sinitic stage share the namespace `('han', traditional form)` so Middle
Chinese 單位, Chinese 單位 and kyūjitai 單位 meet.

1. **Template chains** of the lemma's own entries (distance = chain position).
2. **Descendants lists** of ancestor entries — `raw/Latin.descendants.jsonl` (17,610 Latin entries)
   and `raw/Chinese.descendants.jsonl` (8,158 Chinese entries with Sino-Xenic descendants). The
   compacted extracts had lost this field (the compactor expected the pre-2025 `depth/text/templates`
   shape; the current shape is `{lang_code, word, raw_tags:["borrowed"], descendants:[…]}`), so the
   two raw per-language dumps were streamed once (`raw/desc_filter.py`, 49 s each, nothing else kept).
   A descendant at nesting depth *d* is put under the ancestor at distance *d*, typed `bor` when
   Wiktionary tags it borrowed. Korean descendants written `학생(學生)` are keyed on the hangul.
3. **Self-insertion at distance 0**: a corpus lemma that *is* an ancestor key (Latin `clavis` when
   Latin is a lane; `zh 单位` for `('han','單位')`; `en episode` for `(en, episode)`) joins its own
   key. This is what turns `ja 単位 ← ltc 單位` into a `ja → zh` edge.
4. **One level of attested transitivity through corpus lemmas**: `es hotel ← fr hôtel` and
   `fr hôtel ← la hospitāle` puts `es hotel` under `hospitāle` at distance 2 (both hops are Wiktionary
   statements; the provenance line names both).
5. **Pairing**: nodes of different languages under the same key with combined distance ≤ 4. If one
   side is at distance 0 the edge is direct: `bor` when the other side's first hop is a borrowing,
   else `der`; otherwise `cog`. **Hub guard**: an ancestor with > 40 corpus descendants contributes
   no cognate edges (direct edges bypass the guard, as in etym.py). `cog` templates whose target is a
   corpus lemma give direct `cog` edges; `ncog` never does.
6. Per lemma ≤ 8 edges, ordered (direct before shared-ancestor, then language, then lemma).

### 1.5 The EtymDB half

`build_etymdb()` is `reference-prototype/etym.py` ported into a function: same values/links loading
(negative parents expanded through `links_index`), same per-script keys, same depth-3 DFS that
propagates the first hop's type, same `(distance, rank)` origin rule, direct links typed
`bor/cog/der`, shared-ancestor cognates with `d1 + d2 ≤ 4`, hub guard > 40, cap 8. Changes, all
documented in `pipeline-spec.md` §1.4/§5e: Cf characters stripped from lexemes; registry
`etymdbCodes` alias historical stages onto the app language (`zh ← cmn, ltc, och, lzh`;
`la ← la-vul, la-lat, la-med, …`); every matched node is inserted into its own ancestor set at
distance 0 (so `ja 単位 ← ltc 單位` meets `zh 单位`); a `zh` lemma matched to a Middle/Old Chinese row
gets that row as its origin; affix rows are not origins; `VL./LL./ML.` (which do not exist in the
data) are replaced by `la-vul/la-lat/la-med` in the priority list and name table.

### 1.6 Union and provenance

Per `(lang, lemma)`: origin = Wiktionary's when it has one, else EtymDB's; edges = union keyed on
`(lang, lemma)` with Wiktionary's type winning on conflict, re-capped at 8; `src` lists every
source that has an origin or an edge for the lemma (`"wiktionary"`, `"etymdb"`,
`"wiktionary+etymdb"`); `ipa` is the first untagged IPA of the matched entries. A lemma with neither
origin nor edge is not written (that is what "in graph" counts).

`build/enrich/etym_provenance.jsonl` has one line per emitted fact and per source:
`{"lang","lemma","fact":"origin"|"edge","value",…,"src","via"}` where `via` is the template on the
entry (`wiktionary:es:llave#noun:inh|la|clāvem`), the descendants row
(`wiktionary-descendants:la:thesaurus#noun→es:tesoro`), the shared ancestor and both members' hops
(`shared-ancestor:la:clavis [… | …]`), or the EtymDB row/link ids (`etymdb:value:2`,
`etymdb:link:bor:9~10`, `etymdb:shared-ancestor:2`). Edges attested by both sources get a second
line marked `corroborates`; an EtymDB origin superseded by Wiktionary's is logged as
`superseded_by: wiktionary`. Nothing in `etym.json.gz` lacks such a line (tested).

### 1.7 Coverage on the nine-language Track-A lexicon (`pipeline-run/lexicon.json`)

Measured by the run of 2026-09-02 (`build/enrich/etym_coverage.trackA.json`; the 17-language lexicon is in §6). "Has etymology section" is
the share of lemmas whose matched Wiktionary entries carry any etymology text or template — the
number D-5 quotes (ko 70.1 %, ar 40.3 %, ja 27.6 %, zh 24.5 %, es 51.9 %, fr 48.8 %, it 47.3 %,
de 49.9 %, nl 49.1 %, en 75.8 %; re-measured here with the improved matcher).

<!-- ETYM_TRACKA -->
| Lang | Lemmas | Wiktionary entry found | Has etymology section | With origin | % | With ≥ 1 edge | % | In graph | cog / bor / der endpoints | EtymDB-only (07 §4) | Top origin languages |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---|
| en | 1832 | 1505 | 1418 (77.4 %) | 1251 | **68.3 %** | 807 | 44.1 % | 69.6 % | 1695 / 340 / 81 | 51.4 % | Middle English 775, Latin 98, French 49, Old French 33 |
| es | 1828 | 1062 | 959 (52.5 %) | 841 | **46.0 %** | 662 | 36.2 % | 47.8 % | 1626 / 83 / 23 | 36.8 % | Latin 399, Old Spanish 242, Vulgar Latin 38, Late Latin 34 |
| fr | 1863 | 1075 | 914 (49.1 %) | 782 | **42.0 %** | 657 | 35.3 % | 44.6 % | 1560 / 157 / 60 | 35.0 % | Middle French 287, Old French 228, Latin 157, English 33 |
| it | 1845 | 988 | 883 (47.9 %) | 759 | **41.1 %** | 606 | 32.8 % | 42.3 % | 1603 / 79 / 30 | 31.5 % | Latin 532, Late Latin 74, Vulgar Latin 54, French 21 |
| nl | 1857 | 1002 | 918 (49.4 %) | 755 | **40.7 %** | 514 | 27.7 % | 42.2 % | 1017 / 91 / 57 | 33.1 % | Middle Dutch 576, French 37, Latin 32, English 28 |
| ja | 2088 | 1068 | 620 (29.7 %) | 303 | **14.5 %** | 228 | 10.9 % | 17.8 % | 208 / 196 / 7 | 2.9 % | Middle Chinese 111, English 80, Proto-Japonic 58, Old Japanese 29 |
| ko | 867 | 729 | 677 (78.1 %) | 333 | **38.4 %** | 147 | 17.0 % | 40.8 % | 167 / 137 / 7 | 2.8 % | Chinese 201, Middle Korean 38, English 33, Old Korean 26 |
| zh | 1424 | 1325 | 400 (28.1 %) | 146 | **10.3 %** | 172 | 12.1 % | 18.6 % | 23 / 242 / 6 | 0.6 % | Proto-Sino-Tibetan 73, Middle Chinese 38, English 13, Japanese 7 |
| ar | 605 | 487 | 379 (62.6 %) | 107 | **17.7 %** | 15 | 2.5 % | 18.3 % | 15 / 12 / 3 | 4.5 % | Proto-Semitic 59, Aramaic 12, Proto-West Semitic 7, Hebrew 7 |
| de | 1923 | 1141 | 972 (50.5 %) | 736 | **38.3 %** | 483 | 25.1 % | 40.0 % | 990 / 97 / 71 | 27.4 % | Middle High German 507, Latin 61, English 48, French 30 |

Totals: **6,013 origins** (4,365 inherited, 1,648 borrowed; 5,737 from Wiktionary, 276 from EtymDB where Wiktionary had none) and **10,683 edge endpoints** (8,904 cognate, 1,434 borrowing, 345 derivation; 8,822 from Wiktionary, 1,861 EtymDB-only) over 6,415 lemmas in the graph. Sinographic ja/ko/zh triples: 37 (+174 pairs). Top origin languages overall: Latin 1,281, Middle English 779, Middle Dutch 579, Middle High German 509, Middle French 345, English 288, Old French 277, Old Spanish 242, Chinese 210, French 159, Middle Chinese 153, Late Latin 138. Runtime {'kaikki_match': 40.7, 'wiktionary_graph': 3.2, 'etymdb_graph': 17.1, 'total': 61.4}.

<!-- /ETYM_TRACKA -->

**Why the origin-string share is lower than "has an etymology section".** The gap is by
construction, not a matching loss (entry found ≈ the D-5 "headword found" column):

- Korean (77.5 % → 38.4 %): of the 345 matched lemmas that have an etymology section but no
  origin, 193 are native words whose section says only "of native Korean origin" or cites a Middle
  Korean attestation without a form (`ko-etym-native`, `ko-attest`, `ko-l`/`okm-l`), 86 are
  Sino-Korean homographs excluded by the guard (109 ambiguous lemmas in total, 58 of them
  single-syllable MeCab tokens: 세 = 世/勢/歲, 시 = 市/時/詩, 수 = 受/守/手/數 …), 37 are internal word
  formation (`affix`, `compound`), 29 other.
- Arabic (50.4 % → 17.7 %): almost every Arabic section is a root box (`ar-root`, `ar-rootbox`,
  "from the root ك ت ب") plus a pattern template (`ar-verbal noun`, `ar-active participle`,
  `adverbial accusative`); that is morphology inside Arabic, not an origin in another language, and
  the design deliberately does not display roots as etymology (spec §5f). Only borrowings and the
  `etymon` trees reaching Proto-Semitic (59) yield an origin string.
- Japanese (27.4 % → 14.5 %): of 318 such lemmas, 59 say "From Old Japanese" with no form
  (`{{inh|ja|ojp|-}}`), 94 describe compounds/ren'yōkei derivations inside Japanese (`ja-compound`,
  `ja-ryk`, `ja-com`, `compound`), 66 carry only `ja-r` cross-references, 99 other (kana-only
  spellings are soft redirects, `飲む` → `ja-see`). Kango whose page carries no template still get edges
  through the Chinese descendants lists (飛行, 危険) but no origin string.
- Chinese (24.5 % → 10.3 %): Chinese etymology sections are glyph-origin descriptions (`Han compound`,
  `liushu`) or Proto-Sino-Tibetan reconstructions for single characters; compounds rarely have an
  etymology at all. The Sinographic stratum shows up as **edges** instead (242 borrowing endpoints
  to ja/ko), which is the claim the app needs.
- European languages (≈ 50 % → 38–46 %): the residual are sections consisting of `affix`/`suffix`/
  `compound` templates (internal word formation) and `cog`-only sections.

Everything that can be stated as "<Language> <lexeme>" from a template is stated; the rest is
deliberately left blank (07 §8).

### 1.8 Sinographic layer

`build/enrich/sinographic.json` lists every traditional Han string attested for corpus lemmas in two
or more of ja/ko/zh (Chinese headword or simplified form, Korean hanja form or `ko-etym-sino`,
Japanese headword or kyūjitai form). On the Track-A lexicon: **37 full ja/ko/zh triples** (e.g. 單位 危險 參加 問題 安全 完成 必要
惡魔 成功 政治 時代 治療 準備 火山 特別 祕密 魔女 魔法) and 174 two-language pairs; on the
17-language lexicon 58 triples and 311 pairs — up from
"a handful" in the EtymDB build (07 §5). The ceiling is the corpus: the P&C Korean folder covers
14 of 23 episodes, so most Japanese/Chinese compounds have no Korean counterpart in the lexicon.
Each record lists the corpus lemmas per language and `via` (which attestation supplied the form).

---

## 2. Pronunciations (`enrich_pronunciations.py`)

- **IPA**: first `sounds[].ipa` of the matched entries, preferring records without dialect/register
  tags and broad (`/…/`) over narrow transcriptions.
- **Audio**: `sounds[].audio` is the Commons file name; `mp3_url` (Commons transcode) is preferred
  over `ogg_url` as `audioUrl`. Up to two candidate files per lemma (standard before dialect-tagged).
- **Licence per file**: kaikki does not carry it, so every candidate is looked up once through
  `https://commons.wikimedia.org/w/api.php?action=query&prop=imageinfo&iiprop=extmetadata` — 50
  titles per request, `User-Agent: Clavis-pipeline/0.1 (https://github.com/mbaliga/Clavis)`,
  one request every 6 s (measured 2026-09-01: two requests 2 s apart already get HTTP 429 from this
  egress; the client backs off 20 → 180 s and stretches the interval on 429). Answers are cached in
  `build/enrich/commons_cache.json` (`LicenseShortName`, `License`, `LicenseUrl`, `Artist`,
  `Credit`, `AttributionRequired`, `UsageTerms`, `url`; `missing` for deleted files).
- **Rule** (`enrich_common.permissive_licence`): keep `CC0`, `CC BY x.y`, `CC BY-SA x.y`,
  public domain; exclude anything with `NC` or `ND`, `GFDL`-only, `GPL`, empty/unknown, files the API
  does not know, and — in `--offline` mode — files not in the cache ("unverified"). Excluded files
  are counted per reason in `build/enrich/pronunciation_coverage.json`.
- **Record**: `{lemma, ipa?, audioUrl?, audioLicence?, audioBy?}` (+ `audioFile`, the Commons title,
  for the colophon). `audioBy` is `Artist` with HTML stripped, else `Credit`. The audio itself is
  never downloaded; the app fetches on explicit user action (D-13).

Run on the Track-A lexicon (`build/enrich/pronunciation_coverage.trackA.json`): 5,243 distinct Commons files looked up in 117 requests (110 in the first pass, 1,721 s including 429 back-offs; 7 in the two cached re-runs), then served from the cache:

<!-- PRON_TRACKA -->
| Lang | Lemmas | Wiktionary entry found | With IPA | % | With permissive Commons audio | % | Audio present but excluded |
|---|---:|---:|---:|---:|---:|---:|---:|
| en | 1832 | 1505 | 1310 | 71.5 % | 1246 | 68.0 % | 0 |
| es | 1828 | 1062 | 1043 | 57.1 % | 3 | 0.2 % | 0 |
| fr | 1863 | 1075 | 1049 | 56.3 % | 990 | 53.1 % | 0 |
| it | 1845 | 988 | 958 | 51.9 % | 82 | 4.4 % | 0 |
| nl | 1857 | 1002 | 874 | 47.1 % | 986 | 53.1 % | 0 |
| ja | 2088 | 1068 | 961 | 46.0 % | 1 | 0.0 % | 0 |
| ko | 867 | 729 | 709 | 81.8 % | 4 | 0.5 % | 0 |
| zh | 1424 | 1325 | 265 | 18.6 % | 713 | 50.1 % | 0 |
| ar | 605 | 487 | 408 | 67.4 % | 121 | 20.0 % | 0 |
| de | 1923 | 1141 | 1006 | 52.3 % | 1102 | 57.3 % | 0 |

Commons API: 6 requests in this run, 5183 answers served from the cache, 5243 files in `commons_cache.json`; excluded by reason: none. Runtime {'kaikki_match': 2.6, 'commons': 332.5, 'total': 340.1}.

<!-- /PRON_TRACKA -->

Nearly every Commons pronunciation file is permissively licensed (Lingua Libre CC BY-SA 4.0, Shtooka CC BY 2.0 fr, CC BY-SA 3.0, CC0 …); the licence check exists for the exceptions (a GPL-only file and deleted files) and for the record.

---

## 3. Sentences (`enrich_sentences.py`)

Per enabled language with a `tatoebaCode` (the bridge language `en` is skipped unless
`--include-en`):

1. `sentences.csv` is streamed once; the language's lines pass a cheap prefilter (≤ 160 chars,
   whitespace-token count in range for space-separated scripts, 3–60 chars for ja/zh); every English
   sentence is kept in memory for step 3.
2. Tokenisation mirrors `annotate.py` through `enrich_common.Tokenizer`: `simplemma` for
   Latin/Cyrillic/Devanagari scripts (lemma memoised per surface — 1.4 M candidate sentences
   tokenise in ~35 s), `fugashi`/UniDic `orthBase` for Japanese, `wordfreq.tokenize(…, 'ko')`
   (mecab-ko) for Korean, `wordfreq.tokenize(…, 'zh')` (jieba) on the OpenCC-simplified text for
   Chinese (the text itself is kept as written), the Arabic regex + `ar_norm` for Arabic. A sentence
   is a candidate when it has 3–14 tokens and ≥ 80 % of them map to a lexicon lemma (lemma, or the
   lowercased surface when the lexicon keeps surfaces).
3. `links.csv`: the lowest-numbered directly linked English sentence becomes `en`.
4. `sentences_with_audio.csv`: only rows whose licence is CC BY / CC BY-SA / CC0 are referenced
   (`https://audio.tatoeba.org/sentences/<lang3>/<id>.mp3`, verified with a HEAD → `200 audio/mpeg`);
   empty, `\N` and NC rows are excluded (74,774 excluded vs 3,469 permissive rows among the
   candidates).
5. Rank `(has permissive audio, has English, coverage, distinct lexicon lemmas, id)`; a lazy greedy
   set cover keeps at least one sentence per lexicon lemma that occurs in any candidate (tie-break by
   rank), then the remaining slots up to the cap of 3,000 are filled in rank order.
6. `ATTRIBUTION-tatoeba.json`: per kept sentence the author (`sentences_detailed.csv`), the sentence
   URL, the English sentence's id/author, and the audio contributor/licence/attribution URL.

Run of 2026-09-02 on the Track-A lexicon (Tatoeba exports of 2026-08-29; 13.56 M sentences,
2.04 M English), `build/enrich/sentence_coverage.trackA.json`:

<!-- SENT_TRACKA -->
| Lang | Tatoeba | Candidates | Kept | With permissive audio | With English | Lexicon lemmas covered | Lemmas in any candidate | % of lexicon |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| es | spa | 169,769 | 3,000 | 3 | 2,889 | 1,071 | 1,071 | 58.6 % |
| fr | fra | 278,373 | 3,000 | 578 | 2,891 | 1,101 | 1,101 | 59.1 % |
| it | ita | 413,928 | 3,000 | 581 | 2,906 | 1,047 | 1,047 | 56.7 % |
| nl | nld | 88,440 | 3,000 | 0 | 2,935 | 965 | 965 | 52.0 % |
| ja | jpn | 81,826 | 3,000 | 3 | 2,966 | 1,026 | 1,026 | 49.1 % |
| ko | kor | 4,240 | 3,000 | 0 | 2,873 | 561 | 561 | 64.7 % |
| zh | cmn | 19,596 | 3,000 | 0 | 2,912 | 1,131 | 1,131 | 79.4 % |
| ar | ara | 610 | 610 | 0 | 432 | 229 | 229 | 37.9 % |
| de | deu | 318,256 | 3,000 | 2,304 | 2,748 | 1,135 | 1,135 | 59.0 % |

Audio rows among the candidates: {'excluded': 74774, 'permissive': 3469}. Runtime {'sentences_csv': 12.4, 'tokenise': 35.7, 'links_csv': 9.7, 'audio_csv': 0.7, 'detailed_csv': 9.2, 'total': 79.1}.

<!-- /SENT_TRACKA -->

The set cover reaches every lemma that occurs in any candidate in all nine languages. Permissive
audio follows D-7's measurement exactly: German (CC BY 4.0, 5.3 k clips), French (CC BY-SA 4.0),
Italian (CC BY 4.0); Spanish 11 clips and Japanese 27 clips in the whole export, none for Dutch,
Korean, Chinese, Arabic. Runtime 78 s (sentences.csv 13 s, tokenising 34 s, links.csv 10 s,
sentences_detailed.csv 9 s).

---

## 4. Licences carried in the manifests

| Pack | `licence` | Attribution entries |
|---|---|---|
| etymology | CC BY-SA 4.0 | Wiktionary contributors via wiktextract/kaikki.org (CC BY-SA 4.0); EtymDB 2.1, Fourrier & Sagot 2020 (CC BY-SA 4.0) |
| pronunciations | CC BY-SA 4.0 (IPA); audio per file | Wiktionary contributors; Wikimedia Commons contributors — the per-file licence and author are inside each record |
| sentences | CC BY 2.0 FR (text); audio per clip | Tatoeba contributors; Tatoeba audio contributors — per clip in the record and in `ATTRIBUTION-tatoeba.json` |

Each `manifest-fragment.json` is a `PackManifest` (`id`, `kind`, `version`, `title`, `languages`,
`licence`, `source`, `generatedAt`, `attribution[]`, `files{name: bytes}`, `stats{lang: {…}}`).

The pronunciations row's "Wikimedia Commons contributors" attribution already covers the §6.4 second
source unchanged: Lingua Libre recordings and the per-language pronunciation categories are
Wikimedia Commons files like any other, carrying their own `Artist`/`Credit` extmetadata (Lingua
Libre's is typically "Speaker: `<name>` / Recorder: `<name>`", surfaced as `audioBy`), so no new
attribution-table entry was needed — the existing per-file `audioBy`/`audioLicence` mechanism is
exactly what names Lingua Libre's speakers.

---

## 5. Integration notes (for `run.py` / `languages.json` / the app)

- **Registry fields used** (already present in the current `languages.json`): `kaikkiName`
  (kaikki dump name, e.g. `Chinese`), `etymdbCode` (string; the loader also accepts
  `etymdbCodes`/`etymCodes` lists and always adds the historical stages from its built-in table —
  `zh: cmn, ltc, och, lzh`; `la: la-vul, la-lat, la-med, la-new, la-ecc, la-ren`), `tatoebaCode`
  (ISO 639-3), `tokenizer` (`simplemma` | `fugashi` | `mecab-ko` | `jieba` | `arabic` | `whitespace`),
  `lemmatizer` (`simplemma:<code>` gives the simplemma language when it differs from `iso`),
  `enabled`. Languages absent from the registry are skipped with a warning; without a registry file
  a built-in map covers en es fr it de nl pt la ru pl uk tr hi ja ko zh ar.
- **How `run.py` should invoke the stages** (after `annotate.py`, before `pack_assets.py`):
  ```python
  import enrich_etymology, enrich_pronunciations, enrich_sentences
  enrich_etymology.main(['--lexicon', str(BUILD/'lexicon.json'), '--out-dir', str(BUILD)])
  enrich_pronunciations.main(['--lexicon', str(BUILD/'lexicon.json'), '--out-dir', str(BUILD)] + (['--offline'] if not args.network else []))
  enrich_sentences.main(['--lexicon', str(BUILD/'lexicon.json'), '--out-dir', str(BUILD)])
  ```
  Each `main()` returns its coverage dict. Data directories default to the scratchpad layout and are
  overridable with `--kaikki-dir`, `--etymdb-dir`, `--desc-dir`, `--tatoeba-dir`. Only
  `enrich_pronunciations.py` touches the network; `--offline` makes the whole enrichment run
  reproducible from caches (D-13 applies to the app, but CI should still run offline).
- **The §6.4 second audio source** is two more off-by-default flags on the same stage,
  `--lingualibre` and `--commons-categories`; `run.py --enrich --network --lingualibre
  --commons-categories` passes them through (each becomes part of the stage's cache-stamp signature,
  so turning them on/off busts the stamp and reruns the stage). They only ever add candidates for
  the six languages in `enrich_pronunciations.LINGUALIBRE_ISO3`/`COMMONS_CATEGORIES` (es/it/pl/ja/ko/
  la) — extending them to another language is a one-line addition to those dicts (plus, for the
  category scan, confirming `Category:<Language> pronunciation` is small enough to page).
- **Outputs to pack**: `build/packs/etymology/etym.json.gz` (`{lang: {lemma: EtymEntry}}`), split
  per language by `pack_assets.py` if it keeps the `etym/<iso>.json.gz` layout;
  `build/packs/pronunciations/<lang>.json.gz`; `build/packs/sentences/<lang>.json.gz` +
  `ATTRIBUTION-tatoeba.json`; the three `manifest-fragment.json` files are complete `PackManifest`
  objects to be merged into the pack manifest / colophon.
- **Kotlin contract note**: `EtymEntry.edges` is `List<EtymEdge>` where `EtymEdge` is a plain
  `@Serializable data class` — kotlinx's default form is `{"lang":…,"lemma":…,"type":…}`, while the
  pack (per the brief and the v0.10 web pack) writes `[lang, lemma, type]` triples. Either give
  `EtymEdge` a custom array serializer or run `enrich_etymology.py --edge-objects`, which emits the
  object form. `Sentence` and `Pronunciation` records already match their data classes field for
  field (`audioFile` is an extra key the loader must ignore).
- **Lexicon hygiene feeds straight through**: names, credit tokens and punctuation-bearing Arabic
  tokens in the lexicon acquire (true) etymologies such as `es adam → Latin Adam`; they disappear as
  soon as the D-7 hygiene filter removes them from `lexicon.json`.
- **Coverage on the language screen** (D-6): use `manifest-fragment.json → stats[lang]`
  (`origins`, `withEdges`, `edgeEndpoints`; `ipa`, `audio`; `sentences`, `withAudio`,
  `lemmasCovered`).

---

## 6. Second run: the 17-language lexicon of the new corpus

`build/packs/lexicon/lexicon.json.gz` (core agent, framagit corpus, 1,571 scenes, hygiene-filtered,
17 languages) was enriched with the same code; these are the numbers now in `build/packs/*` and
`build/enrich/*_coverage.json` (the Track-A run is kept as `*.trackA.json` and
`build/enrich/trackA-packs/`). Polish, Ukrainian, Turkish and Hindi have no kaikki extract on this
machine, so they get EtymDB-only etymology, no pronunciations, and sentences only. The
pronunciation run was `--offline` (Commons cache only): Portuguese, Latin and Russian audio files
that were not already cached count as "unverified" and are excluded until a networked run adds
them (next step: `python enrich_pronunciations.py --lexicon build/packs/lexicon/lexicon.json.gz`,
≈ 1,500 new files ≈ 30 throttled requests).

### 6.1 Etymology

<!-- ETYM_NEW -->
| Lang | Lemmas | Wiktionary entry found | Has etymology section | With origin | % | With ≥ 1 edge | % | In graph | cog / bor / der endpoints | EtymDB-only (07 §4) | Top origin languages |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---|
| es | 1627 | 1519 | 1420 (87.3 %) | 1218 | **74.9 %** | 1062 | 65.3 % | 76.7 % | 3675 / 122 / 282 | 36.8 % | Latin 597, Old Spanish 345, Late Latin 56, Vulgar Latin 53 |
| fr | 1767 | 1585 | 1415 (80.1 %) | 1186 | **67.1 %** | 1036 | 58.6 % | 70.6 % | 3308 / 319 / 334 | 35.0 % | Middle French 422, Old French 359, Latin 270, Late Latin 29 |
| it | 1598 | 1443 | 1314 (82.2 %) | 1086 | **68.0 %** | 938 | 58.7 % | 70.0 % | 3502 / 141 / 298 | 31.5 % | Latin 778, Late Latin 103, Vulgar Latin 73, French 27 |
| de | 1770 | 1577 | 1389 (78.5 %) | 1015 | **57.3 %** | 663 | 37.5 % | 59.7 % | 1606 / 126 / 158 | 27.4 % | Middle High German 760, Latin 76, English 39, French 37 |
| nl | 1700 | 1502 | 1415 (83.2 %) | 1158 | **68.1 %** | 774 | 45.5 % | 70.4 % | 1849 / 145 / 122 | 33.1 % | Middle Dutch 910, French 67, Latin 44, Middle French 34 |
| ja | 2062 | 1701 | 941 (45.6 %) | 462 | **22.4 %** | 356 | 17.3 % | 27.4 % | 377 / 324 / 7 | 2.9 % | Middle Chinese 174, English 131, Proto-Japonic 84, Old Japanese 39 |
| ko | 1115 | 923 | 863 (77.4 %) | 416 | **37.3 %** | 206 | 18.5 % | 40.0 % | 233 / 205 / 8 | 2.8 % | Chinese 268, Middle Korean 41, English 38, Old Korean 29 |
| zh | 2439 | 2243 | 647 (26.5 %) | 218 | **8.9 %** | 300 | 12.3 % | 17.4 % | 44 / 387 / 11 | 0.6 % | Proto-Sino-Tibetan 103, Middle Chinese 57, English 20, Japanese 17 |
| ar | 583 | 486 | 379 (65.0 %) | 106 | **18.2 %** | 38 | 6.5 % | 21.1 % | 33 / 11 / 20 | 4.5 % | Proto-Semitic 59, Aramaic 12, Proto-West Semitic 7, Hebrew 7 |
| en | 1603 | 1570 | 1541 (96.1 %) | 1397 | **87.1 %** | 1062 | 66.3 % | 88.6 % | 3095 / 437 / 227 | 51.4 % | Middle English 1061, Latin 108, French 48, Middle French 48 |
| pt | 1557 | 1395 | 1282 (82.3 %) | 1120 | **71.9 %** | 968 | 62.2 % | 74.0 % | 3463 / 143 / 258 | — | Old Galician-Portuguese 562, Latin 365, French 47, English 27 |
| la | 642 | 638 | 612 (95.3 %) | 374 | **58.3 %** | 415 | 64.6 % | 75.2 % | 807 / 255 / 1025 | — | Proto-Italic 206, Proto-Indo-European 83, Ancient Greek 60, Old Latin 16 |
| ru | 1757 | 1518 | 1321 (75.2 %) | 700 | **39.8 %** | 520 | 29.6 % | 46.6 % | 1012 / 88 / 20 | — | Proto-Slavic 239, Old East Slavic 226, Old Church Slavonic 62, French 36 |
| pl | 1863 | 1682 | 1632 (87.6 %) | 921 | **49.4 %** | 442 | 23.7 % | 53.5 % | 1065 / 111 / 51 | — | Old Polish 562, Proto-Slavic 141, Latin 78, French 37 |
| uk | 1431 | 1145 | 1024 (71.6 %) | 515 | **36.0 %** | 516 | 36.1 % | 45.8 % | 903 / 72 / 14 | — | Proto-Slavic 209, Old East Slavic 76, Borrowed from Old Ruthenian 58, Polish 52 |
| tr | 437 | 381 | 340 (77.8 %) | 300 | **68.6 %** | 44 | 10.1 % | 68.6 % | 79 / 7 / 25 | — | Ottoman Turkish 277, French 8, Arabic 5, Proto-Turkic 3 |
| hi | 45 | 41 | 39 (86.7 %) | 38 | **84.4 %** | 6 | 13.3 % | 84.4 % | 8 / 1 / 0 | — | Sanskrit 14, Prakrit 7, Sauraseni Prakrit 6, Old Hindi 6 |

Totals: **12,230 origins** (9,128 inherited, 3,102 borrowed; 11,619 from Wiktionary, 611 from EtymDB where Wiktionary had none) and **30,813 edge endpoints** (25,059 cognate, 2,894 borrowing, 2,860 derivation; 24,700 from Wiktionary, 6,113 EtymDB-only) over 13,290 lemmas in the graph. Sinographic ja/ko/zh triples: 58 (+311 pairs). Top origin languages overall: Latin 2,369, Middle English 1,071, Middle Dutch 916, Middle High German 769, Proto-Slavic 590, Old Galician-Portuguese 563, Old Polish 563, Middle French 511, Old French 431, English 393, French 363, Old Spanish 345. Runtime {'kaikki_match': 66.7, 'wiktionary_graph': 3.6, 'etymdb_graph': 21.5, 'total': 92.9}.

<!-- /ETYM_NEW -->

### 6.2 Pronunciations

<!-- PRON_NEW -->
| Lang | Lemmas | Wiktionary entry found | With IPA | % | With permissive Commons audio | % | Audio present but excluded |
|---|---:|---:|---:|---:|---:|---:|---:|
| es | 1627 | 1519 | 1500 | 92.2 % | 3 | 0.2 % | 0 |
| fr | 1767 | 1585 | 1564 | 88.5 % | 879 | 49.7 % | 644 |
| it | 1598 | 1443 | 1411 | 88.3 % | 81 | 5.1 % | 63 |
| de | 1770 | 1577 | 1418 | 80.1 % | 929 | 52.5 % | 619 |
| nl | 1700 | 1502 | 1302 | 76.6 % | 871 | 51.2 % | 612 |
| ja | 2062 | 1701 | 1556 | 75.5 % | 1 | 0.0 % | 0 |
| ko | 1115 | 923 | 899 | 80.6 % | 4 | 0.4 % | 1 |
| zh | 2439 | 2243 | 447 | 18.3 % | 728 | 29.8 % | 372 |
| ar | 583 | 486 | 407 | 69.8 % | 121 | 20.8 % | 0 |
| en | 1603 | 1570 | 1529 | 95.4 % | 964 | 60.1 % | 557 |
| pt | 1557 | 1395 | 1378 | 88.5 % | 0 | 0.0 % | 690 |
| la | 642 | 638 | 584 | 91.0 % | 0 | 0.0 % | 0 |
| ru | 1757 | 1518 | 1505 | 85.7 % | 0 | 0.0 % | 1330 |
| pl | 1863 | 1682 | 1682 | 90.3 % | 0 | 0.0 % | 0 |
| uk | 1431 | 1145 | 1142 | 79.8 % | 0 | 0.0 % | 1032 |
| tr | 437 | 381 | 316 | 72.3 % | 0 | 0.0 % | 206 |
| hi | 45 | 41 | 39 | 86.7 % | 0 | 0.0 % | 14 |

Commons API: 0 requests in this run, 4583 answers served from the cache, 5,243 files in `commons_cache.json`; 7,706 uncached files treated as unverified in this offline run; excluded by reason: {'unverified': 7706}. Runtime {'kaikki_match': 5.9, 'commons': 0.1, 'total': 21.6}.

<!-- /PRON_NEW -->

### 6.3 Sentences

<!-- SENT_NEW -->
| Lang | Tatoeba | Candidates | Kept | With permissive audio | With English | Lexicon lemmas covered | Lemmas in any candidate | % of lexicon |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| es | spa | 201,882 | 3,000 | 3 | 2,871 | 1,478 | 1,478 | 90.8 % |
| fr | fra | 352,785 | 3,000 | 643 | 2,850 | 1,607 | 1,607 | 90.9 % |
| it | ita | 446,155 | 3,000 | 649 | 2,842 | 1,437 | 1,437 | 89.9 % |
| de | deu | 370,338 | 3,000 | 2,319 | 2,735 | 1,569 | 1,569 | 88.6 % |
| nl | nld | 99,832 | 3,000 | 0 | 2,896 | 1,401 | 1,401 | 82.4 % |
| ja | jpn | 107,837 | 3,000 | 3 | 2,958 | 1,657 | 1,657 | 80.4 % |
| ko | kor | 5,116 | 3,000 | 0 | 2,924 | 714 | 714 | 64.0 % |
| zh | cmn | 30,028 | 3,000 | 0 | 2,851 | 1,959 | 1,959 | 80.3 % |
| ar | ara | 610 | 610 | 0 | 432 | 229 | 229 | 39.3 % |
| pt | por | 217,053 | 3,000 | 3 | 2,877 | 1,354 | 1,354 | 87.0 % |
| la | lat | 6,512 | 3,000 | 0 | 2,960 | 481 | 481 | 74.9 % |
| ru | rus | 510,280 | 3,000 | 1,998 | 2,587 | 1,494 | 1,494 | 85.0 % |
| pl | pol | 47,840 | 3,000 | 28 | 2,819 | 1,440 | 1,440 | 77.3 % |
| uk | ukr | 69,527 | 3,000 | 1 | 2,984 | 1,065 | 1,065 | 74.4 % |
| tr | tur | 118,512 | 3,000 | 0 | 2,983 | 419 | 419 | 95.9 % |
| hi | hin | 0 | 0 | 0 | 0 | 0 | 0 | 0.0 % |

Audio rows among the candidates: {'excluded': 101310, 'permissive': 5712}. Runtime {'sentences_csv': 54.9, 'tokenise': 85.5, 'links_csv': 25.8, 'audio_csv': 2.8, 'detailed_csv': 71.7, 'total': 270.5}.

<!-- /SENT_NEW -->

---

## 6.4 A second pronunciation-audio source: Lingua Libre + Commons categories

kaikki's `{{audio}}` coverage is a property of English Wiktionary's editing conventions, not of what
audio actually exists: the compaction stats (`*.stats.json`) show `with_audio` counts of 42/809,603
entries for Spanish, 6/… for Japanese, 25/… for Korean, 6/… for Polish and 0 for Latin, against
92,620 for French and 301,486 for German — English Wiktionary editors for those languages simply
don't embed `{{audio}}` templates, even though Wikimedia Commons itself holds tens of thousands of
recordings for Spanish and Italian (from **Lingua Libre**, a Wikimedia project that batch-records
single-word pronunciations, all CC BY-SA 4.0 by policy) and smaller but real numbers for Japanese,
Korean and Latin. `enrich_pronunciations.py` now has two opt-in flags, `--lingualibre` and
`--commons-categories`, that add exactly those files as extra candidates for `es`, `it`, `pl`, `ja`,
`ko`, `la` — the six languages kaikki barely covers — without touching the record schema, the licence
check, the cache or the output paths, and without altering anything for the other eleven languages.

**Method.**
1. **Lingua Libre SPARQL** (`https://lingualibre.org/bigdata/namespace/wdq/sparql`). The six
   language items are discovered once (not hard-coded) by querying items that are `prop:P2 entity:Q4`
   ("instance of: language") for the endpoint's own ISO 639-3 property — found by listing the
   endpoint's properties and inspecting them: `prop:P13` is "ISO 639-3 code" — with an English
   `rdfs:label` match as fallback; the six QIDs (`es→Q386, it→Q385, pl→Q298, ja→Q389, ko→Q207,
   la→Q384`) are cached in `build/enrich/lingualibre_languages.json`. Per language, the lexicon's
   lemmas are sent in `VALUES ?transcription { "casa" "perro" … }` chunks of 300 (raw
   `application/sparql-query` POST, `Accept: application/sparql-results+json`, the same descriptive
   `User-Agent` sent to Commons), asking for `?record prop:P2 entity:Q2 ; prop:P4 entity:<qid> ;
   prop:P3 ?file ; prop:P7 ?transcription`. A `VALUES` match is exact, so the returned
   `?transcription` *is* one of our own lemma strings — no fuzzy matching needed on this path. The
   Commons file name is recovered from the `Special:FilePath` URL Lingua Libre returns
   (URL-decoded). One candidate file per lemma is kept (`NEW_SOURCE_CAP = 1`) to keep the licence
   pass short, appended after any kaikki candidates.
2. **Commons category scan** (fallback/supplement), `action=query&list=categorymembers` with
   `cmcontinue` paging, over the five small generic categories only (`Category:Spanish/Italian/
   Japanese/Korean/Latin pronunciation`, 672/1,209/248/39/720 files respectively — one to three
   pages each). `Category:Polish pronunciation` (≈24,141 files) is deliberately skipped, per the
   brief: Lingua Libre already covers Polish, and paging a category two orders of magnitude larger
   for the same yield is not worth it. Filenames are parsed with one rule that covers every
   convention seen (`Xx-<word>.ext`, `Xx-<region>-<word>.ext`, `(iso)-<speaker>-<word>.ext`,
   `LL-Q<n> (iso)-<speaker>-<word>.ext`): the transcribed word is always the segment after the last
   hyphen, whatever precedes it. The result is normalised NFC + lowercase and matched against a
   `{word: lemma}` index built from both the lexicon lemma **and** its corpus surface form.
3. **Merge and licence.** New candidates are appended after kaikki's for the same lemma, deduplicated
   by filename, and go through the *same* `Commons` extmetadata cache and licence filter as every
   other candidate (`prop=imageinfo&iiprop=url|extmetadata`) — nothing in the CC0/CC BY/CC BY-SA/
   public-domain rule was weakened. `audioUrl` is taken from imageinfo's `url` (the original file —
   `.wav` for most Lingua Libre recordings; the app already plays WAV/MP3/OGG) rather than a guessed
   transcode path, since Lingua Libre files have no kaikki-style `mp3_url`/`ogg_url`. No existing
   audio for fr/de/nl/en/ru/zh/uk/pt/tr/ar/hi was reordered or removed — those languages are outside
   `LINGUALIBRE_ISO3`/`COMMONS_CATEGORIES` and are never queried by the new code paths.

**Endpoint behaviour observed (2026-09-16, this egress).** The Lingua Libre SPARQL endpoint answered
trivial and 300-value queries in well under a second in isolation, but under sustained use from this
session it degraded in a distinctive pattern: **the first request of a fresh process usually
succeeds; a process that keeps querying (a second language, or a later chunk of the same language)
increasingly hits `502` with the client's exponential back-off (5→10→20→40→80 s), and after five
failed attempts the client marks the endpoint unavailable for the rest of that process** (by design,
so a dead endpoint doesn't stall the run — this is the "fall back to (2) only" contingency named in
the brief, scoped per-process rather than globally). Re-running as a fresh process reliably worked
again for a while, then a further burst of chunk requests eventually made even fresh processes time
out outright (60 s, no response at all) rather than 502 — read as the endpoint (a small
community-run Blazegraph instance, not WMF infrastructure) becoming genuinely overloaded rather than
this egress being deliberately blocked, since isolated single queries kept succeeding throughout.
Practical effect: **es, it and ko got their full lexicon queried** (6, 6 and 4 chunks respectively,
after retrying failed chunks as separate fresh processes); **pl and ja are partial** (pl: 3 of 7
chunks, ~1,163/1,863 lemmas queried; ja: 5 of 7 chunks, ~1,462/2,062 lemmas queried — the remaining
chunks timed out on every retry and were not forced further); **la's 3 chunks all completed** but
Lingua Libre simply holds almost no Latin recordings (1 hit). The Commons category scan, being a
much smaller and gentler request pattern (1–3 pages per language, 6 s apart, on the already-proven
Commons API), completed for all five configured languages without incident. A future run should
expect to recover the missing pl/ja lemmas once the endpoint has had time to recover, by re-running
`enrich_pronunciations.py --lingualibre` alone — already-permissive lemmas are left untouched, so a
partial re-run only adds coverage.

**Method limits.** (a) Lingua Libre's `transcription` field is what the recording speaker typed, not
a normalised lemma; the SPARQL path exploits `VALUES` exact-match so it only finds lemma spellings
that are recorded verbatim (no inflection matching, unlike the kaikki matcher's Arabic/Korean/Chinese
candidate generation) — a lemma recorded only under an inflected or accented variant is missed.
(b) The category-scan filename parser trusts Commons category membership for the `LL-Q…(iso)` and
`(iso)-…` conventions rather than re-checking the embedded ISO code, since both are rare inside the
five categories used and mis-scoped files would fail to match a lemma anyway. (c) Lingua Libre
records many speakers per word; only one file is kept (the first permissively-licensed one returned
by the endpoint, in whatever order SPARQL happened to return rows), so the same word from two
requests may cite different speakers — this is fine for the app's use (hearing *a* native
pronunciation), not a determinism guarantee across runs. (d) Most new files are `.wav` originals
(no Commons transcode exists for Lingua Libre uploads), larger than the `.ogg`/`.mp3` files kaikki
usually pointed to; this is intentional per the brief rather than a regression.

### Before / after (2026-09-16 run, `build/packs/lexicon/lexicon.json.gz`)

<!-- PRON_LL -->
| Lang | Lemmas | Audio before | Audio after | kaikki | Lingua Libre | Category | Excluded (licence) |
|---|---:|---:|---:|---:|---:|---:|---:|
| es | 1627 | 3 | **560** | 3 | 492 | 65 | 0 |
| it | 1598 | 144 | **767** | 144 | 414 | 209 | 0 |
| pl | 1863 | 0 | **413** | 0 | 413 | — (category skipped) | 0 |
| ja | 2062 | 1 | **63** | 1 | 60 | 2 | 0 |
| ko | 1115 | 5 | **102** | 5 | 95 | 2 | 0 |
| la | 642 | 0 | **143** | 0 | 1 | 142 | 0 |

Requests and runtime (cumulative across the first full run, the endpoint-outage retries as
per-language/per-chunk fresh processes, and the final merge pass): Lingua Libre SPARQL — 1 language
discovery request + roughly 30 chunk requests (300 lemmas each) across the six languages, several
repeated after 502s/timeouts as noted above; Commons category scan — 9 category pages (1–3 per
language) at the existing 6 s/request pace; Commons licence pass — 39 batched `imageinfo` requests
of ≤50 titles (18 in the main run + 21 in the merge pass), growing `commons_cache.json` from 11,373
to 13,317 files; nothing was excluded by licence in this batch (all new files were Lingua Libre
CC BY-SA 4.0 or the categories' usual CC0/CC BY/CC BY-SA/public-domain mix). Wall time: ≈ 8 min for
the first full `--lingualibre --commons-categories` run (`build/enrich/pronunciation_coverage.json`,
`timings_seconds.total`), plus the retries and merge pass described above (≈ 35 min more, dominated
by 502/timeout back-offs on the flaky endpoint, not by useful request volume).

<!-- /PRON_LL -->
