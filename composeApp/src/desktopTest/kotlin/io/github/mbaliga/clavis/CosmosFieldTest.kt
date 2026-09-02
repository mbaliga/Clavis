package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.Card
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.ui.screens.cosmos.Camera
import io.github.mbaliga.clavis.ui.screens.cosmos.FieldData
import io.github.mbaliga.clavis.ui.screens.cosmos.FieldModel
import io.github.mbaliga.clavis.ui.screens.cosmos.Projector
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cosmos Field (template-spec §7.4, parity items 57–66): deterministic geometry, familiarity, picking, the word card. */
@OptIn(ExperimentalTestApi::class)
class CosmosFieldTest {

    private fun build(store: ClavisStore): FieldData = FieldModel.build(
        store.corpus, store.registry, store.state, store.engine.scheduler, { store.engine.thr(it) }, store.now, dustCount = 500, dustLite = Color.Gray,
    )

    @Test
    fun positionsAreDeterministicAndFamiliarityFollowsTheCards() {
        val base = TestBoot.onboarded()
        val esLemmas = base.corpus.lex.getValue(Lang("es")).keys.toList()
        val frLemma = base.corpus.lex.getValue(Lang("fr")).keys.first()
        val store = TestBoot.onboarded {
            it.state.cardsOf(Lang("es"))[esLemmas[0]] = Card(prov = true)
            it.state.cardsOf(Lang("es"))[esLemmas[1]] = Card(intro = true)
            it.state.cardsOf(Lang("fr"))[frLemma] = Card(s = 20.0, d = 5.0, due = NOW + 10 * 86_400_000L, last = NOW - 86_400_000L, reps = 3, g = 3, first = NOW - 86_400_000L)
        }
        val a = build(store)
        val b = build(store)
        assertEquals(a.points.size, b.points.size)
        assertTrue(a.points.size > 5000, "every lemma of every active lane is a point (${a.points.size})")
        for (i in a.points.indices) {
            assertEquals(a.points[i].id, b.points[i].id)
            assertEquals(a.points[i].x, b.points[i].x); assertEquals(a.points[i].z, b.points[i].z); assertEquals(a.points[i].h, b.points[i].h)
        }
        assertEquals(a.edges.size, b.edges.size)
        assertTrue(a.edges.isNotEmpty(), "etymology edges exist inside the drawn set")
        for (e in a.edges) { assertTrue(e.a in a.points.indices && e.b in a.points.indices) }
        assertEquals(0.72f, a.points[a.index.getValue("es|${esLemmas[0]}")].h)
        assertEquals(0f, a.points[a.index.getValue("es|${esLemmas[1]}")].h)
        val fr = a.points[a.index.getValue("fr|$frLemma")].h
        assertTrue(fr > 0.5f && fr <= 1f, "rated card → retrievability ($fr)")
        // untested lane, no card: thr = 9 → nothing is presumed known
        assertTrue(a.points.filter { it.l == Lang("de") }.all { it.h == 0f })
        // radius stays in the world (kinship pull / borrowing push)
        assertTrue(a.points.all { it.d in 0.1f..1.1f })
        // dust is deterministic too
        assertEquals(a.dust.map { it.x }, b.dust.map { it.x })
    }

    @Test
    fun cameraProjectsAndPickingFindsTheNearestSpeck() {
        val store = TestBoot.onboarded()
        val data = build(store)
        val w = 600f; val h = 600f
        val pr = Projector(w, h, Camera.HOME)
        val sx = FloatArray(data.points.size); val sy = FloatArray(data.points.size); val vis = BooleanArray(data.points.size)
        for ((i, q) in data.points.withIndex()) {
            val y = (FieldModel.terrainY(q.x, q.z, 0f) * 0.6f + q.yj + q.h * FieldModel.H + 0.035f * kotlin.math.sin(q.seed))
            val zc = pr.depth(q.x, y, q.z)
            vis[i] = zc >= Projector.MIN_Z
            if (vis[i]) { sx[i] = pr.screenX(q.x, y, q.z, zc); sy[i] = pr.screenY(q.x, y, q.z, zc) }
        }
        assertTrue(vis.count { it } > data.points.size / 2)
        val target = data.points.indices.first { vis[it] && sx[it] in 100f..500f && sy[it] in 100f..500f }
        val picked = FieldModel.nearest(sx, sy, vis, data.points, sx[target], sy[target], 30f)
        assertTrue(picked >= 0)
        assertTrue(hypot(sx[picked] - sx[target], sy[picked] - sy[target]) <= 30f)
        assertEquals(-1, FieldModel.nearest(sx, sy, vis, data.points, -500f, -500f, 30f))
    }

    @Test
    fun tappingASpeckOrSearchingOpensTheWordCard() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "cosmos" }
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("view-cosmos").assertExists()
        onNodeWithTag("field-canvas").assertExists()
        onNodeWithTag("word-card").assertDoesNotExist()
        // search
        onNodeWithTag("search-field").performScrollTo().performTextInput("casa")
        onNodeWithTag("find").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("word-card").assertExists()
        onNodeWithTag("word-surface").assertTextContains("casa", substring = true, ignoreCase = true)
        onNodeWithTag("word-origin").assertExists()
        onNodeWithTag("word-close").performClick()
        waitForIdle()
        onNodeWithTag("word-card").assertDoesNotExist()
        // tap a projected speck (motion is off in tests → the wave is complete and the field is static)
        val canvas = onNodeWithTag("field-canvas").performScrollTo().fetchSemanticsNode()
        val b = canvas.boundsInRoot
        val data = FieldModel.build(store.corpus, store.registry, store.state, store.engine.scheduler, { store.engine.thr(it) }, store.now, dustCount = 10, dustLite = Color.Gray)
        val pr = Projector(b.width, b.height, Camera.HOME)
        var hit: Offset? = null
        for (q in data.points) {
            val y = (FieldModel.terrainY(q.x, q.z, 0f) * 0.6f + q.yj + q.h * FieldModel.H + 0.035f * kotlin.math.sin(q.seed))
            val zc = pr.depth(q.x, y, q.z)
            if (zc < Projector.MIN_Z) continue
            val x = pr.screenX(q.x, y, q.z, zc); val yy = pr.screenY(q.x, y, q.z, zc)
            if (x in b.width * 0.3f..b.width * 0.7f && yy in b.height * 0.3f..b.height * 0.7f) { hit = Offset(x, yy); break }
        }
        val at = hit ?: error("no speck projected inside the canvas")
        onNodeWithTag("field-canvas").performTouchInput { click(at) }
        waitForIdle()
        onNodeWithTag("word-card").assertExists()
        // no voice and no download in the test bundle → no play control and no provenance label: nothing is claimed
        onNodeWithTag("word-audio-label").assertDoesNotExist()
        onNodeWithTag("word-audio-play").assertDoesNotExist()
    }
}
