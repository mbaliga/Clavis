package io.github.mbaliga.clavis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.mbaliga.clavis.core.Token
import io.github.mbaliga.clavis.ui.theme.ClavisMotion
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import kotlinx.coroutines.delay

/** Visual state of one caption token (`tokenSpans`, template-spec §5). */
enum class TokenMark { PLAIN, UNKNOWN, KNOWN_FLIP, HIT, GAP, GAP_HIT, GAP_MISS }

/**
 * The caption row — the accessible carrier of every balloon line (product-spec §8.4). Tokens are
 * buttons; the gap token reads `blank — the word to recall`; RTL for Arabic; CJK at 1.45 rem with
 * no spaces (ja/zh), Korean and everything else spaced.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptionTokens(
    tokens: List<Token>,
    marks: List<TokenMark>,
    rtl: Boolean,
    cjk: Boolean,
    spaced: Boolean,
    readings: Boolean,
    gapText: String? = null,
    onTap: ((Int, Token) -> Unit)?,
    hintIndex: Int? = null,
    hint: (@Composable () -> Unit)? = null,
    onHintDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    testTagPrefix: String = "tok",
) {
    val p = ClavisTheme.palette
    val t = ClavisTheme.type
    val style = if (cjk || rtl) t.captionWide else t.caption
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth()) {
            FlowRow(
                Modifier.fillMaxWidth().semantics { contentDescription = "caption" },
                horizontalArrangement = Arrangement.spacedBy(if (spaced) 6.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                tokens.forEachIndexed { i, tok ->
                    val mark = marks.getOrElse(i) { TokenMark.PLAIN }
                    val isGap = mark == TokenMark.GAP || mark == TokenMark.GAP_HIT || mark == TokenMark.GAP_MISS
                    val bg = when (mark) {
                        TokenMark.UNKNOWN -> p.unk
                        TokenMark.KNOWN_FLIP -> p.mossSoft
                        TokenMark.HIT -> p.accentSoft
                        TokenMark.GAP -> p.accentSoft
                        TokenMark.GAP_HIT -> p.mossSoft
                        TokenMark.GAP_MISS -> p.rustSoft
                        TokenMark.PLAIN -> Color.Transparent
                    }
                    val ink = when (mark) {
                        TokenMark.GAP_HIT -> p.moss
                        TokenMark.GAP_MISS -> p.rust
                        TokenMark.GAP -> p.accentInk
                        else -> p.ink
                    }
                    val label = when {
                        mark == TokenMark.GAP -> "blank — the word to recall"
                        else -> buildString {
                            append(tok.surface)
                            if (readings && !tok.reading.isNullOrEmpty()) append(", ").append(tok.reading)
                            when (mark) {
                                TokenMark.UNKNOWN -> append(", new to you")
                                TokenMark.KNOWN_FLIP -> append(", you know this")
                                else -> {}
                            }
                        }
                    }
                    val tappable = onTap != null && mark != TokenMark.GAP
                    Column(
                        Modifier
                            .defaultMinSize(minHeight = 44.dp, minWidth = if (isGap) 46.dp else 0.dp)
                            .clip(ClavisShapes.tok)
                            .background(bg)
                            .then(if (tappable) Modifier.clickable(role = Role.Button) { onTap!!(i, tok) } else Modifier)
                            .semantics { contentDescription = label }
                            .tagged("$testTagPrefix-$i")
                            .then(
                                if (mark == TokenMark.UNKNOWN) Modifier.drawBehind {
                                    val y = size.height - 2.dp.toPx()
                                    drawLine(p.unkLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                                } else Modifier,
                            )
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val shown = when (mark) {
                            TokenMark.GAP -> "____"
                            TokenMark.GAP_HIT, TokenMark.GAP_MISS -> gapText ?: tok.surface
                            else -> tok.surface
                        }
                        Text(shown, style = style, color = ink, textAlign = TextAlign.Center)
                        if (readings && !isGap && !tok.reading.isNullOrEmpty()) {
                            Text(tok.reading!!, style = t.choiceReading, color = p.ink3, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            if (hintIndex != null && hint != null) {
                LaunchedEffect(hintIndex) {
                    delay(ClavisMotion.HINT_MS.toLong())
                    onHintDismiss()
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .clip(ClavisShapes.hint)
                            .background(p.surface2)
                            .clickable(role = Role.Button, onClick = onHintDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .tagged("hint"),
                    ) { hint() }
                }
            }
        }
    }
}
