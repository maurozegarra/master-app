package com.maurozegarra.master.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.maurozegarra.master.MainActivity
import com.maurozegarra.master.R
import com.maurozegarra.master.i18n.I18n
import java.util.UUID

/**
 * El timer con Android (TD-195): su alarma, su notificación y qué pasa al terminar.
 *
 * Cada timer programa una alarma del sistema para su hora de fin, como el despertador: suena
 * aunque el teléfono duerma o el app esté cerrado. Si suena como alarma va con setAlarmClock,
 * que Android respeta siempre y que deja abrir la pantalla completa; si solo pita, con una
 * exacta. La cuenta regresiva de la notificación la lleva el cronómetro del sistema: no hace
 * falta un servicio vivo para que avance.
 */
object TimerAlarm {

    private const val CHANNEL_LIVE = "timer_live"
    private const val CHANNEL_DONE = "timer_done"
    const val CHANNEL_RING = "timer_ring"
    const val NOTIF_RING = 4301
    private const val NOTIF_BASE = 4400

    fun start(context: Context, name: String, durationMs: Long, alarm: Boolean) {
        val store = TimerStore(context)
        val run = TimerPlan.start(UUID.randomUUID().toString(), name, durationMs, alarm, System.currentTimeMillis())
        store.saveRuns(store.runs() + run)
        store.saveRecents(TimerPlan.addRecent(store.recents(), RecentTimer(run.name, durationMs, alarm)))
        schedule(context, run)
    }

    fun pause(context: Context, id: String) = update(context, id) { TimerPlan.pause(it, System.currentTimeMillis()) }

    fun resume(context: Context, id: String) = update(context, id) { TimerPlan.resume(it, System.currentTimeMillis()) }

    fun restart(context: Context, id: String) = update(context, id) { TimerPlan.restart(it) }

    fun cancel(context: Context, id: String) {
        val store = TimerStore(context)
        val run = store.runs().firstOrNull { it.id == id } ?: return
        store.saveRuns(store.runs().filter { it.id != id })
        unschedule(context, run)
    }

    private fun update(context: Context, id: String, change: (TimerRun) -> TimerRun) {
        val store = TimerStore(context)
        val runs = store.runs()
        val run = runs.firstOrNull { it.id == id } ?: return
        val nuevo = change(run)
        store.saveRuns(runs.map { if (it.id == id) nuevo else it })
        if (nuevo.paused) {
            context.getSystemService(AlarmManager::class.java).cancel(firePending(context, nuevo))
            notifyLive(context, nuevo)
        } else {
            schedule(context, nuevo)
        }
    }

    /**
     * Terminó [id]. Se mira otra vez lo guardado: un timer cancelado, en pausa o que se movió
     * (siguió tras una pausa) no suena por una alarma vieja.
     */
    fun fire(context: Context, id: String): TimerRun? {
        val store = TimerStore(context)
        val run = store.runs().firstOrNull { it.id == id } ?: return null
        if (run.paused || run.endAt > System.currentTimeMillis() + 2_000) return null
        store.saveRuns(store.runs().filter { it.id != id })
        context.getSystemService(NotificationManager::class.java).cancel(notifId(run))
        if (run.alarm) {
            TimerRingService.start(context, run.name)
        } else {
            notifyDone(context, run)
        }
        return run
    }

    /** "Snooze" en la pantalla de alarma: el mismo timer, cinco minutos más. */
    fun snooze(context: Context, name: String) {
        TimerRingService.stop(context)
        val store = TimerStore(context)
        val run = TimerPlan.start(UUID.randomUUID().toString(), name, TimerPlan.SNOOZE_MS, true, System.currentTimeMillis())
        store.saveRuns(store.runs() + run)
        schedule(context, run)
    }

    fun dismiss(context: Context) = TimerRingService.stop(context)

    /**
     * Al reiniciar el teléfono o actualizar el app, Android olvida las alarmas: se vuelven a
     * poner. Uno que terminó con el teléfono apagado suena ahora, que es cuando se puede.
     */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        TimerStore(context).runs().forEach { run ->
            when {
                run.paused -> notifyLive(context, run)
                run.endAt <= now -> fire(context, run.id)
                else -> schedule(context, run)
            }
        }
    }

    private fun schedule(context: Context, run: TimerRun) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = firePending(context, run)
        runCatching {
            if (run.alarm) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(run.endAt, openPending(context)), pi)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, run.endAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, run.endAt, pi)
            }
        }
        notifyLive(context, run)
    }

    private fun unschedule(context: Context, run: TimerRun) {
        context.getSystemService(AlarmManager::class.java).cancel(firePending(context, run))
        context.getSystemService(NotificationManager::class.java).cancel(notifId(run))
    }

    // ---------- Notificaciones ----------

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val w = I18n.EN.more.timer
        // La cuenta, sin sonido: solo informa.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LIVE, w.channelLive, NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null); enableVibration(false); setShowBadge(false)
            },
        )
        // "Terminó": se ve arriba, pero el sonido son los pitidos de MASTER (7-oct).
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, w.channelDone, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false)
            },
        )
        // La alarma: el sonido lo pone el servicio, en el volumen de ALARMA.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RING, w.channelRing, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false)
            },
        )
    }

    private fun title(run: TimerRun): String = run.name.ifBlank { I18n.EN.more.timer.timer }

    /**
     * La cuenta regresiva. Con Android 16 se pide como Live Update, igual que la del player:
     * así sale en la Now Bar y en el chip de la barra de estado con el cronómetro.
     */
    private fun notifyLive(context: Context, run: TimerRun) {
        ensureChannels(context)
        val w = I18n.EN.more.timer
        val zone = java.time.ZoneId.systemDefault()
        val b = Notification.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_notif_timer)
            .setContentTitle(title(run))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(if (run.alarm) Notification.CATEGORY_ALARM else Notification.CATEGORY_STOPWATCH)
            .setContentIntent(openPending(context))
        if (run.paused) {
            b.setContentText(w.pausedLeft.format(TimerPlan.format(run.pausedLeftMs ?: 0)))
                .setShowWhen(false)
                .addAction(action(context, run, TimerReceiver.ACTION_RESUME, w.resume))
        } else {
            b.setContentText((if (run.alarm) w.ringsAt else w.endsAt).format(TimerPlan.clock(run.endAt, zone)))
                .setWhen(run.endAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(action(context, run, TimerReceiver.ACTION_PAUSE, w.pause))
        }
        b.addAction(action(context, run, TimerReceiver.ACTION_CANCEL, w.cancel))
        if (Build.VERSION.SDK_INT >= 36) {
            b.setRequestPromotedOngoing(true)
            if (run.paused) b.setShortCriticalText(TimerPlan.format(run.pausedLeftMs ?: 0))
        }
        context.getSystemService(NotificationManager::class.java).notify(notifId(run), b.build())
    }

    private fun notifyDone(context: Context, run: TimerRun) {
        ensureChannels(context)
        val n = Notification.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notif_timer)
            .setContentTitle(title(run))
            .setContentText(I18n.EN.more.timer.done)
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .setContentIntent(openPending(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(notifId(run), n)
    }

    private fun notifId(run: TimerRun): Int = NOTIF_BASE + (run.id.hashCode() and 0x3FFF)

    private fun firePending(context: Context, run: TimerRun): PendingIntent = PendingIntent.getBroadcast(
        context, run.id.hashCode(),
        Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_FIRE).putExtra(TimerReceiver.EXTRA_ID, run.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, run: TimerRun, act: String, label: String): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            context, (run.id + act).hashCode(),
            Intent(context, TimerReceiver::class.java).setAction(act).putExtra(TimerReceiver.EXTRA_ID, run.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(null as android.graphics.drawable.Icon?, label, pi).build()
    }

    fun openPending(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 4300,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
