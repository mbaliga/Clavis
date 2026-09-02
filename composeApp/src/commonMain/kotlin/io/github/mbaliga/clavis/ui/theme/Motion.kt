package io.github.mbaliga.clavis.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion discipline (template-spec §11.4 + D-11, Hyle's "weight, not bounce"): ordinary
 * transitions use the standard curve at 280–320 ms; the Learn ↔ Cosmos ↔ Paths change is the
 * one long move (520 ms). Everything here is suppressed when [LocalReducedMotion] is true —
 * `settings.motion == false` or the platform's reduced-motion flag.
 */
object ClavisMotion {
    /** cubic-bezier(.4,0,.2,1). */
    val Standard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    /** The morph's ease: `u<0.5 ? 4u³ : 1 − (−2u+2)³/2`. */
    val Morph: Easing = Easing { u ->
        if (u < 0.5f) 4f * u * u * u else 1f - ((-2f * u + 2f) * (-2f * u + 2f) * (-2f * u + 2f)) / 2f
    }
    const val SHORT_MS = 150
    const val STANDARD_MS = 300
    const val MORPH_MS = 520
    const val TOAST_MS = 2200
    const val REF_HIGHLIGHT_MS = 1400
    const val HINT_MS = 5000
    const val CAPROW_LIT_MS = 1600
    const val BPULSE_MS = 2400
    const val BHALO_MS = 2800
}

/** True when animation should be replaced by an instant cut (product-spec §8.6). */
val LocalReducedMotion = staticCompositionLocalOf { false }
