package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Lettering
import io.github.mbaliga.clavis.store.ClavisStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Story (template-spec §7.11, parity item 72): episode filter, page order, numbered balloons, tap = light, nothing graded. */
@OptIn(ExperimentalTestApi::class)
class StoryTest {
    private val es = Lang("es")

    /** First (episode, page index) for Spanish whose page has at least one credibly lettered balloon. */
    private fun letteredPage(store: ClavisStore): Pair<Int, Int> {
        for (ep in store.engine.storyEpisodes(es)) {
            store.engine.storyPages(es, ep).forEachIndexed { pi, key ->
                val scenes = store.engine.pageScenes(key, es)
                if (scenes.size >= 2 && scenes.any { Lettering.rectOK(Lettering.rectFor(it, es)) && !it.x[es].isNullOrEmpty() }) return ep to pi
            }
        }
        error("no lettered Spanish page")
    }

    @Test
    fun episodeFilterKeepsOnlyEpisodesWithSixAlignedScenes() {
        val store = TestBoot.onboarded()
        val counts = HashMap<Int, Int>()
        for (sc in store.corpus.scenes) if (!sc.x[es].isNullOrEmpty()) counts[sc.e] = (counts[sc.e] ?: 0) + 1
        val eps = store.engine.storyEpisodes(es)
        assertTrue(eps.isNotEmpty())
        assertEquals(eps.sorted(), eps)
        for (e in eps) assertTrue((counts[e] ?: 0) >= 6, "episode $e has ${counts[e]} scenes")
        // page order: distinct page keys in scene order
        val pages = store.engine.storyPages(es, eps.first())
        assertEquals(pages.distinct(), pages)
    }

    @Test
    fun pageNavigationAndLaneChipsPersistState() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "story"; it.state.story.lane = "es" }
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("view-story").assertExists()
        val eps = store.engine.storyEpisodes(es)
        for (e in eps.take(3)) onNodeWithTag("episode-$e").assertExists()
        val pages = store.engine.storyPages(es, eps.first())
        onNodeWithTag("story-page-of").performScrollTo().assertTextContains(Copy.pageOfN(1, pages.size))
        onNodeWithTag("story-next").performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, store.state.story.pi)
        onNodeWithTag("story-page-of").performScrollTo().assertTextContains(Copy.pageOfN(2, pages.size))
        onNodeWithTag("story-prev").performScrollTo().performClick()
        waitForIdle()
        assertEquals(0, store.state.story.pi)
        onNodeWithTag("story-lane-fr").performScrollTo().performClick()
        waitForIdle()
        assertEquals("fr", store.state.story.lane)
        assertEquals(0, store.state.story.pi)
        onNodeWithTag("story-done").performScrollTo().performClick()
        waitForIdle()
        assertEquals("learn", store.state.view)
        assertTrue(store.state.seen.isEmpty(), "Story must not stamp seen")
        assertTrue(store.state.log.isEmpty(), "Story must not log")
    }

    @Test
    fun tappingABalloonLightsItsCaptionRow() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "story"; it.state.story.lane = "es" }
        val (ep, pi) = letteredPage(store)
        store.state.story.ep = ep; store.state.story.pi = pi; store.save()
        setContent { ClavisApp(store, darkOverride = false) }
        val key = store.engine.storyPages(es, ep)[pi]
        val scenes = store.engine.pageScenes(key, es)
        val target = scenes.first { Lettering.rectOK(Lettering.rectFor(it, es)) && !it.x[es].isNullOrEmpty() }
        for (sc in scenes) onNodeWithTag("caprow-${sc.i}").assertExists()
        onNodeWithTag("balloon-${target.i}", useUnmergedTree = true).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("caprow-lit").assertExists()
        assertTrue(store.state.seen.isEmpty())
        assertTrue(store.state.cards.values.all { it.isEmpty() })
    }
}
