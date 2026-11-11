package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.ui.screens.settings.KEYBOARD_MAP
import kotlin.test.Test
import kotlin.test.assertTrue

/** USABILITY U18: Settings lists the keys `handleKey` reacts to. */
@OptIn(ExperimentalTestApi::class)
class KeyboardHelpTest {

    @Test
    fun settingsListsEveryBindingTheHandlerHas() = runSkikoComposeUiTest(size = Size(1280f, 1600f)) {
        val store = TestBoot.onboarded { it.state.view = "set" }
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("keyboard-help").assertExists()
        // the keys handleKey reacts to, by group; a new binding in the handler needs a row here and in KEYBOARD_MAP
        val expected = listOf("1 2 3 4 5", "Ctrl/Cmd ,", "Esc", "Space / Enter", "1 2 3", "P", "E", "U / T", "Left / Right, N / Y")
        val listed = KEYBOARD_MAP.map { it.keys }
        for (k in expected) assertTrue(k in listed, "missing from KEYBOARD_MAP: $k")
        for (b in KEYBOARD_MAP) onNodeWithText(b.does).assertExists()
    }
}
