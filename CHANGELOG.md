# Changelog

All notable user-visible, data-format and release-relevant changes to Clavis are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions are semantic (docs/DECISIONS.md D-16) and dates are ISO. The release workflow refuses a tag whose `## [x.y.z]` section is missing here, and the section becomes the top of the GitHub Release notes.

## [Unreleased]

### Domain (`:shared`)

- Added the Kotlin Multiplatform domain layer ported from the v0.10 web build: corpus model and gzip pack decoder, language registry, FSRS-5 scheduler (19-weight vector, `DECAY` −0.5, `FACTOR` 19/81), selection and grading engines (lane hopping, the 6-hour scene rule, one productive language per family), the baseline vocabulary test with pseudoword foils, the Paths engine, and the balloon-geometry and cloze-mask helpers.
- Added the learner-state store: one JSON document per profile (`clavis-state.json`) written to a temporary file and atomically renamed, with a schema version for migrations (D-12).
- Added about 180 JVM unit tests that run on desktop and on both Android unit-test variants (`:shared:allTests`), plus an integration test against the real packs when `pipeline/build/packs` is present.

### Content pipeline (`pipeline/`)

- Added the parameterised corpus pipeline (extract → annotate → pseudo → pack_assets → pack_web → validate) driven by `languages.json`; 17 languages are enabled, the nine defaults plus English and opt-in Portuguese, Latin, Russian, Polish, Ukrainian, Turkish and Hindi (D-6).
- Added lexicon hygiene (wordfreq and Wiktionary attestation) so translator names and credit tokens can no longer become cloze targets (D-7).
- Added enrichment stages: etymology (Wiktionary via wiktextract/kaikki.org and EtymDB 2.1), example sentences (Tatoeba, permissive audio only) and pronunciations (IPA plus Wikimedia Commons audio references with per-file licences) (D-5, D-7).
- Committed the generated packs as app resources (`composeApp/src/commonMain/composeResources/files/packs/`, about 22 MB with the page art) so the app and CI build without the corpus (D-3).
- Generated packs: `clavis-comics` 2026.09.01 (CC BY 4.0; 1,460 balloons from 305 pages of 39 Pepper&Carrot episodes), `clavis-lexicon` 2026.09.01 (CC BY-SA 4.0), `clavis-etymology` 2026.09.02 (CC BY-SA 4.0), `clavis-sentences` 2026.09.02 (CC BY 2.0 FR), `clavis-pronunciations` 2026.09.02 (CC BY-SA 4.0; audio per file). Every pack carries a generated `ATTRIBUTION.md`.

### App shell (`:composeApp`)

- Added the Compose Multiplatform application skeleton: Android entry point with no permissions, desktop entry point, theme tokens, string resources, a placeholder home screen showing the version, and a headless desktop UI test.

### Release engineering

- Added single-source versioning (`gradle/version.gradle.kts`): `-Pclavis.version` > `CLAVIS_VERSION` > `ClavisVersion.kt`; `versionCode = MAJOR·10000 + MINOR·100 + PATCH`; per-OS desktop package versions so jpackage accepts a 0.x app (docs/RELEASE.md §5).
- Added opt-in Android release signing from `keystore.properties` or the `CLAVIS_KEYSTORE_*` secrets; without them the release build stays unsigned and the artifacts say so in their names.
- Added the CI workflow (domain and UI tests, lint, debug and R8 release APK with a 16 KB page-size check, Linux uber-jar smoke test, pipeline unit tests) and the tag-triggered release workflow (Android APK and AAB, Linux `.deb` and uber-jar, macOS `.dmg` with optional Developer ID signing and notarization, Windows `.msi`, dependency graphs, `SHA256SUMS.txt`, GitHub Release with pre-release marking).
- Added `LICENSE` (Apache-2.0), `NOTICE`, `THIRD_PARTY_NOTICES.md`, `PRIVACY.md`, `SECURITY.md`, `CONTRIBUTING.md`, `docs/RELEASE.md`, `docs/STORE-LISTING.md`, Dependabot configuration and issue templates.
- Reviewed the R8 configuration against the merged rules; recorded the named-companion rule for `@Serializable` classes.

### Documentation

- Added `docs/DECISIONS.md` (D-1 … D-18) and `docs/build-platform.md` (the verified toolchain matrix and build commands).

### Known pre-release requirements

- The method is being tested on one person; nothing here is validated pedagogy (D-18).
- Google Play requires `targetSdk` 36 for new apps and updates since 2026-08-31; the build targets API 35 until the SDK bump is verified on the pinned toolchain (docs/RELEASE.md §9).
- Device and desktop acceptance (TalkBack, RTL, 200% text, machines without CJK/Arabic system fonts, in-place upgrades) is not yet recorded.
- macOS and Windows installers are unsigned until certificates exist; the release notes and README carry the Gatekeeper and SmartScreen steps.
- The learner-state document is not yet excluded from Android cloud backup (PRIVACY.md).
