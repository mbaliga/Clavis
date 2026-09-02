package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.engine.ClavisEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathsTest {
    private val now = Fx.T0

    private fun multi(langs: List<String>): Corpus {
        fun toks(l: String, b: Boolean) = if (!b) listOf(Fx.tok("${l}one", 4.0), Fx.tok("${l}two", 7.0)) else listOf(Fx.tok("${l}one", 4.0), Fx.tok("${l}three", 3.0))
        val all = listOf("en") + langs
        val a = Fx.scene(0, "PA", all.associateWith { "${it}one ${it}two" }, all.associateWith { toks(it, false) })
        val b = Fx.scene(1, "PB", all.associateWith { "${it}one ${it}three" }, all.associateWith { toks(it, true) })
        val lex = langs.associate { l -> Lang(l) to linkedMapOf("${l}one" to Fx.lex(1, 4.0, "${l}one"), "${l}two" to Fx.lex(1, 7.0, "${l}two"), "${l}three" to Fx.lex(1, 3.0, "${l}three")) }
        return Corpus.index(listOf(a, b), lex, emptyMap(), emptyMap(), emptyMap())
    }

    @Test fun allUntestedGivesTwoChecksThenLettersReadAndGame() {
        val e = Fx.engine()
        val b = e.computeBranches(now)
        assertEquals(listOf(BranchKind.TEST, BranchKind.TEST, BranchKind.LETTERS, BranchKind.READ, BranchKind.GAME), b.map { it.kind })
        assertEquals(listOf(Fx.ES, Fx.FR, Fx.JA, Fx.ES, null), b.map { it.l }) // es first: prod score 0.42 + 0.35
        assertEquals(0.77, b[0].score, 1e-12); assertEquals(0.63, b[1].score, 1e-12)
        assertEquals("Take the Spanish check", b[0].title); assertEquals(Copy.TEST_WHY, b[0].why); assertEquals("Clavis stops treating Spanish as brand new", b[0].then)
        assertEquals("Learn the Japanese script first", b[2].title); assertEquals(Copy.LETTERS_WHY, b[2].why); assertEquals(Copy.LETTERS_THEN, b[2].then)
        assertEquals("Read Spanish freely", b[3].title); assertEquals(listOf(15, 17), b[3].cites); assertEquals(0.0, b[3].score)
        assertEquals("Which language is this?", b[4].title); assertEquals(listOf(33), b[4].cites); assertEquals(Copy.GAME_THEN, b[4].then)
    }

    @Test fun dueBranchOutranksChecksAndCarriesWords() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1)
        val b = e.computeBranches(now)
        val due = b[0]
        assertEquals(BranchKind.DUE, due.kind); assertEquals(Fx.ES, due.l); assertEquals(1, due.n)
        assertEquals(0.85, due.score, 1e-12) // (0.2 + 0.08 + 0.6·0.2)·1.5 + 0.25
        assertEquals("Bring back 1 Spanish word", due.title); assertEquals("1 word ready to return", due.why); assertEquals(Copy.DUE_THEN, due.then)
        assertEquals(listOf(WordChip("gato", "")), due.words)
        assertEquals(listOf(BranchKind.TEST, BranchKind.TEST), b.subList(1, 3).map { it.kind })
        assertEquals(BranchKind.READ, b[4].kind); assertEquals(Fx.ES, b[4].l) // the tested lane reads freely
    }

    @Test fun newBranchCarriesTheSceneWordsAndUnlockPromise() {
        val e = Fx.engine(random = ScriptedRandom(default = 0.0)); Fx.tested(e.state, Fx.ES, 5.0)
        val b = e.computeBranches(now).first { it.kind == BranchKind.NEW }
        assertEquals(Fx.ES, b.l); assertEquals(1, b.sc!!.i); assertEquals(0.42, b.score, 1e-12)
        assertEquals("Meet 1 new Spanish word", b.title); assertEquals(listOf(WordChip("gato", "")), b.words)
        assertEquals("1 more bubble becomes readable", b.then) // sc1's unknown set {gato} unlocks sc1 itself
        assertEquals("getting to know you", b.why)
    }

    @Test fun newBranchUsesJapaneseReadingsAndPluralPromise() {
        val e = Fx.engine(random = ScriptedRandom(default = 0.0)); Fx.tested(e.state, Fx.JA, 5.0)
        e.state.lanes["es"] = LaneMode.OFF; e.state.lanes["fr"] = LaneMode.OFF
        val b = e.computeBranches(now).first { it.kind == BranchKind.NEW }
        assertEquals(Fx.JA, b.l)
        assertTrue(b.words.all { it.r.isNotEmpty() })
        assertTrue(b.then.endsWith("readable") || b.then == "the next bubbles get easier")
    }

    @Test fun unlockCountIncludesTheCandidateItself() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        assertEquals(1, e.unlockCount(Fx.ES, setOf("gato"), now)) // sc1
        assertEquals(2, e.unlockCount(Fx.ES, setOf("gato", "dormir"), now)) // sc1, sc3
        assertEquals(0, e.unlockCount(Fx.ES, setOf("poción"), now)) // sc2 also needs vuelo
        assertEquals(1, e.unlockCount(Fx.ES, setOf("poción", "vuelo"), now))
        assertEquals(0, e.unlockCount(Fx.ES, emptySet(), now)) // fully known scenes never count
    }

    @Test fun varietyRuleAtMostTwoPerKindAndFourLaneBranches() {
        val c = multi(listOf("es", "fr", "it", "de", "nl", "ja"))
        val e = Fx.engine(corpus = c)
        for (l in listOf("es", "fr", "it", "de")) Fx.tested(e.state, Lang(l), 5.0)
        for (l in listOf("es", "fr", "de")) e.state.cardsOf(Lang(l))["${l}one"] = Fx.knownCard(due = now - 1)
        val b = e.computeBranches(now)
        // scores: es DUE .85, nl TEST .77, fr DUE .65, de DUE .65 (third due → dropped), ja TEST .63, it NEW .28 (fifth lane → cut at 4)
        assertEquals(listOf(BranchKind.DUE, BranchKind.TEST, BranchKind.DUE, BranchKind.TEST, BranchKind.LETTERS), b.map { it.kind })
        assertEquals(listOf(Fx.ES, Fx.NL, Fx.FR, Fx.JA, Fx.JA), b.map { it.l })
        assertEquals(5, b.size) // max fan reached: no read floor, no game
        e.state.coach["letters:ja"] = true
        val b2 = e.computeBranches(now)
        assertEquals(listOf(BranchKind.DUE, BranchKind.TEST, BranchKind.DUE, BranchKind.TEST, BranchKind.READ), b2.map { it.kind })
        assertEquals(Fx.ES, b2[4].l) // first active lane with a test
    }

    @Test fun readFloorPrefersATestedLaneAndGameNeedsThreeLanes() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.FR, 5.0)
        val b = e.computeBranches(now)
        assertEquals(Fx.FR, b.first { it.kind == BranchKind.READ }.l)
        assertTrue(b.any { it.kind == BranchKind.GAME })
        e.state.lanes["ja"] = LaneMode.OFF
        val b2 = e.computeBranches(now)
        assertFalse(b2.any { it.kind == BranchKind.GAME }); assertFalse(b2.any { it.kind == BranchKind.LETTERS })
        assertEquals(listOf(BranchKind.TEST, BranchKind.NEW, BranchKind.READ), b2.map { it.kind })
    }

    @Test fun takeTestBranchLogsAndReturnsStartTest() {
        val e = Fx.engine()
        val b = e.computeBranches(now)[0]
        assertEquals(BranchAction.StartTest(Fx.ES), e.takeBranch(b, now))
        assertEquals(listOf(PathLogEntry(now, "test", "es", "Take the Spanish check")), e.state.pathLog)
        assertNull(e.state.intent); assertFalse(e.canUndo())
    }

    @Test fun takeReadBranchOpensStoryOnThatLane() {
        val e = Fx.engine()
        e.state.story = StoryState(lane = "fr", ep = 2, pi = 1)
        val b = e.computeBranches(now).first { it.kind == BranchKind.READ }
        assertEquals(BranchAction.OpenStory(Fx.ES), e.takeBranch(b, now))
        assertEquals(StoryState(lane = "es", ep = 2, pi = 1), e.state.story); assertEquals("story", e.state.view)
    }

    @Test fun takeGameBranchClearsIntent() {
        val e = Fx.engine()
        e.state.intent = Intent("es", 1)
        val b = e.computeBranches(now).first { it.kind == BranchKind.GAME }
        assertEquals(BranchAction.StartGameSession, e.takeBranch(b, now)); assertNull(e.state.intent)
        assertEquals("game", e.state.pathLog.last().kind); assertNull(e.state.pathLog.last().l)
    }

    @Test fun takeNewAndDueBranchesSetTheIntent() {
        val e = Fx.engine(random = ScriptedRandom(default = 0.0)); Fx.tested(e.state, Fx.ES, 5.0)
        val nb = e.computeBranches(now).first { it.kind == BranchKind.NEW }
        val a = assertIs<BranchAction.StartSession>(e.takeBranch(nb, now))
        assertEquals(Intent("es", 1), a.intent); assertEquals(Intent("es", 1), e.state.intent); assertTrue(e.canUndo())
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1)
        val db = e.computeBranches(now).first { it.kind == BranchKind.DUE }
        assertEquals(BranchAction.StartSession(Intent("es", null)), e.takeBranch(db, now))
        assertEquals(Intent("es", null), e.state.intent)
    }

    @Test fun takeLettersBranchMarksTheCoachFlagAndOpensLetters() { // D-14
        val e = Fx.engine()
        val lb = e.computeBranches(now).first { it.kind == BranchKind.LETTERS }
        assertEquals(BranchAction.OpenLetters(Fx.JA), e.takeBranch(lb, now))
        assertEquals(true, e.state.coach["letters:ja"]); assertEquals("ja", e.state.letterTab)
        assertFalse(e.computeBranches(now).any { it.kind == BranchKind.LETTERS })
        assertEquals("letters", e.state.pathLog.single().kind)
    }

    @Test fun pathLogKeepsTheLastTwelve() {
        val e = Fx.engine()
        val b = e.computeBranches(now)[0]
        repeat(13) { e.takeBranch(b, now + it) }
        assertEquals(12, e.state.pathLog.size); assertEquals(now + 1, e.state.pathLog.first().t); assertEquals(now + 12, e.state.pathLog.last().t)
    }

    @Test fun undoClearsTheIntentAndPopsTheLog() {
        val e = Fx.engine(random = ScriptedRandom(default = 0.0)); Fx.tested(e.state, Fx.ES, 5.0)
        e.takeBranch(e.computeBranches(now)[0], now)
        e.takeBranch(e.computeBranches(now).first { it.kind == BranchKind.NEW }, now + 1)
        assertTrue(e.canUndo()); assertEquals(2, e.state.pathLog.size)
        e.undo()
        assertNull(e.state.intent); assertEquals(1, e.state.pathLog.size); assertFalse(e.canUndo())
        e.undo(); e.undo() // idempotent on an empty log
        assertTrue(e.state.pathLog.isEmpty())
    }

    @Test fun undoStaysReachableUntilTheFirstEncounterIsConsumed() { // D-14
        val e = Fx.engine(random = ScriptedRandom(default = 0.0)); Fx.tested(e.state, Fx.ES, 5.0)
        val nb = e.computeBranches(now).first { it.kind == BranchKind.NEW }
        e.takeBranch(nb, now)
        val s = e.startSession(now)
        assertTrue(e.canUndo()); assertEquals(nb.sc!!.i, (s.cur as Encounter.Read).sc.i)
        e.advance(s, now)
        assertFalse(e.canUndo()); assertNull(e.state.intent)
    }

    @Test fun laneOverviewMetaLine() {
        val e = Fx.engine(); Fx.tested(e.state, Fx.ES, 5.0)
        e.state.cardsOf(Fx.ES)["gato"] = Fx.knownCard(due = now - 1)
        e.state.newToday["es"] = 3; e.state.pin = "es"
        e.state.inter["es"] = mutableMapOf("fr" to 2, "it" to 0); e.state.routes["es"] = mutableMapOf("en" to 1)
        val o = e.laneOverview(Fx.ES, now)
        assertEquals("still watching you · 1 ready · 1 held · 40% readable · new today 3/10 · assumes the top ~2,000", o.meta)
        assertTrue(o.pinned); assertEquals(LaneMode.PROD, o.mode); assertEquals(listOf("French" to 2), o.lookAlikes); assertEquals(listOf("English" to 1), o.bridges)
        assertEquals("look-alikes that fooled you: French ×2", Copy.lookAlikes(o.lookAlikes))
        val fr = e.laneOverview(Fx.FR, now)
        assertEquals("still watching you · 0 ready · 0 held · 0% readable · new today 0/10 · assumes nothing (untested)", fr.meta)
        e.state.banditOf(Fx.FR).hist.addAll(listOf(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0))
        assertEquals("improving fast", e.laneOverview(Fx.FR, now).lpLabel)
        e.state.banditOf(Fx.FR).hist.clear(); e.state.banditOf(Fx.FR).hist.addAll(List(8) { 0.5 })
        assertEquals("steady", e.laneOverview(Fx.FR, now).lpLabel)
    }

    @Test fun storyEpisodesAndPages() {
        val e = Fx.engine()
        assertEquals(listOf(1, 2), e.storyEpisodes(Fx.ES)) // no episode has ≥ 6 scenes → all episodes
        assertEquals(listOf("P0", "P1"), e.storyPages(Fx.ES, 1)); assertEquals(listOf("P2"), e.storyPages(Fx.ES, 2))
        assertEquals(listOf("P0", "P1"), e.storyPages(Fx.JA, 1)) // sc2 has no Japanese but sc1 (same page) does
        assertEquals(listOf(1), e.pageScenes("P1", Fx.JA).map { it.i })
        assertTrue(e.storyEpisodes(Fx.KO).isEmpty())
        val many = Corpus.index((0 until 7).map { i -> Fx.scene(i, "Q$i", mapOf("en" to "x", "es" to "Hola"), mapOf("es" to listOf(Fx.hola)), e = if (i < 6) 1 else 2) }, emptyMap(), emptyMap(), emptyMap(), emptyMap())
        assertEquals(listOf(1), Fx.engine(corpus = many).storyEpisodes(Fx.ES)) // episode 2 has only one scene
    }

    @Test fun branchKindLabelsAndButtons() {
        assertEquals("first, a check", Copy.KIND_LABEL[BranchKind.TEST]); assertEquals("bring back", Copy.KIND_LABEL[BranchKind.DUE])
        assertEquals("Take the check", Copy.branchButton(BranchKind.TEST)); assertEquals("Open the comic", Copy.branchButton(BranchKind.READ))
        assertEquals("Play a round", Copy.branchButton(BranchKind.GAME)); assertEquals("Walk this way", Copy.branchButton(BranchKind.NEW)); assertEquals("Walk this way", Copy.branchButton(BranchKind.DUE))
        assertNotNull(Copy.MAP_KIND_COLOUR[BranchKind.LETTERS]); assertEquals("#86C197", Copy.MAP_KIND_COLOUR[BranchKind.NEW]); assertEquals("which language?", Copy.MAP_SUB[BranchKind.GAME])
    }

    @Test fun engineExposesLexiconSurfaceAndReading() {
        val e: ClavisEngine = Fx.engine()
        assertEquals("Hola", e.surfaceOf(Fx.ES, "hola")); assertEquals("nope", e.surfaceOf(Fx.ES, "nope"))
        assertEquals("neko", e.readingOf(Fx.JA, "猫")); assertEquals("", e.readingOf(Fx.ES, "hola")); assertEquals("English", e.langName(Fx.EN)); assertEquals("xx", e.langName(Lang("xx")))
    }
}
