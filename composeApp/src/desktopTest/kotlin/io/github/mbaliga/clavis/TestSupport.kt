package io.github.mbaliga.clavis

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.mbaliga.clavis.core.AppDirs
import io.github.mbaliga.clavis.core.Card
import io.github.mbaliga.clavis.core.Clock
import io.github.mbaliga.clavis.core.DAY_MS
import io.github.mbaliga.clavis.core.DayKey
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Lettering
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.Token
import io.github.mbaliga.clavis.platform.PlatformServices
import io.github.mbaliga.clavis.resources.Res
import io.github.mbaliga.clavis.store.ClavisStore
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/** 2026-09-02T08:00:00Z, a fixed "now" so every FSRS number is reproducible. */
const val NOW: Long = 1_788_336_000_000L

/**
 * Shared test plumbing: the real packs are parsed once per JVM ([loaded]); each test gets a fresh
 * store over a temp data dir with a seeded Random and a fixed clock. Motion is switched off in
 * every prepared store so no infinite animation keeps the test clock from settling.
 */
@OptIn(ExperimentalResourceApi::class)
object TestBoot {
    val loaded: ClavisStore.Loaded by lazy {
        runBlocking { ClavisStore(services(tempDir()), { Res.readBytes(it) }).loadPacks() }
    }

    fun tempDir(): File = Files.createTempDirectory("clavis-test").toFile().also { it.deleteOnExit() }

    fun services(dir: File): PlatformServices = PlatformServices(
        dirs = object : AppDirs {
            override val data: String = dir.absolutePath
            override val cache: String = File(dir, "cache").absolutePath
            override val downloads: String = File(dir, "downloads").absolutePath
        },
        name = "Test",
    )

    fun store(seed: Int = 7, dir: File = tempDir(), now: Long = NOW, boot: Boolean = true, prepare: (ClavisStore) -> Unit = {}): ClavisStore {
        val s = ClavisStore(services(dir), { Res.readBytes(it) }, clock = Clock { now }, random = Random(seed), dayKey = DayKey { "2026-9-2" })
        if (boot) runBlocking { s.boot(loaded) }
        if (boot) { s.state.settings.motion = false; prepare(s); s.save(); s.flushSaves() } // written before any test reads the file back
        return s
    }

    /** A store past onboarding, on Learn. */
    fun onboarded(seed: Int = 7, dir: File = tempDir(), prepare: (ClavisStore) -> Unit = {}): ClavisStore =
        store(seed, dir) { s -> s.state.onboarded = true; s.state.ob = 4; s.state.view = "learn"; prepare(s) }

    /** A Spanish scene with a credible es rect and a lemma that occurs in exactly one scene, once in its line (≥ 5 chars). */
    data class Seed(val scene: Scene, val token: Token)

    fun clozeSeed(store: ClavisStore): Seed {
        val es = Lang("es")
        val corpus = store.corpus
        for (id in corpus.byLang[es].orEmpty()) {
            val sc = corpus.scene(id)
            if (!Lettering.rectOK(Lettering.rectFor(sc, es))) continue
            val line = sc.x[es] ?: continue
            for (t in sc.k[es].orEmpty()) {
                if (t.surface.length < 5 || !t.surface.all { it.isLetter() }) continue
                if (Regex(Regex.escape(t.surface)).findAll(line).count() != 1) continue
                if (corpus.byLemma[es]?.get(t.lemma)?.size != 1) continue
                if (corpus.lex[es]?.containsKey(t.lemma) != true) continue
                return Seed(sc, t)
            }
        }
        error("no suitable Spanish cloze seed found")
    }

    /** Seeds one due Spanish card with credible geometry and pins Spanish, so the next session opens on that cloze. */
    fun seedDueCloze(store: ClavisStore): Seed {
        val seed = clozeSeed(store)
        val card = Card(s = 10.0, d = 5.0, due = NOW - 1000, last = NOW - 5 * DAY_MS, reps = 2, lapses = 0, g = 3, first = NOW - 6 * DAY_MS)
        store.state.cardsOf(Lang("es"))[seed.token.lemma] = card
        store.state.pin = "es"
        return seed
    }

    /**
     * Drives a started session to its summary through the store's intents (the keyboard path), the
     * way [stepSession] does through the buttons: first option / understood / "I know it" / first
     * language, then Next. Fails loudly if the session does not finish.
     */
    fun finishSession(store: ClavisStore, maxSteps: Int = 80) {
        var guard = 0
        while (store.current.session?.done != true && guard++ < maxSteps) {
            val eu = store.current.enc ?: break
            when (val e = eu.enc) {
                is Encounter.Cloze -> if (eu.answered == null) store.answerCloze(eu.options.first()) else store.next()
                is Encounter.Read -> store.finishRead(true)
                is Encounter.Echo -> if (eu.answered == null) store.answerEcho(3) else store.next()
                is Encounter.LangId -> if (eu.answered == null) store.answerLangId(e.opts.first()) else store.next()
            }
        }
        check(store.current.session?.done == true) { "session did not finish in $guard steps" }
    }

    /** Where the screenshot matrix and the accessibility report land: `-Dclavis.shots=`, else `<root>/build/ui-shots`. */
    fun shotsDir(): File = File(System.getProperty("clavis.shots") ?: "../build/ui-shots").also { it.mkdirs() }
}

fun SemanticsNode.textContent(): String = buildString {
    config.getOrNull(SemanticsProperties.Text)?.forEach { append(it.text) }
    config.getOrNull(SemanticsProperties.EditableText)?.let { append(it.text) }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.allTaggedTexts(prefix: String): List<Pair<String, String>> =
    onAllNodesWithTagPrefix(prefix).fetchSemanticsNodes().map { n -> (n.config.getOrNull(SemanticsProperties.TestTag) ?: "") to n.textContent() }

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.onAllNodesWithTagPrefix(prefix: String): SemanticsNodeInteractionCollection =
    onAllNodes(androidx.compose.ui.test.SemanticsMatcher("tag starts with $prefix") { n ->
        n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }, useUnmergedTree = true)

/** Drives one step of a session by clicking the right control for the current encounter. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.stepSession(store: ClavisStore) {
    val eu = store.current.enc ?: return
    when (eu.enc) {
        is Encounter.Cloze -> if (eu.answered == null) onNodeWithTag("cloze-option-0").performScrollTo().performClick() else onNodeWithTag("next").performScrollTo().performClick()
        is Encounter.Read -> onNodeWithTag("understood").performScrollTo().performClick()
        is Encounter.Echo -> if (eu.answered == null) onNodeWithTag("echo-3").performScrollTo().performClick() else onNodeWithTag("next").performScrollTo().performClick()
        is Encounter.LangId -> if (eu.answered == null) onNodeWithTag("langid-option-0").performScrollTo().performClick() else onNodeWithTag("next").performScrollTo().performClick()
    }
    waitForIdle()
}

@Suppress("unused")
private fun keep() = hasTestTag("x")
