package io.github.mbaliga.clavis.ui.screens.learn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.hash01
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import io.github.mbaliga.clavis.ui.theme.LocalReducedMotion
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The Learn pool (`orb`, template-spec §11.6): up to five soft radial drops in the active lanes'
 * `COSMOS_COL`, orbiting slowly and breathing — faster and brighter when words are due. Holds
 * still when motion is off or the platform asks for reduced motion (R-18b makes the Settings copy
 * true). `role=button`, `Start a session`. When [enabled] is false (every language resting, D-6)
 * the pool is still, dimmed, not clickable and says so.
 */
@Composable
fun Pool(
    lanes: List<Pair<String, Color>>,
    due: Int,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 290.dp,
    enabled: Boolean = true,
) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val reduced = LocalReducedMotion.current || !enabled
    var time by remember { mutableFloatStateOf(0f) }
    if (!reduced) {
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) time += ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                }
            }
        }
    }
    val drops = remember(lanes) {
        lanes.take(5).mapIndexed { i, (code, col) ->
            Drop(col, a = i * 2.39996f + 0.8f, sp = 0.10f + 0.035f * i, rr = 0.30f + 0.13f * hash01("drop$code").toFloat())
        }
    }
    val sub = when { !enabled -> Copy.POOL_RESTING; due > 0 -> Copy.pulling(due); else -> Copy.TWO_MINUTE_DIP }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onStart)
            .alpha(if (enabled) 1f else 0.55f)
            .semantics { contentDescription = "${Copy.START_SESSION_ARIA}, $sub"; if (!enabled) disabled() }
            .tagged("pool"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width; val h = this.size.height
            val cx = w / 2; val cy = h / 2
            // `.orbwrap::before`: the soft `--pool` halo at inset −9 %
            drawCircle(Brush.radialGradient(0.6f to p.pool, 1f to Color.Transparent, center = Offset(cx, cy), radius = w * 0.59f), radius = w * 0.59f, center = Offset(cx, cy))
            val r0 = min(w, h) * 0.34f
            val tt = time
            val breath = 1f + (if (due > 0) 0.06f else 0.025f) * sin(tt * (if (due > 0) 1.5f else 0.8f))
            val al = if (due > 0) 0.5f else 0.36f
            for (d in drops) {
                val ang = d.a + tt * d.sp
                val x = cx + cos(ang) * r0 * 0.42f
                val y = cy + sin(0.93f * ang + d.a) * r0 * 0.38f
                val rad = r0 * d.rr * 1.7f * breath
                drawCircle(
                    Brush.radialGradient(0f to d.col.copy(alpha = al), 0.55f to d.col.copy(alpha = al * 0.4f), 1f to d.col.copy(alpha = 0f), center = Offset(x, y), radius = rad),
                    radius = rad, center = Offset(x, y),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Copy.BEGIN, style = t.orbLabel, color = p.orbInk, textAlign = TextAlign.Center)
            Text(sub, style = t.small, color = p.ink2, textAlign = TextAlign.Center, modifier = Modifier.tagged("pool-sub"))
        }
    }
}

private class Drop(val col: Color, val a: Float, val sp: Float, val rr: Float)
