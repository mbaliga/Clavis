package io.github.mbaliga.clavis.core

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelpersTest {
    // Expected values produced by the template's own hash01 in Node 22 (see brief: FNV-1a, Math.imul, >>>0, %1000).
    @Test fun hash01MatchesJsForAscii() {
        assertEquals(0.261, hash01(""))
        assertEquals(0.065, hash01("es"))
        assertEquals(0.562, hash01("esvuelo"))
        assertEquals(0.374, hash01("vueloes"))
        assertEquals(0.215, hash01("dropar"))
        assertEquals(0.491, hash01("vnl"))
        assertEquals(0.85, hash01("ar"))
        assertEquals(0.756, hash01("casay"))
    }

    @Test fun hash01MatchesJsForCjkAndSurrogatePairs() {
        assertEquals(0.523, hash01("ja飛行"))
        assertEquals(0.218, hash01("🙂x")) // the emoji is two UTF-16 code units, hashed separately like charCodeAt
    }

    @Test fun hash01StaysInUnitInterval() {
        for (s in listOf("a", "zz", "poción", "世界", "x".repeat(500))) { val h = hash01(s); assertTrue(h >= 0.0 && h < 1.0, s) }
    }

    @Test fun bandLabelPicksTheBandContainingZ() {
        assertEquals("the ~500 commonest words", bandLabel(6.0))
        assertEquals("the ~500 commonest words", bandLabel(8.99))
        assertEquals("the top ~2,000", bandLabel(5.99))
        assertEquals("the top ~10,000", bandLabel(4.0))
        assertEquals("the top ~50,000", bandLabel(3.5))
        assertEquals("rare words", bandLabel(2.0))
    }

    @Test fun bandLabelOutsideBandsIsNothingYet() {
        assertEquals("nothing yet", bandLabel(9.0))
        assertEquals("nothing yet", bandLabel(1.99))
        assertEquals("nothing yet", bandLabel(0.0))
    }

    @Test fun jsRoundRoundsHalvesTowardPositiveInfinity() {
        assertEquals(3L, jsRound(2.5)); assertEquals(-2L, jsRound(-2.5)); assertEquals(0L, jsRound(0.49)); assertEquals(1L, jsRound(0.5))
    }

    @Test fun pctFormatsLikeTheTemplate() {
        assertEquals("96%", pct(0.955)); assertEquals("0%", pct(0.004)); assertEquals("100%", pct(1.0)); assertEquals("50%", pct(0.5))
    }

    @Test fun pageOfReturnsSiblingsInReadingOrderFilteredByLanguage() {
        val c = Fx.mini()
        val p1 = pageOf(c, c.scene(2), Fx.ES).map { it.i }
        assertEquals(listOf(1, 2), p1) // sc1 row 1 (y .1), sc2 row 6 (y .5)
        val p2 = pageOf(c, c.scene(4), Fx.JA).map { it.i }
        assertEquals(listOf(3, 4), p2) // same row, ordered by x
        assertEquals(listOf(1), pageOf(c, c.scene(1), Fx.JA).map { it.i }) // sc2 has no Japanese line
    }

    @Test fun tokenJoinUsesSpacesUnlessTheScriptIsUnspaced() {
        assertEquals("Hola mundo", tokenJoin(Fx.registry, Fx.ES, listOf(Fx.hola, Fx.mundo)))
        assertEquals("こんにちは世界", tokenJoin(Fx.registry, Fx.JA, listOf(Fx.konn, Fx.sekai)))
        assertEquals("猫 が", tokenJoin(Fx.registry, Fx.KO, listOf(Fx.neko, Fx.ga))) // Korean is spaced
        assertEquals("a b", tokenJoin(Fx.registry, Lang("xx"), listOf(Fx.tok("a", 1.0), Fx.tok("b", 1.0))))
    }

    @Test fun lineOfStripsSpacesBetweenCjkCharactersOnlyForJaAndZh() {
        assertEquals("こんにちは世界", lineOf(Fx.JA, "こんにちは 世界"))
        assertEquals("猫は眠る", lineOf(Fx.JA, "猫 は  眠る"))
        assertEquals("你好世界", lineOf(Fx.ZH, "你好 世界"))
        assertEquals("猫 cat 猫", lineOf(Fx.JA, "猫 cat 猫")) // Latin in between: untouched
        assertEquals("こんにちは 世界", lineOf(Fx.KO, "こんにちは 世界")) // not applied to other lanes
        assertEquals("Hola mundo", lineOf(Fx.ES, "Hola mundo"))
    }

    @Test fun maskContractNullEmptyOrGapped() {
        val sc = Fx.mini().scene(1) // "El gato come"
        assertNull(Mask.forCloze(sc, Fx.ES, "gato", revealed = true))
        assertEquals("El ____ come", Mask.forCloze(sc, Fx.ES, "gato", revealed = false))
        assertEquals("", Mask.forCloze(sc, Fx.ES, "perro", revealed = false)) // lemma not in the balloon
        assertEquals("", Mask.forCloze(sc, Fx.JA, "gato", revealed = false)) // lemma not in that language
    }

    @Test fun maskReplacesOnlyTheFirstOccurrenceOfTheSurface() {
        val sc = Fx.scene(9, "P9", mapOf("en" to "x", "es" to "el perro y el gato"),
            mapOf("es" to listOf(Fx.tok("el", 7.5), Fx.tok("perro", 4.4), Fx.tok("y", 7.0), Fx.tok("el", 7.5), Fx.tok("gato", 4.2))))
        assertEquals("____ perro y el gato", Mask.forCloze(sc, Fx.ES, "el", revealed = false))
        // surface missing from the line (case differs) → '' : do not letter the balloon
        val sc2 = Fx.scene(8, "P8", mapOf("en" to "x", "es" to "EL GATO"), mapOf("es" to listOf(Fx.tok("el", 7.5), Fx.tok("gato", 4.2))))
        assertEquals("", Mask.forCloze(sc2, Fx.ES, "gato", revealed = false))
    }

    @Test fun rectOkBoundsAndRectForFallback() {
        assertTrue(Lettering.rectOK(Rect(0.0, 0.0, 0.045, 0.01)))
        assertTrue(Lettering.rectOK(Rect(0.0, 0.0, 0.70, 0.45)))
        assertFalse(Lettering.rectOK(Rect(0.0, 0.0, 0.044, 0.1)))
        assertFalse(Lettering.rectOK(Rect(0.0, 0.0, 0.71, 0.1)))
        assertFalse(Lettering.rectOK(Rect(0.0, 0.0, 0.2, 0.009)))
        assertFalse(Lettering.rectOK(Rect(0.0, 0.0, 0.2, 0.46)))
        assertFalse(Lettering.rectOK(null))
        val sc = Fx.mini().scene(0)
        assertEquals(Rect(0.1, 0.1, 0.4, 0.1), Lettering.rectFor(sc, Fx.ES))
        assertEquals(sc.r, Lettering.rectFor(sc, Fx.FR)) // no per-language box → shared rect
        assertFalse(Lettering.rectOK(Lettering.rectFor(sc, Fx.JA))) // too narrow → marker only
    }

    @Test fun localDayKeyIsUnpaddedLocalDate() {
        val utc = LocalDayKey { TimeZone.UTC }
        assertEquals("2025-9-2", utc.today(Fx.T0))
        // 2025-09-02T23:30Z is already Sept 3 in Kolkata (+05:30)
        val kolkata = LocalDayKey { TimeZone.of("Asia/Kolkata") }
        assertEquals("2025-9-3", kolkata.today(Fx.T0 + 15 * Fx.HOUR + 30 * 60_000L))
        assertEquals("2025-9-2", utc.today(Fx.T0 + 15 * Fx.HOUR + 30 * 60_000L))
        assertEquals("2025-12-25", utc.today(Fx.T0 + 114 * DAY_MS))
    }

    @Test fun freshStateHasTheDocumentedDefaults() {
        val s = freshState(Fx.registry, Fx.T0)
        assertEquals(Fx.T0, s.created)
        assertEquals(LaneMode.PROD, s.lanes["es"]); assertEquals(LaneMode.REC, s.lanes["fr"]); assertEquals(LaneMode.PROD, s.lanes["ar"])
        assertEquals(9, s.lanes.size); assertEquals(9, s.cards.size); assertEquals(9, s.bandit.size); assertEquals(9, s.inter.size); assertEquals(9, s.routes.size)
        assertEquals(0, s.newToday["ja"]); assertEquals(0.2, s.bandit["es"]!!.w); assertTrue(s.bandit["es"]!!.hist.isEmpty())
        assertTrue(s.tests.isEmpty()); assertTrue(s.seen.isEmpty()); assertTrue(s.log.isEmpty())
        assertEquals(0.9, s.settings.retention); assertEquals(10, s.settings.newPerDay); assertEquals(5, s.settings.sessionSize)
        assertTrue(s.settings.readings); assertFalse(s.settings.monoSession); assertTrue(s.settings.motion)
        assertEquals("", s.newDay); assertNull(s.pin); assertEquals("learn", s.view); assertFalse(s.onboarded); assertEquals(0, s.ob)
        assertTrue(s.coach.isEmpty()); assertNull(s.intent)
    }

    @Test fun freshStateTurnsOffLanguagesNotEnabledByDefault() {
        val extra = LanguageInfo(Lang("pt"), "Portuguese", "Português", Family.ROMANCE, Script.LATIN, defaultLane = LaneMode.REC, ttsLocale = "pt-PT", colour = 1L)
        val reg = io.github.mbaliga.clavis.core.data.ListLanguageRegistry(Fx.registry.all + extra)
        val s = freshState(reg, Fx.T0)
        assertEquals(LaneMode.OFF, s.lanes["pt"]); assertEquals(0, s.newToday["pt"]); assertTrue(s.cards["pt"]!!.isEmpty())
    }

    @Test fun copyPluralisesExactlyLikeTheTemplate() {
        assertEquals("1 word ready to return", Copy.readyToReturn(1))
        assertEquals("3 words ready to return", Copy.readyToReturn(3))
        assertEquals("1 more bubble becomes readable", Copy.newThen(1))
        assertEquals("4 more bubbles become readable", Copy.newThen(4))
        assertEquals("the next bubbles get easier", Copy.newThen(0))
        assertEquals("Held. It returns in about 1 day.", Copy.clozeHeld(1))
        assertEquals("Held. It returns in about 12 days.", Copy.clozeHeld(12))
        assertEquals("1 word here is probably new; the rest you know (50% of the bubble).", Copy.readCountCopy(1, 0.5))
        assertEquals("2 words here are probably new; the rest you know (67% of the bubble).", Copy.readCountCopy(2, 2.0 / 3))
        assertEquals("Bring back 1 Spanish word", Copy.dueTitle(1, "Spanish")); assertEquals("Meet 2 new Japanese words", Copy.newTitle(2, "Japanese"))
        assertEquals("Spanish: Clavis now assumes you know the top ~2,000", Copy.testAssumes("Spanish", bandLabel(5.0)))
        assertEquals("gato — you met it 1 day ago · grew from Latin cattus. That feeling of almost-knowing is real memory — glimpsed words are relearned faster than new ones [14][15][35].",
            Copy.echoResult("gato", "", 1, "grew from Latin cattus"))
        assertEquals("猫 (neko) — you met it earlier. That feeling of almost-knowing is real memory — glimpsed words are relearned faster than new ones [14][15][35].",
            Copy.echoResult("猫", "neko", null, null))
    }
}
