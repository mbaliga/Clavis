package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every screen of [Screens] into `<root>/build/ui-shots/<screen>-<variant>-<size>.png` for
 * visual review (brief M): light and dark at phone (412×915) and desktop (1280×800) sizes, the whole
 * app under right-to-left layout at both sizes (`-rtl-`), and at the 2× font-size step on the phone
 * (`-x2-mobile`). The directory is overridable with the `clavis.shots` system property.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotTest {
    private val out: File = TestBoot.shotsDir()
    private val mobile = "mobile" to MOBILE
    private val desktop = "desktop" to DESKTOP

    /** The matrix: seven renders per screen. */
    private val matrix: List<Pair<Variant, Pair<String, Size>>> = listOf(
        Variant.LIGHT to mobile, Variant.LIGHT to desktop,
        Variant.DARK to mobile, Variant.DARK to desktop,
        Variant.RTL to mobile, Variant.RTL to desktop,
        Variant.X2 to mobile,
    )

    private fun ComposeUiTest.save(name: String) {
        val img = onRoot().captureToImage().toAwtImage()
        val f = File(out, "$name.png")
        ImageIO.write(img, "png", f)
        assertTrue(f.length() > 1000, "screenshot $name is empty")
    }

    private fun shoot(case: ScreenCase) {
        for ((variant, size) in matrix) {
            val (sizeName, px) = size
            renderScreen(case, variant, px) { save("${case.name}-${variant.id}-$sizeName") }
        }
    }

    @Test fun learn() = shoot(Screens.learn)

    @Test fun onboardingStep1() = shoot(Screens.onboarding1)

    @Test fun settings() = shoot(Screens.settings)

    @Test fun you() = shoot(Screens.you)

    @Test fun colophon() = shoot(Screens.colophon)

    // ui-features screens
    @Test fun cosmos() = shoot(Screens.cosmos)

    @Test fun cosmosExplore() = shoot(Screens.cosmosExplore)

    @Test fun paths() = shoot(Screens.paths)

    @Test fun pathsMap() = shoot(Screens.pathsMap)

    @Test fun letters() = shoot(Screens.letters)

    @Test fun story() = shoot(Screens.story)

    @Test fun languages() = shoot(Screens.languages)

    @Test fun media() = shoot(Screens.media)

    @Test fun encounterCropped() = shoot(Screens.encounterCropped)

    @Test fun encounterWholePage() = shoot(Screens.encounterWholePage)

    // modal states and the blocked Learn
    @Test fun baselineTest() = shoot(Screens.baselineTest)

    @Test fun sessionSummary() = shoot(Screens.sessionSummary)

    @Test fun learnResting() = shoot(Screens.learnResting)
}
