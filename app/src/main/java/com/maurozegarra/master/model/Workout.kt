package com.maurozegarra.master.model

/** Cómo se mide el trabajo (WORK) de un ejercicio. */
enum class WorkMode { TIME, REPS }

/** Cómo se muestra el reloj de una etapa en el player. */
enum class DisplayMode { COUNTDOWN, STATIC, COUNTUP }

/** Confirmación para avanzar de una etapa a la siguiente. */
enum class ConfirmMode { AUTO, MANUAL }

/** Tipo de carga de un ejercicio por repeticiones. */
enum class WeightType { NONE, TOTAL, BARBELL, DUMBBELL }

/**
 * Configuración avanzada común a cada etapa (prepare/work/rest/cooldown).
 * [color] es ARGB empaquetado en Long. [finalCount] = segundos finales con
 * cuenta/alarma (0 = desactivado).
 */
data class StageConfig(
    val color: Long = COLOR_WORK,
    val display: DisplayMode = DisplayMode.COUNTDOWN,
    val alarm: Boolean = true,
    val finalCount: Int = 0,
    val confirm: ConfirmMode = ConfirmMode.AUTO,
    val beepSoundUri: String? = null,
    val beepSoundName: String? = null,
) {
    companion object {
        const val COLOR_PREPARE = 0xFFE2641EL
        const val COLOR_WORK = 0xFFC0392BL
        const val COLOR_REST = 0xFF1565C0L
        const val COLOR_COOLDOWN = 0xFF455A64L
    }
}

/**
 * Una serie: repeticiones y peso "crudo" (su significado depende de [WeightType]), y
 * opcionalmente su propio trabajo y descanso.
 *
 * [sec] y [restSec] valen null salvo que esa serie se salga de lo que dice el ejercicio, y
 * **null significa "usa el del ejercicio", que no es lo mismo que 0**: 0 es "sin
 * descanso". Es esa distincion la que deja una piramide descendente -6x10s, 30s, 4x10s,
 * 30s, 2x10s- en un solo ejercicio de 12 series donde solo dos llevan valor propio, y la
 * que protege lo guardado antes de que estos campos existieran: sin ellos manda el
 * ejercicio, igual que siempre.
 */
data class WorkSet(
    val reps: Int = 12,
    val weight: Double = 0.0,
    /** Segundos de trabajo de esta serie (solo [WorkMode.TIME]); null = los del ejercicio. */
    val sec: Int? = null,
    /** Segundos de descanso tras esta serie; null = los del ejercicio. */
    val restSec: Int? = null,
)

/**
 * Ejercicio dentro de un workout (modelo MOCK-B): tiene su propia estructura de
 * etapas (prepare → sets×(work, rest) → cooldown). En modo REPS soporta peso
 * por serie vía [setList] + [weightType].
 */
data class Exercise(
    val id: Long,
    val exerciseId: String,
    val name: String,
    val note: String = "",
    val prepareSec: Int = 0,
    val sets: Int = 1,
    val workMode: WorkMode = WorkMode.TIME,
    val workValue: Int = 30,
    /** Segundos estimados por repetición (solo REPS): pondera la barra de progreso. */
    val secPerRep: Int = 3,
    val restSec: Int = 30,
    val restSkipOnLastSet: Boolean = true,
    val cooldownSec: Int = 0,
    val weightType: WeightType = WeightType.NONE,
    val barWeight: Double = 20.0,
    val setList: List<WorkSet> = emptyList(),
    val prepareCfg: StageConfig = StageConfig(color = StageConfig.COLOR_PREPARE, finalCount = 3),
    val workCfg: StageConfig = StageConfig(color = StageConfig.COLOR_WORK),
    val restCfg: StageConfig = StageConfig(color = StageConfig.COLOR_REST, finalCount = 3),
    val cooldownCfg: StageConfig = StageConfig(color = StageConfig.COLOR_COOLDOWN),
    /**
     * Si en ESTE training se enseña el vídeo del ejercicio.
     *
     * Va aquí, en la instancia, y no junto al vídeo: qué vídeo demuestra un movimiento es
     * del movimiento —si no, habría que volver a adjuntarlo en cada training, y el
     * manifiesto publicado empareja por `exerciseId`—, pero si aquí se ve es de este
     * ejercicio en este training, igual que el color de la etapa. Guardarlo por movimiento
     * hacía que apagarlo en una copia lo apagase también en el training del que salió.
     *
     * Se borra al publicar (ver [Training.forPublishing]): es una preferencia de quien lo
     * tiene delante, y quien recibe un training asignado no podría deshacerla.
     */
    val showVideo: Boolean = true,
) {
    fun withStageColor(kind: StepKind, color: Long): Exercise = when (kind) {
        StepKind.PREP -> copy(prepareCfg = prepareCfg.copy(color = color))
        StepKind.WORK -> copy(workCfg = workCfg.copy(color = color))
        StepKind.REST -> copy(restCfg = restCfg.copy(color = color))
        StepKind.COOLDOWN -> copy(cooldownCfg = cooldownCfg.copy(color = color))
    }
}

/**
 * Variante de un workout rotativo: un conjunto con nombre propio de ejercicios
 * (ej. "Running", "Lower"). El player elige una variante por corrida.
 */
data class WorkoutVariant(
    val id: Long,
    val name: String = "",
    val exercises: List<Exercise> = emptyList(),
)

/**
 * Workout = bloque/agrupador ordenado de ejercicios (Warmup, Cardio, Lower…).
 * Si [rotating] es true, en cada corrida se reproduce UNA de [variants] según
 * [rotationIndex], que avanza al completar el training (rotación "por completar").
 */
data class Workout(
    val id: Long,
    val name: String = "",
    val exercises: List<Exercise> = emptyList(),
    val rotating: Boolean = false,
    val rotationIndex: Int = 0,
    val variants: List<WorkoutVariant> = emptyList(),
)

/** Variante activa de un workout rotativo (o null si no rota). */
fun Workout.activeVariant(): WorkoutVariant? =
    if (rotating && variants.isNotEmpty()) variants[rotationIndex % variants.size] else null

/** Ejercicios que se reproducen en la corrida actual (resuelve rotación). */
fun Workout.activeExercises(): List<Exercise> = activeVariant()?.exercises ?: exercises

/** Nombre a mostrar en la corrida actual (variante o nombre del workout). */
fun Workout.activeName(): String = activeVariant()?.name?.ifBlank { name } ?: name

/** Indica si el workout tiene contenido reproducible. */
fun Workout.hasContent(): Boolean =
    exercises.isNotEmpty() || variants.any { it.exercises.isNotEmpty() }

/**
 * Copia independiente del workout: reasigna el id propio, el de cada ejercicio, el de
 * cada variante y el de los ejercicios dentro de cada variante.
 *
 * Reasignar las variantes es la parte que se olvida fácil: `copy()` de un data class
 * arrastra [variants] tal cual si no se la nombra, y la copia queda compartiendo ids
 * con el original. Hoy eso no se nota porque todo lookup del editor está acotado por
 * workout, pero esa es justamente la invariante que permite que los lookups sean
 * seguros; direccionar una variante por id sin el contexto de su workout resolvería al
 * objeto equivocado en silencio.
 *
 * [rotationIndex] vuelve a 0: la copia arranca limpia en vez de heredar en qué variante
 * iba el original.
 */
fun Workout.deepCopy(newId: () -> Long): Workout = copy(
    id = newId(),
    exercises = exercises.map { it.copy(id = newId()) },
    rotationIndex = 0,
    variants = variants.map { v ->
        v.copy(id = newId(), exercises = v.exercises.map { it.copy(id = newId()) })
    },
)

/**
 * Training = nivel superior que agrupa workouts y es lo que se EJECUTA de corrido
 * en el player (ej. "Hybrid Strength").
 */
data class Training(
    val id: Long,
    val name: String = "",
    val workouts: List<Workout> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /**
     * Identidad estable entre dispositivos.
     *
     * [id] es un contador local sacado del reloj: sirve para distinguir trainings dentro
     * de un teléfono, pero dos teléfonos generan el mismo número sin problema. En cuanto
     * un training venga asignado desde fuera —que es a donde va TD-063— hace falta algo
     * que no colisione y que sobreviva a exportar e importar.
     *
     * Vacío significa "todavía sin asignar": los trainings guardados antes de que
     * existiera este campo lo reciben al cargarse.
     */
    val uid: String = "",
    /**
     * true si el training llega asignado desde fuera, en vez de haberlo creado el propio
     * usuario. Se sincroniza: al dejar de estar asignado desaparece, y editarlo en local
     * no tendria sentido porque la siguiente sincronizacion lo pisaria.
     */
    val assigned: Boolean = false,
    /**
     * Si al terminar se le pregunta cómo se sintió (TD-089).
     *
     * Va por training y no siempre porque la pregunta es sobre dolor: tiene sentido en una
     * rutina de rehabilitación y no después de un cardio. Hoy solo la encienden los dos
     * lumbares, desde el código; el editor todavía no la ofrece.
     */
    val tracksPain: Boolean = false,
)

/**
 * Rellena el [Training.uid] de los trainings que no lo tengan, dejando intactos los que
 * ya lo tienen.
 *
 * Los guardados antes de que existiera el campo llegan sin él. Quien llame debe
 * **persistir el resultado**: si solo se rellenara en memoria, cada arranque daría un uid
 * distinto y la identidad dejaría de ser estable, que es justo lo único que aporta.
 *
 * El generador se inyecta para poder probar esto sin depender de UUID.
 */
fun List<Training>.withUids(newUid: () -> String): List<Training> =
    map { if (it.uid.isBlank()) it.copy(uid = newUid()) else it }

/**
 * Copia independiente de un training, con identidad propia.
 *
 * Las tres cosas que cambian son las tres que definen "otro training": id local, [uid]
 * estable y, sobre todo, [Training.assigned] a false. Duplicar es como se hace propio uno
 * que llegó de fuera; si la copia siguiera marcada como asignada, la siguiente
 * sincronización la retiraría por no venir en la asignación y el usuario vería
 * desaparecer solo el training que acababa de crear.
 */
fun Training.duplicate(newId: () -> Long, newUid: () -> String, name: String, now: Long): Training = copy(
    id = newId(),
    uid = newUid(),
    assigned = false,
    name = name,
    workouts = workouts.map { it.deepCopy(newId) },
    createdAt = now,
    updatedAt = now,
)

/**
 * El training tal y como debe subirse al repartirlo.
 *
 * Deja fuera lo que es de quien lo tiene delante y no del que lo recibe:
 *
 * - **[Training.assigned] a false**, porque la insignia la pone quien recibe, no quien
 *   reparte.
 * - **[Exercise.showVideo] a true** en todos los ejercicios, los de los workouts simples y
 *   los de las variantes. Apagar un vídeo es una preferencia de quien mira, y publicarla
 *   se la impondría a los demás sin salida: un training asignado no se edita, así que
 *   quien lo recibiera no podría volver a encenderlo.
 *
 * Es una función aparte y pura para poder fijarla con un test: es lo único que impide que
 * algo local se le cuele a otra persona.
 */
fun Training.forPublishing(): Training = copy(
    assigned = false,
    workouts = workouts.map { w ->
        w.copy(
            exercises = w.exercises.map { it.copy(showVideo = true) },
            variants = w.variants.map { v -> v.copy(exercises = v.exercises.map { it.copy(showVideo = true) }) },
        )
    },
)

/** Devuelve la serie [i] del ejercicio, con valores por defecto si falta. */
fun Exercise.setAt(i: Int): WorkSet = setList.getOrElse(i) { WorkSet(reps = workValue) }

/** Segundos de trabajo de la serie [i]: los suyos si los tiene, si no los del ejercicio. */
fun Exercise.workSecAt(i: Int): Int = setAt(i).sec ?: workValue

/** Segundos de descanso tras la serie [i]: los suyos si los tiene, si no los del ejercicio. */
fun Exercise.restSecAt(i: Int): Int = setAt(i).restSec ?: restSec

/**
 * [setList] con exactamente una entrada por serie, rellenando las que falten.
 *
 * Lo necesita el editor: para darle valor propio a la serie 6 hay que tener las seis.
 */
fun Exercise.materializedSets(): List<WorkSet> = (0 until sets.coerceAtLeast(1)).map { setAt(it) }

/**
 * El ejercicio con su lista de series al dia despues de tocar el trabajo, el modo o el
 * tipo de carga. El editor la llama tras cada uno de esos cambios.
 *
 * Sin carga, las repeticiones de cada serie no se ensenan en ninguna parte -la lista
 * existe solo porque alguna serie se llevo su propio tiempo-, asi que no pueden quedarse
 * en el valor que tenia el ejercicio el dia que se creo la lista: divergirian del numero
 * de arriba sin que nadie pueda verlo ni arreglarlo. Con carga cada serie ensena las
 * suyas y mandan ellas, que es lo de siempre.
 *
 * Lo que nunca hace es tirar la lista: ahi viven ahora los tiempos propios de cada serie,
 * y quitarle el peso a un ejercicio no es motivo para perderlos.
 */
fun Exercise.normalizedSets(): Exercise =
    if (setList.isEmpty() || weightType != WeightType.NONE) this
    else copy(setList = setList.map { it.copy(reps = workValue, weight = 0.0) })

/** Peso total (kg) de una serie según el tipo de carga del ejercicio. */
fun Exercise.weightTotal(s: WorkSet): Double = when (weightType) {
    WeightType.BARBELL -> barWeight + s.weight
    WeightType.DUMBBELL -> 2.0 * s.weight
    WeightType.TOTAL -> s.weight
    WeightType.NONE -> 0.0
}

/** Indica si el ejercicio lleva peso (reps + tipo de carga distinto de NONE). */
val Exercise.isWeighted: Boolean
    get() = workMode == WorkMode.REPS && weightType != WeightType.NONE

/**
 * Definición de un ejercicio del catálogo. [custom] = creado por el usuario.
 */
data class ExerciseDef(
    val id: String,
    val name: String,
    val custom: Boolean = false,
)

/** Estado de una sesión registrada. */
enum class SessionStatus { COMPLETED, PARTIAL }

/**
 * De dónde sale un registro del historial (TD-101).
 *
 * El historial tiene que ser **corregible pero auditable**: que se pueda arreglar un dato
 * malo, y que nunca se confunda lo que se midió con lo que se dedujo. Sin esto, la sesión
 * que se reconstruyó desde la rutina (TD-090) es indistinguible de las que cronometró el
 * player, y dentro de unas semanas nadie sabría cuál es cuál.
 */
enum class SessionSource {
    /** La midió el player de principio a fin. Es el caso normal. */
    MEASURED,

    /** Se armó desde la rutina, sin que el player estuviera delante. */
    RECONSTRUCTED,

    /** La midió el player y después se corrigió a mano. */
    EDITED,
}

/** Una serie completada: reps, peso (kg) y duración (s) según corresponda. */
data class SetRecord(
    val reps: Int = 0,
    val weightKg: Double = 0.0,
    val durationSec: Int = 0,
    val skipped: Boolean = false,
)

enum class ExerciseStatus {
    COMPLETED,
    PARTIAL,
    SKIPPED,
}

/** Registro de un ejercicio dentro de una sesión (series completadas). */
data class ExerciseRecord(
    val exerciseId: String,
    val name: String,
    val workoutName: String,
    val workoutIndex: Int,
    /**
     * Posición del ejercicio dentro de su workout, que es como se ordena el historial.
     *
     * Antes se ordenaba por nombre, y el historial contaba una sesión que nadie hizo: en
     * los tres de McGill el bird dog salía primero y el curl-up cuarto, por la B y la C.
     * Los registros viejos llegan sin este campo y se quedan como se guardaron; su orden
     * real ya no está en ninguna parte.
     */
    val exerciseIndex: Int = 0,
    val setsCompleted: Int,
    val totalSets: Int,
    val sets: List<SetRecord>,
    val timeBased: Boolean,
    val totalExercisesInWorkout: Int = 0,
    val feedbackDeltaKg: Double? = null,
    val status: ExerciseStatus = ExerciseStatus.COMPLETED,
)

/** Registro de una sesión de entrenamiento (completa o parcial). */
data class SessionLog(
    val id: Long,
    val trainingId: Long,
    val trainingName: String,
    val completedAt: Long,
    val startedAt: Long = 0L,
    val status: SessionStatus = SessionStatus.COMPLETED,
    val exercises: List<ExerciseRecord> = emptyList(),
    val durationSec: Int = 0,
    /**
     * De dónde sale este registro. Por defecto [SessionSource.MEASURED], que es lo que son
     * todas las sesiones guardadas antes de que el campo existiera: las escribió el player.
     * La única excepción se corrige en su propia migración.
     */
    val source: SessionSource = SessionSource.MEASURED,
    /**
     * Cómo se sintió, preguntado al terminar (TD-089).
     *
     * Null es "no contestó", que no es lo mismo que cero: un cero es un dato y un hueco no.
     * [radiating] es la pregunta que decide si un ejercicio sigue en la rutina —si el dolor
     * baja hacia la pierna, sale ese día—, y por eso va aparte del número.
     */
    val painBefore: Int? = null,
    val painAfter: Int? = null,
    val radiating: Boolean? = null,
    val note: String = "",
)

/** true si la sesión tiene algo anotado de cómo se sintió. */
fun SessionLog.hasFeedback(): Boolean =
    painBefore != null || painAfter != null || radiating != null || note.isNotBlank()

/**
 * Los `exerciseId` que aparecen en algun training, variantes incluidas.
 *
 * Sirve para saber si un ejercicio propio esta en uso antes de tocarlo: borrar uno que
 * algun training use dejaria a ese ejercicio sin nombre de catalogo.
 */
fun List<Training>.usedExerciseIds(): Set<String> {
    val out = mutableSetOf<String>()
    forEach { t ->
        t.workouts.forEach { w ->
            w.exercises.forEach { out.add(it.exerciseId) }
            w.variants.forEach { v -> v.exercises.forEach { out.add(it.exerciseId) } }
        }
    }
    return out
}

/**
 * La sesión con sus ejercicios en el orden que manda [training], o **null si no se puede
 * saber con certeza**.
 *
 * Las sesiones guardadas antes de TD-099 se escribieron por orden alfabético y su posición
 * real no se guardó, pero sí se puede deducir: cada registro dice en qué workout estaba y
 * de qué ejercicio era, y con eso se lee en qué posición va.
 *
 * Devuelve null en cuanto algo no cuadra —un workout que ya no existe, un ejercicio que no
 * está donde decía el registro, o dos apariciones del mismo ejercicio en ese workout—
 * porque entonces el training cambió desde aquel día y reordenar sería inventar. Mejor una
 * sesión en alfabético que una reordenada a ciegas.
 *
 * Es idempotente: aplicarla a una sesión que ya está bien ordenada devuelve lo mismo.
 */
fun SessionLog.reorderedFrom(training: Training): SessionLog? {
    val conPosicion = exercises.map { er ->
        val w = training.workouts.getOrNull(er.workoutIndex) ?: return null
        val enEseWorkout = w.exercises.withIndex().filter { it.value.exerciseId == er.exerciseId }
        val unico = enEseWorkout.singleOrNull() ?: return null
        er.copy(exerciseIndex = unico.index)
    }
    return copy(exercises = conPosicion.sortedWith(compareBy({ it.workoutIndex }, { it.exerciseIndex })))
}

/** Fecha de la última sesión registrada de cada training (id → completedAt). */
fun lastTrainedAt(sessions: List<SessionLog>): Map<Long, Long> {
    val out = HashMap<Long, Long>()
    for (s in sessions) {
        val prev = out[s.trainingId]
        if (prev == null || s.completedAt > prev) out[s.trainingId] = s.completedAt
    }
    return out
}

/**
 * Trainings ordenados por uso real: primero los que se entrenaron, del más reciente al
 * más antiguo según el historial; después los que nunca se entrenaron, por [Training.updatedAt].
 *
 * El criterio es el historial y no `updatedAt` porque la lista acumula trainings de
 * prueba que nunca se ejercitaron: ordenarlos por edición los pondría arriba y taparía
 * el que de verdad se usa.
 */
fun List<Training>.sortedByLastTrained(sessions: List<SessionLog>): List<Training> {
    val last = lastTrainedAt(sessions)
    return sortedWith(
        compareByDescending<Training> { last[it.id] ?: Long.MIN_VALUE }
            .thenByDescending { it.updatedAt }
    )
}
