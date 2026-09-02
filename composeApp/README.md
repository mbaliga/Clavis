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
| `ui.screens.*` | one screen per file: `onboarding`, `test`, `learn` (+ `Pool`), `session` (`SessionScreen` chrome/summary, `Encounters` bodies), `settings`, `you` (+ `Hypotheses`), `colophon`, `placeholders`. |
| `ui.colophon` | `References.kt` (the 40 refs, ids frozen), `Claims.kt` (15 claims, glossary, credits copy). |

## Adding a screen / route (ui-features)

1. Write `ui/screens/<name>/<Name>Screen.kt` as `@Composable fun NameScreen(store: ClavisStore, ui: UiState)`.
   Start the body with `@Suppress("UNUSED_VARIABLE") val rev = LocalRev.current` — the learner
   document and the engine's `Session`/`BaselineTest` are mutated in place, not snapshot state,
   so this read is what makes the screen recompose after every store commit.
2. Use `ScreenTopBar(store, VIEW_NAMES["<view>"]!!)` at the top and end scrollable content with
   `Spacer(Modifier.height(130.dp))` (room for the floating dock).
3. Replace the placeholder in `ClavisApp.ViewContent()` (`"cosmos" -> CosmosPlaceholder(store)` etc.).
   The view ids already exist in `ClavisStore.VALID_VIEWS`, `VIEW_NAMES` and, for dock items, `NAV`.
4. Navigate with `store.setView("<view>")`; the view is persisted as `state.view` (D-10).
   Learn ↔ Cosmos ↔ Paths get the 520 ms morph automatically (`ClavisStore.MORPH_VIEWS`).

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
`takeBranch(branch)/undoBranch`, `updateSettings { … }`, `setTheme`, `setPin`, `exportJson/importJson/resetAll`,
`speak(l, text)`/`canSpeak(l)`, `toast(text)`, `confirm(Confirm(...))`.
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
