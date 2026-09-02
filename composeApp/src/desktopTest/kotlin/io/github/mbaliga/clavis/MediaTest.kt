package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.ui.screens.media.MediaParse
import io.github.mbaliga.clavis.ui.screens.media.permissive
import io.github.mbaliga.clavis.ui.screens.media.rankSentences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Media (D-7 user-media, product-spec §5.5–§5.6) and Sentences (§5.4). */
@OptIn(ExperimentalTestApi::class)
class MediaTest {

    @Test
    fun srtAndLrcParse() {
        val srt = "1\n00:00:01,000 --> 00:00:03,500\nHola, <i>mundo</i>.\n¿Qué tal?\n\n2\n00:00:04,000 --> 00:00:05,000\nAdiós.\n\n"
        assertEquals(listOf("Hola, mundo. ¿Qué tal?", "Adiós."), MediaParse.parseSrt(srt))
        val lrc = "[ar:Someone]\n[ti:Song]\n[00:12.00]La casa azul\n[00:15.50][00:40.10]Y el perro\n\n[01:02.3]Fin"
        assertEquals(listOf("La casa azul", "Y el perro", "Fin"), MediaParse.parseLrc(lrc))
        assertEquals(listOf("uno", "dos"), MediaParse.parseTxt("uno\n\n  dos  \n"))
        assertEquals("srt", MediaParse.kindOf("film.SRT")); assertEquals("lrc", MediaParse.kindOf("x.lrc")); assertEquals("txt", MediaParse.kindOf("notes"))
        val pieces = MediaParse.tokeniseSpaced("¡La casa-grande, azul!")
        assertEquals(listOf("La", "casa-grande", "azul"), pieces.filter { it.word }.map { it.text })
        val cjk = MediaParse.tokeniseLongest("私は猫が好き", setOf("私", "猫", "好き"))
        assertEquals(listOf("私", "は", "猫", "が", "好き"), cjk.map { it.text })
        assertEquals(listOf(true, false, true, false, true), cjk.map { it.word })
    }

    @Test
    fun importOpensReadingModeMarksKnownnessAndLogsATingle() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "media" }
        val es = Lang("es")
        val lex = store.corpus.lex.getValue(es)
        val known = lex.keys.first { it.length > 3 }
        val id = store.addMedia("Canción", es, "lrc", listOf("La $known azul", "zzzqqq"))
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("view-media").assertExists()
        onNodeWithTag("media-item-$id").assertExists()
        onNodeWithTag("media-open-$id").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("media-reading").assertExists()
        onNodeWithTag("media-line-0").assertExists()
        // untested lane: the lexicon word is unknown, so it is lit as new
        val unk = allTaggedTexts("media-unk-0-")
        assertTrue(unk.any { it.second == known }, "lexicon word lit as unknown: $unk")
        assertTrue(allTaggedTexts("media-tok-1-").isNotEmpty(), "non-lexicon token stays dim")
        val tag = unk.first { it.second == known }.first
        onNodeWithTag(tag).performClick()
        waitForIdle()
        onNodeWithTag("media-hint").assertExists()
        onNodeWithTag("tingle-$known").performClick()
        waitForIdle()
        val entry = store.state.log.last()
        assertEquals("tingle", entry.type); assertEquals("es", entry.l); assertEquals(known, entry.lm)
        assertTrue(store.state.cards["es"].isNullOrEmpty(), "a tingle creates no card")
        onNodeWithTag("tingle-noted").assertExists()
        onNodeWithTag("media-back").performClick()
        waitForIdle()
        onNodeWithTag("media-delete-$id").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("confirm-yes").performClick()
        waitForIdle()
        assertTrue(store.state.media.isEmpty())
    }

    @Test
    fun sentencesRankByCoverageAndShowEnglishOnTap() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "media" }
        val fr = Lang("fr")
        val pack = runBlocking { store.sentences(fr) }
        assertTrue(pack.size > 100)
        val ranked = rankSentences(store, fr, pack)
        assertEquals(pack.size, ranked.size)
        // untested lane → nothing known → no sentence passes the gate; order is by coverage anyway
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.coverage >= b.coverage || (a.matched > 0 && a.coverage >= 0.9) })
        assertTrue(permissive("CC BY-SA 4.0")); assertTrue(permissive("CC0 1.0")); assertFalse(permissive("CC BY-NC 2.0")); assertFalse(permissive(null))
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("sentences-fr").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("view-sentences").assertExists()
        waitUntil(timeoutMillis = 30_000) { onAllNodesWithTagPrefix("sentence-row-0").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sentence-text-0").assertTextContains(ranked[0].s.text)
        onNodeWithTag("sentence-en-0").assertDoesNotExist()
        onNodeWithTag("sentence-text-0").performClick()
        waitForIdle()
        if (!ranked[0].s.en.isNullOrBlank()) onNodeWithTag("sentence-en-0").assertExists()
        // no download in the test bundle and no voice: no play control, no label — nothing is claimed
        onNodeWithTag("sentence-audio-0-play").assertDoesNotExist()
        onNodeWithTag("sentences-back").performClick()
        waitForIdle()
        onNodeWithTag("view-media").assertExists()
    }
}
