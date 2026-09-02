package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.ui.screens.letters.LetterData
import kotlin.test.Test
import kotlin.test.assertEquals

/** Letters (template-spec §7.7; D-21 visited marks). */
@OptIn(ExperimentalTestApi::class)
class LettersTest {

    @Test
    fun tabsTilesAndKanaToggle() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "letters" }
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("view-letters").assertExists()
        for (c in LetterData.PRIMER_TABS) onNodeWithTag("letters-tab-$c").assertExists()
        assertEquals("ja", store.state.letterTab)
        assertEquals(true, store.state.coach["letters:ja"], "opening a tab marks the script visited (D-21)")
        assertEquals(LetterData.HIRA.count { it != null }, allTaggedTexts("tile-").size)
        onNodeWithTag("tile-あ").assertExists()
        onNodeWithTag("kana-Katakana").performClick()
        waitForIdle()
        assertEquals("kata", store.state.kanaSet)
        onNodeWithTag("tile-ア").assertExists()
        onNodeWithTag("letters-tab-ko").performClick()
        waitForIdle()
        assertEquals("ko", store.state.letterTab)
        assertEquals(true, store.state.coach["letters:ko"])
        assertEquals(LetterData.JAMO_C.size + LetterData.JAMO_V.size, allTaggedTexts("tile-").size)
        onNodeWithTag("letters-tab-zh").performClick()
        waitForIdle()
        assertEquals(LetterData.TONES.size, allTaggedTexts("tile-").size)
        onNodeWithTag("letters-tab-ar").performClick()
        waitForIdle()
        assertEquals(LetterData.ABJAD.size, allTaggedTexts("tile-").size)
        onNodeWithTag("tile-ا").assertExists()
    }

    @Test
    fun tileWithoutAVoiceToastsHonestly() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "letters" }
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("tile-か").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("toast").assertExists()
        onNodeWithText(Copy.noVoiceLetters("Japanese")).assertExists()
    }

    @Test
    fun scriptWithoutAPrimerShowsAnHonestCard() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded {
            it.state.view = "letters"
            it.state.lanes["ru"] = LaneMode.REC
            it.state.letterTab = "ru"
        }
        assertEquals(LaneMode.REC, store.state.laneMode(Lang("ru")))
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("letters-tab-ru").assertExists()
        onNodeWithTag("letters-noprimer").assertTextContains("Russian", substring = true)
        assertEquals(true, store.state.coach["letters:ru"])
    }
}
