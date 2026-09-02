package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.Lang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** D-10: dock below 600 dp, rail above; modal states hide both; `S.view` persists and invalid → learn. */
@OptIn(ExperimentalTestApi::class)
class NavigationTest {

    @Test
    fun compactWidthShowsTheDockAndSwitchesViews() = runSkikoComposeUiTest(size = Size(412f, 915f)) {
        val store = TestBoot.onboarded()
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("dock").assertExists()
        onNodeWithTag("rail").assertDoesNotExist()
        onNodeWithTag("gear").assertExists()
        onNodeWithTag("nav-learn").assertIsSelected()
        onNodeWithTag("nav-you").performClick()
        waitForIdle()
        assertEquals("you", store.state.view)
        onNodeWithTag("you").assertExists()
        onNodeWithTag("nav-you").assertIsSelected()
        onNodeWithTag("gear").performClick()
        waitForIdle()
        assertEquals("set", store.state.view)
        onNodeWithTag("settings").assertExists()
        // Settings is not a dock item: Learn stays lit
        onNodeWithTag("nav-learn").assertIsSelected()
    }

    @Test
    fun expandedWidthShowsTheRail() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded()
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("rail").assertExists()
        onNodeWithTag("dock").assertDoesNotExist()
        onNodeWithTag("nav-paths").performClick()
        waitForIdle()
        assertEquals("paths", store.state.view)
        onNodeWithTag("view-paths").assertExists()
    }

    @Test
    fun modalStatesHideTheNavigation() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded()
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("rail").assertExists()
        store.startTest(Lang("es"))
        waitForIdle()
        onNodeWithTag("test").assertExists()
        onNodeWithTag("rail").assertDoesNotExist()
        onNodeWithTag("dock").assertDoesNotExist()
        onNodeWithTag("test-cancel").performClick()
        waitForIdle()
        assertNull(store.current.test)
        onNodeWithTag("rail").assertExists()
    }

    @Test
    fun citationOpensTheColophonOverTheSenderAndBackReturns() = runSkikoComposeUiTest(size = Size(412f, 915f)) {
        val store = TestBoot.onboarded()
        setContent { ClavisApp(store, darkOverride = false) }
        store.openColophon(3)
        waitForIdle()
        onNodeWithTag("colophon").assertExists()
        onNodeWithTag("dock").assertDoesNotExist()
        onNodeWithTag("ref-3").assertExists()
        onNodeWithTag("back-to-claim").performClick()
        waitForIdle()
        onNodeWithTag("learn").assertExists()
        assertEquals("learn", store.state.view)
    }

    @Test
    fun persistedViewIsRestoredAndInvalidViewFallsBackToLearn() {
        val dir = TestBoot.tempDir()
        val a = TestBoot.onboarded(dir = dir)
        a.setView("you")
        val b = TestBoot.store(dir = dir)
        assertEquals("you", b.state.view)
        b.state.view = "bogus"
        b.save()
        val c = TestBoot.store(dir = dir)
        assertEquals("learn", c.state.view)
    }
}
