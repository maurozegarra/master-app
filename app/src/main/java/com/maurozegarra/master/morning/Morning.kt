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
    fun next(now: ZonedDateTime, skip: LocalDate? = null): ZonedDateTime? {
        for (i in 0..8) {
            val day = now.toLocalDate().plusDays(i.toLong())
            // Un dia saltado a mano -"Skip tomorrow"- no suena, sin tocar el horario.
            if (day == skip) continue
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
    /**
     * Los minutos ya calculados, cuando no hay horas de las que sacarlos: las mañanas que
     * vienen de una sesión (ver [MorningLog.fromSessions]) traen el número, no los instantes.
     */
    val fadeMin: Int? = null,
) {
    /**
     * Minutos entre contestar y aflojar, redondeados. Null si todavía no aflojó.
     *
     * Contestar es el despertar: la alarma se apaga contestando, así que es el mismo minuto.
     */
    val fadeMinutes: Int?
        get() {
            val desde = answeredAt ?: return fadeMin
            val hasta = easedAt ?: return fadeMin
            return (((hasta - desde).coerceAtLeast(0) + 30_000) / 60_000).toInt()
        }
}

object MorningLog {

    /** Cuántas mañanas se guardan. Un año basta para ver una tendencia de dolor. */
    const val KEEP_DAYS = 400

    fun dateOf(millis: Long, zone: ZoneId): String =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

    /**
     * Las mañanas de verdad: sin las contestadas de noche.
     *
     * Una respuesta después de las [EVENING_HOUR] no es un despertar: es una prueba de la
     * alarma -la del 22-sep a las 21:42 decía "dolor 0" en una mañana que fue 2-. Contarla
     * ensuciaría la serie justo en lo que se quiere mirar. Se filtra al LEER, no se borra.
     */
    fun mornings(entries: List<MorningEntry>, zone: ZoneId): List<MorningEntry> =
        entries.filter { e ->
            val h = e.answeredAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).hour } ?: return@filter true
            h < EVENING_HOUR
        }

    /** Dolor al despertar por día, para pintar el calendario. */
    fun painByDate(entries: List<MorningEntry>, zone: ZoneId): Map<LocalDate, Int> =
        mornings(entries, zone).mapNotNull { e -> e.painOnWaking?.let { LocalDate.parse(e.date) to it } }.toMap()

    const val EVENING_HOUR = 18

    /** Si [e] es una mañana de verdad -no una prueba de noche-. */
    fun isMorning(e: MorningEntry, zone: ZoneId): Boolean =
        mornings(listOf(e), zone).isNotEmpty()

    /**
     * Si una respuesta a [now] puede guardarse sobre lo que ya hay de ese día.
     *
     * Una respuesta de NOCHE no pisa una mañana de verdad. El 24-sep, al probar la alarma a
     * las 23:00, la prueba reemplazó la mañana real de ese día -dolor 1, 24 min- y el filtro
     * de pruebas la descartó después: el día quedó vacío.
     */
    fun mayRecord(existing: MorningEntry?, now: Long, zone: ZoneId): Boolean {
        if (existing == null || !isMorning(existing, zone)) return true
        return java.time.Instant.ofEpochMilli(now).atZone(zone).hour < EVENING_HOUR
    }

    /**
     * Las mañanas que faltan, sacadas de las sesiones: el dolor al despertar y los minutos se
     * preguntaban al final del training antes de que existiera la alarma (TD-125), y ahí
     * siguen. Sin esto la serie empezaría el 23-sep y perdería los días de antes.
     *
     * Solo llena huecos: un día con mañana de verdad no se toca. [sessions] son pares de
     * (inicio de la sesión, dolor al despertar, minutos hasta aflojar).
     */
    fun fromSessions(
        entries: List<MorningEntry>,
        sessions: List<Triple<Long, Int?, Int?>>,
        zone: ZoneId,
    ): List<MorningEntry> {
        val reales = mornings(entries, zone).map { it.date }.toSet()
        val nuevas = sessions
            .filter { (_, dolor, _) -> dolor != null }
            .groupBy { (inicio, _, _) -> dateOf(inicio, zone) }
            .filterKeys { it !in reales }
            .map { (dia, ss) -> ss.first().let { (_, dolor, min) -> MorningEntry(dia, painOnWaking = dolor, fadeMin = min) } }
        if (nuevas.isEmpty()) return entries
        val fuera = nuevas.map { it.date }.toSet()
        return (entries.filter { it.date !in fuera } + nuevas).sortedBy { it.date }
    }

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
