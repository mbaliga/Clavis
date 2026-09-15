package io.github.mbaliga.clavis.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.ClavisVersion
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Family
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.LaneMode
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.Confirm
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.Eyebrow
import io.github.mbaliga.clavis.ui.components.H2
import io.github.mbaliga.clavis.ui.components.forwardArrow
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.LaneRow
import io.github.mbaliga.clavis.ui.components.LinkButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.Segmented
import io.github.mbaliga.clavis.ui.components.SettingRow
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.ScreenTopBar
import io.github.mbaliga.clavis.ui.screens.learn.laneColour
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import io.github.mbaliga.clavis.ui.theme.ThemeMode

const val ABOUT_COPY =
    "Voices come from a downloaded voice or your device. Everything stays on this device — there is no server and nothing is sent anywhere. The evidence behind every design choice, the materials and their licences, and a glossary live in Evidence & credits."
const val FAMILY_WARNING_TITLE = "Two speaking languages in one family"
const val FAMILY_WARNING =
    "Producing two sibling languages early breeds blends — reading across a family is cheap, speaking two of its members at once is not [9][6]. You can still turn it on; Clavis will record any mix-ups it sees."
const val FEEDBACK_SUBJECT = "Clavis feedback"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(store: ClavisStore, ui: UiState) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    val s = store.state.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("settings")) {
        ScreenTopBar(store, "settings")
        H2("Settings")

        SoftCard {
            Eyebrow("Languages")
            val lanes = store.registry.all.filter { (it.enabledByDefault || store.state.laneMode(it.iso) != LaneMode.OFF) && store.sceneCount(it.iso) > 0 }
            for (info in lanes) {
                val l = info.iso
                LaneRow(
                    name = info.name, colour = laneColour(store, l), mode = store.state.laneMode(l),
                    meta = "${store.sceneCount(l)} bubbles · ${store.family(l)} family" + if (store.state.tests[l.code] == null) " · untested" else "",
                    onMode = { mode -> setLaneWithFamilyGuard(store, l, info.family, mode) },
                    testTagPrefix = "lane-${l.code}",
                )
            }
            LinkButton("Manage languages ${forwardArrow()}", onClick = { store.setView("languages") })
        }

        SoftCard {
            Eyebrow("Rhythm")
            SliderRow("How firmly words should stick (retention)", s.retention.toFloat(), 0.80f..0.95f, 14, format = { "%.2f".fmt(it) }) { v -> store.updateSettings { retention = ((v * 100).toInt() / 100.0) } }
            SliderRow("New words per language per day", s.newPerDay.toFloat(), 2f..30f, 27, format = { it.toInt().toString() }) { v -> store.updateSettings { newPerDay = v.toInt() } }
            SliderRow("Encounters per session", s.sessionSize.toFloat(), 3f..10f, 6, format = { it.toInt().toString() }) { v -> store.updateSettings { sessionSize = v.toInt() } }
            ToggleRow("Readings under words", "rōmaji, pīnyīn, romanised Korean and Arabic", s.readings, "readings") { store.updateSettings { readings = !readings } }
            ToggleRow("One language per session", "hold a single language instead of hopping", s.monoSession, "mono") { store.updateSettings { monoSession = !monoSession } }
            ToggleRow("Motion", "the pool and the Cosmos hold still when off", s.motion, "motion") { store.updateSettings { motion = !motion } }
        }

        SoftCard {
            Eyebrow("Appearance")
            Segmented(
                options = listOf("system", "light", "dark"),
                selected = when (ui.theme) { ThemeMode.SYSTEM -> 0; ThemeMode.LIGHT -> 1; ThemeMode.DARK -> 2 },
                onSelect = { store.setTheme(when (it) { 0 -> ThemeMode.SYSTEM; 1 -> ThemeMode.LIGHT; else -> ThemeMode.DARK }) },
                groupLabel = "Appearance", testTagPrefix = "theme",
            )
            Text("Language of the interface: English (more later).", style = t.small, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
        }

        SoftCard {
            Eyebrow("Voices")
            for (l in store.engine.activeLanes()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    LaneDot(laneColour(store, l)); Spacer(Modifier.width(8.dp))
                    Text(store.langName(l), style = t.body, color = p.ink, modifier = Modifier.weight(1f))
                    Text(store.services.speaker.providerFor(l) ?: "none on this device", style = t.small, color = p.ink2)
                }
            }
            Text("Voices are never downloaded without your say-so; this build has no download yet. Arabic, Japanese and Korean rely on your device's own voices.", style = t.small, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
        }

        YourDataCard(store)

        SoftCard {
            Eyebrow("About")
            Text(ABOUT_COPY, style = t.body, color = p.ink)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietButton("Evidence & credits", onClick = { store.openColophon() }, testTag = "about-colophon")
                QuietButton("Replay the intro", onClick = store::replayIntro, testTag = "replay-intro")
                QuietButton("Send feedback", onClick = { sendFeedback(store) }, testTag = "feedback")
                QuietButton("Licences", onClick = { store.openColophon(0) }, testTag = "licences")
            }
            Text("v${ClavisVersion.NAME} · ${store.services.name}", style = t.chip, color = p.ink3, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(10.dp))
            QuietButton("Reset everything", onClick = {
                store.confirm(Confirm(null, Copy.ERASE_CONFIRM, "Erase", Copy.CANCEL, onConfirm = store::resetAll))
            }, testTag = "reset")
        }
        Spacer(Modifier.height(130.dp))
    }
}

private fun String.fmt(v: Float): String {
    val n = (v * 100).toInt()
    return "${n / 100}.${(n % 100).toString().padStart(2, '0')}"
}

/** R-4 / OD-6: a second productive lane in a family is allowed only after a cited warning. */
fun setLaneWithFamilyGuard(store: ClavisStore, l: Lang, family: Family, mode: LaneMode) {
    if (mode == LaneMode.PROD) {
        val other = store.registry.family(family).firstOrNull { it.iso != l && store.state.laneMode(it.iso) == LaneMode.PROD }
        if (other != null) {
            store.confirm(
                Confirm(
                    FAMILY_WARNING_TITLE,
                    FAMILY_WARNING.replace("[9][6]", "(references 9 and 6 in Evidence & credits)"),
                    "Speak both", "Keep reading",
                    onConfirm = { store.setLaneMode(l, LaneMode.PROD) },
                ),
            )
            return
        }
    }
    store.setLaneMode(l, mode)
}

fun sendFeedback(store: ClavisStore) {
    val draft = buildString {
        appendLine("Clavis v${ClavisVersion.NAME} · ${store.services.name}")
        appendLine("Lanes: " + store.engine.activeLanes().joinToString { it.code })
        appendLine("Encounters logged: ${store.state.log.size}")
        appendLine()
        appendLine("What happened:")
        appendLine()
        appendLine("What I expected:")
    }
    if (!store.services.share.share(FEEDBACK_SUBJECT, draft)) store.toast("Draft copied — paste it wherever you report issues")
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, format: (Float) -> String, onChange: (Float) -> Unit) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    var v by remember(value) { mutableStateOf(value) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = t.body, color = p.ink, modifier = Modifier.weight(1.1f))
        Slider(
            value = v, onValueChange = { v = it }, onValueChangeFinished = { onChange(v) }, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.line),
            modifier = Modifier.weight(1f).semantics { contentDescription = "$label ${format(v)}" }.tagged("slider-$label"),
        )
        Spacer(Modifier.width(10.dp))
        Text(format(v), style = t.mono, color = p.ink, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun ToggleRow(label: String, sub: String, on: Boolean, tag: String, onToggle: () -> Unit) {
    SettingRow(label, sub) {
        QuietButton(if (on) "On" else "Off", onClick = onToggle, selected = on, testTag = "toggle-$tag")
    }
}

/** `Your data` (template L2317–2325): a text field + Export (to clipboard) / Import (from the field). */
@Composable
fun YourDataCard(store: ClavisStore) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    SoftCard {
        Eyebrow(Copy.YOUR_DATA)
        Text("Everything lives on this device. Copy the text to back up or move devices; paste it back to restore.", style = t.small, color = p.ink2)
        OutlinedTextField(
            value = text, onValueChange = { text = it }, minLines = 3, maxLines = 6,
            textStyle = t.tiny.copy(fontFamily = ClavisTheme.fonts.mono),
            keyboardOptions = KeyboardOptions.Default,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, unfocusedBorderColor = p.line, focusedTextColor = p.ink, unfocusedTextColor = p.ink),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).tagged("data-field"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton(Copy.EXPORT, onClick = {
                val json = store.exportJson()
                text = json
                clipboard.setText(AnnotatedString(json))
                store.toast(Copy.COPIED)
            }, modifier = Modifier.weight(1f), testTag = "export")
            QuietButton(Copy.IMPORT, onClick = { if (store.importJson(text)) text = "" }, modifier = Modifier.weight(1f), testTag = "import")
        }
    }
}
