package io.github.mbaliga.clavis.core

import kotlinx.serialization.Serializable

/*
 * Content packs (D-7). A pack is a manifest plus files. Bundled packs ship inside the app's
 * compose resources; optional packs are downloaded on explicit user action. Every pack carries
 * its licence and attribution so the colophon can be generated, never hand-maintained.
 */

@Serializable
enum class PackKind { COMICS, LEXICON, ETYMOLOGY, SENTENCES, PRONUNCIATIONS, LETTERS, USER_MEDIA }

@Serializable
data class Attribution(
    val name: String,
    val role: String,
    val url: String? = null,
    val licence: String,
)

@Serializable
data class PackManifest(
    val id: String,
    val kind: PackKind,
    val version: String,
    val title: String,
    val languages: List<String>,
    val licence: String,
    val source: String,
    val generatedAt: String,
    val attribution: List<Attribution> = emptyList(),
    /** Relative file names and byte sizes inside the pack. */
    val files: Map<String, Long> = emptyMap(),
    /** Per-language counts the UI shows honestly (scenes, lemmas, origins, audio …). */
    val stats: Map<String, Map<String, Int>> = emptyMap(),
)

/** A Tatoeba-style sentence with an optional CC-licensed audio reference. */
@Serializable
data class Sentence(
    val id: Long,
    val lang: String,
    val text: String,
    val en: String? = null,
    /** Lemmas of this sentence that are in the Clavis lexicon for [lang]. */
    val lemmas: List<String> = emptyList(),
    val audioUrl: String? = null,
    val audioLicence: String? = null,
    val audioBy: String? = null,
)

/** Pronunciation record for one lemma: IPA and/or an audio reference with its own licence. */
@Serializable
data class Pronunciation(
    val lemma: String,
    val ipa: String? = null,
    val audioUrl: String? = null,
    val audioLicence: String? = null,
    val audioBy: String? = null,
)

interface ContentPackLoader {
    suspend fun manifests(): List<PackManifest>
    suspend fun loadCorpus(): Corpus
    suspend fun sentences(l: Lang): List<Sentence>
    suspend fun pronunciations(l: Lang): Map<String, Pronunciation>
    suspend fun pageBytes(pageKey: String): ByteArray
}
