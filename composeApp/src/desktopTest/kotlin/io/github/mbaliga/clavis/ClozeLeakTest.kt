package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Mask
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.Token
import io.github.mbaliga.clavis.ui.components.PageImages
import io.github.mbaliga.clavis.ui.components.SceneBox
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cloze mask contract (template-spec §5.1; brief G): before answering, the answer word
 * appears in NEITHER the cropped nor the whole-page lettering; after answering, both fill in.
 */
@OptIn(ExperimentalTestApi::class)
class ClozeLeakTest {

    private fun leaks(texts: List<Pair<String, String>>, surface: String): List<Pair<String, String>> =
        texts.filter { (_, t) -> t.contains(surface, ignoreCase = true) }

    @Test
    fun maskIsSharedByCroppedAndWholePageAndFillsAfterAnswering() = runSkikoComposeUiTest(size = Size(412f, 915f)) {
        lateinit var seed: TestBoot.Seed
        val store = TestBoot.onboarded(seed = 1) { s -> seed = TestBoot.seedDueCloze(s) }
        store.startSession()
        val e = store.current.enc?.enc
        assertTrue(e is Encounter.Cloze, "expected a cloze encounter, got $e")
        assertEquals(seed.scene.i, e.sc.i)
        assertEquals(seed.token.lemma, e.lemma)
        val surface = seed.token.surface
        val mask = Mask.forCloze(e.sc, e.l, e.lemma, false)
        assertTrue(!mask.isNullOrEmpty(), "mask should be a gapped string for this seed, was '$mask'")

        setContent { ClavisApp(store, darkOverride = false) }
        waitUntil(timeoutMillis = 30_000) { store.pageImages.peek(e.sc.m) != null }
        waitForIdle()

        // cropped: the current balloon is lettered with the gap, never the answer
        onNodeWithTag("scene-cropped").assertExists()
        val cropped = allTaggedTexts("balloon-${e.sc.i}")
        assertTrue(cropped.isNotEmpty(), "cropped balloon should be lettered (credible rect)")
        assertTrue(cropped.all { it.second.contains(Mask.GAP) }, "cropped balloon should show the gap: $cropped")
        assertEquals(emptyList(), leaks(cropped, surface), "answer leaked in the cropped lettering")

        // whole page: every balloon of the page in Spanish; the current one gapped
        onNodeWithTag("toggle-page").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("scene-full").assertExists()
        val full = allTaggedTexts("balloon-")
        assertTrue(full.any { it.first == "balloon-${e.sc.i}" }, "current balloon should be lettered on the page: $full")
        assertEquals(emptyList(), leaks(full.filter { it.first == "balloon-${e.sc.i}" }, surface), "answer leaked in the whole-page lettering")

        // answer → both renderers fill in together
        onNodeWithTag("cloze-option-0").performScrollTo().performClick()
        waitForIdle()
        val fullAfter = allTaggedTexts("balloon-${e.sc.i}")
        assertTrue(fullAfter.isNotEmpty() && fullAfter.all { it.second.contains(surface) }, "whole page should show the answer after answering: $fullAfter")
        onNodeWithTag("toggle-page").performScrollTo().performClick()
        waitForIdle()
        val croppedAfter = allTaggedTexts("balloon-${e.sc.i}")
        assertTrue(croppedAfter.isNotEmpty() && croppedAfter.all { it.second.contains(surface) }, "cropped should show the answer after answering: $croppedAfter")
    }

    @Test
    fun emptyMaskLettersNothingForTheCurrentBalloon() = runSkikoComposeUiTest(size = Size(412f, 915f)) {
        val store = TestBoot.onboarded(seed = 2)
        val seed = TestBoot.clozeSeed(store)
        val es = Lang("es")
        val siblings = store.pageOf(seed.scene, es)
        setContent {
            ClavisTheme(darkTheme = false, reducedMotion = true) {
                androidx.compose.foundation.layout.Column {
                    SceneBox(store.corpus, store.pageImages, seed.scene, es, full = false, mask = "")
                    SceneBox(store.corpus, store.pageImages, seed.scene, es, full = true, mask = "", siblings = siblings)
                }
            }
        }
        waitForIdle()
        assertTrue(allTaggedTexts("balloon-${seed.scene.i}").isEmpty(), "'' must letter nothing for the current balloon")
        assertTrue(allTaggedTexts("marker-${seed.scene.i}").isNotEmpty(), "a marker should stand in for the un-lettered balloon")
        // other balloons on the page are still lettered
        val others = allTaggedTexts("balloon-").filter { it.first != "balloon-${seed.scene.i}" }
        assertEquals(siblings.count { it.i != seed.scene.i && io.github.mbaliga.clavis.core.Lettering.rectOK(io.github.mbaliga.clavis.core.Lettering.rectFor(it, es)) }, others.size)
    }

    @Suppress("unused")
    private fun keep(s: Scene, t: Token, p: PageImages) = Unit
}
