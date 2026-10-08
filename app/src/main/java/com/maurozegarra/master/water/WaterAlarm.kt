package com.maurozegarra.master.water

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.maurozegarra.master.R
import com.maurozegarra.master.i18n.glassesWord
import com.maurozegarra.master.morning.MorningLog
import com.maurozegarra.master.morning.MorningStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Anotar y recordar el agua (TD-190). La regla está en [WaterPlan]; aquí, de dónde salen los
 * datos (la alarma de la mañana, el aviso para dormir) y AlarmManager.
 *
 * El recordatorio se reprograma cada vez que algo cambia: al anotar, al borrar, al contestar
 * la alarma (empieza el día) y al reiniciar. Así nunca avisa con datos viejos.
 */
object WaterAlarm {

    const val CHANNEL = "water_remind"
    const val NOTIF = 4201
    private const val REQ_REMIND = 51

    /** El día de hoy: cuándo empezó, cuándo corta y lo tomado. */
    data class Today(
        val day: LocalDate,
        val logs: List<WaterLog>,
        val start: Long?,
        val cut: Long,
        /** El horario del día: el de casa o el presencial, según la alarma (ver [WaterPlan.planFor]). */
        val slots: List<Pair<Long, Int>>,
        val office: Boolean,
    )

    fun today(context: Context, now: Long = System.currentTimeMillis()): Today {
        val zone = ZoneId.systemDefault()
        val day = WaterPlan.dayOf(now, zone)
        val logs = WaterPlan.logsOf(WaterStore(context).logs(), day, zone)
        val morning = MorningStore(context)
        // Contestar la alarma es despertar (TD-151). Una prueba de noche no cuenta.
        val wake = MorningLog.forDay(morning.entries(), now, zone)
            ?.takeIf { MorningLog.isMorning(it, zone) }?.answeredAt
        // La alarma de mañana menos las horas de sueño del aviso para dormir (TD-160).
        val manana = morning.schedule().next(day.atTime(12, 0).atZone(zone), morning.skipDate)
        val bed = WaterPlan.bedFromNextRing(manana, day, morning.bedtime().sleepMin)
        // La alarma de hoy dice si es dia presencial: antes de las 6 (decidido con el, 4-oct).
        // No el training corto: ese va tambien los domingos.
        // Un feriado suena como sábado (7-oct), así que tampoco es presencial.
        val alarmaHoy = morning.schedule().alarmsOn(day)
            .filter { morning.skipDate != day }
            .minOfOrNull { it.time }
        val plan = WaterPlan.planFor(alarmaHoy)
        return Today(
            day, logs, WaterPlan.start(wake, logs), WaterPlan.cut(bed, day, zone),
            WaterPlan.slotsOn(plan, day, zone), plan === WaterPlan.OFFICE,
        )
    }

    fun add(context: Context, ml: Int) {
        val store = WaterStore(context)
        val zone = ZoneId.systemDefault()
        store.saveLogs(WaterPlan.add(store.logs(), WaterLog(System.currentTimeMillis(), ml), LocalDate.now(zone), zone))
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF)
        reschedule(context)
    }

    /** Una toma con su hora, su bebida y su cantidad: lo de antes, o corregir una. */
    fun put(context: Context, entry: WaterLog, replacing: Long? = null) {
        val store = WaterStore(context)
        val zone = ZoneId.systemDefault()
        val base = if (replacing != null) WaterPlan.remove(store.logs(), replacing) else store.logs()
        store.saveLogs(WaterPlan.add(base, entry, LocalDate.now(zone), zone))
        reschedule(context)
    }

    fun remove(context: Context, at: Long) {
        val store = WaterStore(context)
        store.saveLogs(WaterPlan.remove(store.logs(), at))
        reschedule(context)
    }

    /** El próximo recordatorio de hoy, si hay. */
    fun next(context: Context): Long? {
        val now = System.currentTimeMillis()
        val t = today(context, now)
        return WaterPlan.nextReminder(now, t.logs, t.start, t.cut, t.slots)
    }

    /**
     * Programa el próximo recordatorio o lo cancela. setExactAndAllowWhileIdle, como el aviso
     * para dormir: no es un despertador, pero tiene que llegar a su hora.
     */
    fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = remindPending(context)
        val at = next(context)
        if (at == null) {
            am.cancel(pi)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    /**
     * El recordatorio: cuánto lleva y cuánto va atrasado, con +200 y +600 para anotar sin
     * abrir el app. Antes de enseñarlo se vuelve a mirar: si mientras tanto tomó, ya no toca.
     */
    fun remind(context: Context) {
        val now = System.currentTimeMillis()
        val t = today(context, now)
        val toca = WaterPlan.nextReminder(now, t.logs, t.start, t.cut, t.slots)
        if (toca == null || toca > now + 60_000) {
            reschedule(context)
            return
        }
        ensureChannel(context)
        val w = com.maurozegarra.master.i18n.I18n.EN.more.water
        val tomado = WaterPlan.total(t.logs)
        val atras = WaterPlan.behind(now, t.logs, t.start, t.slots) ?: 0
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif_water)
            .setContentTitle(w.remindTitle)
            .setContentText(w.remindText.format(tomado, WaterPlan.GOAL_ML, WaterPlan.glasses(atras.coerceAtLeast(0)).let { "$it ${w.glassesWord(it)}" }))
            .setAutoCancel(true)
            .setContentIntent(openPending(context))
            .addAction(0, "+${WaterPlan.GLASS_ML} ml", addPending(context, WaterPlan.GLASS_ML))
            .addAction(0, "+${WaterPlan.BOTTLE_ML} ml", addPending(context, WaterPlan.BOTTLE_ML))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF, n)
        // El siguiente no se programa aquí: sale al anotar. Si no anota, se vuelve a mirar en
        // media hora, que es el mínimo entre recordatorios.
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
        val otra = now + WaterPlan.MIN_GAP_MIN * 60_000
        if (otra < t.cut) runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, otra, remindPending(context)) }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val w = com.maurozegarra.master.i18n.I18n.EN.more.water
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, w.channel, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun remindPending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_REMIND,
        Intent(context, WaterReceiver::class.java).setAction(WaterReceiver.ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun addPending(context: Context, ml: Int): PendingIntent = PendingIntent.getBroadcast(
        context, 60 + ml / 100,
        Intent(context, WaterReceiver::class.java).setAction(WaterReceiver.ACTION_ADD).putExtra(WaterReceiver.EXTRA_ML, ml),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openPending(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 59,
        Intent(context, WaterHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Para la pantalla: la hora del próximo recordatorio, o null. */
    fun nextAt(context: Context): ZonedDateTime? =
        next(context)?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
}
