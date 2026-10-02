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
 * Una alarma: su hora, los días en que suena y si está encendida (TD-175).
 *
 * Hasta TD-175 el horario era un mapa día -> hora, y se editaba día por día. El usuario lo
 * pidió como un despertador: *"2 cards, uno para las 5:00 y otro para las 7:00 y dentro del
 * card habilitar los días"*, cada una con su interruptor, y con soporte para más de dos.
 */
data class MorningAlarmSpec(
    val id: Long,
    val time: LocalTime,
    val days: Set<DayOfWeek>,
    val enabled: Boolean = true,
)

/** Las alarmas del teléfono. Suena la que toque primero. */
data class MorningSchedule(val alarms: List<MorningAlarmSpec>) {

    /** Si alguna puede sonar: encendida y con al menos un día. */
    val anyOn: Boolean get() = alarms.any { it.enabled && it.days.isNotEmpty() }

    /** Si algo suena el día [day]. */
    fun ringsOn(day: DayOfWeek): Boolean = alarms.any { it.enabled && day in it.days }

    fun update(a: MorningAlarmSpec): MorningSchedule = copy(alarms = alarms.map { if (it.id == a.id) a else it })

    fun remove(id: Long): MorningSchedule = copy(alarms = alarms.filter { it.id != id })

    /**
     * Los días con los que nace una alarma nueva (TD-182): los que ninguna encendida cubre,
     * que es para lo que casi siempre se agrega una. Si todos tienen ya la suya, de lunes a
     * viernes, como antes.
     */
    fun newAlarmDays(): Set<DayOfWeek> {
        val libres = DayOfWeek.entries.filter { !ringsOn(it) }.toSet()
        return libres.ifEmpty { DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY - DayOfWeek.SUNDAY }
    }

    /** Una alarma nueva, ordenada por hora con las demás. */
    fun add(time: LocalTime, days: Set<DayOfWeek>): MorningSchedule =
        copy(alarms = (alarms + MorningAlarmSpec((alarms.maxOfOrNull { it.id } ?: 0L) + 1, time, days)).sortedBy { it.time })

    /**
     * La próxima vez que tiene que sonar, estrictamente después de [now]: la más temprana de
     * todas las alarmas encendidas. Null si ninguna suena.
     */
    fun next(now: ZonedDateTime, skip: LocalDate? = null): ZonedDateTime? {
        for (i in 0..8) {
            val day = now.toLocalDate().plusDays(i.toLong())
            // Un dia saltado a mano -"Skip tomorrow"- no suena, sin tocar las alarmas.
            if (day == skip) continue
            val hoy = alarms
                .filter { it.enabled && day.dayOfWeek in it.days }
                .map { day.atTime(it.time).atZone(now.zone) }
                .filter { it.isAfter(now) }
                .minOrNull()
            if (hoy != null) return hoy
        }
        return null
    }

    companion object {
        private val OFICINA = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)

        /**
         * Lo que dijo el usuario el 22-sep: 5:00 los días presenciales -lunes, miércoles y
         * jueves- y 7:00 los demás. Dos alarmas.
         */
        fun default(enabled: Boolean) = MorningSchedule(
            listOf(
                MorningAlarmSpec(1, LocalTime.of(5, 0), OFICINA, enabled),
                MorningAlarmSpec(2, LocalTime.of(7, 0), DayOfWeek.entries.toSet() - OFICINA, enabled),
            ),
        )

        /**
         * El horario de antes de TD-175 -día -> hora- pasado a alarmas: una por cada hora
         * distinta, con sus días. Un teléfono que ya la usaba no pierde nada.
         */
        fun fromDays(times: Map<DayOfWeek, LocalTime?>, enabled: Boolean): MorningSchedule =
            MorningSchedule(
                times.entries.filter { it.value != null }
                    .groupBy({ it.value!! }, { it.key })
                    .entries.sortedBy { it.key }
                    .mapIndexed { i, (hora, dias) -> MorningAlarmSpec(i + 1L, hora, dias.toSet(), enabled) },
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
    /**
     * Cuándo se acostó la noche ANTERIOR a esta mañana, con "Going to bed" (TD-160). Vive en
     * la mañana y no en la noche porque es lo que explica: un dolor después de pocas horas
     * en cama no se lee como uno después de ocho.
     */
    val bedAt: Long? = null,
) {
    /**
     * Minutos en cama: de acostarse a contestar la alarma. Es tiempo en CAMA, no sueño
     * medido: el app no tiene sensor.
     */
    val inBedMinutes: Int?
        get() {
            val desde = bedAt ?: return null
            val hasta = answeredAt ?: return null
            if (hasta <= desde) return null
            return ((hasta - desde + 30_000) / 60_000).toInt()
        }

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
     * Si una alarma que suena cuando la mañana YA se contestó debe guardar otra respuesta.
     * No (TD-175): con varias alarmas, un día puede sonar a las 5 y a las 7, y el despertar
     * es el primero. La segunda respuesta pisaría la hora de contestar, y con ella los
     * minutos hasta aflojar. Corregir el número sigue siendo cosa de la pantalla Morning.
     */
    fun alreadyAnswered(existing: MorningEntry?, zone: ZoneId): Boolean =
        existing?.painOnWaking != null && isMorning(existing, zone)

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
        // Solo cuenta como mañana de verdad la que tiene dolor: una que solo trae la hora de
        // acostarse (TD-160) no debe tapar lo que dijo la sesión.
        val reales = mornings(entries, zone).filter { it.painOnWaking != null }.map { it.date }.toSet()
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

    /**
     * La respuesta a la alarma sobre lo que ya hubiera de ese día: conserva la hora de
     * acostarse (TD-160), que se anotó la noche antes en esta misma mañana.
     */
    fun answer(existing: MorningEntry?, date: String, pain: Int, now: Long): MorningEntry =
        (existing ?: MorningEntry(date)).copy(painOnWaking = pain, answeredAt = now, easedAt = null, fadeMin = null)

    /** La mañana a la que pertenece acostarse a [millis]: de mediodía en adelante, la siguiente. */
    fun morningOf(millis: Long, zone: ZoneId): LocalDate {
        val t = java.time.Instant.ofEpochMilli(millis).atZone(zone)
        return if (t.hour >= 12) t.toLocalDate().plusDays(1) else t.toLocalDate()
    }

    /** [entries] con "me acosté a [bedAt]" en la mañana que le toca. */
    fun withBed(entries: List<MorningEntry>, bedAt: Long, zone: ZoneId): List<MorningEntry> {
        val dia = morningOf(bedAt, zone)
        val e = entries.firstOrNull { it.date == dia.toString() } ?: MorningEntry(dia.toString())
        return upsert(entries, e.copy(bedAt = bedAt), java.time.LocalDate.now(zone))
    }

    /**
     * [e] con los minutos hasta aflojar corregidos a [min] (TD-176).
     *
     * Se mueve la hora de "aflojó" y no se guarda un número suelto: los minutos salen de las
     * dos horas, y así la corrección se lee igual en todas partes. Sin hora de contestar -una
     * mañana que vino de una sesión-, queda el número.
     */
    fun withFade(e: MorningEntry, min: Int): MorningEntry {
        val desde = e.answeredAt ?: return e.copy(fadeMin = min, easedAt = null)
        return e.copy(easedAt = desde + min * 60_000L, fadeMin = null)
    }

    /** [entries] con la de su día reemplazada por [entry], y sin lo más viejo que [KEEP_DAYS]. */
    fun upsert(entries: List<MorningEntry>, entry: MorningEntry, today: LocalDate): List<MorningEntry> {
        val corte = today.minusDays(KEEP_DAYS.toLong()).toString()
        return (entries.filter { it.date != entry.date } + entry)
            .filter { it.date >= corte }
            .sortedBy { it.date }
    }
}

/**
 * El aviso para ir a dormir (TD-160): [sleepMin] de sueño antes de la alarma, y el aviso
 * [leadMin] antes de eso. Lo que eligió el usuario el 28-sep: 7 h 30 y media hora.
 */
data class BedtimeConfig(
    val enabled: Boolean = false,
    val sleepMin: Int = 450,
    val leadMin: Int = 30,
)

object Bedtime {

    /** La hora de acostarse para que [ring] deje las horas de sueño elegidas. */
    fun bedBy(ring: ZonedDateTime, cfg: BedtimeConfig): ZonedDateTime = ring.minusMinutes(cfg.sleepMin.toLong())

    /**
     * El próximo aviso, estrictamente después de [now], y la alarma a la que apunta; null si
     * está apagado o no suena ninguna.
     *
     * Si el aviso de la próxima alarma ya pasó -son las 22:00 y mañana suena a las 5-, no se
     * avisa a destiempo: se busca el de la alarma siguiente. Avisar "acuéstate a las 21:30"
     * a las 22:00 no sirve de nada.
     */
    fun next(schedule: MorningSchedule, skip: LocalDate?, cfg: BedtimeConfig, now: ZonedDateTime): Pair<ZonedDateTime, ZonedDateTime>? {
        if (!cfg.enabled) return null
        var desde = now
        repeat(4) {
            val ring = schedule.next(desde, skip) ?: return null
            val aviso = bedBy(ring, cfg).minusMinutes(cfg.leadMin.toLong())
            if (aviso.isAfter(now)) return aviso to ring
            desde = ring
        }
        return null
    }
}

/** Cuánto falta para la próxima alarma, como lo decía su despertador (28-sep). */
object Countdown {
    /** 507 -> "8 h 27 min", 45 -> "45 min", 0 -> "1 min" (lo que falta nunca es cero). */
    fun text(minutes: Int): String {
        val m = minutes.coerceAtLeast(1)
        return when {
            m < 60 -> "$m min"
            m % 60 == 0 -> "${m / 60} h"
            else -> "${m / 60} h ${m % 60} min"
        }
    }
}
