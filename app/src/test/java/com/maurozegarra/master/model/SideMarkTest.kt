package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El lado como marca de ancho fijo (TD-153). */
class SideMarkTest {

    @Test
    fun `las direcciones son flechas, en los dos idiomas y con o sin tilde`() {
        // El 22-sep "Derecha" se salia por el borde y NIKO leia "HA".
        assertEquals("←", SideMark.of("Izquierda", 0, 2))
        assertEquals("→", SideMark.of("Derecha", 1, 2))
        assertEquals("←", SideMark.of("Left", 0, 2))
        assertEquals("→", SideMark.of("Right", 1, 2))
        assertEquals("↑", SideMark.of("Adelante", 0, 4))
        assertEquals("↓", SideMark.of("Atrás", 1, 4))
        assertEquals("↓", SideMark.of("atras", 1, 4))
    }

    @Test
    fun `una etiqueta que no es direccion se dibuja como posicion`() {
        assertEquals("○●○", SideMark.of("Con goma", 1, 3))
    }

    @Test
    fun `sin lado no hay marca`() {
        assertNull(SideMark.of("", 0, 0))
    }

    @Test
    fun `el motor dice que lado es y de cuantos`() {
        val e = Exercise(
            id = 1L, exerciseId = "ex_neck_iso", name = "Neck", sets = 1,
            workMode = WorkMode.TIME, workValue = 20,
            sides = listOf("Adelante", "Atrás", "Derecha", "Izquierda"),
        )
        val t = Training(id = 1L, name = "T", workouts = listOf(Workout(id = 1L, name = "W", exercises = listOf(e))))
        val trabajo = StepEngine.buildSteps(t).filter { it.kind == StepKind.WORK }
        assertEquals(listOf(0, 1, 2, 3), trabajo.map { it.sideIndex })
        assertEquals(setOf(4), trabajo.map { it.sideCount }.toSet())
    }
}
