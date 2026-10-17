package com.maurozegarra.master.timer

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
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
     * de 23:25 ya no entra.
     */
    fun endsAtChips(now: Long, count: Int = 6): List<Long> {
        val paso = 5 * 60_000L
        val desde = now + 60_000
        // Sobre el instante: en las zonas de horas enteras o medias (Lima) los múltiplos de 5
        // minutos del instante caen en múltiplos de 5 del reloj.
        val primero = ((desde + paso - 1) / paso) * paso
        return (0 until count).map { primero + it * paso }
    }

    /**
     * El teclado: los dígitos entran por la derecha como en su mini-timer (HHMMSS). "130" son
     * 1:30; "10000" es 1:00:00. Los segundos y minutos de más de 59 se suman tal cual.
     */
    fun digitsToMs(digits: String): Long {
        val d = digits.filter { it.isDigit() }.takeLast(6).padStart(6, '0')
        val h = d.substring(0, 2).toLong()
        val m = d.substring(2, 4).toLong()
        val s = d.substring(4, 6).toLong()
        return ((h * 60 + m) * 60 + s) * 1000
    }

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

    /** "14:00": la hora de fin, como la escribe el resto del tablero. */
    fun clock(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis).atZone(zone).let { "%d:%02d".format(it.hour, it.minute) }
}
