package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El tablero de la pantalla principal (TD-194): lo que se calcula sin dibujar. */
class DashboardTest {

    @Test
    fun `los dos puntos laten pum-pum y descansan tenues`() {
        // El golpe fuerte llega casi a 1 al empezar el segundo.
        assertTrue(Dashboard.beat(70) > 0.99f)
        // Entre los dos golpes baja, y el segundo es más chico que el primero.
        val valle = Dashboard.beat(160)
        val segundo = Dashboard.beat(260)
        assertTrue(valle < segundo)
        assertTrue(segundo in 0.55f..0.65f)
        // El resto del segundo, en reposo: el dibujo lo deja tenue, no apagado.
        assertTrue(Dashboard.beat(700) < 0.01f)
    }

    @Test
    fun `el reloj se corre unos pocos dp, en un ciclo de 15 minutos`() {
        val corrimientos = (0 until 60).map { Dashboard.burnInShift(it) }
        assertTrue(corrimientos.all { (x, y) -> x in -2..2 && y in -1..1 })
        assertEquals(15, corrimientos.take(15).toSet().size)
        assertEquals(Dashboard.burnInShift(3), Dashboard.burnInShift(18))
    }

    @Test
    fun `el agua en litros desde el litro`() {
        assertEquals("800 ml", Dashboard.water(800))
        assertEquals("1.6 L", Dashboard.water(1600))
        assertEquals("2.0 L", Dashboard.water(2000))
    }

    @Test
    fun `el barrido vacia la regla en el primer medio segundo del minuto`() {
        assertEquals(1f, Dashboard.wipe(0.0))
        assertEquals(0f, Dashboard.wipe(0.6))
        assertEquals(0f, Dashboard.wipe(30.0))
    }
}
