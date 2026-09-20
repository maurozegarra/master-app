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
     * Revision de la rutina lumbar. **Subir este numero es lo unico que hace falta para que
     * un cambio de la rutina llegue al dispositivo** (TD-103).
     *
     * Antes, cada ajuste pedia su propia migracion con su propia marca: en dos dias el
     * store acumulo ocho. Cargar el bloque de cadera (TD-098) costo una funcion, una marca,
     * una migracion y sus tests, cuando lo unico que aportaba a la rutina eran tres lineas.
     *
     * El trato que lo hace posible: **los dos lumbares son del coach**. El usuario no los
     * edita en el app; dice que quiere cambiar y el cambio entra por aqui. Si los editara,
     * la siguiente revision los pisaria.
     *
     * Historial: sin riesgo. Los ids estan fijos, asi que reemplazar el contenido no
     * desconecta ninguna sesion ya registrada.
     */
    const val LUMBAR_REVISION = 10

    /**
     * De quien es la rutina lumbar.
     *
     * Sembrar desde el codigo llega a CUALQUIER instalacion, y al publicar la v1.0.248 eso
     * significaba que el telefono de NIKO iba a recibir dos trainings que no son suyos. Una
     * rutina de rehabilitacion de la espalda de otro no es ruido neutro: es algo que alguien
     * podria ponerse a hacer.
     *
     * Mientras la siembra siga siendo por codigo, el perfil es lo unico que distingue un
     * telefono de otro. La solucion de fondo es TD-066: repartir por asignacion, donde cada
     * quien recibe lo suyo y esto sobra.
     */
    const val LUMBAR_PROFILE = "mauro"

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
            tracksPain = true,
            workouts = listOf(
                b.walk(if (lang == "es") "Caminata de entrada" else "Warm Walk", sec = 720, note = "Arms loose", kmh = 6.0),
                b.mobility(),
                b.mcgill(),
                b.hipGlute(),
                b.walk(if (lang == "es") "Caminata de cierre" else "Cool Walk", sec = 300, note = "No toe-touch stretching after", kmh = 4.0),
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
            tracksPain = true,
            workouts = listOf(
                b.mobility(),
                // La velocidad va en la nota y no solo en las instrucciones porque es lo que
                // se lee en el player. "Paso vivo" costo tres sesiones: a 3 km/h no hacia
                // nada, a 5 le solto las caderas. Un adjetivo no dosifica.
                b.walk(if (lang == "es") "Caminata corta" else "Short Walk", sec = 360, note = "After the mobility, not before", kmh = 6.0),
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
     * La sesion del 15-sep-2026, que quedo registrada con pesos que no fueron (TD-105).
     *
     * El puente se guardo como 20/30/40 kg porque el ejercicio tenia la barra en 20, y la
     * suya pesa 6: lo que de verdad movio fueron 6, 16 y 21. Se corrige y la sesion queda
     * marcada como [SessionSource.EDITED], que es justo para lo que existe ese campo.
     */
    const val SESSION_15_SEP_ID = 1789478120939L
    val SESSION_15_SEP_WRONG = listOf(20.0, 30.0, 40.0)
    val SESSION_15_SEP_RIGHT = listOf(6.0, 16.0, 21.0)

    /**
     * La sesion del 17-sep-2026, y lo que marco en la tarjeta del peso ese dia (TD-120).
     *
     * Lo marco en el player y el app lo boto -ese era el bug de TD-117-; lo que queda es lo
     * que conto por chat esa misma noche, serie por serie: el puente 6 "muy ligero", 16
     * "ligero" y 31 sin marcar; carry y sentadilla "ligero" en las tres. "Muy ligero" entra
     * como ligero porque la tarjeta no tiene un "muy". Null es la serie que no marco.
     */
    const val SESSION_17_SEP_ID = 1789643650309L
    val SESSION_17_SEP_FEEDBACK: Map<String, List<Double?>> = mapOf(
        "ex_glute_bridge" to listOf(2.5, 2.5, null),
        "ex_suitcase_carry" to listOf(2.5, 2.5, 2.5),
        "ex_box_squat" to listOf(2.5, 2.5, 2.5),
    )

    /**
     * [session] con el feedback del 17-sep escrito, marcada [SessionSource.EDITED]; o null
     * si no hay nada que escribir.
     *
     * Solo escribe en un ejercicio que no tenga NADA marcado y cuyo numero de series
     * coincida con lo contado: si el usuario lo hubiera marcado de otra forma, o la sesion
     * no fuera la que se cree, lo suyo gana y no se toca.
     */
    fun withSep17Feedback(session: SessionLog): SessionLog? {
        if (session.id != SESSION_17_SEP_ID) return null
        val ejercicios = session.exercises.map { er ->
            val contado = SESSION_17_SEP_FEEDBACK[er.exerciseId]
            if (contado == null || contado.size != er.sets.size || er.sets.any { it.feedbackDeltaKg != null }) {
                er
            } else {
                val sets = er.sets.mapIndexed { i, set -> set.copy(feedbackDeltaKg = contado[i]) }
                er.copy(sets = sets, feedbackDeltaKg = sets.lastOrNull { it.feedbackDeltaKg != null }?.feedbackDeltaKg)
            }
        }
        if (ejercicios == session.exercises) return null
        return session.copy(exercises = ejercicios, source = SessionSource.EDITED)
    }

    /**
     * La lista de trainings con los dos lumbares puestos al dia: reemplaza el que ya este y
     * agrega el que falte, en su sitio y sin tocar el resto.
     *
     * De lo que ya hay en el dispositivo conserva **lo que es suyo y no de la definicion**:
     * [Training.uid], que es la identidad estable con la que viaja un training entre
     * dispositivos y con la que se publica, y [Training.createdAt], que es cuando aparecio
     * de verdad. Lo demas -workouts, ejercicios, cargas- lo manda el codigo.
     */
    /**
     * Revision de las rutinas de NIKO (TD-127). Mismo mecanismo que [LUMBAR_REVISION]:
     * cambiar la rutina es editar la funcion y subir este numero.
     */
    const val NIKO_REVISION = 4

    /** Id fijo del dia de gluteo pesado. Ver [LUMBAR_ID] para por que va escrito. */
    const val NIKO_GLUTE_ID = 960001L
    const val NIKO_MUAY_THAI_ID = 960002L

    /**
     * Los dias de NIKO que ya existen, en orden (ver docs/niko.md).
     *
     * La semana base tiene seis, pero se siembran **de a uno, el dia anterior**: el usuario
     * no quiere pasarle seis rutinas de golpe, sino solo la siguiente, y cada una se ajusta
     * con lo que paso en la anterior. Agregar el dia que toca es agregarlo aqui y subir
     * [NIKO_REVISION].
     */
    fun nikoTrainings(lang: String): List<Training> = listOf(
        nikoGluteHeavy(lang),
        nikoMuayThai(lang),
    )

    /**
     * NIKO - Gluteo pesado: el primer dia de su semana nueva (ver docs/niko.md).
     *
     * QUE PROBLEMA RESUELVE. Su queja es que las piernas las siente en el cuadriceps y no en
     * el gluteo, y su rutina lo explica: el volumen de patron de RODILLA -bulgara erguida,
     * knee stand, knee jump, burpees, sentadillas- aplasta al de CADERA, que son dos
     * ejercicios contra muchos. Este dia es bisagra de cadera casi entero.
     *
     * LAS TRES COSAS QUE CAMBIAN LA SENSACION, y van en las notas porque sin ellas el
     * ejercicio es el mismo pero el musculo es otro:
     *  - El puente de activacion ABRE la sesion: si el gluteo no se enciende primero, el
     *    cuadriceps toma el trabajo por defecto.
     *  - El hip thrust lleva PAUSA de 2 s arriba. Sin pausa se pasa rapido justo por donde
     *    el gluteo trabaja.
     *  - La bulgara va con el TORSO INCLINADO y paso largo. Erguida y con paso corto es un
     *    ejercicio de cuadriceps.
     *
     * Las cargas salen de lo que ya movia -hip thrust hasta 70 kg pesando 49.7- menos lo que
     * cuesta la pausa. El peso muerto rumano es nuevo para ella y entra conservador: la
     * bisagra se aprende antes de cargarse.
     */
    fun nikoGluteHeavy(lang: String): Training {
        val b = NikoBlocks(lang, seqStart = 960100L)

        val now = System.currentTimeMillis()
        return Training(
            id = NIKO_GLUTE_ID,
            // Numerado y no con nombre de dia: si un dia no puede, al siguiente sigue con el
            // numero que le toca, en vez de tener que saltarse "el lunes".
            name = "NIKO 1 · Glúteo pesado",
            workouts = listOf(
                b.warmup(),
                Workout(
                    id = b.id(),
                    name = "Despertar glúteos",
                    exercises = listOf(
                        b.ex("ex_glute_bridge", "Aprieta 2 s arriba. En el glúteo, no en los muslos", 15, sets = 2, rest = 30),
                        b.ex("ex_hip_abduction", "De lado, tobillera de 1 kg. Lento", 20, sets = 2, rest = 30),
                    ),
                ),
                Workout(
                    id = b.id(),
                    name = "Bisagra de cadera",
                    exercises = listOf(
                        // Revision 2 (19-sep-2026): su barra es la EZ de 6 kg, y la revision 1
                        // no lo pregunto. El hip thrust iba en kilos TOTALES -45/55/65/70- y 70
                        // sobre una barra de 6 no se puede armar: son 64 de disco, 32 por lado,
                        // y con discos de 1.25 hacia arriba no hay forma. El usuario hizo la
                        // cuenta entre series y le salio 71. El rumano asumia la barra de 20 por
                        // defecto, el mismo error que el 15-sep le costo al otro atleta.
                        //
                        // Ahora son discos sobre la barra de 6 y todos se arman:
                        // hip thrust 46/56/66/71 = 20, 25, 30 y 32.5 por lado.
                        b.ex(
                            "ex_hip_thrust", "PAUSA de 2 s arriba, en cada repetición", 8, rest = 120,
                            weightType = WeightType.BARBELL, barWeight = 6.0, weights = listOf(40.0, 50.0, 60.0, 65.0),
                        ),
                        // Rumano 26/31/36/36 = 10, 12.5 y 15 por lado. Conservador: la bisagra
                        // es nueva para ella y se aprende antes de cargarse.
                        b.ex(
                            "ex_romanian_deadlift", "Cadera atrás, rodillas casi rectas. Barra pegada a las piernas", 10, rest = 120,
                            weightType = WeightType.BARBELL, barWeight = 6.0, weights = listOf(20.0, 25.0, 30.0, 30.0),
                        ),
                    ),
                ),
                Workout(
                    id = b.id(),
                    name = "Una pierna",
                    exercises = listOf(
                        b.ex(
                            "ex_bulgarian_split_squat", "PECHO ADELANTE y paso largo. Cada pierna", 10, rest = 90,
                            weightType = WeightType.DUMBBELL, weights = listOf(5.0, 7.5, 10.0),
                        ),
                        // A 45 grados: la banca es regulable, y el usuario pidio que ella lo
                        // tenga presente al armarla.
                        b.ex("ex_back_extension", "Banca a 45°. Aprieta los glúteos arriba, sin arquear la espalda baja", 12, sets = 3, rest = 60),
                    ),
                ),
                Workout(
                    id = b.id(),
                    name = "Cuello",
                    exercises = listOf(
                        b.ex("ex_neck_iso", "Mano contra la cabeza, empuja y aguanta. Adelante, atrás, derecha, izquierda", 20, sets = 4, rest = 15, mode = WorkMode.TIME),
                    ),
                ),
            ),
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * NIKO 2 · Muay Thai: rounds de shadow y saco, pies con la llanta y core rotacional.
     *
     * Rounds de 3 minutos con 1 de descanso, que es el formato del deporte. El saco lleva
     * una progresion por round en la nota -de las manos a las rodillas-, porque entrena sola
     * y sin profesor: sin un orden, cada round termina siendo el mismo.
     *
     * El core es ROTACIONAL porque la patada sale de ahi, y era el hueco de su rutina
     * anterior. El pallof press, que seria lo ideal, espera a las bandas.
     */
    fun nikoMuayThai(lang: String): Training {
        val b = NikoBlocks(lang, seqStart = 960200L)
        val now = System.currentTimeMillis()
        return Training(
            id = NIKO_MUAY_THAI_ID,
            name = "NIKO 2 · Muay Thai",
            workouts = listOf(
                b.warmup(),
                Workout(
                    id = b.id(),
                    name = "Rounds",
                    exercises = listOf(
                        b.ex("ex_shadow_boxing", "Guardia arriba siempre. Manos y pies", 180, sets = 3, rest = 60, mode = WorkMode.TIME),
                        b.ex("ex_heavy_bag", "R1 jab-recto · R2 +gancho · R3 +teep · R4 rodillas · R5 libre", 180, sets = 5, rest = 60, mode = WorkMode.TIME),
                    ),
                ),
                Workout(
                    id = b.id(),
                    name = "Pies",
                    exercises = listOf(
                        b.ex("ex_tire_jumping", "Ligera, sobre la punta de los pies", 30, sets = 6, rest = 15, mode = WorkMode.TIME),
                    ),
                ),
                Workout(
                    id = b.id(),
                    name = "Core rotacional",
                    exercises = listOf(
                        b.ex(
                            "ex_russian_twist", "10 por lado. Gira el pecho, no solo los brazos", 20, rest = 45,
                            weightType = WeightType.DUMBBELL, dumbbellCount = 1, weights = listOf(5.0, 5.0, 5.0),
                        ),
                        b.ex("ex_side_plank_l", "Codo bajo el hombro, cuerpo recto", 30, sets = 3, rest = 20, mode = WorkMode.TIME),
                        b.ex("ex_side_plank_r", "Codo bajo el hombro, cuerpo recto", 30, sets = 3, rest = 20, mode = WorkMode.TIME),
                    ),
                ),
                Workout(
                    id = b.id(),
                    name = "Cuello",
                    exercises = listOf(
                        b.ex("ex_neck_iso", "Mano contra la cabeza, empuja y aguanta. Adelante, atrás, derecha, izquierda", 20, sets = 4, rest = 15, mode = WorkMode.TIME),
                    ),
                ),
            ),
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Lo que comparten los dias de NIKO: el constructor de ejercicios y el calentamiento.
     *
     * [seqStart] separa los ids de un dia y del otro, igual que en [LumbarBlocks].
     */
    private class NikoBlocks(private val lang: String, seqStart: Long) {
        private var seq = seqStart
        fun id(): Long = seq++

        fun ex(
            exerciseId: String,
            note: String,
            count: Int,
            sets: Int = 1,
            rest: Int = 0,
            prep: Int = 10,
            mode: WorkMode = WorkMode.REPS,
            weightType: WeightType = WeightType.NONE,
            barWeight: Double = 20.0,
            dumbbellCount: Int = 2,
            weights: List<Double> = emptyList(),
        ): Exercise = Exercise(
            id = id(),
            exerciseId = exerciseId,
            name = ExerciseCatalog.name(exerciseId, lang),
            note = note,
            prepareSec = prep,
            sets = if (weights.isEmpty()) sets else weights.size,
            workMode = mode,
            workValue = count,
            restSec = rest,
            restSkipOnLastSet = true,
            weightType = weightType,
            barWeight = barWeight,
            dumbbellCount = dumbbellCount,
            setList = weights.map { WorkSet(reps = count, weight = it) },
        )

        /** Cinco minutos, el mismo todos los dias: la cuerda sube la temperatura y el resto abre cadera y hombros. */
        fun warmup(): Workout = Workout(
            id = id(),
            name = "Calentamiento",
            exercises = listOf(
                ex("ex_rope_jumping", "Suave, para entrar en calor", 120, prep = 10, mode = WorkMode.TIME),
                ex("ex_hip_rotation", "Cada lado", 10, prep = 5),
                ex("ex_90_90", "Lento, sin forzar", 10, prep = 5),
                ex("ex_shoulder_rotation", "Cada lado", 10, prep = 5),
            ),
        )
    }

    /**
     * Revision de las instrucciones del catalogo. Subirla vuelve a sembrar las que falten.
     */
    const val CATALOG_INSTRUCTIONS_REVISION = 6

    /**
     * Como se hace cada ejercicio del catalogo, para TODOS los telefonos (TD-131).
     *
     * Las instrucciones viven en cada telefono y asignar un training no las manda: las del
     * primer dia de NIKO, escritas en el telefono del coach, no le habrian llegado nunca. Y
     * "como se hace un hip thrust" no es de nadie: es del catalogo, asi que viaja con el
     * app. Se siembran solo donde el ejercicio no tiene instrucciones: lo que alguien haya
     * escrito a mano no se pisa.
     *
     * Nacen de una pregunta que no debio hacer falta: *"como se ejecuta el hip abduction y
     * con el neck isometric estoy aun mas perdido"*. Si el atleta tiene que preguntar,
     * faltaban.
     */
    fun catalogInstructions(): Map<String, ExerciseMedia> = mapOf(
        "ex_hip_thrust" to ExerciseMedia(
            listOf(
                "Apoya la parte alta de la espalda en el borde de la banca, justo debajo de los omóplatos. La barra sobre la cadera, con una almohadilla.",
                "Pies planos, al ancho de los hombros. Arriba, las canillas deben quedar verticales.",
                "Mentón metido y la mirada al frente, no al techo.",
                "Empuja con los talones y sube la cadera hasta que rodillas, cadera y hombros formen una línea recta.",
                "PAUSA de 2 segundos arriba, apretando los glúteos. No arquees la espalda baja para subir más.",
                "Baja controlando.",
            ),
        ),
        "ex_romanian_deadlift" to ExerciseMedia(
            listOf(
                "De pie, con la barra a la altura de la cadera, las manos justo por fuera de los muslos y los pies al ancho de la cadera.",
                "Rodillas un poco dobladas, y así se quedan toda la repetición.",
                "Lleva la cadera hacia ATRÁS, como si cerraras una puerta con el trasero. La barra baja pegada a los muslos.",
                "Espalda recta y pecho arriba. Baja hasta sentir que la parte de atrás de los muslos se estira fuerte, normalmente un poco debajo de las rodillas.",
                "Aprieta los glúteos para subir. Arriba no te eches hacia atrás.",
            ),
        ),
        "ex_hip_abduction" to ExerciseMedia(
            listOf(
                "Échate de lado, con la cabeza apoyada en el brazo de abajo.",
                "La pierna de abajo doblada, más o menos a 90 grados, para no rodar. La de arriba estirada, en línea con el cuerpo, con la tobillera.",
                "Lo que decide todo: la pierna de arriba un poco HACIA ATRÁS de la línea del cuerpo, y la punta del pie mirando al frente o un poco al suelo. Nunca al techo: así trabaja el flexor de la cadera, no el glúteo.",
                "Sube la pierna de 30 a 40 cm, no más. Un segundo arriba y baja lento, en 2 o 3 segundos, sin apoyarla.",
                "La cadera de arriba no se va hacia atrás. Imagina que tienes la espalda pegada a una pared.",
                "Tiene que arder el costado de la nalga, arriba y atrás del hueso de la cadera. ¿Arde el frente del muslo? Punta del pie más hacia el suelo y la pierna más atrás.",
            ),
        ),
        "ex_bulgarian_split_squat" to ExerciseMedia(
            listOf(
                "El pie de atrás sobre la banca, con los cordones hacia abajo. El pie de adelante bien lejos: un paso LARGO.",
                "Inclina el pecho hacia adelante, entre 30 y 45 grados, con la espalda recta.",
                "Baja hasta que el muslo de adelante quede más o menos paralelo al piso.",
                "Empuja con el talón de adelante para subir. Tiene que arder el glúteo de la pierna de adelante.",
                "¿Solo lo sientes en el frente del muslo? Paso más largo y más inclinación. Con el cuerpo derecho y el paso corto, es un ejercicio de cuádriceps.",
            ),
        ),
        "ex_back_extension" to ExerciseMedia(
            listOf(
                "La cadera sobre el cojín, justo debajo del pliegue de la cadera, para que pueda doblarse libre.",
                "Pies un poco hacia afuera. Mentón metido.",
                "Baja doblando la cadera.",
                "Sube apretando los glúteos hasta que el cuerpo quede en línea recta. Ahí para: no te arquees más allá.",
            ),
        ),
        "ex_neck_iso" to ExerciseMedia(
            listOf(
                "Isométrico: empujar sin moverse. La cabeza se queda quieta y la mano no la deja ir.",
                "De pie o en una silla, con la espalda recta, la mirada al frente y el mentón un poco metido.",
                "Cada serie es una dirección, en este orden. Serie 1, ADELANTE: la palma en la frente, empuja la cabeza hacia adelante contra ella.",
                "Serie 2, ATRÁS: las manos entrelazadas en la nuca, empuja la cabeza hacia atrás contra ellas.",
                "Serie 3, DERECHA: la palma derecha en la sien derecha, empuja como llevando la oreja al hombro.",
                "Serie 4, IZQUIERDA: igual, del otro lado.",
                "La mitad de tu fuerza, no toda. Respira normal todo el tiempo.",
                "Si algo pica o baja por el brazo, para ahí.",
            ),
        ),
        "ex_step_up" to ExerciseMedia(
            listOf(
                "Un cajón a la altura de la rodilla o un poco más alto. TODO el pie sobre el cajón.",
                "Inclina un poco el pecho hacia adelante y empuja con el talón del pie de arriba.",
                "Sube hasta quedar derecho, sin impulsarte con el pie de abajo.",
                "Baja lento, controlando con la pierna de arriba.",
            ),
        ),
        "ex_heavy_bag" to ExerciseMedia(
            listOf(
                "Guardia arriba antes, durante y después de cada golpe: la mano que no golpea protege la cara.",
                "Distancia: a un brazo del saco. Si lo empujas en vez de golpearlo, estás muy cerca.",
                "Golpea y vuelve a la guardia. La mano regresa por el mismo camino por el que salió.",
                "Muévete entre combinaciones: un paso, cambia el ángulo, vuelve a entrar.",
                "Cada round tiene su tarea, en la nota. Respira al golpear, soltando el aire.",
            ),
        ),
        "ex_russian_twist" to ExerciseMedia(
            listOf(
                "En el piso, con las rodillas dobladas y los talones apoyados. Inclina el torso atrás hasta sentir el abdomen trabajando.",
                "La mancuerna al centro del pecho, con las dos manos.",
                "Gira el PECHO hacia un lado y después al otro. Los brazos acompañan; no son los que giran.",
                "Espalda recta todo el tiempo: si se encorva, inclínate menos hacia atrás.",
                "Para hacerlo más difícil, levanta los pies del piso.",
            ),
        ),
        "ex_side_plank_l" to ExerciseMedia(
            listOf(
                "De lado, sobre el antebrazo izquierdo, con el codo justo debajo del hombro.",
                "Sube la cadera hasta que el cuerpo quede en línea recta de la cabeza a los pies.",
                "Aguanta sin dejar caer la cadera. Si no puedes con las piernas estiradas, apoya las rodillas dobladas.",
            ),
        ),
        "ex_side_plank_r" to ExerciseMedia(
            listOf(
                "De lado, sobre el antebrazo derecho, con el codo justo debajo del hombro.",
                "Sube la cadera hasta que el cuerpo quede en línea recta de la cabeza a los pies.",
                "Aguanta sin dejar caer la cadera. Si no puedes con las piernas estiradas, apoya las rodillas dobladas.",
            ),
        ),
        "ex_glute_bridge" to ExerciseMedia(
            listOf(
                "Échate boca arriba, con las rodillas dobladas y los pies planos al ancho de la cadera.",
                "Empuja con los talones y aprieta el glúteo arriba.",
                "Si sientes que trabaja la espalda baja, no estás usando el glúteo.",
            ),
        ),
        "ex_rope_jumping" to ExerciseMedia(
            listOf(
                "Mide la cuerda: pisa el centro con un pie y los mangos deben llegarte a las axilas.",
                "Codos pegados al cuerpo. La cuerda gira con las MUÑECAS, no con los brazos.",
                "Saltos bajos, de dos dedos del piso, cayendo en la punta de los pies con la rodilla suelta.",
                "Mirada al frente, no a los pies.",
                "Si te trabas, sigue: lo que cuenta son los dos minutos en movimiento, no una serie perfecta.",
            ),
        ),
        "ex_hip_rotation" to ExerciseMedia(
            listOf(
                "De pie, con una mano en la pared si te hace falta equilibrio.",
                "Sube una rodilla hasta la altura de la cadera.",
                "Llévala en círculo hacia afuera, lo más amplio que puedas, y bájala. Ese es un círculo.",
                "El tronco no acompaña: se mueve la pierna, no la espalda.",
                "Diez con una pierna y diez con la otra. Es movilidad, no fuerza: lento y sin rebotes.",
            ),
        ),
        "ex_90_90" to ExerciseMedia(
            listOf(
                "En el piso: una pierna delante doblada a 90° y la otra al costado, también a 90°.",
                "Las dos rodillas y los dos tobillos tocando el piso, y la espalda recta.",
                "Gira las dos piernas al otro lado, dejando caer las rodillas, sin ayudarte con las manos.",
                "Un lado y el otro es una repetición.",
                "Si las rodillas no llegan al piso, no fuerces: llega hasta donde llegues y respira.",
            ),
        ),
        "ex_shoulder_rotation" to ExerciseMedia(
            listOf(
                "De pie, con los brazos sueltos a los costados.",
                "Sube un brazo por delante, estíralo arriba junto a la oreja y bájalo por atrás: un círculo grande y lento.",
                "El hombro se queda abajo, no sube hacia la oreja.",
                "Diez círculos con un brazo y diez con el otro.",
                "Si algo pincha, haz el círculo más pequeño hasta donde no moleste.",
            ),
        ),
        "ex_shadow_boxing" to ExerciseMedia(
            listOf(
                "Guardia: manos a la altura de los pómulos, codos pegados a las costillas y el mentón metido.",
                "Pies al ancho de los hombros, uno delante y otro detrás, con el talón de atrás levantado.",
                "No te quedes quieto: pasos cortos, sin cruzar nunca los pies.",
                "El golpe sale del pie y la cadera y termina en la mano. Si solo mueves el brazo, no es un golpe.",
                "La mano que no golpea NO se cae: vuelve a la cara.",
                "Manos y pies: mete también teeps y rodillas, y vuelve siempre a la guardia.",
            ),
        ),
        "ex_tire_jumping" to ExerciseMedia(
            listOf(
                "De frente a la llanta, a un paso de distancia.",
                "Salta con los dos pies a la vez y cae ENCIMA de la llanta, en la punta de los pies y con las rodillas dobladas.",
                "Baja saltando hacia atrás, igual de suave, y encadena el siguiente.",
                "Ligera y rápida: el piso quema. No aterrices con el talón ni con la pierna rígida.",
                "Si lo sientes en las rodillas, quédate en el piso saltando dentro y fuera del hueco, con los pies rápidos.",
            ),
        ),
    )

    /**
     * La primera version, en ingles (revision 1). No se siembra: solo sirve para reconocer
     * las instrucciones que nadie toco y cambiarlas por las de español. Lo que alguien haya
     * editado a mano no coincide con esto y se respeta.
     *
     * Paso a español el 19-sep-2026, el mismo dia: *"urge las instrucciones en español,
     * niko no maneja el ingles"*.
     */
    fun catalogInstructionsV1(): Map<String, ExerciseMedia> = catalogInstructionsV1Map

    /**
     * Las versiones viejas que se pueden cambiar por las del catalogo, por ejercicio: las
     * que se sembraron y nadie toco. Lo editado a mano no coincide con ninguna y se respeta.
     *
     * Ademas de la inglesa del catalogo (V1) entran las de la plancha lateral de la rutina
     * LUMBAR: en el telefono del coach venian de ahi, en ingles, y NIKO 2 las ensenaba asi.
     * El usuario lo acepto para el suyo tambien: *"imagino que ese cambio tambien me afecta
     * a mi, pero no hay problema"*. El puente de la lumbar NO entra: no se pidio.
     */
    /**
     * Las versiones de instrucciones que este codigo sembro alguna vez y que, por tanto, se
     * pueden reemplazar por lo que llegue del servidor (TD-139).
     *
     * Es la misma idea que sostiene [supersededInstructions] -lo sembrado es reemplazable, lo
     * escrito a mano no- extendida a la actual: en la PRIMERA sincronizacion el telefono ya
     * trae las del APK, y sin contarlas como reemplazables no entraria nunca nada.
     */
    fun replaceableInstructions(): Map<String, List<ExerciseMedia>> {
        val out = supersededInstructions().toMutableMap()
        (catalogInstructions() + lumbarInstructions()).forEach { (id, m) ->
            out[id] = (out[id].orEmpty() + m).distinct()
        }
        return out
    }

    fun supersededInstructions(): Map<String, List<ExerciseMedia>> {
        val lumbar = lumbarInstructions()
        val out = catalogInstructionsV1Map.mapValues { (_, m) -> listOf(m) }.toMutableMap()
        listOf("ex_side_plank_l", "ex_side_plank_r").forEach { id ->
            lumbar[id]?.let { out[id] = out[id].orEmpty() + it }
        }
        // Revisiones 3 y 4 del catalogo: escritas en femenino pensando en NIKO -"apoyada",
        // "sentada"-, cuando las del catalogo le llegan a todos. Se sembraron en el telefono
        // del coach el 19-sep y se reconocen para cambiarlas por las neutras.
        feminineV3().forEach { (id, m) -> out[id] = out[id].orEmpty() + m }
        return out
    }

    private fun feminineV3(): Map<String, ExerciseMedia> {
        val actual = catalogInstructions()
        fun conPrimerPaso(id: String, primero: String) =
            actual.getValue(id).let { it.copy(instructions = listOf(primero) + it.instructions.drop(1)) }
        return mapOf(
            "ex_russian_twist" to conPrimerPaso("ex_russian_twist", """Sentada en el piso, rodillas dobladas, talones apoyados. Inclina el torso atrás hasta sentir el abdomen trabajando."""),
            "ex_side_plank_l" to conPrimerPaso("ex_side_plank_l", """De lado, apoyada en el antebrazo izquierdo, con el codo justo debajo del hombro."""),
            "ex_side_plank_r" to conPrimerPaso("ex_side_plank_r", """De lado, apoyada en el antebrazo derecho, con el codo justo debajo del hombro."""),
        )
    }

    private val catalogInstructionsV1Map: Map<String, ExerciseMedia> = mapOf(
        "ex_hip_thrust" to ExerciseMedia(
            listOf(
                "Upper back on the edge of the bench, just below the shoulder blades. Bar over the hip crease, with a pad.",
                "Feet flat, shoulder-width apart. At the top your shins should be vertical.",
                "Chin tucked, eyes forward, not at the ceiling.",
                "Drive through the heels and raise the hips until knees, hips and shoulders make a straight line.",
                "PAUSE 2 seconds at the top, squeezing the glutes. Do not arch the lower back to go higher.",
                "Lower under control.",
            ),
        ),
        "ex_romanian_deadlift" to ExerciseMedia(
            listOf(
                "Stand tall holding the bar at hip height, hands just outside the thighs, feet hip-width apart.",
                "Knees slightly bent, and they stay that way the whole rep.",
                "Push the hips BACK, as if closing a car door with your backside. The bar slides down the thighs, close to the legs.",
                "Back flat, chest proud. Go down until the back of the thighs stretch hard, usually just below the knees.",
                "Squeeze the glutes to stand up. Do not lean back at the top.",
            ),
        ),
        "ex_hip_abduction" to ExerciseMedia(
            listOf(
                "Lie on your side. Head resting on the bottom arm.",
                "Bottom leg bent about 90 degrees for balance. Top leg straight, in line with the body, ankle weight on it.",
                "The key: top leg slightly BEHIND the body line, toes pointing forward or a bit down. Never to the ceiling: that works the hip flexor, not the glute.",
                "Lift the leg 30-40 cm, no higher. Hold 1 second, lower in 2-3 seconds without resting it.",
                "The top hip does not roll back. Imagine your back against a wall.",
                "You should feel it on the side of the glute, above and behind the hip bone. Feel it in the front of the thigh? Toes more down, leg further back.",
            ),
        ),
        "ex_bulgarian_split_squat" to ExerciseMedia(
            listOf(
                "Rear foot on the bench, laces down. Front foot far forward: a LONG stride.",
                "Lean the chest forward, 30 to 45 degrees, with the back flat.",
                "Lower until the front thigh is about parallel to the floor.",
                "Push through the front heel to stand. It should burn in the glute of the front leg.",
                "Feeling it only in the front of the thigh? Longer stride and more lean. Upright with a short stride is a quad exercise.",
            ),
        ),
        "ex_back_extension" to ExerciseMedia(
            listOf(
                "Hips on the pad, just below the hip crease, so the hips can bend freely.",
                "Feet slightly turned out. Chin tucked.",
                "Lower by bending at the hips.",
                "Come up by squeezing the glutes until the body is a straight line. Stop there: do not arch past straight.",
            ),
        ),
        "ex_neck_iso" to ExerciseMedia(
            listOf(
                "Isometric: push without moving. The head stays still, the hand does not let it go.",
                "Sit or stand tall, eyes forward, chin slightly tucked.",
                "Each set is one direction, in this order. Set 1, FRONT: palm on the forehead, push the head forward against it.",
                "Set 2, BACK: hands clasped behind the head, push the head back against them.",
                "Set 3, RIGHT: right palm on the right temple, push as if taking the ear to the shoulder.",
                "Set 4, LEFT: the same on the other side.",
                "Half your strength, not all of it. Breathe normally the whole time.",
                "If something pinches or runs down the arm, stop there.",
            ),
        ),
        "ex_step_up" to ExerciseMedia(
            listOf(
                "Box at knee height or a bit higher. The WHOLE foot on the box.",
                "Lean the chest slightly forward and push through the heel of the top foot.",
                "Stand up tall without pushing off the bottom foot.",
                "Lower slowly, controlling it with the top leg.",
            ),
        ),
        "ex_glute_bridge" to ExerciseMedia(
            listOf(
                "Lie on your back, knees bent, feet flat and hip-width apart.",
                "Push through the heels and squeeze the glute at the top.",
                "If you feel the lower back working, you are not using the glute.",
            ),
        ),
    )

    /** Igual que [withLumbarRevision], para las rutinas de NIKO. */
    fun withNikoRevision(trainings: List<Training>, lang: String): List<Training> {
        val out = trainings.toMutableList()
        nikoTrainings(lang).forEach { nuevo ->
            val i = out.indexOfFirst { it.id == nuevo.id }
            if (i < 0) {
                out.add(nuevo)
            } else {
                out[i] = nuevo.copy(
                    uid = out[i].uid,
                    createdAt = if (out[i].createdAt > 0L) out[i].createdAt else nuevo.createdAt,
                )
            }
        }
        return out
    }

    fun withLumbarRevision(trainings: List<Training>, lang: String): List<Training> {
        val nuevos = listOf(lumbarTraining(lang), lumbarBadDayTraining(lang))
        val out = trainings.toMutableList()
        nuevos.forEach { nuevo ->
            val i = out.indexOfFirst { it.id == nuevo.id }
            if (i < 0) {
                out.add(nuevo)
            } else {
                val viejo = out[i]
                out[i] = nuevo.copy(
                    uid = viejo.uid,
                    createdAt = if (viejo.createdAt > 0L) viejo.createdAt else nuevo.createdAt,
                )
            }
        }
        return out
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
            weightType = weightType,
            barWeight = barWeight,
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
            barWeight: Double = 20.0,
        ): Exercise = ex(
            exerciseId,
            note = note,
            sets = weights.size,
            mode = WorkMode.REPS,
            work = count,
            prep = 10,
            rest = 60,
            weightType = weightType,
            barWeight = barWeight,
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

        // La velocidad va como DATO del ejercicio y ya no dentro de la nota (TD-124): en la
        // nota es texto que nadie puede comparar entre sesiones, y es la variable que mas ha
        // movido el dolor -3, 4, 5 y 6 km/h en cuatro dias-. Ahora el player la ensena con
        // - y +, y lo que quede al terminar es lo que se registra.
        fun walk(name: String, sec: Int, note: String, kmh: Double?): Workout = Workout(
            id = id(),
            name = name,
            exercises = listOf(ex("ex_walk", note = note, work = sec).copy(speedKmh = kmh)),
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
                // La barra del puente pesa 6 kg, no los 20 que trae el app por defecto. Ese
                // 20 no solo ensuciaba el registro: el 15-sep el player le enseno "40 kg"
                // cuando iban a ser 26, le parecio mucho y bajo la carga. Un numero mal
                // puesto le cambio el entrenamiento.
                //
                // Discos 0/20/35 sobre esa barra: 6, 26 y 41 kg. La serie de barra sola es
                // suya y se respeta -entrar al patron sin carga, en una rutina de columna,
                // es buena idea-. La de arriba fue 21 -> 26 -> 31 -> 36 -> 41, cada subida
                // pedida por el cuerpo: el 19-sep marco LIGERAS las nueve series del bloque.
                //
                // 41 ya se acerca a su hip thrust de MASTER (40-70). Hasta aqui se estaba
                // alcanzando su nivel real; cuando llegue, cargar la cadera los siete dias deja
                // de tener sentido y el bloque pasa a tres por semana (ver coach-log 19-sep).
                loaded("ex_glute_bridge", 12, "Bar on the hips, push through the heels", listOf(0.0, 20.0, 35.0), WeightType.BARBELL, barWeight = 6.0),
                // Las tres "ligero" el 17-sep y la de arriba otra vez el 18: sube entera.
                // UNA mancuerna (TD-130): iba como TOTAL, que es tambien como van las maquinas,
                // y el player no podia decir "1 de 10". El numero por serie es el mismo, asi
                // que el historial no se parte.
                loaded("ex_suitcase_carry", 2, "One trip of 30-40 m per side", listOf(12.5, 15.0, 17.5), WeightType.DUMBBELL).copy(dumbbellCount = 1),
                // Igual: "ligero" en las tres el 17-sep, y las dos primeras el 18.
                // Despues de 20 viene la de 22.5 -no estaba en el inventario del 18-sep; la
                // agrego el usuario el 19- y despues 25. El salto de 22.5 a 25 es de 11%.
                loaded("ex_box_squat", 8, "Goblet at the chest, chest up", listOf(15.0, 17.5, 20.0), WeightType.DUMBBELL).copy(dumbbellCount = 1),
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
