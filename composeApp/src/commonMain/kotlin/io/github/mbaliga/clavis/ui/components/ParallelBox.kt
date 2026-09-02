package io.github.mbaliga.clavis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.lineOf
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/**
 * `.parallel`: the English line and, when the engine says the second meeting has happened,
 * the family siblings' lines (R-5: origins and kin unlock at `reps ≥ 2`). Each row is a lowercase
 * iso tag in mono plus the line; Arabic rows are RTL.
 */
@Composable
fun ParallelBox(sc: Scene, lang: Lang, siblings: List<Lang>, isRtl: (Lang) -> Boolean, modifier: Modifier = Modifier) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val rows = buildList {
        sc.x[Lang.EN]?.let { add(Lang.EN to it) }
        for (s in siblings) if (s != lang) sc.x[s]?.let { add(s to lineOf(s, it)) }
    }
    Column(
        modifier.fillMaxWidth().background(p.surface2, ClavisShapes.parallel).padding(horizontal = 12.dp, vertical = 8.dp).tagged("parallel"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((l, line) in rows) {
            Row(verticalAlignment = Alignment.Top) {
                Text(l.code, style = t.chip, color = p.ink3, modifier = Modifier.width(28.dp).padding(top = 5.dp))
                CompositionLocalProvider(LocalLayoutDirection provides if (isRtl(l)) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Text(line, style = t.small, color = p.ink, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
