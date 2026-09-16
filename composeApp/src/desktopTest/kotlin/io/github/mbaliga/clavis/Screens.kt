@file:OptIn(ExperimentalTestApi::class)

package io.github.mbaliga.clavis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.store.ClavisStore

/** The two window sizes of the matrix: a phone and a desktop window (brief M). */
val MOBILE = Size(412f, 915f)
val DESKTOP = Size(1280f, 800f)

/**
 * One screen state of the visual / accessibility matrix. [prepare] builds a fresh store already in
 * that state — a new store per render, so variants never share mutable state — and [act] runs inside
 * the composition once the app is idle (waiting for a page image to decode, toggling the whole page).
 */
class ScreenCase(val name: String, val prepare: () -> ClavisStore, val act: ComposeUiTest.(ClavisStore) -> Unit = {})

/** How the app root is wrapped for one render: theme, layout direction, font scale. */
class Variant(val id: String, val dark: Boolean = false, val rtl: Boolean = false, val fontScale: Float = 1f) {
    companion object {
        val LIGHT = Variant("light")
        val DARK = Variant("dark", dark = true)
        /** Right-to-left layout around the whole app, the way an Arabic or Hebrew system locale lays it out. */
        val RTL = Variant("rtl", rtl = true)
        /** The largest Android font-size step: every sp value doubles, dp values do not. */
        val X2 = Variant("x2", fontScale = 2f)
    }
}

/** [ClavisApp] under a [Variant]: the same CompositionLocals a platform would set around the root. */
@Composable
fun VariantApp(store: ClavisStore, v: Variant) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalLayoutDirection provides if (v.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalDensity provides Density(density.density, fontScale = v.fontScale),
    ) {
        ClavisApp(store, darkOverride = v.dark)
    }
}

/** Renders [case] under [v] at [size] (px = dp at density 1), settles it, and runs [block] on the composed screen. */
fun renderScreen(case: ScreenCase, v: Variant, size: Size, block: ComposeUiTest.(ClavisStore) -> Unit = {}) {
    val store = case.prepare()
    runSkikoComposeUiTest(size = size) {
        setContent { VariantApp(store, v) }
        waitForIdle()
        case.act(this, store)
        waitForIdle()
        block(store)
    }
}

/** Every screen the matrix covers, each reached deterministically through the store API (as the other tests do). */
object Screens {
    val learn = ScreenCase("learn", { TestBoot.onboarded() })
    val onboarding1 = ScreenCase("onboarding-1", { TestBoot.store() })
    val settings = ScreenCase("settings", { TestBoot.onboarded { it.state.view = "set" } })
    val you = ScreenCase("you", { TestBoot.onboarded { it.state.view = "you" } })
    val colophon = ScreenCase("colophon", { TestBoot.onboarded { it.state.view = "colophon" } })

    // ui-features screens
    val cosmos = ScreenCase("cosmos", { TestBoot.onboarded { it.state.view = "cosmos" } })
    val cosmosExplore = ScreenCase("cosmos-explore", { TestBoot.onboarded { it.state.view = "cosmos"; it.state.cosmos.mode = "explore"; it.state.cosmos.exsets = true } })
    val paths = ScreenCase("paths", { TestBoot.onboarded { it.state.view = "paths" } })
    val pathsMap = ScreenCase("paths-map", { TestBoot.onboarded { it.state.view = "paths"; it.state.pathView = "map" } })
    val letters = ScreenCase("letters", { TestBoot.onboarded { it.state.view = "letters" } })
    val story = ScreenCase("story", { TestBoot.onboarded { it.state.view = "story"; it.state.story.lane = "es" } }) { store ->
        waitUntil(timeoutMillis = 30_000) { store.pageImages.size > 0 }
    }
    val languages = ScreenCase("languages", { TestBoot.onboarded { it.state.view = "languages" } })
    val media = ScreenCase("media", {
        TestBoot.onboarded { it.state.view = "media" }.also { s ->
            s.addMedia("Canción de ejemplo", Lang("es"), "lrc", listOf("La casa azul", "El perro duerme"))
        }
    })
    val encounterCropped = ScreenCase("encounter-cropped", { TestBoot.onboarded(seed = 1) { TestBoot.seedDueCloze(it) }.also { it.startSession() } }) { store ->
        val e = store.current.enc?.enc as Encounter.Cloze
        waitUntil(timeoutMillis = 30_000) { store.pageImages.peek(e.sc.m) != null }
    }
    val encounterWholePage = ScreenCase("encounter-page", { TestBoot.onboarded(seed = 1) { TestBoot.seedDueCloze(it) }.also { it.startSession() } }) { store ->
        val e = store.current.enc?.enc as Encounter.Cloze
        waitUntil(timeoutMillis = 30_000) { store.pageImages.peek(e.sc.m) != null }
        onNodeWithTag("toggle-page").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("scene-full").performScrollTo() // scroll the page into view
    }

    // modal states and the blocked Learn
    /** The baseline yes/no test (§4) on its first Spanish item. */
    val baselineTest = ScreenCase("baseline-test", { TestBoot.onboarded().also { it.startTest(Lang("es")) } })
    /** The summary after a whole seeded session — BootFlowTest's route, driven through the store instead of the buttons. */
    val sessionSummary = ScreenCase("session-summary", { TestBoot.onboarded().also { it.startSession(); TestBoot.finishSession(it) } })
    /** Learn with every language resting (D-6): the pool is disabled and says why. */
    val learnResting = ScreenCase("learn-resting", { TestBoot.onboarded { s -> for (l in s.registry.order) s.state.lanes[l.code] = LaneMode.OFF } })

    val all: List<ScreenCase> = listOf(
        learn, onboarding1, settings, you, colophon,
        cosmos, cosmosExplore, paths, pathsMap, letters, story, languages, media, encounterCropped, encounterWholePage,
        baselineTest, sessionSummary, learnResting,
    )
}
