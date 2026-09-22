package com.maurozegarra.master.model

/** Tipo de etapa en el recorrido del player. */
enum class StepKind { PREP, WORK, REST, COOLDOWN }

/**
 * Paso "aplanado" de un program para reproducirlo: cada ejercicio se expande en
 * prepare → sets×(work, rest) → cooldown, encadenando todos los workouts del
 * program en orden.
 */
data class PlayerStep(
    val kind: StepKind,
    /** WORK: nombre del ejercicio. Otras etapas: vacío (la UI localiza el rótulo). */
    val title: String,
    /** Nota del ejercicio (ej. "each side", "alternado"). */
    val note: String = "",
    /** Ejercicio al que pertenece la etapa (para mostrar dueño en PREP/REST/COOLDOWN). */
    val ownerName: String = "",
    /** Id del catálogo del ejercicio dueño (para icono/animación). */
    val ownerExerciseId: String = "",
    /**
     * Posición del ejercicio dentro de su workout.
     *
     * No basta con [ownerExerciseId]: un workout puede repetir el mismo ejercicio del
     * catálogo, y sin distinguirlos reubicar la posición tras editar el training a mitad
     * de corrida caería en la primera aparición y haría repetir trabajo ya hecho.
     */
    val exerciseIndex: Int = 0,
    /**
     * Si este training enseña el vídeo del ejercicio. Viene de la instancia, no del
     * movimiento, y viaja en el paso porque el player pinta desde los pasos y no desde el
     * training —que ni siquiera tiene por qué estar cargado al reconectar con una corrida.
     */
    val showVideo: Boolean = true,
    /** Lado o dirección de este paso, si el ejercicio los tiene (TD-147). */
    val side: String = "",
    /** Qué lado es y de cuántos (TD-153): lo que dibuja la marca cuando el lado no es una
     *  dirección que se pueda poner como flecha. */
    val sideIndex: Int = 0,
    val sideCount: Int = 0,
    val workoutName: String = "",
    val workoutIndex: Int = 0,
    val totalWorkouts: Int = 1,
    /** Índice de serie (0-based) y total de series del ejercicio (solo WORK relevante). */
    val setIndex: Int = 0,
    val totalSets: Int = 1,
    val durationSec: Int = 0,
    val reps: Int = 0,
    /** true = etapa por tiempo; false = WORK por repeticiones (avance manual). */
    val timeBased: Boolean = true,
    val display: DisplayMode = DisplayMode.COUNTDOWN,
    val confirm: ConfirmMode = ConfirmMode.AUTO,
    val finalCount: Int = 0,
    val beepSoundUri: String? = null,
    val alarm: Boolean = true,
    val colorArgb: Long = StageConfig.COLOR_WORK,
    val weighted: Boolean = false,
    val weightTotal: Double = 0.0,
    val weightLabel: String = "",
    /** Nombre base del workout (sin resolver variante), para agrupar en la vista previa. */
    val workoutBaseName: String = "",
    /** Nombre de la variante activa si el workout es rotativo (vacío si no rota). */
    val variantName: String = "",
    /** true si el workout es rotativo. */
    val rotating: Boolean = false,
    /** Segundos estimados por repetición (solo WORK por reps): pondera la barra de progreso. */
    val secPerRep: Int = 3,
    /** Velocidad prescrita en km/h, si el ejercicio la lleva (TD-124). */
    val speedKmh: Double? = null,
    /**
     * Con qué se carga, para que el player pueda decir QUÉ poner y no solo cuánto (TD-130):
     * los discos por lado de una barra, o cuántas mancuernas y de cuánto.
     */
    val weightType: WeightType = WeightType.NONE,
    /** Peso de la barra, si [weightType] es BARBELL. */
    val barWeight: Double = 0.0,
    /** Cuántas mancuernas, si [weightType] es DUMBBELL. */
    val dumbbellCount: Int = 2,
) {
    /** La etapa requiere confirmación manual (TAP) para avanzar. */
    val manual: Boolean
        get() = (kind == StepKind.WORK && !timeBased) ||
            confirm == ConfirmMode.MANUAL ||
            display == DisplayMode.STATIC

    /** Duración estimada en segundos para ponderar la barra de progreso. */
    val estimatedSec: Int
        get() = if (timeBased) durationSec else reps * secPerRep
}
