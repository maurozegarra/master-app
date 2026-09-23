package com.maurozegarra.master.model

/**
 * Cómo progresa un ejercicio, que es lo que decide qué se le pregunta (TD-152).
 *
 * Hasta TD-152 solo se preguntaba por el peso, porque la respuesta era un ajuste en kilos.
 * Todo lo que va con el peso del cuerpo, los aguantes y los rounds de Muay Thai pasaba sin
 * dejar un dato: el 22-sep NIKO hizo remo invertido, flexiones y colgarse de la barra, y lo
 * único que se supo es que el remo le costó, porque el usuario la vio.
 */
enum class Progression {
    /** Kilos. Tiene su propia tarjeta desde TD-117 y no cambia. */
    LOAD,
    /** Repeticiones con el peso del cuerpo: +2, y en el techo una variante más difícil. */
    REPS,
    /** Aguantes: +5 s. */
    TIME,
    /**
     * Rounds e intervalos. El round no se alarga -3 minutos es el formato del Muay Thai-: se
     * acorta el descanso y después se suma uno.
     */
    ROUNDS,
    /** Nada que preguntar: calentamiento, movilidad, caminata, y un protocolo fijo como McGill. */
    NONE,
}

/** Lo que se contesta: -1 costó, 0 bien, +1 fácil. Igual que el peso, en tres niveles. */
object Effort {
    const val HARD = -1
    const val RIGHT = 0
    const val EASY = 1

    /**
     * Los ejercicios que se hacen por rounds o intervalos, preguntados uno por uno.
     *
     * La cuerda no está: en el calentamiento son dos minutos para entrar en calor, y cuando
     * va por rounds (NIKO 5) la rutina lo declara en el ejercicio.
     */
    private val ROUNDS = setOf(
        "ex_heavy_bag", "ex_shadow_boxing", "ex_long_knees", "ex_deep_knees",
        "ex_burpees", "ex_tire_jumping",
    )

    /** Movilidad y calentamiento: se hacen para moverse, no para progresar. */
    private val NONE = setOf(
        "ex_walk", "ex_cat_cow", "ex_hip_hinge", "ex_hip_rotation", "ex_90_90",
        "ex_shoulder_rotation", "ex_neck_lr", "ex_neck_circle", "ex_chest_opening",
        "ex_trunk_rotation", "ex_knee_rotation", "ex_ankle_rotation", "ex_knee_circle",
        "ex_deep_squat", "ex_front_side_stretch", "ex_kneeling_spine_extension",
        "ex_cobra_to_child", "ex_thread_the_needle", "ex_open_book", "ex_seated_side_bend",
        "ex_thoracic_rotation_half_kneeling",
    )

    /**
     * La progresión de [e]: la que declara la rutina y, si no declara ninguna, la que le
     * corresponde por lo que es.
     *
     * Deducirla deja que funcione en cualquier training -los del usuario incluidos- sin
     * tener que marcar ejercicio por ejercicio. Declararla es para las excepciones: McGill
     * va por tiempo pero es un protocolo fijo, y la cuerda del calentamiento no es un round.
     */
    fun of(e: Exercise): Progression = e.progression ?: when {
        e.isWeighted -> Progression.LOAD
        e.exerciseId in NONE -> Progression.NONE
        e.exerciseId in ROUNDS -> Progression.ROUNDS
        e.workMode == WorkMode.TIME -> Progression.TIME
        else -> Progression.REPS
    }

    /**
     * Si esta serie pregunta cómo fue: **todas**, en cada lado.
     *
     * La primera versión preguntaba solo en la última serie, pensando que las series con el
     * peso del cuerpo son iguales entre sí. El usuario lo tumbó el mismo día, y con razón:
     * *"puede que en una primera serie se sienta pesado porque el músculo apenas está
     * despertando, la segunda y tercera se sienta bien"*. Con una sola respuesta eso se lee
     * "bien" y se pierde lo que dice -que hace falta una serie de entrada, o menos
     * repeticiones en la primera-. Si marcar cada serie resulta demasiado, se vuelve atrás,
     * pero se empieza por el detalle.
     */
    fun asks(step: PlayerStep): Boolean {
        if (step.weighted) return false
        return when (step.progression) {
            Progression.REPS, Progression.TIME, Progression.ROUNDS -> true
            Progression.LOAD, Progression.NONE -> false
        }
    }

    /**
     * Si la tarjeta de [step] -un trabajo o el descanso que le sigue- tiene que salir.
     *
     * También en el descanso: durante un aguante o un round no se puede tocar la pantalla,
     * y es en el minuto de después cuando se contesta. El descanso lleva la misma serie y el
     * mismo lado que el trabajo que cierra, así que la clave es la misma.
     */
    fun cardFor(step: PlayerStep): Boolean =
        (step.kind == StepKind.WORK || step.kind == StepKind.REST) && asks(step)
}
