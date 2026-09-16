pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Opt-in mirrors for machines whose shared proxy egress is rate-limited by Maven Central
        // (HTTP 429, and Gradle does not fall through to the next repository on a 429). Enabled by
        // `clavis.mirror=true` in ~/.gradle/gradle.properties; never set in CI. docs/build-platform.md.
        if (providers.gradleProperty("clavis.mirror").isPresent) {
            maven("https://cache-redirector.jetbrains.com/maven-central")
            maven("https://maven.aliyun.com/repository/central")
            maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Auto-provisions the JDK named by `kotlin { jvmToolchain(17) }` when the machine does not
    // already have one (the constellation documents JDK 17 as its toolchain; CI installs it via
    // actions/setup-java, so the resolver is a no-op there).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Same opt-in Maven Central mirrors as in pluginManagement above (429 rate-limiting through
        // a shared proxy; enabled only by the `clavis.mirror` Gradle property).
        if (providers.gradleProperty("clavis.mirror").isPresent) {
            maven("https://cache-redirector.jetbrains.com/maven-central")
            maven("https://maven.aliyun.com/repository/central")
        }
        mavenCentral()
    }
}

rootProject.name = "Clavis"
include(":shared")
include(":composeApp")

// AGP is pinned to 8.9.1 (gradle/libs.versions.toml) because the constellation's shared
// libraries (Shared-Libraries-asoc, hyle-design-system) are pulled in as composite builds and
// Gradle refuses a composite that mixes AGP versions. Keep the Gradle wrapper at that AGP's
// floor (8.14.3) for the same reason. See docs/build-platform.md.
