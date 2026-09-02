package io.github.mbaliga.clavis.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/**
 * `laneRow` (template L1534–1544): dot · **name** · segmented `speak / read / off`, with the meta
 * line `{n} bubbles · {family} family[ · untested]`. Used by onboarding and Settings.
 */
@Composable
fun LaneRow(
    name: String,
    colour: Color,
    mode: LaneMode,
    meta: String,
    onMode: (LaneMode) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LaneDot(colour)
            Spacer(Modifier.width(10.dp))
            Text(name, style = t.h3, color = p.ink, modifier = Modifier.weight(1f))
            Segmented(
                options = listOf(Copy.CHIP_SPEAK, Copy.CHIP_READ, Copy.CHIP_OFF),
                selected = when (mode) { LaneMode.PROD -> 0; LaneMode.REC -> 1; LaneMode.OFF -> 2 },
                onSelect = { onMode(when (it) { 0 -> LaneMode.PROD; 1 -> LaneMode.REC; else -> LaneMode.OFF }) },
                groupLabel = "$name lane mode",
                testTagPrefix = testTagPrefix,
            )
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(meta, style = t.small, color = p.ink2)
        }
        HairlineDivider(Modifier.padding(top = 10.dp))
    }
}
