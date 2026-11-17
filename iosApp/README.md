# iosApp: the SwiftUI shell around Clavis for iOS and iPadOS

Evidence classes used below: **BUILT-HERE** (run in the Linux authoring container and seen to pass), **CI-LANE-WRITTEN**
(a workflow exists, never run), **CODE-ONLY** (written, never compiled for an Apple target), **NEEDS-DEVICE-VALIDATION**.
Nothing here has been compiled for iOS or run on an Apple device or simulator: the authoring container is Linux
(status and the owner's next steps: `docs/STATUS.md` section 8, `docs/DEVICE_CHECKLIST_IOS.md`).

## What is where

| Piece | Path | Evidence |
|---|---|---|
| Shared domain code, now with iOS targets | `shared/` (`iosArm64`, `iosSimulatorArm64`) | the common tests pass on Kotlin/Native (linuxX64 stand-in), BUILT-HERE; Apple targets CI-LANE-WRITTEN |
| iOS actuals for `:shared` | `shared/src/iosMain` (UIDevice name; SHA-256 and gunzip over pure-Kotlin cores; POSIX `FileIo`) | pure cores BUILT-HERE (JVM cross-checks); `FileIo` source BUILT-HERE on Linux Native; `Platform.ios.kt` CODE-ONLY |
| iOS UI core | `composeApp/src/iosMain` (services, `MainViewController`) | CODE-ONLY |
| The Xcode project spec | `iosApp/project.yml` (XcodeGen) | CI-LANE-WRITTEN (YAML parses; generation never run) |
| The SwiftUI shell | `iosApp/Clavis/*.swift` | syntax-checked with a Linux Swift 6.0.3 `swiftc -parse` (BUILT-HERE); never compiled against SwiftUI |
| CI | `.github/workflows/ios.yml`, the `ios` job of `release.yml` | CI-LANE-WRITTEN (`actionlint` clean) |

## The recipe (for the next repo that wants Compose on iOS)

1. **Targets.** `iosArm64()` and `iosSimulatorArm64()` in each KMP module (no Intel simulator). In the app module
   `binaries.framework { baseName = "ComposeApp"; isStatic = true }`. `kotlin.native.ignoreDisabledTargets=true` keeps
   Linux/Windows builds quiet; the JVM gate is unchanged by the targets.
2. **Actuals.** Everything the platform must provide is an `expect` in common code (`Platform.kt`, `Services.kt`).
   Put as much as possible in plain Kotlin in `commonMain` so it is testable on the JVM (here: SHA-256, gunzip, the
   download and import policies) and keep the iOS actual a thin call. Run the common tests on a Native target early:
   it found two iOS blockers the JVM gate cannot see (`@JvmInline` without the import; a regex using `\p{IsHan}`,
   which Kotlin/Native's regex engine lacks).
3. **Entry point.** `MainViewController()` in `iosMain` returns a `ComposeUIViewController`. It also flushes the
   learner document on `willResignActive` / `didEnterBackground` / `willTerminate`.
4. **Shell.** `ClavisApp.swift` + `ComposeView.swift` host that controller. `project.yml` builds the Kotlin framework in
   a pre-build script (`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`) and links it; `xcodegen generate`
   writes the git-ignored `Clavis.xcodeproj`. There is no `.xcodeproj` in the repo to merge or hand-edit.
5. **Lane.** `ios.yml`: the common tests on the simulator, then the Xcode build, then launch on an iPhone and an iPad
   simulator and assert the process survives 20 s.

## Build it yourself on a Mac

```sh
# Xcode 26.x selected (xcode-select), JDK 17 on PATH (JAVA_HOME), Homebrew:
brew install xcodegen
git clone <repo> && cd Clavis
./gradlew :shared:iosSimulatorArm64Test                 # the domain tests on the simulator
cd iosApp && xcodegen generate && open Clavis.xcodeproj  # run on a simulator; for your iPad pick your team under Signing
```

Set your own team in Xcode for a device run; the repository never holds a team id, certificate or profile.

## Identity, privacy and signing

- **Bundle id `com.asoc.clavis`** is written once, in `project.yml` (`PRODUCT_BUNDLE_IDENTIFIER`, marked). `ios.yml` reads
  it from there. Owner follow-up: a NAMES.md row for it (plan rule R11; NAMES.md is outside this repo) before the App
  Store Connect record is created, and the name-clearance question ("Clavis Sinica", OQ-4) before any listing.
- **No telemetry, no SDK, no usage-description keys, no entitlements.** Document import goes through the system
  picker; the only network request is a download the learner taps, over HTTPS (App Transport Security at its defaults).
  `PrivacyInfo.xcprivacy` declares no tracking and no collected data, plus two required-reason API categories
  (file timestamps C617.1, system boot time 35F9.1) that the Kotlin/Native runtime and Compose are expected to use;
  App Store Connect's validation is the real test and may ask for more.
- **The one approved exception (PT:D-BD, 2026-10-14):** crash reports that Apple itself collects for TestFlight and App
  Store builds. The app does not transmit them and adds no crash-reporting or analytics SDK. It applies only once the
  app is distributed through TestFlight or the App Store.
- **Backup:** `IosAppDirs.EXCLUDE_FROM_BACKUP = true` marks the data directory excluded from iCloud/iTunes backup to mirror
  Android's backup exclusion (PRIVACY.md). Whether that also withholds the document from device-to-device migration is
  unknown (plan section 8 Q12); flip the constant if the owner prefers the document to travel.
- **Signing** lives only in the `apple-release` GitHub environment (`ios.yml`'s disabled `testflight` job). The `.ipa`
  that `release.yml` attaches is unsigned by design.
- **Icon:** `AppIcon-1024.png` is the desktop icon upscaled and flattened onto the app's paper colour so the asset
  catalog is valid. The owner supplies final art (1024x1024, no alpha).

## Known risks the first macOS run is expected to surface

These are guesses at Apple-SDK import shapes that cannot be checked without an Apple SDK; each is isolated to one
file. `IosServices.kt`: enum import shape of `AVSpeechBoundary`; `NSURLSession` delegate overload signatures and the
`UnsafeNumber` opt-ins (`IosDownloader.kt`); `UIDocumentPickerViewController(forOpeningContentTypes:asCopy:)`
(requires the `UniformTypeIdentifiers` cinterop). `project.yml`: whether `embedAndSignAppleFrameworkForXcode` finds the
framework at the search path given. Fonts: iOS is assumed to supply CJK, Arabic, Devanagari and Hebrew glyphs (D-9);
the checklist has the per-script screenshots that decide it. 406 of 12,614 recordings are Ogg Vorbis (Latin almost
entirely) and show no Play control on iOS (`audioFormats` is `wav` and `mp3`).
