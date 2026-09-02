package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.BranchKind
import io.github.mbaliga.clavis.ui.screens.paths.PathsMapLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Paths map (template-spec §6.6, parity item 56): geometry, tap → card, Walk this way. */
@OptIn(ExperimentalTestApi::class)
class PathsMapTest {

    @Test
    fun geometryFollowsTheSpec() {
        val store = TestBoot.onboarded()
        val branches = store.engine.computeBranches(store.now)
        val nodes = PathsMapLayout.build(branches)
        val ghosts = branches.sumOf { b -> (if (b.kind == BranchKind.NEW) 3 else 2).toInt() }
        assertEquals(1 + branches.size + ghosts, nodes.size)
        for (n in nodes.drop(1)) {
            val r = kotlin.math.hypot(n.x, n.y)
            val want = if (n.ghost) PathsMapLayout.R_GHOST else PathsMapLayout.R_BRANCH
            assertTrue(kotlin.math.abs(r - want) < 0.01f, "node ${n.id} at radius $r, expected $want")
            if (n.ghost) assertTrue(n.size in 11f..18f)
        }
        // first branch straight up (angle −π/2)
        assertTrue(kotlin.math.abs(nodes[1].x) < 0.01f && kotlin.math.abs(nodes[1].y + PathsMapLayout.R_BRANCH) < 0.01f)
    }

    @Test
    fun tappingABranchNodeShowsTheCardAndWalksThePath() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "paths"; it.state.pathView = "map" }
        setContent { ClavisApp(store, darkOverride = false) }
        val canvas = onNodeWithTag("paths-map-canvas").performScrollTo().fetchSemanticsNode()
        val b = canvas.boundsInRoot
        val branches = store.engine.computeBranches(store.now)
        val nodes = PathsMapLayout.build(branches)
        val s = PathsMapLayout.scale(b.width, b.height)
        val first = nodes[1]
        onNodeWithTag("paths-map-canvas").performTouchInput { click(Offset(b.width / 2 + first.x * s, b.height / 2 + first.y * s)) }
        waitForIdle()
        onNodeWithTag("map-card").assertExists()
        onNodeWithTag("map-walk").performScrollTo().performClick()
        waitForIdle()
        assertEquals(BranchKind.TEST, branches[0].kind)
        assertNotNull(store.current.test)
        assertEquals("test", store.state.pathLog.last().kind)
    }
}
