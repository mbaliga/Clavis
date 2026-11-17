# iOS / iPadOS device checklist (owner pass, step I6)

Everything below is **NEEDS-DEVICE-VALIDATION**: no row has been seen working. The first four rows decide whether the
rest can start. Devices on record: iPad Pro M4 (+ Apple Pencil), iPhone 12 mini. Start with the iPad. Mark each row
with the date, the device, the iOS version and the build (the `.ipa`/TestFlight build number) when you test it.

## 0. Before anything runs

| # | Step | Who |
|---|---|---|
| 0.1 | Make the repository public in GitHub (Settings, General, Change visibility), so hosted macOS minutes are free and `ios.yml` runs on push/PR. The workflow already gates on `github.event.repository.private == false`. | owner |
| 0.2 | Run **Actions > iOS > Run workflow** once by hand. Read the first red step: the Kotlin and Swift have never met an Apple compiler. The risk list is at the end of `iosApp/README.md`. | owner (or a follow-up session reading the log) |
| 0.3 | Add the NAMES.md row for `com.asoc.clavis` (outside this repo) and settle the "Clavis Sinica" name question before creating an App Store Connect record. | owner |
| 0.4 | Join the Apple Developer Program. Then create the `apple-release` environment (required reviewers), the repository variable `IOS_SIGNING_ENABLED=true`, and the secrets named in `ios.yml`'s `testflight` job. Without these the lane builds only unsigned and simulator artefacts, and the iPad cannot install them. | owner |
| 0.5 | Install route for the first device run, pick one: (a) a Mac with Xcode and your own team (README "Build it yourself on a Mac"); (b) TestFlight after 0.4; (c) re-sign the release's unsigned `.ipa` with Sideloadly/AltStore. | owner |

## 1. Launch and data (iPad first, then iPhone)

| # | Check | Pass looks like |
|---|---|---|
| 1.1 | App launches cold and reaches the Learn screen | no crash in the first 20 s (the lane asserts this on the simulator only) |
| 1.2 | First-launch time with the 22 MB packs | note seconds; flag anything over ~3 s |
| 1.3 | Complete a lesson, background the app, force-quit it, relaunch | progress is still there (saves flush on background; D-12 atomic rename) |
| 1.4 | Fresh install over TestFlight, then restore on a second device | decide Q12: should the document migrate? (data dir is excluded from backup today) |
| 1.5 | Memory with the 12-page comic cache (Letters/Story, open ~20 pages) | no jetsam kill; note peak in Xcode's Memory gauge |

## 2. Services (each is a separate commit-sized piece of Kotlin; a failure names one file)

| # | Check | File to look at if it fails |
|---|---|---|
| 2.1 | Speak a word in each enabled language: the Speak button appears only where a voice exists; rate sounds like the other platforms (0.85 x default rate, tune by ear) | `IosSpeaker` |
| 2.2 | Tap Play on a Tatoeba clip (mp3): downloads, plays, plays with the ringer switch muted | `IosDownloader`, `IosAudioPlayer` |
| 2.3 | A Latin or Italian clip that is Ogg Vorbis: **no Play control** is shown (accepted gap, 3.2% of recordings; decision Q11) | `audioFormats` in `iosServices()` |
| 2.4 | Download guards: airplane mode fails quietly; a clip larger than 25 MB is refused; an http redirect target is refused (needs a test URL) | `IosDownloader` + `DownloadPolicy` (unit-tested) |
| 2.5 | Import a `.srt`, `.lrc`, `.txt` from Files; a `.pdf` and a >2 MB file are refused with the toast | `IosFileImporter` + `ImportPolicy` (unit-tested) |
| 2.6 | Share the feedback draft: the sheet opens, and on iPad anchors as a popover without crashing | `IosShare` |
| 2.7 | Reduce Motion on: Cosmos and transitions are calm | `platformReducedMotion` |

## 3. Scripts and layout (D-9: iOS is assumed to supply the glyphs)

| # | Check |
|---|---|
| 3.1 | Screenshot one screen per script: Japanese, Korean, Chinese, Arabic, Hebrew, Devanagari (if any lane has it). **Tofu boxes anywhere means a Noto face must be bundled** (`scriptFallbackFonts()` in `Platform.ios.kt`); record the D-9 decision either way. |
| 3.2 | Arabic is right-to-left everywhere (lists, session card, cloze) |
| 3.3 | Dynamic Type at the two largest accessibility sizes: nothing truncated without a way to read it |
| 3.4 | iPad Split View across the 600 dp dock/rail breakpoint; Stage Manager window resize; rotate |
| 3.5 | Hardware keyboard: the keyboard map (Settings card) works; Pencil tap behaves as touch |
| 3.6 | VoiceOver, including the **cloze case**: the hidden word is not read aloud before the learner reveals it (`ClozeLeakTest` is desktop-only; VoiceOver is expected to read the same Compose semantics tree, to be confirmed) |
| 3.7 | Safe areas on a notched iPhone (12 mini) and the iPad Pro: no content under the home indicator |

## 4. Record

When rows are checked, copy the dated results into `docs/STATUS.md` section 8 and change the evidence class of what you
saw from NEEDS-DEVICE-VALIDATION to DEVICE-VERIFIED (date, device, OS). Unchecked rows stay open.
