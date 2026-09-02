package io.github.mbaliga.clavis.core.data

import io.github.mbaliga.clavis.core.Bridge
import io.github.mbaliga.clavis.core.Corpus
import io.github.mbaliga.clavis.core.EdgeType
import io.github.mbaliga.clavis.core.EtymEdge
import io.github.mbaliga.clavis.core.EtymEntry
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LexEntry
import io.github.mbaliga.clavis.core.PageInfo
import io.github.mbaliga.clavis.core.Rect
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.Token
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses the compact pack JSON (template-spec §12; `reference-prototype/pack.py`) into the
 * domain model. Token tuples are `[surface, lemma|0, zipf, bridges|0, reading?]`; `0` in slot 1
 * means `lemma = surface.lowercase()` (`lemmaOf`), slot 3 is `0` or `[[iso, word, sim], …]`,
 * slot 4 an optional romanisation (empty string → none, like `romOf`).
 *
 * Every entry point is tolerant of the two root shapes the pipeline may emit (a bare array or an
 * object wrapping it) and of the object/tuple variants of lexicon, etymology and page records.
 */
object CorpusDecoder {
    val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    // ---------------------------------------------------------------- scenes

    /** `packs/comics/scenes.json(.gz)`: a JSON array of compact scenes (or `{scenes:[…]}`). Streams. */
    fun decodeScenes(text: String): List<Scene> {
        val compact: List<CompactScene> = when (text.firstOrNull { !it.isWhitespace() }) {
            '[' -> json.decodeFromString(ListSerializer(CompactScene.serializer()), text)
            '{' -> json.decodeFromString(ScenesWrapper.serializer(), text).scenes
            else -> error("scenes: unexpected JSON root")
        }
        return finishScenes(compact.map { it.toScene() })
    }

    fun scenesFrom(el: JsonElement): List<Scene> =
        finishScenes(el.jsonArray.map { json.decodeFromJsonElement(CompactScene.serializer(), it).toScene() })

    private fun finishScenes(scenes: List<Scene>): List<Scene> {
        val sorted = scenes.sortedBy { it.i }
        sorted.forEachIndexed { idx, sc -> check(sc.i == idx) { "scene ids must be 0..n-1; found id ${sc.i} at index $idx" } }
        return sorted
    }

    /** One packed token tuple → [Token]. */
    fun tokenFromTuple(a: JsonArray): Token {
        val surface = a[0].jsonPrimitive.content
        val lemEl = a.getOrNull(1)
        val lemma = if (lemEl is JsonPrimitive && lemEl.isString && lemEl.content.isNotEmpty()) lemEl.content else surface.lowercase()
        val zipf = (a.getOrNull(2) as? JsonPrimitive)?.doubleOrNull ?: 0.0
        val bridges = (a.getOrNull(3) as? JsonArray)?.mapNotNull { b ->
            val t = b as? JsonArray ?: return@mapNotNull null
            if (t.size < 2) null else Bridge(Lang(t[0].jsonPrimitive.content), t[1].jsonPrimitive.content, (t.getOrNull(2) as? JsonPrimitive)?.doubleOrNull ?: 0.0)
        }.orEmpty()
        val readEl = a.getOrNull(4)
        val reading = if (readEl is JsonPrimitive && readEl.isString && readEl.content.isNotEmpty()) readEl.content else null
        return Token(surface, lemma, zipf, bridges, reading)
    }

    fun tokenToTuple(t: Token): JsonArray {
        val out = mutableListOf<JsonElement>(
            JsonPrimitive(t.surface),
            if (t.lemma == t.surface.lowercase()) JsonPrimitive(0) else JsonPrimitive(t.lemma),
            JsonPrimitive(t.zipf),
            if (t.bridges.isEmpty()) JsonPrimitive(0) else JsonArray(t.bridges.map { JsonArray(listOf(JsonPrimitive(it.lang.code), JsonPrimitive(it.word), JsonPrimitive(it.similarity))) }),
        )
        if (!t.reading.isNullOrEmpty()) out.add(JsonPrimitive(t.reading))
        return JsonArray(out)
    }

    // ---------------------------------------------------------------- lexicon / pseudo / etym / pages

    /** `{lang: {lemma: [count, zipf, surface, reading?]}}` (or object-valued entries). Preserves map order. */
    fun decodeLexicon(text: String): Map<Lang, Map<String, LexEntry>> = lexiconFrom(json.parseToJsonElement(text))

    fun lexiconFrom(el: JsonElement): Map<Lang, Map<String, LexEntry>> =
        el.jsonObject.entries.associate { (l, v) -> Lang(l) to v.jsonObject.entries.associate { (lm, e) -> lm to lexEntryFrom(e) } }

    private fun lexEntryFrom(e: JsonElement): LexEntry = when (e) {
        is JsonArray -> LexEntry(
            count = (e.getOrNull(0) as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0,
            zipf = (e.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: 0.0,
            surface = (e.getOrNull(2) as? JsonPrimitive)?.contentOrNull.orEmpty(),
            reading = (e.getOrNull(3) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() },
        )
        is JsonObject -> LexEntry(
            count = (e["count"] as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0,
            zipf = (e["zipf"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
            surface = (e["surface"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            reading = (e["reading"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() },
        )
        else -> LexEntry(0, 0.0, "")
    }

    /** `{lang: [pseudoword, …]}`. */
    fun decodePseudo(text: String): Map<Lang, List<String>> = pseudoFrom(json.parseToJsonElement(text))

    fun pseudoFrom(el: JsonElement): Map<Lang, List<String>> =
        el.jsonObject.entries.associate { (l, v) -> Lang(l) to (v as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }

    /** `{lang: {lemma: {o, ot, e: [[iso, lemma, type], …], src, ipa}}}`. */
    fun decodeEtym(text: String): Map<Lang, Map<String, EtymEntry>> = etymFrom(json.parseToJsonElement(text))

    fun etymFrom(el: JsonElement): Map<Lang, Map<String, EtymEntry>> =
        el.jsonObject.entries.associate { (l, v) -> Lang(l) to v.jsonObject.entries.associate { (lm, e) -> lm to etymEntryFrom(e.jsonObject) } }

    private fun etymEntryFrom(o: JsonObject): EtymEntry = EtymEntry(
        origin = (o["o"] as? JsonPrimitive)?.contentOrNull,
        originType = (o["ot"] as? JsonPrimitive)?.contentOrNull,
        edges = (o["e"] as? JsonArray).orEmpty().mapNotNull { edgeFrom(it) },
        source = (o["src"] as? JsonPrimitive)?.contentOrNull,
        ipa = (o["ipa"] as? JsonPrimitive)?.contentOrNull,
    )

    private fun edgeFrom(e: JsonElement): EtymEdge? = when (e) {
        is JsonArray -> if (e.size < 2) null else EtymEdge(Lang(e[0].jsonPrimitive.content), e[1].jsonPrimitive.content, edgeType((e.getOrNull(2) as? JsonPrimitive)?.contentOrNull))
        is JsonObject -> {
            val lang = (e["lang"] ?: e["l"])?.jsonPrimitive?.contentOrNull
            val lemma = (e["lemma"] ?: e["lm"])?.jsonPrimitive?.contentOrNull
            if (lang == null || lemma == null) null else EtymEdge(Lang(lang), lemma, edgeType((e["type"] ?: e["t"])?.jsonPrimitive?.contentOrNull))
        }
        else -> null
    }

    /** `bor` is a borrowing; everything else is kin (the web build treats any type ≠ 'bor' as kin). */
    private fun edgeType(s: String?): EdgeType = when (s?.lowercase()) {
        "bor" -> EdgeType.BORROWING
        "der" -> EdgeType.DERIVATION
        else -> EdgeType.COGNATE
    }

    /** `{pageKey: [w, h, file]}` or `{pageKey: {width, height, file}}`. */
    fun decodePages(text: String): Map<String, PageInfo> = pagesFrom(json.parseToJsonElement(text))

    fun pagesFrom(el: JsonElement): Map<String, PageInfo> = el.jsonObject.entries.associate { (k, v) ->
        k to when (v) {
            is JsonArray -> PageInfo(
                width = (v.getOrNull(0) as? JsonPrimitive)?.intOrNull ?: 0,
                height = (v.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: 0,
                file = (v.getOrNull(2) as? JsonPrimitive)?.contentOrNull ?: k,
            )
            is JsonObject -> PageInfo(
                width = (v["width"] ?: v["w"])?.jsonPrimitive?.intOrNull ?: 0,
                height = (v["height"] ?: v["h"])?.jsonPrimitive?.intOrNull ?: 0,
                file = (v["file"] ?: v["f"])?.jsonPrimitive?.contentOrNull ?: k,
            )
            else -> PageInfo(0, 0, k)
        }
    }

    // ---------------------------------------------------------------- whole corpus

    /** Assembles a [Corpus] from the separately shipped pack files. `etym`, `pseudo` and `pages` may be absent. */
    fun decode(scenesJson: String, lexiconJson: String, etymJson: String? = null, pseudoJson: String? = null, pagesJson: String? = null): Corpus =
        Corpus.index(
            scenes = decodeScenes(scenesJson),
            lex = decodeLexicon(lexiconJson),
            etym = etymJson?.let(::decodeEtym).orEmpty(),
            pseudo = pseudoJson?.let(::decodePseudo).orEmpty(),
            pages = pagesJson?.let(::decodePages).orEmpty(),
        )

    /** The v0.10 single blob `{scenes, lex, pseudo, etym}` (pack.py L35) — the reference build's own format. */
    fun decodePackedBlob(text: String, pages: Map<String, PageInfo> = emptyMap()): Corpus {
        val root = json.parseToJsonElement(text).jsonObject
        return Corpus.index(
            scenes = scenesFrom(root["scenes"] ?: JsonArray(emptyList())),
            lex = root["lex"]?.takeUnless { it is JsonNull }?.let(::lexiconFrom).orEmpty(),
            etym = root["etym"]?.takeUnless { it is JsonNull }?.let(::etymFrom).orEmpty(),
            pseudo = root["pseudo"]?.takeUnless { it is JsonNull }?.let(::pseudoFrom).orEmpty(),
            pages = root["pages"]?.takeUnless { it is JsonNull }?.let(::pagesFrom) ?: pages,
        )
    }
}

/** Custom serializer: a [Token] is a JSON array in the pack, not an object. */
internal object TokenTupleSerializer : KSerializer<Token> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("clavis.TokenTuple")
    override fun deserialize(decoder: Decoder): Token =
        CorpusDecoder.tokenFromTuple((decoder as JsonDecoder).decodeJsonElement().jsonArray)
    override fun serialize(encoder: Encoder, value: Token) =
        (encoder as JsonEncoder).encodeJsonElement(CorpusDecoder.tokenToTuple(value))
}

@Serializable
internal class ScenesWrapper(val scenes: List<CompactScene> = emptyList())

/** The packed scene record `{i,e,p,m,r,q,x,k}` (pack.py L31–33). */
@Serializable
internal class CompactScene(
    val i: Int,
    val e: Int = 0,
    val p: String = "",
    val m: String = "",
    val r: List<Double> = emptyList(),
    val q: Map<String, List<Double>?> = emptyMap(),
    val x: Map<String, String?> = emptyMap(),
    val k: Map<String, List<@Serializable(with = TokenTupleSerializer::class) Token>?> = emptyMap(),
) {
    fun toScene(): Scene = Scene(
        i = i, e = e, p = p, m = m,
        r = rectOf(r),
        q = q.entries.mapNotNull { (l, v) -> v?.let { Lang(l) to rectOf(it) } }.toMap(LinkedHashMap()),
        x = x.entries.mapNotNull { (l, v) -> v?.let { Lang(l) to it } }.toMap(LinkedHashMap()),
        k = k.entries.mapNotNull { (l, v) -> v?.let { Lang(l) to it } }.toMap(LinkedHashMap()),
    )

    private fun rectOf(v: List<Double>): Rect =
        Rect(v.getOrElse(0) { 0.0 }, v.getOrElse(1) { 0.0 }, v.getOrElse(2) { 0.0 }, v.getOrElse(3) { 0.0 })
}
