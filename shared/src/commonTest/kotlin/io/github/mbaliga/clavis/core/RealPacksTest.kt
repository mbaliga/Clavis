package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.data.DefaultContentPackLoader
import io.github.mbaliga.clavis.core.engine.ClavisEngine
import io.github.mbaliga.clavis.core.state.FileIo
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration check against the packs the pipeline actually emits (`pipeline/build/packs`).
 * Skips silently when the directory is absent (CI without a pipeline run).
 */
class RealPacksTest {
    private val roots = listOf("/home/user/Clavis/pipeline/build", "../pipeline/build", "pipeline/build")

    private fun root(): String? = roots.firstOrNull { FileIo.exists("$it/packs/comics/scenes.json.gz") && FileIo.exists("$it/packs/lexicon/lexicon.json.gz") }

    private fun loader(root: String) = DefaultContentPackLoader({ path -> FileIo.read("$root/$path") ?: throw IllegalStateException("missing $path") })

    @Test fun realComicsLexiconAndEtymologyPacksLoadWithConsistentIndices() = runTest {
        val root = root() ?: return@runTest
        val corpus = loader(root).loadCorpus()
        assertTrue(corpus.scenes.size > 1000, "scenes: ${corpus.scenes.size}")
        corpus.scenes.forEachIndexed { idx, sc -> assertEquals(idx, sc.i); assertTrue(sc.x.containsKey(Lang.EN), "scene $idx lacks en"); assertEquals(sc.x.keys, sc.k.keys) }
        for ((l, ids) in corpus.byLang) {
            assertEquals(ids, ids.sorted()); assertEquals(ids.size, ids.toSet().size)
            for (id in ids) assertTrue(corpus.scene(id).k[l]!!.isNotEmpty())
        }
        for ((l, m) in corpus.byLemma) for ((lm, ids) in m) for (id in ids) assertTrue(corpus.scene(id).k[l]!!.any { it.lemma == lm }, "$l/$lm not in scene $id")
        assertEquals(corpus.scenes.size, corpus.byPage.values.sumOf { it.size })
        for ((m, ids) in corpus.byPage) { for (id in ids) assertEquals(m, corpus.scene(id).m); assertTrue(corpus.pages.containsKey(m), "no page record for $m") }
        assertTrue(corpus.lex.size >= 17, "lexicon languages: ${corpus.lex.size}")
        assertTrue(corpus.lexArr[Lang("es")]!!.size > 1000)
        assertTrue(corpus.etym.isNotEmpty())
        val withEdges = corpus.etym[Lang("es")]!!.values.first { it.edges.isNotEmpty() }
        assertTrue(withEdges.edges.all { it.lemma.isNotEmpty() && it.lang.code.length == 2 }) // array-triple edges decoded
        assertTrue(corpus.etym[Lang("es")]!!.values.any { it.ipa != null && it.source != null })
        assertTrue(corpus.pseudo[Lang("es")]!!.size >= 8)
        val bridged = corpus.scenes.asSequence().flatMap { it.k[Lang("es")].orEmpty().asSequence() }.first { it.bridges.isNotEmpty() }
        assertTrue(bridged.bridges.all { it.similarity > 0.0 })
        val ja = corpus.scenes.asSequence().flatMap { it.k[Lang("ja")].orEmpty().asSequence() }.first { it.reading != null }
        assertTrue(ja.reading!!.isNotEmpty())
    }

    @Test fun realRegistryManifestSentencesAndPronunciations() = runTest {
        val root = root() ?: return@runTest
        val loader = loader(root)
        val reg = loader.languages()
        assertTrue(reg.all.size >= 17, "registry: ${reg.all.size}")
        for (iso in listOf("es", "fr", "it", "de", "nl", "ja", "ko", "zh", "ar")) {
            val info = assertNotNull(reg[Lang(iso)], iso)
            assertTrue(info.enabledByDefault, "$iso should be enabled by default"); assertTrue(info.name.isNotBlank()); assertTrue(info.ttsLocale.isNotBlank())
        }
        assertTrue(reg.require(Lang("ja")).needsScriptPrimer || reg.require(Lang("ar")).needsScriptPrimer)
        assertTrue(reg.all.count { it.enabledByDefault } >= 9)
        val manifests = loader.manifests()
        assertTrue(manifests.isNotEmpty()); assertTrue(manifests.any { it.kind == PackKind.COMICS }); assertTrue(manifests.all { it.attribution.isNotEmpty() || it.licence.isNotBlank() })
        val sentences = loader.sentences(Lang("es"))
        assertTrue(sentences.isNotEmpty()); assertTrue(sentences.all { it.text.isNotBlank() && it.lang == "es" }) // extra keys such as audioFile are ignored
        val pron = loader.pronunciations(Lang("es"))
        assertTrue(pron.isNotEmpty()); assertTrue(pron.values.any { it.ipa != null })
        val page = corpusPage(loader)
        assertTrue(page.isNotEmpty())
    }

    private suspend fun corpusPage(loader: DefaultContentPackLoader): ByteArray {
        val root = root()!!
        val pages = io.github.mbaliga.clavis.core.data.CorpusDecoder.decodePages(FileIo.read("$root/packs/comics/pages.json")!!.decodeToString())
        val key = pages.keys.first()
        return loader.pageBytes(key)
    }

    @Test fun engineRunsASessionOnTheRealCorpus() = runTest {
        val root = root() ?: return@runTest
        val loader = loader(root)
        val corpus = loader.loadCorpus()
        val reg = loader.languages()
        val state = freshState(reg, Fx.T0)
        val e = ClavisEngine(corpus, reg, state, Clock { Fx.T0 }, Random(7), DayKey { "2025-9-2" })
        assertTrue(e.activeLanes().size >= 9)
        val branches = e.computeBranches(Fx.T0)
        assertTrue(branches.size in 3..5); assertTrue(branches.count { it.kind == BranchKind.TEST } == 2)
        val t = e.start(Lang("es"), Fx.T0, Random(1))
        assertTrue(t.items.count { it.real } == 40 && t.items.count { !it.real } == 8)
        for (it in t.items) e.answer(it.let { t }, it.real)
        val (res, _) = e.finish(t, Fx.T0 + 1000)
        assertEquals(2.0, res.thr)
        val s = e.startSession(Fx.T0)
        var guard = 0
        while (!s.done && guard++ < 20) {
            when (val c = s.cur) {
                is Encounter.Read -> e.finishRead(s, c, true, c.st.unk.map { it.lemma }.toSet(), Fx.T0)
                is Encounter.Cloze -> e.answerCloze(s, c, e.clozeOptions(c, e.random).first { it.right }, 3000, Fx.T0)
                is Encounter.Echo -> e.answerEcho(s, c, 3, Fx.T0)
                is Encounter.LangId -> e.answerLangId(s, c, c.l, Fx.T0)
                null -> error("no encounter")
            }
            e.advance(s, Fx.T0)
        }
        assertTrue(s.done); assertTrue(e.state.log.size >= 6)
        assertTrue(e.corpusCoverage(Lang("es"), Fx.T0) > 0.0)
    }
}
