# Build platform

The toolchain the Kotlin Multiplatform skeleton was verified with, why each pin is what it is, and
how to build and test every target. Decisions that this document *implements* are recorded in
`DECISIONS.md` (D-2, D-3); this file holds the facts a build engineer needs.

Verified on 2026-09-01 on Linux x86-64 (4 CPU / 15 GB), Android SDK at `/opt/android-sdk`.

## 1. Version matrix

| Component | Version | Why / source |
|---|---|---|
| Gradle (wrapper, `bin`) | **8.14.3** | Constellation lockstep: AGP 8.9.1's floor (`Shared-Libraries-asoc/README.md`, "The AGP lockstep"). |
| Android Gradle Plugin | **8.9.1** | Constellation lockstep; a composite build with `Shared-Libraries-asoc` / `Hyle-Design-System` hard-fails on mixed AGP versions. |
| Kotlin (KGP, `kotlin-test`, serialization plugin, `org.jetbrains.kotlin.plugin.compose`) | **2.1.20** | Same line as `Fyl-Manager`; D-2. KGP 2.1.20's *tested* ranges are Gradle 7.6.3–8.12.1 and AGP 7.3.1–8.7.2 ([kotlinlang.org/docs/gradle-configure-project.html](https://kotlinlang.org/docs/gradle-configure-project.html)); the same page says newer Gradle/AGP may be used with at most deprecation warnings. Observed: **zero** warnings with `--warning-mode all` (see §7). |
| Compose Multiplatform | **1.8.2** | Maps to Jetpack Compose 1.8.2; requires Kotlin ≥ 2.1.0 and is K2-only ([compose-compatibility-and-versioning](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)). Release notes ship lifecycle 2.9.1 and navigation 2.9.0-beta03 with it. |
| Android SDK | compileSdk **35**, targetSdk **35**, minSdk **26**, build-tools 35.0.0 | What is installed here and what the brief asked for; D-2 in the build plan lists 36 for `Fyl-Manager` but Clavis has no dependency that needs it yet. |
| JDK toolchain | **17** (`kotlin { jvmToolchain(17) }` in both modules) | Constellation's documented toolchain. See §2. |
| kotlinx-serialization | 1.8.1 | Built against Kotlin 2.1.x; 1.9.0 is built with 2.2.0 and is the pair for a Kotlin 2.2 move. |
| kotlinx-coroutines | 1.10.2 | Current for Kotlin 2.1.x. `-swing` is on the desktop main classpath for `Dispatchers.Main`. |
| kotlinx-datetime | 0.6.2 | 0.7.x switches `Instant` to `kotlin.time.Instant` (still experimental on 2.1.20); defer. |
| okio | 3.10.2 | Last line built with Kotlin 2.1. |
| androidx.activity:activity-compose | 1.10.1 | Same as `Fyl-Manager`; provides `enableEdgeToEdge`. |
| org.jetbrains.androidx.lifecycle (`viewmodel-compose`, `runtime-compose`) | 2.9.1 | The version CMP 1.8.2 was released with. |
| org.jetbrains.androidx.navigation:navigation-compose | 2.9.0-beta03 | The build named in CMP 1.8.2's release notes. **beta04 was tried first and rejected**: it was cut against CMP 1.9.0-beta01 and pulled `compose.runtime`, `ui-backhandler`, `ui-util` 1.9.0-beta01 (and `androidx.compose.runtime` 1.9.0-beta02) into the desktop classpath. Stable 2.9.0 depends on CMP 1.9.0 — bump only together with CMP. |
| org.jetbrains.compose.material:material-icons-core | 1.7.3 | Icons are published on their own cadence; `material3` itself comes from the `compose.material3` accessor (version-locked to CMP). |
| com.russhwolf:multiplatform-settings | 1.3.0 | Kotlin 2.1-built. |
| junit | 4.13.2 | Catalog entry only; `kotlin("test")` selects the JUnit 4 runner for JVM tests automatically. |

All plugin/library POMs above were confirmed present on Maven Central / Google Maven (`curl -I`)
before use, and the full chain resolved them.

### The newer combination that was considered

Kotlin **2.2.20/2.2.21 + Compose Multiplatform 1.9.3** exists on Maven Central and is, per the
KGP table, *fully inside* the tested range for both Gradle 8.14 and AGP 8.9.1 (KGP 2.2.20–2.2.21:
Gradle 7.6.3–8.14, AGP 7.3.1–8.11.1). CMP 1.9.3 ships stable navigation 2.9.1 and lifecycle 2.9.6.
It was **not** adopted because (a) D-2 and the rest of the constellation are on Kotlin 2.1.x, and
(b) the chosen pair already builds with zero warnings, so there was no warning-count reason to
move. Upgrade recipe when the constellation moves: bump `kotlin` → 2.2.2x, `compose-multiplatform`
→ 1.9.3, `androidx-lifecycle` → 2.9.6, `androidx-navigation` → 2.9.1, `kotlinx-serialization` →
1.9.0, `kotlinx-datetime` → 0.7.1, `okio` → 3.15.0, then re-run §5.

## 2. JDK situation

- The machine has only JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`); the Gradle daemon runs on it
  (AGP 8.9 needs ≥ 17, so that is fine).
- Both modules declare `kotlin { jvmToolchain(17) }`. `settings.gradle.kts` applies
  `org.gradle.toolchains.foojay-resolver-convention` 1.0.0, so Gradle auto-provisioned **Eclipse
  Adoptium 17.0.20.1** into `~/.gradle/jdks/eclipse_adoptium-17-amd64-linux.2` on first run.
  Kotlin/Java compilation, all test JVMs and jpackage therefore run on 17, matching CI
  (`actions/setup-java` temurin 17, where the resolver is a no-op).
- `android { compileOptions { 17 } }` is set explicitly as well so the Android Java tasks agree with
  the toolchain even if someone removes it.
- The Compose Desktop plugin does **not** read the Kotlin toolchain; by default it runs
  jlink/jpackage with the daemon's JDK (observed: `/usr/lib/jvm/java-21-openjdk-amd64/bin/jpackage`
  on the first `packageDeb`). `composeApp/build.gradle.kts` therefore sets
  `compose.desktop.application.javaHome` from `javaToolchains.launcherFor(17)`, so the runtime
  image bundled into .deb/.dmg/.msi is JDK 17 on every machine.
- Fallback if provisioning is impossible (air-gapped box): drop `jvmToolchain(17)`, set
  `compilerOptions { jvmTarget = JVM_17 }` on both targets and keep `compileOptions` at 17; the
  build then runs on whatever JDK ≥ 17 is present.

## 3. `gradle.properties` rationale

| Property | Value | Why |
|---|---|---|
| `org.gradle.jvmargs` | `-Xmx3g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8` | Shared 15 GB box; daemon + Kotlin daemon + workers must stay under ~7 GB. |
| `kotlin.daemon.jvmargs` | `-Xmx2g` | Same budget. |
| `org.gradle.workers.max` | `2` | 4 CPUs shared with other processes. |
| `org.gradle.parallel` / `org.gradle.caching` | `true` | Two independent modules; build cache makes test re-runs cheap. |
| `org.gradle.configuration-cache` | **`false`** | With KGP 2.1.20 + CMP 1.8.2 the entry was discarded with a serialization error on `:shared:compileKotlinDesktop` (seen while a dependency resolution failed), and under CC AGP 8.9.1 emits three Gradle-9 "is-property" deprecations. Off until a full green chain stores its entry; then try `org.gradle.configuration-cache.problems=warn` first. |
| `android.useAndroidX`, `android.nonTransitiveRClass` | `true` | Constellation convention. |
| `kotlin.code.style` | `official` | Constellation convention. |
| `compose.desktop.verbose` | `false` | Quiet jpackage/ProGuard output. |

## 4. Module layout

```
Clavis/
├─ settings.gradle.kts            google() + mavenCentral() (+ opt-in mirrors, §8); foojay resolver
├─ build.gradle.kts               plugin aliases, apply false
├─ gradle/libs.versions.toml      every version in one place
├─ shared/                        KMP library  — androidTarget + jvm("desktop")
│  └─ src/commonMain/kotlin/io/github/mbaliga/clavis/core/
│        ClavisVersion.kt  Fsrs.kt (retrievability)  Platform.kt (expect platformName)
│     src/androidMain/…/Platform.android.kt   src/desktopMain/…/Platform.desktop.kt
│     src/commonTest/…/FsrsTest.kt             (5 tests; run on desktop + Android debug/release)
│     namespace io.github.mbaliga.clavis.shared
└─ composeApp/                    KMP application — androidTarget + jvm("desktop")
   ├─ src/commonMain/kotlin/io/github/mbaliga/clavis/App.kt          root composable
   │                       …/ui/theme/ClavisTokens.kt, ClavisTheme.kt  palette + M3 mapping
   ├─ src/commonMain/composeResources/values/strings.xml            → Res.string.*
   ├─ src/androidMain/  MainActivity.kt, AndroidManifest.xml, res/ (adaptive icon, themes)
   ├─ src/desktopMain/  main.kt (application { Window { App() } })
   ├─ src/desktopTest/  AppTest.kt (headless Compose UI test)
   └─ proguard-rules.pro (kotlinx-serialization keep rules)
```

- Packages: domain `io.github.mbaliga.clavis.core`; UI `io.github.mbaliga.clavis` and
  `io.github.mbaliga.clavis.ui.theme`; generated resources `io.github.mbaliga.clavis.resources.Res`
  (configured via `compose.resources { packageOfResClass }`; generated under
  `composeApp/build/generated/compose/resourceGenerator/kotlin/`).
- Android ids: applicationId `io.github.mbaliga.clavis`; debug adds `.debug`. `versionCode` /
  `versionName` and the desktop package versions are derived by `gradle/version.gradle.kts` from
  `-Pclavis.version` / `CLAVIS_VERSION` / `ClavisVersion.NAME` (0.1.0 → versionCode 100; the literals
  left in `composeApp/build.gradle.kts` are overridden fallbacks). Scheme and per-OS mapping:
  `docs/RELEASE.md` §5; `./gradlew :composeApp:clavisVersion` prints the resolved values.
- Desktop: main class `io.github.mbaliga.clavis.MainKt`. jpackage on macOS rejects a leading 0
  (MAJOR must be > 0) and Windows needs numeric `x.y.z` with x,y ≤ 255, z ≤ 65535 — hence the
  per-OS package versions (deb `0.1.0-1`, msi `0.1.99`, dmg `1.1.0` for app 0.1.0; RELEASE.md §5).
  MSI `upgradeUuid` is fixed to `1b0a9429-3b8e-45a2-9f95-a27591135e75`; never change it.
- Release signing is opt-in through `composeApp/keystore.properties` or the `CLAVIS_KEYSTORE_*`
  environment (`gradle/release-signing.gradle.kts`); absent, `assembleRelease` stays unsigned.
- No extra jlink modules are configured. If a runtime `ClassNotFoundException` appears in a
  packaged app, run `./gradlew :composeApp:suggestRuntimeModules` and add the result to
  `nativeDistributions { modules(...) }`.

## 5. Building and testing (dev machine)

Always `export ANDROID_HOME=/opt/android-sdk` (or have `local.properties` with `sdk.dir`).

| Goal | Command | Output (verified) |
|---|---|---|
| Wrapper version | `./gradlew --version` | Gradle 8.14.3 |
| Domain tests, all targets | `./gradlew :shared:allTests` | runs `desktopTest`, `testDebugUnitTest`, `testReleaseUnitTest` (5 tests each) |
| Desktop UI tests (headless) | `./gradlew :composeApp:desktopTest` | 2 tests, `composeApp/build/test-results/desktopTest/` |
| Debug APK | `./gradlew :composeApp:assembleDebug` | `composeApp/build/outputs/apk/debug/composeApp-debug.apk`, 14,133,452 B; badging: `io.github.mbaliga.clavis.debug` versionCode 1 / 0.1.0, minSdk 26, targetSdk 35, label Clavis, launchable `io.github.mbaliga.clavis.MainActivity` |
| Release APK (unsigned, R8 + shrinkResources) | `./gradlew :composeApp:assembleRelease` | `composeApp/build/outputs/apk/release/composeApp-release-unsigned.apk`, 781,584 B; mapping in `composeApp/build/outputs/mapping/release/` |
| Run desktop app | `./gradlew :composeApp:run` | needs a display |
| Desktop uber-jar | `./gradlew :composeApp:packageUberJarForCurrentOS` | `composeApp/build/compose/jars/Clavis-linux-x64-1.0.0.jar`, 38,146,346 B; `java -jar …` |
| Linux .deb | `./gradlew :composeApp:packageDeb` | `composeApp/build/compose/binaries/main/deb/clavis_1.0.0-1_amd64.deb`, 54,620,478 B (Package `clavis`, Section Education, installs `/opt/clavis/bin/Clavis` + `.desktop`; bundled runtime `JAVA_VERSION="17.0.20.1"`) |
| macOS .dmg / Windows .msi | `packageDmg` / `packageMsi` on that OS | not run here |

Check the badging of any APK with
`/opt/android-sdk/build-tools/35.0.0/aapt2 dump badging <apk> | head`.

Linux packaging prerequisites: jpackage's DEB bundler shells out to **`fakeroot`** as well as
`dpkg-deb`. Without it the task fails with
`Bundler DEB Bundle skipped because of a configuration problem: Can not find fakeroot`
(`composeApp/build/compose/logs/packageDeb/jpackage-*-err.txt`). `apt-get install fakeroot`
(on this proxy-restricted box the two `.deb`s were fetched over HTTPS from
`mirrors.edge.kernel.org/ubuntu/pool/main/f/fakeroot/` and installed with `dpkg -i`).
`packageRpm` would additionally need `rpmbuild`.

## 6. Headless UI tests — the exact incantation

```
export ANDROID_HOME=/opt/android-sdk
./gradlew :composeApp:desktopTest
```

That is all. `runComposeUiTest` on desktop renders into an offscreen Skia surface; the run above
passed with `DISPLAY` and `WAYLAND_DISPLAY` unset, without `xvfb-run` and without
`-Djava.awt.headless=true`. Only if a future test touches real AWT windows/toolkit (a `Window`,
`Dialog`, clipboard, `GraphicsEnvironment`) does it need a display — then use
`xvfb-run -a ./gradlew :composeApp:desktopTest` (Xvfb is at `/usr/bin/Xvfb` here).

Note the difference from *running* the app: `java -Djava.awt.headless=true -jar Clavis-….jar`
loads every class and starts composing, then throws `java.awt.HeadlessException` from
`LayoutConfiguration.getGlobalDensity` when it creates the window. That is expected and is the
smoke test for the jar.

## 7. Known warnings and what was done

Full chain (`help`, `:shared:allTests`, `:composeApp:desktopTest`, `assembleDebug`,
`packageUberJarForCurrentOS`, `assembleRelease`, `packageDeb`) with `--warning-mode all`:

- Kotlin compiler warnings (`w:`): **none**.
- Gradle deprecation warnings: **none** with configuration cache off on a warm daemon. With it on,
  AGP 8.9.1 emits three "Declaring an 'is-' property with a Boolean type has been deprecated"
  (Gradle 9.0) notices for `isCrunchPngs`, `isUseProguard`, `isWearAppUnbundled` — AGP's to fix,
  harmless. Correction (2026-09-02): the same three notices also print on a **cold JVM**
  (`--no-daemon`, as CI runs) with configuration cache off — they come from Gradle generating AGP's
  decorated DSL classes, which a warm daemon caches; verified identical (3 and 3) with and without the
  versioning/signing blocks in `composeApp/build.gradle.kts`, so they are not ours.
- KGP ↔ AGP / Gradle compatibility warning: **none printed**, although KGP 2.1.20 is above its
  tested AGP (8.7.2) and Gradle (8.12.1) ranges. D-2 anticipated a warning; there is none.
- R8: `missing_rules.txt` was not produced — the serialization rules in `proguard-rules.pro` plus
  the libraries' consumer rules were sufficient.
- `aapt2 dump badging` shows `uses-permission …DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: injected
  by AGP for every app; not a declared permission.
- Configuration cache: see §3; disabled pending a clean serialisation.
- Dependency graph (`./gradlew :composeApp:dependencies --configuration desktopRuntimeClasspath`):
  every `org.jetbrains.compose.*` artifact resolves to 1.8.2 (after the navigation beta03 fix
  above). `kotlin-stdlib` resolves `2.1.20 -> 2.1.21` because CMP 1.8.2's own artifacts were
  built with Kotlin 2.1.21; a same-minor patch bump of the stdlib is supported by the 2.1.20
  compiler and was left alone (forcing it down, as `Fyl-Manager` does, is possible but not needed).

## 8. Working behind a rate-limited proxy

This machine reaches the internet through a shared proxy whose egress IP Maven Central
(`repo.maven.apache.org` / `repo1.maven.org`) rate-limits with **HTTP 429**. Gradle does not fall
through to the next repository on a 429 (only on 404), so builds fail at random artifacts and
retries do not reliably help.

`settings.gradle.kts` therefore has an **opt-in** block in both `pluginManagement.repositories`
and `dependencyResolutionManagement.repositories`, active only when the Gradle property
`clavis.mirror` is present:

```
# ~/.gradle/gradle.properties   (machine-local; NOT in the repo, NOT in CI)
clavis.mirror=true
```

It inserts, ahead of `mavenCentral()` / `gradlePluginPortal()`:

- `https://cache-redirector.jetbrains.com/maven-central`
- `https://maven.aliyun.com/repository/central`
- `https://cache-redirector.jetbrains.com/plugins.gradle.org/m2` (plugin management only)

All three were checked to serve the plugin markers and artifacts this build needs. Without the
property the repository list is the conventional `google()`, `mavenCentral()`,
`gradlePluginPortal()`, which is what CI uses. Once a full online run has succeeded, resolution can
be re-checked offline with
`./gradlew :composeApp:dependencies --configuration desktopRuntimeClasspath --offline`
(verified here after the online chain: `BUILD SUCCESSFUL`, zero `FAILED` entries; note the task
itself exits 0 even when entries are `FAILED`, so grep the report rather than trusting the exit
code).

If 429s still appear, add to `gradle.properties` for the download phase only:
`systemProp.org.gradle.internal.repository.max.retries=8`,
`systemProp.org.gradle.internal.repository.initial.backoff=2000`,
`systemProp.org.gradle.internal.http.connectionTimeout=120000`, and `org.gradle.workers.max=1`.

## 9. CI and release workflows

`.github/workflows/ci.yml` (push to `main`, pull requests): `ubuntu-24.04`, `actions/checkout@v7`,
`actions/setup-java@v6` (temurin 17), `android-actions/setup-android@v4`
(`platforms;android-35 build-tools;35.0.0 platform-tools`), `gradle/actions/setup-gradle@v6`, then

```
./gradlew :shared:allTests :composeApp:desktopTest :composeApp:lintDebug :composeApp:assembleDebug \
          :composeApp:assembleRelease :composeApp:packageUberJarForCurrentOS --no-daemon --stacktrace
```

plus a 16 KB page-alignment check on the release APK, a headless smoke run of the uber-jar, and a
second job that installs `pipeline/requirements.txt` and runs `pytest pipeline/tests`. Artifacts:
`clavis-debug-apk`, `clavis-release-apk-unsigned` (with `mapping.txt`), `clavis-desktop-jar-linux-x64`,
and `reports` on failure. Actions are pinned to major tags of Node-24 release lines (the policy is in
the file header; Node 20 leaves the runners on 2026-09-23).

`.github/workflows/release.yml` (tag `v*`, or manual dry run) builds the Android APK/AAB, the Linux
`.deb` + jar, the macOS `.dmg` + jar (macos-15, optional Developer ID signing and notarization), the
Windows `.msi` + jar (windows-2025, WiX 3 preinstalled) and the reference web build, then publishes one
GitHub Release with `SHA256SUMS.txt`. Procedure, secrets and artifact names: `docs/RELEASE.md`.

Verified 2026-09-02 on this machine after the versioning/signing change: `assembleRelease`,
`bundleRelease`, `lintDebug`, `desktopTest`, `packageDeb` (`clavis_0.1.0-1_amd64.deb`) and
`packageUberJarForCurrentOS` (`Clavis-linux-x64-0.1.0.jar`) green; `apksigner verify` passes on a
build signed from a throwaway keystore via both configuration paths.
