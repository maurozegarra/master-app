package com.maurozegarra.master.data

import com.maurozegarra.master.model.HomeGym
import com.maurozegarra.master.model.Plates
import com.maurozegarra.master.model.WeightType
import com.maurozegarra.master.model.setAt
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Cada peso de una rutina del coach se puede armar con el equipo de la casa (TD-130).
 *
 * Es el punto 2 del checklist de docs/coach.md, hecho test para que no dependa de acordarse.
 * Nace del primer dia de NIKO: un hip thrust de 70 kg "totales" sobre una barra de 6, que
 * son 32 kg de disco por lado y con discos de 1.25 hacia arriba no existen. El usuario hizo
 * la cuenta entre series y le salio 71.
 */
class RoutinesFitEquipmentTest {

    private val rutinas = listOf(
        MasterDefaults.lumbarTraining("en"),
        MasterDefaults.lumbarBadDayTraining("en"),
        MasterDefaults.nikoGluteHeavy("en"),
    )

    @Test
    fun `cada peso con barra se arma con los discos que hay, sobre una barra que existe`() {
        rutinas.forEach { t ->
            t.workouts.flatMap { it.exercises }.filter { it.weightType == WeightType.BARBELL }.forEach { e ->
                assertTrue("${t.name} / ${e.name}: no hay barra de ${e.barWeight}", e.barWeight in HomeGym.BARS)
                (0 until e.sets).forEach { i ->
                    val discos = e.setAt(i).weight
                    if (Plates.perSide(discos) == null) {
                        fail("${t.name} / ${e.name} serie ${i + 1}: ${discos} kg de disco no se arman")
                    }
                }
            }
        }
    }

    @Test
    fun `cada peso con mancuerna es una mancuerna que existe`() {
        rutinas.forEach { t ->
            t.workouts.flatMap { it.exercises }.filter { it.weightType == WeightType.DUMBBELL }.forEach { e ->
                (0 until e.sets).forEach { i ->
                    val kg = e.setAt(i).weight
                    assertTrue("${t.name} / ${e.name} serie ${i + 1}: no hay mancuerna de $kg", kg in HomeGym.DUMBBELLS)
                }
            }
        }
    }

    @Test
    fun `nada va en kilos TOTALES salvo una maquina declarada`() {
        // El error original iba justo por aqui: el hip thrust de NIKO en TOTAL, que este test
        // no revisaria porque solo mira barras y mancuernas. TOTAL es para maquinas -la
        // pantorrillera, la prensa-, donde no hay discos por lado que calcular. Si un
        // ejercicio de estas rutinas lo usa, tiene que estar en esta lista a conciencia.
        val maquinas = emptySet<String>()
        rutinas.forEach { t ->
            t.workouts.flatMap { it.exercises }.filter { it.weightType == WeightType.TOTAL }.forEach { e ->
                assertTrue("${t.name} / ${e.name} va en kilos totales: con barra o mancuerna no se puede saber que cargar", e.exerciseId in maquinas)
            }
        }
    }

    @Test
    fun `todo ejercicio de la rutina de NIKO tiene instrucciones, y en espanol`() {
        // Punto 3 del checklist. El 19-sep tuvo que preguntar como se hacian dos ejercicios:
        // faltaban. Y ella no lee ingles, asi que tienen que venir en espanol.
        val catalogo = MasterDefaults.catalogInstructions()
        val v1Ingles = MasterDefaults.catalogInstructionsV1()
        MasterDefaults.nikoGluteHeavy("en").workouts.flatMap { it.exercises }.forEach { e ->
            val m = catalogo[e.exerciseId]
            assertTrue("${e.name} no tiene instrucciones en el catalogo", m != null && !m.isEmpty)
            assertTrue("${e.name} sigue con las instrucciones en ingles", m != v1Ingles[e.exerciseId])
        }
    }

    @Test
    fun `ninguna barra de NIKO se queda en los 20 por defecto`() {
        // El defecto no es un dato de nadie. Ella usa la EZ de 6 para hip thrust y rumano.
        MasterDefaults.nikoGluteHeavy("en").workouts.flatMap { it.exercises }
            .filter { it.weightType == WeightType.BARBELL }
            .forEach { assertTrue("${it.name} usa la barra por defecto", it.barWeight == 6.0) }
    }
}
