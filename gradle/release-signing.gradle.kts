// Resolves the OPTIONAL Android release-signing material for :composeApp. Applied from the
// "release signing (release)" block in composeApp/build.gradle.kts; publishes `clavisReleaseSigning`,
// a map with storeFile / storePassword / keyAlias / keyPassword / source, or an EMPTY map when nothing is
// configured — in which case the release build stays unsigned and CI labels the artifacts "-unsigned".
// Lives in its own script so it can import java.util.* (the main build script's `java` accessor shadows the
// package). Sources, first complete one wins:
//   1. keystore.properties (gitignored) in composeApp/ or at the repo root, with keys
//      storeFile (relative to that file), storePassword, keyAlias, keyPassword;
//   2. env vars CLAVIS_KEYSTORE_BASE64 (base64 of the .jks/.p12, line breaks tolerated),
//      CLAVIS_KEYSTORE_PASSWORD, CLAVIS_KEY_ALIAS, CLAVIS_KEY_PASSWORD; the keystore is decoded under
//      build/signing/ (gitignored via build/), never into the source tree.
// Key generation, secret names and verification: docs/RELEASE.md.
import java.util.Base64
import java.util.Properties

fun env(name: String): String? = providers.environmentVariable(name).orNull?.trim()?.takeIf { it.isNotEmpty() }

val signingFromFile: Map<String, String>? = listOf(file("keystore.properties"), rootProject.file("keystore.properties"))
    .firstOrNull { it.isFile }
    ?.let { f ->
        val props = Properties().also { p -> f.inputStream().use(p::load) }
        val keys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        val values = keys.associateWith { props.getProperty(it)?.trim().orEmpty() }
        if (values.values.all { it.isNotEmpty() }) {
            values + ("storeFile" to f.parentFile.resolve(values.getValue("storeFile")).absolutePath) + ("source" to f.path)
        } else {
            logger.warn("Clavis: ${f.path} is present but incomplete (needs ${keys.joinToString()}); ignoring it")
            null
        }
    }

val signingFromEnv: Map<String, String>? = env("CLAVIS_KEYSTORE_BASE64")?.let { b64 ->
    val storePassword = env("CLAVIS_KEYSTORE_PASSWORD")
    val keyAlias = env("CLAVIS_KEY_ALIAS")
    val keyPassword = env("CLAVIS_KEY_PASSWORD")
    require(storePassword != null && keyAlias != null && keyPassword != null) {
        "CLAVIS_KEYSTORE_BASE64 is set but CLAVIS_KEYSTORE_PASSWORD, CLAVIS_KEY_ALIAS and/or CLAVIS_KEY_PASSWORD are missing"
    }
    val decoded = layout.buildDirectory.file("signing/clavis-release.jks").get().asFile
    decoded.parentFile.mkdirs()
    decoded.writeBytes(Base64.getMimeDecoder().decode(b64))
    mapOf(
        "storeFile" to decoded.absolutePath,
        "storePassword" to storePassword,
        "keyAlias" to keyAlias,
        "keyPassword" to keyPassword,
        "source" to "CLAVIS_KEYSTORE_BASE64",
    )
}

extra["clavisReleaseSigning"] = signingFromFile ?: signingFromEnv ?: emptyMap<String, String>()
