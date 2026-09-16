package io.github.mbaliga.clavis

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import io.github.mbaliga.clavis.core.Copy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Automated accessibility checks over every screen of [Screens] at phone size:
 *  1. every clickable carries a role, a description or (merged) text, and every text field a label;
 *  2. every clickable is a 48 dp target — legitimate exceptions are allowlisted with a reason, the
 *     rest fails the assertion ([clickablesAre48dpTargets]);
 *  3. every screen composes under right-to-left layout, and the Story page controls point the
 *     RTL-correct way (`backArrow()`/`forwardArrow()` in Primitives.kt resolve from `LocalLayoutDirection`);
 *  4. every screen composes at the 2× font-size step, and the You record table still scrolls sideways.
 */
@OptIn(ExperimentalTestApi::class)
class AccessibilityAuditTest {

    @Test
    fun everyClickableAndTextFieldIsLabelled() {
        val a = audit
        assertTrue(a.screensWithoutControls.isEmpty(), "screens where the audit saw no control at all: ${a.screensWithoutControls}")
        assertTrue(a.unlabelled.isEmpty(), "${a.unlabelled.size} control(s) without a role, a description or text:\n" + a.unlabelled.joinToString("\n"))
    }

    /**
     * 48 dp targets, measured on the layout size (density is 1f here). The list, with a per-screen
     * count, is printed and written to `<clavis.shots>/a11y-touch-targets.txt`. This test found the
     * last three 44 dp sites in the app — [Segmented] (Primitives.kt), the Cosmos HUD pills
     * (CosmosScreen.kt `Hud`) and the Letters tabs (LettersScreen.kt), all since raised to 48 dp —
     * so the assertion is unconditional; `CLAVIS_A11Y_STRICT`/`clavis.a11y.strict` stay as an escape
     * hatch if a future screen needs to allowlist a deliberate exception before fixing it.
     */
    @Test
    fun clickablesAre48dpTargets() {
        val a = audit
        val report = buildString {
            appendLine("touch targets under 48 dp: ${a.smallTargets.size} offender(s), ${a.allowedSmall.size} allowlisted, ${a.perScreen.values.sumOf { it.first }} clickables audited")
            a.perScreen.forEach { (screen, counts) -> appendLine("  $screen: ${counts.second} of ${counts.first}") }
            a.smallTargets.forEach { appendLine("  $it") }
            if (a.allowedSmall.isNotEmpty()) appendLine("allowlisted:")
            a.allowedSmall.forEach { appendLine("  $it") }
        }
        println(report)
        File(TestBoot.shotsDir(), "a11y-touch-targets.txt").writeText(report)
        if (STRICT_OFF) return
        assertTrue(a.smallTargets.isEmpty(), report)
    }

    @Test
    fun everyScreenComposesUnderRtl() {
        for (case in Screens.all) renderScreen(case, Variant.RTL, MOBILE) {
            assertTrue(onRoot().fetchSemanticsNode().children.isNotEmpty(), "${case.name}: empty semantics under RTL")
            assertTrue(onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty(), "${case.name}: no control under RTL")
        }
    }

    /** Under RTL the start edge is on the right, so "previous" points right and "next" points left. */
    @Test
    fun storyPageControlsPointTheRtlCorrectWay() {
        renderScreen(Screens.story, Variant.RTL, MOBILE) {
            onNodeWithTag("story-prev").assertTextContains("→", substring = true)
            onNodeWithTag("story-next").assertTextContains("←", substring = true)
        }
        renderScreen(Screens.story, Variant.LIGHT, MOBILE) {
            onNodeWithTag("story-prev").assertTextContains("←", substring = true)
            onNodeWithTag("story-next").assertTextContains("→", substring = true)
        }
    }

    @Test
    fun everyScreenComposesAtDoubleFontScale() {
        for (case in Screens.all) renderScreen(case, Variant.X2, MOBILE) {
            assertTrue(onRoot().fetchSemanticsNode().children.isNotEmpty(), "${case.name}: empty semantics at 2× font scale")
            assertTrue(onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty(), "${case.name}: no control at 2× font scale")
        }
    }

    /** The record table keeps its columns at 2× by scrolling sideways (`widthIn(min)` + `horizontalScroll`), not by clipping. */
    @Test
    fun youRecordTableScrollsSidewaysAtDoubleFontScale() {
        renderScreen(Screens.you, Variant.X2, MOBILE) {
            val table = onNode(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyDescendant(hasText(Copy.YOU_HEADERS.first().uppercase())),
                useUnmergedTree = true,
            )
            table.assertExists()
            val range = table.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
            assertTrue(range.maxValue() > 0f, "the record table should overflow sideways at 2× (max scroll = ${range.maxValue()})")
        }
    }

    private companion object {
        /** A render costs about a second: one pass over the screens feeds both the label and the target checks. */
        val audit: Audit by lazy { runAudit() }
    }
}

/** Minimum touch target in px: 48 dp × density, and the skiko test density is 1f. */
private const val MIN_TARGET = 48

/** Escape hatch for a future regression: set to not fail the build while a new offender gets fixed. */
private val STRICT_OFF = (System.getProperty("clavis.a11y.strict") ?: System.getenv("CLAVIS_A11Y_STRICT")) == "false"
private val ANY_NODE = SemanticsMatcher("any node") { true }

private class Audit(
    val unlabelled: List<String>,
    val smallTargets: List<String>,
    val allowedSmall: List<String>,
    val screensWithoutControls: List<String>,
    /** screen → (clickables audited, clickables under 48 dp and not allowlisted). */
    val perScreen: Map<String, Pair<Int, Int>>,
)

/**
 * Exceptions to the 48 dp rule, each keyed by tag or structure and justified. Anything not listed
 * here counts as an offender.
 */
private val TOUCH_ALLOWLIST: List<Pair<String, (SemanticsNode) -> Boolean>> = listOf(
    // Cite.kt citedString: one superscript `[n]` inside a paragraph — the paragraph is the target and the link is read through its text.
    "inline [n] citation link inside running prose" to { n -> isInlineTextLink(n) },
    // TokenRow.kt CaptionTokens: a word inside a caption line is as wide as the word; widening it would break the line, and the line's Speak button carries the same action.
    "a word inside a caption line" to { n -> n.tag()?.let { it.startsWith("tok-") || it.startsWith("story-tok-") } == true },
    // SceneBox.kt BalloonText: a comic balloon lettered at page geometry; the caption row below is the accessible carrier of the same line.
    "a comic balloon lettered at page geometry" to { n -> n.description().startsWith("balloon") },
)

@OptIn(ExperimentalTestApi::class)
private fun runAudit(): Audit {
    val unlabelled = ArrayList<String>()
    val small = ArrayList<String>()
    val allowed = ArrayList<String>()
    val empty = ArrayList<String>()
    val perScreen = LinkedHashMap<String, Pair<Int, Int>>()
    for (case in Screens.all) renderScreen(case, Variant.LIGHT, MOBILE) {
        val unmerged = unmergedById()
        val fields = onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        val fieldIds = fields.map { it.id }.toSet()
        for (n in fields) if (!textFieldLabelled(n, unmerged)) unlabelled += describe(case.name, n, unmerged)
        val clickables = onAllNodes(hasClickAction()).fetchSemanticsNodes()
        if (clickables.isEmpty()) empty += case.name
        var offenders = 0
        for (n in clickables) {
            if (n.id in fieldIds) continue // a text field's own onClick (focus) is covered by the field rule above
            if (!n.isLabelled() && !isInlineTextLink(n)) unlabelled += describe(case.name, n, unmerged)
            // The layout size, unclipped, so a control scrolled out of view measures the same as one in view.
            // (Compose's touchBoundsInRoot would also add the 48 dp hit-slop it grants small targets when no
            // neighbour competes — a tie-breaker, not a target — and clip it to the viewport.)
            if (n.size.width >= MIN_TARGET && n.size.height >= MIN_TARGET) continue
            val why = TOUCH_ALLOWLIST.firstOrNull { it.second(n) }?.first
            if (why == null) { offenders++; small += describe(case.name, n, unmerged) } else allowed += describe(case.name, n, unmerged) + "  [allowed: $why]"
        }
        perScreen[case.name] = clickables.size to offenders
    }
    return Audit(unlabelled, small, allowed, empty, perScreen)
}

// ---- semantics helpers

/** Unmerged nodes by id, for walking parents and children of a merged node. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.unmergedById(): Map<Int, SemanticsNode> =
    onAllNodes(ANY_NODE, useUnmergedTree = true).fetchSemanticsNodes().associateBy { it.id }

private fun SemanticsNode.tag(): String? = config.getOrNull(SemanticsProperties.TestTag)
private fun SemanticsNode.role() = config.getOrNull(SemanticsProperties.Role)
private fun SemanticsNode.description(): String = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" | ").orEmpty()
private fun SemanticsNode.labelText(): String = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

/** Something a screen reader can say: a role, a description, or (merged) text. */
private fun SemanticsNode.isLabelled(): Boolean = role() != null || description().isNotBlank() || textContent().isNotBlank()

/** A field is labelled by a description, by text merged into it (label, placeholder), or by the text just before it. */
private fun textFieldLabelled(n: SemanticsNode, unmerged: Map<Int, SemanticsNode>): Boolean {
    if (n.description().isNotBlank() || n.labelText().isNotBlank()) return true
    val u = unmerged[n.id] ?: return false
    val siblings = u.parent?.children ?: return false
    val i = siblings.indexOfFirst { it.id == u.id }
    return siblings.take(i).lastOrNull { it.textContent().isNotBlank() } != null
}

/**
 * A `LinkAnnotation` inside running text (the `[n]` citations of CitedText): Compose composes one
 * clickable box per link range, marked `LinkTestMarker`, with no label of its own — assistive tech
 * reaches the link through the surrounding text's clickable span, whose label is the linked characters.
 */
private fun isInlineTextLink(n: SemanticsNode): Boolean = n.config.contains(SemanticsProperties.LinkTestMarker)

/** The first text in the node's own subtree, else in its parent's — the nearest label a sighted user reads. */
private fun nearestText(n: SemanticsNode, unmerged: Map<Int, SemanticsNode>): String {
    val u = unmerged[n.id] ?: return ""
    fun firstText(root: SemanticsNode, skip: SemanticsNode?): String? {
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val x = queue.removeFirst()
            if (x !== skip) x.textContent().takeIf { it.isNotBlank() }?.let { return it }
            queue.addAll(x.children)
        }
        return null
    }
    return firstText(u, null) ?: u.parent?.let { firstText(it, u) } ?: ""
}

/** One line per offender: which screen, its tag/role/description/text, its layout size and (unclipped) position, and the nearest text. */
private fun describe(screen: String, n: SemanticsNode, unmerged: Map<Int, SemanticsNode>): String {
    val at = n.positionInRoot
    return buildString {
        append(screen).append(": tag=").append(n.tag() ?: "-")
        append(" role=").append(n.role()?.toString() ?: "-")
        append(" cd='").append(n.description().take(50)).append('\'')
        append(" text='").append(n.textContent().take(50)).append('\'')
        append(" size=").append(n.size.width).append('x').append(n.size.height)
        append(" at=").append(at.x.toInt()).append(',').append(at.y.toInt())
        append(" near='").append(nearestText(n, unmerged).take(50)).append('\'')
    }
}
