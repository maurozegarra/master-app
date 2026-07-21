package com.athletic.ui.athlete

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.athletic.AthleteViewModel
import com.athletic.i18n.Strings
import com.athletic.model.Training
import com.athletic.model.hasContent
import com.athletic.ui.DraggableItem
import com.athletic.ui.ReorderableContentType
import com.athletic.ui.dragContainer
import com.athletic.ui.rememberDragDropState
import com.athletic.ui.theme.AppTheme
import com.athletic.util.formatRemaining
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Router de la sección Athlete según el estado de navegación del ViewModel. */
@Composable
fun AthleteScreen(vm: AthleteViewModel, accent: Color, t: Strings, onStart: () -> Unit = { vm.startPlayerRun() }) {
    when {
        vm.playerTrainingId != null -> PlayerScreen(vm, accent, t, onStart)
        vm.showingHistory -> HistoryScreen(vm, accent, t)
        vm.choosingExercise -> ChooseExerciseScreen(vm, accent, t)
        vm.editingExerciseId != null -> ExerciseEditorScreen(vm, accent, t)
        vm.editingVariantId != null -> WorkoutEditorScreen(vm, accent, t)
        vm.editingWorkoutId != null && vm.editingWorkout()?.rotating == true -> VariantListScreen(vm, accent, t)
        vm.editingWorkoutId != null -> WorkoutEditorScreen(vm, accent, t)
        vm.draft != null -> TrainingEditorScreen(vm, accent, t)
        else -> TrainingsList(vm, accent, t, onStart)
    }
}

@Composable
private fun TrainingsList(vm: AthleteViewModel, accent: Color, t: Strings, onStart: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val baseWeekStart = remember { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    var weekOffset by remember { mutableStateOf(0) }
    val weekStart = remember(weekOffset) { baseWeekStart.plusWeeks(weekOffset.toLong()) }

    val sessionDates = remember(vm.sessions.toList()) {
        vm.sessions.map { Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate() }.toSet()
    }

    Box(Modifier.fillMaxSize()) {
        if (vm.trainings.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WeekCalendar(weekStart, today, sessionDates, accent, onSwipeLeft = { weekOffset++ }, onSwipeRight = { weekOffset-- })
                Spacer(Modifier.weight(1f))
                Text(t.emptyTrainings, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(t.savedHint, color = AppTheme.colors.textDim, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
            }
        } else {
            val listState = rememberLazyListState()
            val dragDropState = rememberDragDropState(listState) { from, to ->
                vm.moveTraining(from, to)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().dragContainer(dragDropState),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "week_calendar") {
                    WeekCalendar(weekStart, today, sessionDates, accent, onSwipeLeft = { weekOffset++ }, onSwipeRight = { weekOffset-- })
                }
                itemsIndexed(
                    vm.trainings,
                    key = { _, it -> it.id },
                    contentType = { _, _ -> ReorderableContentType },
                ) { index, tr ->
                    DraggableItem(dragDropState, index) { _ ->
                        TrainingCard(
                            training = tr,
                            accent = accent,
                            t = t,
                            isActive = vm.activePlayerTrainingId == tr.id,
                            onPlay = { vm.openPlayer(tr.id); onStart() },
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
    }
}

@Composable
private fun WeekCalendar(
    weekStart: LocalDate,
    today: LocalDate,
    sessionDates: Set<LocalDate>,
    accent: Color,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
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
            val hasSession = date in sessionDates
            Column(
                modifier = Modifier.wrapContentSize(),
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
                Box(
                    modifier = Modifier.size(4.dp)
                        .clip(CircleShape)
                        .background(if (hasSession) accent else Color.Transparent),
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
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val exercises = training.workouts.sumOf { w ->
        if (w.variants.isNotEmpty()) w.variants.sumOf { it.exercises.size } else w.exercises.size
    }
    val canPlay = training.workouts.any { it.hasContent() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppTheme.colors.surface)
            .border(1.dp, AppTheme.colors.textDim.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
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
                .background(AppTheme.colors.bg)
                .border(1.dp, accent, CircleShape)
                .clickable(enabled = canPlay, onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = t.start,
                tint = if (canPlay) accent else AppTheme.colors.textDim,
            )
        }

        Box(modifier = Modifier.size(32.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = AppTheme.colors.textDim, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(t.edit) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(t.duplicate) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text(t.delete) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun MiniPlayer(vm: AthleteViewModel, accent: Color, modifier: Modifier = Modifier, onOpen: () -> Unit) {
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
