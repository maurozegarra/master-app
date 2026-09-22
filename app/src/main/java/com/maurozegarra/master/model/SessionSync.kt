package com.maurozegarra.master.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Una sesión de un atleta, tal como la baja el coach (TD-126).
 *
 * Va aparte de las sesiones propias a propósito: el historial del coach es suyo, y mezclar
 * ahí las de NIKO haría que su calendario, sus rachas y lo que se lee de su espalda
 * contaran entrenamientos que no hizo él.
 */
data class AthleteSession(val profileId: String, val session: SessionLog)

/**
 * La parte pura de subir y bajar sesiones (TD-126): qué se sube, en qué forma, y cómo se
 * leen de vuelta. Sin Android ni red, para poder probarla.
 */
object SessionSync {

    /** La sesión como la guarda el teléfono, UN objeto: es el payload que viaja. */
    fun payloadOf(session: SessionLog): String =
        JSONArray(SessionJson.encode(listOf(session))).getJSONObject(0).toString()

    /**
     * Lo que falta subir: sesiones de trainings ASIGNADOS a este teléfono cuyo contenido
     * cambió desde la última subida.
     *
     * [ledger] guarda, por sesión, la huella del payload que se subió. Una sesión se vuelve
     * a subir si su huella cambia, que es justo lo que pasa cuando se contesta el dolor o
     * el feedback después de guardarla. Lo que el servidor rechazó por no estar asignado
     * también queda en el ledger: no se reintenta hasta que cambie algo.
     *
     * Solo cuentan los trainings asignados -decisión del usuario-: lo que un atleta entrena
     * por su cuenta no es asunto del coach. El servidor lo comprueba otra vez.
     */
    fun pending(
        sessions: List<SessionLog>,
        trainings: List<Training>,
        ledger: Map<Long, Int>,
    ): List<Pending> {
        val uidDeAsignado = trainings.filter { it.assigned && it.uid.isNotBlank() }.associate { it.id to it.uid }
        return sessions.mapNotNull { s ->
            val uid = uidDeAsignado[s.trainingId] ?: return@mapNotNull null
            val payload = payloadOf(s)
            if (ledger[s.id] == payload.hashCode()) null else Pending(s, uid, payload)
        }
    }

    /**
     * De las sesiones que se acaban de borrar en este teléfono, las que hay que borrar
     * también en el servidor (TD-149): las que están en el [ledger], porque solo esas
     * llegaron a subir.
     *
     * Sin esto, el borrado se quedaba en el teléfono y la copia del servidor seguía viva: el
     * 21-sep el coach le enseñó el app a NIKO en su teléfono, borró las dos sesiones de la
     * demostración, y el asistente las leyó igual como entrenamientos de ella.
     *
     * Las que nunca subieron -de trainings no asignados, o de un teléfono sin perfil- no
     * tienen nada que borrar allá, y preguntar por ellas sería ir a la red por nada.
     */
    fun toDelete(removed: Collection<Long>, ledger: Map<Long, Int>): Set<Long> =
        removed.filterTo(mutableSetOf()) { it in ledger }

    data class Pending(val session: SessionLog, val trainingUid: String, val payload: String) {
        val fingerprint: Int get() = payload.hashCode()
    }

    /** Filas de `sessions` que devuelve PostgREST: `[{profile_id, payload}, ...]`. */
    fun parseRows(json: String): List<AthleteSession>? = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val row = arr.getJSONObject(i)
            val payload = row.optJSONObject("payload") ?: return@mapNotNull null
            SessionJson.decode(JSONArray().put(payload).toString()).firstOrNull()
                ?.let { AthleteSession(row.getString("profile_id"), it) }
        }
    }.getOrNull()

    fun encodeAthleteSessions(list: List<AthleteSession>): String {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("profileId", a.profileId)
                    .put("session", JSONObject(payloadOf(a.session))),
            )
        }
        return arr.toString()
    }

    fun decodeAthleteSessions(json: String): List<AthleteSession> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val s = o.optJSONObject("session") ?: return@mapNotNull null
            SessionJson.decode(JSONArray().put(s).toString()).firstOrNull()
                ?.let { AthleteSession(o.getString("profileId"), it) }
        }
    }.getOrDefault(emptyList())
}
