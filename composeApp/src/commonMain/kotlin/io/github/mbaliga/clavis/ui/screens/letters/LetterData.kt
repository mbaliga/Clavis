package io.github.mbaliga.clavis.ui.screens.letters

/**
 * The Letters tables, verbatim from the v0.10 web build (template-spec §7.7, parity item 73).
 * Hard-coded there too — none of this is derived from the corpus.
 */
object LetterData {
    /** One kana cell: glyph + romanisation; null = a blank cell of the gojūon grid (ya/yu/yo, wa/wo/n rows). */
    data class Kana(val glyph: String, val roma: String)

    private fun row(vararg cells: String?): List<Kana?> = cells.map { c -> c?.let { val (g, r) = it.split(" "); Kana(g, r) } }

    /** `HIRA` (L2038): 10 rows × 5 in gojūon order; blank cells render empty. */
    val HIRA: List<Kana?> = listOf(
        row("あ a", "い i", "う u", "え e", "お o"),
        row("か ka", "き ki", "く ku", "け ke", "こ ko"),
        row("さ sa", "し shi", "す su", "せ se", "そ so"),
        row("た ta", "ち chi", "つ tsu", "て te", "と to"),
        row("な na", "に ni", "ぬ nu", "ね ne", "の no"),
        row("は ha", "ひ hi", "ふ fu", "へ he", "ほ ho"),
        row("ま ma", "み mi", "む mu", "め me", "も mo"),
        row("や ya", null, "ゆ yu", null, "よ yo"),
        row("ら ra", "り ri", "る ru", "れ re", "ろ ro"),
        row("わ wa", null, "を o", null, "ん n"),
    ).flatten()

    /** `KATA2` (L2039): the same grid in katakana. */
    val KATA: List<Kana?> = listOf(
        row("ア a", "イ i", "ウ u", "エ e", "オ o"),
        row("カ ka", "キ ki", "ク ku", "ケ ke", "コ ko"),
        row("サ sa", "シ shi", "ス su", "セ se", "ソ so"),
        row("タ ta", "チ chi", "ツ tsu", "テ te", "ト to"),
        row("ナ na", "ニ ni", "ヌ nu", "ネ ne", "ノ no"),
        row("ハ ha", "ヒ hi", "フ fu", "ヘ he", "ホ ho"),
        row("マ ma", "ミ mi", "ム mu", "メ me", "モ mo"),
        row("ヤ ya", null, "ユ yu", null, "ヨ yo"),
        row("ラ ra", "リ ri", "ル ru", "レ re", "ロ ro"),
        row("ワ wa", null, "ヲ o", null, "ン n"),
    ).flatten()

    const val KANA_COLS = 5

    /** One jamo tile: the letter, its romanisation and the example syllable that is spoken. */
    data class Jamo(val jamo: String, val roma: String, val syllable: String)

    /** `JAMO_C` — 14 consonants. */
    val JAMO_C: List<Jamo> = listOf(
        Jamo("ㄱ", "g", "가"), Jamo("ㄴ", "n", "나"), Jamo("ㄷ", "d", "다"), Jamo("ㄹ", "r/l", "라"), Jamo("ㅁ", "m", "마"),
        Jamo("ㅂ", "b", "바"), Jamo("ㅅ", "s", "사"), Jamo("ㅇ", "(ng)", "아"), Jamo("ㅈ", "j", "자"), Jamo("ㅊ", "ch", "차"),
        Jamo("ㅋ", "k", "카"), Jamo("ㅌ", "t", "타"), Jamo("ㅍ", "p", "파"), Jamo("ㅎ", "h", "하"),
    )

    /** `JAMO_V` — 10 vowels. */
    val JAMO_V: List<Jamo> = listOf(
        Jamo("ㅏ", "a", "아"), Jamo("ㅑ", "ya", "야"), Jamo("ㅓ", "eo", "어"), Jamo("ㅕ", "yeo", "여"), Jamo("ㅗ", "o", "오"),
        Jamo("ㅛ", "yo", "요"), Jamo("ㅜ", "u", "우"), Jamo("ㅠ", "yu", "유"), Jamo("ㅡ", "eu", "으"), Jamo("ㅣ", "i", "이"),
    )

    data class Tone(val char: String, val pinyin: String, val description: String)

    /** `TONES` — the four tones on ma. */
    val TONES: List<Tone> = listOf(
        Tone("妈", "mā", "high and flat"), Tone("麻", "má", "rising, like a question"), Tone("马", "mǎ", "dips then rises"), Tone("骂", "mà", "falls sharply"),
    )

    data class Letter(val glyph: String, val name: String, val sound: String)

    /** `ABJAD` — the 28 letters `[glyph, name, sound]`. */
    val ABJAD: List<Letter> = listOf(
        Letter("ا", "alif", "a/ā"), Letter("ب", "bā", "b"), Letter("ت", "tā", "t"), Letter("ث", "thā", "th"), Letter("ج", "jīm", "j"),
        Letter("ح", "ḥā", "ḥ"), Letter("خ", "khā", "kh"), Letter("د", "dāl", "d"), Letter("ذ", "dhāl", "dh"), Letter("ر", "rā", "r"),
        Letter("ز", "zāy", "z"), Letter("س", "sīn", "s"), Letter("ش", "shīn", "sh"), Letter("ص", "ṣād", "ṣ"), Letter("ض", "ḍād", "ḍ"),
        Letter("ط", "ṭā", "ṭ"), Letter("ظ", "ẓā", "ẓ"), Letter("ع", "ʿayn", "ʿ"), Letter("غ", "ghayn", "gh"), Letter("ف", "fā", "f"),
        Letter("ق", "qāf", "q"), Letter("ك", "kāf", "k"), Letter("ل", "lām", "l"), Letter("م", "mīm", "m"), Letter("ن", "nūn", "n"),
        Letter("ه", "hā", "h"), Letter("و", "wāw", "w/ū"), Letter("ي", "yā", "y/ī"),
    )

    // ---- copy (template-spec §7.7) ----
    const val H2 = "Letters and their sounds"
    const val INTRO = "Tap anything to hear it (uses your device's voices). Readings also appear under every word in sessions — turn that off in Settings once a script feels like home."
    const val HIRA_CAPTION = "the native syllabary — one symbol, one syllable"
    const val KATA_CAPTION = "same sounds, used for loanwords — many from English"
    const val JA_FOOT = "Two small marks voice a sound: か ka → が ga, は ha → ば ba (and ぱ pa with a circle). Small ゃゅょ blend: き ki + ゃ → きゃ kya. The third script, kanji, are Chinese characters — the Chinese tab is secretly also about Japanese."
    const val KO_INTRO = "Hangul looks like characters but is an alphabet folded into blocks: 한 = ㅎ h + ㅏ a + ㄴ n. Learn 24 letters and you can sound out everything."
    const val KO_INTRO_BOLD = "alphabet folded into blocks"
    const val KO_CONSONANTS = "Consonants"
    const val KO_VOWELS = "Vowels"
    const val KO_FOOT = "Each tile speaks the letter inside the syllable shown (ㄱ alone has no sound of its own). ㅇ is silent at the start of a block and \"ng\" at the end."
    const val ZH_INTRO = "Chinese has no alphabet — words are characters, and pīnyīn (the Latin reading under every word here) tells you the sound. The accent mark is the tone, and tone changes meaning:"
    val ZH_INTRO_BOLD = setOf("pīnyīn", "tone")
    const val ZH_FOOT = "All four are \"ma\" — mother, hemp, horse, to scold. Characters are built from reusable parts (氵 water appears in 河 river, 海 sea), and the same characters power Japanese kanji: learn one, get a discount on the other."
    const val AR_INTRO = "Arabic reads right to left. Letters join like cursive, so shapes shift slightly with position. Short vowels usually aren't written — the readings under words show the consonant skeleton, and you supply vowels as you learn words."
    const val AR_INTRO_BOLD = "right to left"
    const val AR_FOOT = "The same script writes Urdu (with a few extra letters and a different pen style) — your Urdu is a head start here, and this alphabet is the bridge back to it."
    fun noPrimer(lang: String) = "No script primer yet for $lang — it comes with its content pack. Until then the comic is still readable page by page in Story, with readings under every word."

    /** The four scripts the web build ships primers for, in tab order. */
    val PRIMER_TABS: List<String> = listOf("ja", "ko", "zh", "ar")
}
