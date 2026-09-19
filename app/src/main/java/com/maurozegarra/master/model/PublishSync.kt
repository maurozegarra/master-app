package com.maurozegarra.master.model

/**
 * Qué hay que volver a publicar (TD-132).
 *
 * Lo que un atleta recibe es lo que se publicó **al asignar**. Si el training cambia
 * después -una revisión del coach en el código, o una edición suya en el app-, el cambio se
 * queda en el teléfono del coach y el atleta sigue con la versión vieja. Con las rutinas de
 * NIKO ajustándose sesión a sesión, eso pasa casi a diario, y depende de que alguien se
 * acuerde de reasignar: el día que no, ella entrena con lo viejo sin que nadie lo note.
 *
 * El [ledger] guarda, por training publicado, la huella de lo último que se mandó. Un
 * training vuelve a publicarse cuando su huella cambia. **Lo que nunca se publicó no entra**:
 * un training propio que el coach nunca reparte no tiene por qué viajar a ninguna parte.
 */
object PublishSync {

    /** Lo que viaja: el training sin lo que es de quien lo reparte (ver [forPublishing]). */
    fun payloadOf(training: Training): String = TrainingJson.toJson(training.forPublishing()).toString()

    fun fingerprintOf(training: Training): Int = payloadOf(training).hashCode()

    /**
     * Los trainings que hay que volver a publicar, con su huella nueva.
     *
     * Entra uno cuando su huella cambió, y también cuando **alguien lo tiene asignado pero no
     * hay huella**: son los que se repartieron antes de que existiera este mecanismo -NIKO 1
     * y NIKO 2 lo estaban-, y sin esto un cambio suyo no viajaría hasta que alguien los
     * reasignara a mano, que es justo lo que se quiere dejar de hacer.
     *
     * Lo que nunca se publicó y nadie tiene NO entra: un training propio del coach no tiene
     * por qué viajar a ninguna parte. Y los `assigned` tampoco: son los que este teléfono
     * RECIBIÓ, y republicarlos sería devolverle al dueño una copia de lo suyo.
     */
    fun toRepublish(
        trainings: List<Training>,
        ledger: Map<String, Int>,
        assignedUids: Set<String>,
    ): List<Pair<Training, Int>> =
        trainings
            .filter { !it.assigned && it.uid.isNotBlank() }
            .mapNotNull { t ->
                val actual = fingerprintOf(t)
                val publicada = ledger[t.uid]
                when {
                    publicada == null -> if (t.uid in assignedUids) t to actual else null
                    publicada != actual -> t to actual
                    else -> null
                }
            }
}
