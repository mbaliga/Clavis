package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.store.ClavisStore
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the main screens in light and dark at phone (412×915) and desktop (1280×800) sizes into
 * `<root>/build/ui-shots/<screen>-<theme>-<size>.png` for visual review (brief M).
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotTest {
    private val out: File = File(System.getProperty("clavis.shots") ?: "../build/ui-shots").also { it.mkdirs() }
    private val sizes = listOf("mobile" to Size(412f, 915f), "desktop" to Size(1280f, 800f))
    private val themes = listOf("light" to false, "dark" to true)

    private fun ComposeUiTest.save(name: String) {
        val img = onRoot().captureToImage().toAwtImage()
        val f = File(out, "$name.png")
        ImageIO.write(img, "png", f)
        assertTrue(f.length() > 1000, "screenshot $name is empty")
    }

    private fun shoot(screen: String, prepare: () -> ClavisStore, act: ComposeUiTest.(ClavisStore) -> Unit = {}) {
        for ((sizeName, size) in sizes) for ((themeName, dark) in themes) {
            val store = prepare()
            runSkikoComposeUiTest(size = size) {
                setContent { ClavisApp(store, darkOverride = dark) }
                waitForIdle()
                act(store)
                waitForIdle()
                save("$screen-$themeName-$sizeName")
            }
        }
    }

    @Test fun learn() = shoot("learn", { TestBoot.onboarded() })

    @Test fun onboardingStep1() = shoot("onboarding-1", { TestBoot.store() })

    @Test fun settings() = shoot("settings", { TestBoot.onboarded { it.state.view = "set" } })

    @Test fun you() = shoot("you", { TestBoot.onboarded { it.state.view = "you" } })

    @Test fun colophon() = shoot("colophon", { TestBoot.onboarded { it.state.view = "colophon" } })

    @Test fun encounterCropped() = shoot("encounter-cropped", { TestBoot.onboarded(seed = 1) { TestBoot.seedDueCloze(it) }.also { it.startSession() } }) { store ->
        val e = store.current.enc?.enc as Encounter.Cloze
        waitUntil(timeoutMillis = 30_000) { store.pageImages.peek(e.sc.m) != null }
    }

    @Test fun encounterWholePage() = shoot("encounter-page", { TestBoot.onboarded(seed = 1) { TestBoot.seedDueCloze(it) }.also { it.startSession() } }) { store ->
        val e = store.current.enc?.enc as Encounter.Cloze
        waitUntil(timeoutMillis = 30_000) { store.pageImages.peek(e.sc.m) != null }
        onNodeWithTag("toggle-page").performScrollTo().performClick()
        waitForIdle()
        // scroll the page into view
        onNodeWithTag("scene-full").performScrollTo()
    }
}
