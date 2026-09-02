package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.Card
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.core.PackKind
import io.github.mbaliga.clavis.ui.screens.languages.coverageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Languages catalogue (D-6, product-spec §4): family sheet, state, manifest-driven coverage, presets, resting. */
@OptIn(ExperimentalTestApi::class)
class LanguagesTest {

    @Test
    fun secondSpeakingSiblingShowsTheSheetAndOnlyExplicitConfirmationSetsProd() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "languages" }
        assertEquals(LaneMode.PROD, store.state.laneMode(Lang("es")))
        assertEquals(LaneMode.REC, store.state.laneMode(Lang("fr")))
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("view-languages").assertExists()
        onNodeWithTag("lang-fr-speak").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("family-sheet").assertExists()
        assertEquals(LaneMode.REC, store.state.laneMode(Lang("fr")), "no change before the sheet is answered")
        onNodeWithTag("sheet-speak").performClick()
        waitForIdle()
        assertEquals(LaneMode.PROD, store.state.laneMode(Lang("fr")))
        onNodeWithTag("family-sheet").assertDoesNotExist()
        onNodeWithTag("alongside-fr").performScrollTo().assertExists()
        // the other way: read instead keeps it receptive
        onNodeWithTag("lang-it-speak").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("family-sheet").assertExists()
        onNodeWithTag("sheet-read").performClick()
        waitForIdle()
        assertEquals(LaneMode.REC, store.state.laneMode(Lang("it")))
        onNodeWithTag("family-sheet").assertDoesNotExist()
    }

    @Test
    fun coverageLineComesFromTheManifestAndThinBadgeFollowsTheRule() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "languages" }
        setContent { ClavisApp(store, darkOverride = false) }
        val comics = store.manifests.first { it.kind == PackKind.COMICS }
        val ar = comics.stats.getValue("ar")
        val es = comics.stats.getValue("es")
        onNodeWithTag("lang-cov-ar").performScrollTo().assertTextContains("${ar["scenes"]} bubbles", substring = true)
        onNodeWithTag("lang-cov-ar").assertTextContains("${ar["episodes"]}/", substring = true)
        onNodeWithTag("lang-cov-es").performScrollTo().assertTextContains("${es["scenes"]} bubbles", substring = true)
        val covAr = coverageOf(store, Lang("ar"))
        val covEs = coverageOf(store, Lang("es"))
        assertEquals((ar["scenes"] ?: 0) < 200 || (ar["episodes"] ?: 0) < 6, covAr.thin)
        assertFalse(covEs.thin)
        if (covAr.thin) onNodeWithTag("thin-ar").assertExists()
        onNodeWithTag("thin-es").assertDoesNotExist()
        // the MICReLa hint for a read sibling of a speaking Spanish
        onNodeWithTag("hint-it").performScrollTo().assertExists()
    }

    @Test
    fun turningOffPausesButKeepsCardsAndPresetsApply() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded {
            it.state.view = "languages"
            it.state.cardsOf(Lang("fr"))["maison"] = Card(s = 3.0, d = 5.0, due = NOW, last = NOW, reps = 1)
        }
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("lang-fr-off").performScrollTo().performClick()
        waitForIdle()
        assertEquals(LaneMode.OFF, store.state.laneMode(Lang("fr")))
        assertTrue(store.state.cards["fr"]?.containsKey("maison") == true, "cards survive a rest")
        onNodeWithTag("resting-fr").assertExists()
        assertFalse(store.engine.activeLanes().contains(Lang("fr")))
        onNodeWithTag("preset-romance").performScrollTo().performClick()
        waitForIdle()
        assertEquals(LaneMode.PROD, store.state.laneMode(Lang("es")))
        assertEquals(LaneMode.REC, store.state.laneMode(Lang("fr")))
        assertEquals(LaneMode.REC, store.state.laneMode(Lang("it")))
        assertEquals(LaneMode.OFF, store.state.laneMode(Lang("de")))
        assertEquals(LaneMode.OFF, store.state.laneMode(Lang("ja")))
        onNodeWithTag("preset-nine").performScrollTo().performClick()
        waitForIdle()
        assertEquals(9, store.engine.activeLanes().size)
        assertEquals(LaneMode.PROD, store.state.laneMode(Lang("nl")))
    }
}
