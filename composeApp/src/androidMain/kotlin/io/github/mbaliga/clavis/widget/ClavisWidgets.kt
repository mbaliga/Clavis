package io.github.mbaliga.clavis.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
import io.github.mbaliga.clavis.core.widget.FaceClock
import io.github.mbaliga.clavis.core.widget.Glance
import io.github.mbaliga.clavis.core.widget.WidgetFaces
import io.github.mbaliga.clavis.core.widget.WidgetKind
import io.github.mbaliga.clavis.core.widget.WidgetSize
import io.github.mbaliga.clavis.core.widget.WidgetSnapshots
import io.github.mbaliga.clavis.store.ClavisStore
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/*
 * The Android side of the home-screen widgets (docs/widgets.md). The learner document has one
 * owner, the store on the UI thread, so the widgets never touch it: when the activity stops while a
 * widget is placed, the app writes a small snapshot (`WidgetSnapshots.build`) under noBackupFilesDir
 * and asks the launcher to redraw; removing the last widget deletes it. Each provider then reads that file on a background thread, turns it into
 * faces (`WidgetFaces`, unit-tested in :shared) and binds them to RemoteViews (`WidgetViews`).
 * Nothing here opens a socket, holds a wake lock or asks for a permission.
 */
object ClavisWidgets {
    private const val DIR = "widgets"
    private const val FILE = "glance.json"

    /** Every provider, in the order the picker lists them. */
    val providers: List<Class<out ClavisWidgetProvider>> = listOf(
        WordDueWidget::class.java,
        PoolWidget::class.java,
        RecordWidget::class.java,
        LastDipWidget::class.java,
        OriginsWidget::class.java,
        LanguagesWidget::class.java,
    )

    /** One background thread for every widget render, so two redraws never interleave their file reads. */
    internal val executor: ExecutorService by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "clavis-widgets") } }

    /**
     * The snapshot lives under noBackupFilesDir: learner state stays out of cloud backup (PRIVACY.md)
     * and, unlike `clavis-state.json`, out of device-to-device transfer too — the new phone's app
     * writes its own the first time it runs.
     */
    fun snapshotFile(ctx: Context): File = File(File(ctx.noBackupFilesDir, DIR), FILE)

    /**
     * Publishes what the widgets show from the live [store], then asks every placed widget to
     * redraw. Call on the UI thread — the store's only mutator — when the app leaves the
     * foreground (`MainActivity.onStop`). With no widget placed nothing is written, and a snapshot
     * left from earlier is deleted (PRIVACY.md); a newly placed widget shows its first-run text
     * until the app is next left. A failure keeps the previous snapshot, whose "as of" time stays true.
     */
    fun publish(ctx: Context, store: ClavisStore) {
        if (!store.ready) return
        if (!anyPlaced(ctx)) {
            forget(ctx)
            return
        }
        runCatching {
            val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            val previous = (read(ctx) as? Glance.Ready)?.snapshot
            val next = WidgetSnapshots.keepTodaysOrigin(WidgetSnapshots.build(store.engine, store.now), previous, today)
            write(ctx, WidgetSnapshots.encode(next))
        }
        refreshAll(ctx)
    }

    /** True while at least one Clavis widget is on a home screen. */
    fun anyPlaced(ctx: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return false
        return providers.any { mgr.getAppWidgetIds(ComponentName(ctx, it)).isNotEmpty() }
    }

    /** No widget is placed any more: delete the snapshot and drop the redraw alarm. */
    fun forget(ctx: Context) {
        val file = snapshotFile(ctx)
        file.delete()
        File(file.parentFile, "$FILE.tmp").delete()
        alarms(ctx)?.cancel(refreshIntent(ctx))
    }

    /** Temp file, then rename over the previous one, like the learner document (D-12). */
    private fun write(ctx: Context, text: String) {
        val file = snapshotFile(ctx)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "$FILE.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    /** What is on disk: nothing yet, something unreadable, or a snapshot. */
    fun read(ctx: Context): Glance {
        val file = snapshotFile(ctx)
        val text = runCatching { if (file.exists()) file.readText() else null }.getOrElse { "" } // an I/O error reads as unreadable
        return Glance.of(text)
    }

    /** Asks each provider with placed widgets to redraw them (an explicit broadcast to our own receivers). */
    fun refreshAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        var any = false
        for (cls in providers) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isEmpty()) continue
            any = true
            ctx.sendBroadcast(
                Intent(ctx, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
        if (!any) alarms(ctx)?.cancel(refreshIntent(ctx))
    }

    /** Draws [ids] of one [kind] now. Runs on [executor]; reads one small file and binds RemoteViews. */
    internal fun render(ctx: Context, kind: WidgetKind, ids: IntArray) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        val glance = read(ctx)
        val now = System.currentTimeMillis()
        val clock = FaceClock(
            now, LocalDate.now(ZoneId.systemDefault()).toEpochDay(),
            date = { t -> formatDate(ctx, t) }, time = { t -> formatTime(ctx, t) },
        )
        val faces = WidgetFaces.render(kind, glance, clock)
        for (id in ids) {
            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WidgetViews.sized(ctx, id, faces)
            } else {
                WidgetViews.single(ctx, id, faces, sizeOf(mgr, id))
            }
            mgr.updateAppWidget(id, views)
        }
        scheduleNext(ctx, glance, now)
    }

    /**
     * Below Android 12 the provider picks the layout itself: the portrait size is min width × max
     * height (the documented convention), re-read in `onAppWidgetOptionsChanged` after a resize.
     */
    private fun sizeOf(mgr: AppWidgetManager, id: Int): WidgetSize {
        val o = mgr.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (w <= 0 || h <= 0) return WidgetSize.MEDIUM // the default size, before the host reports one
        return WidgetSize.pick(w.toFloat(), h.toFloat())
    }

    /** A date and a time, in the device's locale: an "as of" stays true even when nothing redraws it. */
    private fun formatTime(ctx: Context, t: Long): String =
        DateUtils.formatDateTime(ctx, t, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)

    /** A date alone, for the one-line tile. */
    private fun formatDate(ctx: Context, t: Long): String =
        DateUtils.formatDateTime(ctx, t, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)

    private fun alarms(ctx: Context): AlarmManager? = ctx.getSystemService(AlarmManager::class.java)

    private fun refreshIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, WidgetRefreshReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The one redraw time alone requires: the next card falling due, or local midnight when a
     * word-origin widget is placed. An inexact, non-waking alarm (`AlarmManager.set` with RTC): it
     * may run late under Doze, and an OEM battery manager may drop it — the faces print dates, not
     * "today", so a late redraw is never wrong, only late. No exact-alarm permission.
     */
    private fun scheduleNext(ctx: Context, glance: Glance, now: Long) {
        val am = alarms(ctx) ?: return
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        val pi = refreshIntent(ctx)
        if (!anyPlaced(ctx)) {
            am.cancel(pi)
            return
        }
        val origins = mgr.getAppWidgetIds(ComponentName(ctx, OriginsWidget::class.java)).isNotEmpty()
        val zone = ZoneId.systemDefault()
        val midnight = if (origins) LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() else null
        val at = WidgetFaces.nextRefreshAt(glance, now, midnight)
        if (at == null) am.cancel(pi) else am.set(AlarmManager.RTC, maxOf(at, now + MIN_ALARM_GAP_MS), pi)
    }

    /** Never ask for a redraw sooner than this, so a boundary that is already past cannot spin. */
    private const val MIN_ALARM_GAP_MS = 60_000L
}
