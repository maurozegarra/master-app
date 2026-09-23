package com.maurozegarra.master.morning

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * La alarma de la mañana, que al apagarse pregunta el dolor (TD-151). La parte pura: cuándo
 * suena y qué se guarda.
 *
 * **Por qué existe.** El dolor al despertar -el de años, el que se quiere mover- se contestaba
 * al final del training, una hora después y de memoria: *"mientras más pronto registre el
 * dolor, mejor"*. Una alarma que se apaga contestando lo pregunta en el único momento en que
 * vale: con los ojos recién abiertos, antes de moverse.
 *
 * **Es un módulo aparte a propósito.** Se prueba una semana; si estorba al app de ejercicio,
 * se saca. Todo lo suyo vive en este paquete y el resto de MASTER lo toca por tres sitios:
 * la sesión toma el dolor del día ([MorningLog.forDay]), el respaldo lo incluye, y hay una
 * entrada en Settings.
 */

/**
 * A qué hora suena cada día; null = ese día no suena.
 *
 * Por defecto, lo que dijo el usuario el 22-sep: 5:00 los días presenciales -lunes,
 * miércoles y jueves- y 7:00 los demás.
 */
data class MorningSchedule(val times: Map<DayOfWeek, LocalTime?>) {

    fun at(day: DayOfWeek): LocalTime? = times[day]

    fun with(day: DayOfWeek, time: LocalTime?): MorningSchedule = copy(times = times + (day to time))

    /**
     * La próxima vez que tiene que sonar, estrictamente después de [now]. Null si no suena
     * ningún día.
     */
    fun next(now: ZonedDateTime): ZonedDateTime? {
        for (i in 0..7) {
            val day = now.toLocalDate().plusDays(i.toLong())
            val time = at(day.dayOfWeek) ?: continue
            val at = day.atTime(time).atZone(now.zone)
            if (at.isAfter(now)) return at
        }
        return null
    }

    companion object {
        private val OFICINA = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)

        val DEFAULT = MorningSchedule(
            DayOfWeek.entries.associateWith { if (it in OFICINA) LocalTime.of(5, 0) else LocalTime.of(7, 0) },
        )
    }
}

/**
 * Lo de una mañana: cuánto dolía al despertar y cuándo aflojó.
 *
 * [answeredAt] y [easedAt] son instantes y no minutos: los minutos los calcula el app, que
 * es justo lo que se quería -no tener que contar nada-.
 */
data class MorningEntry(
    /** El día, en formato ISO (2026-09-23). */
    val date: String,
    val painOnWaking: Int? = null,
    val answeredAt: Long? = null,
    val easedAt: Long? = null,
) {
    /**
     * Minutos entre contestar y aflojar, redondeados. Null si todavía no aflojó.
     *
     * Contestar es el despertar: la alarma se apaga contestando, así que es el mismo minuto.
     */
    val fadeMinutes: Int?
        get() {
            val desde = answeredAt ?: return null
            val hasta = easedAt ?: return null
            return (((hasta - desde).coerceAtLeast(0) + 30_000) / 60_000).toInt()
        }
}

object MorningLog {

    /** Cuántas mañanas se guardan. Un año basta para ver una tendencia de dolor. */
    const val KEEP_DAYS = 400

    fun dateOf(millis: Long, zone: ZoneId): String =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

    /** La mañana del día de [millis], si se contestó algo. */
    fun forDay(entries: List<MorningEntry>, millis: Long, zone: ZoneId): MorningEntry? {
        val dia = dateOf(millis, zone)
        return entries.firstOrNull { it.date == dia }
    }

    /** [entries] con la de su día reemplazada por [entry], y sin lo más viejo que [KEEP_DAYS]. */
    fun upsert(entries: List<MorningEntry>, entry: MorningEntry, today: LocalDate): List<MorningEntry> {
        val corte = today.minusDays(KEEP_DAYS.toLong()).toString()
        return (entries.filter { it.date != entry.date } + entry)
            .filter { it.date >= corte }
            .sortedBy { it.date }
    }
}
