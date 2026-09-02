package io.github.mbaliga.clavis.core.data

import io.github.mbaliga.clavis.core.Family
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.core.LanguageInfo
import io.github.mbaliga.clavis.core.LanguageRegistry
import io.github.mbaliga.clavis.core.PackManifest
import io.github.mbaliga.clavis.core.Script
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A [LanguageRegistry] over an ordered list; order = display order (D-6). */
class ListLanguageRegistry(override val all: List<LanguageInfo>) : LanguageRegistry {
    private val byIso: Map<Lang, LanguageInfo> = all.associateBy { it.iso }
    override fun get(lang: Lang): LanguageInfo? = byIso[lang]
}

/**
 * Reads `packs/languages.json` (the pipeline registry, D-6). Field names follow [LanguageInfo];
 * a few v0.10-era aliases (`n`, `c`, `t`, `sib`, `mode`, `col`, `dir`) are accepted so the
 * template's table can be loaded too. Colours are `"0xAARRGGBB"` strings (also `#RRGGBB` or a
 * number). Enum values match case-insensitively; unknown families/scripts become OTHER.
 */
object LanguageRegistryLoader {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun fromBytes(bytes: ByteArray): LanguageRegistry = parse(maybeGunzip(bytes).decodeToString())

    fun parse(text: String): LanguageRegistry = fromElement(json.parseToJsonElement(text))

    fun fromElement(root: JsonElement): LanguageRegistry {
        val arr: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["languages"] ?: root["all"])?.jsonArray ?: error("languages.json: no 'languages' array")
            else -> error("languages.json: unexpected root")
        }
        return ListLanguageRegistry(arr.map { infoFrom(it.jsonObject) })
    }

    fun infoFrom(o: JsonObject): LanguageInfo {
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.contentOrNull }
        fun bool(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.let { p -> p.booleanOrNull ?: p.contentOrNull?.lowercase()?.let { s -> if (s == "true") true else if (s == "false") false else null } } }
        val iso = str("iso", "code", "lang") ?: error("languages.json: entry without iso: $o")
        val name = str("name", "n") ?: iso
        val script = enumOf<Script>(str("script", "t"), Script.OTHER) { s ->
            when (s) { "rtl" -> Script.ARABIC; "cjk" -> Script.CJK; else -> null }
        }
        val family = enumOf<Family>(str("family", "c"), Family.OTHER) { null }
        val defaultLane = enumOf<LaneMode>(str("defaultLane", "mode"), LaneMode.REC) { null }
        val siblings = ((o["siblings"] ?: o["sib"]) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.map(::Lang)
        val rtl = bool("rtl") ?: (str("dir") == "rtl")
        return LanguageInfo(
            iso = Lang(iso),
            name = name,
            nativeName = str("nativeName", "native") ?: name,
            family = family,
            script = script,
            rtl = rtl,
            defaultLane = defaultLane,
            siblings = siblings,
            readingSystem = str("readingSystem", "reading")?.takeIf { it.isNotEmpty() },
            ttsLocale = str("ttsLocale", "locale") ?: "",
            colour = parseColour(o["colour"] ?: o["color"] ?: o["col"]),
            spaceSeparated = bool("spaceSeparated") ?: (iso != "ja" && iso != "zh"),
            enabledByDefault = bool("enabledByDefault") ?: false, // the pipeline's own `enabled` flag is deliberately NOT an alias
            needsScriptPrimer = bool("needsScriptPrimer") ?: false,
        )
    }

    /** `"0xAARRGGBB"`, `"#RRGGBB"`, `"#AARRGGBB"` or a JSON number → `0xAARRGGBB` as Long. Missing → opaque grey `#8B8F9C` (the web fallback dot). */
    fun parseColour(el: JsonElement?): Long {
        val p = el as? JsonPrimitive ?: return 0xFF8B8F9CL
        p.longOrNull?.let { return it }
        val s = p.contentOrNull?.trim() ?: return 0xFF8B8F9CL
        return when {
            s.startsWith("0x", ignoreCase = true) -> s.substring(2).toLong(16)
            s.startsWith("#") -> { val hex = s.substring(1); if (hex.length <= 6) (0xFF000000L or hex.toLong(16)) else hex.toLong(16) }
            else -> s.toLongOrNull() ?: s.toLongOrNull(16) ?: 0xFF8B8F9CL
        }
    }

    private inline fun <reified E : Enum<E>> enumOf(s: String?, default: E, alias: (String) -> E?): E {
        if (s == null) return default
        val lower = s.lowercase()
        alias(lower)?.let { return it }
        return enumValues<E>().firstOrNull { it.name.equals(s, ignoreCase = true) } ?: default
    }
}

/** `packs/manifest.json`: an array of [PackManifest] (or `{packs:[…]}`). Missing/empty → no packs. */
object PackManifests {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; decodeEnumsCaseInsensitive = true }

    fun parse(text: String): List<PackManifest> {
        if (text.isBlank()) return emptyList()
        return when (val root = json.parseToJsonElement(text)) {
            is JsonArray -> root.map { json.decodeFromJsonElement(PackManifest.serializer(), it) }
            is JsonObject -> (root["packs"] ?: root["manifests"])?.let { json.decodeFromJsonElement(ListSerializer(PackManifest.serializer()), it) }
                ?: listOf(json.decodeFromJsonElement(PackManifest.serializer(), root))
            else -> emptyList()
        }
    }
}
