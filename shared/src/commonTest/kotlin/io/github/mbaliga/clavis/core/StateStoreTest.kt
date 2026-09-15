package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.state.FileIo
import io.github.mbaliga.clavis.core.state.JsonStateStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StateStoreTest {
    private val now = Fx.T0

    private fun dirs(): AppDirs {
        val d = "build/tmp/state-test/${Random.nextLong().toString(16)}"
        return object : AppDirs { override val data = d; override val cache = "$d/cache"; override val downloads = "$d/dl" }
    }

    private fun store(dirs: AppDirs = dirs()) = JsonStateStore(dirs, Fx.registry, Clock { now })

    /** A v0.10 web export, shaped exactly like `JSON.stringify(S)` (template-spec §1.2–§1.3). */
    private val webExport = """
        {"v":2,"created":1756700000000,
         "lanes":{"es":"prod","fr":"rec","it":"rec","de":"rec","nl":"prod","ja":"rec","ko":"rec","zh":"prod","ar":"off"},
         "cards":{"es":{"gato":{"s":3.173,"d":5.282434422319005,"reps":1,"first":1756700000000,"last":1756700000000,"prov":false,"intro":false,"g":3,"due":1756959200000},
                        "hola":{"prov":true,"s":0,"d":0,"due":1759292000000,"last":1756700000000,"reps":0,"lapses":0,"g":3},
                        "vuelo":{"intro":true,"s":0,"d":0,"due":1756700000000,"last":1756700000000,"reps":0,"lapses":0,"g":1}},
                  "fr":{},"it":{},"de":{},"nl":{},"ja":{},"ko":{},"zh":{},"ar":{}},
         "tests":{"es":{"done":1756700000000,"thr":5,"fa":0.125,"bands":[{"lo":6,"hi":9,"lab":"the ~500 commonest words","n":8,"hit":1},{"lo":5,"hi":6,"lab":"the top ~2,000","n":8,"hit":0.75},{"lo":4,"hi":5,"lab":"the top ~10,000","n":8,"hit":0.5},{"lo":3,"hi":4,"lab":"the top ~50,000","n":0,"hit":null},{"lo":2,"hi":3,"lab":"rare words","n":8,"hit":0}],"ms":123456}},
         "seen":{"12":1756700000000,"40":1756700500000},
         "bandit":{"es":{"w":0.31,"hist":[1,0,1,1,0.5]},"fr":{"w":0.2,"hist":[]},"it":{"w":0.2,"hist":[]},"de":{"w":0.2,"hist":[]},"nl":{"w":0.2,"hist":[]},"ja":{"w":0.2,"hist":[]},"ko":{"w":0.2,"hist":[]},"zh":{"w":0.2,"hist":[]},"ar":{"w":0.2,"hist":[]}},
         "log":[{"t":1756700000000,"l":"es","type":"test","thr":5,"fa":0.125},
                {"t":1756700001000,"l":"es","type":"read","sc":12,"unk":2,"ok":1},
                {"t":1756700002000,"l":"es","type":"cloze","sc":40,"lm":"gato","ok":0,"ms":3100,"sib":"fr"},
                {"t":1756700003000,"l":"es","type":"cloze","sc":40,"lm":"gato","ok":1,"ms":900,"sib":null},
                {"t":1756700004000,"l":"fr","type":"echo","lm":"chat","ok":1,"g":3},
                {"t":1756700005000,"l":"ja","type":"langid","ok":0,"guess":"zh"}],
         "inter":{"es":{"fr":1},"fr":{},"it":{},"de":{},"nl":{},"ja":{},"ko":{},"zh":{},"ar":{}},
         "routes":{"es":{"en":2},"fr":{},"it":{},"de":{},"nl":{},"ja":{},"ko":{},"zh":{},"ar":{}},
         "newDay":"2025-9-1","newToday":{"es":2,"fr":0,"it":0,"de":0,"nl":0,"ja":0,"ko":0,"zh":0,"ar":0},"pin":null,
         "settings":{"retention":0.85,"newPerDay":12,"sessionSize":6,"readings":false,"monoSession":true,"motion":true},
         "coach":{"read":true,"cloze":true},"ob":4,"onboarded":true,"view":"paths",
         "intent":{"l":"es","sc":77},"lastScene":{"es":12},"lastSession":{"t":1756700010000,"correct":4,"n":5,"newWords":3,"lanes":["es","fr","es","ja"]},
         "pathLog":[{"t":1756700009000,"kind":"new","l":"es","title":"Meet 2 new Spanish words"},{"t":1756700011000,"kind":"game","l":null,"title":"Which language is this?"}],
         "pathView":"map","story":{"lane":"fr","ep":3,"pi":2},"cosmos":{"mode":"top"},"refTarget":14,"letterTab":"ko","kanaSet":"kata"}
    """.trimIndent()

    @Test fun loadReturnsNullWhenNothingIsStoredAndLoadOrFreshGivesDefaults() = runTest {
        val s = store()
        assertNull(s.load())
        val fresh = s.loadOrFresh()
        assertEquals(now, fresh.created); assertEquals(9, fresh.lanes.size); assertEquals(3, fresh.v)
    }

    @Test fun saveThenLoadRoundTripsEveryField() = runTest {
        val d = dirs(); val s = store(d)
        val st = freshState(Fx.registry, now)
        st.cardsOf(Fx.ES)["gato"] = Fx.knownCard(); st.cardsOf(Fx.ES)["p"] = io.github.mbaliga.clavis.core.engine.Fsrs5.provisionalCard(now)
        st.tests["es"] = TestResult(now, 5.0, 0.125, listOf(Band(6.0, 9.0, "x", 8, 1.0), Band(3.0, 4.0, "y", 0, null)), 1234, pool = "unicode")
        st.log.add(LogEntry(now, "es", "cloze", sc = 3, lm = "gato", ok = 1, ms = 800, sib = null))
        st.settings.retention = 0.85; st.settings.voices["es"] = "piper:x"; st.coach["read"] = true
        st.intent = Intent("es", 2); st.pathLog.add(PathLogEntry(now, "new", "es", "t")); st.seen["3"] = now; st.pin = "ja"
        st.lastSession = LastSession(now, 3, 5, 1, listOf("es", "fr")); st.story = StoryState("ja", 2, 1); st.cosmos.exlens = "fisheye"
        st.media["m1"] = ImportedMedia("m1", "Song", "es", now, "lrc", listOf("a", "b"))
        s.save(st)
        assertTrue(FileIo.exists("${d.data}/clavis-state.json")); assertFalse(FileIo.exists("${d.data}/clavis-state.json.tmp"))
        val back = assertNotNull(store(d).load())
        assertEquals(st, back)
    }

    @Test fun exportUsesLowercaseLaneModesAndImportsBack() = runTest {
        val s = store()
        val st = s.loadOrFresh(); st.lanes["fr"] = LaneMode.OFF
        val json = s.exportJson()
        val obj = Json.parseToJsonElement(json).jsonObject
        assertEquals("prod", obj["lanes"]!!.jsonObject["es"]!!.jsonPrimitive.content); assertEquals("off", obj["lanes"]!!.jsonObject["fr"]!!.jsonPrimitive.content)
        assertEquals("3", obj["v"]!!.jsonPrimitive.content); assertTrue(obj.containsKey("cards")); assertTrue(obj.containsKey("settings"))
        val imported = store().importJson(json)
        assertEquals(LaneMode.OFF, imported.lanes["fr"]); assertEquals(LaneMode.PROD, imported.lanes["es"])
    }

    @Test fun mergeKeepsTheStoredVersionAndUnknownKeysAndFillsMissingLanes() = runTest { // D-12
        val s = store()
        val st = s.merge(Json.parseToJsonElement("""{"v":1,"cards":{"es":{}},"lanes":{"es":"rec"},"foo":{"bar":1},"settings":{"retention":0.8}}""").jsonObject)
        assertEquals(1, st.v)
        assertEquals(LaneMode.REC, st.lanes["es"]); assertEquals(LaneMode.REC, st.lanes["fr"]); assertEquals(LaneMode.PROD, st.lanes["ar"]); assertEquals(9, st.lanes.size)
        assertEquals(9, st.cards.size); assertEquals(9, st.bandit.size); assertEquals(0.2, st.bandit["ja"]!!.w); assertEquals(9, st.inter.size); assertEquals(9, st.routes.size); assertEquals(0, st.newToday["zh"])
        assertEquals(0.8, st.settings.retention); assertEquals(10, st.settings.newPerDay); assertEquals(5, st.settings.sessionSize) // one-level merge
        assertTrue(st.coach.isEmpty())
        assertEquals(setOf("foo"), s.extras.keys)
        val out = Json.parseToJsonElement(s.encode(st)).jsonObject
        assertEquals("1", out["foo"]!!.jsonObject["bar"]!!.jsonPrimitive.content) // unknown keys survive a save
    }

    @Test fun mergeReplacesTopLevelKeysWholesaleAndResetsCoach() = runTest {
        val s = store()
        val st = s.merge(Json.parseToJsonElement("""{"cards":{},"newToday":{"es":4},"coach":{"cloze":true}}""").jsonObject)
        assertEquals(4, st.newToday["es"]); assertEquals(0, st.newToday["fr"]) // shallow overlay, then the D-12 fill for the rest
        assertEquals(mapOf("cloze" to true), st.coach)
        assertEquals(now, st.created) // default kept for a key the document lacks
    }

    @Test fun importsAWebBuildExport() = runTest {
        val d = dirs(); val s = store(d)
        val st = s.importJson(webExport)
        assertEquals(2, st.v); assertEquals(1756700000000L, st.created)
        assertEquals(LaneMode.PROD, st.lanes["es"]); assertEquals(LaneMode.OFF, st.lanes["ar"])
        val gato = st.cards["es"]!!["gato"]!!
        assertEquals(3.173, gato.s); assertEquals(1, gato.reps); assertEquals(0, gato.lapses); assertEquals(1756959200000L, gato.due)
        assertTrue(st.cards["es"]!!["hola"]!!.prov); assertTrue(st.cards["es"]!!["vuelo"]!!.intro)
        val t = st.tests["es"]!!
        assertEquals(5.0, t.thr); assertEquals(0.125, t.fa); assertEquals(5, t.bands.size); assertNull(t.bands[3].hit); assertEquals(0.75, t.bands[1].hit); assertEquals(123456L, t.ms)
        assertNull(t.pool) // a web-build result carries no pool rule (D-19)
        assertEquals(1756700500000L, st.seen["40"]); assertEquals(listOf(1.0, 0.0, 1.0, 1.0, 0.5), st.bandit["es"]!!.hist); assertEquals(0.31, st.bandit["es"]!!.w)
        assertEquals(6, st.log.size); assertEquals("fr", st.log[2].sib); assertNull(st.log[3].sib); assertEquals(0, st.log[5].ok); assertEquals("zh", st.log[5].guess); assertEquals(5.0, st.log[0].thr)
        assertEquals(1, st.inter["es"]!!["fr"]); assertEquals(2, st.routes["es"]!!["en"]); assertEquals("2025-9-1", st.newDay); assertEquals(2, st.newToday["es"]); assertNull(st.pin)
        assertEquals(0.85, st.settings.retention); assertEquals(12, st.settings.newPerDay); assertEquals(6, st.settings.sessionSize); assertFalse(st.settings.readings); assertTrue(st.settings.monoSession)
        assertNull(st.settings.uiLocale); assertTrue(st.settings.voices.isEmpty()) // native-only settings get their defaults
        assertEquals(mapOf("read" to true, "cloze" to true), st.coach); assertEquals(4, st.ob); assertTrue(st.onboarded); assertEquals("paths", st.view)
        assertEquals(Intent("es", 77), st.intent); assertEquals(12, st.lastScene["es"]); assertEquals(LastSession(1756700010000L, 4, 5, 3, listOf("es", "fr", "es", "ja")), st.lastSession)
        assertEquals(2, st.pathLog.size); assertNull(st.pathLog[1].l); assertEquals("map", st.pathView); assertEquals(StoryState("fr", 3, 2), st.story)
        assertEquals(CosmosState(mode = "explore", exlens = "off", exsets = false, v3 = true, webFor = null), st.cosmos) // v3 migration: 'top' → 'explore'
        assertEquals(14, st.refTarget); assertEquals("ko", st.letterTab); assertEquals("kata", st.kanaSet)
        assertTrue(st === s.current); assertTrue(FileIo.exists("${d.data}/clavis-state.json"))
        assertEquals(st, store(d).load()) // the imported document was saved and reloads identically
    }

    @Test fun importRejectsDocumentsWithoutCardsOrInvalidJson() = runTest {
        val s = store()
        val e1 = assertFailsWith<IllegalArgumentException> { s.importJson("""{"v":2,"lanes":{}}""") }
        assertEquals(Copy.NOT_A_BACKUP, e1.message)
        assertFailsWith<IllegalArgumentException> { s.importJson("not json at all") }
        assertFailsWith<IllegalArgumentException> { s.importJson("[1,2,3]") }
        assertFailsWith<IllegalArgumentException> { s.importJson("""{"cards":null}""") }
        assertNull(s.current)
    }

    @Test fun cosmosMigrationOnlyWhenNotYetV3() = runTest {
        val s = store()
        val a = s.merge(Json.parseToJsonElement("""{"cards":{},"cosmos":{"mode":"depth"}}""").jsonObject)
        assertEquals(CosmosState("depth", "off", false, true, null), a.cosmos)
        val b = s.merge(Json.parseToJsonElement("""{"cards":{},"cosmos":{"mode":"explore","exlens":"relations","exsets":true,"v3":true}}""").jsonObject)
        assertEquals(CosmosState("explore", "relations", true, true, null), b.cosmos)
        val c = s.merge(Json.parseToJsonElement("""{"cards":{}}""").jsonObject)
        assertEquals(CosmosState(), c.cosmos)
    }

    @Test fun booleanOkInLogEntriesIsCoerced() = runTest {
        val s = store()
        val st = s.merge(Json.parseToJsonElement("""{"cards":{},"log":[{"t":1,"l":"es","type":"cloze","ok":true},{"t":2,"l":"es","type":"read","ok":false},{"t":3,"l":"es","type":"langid","ok":1}]}""").jsonObject)
        assertEquals(listOf(1, 0, 1), st.log.map { it.ok })
    }

    @Test fun corruptFileLoadsAsAFreshDocumentAndIsKeptBesideIt() = runTest {
        val d = dirs()
        val bad = "{{{ not json".encodeToByteArray()
        FileIo.mkdirs(d.data); FileIo.write("${d.data}/clavis-state.json", bad)
        val s = store(d)
        val st = assertNotNull(s.load())
        assertTrue(s.lastLoadWasCorrupt)
        assertEquals(3, st.v); assertEquals(9, st.lanes.size); assertTrue(st.cards["es"]!!.isEmpty())
        val copy = "${d.data}/corrupt/clavis-state.json.corrupt-$now"
        assertTrue(FileIo.exists(copy)); assertContentEquals(bad, FileIo.read(copy))
        assertFalse(FileIo.exists("${d.data}/clavis-state.json")) // moved aside, not overwritten
        s.save(st)
        assertContentEquals(bad, FileIo.read(copy)) // the copy survives the first save of the fresh document
        val again = store(d)
        assertEquals(st, again.load()); assertFalse(again.lastLoadWasCorrupt)
        // a second unreadable document at the same instant gets its own name
        FileIo.write("${d.data}/clavis-state.json", bad)
        store(d).load()
        assertTrue(FileIo.exists("$copy.2")); assertContentEquals(bad, FileIo.read(copy))
    }

    @Test fun aRejectedImportLeavesTheStoredDocumentAlone() = runTest {
        val d = dirs(); val s = store(d)
        val st = s.loadOrFresh(); st.settings.newPerDay = 4; s.save(st)
        assertFailsWith<IllegalArgumentException> { s.importJson("""{"cards":{},"lanes":{"es":"read"}}""") } // a lane mode the model does not know
        assertTrue(st === s.current); assertFalse(s.lastLoadWasCorrupt)
        assertEquals(4, store(d).load()!!.settings.newPerDay)
    }

    @Test fun resetDeletesTheDocument() = runTest {
        val d = dirs(); val s = store(d)
        s.save(freshState(Fx.registry, now))
        assertNotNull(store(d).load())
        s.reset()
        assertNull(s.current); assertNull(store(d).load()); assertFalse(FileIo.exists("${d.data}/clavis-state.json"))
    }

    @Test fun saveIsAtomicAndRepeatable() = runTest {
        val d = dirs(); val s = store(d)
        val st = s.loadOrFresh()
        s.save(st)
        st.settings.newPerDay = 3
        s.save(st)
        assertFalse(FileIo.exists("${d.data}/clavis-state.json.tmp"))
        assertEquals(3, store(d).load()!!.settings.newPerDay)
        val obj: JsonObject = Json.parseToJsonElement(FileIo.read("${d.data}/clavis-state.json")!!.decodeToString()).jsonObject
        assertEquals("3", obj["settings"]!!.jsonObject["newPerDay"]!!.jsonPrimitive.content)
    }

    @Test fun nullMapsInAStoredDocumentFallBackToDefaults() = runTest {
        val s = store()
        val st = s.merge(Json.parseToJsonElement("""{"cards":{},"tests":null,"seen":null,"pathLog":null,"settings":null,"story":null}""").jsonObject)
        assertTrue(st.tests.isEmpty()); assertTrue(st.seen.isEmpty()); assertTrue(st.pathLog.isEmpty()); assertEquals(Settings(), st.settings); assertEquals(StoryState(), st.story)
    }
}
