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
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.ui.screens.cosmos.CosmosCopy
import io.github.mbaliga.clavis.ui.theme.ClavisTheme

/**
 * The provenance-labelled sound of a word or sentence (product-spec §5.1, §5.3; D-7, D-8, D-13):
 * an optional IPA line with its source chip, then ONE play control — a permissive recording
 * (download on tap, cached, labelled `recording · {source} · {licence} · {author}`) when the pack
 * carries one and this platform can decode it, else the synthetic voice (labelled so) when a voice
 * exists, else nothing. Never both, never unlabelled, never fetched without the tap.
 */
@Composable
fun AudioRow(
    store: ClavisStore,
    l: Lang,
    text: String,
    ipa: String?,
    ipaSource: String? = CosmosCopy.PRON_WIKTIONARY,
    audioUrl: String?,
    audioLicence: String?,
    audioBy: String?,
    audioSource: String,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "audio",
) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val ui = store.current
    Column(modifier.fillMaxWidth()) {
        if (!ipa.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(ipa, style = t.mono, color = p.ink, modifier = Modifier.tagged("$testTagPrefix-ipa"))
                if (ipaSource != null) { Spacer(Modifier.width(8.dp)); Chip(ipaSource) }
            }
        }
        val recording = audioUrl != null && store.clipOffered(audioUrl)
        when {
            recording -> {
                val url = audioUrl!!
                val busy = url in ui.clipBusy
                val cached = url in ui.clipsReady || store.clipCached(url)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuietButton(
                        when { busy -> CosmosCopy.DOWNLOADING; cached -> CosmosCopy.PLAY; else -> CosmosCopy.DOWNLOAD_RECORDING },
                        onClick = { if (!busy) store.playClip(url) }, enabled = !busy, minHeight = 40.dp, testTag = "$testTagPrefix-play",
                    )
                    Text(CosmosCopy.recordingLabel(audioSource, audioLicence, audioBy), style = t.tiny, color = p.ink3, modifier = Modifier.weight(1f).tagged("$testTagPrefix-label"))
                }
            }
            store.canSpeak(l) -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpeakButton(onClick = { store.speak(l, text) }, size = 40.dp, testTag = "$testTagPrefix-speak")
                    Text(CosmosCopy.SYNTHETIC, style = t.tiny, color = p.ink3, modifier = Modifier.tagged("$testTagPrefix-label"))
                }
            }
            else -> {}
        }
    }
}
