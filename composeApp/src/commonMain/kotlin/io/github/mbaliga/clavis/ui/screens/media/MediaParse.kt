package io.github.mbaliga.clavis.ui.screens.media

import io.github.mbaliga.clavis.core.Corpus
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LanguageRegistry
import io.github.mbaliga.clavis.core.LexEntry
import io.github.mbaliga.clavis.core.Script

/**
 * Pure parsing for user media (D-7 `user-media`, product-spec §5.6): `.srt` (indices and
 * timestamps stripped, cue lines joined), `.lrc` (`[mm:ss.xx]` tags and metadata stripped), `.txt`
 * (lines); plus the two tokenisers the reading mode uses — whitespace/punctuation for
 * space-separated languages and longest-match against the lexicon's surface forms for ja/zh
 * (v1.0 has no on-device CJK segmenter; the UI says "approximate").
 */
object MediaParse {
    private val TIMESTAMP = Regex("""\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}.*""")
    private val LRC_TAG = Regex("""^(\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?])+""")
    private val LRC_META = Regex("""^\[[a-zA-Z]+:.*]$""")
    private val TAGS = Regex("""<[^>]{1,20}>|\{\\[^}]*}""")
    private val WORD = Regex("""[\p{L}\p{M}\p{N}][\p{L}\p{M}\p{N}'’\-]*""")

    fun kindOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) { "srt" -> "srt"; "lrc" -> "lrc"; else -> "txt" }

    fun parse(name: String, text: String): Pair<String, List<String>> = when (val k = kindOf(name)) {
        "srt" -> k to parseSrt(text)
        "lrc" -> k to parseLrc(text)
        else -> k to parseTxt(text)
    }

    fun parseTxt(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun parseSrt(text: String): List<String> {
        val out = ArrayList<String>()
        val cue = ArrayList<String>()
        fun flush() { if (cue.isNotEmpty()) { out.add(cue.joinToString(" ")); cue.clear() } }
        for (raw in text.lines()) {
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) { flush(); continue }
            if (line.all { it.isDigit() } && cue.isEmpty()) continue // cue index
            if (TIMESTAMP.matches(line)) continue
            val clean = line.replace(TAGS, "").trim()
            if (clean.isNotEmpty()) cue.add(clean)
        }
        flush()
        return out
    }

    fun parseLrc(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.lines()) {
            val line = raw.trim().removePrefix("﻿")
            if (line.isEmpty()) continue
            if (LRC_META.matches(line)) continue
            val stripped = line.replace(LRC_TAG, "").trim()
            if (stripped.isNotEmpty()) out.add(stripped)
        }
        return out
    }

    /** A piece of a line: a word (looked up in the lexicon) or in-between text. */
    data class Piece(val text: String, val word: Boolean)

    fun tokeniseSpaced(line: String): List<Piece> {
        val out = ArrayList<Piece>()
        var last = 0
        for (m in WORD.findAll(line)) {
            if (m.range.first > last) out.add(Piece(line.substring(last, m.range.first), false))
            out.add(Piece(m.value, true))
            last = m.range.last + 1
        }
        if (last < line.length) out.add(Piece(line.substring(last), false))
        return out
    }

    /** Longest-match over [surfaces] (max [maxLen] chars); unmatched runs become non-word pieces. */
    fun tokeniseLongest(line: String, surfaces: Set<String>, maxLen: Int = 8): List<Piece> {
        val out = ArrayList<Piece>()
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            var hit: String? = null
            var len = minOf(maxLen, line.length - i)
            while (len >= 1) {
                val cand = line.substring(i, i + len)
                if (cand in surfaces) { hit = cand; break }
                len--
            }
            if (hit != null) {
                if (sb.isNotEmpty()) { out.add(Piece(sb.toString(), false)); sb.clear() }
                out.add(Piece(hit, true)); i += hit.length
            } else { sb.append(line[i]); i++ }
        }
        if (sb.isNotEmpty()) out.add(Piece(sb.toString(), false))
        return out
    }

    /** Surface/lemma → lexicon entry for one language, lower-cased, built once. */
    class LexIndex(lex: Map<String, LexEntry>) {
        val byForm: Map<String, Pair<String, LexEntry>>
        val surfaces: Set<String>
        init {
            val m = HashMap<String, Pair<String, LexEntry>>()
            for ((lm, e) in lex) {
                m.putIfAbsent(lm.lowercase(), lm to e)
                if (e.surface.isNotEmpty()) m.putIfAbsent(e.surface.lowercase(), lm to e)
            }
            byForm = m
            surfaces = m.keys
        }
        fun lookup(form: String): Pair<String, LexEntry>? = byForm[form.lowercase()] ?: byForm[form.lowercase().trim('\'', '’', '-')]
    }

    fun tokenise(line: String, spaceSeparated: Boolean, index: LexIndex): List<Piece> =
        if (spaceSeparated) tokeniseSpaced(line) else tokeniseLongest(line, index.surfaces)

    /** Script sniff → the most plausible active lane (confirmable in the UI). */
    fun guessLang(lines: List<String>, active: List<Lang>, registry: LanguageRegistry, corpus: Corpus): Lang? {
        val sample = lines.take(40).joinToString(" ")
        var han = 0; var kana = 0; var hangul = 0; var arabic = 0; var cyr = 0; var deva = 0
        for (ch in sample) {
            when (ch) {
                in '一'..'鿿' -> han++
                in '぀'..'ヿ' -> kana++
                in '가'..'힯' -> hangul++
                in '؀'..'ۿ' -> arabic++
                in 'Ѐ'..'ӿ' -> cyr++
                in 'ऀ'..'ॿ' -> deva++
            }
        }
        fun firstActive(pred: (Lang) -> Boolean) = active.firstOrNull(pred)
        val script = when {
            kana > 0 -> return firstActive { it.code == "ja" } ?: Lang("ja")
            hangul > 0 -> return firstActive { it.code == "ko" } ?: Lang("ko")
            han > 0 -> return firstActive { it.code == "zh" } ?: firstActive { it.code == "ja" } ?: Lang("zh")
            arabic > 0 -> Script.ARABIC
            cyr > 0 -> Script.CYRILLIC
            deva > 0 -> Script.DEVANAGARI
            else -> Script.LATIN
        }
        val candidates = active.filter { registry[it]?.script == script }
        if (candidates.size <= 1) return candidates.firstOrNull() ?: active.firstOrNull()
        // several Latin/Cyrillic lanes: the one whose lexicon knows most of the words wins
        val words = tokeniseSpaced(sample).filter { it.word }.map { it.text.lowercase() }.take(200)
        return candidates.maxByOrNull { l -> val lex = corpus.lex[l].orEmpty(); words.count { w -> lex.containsKey(w) || lex.values.any { false } } }
    }
}
