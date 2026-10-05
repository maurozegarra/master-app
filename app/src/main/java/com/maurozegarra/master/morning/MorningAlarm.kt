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
    const val NOTIF_BED = 4103
    const val CHANNEL_BED = "morning_bed"
    const val SNOOZE_MIN = 5L

    private const val REQ_FIRE = 41
    private const val REQ_SHOW = 42
    private const val REQ_BED = 44

    /**
     * Programa la siguiente según el horario, o la cancela si está apagada.
     *
     * Devuelve false si Android no dejó programarla -sin permiso de alarma exacta-, para que
     * Settings lo diga en vez de dar por puesta una alarma que no va a sonar. Nunca se cae:
     * esto corre también al reiniciar el teléfono, donde un fallo no lo vería nadie.
     */
    fun reschedule(context: Context): Boolean {
        // El aviso para ir a dormir sale de las mismas alarmas: se reprograma con ellas, en
        // todos los caminos, para que nunca apunte a una alarma que ya no existe (TD-160).
        scheduleBedtime(context)
        return rescheduleAlarm(context)
    }

    private fun rescheduleAlarm(context: Context): Boolean {
        val store = MorningStore(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = firePending(context)
        if (!store.schedule().anyOn) {
            am.cancel(fire)
            return true
        }
        val now = System.currentTimeMillis()
        val pospuesta = store.snoozedUntil.takeIf { it > now }
        val at = pospuesta ?: nextRing(context)?.toInstant()?.toEpochMilli()
        if (at == null) {
            am.cancel(fire)
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return false
        return runCatching {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showPending(context)), fire)
        }.isSuccess
    }

    /**
     * La próxima vez que suena según el horario y el día saltado, o null. Lo que enseñan la
     * pantalla de la mañana y la barra de MASTER, y lo que se programa.
     */
    fun nextRing(context: Context): ZonedDateTime? {
        val store = MorningStore(context)
        val horario = store.schedule()
        if (!horario.anyOn) return null
        val zone = ZoneId.systemDefault()
        // Un salto ya pasado no sirve para nada: se olvida para que no confunda.
        store.skipDate?.let { if (it.isBefore(LocalDate.now(zone))) store.skipDate = null }
        return horario.next(ZonedDateTime.now(zone), store.skipDate)
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
            val existente = MorningLog.forDay(store.entries(), now, zone)
            // Una prueba de noche no pisa la mañana de verdad de ese día (ver mayRecord), ni
            // una segunda alarma del mismo día la primera respuesta (TD-175).
            if (MorningLog.mayRecord(existente, now, zone) && !MorningLog.alreadyAnswered(existente, zone)) {
                val entry = MorningLog.answer(existente, MorningLog.dateOf(now, zone), pain, now)
                store.saveEntries(MorningLog.upsert(store.entries(), entry, LocalDate.now(zone)))
                showEaseNotification(context)
                // Despertar empieza el dia del agua (TD-190): sin dia empezado no recuerda.
                com.maurozegarra.master.water.WaterAlarm.reschedule(context)
            }
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
        if (!MorningLog.mayRecord(hoy, System.currentTimeMillis(), zone)) return
        store.saveEntries(MorningLog.upsert(store.entries(), hoy.copy(painOnWaking = pain), LocalDate.now(zone)))
    }

    /**
     * Corrige el dolor de la mañana de [date] a cualquier hora, desde la pantalla Morning
     * (TD-164). A diferencia de [correct], no mira la hora: corregir una mañana de verdad a
     * mediodía es legítimo; lo que no puede es una PRUEBA de noche pisarla.
     *
     * El 25-sep el usuario contestó 1, le pareció "demasiado optimista", y como no había
     * forma de editarlo tuvo que acordarse de cambiarlo en la sesión, horas después.
     */
    fun edit(context: Context, date: LocalDate, pain: Int) {
        val store = MorningStore(context)
        val zone = ZoneId.systemDefault()
        val dia = store.entries().firstOrNull { it.date == date.toString() } ?: MorningEntry(date.toString())
        store.saveEntries(MorningLog.upsert(store.entries(), dia.copy(painOnWaking = pain), LocalDate.now(zone)))
    }

    /**
     * Corrige los minutos hasta aflojar de la mañana de [date], desde la pantalla Morning
     * (TD-176). El 28-sep la notificación quedó en 25 porque se tocó tarde, y el usuario
     * -que lo había anotado en 10 al final de la sesión- no encontró cómo corregirlo.
     */
    fun editFade(context: Context, date: LocalDate, min: Int) {
        val store = MorningStore(context)
        val dia = store.entries().firstOrNull { it.date == date.toString() } ?: return
        store.saveEntries(MorningLog.upsert(store.entries(), MorningLog.withFade(dia, min), LocalDate.now(ZoneId.systemDefault())))
    }

    /** El próximo aviso para ir a dormir y la alarma a la que apunta (TD-160). */
    fun nextBedtime(context: Context): Pair<ZonedDateTime, ZonedDateTime>? {
        val store = MorningStore(context)
        return Bedtime.next(store.schedule(), store.skipDate, store.bedtime(), ZonedDateTime.now(ZoneId.systemDefault()), Bedtime.bedRecorded(store.entries()))
    }

    /**
     * Programa el aviso para ir a dormir. Con setExactAndAllowWhileIdle y no setAlarmClock:
     * no es un despertador -no debe poner un reloj en la barra ni encender la pantalla-,
     * pero sí tiene que llegar a su hora aunque el teléfono esté en reposo.
     */
    private fun scheduleBedtime(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = bedPending(context)
        val at = nextBedtime(context)?.first?.toInstant()?.toEpochMilli()
        if (at == null) {
            am.cancel(pi)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    /** La notificación de la hora de acostarse, con "Going to bed". */
    fun showBedtime(context: Context) {
        ensureChannels(context)
        val t = com.maurozegarra.master.i18n.I18n.EN
        // La alarma a la que apunta es la próxima: el aviso sonó justo antes de su hora.
        val ring = nextRing(context) ?: return
        // Si esa mañana ya tiene hora de acostarse, ya se acostó: no se avisa. Cubre el aviso
        // que ya estaba programado cuando se tocó "Going to bed".
        if (ring.toLocalDate() in Bedtime.bedRecorded(MorningStore(context).entries())) return
        val cfg = MorningStore(context).bedtime()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("H:mm")
        val toBed = PendingIntent.getBroadcast(
            context, 45,
            Intent(context, MorningReceiver::class.java).setAction(MorningReceiver.ACTION_TO_BED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_BED)
            .setSmallIcon(R.drawable.ic_notif_play)
            .setContentTitle(t.morning.bedNotifTitle)
            .setContentText(t.morning.bedNotifText.format(Bedtime.bedBy(ring, cfg).format(fmt), ring.format(fmt)))
            .setAutoCancel(true)
            .setContentIntent(toBed)
            .addAction(0, t.morning.goingToBed, toBed)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_BED, n)
    }

    /** "Going to bed": anota la hora en la mañana que le toca. */
    fun toBed(context: Context) {
        val store = MorningStore(context)
        store.saveEntries(MorningLog.withBed(store.entries(), System.currentTimeMillis(), ZoneId.systemDefault()))
        context.getSystemService(NotificationManager::class.java).cancel(NOTIF_BED)
        // El aviso de esta noche, si todavía no salió, ya no hace falta: se pasa al de la
        // noche siguiente.
        reschedule(context)
    }

    /**
     * Corrige la hora de acostarse de la mañana de [date], desde Morning: si se tocó "Going
     * to bed" y después se siguió despierto. [time] de mediodía en adelante es la noche antes.
     */
    fun editBed(context: Context, date: LocalDate, time: java.time.LocalTime) {
        val zone = ZoneId.systemDefault()
        val noche = if (time.hour >= 12) date.minusDays(1) else date
        val at = noche.atTime(time).atZone(zone).toInstant().toEpochMilli()
        val store = MorningStore(context)
        store.saveEntries(MorningLog.withBed(store.entries(), at, zone))
        reschedule(context)
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
        // Un aviso normal, con su sonido de notificación: no es la alarma (TD-160).
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BED, "Bedtime", NotificationManager.IMPORTANCE_DEFAULT),
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

    private fun bedPending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQ_BED,
        Intent(context, MorningReceiver::class.java).setAction(MorningReceiver.ACTION_BEDTIME),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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
