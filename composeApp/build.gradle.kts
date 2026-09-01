import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

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
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
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
}

dependencies {
    debugImplementation(compose.uiTooling)
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

            linux {
                packageName = "clavis"
                debMaintainer = "mbaliga@users.noreply.github.com"
                menuGroup = "Education"
                appCategory = "Education"
            }
            macOS {
                bundleID = "io.github.mbaliga.clavis"
                dockName = "Clavis"
            }
            windows {
                menuGroup = "Clavis"
                // Fixed for the life of the product so MSI upgrades replace instead of duplicate.
                upgradeUuid = "1b0a9429-3b8e-45a2-9f95-a27591135e75"
                menu = true
                shortcut = true
            }
        }
    }
}
