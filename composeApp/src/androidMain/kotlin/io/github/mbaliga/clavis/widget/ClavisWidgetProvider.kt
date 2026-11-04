package io.github.mbaliga.clavis.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.mbaliga.clavis.core.widget.WidgetKind

/**
 * One home-screen widget. Every redraw reads the published snapshot on [ClavisWidgets.executor]
 * inside `goAsync()`, so the broadcast never blocks the main thread and finishes in milliseconds.
 * Exported (the launcher binds it); it changes nothing but its own RemoteViews, whatever sends it
 * an update.
 */
abstract class ClavisWidgetProvider : AppWidgetProvider() {
    abstract val kind: WidgetKind

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderAsync(context, appWidgetIds)
    }

    /** A resize: below Android 12 the layout is chosen here from the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        renderAsync(context, intArrayOf(appWidgetId))
    }

    /**
     * The last widget of this kind was removed. When it was the last Clavis widget of any kind, the
     * snapshot is deleted and the redraw alarm dropped (PRIVACY.md); otherwise the others keep both.
     */
    override fun onDisabled(context: Context) {
        val app = context.applicationContext
        if (!ClavisWidgets.anyPlaced(app)) ClavisWidgets.forget(app)
    }

    /** Dates and times are printed in the device's zone and locale; redraw when either changes. */
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> {
                val ids = AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, javaClass)) ?: return
                if (ids.isNotEmpty()) renderAsync(context, ids)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun renderAsync(context: Context, ids: IntArray) {
        val app = context.applicationContext
        val pending = goAsync()
        ClavisWidgets.executor.execute {
            try {
                ClavisWidgets.render(app, kind, ids)
            } catch (e: Exception) {
                // Nothing to report to (D-13: no crash upload). The launcher keeps the last face it had.
            } finally {
                pending.finish()
            }
        }
    }
}

/** "Word due": the first word ready to return, as a gapped line; a tap opens the review (read-only). */
class WordDueWidget : ClavisWidgetProvider() {
    override val kind = WidgetKind.WORD_DUE
}

/** "The pool": due or still, in words, per language. */
class PoolWidget : ClavisWidgetProvider() {
    override val kind = WidgetKind.POOL
}

/** "Your record": words held and bubbles readable per language, as of the last publish. */
class RecordWidget : ClavisWidgetProvider() {
    override val kind = WidgetKind.RECORD
}

/** "Last dip": the last session's numbers. */
class LastDipWidget : ClavisWidgetProvider() {
    override val kind = WidgetKind.LAST_DIP
}

/** "Word origins": one word a day with its recorded origin. */
class OriginsWidget : ClavisWidgetProvider() {
    override val kind = WidgetKind.ORIGINS
}

/** "Your languages": one shortcut per active lane. */
class LanguagesWidget : ClavisWidgetProvider() {
    override val kind = WidgetKind.LANGUAGES
}

/**
 * The redraw alarm's target (not exported: only this app's own immutable PendingIntent reaches
 * it). Asks every placed widget to redraw; each then schedules the next alarm.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ClavisWidgets.refreshAll(context.applicationContext)
    }
}
