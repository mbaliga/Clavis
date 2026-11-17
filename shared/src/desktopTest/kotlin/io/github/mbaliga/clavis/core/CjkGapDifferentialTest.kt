package io.github.mbaliga.clavis.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `stripCjkGaps` replaced a JVM-only regex (`\p{IsHan}` etc. does not exist on Kotlin/Native). This pins the
 * replacement to the original over random strings built from the interesting classes. Where the original's script
 * classes are wider than the block ranges chosen (rare Han/Katakana extensions), those code points are left out of
 * the alphabet on purpose; they are listed in the doc comment of `isCjkCodePoint`.
 */
class CjkGapDifferentialTest {
    private val old = Regex("(?<=[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\u3000-\\u303F\\uFF00-\\uFFEF])\\s+(?=[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\u3000-\\u303F\\uFF00-\\uFFEF])")
    private val alphabet = listOf(
        "猫", "世", "界", "你", "好", "あ", "ん", "ア", "ン", "ー", "。", "、", "「", "　", "Ａ", "！", "a", "Z", "é", "1", "-",
        " ", " ", "  ", "\t", "\n", "\r\n", "\u000B", "\u000C", " ", " ",
    )

    @Test fun matchesTheOriginalRegex() {
        val r = Random(42)
        repeat(20_000) {
            val s = buildString { repeat(r.nextInt(0, 12)) { append(alphabet[r.nextInt(alphabet.size)]) } }
            assertEquals(s.replace(old, ""), stripCjkGaps(s), "input: ${s.map { it.code.toString(16) }}")
        }
    }

    @Test fun classifiesEveryCodePointLikeTheOriginalClass() {
        // The table was generated on a newer JDK than the one a given test run may use, so the original regex can
        // lag by a Unicode version: code points it does not know yet (unassigned on this JDK) may be CJK in the table.
        val cls = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\u3000-\\u303F\\uFF00-\\uFFEF]")
        var newerOnly = 0
        for (cp in 0x100..0x10FFFF) {
            if (cp in 0xD800..0xDFFF) continue
            val was = cls.matches(String(Character.toChars(cp)))
            val now = isCjkCodePoint(cp)
            if (was && !now) error("U+${cp.toString(16)} was CJK in the regex and is missing from the table")
            if (now && !was) {
                // only newer-Unicode additions or script reassignments; none below the last Unicode-13 BMP Han block
                assertEquals(true, cp >= 0x9FFD, "U+${cp.toString(16)} is in the table but not in this JDK's regex")
                newerOnly++
            }
        }
        println("CJK table vs JDK ${System.getProperty("java.version")} regex: $newerOnly code points the table adds beyond this JDK's Unicode")
    }
}
