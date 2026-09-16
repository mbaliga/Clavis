package io.github.mbaliga.clavis.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.store.UiState
import io.github.mbaliga.clavis.ui.components.CitedText
import io.github.mbaliga.clavis.ui.components.H2
import io.github.mbaliga.clavis.ui.components.LaneRow
import io.github.mbaliga.clavis.ui.components.PrimaryButton
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SoftCard
import io.github.mbaliga.clavis.ui.components.TopBar
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.nav.LocalCompact
import io.github.mbaliga.clavis.ui.screens.settings.setLaneWithFamilyGuard
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import io.github.mbaliga.clavis.ui.theme.ClavisTokens

/** `OB` (template L1504–1509), verbatim; the language picker is step 4 (`S.ob === 4`). */
class ObStep(val glyphs: String, val heading: String, val copy: String)

val OB_STEPS = listOf(
    ObStep(
        "चाबी · chave · چابی", "Nine languages. One graph.",
        "Clavis teaches several languages at once through comic bubbles you can almost read — one to three new words at a time, never a wall of vocabulary. The comic is Pepper&Carrot, a libre webcomic that exists in all nine of your languages. [1][4]",
    ),
    ObStep(
        "____", "Tap what you don't know.",
        "New words come back right before you'd forget them — in a few seconds, then a day, then a week, as questions rather than re-reading. No lessons, no XP. Just short encounters until a word stops needing effort. [2][3]",
    ),
    ObStep(
        "es → 日本語 → عربي", "It hops languages on purpose.",
        "Mixing languages across a session is the method, not a glitch — spacing one language stretches another's memory. In Paths you can see every branch open to you and take one yourself; otherwise Clavis follows wherever you're improving fastest. [2][10]",
    ),
    ObStep(
        "62% readable", "Progress is real reading.", // D-18: never a bare percentage
        "Your progress is the share of the actual comic you can read unaided, per language — and the Cosmos shows every word you've touched as a point of light among the world's languages. Short yes/no tests first, so a language you half-own isn't treated as new. [1][31] " +
            "A bubble counts as readable when at least 95 of every 100 words in it are ones you hold or are presumed to know — that is the only score. [1]",
    ),
)

const val OB_PICKER_H2 = "Your nine."
const val OB_PICKER_COPY =
    "Speaking practice is heavier than reading, so one speaking language per family is the honest default — the rest you'll learn to read and recognise. Change any of this later in Settings."
const val OB_CHOOSE = "Choose my languages"
const val OB_NEXT = "Next"
const val OB_BACK = "Back"

/** D-18: the honesty card, shown with the language picker before `Begin`. */
const val OB_HONESTY_EYEBROW = "Before you begin"
const val OB_HONESTY =
    "Clavis is a method being tested on one person. It is not a validated course — the evidence for and against every design choice is one tap away, in Evidence & credits. It is not for cramming one language in six weeks, it does not score pronunciation, it is not built for children or classrooms, and it keeps everything on this device."

@Composable
fun OnboardingScreen(store: ClavisStore, ui: UiState) {
    val step = store.state.ob.coerceIn(0, 4)
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    // `.ob h2`: clamp(1.7rem, 6vw, 2.2rem) — 1 rem = 17 px: the floor on phones, the cap on wide windows.
    val h2Size = if (LocalCompact.current) 28.9f else 37.4f
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("onboarding")) {
        TopBar("welcome")
        if (step < 4) {
            val s = OB_STEPS[step]
            Spacer(Modifier.height(72.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.glyphs, style = t.obGlyphs, color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.tagged("ob-glyphs"))
                Spacer(Modifier.height(10.dp))
                Text(s.heading, style = t.obH2.copy(fontSize = h2Size.sp, lineHeight = (h2Size * 1.15f).sp), color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.tagged("ob-heading"))
                Spacer(Modifier.height(10.dp))
                CitedText(s.copy, onCite = store::openColophon, color = p.ink2, style = t.body.copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(24.dp))
                Dots(step)
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (step > 0) QuietButton(OB_BACK, onClick = store::obBack, testTag = "ob-back")
                    PrimaryButton(if (step == 3) OB_CHOOSE else OB_NEXT, onClick = store::obNext, modifier = Modifier.weight(1f), testTag = "ob-next")
                }
            }
        } else {
            H2(OB_PICKER_H2, Modifier.tagged("ob-heading"))
            Text(OB_PICKER_COPY, style = t.body, color = p.ink2, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            SoftCard {
                val lanes = store.registry.all.filter { it.enabledByDefault || store.state.laneMode(it.iso) != io.github.mbaliga.clavis.core.LaneMode.OFF }
                    .filter { store.sceneCount(it.iso) > 0 }
                for (info in lanes) {
                    val l = info.iso
                    val untested = store.state.tests[l.code] == null
                    LaneRow(
                        name = info.name,
                        colour = ClavisTokens.laneColour(info.colour),
                        mode = store.state.laneMode(l),
                        meta = "${store.sceneCount(l)} bubbles · ${store.family(l)} family" + if (untested) " · untested" else "",
                        onMode = { setLaneWithFamilyGuard(store, l, info.family, it) }, // same R-4 guard as Settings; its confirm is the shell's global dialog
                        testTagPrefix = "lane-${l.code}",
                    )
                }
            }
            SoftCard {
                Text(OB_HONESTY_EYEBROW.uppercase(), style = t.eyebrow, color = p.ink3)
                Spacer(Modifier.height(6.dp))
                Text(OB_HONESTY, style = t.small, color = p.ink)
            }
            Spacer(Modifier.height(8.dp))
            Dots(4)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton(OB_BACK, onClick = store::obBack, testTag = "ob-back")
                PrimaryButton(Copy.BEGIN, onClick = store::finishOnboarding, modifier = Modifier.weight(1f), testTag = "ob-begin")
            }
            Spacer(Modifier.height(40.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Dots(step: Int) {
    val p = ClavisTheme.palette
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { contentDescription = "step ${step + 1} of 5" }.tagged("ob-dots"),
    ) {
        repeat(5) { i ->
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (i == step) p.accent else p.line))
        }
    }
    Spacer(Modifier.width(0.dp))
}
