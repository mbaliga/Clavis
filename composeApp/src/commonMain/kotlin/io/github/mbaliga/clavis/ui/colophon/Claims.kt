package io.github.mbaliga.clavis.ui.colophon

/** One claim of the colophon's claims card with its citations (template L1454–1470). */
data class Claim(val text: String, val cites: List<Int>)

/** The 15 claim groups, verbatim (product-spec Appendix B). Held as a table so a claim cannot be edited without its references (R-8). */
val CLAIMS: List<Claim> = listOf(
    Claim("Bubbles arrive with one to three new words, the rest known", listOf(1, 4)),
    Claim("Words return spaced, right before you'd forget, as retrieval rather than re-reading", listOf(2, 3, 12)),
    Claim("Sessions hop languages on purpose, and never introduce look-alike sets together", listOf(13, 2)),
    Claim("Reading and listening are opened across whole families; speaking stays narrow", listOf(6, 7, 8, 9)),
    Claim("The next step is chosen by your learning progress, visibly, and you can always override", listOf(10, 11)),
    Claim("Word origins unlock on the second meeting, not the first", listOf(25, 26)),
    Claim("Echoes: media you merely glimpsed still leaves usable memory — the familiar tingle is real", listOf(14, 15, 16, 22, 35)),
    Claim("\"Which language is this?\" trains the map as much as the family tree, because script and sound are geographic signals", listOf(33, 34)),
    Claim("The Cosmos borrows established graph-interaction techniques rather than inventing them: focus-plus-context fisheye, a local edge-filter lens, set isocontours, and force-directed layout for a word's web", listOf(36, 37, 38, 39, 40)),
    Claim("The same word returns in different contexts and media, because varied contexts beat bare repetition", listOf(17, 18, 19)),
    Claim("Songs will use simple repeated melodies carrying the target phrase — and nothing plays behind exercises", listOf(20, 21, 22, 23, 24)),
    Claim("No XP, no leagues; a missed day is not a moral event", listOf(28, 29)),
    Claim("Recall before recognition, always showing the answer", listOf(30, 3)),
    Claim("Maintenance of a resting language costs one retrieval per word every ~8 weeks", listOf(27)),
    Claim("Baseline tests exist because heritage speakers are not beginners", listOf(31)),
)

const val COLOPHON_H2 = "Why Clavis works the way it does"
const val COLOPHON_INTRO = "Every design choice below names its evidence. Tap any [n] anywhere in the app to land here; each entry links to the paper."
const val CREDITS_PLANNED =
    "Planned media, licensing-clean: Mozilla Common Voice (CC0 speech, all nine languages), LibriVox + Wikimedia Commons audio (public-domain readings, anthems, folk songs), and public-domain-composition songs re-recorded simply — melody repeated verbatim, lyrics = target phrases, per the evidence above. Background-music ambience is deliberately absent."
const val BACK_TO_CLAIM = "← Back to the claim"

/** Glossary as it must appear (product-spec Appendix A). */
val GLOSSARY: List<Pair<String, String>> = listOf(
    "Bubble" to "One speech balloon from the comic — the unit you read.",
    "Echo" to "A word you've met resurfacing in a place you haven't — recognition, not testing.",
    "Held" to "A word whose memory strength predicts ≥90% recall right now.",
    "Ready" to "Due for return: recall probability has decayed to the review point.",
    "Readable" to "A bubble is readable when ≥95% of its words are held or presumed known.",
    "Presumed known" to "Above your tested frequency line; treated as known until evidence says otherwise.",
    "Kin / borrowed" to "Kin grew from a shared ancestor; borrowed words were taken across families — both are shortcuts your memory can use.",
    "The surface" to "In the Cosmos: the average height of your knowledge across each region of the word-space.",
)
