package com.maurozegarra.master.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Un pesaje (TD-169): lo que se anota los sábados al despertar.
 *
 * **Por qué existe.** El usuario y NIKO se pesan todos los sábados y le pasaban los números al
 * coach por el chat; la serie vivía solo en `docs/objetivos.md` y `docs/niko.md`. El 3-oct el
 * coach se olvidó de pedirla, y el usuario no tenía dónde ver su curva ni la de NIKO.
 *
 * Todo es opcional menos el día: la balanza y la cinta no siempre van juntas -el 12-sep solo
 * hubo balanza-, y el músculo esquelético solo se sigue en NIKO.
 */
data class BodyEntry(
    /** El día, en formato ISO (2026-10-03). Uno por día: anotar otra vez el mismo día corrige. */
    val date: String,
    val weightKg: Double? = null,
    /** Cintura a la altura del ombligo. Es la medición de verdad; la balanza es tendencia. */
    val waistCm: Double? = null,
    /** El músculo esquelético que da la balanza. Lo que NIKO tiene que construir. */
    val skeletalKg: Double? = null,
    /**
     * La estatura, para cintura / estatura. Va en el pesaje y no en un ajuste aparte porque
     * así viaja sola al teléfono del coach; se pide una vez y los pesajes siguientes la copian.
     */
    val heightCm: Double? = null,
)

/** Un pesaje de un atleta, tal como lo baja el coach. Aparte de los propios, como sus sesiones. */
data class AthleteBody(val profileId: String, val entry: BodyEntry)

object BodyLog {

    /** El índice que se sigue: cintura / estatura, con la meta por debajo de 0.5. */
    const val WAIST_HEIGHT_GOAL = 0.5

    /** Desde qué hora del sábado se avisa si todavía no hay pesaje. Antes, se está durmiendo. */
    const val REMINDER_HOUR = 8

    /** Cintura / estatura con dos decimales, como la serie de los documentos. */
    fun waistToHeight(e: BodyEntry, heightCm: Double?): Double? {
        val cintura = e.waistCm ?: return null
        val alto = heightCm?.takeIf { it > 0 } ?: return null
        return Math.round(cintura / alto * 100) / 100.0
    }

    /** La última estatura anotada: la que se usa para el índice y la que copia el pesaje nuevo. */
    fun heightOf(entries: List<BodyEntry>): Double? =
        entries.sortedBy { it.date }.lastOrNull { it.heightCm != null }?.heightCm

    /** [entries] con la de su día reemplazada por [entry], ordenadas por fecha. */
    fun upsert(entries: List<BodyEntry>, entry: BodyEntry): List<BodyEntry> =
        (entries.filter { it.date != entry.date } + entry).sortedBy { it.date }

    fun remove(entries: List<BodyEntry>, date: String): List<BodyEntry> = entries.filter { it.date != date }

    /** Los pesajes de [profileId], del más viejo al más nuevo. */
    fun of(all: List<AthleteBody>, profileId: String): List<BodyEntry> =
        all.filter { it.profileId == profileId }.map { it.entry }.distinctBy { it.date }.sortedBy { it.date }

    /**
     * Lo que queda en el teléfono del coach después de bajar del servidor.
     *
     * Une y no reemplaza: los pesajes de antes de que existiera la tabla entraron sembrados
     * desde el código y no están en el servidor, y reemplazar los borraría. Por día, manda lo
     * que bajó: es lo que el atleta anotó o corrigió en su teléfono.
     */
    fun merge(local: List<AthleteBody>, downloaded: List<AthleteBody>): List<AthleteBody> {
        val bajados = downloaded.map { it.profileId to it.entry.date }.toSet()
        return (local.filter { (it.profileId to it.entry.date) !in bajados } + downloaded)
            .sortedWith(compareBy({ it.profileId }, { it.entry.date }))
    }

    /**
     * Si hay que recordar el pesaje: sábado, de [REMINDER_HOUR] en adelante, sin pesaje de hoy
     * y sin haberlo recordado ya hoy ([remindedOn]).
     */
    fun needsReminder(entries: List<BodyEntry>, now: LocalDateTime, remindedOn: String?): Boolean {
        if (now.dayOfWeek != DayOfWeek.SATURDAY || now.hour < REMINDER_HOUR) return false
        val hoy = now.toLocalDate().toString()
        return remindedOn != hoy && entries.none { it.date == hoy }
    }

    // ---------- Subir y bajar (como las sesiones, TD-126) ----------

    fun payloadOf(e: BodyEntry): String = toJson(e).toString()

    /**
     * Lo que falta subir: los pesajes cuyo contenido cambió desde la última subida. [ledger]
     * guarda, por día, la huella de lo que se subió.
     */
    fun pending(entries: List<BodyEntry>, ledger: Map<String, Int>): List<BodyEntry> =
        entries.filter { ledger[it.date] != payloadOf(it).hashCode() }

    /** Los días borrados aquí que ya habían subido: solo esos hay que borrar allá. */
    fun toDelete(removed: Collection<String>, ledger: Map<String, Int>): Set<String> = removed.filterTo(mutableSetOf()) { it in ledger }

    /** Filas de `body_logs` que devuelve PostgREST: `[{profile_id, payload}, ...]`. */
    fun parseRows(json: String): List<AthleteBody>? = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val row = arr.getJSONObject(i)
            val e = row.optJSONObject("payload")?.let(::fromJson) ?: return@mapNotNull null
            AthleteBody(row.getString("profile_id"), e)
        }
    }.getOrNull()

    // ---------- JSON ----------

    fun encode(list: List<BodyEntry>): String = JSONArray().apply { list.forEach { put(toJson(it)) } }.toString()

    fun decode(json: String): List<BodyEntry> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::fromJson) }
    }.getOrDefault(emptyList())

    fun encodeAthletes(list: List<AthleteBody>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("profileId", it.profileId).put("entry", toJson(it.entry))) }
    }.toString()

    fun decodeAthletes(json: String): List<AthleteBody> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val e = o.optJSONObject("entry")?.let(::fromJson) ?: return@mapNotNull null
            AthleteBody(o.optString("profileId").takeIf { it.isNotBlank() } ?: return@mapNotNull null, e)
        }
    }.getOrDefault(emptyList())

    private fun toJson(e: BodyEntry): JSONObject = JSONObject().apply {
        put("date", e.date)
        e.weightKg?.let { put("weightKg", it) }
        e.waistCm?.let { put("waistCm", it) }
        e.skeletalKg?.let { put("skeletalKg", it) }
        e.heightCm?.let { put("heightCm", it) }
    }

    private fun fromJson(o: JSONObject): BodyEntry? {
        val date = o.optString("date").takeIf { it.isNotBlank() } ?: return null
        if (runCatching { LocalDate.parse(date) }.isFailure) return null
        fun num(k: String): Double? = if (o.has(k) && !o.isNull(k)) o.optDouble(k).takeIf { !it.isNaN() } else null
        return BodyEntry(date, num("weightKg"), num("waistCm"), num("skeletalKg"), num("heightCm"))
    }

    /**
     * Lo que ya estaba en la serie de los documentos antes de que el app lo registrara: se
     * siembra una vez, por perfil, para que la curva no empiece vacía (ver
     * `docs/objetivos.md` y `docs/niko.md`).
     */
    val SEED: Map<String, List<BodyEntry>> = mapOf(
        "mauro" to listOf(
            BodyEntry("2026-09-12", weightKg = 83.5, heightCm = 181.0),
            BodyEntry("2026-09-19", weightKg = 84.4, waistCm = 93.5, heightCm = 181.0),
            BodyEntry("2026-09-26", weightKg = 84.7, waistCm = 93.0, heightCm = 181.0),
            BodyEntry("2026-10-03", weightKg = 84.6, waistCm = 94.0, skeletalKg = 33.5, heightCm = 181.0),
        ),
        "niko" to listOf(
            BodyEntry("2026-09-12", weightKg = 49.7, skeletalKg = 18.0, heightCm = 155.0),
            BodyEntry("2026-09-19", weightKg = 49.9, waistCm = 67.0, skeletalKg = 18.4, heightCm = 155.0),
            BodyEntry("2026-09-26", weightKg = 49.5, waistCm = 66.0, skeletalKg = 18.2, heightCm = 155.0),
            BodyEntry("2026-10-03", weightKg = 49.7, waistCm = 67.5, skeletalKg = 18.3, heightCm = 155.0),
        ),
    )
}
