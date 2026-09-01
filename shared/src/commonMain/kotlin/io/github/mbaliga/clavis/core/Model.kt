package io.github.mbaliga.clavis.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Shared domain contracts for Clavis. Every module (domain engines, pipeline-generated packs,
 * Compose UI) codes against the types in this package. The shapes mirror the v0.10 web build's
 * packed data (template-spec §12) and persisted state (template-spec §1) closely enough that a
 * JSON export of the web build imports without a lossy translation.
 *
 * Conventions:
 *  - Times are epoch milliseconds (Long), matching the web build's `Date.now()`.
 *  - Language codes are ISO 639-1 strings ("es", "ja", …) wrapped in the value class [Lang].
 *  - Rectangles are page-normalised [0,1] fractions: x, y, w, h.
 */

@JvmInline
@Serializable
value class Lang(val code: String) {
    override fun toString(): String = code
    companion object {
        val EN = Lang("en")
    }
}

@Serializable
data class Rect(val x: Double, val y: Double, val w: Double, val h: Double)

/** A cross-language hint attached to a token: "this looks like <word> in <lang>" (template-spec §12). */
@Serializable
data class Bridge(val lang: Lang, val word: String, val similarity: Double)

/**
 * One token of one balloon line in one language. Packed as `[surface, lemma|0, zipf, bridges|0, reading?]`;
 * [lemma] is always materialised here (the packer's `0` means `surface.lowercase()`).
 */
@Serializable
data class Token(
    val surface: String,
    val lemma: String,
    val zipf: Double,
    val bridges: List<Bridge> = emptyList(),
    val reading: String? = null,
)

/** One aligned balloon across languages: the unit the whole app is built on. */
@Serializable
data class Scene(
    /** 0-based id equal to its index in [Corpus.scenes]. */
    val i: Int,
    /** Episode number. */
    val e: Int,
    /** Page code, e.g. "E05P03". */
    val p: String,
    /** Page image key into [Corpus.pages], e.g. "ep05/gfx_…png" (or the pack's own key). */
    val m: String,
    /** Shared (English-derived) balloon rect, the legacy fallback. */
    val r: Rect,
    /** Per-language balloon rects; a language may be absent. */
    val q: Map<Lang, Rect> = emptyMap(),
    /** The line of dialogue per language; always includes "en". */
    val x: Map<Lang, String>,
    /** Tokens per language, for every language present in [x]. */
    val k: Map<Lang, List<Token>>,
)

/** Lexicon entry: `[count, zipf, surface, reading]` in the pack. */
@Serializable
data class LexEntry(val count: Int, val zipf: Double, val surface: String, val reading: String? = null)

/** A row of the flattened lexicon used by the baseline test and cloze distractors (`lexArr`). */
data class LexRow(val lemma: String, val count: Int, val zipf: Double, val surface: String, val reading: String)

@Serializable
enum class EdgeType { @SerialName("cog") COGNATE, @SerialName("bor") BORROWING, @SerialName("der") DERIVATION }

@Serializable
data class EtymEdge(val lang: Lang, val lemma: String, val type: EdgeType)

/** Etymology record for one lemma. `origin` is display-ready ("Latin clavis"); never invented (D-5). */
@Serializable
data class EtymEntry(
    @SerialName("o") val origin: String? = null,
    /** "inh" inherited or "bor" borrowed. */
    @SerialName("ot") val originType: String? = null,
    @SerialName("e") val edges: List<EtymEdge> = emptyList(),
    /** Provenance of the record: "wiktionary", "etymdb", or both joined by "+". */
    @SerialName("src") val source: String? = null,
    /** IPA transcription when the pronunciation pack carries one. */
    @SerialName("ipa") val ipa: String? = null,
)

@Serializable
data class PageInfo(val width: Int, val height: Int, /** resource/file name of the WebP. */ val file: String)

/**
 * The loaded corpus plus the boot-time indices the engines need (template-spec §12, `indexCorpus`).
 * Construct through [Corpus.index] so the indices are always consistent with the data.
 */
class Corpus(
    val scenes: List<Scene>,
    val lex: Map<Lang, Map<String, LexEntry>>,
    val etym: Map<Lang, Map<String, EtymEntry>>,
    val pseudo: Map<Lang, List<String>>,
    val pages: Map<String, PageInfo>,
) {
    /** Scene ids with a non-empty token list in that language, in scene order. */
    val byLang: Map<Lang, List<Int>>
    /** Scene ids containing a lemma (duplicates possible when a lemma appears twice in a scene). */
    val byLemma: Map<Lang, Map<String, List<Int>>>
    /** Scene ids on a page, sorted by (round(r.y*12), r.x). */
    val byPage: Map<String, List<Int>>
    /** Flattened lexicon rows per language, in map order. */
    val lexArr: Map<Lang, List<LexRow>>

    init {
        val bl = mutableMapOf<Lang, MutableList<Int>>()
        val blm = mutableMapOf<Lang, MutableMap<String, MutableList<Int>>>()
        val bp = mutableMapOf<String, MutableList<Int>>()
        for (sc in scenes) {
            for ((l, toks) in sc.k) {
                if (toks.isEmpty()) continue
                bl.getOrPut(l) { mutableListOf() }.add(sc.i)
                val m = blm.getOrPut(l) { mutableMapOf() }
                for (t in toks) m.getOrPut(t.lemma) { mutableListOf() }.add(sc.i)
            }
            bp.getOrPut(sc.m) { mutableListOf() }.add(sc.i)
        }
        for ((_, ids) in bp) ids.sortWith(compareBy({ kotlin.math.round(scenes[it].r.y * 12) }, { scenes[it].r.x }))
        byLang = bl
        byLemma = blm
        byPage = bp
        lexArr = lex.mapValues { (_, m) -> m.map { (lm, e) -> LexRow(lm, e.count, e.zipf, e.surface, e.reading ?: "") } }
    }

    fun scene(id: Int): Scene = scenes[id]

    companion object {
        fun index(
            scenes: List<Scene>,
            lex: Map<Lang, Map<String, LexEntry>>,
            etym: Map<Lang, Map<String, EtymEntry>>,
            pseudo: Map<Lang, List<String>>,
            pages: Map<String, PageInfo>,
        ): Corpus = Corpus(scenes, lex, etym, pseudo, pages)
    }
}
