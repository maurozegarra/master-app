package com.maurozegarra.master.water

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * El agua del día (TD-190): lo que se tomó y cuándo recordar el siguiente vaso. La parte
 * pura, sin Android, para poder probarla.
 *
 * **Por qué existe.** Su app de agua recordaba a ciegas, a cada hora: los fines de semana se
 * levantaba a las 9:50, tomaba agua y a las 10 ya le recordaba otra vez; no apuraba cuando iba
 * atrasado; seguía recordando después de una botella de 600 aunque fuera adelante; y después
 * de anotar 600 sugería 600, cuando su vaso es de 200.
 *
 * **La regla.** El horario sí es fijo -es como toma de verdad, en casa o presencial-, pero se
 * compara con lo tomado: al día, avisa en la próxima hora que no esté cubierta; con déficit,
 * cada 30 minutos desde el último trago hasta ponerse al día. Está en [WaterPlan.nextReminder].
 * Una primera versión repartía la meta en una curva desde el despertar, y no era como toma.
 */
data class WaterLog(
    val at: Long,
    val ml: Int,
    /** La bebida (ver [Drinks]). Todas cuentan igual para la meta, decidido con él el 4-oct. */
    val type: String = Drinks.WATER,
)

/**
 * Las bebidas que anota (4-oct): las de su app de antes más el café. Cada una con su ícono en
 * la lista, que es lo que le gustaba de esa app: *"con sólo un vistazo sé qué cosa he tomado a
 * lo largo del día"*. Todas cuentan igual para los 2 litros.
 */
object Drinks {
    const val WATER = "water"
    const val CREATINE = "creatine"
    const val QUINOA = "quinoa"
    const val SOUP = "soup"
    const val SODA = "soda"
    const val COFFEE = "coffee"
    const val MACA = "maca"
    /** El refresco del menú del almuerzo, que suele ser limonada (4-oct). */
    const val LEMONADE = "lemonade"
    val ALL = listOf(WATER, CREATINE, QUINOA, MACA, COFFEE, SOUP, LEMONADE, SODA)
}

object WaterPlan {

    /** La meta del día, decidida con él el 4-oct: 2 litros fijos. */
    const val GOAL_ML = 2000

    /** El vaso común: lo que anota el +, y lo que tiene que faltar para recordar. */
    const val GLASS_ML = 200

    /** La botella que compra los días presenciales. */
    const val BOTTLE_ML = 600

    /** Nunca se recuerda antes de esto desde el último trago. */
    const val MIN_GAP_MIN = 30L

    /** Se deja de recordar esto antes de acostarse, para no levantarse de noche. */
    const val CUT_BEFORE_BED_MIN = 120L

    /** Sin alarma al día siguiente no hay hora de acostarse: se corta a esta hora. */
    val FALLBACK_CUT: LocalTime = LocalTime.of(19, 0)

    fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Lo de [day], ordenado por hora. */
    fun logsOf(logs: List<WaterLog>, day: LocalDate, zone: ZoneId): List<WaterLog> =
        logs.filter { dayOf(it.at, zone) == day }.sortedBy { it.at }

    fun total(logs: List<WaterLog>): Int = logs.sumOf { it.ml }

    /**
     * Cuándo empieza el día: al contestar la alarma ([wakeAt]). Sin alarma ese día, con el
     * primer vaso anotado. Sin ninguno de los dos todavía, no hay día: no se recuerda nada.
     */
    fun start(wakeAt: Long?, today: List<WaterLog>): Long? = wakeAt ?: today.minOfOrNull { it.at }

    /**
     * Cuándo se deja de recordar: [CUT_BEFORE_BED_MIN] antes de acostarse ([bedAt], que sale
     * de la alarma siguiente menos las horas de sueño). Sin eso, a [FALLBACK_CUT] del día.
     */
    fun cut(bedAt: Long?, day: LocalDate, zone: ZoneId): Long =
        bedAt?.minus(CUT_BEFORE_BED_MIN * 60_000)
            ?: day.atTime(FALLBACK_CUT).atZone(zone).toInstant().toEpochMilli()

    /** Una toma del horario: a qué hora y cuánto. */
    data class Slot(val time: LocalTime, val ml: Int)

    /**
     * Su horario de un día en casa (4-oct): un vaso a cada hora de 7 a 12 (1.2 L), nada a la
     * 1 -"con la barriga llena el agua me hace sentir embotado"-, y de 2 a 5 los otros 0.8 L.
     * Así le ha funcionado por años: a las 5 ya tiene sus 2 litros.
     */
    val HOME: List<Slot> = (7..12).map { Slot(LocalTime.of(it, 0), GLASS_ML) } + (14..17).map { Slot(LocalTime.of(it, 0), GLASS_ML) }

    /**
     * Su horario de un día presencial, cuando la alarma suena a las 5 (4-oct): creatina al
     * despertar, la bebida del desayuno a las 6:30, la botella a las 9, sopa y limonada en el
     * almuerzo de las 12:30, y la segunda botella, que compra a las 1:50 y empieza a las 2.
     *
     * Cada botella va repartida en vasos por hora (6-oct): no la toma de golpe, la va tomando.
     * Así lo cuenta él: 500 ml a las 2:02 cubren las 2, las 3 y 100 de las 4, y el aviso toca a
     * las 5. Con la botella entera a las 2, el horario se acababa ahí y la regla insistía cada
     * media hora por 100 ml.
     */
    val OFFICE: List<Slot> = listOf(
        Slot(LocalTime.of(5, 0), GLASS_ML),
        Slot(LocalTime.of(6, 30), GLASS_ML),
    ) + (9..11).map { Slot(LocalTime.of(it, 0), GLASS_ML) } +
        Slot(LocalTime.of(12, 30), 2 * GLASS_ML) +
        (14..16).map { Slot(LocalTime.of(it, 0), GLASS_ML) }

    /** Si la alarma del día suena antes de esta hora, es un día presencial (decidido con él). */
    val OFFICE_BEFORE: LocalTime = LocalTime.of(6, 0)

    fun planFor(alarmToday: LocalTime?): List<Slot> = if (alarmToday != null && alarmToday < OFFICE_BEFORE) OFFICE else HOME

    /** El horario de [day] como instantes. */
    fun slotsOn(plan: List<Slot>, day: LocalDate, zone: ZoneId): List<Pair<Long, Int>> =
        plan.map { day.atTime(it.time).atZone(zone).toInstant().toEpochMilli() to it.ml }

    /** Lo que debería llevar a [t] según el horario: lo de todas las horas que ya pasaron. */
    fun expected(t: Long, slots: List<Pair<Long, Int>>): Int = slots.filter { it.first <= t }.sumOf { it.second }

    /**
     * El próximo recordatorio, o null si no toca ninguno: meta cumplida, día sin empezar, o
     * ya pasada la hora de cortar.
     *
     * Su regla (4-oct), sobre su horario fijo y no sobre una curva desde que despierta:
     * 1. Al día o adelante: a la próxima hora del horario que todavía no cubrió (la botella de
     *    600 cubre varias). Las 13 no están en el horario de casa: no avisa si va al día.
     * 2. Atrasado un vaso o más: cada media hora -30 min tras el último trago, llevado a :00
     *    o :30-, también a la 1, hasta alcanzar al horario. Despertar a las 9:50 ya es déficit
     *    (el horario pedía 7, 8 y 9): 10:30, 11:00, 11:30…
     * 3. Pasada la última hora del horario sin la meta, la cadencia sigue: un vaso por hora
     *    (6-oct). Así lo que falta se pide a la hora siguiente y no cada media hora: el 5-oct,
     *    con 1,900 a las 2:02, insistía toda la tarde por 100 ml. Si va atrasado un vaso o más,
     *    vale el punto 2.
     * 4. Si ya no cabe antes de cortar y falta la meta, un último aviso media hora antes.
     *
     * Las versiones anteriores repartían la meta en una curva desde el despertar: despertar
     * tarde nunca era déficit, y la primera ni siquiera avisaba a la media hora.
     */
    fun nextReminder(now: Long, today: List<WaterLog>, start: Long?, cut: Long, slots: List<Pair<Long, Int>>, goal: Int = GOAL_ML): Long? {
        if (start == null || cut <= start) return null
        val tomado = total(today)
        if (tomado >= goal) return null
        val ultimo = today.maxOfOrNull { it.at } ?: start
        val media = halfHourUp(ultimo + MIN_GAP_MIN * 60_000)
        // Pasado el horario, un vaso por hora hasta cortar (punto 3).
        val horas = slots + extraHours(slots, cut)
        val t = if (expected(now, horas) - tomado >= GLASS_ML) {
            maxOf(media, now)
        } else {
            // La próxima hora en la que quedaría un vaso atrás. Si no hay ninguna antes de
            // cortar, lo que falta es menos de un vaso: solo el último aviso (punto 4).
            val proxima = horas.filter { it.first > now }
                .firstOrNull { (hora, _) -> expected(hora, horas) - tomado >= GLASS_ML }?.first
            if (proxima != null) maxOf(proxima, ultimo + MIN_GAP_MIN * 60_000) else cut
        }
        if (t < cut) return t
        val ultimoAviso = cut - MIN_GAP_MIN * 60_000
        return if (ultimoAviso > now && ultimoAviso >= ultimo + MIN_GAP_MIN * 60_000) ultimoAviso else null
    }

    /** Las horas en punto después de la última del horario y antes de [cut], de un vaso cada una. */
    private fun extraHours(slots: List<Pair<Long, Int>>, cut: Long): List<Pair<Long, Int>> {
        val ultima = slots.maxOfOrNull { it.first } ?: return emptyList()
        val hora = 60 * 60_000L
        return generateSequence(ultima + hora) { it + hora }.takeWhile { it < cut }.map { it to GLASS_ML }.toList()
    }

    /**
     * [millis] llevado a la siguiente hora o media hora en punto (o el mismo, si ya lo es).
     * Sobre el instante: vale para las zonas con desfase de horas o medias horas, como Lima.
     */
    fun halfHourUp(millis: Long): Long {
        val media = 30 * 60_000L
        return ((millis + media - 1) / media) * media
    }

    /** Una cantidad en vasos, redondeada al más cercano: 1668 ml -> 8. */
    fun glasses(ml: Int): Int = Math.round(ml.toDouble() / GLASS_ML).toInt()

    /** Cuánto va atrasado (positivo) o adelantado (negativo) a [now] respecto del horario. */
    fun behind(now: Long, today: List<WaterLog>, start: Long?, slots: List<Pair<Long, Int>>): Int? {
        start ?: return null
        return expected(now, slots) - total(today)
    }

    /** Cuántos días se guardan: un año basta para ver una tendencia. */
    const val KEEP_DAYS = 400L

    fun add(logs: List<WaterLog>, entry: WaterLog, today: LocalDate, zone: ZoneId): List<WaterLog> {
        val corte = today.minusDays(KEEP_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        return (logs + entry).filter { it.at >= corte }.sortedBy { it.at }
    }

    fun remove(logs: List<WaterLog>, at: Long): List<WaterLog> = logs.filter { it.at != at }

    /** [old] reemplazada por [new]: corregir la hora, la bebida o la cantidad. */
    fun replace(logs: List<WaterLog>, old: Long, new: WaterLog): List<WaterLog> =
        (logs.filter { it.at != old } + new).sortedBy { it.at }

    /**
     * Las tomas del 4-oct que anotó en su app de antes (captura del 4-oct), para que el primer
     * día no empiece en cero: las ocho de 200 ml, con su hora y su bebida.
     */
    fun seed4Oct(zone: ZoneId): List<WaterLog> {
        val d = LocalDate.of(2026, 10, 4)
        fun at(h: Int, m: Int) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        return listOf(
            WaterLog(at(8, 6), 200, Drinks.CREATINE),
            WaterLog(at(8, 23), 200, Drinks.WATER),
            WaterLog(at(9, 1), 200, Drinks.WATER),
            WaterLog(at(10, 5), 200, Drinks.QUINOA),
            WaterLog(at(11, 4), 200, Drinks.WATER),
            WaterLog(at(15, 0), 200, Drinks.SOUP),
            WaterLog(at(15, 30), 200, Drinks.SODA),
            WaterLog(at(17, 3), 200, Drinks.WATER),
        )
    }

    fun encode(logs: List<WaterLog>): String =
        JSONArray().apply { logs.forEach { put(JSONObject().put("at", it.at).put("ml", it.ml).put("type", it.type)) } }.toString()

    fun decode(json: String): List<WaterLog> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val at = o.optLong("at", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val ml = o.optInt("ml", 0).takeIf { it > 0 } ?: return@mapNotNull null
            WaterLog(at, ml, o.optString("type").takeIf { it in Drinks.ALL } ?: Drinks.WATER)
        }
    }.getOrDefault(emptyList())

    /** La hora de acostarse de la noche de [day], desde la alarma de la mañana siguiente. */
    fun bedFromNextRing(nextRing: ZonedDateTime?, day: LocalDate, sleepMin: Int): Long? {
        val ring = nextRing ?: return null
        // Solo la alarma de la mañana siguiente dice cuándo acostarse esta noche.
        if (ring.toLocalDate() != day.plusDays(1)) return null
        return ring.minusMinutes(sleepMin.toLong()).toInstant().toEpochMilli()
    }
}
