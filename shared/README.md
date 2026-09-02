# `:shared` — the Clavis domain layer

Pure Kotlin (KMP: `androidTarget` + `jvm("desktop")`), no UI, no platform services beyond file
and gzip primitives. It ports the v0.10 web build's behaviour (`reference-prototype/template.html`,
spec in `template-spec.md`) with unit tests on both targets: `./gradlew :shared:allTests`.

## Package map

| Package `io.github.mbaliga.clavis.core…` | What lives there |
|---|---|
| `.` (contracts) | `Model.kt` (Lang, Scene, Token, Corpus + indices), `State.kt` (ClavisState, Card, StateStore, Clock, DayKey), `Engines.kt` (Scheduler, SelectionEngine, EncounterGrader, BaselineTester, PathsEngine, Lettering, Mask, lineOf), `Languages.kt`, `Packs.kt`, `Services.kt` |
| `.` (helpers) | `Helpers.kt`: `DAY_MS`, `BANDS`/`bandLabel`, `hash01` (FNV-1a, JS-exact), `jsRound`, `pct`, `pageOf`, `tokenJoin`, `SystemClock`, `LocalDayKey`, `freshState(registry, now)` · `Copy.kt`: every user-facing string, verbatim · `Fsrs.kt`: retrievability façade |
| `.engine` | `Fsrs5` (the scheduler, template §2) · `ClavisEngine` (one class implementing `SelectionEngine`, `EncounterGrader`, `BaselineTester`, `PathsEngine`, plus You/Story/lane-overview helpers) · `LaneScore`, `YouMetrics`, `LaneOverview`, `SessionSummary` |
| `.data` | `CorpusDecoder` (compact pack JSON → `Corpus`), `LanguageRegistryLoader` / `ListLanguageRegistry`, `PackManifests`, `DefaultContentPackLoader` (over a `suspend (path) -> ByteArray` reader), `gunzip` (expect/actual, `GZIPInputStream` on both targets), `maybeGunzip` (magic-byte detection) |
| `.state` | `JsonStateStore` (atomic JSON document, merge-on-load, web-export import), `FileIo` (expect/actual over java.io/nio) |

## Wiring (what the UI does)

```kotlin
val loader   = DefaultContentPackLoader({ path -> Res.readBytes("files/$path") })   // paths like packs/comics/scenes.json.gz
val registry = loader.languages()                    // packs/languages.json
val corpus   = loader.loadCorpus()                   // scenes + lexicon (+ etym, pseudo, pages)
val store    = JsonStateStore(appDirs, registry)     // clock = SystemClock, fileName = "clavis-state.json"
val state    = store.loadOrFresh()                   // merge-on-load (§1.4) + D-12 lane fill; never rejects on `v`
val engine   = ClavisEngine(corpus, registry, state, SystemClock, Random.Default, LocalDayKey())
```

Call sequence for one session (the engine never calls back into the UI; the UI calls `store.save(engine.state)` after every mutation, like the web build's explicit `save()`):

1. `engine.rollDay()` at every render (zeroes `newToday` on a new local day).
2. Start: `val s = engine.startSession(now)` (honours `state.intent` from Paths and `settings.monoSession`); Paths `game` branch → `engine.startGameSession(now)`.
3. Render `s.cur` (`Encounter.Read | Cloze | Echo | LangId`, each with `why`). Helpers: `Mask.forCloze(sc, l, lemma, revealed)`, `engine.clozeOptions(e, random)`, `engine.clozeGapIndex(e)`, `engine.showFastReturnNotice(e, now)`, `engine.readCopy(e)`, `engine.showReadCoach(e)`, `engine.isKnown(l, tok, now)` for the `.unk` marking, `pageOf(corpus, sc, l)` for the whole-page view, `lineOf(l, text)` for ja/zh display and TTS, `tokenJoin(registry, l, toks)`.
4. Grade: `answerCloze(s, e, chosenOption, ms, now): ClozeResult` · `finishRead(s, e, understood, userUnknownLemmas, now): ReadResult` (pass the lemmas the user considers unknown after flips: `modelKnown ? flipped : !flipped` per token) · `answerEcho(s, e, grade 1|2|3, now): EchoResult` · `answerLangId(s, e, guess, now): Boolean` · `routeHelped(l, via)` for the "Did one of these help" buttons. The UI sets `state.coach["cloze"] = true` when it first renders the cloze coach box (the read coach flag is set by `finishRead`).
5. Advance on `Next` (or immediately after a read): `engine.advance(s, now)` = `s.i++; nextEncounter()`. When `s.done`, `engine.summary()` gives the "That's one dip." numbers from `state.lastSession`.

Baseline test: `val t = engine.start(l, now, random)` → per item `engine.answer(t, knowIt)` (returns true when finished) → `val (result, toast) = engine.finish(t, now)`.
Paths: `engine.computeBranches(now)` → `engine.takeBranch(b, now)` returns a `BranchAction` the UI dispatches (`StartTest(l)` → start the test; `OpenStory(l)`; `StartGameSession` → `engine.startGameSession(now)`; `StartSession(intent)` → `engine.startSession(now)`; `OpenLetters(l)`); `engine.canUndo()` / `engine.undo()` (toast `Copy.UNDONE_TOAST`); `engine.laneOverview(l, now)` for "The longer view".
You: `engine.youMetrics(now)`. Story: `engine.storyEpisodes(l)`, `engine.storyPages(l, ep)`, `engine.pageScenes(pageKey, l)`. Learn: `engine.dueTotal(now)`, `engine.untestedLanes()`, `engine.corpusCoverage(l, now)`.
Backup: `store.exportJson()`; `store.importJson(text)` (throws `IllegalArgumentException(Copy.NOT_A_BACKUP)` when it is not a Clavis backup) — then rebuild the engine with `engine.state = store.current!!`; `store.reset()`.

## Determinism

Every method takes `now` explicitly (the web build's `Date.now()`), and the engine draws all randomness from the injected `kotlin.random.Random` (`nextDouble`/`nextInt(until)`/`shuffled(random)`); `Clock` is used only for `rollDay()` defaults, `DayKey` for the local `Y-M-D` key and the You "days you showed up" count. Tests use fixed clocks, `ScriptedRandom` and stub day keys.

## Parity divergences (deliberate)

| Where | Divergence | Why |
|---|---|---|
| `activeLanes()` | registry order, lane not OFF **and present in the corpus** (the web filtered `byLang` at each call site, and counted lanes without scenes toward the ≥3-lane langid/game rule) | D-6 |
| `chooseLane()` | when no lane is open returns the first registry language with scenes, why `nothing else is open` (the web threw) | D-14 |
| `computeBranches()` | new floor branch `LETTERS` ("Learn the {Lang} script first") when an active lane `needsScriptPrimer` and `coach["letters:<iso>"] != true`; placed before READ and GAME within the fan cap of 5; `takeBranch` sets the coach flag and `letterTab` | D-14 |
| `startSession()` / `nextEncounter()` | `state.intent` is kept until the first encounter is consumed (`advance`), so Paths' `↶ undo` is reachable; the web nulled it immediately | D-14 |
| `JsonStateStore.load()` | per-lane maps missing from a stored document are filled from the registry defaults (the web left them missing) | D-12 |
| log | the 6000-entry cap is applied on every push (the web trimmed only on read/cloze pushes) | uniform bound |
| fresh `v` | a fresh native document has `v = 3` (contract); stored/imported `v` is preserved as-is, never rejected | contract |
| export | lane modes are written lowercase (`prod/rec/off`) and unknown top-level keys are carried along so the web build can read a native export | compatibility |
| `finishRead` | takes a lemma set, so a lemma that occurs twice in a balloon is flipped as one unit (the web tracked flips per token index) | contract |

## Quirks preserved on purpose

- Baseline-test pool filter: the template's `/^[^\W\d_]+$/u` admits only ASCII letters in JavaScript (`\w` stays ASCII under the `u` flag), so Latin-script lemmas with accents (`poción`, `über`) never appear in the test — reproduced exactly (`ASCII_LETTERS`).
- `pickForLane`'s due-scene sort breaks full ties at random; the port picks uniformly among the tied top group.
- `unlockCount` keeps the redundant `unk.size <= learned.size + 1` bound.
- `makeLangId` draws options from every registry language present in the corpus, including lanes set to OFF.
