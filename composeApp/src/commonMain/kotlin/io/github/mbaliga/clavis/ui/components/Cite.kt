package io.github.mbaliga.clavis.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

private val CITE = Regex("\\[(\\d{1,3})]")

/**
 * The citation mechanism (template-spec §13, R-8): every `[n]` in a copy string becomes a real,
 * focusable link — superscript, `accent-ink`, no underline — that opens the colophon at
 * reference n. Copy strings from `Copy.kt` carry their citations inline, so all of them render
 * through this.
 */
@Composable
fun citedString(text: String, onCite: (Int) -> Unit, bold: Set<String> = emptySet()): AnnotatedString {
    val p = ClavisTheme.palette
    return remember(text, p, bold) {
        buildAnnotatedString {
            var last = 0
            for (m in CITE.findAll(text)) {
                appendWithBold(text.substring(last, m.range.first), bold)
                val n = m.groupValues[1].toInt()
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "ref-$n",
                        styles = TextLinkStyles(style = SpanStyle(color = p.accentInk, fontSize = 0.72.em, baselineShift = BaselineShift(0.28f))),
                        linkInteractionListener = { onCite(n) },
                    ),
                ) { append("[$n]") }
                last = m.range.last + 1
            }
            appendWithBold(text.substring(last), bold)
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendWithBold(s: String, bold: Set<String>) {
    if (bold.isEmpty()) { append(s); return }
    var i = 0
    while (i < s.length) {
        val hit = bold.mapNotNull { w -> val at = s.indexOf(w, i); if (at >= 0) at to w else null }.minByOrNull { it.first }
        if (hit == null) { append(s.substring(i)); return }
        append(s.substring(i, hit.first))
        withStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) { append(hit.second) }
        i = hit.first + hit.second.length
    }
}

/** Body copy with inline `[n]` citations. */
@Composable
fun CitedText(
    text: String,
    onCite: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = ClavisTheme.type.body,
    color: Color = ClavisTheme.palette.ink,
    bold: Set<String> = emptySet(),
) {
    Text(citedString(text, onCite, bold), style = style, color = color, modifier = modifier)
}

/** A standalone `[n]` button (for claims lists and legends): accessible as `reference n`. */
@Composable
fun Cite(n: Int, onCite: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = ClavisTheme.palette
    Box(
        modifier
            .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
            .clickable(role = Role.Button) { onCite(n) }
            .semantics { contentDescription = "reference $n" }
            .padding(horizontal = 3.dp, vertical = 4.dp),
    ) {
        Text("[$n]", style = ClavisTheme.type.tiny, color = p.accentInk)
    }
}
