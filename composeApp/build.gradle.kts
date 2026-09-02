import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// --- versioning (release) ---
// Single source of truth for versionName / versionCode / desktop package versions: gradle/version.gradle.kts
// (-Pclavis.version= > CLAVIS_VERSION env > ClavisVersion.NAME in :shared). `./gradlew :composeApp:clavisVersion`
// prints the resolved values; the scheme is documented in docs/RELEASE.md. The literal versionCode/versionName
// and packageVersion further down remain as the documented fallbacks and are overridden from here, so this block
// and the "release signing (release)" block inside android {} are the only lines a release engineer edits.
apply(from = rootProject.file("gradle/version.gradle.kts"))
val clavisVersionCode: Int by extra
val clavisVersionName: String by extra
val clavisBaseVersion: String by extra
val clavisDebVersion: String by extra
val clavisMsiVersion: String by extra
val clavisDmgVersion: String by extra
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(clavisVersionCode)
            output.versionName.set(clavisVersionName)
        }
    }
}
// The Compose Desktop plugin reads nativeDistributions through providers when its packaging tasks are
// realised, so applying the per-OS versions after the script body (i.e. after the literal packageVersion
// below) is what keeps this block self-contained. Verified by the .deb / uber-jar names (docs/RELEASE.md).
afterEvaluate {
    compose.desktop.application.nativeDistributions {
        packageVersion = clavisBaseVersion
        linux { debPackageVersion = clavisDebVersion }
        windows {
            msiPackageVersion = clavisMsiVersion
            exePackageVersion = clavisMsiVersion
        }
        macOS {
            dmgPackageVersion = clavisDmgVersion
            pkgPackageVersion = clavisDmgVersion
        }
    }
}
// --- end versioning (release) ---

kotlin {
    // JDK 17 is the constellation's documented toolchain (auto-provisioned via foojay if absent).
    jvmToolchain(17)

    androidTarget()
    jvm("desktop")

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting

        commonMain.dependencies {
            implementation(project(":shared"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.compose.material.icons.core)

            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // `LocalDayKey`'s constructor exposes kotlinx-datetime's TimeZone in its signature.
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            // MP3 / OGG decoding for downloaded recordings (javax.sound only knows WAV). The decoders are
            // instantiated explicitly (no ServiceLoader), so the uber-jar's duplicate META-INF/services
            // handling cannot silently drop one of them.
            implementation(libs.mp3spi)
            implementation(libs.vorbisspi)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        desktopTest.dependencies {
            // Headless Compose UI tests (runComposeUiTest). Kept desktop-only: on Android the same
            // artifact needs an instrumented device or Robolectric.
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

android {
    namespace = "io.github.mbaliga.clavis"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.mbaliga.clavis"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Keep in step with io.github.mbaliga.clavis.core.ClavisVersion.
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // --- release signing (release) ---
    // Opt-in only; nothing here runs on a plain checkout. gradle/release-signing.gradle.kts resolves the material
    // from composeApp/keystore.properties (gitignored) or from CLAVIS_KEYSTORE_BASE64 + CLAVIS_KEYSTORE_PASSWORD +
    // CLAVIS_KEY_ALIAS + CLAVIS_KEY_PASSWORD, and publishes an empty map when neither is complete. Then the release
    // build stays unsigned: AGP emits composeApp-release-unsigned.apk, the .aab is unsigned, and CI labels both
    // "-unsigned". Key generation, secret names and verification: docs/RELEASE.md.
    project.apply(from = rootProject.file("gradle/release-signing.gradle.kts"))
    val clavisReleaseSigning: Map<String, String> by project.extra
    if (clavisReleaseSigning.isNotEmpty()) {
        signingConfigs.create("release") {
            storeFile = file(clavisReleaseSigning.getValue("storeFile"))
            storePassword = clavisReleaseSigning.getValue("storePassword")
            keyAlias = clavisReleaseSigning.getValue("keyAlias")
            keyPassword = clavisReleaseSigning.getValue("keyPassword")
            // minSdk 26: AGP's defaults (v1 + v2, v3/v4 added automatically) are right; nothing to override.
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
        logger.lifecycle("Clavis: release signing enabled from ${clavisReleaseSigning.getValue("source")}")
    } else {
        logger.lifecycle("Clavis: no release signing configured; release outputs will be unsigned")
    }
    // --- end release signing (release) ---
}

dependencies {
    debugImplementation(compose.uiTooling)
}

// Headless desktop UI tests load the real 20 MB packs and 40 MB of fonts and render screenshots
// into <root>/build/ui-shots (see ScreenshotTest); give the test JVM room and keep AWT headless.
tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    jvmArgs("-Djava.awt.headless=true")
    systemProperty("clavis.shots", rootProject.layout.buildDirectory.dir("ui-shots").get().asFile.absolutePath)
    testLogging { events("failed"); showStackTraces = true; showStandardStreams = false; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

compose.resources {
    // Generated accessor: io.github.mbaliga.clavis.resources.Res (strings, drawables, fonts, files
    // dropped under composeApp/src/commonMain/composeResources/).
    publicResClass = false
    packageOfResClass = "io.github.mbaliga.clavis.resources"
    generateResClass = always
}

// The Compose Desktop plugin runs jlink/jpackage with the JDK the Gradle daemon runs on, not the
// Kotlin toolchain. Point it at the same JDK 17 so the bundled runtime image is 17 everywhere.
val desktopJdk17 = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}

compose.desktop {
    application {
        mainClass = "io.github.mbaliga.clavis.MainKt"
        javaHome = desktopJdk17.get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Clavis"
            // jpackage on macOS rejects a leading 0 in the version (MAJOR must be > 0), so the
            // desktop package version is decoupled from the app's 0.x versionName for now.
            packageVersion = "1.0.0"
            description = "Nine languages, one lexicon: offline-first language learning from parallel comics."
            vendor = "mbaliga"
            copyright = "Copyright 2026 mbaliga. App code licensed separately from the CC BY-SA data pack."

            // Brand icons (scratchpad/brand → src/desktopMain/resources/icons): one file per OS.
            linux {
                packageName = "clavis"
                debMaintainer = "mbaliga@users.noreply.github.com"
                menuGroup = "Education"
                appCategory = "Education"
                iconFile.set(project.file("src/desktopMain/resources/icons/clavis.png"))
            }
            macOS {
                bundleID = "io.github.mbaliga.clavis"
                dockName = "Clavis"
                iconFile.set(project.file("src/desktopMain/resources/icons/clavis.icns"))
            }
            windows {
                menuGroup = "Clavis"
                // Fixed for the life of the product so MSI upgrades replace instead of duplicate.
                upgradeUuid = "1b0a9429-3b8e-45a2-9f95-a27591135e75"
                menu = true
                shortcut = true
                iconFile.set(project.file("src/desktopMain/resources/icons/clavis.ico"))
            }
        }
    }
}
