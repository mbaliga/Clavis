package io.github.mbaliga.clavis.ui.components

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.github.mbaliga.clavis.core.Lettering
import io.github.mbaliga.clavis.core.Rect
import io.github.mbaliga.clavis.core.Scene
import io.github.mbaliga.clavis.core.Lang
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure lettering maths from template-spec §10 (parity items 83–84): the cropped-crop geometry,
 * the plate geometry and the two font-fit rules. Kept free of Compose so it can be unit-tested.
 */
object LetteringMath {
    const val PLATE_DX = -0.09; const val PLATE_DY = -0.16; const val PLATE_SW = 1.18; const val PLATE_SH = 1.32
    const val PAGE_FONT_MIN = 4.5; const val PAGE_FONT_MAX = 26.0; const val PAGE_FONT_SCALE = 1.06
    const val CROP_FONT_MIN = 9.0; const val CROP_FONT_MAX = 26.0; const val CROP_FONT_SCALE = 1.05; const val CROP_FIT_MIN = 8.0
    const val ASPECT = 0.78

    /** A crop of the page in page pixels plus the balloon's rect inside the crop (fractions of the crop). */
    data class Crop(
        val x0: Double, val y0: Double, val ww: Double, val wh: Double,
        val credible: Boolean,
        /** Balloon rect as fractions of the crop box. */
        val bx: Double, val by: Double, val bw: Double, val bh: Double,
    ) {
        val aspect: Float get() = (ww / wh).toFloat()
        val mx: Double get() = bx + bw / 2
        val my: Double get() = by + bh / 2
    }

    /** `sceneBox(...)` cropped mode: centre on the balloon, 0.78 aspect, the 1.35×h escape hatch. */
    fun crop(sc: Scene, lang: Lang?, pageW: Int, pageH: Int): Crop {
        val W = pageW.toDouble(); val H = pageH.toDouble()
        val r: Rect = if (lang != null) Lettering.rectFor(sc, lang) else sc.r
        val credible = Lettering.rectOK(r)
        val rw = r.w; val rh = r.h
        val cx = (r.x + rw / 2) * W; val cy = (r.y + rh / 2) * H
        var ww = if (credible) max(W * 0.5, rw * W * 1.5) else W * 0.55
        var wh = ww * ASPECT
        if (credible && wh < rh * H * 1.35) { wh = min(rh * H * 1.35, H); ww = wh / ASPECT }
        ww = min(ww, W); wh = min(wh, H)
        val x0 = (cx - ww / 2).coerceIn(0.0, max(0.0, W - ww))
        val y0 = (cy - wh / 2).coerceIn(0.0, max(0.0, H - wh))
        val bx = (r.x * W - x0) / ww; val by = (r.y * H - y0) / wh
        val bw = rw * W / ww; val bh = rh * H / wh
        return Crop(x0, y0, ww, wh, credible, bx, by, bw, bh)
    }

    /** Initial page font size in px: `sqrt(max(50, area) / max(6, len)) · 1.06`, clamped 4.5–26. */
    fun pageFontPx(boxWpx: Double, boxHpx: Double, r: Rect, len: Int): Double {
        val area = max(50.0, r.w * boxWpx * r.h * boxHpx)
        return (sqrt(area / max(6, len)) * PAGE_FONT_SCALE).coerceIn(PAGE_FONT_MIN, PAGE_FONT_MAX)
    }

    /** Initial cropped font size in px (template L1795), clamped 9–26, then fitted down to 8. */
    fun cropFontPx(rw: Double, rh: Double, pageW: Int, pageH: Int, len: Int): Double {
        val area = (rw * 640.0) * (rh * pageH * (640.0 / pageW))
        return (sqrt(area / max(6, len)) * CROP_FONT_SCALE).coerceIn(CROP_FONT_MIN, CROP_FONT_MAX)
    }

    /**
     * `fitBubbleText`: shrink by `max(0.4, fs·0.08)` per step, at most 30 steps, until [fits]
     * or the floor is reached. [fits] is evaluated in px.
     */
    fun fit(startPx: Double, minPx: Double, fits: (Double) -> Boolean): Double {
        var fs = startPx
        var i = 0
        while (i < 30 && fs > minPx && !fits(fs)) { fs -= max(0.4, fs * 0.08); i++ }
        return max(fs, minPx)
    }

    /** Fits [text] into a maxW×maxH px box with [measurer]; returns the font size in px. */
    fun fitText(measurer: TextMeasurer, density: Density, text: String, style: TextStyle, maxWpx: Int, maxHpx: Int, startPx: Double, minPx: Double): Double =
        fit(startPx, minPx) { px ->
            val sp = with(density) { px.toFloat().toSp() }
            val r = measurer.measure(text, style.copy(fontSize = sp, lineHeight = (sp.value * 1.05).sp), overflow = TextOverflow.Clip, softWrap = true, constraints = Constraints(maxWidth = max(1, maxWpx)))
            !r.didOverflowWidth && r.size.height <= maxHpx + 1 && r.size.width <= maxWpx + 1
        }
}
