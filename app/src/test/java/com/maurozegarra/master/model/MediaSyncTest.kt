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
}
