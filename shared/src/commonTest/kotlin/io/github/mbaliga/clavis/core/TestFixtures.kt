package io.github.mbaliga.clavis.core

import io.github.mbaliga.clavis.core.data.ListLanguageRegistry
import io.github.mbaliga.clavis.core.engine.ClavisEngine
import kotlin.random.Random

/**
 * Shared test fixtures: the nine v0.10 languages as a registry (template-spec §9), a small
 * synthetic corpus with controllable knownness, and deterministic clock/random/day-key stand-ins.
 */
object Fx {
    val ES = Lang("es"); val FR = Lang("fr"); val IT = Lang("it"); val DE = Lang("de"); val NL = Lang("nl")
    val JA = Lang("ja"); val KO = Lang("ko"); val ZH = Lang("zh"); val AR = Lang("ar"); val EN = Lang.EN

    /** 2025-09-02T08:00:00Z. */
    const val T0: Long = 1_756_800_000_000L
    const val HOUR: Long = 3_600_000L

    private fun info(
        iso: String, name: String, family: Family, script: Script, sib: List<String>, mode: LaneMode, colour: Long,
        reading: String? = null, rtl: Boolean = false, space: Boolean = true, primer: Boolean = false, locale: String = "$iso-XX",
    ) = LanguageInfo(
        iso = Lang(iso), name = name, nativeName = name, family = family, script = script, rtl = rtl, defaultLane = mode,
        siblings = sib.map(::Lang), readingSystem = reading, ttsLocale = locale, colour = colour, spaceSeparated = space,
        enabledByDefault = true, needsScriptPrimer = primer, hasScriptPrimer = primer,
    )

    val registry: LanguageRegistry = ListLanguageRegistry(
        listOf(
            info("es", "Spanish", Family.ROMANCE, Script.LATIN, listOf("fr", "it"), LaneMode.PROD, 0xFFB3543BL, locale = "es-ES"),
            info("fr", "French", Family.ROMANCE, Script.LATIN, listOf("es", "it"), LaneMode.REC, 0xFF7A5CC0L, locale = "fr-FR"),
            info("it", "Italian", Family.ROMANCE, Script.LATIN, listOf("es", "fr"), LaneMode.REC, 0xFF3E8D5CL, locale = "it-IT"),
            info("de", "German", Family.GERMANIC, Script.LATIN, listOf("nl"), LaneMode.REC, 0xFF4E6B8AL, locale = "de-DE"),
            info("nl", "Dutch", Family.GERMANIC, Script.LATIN, listOf("de"), LaneMode.PROD, 0xFFC08A2DL, locale = "nl-NL"),
            info("ja", "Japanese", Family.CJK, Script.CJK, listOf("zh", "ko"), LaneMode.REC, 0xFFB84A6EL, "rōmaji", space = false, primer = true, locale = "ja-JP"),
            info("ko", "Korean", Family.CJK, Script.CJK, listOf("ja", "zh"), LaneMode.REC, 0xFF2E8F8AL, "romanised", primer = true, locale = "ko-KR"),
            info("zh", "Chinese", Family.CJK, Script.CJK, listOf("ja", "ko"), LaneMode.PROD, 0xFF8A4EB8L, "pīnyīn", space = false, primer = true, locale = "zh-CN"),
            info("ar", "Arabic", Family.SEMITIC, Script.ARABIC, emptyList(), LaneMode.PROD, 0xFF3E7DA6L, "romanised", rtl = true, primer = true, locale = "ar-SA"),
        ),
    )

    fun tok(surface: String, zipf: Double, lemma: String = surface.lowercase(), bridges: List<Bridge> = emptyList(), reading: String? = null) =
        Token(surface, lemma, zipf, bridges, reading)

    fun scene(
        i: Int, m: String, x: Map<String, String>, k: Map<String, List<Token>>,
        e: Int = 1, p: String = m, r: Rect = Rect(0.1, 0.1, 0.5, 0.1), q: Map<String, Rect> = emptyMap(),
    ) = Scene(i = i, e = e, p = p, m = m, r = r, q = q.mapKeys { Lang(it.key) }, x = x.mapKeys { Lang(it.key) }, k = k.mapKeys { Lang(it.key) })

    fun lex(count: Int, zipf: Double, surface: String, reading: String? = null) = LexEntry(count, zipf, surface, reading)

    // ----- the synthetic corpus: 5 scenes, lanes es / fr / ja (+ en) -----
    val hola = tok("Hola", 5.0); val mundo = tok("mundo", 5.5)
    val el = tok("El", 7.5); val gato = tok("gato", 4.2); val come = tok("come", 5.0, lemma = "comer")
    val la = tok("La", 7.6)
    val pocion = tok("poción", 2.9, bridges = listOf(Bridge(EN, "Potion", 0.83), Bridge(FR, "Potion", 0.83)))
    val de = tok("de", 7.8); val vuelo = tok("vuelo", 4.6); val duerme = tok("duerme", 4.0, lemma = "dormir")
    val bonjour = tok("Bonjour", 5.2); val monde = tok("monde", 5.6); val le = tok("Le", 7.6); val chat = tok("chat", 4.5)
    val mange = tok("mange", 5.1, lemma = "manger"); val laF = tok("La", 7.6)
    val potion = tok("potion", 3.0, bridges = listOf(Bridge(ES, "Poción", 0.83))); val envol = tok("envol", 3.2)
    val dort = tok("dort", 4.3, lemma = "dormir")
    val konn = tok("こんにちは", 5.0, reading = "konnichiwa"); val sekai = tok("世界", 5.4, reading = "sekai")
    val neko = tok("猫", 4.4, reading = "neko"); val ga = tok("が", 7.0, reading = "ga"); val taberu = tok("食べる", 5.0, reading = "taberu")
    val wa = tok("は", 7.2, reading = "wa"); val nemuru = tok("眠る", 4.1, reading = "nemuru")

    fun miniScenes(): List<Scene> = listOf(
        scene(0, "P0", mapOf("en" to "Hello world", "es" to "Hola mundo", "fr" to "Bonjour monde", "ja" to "こんにちは 世界"),
            mapOf("en" to listOf(tok("Hello", 6.0), tok("world", 6.0)), "es" to listOf(hola, mundo), "fr" to listOf(bonjour, monde), "ja" to listOf(konn, sekai)),
            q = mapOf("es" to Rect(0.1, 0.1, 0.4, 0.1), "ja" to Rect(0.1, 0.1, 0.03, 0.1))),
        scene(1, "P1", mapOf("en" to "The cat eats", "es" to "El gato come", "fr" to "Le chat mange", "ja" to "猫 が 食べる"),
            mapOf("en" to listOf(tok("The", 7.0), tok("cat", 5.0), tok("eats", 4.0)), "es" to listOf(el, gato, come), "fr" to listOf(le, chat, mange), "ja" to listOf(neko, ga, taberu)),
            r = Rect(0.1, 0.1, 0.5, 0.1)),
        scene(2, "P1", mapOf("en" to "The potion of flight", "es" to "La poción de vuelo", "fr" to "La potion d'envol"),
            mapOf("en" to listOf(tok("The", 7.0), tok("potion", 3.5)), "es" to listOf(la, pocion, de, vuelo), "fr" to listOf(laF, potion, envol)),
            r = Rect(0.1, 0.5, 0.5, 0.1)),
        scene(3, "P2", mapOf("en" to "The cat sleeps", "es" to "El gato duerme", "fr" to "Le chat dort", "ja" to "猫 は 眠る"),
            mapOf("en" to listOf(tok("The", 7.0), tok("cat", 5.0)), "es" to listOf(el, gato, duerme), "fr" to listOf(le, chat, dort), "ja" to listOf(neko, wa, nemuru)),
            e = 2, r = Rect(0.1, 0.1, 0.5, 0.1)),
        scene(4, "P2", mapOf("en" to "Hello", "es" to "Hola", "fr" to "Bonjour", "ja" to "こんにちは"),
            mapOf("en" to listOf(tok("Hello", 6.0)), "es" to listOf(hola), "fr" to listOf(bonjour), "ja" to listOf(konn)),
            e = 2, r = Rect(0.6, 0.1, 0.3, 0.1)),
    )

    fun miniLex(): Map<Lang, Map<String, LexEntry>> = mapOf(
        ES to linkedMapOf(
            "hola" to lex(10, 5.0, "Hola"), "mundo" to lex(5, 5.5, "mundo"), "el" to lex(20, 7.5, "El"), "gato" to lex(3, 4.2, "gato"),
            "comer" to lex(2, 5.0, "come"), "la" to lex(9, 7.6, "La"), "poción" to lex(1, 2.9, "Poción"), "de" to lex(30, 7.8, "de"),
            "vuelo" to lex(1, 4.6, "vuelo"), "dormir" to lex(1, 4.0, "duerme"), "pato" to lex(1, 4.3, "pato"), "gata" to lex(1, 4.0, "gata"),
            "ratón" to lex(1, 4.1, "ratón"), "perro" to lex(2, 4.4, "perro"),
        ),
        FR to linkedMapOf(
            "bonjour" to lex(4, 5.2, "Bonjour"), "monde" to lex(2, 5.6, "monde"), "le" to lex(9, 7.6, "Le"), "chat" to lex(2, 4.5, "chat"),
            "manger" to lex(1, 5.1, "mange"), "la" to lex(3, 7.6, "La"), "potion" to lex(1, 3.0, "potion"), "envol" to lex(1, 3.2, "envol"),
            "dormir" to lex(1, 4.3, "dort"),
        ),
        JA to linkedMapOf(
            "こんにちは" to lex(2, 5.0, "こんにちは", "konnichiwa"), "世界" to lex(1, 5.4, "世界", "sekai"), "猫" to lex(2, 4.4, "猫", "neko"),
            "が" to lex(1, 7.0, "が", "ga"), "食べる" to lex(1, 5.0, "食べる", "taberu"), "は" to lex(1, 7.2, "は", "wa"), "眠る" to lex(1, 4.1, "眠る", "nemuru"),
        ),
    )

    val PSEUDO_ES = listOf("apltor", "beltrar", "bienveir", "epsrrada", "grlae", "guntánea", "lugnte", "podrdadero", "wilpleto", "wrigoma")

    fun miniEtym(): Map<Lang, Map<String, EtymEntry>> = mapOf(
        ES to mapOf(
            "poción" to EtymEntry(origin = "Latin potione", originType = "bor"),
            "vuelo" to EtymEntry(origin = "Latin volāre", originType = "inh", edges = listOf(EtymEdge(IT, "volare", EdgeType.COGNATE))),
            "gato" to EtymEntry(origin = "Latin cattus", originType = "inh"),
        ),
    )

    fun miniPages(): Map<String, PageInfo> = mapOf("P0" to PageInfo(640, 900, "p0.webp"), "P1" to PageInfo(640, 900, "p1.webp"), "P2" to PageInfo(640, 900, "p2.webp"))

    fun mini(): Corpus = Corpus.index(miniScenes(), miniLex(), miniEtym(), mapOf(ES to PSEUDO_ES), miniPages())

    fun state(now: Long = T0): ClavisState = freshState(registry, now)

    fun engine(
        corpus: Corpus = mini(), state: ClavisState = state(), random: Random = Random(1), now: Long = T0,
        dayKey: DayKey = DayKey { "2025-9-2" },
    ): ClavisEngine = ClavisEngine(corpus, registry, state, Clock { now }, random, dayKey)

    /** A rated card that is currently known (R ≈ 0.99): s = 10 days, last review one day ago. */
    fun knownCard(now: Long = T0, s: Double = 10.0, g: Int = 3, reps: Int = 1, daysAgo: Double = 1.0, due: Long = now + 9 * DAY_MS) =
        Card(s = s, d = 5.0, due = due, last = now - (daysAgo * DAY_MS).toLong(), reps = reps, lapses = 0, g = g, first = now - (daysAgo * DAY_MS).toLong())

    fun tested(state: ClavisState, l: Lang, thr: Double, now: Long = T0) {
        state.tests[l.code] = TestResult(done = now, thr = thr, fa = 0.0, bands = emptyList(), ms = 1000)
    }
}

/**
 * A [Random] whose `nextDouble()` / `nextInt(until)` are served from [script] first, then from
 * [default] when set (every later draw returns it), else from a seeded fallback.
 */
class ScriptedRandom(script: List<Double> = emptyList(), private val default: Double? = null, seed: Int = 7) : Random() {
    constructor(vararg script: Double) : this(script.toList())
    private val q = ArrayDeque(script)
    private val fb = Random(seed)
    val drawn: MutableList<Double> = mutableListOf()
    override fun nextBits(bitCount: Int): Int = fb.nextBits(bitCount)
    override fun nextDouble(): Double {
        val v = q.removeFirstOrNull() ?: default ?: fb.nextDouble()
        drawn.add(v)
        return v
    }
    override fun nextInt(until: Int): Int {
        val v = q.removeFirstOrNull() ?: default
        return if (v != null) { drawn.add(v); (v * until).toInt().coerceIn(0, until - 1) } else fb.nextInt(until)
    }
    override fun nextInt(from: Int, until: Int): Int = from + nextInt(until - from)
}
