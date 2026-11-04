package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.widget.CardFace
import io.github.mbaliga.clavis.core.widget.DueWord
import io.github.mbaliga.clavis.core.widget.Face
import io.github.mbaliga.clavis.core.widget.FaceClock
import io.github.mbaliga.clavis.core.widget.Glance
import io.github.mbaliga.clavis.core.widget.LaneGlance
import io.github.mbaliga.clavis.core.widget.LastDip
import io.github.mbaliga.clavis.core.widget.ListFace
import io.github.mbaliga.clavis.core.widget.OriginWord
import io.github.mbaliga.clavis.core.widget.PadsFace
import io.github.mbaliga.clavis.core.widget.Relation
import io.github.mbaliga.clavis.core.widget.RelationKind
import io.github.mbaliga.clavis.core.widget.TileFace
import io.github.mbaliga.clavis.core.widget.WidgetCopy
import io.github.mbaliga.clavis.core.widget.WidgetFaces
import io.github.mbaliga.clavis.core.widget.WidgetKind
import io.github.mbaliga.clavis.core.widget.WidgetSize
import io.github.mbaliga.clavis.core.widget.WidgetSnapshot
import io.github.mbaliga.clavis.core.widget.WidgetSnapshots
import io.github.mbaliga.clavis.core.widget.WidgetTap
import io.github.mbaliga.clavis.core.widget.dueAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** What each widget shows at each size, in which words, and where a tap lands (docs/widgets.md). */
class WidgetFacesTest {
    private val at = Fx.T0
    private val min = WidgetSnapshots.MINUTE_MS

    private fun clock(now: Long = at, day: Long = 0) = FaceClock(now, day) { t -> "T$t" }

    private fun lane(l: String, name: String, due: Int = 0, later: List<Int> = emptyList(), more: Boolean = false, held: Int = 0, readable: Double = 0.0) =
        LaneGlance(l, name, 0xFF112233L, dueNow = due, laterMin = later, moreLater = more, held = held, readable = readable, tested = true)

    private val es = lane("es", "Spanish", due = 2, held = 120, readable = 0.34)
    private val fr = lane("fr", "French", due = 1, held = 40, readable = 0.2)
    private val de = lane("de", "German", held = 7, readable = 0.012)

    private fun snap(
        lanes: List<LaneGlance> = listOf(es, fr, de),
        word: DueWord? = DueWord("es", "gato", at - 1000, "El ${Mask.GAP} come"),
        last: LastDip? = null,
        words: List<OriginWord> = emptyList(),
        met: Boolean = true,
        onboarded: Boolean = true,
    ) = WidgetSnapshot(at = at, onboarded = onboarded, lanes = lanes, word = word, last = last, words = words, wordsMet = met)

    private fun render(kind: WidgetKind, s: WidgetSnapshot, now: Long = at, day: Long = 0) = WidgetFaces.render(kind, Glance.Ready(s), clock(now, day))

    /** Every string a face would draw or speak. */
    private fun texts(f: Face): List<String> = when (f) {
        is TileFace -> listOfNotNull(f.label, f.value, f.caption, f.compact)
        is CardFace -> listOfNotNull(f.label, f.title, f.body, f.meta, f.footer, f.primary?.label, f.primary?.description, f.secondary?.label, f.secondary?.description) + f.lines
        is ListFace -> listOfNotNull(f.label, f.headline, f.caption, f.footer) + f.rows.flatMap { listOf(it.start, it.end) }
        is PadsFace -> f.pads.flatMap { listOf(it.title, it.sub, it.description) }
    } + f.description

    /** What `WidgetViews` puts on screen for [f] at [size]: the layout for each size binds only these. */
    private fun visible(f: Face, size: WidgetSize): List<String> = when (f) {
        is TileFace -> if (size == WidgetSize.TINY) listOf(f.compact) else listOfNotNull(f.label, f.value, f.caption)
        is CardFace -> if (size == WidgetSize.LARGE) {
            listOfNotNull(f.label, f.title, f.body, f.meta, f.footer, f.primary?.label, f.secondary?.label) + f.lines
        } else {
            listOfNotNull(f.label, f.title, f.meta, f.primary?.label)
        }
        is ListFace -> listOfNotNull(f.label, f.headline, f.caption.takeIf { size == WidgetSize.LARGE }, f.footer) +
            f.rows.take(if (size == WidgetSize.LARGE) 5 else 3).flatMap { listOf(it.start, it.end) }
        is PadsFace -> f.pads.take(padsAt(size)).flatMap { listOf(it.title, it.sub) }
    }

    private fun padsAt(size: WidgetSize) = when (size) {
        WidgetSize.TINY, WidgetSize.SMALL -> WidgetFaces.PADS_SMALL
        WidgetSize.MEDIUM -> WidgetFaces.PADS_MEDIUM
        WidgetSize.LARGE -> WidgetFaces.PADS_LARGE
    }

    /** A spread of snapshots that reaches every face of every widget. */
    private fun everyState(): List<Glance> = listOf(
        snap(), snap(word = null, lanes = listOf(de)), snap(last = LastDip(at, 4, 5, 2, listOf("Spanish"))),
        snap(words = listOf(casa, kitab, neko)), snap(words = listOf(casa), met = false), snap(onboarded = false), snap(lanes = emptyList()),
        snap(lanes = ('a'..'h').map { lane("x$it", "Lang $it", held = 3) }, word = null),
    ).map { Glance.Ready(it) } + Glance.Missing + Glance.Unreadable

    // ------------------------------------------------------------------ sizes and taps

    /** Android's reference handset: (73n − 16) × (118m − 16) dp in portrait, (142n − 15) × (66.5m − 15) in landscape. */
    @Test fun sizeClassesFollowTheSizesHostsReport() {
        assertEquals(WidgetSize.TINY, WidgetSize.pick(57f, 102f)) // 1×1 portrait
        assertEquals(WidgetSize.TINY, WidgetSize.pick(130f, 102f)) // 2×1 portrait
        assertEquals(WidgetSize.TINY, WidgetSize.pick(276f, 102f)) // 4×1 portrait: a one-row strip collapses
        assertEquals(WidgetSize.TINY, WidgetSize.pick(269f, 51f)) // 2×1 landscape
        assertEquals(WidgetSize.TINY, WidgetSize.pick(554f, 51f)) // 4×1 landscape
        assertEquals(WidgetSize.TINY, WidgetSize.pick(90f, 400f))
        assertEquals(WidgetSize.SMALL, WidgetSize.pick(130f, 220f)) // 2×2 portrait
        assertEquals(WidgetSize.SMALL, WidgetSize.pick(203f, 337f)) // 3×3 portrait
        assertEquals(WidgetSize.MEDIUM, WidgetSize.pick(276f, 220f)) // 4×2 portrait, the default
        assertEquals(WidgetSize.MEDIUM, WidgetSize.pick(269f, 117f)) // 2×2 landscape
        assertEquals(WidgetSize.MEDIUM, WidgetSize.pick(554f, 117f)) // 4×2 landscape
        assertEquals(WidgetSize.MEDIUM, WidgetSize.pick(554f, 184f)) // 4×3 landscape
        assertEquals(WidgetSize.LARGE, WidgetSize.pick(276f, 337f)) // 4×3 portrait
        assertEquals(WidgetSize.LARGE, WidgetSize.pick(554f, 250f)) // 4×4 landscape
        assertEquals(WidgetSize.LARGE, WidgetSize.pick(400f, 400f))
    }

    @Test fun nothingAFaceCarriesIsDroppedByTheLayoutForItsSize() {
        for (g in everyState()) for (kind in WidgetKind.entries) for (size in WidgetSize.entries) {
            when (val f = WidgetFaces.render(kind, g, clock()).face(size)) {
                is CardFace -> if (size == WidgetSize.LARGE) {
                    assertTrue(f.lines.size <= 3, "$kind/$size")
                } else { // widget_card_medium has no body, detail lines, footer or second button
                    assertNull(f.body, "$kind/$size"); assertTrue(f.lines.isEmpty(), "$kind/$size")
                    assertNull(f.footer, "$kind/$size"); assertNull(f.secondary, "$kind/$size")
                }
                is ListFace -> {
                    assertTrue(f.rows.size <= if (size == WidgetSize.LARGE) 5 else 3, "$kind/$size")
                    if (size != WidgetSize.LARGE) assertNull(f.caption, "$kind/$size") // widget_list_medium has no caption
                }
                is PadsFace -> assertTrue(f.pads.size <= padsAt(size), "$kind/$size")
                is TileFace -> Unit
            }
        }
    }

    @Test fun everyWidgetHasAFaceAtEverySizeAndCollapsesToOneLineBelowItsTemplate() {
        for (kind in WidgetKind.entries) {
            val r = render(kind, snap(last = LastDip(at, 4, 5, 2, listOf("Spanish")), words = listOf(casa)))
            for (size in WidgetSize.entries) assertTrue(r.face(size).description.isNotBlank(), "$kind $size")
            assertIs<TileFace>(r.tiny, "$kind collapses at TINY")
        }
        assertIs<CardFace>(render(WidgetKind.WORD_DUE, snap()).medium)
        assertIs<ListFace>(render(WidgetKind.POOL, snap()).large)
        assertIs<PadsFace>(render(WidgetKind.LANGUAGES, snap()).small)
    }

    @Test fun tapsRoundTripThroughExtrasAndJunkIsRefused() {
        val taps = listOf(WidgetTap.Open, WidgetTap.View("you"), WidgetTap.Session(null), WidgetTap.Session("es"), WidgetTap.Word("ja", "猫"))
        for (t in taps) assertEquals(t, WidgetTap.parse { t.fields()[it] })
        fun parse(vararg kv: Pair<String, String>) = WidgetTap.parse { k -> kv.toMap()[k] }
        assertNull(parse())
        assertNull(parse("dest" to "delete"))
        assertNull(parse("dest" to "view", "view" to "set")) // not a destination a widget offers
        assertNull(parse("dest" to "view"))
        assertNull(parse("dest" to "session", "lane" to "ES"))
        assertNull(parse("dest" to "session", "lane" to "e"))
        assertNull(parse("dest" to "session", "lane" to "../x"))
        assertNull(parse("dest" to "word", "lane" to "es"))
        assertNull(parse("dest" to "word", "lane" to "es", "lemma" to " "))
        assertNull(parse("dest" to "word", "lane" to "es", "lemma" to "a\u0000b"))
        assertNull(parse("dest" to "word", "lane" to "es", "lemma" to "x".repeat(129)))
    }

    // ------------------------------------------------------------------ honest states

    @Test fun beforeAnythingIsPublishedEveryWidgetSaysSoAndOpensTheApp() {
        for (kind in WidgetKind.entries) {
            for ((glance, text) in listOf(Glance.Missing to WidgetCopy.FIRST_RUN, Glance.Unreadable to WidgetCopy.UNREADABLE)) {
                val r = WidgetFaces.render(kind, glance, clock())
                for (size in WidgetSize.entries) {
                    val f = assertIs<TileFace>(r.face(size))
                    assertEquals(text, f.caption); assertEquals(text, f.compact); assertEquals(WidgetTap.Open, f.tap)
                    assertEquals(WidgetFaces.title(kind), f.label)
                }
            }
            val notYet = assertIs<TileFace>(render(kind, snap(onboarded = false)).medium)
            assertEquals(WidgetCopy.NOT_ONBOARDED, notYet.caption)
            val resting = assertIs<TileFace>(render(kind, snap(lanes = emptyList())).large)
            assertEquals(Copy.ALL_RESTING, resting.caption); assertEquals(WidgetTap.View("languages"), resting.tap)
        }
    }

    @Test fun dueCountsMoveWithTheClockWithoutTheApp() {
        val l = lane("es", "Spanish", due = 1, later = listOf(10, 60), more = true)
        assertEquals(1, l.dueAt(at, at).n)
        assertEquals(1, l.dueAt(at, at - 5_000).n) // a clock set back never invents due cards
        assertEquals(2, l.dueAt(at, at + 30 * min).n)
        assertFalse(l.dueAt(at, at + 30 * min).atLeast)
        val all = l.dueAt(at, at + 2 * 60 * min)
        assertEquals(3, all.n); assertTrue(all.atLeast) // past the end of a capped list: "3+"
        val p = render(WidgetKind.POOL, snap(lanes = listOf(l)), now = at + 2 * 60 * min)
        assertEquals("● 3+ ready", assertIs<TileFace>(p.small).value)
    }

    // ------------------------------------------------------------------ Word due

    @Test fun wordDueShowsTheGappedLineAndOpensASessionInItsLane() {
        val r = render(WidgetKind.WORD_DUE, snap())
        val m = assertIs<CardFace>(r.medium)
        assertEquals("Word due · Spanish", m.label)
        assertEquals("El ${Mask.GAP} come", m.title)
        assertEquals("● 2 words ready to return", m.meta)
        assertEquals(WidgetCopy.RECALL, m.primary?.label)
        assertEquals(WidgetTap.Session("es"), m.primary?.tap); assertEquals(WidgetTap.Session("es"), m.tap)
        assertEquals("● Spanish · 2 ready", assertIs<TileFace>(r.tiny).compact)
        val s = assertIs<TileFace>(r.small)
        assertEquals("El ${Mask.GAP} come", s.value); assertEquals("● 2 words ready to return", s.caption)
        val l = assertIs<CardFace>(r.large)
        assertEquals("do you remember?", l.body)
        assertEquals(listOf("French · ● 1 ready"), l.lines) // other lanes with something due, German is still
        assertEquals(WidgetTap.View("learn"), l.secondary?.tap)
        assertEquals("Word due · Spanish. El blank come. 2 words ready to return. do you remember?", m.description) // no underscores read aloud
    }

    @Test fun wordDueNeverShowsTheWordItAsksFor() {
        val r = render(WidgetKind.WORD_DUE, snap())
        for (size in WidgetSize.entries) for (t in texts(r.face(size))) assertFalse("gato" in t, "$size: $t")
    }

    @Test fun wordDueWithoutALineStillNamesTheLaneAndCount() {
        val r = render(WidgetKind.WORD_DUE, snap(word = DueWord("es", "pato", at - 1, prompt = null)))
        val m = assertIs<CardFace>(r.medium)
        assertEquals("2 words ready to return", m.title); assertEquals("do you remember?", m.meta)
        assertEquals("Spanish", assertIs<TileFace>(r.small).value)
    }

    @Test fun nothingDueIsAStillPoolThatSaysWhenTheNextWordReturns() {
        val quiet = listOf(lane("es", "Spanish", later = listOf(90)), de)
        val s = snap(lanes = quiet, word = DueWord("es", "gato", at + 90 * min, "El ${Mask.GAP} come"))
        val r = render(WidgetKind.WORD_DUE, s)
        val m = assertIs<CardFace>(r.medium)
        assertEquals(WidgetCopy.STILL_POOL, m.title)
        assertEquals("the next word returns T${at + 90 * min}", m.meta)
        assertEquals(WidgetCopy.BEGIN, m.primary?.label); assertEquals(WidgetTap.Session(null), m.primary?.tap)
        assertEquals(WidgetTap.View("learn"), m.tap)
        assertEquals("○ A still pool", assertIs<TileFace>(r.tiny).compact)
        assertFalse(texts(m).any { "gato" in it || Mask.GAP in it }) // no line before the word is due
        // ninety minutes later, with the app still closed, the same snapshot shows the word
        val later = render(WidgetKind.WORD_DUE, s, now = at + 90 * min)
        assertEquals("El ${Mask.GAP} come", assertIs<CardFace>(later.medium).title)
    }

    // ------------------------------------------------------------------ The pool

    @Test fun thePoolSaysDueOrStillInWordsPerLanguage() {
        val r = render(WidgetKind.POOL, snap())
        val s = assertIs<TileFace>(r.small)
        assertEquals("● 3 ready", s.value); assertEquals("across 2 languages", s.caption)
        val m = assertIs<ListFace>(r.medium)
        assertEquals(listOf("Spanish" to "● 2 ready", "French" to "● 1 ready", "German" to "○ still"), m.rows.map { it.start to it.end })
        assertEquals(WidgetTap.View("learn"), m.tap)
        val still = render(WidgetKind.POOL, snap(lanes = listOf(de), word = null))
        assertEquals("○ still", assertIs<TileFace>(still.small).value)
        assertEquals(WidgetCopy.NOTHING_OVERDUE, assertIs<TileFace>(still.small).caption)
        assertEquals(WidgetCopy.STILL_POOL, assertIs<ListFace>(still.large).caption)
        assertEquals("○ A still pool", assertIs<TileFace>(still.tiny).compact)
    }

    @Test fun thePoolListsFiveLanesThenCountsTheRest() {
        val many = ('a'..'g').map { lane("x$it", "Lang $it") }
        val r = render(WidgetKind.POOL, snap(lanes = many, word = null))
        assertEquals(3, assertIs<ListFace>(r.medium).rows.size)
        val l = assertIs<ListFace>(r.large)
        assertEquals(5, l.rows.size); assertEquals("and 2 more languages", l.footer)
    }

    // ------------------------------------------------------------------ Your record

    @Test fun theRecordIsHeldAndReadableWithItsAsOfTime() {
        val r = WidgetFaces.render(WidgetKind.RECORD, Glance.Ready(snap()), FaceClock(at, 0, { t -> "D$t" }, { t -> "T$t" }))
        assertEquals("167 words held\nas of D$at", assertIs<TileFace>(r.tiny).compact) // the one-line tile: the date alone
        val s = assertIs<TileFace>(r.small)
        assertEquals("167", s.value); assertEquals("words held · as of T$at", s.caption)
        val m = assertIs<ListFace>(r.medium)
        assertEquals(listOf("120 held · 34% readable", "40 held · 20% readable", "7 held · 1% readable"), m.rows.map { it.end })
        assertEquals("as of T$at", m.footer)
        assertEquals(WidgetTap.View("you"), m.tap)
        val l = assertIs<ListFace>(r.large)
        assertEquals(WidgetCopy.READABLE_MEANS, l.caption); assertEquals("as of T$at", l.footer)
    }

    /** Held and readable decay with time, so every size of Your record shows when they were true — on screen, not only to TalkBack. */
    @Test fun theRecordShowsItsAsOfTimeAtEverySize() {
        for (s in listOf(snap(), snap(lanes = ('a'..'h').map { lane("x$it", "Lang $it", held = 3) }))) {
            val r = render(WidgetKind.RECORD, s)
            for (size in WidgetSize.entries) assertTrue(visible(r.face(size), size).any { "as of" in it }, "$size: ${visible(r.face(size), size)}")
        }
    }

    // ------------------------------------------------------------------ Last dip

    @Test fun lastDipBeforeAndAfterTheFirstSession() {
        val none = render(WidgetKind.LAST_DIP, snap())
        assertEquals(WidgetCopy.NO_DIP_YET, assertIs<TileFace>(none.small).value)
        assertEquals(WidgetTap.Session(null), assertIs<CardFace>(none.medium).primary?.tap)
        val r = render(WidgetKind.LAST_DIP, snap(last = LastDip(at - 5000, 4, 5, 1, listOf("Spanish", "French"))))
        val m = assertIs<CardFace>(r.medium)
        assertEquals("Last dip · T${at - 5000}", m.label)
        assertEquals("4/5 understood or recalled", m.title)
        assertEquals("1 new word met · 2 languages visited", m.meta)
        assertEquals(Copy.ANOTHER, m.primary?.label); assertEquals(WidgetTap.Session(null), m.primary?.tap)
        assertEquals(WidgetTap.View("you"), m.tap)
        val l = assertIs<CardFace>(r.large)
        assertEquals(listOf("Spanish · French"), l.lines)
        assertEquals(Copy.SEE_PATHS, l.secondary?.label); assertEquals(WidgetTap.View("paths"), l.secondary?.tap)
        assertEquals("4/5", assertIs<TileFace>(r.small).value)
    }

    // ------------------------------------------------------------------ Word origins

    private val casa = OriginWord(
        l = "es", lemma = "casa", surface = "casa", lang = "Spanish", origin = "Latin casa", borrowed = false, source = "wiktionary+etymdb",
        relations = listOf(Relation("chez", "French", RelationKind.KIN), Relation("case", "French", RelationKind.BORROWED)),
    )
    private val kitab = OriginWord(l = "ar", lemma = "كتاب", surface = "كتاب", lang = "Arabic", origin = "Proto-Semitic *k-t-b", borrowed = true, rtl = true)
    private val neko = OriginWord(l = "ja", lemma = "猫", surface = "猫", reading = "neko", lang = "Japanese", origin = "Old Japanese neko", borrowed = false)

    @Test fun theOriginWidgetShowsOneWordADayAndOpensIt() {
        val s = snap(words = listOf(casa, kitab, neko))
        assertEquals(casa, WidgetFaces.originOfTheDay(s, 0)); assertEquals(kitab, WidgetFaces.originOfTheDay(s, 1))
        assertEquals(casa, WidgetFaces.originOfTheDay(s, 3)); assertEquals(neko, WidgetFaces.originOfTheDay(s, -1))
        val r = render(WidgetKind.ORIGINS, s, day = 0)
        val m = assertIs<CardFace>(r.medium)
        assertEquals("Spanish · a word you've met", m.label)
        assertEquals("casa", m.title)
        assertEquals("grew from Latin casa · Wiktionary + EtymDB", m.meta) // the origin leads the line beside the button
        assertTrue("grew from Latin casa" in visible(m, WidgetSize.MEDIUM).joinToString("|"))
        assertEquals(WidgetTap.Word("es", "casa"), m.tap); assertEquals(WidgetCopy.SEE_WEB, m.primary?.label)
        val l = assertIs<CardFace>(r.large)
        assertEquals(listOf("${Copy.isolate("chez")} · French · kin", "${Copy.isolate("case")} · French · borrowed"), l.lines)
        assertEquals("inherited · source: Wiktionary + EtymDB", l.footer)
        assertEquals("grew from Latin casa", l.body)
        val ar = render(WidgetKind.ORIGINS, s, day = 1)
        assertEquals("borrowed from Proto-Semitic *k-t-b", assertIs<CardFace>(ar.medium).meta)
        assertEquals("${Copy.isolate("كتاب", true)} · Arabic", assertIs<TileFace>(ar.tiny).compact) // an Arabic word cannot reorder the line
        val ja = assertIs<CardFace>(render(WidgetKind.ORIGINS, s, day = 2).medium)
        assertEquals("${Copy.isolate("猫")} (neko)", ja.title); assertEquals("grew from Old Japanese neko", ja.meta)
        val common = render(WidgetKind.ORIGINS, snap(words = listOf(casa), met = false))
        assertEquals("Spanish · a common word", assertIs<CardFace>(common.medium).label)
    }

    @Test fun theWordOfTheDayHoldsWhenTheAppPublishesAgainThatDay() {
        val morning = snap(words = listOf(casa, kitab))
        assertEquals(kitab, WidgetFaces.originOfTheDay(morning, 1))
        // a session meets a new word with an origin, and the rebuilt list would rotate to another word
        val evening = snap(words = listOf(neko, casa, kitab))
        assertEquals(casa, WidgetFaces.originOfTheDay(evening, 1))
        val kept = WidgetSnapshots.keepTodaysOrigin(evening, morning, 1)
        assertEquals(kitab, WidgetFaces.originOfTheDay(kept, 1))
        assertEquals(kitab.surface, assertIs<CardFace>(render(WidgetKind.ORIGINS, kept, day = 1).medium).title)
        assertEquals(kitab, WidgetFaces.originOfTheDay(WidgetSnapshots.keepTodaysOrigin(snap(words = listOf(neko, kitab)), kept, 1), 1)) // and again
        assertEquals(WidgetFaces.originOfTheDay(evening, 2), WidgetFaces.originOfTheDay(kept, 2)) // the next day the rotation resumes
        assertNull(WidgetSnapshots.keepTodaysOrigin(snap(words = listOf(neko, casa)), morning, 1).originPin) // no longer a candidate: it gives way
        assertNull(WidgetSnapshots.keepTodaysOrigin(evening, null, 1).originPin)
        assertEquals(kept, WidgetSnapshots.decode(WidgetSnapshots.encode(kept)))
    }

    @Test fun whereTheGraphIsSilentTheOriginWidgetSaysSo() {
        val r = render(WidgetKind.ORIGINS, snap(words = emptyList()))
        assertEquals(WidgetCopy.NO_ORIGIN, assertIs<TileFace>(r.medium).caption)
    }

    // ------------------------------------------------------------------ Your languages

    @Test fun languagePadsStartASessionInTheirLaneAndSayMoreWhenTheyRunOut() {
        val three = render(WidgetKind.LANGUAGES, snap())
        val m = assertIs<PadsFace>(three.medium)
        assertEquals(listOf("Spanish" to "● 2 ready", "French" to "● 1 ready", "German" to "○ still"), m.pads.map { it.title to it.sub })
        assertEquals(listOf(WidgetTap.Session("es"), WidgetTap.Session("fr"), WidgetTap.Session("de")), m.pads.map { it.tap })
        assertEquals("Spanish. 2 ready. start a session in Spanish", m.pads[0].description)
        val small = assertIs<PadsFace>(three.small)
        assertEquals(listOf("Spanish", WidgetCopy.MORE), small.pads.map { it.title })
        assertEquals("and 2 more languages", small.pads[1].sub); assertEquals(WidgetTap.View("learn"), small.pads[1].tap)
        assertEquals("3 languages · ● 3 ready", assertIs<TileFace>(three.tiny).compact)
        val eight = render(WidgetKind.LANGUAGES, snap(lanes = ('a'..'h').map { lane("x$it", "Lang $it") }, word = null))
        val l = assertIs<PadsFace>(eight.large)
        assertEquals(6, l.pads.size); assertEquals("and 3 more languages", l.pads.last().sub)
        assertEquals(WidgetFaces.PADS_LARGE, l.pads.size)
    }

    // ------------------------------------------------------------------ redraw scheduling

    @Test fun theNextRedrawIsTheNextDueMomentOrMidnight() {
        val s = snap(lanes = listOf(lane("es", "Spanish", later = listOf(10, 60)), lane("fr", "French", later = listOf(45))))
        val g = Glance.Ready(s)
        assertEquals(at + 10 * min, WidgetFaces.nextRefreshAt(g, at, null))
        assertEquals(at + 45 * min, WidgetFaces.nextRefreshAt(g, at + 20 * min, null))
        assertEquals(at + 10 * min, WidgetFaces.nextRefreshAt(g, at, at + 15 * min)) // a card falls due before midnight
        assertEquals(at + 5 * min, WidgetFaces.nextRefreshAt(g, at, at + 5 * min)) // midnight comes first
        assertNull(WidgetFaces.nextRefreshAt(g, at + 61 * min, null))
        assertNull(WidgetFaces.nextRefreshAt(Glance.Missing, at, null))
        assertEquals(at + 5 * min, WidgetFaces.nextRefreshAt(Glance.Missing, at, at + 5 * min))
    }

    // ------------------------------------------------------------------ copy rules and accessibility

    /** CONTRIBUTING "Copy rules (D-18)", plus D-15's mechanics. */
    private val forbidden = listOf(
        "subconscious", "subliminal", "absorb", "passive", "in your sleep", "without noticing", "effortless", "fluent", "fluency",
        "master", "% of the language", "level", "streak", "proven", "scientifically", "league", "don't lose", "keep it up",
    )
    private val forbiddenWords = listOf(Regex("\\bXP\\b"), Regex("\\b[ABC][12]\\b"))

    @Test fun everyWidgetStringKeepsTheCopyRulesAndSpeaksWithoutShapes() {
        var checked = 0
        for (g in everyState()) for (kind in WidgetKind.entries) for (day in 0L..2L) {
            val r = WidgetFaces.render(kind, g, clock(day = day))
            for (size in WidgetSize.entries) {
                val f = r.face(size)
                for (t in texts(f)) {
                    checked++
                    val low = t.lowercase()
                    forbidden.firstOrNull { it in low }?.let { fail("'$it' in $kind/$size: $t") }
                    forbiddenWords.firstOrNull { it.containsMatchIn(t) }?.let { fail("${it.pattern} in $kind/$size: $t") }
                }
                assertFalse(WidgetCopy.MARK_READY in f.description || WidgetCopy.MARK_STILL in f.description, "$kind/$size: ${f.description}")
                if (f is PadsFace) for (p in f.pads) assertFalse(WidgetCopy.MARK_READY in p.description || WidgetCopy.MARK_STILL in p.description)
            }
        }
        assertTrue(checked > 1000)
    }
}
