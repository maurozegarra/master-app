package com.maurozegarra.master.timer

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Un timer en marcha o en pausa (TD-195). Cuando termina, desaparece: los desechables son de
 * una sola vida, y el potty vuelve a estar listo en la franja.
 *
 * Corriendo: [endAt] es la hora de fin y [pausedLeftMs] es null. En pausa: [pausedLeftMs]
 * guarda lo que faltaba y [endAt] no cuenta.
 */
data class TimerRun(
    val id: String,
    val name: String,
    val durationMs: Long,
    val endAt: Long,
    val alarm: Boolean,
    val pausedLeftMs: Long? = null,
) {
    val paused: Boolean get() = pausedLeftMs != null
}

/** Uno de los últimos usados, para arrancarlo otra vez de un toque. */
data class RecentTimer(val name: String, val durationMs: Long, val alarm: Boolean)

/**
 * Una alarma reciente de la pestaña Alarm (TD-200), por HORA del día y no por duración: "la
 * siesta de la 1:30" es la 1:30 todos los días, y una duración guardada sonaría a otra hora.
 */
data class RecentAlarm(val name: String, val minuteOfDay: Int)

/** Las reglas del timer, sin Android, para probarlas. */
object TimerPlan {

    /** Los presets de su Galaxy Watch8 (9-oct), en segundos. */
    val PRESETS_SEC: List<Int> = listOf(30, 60, 120, 180, 240, 300, 600, 900, 1200, 1500, 1800, 3600)

    /** Cuántos recientes se guardan: los de su reloj. */
    const val RECENT_MAX = 5

    /** Lo que suma "Snooze" en la pantalla de alarma del timer. */
    const val SNOOZE_MS = 5 * 60_000L

    fun start(id: String, name: String, durationMs: Long, alarm: Boolean, now: Long): TimerRun =
        TimerRun(id, name.trim(), durationMs, now + durationMs, alarm)

    fun left(run: TimerRun, now: Long): Long = run.pausedLeftMs ?: (run.endAt - now).coerceAtLeast(0)

    fun pause(run: TimerRun, now: Long): TimerRun =
        if (run.paused) run else run.copy(pausedLeftMs = (run.endAt - now).coerceAtLeast(0))

    /**
     * "Restart", el ↻ de su mini-timer (TD-198): vuelve a la duración completa y queda en
     * pausa, listo para darle play. Uno con "Ends at" vuelve a lo que faltaba al crearlo.
     */
    fun restart(run: TimerRun): TimerRun = run.copy(pausedLeftMs = run.durationMs)

    fun resume(run: TimerRun, now: Long): TimerRun =
        run.pausedLeftMs?.let { run.copy(endAt = now + it, pausedLeftMs = null) } ?: run

    /**
     * El orden de la franja del tablero: el último que arrancó, arriba (TD-198). Se guardan en
     * el orden en que arrancaron; el tablero está anclado al fondo y la franja crece hacia
     * arriba, así que lo nuevo aparece arriba y lo de abajo -el potty y el +- no se mueve.
     */
    fun stripOrder(runs: List<TimerRun>): List<TimerRun> = runs.asReversed()

    /** Lo último arriba, sin repetir el mismo timer, y solo [RECENT_MAX]. */
    fun addRecent(list: List<RecentTimer>, r: RecentTimer): List<RecentTimer> =
        (listOf(r) + list.filter { it != r }).take(RECENT_MAX)

    /**
     * Los chips de "Ends at" de su mini-timer: las seis horas siguientes múltiplos de 5
     * minutos, a un minuto o más de ahora. A las 23:22 son 23:25, 23:30... A las 23:24:30 la
     * de 23:25 ya no entra. Salieron con TD-200 y volvieron con TD-211, a pedido suyo.
     */
    fun endsAtChips(now: Long, count: Int = 6): List<Long> {
        val paso = 5 * 60_000L
        val desde = now + 60_000
        // Sobre el instante: en las zonas de horas enteras o medias (Lima) los múltiplos de 5
        // minutos del instante caen en múltiplos de 5 del reloj.
        val primero = ((desde + paso - 1) / paso) * paso
        return (0 until count).map { primero + it * paso }
    }

    /** Lo mismo para las alarmas por hora (TD-200). */
    fun addAlarmRecent(list: List<RecentAlarm>, r: RecentAlarm): List<RecentAlarm> =
        (listOf(r) + list.filter { it != r }).take(RECENT_MAX)

    /** La rueda de horas del timer llega a 99, como la de su Watch8. */
    const val TIMER_HOURS_MAX = 99

    fun durationMs(h: Int, m: Int, s: Int): Long = ((h * 60L + m) * 60 + s) * 1000

    /**
     * Un paso de rueda que da la vuelta, como la de su reloj (TD-200): encima del 00 está el
     * 99, y después del 59 viene el 00.
     */
    fun wrap(value: Int, range: IntRange): Int =
        range.first + Math.floorMod(value - range.first, range.last - range.first + 1)

    /** Lo que deja un dígito tecleado en una columna de la rueda (TD-200). */
    data class Typed(val buffer: String, val value: Int?, val advance: Boolean)

    /**
     * Un dígito más en una columna, como el teclado de Alarm Clock Xtreme: el primero reemplaza
     * al número, el segundo lo completa ("4", "5" -> 45). Si con los dos se pasa del rango, el
     * nuevo empieza otro número ("7", "5" en minutos -> 75 no existe -> 5).
     *
     * [Typed.advance] dice que la columna ya no admite otro dígito y hay que pasar a la
     * siguiente: con dos, o con uno que no puede tener segundo (un 7 en las horas de 1 a 12).
     * [Typed.value] es null mientras lo tecleado no es un número válido (un 0 en las horas).
     */
    fun typeDigit(buffer: String, digit: Char, range: IntRange): Typed {
        var b = (buffer + digit).takeLast(2)
        if (b.length == 2 && b.toInt() > range.last) b = digit.toString()
        val n = b.toInt()
        val valor = n.takeIf { it in range }
        val avanza = valor != null && (b.length == 2 || n * 10 > range.last)
        return Typed(b, valor, avanza)
    }

    /** 1..12 y AM/PM -> 0..23. Las 12 AM son las 0. */
    fun to24(hour12: Int, pm: Boolean): Int = (hour12 % 12) + if (pm) 12 else 0

    fun hour12(hour24: Int): Int = (hour24 % 12).let { if (it == 0) 12 else it }

    /**
     * La próxima vez que el reloj marca [minuteOfDay]: hoy si todavía no pasó, si no mañana.
     * Lo que hace cualquier alarma, y lo que quita la cuenta que antes había que sacar.
     */
    fun nextAt(minuteOfDay: Int, now: Long, zone: ZoneId): Long {
        val ahora = Instant.ofEpochMilli(now).atZone(zone)
        val hoy = ahora.toLocalDate().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)).atZone(zone)
        return (if (hoy.isAfter(ahora)) hoy else hoy.plusDays(1)).toInstant().toEpochMilli()
    }

    fun isTomorrow(at: Long, now: Long, zone: ZoneId): Boolean =
        Instant.ofEpochMilli(at).atZone(zone).toLocalDate() != Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    /** "2 h 15 min", "45 min", "1 h": lo que falta, redondeado al minuto de arriba. */
    fun untilLabel(ms: Long): String {
        val min = ((ms + 59_999) / 60_000).coerceAtLeast(1)
        val h = min / 60
        val m = min % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    /**
     * La hora que propone la pestaña Alarm al abrirse: media hora más tarde, redondeada a los 5
     * minutos de arriba. Un punto de partida cercano, para mover poco la rueda.
     */
    fun defaultAlarmMinute(now: Long, zone: ZoneId): Int {
        val t = Instant.ofEpochMilli(now).atZone(zone).plusMinutes(30)
        val min = t.hour * 60 + t.minute
        return ((min + 4) / 5 * 5) % (24 * 60)
    }

    /** "1:30 PM": la hora en 12 horas, como el reloj del app (TD-183). */
    fun clock12(minuteOfDay: Int): String {
        val h = minuteOfDay / 60
        return "%d:%02d %s".format(hour12(h), minuteOfDay % 60, if (h >= 12) "PM" else "AM")
    }

    fun clock12(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).let { clock12(it.hour * 60 + it.minute) }

    /** "5:00", "24:10", "1:00:00". */
    fun format(ms: Long): String {
        val total = (ms + 999) / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** El nombre corto de un preset: "30s", "5m", "1h". */
    fun presetLabel(sec: Int): String = when {
        sec < 60 -> "${sec}s"
        sec % 3600 == 0 -> "${sec / 3600}h"
        else -> "${sec / 60}m"
    }

    fun encodeRuns(runs: List<TimerRun>): String = JSONArray().apply {
        runs.forEach {
            put(
                JSONObject().put("id", it.id).put("name", it.name).put("durationMs", it.durationMs)
                    .put("endAt", it.endAt).put("alarm", it.alarm).apply { it.pausedLeftMs?.let { p -> put("pausedLeftMs", p) } },
            )
        }
    }.toString()

    fun decodeRuns(json: String): List<TimerRun> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            TimerRun(
                id = o.optString("id").ifBlank { return@mapNotNull null },
                name = o.optString("name"),
                durationMs = o.optLong("durationMs"),
                endAt = o.optLong("endAt"),
                alarm = o.optBoolean("alarm"),
                pausedLeftMs = if (o.has("pausedLeftMs")) o.optLong("pausedLeftMs") else null,
            )
        }
    }.getOrDefault(emptyList())

    fun encodeRecents(list: List<RecentTimer>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("name", it.name).put("durationMs", it.durationMs).put("alarm", it.alarm)) }
    }.toString()

    fun decodeRecents(json: String): List<RecentTimer> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            RecentTimer(o.optString("name"), o.optLong("durationMs").takeIf { it > 0 } ?: return@mapNotNull null, o.optBoolean("alarm"))
        }
    }.getOrDefault(emptyList())

    fun encodeAlarmRecents(list: List<RecentAlarm>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("name", it.name).put("minuteOfDay", it.minuteOfDay)) }
    }.toString()

    fun decodeAlarmRecents(json: String): List<RecentAlarm> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val m = o.optInt("minuteOfDay", -1).takeIf { it in 0 until 24 * 60 } ?: return@mapNotNull null
            RecentAlarm(o.optString("name"), m)
        }
    }.getOrDefault(emptyList())

    /** "14:00": la hora de fin, como la escribe el resto del tablero. */
    fun clock(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).let { "%d:%02d".format(it.hour, it.minute) }
}
