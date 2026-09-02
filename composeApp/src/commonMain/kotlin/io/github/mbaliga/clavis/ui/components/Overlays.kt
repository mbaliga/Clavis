package io.github.mbaliga.clavis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.store.Confirm
import io.github.mbaliga.clavis.store.Toast
import io.github.mbaliga.clavis.ui.theme.ClavisMotion
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import kotlinx.coroutines.delay

/** `.toast`: a quiet pill above the dock, a polite live region, visible 2200 ms. */
@Composable
fun ToastHost(toast: Toast?, onExpire: (Long) -> Unit, modifier: Modifier = Modifier) {
    if (toast == null) return
    val p = ClavisTheme.palette
    LaunchedEffect(toast.id) {
        delay(ClavisMotion.TOAST_MS.toLong())
        onExpire(toast.id)
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 520.dp)
                .shadow(2.dp, ClavisShapes.hint, ambientColor = p.shadowAmbient, spotColor = p.shadowKey)
                .background(p.ink, ClavisShapes.hint)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .tagged("toast"),
        ) {
            Text(toast.text, style = ClavisTheme.type.toast, color = p.paper, textAlign = TextAlign.Center)
        }
    }
}

/** Confirmation dialog for destructive intents; every button is a real 48 dp target. */
@Composable
fun ConfirmDialog(confirm: Confirm?, onDismiss: () -> Unit) {
    if (confirm == null) return
    val p = ClavisTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        titleContentColor = p.ink,
        textContentColor = p.ink2,
        title = confirm.title?.let { { Text(it, style = ClavisTheme.type.h3) } },
        text = { Text(confirm.text, style = ClavisTheme.type.body, color = p.ink) },
        confirmButton = {
            PrimaryButton(confirm.confirmLabel, onClick = { onDismiss(); confirm.onConfirm() }, testTag = "confirm-yes")
        },
        dismissButton = { QuietButton(confirm.dismissLabel, onClick = onDismiss, testTag = "confirm-no") },
        shape = ClavisShapes.card,
    )
}

/** `.coach`: the one-time coaching box, accent-soft. */
@Composable
fun CoachBox(text: String, onCite: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = ClavisTheme.palette
    Box(modifier.fillMaxWidth().background(p.accentSoft, ClavisShapes.hint).padding(horizontal = 14.dp, vertical = 10.dp).tagged("coach")) {
        CitedText(text, onCite, color = p.accentInk, style = ClavisTheme.type.small)
    }
}

/** The topbar: wordmark `Clavis` · view sub-title (mono, upper-case) · optional trailing control. */
@Composable
fun TopBar(subtitle: String, modifier: Modifier = Modifier, showWordmark: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    val p = ClavisTheme.palette
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showWordmark) Text("Clavis", style = ClavisTheme.type.wordmark, color = p.ink, modifier = Modifier.tagged("wordmark"))
        Spacer(Modifier.weight(1f))
        if (subtitle.isNotEmpty()) Text(subtitle.uppercase(), style = ClavisTheme.type.viewName, color = p.ink3)
        if (trailing != null) { Spacer(Modifier.width(10.dp)); trailing() }
    }
}

/** A hairline-bordered surface row used for settings toggles etc. */
@Composable
fun SettingRow(label: String, sub: String? = null, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    val p = ClavisTheme.palette
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.weight(1f)) {
            androidx.compose.foundation.layout.Column {
                Text(label, style = ClavisTheme.type.body, color = p.ink)
                if (sub != null) Text(sub, style = ClavisTheme.type.small, color = p.ink2)
            }
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
}

@Composable
fun OutlinedBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = ClavisTheme.palette
    Box(modifier.border(1.dp, p.line, ClavisShapes.parallel).background(p.surface, ClavisShapes.parallel).padding(12.dp)) { content() }
}
