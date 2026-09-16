package io.github.mbaliga.clavis.ui.screens.you

import io.github.mbaliga.clavis.core.Card
import io.github.mbaliga.clavis.core.ClavisState
import io.github.mbaliga.clavis.core.Corpus
import io.github.mbaliga.clavis.core.DAY_MS
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.core.LanguageRegistry
import io.github.mbaliga.clavis.core.engine.Fsrs5
import io.github.mbaliga.clavis.core.jsRound

/**
 * The hypothesis instrumentation surfaced in You (product-spec §6; D-14(5)), computed from
 * `cards` and `log` only. Where the record does not yet carry the field a metric needs
 * (`card.originShown`, `branchLog`), the panel says so rather than approximating silently.
 * Presentation rules §6.2: every figure with its n; below [MIN_N] per group → "not enough data
 * yet"; no good/bad colouring; verbs "so far", "in this record".
 */
object Hypotheses {
    const val MIN_N = 20
    const val CAVEAT_1 = "This is one person's record, not a study. Nothing here generalises."
    const val CAVEAT_2 = "The instrumented measures are the ones that can be checked against the record rather than against the feeling."
    const val NOT_ENOUGH = "not enough data yet — need at least 20 in each group"

    data class Panel(
        val id: String,
        val title: String,
        val claim: String,
        val lines: List<String>,
        val against: String,
        val cites: List<Int>,
        val enough: Boolean,
        val note: String? = null,
    )

    enum class Cog { KIN, LOOKALIKE, NONE }

    private fun held(c: Card?, now: Long): Boolean = c != null && (c.prov || (Fsrs5.cardR(c, now) >= 0.9 && c.g != 1))

    private fun pctStr(a: Int, n: Int): String = if (n == 0) "—" else "${jsRound(100.0 * a / n)}%"
    private fun d1(x: Double): String = "${(x * 10).toLong() / 10.0}"

    private fun median(xs: List<Double>): Double? = if (xs.isEmpty()) null else xs.sorted().let { s -> if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
    private fun quantile(xs: List<Double>, q: Double): Double? = if (xs.isEmpty()) null else xs.sorted()[((xs.size - 1) * q).toInt()]

    /** `card.cog` per (lane, lemma), classified against today's record (the first-meeting snapshot is not stored yet). */
    fun classify(state: ClavisState, corpus: Corpus, registry: LanguageRegistry, now: Long): Map<Pair<String, String>, Cog> {
        val enabled = registry.order.filter { state.laneMode(it) != LaneMode.OFF }.map { it.code }.toSet()
        val out = HashMap<Pair<String, String>, Cog>()
        for ((l, cards) in state.cards) {
            if (l !in enabled) continue
            val lang = Lang(l)
            for ((lm, c) in cards) {
                if (c.reps < 1) continue
                val et = corpus.etym[lang]?.get(lm)
                val kin = et?.edges?.any { e -> e.lang.code in enabled && e.lang.code != l && held(state.cards[e.lang.code]?.get(e.lemma), now) } == true
                if (kin) { out[l to lm] = Cog.KIN; continue }
                val ids = corpus.byLemma[lang]?.get(lm).orEmpty()
                val look = ids.asSequence().flatMap { corpus.scene(it).k[lang].orEmpty().asSequence() }.filter { it.lemma == lm }
                    .flatMap { it.bridges.asSequence() }
                    .any { b -> b.lang.code in enabled && held(state.cards[b.lang.code]?.get(b.word.lowercase()), now) }
                out[l to lm] = if (look) Cog.LOOKALIKE else Cog.NONE
            }
        }
        return out
    }

    fun h1(state: ClavisState, corpus: Corpus, registry: LanguageRegistry, now: Long): Panel {
        val cog = classify(state, corpus, registry, now)
        // (a) first-attempt accuracy: the first cloze per (l, lm)
        val firstCloze = LinkedHashMap<Pair<String, String>, Int>()
        for (e in state.log) {
            if (e.type != "cloze") continue
            val lm = e.lm ?: continue
            val key = e.l to lm
            if (key !in firstCloze) firstCloze[key] = e.ok ?: 0
        }
        fun acc(k: Cog): Pair<Int, Int> { val xs = firstCloze.filter { cog[it.key] == k }.values; return xs.count { it == 1 } to xs.size }
        val (aOk, aN) = acc(Cog.KIN); val (bOk, bN) = acc(Cog.NONE); val (cOk, cN) = acc(Cog.LOOKALIKE)
        // (b) stability at matched review count (reps ≥ 3), today's s
        fun stab(k: Cog): List<Double> = state.cards.flatMap { (l, m) -> m.filter { (lm, c) -> c.reps >= 3 && cog[l to lm] == k }.values.map { it.s } }
        val s1 = median(stab(Cog.KIN)); val s2 = median(stab(Cog.NONE))
        // (c) lapses per return
        fun lapse(k: Cog): Pair<Int, Int> { val cs = state.cards.flatMap { (l, m) -> m.filter { (lm, c) -> c.reps >= 1 && cog[l to lm] == k }.values }; return cs.sumOf { it.lapses } to cs.sumOf { it.reps } }
        val (l1, r1) = lapse(Cog.KIN); val (l2, r2) = lapse(Cog.NONE)
        val fooled = state.inter.values.sumOf { it.values.sum() }
        val enough = aN >= MIN_N && bN >= MIN_N
        val lines = buildList {
            add("Words with a known relative: recalled first time ${pctStr(aOk, aN)} (n=$aN) · without: ${pctStr(bOk, bN)} (n=$bN) · look-alikes only: ${pctStr(cOk, cN)} (n=$cN)")
            add("Stability after 3 returns: ${s1?.let { d1(it) } ?: "—"} vs ${s2?.let { d1(it) } ?: "—"} days (n=${stab(Cog.KIN).size} / ${stab(Cog.NONE).size})")
            add("Lapses per return: ${if (r1 > 0) d1(l1.toDouble() / r1) else "—"} vs ${if (r2 > 0) d1(l2.toDouble() / r2) else "—"}")
            add("Look-alikes that fooled you: $fooled times")
        }
        return Panel(
            "H1", "Cognate vs non-cognate cost",
            "Words with a held relative in another lane cost less to acquire than words without one.",
            lines, "Equal cost, or more lapses for related words, would mean the links are interfering rather than helping.",
            listOf(7, 9), enough,
            note = "Relatives are classified against today's record, not at the first meeting — this build does not store `card.cog` yet.",
        )
    }

    fun h4(state: ClavisState, corpus: Corpus, now: Long): Panel {
        val withOrigin = state.cards.entries.sumOf { (l, m) -> m.count { (lm, c) -> c.reps >= 1 && !corpus.etym[Lang(l)]?.get(lm)?.origin.isNullOrEmpty() } }
        return Panel(
            "H4′", "Origin-surfaced stability",
            "Words whose origin you were shown (on the second meeting) settle more firmly than words whose origin was never shown.",
            listOf(
                "Words whose origin you were shown: stability — after 3 returns (n=0) · never shown: — (n=0)",
                "Words in your record that have an origin in the graph so far: $withOrigin",
                "Comparison shown: observational",
            ),
            "No difference — or worse form recall for origin-shown words — would mean the story is stealing attention from the word.",
            listOf(25, 26), enough = false,
            note = "This build does not yet record when an origin was first shown to you (`originShown`), so the two groups cannot be separated honestly.",
        )
    }

    fun h8(state: ClavisState): Panel {
        val taken = state.pathLog.size
        val newTaken = state.pathLog.count { it.kind == "new" }
        return Panel(
            "H8", "Branch-prediction accuracy",
            "When Paths promises that n more bubbles become readable, about n do.",
            listOf(
                "Paths promised — more readable bubbles across $newTaken branches; — came true",
                "Branches taken (last 12 remembered): $taken",
                "Sessions begun from a branch: — · from the pool: —",
            ),
            "Predictions that don't come true, or a fan you never use, would mean the graph is not a curriculum.",
            listOf(10, 11), enough = false,
            note = "This build keeps only the last 12 branches (`pathLog`); the uncapped `branchLog` with predicted and realised counts is not recorded yet.",
        )
    }

    fun h9(state: ClavisState, now: Long): Panel {
        // successful returns and the gap since the previous success, per (l, lm)
        data class Ev(val t: Long, val ok: Boolean)
        val byWord = HashMap<Pair<String, String>, MutableList<Ev>>()
        for (e in state.log) {
            val ok = when (e.type) { "cloze" -> (e.ok ?: 0) == 1; "echo" -> (e.g ?: 0) >= 3; else -> continue }
            val lm = e.lm ?: continue
            byWord.getOrPut(e.l to lm) { mutableListOf() }.add(Ev(e.t, ok))
        }
        val gaps = mutableListOf<Double>()
        var longOk = 0; var longN = 0
        for ((_, evs) in byWord) {
            evs.sortBy { it.t }
            var lastOk: Long? = null
            for (ev in evs) {
                val prev = lastOk
                if (prev != null) {
                    val gap = (ev.t - prev) / DAY_MS.toDouble()
                    if (gap >= 56) { longN++; if (ev.ok) longOk++ }
                    if (ev.ok) gaps.add(gap)
                }
                if (ev.ok) lastOk = ev.t
            }
        }
        val held56 = state.cards.values.sumOf { m -> m.values.count { !it.prov && it.s >= 56 } }
        val med = median(gaps); val q1 = quantile(gaps, 0.25); val q3 = quantile(gaps, 0.75)
        val lines = buildList {
            add("Gaps between successful returns: median ${med?.let { d1(it) } ?: "—"} days (25th–75th ${q1?.let { d1(it) } ?: "—"}–${q3?.let { d1(it) } ?: "—"}; n=${gaps.size})")
            add("Returns after a gap ≥ 56 days: $longOk/$longN recalled")
            add("Words held with stability ≥ 56 days: $held56")
            add("Resting cost so far: ≈ ${d1(held56 / 56.0)} retrievals a day to keep $held56 words")
        }
        return Panel(
            "H9", "Maintenance interval",
            "A resting language costs about one retrieval per word every eight weeks.",
            lines, "If words that rested about eight weeks mostly lapse, the maintenance dose is wrong.",
            listOf(27), enough = gaps.size >= MIN_N,
        )
    }
}
