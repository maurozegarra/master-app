package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué instrucciones suben, cuáles bajan y qué no se pisa (TD-139). */
class MediaSyncTest {

    private val sembrada = ExerciseMedia(instructions = listOf("Sentado, espalda recta.", "Gira despacio."))
    private val corregida = ExerciseMedia(instructions = listOf("Sentado, espalda recta.", "Gira SIN mover la cadera."))
    private val suya = ExerciseMedia(instructions = listOf("Ojo con la rodilla izquierda."))

    // ---------- El teléfono del coach publica ----------

    @Test
    fun `se publica lo que cambio, no todo lo que hay`() {
        val local = mapOf("ex_russian_twist" to sembrada, "ex_plank" to suya)
        val ledger = mapOf("ex_plank" to MediaSync.fingerprintOf(suya))
        assertEquals(listOf("ex_russian_twist"), MediaSync.toPublish(local, ledger).map { it.first })

        val alDia = ledger + ("ex_russian_twist" to MediaSync.fingerprintOf(sembrada))
        assertTrue(MediaSync.toPublish(local, alDia).isEmpty())

        // Corregir una instruccion la vuelve a mandar.
        val tocado = local + ("ex_russian_twist" to corregida)
        assertEquals(listOf("ex_russian_twist"), MediaSync.toPublish(tocado, alDia).map { it.first })
    }

    @Test
    fun `un ejercicio sin instrucciones no se publica`() {
        // Vaciar un campo no puede borrar el contenido de todos los telefonos.
        val local = mapOf("ex_plank" to ExerciseMedia())
        assertTrue(MediaSync.toPublish(local, emptyMap()).isEmpty())
    }

    // ---------- El teléfono del atleta aplica ----------

    @Test
    fun `lo que no existe en el telefono entra`() {
        val aplicar = MediaSync.toApply(
            remote = mapOf("ex_russian_twist" to sembrada),
            local = emptyMap(),
            ledger = emptyMap(),
            seeded = emptyMap(),
        )
        assertEquals(mapOf("ex_russian_twist" to sembrada), aplicar)
    }

    @Test
    fun `una version sembrada desde el codigo se deja reemplazar`() {
        // Es el caso de la primera sincronizacion: el telefono ya trae las del APK y el
        // servidor manda la version corregida. Sin esto no entraria nunca.
        val aplicar = MediaSync.toApply(
            remote = mapOf("ex_russian_twist" to corregida),
            local = mapOf("ex_russian_twist" to sembrada),
            ledger = emptyMap(),
            seeded = mapOf("ex_russian_twist" to listOf(sembrada)),
        )
        assertEquals(mapOf("ex_russian_twist" to corregida), aplicar)
    }

    @Test
    fun `lo que escribio quien tiene el telefono no se pisa`() {
        val aplicar = MediaSync.toApply(
            remote = mapOf("ex_plank" to corregida),
            local = mapOf("ex_plank" to suya),
            ledger = emptyMap(),
            seeded = mapOf("ex_plank" to listOf(sembrada)),
        )
        assertTrue(aplicar.isEmpty())
    }

    @Test
    fun `lo aplicado la vez anterior si se actualiza`() {
        val ledger = mapOf("ex_plank" to MediaSync.fingerprintOf(sembrada))
        val aplicar = MediaSync.toApply(
            remote = mapOf("ex_plank" to corregida),
            local = mapOf("ex_plank" to sembrada),
            ledger = ledger,
            seeded = emptyMap(),
        )
        assertEquals(mapOf("ex_plank" to corregida), aplicar)
    }

    @Test
    fun `lo que ya coincide no se vuelve a escribir`() {
        val aplicar = MediaSync.toApply(
            remote = mapOf("ex_plank" to sembrada),
            local = mapOf("ex_plank" to sembrada),
            ledger = emptyMap(),
            seeded = emptyMap(),
        )
        assertTrue(aplicar.isEmpty())
    }

    // ---------- El ida y vuelta con el servidor ----------

    @Test
    fun `las filas del servidor se leen como instrucciones`() {
        val filas = """[{"exercise_id":"ex_plank","instructions":["Uno","Dos"]},{"exercise_id":"","instructions":[]}]"""
        val leidas = MediaSync.parseRows(filas)!!
        assertEquals(1, leidas.size)
        assertEquals(listOf("Uno", "Dos"), leidas["ex_plank"]!!.instructions)
    }

    @Test
    fun `una respuesta rota no se confunde con una tabla vacia`() {
        assertNull(MediaSync.parseRows("esto no es json"))
    }

    @Test
    fun `la fila que se publica lleva el id y los pasos`() {
        val fila = org.json.JSONObject(MediaSync.rowOf("ex_plank", sembrada))
        assertEquals("ex_plank", fila.getString("exercise_id"))
        assertEquals(2, fila.getJSONArray("instructions").length())
    }

    @Test
    fun `lo personal del lumbar no se publica, sale el catalogo en espanol`() {
        // 1-oct: el servidor tenia el puente, el hip thrust y la caminata lateral de NIKO con
        // las instrucciones de el, en ingles.
        val personal = com.maurozegarra.master.data.MasterDefaults.lumbarInstructions()
        val catalogo = com.maurozegarra.master.data.MasterDefaults.catalogInstructions()
        val propia = ExerciseMedia(listOf("Escrita a mano en el telefono del coach"))
        val local = mapOf(
            "ex_glute_bridge" to personal.getValue("ex_glute_bridge"),
            "ex_hip_thrust" to personal.getValue("ex_hip_thrust"),
            "ex_lateral_band_walk" to personal.getValue("ex_lateral_band_walk"),
            "ex_curl_up" to personal.getValue("ex_curl_up"),
            "ex_teep" to propia,
        )
        val publico = MediaSync.publicVersion(local, personal, catalogo)
        listOf("ex_glute_bridge", "ex_hip_thrust", "ex_lateral_band_walk").forEach {
            assertEquals(it, catalogo.getValue(it), publico[it])
        }
        // Lo que escribio el coach a mano si sale, tal cual.
        assertEquals(propia, publico["ex_teep"])
        // Lo personal sin version de catalogo no sale.
        if (!catalogo.containsKey("ex_curl_up")) assertNull(publico["ex_curl_up"])

        // Y el telefono de NIKO cambia el ingles que bajo por el espanol: lo bajo del
        // servidor, asi que esta en su ledger, y no lo escribio ella.
        val ingles = personal.getValue("ex_glute_bridge")
        val aplicar = MediaSync.toApply(
            remote = mapOf("ex_glute_bridge" to catalogo.getValue("ex_glute_bridge")),
            local = mapOf("ex_glute_bridge" to ingles),
            ledger = mapOf("ex_glute_bridge" to MediaSync.fingerprintOf(ingles)),
            seeded = emptyMap(),
        )
        assertEquals(catalogo.getValue("ex_glute_bridge"), aplicar["ex_glute_bridge"])
    }
}
