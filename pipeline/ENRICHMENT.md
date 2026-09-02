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
| `tests/test_enrich_*.py` | 63 pytest tests on synthetic fixtures (20-line kaikki extract, mini lexicon, mini EtymDB, mini Tatoeba CSVs) |
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

# B. pronunciations (network: Commons API; ≈ 45–60 min for ~6,000 files at the rate Commons
#    tolerates from this egress, seconds afterwards thanks to build/enrich/commons_cache.json;
#    --offline never calls the API and treats uncached files as unverified = excluded)
python enrich_pronunciations.py --lexicon build/lexicon.json --kaikki-dir $SCRATCH/data/kaikki --out-dir build

# C. sentences (≈ 80 s: one pass over sentences.csv 756 MB, links.csv 455 MB, sentences_detailed.csv 1.4 GB)
python enrich_sentences.py --lexicon build/lexicon.json --tatoeba-dir $SCRATCH/data/tatoeba --out-dir build

python -m pytest tests/test_enrich_*.py -q     # 63 passed
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
<!-- /ETYM_TRACKA -->

**Why the origin-string share is lower than "has an etymology section".** The gap is by
construction, not a matching loss (entry found ≈ the D-5 "headword found" column):

- Korean (77.5 % → 38.2 %): of the 342 matched lemmas that have an etymology section but no
  origin, 190 are native words whose section says only "of native Korean origin" or cites a Middle
  Korean attestation without a form (`ko-etym-native`, `ko-attest`, `ko-l`/`okm-l`), 86 are
  Sino-Korean homographs excluded by the guard (109 ambiguous lemmas in total, 58 of them
  single-syllable MeCab tokens: 세 = 世/勢/歲, 시 = 市/時/詩, 수 = 受/守/手/數 …), 37 are internal word
  formation (`affix`, `compound`), 29 other.
- Arabic (50.4 % → 14.2 %): almost every Arabic section is a root box (`ar-root`, `ar-rootbox`,
  "from the root ك ت ب") plus a pattern template (`ar-verbal noun`, `ar-active participle`,
  `adverbial accusative`); that is morphology inside Arabic, not an origin in another language, and
  the design deliberately does not display roots as etymology (spec §5f). Only borrowings and the
  `etymon` trees reaching Proto-Semitic (42) yield an origin string.
- Japanese (27.4 % → 13.8 %): of 284 such lemmas, 51 say "From Old Japanese" with no form
  (`{{inh|ja|ojp|-}}`), 81 describe compounds/ren'yōkei derivations inside Japanese (`ja-compound`,
  `ja-ryk`, `ja-com`, `compound`), 58 carry only `ja-r` cross-references, and kana-only spellings
  are soft redirects (`飲む` → `ja-see`). Kango whose page carries no template still get edges
  through the Chinese descendants lists (飛行, 危険) but no origin string.
- Chinese (24.5 % → 9.1 %): Chinese etymology sections are glyph-origin descriptions (`Han compound`,
  `liushu`) or Proto-Sino-Tibetan reconstructions for single characters; compounds rarely have an
  etymology at all. The Sinographic stratum shows up as **edges** instead (233 borrowing endpoints
  to ja/ko), which is the claim the app needs.
- European languages (≈ 50 % → 38–46 %): the residual are sections consisting of `affix`/`suffix`/
  `compound` templates (internal word formation) and `cog`-only sections.

Everything that can be stated as "<Language> <lexeme>" from a template is stated; the rest is
deliberately left blank (07 §8).

### 1.8 Sinographic layer

`build/enrich/sinographic.json` lists every traditional Han string attested for corpus lemmas in two
or more of ja/ko/zh (Chinese headword or simplified form, Korean hanja form or `ko-etym-sino`,
Japanese headword or kyūjitai form). On the Track-A lexicon: **37 full ja/ko/zh triples** (下 世界
任務 使用 別 匿名 危險 參加 唯一 問題 單位 天才 失敗 安全 完全 完成 必要 惡魔 成功 我 投票 政治 時代 月
材料 治療 準備 火山 特別 祕密 製作 讀者 貨幣 頭 魔女 魔法 點) and 170 two-language pairs — up from
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

Run on the Track-A lexicon (`build/enrich/pronunciation_coverage.trackA.json`; ~5,200 distinct Commons files looked up over ~30 min of throttled requests, then served from the cache):

<!-- PRON_TRACKA -->
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
<!-- /ETYM_NEW -->

### 6.2 Pronunciations

<!-- PRON_NEW -->
<!-- /PRON_NEW -->

### 6.3 Sentences

<!-- SENT_NEW -->
<!-- /SENT_NEW -->
