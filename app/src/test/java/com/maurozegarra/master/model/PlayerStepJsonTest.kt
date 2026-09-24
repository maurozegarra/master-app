package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los pasos del player sobreviven al viaje hasta el servicio (TD-129). */
class PlayerStepJsonTest {

    /**
     * Un paso con TODOS los campos distintos de su valor por defecto.
     *
     * Es la red para el error que motivo este test: un campo nuevo de [PlayerStep] que
     * nadie agrega al serializador llega al servicio con su valor por defecto y se pierde
     * sin avisar. Asi se perdio la velocidad de la caminata el 19-sep. Si se agrega un
     * campo, se agrega aqui con un valor distinto del defecto, y si el serializador no lo
     * lleva, este test falla.
     */
    private val completo = PlayerStep(
        kind = StepKind.REST,
        title = "Walk",
        note = "Arms loose",
        ownerName = "Walk",
        ownerExerciseId = "ex_walk",
        exerciseIndex = 2,
        showVideo = false,
        side = "Izquierda",
        sideIndex = 1,
        sideCount = 2,
        progression = Progression.ROUNDS,
        distance = true,
        workoutName = "Warm Walk",
        workoutIndex = 3,
        totalWorkouts = 5,
        setIndex = 1,
        totalSets = 4,
        durationSec = 720,
        reps = 12,
        timeBased = false,
        display = DisplayMode.COUNTUP,
        confirm = ConfirmMode.MANUAL,
        finalCount = 5,
        beepSoundUri = "content://beep",
        alarm = false,
        colorArgb = 0xFF123456L,
        weighted = true,
        weightTotal = 36.0,
        weightLabel = "6 + 30",
        workoutBaseName = "Walks",
        variantName = "Long",
        rotating = true,
        secPerRep = 7,
        speedKmh = 6.5,
        weightType = WeightType.BARBELL,
        barWeight = 6.0,
        dumbbellCount = 1,
    )

    @Test
    fun `un paso con todos sus campos vuelve identico`() {
        assertEquals(listOf(completo), PlayerStepJson.decode(PlayerStepJson.encode(listOf(completo))))
    }

    @Test
    fun `el paso de prueba no deja ningun campo en su valor por defecto`() {
        // Si alguien agrega un campo a PlayerStep con valor por defecto y no lo pone arriba,
        // el test de ida y vuelta pasaria igual: este lo impide, comparando campo a campo
        // contra un paso por defecto.
        val defecto = PlayerStep(kind = StepKind.WORK, title = "")
        val campos = PlayerStep::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        campos.forEach { f ->
            f.isAccessible = true
            assertNotEquals("El campo '${f.name}' esta en su valor por defecto en el paso de prueba", f.get(defecto), f.get(completo))
        }
    }

    @Test
    fun `la velocidad de la caminata llega al servicio`() {
        val paso = PlayerStep(kind = StepKind.WORK, title = "Walk", speedKmh = 6.0)
        assertEquals(6.0, PlayerStepJson.decode(PlayerStepJson.encode(listOf(paso)))[0].speedKmh!!, 0.0)
    }

    @Test
    fun `un paso sin velocidad no inventa una`() {
        val paso = PlayerStep(kind = StepKind.WORK, title = "Curl-up")
        assertNull(PlayerStepJson.decode(PlayerStepJson.encode(listOf(paso)))[0].speedKmh)
    }
}
