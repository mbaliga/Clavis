plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // JDK 17 is the constellation's documented toolchain. Gradle provisions it via the foojay
    // resolver (settings.gradle.kts) when it is not installed locally.
    jvmToolchain(17)

    androidTarget()
    jvm("desktop")
    // iOS (docs/PORTING.md 6.1): device + Apple-silicon simulator, no Intel simulator. These targets are only
    // buildable on a macOS host; on Linux Gradle registers them and skips them, so the JVM gate is unchanged.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "io.github.mbaliga.clavis.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
