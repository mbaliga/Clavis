# `:composeApp` — the Compose Multiplatform UI

Android + JVM desktop from one codebase (D-1, D-3). This module holds rendering only: theme,
navigation, screens, components, the application holder (`ClavisStore`) and the `expect/actual`
platform services. Everything that schedules, selects, grades or persists lives in `:shared`.

## Package map (`io.github.mbaliga.clavis…`)

| Package | What |
|---|---|
| `ClavisApp.kt` | Composition root: boots the store, theme choice, adaptive shell (dock < 600 dp, rail ≥ 600 dp), the `!onboarded → test → session → view` dispatcher, keyboard map, toasts, confirmations. `ViewContent()` is the view switch. |
| `store` | `ClavisStore` (the holder: packs, engine, state store, transient `UiState`, every intent), `UiState` (+ `LocalRev`), `UiPrefs` (theme preference file). |
| `platform` | `PlatformServices` bundle, `ShareSheet`, `NoSpeaker`/`RefusingDownloader`, `expect scriptFallbackFonts()` / `platformReducedMotion()`. Actuals in `androidMain/platform` and `desktopMain/platform`. |
| `ui.theme` | `ClavisTokens` (full light/dark table, lane/cosmos/family/kind colours), `ClavisTheme` (+ `LocalClavisTokens`, `ClavisShapes`, `ThemeMode`), `ClavisType` (font families + type ramp), `Motion`. |
| `ui.nav` | `Views.kt` (`VIEW_NAMES`, `NAV`, icons), `Dock`, `Rail`, `ScreenTopBar` (+ `LocalCompact`, `LocalRailShown`, `LocalShowGear`). |
| `ui.components` | `Primitives` (SoftCard, Eyebrow, PrimaryButton, QuietButton, LinkButton, Chip, Segmented, LaneDot, ProgressBar, StatTile), `Cite` (`CitedText`, `Cite`), `TokenRow` (`CaptionTokens`), `SceneBox` + `Lettering` (`LetteringMath`), `PageImages`, `ParallelBox`, `LaneRow`, `SpeakButton`, `Overlays` (ToastHost, ConfirmDialog, CoachBox, TopBar, SettingRow). |
| `ui.screens.*` | one screen per file: `onboarding`, `test`, `learn` (+ `Pool`), `session` (`SessionScreen` chrome/summary, `Encounters` bodies), `settings`, `you` (+ `Hypotheses`), `colophon`, `paths` (`PathsScreen` tree + longer view, `PathsMap` canvas), `story`, `letters` (`LetterData` verbatim tables), `languages`, `cosmos` (`CosmosModel` pure maths, `FieldCanvas`, `ExploreCanvas`, `WordCard`, `CosmosScreen`), `media` (`MediaParse`, `MediaScreen` + reading mode, `SentencesScreen`). |
| `ui.colophon` | `References.kt` (the 40 refs, ids frozen), `Claims.kt` (15 claims, glossary, credits copy). |

## Adding a screen / route

1. Write `ui/screens/<name>/<Name>Screen.kt` as `@Composable fun NameScreen(store: ClavisStore, ui: UiState)`.
   Start the body with `@Suppress("UNUSED_VARIABLE") val rev = LocalRev.current` — the learner
   document and the engine's `Session`/`BaselineTest` are mutated in place, not snapshot state,
   so this read is what makes the screen recompose after every store commit. **Every child
   composable that reads the document must read `LocalRev.current` too**: with the compose
   compiler's strong skipping, a child that only receives `store` is skipped after a commit.
2. Use `ScreenTopBar(store, VIEW_NAMES["<view>"]!!)` at the top and end scrollable content with
   `Spacer(Modifier.height(130.dp))` (room for the floating dock).
3. Add the case to `ClavisApp.ViewContent()`. The view ids live in `ClavisStore.VALID_VIEWS`,
   `VIEW_NAMES` and, for dock items, `NAV`; rail-only secondary destinations are `RAIL_MORE`.
4. Navigate with `store.setView("<view>")`; the view is persisted as `state.view` (D-10).
   Learn ↔ Cosmos ↔ Paths get the 520 ms morph automatically (`ClavisStore.MORPH_VIEWS`).
5. Root test tag `view-<name>`; in headless tests prefer the 1280×800 (rail) size for clicking —
   at 412 dp the floating dock overlaps whatever `performScrollTo` brings to the bottom edge.

## The feature screens

- **Paths** (`paths`): `engine.computeBranches` once per store revision (`remember(rev)`), the
  tree per template-spec §6.5, `As a map` → `PathsMap` (root, branches on r 130, ghosts on r 200,
  kind colours, tap → card, drag). `S.pathView` persists the mode.
- **Story** (`story`): `engine.storyEpisodes / storyPages / pageScenes`; `SceneBox(full = true,
  numbers = true, noHalo = true, tapCurrent = true)`; balloon tap = speak + 1.6 s row light.
  `S.story` persists lane/episode/page; `store.openStoryAt(l, sceneId)` jumps to a word's page.
- **Languages** (`languages`): every registry language present in the corpus except `en`,
  grouped by family; the family-constraint sheet is in-layout (not a dialog) so it is testable;
  coverage lines are computed from `store.manifests` (`coverageOf`), never constants.
- **Letters** (`letters`): `LetterData` holds the verbatim tables; opening a tab sets
  `coach["letters:<iso>"]` (D-21). Languages with `needsScriptPrimer` but no table get the
  no-primer card.
- **Cosmos** (`cosmos`): `FieldModel.build(...)` is pure and deterministic (positions from
  `hash01`, familiarity from the cards); `FieldCanvas` batches points by (colour, alpha, size)
  into `drawPoints`; `FieldController` holds camera/selection/focus/history. `ExploreController`
  subsamples 1,250 words and runs `ExploreModel.layout` off the main thread. The word card's
  sound goes through `AudioRow` (below). `S.cosmos.mode` = `depth` | `explore`; `webFor` is the
  Explore → Field handoff.
- **Media** (`media`): `MediaParse` (SRT/LRC/TXT, tokenisers), `store.addMedia/deleteMedia/
  openMedia`, reading mode with lexicon knownness via `engine.isKnown`, `store.logTingle`;
  `store.openSentences(l)` opens the Tatoeba browser (`rankSentences`: ≥ 90 % known first).

## Recordings and voices (`ui/components/AudioRow.kt`)

One labelled control per word/sentence: a permissive recording (Commons / Tatoeba) when the pack
has one and `store.clipOffered(url)` (format decodable here, download possible or already cached)
— `Download recording` → `Play`, label `recording · <source> · <licence> · <author>` — else the
synthetic voice (`SpeakButton`, label `synthetic voice`) when `speaker.canSpeak`, else nothing.
`store.playClip(url)` downloads through `services.downloader` into `<cache>/audio/` on the tap
(D-13) and plays through `services.audio`. Desktop decodes MP3/OGG with mp3spi/vorbisspi
(explicit instantiation, THIRD_PARTY_NOTICES "Desktop audio"); Android uses `MediaPlayer`.
`PlatformServices.audioFormats / canDownload / dustCap` describe the platform.

## Store API (all synchronous, UI thread)

Boot: `ClavisStore(services, readResource = { Res.readBytes(it) }, clock, random, dayKey)` then
`boot()` (suspend; `ClavisApp` does it, tests pre-boot with a shared `Loaded` bundle).
Read: `store.state` (the `ClavisState`), `store.engine` (every domain query — `activeLanes`,
`dueCards`, `corpusCoverage`, `computeBranches`, `laneOverview`, `youMetrics`, `storyEpisodes`,
`storyPages`, `pageScenes` …), `store.corpus`, `store.registry`, `store.manifests`,
`store.ui`/`store.current` (`UiState`: session, enc, test, colophon overlay, toast, confirm, theme).
Intents: `setView`, `openColophon(ref?)`/`closeColophon`, `obNext/obBack/setLaneMode/finishOnboarding/replayIntro`,
`startTest/answerTest/cancelTest`, `startSession(origin)/startGameSession/next/stopSession/endSession(view?)`,
`toggleFullPage/showGloss/showHint/showEnglish/flipToken/answerCloze/routeHelped/finishRead/answerEcho/answerLangId`,
`takeBranch(branch)/undoBranch`, `updateSettings { … }`, `setTheme`, `setPin`, `setLanes(map)`, `exportJson/importJson/resetAll`,
`speak(l, text)`/`canSpeak(l)`, `toast(text)`, `confirm(Confirm(...))`; feature intents `setPathView`, `setLetterTab(l, byUser)`,
`setKanaSet`, `setStoryLane/Episode/Page`, `openStoryAt`, `setCosmosMode/WebFor/Lens/Sets`, `addMedia/deleteMedia/openMedia`,
`openSentences`, `logTingle`, `playClip`; suspend loaders `pronunciations(l)`, `sentences(l)` (cached per run).
Every intent that changes the document saves it (`store.save()`); a plain `state` mutation from a new
screen must be followed by `store.save()` and something that bumps `rev` (use an existing intent or
add one to the store — keep mutations inside the store).

## Lettering (`ui/components/SceneBox.kt`)

```kotlin
SceneBox(
    corpus = store.corpus, images = store.pageImages, scene = sc, lang = l,
    full = false | true,          // cropped around the balloon | the whole page
    mask = null | "" | "gapped…",  // §5.1 contract; compute ONCE per render and pass to both modes
    siblings = store.pageOf(sc, l), // whole-page: every balloon of the page in l (pageOf)
    onTapBalloon = { s2 -> … },     // gloss + speak
    numbers = true, noHalo = true,  // Story mode: numbered balloons, no halo
    mini = true,                    // Paths mini scene (marker only, no text)
    rtl = store.isRtl(l),
)
```
Cropped-crop maths, plate geometry and both font-fit rules are pure functions in `LetteringMath`
(`crop`, `pageFontPx`, `cropFontPx`, `fit`, `fitText`). Test tags: `scene-cropped`/`scene-full`,
`balloon-<sceneId>` on lettered text, `marker-<sceneId>` where a marker stands in.
`CaptionTokens(...)` is the accessible carrier of a line (tokens are buttons; `TokenMark` per token).

## Pack access

`store.loader` is the `DefaultContentPackLoader` over `Res.readBytes("files/packs/…")`:
`pageBytes(pageKey)` (WebP bytes; `store.pageImages` caches the decoded bitmaps, LRU 12),
`sentences(l)`, `pronunciations(l)`, `manifests()`. `store.readText("packs/ATTRIBUTION.md")`
or `store.readText("licenses/OFL-Hind.txt")` for bundled texts. Registry lookups: `store.info(l)`,
`store.langName(l)`, `store.family(l)`, `store.isRtl(l)`, `store.sceneCount(l)`; lane colours via
`laneColour(store, l)` / `poolColour(store, l)` (`ui.screens.learn`).

## Tests

`./gradlew :composeApp:desktopTest` (headless). `TestSupport.kt` provides `TestBoot.store()` /
`onboarded()` (temp data dir, fixed clock, seeded Random, motion off), `seedDueCloze()`,
`stepSession()`. `ScreenshotTest` writes `<root>/build/ui-shots/*.png` at 412×915 and 1280×800 in
light and dark.
