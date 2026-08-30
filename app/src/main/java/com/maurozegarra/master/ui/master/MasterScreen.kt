package com.maurozegarra.master.ui.master

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maurozegarra.master.MasterViewModel
import com.maurozegarra.master.i18n.Strings
import com.maurozegarra.master.model.SessionLog
import com.maurozegarra.master.model.SessionStatus
import com.maurozegarra.master.model.Training
import com.maurozegarra.master.model.hasContent
import com.maurozegarra.master.ui.DraggableItem
import com.maurozegarra.master.ui.SwipeAction
import com.maurozegarra.master.ui.SwipeActionsRow
import com.maurozegarra.master.ui.SwipeRowsController
import com.maurozegarra.master.ui.rememberSwipeRowsController
import com.maurozegarra.master.ui.ReorderableContentType
import com.maurozegarra.master.ui.dragContainer
import com.maurozegarra.master.ui.rememberDragDropState
import com.maurozegarra.master.ui.theme.ACTION_DELETE
import com.maurozegarra.master.ui.theme.ACTION_DUPLICATE
import com.maurozegarra.master.ui.theme.ACTION_EDIT
import com.maurozegarra.master.ui.theme.AppTheme
import com.maurozegarra.master.util.formatRemaining
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Router de la sección principal según el estado de navegación del ViewModel. */
@Composable
fun MasterScreen(vm: MasterViewModel, accent: Color, t: Strings, onStart: () -> Unit = { vm.startPlayerRun() }) {
    when {
        vm.playerTrainingId != null -> PlayerScreen(vm, accent, t, onStart)
        vm.exerciseHistoryId != null -> ExerciseHistoryScreen(vm, accent, t)
        vm.showingHistory -> HistoryScreen(vm, accent, t)
        vm.choosingExercise -> ChooseExerciseScreen(vm, accent, t)
        vm.choosingWorkout -> ChooseWorkoutScreen(vm, accent, t)
        vm.editingExerciseId != null -> ExerciseEditorScreen(vm, accent, t)
        vm.editingVariantId != null -> WorkoutEditorScreen(vm, accent, t)
        vm.editingWorkoutId != null && vm.editingWorkout()?.rotating == true -> VariantListScreen(vm, accent, t)
        vm.editingWorkoutId != null -> WorkoutEditorScreen(vm, accent, t)
        vm.draft != null -> TrainingEditorScreen(vm, accent, t)
        else -> TrainingsList(vm, accent, t, onStart)
    }
}

@Composable
private fun TrainingsList(vm: MasterViewModel, accent: Color, t: Strings, onStart: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val baseWeekStart = remember { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    var weekOffset by remember { mutableStateOf(0) }
    val weekStart = remember(weekOffset) { baseWeekStart.plusWeeks(weekOffset.toLong()) }

    val sessionDates = remember(vm.sessions.toList()) {
        vm.sessions
            .groupBy { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() }
            .mapValues { (_, sessions) ->
                if (sessions.any { it.status == SessionStatus.COMPLETED })
                    SessionStatus.COMPLETED else SessionStatus.PARTIAL
            }
    }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val timeFmt = remember { java.time.format.DateTimeFormatter.ofPattern("h:mm a") }
    val swipeController = rememberSwipeRowsController()

    Box(
        Modifier
            .fillMaxSize()
            // Un tap en cualquier zona vacia cierra el panel abierto. Va en el contenedor
            // y solo mientras hay algo abierto: detectTapGestures ignora los taps que un
            // hijo ya consumio (la card, el play), asi que no pisa sus handlers.
            .then(
                if (swipeController.isAnyOpen) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { swipeController.closeAll() }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        if (vm.trainings.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WeekCalendar(weekStart, today, sessionDates, accent, onSwipeLeft = { weekOffset++ }, onSwipeRight = { weekOffset-- }, onDayClick = { selectedDate = it })
                Spacer(Modifier.weight(1f))
                Text(t.emptyTrainings, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(t.savedHint, color = AppTheme.colors.textDim, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
            }
        } else {
            val listState = rememberLazyListState()
            val dragDropState = rememberDragDropState(listState) { from, to ->
                vm.moveTraining(from - 1, to - 1)
            }
            // Al hacer scroll se cierra la fila abierta: dejarla abierta fuera de vista
            // significa volver a encontrarla asi mas tarde, sin recordar por que.
            LaunchedEffect(listState.isScrollInProgress) {
                if (listState.isScrollInProgress) swipeController.closeAll()
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().dragContainer(dragDropState),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "week_calendar") {
                    WeekCalendar(weekStart, today, sessionDates, accent, onSwipeLeft = { weekOffset++ }, onSwipeRight = { weekOffset-- }, onDayClick = { selectedDate = it })
                }
                itemsIndexed(
                    vm.trainings,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> ReorderableContentType },
                ) { index, tr ->
                    DraggableItem(dragDropState, index + 1) { _ ->
                        TrainingCard(
                            training = tr,
                            accent = accent,
                            t = t,
                            isActive = vm.activePlayerTrainingId == tr.id,
                            swipeController = swipeController,
                            onPlay = { vm.openPlayer(tr.id); onStart() },
                            onOpen = { vm.openPlayer(tr.id) },
                            onEdit = { vm.startEditTraining(tr.id) },
                            onDuplicate = { vm.duplicateTraining(tr.id) },
                            onDelete = { vm.deleteTraining(tr.id) },
                        )
                    }
                }
            }
        }

        val activeId = vm.activePlayerTrainingId
        if (activeId != null && vm.playerStep != null) {
            MiniPlayer(
                vm = vm,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp, 0.dp, 16.dp, 80.dp),
                onOpen = { vm.openPlayer(activeId) },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(AppTheme.colors.bg)
                .border(1.dp, accent, CircleShape)
                .clickable { vm.startNewTraining() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = t.createTraining,
                tint = accent,
                modifier = Modifier.size(28.dp),
            )
        }

        if (selectedDate != null) {
            val date = selectedDate!!
            val daySessions = vm.sessions.filter {
                Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() == date
            }.sortedByDescending { it.completedAt }
            DaySessionsSheet(
                date = date,
                zone = zone,
                sessions = daySessions,
                timeFmt = timeFmt,
                accent = accent,
                t = t,
                onDismiss = { selectedDate = null },
                onDeleteSession = { id ->
                    vm.deleteSession(id)
                    val remaining = vm.sessions.filter {
                        Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() == date
                    }
                    if (remaining.isEmpty()) selectedDate = null
                },
                onExerciseClick = { exerciseId ->
                    selectedDate = null
                    vm.openExerciseHistory(exerciseId)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySessionsSheet(
    date: LocalDate,
    zone: ZoneId,
    sessions: List<SessionLog>,
    timeFmt: java.time.format.DateTimeFormatter,
    accent: Color,
    t: Strings,
    onDismiss: () -> Unit,
    onDeleteSession: (Long) -> Unit,
    onExerciseClick: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.colors.bg,
    ) {
        Text(
            dayLabel(date, zone, t),
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sessions, key = { it.id }) { s ->
                SessionRow(
                    session = s,
                    time = Instant.ofEpochMilli(s.completedAt).atZone(zone).format(timeFmt),
                    accent = accent,
                    t = t,
                    onDelete = { onDeleteSession(s.id) },
                    onExerciseClick = onExerciseClick,
                    initiallyExpanded = true,
                )
            }
        }
    }
}

@Composable
private fun WeekCalendar(
    weekStart: LocalDate,
    today: LocalDate,
    sessionDates: Map<LocalDate, SessionStatus>,
    accent: Color,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
) {
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var dragAccum by remember { mutableStateOf(0f) }
    var direction by remember { mutableStateOf(1) }
    AnimatedContent(
        targetState = weekStart,
        transitionSpec = {
            if (direction > 0) {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            } else {
                slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
            }
        },
        label = "week_slide",
    ) { currentWeek ->
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onDragEnd = {
                        val threshold = 80f * density
                        when {
                            dragAccum > threshold -> { direction = -1; onSwipeRight() }
                            dragAccum < -threshold -> { direction = 1; onSwipeLeft() }
                        }
                        dragAccum = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> dragAccum += dragAmount },
                )
            },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (i in 0..6) {
            val date = currentWeek.plusDays(i.toLong())
            val isToday = date == today
            val hasSessions = sessionDates[date] != null
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .then(if (hasSessions) Modifier.clickable { onDayClick(date) } else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = dayLabels[i],
                    color = if (isToday) accent else AppTheme.colors.textDim,
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isToday) accent else Color.Transparent)
                        .border(
                            1.dp,
                            if (isToday) accent else AppTheme.colors.track,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        color = if (isToday) AppTheme.colors.onAccent else AppTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                val sessionStatus = sessionDates[date]
                Box(
                    modifier = Modifier.size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (sessionStatus == SessionStatus.COMPLETED) accent
                            else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (sessionStatus == SessionStatus.PARTIAL) accent
                            else Color.Transparent,
                            CircleShape,
                        ),
                )
            }
        }
    }
    }
}

@Composable
private fun TrainingCard(
    training: Training,
    accent: Color,
    t: Strings,
    isActive: Boolean,
    swipeController: SwipeRowsController,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val exercises = training.workouts.sumOf { w ->
        if (w.variants.isNotEmpty()) w.variants.sumOf { it.exercises.size } else w.exercises.size
    }
    val canPlay = training.workouts.any { it.hasContent() }

    // Orden pedido: borrar, duplicar, editar. El destructivo queda en el extremo
    // izquierdo, el más lejano al pulgar cuando la fila apenas se abre.
    val actions = listOf(
        SwipeAction(Icons.Filled.Delete, ACTION_DELETE, t.delete) { confirmDelete = true },
        SwipeAction(Icons.Filled.ContentCopy, ACTION_DUPLICATE, t.duplicate, onDuplicate),
        SwipeAction(Icons.Filled.Edit, ACTION_EDIT, t.edit, onEdit),
    )

    SwipeActionsRow(actions = actions, controller = swipeController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.textDim.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable { if (!swipeController.consumeTapIfOpen()) onOpen() }
                .padding(end = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    training.name.ifBlank { t.noName },
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                if (isActive) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("IN PROGRESS", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                "${training.workouts.size} ${t.workout} · $exercises ${t.exercise}",
                color = AppTheme.colors.textDim,
                fontSize = 13.sp,
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.dp, accent, CircleShape)
                // Con otra fila abierta, el tap solo descarta: arrancar un training por
                // accidente al intentar cerrar un panel es de lo peor que puede pasar aqui.
                .clickable(enabled = canPlay) {
                    if (!swipeController.consumeTapIfOpen()) onPlay()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = t.start,
                tint = if (canPlay) accent else AppTheme.colors.textDim,
            )
        }

    }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = AppTheme.colors.surface,
            titleContentColor = AppTheme.colors.textPrimary,
            textContentColor = AppTheme.colors.textDim,
            title = { Text(t.delete) },
            text = { Text(t.deleteTrainingConfirm(training.name.ifBlank { t.noName })) },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) {
                    Text(t.delete, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(t.cancel, color = AppTheme.colors.textDim)
                }
            },
        )
    }
}

@Composable
private fun MiniPlayer(vm: MasterViewModel, accent: Color, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val step = vm.playerStep ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.surface)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                step.title.ifBlank { step.workoutName },
                color = AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
            )
            Text(
                if (step.manual) "${step.reps} reps" else formatRemaining(vm.playerRemainingMs),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = { if (vm.playerRunning) vm.pausePlayer() else vm.resumePlayer() }) {
            Icon(
                if (vm.playerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = AppTheme.colors.textPrimary,
            )
        }
        IconButton(onClick = { vm.closePlayer() }) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = AppTheme.colors.textDim)
        }
    }
}
