package io.github.mbaliga.clavis.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import io.github.mbaliga.clavis.MainActivity
import io.github.mbaliga.clavis.R
import io.github.mbaliga.clavis.core.widget.CardFace
import io.github.mbaliga.clavis.core.widget.Face
import io.github.mbaliga.clavis.core.widget.ListFace
import io.github.mbaliga.clavis.core.widget.PadsFace
import io.github.mbaliga.clavis.core.widget.TileFace
import io.github.mbaliga.clavis.core.widget.WidgetAction
import io.github.mbaliga.clavis.core.widget.WidgetRender
import io.github.mbaliga.clavis.core.widget.WidgetSize
import io.github.mbaliga.clavis.core.widget.WidgetTap

/**
 * Binds faces to the widget layouts. No decisions are made here — what to show, at which size,
 * in which words, is `WidgetFaces` in :shared — only which view holds which string, and where
 * each tap goes.
 */
internal object WidgetViews {
    private val ROWS = intArrayOf(R.id.widget_row1, R.id.widget_row2, R.id.widget_row3, R.id.widget_row4, R.id.widget_row5)
    private val ROW_START = intArrayOf(R.id.widget_row1_start, R.id.widget_row2_start, R.id.widget_row3_start, R.id.widget_row4_start, R.id.widget_row5_start)
    private val ROW_END = intArrayOf(R.id.widget_row1_end, R.id.widget_row2_end, R.id.widget_row3_end, R.id.widget_row4_end, R.id.widget_row5_end)
    private val ROW_SWATCH = intArrayOf(R.id.widget_row1_swatch, R.id.widget_row2_swatch, R.id.widget_row3_swatch, R.id.widget_row4_swatch, R.id.widget_row5_swatch)
    private val LINES = intArrayOf(R.id.widget_line1, R.id.widget_line2, R.id.widget_line3)
    private val PADS = intArrayOf(R.id.widget_pad1, R.id.widget_pad2, R.id.widget_pad3, R.id.widget_pad4, R.id.widget_pad5, R.id.widget_pad6)
    private val PAD_TITLE = intArrayOf(R.id.widget_pad1_title, R.id.widget_pad2_title, R.id.widget_pad3_title, R.id.widget_pad4_title, R.id.widget_pad5_title, R.id.widget_pad6_title)
    private val PAD_SUB = intArrayOf(R.id.widget_pad1_sub, R.id.widget_pad2_sub, R.id.widget_pad3_sub, R.id.widget_pad4_sub, R.id.widget_pad5_sub, R.id.widget_pad6_sub)
    private val PAD_SWATCH = intArrayOf(R.id.widget_pad1_swatch, R.id.widget_pad2_swatch, R.id.widget_pad3_swatch, R.id.widget_pad4_swatch, R.id.widget_pad5_swatch, R.id.widget_pad6_swatch)

    /** The root of every layout: `@android:id/background`, so Android 12+ clips it to the launcher's radius. */
    private const val ROOT = android.R.id.background

    /** Android 12+: one layout per size class; the launcher picks the largest that fits without waking the app. */
    @RequiresApi(31)
    fun sized(ctx: Context, id: Int, r: WidgetRender): RemoteViews =
        RemoteViews(WidgetSize.entries.associate { SizeF(it.minWidthDp, it.minHeightDp) to bind(ctx, id, it, r.face(it)) })

    /** Before Android 12: the one layout for the size the provider measured. */
    fun single(ctx: Context, id: Int, r: WidgetRender, size: WidgetSize): RemoteViews = bind(ctx, id, size, r.face(size))

    private fun bind(ctx: Context, id: Int, size: WidgetSize, face: Face): RemoteViews {
        val taps = Taps(ctx, id, size)
        val rv = when (face) {
            is TileFace -> if (size == WidgetSize.TINY) tile(ctx, face) else tileTall(ctx, face)
            is CardFace -> if (size == WidgetSize.LARGE) cardLarge(ctx, face, taps) else cardMedium(ctx, face, taps)
            is ListFace -> if (size == WidgetSize.LARGE) list(ctx, face, R.layout.widget_list_large, 5) else list(ctx, face, R.layout.widget_list_medium, 3)
            is PadsFace -> pads(ctx, face, size, taps)
        }
        rv.setContentDescription(ROOT, face.description)
        rv.setOnClickPendingIntent(ROOT, taps.of(face.tap, slot = 0))
        return rv
    }

    private fun tile(ctx: Context, f: TileFace) = RemoteViews(ctx.packageName, R.layout.widget_tile).apply {
        setTextViewText(R.id.widget_value, f.compact)
    }

    private fun tileTall(ctx: Context, f: TileFace) = RemoteViews(ctx.packageName, R.layout.widget_tile_tall).apply {
        text(R.id.widget_label, f.label)
        text(R.id.widget_value, f.value)
        text(R.id.widget_caption, f.caption)
    }

    private fun cardMedium(ctx: Context, f: CardFace, taps: Taps) = RemoteViews(ctx.packageName, R.layout.widget_card_medium).apply {
        text(R.id.widget_label, f.label)
        text(R.id.widget_title, f.title)
        text(R.id.widget_meta, f.meta)
        action(R.id.widget_primary, f.primary, taps, slot = 1)
    }

    private fun cardLarge(ctx: Context, f: CardFace, taps: Taps) = RemoteViews(ctx.packageName, R.layout.widget_card_large).apply {
        text(R.id.widget_label, f.label)
        text(R.id.widget_title, f.title)
        text(R.id.widget_body, f.body)
        text(R.id.widget_meta, f.meta)
        LINES.forEachIndexed { i, v -> text(v, f.lines.getOrNull(i)) }
        text(R.id.widget_footer, f.footer)
        action(R.id.widget_primary, f.primary, taps, slot = 1)
        action(R.id.widget_secondary, f.secondary, taps, slot = 2)
    }

    private fun list(ctx: Context, f: ListFace, layout: Int, rows: Int) = RemoteViews(ctx.packageName, layout).apply {
        text(R.id.widget_label, f.label)
        text(R.id.widget_headline, f.headline)
        if (layout == R.layout.widget_list_large) text(R.id.widget_caption, f.caption)
        for (i in 0 until rows) {
            val row = f.rows.getOrNull(i)
            setViewVisibility(ROWS[i], if (row == null) View.GONE else View.VISIBLE)
            if (row == null) continue
            setTextViewText(ROW_START[i], row.start)
            setTextViewText(ROW_END[i], row.end)
            swatch(ROW_SWATCH[i], row.swatch)
        }
        text(R.id.widget_footer, f.footer)
    }

    private fun pads(ctx: Context, f: PadsFace, size: WidgetSize, taps: Taps): RemoteViews {
        val (layout, n) = when (size) {
            WidgetSize.TINY, WidgetSize.SMALL -> R.layout.widget_pads_small to 2
            WidgetSize.MEDIUM -> R.layout.widget_pads_medium to 4
            WidgetSize.LARGE -> R.layout.widget_pads_large to 6
        }
        return RemoteViews(ctx.packageName, layout).apply {
            for (i in 0 until n) {
                val p = f.pads.getOrNull(i)
                setViewVisibility(PADS[i], if (p == null) View.GONE else View.VISIBLE)
                if (p == null) continue
                setTextViewText(PAD_TITLE[i], p.title)
                setTextViewText(PAD_SUB[i], p.sub)
                swatch(PAD_SWATCH[i], p.swatch)
                setContentDescription(PADS[i], p.description)
                setOnClickPendingIntent(PADS[i], taps.of(p.tap, slot = 3 + i))
            }
        }
    }

    private fun RemoteViews.text(id: Int, s: String?) {
        if (s.isNullOrEmpty()) {
            setViewVisibility(id, View.GONE)
        } else {
            setViewVisibility(id, View.VISIBLE)
            setTextViewText(id, s)
        }
    }

    /** A lane's identity colour on its dot; no dot when there is no lane. The name beside it carries the meaning. */
    private fun RemoteViews.swatch(id: Int, argb: Long?) {
        if (argb == null) {
            setViewVisibility(id, View.GONE)
        } else {
            setViewVisibility(id, View.VISIBLE)
            setInt(id, "setColorFilter", (argb and 0xFFFFFFFFL).toInt())
        }
    }

    private fun RemoteViews.action(id: Int, a: WidgetAction?, taps: Taps, slot: Int) {
        if (a == null) {
            setViewVisibility(id, View.GONE)
            return
        }
        setViewVisibility(id, View.VISIBLE)
        setTextViewText(id, a.label)
        setContentDescription(id, a.description)
        setOnClickPendingIntent(id, taps.of(a.tap, slot))
    }

    /**
     * Activity PendingIntents for one widget at one size. Request codes are unique per widget id,
     * size class and slot: the intents differ only in their extras, which PendingIntent identity
     * ignores, so a shared code would let one tap's extras overwrite another's.
     */
    private class Taps(private val ctx: Context, private val id: Int, private val size: WidgetSize) {
        fun of(tap: WidgetTap, slot: Int): PendingIntent {
            val code = (id * WidgetSize.entries.size + size.ordinal) * SLOTS + slot
            return PendingIntent.getActivity(ctx, code, WidgetIntents.open(ctx, tap), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    /** Root, two buttons and six pads. */
    private const val SLOTS = 16
}

/** The explicit activity intent a widget tap carries, and its validated reading in [MainActivity]. */
object WidgetIntents {
    /** Not in any intent filter: the intent is explicit, the action only marks where it came from. */
    const val ACTION_OPEN = "io.github.mbaliga.clavis.action.OPEN_FROM_WIDGET"
    private const val EXTRA_PREFIX = "io.github.mbaliga.clavis.widget."

    fun open(ctx: Context, tap: WidgetTap): Intent {
        val i = Intent(ctx, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            // One MainActivity, one store, one document: bring the existing task forward and deliver
            // to onNewIntent rather than stacking a second activity with its own copy of the state.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        for ((k, v) in tap.fields()) i.putExtra(EXTRA_PREFIX + k, v)
        return i
    }

    /** The tap [intent] carries, or null when it is not a widget tap, is malformed, or is replayed from Recents. */
    fun tapOf(intent: Intent?): WidgetTap? {
        if (intent?.action != ACTION_OPEN) return null
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return WidgetTap.parse { k -> intent.getStringExtra(EXTRA_PREFIX + k) }
    }
}
