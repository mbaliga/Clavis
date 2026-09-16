package io.github.mbaliga.clavis

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runSkikoComposeUiTest
import io.github.mbaliga.clavis.core.BranchKind
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Paths tree (template-spec §6.5, §6.7; D-14/D-21 LETTERS branch). */
@OptIn(ExperimentalTestApi::class)
class PathsTreeTest {

    @Test
    fun branchesRenderAndTakingTheTestBranchOpensTheTest() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "paths" }
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("view-paths").assertExists()
        val branches = store.engine.computeBranches(store.now)
        assertTrue(branches.isNotEmpty())
        onNodeWithTag("paths-ways").assertTextContains(Copy.waysForward(branches.size))
        val cardTags = allTaggedTexts("pathcard-").map { it.first }.filter { Regex("pathcard-(test|due|new|read|game|letters)-\\d+").matches(it) }
        assertEquals(branches.size, cardTags.size, "one card per branch: $cardTags")
        // fresh, untested lanes: the first branch is a TEST branch
        assertEquals(BranchKind.TEST, branches[0].kind)
        onNodeWithTag("take-0").performScrollTo().performClick()
        waitForIdle()
        assertNotNull(store.current.test)
        assertEquals(branches[0].l, store.current.test?.l)
        onNodeWithTag("test").assertExists()
        assertEquals("test", store.state.pathLog.last().kind)
    }

    @Test
    fun lettersBranchIsPresentForACjkLaneAndOpensLetters() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "paths" }
        assertEquals(LaneMode.REC, store.state.laneMode(Lang("ja")))
        setContent { ClavisApp(store, darkOverride = false) }
        val branches = store.engine.computeBranches(store.now)
        val idx = branches.indexOfFirst { it.kind == BranchKind.LETTERS }
        assertTrue(idx >= 0, "LETTERS branch expected with ja active: ${branches.map { it.kind }}")
        onNodeWithTag("pathcard-letters-$idx").assertExists()
        onNodeWithTag("take-$idx").performScrollTo().performClick()
        waitForIdle()
        assertEquals("letters", store.state.view)
        assertEquals("ja", store.state.letterTab)
        assertEquals(true, store.state.coach["letters:ja"])
    }

    @Test
    fun longerViewListsEveryActiveLaneAndPinToggles() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "paths" }
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("longer-view").performScrollTo()
        for (l in store.engine.activeLanes()) onNodeWithTag("lane-overview-${l.code}").assertExists()
        onNodeWithTag("lane-meta-es").assertTextContains(Copy.LP_WATCHING, substring = true)
        onNodeWithTag("lane-meta-es").assertTextContains(Copy.ASSUMES_UNTESTED, substring = true)
        assertNull(store.state.pin)
        onNodeWithTag("overview-pin-es").performScrollTo().performClick()
        waitForIdle()
        assertEquals("es", store.state.pin)
        onNodeWithText(Copy.PINNED_BUTTON).assertExists()
        onNodeWithTag("overview-pin-es").performClick()
        waitForIdle()
        assertNull(store.state.pin)
    }

    @Test
    fun treeMapToggleIsPersisted() = runSkikoComposeUiTest(size = Size(1280f, 800f)) {
        val store = TestBoot.onboarded { it.state.view = "paths" }
        setContent { ClavisApp(store, darkOverride = false) }
        onNodeWithTag("paths-tree").assertExists()
        onNodeWithTag("paths-map-btn").performClick()
        waitForIdle()
        assertEquals("map", store.state.pathView)
        onNodeWithTag("paths-map").assertExists()
        onNodeWithTag("paths-tree-btn").performClick()
        waitForIdle()
        assertEquals("tree", store.state.pathView)
    }
}
