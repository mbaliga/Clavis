package io.github.mbaliga.clavis

import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Mask
import io.github.mbaliga.clavis.core.widget.WidgetSize
import io.github.mbaliga.clavis.core.widget.WidgetSnapshots
import io.github.mbaliga.clavis.core.widget.WidgetTap
import io.github.mbaliga.clavis.store.SessionOrigin
import kotlinx.coroutines.runBlocking
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The app side of the home-screen widgets on the real packs: the snapshot the store publishes,
 * and where a widget tap lands (docs/widgets.md). The Android binding itself is device-only.
 */
class WidgetStoreTest {

    @Test
    fun theSnapshotFromTheRealPacksIsSmallHonestAndRoundTrips() {
        val store = TestBoot.onboarded()
        val seed = TestBoot.seedDueCloze(store)
        val s = WidgetSnapshots.build(store.engine, NOW)
        assertEquals(store.engine.activeLanes().map { it.code }, s.lanes.map { it.l })
        val es = s.lanes.first { it.l == "es" }
        assertEquals(store.engine.dueCards(Lang("es"), NOW).size, es.dueNow)
        val w = assertNotNull(s.word)
        assertEquals("es", w.l); assertEquals(seed.token.lemma, w.lemma)
        val prompt = assertNotNull(w.prompt, "the seed's line can be gapped")
        assertTrue(Mask.GAP in prompt)
        assertFalse(prompt.contains(seed.token.surface, ignoreCase = true), prompt)
        for (o in s.words) {
            assertEquals(store.corpus.etym[Lang(o.l)]?.get(o.lemma)?.origin, o.origin) // never invented (R-10)
            assertTrue(o.origin.isNotBlank())
        }
        assertTrue(s.words.isNotEmpty())
        val text = WidgetSnapshots.encode(s)
        assertTrue(text.length < 200_000, "snapshot is ${text.length} chars")
        assertEquals(s, WidgetSnapshots.decode(text))
    }

    @Test
    fun recallItOpensASessionHeldInTheWordsLane() {
        val store = TestBoot.onboarded()
        val seed = TestBoot.seedDueCloze(store)
        store.state.pin = "fr" // the pin would pick French; the widget's lane wins
        store.openFromWidget(WidgetTap.Session("es"))
        val enc = assertIs<Encounter.Cloze>(store.current.enc?.enc)
        assertEquals(Lang("es"), enc.l); assertEquals(seed.token.lemma, enc.lemma); assertEquals(Copy.HOME_SCREEN, enc.why)
        assertEquals(SessionOrigin.WIDGET, store.current.sessionOrigin)
        assertNull(store.state.intent)
    }

    @Test
    fun aLiveSessionOrACheckIsNeverInterrupted() {
        val store = TestBoot.onboarded()
        store.startSession()
        val session = assertNotNull(store.current.session)
        store.openFromWidget(WidgetTap.View("you"))
        store.openFromWidget(WidgetTap.Session("fr"))
        assertSame(session, store.current.session)
        assertEquals("learn", store.state.view)
        store.stopSession()
        store.startTest(Lang("fr"))
        store.openFromWidget(WidgetTap.View("paths"))
        assertNotNull(store.current.test); assertEquals("learn", store.state.view)
    }

    @Test
    fun beforeOnboardingATapOnlyBringsTheAppForward() {
        val store = TestBoot.store()
        store.openFromWidget(WidgetTap.View("you"))
        store.openFromWidget(WidgetTap.Session(null))
        assertNull(store.current.session)
        assertFalse(store.state.onboarded); assertEquals("learn", store.state.view)
    }

    @Test
    fun aTapThatArrivesWhileThePacksLoadLandsOnceTheyHave() {
        val dir = TestBoot.tempDir()
        TestBoot.onboarded(dir = dir) // writes an onboarded document into dir
        val cold = TestBoot.store(dir = dir, boot = false)
        cold.openFromWidget(WidgetTap.View("you"))
        runBlocking { cold.boot(TestBoot.loaded) }
        assertEquals("you", cold.state.view)
    }

    @Test
    fun aWordTapOpensCosmosOnThatWord() {
        val store = TestBoot.onboarded()
        val lemma = store.corpus.lex[Lang("es")]!!.keys.first()
        store.openFromWidget(WidgetTap.Word("es", lemma))
        assertEquals("cosmos", store.state.view); assertEquals("es|$lemma", store.state.cosmos.webFor); assertEquals("depth", store.state.cosmos.mode)
        store.setCosmosWebFor(null)
        store.openFromWidget(WidgetTap.Word("es", "no-such-lemma-here"))
        assertEquals("cosmos", store.state.view); assertNull(store.state.cosmos.webFor)
    }

    @Test
    fun aFinishedSummaryGivesWayToTheDestination() {
        val store = TestBoot.onboarded()
        store.startSession()
        TestBoot.finishSession(store)
        store.openFromWidget(WidgetTap.View("paths"))
        assertNull(store.current.session); assertEquals("paths", store.state.view)
    }

    private fun layout(name: String): Element =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/androidMain/res/layout/$name.xml")).documentElement

    private fun Element.dp(attr: String): Float =
        assertNotNull(getAttribute("android:$attr").removeSuffix("dp").toFloatOrNull(), "$tagName android:$attr")

    /** D-29: from the least height of the size class that draws it, every pad is at least 48 dp tall. */
    @Test
    fun padsAreAtLeast48dpFromTheirSizeClass() {
        for ((name, rows, size) in listOf(
            Triple("widget_pads_small", 2, WidgetSize.SMALL),
            Triple("widget_pads_medium", 2, WidgetSize.MEDIUM),
            Triple("widget_pads_large", 3, WidgetSize.LARGE),
        )) {
            val root = layout(name)
            val pads = root.getElementsByTagName("RelativeLayout").let { nl -> (0 until nl.length).map { nl.item(it) as Element } }
            val cols = root.getAttribute("android:columnCount").toIntOrNull() ?: 1
            assertEquals(rows, (pads.size + cols - 1) / cols, name)
            val each = (size.minHeightDp - 2 * root.dp("padding")) / rows - 2 * pads.first().dp("layout_margin")
            assertTrue(each >= 48f, "$name: $each dp per pad at ${size.minHeightDp} dp")
        }
    }

    /**
     * When a widget is short, the parts that may go (extra rows, detail lines) give way; the "as of"
     * footer and the buttons never do, because neither they nor any view around them is weighted.
     */
    @Test
    fun theAsOfFooterAndTheButtonsNeverGiveWay() {
        fun Element.find(id: String): Element? {
            if (getAttribute("android:id") == "@+id/$id") return this
            val kids = childNodes
            return (0 until kids.length).mapNotNull { (kids.item(it) as? Element)?.find(id) }.firstOrNull()
        }
        for ((name, ids) in listOf(
            "widget_list_medium" to listOf("widget_footer"),
            "widget_list_large" to listOf("widget_footer"),
            "widget_card_large" to listOf("widget_footer", "widget_primary", "widget_secondary"),
            "widget_card_medium" to listOf("widget_primary"),
            "widget_tile_tall" to listOf("widget_caption"),
        )) {
            val root = layout(name)
            for (id in ids) {
                var v: Element? = assertNotNull(root.find(id), "$name: $id")
                while (v != null && v !== root) {
                    assertEquals("", v.getAttribute("android:layout_weight"), "$name: $id sits in a weighted ${v.tagName}")
                    v = v.parentNode as? Element
                }
            }
        }
    }

    /** CONTRIBUTING "Copy rules (D-18)" over the widget names, descriptions and preview samples. */
    @Test
    fun theWidgetStringResourcesKeepTheCopyRules() {
        val xml = File("src/androidMain/res/values/strings.xml").readText()
        val strings = Regex("<string name=\"(widget_[a-z0-9_]+)\">([^<]*)</string>").findAll(xml).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue(strings.size >= 13, "found ${strings.size} widget strings")
        val forbidden = listOf(
            "subconscious", "subliminal", "absorb", "passive", "in your sleep", "without noticing", "effortless", "fluent",
            "master", "% of the language", "level", "streak", "proven", "scientifically", "league",
        )
        for ((name, text) in strings) {
            forbidden.firstOrNull { it in text.lowercase() }?.let { fail("'$it' in $name: $text") }
            if (Regex("\\bXP\\b|\\b[ABC][12]\\b").containsMatchIn(text)) fail("level label in $name: $text")
        }
    }
}
