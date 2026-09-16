package io.github.mbaliga.clavis.core.data

import io.github.mbaliga.clavis.core.ContentPackLoader
import io.github.mbaliga.clavis.core.Corpus
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LanguageRegistry
import io.github.mbaliga.clavis.core.PackManifest
import io.github.mbaliga.clavis.core.PageInfo
import io.github.mbaliga.clavis.core.Pronunciation
import io.github.mbaliga.clavis.core.Sentence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * [ContentPackLoader] over a path-reader callback, so `:composeApp` can plug in compose resources
 * (`Res.readBytes("files/$path")`) without `:shared` depending on Compose. Paths are relative to
 * [base] (default `packs`):
 *
 * ```
 * packs/languages.json              registry (D-6)          → languages()
 * packs/manifest.json               [PackManifest]          → manifests()
 * packs/comics/scenes.json.gz       compact scenes          → loadCorpus()
 * packs/comics/pages.json           {key:[w,h,file]}        → loadCorpus(), pageBytes()
 * packs/lexicon/lexicon.json.gz     {lang:{lemma:[…]}}      → loadCorpus()
 * packs/lexicon/pseudo.json         {lang:[…]}              → loadCorpus()
 * packs/etymology/etym.json.gz      {lang:{lemma:{…}}}      → loadCorpus()
 * packs/sentences/<lang>.json.gz    [Sentence]              → sentences()
 * packs/pronunciations/<lang>.json.gz {lemma:{…}}           → pronunciations()
 * packs/comics/<file>               page WebP               → pageBytes(pageKey)
 * ```
 *
 * Gzip is detected by magic bytes, so a pack shipped uncompressed under a `.gz` name (or vice
 * versa) still loads. Optional files that fail to read yield empty collections; the scenes and
 * the lexicon are required.
 */
class DefaultContentPackLoader(
    private val readBytes: suspend (path: String) -> ByteArray,
    private val base: String = "packs",
) : ContentPackLoader {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private var pages: Map<String, PageInfo>? = null

    private suspend fun readText(path: String): String = maybeGunzip(readBytes(path)).decodeToString()

    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() } catch (e: Exception) { null }

    /** The language registry; the app needs it before anything else. */
    suspend fun languages(): LanguageRegistry = LanguageRegistryLoader.parse(readText("$base/languages.json"))

    override suspend fun manifests(): List<PackManifest> =
        optional { PackManifests.parse(readText("$base/manifest.json")) }.orEmpty()

    override suspend fun loadCorpus(): Corpus {
        val scenes = CorpusDecoder.decodeScenes(readText("$base/comics/scenes.json.gz"))
        val lex = CorpusDecoder.decodeLexicon(readText("$base/lexicon/lexicon.json.gz"))
        val etym = optional { CorpusDecoder.decodeEtym(readText("$base/etymology/etym.json.gz")) }.orEmpty()
        val pseudo = optional { CorpusDecoder.decodePseudo(readText("$base/lexicon/pseudo.json")) }.orEmpty()
        val pg = optional { CorpusDecoder.decodePages(readText("$base/comics/pages.json")) }.orEmpty()
        pages = pg
        return Corpus.index(scenes, lex, etym, pseudo, pg)
    }

    override suspend fun sentences(l: Lang): List<Sentence> = optional {
        when (val root = json.parseToJsonElement(readText("$base/sentences/${l.code}.json.gz"))) {
            is JsonArray -> root.map { json.decodeFromJsonElement(Sentence.serializer(), it) }
            is JsonObject -> (root["sentences"] ?: root["items"])?.let { arr -> (arr as JsonArray).map { json.decodeFromJsonElement(Sentence.serializer(), it) } }.orEmpty()
            else -> emptyList()
        }
    }.orEmpty()

    override suspend fun pronunciations(l: Lang): Map<String, Pronunciation> = optional {
        when (val root = json.parseToJsonElement(readText("$base/pronunciations/${l.code}.json.gz"))) {
            is JsonObject -> root.entries.associate { (lemma, v) ->
                val o = v.jsonObject
                lemma to Pronunciation(
                    lemma = (o["lemma"] as? JsonPrimitive)?.contentOrNull ?: lemma,
                    ipa = (o["ipa"] as? JsonPrimitive)?.contentOrNull,
                    audioUrl = (o["audioUrl"] as? JsonPrimitive)?.contentOrNull,
                    audioLicence = (o["audioLicence"] as? JsonPrimitive)?.contentOrNull,
                    audioBy = (o["audioBy"] as? JsonPrimitive)?.contentOrNull,
                )
            }
            is JsonArray -> root.map { json.decodeFromJsonElement(Pronunciation.serializer(), it) }.associateBy { it.lemma }
            else -> emptyMap()
        }
    }.orEmpty()

    /** Bytes of the page image for a scene's `m` key; resolves through `pages.json` when loaded. */
    override suspend fun pageBytes(pageKey: String): ByteArray {
        val pg = pages ?: optional { CorpusDecoder.decodePages(readText("$base/comics/pages.json")) }.orEmpty().also { pages = it }
        val file = pg[pageKey]?.file ?: pageKey
        return readBytes(if (file.startsWith("$base/")) file else "$base/comics/$file")
    }
}
