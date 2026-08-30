package com.maurozegarra.master

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maurozegarra.master.audio.AlarmPlayer
import com.maurozegarra.master.data.AutoBackup
import com.maurozegarra.master.data.ImportSummary
import com.maurozegarra.master.data.MasterDefaults
import com.maurozegarra.master.data.ExerciseCatalog
import com.maurozegarra.master.data.ExerciseMediaStore
import com.maurozegarra.master.data.SettingsStore
import com.maurozegarra.master.data.WorkoutStore
import com.maurozegarra.master.model.AlarmSound
import com.maurozegarra.master.model.ConfirmMode
import com.maurozegarra.master.model.DisplayMode
import com.maurozegarra.master.model.Exercise
import com.maurozegarra.master.model.ExerciseDef
import com.maurozegarra.master.model.ExerciseMedia
import com.maurozegarra.master.model.ExerciseRecord
import com.maurozegarra.master.model.PlayerStep
import com.maurozegarra.master.model.SessionLog
import com.maurozegarra.master.model.StepEngine
import com.maurozegarra.master.model.StepKind
import com.maurozegarra.master.model.Training
import com.maurozegarra.master.model.Workout
import com.maurozegarra.master.model.WorkoutVariant
import com.maurozegarra.master.model.deepCopy
import com.maurozegarra.master.model.hasContent
import com.maurozegarra.master.model.lastTrainedAt
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

    /** OSD: si los controles del player están visibles (se auto-ocultan tras un tap). */
    var playerControlsVisible by mutableStateOf(true)
        private set

    /** Se incrementa en cada interacción para re-armar el auto-ocultado del OSD. */
    var osdNonce by mutableStateOf(0)
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

    private fun newId(): Long = nextId++

    init {
        val firstRun = store.isFirstRun()
        trainings.addAll(store.loadTrainings())
        customExercises.addAll(store.loadCustomExercises())
        if (firstRun && trainings.isEmpty()) {
            trainings.add(MasterDefaults.masterTraining(lang()))
            trainings.add(MasterDefaults.frikiNikiTraining(lang()))
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
        }
        observePlayer()
        migrateRestorePrefs()
        restorePlayerState()
        refreshSessions()
        exerciseMedia.putAll(mediaStore.load())
        snapshotReady = true
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

    private fun persist() {
        store.saveTrainings(trainings.toList())
        snapshot()
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

    /** Uri reproducible del vídeo, o null si el ejercicio no tiene o falta el archivo. */
    fun videoUriFor(exerciseId: String): Uri? = mediaStore.videoUri(exerciseMedia[exerciseId])

    val canStoreVideos: Boolean get() = mediaStore.canStoreVideos

    /**
     * Copia el vídeo elegido a `Movies/MASTER/` y lo asocia al ejercicio.
     * [onDone] recibe false si no se pudo guardar.
     */
    fun assignVideo(exerciseId: String, source: () -> java.io.InputStream?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { mediaStore.importVideo(exerciseId, source) }
            if (name == null) {
                onDone(false)
                return@launch
            }
            updateMedia(exerciseId) { it.copy(videoFile = name) }
            onDone(true)
        }
    }

    fun removeVideo(exerciseId: String) {
        viewModelScope.launch {
            val current = exerciseMedia[exerciseId]
            withContext(Dispatchers.IO) { mediaStore.deleteVideo(current) }
            updateMedia(exerciseId) { it.copy(videoFile = "") }
        }
    }

    fun setInstructions(exerciseId: String, steps: List<String>) {
        updateMedia(exerciseId) { it.copy(instructions = steps.filter { s -> s.isNotBlank() }) }
    }

    private fun updateMedia(exerciseId: String, transform: (ExerciseMedia) -> ExerciseMedia) {
        val updated = transform(exerciseMedia[exerciseId] ?: ExerciseMedia())
        if (updated.isEmpty) exerciseMedia.remove(exerciseId) else exerciseMedia[exerciseId] = updated
        mediaStore.save(exerciseMedia.toMap())
        snapshot()
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
    }

    fun deleteSession(id: Long) {
        sessions.removeAll { it.id == id }
        store.saveSessions(sessions.toList())
    }

    fun clearHistory() {
        sessions.clear()
        store.saveSessions(emptyList())
    }

    // ---------- Lista de Trainings ----------

    fun startNewTraining() {
        val now = System.currentTimeMillis()
        draft = Training(id = newId(), name = "", createdAt = now, updatedAt = now)
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
        closeTrainingEditor()
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

    fun duplicateTraining(id: Long) {
        val src = trainings.firstOrNull { it.id == id } ?: return
        val copy = src.copy(
            id = newId(),
            name = duplicateName(src.name),
            workouts = src.workouts.map { it.deepCopy(::newId) },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
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
        osdNonce++
    }

    fun hidePlayerControls() {
        playerControlsVisible = false
    }

    fun togglePlayerControls() {
        if (playerControlsVisible) hidePlayerControls() else showPlayerControls()
    }

    /** Feedback de peso recogido durante el run: "exerciseId:workoutIndex" -> (name, peso, delta kg). */
    val weightFeedback = mutableStateMapOf<String, Triple<String, Double, Double>>()

    fun feedbackKey(exerciseId: String, workoutIndex: Int) = "$exerciseId:$workoutIndex"

    fun recordFeedback(exerciseId: String, workoutIndex: Int, name: String, weight: Double, deltaKg: Double) {
        val key = feedbackKey(exerciseId, workoutIndex)
        weightFeedback[key] = Triple(name, weight, deltaKg)
        sendFeedback(exerciseId, workoutIndex, deltaKg)
    }

    /** Sugerencias de ajuste para la próxima vez (solo las que cambian). */
    fun weightSuggestions(): List<Triple<String, Double, Double>> =
        weightFeedback.entries
            .filter { it.value.third != 0.0 }
            .map { Triple(it.value.first, it.value.second, it.value.second + it.value.third) }

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
        showPlayerControls()
        WorkoutPlayerService.start(getApplication(), id, playerName, playerSteps)
        activePlayerTrainingId = id
    }

    fun pausePlayer() = PlayerBus.command.tryEmit(PlayerCommand.PAUSE)
    fun resumePlayer() = PlayerBus.command.tryEmit(PlayerCommand.RESUME)
    fun checkStep() = PlayerBus.command.tryEmit(PlayerCommand.NEXT)
    fun skipStep() = PlayerBus.command.tryEmit(PlayerCommand.SKIP_STEP)
    fun skipExercise() = PlayerBus.command.tryEmit(PlayerCommand.SKIP_EXERCISE)
    fun prevStep() = PlayerBus.command.tryEmit(PlayerCommand.PREV)
    fun sendFeedback(exerciseId: String, workoutIndex: Int, deltaKg: Double) =
        PlayerBus.command.tryEmit(PlayerCommand.FEEDBACK(exerciseId, workoutIndex, deltaKg))

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

    fun previewBeepTone(uri: String) = alarmPlayer.previewTone(uri, 1f)
    fun stopBeepPreview() = alarmPlayer.stopPreview()

    override fun onCleared() {
        super.onCleared()
        alarmPlayer.stop()
        alarmPlayer.stopPreview()
    }
}
