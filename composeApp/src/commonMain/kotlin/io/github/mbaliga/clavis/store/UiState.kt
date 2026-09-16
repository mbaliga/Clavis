package io.github.mbaliga.clavis.store

import androidx.compose.runtime.compositionLocalOf
import io.github.mbaliga.clavis.core.BaselineTest
import io.github.mbaliga.clavis.core.ClozeOption
import io.github.mbaliga.clavis.core.ClozeResult
import io.github.mbaliga.clavis.core.EchoResult
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.Session
import io.github.mbaliga.clavis.ui.theme.ThemeMode

/**
 * The store's revision, provided by the shell on every commit. The learner document and the
 * engine's session/test objects are mutated in place (they are not snapshot state), so a screen
 * that only reads them would be skipped by the Compose compiler when nothing it *uses* changed.
 * Every screen reads `LocalRev.current` once at the top; that subscribes its scope to commits.
 */
val LocalRev = compositionLocalOf { 0L }

/** Boot phase of the app: packs loading → ready, or a load failure shown in place of the UI. */
sealed interface Phase {
    data object Loading : Phase
    data class Failed(val message: String) : Phase
    data object Ready : Phase
}

data class Toast(val id: Long, val text: String)

/** A confirmation the UI must show before a destructive intent runs (Stop, Reset, second productive lane). */
data class Confirm(
    val title: String?,
    val text: String,
    val confirmLabel: String,
    val dismissLabel: String,
    /** True when confirming erases what cannot come back (Reset): the dialog emphasises Cancel and Enter does not confirm. */
    val destructive: Boolean = false,
    val onConfirm: () -> Unit,
)

/** The colophon opened *over* the current view by a `[n]` citation — Back returns to the sender (D-14). */
data class ColophonOverlay(val refTarget: Int)

/** What the user did with the current encounter, once graded. */
sealed interface Answered {
    data class Cloze(val chosen: ClozeOption, val result: ClozeResult) : Answered
    data class Echo(val grade: Int, val result: EchoResult) : Answered
    data class LangId(val guess: Lang, val ok: Boolean) : Answered
}

/** Whole-page gloss card: a tapped sibling balloon's line and its English (template-spec §5). */
data class Gloss(val scene: Scene, val line: String, val english: String)

/** The hint popover under a caption token (template-spec §5.6). */
data class Hint(val tokenIndex: Int)

/**
 * Per-encounter UI state. Everything transient about the encounter on screen lives here so the
 * screen, the keyboard handler and the tests see one truth; it is rebuilt on every advance.
 */
data class EncounterUi(
    val enc: Encounter,
    val startedAt: Long,
    /** Cloze options, computed once per encounter (deterministic under a seeded Random). */
    val options: List<ClozeOption> = emptyList(),
    val fullPage: Boolean = false,
    /** Lemmas the reader flipped in a read encounter. */
    val flips: Set<String> = emptySet(),
    val showEnglish: Boolean = false,
    val answered: Answered? = null,
    /** Bridge languages the reader said helped (`routeHelped`). */
    val routes: Set<String> = emptySet(),
    val gloss: Gloss? = null,
    val hint: Hint? = null,
    val showClozeCoach: Boolean = false,
    val fastReturn: Boolean = false,
    val showReadCoach: Boolean = false,
    val readCopy: String = "",
)

object SessionOrigin {
    const val POOL = "pool"
    const val BRANCH = "branch"
    const val ANOTHER = "another"
}

/**
 * The observable UI state. The learner document itself (`ClavisState`) is mutated in place by the
 * engine; [rev] is bumped on every mutation so composables that read the document directly
 * recompose. `view` is *not* mirrored here — read `store.state.view` — because it is persisted.
 */
data class UiState(
    val phase: Phase = Phase.Loading,
    val rev: Long = 0,
    val session: Session? = null,
    val enc: EncounterUi? = null,
    val sessionOrigin: String? = null,
    val test: BaselineTest? = null,
    val colophon: ColophonOverlay? = null,
    val toast: Toast? = null,
    val confirm: Confirm? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Recording URLs being fetched right now (D-13: only after a tap). */
    val clipBusy: Set<String> = emptySet(),
    /** Recording URLs known to be cached in this run (a hint for recomposition; the file is the truth). */
    val clipsReady: Set<String> = emptySet(),
    /** The imported text open in Media's reading mode, by id. */
    val mediaOpen: String? = null,
    /** The lane whose Tatoeba sentences are being browsed (Sentences mode), or null. */
    val sentencesLane: String? = null,
) {
    val ready: Boolean get() = phase is Phase.Ready
    /** Onboarding, the baseline test and a live session hide the dock/rail (product-spec §3.3 rule 1). */
    val inModal: Boolean get() = test != null || (session != null && !session.done)
}
