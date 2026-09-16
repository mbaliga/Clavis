# Etymology

The word graph: what it is, where it comes from, how it is built, what it actually covers, and where it fails.

Etymology is the intellectual centre of Clavis — the thing that makes nine languages one object rather than nine courses. It is also the component with the largest gap between ambition and delivered coverage, and this document reports both.

---

## 1. What the graph is for

Two jobs, and they are different:

1. **Origin** — where a word comes from. Answers *why does this word look like this?* Surfaced on a word's card and in the Cosmos.
2. **Links** — which words in *other* languages share that history. Answers *what else do I already half-know?* This is the thing that makes the second and third language cheap.

A third job it deliberately does **not** do: it is not a mnemonic device presented at first contact. Barcroft 2002 found that elaborate meaning-work at first exposure suppresses encoding of the word's *form* — so origins are gated behind the second or third meeting (`03-evidence.md` §4). The graph is a retention and inference tool, not a teaching gimmick.

---

## 2. Source: EtymDB 2.1

**Fourrier & Sagot 2020**, *EtymDB 2.0: A Large Etymological Database from Wiktionary* — [aclanthology.org/2020.lrec-1.397](https://aclanthology.org/2020.lrec-1.397/). Licence **CC BY-SA 4.0**, which fits the project's CC0/CC BY/CC BY-SA rule.

It ships as three tab-separated files:

| File | Contents |
|---|---|
| `etymdb_values.csv` | `id ⇥ lang ⇥ … ⇥ lexeme` — every word form, in every language, with an integer id |
| `etymdb_links_info.csv` | `type ⇥ child ⇥ parent` — typed relations between ids |
| `etymdb_links_index.csv` | expands negative parent ids into multiple real parents |

Relation types used: **inheritance**, **borrowing** (`bor`), **cognacy** (`cog`), **derivation** (`der`).

**Why this source and not another.** The alternatives were rejected on licence grounds — CogNet and PanLex are NC-licensed and excluded by the project rule that keeps a paid tier possible. `research/05-data-and-platform.md` recommends **wiktextract/kaikki.org** as a richer, permissively-licensed, weekly-refreshed replacement, and notes that while EtymDB's licence is verified, **all of its scale and coverage figures in the memo are unverified**. That recommendation has not been acted on and is the single highest-value open item for this component (§7).

---

## 3. Build (`etym.py`)

### 3.1 Matching corpus lemmas to database ids

Script-aware normalisation, because a naive string match loses most of the non-Latin corpus:

| Script | Normalisation |
|---|---|
| Arabic | strip diacritics (`ً ٌ ٍ َ ُ ِ ّ ْ`, tatweel, Quranic marks); fold `إ أ آ ٱ → ا` |
| Chinese | try both the simplified form and its traditional conversion via OpenCC `s2t` — EtymDB's Chinese entries are predominantly traditional |
| Japanese, Korean | exact match |
| Latin-script | lowercase |

One lemma can match several ids (homographs, multiple senses); all are kept and walked.

### 3.2 Walking to ancestors

```python
def up(ix, depth=3):
    """Return {ancestor_id: (dist, type_of_first_hop)} walking child->parent."""
```

The walk is depth-first (the frontier is popped from the tail) with a depth cap of 3. Depth 3 is a tuning choice: deep enough to reach Latin from Spanish through Vulgar Latin, shallow enough to stop before Proto-Indo-European swallows the graph.

### 3.3 Choosing *the* origin

Ancestors are ranked by a hand-ordered priority list — Latin, Vulgar/Late/Medieval Latin, Ancient Greek, Sanskrit, Persian, Arabic, Middle Chinese, Old Chinese, Old French, Middle French, Anglo-Norman, Middle English, Old English, Old Norse, Old/Middle High German, Middle/Old Dutch, Frankish, Gothic, Old Spanish, Old Portuguese, Portuguese, Ottoman Turkish, Turkish, Hebrew, Aramaic, Egyptian, then the proto-languages.

Selection prefers **nearest first, then highest priority**. The winning ancestor becomes a human-readable string with its language name spelled out (`"Latin potione"`, `"Middle High German unde"`), tagged `inh` (inherited) or `bor` (borrowed).

### 3.4 Building the edges

Two mechanisms:

1. **Direct links.** If a corpus lemma is directly a parent or child of another corpus lemma in a *different* language, that is an edge, typed `bor` / `cog` / `der`.
2. **Shared ancestor.** If two corpus lemmas in different languages reach the same ancestor within a combined distance of 4, they are cognates.

**The hairball guard.** In the shared-ancestor edge pass — and *only* there — ancestors with **more than 40 descendants in the corpus are skipped**:

```python
if len(lst) > 40: continue   # hub ancestors (e.g. PIE roots) create hairballs
```

A Proto-Indo-European root connects hundreds of words in every branch. Including it makes the graph technically correct and visually useless — every word becomes adjacent to every other. Dropping hubs keeps the edges that *mean* something to a learner: this Spanish word and that Italian word are the same word.

Note the scope: the guard governs **edges only**. Origin selection is untouched, so a handful of lemmas still carry a proto-language origin string (seven Proto-Indo-European, plus Proto-Germanic and Proto-Semitic) where nothing nearer was attested.

Edges are capped at **8 per lemma**, sorted deterministically.

### 3.5 Output format

```json
{
  "es": {
    "vuelo":  { "o": "Latin volāre", "ot": "inh",
                "e": [["it","volare","cog"], ["it","volere","cog"]] },
    "poción": { "o": "Latin potione", "ot": "bor" }
  },
  "de": {
    "und": { "o": "Middle High German unde", "ot": "inh",
             "e": [["en","and","cog"], ["en","end","cog"],
                   ["nl","einde","cog"], ["nl","en","cog"]] }
  }
}
```

- `o` — origin, display-ready
- `ot` — `inh` inherited, `bor` borrowed
- `e` — edges as `[language, lemma, type]`

---

## 4. What it actually covers

Measured on the shipping build. **These are the honest numbers and they are uneven.**

| Lang | Lemmas | In graph | With origin | Edge endpoints | Coverage |
|---|---:|---:|---:|---:|---:|
| English | 1832 | 941 | 898 | 1103 | **51.4%** |
| Spanish | 1828 | 672 | 640 | 958 | **36.8%** |
| French | 1863 | 652 | 618 | 1027 | **35.0%** |
| Dutch | 1857 | 615 | 577 | 651 | **33.1%** |
| Italian | 1845 | 581 | 552 | 923 | **31.5%** |
| German | 1923 | 526 | 488 | 693 | **27.4%** |
| Arabic | 605 | 27 | 27 | 2 | **4.5%** |
| Japanese | 2088 | 60 | 34 | 125 | **2.9%** |
| Korean | 867 | 24 | 14 | 56 | **2.8%** |
| Chinese | 1424 | 8 | 4 | 8 | **0.6%** |

**Totals:** 3,852 origins, 5,546 edge endpoints. Edges by type: cognate 5,052, borrowing 320, derivation 174. Origins: 3,416 inherited, 436 borrowed.

Top origin languages by count:

| | | | |
|---|---:|---|---:|
| Latin | 1516 | Old High German | 127 |
| Middle English | 568 | Old Spanish | 88 |
| Middle Dutch | 470 | Ancient Greek | 82 |
| Old French | 272 | Old English | 53 |
| Middle High German | 233 | Portuguese | 45 |
| Middle French | 222 | Hebrew | 24 |

(Counted by matching the language-name prefix of each origin string, so multi-word etyma such as *Latin nulla res nata* are filed correctly.)

---

## 5. The honest assessment

**For Romance and Germanic, the graph works.** A third of the Spanish, French, Italian, Dutch and German corpus has an attested origin and real cross-language links, dominated by Latin as the shared substrate exactly as the premise predicts. When a learner meets Spanish `vuelo` and the app says *Latin volāre, and Italian has `volare`*, that is a true statement derived from a real database, not a plausible-sounding generation.

**For CJK and Arabic, it does not.** Japanese 2.9%, Korean 2.8%, Chinese **0.6%**, Arabic 4.5% — and Arabic has essentially **no edges at all** (2 endpoints in the entire corpus).

This is not a small caveat. It means:

- **The Sinographic stratum — the single most interesting cross-language claim available in the CJK family — is almost entirely missing.** Japanese 単位, Korean 단위 and Chinese 單位 are the same borrowed Middle Chinese compound. The graph knows this for a handful of words and misses it for hundreds.
- **Arabic's status as the "control condition" (`01-premise.md` §4) is currently untestable**, because the app cannot show what Arabic *doesn't* share when it can barely show what any word's history is.
- What survives for Japanese is skewed toward **gairaigo** — `エピソード → English episode`, `ダース → Korean 다스`. True, useful, and a thin slice of the interesting relationships.

**Root causes, in order of size:**

1. **EtymDB is Wiktionary-derived, and Wiktionary's etymology coverage is far denser for European languages** than for CJK — a well-known asymmetry in the source, not a bug in the matcher.
2. **Chinese script variant matching is fragile.** 0.6% suggests the OpenCC round-trip is not catching most forms; EtymDB's Chinese entries may be keyed differently than assumed. This is the most likely quick win.
3. **Japanese and Korean lemmatisation** produces surface forms (inflected verbs, particle-attached nouns) that do not match dictionary headwords.
4. **Arabic normalisation strips diacritics but does not handle the root-and-pattern morphology** that carries almost all Arabic etymology. Matching a surface form to a triliteral root is a genuinely harder problem than fold-and-lowercase.

---

## 6. How the graph is used in the app

- **Word card** — origin string and up to 8 links, with the link type distinguished (borrowed vs cognate) and each link tappable to jump to that word in its own language.
- **Cosmos / Field** — origin family drives the angular position via `ORIGIN_FAM`, a regex classifier mapping origin strings to Romance / Germanic / CJK / Semitic sectors.
- **Cosmos / Explore** — edges become graph edges; the Relations lens filters them by radius; the Families tool draws BubbleSets contours over them.
- **Bridges** — the packed token format carries up to two `bridges` per token: which other language's word will help you here. These are surfaced during encounters and logged as `S.routes[lang][other]` — *bridges that helped* — which is the app's own record of whether the premise is doing any work.
- **Interference tracking** — the mirror measure, `S.inter[lang][other]` — *look-alikes that fooled you*. This is the false-friend instrument required by `03-evidence.md` §4: without it, the app cannot tell transfer from interference.

---

## 7. Open work, in priority order

1. **Migrate to wiktextract / kaikki.org.** Recommended by the research, permissively licensed, weekly-refreshed, and substantially richer — particularly for the languages where EtymDB is thinnest. This is the highest-value change available to the project.
2. **Fix Chinese matching.** 0.6% is almost certainly a keying problem rather than a genuine absence, and it is blocking the most interesting claim in the CJK family.
3. **Add a Sinographic layer explicitly.** Sino-Japanese and Sino-Korean vocabulary shares are documented in memo 01, sourced to Wikipedia citing the NHK 1989 corpus — Wikipedia-sourced, so verify before quoting. Even a hand-curated set of the top few hundred shared compounds would transform the CJK lanes.
4. **Arabic root extraction.** Needed before Arabic can do the job the design assigned it.
5. **Surface the bridge/interference measures.** They are recorded in state, shown as one line each in Paths, and interference also appears as a *Mix-ups* column in the You table. They are also the direct test of H1 and H4′ and deserve their own view.
6. **Verify Pepper&Carrot's licence** before any commercial step (`03-evidence.md` §12 item 9), and EtymDB's coverage claims against the primary paper — memo 05 marks those figures `[unverified]` even though the licence itself is verified.

---

## 8. A note on what the graph must never do

It must not **invent** an etymology. Every origin string and every edge in the shipping build traces to a row in EtymDB. Where the database is silent, the app shows nothing rather than a plausible guess — because a fabricated cognate is worse than an absent one: it teaches a false relationship that the learner will then generalise from, and the false-friend literature (Marecka et al. 2021) says wrong meanings acquired through apparent similarity are exactly the stickiest kind of error.

Low coverage is an honest failure. Confabulated coverage would not be.
