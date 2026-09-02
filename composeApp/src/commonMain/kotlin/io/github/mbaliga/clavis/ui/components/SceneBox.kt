package io.github.mbaliga.clavis.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import io.github.mbaliga.clavis.core.Corpus
import io.github.mbaliga.clavis.core.Lang
import io.github.mbaliga.clavis.core.Lettering
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.lineOf
import io.github.mbaliga.clavis.ui.theme.ClavisMotion
import io.github.mbaliga.clavis.ui.theme.ClavisShapes
import io.github.mbaliga.clavis.ui.theme.ClavisTheme
import io.github.mbaliga.clavis.ui.theme.ClavisTokens
import io.github.mbaliga.clavis.ui.theme.LocalReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The scene box (template-spec §10, §5.1). Two modes share one [mask]:
 *
 *  - **cropped** (`full = false`): the page cropped around the current balloon (centre on the
 *    rect, 0.78 aspect, escape hatch), a soft vignette, and — when the rect is credible — the
 *    balloon re-lettered on a translucent plate; otherwise a pulsing marker.
 *  - **whole page** (`full = true`): the page at its own aspect with every balloon of [siblings]
 *    lettered; the current one gets a halo; the others are tappable (gloss).
 *
 * Mask contract: `null` letters the real line; `""` letters *nothing* for the current balloon
 * (plate and text omitted — marker only); a string letters that gapped text. The caller computes
 * the mask once per encounter and passes the same value to both modes.
 *
 * The box is decorative for assistive tech (the caption row is the accessible carrier); the
 * lettered texts carry test tags `balloon-<sceneId>` and markers `marker-<sceneId>`.
 */
@Composable
fun SceneBox(
    corpus: Corpus,
    images: PageImages,
    scene: Scene,
    lang: Lang,
    full: Boolean,
    mask: String?,
    modifier: Modifier = Modifier,
    siblings: List<Scene> = emptyList(),
    onTapBalloon: ((Scene) -> Unit)? = null,
    mini: Boolean = false,
    numbers: Boolean = false,
    noHalo: Boolean = false,
    rtl: Boolean = false,
    /** Story mode: the "current" balloon is just the first one and must be tappable too. */
    tapCurrent: Boolean = false,
) {
    val bitmap by rememberPageBitmap(images, scene.m)
    val info = corpus.pages[scene.m]
    val pageW = info?.width?.takeIf { it > 0 } ?: bitmap?.width ?: 640
    val pageH = info?.height?.takeIf { it > 0 } ?: bitmap?.height ?: 904
    if (full) {
        WholePage(bitmap, pageW, pageH, scene, lang, mask, siblings, onTapBalloon, numbers, noHalo, rtl, tapCurrent, modifier)
    } else {
        CroppedScene(bitmap, pageW, pageH, scene, lang, mask, mini, rtl, modifier)
    }
}

private val BalloonStyle: TextStyle
    @Composable get() = ClavisTheme.type.balloon.copy(
        color = ClavisTokens.balloonInk,
        textAlign = TextAlign.Center,
        shadow = Shadow(Color.White, Offset.Zero, 3f),
    )

@Composable
private fun pulse(periodMs: Int, from: Float, to: Float): Float {
    if (LocalReducedMotion.current) return (from + to) / 2f
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(from, to, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "p")
    return v
}

@Composable
private fun haloAlpha(): Float {
    if (LocalReducedMotion.current) return 0.85f
    val t = rememberInfiniteTransition(label = "halo")
    val v by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(ClavisMotion.BHALO_MS / 2), RepeatMode.Reverse), label = "h")
    return v
}

private fun DrawScope.plate(cx: Float, cy: Float, w: Float, h: Float) {
    // `.bplate`: rgba(255,255,255,.9), border-radius 44%/50%, blur(5px) — approximated by a radial
    // falloff so the edge is soft without a blur pass.
    val r = max(w, h) / 2f
    val sx = w / (2f * r); val sy = h / (2f * r)
    scale(sx, sy, pivot = Offset(cx, cy)) {
        drawCircle(
            Brush.radialGradient(0f to Color.White.copy(alpha = 0.92f), 0.72f to Color.White.copy(alpha = 0.9f), 1f to Color.White.copy(alpha = 0f), center = Offset(cx, cy), radius = r),
            radius = r, center = Offset(cx, cy),
        )
    }
}

private fun DrawScope.marker(cx: Float, cy: Float, pulseT: Float, mini: Boolean, dp: Float) {
    if (!mini) {
        val s = 0.45f + pulseT * 1.05f
        drawCircle(Color.White.copy(alpha = (0.85f * (1f - pulseT)).coerceIn(0f, 1f)), radius = 18f * dp * s, center = Offset(cx, cy))
    }
    val rad = if (mini) 3.5f * dp else 6.5f * dp
    drawCircle(Color.White, rad, Offset(cx, cy))
    drawCircle(ClavisTokens.markerRing, rad, Offset(cx, cy), style = Stroke(2.5f * dp))
}

private fun DrawScope.vignette(mx: Float, my: Float, bw: Float, bh: Float, w: Float, h: Float) {
    val rx = max(bw * 1.7f, 0.52f) * w
    val ry = max(bh * 2.6f, 0.52f) * h
    val r = max(rx, ry)
    scale(rx / r, ry / r, pivot = Offset(mx, my)) {
        drawCircle(
            Brush.radialGradient(0.56f to Color.Transparent, 1f to Color(0x570A0A10), center = Offset(mx, my), radius = r),
            radius = r * 3f, center = Offset(mx, my),
        )
    }
}

@Composable
private fun CroppedScene(
    bitmap: ImageBitmap?, pageW: Int, pageH: Int, sc: Scene, lang: Lang, mask: String?, mini: Boolean, rtl: Boolean, modifier: Modifier,
) {
    val crop = remember(sc, lang, pageW, pageH) { LetteringMath.crop(sc, lang, pageW, pageH) }
    val line = sc.x[lang]
    val letter = crop.credible && !line.isNullOrEmpty() && mask != ""
    val text = lineOf(lang, if (mask != null) mask else line.orEmpty())
    val p = ClavisTheme.palette
    val pulseT = pulse(ClavisMotion.BPULSE_MS, 0f, 1f)
    val style = BalloonStyle
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(crop.aspect)
            .clip(ClavisShapes.scene)
            .background(p.surface2)
            .tagged("scene-cropped")
            .semantics { contentDescription = "comic scene" },
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val dp1 = with(density) { 1.dp.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                val sx = bitmap.width.toDouble() / pageW; val sy = bitmap.height.toDouble() / pageH
                drawImage(
                    bitmap,
                    srcOffset = IntOffset((crop.x0 * sx).roundToInt(), (crop.y0 * sy).roundToInt()),
                    srcSize = IntSize(max(1, (crop.ww * sx).roundToInt()).coerceAtMost(bitmap.width), max(1, (crop.wh * sy).roundToInt()).coerceAtMost(bitmap.height)),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
            val mx = (crop.mx * size.width).toFloat(); val my = (crop.my * size.height).toFloat()
            vignette(mx, my, crop.bw.toFloat(), crop.bh.toFloat(), size.width, size.height)
            if (letter) {
                val bx = crop.bx * size.width; val by = crop.by * size.height
                val bw = crop.bw * size.width; val bh = crop.bh * size.height
                plate(((bx + PLATE_DX_F * bw) + bw * 1.18 / 2).toFloat(), ((by + PLATE_DY_F * bh) + bh * 1.32 / 2).toFloat(), (bw * 1.18).toFloat(), (bh * 1.32).toFloat())
            } else {
                marker(mx, my, pulseT, mini, dp1)
            }
        }
        if (!letter) {
            Box(Modifier.size(1.dp).offset { IntOffset((crop.mx * wPx).roundToInt(), (crop.my * hPx).roundToInt()) }.tagged("marker-${sc.i}"))
        }
        if (letter && !mini) {
            val boxW = (crop.bw * wPx).roundToInt().coerceAtLeast(1)
            val boxH = (min(crop.bh, 0.96 - crop.by) * hPx).roundToInt().coerceAtLeast(1)
            val r = Lettering.rectFor(sc, lang)
            val startPx = LetteringMath.cropFontPx(r.w, r.h, pageW, pageH, text.length)
            val fsPx = remember(text, boxW, boxH, startPx) {
                LetteringMath.fitText(measurer, density, text, style, boxW, boxH, startPx, LetteringMath.CROP_FIT_MIN)
            }
            BalloonText(text, fsPx, style, IntOffset((crop.bx * wPx).roundToInt(), (crop.by * hPx).roundToInt()), IntSize(boxW, boxH), rtl, masked = mask != null, tag = "balloon-${sc.i}", onTap = null)
        }
    }
}

private const val PLATE_DX_F = -0.09
private const val PLATE_DY_F = -0.16

@Composable
private fun BalloonText(
    text: String, fsPx: Double, style: TextStyle, at: IntOffset, box: IntSize, rtl: Boolean, masked: Boolean, tag: String, onTap: (() -> Unit)?,
) {
    val density = LocalDensity.current
    val sp = with(density) { fsPx.toFloat().toSp() }
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Box(
            Modifier
                .offset { at }
                .size(with(density) { box.width.toDp() }, with(density) { box.height.toDp() })
                .then(if (onTap != null) Modifier.clickable(role = Role.Button, onClick = onTap) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = style.copy(fontSize = sp, lineHeight = (sp.value * 1.05).sp),
                overflow = TextOverflow.Clip,
                modifier = Modifier.tagged(tag).semantics { contentDescription = if (masked) "balloon, gapped" else "balloon" },
            )
        }
    }
}

@Composable
private fun WholePage(
    bitmap: ImageBitmap?, pageW: Int, pageH: Int, cur: Scene, lang: Lang, mask: String?, siblings: List<Scene>,
    onTap: ((Scene) -> Unit)?, numbers: Boolean, noHalo: Boolean, rtl: Boolean, tapCurrent: Boolean, modifier: Modifier,
) {
    val p = ClavisTheme.palette
    val pulseT = pulse(ClavisMotion.BPULSE_MS, 0f, 1f)
    val halo = haloAlpha()
    val style = BalloonStyle
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scenes = if (siblings.isEmpty()) listOf(cur) else siblings
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(pageW.toFloat() / pageH.toFloat())
            .clip(ClavisShapes.scene)
            .background(p.surface2)
            .border(1.dp, p.line, ClavisShapes.scene)
            .tagged("scene-full")
            .semantics { contentDescription = "comic page" },
    ) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val dp1 = with(density) { 1.dp.toPx() }
        // Geometry per sibling, computed once per layout size.
        val placed = remember(scenes, lang, cur.i, mask, wPx, hPx) {
            scenes.mapIndexed { i, s ->
                val r = Lettering.rectFor(s, lang)
                val ok = Lettering.rectOK(r)
                val isCur = s.i == cur.i
                val line = s.x[lang].orEmpty()
                val shown = if (isCur && mask != null) mask else line
                val lettered = ok && line.isNotEmpty() && !(isCur && mask == "")
                val text = lineOf(lang, shown)
                val boxW = (r.w * wPx).roundToInt().coerceAtLeast(1)
                val boxH = (r.h * hPx).roundToInt().coerceAtLeast(1)
                val fs = if (lettered) LetteringMath.fitText(measurer, density, text, style, boxW, boxH, LetteringMath.pageFontPx(wPx.toDouble(), hPx.toDouble(), r, text.length), LetteringMath.PAGE_FONT_MIN) else 0.0
                Placed(i, s, r, ok, isCur, lettered, text, boxW, boxH, fs)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                drawImage(bitmap, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Medium)
            }
            for (pl in placed) {
                val r = pl.r
                if (!pl.ok) {
                    if (pl.isCur || numbers) {
                        val cx = ((r.x + r.w / 2) * 100).coerceIn(2.0, 98.0) / 100 * size.width
                        val cy = ((r.y + r.h / 2) * 100).coerceIn(2.0, 98.0) / 100 * size.height
                        marker(cx.toFloat(), cy.toFloat(), pulseT, mini = numbers && !pl.isCur, dp = dp1)
                    }
                    continue
                }
                val x = (r.x * size.width).toFloat(); val y = (r.y * size.height).toFloat()
                val w = (r.w * size.width).toFloat(); val h = (r.h * size.height).toFloat()
                if (pl.isCur && !noHalo) {
                    val cx = x + w / 2; val cy = y + h / 2
                    val hw = w * 1.3f; val hh = h * 1.5f
                    val rr = max(hw, hh) / 2
                    scale(hw / (2 * rr), hh / (2 * rr), pivot = Offset(cx, cy)) {
                        drawCircle(Brush.radialGradient(0.55f to Color.White.copy(alpha = 0.55f * halo), 1f to Color.Transparent, center = Offset(cx, cy), radius = rr), rr, Offset(cx, cy))
                        drawCircle(Color.White.copy(alpha = halo), rr * 0.98f, Offset(cx, cy), style = Stroke(2.2f * dp1))
                    }
                }
                if (!pl.lettered) continue
                plate((x + PLATE_DX_F.toFloat() * w) + w * 1.18f / 2, (y + PLATE_DY_F.toFloat() * h) + h * 1.32f / 2, w * 1.18f, h * 1.32f)
            }
        }
        for (pl in placed) {
            if (!pl.ok) {
                if (pl.isCur) Box(Modifier.size(1.dp).offset { IntOffset(((pl.r.x + pl.r.w / 2) * wPx).roundToInt(), ((pl.r.y + pl.r.h / 2) * hPx).roundToInt()) }.tagged("marker-${pl.s.i}"))
                continue
            }
            if (pl.isCur && !pl.lettered) {
                Box(Modifier.size(1.dp).offset { IntOffset(((pl.r.x + pl.r.w / 2) * wPx).roundToInt(), ((pl.r.y + pl.r.h / 2) * hPx).roundToInt()) }.tagged("marker-${pl.s.i}"))
                continue
            }
            val tap = if (onTap != null && (!pl.isCur || tapCurrent)) ({ onTap(pl.s) }) else null
            BalloonText(
                pl.text, pl.fs, style, IntOffset((pl.r.x * wPx).roundToInt(), (pl.r.y * hPx).roundToInt()), IntSize(pl.boxW, pl.boxH), rtl,
                masked = pl.isCur && mask != null, tag = "balloon-${pl.s.i}", onTap = tap,
            )
            if (numbers) {
                val nx = ((pl.r.x - pl.r.w * 0.04) * 100).coerceIn(1.2, 98.5) / 100 * wPx
                val ny = ((pl.r.y - pl.r.h * 0.10) * 100).coerceIn(1.2, 98.5) / 100 * hPx
                Box(
                    Modifier.offset { IntOffset(nx.roundToInt(), ny.roundToInt()) }
                        .background(ClavisTokens.pageNumBg, ClavisShapes.chip).padding(horizontal = 5.dp, vertical = 1.dp),
                ) { Text("${pl.index + 1}", style = ClavisTheme.type.chip, color = Color.White) }
            }
        }
    }
}

private class Placed(
    val index: Int, val s: Scene, val r: io.github.mbaliga.clavis.core.Rect, val ok: Boolean, val isCur: Boolean,
    val lettered: Boolean, val text: String, val boxW: Int, val boxH: Int, val fs: Double,
)
