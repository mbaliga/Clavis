package io.github.mbaliga.clavis.core

/**
 * Every user-facing string the domain layer produces or the UI must reproduce verbatim from the
 * v0.10 web build (template-spec §3.5, §4.3, §5, §6, §8). Citation markers such as `[2]` are
 * written where the template calls `cite(n)`; the UI renders them as superscript links.
 * Strings marked `D-14` are additions mandated by DECISIONS.md and have no web-build original.
 */
object Copy {
    /**
     * Wraps [s] in a Unicode directional isolate (rtl audit): a target-language surface/lemma
     * form (which can be RTL script, e.g. Arabic) concatenated into a larger English sentence can
     * otherwise set the *whole* string's base paragraph direction — Compose's `Text`, like a
     * browser, resolves an unset direction from the string's first strong character — scrambling
     * the English words/digits that follow it. This is the same fix HTML's `<bdi>` element /
     * `unicode-bidi: isolate` provides, applied as plain characters since `Text` has no per-span
     * `textDirection`. Pass [rtl] when the caller already knows the script's direction (RLI/LRI);
     * leave it null to let the isolate resolve its own direction from its own first strong
     * character (FSI) — enough on its own to stop it from hijacking the *surrounding* string.
     */
    fun isolate(s: String, rtl: Boolean? = null): String {
        val open = when (rtl) { true -> '⁧'; false -> '⁦'; null -> '⁨' }
        return "$open$s⁩"
    }

    // ---------- lane reasons (`why`), template-spec §3.5 / §8 ----------
    const val PINNED = "pinned by you"
    const val TRYING_NEW = "trying something new"
    fun readyToReturn(n: Int): String = "$n word${if (n == 1) "" else "s"} ready to return"
    const val IMPROVING_QUICKLY = "you're improving quickly here"
    const val GETTING_TO_KNOW = "getting to know you"
    const val PATH_YOU_CHOSE = "the path you chose"
    const val HELD = "held for this session"
    const val ECHO = "an echo from earlier"
    /** D-14: `chooseLane` guard when no active lane has scenes. */
    const val NOTHING_ELSE_OPEN = "nothing else is open"
    /** D-6: `startSession` when every lane is OFF — shown instead of a session; the Learn pool is disabled with it. */
    const val ALL_RESTING = "Every language is resting — turn one on in Languages"
    const val POOL_RESTING = "resting"
    const val OPEN_LANGUAGES = "Languages"

    // ---------- encounter chrome, §5 ----------
    const val SUB_CLOZE = "· do you remember?"
    const val SUB_ECHO = "· an echo"
    const val SUB_FLUENCY = "· easy read"
    const val SUB_READ = "· new bubble"
    const val LANGID_CHIP = "Which language is this?"
    const val LANGID_TAG = "a between-rounds game"
    const val SEE_WHOLE_PAGE = "See the whole page"
    const val BACK_TO_BALLOON = "Back to the balloon"
    fun hintFullPage(lang: String): String =
        "The whole page, lettered in $lang — the glowing balloon is this line. Tap any balloon for its English."
    const val HINT_CREDIBLE = "The line below is spoken in the marked balloon."
    const val HINT_NOT_CREDIBLE =
        "The marker shows roughly where this line sits — the translators' file doesn't give this balloon's exact shape."
    const val HINT_ECHO_NOT_CREDIBLE = "The marker shows roughly where this line sits on the page."
    const val NEXT = "Next"
    const val SHOW_ENGLISH = "Show the English"

    // ---------- cloze, §5.2 ----------
    const val FAST_RETURN = "You met this one a few minutes ago — the first return comes fast on purpose, then the gaps stretch [2]."
    const val COACH_CLOZE =
        "A word you've met is hidden. Answering from memory is what makes it stick [3] — and one option may be its look-alike from a neighbouring language."
    const val LOW_COV = "This is the only bubble with that word, and it's a hard one — the English is open below."
    fun clozeHeld(days: Int): String = "Held. It returns in about $days day${if (days == 1) "" else "s"}."
    const val CLOZE_MISS = "No harm — it comes straight back in ten minutes."
    const val ROUTES_PROMPT = "Did one of these help you remember? "
    fun routeTicked(lang: String): String = "✓ $lang"
    const val BRIDGE_PREFIX = "looks related to "
    const val BRIDGE_SEP = " · "
    const val BRIDGE_SUFFIX = " (a guess from spelling — verify)"
    /** `looks related to English <b>w</b> · Spanish <b>w</b> (a guess from spelling — verify)` with the words bolded by the UI. */
    fun bridgeLine(parts: List<Pair<String, String>>): String =
        BRIDGE_PREFIX + parts.joinToString(BRIDGE_SEP) { (lang, w) -> "$lang $w" } + BRIDGE_SUFFIX

    // ---------- read, §5.3 ----------
    const val COACH_READ =
        "First one: the highlighted words are probably new to you. Tap any word to hear it and see where it comes from — tapping also flips it between \"new to me\" and \"I know this\". Then be honest with the two buttons below."
    const val FLUENCY_COPY = "You should know everything here — enjoy the read. Tap anything that's actually unfamiliar."
    fun untestedCopy(lang: String): String = "Everything looks new because $lang is untested — a three-minute test in You fixes that."
    fun readCountCopy(n: Int, cov: Double): String =
        "$n word${if (n == 1) "" else "s"} here ${if (n == 1) "is" else "are"} probably new; the rest you know (${pct(cov)} of the bubble)."
    const val TOO_HARD = "Too hard"
    const val UNDERSTOOD = "Understood"

    // ---------- echo, §5.4 ----------
    const val ECHO_COPY = "A bubble you've never studied — but the highlighted word has crossed your path before. How does it land?"
    const val ECHO_KNOW = "I know it"
    const val ECHO_FAMILIAR = "Familiar, can't place it"
    const val ECHO_NEW = "New to me"
    fun borrowedFrom(origin: String): String = "borrowed from $origin"
    fun grewFrom(origin: String): String = "grew from $origin"
    fun echoResult(surface: String, reading: String?, days: Int?, origin: String?): String {
        val r = if (reading.isNullOrEmpty()) "" else " ($reading)"
        val met = if (days != null) "$days day${if (days == 1) "" else "s"} ago" else "earlier"
        val o = if (origin.isNullOrEmpty()) "" else " · $origin"
        return "${isolate(surface)}$r — you met it $met$o. That feeling of almost-knowing is real memory — glimpsed words are relearned faster than new ones [14][15][35]."
    }

    // ---------- langid, §5.5 ----------
    const val LANGID_WHY_EYEBROW = "Why this game is here"
    const val LANGID_WHY =
        "Skirgård, Roberts and Yencken analysed nearly 15 million guesses across the 78 languages of The Great Language Game and found that a language is confused with its geographic neighbours more often than with its genealogical relatives [33] — so naming a language by its shapes teaches the map, not only the family tree. In a study of GeoGuessr play, written signs were the largest single category of cues people used to place an unfamiliar scene [34]."
    const val FULL_CITATIONS = "Full citations in Evidence & credits."

    // ---------- session summary, §5.7 ----------
    const val SUMMARY_H2 = "That's one dip."
    const val SUMMARY_CORRECT = "understood or recalled"
    const val SUMMARY_NEW = "new words met"
    const val SUMMARY_LANGS = "languages visited"
    const val SUMMARY_COPY = "Clavis noted where you're improving [10] — Paths shows every branch now open, and you can take the next one yourself."
    const val SEE_PATHS = "See the paths"
    const val ANOTHER = "Another"
    const val DONE = "Done"

    // ---------- baseline test, §4 ----------
    const val TEST_INTRO = "\"Know\" means you could say what it means. Some words are invented — saying yes to those lowers your estimate, so be honest."
    fun testProgress(i: Int, n: Int): String = "· do you know this word? · $i/$n"
    const val DONT_KNOW = "Don't know"
    const val KNOW_IT = "Know it"
    const val CANCEL = "Cancel"
    fun testBrandNew(lang: String): String = "$lang: treated as brand new — every word will earn its place"
    fun testAssumes(lang: String, band: String): String = "$lang: Clavis now assumes you know $band"
    const val NOTHING_YET = "nothing yet"

    // ---------- Paths, §6 ----------
    fun testTitle(lang: String): String = "Take the $lang check"
    const val TEST_WHY = "three minutes of yes/no, so words you already own don't arrive as new"
    fun testThen(lang: String): String = "Clavis stops treating $lang as brand new"
    fun dueTitle(n: Int, lang: String): String = "Bring back $n $lang word${if (n == 1) "" else "s"}"
    const val DUE_THEN = "each one you recall settles for days, then weeks"
    fun newTitle(k: Int, lang: String): String = "Meet $k new $lang word${if (k == 1) "" else "s"}"
    fun newThen(u: Int): String =
        if (u > 0) "$u more bubble${if (u == 1) " becomes" else "s become"} readable" else "the next bubbles get easier"
    fun readTitle(lang: String): String = "Read $lang freely"
    const val READ_WHY = "no grading, no clock — just the comic"
    const val READ_THEN = "glimpsed words are relearned faster later"
    const val GAME_TITLE = "Which language is this?"
    const val GAME_WHY = "name a line by its shapes alone"
    const val GAME_THEN = "places scripts on the map, not just the family tree"
    /** D-14 LETTERS branch. */
    fun lettersTitle(lang: String): String = "Learn the $lang script first"
    const val LETTERS_WHY = "the balloons are unreadable until the letters are"
    const val LETTERS_THEN = "Letters stops being a wall"
    val KIND_LABEL: Map<BranchKind, String> = mapOf(
        BranchKind.TEST to "first, a check", BranchKind.DUE to "bring back", BranchKind.NEW to "something new",
        BranchKind.READ to "free reading", BranchKind.GAME to "a game", BranchKind.LETTERS to "learn the script",
    )
    /** Primary button per kind (template L2261); LETTERS is D-14. */
    fun branchButton(kind: BranchKind): String = when (kind) {
        BranchKind.TEST -> "Take the check"
        BranchKind.READ -> "Open the comic"
        BranchKind.GAME -> "Play a round"
        BranchKind.LETTERS -> "Open Letters"
        else -> "Walk this way"
    }
    const val PATHS_INTRO =
        "No fixed course. Each branch below is a real next step Clavis can take right now — with the actual words it would use, and what it opens up. Take one yourself, or let Clavis choose: it leans toward wherever your results are changing fastest [10][11]."
    const val HERE_NOW = "Here, now"
    fun waysForward(n: Int): String = "$n ways forward"
    const val TAIL_CARD = "and onward — the paths after these are not computed yet. They form from what you do next."
    const val THEN_PREFIX = "then · "
    fun lastTaken(title: String): String = "last: $title"
    const val UNDO = "↶ undo"
    const val UNDO_TITLE = "Undo that choice"
    const val UNDONE_TOAST = "Choice undone — the fan is open again"
    const val AS_TREE = "As a tree"
    const val AS_MAP = "As a map"
    const val MAP_FOOTER =
        "The same branches, drawn by force [40]: springs pull each choice away from the others, and a soft contour groups choices of the same kind [39]. Faint nodes beyond each branch are the paths that would open — not computed, just possible."
    const val MAP_HINT = "tap a branch to take that path · everything can be dragged"
    const val LONGER_VIEW = "The longer view, per language"
    const val PIN = "Pin"
    const val PINNED_BUTTON = "Pinned"
    const val CHIP_SPEAK = "speak"
    const val CHIP_READ = "read"
    const val CHIP_OFF = "off"
    const val LP_WATCHING = "still watching you"
    const val LP_FAST = "improving fast"
    const val LP_STEADY = "steady"
    const val ASSUMES_UNTESTED = "nothing (untested)"
    fun laneMeta(lpv: String, due: Int, held: Int, readable: Double, newToday: Int, newPerDay: Int, assumes: String): String =
        "$lpv · $due ready · $held held · ${pct(readable)} readable · new today $newToday/$newPerDay · assumes $assumes"
    fun lookAlikes(items: List<Pair<String, Int>>): String =
        "look-alikes that fooled you: " + items.joinToString(", ") { (lang, n) -> "$lang ×$n" }
    fun bridgesHelped(items: List<Pair<String, Int>>): String =
        "bridges that helped: " + items.joinToString(", ") { (lang, n) -> "$lang ×$n" }
    /** Map view constants (parity item 56). */
    val MAP_KIND_COLOUR: Map<BranchKind, String> = mapOf(
        BranchKind.TEST to "#DCAA47", BranchKind.DUE to "#93A4F5", BranchKind.NEW to "#86C197",
        BranchKind.READ to "#B0B6C6", BranchKind.GAME to "#E6907F", BranchKind.LETTERS to "#DCAA47",
    )
    val MAP_SUB: Map<BranchKind, String> = mapOf(
        BranchKind.TEST to "a check", BranchKind.DUE to "bring back", BranchKind.NEW to "new words",
        BranchKind.READ to "free reading", BranchKind.GAME to "which language?", BranchKind.LETTERS to "the script",
    )
    const val MAP_GAME_LABEL = "A game"

    // ---------- Learn, §7.2 ----------
    const val BEGIN = "Begin"
    fun pulling(n: Int): String = "$n word${if (n == 1) "" else "s"} pulling"
    const val TWO_MINUTE_DIP = "a two-minute dip"
    const val POOL_DUE = "The pool stirs and glows when words are ready to come back. Tap to begin."
    const val POOL_STILL = "A still pool — nothing is overdue. A session will meet a few new words."
    const val BEFORE_FIRST_DIP = "Before your first dip"
    const val BEFORE_FIRST_DIP_COPY =
        "Three minutes per language: you'll see words and say if you know them. Without this, Clavis treats that language as brand new."
    const val HOW_MUCH = "How much comic you can read"
    const val HOW_MUCH_FOOTER = "The only score here: bubbles you could read with no help [1]. It grows as words take root."
    const val JUST_READING = "Just reading"
    const val JUST_READING_COPY = "Read the comic itself — no grading, no clock. Words merely met now are relearned faster later [15][17]."
    const val OPEN_COMIC = "Open the comic"
    const val START_SESSION_ARIA = "Start a session"

    // ---------- You, §7.8 ----------
    const val YOU_H2 = "Your record"
    const val YOU_ENCOUNTERS = "encounters"
    const val YOU_DAYS = "days you showed up"
    const val YOU_RECALL = "recall when asked"
    val YOU_HEADERS: List<String> = listOf("Language", "Assumes you know", "Held", "Ready", "Readable", "Recall", "Mix-ups")
    const val NOTHING_UNTESTED = "nothing — untested"
    const val DASH = "—"
    const val YOU_FOOTER =
        "\"Readable\" = share of that language's bubbles you could read unaided [1]. \"Mix-ups\" counts times a neighbouring language's look-alike fooled you [9] — the interference your future self will thank you for measuring. Terms are defined in Evidence & credits."
    const val BASELINE_TESTS = "Baseline tests"
    fun knows(band: String): String = "· knows $band"
    const val UNTESTED = "· untested"
    const val RETAKE = "Retake"
    const val TAKE_TEST = "Take test"
    const val YOUR_DATA = "Your data"
    const val YOUR_DATA_COPY = "Everything lives in this browser. Copy the text to back up or move devices; paste it back to restore."
    const val EXPORT = "Export"
    const val IMPORT = "Import"
    const val COPIED = "Copied"
    const val SELECT_AND_COPY = "Select and copy"
    const val RESTORED = "Restored"
    const val NOT_A_BACKUP = "That doesn't look like a Clavis backup"
    /** The stored document could not be decoded; it was renamed `clavis-state.json.corrupt-<epoch>` beside the fresh one. */
    const val STATE_UNREADABLE = "Your previous record could not be read; a copy was kept beside it."
    /** User-media import limits (`ImportLimits`). */
    const val IMPORT_TOO_LARGE = "That file is bigger than 2 MB — Clavis reads short texts: lyrics, subtitles, a chapter"
    const val IMPORT_NOT_TEXT = "That file doesn't look like text"
    const val IMPORT_TRUNCATED = "Long text — only the first 5,000 lines were kept"

    // ---------- Settings / misc, §7.9, §8 ----------
    const val ERASE_CONFIRM = "Erase all local progress?"
    const val LOADING = "Filling the pool…"
    fun couldNotLoad(message: String): String = "Could not load: $message"
    fun noVoice(lang: String): String = "No $lang voice on this device — install one in your system TTS settings"
    fun noVoiceLetters(lang: String): String = "No $lang voice on this device"
    const val HEAR_IT = "Hear it"

    // ---------- Story, §7.11 ----------
    fun storyLettered(lang: String): String = "The page is lettered in $lang — tap a balloon to hear it and jump to its line."
    const val STORY_NUMBERS = "The numbers mark where each line is spoken."
    const val STORY_FOOTER = "Just reading — nothing is graded here. Words you meet leave a trace that makes later study faster [15][17][35]. Tap a word to hear it."
    fun episode(n: Int): String = "Episode $n"
    fun pageOfN(i: Int, n: Int): String = "page $i of $n"

    // ---------- glossary, §7.10 ----------
    const val GLOSS_HELD = "A word whose memory strength predicts ≥90% recall right now."
    const val GLOSS_READY = "Due for return: recall probability has decayed to the review point."
    const val GLOSS_READABLE = "A bubble is readable when ≥95% of its words are held or presumed known."
    const val GLOSS_PRESUMED = "Above your tested frequency line; treated as known until evidence says otherwise."
}
