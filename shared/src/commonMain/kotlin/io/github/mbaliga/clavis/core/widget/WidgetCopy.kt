package io.github.mbaliga.clavis.core.widget

import io.github.mbaliga.clavis.core.Copy

/**
 * Every data-bound string the home-screen widgets draw, beside [Copy] so the D-18 copy rules
 * (CONTRIBUTING "Copy rules") are checked in one place and a test can run the forbidden list over
 * every widget state. Wherever the app already has the words, they are reused verbatim. The
 * widgets state facts and counts only: no claim that would need a citation, no streak, no score
 * language beyond the app's own "readable", no urgency (D-15, D-18, D-21, IA §4.1).
 *
 * The widgets' names and descriptions in the launcher's picker, their preview samples and their
 * loading text are Android string resources (`composeApp/src/androidMain/res/values/strings.xml`).
 */
object WidgetCopy {
    // ---------- states shared by every widget ----------
    const val FIRST_RUN = "Open Clavis once to fill this in."
    const val NOT_ONBOARDED = "Finish choosing your languages in Clavis."
    const val UNREADABLE = "Clavis could not read what this widget shows. Opening the app writes it again."
    /** D-6: every lane is OFF. */
    const val ALL_RESTING = Copy.ALL_RESTING
    fun asOf(time: String): String = "as of $time"

    // ---------- marks: every state has a shape and a word, never a colour alone ----------
    const val MARK_READY = "●"
    const val MARK_STILL = "○"
    fun ready(n: Int, atLeast: Boolean = false): String = "$MARK_READY ${count(n, atLeast)} ready"
    const val STILL = "$MARK_STILL still"
    fun count(n: Int, atLeast: Boolean): String = if (atLeast) "$n+" else "$n"
    fun readyToReturn(n: Int, atLeast: Boolean = false): String =
        if (atLeast) "$n+ words ready to return" else Copy.readyToReturn(n)

    // ---------- Word due ----------
    const val WORD_DUE = "Word due"
    fun wordDueIn(lang: String): String = "Word due · $lang"
    /** `Copy.SUB_CLOZE` without its leading separator. */
    val DO_YOU_REMEMBER: String = Copy.SUB_CLOZE.removePrefix("· ")
    const val RECALL = "Recall it"
    /** What TalkBack says for the gap, as the in-app cloze's gap token does ("blank — the word to recall"). */
    const val GAP_SPOKEN = "blank"
    const val STILL_POOL = "A still pool — nothing is overdue."
    const val STILL_POOL_SHORT = "A still pool"
    const val NOTHING_OVERDUE = "nothing is overdue"
    const val NEW_WORDS_NEXT = "A session will meet a few new words."
    fun nextReturns(time: String): String = "the next word returns $time"
    const val BEGIN = Copy.BEGIN
    const val OPEN_POOL = "Open the pool"
    fun alsoReady(lang: String, n: Int, atLeast: Boolean): String = "$lang · ${ready(n, atLeast)}"

    // ---------- The pool ----------
    const val POOL = "The pool"
    fun acrossLanguages(k: Int): String = "across $k language${if (k == 1) "" else "s"}"
    fun moreLanguages(k: Int): String = "and $k more language${if (k == 1) "" else "s"}"

    // ---------- Your record ----------
    const val RECORD = Copy.YOU_H2
    const val WORDS_HELD = "words held"
    fun held(n: Int): String = "$n held"
    fun heldWords(n: Int): String = "$n word${if (n == 1) "" else "s"} held"
    fun readable(pct: String): String = "$pct readable"
    /** The glossary's definition (Evidence & credits), not the cited comprehension claim the app prints with [1]. */
    const val READABLE_MEANS = "readable: a bubble with at least 95% of its words held or presumed known"

    // ---------- Last dip ----------
    const val LAST_DIP = "Last dip"
    fun lastDipAt(time: String): String = "Last dip · $time"
    fun correctOf(correct: Int, n: Int): String = "$correct/$n"
    const val UNDERSTOOD_OR_RECALLED = Copy.SUMMARY_CORRECT
    fun newWordsMet(n: Int): String = "$n new word${if (n == 1) "" else "s"} met"
    fun languagesVisited(n: Int): String = "$n language${if (n == 1) "" else "s"} visited"
    const val NO_DIP_YET = "No dip yet"
    const val NO_DIP_YET_LONG = "Your first session's numbers will appear here."
    const val ANOTHER = Copy.ANOTHER
    const val SEE_PATHS = Copy.SEE_PATHS

    // ---------- Word origins ----------
    const val ORIGINS = "Word origins"
    fun metIn(lang: String): String = "$lang · a word you've met"
    fun commonIn(lang: String): String = "$lang · a common word"
    const val NO_ORIGIN = "No word in your languages has a recorded origin yet."
    const val BORROWED = "borrowed"
    const val INHERITED = "inherited"
    fun originLine(origin: String, borrowed: Boolean): String = if (borrowed) Copy.borrowedFrom(origin) else Copy.grewFrom(origin)
    fun relation(surface: String, lang: String, kind: String): String = "$surface · $lang · $kind"
    fun source(label: String): String = "source: $label"
    const val SEE_WEB = "See its web"

    /** Provenance label for an etymology record's `src`, as the Cosmos word card prints it (D-5). */
    fun sourceLabel(src: String?): String? = src?.split('+')?.mapNotNull {
        when (it.trim().lowercase()) {
            "wiktionary" -> "Wiktionary"
            "etymdb" -> "EtymDB"
            "" -> null
            else -> it.trim()
        }
    }?.takeIf { it.isNotEmpty() }?.joinToString(" + ")

    // ---------- Languages (lane shortcuts) ----------
    const val LANGUAGES = "Your languages"
    fun languagesCount(k: Int): String = "$k language${if (k == 1) "" else "s"}"
    const val MORE = "More…"
    fun startIn(lang: String): String = "start a session in $lang"
    const val OPEN = "Open Clavis"
}
