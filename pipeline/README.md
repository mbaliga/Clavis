# Clavis corpus pipeline

The parameterised successor of `reference-prototype/{extract,annotate,pseudo,pack}.py`. It turns the
Pepper&Carrot translation repository into the content packs the KMP app ships (D-7) and still rebuilds
the frozen single-file web app (`clavis.html`, D-1) from the same packs. Languages are data
(`languages.json`, D-6): nothing in the scripts names a language.

```
corpus (framagit)  ─extract─▶ scenes_raw.json ─annotate─▶ scenes.json + lexicon.json ─pseudo─▶ pseudo.json
                                                    │                                          │
                                       hygiene (wordfreq + kaikki)                 pack_assets ─▶ build/packs/  ─pack_web─▶ build/clavis.html
                                                                                       ▲                          │
                                                enrich_*.py (other stage) ── etymology/ sentences/ pronunciations/   smoke.js (Playwright)
```

## Running

```bash
python -m venv venv && . venv/bin/activate && pip install -r requirements.txt
# inputs (never committed): a sparse clone of https://framagit.org/peppercarrot/webcomics with ep*/lang/,
# and the compacted kaikki extracts <Language>.jsonl.gz (fields w, f[{f,t}], ...) for lexicon hygiene
export CLAVIS_CORPUS=/path/to/webcomics CLAVIS_KAIKKI=/path/to/kaikki     # or pass --corpus / --kaikki

python run.py                                   # every enabled language, all stages -> build/packs, build/clavis.html
python run.py --languages core --episodes 1-23  # the frozen ten on the first 23 episodes
python run.py --stages pack_assets,pack_web     # re-pack after the enrichment stage wrote its packs
python run.py --enrich [--network]              # also run enrich_etymology / enrich_pronunciations / enrich_sentences
                                                # between pseudo and pack_assets (they need kaikki + EtymDB + Tatoeba
                                                # data, see ENRICHMENT.md; pronunciations stay offline unless --network)
python run.py --corpus /path/to/2017-snapshot --languages core --no-hygiene \
              --check golden/old-corpus.json    # regression: must reproduce 852 -> 649 scenes etc.
python pack_assets.py --install                 # copy build/packs into composeApp/.../composeResources/files/packs/
```

`run.py` writes one log per stage to `build/work/logs/<stage>.log`, timings to `build/work/timings.json`,
and skips a stage whose stamp (`build/work/.stamps/<stage>.json`) still matches its inputs, settings and
script hash; `--force` reruns everything. Paths: `--corpus/--build/--work/--packs/--kaikki/--registry`
or `CLAVIS_CORPUS/CLAVIS_BUILD/CLAVIS_WORK/CLAVIS_PACKS/CLAVIS_KAIKKI/CLAVIS_REGISTRY`; defaults are
`<repo>/corpus`, `pipeline/build`, `build/work`, `build/packs`, `<repo>/data/kaikki`, `pipeline/languages.json`.
At most two worker processes are used (`--workers`, default 2).

Every stage can also be run on its own with the same flags (`python annotate.py --work build/work ...`);
all of them are idempotent (atomic writes, page art re-encoded only when the source PNG is newer).

### Stages

| stage | script | reads | writes | what it does |
|---|---|---|---|---|
| extract | `extract.py` | SVGs + page PNGs, `lang/<code>/info.json` | `scenes_raw.json`, `credits.json`, `extract-stats.json` | one scene per English balloon (≥ 2 words) present under the same element id in ≥ 4 selected languages; geometry **per element** (`flowRoot` → its `flowRegion/rect`; `text` → box rebuilt from tspan baselines, font sizes, anchor with 0.98/0.46/0.5 em advances); per-language boxes normalised to the page; translator credits per language; warns on unparseable SVGs instead of silently dropping them |
| annotate | `annotate.py` (+ `hygiene.py`) | `scenes_raw.json`, kaikki | `scenes.json`, `lexicon.json`, `hygiene-report.json`, `annotate-stats.json` | registry-driven tokenisation/lemmatisation (simplemma · fugashi/UniDic · jieba · mecab-ko · Arabic normaliser), Zipf from wordfreq (or corpus-derived for `freqSource: "corpus"`), the reference junk/credits filters on the English line, readings (rōmaji, revised romanisation, pīnyīn, Arabic transliteration, IAST for Hindi), bridges (Levenshtein ≥ 0.67 + same initial for Latin/Cyrillic scripts against English and same-script siblings — bucketed by initial letter, so 10 s instead of 397 s; shared Han characters for ja↔zh), then **lexicon hygiene** |
| pseudo | `pseudo.py` | `lexicon.json` | `pseudo.json` | 10 pseudoword foils per Latin/Cyrillic-script target language (reference algorithm, 4-field lexicon fix, per-language seed) |
| pack_assets | `pack_assets.py` | the three above + `credits.json` + page art + enrichment fragments | `build/packs/` | the content packs (layout below), `manifest.json`, `ATTRIBUTION.md`; merges `etymology/ sentences/ pronunciations/manifest-fragment.json` when present; `--install` copies into the app |
| pack_web | `pack_web.py` | `build/packs/` + `reference-prototype/template.html` + `vendor-g6.min.js` | `build/clavis.html`, `build/clavis-standalone.html` | the reference single-file app, filtered to the frozen ten languages (see below) |
| validate | `validate.py` | work dir (+ packs) | `validate.json` | per-language table (scenes, pages, lemmas before/after hygiene, credible-rect %, foils, tokenizer, frequency source); `--check golden.json` fails on drift; `--write-golden` records a run |
| smoke | `web/smoke.js` | `clavis-standalone.html` | `smoke-result.json`, `boot.png` | Playwright boot: no JS errors, onboarding renders, `D.scenes` decoded, ids sequential, one image per page |

### Lexicon hygiene (D-7)

A lemma is dropped when wordfreq has never seen it (zipf of lemma and surface both 0) **and** neither
the lemma nor its first surface is a headword or inflected form in the kaikki extract for that language.
CJK languages are exempt (wordfreq reports no zeros there); languages without a kaikki file are skipped and
reported as such. Dropped lemmas leave the lexicon **and** their tokens leave the scene token lists
(the balloon text is untouched), so translator names and credit tokens can no longer be picked as cloze
targets or count as unknown words. `build/work/hygiene-report.json` lists per language the counts and the
40 most frequent removed lemmas so the rule can be audited; `--no-hygiene` turns it off (the regression run).

### The web build is frozen at ten languages

`template.html` hardcodes `LANGS`, `LANE_ORDER`, `DEFAULT_MODE` and `LOCALE` for es fr it de nl ja ko zh ar
(+ en as bridge). `pack_web.py` therefore filters whatever the packs contain down to those ten
(`--languages core`), keeps scenes that still have ≥ 4 of them, renumbers scene ids, and uses the flat page
keys (`E05P03`) of the pack as image keys. New languages appear only in the KMP app (D-1: "new features
land natively only").

## Pack layout (`build/packs/`)

```
manifest.json                 JSON array of PackManifest (shared/.../core/Packs.kt): id, kind, version, title,
                              languages, licence, source, generatedAt, attribution[], files{name: bytes}, stats{lang: {...}}
languages.json                copy of the registry
ATTRIBUTION.md                David Revoy + every translator (from info.json), wordfreq, tokenisers, and the
                              Wiktionary / EtymDB / Tatoeba / Commons sections filled from enrichment fragments
comics/scenes.json.gz         [{i, e, p, m, r, q, x, k}]  m = "E05P03"; k[lang] = [[surface, lemma|0, zipf, bridges|0, reading?], ...]
comics/pages.json             {"E05P03": [640, 904, "pages/E05P03.webp"]}
comics/pages/E05P03.webp      640 px wide, WebP quality 55, method 6
lexicon/lexicon.json.gz       {lang: {lemma: [count, zipf, surface, reading]}}
lexicon/pseudo.json           {lang: [foil, ...]}
etymology/  sentences/  pronunciations/     written by enrich_*.py (see ENRICHMENT.md); each carries a
                              manifest-fragment.json that pack_assets merges into manifest.json
```

Compact shapes are the web build's (template-spec §12) so the domain layer is one port. Two deliberate
differences from the reference `pack.py`: `m` is the flat page key rather than `epNN/gfx_….png`, and for
languages with `spaceSeparated: false` (ja, zh) the pipeline-introduced spaces between two CJK characters
are removed from `x` at pack time (the template did this at display time in `lineOf()`).

## `languages.json`

One entry per language folder that appears anywhere in the corpus (69 codes). App-facing fields match
`core/Languages.kt` (`iso`, `name`, `nativeName`, `family`, `script`, `rtl`, `defaultLane`, `siblings`,
`readingSystem`, `ttsLocale`, `colour` as a `"0xAARRGGBB"` string, `spaceSeparated`, `enabledByDefault`,
`needsScriptPrimer`); pipeline-only fields follow (`role`, `enabled`, `tokenizer`, `lemmatizer`,
`wordfreqLang`, `freqSource`, `kaikkiName`, `etymdbCode`, `tatoebaCode`, `episodes`, `notes`) — the app's
loader must ignore unknown keys. `iso` is the ISO 639-1 code where one exists, otherwise the code
framagit's `langs.json` gives (639-2/3, e.g. `jbo`, `szl`), otherwise the folder code (`go`, `gb`, `sb`);
regional/script variants get BCP-47 tags (`es-MX`, `es-419`, `ko-Kore`) so keys stay unique.
`enabled` (17: the nine defaults, English, and pt la ru pl uk tr hi) is what the pipeline processes;
`enabledByDefault` (the nine) is what a fresh profile turns on. `pcFolder` is the Pepper&Carrot folder
(`kr` → `ko`, `cn` → `zh`).

### Adding a language — worked example: Catalan (`ca`)

Catalan is in all 39 episodes, simplemma has `ca`, wordfreq has `ca`, Wiktionary has "Catalan".

1. In `languages.json` change the existing `ca` entry: `"enabled": true` (it already has
   `tokenizer: "simplemma"`, `lemmatizer: "simplemma:ca"`, `wordfreqLang: "ca"`, `freqSource: "wordfreq"`,
   `kaikkiName: "Catalan"`, `siblings: ["es", "fr"]`, `defaultLane: "REC"`, a colour and `ttsLocale: "ca-ES"`).
   Leave `enabledByDefault: false` unless it should be on for new profiles (one PROD per family — keep REC).
   For a language that is not in the file yet, copy a row of the same script and fill: `pcFolder` = the
   folder under `ep*/lang/`; `tokenizer` = `simplemma` if the code is in `simplemma`'s `SUPPORTED_LANGUAGES`
   (`python -c "from simplemma.strategies.dictionaries.dictionary_factory import SUPPORTED_LANGUAGES as S; print('ca' in S)"`),
   else `whitespace` (surface = lemma); `wordfreqLang` if `wordfreq.available_languages()` has it, else
   `null` with `freqSource: "corpus"`; `script` picks the word regex and the bridge rule.
2. Drop the compacted extract `Catalan.jsonl.gz` into the kaikki dir (hygiene is skipped, and reported as
   skipped, when it is missing).
3. `python run.py --languages all` — extract's ≥ 4-language gate only gets easier, so no existing scene
   disappears; validate prints the new row (expect ~90 % credible rects and a lemma count near es/it).
4. Foils are automatic (Latin script + wordfreq); readings only exist for ja/ko/zh/ar/hi — add a
   romaniser in `annotate.make_tokenizer` if the script needs one.
5. `python run.py --stages validate --write-golden golden/<date>.json` once the numbers look right, and
   commit `languages.json` + the golden file (never `build/`).

The app then shows the language on its language screen with the per-language coverage from
`manifest.json` → `stats` (scenes, pages, episodes, credibleRectPct, lemmas, hygieneRemoved).

## Golden files

`golden/old-corpus.json` — the 2017 snapshot with the frozen ten and hygiene off: 852 raw → 649 kept
scenes, 163 → 156 pages, per-language scene/lemma/token/bridge counts, credible-rect percentages and
10 × 5 foils, exactly the documented v0.10 numbers. `golden/framagit-2026-09.json` — the first successful
run on the framagit clone with all 17 enabled languages (generated by `--write-golden`). `validate --check`
is exact on counts and ±0.5 pt on percentages.

## Smoke test

`node web/smoke.js build/clavis-standalone.html` needs Node ≥ 22 with the `playwright` package reachable
through `NODE_PATH` and a Chromium under `PLAYWRIGHT_BROWSERS_PATH` (`run.py` defaults both to the
`/opt/node22/lib/node_modules` and `/opt/pw-browsers` installs of the build machine). The single expected
network failure is the Google Fonts `<link>` in the template when offline.

## Licences

Application code (this directory) is Apache-2.0 like the rest of the repository. The generated packs are
data: Pepper&Carrot CC BY 4.0, wordfreq-derived frequencies CC BY-SA 4.0, and — when the enrichment
stage runs — Wiktionary/EtymDB CC BY-SA 4.0 and Tatoeba CC BY 2.0 FR; `ATTRIBUTION.md` in the pack names
every author and translator and is regenerated on each build, never hand-edited.
