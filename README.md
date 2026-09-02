# Clavis

An open-source, offline-first application that teaches nine languages at once — Spanish, French,
Italian, German, Dutch, Japanese, Korean, Chinese, Arabic, bridged through English — by treating
them as one connected system rather than nine separate courses: annotated open-source comics, real
etymology, spaced repetition (FSRS-5), and an adaptive branching curriculum instead of a fixed
syllabus.

> Multi-lingual everyday proficiency without deliberate, dedicated learning effort by the user.

The one-sentence version: every mainstream language app models a learner acquiring *a* language.
Clavis models a learner acquiring *a region of the world's lexicon* — where knowing Spanish `casa`
is already partial knowledge of Italian `casa`, French `chez`, and Latin `casa`, and the useful unit
of study is the relationship between words, not the word.

## Start here

- **[docs/00-README.md](docs/00-README.md)** — the full documentation set: premise, hypotheses (and
  what the research actually said about them), personas, information architecture, app
  architecture, etymology, references, handoff.
- **[docs/10-build-plan.md](docs/10-build-plan.md)** — how to get from what's seeded in this repo to
  a running prototype, both a quick reproduction of the original web build and a native Android
  build in this constellation.
- **[reference-prototype/](reference-prototype/)** — the corpus-pipeline generator and the
  single-file web app template from the original build. This is the "authoritative spec": a native
  port's job is to reproduce its behaviour, not its architecture.

## Status

**Pre-release, version 0.1.0; no tagged release yet.** This branch holds the Kotlin Multiplatform
domain layer (`shared/`: corpus model, FSRS-5, selection and grading engines, baseline test, Paths,
atomic state store — about 180 JVM tests on every target), the parameterised corpus pipeline
(`pipeline/`, with the enrichment stages for etymology, sentences and pronunciations), the Compose
Multiplatform application shell (`composeApp/`, Android + desktop) and the release engineering below.
The generated content packs (comics, lexicon, etymology, sentences, pronunciations — about 22 MB) are
committed as app resources under `composeApp/src/commonMain/composeResources/files/packs/` so builds are
reproducible without the corpus (D-3). Stable release remains
blocked on recorded device, desktop-font, accessibility and signed-upgrade acceptance
([docs/RELEASE.md](docs/RELEASE.md) §9); the method itself is one person's experiment and is not a
validated pedagogy ([docs/01-premise.md](docs/01-premise.md) §6).

## Downloads

Every tagged release on the [Releases page](https://github.com/mbaliga/Clavis/releases) carries the
same set of files plus `SHA256SUMS.txt`. Verify before installing:

```bash
sha256sum -c SHA256SUMS.txt --ignore-missing
```

| Platform | File | Notes |
|---|---|---|
| Android 8.0+ | `clavis-<v>-android.apk` | Universal APK for sideloading. A file named `-unsigned.apk` was built without the maintainer's signing key: Android will not install it over a signed build, nor a signed one over it. The `.aab` is for Google Play only. |
| Linux x64 | `clavis_<v>_amd64.deb` | `sudo apt install ./clavis_<v>_amd64.deb`, then `clavis` or the menu entry (bundles its own Java 17). |
| Any OS with a JRE 17+ | `clavis-<v>-<os>-<arch>.jar` | `java -jar clavis-<v>-linux-x64.jar`. Jars are per OS/architecture (each bundles that platform's Skia), not one jar for all. |
| macOS 11+ (Apple silicon) | `Clavis-<v>-macos-arm64.dmg` | Drag to Applications. **Unsigned builds** (`-unsigned.dmg`): macOS 15 removed the Control-click bypass — double-click once, choose *Done*, then System Settings → Privacy & Security → *Open Anyway* → *Open*. macOS remembers it. |
| Windows 10/11 x64 | `Clavis-<v>-windows-x64.msi` | Not code-signed yet: SmartScreen shows "Windows protected your PC" → *More info* → *Run anyway*. Upgrades install over the previous version. |

Release candidates without a tag are uploaded as workflow artifacts by the CI workflow (debug APK,
unsigned R8 release APK, Linux jar); they are validation outputs, not releases.

## Build

Requirements: JDK 17 (any JDK ≥ 17 runs Gradle; the foojay resolver provisions 17 for compilation),
Android SDK platform 35 with build-tools 35.0.0, the Gradle wrapper (8.14.3), Python 3.11 for the
pipeline. Everything about the pins is in [docs/build-platform.md](docs/build-platform.md).

```bash
export ANDROID_HOME=/opt/android-sdk        # or sdk.dir=… in local.properties
./gradlew :shared:allTests :composeApp:desktopTest :composeApp:lintDebug \
          :composeApp:assembleDebug :composeApp:packageUberJarForCurrentOS   # what CI runs
./gradlew :composeApp:assembleRelease :composeApp:bundleRelease              # unsigned unless docs/RELEASE.md §1
./gradlew :composeApp:packageDeb                                             # .dmg / .msi only on that OS
./gradlew :composeApp:run                                                    # desktop app
python -m pytest pipeline/tests -q                                           # after pip install -r pipeline/requirements.txt
```

On a machine whose proxy is rate-limited by Maven Central (HTTP 429 mid-build), set
`clavis.mirror=true` in `~/.gradle/gradle.properties` to insert the JetBrains and Aliyun mirrors ahead
of Maven Central — machine-local only, never in the repo or CI (`docs/build-platform.md` §8).

GitHub Actions runs the CI line on every push and pull request and uploads the debug APK, an unsigned
R8 release APK and the Linux uber-jar; the tag-triggered Release workflow builds the installers on
their own OS runners and publishes them ([docs/RELEASE.md](docs/RELEASE.md)).

## Documentation

- [Changelog](CHANGELOG.md) · [Decisions](docs/DECISIONS.md) · [Build platform](docs/build-platform.md)
- [Concept docs](docs/00-README.md) · [Build plan](docs/10-build-plan.md) · [Pipeline](pipeline/README.md)
- [Release and signing procedure](docs/RELEASE.md) · [Store listing](docs/STORE-LISTING.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md) · [Privacy](PRIVACY.md) · [Security policy](SECURITY.md) · [Contributing](CONTRIBUTING.md)

## Licensing

Application and pipeline code: Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
Content packs are restricted to CC0 / CC BY / CC BY-SA and are distributed separately from the code:
Pepper&Carrot by David Revoy and its translators (CC BY 4.0 — confirmed in the repository's own
README, [D-4](docs/DECISIONS.md)), wordfreq frequencies (CC BY-SA 4.0), Wiktionary via wiktextract and
EtymDB 2.1 (CC BY-SA 4.0), Tatoeba (CC BY 2.0 FR), Wikimedia Commons audio (per file); the pack as a
whole is CC BY-SA 4.0. Every author, translator and source is named in each pack's generated
`ATTRIBUTION.md` and summarised in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Bundled fonts are
under the SIL Open Font License 1.1.
