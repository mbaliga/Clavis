package io.github.mbaliga.clavis.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/** `VIEWNAMES` (template L763) — the topbar sub-title per view; new screens per product-spec §3.2. */
val VIEW_NAMES: Map<String, String> = mapOf(
    "learn" to "",
    "cosmos" to "every word you've touched",
    "paths" to "what could come next",
    "letters" to "letters & sounds",
    "you" to "your record",
    "set" to "settings",
    "colophon" to "evidence & credits",
    "story" to "just reading",
    "languages" to "speak, read, or rest",
    "media" to "your texts",
)

/** The five dock/rail destinations in their fixed order (`NAV`, D-10). */
data class NavItem(val id: String, val label: String)

val NAV: List<NavItem> = listOf(
    NavItem("learn", "Learn"), NavItem("cosmos", "Cosmos"), NavItem("paths", "Paths"), NavItem("letters", "Letters"), NavItem("you", "You"),
)

/** Which dock item is lit for a view: the view itself, else Learn (Settings/Colophon/Story highlight Learn). */
fun activeNav(view: String): String = if (NAV.any { it.id == view }) view else "learn"

/** The dock icons, drawn to match the reference's line icons (bubble, dots, tree, あ, person). */
@Composable
fun NavIcon(id: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    when (id) {
        "letters" -> Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Text("あ", color = tint, fontSize = (size.value * 0.82f).sp, fontWeight = FontWeight.Normal, fontFamily = ClavisTheme.fonts.body)
        }
        "you" -> Icon(Icons.Outlined.Person, contentDescription = null, tint = tint, modifier = modifier.size(size))
        "set" -> Icon(Icons.Outlined.Settings, contentDescription = null, tint = tint, modifier = modifier.size(size))
        else -> Canvas(modifier.size(size)) {
            val w = this.size.width; val h = this.size.height
            val sw = w * 0.075f
            when (id) {
                "learn" -> {
                    val path = Path().apply {
                        moveTo(w * 0.15f, h * 0.2f); lineTo(w * 0.85f, h * 0.2f); lineTo(w * 0.85f, h * 0.68f)
                        lineTo(w * 0.42f, h * 0.68f); lineTo(w * 0.25f, h * 0.86f); lineTo(w * 0.25f, h * 0.68f); lineTo(w * 0.15f, h * 0.68f); close()
                    }
                    drawPath(path, tint, style = Stroke(sw, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                "cosmos" -> {
                    val pts = listOf(0.5f to 0.22f, 0.25f to 0.4f, 0.75f to 0.4f, 0.35f to 0.7f, 0.65f to 0.7f, 0.5f to 0.5f)
                    for ((i, pt) in pts.withIndex()) drawCircle(tint, radius = if (i == 5) w * 0.09f else w * 0.06f, center = Offset(pt.first * w, pt.second * h))
                }
                "paths" -> {
                    drawLine(tint, Offset(w * 0.5f, h * 0.85f), Offset(w * 0.5f, h * 0.5f), sw, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.25f, h * 0.25f), sw, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.75f, h * 0.25f), sw, StrokeCap.Round)
                    drawCircle(tint, w * 0.07f, Offset(w * 0.25f, h * 0.22f)); drawCircle(tint, w * 0.07f, Offset(w * 0.75f, h * 0.22f))
                    drawCircle(tint, w * 0.07f, Offset(w * 0.5f, h * 0.87f))
                }
                else -> drawRect(tint, size = Size(w, h), style = Stroke(sw))
            }
        }
    }
}
