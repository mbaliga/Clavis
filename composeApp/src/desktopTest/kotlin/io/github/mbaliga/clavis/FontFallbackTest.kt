package io.github.mbaliga.clavis

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.text.font.Font
import io.github.mbaliga.clavis.platform.scriptFallbackFonts
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-9 on desktop: the six Noto fallbacks are bundled and every family renders the five scripts without throwing. */
@OptIn(ExperimentalTestApi::class)
class FontFallbackTest {
    private val sample = "日本語 한국어 中文 العربية हिन्दी עברית"

    @Test
    fun desktopBundlesSixScriptFallbacks() = runSkikoComposeUiTest(size = Size(300f, 300f)) {
        var fonts: List<Font> = emptyList()
        setContent { fonts = scriptFallbackFonts() }
        waitForIdle()
        assertEquals(6, fonts.size)
    }

    @Test
    fun everyFamilyRendersAllScripts() = runSkikoComposeUiTest(size = Size(600f, 400f)) {
        setContent {
            ClavisTheme(darkTheme = false) {
                val t = ClavisTheme.type
                Column {
                    Text(sample, style = t.body, modifier = Modifier.testTag("body"))
                    Text(sample, style = t.h2, modifier = Modifier.testTag("display"))
                    Text(sample, style = t.mono, modifier = Modifier.testTag("mono"))
                }
            }
        }
        waitForIdle()
        for (tag in listOf("body", "display", "mono")) {
            onNodeWithTag(tag).assertIsDisplayed()
            val img = onNodeWithTag(tag).captureToImage()
            // something was painted (not a blank line): at least a few hundred non-background pixels
            val px = img.toPixelMap()
            var dark = 0
            for (y in 0 until px.height step 2) for (x in 0 until px.width step 2) if (px[x, y].red < 0.5f) dark++
            assertTrue(dark > 200, "$tag rendered almost nothing ($dark dark samples)")
        }
    }
}
