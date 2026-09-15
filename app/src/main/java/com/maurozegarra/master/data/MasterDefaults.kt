package com.maurozegarra.master.data

import com.maurozegarra.master.model.Exercise
import com.maurozegarra.master.model.ExerciseMedia
import com.maurozegarra.master.model.ExerciseRecord
import com.maurozegarra.master.model.SessionLog
import com.maurozegarra.master.model.SessionSource
import com.maurozegarra.master.model.SessionStatus
import com.maurozegarra.master.model.SetRecord
import com.maurozegarra.master.model.Training
import com.maurozegarra.master.model.WorkMode
import com.maurozegarra.master.model.setAt
import com.maurozegarra.master.model.workSecAt
import com.maurozegarra.master.model.WorkSet
import com.maurozegarra.master.model.WeightType
import com.maurozegarra.master.model.Workout
import com.maurozegarra.master.model.WorkoutVariant

/**
 * Datos por defecto que se siembran en la primera ejecución.
 * Incluye el Training "Master" con Warmup, Base, Cardio (rotación de 4 opciones)
 * y un bloque de fuerza que alterna Lower/Upper. La rotación avanza al completar.
 */
object MasterDefaults {

    fun masterTraining(lang: String): Training {
        var seq = 1L
        fun id(): Long = seq++

        fun ex(
            exerciseId: String,
            note: String = "",
            sets: Int = 1,
            mode: WorkMode = WorkMode.TIME,
            work: Int = 30,
            prep: Int = 0,
            rest: Int = 0,
            cooldown: Int = 0,
            weightType: WeightType = WeightType.NONE,
            barWeight: Double = 20.0,
            setList: List<WorkSet> = emptyList(),
        ): Exercise = Exercise(
            id = id(),
            exerciseId = exerciseId,
            name = ExerciseCatalog.name(exerciseId, lang),
            note = note,
            prepareSec = prep,
            sets = sets,
            workMode = mode,
            workValue = work,
            restSec = rest,
            restSkipOnLastSet = true,
            cooldownSec = cooldown,
            weightType = weightType,
            barWeight = barWeight,
            setList = setList,
        )

        fun reps(exerciseId: String, count: Int, note: String = ""): Exercise =
            ex(exerciseId, note = note, mode = WorkMode.REPS, work = count)

        // ----- Warmup -----
        val warmup = Workout(
            id = id(),
            name = if (lang == "es") "Calentamiento" else "Warmup",
            exercises = listOf(
                reps("ex_neck_lr", 20),
                reps("ex_neck_circle", 10, note = sideNote(lang)),
                reps("ex_chest_opening", 20),
                reps("ex_shoulder_rotation", 10, note = sideNote(lang)),
                reps("ex_trunk_rotation", 20),
                reps("ex_hip_rotation", 10, note = sideNote(lang)),
                reps("ex_knee_rotation", 10, note = sideNote(lang)),
                reps("ex_ankle_rotation", 10, note = sideNote(lang)),
            ),
        )

        // ----- Base -----
        val base = Workout(
            id = id(),
            name = "Base",
            exercises = listOf(
                ex("ex_jumping_jacks", sets = 2, mode = WorkMode.TIME, work = 30, prep = 10, rest = 10, cooldown = 30),
                ex("ex_burpees", sets = 2, mode = WorkMode.TIME, work = 45, prep = 10, rest = 15, cooldown = 60),
                reps("ex_knee_circle", 20),
                reps("ex_90_90", 20),
            ),
        )

        // ----- Cardio (rotación de 4) -----
        val cardio = Workout(
            id = id(),
            name = "Cardio",
            rotating = true,
            variants = listOf(
                WorkoutVariant(
                    id = id(), name = ExerciseCatalog.name("ex_rope_jumping", lang),
                    exercises = listOf(ex("ex_rope_jumping", sets = 15, work = 30, prep = 10, rest = 10, cooldown = 60)),
                ),
                WorkoutVariant(
                    id = id(), name = ExerciseCatalog.name("ex_tire_jumping", lang),
                    exercises = listOf(ex("ex_tire_jumping", sets = 8, work = 24, prep = 10, rest = 10, cooldown = 60)),
                ),
                WorkoutVariant(
                    id = id(), name = ExerciseCatalog.name("ex_shadow_boxing", lang),
                    exercises = listOf(ex("ex_shadow_boxing", sets = 3, work = 45, prep = 10, rest = 15, cooldown = 60)),
                ),
                WorkoutVariant(
                    id = id(), name = ExerciseCatalog.name("ex_running", lang),
                    exercises = listOf(ex("ex_running", mode = WorkMode.TIME, work = 600, prep = 10, cooldown = 60)),
                ),
            ),
        )

        // ----- Strength (alterna Lower / Upper) -----
        val lower = WorkoutVariant(
            id = id(), name = "Lower",
            exercises = listOf(
                reps("ex_deep_squat", 20),
                reps("ex_knee_stand", 20),
                reps("ex_knee_jump", 20),
                reps("ex_cossack_squat", 20),
                reps("ex_pistol_squat", 20, note = altNote(lang)),
                reps("ex_hip_thrust", 20, note = sideNote(lang)),
                reps("ex_back_extension", 20),
                reps("ex_nordic_curl", 15),
                ex(
                    "ex_seated_calf", sets = 2, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(15, 5.0), WorkSet(15, 10.0)),
                ),
                ex(
                    "ex_donkey_calf", note = sideNote(lang), sets = 2, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(15, 15.0), WorkSet(15, 20.0)),
                ),
                ex(
                    "ex_deadlift", sets = 2, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.BARBELL, barWeight = 20.0,
                    setList = listOf(WorkSet(15, 0.0), WorkSet(15, 10.0)),
                ),
                ex(
                    "ex_zercher_squat", sets = 2, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.BARBELL, barWeight = 20.0,
                    setList = listOf(WorkSet(15, 0.0), WorkSet(15, 10.0)),
                ),
            ),
        )
        val upper = WorkoutVariant(
            id = id(), name = "Upper",
            exercises = listOf(
                ex("ex_leg_raises", sets = 2, mode = WorkMode.REPS, work = 10, prep = 5, rest = 60),
                // Pull-up rep-by-rep: 10 series de 1 rep (confirmación manual) con 15s de descanso entre reps.
                ex("ex_pull_ups", sets = 10, mode = WorkMode.REPS, work = 1, prep = 5, rest = 15),
                ex(
                    "ex_shoulder_press", sets = 2, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.DUMBBELL,
                    setList = listOf(WorkSet(15, 5.0), WorkSet(15, 7.5)),
                ),
                ex("ex_assisted_dips", sets = 1, mode = WorkMode.REPS, work = 15),
                ex("ex_pushups", sets = 2, mode = WorkMode.REPS, work = 20, rest = 60),
            ),
        )
        val strength = Workout(
            id = id(),
            name = if (lang == "es") "Fuerza" else "Strength",
            rotating = true,
            variants = listOf(lower, upper),
        )

        val now = System.currentTimeMillis()
        return Training(
            id = id(),
            name = "Master",
            workouts = listOf(warmup, base, cardio, strength),
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Training "Friki Niki": 7 workouts en secuencia (Warmup, Base, Cardio, Potencia,
     * Box, Fuerza, Extra). Pesos por serie en modo REPS; descansos uniformes por ejercicio.
     * DeadLift en BARBELL (barra 20 kg + discos 0/10/20/30). Sin rotación.
     */
    fun frikiNikiTraining(lang: String): Training {
        var seq = 1000L
        fun id(): Long = seq++

        fun ex(
            exerciseId: String,
            note: String = "",
            sets: Int = 1,
            mode: WorkMode = WorkMode.TIME,
            work: Int = 30,
            prep: Int = 0,
            rest: Int = 0,
            cooldown: Int = 0,
            weightType: WeightType = WeightType.NONE,
            barWeight: Double = 20.0,
            setList: List<WorkSet> = emptyList(),
        ): Exercise = Exercise(
            id = id(),
            exerciseId = exerciseId,
            name = ExerciseCatalog.name(exerciseId, lang),
            note = note,
            prepareSec = prep,
            sets = sets,
            workMode = mode,
            workValue = work,
            restSec = rest,
            restSkipOnLastSet = true,
            cooldownSec = cooldown,
            weightType = weightType,
            barWeight = barWeight,
            setList = setList,
        )

        fun reps(exerciseId: String, count: Int, note: String = ""): Exercise =
            ex(exerciseId, note = note, mode = WorkMode.REPS, work = count)

        val warmup = Workout(
            id = id(),
            name = if (lang == "es") "Calentamiento" else "Warmup",
            exercises = listOf(
                reps("ex_neck_lr", 10),
                reps("ex_neck_circle", 10, note = sideNote(lang)),
                reps("ex_chest_opening", 20),
                reps("ex_shoulder_rotation", 10, note = sideNote(lang)),
                reps("ex_trunk_rotation", 20),
                reps("ex_hip_rotation", 10, note = sideNote(lang)),
                reps("ex_knee_rotation", 10, note = sideNote(lang)),
                reps("ex_ankle_rotation", 10, note = sideNote(lang)),
            ),
        )

        val base = Workout(
            id = id(),
            name = "Base",
            exercises = listOf(
                reps("ex_knee_circle", 20),
                reps("ex_90_90", 20),
                ex("ex_jumping_jacks", sets = 1, mode = WorkMode.TIME, work = 30, prep = 10),
                ex("ex_burpees", sets = 5, mode = WorkMode.TIME, work = 45, prep = 10, rest = 15),
                reps("ex_knee_stand", 20),
                reps("ex_knee_jump", 20),
                reps("ex_front_side_stretch", 20),
                ex("ex_pushups", sets = 3, mode = WorkMode.REPS, work = 20, rest = 60),
            ),
        )

        val cardio = Workout(
            id = id(),
            name = "Cardio",
            exercises = listOf(
                ex("ex_running", mode = WorkMode.TIME, work = 600),
                ex("ex_rope_jumping", sets = 8, mode = WorkMode.TIME, work = 30, prep = 10, rest = 10),
            ),
        )

        val potencia = Workout(
            id = id(),
            name = if (lang == "es") "Potencia" else "Power",
            exercises = listOf(
                ex("ex_tire_jumping", sets = 8, mode = WorkMode.TIME, work = 30, prep = 10, rest = 10),
                ex(
                    "ex_bulgarian_split_squat", sets = 3, mode = WorkMode.REPS, work = 10, rest = 120,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(10, 5.0), WorkSet(10, 7.5), WorkSet(10, 10.0)),
                ),
            ),
        )

        val box = Workout(
            id = id(),
            name = "Box",
            exercises = listOf(
                ex("ex_long_knees", sets = 5, mode = WorkMode.TIME, work = 30, prep = 10, rest = 15),
                ex("ex_deep_knees", sets = 4, mode = WorkMode.TIME, work = 30, prep = 10, rest = 15),
                ex("ex_shadow_boxing", sets = 4, mode = WorkMode.TIME, work = 60, prep = 10, rest = 15),
                ex("ex_kicks", note = sideNote(lang), sets = 3, mode = WorkMode.REPS, work = 10, prep = 10),
            ),
        )

        val fuerza = Workout(
            id = id(),
            name = if (lang == "es") "Fuerza" else "Strength",
            exercises = listOf(
                ex(
                    "ex_seated_calf", note = sideNote(lang), sets = 4, mode = WorkMode.REPS, work = 10, rest = 60,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(10, 2.5), WorkSet(10, 5.0), WorkSet(10, 7.5), WorkSet(10, 7.5)),
                ),
                ex(
                    "ex_donkey_calf", note = sideNote(lang), sets = 3, mode = WorkMode.REPS, work = 15, rest = 60,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(15, 5.0), WorkSet(15, 5.0), WorkSet(15, 5.0)),
                ),
                ex(
                    "ex_hip_thrust", sets = 4, mode = WorkMode.REPS, work = 10, rest = 120,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(10, 40.0), WorkSet(10, 50.0), WorkSet(10, 60.0), WorkSet(10, 70.0)),
                ),
                ex(
                    "ex_cable_pull", sets = 4, mode = WorkMode.REPS, work = 10, rest = 120,
                    weightType = WeightType.TOTAL,
                    setList = listOf(WorkSet(10, 2.5), WorkSet(10, 2.5), WorkSet(10, 5.0), WorkSet(10, 5.0)),
                ),
                ex(
                    "ex_deadlift", sets = 4, mode = WorkMode.REPS, work = 10, rest = 120,
                    weightType = WeightType.BARBELL, barWeight = 20.0,
                    setList = listOf(WorkSet(10, 0.0), WorkSet(10, 10.0), WorkSet(10, 20.0), WorkSet(10, 30.0)),
                ),
            ),
        )

        val extra = Workout(
            id = id(),
            name = "Extra",
            exercises = listOf(
                ex("ex_walking_dog", sets = 2, mode = WorkMode.TIME, work = 60),
            ),
        )

        val now = System.currentTimeMillis()
        return Training(
            id = id(),
            name = "Friki Niki",
            workouts = listOf(warmup, base, cardio, potencia, box, fuerza, extra),
            createdAt = now,
            updatedAt = now,
        )
    }


    /**
     * Ids fijos de los dos trainings lumbares.
     *
     * Van escritos y no salidos del contador porque **el historial apunta a ellos**: la
     * sesion del 13-sep-2026 ([lumbarFirstSession]) guarda `trainingId = LUMBAR_ID`, y si
     * ese numero se moviera al reordenar el codigo, la sesion dejaria de pertenecer a
     * ningun training. 950016 es el que quedo sembrado en el dispositivo del usuario.
     */
    const val LUMBAR_ID = 950016L
    const val LUMBAR_BAD_DAY_ID = 951016L

    /**
     * Training "LUMBAR": la rutina de columna lumbar (McGill Big 3 + trabajo de cadera).
     *
     * Se siembra una vez, como Friki Niki, y a partir de ahi es del usuario: editarlo no
     * lo devuelve a este estado.
     *
     * Los tres de McGill van en piramide descendente -6 aguantes de 10 s, 30 s de respiro,
     * 4, otros 30 s, 2-, y eso cabe en UN ejercicio de 12 series gracias al descanso por
     * serie (TD-085). Antes hacian falta tres ejercicios por movimiento y el historial los
     * contaba como tres.
     */
    fun lumbarTraining(lang: String): Training {
        val b = LumbarBlocks(lang, seqStart = 950000L)
        val now = System.currentTimeMillis()
        return Training(
            id = LUMBAR_ID,
            name = "LUMBAR",
            workouts = listOf(
                b.walk(if (lang == "es") "Caminata de entrada" else "Warm Walk", sec = 720, note = "Brisk pace, arms loose"),
                b.mobility(),
                b.mcgill(),
                b.hipGlute(),
                b.walk(if (lang == "es") "Caminata de cierre" else "Cool Walk", sec = 300, note = "Easy. No toe-touch stretching after"),
            ),
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Training "LUMBAR (bad day)": la misma rutina reordenada para un dia de crisis (TD-088).
     *
     * Sale de la primera corrida, el 13-sep-2026 (ver `docs/coach-log.md`): los 12 minutos
     * de caminata de entrada no bajaron el dolor y lo que destrabo fue la movilidad, sobre
     * todo la bisagra de cadera. Ademas la hizo media hora despues de levantarse tras una
     * manana en cama, que es la peor ventana para cargar la columna.
     *
     * Por eso cambia el ORDEN y la dosis de la caminata, no el contenido: la movilidad
     * abre, la caminata se acorta a 6 minutos y pasa detras, McGill se queda igual -no se
     * sube nada en un dia malo- y el bloque de cadera va al final, donde se puede saltar
     * con el skip del player si ese dia no toca. Saltar ya queda registrado, asi que el
     * dato para la proxima decision se guarda solo.
     *
     * Es un training aparte y no una edicion del otro: el normal ya esta validado para un
     * dia normal, y tener los dos permite comparar en la bitacora que paso con cada orden.
     */
    fun lumbarBadDayTraining(lang: String): Training {
        val b = LumbarBlocks(lang, seqStart = 951000L)
        val now = System.currentTimeMillis()
        return Training(
            id = LUMBAR_BAD_DAY_ID,
            name = "LUMBAR (bad day)",
            workouts = listOf(
                b.mobility(),
                b.walk(if (lang == "es") "Caminata corta" else "Short Walk", sec = 360, note = "After the mobility, not before"),
                b.mcgill(),
                b.hipGlute(),
            ),
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * La sesion del 13-sep-2026: la primera corrida de la rutina lumbar (TD-090).
     *
     * La hizo **sin el app** -el training no existia todavia- y pidio que quedara en el
     * historial en vez de solo en la conversacion. Se reconstruye desde la propia rutina
     * porque hizo todo lo prescrito: empezo a la 1:00 pm y le tomo unos 55 minutos.
     *
     * Es el unico registro del historial que no midio el player. Lo que se sabe de esa
     * sesion -que el dolor bajo desde el gato-camello y se estabilizo en el tercer
     * ejercicio de McGill- no cabe en un [SessionLog] y vive en `docs/coach-log.md`; el
     * dia que exista TD-089 esto se podra anotar dentro del app.
     */
    fun lumbarFirstSession(lang: String): SessionLog {
        val t = lumbarTraining(lang)
        val exercises = mutableListOf<ExerciseRecord>()
        t.workouts.forEachIndexed { wi, w ->
            w.exercises.forEach { e ->
                val porTiempo = e.workMode == WorkMode.TIME
                exercises.add(
                    ExerciseRecord(
                        exerciseId = e.exerciseId,
                        name = e.name,
                        workoutName = w.name,
                        workoutIndex = wi,
                        setsCompleted = e.sets,
                        totalSets = e.sets,
                        sets = (0 until e.sets).map { i ->
                            if (porTiempo) SetRecord(durationSec = e.workSecAt(i))
                            else SetRecord(reps = e.setAt(i).reps)
                        },
                        timeBased = porTiempo,
                        totalExercisesInWorkout = w.exercises.size,
                    ),
                )
            }
        }
        return SessionLog(
            id = FIRST_SESSION_ID,
            trainingId = t.id,
            trainingName = t.name,
            startedAt = FIRST_SESSION_START,
            completedAt = FIRST_SESSION_START + FIRST_SESSION_SEC * 1000L,
            status = SessionStatus.COMPLETED,
            exercises = exercises,
            durationSec = FIRST_SESSION_SEC,
            // No la midio el player: se dedujo de la rutina. Que se note es el punto entero
            // de TD-101.
            source = SessionSource.RECONSTRUCTED,
        )
    }

    /** 13-sep-2026, 1:00 pm, hora de Peru: cuando se subio a la caminadora. */
    private const val FIRST_SESSION_START = 1789322400000L
    private const val FIRST_SESSION_SEC = 55 * 60
    /** Id fijo de la sesion reconstruida: es como se la reconoce para marcarla (TD-101). */
    const val FIRST_SESSION_ID = 950017L

    /**
     * El training con el bloque de cadera y gluteo ya cargado (TD-098).
     *
     * Hace falta porque los dos lumbares ya estan sembrados en el dispositivo y la siembra
     * no vuelve a correr: cambiar [LumbarBlocks.hipGlute] solo llegaria a una instalacion
     * nueva.
     *
     * Dos cuidados. Conserva el **id de cada instancia**, para que una corrida en marcha no
     * pierda el sitio al rehacerse la cola de pasos. Y solo cambia los ejercicios que
     * **siguen sin carga**: si el usuario ya les puso peso por su cuenta, ese numero es
     * suyo y pisarlo seria quitarle algo.
     *
     * Los ids que genera el bloque aqui se descartan; solo se le piden los ejercicios.
     */
    fun withHipGluteLoaded(t: Training, lang: String): Training {
        val cargados = LumbarBlocks(lang, seqStart = 0L).hipGlute().exercises.associateBy { it.exerciseId }
        return t.copy(
            workouts = t.workouts.map { w ->
                w.copy(
                    exercises = w.exercises.map { e ->
                        val nuevo = cargados[e.exerciseId]
                        if (nuevo != null && e.weightType == WeightType.NONE) nuevo.copy(id = e.id) else e
                    },
                )
            },
        )
    }

    /**
     * Los bloques de la rutina lumbar, compartidos por los dos trainings.
     *
     * Existe porque las dos versiones son el **mismo contenido en otro orden** -eso es lo
     * que cambia en un dia de crisis, no los ejercicios-, y duplicarlos dejaria dos sitios
     * donde corregir la proxima vez que un dato pida ajustar algo.
     *
     * [seqStart] separa los ids de un training y del otro.
     */
    private class LumbarBlocks(private val lang: String, seqStart: Long) {

        private var seq = seqStart
        private fun id(): Long = seq++

        private fun ex(
            exerciseId: String,
            note: String = "",
            sets: Int = 1,
            mode: WorkMode = WorkMode.TIME,
            work: Int = 30,
            prep: Int = 0,
            rest: Int = 0,
            weightType: WeightType = WeightType.NONE,
            setList: List<WorkSet> = emptyList(),
        ): Exercise = Exercise(
            id = id(),
            exerciseId = exerciseId,
            name = ExerciseCatalog.name(exerciseId, lang),
            note = note,
            prepareSec = prep,
            sets = sets,
            workMode = mode,
            workValue = work,
            restSec = rest,
            restSkipOnLastSet = true,
            weightType = weightType,
            setList = setList,
        )

        /**
         * Un ejercicio por repeticiones con carga que sube entre series (TD-098).
         *
         * Los pesos van por serie porque es como el usuario ya entrena -el 14-sep-2026 hizo
         * el suitcase carry con 7.5, 10 y 12.5- y porque asi el historial guarda la carga
         * real, que es lo que luego permite subirla con criterio en vez de a ojo.
         *
         * Con [WeightType.BARBELL] los numeros son DISCOS: el total es la barra mas eso.
         */
        private fun loaded(
            exerciseId: String,
            count: Int,
            note: String,
            weights: List<Double>,
            weightType: WeightType = WeightType.TOTAL,
        ): Exercise = ex(
            exerciseId,
            note = note,
            sets = weights.size,
            mode = WorkMode.REPS,
            work = count,
            prep = 10,
            rest = 60,
            weightType = weightType,
            setList = weights.map { WorkSet(reps = count, weight = it) },
        )

        private fun reps(exerciseId: String, count: Int, note: String = "", sets: Int = 1, rest: Int = 0, prep: Int = 0): Exercise =
            ex(exerciseId, note = note, sets = sets, mode = WorkMode.REPS, work = count, rest = rest, prep = prep)

        // Un movimiento de McGill entero: 12 aguantes de 10 s con 3 s entre ellos, y 30 s
        // al cerrar el bloque de 6 (serie 6) y el de 4 (serie 10). La ultima no descansa.
        //
        // Los 10 s no se tocan al progresar: se suben los aguantes por bloque (8/6/4,
        // 10/8/6). Pasado ese tiempo la calidad del bracing cae y el ejercicio cobra mas
        // de lo que da.
        private fun pyramid(exerciseId: String, note: String): Exercise = ex(
            exerciseId,
            note = note,
            sets = 12,
            work = 10,
            prep = 20,
            rest = 3,
            setList = (0 until 12).map { i ->
                WorkSet(reps = 10, restSec = if (i == 5 || i == 9) 30 else null)
            },
        )

        fun walk(name: String, sec: Int, note: String): Workout = Workout(
            id = id(),
            name = name,
            exercises = listOf(ex("ex_walk", note = note, work = sec)),
        )

        fun mobility(): Workout = Workout(
            id = id(),
            name = if (lang == "es") "Movilidad" else "Mobility",
            exercises = listOf(
                reps("ex_cat_cow", 8, note = "6-8 slow cycles", prep = 10),
                reps("ex_hip_hinge", 10, note = "Stick on nape, mid-back and sacrum", prep = 10),
            ),
        )

        fun mcgill(): Workout = Workout(
            id = id(),
            name = "McGill Big 3",
            exercises = listOf(
                pyramid("ex_curl_up", "Alternate the bent leg between blocks"),
                pyramid("ex_side_plank_l", "Elbow under the shoulder, knees at 90"),
                pyramid("ex_side_plank_r", "Elbow under the shoulder, knees at 90"),
                pyramid("ex_bird_dog", "Alternate sides between holds"),
            ),
        )

        /**
         * El bloque de cadera y gluteo, con carga desde TD-098.
         *
         * A peso corporal se le quedaba corto y sus propios datos decian por que: en el
         * training MASTER empuja hip thrust con 40, 50, 60 y 70 kg, y aqui estaba haciendo
         * el puente a peso corporal. Empezar en 40 es conservador a proposito.
         *
         * Sigue siendo puente de SUELO y no hip thrust: menos recorrido de extension
         * lumbar, que es lo que interesa en una rutina de columna. Y la sentadilla va
         * goblet y no con barra porque el peso delante ayuda a mantener el pecho arriba y
         * carga menos la espalda.
         */
        fun hipGlute(): Workout = Workout(
            id = id(),
            name = if (lang == "es") "Cadera y gluteo" else "Hip & Glute",
            exercises = listOf(
                // Discos: con la barra de 20 son 20, 30 y 40 kg.
                loaded("ex_glute_bridge", 12, "Bar on the hips, push through the heels", listOf(0.0, 10.0, 20.0), WeightType.BARBELL),
                loaded("ex_suitcase_carry", 2, "One trip of 30-40 m per side", listOf(7.5, 10.0, 12.5)),
                loaded("ex_box_squat", 8, "Goblet at the chest, chest up", listOf(7.5, 10.0, 12.5)),
            ),
        )
    }

    /**
     * Instrucciones de los ejercicios de [lumbarTraining], en ingles como el resto del app.
     *
     * Las dos reglas de la rutina -no entrenar en la primera hora tras levantarse, y que
     * hacer si el dolor irradia a la pierna- viven en la caminata de entrada, que es el
     * primer ejercicio del training y por tanto lo primero que se abre.
     *
     * Quien las siembre debe respetar lo que el usuario ya tenga escrito: eso es suyo.
     */
    fun lumbarInstructions(): Map<String, ExerciseMedia> = mapOf(
        "ex_walk" to ExerciseMedia(
            listOf(
                "Brisk pace, arms loose. It hydrates the disc and warms the hip up.",
                "If the pain drops while walking, good sign to carry on with the rest.",
                "First hour after waking up: what is risky then is loaded lumbar FLEXION, not moving. Mobility, walking and the McGill three are fine - the curl-up keeps the lower back from flattening on purpose. Waiting 60-90 minutes is still better when you can.",
                "Training straight out of bed? Skip the suitcase carry or drop the weight. It is the only exercise with an external load, and compression is what costs most at that hour.",
                "If anything radiates down the leg, drop it for the day and write it down for the physio. If the pain centralises, from the leg back to the spine, you are on track.",
            ),
        ),
        "ex_cat_cow" to ExerciseMedia(
            listOf(
                "6 to 8 slow cycles through a comfortable range.",
                "It is lubrication, not a stretch: do not force the end range.",
            ),
        ),
        "ex_hip_hinge" to ExerciseMedia(
            listOf(
                "Stick or broom against the nape, mid-back and sacrum.",
                "Hinge at the hip keeping the three contact points.",
                "This is the pattern that protects you the rest of the day.",
            ),
        ),
        "ex_curl_up" to ExerciseMedia(
            listOf(
                "Hands under the lower back, palms down. One leg bent, the other straight.",
                "Lift head and shoulders a few centimetres, as one rigid block.",
                "The chin does not tuck into the chest and the lower back does not flatten.",
                "Breathe normally through the hold: holding your breath means you are bracing too hard.",
                "Alternate the bent leg between blocks.",
            ),
        ),
        "ex_side_plank_l" to sidePlankSteps(),
        "ex_side_plank_r" to sidePlankSteps(),
        "ex_bird_dog" to ExerciseMedia(
            listOf(
                "Opposite arm and leg, up to shoulder and hip height, no higher.",
                "The back stays still: if the hip tilts, you went lower than your control.",
                "Alternate sides between holds.",
            ),
        ),
        "ex_glute_bridge" to ExerciseMedia(
            listOf(
                "Push through the heels, squeeze the glute at the top.",
                "If you feel the lower back working, you are not using the glute.",
            ),
        ),
        "ex_suitcase_carry" to ExerciseMedia(
            listOf(
                "Dumbbell or anything with a handle, on one side only.",
                "30-40 m per side, walking tall without leaning towards the weight.",
                "One of the best there is for the core with minimal load on the spine.",
            ),
        ),
        "ex_box_squat" to ExerciseMedia(
            listOf(
                "To a box or a chair, no weight.",
                "Controlled on the way down until you touch, then stand up. Chest up.",
            ),
        ),
    )

    /**
     * Las indicaciones de la caminata **tal y como se sembraron el 13-sep-2026**, antes de
     * TD-091.
     *
     * Existen para poder distinguir "esto lo escribio el app" de "esto lo escribio el
     * usuario": la migracion solo reescribe el texto si sigue siendo palabra por palabra
     * este, y si no, lo deja en paz.
     */
    val WALK_INSTRUCTIONS_V1 = ExerciseMedia(
        listOf(
            "Brisk pace, arms loose. It hydrates the disc and warms the hip up.",
            "If the pain drops while walking, good sign to carry on with the rest.",
            "Do not train in the first hour after waking up: the disc is more hydrated and lumbar flexion is riskier there. Let 60-90 minutes pass.",
            "If anything radiates down the leg, drop it for the day and write it down for the physio. If the pain centralises, from the leg back to the spine, you are on track.",
        ),
    )

    private fun sidePlankSteps() = ExerciseMedia(
        listOf(
            "Elbow under the shoulder, knees bent at 90 and resting on the floor.",
            "Straight line shoulder, hip and knee.",
            "Do not let the hip drop or rotate the chest towards the floor.",
        ),
    )

    private fun sideNote(lang: String) = if (lang == "es") "cada lado" else "each side"
    private fun altNote(lang: String) = if (lang == "es") "alternado" else "alternating"
}
