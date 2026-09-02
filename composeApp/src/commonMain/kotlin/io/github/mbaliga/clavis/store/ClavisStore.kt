package io.github.mbaliga.clavis.store

import io.github.mbaliga.clavis.core.Branch
import io.github.mbaliga.clavis.core.BranchAction
import io.github.mbaliga.clavis.core.ClavisState
import io.github.mbaliga.clavis.core.Clock
import io.github.mbaliga.clavis.core.ClozeOption
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Corpus
import io.github.mbaliga.clavis.core.DayKey
import io.github.mbaliga.clavis.core.Encounter
import io.github.mbaliga.clavis.core.ImportLimits
import io.github.mbaliga.clavis.core.ImportedMedia
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.core.LanguageInfo
import io.github.mbaliga.clavis.core.LanguageRegistry
import io.github.mbaliga.clavis.core.LocalDayKey
import io.github.mbaliga.clavis.core.LogEntry
import io.github.mbaliga.clavis.core.PackManifest
import io.github.mbaliga.clavis.core.Pronunciation
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.Sentence
import io.github.mbaliga.clavis.core.Session
import io.github.mbaliga.clavis.core.Settings
import io.github.mbaliga.clavis.core.SystemClock
import io.github.mbaliga.clavis.core.data.DefaultContentPackLoader
import io.github.mbaliga.clavis.core.engine.ClavisEngine
import io.github.mbaliga.clavis.core.freshState
import io.github.mbaliga.clavis.core.lineOf
import io.github.mbaliga.clavis.core.sha256Hex
import io.github.mbaliga.clavis.core.state.FileIo
import io.github.mbaliga.clavis.core.state.JsonStateStore
import io.github.mbaliga.clavis.platform.PlatformServices
import io.github.mbaliga.clavis.platform.ioDispatcher
import io.github.mbaliga.clavis.platform.runBlockingCompat
import io.github.mbaliga.clavis.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * The application holder: owns the loaded packs, the engine, the state store and the transient
 * UI state, and exposes intents. Every intent that changes the learner document is followed by a
 * save, like the web build's `save()`. All intents are synchronous and must be called from the
 * UI thread; only [boot] suspends. Persistence is cheap on that thread: a commit marks the
 * document dirty, the shell snapshots it once per burst, and one writer thread does the file work
 * (see "persistence" below).
 *
 * Determinism hook for tests: pass a seeded [random] and a fixed [clock].
 */
class ClavisStore(
    val services: PlatformServices,
    private val readResource: suspend (String) -> ByteArray,
    val clock: Clock = SystemClock,
    val random: Random = Random.Default,
    val dayKey: DayKey = LocalDayKey(),
    private val stateFileName: String = "clavis-state.json",
) {
    companion object {
        val PRIMARY_VIEWS = listOf("learn", "cosmos", "paths", "letters", "you")
        val VALID_VIEWS = PRIMARY_VIEWS + listOf("set", "colophon", "story", "languages", "media")
        val MORPH_VIEWS = setOf("learn", "cosmos", "paths")
        /** Quiet time after the last commit before the shell snapshots the document. */
        const val SAVE_COALESCE_MS = 100L
        /** A burst of commits never postpones the snapshot longer than this. */
        const val SAVE_MAX_DELAY_MS = 600L
    }

    /** A loaded pack bundle; tests share one across cases to avoid re-parsing 20 MB per test. */
    class Loaded(
        val loader: DefaultContentPackLoader,
        val registry: LanguageRegistry,
        val corpus: Corpus,
        val manifests: List<PackManifest>,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    val current: UiState get() = _ui.value

    lateinit var loader: DefaultContentPackLoader
        private set
    lateinit var registry: LanguageRegistry
        private set
    lateinit var corpus: Corpus
        private set
    lateinit var engine: ClavisEngine
        private set
    lateinit var stateStore: JsonStateStore
        private set
    var manifests: List<PackManifest> = emptyList()
        private set

    private val prefs = UiPrefs(services.dirs)
    private var toastSeq = 0L

    /** Decoded page WebPs, LRU of ~12 (template-spec §10; brief G). */
    val pageImages: io.github.mbaliga.clavis.ui.components.PageImages by lazy {
        io.github.mbaliga.clavis.ui.components.PageImages({ key -> loader.pageBytes(key) })
    }

    val ready: Boolean get() = current.phase is Phase.Ready
    val state: ClavisState get() = engine.state
    val now: Long get() = clock.now()

    // ------------------------------------------------------------------ boot

    suspend fun loadPacks(): Loaded = withContext(Dispatchers.Default) {
        val l = DefaultContentPackLoader({ path -> readResource("files/$path") })
        val reg = l.languages()
        val corpus = l.loadCorpus()
        val man = l.manifests()
        Loaded(l, reg, corpus, man)
    }

    suspend fun boot(preloaded: Loaded? = null) {
        try {
            val p = preloaded ?: loadPacks()
            loader = p.loader; registry = p.registry; corpus = p.corpus; manifests = p.manifests
            (services.speaker as? io.github.mbaliga.clavis.platform.LocaleAware)?.localeFor = { l -> registry[l]?.ttsLocale?.takeIf { it.isNotBlank() } }
            stateStore = JsonStateStore(services.dirs, registry, clock, stateFileName)
            val st = withContext(Dispatchers.Default) { stateStore.loadOrFresh() }
            engine = ClavisEngine(corpus, registry, st, clock, random, dayKey)
            if (st.view !in VALID_VIEWS) st.view = "learn"
            engine.rollDay(now)
            val theme = prefs.load().theme
            _ui.update { it.copy(phase = Phase.Ready, theme = theme, rev = it.rev + 1) }
            if (stateStore.lastLoadWasCorrupt) toast(Copy.STATE_UNREADABLE) // the unreadable file sits beside the fresh one
        } catch (e: Throwable) {
            _ui.update { it.copy(phase = Phase.Failed(Copy.couldNotLoad(e.message ?: (e::class.simpleName ?: "unknown error")))) }
        }
    }

    /** Reads a bundled file (`packs/ATTRIBUTION.md`, `licenses/OFL-Hind.txt`) as text. */
    suspend fun readText(path: String): String = readResource("files/$path").decodeToString()

    // ------------------------------------------------------------------ plumbing

    private fun commit(save: Boolean = true, f: (UiState) -> UiState = { it }) {
        _ui.update { f(it).copy(rev = it.rev + 1) }
        if (save) save()
    }

    /** Bumps the revision without touching the document (a platform service became ready, e.g. TTS). */
    fun refresh() = commit(save = false)

    // ------------------------------------------------------------------ persistence (D-12)
    //
    // Encoding runs on the UI thread — the only thread that mutates the document, so a snapshot is
    // always consistent. Writing (temp file → fsync → rename) runs on one writer thread, in order,
    // and when several snapshots queue up only the newest is written. While the shell is composed
    // (`deferSnapshots`) a commit merely marks the document dirty; the shell calls `snapshot()` once,
    // SAVE_COALESCE_MS after the last commit and at most SAVE_MAX_DELAY_MS after the first. Without
    // a shell (tests, a bare store) `save()` snapshots and writes synchronously, as the web build did.

    private class Snapshot(val seq: Long, val json: String)

    private val writer = CoroutineScope(SupervisorJob() + ioDispatcher())
    private val writeQueue = Channel<Snapshot>(Channel.CONFLATED)
    private val writeMutex = Mutex()
    private var writtenSeq = 0L // guarded by writeMutex
    private var seq = 0L // UI thread
    private var latest: Snapshot? = null // UI thread
    private var dirtySince = 0L // UI thread: wall-clock ms of the first commit not yet snapshotted; 0 = clean

    /** Set by the shell while it is composed: commits defer their snapshot to [snapshot]; cleared (with a flush) when it leaves. */
    var deferSnapshots: Boolean = false

    /** True when the document changed since the last [snapshot]. */
    val hasPendingSnapshot: Boolean get() = dirtySince != 0L

    init {
        writer.launch { for (s in writeQueue) writeSnapshot(s) }
    }

    /** Persists the learner document: deferred and coalesced while the shell is composed, synchronous otherwise. */
    fun save() {
        if (!ready) return
        val wall = SystemClock.now()
        if (dirtySince == 0L) dirtySince = wall
        when {
            !deferSnapshots -> flushSaves()
            wall - dirtySince >= SAVE_MAX_DELAY_MS -> snapshot()
        }
    }

    /** Encodes the document now (call on the UI thread) and hands it to the writer; a no-op when nothing changed. */
    fun snapshot() {
        if (!ready || dirtySince == 0L) return
        dirtySince = 0L
        val json = runCatching { stateStore.encode(engine.state) }.getOrNull() ?: return
        val s = Snapshot(++seq, json)
        latest = s
        writeQueue.trySend(s)
    }

    /**
     * Snapshots and writes synchronously. The shell calls it when it leaves the composition, the
     * entry points before the process may die (activity stop, window close), tests before reading
     * the file back.
     */
    fun flushSaves() {
        if (!ready) return
        snapshot()
        val s = latest ?: return
        runBlockingCompat { writeSnapshot(s) }
    }

    private suspend fun writeSnapshot(s: Snapshot) {
        writeMutex.withLock {
            if (s.seq <= writtenSeq) return // already written, or superseded by a flush
            runCatching { stateStore.writeEncoded(s.json) }
            writtenSeq = s.seq
        }
    }

    /** Drops every queued snapshot: the document about to be written (import, reset) supersedes them. */
    private fun discardQueued() {
        runBlockingCompat { writeMutex.withLock { writtenSeq = seq } }
        dirtySince = 0L
    }

    /** Runs a suspend function that never actually suspends, on the calling thread. */
    private fun <T> blocking(block: suspend () -> T): T {
        var result: Result<T>? = null
        CoroutineScope(Dispatchers.Unconfined).launch { result = runCatching { block() } }
        return checkNotNull(result) { "blocking call suspended" }.getOrThrow()
    }

    fun toast(text: String) {
        val t = Toast(++toastSeq, text)
        _ui.update { it.copy(toast = t) }
    }

    fun clearToast(id: Long) {
        _ui.update { if (it.toast?.id == id) it.copy(toast = null) else it }
    }

    fun confirm(c: Confirm) = _ui.update { it.copy(confirm = c) }
    fun dismissConfirm() = _ui.update { it.copy(confirm = null) }

    /** Speaks through the D-8 chain; silently does nothing when no provider can voice the language. */
    fun speak(l: Lang, text: String) {
        if (text.isBlank()) return
        if (services.speaker.canSpeak(l)) services.speaker.speak(l, lineOf(l, text))
    }

    fun canSpeak(l: Lang): Boolean = services.speaker.canSpeak(l)

    // ------------------------------------------------------------------ registry helpers

    fun info(l: Lang): LanguageInfo? = registry[l]
    fun langName(l: Lang): String = engine.langName(l)
    fun family(l: Lang): String = registry[l]?.family?.name?.lowercase() ?: "other"
    fun isRtl(l: Lang): Boolean = registry[l]?.rtl == true
    fun sceneCount(l: Lang): Int = corpus.byLang[l]?.size ?: 0

    // ------------------------------------------------------------------ views (D-10)

    fun setView(v: String) {
        if (!ready || v !in VALID_VIEWS) return
        state.view = v
        commit { it.copy(colophon = null) }
    }

    /** `[n]` tapped: open the colophon over the current screen, remembering the way back (D-14). */
    fun openColophon(ref: Int? = null) {
        if (!ready) return
        if (ref != null) {
            state.refTarget = ref
            commit(save = false) { it.copy(colophon = ColophonOverlay(ref)) }
        } else {
            state.refTarget = null
            state.view = "colophon"
            commit { it.copy(colophon = null) }
        }
    }

    fun closeColophon() = commit(save = false) { it.copy(colophon = null) }

    /** The colophon consumed `state.refTarget` (scrolled + highlighted). */
    fun consumeRefTarget() {
        if (state.refTarget != null) { state.refTarget = null; save() }
    }

    // ------------------------------------------------------------------ onboarding (§7.1)

    fun obNext() { state.ob = minOf(4, state.ob + 1); commit() }
    fun obBack() { state.ob = maxOf(0, state.ob - 1); commit() }

    fun setLaneMode(l: Lang, mode: LaneMode) {
        state.lanes[l.code] = mode
        if (mode == LaneMode.OFF && state.pin == l.code) state.pin = null
        commit()
    }

    /** A preset (D-6 / OD-3): every registry language gets a mode in one commit; the pin is dropped if its lane rests. */
    fun setLanes(modes: Map<String, LaneMode>) {
        for (info in registry.all) state.lanes[info.iso.code] = modes[info.iso.code] ?: LaneMode.OFF
        if (state.pin != null && state.lanes[state.pin] == LaneMode.OFF) state.pin = null
        commit()
    }

    fun finishOnboarding() { state.onboarded = true; state.view = "learn"; commit() }
    fun replayIntro() { state.ob = 0; state.onboarded = false; commit { it.copy(session = null, enc = null, test = null, colophon = null) } }

    // ------------------------------------------------------------------ baseline test (§4)

    fun startTest(l: Lang) {
        if (!ready) return
        val t = engine.start(l, now, random)
        commit(save = false) { it.copy(test = t, colophon = null) }
    }

    fun answerTest(knowIt: Boolean) {
        val t = current.test ?: return
        val done = engine.answer(t, knowIt)
        if (done) {
            val (_, toastText) = engine.finish(t, now)
            commit { it.copy(test = null) }
            toast(toastText)
        } else {
            commit(save = false)
        }
    }

    fun cancelTest() = commit(save = false) { it.copy(test = null) }

    // ------------------------------------------------------------------ session (§3.6, §5)

    private fun encounterUi(session: Session): EncounterUi? {
        val e = session.cur ?: return null
        val t = now
        val opts = if (e is Encounter.Cloze) engine.clozeOptions(e, random) else emptyList()
        var coach = false
        if (e is Encounter.Cloze && state.coach["cloze"] != true) { coach = true; state.coach["cloze"] = true }
        return EncounterUi(
            enc = e, startedAt = t, options = opts, showClozeCoach = coach,
            fastReturn = e is Encounter.Cloze && engine.showFastReturnNotice(e, t),
            showReadCoach = e is Encounter.Read && engine.showReadCoach(e),
            readCopy = if (e is Encounter.Read) engine.readCopy(e) else "",
        )
    }

    fun startSession(origin: String = SessionOrigin.POOL) {
        if (!ready) return
        engine.rollDay(now)
        val s = engine.startSession(now)
        s.blocked?.let { toast(it); return } // every lane resting (D-6): no session, the reason instead
        val enc = encounterUi(s)
        commit { it.copy(session = s, enc = enc, sessionOrigin = origin, test = null, colophon = null) }
    }

    fun startGameSession() {
        if (!ready) return
        engine.rollDay(now)
        val s = engine.startGameSession(now)
        s.blocked?.let { toast(it); return }
        commit { it.copy(session = s, enc = encounterUi(s), sessionOrigin = SessionOrigin.BRANCH, test = null, colophon = null) }
    }

    private inline fun updateEnc(save: Boolean = false, crossinline f: (EncounterUi) -> EncounterUi) =
        commit(save) { u -> u.copy(enc = u.enc?.let(f)) }

    fun toggleFullPage() = updateEnc { it.copy(fullPage = !it.fullPage, gloss = null, hint = null) }
    fun showGloss(g: Gloss?) = updateEnc { it.copy(gloss = g) }
    fun showHint(index: Int?) = updateEnc { it.copy(hint = index?.let(::Hint)) }
    fun showEnglish() = updateEnc { it.copy(showEnglish = true) }

    /** Read encounter: flips a token between "new to me" and "I know this" (all occurrences of its lemma). */
    fun flipToken(lemma: String) = updateEnc { it.copy(flips = if (lemma in it.flips) it.flips - lemma else it.flips + lemma) }

    fun answerCloze(opt: ClozeOption) {
        val u = current
        val s = u.session ?: return
        val eu = u.enc ?: return
        val e = eu.enc as? Encounter.Cloze ?: return
        if (eu.answered != null) return
        val t = now
        val r = engine.answerCloze(s, e, opt, t - eu.startedAt, t)
        speak(e.l, e.sc.x[e.l].orEmpty())
        commit { it.copy(enc = it.enc?.copy(answered = Answered.Cloze(opt, r), hint = null)) }
    }

    fun routeHelped(via: Lang) {
        val e = current.enc?.enc ?: return
        engine.routeHelped(e.l, via)
        updateEnc(save = true) { it.copy(routes = it.routes + via.code) }
    }

    fun finishRead(understood: Boolean) {
        val u = current
        val s = u.session ?: return
        val eu = u.enc ?: return
        val e = eu.enc as? Encounter.Read ?: return
        val t = now
        val userUnknown = LinkedHashSet<String>()
        for (tok in e.sc.k[e.l].orEmpty()) {
            val modelKnown = engine.isKnown(e.l, tok, t)
            val flipped = tok.lemma in eu.flips
            if (if (modelKnown) flipped else !flipped) userUnknown.add(tok.lemma)
        }
        engine.finishRead(s, e, understood, userUnknown, t)
        advance(s)
    }

    fun answerEcho(grade: Int) {
        val u = current
        val s = u.session ?: return
        val eu = u.enc ?: return
        val e = eu.enc as? Encounter.Echo ?: return
        if (eu.answered != null) return
        val r = engine.answerEcho(s, e, grade, now)
        speak(e.l, e.sc.x[e.l].orEmpty())
        commit { it.copy(enc = it.enc?.copy(answered = Answered.Echo(grade, r), hint = null)) }
    }

    fun answerLangId(guess: Lang) {
        val u = current
        val s = u.session ?: return
        val eu = u.enc ?: return
        val e = eu.enc as? Encounter.LangId ?: return
        if (eu.answered != null) return
        val ok = engine.answerLangId(s, e, guess, now)
        speak(e.l, e.sc.x[e.l].orEmpty())
        commit { it.copy(enc = it.enc?.copy(answered = Answered.LangId(guess, ok))) }
    }

    /** `Next`: `session.i++; nextEncounter()`. */
    fun next() {
        val s = current.session ?: return
        advance(s)
    }

    private fun advance(s: Session) {
        engine.advance(s, now)
        val enc = if (s.done) null else encounterUi(s)
        commit { it.copy(enc = enc) }
    }

    /** Stop control: abandons the session without grading the current item (product-spec §3.7.4). */
    fun stopSession() = commit(save = false) { it.copy(session = null, enc = null, confirm = null) }

    /** Summary actions: `Done` / `See the paths` (optionally switching view). */
    fun endSession(toView: String? = null) {
        if (toView != null && toView in VALID_VIEWS) state.view = toView
        commit { it.copy(session = null, enc = null) }
    }

    // ------------------------------------------------------------------ paths (§6)

    fun takeBranch(b: Branch) {
        when (val a = engine.takeBranch(b, now)) {
            is BranchAction.StartTest -> { save(); startTest(a.l) }
            is BranchAction.OpenStory -> commit()
            BranchAction.StartGameSession -> { save(); startGameSession() }
            is BranchAction.StartSession -> { save(); startSession(SessionOrigin.BRANCH) }
            is BranchAction.OpenLetters -> { state.view = "letters"; commit() }
        }
    }

    fun undoBranch() {
        engine.undo()
        commit()
        toast(Copy.UNDONE_TOAST)
    }

    // ------------------------------------------------------------------ ui-features intents

    /** `As a tree` / `As a map` (persisted `S.pathView`). */
    fun setPathView(v: String) { state.pathView = if (v == "map") "map" else "tree"; commit() }

    /**
     * Letters tab. When the learner opens a script's tab themselves the script counts as visited
     * (D-21: `coach["letters:<iso>"] = true`), which retires the LETTERS branch in Paths.
     */
    fun setLetterTab(l: Lang, byUser: Boolean = true) {
        state.letterTab = l.code
        if (byUser) state.coach["letters:${l.code}"] = true
        commit()
    }

    fun setKanaSet(set: String) { state.kanaSet = if (set == "kata") "kata" else "hira"; commit() }

    /** Story lane / episode / page (template-spec §7.11): a lane change resets ep and page. */
    fun setStoryLane(l: Lang) { state.story.lane = l.code; state.story.ep = null; state.story.pi = 0; commit() }
    fun setStoryEpisode(ep: Int) { state.story.ep = ep; state.story.pi = 0; commit() }
    fun setStoryPage(pi: Int) { state.story.pi = maxOf(0, pi); commit() }

    /** Cosmos mode `depth` (Field) / `explore`, and the Explore→Field handoff word (`S.cosmos.webFor`). */
    fun setCosmosMode(mode: String) { state.cosmos.mode = if (mode == "explore") "explore" else "depth"; commit() }
    fun setCosmosWebFor(id: String?) { state.cosmos.webFor = id; commit() }
    fun setCosmosLens(lens: String) { state.cosmos.exlens = lens; commit() }
    fun setCosmosSets(on: Boolean) { state.cosmos.exsets = on; commit() }

    /** `Read where it appears`: opens Story on the page of [sceneId] in lane [l]. */
    fun openStoryAt(l: Lang, sceneId: Int) {
        val sc = corpus.scenes.getOrNull(sceneId) ?: return
        state.story.lane = l.code
        state.story.ep = sc.e
        state.story.pi = engine.storyPages(l, sc.e).indexOf(sc.m).coerceAtLeast(0)
        state.view = "story"
        commit()
    }

    // ------------------------------------------------------------------ media (D-7 user-media) & tingles (§5.5)

    /** Stores an imported text (at most [ImportLimits.MAX_LINES] lines); returns its id. Files never leave the device (D-13). */
    fun addMedia(title: String, l: Lang, kind: String, lines: List<String>): String {
        val id = "m${now}-${random.nextInt(1_000_000)}"
        state.media[id] = ImportedMedia(id = id, title = title.ifBlank { "untitled" }, lang = l.code, addedAt = now, kind = kind, lines = lines.take(ImportLimits.MAX_LINES))
        commit()
        return id
    }

    fun deleteMedia(id: String) {
        state.media.remove(id)
        commit { if (it.mediaOpen == id) it.copy(mediaOpen = null) else it }
    }

    fun openMedia(id: String?) = commit(save = false) { it.copy(mediaOpen = id) }

    /** Sentences mode for a lane (null closes it); lands on the Media view. */
    fun openSentences(l: Lang?) {
        if (l != null && state.view != "media") state.view = "media"
        commit(save = l != null) { it.copy(sentencesLane = l?.code, mediaOpen = null) }
    }

    /**
     * A "tingle" (product-spec §5.5): the learner met a lexicon word out in the world. Logged as
     * `{type:"tingle", l, lm}` — no grade, no reward, no scheduling, nothing counted as progress.
     * (`LogEntry` has no `src`/`ctx` field and the engine has no pre-exposure hook: shared_issues.)
     */
    fun logTingle(l: Lang, lemma: String) {
        state.log.add(LogEntry(t = now, l = l.code, type = "tingle", lm = lemma))
        val over = state.log.size - ClavisEngine.LOG_CAP
        if (over > 0) state.log.subList(0, over).clear()
        commit()
    }

    // ------------------------------------------------------------------ optional packs (D-7)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pronCache = HashMap<String, Map<String, Pronunciation>>()
    private val sentCache = HashMap<String, List<Sentence>>()

    /** The pronunciation pack for [l] (IPA + Commons audio references), parsed once per run. */
    suspend fun pronunciations(l: Lang): Map<String, Pronunciation> =
        pronCache[l.code] ?: withContext(Dispatchers.Default) { loader.pronunciations(l) }.also { pronCache[l.code] = it }

    /** The Tatoeba sentence pack for [l], parsed once per run. */
    suspend fun sentences(l: Lang): List<Sentence> =
        sentCache[l.code] ?: withContext(Dispatchers.Default) { loader.sentences(l) }.also { sentCache[l.code] = it }

    // ------------------------------------------------------------------ recordings (D-13: download on tap, cached)

    /** Container extension of a recording URL ("mp3", "ogg", "wav"). */
    fun clipFormat(url: String): String {
        val tail = url.substringAfterLast('/').substringBefore('?').substringAfterLast('.', "").lowercase()
        return when (tail) { "mp3", "ogg", "wav", "oga" -> if (tail == "oga") "ogg" else tail; else -> "mp3" }
    }

    /** Local cache path for a recording URL: `<cache>/audio/<sha256 of the URL>.<ext>` — nothing from the URL itself reaches the file system. */
    fun clipPath(url: String): String = "${services.dirs.cache}/audio/${sha256Hex(url.encodeToByteArray())}.${clipFormat(url)}"

    fun clipCached(url: String): Boolean = FileIo.exists(clipPath(url))

    /** Whether a Play control may be shown for [url]: this platform decodes the format and can fetch it (or already has it). */
    fun clipOffered(url: String): Boolean = clipFormat(url) in services.audioFormats && (services.canDownload || clipCached(url))

    private fun mimeOf(url: String) = when (clipFormat(url)) { "ogg" -> "audio/ogg"; "wav" -> "audio/wav"; else -> "audio/mpeg" }

    /**
     * Plays a recording: from the cache when present, otherwise downloads it first (the tap is the
     * consent, D-13). Runs off the UI thread; the UI sees `clipBusy` / `clipsReady` change.
     */
    fun playClip(url: String) {
        if (!ready || url in current.clipBusy) return
        val path = clipPath(url)
        _ui.update { it.copy(clipBusy = it.clipBusy + url, rev = it.rev + 1) }
        scope.launch {
            val ok = FileIo.exists(path) || services.downloader.download(url, path)
            if (ok) {
                _ui.update { it.copy(clipBusy = it.clipBusy - url, clipsReady = it.clipsReady + url, rev = it.rev + 1) }
                val bytes = FileIo.read(path)
                if (bytes != null) runCatching { services.audio.play(bytes, mimeOf(url)) }
            } else {
                _ui.update { it.copy(clipBusy = it.clipBusy - url, rev = it.rev + 1) }
                toast(io.github.mbaliga.clavis.ui.screens.cosmos.CosmosCopy.DOWNLOAD_FAILED)
            }
        }
    }

    // ------------------------------------------------------------------ settings (§7.9)

    fun updateSettings(block: Settings.() -> Unit) {
        state.settings.block()
        commit()
    }

    fun setTheme(mode: ThemeMode) {
        prefs.save(UiPrefsData(mode))
        commit(save = false) { it.copy(theme = mode) }
    }

    fun setPin(l: Lang?) { state.pin = l?.code; commit() }

    fun exportJson(): String = stateStore.encode(engine.state)

    /** Returns true on success; toasts either way (`Restored` / `That doesn't look like a Clavis backup`). */
    fun importJson(text: String): Boolean = try {
        discardQueued() // nothing older may land on disk after the imported document
        val st = blocking { stateStore.importJson(text) } // throws before writing when the backup is bad
        engine.state = st
        if (st.view !in VALID_VIEWS) st.view = "learn"
        engine.rollDay(now)
        commit { it.copy(session = null, enc = null, test = null) }
        flushSaves()
        toast(Copy.RESTORED)
        true
    } catch (e: Exception) {
        toast(Copy.NOT_A_BACKUP)
        false
    }

    fun resetAll() {
        discardQueued()
        runCatching { blocking { stateStore.reset() } }
        engine.state = freshState(registry, now)
        engine.rollDay(now)
        commit { it.copy(session = null, enc = null, test = null, colophon = null, confirm = null) }
        flushSaves()
    }

    /** Scenes on the same page carrying a line in [l] (`pageOf`). */
    fun pageOf(sc: Scene, l: Lang): List<Scene> = engine.pageScenes(sc.m, l)
}
