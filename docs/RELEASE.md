# Clavis release procedure

Automated: `.github/workflows/release.yml` builds every artifact in D-16 on its own OS runner when a tag `v<version>` is pushed, and publishes one GitHub Release with `SHA256SUMS.txt`. Manual: bumping, tagging, verifying, and the Play Console. Every secret is optional — without them the workflow still publishes, with `-unsigned` in the Android and macOS file names. This document mirrors `Fyl-Manager/docs/RELEASE.md` where the two projects overlap.

## 0. What the workflow produces

| Job (runner) | Gradle tasks | Release assets (`<v>` = version without the `v`) |
|---|---|---|
| `preflight` (ubuntu-24.04) | — | fails fast if the tag ≠ `ClavisVersion.NAME` or `CHANGELOG.md` has no `## [<v>]` section |
| `android` (ubuntu-24.04) | `:shared:allTests :composeApp:desktopTest :composeApp:lintDebug :composeApp:assembleRelease :composeApp:bundleRelease` | `clavis-<v>-android.apk` / `clavis-<v>-android-unsigned.apk`, `clavis-<v>-android.aab` / `-unsigned.aab`, `clavis-<v>-android-mapping.txt` |
| `linux` (ubuntu-24.04) | `:composeApp:desktopTest :composeApp:packageDeb :composeApp:packageUberJarForCurrentOS` + dependency graphs | `clavis_<v>_amd64.deb`, `clavis-<v>-linux-x64.jar`, `clavis-<v>-dependencies-{android,desktop}.txt` |
| `macos` (macos-15, Apple silicon) | `:composeApp:desktopTest :composeApp:packageUberJarForCurrentOS` then `notarizeDmg` (secrets) or `packageDmg` | `Clavis-<v>-macos-arm64.dmg` / `Clavis-<v>-macos-arm64-unsigned.dmg`, `clavis-<v>-macos-arm64.jar` |
| `windows` (windows-2025) | `:composeApp:desktopTest :composeApp:packageMsi :composeApp:packageUberJarForCurrentOS` | `Clavis-<v>-windows-x64.msi` (never code-signed yet), `clavis-<v>-windows-x64.jar` |
| `web` (ubuntu-24.04) | `python pipeline/pack_web.py` on the committed packs under `composeApp/src/commonMain/composeResources/files/packs/` — the frozen ten-language reference build (D-1, D-22); standard library only, about 21 MB, verified locally in 3 s. Should that directory ever be absent, the job records a notice and uploads nothing | `clavis-<v>-web.html` |
| `release` (ubuntu-24.04) | download everything, `sha256sum`, `softprops/action-gh-release` | `SHA256SUMS.txt`, the Release itself — pre-release when the version has an `-alpha/-beta/-rc` suffix, "latest" otherwise |

The uber-jars are per OS: `packageUberJarForCurrentOS` bundles only the host's Skiko. Running the Release workflow with *Run workflow* and a version is a dry run: everything is built and uploaded to the workflow run, nothing is published. Jobs that fail can be re-run individually; the `release` job only runs when all five build jobs succeeded, so a partial release cannot be published by accident.

## 1. One-time setup

### 1.1 Android upload key (never in the repository)

```bash
keytool -genkeypair -v -keystore clavis-upload.jks -alias clavis-upload \
        -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
# Play accepts an RSA upload key of 2048 bits or more; 4096 is fine. PKCS12 uses one password for store and key.
keytool -list -v -keystore clavis-upload.jks -alias clavis-upload | grep -A1 'SHA256:'   # record the fingerprint privately
base64 -w0 clavis-upload.jks > clavis-upload.jks.b64                                    # macOS: base64 -i clavis-upload.jks | pbcopy
```

Repository secrets (Settings → Secrets and variables → Actions):

| Secret | Content |
|---|---|
| `CLAVIS_KEYSTORE_BASE64` | the base64 text (line breaks are tolerated) |
| `CLAVIS_KEYSTORE_PASSWORD` | store password |
| `CLAVIS_KEY_ALIAS` | `clavis-upload` |
| `CLAVIS_KEY_PASSWORD` | key password (same as the store password for PKCS12) |

The Gradle build (`gradle/release-signing.gradle.kts`) decodes the keystore under `composeApp/build/signing/` and signs `assembleRelease` and `bundleRelease` when all four are present; the release workflow checks for all four before labelling the artifacts as signed. Pull-request builds never see secrets. Back the keystore up in the maintainer's password manager and delete `clavis-upload.jks.b64`; `.gitignore` refuses `*.jks`, `*.p12`, `*.b64` and `keystore.properties`. A lost upload key means a Play "upload key reset" request.

Local signing without secrets: put `keystore.properties` in `composeApp/` (or the repo root):

```properties
storeFile=/absolute/or/relative/path/clavis-upload.jks   # relative paths resolve against this file
storePassword=…
keyAlias=clavis-upload
keyPassword=…
```

Both paths were verified on 2026-09-02 with a throwaway keystore: `apksigner verify --verbose --print-certs` passes on the APK, `jarsigner -verify` on the AAB. The file wins over the environment when both are present.

### 1.2 Apple (optional; needs a paid Apple Developer Program membership)

Create a **Developer ID Application** certificate, export it with its private key from Keychain Access as a `.p12`, and create an app-specific password at appleid.apple.com. Secrets:

| Secret | Content |
|---|---|
| `MACOS_CERT_P12_BASE64` | `base64 -i developer-id.p12 \| pbcopy` |
| `MACOS_CERT_PASSWORD` | the `.p12` export password |
| `APPLE_ID` | the Apple ID e-mail |
| `APPLE_TEAM_ID` | the 10-character team id |
| `APPLE_APP_PASSWORD` | the app-specific password |
| `MACOS_SIGNING_IDENTITY` (optional) | `Developer ID Application: Name (TEAMID)` — otherwise read back from the imported certificate |

With all five present the `macos` job imports the certificate into a temporary keychain (deleted `if: always()`), runs `notarizeDmg` (= `packageDmg` + `xcrun notarytool submit --wait` + `xcrun stapler staple`, Compose Multiplatform 1.8.2) and validates the staple. Without them the DMG is built unsigned and named `-unsigned`; §7 has the user-facing text.

### 1.3 Windows

No code signing in v1: the MSI is always unsigned and the release notes say so. §7 lists the options for adding it.

### 1.4 Google Play Console (once)

Create the app with package `io.github.mbaliga.clavis`. Under *App integrity → Play App Signing* choose **"Export and upload a key from Java keystore"** and upload the *same* key that signs the GitHub APK; if Google generates the app-signing key instead, Play installs and GitHub sideloads carry different certificates and cannot update over each other. Decide once — it cannot be changed later without a key upgrade.

## 2. Before tagging

1. Bump `shared/src/commonMain/kotlin/io/github/mbaliga/clavis/core/ClavisVersion.kt` (`NAME`, and `CODE` to the value `./gradlew :composeApp:clavisVersion` prints for it — `MAJOR·10000 + MINOR·100 + PATCH`; `FsrsTest` fails when the two disagree). The build derives everything else from the version; `preflight` fails a tag that disagrees with `NAME`.
2. `CHANGELOG.md`: move *Unreleased* into `## [X.Y.Z] — YYYY-MM-DD`; every `@Serializable` state change has a *State migration* line, every regenerated pack its version and licence (D-7, D-12); *Known pre-release requirements* keeps the D-18 sentence.
3. `THIRD_PARTY_NOTICES.md` and `NOTICE`: run the review procedure in `THIRD_PARTY_NOTICES.md`; regenerate the packs' `ATTRIBUTION.md` from the pipeline run that produced the shipped packs — never by hand.
4. `README.md`, `PRIVACY.md`, `SECURITY.md`, `docs/STORE-LISTING.md`: still true?
5. Run the D-17 acceptance pass and link the record (TalkBack, RTL, 200 % text, a desktop without CJK/Arabic system fonts, an in-place upgrade over the previous release that keeps the learner state; the API-36 items in §9).
6. Locally green: `./gradlew :shared:allTests :composeApp:desktopTest :composeApp:lintDebug :composeApp:assembleRelease :composeApp:bundleRelease :composeApp:packageDeb`.
7. Commit `release: X.Y.Z`.

## 3. Tag → artifacts

```bash
git tag -a vX.Y.Z -m "Clavis X.Y.Z" && git push origin vX.Y.Z
```

Watch *Actions → Release*. Pre-release tags (`vX.Y.Z-rc.1`) are marked pre-release and are not "latest". Start Play's closed test with the first `-beta`/`-rc` tag, not the first stable one (see §6).

## 4. Verify — on your machine, not the runner

```bash
sha256sum -c SHA256SUMS.txt --ignore-missing
apksigner verify --verbose --print-certs clavis-X.Y.Z-android.apk     # fingerprint == the one recorded in §1.1
java -jar clavis-X.Y.Z-linux-x64.jar                                   # the app shows X.Y.Z
sudo apt install ./clavis_X.Y.Z_amd64.deb && clavis
# macOS: open the dmg; an unsigned one needs the §7 steps once.  Windows: install the msi over the previous version.
```

Record (the Fylz convention): commit SHA and version; SHA-256 of every asset (the `SHA256SUMS.txt`); signing-certificate SHA-256 fingerprint; the workflow run URL; the two dependency graphs reviewed; the acceptance record.

## 5. Versioning

One input, four derived numbers. `gradle/version.gradle.kts` reads `-Pclavis.version=` > `CLAVIS_VERSION` > `ClavisVersion.NAME` (the fallback for local builds) and validates `MAJOR.MINOR.PATCH[-alpha.N|-beta.N|-rc.N]` with N in 1..29 and MINOR, PATCH in 0..99. `./gradlew :composeApp:clavisVersion` prints the result; the release workflow sets `CLAVIS_VERSION` from the tag.

| Output | Rule | 0.1.0 | 0.2.0-rc.1 | 1.4.7-beta.3 |
|---|---|---|---|---|
| Android `versionName` | the input, verbatim | `0.1.0` | `0.2.0-rc.1` | `1.4.7-beta.3` |
| Android `versionCode` | `MAJOR·10000 + MINOR·100 + PATCH` | 100 | 200 | 10407 |
| uber-jar name / jpackage default | `MAJOR.MINOR.PATCH` | `0.1.0` | `0.2.0` | `1.4.7` |
| `.deb` version | `MAJOR.MINOR.PATCH[~pre.N]-1` (`~` sorts before the final, Debian policy) | `0.1.0-1` | `0.2.0~rc.1-1` | `1.4.7~beta.3-1` |
| `.msi` ProductVersion | `MAJOR.MINOR.(PATCH·100 + PRE)`, PRE: alpha.N → N, beta.N → 30+N, rc.N → 60+N, final → 99 | `0.1.99` | `0.2.61` | `1.4.733` |
| `.dmg` CFBundleShortVersionString | `(MAJOR == 0 ? 1 : MAJOR).MINOR.PATCH` | `1.1.0` | `1.2.0` | `1.4.7` |

Why three desktop mappings instead of one: jpackage on macOS rejects a leading 0 ("The first number in an app-version cannot be zero or negative", `MacResources.properties`), so 0.x is shifted to 1.x there — cosmetic, because a DMG is a drag-install with no upgrade semantics, and the shift disappears at 1.0.0. Windows allows MAJOR 0 (`[0, 255]`, `WinResources.properties`) but compares `ProductVersion` numerically for upgrades under the fixed `upgradeUuid` (`1b0a9429-3b8e-45a2-9f95-a27591135e75` — never change it), and jpackage's `main.wxs` treats an *equal* version as neither upgrade nor downgrade, i.e. a second copy; encoding the pre-release into BUILD keeps `rc.1 < final` strictly at the cost of Add/Remove Programs showing `0.1.99` for 0.1.0 while the app itself shows the true version. Debian already has `~` for this. The Android `versionCode` follows the brief's scheme; consequences: a pre-release tag shares its final's code, and Play requires a strictly increasing code per upload, so upload **at most one build per X.Y.Z** to Play (the last `-rc` goes to the closed track, the final replaces it only if the rc was never uploaded — otherwise bump PATCH). GitHub Releases have no such constraint.

Non-tag builds (`CLAVIS_VERSION` unset) carry `ClavisVersion.NAME`; CI's debug APK has its own application id (`.debug`) and is never uploaded anywhere.

## 6. Google Play

- *Testing → Closed testing*: upload `clavis-X.Y.Z-android.aab` (signed). Personal developer accounts created after 13 November 2023 must run a closed test with at least 12 testers opted in for 14 days before applying for production.
- *App content → Data safety*: "Does your app collect or share any of the required user data types?" → **No**. Nothing is transmitted; learner state, review log and settings stay on the device; no accounts; crash logs are not collected (crash recovery is local-only, D-11/D-13). Recording downloads are user-initiated fetches of public files over HTTPS and the answer stays **No**; the manifest declares `INTERNET` for exactly that path (packs and voices will use it when they ship). Deletion: uninstall or Settings → Reset everything.
- Other declarations: Ads → none; App access → no restricted access; Content rating questionnaire (IARC, Education/Reference); Target audience → 18 and over (Clavis is not for children, docs/04-personas.md); News, Government, Financial, Health → no; Privacy policy URL → the raw `PRIVACY.md` of the tagged commit (recommended even though not required for a no-data app).
- Store listing: copy from `docs/STORE-LISTING.md` only — it is written to D-18 (N=1 stated up front, no forbidden vocabulary). Assets: icon 512 × 512 PNG with alpha (≤ 1 MB), feature graphic 1024 × 500 JPEG/PNG without alpha, ≥ 2 (aim for 4+) phone screenshots and 4+ tablet screenshots, 320–3840 px. The R8 mapping is read from the AAB automatically.
- Target API: Play requires `targetSdk` 36 for new apps and updates since 2026-08-31 (extension possible to 2026-11-01). The build compiles against and targets API 36 (`gradle/libs.versions.toml`, bumped 2026-09-02); the API-36 device pass in §9 is still open.
- Promote to production only after the closed-test window and the acceptance pass.

## 7. Unsigned desktop installers, and signing later

**macOS (unsigned DMG).** Since macOS Sequoia (15) Control-click → Open no longer bypasses Gatekeeper (Apple, 2024-08-06). Users: drag Clavis to Applications, double-click once, click **Done** (not Move to Trash), open **System Settings → Privacy & Security**, scroll down, click **Open Anyway**, confirm **Open**. macOS remembers the choice. Terminal alternative (not Apple-documented): `xattr -d com.apple.quarantine /Applications/Clavis.app`. Always verify `SHA256SUMS.txt` first. To sign later: join the Apple Developer Program, create the Developer ID Application certificate, add the §1.2 secrets — the workflow already signs, notarizes and staples; nothing else changes.

**Windows (unsigned MSI).** SmartScreen shows "Windows protected your PC" → **More info** → **Run anyway**. To sign later: an OV/EV code-signing certificate with `signtool sign /fd SHA256 /tr <timestamp-url> /td SHA256`, Azure Trusted Signing, or SignPath (free for open source); add a step after `packageMsi` in the `windows` job and drop the "never code-signed" wording here and in the release body.

**Linux.** The `.deb` is not signed either (no apt repository); the checksum file is the verification path.

## 8. Workflows: policies worth knowing before editing

- Actions are pinned to **major tags** of release lines that run on Node 24 (GitHub removes Node 20 from hosted runners on 2026-09-23): `checkout@v7`, `setup-java@v6`, `gradle/actions/setup-gradle@v6`, `upload-artifact@v7`, `download-artifact@v8`, `setup-android@v4`, `setup-python@v7`, `softprops/action-gh-release@v3`. Dependabot proposes bumps monthly. Full-SHA pinning is stricter and was not adopted for a single-maintainer repo; if the repo gains contributors with write access, switch and keep the version in a trailing comment.
- Runners are pinned images (`ubuntu-24.04`, `macos-15`, `windows-2025`), never `-latest`; image jumps are deliberate. macOS x64 is not built (no `macos-15-intel` job); add one if a user needs it.
- The Android SDK numbers appear in three places: `gradle/libs.versions.toml`, the `setup-android` `packages:` input and the `build-tools/36.0.0` path in both workflows. Bump them together.
- Secrets reach Gradle only through `env:` on the steps that need them; `secrets` cannot be used in `if:`, so presence is converted into a step output first. Never run the signing steps with `--info`/`--debug`.
- `-Dorg.gradle.jvmargs` is passed on the command line per job (3 GB on Linux/Windows, 2 GB plus a 1.5 GB Kotlin daemon on the 7 GB macOS runner); command-line values beat `gradle.properties`.
- Validate edits with `actionlint` and `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"` before pushing.

## 9. Open items before the first stable tag

- **API-36 device pass** (`compileSdk`/`targetSdk` are 36 since 2026-09-02, both workflows install `platforms;android-36` and `build-tools;36.0.0`, `docs/build-platform.md` §5 records the green build): test on an API 36 emulator — predictive back through the navigation (a `PredictiveBackHandler` now covers modal states in code; the actual swipe-preview animation still needs confirming on-device), edge-to-edge insets, Arabic line height (`elegantTextHeight` is ignored from 36), TalkBack, RTL, 200 % font scale, ≥ 600 dp widths. This is the item the `0.1.0-rc.1` tag is standing in for: no emulator/KVM is available in the environment that prepared this release candidate, so none of the above has actually been exercised on a device yet.
- **Font content, not just the notice**: `THIRD_PARTY_NOTICES.md` now matches what the bundled `.ttf` files' own `name` tables say (verified by extracting them directly) — but that extraction also surfaced that the Hebrew/Japanese/Korean/Chinese files are the **Thin** (weight 100) static instance, not Regular, so the un-weighted `Font()` calls in `Platform.desktop.kt` render Thin-weight fallback glyphs for those scripts. Decide whether Thin was intentional or re-fetch the Regular-weight statics from upstream and re-verify rendering.
- **Name**: "Clavis" is a working name; only a preliminary web search has been done, not a cleared trademark check — that precedes the first store listing.

## 10. Rollback

Keep the previous signed release and its record. If a release loses learner state (a D-12 migration fault), ships wrong attribution (D-7) or makes any network request without a user action (D-13): halt the rollout in Play, mark the GitHub Release as pre-release with a note, and publish a patch tag from a fixed commit. Preserve the evidence needed to reproduce the fault before rebuilding.
