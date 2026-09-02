package io.github.mbaliga.clavis.core

import kotlinx.serialization.Serializable

/** Language family, the axis the whole premise is organised on (01-premise §4). */
@Serializable
enum class Family { ROMANCE, GERMANIC, SLAVIC, CJK, SEMITIC, INDIC, TURKIC, OTHER }

/** Script class; drives lettering metrics, token spacing, fonts and the langid "same script" grouping. */
@Serializable
enum class Script { LATIN, CYRILLIC, GREEK, ARABIC, HEBREW, DEVANAGARI, CJK, HANGUL, OTHER }

@Serializable
enum class LaneMode { PROD, REC, OFF }

/**
 * One row of the language registry (`pipeline/languages.json`, D-6). The app never hardcodes a
 * language list; the nine original languages are those with `enabledByDefault = true`.
 */
@Serializable
data class LanguageInfo(
    val iso: Lang,
    val name: String,
    val nativeName: String,
    val family: Family,
    val script: Script,
    val rtl: Boolean = false,
    /** Default lane mode for a fresh profile (one PROD per family — 03-evidence §3). */
    val defaultLane: LaneMode,
    /** Sibling languages within the family used for look-alike distractors (`LANGS[l].sib`). */
    val siblings: List<Lang> = emptyList(),
    /** Romanisation system name shown in settings ("rōmaji", "pīnyīn", …), null for Latin scripts. */
    val readingSystem: String? = null,
    /** BCP-47 locale used for TTS voice selection ("es-ES", "ja-JP"). */
    val ttsLocale: String,
    /** Lane colour as 0xAARRGGBB. */
    val colour: Long,
    /** Whether tokens are separated by spaces when re-joined (false for ja/zh). */
    val spaceSeparated: Boolean = true,
    /** Whether this language is on for a new profile. */
    val enabledByDefault: Boolean = false,
    /** Whether the script should be learned before reading (a Paths LETTERS branch is offered, D-14). */
    val needsScriptPrimer: Boolean = false,
    /**
     * Whether the app ships a Letters primer for this script (ja, ko, zh, ar). A LETTERS branch is
     * offered only when both flags hold; a language that needs a primer nobody has written yet
     * gets the honest "no primer yet" card in Letters and no branch (D-21).
     */
    val hasScriptPrimer: Boolean = false,
)

/** Registry access. Implemented in :shared from the bundled `languages.json`. */
interface LanguageRegistry {
    val all: List<LanguageInfo>
    operator fun get(lang: Lang): LanguageInfo?
    fun require(lang: Lang): LanguageInfo = get(lang) ?: error("unknown language $lang")
    /** Display order: registry order. */
    val order: List<Lang> get() = all.map { it.iso }
    fun family(f: Family): List<LanguageInfo> = all.filter { it.family == f }
}
