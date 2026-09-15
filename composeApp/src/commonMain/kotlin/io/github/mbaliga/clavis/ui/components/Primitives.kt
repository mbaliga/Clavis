package io.github.mbaliga.clavis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/** `.card`: surface, 14 dp radius, hairline, the soft two-layer shadow, 16 dp padding, 12 dp vertical margin. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = ClavisTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .shadow(elevation = 1.dp, shape = ClavisShapes.card, ambientColor = p.shadowAmbient, spotColor = p.shadowKey)
            .background(p.surface, ClavisShapes.card)
            .border(1.dp, p.line, ClavisShapes.card)
            .padding(padding),
        content = content,
    )
}

/** `.eyebrow`: mono, upper-case, letter-spaced, quiet. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = ClavisTheme.type.eyebrow, color = ClavisTheme.palette.ink3, modifier = modifier.padding(bottom = 6.dp))
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = ClavisTheme.palette.ink, style: TextStyle = ClavisTheme.type.body) {
    Text(text, style = style, color = color, modifier = modifier)
}

@Composable
fun SmallText(text: String, modifier: Modifier = Modifier, color: Color = ClavisTheme.palette.ink2) {
    Text(text, style = ClavisTheme.type.small, color = color, modifier = modifier)
}

@Composable
fun H2(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ClavisTheme.type.h2, color = ClavisTheme.palette.ink, modifier = modifier)
}

@Composable
fun H3(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ClavisTheme.type.h3, color = ClavisTheme.palette.ink, modifier = modifier)
}

/** The charcoal primary button (`--btn-primary`), 48 dp tall. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val p = ClavisTheme.palette
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp, minWidth = 88.dp)
            .clip(ClavisShapes.button)
            .background(if (enabled) p.btnPrimary else p.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (testTag != null) Modifier.tagged(testTag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ClavisTheme.type.button, color = if (enabled) p.btnPrimaryInk else p.ink3, textAlign = TextAlign.Center)
    }
}

/** The quiet bordered button (`button` default styling): surface, hairline, 48 dp. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    minHeight: Dp = 48.dp,
    testTag: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val p = ClavisTheme.palette
    Row(
        modifier
            .defaultMinSize(minHeight = minHeight, minWidth = 48.dp)
            .clip(ClavisShapes.button)
            .background(if (selected) p.accentSoft else p.surface)
            .border(1.dp, if (selected) p.accentSoft else p.line, ClavisShapes.button)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (testTag != null) Modifier.tagged(testTag) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
        Text(text, style = ClavisTheme.type.quietButton, color = if (selected) p.accentInk else if (enabled) p.ink else p.ink3, textAlign = TextAlign.Center)
    }
}

/** Text-only link-like button (`.link`/quiet text actions such as `See the whole page`). */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, testTag: String? = null, contentDescription: String? = null) {
    val p = ClavisTheme.palette
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(ClavisShapes.button)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (testTag != null) Modifier.tagged(testTag) else Modifier)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = ClavisTheme.type.quietButton, color = p.ink2)
    }
}

/** `.chip`: pill, mono, quiet. */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val p = ClavisTheme.palette
    Box(
        modifier
            .clip(ClavisShapes.chip)
            .background(if (selected) p.accentSoft else p.surface2)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick).defaultMinSize(minHeight = 48.dp) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ClavisTheme.type.chip, color = if (selected) p.accentInk else p.ink2)
    }
}

/** `.seg`: a segmented control with radio-group semantics (product-spec §8.4). */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    groupLabel: String? = null,
    testTagPrefix: String? = null,
) {
    val p = ClavisTheme.palette
    Row(
        modifier
            .clip(ClavisShapes.button)
            .border(1.dp, p.line, ClavisShapes.button)
            .background(p.surface)
            .selectableGroup()
            .then(if (groupLabel != null) Modifier.semantics { contentDescription = groupLabel } else Modifier),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .defaultMinSize(minHeight = 44.dp, minWidth = 52.dp)
                    .background(if (on) p.accentSoft else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(i) })
                    .then(if (testTagPrefix != null) Modifier.tagged("$testTagPrefix-$label") else Modifier)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = ClavisTheme.type.quietButton, color = if (on) p.accentInk else p.ink2)
            }
        }
    }
}

/** A lane-coloured dot, always paired with a name by its callers (colour is never the sole carrier). */
@Composable
fun LaneDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** `.progressbar`: 6 dp track with a lane-coloured fill; width `max(1, v*100)%`. */
@Composable
fun ProgressBar(fraction: Double, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val p = ClavisTheme.palette
    val f = fraction.coerceIn(0.0, 1.0).toFloat().coerceAtLeast(0.01f)
    Box(modifier.fillMaxWidth().height(height).clip(ClavisShapes.chip).background(p.surface2)) {
        Box(Modifier.fillMaxWidth(f).height(height).clip(ClavisShapes.chip).background(color))
    }
}

/** `.stat`: a big Rozha number with a quiet label. */
@Composable
fun RowScope.StatTile(value: String, label: String) {
    val p = ClavisTheme.palette
    Column(
        Modifier.weight(1f).background(p.surface2, ClavisShapes.stat).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, style = ClavisTheme.type.stat, color = p.ink)
        Text(label, style = ClavisTheme.type.tiny, color = p.ink2)
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(ClavisTheme.palette.line2))
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

/**
 * Arrow glyph for a "step back"/"previous" chrome label (e.g. `"${backArrow()} Page"`), resolved
 * from the ambient layout direction so it keeps pointing toward the start edge — not its original
 * physical direction — when Compose auto-mirrors the surrounding Row under RTL (rtl audit: a
 * literal `←`/`→` character baked into a button's text never mirrors on its own).
 */
@Composable
fun backArrow(): String = if (LocalLayoutDirection.current == LayoutDirection.Ltr) "←" else "→"

/** The `→`/`←` counterpart of [backArrow], for a "step forward"/"next" chrome label. */
@Composable
fun forwardArrow(): String = if (LocalLayoutDirection.current == LayoutDirection.Ltr) "→" else "←"
