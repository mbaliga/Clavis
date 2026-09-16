# Decisions

Architectural and product decisions for Clavis, numbered so other documents can cite them
(`D-4`), in the same spirit as the constellation's `DECISIONS.md` files. Each entry records the
decision, the evidence or constraint that forced it, what it costs, and what would reopen it.
Superseded decisions stay in the file with a note rather than being deleted.

Status legend: **settled** — implemented or committed to; **provisional** — decided on current
evidence, expected to be confirmed by a build or a measurement named in the entry.

---

## D-1 · Clavis is a Kotlin Multiplatform app; the web build stays as the executable spec

**Decision.** The product is a Kotlin Multiplatform + Compose Multiplatform application with an
Android target and a JVM desktop target (Linux, macOS, Windows) built from one codebase. The
single-file web build (`reference-prototype/`) is kept as the *reference implementation*: the
corpus pipeline still produces it, and behavioural parity is checked against it. It is not
shipped as a product.

**Why.** The user's targets are Android first, then Linux, macOS and Windows (`04-personas.md`
P1). KMP is the only single-codebase route that is also consistent with the rest of the
constellation (Kotlin/Compose everywhere). The web build's own README calls itself "the
authoritative spec … that native ports must reproduce", so keeping it as the oracle costs
nothing and buys a regression baseline.

**Cost.** Two rendering stacks exist during the transition (Compose and the web template).
Anything changed in the domain logic must be changed in both until the template is frozen.
Decision: the template is frozen at v0.10 behaviour; new features land natively only.

**Status.** Settled.

## D-2 · Toolchain: Kotlin 2.1.20 · Compose Multiplatform 1.8.x · AGP 8.9.1 · Gradle 8.14.3 · JDK 17

**Decision.** Pin AGP 8.9.1 and the Gradle wrapper 8.14.3 (the constellation's lockstep, required
for any composite build with `Shared-Libraries-asoc` or `Hyle-Design-System`); Kotlin 2.1.20 (the
same as `Fyl-Manager`; the newest line that Compose Multiplatform 1.8 supports without moving
AGP); JDK 17 as the documented toolchain, compiled on JDK 21 where that is what the machine has.
Exact versions and the sources they were verified against live in `docs/build-platform.md`.

**Why.** `Shared-Libraries-asoc/README.md` documents that Gradle hard-fails a composite build that
mixes AGP versions, so AGP is not a free choice. Kotlin 2.1.20 rather than 2.1.0 because Compose
Multiplatform 1.8 requires ≥ 2.1.20 and 1.8 is the first line with stable multiplatform
resources, navigation and adaptive layouts, all of which this app uses.

**Cost.** Kotlin 2.1.20 + AGP 8.9.1 is outside KGP's officially *tested* AGP range. In practice the
first full build chain (debug + release APK, uber-jar, `.deb`, headless UI tests) completed with
**zero** compatibility warnings under `--warning-mode all`, and `Fyl-Manager` ships on the same
pair. Newer Kotlin/CMP lines are deferred until the constellation moves AGP; the upgrade recipe is
in `build-platform.md` §1.

**JDK.** The machine only had JDK 21; `org.gradle.toolchains.foojay-resolver-convention`
auto-provisioned Eclipse Adoptium 17 for compilation, tests and jpackage, so every artifact is
built on 17 as documented, and CI's `setup-java` 17 makes the resolver a no-op.

**Status.** Settled — confirmed by the green build recorded in `build-platform.md` and by the
first GitHub Actions run on a clean runner.

## D-3 · Repository layout: `:shared` (domain, no UI) · `:composeApp` (UI + entry points) · `pipeline/` (Python)

**Decision.** `:shared` is a KMP library holding everything that is not rendering: corpus model
and loader, FSRS-5, the selection engine, the baseline test, the Paths engine, state persistence,
the language registry and content-pack manifests. `:composeApp` holds Compose UI, navigation,
theme, and the Android/desktop entry points, plus `expect/actual` platform services (TTS, audio,
file import, app directories). `pipeline/` is the Python corpus pipeline (parameterised successor
of `reference-prototype/`), run off-device; its outputs are committed as app assets.

**Why.** Everything the original web build did well was already separated from rendering in
its JavaScript (`06-app-architecture.md` §7); porting that separation into a module boundary
makes the domain testable with plain JVM unit tests, the pattern `Fyl-Manager` uses ("the pure
layers carry the tests"). Committing generated assets (a few MB) keeps CI builds reproducible
without CI needing MeCab, jieba and 2 GB of comics.

**Status.** Settled.

## D-4 · Corpus source is the current Pepper&Carrot repository on framagit, not the 2017 GitHub snapshot

**Decision.** The pipeline reads `https://framagit.org/peppercarrot/webcomics` (sparse: `ep*/lang/`),
which as of 2026‑09‑01 carries **39 episodes** and **74 languages** — versus 23 episodes and 40
languages in the `Deevad/peppercarrot_epNN_translation` snapshots the original pipeline used.

**Evidence.** Cloned and inspected on 2026‑09‑01. The framagit SVGs for ep01–23 are structurally
identical to the snapshot (same element histogram for ep01 P01); episodes ≥ 13 are
`<text>/<tspan>` based; episodes 18, 24, 27 and 29 **mix** `flowRoot` and `text` on one page, so
the extractor must classify per element, not per episode as the original did. Licence: the
repository README states CC BY 4.0 for all content; each language folder's `info.json` credits
its translators, which is what the in-app attribution uses.

**What it changes.** Per-episode language coverage falls off in newer episodes (ep01: 69 languages,
ep39: 14), so the app's "how much of the corpus is available in language X" readout must be
computed per language from the manifest rather than assumed uniform. Hindi (`hi`), Tamil,
Malayalam, Turkish and Hebrew become available as candidate languages (see D-6).

**Cost.** Regenerated corpus numbers will differ from every figure quoted in `01`–`09`; those
documents describe the v0.10 build and are left as the historical record with this note.

**Status.** Settled.

## D-5 · Etymology graph: wiktextract (kaikki.org) is the primary source; EtymDB is secondary

**Decision.** Origins, cognate/borrowing edges, IPA and audio references are derived from the
kaikki.org per-language Wiktionary extracts (CC BY-SA 4.0), with EtymDB 2.1 (CC BY-SA 4.0) kept as
a second source whose links are unioned in and provenance-tagged. Every displayed origin or edge
traces to a specific source row (`07-etymology.md` §8 — "never invent an etymology" — still
holds).

**Evidence (measured 2026‑09‑01 against the real v0.10 lexicon).** Share of corpus lemmas with an
etymology — EtymDB (shipped) → Wiktionary (measured):

| Language | EtymDB | Wiktionary | Headword found |
|---|---:|---:|---:|
| Korean | 2.8% | **70.1%** | 76.7% |
| Arabic | 4.5% | **40.3%** | 67.1% |
| Japanese | 2.9% | **27.6%** | 51.6% |
| Chinese | 0.6% | **24.5%** | 93.0% |
| Spanish | 36.8% | **51.9%** | 57.7% |
| French | 35.0% | **48.8%** | 57.5% |
| Italian | 31.5% | **47.3%** | 53.3% |
| German | 27.4% | **49.9%** | 58.9% |
| Dutch | 33.1% | **49.1%** | 53.5% |
| English | 51.4% | **75.8%** | 81.3% |

This is the migration `07-etymology.md` §7 ranks as the highest-value open item, now with
numbers. The residual misses are dominated by translator credit names that leaked into the
lexicon, interjections, digits, and un-lemmatised inflections (Korean conjugated stems, Arabic
clitic forms) — each of which is a pipeline fix (D-7, lexicon hygiene), not a source limitation.

**How edges are built.** (a) Wiktionary etymology templates (`inh`, `bor`, `der`, `cog`, and the
CJK/Sinographic templates such as Korean `ko-etym-sino`/`bor|ko|zh`, Japanese kango markers) give
typed parent links; (b) Latin/Middle Chinese/etc. `descendants` lists (17,610 Latin entries carry
one) give explicit ancestor → descendant edges across the Romance and Sinographic families;
(c) two corpus lemmas sharing an attested ancestor within the depth cap are cognates, with the
original hub guard (> 40 corpus descendants ⇒ skip) retained. Chinese is matched through both the
headword and Wiktionary's `forms` (simplified/traditional), which is what raised headword recall
to 93% and answers the "keying bug" suspicion in `07-etymology.md` §5.

**Cost.** The kaikki extracts are large (11.8 GB raw for 13 languages); the pipeline streams and
compacts them (~340 MB compacted) and never commits them. CC BY-SA attribution obligations
extend to Wiktionary contributors and are carried in the pack manifest and the colophon.

**Status.** Settled (source); provisional (edge-construction details, to be confirmed by the
regenerated graph's coverage report).

## D-6 · Languages are data, not code: a registry, nine enabled by default, more available

**Decision.** `pipeline/languages.json` is the single registry of languages (ISO code,
Pepper&Carrot folder code, name, family, script, direction, tokenizer, `wordfreq` code, reading
system, TTS locale, default lane mode, colour). The pipeline processes every language marked
`enabled`; the app ships the resulting packs and lets the user turn languages on or off and set
each one to productive or receptive. The nine original languages are on by default. Additional
languages present in the corpus ship where a tokenizer and frequency data exist — first
candidates: Portuguese, Latin, Russian, Polish, Ukrainian, Turkish, Hindi.

**Rules the app enforces.** One productive language per family (`03-evidence.md` §3, H2): enabling
a second productive sibling is allowed only after an explicit, cited warning, and the default is
receptive. Turning a language off hides it and pauses its cards; it never deletes state.
Per-language corpus coverage (scenes available, lemma count, etymology coverage) is shown on the
language screen, because coverage is uneven (D-4) and the app must not imply otherwise.

**Why Hindi is a candidate and not yet a default.** P1's native language and the P2 heritage
persona both point at it; the corpus has it from ep01, `wordfreq` has frequency data, Piper has
no permissively licensed voice (D-8), and Devanagari needs a bundled font on desktop (D-9). It
ships as an opt-in receptive language once the pipeline's Hindi tokenisation is validated.

**Status.** Settled (mechanism); provisional (exact extra-language list, pending tokenizer checks).

## D-7 · Content packs: versioned, licence-tagged, attribution-carrying

**Decision.** All learning content is delivered as content packs with a manifest (`id`, `kind`,
`version`, `languages`, `licence`, `attribution[]`, `source`, `generatedAt`, `sizes`). Pack kinds
in v1: `comics` (Pepper&Carrot scenes + page art), `lexicon`, `etymology` (D-5), `sentences`
(Tatoeba text, CC BY 2.0 FR), `pronunciations` (IPA from Wiktionary; audio *references* to
Wikimedia Commons files with per-file licence), and `user-media` (text the user imports:
`.txt`, `.srt`, `.lrc`). Bundled packs are loaded from app resources; optional packs and audio
are downloaded only on explicit user action and cached locally.

**Licence rule.** Only CC0 / CC BY / CC BY-SA content is bundled or offered for download; anything
NC or ND is excluded at pipeline time, including Tatoeba audio not explicitly CC BY/BY-SA/CC0
(measured 2026‑09‑01: permissive sentence audio exists mainly for German 5.3k, Russian 6.9k,
English 6.7k, French 2.1k, Italian 1.6k; Spanish 11, Japanese 27, Dutch/Chinese/Arabic/Korean 0).
Wiktionary/Commons word audio is rich for German (~57% of corpus lemmas), French (~53%), Dutch
(~52%), English (~67%), Chinese (~30%), Arabic (~12%) and near-absent for Spanish, Italian,
Japanese, Korean — the app shows real audio where it exists and falls back to TTS (D-8),
labelled as such.

**Lexicon hygiene.** Lemmas are kept only if attested in `wordfreq` or Wiktionary for that
language, which removes the translator names and credit tokens that survived into v0.10.

**Status.** Settled (format and rule); provisional (which optional packs ship in v1.0).

## D-8 · Text-to-speech: downloaded Piper voice → system TTS → no button

**Decision.** The Speak action uses, in order: a Piper voice (MIT runtime, executed as a separate
process, downloaded on demand and only from the permissively licensed list), the platform's own
TTS (Android `TextToSpeech`; macOS `say`; Windows SAPI), and otherwise the button is not shown.
The app never claims pronunciation scoring (`04-personas.md`, "who this is not for").

**Evidence (2026‑09‑01 audit of `rhasspy/piper-voices` model cards).** Permissive (CC0 / CC BY /
CC BY-SA / public domain) voices exist for German, English, Spanish, French, Italian, Dutch,
Polish, Portuguese, Russian, Ukrainian and Chinese; **none** for Arabic, Japanese, Korean, Hindi
or Turkish (their datasets are NC or restricted). A Spanish medium voice synthesised 2.6 s of
audio in 0.38 s on this 4-core machine. Piper's runtime bundles `espeak-ng` (GPL) for
phonemisation, which is why it is invoked as an external tool rather than linked into the app.

**Cost.** Voices are ~63 MB each; they are never bundled, only downloaded by choice. The
languages where the premise is hardest to test (CJK, Arabic) depend on system voices.

**Status.** Settled.

## D-9 · Typography: Hind · Rozha One · IBM Plex Mono, with Noto fallbacks bundled on desktop

**Decision.** UI text uses **Hind** (Latin + Devanagari), display uses **Rozha One**, readings
and IPA use **IBM Plex Mono** — the three faces the v0.10 web build requested from Google
Fonts (all SIL OFL 1.1). For scripts those do not cover, the desktop builds bundle Noto Sans
Arabic, Hebrew, JP, KR and SC (OFL; ~50 MB); the Android build relies on the system's Noto
fonts for CJK/Arabic to keep the APK small, bundling only Hind/Rozha/Plex.

**Why.** The desktop JVM has no guarantee of CJK or Arabic system fonts on Linux and Windows,
and a language app that renders tofu for four of its nine languages is broken. Android ships
these scripts in every locale. Hind is also the natural choice for the Hindi candidate (D-6).

**Status.** Settled; font subsetting for size is a later optimisation.

## D-10 · Navigation is adaptive: bottom dock on compact widths, navigation rail on expanded

**Decision.** The five primary destinations (Learn, Cosmos, Paths, Letters, You) live in a bottom
dock on phones and a left navigation rail on tablets and desktop windows. Settings, Colophon and
Story remain secondary destinations reached from context (D-14 fixes the IA weaknesses recorded
in `05-information-architecture.md` §9). Learn ↔ Cosmos ↔ Paths keep a shared transition (the
"same substance re-pooling" idea, `05-information-architecture.md` §1) implemented with
Compose's own transition APIs first; a ferrofluid-style material treatment is a later pass.

**The alternative considered.** `dev.aarso:cell-shell`'s edge-room pattern, used by `Fyl-Manager`
and `Foto Xplorr`. It is an Android-only Compose library and cannot be used from KMP common code,
and the three co-equal arrangements of one dataset map poorly onto "one home + parked rooms".
Recorded here so it is not re-litigated by accident; reopen if cell-shell gains a KMP target.

**Status.** Settled.

## D-11 · Constellation reuse: crash-recovery yes (Android), Hyle tokens partially, cell-shell and evidence-schema no

**Decision.** `dev.aarso:crash-recovery` is consumed on Android via git submodule + `includeBuild`
with explicit `dependencySubstitution` (the Hyle tombstone gotcha in `Fyl-Manager`'s
`settings.gradle.kts`) once the core app compiles; `dev.aarso:feedback` is adopted for the
opt-in, share-sheet-only feedback flow. From Hyle, Clavis takes the motion discipline ("law"
before material; weight not bounce) and the token *structure*, but keeps its own quiet
low-contrast palette from `06-app-architecture.md` §8 — Hyle's near-black provenance-lit field
is a different product's material. `evidence-schema` is not adopted in v1: its record shape is
close to what the hypothesis instrumentation needs, but the cost of a cross-repo schema
dependency is not justified until the `You` view's metrics stabilise.

**Status.** Provisional (crash-recovery wiring lands after the first green multiplatform build).

## D-12 · State is one JSON document per profile, written atomically, with an import path from the web build

**Decision.** All learner state (`lanes`, `cards`, `tests`, `bandit`, `inter`, `routes`, `seen`,
`log`, `pathLog`, `settings`, …) is a single `kotlinx.serialization` document persisted to the
app's data directory with atomic replace and a schema version + migration step, mirroring the
web build's `clavis.v1` shape closely enough that a JSON export from the web build imports
directly. No account, no server, no sync (`01-premise.md` §3).

**Why not a database now.** Card counts are bounded by touched lemmas (thousands, not millions);
a document load at start-up is milliseconds and keeps the migration story trivial. Reopen when
review logs grow past what a single document loads comfortably, or when FSRS parameter
optimisation (`06-app-architecture.md` §13) needs queryable review history.

**Status.** Settled.

## D-13 · No telemetry, no network by default; every download is an explicit user action

**Decision.** The app makes no network requests unless the user starts a download (voice,
optional pack, Commons audio) or an import. There is no analytics, no crash upload
(crash-recovery is local-only by design), and the store listing's data-safety section says so.

**Status.** Settled.

## D-14 · IA fixes carried from the weaknesses list

**Decision.** (1) Letters becomes a Paths branch kind ("learn the script first") offered whenever a
CJK/Arabic lane is enabled without script familiarity, and stays in the dock. (2) Story is
reachable from Learn ("just read") as well as from Paths. (3) The pedagogically significant
switches (`monoSession`, `readings`) are surfaced on the language screen, not only in Settings.
(4) The colophon keeps a "back to the claim" affordance. (5) `You` gains the hypothesis
instrumentation: cognate vs non-cognate acquisition cost (H1), origin-surfaced vs not stability
(H4′), branch-prediction accuracy (H8), maintenance interval (H9), each with the N=1 caveat
wording from `02-hypothesis.md`.

**Status.** Settled (scope); implementation order is in `10-build-plan.md`.

## D-15 · Streaks: none, still

**Decision.** No streaks, XP, leagues or loss-aversion mechanics in v1. `research/06` recommends
*forgiving* streaks with the largest behavioural dataset in the corpus; the build keeps the
stronger no-streak position deliberately and records that this is a design choice, not an
evidential one (`03-evidence.md` §10). Reopen after the first real usage data exists.

**Status.** Settled.

## D-16 · Release shape

**Decision.** Semantic versions; one GitHub Release per tag carrying: Android APK (universal) and
AAB (signed when the CI secrets exist, otherwise unsigned and clearly named), Linux `.deb` and per-OS
uber-jars (each bundles its platform's Skia — there is no single jar for all systems), macOS `.dmg` (unsigned unless signing secrets exist; the Gatekeeper
caveat is documented), Windows `.msi`. Desktop installers are built on their own OS runners
(jpackage cannot cross-build). Application code is Apache-2.0 (constellation convention); the
data packs are CC BY-SA 4.0 with an `ATTRIBUTION.md` naming Pepper&Carrot's author and
translators, Wiktionary, EtymDB, Tatoeba and Commons contributors. Procedure in
`docs/RELEASE.md`.

**Status.** Settled.

## D-17 · Testing shape

**Decision.** Pure-layer JVM unit tests for FSRS, the selection engine, the Paths engine, rect and
cloze helpers, and pack loading; headless desktop Compose UI tests for the boot/onboarding →
session flow and the cloze-leak regression (both render paths); CI runs them on every push.
Device acceptance (TalkBack, RTL, font scaling, foldables) is a documented gate before a stable
release, as in `Fyl-Manager/docs/DEVICE_ACCEPTANCE.md`.

**Status.** Settled.

## D-18 · Honesty rules are product requirements

**Decision.** The copy rules from the evidence set are enforced in review, not left to taste:
never "subconscious"/"absorb"; coverage is "scenes readable at the 95% threshold", never "% of a
language learned"; every on-screen claim carries a citation; the store listing and onboarding
state that the method is being tested on one person and is not a validated pedagogy
(`01-premise.md` §6).

**Status.** Settled.

## D-19 · Baseline-test pool admits Unicode letters, not only ASCII

**Decision.** The yes/no baseline test draws its candidates with a Unicode-letter test
(`^\p{L}+$`) for Latin-script lanes and drops capitalised surfaces with a Unicode-aware check
(`isUpperCase()`), instead of the web build's `/^[^\W\d_]+$/u`, which in JavaScript keeps `\w`
ASCII-only, and its `^[A-Z]` proper-noun test.

**Why.** Reproducing the original regex exactly (as the first port did, for parity) meant no accented
lemma — `poción`, `über`, `città`, every Polish or Turkish word with a diacritic — could ever appear
in the test, which silently biased the threshold toward the plainest vocabulary and got worse with
every language added under D-6. Measured on the shipped lexicon after the other pre-filters, the
ASCII rule alone excluded 52 % of otherwise-eligible Polish lemmas, 40 % of Turkish, 24 % of French,
17 % of Portuguese, 15 % of German and 14 % of Spanish, including top-band words such as `się`,
`być`, `être`, `für`, `über`, `más`, `também`, `você`, `için` and `çok`. The web behaviour is a
defect, not a design.

**Cost.** A learner's threshold from the native test is not numerically comparable with one taken in
the web build. `tests{l}.pool` records which pool rule produced a result (`"unicode"`; a result
imported from the web build has none). A deliberate parity divergence (template-spec §4.1), listed in
`shared/README.md`.

**Status.** Settled; implemented in the review pass of 2026‑09‑02 (`ClavisEngine.start`,
`TestResult.pool`, `BaselineTestTest`).

## D-20 · Script is a rendering property; the language-ID game groups by it as-is

**Decision.** `LanguageInfo.script` stays truthful (Korean is `HANGUL`, Japanese and Chinese are
`CJK`), and the language-ID game's "same script first" option ordering follows that field
(`ClavisEngine.makeLangId`).

**Considered and rejected.** Grouping the CJK *family* for the game so that Korean lines still get
Japanese and Chinese as the hard options — what the web build's coarser `t: 'cjk'` table did.
Script drives fonts, spacing metrics and lettering; overloading it (or adding a second grouping
axis) for a game heuristic would either break those or leave two notions of "script" to keep in
step. The game still draws from every language present in the corpus, so Korean appears as an
option — it simply is not privileged as a look-alike of kanji/hanzi, which it is not.

**Status.** Settled.

## D-21 · The Letters branch: cleared by a visit, never displacing the floors, offered only where a primer exists

**Decision.** Three rules for the Paths `LETTERS` branch (D-14):

1. **A visit clears it.** The branch is offered until `coach["letters:<iso>"]` is set, and that flag
   is set both by taking the branch and by the learner opening that language's tab in Letters on
   their own.
2. **It never displaces the floors.** The READ and GAME floors are decided on the lane-branch count
   alone, exactly as template §6.2 does (READ whenever fewer than five lane branches exist — always,
   given the cap of four; GAME when it still fits under five). LETTERS is then inserted before them
   without counting against the cap, so the fan may hold six entries. The alternative — LETTERS
   taking one of the five slots — meant a learner with three CJK/Arabic lanes never saw the
   language-ID game, and one with four lane branches saw neither free reading nor the game, until
   they had visited Letters.
3. **Only where a primer exists.** The branch requires the registry entry to say both
   `needsScriptPrimer` and `hasScriptPrimer` (Japanese, Korean, Chinese and Arabic today; the loader
   defaults the second flag to true for exactly those four when a registry omits it). A language that
   needs a primer nobody has written yet (Russian, Ukrainian, Hindi, …) shows the honest "no primer
   yet" card in Letters and gets no branch whose "then" promises that "Letters stops being a wall".

**Why.** A branch that keeps nagging after the learner has found the primer, a branch that hides the
game, and a branch that opens onto an empty room are all the kind of manufactured urgency
`01-premise.md` §5 rules out.

**Status.** Settled; all three implemented in the review pass of 2026‑09‑02
(`ClavisEngine.computeBranches`, `LanguageInfo.hasScriptPrimer`, `LettersScreen`,
`ClavisStore.setLetterTab`, `PathsTest`).

## D-22 · Generated content packs are committed to the repository

**Decision.** `pipeline/run.py --install` copies the generated packs (about 22 MB: comics pages,
lexicon, etymology, pronunciations, sentences, registry, manifest, attribution) into
`composeApp/src/commonMain/composeResources/files/packs/`, and that directory is committed. CI
therefore builds every artifact without MeCab, jieba, 2 GB of comics or network access.

**Cost.** Repository growth on every regeneration whose page art changes (WebP pages are ~15 MB).
Reopen when regenerations become frequent; the escape hatch is Git LFS or a release-asset pack
downloaded at build time.

**Status.** Settled.

## D-23 · Which languages ship, and how thin ones are shown

**Decision.** Seventeen languages are processed and shipped: the nine originals (on by default),
plus Portuguese, Latin, Russian, Polish, Ukrainian, Turkish and Hindi as opt-in receptive
languages. Measured on the 39-episode corpus (2026‑09‑02): Arabic has no translations beyond the
first eight episodes (177 scenes), Latin 389, Turkish 223, Korean 470, and **Hindi only 16 scenes
in two episodes** — the Languages screen shows a "thin" badge below 200 scenes or 6 episodes and
the real counts next to every language, and never implies uniform coverage (D-4). Latin has no
`wordfreq` data, so its Zipf values are derived from corpus counts and labelled as such.

**Status.** Settled for v1.0; the list grows as the pipeline's tokenizer table does.

## D-24 · Etymology coverage after the migration (record)

Measured on the 17-language lexicon with Wiktionary primary + EtymDB secondary (D-5), share of
lemmas with an origin / with at least one edge: es 74.9/65.3, fr 67.1/58.6, it 68.0/58.7,
de 57.3/37.5, nl 68.1/45.5, en 87.1/66.3, pt 71.9/62.2, la 58.3/64.6, ru 39.8/29.6, pl 49.4/23.7,
uk 36.0/36.1, ko 37.3/18.5, ja 22.4/17.3, ar 18.2/6.5, zh 8.9/12.3, tr 68.6/10.1, hi 84.4/13.3;
30,813 typed edges (25,059 cognate, 2,894 borrowing, 2,860 derivation) against 5,546 before, and
58 Sino-Japanese/Sino-Korean/Chinese triples. Chinese origin strings stay low by design: Wiktionary's
Chinese etymology sections describe glyph origins, so the Sinographic stratum shows up as
ja/ko ↔ zh borrowing *edges* rather than origin text; Arabic root boxes are morphology, not an
origin language, and are not displayed as etymology. Every emitted fact is traceable in
`pipeline/build/enrich/etym_provenance.jsonl`.

## D-25 · Review pass 2026‑09‑02 (record)

An adversarial review of the merged application, verified finding by finding against the code and
the built artifacts, was applied in one pass. Fixed — domain: the ASCII-only baseline pool (F1,
D-19); the Letters branch displacing the READ/GAME floors (F2) and being offered for languages
without a primer (F3), both D-21; an unreadable state document being silently replaced on the next
save — it is now moved to `clavis-state.json.corrupt-<epoch>` and the app says so (F7); a session
served in a language the learner had paused when every lane was off — it is now refused with the
reason and the Learn pool is disabled (F12); `ClavisVersion.CODE` disagreeing with the emitted
`versionCode` (F-10, now 100 with the `MAJOR·10000 + MINOR·100 + PATCH` rule documented). Fixed —
application: unbounded file import, now 2 MB read through a bounded stream, 5,000 stored lines and a
printable-text check (F5); learner-state serialisation and `fsync` on the UI thread, now encoded on
the UI thread once per burst of commits and written by one ordered writer thread (F6); the only
network path accepting `http://`, any size and leaving `.part` files, now HTTPS-only including
redirects, capped at 25 MB, cleaned up, with cache names derived from a SHA-256 of the URL (F8); the
Android `TextToSpeech` never shut down (F9); key auto-repeat grading consecutive reads (F10); system
TTS receiving the utterance where a leading hyphen made it an option, now after `--` or via the
environment for PowerShell (F11); `junit:junit:3.8.2` (CPL 1.0) riding into the desktop runtime
through jlayer's POM (F-3). Fixed — documents and release: the privacy policy, store listing and
third-party notices still denying the `INTERNET` permission the manifest declares for tapped
recordings (F4, F-4); the LGPL 2.1 text absent from every desktop distribution, now shipped in the
application resources, shown in the colophon and appended to `THIRD_PARTY_NOTICES.md` (F-2); the
fonts table and pack versions (F-6); the Linux data-directory casing and the missing cache directory
in `PRIVACY.md` (F-7); the stale API-35 and desktop-icon wording in `CHANGELOG.md` and `RELEASE.md`
(F-5); the release preflight admitting pre-release numbers the build rejects (F-9); the web job
uploading `pack_web`'s side files (F-11); blank translator credits reaching `ATTRIBUTION.md` and the
manifest (F-8); and this file's duplicated D-19–D-21 entries, merged above. Refuted and left alone:
the blank P00 title pages (F-1) — they are the upstream header artwork, faithfully rendered.

**Status.** Record.

## D-26 · Release assets go straight to a draft GitHub Release; CI keeps only the small APK

**Decision.** `release.yml` no longer hands artifacts between jobs. `preflight` creates one *draft*
Release (for a tag, on that tag; for a `workflow_dispatch` dry run, a throw-away draft that is
deleted at the end and never published); every build job uploads its files into it with
`gh release upload`; the `release` job (which also runs, minus the final publish step, on a dry
run — so a dry run exercises the download/checksum/body path too) downloads them back, writes
`SHA256SUMS.txt`, and flips the draft to published only when all five build jobs succeeded; a
`cleanup` job always deletes a dry run's throw-away draft, but leaves a failed *tag* build's draft
in place — deleting it unconditionally would strand its already-uploaded assets past a `Re-run
failed jobs`, which is exactly the case a partial multi-OS release run needs. `ci.yml` uploads only
the R8 release APK and its mapping, for three days, and an upload failure can no longer fail the
verification job.

**Reason (measured).** GitHub Actions *artifact* storage is metered per account (500 MB on the
Free plan, recalculated every 6–12 h) and was exhausted on 2026‑09‑15 by CI itself: every push
uploaded the debug APK (31 MB), the R8 APK (22 MB) and the Linux uber-jar (83 MB) with 14-day
retention. The old release handoff needed about 630 MB of intermediates per run (three ~83 MB
jars, a ~100 MB `.deb`, `.dmg`, `.msi`, APK, AAB, web build), so it could not have succeeded on
this account even with an empty quota. Files attached to a Release are stored separately and are
not counted against that quota.

**Cost.** Build jobs need `contents: write`; `gh` (preinstalled on every hosted runner) is the
transport; a cancelled run can leave a draft that the next run's cleanup or a maintainer deletes.
Auto-generated release notes were dropped — the curated `CHANGELOG.md` section is the body.

**Status.** Settled; see docs/RELEASE.md §0, §3, §8.

## D-27 · The desktop script fallbacks ship the Regular weight

**Decision.** `notosanshebrew/jp/kr/sc.ttf` are the Regular static instances (weight class 400,
no `fvar`), fetched from Google Fonts' static set and verified from the files' own `name` and
`OS/2` tables. The files they replace were the variable fonts whose *default* instance is Thin
(weight 100), so Hebrew, Japanese, Korean and Chinese fallback text on desktop rendered hairline-
thin against Hind Regular — and the notice table said "Regular". The bundled desktop font payload
fell from 37 MB to 23 MB in the process. Arabic and Devanagari were already Regular.

**Status.** Settled; `THIRD_PARTY_NOTICES.md` quotes each file's embedded copyright line verbatim.

## D-28 · Pronunciation audio takes a second source: Lingua Libre and the Commons categories

**Decision.** `enrich_pronunciations.py` matches lexicon lemmas against Lingua Libre records
(queried through its SPARQL endpoint by transcription, CC BY‑SA 4.0 by that project's policy) and
against the small per-language Commons pronunciation categories, in addition to the audio files
English Wiktionary lists. Every new candidate still passes the same per-file licence lookup and is
written in the same record shape, so the apps and `pack_assets.py` are unchanged.

**Reason (measured).** The kaikki.org extract of English Wiktionary carries audio for 42 of
809,603 Spanish entries, 6 Japanese, 25 Korean, 6 Polish, 2,047 Italian and 0 Latin — against
92,620 French and 301,486 German. The shipped 2026‑09‑02 pack therefore had recordings for 3 of
1,627 Spanish lemmas (Japanese 1, Korean 5, Polish 0, Italian 144) while French had 1,523, and no
amount of re-running the Wiktionary-only stage could change that. The results of the first
networked run are in `pipeline/ENRICHMENT.md` §6.4 and in each language's coverage line in the
app.

**Status.** Settled; opt-in flags (`--lingualibre`, `--commons-categories`) so the Wiktionary-only
run stays reproducible.

## D-29 · The device pass is partly automated: RTL and 2× screenshots, a semantics audit

**Decision.** The headless desktop UI suite renders every screen under a forced right-to-left
layout direction and at 2× font scale (in addition to light/dark × phone/desktop), and an
accessibility audit test walks the semantics tree of every screen asserting that each clickable
node has a label or role and a ≥ 48 dp target. That is the repeatable, honest part of the
docs/RELEASE.md §9 device pass; TalkBack traversal, the predictive-back preview animation,
edge-to-edge insets and Arabic line height on a real API-36 device stay manual and are still
open (`CHANGELOG.md` → Known pre-release requirements).

**Status.** Settled.

## D-30 · Usability pass 2026‑09‑16 (record)

A screenshot review of every screen (light/dark × phone/desktop, then RTL and 2×) and a
code-level heuristic walkthrough (Nielsen's ten, first run, empty and error states, feedback,
navigation, copy, mobile ergonomics, consistency, contrast, performance-as-UX) are recorded in
`docs/USABILITY.md`, with each finding's severity, evidence and status. The fixes applied in this
pass and the follow-ups left open are listed there; one finding from the screenshots (content
"hidden under the floating dock") was withdrawn on reading the code — every screen already ends
with a dock-height spacer. Open product questions raised by the pass (whether "days you showed
up" is a streak in disguise, D-15; the 27 minutes of baseline checks before the first honest
session; whether Cosmos needs a list view for assistive technology) are for the maintainer.

**Status.** Record.
