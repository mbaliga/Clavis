# Build plan

How to get from what is seeded in this repo to a running Clavis prototype. Written the same way
the rest of this set is written: the tradeoffs stated plainly, the open decisions left open rather
than silently resolved.

---

## 1. What is actually here, and what a "prototype" requires

This repo currently holds:

- `docs/` — the full concept, research and (web-build) architecture documentation, seeded verbatim.
- `reference-prototype/` — the corpus-pipeline **generator** (`extract.py`, `annotate.py`, `etym.py`,
  `pack.py`, `pseudo.py`), the 2,368-line `template.html` application, and the vendored G6 graph
  library. This is code, not data or a build artifact.

It does **not** hold: the Pepper&Carrot corpus, the EtymDB 2.1 CSVs, any of the generated
intermediates (`scenes.json`, `lexicon.json`, `etym.json`, `pseudo.json`), or a built `clavis.html`.
Every number quoted in `docs/*.md` (649 scenes, ~14,000 lemmas, 30% Romance etymology coverage) is
real, but it describes a build made from those missing inputs — nothing in this repo can currently
reproduce it without first pulling them in.

So "build a prototype" has two genuinely different meanings, and this plan covers both because
they serve different purposes:

- **Track A — reproduce the reference build.** Run the existing pipeline against the real corpus
  and get `clavis.html` working again, in a browser, today. Low engineering risk because almost
  none of the code is new; the risk is entirely in the external inputs (licensing, corpus
  availability, dependency drift since the pipeline was written).
- **Track B — build the native prototype.** A real Android app, in the same constellation as
  `Fyl-Manager` and `Hyle-Design-System`, that eventually replaces the single-HTML-file approach.
  This is what the shared feature branch across four repos signals is actually wanted.

Recommendation: **do Track A first, cheaply, before committing to Track B.** It validates the
pipeline and the corpus with a couple of days of work instead of weeks, and Track B's UI phases
(§4.5) are much easier to build against a `template.html` you can run side by side as the oracle
for "does this look and behave right."

---

## 2. Blocking item: Pepper&Carrot's licence

`00-README.md`, `06-app-architecture.md` §2, and `09-handoff.md` §7 all flag the same thing and it
is worth repeating here because it blocks both tracks, not just a commercial one: **the licence
Pepper&Carrot's translated SVGs are actually distributed under is not documented anywhere in the
research corpus**, despite the entire corpus — 156 pages, all nine languages' balloon text — being
built on it. The `[Deevad/peppercarrot_epNN_translation]` GitHub snapshots and the current
`framagit.org/peppercarrot/webcomics` source should state per-episode and per-translation-layer
terms; confirm them before pulling the corpus into this repo, not after. If translations carry
per-translator terms distinct from Revoy's own CC BY on the art, that changes what can be
redistributed inside `clavis.html`'s single file versus what needs to stay a build-time-only input.
This is a research task (read the actual repo licences and any translator-facing terms), not an
engineering one, and it is the one item on this plan that isn't a coding decision.

---

## 3. Track A — reproduce the reference build

Steps, using `reference-prototype/` mostly as-is:

1. **Pull the corpus.** `reference-prototype/README.md` has the clone loop for the 23 episode
   repos. Re-verify against the current `framagit.org/peppercarrot/webcomics` layout — the pipeline
   was written against a 2017 GitHub snapshot and the framagit source "same layout, more languages"
   claim in the README should be checked against `extract.py`'s actual parsing assumptions (it
   branches on Inkscape flowed text vs. plain `<text>`/`<tspan>` by episode range; a newer source
   layout could shift where that boundary falls).
2. **Pull EtymDB 2.1.** `etym.py` needs the split CSVs; `09-handoff.md` §2 notes these aren't part
   of any repo and were expected to live wherever `etym.py`'s `SCR` constant points.
3. **Run the pipeline**, adjusting the hardcoded `/home/claude` paths as you go:
   `extract.py` → `annotate.py` → `pseudo.py` (or recover `pseudo.json` if it shipped anywhere) →
   `etym.py` → `pack.py`. Expect to spend real time here — the scripts assume a specific Python
   environment (`wordfreq[cjk]`, `simplemma`, `fugashi`+`unidic-lite`, `mecab-python3`+
   `mecab-ko-dic`, `opencc-python-reimplemented`) that has had years to drift since this was
   written.
4. **Validate against the numbers in `06-app-architecture.md`** (lemma counts per language, ~90%
   credible-rect rate, 649/852 scene retention). A materially different number means either the
   corpus source changed shape or a pipeline assumption broke — worth knowing before trusting
   anything built on top.
5. **Recreate the Playwright checks** described in `09-handoff.md` §3, at minimum
   `clavis-boot-test.js` and the cloze-leak check in `v10.js` — they don't ship in this repo (they
   lived in `/tmp/` in the original environment as scratch) but the file describes exactly what
   each one does and the cloze-leak one is explicitly "the most important single regression."

Output: a working `clavis.html` you can open in a browser, and a validated corpus pipeline you can
regenerate from. This is a real, complete prototype on its own terms — the docs already argue the
case for it. Track B below is about the different thing of making it a native app in the
constellation.

---

## 4. Track B — native Android prototype

### 4.1 Why native, and why this looks like the intent

The four repos sharing this session's branch — `Shared-Libraries-asoc`, `Fyl-Manager`, `Clavis`,
`Hyle-Design-System` — are one ecosystem: `Hyle-Design-System` is the cross-platform design system
(`dev.aarso:hyle` tokens, `Finish`/`Pulse`/`RadiantHues`), `Shared-Libraries-asoc` is the
cross-app library set consumed by every app in it (`dev.aarso:cell-shell`,
`dev.aarso:crash-recovery`, `dev.aarso:feedback`, `dev.aarso:search-core`,
`dev.aarso:evidence-schema`), and `Fyl-Manager` is a mature example consumer. Building Clavis as a
Kotlin/Compose Android app that joins that constellation is a different, larger undertaking than
Track A, but it's what the repo scaffolding around this task points at, and the payoff is real:
Clavis inherits crash recovery, a navigation shell, and (if wired up per §4.6) a citation/evidence
model for free, instead of reinventing them the way the original single-file build had to.

This also fixes real, named costs of the HTML approach that `06-app-architecture.md` §12 already
admits: the 11 MB single-file payload with no incremental updates, no OS-level file access or
share-sheet integration, and a hand-rolled canvas transition that took three rejected rewrites to
land (§7.7) — a Compose app gets normal APK asset delivery and can draw on Hyle's own motion
language instead.

### 4.2 Baseline project setup

| Item | Value | Why |
|---|---|---|
| Package | `io.github.mbaliga.clavis` | Matches the `io.github.mbaliga.<app>` convention (`Fylz` = `io.github.mbaliga.fylz`) |
| UI | Jetpack Compose, Material 3 | Constellation-wide |
| AGP | **8.9.1** | The lockstep version in `Shared-Libraries-asoc/README.md` — everything in the composite build graph must match |
| Kotlin | **2.1.0** | The constellation default; `Fyl-Manager` diverges to 2.1.20 and that divergence is recorded as a gap to close, not a precedent to repeat |
| Gradle wrapper | **8.14.3** | AGP 8.9.1's floor |
| Submodules | `hyle-design-system`, `shared-libraries` via `git submodule add` + `includeBuild(...)` | Same pattern as `Fyl-Manager` |

Bring in, at minimum:

```kotlin
implementation("dev.aarso:hyle:0.1.0")
implementation("dev.aarso:crash-recovery:1.0.0")
implementation("dev.aarso:cell-shell:<version>")
```

`CrashRecovery.install(this, appLabel = "Clavis")` in `Application.onCreate()`, before anything
that could itself throw, and the `maybeShowRecovery` check as the first line of the launcher
`Activity` — per `Hyle-Design-System/README.md`'s own snippet.

Evaluate, don't commit yet:

- **`dev.aarso:evidence-schema`** — see §4.6. Its record shape (`recordType`, `recordClass`,
  `evidenceGrade` E0–E5, `provenance`) was built for Baseline/Crocodyl/Ebbflow, a different domain,
  but it's generic enough that it may be a better home for Clavis's per-hypothesis instrumentation
  than a bespoke schema. Read `evidence-schema/src/main/resources/evidence/v1/*.schema.json`
  against `02-hypothesis.md`'s falsification protocol before deciding either way.
- **`dev.aarso:search-core`** — a pure-JVM query language/facet/ranking engine. Possibly useful for
  the lexicon and Cosmos search, but it's unclear yet whether its query model fits "search 14,000
  lemmas across nine languages by surface form, reading and origin" without distortion. Worth a
  short spike before adopting.
- **`dev.aarso:feedback`** — low-cost opt-in feedback (share-sheet only, no telemetry). Fits
  Clavis's own no-telemetry stance (`01-premise.md` §5) directly; likely worth adopting as-is once
  there's a settings screen to hang it off.

### 4.3 Data pipeline: from single-file blob to Android assets

The web build's `pack.py` step 3–5 (gzip+base64 the JSON, WebP+data-URI the pages, inline the 1.38
MB G6 bundle) exists entirely to satisfy "one HTML file, no network, no server." None of that
constraint applies to an APK: assets ship inside it natively. The corpus pipeline's first three
stages carry over unchanged (`extract.py`, `annotate.py`, `etym.py` produce the same
`scenes.json`/`lexicon.json`/`etym.json`); write a new packer (`pack_assets.py`, alongside
`pack.py` rather than replacing it, since Track A still wants the original) that instead:

- Writes `scenes.json`, `lexicon.json`, `etym.json`, `pseudo.json` as plain (optionally gzipped)
  JSON into `app/src/main/assets/corpus/`.
- Resizes and encodes page art as WebP files under `app/src/main/assets/pages/`, keyed the same way
  `scenes[].m` keys them now, instead of inlining as data URIs.
- Drops the G6 inlining step entirely — the native Cosmos view doesn't use G6 (see §4.5).

Keep the derived-index construction (`byLang`, `byLemma`, `byPage`, `lexArr` — `05-information-
architecture.md` §5) as a one-time boot-time build step in Kotlin, same as the web build does in
JS.

### 4.4 Domain layer

These port with little structural change because they're already pure logic, separated from
rendering, in the original:

- **Content model** — `Scene`, `LexEntry`, `EtymEntry`, `PseudoEntry` data classes mirroring
  `05-information-architecture.md` §5's field shapes (`scenes[].q{lang}` per-language rects,
  `k{lang}[]` tokens as `[surface, lemma, zipf, bridges, reading]`, etc.).
- **FSRS-5 scheduler** (`06-app-architecture.md` §7.3) — `retr`, `ivlDays`, `initS`/`initD`,
  `recallS`/`forgetS`/`shortS`, `nextD`, with the same published 19-parameter weight vector,
  `DECAY = -0.5`, `FACTOR = 19/81`. This is arithmetic; port it as a pure Kotlin object and unit
  test it against the same fixed inputs the original would have used.
- **Selection engine** (`05-information-architecture.md` §6) — the two-stage lane bandit +
  `pickForLane`/`nextEncounter` — is exactly the "named, pure policy object" pattern `Fyl-Manager`
  already uses throughout (`ArchiveExtractionPolicy`, `RecycleBinPolicy`, etc.). Port it the same
  way: `LaneSelectionPolicy`, `EncounterSelectionPolicy`, each testable with no device, no Compose,
  no corpus loaded beyond a small fixture.
- **Rect/lettering helpers** — `rectFor`, `rectOK`, `pageOf`, `lineOf` (`06-app-architecture.md`
  §7.6) are small pure functions over data already in memory; port verbatim in behavior.

State persistence: the original is one `localStorage` blob (`clavis.v1`, schema `v: 2`,
merge-on-load migration — `05-information-architecture.md` §7). For the native build, `cards{l}
{lemma}` is genuinely relational (one row per language×lemma, queried by due-date) and is worth a
Room table from the start rather than a blob, since that's exactly the kind of query FSRS scheduling
needs. Everything else in `S` (settings, bandit state, logs, view/nav state) can stay a single
serialized document (DataStore, JSON) matching the original shape closely enough that the migration
story stays simple if data ever needs to move between the two.

### 4.5 UI: the open IA decision, then the view-by-view plan

**The one decision this plan is deliberately not making silently:** the web build uses a five-item
bottom dock (Learn/Cosmos/Paths/Letters/You, `05-information-architecture.md` §2.1) with three of
those views morphing into each other as "one substance re-pooling." Nothing in the constellation
uses a bottom dock — `cell-shell`'s pattern (used identically by `Fylz` and `Foto Xplorr`) is one
home surface with secondary surfaces parked off **edges** (drag-to-reveal, no back-stack entry,
shake-to-refresh instead of pull-to-refresh), specifically because the pull-down space is reserved
and tabs were rejected constellation-wide.

These two models aren't a small stylistic gap — picking one changes what "the app" visually is on
first launch. Two honest options, not a recommendation dressed up as a foregone conclusion:

- **(a) Adapt `cell-shell`'s rooms model.** Learn is home. Cosmos and Paths — the two views the
  source docs describe as answering "what do I know" and "where could I go" rather than "what's
  due right now" — become edge rooms (e.g. Cosmos right, Paths left), matching the LEFT/RIGHT
  semantics `Fylz` already established (locations/tools as lateral, not sequential, moves). You and
  Letters/Settings/Story become further rooms or reachable from within one. This keeps Clavis
  visually and interactionally consistent with every other app in the constellation, at the cost of
  reworking the three-way "ferrofluid" morph the original docs treat as the signature interaction.
- **(b) Keep the bottom dock as designed**, accepting that Clavis looks different from its siblings.
  This preserves the original IA and its morph transition intact, and is less work up front, at the
  cost of the inconsistency (a)  avoids.

Recommendation, weakly held: (a), because the "same dataset, three arrangements, fluid re-pooling
between states" idea the source docs already describe (`05-information-architecture.md` §1) maps
onto edge-drag-to-reveal rooms unusually well — better, arguably, than it mapped onto the bottom-tab
model it shipped with, since a `cell-shell` room is a lateral peek at another arrangement of the
same live surface, not a route to a different screen. But this changes the product's shape, so
confirm it before the Learn/Cosmos/Paths screens get built, not after.

View-by-view build order, once that's settled:

1. **Onboarding + baseline test** — no corpus rendering dependencies, so first. Straightforward
   Compose forms/flows; the baseline test's yes/no-with-pseudoword-foils logic is pure and portable
   directly from `pseudo{lang}[]`.
2. **Learn (the pool) + session/encounter loop** — the actual core: whole-page lettering
   (`letterPage` in the original) becomes a Compose layer that positions `Text`/plates over a WebP
   `Image` using the same `[x, y, w, h]` fractional boxes already computed by the pipeline —
   `BoxWithConstraints` plus fractional offsets is a direct fit, no canvas needed for this part. Port
   the cloze-mask three-value contract (`null`/`''`/string, `05-information-architecture.md` §4.2)
   exactly, and write the Compose-test equivalent of the cloze-leak regression before considering
   this view done — the original build actually shipped the leak once, per `09-handoff.md` §5.
3. **Cosmos** — Field mode (the pseudo-3D word cloud) is a hand-rolled canvas renderer in the
   original and ports reasonably directly to Compose `Canvas`. Explore mode (AntV G6 — fisheye,
   edge-filter lens, BubbleSets, force layout) does not port; G6 is JS-only. For the prototype, skip
   Explore's interactive graph entirely rather than attempting a from-scratch Compose graph-viz
   engine with fisheye/lens/bubble-set parity — that's real, separate scope. A static or
   force-simplified node/edge view (e.g. backed by a lightweight JVM force-layout pass run once at
   load) can stand in later; it is explicitly not a prototype blocker.
4. **Paths** — the branch-fan computation (`test`/`due`/`new`/`read`/`game` kinds,
   `05-information-architecture.md` §4.4) is pure logic once the selection engine exists; render it
   as cards first ("as a tree"), skip the `d3-force` map presentation for the prototype.
5. **Letters, You, Settings, Colophon, Story** — mostly straightforward CRUD-shaped Compose screens
   over data already modeled by step 2–4; sequence last.

For the morph/transition itself: don't re-run the original's three-rewrite process. Hyle's own
motion law (`Pulse` — "heartbeat, not weather" — and `Finish`) already exists for exactly this
class of problem, and `docs/PHILOSOPHY.md`'s recommendation #1 ("no material ships without its law
written down") is a genuinely useful discipline to borrow here: write down what state the
Learn/Cosmos/Paths transition is showing (which is, per `05-information-architecture.md` §1,
literally "the same dataset, differently arranged") before animating it, and prefer Compose's own
shared-element / `AnimatedContent` transitions for the prototype over a bespoke particle or
ferrofluid canvas system. A closer Hyle-native treatment (a real ferrofluid-style material) is a
reasonable v2, not a prototype requirement.

### 4.6 Citations and the hypothesis instrumentation

`06-app-architecture.md` §13 and `09-handoff.md` §7 both list the same open item: the app already
tracks what it needs to test H1 (cognate vs. non-cognate acquisition cost), H4′ (origin-surfaced
stability), and H8 (branch-prediction accuracy) in state, but never surfaces it anywhere a person
can read it — "the largest gap between what this project claims and what it can currently
demonstrate," per `00-README.md`. A native rebuild is a natural point to fix this rather than carry
the gap forward again, and `evidence-schema`'s shape (a graded, provenance-carrying, versioned
record — `recordType: CLAIM`, `evidenceGrade: E0..E5`) is close enough to "a hypothesis, its
falsification criteria, and the observations bearing on it" that it's worth a genuine evaluation
(§4.2) rather than reinventing a bespoke schema by default. Whichever schema is used, treat "a `You`
screen that reports cognate-vs-non-cognate cost and origin-surfaced-vs-not stability" as in scope
for the prototype's later phases, not a someday item — it's cheap once the FSRS/selection data
already exists, and it's the one thing the source docs are most explicit about wanting.

### 4.7 Testing

Mirrors `Fyl-Manager`'s pattern directly: pure layers carry the tests.

| Layer | Test type | Covers |
|---|---|---|
| FSRS scheduler | JVM unit test | `retr`/`ivlDays`/stability updates against known inputs |
| Selection engine | JVM unit test | Lane bandit proportional selection, `pickForLane` due/new/fluency ordering |
| Rect/cloze helpers | JVM unit test | `rectOK` bounds, the three-valued mask contract, `lineOf` CJK space-stripping |
| Whole-page lettering, cloze | Compose UI test | The cloze-leak regression — no answer text present pre-answer, in **both** the cropped and full-page render paths |
| Boot/onboarding → session | Compose UI test | Equivalent of `clavis-boot-test.js`: fresh install through onboarding to a completed session |

CI: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, matching `Fyl-Manager`'s workflow.

---

## 5. Prototype definition of done

Deliberately narrow, matching the project's own N=1, one-thing-at-a-time posture rather than
attempting all nine languages at once:

- One productive lane (**Spanish**) and one receptive sibling (**Italian** or **French**) fully
  wired: onboarding → baseline test → Learn → a complete session mixing cloze/read/echo encounters
  → Cosmos Field view showing touched words → You record.
- Real corpus data (Track A's output, or the native asset pipeline fed from the same pipeline run),
  not fixtures — the point is to prove the pipeline-to-app path works, not just the UI shell.
- The cloze-leak regression passing as an automated test, not a manual check.
- FSRS scheduling actually persisting and advancing due dates across app restarts.

Everything in §4.5 marked as deferred (Explore/G6 parity, the Paths map presentation, the full
nine-language matrix, CJK/Arabic etymology beyond what the pipeline already produces at 0.6–4.5%
coverage) stays deferred past this point by design.

---

## 6. Debt inherited from the spec, not to re-solve now

Carried forward from `06-app-architecture.md` §13 and `09-handoff.md` §7 — real, already known, and
explicitly out of scope for a first prototype in either track:

- Chinese lemma matching sits at 0.6% coverage and is suspected to be a keying bug, not a true
  coverage ceiling.
- Korean and Arabic balloon geometry (81–82% credible vs. 90% elsewhere) leaves roughly one balloon
  in five unlettered in those two languages.
- FSRS parameters are the published population defaults, un-fit to any real usage yet — there's no
  usage to fit them to until the prototype exists.
- Arabic root extraction and the wiktextract/kaikki.org etymology migration are both real
  improvements, both bigger than "prototype" scope.

---

## 7. Open decisions for the user

1. **Track A, Track B, or both, and in what order.** This plan recommends A then B; confirm that's
   still wanted once the licence question in §2 has an answer.
2. **The IA decision in §4.5** — bottom dock vs. adapted `cell-shell` rooms. Recommendation given,
   not assumed.
3. **`evidence-schema` adoption** (§4.6) — worth a short spike against the actual hypothesis records
   before committing either way.
4. **How committed to full Pepper&Carrot licence verification (§2)** the user wants to be before
   further engineering spend — this is a research task for the user or someone with time to read
   the actual repo/translator licences, not something resolvable from this seed alone.
