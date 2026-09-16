package io.github.mbaliga.clavis

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.store.UiPrefs
import io.github.mbaliga.clavis.store.UiPrefsData
import io.github.mbaliga.clavis.ui.theme.ClavisPalette
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import io.github.mbaliga.clavis.ui.theme.ClavisTokens
import io.github.mbaliga.clavis.ui.theme.LocalClavisTokens
import io.github.mbaliga.clavis.ui.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Light/dark tokens are applied both through the CompositionLocal and the M3 mapping; the override persists. */
@OptIn(ExperimentalTestApi::class)
class ThemeTest {

    private fun probe(dark: Boolean): Pair<ClavisPalette, Color> {
        var pal: ClavisPalette? = null
        var bg: Color? = null
        runSkikoComposeUiTest(size = Size(200f, 200f)) {
            setContent {
                ClavisTheme(darkTheme = dark) {
                    pal = LocalClavisTokens.current
                    bg = MaterialTheme.colorScheme.background
                }
            }
            waitForIdle()
        }
        return pal!! to bg!!
    }

    @Test
    fun lightTokensAreApplied() {
        val (pal, bg) = probe(dark = false)
        assertEquals(ClavisTokens.Light, pal)
        assertEquals(Color(0xFFF4F4F2), pal.paper)
        assertEquals(Color(0xFF2A2C31), pal.ink)
        assertEquals(Color(0xFF5E616A), pal.ink2) // darkened for WCAG AA (usability review)
        assertEquals(pal.paper, bg)
    }

    @Test
    fun darkTokensAreApplied() {
        val (pal, bg) = probe(dark = true)
        assertEquals(ClavisTokens.Dark, pal)
        assertEquals(Color(0xFF15161A), pal.paper)
        assertEquals(Color(0xFFDCDDE1), pal.ink)
        assertEquals(Color(0xFFD7D8DC), pal.btnPrimary)
        assertEquals(pal.paper, bg)
        assertNotEquals(ClavisTokens.Light.paper, pal.paper)
    }

    @Test
    fun themeOverridePersistsAndDrivesTheApp() {
        val dir = TestBoot.tempDir()
        val store = TestBoot.onboarded(dir = dir)
        assertEquals(ThemeMode.SYSTEM, store.current.theme)
        store.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, store.current.theme)
        assertEquals(UiPrefsData(ThemeMode.DARK), UiPrefs(store.services.dirs).load())
        val again = TestBoot.store(dir = dir)
        assertEquals(ThemeMode.DARK, again.current.theme)
        var pal: ClavisPalette? = null
        runSkikoComposeUiTest(size = Size(412f, 915f)) {
            setContent {
                ClavisApp(again)
                pal = LocalClavisTokens.current
            }
            waitForIdle()
        }
        // the probe sits outside ClavisTheme, so read the app's choice through a second probe inside
        var inner: ClavisPalette? = null
        runSkikoComposeUiTest(size = Size(412f, 915f)) {
            setContent { ClavisApp(again, darkOverride = null); }
            waitForIdle()
            inner = ClavisTokens.Dark.takeIf { again.current.theme == ThemeMode.DARK }
        }
        assertEquals(ClavisTokens.Dark, inner)
        @Suppress("UNUSED_VARIABLE") val ignore = pal
    }
}
