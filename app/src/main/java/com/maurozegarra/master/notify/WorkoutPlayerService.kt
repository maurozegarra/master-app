package com.maurozegarra.master.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.maurozegarra.master.MainActivity
import com.maurozegarra.master.PlayerBus
import com.maurozegarra.master.PlayerCommand
import com.maurozegarra.master.PlayerSnapshot
import com.maurozegarra.master.R
import com.maurozegarra.master.data.WorkoutStore
import com.maurozegarra.master.model.ConfirmMode
import com.maurozegarra.master.model.DisplayMode
import com.maurozegarra.master.model.PlayerStep
import com.maurozegarra.master.model.SessionLog
import com.maurozegarra.master.model.SessionRecorder
import com.maurozegarra.master.model.SessionStatus
import com.maurozegarra.master.model.StepEngine
import com.maurozegarra.master.model.StepKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Servicio en primer plano que ejecuta una rutina (player) de forma robusta:
 * sobrevive a que la app pase a segundo plano, se apague la pantalla o se cierre
 * la Activity. Mantiene el recorrido (cuenta regresiva + auto-avance), reproduce
 * el cue de alarma en las transiciones, publica el estado en [PlayerBus] y
 * muestra una notificación con el paso actual y la cuenta.
 */
class WorkoutPlayerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var tickJob: Job? = null
    private var lastBeepSec = -1L

    private var steps: List<PlayerStep> = emptyList()
    private var index = 0
    private var running = false
    private var finished = false
    private var endAt = 0L
    private var remainingMs = 0L
    private var name = ""
    private var workoutId = 0L
    private var lastShownSec = -1L
    private var startedAt = 0L
    private var lastPersistAt = 0L
    private val recorder = SessionRecorder()
    /** Índices de workout cuya rotación ya se avanzó en esta corrida (rotación independiente). */
    private val advancedWorkouts = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundCompat(buildNotification())
        if (restore()) {
            publish()
            startTick()
        }
        collectCommands()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_STEPS)?.let { json ->
                steps = decodeSteps(json)
                workoutId = intent.getLongExtra(EXTRA_WORKOUT_ID, 0L)
                name = intent.getStringExtra(EXTRA_NAME) ?: ""
                if (steps.isNotEmpty()) {
                    finished = false
                    advancedWorkouts.clear()
                    recorder.clear()
                    recorder.setTotalExercisesByWorkout(
                        steps.filter { it.kind == StepKind.WORK }
                            .groupBy { it.workoutIndex }
                            .mapValues { it.value.map { ex -> ex.ownerExerciseId }.distinct().size }
                    )
                    startedAt = System.currentTimeMillis()
                    alarmCue(steps.getOrNull(0))
                    beginStep(0)
                }
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_NEXT -> advance()
            ACTION_STOP -> stopPlayer()
            ACTION_RECONNECT -> {
                if (steps.isEmpty()) { restore() }
                if (steps.isNotEmpty()) { publish(); startTick() }
            }
        }
        return START_STICKY
    }

    private fun collectCommands() {
        scope.launch {
            PlayerBus.command.collect { cmd ->
                when (cmd) {
                    PlayerCommand.PAUSE -> pause()
                    PlayerCommand.RESUME -> resume()
                    PlayerCommand.NEXT -> advance()
                    PlayerCommand.PREV -> goBack()
                    PlayerCommand.STOP -> stopPlayer()
                    is PlayerCommand.FEEDBACK -> recorder.setFeedback(cmd.exerciseId, cmd.workoutIndex, cmd.deltaKg)
                }
            }
        }
    }

    // ---------- Máquina de estados ----------

    private fun beginStep(i: Int) {
        index = i
        val step = steps.getOrNull(i) ?: return finishPlayer()
        markCompletedWorkouts(step.workoutIndex)
        if (step.manual) {
            running = false
            remainingMs = 0L
            stopTick()
        } else {
            remainingMs = step.durationSec * 1000L
            running = true
            endAt = System.currentTimeMillis() + remainingMs
            startTick()
        }
        publishAndNotify()
        persist()
    }

    private fun pause() {
        if (!running) return
        remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        running = false
        stopTick()
        publishAndNotify()
        persist()
    }

    private fun resume() {
        val step = steps.getOrNull(index) ?: return
        if (step.manual || running || finished) return
        running = true
        endAt = System.currentTimeMillis() + remainingMs
        startTick()
        publishAndNotify()
        persist()
    }

    private fun advance() {
        if (finished) return
        steps.getOrNull(index)?.let { if (it.kind == StepKind.WORK) recorder.onWorkStepCompleted(it) }
        val next = index + 1
        if (next >= steps.size) {
            finishPlayer()
        } else {
            alarmCue(steps.getOrNull(next))
            beginStep(next)
        }
    }

    private fun goBack() {
        if (finished) return
        val prev = index - 1
        if (prev < 0) return
        beginStep(prev)
    }

    private fun finishPlayer() {
        running = false
        finished = true
        stopTick()
        markCompletedWorkouts((steps.maxOfOrNull { it.workoutIndex } ?: -1) + 1)
        alarmCue()
        recordSession(SessionStatus.COMPLETED)
        publish()
        clearPersist()
        // Notificación final no-ongoing y salir del primer plano.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildCompletedNotification())
        stopSelf()
    }

    private fun stopPlayer() {
        running = false
        stopTick()
        beepPlayer.stopPreview()
        if (!recorder.isEmpty()) recordSession(SessionStatus.PARTIAL)
        clearPersist()
        PlayerBus.state.value = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        stopSelf()
    }

    private fun startTick() {
        stopTick()
        lastBeepSec = -1L
        tickJob = scope.launch {
            while (running) {
                val left = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
                remainingMs = left
                if (left <= 0L) {
                    advance()
                    break
                }
                publish()
                val sec = (left + 999) / 1000
                if (sec != lastShownSec) {
                    lastShownSec = sec
                    updateNotification()
                    // Beep de cuenta final: suena una vez por segundo en los últimos N segundos.
                    val step = steps.getOrNull(index)
                    val fc = step?.finalCount ?: 0
                    if (fc > 0 && sec <= fc && sec >= 1 && sec != lastBeepSec && step != null) {
                        lastBeepSec = sec
                        playBeep(step)
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }

    private val beepPlayer by lazy { com.maurozegarra.master.audio.AlarmPlayer(this) }

    private fun playBeep(step: com.maurozegarra.master.model.PlayerStep) {
        val uri = step.beepSoundUri
            ?: "android.resource://${packageName}/${R.raw.beep_second}"
        beepPlayer.beepTone(uri)
    }

    private fun alarmCue(step: com.maurozegarra.master.model.PlayerStep? = null) {
        if (step?.alarm == false) return
        val uri = step?.beepSoundUri
            ?: "android.resource://${packageName}/${R.raw.beep_work}"
        beepPlayer.beepTone(uri)
    }

    /**
     * Avanza la rotación de cada workout cuyos pasos ya se completaron por entero.
     * Cada workout rota de forma INDEPENDIENTE: si abandonas antes de terminar uno,
     * ese no avanza. [uptoExclusive] = todos los workouts con índice menor están completos.
     */
    private fun markCompletedWorkouts(uptoExclusive: Int) {
        for (wi in StepEngine.workoutsToRotate(advancedWorkouts, uptoExclusive)) {
            advancedWorkouts.add(wi)
            advanceWorkoutRotation(wi)
        }
        persist()
    }

    private fun advanceWorkoutRotation(workoutIndex: Int) {
        try {
            val store = WorkoutStore(this)
            val trainings = store.loadTrainings().toMutableList()
            val ti = trainings.indexOfFirst { it.id == workoutId }
            if (ti < 0) return
            val t = trainings[ti]
            val w = t.workouts.getOrNull(workoutIndex) ?: return
            val nextIdx = StepEngine.nextRotationIndex(w) ?: return
            val updatedWorkouts = t.workouts.toMutableList()
            updatedWorkouts[workoutIndex] = w.copy(rotationIndex = nextIdx)
            trainings[ti] = t.copy(workouts = updatedWorkouts, updatedAt = System.currentTimeMillis())
            store.saveTrainings(trainings)
        } catch (_: Exception) {
        }
    }

    private fun recordSession(status: SessionStatus) {
        try {
            val store = WorkoutStore(this)
            val now = System.currentTimeMillis()
            val durationSec = if (startedAt > 0L) ((now - startedAt) / 1000).toInt() else 0
            val log = SessionLog(
                id = now,
                trainingId = workoutId,
                trainingName = name,
                completedAt = now,
                startedAt = startedAt,
                status = status,
                exercises = recorder.build(),
                durationSec = durationSec,
            )
            store.addSession(log)
        } catch (_: Exception) {
        }
    }

    // ---------- Publicación / notificación ----------

    private fun currentRemaining(): Long {
        val step = steps.getOrNull(index) ?: return 0L
        if (step.manual) return 0L
        return if (running) (endAt - System.currentTimeMillis()).coerceAtLeast(0L) else remainingMs
    }

    private fun publish() {
        val step = steps.getOrNull(index) ?: return
        PlayerBus.state.value = PlayerSnapshot(
            trainingId = workoutId,
            name = name,
            index = index,
            totalSteps = steps.size,
            stepKind = step.kind,
            stepTitle = step.title,
            note = step.note,
            ownerName = step.ownerName,
            ownerExerciseId = step.ownerExerciseId,
            workoutName = step.workoutName,
            workoutIndex = step.workoutIndex,
            totalWorkouts = step.totalWorkouts,
            setIndex = step.setIndex,
            totalSets = step.totalSets,
            reps = step.reps,
            timeBased = step.timeBased,
            display = step.display,
            finalCount = step.finalCount,
            colorArgb = step.colorArgb,
            weighted = step.weighted,
            weightTotal = step.weightTotal,
            weightLabel = step.weightLabel,
            remainingMs = currentRemaining(),
            running = running,
            finished = finished,
        )
    }

    private fun publishAndNotify() {
        publish()
        lastShownSec = -1L
        updateNotification()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    // Textos de notificación en inglés (MASTER es solo-EN; ver hoja de ruta).
    private fun stepTitleText(step: PlayerStep?): String = when (step?.kind) {
        StepKind.PREP -> "Get ready"
        StepKind.REST -> "Rest"
        StepKind.COOLDOWN -> "Cooldown"
        StepKind.WORK -> step.title.ifBlank { "Exercise" }
        null -> "MASTER"
    }

    private fun buildNotification(): Notification {
        val step = steps.getOrNull(index)
        val title = stepTitleText(step)
        val manual = step?.manual == true
        val info = if (manual && step?.kind == StepKind.WORK && !step.timeBased) {
            "${step.reps} reps"
        } else {
            fmtClock(currentRemaining())
        }
        val round = if (step != null && step.kind == StepKind.WORK && step.totalSets > 1) {
            " · ${step.setIndex + 1}/${step.totalSets}"
        } else ""

        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("workoutId", workoutId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("$title$round")
            .setContentText(info)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setShowWhen(false)

        if (manual) {
            builder.addAction(action(R.drawable.ic_notif_play, "Done", ACTION_NEXT))
        } else {
            if (running) {
                builder.addAction(action(R.drawable.ic_notif_pause, "Pause", ACTION_PAUSE))
            } else {
                builder.addAction(action(R.drawable.ic_notif_play, "Resume", ACTION_RESUME))
            }
            builder.addAction(action(R.drawable.ic_notif_play, "Next", ACTION_NEXT))
        }
        builder.addAction(action(R.drawable.ic_notif_close, "Close", ACTION_STOP))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun buildCompletedNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Workout complete!")
            .setContentText(name)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
    }

    private fun action(icon: Int, title: String, actionName: String): Notification.Action {
        val intent = Intent(this, WorkoutPlayerService::class.java).setAction(actionName)
        val pi = PendingIntent.getService(
            this,
            actionName.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, icon),
            title,
            pi,
        ).build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setShowBadge(true)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // ---------- Persistencia (restauración tras muerte del proceso) ----------

    private fun prefs() = getSharedPreferences("athlete_player", Context.MODE_PRIVATE)

    private fun persist() {
        lastPersistAt = System.currentTimeMillis()
        prefs().edit()
            .putBoolean("active", true)
            .putLong("workoutId", workoutId)
            .putString("name", name)
            .putString("steps", encodeSteps(steps))
            .putInt("index", index)
            .putLong("endAt", endAt)
            .putLong("remainingMs", remainingMs)
            .putBoolean("running", running)
            .putString("advancedWorkouts", advancedWorkouts.joinToString(","))
            .putLong("startedAt", startedAt)
            .putLong("lastPersistAt", lastPersistAt)
            .putString("recorderJson", com.maurozegarra.master.model.SessionJson.encode(
                listOf(com.maurozegarra.master.model.SessionLog(
                    id = 0L, trainingId = 0L, trainingName = "",
                    completedAt = 0L, startedAt = 0L,
                    status = SessionStatus.PARTIAL,
                    exercises = recorder.build(),
                ))
            ))
            .apply()
    }

    private fun clearPersist() {
        prefs().edit().clear().apply()
    }

    private fun restore(): Boolean {
        val p = prefs()
        if (!p.getBoolean("active", false)) return false
        val persistedAt = p.getLong("lastPersistAt", 0L)
        if (persistedAt > 0L && System.currentTimeMillis() - persistedAt > ZOMBIE_TIMEOUT_MS) {
            steps = decodeSteps(p.getString("steps", "[]") ?: "[]")
            workoutId = p.getLong("workoutId", 0L)
            name = p.getString("name", "") ?: ""
            startedAt = p.getLong("startedAt", 0L)
            recorder.clear()
            p.getString("recorderJson", null)?.let { rj ->
                com.maurozegarra.master.model.SessionJson.decode(rj).firstOrNull()?.exercises?.forEach { er ->
                    repeat(er.setsCompleted) { setIdx ->
                        val sr = er.sets.getOrNull(setIdx)
                        if (sr != null) recorder.onWorkStepCompleted(
                            PlayerStep(
                                kind = StepKind.WORK,
                                title = er.name,
                                ownerName = er.name,
                                ownerExerciseId = er.exerciseId,
                                workoutName = er.workoutName,
                                workoutIndex = er.workoutIndex,
                                setIndex = setIdx,
                                totalSets = er.totalSets,
                                reps = sr.reps,
                                durationSec = sr.durationSec,
                                timeBased = er.timeBased,
                                weighted = sr.weightKg > 0,
                                weightTotal = sr.weightKg,
                            )
                        )
                    }
                    if (er.feedbackDeltaKg != null) {
                        recorder.setFeedback(er.exerciseId, er.workoutIndex, er.feedbackDeltaKg)
                    }
                }
            }
            if (!recorder.isEmpty()) recordSession(SessionStatus.PARTIAL)
            clearPersist()
            return false
        }
        steps = decodeSteps(p.getString("steps", "[]") ?: "[]")
        if (steps.isEmpty()) {
            clearPersist()
            return false
        }
        workoutId = p.getLong("workoutId", 0L)
        name = p.getString("name", "") ?: ""
        index = p.getInt("index", 0).coerceIn(0, steps.size - 1)
        running = p.getBoolean("running", false)
        finished = false
        advancedWorkouts.clear()
        p.getString("advancedWorkouts", "")?.split(",")?.forEach { s ->
            s.toIntOrNull()?.let { advancedWorkouts.add(it) }
        }
        startedAt = p.getLong("startedAt", 0L)
        lastPersistAt = p.getLong("lastPersistAt", 0L)
        recorder.clear()
        p.getString("recorderJson", null)?.let { rj ->
            com.maurozegarra.master.model.SessionJson.decode(rj).firstOrNull()?.exercises?.forEach { er ->
                repeat(er.setsCompleted) { setIdx ->
                    val sr = er.sets.getOrNull(setIdx)
                    if (sr != null) recorder.onWorkStepCompleted(
                        PlayerStep(
                            kind = StepKind.WORK,
                            title = er.name,
                            ownerName = er.name,
                            ownerExerciseId = er.exerciseId,
                            workoutName = er.workoutName,
                            workoutIndex = er.workoutIndex,
                            setIndex = setIdx,
                            totalSets = er.totalSets,
                            reps = sr.reps,
                            durationSec = sr.durationSec,
                            timeBased = er.timeBased,
                            weighted = sr.weightKg > 0,
                            weightTotal = sr.weightKg,
                        )
                    )
                }
                if (er.feedbackDeltaKg != null) {
                    recorder.setFeedback(er.exerciseId, er.workoutIndex, er.feedbackDeltaKg)
                }
            }
        }
        val step = steps[index]
        if (step.manual) {
            running = false
            remainingMs = 0L
        } else if (running) {
            endAt = p.getLong("endAt", System.currentTimeMillis())
            remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
        } else {
            remainingMs = p.getLong("remainingMs", step.durationSec * 1000L)
        }
        return true
    }

    // Serialización de pasos delegada al companion (encodeSteps/decodeSteps).

    override fun onDestroy() {
        super.onDestroy()
        stopTick()
        scope.cancel()
        beepPlayer.stopPreview()
    }

    companion object {
        private const val CHANNEL_ID = "master_workout_v1"
        private const val NOTIF_ID = 43
        private const val ACTION_START = "com.maurozegarra.master.player.START"
        private const val ACTION_PAUSE = "com.maurozegarra.master.player.PAUSE"
        private const val ACTION_RESUME = "com.maurozegarra.master.player.RESUME"
        private const val ACTION_NEXT = "com.maurozegarra.master.player.NEXT"
        private const val ACTION_STOP = "com.maurozegarra.master.player.STOP"
        private const val ACTION_RECONNECT = "com.maurozegarra.master.player.RECONNECT"
        private const val EXTRA_STEPS = "steps"
        private const val EXTRA_WORKOUT_ID = "workoutId"
        private const val EXTRA_NAME = "name"
        private const val ZOMBIE_TIMEOUT_MS = 12 * 60 * 60 * 1000L

        fun start(context: Context, trainingId: Long, name: String, steps: List<PlayerStep>) {
            val intent = Intent(context, WorkoutPlayerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_STEPS, encodeSteps(steps))
                .putExtra(EXTRA_WORKOUT_ID, trainingId)
                .putExtra(EXTRA_NAME, name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun encodeSteps(list: List<PlayerStep>): String {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("kind", s.kind.name)
                        .put("title", s.title)
                        .put("note", s.note)
                        .put("ownerName", s.ownerName)
                        .put("ownerExerciseId", s.ownerExerciseId)
                        .put("workoutName", s.workoutName)
                        .put("workoutIndex", s.workoutIndex)
                        .put("totalWorkouts", s.totalWorkouts)
                        .put("setIndex", s.setIndex)
                        .put("totalSets", s.totalSets)
                        .put("durationSec", s.durationSec)
                        .put("reps", s.reps)
                        .put("timeBased", s.timeBased)
                        .put("display", s.display.name)
                        .put("confirm", s.confirm.name)
                        .put("finalCount", s.finalCount)
                        .apply { if (s.beepSoundUri != null) put("beepSoundUri", s.beepSoundUri) }
                        .put("alarm", s.alarm)
                        .put("colorArgb", s.colorArgb)
                        .put("weighted", s.weighted)
                        .put("weightTotal", s.weightTotal)
                        .put("weightLabel", s.weightLabel),
                )
            }
            return arr.toString()
        }

        fun decodeSteps(json: String): List<PlayerStep> = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlayerStep(
                    kind = StepKind.valueOf(o.getString("kind")),
                    title = o.optString("title", ""),
                    note = o.optString("note", ""),
                    ownerName = o.optString("ownerName", ""),
                    ownerExerciseId = o.optString("ownerExerciseId", ""),
                    workoutName = o.optString("workoutName", ""),
                    workoutIndex = o.optInt("workoutIndex", 0),
                    totalWorkouts = o.optInt("totalWorkouts", 1),
                    setIndex = o.optInt("setIndex", 0),
                    totalSets = o.optInt("totalSets", 1),
                    durationSec = o.optInt("durationSec", 0),
                    reps = o.optInt("reps", 0),
                    timeBased = o.optBoolean("timeBased", true),
                    display = runCatching { DisplayMode.valueOf(o.optString("display")) }.getOrDefault(DisplayMode.COUNTDOWN),
                    confirm = runCatching { ConfirmMode.valueOf(o.optString("confirm")) }.getOrDefault(ConfirmMode.AUTO),
                    finalCount = o.optInt("finalCount", 0),
                    beepSoundUri = o.optString("beepSoundUri", "").takeIf { it.isNotBlank() && it != "null" },
                    alarm = o.optBoolean("alarm", true),
                    colorArgb = o.optLong("colorArgb", 0xFF2E9E5BL),
                    weighted = o.optBoolean("weighted", false),
                    weightTotal = o.optDouble("weightTotal", 0.0),
                    weightLabel = o.optString("weightLabel", ""),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, WorkoutPlayerService::class.java).setAction(ACTION_STOP),
            )
        }

        fun reconnect(context: Context) {
            val intent = Intent(context, WorkoutPlayerService::class.java).setAction(ACTION_RECONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun fmtClock(ms: Long): String {
            val total = ((ms + 999) / 1000).toInt()
            return "%d:%02d".format(total / 60, total % 60)
        }
    }
}
