package io.github.mbaliga.clavis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/**
 * `.speak`: a 44 dp circular button labelled `Hear it`. Callers render it only when the
 * platform can voice the language (`speaker.canSpeak`, D-8) — never a button that fails silently.
 */
@Composable
fun SpeakButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, testTag: String? = null) {
    val p = ClavisTheme.palette
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(p.surface)
            .border(1.dp, p.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = Copy.HEAR_IT }
            .then(if (testTag != null) Modifier.tagged(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.5f)) {
            val w = this.size.width
            val h = this.size.height
            val ink = p.ink2
            val body = Path().apply {
                moveTo(w * 0.12f, h * 0.38f); lineTo(w * 0.32f, h * 0.38f); lineTo(w * 0.52f, h * 0.2f)
                lineTo(w * 0.52f, h * 0.8f); lineTo(w * 0.32f, h * 0.62f); lineTo(w * 0.12f, h * 0.62f); close()
            }
            drawPath(body, ink)
            drawArc(ink, -40f, 80f, false, topLeft = Offset(w * 0.42f, h * 0.28f), size = Size(w * 0.36f, h * 0.44f), style = Stroke(width = w * 0.07f))
            drawArc(ink, -45f, 90f, false, topLeft = Offset(w * 0.34f, h * 0.12f), size = Size(w * 0.6f, h * 0.76f), style = Stroke(width = w * 0.07f))
        }
    }
}
