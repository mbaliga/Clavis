# Clavis — multi-platform porting plan

> Part of the constellation-wide porting program (`Personal-Tracker/PORTING_PROGRAM.md`, 2026-10-06).
> Status: **PLAN — nothing in this document has been built.** Every claim about a target platform is
> labelled with its evidence class (§0). This file is owned by the lead planning session; a platform
> track updates only its own §4 row and appends progress to `STATUS.md`, this repo's state file.
> `DECISIONS.md` wins where documents disagree (`00-README.md`): this plan proposes, and **D-31 is
> proposed, not ruled** — D-1 and D-16 stand as written until the owner says otherwise.

## 0. Evidence labels (never dropped)

asom's set, unchanged: `LAB` · `CI (hosted VM) evidence` · `EMULATOR EVIDENCE` · `SIMULATOR` ·
`CI-APPROX — NOT DEVICE EVIDENCE` · `SIMULATED — NOT DEVICE EVIDENCE` · `VIRTUALIZED — NOT DEVICE EVIDENCE` ·
`SYNTHETIC` · `CI-ONLY / NOT RUN` · `NEEDS-DEVICE-VALIDATION` (NDV) · `NEEDS-OWNER-VALIDATION` (NOV).
This program's additions: `PLAN` · `NOT-APPLICABLE (<reason>)` · `CONTAINER-BUILD-ONLY` · `BROWSER-HEADLESS`
(program §2). `CI (hosted VM)` below is shorthand for `CI (hosted VM) evidence`.

Here: a simulator run on a hosted `macos-15` runner is `CI (hosted VM)` + `SIMULATOR`; a click or Flatpak
built on a hosted runner is `CI (hosted VM)`; the `ubuntu-24.04-arm` runner has a real arm64 userland but is
still `CI (hosted VM)`; anything run on an iPad, a phone, the Steam Deck, a Mac or a Windows machine is NDV
or NOV. The session that wrote this plan built nothing and ran nothing on any target. The evidence the repo
already records for its own builds lives in `STATUS.md` §5 and is not re-labelled here.

## 1. What this repo is, in porting terms

- **Product.** Clavis is an open-source, offline-first language-learning app: nine languages on by default
  (17 shipped), bridged through English, taught from annotated Pepper&Carrot comics, etymology, FSRS-5 and an
  adaptive curriculum (`README.md`, `docs/01-premise.md`). No account, no server, no telemetry.
- **State.** Release candidate 0.1.0-rc.1, no stable tag, head `135b334` (2026-09-16) (`README.md` Status;
  `docs/STATUS.md` §4). Targets today (D-1, D-16; `docs/RELEASE.md` §0): Android (APK + AAB), and one JVM
  desktop codebase packaged as Linux x64 `.deb` + uber-jar, macOS arm64 `.dmg`, Windows x64 `.msi`. A frozen
  single-file web build is attached to each release as a reference artefact; D-1 says it is **not a product**.
- **Stack.** Kotlin 2.1.20 Multiplatform; Compose Multiplatform 1.8.2 (Material 3, Compose Resources,
  org.jetbrains navigation 2.9.0-beta03 / lifecycle 2.9.1); Gradle 8.14.3, AGP 8.9.1, JDK 17 via foojay (D-2);
  kotlinx-serialization / coroutines / datetime; desktop-only LGPL decoders (mp3spi, vorbisspi). Python 3.11
  corpus pipeline (build time only). A vanilla-JS reference prototype (`reference-prototype/template.html`,
  G6 vendored). **No native code** (no JNI, C++ or Rust), no SQL database, and no dependency on Hyle or
  Shared-Libraries-asoc (no `includeBuild`, no `.gitmodules`; D-10, D-11).
- **Size (measured 2026-10-06 on this checkout).** 114 Kotlin files, 16,691 lines (12,565 outside `*Test`
  directories); 22 Python files; 30 Kotlin + 4 pytest test files; 187 `@Test` in `shared/src/commonTest`, 61 in
  `composeApp/src/desktopTest`; 604 tracked files. Commands:
  `find shared composeApp -name '*.kt' -not -path '*/build/*' | wc -l`;
  `find … -print0 | xargs -0 cat | wc -l` (add `-not -path '*Test/*'` for the non-test figure);
  `grep -rc '@Test' shared/src/commonTest composeApp/src/desktopTest`; `git ls-files | wc -l`.

## 2. Portable core vs platform-bound layers

| Module / dir | Role | Portability | Approx LOC | Notes |
|---|---|---|---|---|
| `shared/src/commonMain` | Domain: corpus model and gzip pack decoder, language registry, FSRS-5, `ClavisEngine`, `JsonStateStore`, `Services.kt` interfaces, `Copy.kt` | KMP common | 2,830 | 4 `expect`s (`platformName`, `sha256Hex`, `gunzip`, `object FileIo`); no `java.*`/`android.*` import (grep-checked) |
| `shared/src/commonTest` | 187 domain tests | KMP common | 2,391 | Run today on desktop + Android unit variants; the first thing an iOS target must run |
| `shared/src/{android,desktop}Main` | Actuals for the 4 `expect`s | pure JVM | 56 + 55 | Near-identical (`MessageDigest`, `GZIPInputStream`, `java.nio` atomic move) |
| `composeApp/src/commonMain` | Every screen (13 `ui/screens` directories), `ClavisStore`, theme, Cosmos/Paths canvases, `Platform.kt` | KMP common | 8,749 | 4 `expect`s (`scriptFallbackFonts`, `platformReducedMotion`, `ioDispatcher`, `runBlockingCompat`); `PlatformServices` with `No*` defaults |
| `composeApp/src/androidMain` | Entry, TTS, `MediaPlayer`, SAF picker, share, `HttpDownloader` | Android-bound | 363 | 10 `android.*` import lines (grep; one more in `shared/src/androidMain`); manifest is `INTERNET` only |
| `composeApp/src/desktopMain` | `main.kt`, `DesktopServices` (XDG/Library/APPDATA dirs, Piper → `say`/`spd-say`/`espeak-ng`/PowerShell, `javax.sound`, AWT dialogs), Noto fallbacks | pure JVM | 512 | 22 MB of desktop resources; this layer is why Linux/macOS/Windows already build; unusable on iOS or in a Click |
| `composeApp/src/desktopTest` | Headless Compose UI suite incl. `ClozeLeakTest`, RTL/2× screenshots, semantics audit | pure JVM | 1,735 | Desktop-only; does not run on iOS (D-29) |
| `composeResources/files/packs` | Generated content packs (D-22) | data | n/a | ~22 MB (15 MB comics, 305 WebP pages); never hand-edited; every target ships it unchanged |
| `pipeline/` | Corpus pipeline, `pack_web.py` | Python | 5,279 | Build time only; nothing to port |
| `reference-prototype/` | Frozen web template + generators | web | 2,368 (template) | Frozen at v0.10 / ten languages (D-1); the only artefact a Click could host |
| `gradle/`, `.github/workflows/` | Versioning, signing, `ci.yml`, `release.yml` | build | 783 (workflows) | D-26: assets go straight into a draft Release |

Platform-bound APIs that matter:

| API | Where | Porting impact |
|---|---|---|
| Targets are `androidTarget()` + `jvm("desktop")` only | `shared/build.gradle.kts`, `composeApp/build.gradle.kts` | iOS means two Native targets in both modules plus an Xcode shell; no Compose target exists for Native Linux, so a Click cannot be reached by adding a target |
| `Speaker`, `AudioPlayer`, `AppDirs`, `FileImporter`, `Downloader`, `ShareSheet` | `core/Services.kt`, `platform/Platform.kt` | Six small services per platform; the `No*` defaults let a port boot first; `audioFormats` and `canDownload` hide Play/Download honestly |
| `TextToSpeech`, `MediaPlayer`, SAF, `Intent.ACTION_SEND`, animator-scale reduced motion | `androidMain/…/AndroidServices.kt` | Android only; iOS analogues are `AVSpeechSynthesizer`, `AVAudioPlayer`, `UIDocumentPickerViewController`, `UIActivityViewController`, `UIAccessibility.isReduceMotionEnabled` |
| AWT `FileDialog`, clipboard, `javax.sound` + mp3spi/vorbisspi, `ProcessBuilder` TTS | `desktopMain/…/DesktopServices.kt` | The desktop layer; needs X11/XWayland on Linux; LGPL notices are desktop-only |
| `HttpURLConnection` downloader, byte-identical in both JVM source sets | `androidMain` and `desktopMain` `HttpDownloader.kt` | JVM only; iOS needs its own implementation preserving D-13's guards |
| `decodeToImageBitmap` of 305 WebP pages behind a 12-page LRU (≈28 MB decoded) | `ui/components/PageImages.kt` | Skia decodes it on iOS in principle; unverified. Failure is swallowed to `null`, and `USABILITY.md` already lists a page-decode failure state as open |
| `jpackage` formats `Dmg`/`Msi`/`Deb`, fixed MSI `upgradeUuid`, `bundleID` | `composeApp/build.gradle.kts` | Desktop packaging complete; `jpackage` cannot cross-build, so other architectures need their own runners |

## 3. Binding rules this port must not break

- **No telemetry, no network by default; one user-tapped HTTPS download** (https-only including every redirect,
  at most 5 hops, 25 MB cap, `.part` then rename). D-13; `PRIVACY.md`; `SECURITY.md` ("a security
  requirement, not a preference"); `CONTRIBUTING.md` ("do not weaken the no-permission manifest, the offline
  default or the atomic state write"). Any new entitlement, policy group or sandbox permission is a visible
  choice that must be explained in `PRIVACY.md`.
- **D-1:** the web build is the executable spec, frozen at v0.10 / ten languages, not shipped as a product;
  new features land natively only. Shipping it anywhere needs a new numbered decision (proposed D-31).
- **D-2:** Kotlin 2.1.20, CMP 1.8.2, AGP 8.9.1, Gradle 8.14.3, JDK 17 are pinned in lockstep with the rest of
  the constellation. A move is a constellation decision (program OQ-17); the recipe is `docs/build-platform.md` §1.
- **D-3:** `:shared` holds everything that is not rendering; platform services are interfaces in `Services.kt`
  implemented per target in `:composeApp`. The UI depends only on those interfaces.
- **D-8:** TTS chain on every platform is Piper process → the platform's own TTS → no Speak button; `canSpeak`
  gates the button; no pronunciation-scoring claims. GPL `espeak-ng` is only ever an external process.
- **D-9:** Hind / Rozha One / IBM Plex Mono everywhere; Noto script fallbacks are bundled only where the OS
  does not guarantee the scripts. Each new OS decides, and `FontFallbackTest` must stay honest.
- **D-12:** one JSON document per profile, temp file → fsync → atomic rename, schema version + migration; an
  unreadable document is moved aside, never overwritten; no named companion objects on `@Serializable` classes.
- **D-16 / D-26:** one GitHub Release per tag, assets uploaded straight into a draft Release (no Actions
  artifact storage; the account's quota is exhausted); installers build on their own OS runners; the release
  job's expected-asset check (11, or 12 with the web build) must be extended for any new asset.
- **D-18 and the copy rules** (`CONTRIBUTING.md`): the banned vocabulary, "scenes readable at the 95%
  threshold", citations `[n]` with frozen ids 1–40, and the N=1 / "not a validated pedagogy" statement in
  onboarding and every store listing. Any new listing (App Store, OpenStore, Flathub) carries it.
- **D-22 / licensing** (`LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`): code Apache-2.0; packs CC BY-SA 4.0
  and never hand-edited; fonts OFL 1.1; LGPL decoders desktop-only; the notices list exactly what each
  distribution ships; DCO sign-off on every commit; **no licence banners** in source files, so the program's
  optional SPDX-header convention (F5) does not apply here.
- **Environment honesty** (`STATUS.md` §4, `RELEASE.md` §9, `CHANGELOG.md`): no device, emulator or Mac in the
  build environment; device behaviour is owner-verified only. D-29 automates the repeatable part of the Android
  device pass; TalkBack-class checks stay manual, and so will VoiceOver-class checks.
- **Colour is never the only signal** (`USABILITY.md`: grades use glyphs and dashed underlines, lane dots carry
  names). Clavis is not a Hyle consumer (D-11), so the program's I-3 binds it by this repo's own practice; no
  screen added for a port introduces a colour-only meaning.
- **Fixed identifiers:** `io.github.mbaliga.clavis` (applicationId and macOS `bundleID`), MSI `upgradeUuid`
  `1b0a9429-3b8e-45a2-9f95-a27591135e75` "fixed for the life of the product", data directory `Clavis` on every
  desktop OS. A new identifier (iOS bundle id, Flatpak id, click name) gets a NAMES.md row first (program R11).
- **D-10 / D-11:** no Android-only constellation library enters `commonMain`; crash-recovery wiring is
  Android-only and still provisional.
- **Owner-gated** (`STATUS.md` §4): tag push, keystore, Apple and Windows signing, API-36 device pass, Play
  Console, and the "Clavis" name clearance (a registered mark, *Clavis Sinica*, was found) before any listing.
- **Program rules in force:** R1 disjoint directories and source sets; R2 the existing gate stays green; R3 new
  workflow files only, `ci.yml` untouched; R4 pure core first; R6 nothing released or stored on PRs, every
  artefact `UNSIGNED — not for release`; R7 real output only; R11 no identifier without a row; R12 reframes
  are labelled (a Click here is "the reference build", never "Clavis on Ubuntu Touch"). I-1: TestFlight shares
  tester crash reports with the developer automatically, an OS-level egress `PRIVACY.md` would have to disclose
  and the owner would have to accept (program OQ-2).

## 4. Target matrix (owner's order)

| Target | Feasibility | Approach | Blockers | Effort (eng-weeks, estimate) | Evidence today |
|---|---|---|---|---|---|
| Ubuntu Touch | reframe | Repackage the unmodified frozen ten-language `clavis-<v>-web.html` (~21 MB, vanilla JS, `localStorage`, canvas 2D, Web Speech, no network) as a webapp-container (Morph) click labelled as the reference build. No JVM/AWT path exists in a click and CMP has no Native Linux target. If D-31 is declined the answer is "no Ubuntu Touch port" | Proposed D-31 (D-1 forbids shipping it); Morph's engine, Web Speech and storage behaviour unverified; no UT device on record (OQ-1); name clearance (OQ-4) | 3, after D-31; excludes device time | PLAN |
| Linux desktop | done | Builds exist (never tagged): `.deb` amd64 + linux-x64 uber-jar from `release.yml`. Remaining: arm64 `.deb` + jar on `ubuntu-24.04-arm`, a per-arch tarball with `install.sh`, a Flatpak consuming the app-image. JDK 17 stays (D-2) | Never tagged; arm64 minutes on a private repo (OQ-20); Flathub needs a public source and an app-id (OQ-25, OQ-4); TTS under the sandbox unknown; Deck and RedMagic device-only | 1 (estimate; excludes Flathub review and device passes) | PLAN |
| iOS / iPadOS | straight | Add `iosArm64` + `iosSimulatorArm64` to both modules; 8 `actual`s; `IosServices`; SwiftUI shell `iosApp/`; a pinned-`macos-15` simulator lane. Clavis pilots the constellation's CMP-iOS recipe | Whether Kotlin 2.1.20 / CMP 1.8.2 suffices is unverified; Apple Developer Program and delivery route (OQ-2, OQ-3); name clearance (OQ-4); 3.2% of recordings are Ogg and would show no Play control; no Mac or iOS device here | 5 (range 4–6): code 2–3, CI/release wiring 1, owner device iteration 1–2; excludes a toolchain bump | PLAN |
| macOS | done | Build exists (never tagged): arm64 `.dmg` + uber-jar via `jpackage` on `macos-15`; notarisation wiring exists and runs when secrets exist. Remaining: first signed, notarised build; entitlement verification; Intel optional | Developer ID secrets (OQ-3); no Mac on record, so device gates are NOV (OQ-5); first real tag may surface issues | 0.5; excludes Intel and the Mac App Store | PLAN |
| Windows | done | Build exists (never tagged): x64 `.msi` (WiX 3, fixed `upgradeUuid`) + uber-jar on `windows-2025`, never signed. Remaining: a signing route, first real tag; arm64 app-image and winget optional | Signing route (OQ-3); a private repo blocks winget and SignPath Foundation (OQ-20); the Dell's fate (OQ-5) | 0.5; excludes arm64, MSIX, winget | PLAN |

Notes. **"done"** (program §5 vocabulary) means the build exists in `release.yml` and `composeApp/build.gradle.kts`, not that it
is proven: `STATUS.md` §1 and `CHANGELOG.md` say the macOS and Windows jobs were first exercised by the
2026-09-16 dry run, while `STATUS.md` §5 says the dry run is "not yet run". This plan claims neither and treats
macOS and Windows packaging as unproven until a dry-run log is recorded (R7). **Ubuntu Touch alternatives not
proposed:** a Kotlin/Wasm head (the platform brief suggests one, but `ClavisStore.save`/`flushSaves` use
`runBlockingCompat` and `FileIo`, which have no Wasm equivalent, so it is a redesign, not estimated, and
WasmGC support in the Click engine is unverified) and Compose-on-JRE under XMir (unconfined, manual review,
unproven). Waydroid running the unmodified APK is an owner-device option, never a port (program OQ-21);
no Google Play services dependency is declared in the build files. All efforts are engineer-week estimates.

## 5. Tier and sequencing

**Tier A — flagship** (matches program §5). Clavis is the most port-ready repo in the constellation: three of
five targets already build, the platform layer is 8 `expect`s and six small interfaces (363 Android and 512
desktop lines, no `android.*` in common code), and the one real addition, iOS, would establish the constellation's
CMP-iOS recipe (targets, actuals pattern, Xcode shell, macOS CI lane, draft-Release transport) for Typewright,
nooz, csapp and hnm. The owner's order does not fit this repo: Ubuntu Touch is a reframe gated on D-31, so the
cheap engineering order is iOS and desktop hardening first. Program OQ-28 asks whether that matches product
priority.

Gate before any wave (program §5): none for the JVM targets; Clavis:D-31 for Ubuntu Touch; OQ-2 for iOS devices;
the "Clavis Sinica" name clearance before any store listing (OQ-4). Every wave also requires the existing gate
(R2) to be green: `./gradlew :shared:allTests :composeApp:desktopTest :composeApp:lintDebug
:composeApp:assembleRelease` and `python -m pytest pipeline/tests -q`.

| Wave (program §7) | Clavis item | Build-entry (repo-local) | Device-entry |
|---|---|---|---|
| P-0 pre-wave proof | iOS simulator build, no F-dependency, no Apple Program needed | The gate green on the base branch; runs on the current pin (Option B of OQ-17); if it fails on the pin, `BLOCKED(toolchain)` and OQ-17 is ruled before more work | None (simulator) |
| P-UT a | Reference-build click, only after D-31, the Click-engine spike (§6.2) and a device or an explicit CI-only waiver | D-31 ruled Option A | A UT 24.04-2.x device (OQ-1) |
| P-LX | arm64 + Flatpak, after Typewright's Linux packaging pilot | A recorded `release.yml` dry run (§6.3); on this private repo lanes are manual-dispatch until OQ-20 | `DEVICE_CHECKLIST_LINUX.md` on the Deck; RedMagic under Termux:X11; the Dell only if OQ-5 says Linux |
| P-iOS | First repo through the recipe, iPadOS first | Simulator lane green; OQ-2 and OQ-3 answered for anything beyond the simulator | Apple Developer Program + the OQ-2 route; the iPad Pro M4; iPhone items NDV |
| P-mac | Signed, notarised DMG | P-LX binaries; OQ-3 secrets | No Mac on record: NOV until OQ-5 changes |
| P-win | Signed MSI; winget only if public | P-LX binaries; a signing route (OQ-3) or accepted unsigned | The Dell while it is Windows (OQ-5); afterwards `CI (hosted VM)` only |

The program's P-UT a row groups Clavis with the Kotlin/Wasm clicks. This repo's §5 cell, and its profile, describe the
click as the **frozen hand-written web build**, which is not Kotlin/Wasm and does not need WasmGC; this plan
follows the §5 cell, and its spike asks whether the Click engine runs one 21 MB file, not whether it supports WasmGC.

## 6. Work breakdown

### 6.0 Rules for every step

New files go under `iosApp/`, `shared/src/iosMain`, `composeApp/src/iosMain`, `packaging/<os>/`, `ubuntu-touch/`
and new workflow files (`ios.yml`, `desktop-linux.yml`, `desktop-macos.yml`, `desktop-windows.yml`,
`ubuntu-touch.yml`), each SHA-pinned (R3; `RELEASE.md` §8 records that the repo's own workflows use major tags
and calls full-SHA pinning stricter than a single-maintainer repo needs, so this is flagged in §8). No port
edits `ci.yml`, `androidMain`, `desktopMain`, `reference-prototype/` or any generated pack. The existing-file
edits this plan needs are named where they occur: the two `kotlin { }` target blocks (iOS), the `macOS { }`
block if an entitlements file is needed, and `release.yml`'s asset list (a separate, gated PR). Every PR pastes
the gate's real output (R7) and names the evidence label of each claim. **Windows-checkout path lint, run today:**
`git ls-files | grep -E '[:<>|?*"]|(^|/)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\.|/|$)'` matches none of the 604
tracked files, so the R3 precondition holds; it becomes a step of `desktop-windows.yml` if that file is created.

### 6.1 iOS / iPadOS (first; pilots the recipe)

1. **I0, optional hygiene, own PR (§8 Q6, Q7).** Move the byte-identical JVM `HttpDownloader` into a `jvm`
   intermediate source set; drop `okio` and `multiplatform-settings`, declared in `:shared` and never imported,
   so a new target does not carry, or list in the notices, dependencies nothing uses. This edits Android source sets, so it goes through
   the repo's normal PR with the full gate. iOS does not depend on it: its downloader is a different
   implementation either way. Done when the gate output equals the baseline.
2. **I1, `:shared` first (R4).** Add `iosArm64()` and `iosSimulatorArm64()` to `shared/build.gradle.kts` (no
   `iosX64`). Write `shared/src/iosMain`: `platformName` (UIKit), `sha256Hex` (CommonCrypto, or a pure-Kotlin
   digest), `gunzip` (`platform.zlib` or a small pure-Kotlin inflater), `FileIo` (POSIX open/write/**fsync**/
   `rename`, which is atomic; D-12). Done when `:shared:iosSimulatorArm64Test` runs the 187 domain tests green
   on `macos-15` (`CI (hosted VM)` + `SIMULATOR`). Unknowns this step answers: whether `\p{L}` regexes behave on
   Native, and whether some tests assume JVM fixture paths. The platform brief records a Kotlin 2.2.20+
   recommendation for iOS; if the current pin fails, stop at `BLOCKED(toolchain)` (R7) for OQ-17.
3. **I2, UI core.** Add targets to `composeApp/build.gradle.kts` with a static `ComposeApp` framework. Write
   `composeApp/src/iosMain`: the 4 `expect`s (`scriptFallbackFonts` = empty list on the working assumption that
   iOS supplies CJK, Arabic, Devanagari and Hebrew, **to be shown by simulator screenshots of each script before
   it is recorded as a D-9 decision**; `platformReducedMotion` over `UIAccessibility`; `ioDispatcher` =
   `Dispatchers.IO.limitedParallelism(1)`; `runBlockingCompat` = `runBlocking`, never called while waiting on
   the main queue), a `MainViewController` hosting `ClavisApp`, and `IosServices`. Boot on the `No*` defaults,
   then enable one service per commit:
   - **AppDirs:** Application Support and Caches; mark the state document, its `.tmp` and `corrupt/` as excluded
     from backup to mirror Android's cloud-backup exclusion (`PRIVACY.md` "Android backup"). Whether that also
     withholds them from device-to-device migration is unknown (§8 Q12).
   - **Speaker:** `AVSpeechSynthesizer`, voice chosen exact locale → language → none; cancel before speak; map the
     0.85 rate onto `AVSpeechUtterance`'s scale by ear (NDV); no Piper on iOS; voice availability per language is
     device-specific.
   - **AudioPlayer:** `AVAudioPlayer` with `audioFormats = {wav, mp3}`. **Measured:** of 12,614 pronunciation
     recordings in the committed packs, 406 (3.2%) are Ogg Vorbis and would show no Play control: Latin 142 of
     143, Italian 207 of 767, Spanish 53 of 560, Japanese 2, Korean 2; all 5,647 Tatoeba clips are mp3. Method:
     count `audioUrl` extensions in `packs/pronunciations/*.json.gz` and `packs/sentences/*.json.gz` with
     `ClavisStore.clipFormat`'s rule. That `AVAudioPlayer` cannot decode Ogg Vorbis is the profile's statement;
     confirm in the simulator. A decoder would be new licence-reviewed code (§8 Q11).
   - **Downloader:** `NSURLSession` with D-13 enforced in the delegate (reject any non-https redirect, count
     hops to 5, cap at 25 MB by declared length and running count, write `.part`, rename); App Transport
     Security left at its defaults with no exceptions. Factor the pure policy checks into `:shared` with
     `commonTest`s only if that stays a no-behaviour-change refactor; the session wiring is NDV.
   - **FileImporter:** `UIDocumentPickerViewController` over `ImportLimits` (2 MB bounded read, mostly-text check).
   - **ShareSheet:** `UIActivityViewController`; on iPad it needs a popover anchor, to be checked in the iPad
     simulator.
   - **Lifecycle:** call `flushSaves()` when the scene goes inactive or to background (the analogue of Android's
     `onStop`), inside the ~30 s grace the program records for iOS. Done when the app boots in the simulator on the
     real packs, every `No*` fallback hides its control, and nothing outside D-3's interfaces is touched.
4. **I3, shell `iosApp/`.** A SwiftUI `@main` app hosting the controller, an XcodeGen `project.yml` (Clavis writes the
   first one; F10 will generalise it), `Info.plist` with no usage-description keys and no entitlements (the iOS
   form of the no-permission manifest), `PrivacyInfo.xcprivacy` declaring no tracking and no collected data plus
   whichever required-reason APIs the build actually uses (unknown until the lint runs). The largest raster
   icon in the repo is 512×512 RGBA (`desktopMain/resources/icons/clavis.png`); an App Store icon needs 1024×1024
   without alpha, so the owner supplies the source art. Bundle id: not written until its NAMES.md row exists (R11).
   `iosApp/README.md` documents the recipe (actuals pattern, shell, lane) so other repos can lift it.
5. **I4, CI `.github/workflows/ios.yml`.** Pinned `macos-15` (the repo pins runner images; the program's
   `macos-latest` does not override that). Jobs: `:shared:iosSimulatorArm64Test`; link the simulator framework;
   `xcodebuild … -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build`; boot a simulator, launch, assert the process
   stays up. `workflow_dispatch` only until OQ-20, then PRs with a path filter. No artefact uploads (R6). Memory
   flags follow `RELEASE.md` §8 (the macOS runner has 7 GB); Native link memory is unmeasured. An iOS dependency
   graph for the licence review joins the existing graphs; LGPL decoders and Noto fonts are desktop-only and stay
   out of `THIRD_PARTY_NOTICES.md`'s iOS list.
6. **I5, release wiring (gated: D-31, OQ-2, OQ-3, OQ-4).** A separate PR edits `release.yml` to add an `ios` job and
   one expected asset; signing and TestFlight upload exist only as disabled templates (R6). Without a paid
   Program the only evidence is simulator CI, and an unsigned archive cannot be installed on a device.
7. **I6, owner device pass.** `docs/DEVICE_CHECKLIST_IOS.md` from F11's template when the wave opens; iPad Pro M4
   first. Items: VoiceOver including the cloze case (`ClozeLeakTest` is desktop-only, and VoiceOver is expected to read
   the same Compose semantics tree, to be confirmed), Dynamic Type, Arabic RTL, voices per language, Reduce Motion, hardware-keyboard map, Split
   View across the 600 dp dock/rail breakpoint, memory under the 12-page cache, first-launch time over the 22 MB
   packs. All NDV or NOV.

### 6.2 Ubuntu Touch (only if D-31 Option A)

1. **UT0.** The owner rules D-31. Option B ends this track: record "no Ubuntu Touch port" in D-31 and `STATUS.md` §4.
2. **UT1, spike (Clavis-local; not the program's S-UT1, which concerns JVM cores).** On a 24.04-2.x device (OQ-1): does
   the webapp-container load one ~21 MB local file; does `localStorage` survive a restart and Lomiri's
   suspend of the app; does `speechSynthesis` expose any voice (D-8's no-button fallback covers absence but must be seen);
   canvas 2D, `ResizeObserver`, `prefers-reduced-motion`; can the build's JSON export reach a user-visible file.
   Cannot run without a device; CI can only build the click (`CI-APPROX — NOT DEVICE EVIDENCE`).
3. **UT2, scaffold `ubuntu-touch/`** (manifest, desktop file, AppArmor profile, icon, README, Clickable config) from F7's
   webapp template once it exists (OQ-24), else minimal and hand-written. Policy groups: `webview` and nothing
   else unless UT1 proves `networking` is required just to start the container; the web build makes no request.
   Click name from NAMES.md (R11). Assumption UA02 (a 24.04-1.x click installs on 2.x) is unverified.
4. **UT3, content.** The click wraps `python pipeline/pack_web.py` output (standard library, about 3 s per
   `RELEASE.md`), with **no edit to `reference-prototype/template.html`**. The "reference build, ten languages"
   label goes in the click title and description; an in-page banner would be a template change D-31 would need to allow.
5. **UT4, CI `.github/workflows/ubuntu-touch.yml`.** Clickable in the digest-pinned `clickable/ci-ut24.04-1.x-arm64` image,
   plus the policy/click-review check from F7. `CI (hosted VM)`. The click reaches a draft Release only through the
   later gated `release.yml` PR. Install and run are NDV.
6. **UT5, listing and docs.** The OpenStore listing is an owner action after name clearance (OQ-4). `PRIVACY.md` gains a row:
   state lives in the WebView's storage, clearing app data erases it, the in-app export is the only copy. The
   N=1 statement (D-18) appears in the listing.

### 6.3 Linux desktop

1. **LX0, evidence first (no code).** Run `release.yml` as a dry run (`workflow_dispatch` with a version) and record the
   `linux` job log and asset sizes in `STATUS.md`; the dry run also supplies the macOS and Windows logs.
2. **LX1, arm64, `.github/workflows/desktop-linux.yml`.** A job on `ubuntu-24.04-arm`: `:composeApp:desktopTest`, `packageDeb`,
   `packageUberJarForCurrentOS`, then the headless uber-jar smoke `ci.yml` already runs. Names `clavis_<v>_arm64.deb` and
   `clavis-<v>-linux-arm64.jar`. Tests on PRs, packages on main and tags (R6), manual dispatch until OQ-20.
   Unknown until a hosted run: whether `compose.desktop.currentOs` resolves Skiko's linux-arm64 natives and the
   foojay arm64 JDK 17 packages cleanly.
3. **LX2, tarball, `packaging/linux/`.** `createDistributable` per architecture, `clavis-<v>-linux-{x86_64,arm64}.tar.gz`, an
   `install.sh` placing files under `~/.local` (the no-root path, and the primary one on the RedMagic under
   Termux:X11, where GL is software and every app must start without Wayland, Vulkan or Bluetooth; the Cosmos
   canvases' cost there is unmeasured). Clavis builds no native code, so the glibc concern is the JDK launcher and
   Skiko's prebuilt libraries; whether the app-image starts on SteamOS is NDV.
4. **LX3, Flatpak, `packaging/linux/flatpak/` + a `flatpak-builder` job in `desktop-linux.yml` (main and tags).** The
   manifest consumes the LX2 tarball from a Release (Flathub builds offline). The image already bundles JDK 17
   (D-2), so the `openjdk` extension may be unnecessary; F10's template decides. Finish-args:
   `--socket=x11 --socket=fallback-x11 --device=dri --share=ipc` and `--share=network`, which is the single visible D-13
   path (no request until the user taps; Flatpak cannot grant network on demand, and `PRIVACY.md` must say so).
   App id not chosen here (R11, OQ-25, OQ-4). Open: whether Piper, `spd-say` or `espeak-ng` exist in the sandbox
   (bundling `espeak-ng` would distribute GPL code, against D-8's external-process rule, so that is a licence
   question, not an assumption), and AWT file dialogs under the sandbox. Under a Flatpak, XDG data lives beneath
   `~/.var/app/<id>/`, which is a new `PRIVACY.md` row.
5. **LX4, on ship.** README Downloads row, `RELEASE.md` §0, `PRIVACY.md` Linux row, and `docs/DEVICE_CHECKLIST_LINUX.md` (Deck
   Desktop Mode via Discover, Gaming Mode under gamescope, RedMagic, HiDPI on Plasma and GNOME). A Wayland path
   stays a flagged spike; AWT runs through XWayland. No AppImage or Snap is proposed.

### 6.4 macOS

1. **MA1, first signed build.** The owner supplies the Developer ID secrets (`RELEASE.md` §1.2; OQ-3). The `notarizeDmg`
   path exists; a real tag exercises it (`CI (hosted VM)`). Opening it under Gatekeeper on a Mac is NOV.
2. **MA2, entitlements.** Whether a hardened, notarised JVM app needs `allow-jit` or `allow-unsigned-executable-memory`
   is unknown until MA1; F10's verdict (asom S-M3) is the source. If a file is needed it lives in
   `packaging/macos/` and the `macOS { }` block gains a reference (a few lines in `composeApp/build.gradle.kts`).
3. **MA3, optional.** `desktop-macos.yml` with a `macos-15-intel` job, manual dispatch (`RELEASE.md` §8 anticipates it);
   Intel is best-effort until September 2027. The Mac App Store is not planned. "Designed for iPad" on
   Apple-silicon Macs arrives only with the iOS wave if the owner enables it; it is a different build and does not replace the DMG (NOV).

### 6.5 Windows

1. **WI1, signing.** Until OQ-3 is ruled the MSI stays unsigned with the documented SmartScreen steps (`RELEASE.md` §7).
   Routes: SignPath Foundation (public OSS only; the repo is private), OV certificate on an HSM, or the Microsoft
   Store's MSIX re-signing (needs an MSIX package `jpackage` does not produce; tooling unknown, not estimated).
   Azure Artifact Signing is unavailable to the owner as an individual in India (program §4.5).
2. **WI2, optional arm64.** `desktop-windows.yml` on `windows-11-arm`: tests and an app-image or zip only, because WiX is
   absent there (program §4.5); an arm64 MSI is unproven.
3. **WI3, winget.** Needs a publicly downloadable installer URL and an id with a NAMES.md row; blocked by repo visibility
   (OQ-20) and OQ-25. The `upgradeUuid` never changes.

## 7. Shared foundation this repo consumes or provides

| Item (program §6) | Clavis consumes | Clavis provides or notes |
|---|---|---|
| F1 hyle-kmp, F3 cell-shell, F4 asom-client, F8 native-engines | Nothing: no Hyle dependency (D-11), no cell-shell (D-10), no asom, no native engines | n/a |
| F2 crash-recovery KMP | A future consumer; D-11 calls the wiring Android-only and provisional | Only after PT:D-P closes and OQ-27 rules the toolkit; not in any estimate here |
| F5 kmp-conventions | The `android.*`-import ban, a licence allowlist and the version catalog at OQ-17's pin could fit; SPDX headers do not (no banners) | Clavis's `iosArm64()` conventions are the first input to F5; no `includeBuild` is added at P-0 |
| F6 platform-ports | No secrets, so no secure-storage need; overlaps in dirs, picker and share | Clavis keeps its own `Services.kt`; the iOS actuals are offered as F6's reference implementation, not assumed |
| F7 ubuntu-touch-shell | The webapp-container template, not the JVM bridge, so the S-UT1 verdict is irrelevant | Candidate first consumer of the shared Click/Morph template (OQ-24) |
| F9 CI matrix, F10 packaging templates | Optional: Clavis already has `jpackage` config and a `release.yml`; WiX and notarisation templates add little | The first working `ios-sim` lane (`ios.yml`) and XcodeGen shell, plus the Linux arm64 job, as pilots; donated by PR, never copy-vendored (I-6) |
| F11 evidence and device checklists | The five `DEVICE_CHECKLIST_*` templates | None |
| F12 web/brand kit | Not a roster web property | n/a |

Other repos need from Clavis: the CMP-iOS recipe (Typewright, nooz, csapp, hnm), the D-13 downloader guards as a
reusable pattern, and the Linux arm64 job. Clavis needs from the program: OQ-17's pin, the Click template (only for Option A), and a CI budget (OQ-20).

## 8. Open questions for the owner

1. **Ubuntu Touch and D-31 (program OQ-1, OQ-21).** Relax D-1 so the frozen ten-language reference build may ship as a
   labelled Morph click (Option A), or record "no Ubuntu Touch port" (Option B)? Would Waydroid running the
   unmodified APK be an acceptable answer instead? *Blocks:* all of §6.2; the P-UT a entry.
2. **iOS route and hardware (OQ-2, OQ-3, OQ-5).** Join the Apple Developer Program; App Store, TestFlight-only
   (its automatic crash-report sharing is an egress `PRIVACY.md` must disclose) or unsigned archives? Which devices
   exist (iPad Pro M4 on record; iPhone? Mac?)? *Blocks:* I5, the iOS device-entry, the `release.yml` iOS job.
3. **Name (OQ-4).** Resolve or rename "Clavis" (*Clavis Sinica*) before any store listing? *Blocks:* App Store, OpenStore,
   Flathub and Store submissions; not the code work, nothing is submitted.
4. **Toolchain (OQ-17).** Keep Kotlin 2.1.20 / CMP 1.8.2 for the iOS proof (Option B), or move first? The repo's own recipe
   (Kotlin 2.2.x / CMP 1.9.3, AGP unchanged) differs from Option A (2.4.20 / 1.12.1 / AGP 9.4.1, which moves AGP, so D-2's
   lockstep holds only if Hyle and Shared-Libraries-asoc move too). *Blocks:* nothing in I1 unless it fails on the pin.
5. **Desktop breadth and channels (OQ-4, OQ-25).** Linux arm64, a Flatpak and its app id and domain (Flathub is a public
   surface), macOS Intel, Windows arm64; and is AppImage retired (none exists here)? *Blocks:* LX1–LX3, MA3, WI2.
6. **Downloader (new, repo-local).** Consolidate the duplicated JVM `HttpDownloader` first, and is a dependency-free
   `NSURLSession` implementation preferred to adding Ktor? Plan default: no Ktor, so `THIRD_PARTY_NOTICES.md` gains
   nothing. *Blocks:* the shape of I0 and the iOS downloader only.
7. **Unused dependencies (new).** Drop `okio` and `multiplatform-settings` before adding targets? *Blocks:* I0 only.
8. **Device passes (OQ-1, OQ-2, OQ-5).** Who runs iPad, iPhone and Ubuntu Touch passes, given no container can? *Blocks:* every NDV/NOV label.
9. **Actions budget and visibility (OQ-20).** Paid macOS minutes (10× on private repos) and arm64 runners, or make the repo public? A
   public repo also unblocks winget, SignPath Foundation and Flathub. *Blocks:* automatic triggers for `ios.yml` and `desktop-*.yml`.
10. **Workflow conventions (new).** May a gated port PR edit `release.yml` (D-16/D-26 require its expected-asset list to grow),
    and may new workflow files SHA-pin actions where the repo uses major tags (`RELEASE.md` §8)? *Blocks:* I5, LX4 asset publishing.
11. **Ogg on iOS (new).** Accept that 406 of 12,614 recordings (Latin nearly entirely) show no Play control on iOS, or fund a
    licence-reviewed decoder? *Blocks:* iOS audio parity only.
12. **iOS backup (new).** Exclude the state document from iCloud backup to mirror Android's cloud-backup exclusion? *Blocks:* the
    AppDirs step and the iOS row of `PRIVACY.md`.
13. **Identifiers (OQ-25).** iOS bundle id, Flatpak id, click name: NAMES.md rows before any manifest. *Blocks:* I3, LX3, UT2.

## 9. Sources read

**Read by the reader session that produced this repo's profile (2026-10-06; the profile is not checked in):**
`README.md`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`,
`shared/build.gradle.kts`, `composeApp/build.gradle.kts`, `shared/README.md`, `composeApp/README.md`,
`shared/src/commonMain/…/core/Services.kt`, `…/core/ClavisVersion.kt`, `composeApp/src/commonMain/…/platform/Platform.kt`,
`composeApp/src/androidMain/…/{AndroidServices,Platform.android,MainActivity,ClavisApplication}.kt`, `AndroidManifest.xml`,
`composeApp/proguard-rules.pro`, `composeApp/src/desktopMain/…/{DesktopServices,Platform.desktop,HttpDownloader,main}.kt`,
`.github/workflows/{ci,release}.yml`, `pipeline/requirements.txt`, `pipeline/README.md`, `pipeline/pack_web.py`,
`reference-prototype/README.md`, `reference-prototype/template.html` (API grep only), `docs/STATUS.md`, `docs/DECISIONS.md`,
`docs/build-platform.md`, `docs/06-app-architecture.md`, `docs/10-build-plan.md`, `docs/09-handoff.md`, `docs/00-README.md`,
`docs/RELEASE.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `PRIVACY.md`, `SECURITY.md`, `.gitattributes`.

**Re-checked against the checkout while writing this plan:** the source-set and `expect` declarations, line and test counts (§1, §2),
the common-source import grep, `ClavisStore.kt` (`clipFormat`, `flushSaves`, `runBlockingCompat` call sites), `ui/components/PageImages.kt`,
`res/xml/{backup_rules,data_extraction_rules}.xml`, `docs/USABILITY.md` (the colour rule), `docs/RELEASE.md` §0, §8, §9, the
`packs/pronunciations/*.json.gz` and `packs/sentences/*.json.gz` audio-format count, the Windows path lint, `git log`, and the icon sizes.

**Program inputs:** `Personal-Tracker/PORTING_PROGRAM.md` (§0–§3, §4.1–§4.6, the Clavis row of §5, §6, §7, §8) and the platform briefs
it summarises (`porting/platforms/`).

## Owner rulings and the proposed line (added 2026-10-07)

Status: PLAN. Nothing here is built, run on a device, signed or submitted. The program-level plan is Personal-Tracker `PORTING_PROGRAM.md` ([PR #10](https://github.com/mbaliga/Personal-Tracker/pull/10)), which holds the owner's rulings and section 5A, the proposed port / no-port line. The cells, estimates and open questions above are this repo's original plan and are unedited. Where the owner has since answered a question, the answer is below. Section 5A is a proposal; the owner has not yet confirmed it.

### Where Clavis sits in the proposed line (program section 5A.3, a proposal)

| Target       | Verdict    | Weeks and flags |
| ------------ | ---------- | --------------- |
| Ubuntu Touch | no-port    | -               |
| Linux        | exists     | 1w              |
| iOS/iPadOS   | owner-call | 5w              |
| macOS        | exists     | 0.5w            |
| Windows      | exists     | 0.5w            |

Key: `follows` means it ports only as far as the products that depend on it; `exists` means the program reads it as already running there, unverified (finish, verify and sign); `owner-call` means a genuine toss-up that the owner decides, with the program's lean in section 5A.4; flags: `g` gated on a prerequisite, `r` re-estimate or floor, `o` its own program, `s` scope note. The program's P4, P8, P12 and P13 gate whole columns or repos and are not flagged per cell. A port verdict counts the deliverable in the line; where this repo's plan calls a deliverable a reframe (program rule R12) it keeps that label. Tests cited in the reason: (a) the owner said it is needed there; (b) its job is really done on that OS by real users; (c) that OS is where it is sold or its audience is; it has no reason to exist if (x) its surface is absent or untouchable, (y) the capability is forbidden or impossible, or (z) the only form is a thin wrapper or a different product nobody asked for. P-numbers and OQ-numbers refer to the program plan (Personal-Tracker `PORTING_PROGRAM.md`, sections 5A.5 and 8).

Reason: JVM builds already exist on Linux, macOS and Windows (finish and sign). iOS is an owner-call, lean port, deferred: language learning is a phone-and-tablet habit and the App Store is the owner's channel (tests b and c), but it has no paid tier, no owner statement names Clavis, and "Clavis Sinica" name clearance blocks any listing; its simulator proof is the pin test and is needed whatever the verdict. UT has no native shape before S-UT1: the only thing a click can hold today is the frozen ten-language web build, a lesser product than the 17-language app (test z), and the owner was asked about Clavis and named only Typewright.

### Owner rulings that apply here

- **OQ-17 toolchain (2026-10-06):** "B: staged pin (Recommended)": Kotlin 2.1.20 and Compose Multiplatform 1.8.2 for the first wave, 2.4.x deferred.
- **OQ-38 iOS pin (2026-10-07):** "Move iOS to Kotlin 2.2.21 + CMP 1.9.3 (Recommended)", with the first iOS proof on an explicitly selected Xcode 26.x. The program plan reads this as moving a repo that ships an iOS target as a whole (a Gradle build has one Kotlin version); that reading is not researched, and the bump cost for repos pinned lower is in no figure.
- **OQ-31 Mac (2026-10-06 and 2026-10-07):** "Buy a Mac", and on 2026-10-07 an Apple-silicon Mac mini, not yet bought; no Apple device gate is called checkable before then.
- **Repo-specific:** this plan's Q4 asked whether to keep Kotlin 2.1.20 and CMP 1.8.2 for the iOS proof or move first; OQ-38 reads as answering that the iOS targets move to Kotlin 2.2.21 and CMP 1.9.3, the repo's own recipe with AGP unchanged. Clavis:D-2 stands as written until the owner records the move in DECISIONS.md. Step I1's stop at BLOCKED(toolchain) and the pinned macos-15 lane in step I4 (no Xcode named) predate the ruling, which asks for an explicitly selected Xcode 26.x. This repo is private, so per OQ-20 and OQ-31 the proof waits on the Mac or on a public repo.
- **Apple (OQ-2, 2026-10-06):** "Whatever let's me sell apps on the app store": the paid Developer Program and the App Store are the target channel. TestFlight is not used until the exception to I-1 (OQ-32, drafted as PROPOSED-1, not approved) is approved.
- **OQ-20 CI (2026-10-06):** "Linux-only CI when private (Recommended)": this repo is private, so the program's jvm-macos, jvm-windows, ios-sim and ios-device-build lanes do not run while it stays private. An owner Mac is a third route for the iOS and macOS builds; the Windows MSI job in this repo's release.yml (never yet run: its STATUS.md says macOS and Windows packaging has not been exercised) still depends on the repo going public or a funded budget.
- **OQ-5 hardware (2026-10-06):** the owner has a Dell, a Windows desktop and an HP laptop; the Steam Deck is no longer a dependency for now. Which machine carries which device gate is not decided (OQ-33).
- **Directives (2026-10-06):** "Draft amendments for approval": program directives I-1 to I-12 and rules R1 to R12 are unchanged; PROPOSED-1 to PROPOSED-4 in Personal-Tracker `DECISIONS.md` are drafts awaiting the owner.

### Prerequisites and open questions that touch this repo (program sections 5A.5 and 8)

Prerequisites (program-level; not costed here):

- program P8: An Apple-silicon Mac (OQ-31: a Mac mini chosen on 2026-10-07, not yet bought)
- program P12: The iOS toolchain pin (OQ-38, ruled 2026-10-07): Kotlin 2.2.21 with Compose Multiplatform 1.9.3 for the iOS targets, proven on an explicitly selected Xcode 26.x; the bump cost for consumers pinned lower is in no figure
- program P13: OQ-4: 'Clavis Sinica' name clearance before any store listing

Owner questions in the program register that concern this repo (status as of 2026-10-07):

- OQ-2 (ruled): Apple Developer Program and the delivery route
- OQ-3 (open): Signing custody
- OQ-4 (open): Channels and store compatibility
- OQ-5 (ruled): Hardware stance
- OQ-17 (ruled): Toolchain pins: the pin is ruled; the "Also" approvals (converting shared modules to kotlin("multiplatform"), asom's no-KMP rule staying asom-local) are unanswered
- OQ-20 (ruled): CI minutes, storage and repo visibility
- OQ-25 (open): Identifier registry
- OQ-31 (ruled): CI for App Store builds; which Mac
- OQ-32 (open): Exception to I-1 for TestFlight and App Store crash reports
- OQ-33 (open): Hardware details still open
- OQ-38 (ruled): iOS toolchain pin

When the owner confirms or changes the line, this repo's original cells above stay as the engineering detail; only the verdicts and re-costs in program section 5A change.

## Update 2026-10-14: the market lens, iPadOS and the owner's latest answers

Status: PLAN. Nothing here is built, run on a device, signed or submitted. This update follows the section "Owner rulings and the proposed line (added 2026-10-07)" above and the program plan's section 5A.12 (Personal-Tracker `PORTING_PROGRAM.md`, [PR #10](https://github.com/mbaliga/Personal-Tracker/pull/10)), which governs where the two differ. It records the owner's market lens and answers of 2026-10-13 and 2026-10-14; the proposed line is still a proposal. Two tests were added to the program's section 5A.1: (d) a real gap, or we are better or cheaper ("better" only where it has been measured; free alone is no gap when free tools exist); (w) mature free alternatives abound and the app adds nothing.

### Where Clavis sits now (program section 5A.12, a proposal)

| Target       | Verdict, weeks and flags |
| ------------ | ------------------------ |
| Ubuntu Touch | no-port                  |
| Linux        | exists 1w                |
| iPhone       | follows                  |
| iPad         | port 5w (early)          |
| macOS        | exists 0.5w              |
| Windows      | exists 0.5w              |

The Apple column is now split: iPhone and iPad are judged apart. Where an app ports to both, the weeks are counted once on the lead cell and the other reads follows. The wave (early or later) is for the iPad, and for the iPhone where it leads.

### What changed for this repo

- iPad is now its own column: **port, early wave, 5 weeks**, the first repo through the Compose-on-iOS recipe; iPhone follows (the earlier section had iOS owner-call 5w).
- Ubuntu Touch stays no-port: its only shape is the frozen web build in a click, which the Ubuntu Touch scope ruling excludes, although a search of OpenStore found no offline language course (not exhaustive).
- **The owner chose to make this repo public** (OQ-40) so the iPad proof can use free hosted macOS runners. That is an owner action in GitHub (Settings, General, Change visibility); the assistant cannot change visibility. A pre-publication audit found no secret-shaped strings in all 37 commits, no keystore, env or credential files, only noreply author addresses, and the licence files (Apache-2.0 LICENSE, NOTICE, SECURITY.md, PRIVACY.md). The docs under docs/research hold competitor pricing and market sizing, which become public. After the flip, the lines in this plan and in the earlier section that say the repo is private (the OQ-20 and signing-route statements) are out of date and should be updated.

### Owner answers that apply here

- **TestFlight (PT:D-AD, approved 2026-10-14):** "Approve PROPOSED-1 as drafted". Directive I-1 now carries an exception for crash reports that Apple collects on TestFlight and the App Store. The app does not transmit those reports and adds no crash-reporting or analytics SDK of its own, and its README, store listing and about/privacy page must name the exception. This supersedes any statement in the earlier section that TestFlight is not used until the exception is approved. Any repo-local no-telemetry rule here is a restatement that PT:D-AD does not amend; recording the exception there is for the owner.
- **Mac mini timing (OQ-31, 2026-10-14):** "Use hosted macOS runners on public repos first, buy later". This repo is private today, so hosted macOS builds are billed (the program's figure is about 10 times the rate, not independently verified). The owner chose to make it public (OQ-40, an owner action, not done); after that, simulator builds, unit tests and unsigned archives can start on free hosted macOS runners. The Mac mini stays chosen and not bought.
- **Apple ids and signing:** Apple bundle ids use `com.asoc.<appname>` (OQ-25, in part; a NAMES.md row first, R11). Signing material is to live in GitHub environment secrets behind a required-approval gate (OQ-3, Apple half). The owner will enrol in the Apple Developer Program ("Not yet, I'll enrol"); until then every Apple build is a simulator or unsigned hosted build.
- **Apple devices:** the owner has an iPad Pro M4, an Apple Pencil and an iPhone 12 mini; the Mac mini is not yet bought, and nothing is device-verified.
- **Skin contract (OQ-39, 2026-10-14):** proposed conformance level for this app: Level 1, cost class A (already Compose Multiplatform commonMain with its own ClavisPalette); second in the rollout order. The owner chose Tier 1 (palette) everywhere first and every app declaring its own level; the assistant chose JSON, not XML, after the owner asked for a check; the draft spec is Personal-Tracker `porting/skin-contract/SPEC-DRAFT.md`. This is a proposal, not decided for this app.

When the owner confirms or changes the line, this repo's original cells above stay as the engineering detail; only the verdicts and re-costs in program section 5A.12 change.
