package com.maurozegarra.master.morning

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.maurozegarra.master.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Programar, sonar, posponer y anotar (TD-151).
 *
 * **setAlarmClock y no set/setExact.** Es la API de los despertadores: suena en hora
 * aunque el teléfono esté en reposo profundo y enseña el reloj en la barra de estado. Esta
 * alarma REEMPLAZA a su despertador: un minuto de retraso por ahorro de batería sería
 * quedarse dormido. Pide permiso de alarma exacta (ver el manifiesto): la primera versión
 * creyó que no, y el app se caía al encenderla.
 */
object MorningAlarm {

    const val CHANNEL_RING = "morning_ring"
    const val CHANNEL_EASE = "morning_ease"
    const val NOTIF_RING = 4101
    const val NOTIF_EASE = 4102
    const val SNOOZE_MIN = 5L

    private const val REQ_FIRE = 41
    private const val REQ_SHOW = 42

    /**
     * Programa la siguiente según el horario, o la cancela si está apagada.
     *
     * Devuelve false si Android no dejó programarla -sin permiso de alarma exacta-, para que
     * Settings lo diga en vez de dar por puesta una alarma que no va a sonar. Nunca se cae:
     * esto corre también al reiniciar el teléfono, donde un fallo no lo vería nadie.
     */
    fun reschedule(context: Context): Boolean {
        val store = MorningStore(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = firePending(context)
        if (!store.enabled) {
            am.cancel(fire)
            return true
        }
        val now = System.currentTimeMillis()
        val pospuesta = store.snoozedUntil.takeIf { it > now }
        val at = pospuesta ?: store.schedule().next(ZonedDateTime.now(ZoneId.systemDefault()))?.toInstant()?.toEpochMilli()
        if (at == null) {
            am.cancel(fire)
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return false
        return runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showPending(context)), fire)
        }.isSuccess
    }

    /** Si Android deja programar alarmas exactas. */
    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Pospone [SNOOZE_MIN] minutos: la pregunta del dolor sale igual al apagarla de verdad. */
    fun snooze(context: Context) {
        MorningStore(context).snoozedUntil = System.currentTimeMillis() + SNOOZE_MIN * 60_000
        stopRinging(context)
        reschedule(context)
    }

    /**
     * Apaga la alarma. Con [pain] anota el dolor y deja la notificación de "cuando afloje";
     * sin él -el Dismiss pequeño- solo la apaga.
     */
    fun dismiss(context: Context, pain: Int?) {
        val store = MorningStore(context)
        store.snoozedUntil = 0L
        stopRinging(context)
        if (pain != null) {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val entry = MorningEntry(MorningLog.dateOf(now, zone), painOnWaking = pain, answeredAt = now)
            store.saveEntries(MorningLog.upsert(store.entries(), entry, LocalDate.now(zone)))
            showEaseNotification(context)
        }
        reschedule(context)
    }

    /**
     * Corrige el dolor de hoy, desde el "Change" de la confirmacion. Conserva la hora en que
     * se contesto: esa es la del despertar, y de ella salen los minutos hasta aflojar.
     */
    fun correct(context: Context, pain: Int) {
        val store = MorningStore(context)
        val zone = ZoneId.systemDefault()
        val hoy = MorningLog.forDay(store.entries(), System.currentTimeMillis(), zone) ?: return
        store.saveEntries(MorningLog.upsert(store.entries(), hoy.copy(painOnWaking = pain), LocalDate.now(zone)))
    }

    /** "Aflojó": anota la hora. Los minutos salen solos de [MorningEntry.fadeMinutes]. */
    fun eased(context: Context) {
        val store = MorningStore(context)
        val zone = ZoneId.systemDefault()
        val hoy = MorningLog.forDay(store.entries(), System.currentTimeMillis(), zone)
        if (hoy != null && hoy.easedAt == null) {
            store.saveEntries(MorningLog.upsert(store.entries(), hoy.copy(easedAt = System.currentTimeMillis()), LocalDate.now(zone)))
        }
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_EASE)
    }

    /** Empieza a sonar: el servicio pone el sonido y la notificación de pantalla completa. */
    fun startRinging(context: Context) {
        val i = Intent(context, MorningRingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i) else context.startService(i)
    }

    fun stopRinging(context: Context) {
        context.stopService(Intent(context, MorningRingService::class.java))
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_RING)
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        // Sin sonido en el canal: lo pone el servicio, en el volumen de ALARMA. El de la
        // notificación seguiría al de notificaciones, que puede estar en silencio de noche.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RING, "Morning alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EASE, "Morning pain", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun showEaseNotification(context: Context) {
        ensureChannels(context)
        val eased = PendingIntent.getBroadcast(
            context, 43,
            Intent(context, MorningReceiver::class.java).setAction(MorningReceiver.ACTION_EASED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_EASE)
            .setSmallIcon(R.drawable.ic_notif_play)
            .setContentTitle("Tap when it eases")
            .setContentText("The pain from waking up")
            .setOngoing(true)
            .setContentIntent(eased)
            .addAction(0, "It eased", eased)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_EASE, n)
    }

    private fun firePending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_FIRE,
        Intent(context, MorningReceiver::class.java).setAction(MorningReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Lo que abre el reloj de la barra de estado: los ajustes de la alarma, en MASTER. */
    private fun showPending(context: Context): PendingIntent = PendingIntent.getActivity(
        context, REQ_SHOW,
        context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
