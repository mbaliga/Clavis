package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.ui.screens.onboarding.OB_STEPS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fresh state → onboarding through all five steps with defaults → Learn → tap the pool → a whole
 * session runs to the summary against the REAL packs, with a seeded Random.
 */
@OptIn(ExperimentalTestApi::class)
class BootFlowTest {

    @Test
    fun freshStateWalksOnboardingLearnAndAFullSession() = runSkikoComposeUiTest(size = Size(412f, 915f)) {
        val store = TestBoot.store(seed = 3)
        setContent { ClavisApp(store, darkOverride = false) }

        onNodeWithTag("onboarding").assertExists()
        for (i in 0 until 4) {
            onNodeWithText(OB_STEPS[i].heading).assertIsDisplayed()
            assertEquals(i, store.state.ob)
            onNodeWithTag("ob-next").performClick()
            waitForIdle()
        }
        onNodeWithText("Your nine.").assertExists()
        assertEquals(4, store.state.ob)
        onNodeWithTag("ob-begin").performScrollTo().performClick()
        waitForIdle()
        assertTrue(store.state.onboarded)

        onNodeWithTag("learn").assertExists()
        onNodeWithTag("dock").assertExists()
        onNodeWithText(Copy.POOL_STILL).assertExists()
        onNodeWithTag("pool").performClick()
        waitForIdle()
        assertNotNull(store.current.session)
        onNodeWithTag("session").assertExists()
        onNodeWithTag("dock").assertDoesNotExist()

        var guard = 0
        while (store.current.session?.done != true && guard++ < 40) stepSession(store)
        assertTrue(store.current.session?.done == true, "session did not finish in $guard steps")
        onNodeWithTag("summary-h2").assertExists()
        onNodeWithText(Copy.SUMMARY_H2).assertExists()
        assertEquals(store.state.settings.sessionSize, store.engine.summary()?.n)
        assertTrue(store.state.log.isNotEmpty())

        onNodeWithTag("done").performClick()
        waitForIdle()
        onNodeWithTag("learn").assertExists()
        onNodeWithTag("dock").assertExists()
    }
}
