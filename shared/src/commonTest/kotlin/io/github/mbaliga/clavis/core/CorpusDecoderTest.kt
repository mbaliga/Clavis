package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.data.CorpusDecoder
import io.github.mbaliga.clavis.core.data.DefaultContentPackLoader
import io.github.mbaliga.clavis.core.data.LanguageRegistryLoader
import io.github.mbaliga.clavis.core.data.PackManifests
import io.github.mbaliga.clavis.core.data.gunzip
import io.github.mbaliga.clavis.core.data.maybeGunzip
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class CorpusDecoderTest {
    private val gzBytes: ByteArray get() = Base64.decode(RealFixture.SCENES_GZ_B64)
    private val P00 = "ep01/gfx_Pepper-and-Carrot_by-David-Revoy_E01P00.png"
    private val P01 = "ep01/gfx_Pepper-and-Carrot_by-David-Revoy_E01P01.png"

    @Test fun decodesRealScenesWithRectsLinesAndTokens() {
        val scenes = CorpusDecoder.decodeScenes(RealFixture.SCENES_JSON)
        assertEquals(listOf(0, 1, 2), scenes.map { it.i })
        val s0 = scenes[0]
        assertEquals(1, s0.e); assertEquals("E01P00", s0.p); assertEquals(P00, s0.m)
        assertEquals(Rect(0.0794, 0.7908, 0.8621, 0.1307), s0.r)
        assertEquals(Rect(0.042, 0.789, 0.914, 0.139), s0.q[Fx.ES]); assertEquals(Rect(0.0, 0.758, 1.0, 0.242), s0.q[Fx.AR])
        assertEquals("Episode 1 : Potion of flight", s0.x[Fx.EN]); assertEquals("Episodio 1 : Poción de vuelo", s0.x[Fx.ES])
        assertEquals(9, s0.x.size); assertEquals(s0.x.keys, s0.k.keys)
        val es = s0.k[Fx.ES]!!
        assertEquals(4, es.size)
        assertEquals(Token("Episodio", "episodio", 4.4, listOf(Bridge(Fx.EN, "Episode", 0.75), Bridge(Fx.FR, "Episode", 0.75)), null), es[0])
        assertEquals("poción", es[1].lemma); assertEquals(2.89, es[1].zipf)
    }

    @Test fun tokenTupleSlotsLemmaZeroBridgesZeroAndOptionalReading() {
        val scenes = CorpusDecoder.decodeScenes(RealFixture.SCENES_JSON)
        val ja = scenes[0].k[Fx.JA]!!
        assertEquals(Token("エピソード", "エピソード", 4.32, emptyList(), "episoodo"), ja[0])
        val hikou = ja.first { it.surface == "飛行" }
        assertEquals("hikoo", hikou.reading); assertEquals(listOf(Bridge(Fx.ZH, "行飛", 1.0)), hikou.bridges)
        val en = scenes[0].k[Fx.EN]!!
        assertEquals(Token("Episode", "episode", 4.97, emptyList(), null), en[0])
        val envol = scenes.flatMap { it.k[Fx.FR].orEmpty() }.first { it.surface == "d'envol" }
        assertEquals("envol", envol.lemma) // explicit lemma in slot 1
        val letzte = scenes.flatMap { it.k[Fx.DE].orEmpty() }.first { it.surface == "letzte" }
        assertEquals("letzter", letzte.lemma)
    }

    @Test fun gunzipRoundTripsThePackedScenes() {
        val plain = gunzip(gzBytes).decodeToString()
        assertEquals(RealFixture.SCENES_JSON, plain)
        assertContentEquals(gunzip(gzBytes), maybeGunzip(gzBytes))
        val raw = RealFixture.SCENES_JSON.encodeToByteArray()
        assertContentEquals(raw, maybeGunzip(raw)) // no magic → untouched
        assertEquals(CorpusDecoder.decodeScenes(RealFixture.SCENES_JSON), CorpusDecoder.decodeScenes(plain))
    }

    @Test fun acceptsTheWrappedRootAndRejectsNonContiguousIds() {
        val wrapped = """{"generated":"x","scenes":${RealFixture.SCENES_JSON}}"""
        assertEquals(3, CorpusDecoder.decodeScenes(wrapped).size)
        assertFailsWith<IllegalStateException> { CorpusDecoder.decodeScenes("""[{"i":1,"e":1,"p":"P","m":"m","r":[0,0,0.5,0.1],"x":{"en":"a"},"k":{"en":[["a",0,1.0,0]]}}]""") }
        assertFailsWith<IllegalStateException> { CorpusDecoder.decodeScenes("42") }
    }

    @Test fun tokenTupleSerialisesBackToThePackShape() {
        val t = Token("Poción", "poción", 2.89, listOf(Bridge(Fx.EN, "Potion", 0.83)), null)
        assertEquals(t, CorpusDecoder.tokenFromTuple(CorpusDecoder.tokenToTuple(t)))
        val ja = Token("猫", "猫", 4.4, emptyList(), "neko")
        val arr = CorpusDecoder.tokenToTuple(ja)
        assertEquals(5, arr.size); assertEquals("0", arr[1].toString()); assertEquals("0", arr[3].toString()); assertEquals("\"neko\"", arr[4].toString())
        val explicit = Token("come", "comer", 5.0)
        assertEquals("\"comer\"", CorpusDecoder.tokenToTuple(explicit)[1].toString())
        assertEquals(explicit, CorpusDecoder.tokenFromTuple(Json.parseToJsonElement("""["come","comer",5.0,0,""]""").jsonArray)) // empty reading → null
    }

    @Test fun decodesTheLexiconPreservingOrder() {
        val lex = CorpusDecoder.decodeLexicon(RealFixture.LEXICON_JSON)
        assertEquals(listOf("de", "el", "episodio"), lex[Fx.ES]!!.keys.take(3))
        assertEquals(LexEntry(268, 7.81, "de", null), lex[Fx.ES]!!["de"]) // "" reading → null
        assertEquals("episoodo", lex[Fx.JA]!!["エピソード"]!!.reading)
        val obj = CorpusDecoder.decodeLexicon("""{"es":{"casa":{"count":3,"zipf":5.5,"surface":"casa","reading":""},"sol":[2,5.1,"sol"]}}""")
        assertEquals(LexEntry(3, 5.5, "casa", null), obj[Fx.ES]!!["casa"]); assertEquals(LexEntry(2, 5.1, "sol", null), obj[Fx.ES]!!["sol"])
    }

    @Test fun decodesEtymologyEdgesAndOrigins() {
        val et = CorpusDecoder.decodeEtym(RealFixture.ETYM_JSON)
        val no = et[Fx.ES]!!["no"]!!
        assertEquals("Old Spanish non", no.origin); assertEquals("inh", no.originType)
        assertEquals(listOf(EtymEdge(Fx.FR, "ne", EdgeType.COGNATE), EtymEdge(Fx.FR, "non", EdgeType.COGNATE), EtymEdge(Fx.IT, "no", EdgeType.COGNATE), EtymEdge(Fx.IT, "non", EdgeType.COGNATE)), no.edges)
        assertEquals(EtymEntry(origin = "Latin potione", originType = "bor"), et[Fx.ES]!!["poción"])
        val extra = CorpusDecoder.decodeEtym("""{"es":{"x":{"o":"Latin x","ot":"bor","e":[["fr","y","bor"],["it","z","der"],["de","w","weird"],{"lang":"nl","lemma":"v","type":"cog"}],"src":"wiktionary+etymdb","ipa":"ˈeks"}}}""")[Fx.ES]!!["x"]!!
        assertEquals(listOf(EdgeType.BORROWING, EdgeType.DERIVATION, EdgeType.COGNATE, EdgeType.COGNATE), extra.edges.map { it.type })
        assertEquals("wiktionary+etymdb", extra.source); assertEquals("ˈeks", extra.ipa); assertEquals(Fx.NL, extra.edges[3].lang)
    }

    @Test fun decodesPseudowordsAndPages() {
        val ps = CorpusDecoder.decodePseudo(RealFixture.PSEUDO_JSON)
        assertEquals(10, ps[Fx.ES]!!.size); assertEquals("apltor", ps[Fx.ES]!![0]); assertEquals(10, ps[Fx.FR]!!.size)
        val pg = CorpusDecoder.decodePages(RealFixture.PAGES_JSON)
        assertEquals(PageInfo(640, 905, "gfx_Pepper-and-Carrot_by-David-Revoy_E01P00.webp"), pg[P00])
        val obj = CorpusDecoder.decodePages("""{"k":{"width":640,"height":900,"file":"k.webp"},"j":[640,800]}""")
        assertEquals(PageInfo(640, 900, "k.webp"), obj["k"]); assertEquals(PageInfo(640, 800, "j"), obj["j"])
    }

    @Test fun assemblesACorpusFromSeparateFilesAndFromTheLegacyBlob() {
        val c = CorpusDecoder.decode(RealFixture.SCENES_JSON, RealFixture.LEXICON_JSON, RealFixture.ETYM_JSON, RealFixture.PSEUDO_JSON, RealFixture.PAGES_JSON)
        assertEquals(3, c.scenes.size); assertEquals(listOf(0, 1, 2), c.byLang[Fx.ES]); assertEquals(listOf(0, 1, 2), c.byLang[Fx.EN])
        assertEquals(listOf(1, 2), c.byLang[Fx.DE]) // scene 0 has no German
        assertEquals(listOf(1, 2), c.byPage[P01]) // rows round(0.0669·12)=1 then round(0.3845·12)=5
        assertTrue(c.byLemma[Fx.ES]!!["de"]!!.contains(0)); assertEquals(c.lex[Fx.ES]!!.keys.toList(), c.lexArr[Fx.ES]!!.map { it.lemma })
        assertEquals("", c.lexArr[Fx.ES]!!.first().reading); assertEquals(2, c.pages.size); assertEquals(10, c.pseudo[Fx.ES]!!.size)
        val blob = """{"scenes":${RealFixture.SCENES_JSON},"lex":${RealFixture.LEXICON_JSON},"pseudo":${RealFixture.PSEUDO_JSON},"etym":${RealFixture.ETYM_JSON}}"""
        val b = CorpusDecoder.decodePackedBlob(blob)
        assertEquals(c.scenes, b.scenes); assertEquals(c.lex, b.lex); assertEquals(c.etym, b.etym); assertTrue(b.pages.isEmpty())
        val minimal = CorpusDecoder.decodePackedBlob("""{"scenes":[]}""")
        assertTrue(minimal.scenes.isEmpty() && minimal.lex.isEmpty())
    }

    @Test fun duplicateLemmasInASceneAppearTwiceInByLemma() {
        val sc = Fx.scene(0, "P", mapOf("en" to "x", "es" to "el y el"), mapOf("es" to listOf(Fx.tok("el", 7.5), Fx.tok("y", 7.0), Fx.tok("el", 7.5))))
        val c = Corpus.index(listOf(sc), emptyMap(), emptyMap(), emptyMap(), emptyMap())
        assertEquals(listOf(0, 0), c.byLemma[Fx.ES]!!["el"])
    }

    @Test fun languageRegistryLoaderReadsTheNewFormat() {
        val json = """[
          {"iso":"es","name":"Spanish","nativeName":"Español","family":"romance","script":"latin","rtl":false,"defaultLane":"prod","siblings":["fr","it"],"readingSystem":null,"ttsLocale":"es-ES","colour":"0xFFB3543B","spaceSeparated":true,"enabledByDefault":true,"needsScriptPrimer":false,"pcFolder":"es","tokenizer":"simplemma"},
          {"iso":"ja","name":"Japanese","nativeName":"日本語","family":"cjk","script":"cjk","defaultLane":"rec","siblings":["zh","ko"],"readingSystem":"rōmaji","ttsLocale":"ja-JP","colour":"0xFFB84A6E","spaceSeparated":false,"enabledByDefault":true,"needsScriptPrimer":true},
          {"iso":"hi","name":"Hindi","family":"indic","script":"devanagari","defaultLane":"rec","ttsLocale":"hi-IN","colour":"#8B8F9C","needsScriptPrimer":true},
          {"iso":"ko","name":"Korean","family":"cjk","script":"hangul","defaultLane":"rec","ttsLocale":"ko-KR","colour":"#2E8F8A","needsScriptPrimer":true,"hasScriptPrimer":false}
        ]"""
        val reg = LanguageRegistryLoader.parse(json)
        assertEquals(listOf("es", "ja", "hi", "ko"), reg.order.map { it.code })
        val es = reg.require(Fx.ES)
        assertEquals(LanguageInfo(Fx.ES, "Spanish", "Español", Family.ROMANCE, Script.LATIN, false, LaneMode.PROD, listOf(Fx.FR, Fx.IT), null, "es-ES", 0xFFB3543BL, true, true, false, false), es)
        val ja = reg.require(Fx.JA)
        assertEquals(Script.CJK, ja.script); assertFalse(ja.spaceSeparated); assertTrue(ja.needsScriptPrimer); assertEquals("rōmaji", ja.readingSystem); assertEquals("日本語", ja.nativeName)
        assertTrue(ja.hasScriptPrimer) // D-21: absent in the document → the shipped primers (ja/ko/zh/ar) default to true
        val hi = reg.require(Lang("hi"))
        assertEquals(Family.INDIC, hi.family); assertEquals(Script.DEVANAGARI, hi.script); assertFalse(hi.enabledByDefault); assertEquals(0xFF8B8F9CL, hi.colour); assertEquals("Hindi", hi.nativeName); assertTrue(hi.siblings.isEmpty())
        assertTrue(hi.needsScriptPrimer); assertFalse(hi.hasScriptPrimer) // needs one, none ships
        assertFalse(reg.require(Fx.KO).hasScriptPrimer) // an explicit value wins over the default
        assertNull(reg[Lang("xx")])
    }

    @Test fun languageRegistryLoaderAcceptsTheTemplateTableAndWrapper() {
        val json = """{"languages":[{"iso":"ar","n":"Arabic","c":"semitic","sib":[],"t":"rtl","col":"#3E7DA6","mode":"prod","dir":"rtl","reading":"romanised"},
                                     {"iso":"ko","n":"Korean","c":"cjk","sib":["ja","zh"],"t":"cjk","col":"#2E8F8A","mode":"rec"},
                                     {"iso":"qq","name":"Quux","family":"martian","script":"runes"}]}"""
        val reg = LanguageRegistryLoader.parse(json)
        val ar = reg.require(Fx.AR)
        assertEquals(Script.ARABIC, ar.script); assertTrue(ar.rtl); assertEquals(0xFF3E7DA6L, ar.colour); assertEquals(LaneMode.PROD, ar.defaultLane); assertEquals("romanised", ar.readingSystem); assertEquals(Family.SEMITIC, ar.family)
        val ko = reg.require(Fx.KO)
        assertEquals(Script.CJK, ko.script); assertEquals(listOf(Fx.JA, Fx.ZH), ko.siblings); assertTrue(ko.spaceSeparated)
        val qq = reg.require(Lang("qq"))
        assertEquals(Family.OTHER, qq.family); assertEquals(Script.OTHER, qq.script); assertEquals(LaneMode.REC, qq.defaultLane); assertEquals(0xFF8B8F9CL, qq.colour)
        assertEquals(0xAABBCCDDL, LanguageRegistryLoader.parseColour(Json.parseToJsonElement("\"#AABBCCDD\""))); assertEquals(255L, LanguageRegistryLoader.parseColour(Json.parseToJsonElement("255")))
        assertEquals(0xFF112233L, LanguageRegistryLoader.parseColour(Json.parseToJsonElement("\"0xff112233\"")))
    }

    @Test fun packManifestsParse() {
        val json = """[{"id":"comics-pepper","kind":"comics","version":"2026.09.01","title":"Pepper&Carrot","languages":["es","fr"],"licence":"CC BY 4.0","source":"https://framagit.org/peppercarrot/webcomics","generatedAt":"2026-09-01T00:00:00Z",
                       "attribution":[{"name":"David Revoy","role":"author","url":"https://peppercarrot.com","licence":"CC BY 4.0"}],"files":{"scenes.json.gz":12345},"stats":{"es":{"scenes":640}},"extra":1}]"""
        val m = PackManifests.parse(json)
        assertEquals(1, m.size); assertEquals(PackKind.COMICS, m[0].kind); assertEquals("David Revoy", m[0].attribution[0].name); assertEquals(640, m[0].stats["es"]!!["scenes"]); assertEquals(12345L, m[0].files["scenes.json.gz"])
        assertEquals(1, PackManifests.parse("""{"packs":$json}""".replace("[{", "[{").let { """{"packs":$json}""" }).size)
        assertTrue(PackManifests.parse("   ").isEmpty())
    }

    @Test fun defaultContentPackLoaderReadsThroughTheCallback() = runTest {
        val files: Map<String, ByteArray> = mapOf(
            "packs/comics/scenes.json.gz" to gzBytes,
            "packs/lexicon/lexicon.json.gz" to RealFixture.LEXICON_JSON.encodeToByteArray(), // uncompressed under a .gz name: magic detection
            "packs/lexicon/pseudo.json" to RealFixture.PSEUDO_JSON.encodeToByteArray(),
            "packs/comics/pages.json" to RealFixture.PAGES_JSON.encodeToByteArray(),
            "packs/comics/gfx_Pepper-and-Carrot_by-David-Revoy_E01P00.webp" to byteArrayOf(1, 2, 3),
            "packs/languages.json" to """[{"iso":"es","name":"Spanish","family":"romance","script":"latin","defaultLane":"prod","ttsLocale":"es-ES","colour":"0xFFB3543B","enabledByDefault":true}]""".encodeToByteArray(),
            "packs/sentences/es.json.gz" to """[{"id":1,"lang":"es","text":"Hola.","en":"Hello.","lemmas":["hola"]}]""".encodeToByteArray(),
            "packs/pronunciations/es.json.gz" to """{"hola":{"ipa":"ˈola"},"gato":{"lemma":"gato","ipa":"ˈɡato","audioUrl":"https://x/y.ogg","audioLicence":"CC BY-SA 4.0","audioBy":"someone"}}""".encodeToByteArray(),
        )
        val reads = mutableListOf<String>()
        val loader = DefaultContentPackLoader({ p -> reads.add(p); files[p] ?: throw IllegalStateException("missing $p") })
        val corpus = loader.loadCorpus()
        assertEquals(3, corpus.scenes.size); assertEquals(13, corpus.lex[Fx.ES]!!.size); assertTrue(corpus.etym.isEmpty()); assertEquals(10, corpus.pseudo[Fx.ES]!!.size); assertEquals(2, corpus.pages.size)
        assertContentEquals(byteArrayOf(1, 2, 3), loader.pageBytes(P00))
        assertTrue(loader.manifests().isEmpty()) // manifest.json missing → no packs
        assertEquals("Spanish", loader.languages().require(Fx.ES).name)
        val sent = loader.sentences(Fx.ES)
        assertEquals(1, sent.size); assertEquals("Hola.", sent[0].text); assertEquals(listOf("hola"), sent[0].lemmas)
        assertTrue(loader.sentences(Fx.FR).isEmpty())
        val pr = loader.pronunciations(Fx.ES)
        assertEquals(Pronunciation("hola", "ˈola"), pr["hola"]); assertEquals("someone", pr["gato"]!!.audioBy)
        assertTrue(loader.pronunciations(Fx.JA).isEmpty())
        assertTrue(reads.contains("packs/etymology/etym.json.gz"))
    }
}
