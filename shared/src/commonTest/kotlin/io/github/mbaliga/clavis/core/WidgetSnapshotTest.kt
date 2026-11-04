package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.widget.Glance
import io.github.mbaliga.clavis.core.widget.Relation
import io.github.mbaliga.clavis.core.widget.RelationKind
import io.github.mbaliga.clavis.core.widget.WIDGET_SNAPSHOT_VERSION
import io.github.mbaliga.clavis.core.widget.WidgetSnapshots
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The document the app publishes for its home-screen widgets (docs/widgets.md). */
class WidgetSnapshotTest {
    private val now = Fx.T0
    private val min = WidgetSnapshots.MINUTE_MS

    @Test fun lanesCountDueExactlyLikeDueCardsAndListWhenTheRestFallDue() {
        val e = Fx.engine()
        Fx.tested(e.state, Fx.ES, 5.0)
        val es = e.state.cardsOf(Fx.ES)
        es["gato"] = Fx.knownCard(due = now - 1)
        es["vuelo"] = Fx.knownCard(due = now + 90 * min)
        es["mundo"] = Fx.knownCard(due = now + 30 * min + 1) // rounds up to 31: never counted due early
        es["poción"] = Card(prov = true, due = now - 5) // provisional: not reviewed, as in dueCards
        es["de"] = Card(intro = true, due = now - 5) // introduced only: not reviewed either
        val s = WidgetSnapshots.build(e, now)
        assertEquals(listOf("es", "fr", "ja"), s.lanes.map { it.l }) // active lanes with scenes, registry order
        val lane = s.lanes.first()
        assertEquals("Spanish", lane.name); assertEquals(0xFFB3543BL, lane.colour)
        assertEquals(e.dueCards(Fx.ES, now).size, lane.dueNow); assertEquals(1, lane.dueNow)
        assertEquals(listOf(31, 90), lane.laterMin); assertFalse(lane.moreLater)
        assertEquals(e.knownCount(Fx.ES, now), lane.held)
        assertEquals(e.corpusCoverage(Fx.ES, now), lane.readable)
        assertTrue(lane.tested); assertFalse(s.lanes[1].tested)
        assertEquals(now, s.at); assertEquals(WIDGET_SNAPSHOT_VERSION, s.v)
    }

    @Test fun manyLaterCardsAreCappedAndSaySo() {
        val e = Fx.engine()
        val es = e.state.cardsOf(Fx.ES)
        repeat(WidgetSnapshots.MAX_LATER + 5) { es["w$it"] = Fx.knownCard(due = now + (it + 1) * min) }
        val lane = WidgetSnapshots.build(e, now).lanes.first()
        assertEquals(WidgetSnapshots.MAX_LATER, lane.laterMin.size)
        assertTrue(lane.moreLater)
    }

    @Test fun theWordFirstInLineIsTheEarliestDueAcrossLanesWithItsLineGapped() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1000)
        e.state.cardsOf(Fx.FR)["chat"] = Fx.knownCard(due = now - 5)
        val w = assertNotNull(WidgetSnapshots.build(e, now).word)
        assertEquals("es", w.l); assertEquals("gato", w.lemma); assertEquals(now - 1000, w.due)
        // gato appears in scenes 1 and 3 with the same coverage and unknown count: the first wins
        assertEquals("El ${Mask.GAP} come", w.prompt)
        assertFalse(w.rtl)
    }

    @Test fun whenNothingIsDueTheWordIsTheNextToFallDue() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now + 3 * DAY_MS)
        e.state.cardsOf(Fx.FR)["chat"] = Fx.knownCard(due = now + 2 * DAY_MS)
        val w = assertNotNull(WidgetSnapshots.build(e, now).word)
        assertEquals("fr", w.l); assertEquals("chat", w.lemma)
        assertEquals("Le ${Mask.GAP} mange", w.prompt)
    }

    @Test fun japaneseLinesLoseTheTranslatorsSpacesAroundTheGap() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.JA)["猫"] = Fx.knownCard(due = now - 1)
        assertEquals("${Mask.GAP} が食べる", WidgetSnapshots.build(e, now).word?.prompt)
    }

    @Test fun aLineThatWouldStillShowTheWordIsNotShown() {
        val twice = Fx.scene(
            0, "P0", mapOf("en" to "Cat, cat", "es" to "gato, gato"),
            mapOf("en" to listOf(Fx.tok("Cat", 5.0)), "es" to listOf(Fx.tok("gato", 4.2), Fx.tok("gato", 4.2))),
        )
        val corpus = Corpus.index(listOf(twice), Fx.miniLex(), emptyMap(), emptyMap(), emptyMap())
        val e = Fx.engine(corpus = corpus)
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1)
        val w = assertNotNull(WidgetSnapshots.build(e, now).word)
        assertNull(w.prompt) // "____, gato" would answer its own question
    }

    @Test fun aWordWithNoSceneHasNoLine() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["pato"] = Fx.knownCard(due = now - 1) // in the lexicon, in no scene
        assertNull(WidgetSnapshots.build(e, now).word?.prompt)
    }

    @Test fun theLastSessionNamesEachLanguageOnce() {
        val e = Fx.engine()
        e.state.lastSession = LastSession(now - 1000, 3, 5, 2, listOf("es", "fr", "es"))
        val last = assertNotNull(WidgetSnapshots.build(e, now).last)
        assertEquals(listOf("Spanish", "French"), last.languages)
        assertEquals(3, last.correct); assertEquals(5, last.n); assertEquals(2, last.newWords); assertEquals(now - 1000, last.t)
        assertNull(WidgetSnapshots.build(Fx.engine(), now).last)
    }

    @Test fun originWordsPreferWordsTheLearnerHasMet() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["vuelo"] = Fx.knownCard(reps = 1)
        e.state.cardsOf(Fx.ES)["gato"] = Card() // never rated, never provisional: not "met"
        e.state.cardsOf(Fx.ES)["hola"] = Fx.knownCard(reps = 2) // met, but the graph is silent: never shown (R-10)
        val s = WidgetSnapshots.build(e, now)
        assertTrue(s.wordsMet)
        val w = s.words.single()
        assertEquals("vuelo", w.surface); assertEquals("Latin volāre", w.origin); assertFalse(w.borrowed)
        assertEquals("Spanish", w.lang); assertEquals("es", w.l); assertEquals("vuelo", w.lemma)
        assertEquals(listOf(Relation("volare", "Italian", RelationKind.KIN)), w.relations)
    }

    @Test fun withNoMetWordTheCommonestWordsWithARecordedOriginStandIn() {
        val s = WidgetSnapshots.build(Fx.engine(), now)
        assertFalse(s.wordsMet)
        assertEquals(setOf("poción", "vuelo", "gato"), s.words.map { it.lemma }.toSet())
        val pocion = s.words.first { it.lemma == "poción" }
        assertEquals("Poción", pocion.surface); assertTrue(pocion.borrowed); assertEquals("Latin potione", pocion.origin)
    }

    @Test fun readingsFollowTheLearnersSetting() {
        val etym = mapOf(Fx.JA to mapOf("猫" to EtymEntry(origin = "Old Japanese neko", originType = "inh", source = "wiktionary")))
        val corpus = Corpus.index(Fx.miniScenes(), Fx.miniLex(), etym, emptyMap(), Fx.miniPages())
        val e = Fx.engine(corpus = corpus)
        e.state.cardsOf(Fx.JA)["猫"] = Fx.knownCard()
        assertEquals("neko", WidgetSnapshots.build(e, now).words.single().reading)
        e.state.settings.readings = false
        assertNull(WidgetSnapshots.build(e, now).words.single().reading)
        assertEquals("wiktionary", WidgetSnapshots.build(e, now).words.single().source)
    }

    @Test fun buildingReadsTheDocumentAndChangesNothing() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1)
        e.state.lastSession = LastSession(now, 1, 5, 1, listOf("es"))
        val json = Json { encodeDefaults = true }
        val before = json.encodeToString(ClavisState.serializer(), e.state)
        WidgetSnapshots.build(e, now)
        assertEquals(before, json.encodeToString(ClavisState.serializer(), e.state))
    }

    @Test fun encodingRoundTripsAndUnreadableFilesAreSaidToBe() {
        val e = Fx.engine()
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1)
        e.state.lastSession = LastSession(now, 4, 5, 2, listOf("es", "fr"))
        val s = WidgetSnapshots.build(e, now)
        val text = WidgetSnapshots.encode(s)
        assertEquals(s, WidgetSnapshots.decode(text))
        assertEquals(s, assertIs<Glance.Ready>(Glance.of(text)).snapshot)
        assertNull(WidgetSnapshots.decode("{"))
        assertNull(WidgetSnapshots.decode(text.replaceFirst("\"v\":$WIDGET_SNAPSHOT_VERSION", "\"v\":${WIDGET_SNAPSHOT_VERSION + 1}")))
        assertEquals(Glance.Missing, Glance.of(null))
        assertEquals(Glance.Unreadable, Glance.of("not json"))
        assertEquals(Glance.Unreadable, Glance.of(""))
    }

    @Test fun minutesRoundUp() {
        assertEquals(0, WidgetSnapshots.minutesUntil(now, now))
        assertEquals(0, WidgetSnapshots.minutesUntil(now, now - 5))
        assertEquals(1, WidgetSnapshots.minutesUntil(now, now + 1))
        assertEquals(1, WidgetSnapshots.minutesUntil(now, now + min))
        assertEquals(2, WidgetSnapshots.minutesUntil(now, now + min + 1))
    }
}
