package io.github.mbaliga.clavis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mbaliga.clavis.core.ClavisVersion
import io.github.mbaliga.clavis.core.platformName
import io.github.mbaliga.clavis.resources.Res
import io.github.mbaliga.clavis.resources.tagline
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Application root shared by every target. Deliberately a placeholder: a wordmark, the tagline
 * (read through generated compose resources so the pipeline is exercised) and a counter button
 * that the headless UI test drives.
 */
@Composable
fun App() {
    ClavisTheme {
        var taps by rememberSaveable { mutableIntStateOf(0) }
        val palette = ClavisTheme.palette

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Clavis",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = 6.sp,
                    ),
                    color = palette.ink,
                    modifier = Modifier.testTag("wordmark"),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.ink2,
                )
                Spacer(Modifier.height(40.dp))
                Text(
                    text = taps.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.accent,
                    modifier = Modifier.testTag("counter-value"),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { taps++ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.btnPrimary,
                        contentColor = palette.btnPrimaryInk,
                    ),
                    modifier = Modifier.testTag("counter-button"),
                ) {
                    Text("Tap")
                }
                Spacer(Modifier.height(48.dp))
                Text(
                    text = "v${ClavisVersion.NAME} · ${platformName()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.ink3,
                )
            }
        }
    }
}
