package io.github.mbaliga.clavis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Headless Compose UI test for the desktop target. `runComposeUiTest` renders into an offscreen
 * Skia surface, so no display server is needed (see docs/build-platform.md for the incantation).
 */
@OptIn(ExperimentalTestApi::class)
class AppTest {

    @Test
    fun showsWordmarkAndTagline() = runComposeUiTest {
        setContent { App() }

        onNodeWithText("Clavis").assertIsDisplayed()
        onNodeWithTag("wordmark").assertTextEquals("Clavis")
        // Read through generated compose resources: proves Res works on the test classpath.
        onNodeWithText("nine languages, one lexicon").assertIsDisplayed()
    }

    @Test
    fun tappingTheButtonIncrementsTheCounter() = runComposeUiTest {
        setContent { App() }

        onNodeWithTag("counter-value").assertTextEquals("0")
        onNodeWithTag("counter-button").performClick()
        onNodeWithTag("counter-value").assertTextEquals("1")
        onNodeWithTag("counter-button").performClick()
        onNodeWithTag("counter-value").assertTextEquals("2")
    }
}
