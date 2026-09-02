package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.Card
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.ui.screens.cosmos.ExploreModel
import io.github.mbaliga.clavis.ui.screens.cosmos.FieldModel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cosmos Explore (template-spec §7.5, parity items 67–70): subsample, edges inside it, a converging layout, the tools. */
@OptIn(ExperimentalTestApi::class)
class ExploreLayoutTest {

    @Test
    fun subsampleEdgesAndLayoutConverge() {
        val store = TestBoot.onboarded { it.state.cardsOf(Lang("es"))["casa"] = Card(prov = true) }
        val data = FieldModel.build(store.corpus, store.registry, store.state, store.engine.scheduler, { store.engine.thr(it) }, store.now, dustCount = 10, dustLite = Color.Gray)
        val nodes = ExploreModel.subsample(data)
        assertEquals(ExploreModel.CAP, nodes.size)
        // scores are non-increasing and a carded word scores into the sample
        val scores = nodes.map { ExploreModel.score(data.points[it.pointIndex]) }
        assertTrue(scores.zipWithNext().all { (a, b) -> a >= b })
        assertTrue(nodes.any { data.points[it.pointIndex].id == "es|casa" })
        val inSample = nodes.map { it.pointIndex }.toSet()
        val edges = ExploreModel.edges(data, nodes)
        assertTrue(edges.isNotEmpty())
        for ((a, b, _) in edges) { assertTrue(nodes[a].pointIndex in inSample && nodes[b].pointIndex in inSample) }
        val res = runBlocking { ExploreModel.layout(nodes, edges, maxIter = 200) }
        assertTrue(res.converged, "layout should converge within 200 iterations (took ${res.iterations})")
        assertTrue(res.iterations in 2..200)
        assertTrue(nodes.all { it.x.isFinite() && it.y.isFinite() })
        // family geography survives: the anchor pull keeps nodes near their Field position
        val drift = nodes.map { kotlin.math.hypot(it.x - it.x0, it.y - it.y0) }
        assertTrue(drift.average() < 100.0, "average drift ${drift.average()} (field radius ≈ 190 units)")
        assertTrue(drift.average() > 1.0, "the layout actually moved nodes apart")
        // hull of three or more points is a polygon
        assertEquals(4, ExploreModel.hull(listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f, 0.5f to 0.5f)).size)
    }

    @Test
    fun exploreModeToolsAndSearchHandoff() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "cosmos"; it.state.cosmos.mode = "explore" }
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("explore-canvas").assertExists()
        onNodeWithTag("tool-families").performClick()
        waitForIdle()
        assertEquals(true, store.state.cosmos.exsets)
        onNodeWithTag("tool-relations").performClick()
        waitForIdle()
        assertEquals("relations", store.state.cosmos.exlens)
        onNodeWithTag("search-field").performScrollTo().performTextInput("casa")
        onNodeWithTag("find").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("word-card").assertExists()
        // See its web hands the word to the Field
        onNodeWithTag("word-web").performScrollTo().performClick()
        waitForIdle()
        assertEquals("depth", store.state.cosmos.mode)
        onNodeWithTag("field-canvas").assertExists()
        onNodeWithTag("word-card").assertExists()
        onNodeWithTag("hud-explore").performClick()
        waitForIdle()
        assertEquals("explore", store.state.cosmos.mode)
    }
}
