package io.github.mbaliga.clavis.ui.screens.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.BaselineTest
import io.github.mbaliga.clavis.core.Copy
import io.github.mbaliga.clavis.core.Script
import io.github.mbaliga.clavis.store.ClavisStore
import io.github.mbaliga.clavis.store.Confirm
import io.github.mbaliga.clavis.store.LocalRev
import io.github.mbaliga.clavis.ui.components.LaneDot
import io.github.mbaliga.clavis.ui.components.LinkButton
import io.github.mbaliga.clavis.ui.components.PrimaryButton
import io.github.mbaliga.clavis.ui.components.ProgressBar
import io.github.mbaliga.clavis.ui.components.QuietButton
import io.github.mbaliga.clavis.ui.components.SpeakButton
import io.github.mbaliga.clavis.ui.components.TopBar
import io.github.mbaliga.clavis.ui.components.tagged
import io.github.mbaliga.clavis.ui.screens.session.KEEP_GOING
import io.github.mbaliga.clavis.ui.screens.session.STOP
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import io.github.mbaliga.clavis.ui.theme.ClavisTokens

/**
 * Cancel, Escape and system back all come through here: with answers on the board the test stops
 * only after a confirm (they are not kept — usability review); an untouched test just closes.
 */
fun confirmCancelTest(store: ClavisStore, test: BaselineTest) {
    if (test.i > 0) store.confirm(Confirm(null, Copy.TEST_STOP_CONFIRM, STOP, KEEP_GOING, onConfirm = store::cancelTest))
    else store.cancelTest()
}

/** `renderTest` (template L1597–1611): yes/no with pseudoword foils; keyboard ←/n and →/y. */
@Composable
fun BaselineTestScreen(store: ClavisStore, test: BaselineTest) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    @Suppress("UNUSED_VARIABLE") val rev = LocalRev.current // `test.i` is mutated in place
    val info = store.info(test.l)
    val item = test.items.getOrNull(test.i) ?: return
    val latin = info?.script == Script.LATIN
    val rtl = info?.rtl == true
    val colour = info?.let { ClavisTokens.laneColour(it.colour) } ?: ClavisTokens.fallbackDot
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).tagged("test")) {
        TopBar("")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            LaneDot(colour)
            Spacer(Modifier.width(8.dp))
            Text(store.langName(test.l), style = t.h3, color = p.ink)
            Spacer(Modifier.width(8.dp))
            Text(Copy.testProgress(test.i + 1, test.items.size), style = t.small, color = p.ink2, modifier = Modifier.tagged("test-progress"))
        }
        Text(Copy.TEST_INTRO, style = t.small, color = p.ink2, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        ProgressBar(test.i.toDouble() / test.items.size, colour)
        Spacer(Modifier.height(36.dp))
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Text(
                item.w,
                style = if (latin) t.testWord else t.testWordPlain,
                color = p.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().tagged("test-word").semantics { contentDescription = "word: ${item.w}" },
            )
        }
        if (store.state.settings.readings && item.r.isNotEmpty()) {
            Text(item.r, style = t.mono, color = p.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        if (item.real && store.canSpeak(test.l)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SpeakButton(onClick = { store.speak(test.l, item.w) }, testTag = "test-speak")
            }
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton(Copy.DONT_KNOW, onClick = { store.answerTest(false) }, modifier = Modifier.weight(1f), testTag = "test-no")
            PrimaryButton(Copy.KNOW_IT, onClick = { store.answerTest(true) }, modifier = Modifier.weight(1f), testTag = "test-yes")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            LinkButton(Copy.CANCEL, onClick = { confirmCancelTest(store, test) }, testTag = "test-cancel")
        }
        Text("← / n · → / y", style = t.chip, color = p.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Spacer(Modifier.height(40.dp))
    }
}
