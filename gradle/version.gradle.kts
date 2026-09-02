// Single source of truth for every version number the build emits (docs/DECISIONS.md D-16).
// Applied from composeApp/build.gradle.kts inside its "versioning (release)" block; the results are
// published as extra properties on the applying project. Scheme and mapping: docs/RELEASE.md §"Versioning".
//
//   input       -Pclavis.version=X  >  CLAVIS_VERSION env var  >  ClavisVersion.NAME in :shared (fallback; not edited here)
//   format      MAJOR.MINOR.PATCH[-alpha.N|-beta.N|-rc.N]      N = 1..29, MINOR/PATCH = 0..99
//   versionCode MAJOR*10000 + MINOR*100 + PATCH                 (a pre-release tag shares the code of its final)
//   versionName the input string, verbatim (e.g. 0.2.0-rc.1)
//   desktop     MAJOR.MINOR.PATCH                               (uber-jar name and jpackage default)
//   .deb        MAJOR.MINOR.PATCH[~alpha.N|~beta.N|~rc.N]        (Debian "~" sorts a pre-release before its final)
//   .msi        MAJOR.MINOR.(PATCH*100 + PRE)   PRE: alpha.N -> N, beta.N -> 30+N, rc.N -> 60+N, final -> 99
//               (MSI ProductVersion is numeric x.y.z with z <= 65535; an equal version is not an upgrade, so
//               pre-releases must sort strictly below their final; Windows accepts MAJOR = 0)
//   .dmg        (MAJOR == 0 ? 1 : MAJOR).MINOR.PATCH             (jpackage on macOS rejects a leading 0; cosmetic, a DMG has no upgrade semantics)

val clavisVersionSourceFile = rootProject.file("shared/src/commonMain/kotlin/io/github/mbaliga/clavis/core/ClavisVersion.kt")
val clavisFallbackVersion: String = Regex("""const val NAME = "([^"]+)"""")
    .find(clavisVersionSourceFile.readText())?.groupValues?.get(1)
    ?: error("Cannot read `const val NAME` from $clavisVersionSourceFile")

val clavisPropVersion: String? = providers.gradleProperty("clavis.version").orNull?.trim()?.takeIf { it.isNotEmpty() }
val clavisEnvVersion: String? = providers.environmentVariable("CLAVIS_VERSION").orNull?.trim()?.takeIf { it.isNotEmpty() }
val clavisVersionSource: String = when {
    clavisPropVersion != null -> "-Pclavis.version"
    clavisEnvVersion != null -> "CLAVIS_VERSION"
    else -> "ClavisVersion.kt"
}
val clavisVersion: String = (clavisPropVersion ?: clavisEnvVersion ?: clavisFallbackVersion).removePrefix("v")

val clavisSemver = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.([1-9]\d?))?$""")
val clavisMatch = clavisSemver.matchEntire(clavisVersion)
    ?: error("Clavis version '$clavisVersion' (from $clavisVersionSource) must be MAJOR.MINOR.PATCH[-alpha.N|-beta.N|-rc.N], N in 1..29")
val clavisMajor = clavisMatch.groupValues[1].toInt()
val clavisMinor = clavisMatch.groupValues[2].toInt()
val clavisPatch = clavisMatch.groupValues[3].toInt()
val clavisPreKind = clavisMatch.groupValues[4]
val clavisPreNum = clavisMatch.groupValues[5].toIntOrNull() ?: 0
require(clavisMinor in 0..99 && clavisPatch in 0..99) { "Clavis version '$clavisVersion': MINOR and PATCH must be 0..99 for the versionCode scheme" }
require(clavisPreNum in 0..29) { "Clavis version '$clavisVersion': pre-release number must be 1..29" }
require(clavisMajor in 0..255) { "Clavis version '$clavisVersion': MAJOR must be 0..255 (MSI ProductVersion limit)" }

val clavisPreOffset = when (clavisPreKind) {
    "" -> 99
    "alpha" -> clavisPreNum
    "beta" -> 30 + clavisPreNum
    else -> 60 + clavisPreNum
}

val clavisVersionCode: Int = clavisMajor * 10_000 + clavisMinor * 100 + clavisPatch
val clavisVersionName: String = clavisVersion
val clavisBaseVersion = "$clavisMajor.$clavisMinor.$clavisPatch"
val clavisDebVersion = if (clavisPreKind.isEmpty()) clavisBaseVersion else "$clavisBaseVersion~$clavisPreKind.$clavisPreNum"
val clavisMsiVersion = "$clavisMajor.$clavisMinor.${clavisPatch * 100 + clavisPreOffset}"
val clavisDmgVersion = "${if (clavisMajor == 0) 1 else clavisMajor}.$clavisMinor.$clavisPatch"
val clavisIsPreRelease = clavisPreKind.isNotEmpty()

extra["clavisVersion"] = clavisVersion
extra["clavisVersionSource"] = clavisVersionSource
extra["clavisVersionCode"] = clavisVersionCode
extra["clavisVersionName"] = clavisVersionName
extra["clavisBaseVersion"] = clavisBaseVersion
extra["clavisDebVersion"] = clavisDebVersion
extra["clavisMsiVersion"] = clavisMsiVersion
extra["clavisDmgVersion"] = clavisDmgVersion
extra["clavisIsPreRelease"] = clavisIsPreRelease

if (clavisVersion != clavisFallbackVersion) {
    logger.warn(
        "Clavis: building version $clavisVersion (from $clavisVersionSource) but ClavisVersion.NAME in :shared is " +
            "$clavisFallbackVersion; the version the app displays will not match. Bump ClavisVersion.kt before tagging (docs/RELEASE.md)."
    )
}

tasks.register("clavisVersion") {
    group = "help"
    description = "Prints the resolved Clavis version numbers (source, versionCode, desktop package versions)."
    val report = listOf(
        "version=$clavisVersion",
        "source=$clavisVersionSource",
        "versionName=$clavisVersionName",
        "versionCode=$clavisVersionCode",
        "prerelease=$clavisIsPreRelease",
        "desktop=$clavisBaseVersion",
        "deb=$clavisDebVersion",
        "msi=$clavisMsiVersion",
        "dmg=$clavisDmgVersion",
        "clavisVersionKt=$clavisFallbackVersion",
    )
    doLast { report.forEach { println(it) } }
}
