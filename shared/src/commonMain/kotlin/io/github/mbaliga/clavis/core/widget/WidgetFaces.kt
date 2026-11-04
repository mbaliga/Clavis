package io.github.mbaliga.clavis.core.widget

import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Mask
import io.github.mbaliga.clavis.core.pct

/*
 * What each home-screen widget shows at each size, decided here as plain data so it is tested on
 * the JVM; `composeApp/src/androidMain/.../widget` only binds these faces to RemoteViews. The
 * layouts follow the constellation's widget templates: `stat` (a [TileFace]), `card`, `list` and
 * `pads`. Below a template's minimum size every widget collapses to one tappable summary line.
 */

/** The six widgets. */
enum class WidgetKind { WORD_DUE, POOL, RECORD, LAST_DIP, ORIGINS, LANGUAGES }

/**
 * Size classes, by the size in dp the launcher reports. Each threshold is the least height or
 * width at which that class's layouts fit at 1× font scale with every part that must stay — the
 * label, an "as of" time, the buttons — and with pads at least 48 dp tall (D-29); extra rows and
 * detail lines are the parts the layouts let go first. On Android's reference handset (cells of
 * (73n − 16) × (118m − 16) dp in portrait, (142n − 15) × (66.5m − 15) in landscape) a one-row strip
 * (102 dp) is TINY, 2×2 portrait is SMALL, 4×2 portrait (220) and 4×2 to 4×3 landscape (117–184)
 * are MEDIUM, and 4×3 portrait (337) or 4×4 landscape (250) is LARGE. API 31+ hands the same
 * thresholds to the system as a size map, and it picks the same class [pick] does.
 */
enum class WidgetSize(val minWidthDp: Float, val minHeightDp: Float) {
    /** A one-row strip, or anything narrower than two cells: one summary line. */
    TINY(100f, 40f),

    /**
     * ≈ 2×2: label, value, caption — or two pads. 112 dp = two 48 dp pads with the pads layouts'
     * 2 dp margins and 4 dp padding, which also holds the tile and the card's label, title line and
     * 48 dp button.
     */
    SMALL(100f, 112f),

    /** ≈ 4×2: the template proper (the list's header, three rows and its "as of" footer need about 110 dp). */
    MEDIUM(240f, 112f),

    /** ≈ 4×3 and taller: the template with its extra rows and a second action (the fullest card needs about 235 dp). */
    LARGE(240f, 240f),
    ;

    companion object {
        /** The class for a widget [widthDp] × [heightDp] (Android < 12: portrait min width × max height). */
        fun pick(widthDp: Float, heightDp: Float): WidgetSize = when {
            widthDp >= LARGE.minWidthDp && heightDp >= LARGE.minHeightDp -> LARGE
            widthDp >= MEDIUM.minWidthDp && heightDp >= MEDIUM.minHeightDp -> MEDIUM
            widthDp >= SMALL.minWidthDp && heightDp >= SMALL.minHeightDp -> SMALL
            else -> TINY
        }
    }
}

/**
 * Where a tap lands in the app. Carried as plain string extras on an explicit, immutable intent;
 * [parse] validates whatever arrives, since the activity is exported and anything can start it.
 */
sealed interface WidgetTap {
    /** The app as it is. */
    data object Open : WidgetTap

    /** A primary view: one of [VIEWS]. */
    data class View(val view: String) : WidgetTap

    /** A session; held in [lane] from its first encounter when the lane is active, otherwise an ordinary one. */
    data class Session(val lane: String?) : WidgetTap

    /** Cosmos, on one word (the "See its web" hand-off). */
    data class Word(val lane: String, val lemma: String) : WidgetTap

    /** The extras that carry this tap. */
    fun fields(): Map<String, String> = when (this) {
        Open -> mapOf(DEST to "open")
        is View -> mapOf(DEST to "view", VIEW to view)
        is Session -> if (lane == null) mapOf(DEST to "session") else mapOf(DEST to "session", LANE to lane)
        is Word -> mapOf(DEST to "word", LANE to lane, LEMMA to lemma)
    }

    companion object {
        const val DEST = "dest"
        const val VIEW = "view"
        const val LANE = "lane"
        const val LEMMA = "lemma"
        val VIEWS: Set<String> = setOf("learn", "you", "paths", "languages", "cosmos")
        private val LANE_CODE = Regex("^[a-z]{2,8}$")
        private const val MAX_LEMMA = 128

        /** The tap the [field] lookup describes, or null when anything in it is malformed. */
        fun parse(field: (String) -> String?): WidgetTap? {
            fun lane(): String? = field(LANE)?.takeIf { LANE_CODE.matches(it) }
            return when (field(DEST)) {
                "open" -> Open
                "view" -> field(VIEW)?.takeIf { it in VIEWS }?.let(::View)
                "session" -> {
                    val raw = field(LANE)
                    if (raw == null) Session(null) else lane()?.let { Session(it) }
                }
                "word" -> {
                    val l = lane() ?: return null
                    val lm = field(LEMMA)?.takeIf { it.isNotBlank() && it.length <= MAX_LEMMA && it.none { c -> c.isISOControl() } } ?: return null
                    Word(l, lm)
                }
                else -> null
            }
        }
    }
}

/** A button inside a widget. */
data class WidgetAction(val label: String, val tap: WidgetTap, val description: String)

sealed interface Face {
    /** Where a tap anywhere outside a button lands. */
    val tap: WidgetTap

    /** What TalkBack reads for the whole widget. */
    val description: String
}

/** The `stat` template: [compact] alone at TINY; [label], [value] and [caption] from SMALL up. */
data class TileFace(
    val label: String,
    val value: String,
    val caption: String?,
    val compact: String,
    override val tap: WidgetTap,
    override val description: String,
) : Face

/** The `card` template. */
data class CardFace(
    val label: String,
    val title: String,
    val body: String?,
    val meta: String?,
    val lines: List<String>,
    val footer: String?,
    val primary: WidgetAction?,
    val secondary: WidgetAction?,
    override val tap: WidgetTap,
    override val description: String,
) : Face

data class ListRow(val start: String, val end: String, val swatch: Long?)

/** The `list` template: a headline and up to five rows. */
data class ListFace(
    val label: String,
    val headline: String,
    val caption: String?,
    val rows: List<ListRow>,
    val footer: String?,
    override val tap: WidgetTap,
    override val description: String,
) : Face

data class Pad(val title: String, val sub: String, val swatch: Long?, val tap: WidgetTap, val description: String)

/** The `pads` template: two to six buttons. */
data class PadsFace(val pads: List<Pad>, override val tap: WidgetTap, override val description: String) : Face

/** One widget's face at each size class. */
data class WidgetRender(val tiny: Face, val small: Face, val medium: Face, val large: Face) {
    fun face(size: WidgetSize): Face = when (size) {
        WidgetSize.TINY -> tiny
        WidgetSize.SMALL -> small
        WidgetSize.MEDIUM -> medium
        WidgetSize.LARGE -> large
    }
}

/** What the widget process found on disk. */
sealed interface Glance {
    /** Nothing published yet: the app has not run since install, or its data was cleared. */
    data object Missing : Glance

    /** A file that does not decode, or one from a newer schema. */
    data object Unreadable : Glance

    data class Ready(val snapshot: WidgetSnapshot) : Glance

    companion object {
        fun of(text: String?): Glance {
            if (text == null) return Missing
            return WidgetSnapshots.decode(text)?.let(::Ready) ?: Unreadable
        }
    }
}

/**
 * The moment a widget is drawn: [now], the local calendar day, and the platform's formats for a
 * date and time ([time]) and for a date alone ([date], where a one-line tile has no room for both).
 */
class FaceClock(val now: Long, val dayIndex: Long, val date: (Long) -> String, val time: (Long) -> String) {
    constructor(now: Long, dayIndex: Long, time: (Long) -> String) : this(now, dayIndex, time, time)
}

/** A lane's due count at some moment; [atLeast] when the snapshot's list of later due times ran out. */
data class DueCount(val n: Int, val atLeast: Boolean)

/** Cards of this lane due at [now], from a snapshot built at [at]. */
fun LaneGlance.dueAt(at: Long, now: Long): DueCount {
    if (now <= at) return DueCount(dueNow, false)
    val passed = laterMin.count { at + it * WidgetSnapshots.MINUTE_MS <= now }
    return DueCount(dueNow + passed, moreLater && passed == laterMin.size)
}

object WidgetFaces {
    /** Rows shown by the `list` template at MEDIUM and LARGE. */
    const val LIST_ROWS_MEDIUM = 3
    const val LIST_ROWS_LARGE = 5

    /** Pads shown at SMALL, MEDIUM and LARGE. */
    const val PADS_SMALL = 2
    const val PADS_MEDIUM = 4
    const val PADS_LARGE = 6

    /** Other lanes listed under the word at LARGE. */
    const val OTHER_LANES = 3

    fun title(kind: WidgetKind): String = when (kind) {
        WidgetKind.WORD_DUE -> WidgetCopy.WORD_DUE
        WidgetKind.POOL -> WidgetCopy.POOL
        WidgetKind.RECORD -> WidgetCopy.RECORD
        WidgetKind.LAST_DIP -> WidgetCopy.LAST_DIP
        WidgetKind.ORIGINS -> WidgetCopy.ORIGINS
        WidgetKind.LANGUAGES -> WidgetCopy.LANGUAGES
    }

    fun render(kind: WidgetKind, glance: Glance, clock: FaceClock): WidgetRender {
        val s = when (glance) {
            Glance.Missing -> return message(kind, WidgetCopy.OPEN, WidgetCopy.FIRST_RUN, WidgetTap.Open)
            Glance.Unreadable -> return message(kind, WidgetCopy.OPEN, WidgetCopy.UNREADABLE, WidgetTap.Open)
            is Glance.Ready -> glance.snapshot
        }
        if (!s.onboarded) return message(kind, WidgetCopy.OPEN, WidgetCopy.NOT_ONBOARDED, WidgetTap.Open)
        if (s.lanes.isEmpty()) return message(kind, Copy.POOL_RESTING, WidgetCopy.ALL_RESTING, WidgetTap.View("languages"))
        return when (kind) {
            WidgetKind.WORD_DUE -> wordDue(s, clock)
            WidgetKind.POOL -> pool(s, clock)
            WidgetKind.RECORD -> record(s, clock)
            WidgetKind.LAST_DIP -> lastDip(s, clock)
            WidgetKind.ORIGINS -> origins(s, clock)
            WidgetKind.LANGUAGES -> languages(s, clock)
        }
    }

    /**
     * When the widgets next need drawing without the app: the next moment a card falls due, and,
     * with [midnight] (the next local midnight, when a word-origin widget is placed), the day's
     * word changing. Null when nothing will change on its own.
     */
    fun nextRefreshAt(glance: Glance, now: Long, midnight: Long?): Long? {
        val s = (glance as? Glance.Ready)?.snapshot
        val due = s?.lanes?.mapNotNull { lane ->
            lane.laterMin.asSequence().map { s.at + it * WidgetSnapshots.MINUTE_MS }.firstOrNull { it > now }
        }?.minOrNull()
        return listOfNotNull(due, midnight?.takeIf { it > now }).minOrNull()
    }

    // ------------------------------------------------------------------ shared pieces

    /**
     * TalkBack text: the visible parts without the shape marks, which carry no words of their own,
     * and with the cloze gap read as a word rather than four underscores.
     */
    fun spoken(vararg parts: String?): String =
        parts.filterNotNull()
            .map { it.replace(WidgetCopy.MARK_READY, "").replace(WidgetCopy.MARK_STILL, "").replace(Mask.GAP, WidgetCopy.GAP_SPOKEN).trim() }
            .filter { it.isNotEmpty() }.joinToString(". ")

    private fun message(kind: WidgetKind, value: String, text: String, tap: WidgetTap): WidgetRender {
        val label = title(kind)
        val tile = TileFace(label, value, text, text, tap, spoken(label, text))
        return WidgetRender(tile, tile, tile, tile)
    }

    /** A target-language form inside an English line, isolated so an Arabic word cannot reorder the line (`Copy.isolate`). */
    private fun isolated(s: String, rtl: Boolean): String = Copy.isolate(s, if (rtl) true else null)

    private fun dues(s: WidgetSnapshot, now: Long): List<Pair<LaneGlance, DueCount>> = s.lanes.map { it to it.dueAt(s.at, now) }

    private fun readyOrStill(d: DueCount): String = if (d.n > 0) WidgetCopy.ready(d.n, d.atLeast) else WidgetCopy.STILL

    // ------------------------------------------------------------------ Word due (`card`, read-only)

    private fun wordDue(s: WidgetSnapshot, clock: FaceClock): WidgetRender {
        val now = clock.now
        val dues = dues(s, now)
        val w = s.word?.takeIf { it.due <= now }
        val focus: Pair<LaneGlance, DueCount>? = w?.let { word -> dues.firstOrNull { it.first.l == word.l } }
            ?: dues.filter { it.second.n > 0 }.maxByOrNull { it.second.n }
        if (focus == null || focus.second.n == 0) return stillPool(s, clock)
        val (lane, d) = focus
        val prompt = w?.takeIf { it.l == lane.l }?.prompt
        val tap = WidgetTap.Session(lane.l)
        val returning = WidgetCopy.readyToReturn(d.n, d.atLeast)
        val label = WidgetCopy.wordDueIn(lane.name)
        val recall = WidgetAction(WidgetCopy.RECALL, tap, spoken(WidgetCopy.RECALL, WidgetCopy.startIn(lane.name)))
        val others = dues.filter { it.first.l != lane.l && it.second.n > 0 }.sortedByDescending { it.second.n }
            .take(OTHER_LANES).map { (o, od) -> WidgetCopy.alsoReady(o.name, od.n, od.atLeast) }
        val tiny = TileFace(
            label, lane.name, returning, "${WidgetCopy.MARK_READY} ${lane.name} · ${WidgetCopy.count(d.n, d.atLeast)} ready",
            tap, spoken(label, returning),
        )
        val small = TileFace(
            label, prompt ?: lane.name, "${WidgetCopy.MARK_READY} $returning", tiny.compact, tap,
            spoken(label, prompt, returning, WidgetCopy.DO_YOU_REMEMBER),
        )
        val medium = CardFace(
            label = label, title = prompt ?: returning, body = null,
            meta = if (prompt != null) "${WidgetCopy.MARK_READY} $returning" else WidgetCopy.DO_YOU_REMEMBER,
            lines = emptyList(), footer = null, primary = recall, secondary = null, tap = tap,
            description = spoken(label, prompt, returning, WidgetCopy.DO_YOU_REMEMBER),
        )
        val large = medium.copy(
            body = WidgetCopy.DO_YOU_REMEMBER,
            meta = if (prompt != null) "${WidgetCopy.MARK_READY} $returning" else null,
            lines = others,
            secondary = WidgetAction(WidgetCopy.OPEN_POOL, WidgetTap.View("learn"), WidgetCopy.OPEN_POOL),
            description = spoken(label, prompt, returning, WidgetCopy.DO_YOU_REMEMBER, *others.toTypedArray()),
        )
        return WidgetRender(tiny, small, medium, large)
    }

    /** Nothing is due: the pool's own honest words (IA §4.1), and when the next word returns. */
    private fun stillPool(s: WidgetSnapshot, clock: FaceClock): WidgetRender {
        val next = s.word?.due?.takeIf { it > clock.now }?.let { WidgetCopy.nextReturns(clock.time(it)) }
        val label = WidgetCopy.WORD_DUE
        val tap = WidgetTap.View("learn")
        val begin = WidgetAction(WidgetCopy.BEGIN, WidgetTap.Session(null), spoken(WidgetCopy.BEGIN, Copy.START_SESSION_ARIA))
        val tiny = TileFace(
            label, WidgetCopy.STILL_POOL_SHORT, WidgetCopy.NOTHING_OVERDUE, "${WidgetCopy.MARK_STILL} ${WidgetCopy.STILL_POOL_SHORT}",
            tap, spoken(label, WidgetCopy.STILL_POOL),
        )
        val small = tiny.copy(
            value = "${WidgetCopy.MARK_STILL} ${WidgetCopy.STILL_POOL_SHORT}",
            caption = listOfNotNull(WidgetCopy.NOTHING_OVERDUE, next).joinToString(" · "),
            description = spoken(label, WidgetCopy.STILL_POOL, next),
        )
        val medium = CardFace(
            label = label, title = WidgetCopy.STILL_POOL, body = null, meta = next ?: WidgetCopy.NEW_WORDS_NEXT,
            lines = emptyList(), footer = null, primary = begin, secondary = null, tap = tap,
            description = spoken(label, WidgetCopy.STILL_POOL, next ?: WidgetCopy.NEW_WORDS_NEXT),
        )
        val large = medium.copy(
            body = WidgetCopy.NEW_WORDS_NEXT, meta = next,
            secondary = WidgetAction(WidgetCopy.OPEN_POOL, tap, WidgetCopy.OPEN_POOL),
            description = spoken(label, WidgetCopy.STILL_POOL, WidgetCopy.NEW_WORDS_NEXT, next),
        )
        return WidgetRender(tiny, small, medium, large)
    }

    // ------------------------------------------------------------------ The pool (`stat` → `list`)

    private fun pool(s: WidgetSnapshot, clock: FaceClock): WidgetRender {
        val dues = dues(s, clock.now)
        val total = dues.sumOf { it.second.n }
        val atLeast = dues.any { it.second.atLeast }
        val k = dues.count { it.second.n > 0 }
        val tap = WidgetTap.View("learn")
        val label = WidgetCopy.POOL
        val headline = if (total > 0) WidgetCopy.ready(total, atLeast) else WidgetCopy.STILL
        val caption = if (total > 0) WidgetCopy.acrossLanguages(k) else WidgetCopy.NOTHING_OVERDUE
        val rows = dues.sortedByDescending { it.second.n }.map { (lane, d) -> ListRow(lane.name, readyOrStill(d), lane.colour) }
        val compact = if (total > 0) "${WidgetCopy.MARK_READY} ${WidgetCopy.readyToReturn(total, atLeast)}" else "${WidgetCopy.MARK_STILL} ${WidgetCopy.STILL_POOL_SHORT}"
        fun rowsSpoken(n: Int) = rows.take(n).map { "${it.start}, ${it.end}" }.toTypedArray()
        val tiny = TileFace(label, headline, caption, compact, tap, spoken(label, compact))
        val small = TileFace(label, headline, caption, compact, tap, spoken(label, headline, caption))
        val medium = ListFace(label, headline, null, rows.take(LIST_ROWS_MEDIUM), null, tap, spoken(label, headline, *rowsSpoken(LIST_ROWS_MEDIUM)))
        val largeCaption = if (total > 0) "${WidgetCopy.readyToReturn(total, atLeast)} ${WidgetCopy.acrossLanguages(k)}" else WidgetCopy.STILL_POOL
        val more = (rows.size - LIST_ROWS_LARGE).takeIf { it > 0 }?.let(WidgetCopy::moreLanguages)
        val large = ListFace(label, headline, largeCaption, rows.take(LIST_ROWS_LARGE), more, tap, spoken(label, largeCaption, *rowsSpoken(LIST_ROWS_LARGE), more))
        return WidgetRender(tiny, small, medium, large)
    }

    // ------------------------------------------------------------------ Your record (`stat` → `list`)

    private fun record(s: WidgetSnapshot, clock: FaceClock): WidgetRender {
        val held = s.lanes.sumOf { it.held }
        val asOf = WidgetCopy.asOf(clock.time(s.at))
        val tap = WidgetTap.View("you")
        val label = WidgetCopy.RECORD
        val rows = s.lanes.map { ListRow(it.name, "${WidgetCopy.held(it.held)} · ${WidgetCopy.readable(pct(it.readable))}", it.colour) }
        fun rowsSpoken(n: Int) = rows.take(n).map { "${it.start}, ${it.end}" }.toTypedArray()
        val heldWords = WidgetCopy.heldWords(held)
        // Held and readable decay with time, so even the one-line tile says when they were true;
        // the date alone fits a 2×1 strip, and a date is never wrong however late the redraw.
        val tiny = TileFace(label, "$held", WidgetCopy.WORDS_HELD, "$heldWords\n${WidgetCopy.asOf(clock.date(s.at))}", tap, spoken(label, heldWords, asOf))
        val small = TileFace(label, "$held", "${WidgetCopy.WORDS_HELD} · $asOf", heldWords, tap, spoken(label, heldWords, asOf))
        val medium = ListFace(label, heldWords, null, rows.take(LIST_ROWS_MEDIUM), asOf, tap, spoken(label, heldWords, *rowsSpoken(LIST_ROWS_MEDIUM), asOf))
        val more = (rows.size - LIST_ROWS_LARGE).takeIf { it > 0 }?.let(WidgetCopy::moreLanguages)
        val footer = listOfNotNull(more, asOf).joinToString(" · ")
        val large = ListFace(label, heldWords, WidgetCopy.READABLE_MEANS, rows.take(LIST_ROWS_LARGE), footer, tap, spoken(label, heldWords, *rowsSpoken(LIST_ROWS_LARGE), WidgetCopy.READABLE_MEANS, footer))
        return WidgetRender(tiny, small, medium, large)
    }

    // ------------------------------------------------------------------ Last dip (`stat` → `card`)

    private fun lastDip(s: WidgetSnapshot, clock: FaceClock): WidgetRender {
        val label = WidgetCopy.LAST_DIP
        val another = WidgetAction(WidgetCopy.ANOTHER, WidgetTap.Session(null), spoken(WidgetCopy.ANOTHER, Copy.START_SESSION_ARIA))
        val last = s.last
        if (last == null) {
            val tap = WidgetTap.View("learn")
            val begin = WidgetAction(WidgetCopy.BEGIN, WidgetTap.Session(null), spoken(WidgetCopy.BEGIN, Copy.START_SESSION_ARIA))
            val tile = TileFace(label, WidgetCopy.NO_DIP_YET, WidgetCopy.NO_DIP_YET_LONG, WidgetCopy.NO_DIP_YET, tap, spoken(label, WidgetCopy.NO_DIP_YET, WidgetCopy.NO_DIP_YET_LONG))
            val card = CardFace(label, WidgetCopy.NO_DIP_YET, null, WidgetCopy.NO_DIP_YET_LONG, emptyList(), null, begin, null, tap, tile.description)
            return WidgetRender(tile, tile, card, card)
        }
        val tap = WidgetTap.View("you")
        val time = clock.time(last.t)
        val score = WidgetCopy.correctOf(last.correct, last.n)
        val title = "$score ${WidgetCopy.UNDERSTOOD_OR_RECALLED}"
        val newWords = WidgetCopy.newWordsMet(last.newWords)
        val visited = WidgetCopy.languagesVisited(last.languages.size)
        val tiny = TileFace(label, score, WidgetCopy.UNDERSTOOD_OR_RECALLED, title, tap, spoken(label, title, time))
        val small = TileFace(label, score, "${WidgetCopy.UNDERSTOOD_OR_RECALLED} · $time", title, tap, spoken(label, title, time))
        val dipLabel = WidgetCopy.lastDipAt(time)
        val medium = CardFace(
            dipLabel, title, null, "$newWords · $visited", emptyList(), null, another, null, tap,
            spoken(dipLabel, title, newWords, visited),
        )
        val names = last.languages.joinToString(" · ").takeIf { it.isNotEmpty() }
        val large = CardFace(
            dipLabel, title, newWords, visited, listOfNotNull(names), null, another,
            WidgetAction(WidgetCopy.SEE_PATHS, WidgetTap.View("paths"), WidgetCopy.SEE_PATHS), tap,
            spoken(dipLabel, title, newWords, visited, names),
        )
        return WidgetRender(tiny, small, medium, large)
    }

    // ------------------------------------------------------------------ Word origins (`card`)

    /**
     * Today's word: the word [WidgetSnapshot.originPin] holds for this day when it is still a
     * candidate (`WidgetSnapshots.keepTodaysOrigin` sets it when the app publishes again within the
     * day), otherwise the candidates rotated once per local day.
     */
    fun originOfTheDay(s: WidgetSnapshot, dayIndex: Long): OriginWord? {
        if (s.words.isEmpty()) return null
        s.originPin?.takeIf { it.day == dayIndex }
            ?.let { p -> s.words.firstOrNull { it.l == p.l && it.lemma == p.lemma } }
            ?.let { return it }
        val i = ((dayIndex % s.words.size) + s.words.size) % s.words.size
        return s.words[i.toInt()]
    }

    private fun origins(s: WidgetSnapshot, clock: FaceClock): WidgetRender {
        val w = originOfTheDay(s, clock.dayIndex)
            ?: return message(WidgetKind.ORIGINS, Copy.DASH, WidgetCopy.NO_ORIGIN, WidgetTap.View("cosmos"))
        val tap = WidgetTap.Word(w.l, w.lemma)
        val label = if (s.wordsMet) WidgetCopy.metIn(w.lang) else WidgetCopy.commonIn(w.lang)
        val origin = WidgetCopy.originLine(w.origin, w.borrowed)
        val kindWord = if (w.borrowed) WidgetCopy.BORROWED else WidgetCopy.INHERITED
        val source = WidgetCopy.sourceLabel(w.source)
        val inline = isolated(w.surface, w.rtl)
        val web = WidgetAction(WidgetCopy.SEE_WEB, tap, spoken(WidgetCopy.SEE_WEB, w.surface))
        val tiny = TileFace(label, w.surface, origin, "$inline · ${w.lang}", tap, spoken(label, w.surface, origin))
        val small = TileFace(label, w.surface, origin, tiny.compact, tap, spoken(label, w.surface, w.reading, origin))
        // The medium card has no body line: the origin leads the meta beside the button ("grew from"
        // and "borrowed from" already say which kind it is), and a reading follows the word as the
        // app writes it elsewhere ("casa", "猫 (neko)").
        val mediumTitle = w.reading?.let { "$inline ($it)" } ?: w.surface
        val meta = listOfNotNull(origin, source).joinToString(" · ")
        val medium = CardFace(label, mediumTitle, null, meta, emptyList(), null, web, null, tap, spoken(label, w.surface, w.reading, origin, source))
        val rels = w.relations.map { WidgetCopy.relation(isolated(it.surface, it.rtl), it.lang, it.kind) }
        val large = CardFace(
            label, w.surface, origin, w.reading, rels, listOfNotNull(kindWord, source?.let(WidgetCopy::source)).joinToString(" · "),
            web, null, tap, spoken(label, w.surface, w.reading, origin, *rels.toTypedArray(), kindWord, source),
        )
        return WidgetRender(tiny, small, medium, large)
    }

    // ------------------------------------------------------------------ Your languages (`pads`)

    private fun languages(s: WidgetSnapshot, clock: FaceClock): WidgetRender {
        val dues = dues(s, clock.now)
        val total = dues.sumOf { it.second.n }
        val atLeast = dues.any { it.second.atLeast }
        val tap = WidgetTap.View("learn")
        val summary = "${WidgetCopy.languagesCount(dues.size)} · ${if (total > 0) WidgetCopy.ready(total, atLeast) else WidgetCopy.STILL}"
        val tiny = TileFace(WidgetCopy.LANGUAGES, summary, null, summary, tap, spoken(WidgetCopy.LANGUAGES, summary))
        fun pad(lane: LaneGlance, d: DueCount): Pad {
            val sub = readyOrStill(d)
            return Pad(lane.name, sub, lane.colour, WidgetTap.Session(lane.l), spoken(lane.name, sub, WidgetCopy.startIn(lane.name)))
        }
        fun pads(n: Int): PadsFace {
            val list = if (dues.size <= n) {
                dues.map { (l, d) -> pad(l, d) }
            } else {
                val rest = dues.size - (n - 1)
                dues.take(n - 1).map { (l, d) -> pad(l, d) } +
                    Pad(WidgetCopy.MORE, WidgetCopy.moreLanguages(rest), null, tap, spoken(WidgetCopy.MORE, WidgetCopy.moreLanguages(rest)))
            }
            return PadsFace(list, tap, spoken(WidgetCopy.LANGUAGES, summary))
        }
        return WidgetRender(tiny, pads(PADS_SMALL), pads(PADS_MEDIUM), pads(PADS_LARGE))
    }
}
