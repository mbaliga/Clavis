package io.github.mbaliga.clavis

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.Phase
import io.github.mbaliga.clavis.store.SessionOrigin
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.ConfirmDialog
import io.github.mbaliga.clavis.ui.components.ToastHost
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.Dock
import io.github.mbaliga.clavis.ui.nav.LocalCompact
import io.github.mbaliga.clavis.ui.nav.LocalRailShown
import io.github.mbaliga.clavis.ui.nav.LocalShowGear
import io.github.mbaliga.clavis.ui.nav.Rail
import io.github.mbaliga.clavis.ui.nav.activeNav
import io.github.mbaliga.clavis.ui.screens.colophon.ColophonScreen
import io.github.mbaliga.clavis.ui.screens.learn.LearnScreen
import io.github.mbaliga.clavis.ui.screens.onboarding.OnboardingScreen
import io.github.mbaliga.clavis.ui.screens.cosmos.CosmosScreen
import io.github.mbaliga.clavis.ui.screens.languages.LanguagesScreen
import io.github.mbaliga.clavis.ui.screens.letters.LettersScreen
import io.github.mbaliga.clavis.ui.screens.media.MediaScreen
import io.github.mbaliga.clavis.ui.screens.paths.PathsScreen
import io.github.mbaliga.clavis.ui.screens.story.StoryScreen
import io.github.mbaliga.clavis.ui.screens.session.SessionScreen
import io.github.mbaliga.clavis.ui.screens.session.confirmStop
import io.github.mbaliga.clavis.ui.screens.settings.SettingsScreen
import io.github.mbaliga.clavis.ui.screens.test.BaselineTestScreen
import io.github.mbaliga.clavis.ui.screens.you.YouScreen
import io.github.mbaliga.clavis.ui.theme.ClavisMotion
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import io.github.mbaliga.clavis.ui.theme.ThemeMode
import kotlinx.coroutines.delay

/** Compact/expanded breakpoint (Material 3 window size classes; product-spec §3.4). */
val COMPACT_MAX_WIDTH = 600.dp

/**
 * The application root shared by every target. Boots the store on first composition (unless a
 * test pre-booted it), picks the theme (system by default, Settings override), and renders the
 * adaptive shell: dispatcher `!onboarded → onboarding · test → test · session → session/summary ·
 * else by view`, a floating dock (compact) or a rail (expanded) in non-modal states, toasts,
 * confirmations and the desktop keyboard map.
 */
@Composable
fun ClavisApp(store: ClavisStore, darkOverride: Boolean? = null) {
    val ui by store.ui.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = darkOverride ?: when (ui.theme) { ThemeMode.SYSTEM -> systemDark; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val reduced = (ui.ready && !store.state.settings.motion) || store.services.reducedMotion()
    LaunchedEffect(Unit) { if (store.current.phase is Phase.Loading) store.boot() }
    // Persistence: while the shell is composed a commit only marks the document dirty; the snapshot is
    // taken here, on the UI thread, once the commits pause (see ClavisStore "persistence"), and written
    // off-thread. Leaving the composition flushes synchronously so nothing is lost.
    DisposableEffect(store) {
        store.deferSnapshots = true
        onDispose { store.deferSnapshots = false; store.flushSaves() }
    }
    LaunchedEffect(ui.rev) {
        if (store.hasPendingSnapshot) { delay(ClavisStore.SAVE_COALESCE_MS); store.snapshot() }
    }
    ClavisTheme(darkTheme = dark, reducedMotion = reduced) {
        Surface(color = ClavisTheme.palette.paper, modifier = Modifier.fillMaxSize()) {
            when (val ph = ui.phase) {
                Phase.Loading -> CentredMessage(Copy.LOADING, "loading")
                is Phase.Failed -> CentredMessage(ph.message, "failed")
                Phase.Ready -> Shell(store, ui, reduced)
            }
        }
    }
}

@Composable
private fun CentredMessage(text: String, tag: String) {
    val p = ClavisTheme.palette
    Column(Modifier.fillMaxSize().tagged(tag), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Clavis", style = ClavisTheme.type.wordmark, color = p.ink)
        Spacer(Modifier.height(10.dp))
        Text(text, style = ClavisTheme.type.body, color = p.ink2, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
    }
}

@Composable
private fun Shell(store: ClavisStore, ui: UiState, reduced: Boolean) {
    val st = store.state
    val focus = remember { FocusRequester() }
    val keys = remember { KeyGuard() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BoxWithConstraints(
        Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { handleKey(store, ui, it, keys) }.safeDrawingPadding(),
    ) {
        val compact = maxWidth < COMPACT_MAX_WIDTH
        val active = activeNav(st.view)
        val showNav = st.onboarded && !ui.inModal && ui.colophon == null
        Row(Modifier.fillMaxSize()) {
            if (showNav && !compact) Rail(active, onSelect = store::setView, onSettings = { store.setView("set") })
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = if (compact) 600.dp else 640.dp).fillMaxSize()) {
                        CompositionLocalProvider(
                            LocalShowGear provides (showNav && compact),
                            LocalCompact provides compact,
                            LocalRailShown provides (showNav && !compact),
                            LocalRev provides ui.rev,
                        ) {
                            Content(store, ui, reduced)
                        }
                    }
                }
                if (showNav && compact) {
                    Dock(active, onSelect = store::setView, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
                }
                ToastHost(ui.toast, onExpire = store::clearToast, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (showNav && compact) 116.dp else 24.dp))
            }
        }
        ConfirmDialog(ui.confirm, onDismiss = store::dismissConfirm)
    }
}

@Composable
private fun Content(store: ClavisStore, ui: UiState, reduced: Boolean) {
    val st = store.state
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    when {
        ui.colophon != null -> ColophonScreen(store, ui, overlay = true)
        !st.onboarded -> OnboardingScreen(store, ui)
        ui.test != null -> BaselineTestScreen(store, ui.test)
        ui.session != null -> SessionScreen(store, ui)
        else -> AnimatedContent(
            targetState = st.view,
            transitionSpec = { viewTransition(initialState, targetState, reduced) },
            label = "view",
        ) { view -> ViewContent(store, ui, view) }
    }
}

/** Learn ↔ Cosmos ↔ Paths take the 520 ms morph (fade + scale); everything else cuts at 300 ms. */
private fun viewTransition(from: String, to: String, reduced: Boolean): ContentTransform {
    val morph = from in ClavisStore.MORPH_VIEWS && to in ClavisStore.MORPH_VIEWS
    val t = when {
        reduced -> EnterTransition.None togetherWith ExitTransition.None
        morph -> (fadeIn(tween(ClavisMotion.MORPH_MS, easing = ClavisMotion.Morph)) + scaleIn(tween(ClavisMotion.MORPH_MS, easing = ClavisMotion.Morph), initialScale = 0.965f)) togetherWith
            (fadeOut(tween(ClavisMotion.MORPH_MS / 2, easing = ClavisMotion.Standard)) + scaleOut(tween(ClavisMotion.MORPH_MS, easing = ClavisMotion.Morph), targetScale = 1.02f))
        else -> fadeIn(tween(ClavisMotion.STANDARD_MS, easing = ClavisMotion.Standard)) togetherWith fadeOut(tween(ClavisMotion.SHORT_MS, easing = ClavisMotion.Standard))
    }
    return ContentTransform(t.targetContentEnter, t.initialContentExit, t.targetContentZIndex, SizeTransform(clip = false))
}

/** The view switch. ui-features: replace a placeholder here with the real screen. */
@Composable
private fun ViewContent(store: ClavisStore, ui: UiState, view: String) {
    when (view) {
        "cosmos" -> CosmosScreen(store, ui)
        "paths" -> PathsScreen(store, ui)
        "letters" -> LettersScreen(store, ui)
        "you" -> YouScreen(store, ui)
        "set" -> SettingsScreen(store, ui)
        "colophon" -> ColophonScreen(store, ui, overlay = false)
        "story" -> StoryScreen(store, ui)
        "languages" -> LanguagesScreen(store, ui)
        "media" -> MediaScreen(store, ui)
        else -> LearnScreen(store, ui)
    }
}

private fun digitIndex(k: Key): Int? = when (k) {
    Key.One, Key.NumPad1 -> 0
    Key.Two, Key.NumPad2 -> 1
    Key.Three, Key.NumPad3 -> 2
    Key.Four, Key.NumPad4 -> 3
    Key.Five, Key.NumPad5 -> 4
    else -> null
}

/**
 * Keyboard re-entrancy guard: OS auto-repeat delivers KeyDown after KeyDown while a key is held,
 * and a read encounter is finished by a single press — so a read is finished only on the first
 * KeyDown after a KeyUp of that key, and never twice within [READ_DEBOUNCE_MS] (the next
 * encounter may be a read too, under the same key).
 */
class KeyGuard(private val wall: () -> Long = { io.github.mbaliga.clavis.core.SystemClock.now() }) {
    private val held = HashSet<Key>()
    private var lastReadFinish = 0L

    /** Records the event; true when this KeyDown repeats a key that was never released. */
    fun isRepeat(ev: KeyEvent): Boolean = when (ev.type) {
        KeyEventType.KeyDown -> !held.add(ev.key)
        KeyEventType.KeyUp -> { held.remove(ev.key); false }
        else -> false
    }

    /** True at most once per [READ_DEBOUNCE_MS]; the caller finishes the read only then. */
    fun allowReadFinish(): Boolean {
        val t = wall()
        if (t - lastReadFinish < READ_DEBOUNCE_MS) return false
        lastReadFinish = t
        return true
    }

    companion object { const val READ_DEBOUNCE_MS = 600L }
}

/**
 * Desktop keyboard map (product-spec §8.9, brief C): 1–5 dock destinations; Esc leaves modal
 * states through the same confirm as the button; test ←/n →/y; cloze 1–3; langid 1–4; echo 1–3;
 * Space/Enter = primary action. Text fields consume their own keys before this sees them.
 */
internal fun handleKey(store: ClavisStore, ui: UiState, ev: KeyEvent, guard: KeyGuard): Boolean {
    val repeat = guard.isRepeat(ev)
    if (ev.type != KeyEventType.KeyDown || !store.ready) return false
    val st = store.state
    val k = ev.key
    ui.confirm?.let { c ->
        return when (k) {
            Key.Escape -> { store.dismissConfirm(); true }
            Key.Enter -> { store.dismissConfirm(); c.onConfirm(); true }
            else -> false
        }
    }
    if (ui.colophon != null) {
        return if (k == Key.Escape || k == Key.Backspace) { store.closeColophon(); true } else false
    }
    if (!st.onboarded) {
        return when (k) {
            Key.DirectionRight, Key.Enter -> { if (st.ob >= 4) store.finishOnboarding() else store.obNext(); true }
            Key.DirectionLeft -> { store.obBack(); true }
            else -> false
        }
    }
    if (ui.test != null) {
        return when (k) {
            Key.DirectionRight, Key.Y -> { store.answerTest(true); true }
            Key.DirectionLeft, Key.N -> { store.answerTest(false); true }
            Key.Escape -> { store.cancelTest(); true }
            else -> false
        }
    }
    val s = ui.session
    if (s != null) {
        if (s.done) {
            return when (k) {
                Key.Enter, Key.Spacebar -> { store.startSession(SessionOrigin.ANOTHER); true }
                Key.Escape -> { store.endSession(); true }
                else -> false
            }
        }
        val eu = ui.enc ?: return false
        if (k == Key.Escape) { confirmStop(store); return true }
        val d = digitIndex(k)
        val primary = k == Key.Enter || k == Key.Spacebar
        when (val e = eu.enc) {
            is Encounter.Cloze -> {
                if (d != null && eu.answered == null && d < eu.options.size) { store.answerCloze(eu.options[d]); return true }
                if (primary && eu.answered != null) { store.next(); return true }
                if (k == Key.P) { store.toggleFullPage(); return true }
            }
            is Encounter.Read -> {
                // a held key or a second press within the debounce window must not grade the next read too
                if (k == Key.U || primary) { if (!repeat && guard.allowReadFinish()) store.finishRead(true); return true }
                if (k == Key.T) { if (!repeat && guard.allowReadFinish()) store.finishRead(false); return true }
                if (k == Key.E) { store.showEnglish(); return true }
                if (k == Key.P) { store.toggleFullPage(); return true }
            }
            is Encounter.Echo -> {
                if (d != null && eu.answered == null && d < 3) { store.answerEcho(3 - d); return true }
                if (primary && eu.answered != null) { store.next(); return true }
            }
            is Encounter.LangId -> {
                if (d != null && eu.answered == null && d < e.opts.size) { store.answerLangId(e.opts[d]); return true }
                if (primary && eu.answered != null) { store.next(); return true }
            }
        }
        return false
    }
    digitIndex(k)?.let { d -> if (d < ClavisStore.PRIMARY_VIEWS.size) { store.setView(ClavisStore.PRIMARY_VIEWS[d]); return true } }
    if (k == Key.Comma && (ev.isCtrlPressed || ev.isMetaPressed)) { store.setView("set"); return true }
    if (k == Key.Escape && st.view !in ClavisStore.PRIMARY_VIEWS) { store.setView("learn"); return true }
    if ((k == Key.Spacebar || k == Key.Enter) && st.view == "learn") { store.startSession(SessionOrigin.POOL); return true }
    return false
}
