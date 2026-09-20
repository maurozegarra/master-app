package com.maurozegarra.master

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maurozegarra.master.audio.AlarmPlayer
import com.maurozegarra.master.data.AssignmentRepository
import com.maurozegarra.master.data.AuthStore
import com.maurozegarra.master.data.AutoBackup
import com.maurozegarra.master.i18n.I18n
import com.maurozegarra.master.data.ImportSummary
import com.maurozegarra.master.data.MasterDefaults
import com.maurozegarra.master.data.ExerciseCatalog
import com.maurozegarra.master.data.ExerciseMediaStore
import com.maurozegarra.master.data.SettingsStore
import com.maurozegarra.master.data.VideoCache
import com.maurozegarra.master.data.VideoRepository
import com.maurozegarra.master.data.VideoState
import com.maurozegarra.master.data.WorkoutStore
import com.maurozegarra.master.model.AlarmSound
import com.maurozegarra.master.model.Exercise
import com.maurozegarra.master.model.ExerciseDef
import com.maurozegarra.master.model.ExerciseMedia
import com.maurozegarra.master.model.ExerciseRecord
import com.maurozegarra.master.model.PlayerStep
import com.maurozegarra.master.model.Profile
import com.maurozegarra.master.model.SessionLog
import com.maurozegarra.master.model.SessionStatus
import com.maurozegarra.master.model.reorderedFrom
import com.maurozegarra.master.model.SessionSource
import com.maurozegarra.master.model.SessionSync
import com.maurozegarra.master.model.Archive
import com.maurozegarra.master.model.PublishSync
import com.maurozegarra.master.model.MediaSync
import com.maurozegarra.master.model.MissingContent
import com.maurozegarra.master.model.DeliveryCheck
import com.maurozegarra.master.model.SPEED_MAX
import com.maurozegarra.master.model.SPEED_MIN
import com.maurozegarra.master.model.StepEngine
import com.maurozegarra.master.model.StepKind
import com.maurozegarra.master.model.Training
import com.maurozegarra.master.model.usedExerciseIds
import com.maurozegarra.master.model.Workout
import com.maurozegarra.master.model.activeExercises
import com.maurozegarra.master.model.activeVariant
import com.maurozegarra.master.model.WorkoutVariant
import com.maurozegarra.master.model.deepCopy
import com.maurozegarra.master.model.duplicate
import com.maurozegarra.master.model.hasContent
import com.maurozegarra.master.model.lastTrainedAt
import com.maurozegarra.master.model.mergeAssigned
import com.maurozegarra.master.model.sortedByLastTrained
import com.maurozegarra.master.model.weightTotal
import com.maurozegarra.master.notify.WorkoutPlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Resultado de un import: qué entró y si quedó copia del estado anterior.
 *
 * [backedUp] se le dice al usuario. Un import reemplaza todos sus datos, y si la copia
 * de seguridad previa no se pudo escribir tiene derecho a saberlo en ese momento, no
 * cuando vaya a buscarla.
 */
data class ImportResult(val summary: ImportSummary, val backedUp: Boolean)

/**
 * Cómo acabó una sincronización, para poder decirlo en vez de callar.
 *
 * Existe porque los tres finales se parecen desde fuera —la pantalla no cambia— y
 * significan cosas opuestas: no se pudo leer, no te toca nada, o ya lo tenías todo.
 */
sealed interface SyncResult {
    /** Este dispositivo todavía no tiene dueño, así que no hay nada que traer. */
    data object NoProfile : SyncResult

    /** No se pudo leer. No se ha tocado ningún training. */
    data object Failed : SyncResult

    /** Se leyó bien: [assigned] es cuántos le tocan, [changed] si alguno cambió aquí. */
    data class Ok(val assigned: Int, val changed: Boolean) : SyncResult
}

/**
 * Estado y lógica principal de MASTER (jerarquía Training > Workout > Exercise).
 * Mantiene la lista de trainings persistida y un "draft" en edición que contiene
 * todo el árbol (workouts → exercises) hasta que se guarda.
 */
class MasterViewModel(
    app: Application,
    private val store: WorkoutStore,
    private val alarmPlayer: AlarmPlayer,
    private val autoBackup: AutoBackup,
    private val mediaStore: ExerciseMediaStore,
    private val videos: VideoRepository,
    private val videoCache: VideoCache,
    private val assignments: AssignmentRepository,
    private val auth: AuthStore,
) : AndroidViewModel(app) {

    val trainings = mutableStateListOf<Training>()
    private val customExercises = mutableStateListOf<ExerciseDef>()
    private var nextId = System.currentTimeMillis()

    // ---------- Player state (debe ir antes del init para que restorePlayerState funcione) ----------

    var playerTrainingId by mutableStateOf<Long?>(null)
        private set
    var playerSteps by mutableStateOf<List<PlayerStep>>(emptyList())
        private set
    var playerName by mutableStateOf("")
        private set
    var playerStarted by mutableStateOf(false)
        private set
    var playerFinished by mutableStateOf(false)
        private set
    var playerRunning by mutableStateOf(false)
        private set
    var playerIndex by mutableStateOf(0)
        private set
    var playerTotalSteps by mutableStateOf(0)
        private set
    var playerRemainingMs by mutableStateOf(0L)
        private set
    var playerStep by mutableStateOf<PlayerStep?>(null)
        private set

    /**
     * OSD: si las dos franjas de arriba del player están visibles.
     *
     * **Nace oculto y solo lo saca el tap del usuario.** No lo levanta arrancar la corrida
     * ni avanzar de etapa: la corrida se sigue por el vídeo, el reloj y los controles, que
     * están siempre ahí. Que las franjas salieran solas hacía que en un ejercicio por
     * repeticiones aparecieran en cada serie, porque cada una avanza confirmando.
     */
    var playerControlsVisible by mutableStateOf(false)
        private set


    /** ID del training con un player en curso (para mostrar indicador en la lista). */
    var activePlayerTrainingId by mutableStateOf<Long?>(null)
        private set

    // ---------- Historial de sesiones ----------

    /** Sesiones completadas (las registra el servicio del player en el mismo store). */
    val sessions = mutableStateListOf<SessionLog>()

    /** Material por ejercicio del catalogo. Clave: exerciseId, no el id de la instancia. */
    private val exerciseMedia = mutableStateMapOf<String, ExerciseMedia>()

    /**
     * El snapshot automatico no corre hasta terminar el init: durante el arranque el
     * seeding de una instalacion limpia llama a persist(), y ese estado no debe pisar
     * el respaldo bueno que haya en Documents/MASTER/.
     */
    private var snapshotReady = false

    /**
     * Una sola subida de sesiones a la vez: el arranque, el fin de sesion y el sync pueden
     * coincidir (TD-126).
     *
     * Declarado AQUI, antes del `init`, y no junto a [syncSessions]: Kotlin inicializa en
     * orden de aparicion, y el `init` llama a refreshSessions -> syncSessions, que lanza un
     * hilo que usa este candado. Declarado mas abajo, ese hilo lo encontraba todavia en null
     * y el app se caia al arrancar, en bucle -paso con la v1.0.283 el 19-sep-. Es la misma
     * trampa que el comentario del init ya advierte para `syncing`.
     */
    private val sessionSyncLock = kotlinx.coroutines.sync.Mutex()

    /** Lo mismo para republicar: ver la nota de arriba sobre el orden de inicializacion. */
    private val republishLock = kotlinx.coroutines.sync.Mutex()

    /** Y para las instrucciones (TD-139). Misma razon, mismo sitio. */
    private val mediaSyncLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Aviso de que un training repartido se volvio a publicar, para que la pantalla lo diga
     * (TD-132). Null cuando no hay nada que avisar.
     */
    var republishNotice by mutableStateOf<String?>(null)

    /**
     * Si este dispositivo puede escribir. Lo decide el servidor; esto solo pinta la UI.
     *
     * Es estado observable y no una lectura directa del almacenamiento: la accion
     * "Assign to" de la lista de trainings depende de esto, y con una lectura simple solo
     * apareceria cuando algo mas obligase a repintar esa pantalla.
     *
     * Declarado AQUI, antes del `init`, por lo mismo que los candados de arriba (TD-142).
     * Vivia junto al resto de lo del entrenador, y desde que [republishChanged] lo mira
     * (TD-132) eso lo convirtio en una bomba: el `init` llama a persist() al sembrar, y ahi
     * el delegado todavia era null. Lo que corre desde el `init` mira [AuthStore] y no este
     * estado, pero la declaracion se queda arriba igual: el siguiente que lo lea desde el
     * arranque no tiene por que saber esta historia.
     */
    var isCoach by mutableStateOf(auth.isCoach)
        private set

    /**
     * Los uid archivados (TD-138), en memoria para que la lista reaccione al archivar.
     * Declarado antes del `init` por la misma razon que los candados de arriba.
     */
    var archivedUids by mutableStateOf(store.archivedUids())
        private set

    /** Lo que se ve en la lista: todo menos lo archivado. */
    val visibleTrainings: List<Training> get() = Archive.visible(trainings, archivedUids)

    /** Lo archivado, que la lista ensena plegado al final. */
    val archivedTrainings: List<Training> get() = Archive.archived(trainings, archivedUids)

    /**
     * Archiva o desarchiva. No borra, no desasigna y no para las republicaciones (TD-132):
     * solo deja de ocupar sitio en la lista.
     */
    fun setArchived(trainingId: Long, archived: Boolean) {
        val uid = trainings.firstOrNull { it.id == trainingId }?.uid ?: return
        if (uid.isBlank()) return
        archivedUids = if (archived) archivedUids + uid else archivedUids - uid
        store.saveArchivedUids(archivedUids)
    }

    private fun newId(): Long = nextId++

    init {
        val firstRun = store.isFirstRun()
        trainings.addAll(store.loadTrainings())
        customExercises.addAll(store.loadCustomExercises())
        // Lo guardado ANTES de las correcciones del arranque, para saber al final si alguna
        // cambio algo (TD-121). Se compara el resultado y no se avisa desde cada correccion:
        // una lista de llamadas escrita a mano se queda atras en cuanto se agrega otra.
        val antesDeCorregir = Triple(trainings.toList(), store.loadSessions(), mediaStore.load())
        if (firstRun && trainings.isEmpty()) {
            trainings.add(MasterDefaults.masterTraining(lang()))
            trainings.add(MasterDefaults.frikiNikiTraining(lang()))
            applyLumbarRevision()
            // Una instalacion limpia no es la del usuario: no hay historial que marcar ni
            // reordenar, ni sesion pasada que reconstruir. Sin estas marcas, todo eso caeria
            // en el segundo arranque.
            store.setFirstSessionSeeded()
            store.setFirstSessionMarked()
            store.setLumbarSessionsReordered()
            store.setFrikiSeeded()
            store.setMasterV2Seeded()
            store.setMasterV3Seeded()
            persist()
        } else {
            var changed = false
            if (!store.isFrikiSeeded()) {
                val masterIdx = trainings.indexOfFirst { it.name == "Master" }
                val friki = MasterDefaults.frikiNikiTraining(lang())
                if (masterIdx >= 0) trainings.add(masterIdx + 1, friki) else trainings.add(friki)
                store.setFrikiSeeded()
                changed = true
            }
            if (!store.isMasterV2Seeded()) {
                val masterIdx = trainings.indexOfFirst { it.name == "Master" }
                val master = MasterDefaults.masterTraining(lang())
                if (masterIdx >= 0) trainings[masterIdx] = master else trainings.add(0, master)
                store.setMasterV2Seeded()
                changed = true
            }
            if (!store.isMasterV3Seeded()) {
                val masterIdx = trainings.indexOfFirst { it.name == "Master" }
                val master = MasterDefaults.masterTraining(lang())
                if (masterIdx >= 0) trainings[masterIdx] = master else trainings.add(0, master)
                store.setMasterV3Seeded()
                changed = true
            }
            if (changed) persist()
            applyLumbarRevision()
            applyNikoRevision()
            cleanUpLumbarLeftovers()
            seedFirstSession()
            updateWalkInstructions()
            markReconstructedSession()
            reorderLumbarSessions()
            fixSep15Weights()
            fillSep17Feedback()
        }
        // Fuera del if a proposito: tambien una instalacion limpia las necesita.
        seedCatalogInstructions()
        observePlayer()
        migrateRestorePrefs()
        restorePlayerState()
        refreshSessions()
        exerciseMedia.putAll(mediaStore.load())
        snapshotReady = true
        // Si una correccion del arranque cambio los datos, el respaldo tiene que decirlo: el
        // coach lee el historial desde ahi, y un respaldo viejo le hace decir cosas que no
        // son (TD-121). En una instalacion limpia NO: ahi lo unico que hay son los defaults,
        // y escribirlos es justo lo que el guardia de [snapshot] existe para impedir.
        if (!firstRun && Triple(trainings.toList(), store.loadSessions(), mediaStore.load()) != antesDeCorregir) {
            snapshot()
        }
        // Aqui NO se sincroniza. El arranque en frio lo cubre MainActivity.onStart, igual
        // que cualquier vuelta a primer plano. Ademas seria imposible: syncAssignments lee
        // `syncing`, que es un mutableStateOf declarado mas abajo, y los inicializadores
        // corren en orden de declaracion — desde el init su delegado todavia es null.
    }

    /**
     * Marca como reconstruida la sesion del 13-sep, que el player nunca midio (TD-101).
     *
     * La sembro TD-090 antes de que existiera [SessionSource], asi que quedo guardada sin
     * el campo y al leerla se la tomaba por medida. Era justo la confusion que TD-101 viene
     * a impedir, y encima la introdujo el asistente.
     *
     * Se reconoce por su id fijo. Si no esta -porque el usuario la borro- no se hace nada.
     */
    private fun markReconstructedSession() {
        if (store.isFirstSessionMarked()) return
        val sesiones = store.loadSessions()
        val i = sesiones.indexOfFirst { it.id == MasterDefaults.FIRST_SESSION_ID }
        if (i >= 0 && sesiones[i].source != SessionSource.RECONSTRUCTED) {
            store.saveSessions(
                sesiones.toMutableList().also { it[i] = it[i].copy(source = SessionSource.RECONSTRUCTED) },
            )
        }
        store.setFirstSessionMarked()
    }

    /**
     * Corrige los pesos del puente en la sesion del 15-sep y la marca como editada (TD-105).
     *
     * Quedo guardada con 20/30/40 kg porque el ejercicio llevaba la barra en 20, que es el
     * valor por defecto del app; la suya pesa 6, asi que movio 6, 16 y 21. El numero
     * inflado ademas le hizo bajar la carga: un dato mal puesto no solo ensucio el
     * registro, le cambio el entrenamiento.
     *
     * Solo toca la sesion si sigue teniendo exactamente los pesos malos. Y la deja en
     * [SessionSource.EDITED]: corregir un registro esta bien, disimularlo no.
     *
     * Esto es una correccion a mano de un dato concreto, que es lo que habra que dejar de
     * hacer por codigo cuando exista el nivel 2 de TD-101.
     */
    private fun fixSep15Weights() {
        if (store.isSep15Fixed()) return
        val sesiones = store.loadSessions()
        val i = sesiones.indexOfFirst { it.id == MasterDefaults.SESSION_15_SEP_ID }
        if (i >= 0) {
            val s = sesiones[i]
            val ejercicios = s.exercises.map { er ->
                if (er.exerciseId != "ex_glute_bridge" ||
                    er.sets.map { it.weightKg } != MasterDefaults.SESSION_15_SEP_WRONG
                ) {
                    er
                } else {
                    er.copy(
                        sets = er.sets.mapIndexed { j, set ->
                            set.copy(weightKg = MasterDefaults.SESSION_15_SEP_RIGHT[j])
                        },
                    )
                }
            }
            if (ejercicios != s.exercises) {
                store.saveSessions(
                    sesiones.toMutableList().also {
                        it[i] = s.copy(exercises = ejercicios, source = SessionSource.EDITED)
                    },
                )
            }
        }
        store.setSep15Fixed()
    }

    /**
     * Escribe en la sesion del 17-sep lo que el usuario marco y el app boto (TD-120).
     *
     * La logica vive en [MasterDefaults.withSep17Feedback], que es pura y tiene su test;
     * aqui solo se aplica una vez. Igual que [fixSep15Weights], es una correccion a mano
     * de un dato concreto, y la sesion queda en [SessionSource.EDITED].
     */
    private fun fillSep17Feedback() {
        if (store.isSep17FeedbackFilled()) return
        val sesiones = store.loadSessions()
        val i = sesiones.indexOfFirst { it.id == MasterDefaults.SESSION_17_SEP_ID }
        if (i >= 0) {
            MasterDefaults.withSep17Feedback(sesiones[i])?.let { corregida ->
                store.saveSessions(sesiones.toMutableList().also { it[i] = corregida })
            }
        }
        store.setSep17FeedbackFilled()
    }

    /**
     * Devuelve a las dos sesiones lumbares el orden de la rutina (TD-102).
     *
     * Se guardaron antes de TD-099, o sea por orden alfabetico. Se limita a los dos
     * trainings lumbares **a proposito**: de ellos consta que no se reordenaron desde
     * entonces -lo unico que cambio fue la carga del bloque de cadera-, y para cualquier
     * otro training reconstruir el orden desde como esta hoy seria inventarlo.
     *
     * [reorderedFrom] devuelve null en cuanto algo no cuadra, y entonces esa sesion se
     * queda como esta.
     */
    private fun reorderLumbarSessions() {
        if (store.isLumbarSessionsReordered()) return
        val lumbares = trainings.filter {
            it.id == MasterDefaults.LUMBAR_ID || it.id == MasterDefaults.LUMBAR_BAD_DAY_ID
        }.associateBy { it.id }
        val sesiones = store.loadSessions()
        var changed = false
        val nuevas = sesiones.map { s ->
            val t = lumbares[s.trainingId] ?: return@map s
            val ordenada = s.reorderedFrom(t) ?: return@map s
            if (ordenada != s) changed = true
            ordenada
        }
        if (changed) store.saveSessions(nuevas)
        store.setLumbarSessionsReordered()
    }

    /**
     * Reescribe las indicaciones de la caminata (TD-091).
     *
     * Las primeras decian "no entrenes en la primera hora tras levantarte" a secas, y leido
     * a las seis de la manana eso se entiende como "hoy no entrenes". Lo que la regla
     * protege es la flexion lumbar con carga, no el movimiento, y esa diferencia es la que
     * le permite decidir solo cuando no tiene margen para esperar.
     *
     * **Solo pisa el texto si sigue siendo palabra por palabra el que se sembro.** Si lo
     * edito, es suyo: sembrar encima de lo que alguien escribio es la unica forma de que
     * esto le quite algo en vez de darle.
     */
    private fun updateWalkInstructions() {
        if (store.isWalkNoteUpdated()) return
        val current = mediaStore.load()
        val suyo = current["ex_walk"]
        if (suyo == null || suyo == MasterDefaults.WALK_INSTRUCTIONS_V1) {
            val nuevas = MasterDefaults.lumbarInstructions().getValue("ex_walk")
            mediaStore.save(current + ("ex_walk" to nuevas))
        }
        store.setWalkNoteUpdated()
    }

    /**
     * Anota en el historial la sesion del 13-sep-2026, que el usuario hizo antes de que el
     * training existiera en el app (TD-090).
     *
     * Es el unico registro que no midio el player, y va detras de su propia marca para que
     * no se duplique. Si ese dia ya hay una sesion **completa** de LUMBAR -porque la corrio
     * de verdad- no se anota nada: la medida gana sobre la reconstruida.
     *
     * La condicion pide COMPLETED y no una sesion cualquiera por lo que paso el 13-sep: el
     * usuario corrio el training cuatro minutos para comprobar que se habia sembrado bien,
     * y esa sesion parcial basto para que la version anterior de esto se callara y diera la
     * siembra por hecha. Una corrida de prueba no es la sesion que se esta reconstruyendo.
     */
    private fun seedFirstSession() {
        if (store.isFirstSessionSeeded()) return
        val log = MasterDefaults.lumbarFirstSession(lang())
        val yaEsta = store.loadSessions().any {
            it.trainingId == log.trainingId &&
                it.status == SessionStatus.COMPLETED &&
                sameDay(it.completedAt, log.completedAt)
        }
        if (!yaEsta) store.addSession(log)
        store.setFirstSessionSeeded()
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
            java.time.Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
    }

    /**
     * Borra de una vez los ejercicios propios que quedaron del intento de armar la rutina
     * lumbar a mano, antes de que existiera el training sembrado (TD-086).
     *
     * Solo se van los que **no use ningun training**: si alguno acabo dentro de uno, su
     * ejercicio se quedaria sin nombre de catalogo y en el player se leeria el id.
     *
     * Va detras de su propia marca y no de la de la siembra porque son dos cosas
     * distintas, y porque el dia que el app sepa borrar ejercicios propios desde la UI
     * este metodo se va entero y la marca se queda donde esta.
     */
    private fun cleanUpLumbarLeftovers() {
        if (store.isLumbarCleanupDone()) return
        val used = trainings.toList().usedExerciseIds()
        val sobran = LUMBAR_LEFTOVERS - used
        if (sobran.isNotEmpty()) {
            customExercises.removeAll { it.id in sobran }
            store.saveCustomExercises(customExercises.toList())
        }
        store.setLumbarCleanupDone()
    }

    /**
     * Pone la rutina lumbar al dia si el codigo trae una revision mas nueva (TD-103).
     *
     * Es el unico sitio por donde entra un cambio de la rutina. Sustituye a las tres
     * migraciones que habia -sembrar LUMBAR, sembrar el bad day y cargar el bloque de
     * cadera-: reemplazar por la definicion actual hace lo mismo que las tres y ademas
     * cubre todo lo que venga, sin codigo nuevo por cada ajuste.
     *
     * Las instrucciones van con merge y sin pisar: son del usuario en cuanto las toca.
     */
    /**
     * Siembra las rutinas de NIKO en el telefono del COACH, para que las asigne (TD-127).
     *
     * Van al telefono de el y no al de ella a proposito: quien disena las revisa antes de
     * repartirlas, y el reparto ya tiene su camino -asignar desde el telefono, que baja por
     * Supabase-. Mismo perfil que la rutina lumbar porque es el mismo telefono.
     */
    private fun applyNikoRevision() {
        if (assignments.profileId != MasterDefaults.LUMBAR_PROFILE) return
        if (store.nikoRevision() >= MasterDefaults.NIKO_REVISION) return
        val nuevos = MasterDefaults.withNikoRevision(trainings.toList(), lang())
        if (nuevos != trainings.toList()) {
            trainings.clear()
            trainings.addAll(nuevos)
            persist()
        }
        store.setNikoRevision(MasterDefaults.NIKO_REVISION)
    }

    private fun applyLumbarRevision() {
        // Solo en el telefono de su dueno. Ver MasterDefaults.LUMBAR_PROFILE.
        if (assignments.profileId != MasterDefaults.LUMBAR_PROFILE) {
            removeLumbarFromOtherPhone()
            return
        }
        if (store.lumbarRevision() >= MasterDefaults.LUMBAR_REVISION) return
        val nuevos = MasterDefaults.withLumbarRevision(trainings.toList(), lang())
        if (nuevos != trainings.toList()) {
            trainings.clear()
            trainings.addAll(nuevos)
            persist()
        }
        seedLumbarInstructions()
        store.setLumbarRevision(MasterDefaults.LUMBAR_REVISION)
    }

    /**
     * Quita la rutina lumbar de un telefono que no es el suyo, si llego a sembrarse.
     *
     * La v1.0.248 se publico antes de que la siembra mirara el perfil, asi que quien
     * actualizara en esa ventana se llevo dos trainings ajenos. Esto los retira.
     *
     * **Solo si no se han usado.** Si hay una sesion registrada contra ellos, alguien los
     * entreno y entonces ya no son un accidente: borrarlos le quitaria su historial. En ese
     * caso se quedan y que decida quien los tenga.
     */
    private fun removeLumbarFromOtherPhone() {
        val ajenos = setOf(MasterDefaults.LUMBAR_ID, MasterDefaults.LUMBAR_BAD_DAY_ID)
        val usados = store.loadSessions().map { it.trainingId }.toSet()
        val sobran = trainings.filter { it.id in ajenos && it.id !in usados }
        if (sobran.isEmpty()) return
        trainings.removeAll(sobran)
        persist()
    }

    /**
     * Instrucciones del training lumbar, sin pisar las que ya haya.
     *
     * El merge no es un detalle: `ex_cat_cow` es del catalogo y puede traer las que
     * escribio el usuario, y sembrar encima se las borraria sin que las pidiera nadie.
     */
    /**
     * Las instrucciones del catalogo, en TODOS los telefonos y sin pisar nada (TD-131).
     *
     * Sin puerta de perfil a proposito: son del catalogo, no de nadie, y el telefono de un
     * atleta asignado es justo donde mas hacen falta -asignar no las manda-.
     */
    private fun seedCatalogInstructions() {
        if (store.catalogInstructionsRevision() >= MasterDefaults.CATALOG_INSTRUCTIONS_REVISION) return
        val current = mediaStore.load()
        val viejas = MasterDefaults.supersededInstructions()
        // Se escribe donde no hay nada, o donde sigue una version vieja tal cual se sembro:
        // esa nadie la toco. Lo editado a mano no coincide con nada de esto y se queda.
        val merged = current + MasterDefaults.catalogInstructions().filterKeys { id ->
            val actual = current[id]
            actual == null || actual.isEmpty || actual in viejas[id].orEmpty()
        }
        if (merged != current) mediaStore.save(merged)
        store.setCatalogInstructionsRevision(MasterDefaults.CATALOG_INSTRUCTIONS_REVISION)
    }

    private fun seedLumbarInstructions() {
        val current = mediaStore.load()
        val merged = current + MasterDefaults.lumbarInstructions().filterKeys { it !in current }
        if (merged != current) mediaStore.save(merged)
    }

    private fun migrateRestorePrefs() {
        val app = getApplication<Application>()
        val prefs = app.getSharedPreferences("master_restore", android.content.Context.MODE_PRIVATE)
        if (prefs.contains("active")) return
        val legacy = app.getSharedPreferences("athlete_player", android.content.Context.MODE_PRIVATE)
        val all = legacy.all
        if (all.isEmpty()) return
        prefs.edit().apply {
            all.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Boolean -> putBoolean(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Float -> putFloat(k, v)
                }
            }
        }.apply()
    }

    /** Reconecta la UI al player activo tras reabrir la app (estilo YouTube). */
    fun restorePlayerState() {
        val prefs = getApplication<Application>().getSharedPreferences("master_restore", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return
        val steps = WorkoutPlayerService.decodeSteps(prefs.getString("steps", "[]") ?: "[]")
        if (steps.isEmpty()) return
        val trainingId = prefs.getLong("workoutId", 0L)
        playerSteps = steps
        playerTrainingId = trainingId
        activePlayerTrainingId = trainingId
        playerName = prefs.getString("name", "") ?: ""
        playerStarted = true
        playerFinished = false
        playerRunning = prefs.getBoolean("running", false)
        playerIndex = prefs.getInt("index", 0).coerceIn(0, steps.lastIndex)
        playerTotalSteps = steps.size
        val step = steps[playerIndex]
        playerStep = step
        val endAt = prefs.getLong("endAt", 0L)
        playerRemainingMs = if (step.manual) {
            0L
        } else if (playerRunning && endAt > 0) {
            (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        } else {
            prefs.getLong("remainingMs", step.durationSec * 1000L)
        }
        if (PlayerBus.state.value == null) {
            WorkoutPlayerService.reconnect(getApplication())
        }
    }

    /**
     * Republica los trainings repartidos que cambiaron (TD-132).
     *
     * Cualquier cambio, no solo las revisiones del coach: tambien lo que el edite en el app.
     * Lo eligio asi el usuario, y por eso avisa cada vez -una edicion por error tiene que
     * verse-. Sin red o sin sesion de entrenador no se pierde: la huella no se marca y se
     * reintenta en la proxima sincronizacion.
     */
    private fun republishChanged() {
        // auth.isCoach y no isCoach: esto se llama desde persist(), que corre dentro del
        // `init` al sembrar. Preguntarle al almacen no depende del orden de inicializacion
        // (TD-142).
        if (!auth.isCoach) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!republishLock.tryLock()) return@launch
            try {
                // Sin red no se sabe que esta repartido: se trabaja solo con las huellas que
                // ya hay, y lo demas espera a la proxima vuelta.
                val repartidos = assignments.assignedUids() ?: emptySet()
                val cambiados = PublishSync.toRepublish(trainings.toList(), store.publishLedger(), repartidos)
                if (cambiados.isEmpty()) return@launch
                val avisos = mutableListOf<Pair<String, List<String>>>()
                cambiados.forEach { (training, huella) ->
                    if (assignments.republish(training) != null) return@forEach
                    store.markPublished(training.uid, huella)
                    val quienes = assignments.profilesWith(training.uid).orEmpty()
                    if (quienes.isNotEmpty()) avisos += training.name to quienes
                }
                if (avisos.isEmpty()) return@launch
                val nombres = assignments.directory()?.associate { it.id to it.name }.orEmpty()
                val texto = avisos.joinToString("\n") { (nombre, ids) ->
                    "$nombre \u2192 " + ids.joinToString(", ") { nombres[it] ?: it }
                }
                withContext(Dispatchers.Main) { republishNotice = texto }
            } finally {
                republishLock.unlock()
            }
        }
    }

    /**
     * Las instrucciones de los ejercicios, en la misma pasada que lo demas (TD-139).
     *
     * **La direccion la marca quien es coach**: el suyo es el telefono que las escribe, asi
     * que publica; el de un atleta recibe, asi que aplica. Nadie fusiona en las dos
     * direcciones. Sin red no se pierde nada: la huella no se marca y se reintenta.
     */
    private fun syncMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!mediaSyncLock.tryLock()) return@launch
            try {
                if (auth.isCoach) publishMediaChanges() else applyPublishedMedia()
            } finally {
                mediaSyncLock.unlock()
            }
        }
    }

    private fun publishMediaChanges() {
        for ((id, media) in MediaSync.toPublish(mediaStore.load(), store.mediaLedger())) {
            // Al primer fallo se corta: sin red o sin sesion, seguir es gastar intentos.
            if (assignments.publishMedia(id, media) != null) return
            store.markMediaSynced(id, MediaSync.fingerprintOf(media))
        }
    }

    private suspend fun applyPublishedMedia() {
        val remote = assignments.exerciseMedia() ?: return
        val local = mediaStore.load()
        val aplicar = MediaSync.toApply(
            remote = remote,
            local = local,
            ledger = store.mediaLedger(),
            seeded = MasterDefaults.replaceableInstructions(),
        )
        if (aplicar.isEmpty()) return
        val merged = local + aplicar
        mediaStore.save(merged)
        aplicar.forEach { (id, m) -> store.markMediaSynced(id, MediaSync.fingerprintOf(m)) }
        withContext(Dispatchers.Main) {
            exerciseMedia.clear()
            exerciseMedia.putAll(merged)
            snapshot()
        }
    }

    private fun persist() {
        // Se recoge lo que devuelve el store: viene con los uid rellenados, y sin eso la
        // lista de la pantalla se queda con los huecos hasta el siguiente arranque.
        val guardados = store.saveTrainings(trainings.toList())
        if (guardados != trainings.toList()) {
            trainings.clear()
            trainings.addAll(guardados)
        }
        snapshot()
        // Guardar es el unico sitio por donde pasa cualquier cambio de un training, venga de
        // una revision del coach o del editor: es donde toca mirar si hay que republicar.
        republishChanged()
    }

    /**
     * Escribe un snapshot automático en almacenamiento compartido (ver [AutoBackup]).
     *
     * Las dos guardas importan. [snapshotReady] evita disparar durante el init, cuando el
     * seeding de una instalación limpia llama a `persist()`; y no se escribe con datos
     * vacíos. Sin eso, reinstalar la app pisaría el snapshot bueno con sus defaults —que
     * es exactamente como se perdió el historial el 29-ago-2026, pero con el backup
     * automático de Google.
     */
    private fun snapshot() {
        if (!snapshotReady) return
        if (trainings.isEmpty() && sessions.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            autoBackup.write(store.exportJson())
        }
    }

    /** Recarga desde almacenamiento (tras restaurar un backup). */
    fun reload() {
        trainings.clear()
        trainings.addAll(store.loadTrainings())
        customExercises.clear()
        customExercises.addAll(store.loadCustomExercises())
    }

    // ---------- Vídeo e instrucciones por ejercicio (TD-058 / TD-059) ----------

    fun mediaFor(exerciseId: String): ExerciseMedia? = exerciseMedia[exerciseId]

    /** En qué punto está el vídeo del ejercicio: descargado, bajando, pendiente o ninguno. */
    fun videoStateFor(exerciseId: String): VideoState = videos.stateOf(exerciseId)

    /** El archivo listo para reproducir, o null. */
    fun videoFileFor(exerciseId: String): java.io.File? = videos.fileFor(exerciseId)

    /** Si el vídeo que se ve es uno que puso el usuario, y por tanto puede quitarlo. */
    fun hasOwnVideo(exerciseId: String): Boolean = videoCache.hasOwnVideo(exerciseId)

    /**
     * Pide los vídeos de un training entero. Se llama al abrir su preview: es el momento
     * en que se sabe qué hará el usuario y todavía queda tiempo para traerlos.
     */
    fun prefetchVideos(exerciseIds: List<String>) = videos.requestAll(exerciseIds)

    /** Pide el vídeo del ejercicio que viene ahora, saltándose la cola. */
    fun requestVideoNow(exerciseId: String) = videos.request(exerciseId, urgent = true)

    /**
     * Copia el vídeo elegido por el usuario a la caché privada del app, donde gana sobre
     * el publicado. [onDone] recibe false si no se pudo copiar.
     */
    fun assignVideo(exerciseId: String, source: () -> java.io.InputStream?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val target = videoCache.ownFile(exerciseId)
                    target.parentFile?.mkdirs()
                    source()?.use { input -> target.outputStream().use { input.copyTo(it) } }
                        ?: error("sin origen")
                }.isSuccess
            }
            if (ok) videos.refreshState(exerciseId)
            onDone(ok)
        }
    }

    /** Quita el vídeo propio. Si el ejercicio tiene uno publicado, vuelve a ser el que se ve. */
    fun removeVideo(exerciseId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { videoCache.deleteOwn(exerciseId) }
            videos.refreshState(exerciseId)
        }
    }

    /** Cuánto ocupan los vídeos descargados, para Ajustes. Es disco: fuera del hilo principal. */
    fun loadDownloadedVideoBytes(onDone: (Long) -> Unit) {
        viewModelScope.launch {
            onDone(withContext(Dispatchers.IO) { videoCache.bytesDownloaded() })
        }
    }

    /**
     * Borra los vídeos descargados del manifiesto (TD-072). Los propios se quedan: esos no
     * se recuperan solos.
     *
     * Después se recalcula el estado de todos, para que los publicados vuelvan a pendiente
     * y se descarguen otra vez la próxima vez que un training los pida, en vez de quedarse
     * marcados como listos apuntando a un archivo que ya no existe.
     */
    fun clearDownloadedVideos(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { videoCache.clearDownloaded() }
            videos.rebuildStates()
            onDone()
        }
    }


    /**
     * Que le llegaria incompleto a quien reciba este training (TD-143). Lista vacia = nada
     * que avisar.
     */
    fun deliveryGaps(trainingId: Long): List<MissingContent> {
        val training = trainings.firstOrNull { it.id == trainingId } ?: return emptyList()
        return DeliveryCheck.gaps(training, exerciseMedia.toMap(), videos.publishedIds())
    }

    fun setInstructions(exerciseId: String, steps: List<String>) {
        updateMedia(exerciseId) { it.copy(instructions = steps.filter { s -> s.isNotBlank() }) }
    }

    private fun updateMedia(exerciseId: String, transform: (ExerciseMedia) -> ExerciseMedia) {
        val updated = transform(exerciseMedia[exerciseId] ?: ExerciseMedia())
        if (updated.isEmpty) exerciseMedia.remove(exerciseId) else exerciseMedia[exerciseId] = updated
        mediaStore.save(exerciseMedia.toMap())
        snapshot()
        // Corregir una instruccion es publicarla: es el punto entero de TD-139, que no haga
        // falta una version del app para que le llegue a quien entrena con ella.
        syncMedia()
    }

    // ---------- Perfil y trainings asignados (TD-063) ----------

    /** Perfil elegido en este dispositivo, o null si aún no se ha elegido ninguno. */
    val profileId: String? get() = assignments.profileId

    /**
     * Nombre del perfil elegido.
     *
     * Es estado observable y no una lectura del almacenamiento: Ajustes lo enseña, y con
     * una lectura simple solo cambiaría cuando algo más obligase a repintar la pantalla.
     */
    var profileName by mutableStateOf(assignments.profileName)
        private set

    /**
     * Perfiles publicados, para elegir. Va a la red, así que se llama al abrir la lista.
     *
     * **Null es "no se pudo leer", no "no hay ninguno"**. Enseñar una lista vacía cuando
     * falló la conexión haría creer que se borraron los perfiles.
     */
    fun loadProfiles(onDone: (List<Profile>?) -> Unit) {
        viewModelScope.launch {
            onDone(withContext(Dispatchers.IO) { assignments.directory() })
        }
    }

    /** Fija quién usa este dispositivo y trae en el acto lo que le toque. */
    fun chooseProfile(profile: Profile, onDone: (SyncResult) -> Unit = {}) {
        assignments.profileId = profile.id
        assignments.profileName = profile.name
        profileName = profile.name
        // Sin pasar por el freno del sincronizado automático: el perfil acaba de cambiar,
        // y lo que se leyó hace un minuto era de otra persona.
        runSync(onDone)
    }

    /**
     * Deja el dispositivo sin dueño: deja de bajar asignaciones.
     *
     * **No retira los trainings que ya llegaron.** Retirarlos sería lo simétrico, pero
     * rompería el enlace entre cada training y su historial —`SessionLog.trainingId`
     * apunta al id local, y volver a elegir el perfil crearía uno nuevo—, y eso cuesta
     * mucho más de recuperar que un training de sobra en la lista, que se borra a mano.
     */
    fun clearProfile() {
        assignments.profileId = null
        assignments.profileName = ""
        profileName = ""
    }

    // ---------- Entrenador: crear personas y asignar (TD-067, etapa B) ----------

    val coachEmail: String get() = auth.email

    /** [onDone] recibe null si entró, o el motivo. */
    fun coachSignIn(email: String, password: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { auth.signIn(email, password) }
            isCoach = auth.isCoach
            onDone(error)
        }
    }

    /** Cerrar sesión no toca lo asignado: solo retira el permiso de este dispositivo. */
    fun coachSignOut() {
        auth.signOut()
        isCoach = auth.isCoach
    }

    fun createProfile(name: String, onDone: (String?) -> Unit) = write(onDone) {
        assignments.createProfile(name)
    }

    fun renameProfile(id: String, name: String, onDone: (String?) -> Unit) = write(onDone) {
        assignments.renameProfile(id, name)
    }

    fun deleteProfile(id: String, onDone: (String?) -> Unit) = write(onDone) {
        assignments.deleteProfile(id)
    }

    /** Lo que tiene asignado alguien, leido del servidor. Null si no se pudo leer (TD-134). */
    fun loadAssignedTo(profileId: String, onDone: (List<Training>?) -> Unit) {
        viewModelScope.launch {
            onDone(withContext(Dispatchers.IO) { assignments.assignedTrainings(profileId) })
        }
    }

    /** Quita un training a una persona, lo tenga o no este telefono (TD-134). */
    fun unassign(profileId: String, trainingUid: String, onDone: (String?) -> Unit) = write(onDone) {
        assignments.unassign(profileId, trainingUid)
    }

    /**
     * Borra un training, y si el coach lo tenia repartido, se lo quita antes a todos (TD-134).
     *
     * Borrar solo en el telefono dejaba la asignacion viva en el servidor: el atleta lo
     * seguia recibiendo y el coach ya no tenia donde quitarselo. Asi se quedo "VIDEO" en el
     * telefono de NIKO desde el 6-sep.
     *
     * Si no se puede saber quien lo tiene -sin red-, NO se borra: borrarlo a ciegas es
     * justo como nacen las huerfanas. Sin sesion de entrenador no se consulta: un telefono
     * de atleta no reparte nada.
     */
    fun deleteTrainingEverywhere(id: Long, onDone: (String?) -> Unit) {
        val tr = trainings.firstOrNull { it.id == id } ?: return onDone(null)
        if (!isCoach || tr.uid.isBlank() || tr.assigned) {
            deleteTraining(id)
            return onDone(null)
        }
        viewModelScope.launch {
            val quienes = withContext(Dispatchers.IO) { assignments.profilesWith(tr.uid) }
            when {
                quienes == null -> onDone(I18n.get().cantCheckAssignees)
                quienes.isEmpty() -> {
                    deleteTraining(id)
                    onDone(null)
                }
                else -> {
                    val error = withContext(Dispatchers.IO) { assignments.setAssignees(tr, emptySet()) }
                    isCoach = auth.isCoach
                    if (error == null) {
                        deleteTraining(id)
                        runSync {}
                    }
                    onDone(error)
                }
            }
        }
    }

    /** Cuántos trainings tiene asignados alguien, para avisarlo antes de borrarlo. */
    fun countAssignments(profileId: String, onDone: (Int?) -> Unit) {
        viewModelScope.launch {
            onDone(withContext(Dispatchers.IO) { assignments.assignmentCount(profileId) })
        }
    }

    /** Quién tiene ya este training, para marcarlo al abrir el diálogo de asignar. */
    fun loadAssignees(trainingUid: String, onDone: (List<String>?) -> Unit) {
        viewModelScope.launch {
            onDone(withContext(Dispatchers.IO) { assignments.profilesWith(trainingUid) })
        }
    }

    /**
     * Deja el training en manos exactamente de [profileIds], y sincroniza al terminar.
     *
     * La sincronización es lo que hace visible el efecto en ESTE teléfono cuando el
     * entrenador se asigna algo a sí mismo; en los demás llega en su siguiente arranque.
     */
    fun setAssignees(trainingId: Long, profileIds: Set<String>, onDone: (String?) -> Unit) {
        val training = trainings.firstOrNull { it.id == trainingId } ?: return onDone("Training not found")
        write({ error ->
            // Asignar publica el contenido: es el punto de partida de la huella con la que
            // despues se decide si hay que republicar (TD-132).
            if (error == null) store.markPublished(training.uid, PublishSync.fingerprintOf(training))
            onDone(error)
        }) { assignments.setAssignees(training, profileIds) }
    }

    /**
     * Escritura de red: fuera del hilo principal y devolviendo el motivo si falló.
     *
     * Al terminar bien se resincroniza, porque casi cualquier escritura puede haber
     * cambiado lo asignado a este teléfono —borrar un perfil se lleva sus asignaciones en
     * cascada—, y esperar al siguiente arranque para verlo sería raro justo después de
     * haberlo hecho uno mismo.
     */
    private fun write(onDone: (String?) -> Unit, block: () -> String?) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { block() }
            // La sesión puede haber caducado durante la escritura; AuthStore la cierra al
            // saberlo, y la UI tiene que dejar de ofrecer lo que ya no se puede hacer.
            isCoach = auth.isCoach
            // runSync y no syncAssignments: el freno del automático se saltaría justo la
            // sincronización que el entrenador acaba de provocar a mano.
            if (error == null) runSync {}
            onDone(error)
        }
    }

    /** Si hay una sincronización en curso, para no lanzar dos a la vez. */
    var syncing by mutableStateOf(false)
        private set

    private var lastSyncAt = 0L

    /**
     * Sincronización automática: al arrancar y cada vez que el app vuelve a primer plano.
     *
     * Se salta si acaba de correr. Volver a primer plano ocurre decenas de veces al día
     * —cada vez que se desbloquea el teléfono—, y lo asignado cambia de semana en semana.
     * "Sincronizar ahora" no pasa por este freno: ese va a la red siempre.
     */
    fun syncAssignments() {
        if (syncing || System.currentTimeMillis() - lastSyncAt < AUTO_SYNC_MIN_MS) return
        runSync {}
    }

    /**
     * Refresco a mano: va a la red sin excusas y cuenta cómo fue.
     *
     * No mira [syncing] a propósito. Rebotar la petición dejaría a quien la pidió sin
     * respuesta —y al indicador de refresco girando para siempre—; repetir una lectura
     * pequeña es más barato que eso.
     */
    fun syncNow(onDone: (SyncResult) -> Unit) = runSync(onDone)

    /**
     * Trae los trainings asignados y los aplica.
     *
     * Si no hay perfil o no se pudo leer la asignación **no se toca nada**: un fallo de
     * red no puede parecerse a "ya no te toca ninguno", que sí retira trainings. Y un
     * fallo tampoco cuenta como sincronización hecha, para que se reintente en la
     * siguiente vuelta a primer plano en vez de esperar al freno.
     */
    private fun runSync(onDone: (SyncResult) -> Unit) {
        val id = assignments.profileId ?: return onDone(SyncResult.NoProfile)
        syncing = true
        // Se marca antes de salir a la red, no al volver: el arranque y la primera vuelta
        // a primer plano llegan casi juntos, y así el segundo ve que el primero ya va.
        lastSyncAt = System.currentTimeMillis()
        viewModelScope.launch {
            val incoming = withContext(Dispatchers.IO) { assignments.assignedTrainings(id) }
            syncing = false
            if (incoming == null) {
                lastSyncAt = 0L
                return@launch onDone(SyncResult.Failed)
            }
            val merged = mergeAssigned(trainings.toList(), incoming, ::newId)
            val changed = merged != trainings.toList()
            if (changed) {
                trainings.clear()
                trainings.addAll(merged)
                persist()
            }
            // Cada vuelta a primer plano sincroniza asignaciones; es tambien el momento de
            // reintentar lo que no subio y de bajar lo nuevo de los atletas.
            syncSessions()
            republishChanged()
            syncMedia()
            onDone(SyncResult.Ok(assigned = incoming.size, changed = changed))
        }
    }

    // ---------- Como se sintio la sesion (TD-089) ----------

    /**
     * El dolor con el que empezo, contestado ANTES de darle a empezar (TD-089 nivel 2).
     *
     * Vive aqui y no en la sesion porque la sesion todavia no existe: la escribe el servicio
     * al terminar. Se vuelca en cuanto aparece y se limpia.
     *
     * Preguntarlo al empezar y no al final era el punto entero: "mientras mas inmediata la
     * pregunta, mas pegada a la realidad sera la respuesta". Al terminar, el numero de antes
     * ya es un recuerdo de hace una hora.
     */
    var pendingPainBefore by mutableStateOf<Int?>(null)
        private set

    fun setPainBefore(n: Int) {
        pendingPainBefore = n
    }

    /** true si al training que se acaba de correr hay que preguntarle como se sintio. */
    fun asksHowItWent(): Boolean = trainings.firstOrNull { it.id == playerTrainingId }?.tracksPain == true

    /** Lo ya contestado de la sesion recien terminada, para que la pantalla lo ensene marcado. */
    fun lastSessionFeedback(): SessionLog? = lastSessionIndex()?.let { sessions[it] }

    /**
     * Anota cómo se sintió la sesión recién terminada.
     *
     * Se guarda **en cuanto toca cada cosa**, sin botón de enviar: el usuario pidió que el
     * registro fuera en caliente porque "después se vuelve un ejercicio de memoria y
     * muchas veces falla", y un formulario que hay que confirmar es una ocasión más de
     * olvidarse. Media respuesta guardada vale más que una completa que no llegó.
     *
     * Solo toca la última sesión de ESE training y solo si es de hace menos de una hora:
     * la escribió el servicio hace un momento, y sin ese límite una pantalla vieja podría
     * anotar sobre la sesión equivocada.
     */
    fun saveHowItWent(
        painBefore: Int? = null,
        painAfter: Int? = null,
        radiating: Boolean? = null,
        note: String? = null,
        painOnWaking: Int? = null,
        painFadeMin: Int? = null,
    ) {
        val i = lastSessionIndex() ?: return
        val s = sessions[i]
        val nueva = s.copy(
            painBefore = painBefore ?: s.painBefore,
            painAfter = painAfter ?: s.painAfter,
            radiating = radiating ?: s.radiating,
            note = note ?: s.note,
            painOnWaking = painOnWaking ?: s.painOnWaking,
            painFadeMin = painFadeMin ?: s.painFadeMin,
        )
        if (nueva == s) return
        sessions[i] = nueva
        store.saveSessions(sessions.toList())
        snapshot()
        syncSessions()
    }

    private fun lastSessionIndex(): Int? {
        val id = playerTrainingId ?: return null
        val i = sessions.indexOfFirst { it.trainingId == id }
        if (i < 0) return null
        val edadMs = System.currentTimeMillis() - sessions[i].completedAt
        return if (edadMs in 0..3_600_000) i else null
    }

    // ---------- Respaldo: export / import ----------

    /** Contenido del archivo de respaldo (trainings + ejercicios propios + historial). */
    fun exportData(): String = store.exportJson()

    /**
     * Reemplaza todos los datos con los del respaldo y refresca la UI.
     * Devuelve null si el archivo no era un respaldo válido, sin haber tocado nada.
     */
    fun importData(json: String): ImportResult? {
        // Snapshot ANTES de tocar nada, y en este hilo: el import es la única operación
        // que borra todos los datos a propósito, así que la copia tiene que existir
        // cuando empiece, no cuando termine una corrutina. Es un archivo pequeño.
        val backedUp = autoBackup.writeBeforeImport(store.exportJson())
        val summary = store.importJson(json) ?: return null
        reload()
        refreshSessions()
        exerciseMedia.clear()
        exerciseMedia.putAll(mediaStore.load())
        return ImportResult(summary, backedUp)
    }

    // MASTER es English-only (decisión de producto): el idioma del catálogo y de
    // los defaults es siempre inglés, sin depender de ajustes.
    private fun lang(): String = "en"

    /** Preferencia de reloj del player: ceros a la izquierda ("00:30" vs "30"). */
    fun padPlayerClock(): Boolean =
        SettingsStore(getApplication()).loadConfig().masterConfig.padPlayerClock

    // ---------- Catálogo de ejercicios ----------

    fun catalog(): List<ExerciseDef> {
        val l = lang()
        return (customExercises.toList() + ExerciseCatalog.base(l)).sortedBy { it.name.lowercase() }
    }

    fun addCustomExercise(name: String): ExerciseDef {
        val def = ExerciseDef(id = "custom_${newId()}", name = name.trim(), custom = true)
        customExercises.add(def)
        store.saveCustomExercises(customExercises.toList())
        return def
    }

    // ---------- Navegación / drafts ----------

    /** Training en edición; null = lista de trainings. */
    var draft by mutableStateOf<Training?>(null)
        private set

    /** Workout abierto dentro del draft (editor de workout). */
    var editingWorkoutId by mutableStateOf<Long?>(null)
        private set

    /** Ejercicio abierto dentro del workout (editor de ejercicio). */
    var editingExerciseId by mutableStateOf<Long?>(null)
        private set

    /** Variante abierta dentro de un workout rotativo (editor de variante). */
    var editingVariantId by mutableStateOf<Long?>(null)
        private set

    /** Selector de ejercicios abierto (añade al contenedor en edición). */
    var choosingExercise by mutableStateOf(false)
        private set

    /** Selector de workouts existentes abierto (copia uno de otro training al draft). */
    var choosingWorkout by mutableStateOf(false)
        private set

    fun editingWorkout(): Workout? =
        draft?.workouts?.firstOrNull { it.id == editingWorkoutId }

    /** Variante actualmente en edición (o null si se edita el workout simple). */
    fun editingVariant(): WorkoutVariant? =
        editingVariantId?.let { vId -> editingWorkout()?.variants?.firstOrNull { it.id == vId } }

    /** Lista de ejercicios del contenedor en edición (variante si hay, si no workout). */
    fun editorExercises(): List<Exercise> {
        val w = editingWorkout() ?: return emptyList()
        val vId = editingVariantId ?: return w.exercises
        return w.variants.firstOrNull { it.id == vId }?.exercises ?: emptyList()
    }

    /** Nombre del contenedor en edición (variante si hay, si no workout). */
    fun editorName(): String {
        val w = editingWorkout() ?: return ""
        val vId = editingVariantId ?: return w.name
        return w.variants.firstOrNull { it.id == vId }?.name ?: ""
    }

    fun editingExercise(): Exercise? =
        editorExercises().firstOrNull { it.id == editingExerciseId }

    private fun updateDraft(transform: (Training) -> Training) {
        draft = draft?.let(transform)
    }

    private fun updateWorkout(id: Long, transform: (Workout) -> Workout) = updateDraft { t ->
        t.copy(workouts = t.workouts.map { if (it.id == id) transform(it) else it })
    }

    // ---------- Historial de sesiones ----------

    /** Pantalla de historial abierta desde la raíz. */
    var showingHistory by mutableStateOf(false)
        private set

    /** Ejercicio seleccionado para ver su historial (navegación desde HistoryScreen). */
    var exerciseHistoryId by mutableStateOf<String?>(null)
        private set

    fun openHistory() {
        // Se recargan al abrir porque las escribe el servicio (otro contexto) al terminar.
        refreshSessions()
        showingHistory = true
    }

    fun closeHistory() {
        showingHistory = false
        exerciseHistoryId = null
    }

    fun openExerciseHistory(exerciseId: String) {
        exerciseHistoryId = exerciseId
    }

    fun closeExerciseHistory() {
        exerciseHistoryId = null
    }

    /** Sesiones que contienen un ejercicio específico (para ExerciseHistoryScreen). */
    fun sessionsForExercise(exerciseId: String): List<Pair<SessionLog, ExerciseRecord>> =
        sessions.mapNotNull { s ->
            val er = s.exercises.firstOrNull { it.exerciseId == exerciseId }
            if (er != null) s to er else null
        }

    private fun refreshSessions() {
        sessions.clear()
        sessions.addAll(store.loadSessions().sortedByDescending { it.completedAt })
        // Se llama al arrancar y al terminar una sesion: los dos momentos en que puede haber
        // algo nuevo que subir.
        syncSessions()
    }

    /**
     * Sube las sesiones pendientes de este telefono y, si es el del coach, baja las de sus
     * atletas (TD-126).
     *
     * Subir: solo las de trainings ASIGNADOS y solo las que cambiaron desde la ultima vez
     * -ver [SessionSync.pending]-, porque la sesion se sigue completando despues de
     * guardarse. Sin red se corta y se reintenta la proxima vez; nada se pierde, porque la
     * sesion ya esta en el telefono y la nube va despues.
     *
     * Bajar: van a un almacen APARTE y al respaldo, no al historial propio. Es por donde el
     * asistente las lee; mezclarlas con las del coach le contaria entrenamientos que no hizo.
     */
    private fun syncSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!sessionSyncLock.tryLock()) return@launch
            try {
                assignments.profileId?.let { perfil ->
                    val pendientes = SessionSync.pending(store.loadSessions(), store.loadTrainings(), store.uploadLedger())
                    for (p in pendientes) {
                        // true se guardo, false no era un training asignado: en los dos casos
                        // queda anotada y no se reintenta hasta que cambie. Null es la red.
                        assignments.uploadSession(perfil, p) ?: break
                        store.markUploaded(p.session.id, p.fingerprint)
                    }
                }
                if (auth.isCoach) {
                    assignments.athleteSessions()?.let { bajadas ->
                        if (bajadas != store.loadAthleteSessions()) {
                            store.saveAthleteSessions(bajadas)
                            withContext(Dispatchers.Main) { snapshot() }
                        }
                    }
                }
            } finally {
                sessionSyncLock.unlock()
            }
        }
    }

    // Borrar escribe el respaldo, igual que guardar (TD-121). Antes no, y el respaldo se
    // quedaba con sesiones que ya no existian: el 19-sep el coach leyo ahi una sesion de
    // prueba que el usuario habia borrado y le dijo que su telefono la tenia. Los respaldos
    // anteriores se conservan, asi que un borrado por error se sigue pudiendo recuperar.
    fun deleteSession(id: Long) {
        sessions.removeAll { it.id == id }
        store.saveSessions(sessions.toList())
        snapshot()
    }

    fun clearHistory() {
        sessions.clear()
        store.saveSessions(emptyList())
        snapshot()
    }

    // ---------- Lista de Trainings ----------

    fun startNewTraining() {
        val now = System.currentTimeMillis()
        draft = Training(id = newId(), uid = store.newUid(), name = "", createdAt = now, updatedAt = now)
        editingWorkoutId = null
        editingExerciseId = null
        choosingExercise = false
        choosingWorkout = false
    }

    fun startEditTraining(id: Long) {
        draft = trainings.firstOrNull { it.id == id }?.copy() ?: return
        editingWorkoutId = null
        editingVariantId = null
        editingExerciseId = null
        choosingExercise = false
        choosingWorkout = false
    }

    fun closeTrainingEditor() {
        draft = null
        editingWorkoutId = null
        editingVariantId = null
        editingExerciseId = null
        choosingExercise = false
        choosingWorkout = false
    }

    val canSaveTraining: Boolean
        get() = draft?.let { it.name.isNotBlank() && it.workouts.any { w -> w.hasContent() } } == true

    fun saveTraining() {
        val d = draft ?: return
        if (!canSaveTraining) return
        val updated = d.copy(updatedAt = System.currentTimeMillis())
        val i = trainings.indexOfFirst { it.id == updated.id }
        if (i >= 0) trainings[i] = updated else trainings.add(updated)
        persist()
        applyToRunningPlayer(updated)
        closeTrainingEditor()
    }

    /**
     * Si lo que se acaba de guardar es el training que está corriendo, rehace su cola.
     *
     * Es el único disparador, y cubre los dos caminos: el atajo desde el player y editar
     * desde la lista con el player minimizado, que ya se podía hacer y simplemente no
     * tenía efecto.
     *
     * **El paso en curso se conserva tal cual**: si estás en la serie 15 de 30s y subes el
     * tiempo a 40, esa serie termina siendo de 30 y el cambio entra en la siguiente. Así el
     * reloj no salta bajo los pies y lo que se registre para esa serie es lo que de verdad
     * se hizo. Solo se conserva si la casilla sigue existiendo: si esa serie desapareció,
     * [StepEngine.relocate] deja el índice en el primer paso posterior y ahí manda el paso
     * nuevo, no el viejo.
     */
    private fun applyToRunningPlayer(updated: Training) {
        if (activePlayerTrainingId != updated.id) return
        val current = playerStep ?: return
        val rebuilt = StepEngine.buildSteps(updated)
        if (rebuilt.isEmpty()) return
        val at = StepEngine.relocate(current, rebuilt)
        val steps = if (StepEngine.sameSlot(rebuilt[at], current)) {
            rebuilt.toMutableList().also { it[at] = current }
        } else {
            rebuilt
        }
        playerSteps = steps
        playerTotalSteps = steps.size
        WorkoutPlayerService.update(getApplication(), steps, at)
    }

    fun deleteTraining(id: Long) {
        trainings.removeAll { it.id == id }
        persist()
    }

    fun moveTraining(from: Int, to: Int) {
        if (from == to || from !in trainings.indices || to !in trainings.indices) return
        trainings.add(to, trainings.removeAt(from))
        persist()
    }

    /**
     * Reordenar arrastrando sobre la lista, donde lo archivado no aparece: las posiciones que
     * llegan son las de lo visible y hay que traducirlas (TD-138).
     */
    fun moveVisibleTraining(from: Int, to: Int) {
        val reales = Archive.visibleIndices(trainings, archivedUids)
        moveTraining(reales.getOrNull(from) ?: return, reales.getOrNull(to) ?: return)
    }

    fun duplicateTraining(id: Long) {
        val src = trainings.firstOrNull { it.id == id } ?: return
        val copy = src.duplicate(
            newId = ::newId,
            newUid = store::newUid,
            name = duplicateName(src.name),
            now = System.currentTimeMillis(),
        )
        val i = trainings.indexOfFirst { it.id == id }
        trainings.add(i + 1, copy)
        persist()
    }

    private fun duplicateName(name: String): String = if (name.isBlank()) name else "$name (copy)"

    // ---------- Editor de Training (workouts) ----------

    fun setTrainingName(name: String) = updateDraft { it.copy(name = name) }

    fun addWorkout() {
        val id = newId()
        updateDraft { it.copy(workouts = it.workouts + Workout(id = id, name = "")) }
        editingWorkoutId = id
    }

    // ---------- Reutilizar un workout de otro training ----------

    /**
     * Trainings que pueden aportar workouts, con los workouts que ofrecen. Ordenados por
     * uso real (historial), no por edición: la lista acumula trainings de prueba que nunca
     * se ejercitaron y taparían al que de verdad se usa. Ver [sortedByLastTrained].
     *
     * Excluye el training en edición —sus workouts ya están en pantalla, y duplicarlos ahí
     * es lo que hace `duplicateWorkout`— y los workouts vacíos, que no aportan nada.
     */
    fun workoutPickerSources(): List<Pair<Training, List<Workout>>> {
        val draftId = draft?.id
        return trainings.sortedByLastTrained(sessions)
            .filter { it.id != draftId }
            .map { t -> t to t.workouts.filter { it.hasContent() } }
            .filter { (_, workouts) -> workouts.isNotEmpty() }
    }

    /** Fecha de la última sesión de cada training, para el encabezado del selector. */
    fun lastTrainedByTraining(): Map<Long, Long> = lastTrainedAt(sessions)

    fun openWorkoutPicker() {
        choosingWorkout = true
    }

    fun closeWorkoutPicker() {
        choosingWorkout = false
    }

    /**
     * Copia un workout de otro training al final del draft y abre su editor.
     * La copia es profunda (ids nuevos), así que editarla no toca el original; el nombre
     * se conserva sin sufijo porque viene de otro training y no colisiona.
     */
    fun pickWorkout(workoutId: Long) {
        val src = trainings
            .firstNotNullOfOrNull { t -> t.workouts.firstOrNull { it.id == workoutId } } ?: return
        val copy = src.deepCopy(::newId)
        updateDraft { it.copy(workouts = it.workouts + copy) }
        choosingWorkout = false
        editingWorkoutId = copy.id
    }

    fun openWorkout(id: Long) {
        editingWorkoutId = id
        editingVariantId = null
    }

    fun closeWorkoutEditor() {
        editingWorkoutId = null
        editingVariantId = null
    }

    // ---------- Workouts rotativos / variantes ----------

    /** Convierte un workout simple en rotativo: mueve sus ejercicios a una variante. */
    fun makeWorkoutRotating(id: Long) = updateWorkout(id) { w ->
        if (w.rotating) return@updateWorkout w
        val first = WorkoutVariant(
            id = newId(),
            name = w.name.ifBlank { "A" },
            exercises = w.exercises,
        )
        w.copy(rotating = true, rotationIndex = 0, variants = listOf(first), exercises = emptyList())
    }

    /** Vuelve simple un workout rotativo: conserva los ejercicios de la 1ª variante. */
    fun makeWorkoutSimple(id: Long) = updateWorkout(id) { w ->
        if (!w.rotating) return@updateWorkout w
        w.copy(
            rotating = false,
            rotationIndex = 0,
            exercises = w.variants.firstOrNull()?.exercises ?: w.exercises,
            variants = emptyList(),
        )
    }

    fun addVariant() {
        val wId = editingWorkoutId ?: return
        val id = newId()
        updateWorkout(wId) { it.copy(variants = it.variants + WorkoutVariant(id = id, name = "")) }
        editingVariantId = id
    }

    fun openVariant(id: Long) {
        editingVariantId = id
    }

    fun closeVariantEditor() {
        editingVariantId = null
    }

    fun deleteVariant(id: Long) {
        val wId = editingWorkoutId ?: return
        updateWorkout(wId) { w -> w.copy(variants = w.variants.filterNot { it.id == id }) }
    }

    fun duplicateVariant(id: Long) {
        val wId = editingWorkoutId ?: return
        updateWorkout(wId) { w ->
            val i = w.variants.indexOfFirst { it.id == id }
            if (i < 0) return@updateWorkout w
            val src = w.variants[i]
            val copy = src.copy(
                id = newId(),
                name = duplicateName(src.name),
                exercises = src.exercises.map { it.copy(id = newId()) },
            )
            w.copy(variants = w.variants.toMutableList().apply { add(i + 1, copy) })
        }
    }

    fun moveVariant(from: Int, to: Int) {
        val wId = editingWorkoutId ?: return
        updateWorkout(wId) { w ->
            if (from == to || from !in w.variants.indices || to !in w.variants.indices) return@updateWorkout w
            w.copy(variants = w.variants.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun deleteWorkout(id: Long) = updateDraft { t ->
        t.copy(workouts = t.workouts.filterNot { it.id == id })
    }

    fun duplicateWorkout(id: Long) = updateDraft { t ->
        val i = t.workouts.indexOfFirst { it.id == id }
        if (i < 0) return@updateDraft t
        val src = t.workouts[i]
        val copy = src.deepCopy(::newId).copy(name = duplicateName(src.name))
        t.copy(workouts = t.workouts.toMutableList().apply { add(i + 1, copy) })
    }

    fun moveWorkout(from: Int, to: Int) = updateDraft { t ->
        if (from == to || from !in t.workouts.indices || to !in t.workouts.indices) return@updateDraft t
        t.copy(workouts = t.workouts.toMutableList().apply { add(to, removeAt(from)) })
    }

    // ---------- Editor de Workout (exercises) ----------

    /** Renombra el contenedor en edición: variante si hay una abierta, si no el workout. */
    fun setEditorName(name: String) {
        val wId = editingWorkoutId ?: return
        val vId = editingVariantId
        if (vId == null) {
            updateWorkout(wId) { it.copy(name = name) }
        } else {
            updateWorkout(wId) { w ->
                w.copy(variants = w.variants.map { if (it.id == vId) it.copy(name = name) else it })
            }
        }
    }

    /** Aplica una transformación a la lista de ejercicios del contenedor en edición. */
    private fun updateEditorExercises(transform: (List<Exercise>) -> List<Exercise>) {
        val wId = editingWorkoutId ?: return
        val vId = editingVariantId
        if (vId == null) {
            updateWorkout(wId) { it.copy(exercises = transform(it.exercises)) }
        } else {
            updateWorkout(wId) { w ->
                w.copy(variants = w.variants.map { if (it.id == vId) it.copy(exercises = transform(it.exercises)) else it })
            }
        }
    }

    fun openExercisePicker() {
        choosingExercise = true
    }

    fun closeExercisePicker() {
        choosingExercise = false
    }

    /** Crea un ejercicio por defecto desde el catálogo y abre su editor. */
    fun pickExercise(def: ExerciseDef) {
        if (editingWorkoutId == null) return
        val id = newId()
        val ex = Exercise(id = id, exerciseId = def.id, name = def.name)
        updateEditorExercises { it + ex }
        choosingExercise = false
        editingExerciseId = id
    }

    fun openExercise(id: Long) {
        editingExerciseId = id
    }

    fun closeExerciseEditor() {
        editingExerciseId = null
    }

    fun deleteExercise(id: Long) = updateEditorExercises { list -> list.filterNot { it.id == id } }

    fun duplicateExercise(id: Long) = updateEditorExercises { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) list else list.toMutableList().apply { add(i + 1, list[i].copy(id = newId())) }
    }

    fun moveExercise(from: Int, to: Int) = updateEditorExercises { list ->
        if (from == to || from !in list.indices || to !in list.indices) list
        else list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Persiste los cambios del editor de ejercicio en el draft. */
    fun saveExercise(updated: Exercise) {
        updateEditorExercises { list -> list.map { if (it.id == updated.id) updated else it } }
        editingExerciseId = null
    }

    /** Aplica un color a una etapa (kind) en todos los ejercicios del training. */
    fun applyColorToTraining(kind: StepKind, color: Long) {
        val d = draft ?: return
        updateDraft { t ->
            t.copy(workouts = t.workouts.map { w ->
                w.copy(
                    exercises = w.exercises.map { e -> e.withStageColor(kind, color) },
                    variants = w.variants.map { v ->
                        v.copy(exercises = v.exercises.map { e -> e.withStageColor(kind, color) })
                    },
                )
            })
        }
    }

    // ---------- Player helpers ----------

    fun showPlayerControls() {
        playerControlsVisible = true
    }

    fun hidePlayerControls() {
        playerControlsVisible = false
    }

    fun togglePlayerControls() {
        if (playerControlsVisible) hidePlayerControls() else showPlayerControls()
    }

    /**
     * Feedback de peso recogido durante el run, POR SERIE (TD-117). La clave lleva la serie
     * para que el chip marcado en la serie 2 sea el de la serie 2: antes era uno por
     * ejercicio, y al empezar una serie nueva aparecía marcado lo que se eligió en la otra.
     */
    val weightFeedback = mutableStateMapOf<String, SetFeedback>()

    fun feedbackKey(exerciseId: String, workoutIndex: Int, setIndex: Int) = "$exerciseId:$workoutIndex:$setIndex"

    fun recordFeedback(exerciseId: String, workoutIndex: Int, setIndex: Int, name: String, weight: Double, deltaKg: Double) {
        weightFeedback[feedbackKey(exerciseId, workoutIndex, setIndex)] =
            SetFeedback(name, "$exerciseId:$workoutIndex", setIndex, weight, deltaKg)
        sendFeedback(exerciseId, workoutIndex, setIndex, deltaKg)
    }

    /**
     * Las series con peso de esta corrida que se quedaron SIN marcar (TD-122).
     *
     * Se leen de los pasos, no del registro: son las mismas series, y así la pantalla de
     * resumen puede ofrecer completarlas aunque la sesión ya esté guardada.
     */
    fun unmarkedWeightSets(): List<PlayerStep> =
        playerSteps.filter {
            it.kind == StepKind.WORK && it.weighted &&
                weightFeedback[feedbackKey(it.ownerExerciseId, it.workoutIndex, it.setIndex)] == null
        }

    /**
     * Marca una serie cuando la sesión YA se guardó: al terminar, desde el resumen.
     *
     * El recorder del servicio ya no existe a esas alturas, así que esto escribe directo en
     * el registro guardado: la última sesión, que es la que se acaba de cerrar.
     *
     * La sesión NO pasa a [SessionSource.EDITED]. Lo anota el propio usuario, minutos
     * después y sobre lo que acaba de hacer: sigue siendo lo medido, no la corrección de
     * otro sobre un registro ajeno.
     */
    fun markFinishedFeedback(step: PlayerStep, deltaKg: Double) {
        weightFeedback[feedbackKey(step.ownerExerciseId, step.workoutIndex, step.setIndex)] =
            SetFeedback(step.ownerName, "${step.ownerExerciseId}:${step.workoutIndex}", step.setIndex, step.weightTotal, deltaKg)
        val sesion = sessions.firstOrNull() ?: return
        val ejercicios = sesion.exercises.map { er ->
            if (er.exerciseId != step.ownerExerciseId || er.workoutIndex != step.workoutIndex ||
                step.setIndex !in er.sets.indices
            ) {
                er
            } else {
                val sets = er.sets.mapIndexed { i, set ->
                    if (i == step.setIndex) set.copy(feedbackDeltaKg = deltaKg) else set
                }
                er.copy(sets = sets, feedbackDeltaKg = sets.lastOrNull { it.feedbackDeltaKg != null }?.feedbackDeltaKg)
            }
        }
        if (ejercicios == sesion.exercises) return
        sessions[0] = sesion.copy(exercises = ejercicios)
        store.saveSessions(sessions.toList())
        snapshot()
        syncSessions()
    }

    /**
     * Sugerencias de ajuste para la próxima vez (solo las que cambian).
     *
     * Una por ejercicio, la de su ÚLTIMA serie marcada: en una pirámide es la serie de
     * arriba, que es la que decide si el ejercicio sube.
     */
    fun weightSuggestions(): List<Triple<String, Double, Double>> =
        weightFeedback.values
            .groupBy { it.exerciseKey }
            .map { (_, marcadas) -> marcadas.maxBy { it.setIndex } }
            .filter { it.deltaKg != 0.0 }
            .map { Triple(it.name, it.weight, it.weight + it.deltaKg) }

    /** Evita recargar los índices de rotación más de una vez por corrida (finished puede repetir). */
    private var sessionReloaded = false
    private var pendingSessionRefresh = false

    fun openPlayer(trainingId: Long) {
        // Si hay un player activo para este training, reconectar (estilo YouTube).
        val prefs = getApplication<Application>()
            .getSharedPreferences("master_restore", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("active", false) && prefs.getLong("workoutId", 0L) == trainingId) {
            restorePlayerState()
            return
        }
        val t = trainings.firstOrNull { it.id == trainingId } ?: return
        val steps = StepEngine.buildSteps(t)
        if (steps.isEmpty()) return
        PlayerBus.state.value = null
        sessionReloaded = false
        weightFeedback.clear()
        playerSteps = steps
        playerTrainingId = trainingId
        playerName = t.name
        playerStarted = false
        playerFinished = false
        playerRunning = false
        playerIndex = 0
        playerStep = steps[0]
        playerTotalSteps = steps.size
        playerRemainingMs = steps[0].durationSec * 1000L
    }

    fun startPlayerRun() {
        val id = playerTrainingId ?: return
        if (playerSteps.isEmpty()) return
        playerStarted = true
        WorkoutPlayerService.start(getApplication(), id, playerName, playerSteps)
        activePlayerTrainingId = id
    }

    fun pausePlayer() = PlayerBus.command.tryEmit(PlayerCommand.PAUSE)
    fun resumePlayer() = PlayerBus.command.tryEmit(PlayerCommand.RESUME)
    fun checkStep() = PlayerBus.command.tryEmit(PlayerCommand.NEXT)
    fun skipStep() = PlayerBus.command.tryEmit(PlayerCommand.SKIP_STEP)
    fun skipExercise() = PlayerBus.command.tryEmit(PlayerCommand.SKIP_EXERCISE)
    fun prevStep() = PlayerBus.command.tryEmit(PlayerCommand.PREV)
    fun sendFeedback(exerciseId: String, workoutIndex: Int, setIndex: Int, deltaKg: Double) =
        PlayerBus.command.tryEmit(PlayerCommand.FEEDBACK(exerciseId, workoutIndex, setIndex, deltaKg))

    /**
     * La velocidad a la que va de verdad cada serie (TD-124).
     *
     * Arranca en la prescrita y el usuario la sube o la baja en el player. Se guarda aquí
     * para que la tarjeta enseñe lo elegido aunque el paso se recomponga, y se manda al
     * servicio, que es quien escribe el registro.
     */
    val setSpeed = mutableStateMapOf<String, Double>()

    fun speedOf(step: PlayerStep): Double? =
        setSpeed[feedbackKey(step.ownerExerciseId, step.workoutIndex, step.setIndex)] ?: step.speedKmh

    fun recordSpeed(step: PlayerStep, kmh: Double) {
        val v = kmh.coerceIn(SPEED_MIN, SPEED_MAX)
        setSpeed[feedbackKey(step.ownerExerciseId, step.workoutIndex, step.setIndex)] = v
        PlayerBus.command.tryEmit(
            PlayerCommand.SPEED(step.ownerExerciseId, step.workoutIndex, step.setIndex, v),
        )
    }

    fun closePlayer() {
        pendingSessionRefresh = true
        WorkoutPlayerService.stop(getApplication())
        playerTrainingId = null
        playerSteps = emptyList()
        playerStarted = false
        playerRunning = false
        playerFinished = false
        playerStep = null
        activePlayerTrainingId = null
        reload()
    }

    /**
     * Abre el editor en el ejercicio que se está haciendo, con la corrida en marcha.
     *
     * Minimiza en vez de cerrar: el servicio es independiente de la pantalla, así que el
     * reloj sigue corriendo mientras se edita. Al guardar, [applyToRunningPlayer] rehace la
     * cola desde el paso siguiente.
     *
     * Un training asignado no se edita —ni parado ni corriendo—, así que aquí no se ofrece.
     */
    fun editRunningExercise(step: PlayerStep) {
        val id = playerTrainingId ?: activePlayerTrainingId ?: return
        val training = trainings.firstOrNull { it.id == id } ?: return
        if (training.assigned) return

        minimizePlayer()
        startEditTraining(id)
        val workout = draft?.workouts?.getOrNull(step.workoutIndex) ?: return
        openWorkout(workout.id)
        // En un workout rotativo hay que entrar antes en la variante que se está corriendo:
        // si no, el editor se queda en la lista de variantes y no en el ejercicio.
        if (workout.rotating) workout.activeVariant()?.let { openVariant(it.id) }
        workout.activeExercises().getOrNull(step.exerciseIndex)?.let { openExercise(it.id) }
    }

    /** Vuelve a la lista de trainings sin detener ni pausar el player (estilo YouTube). */
    fun minimizePlayer() {
        playerStarted = false
        playerTrainingId = null
    }

    private fun refreshActivePlayerId() {
        val prefs = getApplication<Application>()
            .getSharedPreferences("master_restore", android.content.Context.MODE_PRIVATE)
        activePlayerTrainingId = if (prefs.getBoolean("active", false))
            prefs.getLong("workoutId", 0L).takeIf { it != 0L } else null
    }

    private fun observePlayer() {
        viewModelScope.launch {
            PlayerBus.state.collect { snap ->
                if (snap == null) {
                    val hadPlayer = playerStep != null || activePlayerTrainingId != null
                    playerStep = null
                    playerRunning = false
                    playerRemainingMs = 0L
                    activePlayerTrainingId = null
                    playerTrainingId = null
                    playerStarted = false
                    playerFinished = false
                    if (hadPlayer || pendingSessionRefresh) {
                        refreshSessions()
                        pendingSessionRefresh = false
                        // La sesion recien registrada por el servicio es justo lo que
                        // mas duele perder: respaldarla en cuanto aparece.
                        snapshot()
                    }
                    return@collect
                }
                playerIndex = snap.index
                playerTotalSteps = snap.totalSteps
                playerRemainingMs = snap.remainingMs
                playerRunning = snap.running
                playerFinished = snap.finished
                playerName = snap.name
                playerStep = playerSteps.getOrNull(snap.index) ?: PlayerStep(
                    kind = snap.stepKind,
                    title = snap.stepTitle,
                    note = snap.note,
                    ownerName = snap.ownerName,
                    ownerExerciseId = snap.ownerExerciseId,
                    exerciseIndex = snap.exerciseIndex,
                    showVideo = snap.showVideo,
                    workoutName = snap.workoutName,
                    workoutIndex = snap.workoutIndex,
                    totalWorkouts = snap.totalWorkouts,
                    setIndex = snap.setIndex,
                    totalSets = snap.totalSets,
                    reps = snap.reps,
                    timeBased = snap.timeBased,
                    display = snap.display,
                    finalCount = snap.finalCount,
                    colorArgb = snap.colorArgb,
                    weighted = snap.weighted,
                    weightTotal = snap.weightTotal,
                    weightLabel = snap.weightLabel,
                )
                if (snap.finished) {
                    playerRunning = false
                    // La rotación por-workout la avanza el servicio; recargar para reflejarla.
                    if (!sessionReloaded) {
                        sessionReloaded = true
                        reload()
                        refreshSessions()
                        // Ahora sí existe la sesión: el dolor de antes ya tiene dónde caer.
                        pendingPainBefore?.let {
                            saveHowItWent(painBefore = it)
                            pendingPainBefore = null
                        }
                        snapshot()
                    }
                }
            }
        }
    }

    // ---------- Beep sound picker helpers ----------

    fun loadAlarmSounds(): List<AlarmSound> {
        val ctx = getApplication<Application>()
        val result = mutableListOf<AlarmSound>()
        try {
            val rm = android.media.RingtoneManager(ctx).apply { setType(android.media.RingtoneManager.TYPE_NOTIFICATION) }
            val cursor = rm.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(android.media.RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = rm.getRingtoneUri(cursor.position)
                if (title != null && uri != null) {
                    result.add(AlarmSound(title, uri.toString()))
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    /** El nivel de los pitidos elegido en Ajustes, de 0 a 1. */
    private fun beepVolume(): Float =
        SettingsStore(getApplication()).loadConfig().masterConfig.beepVolume / 100f

    // La vista previa del editor suena al nivel de Ajustes: antes iba fija al 100 %, y al
    // bajar el volumen se oiría una cosa en el editor y otra en el training.
    fun previewBeepTone(uri: String) = alarmPlayer.previewTone(uri, beepVolume())

    /**
     * Suena el pitido por defecto a [percent], para oír el nivel al elegirlo en Ajustes.
     *
     * Recibe el nivel en vez de leerlo de Ajustes porque se llama en el mismo toque que lo
     * guarda, y leerlo ahí podría devolver todavía el anterior.
     */
    fun previewBeepVolume(percent: Int) = alarmPlayer.previewTone(
        "android.resource://${getApplication<Application>().packageName}/${R.raw.beep_second}",
        percent / 100f,
    )
    fun stopBeepPreview() = alarmPlayer.stopPreview()

    override fun onCleared() {
        super.onCleared()
        alarmPlayer.stop()
        alarmPlayer.stopPreview()
    }

    private companion object {
        /** Cuánto tiene que pasar para que volver a primer plano vuelva a mirar. */
        const val AUTO_SYNC_MIN_MS = 60_000L

        /**
         * Los cinco ejercicios propios que el usuario creo a mano el 13-sep-2026 para la
         * rutina lumbar y nunca llego a colocar. "Rest" era el apano para meter los 30 s
         * entre bloques de la piramide, que ya no hace falta.
         */
        val LUMBAR_LEFTOVERS = setOf(
            "custom_1789324269985", // Cat Camel   -> lo cubre ex_cat_cow, que ademas tiene video
            "custom_1789324269987", // Hip Hinge   -> ex_hip_hinge
            "custom_1789324269989", // Curl Up     -> ex_curl_up
            "custom_1789324269991", // Rest        -> ya no hace falta
            "custom_1789324269998", // Side Plank  -> ex_side_plank_l / ex_side_plank_r
        )
    }
}

/** Lo marcado en la tarjeta del peso en una serie concreta. */
data class SetFeedback(
    val name: String,
    /** "exerciseId:workoutIndex": agrupa las series de un mismo ejercicio. */
    val exerciseKey: String,
    val setIndex: Int,
    val weight: Double,
    val deltaKg: Double,
)
